package souther.compiler.partition;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A rule may name a position the reading of an input has none for, and that is not an error.
 *
 * <p>The walk enumerates what positions a type can have and stops where a path returns to a
 * declaration already open on it, because that question has no other end. Nothing stops a rule from
 * naming what is under that: the reader that turns an expression into a path follows as many steps
 * as are written, and a written path is as long as the author made it. So a term at a path the
 * reading has no position for is an ordinary thing to be handed, and the line drawn on it is
 * measured where the model wrote it.
 *
 * <p>Told apart from a term of some other behavior's input, which is a caller's mistake and is
 * refused as one. Both are "no position of this input is at that path", and only one of them is
 * anybody's fault: a path under a parameter of this input belongs to it however far down it goes,
 * and what is unknown there is what the rules relate it to rather than whose it is.
 */
class ATermMayNameAPositionTheWalkStoppedShortOfTest {

    /** Two fields of the link below the one the walk stopped at, compared against each other. The
     *  line is between two positions the reading never enumerated. */
    private static final String BELOW_WHERE_THE_READING_STOPS = """
            module example.deep

            data Nil
            data Cons = { a: Int, b: Int, tail: Chain }
            data Chain = Nil | Cons

            data No = { why: Int }
            data Yes = { v: Int }
            data Result = No | Yes

            behavior f : (c: Chain) -> Result
                constructs Yes, No
            let f (c) =
                match c with
                    | Nil -> No { why = 0 }
                    | Cons as k -> match k.tail with
                        | Nil -> No { why = 1 }
                        | Cons as m -> {
                            guard m.a < m.b else No { why = 2 }
                            Yes { v = 1 }
                          }

            example f
                | "under" : (Cons { a = 0, b = 0, tail = Cons { a = 1, b = 2, tail = Nil } })
                    -> Yes { v = 1 }
            """;

    /**
     * The line is drawn where the model named it, under the position the reading stopped at.
     *
     * <p>Where the reading stopped is
     * {@code TheWalkStopsWhereTheInputReturnsToADeclarationTest}'s to say; what is asked here is
     * that the line is measured all the same.
     */
    @Test
    void aLineBetweenTwoPositionsBelowWhereTheReadingStopsIsStillMeasured() {
        String report = report(BELOW_WHERE_THE_READING_STOPS);

        assertTrue(report.contains("borders 1"), report);
        assertTrue(report.contains("read as f/c@Cons.tail@Cons.a: = c@Cons.tail@Cons.b"), report);
    }

    /**
     * And a row is asked for at it, because the position is one the reading has.
     *
     * <p>Which is what following the path is worth. A line drawn at a place the reading knows
     * nothing about is a line no search can compose a row for, and an author reading such a report
     * is told a point exists and never told what to write. Here the point is owed like any other.
     */
    @Test
    void aRowIsAskedForAtThePointTheLineDraws() {
        String report = report(BELOW_WHERE_THE_READING_STOPS);

        assertTrue(report.contains("no row is at the OFF point ("), report);
        assertTrue(report.contains("read as f/c@Cons.tail@Cons.a: = c@Cons.tail@Cons.b"), report);
    }

    /** The same two fields, held one apart, which is a rule no operand of the comparison names. */
    private static final String REWRITTEN_BY_THE_ARITHMETIC = BELOW_WHERE_THE_READING_STOPS
            .replace("m.a < m.b", "m.a + 1 < m.b");

    /**
     * And so is one the arithmetic had to rewrite to find the two positions in.
     *
     * <p>Which is the case that has nowhere else to get the answer. A comparison written between the
     * two positions themselves names each of them with an operand, and something of the position can
     * be had from what the checker gave that operand; {@code a + 1 < b} names neither, so the order
     * each position is counted on is the reading of the declarations' to say or nobody's.
     *
     * <p>That reading stops where the path returns to a declaration and the declarations do not, so
     * it follows the type the rest of the way down. Made to stop where the positions stop — a null
     * for a path with no position, which reads like tidying up — this line is not drawn at all.
     */
    @Test
    void soIsALineTheArithmeticHadToRewriteToFind() {
        String report = report(REWRITTEN_BY_THE_ARITHMETIC);

        // The line is where `a` is one below `b`, so the point on it is the pair two apart: the
        // rule is written `<` and the last pair satisfying it is the one before they are one apart.
        assertTrue(report.contains("read as f/c@Cons.tail@Cons.a: = c@Cons.tail@Cons.b - 2"),
                report);
    }

    /** Two fields of what a list holds, compared inside a closure. The path reaches them through
     *  what the sequence holds, which is a step of its own and not a field. */
    private static final String UNDER_WHAT_A_SEQUENCE_HOLDS = """
            module example.held

            data Inner = { a: Int, b: Int }
            data Item  = { inner: Inner }
            data Cart  = { items: List<Item> }

            data No = { why: Int }
            data Yes = { v: Int }
            data Result = No | Yes

            behavior f : (cart: Cart) -> Result
                constructs Yes, No
            let f (cart) = {
                guard List.all(i -> i.inner.a < i.inner.b, cart.items) else No { why = 0 }
                Yes { v = 1 }
            }
            """;

    /**
     * And so is one under what a sequence holds, which is a step of its own.
     *
     * <p>A second kind of step and the reason the reading of a path is exhaustive over them. A path
     * goes into a field, into what a sequence holds, or nowhere while narrowing where it already is;
     * written for fields alone, the reading answered nothing for every path carrying one of the
     * other two, and this line — which the compiler drew before any of this — went away.
     *
     * <p><b>What this asks for moved, and why.</b> It used to be that the report named these points
     * as ones nothing could build a representative for: the composer read a position's declared type
     * off a walk of its own that stopped at the parameter, so a position three fields and a sequence
     * down had none. It reads the declarations now, so the points have rows — which is the same
     * claim about the line being measured, made by what the line came to rather than by the sentence
     * saying it came to nothing.
     */
    @Test
    void andSoIsOneUnderWhatASequenceHolds() {
        String report = report(UNDER_WHAT_A_SEQUENCE_HOLDS);
        assertTrue(report.contains("borders 1"), report);
        assertFalse(report.contains("nothing here could build a representative"), report);

        assertTrue(offered(UNDER_WHAT_A_SEQUENCE_HOLDS)
                        .contains("Cart { items = [Item { inner = Inner { a = -1, b = 0 } }] }"),
                offered(UNDER_WHAT_A_SEQUENCE_HOLDS));
    }

    /** What {@code --generate --boundaries} offers for the model, which is where a point that is
     *  writable says so. */
    private static String offered(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return souther.compiler.report.GeneratedRows.of(compilation, "example.held", null, true,
                SourceRendering.namedByIdentity(SourceLayouts.NONE)).text();
    }

    private static String report(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).human(SourceRendering.namedByIdentity(SourceLayouts.NONE));
    }
}
