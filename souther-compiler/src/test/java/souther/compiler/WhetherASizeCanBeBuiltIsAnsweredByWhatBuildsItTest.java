package souther.compiler;

import org.junit.jupiter.api.Test;

import souther.compiler.partition.Generator;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Rows at the edges a model draws on a measure taken of a value.
 *
 * <p>A line at {@code String.length = 1} is met by a string of one character, and whether one can be
 * composed is a question about strings. Answered where strings are built, it is an answer every
 * caller has; answered at each caller, it is an assumption that goes on being true only until the
 * builder learns something — which is what happened here, and the boundary went on reporting that
 * nothing composed a value it was by then being handed in another row of the same generation.
 *
 * <p>What is held to is the boundary an author sees, not the helper underneath. A test on the helper
 * would have gone on passing throughout.
 */
class WhetherASizeCanBeBuiltIsAnsweredByWhatBuildsItTest {

    /** A model whose one behavior divides on {@code s}, so that a numeric edge is owed beside whatever
     * the constrained position owes. */
    private static String model(String declaration, String written) {
        return """
                module sz.gen

                data Size = Int
                    invariant value >= 1

                data Tag = Big | Small

                %s

                behavior label : (c: C, s: Size) -> Tag

                let label (c, s) = if s.value >= 5 then Big else Small

                example label
                    | (%s, Size(9)) -> Big
                """.formatted(declaration, written);
    }

    /**
     * What the generator answers at the lines this behavior's values are held to and no row sits on.
     *
     * <p>Both authorities. The lines here are an {@code invariant}'s, so the two points against each
     * of them are the declaration's and are resolved once for the module (issue #1076); the regions
     * either side are this reading's. What a person reading the block beside {@code label} sees is
     * both, and that is what this asks for.
     */
    private static Generator.GenerationResult boundaries(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        assertEquals(List.of(), compilation.diagnostics().values().stream()
                .flatMap(List::stream).toList(), "the model under test compiles");
        assertNotNull(Adequacy.generatedOf(compilation.db(), compilation.modules().get(0)),
                "the rows come back");
        // Both authorities, because the lines here are an `invariant`'s: one is owed once over
        // every behavior carrying the type and is resolved for the module, and what a person
        // reading the block beside `label` sees is that row beside the behavior's own.
        return OfferedAtTheLines.of(compilation, compilation.modules().get(0), "label");
    }

    /** The row offered at {@code edge}, as the text an author is handed for its first input. */
    private static String rowAt(Generator.GenerationResult filled, String edge) {
        for (Generator.GeneratedRow row : filled.rows()) {
            if (row.labels().contains(edge)) {
                return row.inputs().get(0).text();
            }
        }
        return null;
    }

    // --- a string as long as the line says --------------------------------------------------------

    /**
     * The edge a minimum on a length draws is a string of that length.
     *
     * <p>The value is the one the same generation writes into every other row it offers, which is how
     * the claim that nothing composed one could be read off the block that made it.
     */
    @Test
    void aLineOnALengthIsMetByAStringOfThatLength() {
        Generator.GenerationResult filled = boundaries(model("""
                data C = String
                    invariant String.length(value) >= 1
                """, "C(\"abc\")"));

        assertEquals("C(\"x\")", rowAt(filled, "String.length(c) = 1"));
    }

    /** A line above the smallest one is met at its own length rather than at the smallest. */
    @Test
    void aLineFurtherUpIsMetAtItsOwnLength() {
        Generator.GenerationResult filled = boundaries(model("""
                data C = String
                    invariant String.length(value) >= 1 && String.length(value) <= 4
                """, "C(\"ab\")"));

        assertEquals("C(\"xxxx\")", rowAt(filled, "String.length(c) = 4"));
    }

    // --- a collection counted the same way --------------------------------------------------------

    /**
     * A count of elements reaches the same builder a count of characters does.
     *
     * <p>Here so that the fix is about what carries a count rather than about strings. Nothing in the
     * corpora draws this line today, and a repair that worked only for the case that showed the defect
     * would leave the next carrier to find it again.
     */
    @Test
    void aLineOnAnElementCountIsMetByACollectionOfThatSize() {
        Generator.GenerationResult filled = boundaries(model("""
                data C = List<Int>
                    invariant List.length(value) >= 2
                """, "C([1, 2, 3])"));

        assertEquals("C([0, 0])", rowAt(filled, "List.length(c) = 2"));
    }

    // --- the position's own content, unchanged ----------------------------------------------------

    /**
     * A line on the content of a location is still met by writing that number at it.
     *
     * <p>The path this shares nothing with. A number read out of a value and a number counted of one
     * are two questions, and the second learning an answer does not change the first.
     */
    @Test
    void aLineOnTheContentOfALocationIsStillWrittenThere() {
        Generator.GenerationResult filled = boundaries(model("""
                data C = String
                    invariant String.length(value) >= 1
                """, "C(\"abc\")"));

        assertEquals("Size(5)", sizeAt(filled, "s = 5"),
                "the edge on `s` is still the number written at `s`");
        assertEquals("Size(1)", sizeAt(filled, "s = 1"),
                "and so is the edge its invariant draws");
    }

    /** The second input of the row offered at {@code edge}, or null where none was. */
    private static String sizeAt(Generator.GenerationResult filled, String edge) {
        for (Generator.GeneratedRow row : filled.rows()) {
            if (row.labels().contains(edge)) {
                return row.inputs().get(1).text();
            }
        }
        return null;
    }
}
