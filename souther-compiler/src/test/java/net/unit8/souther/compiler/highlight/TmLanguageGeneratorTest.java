package net.unit8.souther.compiler.highlight;

import net.unit8.souther.compiler.cst.CstLexer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The generated TextMate grammar is valid JSON, categorises every lexer keyword, and matches the
 * committed grammar file — so a keyword added to the lexer forces the highlighter to be regenerated. */
class TmLanguageGeneratorTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    @SuppressWarnings("unchecked")
    void theGrammarIsValidJsonForSourceSouther() {
        Map<String, Object> grammar = (Map<String, Object>) JSON.readValue(
                TmLanguageGenerator.generate(), Object.class);
        assertEquals("source.souther", grammar.get("scopeName"));
        assertTrue(grammar.containsKey("repository"));
    }

    @Test
    void everyLexerKeywordIsCategorised() {
        Set<String> inGrammar = new TreeSet<>();
        inGrammar.addAll(alternatives("declaration-keywords"));
        inGrammar.addAll(alternatives("control-keywords"));
        inGrammar.addAll(alternatives("booleans"));
        assertEquals(new TreeSet<>(CstLexer.keywords()), inGrammar,
                "the highlighter grammar must categorise exactly the lexer's keywords");
    }

    @Test
    void theCommittedGrammarMatchesTheGenerator() throws Exception {
        Path committed = Path.of("..", "editors", "vscode", "syntaxes", "souther.tmLanguage.json");
        assertTrue(Files.exists(committed), "run TmLanguageGenerator to write " + committed);
        String onDisk = Files.readString(committed, StandardCharsets.UTF_8);
        assertEquals(TmLanguageGenerator.generate(), onDisk,
                "the committed grammar is stale — regenerate it with TmLanguageGenerator");
    }

    /** The keyword alternatives of a repository entry whose match is {@code \b(a|b|c)\b}. */
    @SuppressWarnings("unchecked")
    private Set<String> alternatives(String entry) {
        Map<String, Object> grammar = (Map<String, Object>) JSON.readValue(
                TmLanguageGenerator.generate(), Object.class);
        Map<String, Object> repository = (Map<String, Object>) grammar.get("repository");
        Map<String, Object> node = (Map<String, Object>) repository.get(entry);
        String match = (String) node.get("match");
        String group = match.substring(match.indexOf('(') + 1, match.lastIndexOf(')'));
        return new TreeSet<>(Set.of(group.split("\\|")));
    }
}
