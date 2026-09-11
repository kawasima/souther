package souther.compiler.publish;

import souther.compiler.diag.SourceRendering;
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
    static String of(PublishedSentence sentence, SourceRendering sources, SourceId sectionSource) {
        return switch (sentence) {
            case PublishedSentence.Words it -> it.said();
            case PublishedSentence.AroundAHandle it ->
                    it.before() + said(it.handle(), sources, sectionSource) + it.after();
        };
    }

    static String said(PublishedRuleHandle handle, SourceRendering sources,
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
                    it.kind().word() + "@" + place(it.at(), sources, sectionSource);
            // Written somewhere else and reached from here: the rule is one and the reader is sent
            // to two places, which the sentence keeps apart.
            case PublishedRuleHandle.Reached it -> it.kind().word() + " in `" + it.reachedBy() + "`"
                    + ", reached at " + place(it.at(), sources, sectionSource);
            // And with no position to send them to, the declaration is the whole of it.
            case PublishedRuleHandle.ReachedOutOfSight it ->
                    it.kind().word() + " in `" + it.reachedBy() + "`";
        };
    }

    /**
     * A place as the sentence writes it.
     *
     * <p>The words themselves are {@link PlaceProse}'s, so that a place in a handle and a place
     * beside one read alike. What is left here is the arm with no file to name, whose numbers are
     * the reader's to place.
     */
    private static String place(PublishedRuleHandle.Place at, SourceRendering sources,
                                SourceId sectionSource) {
        return switch (at) {
            case PublishedRuleHandle.Place.InSource it ->
                    PlaceProse.said(it.at(), sources, sectionSource);
            case PublishedRuleHandle.Place.Unplaced it ->
                    String.valueOf(sources.layouts().resolve(it.at()));
        };
    }
}
