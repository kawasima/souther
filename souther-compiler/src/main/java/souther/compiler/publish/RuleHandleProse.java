package souther.compiler.publish;

import souther.compiler.diag.SourceRendering;
import souther.compiler.source.SourceId;

/**
 * What a rule handle reads as in the report a person reads.
 *
 * <p>The same sentence a document writes, because a reader meeting a rule in the report and a
 * consumer meeting it in the document are meeting one rule, and two spellings of one handle read as
 * two handles.
 *
 * <p>Apart from {@link RuleHandleSurface} rather than an arm of it. What a person is shown is held
 * to nothing in the schema, so it is not one of the fields a check counts — and a renderer that
 * handed back a string a document writer could put anywhere would leave that count a list of the
 * places somebody remembered. Which classes may name this is written down
 * ({@code WhoMaySayWhatARuleHandleReadsAs}).
 */
public final class RuleHandleProse {

    private RuleHandleProse() {
    }

    /** {@code handle} in a report about {@code sectionSource}, with the sources under the names
     *  {@code names} gives them. */
    public static String said(PublishedRuleHandle handle, SourceRendering sources,
                              SourceId sectionSource) {
        return RuleHandleSentence.said(handle, sources, sectionSource);
    }

    /** The same for a sentence with a handle in it, so that the words around one are put together
     *  the one way wherever they are read. */
    public static String said(PublishedSentence sentence, SourceRendering sources,
                              SourceId sectionSource) {
        return RuleHandleSentence.of(sentence, sources, sectionSource);
    }
}
