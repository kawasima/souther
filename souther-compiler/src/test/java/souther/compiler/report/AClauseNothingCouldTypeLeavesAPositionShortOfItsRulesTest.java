package souther.compiler.report;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import souther.compiler.report.AdequacyReport;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which word a position whose rules were never arrived at gets, and what such a model is.
 *
 * <p>Two questions about one model, and neither is whether the vocabulary's words reach a document
 * at all. That is asked of every word of it in one place
 * ({@link souther.compiler.query.EveryNotReadReasonIsWrittenBySomeCompilationTest}), which is
 * where a word added arrives as a question — so what a model writes is settled there, and what is
 * left here is what that answer does not say.
 *
 * <p>The first is which of two words. A position the walk reached and whose rules it did not is not
 * a position holding values inside something the walk does not reach into, and the model below is
 * one a reading could take either way: the reaching was made, and what went unread is a clause.
 * Read as the second, an author is sent after a container that is not the matter.
 *
 * <p>The second is what this model is. It reaches the word through a clause the front end could not
 * type, and the same model is one this compiler refuses — two facts about one witness, kept
 * together so that neither is read without the other.
 *
 * <p><b>And no further than this witness.</b> Nothing here says every route to the word wants a
 * refused model: a declaration that resolves while nothing expands the clauses of its module
 * reaches it too ({@code AnExpansionThatDidNotHappenIsARuleNotReachedTest}), so what the word means
 * is not read off what this fixture is. Written as a property of the word, the sentence would be
 * one this cannot fail on — another route arriving tomorrow leaves this witness refused and this
 * test green.
 */
class AClauseNothingCouldTypeLeavesAPositionShortOfItsRulesTest {

    private static String reportOf(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).json(SourceRendering.namedByIdentity(SourceLayouts.NONE));
    }

    /**
     * A position the walk reached, whose rules it did not.
     *
     * <p>The clause below states nothing that could be typed, so it never reaches a reading and
     * which position it governed is exactly what is unknown about it. So the word is the one for a
     * position whose rules were never arrived at, and not the one for values held inside something
     * the walk does not reach into: the reaching was made.
     *
     * <p>This model used to carry {@code unsupported_traversal}, and it was the only model here
     * that did. What is left carrying that word is an {@code Option} and a {@code Map}, and no
     * model written here reaches it through either: an optional divides into its two cases and is
     * measured, and a map's contents are named by nothing a body can write that this reads. So the
     * word goes uncarried by any document this test builds until those two are reached into, and
     * what still holds it to meaning one thing is the projection that writes it
     * ({@link souther.compiler.partition.ReportedReason}), tested where that is.
     */
    private static final String RULES_NEVER_ARRIVED_AT = """
            module demo
            data Ok
            data Item = String
                invariant unreadable = value == 1
            data Basket = { item: Item }
            behavior run : (b: Basket) -> Ok
            let run (b) = Ok
            """;

    @Test
    void aPositionWhoseRulesTheReadingNeverArrivedAtIsWrittenAsThatWord() {
        String json = reportOf(RULES_NEVER_ARRIVED_AT);

        assertTrue(json.contains("\"rules_not_read_at_all\""), json);
        assertFalse(json.contains("\"unsupported_traversal\""), json);
    }
    /**
     * And the witness above is a model this compiler refuses.
     *
     * <p>Said out loud, and about this model. What it reaches the word through is a clause the
     * front end could not type, and a model carrying one is refused — so the pair of facts is what
     * the fixture is, and a reader taking the word from it is reading a model nobody can compile.
     *
     * <p>A tripwire for the fixture and not a property of the word. The day this model compiles,
     * what it is a witness of has changed and the entry above is about something else; the day
     * some other model reaches the word without a diagnostic, nothing here notices and nothing
     * here claimed to.
     */
    @Test
    void thisWitnessIsOneTheCompilerRefuses() {
        Compilation compilation = Compilation.ofSource(RULES_NEVER_ARRIVED_AT, "Main");
        compilation.answerEverything();
        assertFalse(compilation.diagnostics().values().stream()
                        .flatMap(java.util.List::stream).toList().isEmpty(),
                "a position whose rules never arrived takes a clause nothing could type");
    }

}
