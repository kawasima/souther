package souther.compiler.query;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * What a store is left holding when the walk over it was told to stop.
 *
 * <p>Two halves, and each is worth nothing without the other. The question the walk was inside when
 * it stopped never returned a value, so nothing may be kept for it: a store that kept one would hand
 * the next asker an answer that was never worked out. The questions that did return a value inside
 * it are answers of the revision they were asked at, and being told to stop is not an input moving —
 * nothing can move an input while a walk is running, because the walk is what the one thread holding
 * the store is doing. Those are kept, and the walk that follows pays only for what this one left.
 *
 * <p>Counting both is what makes this say anything. A store that threw everything away would satisfy
 * the first half on its own, and one that kept everything would satisfy the second.
 */
class AWalkThatWasStoppedKeepsWhatItAnsweredAndNothingElseTest {

    /** Whether the store is under a stop at all. The stop itself falls where the inner question
     * has answered and the outer one has not, which is the only place worth looking at. */
    private static final AtomicBoolean ARMED = new AtomicBoolean();

    private static final AtomicInteger OUTER_RUNS = new AtomicInteger();

    private static final AtomicInteger INNER_RUNS = new AtomicInteger();

    @Test
    void theQuestionItStoppedInsideIsAskedAgainAndTheOnesThatAnsweredAreNot() {
        ARMED.set(true);
        OUTER_RUNS.set(0);
        INNER_RUNS.set(0);

        Db db = new Db();
        db.abandonWhen(new Abandonment(() -> ARMED.get() && INNER_RUNS.get() > 0));

        assertThrows(Abandoned.class, () -> db.ask(new Outer()),
                "a walk told to stop stops, rather than answering with whatever it had");
        assertEquals(1, OUTER_RUNS.get(), "the outer question was entered once");
        assertEquals(1, INNER_RUNS.get(), "and the inner one answered once inside it");

        ARMED.set(false);
        assertEquals("inner", db.ask(new Outer()).value(),
                "asked again with nothing stopping it, the walk answers");
        assertEquals(2, OUTER_RUNS.get(),
                "the question that never returned a value left nothing behind to be handed over");
        assertEquals(1, INNER_RUNS.get(),
                "the one that did is an answer of this revision, and was not worked out twice");
    }

    /** Asks the inner question, and then a second one — which is where the stop falls. */
    private record Outer() implements Key<String> {

        @Override
        public Answer<String> compute(Db db) {
            OUTER_RUNS.incrementAndGet();
            String inner = db.ask(new Inner()).value();
            db.ask(new Beside());
            return Answer.of(inner);
        }
    }

    private record Inner() implements Key<String> {

        @Override
        public Answer<String> compute(Db db) {
            INNER_RUNS.incrementAndGet();
            return Answer.of("inner");
        }
    }

    /** Asked after the inner one, so that asking for it is where a stop is met. */
    private record Beside() implements Key<String> {

        @Override
        public Answer<String> compute(Db db) {
            return Answer.of("beside");
        }
    }
}
