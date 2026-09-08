package souther.bench;

import org.junit.jupiter.api.AfterAll;
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
import java.util.Collections;
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
 * <p>What the measurement rests on, asked one claim at a time. The rule is in the corpus, so there
 * is something to rewrite. Compiling it takes a rule of that contract into a caller's arguments, so
 * the walk this is about is one the corpus reaches rather than one a fixture reaches. The body that
 * takes it in is checked against it. The rewrite changes what that body may assume, so the store has
 * an answer to re-establish rather than one it finds unchanged. And it leaves the other readings the
 * corpus is held to where they were, so the round times one edit and not two.
 */
@Tag("population")
class TheRelationAnEditRestatesIsOneACallerReadsTest {

    private static final String MODULE = "example.crm";
    private static final ValueName.Behavior STATING =
            new ValueName.Behavior(MODULE, "findAccountByDomain");
    private static final String CALLER = "linkLeadToAccount";

    /**
     * The corpus as it is written and the corpus with the rule restated, each compiled once for the
     * class.
     *
     * <p>Two texts and five questions. A corpus is the size somebody writes, so compiling one is
     * most of what this class costs, and asking each question of its own compile is the same answer
     * worked out again — which is what {@link CorpusTest} does for the same reason.
     *
     * <p>The one made of the corpus as written carries the watch, so what it took in is recorded on
     * the compile the other questions are asked of rather than on a second one made to record it.
     * The watch is set for as long as that compile runs and put back after: it is read by whatever
     * thread the compile is on, and left assigned it would collect from every compile after.
     */
    private static Compilation before;
    private static Compilation after;
    private static List<ValueName.Behavior> takenIn;

    private static synchronized Compilation before() {
        if (before == null) {
            List<ValueName.Behavior> watching = Collections.synchronizedList(new ArrayList<>());
            AssumedContract.TAKEN_IN = watching;
            try {
                before = compiled(Corpus.load("crm").sources());
            } finally {
                AssumedContract.TAKEN_IN = null;
            }
            takenIn = watching;
        }
        return before;
    }

    private static synchronized Compilation after() {
        if (after == null) {
            after = compiled(restated(Corpus.load("crm"), 0));
        }
        return after;
    }

    /**
     * And given back when the class is done with them.
     *
     * <p>A fork runs its classes one after another and keeps the JVM, so two answered compilations
     * of a model this size, with everything they reached, would be a floor under the heap that every
     * class after this one runs above.
     */
    @AfterAll
    static void released() {
        before = null;
        after = null;
        takenIn = null;
    }

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

    /**
     * Compiling the corpus takes a rule of that contract into a caller's arguments.
     *
     * <p>The claim the measurement rests on, and the one nothing else here makes. What a body is
     * checked against is a question about the graph: the contract is in the map handed to the
     * analysis. Whether the analysis reached it is a question about the walk — the call has to be
     * followed to what produced the answer, the arm has to name a case the rule is about, and the
     * declaration's parameters have to be paired with what this call handed over. A body that went
     * on calling the behavior and stopped matching its cases would leave the map as it is and take
     * nothing in, and so would a hand-over that stopped between the two.
     *
     * <p>Read where the substitution is made ({@link AssumedContract#TAKEN_IN}), because taking a
     * rule in is silent: it narrows what the caller may hold of the answer, and a body that is
     * correct either way is checked either way. There is no diagnostic to read it off.
     */
    @Test
    void compilingTheCorpusTakesInWhatItStates() {
        before();
        assertTrue(takenIn.contains(STATING),
                () -> "compiling the corpus substituted no rule of " + STATING + " into a call, so"
                        + " the reading a caller depends on is built and not reached. Taken in: "
                        + takenIn);
    }

    /** And the body that takes it in is checked against it, which is what says the two are one
     *  arrangement rather than the contract being reached from somewhere else. */
    @Test
    void whatThatCallerIsCheckedAgainstIncludesWhatItStates() {
        Map<ValueName.Behavior, AssumedContract> read =
                before().db().ask(new Bodies.ContractsForBody(MODULE, CALLER)).value();
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
        assertNotEquals(crm.sources(), restated(crm, 0),
                "the relation edit left the corpus as it found it");
        assertEquals(crm.sources(), restated(crm, 1),
                "two rounds of the relation edit write one text, so the second is no edit and the"
                        + " round after it times the floor");

        assertEquals(List.of(), errorsOf(after()),
                "the corpus no longer compiles once the relation is restated");

        AssumedContract was = before().db().ask(new Bodies.Assumptions(STATING)).value();
        AssumedContract now = after().db().ask(new Bodies.Assumptions(STATING)).value();
        assertNotNull(was, () -> STATING + " states nothing before the edit");
        assertNotEquals(was, now, "restating the rule left what a caller may assume unchanged, so"
                + " the store has nothing to re-establish and the round times the floor");
    }

    /**
     * And changes nothing else this corpus is read for.
     *
     * <p>A rule is read by more than the caller. What it compares draws a line on the values it
     * compares (spec §a-clause-draws-a-line-on-what-it-compares-an-input-against), so a rule stating
     * a relation the clause did not state moves the adequacy reading as well, and the round would be
     * timing an edit to two readings under a name that says one. Which is what the first writing of
     * this edit did: it added a bound on the parameter, the way an author tightening a rule would,
     * and the adequacy answer for the module came back different.
     *
     * <p>Two readings, and neither is every reading there is. What they are is the two a rule is
     * known to be read by beside the caller — what the compiler says about the module, and how well
     * the module's rows cover it — so an edit that moved a third would go on being timed here. The
     * claim is that these two do not move, not that nothing else can.
     */
    @Test
    void restatingItLeavesTheOtherReadingsOfTheCorpusWhereTheyWere() {
        assertEquals(diagnosticsOf(before()), diagnosticsOf(after()),
                "restating the rule moved what the compiler says about the corpus");
        assertEquals(before().adequacy(MODULE), after().adequacy(MODULE),
                "restating the rule moved how well the module's rows are said to cover it, so the"
                        + " round times an edit to that reading as well as to the caller's");
    }

    private static List<String> restated(Corpus corpus, int round) {
        List<String> out = new ArrayList<>();
        for (String source : corpus.sources()) {
            out.add(Incremental.restated(source, round));
        }
        return out;
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

    /** Every diagnostic, by what it says and where — the primary is read so that a diagnostic
     *  moving to another line is a difference and not a coincidence of counts. */
    private static List<String> diagnosticsOf(Compilation compilation) {
        List<String> said = new ArrayList<>();
        for (List<Diagnostic> found : Located.diagnosticsOf(compilation.diagnostics()).values()) {
            for (Diagnostic diagnostic : found) {
                said.add(diagnostic.severity() + " " + diagnostic.code() + " at "
                        + diagnostic.primary());
            }
        }
        return said;
    }
}
