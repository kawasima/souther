package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.diag.Located;
import souther.compiler.diag.msg.DataMessage;
import souther.compiler.query.Compilation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A denial between two positions, held beside the product the alternatives are over.
 *
 * <p>An equality between two positions is a congruence and can be what a product is indexed by: the
 * two become one side of it, and a rule about either is a rule about both. A denial is not one. It
 * removes the diagonal from a product of two sides and makes no side, so it reaches no coordinate —
 * and left there, what the rules stated about {@code p} and what they stated about {@code r} stayed
 * two facts about two places, and a declaration whose positions cannot differ was admitted.
 *
 * <p>What is asserted here is that it reaches a reading, that the reading is the same whatever the
 * positions are of, and that what it comes to is worked out from what each of the positions is left
 * rather than from the rule alone.
 *
 * <p>Each refusal is written beside the nearest model that does have a value. A reading that held
 * the positions apart and left them nothing would refuse both, and what is asserted would be that
 * something was refused rather than that the denial was read.
 */
class ADenialBetweenTwoPositionsIsHeldBesideTheProductTest {

    private static final String STAGE = """
            module demo

            data Ready
            data Done
            data Stage = Ready | Done

            """;

    private static List<String> saidOf(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        return compilation.diagnostics().values().stream()
                .flatMap(List::stream)
                .map(Located::diagnostic)
                .map(each -> each.said().getClass().getSimpleName())
                .toList();
    }

    private static void refuses(String source) {
        assertEquals(List.of("NoValuesTheseCanAllDifferIn"), saidOf(source),
                "no value of this can be written");
    }

    private static void admits(String source) {
        assertEquals(List.of(), saidOf(source), "a value of this can be written");
    }

    /** The places a refusal names, as it writes them. */
    private static String named(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        return compilation.diagnostics().values().stream()
                .flatMap(List::stream)
                .map(Located::diagnostic)
                .map(each -> each.said())
                .filter(DataMessage.NoValuesTheseCanAllDifferIn.class::isInstance)
                .map(each -> ((DataMessage.NoValuesTheseCanAllDifferIn) each).at())
                .findFirst()
                .orElseThrow(() -> new AssertionError("nothing said which places cannot differ"));
    }

    /**
     * Two positions of a sum, each left the one value, and said to differ.
     *
     * <p>Each clause about one position is read. What was not read is the one relating them, and
     * with it read there is no value {@code p} can take that {@code r} does not.
     */
    @Test
    void twoPositionsOfASumHeldApartAreLeftNoWayOfDiffering() {
        refuses(STAGE + """
                data Pair = { p: Stage, r: Stage }
                    invariant no = p /= r && p == Ready && r == Ready
                """);
    }

    /** And the same rules leaving them different values are admitted, so what refuses is the pair
     *  of facts and not the denial. */
    @Test
    void andTheSameDenialOverRulesThatLeaveRoomIsAdmitted() {
        admits(STAGE + """
                data Pair = { p: Stage, r: Stage }
                    invariant ok = p /= r && p == Ready && r == Done
                """);
    }

    /** A denial nothing else narrows leaves both positions everything and refuses nothing. */
    @Test
    void aDenialOnItsOwnRefusesNothing() {
        admits(STAGE + """
                data Pair = { p: Stage, r: Stage }
                    invariant apart = p /= r
                """);
    }

    /** A string is a carrier the numbers do not hold, and is read the same way. */
    @Test
    void twoStringPositionsHeldApartAreReadTheSameWay() {
        refuses("""
                module demo

                data Pair = { p: String, r: String }
                    invariant no = p /= r && p == "A" && r == "A"
                """);
    }

    /**
     * And where one of them is left its one value by its ends rather than by a written value.
     *
     * <p>What a denial comes to is settled against what each position is left, which is its values
     * met with where its order stops. Read against the values alone, {@code r} would come back
     * admitting every string and the pair would be admitted — the same rule refused one way when an
     * author wrote it as an equality and another when they wrote it as two bounds.
     */
    @Test
    void andWhereTheOneValueIsLeftByTheEndsRatherThanByAWrittenValue() {
        refuses("""
                module demo

                data Pair = { p: String, r: String }
                    invariant no = p /= r && p == "b" && r >= "b" && r <= "b"
                """);
    }

    /** A position of two values is one the numbers do not hold either, and is read the same way. */
    @Test
    void twoTruthValuedPositionsHeldApartAreReadTheSameWay() {
        refuses("""
                module demo

                data Pair = { p: Bool, r: Bool }
                    invariant no = p /= r && p == true && r == true
                """);
    }

    /**
     * An {@code Int} is beside them, because two readings answering one clause is not two meanings
     * of it.
     *
     * <p>The numbers hold a denial between two of them as a hole in a sum, and this reading holds
     * it as a relation between two blocks. Both are right and the answer has to be the same.
     */
    @Test
    void andAnIntIsRefusedTheSameWayTheNumbersRefuseIt() {
        refuses("""
                module demo

                data Pair = { p: Int, r: Int }
                    invariant no = p /= r && p == 1 && r == 1
                """);
    }

    /**
     * Three positions each stated to differ from the others, over a carrier of two values.
     *
     * <p>Refused by counting and by no pair: every pair of them can differ, and the three of them
     * need a value each. So what the reading answers is how many values the positions it relates
     * can take between them.
     */
    @Test
    void threePositionsAllHeldApartOverTwoValuesAreRefusedByCounting() {
        refuses(STAGE + """
                data Trio = { p: Stage, q: Stage, r: Stage }
                    invariant no = p /= q && q /= r && r /= p
                """);
    }

    /**
     * And a chain of the same length is not, which is what tells the counting rule from the parts
     * the relation falls into.
     *
     * <p>{@code p /= q && q /= r} relates all three and states nothing of {@code p} and {@code r},
     * so two values are enough: {@code p} and {@code r} may be {@code Ready} with {@code q}
     * {@code Done}. A reading that counted the blocks a relation reaches rather than the blocks all
     * stated to differ would refuse this, and no rule of the model says so.
     */
    @Test
    void andAChainOfThreeOverTwoValuesStands() {
        admits(STAGE + """
                data Trio = { p: Stage, q: Stage, r: Stage }
                    invariant ok = p /= q && q /= r
                """);
    }

    /**
     * Positions the rules hold as one value and state to differ.
     *
     * <p>A conjunction puts the two blocks together, and the denial between them becomes a value
     * stated to differ from itself. Read against what the blocks hold, there is nothing to read:
     * the rule refuses whatever they were left.
     */
    @Test
    void twoPositionsHeldAsOneAndApartAreRefusedWhateverTheyHold() {
        assertEquals(List.of("ItsRulesCannotAllHold"), saidOf(STAGE + """
                data Pair = { p: Stage, r: Stage }
                    invariant no = p == r && p /= r
                """), "no value of this can be written");
    }

    /** The refusal names the positions together, since each of them is left values of its own. */
    @Test
    void theRefusalNamesThePositionsTogether() {
        String source = STAGE + """
                data Pair = { p: Stage, r: Stage }
                    invariant no = p /= r && p == Ready && r == Ready
                """;

        refuses(source);
        assertEquals("`p`, `r`", named(source));
    }
}
