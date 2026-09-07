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
            // Which rules count their lines this way, said once and over every kind of rule there
            // is. A declaration's clause counts them by the parts its author wrote and is named by
            // one of those instead, so a rule standing here as well would be a line that is a
            // part's and is not — answering that a declaration owes it and that no part drew it.
            //
            // Written as a switch with no default, so a kind of rule added later is one this stops
            // at until somebody says which of the two counts its lines. Asked as a list of the
            // kinds that may stand here, the new kind would be refused for not being on a list
            // nobody had reason to revisit.
            switch (rule) {
                case RuleRef.Comparison _, RuleRef.Ensures _ -> { }
                case RuleRef.Invariant _ -> throw new IllegalArgumentException(
                        "a declaration's clause counts its lines by the parts its author wrote: "
                                + rule);
                case RuleRef.Predicate _ -> throw new IllegalArgumentException(
                        "a rule that tells values apart draws no line to count: " + rule);
                // A fork is a rule by having been written and not by anything read out of it, so
                // there is no line of it to be the first, second or any of. A line counted here
                // would be a reader's own arithmetic filed under the construct that occasioned it.
                case RuleRef.Fork _ -> throw new IllegalArgumentException(
                        "a fork whose condition states no rule draws no line to count: " + rule);
            }
        }
    }
}
