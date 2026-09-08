package souther.compiler.observe;

import souther.compiler.diag.SourcePos;
import souther.compiler.types.TypeSymbol;

import java.util.List;
import java.util.Objects;

/**
 * What one {@code example} row turned out to be — the observation every adequacy measure reads.
 *
 * <p>A row is evidence of several different things at once, and which thing depends on how far it got.
 * A row whose expectation was built states that its case is expected; a row that ran states which case
 * the behavior actually produced, whether or not that is the one it expected; only a row that held
 * states that the behavior produces the case the model says it should. So {@link #expectedArm} and
 * {@link #resultArm} are both kept: a row that expected {@code Approved} and saw {@code Rejected} is
 * not evidence for {@code Approved}, but it is evidence that {@code Rejected} can happen.
 *
 * <p>A row whose answer is owed is evidence of the second and of neither of the others. It hands
 * over its inputs, it is applied, and what it saw is what it saw; nothing it did is evidence that
 * the model answers as it should. Which of the two a row is is {@link #expectation}, read off the
 * source where the row was read — not off {@link #expectedArm} being absent, which is also what a
 * row whose text names no case looks like, and not off what the row states, which is dropped
 * whenever the row's values are too large to hand on.
 *
 * <p>{@link #at} carries a source id as well as a position, because rows are gathered under the module
 * they belong to and a module's rows are written across its own source and any number of attached
 * {@code examples for} files.
 *
 * <p>The combinations that arise, so that a reader knows which are real and a writer knows where a new
 * stop-point belongs:
 *
 * <pre>
 * how it ended               stage               disposition      failurePhase
 * ---------------------------------------------------------------------------------
 * recorded, not evaluated    FIXTURES_VALIDATED  PENDING          NONE
 * a wrong arity              NONE                FAILED           INPUT_FIXTURE
 * an input fixture failed    NONE                FAILED           INPUT_FIXTURE
 * the expectation failed     NONE                FAILED           EXPECTED_FIXTURE
 * the expected arm is wrong  NONE                FAILED           EXPECTED_FIXTURE
 * the values broke a clause  FIXTURES_VALIDATED  FAILED           ENSURES
 * a dependency had no fake   FIXTURES_VALIDATED  FAILED           FAKE_RESOLUTION
 * a fake had no answer       INVOKED             FAILED           FAKE_RESOLUTION
 * an `unreachable` reached   INVOKED             FAILED           INVOCATION
 * an invariant aborted       INVOKED             FAILED           INVOCATION
 * the answer disagreed       COMPARED            FAILED           COMPARISON
 * it held                    COMPARED            HELD             NONE
 * its answer is owed         ANSWERED            NOTHING_TO_HOLD  NONE
 * a fixture's helper hung    NONE                INCOMPLETE       TIMEOUT
 * the behavior hung          INVOKED             INCOMPLETE       TIMEOUT
 * it could not be handed on  FIXTURES_VALIDATED  INCOMPLETE       VALUE_CROSSING
 * the answer was not this
 *   model's to hand it to    FIXTURES_VALIDATED  INCOMPLETE       ANSWERER_ESTABLISHMENT
 * </pre>
 *
 * <p>A row whose answer is owed reaches every one of these but the four that are about an
 * expectation. Its inputs can fail to build, its fake can have no answer, the body it runs can
 * abort and its budget can run out, and each of those is the row ending the way any other row ends
 * that way. What it cannot do is fail to build an expectation it does not have, disagree with one,
 * or hold — so the row above is the only end that is its alone, and the four it cannot reach are
 * held to that at construction.
 *
 * <p>The last two share a stage and a disposition and are not the same thing, which is what the
 * phase is for. {@link FailurePhase#VALUE_CROSSING} is a row that could not be handed on: the value
 * it built could not be put in the form an answerer of other classes reads, and whether the answer
 * is of this model was never in question. {@link FailurePhase#ANSWERER_ESTABLISHMENT} is that
 * question answered badly: nothing could establish that what answers the behavior was built against
 * the module the row is written for, so the row was not handed on at all. Both are
 * {@code FIXTURES_VALIDATED}/{@code INCOMPLETE}, because in both nothing about the model was
 * established and nothing applied the row.
 *
 * <p>What a host was supposed to provide and did not — the runtime off the classpath, so the
 * generated classes will not link — is still said of the module rather than of a row
 * ({@link Incompleteness}).
 *
 * <p>Whether the behavior was applied is the {@link Stage} column: everything at {@code INVOKED} or
 * past it says what applied it ({@link Run#applied()}), and everything before it says nothing did.
 * What the row counted is a different question and is not read off this table — a row that stopped
 * at {@code NONE} spent whatever its fixtures spent, and a row given up on was never read at all
 * ({@link Counting}).
 *
 * @param at             where the row is written
 * @param target         the behavior the row is about
 * @param identity       what the row names itself. A {@link RowIdentity.Named} is unique among the
 *                       rows this module writes for {@link #target}, so something outside the file
 *                       can say which row it means; a {@link RowIdentity.Unnamed} can be shown and
 *                       not addressed
 * @param expectation    what the source put where the row's answer goes, settled where the row was
 *                       read and true whatever became of the evaluation
 * @param stage          how far it got
 * @param disposition    how it ended
 * @param failurePhase   where it stopped, when it did
 * @param expectedArm    the case the row's expectation constructs, or null when the text does not say
 * @param resultArm      the case the behavior answered with, or null when it did not run or did not
 *                       answer with a case
 * @param inputCases     the case each input fixture constructs, in order; an entry is null where the
 *                       text does not say
 * @param inputs         each input as the compiler owns it, in order
 * @param statement      what the row states, taken as this evaluation read it. Here rather than
 *                       worked out again, because reading it is running what the fixtures name: a
 *                       second reading would apply the same helpers a second time, and a helper
 *                       applied twice is counted twice and does whatever it does twice. What is
 *                       carried is what this evaluation had in hand, so nothing downstream reads a
 *                       source text
 * @param run            what applied the behavior, and what this compile counted while the row ran.
 *                       A row that reached {@link Stage#INVOKED} says what applied it and a row that
 *                       did not says nothing did, which is held to at construction: the two are
 *                       different cuts of one evaluation and cannot be recorded disagreeing. What
 *                       the counting says is not held to the stage, because a row's evaluation is
 *                       not only its application — a fixture applies the helpers it names first, so
 *                       a row that applied nothing can still have spent counted points
 */
public record RowOutcome(SourcePos at,
                         String target,
                         RowIdentity identity,
                         ExpectationState expectation,
                         Stage stage,
                         Disposition disposition,
                         FailurePhase failurePhase,
                         TypeSymbol expectedArm,
                         TypeSymbol resultArm,
                         List<TypeSymbol> inputCases,
                         List<ObservedValue> inputs,
                         RowStatement statement,
                         Run run) {

    public RowOutcome {
        Objects.requireNonNull(statement, "a row states something");
        if (statement instanceof RowStatement.Stated values
                && !values.inputs().equals(inputs)) {
            // One fact with two places to be read from, held to being one. A row that states values
            // states the ones it handed over, and a reader taking them from either side gets the
            // same values — where a measure reads them here and an output reads them there, two
            // lists that could differ are two answers about what a row is about.
            throw new IllegalArgumentException("a row states the values it handed over: " + inputs
                    + " against " + values.inputs());
        }
        if (stage.reached(Stage.FIXTURES_VALIDATED)
                == statement instanceof RowStatement.StoppedBeforeItsValues) {
            // The two are one evaluation read apart, and they are written a line from each other:
            // what the row states is taken as its values are read, and the stage says they were.
            // Kept apart, a reader meets a row whose values were read saying the reading stopped
            // before them, or one that says what it states beside a stage that never got there.
            throw new IllegalArgumentException("a row whose values were read states them, and one"
                    + " the reading stopped before does not: " + stage + " with " + statement);
        }
        // A list that keeps a null in it cannot be List.copyOf'd, and an input whose case the text does
        // not say is exactly that — so the unmodifiable wrapper is taken rather than the copying factory.
        inputCases = inputCases == null ? List.of()
                : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(inputCases));
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        Objects.requireNonNull(run, "a row says what became of its evaluation");
        if (stage.reached(Stage.INVOKED) == run.applied() instanceof Applied.Nothing) {
            // Held here because the two are written from one evaluation and read apart: a stage that
            // says the behavior was applied and a run that says nothing applied it is a state no
            // evaluation produces, and a reader that met it would have to decide which half to trust.
            // What the row counted is not held to the stage — a fixture spends counted points before
            // the behavior is reached, and a row that never reached it spent what it spent.
            throw new IllegalArgumentException(
                    "a row that applied the behavior says what applied it, and one that did not says "
                            + "nothing did: " + stage + " with " + run.applied());
        }
        // What the source put where the answer goes and what became of the answer are held to each
        // other. Asked of {@link #expectation}, which is what the row was read as, and never of
        // what the row states: a statement carries the expectation only while it carries the
        // values, and a row whose input is larger than a snapshot keeps states nothing while being
        // a perfectly well written row whose answer is owed.
        //
        // A row whose answer is owed has nothing to compare an answer against and nothing for an
        // answer to keep, so it does not reach a comparison and does not end as one that held; and
        // ending with nothing to hold is what such a row ends as and not something a row that
        // states an answer can be recorded as. Everything else a row can end as it can end as
        // either way: an input fixture, a clause, a fake, an application and a budget are all
        // reached before what the row states of the answer is of any use.
        Objects.requireNonNull(expectation,
                "a row says what its source put where its answer goes");
        boolean owed = expectation == ExpectationState.OWED;
        if (owed && (stage.reached(Stage.COMPARED) || disposition == Disposition.HELD
                || failurePhase == FailurePhase.EXPECTED_FIXTURE
                || failurePhase == FailurePhase.COMPARISON)) {
            throw new IllegalArgumentException("a row whose answer is owed holds an answer to"
                    + " nothing: " + stage + " with " + disposition + " at " + failurePhase);
        }
        if (!owed && disposition == Disposition.NOTHING_TO_HOLD) {
            throw new IllegalArgumentException("a row that ended with nothing to hold its answer to"
                    + " is one whose answer is owed: " + expectation);
        }
        // And the two are not allowed to drift where both are there. A statement that carries an
        // expectation carries the one the row was read as.
        if (statement instanceof RowStatement.Stated values
                && (values.expects() instanceof Expectation.Owed) != owed) {
            throw new IllegalArgumentException("a row states the answer its source put there: "
                    + expectation + " with " + values.expects());
        }
    }

    /** Whether the behavior answered for this row — it was applied and a value came back, which is
     * what {@link Stage#ANSWERED} is reached by. Where a row states what it expects the answer is
     * then compared, and where it is owed the row ends here; both saw the answer. Whether that
     * answer can be named as a case is {@link #observed}: a value of a type no declaration of the
     * module's names is an answer all the same, and a run that got one is not a run that produced
     * nothing. */
    public boolean answered() {
        return stage.reached(Stage.ANSWERED);
    }

    /** Whether this row is evidence that the behavior can answer with {@link #resultArm}. A row that
     * disagreed still saw what it saw. */
    public boolean observed() {
        return answered() && resultArm != null;
    }
}
