package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.query.Compilation;
import souther.compiler.query.Scopes;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * What a rule raises is asked of the rule, and not of the readers there happen to be.
 *
 * <p>A completeness written as "every reading ran to the end" says the model was read in full for
 * exactly as long as nobody adds a reading. The questions a rule raises are the model's, and which
 * reading answered one of them is this compiler's — so the questions are what a measure counts, and
 * an ordering bound raises the same question about which values may stand somewhere as an equality
 * does, whichever reading happens to be able to take it in.
 *
 * <p>{@link Required.Irrelevant} is the one answer that has to cost something to say. A rule
 * relating two positions raises no question about one of them, and read as a rule nothing accounted
 * for, a model whose every rule is fine comes back as one this compiler could not read.
 */
class WhatARuleRaisesIsAskedOfTheRuleTest {

    private static Map<RuleRef, Required> raisedBy(String source, String type) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Symbols symbols = Scopes.derived(compilation.db(), module).value();
        assertNotNull(symbols);
        TypeSymbol.AtModule named = TypeSymbols.declared(new TypeKey(module, type));
        assertNotNull(symbols.declaredNode(named.key()), "no `" + type + "` declared");
        return FieldDomains.of(named, RuleReadings.of(compilation, module),
                souther.compiler.query.ReadAs.THE_COMPILATION_DOES).required();
    }

    /** What the rule raises, as `obligation at subject`, so a question and its subject are read
     * together. */
    private static Set<String> said(Required required) {
        return required.obligations().stream()
                .map(o -> o.obligation() + " at " + o)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    /** What the author called the clause, of a rule that is a declaration's invariant. */
    private static java.util.Optional<String> nameOf(RuleRef rule) {
        return rule instanceof RuleRef.Invariant invariant
                ? invariant.clause().name().map(ClauseName::value) : java.util.Optional.empty();
    }

    private static Required only(Map<RuleRef, Required> raised, String clause) {
        return raised.entrySet().stream()
                .filter(e -> nameOf(e.getKey()).filter(clause::equals).isPresent())
                .map(Map.Entry::getValue).findFirst()
                .orElseThrow(() -> new AssertionError("no clause called `" + clause + "`; had "
                        + raised.keySet()));
    }

    /**
     * An ordering bound raises what it says about the values, and the line it draws.
     *
     * <p>Both. The line is the visible half; the other is why a report may not go on to say that a
     * rule about this position went unread because the reading that turns clauses into sets of
     * values has no word for a range. The question was raised once and answered once.
     */
    @Test
    void anOrderingBoundRaisesTheValuesAndTheLine() {
        Map<RuleRef, Required> raised = raisedBy("""
                module example.rooms

                data Length = Int
                    invariant floor = value >= 1
                    invariant ceiling = value <= 100
                """, "Length");

        assertEquals(Set.of("ADMITTED_VALUES at the value", "BOUNDARY at the value"),
                said(only(raised, "floor")));
        assertEquals(Set.of("ADMITTED_VALUES at the value", "BOUNDARY at the value"),
                said(only(raised, "ceiling")));
    }

    /**
     * A rule of a shape nothing here reads raises the same question about the values, and no line.
     *
     * <p>Which is the arm that has to be right for a report to keep saying anything. Classified by
     * what this compiler could do with it, such a rule would raise nothing and the model would come
     * back fully accounted for.
     */
    @Test
    void aRuleThisCannotReadStillRaisesTheValuesItIsAbout() {
        Required even = only(raisedBy("""
                module example.rooms

                data Length = Int
                    invariant floor = value >= 1
                    invariant even = value * value >= 4
                """, "Length"), "even");

        assertEquals(Set.of("ADMITTED_VALUES at the value"), said(even));
    }

    /** An equality names values and draws no line: the same question, without the boundary. */
    @Test
    void anEqualityRaisesTheValuesAndNoLine() {
        Required only = only(raisedBy("""
                module example.rooms

                data Length = Int
                    invariant seven = value == 7
                """, "Length"), "seven");

        assertEquals(Set.of("ADMITTED_VALUES at the value"), said(only));
    }

    /**
     * A rule about no position of this value raises nothing, and says what settled that.
     *
     * <p>{@code 1 >= 0} says nothing about anywhere, so there is nothing about a position for
     * anything to have read. Filed at the value for want of somewhere else, it was a rule nothing
     * had accounted for — a false one, since what it says was read and what it says is about
     * nothing here.
     */
    @Test
    void aRuleAboutNoPositionRaisesNothing() {
        Required.Irrelevant said = assertInstanceOf(Required.Irrelevant.class,
                only(raisedBy("""
                        module example.rooms

                        data Length = Int
                            invariant floor = value >= 1
                            invariant always = 1 >= 0
                        """, "Length"), "always"));

        assertEquals(Set.of(Required.Because.IT_NAMES_NO_POSITION), said.because());
        assertEquals(Set.of(), said.obligations());
    }

    /**
     * A rule about a position that restricts no value of it raises nothing either, under its own
     * word.
     *
     * <p>{@code value - value >= 0} holds of every row: the position cancels against itself, so the
     * quantity the rule cuts is empty and there is no value anywhere it admits or refuses. Counted
     * off the sides it is a rule about {@code value} — the name is written twice — and it raised
     * the questions a rule about a position raises. Neither of them is anything the rule states, so
     * nothing could answer either, and a clause read from end to end went out as a question nobody
     * had answered.
     *
     * <p>Which the quantity settles and the spelling cannot. What a rule restricts is what its
     * canonical form cuts, which is the rule {@link UnreadComparison.Quantity.NotRead} is written around one
     * layer down — and this classification was still counting the sides.
     */
    @Test
    void aRuleRestrictingNoValueRaisesNothing() {
        Required.Irrelevant said = assertInstanceOf(Required.Irrelevant.class,
                only(raisedBy("""
                        module example.rooms

                        data Length = Int
                            invariant floor = value >= 1
                            invariant always = value - value >= 0
                        """, "Length"), "always"));

        assertEquals(Set.of(Required.Because.IT_CONSTRAINS_NO_VALUE), said.because());
        assertEquals(Set.of(), said.obligations());
    }

    /**
     * A rule relating two positions raises nothing, and says what settled that.
     *
     * <p>The conclusion, not an empty result. Both sides were recognised, and a partition is of one
     * position while a line is on one number — so there is no question left standing at either of
     * them.
     */
    @Test
    void aRelationBetweenTwoPositionsRaisesNothing() {
        Map<RuleRef, Required> raised = raisedBy("""
                module example.booking

                data Span = { startsAt: Int, endsAt: Int }
                    invariant ordered = startsAt < endsAt
                    invariant capped = endsAt <= 1440
                """, "Span");

        Required.Irrelevant ordered = assertInstanceOf(Required.Irrelevant.class,
                only(raised, "ordered"), "a rule about a pair raises no question about one position");
        assertEquals(Set.of(Required.Because.IT_RELATES_TWO_POSITIONS), ordered.because());
        assertEquals(Set.of(), ordered.obligations());

        assertEquals(Set.of("ADMITTED_VALUES at endsAt", "BOUNDARY at endsAt"),
                said(only(raised, "capped")),
                "and the clause beside it is unaffected");
    }

    /**
     * And a denial between two positions raises nothing either, though a reading takes it in.
     *
     * <p>Two questions with one name between them. Whether a reading can hold what a rule says and
     * whether a rule raises a question about one position are different things: the values now hold
     * {@code p /= q} as a relation between two blocks, and it still divides neither of them, so
     * there is nothing here for a measure of coverage to go and check.
     *
     * <p>Which is why the two are not kept in step. A capability gained on one side is not a reason
     * to move the other, and this is what says so.
     */
    @Test
    void andSoDoesADenialAReadingTakesIn() {
        Required.Irrelevant said = assertInstanceOf(Required.Irrelevant.class,
                only(raisedBy("""
                        module example.booking

                        data Pair = { p: Int, q: Int }
                            invariant differ = p /= q
                        """, "Pair"), "differ"),
                "a rule about a pair raises no question about one position");

        assertEquals(Set.of(Required.Because.IT_RELATES_TWO_POSITIONS), said.because());
        assertEquals(Set.of(), said.obligations());
    }

    /**
     * A conjunction is one rule, and raises what its parts raise together.
     *
     * <p>The relational half takes nothing away. Written as a first-wins answer, whichever conjunct
     * the walk reached first would decide whether the line the author drew is owed a row.
     */
    @Test
    void aConjunctionRaisesWhatItsPartsRaiseTogether() {
        Map<RuleRef, Required> raised = raisedBy("""
                module example.booking

                data Span = { startsAt: Int, endsAt: Int }
                    invariant both = startsAt < endsAt && endsAt <= 1440
                """, "Span");

        assertEquals(List.of(Set.of("ADMITTED_VALUES at endsAt", "BOUNDARY at endsAt")),
                raised.values().stream()
                        .map(WhatARuleRaisesIsAskedOfTheRuleTest::said).toList(),
                "one clause, and the end in it is still owed a row");
    }
}
