package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.query.Compilation;
import souther.compiler.query.Front;
import souther.compiler.query.Shapes;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A line printed under the declaration that drew it is named in the terms that declaration wrote,
 * and not in the terms of whichever behavior a reading of it reached.
 *
 * <p>{@code UserId}'s clause draws one line. The behaviors carrying the type meet it at
 * {@code draft.owner}, at {@code activities[*]@CallTask.owner} and at seventy-two other positions,
 * and none of those is what the author wrote — so a debt named after a reading is named after
 * whichever reading a report happened to reach first (issue #1062).
 */
class ALineIsNamedInTheTermsItWasWrittenInTest {

    /**
     * A newtype's clause is about the value it wraps, however many positions carry it.
     *
     * <p>The frame is the declaration's, so the answer says {@code value} and never a path through
     * some record. Read from a reading instead, this is where the two frames of one line differ.
     */
    @Test
    void aNewtypesClauseIsAboutTheValueItWraps() {
        var line = lineAt("String.length(u) = 1");
        assertEquals("String.length(value)",
                declaredBy("UserId").nameOf(line.drawnBy()));
    }

    /**
     * A clause bounding two numbers names each of them, told apart by the conjunct that drew it.
     *
     * <p>The pair that has to be told apart at all. Both ends are at 1, on one carrier, from one
     * clause: nothing but which conjunct placed them says which line is which, and a report that
     * could not say it would print one item twice and leave the author to guess.
     */
    @Test
    void aClauseBoundingTwoNumbersNamesEachOfThem() {
        DeclaredBorders lines = declaredBy("Pair");
        var name = lineAt("String.length(p.name) = 1");
        var code = lineAt("String.length(p.code) = 1");
        assertEquals("String.length(name)", lines.nameOf(name.drawnBy()));
        assertEquals("String.length(code)", lines.nameOf(code.drawnBy()));
    }

    /** Both ends of a range are the one number, which is what tells this from the case above. */
    @Test
    void bothEndsOfARangeAreTheOneNumber() {
        DeclaredBorders lines = declaredBy("Range");
        var bottom = lineAt("r = 1");
        var top = lineAt("r = 10");
        assertEquals("value", lines.nameOf(bottom.drawnBy()));
        assertEquals("value", lines.nameOf(top.drawnBy()));
        org.junit.jupiter.api.Assertions.assertNotEquals(bottom.part(), top.part(),
                "the two ends are the one number and different lines");
    }

    /**
     * A declaration answers for the clauses it wrote and not for the ones it holds.
     *
     * <p>A line is named by the rule that drew it (ADR-0090), so {@code Span} has nothing to say
     * about {@code Day}'s clause even though every value of {@code Span} is held to it. Answered
     * here, the same line would be named twice and in two frames.
     */
    @Test
    void aDeclarationAnswersForTheClausesItWrote() {
        var line = lineAt("s.d = 0");
        assertNotNull(declaredBy("Day").at(line.drawnBy()),
                "Day wrote the clause, so Day names the line");
        assertNull(declaredBy("Span").at(line.drawnBy()),
                "and Span holds a value that is held to it, which is not the same thing");
    }

    private static final String MODEL = """
            module example.forms

            data UserId = String
                invariant nonempty = String.length(value) >= 1

            data Pair = { name: String, code: String }
                invariant both = String.length(name) >= 1 && String.length(code) >= 1

            data Range = Int
                invariant within = value >= 1 && value <= 10

            data Day = Int
                invariant days = value >= 0 && value <= 6

            data Span = { d: Day }

            data Ok

            behavior f : (u: UserId) -> Ok
            let f (u) = Ok

            behavior g : (p: Pair) -> Ok
            let g (p) = Ok

            behavior h : (r: Range) -> Ok
            let h (r) = Ok

            behavior k : (s: Span) -> Ok
            let k (s) = Ok

            example f
                | "a" : (UserId("x")) -> Ok

            example g
                | "a" : (Pair { name = "x", code = "y" }) -> Ok

            example h
                | "a" : (Range(5)) -> Ok

            example k
                | "a" : (Span { d = Day(1) }) -> Ok
            """;

    /** Every line the model draws, by what a report calls it. Read once: the whole point of the
     *  test is that both sides of the lookup come from the one compile. */
    private static final Map<String, souther.compiler.partition.LineOrigin> LINES = linesOf();

    private static Map<String, souther.compiler.partition.LineOrigin> linesOf() {
        Compilation compilation = compiled();
        Map<String, List<souther.compiler.query.BorderAssessment>> boundaries =
                souther.compiler.query.Adequacy.boundariesOf(compilation.db(), "example.forms");
        assertNotNull(boundaries, "the model under test compiles");
        Map<String, souther.compiler.partition.LineOrigin> out = new java.util.LinkedHashMap<>();
        boundaries.values().forEach(each ->
                each.forEach(line -> out.put(line.label(), line.border().origin())));
        return out;
    }

    private static Compilation compiled() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.measure(souther.compiler.query.Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return compilation;
    }

    /**
     * The rule and the conjunct of the line at {@code label}, as the measurement met it.
     *
     * <p>Taken from the measurement rather than built here, because that is what a caller holds: a
     * report reaches this with a debt the readings produced. Built in the test, the two sides could
     * key their answers differently and nothing would say so — which they did, over the name the
     * author gave the clause.
     */
    private static souther.compiler.partition.LineOrigin.InvariantOrigin lineAt(String label) {
        souther.compiler.partition.LineOrigin origin = LINES.get(label);
        assertNotNull(origin, () -> label + " is not a line of the model: " + LINES.keySet());
        return (souther.compiler.partition.LineOrigin.InvariantOrigin) origin;
    }

    /** The lines {@code name} draws, in its own terms. */
    private static DeclaredBorders declaredBy(String name) {
        Compilation compilation = compiled();
        String module = compilation.modules().get(0);
        ReadingPolicy policy = compilation.db().ask(new Front.Reading()).value();
        TypeSymbol named = TypeSymbols.declared(new TypeKey("example.forms", name));
        return DeclaredBorders.of(named, Shapes.publishedDeclarations(compilation.db()),
                Shapes.declarationCitations(compilation.db()),
                RuleReadings.of(compilation, module), policy);
    }
}
