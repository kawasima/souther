package souther.compiler.query;

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
 * composes one, and such a way is one nothing composes a row for.
 *
 * <p><b>What could not be composed is an answer and never an absence.</b> A row short of a stand-in
 * its target requires is a row nothing applies, and one that wants a table is one this compiler
 * does not write — both are said in the words a search comes back with, so that no reader
 * downstream has to work out what an empty list meant.
 *
 * @param requires what the behavior has to stand in for, in the order it requires them
 * @param standing one subject per dependency a value can be composed for, and no entry for one
 *                 whose answer no position stands at. Handed in rather than made: where a reading
 *                 of a type is made is where every reading of a behavior's input is made
 */
record AnswersForARule(RequiredDependencies requires,
                       Map<ValueName.Behavior, AnswerSubjects> standing) {

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
     * <p>A dependency the way asks nothing of is answered all the same: what such a row needs is
     * something to run against. So is one whose askings all want the same value — one line of
     * source answers them all.
     */
    private AnswersStoodIn standingIn(RequiredDependencies.Required required,
                                      Map<InjectedAnswer, List<AnswerDemand>> asked) {
        Map<InjectedAnswer, FixtureTemplate> values = new LinkedHashMap<>();
        for (Map.Entry<InjectedAnswer, List<AnswerDemand>> each : asked.entrySet()) {
            FixtureTemplate value = composed(required, each.getValue());
            if (value == null) {
                return nothingStandsIn();
            }
            values.put(each.getKey(), value);
        }
        FixtureTemplate one = oneValueForAll(required, values);
        // A way that wants the dependency to answer differently at different calls wants a table,
        // which is written once for a module and is part of the environment several rows share
        // rather than part of a row. Nothing here composes one — what a module already states about
        // that environment is not read, and a table put out beside a module that states its own
        // would leave neither standing in.
        return one != null
                ? new AnswersStoodIn.Stood(List.of(new StoodInAnswer(required.dependency(), one)))
                : new AnswersStoodIn.NothingComposed(
                        Generator.UnresolvedCombination.Reason.A_TABLE_IS_WHAT_THIS_NEEDS);
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
