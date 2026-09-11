package souther.bench;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nothing works out how far a measurement got except the one place that turns it into a word.
 *
 * <p>{@code MeasurementStatus} is four words for a document. It was also the compiler's own answer
 * to how much of a measure was made, which made it a lattice: every producer worked its word out
 * from a fact it then let go of, and every parent worked the same word out again from the fields
 * beside its children (issue #953). The fix was not to add a field but to leave the word with the
 * only reader that needs one.
 *
 * <p>So this is the rule the change is worth keeping: <b>a measurement is asked what it is, and only
 * a renderer asks what to call it.</b> Held over the compiled classes and over the source, because
 * the two catch different things — bytecode says who reaches what, and source says what somebody
 * wrote down.
 *
 * <p><b>And the defences that are gone.</b> Most of what this replaces was a check over a state the
 * type still let anybody write: a status paired with the wrong reason, a flag beside a status that
 * had to agree with it, a boolean recovering a state the enum had no word for. Each is named here
 * so that reintroducing one is a decision somebody makes rather than a line somebody adds.
 */
class OnlyAProjectionSaysHowFarAMeasurementGotTest {

    private static final String STATUS = "souther.compiler.observe.MeasurementStatus";

    /**
     * The classes that may read the four words, named one at a time.
     *
     * <p>{@code ReportMeasurement} is the projection and {@code AdequacyReport} is what prints what
     * it makes; the enum's own file names its constants. Everything else that wants to know what a
     * measurement is asks the measurement.
     *
     * <p><b>Named and not a package.</b> Excusing {@code souther.compiler.report} as a whole would
     * let a renderer added later switch over the five states itself, which is the one thing this is
     * for — the word a document uses is decided once, and a second decision is not caught by being
     * made next door to the first.
     */
    private static final Set<String> MAY_READ_THE_WORDS = Set.of(
            "souther.compiler.report.ReportMeasurement",
            "souther.compiler.report.AdequacyReport",
            STATUS);

    @Test
    void onlyTheProjectionReadsTheWordsADocumentUses() throws IOException {
        Set<String> readers = new LinkedHashSet<>();
        int reached = 0;
        for (Compiled.Site site : Compiled.sites()) {
            if (site.owner().equals(STATUS)) {
                reached++;
            }
            // By the class the site is in, with a nested one answering for the file it is written
            // in: a lambda or a record inside the projection is the projection.
            String from = site.from();
            int nested = from.indexOf('$');
            String outer = nested < 0 ? from : from.substring(0, nested);
            if (site.owner().equals(STATUS) && !MAY_READ_THE_WORDS.contains(outer)) {
                readers.add(from);
            }
        }
        // Reading a constant is a field read and not a call, so a walk over calls alone meets no
        // reader of an enum at all and passes by seeing nothing. Said before the rule, because that
        // is exactly how this check spent its first afternoon.
        assertTrue(reached > 0,
                "nothing in the compiled classes reaches these words, so the rule below is held"
                        + " over nothing — ask Compiled for a way of reaching that can see it");
        assertEquals(Set.of(), readers,
                "a measurement's word is the document's, and these worked it out for themselves");
    }

    /**
     * The methods and fields the old model needed, each gone, asked of the file that declared it.
     *
     * <p>Named rather than counted, and asked where it was written rather than of the whole tree. A
     * count that only went up would pass a rewrite that dropped one and added another; a grep over
     * everything would answer about the language's own {@code partial} and about a {@code Counting}
     * that has an observation of its own. What is worth holding is that no one of these comes back
     * to the type that had it — each was a way of saying how far a measurement got from something
     * other than the measurement.
     */
    @Test
    void nothingRecoversAMeasurementsStateFromSomethingBesideIt() throws IOException {
        record Gone(String file, String written, String why) {}
        List<Gone> gone = List.of(
                new Gone("observe/MeasureReason.java", "boolean somethingWasUnreadable",
                        "a boolean recovering the state the four words had no room for"),
                new Gone("observe/MeasureReason.java", "MeasurementStatus status()",
                        "a reason answering which kind of no-number it is, rather than being it"),
                new Gone("observe/MeasurementStatus.java", "MeasurementStatus and(",
                        "a meet that discarded what the two sides went without"),
                new Gone("query/PartitionEvidence.java", "boolean truncated",
                        "a flag beside a status, each free to say the other was wrong"),
                new Gone("query/Adequacy.java", "MeasurementStatus observation",
                        "a second status beside the first, for the one measure that needed two"),
                new Gone("report/AdequacyReport.java", "private static boolean fellShort",
                        "a parent folding its children back into the word they came from"));

        List<String> found = new ArrayList<>();
        for (Gone each : gone) {
            Path at = Reactor.root().resolve(
                    "souther-compiler/src/main/java/souther/compiler/" + each.file());
            String source = code(Files.readString(at, StandardCharsets.UTF_8));
            if (source.contains(each.written())) {
                found.add(each.file() + ": " + each.written() + " — " + each.why());
            }
        }
        assertEquals(List.of(), found, "what the old model needed, back again");

        // And the file the whole arrangement rested on. A check over a status and a reason that
        // could be written out of step is a check somebody has to keep running; three reason types
        // is the same rule with nothing left to run.
        assertTrue(Files.notExists(Reactor.root().resolve(
                        "souther-compiler/src/main/java/souther/compiler/query/Unavailable.java")),
                "the check over a status and a reason held beside it is back");
    }

    /** A source with its comments taken out, since what these rules are about is what runs. The
     *  javadoc says what each of them replaced, and saying so is not doing it. */
    private static String code(String source) {
        StringBuilder out = new StringBuilder();
        int at = 0;
        while (at < source.length()) {
            int block = source.indexOf("/*", at);
            int line = source.indexOf("//", at);
            int next = block < 0 ? line : line < 0 ? block : Math.min(block, line);
            if (next < 0) {
                out.append(source, at, source.length());
                break;
            }
            out.append(source, at, next);
            at = next == block
                    ? end(source, source.indexOf("*/", next), 2)
                    : end(source, source.indexOf('\n', next), 0);
        }
        return out.toString();
    }

    private static int end(String source, int at, int past) {
        return at < 0 ? source.length() : at + past;
    }

    /**
     * A finding is made from a measurement, and from nothing else.
     *
     * <p>What a finding carries decides whether a build may refuse over it, so handing one the wrong
     * account is a build going quiet about work somebody is owed. It used to take a
     * {@code WeakeningSet} beside the subject, and one method that produced several findings worked
     * one out at the top and gave it to all of them — the signature's, which is the union of its
     * output's and every input's, so a case the output was counted for in full read as undecided
     * over an input's unreadable row.
     *
     * <p>The factories take the measurement instead, which leaves no argument to pass the wrong
     * thing in: a caller hands over the one it is looking at. This holds the other half — that the
     * canonical constructor, which a public record cannot hide, is not how anybody makes one.
     *
     * <p>The three take a {@link souther.compiler.query.FindingSubject} and not a behavior's name.
     * What a finding is about is a value rather than a word, because not every finding is about a
     * behavior. The overloads that take a name hand one over and make nothing, which is why they are
     * not here.
     *
     * <p>Two of them and not one, because what finds a finding is not always a measure. A point of
     * an authored line is answered by {@link souther.compiler.query.ObligationCoverage}, which folds
     * the readings and has no status of its own, and it is taken whole for the reason a measure is:
     * there is no argument here to pass a set worked out somewhere else.
     */
    @Test
    void aFindingIsMadeFromTheMeasurementThatFoundIt() throws IOException {
        String finding = "souther.compiler.query.Adequacy$Finding";
        Set<String> makers = new LinkedHashSet<>();
        for (Compiled.Site site : Compiled.sites()) {
            if (site.makesA(finding)) {
                makers.add(site.at());
            }
        }
        assertEquals(Set.of(
                        "souther.compiler.query.Adequacy$Finding#by("
                                + "Lsouther/compiler/query/FindingSubject;"
                                + "Lsouther/compiler/query/Measure;"
                                + "Lsouther/compiler/query/About;)"
                                + "Lsouther/compiler/query/Adequacy$Finding;",
                        "souther.compiler.query.Adequacy$Finding#by("
                                + "Lsouther/compiler/query/FindingSubject;"
                                + "Lsouther/compiler/query/ObligationCoverage;"
                                + "Lsouther/compiler/query/About;)"
                                + "Lsouther/compiler/query/Adequacy$Finding;",
                        // A third thing that finds one: the reading of a body's decision. What it
                        // went without is neither a measure's status nor a fold of the readings of
                        // a line — a run it could not place among the rules and a row nothing
                        // watched each leave a rule nothing was seen taking as one a row may
                        // already take — and it is taken whole for the reason the two above are.
                        "souther.compiler.query.Adequacy$Finding#by("
                                + "Lsouther/compiler/query/FindingSubject;"
                                + "Lsouther/compiler/query/DecisionEvidence;"
                                + "Lsouther/compiler/query/About;)"
                                + "Lsouther/compiler/query/Adequacy$Finding;",
                        "souther.compiler.query.Adequacy$Finding#noticed("
                                + "Lsouther/compiler/query/FindingSubject;"
                                + "Lsouther/compiler/query/About;)"
                                + "Lsouther/compiler/query/Adequacy$Finding;"),
                // None of them takes a place. What a finding says is what a reading came to, and
                // where a report about it is shown is asked of the module that wrote the code —
                // so there is no argument here to hand one over.
                makers,
                "what makes a finding rather than asking the measurement that found it");
    }

    /**
     * A measurement made in part, or one that could not be finished, cannot be built empty.
     *
     * <p>The one invariant left that a constructor still runs, and the only one worth running: a
     * measurement saying it is weaker than complete and carrying nothing is the whole of what this
     * change was about, and a type cannot say "non-empty" on its own.
     */
    @Test
    void theOneCheckLeftIsTheOneTheTypeCannotMake() throws IOException {
        String measurement = Files.readString(
                Reactor.root().resolve("souther-compiler/src/main/java/souther/compiler/query"
                        + "/Measurement.java"), StandardCharsets.UTF_8);
        assertTrue(measurement.contains("record Partial<T>(T value, WeakeningSet by)"),
                "a partial measurement carries what weakened it");
        assertTrue(measurement.contains("record FailedToMeasure<T>(FailureReason why, WeakeningSet by)"),
                "and so does one that could not be finished");
        assertEquals(2, count(measurement, "by == null || by.isEmpty()"),
                "each of the two refuses an empty one where it is built");
    }

    private static int count(String text, String of) {
        int n = 0;
        for (int at = text.indexOf(of); at >= 0; at = text.indexOf(of, at + of.length())) {
            n++;
        }
        return n;
    }

}
