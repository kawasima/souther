package souther.compiler.coverage;

import org.junit.jupiter.api.Test;
import souther.compiler.check.PathReachability;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.reach.Reachability;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Where a run through an arm is recorded and which arm that is are one value, made once.
 *
 * <p>A site of an arm holds the place, and the address it publishes is the place's own. Held beside
 * it, the address would be the same fact written twice — and a reader given one half would have to
 * find the other, which over a reading of a body means searching it for whichever entry carries the
 * address asked about. That search assumes what the numbering guarantees, silently: it reads the
 * first entry it finds and could not notice two arms answering to one probe.
 */
class AnArmsSiteAndItsPlaceAreOneValueTest {

    private static final String MODEL = """
            module example.arms

            data Count = Int
                invariant lower = value >= 0
            data Small
            data Big

            behavior classify : (c: Count) -> Small | Big

            let classify (c) =
                if c.value >= 50
                    then Big
                    else Small

            example classify
                | "small" : (Count(1)) -> Small
            """;

    /**
     * The site of an arm holds that arm's own place, and holds the very value the fork's arms are
     * held by.
     *
     * <p>Which arm a site is of is taken from the obligation, which says which way through the fork
     * a row would be owed for. That is the other end of the same act and is not what is under test:
     * what the finalization could get wrong is the arm a site was paired with, so the expected value
     * has to be reached by something other than the pairing.
     *
     * <p>Identity and not equality. Two occurrences of one arm are equal records, so a second one
     * made beside the first would answer every question alike until a reader kept one and looked the
     * other up.
     */
    @Test
    void aSiteHoldsThePlaceOfTheArmItIsASiteOf() {
        CoverageSites.Plan plan = planOf(MODEL);
        ControlPointId.ArmOccurrence[] arms = plan.armsByNode().values().stream().findFirst()
                .orElseThrow(() -> new AssertionError("the model writes a fork"));
        assertEquals(1, plan.armsByNode().size(),
                "one fork, so which arms a site is to be found among is not in question");

        List<CoverageSites.ArmSite> sites = plan.sites().stream()
                .filter(CoverageSites.ArmSite.class::isInstance)
                .map(CoverageSites.ArmSite.class::cast).toList();
        assertEquals(arms.length, sites.size(), "every arm of it is a place a run is recorded in");
        for (CoverageSites.ArmSite site : sites) {
            assertSame(arms[site.obligation().part()], site.occurrence(),
                    () -> "the site owed for arm " + site.obligation().part() + " holds "
                            + site.occurrence() + ", which is not that arm of the fork");
        }
    }

    /**
     * A model whose refused arm answers no value, so the walk numbers no place in it.
     *
     * <p>An {@code Active} is never {@code Off}, so the rules prove nothing arrives at that arm; the
     * arm answers no value, so no run through it could ever be recorded. Both are true of one arm,
     * which is what makes it the arm this is about.
     */
    private static final String SILENT_REFUSED_ARM = """
            module example.silent

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
                | Off     -> unreachable "an Active is never Off"

            example pick
                | "on" : (Active(On)) -> Yes
            """;

    /**
     * A place with no probe carries the proof about it, and the reading answers it.
     *
     * <p>The two are independent and were one answer while the question could only be put by probe:
     * whether a run through an arm can be recorded is what instrumenting it decides, and whether
     * anything arrives is what the model's rules decide. An arm nobody can record a run in is
     * exactly the arm a proof about it can never be shown wrong by, which is the arm a reader most
     * needs the answer for.
     */
    @Test
    void aRealPlaceWithNoProbeCarriesTheProofAboutIt() {
        PathReachability.Answers answers = arrivalsOf(SILENT_REFUSED_ARM);
        List<ControlPointId.ArmOccurrence> silent = answers.found().keySet().stream()
                .filter(ControlPointId.ArmOccurrence.class::isInstance)
                .map(ControlPointId.ArmOccurrence.class::cast)
                .filter(arm -> arm.probe().isEmpty()).toList();

        assertEquals(1, silent.size(),
                () -> "the refused arm answers no value, so it is the arm nothing numbered: "
                        + answers.found().keySet());
        assertInstanceOf(Reachability.Unreachable.class, answers.at(silent.get(0)),
                "and the rules prove nothing arrives at it, which the reading answers about the"
                        + " place rather than about an address it has not got");
    }

    /**
     * A place this reading never filed is unsettled, which is not the same fact as the one above.
     *
     * <p>The fail-open direction of {@link PathReachability.Answers#at}: a walk that did not get
     * somewhere says nothing about it, and every consumer treats that as it treats any other place
     * the rules settled nothing at.
     */
    @Test
    void aPlaceTheWalkDidNotReachIsUnsettledRatherThanAbsent() {
        PathReachability.Answers answers = arrivalsOf(MODEL);
        ControlPointId.ArmOccurrence any = answers.found().keySet().stream()
                .filter(ControlPointId.ArmOccurrence.class::isInstance)
                .map(ControlPointId.ArmOccurrence.class::cast)
                .findFirst().orElseThrow();
        ControlPointId.ArmOccurrence never = new ControlPointId.ArmOccurrence(
                Integer.MAX_VALUE, Optional.empty(), any.anchor(), any.origin());

        assertInstanceOf(Reachability.Unsettled.class, answers.at(never),
                "a place nothing was filed under is one the walk did not reach");
    }

    /** What the model's own rules say arrives at each place of {@code model}'s one behavior. */
    private static PathReachability.Answers arrivalsOf(String model) {
        Compilation compilation = compiled(model);
        Map<String, PathReachability.Answers> answers = compilation.db()
                .ask(new Adequacy.PathReached(compilation.modules().get(0))).value();
        return answers.values().stream().findFirst()
                .orElseThrow(() -> new AssertionError("the model under test is measured"));
    }

    /**
     * The one thing the reading may be handed a run's own vocabulary for is a correction by a run.
     *
     * <p>The whole surface and not the methods anybody remembers, and said as the rule rather than
     * as a list of the answers it could come back as. A reading asked with an address answers a
     * question the numbering owns — which arm a number addresses — and it holds no numbering to ask;
     * what it can be told is what a run lit, and what comes back from that is the corrected reading
     * ({@link PathReachability.Answers.AsRun}) rather than an answer about any one place.
     *
     * <p>Read off the generic signature, since a run's vocabulary reaches this surface inside a
     * {@code Set} and the erased parameter is nothing but {@code Set}. And read as a family: what
     * addresses a place a run is recorded at is {@link RunSite}, so a third kind of address added
     * beside the two is under this rule the day it exists.
     *
     * <p>Over this reading's own surface and no further. What a correction hands back holds what a
     * run lit and is asked about in a run's words rightly — {@code provedWrong()} is a set of
     * probes, and a reader is free to ask it whether one is in there. The rule is about answering a
     * question of the model's out of a number, which is what only this class answers.
     */
    @Test
    void theReadingTakesARunsVocabularyOnlyToBeCorrectedByOne() {
        List<String> asking = new ArrayList<>();
        for (Method each : PathReachability.Answers.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(each.getModifiers())) {
                continue;
            }
            for (Type taken : each.getGenericParameterTypes()) {
                if (namesTheArmsARunLit(taken)) {
                    if (each.getReturnType() != PathReachability.Answers.AsRun.class) {
                        asking.add(each.getName() + " takes " + taken + " and answers "
                                + each.getReturnType().getSimpleName());
                    }
                } else if (namesARunsOwnVocabulary(taken)) {
                    asking.add(each.getName() + " takes " + taken);
                }
            }
        }
        assertEquals(List.of(), asking,
                "a number means a place under the numbering that handed it out, and this reading"
                        + " holds no numbering to ask");
    }

    /** The probes a run lit, which is the one thing a run tells this reading. */
    private static boolean namesTheArmsARunLit(Type taken) {
        return mentions(taken, each -> each == ArmProbe.class);
    }

    /** Anything else a run is written and read in, which this reading is never asked with. */
    private static boolean namesARunsOwnVocabulary(Type taken) {
        return mentions(taken, each -> RunSite.class.isAssignableFrom(each)
                || each == ComparisonOutcome.class || each == int.class);
    }

    /**
     * Whether {@code type} names such a class anywhere in it, past every erasure.
     *
     * <p>Each type is read once. A variable can be bounded by something naming itself — {@code <T
     * extends Comparable<T>>} — and a walk that took the bound again would not come back.
     */
    private static boolean mentions(Type type, java.util.function.Predicate<Class<?>> what) {
        return mentions(type, what, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static boolean mentions(Type type, java.util.function.Predicate<Class<?>> what,
                                    java.util.Set<Type> read) {
        if (type != null && !read.add(type)) {
            return false;
        }
        return switch (type) {
            case Class<?> each -> what.test(each)
                    || (each.isArray() && mentions(each.getComponentType(), what, read));
            case ParameterizedType each -> mentions(each.getRawType(), what, read)
                    || Arrays.stream(each.getActualTypeArguments())
                            .anyMatch(argument -> mentions(argument, what, read));
            case GenericArrayType each -> mentions(each.getGenericComponentType(), what, read);
            case WildcardType each -> Stream.concat(Arrays.stream(each.getUpperBounds()),
                            Arrays.stream(each.getLowerBounds()))
                    .anyMatch(bound -> mentions(bound, what, read));
            case TypeVariable<?> each -> Arrays.stream(each.getBounds())
                    .anyMatch(bound -> mentions(bound, what, read));
            case null, default -> false;
        };
    }

    private static CoverageSites.Plan planOf(String model) {
        Compilation compilation = compiled(model);
        Bodies.Elaborated checked = compilation.db()
                .ask(new Bodies.Checked(compilation.modules().get(0))).value();
        return checked.plan();
    }

    private static Compilation compiled(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.answerEverything();
        return compilation;
    }
}
