package souther.compiler.publish;

import souther.compiler.diag.SourceNameResolver;
import souther.compiler.source.SourceId;

/**
 * What a place reads as in the report a person reads.
 *
 * <p>One spelling, because a reader meeting two places in one report is meeting two places and not
 * two conventions. It was a private method of {@link RuleHandleSentence} while a handle was the
 * only thing with a place in it; a second caller wanting the same words would have written them
 * again, and a line and a column mean nothing until somebody says which file they are of.
 *
 * <p>Not {@link RuleHandleProse}. That says how a reader is sent to a rule, and who may ask it is
 * counted ({@code WhoMaySayWhatARuleHandleReadsAs}) because a handle reaching a document past
 * {@link RuleHandleSurface} is a field no check knows about. A place is not a handle: it says where
 * to look and never which rule, so asking for one is not a way to write a handle.
 */
public final class PlaceProse {

    private PlaceProse() {
    }

    /**
     * {@code at} in a report about {@code sectionSource}, with the sources under the names
     * {@code names} gives them.
     *
     * <p>A line and a column are a place only beside a file. They are written on their own where
     * the section already names the file, and with the file where it does not — a position from
     * another source, printed bare, points at whatever happens to sit at those numbers in the one
     * the reader has in mind.
     */
    public static String said(PublishedAt at, SourceNameResolver names, SourceId sectionSource) {
        String numbers = at.line() + ":" + at.column();
        return at.source().equals(sectionSource) ? numbers
                : names.nameOf(at.source()) + ":" + numbers;
    }
}
