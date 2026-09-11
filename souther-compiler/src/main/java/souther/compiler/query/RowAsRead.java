package souther.compiler.query;

import souther.compiler.check.Sig;
import souther.compiler.execute.BoundaryValues;
import souther.compiler.observe.ObservedValue;
import souther.compiler.partition.RowToRun;
import souther.compiler.partition.FixtureTemplate;
import souther.compiler.partition.Generator;
import souther.compiler.partition.ObservedInputs;

import java.util.ArrayList;
import java.util.List;

/**
 * A row that was written down, read as the two things every question here is put to.
 *
 * <p>The values are built through the module's own decoders and the account is what running it
 * recorded. Both of them are how a row a person is being handed is put to the same walks a row in
 * the file goes through — and there is one of these because there is one way to read a row: a
 * second reading built where a second caller needed it would be free to answer differently about
 * one row.
 *
 * <p>Made once per row rather than once per question. A row read again for the next item would be
 * run again for it, which is the same row doing the same thing as many times as somebody has
 * questions.
 *
 * @param values      what its inputs build to, or null where they do not all build
 * @param whyNotRead  why they did not, where they did not, and null where they did
 * @param watched     what running it recorded, which a row whose values would not build still has:
 *                    the two are found out separately and one failing is not the other failing
 */
public record RowAsRead(List<ObservedValue> values, Settlement.Reason whyNotRead,
                        Generator.Watched watched) {

    public RowAsRead {
        values = values == null ? null : List.copyOf(values);
    }

    /** Nothing here could read a row of this behavior at all. */
    public static RowAsRead nothingRead() {
        return new RowAsRead(null, Settlement.Reason.NOTHING_BUILT_THE_VALUES,
                new Generator.Watched.NoAccount());
    }

    /**
     * {@code row} built and run.
     *
     * <p>The account of the run is taken whatever the values came to. A row the model would not
     * take is still a row something may have watched, and the two answers are about different
     * things.
     */
    public static RowAsRead of(Sig sig, BoundaryValues building, Generator.Trial trial,
                               RowToRun row) {
        List<FixtureTemplate> inputs = row.inputs();
        Generator.Watched watched = trial.run(row);
        if (building == null || sig == null) {
            return new RowAsRead(null, Settlement.Reason.NOTHING_BUILT_THE_VALUES, watched);
        }
        List<ObservedValue> values = new ArrayList<>();
        for (int at = 0; at < inputs.size(); at++) {
            if (at >= sig.ins().size()) {
                return new RowAsRead(null, Settlement.Reason.NOTHING_BUILT_THE_VALUES, watched);
            }
            switch (building.build(sig.ins().get(at), inputs.get(at).value())) {
                case BoundaryValues.Built.Value(var observed) -> values.add(observed);
                // The model would not take the value the row names. Told apart from having nothing
                // to build against: this found something out about the row, and that found nothing
                // out at all.
                case BoundaryValues.Built.Refused _ -> {
                    return new RowAsRead(null, Settlement.Reason.THE_VALUES_WERE_REFUSED, watched);
                }
            }
        }
        return new RowAsRead(values, null, watched);
    }

    /** The row as the walks take it, or null where its values are not here to be walked. */
    public ObservedInputs asInputs() {
        return values == null ? null : new ObservedInputs(values, watched);
    }
}
