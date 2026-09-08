package souther.compiler.partition;

import souther.compiler.inputs.Position;
import souther.compiler.inputs.StandingQuestion;
import souther.compiler.inputs.StructuralInspection;
import souther.compiler.inputs.TermPath;
import souther.compiler.types.Type;

import java.util.List;

/**
 * One position of a behavior's input, and what this phase is left answering for at it.
 *
 * <p>Held once for the position, with the measures made of it under it
 * ({@link PositionMeasurements}), because a position is measured at as many numbers as the rules
 * name of it and none of what is here is one of those numbers. Where the walk stopped, what the
 * reading of the declarations left standing, and what the position is if nothing answers are facts
 * about the location — true of it once, whether it is measured at its own content, at how long it
 * is, at both, or at nothing.
 *
 * <p><b>Kept on the position and not on a measure of it.</b> Whether a position is still waiting on
 * anything is a question about the location ({@link PendingPosition}), and a measure that could
 * answer it lets any reader ask through whichever number it happens to hold. Which one that is
 * decides nothing, so a reader that has to pick has been handed the wrong thing.
 *
 * <p>A measure holds none of this. What a measure needs of the location is what stands there, and
 * it has that; a copy of the rest per measure would be a fact with as many representations as the
 * position has numbers, and any of them may be rebuilt without one of its parts — which is what
 * {@link ReadingResidue} guards one field at a time.
 *
 * @param pending  where nothing has answered for this position yet, what the structural reading
 *                 found — and so what the position is left with if nothing else answers. Null where
 *                 the reading of the declarations already answered, which needs no fallback
 * @param standing the questions the rules of this position raise that nothing answered, empty
 *                 where every one of them was answered. The accounting's answer and not a reading's:
 *                 a rule is answered by whichever reading took it in, so a reading that was short
 *                 of a rule another one read leaves nothing standing here
 */
public record PositionAccount(String behavior, TermPath path, Type type, ReadingResidue residue,
                              souther.compiler.values.ValueSet admits,
                              StructuralInspection.Continuation pending,
                              List<StandingQuestion> standing,
                              List<souther.compiler.check.RuleRef> endsLeftOpenByAChoice) {

    public PositionAccount {
        if (residue == null) {
            throw new IllegalArgumentException(
                    "a position with no account of what its reading came to");
        }
        standing = List.copyOf(standing);
        endsLeftOpenByAChoice = List.copyOf(endsLeftOpenByAChoice);
    }

    /**
     * Where this position is, which is what tells it from the others of one behavior's input.
     *
     * <p>The path and nothing else, as the reading of the input names it. What tells it from a
     * position of another behavior is {@link #behavior}, which travels beside this wherever an
     * account of one behavior is put together with another's — every such account says whose it is
     * ({@code Weakening.ProofContradicted}), and a fact that did not would be one fact where two
     * behaviors happen to be shaped alike.
     */
    public souther.compiler.inputs.PositionId id() {
        return new souther.compiler.inputs.PositionId(path);
    }

    /**
     * Why the reading did not get to the rules of this position, or null where it did.
     *
     * <p>Two ways of not getting there and one answer, because neither of them names a rule. The
     * walk could not go into what the position holds, or it entered and lost a clause of its own —
     * and what is written under either is whatever it is. Which of the two it was is this
     * compiler's route to the hole rather than anything about the model, so a reader is told the
     * hole.
     *
     * <p>Asked here, where both facts are, and read by everything that has to know. A verdict about
     * the position and a finding written at it are the same question asked twice, and answered
     * apart they answer differently the day one of them learns about an arm.
     *
     * <p>Over what the walk found, with no {@code default}, because that is what makes this the one
     * place: a continuation added later says here whether the rules were got to, or does not
     * compile. Asked with an {@code instanceof} and left to a caller to switch on the rest, the
     * question would be answered in as many places as there are callers.
     */
    public souther.compiler.inputs.BlockReason.AboutThePosition notReachedInto() {
        return switch (pending) {
            case StructuralInspection.Continuation.Blocked blocked -> blocked.why();
            // The walk went in, so what is left is whether this reading lost a clause of its own.
            // A handing over nobody took over is the descent above said from the other end and is
            // not one of these, for the reason it is not reported twice: the descent is the cause.
            case StructuralInspection.Continuation.None _,
                 StructuralInspection.Continuation.Elements _,
                 StructuralInspection.Continuation.Branches _ -> residue.rulesLeftUnread().stream()
                            .anyMatch(souther.compiler.inputs.RulesLeftUnread
                                    .ClauseOfThisReadingWasUnread.class::isInstance)
                    ? new souther.compiler.inputs.BlockReason.ValueRulesNotReached() : null;
            // The reading of the declarations answered for the position, so there is no fallback
            // and nothing here was not got to. Whether a position with no fallback and no evidence
            // is one anything was read at is a different question, asked where that pair is
            // ({@link PendingPosition#of}).
            case null -> null;
        };
    }

    /** What one position's reading came to, as the reading itself answered it. */
    public static PositionAccount of(String behavior, Position position,
                                     StructuralInspection.Continuation pending) {
        // What the declarations left this position, which is what a behavior's rules divide. An
        // invariant restricts and a behavior divides what is left, so the measure that composes
        // classes out of a body's rules has to be told what there is to divide.
        return new PositionAccount(behavior, position.path(), position.type(),
                ReadingResidue.of(position), position.admitted().approximation(), pending,
                position.unansweredQuestions(), position.endsLeftOpenByAChoice());
    }

    /** A position outside a reading of the declarations, which is where a test writes one. */
    static PositionAccount at(String behavior, TermPath path, Type type) {
        return new PositionAccount(behavior, path, type, ReadingResidue.NOTHING,
                souther.compiler.values.ValueSet.ANY, null, List.of(), List.of());
    }
}
