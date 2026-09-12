package souther.compiler.query;

import souther.compiler.check.FakeTables;
import souther.compiler.examples.ExampleProvisioning;
import souther.compiler.partition.AnAnswerComposed;
import souther.compiler.partition.AnswerDemand;
import souther.compiler.partition.AnswersDemanded;
import souther.compiler.partition.AnswersStoodIn;
import souther.compiler.partition.FixtureTemplate;
import souther.compiler.partition.Generator;
import souther.compiler.partition.InjectedAnswer;
import souther.compiler.partition.StoodInAnswer;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a row takes to stand a behavior's dependencies in for one way through its body.
 *
 * <p>Every dependency the behavior requires, and not only the ones the way turns on. A row that
 * answers what the way asks and leaves a clock unanswered is a row nothing applies, so the two are
 * composed together — the first against what the way demands, the rest against what their own
 * answer type leaves.
 *
 * <p><b>One value per dependency, which is what a row writes as a {@code with}.</b> A way that
 * needs one to answer differently at different calls needs a table, and a table is written once for
 * a module — it belongs to the environment several rows share rather than to a row. Nothing here
 * composes one.
 *
 * <p><b>What the module already states is that environment, and a row is composed inside it.</b> A
 * dependency the module writes a {@code fake} for is answered by that table, and the row writes at
 * it only what it has to say of its own — which is where the way turns on what the dependency
 * answers and a value serving every call composes. So a way that wants a table is a way this
 * compiler has a row for wherever the module states one, and a way that wants nothing of a
 * dependency the module answers leaves it answered rather than writing over it: a row of a module
 * runs in the environment the module's written rows run in, or what a search certified is not what
 * an author pastes.
 *
 * <p>Whether the table answers what the way needs is the run's to settle and is not decided here.
 * Reading the table to find out would be a second answer to which of its rows answers a call, and
 * it would be a reading — which is the thing a trial exists because it may be wrong.
 *
 * <p><b>What could not be composed is an answer and never an absence.</b> A row short of a stand-in
 * its target requires is a row nothing applies, and one that wants a table no module states is one
 * this compiler does not write — both are said in the words a search comes back with, so that no
 * reader downstream has to work out what an empty list meant.
 *
 * @param requires what the behavior has to stand in for, in the order it requires them
 * @param standing one subject per dependency a value can be composed for, and no entry for one
 *                 whose answer no position stands at. Handed in rather than made: where a reading
 *                 of a type is made is where every reading of a behavior's input is made
 * @param blocks   what the module's {@code fake} blocks declare, which is what says whether a
 *                 dependency is answered without the row writing anything
 */
record AnswersForARule(RequiredDependencies requires,
                       Map<ValueName.Behavior, AnswerSubjects> standing,
                       FakeTables blocks) {

    /** What a row taking {@code demanded} stands the dependencies in with. */
    AnswersStoodIn of(AnswersDemanded demanded) {
        if (!demanded.whole()) {
            // The way asks something of an answer that nothing here states, so a value composed
            // against the rest would be composed against part of what the row has to be.
            return new AnswersStoodIn.NothingComposed(
                    Generator.UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE);
        }
        Map<ValueName.Behavior, Map<InjectedAnswer, List<AnswerDemand>>> asked = byDependency(
                demanded.byAnswer());
        List<StoodInAnswer> out = new ArrayList<>();
        for (RequiredDependencies.Required each : requires.inOrder()) {
            AnswersStoodIn here =
                    standingIn(each, asked.getOrDefault(each.dependency(), Map.of()));
            if (!(here instanceof AnswersStoodIn.Stood(var answers))) {
                return here;
            }
            out.addAll(answers);
        }
        return new AnswersStoodIn.Stood(List.copyOf(out));
    }

    /**
     * What one dependency is stood in with.
     *
     * <p>A dependency the way asks nothing of is left to the module where the module answers it,
     * and answered with a value of the row's own where nothing else does: what such a row needs is
     * something to run against, and where the module already says what that is, saying it again on
     * the row puts this row somewhere none of the module's own rows are.
     *
     * <p>A dependency the way does ask something of is answered by the row wherever one value
     * serves every asking, that being the one thing that certainly meets what the way asks. Where
     * no such value composes, the module's table stands where it states one — the way may be one
     * the module was written for — and what the run reaches decides whether it was.
     */
    private AnswersStoodIn standingIn(RequiredDependencies.Required required,
                                      Map<InjectedAnswer, List<AnswerDemand>> asked) {
        if (asked.isEmpty() && stated(required.dependency())) {
            return byTheModule(required);
        }
        Map<InjectedAnswer, FixtureTemplate> values = new LinkedHashMap<>();
        for (Map.Entry<InjectedAnswer, List<AnswerDemand>> each : asked.entrySet()) {
            FixtureTemplate value = composed(required, each.getValue());
            if (value == null) {
                return stated(required.dependency()) ? byTheModule(required) : nothingStandsIn();
            }
            values.put(each.getKey(), value);
        }
        FixtureTemplate one = oneValueForAll(required, values);
        if (one != null) {
            return new AnswersStoodIn.Stood(
                    List.of(new StoodInAnswer.OnTheRow(required.dependency(), one)));
        }
        // The way wants the dependency to answer differently at different calls, which wants a
        // table — written once for a module and part of the environment several rows share rather
        // than part of a row. Nothing here composes one, so the row leans on the one the module
        // states, and where the module states none there is nothing for this way to be tried with.
        return stated(required.dependency()) ? byTheModule(required)
                : new AnswersStoodIn.NothingComposed(
                        Generator.UnresolvedCombination.Reason.A_TABLE_IS_WHAT_THIS_NEEDS);
    }

    /** Whether the module answers {@code dependency} without a row writing anything for it. */
    private boolean stated(ValueName.Behavior dependency) {
        return ExampleProvisioning.standingIn(List.of(), dependency, blocks)
                instanceof ExampleProvisioning.Standin.InTheModule;
    }

    private static AnswersStoodIn byTheModule(RequiredDependencies.Required required) {
        return new AnswersStoodIn.Stood(
                List.of(new StoodInAnswer.InTheModule(required.dependency())));
    }

    private static AnswersStoodIn nothingStandsIn() {
        return new AnswersStoodIn.NothingComposed(
                Generator.UnresolvedCombination.Reason.NOTHING_STANDS_IN_FOR_A_DEPENDENCY);
    }

    /**
     * The value that serves every asking, or null where the askings want different ones.
     *
     * <p>Asked of what the row would write, which is what a {@code with} is. It is not a question
     * about which askings are one — that is {@link InjectedAnswer}'s and was settled where the body
     * was read — but about whether the answers a row states come to one line of source.
     */
    private FixtureTemplate oneValueForAll(RequiredDependencies.Required required,
                                           Map<InjectedAnswer, FixtureTemplate> values) {
        if (values.isEmpty()) {
            return composed(required, List.of());
        }
        FixtureTemplate first = values.values().iterator().next();
        return values.values().stream().allMatch(each -> each.text().equals(first.text()))
                ? first : null;
    }

    /** A value of the dependency's answer meeting {@code demands}, or null where none was composed. */
    private FixtureTemplate composed(RequiredDependencies.Required required,
                                     List<AnswerDemand> demands) {
        AnswerSubjects subjects = standing.get(required.dependency());
        AnswerSubjects.Chosen chosen = subjects == null ? null : subjects.against(demands);
        return chosen != null
                && AnAnswerComposed.of(chosen.standing(), chosen.demands())
                        instanceof AnAnswerComposed.Outcome.Composed(var value) ? value : null;
    }

    /** The askings of each dependency, in the order they were first asked about. */
    private static Map<ValueName.Behavior, Map<InjectedAnswer, List<AnswerDemand>>> byDependency(
            Map<InjectedAnswer, List<AnswerDemand>> byAnswer) {
        Map<ValueName.Behavior, Map<InjectedAnswer, List<AnswerDemand>>> out =
                new LinkedHashMap<>();
        byAnswer.forEach((answer, demands) -> out
                .computeIfAbsent(answer.dependency(), _ -> new LinkedHashMap<>())
                .put(answer, demands));
        return out;
    }
}
