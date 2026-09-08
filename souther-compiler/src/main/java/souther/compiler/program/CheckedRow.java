package souther.compiler.program;

import souther.compiler.diag.SourcePos;
import souther.compiler.observe.Comparisons;
import souther.compiler.observe.Expectation;
import souther.compiler.observe.ObservedValue;
import souther.compiler.observe.Position;
import souther.compiler.observe.RowIdentity;
import souther.compiler.observe.RowStatement;
import souther.compiler.observe.StoodIn;
import souther.compiler.observe.ValueTypes;
import souther.compiler.observe.Verdict;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One {@code example} row of a behavior, for an output that lives outside this compiler.
 *
 * <p>A behavior's rows are what the language says it answers, and running them is not a test of the
 * compiler: a program whose rows do not hold is not accepted. So an output holding one of these
 * holds an obligation the language already checked as far as it can be checked without something to
 * apply the behavior — and what it can do with it is apply its own emission and ask whether the row
 * holds.
 *
 * <p>Asking is {@link SelfContained#holds}, or {@link WithStandIns#holds} for a row that needed
 * something stood in for, and it is asked of the language rather than answered by the reader. Whether an answer is the value a row states is a decision the language makes — a
 * written {@code Set.fromList([1])} at a {@code List} is not the sequence a list is, and two values
 * differ when their types differ as much as when their contents do. An output free to answer it for
 * itself would be a second reading of what a row means, and two outputs of one program would then
 * agree about it only as far as whoever wrote the second one agreed with the first.
 */
public final class CheckedRow {

    private final RowIdentity identity;
    private final SourcePos at;
    private final Statement statement;

    CheckedRow(RowIdentity identity, SourcePos at, Statement statement) {
        if (identity == null || at == null || statement == null) {
            throw new IllegalArgumentException("a row is what it names itself, where it is written,"
                    + " and what it states");
        }
        this.identity = identity;
        this.at = at;
        this.statement = statement;
    }

    /**
     * What the row names itself.
     *
     * <p>A name where it was written with one, and which of its behavior's rows it is where it was
     * not. What a report writes and what a generated test is named after; not a key, since a row
     * written without a name answers to nothing outside its file.
     */
    public RowIdentity identity() {
        return identity;
    }

    /** Where the row is written, which says the source as well as the line: a behavior exampled in
     *  its module and in an attached file has rows in both. */
    public SourcePos at() {
        return at;
    }

    /** What the row states, and whether an answer can be held against it. */
    public Statement statement() {
        return statement;
    }

    /**
     * What a row states, as a reader of a checked program may act on it.
     *
     * <p>Four arms, because there are four things a reader can do. A row of a behavior that
     * depends on nothing can be applied to the emission and asked whether the answer keeps it; a row
     * of one that takes something injected can too, once what the row states the dependency answers
     * is behind the import; a row whose answer is owed hands over its values and states nothing for
     * an answer to keep, so there is a row to read and no question to ask of it; a row that hands
     * over no values says why, and there is nothing to ask either. Said as arms rather than as one
     * arm answering for all of them, so that a reader written before a row could need a stand-in
     * cannot be handed one — a row it would apply with nothing behind its imports, reporting what a
     * run that cannot happen answered. The type is what stops it, rather than a rule it has to have
     * read.
     *
     * <p>And nothing here answers with what a row states, which each arm answers for itself. Asked
     * of all at once, the answer would be the whole of what an evaluation can come away with — a
     * compile that has not finished with a row among it — and a state no output can be handed would
     * be one every output has to consider, one method away from the arms that leave it out.
     */
    public sealed interface Statement {}

    /**
     * A row an output can put to its own emission as it stands: the inputs it hands over, and what
     * it states of the answer.
     *
     * <p>What it carries to answer {@link #holds} is the question and not an answerer: what this
     * program's declarations say stands inside a value, and where this behavior's answer stands.
     * It holds no program, no module and no declaration of its own — a row is a row of the program
     * it was read from, and it is that program's reading of the declarations that it asks.
     */
    public static final class SelfContained implements Statement {

        private final Asking asking;

        SelfContained(RowStatement.Stated stated, ValueTypes types, Position answers) {
            this.asking = new Asking(stated, types, answers);
            if (!stated.standIns().isEmpty()) {
                // What the behavior takes injected is the rest of what makes the row runnable, so a
                // row stating one is not a row an output applies to its emission and nothing else.
                throw new IllegalArgumentException("a row that needs something stood in for is not"
                        + " one an output can run on its own");
            }
        }

        /** The values it hands over and what it states of the answer. */
        public RowStatement.Stated states() {
            return asking.stated();
        }

        /** Whether {@code answered} is what this row states the behavior answers. */
        public Verdict holds(ObservedValue answered) {
            return asking.holds(answered);
        }

        @Override
        public String toString() {
            return asking.toString();
        }
    }

    /**
     * A row an output can put to its emission once it has answered what the behavior depends on.
     *
     * <p>The same row as {@link SelfContained} with one thing more, and an arm of its own all the
     * same. What an output does with this one it cannot do with the values alone: the behavior it
     * emits imports what it depends on, and running the row means putting what {@link #standsIn}
     * answers behind each import. A reader that walked past that would apply the behavior against
     * whatever its own imports answer — a different row, with this row's inputs.
     */
    public static final class WithStandIns implements Statement {

        private final Asking asking;
        private final List<StandsIn> standIns;

        WithStandIns(RowStatement.Stated stated, ValueTypes types, Position answers,
                     Map<ValueName.Behavior, List<Position>> arguments) {
            this.asking = new Asking(stated, types, answers);
            if (stated.standIns().isEmpty()) {
                throw new IllegalArgumentException("a row with nothing stood in for is one an"
                        + " output can run on its own");
            }
            // One per stand-in the row states, made here rather than handed in. What the row states
            // of its stand-ins and what a reader asks them are one fact, and taking the second as a
            // list would let a row answer one thing about a dependency through `states` and another
            // through `standsIn`. What comes from outside is where each dependency's arguments
            // stand, which is what its declaration says and not what the row states.
            List<StandsIn> standIns = new ArrayList<>();
            for (StoodIn stoodIn : stated.standIns()) {
                List<Position> stands = arguments.get(stoodIn.dependency());
                if (stands == null) {
                    throw new IllegalArgumentException("nothing says where the arguments of `"
                            + stoodIn.dependency() + "` stand, which is what its stand-in is asked"
                            + " at");
                }
                standIns.add(new StandsIn(stoodIn, types, stands));
            }
            this.standIns = List.copyOf(standIns);
        }

        /** The values it hands over and what it states of the answer. */
        public RowStatement.Stated states() {
            return asking.stated();
        }

        /**
         * What answers each of the behavior's dependencies, in the order it requires them.
         *
         * <p>All of them: a behavior reaches every one of them, and a row run with some of them
         * answered is a row that stopped at the first import nothing was behind.
         */
        public List<StandsIn> standsIn() {
            return standIns;
        }

        /** Whether {@code answered} is what this row states the behavior answers. */
        public Verdict holds(ObservedValue answered) {
            return asking.holds(answered);
        }

        @Override
        public String toString() {
            return asking.toString();
        }
    }

    /**
     * What holding a row to an answer takes, for the arms that can be asked.
     *
     * <p>One of these and not one for each arm. Whether an answer is what a row states is one
     * question however the row is run, and two arms answering it apart would be two readings of one
     * row — which is the thing {@link SelfContained#holds} exists to prevent a reader from being.
     */
    private record Asking(RowStatement.Stated stated, Expectation.Asserts asserts, ValueTypes types,
                          Position answers) {

        private Asking(RowStatement.Stated stated, ValueTypes types, Position answers) {
            this(stated, asserted(stated), types, answers);
        }

        private Asking {
            if (stated == null || types == null || answers == null) {
                throw new IllegalArgumentException("a row that can be asked states values, and is"
                        + " read with what the declarations say and where its answer stands");
            }
        }

        /** What the row states of the answer, where it is a row an answer can be held to. A row
         *  whose answer is owed is not one, and is an arm of its own rather than one of these with
         *  nothing to compare against. */
        private static Expectation.Asserts asserted(RowStatement.Stated stated) {
            if (stated.expects() instanceof Expectation.Asserts asserts) {
                return asserts;
            }
            throw new IllegalArgumentException("a row that can be asked states an answer to hold"
                    + " one to: " + stated.expects());
        }

        Verdict holds(ObservedValue answered) {
            if (answered == null) {
                throw new IllegalArgumentException("a row is held against an answer");
            }
            return Comparisons.verdict(asserts, answered, types, answers);
        }

        @Override
        public String toString() {
            return stated.toString();
        }
    }

    /**
     * A row whose answer is owed: it hands over its values and states nothing for an answer to keep.
     *
     * <p>An arm of its own and not a {@link SelfContained} with a hole in it. Whether an answer is
     * what a row states is a question about a row that states one, and a reader handed this one
     * with a way of asking would be handed a verdict about a row that claims nothing — which is the
     * verdict of whoever wrote the reader rather than the language's.
     *
     * <p>Said rather than left out. The row is written, an author owes it an answer, and a reader
     * that never heard of it would count a behavior's rows and find one fewer than were written.
     */
    public record AnswerOwed(RowStatement.Stated states) implements Statement {

        public AnswerOwed {
            if (states == null) {
                throw new IllegalArgumentException("a row whose answer is owed states its values");
            }
            if (!(states.expects() instanceof Expectation.Owed)) {
                throw new IllegalArgumentException("a row whose answer is owed is one that states"
                        + " no answer: " + states.expects());
            }
        }
    }

    /**
     * A row that hands over no values, and what it says instead.
     *
     * <p>Here rather than left out. A row an output cannot reproduce is still a row someone wrote,
     * and one arriving as no row at all would have a reader count a set it never walked as one it
     * walked and found empty.
     */
    public record NotReproducible(RowStatement.NotStated why) implements Statement {

        public NotReproducible {
            if (why == null) {
                throw new IllegalArgumentException("a row that states no values says why");
            }
        }

    }

    @Override
    public String toString() {
        return identity.shown();
    }
}
