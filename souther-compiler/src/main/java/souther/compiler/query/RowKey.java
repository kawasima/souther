package souther.compiler.query;

import souther.compiler.partition.FixtureTemplate;
import souther.compiler.partition.Generator;
import souther.compiler.partition.StoodInAnswer;

import java.util.List;

/**
 * What tells one piece of work from another among the rows a run offers.
 *
 * <p>The row as a person is handed it: the behavior it is written under and the values it is written
 * with, in the form they will be written in. A candidate is composed once per thing it is owed for
 * and the positions that thing does not name hold whatever the row has to hold, so two of them come
 * out as one piece of work — and what a reader would otherwise be pasting twice is one row here.
 *
 * <p><b>What is written, and not what the values are.</b> Two rows of one behavior that mean the
 * same thing and are spelt differently stay two rows, each answering what it was composed for. That
 * is the direction to be wrong in: a person reads the row, and an identity over the values would
 * need something none of them has — a fixture carries the position it was parsed at and the path it
 * was constructed through, neither of which is what a reader is looking at.
 *
 * <p>Under one behavior, because a row is written in one behavior's terms. The same values written
 * for two behaviors are two rows in two blocks, and a key that left the behavior out would join them
 * into one piece of work nobody can write.
 *
 * <p><b>And what it stands the dependencies in with.</b> A row of a behavior that decides on what
 * one answers is written with the answer beside the values, and two rows that write one set of
 * values under two answers are two pieces of work that go two different ways. Left out, the second
 * would be joined onto the first and a person would be handed one row said to answer both.
 *
 * @param behavior the behavior the row is written under
 * @param written  one value per parameter, as the source an author is handed
 * @param stoodIn  what each asking of a dependency is answered with, as the source says it
 */
public record RowKey(String behavior, List<String> written, List<String> stoodIn) {

    public RowKey {
        written = List.copyOf(written);
        stoodIn = List.copyOf(stoodIn);
        if (behavior == null) {
            throw new IllegalArgumentException("a row is written under some behavior");
        }
    }

    /** The key of one composed row, under the behavior whose block it belongs in. */
    public static RowKey of(String behavior, Generator.GeneratedRow row) {
        return new RowKey(behavior, row.inputs().stream().map(FixtureTemplate::text).toList(),
                row.answers().stream().map(RowKey::spelled).toList());
    }

    /**
     * One answer a row states, as what tells it from another.
     *
     * <p>What it answers <em>for</em> as well as what it answers. Two rows may state one set of
     * values for one dependency at two different calls — a table keyed on one pair of calls and a
     * table keyed on another — and those are two rows with two environments. Spelled by the value
     * alone they are one key, and the second is folded into the first before anything reads the
     * table it needed.
     *
     * <p>The dependency under the module that declares it, because two modules may declare
     * behaviors of one name and a key that left the module off would join two rows standing two
     * dependencies in.
     */
    private static String spelled(StoodInAnswer stood) {
        String dependency = stood.dependency().module() + "." + stood.dependency().name();
        return switch (stood.asking()) {
            case StoodInAnswer.Asking.ForEveryCall _ ->
                    dependency + " = " + stood.value().text();
            case StoodInAnswer.Asking.OfOne one ->
                    dependency + "(" + String.join(", ", one.writtenAs()) + ") = "
                            + stood.value().text();
        };
    }

    /** The values as one line, which is how a row reads where it is written. */
    public String inputs() {
        return String.join(", ", written);
    }
}
