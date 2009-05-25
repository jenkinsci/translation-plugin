package hudson.plugins.translation;

import org.kohsuke.stapler.jelly.ResourceBundle;
import org.kohsuke.stapler.jelly.ResourceBundleFactory;

import java.util.Properties;

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
            private int modCount = reloadModCount;
            @Override
            protected Properties wrapUp(String locale, Properties props) {
                store.loadTo(locale,getBaseName(),props);
                return props;
            }
            @Override
            protected Properties get(String key) {
                int mc = reloadModCount;
                if (modCount!=mc) {
                    clearCache();
                    modCount = mc;
                }
                return super.get(key);
            }
        };
    }

    /**
     * Used to force the reloading of a cache.
     */
    private volatile int reloadModCount = 0;

    public void clearCache() {
        reloadModCount++;
    }
}
