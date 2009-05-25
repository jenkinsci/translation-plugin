package hudson.plugins.translation;

import org.kohsuke.stapler.jelly.InternationalizedStringExpressionListener;
import org.kohsuke.stapler.jelly.InternationalizedStringExpression;

import java.util.Set;
import java.util.LinkedHashSet;

/**
 * {@link InternationalizedStringExpressionListener} that collects {@link Msg}s.
 * 
 * @author Kohsuke Kawaguchi
 */
final class MsgRecorder implements InternationalizedStringExpressionListener {
    final Set<Msg> set = new LinkedHashSet<Msg>();

    public void onUsed(InternationalizedStringExpression exp, Object[] args) {
        set.add(new Msg(exp));
    }
}
