package souther.compiler.reading;

import org.junit.jupiter.api.Test;

import souther.compiler.check.NumericMeasures;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.Symbols;
import souther.compiler.core.Core;
import souther.compiler.inputs.InputDomain;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.Refinement;
import souther.compiler.inputs.TermPath;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.types.Type;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * What a body writes inside {@code | Some v} is written at the position the reading of the input
 * holds for that narrowing.
 *
 * <p>The integration invariant, and it is a different claim from the one the two vocabularies
 * answer alike. That one is about a narrowing: given an optional, the division its type states and
 * the selector a pattern selecting it carries come to the same {@link Refinement}. This one is
 * about a path: the reading of a body, walking through the arm, arrives at a location the reading
 * of the input can be asked about. Both were true of a sum's arm and only the first is enough to
 * make the second so — a producer can hold the right narrowing and still put it after the wrong
 * steps.
 *
 * <p>What made this worth a test of its own is that the two came apart silently. The reading of an
 * arm built its narrowing from the case's name, which spells an optional's present carrier and a
 * sum's case alike, so a body inside {@code | Some v} wrote at {@code x@Some} carrying a sum's case
 * while the input's reading held {@code x@Some} carrying a presence. Nothing compared the two: most
 * readers look a path up, find nothing and say nothing, and the one that refuses raised in the
 * middle of a report and took the whole model's with it (#1252).
 *
 * <p>Read as the location a comparison is about rather than as a line of a report. What a report
 * prints about such a position is a further question and moves with how it is worded; what may not
 * move is that the two readings meet at one place.
 */
class AnArmOfAnOptionalWritesAtThePositionTheReadingHoldsTest {

    private static final String THROUGH_AN_OPTIONAL = """
            module example.optional

            data Yes
            data No
            data Answer = Yes | No

            data Slot = { c: String }
            data Box = { held: Slot? }

            behavior gate : (b: Box) -> Answer
            let gate (b) =
                match b.held with
                    | Some s -> if String.length(s.c) <= 3 then Yes else No
                    | None -> No
            """;

    /**
     * The position the arm's name stands at.
     *
     * <p>Spelled in the reading's own vocabulary — the distinction an optional's type states — and
     * not in the arm's. What is being claimed is that the two meet, so naming the place from the
     * side the body is compared against leaves the body's side with nothing written here to agree
     * with.
     */
    private static TermPath underSome() {
        return TermPath.of("b").then("held")
                .refine(Refinement.of(new souther.compiler.inputs.Case.Presence(true)))
                .then("c");
    }

    /**
     * The comparison inside the arm is about that position.
     *
     * <p>Which is what the arm's name means: {@code s} is {@code b.held} read as the case it turned
     * out to be, and {@code s.c} is the field of it. A narrowing spelled any other way puts the
     * comparison at a location spelled the same and equal to nothing.
     */
    @Test
    void theComparisonInsideTheArmIsAboutThePositionUnderThePresence() {
        Read read = read(THROUGH_AN_OPTIONAL);

        assertEquals(List.of(read.lengthAt(underSome())),
                read.sides().stream().map(Condition.Side::at).distinct().toList(),
                "the number is the length of the field under the optional's present carrier");
    }

    /** And that position is one the reading of the input holds, which is what makes it a place
     *  anything else can be asked about. */
    @Test
    void thatPositionIsOneTheReadingOfTheInputHolds() {
        Read read = read(THROUGH_AN_OPTIONAL);

        // Said with each narrowing's kind on it, because that is the whole of what goes wrong here:
        // a position spelled `b.held@Some` is in the list either way, and only the kind tells the
        // one the reading holds from the one a body would have written at.
        assertNotNull(read.inputs.at(underSome()),
                () -> "the body writes at a position the reading does not hold: "
                        + read.inputs.positions().stream()
                                .map(each -> each.path().discriminated()).toList());
    }

    /** One model read, with the symbols a term named here is built against. */
    private record Read(CoverageRead.Read read, InputDomain inputs, Symbols symbols) {

        /** Every comparison the reading named, over every way in to every arm. */
        List<Condition.Side> sides() {
            return read.arms().values().stream()
                    .filter(PathAccess.Ways.class::isInstance)
                    .flatMap(access -> ((PathAccess.Ways) access).ways().stream())
                    .flatMap(way -> way.decisions().stream())
                    .map(Decision::constrains)
                    .filter(Condition.Side.class::isInstance)
                    .map(Condition.Side.class::cast)
                    .distinct().toList();
        }

        /** The number a string's length is, built here rather than matched by how it is written. */
        NumericTerm lengthAt(TermPath at) {
            NumericTerm.TakenOf taken = NumericTerm.TakenOf.of(
                    NumericMeasures.takenOf(Type.STRING, souther.compiler.check.ScopedDeclarations.wrapsOf(symbols)),
                at, Type.STRING, souther.compiler.check.ScopedDeclarations.wrapsOf(symbols),
                symbols);
            assertNotNull(taken, "the length of a string is a number this compiler names");
            return taken;
        }
    }

    private static Read read(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Bodies.Elaborated checked = compilation.db().ask(new Bodies.Checked(module)).value();
        assertNotNull(checked, "the model under test compiles");
        Core body = checked.behaviorBodies().get("gate");
        assertNotNull(body, "the behavior under test has a body");
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        InputDomain inputs = compilation.db().ask(new Adequacy.Inputs(module)).value().get("gate");
        assertNotNull(inputs, "the behavior's input is read");
        return new Read(CoverageRead.of("gate", body,
                checked.plan(),
                inputs, rules), inputs, rules.symbols());
    }
}
