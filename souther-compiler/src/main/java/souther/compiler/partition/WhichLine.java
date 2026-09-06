package souther.compiler.partition;

import souther.compiler.check.PartId;
import souther.compiler.check.RuleRef;

/**
 * Which of a rule's lines a line of the model is.
 *
 * <p>Two questions under one word until now. A declaration's clause is written in the parts its
 * author wrote, and a line it draws is one of those parts' — the part is what issued the name, and
 * a part may draw more than one line. A rule written in a body is not written in parts: what a
 * behavior's clause draws is counted over the comparisons the clause states, and a comparison is a
 * rule apiece. Held as one number, a reader was told which line without being told which of the two
 * counts it, and the number an author's conjunct carries and the number a reading of comparisons
 * carries are not the same fact.
 *
 * <p>So the answer says which it is. What it is for is the reader that wants the words a
 * declaration wrote its line in: that reader has a part to look the line up by where there is one,
 * and no reason to invent one where there is not.
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
     */
    record OfAPart(PartId part) implements WhichLine {

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
     * A comparison a rule written in a body states, counted over the comparisons of that rule.
     *
     * <p>Not a part of anything: a rule of a body is one rule and states as many comparisons as it
     * is written with, and this says which of them. Zero where the rule is a comparison itself,
     * which is a rule apiece and has no second line to be told from.
     */
    record OfAComparison(RuleRef rule, int line) implements WhichLine {

        public OfAComparison {
            if (rule == null) {
                throw new IllegalArgumentException("a line of a body is some rule's");
            }
            if (line < 0) {
                throw new IllegalArgumentException(
                        "the comparisons of a rule are counted from zero: " + line);
            }
        }
    }
}
