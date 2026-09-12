package souther.compiler.report;

import souther.compiler.check.PartId;
import souther.compiler.check.RuleRef;
import souther.compiler.diag.Citation;
import souther.compiler.types.SourceConstructOrigin;

/**
 * Where this report shows each part of a rule it sends a reader inside.
 *
 * <p>A capability and not a table, for the reason the rules' one is: a report narrowed to one
 * behavior shows the parts that behavior's findings name and knows about no others, and a table
 * kept beside it would go on holding places for pages it no longer has.
 *
 * <p>It refuses rather than answering where a part was not assembled with the report. A finding is
 * about a part some reading of this compilation reached, so one this report cannot place is two of
 * this compiler's answers disagreeing — answered with a place that points nowhere, the document
 * would send a reader inside a rule at a caret nobody wrote.
 */
public interface WhereAPartIs {

    /** Where {@code part} is written. */
    Citation of(PartId<RuleRef.Invariant> part);

    /**
     * Where {@code origin} is written.
     *
     * <p>Beside the parts and answered from the same table, because they are two grains of one
     * question: a reader is sent inside a rule, and what they are sent to is the finest thing its
     * author wrote there. Two capabilities would be two answers to where a reader goes, free to
     * disagree the day one of them was assembled and the other was not.
     */
    Citation of(SourceConstructOrigin origin);
}
