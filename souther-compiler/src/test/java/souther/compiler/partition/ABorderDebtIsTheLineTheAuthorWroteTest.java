package souther.compiler.partition;

import souther.compiler.coverage.ComparisonOccurrence;
import souther.compiler.coverage.Numberings;
import souther.compiler.types.WrittenOwner;
import souther.compiler.coverage.SiteNumbering;

import org.junit.jupiter.api.Test;

import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.query.Adequacy;
import souther.compiler.query.BorderAssessment;
import souther.compiler.query.Compilation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * What a row is owed for is the line the author wrote, and neither the rule it came from nor the
 * position it was read at.
 *
 * <p>Four questions and not one. Which rule drew the line is provenance and the same value however
 * many lines the rule drew; which line of that rule it is, and which value it is at, are the debt;
 * where that line was read is an occurrence, one per position of every behavior carrying the type,
 * and which of those readings are one line is what a partition folds under. A measure keyed on the
 * occurrence asks for one rule's row once per position — 126 times over {@code crm}'s
 * {@code UserId} — and a measure keyed on the rule asks for one row where the author drew two lines.
 *
 * <p>How far a debt reaches is the rule's own answer: a clause of a {@code data} is the type's
 * wherever the type is carried, and a comparison is written in a body and is that body's. Both
 * directions are measured here, because a debt that dropped the behavior would pass the first and
 * fail the second.
 */
class ABorderDebtIsTheLineTheAuthorWroteTest {

    /**
     * One clause placing two ends is two debts, and a row at one is not a row at the other.
     *
     * <p>The case a debt keyed on the rule gets wrong. {@code within} is one clause and one
     * {@link souther.compiler.check.RuleRef.Invariant}, and the lines it draws at 1 and at 10 have
     * nothing to say about each other: a row standing at the bottom of the range is no evidence
     * about the top. So the rule is the same value at both and the debt is not.
     */
    @Test
    void aClausesTwoEndsAreTwoDebts() {
        Map<String, BorderAssessment> lines = bordersOf(RANGE, "example.range");
        BorderAssessment bottom = at(lines, "n = 1");
        BorderAssessment top = at(lines, "n = 10");

        assertEquals(bottom.border().obligation().provenance(),
                top.border().obligation().provenance(),
                "one clause drew both, which is what provenance answers");
        assertNotEquals(bottom.border().obligation(), top.border().obligation(),
                "and a row at the bottom of the range is no evidence about the top");
    }

    /**
     * One invariant read at two behaviors is one debt.
     *
     * <p>What issue #1062 is. {@code UserId} says a user id is a string of one character or more,
     * and whether a row standing at length 1 is believed is a question about {@code UserId} — the
     * answer cannot differ between {@code schedule} and {@code touch}, since neither says anything
     * about the length of a user id. Two occurrences and one debt.
     */
    @Test
    void oneInvariantReadAtTwoBehaviorsIsOneDebt() {
        Map<String, BorderAssessment> lines = bordersOf(TWO_BEHAVIORS, "example.two");
        BorderAssessment scheduled = at(lines, "String.length(d.owner) = 1");
        BorderAssessment touched = at(lines, "String.length(t.owner) = 1");

        assertNotEquals(scheduled.border().cut(), touched.border().cut(),
                "the two are read at different positions, which is what an occurrence is");
        assertEquals(scheduled.border().obligation(), touched.border().obligation(),
                "and both are the one line UserId's clause drew");
    }

    /**
     * Two positions of one type in one behavior are one debt too.
     *
     * <p>The same equivalence with the behavior held still, so that nothing can satisfy the one
     * above by keying on the behavior. A row standing at the boundary through {@code owner} is
     * evidence about {@code UserId}, and {@code reviewer} is the same type saying the same thing.
     */
    @Test
    void twoPositionsOfOneTypeInOneBehaviorAreOneDebt() {
        Map<String, BorderAssessment> lines = bordersOf(TWO_POSITIONS, "example.both");

        assertEquals(1,
                lines.values().stream().map(each -> each.border().obligation()).distinct().count(),
                "one clause, one line, one debt — however many positions carry the type: "
                        + lines.keySet());
        assertEquals(2, lines.size(), "read at both of them: " + lines.keySet());
    }

    /**
     * Two clauses cutting at one value are two debts.
     *
     * <p>The other way a debt keyed too widely goes wrong, and the reason a level alone is not the
     * key either. {@code Window} and {@code Slot} each stop a {@code Minute} at 1000, on one carrier
     * and at one value; they are two rules an author wrote and either could be changed without the
     * other. A row at the line one drew establishes nothing about the line the other drew.
     *
     * <p>Beside {@code Minute}'s own lower end, which both records carry and which is one debt. The
     * pair is the measurement: an implementation that told the two 1000s apart by the position they
     * were read at would pass the first half and split the second.
     */
    @Test
    void twoClausesCuttingAtOneValueAreTwoDebts() {
        Map<String, BorderAssessment> lines = bordersOf(TWO_RECORDS, "example.narrow");

        assertNotEquals(at(lines, "x.stop = 1000").border().obligation(),
                at(lines, "y.at = 1000").border().obligation(),
                "two rules drew a line at one value, and each is owed its own row");
        assertEquals(at(lines, "x.stop = 0").border().obligation(),
                at(lines, "y.at = 0").border().obligation(),
                "and one rule drew the lower end of both, which is one row to write");
    }

    /**
     * One clause bounding two numbers of one declaration is two debts.
     *
     * <p>The case a debt keyed on the clause and the value gets wrong, and the reason the number the
     * clause was written about is part of what a debt is. {@code both} places an end at 1 on
     * {@code String.length(name)} and an end at 1 on {@code String.length(code)}: one clause, one
     * carrier, one value, one polarity, and two lines. A row whose {@code name} is one character
     * long says nothing about {@code code}.
     *
     * <p>What the two share is everything an identity read off the rule and the cut would have had
     * to tell them apart by, which is why this is measured rather than argued: the language builds
     * it, so a reading that folds them is a reading that reports one of them covered by the other's
     * row.
     */
    @Test
    void oneClauseBoundingTwoNumbersOfOneDeclarationIsTwoDebts() {
        Map<String, BorderAssessment> lines = bordersOf(TWO_NUMBERS, "example.numbers");
        BorderAssessment name = at(lines, "String.length(p.name) = 1");
        BorderAssessment code = at(lines, "String.length(p.code) = 1");

        assertEquals(name.border().obligation().provenance(),
                code.border().obligation().provenance(), "one clause placed both ends");
        assertEquals(name.border().cut().at(), code.border().cut().at(),
                "at one value of one carrier, so neither the rule nor the level tells them apart");
        assertNotEquals(name.border().obligation(), code.border().obligation(),
                "and the number the clause was written about does");
    }

    /**
     * Two readings of one comparison are one debt.
     *
     * <p>A non-recursive helper is spliced into every body that calls it, so one guard the author
     * wrote is read once per call: the readings carry different comparison sites, each is measured
     * on its own, and the author owes one row for the line. Keyed on the reading they are two debts
     * that only the merge collecting what each reading saw brings back to one, and which of the two
     * call sites the surviving debt names then comes down to the order a walk took.
     *
     * <p>Built here rather than compiled, because what is being told apart is two readings of one
     * line at one position: through a model they are merged into one assessment before anything can
     * be asked of the pair, and the question is what the pair answers.
     */
    @Test
    void twoReadingsOfOneComparisonAreOneDebt() {
        BoundaryTarget line = aLineAt(100);
        Border first = Border.at(line, readAt(1), ANYWHERE);
        Border second = Border.at(line, readAt(5), ANYWHERE);

        assertNotEquals(first.origin(), second.origin(),
                "two occurrences of the guard, each a reading measured on its own");
        assertEquals(first.obligation(), second.obligation(),
                "and one line the author wrote, which is one row to write");
        assertEquals(BoundaryLine.of(first), BoundaryLine.of(second),
                "so the two readings fold into one line, and the fold is inside the one debt");
    }

    /**
     * A comparison and a bound at one value are two debts.
     *
     * <p>The pair that keeps the case above from being satisfied by a debt keyed on the value and
     * the behavior. Both lines stand at 100 on one carrier and are read at one position; an
     * {@code invariant} drew one and a comparison in a body drew the other, and either could be
     * changed without the other.
     */
    @Test
    void aComparisonAndABoundAtOneValueAreTwoDebts() {
        Border guard = Border.at(aLineAt(100), readAt(1), ANYWHERE);
        Border bound = Border.at(aLineAt(100),
                new LineOrigin.InvariantOrigin(
                        new souther.compiler.check.PartId<>(aClause(), 0),
                        souther.compiler.numeric.EndSide.LOWER, true),
                new souther.compiler.numeric.NumericDomain.Bounds(
                        souther.compiler.numeric.Endpoint.inclusive(
                                souther.compiler.numeric.Count.of(100)), null));

        assertEquals(guard.cut(), bound.cut(), "one value of one quantity, read at one position");
        assertNotEquals(guard.obligation(), bound.obligation(),
                "and two rules the author wrote, each owed a row of its own");
    }

    /** What the rules leave the quantity, where they leave it everything. */
    private static final souther.compiler.numeric.NumericDomain.Bounds ANYWHERE =
            new souther.compiler.numeric.NumericDomain.Bounds(null, null);

    /** One position's line at {@code at}, on the carrier a count of whole numbers runs on. */
    private static BoundaryTarget aLineAt(int at) {
        souther.compiler.check.Carrier carrier = new souther.compiler.check.Carrier.Whole();
        AxisId axis = new AxisId("twice", "a.value");
        souther.compiler.inputs.NumericTerm.ValueOf term =
                new souther.compiler.inputs.NumericTerm.ValueOf(
                        souther.compiler.inputs.TermPath.of(axis.term()));
        return BoundaryTarget.at(
                new BorderQuantity.OfACoordinate(axis.behavior(), term,
                        souther.compiler.inputs.TermOrdersFixtures.itself(term, carrier)),
                new Level.OnACarrier(carrier, souther.compiler.numeric.Count.of(at)));
    }

    /** The numbering this fixture's places are of. One of them, so that two readings written here
     *  as the same occurrence address one place. */
    private static final SiteNumbering WHERE = Numberings.ofComparisons(8);

    /**
     * One reading of one comparison: the same rule and the same place it is written, at the
     * occurrence the call it was spliced into was numbered.
     */
    private static LineOrigin readAt(int occurrence) {
        souther.compiler.check.RuleRef.Comparison rule =
                new souther.compiler.check.RuleRef.Comparison("twice",
                        new souther.compiler.types.SourceConstructOrigin(
                                new WrittenOwner.Body("example.banding", "twice"), 2, 0,
                                souther.compiler.types.SourceConstruct.BINARY));
        return new LineOrigin.ComparisonOrigin(
                new LineOrigin.ComparisonOrigin.Read(
                        rule,
                        souther.compiler.diag.Citation.of(
                                new souther.compiler.diag.SourcePos(15, 16)),
                        List.of(new LineOrigin.ComparisonOrigin.Watched(
                                new ComparisonOccurrence(
                                        "example.banding", "twice", occurrence),
                                WHERE.comparison(occurrence)))),
                new LineFacts(new souther.compiler.check.ComparisonClaim.Cut(
                        souther.compiler.numeric.Towards.BELOW, true)));
    }

    /** The clause the bound in these tests names, which is only an identity here. */
    private static souther.compiler.check.RuleRef.Invariant aClause() {
        return new souther.compiler.check.RuleRef.Invariant(
                new souther.compiler.check.Clause.Ref(
                        new souther.compiler.check.Clause.Id(
                                souther.compiler.types.TypeSymbols.declared(
                                        new souther.compiler.types.TypeKey("example.banding",
                                                "Amount")), 0),
                        java.util.Optional.of(
                                new souther.compiler.check.ClauseName("cap"))));
    }

    /**
     * One helper's comparison called from two behaviors is two debts.
     *
     * <p>The other side of the equivalence above, and the reason a debt does not simply drop the
     * behavior it was read in. A clause of a {@code data} states something about the type wherever
     * the type is carried; a comparison is written in a body and states something about that body,
     * so {@code one} and {@code two} each owe a row at the line {@code band}'s comparison draws in
     * them. What the rule is is where that is decided
     * ({@link souther.compiler.check.RuleRef.Comparison}), and a debt says no more about it.
     */
    @Test
    void oneHelpersComparisonCalledFromTwoBehaviorsIsTwoDebts() {
        Map<String, List<BorderAssessment>> lines = boundariesOf(TWO_CALLERS, "example.callers");
        BorderAssessment first = at(lines.get("one"), "a = 100");
        BorderAssessment second = at(lines.get("two"), "a = 100");

        assertNotEquals(first.border().obligation(), second.border().obligation(),
                "a comparison is written in a body, so each body that holds it owes its own row");
        assertEquals(at(lines.get("one"), "a = 0").border().obligation(),
                at(lines.get("two"), "a = 0").border().obligation(),
                "and the clause of the type they both carry is the one row it always was");
    }

    /** One helper holding a comparison, called from two behaviors. */
    private static final String TWO_CALLERS = """
            module example.callers

            data Amount = Int
                invariant value >= 0

            data Small
            data Large
            data Size = Small | Large

            let band (a: Amount): Size =
                if a.value <= 100 then Small else Large

            behavior one : (a: Amount) -> Size
            let one (a) = band(a)

            behavior two : (a: Amount) -> Size
            let two (a) = band(a)

            example one
                | "x" : (Amount(1)) -> Small

            example two
                | "y" : (Amount(1)) -> Small
            """;

    /**
     * One {@code ensures} clause naming two positions is two debts.
     *
     * <p>The same pair as the clause above, on the other rule that states something about values.
     * {@code Found -> r.a >= 5 && r.b >= 5} is one clause and two lines, and a row whose {@code a}
     * is 5 says nothing about {@code b}. Which of the clause's comparisons drew the line is what
     * tells them apart: everything else about the two is the same, down to the value and the
     * carrier.
     */
    @Test
    void oneEnsuresClauseNamingTwoPositionsIsTwoDebts() {
        Map<String, BorderAssessment> lines = bordersOf(TWO_STATED, "example.stated");
        BorderAssessment first = at(lines, "r.a = 5");
        BorderAssessment second = at(lines, "r.b = 5");

        assertEquals(first.border().obligation().provenance(),
                second.border().obligation().provenance(), "one clause states both");
        assertEquals(first.border().cut().at(), second.border().cut().at(),
                "at one value of one carrier, so neither the rule nor the level tells them apart");
        assertNotEquals(first.border().obligation(), second.border().obligation(),
                "and which comparison of the clause drew the line does");
    }

    /** One {@code ensures} clause stating something about two of a record's numbers. */
    private static final String TWO_STATED = """
            module example.stated

            data R = { a: Int, b: Int }
            data Found
            data Missing

            behavior f : (r: R) -> Found | Missing
                ensures Found -> r.a >= 5 && r.b >= 5
            let f (r) = if r.a >= 30 * 2 then Found else Missing

            example f
                | "one" : (R { a = 1, b = 1 }) -> Missing
            """;

    /** One clause bounding two numbers of the declaration it is written on. */
    private static final String TWO_NUMBERS = """
            module example.numbers

            data Pair = { name: String, code: String }
                invariant both = String.length(name) >= 1 && String.length(code) >= 1

            data Ok

            behavior f : (p: Pair) -> Ok
            let f (p) = Ok

            example f
                | "a" : (Pair { name = "x", code = "y" }) -> Ok
            """;

    /**
     * One conjunct read through two frames is one debt.
     *
     * <p>The direction the case above does not fix. A clause of {@code Day} is read twice — once
     * from {@code Day}, where it is about {@code value}, and once from the {@code Span} holding it,
     * where it is about {@code d} — and the two readings are merged into one range
     * ({@code DeclaredBounds.and}). What the author wrote is one line either way, so the two are one
     * debt and each position carries two ends rather than four.
     *
     * <p>A second bounded field beside it, because the merge is what this is about: with one field
     * the reading through the record contributes nothing and the model goes past the case without
     * touching it.
     *
     * <p>Measured as the count of borders rather than by comparing two of them, because the way this
     * goes wrong is a border appearing twice: an identity read off the number the clause was written
     * about spells it {@code value} in one reading and {@code d} in the other, and both survive into
     * the cut. Counting is what notices that; asking two borders whether they agree finds one of the
     * pair and never the pair.
     */
    @Test
    void oneConjunctReadThroughTwoFramesIsOneDebt() {
        List<Border> lines = bordersOfThePosition(TWO_FRAMES, "classify", "s.d");

        assertEquals(2, lines.size(),
                () -> "the clause states two ends, so the position has two borders: "
                        + lines.stream().map(Border::label).toList());
        assertEquals(2, lines.stream().map(Border::obligation).distinct().count(),
                "and neither of them is two debts");
    }

    /**
     * Every border of one position, as the partition makes them.
     *
     * <p>Below the report, because that is where a line read twice shows as two. What reaches a
     * report is the assessment, and a border read through two frames arrives there as one entry
     * whichever answer this gives — so a test asking the report would have said the identity was
     * right whatever it was.
     */
    private static List<Border> bordersOfThePosition(String model, String behavior, String path) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        Map<String, souther.compiler.check.DeclaredSig> sigs = compilation.db()
                .ask(new souther.compiler.query.Bodies.DeclaredSignatures(module)).value();
        assertNotNull(sigs.get(behavior), "the model under test compiles");
        souther.compiler.inputs.InputDomain domain =
                souther.compiler.inputs.InputDomain.of(sigs.get(behavior), rules,
                        souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
        Partitions.Partitioning partitioning = Partitions.of(behavior, domain, rules,
                souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
        Axis axis = partitioning.axes().stream()
                .filter(a -> a.path().toString().equals(path)).findFirst().orElseThrow();
        souther.compiler.inputs.Quantities reading = domain.quantities(rules);
        return Partitions.bordersOf(axis, reading,
                reading.runsBetween(axis.term()), new LinesRead());
    }

    /** A newtype's own clause, reached through the record that holds it. */
    private static final String TWO_FRAMES = """
            module example.frames

            data Slot = Int
                invariant value >= 0 && value <= 10

            data Day = Date
                invariant value >= Date("2020-01-01") && value <= Date("2020-01-02")

            data Span = { a: Slot, d: Day }

            data Ok

            behavior classify : (s: Span) -> Ok
            let classify (s) = Ok

            example classify
                | "a" : (Span { a = Slot(1), d = Day(Date("2020-01-01")) }) -> Ok
            """;

    /** One clause placing both ends of a range. */
    private static final String RANGE = """
            module example.range

            data N = Int
                invariant within = value >= 1 && value <= 10

            data Ok

            behavior f : (n: N) -> Ok
            let f (n) = Ok

            example f
                | "x" : (N(3)) -> Ok
            """;

    /** One type's invariant carried by two behaviors, at a position apiece. */
    private static final String TWO_BEHAVIORS = """
            module example.two

            data UserId = String
                invariant nonempty = String.length(value) >= 1

            data Draft = { owner: UserId }
            data Task = { owner: UserId }

            data Ok

            behavior schedule : (d: Draft) -> Ok
            let schedule (d) = Ok

            behavior touch : (t: Task) -> Ok
            let touch (t) = Ok

            example schedule
                | "a" : (Draft { owner = UserId("x") }) -> Ok

            example touch
                | "a" : (Task { owner = UserId("x") }) -> Ok
            """;

    /** One type's invariant carried twice by one behavior. */
    private static final String TWO_POSITIONS = """
            module example.both

            data UserId = String
                invariant nonempty = String.length(value) >= 1

            data Draft = { owner: UserId, reviewer: UserId }

            data Ok

            behavior schedule : (d: Draft) -> Ok
            let schedule (d) = Ok

            example schedule
                | "a" : (Draft { owner = UserId("x"), reviewer = UserId("y") }) -> Ok
            """;

    /** Two records stopping one type at one value, each by a clause of its own. */
    private static final String TWO_RECORDS = """
            module example.narrow

            data Minute = Int
                invariant range = value >= 0 && value <= 1439

            data Window = { stop: Minute }
                invariant fits = stop.value <= 1000

            data Slot = { at: Minute }
                invariant fits = at.value <= 1000

            data Ok

            behavior w : (x: Window) -> Ok
            let w (x) = Ok

            behavior s : (y: Slot) -> Ok
            let s (y) = Ok

            example w
                | "a" : (Window { stop = Minute(5) }) -> Ok

            example s
                | "a" : (Slot { at = Minute(5) }) -> Ok
            """;

    private static BorderAssessment at(Map<String, BorderAssessment> lines, String label) {
        BorderAssessment line = lines.get(label);
        assertNotNull(line, () -> label + " is not a line of this model: " + lines.keySet());
        return line;
    }

    /** The line one behavior draws at {@code label}, for a caller reading the behaviors apart. */
    private static BorderAssessment at(List<BorderAssessment> lines, String label) {
        return lines.stream().filter(each -> each.border().label().equals(label)).findFirst()
                .orElseGet(() -> {
                    throw new AssertionError(label + " is not a line of this behavior: "
                            + lines.stream().map(each -> each.border().label()).toList());
                });
    }

    /** Every border of {@code module}, as the measure holds them: per behavior, and one entry per
     *  occurrence. For a caller counting them rather than looking one up. */
    private static Map<String, List<BorderAssessment>> boundariesOf(String model, String module) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        Map<String, List<BorderAssessment>> boundaries =
                Adequacy.boundariesOf(compilation.db(), module);
        assertNotNull(boundaries, "the model under test compiles");
        return boundaries;
    }

    /** Every border of {@code module}, by the line it is at. Each label names one position, so the
     *  map holds one entry per occurrence and not one per debt. */
    private static Map<String, BorderAssessment> bordersOf(String model, String module) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        Map<String, List<BorderAssessment>> boundaries =
                Adequacy.boundariesOf(compilation.db(), module);
        assertNotNull(boundaries, "the model under test compiles");
        Map<String, BorderAssessment> out = new LinkedHashMap<>();
        boundaries.values().forEach(each -> each.forEach(b -> out.put(b.label(), b)));
        return out;
    }
}
