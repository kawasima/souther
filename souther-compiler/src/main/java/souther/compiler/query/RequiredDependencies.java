package souther.compiler.query;

import souther.compiler.check.BehaviorRequirement;
import souther.compiler.check.Sig;
import souther.compiler.execute.RowTrials;
import souther.compiler.partition.StoodInAnswer;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a row of one behavior has to stand in for, in the order the behavior requires them.
 *
 * <p>One answer to two questions a search asks. Composing a candidate has to know which dependencies
 * need an answer at all — every one the target requires, whether or not the body decides on what it
 * answers — and running one has to hand them over in the order the injecting constructor takes
 * them. Worked out twice, a row would be composed for one list and applied against another, and the
 * mismatch would read as a behavior nothing applies.
 *
 * <p>The signature is the dependency's own, taken from what this module reaches rather than from
 * what it declares: a dependency another module declares is still the behavior a stand-in stands
 * where.
 */
public record RequiredDependencies(List<Required> inOrder) {

    public RequiredDependencies {
        inOrder = List.copyOf(inOrder);
    }

    /** One dependency a behavior requires, and what it takes and answers. */
    public record Required(ValueName.Behavior dependency, Sig signature) {

        public Required {
            if (dependency == null || signature == null) {
                throw new IllegalArgumentException("a requirement is some behavior with a shape");
            }
        }
    }

    /**
     * What {@code behavior} of {@code module} requires, or nothing where the module did not build
     * far enough to say.
     *
     * <p>Null and not an empty list. A behavior that requires nothing and a module whose
     * requirements were never worked out are different facts, and a row composed under the second
     * as though it were the first stands nothing in and is run anyway.
     */
    public static RequiredDependencies of(Db db, String module, String behavior) {
        Map<String, List<BehaviorRequirement>> required =
                db.ask(new Bodies.Requirements(module)).value();
        Map<ValueName.Behavior, Sig> reachable = db.ask(new Bodies.Reachable(module)).value();
        if (required == null || reachable == null) {
            return null;
        }
        List<Required> out = new ArrayList<>();
        for (BehaviorRequirement each : required.getOrDefault(behavior, List.of())) {
            Sig signature = reachable.get(each.dependency());
            if (signature == null) {
                // Nothing this module reaches says what the dependency answers, so no value can be
                // composed for it and no instance made of it. Absent rather than a list missing one
                // entry: a row standing in for all but one of what a behavior requires is not a row
                // that runs, and a caller handed a short list would compose one.
                return null;
            }
            out.add(new Required(each.dependency(), signature));
        }
        return new RequiredDependencies(out);
    }

    /** Whether anything has to be stood in at all. */
    public boolean none() {
        return inOrder.isEmpty();
    }

    /**
     * {@code answers} as what a run stands the dependencies in with, or null where they do not cover
     * what the behavior requires.
     *
     * <p>Null where a required dependency has no answer here. A run that was handed one fewer
     * instance than the constructor takes does not enter the behavior at all, and what comes back
     * is a row nothing was seen doing — which reads as a row that went nowhere unless the shortfall
     * is said here instead.
     *
     * <p>One value per dependency, which is what a row's {@code with} states. A row holding two
     * answers for one dependency is a row nothing composed, said where a row is composed rather
     * than read back out of a list here.
     */
    public List<RowTrials.AnsweredWith> standingIn(List<StoodInAnswer> answers) {
        Map<ValueName.Behavior, StoodInAnswer> byDependency = new LinkedHashMap<>();
        for (StoodInAnswer each : answers) {
            byDependency.put(each.dependency(), each);
        }
        List<RowTrials.AnsweredWith> out = new ArrayList<>(inOrder.size());
        for (Required each : inOrder) {
            StoodInAnswer stood = byDependency.get(each.dependency());
            if (stood == null) {
                return null;
            }
            out.add(new RowTrials.AnsweredWith(each.dependency(), each.signature(),
                    stood.value().value()));
        }
        return List.copyOf(out);
    }
}
