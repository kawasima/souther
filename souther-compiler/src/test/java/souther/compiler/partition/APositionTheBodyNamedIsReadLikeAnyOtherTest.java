package souther.compiler.partition;

import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A position the body named and the enumeration did not is measured by everything that measures a
 * position, and not by a line drawn on it and nothing else.
 *
 * <p><b>What a position carries and what an axis carries are not the same list.</b> An axis is a
 * number and the classes drawn on it; a position is that and the ends its declarations put on it,
 * the rules that reached it and came to no line, what its reading was left with. Assembled at the
 * axis, a position arrived at by being named carried the type it stands at and none of the rest —
 * so a line an author drew outside what the position's own invariant admits was kept rather than
 * dropped, and a position with no account of its reading had nothing to answer when asked for one.
 *
 * <p>So this holds the two answers against each other: the same rule, at a position the enumeration
 * reaches and at one only the body names, comes to the same verdict.
 */
class APositionTheBodyNamedIsReadLikeAnyOtherTest {

    /** An amount no value of which is under a hundred, held in a chain. */
    private static final String CHAIN = """
            module example.bound

            data Amount = Int
                invariant atLeast = value >= 100

            data Nil
            data Cons = { head: Amount, tail: Chain }
            data Chain = Nil | Cons

            behavior f : (c: Chain) -> Int
            let f (c) =
                match c with
                    | Nil -> 0
                    | Cons as k -> match k.tail with
                        | Nil -> 1
                        | Cons as m -> if m.head.value >= LINE then 2 else 3

            example f | "empty" : (Nil) -> 0
            """;

    /** A line the declaration leaves no value on either side of. */
    private static final String OUTSIDE_THE_BOUND = CHAIN.replace("LINE", "10");

    /** And one it does. */
    private static final String INSIDE_THE_BOUND = CHAIN.replace("LINE", "150");

    /**
     * A line outside what the declaration admits draws nothing, two links down as at one.
     *
     * <p>The regression this is here for. The position is one the enumeration never listed, so what
     * says its values never go below a hundred is the reading of {@code Amount}'s own clause at that
     * occurrence — and a reading that arrived without it kept a line at ten and asked for a row on
     * both sides of it, at a position no value under a hundred ever stands at.
     */
    @Test
    void aLineOutsideWhatTheDeclarationAdmitsDrawsNothingThere() {
        String report = report(OUTSIDE_THE_BOUND);

        assertTrue(report.contains("no line: comparison@16:44 — it was read to the end and draws"
                        + " its line outside what the quantity it cuts ever holds, about"
                        + " `c@Cons.tail@Cons.head`"),
                report);
        // Matched with what follows the number, since the bound's own point is at a hundred and
        // the ten it is not is a prefix of it.
        assertFalse(report.contains("c@Cons.tail@Cons.head = 10 (comparison"),
                () -> "and no row is asked for at it:\n" + report);
    }

    /**
     * And the declaration reaches the occurrence, not only the one the enumeration listed.
     *
     * <p>Said as the bound's own border, which is what a clause of {@code Amount} owes wherever an
     * {@code Amount} stands. Both occurrences carry it or the second was measured by a reading that
     * had never met the declaration.
     */
    @Test
    void theDeclarationsOwnBoundIsAtBothOccurrences() {
        String report = report(OUTSIDE_THE_BOUND);

        assertTrue(report.contains("no OFF point is owed at c@Cons.head = 100"
                        + " (invariant Amount (atLeast))"),
                report);
        assertTrue(report.contains("no OFF point is owed at c@Cons.tail@Cons.head = 100"
                        + " (invariant Amount (atLeast))"),
                () -> "the occurrence the body named carries it too:\n" + report);
    }

    /** And a line the declaration does admit is drawn there, so nothing was suppressed wholesale. */
    @Test
    void aLineInsideWhatTheDeclarationAdmitsIsDrawn() {
        String report = report(INSIDE_THE_BOUND);

        assertTrue(report.contains("read as f/c@Cons.tail@Cons.head: = 150"),
                report);
    }

    /** A case the rules of the model leave nothing at, with a rule written inside it. */
    private static final String REFUSED = """
            module example.refused

            data On
            data Off
            data Flag = On | Off

            data Held = { flag: Flag, n: Int }
                invariant never = flag /= Off

            data Ok

            behavior f : (h: Held) -> Ok
                constructs Ok
            let f (h) =
                match h.flag with
                    | On -> Ok
                    | Off -> Ok
            """;

    /**
     * A case the rules refuse gets no position, whether the body names it or not.
     *
     * <p>Naming a path is not evidence that a row is ever written at it. The demand a body raises is
     * allowed to be wider than what the model admits — that is what lets it be collected before
     * there is a reading to ask — and the reading is what answers, by the same rule it answers for
     * every other branch: a case its obligations do not owe is not walked into.
     */
    @Test
    void aCaseTheRulesRefuseIsNotReadBecauseTheBodyNamedIt() {
        String report = report(REFUSED);

        assertFalse(report.contains("h.flag@Off"),
                () -> "no value of this input is ever `Off`:\n" + report);
    }

    /** A chain whose deepest link has a case an arm claims nothing ever reaches. */
    private static final String CLAIMED = """
            module example.claimed

            data On
            data Off
            data Flag = On | Off

            data Nil
            data Cons = { flag: Flag, tail: Chain }
            data Chain = Nil | Cons

            data Answer = Int

            behavior f : (c: Chain) -> Answer
                constructs Answer
            let f (c) =
                match c with
                    | Nil -> Answer(0)
                    | Cons as k -> match k.tail with
                        | Nil -> Answer(1)
                        | Cons as m -> match m.flag with
                            | On  -> Answer(2)
                            | Off -> unreachable "the probe never passes Off"

            example f | "empty" : (Nil) -> Answer(0)
            """;

    /**
     * A claim written past a return is judged against a position, not against an absence.
     *
     * <p>What a claim is held to is the reading of the position it is about, so a claim below where
     * the enumeration stops used to come back unproven for the one reason no author can act on:
     * nothing was read about the case. The arm names the position, so the reading has it, and the
     * claim is answered the way one about any other position is.
     *
     * <p>A claim raises no demand of its own. What it is about is the scrutinee of a {@code match},
     * which is a location the body already reads — a second collector for claims would derive the
     * same paths a second way, which is the arrangement the demand exists to remove.
     *
     * <p>What is left unproven here is that the arm stands inside another, which is a limit on
     * reading what reaches a nested arm and holds wherever one is written. It is not this position
     * being unknown, and the two are what a reader has to be able to tell apart.
     */
    @Test
    void aClaimPastAReturnIsJudgedAgainstThePositionItIsAbout() {
        String report = report(CLAIMED);

        assertTrue(report.contains("no row is in `Off` at c@Cons.tail@Cons.flag"),
                () -> "the case the claim is about is one the reading divides:\n" + report);
        assertFalse(report.contains("nothing was read about this case"),
                () -> "so the claim is not refused for want of a position:\n" + report);
    }

    /**
     * And the reading is the same however the paths were demanded.
     *
     * <p>A reading closed over its demand answers one way; one that grew a position when somebody
     * looked one up would answer by what had been asked before. Held by measuring the same model
     * twice and comparing the whole report, which is every count and every sentence in it.
     */
    @Test
    void twoReadingsOfOneModelSayTheSameThing() {
        assertEquals(report(OUTSIDE_THE_BOUND), report(OUTSIDE_THE_BOUND));
        assertEquals(report(INSIDE_THE_BOUND), report(INSIDE_THE_BOUND));
    }

    private static String report(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).human(SourceRendering.namedByIdentity(compilation.texts()));
    }
}
