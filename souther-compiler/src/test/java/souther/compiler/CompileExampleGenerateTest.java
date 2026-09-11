package souther.compiler;

import souther.compiler.diag.SourceRendering;
import souther.compiler.source.SourceId;

import org.junit.jupiter.api.Test;

import souther.compiler.partition.Generator;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.query.OfferingRequest;
import souther.compiler.report.GeneratedRows;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rows generated against a model that really compiles, and put through the decoders that model derives.
 *
 * <p>Which is the part a generator cannot work out on its own. Whether two values are allowed together
 * is what an invariant relating two fields says, and the only way to find out is to build the value the
 * way a row's own fixture is built.
 */
class CompileExampleGenerateTest {

    private static final String TRIP = """
            module example.trip

            data Domestic
            data Overseas
            data Kind = Domestic | Overseas

            data Request = { kind: Kind, urgent: Bool }

            data Accepted = { at: String }

            behavior submit : (request: Request) -> Accepted
                constructs Accepted

            let submit (request) = Accepted { at = "now" }

            example submit
                | (Request { kind = Domestic, urgent = true }) -> Accepted { at = "now" }
            """;

    private static Compilation compiledOf(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        // What `souther examples` asks for. A line a `guard` drew is only decidable where the arms
        // were measured, so below this the generator has nothing to say about one.
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return compilation;
    }

    /**
     * The block a person is handed for {@code module}.
     *
     * <p>What is offered and not what was composed: a row another offered row answers is not one of
     * these, and a test that rendered the composition would be reading rows nobody is given.
     */
    private static String blockOf(String source, String module, boolean boundaries) {
        Compilation compilation = compiledOf(source);
        souther.compiler.query.Offering offering = Adequacy.offeredFor(compilation.db(),
                OfferingRequest.overTheModule(module, boundaries));
        assertNotNull(offering, "the model under test compiles");
        return GeneratedRows.of(offering, Map.of(), SourceRendering.namedByIdentity(compilation.texts()),
                compilation.db()).text();
    }

    private static Map<String, Adequacy.Filling> generated(String source) {
        Compilation compilation = compiledOf(source);
        Map<String, Adequacy.Filling> all = Adequacy.generatedOf(compilation.db(), compilation.modules().get(0));
        assertNotNull(all, "the model under test compiles");
        return all;
    }

    /**
     * What one behavior is offered at the lines its values are held to.
     *
     * <p>Two authorities and one question. A line an {@code invariant} drew is owed once over every
     * behavior carrying the type and is resolved for the module; a line this body drew is its own
     * and is in its filling (issue #1076). A person reading the block beside the behavior sees both.
     */
    private static Generator.GenerationResult atTheLines(String source, String behavior) {
        Compilation compilation = compiledOf(source);
        return OfferedAtTheLines.of(compilation, compilation.modules().get(0), behavior);
    }

    private static List<String> inputs(souther.compiler.partition.FillResult result) {
        return inputs(result.rows());
    }

    private static List<String> inputs(Generator.GenerationResult result) {
        return inputs(result.rows());
    }

    private static List<String> inputs(List<Generator.GeneratedRow> rows) {
        return rows.stream()
                .map(r -> String.join(", ",
                        r.inputs().stream().map(i -> i.text()).toList()))
                .toList();
    }

    @Test
    void whatTheWrittenRowAlreadyCoversIsNotGeneratedAgain() {
        souther.compiler.partition.FillResult filled = generated(TRIP).get("submit").composed();

        // One row per class the written row is not in, and no more. The written row is
        // `Domestic, urgent = true`, so what is left is `Overseas` at one position and `false` at
        // the other — two classes, two rows, each moving the position it is about and no other.
        assertEquals(List.of(
                        "Request { kind = Overseas, urgent = true }",
                        "Request { kind = Domestic, urgent = false }"),
                inputs(filled));
        assertEquals(List.of(), filled.unresolved());
    }

    /**
     * A rule relating two fields refuses a value each field would have allowed on its own.
     *
     * <p>Here the low end may not be above the high end. Each position on its own admits the values
     * that break it — the classes are drawn by the behavior's own comparisons and know nothing of each
     * other — so the only thing that can say the pair is not allowed is the decoder the model derives.
     * What comes back is the reason it was refused, not a claim that the combination is impossible.
     */
    @Test
    void aRuleRelatingTwoFieldsRefusesTheValueAndIsSaidSo() {
        String correlated = """
                module example.trip

                data Amount = Int
                    invariant value >= 0

                data Request = { lo: Amount, hi: Amount }
                    invariant lo.value <= hi.value

                data Accepted = { at: String }

                behavior submit : (request: Request) -> Accepted
                    constructs Accepted

                let submit (request) = {
                    guard request.lo.value <= 100 else Accepted { at = "wide" }
                    guard request.hi.value <= 50 else Accepted { at = "tall" }
                    Accepted { at = "now" }
                }
                """;
        souther.compiler.partition.FillResult filled = generated(correlated).get("submit").composed();

        assertTrue(inputs(filled).stream().noneMatch(row -> row.contains("Amount(101)")),
                "a value the model refuses is not written into a row: " + inputs(filled));
        // The class, once. It used to be said three times — the class on its own and the two
        // combinations it took part in — which is one fact repeated as many times as the
        // arithmetic allowed.
        assertEquals(1, filled.unresolved().size(), filled.unresolved().toString());
        for (Generator.UnresolvedCombination left : filled.unresolved()) {
            assertEquals(Generator.UnresolvedCombination.Reason.ALL_CANDIDATES_REJECTED,
                    left.reason());
            assertTrue(left.classes().contains("request.lo=100 < x"),
                    left.classes().toString());
        }
        // And the class beside it is not left unwritten with it. A row for `request.hi = 50 < x`
        // stands where the low end can be built, so it is composed — which is what says the refusal
        // above is about the values these two positions were tried at together and not about either
        // class. Calling one impossible would take a class somebody can write a row for out of the
        // denominator.
        assertTrue(inputs(filled).stream().anyMatch(row -> row.contains("Amount(51)")),
                "the high end's upper class is still written: " + inputs(filled));
    }

    /**
     * A boundary is a row of its own, apart from the rows that fill the classes.
     *
     * <p>The two ask different things. A class wants any value inside it; an edge wants exactly the
     * value the rule was written at, and a row that sat on three edges at once would be three answers
     * an author has to separate.
     */
    @Test
    void aBoundaryNothingReachedGetsARowOfItsOwn() {
        String bounded = """
                module example.trip

                data Amount = Int
                    invariant value >= 0 && value <= 1000

                data Domestic
                data Overseas
                data Kind = Domestic | Overseas

                data Request = { kind: Kind, cost: Amount }

                data Accepted = { at: String }

                behavior submit : (request: Request) -> Accepted
                    constructs Accepted

                let submit (request) = Accepted { at = "now" }
                """;
        Adequacy.Filling filling = generated(bounded).get("submit");

        assertEquals(List.of("Request { kind = Domestic, cost = Amount(0) }",
                        "Request { kind = Overseas, cost = Amount(0) }"),
                inputs(filling.composed()), "the classes, at whatever cost builds");
        // Each line's own value and then a value inside the run beside it, line by line. All four
        // are the declaration's: nothing in this body cuts the cost, so where the runs stop is what
        // the clause leaves and a row anywhere settles them.
        assertEquals(List.of("Request { kind = Domestic, cost = Amount(0) }",
                        "Request { kind = Domestic, cost = Amount(1) }",
                        "Request { kind = Domestic, cost = Amount(1000) }",
                        "Request { kind = Domestic, cost = Amount(999) }"),
                inputs(atTheLines(bounded, "submit")),
                "the points against each line at exactly the value the rule names, and the points"
                        + " away from them at a value the side holds");
    }

    /**
     * A line a `guard` drew is offered, not only named.
     *
     * <p>Writing the row and meeting the line are different questions, and only the second needs the
     * arms — but the row to write is the same row either way. A boundary the report names as unmet and
     * the generator will not offer is the one place an author is told about a gap and left to close it
     * by hand.
     */
    @Test
    void aGuardsLineIsOfferedAsWellAsNamed() {
        String guarded = """
                module example.trip

                data Amount = Int
                    invariant value >= 0

                data Draft = { cost: Amount }
                data Submitted = { cost: Amount }
                data Waiting = { cost: Amount }

                behavior submit : (request: Draft) -> Submitted | Waiting
                    constructs Submitted, Waiting

                let submit (request) = {
                    guard request.cost.value <= 100 else Waiting { cost = request.cost }
                    Submitted { cost = request.cost }
                }

                example submit
                    | (Draft { cost = Amount(50) })  -> Submitted { cost = Amount(50) }
                    | (Draft { cost = Amount(500) }) -> Waiting { cost = Amount(500) }
                """;

        assertEquals(List.of("Draft { cost = Amount(100) }",
                        "Draft { cost = Amount(101) }",
                        "Draft { cost = Amount(0) }"),
                inputs(atTheLines(guarded, "submit")),
                "the invariant's edge and both sides of the guard's line");
    }

    /**
     * A value a format rule accepts, and one the model then builds.
     *
     * <p>An identifier saying what it looks like is the commonest rule there is, and nothing could
     * write one — so a record holding an id could not be composed at all, and every combination that
     * record took part in came back as one whose values the model refused. What settles this is not
     * that the string looks right but that the derived decoder takes it, which is what the check here
     * is: the row is offered only if it built.
     */
    @Test
    void anIdentifierWithAFormatRuleGetsAValueTheModelAccepts() {
        String formatted = """
                module example.office

                data OfficeId = String
                    invariant String.matches("[0-9]{2}-[0-9]{6}", value)

                data Prefecture = String
                    invariant String.matches("0[1-9]|[1-3][0-9]|4[0-7]", value)

                data Domestic
                data Overseas
                data Kind = Domestic | Overseas

                data Office = { id: OfficeId, prefecture: Prefecture, kind: Kind }
                data Ok = { n: Int }

                behavior register : (office: Office) -> Ok
                    constructs Ok

                let register (office) = Ok { n = 0 }

                example register
                    | (Office { id = OfficeId("12-345678"), prefecture = Prefecture("13"),
                                kind = Domestic }) -> Ok { n = 0 }
                """;

        assertEquals(List.of(
                        "Office { id = OfficeId(\"00-000000\"), prefecture = Prefecture(\"01\"),"
                                + " kind = Overseas }"),
                inputs(generated(formatted).get("register").composed()),
                "the class nothing covers, with an id and a prefecture the rules accept");
    }

    /** And a date, which a row writes as its ISO form. */
    @Test
    void aDateGetsAValueTheModelAccepts() {
        String dated = """
                module example.dated

                data Yes
                data No
                data Flag = Yes | No

                data Request = { on: Date, flag: Flag }
                data Ok = { n: Int }

                behavior take : (request: Request) -> Ok
                    constructs Ok

                let take (request) = Ok { n = 0 }

                example take
                    | (Request { on = Date("2026-08-03"), flag = Yes }) -> Ok { n = 0 }
                """;

        assertEquals(List.of("Request { on = Date(\"2000-01-01\"), flag = No }"),
                inputs(generated(dated).get("take").composed()));
    }

    /**
     * A value carrying a character a literal has to escape.
     *
     * <p>The row is text somebody pastes, so what it says has to read back as what it was made from.
     * Written as itself, a tab is invisible in the row and a newline ends it — the rest of the row
     * lands on a line of its own, and what was pasted is not what was offered. So this asks the
     * compiler rather than the text: the block goes back in, and the rows have to hold.
     */
    @Test
    void aValueWithACharacterALiteralEscapesSurvivesBeingPasted() {
        String tabbed = """
                module example.tabbed

                data Spaced = String
                    invariant String.matches("a[\\\\t]b", value)

                data Yes
                data No
                data Flag = Yes | No

                data Note = { text: Spaced, flag: Flag }
                data Ok = { n: Int }

                behavior take : (note: Note) -> Ok
                    constructs Ok

                let take (note) = Ok { n = 0 }

                example take
                    | (Note { text = Spaced("a\\tb"), flag = Yes }) -> Ok { n = 0 }
                """;

        assertEquals(List.of("Note { text = Spaced(\"a\\tb\"), flag = No }"),
                inputs(generated(tabbed).get("take").composed()),
                "the tab is written the way a literal spells one");

        String pasted = tabbed + sourceOf(blockOf(tabbed, "example.tabbed", false))
                .replace("<?>", "Ok { n = 0 }");

        Compilation compilation = Compilation.ofSource(pasted, "Main");
        compilation.answerEverything();
        List<souther.compiler.observe.RowOutcome> rows = outcomes(compilation);

        assertEquals(2, rows.size(), "the row that was there, and the one generated");
        for (souther.compiler.observe.RowOutcome row : rows) {
            assertEquals(souther.compiler.observe.Disposition.HELD, row.disposition(),
                    row.identity().shown() + " -> " + row.failurePhase());
        }
    }

    /** A model where the position under test is a field of the input and one flag divides the rows. */
    private static String withField(String declarations, String field) {
        return """
                module example.candidates

                %s

                data Yes
                data No
                data Flag = Yes | No

                data Req = { v: %s, f: Flag }
                data Ok = { n: Int }

                behavior take : (r: Req) -> Ok
                    constructs Ok

                let take (r) = Ok { n = 0 }

                example take
                    | (Req { v = %s, f = Yes }) -> Ok { n = 0 }
                """.formatted(declarations, field, "%s");
    }

    /**
     * A rule this cannot read leaves the position what it had.
     *
     * <p>Reading a format rule is a way to offer a value, not a way to decide there is none. A lookahead
     * is past what the reader understands, and the plain representative for the position — which built
     * before any of this existed, and which this rule happens to accept — is still offered.
     */
    @Test
    void unsupportedPatternThatAcceptsTheDefaultCandidateStillGenerates() {
        String source = withField("""
                data V = String
                    invariant String.matches("(?=x)x", value)""", "V").formatted("V(\"x\")");

        assertEquals(List.of("Req { v = V(\"x\"), f = No }"),
                inputs(generated(source).get("take").composed()));
    }

    /**
     * Two rules on one position, in either order.
     *
     * <p>Every clause of an invariant has to hold, so the order they are written in cannot decide
     * whether a value can be found. Each readable rule contributes a candidate and the constructor
     * settles which of them stands — reading only the first would make `[a-z]+` before `x+` produce
     * `"a"` and stop, and the same two rules the other way round produce a value.
     */
    @Test
    void conjoinedPatternsDoNotDependOnDeclarationOrder() {
        String forwards = withField("""
                data V = String
                    invariant String.matches("[a-z]+", value)
                    invariant String.matches("x+", value)""", "V").formatted("V(\"x\")");
        String backwards = withField("""
                data V = String
                    invariant String.matches("x+", value)
                    invariant String.matches("[a-z]+", value)""", "V").formatted("V(\"x\")");

        assertEquals(List.of("Req { v = V(\"x\"), f = No }"),
                inputs(generated(forwards).get("take").composed()));
        assertEquals(inputs(generated(forwards).get("take").composed()),
                inputs(generated(backwards).get("take").composed()),
                "the same two rules, written the other way round");
    }

    /**
     * Two positions, each needing a different one of the values it was offered.
     *
     * <p>What has to build is the whole input at once — the check is the model's own constructor — so
     * what is being tried is the tuple, not a value. Here the two positions carry the same two rules
     * written the other way round, so their values come out in opposite orders and the assignment that
     * builds is the first value of one with the second of the other. Trying one index across every
     * position walks the diagonal of the choices and never reaches it.
     */
    @Test
    void candidatesAtDifferentPositionsAreNotWalkedInLockstep() {
        String source = """
                module example.lock

                data Left = String
                    invariant String.matches("[a-z]+", value)
                    invariant String.matches("x+", value)

                data Right = String
                    invariant String.matches("x+", value)
                    invariant String.matches("[a-z]+", value)

                data Yes
                data No
                data Flag = Yes | No

                data Req = { left: Left, right: Right, flag: Flag }
                data Ok = { n: Int }

                behavior take : (request: Req) -> Ok
                    constructs Ok

                let take (request) = Ok { n = 0 }

                example take
                    | (Req { left = Left("x"), right = Right("x"), flag = Yes }) -> Ok { n = 0 }
                """;

        assertEquals(List.of("Req { left = Left(\"x\"), right = Right(\"x\"), flag = No }"),
                inputs(generated(source).get("take").composed()));
        // And the same model with one of the two rewritten to declare its rules in the other order,
        // which is the same model. Order decided this before the assignments were walked as tuples.
        assertEquals(inputs(generated(source).get("take").composed()),
                inputs(generated(source.replace("module example.lock", "module example.lock2")
                        .replace("""
                                data Right = String
                                    invariant String.matches("x+", value)
                                    invariant String.matches("[a-z]+", value)""", """
                                data Right = String
                                    invariant String.matches("[a-z]+", value)
                                    invariant String.matches("x+", value)""")).get("take").composed()));
    }

    /**
     * One parameter's choices do not multiply with another's.
     *
     * <p>Each parameter is built and refused on its own, so searching them together spends the bound
     * on assignments differing only in a parameter already settled. Here every field's value is the
     * second of the two it was offered, which is four steps out in each of two parameters — reachable
     * on its own and, taken together, eight steps into a space of two hundred and fifty-six.
     */
    @Test
    void eachParameterIsSearchedOnItsOwn() {
        StringBuilder declarations = new StringBuilder();
        for (char c = 'a'; c <= 'h'; c++) {
            declarations.append("""
                    data V%1$s = String
                        invariant String.matches("[a-z]+", value)
                        invariant String.matches("x+", value)

                    """.formatted(Character.toUpperCase(c)));
        }
        String source = """
                module example.two

                %sdata Yes
                data No
                data Flag = Yes | No

                data One = { a: VA, b: VB, c: VC, d: VD }
                data Two = { e: VE, f: VF, g: VG, h: VH, flag: Flag }
                data Ok = { n: Int }

                behavior take : (one: One, two: Two) -> Ok
                    constructs Ok

                let take (one, two) = Ok { n = 0 }
                """.formatted(declarations);

        List<String> rows = inputs(generated(source).get("take").composed());

        assertEquals(2, rows.size(), "one row per class of the only axis: " + rows);
        for (String row : rows) {
            for (char c = 'a'; c <= 'h'; c++) {
                assertTrue(row.contains("V%1$s(\"x\")".formatted(Character.toUpperCase(c))),
                        "every field at the value its rules allow: " + row);
            }
        }
    }

    /**
     * A value held in reserve does not push aside the assignments already being tried.
     *
     * <p>The search walks the product of what the positions offer, and it is bounded — so a value
     * added to one position moves every assignment past it further back, and enough positions
     * carrying one more value each puts an assignment that used to be reached past the bound. Here
     * six fields refuse both edges of their type and take the value between them, which is the
     * sixty-fourth assignment out of sixty-four while each position offers two values and the last
     * of seven hundred and twenty-nine while each offers three. So a value offered for the case
     * where the ordinary ones are refused is tried after all of them, and not as one of them.
     */
    @Test
    void aValueHeldInReserveDoesNotDisplaceTheAssignmentsAlreadyTried() {
        String source = """
                module example.reserve

                data N = Decimal
                    invariant bounded = value >= -1.0m && value <= 1.0m
                    invariant notLow = value /= -1.0m
                    invariant notHigh = value /= 1.0m

                data R = { a: N, b: N, c: N, d: N, e: N, f: N }

                data Ok = { n: Int }

                behavior take : (request: R, flag: Bool) -> Ok
                    constructs Ok

                let take (request, flag) = Ok { n = 0 }
                """;

        souther.compiler.partition.FillResult filled = generated(source).get("take").composed();

        assertEquals(List.of(), filled.unresolved(),
                () -> "the value between the edges builds: " + filled.unresolved());
        assertFalse(filled.rows().isEmpty(), "so there are rows");
    }

    /**
     * A search that stopped says so, rather than saying everything was refused.
     *
     * <p>The choices multiply, so a row is tried at a bounded number of assignments. Past that bound
     * the ones not reached were not refused — nothing was written and nothing built — and calling them
     * refused tells an author their model rules out a combination it does not.
     *
     * <p>What refuses every value here is a pattern the record states about one field, which nothing
     * derives a value from: the field's own type says its values are x's, and the record wants y's.
     * A rule counting the field would not do — a floor is read now, and the value built for it is
     * one this model would accept.
     */
    @Test
    void whatTheSearchDidNotReachIsNotReportedAsRefused() {
        StringBuilder declarations = new StringBuilder();
        StringBuilder fields = new StringBuilder();
        for (char c = 'a'; c <= 'i'; c++) {
            declarations.append("""
                    data V%1$s = String
                        invariant String.matches("[a-z]+", value)
                        invariant String.matches("x+", value)

                    """.formatted(Character.toUpperCase(c)));
            fields.append(c).append(": V").append(Character.toUpperCase(c)).append(", ");
        }
        String source = """
                module example.wide

                %sdata Yes
                data No
                data Flag = Yes | No

                data Req = { %sflag: Flag }
                    invariant UNREAD

                data Ok = { n: Int }

                behavior take : (request: Req) -> Ok
                    constructs Ok

                let take (request) = Ok { n = 0 }
                """.formatted(declarations, fields)
                .replace("UNREAD", ARuleNoReadingTakesIn.about("a.value"));

        List<Generator.UnresolvedCombination> left = generated(source).get("take").composed()
                .unresolved();

        assertFalse(left.isEmpty(), "nothing builds, so something is left");
        for (Generator.UnresolvedCombination each : left) {
            assertEquals(Generator.UnresolvedCombination.Reason.THE_SEARCH_LEFT_SOMETHING_UNTRIED, each.reason(),
                    each.toString());
            assertTrue(each.subject().startsWith("request.flag="),
                    "and it is still about the combination: " + each.subject());
        }
    }

    /**
     * A newtype's rules are read wherever it is asked for a value.
     *
     * <p>A newtype is asked for one from two places — a field of a record, and a case of a sum — and
     * the rules on it are the same rules in both. Read in only one of them, a formatted identifier
     * composes as a field and is refused as a case, which is the same model reporting a position as
     * unfillable depending on where it was written.
     */
    @Test
    void aFormattedNewtypeUsedAsASumCaseGetsARepresentative() {
        String source = """
                module example.cases

                data Email = String
                    invariant String.matches("[a-z]+@[a-z]+", value)

                data Amount = Int
                    invariant value >= 100

                data NoContact
                data Contact = Email | NoContact
                data NoAmount
                data Wallet = NoAmount | Amount

                data Req = { contact: Contact, w: Wallet }
                data Ok = { n: Int }

                behavior take : (r: Req) -> Ok
                    constructs Ok

                let take (r) = Ok { n = 0 }

                example take
                    | (Req { contact = NoContact, w = NoAmount }) -> Ok { n = 0 }
                """;

        List<String> rows = inputs(generated(source).get("take").composed());

        assertTrue(rows.stream().anyMatch(r -> r.contains("Email(\"a@a\")")),
                "a case whose rule states a format: " + rows);
        assertTrue(rows.stream().anyMatch(r -> r.contains("Amount(100)")),
                "and one whose rule states a bound: " + rows);
    }

    // --- the block, put back through the compiler ------------------------------------------------

    /** The rows of the block, with the mark answered the way an author answers it. */
    private static String answered(String source, String expected) {
        String rows = sourceOf(blockOf(source, "example.trip", false))
                .replace("<?>", expected);
        return source + rows;
    }

    /**
     * The block's rows, which is every line of it that is not prose.
     *
     * <p>Read by what a line starts with, because that is what tells the two apart now: the rows and
     * the heading over them are source, and everything said about them is a comment. Nothing here
     * takes a marker off, which is the whole of what the block being rows means — an author pastes
     * it as it stands.
     */
    private static String sourceOf(String block) {
        return block.lines()
                .filter(line -> !line.startsWith("//"))
                .reduce("", (all, line) -> all + line + "\n");
    }

    /** The rows a source's examples left, across every file that writes one. */
    private static List<souther.compiler.observe.RowOutcome> outcomes(Compilation compilation) {
        List<souther.compiler.observe.RowOutcome> rows = new ArrayList<>();
        for (String name : compilation.modules()) {
            for (SourceId sourceId : compilation.exampleSourcesOf(name)) {
                souther.compiler.query.Output.Examples.Of observed = compilation.db()
                        .ask(souther.compiler.query.Output.Examples.asked(
                                compilation.db(), name, sourceId)).value();
                if (observed != null) {
                    rows.addAll(observed.rows());
                }
            }
        }
        return rows;
    }

    /**
     * Answered, the block is rows that hold, and what they were generated for is covered.
     *
     * <p>The whole claim about a generated row, put to the compiler rather than to the text: it parses,
     * the inputs build, the behavior takes them, the answer written against them is the one it gives,
     * and afterwards there is nothing left to generate. A row that only looked right would pass none of
     * those.
     */
    @Test
    void answeringTheBlockGivesRowsThatHoldAndCoverWhatTheyWereFor() {
        String source = answered(TRIP, "Accepted { at = \"now\" }");

        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        List<souther.compiler.observe.RowOutcome> rows = outcomes(compilation);

        assertEquals(3, rows.size(), "the row that was there, and the two generated");
        for (souther.compiler.observe.RowOutcome row : rows) {
            assertEquals(souther.compiler.observe.Disposition.HELD, row.disposition(),
                    row.identity().shown() + " -> " + row.failurePhase());
        }
        assertEquals("", blockOf(source, "example.trip", false),
                "nothing is left to fill");
    }

    /** And the check above is not vacuous: a row answered with something the behavior does not give
     * fails, so the rows holding is the block's doing and not the compiler's indifference. */
    @Test
    void aRowAnsweredWrongDoesNotHold() {
        String wrong = answered(TRIP, "Accepted { at = \"later\" }");

        Compilation compilation = Compilation.ofSource(wrong, "Main");
        compilation.answerEverything();

        assertTrue(outcomes(compilation).stream()
                        .anyMatch(row -> row.disposition() == souther.compiler.observe.Disposition
                                .FAILED),
                "a wrong answer is a failing row");
    }

    /**
     * Pasted as it comes, the block is rows the module keeps and asserts nothing with.
     *
     * <p>Both halves, because either alone is a different design. The rows arrive — the module goes
     * on compiling with them in it, so an author answers them one at a time and everything that
     * keeps source keeps these. And nothing is asserted: a row whose answer nobody wrote states no
     * answer, which is what stops the paste from holding the model to a claim nobody made.
     *
     * <p>And the block does not offer them again. What was owed is written down now, so a second
     * run has a row at every point the first composed one for; offered twice, an author who pasted
     * the block and came back would be handed the same questions beside the ones they are already
     * looking at.
     */
    @Test
    void theBlockPastedUnchangedIsRowsThatStateNoAnswer() {
        String pasted = TRIP + sourceOf(blockOf(TRIP, "example.trip", false));

        Compilation compilation = Compilation.ofSource(pasted, "Main");
        compilation.answerEverything();
        List<souther.compiler.observe.RowOutcome> rows = outcomes(compilation);

        assertEquals(3, rows.size(), "the row that was there, and the two pasted");
        assertEquals(List.of(souther.compiler.observe.Disposition.HELD,
                        souther.compiler.observe.Disposition.NOTHING_TO_HOLD,
                        souther.compiler.observe.Disposition.NOTHING_TO_HOLD),
                rows.stream().map(souther.compiler.observe.RowOutcome::disposition).sorted()
                        .toList(),
                "the row that was answered holds, and the two pasted hold nothing: " + rows);

        assertEquals("", blockOf(pasted, "example.trip", false),
                "and nothing is offered a second time");
    }

    /**
     * A row for a class is written against a value the module already states.
     *
     * <p>The model issue #967 was reported from. Its gap is a class at one position, and the row a
     * reader of the table it copies wants is that class against the values already beside it — one
     * column moved, and the rest as the model says. Composed from the classes alone, every position
     * held whatever the search named there and a reader had to work out which of the three
     * differences the answer turned on.
     */
    private static final String AGAINST_A_LET = """
            module example.trip

            data C
            data B
            data F = C | B

            data G1
            data G2
            data G = G1 | G2

            data Cond = { f: F, g: G }
            data Out = { n: Int }

            let none = Cond { f = B, g = G1 }

            behavior calc : (c: Cond) -> Out
                constructs Out

            let calc (c) = Out { n = 0 }

            example calc
                | "base" : (none) -> Out { n = 0 }
            """;

    @Test
    void aRowForAClassIsWrittenAgainstTheValueTheModuleStates() {
        souther.compiler.partition.FillResult filled = generated(AGAINST_A_LET).get("calc").composed();

        assertEquals(List.of("Cond { ...none, f = C }", "Cond { ...none, g = G2 }"),
                inputs(filled));
        assertEquals(List.of(List.of("c.f=C"), List.of("c.g=G2")),
                filled.rows().stream().map(row -> row.labels()).toList(),
                "each named for the one class it moves");
    }

    /**
     * And the spread is the value, not a way of printing one.
     *
     * <p>What a row is written as and what a decoder builds it from are two forms of one value
     * ({@code FixtureTemplate}), so the row offered goes back through the reading a written row
     * goes through. Printed as a spread over a tree that had the record written out, the two would
     * be one value under two spellings — and the row an author pasted would be a row nobody built.
     */
    @Test
    void theSpreadRowsHoldWhenTheyArePastedBack() {
        String source = answered(AGAINST_A_LET, "Out { n = 0 }");

        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        List<souther.compiler.observe.RowOutcome> rows = outcomes(compilation);

        assertEquals(3, rows.size(), "the row that was there, and the two generated");
        for (souther.compiler.observe.RowOutcome row : rows) {
            assertEquals(souther.compiler.observe.Disposition.HELD, row.disposition(),
                    row.identity().shown() + " -> " + row.failurePhase());
        }
        assertEquals("", blockOf(source, "example.trip", false),
                "and nothing is left to offer once they are answered");
    }

    /** A rule relating two fields, a value the model states, and a class each of two positions. */
    private static final String CORRELATED = """
            module example.trip

            data Amount = Int
                invariant value >= 0

            data Request = { lo: Amount, hi: Amount }
                invariant lo.value <= hi.value

            data Accepted = { at: String }

            let mid = Request { lo = Amount(60), hi = Amount(70) }

            behavior submit : (request: Request) -> Accepted
                constructs Accepted

            let submit (request) = {
                guard request.lo.value <= 50 else Accepted { at = "wide" }
                guard request.hi.value <= 60 else Accepted { at = "tall" }
                Accepted { at = "now" }
            }
            """;

    /**
     * The value the model states is where the search starts, not what it rewrites its answer into.
     *
     * <p>Two of these rows cannot be composed from the classes at all. A rule relating {@code lo}
     * and {@code hi} refuses the pair a composition names — each class offers the bottom of its own
     * range, and the bottom of one against the bottom of the other is a value the model rules out —
     * while the same class against the model's own {@code mid} builds. Composed first and rewritten
     * after, the class came back as one nothing offered a row for.
     *
     * <p>And a row that needs another field moved beside the one it is about moves that one and no
     * more. Composing instead moves every position to whatever the classes named there, which is
     * what a row about one class exists not to be (issue #967).
     */
    @Test
    void aClassIsWrittenAgainstTheModelsOwnValueOrNotWrittenAtAll() {
        Adequacy.Filling filling = generated(CORRELATED).get("submit");

        assertEquals(List.of(
                        // The target and one supporting field: `hi` at the bottom of its lower
                        // class is under `mid`'s `lo`, which the rule refuses, so `lo` moves too.
                        // Which is every field `Request` has, so the row is written out.
                        "Request { lo = Amount(0), hi = Amount(0) }",
                        // `mid` is already in the upper class of `hi`, so the row is `mid`.
                        "mid",
                        "Request { ...mid, lo = Amount(0) }",
                        // And already in the upper class of `lo`.
                        "mid"),
                filling.composed().rows().stream()
                        .map(CompileExampleGenerateTest::inputsOf).toList());
        assertEquals(List.of(), filling.composed().unresolved(),
                "every class is written for, which composing them could not do");
    }

    /**
     * A class the stated value already sits in is answered by that value, written out as it stands.
     *
     * <p>How far a row is from what a reader recognises is what the search minimises, and a value of
     * the module already in the class is no distance from it. The position the row is about used to
     * be written over whatever it held — the walk counted it as moved for being the target rather
     * than for the row differing there — so a class {@code mid} answers came out as {@code mid} with
     * {@code lo} set to a value of the class it already stood in.
     *
     * <p>Which says less than the value it was written from. A reader is told to write
     * {@code Request {...mid, lo = Amount(51)}} where {@code mid} covers the class, and
     * has to compare two numbers against a range to see the spread changes nothing.
     */
    @Test
    void aClassTheStatedValueIsAlreadyInIsWrittenAsThatValue() {
        List<String> rows = generated(CORRELATED).get("submit").composed().rows().stream()
                .map(CompileExampleGenerateTest::inputsOf).toList();

        assertEquals(2, rows.stream().filter("mid"::equals).count(),
                "`mid` is in the upper class of both positions: " + rows);
    }

    /**
     * A row that moves every field of a value is written out rather than spread over it.
     *
     * <p>The spread is what says the row is a value the reader recognises with something changed.
     * Where the row changes the whole record, the value it spreads contributes nothing to what is
     * built — {@code Request {...mid, hi = Amount(0), lo = Amount(0)}} names {@code mid}
     * and keeps none of it — and a reader comparing the row against the file finds every field
     * different.
     *
     * <p>Written out, and not composed again. The values are the ones the search built and offered
     * from {@code mid}: a rule relating {@code lo} and {@code hi} refuses the pair the classes name
     * while the model's own value builds, so composing this parameter a second time would be a
     * different row wearing this one's answer.
     */
    @Test
    void aRowThatMovesEveryFieldIsWrittenOutRatherThanSpread() {
        List<String> rows = generated(CORRELATED).get("submit").composed().rows().stream()
                .map(CompileExampleGenerateTest::inputsOf).toList();

        assertTrue(rows.contains("Request { lo = Amount(0), hi = Amount(0) }"),
                "the row for the lower class of `hi` moves both fields: " + rows);
        assertTrue(rows.stream().noneMatch(row -> row.startsWith("Request { ...mid, hi =")
                        && row.contains("lo =")),
                "and nothing is spread over a value it writes over entirely: " + rows);
    }

    /**
     * A second value of the same type is another origin, not the end of them.
     *
     * <p>Which of an author's values is the ordinary one is not this compiler's to decide, and it
     * used to answer that by refusing to use either — so a module that stated a second value lost
     * the spread from every row of every behavior taking that type. A change somewhere else in the
     * file, answering a question nobody asked it.
     *
     * <p>What decides which is used is how far the row has to move from it, which is the search's
     * question and not a rule about the names.
     */
    @Test
    void aSecondValueOfOneTypeIsAnotherOriginRatherThanNone() {
        String twice = CORRELATED.replace("let mid = Request { lo = Amount(60), hi = Amount(70) }",
                """
                        let mid = Request { lo = Amount(60), hi = Amount(70) }
                        let wide = Request { lo = Amount(0), hi = Amount(200) }""");

        List<String> rows = generated(twice).get("submit").composed().rows().stream()
                .map(CompileExampleGenerateTest::inputsOf).toList();

        // Named outright where the value is already in the class, and spread where the row moves
        // away from it. Both are the row written against what the module states; which of the two
        // it is, is how far the row had to move.
        assertTrue(rows.stream().allMatch(row -> row.equals("mid") || row.equals("wide")
                        || row.contains("...mid") || row.contains("...wide")),
                "every row is still written against a value the model states: " + rows);
    }

    /**
     * Between two values one distance away, the one a row already names comes first.
     *
     * <p>A row of the author's is what they reached for at this behavior, and it names its
     * positions together — which is more than this can say of one value chosen per position on its
     * own. So it is the first origin, and the rows offered beside it are written against the same
     * value the row beside them is.
     *
     * <p>Asked where the two are the same distance from the class, because distance is what this
     * search minimises and provenance orders the origins within one distance. The two values here
     * sit in the same classes as each other, so which is used is the order they were gathered in
     * and nothing else.
     */
    @Test
    void theValueARowAlreadyNamesIsTheFirstOrigin() {
        String written = CORRELATED.replace("let mid = Request { lo = Amount(60), hi = Amount(70) }",
                """
                        let mid = Request { lo = Amount(60), hi = Amount(70) }
                        let near = Request { lo = Amount(62), hi = Amount(72) }""")
                + """

                        example submit
                            | "a near one" : (near) -> Accepted { at = "now" }
                        """;

        // Every row offered, whatever it was composed for. A row through an arm is written against
        // a value the model states the way a row for a class is, so a filter here would be leaving
        // out the rows this used to be unable to say anything about (issue #1034).
        List<String> rows = generated(written).get("submit").composed().rows().stream()
                .map(CompileExampleGenerateTest::inputsOf).toList();
        assertFalse(rows.isEmpty(), "there are classes the written row is not in");
        // Where a row keeps any of the value it was written from, that value is the author's own.
        // The row that keeps none of it moves every field, and is written out rather than spread
        // over a value it contributes nothing to — which is a fact about how it reads and not about
        // which value it came from.
        assertTrue(rows.stream().anyMatch(row -> row.contains("...near")),
                "the author's own value is what the rows are written from: " + rows);
        assertTrue(rows.stream().noneMatch(row -> row.contains("...") && !row.contains("...near")),
                "and no row is written from any other: " + rows);
    }

    /** Each named for the one class it is about, whatever it took to write one. */
    @Test
    void aFieldMovedToMakeARowBuildableIsNoPartOfWhatItIsFor() {
        assertEquals(List.of(List.of("request.hi=0 <= x <= 60"), List.of("request.hi=60 < x"),
                        List.of("request.lo=0 <= x <= 50"), List.of("request.lo=50 < x")),
                generated(CORRELATED).get("submit").composed().rows().stream()
                        .map(row -> row.labels()).toList());
    }

    private static String inputsOf(Generator.GeneratedRow row) {
        return String.join(", ", row.inputs().stream()
                .map(souther.compiler.partition.FixtureTemplate::text).toList());
    }

    /**
     * The rows come out in the form {@code souther fmt} writes them.
     *
     * <p>These lines are meant to be pasted into a file the formatter then runs over. A block in a
     * shape the formatter would change turns a paste into a diff on the next commit.
     *
     * <p>Asked of the block and not of a block somebody has answered. A row is written with the mark
     * in it, and an answer is wider than the mark — so the line an author ends up with is a
     * different width from the one offered, and what the formatter does about <em>that</em> is the
     * author's own {@code fmt} run rather than anything this block chose. What this holds is that
     * nothing the block does to the formatter's output afterwards — taking off the header it needed
     * to parse, writing the prose beside the rows — leaves a line the formatter would not have
     * written.
     */
    @Test
    void theBlockIsWrittenInTheFormattersOwnShape() {
        String rows = "examples for example.trip\n\n"
                + sourceOf(blockOf(TRIP, "example.trip", false));

        assertEquals(rows, souther.compiler.fmt.Formatter.format(rows));
    }

    /**
     * The behaviors come out in the order the module declares them.
     *
     * <p>A block is read against the one before it, to see what the last change to the model added. So
     * where a behavior's rows sit has to be a fact about the model and not about the run that printed
     * them: rows that moved make every line look changed, and there is nothing left to read the block
     * for. Declaration order is the order the module already has, and the one the report prints in.
     */
    @Test
    void theBlockNamesTheBehaviorsInTheOrderTheyAreDeclared() {
        List<String> declared = List.of("submit", "approve", "reject", "withdraw", "reopen", "close");
        StringBuilder behaviors = new StringBuilder();
        StringBuilder rows = new StringBuilder();
        for (String name : declared) {
            behaviors.append("""
                    behavior %1$s : (r: Req) -> Ok
                        constructs Ok

                    let %1$s (r) = Ok { n = 0 }

                    """.formatted(name));
            rows.append("""
                    example %s
                        | (Req { flag = Yes }) -> Ok { n = 0 }

                    """.formatted(name));
        }
        String model = """
                module example.order

                data Yes
                data No
                data Flag = Yes | No

                data Req = { flag: Flag }
                data Ok = { n: Int }

                %s""".formatted(behaviors);
        // The rows sit in a companion source, which is where a module with this many behaviors puts
        // them, and which is the arrangement the block is read against.
        String companion = """
                examples for example.order

                %s""".formatted(rows);

        Compilation compilation = Compilation.ofSources(List.of(model, companion),
                souther.compiler.meta.ModulePath.EMPTY);
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        String block = GeneratedRows.of(compilation, null, null, false, SourceRendering.namedByIdentity(compilation.texts())).text();

        assertEquals(declared, block.lines()
                        .filter(line -> line.startsWith("example "))
                        .map(line -> line.substring("example ".length()))
                        .toList(),
                block);
    }

    /** Nothing to fill is nothing printed, rather than a header over an empty list. */
    @Test
    void aModelTheRowsCoverPrintsNothing() {
        String covered = TRIP + """
                    | (Request { kind = Domestic, urgent = false }) -> Accepted { at = "now" }
                    | (Request { kind = Overseas, urgent = true }) -> Accepted { at = "now" }
                    | (Request { kind = Overseas, urgent = false }) -> Accepted { at = "now" }
                """;

        assertEquals("", blockOf(covered, "example.trip", false));
    }

    /**
     * A model with a gap at every point of a border, and a search that composes a row for none of
     * them: the string the rules admit is one the generator's candidates never spell.
     *
     * <p>What it is for is the note beside a withheld row. A run asking for no edges withholds the
     * rows at them, so a line saying why one could not be composed is a line about work that run did
     * not ask for — and an author reads it as a report on the rows above it.
     */
    private static final String EVERY_POINT_UNFILLED = """
            module sz.gen

            data Size = Int
                invariant value >= 1

            data Tag = Big | Small

            data C = String
                invariant String.length(value) >= 2 && String.matches("[0-9]+", value)

            behavior label : (c: C, s: Size) -> Tag

            let label (c, s) = if s.value >= 5 then Big else Small

            example label
                | "digits" : (C("123"), Size(9)) -> Big
            """;

    /**
     * The edges are said where the edges were asked for, at every point of them.
     *
     * <p>A border owes rows at four points and they are reported under two kinds — the two against
     * the line and the two away from it. Written to one of the kinds, the flag withheld the rows at
     * all four and printed the notes for two of them.
     */
    @Test
    void aNoteAboutABorderPointIsSaidWhereTheBordersWereAskedFor() {
        String asked = blockOf(EVERY_POINT_UNFILLED, "sz.gen", true);
        String notAsked = blockOf(EVERY_POINT_UNFILLED, "sz.gen", false);

        // One against the line and one away from it, so neither kind is answering for the other.
        assertTrue(asked.contains("// no row for `s = 5` in `label`"), asked);
        assertTrue(asked.contains("// no row for `1 < s < 5` in `label`"), asked);
        assertFalse(notAsked.contains("`s = 5`"),
                "no edge is spoken of in a run that asked for none: " + notAsked);
        assertFalse(notAsked.contains("`1 < s < 5`"),
                "and no point away from one either: " + notAsked);
        // And what a run that asked for no edges does still say, so this is not passing on a block
        // with nothing in it. The arm is looked for at the classes the way into it leaves, and
        // every value of them is refused here — which is the search's answer and is said as one.
        assertTrue(notAsked.contains("// no row for `else` in `label`: every value tried was"
                + " refused at construction"), notAsked);
    }

    /**
     * A floor a settled sibling made concrete is offered where the value is chosen, too.
     *
     * <p>The value at a position is chosen twice over: once with every position taking its value
     * knowing only what the caller settled, and again a position at a time, each from what is left
     * once the ones before it are asserted. A rule counting one field against another asks for
     * nothing in particular in the first pass — {@code n} could be anything the domain keeps — and
     * asks for something definite in the second, once {@code n} has been chosen. A second pass
     * reading only what its siblings leave a <em>number</em> offers the list that holds nothing,
     * which is the value the rule refuses.
     *
     * <p>The edge is on {@code bill}, so nothing settles {@code n} from outside and the row has to
     * choose it. {@code n} cannot be zero and the domain keeps no hole, so the first pass offers a
     * zero the model refuses and the row is left to the second.
     */
    @Test
    void aFloorReachedByChoosingASiblingFirstIsOfferedThere() {
        String correlated = """
                module example.paired

                data Yen = Int
                    invariant value >= 0

                data Count = Int
                    invariant within = value >= 0 && value <= 5
                    invariant notNone = value /= 0

                data Paired = { n: Count, xs: List<Int> }
                    invariant enough = List.length(xs) >= n.value

                data Accepted = { at: String }

                behavior submit : (bill: Yen, request: Paired) -> Accepted
                    constructs Accepted

                let submit (bill, request) = Accepted { at = "now" }
                """;
        Generator.GenerationResult offered = atTheLines(correlated, "submit");

        Generator.GeneratedRow atTheEdge = offered.rows().stream()
                .filter(row -> row.labels().contains("bill = 0")).findFirst().orElse(null);
        assertNotNull(atTheEdge, "the edge on `bill` is a row somebody can write: "
                + offered.unresolved());
        assertEquals("Paired { n = Count(1), xs = [0] }",
                atTheEdge.inputs().get(1).text(),
                "as many elements as the n this row settled on asks for");
    }

    /**
     * And why nothing was built reads that floor too, where the edge is what made it concrete.
     *
     * <p>The row is at {@code n}'s upper edge, so the list it needs is a hundred thousand long —
     * more than a row is built to carry. What is owed is "nothing here composes one", which is about
     * this compiler; "every value tried was refused" would send an author looking for the rule that
     * refuses the empty list, and the rule that refuses it is the one this row settled.
     */
    @Test
    void aFloorAnEdgeMadeConcreteIsWhyNothingWasBuilt() {
        String correlated = """
                module example.paired

                data Count = Int
                    invariant within = value >= 0 && value <= 100000

                data Paired = { n: Count, xs: List<Int> }
                    invariant enough = List.length(xs) >= n.value

                data Accepted = { at: String }

                behavior submit : (request: Paired) -> Accepted
                    constructs Accepted

                let submit (request) = Accepted { at = "now" }
                """;
        // Both authorities, because the lines are the clause's: nothing in this body cuts the count,
        // so every point of them is the declaration's and none is in this behavior's own filling.
        Generator.GenerationResult edges = atTheLines(correlated, "submit");

        Generator.UnresolvedCombination atTheTop = edges.unresolved().stream()
                .filter(left -> left.subject().contains("100000")).findFirst().orElse(null);
        assertNotNull(atTheTop, "the top edge is owed a row and got none: " + edges.unresolved()
                + " rows " + inputs(edges));
        assertEquals(Generator.UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE,
                atTheTop.reason(), atTheTop.toString());
    }

}
