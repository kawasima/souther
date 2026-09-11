package souther.compiler.query;

import souther.compiler.diag.SourceRendering;
import souther.compiler.Compiler;
import souther.compiler.conformance.ConformanceCorpus;
import souther.compiler.jvm.ClassFileImage;
import souther.compiler.meta.ModulePath;
import souther.compiler.report.AdequacyReport;
import souther.compiler.report.GeneratedRows;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every question this compiler declares is one a run over the corpus reaches, or one written down as
 * outside a run — for want of the input it takes, or for want of anything that reads its answer.
 *
 * <p>A question nobody asks is a question nothing checks. What is kept in an answer, whether two
 * compiles of one source answer alike, what a report says — every one of those is held against the
 * answers a run happens to produce, so a family of questions no run reaches is outside all of it and
 * says so nowhere. That is not something the checks themselves can notice: they compare what they
 * found with what is written down, and both are silent about a question that was never put.
 *
 * <p>So the vocabulary is closed from both ends. What the compiler declares is read off the compiled
 * classes rather than off a list somebody maintains — a list is a second truth, and the day a key is
 * added and not registered is the day it says the coverage is whole. What a run reaches is the union
 * of what this project's own operations ask for. Between them:
 *
 * <pre>declared = reached ⊎ what a batch run has no input for ⊎ what no batch result reads</pre>
 *
 * <p>and the union is disjoint, so a question written down as outside a run that a run turns out to
 * reach is a failure as much as one in neither.
 *
 * <p>Two ways of being outside a run and not one, because they are refuted by different things. The
 * first is refuted by a corpus: write a source that puts a cursor somewhere and the question becomes
 * askable. The second is refuted by a consumer: the input has been there all along, and what is
 * missing is anything in a batch result that depends on the answer. Held as one set, a question of
 * the second kind would be excused by a sentence written about the first, which is true of none of
 * them.
 *
 * <p><b>Operations and not questions.</b> What is run below is what this project does — analyse and
 * report, offer rows, ask what a module declares, build at the level a build measures at. Which
 * questions that reaches is the consequence. Written the other way round, with a fixture per key,
 * the suite would be a list of stimuli invented to reach a list, and a question added to one of these
 * operations would arrive uncovered while the arithmetic still added up.
 */
@Tag("population")
class EveryQuestionThisCompilerDeclaresIsReachedOrOutsideABatchRunTest {

    /**
     * The questions a batch run has no input for.
     *
     * <p>Every one of them takes a place in a text that somebody's cursor is at, or is reached only
     * from one that does. A compilation of a set of sources has no such place, so these are not
     * questions the operations below could be made to reach by writing a better corpus — the input
     * they take does not exist in a batch run at all.
     *
     * <p>What this does not say is that they go unasked. {@code souther-lsp} asks them, and whether
     * that vocabulary is closed the way this one is closed is a question about the editor and is not
     * answered here.
     */
    private static final Set<String> NO_INPUT_IN_A_BATCH_RUN = Set.of(
            "souther.compiler.query.Bodies$ContractCapabilities",
            "souther.compiler.query.Names$Declaration",
            "souther.compiler.query.Names$DeclaredAt",
            "souther.compiler.query.Names$DenotedAt",
            "souther.compiler.query.Names$Reachable",
            "souther.compiler.query.Names$TypeAt",
            "souther.compiler.query.Names$UsesOf",
            "souther.compiler.query.Names$ValueAt",
            "souther.compiler.query.Names$ValueDeclarationsOf",
            "souther.compiler.query.Names$ValueDeclaredAt",
            "souther.compiler.query.Names$ValueDenotedAt",
            "souther.compiler.query.Names$ValueUsesOf",
            "souther.compiler.query.Shapes$InvariantCapabilities");

    /**
     * The questions no batch result reads, and why each of them does not.
     *
     * <p>A batch run can put these: they take a module name, which it has. What it has no use for is
     * the answer — nothing a compilation produces rests on one, because what does is outside the
     * compiler. Asking one from an operation here to make this arithmetic balance would file a
     * dependency in the graph that no reader of the result stands behind, and every question this
     * test exists to notice could be excused the same way.
     *
     * <p>One entry per question, each with its own reason. There is no rule of the form "everything a
     * tooling boundary asks belongs here": what a package a key was written in says about who reads
     * it is nothing, and a rule of that shape would admit the next key without anyone stating why it
     * is not read. A reason is written where the exemption is claimed, so a claim that stops being
     * true is a line that has to be edited.
     *
     * <p>And it is a claim that expires. {@link #andNothingReachedIsWrittenDownAsOutsideARun} fails
     * on an entry here that a run does reach, so the day a batch result comes to read one of these,
     * the line saying nothing does is the thing that has to go.
     */
    private static final Map<String, String> NO_BATCH_CONSUMER = Map.of(
            Sites.Authored.class.getName(),
            "where a module's source was written, occurrence by occurrence — a projection for a"
                    + " reader outside the compiler, and no answer of a batch compilation reads it");

    /** What the scan counted, whether or not it read everything it found. */
    private static Set<String> declared() throws Exception {
        Set<String> out = new TreeSet<>();
        DeclaredQuestions.found(DeclaredQuestions.scan()).forEach(each -> out.add(each.getName()));
        return out;
    }

    /** Where the query vocabulary is kept. */
    private static final String WHERE_QUESTIONS_LIVE = "souther.compiler.query.";

    /**
     * And every question is declared where the vocabulary is kept.
     *
     * <p>Its own claim rather than something the scan assumes. What the scan counts is every key
     * this compiler has, wherever it was written; that they are all written in one place is a fact
     * about how the compiler is arranged, and a key put somewhere else moves the vocabulary rather
     * than escaping the count.
     */
    @Test
    void everyQuestionIsDeclaredWhereTheyBelong() throws Exception {
        Set<String> elsewhere = new TreeSet<>();
        declared().stream().filter(each -> !each.startsWith(WHERE_QUESTIONS_LIVE))
                .forEach(elsewhere::add);

        assertEquals(Set.of(), elsewhere,
                "a question declared outside " + WHERE_QUESTIONS_LIVE + ", which moves where this"
                        + " compiler keeps what it can be asked");
    }

    /**
     * The scan read every class it found.
     *
     * <p>Asked before the arithmetic is. A class the scan cannot load drops out of the left-hand
     * side, and every equation below goes on balancing over a world one question smaller — which is
     * the failure this whole test exists to prevent, arriving through the thing that counts.
     */
    @Test
    void theScanReadEveryClassItFound() throws Exception {
        Set<String> fellShort = new TreeSet<>();
        if (DeclaredQuestions.scan()
                instanceof Covered.Partly<Class<?>>(List<Class<?>> _, List<Gap> gaps)) {
            gaps.forEach(each -> fellShort.add(each.toString()));
        }

        assertEquals(Set.of(), fellShort,
                "a class the scan could not load, so the vocabulary it counted is smaller than the"
                        + " one that is there");
    }

    /** And every question this project's own operations put over the corpus. */
    private static Set<String> reached() {
        Set<String> out = new TreeSet<>();
        for (ConformanceCorpus corpus : ConformanceCorpus.all()) {
            // Analysing a corpus and writing the report, which is `souther examples`.
            into(corpus.analyse().compilation(), out);

            // Offering an author the rows nothing covers, which is `souther examples --generate`,
            // with and without the rows at the edges a rule draws.
            ConformanceCorpus.Analysed generating = corpus.analyse();
            GeneratedRows.of(generating.compilation(), null, null, true,
                    new SourceRendering(corpus.names(), generating.compilation().texts())).text();
            GeneratedRows.of(generating.compilation(), null, null, false,
                    new SourceRendering(corpus.names(), generating.compilation().texts())).text();
            into(generating.compilation(), out);

            // Asking a compilation what a module declares, which is what the command line reads to
            // narrow what it is about to do to one behavior.
            ConformanceCorpus.Analysed asking = corpus.analyse();
            asking.compilation().modules()
                    .forEach(module -> asking.compilation().declaredBehaviors(module));
            into(asking.compilation(), out);

            // And a build, which measures what the rows already established and composes no values.
            // Its own operation and not a smaller `souther examples`: what it asks for at a line is a
            // different question from what a search of that line asks.
            into(Compiler.analyzedModules(corpus.sources(), ModulePath.EMPTY, new ArrayList<>(),
                    Adequacy.Asked.warningsAt(Adequacy.Level.WITNESS)), out);
        }
        // And the same operation over a model that reads a module this compile holds no source for,
        // which is the other world this compiler works in.
        into(readingWhatIsPublished(), out);
        return out;
    }

    /**
     * Analysing a model whose rules are partly in a module that was published rather than written
     * here, and writing the report.
     *
     * <p>An operation and not a question, like every other above: what is run is what this project
     * does over a source that imports a compiled module, and which questions that reaches is the
     * consequence. Asked question by question instead, this would be a stimulus invented to reach a
     * list, and the questions a published module brings in later would arrive uncovered while the
     * arithmetic still added up.
     *
     * <p><b>Its own operation and not another corpus.</b> A corpus is a set of sources handed over
     * together, so no {@code .sou} written beside the others makes one of them a module whose text
     * this compile cannot open — that is a fact about how the compile was started rather than about
     * what anybody wrote. So the model is built here, published, and read back.
     *
     * <p>The rule inside the published module is read at two calls, because that is what a reader
     * out here has to be able to be sent to: one rule, out of sight, met twice, and where a report
     * points for each is what the reading that met it says rather than what the rule does.
     */
    private static Compilation readingWhatIsPublished() {
        Map<String, ClassFileImage> published = Compiler.compile("""
                module lib exposing ( wide )

                let wide (n: Int): Bool = n > 10
                """, "lib.sou");
        Compilation compilation = Compilation.ofSources(List.of("""
                module reader

                import lib ( wide )

                data Low
                data High = { at: Int }

                behavior classify : (n: Int, m: Int) -> High | Low
                    constructs High

                let classify (n, m) = {
                    guard wide(n) else Low
                    guard wide(m) else Low
                    High { at = n }
                }
                """), ModulePath.of(published));
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        AdequacyReport.of(compilation);
        return compilation;
    }

    private static void into(Compilation compilation, Set<String> reached) {
        compilation.db().everyAnswer().keySet()
                .forEach(key -> reached.add(key.getClass().getName()));
    }

    /** What is written down as outside a run, whichever of the two reasons it is outside for. */
    private static Set<String> outsideARun() {
        Set<String> out = new TreeSet<>(NO_INPUT_IN_A_BATCH_RUN);
        out.addAll(NO_BATCH_CONSUMER.keySet());
        return out;
    }

    @Test
    void whatIsDeclaredAndNotReachedIsExactlyWhatIsWrittenDownAsOutsideARun() throws Exception {
        Set<String> unreached = new TreeSet<>(declared());
        unreached.removeAll(reached());

        assertEquals(outsideARun(), unreached,
                "a question this compiler declares that no run of the corpus asks. Either an"
                        + " operation above is missing one, or the question takes an input a batch"
                        + " run does not have, or nothing a batch run produces reads its answer —"
                        + " and the last two are the two sets beside it");
    }

    /**
     * And nothing is in both.
     *
     * <p>The other half of the partition. A question written down as one a run does not put, and then
     * put by one, is a line nobody would ever be made to remove — the sets above would go on excusing
     * a question that no longer needs excusing, and the next one filed beside it would inherit the
     * excuse. It is what makes the second set an account of where the compiler stands today rather
     * than a list things are added to.
     */
    @Test
    void andNothingReachedIsWrittenDownAsOutsideARun() {
        Set<String> both = new TreeSet<>(reached());
        both.retainAll(outsideARun());

        assertEquals(Set.of(), both,
                "a question a run reaches and this says is outside one");
    }

    /**
     * And the two reasons for being outside a run are two.
     *
     * <p>A question in both would be excused twice and refuted by neither: removing the entry that
     * has stopped being true leaves the other one standing, and nothing says which of the two was the
     * reason.
     */
    @Test
    void andNoQuestionIsOutsideARunForBothReasons() {
        Set<String> both = new TreeSet<>(NO_INPUT_IN_A_BATCH_RUN);
        both.retainAll(NO_BATCH_CONSUMER.keySet());

        assertEquals(Set.of(), both,
                "a question written down as one a batch run cannot put and as one whose answer"
                        + " nothing reads");
    }

    /**
     * And every question a run reaches is one the scan found.
     *
     * <p>What holds the discovery to the vocabulary. A key declared outside the package this scans
     * would leave the arithmetic above adding up over a smaller world, and the question would be
     * unwatched for exactly the reason this test exists.
     */
    @Test
    void andEveryQuestionAskedIsOneTheScanFound() throws Exception {
        Set<String> unknown = new TreeSet<>(reached());
        unknown.removeAll(declared());

        assertEquals(Set.of(), unknown,
                "a question asked whose key the scan of the query package did not find");
    }
}
