package souther.compiler;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.GeneratedRows;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code --generate} writes rows, and a written row carries a name — so the generator is a producer
 * of names and is held to what a name is (<<a-row-name-is-unique-within-its-behavior>>).
 *
 * <p>Being distinct within one run is the smaller half. The half that decides the design is that a
 * row's name says which row it is rather than what this run of the generator happened to hand it: a
 * candidate is composed for a cell of the partition or for a line a rule draws, and it is named for
 * that. What it turns out to settle beside that is a fact about the run — a row written elsewhere can
 * meet a line this row also sits on, and the line stops being offered — and naming a row for it made
 * the name move when nothing about the row had.
 *
 * <p>That is what the second test here pins, and it pins the other direction too: the set a row
 * settles is allowed to change. The two are separate facts and only one of them is an identity.
 */
class AGeneratedRowIsNamedForWhatItWasComposedForTest {

    private static final String LIMIT = """
            module example.limit

            data Amount = Int
                invariant value >= 0

            data Tier = Gold | Silver
            data Draft = { cost: Amount, tier: Tier }
            data Submitted = { cost: Amount }
            data Rejected = { reason: String }

            behavior submit : (request: Draft) -> Submitted | Rejected
                constructs Submitted, Rejected

            let submit (request) = {
                guard request.cost.value <= 100 else Rejected { reason = "over" }
                Submitted { cost = request.cost }
            }
            """;

    /** A row for one of the classes, written by hand — and nothing about the rows below it. */
    private static final String AND_A_ROW = LIMIT + """

            example submit
                | "a silver draft under the ceiling is submitted"
                    : (Draft { cost = Amount(0), tier = Silver })
                    -> Submitted { cost = Amount(0) }
            """;

    /** {@code | "name" : (inputs)} as the block writes it, over lines the formatter may have wrapped. */
    private static final Pattern OFFERED = Pattern.compile("\\|\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*\\n?\\s*:");

    private static String block(String source, boolean boundaries) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        Map<String, Adequacy.Filling> filling = Adequacy.generatedOf(compilation.db(), compilation.modules().get(0));
        assertNotNull(filling, "the model under test compiles");
        return GeneratedRows.of(Adequacy.offeredFor(compilation.db(),
                        souther.compiler.query.OfferingRequest.overTheModule(
                                compilation.modules().get(0), boundaries)),
                Map.of(), SourceRendering.namedByIdentity(SourceLayouts.NONE), compilation.db()).text();
    }

    /** Two minimum edges of one behavior, which compose one row between them. */
    private static final String POLICY = """
            module example.policy

            data Rate = Int
                invariant nonNegative = value >= 0

            data Cap = Int
                invariant nonNegative = value >= 0

            data Policy =
                { rate: Rate
                , cap: Cap
                }

            behavior fee : (days: Int, policy: Policy) -> Int

            let fee (days, policy) = {
                let accrued = days * policy.rate.value

                if accrued > policy.cap.value then policy.cap.value else accrued
            }

            example fee
                | "under the cap" : (5, Policy { rate = Rate(10), cap = Cap(500) }) -> 50
            """;

    /** The same, and a row meeting `policy.cap = 0` and no other line. */
    private static final String POLICY_AND_A_ROW_AT_THE_CAP = POLICY + """
                | "a cap of nothing caps everything" : (5, Policy { rate = Rate(10), cap = Cap(0) }) -> 0
            """;

    /** How many rows the block writes, named or not. A row the formatter wrapped is still one. */
    private static int rows(String block) {
        return (int) block.lines().filter(line -> line.startsWith("    | ")).count();
    }

    /** The names the block offers, in the order it writes them. */
    private static List<String> names(String block) {
        List<String> found = new ArrayList<>();
        Matcher m = OFFERED.matcher(block.replace("//", ""));
        while (m.find()) {
            found.add(m.group(1));
        }
        return found;
    }

    @Test
    void noTwoRowsOfOneBehaviorAreOfferedUnderOneName() {
        String block = block(LIMIT, true);
        List<String> offered = names(block);

        assertEquals(4, rows(block),
                "two rows for the classes and two at the points of the border:\n" + block);

        // Two of the four classes are named, and the two that are not are where the named rows
        // stand: the row at the top of the range holds the first tier, and the row for the other
        // tier holds the bottom of the range. A row is named for what it was composed for, so
        // neither name moves onto the row that answers it — what a class is offered under is the
        // row a person writes, and which class that row also settles is in the report.
        assertEquals(List.of("request.cost=100 < x", "request.tier=Silver"),
                offered.stream().sorted().toList(), block);

        Set<String> distinct = new LinkedHashSet<>(offered);
        assertEquals(offered.size(), distinct.size(),
                "a name says which row it is, so no two rows share one: " + offered);
    }

    /**
     * A class stays offered when a row settles something else it also sat on.
     *
     * <p>Offered and not named, which are two things and only the first is what this is about. A
     * row that answers one thing carries its name; one that answers two carries neither and says
     * what it fills over itself — and the class over the ceiling is answered by the same values as
     * the arm the guard sends a run down, so what it is offered under moves from the name to the
     * line above it. What would be a defect is the class going unmentioned.
     */
    @Test
    void aRowKeepsWhatItIsOfferedForWhenAnotherRowSettlesWhatItAlsoSatOn() {
        String before = block(LIMIT, true);
        String after = block(AND_A_ROW, true);

        String over = "request.cost=100 < x";
        assertTrue(offeredFor(before).contains(over),
                "the class is owed before anything is written: " + before);
        assertTrue(offeredFor(after).contains(over),
                "and is still what a row is offered for, beside the row written by hand: " + after);
    }

    /** What the block says its rows are for, whether as a name or as a line over an unnamed row. */
    private static List<String> offeredFor(String block) {
        List<String> found = new ArrayList<>(names(block));
        block.lines().filter(line -> line.contains("fills "))
                .map(line -> line.substring(line.indexOf("fills ") + "fills ".length()))
                .forEach(found::add);
        return found;
    }

    /**
     * The other direction: what a row settles is the generation's to change. The line at the bottom of
     * the range is offered while nothing meets it and is not offered once a row does, and neither says
     * anything about the row named for the cell above.
     */
    @Test
    void whatIsOfferedBesideItIsAllowedToChange() {
        List<String> before = names(block(LIMIT, true));
        List<String> after = names(block(AND_A_ROW, true));

        assertTrue(before.contains("request.tier=Silver"),
                "the class the row was written for was owed: " + before);
        assertTrue(!after.contains("request.tier=Silver"),
                "and is not owed once a row sits in it: " + after);
    }

    /**
     * Nor does the flag rename anything. Asking for the lines adds rows; it does not make the rows
     * already offered different rows.
     */
    @Test
    void askingForTheLinesDoesNotRenameTheRowsOfferedWithoutThem() {
        String without = block(LIMIT, false);
        String with = block(LIMIT, true);

        assertEquals(names(without), names(with),
                "every row offered without the lines is offered under the same name with them");
        assertTrue(rows(with) > rows(without), "and the lines add rows of their own: " + with);
    }

    /**
     * Two lines composing one row is the case with no cell to name it and no line that can. Each
     * probe fills what its own edge does not name from the bottom of the other's domain, so the two
     * minimum edges here compose one row; which of them is still owed is what an unrelated row
     * changes, and a row named for whichever was offered would be renamed by that. It is offered
     * without a name, and stays that way.
     */
    @Test
    void aRowTwoLinesComposeIsOfferedWithoutAName() {
        String before = block(POLICY, true);
        String after = block(POLICY_AND_A_ROW_AT_THE_CAP, true);
        String written = "| (0, Policy { rate = Rate(0), cap = Cap(0) }) -> <?>";

        assertTrue(before.contains(written), "the row two lines compose carries no name: " + before);
        assertTrue(after.contains(written),
                "and carries none once a row meets one of the two lines: " + after);
        assertEquals(List.of(), names(before), "there is nothing here a cell composed");
        assertEquals(List.of(), names(after), "nor after");
    }
}
