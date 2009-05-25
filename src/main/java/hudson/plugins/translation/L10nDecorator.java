package hudson.plugins.translation;

import com.trilead.ssh2.crypto.Base64;
import hudson.Extension;
import hudson.model.Hudson;
import hudson.model.PageDecorator;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.jelly.InternationalizedStringExpressionListener;
import org.kohsuke.stapler.jelly.ResourceBundle;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.CipherInputStream;
import static javax.crypto.Cipher.ENCRYPT_MODE;
import static javax.crypto.Cipher.DECRYPT_MODE;
import java.io.IOException;
import java.io.PrintStream;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
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

}
