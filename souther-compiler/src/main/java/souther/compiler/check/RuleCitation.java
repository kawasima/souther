package souther.compiler.check;

import souther.compiler.diag.Citation;
import souther.compiler.diag.SourceNameResolver;
import souther.compiler.source.SourceId;

import java.util.Set;

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
 * <p><b>Not {@link souther.compiler.partition.LineOrigin}.</b> That says where a rule was read, and
 * one rule read in two calls of a helper has two of them — so putting it here would make a document
 * choose which reading to show for a question the model raised once. This says where the rule was
 * written, which is the rule's own and is one however often it is read.
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
     * Where the author wrote it, for a rule that has no name.
     *
     * <p>{@link Citation} and not a bare position, because where a rule is written and where a
     * reader is standing are not always the same file: a comparison inside a helper is written
     * there and reached from the call, and the same type says both.
     *
     * <p>{@link RuleRef.Written} and not a kind of one. A holder that answers for a comparison and
     * for nothing else keeps the comparison and the place, and makes the handle
     * ({@link souther.compiler.partition.LineOrigin.ComparisonOrigin}) — which is the shape a fold
     * over these already has. Carried as a type variable here instead, the variable is unbound
     * wherever a handle is reached through this interface, and a document's own answers are then
     * types nothing settles.
     */
    record WrittenAt(RuleRef.Written rule, Citation at) implements RuleCitation {

        public WrittenAt {
            if (rule == null || at == null) {
                throw new IllegalArgumentException(
                        "a rule with no name is found by where it is: " + rule + " at " + at);
            }
        }
    }

    /**
     * How a report writes this, where it knows what to call a source.
     *
     * <p>The one formatter, shared with the borders a comparison draws — a rule and a line the same
     * rule drew are found the same way, and two spellings of one place would read as two places.
     * What is not shared is an identity: where a rule was read is
     * {@link souther.compiler.partition.LineOrigin}'s and one rule has as many of those as it has
     * readings.
     *
     * <p>Every word here is read off {@link #rule}. What goes in front of a place is what that rule
     * is, so a kind of rule added to the seal is one this sentence has words for or one that stops
     * the compile.
     */
    default String said(SourceNameResolver names, SourceId sectionSource) {
        return switch (this) {
            case Named it -> it.rule().citedName();
            // Written here, and reached from somewhere else: a comparison inside a helper is one
            // rule and a reader is sent to two places, which the citation already tells apart.
            case WrittenAt it -> it.rule().whatItIs()
                    + joining(it.at()) + it.at().said(names, sectionSource);
        };
    }

    /**
     * Where a handle reaches its rule, which is nothing for a rule the author named.
     *
     * <p>The one place a handle is taken apart. What a fold over these keeps is the rule once and
     * the places beside it, so that no state holds a second answer to which rule it is about, and
     * this is how a reader's handle becomes a place to keep.
     */
    static Set<Citation> placeOf(RuleCitation cited) {
        return switch (cited) {
            case Named _ -> Set.of();
            case WrittenAt it -> Set.of(it.at());
        };
    }

    /**
     * Every handle for {@code rule} that the places in {@code reachedAt} offer.
     *
     * <p>The inverse of {@link #placeOf}, and the one place a handle is put back together. A rule
     * the author named is found by that name from anywhere, so it has one handle however many
     * readers offered it; one written rather than named has a handle per place it was reached at.
     */
    static Set<RuleCitation> handlesFor(RuleRef rule, Set<Citation> reachedAt) {
        requireReached(rule, reachedAt);
        return switch (rule) {
            case RuleRef.Named it -> Set.of(new Named(it));
            case RuleRef.Written it -> reachedAt.stream()
                    .map(each -> (RuleCitation) new WrittenAt(it, each))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        };
    }

    /**
     * That {@code rule} was reached in the way rules of its kind are reached.
     *
     * <p>A rule with no name is found by where it is, so one of those with no place is something
     * nobody can be sent to look at; and a place beside a rule the author named is a second way to
     * say one thing, which two readers could spell two ways.
     */
    static void requireReached(RuleRef rule, Set<Citation> reachedAt) {
        if (rule instanceof RuleRef.Written == reachedAt.isEmpty()) {
            throw new IllegalArgumentException("a rule with no name is reached at a place and a rule"
                    + " with one is reached by it: " + rule + " at " + reachedAt);
        }
    }

    /**
     * What goes between the construct and the place.
     *
     * <p>Shared with the borders the same rule drew, which is the whole of what those two have in
     * common: a word, and how it joins to a place. Neither holds the other's identity.
     */
    static String joining(Citation at) {
        return at instanceof Citation.Elsewhere ? " in " : "@";
    }
}
