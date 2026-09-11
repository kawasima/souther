package souther.compiler.query;

import souther.compiler.partition.FixtureTemplate;
import souther.compiler.partition.StoodInAnswer;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.List;

/**
 * What one row stands a dependency in with, where one answer does not serve every call it makes.
 *
 * <p>The whole of what that dependency does while the row runs: an entry per call the row was
 * composed against, and what it answers a call none of them names. Whole, because it is the
 * environment the row is held to — the run that certified the row went against this, and the block
 * publishes this, and a row certified against one table and published beside another is a row
 * nobody measured.
 *
 * <p>Which is why a module publishes one of these per dependency rather than a union of several.
 * Two rows wanting two tables are two environments, and merging them makes a third neither row was
 * run against — differing at exactly the calls a fallback is here for. So a row whose table is not
 * the one already published is a row the block cannot hold.
 *
 * <p><b>The fallback is decided here and nowhere else.</b> What a run answers a call it did not
 * foresee and what a block writes a {@code _} row as are the same answer; decided twice, a row runs
 * against one of them and is published beside the other.
 *
 * @param dependency which behavior the table stands in for
 * @param entries    what it answers, one per call the row was composed against, in the order they
 *                   were composed
 */
public record StandInTable(ValueName.Behavior dependency, List<Entry> entries) {

    public StandInTable {
        if (dependency == null || entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("a table stands some behavior in, and answers");
        }
        entries = List.copyOf(entries);
    }

    /**
     * The table {@code stood} comes to, or null where one answer serves every call.
     *
     * <p>The one place a row's answers become a table. Read again anywhere else, what a run goes
     * against and what a block writes would be two readings of one row's answers.
     */
    public static StandInTable of(ValueName.Behavior dependency, List<StoodInAnswer> stood) {
        List<Entry> entries = new ArrayList<>();
        for (StoodInAnswer each : stood) {
            if (each.asking() instanceof StoodInAnswer.Asking.OfOne(var _, var appliedTo)) {
                entries.add(new Entry(appliedTo, each.value()));
            }
        }
        return entries.isEmpty() ? null : new StandInTable(dependency, entries);
    }

    /**
     * What it answers a call none of its entries names.
     *
     * <p>The first entry's answer. Which one it is matters less than that it is one thing: a run
     * meets calls this reading did not foresee — that is what the entries being about the calls it
     * did foresee means — and a row that stopped at one would say nothing about where it went.
     */
    public FixtureTemplate fallback() {
        return entries.getFirst().answers();
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
