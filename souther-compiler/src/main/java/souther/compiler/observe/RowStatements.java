package souther.compiler.observe;

import souther.compiler.diag.SourcePos;
import souther.compiler.types.Type;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.List;

/**
 * What a row states, made once from what was read of it.
 *
 * <p>The one place a statement is made, so that what {@link RowStatement.Stated} means is decided
 * once. A value that is not there in full, and one larger than what is kept, are both values a
 * reader cannot be given — the first because nothing here has it, the second because carrying it
 * would carry a value nobody wrote — and a row holding one of them hands over nothing and says
 * which value and why.
 *
 * <p>Held to {@link Limits#DEFAULT} and not to limits a caller chooses. What a row's inputs are
 * observed under is that one, so everything a row hands over is held to one size; and a statement
 * whose meaning came from an argument would mean one thing where it was made and another where it
 * was read, which is what a reader of a statement would then have to ask about before it could do
 * anything with the values. How much is kept is a thing to change, in the one place it is written;
 * whose choice it is, is not.
 *
 * <p>What is read here is what the row wrote, and nothing else reaches it. Whether this compile had
 * anything to apply the behavior with is not part of what the row states: a row of a behavior no
 * implementation was found for states the same values as one of a behavior that ran, and a snapshot
 * whose rows said otherwise would be publishing the compile it was taken from. Kept that way by
 * where this stands rather than by a rule to keep: this package is not one the run that applies a
 * behavior can be named from, so a statement made here cannot ask what applied anything.
 */
public final class RowStatements {

    private RowStatements() {}

    /**
     * What was read of one dependency's stand-in.
     *
     * <p>Between the reading that builds a stand-in's values and the statement made of them, and
     * nothing further. The values a stand-in states are built where a fixture is read, and whether
     * they are values a row can hand over is decided here — so the two are one step apart, and a
     * caller that had to decide the second for itself would be a second place that knew what a row
     * may carry.
     */
    public sealed interface StandInRead {

        /** It states values that can be handed over, and this is what it states. */
        record Available(StoodIn stoodIn) implements StandInRead {

            public Available {
                if (stoodIn == null) {
                    throw new IllegalArgumentException("a stand-in that was read is what it states");
                }
            }
        }

        /** It could not be handed over, and this is where a reader is sent and why. */
        record Unavailable(ValueName.Behavior dependency, SourcePos at,
                           RowStatement.StandInUnavailable.Why why) implements StandInRead {

            public Unavailable {
                if (dependency == null || at == null || why == null) {
                    throw new IllegalArgumentException("a stand-in that could not be handed over"
                            + " names the dependency, where to look, and why");
                }
            }
        }

        /**
         * Nothing was read that states what {@code dependency} answers, quoted at {@code at}.
         *
         * <p>Beside {@link #of} and not among its answers: that reads what a stand-in states, and
         * this is a caller with nothing to read it. Which of the two a reading is, is the reader's
         * to say — a factory answering it from an empty table would make a stand-in that lists
         * nothing and a dependency nothing stands in for one thing.
         */
        static StandInRead nothingRead(ValueName.Behavior dependency, SourcePos at) {
            return new Unavailable(dependency, at,
                    new RowStatement.StandInUnavailable.Why.NothingWasRead());
        }

        /**
         * One value of a stand-in as it was read: what it is, and where it is written.
         *
         * <p>Kept until the stand-in is known to be one a row can hand over, and not after. What a
         * reader is given is the values; where each of them was written is what a reader is sent to
         * when one of them turns out not to be carryable, and there is nothing to send anyone to
         * once they all are.
         */
        record Written(ObservedValue value, SourcePos at) {

            public Written {
                if (value == null || at == null) {
                    throw new IllegalArgumentException("a value a stand-in states is written"
                            + " somewhere");
                }
            }
        }

        /** One entry as it was read: the arguments it answers for, its answer, and where the entry
         *  itself is written. */
        record EntryRead(List<Written> arguments, Written answer, SourcePos at) {

            public EntryRead {
                arguments = List.copyOf(arguments);
                if (answer == null || at == null) {
                    throw new IllegalArgumentException("an entry answers what it states, and is"
                            + " written somewhere");
                }
            }
        }

        /**
         * What a stand-in stating these was read as.
         *
         * <p>Every value it states, in the order it states them: each entry as it is written, its
         * arguments before the answer it states for them, and then what it answers where no entry
         * states what it is asked. The first that could not be carried is what comes back, so what
         * is said is what stopped the stand-in being handed over rather than everything about it.
         *
         * @param at    where what stands in is written, which is what the stand-in is quoted at;
         *              where a value of it could not be carried, the place said is that value's own
         * @param takes what the dependency declares it takes, for the entries to be held to. Read
         *              and not carried: what a reader compares an argument at is the same
         *              declaration, bound where a reading of the declarations already is, and a
         *              stand-in carrying it would be that declaration written down twice
         */
        static StandInRead of(ValueName.Behavior dependency, SourcePos at, List<Type> takes,
                              List<EntryRead> entries, StoodIn.Otherwise otherwise) {
            if (dependency == null || at == null || otherwise == null) {
                throw new IllegalArgumentException("a stand-in is one dependency's, written"
                        + " somewhere, and answers what it is not written to");
            }
            List<StoodIn.Entry> carried = new ArrayList<>();
            for (EntryRead entry : entries) {
                if (entry.arguments().size() != takes.size()) {
                    // An entry states a call, and a call to this dependency is as many values as it
                    // declares. One stating another number is an entry nothing can ever be matched
                    // against — carried, it would be a stand-in with an answer no argument reaches
                    // and a reader with no way to tell that from one it simply was not asked.
                    throw new IllegalArgumentException("`" + dependency + "` takes " + takes.size()
                            + " and an entry states " + entry.arguments().size());
                }
                List<ObservedValue> arguments = new ArrayList<>();
                for (Written argument : entry.arguments()) {
                    Incompleteness.Code stopped = Limits.DEFAULT.stoppedBy(argument.value());
                    if (stopped != null) {
                        return unavailable(dependency, argument.at(), stopped);
                    }
                    arguments.add(argument.value());
                }
                Incompleteness.Code stopped = Limits.DEFAULT.stoppedBy(entry.answer().value());
                if (stopped != null) {
                    return unavailable(dependency, entry.answer().at(), stopped);
                }
                carried.add(new StoodIn.Entry(arguments, entry.answer().value(), entry.at()));
            }
            if (otherwise instanceof StoodIn.Otherwise.Answer(ObservedValue value, SourcePos where)) {
                Incompleteness.Code stopped = Limits.DEFAULT.stoppedBy(value);
                if (stopped != null) {
                    return unavailable(dependency, where, stopped);
                }
            }
            return new Available(StoodIn.of(dependency, at, carried, otherwise));
        }

        private static StandInRead unavailable(ValueName.Behavior dependency, SourcePos at,
                                               Incompleteness.Code stopped) {
            return new Unavailable(dependency, at,
                    new RowStatement.StandInUnavailable.Why.AValueOfIt(stopped));
        }
    }

    /**
     * What the row states, from what was read of it.
     *
     * <p>In one order, so that what a reader is told is settled rather than depending on which of
     * two things was noticed first. What the behavior needs stood in for comes first, because a row
     * of a behavior that takes something injected does not hand over a runnable thing without it —
     * a reader given the values alone would apply the behavior with nothing to answer the dependency
     * with. Then the inputs in the order they are written, then the expectation.
     *
     * @param standIns what was read of each dependency's stand-in, in the order the behavior
     *                 requires them; empty for a behavior that depends on nothing
     */
    public static RowStatement read(List<StandInRead> standIns, List<ObservedValue> inputs,
                                    Expectation expects) {
        if (expects == null) {
            throw new IllegalArgumentException("a row states something of the answer");
        }
        List<StoodIn> stood = new ArrayList<>();
        for (StandInRead standIn : standIns) {
            switch (standIn) {
                case StandInRead.Unavailable(ValueName.Behavior dependency, SourcePos at,
                                             RowStatement.StandInUnavailable.Why why) -> {
                    return new RowStatement.StandInUnavailable(dependency, at, why);
                }
                case StandInRead.Available(StoodIn stoodIn) -> stood.add(stoodIn);
            }
        }
        for (int i = 0; i < inputs.size(); i++) {
            Incompleteness.Code stopped = Limits.DEFAULT.stoppedBy(inputs.get(i));
            if (stopped != null) {
                return new RowStatement.Incomplete(new RowStatement.Side.AnInput(i), stopped);
            }
        }
        // What the row states of the answer is read whole, so that a comparison is made against what
        // was written; whether it may be carried is asked of the same limits an input is held to, so
        // that one row's halves are held to one size.
        Incompleteness.Code stopped = switch (expects) {
            case Expectation.TheValue(Asserted value) -> Limits.DEFAULT.stoppedBy(value);
            // A case is a name. Nothing about it can be too large or fail to be read.
            case Expectation.TheCase _ -> null;
            // An answer that is owed is no value either. What such a row is short of is the
            // author's half, and a limit on what may be carried has nothing to say about it.
            case Expectation.Owed _ -> null;
        };
        return stopped != null
                ? new RowStatement.Incomplete(new RowStatement.Side.TheExpectation(), stopped)
                : RowStatement.Stated.of(stood, inputs, expects);
    }
}
