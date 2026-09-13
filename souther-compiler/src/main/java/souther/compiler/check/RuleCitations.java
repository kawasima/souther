package souther.compiler.check;

import java.util.Set;

/**
 * Every handle a value holds for a rule of the model it read.
 *
 * <p>What a document has to answer before it can send a reader anywhere: a page that names a rule
 * needs somewhere to point, and where that is, is looked up once per handle when the report is
 * assembled. Which handles there are is the question this asks, and it is asked of the value that
 * did the reading rather than of the page.
 *
 * <p><b>Asked of the producer, because the consumer cannot be told.</b> A report gathering these by
 * listing the places a rule turns up in is a list somebody has to keep in step: a reading filed
 * somewhere new is a reading nothing points at, nothing fails, and the page names a class with no
 * handle for the rule that made it. Which is what happened to the readings that compose a
 * position's classes. So the value that holds the reading answers for it, and a whole answers by
 * asking its parts.
 *
 * <p><b>No default answer.</b> A value that reads no rule says so by writing the empty answer out,
 * and one that reads rules cannot compile without saying which — an inherited empty would put the
 * silence back where this exists to remove it.
 *
 * <p>Handles and not identities. Two readings of one rule offer one handle, and a value holding
 * both offers it once: what is asked about here is where a reader is sent, and which reading sent
 * them is no part of that. Unordered for the same reason — a document choosing between two handles
 * chooses by the canonical order it publishes under, and an order promised here would be a second
 * answer to that comparison.
 */
public interface RuleCitations {

    /** Every handle for a rule this value read, which is none where it read none. */
    Set<RuleCitation> ruleCitations();
}
