package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.core.Core;
import souther.compiler.inputs.InputReads;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.numeric.LinearForm;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a walk took in on the way to a comparison holds of every row that gets there.
 *
 * <p>The inclusion {@link souther.compiler.inputs.SearchRegion} rests on, at the place it is
 * established. A region is narrowed by exactly these cuts, so a cut that does not hold of a row
 * that arrives is a region that excludes it — and a row for a border is then looked for outside the
 * set of rows that could ever meet it, which is the one direction a coverage measure may not move
 * in.
 *
 * <p>Held against the condition itself rather than against a region. What a domain does with a cut
 * is the domain's, and two accounts of one condition can leave one region identical; an answer read
 * off the region would hold as well for a walk that took in something the condition never said.
 *
 * <p>And every shape is answered for. A condition this reading has no words for and one it read and
 * could place no constraint from are both on the list as declines, so a comparison reached under
 * nothing at all is told from one reached past something nothing could carry — which reading a
 * report gets is the whole of what an author has to go on.
 */
class WhatAWalkTakesInHoldsOfEveryRowItLetsThroughTest {

    private static final String MODEL = """
            module example.walk

            data Pair = { x: Int, y: Int }

            behavior simple : (p: Pair) -> Bool
            let simple (p) = p.x > 0

            behavior both : (p: Pair) -> Bool
            let both (p) = p.x > 0 && p.y > 10

            behavior either : (p: Pair) -> Bool
            let either (p) = p.x > 0 || p.y > 10

            behavior affineSum : (p: Pair) -> Bool
            let affineSum (p) = p.x + 2 * p.y <= 7

            behavior withACall : (p: Pair) -> Bool
            let withACall (p) = p.x > 0 && Int.max(p.y, 3) > 10

            behavior product : (p: Pair) -> Bool
            let product (p) = p.x * p.y > 4

            behavior nested : (p: Pair) -> Bool
            let nested (p) = (p.x > 0 || p.y > 1) && p.y < 5
            """;

    /** The same conditions, as Java reads them. */
    private static final Map<String, BiPredicate<Integer, Integer>> MEANS = Map.of(
            "simple", (x, y) -> x > 0,
            "both", (x, y) -> x > 0 && y > 10,
            "either", (x, y) -> x > 0 || y > 10,
            "affineSum", (x, y) -> x + 2 * y <= 7,
            "withACall", (x, y) -> x > 0 && Math.max(y, 3) > 10,
            "product", (x, y) -> x * y > 4,
            "nested", (x, y) -> (x > 0 || y > 1) && y < 5);

    /**
     * What {@code behavior}'s body states, coming out {@code holding}.
     *
     * <p>A body that is one condition, so that what is asked here is the rule itself and not a walk
     * of a body that happens to reach it.
     */
    private static List<OnTheWay> stating(String behavior, boolean holding) {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Bodies.Elaborated checked = compilation.db().ask(new Bodies.Checked(module)).value();
        assertNotNull(checked, "the model under test compiles");
        Core body = checked.behaviorBodies().get(behavior);
        assertNotNull(body, () -> "the model under test writes " + behavior);
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        souther.compiler.inputs.InputDomain inputs =
                compilation.db().ask(new Adequacy.Inputs(module)).value().get(behavior);
        InputReads reads = InputReads.ofParameters(inputs.parameterReads(),
                checked.elementBindings().get(behavior));
        return ReachingCuts.stating(Condition.of(body, reads, rules.symbols(), rules.newtypes(),
                new ConditionNumbering(module, behavior)), inputs, holding, rules);
    }

    /** Whether {@code cut} holds where {@code x} and {@code y} stand at these values. */
    private static boolean holdsAt(ReachingCuts.Cut cut, int x, int y) {
        LinearForm<NumericTerm> form = cut.form();
        BigDecimal value = form.constant();
        for (Map.Entry<NumericTerm, BigDecimal> each : form.coefs().entrySet()) {
            String term = each.getKey().toString();
            assertTrue(term.endsWith("x") || term.endsWith("y"),
                    () -> "the cuts of this model are over its two positions: " + term);
            value = value.add(each.getValue()
                    .multiply(BigDecimal.valueOf(term.endsWith("x") ? x : y)));
        }
        int against = value.compareTo(BigDecimal.ZERO);
        return switch (cut.rel()) {
            case GE -> against >= 0;
            case GT -> against > 0;
            case LE -> against <= 0;
            case LT -> against < 0;
            case EQ -> against == 0;
            case NE -> against != 0;
        };
    }

    /**
     * Every cut taken in holds of every row that comes out that way.
     *
     * <p>Walked over the whole grid rather than sampled, so that a cut wrong by one is caught at the
     * value it is wrong at: the thresholds these conditions name are all inside it.
     */
    @Test
    void whatWasTakenInHoldsWhereverTheConditionComesOutThatWay() {
        for (String behavior : MEANS.keySet()) {
            for (boolean holding : List.of(true, false)) {
                List<ReachingCuts.Cut> cuts = stating(behavior, holding).stream()
                        .filter(each -> each instanceof OnTheWay.TakenIn)
                        .map(each -> ((OnTheWay.TakenIn) each).cut())
                        .toList();
                for (int x = -4; x <= 13; x++) {
                    for (int y = -4; y <= 13; y++) {
                        if (MEANS.get(behavior).test(x, y) != holding) {
                            continue;   // this row does not come here, so nothing is claimed of it
                        }
                        for (ReachingCuts.Cut cut : cuts) {
                            int atX = x;
                            int atY = y;
                            assertTrue(holdsAt(cut, x, y),
                                    () -> behavior + " coming out " + holding + " does not state "
                                            + cut + ", which fails at x=" + atX + " y=" + atY);
                        }
                    }
                }
            }
        }
    }

    /**
     * And every shape of condition is answered for.
     *
     * <p>An empty answer is the one thing this may not come back with. A comparison at the top of a
     * body has nothing on the way to it and a comparison past a condition nothing could read has
     * something on the way that is not represented — said with an empty list, the two are one
     * answer, and the second is the one that costs an author a search they cannot account for.
     */
    @Test
    void everyShapeOfConditionIsAnsweredFor() {
        for (String behavior : MEANS.keySet()) {
            for (boolean holding : List.of(true, false)) {
                assertFalse(stating(behavior, holding).isEmpty(),
                        () -> behavior + " coming out " + holding + " says nothing at all");
            }
        }
    }

    /**
     * The two ways round a fork's operators say one of two things, and neither is approximated.
     *
     * <p>{@code A && B} coming out false says one of them failed and names neither. Taking either
     * would narrow a region on something no row here satisfies, which is what the answer being a
     * decline rather than a cut is for — and it is filed at the whole condition, since neither
     * operand is what could not be carried.
     */
    @Test
    void anArmThatStatesOneOfTwoThingsIsDeclinedWhole() {
        assertEquals(List.of(new OnTheWay.Why.OneOfTwoThings()), whys("both", false));
        assertEquals(List.of(new OnTheWay.Why.OneOfTwoThings()), whys("either", true));
        assertEquals(List.of(), whys("both", true), "and both operands are taken in the other way");
        assertEquals(List.of(), whys("either", false));
    }

    /**
     * A comparison this reading could not turn into a cut says that, and says no more.
     *
     * <p>Not the answer the same comparison gets for drawing no line. That question is
     * {@link souther.compiler.check.UnreadComparison}'s and its answers are about boundaries: a
     * relation between two positions stops a line there and is carried here without trouble, and a
     * comparison of two constants comes back there as a form nothing reads when what is true of it
     * is that it constrains no position.
     */
    @Test
    void aComparisonItCouldNotTurnIntoACutSaysThatAndNoMore() {
        assertEquals(List.of(new OnTheWay.Why.ComparisonNotRepresentedAsACut()),
                whys("product", true));
        // The affine operand is taken in beside it: a conjunction coming out true says both, and
        // one of them being unreadable is no reason to lose the other.
        assertEquals(List.of(new OnTheWay.Why.ComparisonNotRepresentedAsACut()),
                whys("withACall", true));
        assertEquals(1, stating("withACall", true).stream()
                .filter(each -> each instanceof OnTheWay.TakenIn).count());
    }

    /** What the walk declined, and why, in the order it met them. */
    private static List<OnTheWay.Why> whys(String behavior, boolean holding) {
        return stating(behavior, holding).stream()
                .filter(each -> each instanceof OnTheWay.Declined)
                .map(each -> ((OnTheWay.Declined) each).why())
                .toList();
    }
}
