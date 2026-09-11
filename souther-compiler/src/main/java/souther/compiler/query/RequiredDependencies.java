package souther.compiler.query;

import souther.compiler.check.BehaviorRequirement;
import souther.compiler.check.Sig;
import souther.compiler.execute.RowTrials;
import souther.compiler.partition.FixtureTemplate;
import souther.compiler.partition.StoodInAnswer;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
     * <p>A dependency answered once, for no particular arguments, is what a row writes as a
     * {@code with}; one answered differently at different arguments is a table. Which of the two a
     * block prints is the block's, and both arrive here the same way.
     */
    public List<RowTrials.AnsweredWith> standingIn(List<StoodInAnswer> answers) {
        Map<ValueName.Behavior, List<StoodInAnswer>> byDependency = new LinkedHashMap<>();
        for (StoodInAnswer each : answers) {
            byDependency.computeIfAbsent(each.dependency(), _ -> new ArrayList<>()).add(each);
        }
        List<RowTrials.AnsweredWith> out = new ArrayList<>(inOrder.size());
        for (Required each : inOrder) {
            List<StoodInAnswer> stood = byDependency.get(each.dependency());
            if (stood == null || stood.isEmpty()) {
                return null;
            }
            out.add(new RowTrials.AnsweredWith(each.dependency(), each.signature(),
                    entries(stood)));
        }
        return List.copyOf(out);
    }

    /**
     * The entries one dependency is stood in by, in the order they are to be tried.
     *
     * <p>One entry answering every call where one value serves every asking, which is what a row's
     * {@code with} states and what a body asking one dependency about one thing needs. Where the
     * askings want different answers, each is stated against the arguments it is an answer for, and
     * the first of them answers a call none of them state — a call this reading did not foresee is
     * answered rather than left to fail, since a run that stopped there would say nothing about
     * where the row went.
     *
     * <p>Whether one value serves is asked of what a row would write. It is not a question about
     * which askings are one — that is settled by {@link souther.compiler.partition.InjectedAnswer}
     * and was settled where the body was read — but about whether the answers a row states come to
     * one line of source, which is what the text is.
     */
    private static List<RowTrials.AnsweredWith.Answer> entries(List<StoodInAnswer> stood) {
        Set<String> distinct = new LinkedHashSet<>();
        for (StoodInAnswer each : stood) {
            distinct.add(each.value().text());
        }
        if (distinct.size() == 1) {
            return List.of(new RowTrials.AnsweredWith.Answer(null, stood.getFirst().value().value()));
        }
        List<RowTrials.AnsweredWith.Answer> out = new ArrayList<>(stood.size() + 1);
        for (StoodInAnswer each : stood) {
            out.add(new RowTrials.AnsweredWith.Answer(
                    each.appliedTo().stream().map(FixtureTemplate::value)
                            .toList(),
                    each.value().value()));
        }
        out.add(new RowTrials.AnsweredWith.Answer(null, stood.getFirst().value().value()));
        return List.copyOf(out);
    }
}
