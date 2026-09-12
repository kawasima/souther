package souther.compiler.inputs;

import souther.compiler.check.PartId;
import souther.compiler.check.RuleRef;
import souther.compiler.types.SourceConstructOrigin;

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
 * <p>An author's editing place, and not how many times a reading met the operator. How many times
 * it was met is the reading's own answer, an identity two constructions of one declaration give
 * differently and which no published answer may hold. What an author is owed is how many places
 * they have to go, and one part written once is one place however often an expansion copies it:
 * rewriting it there answers every copy.
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
}
