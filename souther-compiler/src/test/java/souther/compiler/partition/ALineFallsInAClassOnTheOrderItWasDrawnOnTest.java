package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.Carrier;
import souther.compiler.inputs.Membership;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A line is a place on the order of the number the rules cut, and the class it falls in is asked
 * with that place.
 *
 * <p>The two orders a measure has are not the same one. What stands at {@code slot.at} is a time,
 * written on the order a time is written on; what {@code Time.minute} answers is a whole number, and
 * a line drawn by {@code >= 30} is at thirty on that second order. Asked with a value of the first —
 * the place turned back into something a row could hold — a class about the minute is handed a whole
 * number where it expects a time, reads no minute out of it, and says it holds nothing. Every class
 * says that, so the line falls in none of them and the condition narrows nothing.
 *
 * <p>Which is why the case below is a time and not an {@code Int}. Where the number is what stands at
 * the position the two orders coincide, the round trip through a value comes back to the same place,
 * and nothing about a lookup written either way can be told apart.
 */
class ALineFallsInAClassOnTheOrderItWasDrawnOnTest {

    private static final String TAKEN = """
            module example.taken

            data Yes
            data No
            data Answer = Yes | No

            data Slot = { at: Time }

            behavior gate : (slot: Slot) -> Answer
            let gate (slot) = {
                guard Time.minute(slot.at) >= 30 else No
                Yes
            }
            """;

    private static final String OWN = """
            module example.own

            data Yes
            data No
            data Answer = Yes | No

            data Slot = { n: Int }

            behavior gate : (slot: Slot) -> Answer
            let gate (slot) = {
                guard slot.n >= 30 else No
                Yes
            }
            """;

    private static final String NAMED = """
            module example.named

            data Yes
            data No
            data Answer = Yes | No

            data Level = Int
                invariant value == 1 || value == 2 || value == 3

            data Slot = { level: Level }

            behavior gate : (slot: Slot) -> Answer
            let gate (slot) = {
                guard slot.level.value >= 2 else No
                Yes
            }
            """;

    /** A model's one measure, with the orders the reading of its input came to for that number. */
    private record Measured(Axis axis, souther.compiler.inputs.TermOrders orders) {

        Cut line() {
            assertEquals(1, axis.cuts().size(), "the model draws the one line");
            return axis.cuts().get(0);
        }

        /** The line the rules drew at {@code place}, of however many they drew. */
        Cut lineAt(String place) {
            return axis.cuts().stream().filter(each -> each.key().equals(place)).findFirst()
                    .orElseThrow(() -> new AssertionError("no line at " + place + ", among "
                            + axis.cuts().stream().map(Cut::key).toList()));
        }

        Carrier carrier() {
            return orders.answered();
        }

        List<PartitionClass> holding(Cut line) {
            return axis.classes().stream()
                    .filter(each -> each.holdsTheNumberAt(line.at())).toList();
        }
    }

    private static Measured measured(String source, String id) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Partitions.Partitioning read = compilation.db()
                .ask(new Adequacy.Divided(module, "gate")).value();
        assertNotNull(read, "the model compiles and is measured");
        Axis axis = read.axes().stream().filter(each -> each.id().toString().equals(id))
                .findFirst().orElse(null);
        assertNotNull(axis, "the model is measured at " + id + ", among "
                + read.axes().stream().map(each -> each.id().toString()).toList());
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        souther.compiler.inputs.InputDomain domain = compilation.db()
                .ask(new Adequacy.Inputs(module)).value().get("gate");
        assertNotNull(domain, "the input of the behavior under test was read");
        return new Measured(axis, domain.quantities(rules).ordersOf(axis.term()));
    }

    /** The line the rules drew on a number taken of a location falls in one of that number's runs. */
    @Test
    void aLineOnANumberTakenOfALocationFallsInAClassOfThatNumber() {
        Measured minute = measured(TAKEN, "gate/Time.minute(slot.at)");

        List<PartitionClass> holding = minute.holding(minute.line());

        assertEquals(List.of("Time.minute(slot.at)/30 <= x <= 59"),
                holding.stream().map(PartitionClass::id).toList(),
                "the run the line opens, and only that one");
    }

    /**
     * And the same class says nothing about the value that place stands for, which is what asking
     * the other way comes to.
     *
     * <p>The two orders in one assertion: thirty on the order the minute is counted on is half past
     * midnight on the order the position is written on, and a class about minutes reads no minute out
     * of a whole number. Without this the case above would pass on a model whose two orders are one.
     */
    @Test
    void andSaysNothingAboutTheValueThatPlaceStandsFor() {
        Measured minute = measured(TAKEN, "gate/Time.minute(slot.at)");
        Cut line = minute.line();

        assertNotEquals(minute.orders().answered(), minute.orders().observed(),
                "the number is counted on an order of its own");
        assertTrue(minute.axis().classes().stream().allMatch(each ->
                        !(each.classifier().membershipOf(minute.carrier().valueOf(line.at()))
                                instanceof Membership.Match)),
                "no class of the minute holds the whole number thirty as a value of the position");
        assertFalse(minute.axis().classes().isEmpty(), "and there are classes to say it of");
    }

    /** Where what stands at the position is the number, the line falls in a class the same way. */
    @Test
    void aLineOnAPositionsOwnValueFallsInAClassOfIt() {
        Measured own = measured(OWN, "gate/slot.n");

        assertEquals(List.of("slot.n/30 <= x"),
                own.holding(own.line()).stream().map(PartitionClass::id).toList());
    }

    private static final String ORDERED = """
            module example.ordered

            data Prospecting
            data Qualified
            data Won
            data Stage = Prospecting | Qualified | Won

            data Bigger
            data Smaller
            data Size = Bigger | Smaller

            behavior gate : (s: Stage) -> Size
            let gate (s) = {
                guard s < Qualified else Bigger
                Smaller
            }
            """;

    /**
     * A case of an ordered enumeration holds the line drawn at it.
     *
     * <p>The classes here are the cases, which are finer than the two sides the guard makes; the
     * line is at the place {@code Qualified} has on the enumeration's order. A case's class is asked
     * at the place it was given when it was built, and a class that could not be asked would leave
     * the line in no class — where a reader turning the place back into a value had, by the way,
     * found it.
     */
    @Test
    void aCaseOfAnOrderedEnumerationHoldsTheLineDrawnAtIt() {
        Measured stage = measured(ORDERED, "gate/s");

        assertEquals(List.of("Qualified"),
                stage.holding(stage.line()).stream().map(PartitionClass::id).toList());
    }

    /**
     * A class of a value the rules named holds the line drawn at that value.
     *
     * <p>Such a class is told from a row by looking at the value, and it is asked here with a place.
     * What answers is where the value it holds sits on the position's order, written down when the
     * class was made — so a position whose rules name its values is narrowed by a comparison on it,
     * as it was while the line was turned back into a value to ask.
     *
     * <p>The guard's is one of the lines this model draws and not the only one: the invariant names
     * three values under a choice, so the rules stop the level at one and at three as well, and the
     * line asked about here is named rather than taken as whatever the model happened to draw.
     */
    @Test
    void aClassOfANamedValueHoldsTheLineDrawnAtThatValue() {
        Measured named = measured(NAMED, "gate/slot.level");

        assertEquals(List.of("2"),
                named.holding(named.lineAt("2")).stream().map(PartitionClass::id).toList(),
                "the line is at two, and the class holding two is the one that holds it");
    }
}
