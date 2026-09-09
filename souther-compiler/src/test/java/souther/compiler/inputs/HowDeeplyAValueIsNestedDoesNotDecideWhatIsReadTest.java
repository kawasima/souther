package souther.compiler.inputs;

import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredSig;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every path that opens no declaration twice is followed to its end, however many steps that is.
 *
 * <p>The reading used to stop after counting two steps, so whether a rule an author wrote was
 * measured turned on how deeply they had nested the value it was about. A rule at the bottom of an
 * expense claim — an amount, in a category, in a line, in a list — drew no line, and the sister
 * model whose amount sat one step higher had the same rule measured.
 *
 * <p>What ends a path is a fact about the declarations and not a number: the path returns to a
 * declaration already open on it ({@link ExpansionTrace}). So nesting decides nothing here, and the
 * tests below are about the two ways a count would have been wrong — a long path, and one type
 * standing at more than one place.
 */
class HowDeeplyAValueIsNestedDoesNotDecideWhatIsReadTest {

    /** Ten records deep, with the position that divides at the bottom of them. */
    private static final String TEN = """
            module g

            data Red
            data Blue
            data Colour = Red | Blue

            data L10 = { c: Colour }
            data L9 = { d: L10 }
            data L8 = { d: L9 }
            data L7 = { d: L8 }
            data L6 = { d: L7 }
            data L5 = { d: L6 }
            data L4 = { d: L5 }
            data L3 = { d: L4 }
            data L2 = { d: L3 }
            data L1 = { d: L2 }

            data Ok

            behavior read : (x: L1) -> Ok
            """;

    /**
     * The colour ten records down is a position, and it divides.
     *
     * <p>Ten and not three, so that a limit put back at some larger number fails here rather than
     * passing for having been raised past whatever the last model needed.
     */
    @Test
    void aPositionTenRecordsDownIsRead() {
        InputDomain read = reading(TEN, "read");
        Position bottom = read.at(TermPath.of("x").then("d").then("d").then("d").then("d")
                .then("d").then("d").then("d").then("d").then("d").then("c"));

        assertNotNull(bottom, () -> "every step of it opens a declaration once: "
                + spelled(read));
        assertEquals(2, bottom.obligationCases().size(), "and the sum there divides two ways");
    }

    /** Two fields of one record declared as the same type. */
    private static final String SIBLINGS = """
            module g

            data Postcode = { code: String }
            data Address = { post: Postcode }
            data Order = { from: Address, to: Address }

            data Ok

            behavior read : (o: Order) -> Ok
            """;

    /**
     * A type standing at two places is read at both.
     *
     * <p>What the walk carries down is the ancestors of one path and not everywhere it has been.
     * Held as one set for the whole walk, the second address would come back as a path returning to
     * a declaration already open, and a model with two addresses in it would have one of them
     * measured.
     */
    @Test
    void twoFieldsOfOneTypeAreBothRead() {
        InputDomain read = reading(SIBLINGS, "read");

        assertNotNull(read.at(TermPath.of("o").then("from").then("post").then("code")),
                () -> spelled(read));
        assertNotNull(read.at(TermPath.of("o").then("to").then("post").then("code")),
                () -> spelled(read));
    }

    /** One type reached down two different paths, which is the same question one level up. */
    private static final String DIAMOND = """
            module g

            data Leaf = { n: Int }
            data Left = { leaf: Leaf }
            data Right = { leaf: Leaf }
            data Top = { l: Left, r: Right }

            data Ok

            behavior read : (t: Top) -> Ok
            """;

    @Test
    void oneTypeReachedDownTwoPathsIsReadOnBoth() {
        InputDomain read = reading(DIAMOND, "read");

        assertNotNull(read.at(TermPath.of("t").then("l").then("leaf").then("n")),
                () -> spelled(read));
        assertNotNull(read.at(TermPath.of("t").then("r").then("leaf").then("n")),
                () -> spelled(read));
    }

    /**
     * And a reading that stops nowhere says so at no position.
     *
     * <p>The other half of the claim above: the positions are there, and none of them came back
     * carrying a reason for having gone no further.
     */
    @Test
    void nothingInAnAcyclicModelStopsTheWalk() {
        for (String source : List.of(TEN, SIBLINGS, DIAMOND)) {
            InputDomain read = reading(source, "read");
            assertTrue(read.positions().stream().noneMatch(
                            each -> BlockedDescent.of(each.structure()) != null),
                    () -> "no path here opens a declaration twice: " + spelled(read));
        }
    }

    /** The positions, spelled the way a report names them. */
    private static String spelled(InputDomain read) {
        return read.positions().stream().map(each -> each.path().toString()).toList().toString();
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
