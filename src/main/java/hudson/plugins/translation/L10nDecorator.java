package hudson.l10n;

import hudson.model.PageDecorator;
import hudson.Extension;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.jelly.InternationalizedStringExpressionListener;
import org.kohsuke.stapler.jelly.InternationalizedStringExpression;
import org.kohsuke.stapler.jelly.ResourceBundle;

import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Collection;
import java.util.Locale;

/**
 * @author Kohsuke Kawaguchi
 */
@Extension
public class L10nDecorator extends PageDecorator {
    public L10nDecorator() {
        super(L10nDecorator.class);
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

    public static final class Entry {
        public final ResourceBundle resourceBundle;
        public final String key;

        public Entry(InternationalizedStringExpression exp) {
            this.resourceBundle = exp.resourceBundle;
            this.key = exp.key;
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
