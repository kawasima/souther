package souther.compiler.sites;

import souther.compiler.diag.SourcePos;

import java.util.Map;

/**
 * Where each condition a module wrote stands in its own source.
 *
 * <p>Beside {@link WrittenForks} and read off the same walk, and not folded into it. A fork is what
 * a body takes an arm of and a condition is what a row had to satisfy to get somewhere; a reader
 * holds one question or the other, and one table answering both would be a place filed under an
 * identity a caller has no way to have.
 *
 * <p>Filed under {@link WrittenCondition}, which a copy cannot change, so a reader that met a
 * condition inside a helper spliced into its own body asks the module that wrote the helper. What
 * it gets back moves when that module's text moves and at no other time.
 *
 * <p><b>What a condition can be written as, and not what a reading made of it.</b> A condition that
 * is a construct is a binary and an arm of a fork is an arm, so those are what is filed — every one
 * of them, the operator unread. Which of them a reading calls a condition is that reading's answer,
 * and a walk that decided it here would be that recognition made a second time with none of what
 * the reading knows. The surplus is places nobody asks for.
 *
 * <p>A condition of a shape the reading has no words for is any expression at all, and nothing here
 * files one; where such a one is reported is asked of the reading that met it. So a lookup that
 * comes back with nothing is a question asked of the wrong table rather than a module that mislaid
 * something.
 */
public final class WrittenConditions {

    private final Map<WrittenCondition, SourcePos> byCondition;

    WrittenConditions(Map<WrittenCondition, SourcePos> byCondition) {
        this.byCondition = Map.copyOf(byCondition);
    }

    /** Where the condition {@code which} names is written, or null where this module wrote no such
     *  condition. */
    public SourcePos at(WrittenCondition which) {
        return which == null ? null : byCondition.get(which);
    }

    /** How many were found, which is what says a walk reached a module at all. */
    public int count() {
        return byCondition.size();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof WrittenConditions written
                && byCondition.equals(written.byCondition);
    }

    @Override
    public int hashCode() {
        return byCondition.hashCode();
    }

    @Override
    public String toString() {
        return byCondition.size() + " written conditions";
    }
}
