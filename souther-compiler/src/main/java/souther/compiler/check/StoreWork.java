package souther.compiler.check;

import java.util.function.Supplier;

/**
 * Work whose result one question may be handed after another question did it.
 *
 * <p>A question of the store is kept by what it read: an edit reaches it because something it read
 * came out different. So work shared between two questions cannot be shared as a result alone — the
 * question handed it read nothing to do it, and would be kept over an edit to what doing it read.
 * What is shared is the result and the reads together, which is what this is for: it watches what
 * the store is asked while the work is done, and hands back a way of asking the same again on
 * behalf of whoever is answered out of it.
 *
 * <p>Not a memo. Nothing here keeps anything, compares anything, or decides that a question need
 * not be answered; it says what a piece of work read, and it is the caller that keeps the work and
 * for as long as it is entitled to.
 */
public interface StoreWork {

    /** Does {@code work}, watching what the store is asked while it is done. */
    <T> Made<T> watching(Supplier<T> work);

    /**
     * What the work made, and the reads it was made by.
     *
     * @param value what the work made
     * @param reads what the store was asked while it was made
     */
    record Made<T>(T value, Reads reads) {}

    /** What a piece of work read. */
    interface Reads {

        /**
         * Reads them for whoever is being answered now.
         *
         * <p>Said by a caller handing on work somebody else did: what that work read is what the
         * question being answered read, because the answer it is getting was made by reading it.
         */
        void here();
    }

    /** Work nobody watches, for a caller with no store: it is done, and it read nothing anybody
     *  will be handed. */
    StoreWork UNWATCHED = new StoreWork() {

        @Override
        public <T> Made<T> watching(Supplier<T> work) {
            return new Made<>(work.get(), () -> { });
        }
    };
}
