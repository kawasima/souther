package souther.compiler.partition;

import souther.compiler.check.DeclarationNewtypes;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.Symbols;
import souther.compiler.core.Core;
import souther.compiler.inputs.InputDomain;
import souther.compiler.inputs.InputReads;

import java.util.List;

/**
 * What the conditions of one behavior's body state, against the reading of its input.
 *
 * <p>One value for the whole reading of a body, which is what the reading of the input is. A walk is
 * at a program point and what it carries is copied at every step, so a reading held in the walk is
 * asked of whichever copy a reader happens to hold — the defect
 * {@code NothingThatWalksATreeNamesTheReadingOfTheInput} is written against. What moves along the
 * walk is the environment; this is made once beside it and asked.
 *
 * <p>Nothing is decided here. Both answers are {@link ReachingCuts}'s, which is where a comparison
 * is turned into what it states and where reaching an arm is turned into a narrowing; asked a second
 * way, a column of a decision table and a line drawn on the same comparison would be free to
 * disagree.
 *
 * @param inputs the reading of what this behavior's input holds
 * @param rules  the reading of the declarations the comparisons are read against
 */
record ConditionMeanings(InputDomain inputs, RuleReadingSource rules) {

    /** The names the body's own text is read under. */
    Symbols symbols() {
        return rules.symbols();
    }

    /** Which declarations wear one value, which is what says whether reading a field reaches
     *  somewhere else ({@link souther.compiler.check.Location#isStep}). */
    DeclarationNewtypes newtypes() {
        return rules.newtypes();
    }

    /** What {@code condition} coming out {@code held} states, and where it states nothing, that. */
    List<OnTheWay> stating(Condition condition, boolean held) {
        return ReachingCuts.stating(condition, inputs, held, rules);
    }

    /**
     * What entering {@code part} of {@code match} says the scrutinee turned out to be.
     *
     * <p>The environment is handed in rather than held, because it is a function of the program
     * point and this is not.
     */
    OnTheWay entering(Core.Match match, int part, InputReads reads, ConditionNumbering numbering) {
        return ReachingCuts.entering(match, match.cases().get(part), part, inputs, reads, rules,
                numbering);
    }
}
