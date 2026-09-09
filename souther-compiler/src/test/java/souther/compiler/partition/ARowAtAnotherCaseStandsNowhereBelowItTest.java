package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.DeclaredSig;
import souther.compiler.inputs.Refinement;
import souther.compiler.inputs.TermPath;
import souther.compiler.observe.Classification;
import souther.compiler.observe.ObservedValue;
import souther.compiler.observe.RowOutcome;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.Output;
import souther.compiler.query.ReadAs;
import souther.compiler.types.CaseSelector;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A row whose value is another case stands nowhere below the case it is not.
 *
 * <p>Three answers and not two. A walk that could not be taken is the path and the declaration
 * disagreeing and says nothing about the row; a walk that arrived and found no value there is a row
 * that was read and does not reach the position. A refinement gives the second its ordinary case:
 * the row wrote a {@code FeedQuery}, so {@code query@GlobalQuery.tag} is a position it puts nothing
 * at — the same answer a row writing the empty list gives at an element, and not the answer a row
 * nothing could read gives.
 */
class ARowAtAnotherCaseStandsNowhereBelowItTest {

    private static final String MODEL = """
            module example.q

            data Tag = String
            data Limit = Int
                invariant value >= 1

            data GlobalQuery = { limit: Limit, tag: Tag? }
            data FeedQuery = { limit: Limit }
            data ArticleQuery = GlobalQuery | FeedQuery
            data Page = { n: Int }

            behavior read : (query: ArticleQuery) -> Page
                constructs Page

            let read (query) = Page { n = 1 }

            example read
                | (GlobalQuery { limit = Limit(1), tag = Tag("x") }) -> Page
                | (FeedQuery { limit = Limit(2) }) -> Page
            """;

    private record Read(MeasuredInput subject, List<RowOutcome> rows) {

        BehaviorInputs inputs() {
            return subject.inputs();
        }
    }

    private static Read read() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        assertNotNull(compilation.db().ask(new Bodies.Checked(module)).value(),
                "the model under test compiles");
        Output.Examples.Of observed = compilation.db()
                .ask(Output.Examples.asked(compilation.db(), module,
                        compilation.sourceIds().get(0))).value();
        assertNotNull(observed);
        souther.compiler.inputs.InputDomain domain = souther.compiler.inputs.InputDomain.of(
                sigs.get("read"), rules, ReadAs.THE_COMPILATION_DOES);
        Partitions.Partitioning partitioning = Partitions.of("read", domain,
                rules, ReadAs.THE_COMPILATION_DOES);
        return new Read(MeasuredInput.of("read", domain.reading(rules), partitioning),
                observed.rows());
    }

    /** The measurement narrowed to {@code axis}, which is one of its own. */
    private static MeasuredInput.MeasuredAxes only(Read read, Axis axis) {
        return read.subject().axes().where(each -> each.id().equals(axis.id()));
    }

    /** The narrowing to one leaf, spelled the way the checker's resolution of an arm spells it: a
     *  leaf is a case that covers itself, so selecting it narrows to that one distinction. */
    private static Refinement toLeaf(souther.compiler.types.TypeSymbol leaf) {
        return Refinement.of(souther.compiler.types.ResolvedCase.of(
                CaseSelector.direct(leaf), java.util.List.of(leaf)));
    }

    private static TermPath under(String module, String leaf, String field) {
        return TermPath.of("query")
                .refine(toLeaf(TypeSymbols.declared(new TypeKey(module, leaf))))
                .then(field);
    }

    /** What the walk came to, held to its having been taken first. */
    private static List<ObservedValue> stood(WalkResult<List<ObservedValue>> walked) {
        if (walked instanceof WalkResult.Reached(List<ObservedValue> values)) {
            return values;
        }
        throw new AssertionError("the walk was taken: nothing went wrong with the row");
    }

    /** The row that wrote the case stands at the positions under it. */
    @Test
    void aRowAtTheCaseStandsAtItsFields() {
        Read read = read();
        List<ObservedValue> values = stood(read.inputs().valuesAt(read.rows().get(0).inputs(),
                under("example.q", "GlobalQuery", "tag")));
        assertEquals(1, values.size(), values.toString());
    }

    /**
     * And the row that wrote the other case stands at none of them.
     *
     * <p>Read, and in no class there. Answered as a value nothing could read, every row of every
     * behavior taking a sum would report the measurement as short of what it plainly settles.
     */
    @Test
    void aRowAtAnotherCaseIsReadAndStandsNowhere() {
        Read read = read();
        List<ObservedValue> values = stood(read.inputs().valuesAt(read.rows().get(1).inputs(),
                under("example.q", "GlobalQuery", "tag")));
        assertEquals(List.of(), values, "and the row put nothing at a position it is not at");
    }

    /** Which is a step taken and not a step refused: the path and the declaration agree. */
    @Test
    void thePathAndTheDeclarationAgreeAtBothRows() {
        Read read = read();
        for (RowOutcome row : read.rows()) {
            assertInstanceOf(WalkResult.Reached.class,
                    read.inputs().occurrencesAt(row.inputs(),
                            under("example.q", "FeedQuery", "limit")),
                    () -> "the walk was taken for " + row.inputs());
        }
    }

    /**
     * A class reads the row that is there and says nothing of the row that is not.
     *
     * <p>The whole of what the two answers are for. A position under a case is measured by the rows
     * at that case, and a row at another case is neither evidence for it nor a hole in the
     * measurement.
     */
    @Test
    void theClassesSeeOnlyTheRowsAtTheCase() {
        Read read = read();
        Axis tag = axisAt(read, under("example.q", "GlobalQuery", "tag"));

        Classification atTheCase = InputClassifications
                .of(read.rows().get(0).inputs(), only(read, tag)).get(tag.id());
        assertEquals(List.of("Some"),
                assertInstanceOf(Classification.Classified.class, atTheCase).classIds());

        Classification elsewhere = InputClassifications
                .of(read.rows().get(1).inputs(), only(read, tag)).get(tag.id());
        assertTrue(elsewhere instanceof Classification.Classified in && in.classIds().isEmpty(),
                () -> "a row at another case is in no class there: " + elsewhere);
        assertNull(elsewhere.stopped(),
                "and stopped nothing, so the measurement is not short of it");
    }

    private static Axis axisAt(Read read, TermPath path) {
        return read.subject().axes().axes().stream().filter(each -> each.path().equals(path)).findFirst()
                .orElseThrow(() -> new AssertionError("no axis at " + path
                        + "; the reading has " + read.subject().axes().axes().stream().map(Axis::path).toList()));
    }
}
