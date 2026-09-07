package souther.compiler.inputs;

import org.junit.jupiter.api.Test;

import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.Prepared;
import souther.compiler.check.Sig;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;
import souther.compiler.query.Shapes;
import souther.compiler.types.Type;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What the reading of ends hands on and what it reports are two lists, and neither is read off the
 * other.
 *
 * <p>A conjunct is handed on because nothing about it was settled here; it is reported because this
 * reading owed a line and drew none. Both come of the same conjunct having no end, and they are
 * different sentences to different readers — one goes to whoever reads the clause next, and the
 * other goes to an author.
 *
 * <p>The two axes move apart on a rule that names a value. An equality and a disequality place no
 * end, and that is no failure of anything: there is nothing for an author to lift and there is a
 * conjunct for the next reading to make what it can of. Built one from the other, such a rule
 * either went out as a boundary nobody wrote or could not be passed along at all.
 */
class WhatIsHandedOnIsNotWhatIsReportedTest {

    /**
     * Four rules of one record, one per pairing of the two answers.
     *
     * <p>{@code ordered} relates two positions: it places no end at either, and where the values of
     * one stop is a question it raises and does not answer. {@code notZero} and {@code exactlyFive}
     * name a value of one position. {@code capped} places an end and is neither.
     */
    private static final String MODEL = """
            module example.handover

            data R = { lo: Int, hi: Int }
                invariant ordered = lo <= hi
                invariant notZero = lo /= 0
                invariant exactlyFive = hi == 5
                invariant capped = lo <= 9

            data Taken

            behavior take : (r: R) -> Taken
            let take (r) = Taken
            """;

    /** Everything with no end is handed on, whatever this reading owed about it. */
    @Test
    void everyConjunctWithoutAnEndIsHandedOn() {
        assertEquals(List.of("ordered", "notZero", "exactlyFive"), handedOn(),
                "the rule that placed an end is the one that is not here");
    }

    /**
     * And only the rule this reading owed a line for is reported as one.
     *
     * <p>Asked of the reason and not of the list. What a reading found no line for is one list and
     * holds two kinds of fact: a rule it owed a line for and drew none, and a rule that placed no
     * end and held the position to what it admits instead. {@code notZero} is the second — every
     * value but nothing is what it leaves {@code lo} — and read off the list alone it counts as a
     * boundary an author was owed.
     */
    @Test
    void onlyARuleALineWasOwedForIsReported() {
        assertEquals(List.of("ordered"), reportedAsALineOwed(),
                "naming a value is not a line this reading failed to draw");
        assertEquals(List.of("notZero"), reportedAsARestriction(),
                "and the rule that holds the position to what it admits says that instead");
    }

    /**
     * And the hand-over as its reader is given it, which is the list the split is for.
     *
     * <p>Asked of {@code clausesWithoutAnEnd} and not only of what it is built from: taking it off
     * the findings again is a change neither of the two above would notice, and it is the one that
     * put the reading of lines back where it started.
     */
    @Test
    void theClausesHandedOverAreTheOnesWithoutAnEnd() {
        assertEquals(List.of("ordered", "notZero", "exactlyFive"),
                read().clausesWithoutAnEnd().stream()
                        .map(each -> named(each.part())).distinct().toList());
    }

    /**
     * A number taken of the value is handed on as the value's own is.
     *
     * <p>Which of a position's numbers a rule is about is no part of whether anything was settled
     * about it. Read off the findings, a rule naming a length reached the drawing reading only
     * where something else had already reported it, so whether such a rule was ever read again
     * turned on what a report happened to have a sentence for.
     */
    @Test
    void aRuleAboutANumberTakenOfTheValueIsHandedOnToo() {
        assertEquals(List.of("notBlank", "exactlyFive"),
                read(MEASURED, "n").clausesWithoutAnEnd().stream()
                        .map(each -> named(each.part())).distinct().toList());
    }

    /** A newtype measured by the length of what stands at it, with both shapes of rule that name a
     *  value of that length. */
    private static final String MEASURED = """
            module example.handover

            data Subject = String
                invariant notBlank = String.length(value) /= 0
                invariant exactlyFive = String.length(value) == 5

            data Taken

            behavior take : (n: Subject) -> Taken
            let take (n) = Taken
            """;

    private static List<String> handedOn() {
        return read().bounds().withoutAnEnd().stream()
                .map(each -> named(each.part()))
                .distinct().toList();
    }

    private static List<String> reportedAsALineOwed() {
        return reportedFor(why -> !(why instanceof BlockReason.RuleRestrictingToAdmittedValues));
    }

    private static List<String> reportedAsARestriction() {
        return reportedFor(why -> why instanceof BlockReason.RuleRestrictingToAdmittedValues);
    }

    private static List<String> reportedFor(
            java.util.function.Predicate<BlockReason.RuleWithoutLineReason> of) {
        return read().bounds().noLines().stream()
                .filter(each -> of.test(each.why()))
                .map(each -> named(each.part()))
                .distinct().toList();
    }

    /** What the clause is called, taken out of the name a report prints it under. */
    private static String named(
            souther.compiler.check.PartId<souther.compiler.check.RuleRef.Invariant> part) {
        String written = part.rule().citedName();
        return written.substring(written.indexOf('(') + 1, written.indexOf(')'));
    }

    private static PlacedRules read() {
        return read(MODEL, "r");
    }

    private static PlacedRules read(String source, String parameter) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Prepared prepared = compilation.db().ask(new Shapes.Prepared(module)).value();
        Map<String, Sig> sigs = compilation.db().ask(new Bodies.Signatures(module)).value();
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        if (prepared.behaviors().isEmpty()) {
            throw new AssertionError("the model under test compiles");
        }
        Type type = sigs.get("take").inputTypes().get(0);
        return PlacedRules.of(TermPath.of(parameter), type, rules, ReadAs.THE_COMPILATION_DOES);
    }
}
