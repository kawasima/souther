package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.partition.LineOrigin;
import souther.compiler.query.Adequacy;
import souther.compiler.query.BorderAssessment;
import souther.compiler.query.Compilation;
import souther.compiler.query.Front;
import souther.compiler.query.Shapes;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A conjunct states as many lines as the reading arrives at statements inside it, and each of them
 * is a line of the model in its own right.
 *
 * <p>A denial is carried to the leaves as a clause is read, so a conjunction an author wrote as a
 * denied choice is one conjunct stating one comparison per branch. Named by the conjunct, the second
 * of them is the first said again: a report that looks up what to call a line finds whichever was
 * written down last, and an accounting that counts lines counts one where the author drew two.
 *
 * <p><b>What tells the two apart is the statement and nothing beside it.</b> The facts of a line —
 * which side the rule keeps and whether it admits the value it stops at — are the same for two lower
 * bounds on two numbers, so a reader holding those is holding one line twice. Which number each is
 * on would tell them apart and is no part of a line: a clause is read once per position carrying the
 * type, and the coordinate is spelt differently by each of those readings.
 */
class OneConjunctStatesAsManyLinesAsItHasStatementsTest {

    /**
     * The denied spelling draws two lines, and a report names each of them for the number it is on.
     *
     * <p>The case the identity is for. Both are lower bounds admitting their own value, so
     * {@link souther.compiler.partition.LineFacts} says the same of both; they are about two
     * numbers, and one conjunct wrote them.
     */
    @Test
    void aConjunctWrittenUnderADenialDrawsALineOnEachNumberItStops() {
        Map<String, LineOrigin> lines = linesOf(DENIED);
        LineOrigin name = lineAt(lines, "String.length(p.name) = 1");
        LineOrigin code = lineAt(lines, "String.length(p.code) = 1");

        assertEquals(part(name), part(code), "one conjunct wrote both");
        assertNotEquals(name.authoredLine(), code.authoredLine(),
                "and they are two lines of the model, which is what a row at either is owed to");

        DeclaredBorders borders = declaredBy(DENIED, "Pair");
        assertEquals("String.length(name)", borders.nameOf(drawnBy(name)));
        assertEquals("String.length(code)", borders.nameOf(drawnBy(code)));
    }

    /**
     * And the spelling with {@code &&} draws the same two, which is what the two spellings agreeing
     * means.
     *
     * <p>Here the conjuncts differ, so this passed while the identity was the conjunct alone. Read
     * beside the case above, it says the answer does not turn on which spelling the author chose.
     */
    @Test
    void andTheSpellingWithAndDrawsTheSameTwo() {
        Map<String, LineOrigin> lines = linesOf(PLAIN);
        LineOrigin name = lineAt(lines, "String.length(p.name) = 1");
        LineOrigin code = lineAt(lines, "String.length(p.code) = 1");

        assertNotEquals(part(name), part(code), "the author wrote two conjuncts here");
        assertNotEquals(name.authoredLine(), code.authoredLine(), "and they are two lines");

        DeclaredBorders borders = declaredBy(PLAIN, "Pair");
        assertEquals("String.length(name)", borders.nameOf(drawnBy(name)));
        assertEquals("String.length(code)", borders.nameOf(drawnBy(code)));
    }

    /**
     * Two ends of one conjunct on one number, which the facts of a line do tell apart.
     *
     * <p>The control for the first case. A minimum and a maximum differ in which side they keep, so
     * a reader holding the facts alone tells these two apart and is no worse off for it — which is
     * why the first case is the one that says the statement is needed.
     */
    @Test
    void aConjunctStoppingOneNumberAtBothEndsDrawsTwoLinesThere() {
        Map<String, LineOrigin> lines = linesOf(DENIED);
        LineOrigin bottom = lineAt(lines, "r.v = 1");
        LineOrigin top = lineAt(lines, "r.v = 10");

        assertEquals(part(bottom), part(top), "one conjunct wrote both");
        assertNotEquals(bottom.authoredLine(), top.authoredLine(), "and they are two lines");
        assertNotEquals(bottom.lineFacts(), top.lineFacts(),
                "which the facts of the line say here, unlike two ends on two numbers");
    }

    /**
     * One statement read at two positions is one line of the model.
     *
     * <p>The other half of what an identity is for. A clause is read once per position carrying the
     * type, and a row is owed for what the author wrote rather than for how far the type travelled —
     * so the readings of one statement come back as one authored line.
     */
    @Test
    void oneStatementReadAtTwoPositionsIsOneLine() {
        Map<String, LineOrigin> lines = linesOf(DENIED);
        LineOrigin here = lineAt(lines, "String.length(p.name) = 1");
        LineOrigin there = lineAt(lines, "String.length(q.name) = 1");

        assertEquals(here.authoredLine(), there.authoredLine(),
                "one statement, read at two positions, is one line and one debt");
    }

    /**
     * A conjunct whose statements move an end none of them places draws the conjunct's own line.
     *
     * <p>{@code apart} states two disequalities, neither of which stops the values anywhere: what
     * leaves the number starting at two is the pair of them, and the reading that finds it took the
     * whole conjunct away. So the line is the conjunct's, and saying it was one statement's would be
     * a claim nothing tested.
     */
    @Test
    void aConjunctThatMovesAnEndNoStatementPlacesDrawsItsOwnLine() {
        // Both conjuncts account for where the value starts, so the end carries a line apiece: the
        // one `v >= 0` states, and the one `apart` draws between the two disequalities under it.
        List<DeclaredLine> drawn = drawnAt(DENIED, "h.v = 2");
        RuleRef.Invariant holes = drawn.get(0).part().rule();
        PartId<RuleRef.Invariant> apart = new PartId<>(holes, 0);
        PartId<RuleRef.Invariant> floor = new PartId<>(holes, 1);

        assertEquals(java.util.Set.of(
                        new DeclaredLine.OfAChoice(java.util.Set.of(
                                new InvariantStatementId(apart, 0),
                                new InvariantStatementId(apart, 1))),
                        new DeclaredLine.OfAStatement(new InvariantStatementId(floor, 0))),
                java.util.Set.copyOf(drawn),
                "what `apart` accounts for is the conjunct's line, drawn between the statements"
                        + " about the number, and neither of them alone drew it");
    }

    private static final String DENIED = """
            module example.forms

            data Pair = { name: String, code: String }
                invariant both = Bool.not(String.length(name) < 1 || String.length(code) < 1)

            data Range = { v: Int }
                invariant within = Bool.not(v < 1 || v > 10)

            let apart (n: Int) = n /= 0 && n /= 1

            data Holes = { v: Int }
                invariant holes = apart(v) && v >= 0

            data Ok

            behavior g : (p: Pair, q: Pair) -> Ok
            let g (p, q) = Ok

            behavior h : (r: Range) -> Ok
            let h (r) = Ok

            behavior k : (h: Holes) -> Ok
            let k (h) = Ok

            example g
                | "a" : (Pair { name = "x", code = "y" }, Pair { name = "x", code = "y" }) -> Ok

            example h
                | "a" : (Range { v = 5 }) -> Ok

            example k
                | "a" : (Holes { v = 5 }) -> Ok
            """;

    private static final String PLAIN = """
            module example.forms

            data Pair = { name: String, code: String }
                invariant both = String.length(name) >= 1 && String.length(code) >= 1

            data Ok

            behavior g : (p: Pair, q: Pair) -> Ok
            let g (p, q) = Ok

            example g
                | "a" : (Pair { name = "x", code = "y" }, Pair { name = "x", code = "y" }) -> Ok
            """;

    /** Every line the model draws, by what a report calls it. */
    private static Map<String, LineOrigin> linesOf(String model) {
        Compilation compilation = compiled(model);
        Map<String, List<BorderAssessment>> boundaries =
                Adequacy.boundariesOf(compilation.db(), "example.forms");
        assertNotNull(boundaries, "the model under test compiles");
        Map<String, LineOrigin> out = new LinkedHashMap<>();
        boundaries.values().forEach(each ->
                each.forEach(line -> out.put(line.label(), line.border().origin())));
        return out;
    }

    private static Compilation compiled(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return compilation;
    }

    /** Every line drawn at {@code label}, which is more than one where two rules stop the values in
     *  the same place. */
    private static List<DeclaredLine> drawnAt(String model, String label) {
        Compilation compilation = compiled(model);
        Map<String, List<BorderAssessment>> boundaries =
                Adequacy.boundariesOf(compilation.db(), "example.forms");
        assertNotNull(boundaries, "the model under test compiles");
        List<DeclaredLine> out = new java.util.ArrayList<>();
        boundaries.values().forEach(each -> each.stream()
                .filter(line -> line.label().equals(label))
                .forEach(line -> out.add(drawnBy(line.border().origin()))));
        org.junit.jupiter.api.Assertions.assertFalse(out.isEmpty(),
                () -> label + " is not a line of the model");
        return out;
    }

    private static LineOrigin lineAt(Map<String, LineOrigin> lines, String label) {
        LineOrigin origin = lines.get(label);
        assertNotNull(origin, () -> label + " is not a line of the model: " + lines.keySet());
        return origin;
    }

    /** What the reading knows about the clause that drew the line. */
    private static DeclaredLine drawnBy(LineOrigin origin) {
        return ((LineOrigin.InvariantOrigin) origin).drawnBy();
    }

    private static PartId<RuleRef.Invariant> part(LineOrigin origin) {
        return drawnBy(origin).part();
    }

    /** The lines {@code name} draws, in its own terms. */
    private static DeclaredBorders declaredBy(String model, String name) {
        Compilation compilation = compiled(model);
        String module = compilation.modules().get(0);
        ReadingPolicy policy = compilation.db().ask(new Front.Reading()).value();
        TypeSymbol named = TypeSymbols.declared(new TypeKey("example.forms", name));
        return DeclaredBorders.of(named, Shapes.publishedDeclarations(compilation.db()),
                Shapes.declarationCitations(compilation.db()),
                RuleReadings.of(compilation, module), policy);
    }
}
