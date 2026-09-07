package souther.compiler.query;

import java.util.function.BooleanSupplier;

/**
 * What long work asks between its steps: whether whoever set it going still wants the answer.
 *
 * <p>One value rather than a boolean passed down, because the two ends of it are not alike. What
 * makes work stop is a question only its caller can answer — a client that cancelled a request, a
 * keystroke that arrived while a diagnose was walking — and every step in between should be able to
 * ask it without being told which of those it is. A step asks {@link #stopIfAsked} and goes on.
 *
 * <p>Asking is not a place to do anything else. A supplier that throws is throwing what the work
 * should stop for, and this passes it through: the caller that built this decides both what stopping
 * means and what stopping raises.
 */
public final class Abandonment {

    /** Work that runs to the end whatever else happens — a batch compile, a test. */
    public static final Abandonment NEVER = new Abandonment(() -> false);

    private final BooleanSupplier asked;

    public Abandonment(BooleanSupplier asked) {
        this.asked = asked;
    }

    /** Whether the answer is still wanted. */
    public boolean asked() {
        return asked.getAsBoolean();
    }

    /** Stops the work here when the answer is no longer wanted. */
    public void stopIfAsked() {
        if (asked.getAsBoolean()) {
            throw new Abandoned();
        }
    }
}
