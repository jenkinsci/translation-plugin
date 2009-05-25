package hudson.plugins.translation;

import hudson.model.Hudson;
import hudson.Util;

import java.util.Properties;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;

/**
 * Handles the persistence of the contributed localization.
 *
 * @author Kohsuke Kawaguchi
 */
public class ContributedL10nStore {
    private final Hudson hudson = Hudson.getInstance();

    public void loadTo(String locale, String baseName, Properties props) {
        File d = getDir(locale);
        if(!d.exists()) return;

        File f = new File(d,Util.getDigestOf(baseName));
        if(!f.exists()) return;

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
            f.delete();
        }
        props.putAll(override);
    }

    public void save(String locale, String baseName, Properties props) throws IOException {
        File d = getDir(locale);
        if(!d.exists()) d.mkdirs();

        File f = new File(d,Util.getDigestOf(baseName));
        FileOutputStream out = new FileOutputStream(f);
        try {
            props.store(out,null);
        } finally {
            out.close();
        }
    }

    private File getDir(String locale) {
        return new File(hudson.getRootDir(), "contributed-localizations/" + locale);
    }
}
