package souther.compiler;

import souther.compiler.check.CheckedEnsures;
import souther.compiler.observe.WrittenStatements;
import souther.compiler.diag.Primary;

import souther.compiler.source.SourceId;
import souther.compiler.diag.QuotedFrom;

import souther.compiler.execute.jvm.JvmDeadlines;
import souther.compiler.execute.jvm.JvmExampleDeadlines;
import souther.compiler.execute.EvaluationPolicy;
import souther.compiler.examples.ExampleStatements;
import souther.compiler.examples.ExampleVerifier;
import org.junit.jupiter.api.Test;

import souther.compiler.diag.CompileException;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.HumanRenderer;
import souther.compiler.diag.Located;
import souther.compiler.meta.ModulePath;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@code fake} and an {@code example} row are two statements about what one behavior answers,
 * written independently: the rows say what it will owe, the fake says what it stands in with while
 * some other behavior's row runs. Where both answer one input and answer it differently, that is
 * said — at both of the places it is written, and with neither named as the right one. A model being
 * migrated onto may run against a stand-in while the real answer is still being harvested, which is
 * written here exactly as a mistake is.
 */
class CompileFakeExampleDisagreementTest {

    private static final String BASE = """
            module example.clash

            data MemberId = String
            data Found = { id: MemberId }
            data Missing = { why: String }

            data Order = { by: MemberId }
            data Placed = { by: MemberId }
            data Refused = { why: String }

            behavior findMember : (id: MemberId) -> Found | Missing

            behavior place : (order: Order) -> Placed | Refused
                depends on findMember
                constructs Placed, Refused

            let place (order, findMember) = match findMember(order.by) with
                | Found    -> Placed { by = order.by }
                | Missing  -> Refused { why = "unknown" }
            """;

    /** The E1919s of a single-source compile, in the order they were said. */
    private static List<Located> disagreements(String model) {
        return onlyDisagreements(warningsOf(model));
    }

    private static List<Located> onlyDisagreements(List<Located> warnings) {
        return only("E1919", warnings);
    }

    private static boolean anyDisagreement(String model) {
        return !disagreements(model).isEmpty();
    }

    // --- a fake row and a recorded row --------------------------------------------------------

    @Test
    void aFakeAndARowThatAnswerOneInputDifferentlyAreSaidAtBoth() {
        List<Located> found = disagreements(BASE + """

                example findMember
                    | "m-1 is a member" : (MemberId("m-1")) -> Found { id = MemberId("m-1") }

                fake findMember
                    | (MemberId("m-1")) -> Missing { why = "no such member" }

                example place
                    | "m-1 cannot order" : (Order { by = MemberId("m-1") }) -> Refused { why = "unknown" }
                """);

        // One disagreement, one warning. It is anchored at the row and points at the fake row:
        // both are written statements, and which of them is right is not what this reports.
        assertEquals(1, found.size(), found.toString());
        Diagnostic one = found.get(0).diagnostic();
        assertEquals(22, ((Primary.InSource) one.primary()).place().region().start().line(), "anchored at the recorded row");
        assertEquals(1, one.secondary().size(), one.secondary().toString());
        assertEquals(25, ((souther.compiler.diag.DiagnosticPlace.InSource) one.secondary().get(0).place()).region().start().line(), "pointing at the fake row");
        assertEquals(new QuotedFrom.ASourceThisCompileHolds(((souther.compiler.diag.DiagnosticPlace.InSource) one.secondary().get(0).place()).source()), ((Primary.InSource) one.primary()).place().region().start().quotedFrom(),
                "both are in this source, and the second region says so rather than leaving a"
                        + " reader to work it out from where the diagnostic was filed");
    }

    @Test
    void aFakeAndARowThatAgreeSayNothing() {
        assertFalse(anyDisagreement(BASE + """

                example findMember
                    | "m-1 is a member" : (MemberId("m-1")) -> Found { id = MemberId("m-1") }

                fake findMember
                    | (MemberId("m-1")) -> Found { id = MemberId("m-1") }
                """));
    }

    @Test
    void aRowIsStillHeldAgainstAStaleFakeOnceTheLetArrives() {
        // `findMember` is implemented here, so its rows are run rather than recorded — and the fake
        // beside them is still a second statement about what it answers.
        assertTrue(anyDisagreement("""
                module example.clash

                data MemberId = String
                data Found = { id: MemberId }
                data Missing = { why: String }

                behavior findMember : (id: MemberId) -> Found | Missing
                    constructs Found

                let findMember (id) = Found { id = id }

                example findMember
                    | "m-1 is a member" : (MemberId("m-1")) -> Found { id = MemberId("m-1") }

                fake findMember
                    | (MemberId("m-1")) -> Missing { why = "no such member" }
                """));
    }

    // --- what a fake actually answers for the input -------------------------------------------

    @Test
    void aDefaultRowAnswersAnInputNoExplicitRowStates() {
        // The default is what the fake answers `m-1` with, so it is what `m-1`'s recorded row is
        // held against — the fake's own dispatch decides which row stands in, not the shape of it.
        assertTrue(anyDisagreement(BASE + """

                example findMember
                    | "m-1 is a member" : (MemberId("m-1")) -> Found { id = MemberId("m-1") }

                fake findMember
                    | _ -> Missing { why = "no such member" }
                """));
    }

    @Test
    void anExplicitRowIsWhatAnswersWhereOneStatesTheInput() {
        assertFalse(anyDisagreement(BASE + """

                example findMember
                    | "m-1 is a member" : (MemberId("m-1")) -> Found { id = MemberId("m-1") }

                fake findMember
                    | (MemberId("m-1")) -> Found { id = MemberId("m-1") }
                    | _ -> Missing { why = "no such member" }
                """));
    }

    @Test
    void aTableThatAnswersNothingForTheInputSaysNothing() {
        assertFalse(anyDisagreement(BASE + """

                example findMember
                    | "m-1 is a member" : (MemberId("m-1")) -> Found { id = MemberId("m-1") }

                fake findMember
                    | (MemberId("m-9")) -> Missing { why = "no such member" }
                """));
    }

    /**
     * Two blocks naming one behavior are compared with nothing.
     *
     * <p>What this comparison holds against a recorded row is a stand-in, and a behavior more than
     * one block names has none (E1933). Neither of them is a second statement about the input the
     * row states, and reporting one as disagreeing with the row would name a table that stands in
     * for nothing as one of the two the model is to be held to.
     */
    @Test
    void twoTablesForOneBehaviorAreHeldAgainstNoRow() {
        List<String> codes = allCodesOf(BASE + """

                example findMember
                    | "m-1 is a member" : (MemberId("m-1")) -> Found { id = MemberId("m-1") }

                fake findMember
                    | (MemberId("m-1")) -> Found { id = MemberId("m-1") }

                fake findMember
                    | (MemberId("m-1")) -> Missing { why = "no such member" }
                """);

        assertEquals(List.of("E1933", "E1933"), codes, codes.toString());
    }

    /**
     * A table with a row that will not build is one the fake cannot stand in with at all (E1908), so
     * it answers nothing — not even for the inputs its other rows state. Reading one row of it would
     * report what a fake answers for a table the runner refuses to build.
     */
    @Test
    void aTableWithAnUnbuildableRowAnswersNothing() {
        assertEquals(List.of("E1908"), allCodesOf("""
                module example.clash
                import String ( length )

                data MemberId = String
                    invariant length(value) > 0

                data Found = { id: MemberId }
                data Missing = { why: String }

                behavior findMember : (id: MemberId) -> Found | Missing

                behavior place : (id: MemberId) -> Found | Missing
                    depends on findMember
                let place (id, findMember) = findMember(id)

                example findMember
                    | "m-1 is a member" : (MemberId("m-1")) -> Found { id = MemberId("m-1") }

                fake findMember
                    | (MemberId("m-1")) -> Missing { why = "none" }
                    | (MemberId("m-2")) -> Found { id = MemberId("") }

                example place
                    | "runs" : (MemberId("m-1")) -> Missing { why = "none" }
                """));
    }

    /**
     * A row expecting a case the behavior has no arm for records nothing about it — that is E1904 —
     * so there is no answer of its for a stand-in to disagree with.
     */
    @Test
    void aRowExpectingACaseTheBehaviorCannotAnswerWithStatesNothing() {
        assertEquals(List.of("E1904"), allCodesOf("""
                module example.clash

                data Found = { id: String }
                data Missing = { why: String }
                data Unauthorized = { why: String }

                behavior find : (id: String) -> Found | Missing

                example find
                    | "blocked" : ("m-1") -> Unauthorized { why = "blocked" }

                fake find
                    | ("m-1") -> Missing { why = "none" }
                """));
    }

    /**
     * A row whose fixture did not answer states nothing, so nothing is held against it. What
     * happened is said where the row is evaluated (E1923), and the reading adds nothing beside it.
     *
     * <p>This reaches the reading through {@code builtOrNull}, which drops the row as the fixture
     * failure it is. The other way a reading can end — a helper that overruns the budget on the
     * reading's own worker — is handled where the worker is joined, and nothing here provokes it.
     */
    @Test
    void aRowWhoseFixtureWillNotFinishIsHeldAgainstNothing() {
        CompileException e = org.junit.jupiter.api.Assertions.assertThrows(CompileException.class,
                () -> DoesNotComeBack.compileOverrunning("""
                        module example.spin

                        data N = Int
                        data Found = { n: N }
                        data Missing = { why: String }

                        partial let spin (n: Int): Int = spin(n)

                        behavior find : (n: N) -> Found | Missing

                        example find
                            | "loops" : (N(spin(1))) -> Found { n = N(0) }

                        fake find
                            | (N(1)) -> Missing { why = "none" }
                        """, DoesNotComeBack.everythingAboutRowsOf("find")));

        assertTrue(codesOf(e).contains("E1923"), codesOf(e).toString());
        assertFalse(codesOf(e).contains("E1919"), codesOf(e).toString());
    }

    // --- a table that did not finish ----------------------------------------------------------

    private static final String UNUSED_FAKE = """
            module example.table

            data N = Int
            data Found = { n: N }
            data Missing = { why: String }

            partial let spin (n: Int): Int = spin(n)

            behavior find : (n: N) -> Found | Missing
                constructs Found

            let find (n) = Found { n = n }

            example find
                | "one" : (N(1)) -> Found { n = N(1) }
            """;

    /**
     * A fake nobody depends on is built here and nowhere else, so here is where running out of budget
     * has to be said.
     *
     * <p>The other place a table is built is {@code resolveFake}, which runs while a row of a behavior
     * that depends on the faked one is evaluated. `find` has no such row — nothing depends on it — so
     * the reading is the table's only reader, and a table that never finishes would otherwise leave
     * "these two agree" as the answer to a question nobody got to ask.
     *
     * <p>Two rows that will not build, and one warning: the whole table is one reading held to one
     * budget, so what is said is said about the fake and not about a row of it.
     */
    @Test
    void aTableThatDidNotFinishIsSaidOnceAtTheFake() {
        List<Located> said = only("E1920", warningsOf(UNUSED_FAKE + """

                fake find
                    | (N(spin(1))) -> Missing { why = "none" }
                    | (N(spin(2))) -> Missing { why = "none" }
                """, DoesNotComeBack.overrunningOn(DoesNotComeBack.everyTableOf("find"))));

        assertEquals(1, said.size(), said.toString());
        Diagnostic one = said.get(0).diagnostic();
        assertEquals(17, ((Primary.InSource) one.primary()).place().region().start().line(), "anchored where the fake names the behavior");
        assertEquals(6, ((Primary.InSource) one.primary()).place().region().start().column());
        // What could not be done, then what stopped: the table is what did not answer, and the
        // comparison is what that cost. The number is read off the wait this compile was given
        // rather than written in, so the line still holds if that wait changes — and it is read as
        // it is set and not as a locale would group it, which is what the number in this line is for.
        assertTrue(rendered(one).contains("Could not compare this fake with the rows recorded for"
                        + " `find` — building the table did not answer within "
                        + DoesNotComeBack.WAIT.toMillis() + "ms."),
                rendered(one));
    }

    /** And a table that finishes says nothing: the code is not one every fake gets. */
    @Test
    void aTableThatFinishesIsSaidNowhere() {
        assertEquals(List.of(), only("E1920", warningsOf(UNUSED_FAKE + """

                fake find
                    | (N(1)) -> Found { n = N(1) }
                """)));
    }

    /**
     * A row that runs the table and the reading of the table are two attempts, and each says what it
     * found: the row did not answer (E1923, at the row), and what the fake and the recorded rows state
     * was never compared (E1920, at the fake). Neither stands for the other — a table can overrun the
     * reading with no row that uses it, and a row can overrun for what its own behavior does.
     */
    @Test
    void aRowThatRunsTheTableAndTheReadingBothSayWhatTheyFound() {
        List<Located> warnings = new ArrayList<>();
        CompileException e = org.junit.jupiter.api.Assertions.assertThrows(CompileException.class,
                () -> DoesNotComeBack.compileModulesOverrunning(List.of("""
                        module example.both

                        data N = Int
                        data Found = { n: N }
                        data Missing = { why: String }
                        data Ok = { n: N }

                        partial let spin (n: Int): Int = spin(n)

                        behavior find : (n: N) -> Found | Missing

                        behavior use : (n: N) -> Ok | Missing
                            depends on find
                            constructs Ok, Missing

                        let use (n, find) = match find(n) with
                            | Found   -> Ok { n = n }
                            | Missing -> Missing { why = "none" }

                        example find
                            | "one" : (N(1)) -> Found { n = N(1) }

                        example use
                            | "one" : (N(1)) -> Ok { n = N(1) }

                        fake find
                            | (N(spin(1))) -> Missing { why = "none" }
                        """), warnings, DoesNotComeBack.everyTableOf("find").or(DoesNotComeBack.everyRowOf("use"))));

        assertTrue(codesOf(e).contains("E1923"), codesOf(e).toString());
        assertEquals(1, only("E1920", warnings).size(), warnings.toString());
    }

    /**
     * A host with no runtime says nothing about any fake. That is a fact about the host — the runtime
     * is `provided`, as it is for CTFE — so it is not turned into a warning per fake; where the rows
     * are evaluated it is recorded once, as an {@code Incompleteness}, which is what a measure
     * reading the rows needs.
     *
     * <p>The same model read with the runtime present says the two statements disagree, so what is
     * being checked is a reading that reaches a value and not one that never started.
     *
     * <p>Where it stops is before a fake: with no runtime the recorded rows do not build either, and
     * a reading with no rows read has nothing to hold a stand-in against. So this says what the
     * module answers and not which arm answered it — {@code againstFake} is not reached, and its
     * {@code RuntimeAbsent} arm is what a fake would meet if a row ever got past one.
     */
    @Test
    void aHostWithNoRuntimeSaysNothingAboutAnyFake() {
        String model = BASE + """

                example findMember
                    | "m-1 is a member" : (MemberId("m-1")) -> Found { id = MemberId("m-1") }

                fake findMember
                    | (MemberId("m-1")) -> Missing { why = "no such member" }
                """;

        assertEquals(1, readingOf(model, ExampleVerifier.class.getClassLoader()).disagreements().size(),
                "with a runtime, the fake and the row are read and disagree");

        WrittenStatements.Readings withoutRuntime = readingOf(model, new ClassLoader(null) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("souther.runtime.")) {
                    throw new NoClassDefFoundError(name);
                }
                return ExampleVerifier.class.getClassLoader().loadClass(name);
            }
        });

        assertEquals(List.of(), withoutRuntime.disagreements());
        assertEquals(List.of(), withoutRuntime.unread());
    }

    /** {@code disagreements} as the query asks it, but against a class loader the test chooses. */
    private static WrittenStatements.Readings readingOf(String model, ClassLoader parent) {
        souther.compiler.query.Compilation c =
                souther.compiler.query.Compilation.ofSource(model, "Main");
        c.db().ask(new souther.compiler.query.Output.All());
        String name = c.modules().get(0);
        souther.compiler.check.Prepared prepared =
                c.db().ask(new souther.compiler.query.Shapes.Prepared(name)).value();
        return ExampleStatements.disagreements(
                prepared.forExamples(),
                souther.compiler.query.Scopes.derived(c.db(), name).value(),
                souther.compiler.query.ExampleExecutions.of(c.db(), name).fieldTypes(),
                c.db().ask(new souther.compiler.query.Bodies.Reachable(name)).value(),
                c.db().ask(new souther.compiler.query.Output.EvaluationLinked(
                        name, souther.compiler.observe.ArmObservation.OMIT)).value().classes(),
                parent,
                c.db().ask(new souther.compiler.query.Bodies.ModuleDefinitions(name)).value(),
                JvmDeadlines.of(EvaluationPolicy.DEFAULT.compilerTimeout()),
                EvaluationPolicy.DEFAULT,
                CheckedEnsures.executableOf(c.db().ask(
                        new souther.compiler.query.Bodies.ReachableContracts(name)).value()),
                // One source, so there is no module whose rows this one stands in for a behavior of.
                java.util.Map.of());
    }

    /** The warnings of a single-source compile that holds. */
    private static List<Located> warningsOf(String model) {
        List<Located> out = new ArrayList<>();
        assertDoesNotThrow(() -> Compiler.compiled(model, "Main", out));
        return out;
    }

    /** As {@link #warningsOf(String)}, for a model whose table does not finish being built —
     * which is said here rather than waited for. */
    private static List<Located> warningsOf(String model, JvmExampleDeadlines overrun) {
        List<Located> out = new ArrayList<>();
        assertDoesNotThrow(() -> Compiler.compiled(model, "Main", out,
                souther.compiler.query.Adequacy.Asked.NOTHING, DoesNotComeBack.WAIT, overrun));
        return out;
    }

    private static List<String> codesOf(CompileException e) {
        List<String> codes = new ArrayList<>();
        for (Diagnostic d : e.diagnostics()) {
            codes.add(d.code());
        }
        return codes;
    }

    private static String rendered(Diagnostic d) {
        return new HumanRenderer(false).render(d, null, Locale.ENGLISH);
    }

    private static List<Located> only(String code, List<Located> warnings) {
        List<Located> found = new ArrayList<>();
        for (Located w : warnings) {
            if (code.equals(w.diagnostic().code())) {
                found.add(w);
            }
        }
        return found;
    }

    /**
     * A `with` stands in with a value. `Missing` has fields, so its name stands for no value —
     * which the language says where the name is written, as it does anywhere else a name is
     * written where a value goes. Nothing about stand-ins is involved, and the row never gets as
     * far as having something to disagree with.
     */
    @Test
    void aWithThatCannotBeBuiltStandsInForNothing() {
        CompileException e = org.junit.jupiter.api.Assertions.assertThrows(
                CompileException.class, () -> Compiler.compile("""
                module example.b1

                data Found = { id: String }
                data Missing = { why: String }
                data Done

                behavior lookup : () -> Found | Missing

                behavior use : () -> Done
                    depends on lookup
                let use (lookup) = match lookup() with
                    | Found   -> Done
                    | Missing -> Done

                example lookup
                    | "found" : () -> Found

                example use
                    | "runs" : () with lookup = Missing -> Done
                """));
        assertEquals("E1023", e.diagnostic().code(), e.getMessage());
        assertTrue(e.getMessage().contains("Missing"), e.getMessage());
    }

    /**
     * A fixture that will not finish costs its own reading and no other. One row read within one
     * budget would let a row nobody is asking about spend what every other statement in the module
     * needed, and a plain contradiction elsewhere would go unsaid because of it.
     */
    @Test
    void aRowThatWillNotFinishDoesNotTakeTheRestOfTheModuleWithIt() {
        assertEquals(List.of("E1923", "E1919"), allCodesOf("""
                module example.b2

                data N = Int
                data Found = { id: String }
                data Missing = { why: String }

                partial let spin (n: Int): Int = spin(n)

                behavior findMember : (id: String) -> Found | Missing
                behavior other : (n: N) -> Found | Missing

                example findMember
                    | "clash" : ("m-1") -> Found { id = "m-1" }

                fake findMember
                    | ("m-1") -> Missing { why = "none" }

                example other
                    | "loops" : (N(spin(1))) -> Found { id = "x" }

                fake other
                    | (N(1)) -> Missing { why = "none" }
                """, DoesNotComeBack.overrunningOn(DoesNotComeBack.everythingAboutRowsOf("other"))));
    }

    /**
     * Which case a value is comes from the value, not from the call that produced it. A fake row
     * answering through a helper states the case that helper returned, and a row naming only a case
     * is held against that.
     */
    @Test
    void aCaseAHelperAnsweredWithIsWhatTheFakeStates() {
        assertEquals(List.of("E1919"), allCodesOf("""
                module example.m3b

                data Found = { id: String }
                data Missing = { why: String }

                let missing (reason: String): Missing = Missing { why = reason }

                behavior lookup : () -> Found | Missing

                example lookup
                    | "found" : () -> Found

                fake lookup
                    | _ -> missing("none")
                """));
    }

    /**
     * A primitive case of a union is a case like any other. Its values arrive as the class that
     * carries them — an `Int` as a `Long` — so a case read off the class a value is in finds nothing
     * and the row naming the other case is held against nothing.
     */
    @Test
    void aPrimitiveCaseOfAUnionIsACaseLikeAnyOther() {
        assertEquals(List.of("E1919"), allCodesOf("""
                module example.prim

                data Missing = { why: String }

                behavior lookup : () -> Int | Missing

                example lookup
                    | "absent" : () -> Missing

                fake lookup
                    | _ -> 1
                """));
    }

    /** Every example-family code the compile reported, in order. Read off the reports so that the
     * error a case is really about is visible beside the E1919s, and a test cannot pass by the whole
     * comparison having stopped working. */
    private static List<String> allCodesOf(String model) {
        return allCodesOf(model, null);
    }

    /** As {@link #allCodesOf(String)}, for a model with a row that does not come back. */
    private static List<String> allCodesOf(String model, JvmExampleDeadlines overrun) {
        souther.compiler.query.Compilation compilation =
                souther.compiler.query.Compilation.ofSource(model, "Main");
        if (overrun != null) {
            compilation.withJvmExampleDeadlines(overrun);
        }
        compilation.answerEverything();
        List<String> codes = new ArrayList<>();
        for (souther.compiler.query.Db.Found found : compilation.db().allReports()) {
            String code = found.report().diagnostic().code();
            if (code != null && code.startsWith("E19")) {
                codes.add(code);
            }
        }
        return codes;
    }

    // --- a row that names only a case ---------------------------------------------------------

    @Test
    void aRowThatNamesOnlyACaseIsComparedOnTheCase() {
        assertTrue(anyDisagreement(BASE + """

                example findMember
                    | "m-1 is a member" : (MemberId("m-1")) -> Found

                fake findMember
                    | (MemberId("m-1")) -> Missing { why = "no such member" }
                """));
    }

    @Test
    void aRowThatNamesOnlyACaseAgreesWithAFakeBuildingThatCase() {
        assertFalse(anyDisagreement(BASE + """

                example findMember
                    | "m-1 is a member" : (MemberId("m-1")) -> Found

                fake findMember
                    | (MemberId("m-1")) -> Found { id = MemberId("m-9") }
                """),
                "the row asserts the case and nothing under it");
    }

    @Test
    void twoValuesOfOneCaseThatDifferInAFieldDisagree() {
        assertTrue(anyDisagreement(BASE + """

                example findMember
                    | "m-1 is a member" : (MemberId("m-1")) -> Found { id = MemberId("m-1") }

                fake findMember
                    | (MemberId("m-1")) -> Found { id = MemberId("m-9") }
                """));
    }

    // --- a `with` --------------------------------------------------------------------------------

    private static final String CLOCKED = """
            module example.clocked

            data Stamp = { at: String }
            data Note = { at: String }

            behavior now : () -> Stamp

            behavior record : (text: String) -> Note
                depends on now
                constructs Note

            let record (text, now) = Note { at = now().at }

            example now
                | "the clock reads noon" : () -> Stamp { at = "12:00" }
            """;

    @Test
    void aWithOnADependencyThatTakesNoInputIsHeldAgainstItsRecordedRow() {
        assertTrue(anyDisagreement(CLOCKED + """

                example record
                    | "stamped" : ("x") with now = Stamp { at = "09:00" } -> Note { at = "09:00" }
                """));
    }

    @Test
    void aWithThatAgreesWithTheRecordedRowSaysNothing() {
        assertFalse(anyDisagreement(CLOCKED + """

                example record
                    | "stamped" : ("x") with now = Stamp { at = "12:00" } -> Note { at = "12:00" }
                """));
    }

    /**
     * A `with` on a dependency that takes inputs says nothing about which of them it answers: what
     * reaches the dependency is whatever the parent behavior computes and passes. Rows faking two
     * answers for two orders are each right about their own, and holding either against every
     * recorded row would report a model that agrees with itself as contradicting.
     */
    @Test
    void rowLocalWithsForAnInputTakingDependencyDoNotDisagreeWithItsRecordedRows() {
        assertFalse(anyDisagreement(BASE + """

                example findMember
                    | "m-1 is a member" : (MemberId("m-1")) -> Found { id = MemberId("m-1") }
                    | "m-2 is not"      : (MemberId("m-2")) -> Missing { why = "no such member" }

                example place
                    | "m-1 may order" : (Order { by = MemberId("m-1") })
                        with findMember = Found { id = MemberId("m-1") }
                        -> Placed { by = MemberId("m-1") }
                    | "m-2 is refused" : (Order { by = MemberId("m-2") })
                        with findMember = Missing { why = "no such member" }
                        -> Refused { why = "unknown" }
                """));
    }

    @Test
    void aWithOnADependencyThatTakesInputsIsNotComparedAtAll() {
        assertFalse(anyDisagreement(BASE + """

                example findMember
                    | "m-1 is a member" : (MemberId("m-1")) -> Found { id = MemberId("m-1") }

                example place
                    | "refused" : (Order { by = MemberId("m-1") })
                        with findMember = Missing { why = "no such member" } -> Refused { why = "unknown" }
                """));
    }

    @Test
    void aWithDoesNotSettleWhatTheTableStates() {
        // The `with` takes precedence while this row runs. That is dispatch: the table is still a
        // statement about the same behavior, written for every other row and every other run.
        List<Located> found = disagreements(BASE + """

                example findMember
                    | "m-1 is a member" : (MemberId("m-1")) -> Found { id = MemberId("m-1") }

                fake findMember
                    | _ -> Missing { why = "no such member" }

                example place
                    | "placed" : (Order { by = MemberId("m-1") })
                        with findMember = Found { id = MemberId("m-1") } -> Placed { by = MemberId("m-1") }
                """);

        // The table disagrees. The `with` beside it is not compared and does not stop the table
        // from being.
        assertEquals(1, found.size(), found.toString());
    }

    // --- across files ----------------------------------------------------------------------------

    @Test
    void aFakeInTheModuleIsHeldAgainstARowInAnAttachedFile() {
        String module = BASE + """

                fake findMember
                    | (MemberId("m-1")) -> Missing { why = "no such member" }
                """;
        String attached = """
                examples for example.clash

                example findMember
                    | "m-1 is a member" : (MemberId("m-1")) -> Found { id = MemberId("m-1") }
                """;

        assertSaidInBothFiles(module, attached,
                "each statement is said in the file it is written in");
    }

    @Test
    void aFakeInAnAttachedFileIsHeldAgainstARowInTheModule() {
        String module = BASE + """

                example findMember
                    | "m-1 is a member" : (MemberId("m-1")) -> Found { id = MemberId("m-1") }
                """;
        String attached = """
                examples for example.clash

                fake findMember
                    | (MemberId("m-1")) -> Missing { why = "no such member" }
                """;

        assertSaidInBothFiles(module, attached,
                "the attached file wrote only the fake, and still says its side");
    }

    /** One disagreement across two sources: one warning that quotes both files. */
    private static void assertSaidInBothFiles(String module, String attached, String why) {
        List<Located> out = new ArrayList<>();
        assertDoesNotThrow(() -> Compiler.compiledModules(
                List.of(module, attached), ModulePath.EMPTY, out));
        List<Located> found = onlyDisagreements(out);

        assertEquals(1, found.size(), found.toString());
        Diagnostic one = found.get(0).diagnostic();
        assertEquals(1, one.secondary().size(), one.secondary().toString());
        souther.compiler.source.SourceId primary = found.get(0).context().filedUnder().orElse(null);
        souther.compiler.source.SourceId other = ((souther.compiler.diag.DiagnosticPlace.InSource)
                one.secondary().get(0).place()).source();
        assertNotNull(other, why + ": the second region names the file it is in");
        assertNotEquals(primary, other, why + ": the two statements are in different sources");
        assertEquals(java.util.Set.of(new SourceId("0"), new SourceId("1")), java.util.Set.of(primary, other),
                why + ": " + primary + " and " + other);
    }

    // --- what is not said ------------------------------------------------------------------------

    @Test
    void aRowWhoseInputWillNotBuildIsNotHeldAgainstAnything() {
        // The input breaks its type's invariant, which is E1903's to say. Nothing was read off the
        // row, so there is no answer of its to disagree with.
        List<String> codes = new ArrayList<>();
        CompileException e = org.junit.jupiter.api.Assertions.assertThrows(CompileException.class,
                () -> Compiler.compile("""
                        module example.clash
                        import String ( length )

                        data MemberId = String
                            invariant length(value) > 0

                        data Found = { id: MemberId }
                        data Missing = { why: String }

                        behavior findMember : (id: MemberId) -> Found | Missing

                        example findMember
                            | "empty" : (MemberId("")) -> Found { id = MemberId("m-1") }

                        fake findMember
                            | (MemberId("m-1")) -> Missing { why = "no such member" }
                        """));
        for (souther.compiler.diag.Diagnostic d : e.diagnostics()) {
            codes.add(d.code());
        }
        assertTrue(codes.contains("E1903"), codes.toString());
        assertFalse(codes.contains("E1919"), codes.toString());
    }

    /**
     * A measured compile evaluates every row a second time, against instrumented classes
     * ({@code Adequacy.ProbedExamples}). The disagreement is read off the text and not off either
     * run, so what is said does not depend on how many times the rows were run.
     */
    @Test
    void aDisagreementIsSaidOnceWhateverIsBeingMeasured() {
        String model = BASE + """

                example findMember
                    | "m-1 is a member" : (MemberId("m-1")) -> Found { id = MemberId("m-1") }

                fake findMember
                    | (MemberId("m-1")) -> Missing { why = "no such member" }
                """;

        assertEquals(1, measured(model, souther.compiler.query.Adequacy.Asked.NOTHING));
        assertEquals(1, measured(model,
                souther.compiler.query.Adequacy.Asked.reportOnly(
                        souther.compiler.query.Adequacy.Level.ALL)));
    }

    private static int measured(String model, souther.compiler.query.Adequacy.Asked asked) {
        souther.compiler.query.Compilation compilation =
                souther.compiler.query.Compilation.ofSource(model, "Main");
        compilation.measure(asked);
        compilation.answerEverything();
        int said = 0;
        for (souther.compiler.query.Db.Found found : compilation.db().allReports()) {
            if ("E1919".equals(found.report().diagnostic().code())) {
                said++;
            }
        }
        return said;
    }

    @Test
    void theBuildIsNotFailedByADisagreement() {
        assertDoesNotThrow(() -> Compiler.compile(BASE + """

                example findMember
                    | "m-1 is a member" : (MemberId("m-1")) -> Found { id = MemberId("m-1") }

                fake findMember
                    | (MemberId("m-1")) -> Missing { why = "no such member" }
                """));
    }
}
