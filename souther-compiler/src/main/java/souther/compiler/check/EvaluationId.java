package souther.compiler.check;

import souther.compiler.diag.SourcePos;
import souther.compiler.hash.SaysWhatStandsForIt;

/**
 * One evaluation the check reads, told apart from every other.
 *
 * <p>Equality is identity, and that is the whole of it. Two of these are the same evaluation when
 * they are the same object, so there is no way to spell one that equals another — which is what a
 * subject for a value nothing may share has to be. A record over the position would not do it: a
 * position says where something is written, and the same line evaluated in two places is two values
 * ({@link Location} states the same rule for bindings, and {@code an offset is not an identity} is
 * how it was learned).
 *
 * <p>Made once per occurrence and handed back, by {@link Terms#evaluationIdOf}. Constructed rather
 * than worked out again at each ask: a subject recomputed from what a walk happens to hold is a
 * subject that moves when the walk does, and a fact filed under the old one is then about nothing.
 *
 * <p>The position it carries is for a reader and takes no part in telling two apart.
 *
 * <p>What a term carrying one of these is hashed from is not which object it is. {@code Object}'s
 * hash is drawn afresh each run, and a term filed under one would be filed somewhere else the next
 * time — so what stands for one here is what was written and which occurrence of the reading it is,
 * which agrees with equality the one way a hash has to: two that are one hash alike.
 *
 * <p>Which is less than what tells two of these apart, and that is allowed of what stands for a
 * value: two of them are one only when they are the same object, so any two that are equal name the
 * same thing whatever else is true. The position is left out for a reason of its own. It stands on
 * a {@link souther.compiler.diag.Placement}, and what that reads is a tree rather than a value this
 * can answer for.
 */
final class EvaluationId implements SaysWhatStandsForIt {

    /**
     * What stands for an evaluation where a number is wanted of it.
     *
     * @param what       what was written where it stands
     * @param occurrence which one it is of the evaluations its reading has named, in the order it
     *                   named them
     */
    record Named(String what, int occurrence) {
    }

    private final SourcePos where;

    private final Named named;

    EvaluationId(String what, SourcePos where, int occurrence) {
        this.named = new Named(what, occurrence);
        this.where = where;
    }

    /** What was written where this stands. */
    String what() {
        return named.what();
    }

    /** Which one this is of the evaluations its reading has named. */
    int occurrence() {
        return named.occurrence();
    }

    SourcePos where() {
        return where;
    }

    @Override
    public Named standsFor() {
        return named;
    }

    String rendered() {
        return "<" + named.what() + " at " + (where == null ? "?" : where) + ">";
    }

    @Override
    public String toString() {
        return rendered();
    }
}
