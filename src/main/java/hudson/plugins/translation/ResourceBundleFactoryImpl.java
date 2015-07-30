package hudson.plugins.translation;

import org.kohsuke.stapler.jelly.ResourceBundle;
import org.kohsuke.stapler.jelly.ResourceBundleFactory;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link ResourceBundleFactory} to inject contributed localization.
 * 
 * @author Kohsuke Kawaguchi
 */
final class ResourceBundleFactoryImpl extends ResourceBundleFactory {
    private final ContributedL10nStore store;

    ResourceBundleFactoryImpl(ContributedL10nStore store) {
        this.store = store;
    }

    @Override
    public ResourceBundle create(String baseName) {
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

    /**
     * Used to force the reloading of a cache.
     */
    private final AtomicInteger reloadModCount = new AtomicInteger();

    public void clearCache() {
        reloadModCount.incrementAndGet();
    }
}
