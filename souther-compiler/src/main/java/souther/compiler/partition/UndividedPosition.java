package souther.compiler.partition;

import souther.compiler.inputs.BlockReason;
import souther.compiler.inputs.TermPath;

/**
 * A position no class came back for, and which of the three ways that happened.
 *
 * <p>The list of these used to be a list of paths, and everything downstream read a path in it as the
 * model having no distinction to draw there. A position whose rule is written in a form this does not
 * read is in that same list, and saying the same sentence about both tells an author to stop looking
 * for the line their own body draws.
 *
 * <p>So the absence is a value that has to be produced rather than the default reading of an empty
 * result. {@link Why.Absent} is produced by {@link PendingPosition#complete} and nowhere else, from
 * a position the reading got to the rules of, every question of which was answered, and which no
 * rule is filed at — which is what the word means.
 *
 * <p>The other two are the two ways of not being that, and they are opposite sentences about this
 * compiler. {@link Why.CannotDerive} says the readings did not get far enough for anything about
 * the model to follow; what leaves a position in that state is enumerated where the verdict is
 * made ({@link PendingPosition#complete}) and is not counted again here.
 * {@link Why.StatedWithoutALine} says the other thing — a rule is filed here and came to no line,
 * with nothing outstanding about it, so a reader sent after a limit would be looking for one that
 * is not there.
 *
 * <p><b>All three are a projection and none is a reading's own account of itself.</b> Whether a
 * question stands is asked of the accounting that holds every question a rule raises against
 * whatever answered it, so a reading short of a rule that another reading took in leaves nothing
 * standing. Read instead off what one reading was left with, a rule the reading of ends read from
 * end to end and the reading of values did not take in came out as a position nothing could read.
 *
 * @param at  the position, spelled the way a report names it
 * @param why whether the model draws nothing here, the readings did not get far enough to say, or
 *            a rule filed here came to no line
 */
public record UndividedPosition(TermPath at, Why why) {

    /** Which of the three it is. */
    public sealed interface Why {

        /**
         * The readings got to the rules of the position, every question those rules raise was
         * answered, and no rule is filed here: the model divides it no way at all.
         *
         * <p>A class with no way to make one rather than a record, because what it says is a
         * conclusion about a model and the only thing entitled to draw it is the completion of a
         * {@link PendingPosition}. Anything able to write {@code new Absent()} is able to say the
         * model divides a position no way without having asked anything, which is the sentence this
         * whole protocol exists to stop being cheap to write.
         */
        final class Absent implements Why {

            /** The one, reached through {@link PendingPosition#complete}. */
            static final Absent PROVEN = new Absent();

            private Absent() {}

            @Override
            public boolean equals(Object other) {
                return other instanceof Absent;
            }

            @Override
            public int hashCode() {
                return Absent.class.hashCode();
            }

            @Override
            public String toString() {
                return "Absent";
            }
        }

        /**
         * The readings did not get far enough for anything about the model to follow.
         *
         * <p>However many ways there are of that being so, one word. Which of them, and what was
         * short of it, is not here — the list is where the verdict is made
         * ({@link PendingPosition#complete}), so that a way added is added in one place rather than
         * in every sentence that describes this one. A verdict says whether anything divides the position;
         * the findings beside it say what was not read and by whose account, and each of those is
         * made by the reader that has the fact — with the rule where there is one. Carried here
         * too, a report read the cause back off the verdict, which is where the rule had already
         * been lost.
         */
        record CannotDerive() implements Why {}

        /**
         * A rule is filed here that came to no line, with nothing about it outstanding.
         *
         * <p>Neither of the two above. Not an absence — the model states something at this position,
         * and a verdict saying it divides the position no way would deny the declaration two tokens
         * away. Not a derivation this compiler could not make either: nothing was missing, and a
         * reader sent after a limit would be looking for one that is not there.
         *
         * <p>What states it is named in a finding of its own, with the rule. Nothing here carries
         * the rule, for the reason {@link CannotDerive} carries no cause: a verdict says whether
         * anything divides the position, and reading a cause back off a verdict is where the rule
         * was lost.
         */
        record StatedWithoutALine() implements Why {}
    }

    /**
     * What stopped the derivation, in the words a document is written in.
     *
     * <p>Each of these is a fact about this compiler, said at the coarseness a reader of a document
     * is promised. They are told apart because they are lifted by different work: one wants a
     * reader for a form of condition, one wants the walk to go deeper, and a report that named
     * neither could not say which.
     *
     * <p>Not the cause itself. What a producer recorded is a {@link BlockReason}, and one of these
     * is what {@link ReportedReason} projects it to — three missing traversals arrive here as one
     * word. So a reader of this knows which kind of thing stopped the derivation and not which
     * capability was missing.
     */
    public enum Reason {
        /**
         * A rule about this position was read and could not be used.
         *
         * <p>Said of the rule and not of one way of failing to use it. A comparison inside a
         * condition this does not take apart is one; a rule naming which values may stand here,
         * written as something other than a value written out, is another. Which reader of the
         * clause gave up is not part of the promise.
         *
         * <p>A rule this never arrived at is {@link #RULES_NOT_READ_AT_ALL} and not this. The two
         * were one word, and the sentence this one prints — an expression the terms do not name —
         * was said of positions whose rules nothing had looked at, which is a different thing and
         * is lifted by different work.
         */
        UNSUPPORTED_SYNTAX,
        /**
         * The reading did not reach the rules written about this position.
         *
         * <p>Nothing here was read and found wanting: the rules are behind something this walk did
         * not enter, and what is written under it is whatever it is. Which limit stopped the walk
         * is not recorded and is not promised — a depth, a type it had been through, a shape it
         * does not descend into, and a clause that could not be typed all leave the same hole, and
         * a reader is told the hole and not this compiler's route to it.
         */
        RULES_NOT_READ_AT_ALL,
        /**
         * A rule about this position offers an alternative this compiler does not read, and what
         * the rule leaves here is what its alternatives leave together.
         *
         * <p>Its own word beside {@link #UNSUPPORTED_SYNTAX}, and the difference is what an author
         * does about it. That one promises the rule at this position is written in a form nothing
         * here takes apart, which sends them to rewrite it; the rule here reads perfectly well and
         * what they can act on is the branch written beside it. Said as the other word, an author
         * rewrites a bound that was never the difficulty.
         *
         * <p>A fact about this compiler and not about the model. The choice may hold the position
         * exactly where the rule at it says, and may hold it nowhere; which of the two it is, is
         * what reading the alternative would answer.
         */
        UNREAD_ALTERNATIVE_OF_A_CHOICE,
        /**
         * The compiler followed the rules about this position, and building the exact set of values
         * they leave between them cost more than it allows itself.
         *
         * <p>Its own word because nothing else here says it. {@link #UNSUPPORTED_SYNTAX} promises a
         * rule was read and could not be used, which sends an author after the form it is written
         * in. Here every rule was interpreted and the interpretation is what turned out to be too
         * large, so that word would send a reader after something that is not the matter.
         *
         * <p>Said of a question a rule raised as readily as of a position. A rule read from end to
         * end whose position's values were not worked out leaves its question standing on this and
         * on nothing else, and one whose form also defeated a reading leaves it standing on both.
         *
         * <p><b>About the answer and not about a rule.</b> Two rules each cheap on their own can
         * have an answer between them that is not, so nothing here names a rule to go and change.
         * What a reader may do about it is state the position's values in a way that composes to
         * less — or take the answer as the upper bound it is.
         */
        EXACT_VALUES_TOO_COSTLY,
        /**
         * The compiler read the behavior's rules about the values at this position, and working out
         * what they tell apart cost more than it allows itself.
         *
         * <p>Its own word beside {@link #EXACT_VALUES_TOO_COSTLY}, which is what the position
         * admits coming out wider than the rules leave it. That is a fact about the declarations,
         * and this is a fact about a body: a position whose declaration was answered to the letter
         * still lands here when the behavior's own rules about it are the expensive ones. Told the
         * other word, an author would go and simplify a declaration that was never the matter.
         *
         * <p><b>About the position's distinctions and not about a rule.</b> The rules of one
         * position are worked out as one group, so that which of them a reader hears about does not
         * follow the order they were walked in — and what ran out is the group's, which no single
         * rule is answerable for.
         */
        BEHAVIOR_DISTINCTIONS_TOO_COSTLY,

        /**
         * A rule is written more deeply nested than this compiler reads.
         *
         * <p>Its own word because what a reader may do about it is its own. {@link
         * #UNSUPPORTED_SYNTAX} promises a construct nothing here enters, and every construct in
         * this rule is entered; {@link #EXACT_VALUES_TOO_COSTLY} promises the values were worked
         * out as far as an allowance allowed, and this stopped before any of that. What is left is
         * how the rule is bracketed, which is something an author can write differently.
         */
        PATTERN_TOO_DEEPLY_NESTED,
        /** The values the comparison is against are not ones a line can be drawn on here. */
        UNSUPPORTED_DOMAIN,
        /**
         * The line reaches positions under the cases each side of it, and which of them go together
         * is not worked out.
         *
         * <p>Not about how many sums are on the way. A name each side of the line stands at more
         * than one position, and whether those positions pair off one for one or every one against
         * every other is a fact about the model — two names narrowed by one value are narrowed
         * together, and two names under separate choices are not. Said as a shape this compiler does
         * not read, an author would go looking for another way to write a comparison it reads
         * perfectly well.
         */
        UNRESOLVED_CASE_PAIRING,
        /**
         * The comparison relates two positions rather than dividing one.
         *
         * <p>`+x < y+` says where one position stands against another, and a class here is a set of
         * values of one position. Nothing is missing from the carrier — both sides are ordered and a
         * line drawn on either against a number would be read — so saying the values cannot carry a
         * line would send a reader after the wrong thing entirely.
         */
        UNSUPPORTED_PARTITION_SHAPE,
        /**
         * The rule draws its line on a number taken over a run of this position's values, so it
         * divides none of them.
         *
         * <p>Its own word and not the shape above. Nothing here is between two positions and
         * nothing is missing: the rule is read, its border is drawn, and the number it is about is
         * what the values at this place come to rather than any one of them. Two of them either
         * side of a total are on the line as surely as one is, so the position has no class this
         * rule drew — and a reader told the rule relates two positions would go looking for a pair
         * that is not there.
         */
        RULE_ABOUT_A_RUN,
        /**
         * The rule holds this position to the values it admits, and places no end on them.
         *
         * <p>A format states which strings stand here, and everything else is refused at
         * construction — so what the rule did is restrict the position, and the strings it leaves
         * out are no class of it. What a reader acts on is that the value written here is one the
         * rule admits.
         *
         * <p>A position carrying only such a rule is not one the model divides no way, and neither
         * is it one divided into something this measure has no representation for. Said as the
         * first, the position goes out with nothing to act on and a reader takes the silence for a
         * conclusion; said as the second, a reader is told the model divides a position its
         * declaration refuses to build the other side of.
         *
         * <p>Nothing here about whether the position is divided. A rule may restrict and divide at
         * once, and what a position divides into is said where the classes are.
         */
        POSITION_RESTRICTED_TO_WHAT_A_RULE_ADMITS,
        /**
         * The input returns here to a declaration it has already been through, and what is under
         * this position was not read again.
         *
         * <p>A position whose values are values of a type the path has already met. What stands
         * under it is what stands under that one, so a reader is told the return rather than a list
         * of positions repeating for as long as anybody keeps reading.
         */
        RETURNS_TO_A_DECLARATION_ALREADY_READ,
        /**
         * The type at this position could not be interpreted, so nothing about its values is
         * established. A report is written about a model carrying one, which is why this is a word
         * a report writes rather than a state nothing reaches.
         */
        TYPE_UNRESOLVED,
        /**
         * A rule is written about a value that came from this position rather than about it.
         *
         * <p>The rule was read and where its value came from is known. What it says about the values
         * at this position is not: an operation made them into something else first, and reading the
         * rule back through that is a capability nothing here has.
         *
         * <p>Its own word, so that a reader is not sent after a form this compiler cannot parse.
         * What would lift it is a reading of what a closure does to a value, and not a wider
         * fragment of comparisons.
         */
        RULE_ABOUT_A_DERIVED_VALUE,
        /**
         * A rule is written about an element of a sequence, inside a block handed to more than one
         * walk — so it is about this position or another and nothing here says which.
         *
         * <p>The rule was read and every position it might be about is known; each of them is told
         * this. What is missing is not a reading of the rule but a way to tell the walks apart, and
         * an author can see it in their own model: the block is written once and given to two
         * operations over two sequences.
         *
         * <p>Its own word beside {@link #RULE_ABOUT_A_DERIVED_VALUE}, which says an operation made
         * the value into something else. Nothing was made here — the value is the element — and a
         * reader told the other would go looking for a computation to invert that is not there.
         */
        RULE_ABOUT_AN_ELEMENT_OF_SEVERAL_SEQUENCES,
        /**
         * A rule naming this position was read to the end and cuts nothing at all: what it compares
         * is a number the position does not appear in.
         *
         * <p>Its own word because nothing is owed on its account. A reader told the spelling
         * defeated this compiler would go looking for a form to rewrite, and the form was read
         * completely — there was no line in the rule to draw.
         */
        RULE_CUTS_NOTHING,
        /**
         * A rule naming this position was read to the end and puts every value it may hold on one
         * side of itself.
         *
         * <p>What a rule about the values at a position does is tell some of them from the rest,
         * and that is two classes only where both sides hold a value. One no value satisfies, and
         * one every value satisfies, leave the position where they found it.
         *
         * <p>Its own word beside {@link #RULE_CUTS_NOTHING}, which is a rule that names no quantity
         * the position appears in. Here the rule is about this position and was read completely;
         * what came of it is that the model draws no line between any two of its values, and that
         * is something an author can see in the rule itself.
         */
        RULE_TELLS_NOTHING_APART,
        /**
         * The rules about this position were read, and what they say cannot all be said as one list
         * of classes.
         *
         * <p>A rule puts a line on the order the values are counted on, or tells a set of them from
         * the rest, and a class written in one of those cannot be written in the other. A position
         * both kinds of rule reach has no single denominator here.
         *
         * <p>Its own word because nothing fell short. {@link #EXACT_VALUES_TOO_COSTLY} and
         * {@link #BEHAVIOR_DISTINCTIONS_TOO_COSTLY} are sets that were not worked out, and a reader
         * told one of those would go looking at how much the rules cost; here every set was worked
         * out and what a wider run would change is nothing.
         *
         * <p>What the rules cut and where they part the position are unaffected and are still
         * reported, because those are observations of their own and not a projection of the classes.
         */
        CLASSES_NOT_COMPOSED,
        /**
         * A rule naming this position was read to the end and draws its line where the quantity it
         * cuts never runs: three times a length is never negative, and a rule comparing one against
         * a negative has no value either side of its line.
         *
         * <p>Its own word beside {@link #RULE_CUTS_NOTHING}, which is what a rule with no quantity
         * to cut gets. Both were read completely and neither divides the position, and what a
         * reader may go on to do about them differs: one rule states something about the position
         * that no row can satisfy, and the other states nothing about it at all.
         */
        RULE_CUTS_OUTSIDE_WHAT_THE_QUANTITY_HOLDS,
        /**
         * A rule naming this position was read to the end, its line is inside what the
         * declarations leave — and no row that arrives at the comparison holds a value at it: the
         * conditions on the way there rule the line's values out.
         *
         * <p>Its own word beside {@link #RULE_CUTS_OUTSIDE_WHAT_THE_QUANTITY_HOLDS}, which is a
         * fact about the declarations and holds wherever the rule stands. This one is about the
         * place the rule stands at, and what a reader does about it differs: there they read one
         * rule against the declarations, here they read the guards above it.
         */
        NOTHING_ARRIVES_AT_THE_RULES_LINE,
        /**
         * The position holds its values inside something this does not reach into — the elements of
         * a collection, what an optional holds, what a map holds. One word for all of them: which
         * reaching is missing is a fact about this compiler, and the model reads the same either
         * way. What this compiler could not do is told apart internally
         * ({@link BlockReason.UnsupportedTraversal}).
         */
        UNSUPPORTED_TRAVERSAL
    }

    /**
     * The absence, of a position that has been completed.
     *
     * <p>The argument is the proof. A {@link PendingPosition} is made from a position whose local
     * producers all came back with nothing, and only completing one reaches this — so what cannot
     * happen is an absence written by a reader that did not ask. Outside this package there is
     * neither a way to make one of those nor a name for this.
     */
    static UndividedPosition absentAfter(PendingPosition proven) {
        return new UndividedPosition(proven.at(), Why.Absent.PROVEN);
    }

    static UndividedPosition statedWithoutALine(TermPath at) {
        return new UndividedPosition(at, new Why.StatedWithoutALine());
    }

    static UndividedPosition cannotDerive(TermPath at) {
        return new UndividedPosition(at, new Why.CannotDerive());
    }

}
