package souther.compiler.conformance;

import org.junit.jupiter.api.Test;

import souther.compiler.DefaultStdlib;
import souther.compiler.Reserved;
import souther.compiler.check.BehaviorRequirement;
import souther.compiler.check.Prepared;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.Shapes;
import souther.compiler.types.ValueName;
import souther.compiler.stdlib.Stdlib;
import souther.compiler.cst.CstLexer;
import souther.compiler.cst.CstParser;
import souther.compiler.cst.SyntaxElement;
import souther.compiler.cst.SyntaxKind;
import souther.compiler.cst.SyntaxNode;
import souther.compiler.cst.SyntaxToken;
import souther.compiler.cst.TopLevelForm;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * What the conformance corpus has to reach, asked of the language rather than of a list kept beside
 * it.
 *
 * <p>A corpus is written to cover something, and the covering is the whole of its value: a model
 * that reaches most of the language answers most of the question and says nothing about which part
 * it left out. Judging that by reading it does not survive the language growing — a form added
 * tomorrow is one nobody thought to look for.
 *
 * <p>So every claim here reads a declaration the language already keeps for its own reasons —
 * {@link TopLevelForm}, {@link CstLexer#keywords()}, {@link Reserved#MODULES} — and reports what the
 * corpus does not reach. Nothing is written out beside those, because a written-out copy is a second
 * table that goes on agreeing with the first only until one of them changes.
 *
 * <p>Japanese identifiers are the one thing here with no enum behind them. They are a fact about the
 * lexer rather than about any domain, and a corpus written in English reaches none of them, so the
 * claim is made directly.
 *
 * <p>Not every construct is a form the parser opens. Where a behavior's body comes from and whether
 * the module writing about it is the one that declared it are two enumerations the language already
 * keeps, and what a corpus reaches of their product is asked here as the tokens are asked above —
 * computed from the corpus rather than written out beside it.
 */
class AConformanceCorpusReachesEveryConstructTheLanguageDeclaresTest {

    /** Every meaningful token of every source in the corpus, one list per source. */
    private static List<List<SyntaxToken>> sources() {
        List<List<SyntaxToken>> out = new ArrayList<>();
        for (ConformanceCorpus corpus : ConformanceCorpus.all()) {
            for (String source : corpus.sources()) {
                List<SyntaxToken> tokens = new ArrayList<>();
                meaningful(CstParser.parse(source).root(), tokens);
                out.add(tokens);
            }
        }
        return out;
    }

    private static void meaningful(SyntaxNode node, List<SyntaxToken> out) {
        for (SyntaxElement element : node.children()) {
            if (element instanceof SyntaxNode child) {
                meaningful(child, out);
            } else if (element instanceof SyntaxToken token && !token.isTrivia()) {
                out.add(token);
            }
        }
    }

    /** A reader of the tokens ahead of one position, which is what {@link TopLevelForm} asks for. */
    private record Ahead(List<SyntaxToken> tokens, int from) implements TopLevelForm.Lookahead {

        @Override
        public SyntaxKind kindAt(int i) {
            int at = from + i;
            return at < tokens.size() ? tokens.get(at).kind() : SyntaxKind.EOF;
        }

        @Override
        public String textAt(int i) {
            int at = from + i;
            return at < tokens.size() ? tokens.get(at).text() : "";
        }
    }

    /**
     * Every form that may open something at the top level is opened somewhere in the corpus.
     *
     * <p>Asked of every position rather than of the top-level positions only. A form is recognised
     * by the words that open it and nothing here reads further, so a position inside a body cannot
     * answer for one: no two forms begin with the same word, and none of those words opens anything
     * where a body may be written. Sweeping every position costs a walk and takes the test out of
     * the business of deciding which positions are the top-level ones — a decision the parser
     * already makes and this would be a second copy of.
     */
    @Test
    void everyTopLevelFormIsWrittenSomewhere() {
        Set<TopLevelForm> opened = new LinkedHashSet<>();
        for (List<SyntaxToken> tokens : sources()) {
            for (int i = 0; i < tokens.size(); i++) {
                TopLevelForm.at(new Ahead(tokens, i)).ifPresent(opened::add);
            }
        }
        Set<String> missing = new TreeSet<>();
        for (TopLevelForm form : TopLevelForm.values()) {
            if (!opened.contains(form)) {
                missing.add(form + " (" + form.starter() + ")");
            }
        }
        assertEquals(new TreeSet<String>(), missing,
                "top-level forms the conformance corpus never writes");
    }

    /**
     * Every reserved word is written somewhere in the corpus.
     *
     * <p>Matched on the spelling of a token the lexer did not read as an identifier. A word in a
     * comment or inside a string is not the language being used, and neither is a field someone
     * named after one — {@link CstLexer#keywords()} is the set the lexer gives a kind of its own, so
     * a token carrying one of those spellings under any other kind is not the word.
     */
    @Test
    void everyReservedWordIsWrittenSomewhere() {
        Set<String> written = new LinkedHashSet<>();
        for (List<SyntaxToken> tokens : sources()) {
            for (SyntaxToken token : tokens) {
                if (token.kind() != SyntaxKind.IDENT) {
                    written.add(token.text());
                }
            }
        }
        Set<String> missing = new TreeSet<>(CstLexer.keywords());
        missing.removeAll(written);
        assertEquals(new TreeSet<String>(), missing,
                "reserved words the conformance corpus never writes");
    }

    /**
     * Every standard library module is used by the corpus, in whichever of the two ways it can be.
     *
     * <p>Called rather than imported, where it declares anything to call. An import brings names in
     * and a corpus could carry one it never uses, which would leave the module reached by the import
     * list and by nothing the compiler has to answer about. A qualified call is the module being
     * used, and it is written the same way whether or not the module was imported.
     *
     * <p>A module that declares no operation cannot be called, and one of them does not:
     * {@code souther.instant} is a module line and nothing else, there to give {@code Instant} a
     * type. Demanding a call there would demand something the library does not offer, so what is
     * asked of such a module is that its type is named. Which of the two applies is read from
     * {@link Stdlib#entries()} rather than decided here, so a module that grows its first
     * operation
     * starts being held to the call without anything being rewritten.
     */
    @Test
    void everyStandardLibraryModuleIsUsed() {
        Set<String> called = new LinkedHashSet<>();
        Set<String> named = new LinkedHashSet<>();
        for (List<SyntaxToken> tokens : sources()) {
            for (int i = 0; i < tokens.size(); i++) {
                if (tokens.get(i).kind() != SyntaxKind.IDENT) {
                    continue;
                }
                named.add(tokens.get(i).text());
                if (i + 1 < tokens.size() && tokens.get(i + 1).kind() == SyntaxKind.DOT) {
                    called.add(tokens.get(i).text());
                }
            }
        }
        Set<String> missing = new TreeSet<>();
        for (Reserved.StdlibModule module : Reserved.MODULES) {
            String qualifier = module.qualifier();
            // Which module an operation belongs to is the alias it holds, rather than the first
            // part of a spelling read up to a dot.
            boolean declaresOperations = DefaultStdlib.get().entries().keySet().stream()
                    .anyMatch(operation -> operation.alias().equals(qualifier));
            if (declaresOperations ? !called.contains(qualifier) : !named.contains(qualifier)) {
                missing.add(qualifier + " (" + module.moduleName()
                        + (declaresOperations ? ", never called into" : ", type never named") + ")");
            }
        }
        assertEquals(new TreeSet<String>(), missing,
                "standard library modules the conformance corpus never uses");
    }

    /** One module of one corpus, compiled, with what the compiler answers about it. */
    private record Module(Compilation compiled, String name) {}

    private static List<Module> modules() {
        List<Module> out = new ArrayList<>();
        for (ConformanceCorpus corpus : ConformanceCorpus.all()) {
            Compilation compiled = corpus.analyse().compilation();
            for (String name : compiled.modules()) {
                out.add(new Module(compiled, name));
            }
        }
        return out;
    }

    /**
     * The corpus carries a requirement across a module boundary.
     *
     * <p>Two halves, because it takes two modules to write and either alone is a construct the
     * corpus already had. A behavior of one module requires one another module declares — which is
     * what a {@code depends on} naming a borrowed name comes to, read off {@code Requirements}
     * rather than off the clause, since that is the answer the emitter and the rows are built from.
     * And a stand-in written where the rows run answers it, which is what makes the requirement
     * something a row can be evaluated through rather than only declared.
     *
     * <p>Made directly, as the claim about Japanese identifiers is. There is no enum of the ways two
     * modules can be arranged, and a product of the two enums that are here — where a body comes
     * from, and whether the module writing about it declared it — would be a table of cells nobody
     * checked against the rules. What is claimed is the arrangement the compiler answers questions
     * about that a whole-corpus run left unasked (issue #1108).
     */
    @Test
    void aRequirementIsCarriedAcrossAModuleBoundary() {
        List<String> closed = new ArrayList<>();
        List<String> owed = new ArrayList<>();
        for (Module module : modules()) {
            Prepared prepared =
                    module.compiled().db().ask(new Shapes.Prepared(module.name())).value();
            Map<String, List<BehaviorRequirement>> requirements =
                    module.compiled().db().ask(new Bodies.Requirements(module.name())).value();
            if (prepared == null || requirements == null) {
                continue;
            }
            // The stand-ins this module writes, as the behaviors they answer for. A requirement is
            // closed by one of these or by nothing: `with` supplies a dependency taking no input,
            // and what this is about takes one.
            Set<ValueName.Behavior> standsInFor =
                    prepared.forExamples().fakes().answering().keySet();
            requirements.forEach((behavior, required) -> {
                for (BehaviorRequirement each : required) {
                    if (each.dependency().module().equals(module.name())) {
                        continue;
                    }
                    String said = module.name() + "." + behavior + " requires " + each.dependency();
                    // The same behavior on both sides. Collected apart, a requirement borrowed by
                    // one module and a table written in another for something nothing requires
                    // would answer this together while the construct was written nowhere.
                    (standsInFor.contains(each.dependency()) ? closed : owed).add(said);
                }
            });
        }
        assertFalse(closed.isEmpty(), "no behavior of the conformance corpus requires one another"
                + " module declares and closes it with a stand-in written where its rows run, so"
                + " nothing here carries a requirement across a module boundary and runs through"
                + " it. Borrowed and left owed: " + owed);
    }

    /**
     * Something in the corpus is named in Japanese.
     *
     * <p>Held because the corpus is written by whoever is working on the compiler, and what they
     * write is in the language they are writing the compiler in. An identifier outside ASCII is
     * lexed, resolved, rendered in a diagnostic, measured for width by the formatter and written
     * into a generated class name, and a corpus that is all English exercises none of that while
     * looking complete by every other measure here.
     */
    @Test
    void somethingIsNamedInJapanese() {
        for (List<SyntaxToken> tokens : sources()) {
            for (SyntaxToken token : tokens) {
                if (token.kind() == SyntaxKind.IDENT && !token.text().codePoints().allMatch(
                        c -> c < 0x80)) {
                    return;
                }
            }
        }
        throw new AssertionError("no identifier in the conformance corpus is written in Japanese");
    }
}
