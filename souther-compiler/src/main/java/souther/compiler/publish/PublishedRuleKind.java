package souther.compiler.publish;

import souther.compiler.check.RuleRef;

/**
 * The word a document uses for a rule the author wrote rather than named.
 *
 * <p>A published word, so it is spelled here and not read off the class that happens to hold such a
 * rule today. Generated from the internal seal, renaming {@code RuleRef.Comparison} would widen or
 * move what a consumer must handle without anybody deciding to — which is the mistake the schema's
 * enumerated fields are held against rather than generated from.
 *
 * <p>What keeps this in step with the rest is that the projection below is total. A kind of written
 * rule added to the seal and left without a word stops the compile, at the one place a word is
 * chosen.
 */
public enum PublishedRuleKind {

    /** A rule that puts a line on the order the values at a position are counted on. */
    COMPARISON("comparison"),

    /** A rule that tells a set of the values at a position from the rest, and draws no line. */
    PREDICATE("predicate"),

    /** A fork whose condition states none of the others, which is the model saying it divides on
     *  something this compiler did not read. */
    FORK("fork");

    private final String word;

    PublishedRuleKind(String word) {
        this.word = word;
    }

    /** What a document writes. */
    public String word() {
        return word;
    }

    /**
     * The word for {@code rule}.
     *
     * <p>No {@code default} arm, so a kind of written rule added to the seal is one somebody gives a
     * published word rather than one that arrives in a document under a word that already meant
     * something else.
     */
    public static PublishedRuleKind of(RuleRef.Written rule) {
        return switch (rule) {
            case RuleRef.Comparison _ -> COMPARISON;
            case RuleRef.Fork _ -> FORK;
            case RuleRef.Predicate _ -> PREDICATE;
        };
    }
}
