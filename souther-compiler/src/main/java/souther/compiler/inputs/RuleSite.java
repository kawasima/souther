package souther.compiler.inputs;

import souther.compiler.check.ClauseName;
import souther.compiler.check.PartId;
import souther.compiler.check.RuleRef;
import souther.compiler.types.SourceConstructOrigin;

import java.util.Comparator;

/**
 * Where inside a rule a reader is sent, said as what an author wrote rather than as where it is.
 *
 * <p>What a reader lifts is a rule for most of what this compiler is short of, and a part of one
 * for the rest. A clause the reading of ends could not turn into a line is lifted by rewriting the
 * clause; an end a choice in it left open is lifted inside the part the choice is written in, and
 * the parts beside it read perfectly well. Said with the rule alone, the second is the first — which
 * is how two parts of one clause came to one entry that named neither.
 *
 * <p><b>The part and not the place.</b> Where a part is written follows from the declaration and
 * from nothing any reading did, so it is looked up by whoever is about to point somewhere
 * ({@link souther.compiler.check.PartLocations}) and this says which part to look up. Held as a
 * place, every answer built out of one would differ whenever a declaration above it moved — and the
 * answers travel to every module that imports the declaration.
 *
 * <h2>What makes two of them one</h2>
 *
 * <p>An author's editing place, and not how many things they are left with. How many there are is
 * the copy's answer ({@link souther.compiler.types.ConstructOccurrence}), which a published answer
 * does hold and which this deliberately does not: a helper expanded at two calls leaves two things
 * to lift and one place to go, and an author told to go twice would be acting on how often this
 * compiler copied the operator. Rewriting it where they wrote it answers every copy.
 *
 * <p>What may not be held is the coordinate of the tree a reading walked
 * ({@code check.ClauseOccurrence}), which two constructions of one declaration give differently.
 * The copy is not that: it is counted over expansion sites the source settles.
 */
public sealed interface RuleSite {

    /** The rule, with nothing inside it singled out: what a reader lifts is the whole of it. */
    record TheRuleItself() implements RuleSite {}

    /**
     * One part of the rule, which is where a reader is sent instead of to the rule.
     *
     * <p>Told from another by which part it is, which is counted over what the author wrote and is
     * the same whichever reading met it.
     */
    record APartOfIt(PartId<RuleRef.Invariant> part) implements RuleSite {

        public APartOfIt {
            if (part == null) {
                throw new IllegalArgumentException("a part of a rule is some part of it");
            }
        }
    }

    /**
     * One construct its author wrote, which is the finest thing a reader can be sent to.
     *
     * <p>Told from another by which construct of which owner it is
     * ({@link SourceConstructOrigin}), counted within that owner and holding no place — so a
     * declaration written above it moves it nowhere.
     *
     * <p><b>The construct and not the copy of it.</b> A helper is expanded at each call, and the
     * copies carry the origin they were given; what an author rewrites is the one they wrote, and
     * rewriting it answers every copy. Told apart by the copy as well, one operator would come back
     * as one thing to look at per call — which is a fact about this compiler's expansions and not
     * about how many places its author has to go.
     */
    record AConstructTheAuthorWrote(SourceConstructOrigin origin) implements RuleSite {

        public AConstructTheAuthorWrote {
            if (origin == null || !origin.isWritten()) {
                throw new IllegalArgumentException(
                        "a construct a reader is sent to is one its author wrote: " + origin);
            }
        }
    }

    /** The rule as a whole, for a reader with nothing inside it to point at. */
    static RuleSite theRuleItself() {
        return THE_RULE_ITSELF;
    }

    /** The part {@code part} names, which is where an author goes about what was decided there. */
    static RuleSite at(PartId<RuleRef.Invariant> part) {
        return new APartOfIt(part);
    }

    /**
     * The construct {@code origin} names, and the part it stands in where no author wrote it.
     *
     * <p>The one place the fallback is decided. A reading meets constructs a pass composed —
     * substituting a construction's field for a read puts a shape in the tree its author did not
     * write — and those have no construct of anybody's to be sent to. Given one anyway, a reader
     * would be pointed at something nobody can edit; dropped, a rule nothing read would come back
     * with nothing said about it. So what they get is the part it stands in, which is the finest
     * thing an author did write there.
     */
    static RuleSite at(SourceConstructOrigin origin, PartId<RuleRef.Invariant> standingIn) {
        return origin != null && origin.isWritten()
                ? new AConstructTheAuthorWrote(origin) : at(standingIn);
    }

    /** The one of those, since it holds nothing and two of them say the same thing. */
    RuleSite THE_RULE_ITSELF = new TheRuleItself();

    /**
     * Where this stands among these, for a reader putting some of them in a steady order.
     *
     * <p>Beside the members, so that whoever adds one places it. It calls two of these one exactly
     * where they are one: every component the equality reads is read here too, in turn and never
     * run together into a word — two names joined are one word, and one word is the same word for
     * more than one pair of names. Two entries alike in every word a document prints are told apart
     * by what they are about, and an order that stopped at the word would leave exactly those in
     * the order they were met ({@code ASteadyOrderTellsApartWhateverEqualityTellsApartTest}).
     *
     * <p><b>Steady and nothing else.</b> Which of two things an author wrote first is a fact about
     * where they wrote them, and is asked of the places at the one boundary that has them. Nothing
     * here is that, and nothing may be read off it: a part coming before a construct says only that
     * this compiler writes them in that order twice.
     */
    Comparator<RuleSite> IN_A_STEADY_ORDER =
            Comparator.<RuleSite>comparingInt(RuleSite::rank)
                    .thenComparing(RuleSite::inWhichModule, Comparator.naturalOrder())
                    .thenComparing(RuleSite::onWhatItIsDeclared, Comparator.naturalOrder())
                    .thenComparingInt(RuleSite::whichClause)
                    .thenComparing(RuleSite::wasNamed, Comparator.naturalOrder())
                    .thenComparing(RuleSite::calledWhat, Comparator.naturalOrder())
                    .thenComparingInt(RuleSite::whichPart)
                    .thenComparing(RuleSite::wroteIt,
                            Comparator.nullsFirst(SourceConstructOrigin.inASteadyOrder()));

    private static int rank(RuleSite site) {
        return switch (site) {
            case TheRuleItself _ -> 0;
            case APartOfIt _ -> 1;
            case AConstructTheAuthorWrote _ -> 2;
        };
    }

    /**
     * Which module declared what a part is of, and which declaration of that module it is.
     *
     * <p>Two names and two comparisons, never joined into one word: two names run together are one
     * word, and one word is the same word for more than one pair of names. An order that compared
     * the rendering would call two declarations one and leave them wherever the walk put them.
     */
    private static String inWhichModule(RuleSite site) {
        return switch (site) {
            case TheRuleItself _, AConstructTheAuthorWrote _ -> "";
            case APartOfIt it -> it.part().rule().clause().id().declaredOn().key().module();
        };
    }

    /** Which declaration of that module it is — see {@link #inWhichModule}. */
    private static String onWhatItIsDeclared(RuleSite site) {
        return switch (site) {
            case TheRuleItself _, AConstructTheAuthorWrote _ -> "";
            case APartOfIt it -> it.part().rule().clause().id().declaredOn().key().name();
        };
    }

    /**
     * Whether the author named the clause, and what they called it — two questions and two
     * comparisons.
     *
     * <p>Part of what a part is, so part of what tells two apart: a clause carries the name it was
     * written under beside the number it is, and two parts alike in every number and not in the
     * name are two parts. Left out, the order would call them one while everything that holds them
     * calls them two.
     *
     * <p>Asked apart because an absence is not a name. A clause nobody named and one named with no
     * characters are two clauses to everything that holds them, and a comparison that answered
     * both with the same word would be reading an absence as a value that happens to be empty —
     * which is how an order comes to call two things one.
     */
    private static boolean wasNamed(RuleSite site) {
        return switch (site) {
            case TheRuleItself _, AConstructTheAuthorWrote _ -> false;
            case APartOfIt it -> it.part().rule().clause().name().isPresent();
        };
    }

    /** What they called it, where they named it — see {@link #wasNamed}. */
    private static String calledWhat(RuleSite site) {
        return switch (site) {
            case TheRuleItself _, AConstructTheAuthorWrote _ -> "";
            case APartOfIt it -> it.part().rule().clause().name()
                    .map(ClauseName::value).orElse("");
        };
    }

    /**
     * Which clause of that declaration a part is of, and which of its parts it is.
     *
     * <p>Two numbers and two steps, because they are counted within two things: a clause within a
     * declaration and a part within a clause. Packed into one, the pair would be an order until a
     * clause was written in more parts than the packing left room for, and what came out then would
     * be a document that had quietly changed its mind.
     */
    private static int whichClause(RuleSite site) {
        return switch (site) {
            case TheRuleItself _, AConstructTheAuthorWrote _ -> 0;
            case APartOfIt it -> it.part().rule().clause().id().ordinal();
        };
    }

    /** Which of that clause's parts it is — see {@link #whichClause}. */
    private static int whichPart(RuleSite site) {
        return switch (site) {
            case TheRuleItself _, AConstructTheAuthorWrote _ -> 0;
            case APartOfIt it -> it.part().ordinal();
        };
    }

    /** And what an author wrote, for the one that names it. */
    private static SourceConstructOrigin wroteIt(RuleSite site) {
        return switch (site) {
            case TheRuleItself _, APartOfIt _ -> null;
            case AConstructTheAuthorWrote it -> it.origin();
        };
    }
}
