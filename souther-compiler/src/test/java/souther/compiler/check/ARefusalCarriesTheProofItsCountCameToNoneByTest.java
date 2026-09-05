package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.query.Scopes;
import souther.compiler.query.Compilation;
import souther.compiler.types.TypeSymbol;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a group with no value is reported as having been shown by.
 *
 * <p>A refusal was one sentence for as long as nothing carried an answer to "how was this shown", so
 * every way of coming to no value was told the way one of them came to it. What is asserted here is
 * the proof itself and not the sentence: the proof is what travels with the count, and the sentence
 * is chosen from it in one place.
 *
 * <p>The proof read is the one the group is established by — everything outside it granted — and it
 * is taken once the declaration to report at is settled. Which member of a group reaches the others
 * how is that member's own, so a proof taken before the anchor is settled would suggest to one member
 * what is true of another.
 */
class ARefusalCarriesTheProofItsCountCameToNoneByTest {

    private static List<UninhabitableTypes.UninhabitableGroup> reported(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        assertEquals(List.of(), compilation.diagnostics().values().stream()
                        .flatMap(List::stream).map(each -> each.diagnostic().code().toString())
                        .filter(each -> !each.equals("E1013")).toList(),
                "the model this reads has to be one somebody could write");
        List<souther.compiler.ast.Hir.Def> defs =
                compilation.module("demo").defs().stream().map(each -> each.declaration().node()).toList();
        return UninhabitableTypes.withNoValueOfTheirOwn(defs,
                TypeCardinality.solve(defs, RuleReadings.of(compilation, "demo"),
                        souther.compiler.query.ReadAs.THE_COMPILATION_DOES));
    }

    private static Emptiness only(String source) {
        List<UninhabitableTypes.UninhabitableGroup> reported = reported(source);
        assertEquals(1, reported.size(), "one group to say something about");
        return reported.get(0).why();
    }

    /** What every declaration of the model was shown by, before any group is separated out. */
    private static String shownBy(String source, String name) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        List<souther.compiler.ast.Hir.Def> defs =
                compilation.module("demo").defs().stream().map(each -> each.declaration().node()).toList();
        Symbols symbols = Scopes.derived(compilation.db(), "demo").value();
        return shape(TypeCardinality.solve(defs, RuleReadings.of(compilation, "demo"),
                souther.compiler.query.ReadAs.THE_COMPILATION_DOES).of(
                souther.compiler.types.TypeSymbols.declared(
                        new souther.compiler.types.TypeKey(symbols.module(), name))).why());
    }

    /** A proof written out, short enough to read in an assertion. */
    private static String shape(Emptiness why) {
        return switch (why) {
            case null -> "has values";
            case Emptiness.NoBaseInComponent it -> "no base in " + named(it.members());
            case Emptiness.AtAField it -> switch (it.where()) {
                case Emptiness.AtAField.Where.TheValueItself _ -> "value";
                case Emptiness.AtAField.Where.In(String spelled) -> spelled;
            } + " " + shape(it.under());
            case Emptiness.AtEqualPositions it -> it.where().stream().map(each -> switch (each) {
                case Emptiness.AtAField.Where.TheValueItself _ -> "value";
                case Emptiness.AtAField.Where.In(String spelled) -> spelled;
            }).toList() + " " + shape(it.under());
            case Emptiness.TheNameHasNone it -> "which is " + it.name().name();
            case Emptiness.NonEmptyCollectionWithNoElement it -> "cannot be empty, " + shape(it.element());
            case Emptiness.AcrossEveryCase it ->
                    "every case " + it.cases().stream().map(each -> shape(each)).toList();
            case Emptiness.ConflictingRules _ -> "rules contradict";
            case Emptiness.EmptyNumericInterval _ -> "an empty range";
            case Emptiness.EmptyOrderedInterval _ -> "ends with nothing between them";
            case Emptiness.NoAllowedValueInRange _ -> "no allowed value in the range";
            case Emptiness.NoAllowedValueWithinRequiredBounds _ ->
                    "no allowed value within the bounds required";
            case Emptiness.AtPositionsHeldApart it -> it.where().stream().map(each -> switch (each) {
                case Emptiness.AtAField.Where.TheValueItself _ -> "value";
                case Emptiness.AtAField.Where.In(String spelled) -> spelled;
            }).toList() + " " + shape(it.under());
            case Emptiness.NoCommonValueForEqualPositions _ -> "no value they can all hold";
            case Emptiness.NoDistinctValuesForPositionsHeldApart _ ->
                    "no values they can all differ in";
            case Emptiness.SetRequiresTooManyDistinctValues it -> "a set over at most " + it.available();
            case Emptiness.NoAllowedCollectionSize _ -> "no size";
        };
    }

    private static List<String> named(List<TypeSymbol> these) {
        return these.stream().map(TypeSymbol::name).toList();
    }

    /**
     * A position bounded above a value it is bounded below of, carried as that and with its place.
     *
     * <p>Nearer than {@link Emptiness.ConflictingRules}, which says the rules contradict and nothing
     * further. Two domains hold this contradiction — the interval algebra reads the numbers and the
     * ordering reads the ends — and which proof is carried has to be the one that can say what it
     * found, whichever of them a reader happens to ask first.
     */
    @Test
    void endsWithNothingBetweenThemAreCarriedAsThatAndNotAsRulesContradicting() {
        assertEquals(new Emptiness.AtAField(new Emptiness.AtAField.Where.TheValueItself(),
                        new Emptiness.EmptyOrderedInterval()), only("""
                module demo

                data Bad = Int
                    invariant no = value >= 2 && value <= 1
                """));
    }

    /**
     * And a position on an order the numbers do not carry is shown by the same proof.
     *
     * <p>The whole point of the two issues this closes: one shape, one proof, one sentence. A date
     * bounded above a date it is bounded below of was not refused at all, and refusing it under the
     * general sentence would leave the same model told two different things depending on what
     * carries it.
     */
    @Test
    void anOrderTheNumbersDoNotCarryIsShownByTheSameProof() {
        assertEquals(new Emptiness.AtAField(new Emptiness.AtAField.Where.TheValueItself(),
                        new Emptiness.EmptyOrderedInterval()), only("""
                module demo

                data Bad = Date
                    invariant no = value >= Date("2020-01-01") && value <= Date("2010-01-01")
                """));
    }

    /** And the place named is the field, where the rules were written about one. */
    @Test
    void thePlaceAnEmptyOrderNamesIsTheFieldTheRulesBound() {
        assertEquals(new Emptiness.AtAField(new Emptiness.AtAField.Where.In("at"),
                        new Emptiness.EmptyOrderedInterval()), only("""
                module demo

                data Held = { at: Date }
                    invariant no = at >= Date("2020-01-01") && at <= Date("2010-01-01")
                """));
    }

    /**
     * The position a choice names is the one every alternative leaves nothing at.
     *
     * <p>Two situations were answered with one rule. An alternative nobody can take leaves the
     * answer to the others, and its evidence goes with it; where <em>every</em> alternative is
     * impossible, no one of them speaks for the rest, and taking the first one found impossible out
     * of the answer settles the proof by the order the operands were written in.
     *
     * <p>Nor may they be met. A meet is a conjunction and the alternatives were never stated
     * together: both alternatives here are impossible because of {@code a}, and met they are a
     * {@code b} bounded at 0 and at 1 — a contradiction neither alternative contains, at a position
     * the rules are fine with. {@code b} is declared first, so the refusal was written about it.
     */
    @Test
    void thePositionAChoiceNamesIsTheOneEveryAlternativeLeavesNothingAt() {
        Emptiness expected = new Emptiness.AtAField(new Emptiness.AtAField.Where.In("a"),
                new Emptiness.EmptyOrderedInterval());
        assertEquals(expected, only("""
                module demo

                data X = { b: Int, a: String }
                    invariant no = (a < "" && b == 0) || (a < "" && b == 1)
                """));
        assertEquals(expected, only("""
                module demo

                data X = { b: Int, a: String }
                    invariant no = (a < "" && b == 1) || (a < "" && b == 0)
                """), "and the operands the other way round");
    }

    /**
     * And where the alternatives leave nothing at different positions, none of them is named.
     *
     * <p>A choice between {@code a < ""} and {@code b < ""} admits nothing, and neither position is
     * one the choice leaves empty: each alternative admits every value of the other's. So what can
     * be said is that the rules cannot all hold, which is the general form and is what a reading
     * that could show a contradiction and no more leaves.
     */
    @Test
    void aChoiceWhoseAlternativesFailAtDifferentPositionsNamesNone() {
        assertEquals(new Emptiness.ConflictingRules(), only("""
                module demo

                data X = { a: String, b: String }
                    invariant no = a < "" || b < ""
                """));
        assertEquals(new Emptiness.ConflictingRules(), only("""
                module demo

                data X = { a: String, b: String }
                    invariant no = b < "" || a < ""
                """), "and the operands the other way round");
    }

    /** And the same where the two alternatives are shown impossible by different readings. */
    @Test
    void aChoiceShownImpossibleByTwoDifferentReadingsNamesNoPosition() {
        assertEquals(new Emptiness.ConflictingRules(), only("""
                module demo

                data X = { s: String, b: Bool }
                    invariant no = s < "" || (b == true && b == false)
                """));
        assertEquals(new Emptiness.ConflictingRules(), only("""
                module demo

                data X = { s: String, b: Bool }
                    invariant no = (b == true && b == false) || s < ""
                """), "and the operands the other way round");
    }

    /** Rules that contradict in a way no range holds, which is the general form. */
    @Test
    void rulesThatCannotAllHoldAreCarriedAsThat() {
        assertEquals(new Emptiness.ConflictingRules(), only("""
                module demo

                data Bad = String
                    invariant no = String.matches("[A-Z]+", value)
                        && Bool.not(String.matches("[A-Z]+", value))
                """));
    }

    /** A set too small for what it holds, carried with the bound the comparison was made against. */
    @Test
    void aSetTooSmallForItsElementIsCarriedWithBothCounts() {
        assertEquals(new Emptiness.AtAField(new Emptiness.AtAField.Where.TheValueItself(),
                        new Emptiness.SetRequiresTooManyDistinctValues(1)), only("""
                module demo

                data One = Int
                    invariant only = value >= 1 && value <= 1

                data Pair = Set<One>
                    invariant two = Set.size(value) >= 2
                """));
    }

    /** And the same at a field, where the position the proof names is the field. */
    @Test
    void thePositionAProofNamesIsWhereTheRulesWereWritten() {
        assertEquals(new Emptiness.AtAField(new Emptiness.AtAField.Where.In("pair"),
                        new Emptiness.SetRequiresTooManyDistinctValues(1)), only("""
                module demo

                data One = Int
                    invariant only = value >= 1 && value <= 1

                data Holder = { pair: Set<One> }
                    invariant two = Set.size(pair) >= 2
                """));
    }

    /**
     * A cycle is shown by the rising having stopped with all of them at nothing.
     *
     * <p>Not by either of them naming the other. That {@code A} has no value because {@code B} has
     * none and {@code B} because {@code A} has is two true statements and no proof, and it is what
     * the reading has to hand while it is still running. What makes it a proof is the least fixed
     * point having been reached, which is the one thing neither declaration says.
     */
    @Test
    void aCycleIsCarriedAsTheRisingHavingStoppedWithNothingShown() {
        Emptiness why = only("""
                module demo

                data A = { b: B }
                data B = { a: A }
                """);
        if (!(why instanceof Emptiness.NoBaseInComponent it)) {
            throw new AssertionError("shown by the rising and not by one of them naming the other: " + why);
        }
        assertEquals(List.of("A", "B"), named(it.members()));
        assertEquals(new Emptiness.AtAField.Where.In("b"),
                ((Emptiness.AtAField) it.through()).where(),
                "and how the one reported reaches the rest, which is what a suggestion is read off");
    }

    /**
     * A member of a cycle with a lack of its own is separated out and keeps its own proof.
     *
     * <p>Both readings run over the same declarations. What tells them apart is granting: {@code Bad}
     * has none with everything else granted, and {@code A} and {@code B} still have none with
     * {@code Bad} granted.
     */
    @Test
    void aLackOfItsOwnBesideACycleKeepsBothProofs() {
        List<UninhabitableTypes.UninhabitableGroup> reported = reported("""
                module demo

                data Bad = Int
                    invariant no = value >= 2 && value <= 1

                data A = { b: B }
                data B = { a: A, bad: Bad }
                """);
        assertEquals(List.of(List.of("Bad"), List.of("A", "B")),
                reported.stream().map(each -> named(each.members())).toList());
        assertEquals(new Emptiness.AtAField(new Emptiness.AtAField.Where.TheValueItself(),
                new Emptiness.EmptyOrderedInterval()), reported.get(0).why());
        if (!(reported.get(1).why() instanceof Emptiness.NoBaseInComponent)) {
            throw new AssertionError("the cycle is left with nothing to bottom out: "
                    + reported.get(1).why());
        }
    }

    /**
     * A lack of its own inside a cycle is that member's, and so is what holds it only through them.
     *
     * <p>All three are answered in one rising and all three come to none, and what tells them apart
     * is what each was shown by. The set cannot be filled whatever the rest of the cycle comes to,
     * so `A` has been shown something on its own; `B` and `C` have been shown nothing but that `A`
     * has none, and they come right when it does.
     *
     * <p>A reading that took having reached the cycle for having leaned on it puts all three in it.
     * That is why what is read is the proof and not what the reading touched: `A`'s reading does
     * reach `C`, and reaching is not resting on.
     */
    @Test
    void aLackOfItsOwnInsideACycleIsNotTheCycles() {
        List<UninhabitableTypes.UninhabitableGroup> reported = reported("""
                module demo

                data One = Int
                    invariant only = value >= 1 && value <= 1

                data A = { s: Set<One>, c: C }
                    invariant two = Set.size(s) >= 2
                data B = { a: A }
                data C = { b: B }
                """);
        assertEquals(List.of(List.of("A")), reported.stream()
                        .map(each -> named(each.members())).toList(),
                "`B` and `C` come right when `A` does, so nothing is said of them");
        assertEquals(new Emptiness.AtAField(new Emptiness.AtAField.Where.In("s"),
                new Emptiness.SetRequiresTooManyDistinctValues(1)), reported.get(0).why());
    }

    /**
     * And the same read before any group is separated out, which is where it can go wrong.
     *
     * <p>Separating the group grants everything outside it, so the reading that establishes a group
     * reaches nothing unshown and comes out right whichever way the proofs are read. What is asked
     * here is the answer the rising itself arrived at, with all three still in one another's way.
     */
    @Test
    void andTheSameOfTheRisingsOwnAnswers() {
        String source = """
                module demo

                data One = Int
                    invariant only = value >= 1 && value <= 1

                data A = { s: Set<One>, c: C }
                    invariant two = Set.size(s) >= 2
                data B = { a: A }
                data C = { b: B }
                """;
        assertEquals("s a set over at most 1", shownBy(source, "A"));
        assertEquals("a which is A", shownBy(source, "B"));
        assertEquals("b which is B", shownBy(source, "C"));
    }

    /**
     * Rules of a declaration's own are nearer than anything a field of it lacks.
     *
     * <p>Both are true of this one at once. Which is carried has to be settled by something other
     * than which the reading reached first, or the same model is refused for a different reason each
     * time a field moves.
     */
    @Test
    void aLackOfItsOwnIsNearerThanOneItReaches() {
        String before = """
                module demo

                data Bad = Int
                    invariant no = value >= 2 && value <= 1

                data Both = { held: Bad, n: Int }
                    invariant no = n >= 2 && n <= 1
                """;
        String after = """
                module demo

                data Bad = Int
                    invariant no = value >= 2 && value <= 1

                data Both = { n: Int, held: Bad }
                    invariant no = n >= 2 && n <= 1
                """;
        assertEquals(new Emptiness.AtAField(new Emptiness.AtAField.Where.In("n"), new Emptiness.EmptyOrderedInterval()),
                reported(before).get(1).why());
        assertEquals(new Emptiness.AtAField(new Emptiness.AtAField.Where.In("n"), new Emptiness.EmptyOrderedInterval()),
                reported(after).get(1).why(), "and the fields the other way round");
    }
}
