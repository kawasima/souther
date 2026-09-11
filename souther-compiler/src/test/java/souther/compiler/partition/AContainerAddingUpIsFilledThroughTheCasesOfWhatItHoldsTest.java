package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.ReadingPolicy;
import souther.compiler.check.RuleReadingContext;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.Symbols;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.RunSource;
import souther.compiler.inputs.TermOrders;
import souther.compiler.inputs.TermOrdersFixtures;
import souther.compiler.inputs.TermPath;
import souther.compiler.numeric.Count;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;
import souther.compiler.types.ValueName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A container is filled to a total through the cases of what it holds.
 *
 * <p>Where every case of a sum spreads the same declaration, a field of it is read through the sum:
 * the total {@code List.sum(ns[*].method.amount)} names a position of the sum and no case of it,
 * which is what makes the rule readable and is not enough to write a value with. A value of that
 * position is a value of one of the cases, and which one is a fact about the value — so the walk
 * offers one shape per case rather than choosing.
 *
 * <p>The way down is the plan's. Walked from the declarations instead, a field of a plain record at
 * a time, the sum was a shape it had no answer for: every count and every spread came back with
 * nothing built, and the point the total was about was reported in the words of a figure this
 * compiler had stopped at.
 */
class AContainerAddingUpIsFilledThroughTheCasesOfWhatItHoldsTest {

    /**
     * Entries whose method is a sum of two cases, the amount spread through both.
     *
     * <p>Each case carries a field of its own, so that composing one is composing a record and not
     * merely naming a case — which is the shape a model writes and the one this could not fill.
     */
    private static final String SHARED = """
            module g

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

            data Entry = { method: Method, settled: Bool }

            data Page = { count: Int }

            behavior readArticles : (ns: List<Entry>) -> Page
            """;

    private static final ReadingPolicy POLICY = new ReadingPolicy(64, 12,
            souther.compiler.values.AsACompilationAllows.admittedValues(),
            souther.compiler.values.AsACompilationAllows.whatARuleLeaves());

    /**
     * A total two counts reach, so that both a container of one and a container of two are among
     * what is offered.
     */
    private static final Count SIX = Count.of(6);

    /**
     * Every case is offered, and each container is a whole value of it.
     *
     * <p>The texts and not a count of them. What a case brings with it is the rest of its own
     * record, and a walk that reached the amount without composing the case around it would offer
     * as many containers as this and none the decoder accepts.
     */
    @Test
    void aContainerIsOfferedForEachCaseOfWhatItHolds() {
        assertEquals(List.of(
                        "[Entry { method = Card { amount = Amount(6), issuer = Issuer(\"x\") }"
                                + ", settled = true }]",
                        "[Entry { method = Cash { amount = Amount(6), note = Note(\"x\") }"
                                + ", settled = true }]",
                        "[Entry { method = Card { amount = Amount(6), issuer = Issuer(\"x\") }"
                                + ", settled = true }, Entry { method = Card"
                                + " { amount = Amount(0), issuer = Issuer(\"x\") }"
                                + ", settled = true }]",
                        "[Entry { method = Cash { amount = Amount(6), note = Note(\"x\") }"
                                + ", settled = true }, Entry { method = Cash"
                                + " { amount = Amount(0), note = Note(\"x\") }"
                                + ", settled = true }]"),
                offered().stream().map(FixtureTemplate::text).toList(),
                "one container per case at each count, with the case's own field beside the"
                        + " amount");
    }

    /**
     * The ways down are stopped by a figure of their own, and it is not the one that counts the
     * containers offered.
     *
     * <p>A way that plans is not a container that was offered: the values are composed afterwards,
     * and a case may be refused there. Charged to one figure, a case that planned and built nothing
     * would spend an offer, so which cases are tried at all would be decided by the order they are
     * declared in — which is what the offers were meant not to turn on.
     */
    @Test
    void theWaysDownAreStoppedByTheirOwnFigure() {
        TermRealizations.Realization.Built made = assertInstanceOf(
                TermRealizations.Realization.Built.class, realizing(MANY_CASES),
                "the cases that were tried compose containers");

        assertTrue(made.heldBack().contains(CompositionBudget.WAYS_DOWN_TO_A_TOTAL_TRIED),
                () -> "more cases than this tries, and it says which figure stopped it: "
                        + made.heldBack());
        assertFalse(made.values().isEmpty(),
                "and the ways it did try composed containers");
    }

    /**
     * The figure counts what this walk did, so a walk that keeps nothing is stopped by it too.
     *
     * <p>Every position that has to be narrowed multiplies what is left to ask about. Where the
     * branches plan, a figure over what was kept stops the walk soon enough that nothing shows;
     * where none of them does — here the number stands deeper than a value is built — nothing is
     * kept, and a figure over what was kept would let the cases of every sum on the way be walked
     * in full while saying the walk was never stopped.
     */
    @Test
    void theFigureCountsTheWaysTriedAndNotTheOnesKept() {
        TermRealizations.Realization came = realizing(DEEP_UNDER_SUMS, DEEPLY);

        assertEquals(java.util.Set.of(CompositionBudget.WAYS_DOWN_TO_A_TOTAL_TRIED,
                        CompositionBudget.DEPTH_A_CONSTRUCTION_PLAN_DESCENDS),
                assertInstanceOf(TermRealizations.Realization.Stopped.class, came,
                        "no way down was kept, so there is no container and no reason of the"
                                + " model's").by(),
                "and both figures are said: the ways this tried, and what each of them was planned"
                        + " no further than");
    }

    /** The amount under two sums and then below more levels than a value is built through. */
    private static final List<String> DEEPLY = List.of("first", "second", "down", "down", "down",
            "down", "down", "down", "down", "amount");

    /**
     * Two sums on the way down, with the number below the depth a plan descends.
     *
     * <p>So every combination of their cases is a way to ask about and none of them plans, which is
     * the walk a figure over what was kept does not bound.
     */
    private static final String DEEP_UNDER_SUMS = """
            module g

            data Amount = Int
                invariant value >= 0

            data Common = { amount: Amount }

            data L8 = { ...Common, tag: Int }
            data L7 = { down: L8 }
            data L6 = { down: L7 }
            data L5 = { down: L6 }
            data L4 = { down: L5 }
            data L3 = { down: L4 }
            data L2 = { down: L3 }
            data L1 = { down: L2 }

            data A1 = { down: L1 }
            data A2 = { down: L1 }
            data A3 = { down: L1 }
            data A4 = { down: L1 }
            data Second = A1 | A2 | A3 | A4

            data B1 = { second: Second }
            data B2 = { second: Second }
            data B3 = { second: Second }
            data B4 = { second: Second }
            data First = B1 | B2 | B3 | B4

            data Entry = { first: First, settled: Bool }

            data Page = { count: Int }

            behavior readArticles : (ns: List<Entry>) -> Page
            """;

    /** The same sum with more cases than the ways down are tried. */
    private static final String MANY_CASES = SHARED.replace(
            "data Method = Card | Cash",
            """
            data Cash2 = { ...Common, note: Note }
            data Cash3 = { ...Common, note: Note }
            data Cash4 = { ...Common, note: Note }
            data Cash5 = { ...Common, note: Note }
            data Cash6 = { ...Common, note: Note }
            data Cash7 = { ...Common, note: Note }
            data Cash8 = { ...Common, note: Note }
            data Cash9 = { ...Common, note: Note }

            data Method = Card | Cash | Cash2 | Cash3 | Cash4 | Cash5 | Cash6 | Cash7 | Cash8
                        | Cash9""");

    /**
     * Every way down was tried and none composed a value, which is not the answer a demand nothing
     * reaches gets.
     *
     * <p>The evidence and not the word. What a reader downstream does with a total nothing composed
     * is one classification; what happened in this attempt is another, and a walk that said the same
     * thing about both would leave the difference to be worked out from what the sentence left out.
     */
    @Test
    void everyWayTriedAndNoneComposedSaysSo() {
        TermRealizations.Realization.None came = assertInstanceOf(
                TermRealizations.Realization.None.class,
                realizing(NO_CASE_COMPOSES, AMOUNT, namedIn(NO_CASE_COMPOSES, "Entries")),
                "the ways down are there and neither of them builds a value");

        assertEquals(Generator.UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE, came.why());
        assertEquals("`ns[*].method@Card.amount`, `ns[*].method@Cash.amount` were ways down to"
                        + " `ns[*].method.amount`, and none of them composed a value",
                came.detail(),
                "and the ways that were walked are named, so a reader is not left to tell this"
                        + " from a demand nothing reaches");
    }

    /**
     * A demand under a position nothing stands under says that instead.
     *
     * <p>The other corner. Both come back with the one word a reader downstream acts on, and what
     * tells them apart is what each says it found.
     */
    @Test
    void aDemandNothingStandsUnderSaysThatInstead() {
        TermRealizations.Realization.None came = assertInstanceOf(
                TermRealizations.Realization.None.class, realizing(SHARED, UNDER_A_TRUTH),
                "a truth value holds no position, so there is no way down to ask for");

        assertEquals(Generator.UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE, came.why());
        assertEquals("nothing standing at `ns[*].settled` gives a way down to"
                        + " `ns[*].settled.amount`",
                came.detail(),
                "and what it says is that there was never a way, which is not that the ways came"
                        + " to nothing");
    }

    /** The amount, read through the sum every case spreads it in. */
    private static final List<String> AMOUNT = List.of("method", "amount");

    /** A way down that goes under a truth value, which divides into two values and holds no
     *  position under either. */
    private static final List<String> UNDER_A_TRUTH = List.of("settled", "amount");

    /**
     * The same sum with both cases holding a string longer than one is worth writing out, so the
     * way down to the amount is there and neither case composes a value.
     */
    private static final String NO_CASE_COMPOSES = """
            module g

            data Amount = Int
                invariant value >= 0

            data Common = { amount: Amount }

            data Card = { ...Common, inner: Method }
            data Cash = { ...Common, inner: Method }

            data Method = Card | Cash

            data Entries = List<Entry>
                invariant List.length(value) <= 1

            data Entry = { method: Method, settled: Bool }

            data Page = { count: Int }

            behavior readArticles : (ns: List<Entry>) -> Page
            """;

    /** The containers this offered for the model every test here is about. */
    private static List<FixtureTemplate> offered() {
        return offeredBy(SHARED);
    }

    /** The containers this offered for {@code source}, whose behavior takes the entries. */
    private static List<FixtureTemplate> offeredBy(String source) {
        return assertInstanceOf(TermRealizations.Realization.Built.class, realizing(source),
                "a list of entries whose amounts come to six is a container this composes")
                .values();
    }

    /** What this walk came to for {@code source}, with the amount read the way it is written. */
    private static TermRealizations.Realization realizing(String source) {
        return realizing(source, AMOUNT);
    }

    /** The same, for a total read at {@code steps} below each element. */
    private static TermRealizations.Realization realizing(String source, List<String> steps) {
        return realizing(source, steps, new Type.ListOf(named("Entry")));
    }

    /** The same, over a container of {@code held}. */
    private static TermRealizations.Realization realizing(String source, List<String> steps,
                                                          Type held) {
        RuleReadingSource rules = RuleReadings.ofSource(source);
        Symbols symbols = rules.symbols();
        Type entries = held;
        TermPath read = TermPath.of("ns").element();
        for (String step : steps) {
            read = read.then(step);
        }
        NumericTerm.TakenOver total = NumericTerm.TakenOver.of(
                ValueName.Stdlib.operation("List", "sum"),
                new RunSource.ProjectedOccurrences(read),
                named("Amount"), symbols);
        assertNotNull(total, "adding up the amounts of a run is a number of it");
        TermOrders orders = TermOrdersFixtures.at(total, named("Amount"), symbols);
        assertNotNull(orders.answered(), "and the order it answers on is the amounts'");

        return TermRealizations.at(entries, orders, SIX, NothingTheRulesSay.REGION,
                RuleReadingContext.unshared(rules, POLICY));
    }

    private static Type named(String data) {
        return Type.ref(TypeSymbols.declared(new TypeKey("g", data)));
    }

    /** The same, said of a model whose declarations are the ones being read. */
    private static Type namedIn(String source, String data) {
        assertTrue(source.contains("data " + data), "the model declares `" + data + "`");
        return named(data);
    }
}
