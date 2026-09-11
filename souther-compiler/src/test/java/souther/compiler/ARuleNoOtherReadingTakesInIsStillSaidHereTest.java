package souther.compiler;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A rule stating an end past the end of an order, said by whichever reading is its only one.
 *
 * <p>Such a rule is read: what it says is that nothing satisfies it. Where the declaration is
 * refused for that, this report is never produced, and naming the rule here as one nothing could
 * read would send an author after a bound the compiler understood perfectly.
 *
 * <p>Where nothing refuses it, this report is its only reader. A size is that case: the reading that
 * refuses a declaration for holding no value is over the positions a value has, and a size is a
 * number taken of one, so no end of a size reaches it. Silence here would leave the rule unsaid
 * everywhere — which is what a reading justified by "something else refuses this" costs when the
 * justification is borrowed by a caller it is not true of.
 */
class ARuleNoOtherReadingTakesInIsStillSaidHereTest {

    private static String reportOf(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).human(SourceRendering.namedByIdentity(SourceLayouts.NONE));
    }

    /**
     * A size bounded past the greatest whole number, which nothing else takes in.
     *
     * <p>Said as a question nothing answered, and it names the rule. Which reading was short of it
     * is this compiler's own business and is not a second thing to tell an author: what they can
     * act on is that a rule of theirs raised a question about the values here and no reading of the
     * model answered it. It used to be said twice — once here without naming the rule, because a
     * value reading files what it could not take in under the position rather than under the
     * clause.
     */
    @Test
    void aSizeBoundPastTheEndOfTheWholeNumbersIsNamedAsUnaccountedFor() {
        String human = reportOf("""
                module example.sized

                data Tag = String
                    invariant long = String.length(value) > 9223372036854775807

                data Short
                data Long

                behavior weigh : (t: Tag) -> Short | Long
                let weigh (t) = if String.length(t.value) > 3 then Long else Short
                """);

        assertTrue(human.contains("not accounted for: invariant Tag (long)"), human);
        assertTrue(human.contains("which values may stand at t"), human);
    }

    /**
     * And an ordinary size bound is read, so nothing is said about it.
     *
     * <p>The control. Without it the assertion above holds of a reading that calls every size rule
     * unread, which is the answer this is meant to tell apart.
     */
    @Test
    void anOrdinarySizeBoundIsNotNamedAsUnaccountedFor() {
        String human = reportOf("""
                module example.sized

                data Tag = String
                    invariant long = String.length(value) > 2

                data Short
                data Long

                behavior weigh : (t: Tag) -> Short | Long
                let weigh (t) = if String.length(t.value) > 3 then Long else Short
                """);

        assertFalse(human.contains("not accounted for: invariant Tag (long)"), human);
        assertFalse(notReadAbout(human, "t"), human);
    }

    /**
     * Whether any {@code not read} line of {@code block} is about {@code position}.
     *
     * <p>Asked as a line rather than as a prefix. A finding about a rule names the rule first and
     * the position after it, and one about a position names the position — so a test matching
     * `+not read: <position>+` stopped meaning anything for the first kind rather than failing,
     * which is a negative assertion that passes because the words moved.
     */
    private static boolean notReadAbout(String block, String position) {
        return block.lines().anyMatch(line -> line.contains("not read:")
                && (line.contains("not read: " + position + " ")
                        || line.contains("about `" + position + "`")));
    }
}
