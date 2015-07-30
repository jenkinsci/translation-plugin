package hudson.plugins.translation;

import hudson.Util;
import hudson.model.Hudson;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles the persistence of the contributed localization.
 *
 * @author Kohsuke Kawaguchi
 */
public class ContributedL10nStore {
    private final Hudson jenkins = Hudson.getInstance();
    private static final Logger LOGGER = Logger.getLogger(ContributedL10nStore.class.getName());

    public void loadTo(String locale, String baseName, Properties props) {
        File d = getDir(locale);
        if(!d.exists()) return;

        File f = new File(d,Util.getDigestOf(trim(baseName)));
        if(!f.exists()) {
            // for compatibility reason, check the untrimmed location, too
            f = new File(d,Util.getDigestOf(baseName));
            if(!f.exists()) return;
        }

        // load and override
        Properties override = new Properties();
        try {
            FileInputStream in = new FileInputStream(f);
            try {
                override.load(in);
            } finally {
                in.close();
            }
        } catch (IOException e) {
            // We supress all errors here
            LOGGER.log(Level.WARNING, "Cannot load translation of "+baseName+" to locale "+locale, e);
            if (!f.delete()) {
                LOGGER.log(Level.WARNING, "Cannot delete localization file {0}", f);
            }
        }
        props.putAll(override);
    }

    public void save(String locale, String baseName, Properties props) throws IOException {
        File d = getDir(locale);
        if(!d.exists() && !d.mkdirs()) {
            throw new IOException("Cannot create directory "+d);
        }

        File f = new File(d,Util.getDigestOf(trim(baseName)));
        FileOutputStream out = new FileOutputStream(f);
        try {
            props.store(out,null);
        } finally {
            out.close();
        }
    }

    /**
     * The base name of a resource is an absolute URL, which often includes the version number of Jenkins.
     * So trim it back to just contain the classpath portion.
     *
     * For example, file:/.../glassfish/domains/domain1/generated/jsp/j2ee-modules/hudson/loader/lib/hudson/buildHealth
     * to "/lib/hudson/buildHealth"
     */
    private String trim(String baseName) {
        if (baseName.startsWith("file:")) {
            int idx = baseName.lastIndexOf("WEB-INF/classes");
            if(idx>=0)
                return baseName.substring(idx+"WEB-INF/classes".length());

            // glassfish uses URLs like file:/.../glassfish/domains/domain1/generated/jsp/j2ee-modules/hudson/loader/lib/hudson/buildHealth
            // or                       file:/.../glassfish/domains/domain1/generated/jsp/hudson/loader/hudson/model/View/builds
            Matcher m = JSP_LOADER_PATTERN.matcher(baseName);
            if (m.find())
                return baseName.substring(m.end()-1); // the pattern matches the first '/', hence -1
        }
        if (baseName.startsWith("jar:")) {
            int idx = baseName.lastIndexOf('!');
            return baseName.substring(idx+1);
        }
        // couldn't figure it out
        return baseName;
    }

    private File getDir(String locale) {
        return new File(jenkins.getRootDir(), "contributed-localizations/" + locale);
    }

    private static final Pattern JSP_LOADER_PATTERN = Pattern.compile("generated/jsp/(.+)/loader/");
}
