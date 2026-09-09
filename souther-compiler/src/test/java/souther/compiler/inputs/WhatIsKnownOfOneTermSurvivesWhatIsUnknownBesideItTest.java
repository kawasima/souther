package souther.compiler.inputs;

import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredSig;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.NumberAt;
import souther.compiler.numeric.Count;
import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.LinearForm;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * What is known of one term is not given up for what is unknown beside it.
 *
 * <p>Three things say where a term's values run, and they are not one thing. What the declarations
 * relate it to is one; where the values it is answered from leave it is another; what the term guarantees
 * of itself is a third, and a value a caller has fixed it at is a fourth. Asked as one question of
 * the reading that relates positions, the other three are answered only where that reading happens
 * to have a name for the coordinate — and where it does not, an answer that was in hand is dropped.
 *
 * <p>And it gives them up for whole reasons at a time. The clauses relating positions are read one
 * value at a time, so a coordinate that reading cannot name is one it can say nothing about — and an
 * answer that gave up on its account would give up on every coordinate beside it too: two positions
 * each known to be at least none would add up to something with no floor.
 *
 * <p>So all four go in beside each other and the answer is read out of the four together. Taking one
 * in only narrows, so what comes back is never wider than what the term alone says — which is the
 * direction the whole boundary is written in. What these hold to is the answer and not the way it is
 * arrived at: a fact in hand may not be dropped because the mechanism asked for it declined.
 */
class WhatIsKnownOfOneTermSurvivesWhatIsUnknownBesideItTest {

    /** A position ordered by its own values rather than counted, whose type places a floor. */
    private static final String TEXT = """
            module example.text

            data Code = String
                invariant atLeastA = value >= "A"

            data P = { c: Code, n: Int }

            data Taken

            behavior take : (p: P) -> Taken
            """;

    /** A collection no clause counts, beside a number a clause bounds. */
    private static final String UNCOUNTED = """
            module example.uncounted

            data P = { xs: List<Int>, n: Int }
                invariant none = n >= 0

            data Taken

            behavior take : (p: P) -> Taken
            """;

    /**
     * A floor on a position the arithmetic has no word for is still where its values stop.
     *
     * <p>Read only through the reading that relates positions, a text position has no coordinate to
     * be related by and comes back with nothing said about it — so a line drawn below the floor is
     * one nothing refuses, and the rows for it are asked for at values the position does not hold.
     */
    @Test
    void aFloorOnATextPositionIsWhereItsValuesStop() {
        Read read = read(TEXT);
        Position c = read.inputs().at(TermPath.of("p").then("c"));

        NumericTerm.FromOnePosition values = new NumericTerm.ValueOf(c.path());
        assertEquals(c.boundsFor(values).admissible(),
                read.quantities().runsBetween(values),
                "the position's own answer and the quantity's are about the same values");
    }

    /** And a value fixed at a coordinate no clause names is where that term stands. */
    @Test
    void aTermFixedAtAValueRunsBetweenThatValue() {
        Read read = read(UNCOUNTED);
        NumericTerm size = size(read, "xs");

        assertEquals(at(2, 2), read.quantities().given(size, count(2)).runsBetween(size));
    }

    /** And a form keeps what is known of each of its terms where one of them is unknown. */
    @Test
    void aFormKeepsWhatIsKnownOfTheTermsBesideAnUnknownOne() {
        Read read = read(UNCOUNTED);
        NumericTerm size = size(read, "xs");
        NumericTerm n = new NumericTerm.ValueOf(TermPath.of("p").then("n"));
        Map<NumericTerm, BigDecimal> coefs = new LinkedHashMap<>();
        coefs.put(n, BigDecimal.ONE);
        coefs.put(size, BigDecimal.ONE);

        NumericDomain.Bounds runs = read.quantities()
                .runsBetween(new LinearForm<>(BigDecimal.ZERO, coefs));

        assertEquals(Endpoint.inclusive(count(0)), runs.min(),
                "each of them is at least none, so their sum is");
    }

    /** A collection under a position where the input returns to a declaration already open. */
    private static final String BELOW_WHERE_THE_WALK_STOPS = """
            module example.deep

            data N = Int
                invariant atLeastNone = value >= 0
                invariant atMostFive  = value <= 5

            data Inner = { xs: List<Int> }
            data Mid = { inner: Inner, more: List<Mid> }

            data P = { m: Mid, n: N, ys: List<Int> }

            data Taken

            behavior take : (p: P) -> Taken
            """;

    /**
     * A count under a position the walk stopped at is still never negative.
     *
     * <p>Owned is not the same as known about. The walk that reads an input's positions stops where
     * a path returns to a declaration it has already opened, and nothing stops a rule from naming
     * what is under that — so a term down there is answered for with what it guarantees of itself
     * and nothing else.
     *
     * <p>What it guarantees is the term's to say and not the position's. A count is a whole number
     * whatever it is a count of, and a reading that asked the position how its values are spaced
     * would have nowhere to ask here and would drop the floor for it — leaving a sum of two things
     * each at least none with no floor at all.
     */
    @Test
    void aCountUnderAPositionTheWalkStoppedAtKeepsItsFloor() {
        Read read = read(BELOW_WHERE_THE_WALK_STOPS);
        TermPath deep = TermPath.of("p").then("m").then("more").element().then("inner").then("xs");
        assertNull(read.inputs().at(deep), "the walk stopped at the return above this");
        souther.compiler.types.Type there =
                read.inputs().at(TermPath.of("p").then("ys")).type();
        NumericTerm buried = NumericTerm.TakenOf.of(
                souther.compiler.check.NumericMeasures.takenOf(there, read.rules().symbols()),
                deep, there, read.rules().symbols());
        assertNotNull(buried, "the term is one the operation may be taken of");
        NumericTerm n = new NumericTerm.ValueOf(TermPath.of("p").then("n"));
        Map<NumericTerm, BigDecimal> coefs = new LinkedHashMap<>();
        coefs.put(n, BigDecimal.ONE);
        coefs.put(buried, BigDecimal.ONE);

        NumericDomain.Bounds runs = read.quantities()
                .runsBetween(new LinearForm<>(BigDecimal.ZERO, coefs));

        assertEquals(Endpoint.inclusive(count(0)), runs.min(),
                "each of them is at least none, so their sum is");
    }

    /** A collection a clause counts, so what its count runs between has both ends. */
    private static final String COUNTED = """
            module example.counted

            data P = { xs: List<Int> }
                invariant few = List.length(xs) <= 5

            data Taken

            behavior take : (p: P) -> Taken
            """;

    /**
     * Two operations taken of one location are two coordinates.
     *
     * <p>This held the other way round while a coordinate recorded only that something had been
     * taken here rather than what. Two terms over one path then came to one name, so what a clause
     * said about the count of a list was read as saying the same about anything else taken of it
     * (#1027).
     *
     * <p>Asked of the identity and not through a term, because no term pairs those two operations
     * with one location: what an operation may be taken of is held where a term is made, and a
     * {@code List} is not what a set's size is taken of. An earlier version of this test built the
     * impossible pair by hand to ask the question, which is a test standing on the invariant being
     * open — and the invariant is the thing the reading rests on.
     */
    @Test
    void twoOperationsTakenOfOneLocationAreTwoCoordinates() {
        souther.compiler.check.RuleKey path =
                new souther.compiler.check.RuleKey(List.of("p", "xs"));
        NumberAt<souther.compiler.check.RuleKey> byLength = NumberAt.takenOf(path,
                souther.compiler.types.ValueName.Stdlib.operation("List", "length"));
        NumberAt<souther.compiler.check.RuleKey> bySize = NumberAt.takenOf(path,
                souther.compiler.types.ValueName.Stdlib.operation("Set", "size"));

        assertNotEquals(byLength, bySize,
                "one path, two operations, two numbers — and the claim says which");
        assertNotEquals(byLength, NumberAt.valueOf(path),
                "and neither of them is the value the position holds");
        assertEquals(byLength, NumberAt.takenOf(path,
                        souther.compiler.types.ValueName.Stdlib.operation("List", "length")),
                "while two namings of one number are one coordinate");
    }

    /**
     * One number named twice is weighed twice.
     *
     * <p>The half above cannot show, and the one the translation is really about: a form carries a
     * coefficient per number, and a reading that renamed the keys had to add what two of them
     * brought rather than keep whichever arrived last.
     */
    @Test
    void oneCountNamedWithACoefficientOfTwoRunsToTwiceWhatItIsBounded() {
        Read read = read(COUNTED);
        NumericTerm one = size(read, "xs");

        NumericDomain.Bounds twice = read.quantities().runsBetween(
                new LinearForm<>(BigDecimal.ZERO,
                        Map.of(one, BigDecimal.valueOf(2))));

        assertEquals(Endpoint.inclusive(count(10)), twice.max(),
                "a count held at five, taken twice, runs to ten");
    }

    /** Two positions the record relates, beside a collection no clause counts. */
    private static final String RELATED_BESIDE_AN_UNCOUNTED = """
            module example.beside

            data N = Int
                invariant atLeastNone = value >= 0
                invariant atMostFive  = value <= 5

            data P =
                { xs: List<Int>
                , x: N
                , y: N
                }
                invariant together = x.value + y.value >= 5

            data Taken

            behavior take : (p: P) -> Taken
            """;

    /**
     * And the rule relating two terms survives a third the reading cannot name.
     *
     * <p>A term the reading has no coordinate for is one no relation can be asked about, and that is
     * the whole of what it costs. Asked as two totals instead — everything each term is on its own,
     * against everything the relations leave the whole form — the unnameable term makes the
     * relational total say nothing and takes the relation between the two beside it with it. Meeting
     * does not distribute over addition, so the unit an answer is assembled at is which answer comes
     * out.
     *
     * <p>Held here and not at a report, because no report reaches it today. A coordinate the reading
     * of a value never named is in practice a count nothing takes, and a position whose values are
     * held inside something the walk does not reach into is set aside before any line is drawn on a
     * form over it. What is being held is the boundary's own answer, which is what the next reader
     * of it will get.
     */
    @Test
    void aRuleRelatingTwoTermsSurvivesAThirdThatCannotBeNamed() {
        Read read = read(RELATED_BESIDE_AN_UNCOUNTED);
        NumericTerm size = size(read, "xs");
        NumericTerm x = new NumericTerm.ValueOf(TermPath.of("p").then("x"));
        NumericTerm y = new NumericTerm.ValueOf(TermPath.of("p").then("y"));
        Map<NumericTerm, BigDecimal> coefs = new LinkedHashMap<>();
        coefs.put(size, BigDecimal.ONE);
        coefs.put(x, BigDecimal.ONE);
        coefs.put(y, BigDecimal.ONE);

        NumericDomain.Bounds runs = read.quantities()
                .runsBetween(new LinearForm<>(BigDecimal.ZERO, coefs));

        assertEquals(Endpoint.inclusive(count(5)), runs.min(),
                "the two the record relates come to five, and nothing is negative beside them");
    }

    /** Three counts, two of them related and the third bounded on its own. */
    private static final String THREE_COUNTS = """
            module example.three

            data P =
                { a: List<Int>
                , b: List<Int>
                , c: List<Int>
                }
                invariant oneOfAB = List.length(a) + List.length(b) >= 1
                invariant capC    = List.length(c) <= 10

            data Taken

            behavior take : (p: P) -> Taken
            """;

    /**
     * And a term the rules name, whose floor only the term itself states, does not undo them.
     *
     * <p>A count is never negative and no clause writes that down, so the reading of the clauses has
     * a coordinate for it and no floor under it. Asked for a bound on the three together, that
     * reading may put the third as far below nothing as it likes and answers with no floor at all —
     * and met afterwards against what each of them is on its own, the rule holding the first two at
     * one is gone. Projecting is not distributive over meeting either: what the rules and the terms
     * leave together is narrower than each of them projected and then met.
     */
    @Test
    void aFloorOnlyTheTermStatesDoesNotUndoTheRuleBesideIt() {
        Read read = read(THREE_COUNTS);
        Map<NumericTerm, BigDecimal> coefs = new LinkedHashMap<>();
        coefs.put(size(read, "a"), BigDecimal.ONE);
        coefs.put(size(read, "b"), BigDecimal.ONE);
        coefs.put(size(read, "c"), BigDecimal.ONE);

        NumericDomain.Bounds runs = read.quantities()
                .runsBetween(new LinearForm<>(BigDecimal.ZERO, coefs));

        assertEquals(Endpoint.inclusive(count(1)), runs.min(),
                "two of them come to one, and the third is never negative");
    }

    /** The term for the number that counts what stands at {@code field}, built the way the
     *  compiler builds one: through the factory that holds the operation to what is there. */
    private static NumericTerm size(Read read, String field) {
        TermPath at = TermPath.of("p").then(field);
        souther.compiler.types.Type type = read.inputs().at(at).type();
        NumericTerm.TakenOf made = NumericTerm.TakenOf.of(
                souther.compiler.check.NumericMeasures.takenOf(type, read.rules().symbols()),
                at, type, read.rules().symbols());
        assertNotNull(made, at + " is counted by what its type is counted by");
        return made;
    }

    private static Count count(int at) {
        return new Count(BigDecimal.valueOf(at));
    }

    private static NumericDomain.Bounds at(int least, int most) {
        return new NumericDomain.Bounds(Endpoint.inclusive(count(least)),
                Endpoint.inclusive(count(most)));
    }

    private record Read(InputDomain inputs, RuleReadingSource rules) {
        Quantities quantities() {
            return inputs.quantities(rules);
        }
    }

    private static Read read(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        return new Read(InputDomain.of(sigs.get("take"), rules,
                ReadAs.THE_COMPILATION_DOES), rules);
    }
}
