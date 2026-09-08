package souther.compiler.coverage;

import org.junit.jupiter.api.Test;

import souther.compiler.meta.ModulePath;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When two numberings of one module are one, and when they are two.
 *
 * <p>What everything else here is for. A run is recorded in bare numbers; a number means a place
 * only under the numbering that handed it out; so a measure reading a recording is answering about
 * the places that recording is of, or about other places and saying nothing either way. The parts
 * are held on their own — where a place is, what a body does, where a name is bound — and this is
 * the whole of them: the answer a reader would act on.
 *
 * <p>Written as the two directions together, because either alone is easy and wrong. Called equal
 * whenever the source is the same, a numbering says nothing and refuses nothing; called equal only
 * for the very same construction, it refuses every recomputation and the store recomputes.
 */
class TwoNumberingsOfOneModuleAreOneTest {

    private static final String MODEL = """
            module demo

            let picked (n: Int, cap: Int): List<Int> = [ n | n >= 240, n <= cap ]

            behavior over : (a: Int, b: Int) -> List<Int>
            let over (a, b) = picked(a, b) ++ picked(b, a)

            behavior graded : (n: Int) -> Int
            let graded (n) = if n >= 1 then 1 else 2
            """;

    /** The same model with a blank line above it and a comment in it: the same bodies, written
     *  further down the file. */
    private static final String MOVED = "\n// a note the author left\n" + MODEL;

    /** The same bodies with a declaration above them, so the module counts its constructs from
     *  somewhere else. */
    private static final String AFTER_ANOTHER = MODEL.replace(
            "behavior over :",
            """
            behavior first : (n: Int) -> Int
            let first (n) = if n >= 9 then 1 else 2

            behavior over :""");

    /** The same two behaviors, declared the other way round: the same bodies, numbered from the
     *  other end. */
    private static final String DECLARED_THE_OTHER_WAY = """
            module demo

            let picked (n: Int, cap: Int): List<Int> = [ n | n >= 240, n <= cap ]

            behavior graded : (n: Int) -> Int
            let graded (n) = if n >= 1 then 1 else 2

            behavior over : (a: Int, b: Int) -> List<Int>
            let over (a, b) = picked(a, b) ++ picked(b, a)
            """;

    /** Two comparisons, the other way round. The same places and the same shapes: what differs is
     *  which comparison stands at which of them. */
    private static final String A_THEN_B = """
            module demo

            behavior f : (a: Int, b: Int) -> Int
            let f (a, b) = if a >= 1 && b >= 2 then 1 else 2
            """;

    private static final String B_THEN_A = A_THEN_B.replace(
            "if a >= 1 && b >= 2", "if b >= 2 && a >= 1");

    // --- one numbering ------------------------------------------------------------------------

    @Test
    void aNumberingIsTheOneAnotherCheckOfTheSameSourceComesTo() {
        assertEquals(numberingOf(MODEL), numberingOf(MODEL),
                "two compiles of one source number its bodies alike");
    }

    @Test
    void andItDoesNotMoveWhenTheSourceMovesUnderIt() {
        assertEquals(numberingOf(MODEL), numberingOf(MOVED),
                "a blank line and a comment above the bodies leave what they do and where their"
                        + " places are alone");
    }

    @Test
    void norWhenTheModuleCountsItsConstructsFromSomewhereElse() {
        NumberingIdentity plain = numberingOf(MODEL);
        NumberingIdentity behind = numberingOf(AFTER_ANOTHER);

        assertEquals(plain.executable().get("over"), behind.executable().get("over"),
                "a declaration above them does not change what the bodies below it do");
        assertEquals(plain.executable().get("graded"), behind.executable().get("graded"),
                "and the same of the other one");
    }

    // --- two numberings -----------------------------------------------------------------------

    @Test
    void twoBodiesThatCompareTheOtherWayAreTwoNumberings() {
        assertNotEquals(numberingOf(A_THEN_B), numberingOf(B_THEN_A),
                "the same places, with the other comparison standing at each of them");
    }

    /**
     * And declaring the behaviors the other way round is two numberings.
     *
     * <p>What the two halves are for, one each. The bodies do the same things, so the executable
     * half is equal and says nothing; what moved is which number went to which place, and the
     * addresses say so. A numbering that held only what the bodies do would call these one and let
     * a recording of the first be read under the second.
     */
    @Test
    void andSoIsNumberingThemFromTheOtherEnd() {
        NumberingIdentity one = numberingOf(MODEL);
        NumberingIdentity other = numberingOf(DECLARED_THE_OTHER_WAY);

        assertEquals(one.executable(), other.executable(),
                "the bodies do the same things whichever order they are declared in");
        assertNotEquals(one.byNumber(), other.byNumber(),
                "and the numbers went to other places");
        assertNotEquals(one, other, "so it is another numbering");
    }

    /** And a number is an address of an arm or of a comparison, never of both. */
    @Test
    void eachNumberIsAnAddressOfOneOrTheOther() {
        NumberingIdentity numbering = numberingOf(MODEL);
        Map<String, Integer> byFamily = new LinkedHashMap<>();
        for (int n = 0; n < numbering.byNumber().size(); n++) {
            byFamily.merge(numbering.at(n).getClass().getSimpleName(), 1, Integer::sum);
        }

        assertTrue(byFamily.containsKey("Arm") && byFamily.containsKey("Comparison"),
                () -> "both families are handed numbers out of the one counter: " + byFamily);
    }

    /**
     * The numbering one check of {@code source} issued.
     *
     * <p>Read off the answer rather than walked for here. The check makes the plan of a module and
     * keeps it, so a walk of its bodies taken here would be a second plan of that module — the one
     * thing the addresses are filed by identity to make impossible to confuse. What this compares
     * is what the check itself came to.
     */
    private static NumberingIdentity numberingOf(String source) {
        Compilation compilation = Compilation.ofSources(List.of(source), ModulePath.EMPTY);
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Bodies.Elaborated checked =
                compilation.db().ask(new Bodies.Checked(module)).value();
        assertTrue(checked != null,
                () -> "the model under test compiled to nothing: " + compilation.errors());
        return checked.numberingIdentity();
    }
}
