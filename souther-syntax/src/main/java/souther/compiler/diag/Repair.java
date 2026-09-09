package souther.compiler.diag;

import java.util.Objects;

/**
 * An edit that answers a finding: replace {@code target} with {@code with}.
 *
 * <p>The whole of what a machine may apply. A deletion is an empty replacement and an insertion is a
 * replacement of an empty stretch, so one shape says all three, and nothing here is sorted into
 * kinds a caller would have to tell apart.
 *
 * <p>{@code target} is the answer to a different question from a diagnostic's {@link Primary}. What
 * a finding is said about and what makes it go away are the same stretch often enough to be
 * mistaken for one rule, and they are not: a qualified name nothing denotes is reported over the
 * whole name and repaired by rewriting the one part that is wrong, and a report moved to where a
 * reader can reach it ({@link Diagnostic#reachedFrom}) is said at an import while the text to change
 * stays where it was written. So the two are carried separately and a repair says its own place.
 */
public record Repair(Region target, String with) {

    public Repair {
        Objects.requireNonNull(target, "a repair says where it applies");
        Objects.requireNonNull(with, "a repair says what to write");
    }
}
