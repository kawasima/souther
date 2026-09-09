package souther.bench;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.classfile.ClassModel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every whole number the composing stage is written to stop at is one of this compiler's budgets, or
 * is said here not to be.
 *
 * <p>The second sheet, beside the one that counts where a budget is read. That one holds the
 * registered figures to being carried; this one holds the classes to registering a figure at all —
 * and it is the sheet the population needed. Twice while #1232 was written the budgets were taken
 * from the constants that looked like budgets, and twice the list was short. A name is not what
 * makes a budget.
 *
 * <p><b>Told by how the number arrives and not by what it is called.</b> A figure that comes from
 * {@link souther.compiler.partition.CompositionBudget} is worked out when the class is initialised
 * and has no constant value in the class file; a number written into the source has one. So a
 * constant that is a budget is one nobody wrote down twice, and this is what says so mechanically —
 * a rule about spelling would be passed by the next figure spelled differently, which is the failure
 * that made this necessary.
 *
 * <p>What it still does not see is a figure derived from something other than a number, which is
 * anything worked out rather than written down. Every figure there is is written down as one of
 * this compiler's budgets, so what such a number would be is a bound somebody put beside a walk
 * without registering it — and that one is caught by the other sheet, which sees the enum being
 * read where the walk stops. Neither is enough alone.
 */
class EveryFigureTheComposingStageStopsAtIsABudgetOrIsSaidNotToBeTest {

    /** Where a row for a coverage item is composed, which is what a budget of this kind bounds. */
    private static final String COMPOSING = "souther.compiler.partition.";

    /**
     * The whole numbers written into these classes, and why each is not a figure a search stops at.
     *
     * <p>One entry per constant, with what it is instead. A number added is a question — is this
     * something this compiler stops at, and does what stops there carry it — and the answer belongs
     * beside the code that stops.
     */
    private static final Map<String, String> NOT_A_BUDGET = new LinkedHashMap<>(Map.ofEntries(
            // Numbers a value is written at, which are what a date or a time looks like and not how
            // far this looks for one.
            Map.entry("souther.compiler.partition.TermRealizations.A_YEAR",
                    "the year a time of day is written in"),
            Map.entry("souther.compiler.partition.TermRealizations.A_LONGEST_MONTH",
                    "the month a day of the month is offered in"),
            Map.entry("souther.compiler.partition.TermRealizations.FIRST_OF_THE_MONTH",
                    "the day a date's other parts are offered at"),

            // A place in a list rather than an amount of anything.
            Map.entry("souther.compiler.partition.Generator.NOT_HERE",
                    "no position, which is what an index that found none is"),

            // How much of a number is written down, which bounds a value's shape and not a search:
            // any length is sound while the rounding goes outward, so nothing is left unlooked at.
            Map.entry("souther.compiler.partition.LevelRealizer.DIGITS_A_DERIVED_END_KEEPS",
                    "how far a derived end is written out, which leaves nothing untried"),

            // Budgets of walks that are not the composing of a row at a coverage item. Each bounds
            // a search of its own, and what reaching one costs is that search's to carry — measured
            // for #1232 and left where they are, which is why they are named rather than omitted.
            Map.entry("souther.compiler.partition.Generator.MOST_REPAIRS",
                    "the walk over which classes to settle a row at, not the row at an item"),
            Map.entry("souther.compiler.partition.Generator.MOST_INTERPRETATIONS",
                    "how many readings of a row's values one run tries"),
            Map.entry("souther.compiler.partition.Generator.MOST_RUNS_PER_INTERPRETATION",
                    "how many runs one reading of a row's values is given"),
            Map.entry("souther.compiler.partition.StandingAtAPoint.MOST_READINGS",
                    "the reading of a row that was built, which is where an observation stops")));

    /**
     * A number written into a composing class is a budget's, or is one of the above.
     *
     * <p>Both ways round, as the other sheet is. A number nobody has said anything about is one
     * somebody has to decide about; an entry naming a constant that has gone is a rule about code
     * that is not there.
     */
    @Test
    void aFigureIsThisCompilersBudgetOrIsSaidToBeSomethingElse() throws IOException {
        List<String> written = new ArrayList<>();
        for (ClassModel model : Reactor.classes()) {
            String from = model.thisClass().asInternalName().replace('/', '.').replace('$', '.');
            if (!from.startsWith(COMPOSING) || from.equals(COMPOSING + "CompositionBudget")) {
                continue;
            }
            for (var field : model.fields()) {
                if (!field.fieldTypeSymbol().descriptorString().equals("I")
                        || field.findAttribute(java.lang.classfile.Attributes.constantValue())
                                .isEmpty()) {
                    continue;   // not a whole number, or worked out rather than written down
                }
                written.add(from + "." + field.fieldName().stringValue());
            }
        }

        List<String> unsaid = new ArrayList<>(written);
        unsaid.removeAll(NOT_A_BUDGET.keySet());
        List<String> gone = new ArrayList<>(NOT_A_BUDGET.keySet());
        gone.removeAll(written);

        assertEquals(List.of(), NOT_A_BUDGET.isEmpty() || written.isEmpty()
                        ? List.of("nothing was read, so this asserts nothing") : List.of(),
                "the classes were read and hold whole numbers, or the census found none and"
                        + " everything below passes by looking at nothing");
        assertEquals(List.of(), unsaid,
                "a whole number written into a composing class that nothing says anything about."
                        + " Either a search stops at it — and then it is a CompositionBudget, whose"
                        + " figure the class reads rather than writing its own — or it is something"
                        + " else and the list above says what");
        assertEquals(List.of(), gone, "a rule about a constant that is no longer written");
    }
}
