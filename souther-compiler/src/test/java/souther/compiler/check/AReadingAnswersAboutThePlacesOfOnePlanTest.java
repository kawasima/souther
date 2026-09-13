package souther.compiler.check;

import org.junit.jupiter.api.Test;
import souther.compiler.core.Core;
import souther.compiler.coverage.ArmProbe;
import souther.compiler.coverage.ControlPlace;
import souther.compiler.claims.Claims;
import souther.compiler.claims.UnreachableClaims;
import souther.compiler.coverage.CoverageSites;
import souther.compiler.inputs.InputDomain;
import souther.compiler.partition.GuardThresholds;
import souther.compiler.partition.ProducedCases;
import souther.compiler.partition.RuleReachNumbering;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.reach.Reachability;
import souther.compiler.reach.WhyUnsettled;
import souther.compiler.types.TypeSymbol;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A reading and the plan whose places it is asked about are one plan's, and are held to it.
 *
 * <p>A place this reading has nothing filed under is one the walk did not get to, and every reader
 * takes that as unsettled and goes on. That is the right answer for a body this walk stopped short
 * in, and it is the same answer for every place of another module — where nothing was asked and
 * nothing is being said. Under one answer, a reader handed the wrong reading is told that nothing
 * arrives anywhere, which is the shape of a behavior with nothing to measure.
 *
 * <p><b>Asked of the pairing and not of the place.</b> A place cannot say which plan it is of: an
 * arm no run can be recorded in carries no numbering at all, and giving it one would put the plan's
 * own address inside the identity the plan is supposed to be reading. So the two halves are held to
 * each other where a reader puts them together, and once.
 */
class AReadingAnswersAboutThePlacesOfOnePlanTest {

    /** Nothing at or above the cap reaches the arm that answers {@code No}. */
    private static final String CAPPED = """
            module example.capped

            data Count = Int
                invariant lower = value >= 0
                invariant cap = value <= 10
            data Yes
            data No

            behavior pick : (c: Count) -> Yes | No

            let pick (c) =
                if c.value >= 50
                    then No
                    else Yes

            example pick
                | "small" : (Count(1)) -> Yes
            """;

    /** Another module, whose reading also proves an arm dead — so it is not an empty reading that
     *  would be turned away for saying nothing. */
    private static final String REFUSED = """
            module example.refused

            data On
            data Off
            data Pending
            data Flag = On | Off | Pending
            data Active = Flag invariant value /= Off
            data Yes
            data No

            behavior pick : (f: Active) -> Yes | No

            let pick (f) = match f.value with
                | On      -> Yes
                | Pending -> Yes
                | Off     -> No

            example pick
                | "on" : (Active(On)) -> Yes
            """;

    /**
     * A guard under a guard that rules it out: both lines are ones the declarations allow, and
     * nothing arrives at the inner one. What drops it is the reading of what arrives and nothing
     * else, which is the lookup under test.
     */
    private static final String SHADOWED = """
            module example.shadowed

            data Count = Int
                invariant lower = value >= 0
                invariant cap = value <= 100
            data Small
            data Large
            data Under

            behavior pick : (c: Count) -> Small | Large | Under

            let pick (c) =
                if c.value >= 50
                    then if c.value <= 10
                        then Small
                        else Large
                    else Under

            example pick
                | "big" : (Count(60)) -> Large
                | "under" : (Count(1)) -> Under
            """;

    /** A body that declares a case cannot arrive, which is what a claim is made of. */
    private static final String DECLARED = """
            module example.declared

            data On
            data Off
            data Pending
            data Flag = On | Off | Pending
            data Active = Flag invariant value /= Off
            data Yes

            behavior pick : (f: Active) -> Yes

            let pick (f) = match f.value with
                | On      -> Yes
                | Pending -> Yes
                | Off     -> unreachable "an Active never carries Off"

            example pick
                | "on" : (Active(On)) -> Yes
            """;

    /**
     * What the reading of one module says about a place of another, which is nothing, said the way
     * "the walk did not get there" is said.
     *
     * <p>The reason the pairing is checked at all. Both readings here prove an arm dead, so neither
     * is turned away by a reader that skips a reading with nothing to say.
     */
    @Test
    void aReadingHasNothingFiledUnderAnotherPlansPlaceAndSaysSoLikeAWalkThatStoppedShort() {
        Read capped = read(CAPPED);
        Read refused = read(REFUSED);

        Reachability said = refused.arrives.at(capped.armProvenDead());

        WhyUnsettled why = assertInstanceOf(Reachability.Unsettled.class, said).why();
        assertEquals(WhyUnsettled.theWalkDidNotReachIt(), why,
                "the arm one module proved dead is, to the other module's reading, a place its own"
                        + " walk never came to — and a reader cannot tell the two apart");
    }

    /**
     * The walk over what a body can answer with refuses the pair rather than reading it.
     *
     * <p>What it would answer instead is the whole of the harm: the arm that takes {@code No} away
     * is proven dead in {@code capped}'s own reading, and is a place {@code refused}'s reading has
     * nothing filed under — so the case only that arm answers with comes back owed, and a row is
     * asked for at a branch nothing can reach.
     */
    @Test
    void theWalkOverWhatABodyAnswersWithRefusesAReadingOfAnotherPlan() {
        Read capped = read(CAPPED);
        Read refused = read(REFUSED);

        assertEquals(Set.of("example.capped.Yes"), namesOf(ProducedCases.of(
                        capped.body, capped.plan, capped.arrives, capped.answersWith())),
                "read against its own plan, the case only the dead arm answers with is taken away");

        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                () -> ProducedCases.of(capped.body, capped.plan, refused.arrives,
                        capped.answersWith()));

        assertTrue(refusal.getMessage().contains("was made under")
                        && refusal.getMessage().contains("is being read against"),
                () -> "the refusal names the two numberings: " + refusal.getMessage());
    }

    /**
     * Taking the rows in refuses probes of another numbering rather than correcting nothing.
     *
     * <p>The same invariant met from the other side. What a run corrects is read by looking each
     * arm's probe up among the probes a row was recorded at, so probes of another numbering answer
     * to no arm here — every proof stands, and a reading shown wrong by a run goes on saying
     * nothing arrives.
     */
    @Test
    void takingTheRowsInRefusesProbesOfAnotherNumbering() {
        Read capped = read(CAPPED);
        Read refused = read(REFUSED);

        ArmProbe elsewhere = refused.armProvenDead().probe().orElseThrow();

        assertEquals(Set.of(),
                capped.arrives.asRunWith(Set.of()).provedWrong(),
                "no row ran, so this reading is shown wrong nowhere");

        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                () -> capped.arrives.asRunWith(Set.of(elsewhere)));

        assertTrue(refusal.getMessage().contains("was made under"),
                () -> "the refusal names the two numberings: " + refusal.getMessage());
    }

    /**
     * The rules read off a behavior's guards refuse the pair rather than reading it.
     *
     * <p>The other lookup a reading holds, and the one an absence is widest at. What a comparison
     * this reading has nothing filed under comes back as is that nothing is known about it, which
     * restricts nothing — so a reading of another module would leave every line of this body drawn
     * exactly as the declarations leave it, and say nothing about having been the wrong reading.
     *
     * <p>What that costs here, measured with the pair let through: the inner line comes back
     * alongside the outer one, and a row is asked for at a boundary nothing can arrive at.
     */
    @Test
    void theRulesReadOffTheGuardsRefuseAReadingOfAnotherPlan() {
        Read shadowed = read(SHADOWED);
        Read refused = read(REFUSED);

        assertEquals(1, shadowed.guardsWith(shadowed.arrives).thresholds().size(),
                "read against its own plan, the inner line is dropped: nothing arrives at it");

        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                () -> shadowed.guardsWith(refused.arrives));

        assertTrue(refusal.getMessage().contains("was made under"),
                () -> "the refusal names the two numberings: " + refusal.getMessage());
    }

    /**
     * Judging what a body declares cannot arrive refuses the pair rather than reading it.
     *
     * <p>The seam the whole question was found at. A claim is judged by looking its arm up in a
     * reading, and a reading of another plan has nothing filed under any of them — so every claim
     * an author wrote comes back unproven, which is what a claim about a place this compiler did
     * not walk to comes back as.
     */
    @Test
    void judgingWhatABodyDeclaresCannotArriveRefusesAReadingOfAnotherPlan() {
        Read declared = read(DECLARED);
        Read refused = read(REFUSED);

        UnreachableClaims claims = declared.claims();
        assertFalse(claims.isEmpty(), "the body declares that a case cannot arrive");
        assertEquals(claims.all().size(), Claims.of(claims, declared.arrives).all().size(),
                "read against its own plan, every claim it makes is judged");

        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                () -> Claims.of(claims, refused.arrives));

        assertTrue(refusal.getMessage().contains("was made under"),
                () -> "the refusal names the two numberings: " + refusal.getMessage());
    }

    /** A reading that was never made goes with any plan, because it says nothing about one. */
    @Test
    void noReadingAtAllIsReadAgainstWhicheverPlanTheReaderHolds() {
        Read capped = read(CAPPED);

        assertEquals(capped.answersWith(),
                ProducedCases.of(capped.body, capped.plan, PathReachability.Answers.NONE,
                        capped.answersWith()),
                "nothing was proven, so nothing is taken away — and the pair is not refused");
    }

    /** One module compiled, with what the readers under test put together. */
    private record Read(Compilation compilation, String module, Bodies.Elaborated checked,
                        Core body, CoverageSites.Plan plan, PathReachability.Answers arrives) {

        /** What this behavior's guards state, read against {@code against}. */
        GuardThresholds.Guards guardsWith(PathReachability.Answers against) {
            RuleReadingSource rules = RuleReadings.of(compilation, module);
            InputDomain inputs = compilation.db()
                    .ask(new Adequacy.Inputs(module)).value().get("pick");
            AnalysisBody analysis = checked.analysisBodies().get("pick");
            return GuardThresholds.of("pick", analysis, body, plan, inputs.reading(rules),
                    ElementBindings.of(analysis.core(), analysis.elements(), rules.newtypes()),
                    against, new RuleReachNumbering(module, "pick"));
        }

        /** What this behavior's body declares cannot arrive. */
        UnreachableClaims claims() {
            RuleReadingSource rules = RuleReadings.of(compilation, module);
            InputDomain inputs = compilation.db()
                    .ask(new Adequacy.Inputs(module)).value().get("pick");
            return UnreachableClaims.of(body, inputs, rules.symbols(), rules.newtypes(), plan);
        }

        /** The arm this module's own reading proves nothing arrives at. */
        ControlPlace.Arm armProvenDead() {
            return arrives.found().entrySet().stream()
                    .filter(each -> each.getValue() instanceof Reachability.Unreachable)
                    .map(Map.Entry::getKey)
                    .filter(ControlPlace.Arm.class::isInstance)
                    .map(ControlPlace.Arm.class::cast)
                    .findFirst().orElseThrow(() ->
                            new AssertionError("this module proves no arm dead, so there is"
                                    + " nothing here for a foreign reading to answer about"));
        }

        /** The cases the body names, which is what the walk takes from. */
        Set<TypeSymbol> answersWith() {
            Set<TypeSymbol> out = new LinkedHashSet<>();
            gather(body, out);
            return out;
        }
    }

    private static Read read(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Bodies.Elaborated checked = compilation.db().ask(new Bodies.Checked(module)).value();
        Map<String, PathReachability.Answers> answers =
                compilation.db().ask(new Adequacy.PathReached(module)).value();
        return new Read(compilation, module, checked, checked.behaviorBodies().get("pick"),
                checked.plan(), answers.get("pick"));
    }

    private static void gather(Core e, Set<TypeSymbol> out) {
        if (e == null) {
            return;
        }
        switch (e) {
            case Core.UnitValue value -> out.add(value.data());
            case Core.Construct built -> out.add(built.typeName());
            default -> { }
        }
        Core.forEachChild(e, child -> gather(child, out));
    }

    private static Set<String> namesOf(Set<TypeSymbol> types) {
        return types.stream().map(TypeSymbol::toString)
                .collect(java.util.stream.Collectors.toSet());
    }
}
