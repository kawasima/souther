package souther.compiler.sites;

import souther.compiler.diag.SourcePos;
import souther.compiler.types.SourceConstructOrigin;

import java.util.Map;

/**
 * Where each application a module wrote stands in its own source.
 *
 * <p>Beside {@link WrittenForks} and {@link WrittenConditions} and read off the same walk. The three
 * are told apart by what a reader holds: one holds a fork it takes an arm of, one a condition a row
 * had to satisfy, and this one an application a rule was read off. A module wrote what it wrote
 * once, and three walks of it would be three answers to that.
 *
 * <p>Filed under {@link SourceConstructOrigin}, which a copy cannot change, so a reader that met an
 * application inside a helper spliced into its own body asks the module that wrote the helper. What
 * it gets back moves when that module's text moves and at no other time.
 *
 * <p><b>Every application, and what one states is not read here.</b> Which applications state a rule
 * about the strings at a position is the reading's answer, and a walk that decided it here would be
 * that recognition made a second time with none of what the reading knows. So what is filed is where
 * each stands; the surplus is places nobody asks for.
 *
 * <p>An application this compiler composed is not one of them. What is filed is what an author
 * wrote, which is the case an application carries a written origin in — a call a pass wrote back out
 * of a checked value states nothing, and a place filed for one would answer a question about a rule
 * with somewhere no author can be sent.
 */
public final class WrittenApplications {

    private final Map<SourceConstructOrigin, SourcePos> byOrigin;

    WrittenApplications(Map<SourceConstructOrigin, SourcePos> byOrigin) {
        this.byOrigin = Map.copyOf(byOrigin);
    }

    /** Where the application {@code origin} names is written, or null where this module wrote no
     *  such application. */
    public SourcePos at(SourceConstructOrigin origin) {
        return origin == null ? null : byOrigin.get(origin);
    }

    /** How many were found, which is what says a walk reached a module at all. */
    public int count() {
        return byOrigin.size();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof WrittenApplications written && byOrigin.equals(written.byOrigin);
    }

    @Override
    public int hashCode() {
        return byOrigin.hashCode();
    }

    @Override
    public String toString() {
        return byOrigin.size() + " written applications";
    }
}
