package souther.compiler.report;

import org.junit.jupiter.api.Test;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A page shows the rules of what it shows, and knows about no others.
 *
 * <p>Where a report sends a reader for a rule with no name is worked out when the report is
 * assembled, and kept beside the account it is about. Which account that is, is the question this
 * holds: a page is about one behavior, so the rules it may send a reader to are that behavior's.
 *
 * <p><b>What it costs to get this wrong is not an extra entry.</b> A page is a value, so a place
 * gathered from a behavior the page does not show is a place that moves when that behavior's source
 * moves — and a report narrowed to the page then changes for an edit to something nobody reading it
 * can see. Which is the shape this whole change is about, arriving at the boundary a filtered report
 * is taken at.
 */
class APageShowsTheRulesOfWhatItShowsTest {

    /**
     * Two behaviors with a rule each, and the second written after the first — so an edit above the
     * second moves it and leaves the first where it was.
     *
     * <p>The second's rule is one this compiler does not read to the end, so what a report says
     * about it is a finding about the rule and a sentence sending a reader to it. A pair of rules
     * that both come to lines says nothing here: what a page gathers from a line is already its
     * own, and the entry a page could take from another is the one a finding brings.
     */
    private static final String MODEL = """
            module m

            data Low
            data High

            behavior f : (n: Int) -> Low | High

            let f (n) = if n > 10 then High else Low

            %sbehavior g : (o: Int) -> Low | High

            let g (o) = if Int.multiply(o, o) > 20 then High else Low
            """;

    @Test
    void movingARuleOfABehaviorAPageDoesNotShowLeavesThePageAlone() {
        AdequacyReport before = reportOf(MODEL.formatted(""));
        AdequacyReport after = reportOf(MODEL.formatted("// a line written above the second\n"));

        // The page is about `f`, and `f` has rules a reader can be sent to. Asked of a page with
        // none, everything below would hold of a report that gathers nothing at all.
        assertFalse(before.modules().get(0).behaviors().stream()
                        .filter(each -> each.name().equals("f"))
                        .findFirst().orElseThrow().rulePlaces().isEmpty(),
                "the page under test sends a reader to a rule with no name");
        // And the edit moved something: the whole report is not the same value.
        assertEquals(false, before.equals(after),
                "the edit moved the rule of the behavior the page does not show");

        assertEquals(before.only("m", "f"), after.only("m", "f"),
                "a page about `f` says nothing that moves when `g` does");
    }

    private static AdequacyReport reportOf(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation);
    }
}
