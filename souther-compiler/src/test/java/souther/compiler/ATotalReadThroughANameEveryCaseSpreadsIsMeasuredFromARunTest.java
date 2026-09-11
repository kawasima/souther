package souther.compiler;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A total whose values are read through a name every case of a sum spreads is measured from a run of
 * them.
 *
 * <p>The cases of the sum spread the field the total adds up, so the term's path names a position of
 * the sum and no case of it. That spelling is what the reading gives it and it is right — the rule
 * is about the shared name — and what has to hold is that the values a run walks are gathered under
 * the same name the term stands at, wherever the value at each occurrence turns out to be a case.
 *
 * <p>It did not. The walk over a row's values asked where a value is <em>written</em>, which at that
 * name is nowhere, so every point of the line came back undecided with two sentences: that the walk
 * reached no value there to read, and that nothing composed a row seen reaching it. The row standing
 * exactly on the point was in the file the whole time.
 *
 * <p><b>Two models and the same answer.</b> One spreads the amount through the cases and one puts it
 * on the element, and they owe the same points and meet the same ones. Held as a count alone, both
 * could go wrong together and agree about it; held as the points themselves, what each owes is
 * checked before the two are compared.
 */
class ATotalReadThroughANameEveryCaseSpreadsIsMeasuredFromARunTest {

    /** The amount spread through the cases of a sum, with a row exactly on the point. */
    private static final String SPREAD = """
            module example.trip

            data Amount = Int
                invariant value >= 0

            data Common = { amount: Amount }

            data Card = { ...Common, issuer: Issuer }
            data Cash = { ...Common, note: Note }

            data Issuer = String
                invariant String.length(value) >= 1

            data Note = String
                invariant String.length(value) >= 1

            data Method = Card | Cash

            data Entry = { method: Method }

            data Many
            data Few

            behavior decide : (entries: List<Entry>) -> Many | Few

            let decide (entries) =
                if List.sum(List.map(one -> one.method.amount.value, entries)) >= 100
                then Many else Few

            example decide
                | "on the point" : ([ Entry { method = Card { amount = Amount(100), issuer = Issuer("x") } } ]) -> Many
            """;

    /** The same rule with the amount on the element, which is the shape that always worked. */
    private static final String ON_THE_ELEMENT = """
            module example.trip

            data Amount = Int
                invariant value >= 0

            data Entry = { amount: Amount }

            data Many
            data Few

            behavior decide : (entries: List<Entry>) -> Many | Few

            let decide (entries) =
                if List.sum(List.map(one -> one.amount.value, entries)) >= 100
                then Many else Few

            example decide
                | "on the point" : ([ Entry { amount = Amount(100) } ]) -> Many
            """;

    /**
     * What the line owes, worked out from the model rather than read off a run.
     *
     * <p>A comparison at a hundred draws four points — the value it names, the one below it, and the
     * two sides — and the file holds one row, whose entries come to exactly the hundred. So the point
     * that row is on is met and the other three are owed, whichever way the amount is declared.
     *
     * <p>Named as the gaps rather than as which point was met, because that is what a report prints:
     * a point a row covers is not written out. The two together say which one it was — four points,
     * three of them owed and none of those the one the row is on.
     */
    private static final List<String> OWED = List.of(
            "! no row is at the OFF point",
            "! no row is at an IN point",
            "! no row is at an OUT point");

    private static final String COUNTED = "1/4";

    /**
     * The spread model owes what the rule says it owes, and meets the point its row is on.
     *
     * <p>The count and the points, because neither says the other. Three gaps and a count of four
     * leave exactly one met and say it is not one of the three; the count alone would go on reading
     * the same while a point moved from one kind to another.
     */
    @Test
    void aTotalReadThroughASharedNameMeetsThePointItsRowIsOn() {
        String report = report(SPREAD);

        assertEquals(COUNTED, obligationsIn(report),
                () -> "one row is on the point the comparison names, and the other three are"
                        + " owed: " + report);
        assertEquals(OWED, borderGapsIn(report),
                () -> "and the three owed are the ones the row is not on: " + report);
    }

    /**
     * And no point of the line is one this compiler could not look at, whatever the points are.
     *
     * <p>The other half of what a point that was not met can be. An owed point says the model has
     * nothing there; an undecided one says this compiler could not look — and the walk that could
     * not look is what put every point of this line into the second while a row sat on one of them.
     *
     * <p>Said of the mark and not of the sentence. Which of the two a point is is what the mark
     * carries, and a check written against the words would go green the day either sentence is
     * reworded, over a line every point of which had gone back to being undecidable. And said of
     * whatever the border holds rather than of the three above: this is the property the line has,
     * where the list is what this model happens to owe.
     */
    @Test
    void noPointOfTheLineIsLeftUndecided() {
        String report = report(SPREAD);

        assertEquals(List.of(), borderGapsIn(report).stream()
                        .filter(each -> each.startsWith(UNDECIDED)).toList(),
                () -> "every point the line is short of is one the model owes a row at: " + report);
    }

    /** How a report marks a point it could not be told about, beside {@code !} for one that is
     *  owed. */
    private static final String UNDECIDED = "?";

    /**
     * And the model that puts the amount on the element comes to the same thing.
     *
     * <p>Compared after each has been held to what its own rule owes, so this says the sum changes
     * nothing rather than that the two agree. Only the behavior's own line: the models declare
     * different types, so what their declarations owe is different and is not the same question.
     */
    @Test
    void theSumChangesNothingAboutWhatTheLineOwes() {
        String spread = report(SPREAD);
        String element = report(ON_THE_ELEMENT);

        assertEquals(COUNTED, obligationsIn(element),
                () -> "the amount on the element owes the same four points: " + element);
        assertEquals(OWED, borderGapsIn(element),
                () -> "and is short of the same three: " + element);
        assertEquals(borderGapsIn(spread), borderGapsIn(element),
                "the cases spreading the name change nothing about what the line owes");
        assertEquals(obligationsIn(spread), obligationsIn(element),
                "nor about how much of it is met");
    }

    /** What the border section says a behavior's obligations came to. */
    private static String obligationsIn(String report) {
        return report.lines()
                .filter(each -> each.contains("border") && each.contains("obligations"))
                .map(each -> each.substring(each.indexOf("obligations") + "obligations ".length()))
                .findFirst().orElseThrow(() -> new AssertionError(report));
    }

    /**
     * The points of the border a row is short of, as the report writes them.
     *
     * <p>Without where the comparison is written or what the term is spelled as, which are the two
     * things that differ between the models by construction. What is left is which point of which
     * kind went unmet, which is what the two are being held to.
     *
     * <p><b>A border with no gaps and a border this could not find are two answers.</b> Both are no
     * lines, and a caller reading the first out of the second would say a line is fully covered
     * because the section it is in moved. So the section not being there is said here, where it can
     * still be told apart, rather than handed on as an empty list.
     */
    private static List<String> borderGapsIn(String report) {
        List<String> gaps = new ArrayList<>();
        boolean found = false;
        boolean inBorder = false;
        for (String line : report.lines().toList()) {
            String said = line.strip();
            if (line.startsWith("    ") && !line.startsWith("     ")) {
                inBorder = said.startsWith("border");
                found |= inBorder;
                continue;
            }
            if (inBorder && (said.startsWith("!") || said.startsWith("?"))) {
                int at = said.indexOf(" (comparison@");
                gaps.add(at < 0 ? said : said.substring(0, at));
            }
        }
        if (!found) {
            throw new AssertionError("this reads the points of a border out of the report, and"
                    + " there is no border section in it: " + report);
        }
        return gaps;
    }

    private static String report(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).human(SourceRendering.namedByIdentity(SourceLayouts.NONE));
    }
}
