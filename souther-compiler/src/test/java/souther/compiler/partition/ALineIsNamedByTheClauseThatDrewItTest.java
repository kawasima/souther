package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredSig;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.diag.SourceNameResolver;
import souther.compiler.inputs.InputDomain;
import souther.compiler.numeric.Count;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A line is named by the clause that drew it, and not by the end it placed.
 *
 * <p>ADR-0090 says a cut keeps every rule that drew it, and the invariant arm was not keeping them.
 * What named a line was the declaration together with the word {@code min} or {@code max} — which
 * says what the clause did rather than which clause it is — so two clauses of one declaration
 * bounding a position at one value came back as one rule, and a report owed one line for a boundary
 * two rules had drawn. What the author called the clause never reached the report at all.
 */
class ALineIsNamedByTheClauseThatDrewItTest {

    /** Two clauses of one declaration, each placing the same end at the same value. */
    private static final String TWO_CLAUSES_AT_ONE_VALUE = """
            module example.rooms

            data Length = Int
                invariant floorA = value >= 1
                invariant floorB = value >= 1

            behavior price : (length: Length) -> Int
            let price (length) =
                if length.value >= 50 then 1 else 2

            example price
                | "long" : (Length(50)) -> 1
                | "short" : (Length(49)) -> 2
            """;

    /** Two clauses, each placing a different end, named nothing like the end they place. */
    private static final String NAMED_CLAUSES = """
            module example.rooms

            data Length = Int
                invariant floor = value >= 1
                invariant ceiling = value <= 100

            behavior price : (length: Length) -> Int
            let price (length) =
                if length.value >= 50 then 1 else 2

            example price
                | "long" : (Length(50)) -> 1
                | "short" : (Length(49)) -> 2
            """;

    /** One clause placing both ends, and no name on it. */
    private static final String ONE_UNNAMED_CLAUSE = """
            module example.rooms

            data Length = Int
                invariant value >= 1 && value <= 100

            behavior price : (length: Length) -> Int
            let price (length) =
                if length.value >= 50 then 1 else 2

            example price
                | "long" : (Length(50)) -> 1
                | "short" : (Length(49)) -> 2
            """;

    /**
     * Two clauses at one value are two obligations.
     *
     * <p>The cut is one — the classes either side of 1 are the same classes whichever clause is
     * read — and what is owed is one row per rule. Held as the declaration, {@code floorA} and
     * {@code floorB} were one origin, and an author who deleted one of them would have seen no
     * change in what the report asked for.
     */
    @Test
    void twoClausesAtOneValueAreTwoRules() {
        Axis length = axis(TWO_CLAUSES_AT_ONE_VALUE);
        Cut at1 = cutAt(length, 1L);

        assertEquals(List.of("invariant Length (floorA)", "invariant Length (floorB)"),
                at1.origins().stream().map(LineOrigin::saidWithoutAPlace).toList(),
                "one cut, two rules that drew it");

        // Under the declaration that drew them, and named in the terms it wrote: the line is owed
        // once over every behavior carrying the type (issue #1062).
        String human = humanOf(TWO_CLAUSES_AT_ONE_VALUE);
        assertTrue(human.contains("value = 1 (invariant Length (floorA))"), human);
        assertTrue(human.contains("value = 1 (invariant Length (floorB))"), human);
    }

    /**
     * The word beside a line is the one the author wrote.
     *
     * <p>Not what the clause did. {@code floor} and {@code ceiling} were both reported as
     * {@code (min)} and {@code (max)}, which is this compiler describing the clause back to the
     * author in words they did not use — and two clauses doing the same thing had nothing to tell
     * them apart by.
     */
    @Test
    void aLineNamesTheClauseTheAuthorWrote() {
        String human = humanOf(NAMED_CLAUSES);

        assertTrue(human.contains("value = 1 (invariant Length (floor))"), human);
        assertTrue(human.contains("value = 100 (invariant Length (ceiling))"), human);
    }

    /**
     * One clause placing two ends is one rule, and a clause with no name is named by its
     * declaration alone.
     *
     * <p>Both halves matter. A conjunction is one thing the author wrote, so the two lines it draws
     * are owed to the same rule; and where no name was written there is nothing to put in the
     * brackets, which is what an {@code ensures} clause with no name already does.
     */
    @Test
    void oneClauseIsOneRuleAndAnUnnamedOneIsNamedByItsDeclaration() {
        String human = humanOf(ONE_UNNAMED_CLAUSE);

        assertTrue(human.contains("value = 1 (invariant Length #1)"), human);
        assertTrue(human.contains("value = 100 (invariant Length #1)"), human);

        Axis length = axis(ONE_UNNAMED_CLAUSE);
        assertEquals(1, cutAt(length, 1L).origins().size(),
                "one clause is one rule however many ends it places");
    }

    private static Cut cutAt(Axis axis, long value) {
        return axis.cuts().stream()
                .filter(c -> c.at().sameAs(Count.of(value)))
                .findFirst().orElseThrow(() -> new AssertionError("no cut at " + value + "; had "
                        + axis.cuts().stream().map(Cut::at).toList()));
    }

    private static Axis axis(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        assertNotNull(sigs);
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        return Partitions.of("price", InputDomain.of(sigs.get("price"), rules, souther.compiler.query.ReadAs.THE_COMPILATION_DOES), rules, souther.compiler.query.ReadAs.THE_COMPILATION_DOES)
                .axes().stream().filter(a -> a.path().toString().equals("length"))
                .findFirst().orElseThrow();
    }

    private static String humanOf(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).human(SourceNameResolver.identity());
    }
}
