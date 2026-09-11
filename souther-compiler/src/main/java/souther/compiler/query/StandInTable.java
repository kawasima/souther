package souther.compiler.query;

import souther.compiler.partition.FixtureTemplate;
import souther.compiler.types.ValueName;

import java.util.List;

/**
 * What a block writes beside its rows so one dependency answers by what it was applied to.
 *
 * <p>A row answers a dependency for itself where one value serves every call it makes; where it
 * needs the answer to vary by the call, what answers is a table, and a table is written once for a
 * module. So this is of the block and not of a row: the rows that need one are merged into it, and
 * a row wanting an answer the table already gives another way is one the block cannot hold.
 *
 * <p>Settled where the rows a person is handed are settled, and never where they are printed. What
 * a block does with this is write it; which rows it holds and which rows it cost is the offering's
 * answer, so a renderer cannot come to a different one.
 *
 * @param dependency which behavior the table stands in for
 * @param entries    what it answers, one entry per call it is written for, in the order they were
 *                   composed
 */
public record StandInTable(ValueName.Behavior dependency, List<Entry> entries) {

    public StandInTable {
        if (dependency == null || entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("a table stands some behavior in, and answers");
        }
        entries = List.copyOf(entries);
    }

    /**
     * One entry of it: the call it answers for, and what it answers.
     *
     * @param appliedTo what the call was applied to, as the table writes it
     * @param answers   the value
     */
    public record Entry(List<FixtureTemplate> appliedTo, FixtureTemplate answers) {

        public Entry {
            if (appliedTo == null || answers == null) {
                throw new IllegalArgumentException("an entry of a table answers some call");
            }
            appliedTo = List.copyOf(appliedTo);
        }

        /** What the call is written as, which is what tells one entry of a table from another. */
        public List<String> writtenAs() {
            return appliedTo.stream().map(FixtureTemplate::text).toList();
        }
    }
}
