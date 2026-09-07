package souther.compiler.check;

import souther.compiler.Compiler;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.Severity;
import souther.compiler.numeric.Count;
import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.LinearForm;
import souther.compiler.numeric.Rel;
import souther.compiler.numeric.OrderedInterval;
import souther.compiler.numeric.OrderedIntervals;
import souther.compiler.values.AdmissibleValues;
import souther.compiler.values.AdmittedPlan;
import souther.compiler.values.PlannedValues;
import souther.compiler.values.Value;
import souther.compiler.values.ValueSet;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A construction under guards that cannot all hold is not reported, whichever domain holds the
 * contradiction.
 *
 * <p>A guard reaches whatever domain has a word for what it says. A comparison reaches the numbers;
 * a predicate about a list reaches the facts and says nothing to the numbers. So the conditions on
 * a path can come to contradict in one domain while every other is untouched, and a reader asking
 * a single domain whether the path is reached walks it whenever it asked the domain that had never
 * heard of the guards. What it then reports is a violation on a path the program never takes.
 *
 * <p>Which domain is not something the author chose, so the programs below are the same program
 * twice with the contradiction moved. What made the difference visible is a clause that only the
 * numbers have a word for: a state at bottom proves everything, so a clause the contradicted domain
 * can read comes out discharged by that alone, and the two readings agree for a reason that has
 * nothing to do with the path. {@code List.length(value) >= 1} over a container built by
 * {@code List.append} is such a clause — no guard could be written about the built container, so it
 * is owed as a number and as nothing else.
 */
class WhetherAPathIsReachedIsAskedOfEveryDomainTest {

    private static final String NEL = """
            module demo
            data Bad
            data NEL = List<Int>
                invariant nonEmpty = List.length(value) >= 1
            behavior f : (a: List<Int>, b: List<Int>) -> NEL | Bad constructs NEL
            """;

    /** What this compile warns about, as the codes it says it under. The codes and not how many
     * there are: a path that is walked and a path that is not differ by one warning either way, and
     * a count would be answered the same by a compile that lost the report and by one that swapped
     * it for another. */
    private static List<String> warnedAbout(String source) {
        return Compiler.compileWithWarnings(source).warnings().stream()
                .filter(d -> d.severity() == Severity.WARNING)
                .map(Diagnostic::code)
                .toList();
    }

    /**
     * Guards that cannot both hold as a comparison, which is the domain the reading always asked.
     *
     * <p>This one holds whether or not the walk asks anything: a numeric clause under numbers that
     * contradict is proved by the contradiction itself. So it is here to say what the answer is and
     * not to catch a walk that stopped asking — the case below is the one that does that.
     */
    @Test
    void aPathTheNumbersRuleOutIsNotWalked() {
        // The construction is said nothing about; the branch holding it is said to be one nothing
        // reaches, which is the same contradiction read for the other question.
        assertEquals(List.of("E1327"), warnedAbout(NEL + """
                let f (a, b) =
                    if List.length(a) >= 5 then
                        Bad
                    else
                        (if List.length(a) >= 10 then NEL(List.append(a, b)) else Bad)
                """),
                "nothing of length under five is of length ten, so nothing builds this");
    }

    /** The same guards as a predicate, which reaches no number at all. */
    @Test
    void aPathThePredicatesRuleOutIsNotWalked() {
        assertEquals(List.of("E1327"), warnedAbout(NEL + """
                let f (a, b) =
                    if List.contains(1, a) then
                        Bad
                    else
                        (if List.contains(1, a) then NEL(List.append(a, b)) else Bad)
                """),
                "a list both holding and not holding one is no list, so nothing builds this");
    }

    /**
     * And the construction is still reported where the guards can hold.
     *
     * <p>Without this the two above are passed by a check that stopped reading conditionals
     * altogether: what they say is that these constructions go unreported, and going unreported is
     * what a walk that reads nothing does everywhere.
     */
    @Test
    void aPathThatIsReachedIsWalkedAndWhatStandsOnItIsReported() {
        assertEquals(List.of("E2011"), warnedAbout(NEL + """
                let f (a, b) =
                    if List.contains(1, a) then
                        Bad
                    else
                        (if List.contains(2, a) then NEL(List.append(a, b)) else Bad)
                """),
                "a list may hold two and not one, and nothing there says the built list is not empty");
    }

    // --- the question itself -------------------------------------------------------------------

    private static final Term.Interner NAMES = new Term.Interner();
    private static final FactSubject A_PREDICATE = FactSubject.of(NAMES.written("some predicate"));
    private static final FactSubject A_POSITION = FactSubject.of(NAMES.written("some position"));

    /**
     * Each domain at its own bottom, asked of the walk's own question.
     *
     * <p>Written here as well as over the programs above because a program can only reach the
     * domains the walk refines today — the guards settle numbers and predicates, and nothing on a
     * path names a value or moves an end. A domain that cannot hold a path's contradiction this year
     * can hold one the year a guard learns to speak to it, and the question has to be right before
     * then rather than after.
     */
    @Test
    void aPathIsReachedOnlyWhereEveryDomainLeavesSomething() {
        assertFalse(Known.top().reachesNothing(), "nothing taken in leaves the path as it was");
        assertTrue(reaching(numbersAtBottom()).reachesNothing());
        assertTrue(reaching(factsAtBottom()).reachesNothing());
        assertTrue(reaching(valuesAtBottom()).reachesNothing());
        assertTrue(reaching(orderedAtBottom()).reachesNothing());
    }

    /**
     * What the walk says of a path it has been told is not taken, and of the values there.
     *
     * <p>The saying reaches the state and no domain of it. Written into the numbers, as
     * {@code 1 <= 0}, it read as an arithmetic claim the model never made — so a reader asking why
     * nothing satisfies the state was told the rules about numbers conflict, and a reader asking
     * whether the path is reached was answered by {@code numbers.isBottom()}, which is the reading
     * that walked a path the predicates alone ruled out.
     */
    @Test
    void aPathSaidNotToBeTakenIsNotTakenAndNoDomainIsMadeToSaySo() {
        Known nothing = Known.top().reachingNothing();
        assertTrue(nothing.reachesNothing());
        assertFalse(nothing.numbers().isBottom(), "no domain was made to carry the argument");
        assertFalse(nothing.facts().isBottom());
        assertFalse(nothing.constraints().confinement().holdsNothing());
        assertFalse(nothing.unguarded().constraints().isBottom(),
                "it is the guards that cannot all hold, not the values that fail");
    }

    private static Known reaching(ConstraintState<FactSubject> constraints) {
        return new Known(constraints, List.of(), Set.of(), new Known.Unguarded(ConstraintState.<FactSubject>top()));
    }

    private static ConstraintState<FactSubject> numbersAtBottom() {
        return ConstraintState.<FactSubject>top()
                .taking(LinearForm.constant(BigDecimal.ONE), Rel.LE, Map.of());
    }

    private static ConstraintState<FactSubject> factsAtBottom() {
        return ConstraintState.<FactSubject>top().taking(A_PREDICATE, true).taking(A_PREDICATE, false);
    }

    private static ConstraintState<FactSubject> valuesAtBottom() {
        // Met as one reading and handed over as one. Two readings are combined where the
        // clauses of a declaration are read, and never at the state's boundary.
        souther.compiler.values.Allowance<FactSubject> sets =
                souther.compiler.values.AsACompilationAllows.forAdmittedValues();
        return ConstraintState.<FactSubject>top().takingRead(Confinement.Worked.of(
                says("A", sets).meet(says("B", sets), sets),
                OrderedIntervals.top(), Map.of()), sets);
    }

    /** One rule about the position, worked out, which is how a reading is come by. */
    private static AdmissibleValues<FactSubject> says(String text,
            souther.compiler.values.Allowance<FactSubject> sets) {
        return PlannedValues.at(A_POSITION, AdmittedPlan.of(ValueSet.just(Value.text(text))))
                .resolve(sets).values();
    }

    private static ConstraintState<FactSubject> orderedAtBottom() {
        return ConstraintState.<FactSubject>top()
                .taking(OrderedIntervals.at(A_POSITION,
                        new OrderedInterval(Endpoint.inclusive(Count.of(6)), null)), Map.of())
                .taking(OrderedIntervals.at(A_POSITION,
                        new OrderedInterval(null, Endpoint.inclusive(Count.of(2)))), Map.of());
    }
}
