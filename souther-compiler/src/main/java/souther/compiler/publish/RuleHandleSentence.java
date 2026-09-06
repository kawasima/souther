package souther.compiler.publish;

import souther.compiler.diag.SourceNameResolver;
import souther.compiler.source.SourceId;

/**
 * What a rule handle reads as, which is one sentence per form of {@link PublishedRuleHandle}.
 *
 * <p>The one spelling. A rule and a line the same rule drew and a question about it are found the
 * same way, and two spellings of one handle read as two handles — which is what a border and a
 * standing question had between them until this, one of them writing the word for the rule and the
 * other reading it off the construct the rule stood in.
 *
 * <p>Reached only through {@link RuleHandleSurface}, which is why nothing here is public. A handle
 * written into a document under a field nobody declared is a surface no check knows about, and a
 * check that has to find such a field by looking for a call is a check that guesses. Held to a
 * surface, a new one cannot be written without being named.
 */
final class RuleHandleSentence {

    private RuleHandleSentence() {
    }

    /**
     * {@code handle} as a report about {@code sectionSource} writes it, with the sources under the
     * names {@code names} gives them.
     *
     * <p>No {@code default} arm, so a form added to the grammar is one somebody spells rather than
     * one that arrives at a reader as a sentence about something else.
     */
    static String said(PublishedRuleHandle handle, SourceNameResolver names,
                       SourceId sectionSource) {
        return switch (handle) {
            case PublishedRuleHandle.NamedInvariant it ->
                    "invariant " + it.declaredOn() + " (" + it.clause() + ")";
            // Counted from one, as somebody reading the declaration counts them.
            case PublishedRuleHandle.NumberedInvariant it ->
                    "invariant " + it.declaredOn() + " #" + it.number();
            case PublishedRuleHandle.NamedEnsures it ->
                    "ensures " + it.behavior() + " (" + it.clause() + ")";
            case PublishedRuleHandle.WholeEnsures it -> "ensures " + it.behavior();
            case PublishedRuleHandle.Written it ->
                    it.kind().word() + "@" + place(it.at(), names, sectionSource);
            // Written somewhere else and reached from here: the rule is one and the reader is sent
            // to two places, which the sentence keeps apart. Where this compile met no position
            // there is nothing to send them to but the declaration.
            case PublishedRuleHandle.Reached it -> it.kind().word() + " in `" + it.reachedBy() + "`"
                    + (it.at() instanceof PublishedRuleHandle.Place.Nowhere ? ""
                            : ", reached at " + place(it.at(), names, sectionSource));
        };
    }

    /**
     * A place as the sentence writes it.
     *
     * <p>A line and a column are a place only beside a file. They are written on their own where the
     * section already names the file, and with the file where it does not — a position from another
     * source, printed bare, points at whatever happens to sit at those numbers in the one the reader
     * has in mind.
     */
    private static String place(PublishedRuleHandle.Place at, SourceNameResolver names,
                                SourceId sectionSource) {
        return switch (at) {
            case PublishedRuleHandle.Place.InSource it -> {
                PublishedAt where = it.at();
                String numbers = where.line() + ":" + where.column();
                yield where.source().equals(sectionSource) ? numbers
                        : names.nameOf(where.source()) + ":" + numbers;
            }
            case PublishedRuleHandle.Place.Unplaced it -> it.line() + ":" + it.column();
            // Refused rather than spelled as nothing. The one form that reaches here without a
            // position says so in its own sentence, and a place written as an empty string would be
            // a reader sent to a file with no line in it.
            case PublishedRuleHandle.Place.Nowhere _ -> throw new IllegalStateException(
                    "code out of sight is said by what reaches it and not by where it is");
        };
    }
}
