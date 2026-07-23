package hudson.plugins.translation;

import hudson.Extension;
import hudson.plugins.translation.Locales.Entry;
import hudson.model.PageDecorator;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.WebApp;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.jelly.InternationalizedStringExpressionListener;
import org.kohsuke.stapler.jelly.JellyFacet;

import jakarta.servlet.ServletException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.Properties;
import java.util.Map;
import java.util.HashMap;

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

    public L10nDecorator() {
        super();

        // hook into Stapler to activate contributed l10n
        WebApp.get(Jenkins.get().getServletContext()).getFacet(JellyFacet.class).resourceBundleFactory
                = bundleFactory;
    }

    public List<Locales.Entry> listLocales() {
        return Locales.LIST;
    }

    /**
     * Activate the recording of the localized resources.
     */
    public void startRecording(StaplerRequest2 request) {
        request.setAttribute(InternationalizedStringExpressionListener.class.getName(), new MsgRecorder());
    }

    public Collection<Msg> getRecording(StaplerRequest2 request) {
        MsgRecorder recording = (MsgRecorder) request.getAttribute(InternationalizedStringExpressionListener.class.getName());
        return recording != null ? recording.set : Collections.emptySet();
    }

    /**
     * Encrypts the recorded resource bundle list into an opaque, tamper-proof
     * token that round-trips through the browser.
     */
    public String encodeRecording(StaplerRequest2 request) {
        StringBuilder buf = new StringBuilder();
        for (Msg e : getRecording(request)) {
            buf.append(e.resourceBundle.getBaseName()).append('\n');
            buf.append(e.key).append('\n');
        }
        return Secret.fromString(buf.toString()).getEncryptedValue();
    }

    /**
     * Does the opposite of {@link #encodeRecording(StaplerRequest2)}.
     */
    public List<Msg> decode(StaplerRequest2 request) {
        List<Msg> l = new ArrayList<>();
        Secret secret = Secret.decrypt(request.getParameter("bundles"));
        if (secret == null) { // tampered with or not produced by this instance
            return l;
        }
        try (BufferedReader r = new BufferedReader(new StringReader(secret.getPlainText()))) {
            String s;
            while((s=r.readLine())!=null) {
                l.add(new Msg(bundleFactory.create(s),r.readLine()));
            }
        } catch (IOException e) {
            throw new AssertionError(e); // StringReader never throws
        }
        return l;
    }

    /**
     * Looks at {@code Accept-Language} header manually and decide which locale
     * this user is likely capable of translating.
     */
    public String getPrimaryTranslationLocale(StaplerRequest2 req) {
        String lang = req.getHeader("Accept-Language");
        if(lang==null)  return null;

        for (String t : lang.split(",")) {
            // ignore q=N
            int idx = t.indexOf(';');
            if(idx>=0)  t=t.substring(0,idx);

            // HTTP uses en-US but Java uses en_US
            t=t.replace('-','_').toLowerCase(Locale.ENGLISH);

            // First, look for an exact matching, so that 'en_US' matches 'en_US' and not 'en_UK' nor 'en'
            for (Entry e : Locales.LIST)
                if(t.equals(e.lcode))
                    return e.lcode;

            // Eventually,  look for the generic locale, so that 'en_US' matches 'en'
            for (Entry e : Locales.LIST)
                if(t.startsWith(e.lcode))
                    return e.lcode;
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
     * Handles the submission from the browser, storing the contributed
     * translations locally on this Jenkins instance.
     */
    @RequirePOST
    public void doSubmit(StaplerRequest2 req, StaplerResponse2 rsp, @QueryParameter String locale) throws IOException, ServletException {
        JSONObject json = req.getSubmittedForm();

        if (locale == null || locale.isBlank()) {
            rsp.sendError(400, "No locale selected");
            return;
        }

        Jenkins jenkins = Jenkins.get();
        if(jenkins.hasPermission(Jenkins.ADMINISTER)) {
            // organize contributions by baseName
            Map<String,Properties> updates = new HashMap<>();
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
    }
}
