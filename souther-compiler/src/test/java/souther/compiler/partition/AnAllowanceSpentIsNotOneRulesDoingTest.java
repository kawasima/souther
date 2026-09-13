package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.Clause;
import souther.compiler.check.ClauseName;
import souther.compiler.check.PartId;
import souther.compiler.check.RuleRef;
import souther.compiler.inputs.BlockReason;
import souther.compiler.inputs.TermPath;
import souther.compiler.regex.Meter;
import souther.compiler.regex.PatternPlan;
import souther.compiler.regex.PatternSyntax;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.SequencedMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * An allowance already spent is what composing a value came to, and not what one rule of it is.
 *
 * <p>One meter pays for every rule of a position, so which construction meets the end of it is
 * which one came next. Named for that one, an author is sent to a rule that — asked first — would
 * have been built, and which the offer may already carry a value from: what a rule proposes on its
 * own is built out of an allowance of its own, so the rule named as having given nothing is one
 * whose value is in the row.
 *
 * <p>Held here rather than through a compile. Reaching the end of the allowance means having built
 * most of what it allows, which is the one thing about this that cannot be made cheap — so what is
 * held is the decision, over a meter that really did stop.
 */
class AnAllowanceSpentIsNotOneRulesDoingTest {

    /** A meter that has been refused, and by which limit. */
    private static Meter stoppedBy(Meter.Stopped which) {
        // Nothing left to build with, so the first state asked for is refused by the whole
        // allowance; and a machine larger than one may be is refused by the other limit first.
        Meter meter = which == Meter.Stopped.THE_ANSWER ? new Meter(50_000, 1) : new Meter(1, 1);
        PatternPlan.of(PatternSyntax.ofAnySymbols(2, 2)).compile(meter);
        assertEquals(which, meter.stoppedBy(), "the fixture reaches the limit it names");
        return meter;
    }

    private static StringOfferShortfall.Subject.ARule aRule() {
        return new StringOfferShortfall.Subject.ARule(new PartId<>(
                new RuleRef.Invariant(new Clause.Ref(new Clause.Id(
                        TypeSymbols.declared(new TypeKey("example.spent", "Code")), 0),
                        Optional.of(new ClauseName("shape")))), 0));
    }

    /**
     * A machine larger than a machine may be is the rule's, because that is the machine somebody
     * wrote and can write smaller.
     */
    @Test
    void aMachineTooLargeIsTheRulesOwn() {
        StringOfferShortfall.NotOffered made =
                StringOfferShortfall.NotOffered.whileMaking(aRule(), stoppedBy(
                        Meter.Stopped.ONE_MACHINE));

        assertInstanceOf(StringOfferShortfall.Subject.ARule.class, made.of());
    }

    /**
     * And an allowance spent is not, however plainly the rule was the one in hand when it ran out.
     */
    @Test
    void anAllowanceSpentIsAboutComposingTheValue() {
        StringOfferShortfall.NotOffered made =
                StringOfferShortfall.NotOffered.whileMaking(aRule(), stoppedBy(
                        Meter.Stopped.THE_ANSWER));

        assertInstanceOf(StringOfferShortfall.Subject.ComposingAValue.class, made.of(),
                "the rule was next and not the cause: asked first it would have been built");
    }

    /**
     * And what a combination carries is what it was made with, whatever the caller does with the
     * map afterwards.
     *
     * <p>Two of these are equal by what they hold, so a map a caller kept a handle on is a value
     * that can be changed after it was made — which is what a record is for the absence of.
     */
    @Test
    void whatACombinationCarriesIsNotTheCallersToChange() {
        SequencedMap<TermPath, StringOfferShortfall> handed = new LinkedHashMap<>();
        handed.put(TermPath.of("t").then("code"), StringOfferShortfall.of(
                StringOfferShortfall.NotOffered.ofARuleNotRead(aRule().part(),
                        new BlockReason.UnreadValueRule())));
        Generator.UnresolvedCombination made = new Generator.UnresolvedCombination(
                List.of("t.flag=Yes"),
                Generator.UnresolvedCombination.Reason.NOT_ALL_CANDIDATES_COULD_BE_OFFERED,
                null, Optional.empty(), handed);

        handed.clear();

        assertEquals(1, made.alsoShort().size(),
                "the combination holds what it was made with, and the caller's map is the"
                        + " caller's");
        assertThrows(UnsupportedOperationException.class, () -> made.alsoShort().clear());
    }

    /**
     * Nor can one be written by hand, which is what keeps the rule above from being a rule only
     * this one caller keeps.
     */
    @Test
    void nothingCanPairAnAllowanceSpentWithARule() {
        StringOfferShortfall.Why spent =
                new StringOfferShortfall.Why.TooCostly(Meter.Stopped.THE_ANSWER);

        assertNotNull(assertThrows(IllegalArgumentException.class,
                () -> new StringOfferShortfall.NotOffered(aRule(), spent)).getMessage());
        assertNotNull(assertThrows(IllegalArgumentException.class,
                () -> new StringOfferShortfall.NotOffered(
                        new StringOfferShortfall.Subject.WhatTheyLeaveTogether(), spent))
                .getMessage());
    }
}
