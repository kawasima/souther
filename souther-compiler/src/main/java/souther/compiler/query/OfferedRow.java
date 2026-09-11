package souther.compiler.query;

import souther.compiler.partition.FixtureTemplate;
import souther.compiler.partition.Generator;

import java.util.ArrayList;
import java.util.List;

/**
 * One row as it will be written, and what it may be named after.
 *
 * <p>A candidate is composed once per thing it is owed for and the positions that thing does not
 * name hold whatever the row has to hold, so two of them can come out as one row. What is
 * offered is the row: a reader is handed one piece of work rather than the same values twice.
 *
 * <p><b>Every purpose a cell composed it for, and not the first.</b> Two purposes converging on
 * one row is a fact about this run, and keeping one of them leaves a name that says the row is
 * about one thing while it answers two.
 *
 * <p><b>And nothing from a line.</b> A cell can name a row — a candidate's values follow from
 * the classes it was composed for, so the cell a row is named by is the row's own and is there
 * whatever else this run offers. A line cannot: lines coincide, each probe filling the positions
 * its own edge does not name from the bottom of their domains, so two minimum edges compose one
 * row and which of them is offered is exactly what changes when something else is written. A row
 * named for whichever line happened to be offered would be renamed by an edit that did not touch
 * it. So a row composed only at lines is offered with nothing to be named after, which the
 * language allows — an unnamed row cannot be addressed from outside, and that is the state of a
 * row nobody has named yet.
 *
 * @param key      what tells this row from the others a person is handed
 * @param inputs   the values, as the search composed them. One row's worth: rows that came out
 *                 as one piece of work are written the same way, so which of them these came
 *                 from is not a difference anybody can read
 * <p>A rule of the body's decision is the row's own the same way an arm is: the search that
 * composed the row ran it and saw it take the way, so nothing an edit does elsewhere moves what it
 * is for. What it has no word for is a name — a rule is told from the rules beside it by the
 * conditions it turns on, and those are said under the row rather than in it.
 *
 * @param namedFor the classes, arms and rules this row was composed for, in the order they were
 *                 taken
 */
public record OfferedRow(RowKey key, List<FixtureTemplate> inputs,
                         List<Generator.Purpose> namedFor) {

    public OfferedRow {
        inputs = List.copyOf(inputs);
        namedFor = List.copyOf(namedFor);
        for (Generator.Purpose purpose : namedFor) {
            if (!(purpose instanceof Generator.Purpose.ForAClass
                    || purpose instanceof Generator.Purpose.ForAnArm
                    || purpose instanceof Generator.Purpose.ForADecisionRule)) {
                throw new IllegalArgumentException(
                        "a row is composed for a class, an arm or a rule, and never for a line: "
                                + purpose);
            }
        }
    }

    /** The row with {@code more} added to what it may be named after. */
    OfferedRow and(List<Generator.Purpose> more) {
        if (more.isEmpty()) {
            return this;
        }
        List<Generator.Purpose> both = new ArrayList<>(namedFor);
        both.addAll(more);
        return new OfferedRow(key, inputs, both);
    }
}
