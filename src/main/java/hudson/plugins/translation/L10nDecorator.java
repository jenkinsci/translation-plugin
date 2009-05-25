package hudson.plugins.translation;

import com.trilead.ssh2.crypto.Base64;
import hudson.Extension;
import hudson.plugins.translation.Locales.Entry;
import hudson.model.Hudson;
import hudson.model.PageDecorator;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.jelly.InternationalizedStringExpressionListener;
import org.kohsuke.stapler.jelly.ResourceBundle;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.CipherInputStream;
import static javax.crypto.Cipher.ENCRYPT_MODE;
import static javax.crypto.Cipher.DECRYPT_MODE;
import static javax.servlet.http.HttpServletResponse.SC_ACCEPTED;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import java.io.IOException;
import java.io.PrintStream;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.zip.GZIPOutputStream;
import java.util.zip.GZIPInputStream;

/**
 * {@link PageDecorator} that adds the translation dialog.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class L10nDecorator extends PageDecorator {
    public L10nDecorator() {
        super(L10nDecorator.class);
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
            l.add(new Msg(new ResourceBundle(s),r.readLine()));
        }
        return l;
    }

    private Cipher getCipher(int mode) {
        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(mode, Hudson.getInstance().getSecretKeyAsAES128());
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

    /**
     * Handles the submission from the browser.
     */
    public void doSubmit(StaplerRequest req, StaplerResponse rsp) throws IOException {
        if(!Hudson.getInstance().hasPermission(Hudson.ADMINISTER)) {
            // let the browser know that this server isn't willing to reflect the submission immediately.
            rsp.setStatus(SC_ACCEPTED);
            return;
        }

        for (Msg msg : decode(req)) {
            System.out.println(msg.key);
        }

        rsp.setStatus(SC_OK);
    }
}
