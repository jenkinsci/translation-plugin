package hudson.plugins.translation;

import com.trilead.ssh2.crypto.Base64;
import hudson.Extension;
import hudson.model.Hudson;
import hudson.model.PageDecorator;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.jelly.InternationalizedStringExpression;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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

        request.setAttribute(InternationalizedStringExpressionListener.class.getName(), new Recorder());
    }

    public Collection<Entry> getRecording(StaplerRequest request) {
        return ((Recorder)request.getAttribute(InternationalizedStringExpressionListener.class.getName())).set;
    }

    public String encodeRecording(StaplerRequest request) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // string -> gzip -> encrypt -> base64 -> string
        PrintStream w = new PrintStream(new GZIPOutputStream(new CipherOutputStream(baos,getCipher(ENCRYPT_MODE))));
        for (Entry e : getRecording(request)) {
            w.println(e.resourceBundle.getBaseName());
            w.println(e.key);
        }
        w.close();

        return new String(Base64.encode(baos.toByteArray()));
    }

    /**
     * Does the opposite of {@link #encodeRecording(StaplerRequest)}.
     */
    public List<Entry> decode(StaplerRequest request) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(new GZIPInputStream(new CipherInputStream(
                new ByteArrayInputStream(Base64.decode(request.getParameter("bundles").toCharArray())),
                getCipher(DECRYPT_MODE)))));

        List<Entry> l = new ArrayList<Entry>();
        String s;
        while((s=r.readLine())!=null) {
            l.add(new Entry(new ResourceBundle(s),r.readLine()));
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

    public static final class Entry {
        public final ResourceBundle resourceBundle;
        public final String key;

        public Entry(InternationalizedStringExpression exp) {
            this.resourceBundle = exp.resourceBundle;
            this.key = exp.key;
        }

        public Entry(ResourceBundle resourceBundle, String key) {
            this.resourceBundle = resourceBundle;
            this.key = key;
        }

        public String getEnglish() {
            String msg = resourceBundle.getFormatString(Locale.ENGLISH, key);
            if(msg==null)   msg=key;
            return msg;
        }

        public String getText() {
            // TODO: how do we accept a locale?
            return resourceBundle.getFormatString(Locale.SIMPLIFIED_CHINESE,key);
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            Entry that = (Entry) o;
            return this.key.equals(that.key) && that.resourceBundle.equals(that.resourceBundle);
        }

        @Override
        public int hashCode() {
            return resourceBundle.hashCode()*31 + key.hashCode();
        }
    }

    public static class Recorder implements InternationalizedStringExpressionListener {
        private final Set<Entry> set = new LinkedHashSet<Entry>();
        public void onUsed(InternationalizedStringExpression exp, Object[] args) {
            set.add(new Entry(exp));
        }
    }
}
