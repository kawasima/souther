package souther.compiler.query;

import org.junit.jupiter.api.Test;

import souther.compiler.meta.ModulePath;
import souther.compiler.partition.BorderObligationId;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who owes a row at a line, and who only carries values held to it.
 *
 * <p>A clause of a {@code data} settles a question about the type: whether a string of one character
 * is a {@code Sku} is the same question wherever a {@code Sku} goes, and a row standing there answers
 * it for everybody. So the module that wrote the clause owes the row, and a module importing the type
 * owes nothing — asked for it, four of the five modules carrying a type are asked for work they cannot
 * do and cannot check.
 *
 * <p>The other side of the same rule is that the evidence stays where the debt is. A row written in an
 * importer is a row of that module, and reading it here would make a module's account of its own
 * declarations move when a module it has never heard of is added to the compile.
 */
class ALineIsOwedByTheDeclarationsThatWroteItTest {

    /** Declares a type whose line no row of its own stands at, and carries it. */
    private static final String PRODUCER = """
            module example.producer exposing ( Sku, Item )

            data Sku = String
                invariant String.length(value) >= 1

            data Item = { sku: Sku }

            behavior label : (i: Item) -> Sku
            let label (i) = i.sku

            example label
                | "a sku of more than one character is a sku" :
                    (Item { sku = Sku("abc") }) -> Sku("abc")
            """;

    /** Imports the type, carries it, and writes the row the producer's line asks for. */
    private static final String CONSUMER = """
            module example.consumer

            import example.producer ( Sku, Item )

            behavior tag : (i: Item) -> Sku
            let tag (i) = i.sku

            example tag
                | "the shortest sku there is" : (Item { sku = Sku("a") }) -> Sku("a")
            """;

    /**
     * A line the importer only carries is not the importer's debt.
     *
     * <p>Nothing in {@code example.consumer} can change what {@code Sku} says, so there is nothing
     * for it to answer here.
     */
    @Test
    void aLineAnImportedTypeDrewIsNotTheImportersDebt() {
        Compilation both = compiled(PRODUCER, CONSUMER);

        assertEquals(List.of(), debtsOf(both, "example.consumer"),
                "the consumer carries the type and wrote none of its rules");
        assertEquals(List.of("String.length(value) = 1",
                        "String.length(value) in 1 < String.length(value)"),
                said(both, "example.producer"),
                "and the module that wrote the clause holds what the clause asks for: a row at the"
                        + " line, and a row of an ordinary length above it");
    }

    /**
     * The importer's row does not pay the producer's debt.
     *
     * <p>{@code example.consumer} is the only module writing a row at length 1. Read as evidence
     * here, {@code example.producer} would be settled by a module it does not know about — and
     * taking that module out of the compile would put the debt back with nothing in the producer's
     * source having changed.
     */
    @Test
    void aRowInTheImporterDoesNotDischargeTheProducersDebt() {
        Compilation both = compiled(PRODUCER, CONSUMER);
        assertTrue(Adequacy.boundariesOf(both.db(), "example.consumer").get("tag").stream()
                        .anyMatch(each -> each.owedAt(
                                souther.compiler.partition.PointRole.ON).hasRowWitness()),
                "the consumer's row does stand at the line, so there is something to be read");

        List<Adequacy.Finding> alone = declaredFindings(compiled(PRODUCER), "example.producer");
        List<Adequacy.Finding> withConsumer = declaredFindings(both, "example.producer");

        assertEquals(1, alone.size(),
                () -> "the producer writes no row at the line: " + said(alone));
        assertEquals(said(alone), said(withConsumer),
                "and what it is short of does not move when a module carrying the type is added");
    }

    /**
     * Adding the importer moves nothing about the producer's account, findings and all.
     *
     * <p>Stronger than the sentences being equal: what a verdict counts is the debts, so a
     * denominator that grew by one and a point that changed from asked to met are both this.
     */
    @Test
    void addingAModuleThatCarriesTheTypeChangesNothingTheProducerOwes() {
        assertEquals(account(compiled(PRODUCER), "example.producer"),
                account(compiled(PRODUCER, CONSUMER), "example.producer"),
                "a module's account of its own declarations is its own");
    }

    /** What each of the module's debts asks a row for, as a report writes it. */
    private static List<String> said(Compilation compilation, String module) {
        return debtsOf(compilation, module).stream().map(each -> each.said()).toList();
    }

    /** How many lines the module's debts are points of, which the owners of a line never multiply
     *  and the points of one line always do. */
    private static long linesAmong(List<Adequacy.DeclaredDebt> debts) {
        return debts.stream().map(each -> each.debt().id()).distinct().count();
    }

    /**
     * The module's account of its declarations: which points, owed by whom, and what became of each.
     *
     * <p>Written out rather than compared as the debts themselves, because a debt carries the row a
     * search composed and a row carries a region that compares by identity.
     */
    private static List<String> account(Compilation compilation, String module) {
        return debtsOf(compilation, module).stream()
                .map(each -> each.subject().named() + " owes " + each.debt().id() + " "
                        + each.debt().role() + "="
                        + (each.debt().item().disposition()
                                instanceof ObligationDisposition.Unmet)
                        + "/" + each.debt().demand())
                .toList();
    }

    /** Two records of one module taking one end in to one value together. */
    private static final String TOGETHER = """
            module example.together

            data Cap = Int
                invariant value <= 100

            data Lim = Int
                invariant value <= 50

            data Inner = { a: Cap, b: Lim }
                invariant a.value <= b.value

            data Outer = { inner: Inner, c: Lim }
                invariant inner.a.value <= c.value

            behavior f : (o: Outer) -> Int
            let f (o) = o.c.value

            example f
                | "well inside the range the two of them leave" :
                    (Outer { inner = Inner { a = Cap(10), b = Lim(20) }, c = Lim(30) }) -> 30
            """;

    /**
     * Two declarations holding one end are one debt and not two.
     *
     * <p>Taking either of them away leaves the end at 50, so each is as much the author as the
     * other. That makes them both the subject of the one finding; it does not make the row two rows
     * to write.
     */
    @Test
    void twoDeclarationsHoldingOneEndOweOneRowBetweenThem() {
        Compilation compilation = compiled(TOGETHER);
        List<Adequacy.DeclaredDebt> narrowed = debtsOf(compilation, "example.together").stream()
                .filter(each -> each.owners().size() > 1).toList();

        assertEquals(1, linesAmong(narrowed),
                () -> "the end at 50 is held by both records: "
                        + subjects(compilation, "example.together"));
        assertEquals("Inner or Outer", narrowed.get(0).subject().named(),
                "and a finding about it names both, because the reading does not know which");

        // One of everything per point of the line, and nothing per owner. Counted here because the
        // failure this is about is an owner list being spent as a multiplier.
        assertEquals(1, declaredFindings(compilation, "example.together").stream()
                        .filter(each -> each.subject().equals(narrowed.get(0).subject())).count(),
                "one row to write is one thing to be told about");
        assertEquals(narrowed.size(), generationTargetsFor(compilation, "example.together",
                        narrowed.get(0).debt().id()),
                "and one thing to offer a row for at each point of the line, whichever of the two"
                        + " records is read as having put the end there");
    }

    /** The inner record in one module, the outer in another, each taking the same end in. */
    private static final String INNER = """
            module example.inner exposing ( Cap, Lim, Pair )

            data Cap = Int
                invariant value <= 100

            data Lim = Int
                invariant value <= 50

            data Pair = { a: Cap, b: Lim }
                invariant a.value <= b.value

            behavior g : (p: Pair) -> Int
            let g (p) = p.b.value
            """;

    private static final String AROUND = """
            module example.around exposing ( Box, Lid )

            import example.inner ( Pair )

            data Lid = Int
                invariant value <= 50

            data Box = { pair: Pair, lid: Lid }
                invariant pair.a.value <= lid.value

            behavior f : (b: Box) -> Int
            let f (b) = b.lid.value
            """;

    /**
     * An end two modules took in is two lines, and each module is answered for its own.
     *
     * <p>The bare {@code Pair} a behavior of {@code example.inner} carries is not the {@code Pair}
     * inside a {@code Box}: one has an end {@code Pair} holds and the other an end {@code Pair} and
     * {@code Box} hold together, and a row at one says nothing about the other. So there is no line
     * here that two modules both hold an account of, and neither of them is told about a declaration
     * it did not write.
     */
    @Test
    void anEndTwoModulesTookInIsAnsweredForWhereEachOfThemWroteIt() {
        Compilation compilation = compiled(INNER, AROUND);
        Adequacy.DeclaredDebt inner = narrowedDebtOf(compilation, "example.inner");
        Adequacy.DeclaredDebt around = narrowedDebtOf(compilation, "example.around");

        assertNotEquals(inner.debt().id(), around.debt().id(),
                "an end held by one record and an end held by two are two lines");
        assertEquals("Pair", inner.subject().named());
        assertEquals("Box", around.subject().named(),
                "and each module is answered for the declaration it wrote");

        // What the line says of itself still names both, which is why the row at it is `Box`'s
        // alone: `Pair`'s clause is why there is an end and `Box`'s is why it is here.
        assertEquals(List.of("example.around.Box", "example.inner.Pair"),
                around.debt().id().line().obligationOwners().stream()
                        .map(each -> each.module() + "." + each.name()).toList(),
                "the owners of the line are not one module's");
    }

    /** A comparison written in a body, which draws a line and places no end. */
    private static final String GUARDED = """
            module example.guarded

            data Ok
            data Small

            behavior f : (n: Int) -> Ok | Small
            let f (n) = {
                guard n >= 3 else Small
                Ok
            }
            """;

    /**
     * A rule written in a body is not something a declaration can take in.
     *
     * <p>The state the owners would have to have an answer for, and the reason there is none: a
     * comparison places no end, so nothing is there to be taken in, and a line saying otherwise
     * would owe a declaration a row for a rule that is some behavior's. Refused where a line is
     * made, so no reader downstream has to decide what to do with one.
     */
    @Test
    void aRuleWrittenInABodyCannotBeTakenIn() {
        souther.compiler.partition.AuthoredLine comparison =
                lineOf(compiled(GUARDED), "example.guarded", "f");
        assertEquals(List.of(), comparison.obligationOwners(),
                "the line under test is a body's, so nothing owes it");

        assertThrows(IllegalArgumentException.class,
                () -> new souther.compiler.partition.AuthoredLine(comparison.which(),
                        comparison.facts(),
                        List.of(souther.compiler.types.TypeSymbols.declared(
                                new souther.compiler.types.TypeKey("example.guarded", "Ok")))),
                "and no declaration can be said to have taken its end in");
    }

    /** The first line {@code behavior} reads, whatever drew it. */
    private static souther.compiler.partition.AuthoredLine lineOf(Compilation compilation,
                                                                  String module, String behavior) {
        List<BorderAssessment> lines =
                Adequacy.boundariesOf(compilation.db(), module).get(behavior);
        assertNotNull(lines, "the behavior under test reads a line");
        assertFalse(lines.isEmpty(), "the behavior under test reads a line");
        return lines.get(0).border().origin().authoredLine();
    }

    /** The module's debts, in the order the query answers them. */
    private static List<Adequacy.DeclaredDebt> debtsOf(Compilation compilation, String module) {
        Adequacy.DeclaredBoundaries account =
                compilation.db().ask(new Adequacy.DeclaredBorders(module)).value();
        assertNotNull(account, "the model under test compiles");
        return account.owed();
    }

    /** The one debt of {@code module} whose end something took in. */
    private static Adequacy.DeclaredDebt narrowedDebtOf(Compilation compilation, String module) {
        List<Adequacy.DeclaredDebt> narrowed = debtsOf(compilation, module).stream()
                .filter(each -> !each.debt().id().line().narrowedWithin().isEmpty()).toList();
        assertEquals(1, linesAmong(narrowed),
                () -> module + " reads one taken-in end: " + subjects(compilation, module));
        return narrowed.get(0);
    }

    /** Every finding the module's declarations are short of. */
    private static List<Adequacy.Finding> declaredFindings(Compilation compilation, String module) {
        List<Adequacy.Finding> findings =
                compilation.db().ask(new Adequacy.Findings(module)).value();
        assertNotNull(findings, "the model under test compiles");
        return findings.stream()
                .filter(each -> each.about() instanceof About.APointOfADeclaredBorder)
                .toList();
    }

    /** How many rows the module is offered for one line. */
    private static long generationTargetsFor(Compilation compilation, String module,
                                             BorderObligationId line) {
        return Adequacy.accountFor(compilation.db(), module,
                        new GenerationScope.Module())
                .resolved().keySet().stream()
                .filter(each -> each.line().equals(line)).count();
    }

    /** What each finding asks for, which is what a reader is told. */
    private static List<String> said(List<Adequacy.Finding> findings) {
        return findings.stream()
                .map(each -> {
                    About.APointOfADeclaredBorder about =
                            (About.APointOfADeclaredBorder) each.about();
                    return each.named() + ": " + about.owed().said();
                })
                .toList();
    }

    private static List<String> subjects(Compilation compilation, String module) {
        return debtsOf(compilation, module).stream()
                .map(each -> each.subject().named() + " " + each.axis()).toList();
    }

    private static Compilation compiled(String... sources) {
        Compilation compilation = Compilation.ofSources(List.of(sources), ModulePath.EMPTY);
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        List<String> refused = compilation.db().allReports().stream()
                .filter(each -> each.report().isError())
                .map(each -> each.report().diagnostic().code()).toList();
        assertEquals(List.of(), refused, "the model under test compiles");
        return compilation;
    }
}
