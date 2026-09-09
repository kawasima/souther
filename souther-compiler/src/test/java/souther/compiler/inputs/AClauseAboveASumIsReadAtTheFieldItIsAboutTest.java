package souther.compiler.inputs;

import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredSig;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;
import souther.compiler.types.TypeSymbol;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A clause of the value a sum sits in names a field the cases share, and that field is under a case.
 *
 * <p>So the clause is read where the field is. What crosses the narrowing is the name — the clause
 * stays where it was written, and the end it draws is the one it drew: an author sent to look at it
 * is sent to the declaration that wrote it and not to the case the reading came through.
 */
class AClauseAboveASumIsReadAtTheFieldItIsAboutTest {

    private static final String THROUGH_A_SUM = """
            module g

            data Paging = { limit: Int }
            data A = { ...Paging, x: Int }
            data B = { ...Paging, y: Int }
            data Q = A | B

            data Holder = { q: Q }
                invariant fits = q.limit <= 10

            data Ok

            behavior read : (h: Holder) -> Ok
            """;

    /** The same clause where the field is under a record, which is what it has always done. */
    private static final String THROUGH_A_RECORD = THROUGH_A_SUM.replace("q: Q", "q: A");

    /**
     * The end the clause draws stands at the field under each case.
     *
     * <p>And it is the same end the clause draws where no sum is in the way, because a narrowing
     * does not change what a rule says.
     */
    @Test
    void theEndStandsUnderEachCase() {
        InputDomain sum = reading(THROUGH_A_SUM);
        InputDomain record = reading(THROUGH_A_RECORD);

        String underTheRecord = String.valueOf(
                valuesAt(record.at(TermPath.of("h").then("q").then("limit"))).ownEnds());
        for (String each : List.of("A", "B")) {
            Position at = sum.at(TermPath.of("h").then("q").refine(caseNamed(sum,each)).then("limit"));
            assertNotNull(at, "the shared field is a position under the case");
            assertEquals(underTheRecord.replace("h.q.limit", "h.q@" + each + ".limit"),
                    String.valueOf(valuesAt(at).ownEnds()),
                    "the clause draws the end it draws, wherever the reading came through");
        }
    }

    /**
     * The declaration whose relation is holding the end is named under each case.
     *
     * <p>A clause comparing two coordinates of the value above draws no end of its own; where it
     * stops the shared field is what the coordinate it compares against stops at, carried across.
     * So the declaration to send an author to is the one that wrote the relation, and the case the
     * reading came through has no clause about the pair at all.
     */
    @Test
    void theDeclarationHoldingAnEndAboveIsNamedUnderEachCase() {
        String model = """
                module g

                data Paging = { limit: Int }
                data A = { ...Paging, x: Int }
                data B = { ...Paging, y: Int }
                data Q = A | B

                data Holder = { q: HELD, cap: Int }
                    invariant capped = cap <= 10
                    invariant fits = q.limit <= cap

                data Ok

                behavior read : (h: Holder) -> Ok
                """;

        List<String> underTheRecord = holdingTheCeiling(reading(model.replace("HELD", "A"))
                .at(TermPath.of("h").then("q").then("limit")));
        assertEquals(List.of("Holder"), underTheRecord,
                "the relation `Holder` wrote is what stops the field, with no sum in the way");

        InputDomain sum = reading(model.replace("HELD", "Q"));
        for (String each : List.of("A", "B")) {
            assertEquals(underTheRecord, holdingTheCeiling(sum.at(TermPath.of("h").then("q")
                            .refine(caseNamed(sum, each)).then("limit"))),
                    () -> "and the same declaration holds it under case " + each);
        }
    }

    /**
     * A case that stops the shared field short of where the value above does holds that end alone.
     *
     * <p>Two readings of one position, each asked over its own rules, and each answers about the end
     * it arrived at. So the value above is holding an end this position does not stop at, and the
     * case's own is what a report sends an author to. Kept as two answers and put back together
     * afterwards, both were named — and `Holder`'s clause admits every value the field stops short
     * of.
     */
    @Test
    void aCaseThatStopsTheFieldShorterThanTheValueAboveHoldsThatEndAlone() {
        InputDomain sum = reading("""
                module g

                data Paging = { limit: Int }
                data A = { ...Paging, x: Int, own: Int }
                    invariant tight = limit <= own
                    invariant ownCap = own <= 3
                data B = { ...Paging, y: Int }
                data Q = A | B

                data Holder = { q: Q, cap: Int }
                    invariant capped = cap <= 10
                    invariant fits = q.limit <= cap

                data Ok

                behavior read : (h: Holder) -> Ok
                """);

        Position underA = sum.at(TermPath.of("h").then("q")
                .refine(caseNamed(sum, "A")).then("limit"));
        assertEquals("3", String.valueOf(valuesAt(underA).narrowedEnds().bounds().max().at()),
                "`A` stops the field at three and `Holder` at ten");
        assertEquals(List.of("A"), holdingTheCeiling(underA),
                "so `A` is holding it, and `Holder` moved this end nowhere");

        Position underB = sum.at(TermPath.of("h").then("q")
                .refine(caseNamed(sum, "B")).then("limit"));
        assertEquals(List.of("Holder"), holdingTheCeiling(underB),
                "and under the case that says nothing about it, the value above is holding it");
    }

    /** The declarations whose clauses are holding this position's ceiling, named. */
    private static List<String> holdingTheCeiling(Position at) {
        return souther.compiler.check.AReadingOfAPosition
                .holding(valuesAt(at).narrowedEnds(), souther.compiler.numeric.EndSide.UPPER)
                .stream()
                .map(TypeSymbol::name).toList();
    }

    /**
     * A field only one case declares is not reached from above.
     *
     * <p>The clause of the value above could not have named it, so nothing of that value's is read
     * there. A reading that walked outwards until something had a rule of the name would answer for
     * the same field of every other case.
     */
    @Test
    void aFieldOnlyOneCaseHasIsNotReachedFromAbove() {
        InputDomain sum = reading(THROUGH_A_SUM);
        Position own = sum.at(TermPath.of("h").then("q").refine(caseNamed(sum,"A")).then("x"));

        assertNotNull(own, "the case's own field is a position");
        assertNull(valuesAt(own).ownEnds() == null ? null : valuesAt(own).ownEnds().min(),
                "and nothing above bounds it");
    }

    /**
     * Two sums on the way and the clause is read at every pairing of their cases.
     *
     * <p>Each narrowing is crossed on its own, by the names that narrowing's cases share. Carried as
     * one rewrite of the whole name, whether the pairing is reached would be this compiler's answer
     * rather than the model's.
     */
    @Test
    void aClauseTwoSumsUpIsReadAtEveryPairing() {
        // The parameter is the outer sum, so that both narrowings fit inside the depth this reading
        // takes a product apart: a narrowing takes no level, so `deep` is two fields down however
        // many cases are named on the way.
        String nested = """
                module g

                data Inner = { deep: Int }
                data IA = { ...Inner, p: Int }
                data IB = { ...Inner, r: Int }
                data IS = IA | IB

                data Outer = { s: IS }
                data OA = { ...Outer, m: Int }
                data OB = { ...Outer, n: Int }
                data OS = OA | OB
                RULE

                data Ok

                behavior read : (q: OS) -> Ok
                """;

        assertEquals(List.of(), boundedIn(reading(nested.replace("RULE", ""))),
                "nothing bounds anything until a clause does");
        assertEquals(List.of("q@OA.s@IA.deep", "q@OA.s@IB.deep",
                        "q@OB.s@IA.deep", "q@OB.s@IB.deep"),
                boundedIn(reading(nested.replace("RULE",
                        "data Held = OS invariant deep = value.s.deep <= 10")
                        .replace("(q: OS)", "(q: Held)"))));
    }

    /**
     * Nothing crosses into what a sequence holds.
     *
     * <p>What a clause out here says about a list is written about the list, and an element is a
     * value with a declaration of its own. Crossed here as well, a rule about the container would be
     * read as a rule about each of the things in it.
     */
    @Test
    void nothingCrossesIntoWhatASequenceHolds() {
        InputDomain read = reading("""
                module g

                data Paging = { limit: Int }
                data A = { ...Paging, x: Int }
                data B = { ...Paging, y: Int }
                data Q = A | B

                data Holder = { qs: List<Q> }

                data Ok

                behavior read : (h: Holder) -> Ok
                """, "read");

        Position element = read.at(TermPath.of("h").then("qs").element());
        assertNotNull(element, "what the list holds is a position");
        assertTrue(read.reach().crossings().isEmpty()
                        || read.reach().crossings().stream()
                                .allMatch(each -> each.at().insideASequence()),
                "and any crossing under it is the sum's own, not the list's");
    }

    /**
     * A clause of the value above that this reading did not take in leaves the shared field short.
     *
     * <p>What a position is short of is asked of every reading that reaches it, and the value a case
     * was narrowed out of is one of them. Asked of the case alone, a field the value above wrote an
     * unread clause about came back as one every rule of which had been read.
     */
    @Test
    void aClauseAboveThisReadingDidNotTakeInLeavesTheFieldShort() {
        String model = """
                module g

                data Paging = { limit: Int }
                data A = { ...Paging, x: Int }
                data B = { ...Paging, y: Int }
                data Q = A | B

                data Floor = Int invariant atLeastOne = value >= 1

                data Holder = { q: HELD, floor: Floor }
                    invariant loose = q.limit >= floor + 1
                    invariant tight = q.limit >= floor + 5

                data Ok

                behavior read : (h: Holder) -> Ok
                """;
        InputDomain sum = reading(model.replace("HELD", "Q"));

        for (String each : List.of("A", "B")) {
            Position at = sum.at(TermPath.of("h").then("q")
                    .refine(caseNamed(sum, each)).then("limit"));
            assertFalse(at.rulesLeftUnread().isEmpty(),
                    () -> "the clause of `Holder` about this field went unread, and the field says "
                            + "so under case " + each);
        }
        assertFalse(reading(model.replace("HELD", "A"))
                        .at(TermPath.of("h").then("q").then("limit")).rulesLeftUnread().isEmpty(),
                "which is what the same clause leaves where no sum is in the way");
    }

    /**
     * A question the value above raised about the shared field is raised at each case.
     *
     * <p>A row is written under one case, so a question about what may stand at that field is a
     * question at every position the field stands at. Asked of the case's own rules alone, the
     * question the value above raised was one nobody was waiting on.
     */
    @Test
    void aQuestionRaisedAboveIsRaisedAtEachCase() {
        InputDomain read = reading("""
                module g

                data Code = String invariant shaped = String.length(value) >= 2
                data Paging = { code: Code }
                data A = { ...Paging, x: Int }
                data B = { ...Paging, y: Int }
                data Q = A | B

                data Holder = { q: Q }
                    invariant notBlank = q.code /= Code("zz")

                data Ok

                behavior read : (h: Holder) -> Ok
                """);

        for (String each : List.of("A", "B")) {
            Position at = read.at(TermPath.of("h").then("q")
                    .refine(caseNamed(read, each)).then("code"));
            assertEquals(1, at.unansweredQuestions().size(),
                    () -> "the question `Holder` raised stands at the field under case " + each);
        }
    }

    /**
     * The proof that the bounds say everything the rules do is not one value's alone.
     *
     * <p>A shared field is a position of the case and a name the value above writes about, so the
     * rules reaching it are two systems. A certificate is a theorem about one of them — that its
     * relations carry nothing its box does not already describe — and two systems that each hold it
     * separately need not hold it together. So the pair is not certified, and a rule of the value
     * above that the bounds cannot express is not lost on the way down.
     *
     * <p>Which is what a whole-value question asked of a value gets wrong once a position has two.
     * {@code q.limit /= 0} leaves a hole no range holds; through a record the field says so, and
     * through a sum it came back proved exactly representable by a reading that never saw the rule.
     */
    @Test
    void theProofThatBoundsSayEverythingIsNotOneValuesAlone() {
        String model = """
                module g

                data Paging = { limit: Int }
                data A = { ...Paging, x: Int }
                data B = { ...Paging, y: Int }
                data Q = A | B

                data Holder = { q: HELD }
                    invariant nonzero = q.limit /= 0

                data Ok

                behavior read : (h: Holder) -> Ok
                """;
        InputDomain record = reading(model.replace("HELD", "A"));
        assertFalse(record.at(TermPath.of("h").then("q").then("limit")).projection().isCertified(),
                "the hole the clause leaves is one no range holds");

        InputDomain sum = reading(model.replace("HELD", "Q"));
        for (String each : List.of("A", "B")) {
            Position at = sum.at(TermPath.of("h").then("q")
                    .refine(caseNamed(sum, each)).then("limit"));
            assertFalse(at.projection().isCertified(),
                    () -> "and the same clause reaches the field under case " + each);
        }
        assertTrue(sum.at(TermPath.of("h").then("q").refine(caseNamed(sum, "A")).then("x"))
                        .projection().isCertified(),
                "a field of the case the value above cannot name keeps the case's own proof");
    }

    /**
     * A value that states nothing is not a second system.
     *
     * <p>A sum writes no clause of its own, so a shared field of one is reached by a name from a
     * value that has said nothing — and a value that has said nothing has lost nothing on the way to
     * a box. Read off the name reaching here, the plainest model there is would give up the case's
     * certificate to a pair that is really one, and the cause would say two values stated rules
     * where one of them stated none.
     */
    @Test
    void aValueThatStatesNothingIsNotASecondSystem() {
        String model = """
                module g

                data Paging = { limit: Int }
                data A = { ...Paging, x: Int }
                data B = { ...Paging, y: Int }
                data Q = A | B

                data Ok

                behavior read : (q: HELD) -> Ok
                """;
        InputDomain record = reading(model.replace("HELD", "A"));
        InputDomain sum = reading(model.replace("HELD", "Q"));

        assertEquals(record.at(TermPath.of("q").then("limit")).projection(),
                sum.at(TermPath.of("q").refine(caseNamedAt(sum, TermPath.of("q"), "A"))
                        .then("limit")).projection(),
                "the shared field says what it says with no sum in the way, and the sum states "
                        + "nothing to change it");
    }

    /**
     * A case that writes no clause of its own is not a second system either.
     *
     * <p>The one the pair above leaves out. A {@code data} is a declaration clauses may be written
     * on whether or not any were, so a case with nothing written on it looks like a value that
     * spoke to anything reading the kind of thing it is — and the field it shares with its siblings
     * would give up a certificate the same field keeps with no sum in the way, over a second system
     * that is empty.
     */
    @Test
    void aCaseThatWroteNoClauseIsNotASecondSystemEither() {
        String model = """
                module g

                data Paging = { limit: Int }
                data A = { ...Paging, x: Int }
                data B = { ...Paging, y: Int }
                data Q = A | B

                data Holder = { q: HELD }
                    invariant capped = q.limit <= 10

                data Ok

                behavior read : (h: Holder) -> Ok
                """;
        InputDomain record = reading(model.replace("HELD", "A"));
        InputDomain sum = reading(model.replace("HELD", "Q"));

        for (String each : List.of("A", "B")) {
            assertEquals(record.at(TermPath.of("h").then("q").then("limit")).projection(),
                    sum.at(TermPath.of("h").then("q")
                            .refine(caseNamed(sum, each)).then("limit")).projection(),
                    () -> "the only value that wrote anything is `Holder`, under case " + each);
        }
    }

    /** The narrowing that names this case of the sum standing at {@code sum}. */
    private static Refinement caseNamedAt(InputDomain read, TermPath sum, String name) {
        for (Case each : read.at(sum).obligationCases()) {
            if (each instanceof Case.SumCase one && one.leaf().name().equals(name)) {
                return Refinement.of(one);
            }
        }
        throw new IllegalStateException("no case named " + name);
    }

    /** What the rules leave the value standing at {@code at}, which is the number the clauses
     *  read here place their ends on. */
    private static PositionBounds valuesAt(Position at) {
        return at.boundsFor(new NumericTerm.ValueOf(at.path()));
    }

    /** Every position something puts a ceiling on, spelled the way a report names it. */
    private static List<String> boundedIn(InputDomain read) {
        return read.positions().stream()
                .filter(each -> valuesAt(each).ownEnds() != null
                        && valuesAt(each).ownEnds().max() != null)
                .map(each -> each.path().toString()).toList();
    }

    /** The narrowing that names this case of the sum the parameter or its field holds. */
    private static Refinement caseNamed(InputDomain read, String name) {
        for (Case each : read.at(TermPath.of("h").then("q")).obligationCases()) {
            if (each instanceof Case.SumCase one && one.leaf().name().equals(name)) {
                return Refinement.of(one);
            }
        }
        throw new IllegalStateException("no case named " + name);
    }

    private static InputDomain reading(String source) {
        return reading(source, "read");
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
