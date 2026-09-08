package souther.compiler.values;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static souther.compiler.values.Emptiness.EMPTY;
import static souther.compiler.values.Emptiness.NONEMPTY;
import static souther.compiler.values.Emptiness.UNDECIDED;
import static souther.compiler.values.Emptiness.Alternatives.BOTH_STAND;
import static souther.compiler.values.Emptiness.Alternatives.NEITHER_STANDS;
import static souther.compiler.values.Emptiness.Alternatives.ONLY_THE_LEFT;
import static souther.compiler.values.Emptiness.Alternatives.ONLY_THE_RIGHT;

/**
 * Which alternatives two answers leave standing, written out.
 *
 * <p>Every pair, said here rather than worked out, because a reading of this written the way the
 * subject is written would agree with it however either was wrong. What every caller of this shares
 * is the classification; what is held against it has to come from somewhere else, and the only
 * somewhere else a table of nine has is the table.
 *
 * <p>Which is why writing it out is not the copying this exists to remove. What was copied was each
 * layer working the classification out for itself and then acting on its own answer; what is here
 * is one statement of what the answer is, held against the one place that gives it.
 *
 * <p><b>{@link Emptiness#UNDECIDED} stands.</b> Nobody has shown that nothing satisfies such a
 * branch, and a reading that dropped it would drop a branch on the strength of not having looked.
 * That is the whole content of the rule, and the rows it decides are the four an answer nobody
 * settled takes part in.
 */
class ABranchStandsUnlessSomethingShowedItEmptyTest {

    /** Two answers and which of the alternatives they leave standing. */
    private record Row(Emptiness left, Emptiness right, Emptiness.Alternatives standing) {}

    private static List<Row> table() {
        return List.of(
                new Row(EMPTY, EMPTY, NEITHER_STANDS),
                new Row(EMPTY, NONEMPTY, ONLY_THE_RIGHT),
                new Row(EMPTY, UNDECIDED, ONLY_THE_RIGHT),
                new Row(NONEMPTY, EMPTY, ONLY_THE_LEFT),
                new Row(UNDECIDED, EMPTY, ONLY_THE_LEFT),
                new Row(NONEMPTY, NONEMPTY, BOTH_STAND),
                new Row(NONEMPTY, UNDECIDED, BOTH_STAND),
                new Row(UNDECIDED, NONEMPTY, BOTH_STAND),
                new Row(UNDECIDED, UNDECIDED, BOTH_STAND));
    }

    /**
     * Every pair leaves what the table says, each alternative answered for on its own side.
     *
     * <p>Which is where the sides are told apart, and the only place they are: an answer that named
     * the wrong side of every pair would still be a classification, and every law two of these
     * satisfy it would satisfy as well.
     */
    @Test
    void everyPairOfAnswersLeavesWhatTheTableSays() {
        for (Row row : table()) {
            assertEquals(row.standing(),
                    Emptiness.Alternatives.of(row.left(), row.right()),
                    () -> row.left() + " on the left and " + row.right() + " on the right");
        }
    }

    /**
     * And the table is about every pair of answers there are.
     *
     * <p>Asked of the answers themselves rather than of the rows, so that an answer added to the
     * three is a pair this says nothing about and not a pair nobody noticed. The rows say what each
     * of them leaves; this says that the rows are asked of all of them.
     */
    @Test
    void andTheTableIsAboutEveryPairOfAnswersThereIs() {
        List<String> pairs = new ArrayList<>();
        for (Emptiness left : Emptiness.values()) {
            for (Emptiness right : Emptiness.values()) {
                pairs.add(left + "/" + right);
            }
        }
        List<String> written = new ArrayList<>();
        table().forEach(row -> written.add(row.left() + "/" + row.right()));
        assertEquals(pairs.stream().sorted().toList(), written.stream().sorted().toList(),
                "a pair of answers the table says nothing about is one this compiler classifies"
                        + " without anybody having written down what the classification is");
    }

    /**
     * And every way the alternatives can fall is one some pair of answers reaches.
     *
     * <p>A way nothing reaches is one no reader's arm is ever taken, and the readers that switch
     * over these would be answering for a case that cannot happen while the case they are wrong
     * about goes unwritten.
     */
    @Test
    void andEveryWayTheAlternativesCanFallIsReached() {
        Set<Emptiness.Alternatives> reached = new LinkedHashSet<>();
        table().forEach(row -> reached.add(row.standing()));
        assertEquals(Set.of(Emptiness.Alternatives.values()), reached,
                "every way two alternatives can fall is one some pair of answers leaves");
    }

    /**
     * And whether there is a choice at all is that table and not a second one.
     *
     * <p>What a reader composing two things rather than four asks. Read off the rows, so that the
     * coarse answer cannot come apart from the answer it is coarse about — worked out separately,
     * the two would be two classifications and the second would be the thing this removes.
     */
    @Test
    void andWhetherThereIsAChoiceAtAllIsTheSameTable() {
        for (Row row : table()) {
            assertEquals(row.standing() == BOTH_STAND,
                    Emptiness.Alternatives.of(row.left(), row.right()).bothStand(),
                    () -> "a choice between " + row.left() + " and " + row.right());
        }
        assertTrue(BOTH_STAND.bothStand(), "two standing alternatives are a choice");
        assertFalse(NEITHER_STANDS.bothStand(), "and none of the other three is one");
        assertFalse(ONLY_THE_LEFT.bothStand());
        assertFalse(ONLY_THE_RIGHT.bothStand());
    }

    /**
     * And neither alternative is answered for out of what the other one is.
     *
     * <p>Read the pair backwards and the answer is the same one with its sides exchanged, which is
     * what it means for this to be about two alternatives and not about a first and a second. What
     * it does not say is which side is which: an answer with the two names exchanged everywhere
     * satisfies this as well, and what says which is which is the table above.
     */
    @Test
    void andReadingThePairBackwardsExchangesTheSides() {
        for (Emptiness left : Emptiness.values()) {
            for (Emptiness right : Emptiness.values()) {
                assertEquals(exchanged(Emptiness.Alternatives.of(left, right)),
                        Emptiness.Alternatives.of(right, left),
                        () -> left + " and " + right + ", read from either end");
            }
        }
    }

    /**
     * And it classifies two answers and does nothing with the alternatives.
     *
     * <p>What a choice comes to, which branch a caller takes, whether the values are merged or held
     * apart and whether the question waits are each the caller's, and each differs by what the
     * caller is composing. Written here as well, one of them would be a second place composing a
     * choice — and the one that must not be written is taking the standing alternative, which is
     * the operation a choice with one live branch was decided to have none of.
     *
     * <p>Held as what this may mention, because that is what an operation over branches needs: a
     * branch to be handed, or a type variable to be handed one under. Something that mentions
     * neither cannot be given one, whatever it is called.
     */
    @Test
    void andItClassifiesTwoAnswersAndActsOnNeither() {
        List<String> written = new ArrayList<>();
        for (Method each : Emptiness.Alternatives.class.getDeclaredMethods()) {
            // What an enum is, and not an operation somebody wrote over these.
            if (each.isSynthetic() || each.getName().equals("values")
                    || each.getName().equals("valueOf")) {
                continue;
            }
            written.add(each.getName());
            assertEquals(0, each.getTypeParameters().length,
                    () -> each.getName() + " is written over some type of the caller's, which is"
                            + " what an operation that is handed a branch needs");
            List<Class<?>> mentions = new ArrayList<>(List.of(each.getParameterTypes()));
            mentions.add(each.getReturnType());
            for (Class<?> what : mentions) {
                assertTrue(what == Emptiness.class || what == Emptiness.Alternatives.class
                                || what == boolean.class,
                        () -> each.getName() + " mentions " + what.getName() + ", which is neither"
                                + " an answer nor which alternatives stand nor whether they both do");
            }
        }
        assertEquals(List.of("bothStand", "of", "stands"), written.stream().sorted().toList(),
                "which alternatives two answers leave standing, whether that is a choice at all,"
                        + " and the rule they are read by — an operation beside them would be a"
                        + " second place saying what a choice comes to");
    }

    private static Emptiness.Alternatives exchanged(Emptiness.Alternatives standing) {
        return switch (standing) {
            case NEITHER_STANDS -> NEITHER_STANDS;
            case ONLY_THE_LEFT -> ONLY_THE_RIGHT;
            case ONLY_THE_RIGHT -> ONLY_THE_LEFT;
            case BOTH_STAND -> BOTH_STAND;
        };
    }
}
