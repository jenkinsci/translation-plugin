package hudson.plugins.translation;

import org.kohsuke.stapler.jelly.ResourceBundle;
import org.kohsuke.stapler.jelly.InternationalizedStringExpression;
import org.kohsuke.stapler.Stapler;

import java.util.Locale;

/**
 * Use of a localized resource.
 *
 * @author Kohsuke Kawaguchi
*/
public final class Msg {
    public final ResourceBundle resourceBundle;
    public final String key;

    public Msg(InternationalizedStringExpression exp) {
        this.resourceBundle = exp.resourceBundle;
        this.key = exp.key;
    }

    public Msg(ResourceBundle resourceBundle, String key) {
        this.resourceBundle = resourceBundle;
        this.key = key;
    }

    public String getEnglish() {
        String msg = resourceBundle.getFormatString(Locale.ENGLISH, key);
        if(msg==null)   msg=key;
        return msg;
    }

    /**
     * Gets the localized messages for the current request locale.
     */
    public String getLocalizedText() {
        return resourceBundle.getFormatStringWithoutDefaulting(Stapler.getCurrentRequest().getLocale(),key);
    }

    /**
     * Is this message already localized for the current request locale?
     */
    public boolean isLocalized() {
        return getLocalizedText()!=null;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Msg that = (Msg) o;
        return this.key.equals(that.key) && that.resourceBundle.equals(that.resourceBundle);
    }

    @Override
    public int hashCode() {
        return resourceBundle.hashCode()*31 + key.hashCode();
    }
}
