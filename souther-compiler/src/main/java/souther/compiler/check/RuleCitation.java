package souther.compiler.check;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * How a reader finds the rule a question is about.
 *
 * <p>A projection of {@link RuleRef} for a document to print, and never an identity. Two rules are
 * the same rule when their {@code RuleRef}s are equal; what this adds is a handle an author can act
 * on, which is not the same thing and must not become a key — a rule written once and read twice is
 * one rule, and a rule and the handle for it are not in step wherever a name is absent.
 *
 * <p><b>A projection that holds what it is a projection of.</b> {@link #rule} is the rule this is
 * the handle for, and it is here rather than beside this in whoever is holding both. A state
 * carrying a rule and a handle built apart from it can be built with the two disagreeing, and
 * nothing about such a state is wrong until a document writes both — where the rule says one thing
 * and the sentence beside it says another. So there is one path from a piece of evidence to the rule
 * it is about, and it runs through here.
 *
 * <p>Holding the rule does not make this an identity. What a reader is shown is
 * {@link souther.compiler.publish.PublishedRuleHandle}, which is what an order over handles is taken
 * over, and two of these that a document writes alike come to one value there.
 *
 * <p>Two answers, because rules are found two ways, and which of the two a rule is found by is the
 * rule's own answer rather than a choice a caller makes ({@link RuleRef.Named},
 * {@link RuleRef.Written}). An author names a clause of an invariant and looks it up by that name; a
 * comparison in a body has no name and is found where it is written.
 *
 * <p><b>No place, and that is what makes it a projection rather than an answer.</b> Where a reader
 * is sent is worked out when a sentence is written, from the question {@link RuleReportAnchor}
 * names — so a helper whose rules move, saying the same thing, moves where the sentences point and
 * leaves every value holding one of these alone. A place here is a place in every answer that
 * carries one, which is every answer of every module that reads the rule.
 *
 * <p><b>Not {@link souther.compiler.partition.LineOrigin}.</b> That says which reading of a rule
 * this is, and one rule read in two calls of a helper has two of them. What is here is one per way
 * a document can be sent to the rule, which is not the same count: a rule the author named has one
 * from anywhere, a rule written where a reader can open it has one however often it is read, and a
 * rule whose source this compilation holds no file for has one per call it was reached through —
 * because there is nothing to open, and the call is what there is to show instead.
 */
public sealed interface RuleCitation {

    /**
     * Which rule of the model this is the handle for.
     *
     * <p>The one way from a handle to the rule. A reader holding a piece of evidence asks it for the
     * citation and the citation for the rule, so the two cannot be about different rules.
     */
    RuleRef rule();

    /** How a reader finds a rule the author wrote a name beside. */
    record Named(RuleRef.Named rule) implements RuleCitation {

        public Named {
            if (rule == null) {
                throw new IllegalArgumentException("a citation is of some rule");
            }
        }
    }

    /**
     * How a reader finds a rule the author wrote rather than named, which is by where it is.
     *
     * <p>{@link RuleReportAnchor} and not a place. Which question answers where such a rule is is
     * settled where the rule is read — the module that wrote it, or the reading that met it where
     * there is no such module to ask — and the answer is worked out when a sentence is written.
     *
     * <p>{@link RuleRef.Written} and not a kind of one. A holder that answers for a comparison and
     * for nothing else keeps the comparison and the anchor, and makes the handle
     * ({@link souther.compiler.partition.LineOrigin.ComparisonOrigin}) — which is the shape a fold
     * over these already has. Carried as a type variable here instead, the variable is unbound
     * wherever a handle is reached through this interface, and a document's own answers are then
     * types nothing settles.
     */
    record Written(RuleRef.Written rule, RuleReportAnchor anchor) implements RuleCitation {

        public Written {
            if (rule == null || anchor == null) {
                throw new IllegalArgumentException("a rule with no name is found by where it is: "
                        + rule + " placed " + anchor);
            }
        }
    }

    /**
     * Which question places a handle's rule, which is nothing for a rule the author named.
     *
     * <p>The one place a handle is taken apart. What a fold over these keeps is the rule once and
     * the anchors beside it, so that no state holds a second answer to which rule it is about, and
     * this is how a reader's handle becomes something to keep.
     */
    static Set<RuleReportAnchor> anchorOf(RuleCitation cited) {
        return switch (cited) {
            case Named _ -> Set.of();
            case Written it -> Set.of(it.anchor());
        };
    }

    /**
     * Every handle for {@code rule} that the questions in {@code reachedBy} offer.
     *
     * <p>The inverse of {@link #anchorOf}, and the one place a handle is put back together. A rule
     * the author named is found by that name from anywhere, so it has one handle however many
     * readers offered it. So is one written where a reader can open it: every reader of it offers
     * the same question, which is the writing module's, and the set comes to one entry by being
     * one value. A rule whose source this compilation holds no file for has a handle per call it
     * was reached through, because the call is what a report can show and each is a different one.
     */
    static Set<RuleCitation> handlesFor(RuleRef rule, Set<RuleReportAnchor> reachedBy) {
        requireReached(rule, reachedBy);
        return switch (rule) {
            case RuleRef.Named it -> Set.of(new Named(it));
            case RuleRef.Written it -> reachedBy.stream()
                    .map(each -> (RuleCitation) new Written(it, each))
                    .collect(Collectors.toUnmodifiableSet());
        };
    }

    /**
     * That {@code rule} was reached in the way rules of its kind are reached.
     *
     * <p>A rule with no name is found by where it is, so one of those with nothing to ask is
     * something nobody can be sent to look at; and a question about where a rule the author named
     * is written is a second way to say one thing, which two readers could spell two ways.
     */
    static void requireReached(RuleRef rule, Set<RuleReportAnchor> reachedBy) {
        if (rule instanceof RuleRef.Written == reachedBy.isEmpty()) {
            throw new IllegalArgumentException("a rule with no name is placed by some question and"
                    + " a rule with one is found by it: " + rule + " placed " + reachedBy);
        }
    }
}
