package souther.compiler.query;

import souther.compiler.ast.Hir;
import souther.compiler.check.Lower;
import souther.compiler.check.PipelineSigs;
import souther.compiler.check.Sig;
import souther.compiler.check.DerivedSymbols;
import souther.compiler.check.Unanswerable;
import souther.compiler.types.ValueName;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What each of a module's composed behaviors was settled to route.
 *
 * <p>Asked here rather than worked out where it is emitted. A composition's routing decides what
 * the composition answers, so the checker has already walked it; a backend that walked it again
 * would be a second answer to a question about what a Souther program means, and the two agree
 * only until one of them is edited.
 *
 * <p>A behavior written with a {@code let} is not here. Its checked form is its {@link
 * souther.compiler.core.Core} body, which {@link Bodies.Checked} answers with.
 */
public final class Compositions {

    private Compositions() {}

    /** Every composed behavior this module declares, by the name it is reached by. */
    public record Of(String name) implements Key<Map<ValueName.Behavior, souther.compiler.core.Composition>> {

        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Map<ValueName.Behavior, souther.compiler.core.Composition>> compute(Db db) {
            Answer<Lower.Lowered> lowering = db.ask(new Bodies.Lowering(name));
            Answer<Map<ValueName.Behavior, Sig>> sigs = db.ask(new Bodies.Reachable(name));
            Answer<DerivedSymbols> scope = Names.derivedSymbols(db, name);
            if (!lowering.present() || !sigs.present() || !scope.present()) {
                return Answer.absent();
            }
            Hir.Module module = lowering.value().lowered();
            Map<ValueName.Behavior, List<Hir.Var>> stages = PipelineSigs.pipelineStages(module);
            Map<ValueName.Behavior, souther.compiler.core.Composition> out = new LinkedHashMap<>();
            for (Hir.BehaviorDef behavior : module.behaviors()) {
                if (behavior instanceof Hir.PipeBehavior pipe) {
                    try {
                        out.put(new ValueName.Behavior(module.name(), pipe.name()),
                                PipelineSigs.composition(pipe, sigs.value(), scope.value(), stages));
                    } catch (Unanswerable _) {
                        // A stage that names nothing was reported where it was written, and this
                        // composition has nothing to route. Left out for the reason its signature
                        // is: the others keep theirs, and nothing here is emitted anyway — the name
                        // that denotes nothing is what `Names.Sound` answers for.
                    }
                }
            }
            return Answer.of(Ordered.map(out));
        }
    }
}
