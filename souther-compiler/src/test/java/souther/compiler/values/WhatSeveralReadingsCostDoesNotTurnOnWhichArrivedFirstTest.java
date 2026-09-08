package souther.compiler.values;

import org.junit.jupiter.api.Test;

import souther.compiler.regex.PatternPlan;
import souther.compiler.regex.PatternParser;
import souther.compiler.regex.PatternRead;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Several readings met together cost what they are, and say what their author wrote.
 *
 * <p>Two orders and they are about different things. Which of the readings is built into which
 * first is a question about the machines, and it is settled by what the readings hold — so the same
 * readings come out the same, and cost the same, whichever of them a caller happened to hold first.
 * What is written down about each position is the other one: those are the author's rules going
 * unread, and a reader is shown them in the order they were written.
 *
 * <p>Kept apart because a fold that served both served neither. Ordered by content throughout, a
 * report listed an author's rules by a rule of this compiler's; ordered by arrival throughout, what
 * a model could be told exactly turned on which declaration this compiler read first.
 */
class WhatSeveralReadingsCostDoesNotTurnOnWhichArrivedFirstTest {

    /**
     * Two patterns whose meet is a large machine, and the one string they share.
     *
     * <p>The same shape a clause has when its rules are written in one declaration, one layer out:
     * three readings of one position, met by whoever needed all three. Met smallest first, the
     * answer is a question about one string; met the other way round, it is a product of three
     * hundred states against three hundred.
     */
    private static AdmissibleValues<String> matching(String regex) {
        PatternRead said = PatternParser.read(regex);
        return built(PlannedValues.at("value", AdmittedPlan.of(ValueSet.matching(
                PatternPlan.of(assertInstanceOf(PatternRead.Read.class, said).syntax())
                        .compile(PatternPlan.Budget.OF_ADMITTED_VALUES.meter())))));
    }

    /** A description worked out, which is how a reading is come by. The machine is compiled
     *  above, so working this out builds nothing and spends nothing. */
    private static AdmissibleValues<String> built(PlannedValues<String> planned) {
        return planned.resolve(AsACompilationAllows.forAdmittedValues()).values();
    }

    private static List<AdmissibleValues<String>> readings() {
        return List.of(matching("x|a{300}"), matching("x|b{300}"), matching("x"));
    }

    private static final List<List<Integer>> ORDERS = List.of(
            List.of(0, 1, 2), List.of(0, 2, 1), List.of(1, 0, 2),
            List.of(1, 2, 0), List.of(2, 0, 1), List.of(2, 1, 0));

    /** What the same three readings leave, and what they spend, in every order they can arrive. */
    @Test
    void everyArrivalOrderLeavesOneAnswerAndSpendsTheSame() {
        ValueSet first = null;
        int spent = -1;
        for (List<Integer> order : ORDERS) {
            List<AdmissibleValues<String>> read = readings();
            Allowance<String> allowed = AsACompilationAllows.forAdmittedValues();
            AdmissibleValues<String> made = AdmissibleValues.metAll(
                    order.stream().map(read::get).toList(), allowed);

            assertEquals(List.of(), made.whyUnread("value"),
                    "nothing here has to be built, arriving as " + order);
            if (first == null) {
                first = made.at("value");
                spent = spentOn(allowed);
                continue;
            }
            assertEquals(first, made.at("value"), "the values, arriving as " + order);
            assertEquals(spent, spentOn(allowed), "and the states, arriving as " + order);
        }
        assertTrue(spent > 0, "something was built, or this is measuring nothing");
    }

    /**
     * And what went unread is written in the order the readings were handed over.
     *
     * <p>The work order puts the smallest first; this one has to stay the author's. Both at once,
     * because a fold ordered by content would put these two the other way round.
     */
    @Test
    void whatWentUnreadIsWrittenInTheOrderItWasRead() {
        // The large one and the small one, so that the work order is not the order they arrive in.
        AdmissibleValues<String> big = alsoUnread(matching("a{300}"), UnreadReason.FORM_NOT_READ);
        AdmissibleValues<String> small =
                alsoUnread(matching("x"), UnreadReason.RELATES_TWO_POSITIONS);

        assertEquals(List.of(UnreadReason.FORM_NOT_READ, UnreadReason.RELATES_TWO_POSITIONS),
                AdmissibleValues.metAll(List.of(big, small), AsACompilationAllows.forAdmittedValues())
                        .whyUnread("elsewhere"),
                "the large one was read first, so its reason is written first");
        assertEquals(List.of(UnreadReason.RELATES_TWO_POSITIONS, UnreadReason.FORM_NOT_READ),
                AdmissibleValues.metAll(List.of(small, big), AsACompilationAllows.forAdmittedValues())
                        .whyUnread("elsewhere"),
                "and the other way round when it was read second");
    }

    /** The same reading, with a rule about another position that it could not read. */
    private static AdmissibleValues<String> alsoUnread(AdmissibleValues<String> read,
                                                       UnreadReason why) {
        return read.meet(built(PlannedValues.unreadable(java.util.Set.of("elsewhere"), why)),
                AsACompilationAllows.forAdmittedValues());
    }

    /** How much of one position's allowance has gone, which is what the work order decides. */
    private static int spentOn(Allowance<String> allowed) {
        return PatternPlan.Budget.OF_ADMITTED_VALUES.mostBuilt()
                - allowed.left(Sameness.Block.of("value"));
    }
}
