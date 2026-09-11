package souther.compiler.partition;

import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.check.RuleReadingContext;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.WhatTheRowsReached;
import souther.compiler.check.TypeView;
import souther.compiler.inputs.Membership;
import souther.compiler.observe.ObservedValue;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.query.PartitionEvidence;
import souther.compiler.report.GeneratedRows;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;
import souther.compiler.types.TypeSymbol;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A name comes off to say what a position is and goes back on to say what stands at it.
 *
 * <p>The two directions are one fact and have to be read as one. A {@code data DecisionN = Decision}
 * divides into {@code Decision}'s cases — which is the reading that stopped at the name before
 * (issue #631) — and a row at that position writes {@code DecisionN(Approved { id = 1 })}, which is
 * that reading run backwards. Three things have to hold together for an axis to be worth anything:
 * the classes are derived, a value for one is written under the names, and a value somebody already
 * wrote is read back into its class. Any one of them alone is an axis nothing can be measured at.
 *
 * <p>Which is why the wrapper order is pinned against written-out values in both directions rather
 * than against each other. Put on and taken off by the same wrong order, a round trip through the
 * two would agree with itself and every row would carry {@code DecisionN(DecisionNN(...))}.
 */
class ANameGoesBackOnTheWayItCameOffTest {

    private static final String MODULE = """
            module demo

            data Ok
            data Rejected
            data Approved = { id: Int }
            data Decision = Approved | Rejected
            data DecisionN = Decision
            data DecisionNN = DecisionN

            behavior run : (x: DecisionN) -> Ok
            let run (x) = Ok
            """;

    private final RuleReadingSource rules = RuleReadings.ofSource(MODULE);

    private TypeSymbol named(String name) {
        return TypeSymbols.declared(new TypeKey(rules.symbols().module(), name));
    }

    /** The same name as this module writes it, which is what a row is written with. */
    private souther.compiler.types.TypeReachName.Written reached(String name) {
        return (souther.compiler.types.TypeReachName.Written) rules.symbols().scope().reach(named(name));
    }

    private PartitionClass classOf(String type, String id) {
        return PartitionClasses.of(Type.ref(named(type)), RuleReadingContext.unshared(rules,
                souther.compiler.query.ReadAs.THE_COMPILATION_DOES), java.util.Set.of()).stream()
                .filter(each -> each.id().equals(id)).findFirst().orElseThrow();
    }

    // --- the names, read off ---------------------------------------------------------------------

    /** What the reading says the position wears, which everything below is the two directions of. */
    @Test
    void theNamesAreReadOffOutermostFirst() {
        assertEquals(List.of("DecisionNN", "DecisionN"),
                TypeView.of(Type.ref(named("DecisionNN")), rules.symbols()).wrappers().stream()
                        .map(TypeSymbol::name).toList());
    }

    // --- and put back on -------------------------------------------------------------------------

    /** A value the class names, under one name and under two. */
    @Test
    void aNamedValueIsWrittenUnderEveryNameThePositionWears() {
        assertEquals(List.of("DecisionN(Rejected)"),
                written(classOf("DecisionN", "Rejected")));
        assertEquals(List.of("DecisionNN(DecisionN(Rejected))"),
                written(classOf("DecisionNN", "Rejected")));
    }

    /**
     * And a value nothing has composed yet, which is what the projection is for.
     *
     * <p>{@code Approved} is a record, so the class names the constructor and the generator composes
     * the fields. The names still belong to the position, and they are carried until there is a
     * value to put them on.
     */
    @Test
    void aComposedValueCarriesTheNamesUntilThereIsSomethingToPutThemOn() {
        RepresentativeSource.Evaluation.Compose compose = assertInstanceOf(
                RepresentativeSource.Evaluation.Compose.class,
                classOf("DecisionNN", "Approved").representatives().evaluate());

        assertEquals(named("Approved"), compose.through());
        assertEquals(List.of(reached("DecisionNN"), reached("DecisionN")), compose.worn());
        assertEquals("DecisionNN(DecisionN(Approved { id = 1 }))",
                compose.written(FixtureTemplate.record(reached("Approved"),
                        Map.of("id", FixtureTemplate.integer(1)))).text());
    }

    // --- and taken off again ---------------------------------------------------------------------

    /**
     * A row's value read back into its class, at the same two depths.
     *
     * <p>The observation is written out here rather than made by writing a fixture and observing it:
     * a value built by the direction under test would agree with it whichever order the names went
     * on in.
     */
    @Test
    void aValueWrittenUnderTheNamesIsReadBackThroughThem() {
        ObservedValue approved = new ObservedValue.Constructed(named("Approved"),
                Map.of("id", new ObservedValue.Integer(1)));

        assertInstanceOf(Membership.Match.class, classOf("DecisionN", "Approved").classifier()
                .membershipOf(wearing(approved, "DecisionN")));
        assertInstanceOf(Membership.Match.class, classOf("DecisionNN", "Approved").classifier()
                .membershipOf(wearing(approved, "DecisionNN", "DecisionN")));
        assertInstanceOf(Membership.NoMatch.class, classOf("DecisionNN", "Rejected").classifier()
                .membershipOf(wearing(approved, "DecisionNN", "DecisionN")));
    }

    /** And the names are taken off in the order they were written, not in whatever order arrives. */
    @Test
    void namesInTheWrongOrderAreNotTheNamesThePositionWears() {
        ObservedValue approved = new ObservedValue.Constructed(named("Approved"),
                Map.of("id", new ObservedValue.Integer(1)));

        assertInstanceOf(Membership.NoMatch.class, classOf("DecisionNN", "Approved").classifier()
                .membershipOf(wearing(approved, "DecisionN", "DecisionNN")));
    }

    // --- and the same three, through a whole compilation -----------------------------------------

    /**
     * The class the model divides the position into, the row written for it, and a row already
     * written read back — end to end, where each of the three is a different reader.
     */
    @Test
    void aRowIsWrittenUnderTheNameAndReadBackThroughIt() {
        Compilation compilation = Compilation.ofSource(MODULE, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        PartitionEvidence evidence = compilation.db()
                .ask(new Adequacy.Coverage("demo")).value().get("run");

        assertEquals(List.of("Approved", "Rejected"), evidence.axes().get(0).classes());
        String rows = GeneratedRows.of(compilation, "demo", "run", true,
                SourceRendering.namedByIdentity(compilation.texts())).text();
        assertTrue(rows.contains("DecisionN(Approved { id = 0 })"), rows);
        assertTrue(rows.contains("DecisionN(Rejected)"), rows);
    }

    /** And what an author already wrote is counted, which is the direction a generated row cannot
     *  show. */
    @Test
    void aRowAnAuthorWroteIsCountedAtTheClassItIsIn() {
        Compilation compilation = Compilation.ofSource(MODULE + """
                example run
                    | (DecisionN(Approved { id = 1 })) -> Ok
                """, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        PartitionEvidence evidence = compilation.db()
                .ask(new Adequacy.Coverage("demo")).value().get("run");

        assertEquals(java.util.Set.of("Approved"),
                WhatTheRowsReached.at(evidence.axes().get(0)).covered());
    }

    /** The observation of {@code value} written under {@code names}, outermost first. */
    private ObservedValue wearing(ObservedValue value, String... names) {
        ObservedValue at = value;
        for (int i = names.length - 1; i >= 0; i--) {
            at = new ObservedValue.Constructed(named(names[i]), Map.of("value", at));
        }
        return at;
    }

    private List<String> written(PartitionClass each) {
        return Partitions.standingFor(each.representatives(), RuleReadingContext.unshared(rules,
                souther.compiler.query.ReadAs.THE_COMPILATION_DOES), java.util.Set.of()).stream()
                .map(FixtureTemplate::text).toList();
    }
}
