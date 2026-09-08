package souther.compiler.sites;

import souther.compiler.diag.SourcePos;
import souther.compiler.types.SourceConstructOrigin;

import java.util.Map;

/**
 * Where each fork a module wrote stands in its own source.
 *
 * <p>Filed under the identity a copy cannot change ({@link SourceConstructOrigin}), which is what
 * lets a reader holding a fork it met somewhere else ask the module that wrote it where it is. A
 * helper expanded into three callers puts one fork in three trees; the fork is one, and where it is
 * written is the module's own answer rather than something each copy carries.
 *
 * <p>That is the whole of what this is for. A report about an arm of a fork spliced in from another
 * module points into the file that fork is written in, and it can do so while the value it holds
 * says only which fork it is — where that fork is is asked of the module that wrote it, and asked
 * again when that module's source moves.
 *
 * <p><b>Forks and not every construct the source wrote.</b> An origin names any construct — an
 * application, a comparison, a collection literal — and this holds the ones a body's arms are owed
 * for: what an author writes as an {@code if}, an attempted construction, a {@code match}, and each
 * guard of a comprehension, which lowers to a fork of its own. Widened to whatever carries an
 * origin, the question would be one a caller could ask about a construct nothing files and be
 * answered that nobody wrote it.
 *
 * <p>Of the module's own source and nothing it was handed. What another module wrote is that
 * module's to answer for, and a table gathered over everything a body reaches would say one thing
 * here and another there, depending on which caller was walked.
 */
public final class WrittenForks {

    private final Map<SourceConstructOrigin, SourcePos> byOrigin;

    WrittenForks(Map<SourceConstructOrigin, SourcePos> byOrigin) {
        this.byOrigin = Map.copyOf(byOrigin);
    }

    /** Where the fork {@code origin} names is written, or null where this module wrote no such
     *  fork. */
    public SourcePos at(SourceConstructOrigin origin) {
        return origin == null ? null : byOrigin.get(origin);
    }

    /** How many were found, which is what says a walk reached a module at all. */
    public int count() {
        return byOrigin.size();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof WrittenForks written && byOrigin.equals(written.byOrigin);
    }

    @Override
    public int hashCode() {
        return byOrigin.hashCode();
    }

    @Override
    public String toString() {
        return byOrigin.size() + " written forks";
    }
}
