package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.query.Scopes;
import souther.compiler.ast.Hir;
import souther.compiler.query.Compilation;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;
import souther.compiler.types.TypeSymbol;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One step of the count, taken with the answers about everything else handed in.
 *
 * <p>What every type comes to is settled by rising from "no value" until nothing moves. That rising
 * and this step are two things to get wrong, and a table of steps taken against answers written by
 * hand is what tells them apart: everything below is one declaration read once, with what it reaches
 * stated rather than solved.
 *
 * <p>The rows that matter most are the ones where an answer meets a nothing. A record with a field of
 * no value has no value however little is known about the field beside it, and a sum with a case of
 * no value is as wide as its other case — the same two answers composed the two ways round.
 */
class WhatOneDeclarationComesToUnderWhatIsKnownTest {

    /**
     * An answer of none handed in.
     *
     * <p>What showed it is not read here and could be any of them: a declaration reached by name is
     * read for its count, and the proof it carries is left where it is.
     */
    private static final Cardinality NO_VALUE = Cardinality.none(new Emptiness.ConflictingRules());

    private static Cardinality upperOf(String source, String name, Object... assumed) {
        return upperOf(source, name, def -> def, assumed);
    }

    /** The same, with the declaration handed to {@code as} before it is read. */
    private static Cardinality upperOf(String source, String name,
                                       java.util.function.UnaryOperator<Hir.Def> as,
                                       Object... assumed) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        // A clause nothing could type is a clause nothing reads, and a model with one in it would
        // give every row it appears in the answer of a model with no rules at all. Everything but the
        // refusal these answers are what decides: a model here is meant to hold types with no value.
        assertEquals(java.util.List.of(), compilation.diagnostics().values().stream()
                        .flatMap(java.util.List::stream)
                        .map(each -> each.diagnostic().code().toString())
                        .filter(each -> !each.equals("E1013")).toList(),
                "the model this reads has to be one somebody could write");
        Symbols symbols = Scopes.derived(compilation.db(), "demo").value();
        Map<TypeSymbol, Cardinality> solution = new HashMap<>();
        for (int each = 0; each < assumed.length; each += 2) {
            solution.put(TypeSymbols.declared(new TypeKey(symbols.module(), (String) assumed[each])), (Cardinality) assumed[each + 1]);
        }
        for (Hir.Def def : compilation.module("demo").defs().stream().map(each -> each.declaration().node()).toList()) {
            if (def.name().equals(name)) {
                return CardinalityTransfer.upperOf(
                        TypeSymbols.declared(new TypeKey(symbols.module(), name)), as.apply(def),
                        RuleReadings.of(compilation, "demo"),
                        souther.compiler.query.ReadAs.THE_COMPILATION_DOES,
                        Answers.settled(solution), Set.of());
            }
        }
        throw new IllegalArgumentException("no such declaration: " + name);
    }

    @Test
    void aDeclarationWithNoFieldsIsOneValue() {
        assertEquals(Cardinality.atMost(1), upperOf("""
                module demo

                data U
                """, "U"));
    }

    @Test
    void aBooleanIsTwo() {
        assertEquals(Cardinality.atMost(2), upperOf("""
                module demo

                data Flag = Bool
                """, "Flag"));
    }

    @Test
    void aNumberIsAsManyValuesAsItsRulesLeave() {
        String source = """
                module demo

                data One = Int
                    invariant only = value >= 1 && value <= 1

                data Any = Int

                data Money = Decimal
                    invariant range = value >= 1.0m && value <= 10.0m
                """;
        assertEquals(Cardinality.atMost(1), upperOf(source, "One"));
        assertEquals(Cardinality.UNKNOWN, upperOf(source, "Any"), "open at both ends");
        assertEquals(Cardinality.UNKNOWN, upperOf(source, "Money"), "and spaced too finely to count");
    }

    @Test
    void rulesThatCannotAllHoldLeaveNoValue() {
        assertTrue(upperOf("""
                module demo

                data Bad = Int
                    invariant no = value >= 2 && value <= 1
                """, "Bad").none());
    }

    @Test
    void aRecordIsItsFieldsMultipliedTogether() {
        String source = """
                module demo

                data One = Int
                    invariant only = value >= 1 && value <= 1

                data Any = Int

                data Both = { a: One, b: One }

                data Beside = { a: Any, b: One }
                """;
        assertEquals(Cardinality.atMost(1), upperOf(source, "Both", "One", Cardinality.atMost(1)));
        assertEquals(Cardinality.UNKNOWN, upperOf(source, "Beside",
                "One", Cardinality.atMost(1), "Any", Cardinality.UNKNOWN));
    }

    /** The row the product exists for: what has no value takes the record with it. */
    @Test
    void aRecordWithAFieldOfNoValueHasNoValueHoweverLittleIsKnownBeside() {
        assertTrue(upperOf("""
                module demo

                data Any = Int

                data Empty = Int

                data Beside = { a: Any, b: Empty }
                """, "Beside", "Any", Cardinality.UNKNOWN, "Empty", NO_VALUE).none());
    }

    @Test
    void aSumIsItsCasesAddedUp() {
        String source = """
                module demo

                data Left
                data Right
                data Either = Left | Right
                """;
        assertEquals(Cardinality.atMost(2), upperOf(source, "Either",
                "Left", Cardinality.atMost(1), "Right", Cardinality.atMost(1)));
        assertTrue(upperOf(source, "Either",
                "Left", NO_VALUE, "Right", NO_VALUE).none());
    }

    /**
     * A case resolution read and found nothing for leaves the sum unknown, whatever the rest come to.
     *
     * <p>What a name nobody could resolve stands for is not none: it is a case this reading never
     * took in, and adding nothing for it says the sum has no value on the strength of a shape that
     * was never read. Every other arm here answers that way already — a type standing for another, a
     * form only an expression reaches — and the sum is where the same rule was not applied.
     *
     * <p>The refusal that follows is what makes it worth saying. A sum answered none is refused where
     * it is declared, so a model with one name spelled wrong is told the name and then told the type
     * cannot be built.
     */
    @Test
    void aSumWithACaseThatNamesNothingIsUnknownAndNotNone() {
        String source = """
                module demo

                data Left
                data Right
                data Either = Left | Right
                """;
        assertEquals(Cardinality.UNKNOWN, upperOf(source, "Either",
                        def -> {
                            Hir.SumData sum = (Hir.SumData) def;
                            return new Hir.SumData(sum.written(), sum.declares(),
                                    java.util.List.of(sum.cases().get(0).unanswered(), sum.cases().get(1)),
                                    sum.pos());
                        },
                        "Left", Cardinality.atMost(1), "Right", NO_VALUE),
                "one case nothing names, and the other with no value");
    }

    /** And the same two answers the other way round: a case of no value adds none. */
    @Test
    void aSumWithACaseOfNoValueIsAsWideAsItsOtherCase() {
        assertEquals(Cardinality.UNKNOWN, upperOf("""
                module demo

                data Left
                data Right
                data Either = Left | Right
                """, "Either", "Left", Cardinality.UNKNOWN, "Right", NO_VALUE));
    }

    /** A `None` is a value of an optional whatever it wraps, including a type nothing can build. */
    @Test
    void anOptionalOfSomethingWithNoValueIsStillOneValue() {
        assertEquals(Cardinality.atMost(1), upperOf("""
                module demo

                data Empty = Int

                data Held = { x: Empty? }
                """, "Held", "Empty", NO_VALUE));
    }

    @Test
    void aSetAsksItsElementForAsManyValuesAsItHolds() {
        String source = """
                module demo

                data One = Int
                    invariant only = value >= 1 && value <= 1

                data Two = Set<One>
                    invariant two = Set.size(value) >= 2

                data Just = Set<One>
                    invariant one = Set.size(value) == 1

                data Any = Set<One>
                """;
        assertTrue(upperOf(source, "Two", "One", Cardinality.atMost(1)).none(),
                "two of a value there is one of");
        assertEquals(Cardinality.atMost(1), upperOf(source, "Just", "One", Cardinality.atMost(1)));
        assertEquals(Cardinality.atMost(2), upperOf(source, "Any", "One", Cardinality.atMost(1)),
                "the empty set and the one holding it");
    }

    /** A list holds a value over again, so how many values it has is a question about its length. */
    @Test
    void aListOfOneValueIsAsManyListsAsItMayBeLong() {
        String source = """
                module demo

                data One = Int
                    invariant only = value >= 1 && value <= 1

                data Many = List<One>
                    invariant two = List.length(value) >= 2

                data Just = List<One>
                    invariant one = List.length(value) == 2
                """;
        assertEquals(Cardinality.UNKNOWN, upperOf(source, "Many", "One", Cardinality.atMost(1)),
                "left long enough to be past counting");
        assertEquals(Cardinality.atMost(1), upperOf(source, "Just", "One", Cardinality.atMost(1)));
    }

    @Test
    void aMapIsFiniteOnlyWhereItHoldsNothing() {
        String source = """
                module demo

                data Empty = Int

                data One = Int
                    invariant only = value >= 1 && value <= 1

                data Bare = { m: Map<String, Empty> }
                    invariant empty = Map.size(m) == 0

                data Holding = { m: Map<String, Empty> }
                    invariant some = Map.size(m) >= 1

                data Keyed = { m: Map<String, One> }
                    invariant some = Map.size(m) >= 1
                """;
        assertEquals(Cardinality.atMost(1), upperOf(source, "Bare", "Empty", NO_VALUE));
        assertTrue(upperOf(source, "Holding", "Empty", NO_VALUE).none());
        assertEquals(Cardinality.UNKNOWN, upperOf(source, "Keyed", "One", Cardinality.atMost(1)),
                "there is no end of keys to hold it under");
    }

    /**
     * A collection left with nowhere to put anything is the empty one, which is a value. Read before
     * the element, so a type nothing can build still leaves a collection of it something to be.
     */
    @Test
    void aCollectionAdmittingOnlyTheEmptyOneIsOneValue() {
        String source = """
                module demo

                data Empty = Int

                data NoSet = { s: Set<Empty> }
                    invariant empty = Set.size(s) == 0

                data NoList = { l: List<Empty> }
                    invariant empty = List.length(l) == 0
                """;
        assertEquals(Cardinality.atMost(1), upperOf(source, "NoSet", "Empty", NO_VALUE));
        assertEquals(Cardinality.atMost(1), upperOf(source, "NoList", "Empty", NO_VALUE));
    }

    /** A floor a record wrote about a field reaches the collection the field's name wraps. */
    @Test
    void aFloorWrittenAtTheFieldIsReadThereAndNotAtTheNamesOwnDeclaration() {
        assertTrue(upperOf("""
                module demo

                data One = Int
                    invariant only = value >= 1 && value <= 1

                data Held = Set<One>

                data Outer = { held: Held }
                    invariant two = Set.size(held.value) >= 2
                """, "Outer", "One", Cardinality.atMost(1), "Held", Cardinality.UNKNOWN).none());
    }
}
