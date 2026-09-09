package souther.compiler.inputs;

import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredSig;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.RuleKey;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A field every case of a sum spreads is readable on a value of the sum, and a row writes it under
 * one case. So the name is at the sum, the positions are under its cases, and the reading says which
 * positions the name stands at.
 *
 * <p>What makes a name cross is the same answer that makes it readable — the declarations the cases
 * share. A field one case has and another has not is not readable on the sum, and does not cross
 * here either.
 */
class ANameTheCasesShareStandsUnderEachCaseTest {

    private static final String SHARED = """
            module g

            data Paging = { limit: Int }
            data A = { ...Paging, x: Int }
            data B = { ...Paging, y: Int }
            data Q = A | B

            data Ok

            behavior read : (q: Q) -> Ok
            behavior readOne : (q: A) -> Ok
            """;

    /** Two sums on the way down, so that a name crossing both is asked of what the first left. */
    private static final String NESTED = """
            module g

            data Inner = { deep: Int }
            data IA = { ...Inner, p: Int }
            data IB = { ...Inner, r: Int }
            data IS = IA | IB

            data Outer = { s: IS }
            data OA = { ...Outer, m: Int }
            data OB = { ...Outer, n: Int }
            data OS = OA | OB

            data Ok

            behavior read : (q: OS) -> Ok
            """;

    /**
     * The name stands at one position under each case.
     *
     * <p>And at no position of its own. A row states a value where a value is written, and what the
     * shared field may hold is settled by which case the row picked — so the sum is not a second
     * place the same field is asked for.
     */
    @Test
    void aSharedNameStandsUnderEachCase() {
        InputDomain read = reading(SHARED, "read");

        assertEquals(List.of("q@A.limit", "q@B.limit"),
                spelled(read.positionsNamed(TermPath.of("q"), RuleKey.of("limit"))));
        assertFalse(read.positions().stream().anyMatch(each -> "q.limit".equals(each.path().toString())),
                "the shared field is named at the sum and is a position under each of its cases");
    }

    /**
     * A field one case declares of its own does not cross.
     *
     * <p>The language will not read {@code q.x} on a value of the sum, and neither will this: what
     * crosses is what the cases share, and taking the answer from what a case declares would let a
     * rule reach a position through a name nothing can write.
     */
    @Test
    void aNameOnlyOneCaseHasCrossesNowhere() {
        InputDomain read = reading(SHARED, "read");

        assertEquals(List.of(), read.positionsNamed(TermPath.of("q"), RuleKey.of("x")));
    }

    /** An ordinary name is where it always was: the position of that name one step down. */
    @Test
    void anOrdinaryNameIsTheOneStepDown() {
        InputDomain read = reading(SHARED, "readOne");

        assertEquals(List.of("q.limit"),
                spelled(read.positionsNamed(TermPath.of("q"), RuleKey.of("limit"))));
        assertTrue(read.reach().crossings().isEmpty(), "no sum stands anywhere in this input");
    }

    /**
     * Two sums on the way and the name stands at every pairing of their cases.
     *
     * <p>Which is what a name is worth taking a step at a time for. Crossing at the first sum
     * settles which positions the second step is asked of, and a reading that rewrote the name and
     * looked the types up again would have to answer for the pairing itself.
     */
    @Test
    void aNameCrossingTwoSumsStandsAtEveryPairingOfTheirCases() {
        InputDomain read = reading(NESTED, "read");

        assertEquals(List.of("q@OA.s@IA.deep", "q@OA.s@IB.deep",
                        "q@OB.s@IA.deep", "q@OB.s@IB.deep"),
                spelled(read.positionsNamed(TermPath.of("q"),
                        new RuleKey(List.of("s", "deep")))));
    }

    /**
     * A name below where this reading stops crosses nowhere.
     *
     * <p>A crossing is written as the position it names is made, so a field the walk never got to
     * leaves the name with nowhere to stand rather than with a position nobody made. What that
     * absence means is for whoever asks; what it may not do is answer as though the position were
     * there.
     */
    @Test
    void aNameBelowWhereTheReadingStopsCrossesNowhere() {
        InputDomain read = reading("""
                module g

                data Paging = { limit: Int }
                data A = { ...Paging, x: Int }
                data B = { ...Paging, deeper: Q }
                data Q = A | B

                data Outer = { q: Q }

                data Ok

                behavior read : (o: Outer) -> Ok
                """, "read");

        // The sum under `B` is where the input returns to `Q`, so the reading stops before its
        // cases and the name a case would carry stands nowhere.
        TermPath returns = pathOf(read, "o.q@B.deeper");
        assertEquals(List.of(), read.positionsNamed(returns, RuleKey.of("limit")));
        assertTrue(read.reach().crossings().stream().noneMatch(each -> each.at().equals(returns)),
                "the reading stopped there, so its cases put no field anywhere: "
                        + read.reach().crossings());
        // And the sum this one returns to was entered, so the absence above is the stop and not a
        // name that crosses nowhere in this model.
        assertEquals(List.of("o.q@A.limit", "o.q@B.limit"),
                spelled(read.positionsNamed(TermPath.of("o"),
                        new RuleKey(List.of("q", "limit")))));
    }

    /** The position this reading made at {@code spelled}. */
    private static TermPath pathOf(InputDomain read, String spelled) {
        return read.positions().stream().map(Position::path)
                .filter(each -> each.toString().equals(spelled))
                .findFirst().orElseThrow(() -> new AssertionError(
                        "no position at " + spelled + " among " + read.positions().stream()
                                .map(Position::path).toList()));
    }

    /** The paths, spelled the way a report names them. */
    private static List<String> spelled(List<TermPath> paths) {
        return paths.stream().map(TermPath::toString).toList();
    }

    private static InputDomain reading(String source, String behavior) {
        Compilation compilation =
                Compilation.ofSources(List.of(source), souther.compiler.meta.ModulePath.EMPTY);
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        return InputDomain.of(sigs.get(behavior), rules, ReadAs.THE_COMPILATION_DOES);
    }
}
