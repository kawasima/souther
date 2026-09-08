package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.numeric.Count;
import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.numeric.OrderedInterval;
import souther.compiler.numeric.OrderedIntervals;
import souther.compiler.numeric.LinearForm;
import souther.compiler.numeric.Rel;
import souther.compiler.values.AdmittedPlan;
import souther.compiler.values.AsACompilationAllows;
import souther.compiler.values.PlannedValues;
import souther.compiler.values.Value;
import souther.compiler.values.ValueSet;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A state said in another vocabulary keeps two subjects two subjects.
 *
 * <p>Which is not what {@link NumericDomain#over} does. That is a fold: two atoms arriving at one
 * name are one number and their coefficients are added, because a caller writing
 * {@code List.length(xs) + Set.size(xs)} wrote two spellings of one count. Right of a form and wrong
 * of a state — two positions under one name would bound each other, hold each other's values and
 * settle each other's predicates, which is the reading saying what nobody wrote.
 *
 * <p><b>And the rule is about the whole vocabulary, not about each domain.</b> A subject may sit in
 * one domain and no other: a predicate the numbers have no word for, a position only an ordering
 * bounds. So a naming that is injective on every domain read by itself can still put two subjects
 * under one name, and nothing that sees one domain can tell. That is the case below, and it is the
 * one a check written per domain would let through.
 */
class ARenamingNamesTwoSubjectsTwoSubjectsTest {

    private static final Term.Interner NAMES = new Term.Interner();
    private static final FactSubject ONLY_IN_FACTS = FactSubject.of(NAMES.written("a predicate"));
    private static final FactSubject ONLY_IN_ORDERED = FactSubject.of(NAMES.written("a position"));
    private static final FactSubject ONLY_IN_VALUES = FactSubject.of(NAMES.written("another position"));
    private static final FactSubject ONLY_IN_NUMBERS = FactSubject.of(NAMES.written("a number"));

    /** Each of the four sits in one domain and no other, which is what makes the case. */
    @Test
    void eachSubjectIsHeldByOneDomainAlone() {
        ConstraintState<FactSubject> state = spread();
        assertTrue(state.facts().entails(ONLY_IN_FACTS, true));
        assertFalse(state.confinement().holdingNothing().contains(ONLY_IN_FACTS));
        assertEquals(ValueSet.ANY, state.values().at(ONLY_IN_ORDERED));
    }

    /** Two subjects no one domain holds together, sent to one name. */
    @Test
    void twoSubjectsFromDifferentDomainsMayNotShareAName() {
        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> spread().renamed(InjectiveRenaming.of(subject -> "one name")));

        assertTrue(refused.getMessage().contains("one subject"), refused.getMessage());
    }

    /** And a naming that keeps them apart is carried through every domain. */
    @Test
    void aNamingThatKeepsThemApartIsCarriedThrough() {
        Map<FactSubject, String> apart = Map.of(ONLY_IN_FACTS, "p.f", ONLY_IN_ORDERED, "p.o",
                ONLY_IN_VALUES, "p.v", ONLY_IN_NUMBERS, "p.n");
        ConstraintState<String> said = spread().renamed(InjectiveRenaming.of(apart::get));

        assertTrue(said.facts().entails("p.f", true));
        assertTrue(said.confinement().holdingNothing().contains("p.o"));
        assertEquals(ValueSet.just(Value.text("A")), said.values().at("p.v"));
        assertEquals(Endpoint.inclusive(Count.of(3)),
                said.numbers().boundsOf(LinearForm.<String>atom("p.n")).max());
    }

    /**
     * One subject gets one name, however many times it is asked about.
     *
     * <p>A renaming is a function as well as an injection, and Java's {@link
     * java.util.function.Function} promises neither. The first half is not a nicety: one of these
     * renames a state and the places of that state's positions, and a naming free to answer twice
     * would put the two in different vocabularies — a state whose rules are about {@code p.x#0} and
     * a report pointing at {@code p.x#1}, both well formed and about different things.
     */
    @Test
    void aSubjectAskedAboutTwiceKeepsItsFirstName() {
        java.util.concurrent.atomic.AtomicInteger asked = new java.util.concurrent.atomic.AtomicInteger();
        InjectiveRenaming<String, String> counting =
                InjectiveRenaming.of(subject -> subject + "#" + asked.getAndIncrement());

        assertEquals("x#0", counting.apply("x"));
        assertEquals("x#0", counting.apply("x"), "the name it was given is the name it keeps");
        assertEquals("y#1", counting.apply("y"), "and another subject is another subject");
    }

    /**
     * A naming that gives a subject no name is refused rather than remembered.
     *
     * <p>The one hole a name kept in a map cannot cover. A subject called null could not be told
     * from a subject this has not named, so it would be asked of the function again and could come
     * back called something else — the function it was held to being a function.
     */
    @Test
    void aNamingThatGivesNoNameIsRefused() {
        assertThrows(NullPointerException.class,
                () -> InjectiveRenaming.<String, String>of(_ -> null).apply("x"));
    }

    /** The same, through the domains a state is renamed by: one subject in facts and in the values
     *  arrives under one name from both. */
    @Test
    void aSubjectHeldByTwoDomainsIsRenamedOnce() {
        ConstraintState<FactSubject> both = ConstraintState.<FactSubject>top()
                .taking(ONLY_IN_FACTS, true)
                .takingRead(new Confinement.Planned<>(says(ONLY_IN_FACTS, "A"),
                                OrderedIntervals.top(), Map.<FactSubject, Carrier>of())
                                .resolve(AsACompilationAllows.forAdmittedValues()),
                        AsACompilationAllows.forAdmittedValues());
        java.util.concurrent.atomic.AtomicInteger asked = new java.util.concurrent.atomic.AtomicInteger();

        ConstraintState<String> said = both.renamed(
                InjectiveRenaming.of(subject -> "p#" + asked.getAndIncrement()));

        assertTrue(said.facts().entails("p#0", true));
        assertEquals(ValueSet.just(Value.text("A")), said.values().at("p#0"));
    }

    /** One rule about one position. */
    private static PlannedValues<FactSubject> says(FactSubject atom, String text) {
        return PlannedValues.at(atom, AdmittedPlan.of(ValueSet.just(Value.text(text))));
    }

    /** One subject in each domain, and no subject in two of them. */
    private static ConstraintState<FactSubject> spread() {
        return ConstraintState.<FactSubject>top()
                .taking(ONLY_IN_FACTS, true)
                .takingRead(new Confinement.Planned<>(says(ONLY_IN_VALUES, "A"),
                                OrderedIntervals.at(ONLY_IN_ORDERED, new OrderedInterval(
                                        Endpoint.inclusive(Count.of(6)),
                                        Endpoint.inclusive(Count.of(2)))),
                                Map.<FactSubject, Carrier>of())
                                .resolve(AsACompilationAllows.forAdmittedValues()),
                        AsACompilationAllows.forAdmittedValues())
                .taking(LinearForm.<FactSubject>atom(ONLY_IN_NUMBERS)
                                .minus(LinearForm.<FactSubject>constant(BigDecimal.valueOf(3))),
                        Rel.LE, Map.of(ONLY_IN_NUMBERS, souther.compiler.numeric.Granularity.DISCRETE));
    }
}
