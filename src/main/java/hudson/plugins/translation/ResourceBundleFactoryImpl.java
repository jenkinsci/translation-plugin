package hudson.plugins.translation;

import org.kohsuke.stapler.jelly.ResourceBundle;
import org.kohsuke.stapler.jelly.ResourceBundleFactory;

import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link ResourceBundleFactory} to inject contributed localization.
 * 
 * @author Kohsuke Kawaguchi
 */
final class ResourceBundleFactoryImpl extends ResourceBundleFactory {
    private final ContributedL10nStore store;
    private ResourceBundleFactory parentFactory;

    ResourceBundleFactoryImpl(ContributedL10nStore store, ResourceBundleFactory parentFactory) {
        this.store = store;
        this.parentFactory = parentFactory;
    }

    @Override
    public ResourceBundle create(String baseName) {
        final ResourceBundle resourceBundle = this.parentFactory.create(baseName);

        return new ResourceBundle(baseName) {
            private int modCount = reloadModCount.get();
            @Override
            protected Properties wrapUp(String locale, Properties props) {
                store.loadTo(locale,getBaseName(),props);
                return props;
            }
            @Override
            protected Properties get(String key) {
                int mc = reloadModCount.get();
                if (modCount!=mc) {
                    ResourceBundleFactoryImpl.this.clearCache();
                    modCount = mc;
                }
                return super.get(key);
            }

            @Override
            public String getFormatString(Locale locale, String key) {
                String text =  super.getFormatString(locale, key);
                if(text == null) {
                    text = resourceBundle.getFormatString(locale, key);
                }
                return text;
            }

            @Override
            public String getFormatStringWithoutDefaulting(Locale locale, String key) {
                String text = super.getFormatStringWithoutDefaulting(locale, key);
                if(text == null) {
                    text = resourceBundle.getFormatStringWithoutDefaulting(locale, key);
                }
                return text;
            }

            @Override
            public boolean equals(Object o) {
                // Modifications count should not been taken into account in equals() 
                return super.equals(o);
            }
            
            @Override
            public int hashCode() {
                return super.hashCode();
            }       
        };
    }

    public ResourceBundleFactory getParentFactory() {
        return parentFactory;
    }

    /**
     * Used to force the reloading of a cache.
     */
    private final AtomicInteger reloadModCount = new AtomicInteger();

    public void clearCache() {
        reloadModCount.incrementAndGet();
    }
}
