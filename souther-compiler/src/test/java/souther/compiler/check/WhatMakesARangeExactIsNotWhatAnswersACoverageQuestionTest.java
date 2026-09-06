package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.numeric.Count;
import souther.compiler.numeric.Endpoint;
import souther.compiler.query.Compilation;
import souther.compiler.query.Scopes;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Whether the range is exact and whether the rules were answered are two questions, and neither
 * follows from the other.
 *
 * <p>What an edge stands on is the first: a row at an edge is a whole value with that edge in it, so
 * a rule the bounds do not express is a way that value can be refused. What the coverage accounting
 * measures is the second: a rule is answered where some reading took it in, whatever it made of it.
 * Written down here as a grid rather than as two tests, because the pairs that disagree are the whole
 * content — a measure that read either off the other would be right on the rows that agree and would
 * promise a row nobody can build on the rows that do not.
 *
 * <p>Each row was measured before it was written down, and two of them are answers this compiler gets
 * wrong today. They are asserted as they stand so that the change that fixes them has to say so.
 */
class WhatMakesARangeExactIsNotWhatAnswersACoverageQuestionTest {

    /** The clause alone, so that nothing beside it decides the answer. */
    private static String only(String clauses) {
        return """
                module example.rooms

                data Length = Int
                    %s
                """.formatted(clauses);
    }

    private static FieldDomains read(String source) {
        return read(source, "Length");
    }

    private static FieldDomains read(String source, String type) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Symbols symbols = Scopes.derived(compilation.db(), module).value();
        assertNotNull(symbols);
        TypeSymbol.AtModule named = TypeSymbols.declared(new TypeKey(module, type));
        assertNotNull(symbols.declaredNode(named.key()), "no `" + type + "` declared");
        return FieldDomains.of(named, RuleReadings.of(compilation, module),
                souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
    }

    /**
     * The three answers as one value, so that a row states all of them or none.
     *
     * @param everyQuestionAnswered   whether every coverage question the rules raise was answered
     * @param evidence                what the reading that built the bounds says it managed, named
     *                                by the cause rather than compared whole: the atoms a cause
     *                                carries are this compiler's and a test asserting them would be
     *                                a copy of the reading rather than a statement about it
     */
    private record Answers(boolean everyQuestionAnswered, String evidence) {

        static Answers of(String clauses) {
            FieldDomains domains = read(only(clauses));
            return new Answers(
                    domains.accounting().values().stream().allMatch(a -> a.unaccounted().isEmpty()),
                    named(domains.projection()));
        }
    }

    /** A projection's evidence as the causes it names, in order, and "exact" where it names none. */
    private static String named(ProjectionEvidence evidence) {
        return evidence.causes().isEmpty() ? "exact"
                : evidence.causes().stream()
                        .map(cause -> cause.getClass().getSimpleName())
                        .collect(java.util.stream.Collectors.joining(", "));
    }

    private static final Answers EXACT_AND_ANSWERED = new Answers(true, "exact");
    private static final Answers ANSWERED_AND_LOSSY = new Answers(true, "Lossy");
    private static final Answers ANSWERED_AND_UNREPRESENTED = new Answers(true, "Unrepresented");
    private static final Answers UNANSWERED_AND_UNREPRESENTED = new Answers(false, "Unrepresented");

    /** A bound is both, which is the row every other one is read against. */
    @Test
    void aBoundIsExactAndAnswered() {
        assertEquals(EXACT_AND_ANSWERED, Answers.of("invariant top = value <= 10"));
    }

    /**
     * A hole is answered and leaves the range approximate.
     *
     * <p>The reading that turns clauses into sets of values holds {@code value /= 0} whole, so every
     * question about which values may stand at the position is answered. A range is all the interval
     * algebra holds, so the projection admits the 0 and no row can write it.
     */
    @Test
    void aHoleIsAnsweredAndLeavesTheRangeApproximate() {
        assertEquals(ANSWERED_AND_LOSSY, Answers.of("invariant nonzero = value /= 0"));
    }

    /**
     * The same hole beside a bound that already excludes it, which the projection takes in whole.
     *
     * <p>Nothing is lost: a value at or above 1 is on one side of zero, so the domain sharpens the
     * denial to an end rather than dropping it, and the range is the range. Which is a fact about
     * what the projection made of the clause, and not about the shape the clause is written in — a
     * reader asking what a denial could have become has no end for it either way.
     */
    @Test
    void aHoleAlreadyExcludedIsExact() {
        assertEquals(EXACT_AND_ANSWERED,
                Answers.of("""
                        invariant floor = value >= 1
                            invariant nonzero = value /= 0"""));
    }

    /**
     * A rule the numeric reading takes in whole and the second walk has no word for.
     *
     * <p>{@code value * 2 >= 4} moves the floor to 2, and is beyond the reading of ends and the
     * reading of values both. The projection took it in whole, which is the only thing that decides
     * this.
     */
    @Test
    void aScaledBoundIsExact() {
        assertEquals(EXACT_AND_ANSWERED, Answers.of("invariant said = value * 2 >= 4"));
    }

    /**
     * The row a measure built out of what the projection knows has to be built against.
     *
     * <p>Both values are named, so the reading of values holds the clause and every question is
     * answered. The projection is the interval {@code [3, 5]}, and the 4 in the middle is a row
     * nobody can write. Nothing records a loss for it: the clause never reached the interval algebra
     * as a form it could narrow by, so there was no narrowing to lose anything at. A reading that
     * concluded the range was exact from the absence of a loss would offer that 4.
     */
    @Test
    void valuesNamedApartAreAnsweredAndTheRangeBetweenThemIsNot() {
        assertEquals(ANSWERED_AND_UNREPRESENTED,
                Answers.of("invariant said = value == 3 || value == 5"));
    }

    /** A rule nothing took in is unanswered as well, which is the one row where the two agree that
     *  something is missing — and they are answering different questions to get there. */
    @Test
    void aRuleNothingReadIsUnansweredAndLeavesTheRangeApproximate() {
        assertEquals(UNANSWERED_AND_UNREPRESENTED,
                Answers.of("invariant said = Int.abs(value) >= 2"));
    }

    /**
     * A rule written a record away, named as the rule it is and at the position it is about.
     *
     * <p>The reading that builds the bounds reaches a field's own declaration, so what it says it
     * managed is said there too. A rule of the field's type that the interval algebra has no form
     * for leaves the field's range wider than the rule, and the evidence names both which rule and
     * which position — neither of which is derivable from the value the walk came back with.
     */
    @Test
    void aRuleUnderAFieldIsNamedWhereItIsWritten() {
        FieldDomains domains = read("""
                module example.rooms

                data Side = Int
                    invariant said = value == 3 || value == 5

                data Length = { a: Side }
                """, "Length");
        assertEquals(
                List.of(new ProjectionEvidence.Cause.Unrepresented(
                        new RuleRef.Invariant(only(domains, "said")), "a")),
                domains.projection().causes());
    }

    /**
     * One shape, one answer, wherever it is written.
     *
     * <p>A product of a coordinate with itself states a relation about an atom standing for the
     * product and not one about the coordinate, so the range of the coordinate is wider than the
     * rule on a record and on a newtype alike. It was two answers while a walk of the declarations
     * classified the rules by their shape: the record arm asked whether every reading of the clause
     * discharged it and the newtype arm whether the ordered reading placed an end, and one shape
     * went two ways because two walks were being asked.
     */
    @Test
    void oneShapeIsOneAnswerWhereverItIsWritten() {
        FieldDomains onARecord = read("""
                module example.rooms

                data R = { n: Int }
                    invariant odd = n * n >= 4
                """, "R");
        assertEquals("Unrepresented", named(onARecord.projection()));

        assertEquals(UNANSWERED_AND_UNREPRESENTED,
                Answers.of("invariant odd = value * value >= 4"),
                "and the same on a newtype");
    }

    /**
     * A conjunct the bounds hold nothing of, beside one they hold.
     *
     * <p>Asked of the rule rather than of each part of it, the half that was taken in answers for
     * the half that was not, and the same two rules come out one way written with {@code &&} and
     * another written on two lines. What the author reached for is not what the bounds state.
     */
    @Test
    void aConjunctTheBoundsDoNotHoldIsNotAnsweredForByTheOneBesideIt() {
        Answers apart = Answers.of("""
                invariant a = value >= 1
                    invariant b = value * value >= 4""");
        assertEquals(UNANSWERED_AND_UNREPRESENTED, apart);
        assertEquals(apart, Answers.of("invariant c = value >= 1 && value * value >= 4"),
                "and one rule written with `&&` says what the two written apart say");
    }

    /**
     * A rule about a count, held as a fact and not as a form.
     *
     * <p>What a size is never — negative — holds of every value and is taken into the bounds
     * whatever the rule beside it says, so a reading that counted it would call any rule mentioning
     * a size a rule that narrowed the size. The sizes 3 and 5 are named and the range between them
     * is `[3, 5]`, and a set of four is a row nobody can write.
     */
    @Test
    void aCountNamedApartIsNotNarrowedByWhatHoldsOfEveryCount() {
        FieldDomains domains = read("""
                module example.rooms

                data Codes = Set<String>
                    invariant said = Set.size(value) == 3 || Set.size(value) == 5
                """, "Codes");
        assertEquals("Unrepresented", named(domains.projection()));
    }

    /**
     * A conjunct the bounds hold, beside one they hold only part of.
     *
     * <p>The half that was taken in does not answer for the half that names two values and leaves
     * the one between them, and it is the same rule either way it is written.
     */
    @Test
    void aMixedConjunctIsNotAnsweredForByTheHalfTheBoundsHold() {
        assertEquals(ANSWERED_AND_UNREPRESENTED,
                Answers.of("invariant mixed = value >= 1 && (value == 3 || value == 5)"));
    }

    /**
     * A hole a clause written beside it excludes, in either order.
     *
     * <p>A loss is a fact about the step it happened at: written first, {@code value /= 0} drops a
     * hole that {@code value >= 1} puts outside the range a clause later, and the projection has no
     * hole in it. Asked of the step, the same two rules answer one way in one order and another in
     * the other; asked of the state the reading ended in, they answer once.
     */
    @Test
    void aHoleAClauseBesideItExcludesIsExactInEitherOrder() {
        assertEquals(EXACT_AND_ANSWERED,
                Answers.of("""
                        invariant nonzero = value /= 0
                            invariant floor = value >= 1"""),
                "the hole is dropped first and the bound puts it outside the range after");
        assertEquals(EXACT_AND_ANSWERED,
                Answers.of("""
                        invariant floor = value >= 1
                            invariant nonzero = value /= 0"""));
        assertEquals(ANSWERED_AND_LOSSY, Answers.of("invariant nonzero = value /= 0"),
                "and a hole nothing excludes is still a hole");
    }

    /**
     * A count is measured on its own axis, and so is where its lines fall.
     *
     * <p>A `List` is bounded at its length and has no order of its own, so the range a line on it is
     * clamped to is the one taken of the count. Read off the position's own values instead, there is
     * no range to clamp with and the end as written stands — which is the newtype's own value all
     * over again, on the axis beside it.
     */
    @Test
    void aLineOnACountIsClampedByTheRangeTakenOfTheCount() {
        FieldDomains domains = read("""
                module example.rooms

                data Length = List<Int>
                    invariant floor = List.length(value) >= 0
                    invariant nonempty = List.length(value) /= 0
                """, "Length");

        assertEquals(Endpoint.inclusive(Count.of(0)),
                domains.placedAt(RuleKey.THE_VALUE).stream().filter(FieldDomains.Placed::lower)
                        .findFirst().orElseThrow().end(),
                "`floor` writes the end at none");
        assertEquals(Endpoint.inclusive(Count.of(1)), domains.leftAt(RuleKey.THE_VALUE, new NumberAt.OfWhatNumber.OfWhatAnOperationAnswers(souther.compiler.types.ValueName.Stdlib.operation("List", "length"))).min(),
                "and the rules leave the count at one");
        assertEquals(null, domains.leftAt(RuleKey.THE_VALUE, new NumberAt.OfWhatNumber.OfItsOwnValue()),
                "while the position's own values have no range for a line to be clamped by");
    }

    /**
     * Two holes, one of them shut by a bound written beside them, in every order they can be
     * written in.
     *
     * <p>`value >= 1` puts the 0 outside the range and leaves the 5 where it was, so one rule is
     * answered by the bounds and the other is not — and which is which is a fact about the rules
     * rather than about the order the author wrote them in. Read off what the algebra dropped at the
     * step it dropped it, the second hole is invisible: the domain holds which kinds of loss an atom
     * has and not how many times each happened, so a hole at an atom that already has one adds
     * nothing to look for and the whole value comes back exact.
     */
    @Test
    void aHoleShutByABoundIsAnsweredAndTheOneBesideItIsNot() {
        List<String> holes = List.of("invariant nonzero = value /= 0",
                "invariant nonfive = value /= 5", "invariant floor = value >= 1");
        for (List<Integer> order : List.of(List.of(0, 1, 2), List.of(0, 2, 1), List.of(1, 0, 2),
                List.of(1, 2, 0), List.of(2, 0, 1), List.of(2, 1, 0))) {
            String written = order.stream().map(holes::get)
                    .collect(java.util.stream.Collectors.joining("\n    "));
            FieldDomains domains = read(only(written));
            assertEquals(List.of("invariant Length (nonfive)"),
                    domains.projection().causes().stream()
                            .map(cause -> ((RuleRef.Named)
                                    ((ProjectionEvidence.Cause.Lossy) cause).rule()).citedName())
                            .distinct().toList(),
                    "written as `" + written.replace('\n', ';') + "`");
        }
    }

    /**
     * A rule the algebra keeps as written does not answer for itself.
     *
     * <p>A form that is neither an interval nor a difference is kept whole and marked lost for
     * exactly that reason: what a reader is handed does not read it. Asked of everything the state
     * holds, the kept form is a premise for its own proof and every rule the projection dropped
     * comes back proven — so the question is put to what the projection states and to nothing
     * beside it.
     */
    @Test
    void aFormKeptAsWrittenIsNotProvenByItsOwnBeingKept() {
        FieldDomains domains = read("""
                module example.rooms

                data Length = { a: Int, b: Int }
                    invariant sum = a + b >= 10
                """, "Length");
        assertEquals("Lossy", named(domains.projection()).split(", ")[0]);
    }

    /**
     * The conjunct the bounds do not hold names its own position, and not the one beside it.
     *
     * <p>A conjunction is one rule the author wrote, and what it raises is what its conjuncts raise
     * together — so a reader that found one conjunct wanting and reached for the rule's questions
     * would name the position of the conjunct the bounds do hold among the ones they do not. That
     * the range is approximate would still be true; what it was approximate about would not be.
     *
     * <p>The same two rules written apart name the same position, which is the point: where the
     * author put the {@code &&} is not a fact about which position is short of its rules.
     */
    @Test
    void theConjunctTheBoundsDoNotHoldNamesItsOwnPosition() {
        FieldDomains together = read("""
                module example.rooms

                data Length = { a: Int, b: Int }
                    invariant both = a >= 1 && b * b >= 4
                """, "Length");
        assertEquals(List.of("b"), paths(together),
                "`a >= 1` is in the bounds, so `a` is not what is missing from them");

        FieldDomains apart = read("""
                module example.rooms

                data Length = { a: Int, b: Int }
                    invariant one = a >= 1
                    invariant two = b * b >= 4
                """, "Length");
        assertEquals(paths(together), paths(apart));
    }

    /** The positions the causes name, in the order they are held. */
    private static List<String> paths(FieldDomains domains) {
        return domains.projection().causes().stream()
                .map(cause -> ((ProjectionEvidence.Cause.Unrepresented) cause).path())
                .distinct().toList();
    }

    /** The clause of the read declaration the author called {@code name}. */
    private static Clause.Ref only(FieldDomains domains, String name) {
        return domains.accounting().keySet().stream()
                .filter(rule -> rule instanceof RuleRef.Invariant invariant
                        && invariant.clause().name().map(ClauseName::value)
                                .filter(name::equals).isPresent())
                .map(RuleRef.Invariant::clause)
                .findFirst().orElseThrow(() -> new AssertionError("no clause called `" + name + "`"));
    }
}
