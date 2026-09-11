package souther.compiler.partition;

import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.BorderAssessment;
import souther.compiler.query.Compilation;
import souther.compiler.query.PartitionEvidence;
import souther.compiler.report.AdequacyReport;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A type writing about both of its numbers is measured at both of them.
 *
 * <p>A {@code String} has two — its own order, and the length of it — and a rule is about one of
 * them. Neither is a candidate the other beats: an axis at each is a run of classes and lines over
 * that number's own values, so a rule about the length and a rule about the order both draw the
 * line they draw, and there is nothing here to choose.
 *
 * <p>Which is what the ends already say. A clause records the number it placed its end on, so
 * filing it takes no decision; a reading that had to pick one number for the position had to drop
 * every end on the other, and an author was shown a model that draws one line where theirs draws
 * two.
 */
class ATypeWritingAboutBothOfItsNumbersMeasuresBothTest {

    /**
     * One type writing about both of its numbers, and a record bounding one of them beside it.
     *
     * <p>Both in one module because the second is only right if the first is: what a rule reaching
     * `Code` from `Holder` does is settled by what `Code`'s own rules already did.
     */
    private static final String BOTH = """
            module owned

            data Code = String
                invariant value >= "m"
                invariant String.length(value) >= 3

            data Holder = { c: Code } invariant String.length(c.value) >= 5

            data Ok = { size: Int }

            behavior onCode : (v: Code) -> Ok
                constructs Ok
            let onCode (v) = Ok { size = String.length(v.value) }

            behavior onHolder : (v: Holder) -> Ok
                constructs Ok
            let onHolder (v) = Ok { size = String.length(v.c.value) }

            example onCode   | (Code("zzz")) -> Ok { size = 3 }
            example onHolder | (Holder { c = Code("zzzzz") }) -> Ok { size = 5 }
            """;

    /** Both of the type's own rules draw their line, and neither goes out unread. */
    @Test
    void bothOfTheTypesOwnRulesAreRead() {
        assertEquals(List.of(), notReadIn(BOTH, "onCode"),
                "each clause is about a number the position has, and each is measured");
    }

    /**
     * And each rule leaves its line where it drew it.
     *
     * <p>Neither number is divided into classes — everything outside a bound is refused at
     * construction — so what there is to see of the two measures is the two edges, and both are
     * there. Measured at one number, whichever rule was about the other left no edge at all.
     */
    @Test
    void eachNumberKeepsTheLineItsRuleDraws() {
        assertEquals(List.of("String.length(v) = 3", "v = m"), linesIn(BOTH, "owned", "onCode"),
                "the length stops at three and the order at m");
    }

    /**
     * A rule arriving from the value the position sits in reaches the number it is about.
     *
     * <p>What such a rule states is where one number stops, and it states it about that number.
     * There is no standing to break a tie at and none to be overruled by: the record's clause takes
     * its end to the length beside the type's own, and the type's clause about the order keeps its
     * line.
     */
    @Test
    void aRuleFromOutsideReachesTheNumberItIsAbout() {
        assertEquals(List.of("String.length(v.c) = 5", "v.c = m"), linesIn(BOTH, "owned", "onHolder"),
                "the record's end meets the type's on the length, and the order keeps its own");
        assertEquals(List.of(), namedIn(BOTH, "onHolder"),
                "and none of the three clauses is reported as one nothing could read");
    }

    /**
     * A type writing about both, where one of the two draws no comparison.
     *
     * <p>`Code` is written about its own order and about its length, and the length rule places no
     * end: a disequality states where nothing stops. What it does do is take a value away from the
     * end of the range, which is an end at this position that no comparison put there.
     */
    private static final String ONE_OF_THEM_PLACES_NO_END = """
            module noend

            data Code = String
                invariant value >= "m"
                invariant String.length(value) /= 0

            data Holder = { c: Code } invariant c.value >= "n"

            data Ok = { size: Int }

            behavior onHolder : (v: Holder) -> Ok
                constructs Ok
            let onHolder (v) = Ok { size = String.length(v.c) }

            example onHolder | (Holder { c = Code("zzz") }) -> Ok { size = 3 }
            """;

    /** An end nothing compared is an end on the number it was placed on, like any other. */
    @Test
    void anEndNothingComparedReachesItsOwnNumber() {
        assertEquals(List.of("String.length(v.c) = 1", "v.c = n"),
                linesIn(ONE_OF_THEM_PLACES_NO_END, "noend", "onHolder"),
                "the disequality took the nought away, so the length starts at one and is measured"
                        + " there beside the order the two clauses about it stop at");
        assertEquals(List.of(), notReadIn(ONE_OF_THEM_PLACES_NO_END, "onHolder"),
                "and no end at the position is left without a number to be on");
    }

    /**
     * A format beside a length: the length keeps its line, and the format answers for itself.
     *
     * <p>A rule about the strings a position holds is a rule about the position's own value, and a
     * rule about how long one is is a rule about its length. Two numbers and two rules, so the line
     * at two is drawn — where a position with one number to measure had to give it up to say
     * anything about the strings.
     *
     * <p>The word beside the format is its own reading's and not this one's: the strings
     * {@code [0-9]+} admits are not a set this compiler works out, which it would answer whether or
     * not a length rule stood beside it.
     */
    @Test
    void aFormatBesideALengthLeavesTheLengthItsLine() {
        String source = """
                module formatted

                data C = String
                    invariant String.length(value) >= 2 && String.matches("[0-9]+", value)

                data Ok = { size: Int }

                behavior onC : (v: C) -> Ok
                    constructs Ok
                let onC (v) = Ok { size = String.length(v) }

                example onC | (C("123")) -> Ok { size = 3 }
                """;

        assertEquals(List.of("v: EXACT_VALUES_TOO_COSTLY"), notReadIn(source, "onC"),
                "only the format is unread, and for a reason of its own reading");
        assertTrue(report(source).contains("String.length(v) = 2"),
                "and the length keeps the line its rule draws");
    }

    /**
     * The same, with the rule each finding is about.
     *
     * <p>Which authored clause a finding names is what a reader looks a finding up by. Held to the
     * coordinate and the word alone, a reading that named one clause twice and dropped another
     * would pass.
     */
    private static List<String> namedIn(String source, String behavior) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).modules().get(0).behaviors().stream()
                .filter(each -> each.name().equals(behavior))
                .flatMap(each -> each.partition().notRead().stream())
                .map(each -> ((PartitionEvidence.NotRead.ARule) each).rule() + " at " + each.at()
                        + ": " + each.reason())
                .toList();
    }

    /** Every line {@code behavior} draws, by the label a report shows it under, in order. */
    private static List<String> linesIn(String source, String module, String behavior) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        Map<String, List<BorderAssessment>> read =
                Adequacy.readingsOf(compilation.db(), module);
        assertNotNull(read, () -> module + " is a module of the source under test");
        List<BorderAssessment> lines = read.get(behavior);
        assertNotNull(lines, () -> behavior + " draws lines in " + module + ": " + read.keySet());
        return lines.stream().map(BorderAssessment::label).sorted().toList();
    }

    private static String report(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).human(SourceRendering.namedByIdentity(compilation.texts()));
    }

    /** What {@code behavior} left unread, as the number it is about and the reason. */
    private static List<String> notReadIn(String source, String behavior) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).modules().get(0).behaviors().stream()
                .filter(each -> each.name().equals(behavior))
                .flatMap(each -> each.partition().notRead().stream())
                .map(each -> each.at() + ": " + each.reason()).toList();
    }
}
