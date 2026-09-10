package souther.compiler.query;

import org.junit.jupiter.api.Test;

import souther.compiler.diag.SourceNameResolver;
import souther.compiler.report.GeneratedRows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A row is looked for at every place an arm stands in the body, not at one of them.
 *
 * <p>A non-recursive helper is spliced into each body that calls it, so one arm the author wrote
 * stands in the running tree once per call site. What steers a row into a splice is what stands on
 * the way to <em>that</em> splice, and two splices of one arm are reached by different ways: a
 * helper called with a value the caller wrote has an arm nothing can steer a row to, and the same
 * helper called with a parameter has one it can.
 *
 * <p>Asked at a single occurrence, the answer was whichever the walk wrote first. The two models
 * below are one body with its call sites swapped — the same arms, the same ways in, the same rows —
 * and one of them was offered a row for the arm while the other was told nothing could steer one
 * along it.
 *
 * <p>Held as the two answers agreeing rather than as one expected block. What the block says about
 * the rest of the model is not this test's, and an assertion over the whole of it would fail for
 * every change to any of it.
 */
class AnArmIsLookedForAtEveryPlaceItStandsInTest {

    /**
     * One three-line difference: which arm of {@code twice} calls the helper with a parameter.
     *
     * <p>{@code pick(On)} can never take the helper's {@code Off} arm — the caller wrote the value
     * — so the splice under it is one no row reaches. The other splice is reached by {@code b}, and
     * the arm the author wrote is owed a row through it.
     */
    private static String twice(String underOn, String underOff) {
        return """
                module example.arms

                data On
                data Off
                data Flag = On | Off
                data Yes
                data No
                data Verdict = Yes | No

                let pick (f: Flag): Verdict =
                    match f with
                        | On  -> Yes
                        | Off -> No

                behavior twice : (a: Flag, b: Flag) -> Verdict
                let twice (a, b) =
                    match a with
                        | On  -> pick(%s)
                        | Off -> pick(%s)

                example twice
                    | "on on" : (On, On) -> Yes
                """.formatted(underOn, underOff);
    }

    /** The helper's arm is offered a row whichever call site can reach it. */
    @Test
    void theArmIsOfferedARowWhicheverSpliceReachesIt() {
        assertTrue(offersARowForTheHelpersArm(twice("b", "On")),
                "reached through the arm the walk meets first");
        assertTrue(offersARowForTheHelpersArm(twice("On", "b")),
                "and through the one it meets second");
    }

    /**
     * And neither is told that nothing can steer a row there.
     *
     * <p>The other half, and not the same assertion. A block that offered a row and printed the
     * note beside it would pass the check above while telling an author the work cannot be done.
     */
    @Test
    void neitherIsToldThatNothingCanSteerARowToIt() {
        assertEquals("", noteAboutTheHelpersArm(twice("b", "On")),
                "no note about the helper's arm in the first order");
        assertEquals("", noteAboutTheHelpersArm(twice("On", "b")),
                "and none in the second");
    }

    /**
     * Whether some row of the block is offered for the helper's own {@code Off} arm.
     *
     * <p>Either way a row says what it was composed for. A row that fills one thing wears the name
     * inline and a row that fills several is written under a note apiece, and which of the two a
     * run produces turns on what else the model leaves — neither is what this is about.
     */
    private static boolean offersARowForTheHelpersArm(String model) {
        return blockOf(model).lines().anyMatch(line ->
                line.equals("// fills case Off") || line.contains("\"case Off\""));
    }

    /** What the block says about the helper's arm where it offers no row for it. */
    private static String noteAboutTheHelpersArm(String model) {
        return blockOf(model).lines()
                .filter(line -> line.startsWith("// no row for `case Off`"))
                .findFirst().orElse("");
    }

    private static String blockOf(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return GeneratedRows.of(compilation, "example.arms", null, true,
                SourceNameResolver.identity()).text();
    }
}
