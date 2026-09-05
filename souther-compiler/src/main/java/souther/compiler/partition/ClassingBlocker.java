package souther.compiler.partition;

import souther.compiler.inputs.BlockReason;
import souther.compiler.inputs.FilingCoordinate;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.RuleWithoutALine;

/**
 * A rule that would have told a set of one position's values from the rest, and did not.
 *
 * <p>What keeps a position's classes from being published out of the rules that happened to work.
 * The rules of a position are one denominator, so a reader given the classes of some of them holds
 * a partition the model does not draw — and the ones that did not come out have to be present where
 * that decision is made, or the decision is made over the successes.
 *
 * <p><b>Not a piece of evidence</b> ({@link RuleEvidence}). That is what a rule of the model
 * was read to say about how a position's values are divided, and this rule was not read to say
 * anything: what it carries is that this compiler did not get there. Put among the evidence, an
 * account would owe a measure for it and a reader would be told the model divides a position in a
 * way nothing can name.
 *
 * <p><b>And not a finding either, though it becomes one.</b> A finding is what a person is shown;
 * this is what closes — or refuses to close — a denominator, which is a fact this compiler acts on.
 * Carried as a finding and picked back out by asking which rule it was about and why it stopped,
 * every reader would be re-deciding what kind of thing it is, one {@code instanceof} at a time.
 * So it is the value, and {@link #reported()} is where it becomes the other thing.
 *
 * @param at  the position it would have divided, which is known before the reading of it is: where
 *            the rule's subject stands is settled by the walk that met it, and what the rule states
 *            is settled afterwards
 * @param by  which rule it is and which reading of it, so that two readings of one rule block the
 *            two positions they are about rather than one blocking both
 * @param why what became of the reading, kept as it was. Collapsed into one word for "the classes
 *            were not composed", a pattern this compiler would not read and two vocabularies that
 *            will not go together would be one sentence — and only one of them is about the model
 */
public record ClassingBlocker(NumericTerm.FromOnePosition at, PredicateOrigin by,
                              BlockReason.RuleWithoutLineReason why) {

    public ClassingBlocker {
        if (at == null || by == null || why == null) {
            throw new IllegalArgumentException(
                    "a rule that did not divide a position is some rule, about some position, and"
                            + " something became of it");
        }
    }

    /** The same, measured at {@code term} — what filing does to it, and to the evidence beside it. */
    public ClassingBlocker measuredAt(NumericTerm.FromOnePosition term) {
        return new ClassingBlocker(term, by, why);
    }

    /** What a person is shown, which is the other thing this is. */
    public RuleWithoutALine reported() {
        return RuleWithoutALine.of(by.rule(), by.cited(),
                FilingCoordinate.at(at.position()), why);
    }
}
