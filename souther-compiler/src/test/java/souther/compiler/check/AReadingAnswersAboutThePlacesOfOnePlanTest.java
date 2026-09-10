package souther.compiler.check;

import org.junit.jupiter.api.Test;
import souther.compiler.core.Core;
import souther.compiler.coverage.ControlPlace;
import souther.compiler.coverage.CoverageSites;
import souther.compiler.partition.ProducedCases;
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

    /** A reading that was never made goes with any plan, because it says nothing about one. */
    @Test
    void noReadingAtAllIsReadAgainstWhicheverPlanTheReaderHolds() {
        Read capped = read(CAPPED);

        assertEquals(capped.answersWith(),
                ProducedCases.of(capped.body, capped.plan, PathReachability.Answers.NONE,
                        capped.answersWith()),
                "nothing was proven, so nothing is taken away — and the pair is not refused");
    }

    /** One module compiled, with the three things a reader of it puts together. */
    private record Read(Core body, CoverageSites.Plan plan, PathReachability.Answers arrives) {

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
        return new Read(checked.behaviorBodies().get("pick"), checked.plan(), answers.get("pick"));
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
