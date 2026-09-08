package souther.compiler.partition;

import souther.compiler.check.PartId;
import souther.compiler.check.RuleRef;

/**
 * Which of a rule's lines a line of the model is.
 *
 * <p>Three questions under one word until now, and each is answered by whatever decomposed the rule
 * rather than by a number a reader arrived at. A clause an author named is written in the parts they
 * joined, and the split that made those parts issues each one's name; a part of a behavior's clause
 * states as many things as the reading of it finds, and that reading issues each statement's name; a
 * rule written in a body is a rule apiece and is decomposed by nothing, so there is no name below the
 * rule for it to carry. Held as one number over all three, a reader was told which line without being
 * told which of the three counted it, and no two of those counts are the same fact.
 *
 * <p>So the answer says which it is, and the arm carrying a number carries the one that was issued
 * with it. Nothing here numbers anything: an arm assembled from a rule and a number a caller had
 * would be that caller's own count filed under whichever rule they were holding, which is what
 * naming the parts and naming the statements were each made to close.
 */
public sealed interface WhichLine {

    /** Which rule of the model drew it. */
    RuleRef rule();

    /**
     * A part of a declaration's clause, named by what issued it.
     *
     * <p>One part may draw more than one line — a rule an author named states as many rules as its
     * body joins — so this says which part drew it and not which line of that part it is. Two lines
     * of one part are told apart by what each says about its own value ({@link LineFacts}).
     *
     * <p>A part of a {@code data}'s clause, which is where this parts from {@link OfAComparisonOfAPart}.
     * The lines a declaration draws are looked up by the words that declaration wrote
     * ({@link souther.compiler.check.DeclaredBorders}), and a behavior's clause has no such reading —
     * so which kind of clause the part is of is in the type rather than asked of one that arrives.
     */
    record OfAPart(PartId<RuleRef.Invariant> part) implements WhichLine {

        public OfAPart {
            if (part == null) {
                throw new IllegalArgumentException("a line of a declaration is some part's");
            }
        }

        @Override
        public RuleRef rule() {
            return part.rule();
        }
    }

    /**
     * One statement of one part of a behavior's clause.
     *
     * <p>Both, because a behavior's clause is decomposed twice. The author joined the parts, and what
     * one part states is read off the tree it expanded into — where a helper's body brings
     * connectives nobody wrote. A line is drawn by one of those statements, so which part and which
     * of that part's statements are both needed to say which line it is, and neither on its own does:
     * two parts each have a statement numbered nought, and one part states things a row at another's
     * line says nothing about.
     *
     * <p>Held as one number running over the whole clause, which the reading of lines counted for
     * itself, the number moved when a part before it came to state one thing more — so a line that
     * had not changed was a different line the day a helper above it gained a conjunct.
     */
    record OfAComparisonOfAPart(ClauseStatementId statement) implements WhichLine {

        public OfAComparisonOfAPart {
            if (statement == null) {
                throw new IllegalArgumentException("a line of a behavior's clause is some"
                        + " statement of some part of it");
            }
        }

        @Override
        public RuleRef rule() {
            return statement.rule();
        }
    }

    /**
     * A comparison written in a body, which is a rule apiece.
     *
     * <p>No number, because there is nothing below the rule to number. A condition holding three
     * comparisons is three rules, each with a line of its own, so there is no second line of this one
     * to tell it from; two lines of it at one value are told apart by what each says about its own
     * value ({@link LineFacts}), the way two lines of one part are.
     *
     * <p>A comparison and nothing else of what a body writes. A predicate tells a set of values from
     * the rest and draws no line to be one of; a fork is a rule by having been written, and a line of
     * it would be a reader's own arithmetic filed under the construct that occasioned it. Written
     * over every rule a body states, this would be an arm those two could stand in, and what refused
     * them would be a check somewhere below.
     */
    record OfAComparison(RuleRef.Comparison rule) implements WhichLine {

        public OfAComparison {
            if (rule == null) {
                throw new IllegalArgumentException("a line of a body is some comparison's");
            }
        }
    }
}
