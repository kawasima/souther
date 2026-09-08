package souther.bench;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import souther.compiler.check.AssumedContract;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.Located;
import souther.compiler.diag.Severity;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the edit {@link Incremental} times as a restated relation is one a body of the corpus reads.
 *
 * <p>A behavior states what a caller may take as holding of its answer, and a caller that has
 * matched a case reads it. Nothing about a timing says whether it reached that: a corpus stating no
 * relation compiles, is timed, and reports a number for a walk that stopped at the declaration.
 * Which is what this corpus did until the relation was written.
 *
 * <p>Three things, because the edit measures nothing if any of them stops holding. The rule is in
 * the corpus, so there is something to rewrite. A caller reads it, so rewriting it reaches a body.
 * And the rewrite changes what that caller may assume, so the store has an answer to re-establish
 * rather than one it finds unchanged and stops at.
 */
@Tag("population")
class TheRelationAnEditRestatesIsOneACallerReadsTest {

    private static final String MODULE = "example.crm";
    private static final ValueName.Behavior STATING =
            new ValueName.Behavior(MODULE, "findAccountByDomain");
    private static final String CALLER = "linkLeadToAccount";

    /**
     * The rule the edit rewrites is written in the corpus, once.
     *
     * <p>Once, and not merely somewhere: the edit rewrites every occurrence of the text, so two
     * would make one keystroke into two edits and the number would be of neither.
     */
    @Test
    void theCorpusStatesTheRuleTheEditRestates() {
        Corpus crm = Corpus.load("crm");
        int written = 0;
        for (String source : crm.sources()) {
            int at = source.indexOf(Incremental.STATED_RULE);
            while (at >= 0) {
                written++;
                at = source.indexOf(Incremental.STATED_RULE, at + 1);
            }
        }
        int found = written;
        assertEquals(1, found, () -> "the crm corpus writes the rule `" + Incremental.STATED_RULE
                + "` " + found + " time(s), and the relation edit rewrites every one of them");
    }

    /** A body of the corpus reads what that behavior states. */
    @Test
    void aCallerOfItReadsWhatItStates() {
        Compilation compilation = compiled(Corpus.load("crm").sources());
        Map<ValueName.Behavior, AssumedContract> read =
                compilation.db().ask(new Bodies.ContractsForBody(MODULE, CALLER)).value();
        assertNotNull(read, () -> MODULE + "." + CALLER + " has no body to read contracts for");
        AssumedContract assumed = read.get(STATING);
        assertNotNull(assumed, () -> MODULE + "." + CALLER + " reads no contract of " + STATING
                + ", so nothing here reaches what a caller may assume. It reads: " + read.keySet());
        assertTrue(!assumed.rules().isEmpty(),
                () -> STATING + " states a contract with no rule in it");
    }

    /**
     * And restating the rule changes what that caller may assume.
     *
     * <p>Asked of the reading a caller is handed rather than of the source text. An edit that
     * changed the file and came out equal here is one the store answers by finding every answer
     * still holding, which is the floor the re-ask round already measures.
     *
     * <p>The restated corpus is compiled and checked for errors as well. A rule that stopped
     * compiling would be timed as a compile that gives up early, which is faster than one that
     * finishes and reads as an improvement.
     */
    @Test
    void restatingItChangesWhatThatCallerMayAssume() {
        Corpus crm = Corpus.load("crm");
        List<String> restated = new ArrayList<>();
        for (String source : crm.sources()) {
            restated.add(Incremental.restated(source, 0));
        }
        assertNotEquals(crm.sources(), restated,
                "the relation edit left the corpus as it found it");

        Compilation before = compiled(crm.sources());
        Compilation after = compiled(restated);
        assertEquals(List.of(), errorsOf(after),
                "the corpus no longer compiles once the relation is restated");

        AssumedContract was = before.db().ask(new Bodies.Assumptions(STATING)).value();
        AssumedContract now = after.db().ask(new Bodies.Assumptions(STATING)).value();
        assertNotNull(was, () -> STATING + " states nothing before the edit");
        assertNotEquals(was, now, "restating the rule left what a caller may assume unchanged, so"
                + " the store has nothing to re-establish and the round times the floor");
    }

    private static Compilation compiled(List<String> sources) {
        Compilation compilation = Compilation.ofSources(sources, ModulePath.EMPTY);
        compilation.answerEverything();
        return compilation;
    }

    private static List<String> errorsOf(Compilation compilation) {
        List<String> errors = new ArrayList<>();
        for (List<Diagnostic> found : Located.diagnosticsOf(compilation.diagnostics()).values()) {
            for (Diagnostic diagnostic : found) {
                if (diagnostic.severity() == Severity.ERROR) {
                    errors.add(diagnostic.code() + " at " + diagnostic.primary());
                }
            }
        }
        return errors;
    }
}
