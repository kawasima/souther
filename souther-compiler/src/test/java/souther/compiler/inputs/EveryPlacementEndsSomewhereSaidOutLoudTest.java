package souther.compiler.inputs;

import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredSig;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.NumberAt;
import souther.compiler.check.RuleKey;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A rule placed somewhere comes back with an answer for every place that name reaches, and the
 * answer is never an absence.
 *
 * <p>What a rule wrote is a name in one value's words, and a name written at a sum stands under each
 * of its cases. So one placement has as many answers as the name has places, they need not agree,
 * and a reader counting what a build took in reads them rather than reading how many there happen
 * to be.
 */
class EveryPlacementEndsSomewhereSaidOutLoudTest {

    private static final String SHARED = """
            module g

            data Paging = { limit: Int }
            data A = { ...Paging, x: Int }
            data B = { ...Paging, y: Int }
            data Q = A | B

            data Holder = { q: Q }

            data Ok

            behavior read : (h: Holder) -> Ok
            behavior atTheSum : (q: Q) -> Ok
            """;

    /** A chain: the reading of a link returns to the declaration the link stands at. */
    private static final String RETURNS = """
            module g

            data Nil
            data Cons = { head: Int, tail: Chain }
            data Chain = Nil | Cons

            data Ok

            behavior read : (c: Chain) -> Ok
            """;

    /**
     * One rule, one answer per case.
     *
     * <p>And the answers are filings at the positions the name stands at, which is where a row
     * writes what the rule is about.
     */
    @Test
    void oneNameAtASumIsFiledUnderEachCase() {
        InputDomain read = reading(SHARED, "atTheSum");
        PlacementFiling filing = read.file(new PlacementSeed(
                new RuleAddress(TermPath.of("q"), RuleKey.of("limit")),
                new PlacementSeed.Placed.ANumberOfIt(
                        new NumberAt.OfWhatNumber.OfItsOwnValue()),
                someCitation(aRule(read))));

        assertEquals(List.of("q@A.limit", "q@B.limit"),
                filedAt(filing));
        assertFalse(filing.anythingUnresolved(), "both cases took it");
    }

    /**
     * A rule of the value the sum sits in reaches the same positions.
     *
     * <p>A clause of a {@code Holder} says {@code q.limit}, and what it is about is where a row
     * writes a limit — which is under a case, because a value of the sum is of one case. What
     * crosses the narrowing is the name and not the clause.
     */
    @Test
    void aNameFromTheValueAboveReachesTheSameCases() {
        InputDomain read = reading(SHARED, "read");
        PlacementFiling filing = read.file(new PlacementSeed(
                new RuleAddress(TermPath.of("h"), new RuleKey(List.of("q", "limit"))),
                new PlacementSeed.Placed.TheValuesThere(), someCitation(aRule(read))));

        assertEquals(List.of("h.q@A.limit", "h.q@B.limit"),
                filedAt(filing));
    }

    /**
     * A name that reaches no position says so, and says what the reading was left with.
     *
     * <p>The one thing a measurement may not be called complete over. Read off the filings alone,
     * this rule would be a rule nobody wrote.
     */
    @Test
    void aNameThatReachesNoPositionComesBackSayingSo() {
        InputDomain read = reading(RETURNS, "read");
        // A clause of `Cons` naming a field of the link below it. The link is where the input
        // returns to `Chain`, so the name gets that far and no further.
        PlacementFiling filing = read.file(new PlacementSeed(
                new RuleAddress(pathOf(read, "c@Cons"), new RuleKey(List.of("tail", "head"))),
                new PlacementSeed.Placed.TheValuesThere(), someCitation(aRule(read))));

        assertEquals(List.of(), filedAt(filing));
        assertTrue(filing.anythingUnresolved(), "and nothing else stands in its place");
        assertEquals(1, filing.outcomes().size(),
                "the sum was not entered at all, so there is one stop and it is the sum's");
        PlacementOutcome.Unresolved first =
                assertInstanceOf(PlacementOutcome.Unresolved.class, filing.outcomes().getFirst());
        PlacementOutcome.Reason.TheReadingStoppedThere stopped = assertInstanceOf(
                PlacementOutcome.Reason.TheReadingStoppedThere.class, first.why(),
                "the reading stopped, and it is the position it stopped at that says so");
        assertEquals("c@Cons.tail", stopped.at().toString());
        BlockReason.RecursiveExpansion why =
                assertInstanceOf(BlockReason.RecursiveExpansion.class, stopped.why());
        assertEquals("c", why.openedAt().toString(),
                "and it says where the path had already opened `Chain`");
    }

    /** The position this reading made at {@code spelled}, which is where a case's rules are rooted. */
    private static TermPath pathOf(InputDomain read, String spelled) {
        return read.positions().stream().map(Position::path)
                .filter(each -> each.toString().equals(spelled))
                .findFirst().orElseThrow(() -> new AssertionError(
                        "no position at " + spelled + " among " + read.positions().stream()
                                .map(Position::path).toList()));
    }

    /**
     * A placement with no outcome at all cannot be written down.
     *
     * <p>Which is what keeps a rule from going nowhere quietly: an empty answer is what a reader
     * further on would have to make a cause out of, and following a name is the only thing that
     * hands one over — so there is nowhere for a caller to write one at all.
     */
    @Test
    void everyPlacementOfEveryRuleComesBackWithAnAnswer() {
        for (String source : List.of(SHARED, RETURNS, MIXED)) {
            for (String behavior : behaviorsOf(source)) {
                InputDomain read = reading(source, behavior);
                for (PlacementFiling filing : read.placements()) {
                    assertFalse(filing.outcomes().isEmpty(),
                            () -> "`" + filing.seed().address() + "` was placed by "
                                    + filing.seed().by() + " and came to nothing");
                }
            }
        }
    }

    /**
     * Wherever a name crosses into the cases, every case was walked.
     *
     * <p>Measured rather than assumed, and it holds for a reason worth writing down: what makes a
     * name cross is a spread every case shares, a unit case spreads nothing, and the rules of a sum
     * reach past a case only by naming one — which is written of a case that is the whole of a value.
     * So a sum with a shared spread has record cases and the rules have no way to leave one of them.
     *
     * <p>Which is why no refusal comes out of a crossing today. The arm is there because the walk
     * observes the case either way and a reader must never read one from the other; this says that
     * the day the observation starts happening, somebody comes and looks at what a report should
     * make of it.
     */
    @Test
    void aSumWhoseNamesCrossHasEveryCaseWalked() {
        // A sum whose cases share a spread and whose names do cross, said first. Read off the
        // crossings alone, a day when every case is left takes the crossings with it and the sweep
        // below passes over a list with nothing in it.
        InputDomain shared = reading(SHARED, "atTheSum");
        assertFalse(shared.reach().crossings().isEmpty(), "the cases of `Q` share `limit`");
        assertEquals(List.of(), shared.reach().branchesNotEntered(),
                "and the reading went down both of them");

        for (String source : List.of(SHARED, RETURNS, MIXED)) {
            for (String behavior : behaviorsOf(source)) {
                InputDomain read = reading(source, behavior);
                for (NameReach.Crossing crossing : read.reach().crossings()) {
                    assertTrue(read.reach().branchesNotEntered().stream()
                                    .noneMatch(each -> each.at().equals(crossing.at())),
                            () -> "a case of the sum at " + crossing.at() + " was left, and `"
                                    + crossing.field() + "` crosses there: "
                                    + read.reach().branchesNotEntered());
                }
            }
        }
    }

    /** A sum of cases that share a spread beside one that is the whole of a value. */
    private static final String MIXED = """
            module g

            data Paging = { limit: Int }
            data A = { ...Paging, x: Int }
            data B = { ...Paging, y: Int }
            data Gone
            data Q = A | B | Gone

            data Held = Q invariant here = value /= Gone

            data Ok

            behavior read : (q: Held) -> Ok
            """;

    /** Every behavior the source declares, so the sweep above is over what it has. */
    private static List<String> behaviorsOf(String source) {
        return source.lines().filter(each -> each.startsWith("behavior "))
                .map(each -> each.substring("behavior ".length()).split(" ")[0].trim())
                .toList();
    }

    /**
     * Where the filings among a placement's outcomes are, as this test wants to read them.
     *
     * <p>Written here and not on {@link PlacementFiling}. What a caller does about a refusal and
     * about a name left unresolved is what a filing is read for, and an accessor handing out the
     * positions alone lets that be settled by how many came back.
     */
    private static List<String> filedAt(PlacementFiling filing) {
        return filing.outcomes().stream()
                .filter(each -> each instanceof PlacementOutcome.Filed)
                .map(each -> ((PlacementOutcome.Filed) each).at().toString())
                .toList();
    }

    /** A rule of the model to hang a made-up placement on. */
    private static souther.compiler.check.RuleRef.Invariant aRule(InputDomain read) {
        for (Position each : read.positions()) {
            if (each.type() instanceof souther.compiler.types.Type.Ref ref
                    && ref.name() instanceof souther.compiler.types.TypeSymbol.AtModule at) {
                return someRule(at);
            }
        }
        throw new IllegalStateException("no declaration to hang a rule on");
    }

    /** A rule to hang a placement on, so that a seed made here is one some rule placed. */
    private static souther.compiler.check.RuleRef.Invariant someRule(
            souther.compiler.types.TypeSymbol.AtModule on) {
        return new souther.compiler.check.RuleRef.Invariant(
                new souther.compiler.check.Clause.Ref(
                        new souther.compiler.check.Clause.Id(on, 0),
                        java.util.Optional.of(new souther.compiler.check.ClauseName("here"))));
    }

    /** How a report would send a reader to it. */
    private static souther.compiler.check.RuleCitation someCitation(
            souther.compiler.check.RuleRef.Invariant rule) {
        return new souther.compiler.check.RuleCitation.Named(rule);
    }

    private static InputDomain reading(String source, String behavior) {
        Compilation compilation =
                Compilation.ofSources(List.of(source), souther.compiler.meta.ModulePath.EMPTY);
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        return InputDomain.of(sigs.get(behavior), rules, ReadAs.THE_COMPILATION_DOES);
    }
}
