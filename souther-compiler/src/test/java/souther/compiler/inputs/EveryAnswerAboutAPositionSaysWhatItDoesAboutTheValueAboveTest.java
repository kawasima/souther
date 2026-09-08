package souther.compiler.inputs;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A position under a case belongs to two values, and every answer about it says which of them it
 * asked.
 *
 * <p>A field every case of a sum spreads is a position of the case and a name the value the sum sits
 * in writes about. So each answer a reading takes from {@link PlacedRules} has a decision to make —
 * whether the value above is asked, and what the two come to together — and each of them made it
 * where it was written, by looking at the answer beside it.
 *
 * <p><b>Which is why the set is written down.</b> One of these was missed exactly because it did not
 * look like the others: {@code projection} was the whole-value question and took no position, so
 * mirroring its neighbours did not reach it, and a field the value above bounded came back proved
 * exactly representable by a reading that had never seen those rules. A register catches that and a
 * symmetry does not: an answer added here without a line below fails, and a line here with no answer
 * fails too.
 *
 * <p>What it does not check is that each answer does what its line says. That is what the fixtures
 * beside it are for, and where one is missing the line says so.
 */
class EveryAnswerAboutAPositionSaysWhatItDoesAboutTheValueAboveTest {

    /** Whether the value a case was narrowed out of is asked, for an answer about a position. */
    private enum Above {
        /** Asked, and what the two say is combined the way the line writes. */
        ASKED,
        /** Not asked, and the line says why the value above has nothing to add. */
        NOT_ASKED
    }

    /**
     * What each answer does about the value above, and what holds it to that.
     *
     * <p>Exactly the answers {@link PlacedRules} gives about a position. A line whose fixture is
     * {@code null} is an answer nothing holds to its decision — written out rather than left to be
     * noticed, because that is the state a reader of this file has to be able to see.
     */
    private record Decided(Above above, String how, String fixture) {}

    private static Map<String, Decided> theRegister() {
        Map<String, Decided> table = new LinkedHashMap<>();
        table.put("at", new Decided(Above.ASKED,
                "what both leave, met — and the declarations holding each end follow the end that "
                        + "survived, which is why they are one answer",
                "AClauseAboveASumIsReadAtTheFieldItIsAboutTest"
                        + ".aCaseThatStopsTheFieldShorterThanTheValueAboveHoldsThatEndAlone"));
        table.put("leftAt", new Decided(Above.ASKED, "what both leave, met",
                "AClauseAboveASumIsReadAtTheFieldItIsAboutTest.theEndStandsUnderEachCase"));
        table.put("admits", new Decided(Above.ASKED,
                "the values both admit, and short of what either was short of",
                "ALineDrawnOnASharedNameFallsUnderEachCaseTest"
                        + ".aClauseThatComesToNoLineNamesTheSharedFieldUnderEachCase"));
        table.put("placedAt", new Decided(Above.ASKED, "both, kept apart by the rule that drew each",
                "AClauseAboveASumIsReadAtTheFieldItIsAboutTest.theEndStandsUnderEachCase"));
        table.put("noLineAt", new Decided(Above.ASKED, "both",
                "ALineDrawnOnASharedNameFallsUnderEachCaseTest"
                        + ".aClauseThatComesToNoLineNamesTheSharedFieldUnderEachCase"));
        table.put("endsLeftOpenAt", new Decided(Above.ASKED, "both, as the rules with no line are",
                "ALineDrawnOnASharedNameFallsUnderEachCaseTest"
                        + ".aClauseWhoseEndAChoiceLeftOpenNamesTheSharedFieldUnderEachCase"));
        table.put("unanswered", new Decided(Above.ASKED, "both",
                "AClauseAboveASumIsReadAtTheFieldItIsAboutTest.aQuestionRaisedAboveIsRaisedAtEachCase"));
        table.put("unclassified", new Decided(Above.ASKED, "both, as the questions beside them are",
                "AClauseAboveASumIsReadAtTheFieldItIsAboutTest.aQuestionRaisedAboveIsRaisedAtEachCase"));
        table.put("everyRuleReachedAt", new Decided(Above.ASKED, "both, and short if either is",
                "AClauseAboveASumIsReadAtTheFieldItIsAboutTest"
                        + ".aClauseAboveThisReadingDidNotTakeInLeavesTheFieldShort"));
        table.put("projection", new Decided(Above.ASKED,
                "neither certificate is one for the pair, so the pair is not certified",
                "AClauseAboveASumIsReadAtTheFieldItIsAboutTest"
                        + ".theProofThatBoundsSayEverythingIsNotOneValuesAlone"));
        table.put("handsTheRulesOnAt", new Decided(Above.NOT_ASKED,
                "the value above raises its own handing over at the sum, and the descent into the "
                        + "case is what takes it up — asked here as well, one handing over would be "
                        + "taken up twice",
                "APositionUnderACaseIsAPositionTest"));
        table.put("placed", new Decided(Above.NOT_ASKED,
                "a rule of the value above is placed under that value and accounted for there; "
                        + "counted here as well, one rule would owe an answer twice",
                "NoRuleIsPlacedWhereNothingAccountsForItTest.everyRuleThatPlacedAnEndIsInTheAccount"));
        table.put("clausesWithoutAnEnd", new Decided(Above.NOT_ASKED,
                "the pair of `placed`, and the same answer for the same reason: a clause of the "
                        + "value above is handed over by that value's own reading, and one handed "
                        + "over here as well would draw its line twice. Which is where this parts "
                        + "from `noLineAt` beside it — that one is asked about a position and owes "
                        + "every rule reaching it, and this is asked of a reading and owes the "
                        + "clauses that reading holds",
                "AClauseAboveASumDrawsItsLineOnceTest"));
        table.put("movedAtTheValue", new Decided(Above.NOT_ASKED,
                "the same answer as `placed` and for the same reason, which is what it is the "
                        + "other half of: an end a rule of the value above moved is an end of that "
                        + "value's own coordinate, placed under that value and accounted for "
                        + "there. What is asked here is only the ends moved at the value this "
                        + "reading is opened at, which is the one place the reading of the clauses "
                        + "as they are written cannot see them",
                "NoRuleIsPlacedWhereNothingAccountsForItTest.everyRuleThatPlacedAnEndIsInTheAccount"));
        table.put("statedAtTheValue", new Decided(Above.NOT_ASKED,
                "the same answer as `placed` and for the same reason, and the other half of what "
                        + "`movedAtTheValue` is the first half of: an end of the value above is "
                        + "placed under that value and accounted for there. What is asked here is "
                        + "the ends this value's own conjuncts state on its own coordinates, which "
                        + "is where a rule stating no comparison puts one",
                "ARunOfTheStringsIsABoundAndIsOwedItsEdgeTest"));
        table.put("ownEndsAt", new Decided(Above.NOT_ASKED,
                "the two above, asked about one position rather than about the reading, and so the "
                        + "same answer for the same reason: an end of the value above is placed "
                        + "under that value and accounted for there. What this adds to them is "
                        + "which position their ends are of — they are the ends of the value this "
                        + "reading is opened at, and at any other position they are ends of "
                        + "another value. Answered here because this reading is what knows which "
                        + "value that is; answered by the caller, the root's ends were held "
                        + "against the numbers of whatever it was reading",
                "ATypeWritingAboutBothOfItsNumbersChoosesNeitherTest"));
        table.put("bounds", new Decided(Above.NOT_ASKED,
                "what a reading holds of its own value, which is what the answers above are taken "
                        + "from — the value above is asked through them and not through this",
                null));
        table.put("given", new Decided(Above.NOT_ASKED,
                "the value above is a reading of its own, and what it constrains is taken under its "
                        + "own root; asked here as well, one rule would be given twice",
                null));
        table.put("machines", new Decided(Above.NOT_ASKED,
                "where the answers about a declaration's string machines are asked for — a "
                        + "capability this reading was handed and hands on, not an answer about any "
                        + "position; the value above was handed the same one",
                null));
        table.put("answers", new Decided(Above.NOT_ASKED,
                "the answers about this value's own string machines, which `bounds` is asked "
                        + "with; the value above has its own, filed under its own declaration",
                null));
        return table;
    }

    /**
     * Every answer is in the register, and every line is an answer.
     *
     * <p>Both directions. An answer added without a line is one whose decision about the value above
     * was made where it was written and nowhere said; a line left behind after its answer is gone
     * says a decision is being kept for something that no longer asks it.
     */
    @Test
    void everyAnswerAboutAPositionIsInTheRegister() {
        assertEquals(new TreeSet<>(theRegister().keySet()), new TreeSet<>(answers()),
                "the answers a reading takes from the rules of the value a position is in. Each of "
                        + "them decides what to do about the value a case was narrowed out of, and "
                        + "the decision is written down here rather than made where the answer is");
    }

    /**
     * An answer that asks the value above has something that fails when it stops asking.
     *
     * <p>Asked of the ones that ask, because they are the ones a fixture can measure: take the
     * asking away and a model exists whose answer changes. An answer that does not ask has nothing
     * to add and nothing to take away, and what stands for it is the reason written beside it.
     */
    @Test
    void everyAnswerThatAsksTheValueAboveHasSomethingThatMeasuresIt() {
        Map<String, String> without = new TreeMap<>();
        theRegister().forEach((name, decided) -> {
            if (decided.above() == Above.ASKED && decided.fixture() == null) {
                without.put(name, decided.how());
            }
        });

        assertEquals(Map.of(), without,
                "answers that ask the value above and that nothing measures. What closes one is a "
                        + "model reaching it through a sum, and an answer added here arrives with "
                        + "no such model — so this is empty, and a name appearing in it is an "
                        + "answer whose decision about the value above nothing holds it to");
    }

    /**
     * The answers a reading takes about a position, read off the type.
     *
     * <p>Off the methods and not off a list beside them, so that an answer added is one this test
     * has to be told about. What is left out is how a reading reaches the rules at all — the
     * components the record is made of, and the ways one is built.
     */
    private static java.util.Set<String> answers() {
        java.util.Set<String> out = new TreeSet<>();
        for (Method each : PlacedRules.class.getDeclaredMethods()) {
            if (Modifier.isPrivate(each.getModifiers()) || Modifier.isStatic(each.getModifiers())
                    || each.isSynthetic() || COMPONENTS.contains(each.getName())) {
                continue;
            }
            out.add(each.getName());
        }
        return out;
    }

    /**
     * What the record is made of, which is not an answer about a position.
     *
     * <p>{@code sets} is here because it answers nothing: it is what this reading is allowed to
     * build while working its answers out, and the question it settles is about this compiler
     * rather than about the value a case was narrowed out of. Every answer that does reach the
     * value above spends from it, and what each of those decides is the line written for that
     * answer above.
     */
    private static final java.util.Set<String> COMPONENTS =
            java.util.Set.of("root", "value", "rules", "alsoReaching", "sets",
                    "equals", "hashCode", "toString");
}
