package net.unit8.souther.compiler.highlight;

import net.unit8.souther.compiler.cst.CstLexer;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Generates a TextMate grammar ({@code souther.tmLanguage.json}) from the language's lexical
 * vocabulary, so the editor highlighting and GitHub Linguist rendering stay in step with the lexer.
 * The keyword list comes from {@link CstLexer#keywords()} — the single source of truth — and a test
 * asserts every keyword is categorised here, so adding one to the lexer forces a grammar update.
 *
 * <p>A regex grammar deliberately stops at what the token stream can classify (keywords, operators,
 * literals, comments, stdlib qualifiers). Distinguishing a type name from a value — which in Souther
 * are Japanese identifiers, not capitalised — is left to the LSP's semantic tokens.
 */
public final class TmLanguageGenerator {

    /** Declaration keywords — the top-level shapes. */
    private static final Set<String> DECLARATION =
            Set.of("module", "import", "exposing", "data", "behavior", "let");

    /** Control and clause keywords. */
    private static final Set<String> CONTROL =
            Set.of("if", "then", "else", "match", "with", "require", "constructs", "requires",
                    "as", "invariant");

    /** Boolean literals (scoped as language constants, not keywords). */
    private static final Set<String> BOOLEANS = Set.of("true", "false");

    /** The standard-library qualifiers (mirrors {@code Prelude.QUALIFIERS}); highlighted before a dot. */
    private static final List<String> QUALIFIERS =
            List.of("List", "String", "Map", "Set", "Bool", "Int", "Decimal", "Date", "DateTime");

    /** The operators, longest first so a prefix (e.g. {@code >}) never masks a longer form ({@code >->}). */
    private static final List<String> OPERATORS =
            List.of(">->", "|>", "...", "->", "<-", "++", "==", "/=", "<=", ">=", "&&", "||",
                    "+", "-", "*", "/", "=", "<", ">", "|", "?");

    private TmLanguageGenerator() {
    }

    /** Writes the grammar to the path given as the first argument. */
    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0]
                : "editors/vscode/syntaxes/souther.tmLanguage.json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, generate());
    }

    /** The grammar as pretty-printed JSON (ending with a newline). */
    public static String generate() {
        Map<String, Object> repository = new LinkedHashMap<>();
        repository.put("comments", match("comment.line.double-slash.souther", "//.*$"));
        repository.put("strings", strings());
        repository.put("decimal", match("constant.numeric.decimal.souther", "\\b[0-9]+(\\.[0-9]+)?m\\b"));
        repository.put("integer", match("constant.numeric.integer.souther", "\\b[0-9]+\\b"));
        repository.put("booleans", match("constant.language.boolean.souther", wordAlternation(BOOLEANS)));
        repository.put("declaration-keywords",
                match("keyword.declaration.souther", wordAlternation(DECLARATION)));
        repository.put("control-keywords",
                match("keyword.control.souther", wordAlternation(CONTROL)));
        repository.put("type-variable", match("variable.other.generic.souther", "'[A-Za-z][A-Za-z0-9_]*"));
        repository.put("qualifiers",
                match("support.class.souther", "\\b(" + String.join("|", QUALIFIERS) + ")\\b(?=\\s*\\.)"));
        repository.put("operators", match("keyword.operator.souther", operatorAlternation()));

        List<Object> patterns = List.of(
                include("#comments"), include("#strings"), include("#decimal"), include("#integer"),
                include("#booleans"), include("#declaration-keywords"), include("#control-keywords"),
                include("#type-variable"), include("#qualifiers"), include("#operators"));

        Map<String, Object> grammar = new LinkedHashMap<>();
        grammar.put("$schema",
                "https://raw.githubusercontent.com/martinring/tmlanguage/master/tmlanguage.json");
        grammar.put("name", "Souther");
        grammar.put("scopeName", "source.souther");
        grammar.put("fileTypes", List.of("sou"));
        grammar.put("patterns", patterns);
        grammar.put("repository", repository);

        try {
            return JsonMapper.builder().build()
                    .writerWithDefaultPrettyPrinter().writeValueAsString(grammar) + "\n";
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialise the TextMate grammar", e);
        }
    }

    private static Map<String, Object> match(String scope, String regex) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", scope);
        m.put("match", regex);
        return m;
    }

    private static Map<String, Object> include(String ref) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("include", ref);
        return m;
    }

    private static Map<String, Object> strings() {
        Map<String, Object> escape = new LinkedHashMap<>();
        escape.put("name", "constant.character.escape.souther");
        escape.put("match", "\\\\.");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", "string.quoted.double.souther");
        m.put("begin", "\"");
        m.put("end", "\"");
        m.put("patterns", List.of(escape));
        return m;
    }

    /** A word-boundary alternation over a sorted keyword set (sorted for a stable, reproducible file). */
    private static String wordAlternation(Set<String> words) {
        return "\\b(" + String.join("|", new TreeSet<>(words)) + ")\\b";
    }

    private static String operatorAlternation() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < OPERATORS.size(); i++) {
            if (i > 0) {
                sb.append('|');
            }
            sb.append(regexEscape(OPERATORS.get(i)));
        }
        return sb.toString();
    }

    private static String regexEscape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ("\\^$.|?*+()[]{}".indexOf(c) >= 0) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
