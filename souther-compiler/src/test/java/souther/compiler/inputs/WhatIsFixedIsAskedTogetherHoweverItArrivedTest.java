package souther.compiler.inputs;

import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredSig;
import souther.compiler.check.InvariantChecker;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.Emptiness;
import souther.compiler.numeric.Count;
import souther.compiler.numeric.LinearForm;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fixing a position refines what is asked, and what is asked is the whole of what was fixed.
 *
 * <p>Three searches condition this input's rules, each fixing positions in an order of its own. What
 * the rules leave may not depend on that order, or two of them asking the same thing get two answers
 * and neither is wrong about anything a reader could check. So the answers are held to an algebra:
 * fixing nothing changes nothing, fixing twice is fixing both, and the two orders agree.
 *
 * <p>Held of what is proved empty as well as of what is left. A proof reached by one route and not
 * by another would make which question was asked first into something a caller can see, and the
 * routes are a cache and an arithmetic — neither of which the model has anything to do with.
 *
 * <p>Not a check that the reading is made a particular way. Conditioning that kept every relation
 * would satisfy this however it was carried, and that is the point: what is fixed here is the
 * meaning, so an implementation may change under it.
 */
class WhatIsFixedIsAskedTogetherHoweverItArrivedTest {

    /** Two fields each running from none to five, held together at five by the record. */
    private static final String SOURCE = """
            module example.together

            data N = Int
                invariant atLeastNone = value >= 0
                invariant atMostFive  = value <= 5

            data P = { x: N, y: N }
                invariant together = x.value + y.value <= 5

            data Taken

            behavior take : (p: P) -> Taken
            """;

    private static final NumericTerm X = new NumericTerm.ValueOf(TermPath.of("p").then("x"));
    private static final NumericTerm Y = new NumericTerm.ValueOf(TermPath.of("p").then("y"));

    /**
     * The relation crosses the boundary, which is the whole point of asking here.
     *
     * <p>Read a position at a time, {@code y} runs to five whatever {@code x} holds. The record says
     * otherwise, and with {@code x} at four there is one value left for {@code y}.
     */
    @Test
    void fixingOnePositionNarrowsTheOneItIsRelatedTo() {
        Quantities read = quantities();

        assertEquals(bounds(0, 5), read.runsBetween(Y));
        assertEquals(bounds(0, 1), read.given(X, count(4)).runsBetween(Y));
    }

    /** A form is bounded by the rule relating its positions, and not by the box of the two. */
    @Test
    void aFormIsBoundedByWhatRelatesItsPositions() {
        assertEquals(bounds(0, 5), quantities().runsBetween(sum()));
    }

    @Test
    void fixingNothingAsksTheSameThing() {
        Quantities read = quantities();

        assertEquals(read.runsBetween(sum()), read.given(Map.of()).runsBetween(sum()));
        assertEquals(read.runsBetween(Y), read.given(Map.of()).runsBetween(Y));
    }

    @Test
    void fixingTwiceIsFixingBoth() {
        Quantities read = quantities();
        Quantities apart = read.given(X, count(2)).given(Y, count(1));
        Quantities together = read.given(fixing(X, 2, Y, 1));

        assertEquals(together.runsBetween(sum()), apart.runsBetween(sum()));
        assertEquals(together.runsBetween(X), apart.runsBetween(X));
        assertEquals(together.emptiness(), apart.emptiness());
    }

    @Test
    void theOrderTheyWereFixedInDoesNotShow() {
        Quantities read = quantities();
        Quantities one = read.given(X, count(2)).given(Y, count(1));
        Quantities other = read.given(Y, count(1)).given(X, count(2));

        assertEquals(one.runsBetween(sum()), other.runsBetween(sum()));
        assertEquals(one.runsBetween(X), other.runsBetween(X));
        assertEquals(one.emptiness(), other.emptiness());
    }

    /** And the same of a pair the rules refuse, which is proved either way round. */
    @Test
    void theOrderDoesNotShowWhereNothingIsLeftEither() {
        Quantities read = quantities();
        Quantities one = read.given(X, count(4)).given(Y, count(4));
        Quantities other = read.given(Y, count(4)).given(X, count(4));

        assertTrue(one.emptiness().isPresent(), "x = 4 beside y = 4 comes to eight, and the record"
                + " holds the pair at five");
        assertEquals(one.emptiness(), other.emptiness());
    }

    /** One position holds one value, so fixing it twice fixes it at nothing. */
    @Test
    void twoValuesAtOnePositionLeaveNothing() {
        Quantities read = quantities().given(X, count(1)).given(X, count(2));

        assertEquals(new EmptyInput.TwoValuesAtOnePosition(X, count(1), count(2)),
                read.emptiness().orElseThrow());
    }

    /** A value outside where the position runs is proved impossible against where it runs. */
    @Test
    void aValueOutsideWhereThePositionRunsLeavesNothing() {
        assertTrue(quantities().given(X, count(9)).emptiness().isPresent());
    }

    /**
     * Nothing proved is not a value proved to exist.
     *
     * <p>The pair below is one the rules do leave, and the answer is empty. Read the other way, a
     * search would close no branch it should and open every branch it should not — so what this
     * holds is that the two readings are told apart at all.
     */
    @Test
    void aPairTheRulesLeaveIsNotProvedEmpty() {
        assertEquals(java.util.Optional.empty(),
                quantities().given(fixing(X, 2, Y, 1)).emptiness());
    }

    /**
     * A term at a path this input does not have is the caller's mistake.
     *
     * <p>Not an emptiness and not an unbounded answer: either would be a bug wearing the words of
     * something the model said.
     */
    @Test
    void atermOfAnotherInputIsRefused() {
        NumericTerm elsewhere = new NumericTerm.ValueOf(TermPath.of("q").then("x"));

        assertThrows(IllegalArgumentException.class,
                () -> quantities().runsBetween(elsewhere));
        assertThrows(IllegalArgumentException.class,
                () -> quantities().given(elsewhere, count(1)));
    }

    /** And a path this input does have is not refused, so the check above is about the path. */
    @Test
    void atermOfThisInputIsNotRefused() {
        assertNotEquals(null, quantities().runsBetween(X));
    }

    /** A record whose field is a list, so that a number inside the list is under a value of its
     *  own. */
    private static final String INSIDE_A_LIST = """
            module example.inside

            data N = Int
                invariant atLeastNone = value >= 0

            data Item = { charge: N }

            data Cart = { items: List<Item> }

            data Taken

            behavior take : (c: Cart) -> Taken
            """;

    /**
     * A number inside a sequence is asked about, and is not a place this reading has no name for.
     *
     * <p>What names it is the value it is a field of, and the reading roots that value inside the
     * sequence — so the step into the sequence is above the root and what is left below it is a
     * field. A reading that took the parameter for the root would have {@code items[*].charge} to
     * name, which is no name any rule of the parameter writes, and the quantity would be one
     * nothing could be asked about.
     */
    @Test
    void aNumberInsideASequenceIsUnderAValueWhoseRulesNameIt() {
        Read read = read(INSIDE_A_LIST, "take");
        Quantities asked = read.inputs().quantities(read.rules());
        NumericTerm charge = new NumericTerm.ValueOf(
                TermPath.of("c").then("items").element().then("charge"));

        assertNotEquals(null, asked.runsBetween(charge),
                "the number inside the list is one this reading answers about");
        assertNotEquals(null, asked.given(charge, count(1)),
                "and one it can be told a value for");
    }

    private static LinearForm<NumericTerm> sum() {
        Map<NumericTerm, BigDecimal> coefs = new LinkedHashMap<>();
        coefs.put(X, BigDecimal.ONE);
        coefs.put(Y, BigDecimal.ONE);
        return new LinearForm<>(BigDecimal.ZERO, coefs);
    }

    private static Map<NumericTerm, Count> fixing(NumericTerm one, int at,
                                                  NumericTerm other, int also) {
        Map<NumericTerm, Count> out = new LinkedHashMap<>();
        out.put(one, count(at));
        out.put(other, count(also));
        return out;
    }

    private static Count count(int at) {
        return new Count(BigDecimal.valueOf(at));
    }

    private static NumericDomain.Bounds bounds(int least, int most) {
        return new NumericDomain.Bounds(
                souther.compiler.numeric.Endpoint.inclusive(count(least)),
                souther.compiler.numeric.Endpoint.inclusive(count(most)));
    }

    /**
     * A count is never negative, and no clause anywhere writes that down.
     *
     * <p>The one fixing the declarations cannot be told about. Their reading settles positions and a
     * count taken of one is not a position it settles, so nothing there refuses this — what refuses
     * it is what the term guarantees of itself, which is part of what is asked here.
     */
    @Test
    void aCountFixedBelowNoneLeavesNothingThoughNoClauseSaysSo() {
        Read read = read(BAG, "take");
        NumericTerm size = takenOfWhatIsThere(read, TermPath.of("b").then("xs"));
        Quantities asked = read.inputs().quantities(read.rules());

        assertTrue(asked.given(size, count(-1)).emptiness().isPresent());
        assertEquals(java.util.Optional.empty(), asked.given(size, count(1)).emptiness());
    }

    /**
     * Two counts the record relates, so that fixing one says something about the other.
     *
     * <p>A count is a number of a position and not a position, and the two are filed under different
     * names by the reading that makes them. A fixing that reached only the first left a rule over two
     * counts unconditioned while the same rule was read whole when it was asked about — so the answer
     * to "where does this run" knew the relation and the answer to "what is left once that is fixed"
     * did not.
     */
    private static final String COUNTED = """
            module example.counted

            data Possible =
                { accounts: List<Int>
                , contacts: List<Int>
                }
                invariant oneOfThem =
                    List.length(accounts) + List.length(contacts) >= 1

            data Taken

            behavior take : (p: Possible) -> Taken
            """;

    @Test
    void fixingOneCountNarrowsTheCountItIsRelatedTo() {
        Read read = read(COUNTED, "take");
        Quantities asked = read.inputs().quantities(read.rules());
        NumericTerm accounts = size(read, "accounts");
        NumericTerm contacts = size(read, "contacts");

        assertEquals(new Count(BigDecimal.ZERO),
                asked.runsBetween(contacts).min().at(), "nothing bounds it on its own");
        assertEquals(new Count(BigDecimal.ONE),
                asked.given(accounts, count(0)).runsBetween(contacts).min().at());
    }

    @Test
    void twoCountsTheRuleRefusesTogetherLeaveNothing() {
        Read read = read(COUNTED, "take");
        Quantities asked = read.inputs().quantities(read.rules());
        NumericTerm accounts = size(read, "accounts");
        NumericTerm contacts = size(read, "contacts");

        assertTrue(asked.given(accounts, count(0)).given(contacts, count(0))
                .emptiness().isPresent());
    }

    /** And the algebra holds of a count as much as of a value, which is where the two came apart. */
    @Test
    void theAlgebraHoldsOfACountToo() {
        Read read = read(COUNTED, "take");
        Quantities asked = read.inputs().quantities(read.rules());
        NumericTerm accounts = size(read, "accounts");
        NumericTerm contacts = size(read, "contacts");
        Quantities one = asked.given(accounts, count(0)).given(contacts, count(2));
        Quantities other = asked.given(contacts, count(2)).given(accounts, count(0));
        Quantities together = asked.given(fixing(accounts, 0, contacts, 2));

        assertEquals(together.runsBetween(contacts), one.runsBetween(contacts));
        assertEquals(together.runsBetween(accounts), other.runsBetween(accounts));
        assertEquals(one.runsBetween(accounts), other.runsBetween(accounts));
        assertEquals(one.emptiness(), other.emptiness());
        assertEquals(together.emptiness(), one.emptiness());
    }

    /**
     * Settling a position does not read the declarations again, whenever it is asked about.
     *
     * <p>A settling is an equality on one number taken onto everything else the clauses came to,
     * and the reading states it that way itself at the end of its own work. So the clauses have
     * been read when the input was read, and a caller fixing a position — or fixing four of them
     * one at a time and asking after each — is arriving where the reading already is rather than
     * paying for it again.
     *
     * <p>Counted rather than timed. What is held is the shape of the thing: a search that fixes a
     * position per step down a box would read a declaration per step, and how fast one reading is
     * would decide whether that mattered.
     */
    @Test
    void settlingAPositionDoesNotReadTheDeclarationsAgain() {
        Read read = read(SOURCE, "take");
        Quantities asked = read.inputs().quantities(read.rules());
        long before = InvariantChecker.readingsMade();

        Quantities twice = asked.given(X, count(1)).given(Y, count(1));
        twice.runsBetween(sum());
        twice.runsBetween(Y);
        twice.emptiness();

        assertEquals(before, InvariantChecker.readingsMade());
    }

    /**
     * And what it answers is what reading them again with the same thing settled would answer.
     *
     * <p>Which is why not reading them again is allowed. The two are one statement or they are two,
     * and stating a settling in two places is how they would come apart — so what the cheap way says
     * is held against what the reading itself says, at a position and over a form and about whether
     * anything is left.
     */
    @Test
    void whatASettlingLeavesIsWhatReadingItInWouldLeave() {
        Read read = read(SOURCE, "take");
        souther.compiler.types.TypeSymbol.AtModule name =
                souther.compiler.types.TypeSymbols.declared(
                new souther.compiler.types.TypeKey(read.rules().symbols().module(), "P"));
        souther.compiler.check.FieldDomains whole = souther.compiler.check.FieldDomains.of(
                name, read.rules(), ReadAs.THE_COMPILATION_DOES);

        for (int at = 0; at <= 5; at++) {
            Map<souther.compiler.check.RuleKey, Count> settled =
                    Map.of(souther.compiler.check.RuleKey.of("x"), count(at));
            souther.compiler.check.FieldDomains readIn = souther.compiler.check.FieldDomains.of(
                    name, read.rules(), ReadAs.THE_COMPILATION_DOES, settled);
            souther.compiler.check.FieldDomains.Carried<String> taken = whole.given(Map.of(
                    souther.compiler.check.NumberAt
                            .valueOf(souther.compiler.check.RuleKey.of("x")), count(at)))
                    .constraintsOver(claim -> claim.of()
                                    instanceof souther.compiler.check.NumberAt
                                            .OfWhatNumber.OfWhatAnOperationAnswers
                                    ? "#" + claim.position() : claim.position().toString(),
                            subject -> "?" + subject);
            java.util.SequencedMap<String, Emptiness.AtAField.Where> where =
                    new LinkedHashMap<>();
            taken.named().forEach((subject, spelled) ->
                    where.put(subject, new Emptiness.AtAField.Where.In(subject)));

            assertEquals(readIn.holdsNothing().isPresent(),
                    taken.constraints().holdsNothing(where).isPresent(),
                    "whether anything is left, with x at " + at);
            assertEquals(readIn.leftAt(souther.compiler.check.RuleKey.of("y"),
                            new souther.compiler.check.NumberAt.OfWhatNumber.OfItsOwnValue()),
                    taken.constraints().numbers().boundsOf(
                            LinearForm.<String>atom("y")),
                    "where y runs, with x at " + at);
        }
    }

    /**
     * A proof of emptiness says where it sits, in this input's words.
     *
     * <p>The rules of every parameter are said together under names this input can spell, so the
     * place the proof names is already {@code p.x} and nothing re-spells it. Said in the
     * declaration's words a report would name a field of nothing, and said as the parameter alone it
     * would name the whole of what the behavior takes for a contradiction at one field of it.
     */
    @Test
    void aProofThatNamesAPositionIsSaidUnderTheParameter() {
        Read read = read(NOTHING_AT_A_FIELD, "take");

        EmptyInput why = read.inputs().quantities(read.rules()).emptiness().orElseThrow();

        assertEquals(new EmptyInput.ProvedByTheRules(
                        new Emptiness.AtAField(new Emptiness.AtAField.Where.In("p.x"),
                                new Emptiness.EmptyOrderedInterval())),
                why);
    }

    /**
     * And where the proof names no position, it says so rather than naming one.
     *
     * <p>A pair the rules refuse is a fact about the value and not about one field of it, so there
     * is no field to name and naming one would be inventing a place. It used to be named all the
     * same — the parameter stood in for the place, because the proof arrived from a reading that
     * could only be about one parameter. Said over the whole input, there is no such stand-in to
     * reach for.
     */
    @Test
    void aProofAboutNoOnePositionNamesNone() {
        Read read = read(SOURCE, "take");

        EmptyInput why = read.inputs().quantities(read.rules())
                .given(fixing(X, 4, Y, 4)).emptiness().orElseThrow();

        assertEquals(new EmptyInput.ProvedByTheRules(new Emptiness.ConflictingRules()), why);
    }

    /** A field whose own type the rules leave no value of, so the proof names that field. */
    private static final String NOTHING_AT_A_FIELD = """
            module example.nofield

            data Impossible = Int
                invariant impossible = value > 1 && value < 1

            data P = { x: Impossible, y: Int }

            data Taken

            behavior take : (p: P) -> Taken
            """;

    /**
     * And the proof does not say which of two impossible values was fixed first.
     *
     * <p>Two counts fixed below nothing are two contradictions, and which of them a caller hears
     * about is not something the model says: the same pair fixed the other way round is the same
     * pair. Kept as the first one the fixing happened to meet, the answer carries how the question
     * was asked — and the algebra says of what is proved empty exactly what it says of what is left.
     */
    @Test
    void theProofDoesNotSayWhichImpossibleValueWasFixedFirst() {
        Read read = read(COUNTED, "take");
        Quantities asked = read.inputs().quantities(read.rules());
        NumericTerm accounts = size(read, "accounts");
        NumericTerm contacts = size(read, "contacts");

        Quantities one = asked.given(accounts, count(-1)).given(contacts, count(-1));
        Quantities other = asked.given(contacts, count(-1)).given(accounts, count(-1));
        Quantities together = asked.given(fixing(accounts, -1, contacts, -1));

        assertEquals(one.emptiness(), other.emptiness());
        assertEquals(one.emptiness(), together.emptiness());
    }

    /** And the same of one position fixed at two values, whichever way round they arrived. */
    @Test
    void theProofDoesNotSayWhichOfTwoValuesAtOnePositionCameFirst() {
        Quantities asked = quantities();

        assertEquals(asked.given(X, count(1)).given(X, count(2)).emptiness(),
                asked.given(X, count(2)).given(X, count(1)).emptiness());
    }

    private static NumericTerm size(Read read, String field) {
        return takenOfWhatIsThere(read, TermPath.of("p").then(field));
    }

    /** The term for the number that counts what stands at {@code at}, built the way the compiler
     *  builds one: through the factory that holds the operation to what is there. */
    private static NumericTerm takenOfWhatIsThere(Read read, TermPath at) {
        souther.compiler.types.Type type = read.inputs().at(at).type();
        NumericTerm.TakenOf made = NumericTerm.TakenOf.of(
                souther.compiler.check.NumericMeasures.takenOf(type, read.rules().symbols()),
                at, type, read.rules().symbols());
        assertNotNull(made, at + " is counted by what its type is counted by");
        return made;
    }

    /** A collection nothing bounds, so only what a count guarantees of itself is left to refuse
     *  one. */
    private static final String BAG = """
            module example.bag

            data Bag = { xs: List<String> }

            data Taken

            behavior take : (b: Bag) -> Taken
            """;

    private record Read(InputDomain inputs, RuleReadingSource rules) {}

    private static Quantities quantities() {
        Read read = read(SOURCE, "take");
        return read.inputs().quantities(read.rules());
    }

    private static Read read(String source, String behavior) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        return new Read(InputDomain.of(sigs.get(behavior), rules,
                ReadAs.THE_COMPILATION_DOES), rules);
    }
}
