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
import static souther.compiler.values.Emptiness.SidesShownEmpty.BOTH;
import static souther.compiler.values.Emptiness.SidesShownEmpty.NEITHER;
import static souther.compiler.values.Emptiness.SidesShownEmpty.THE_LEFT;
import static souther.compiler.values.Emptiness.SidesShownEmpty.THE_RIGHT;

/**
 * Which sides two answers show empty, and which alternatives a choice reads off that.
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
 * <p><b>{@link Emptiness#UNDECIDED} was not shown empty.</b> Nobody has shown that nothing satisfies
 * such a branch, and a reading that sorted it with {@link Emptiness#EMPTY} would drop a branch on
 * the strength of not having looked. That is the whole content of the rule, and the rows it decides
 * are the four an answer nobody settled takes part in.
 *
 * <p><b>And the choice's reading is the classification with its sides exchanged.</b> The side shown
 * empty is the side that drops, so the four ways two sides can be shown empty and the four ways two
 * alternatives can fall are the same four read in opposite directions. Written out here, because a
 * projection that carried the word across would name the alternative a choice has lost — and a
 * conjunction, which reads the same classification the other way, would then be handed a word for
 * its surviving conjunct that means the refused one.
 */
class ABranchStandsUnlessSomethingShowedItEmptyTest {

    /** Two answers and which of the sides they show empty. */
    private record Row(Emptiness left, Emptiness right, Emptiness.SidesShownEmpty shown) {}

    private static List<Row> table() {
        return List.of(
                new Row(EMPTY, EMPTY, BOTH),
                new Row(EMPTY, NONEMPTY, THE_LEFT),
                new Row(EMPTY, UNDECIDED, THE_LEFT),
                new Row(NONEMPTY, EMPTY, THE_RIGHT),
                new Row(UNDECIDED, EMPTY, THE_RIGHT),
                new Row(NONEMPTY, NONEMPTY, NEITHER),
                new Row(NONEMPTY, UNDECIDED, NEITHER),
                new Row(UNDECIDED, NONEMPTY, NEITHER),
                new Row(UNDECIDED, UNDECIDED, NEITHER));
    }

    /** Which alternatives a choice is left by each way the sides can be shown empty. */
    private static List<Reading> readings() {
        return List.of(
                new Reading(NEITHER, BOTH_STAND),
                new Reading(THE_LEFT, ONLY_THE_RIGHT),
                new Reading(THE_RIGHT, ONLY_THE_LEFT),
                new Reading(BOTH, NEITHER_STANDS));
    }

    /** One way the sides can be shown empty, and what a choice makes of it. */
    private record Reading(Emptiness.SidesShownEmpty shown, Emptiness.Alternatives standing) {}

    /**
     * Every pair shows what the table says, each side answered for on its own.
     *
     * <p>Which is where the sides are told apart, and the only place they are: an answer that named
     * the wrong side of every pair would still be a classification, and every law two of these
     * satisfy it would satisfy as well.
     */
    @Test
    void everyPairOfAnswersShowsWhatTheTableSays() {
        for (Row row : table()) {
            assertEquals(row.shown(),
                    Emptiness.SidesShownEmpty.of(row.left(), row.right()),
                    () -> row.left() + " on the left and " + row.right() + " on the right");
        }
    }

    /**
     * And the table is about every pair of answers there are.
     *
     * <p>Asked of the answers themselves rather than of the rows, so that an answer added to the
     * three is a pair this says nothing about and not a pair nobody noticed. The rows say what each
     * of them shows; this says that the rows are asked of all of them.
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
     * And every way the sides can be shown empty is one some pair of answers reaches.
     *
     * <p>A way nothing reaches is one no reader's arm is ever taken, and the readers that switch
     * over these would be answering for a case that cannot happen while the case they are wrong
     * about goes unwritten.
     */
    @Test
    void andEveryWayTheSidesCanBeShownEmptyIsReached() {
        Set<Emptiness.SidesShownEmpty> reached = new LinkedHashSet<>();
        table().forEach(row -> reached.add(row.shown()));
        assertEquals(Set.of(Emptiness.SidesShownEmpty.values()), reached,
                "every way two sides can be shown empty is one some pair of answers shows");
    }

    /**
     * And which alternatives stand is that classification with its sides exchanged.
     *
     * <p>The direction, written out. A choice loses the side that was shown empty, so the left of
     * the classification is the alternative the choice no longer has — and the four rows here are
     * the whole of what a reader gets wrong by carrying the word across rather than exchanging it.
     */
    @Test
    void andWhichAlternativesStandIsThatWithTheSidesExchanged() {
        for (Reading reading : readings()) {
            assertEquals(reading.standing(), Emptiness.Alternatives.from(reading.shown()),
                    () -> "a choice whose sides shown empty are " + reading.shown());
        }
        Set<Emptiness.SidesShownEmpty> read = new LinkedHashSet<>();
        readings().forEach(reading -> read.add(reading.shown()));
        assertEquals(Set.of(Emptiness.SidesShownEmpty.values()), read,
                "and a way the sides can be shown empty this says nothing about is one a choice is"
                        + " left something by without anybody having written down what");
        assertEquals(Set.of(Emptiness.Alternatives.values()),
                new LinkedHashSet<>(readings().stream().map(Reading::standing).toList()),
                "and every way two alternatives can fall is one some classification reads as");
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
            assertEquals(row.shown() == NEITHER,
                    Emptiness.Alternatives.from(
                            Emptiness.SidesShownEmpty.of(row.left(), row.right())).bothStand(),
                    () -> "a choice between " + row.left() + " and " + row.right());
        }
        assertTrue(BOTH_STAND.bothStand(), "two standing alternatives are a choice");
        assertFalse(NEITHER_STANDS.bothStand(), "and none of the other three is one");
        assertFalse(ONLY_THE_LEFT.bothStand());
        assertFalse(ONLY_THE_RIGHT.bothStand());
    }

    /**
     * And neither side is answered for out of what the other one is.
     *
     * <p>Read the pair backwards and the answer is the same one with its sides exchanged, which is
     * what it means for this to be about two sides and not about a first and a second. What it does
     * not say is which side is which: an answer with the two names exchanged everywhere satisfies
     * this as well, and what says which is which is the table above.
     */
    @Test
    void andReadingThePairBackwardsExchangesTheSides() {
        for (Emptiness left : Emptiness.values()) {
            for (Emptiness right : Emptiness.values()) {
                assertEquals(exchanged(Emptiness.SidesShownEmpty.of(left, right)),
                        Emptiness.SidesShownEmpty.of(right, left),
                        () -> left + " and " + right + ", read from either end");
            }
        }
    }

    /**
     * And the choice's reading exchanges the sides with it.
     *
     * <p>A law about the projection and not about either end of it: whichever way round a choice's
     * two alternatives were written, reading them the other way answers with the sides exchanged.
     * A projection that mapped both of the one-sided classifications onto one alternative would
     * satisfy the rows above for three of the four and fail this.
     */
    @Test
    void andTheChoicesReadingExchangesTheSidesWithIt() {
        for (Emptiness.SidesShownEmpty shown : Emptiness.SidesShownEmpty.values()) {
            assertEquals(exchanged(Emptiness.Alternatives.from(shown)),
                    Emptiness.Alternatives.from(exchanged(shown)),
                    () -> shown + ", read from either end");
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
        assertEquals(List.of("bothStand", "from"),
                writtenOver(Emptiness.Alternatives.class,
                        Set.of(Emptiness.Alternatives.class, Emptiness.SidesShownEmpty.class,
                                boolean.class)),
                "which alternatives a classification leaves standing and whether that is a choice at"
                        + " all — an operation beside them would be a second place saying what a"
                        + " choice comes to");
        assertEquals(List.of("of"),
                writtenOver(Emptiness.SidesShownEmpty.class,
                        Set.of(Emptiness.class, Emptiness.SidesShownEmpty.class)),
                "and the classification sorts two answers and reads neither connective's meaning"
                        + " into them: a reading named for what a side is worth is a connective's"
                        + " and is written where that connective is");
    }

    /**
     * What is written over {@code word}, each of them mentioning nothing outside {@code mentions}.
     *
     * <p>Asked of the declared methods, so that a reading added to one of these words has to be
     * written down here — and asked about what each of them mentions, because an operation that is
     * handed a branch or written over a type of the caller's is one such a word has begun composing.
     */
    private static List<String> writtenOver(Class<?> word, Set<Class<?>> mentions) {
        List<String> written = new ArrayList<>();
        for (Method each : word.getDeclaredMethods()) {
            // What an enum is, and not an operation somebody wrote over these.
            if (each.isSynthetic() || each.getName().equals("values")
                    || each.getName().equals("valueOf")) {
                continue;
            }
            written.add(each.getName());
            assertEquals(0, each.getTypeParameters().length,
                    () -> each.getName() + " is written over some type of the caller's, which is"
                            + " what an operation that is handed a branch needs");
            List<Class<?>> said = new ArrayList<>(List.of(each.getParameterTypes()));
            said.add(each.getReturnType());
            for (Class<?> what : said) {
                assertTrue(mentions.contains(what),
                        () -> each.getName() + " mentions " + what.getName() + ", which is none of"
                                + " what " + word.getSimpleName() + " is about");
            }
        }
        return written.stream().sorted().toList();
    }

    private static Emptiness.SidesShownEmpty exchanged(Emptiness.SidesShownEmpty shown) {
        return switch (shown) {
            case NEITHER -> NEITHER;
            case THE_LEFT -> THE_RIGHT;
            case THE_RIGHT -> THE_LEFT;
            case BOTH -> BOTH;
        };
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
