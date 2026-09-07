package souther.compiler.partition;

import souther.compiler.check.BehaviorContract;
import souther.compiler.check.Clause;
import souther.compiler.check.ClauseName;
import souther.compiler.check.PartId;
import souther.compiler.check.RuleRef;
import souther.compiler.types.SourceConstruct;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;
import souther.compiler.types.WrittenOwner;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Which of a rule's lines a line is, said by what named it, and never by both.
 *
 * <p>A declaration's clause is written in the parts its author wrote, and a line it draws is one of
 * those parts'. A part of a behavior's clause states as many things as a reading of it finds, and a
 * line it draws is one of those statements'. A comparison written in a body is a rule apiece and is
 * decomposed by nothing, so a line of it has no number under the rule at all. Three answers, and a
 * line says which of them it is.
 *
 * <p>What is held here is that no line can be built that answers two of them. Which arm a line is
 * decides what it carries, and each arm carries the identity that was issued with the line — so
 * there is no value a reader would be told belongs to a declaration and that names no part of one,
 * and none carrying a number nothing counted.
 *
 * <p>The other half of that is javac's and is not asserted here: the arm that names a part takes a
 * part of a {@code data}'s clause, the arm that names a statement takes one of a behavior's, and the
 * arm for a body takes a comparison. A test that built the wrong one to watch it be refused would be
 * a test that did not compile.
 */
class ALineSaysWhatCountsItAndCannotSayBothTest {

    private static RuleRef.Invariant aClause() {
        return new RuleRef.Invariant(new Clause.Ref(
                new Clause.Id(TypeSymbols.declared(new TypeKey("example.line", "N")), 0),
                Optional.of(new ClauseName("within"))));
    }

    private static RuleRef.Ensures anEnsures() {
        return new RuleRef.Ensures(
                new BehaviorContract.RuleId(null, 0, 0, null), "cap");
    }

    private static RuleRef.Comparison aComparison() {
        return new RuleRef.Comparison("weigh", new SourceConstructOrigin(
                new WrittenOwner.Body("example.line", "weigh"), 2, 0, SourceConstruct.BINARY));
    }

    /** A line of a declaration's clause is named by the part that drew it. */
    @Test
    void aLineOfADeclarationIsNamedByItsPart() {
        assertEquals(aClause(), new WhichLine.OfAPart(new PartId<>(aClause(), 0)).rule(),
                "the clause the part is a part of, which is what such a line is of");
    }

    /**
     * A line of a behavior's clause is named by a part and by which of that part's statements it is.
     *
     * <p>Both, because the two are counted in different things: the parts are counted over the
     * clause and the statements over one part, so the second statement of the second part is not
     * the fourth of anything.
     */
    @Test
    void aLineOfABehaviorsClauseIsNamedByAPartAndAStatementOfIt() {
        ClauseStatementId said = new ClauseStatementId(new PartId<>(anEnsures(), 1), 0);
        WhichLine.OfAComparisonOfAPart line = new WhichLine.OfAComparisonOfAPart(said);

        assertEquals(anEnsures(), line.rule(), "the clause the part is a part of");
        assertEquals(1, line.statement().part().ordinal(), "which part its author wrote");
        assertEquals(0, line.statement().ordinal(), "which of that part's statements");
    }

    /**
     * And a line of a body's comparison is named by the rule alone.
     *
     * <p>A comparison is a rule apiece — a condition holding three of them is three rules — so
     * there is nothing under the rule for a line of it to be one of. What told two lines of it at
     * one value apart was never the number, which was nought at every one of them.
     */
    @Test
    void aLineOfABodyIsNamedByTheComparisonAlone() {
        assertEquals(aComparison(), new WhichLine.OfAComparison(aComparison()).rule(),
                "which rule drew it, and there is no second line of it to be told from");
    }
}
