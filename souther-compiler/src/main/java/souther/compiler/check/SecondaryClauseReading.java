package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.diag.CompileException;
import souther.compiler.types.Type;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The one place a clause is typed below the check that answers for the program, and the one place
 * that says what such a reading may stop on.
 *
 * <p>Two readings type a clause here — a declaration's invariant over its fields, and a behavior's
 * rule over its signature — and both used to catch whatever came out of the elaborator and read it
 * as this analysis meeting its limit. What came out included a representation refusing to be built,
 * so a node saying it had come apart and a shape this reading has no rule for arrived alike, and the
 * compile went on with the invariant quietly undischarged.
 *
 * <p>So the limits are named here and everything else goes past. What this reading may stop on is a
 * call its expansion left standing that it has no signature for, and a clause the elaborator refused
 * — and refusing a clause is what the authoritative check does with a program, not something this
 * reading found out. Nothing else is a limit: an {@code IllegalStateException} from a walk, a node's
 * own refusal, a name read through nothing are this compiler disagreeing with itself, and there is no
 * reading of the program under which they are acceptable.
 */
final class SecondaryClauseReading {

    private SecondaryClauseReading() {}

    /**
     * {@code read} as this reading types it, or the limit it stopped on.
     *
     * <p>What {@code clause} carries beside its tree is the expansion's own answer about it. Asked
     * before the
     * elaborator, because a call left standing that this reading cannot name is a limit and the
     * elaborator has no way to say so: reaching it with one is this compiler having failed to expand
     * what it says it expands, which is what it refuses.
     */
    static TypedClause of(ClauseAsExpanded clause, Supplier<Over> over, String describing) {
        try {
            Over read = over.get();
            WhatTheCheckCannotRead unreadable = standingCallNothingHereNames(clause, read.scope());
            if (unreadable != null) {
                return stoppedOn(describing, unreadable);
            }
            return new TypedClause.Typed(
                    Elaborator.elaborate(clause.read(), read.scope(), read.ctx(), Type.BOOL));
        } catch (CompileException why) {
            return stoppedOn(describing, WhatTheCheckCannotRead.secondaryTypingDidNotFinish(why));
        } catch (Unanswerable why) {
            return stoppedOn(describing, WhatTheCheckCannotRead.secondaryTypingDidNotFinish(why));
        }
    }


    /** Records the limit and answers with the stop it authorizes, which is one act and is written
     *  as one. */
    private static TypedClause stoppedOn(String describing, WhatTheCheckCannotRead met) {
        InvariantChecker.gaveUp(describing, met);
        return new TypedClause.Stopped(met);
    }

    /**
     * What a clause is read over: the names it may use and the context it is typed in.
     *
     * <p>Asked for rather than handed in, so that working it out happens inside the reading. What a
     * declaration's clause is read over is its fields, and reading those off a declaration is itself
     * a thing this compiler can refuse — a spread of something that is not a product, two spreads
     * bringing one field in. Worked out before the reading begins, such a refusal would leave the
     * analysis by a door the policy has no name for, though it is the same secondary reading
     * failing.
     */
    record Over(Scope scope, CheckContext ctx) {}

    /**
     * The limit this reading stops on, or null where it has none to stop on.
     *
     * <p><b>Asked of every call and not of one.</b> What makes a stop right here is that
     * <em>everything</em> this reading cannot name was left standing on purpose. Answered with the
     * first such call instead, a clause holding one call the expansion meant to leave and one it
     * failed to remove stops on the first and never reaches the elaborator — so this compiler's own
     * failure comes out as an ordinary limit, which is the whole of what this file exists to
     * prevent, met one clause further in.
     *
     * <p><b>And the elaborator is the authority on the other side.</b> Where a call is unreadable
     * here and no expansion named it, nothing is decided here: the clause goes on to be typed, and
     * what that call is — a tree this compiler failed to expand, or one the typing can read by a
     * route this does not model ({@link Elaborator}'s own reading of what a scope reaches) — is
     * answered by the typing rather than guessed at here.
     */
    private static WhatTheCheckCannotRead standingCallNothingHereNames(ClauseAsExpanded clause,
                                                                       Scope scope) {
        // Nothing was left standing, so no unreadable call in this tree is one of these and the
        // typing answers for all of them. The ordinary case, and asked before the walk.
        if (clause.standing().leftNothing()) {
            return null;
        }
        List<Hir.Apply> unreadable = new ArrayList<>();
        callsNothingHereNames(clause.read(), scope, unreadable);
        if (unreadable.isEmpty()) {
            return null;
        }
        for (Hir.Apply call : unreadable) {
            if (!clause.standing().names(call.answered().reachesADeclaration())) {
                return null;
            }
        }
        Hir.Apply first = unreadable.get(0);
        return WhatTheCheckCannotRead.standingCallHasNoSignatureHere(first.written(), first.pos());
    }

    /** Every helper applied in {@code read} that {@code scope} has no signature for, in the order
     *  they are written. */
    private static void callsNothingHereNames(Hir.Expr read, Scope scope, List<Hir.Apply> out) {
        if (read instanceof Hir.Apply call
                && call.answered() instanceof Hir.Var.Denoting callee
                && callee.denotes() instanceof ValueName.Helper
                && scope.of(callee.denotes(), callee.reaches()) == null) {
            out.add(call);
        }
        Hir.forEachChild(read, child -> callsNothingHereNames(child, scope, out));
    }
}
