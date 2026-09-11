package souther.compiler.query;

import org.junit.jupiter.api.Test;

import souther.compiler.meta.ModulePath;
import souther.compiler.partition.BorderObligationPoint;
import souther.compiler.partition.OwedPoint;
import souther.compiler.partition.PointAttribution;
import souther.compiler.partition.PointRole;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every point a row is owed at is in one account, and the two accounts are made from the readings.
 *
 * <p>A border owes a row at up to four points, and whose it is to write one there is settled per
 * point: a run that stops at this body's own rule exists in this body and nowhere else, and a line a
 * {@code data} clause drew is answered once for the module by a row written for any behavior
 * carrying the type. So the readings refine into two accounts, and this holds them to it — a point
 * in both would be a row asked for twice, and a point in neither would be one nothing measures.
 *
 * <p><b>Counting is not the check.</b> The declarations' side collapses every reading of one point
 * into one debt, so the number of debts is smaller than the number of readings that owe them and no
 * total is conserved. What holds is the refinement: each reading's point goes to exactly one side,
 * and each debt is what some readings on the declarations' side came to together.
 */
class EveryPointOwedIsInOneAccountTest {

    /**
     * A type whose clause draws a line, carried at a position of two behaviors, and one of those
     * behaviors drawing a line of its own.
     */
    private static final String MODEL = """
            module example.both

            data Amount = Int
                invariant value >= 10

            data Draft = { cost: Amount }
            data Ok = { n: Int }
            data No = { n: Int }

            behavior keep : (d: Draft) -> Ok | No
                constructs Ok, No

            let keep (d) = {
                guard d.cost.value <= 100 else No { n = 0 }
                Ok { n = d.cost.value }
            }

            behavior hold : (d: Draft) -> Ok
                constructs Ok

            let hold (d) = Ok { n = d.cost.value }

            example keep
                | "mid" : (Draft { cost = Amount(50) }) -> Ok { n = 50 }

            example hold
                | "mid" : (Draft { cost = Amount(50) }) -> Ok { n = 50 }
            """;

    @Test
    void abehaviorIsOwedNothingThatIsOwedToTheDeclarations() {
        Compilation compilation = measured();
        Map<String, List<BorderAssessment>> lines =
                Adequacy.readingsOf(compilation.db(), "example.both");

        List<OwedPoint> theDeclarations = new ArrayList<>();
        List<OwedPoint> theReadings = new ArrayList<>();
        for (List<BorderAssessment> read : lines.values()) {
            for (BorderAssessment border : read) {
                for (PointRole role : PointRole.values()) {
                    for (OwedPoint owed : border.border().owes(role)) {
                        (owed.attribution() instanceof PointAttribution.TheDeclarations
                                ? theDeclarations : theReadings).add(owed);
                    }
                }
            }
        }
        // Both sides are there, so the two assertions below are about something. A model whose
        // every point fell on one side would pass them by having nothing to tell apart.
        assertFalse(theDeclarations.isEmpty(), "the clause's line is owed to the declaration");
        assertFalse(theReadings.isEmpty(), "and the guard's line is owed to the body that drew it");

        List<BorderObligationPoint> account = new ArrayList<>();
        compilation.db().ask(new Adequacy.BodyBorders("example.both")).value()
                .forEach((behavior, owed) -> owed.made().orElseGet(List::of)
                        .forEach(point -> account.add(point.point())));
        assertEquals(theReadings.stream().map(OwedPoint::point).distinct().toList(), account,
                "a behavior's account is the points its own rules settled, once each, and no"
                        + " others");

        Set<BorderObligationPoint> owedToDeclarations = new LinkedHashSet<>(
                theDeclarations.stream().map(OwedPoint::point).toList());
        assertTrue(account.stream().noneMatch(owedToDeclarations::contains),
                "and nothing in it is a point the declarations are owed a row at");
    }

    /**
     * And what the module's declarations are owed is what those readings came to together.
     *
     * <p>Which is why no count is conserved: {@code Amount}'s line is met at a position of both
     * behaviors, and the two readings of one point are one debt.
     */
    @Test
    void theDeclarationsAccountIsOneDebtHoweverManyReadingsOwedIt() {
        Compilation compilation = measured();
        List<Adequacy.DeclaredDebt> debts = compilation.db()
                .ask(new Adequacy.DeclaredBorders("example.both")).value().owed();

        assertFalse(debts.isEmpty(), "the module's own declaration draws a line its behaviors meet");
        for (Adequacy.DeclaredDebt owed : debts) {
            assertTrue(owed.debt().met().size() >= 1, "a debt is what some readings came to");
        }
        List<Adequacy.DeclaredDebt> acrossBoth = debts.stream()
                .filter(owed -> owed.debt().met().size() > 1).toList();
        assertFalse(acrossBoth.isEmpty(),
                () -> "both behaviors carry the type, so a point of its line is read twice and owed"
                        + " once: " + debts.stream().map(each -> each.said()).toList());
    }

    /**
     * A module whose declarations draw a line the walk never reached the position of.
     *
     * <p>What the mapping holds is a value this compiler names no position for, so {@code Amount}'s
     * clause is written under a position no reading is ever opened at.
     */
    private static final String NOT_REACHED = """
            module example.notreached

            data Amount = Int
                invariant value >= 0 && value <= 100
            data Req = { cost: Map<String, Amount>, flag: Bool }
            data Res = { n: Int }

            behavior f : (r: Req) -> Res
                constructs Res
            let f (r) = Res { n = 0 }

            example f
                | "one" : (Req { cost = [ ("a", Amount(1)) ], flag = true }) -> Res { n = 0 }
            """;

    /**
     * A reading that did not run out leaves the declarations' account short, and it says so.
     *
     * <p>The other half of what a behavior's account answers. What a module's declarations are owed
     * is read off the lines its behaviors met, so a reading that stopped may have left a line their
     * declarations owe unseen — and an account that came back with no debts and nothing to say would
     * be a module whose declarations owe nothing, which is what a module every line of which is
     * covered also answers.
     *
     * <p>{@code Amount} is such a declaration: it states something about its values, and this run
     * could not get to the position carrying it to find out what is owed there.
     */
    @Test
    void aReadingThatDidNotRunOutLeavesTheDeclarationsAccountShort() {
        Compilation compilation = Compilation.ofSources(List.of(NOT_REACHED), ModulePath.EMPTY);
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();

        Adequacy.DeclaredBoundaries account =
                compilation.db().ask(new Adequacy.DeclaredBorders("example.notreached")).value();
        assertNotNull(account, "the model under test compiles");

        assertEquals(List.of(), account.owed(),
                "the walk never reached the position, so no debt of the declaration was found");
        assertFalse(account.weakening().isEmpty(),
                "and the account says the reading it was made from did not run out, so the debts it"
                        + " holds are not all there are");
    }

    /**
     * An entry of an account is made where the account is, and nowhere else.
     *
     * <p>The four readings of one of these — the line, the role, what is owed there and what became
     * of it — go to three different readers, so a value assembled from parts of different points
     * would be shown by one, named by another and judged by a third with nothing to notice. What
     * keeps them one point is that nobody outside can make one.
     */
    @Test
    void nothingOutsideTheAccountCanMakeAnEntryOfIt() {
        assertEquals(List.of(), List.of(OwedBoundaryPoint.class.getConstructors()),
                "a public constructor is a way to assemble a point out of parts of others");
    }

    /**
     * And a behavior's two readings of one measurement are of one measurement.
     *
     * <p>What it is owed a row for is read off the lines it met, and the two are read by different
     * readers: a block and a document show the lines, a finding and a verdict read the account. Held
     * apart without being held together, a behavior could show one reading's borders under another
     * reading's findings.
     */
    @Test
    void aBehaviorsAccountIsWhatItsOwnLinesComeTo() {
        Compilation compilation = measured();
        Map<String, PartitionEvidence> partitions =
                compilation.db().ask(new Adequacy.Coverage("example.both")).value();
        Map<String, Measure<List<BorderAssessment>>> lines = compilation.db()
                .ask(new Adequacy.BoundaryReadings("example.both")).value();
        Map<String, Measure<List<BorderObligationPointAssessment>>> accounts = compilation.db()
                .ask(new Adequacy.BodyBorders("example.both")).value();

        // The two behaviors meet different lines, so one's account is not the other's — which is
        // what makes the refusal below about something.
        assertNotEquals(accounts.get("keep"), accounts.get("hold"),
                "the two behaviors are owed different rows");

        assertDoesNotThrow(() -> new BehaviorEvidence(Adequacy.RowReading.NONE, null,
                partitions.get("keep"), lines.get("keep"), accounts.get("keep"), null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new BehaviorEvidence(Adequacy.RowReading.NONE, null,
                        partitions.get("keep"), lines.get("hold"), accounts.get("keep"), null,
                        null),
                "an account and the lines of another behavior are two measurements");
    }

    /**
     * Two behaviors carrying one declared type, one of whose rows does not come back.
     *
     * <p>So the debt {@code Amount}'s line leaves is read twice and what the two readings came to
     * differs: one of them is undecided for a reason about a position of {@code stalls}.
     */
    private static final String ONE_ROW_STOPS = """
            module example.stopped

            data Amount = Int
                invariant value >= 0

            data Draft = { n: Amount }
            data Ok = { n: Int }

            behavior seen : (d: Draft) -> Ok
                constructs Ok
            let seen (d) = Ok { n = d.n.value }

            behavior stalls : (d: Draft) -> Ok
                constructs Ok
            let stalls (d) = Ok { n = d.n.value }

            example seen
                | "inside the run" : (Draft { n = Amount(5) }) -> Ok { n = 5 }

            example stalls
                | "never read" : (Draft { n = Amount(7) }) -> Ok { n = 7 }
            """;

    /**
     * A view shown some of a module's behaviors carries nothing a hidden one went without.
     *
     * <p>A debt is what its readings came to together, so the account a reader is shown has to be
     * made again from the readings that reader can see. Filtered by dropping debts nobody shown
     * carries — and keeping the ones somebody does whole — a view carries the hidden behavior's
     * evidence inside the debt it kept: undecided, for a reason naming a position that is not on
     * the page and a row its reader cannot write.
     */
    @Test
    void aViewOfSomeBehaviorsCarriesNothingAHiddenOneWentWithout() {
        // The row of `stalls` is not read at all, which is what bears on a line an `invariant`
        // drew: meeting one of those takes writing the value and no comparison has to have run, so
        // a row that was read and stopped leaves it settled and a row nobody read does not.
        Compilation compilation = Compilation.ofSource(ONE_ROW_STOPS, "Main");
        compilation.withJvmExampleDeadlines(souther.compiler.DoesNotComeBack.overrunningOn(
                souther.compiler.DoesNotComeBack.everythingAboutRowsOf("stalls")));
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();

        Adequacy.DeclaredBoundaries account =
                compilation.db().ask(new Adequacy.DeclaredBorders("example.stopped")).value();
        assertNotNull(account, "the model under test compiles");
        assertFalse(account.owed().isEmpty(), "the clause draws a line both behaviors carry");

        // Over both behaviors the debt is undecided, because one of the rows that would settle it
        // never came back. Asserted so that what the filtered view drops is something it had.
        assertFalse(account.weakening().isEmpty(),
                () -> "a row of `stalls` did not come back, so the debt is not settled: "
                        + account.owed().stream().map(each -> each.said()).toList());

        Adequacy.DeclaredBoundaries shown = account.keptFor(java.util.Set.of("seen"));
        assertFalse(shown.owed().isEmpty(), "`seen` carries the line, so the debt is still work");
        assertTrue(shown.weakening().isEmpty(),
                () -> "a view of `seen` carries what `stalls` went without: "
                        + shown.weakening().causes());
    }

    /**
     * Who owes a point does not turn on which reading of it was met first.
     *
     * <p>Two readings of one point can each name a declaration the other does not, and the point is
     * owed to both — so the owners are gathered by putting two sets together, and a set put together
     * as its members arrive comes out in the order of the walk. That order is read: it is what a
     * report calls the pair, which module keeps the account, and which entry of a document they are
     * gathered under.
     *
     * <p>Asked of the value rather than of a page, because the value is what every one of those
     * reads. Two attributions that differ only in the order they were merged are one attribution.
     */
    @Test
    void whoOwesAPointIsTheSameWhicheverReadingWasMetFirst() {
        souther.compiler.types.TypeSymbol.AtModule cap = souther.compiler.types.TypeSymbols
                .declared(new souther.compiler.types.TypeKey("example.both", "Cap"));
        souther.compiler.types.TypeSymbol.AtModule held = souther.compiler.types.TypeSymbols
                .declared(new souther.compiler.types.TypeKey("example.both", "Held"));

        PointAttribution forwards = new PointAttribution.TheDeclarations(List.of(cap))
                .and(new PointAttribution.TheDeclarations(List.of(held)));
        PointAttribution backwards = new PointAttribution.TheDeclarations(List.of(held))
                .and(new PointAttribution.TheDeclarations(List.of(cap)));

        assertEquals(forwards, backwards,
                "who owes the point is a set, and a set is not the order it was gathered in");
        assertEquals(((PointAttribution.TheDeclarations) forwards).ownersIn("example.both"),
                ((PointAttribution.TheDeclarations) backwards).ownersIn("example.both"),
                "so the module's account of it names them the same way round");
        assertEquals(new souther.compiler.query.FindingSubject.OfADeclaration(
                        ((PointAttribution.TheDeclarations) forwards).owners()).named(),
                new souther.compiler.query.FindingSubject.OfADeclaration(
                        ((PointAttribution.TheDeclarations) backwards).owners()).named(),
                "and a report calls them the same thing");
    }

    private static Compilation measured() {
        Compilation compilation = Compilation.ofSources(List.of(MODEL), ModulePath.EMPTY);
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        assertEquals(List.of(), compilation.db().allReports().stream()
                        .filter(each -> each.report().isError())
                        .map(each -> each.report().diagnostic().code()).toList(),
                "the model under test compiles");
        return compilation;
    }
}
