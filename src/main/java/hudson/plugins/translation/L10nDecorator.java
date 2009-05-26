package hudson.plugins.translation;

import com.trilead.ssh2.crypto.Base64;
import com.sun.mail.util.BASE64EncoderStream;
import hudson.Extension;
import hudson.Util;
import hudson.plugins.translation.Locales.Entry;
import hudson.model.Hudson;
import hudson.model.PageDecorator;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.WebApp;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.jelly.InternationalizedStringExpressionListener;
import org.kohsuke.stapler.jelly.JellyFacet;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.CipherInputStream;
import static javax.crypto.Cipher.ENCRYPT_MODE;
import static javax.crypto.Cipher.DECRYPT_MODE;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.OutputStreamWriter;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Properties;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;
import java.util.zip.GZIPInputStream;

import net.sf.json.JSONObject;

/**
 * {@link PageDecorator} that adds the translation dialog.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class L10nDecorator extends PageDecorator {
    public final ContributedL10nStore store = new ContributedL10nStore();
    private final ResourceBundleFactoryImpl bundleFactory = new ResourceBundleFactoryImpl(store);
    private final Hudson hudson;

    public L10nDecorator() {
        super(L10nDecorator.class);

        // hook into Stapler to activate contributed l10n
        hudson = Hudson.getInstance();
        WebApp.get(hudson.servletContext).getFacet(JellyFacet.class).resourceBundleFactory
                = bundleFactory;
    }

    public List<Locales.Entry> listLocales() {
        return Locales.LIST;
    }

    /**
     * Activate the recording of the localized resources.
     */
    public void startRecording(StaplerRequest request) {
        if(request.getParameter("l10n")==null)  return;

        request.setAttribute(InternationalizedStringExpressionListener.class.getName(), new MsgRecorder());
    }

    public Collection<Msg> getRecording(StaplerRequest request) {
        return ((MsgRecorder)request.getAttribute(InternationalizedStringExpressionListener.class.getName())).set;
    }

    public String encodeRecording(StaplerRequest request) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // string -> gzip -> encrypt -> base64 -> string
        PrintStream w = new PrintStream(new GZIPOutputStream(new CipherOutputStream(baos,getCipher(ENCRYPT_MODE))));
        for (Msg e : getRecording(request)) {
            w.println(e.resourceBundle.getBaseName());
            w.println(e.key);
        }
        w.close();

        return new String(Base64.encode(baos.toByteArray()));
    }

    /**
     * Does the opposite of {@link #encodeRecording(StaplerRequest)}.
     */
    public List<Msg> decode(StaplerRequest request) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(new GZIPInputStream(new CipherInputStream(
                new ByteArrayInputStream(Base64.decode(request.getParameter("bundles").toCharArray())),
                getCipher(DECRYPT_MODE)))));

        List<Msg> l = new ArrayList<Msg>();
        String s;
        while((s=r.readLine())!=null) {
            l.add(new Msg(bundleFactory.create(s),r.readLine()));
        }
        return l;
    }

    private Cipher getCipher(int mode) {
        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(mode, hudson.getSecretKeyAsAES128());
            return cipher;
        } catch (GeneralSecurityException e) {
            throw new Error(e); // impossible
        }
    }

    /**
     * Looks at {@code Accept-Language} header manually and decide which locale
     * this user is likely capable of translating.
     */
    public String getPrimaryTranslationLocale(StaplerRequest req) {
        String lang = req.getHeader("Accept-Language");
        if(lang==null)  return null;

        for (String t : lang.split(",")) {
            // ignore q=N
            int idx = t.indexOf(';');
            if(idx>=0)  t=t.substring(0,idx);

            // HTTP uses en-US but Java uses en_US
            t=t.replace('-','_').toLowerCase(Locale.ENGLISH);

            for (Entry e : Locales.LIST)
                if(t.startsWith(e.lcode)) // so that 'en_US' matches 'en'.
                    return e.code;
        }

        return null;
    }

    public static final class SubmissionEntry {
        public final String text, baseName, key, original;

        @DataBoundConstructor
        public SubmissionEntry(String text, String baseName, String key, String original) {
            this.text = text;
            this.baseName = baseName;
            this.key = key;
            this.original = original;
        }

        public boolean isUpdated() {
            return !original.equals(text);
        }
    }

    /**
     * Handles the submission from the browser.
     */
    public void doSubmit(StaplerRequest req, StaplerResponse rsp, @QueryParameter String locale) throws IOException, ServletException {
        JSONObject json = req.getSubmittedForm();

        if(hudson.hasPermission(Hudson.ADMINISTER)) {
            // let this submission reflected to this Hudson right away

            // organize contributions by baseName
            Map<String,Properties> updates = new HashMap<String,Properties>();
            for (SubmissionEntry e : req.bindJSONToList(SubmissionEntry.class, json.get("entry"))) {
                if(!e.isUpdated())  continue;

                Properties p = updates.get(e.baseName);
                if(p==null) {
                    p = new Properties();
                    store.loadTo(locale,e.baseName,p);
                    updates.put(e.baseName,p);
                }
                p.put(e.key,e.text);
            }

            // then write them back
            for (Map.Entry<String,Properties> p : updates.entrySet())
                store.save(locale,p.getKey(),p.getValue());
            bundleFactory.clearCache();
        }

        json.remove("bundles"); // we don't need this bulky data send to Hudson project website
        json.put("id", UUID.randomUUID().toString()); // simplifies the correlation between multiple posting sites
        json.put("installation", Util.getDigestOf(hudson.getSecretKey()));
        json.put("version", hudson.VERSION);

        // write back the data that the browser should then submit to the Hudson website
        rsp.setContentType("text/plain;charset=UTF-8");
        OutputStreamWriter w = new OutputStreamWriter(new GZIPOutputStream(new BASE64EncoderStream(rsp.getOutputStream())),"UTF-8");
        json.write(w);
        w.close();

        rsp.setStatus(SC_OK);
    }
}
