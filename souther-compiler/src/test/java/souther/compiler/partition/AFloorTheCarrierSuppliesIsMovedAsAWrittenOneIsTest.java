package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredSig;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.inputs.InputDomain;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.Position;
import souther.compiler.inputs.TermPath;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Bodies;
import souther.compiler.query.BorderAssessment;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * A floor the carrier supplies and a floor a clause wrote are moved by one mechanism.
 *
 * <p>A length is never negative and nothing writes that down, so {@code String.length(value) /= 0}
 * takes the nought off a floor nobody wrote and the position starts at one. A written floor with
 * the same value taken off it comes to the same place. Neither is named in the rule that moves it:
 * what they have in common is being in what the other rules leave, and that is where the rule is
 * read against.
 *
 * <p>Two things are asked of each model and neither settles the other. What the rules leave the
 * position is the geometry; what is owed for it is the authored lines that account for where the
 * values stop. Two models leaving the same range may owe different lines, and two models writing
 * different numbers of clauses may owe the same — a clause restating what the carrier already
 * states holds nothing, and one whose end a tighter one covers owes nothing either, as before.
 *
 * <p>Both directions are held here. Read as "the same range means the same obligations", a check
 * would let a debt be folded away the day two ranges agreed; read as "different clauses mean
 * different obligations", it would demand a row for a clause that accounts for nothing.
 */
class AFloorTheCarrierSuppliesIsMovedAsAWrittenOneIsTest {

    /** The floor is the carrier's: a length is never negative, and no clause says so. */
    private static final String CARRIERS = """
            data Subject = String
                invariant notBlank = String.length(value) /= 0
            """;

    /** The same, with the floor written out beside the rule that moves it. */
    private static final String WRITTEN = """
            data Subject = String
                invariant notNegative = String.length(value) >= 0
                invariant notBlank = String.length(value) /= 0
            """;

    /** And the same two, the other way round, as one clause of two conjuncts. */
    private static final String TURNED = """
            data Subject = String
                invariant both = String.length(value) /= 0 && String.length(value) >= 0
            """;

    /** The row the issue was written from: a border where there was none. */
    @Test
    void aHoleAtTheCarriersFloorLeavesABorderAtOne() {
        assertEquals(List.of("String.length(n) = 1"), bordersOf(CARRIERS),
                "the position starts at one, and one is a line somebody has to write a row at");
    }

    /** The geometry is one answer, whichever floor the model wrote. */
    @Test
    void theRulesLeaveThePositionTheSameRangeEitherWay() {
        assertEquals(lengthRangeOf(CARRIERS), lengthRangeOf(WRITTEN));
        assertEquals(lengthRangeOf(CARRIERS), lengthRangeOf(TURNED));
        assertEquals("Bounds[min=Endpoint[at=1, inclusive=true], max=null]",
                lengthRangeOf(CARRIERS).toString(), "and the range is the one the rules leave");
    }

    /**
     * A clause restating what the carrier already states holds nothing, and owes nothing.
     *
     * <p>A length is never negative, so writing {@code String.length(value) >= 0} beside the rule
     * that takes the nought away leaves the values exactly where that rule leaves them alone: take
     * the written floor away and the position still starts at one. The clause's own end is at
     * nought, which the rules refuse, and a looser end falls out as any does.
     *
     * <p>Which is not the answer where the carrier holds no floor. On an {@code Int} the written
     * one is load-bearing — without it the values stop nowhere — so it is owed a row at one beside
     * the rule that took the nought away.
     */
    @Test
    void aClauseRestatingTheCarrierOwesNothingBesideTheRuleThatMovesTheEnd() {
        assertEquals(1, owedBy(WRITTEN).size(),
                "the written floor says what the length's own order already says");
        assertEquals(clausesOwing(CARRIERS), clausesOwing(WRITTEN),
                "so the model with it owes the row the model without it owes, and no other");
        assertEquals(2, bordersOf("""
                data Subject = Int
                    invariant notNegative = value >= 0
                    invariant notZero = value /= 0
                """).size(),
                "and where the carrier holds no floor the written one is holding the end");
    }

    /** Swapping the conjuncts moves neither the range nor what is owed. */
    @Test
    void writingTheConjunctsTheOtherWayRoundChangesNothing() {
        assertEquals(bordersOf(WRITTEN), bordersOf(TURNED));
    }

    /**
     * And the same range is not the same obligation.
     *
     * <p>Two clauses each saying where the value stops are two authored lines, and the row written
     * for one is no evidence about the other. One clause saying it is one. Both models leave the
     * position the same values.
     *
     * <p>The count and the clauses, not that the two answers differ. A model whose one debt moved
     * from one clause to another differs too, and that is not what this is about — what a check
     * here has to refuse is the day somebody folds two debts into one because the ranges agree.
     */
    @Test
    void theSameRangeIsNotTheSameObligation() {
        String one = """
                data Subject = Int
                    invariant atLeastFive = value >= 5
                """;
        String two = """
                data Subject = Int
                    invariant atLeastFive = value >= 5
                    invariant aboveFour = value > 4
                """;

        assertEquals(rangeOf(one), rangeOf(two), "the rules leave the position the same values");
        assertEquals(1, bordersOf(one).size());
        assertEquals(2, bordersOf(two).size(),
                "two clauses drew the line, and a row at it is owed to each of them");
        assertNotEquals(owedBy(one), owedBy(two));
    }

    /**
     * A rule whose arithmetic no end was read from leaves its line where the plain one does.
     *
     * <p>{@code value * 2 >= 4} has a bare name on neither side, so the reading of ends made
     * nothing of it and every reader below was told the model bounds the value nowhere. What it
     * leaves the position is where the plain rule leaves it, and a row is owed at the same place.
     *
     * <p>Nothing inverts the {@code 2 *}. What the rule did is read from the values the rules leave
     * with it and without it, which is the same question a rule naming a value is asked.
     */
    @Test
    void aRuleWhoseArithmeticPlacedNoEndLeavesItsLineWhereThePlainOneDoes() {
        assertEquals(bordersOf("""
                data Subject = Int
                    invariant plain = value >= 2
                """), bordersOf("""
                data Subject = Int
                    invariant doubled = value * 2 >= 4
                """));
    }

    /**
     * The same where the number is one taken of the value, which is the axis that can be lost.
     *
     * <p>An {@code Int} has one number and a rule about it cannot be about anything else, so an
     * arithmetic there is measured on the right axis whatever recognised it. A {@code String} has
     * two, and {@code String.length(value) * 2 >= 4} names neither of them on a side — recognised
     * from the spelling, the model writes about no number of the value, and the position comes back
     * measured on the string's own order with the length no number of the model at all.
     */
    @Test
    void anArithmeticOnANumberTakenOfTheValueLeavesItsLineWhereThePlainOneDoes() {
        assertEquals(bordersOf("""
                data Subject = String
                    invariant plain = String.length(value) >= 2
                """), bordersOf("""
                data Subject = String
                    invariant doubled = String.length(value) * 2 >= 4
                """));
    }

    /**
     * Two clauses saying one thing are both owed a row, and neither is missed on its own.
     *
     * <p>Taking either away leaves the value where it is, so a reading that asked only which
     * conjunct is missed on its own answered nobody twice and the end came back owed to nobody at
     * all. Which of them accounts for it is the second question — whether it holds the end with
     * every other candidate gone — and both of these do.
     */
    @Test
    void twoClausesSayingOneThingAreBothOwedARow() {
        assertEquals(4, bordersOf("""
                data Subject = Int
                    invariant five = value == 5
                    invariant alsoFive = value == 5
                """).size(), "two clauses, each with an end either side of the value they name");
    }

    /** The same of a record's field, which is read through another walk. */
    @Test
    void aFieldsArithmeticLeavesItsLineWhereThePlainOneDoes() {
        assertEquals(bordersOf("""
                data Subject = { x: Int }
                    invariant plain = x >= 2
                """), bordersOf("""
                data Subject = { x: Int }
                    invariant doubled = x * 2 >= 4
                """));
    }

    /**
     * And a quantity over two coordinates is not read this way at all.
     *
     * <p>Such a rule divides neither of them, so an end attributed to it at either would be an end
     * of a number it does not divide. Its line is drawn as the relation it is, and a row is owed
     * there.
     */
    @Test
    void aQuantityOverTwoCoordinatesIsNoOnePositionsEnd() {
        assertEquals(List.of("n.lo = n.hi"), bordersOf("""
                data Subject = { lo: Int, hi: Int }
                    invariant ordered = lo <= hi
                """), "one line, where the pair parts, and no end at either of them");
    }

    /**
     * Two conjuncts holding one end are two rows to write, whatever each of them placed.
     *
     * <p>Four pairs, and the fourth is the control. Three of them put a conjunct that placed an end
     * beside one that placed none, and the fourth puts two that placed one — where the reading of
     * ends has always answered, and still does.
     *
     * <p>Split by whether a conjunct placed an end, the first three lose a debt: the reading of
     * ends cannot see a conjunct that placed none, and a counterfactual over the ones that placed
     * none cannot see the one beside it. Neither can attribute an end the two of them hold together.
     */
    @Test
    void twoConjunctsHoldingOneEndAreTwoRowsToWrite() {
        assertEquals(2, bordersOf("""
                data Subject = Int
                    invariant plain = value >= 2
                    invariant doubled = value * 2 >= 4
                """).size(), "an ordering beside an arithmetic no end was read from");
        assertEquals(2, bordersOf("""
                data Subject = String
                    invariant floor = String.length(value) >= 1
                    invariant hole = String.length(value) /= 0
                """).size(), "an ordering beside a hole that moves the carrier's floor onto it");
        assertEquals(2, bordersOf("""
                data Subject = Int
                    invariant notNegative = value >= 0
                    invariant notZero = value /= 0
                """).size(), "and where the end is at neither of the values they name");
        assertEquals(2, bordersOf("""
                data Subject = Int
                    invariant atLeastFive = value >= 5
                    invariant aboveFour = value > 4
                """).size(), "two orderings at one value, which is the answer this does not change");
    }

    /**
     * A rule stating what the carrier already states is owed a row all the same.
     *
     * <p>The control for leaving a coordinate whose conjuncts all placed an end to the reading of
     * ends. A length is never negative, so taking this clause away moves nothing and a
     * counterfactual names nobody — and the clause is still a line an author wrote and a row is
     * owed at it. Which is why the case where the ends answer for themselves is left with them,
     * and not because the two readings agree.
     */
    @Test
    void aRuleStatingWhatTheCarrierStatesIsStillOwedARow() {
        assertEquals(List.of("String.length(n) = 0"), bordersOf("""
                data Subject = String
                    invariant nonNegative = String.length(value) >= 0
                """));
    }

    /** What each border is called, in the order a report shows them. */
    private static List<String> bordersOf(String declaration) {
        return boundariesOf(declaration).stream().map(BorderAssessment::label).toList();
    }

    /**
     * Which clauses are owed a row, by the names their authors gave them.
     *
     * <p>The name and not the whole origin: which of a declaration's clauses a rule is comes from
     * how many are written above it, and that is what two models differ in when one of them writes
     * a floor the other leaves to the carrier.
     */
    private static List<String> clausesOwing(String declaration) {
        return boundariesOf(declaration).stream()
                .map(each -> ((LineOrigin.InvariantOrigin) each.origin()).rule().clause().name()
                        .orElseThrow().toString())
                .sorted().toList();
    }

    /** Which authored lines are owed a row, as the rules that drew them are named. */
    private static List<String> owedBy(String declaration) {
        return boundariesOf(declaration).stream()
                .map(each -> each.origin().toString()).toList();
    }

    private static List<BorderAssessment> boundariesOf(String declaration) {
        Compilation compilation = Compilation.ofSource(sourceOf(declaration), "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        Map<String, List<BorderAssessment>> boundaries =
                Adequacy.boundariesOf(compilation.db(), "example.floor");
        if (boundaries == null) {
            throw new AssertionError("the model under test compiles: " + declaration);
        }
        return boundaries.values().stream().flatMap(List::stream).toList();
    }

    /** Where the length of the string at {@code n} is left, which is the number these clauses are
     *  about. */
    private static NumericDomain.Bounds lengthRangeOf(String declaration) {
        return rangeOf(declaration, NumericTerm.TakenOf.class);
    }

    /** And where the value standing at {@code n} is left. */
    private static NumericDomain.Bounds rangeOf(String declaration) {
        return rangeOf(declaration, NumericTerm.ValueOf.class);
    }

    /** Where one of the position's numbers is left, named by which kind of number it is. */
    private static NumericDomain.Bounds rangeOf(String declaration,
                                                Class<? extends NumericTerm> kind) {
        Position at = positionOf(declaration);
        NumericTerm.FromOnePosition term = at.numbers().stream().filter(kind::isInstance)
                .findFirst().orElseThrow(() -> new AssertionError(
                        "the position has no " + kind.getSimpleName() + " among " + at.numbers()));
        return at.boundsFor(term).rangeLeft();
    }

    private static Position positionOf(String declaration) {
        Compilation compilation = Compilation.ofSource(sourceOf(declaration), "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        return InputDomain.of(sigs.get("take"), rules, ReadAs.THE_COMPILATION_DOES)
                .at(TermPath.of("n"));
    }

    private static String sourceOf(String declaration) {
        return """
                module example.floor

                %s
                data Ok

                behavior take : (n: Subject) -> Ok
                let take (n) = Ok
                """.formatted(declaration);
    }
}
