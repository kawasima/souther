package souther.compiler;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The same rule, written as an {@code invariant} and as a {@code guard}, reported the same way.
 *
 * <p>ADR-0090's promise: a rule this could not read is named, whichever rule it was. The two
 * behaviors below compare the same arithmetic form over two positions — one in a declaration, one
 * in a body — and a reader is owed the same sentence about both.
 *
 * <p>Issue #868. The {@code invariant}'s half was silent, and what silenced it was that its fields
 * carry a bound of their own: the account was kept as what a position was left with if nothing
 * divided it, so a line read from one rule answered for the rule beside it. A reader was left with
 * a model whose only stated rule about {@code length} looked like the one on {@code Cm}.
 */
class AnInvariantSaysARuleWentUnreadTheWayAGuardDoesTest {

    private static final String MODEL = """
            module example.parcels

            data Cm = Int
                invariant value >= 0

            data Parcel = { length: Cm, width: Cm }
                invariant Int.add(length.value, width.value) <= 150

            data Order = { straw: Cm, choco: Cm }

            data Ok
            data No

            behavior quote : (parcel: Parcel) -> Ok | No
            let quote (parcel) = Ok

            behavior mix : (order: Order) -> Ok | No
            let mix (order) =
                if Int.add(order.straw.value, order.choco.value) <= 150 then Ok else No

            data Pair = { x: Int, y: Int }
                invariant x < y + 1

            data Bare = { x: Int, y: Int }

            behavior byRule : (p: Pair) -> Ok | No
            let byRule (p) = Ok

            behavior byGuard : (b: Bare) -> Ok | No
            let byGuard (b) = if b.x < b.y + 1 then Ok else No

            data Sole = { x: Int }
                invariant x < x + 1

            behavior byRuleAboutOne : (s: Sole) -> Ok | No
            let byRuleAboutOne (s) = Ok

            behavior byGuardAboutOne : (b: Bare) -> Ok | No
            let byGuardAboutOne (b) = if b.x < b.x + 1 then Ok else No
            """;

    private static String blockOf(String behavior) {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        String human = AdequacyReport.of(compilation).human(SourceRendering.namedByIdentity(SourceLayouts.NONE));
        StringBuilder block = new StringBuilder();
        boolean inside = false;
        for (String line : human.split("\n", -1)) {
            if (line.startsWith("  ") && !line.startsWith("   ")) {
                inside = line.startsWith("  " + behavior + " ");
            }
            if (inside) {
                block.append(line).append('\n');
            }
        }
        return block.toString();
    }

    /** The {@code guard}, which always said it, at both the positions it compares. */
    @Test
    void aGuardNamesBothPositionsItCompares() {
        String block = blockOf("mix");

        assertTrue(notReadAbout(block, "order.straw"), block);
        assertTrue(notReadAbout(block, "order.choco"), block);
    }

    /** And the {@code invariant} of the same shape, at both the positions it compares. */
    @Test
    void andSoDoesAnInvariantOfTheSameShape() {
        String block = blockOf("quote");

        assertTrue(notReadAbout(block, "parcel.length"), block);
        assertTrue(notReadAbout(block, "parcel.width"), block);
    }

    /** In the same words, since what stopped each of them is the same fact about this compiler. */
    @Test
    void andInTheSameWords() {
        assertEquals(saidAbout(blockOf("mix"), "order.straw"),
                saidAbout(blockOf("quote"), "parcel.length"));
    }

    /**
     * And in the same words for a comparison with one position on each side.
     *
     * <p>{@code x < y + 1}, which is the form between the two the pair above holds. The arithmetic
     * is on the far side of a relation rather than in place of it, so the answer is that the rule
     * relates two positions — and it has to be that answer on both sides of the language, since the
     * work a reader is being sent to do is a class about two positions in one case and a reader for
     * a form in the other.
     */
    @Test
    void andForAComparisonWithOnePositionOnEachSide() {
        assertEquals(saidAbout(blockOf("byGuard"), "b.x"),
                saidAbout(blockOf("byRule"), "p.x"));
        assertTrue(blockOf("byRule").contains("relates two positions"), blockOf("byRule"));
    }

    /**
     * And neither of them says "another position" where there is only one.
     *
     * <p>{@code x < x + 1} puts a position either side of the comparison and names one position.
     * Both readers had been answering it off whether each side names any position at all, so both
     * sent a reader looking for a second one the model never wrote — the {@code guard} for as long
     * as it has been reading comparisons.
     */
    @Test
    void andNeitherNamesASecondPositionWhereThereIsOnlyOne() {
        String rule = blockOf("byRuleAboutOne");
        String guard = blockOf("byGuardAboutOne");

        assertFalse(rule.contains("relates two positions"), rule);
        assertFalse(guard.contains("relates two positions"), guard);
        assertEquals(saidAbout(guard, "b.x"), saidAbout(rule, "s.x"));
    }

    /**
     * And the line the fields' own type draws is still drawn.
     *
     * <p>The rule beside it went unread; this one did not. A reading that answered the pair with
     * one verdict would have taken the boundary with it.
     *
     * <p>Three lines: one at each field's own end, and one on what the record's clause relates. That
     * last divides neither field — which is what the partition above says of it — and it is still a
     * line, drawn on the form the clause cuts rather than on either position.
     */
    @Test
    void andTheBoundTheFieldsOwnTypeStatesIsStillALine() {
        String block = blockOf("quote");

        assertTrue(block.contains("border      borders 3   obligations 0/0\n"), block);
    }

    /** One clause and one sentence: what a reader has to lift is one thing. */
    @Test
    void andOnceEach() {
        String block = blockOf("quote");

        assertEquals(1, block.lines().filter(line -> notReadAbout(line, "parcel.length")).count(),
                block);
    }

    /**
     * And one clause is one sentence, whichever reading of it gave up first.
     *
     * <p>A rule about a position no line can be drawn on is a rule the end reading refuses for one
     * cause and the value reading for another. Read off both, the report printed two lines about
     * one clause naming two different limits — a reader cannot act on that, and only one of them is
     * what would give the position an axis.
     */
    @Test
    void andOnceWhereTwoReadingsBothGaveUpOnTheSameClause() {
        Compilation compilation = Compilation.ofSource("""
                module example.stages

                data Prospecting
                data Qualified
                data Won
                data Stage = Prospecting | Qualified | Won

                data AtLeastQualified = Qualified
                    invariant value >= Prospecting

                data Ok

                behavior stage : (q: AtLeastQualified) -> Ok
                let stage (q) = Ok
                """, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        String human = AdequacyReport.of(compilation).human(SourceRendering.namedByIdentity(SourceLayouts.NONE));

        assertEquals(1, human.lines().filter(line -> notReadAbout(line, "q")).count(), human);
        assertTrue(human.contains("no line can be drawn on"), human);
    }

    /**
     * What the report says stopped the reading at {@code position}, without the rule that stopped
     * there.
     *
     * <p>The words alone, which is what these rows compare: an invariant and a guard of one shape
     * are owed the same sentence, and they are two rules with two handles. Taken with the handle,
     * every row here would differ for the reason the rows exist to say does not matter.
     */
    private static String saidAbout(String block, String position) {
        return block.lines().filter(line -> notReadAbout(line, position))
                .map(line -> line.contains(" — ")
                        ? line.substring(line.indexOf(" — ") + 3, line.indexOf(", about `"))
                        : line.substring(line.indexOf('(') + 1, line.lastIndexOf(')')))
                .findFirst().orElseThrow(() -> new AssertionError(
                        "nothing said about `" + position + "`: " + block));
    }

    /**
     * Whether any line of {@code block} saying a rule left the position with no line is about
     * {@code position}.
     *
     * <p>Asked as a line rather than as a prefix. A finding about a rule names the rule first and
     * the position after it, and one about a position names the position — so a test matching
     * `+not read: <position>+` stopped meaning anything for the first kind rather than failing,
     * which is a negative assertion that passes because the words moved.
     *
     * <p>Either word, because the report writes two: a reading that stopped is `+not read+` and a
     * rule read to the end that divided no position is `+no line+`. Which of them a rule gets is
     * its reason's business and not this one's — what is asked here is whether the position was
     * named at all.
     */
    private static boolean notReadAbout(String block, String position) {
        return block.lines().anyMatch(line ->
                (line.contains("not read:") || line.contains("no line:"))
                && (line.contains("not read: " + position + " ")
                        || line.contains("no line: " + position + " ")
                        || line.contains("about `" + position + "`")));
    }
}
