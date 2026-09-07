package souther.compiler.partition;

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
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Which of a rule's lines a line is, said by what counts them, and never by both.
 *
 * <p>A declaration's clause is written in the parts its author wrote, and a line it draws is one of
 * those parts'. A rule written in a body is not written in parts: what it draws is counted over the
 * comparisons it states. Two countings, and a line answers by which one it is.
 *
 * <p>What is held here is that the two do not overlap. A line naming a part is a declaration's by
 * the type it carries; a line counting comparisons is refused a declaration's clause, so no value
 * can be built that a reader would be told belongs to a declaration and that names no part of one.
 */
class ALineSaysWhatCountsItAndCannotSayBothTest {

    private static RuleRef.Invariant aClause() {
        return new RuleRef.Invariant(new Clause.Ref(
                new Clause.Id(TypeSymbols.declared(new TypeKey("example.line", "N")), 0),
                Optional.of(new ClauseName("within"))));
    }

    private static RuleRef.Comparison aComparison() {
        return new RuleRef.Comparison("weigh", new SourceConstructOrigin(
                new WrittenOwner.Body("example.line", "weigh"), 2, 0, SourceConstruct.BINARY));
    }

    /** A rule written in a body counts its lines over the comparisons it states. */
    @Test
    void aRuleWrittenInABodyCountsTheComparisonsItStates() {
        assertEquals(aComparison(), new WhichLine.OfAComparison(aComparison(), 1).rule(),
                "which rule drew it, whichever of its comparisons this line is");
    }

    /**
     * And a declaration's clause is refused that counting.
     *
     * <p>Its lines are counted by the parts its author wrote, and a part is what names them. Built
     * this way, one value would answer that a declaration owes the line and that no part of one
     * drew it, and a reader has no way to tell which of the two to believe.
     */
    @Test
    void aDeclarationsClauseIsNotCountedThatWay() {
        assertThrows(IllegalArgumentException.class,
                () -> new WhichLine.OfAComparison(aClause(), 0),
                "a clause of a declaration counts its lines by its parts");
    }

    /** And what the part-counted arm carries is a part, which is a declaration's clause by type. */
    @Test
    void aLineCountedByPartsIsADeclarationsOwn() {
        assertEquals(aClause(), new WhichLine.OfAPart(new PartId<>(aClause(), 0)).rule(),
                "the clause the part is a part of, which is what such a line is of");
    }
}
