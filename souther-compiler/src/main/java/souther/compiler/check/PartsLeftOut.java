package souther.compiler.check;

import souther.compiler.core.Core;

import java.util.List;
import java.util.Set;

/**
 * Which authored part of which rule a reading was asked to leave out.
 *
 * <p>Beside {@link RulesLeftOut} and not a finer setting of it. That one leaves out every rule some
 * declaration wrote, and what it is asked with is a declaration; this leaves out one part of one
 * rule, and what it is asked with is the name that part was issued under. The two are different
 * questions about different things, and a reading told to leave out "a part" with no rule beside it
 * would leave out whatever else happened to be written the same way.
 *
 * <p><b>The name and not the tree.</b> A part is named where a clause is split ({@link PartId}), so
 * a reading told to leave one out is told which one and has nothing to recognise. Named by its tree
 * instead, every reading that had to skip it matched a node — and a reading asked without a part
 * types the clause again, so what it walks is a node equal to the one named rather than the same
 * one, which is a match that holds only while two identical parts of one clause never both come up.
 *
 * <p>What such a reading is for is not here. A reader comparing what a value's rules leave with and
 * without one part is asking what that part was holding; nothing about the comparison belongs to
 * the reading that answers it.
 */
sealed interface PartsLeftOut permits PartsLeftOut.Nothing, PartsLeftOut.Some {

    /** Every part of every rule is read. */
    PartsLeftOut NONE = new Nothing();

    /** Whether anything at all is left out, which is what a reading standing in for a
     *  counterfactual is. Asked of the arms rather than of one of them, so that an arm added is a
     *  case here and not a reading that quietly counts as the whole one. */
    boolean leavesAnythingOut();

    /**
     * The clause written in {@code parts}, as this world holds it.
     *
     * <p><b>Handed out and never asked about.</b> Two walks over one clause have to leave the same
     * parts out — a part one reached that the other never read is a value whose rules were not
     * gathered — and a rule saying which one to leave out is a rule a walk can be written without
     * consulting. So what a reading is given is the clause this world has ({@link ClauseView}), and
     * there is nothing here to ask about a part on its own.
     *
     * <p>What such a reading is for is not here. A reader comparing what a value's rules leave with
     * and without one part is asking what that part was holding; nothing about the comparison
     * belongs to the world that answers it.
     */
    ClauseView viewOf(List<Clauses.StatedPart> parts);

    /** Nothing is left out. */
    record Nothing() implements PartsLeftOut {

        @Override
        public boolean leavesAnythingOut() {
            return false;
        }

        @Override
        public ClauseView viewOf(List<Clauses.StatedPart> parts) {
            return ClauseView.whole(parts);
        }
    }

    /**
     * Some parts of some rules are left out.
     *
     * <p>A set, because that is what a counterfactual reading is asked. Which of the candidates
     * account for an end is three questions and two of them take more than one away at a time — one
     * candidate is missed on its own, and one holds the end with every other candidate gone — so a
     * scope that could only name one would answer the first and stop.
     */
    record Some(Set<PartId<RuleRef.Invariant>> parts) implements PartsLeftOut {

        public Some {
            parts = Set.copyOf(parts);
            if (parts.isEmpty()) {
                throw new IllegalArgumentException("leaving nothing out is `NONE`");
            }
        }

        @Override
        public boolean leavesAnythingOut() {
            return true;
        }

        @Override
        public ClauseView viewOf(List<Clauses.StatedPart> written) {
            Set<Core> out = ClauseView.roots();
            for (Clauses.StatedPart each : written) {
                if (parts.contains(each.id())) {
                    out.add(each.expr());
                }
            }
            return out.isEmpty() ? ClauseView.whole(written) : ClauseView.without(written, out);
        }
    }

    /** Every part but these. */
    static PartsLeftOut without(Set<PartId<RuleRef.Invariant>> parts) {
        return parts.isEmpty() ? NONE : new Some(parts);
    }
}
