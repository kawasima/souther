package souther.compiler.query;

import souther.compiler.partition.AnAnswerComposed;
import souther.compiler.partition.AnswerDemand;
import souther.compiler.partition.AnswersDemanded;
import souther.compiler.partition.FixtureTemplate;
import souther.compiler.partition.Generator;
import souther.compiler.partition.InjectedAnswer;
import souther.compiler.partition.MeasuredInput;
import souther.compiler.partition.StoodInAnswer;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * What a row takes to stand a behavior's dependencies in for one rule of its decision.
 *
 * <p>Every dependency the behavior requires, and not only the ones the rule turns on. A row that
 * answers what the way asks and leaves a clock unanswered is a row nothing applies, so the two are
 * composed together — the first against what the way demands, the rest against what their own
 * answer type leaves.
 *
 * <p><b>One value per dependency, which is what a row writes as a {@code with}.</b> A way that
 * needs one to answer differently at different calls needs a table beside the block, and is a way
 * nothing composes a row for — said here, so that a block never has to decide what a row it was
 * handed can be written as.
 *
 * @param requires what the behavior has to stand in for, in the order it requires them
 * @param standing a subject of one position for what a dependency answers, or null where none
 *                 could be read. Asked for rather than made: where a reading of a type is made is
 *                 where every reading of a behavior's input is made, and a second maker of one
 *                 would be free to read the same declarations another way
 */
record AnswersForARule(RequiredDependencies requires,
                       Function<RequiredDependencies.Required, MeasuredInput> standing) {

    /** What the answers for one rule came to. */
    sealed interface Outcome {

        /** Every dependency answered, in the order they are required. */
        record Stood(List<StoodInAnswer> answers) implements Outcome {}

        /** Nothing came of it, in the words a search comes back with. Never a statement that no row
         *  exists. */
        record NothingComposed(Generator.UnresolvedCombination.Reason why) implements Outcome {}
    }

    /**
     * What a row taking {@code demanded} stands the dependencies in with.
     *
     * <p>Every dependency the behavior requires, whether the way asks anything of it or not: a row
     * short of one is a row nothing applies, and what a way says nothing about is answered out of
     * what the dependency's own answer type leaves.
     */
    Outcome of(AnswersDemanded demanded) {
        if (!demanded.whole()) {
            // The way asks something of an answer that nothing here states, so a value composed
            // against the rest would be composed against part of what the row has to be.
            return new Outcome.NothingComposed(
                    Generator.UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE);
        }
        Map<ValueName.Behavior, Map<InjectedAnswer, List<AnswerDemand>>> asked = byDependency(
                demanded.byAnswer());
        List<StoodInAnswer> out = new ArrayList<>();
        for (RequiredDependencies.Required each : requires.inOrder()) {
            Map<InjectedAnswer, List<AnswerDemand>> here =
                    asked.getOrDefault(each.dependency(), Map.of());
            StoodInAnswer stood = standingIn(each, here);
            if (stood == null) {
                return new Outcome.NothingComposed(
                        Generator.UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE);
            }
            out.add(stood);
        }
        return new Outcome.Stood(List.copyOf(out));
    }

    /**
     * What one dependency is stood in with, or null where nothing composed it.
     *
     * <p>A dependency the way asks nothing of is answered for every call: there is no asking to
     * name and no arguments to key a table on, and what such a row needs is something to run
     * against. So is one asked a single thing, for the other reason — one value serves every call
     * the row makes, which is the row with one line to write rather than a table of one row.
     */
    private StoodInAnswer standingIn(RequiredDependencies.Required required,
                                     Map<InjectedAnswer, List<AnswerDemand>> asked) {
        Map<InjectedAnswer, FixtureTemplate> values = new LinkedHashMap<>();
        for (Map.Entry<InjectedAnswer, List<AnswerDemand>> each : asked.entrySet()) {
            FixtureTemplate value = composed(required, each.getValue());
            if (value == null) {
                return null;
            }
            values.put(each.getKey(), value);
        }
        FixtureTemplate one = oneValueForAll(required, values);
        if (one != null) {
            return new StoodInAnswer(required.dependency(), one);
        }
        // A row that needs the dependency to answer differently at different calls, which is a
        // table beside the block rather than a line on the row. Nothing composes one here yet: a
        // {@code fake} is written once for a module and a row is written once for itself, so two
        // rows wanting two tables for one dependency are two blocks, which the language says are
        // not rows of one table — and offering the first of them would hand a person a row whose
        // table the next row takes away.
        //
        // A statement about this compiler and not about the model. The rule stays where it was, the
        // way every other search that came to nothing leaves one.
        return null;
    }

    /**
     * The value that serves every asking, or null where the askings want different ones.
     *
     * <p>Asked of what the row would write, which is what a {@code with} is. Two askings answered
     * alike are one line of source, and a table of rows that all say the same thing is the same
     * answer written as many times as the body asks.
     *
     * <p>A dependency nothing asked about is answered this way as well, and for the other reason:
     * there is no asking to name and no arguments to key a table on, and what such a row needs is
     * something to run against.
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
        MeasuredInput subject = standing.apply(required);
        return subject != null
                && AnAnswerComposed.of(subject, demands)
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
