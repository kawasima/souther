package souther.compiler.diag.msg;

import souther.compiler.diag.DiagnosticCode;

/**
 * What an author is told about a branch the model's own rules make dead.
 *
 * <p>The converse of {@link UnreachableMessage}, which is about an {@code unreachable} the rules
 * leave standing. Here the author wrote an ordinary branch and the rules rule it out.
 *
 * <p><b>The supporting arms are the proofs, one each.</b> This is the one place a proof is taken
 * apart: everything that decides anything reads the three answers and treats the proof as payload,
 * so a proof added later is a compile error here and a change to no policy.
 */
public sealed interface DeadBranchMessage extends Message {

    /** Nothing the model admits arrives at this branch, so no row can be written through it. */
    @Code(DiagnosticCode.E1327)
    record NothingReachesThisBranch() implements DeadBranchMessage, Reported {}

    /**
     * The conditions on the way here cannot all hold.
     *
     * <p>Which ones, and which way each of them goes, are marked where they are written rather than
     * listed here as line numbers. A condition is somewhere in the body, and where that is is what
     * the file is laid out as at the moment — so it is a caret for a renderer to place and not a
     * number for a report to carry.
     */
    record TheConditionsOnTheWayHereCannotAllHold() implements DeadBranchMessage, Supporting {}

    /** One of those conditions, on a way here that needs it to hold. */
    record ThisOneHoldsOnTheWayHere() implements DeadBranchMessage, Supporting {}

    /** One of those conditions, on a way here that needs it to fail. */
    record ThisOneFailsOnTheWayHere() implements DeadBranchMessage, Supporting {}

    /** The values it is written for are not values the position can hold. */
    record ThePositionStopsShortOfIt(String position, String admits) implements DeadBranchMessage,
            Supporting {}

    /** Every case the arm names is one the rules refuse where it is matched on. */
    record EveryCaseItIsWrittenForIsRefused(String position, String cases)
            implements DeadBranchMessage, Supporting {}

    /** What to do about it. */
    record TakeItOutOrLetSomethingReachIt() implements DeadBranchMessage, Supporting {}
}
