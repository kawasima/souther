package souther.compiler.report;

import souther.compiler.diag.SourceRendering;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A row is offered as source, whatever the thing it is named after is called.
 *
 * <p>The block is written to be pasted into a model, so every part of it that is not prose is
 * something the language reads back. A row's name is a string the language reads, and a name is
 * what a class of the position is called — which for a rule about strings is words with the rule's
 * own quotes in them. Written out as it stands, such a name closes the literal early and the rest
 * of the row becomes source saying something else.
 *
 * <p>Held on the block rather than on the spelling alone. What a reader pastes is the text, and a
 * test that only asked how one string is escaped would go green over a block that had stopped
 * putting it through the escaping at all.
 */
class ARowIsOfferedAsSourceWhateverItIsNamedAfterTest {

    /**
     * A body dividing a string, whose classes are named after the rule that divides them.
     *
     * <p>No invariant on {@code Code}: what is under test is the name a row carries, and the class
     * on either side of the rule has a value to be written for.
     */
    private static final String NAMED_AFTER_A_RULE_ABOUT_STRINGS = """
            module example.esc

            data Code = String
            data Answered = { code: Code }

            behavior route : (code: Code) -> Answered | Abroad
                constructs Answered

            let route (code) = {
                guard String.startsWith("JP", code.value) else Abroad
                Answered { code = code }
            }
            """;

    private static String offered(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        assertEquals(java.util.List.of(), compilation.errors(),
                "the model under test compiles");
        return GeneratedRows.of(compilation, null, null,
                new SourceRendering(id -> "esc.sou", compilation.texts())).text();
    }

    /** The name is written as a string the language reads back. */
    @Test
    void aNameHoldingAQuoteIsWrittenAsALiteral() {
        String block = offered(NAMED_AFTER_A_RULE_ABOUT_STRINGS);

        assertTrue(block.contains("""
                    | "code=String.startsWith(\\"JP\\", x)"\
                """),
                () -> "a row named after a rule about strings, as source: " + block);
    }

    /**
     * And what is offered parses.
     *
     * <p>Beside the spelling and not instead of it. The rows are read back by the same parser a
     * person's file goes through, so a block that stopped parsing for some other reason is caught
     * here — and the spelling above says which row it was about.
     */
    @Test
    void whatIsOfferedIsSomethingAFileCanHold() {
        String block = offered(NAMED_AFTER_A_RULE_ABOUT_STRINGS);

        assertEquals(java.util.List.of(),
                souther.compiler.cst.CstParser.parse("examples for example.esc\n\n" + block)
                        .errors(),
                () -> "the block a reader pastes, as source: " + block);
    }
}
