package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredSig;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.inputs.InputDomain;
import souther.compiler.inputs.Refinement;
import souther.compiler.inputs.Requirements;
import souther.compiler.inputs.TermPath;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;
import souther.compiler.types.CaseSelector;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A row for a class under a case is a row at that case.
 *
 * <p>Issue #1012. The eight combinations of a query's optionals are what decide how a {@code WHERE}
 * clause is assembled, and nothing offered a row for any of them: the axes were not there. With the
 * positions read, the row a class under a case is offered has to be that case — which is not
 * something the generator works out for itself. The class states a narrowing by being the class it
 * is, the position states one by being under it, and one merge decides both.
 */
class AClassUnderACaseIsOfferedARowAtThatCaseTest {

    private static final String QUERIES = """
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
            """;

    private record Model(MeasuredInput subject, List<Axis> axes) {}

    private static Model model() {
        Compilation compilation = Compilation.ofSource(QUERIES, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        InputDomain domain = InputDomain.of(sigs.get("read"), rules, ReadAs.THE_COMPILATION_DOES);
        Partitions.Partitioning partitioning =
                Partitions.of("read", domain, rules, ReadAs.THE_COMPILATION_DOES);
        return new Model(
                MeasuredInput.of("read", domain.reading(rules), partitioning),
                partitioning.axes());
    }

    private static TermPath under(String leaf) {
        return TermPath.of("query").refine(toLeaf(leaf));
    }

    /** The narrowing to one leaf, spelled the way the checker's resolution of an arm spells it: a
     *  leaf is a case that covers itself, so selecting it narrows to that one distinction. */
    private static Refinement toLeaf(String leaf) {
        souther.compiler.types.TypeSymbol named =
                TypeSymbols.declared(new TypeKey("example.q", leaf));
        return Refinement.of(souther.compiler.types.ResolvedCase.of(
                CaseSelector.direct(named), java.util.List.of(named)));
    }

    /** Every class of every position, including the ones only one case has. */
    @Test
    void aClassUnderACaseIsOfferedARow() {
        FillResult filled = Generator.fill(model().subject(), List.of(),
                Generator.CandidateCheck.ANY, Budgets.generation());

        assertEquals(List.of(), filled.unresolved(), filled.unresolved().toString());
        assertTrue(filled.rows().stream().map(Generator.GeneratedRow::labels)
                        .anyMatch(each -> each.equals(List.of("query@GlobalQuery.tag=Some"))),
                () -> "no row for the optional under the case: "
                        + filled.rows().stream().map(Generator.GeneratedRow::labels).toList());
    }

    /** And the row written for it is that case, because the class under it says so. */
    @Test
    void theRowWrittenForItIsThatCase() {
        FillResult filled = Generator.fill(model().subject(), List.of(),
                Generator.CandidateCheck.ANY, Budgets.generation());

        for (Generator.GeneratedRow row : filled.rows()) {
            if (row.labels().stream().anyMatch(each -> each.startsWith("query@GlobalQuery"))) {
                assertTrue(row.inputs().get(0).text().startsWith("GlobalQuery"),
                        () -> row.labels() + " is written as " + row.inputs().get(0).text());
            }
            if (row.labels().stream().anyMatch(each -> each.startsWith("query@FeedQuery"))) {
                assertTrue(row.inputs().get(0).text().startsWith("FeedQuery"),
                        () -> row.labels() + " is written as " + row.inputs().get(0).text());
            }
        }
    }

    /**
     * And the two cases' positions make no combinations between them.
     *
     * <p>What a pair space counts and what a row is offered for come off one merge. Counted as the
     * product of the classes, a behavior taking a sum would be measured against combinations no
     * value of it has.
     */
    @Test
    void classesUnderTwoCasesAreNotACombination() {
        Model model = model();
        Axis sum = axisAt(model, TermPath.of("query"));
        Axis tag = axisAt(model, under("GlobalQuery").then("tag"));

        assertTrue(tag.requirements().compatibleWith(
                        sum.requiring(classOf(sum, "GlobalQuery"))),
                "a row that is a GlobalQuery is at the positions the case declares");
        assertFalse(tag.requirements().compatibleWith(sum.requiring(classOf(sum, "FeedQuery"))),
                "and a row that is a FeedQuery is at none of them");
        assertEquals(Requirements.NONE.and(TermPath.of("query"), toLeaf("GlobalQuery")),
                tag.requirements(),
                "which the path says on its own, with nothing kept beside it");
    }

    private static PartitionClass classOf(Axis axis, String id) {
        return axis.classes().stream().filter(each -> each.id().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError("no class " + id + " at " + axis.path()));
    }

    private static Axis axisAt(Model model, TermPath path) {
        return model.axes().stream().filter(each -> each.path().equals(path)).findFirst()
                .orElseThrow(() -> new AssertionError("no axis at " + path + "; there are "
                        + model.axes().stream().map(Axis::path).toList()));
    }
}
