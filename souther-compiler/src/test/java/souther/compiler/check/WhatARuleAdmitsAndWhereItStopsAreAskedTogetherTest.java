package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.ast.Hir;
import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.OrderedInterval;
import souther.compiler.numeric.OrderedIntervals;
import souther.compiler.numeric.Text;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;
import souther.compiler.query.Scopes;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;
import souther.compiler.values.AdmittedPlan;
import souther.compiler.values.Allowance;
import souther.compiler.values.AsACompilationAllows;
import souther.compiler.values.PlannedValues;
import souther.compiler.values.Value;
import souther.compiler.values.ValueSet;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A rule saying which values a position may take, beside one saying where its order stops.
 *
 * <p>Neither reading has a word for what the other holds. A pattern names no ends and an ordering
 * names no finite set, so a clause reaches whichever of them can say something about it and stays
 * there — and whether anything satisfies the rules was asked of each of them alone. Both answered
 * that something did, each about the half it could see.
 *
 * <p>What is asserted here is the proof and not the sentence. Where a position is left no value by
 * its set and its range together, that is what was shown: not an empty range, which is true of
 * neither half, and not a rule admitting nothing.
 */
class WhatARuleAdmitsAndWhereItStopsAreAskedTogetherTest {

    /** The rule this issue is written about: the strings from {@code "JP"} up, and the strings
     *  below {@code "JA"}. Nothing is in both. */
    @Test
    void aPatternAndACutBelowItShareNoString() {
        assertEquals(atTheValue(new Emptiness.NoAllowedValueInRange()), only("""
                module demo

                data Code = String
                    invariant no = String.startsWith("JP", value) && value < "JA"
                """));
    }

    /** And a cut the pattern reaches over is a rule somebody can satisfy. */
    @Test
    void aPatternAndACutItReachesOverAreARuleAValueSatisfies() {
        assertNull(only("""
                module demo

                data Code = String
                    invariant yes = String.startsWith("JP", value) && value < "JZ"
                """), "every string from `JP` up to `JQ` is below `JZ`");
    }

    /** A denial is a set and an equality is a range, which is the same pair said the short way. */
    @Test
    void aDeniedValueAndARangeHoldingOnlyThatValueShareNone() {
        assertEquals(atTheValue(new Emptiness.NoAllowedValueInRange()), only("""
                module demo

                data Code = String
                    invariant no = value /= "A" && value >= "A" && value <= "A"
                """));
    }

    /**
     * And over an enumeration, where the interval algebra carries nothing at all.
     *
     * <p>The same shape over an {@code Int} is refused by the numbers, which read both the denial
     * and the ends. That is the numbers answering and not the pair, so it says nothing about
     * whether the pair is asked — and every carrier the numbers do not hold was accepted.
     */
    @Test
    void aDeniedCaseAndAnOrderStoppingAtItShareNone() {
        assertEquals(atAField("q", new Emptiness.NoAllowedValueInRange()), only("""
                module demo

                data Ready
                data Done
                data Stage = Ready | Done

                data Held = { q: Stage }
                    invariant no = q /= Ready && q <= Ready
                """));
    }

    /**
     * A choice both of whose branches are that pair, and neither of which anybody can be in.
     *
     * <p>Which branch of a choice anybody can be in is settled while the declaration is read, and
     * what settles it is the same question — so a branch whose set and whose range share no value
     * is a branch nobody can be in, and a choice of two of them admits nothing.
     *
     * <p>The shape that tells this from the pattern above is that nothing has to be built to answer
     * it. A denial is a set already, so a reading that answered "something is admitted" off the
     * descriptions alone settled both branches as live before anything asked the order — and two
     * branches nothing satisfies were then joined into a choice that does.
     *
     * <p>The place is named because both branches were refused at it. Which is the same rule the
     * choice below is under, answered the other way by what the branches happen to be about.
     */
    @Test
    void aChoiceOfTwoSuchBranchesAdmitsNothing() {
        assertEquals(atTheValue(new Emptiness.NoAllowedValueInRange()), only("""
                module demo

                data Code = String
                    invariant no =
                        (value /= "A" && value >= "A" && value <= "A")
                        || (value /= "B" && value >= "B" && value <= "B")
                """));
    }

    /** And the same over an enumeration, where the interval algebra carries nothing at all. */
    @Test
    void aChoiceOfTwoSuchBranchesOverAnEnumerationAdmitsNothing() {
        assertEquals(atAField("q", new Emptiness.NoAllowedValueInRange()), only("""
                module demo

                data Ready
                data Done
                data Stage = Ready | Done

                data Held = { q: Stage }
                    invariant no = (q /= Ready && q <= Ready) || (q /= Done && q >= Done)
                """));
    }

    /**
     * Every alternative refused, each at a position the others stand at.
     *
     * <p>What the alternatives leave one position is their union, and the range at that position
     * holds a value of it: {@code x} may begin with {@code "A"}, which is below {@code "B"}, and
     * {@code y} may begin with {@code "D"}, which is at {@code "D"} or above. So a reading that
     * met the sets against the ranges position by position would find something at each of them and
     * nothing wrong anywhere — and no alternative survives, since the one that reaches {@code x} is
     * refused at {@code y} and the other way round.
     *
     * <p>No position is named, for the same reason: each of them holds values some alternative
     * stands at, so the lack is the whole product's. What was shown travels with the branches as
     * they are dropped — worked out again after the drop, the answer would be that the values admit
     * nothing, which is the general form of what was actually shown.
     */
    @Test
    void alternativesRefusedAtDifferentPositionsLeaveNothingAndNameNoPosition() {
        assertEquals(new Emptiness.NoAllowedValueInRange(), only("""
                module demo

                data Held = { x: String, y: String }
                    invariant no = ((String.startsWith("A", x) && String.startsWith("B", y))
                        || (String.startsWith("C", x) && String.startsWith("D", y)))
                        && x < "B" && y >= "D"
                """));
    }

    /**
     * And the author is told it in a sentence that names no place.
     *
     * <p>The other end of the same proof. What names a position and what does not are two sentences,
     * and a proof shown of a whole product read as one about a position would send an author to a
     * place its own rules are fine with — so the sentence chosen where the proof names none is the
     * one that says the rules cannot all hold.
     */
    @Test
    void andTheSentenceForOneNamesNoPlace() {
        assertEquals(List.of("ItsRulesCannotAllHold"), saidBy("""
                module demo

                data Held = { x: String, y: String }
                    invariant no = ((String.startsWith("A", x) && String.startsWith("B", y))
                        || (String.startsWith("C", x) && String.startsWith("D", y)))
                        && x < "B" && y >= "D"
                """));
        assertEquals(List.of("NoValueItsRulesAllowIsInThatRange"), saidBy("""
                module demo

                data Code = String
                    invariant no = String.startsWith("JP", value) && value < "JA"
                """), "and one that names a place is said with it");
    }

    /** What an author is told about the one declaration of {@code source}. */
    private static List<String> saidBy(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        return compilation.diagnostics().values().stream()
                .flatMap(List::stream)
                .map(souther.compiler.diag.Located::diagnostic)
                .filter(each -> "E1013".equals(each.code()))
                .map(each -> each.said().getClass().getSimpleName())
                .toList();
    }

    /** And the same alternatives with one of them left standing are a rule somebody satisfies. */
    @Test
    void oneAlternativeStandingIsARuleAValueSatisfies() {
        assertNull(only("""
                module demo

                data Held = { x: String, y: String }
                    invariant yes = ((String.startsWith("A", x) && String.startsWith("B", y))
                        || (String.startsWith("C", x) && String.startsWith("D", y)))
                        && x < "B" && y >= "B"
                """), "the first alternative stands: `x` begins with `A` and `y` with `B`");
    }

    /**
     * Alternatives met with a range that reaches each position and no alternative of them.
     *
     * <p>The same shape as the model above, at the boundary where the two arrive apart: a reading
     * holding its alternatives, and ends put on the positions by rules read somewhere else. Nothing
     * settled the alternatives against those ends, so both are still standing when they meet, and
     * what each position holds across them reaches inside the range — {@code x} may be {@code "A"},
     * which is at {@code "A"} or below, and {@code y} may be {@code "D"}, which is at {@code "D"} or
     * above. Neither pair is: the one that reaches {@code x} is refused at {@code y}, and the other
     * way round.
     *
     * <p>Which is why the question is asked of every alternative and never of what they project onto
     * a position. Asked position by position, this state holds values.
     */
    @Test
    void alternativesAreAskedWholeAndNotThroughWhatTheyProjectOntoAPosition() {
        Allowance<FactSubject> sets = AsACompilationAllows.forAdmittedValues();
        PlannedValues<FactSubject> here = pair("A", "B");
        PlannedValues<FactSubject> there = pair("C", "D");
        OrderedIntervals<FactSubject> ends = OrderedIntervals
                .at(X, new OrderedInterval(null, Endpoint.inclusive(Text.of("A"))))
                .meet(OrderedIntervals.at(Y, new OrderedInterval(
                        Endpoint.inclusive(Text.of("D")), null)));
        ConstraintState<FactSubject> state = ConstraintState.<FactSubject>top()
                .takingRead(new Confinement.Planned<>(here.joinLiveApart(there), ends,
                        Map.of(X, Carrier.TEXT, Y, Carrier.TEXT)).resolve(sets), sets);

        assertEquals(ValueSet.oneOf(new LinkedHashSet<>(List.of(
                        Value.text("A"), Value.text("C")))), state.values().at(X),
                "each position holds a value the range reaches");
        assertEquals(Set.of(), state.confinement().holdingNothing(),
                "and neither range is one the order leaves nothing in");
        assertTrue(state.isBottom(), "and no alternative of them stands");
    }

    private static final Term.Interner NAMES = new Term.Interner();
    private static final FactSubject X = FactSubject.of(NAMES.written("x"));
    private static final FactSubject Y = FactSubject.of(NAMES.written("y"));

    private static Emptiness atTheValue(Emptiness under) {
        return new Emptiness.AtAField(new Emptiness.AtAField.Where.TheValueItself(), under);
    }

    private static Emptiness atAField(String spelled, Emptiness under) {
        return new Emptiness.AtAField(new Emptiness.AtAField.Where.In(spelled), under);
    }

    /** One alternative of the choice below, while it is still a description — which is where a
     *  choice is taken. */
    private static PlannedValues<FactSubject> pair(String here, String there) {
        return PlannedValues.<FactSubject>at(X, AdmittedPlan.of(ValueSet.just(Value.text(here))))
                .meet(PlannedValues.at(Y, AdmittedPlan.of(ValueSet.just(Value.text(there)))));
    }

    /** How the one declaration of {@code source} was shown to have no value, or null where it has
     *  one. */
    private static Emptiness only(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        assertEquals(List.of(), compilation.diagnostics().values().stream()
                        .flatMap(List::stream).map(each -> each.diagnostic().code().toString())
                        .filter(each -> !each.equals("E1013")).toList(),
                "the model this reads has to be one somebody could write");
        List<Hir.Def> defs = compilation.module("demo").defs().stream()
                .map(each -> each.declaration().node()).toList();
        Symbols symbols = Scopes.derived(compilation.db(), "demo").value();
        String named = source.contains("data Code") ? "Code" : "Held";
        return TypeCardinality.solve(defs, RuleReadings.of(compilation, "demo"),
                        ReadAs.THE_COMPILATION_DOES)
                .of(TypeSymbols.declared(new TypeKey(symbols.module(), named))).why();
    }
}
