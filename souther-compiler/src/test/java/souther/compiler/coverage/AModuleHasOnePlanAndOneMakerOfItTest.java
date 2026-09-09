package souther.compiler.coverage;

import org.junit.jupiter.api.Test;
import souther.compiler.WhatWasCompiled;

import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A module's places are walked once, by the answer that holds its bodies.
 *
 * <p>A plan is filed by which {@link souther.compiler.core.Core} objects were put in it. Two checks
 * of one source build trees a record compares as equal, so a plan of one and a plan of the other
 * hold everything alike and answer for different objects — and a reader handed the wrong one gets
 * nothing back from every lookup, which is indistinguishable from a place that was never numbered.
 * So there is one plan of a module, kept by the check that walked the bodies for it, and everything
 * else is handed that one.
 *
 * <p><b>Both doors, because either alone leaves the other open.</b> The walk is one way in and the
 * constructor is the other: a caller assembling a plan out of another plan's parts never calls the
 * walk, and a caller walking the bodies a second time never calls the constructor. Each is counted
 * rather than merely looked for, because a second walk written beside the first is a second plan
 * and would leave a list of who walks unchanged.
 *
 * <p>What keeps those counts a population rather than a census of today's readers is the language,
 * and that is asked for on its own below. A modifier is what puts the constructor and the indexes
 * out of reach, and a modifier changing moves nothing either count would see.
 *
 * <p>Read off the compiled classes, so what is counted is what a method does rather than what a
 * reading of the sources makes of it. The tests are not in {@code target/classes}, so a plan built
 * by a fixture to drive a reader into a state no source reaches is not among these.
 */
class AModuleHasOnePlanAndOneMakerOfItTest {

    private static final String SITES = "souther/compiler/coverage/CoverageSites";

    private static final String A_PLAN = "souther/compiler/coverage/CoverageSites$Plan";

    /** Who walks a module's bodies for its places, and what makes it the one that may. */
    private static final Map<String, String> MAY_WALK = Map.of(
            "souther.compiler.query.Bodies.Checked.compute", "the check that holds the bodies. It"
                    + " walks them once, judges the claims against what that walk found, and keeps"
                    + " the plan on the answer, so every reader downstream is looking at that one");

    /** Who puts a plan together field by field, and what makes it the one that may. */
    private static final Map<String, String> MAY_CONSTRUCT = Map.of(
            "souther.compiler.coverage.CoverageSites.asPlan", "the end of the walk, where what it"
                    + " found becomes a plan. The checks in the constructor are for this caller:"
                    + " the fields are handed over one at a time and can be handed over out of"
                    + " step",
            "souther.compiler.coverage.CoverageSites.Plan.<clinit>", "the plan of nothing, which is"
                    + " what a reader is given where a module's bodies did not come out. It numbers"
                    + " no place and its numbering is nobody's, so a recording aligned against it"
                    + " is refused");

    @Test
    void onlyTheCheckThatHoldsTheBodiesWalksThemForAPlan() {
        assertEquals(once(MAY_WALK), calling(SITES, "of"),
                () -> "a second walk of one module's bodies makes a second plan of it, addressing"
                        + " objects the first plan does not — and a second walk written where the"
                        + " first is counts as one. What walks them today, and what makes it the"
                        + " one that may: " + MAY_WALK);
    }

    @Test
    void andOnlyTheEndOfThatWalkPutsOneTogether() {
        assertEquals(once(MAY_CONSTRUCT), calling(A_PLAN, "<init>"),
                () -> "a plan assembled out of parts is an index into a graph its assembler does"
                        + " not own. What builds one today, and what makes it the one that may: "
                        + MAY_CONSTRUCT);
    }

    /**
     * And what a caller outside this package can reach is written down.
     *
     * <p>Counting who calls the constructor says nothing about who could. The constructor being
     * out of reach is what makes the count above the whole population rather than today's readers,
     * and it is a modifier — so a change to it moves nothing this test would otherwise see, and
     * every reader of the plan goes on compiling.
     *
     * <p>The whole surface and not the constructor alone. What must not leave is the indexes filed
     * by which objects were put in them: handed out, they are this plan's way into trees the caller
     * does not own, and what a caller does with one cannot be told from what it does with its own.
     *
     * <p><b>Written down as what each member hands over, not as what it is called.</b> A name is
     * not what a member gives a caller, and reading the names would be watching the wrong thing:
     * a second {@code armsOf} taking something else and answering with the index is a name already
     * on the list, and so is the same name answering with a wider type than it did. What is listed
     * is the kind, the name, what it takes and what comes back — which is the whole of what a
     * caller can get through it.
     *
     * <p>Fields among them, because a field hands its value over as surely as a method does and is
     * reached without one. There is a public field here already.
     */
    @Test
    void andWhatAPlanHandsOutIsWrittenDown() {
        assertEquals(MAY_BE_PUBLIC, publicSurfaceOfAPlan(),
                "a plan holds indexes filed by which Core objects were put in them, and handing"
                        + " one out is handing over a way into trees the taker does not own. What"
                        + " may be reached from outside this package is a value, or a question"
                        + " answered with one");
    }

    /** What a plan lets a caller outside this package reach, and what it gets through each. */
    private static final Set<String> MAY_BE_PUBLIC = Set.of(
            // The plan of nothing, which is what stands where a module's bodies did not come out.
            "field NONE : souther.compiler.coverage.CoverageSites$Plan",
            // The places, in the order they were numbered, and what a run at one would be.
            "method sites() :"
                    + " java.util.List<souther.compiler.coverage.CoverageSites$Site>",
            "method arms(java.lang.String) :"
                    + " java.util.List<souther.compiler.coverage.CoverageSites$ArmSite>",
            "method hasNoProbes() : boolean",
            // Which comparisons the bodies hold, and what this plan did about each.
            "method comparisons() : souther.compiler.coverage.ComparisonCatalog",
            "method instruments(souther.compiler.coverage.ComparisonOccurrence) : boolean",
            "method emissionSiteOf(souther.compiler.coverage.ComparisonOccurrence) :"
                    + " java.util.Optional<souther.compiler.coverage.ComparisonEmissionSite>",
            "method requireEmissionSiteOf(souther.compiler.coverage.ComparisonOccurrence) :"
                    + " souther.compiler.coverage.ComparisonEmissionSite",
            "method outcomeOf(souther.compiler.coverage.ComparisonOccurrence,boolean) :"
                    + " java.util.Optional<"
                    + "souther.compiler.coverage.ControlPointId$ComparisonPoint>",
            // What a number means, which is the half of a plan that outlives the graph.
            "method numbering() : souther.compiler.coverage.SiteNumbering",
            "method identity() : souther.compiler.coverage.NumberingIdentity",
            // Asked about a node the caller is already holding, which is the emitter's question.
            "method mayRepeat(souther.compiler.core.Core) : boolean",
            "method armsOf(souther.compiler.core.Core) :"
                    + " souther.compiler.coverage.ControlPointId$ArmOccurrence[]",
            "method forkAt(souther.compiler.core.Core) : souther.compiler.coverage.ForkOccurrence",
            "method probesOf(souther.compiler.core.Core) : int[]",
            // Where the fork each numbered arm stands in is written.
            "method whereEachArmsForkIsWritten() :"
                    + " java.util.Map<java.lang.Integer, souther.compiler.diag.Citation>");

    private static Set<String> publicSurfaceOfAPlan() {
        Set<String> out = new TreeSet<>();
        for (Constructor<?> each : CoverageSites.Plan.class.getDeclaredConstructors()) {
            if (Modifier.isPublic(each.getModifiers()) && !each.isSynthetic()) {
                out.add("constructor <init>(" + parameters(each.getGenericParameterTypes()) + ")");
            }
        }
        for (Field each : CoverageSites.Plan.class.getDeclaredFields()) {
            if (Modifier.isPublic(each.getModifiers()) && !each.isSynthetic()) {
                out.add("field " + each.getName() + " : "
                        + each.getGenericType().getTypeName());
            }
        }
        for (Method each : CoverageSites.Plan.class.getDeclaredMethods()) {
            if (Modifier.isPublic(each.getModifiers()) && !each.isSynthetic()) {
                out.add("method " + each.getName()
                        + "(" + parameters(each.getGenericParameterTypes()) + ") : "
                        + each.getGenericReturnType().getTypeName());
            }
        }
        return out;
    }

    private static String parameters(java.lang.reflect.Type[] taken) {
        return Stream.of(taken).map(java.lang.reflect.Type::getTypeName)
                .collect(java.util.stream.Collectors.joining(","));
    }

    /** Each of them written down as happening once, which is what the count is for. */
    private static Map<String, Integer> once(Map<String, String> who) {
        Map<String, Integer> out = new TreeMap<>();
        who.keySet().forEach(each -> out.put(each, 1));
        return out;
    }

    /**
     * Which methods of this compiler invoke {@code name} on {@code owner}, and how often.
     *
     * <p>Both doors are counted out of one reading of the classes. Every class of this module is
     * read and every instruction of it decoded, which is not what a run somebody is waiting on
     * should do twice to answer two questions about the same compiled code.
     */
    private static Map<String, Integer> calling(String owner, String name) {
        return counted().getOrDefault(owner + "." + name, Map.of());
    }

    /** What each door is called by, read once. */
    private static Map<String, Map<String, Integer>> counted() {
        if (COUNTED == null) {
            COUNTED = count();
        }
        return COUNTED;
    }

    private static Map<String, Map<String, Integer>> COUNTED;

    private static Map<String, Map<String, Integer>> count() {
        Map<String, Map<String, Integer>> calls = new TreeMap<>();
        for (ClassModel model : WhatWasCompiled.compiled().all()) {
            String from = model.thisClass().asInternalName().replace('/', '.').replace('$', '.');
            for (MethodModel method : model.methods()) {
                method.code().ifPresent(code -> code.forEach(element -> {
                    if (element instanceof InvokeInstruction call) {
                        String door = call.owner().asInternalName() + "."
                                + call.name().stringValue();
                        if (DOORS.contains(door)) {
                            calls.computeIfAbsent(door, _ -> new TreeMap<>())
                                    .merge(from + "." + method.methodName().stringValue(), 1,
                                            Integer::sum);
                        }
                    }
                }));
            }
        }
        return calls;
    }

    /** The two ways to a plan, as an invocation names them. */
    private static final Set<String> DOORS = Set.of(SITES + ".of", A_PLAN + ".<init>");
}
