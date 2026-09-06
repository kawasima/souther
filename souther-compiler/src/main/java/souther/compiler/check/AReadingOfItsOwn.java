package souther.compiler.check;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A source somebody made for a reading of their own. Two of these are never one.
 *
 * <p>What a source is where nothing else is said, and the safe answer: a reading made from one is a
 * reading nothing shares, so a reader that wraps a lookup to watch what it is asked — or builds a
 * scope of its own — gets what it built and nobody else's.
 */
record AReadingOfItsOwn(long ordinal) implements RuleReadingSource.Origin {

    private static final AtomicLong MADE = new AtomicLong();

    /** One nobody else can name. */
    static AReadingOfItsOwn next() {
        return new AReadingOfItsOwn(MADE.incrementAndGet());
    }
}
