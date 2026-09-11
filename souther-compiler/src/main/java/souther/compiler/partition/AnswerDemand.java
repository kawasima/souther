package souther.compiler.partition;

import souther.compiler.inputs.Refinement;
import souther.compiler.inputs.TermPath;
import souther.compiler.numeric.LinearForm;
import souther.compiler.numeric.Rel;

import java.util.List;

/**
 * What one condition of a rule asks of an answer a row stands a dependency in with.
 *
 * <p>The other half of what a way states. {@link WayToTheBorder} says what the input has to be for
 * a row to take a way, in the vocabulary of the input's terms; this says what a dependency has to
 * answer for the same way, in the vocabulary of what a row may pin one to. Two vocabularies because
 * the two are different licences — what a search may assume about the input is not what a row may
 * write for a dependency — and read off the same conditions so that neither can come to say
 * something the other did not.
 *
 * <p><b>The place inside the answer, and not the answer alone.</b> A body deciding on two fields of
 * one answer asks two things of it, and a demand stopping at the answer would run them together
 * after which a row could be composed satisfying one of them.
 *
 * <p>Nothing here says a value can be found. A demand says what a value would have to be; whether
 * one can be composed is settled by composing it.
 */
public sealed interface AnswerDemand {

    /** Whose answer this is about. */
    InjectedAnswer of();

    /** Which question a report about the condition this came from asks for its place. */
    ConditionReportAnchor anchor();

    /**
     * The answer, at a place inside it, read as one of its cases.
     *
     * @param at the fields read off the answer to reach what was forked on, in the order they are
     *           written. Empty where the fork is on the answer itself
     */
    record ACase(InjectedAnswer of, ConditionReportAnchor anchor, List<TermPath.Step> at,
                 Refinement to) implements AnswerDemand {

        public ACase {
            if (of == null || anchor == null || at == null || to == null) {
                throw new IllegalArgumentException("a case asked of an answer is some case");
            }
            at = List.copyOf(at);
        }
    }

    /** The answer, at a place inside it, read for its truth. */
    record ATruth(InjectedAnswer of, ConditionReportAnchor anchor, List<TermPath.Step> at,
                  boolean held) implements AnswerDemand {

        public ATruth {
            if (of == null || anchor == null || at == null) {
                throw new IllegalArgumentException("a truth asked of an answer is of something");
            }
            at = List.copyOf(at);
        }
    }

    /**
     * A comparison over numbers of one answer, as the relation that has to hold of them.
     *
     * <p>The relation the way took and not the column's canonical one. What a column is read as
     * faces one way whichever side of it a path took; what a row has to satisfy is the side this
     * path took, and a demand carrying the column's reading would compose a value for the other
     * arm as often as not.
     *
     * <p>Of one answer, because that is what this can state. A comparison whose numbers come from
     * two answers, or from an answer and the input, is one statement about the pair — which value
     * either may take depends on what the other took — and there is nothing here that composes two
     * of them together. Such a condition is not stated as a demand at all, the way a condition the
     * arithmetic could not take in is not stated as a narrowing.
     *
     * @param form what the comparison states, as {@code form rel 0}
     * @param rel  the relation this path took
     */
    record AComparison(InjectedAnswer of, ConditionReportAnchor anchor,
                       LinearForm<DecisionAtom> form, Rel rel) implements AnswerDemand {

        public AComparison {
            if (of == null || anchor == null || form == null || rel == null) {
                throw new IllegalArgumentException(
                        "a comparison asked of an answer is a relation over a form");
            }
        }
    }
}
