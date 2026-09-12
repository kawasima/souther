package souther.compiler.query;

import souther.compiler.partition.Generator;

/**
 * What one search of a rule of a decision came to.
 *
 * <p>Two answers and not one. Whether a row is owed at the rule is a question about the model;
 * whether this compiler could compose one to try it with is a question about this compiler. They
 * are asked by one search because composing is the instrument the first is asked through, and they
 * may not be recorded as one answer: a generator that could not compose is not a rule the model
 * leaves open, and a rule nothing settled is not a generator that fell short. An account folding
 * them writes what this compiler managed into what the model says, which is the one thing the
 * account exists to keep apart.
 *
 * <p>They do line up in one place, and the invariant below is that alignment rather than the two
 * being one: a search with nothing to try the rule with settled nothing, and a search that settled
 * something had something to try. What it rules out is a shortfall recorded beside a requirement
 * that was settled some other way — which would be a reader told a way is owed a row and that
 * nothing could be composed for it, of one search that composed one and ran it.
 *
 * @param requirement       whether a row is owed at the rule, in the three states ADR-0091 fixes
 * @param synthesisShortfall what the composing came to where it came to no candidate, or null where
 *                          it produced one. Null and not a word for "nothing": a search that
 *                          composed a row and a search whose shortfall nobody recorded are
 *                          different things to be told, and only one of them is a state this has
 */
public record RuleSettlement(RuleRequirement requirement,
                             Generator.UnresolvedCombination synthesisShortfall) {

    public RuleSettlement {
        if (requirement == null) {
            throw new IllegalArgumentException("a search of a rule comes to some answer about it");
        }
        boolean nothingToTry =
                requirement instanceof RuleRequirement.Unsettled.NothingWasComposedToTry;
        if (nothingToTry != (synthesisShortfall != null)) {
            throw new IllegalArgumentException(
                    "a search with nothing to try the rule with is one whose composing fell short,"
                            + " and this one says " + requirement + " beside " + synthesisShortfall);
        }
    }

    /** A search that had a candidate to try the rule with, whatever it then settled. */
    public static RuleSettlement of(RuleRequirement requirement) {
        return new RuleSettlement(requirement, null);
    }

    /** A search whose composing produced no candidate, in the words the composing came back with. */
    public static RuleSettlement nothingToTryWith(Generator.UnresolvedCombination why) {
        if (why == null) {
            throw new IllegalArgumentException("a composing that came to nothing says what of");
        }
        return new RuleSettlement(new RuleRequirement.Unsettled.NothingWasComposedToTry(), why);
    }
}
