package souther.compiler.query;

import org.junit.jupiter.api.Test;
import souther.compiler.coverage.CoverageSites;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Everything still holding a place under a finding is read twice: for what a compile saw of it, and
 * for whether the module-boundary cut has to wait on it.
 *
 * <p><b>Two questions and two axes.</b> What a corpus saw is one thing — two values differing only
 * in their place, nothing reaching one at all, or neither seen. Whether a location crosses the
 * boundary the cut is taken at is another, and no answer to the first is an answer to the second: a
 * place that tells two values apart is identity the cut has to carry across some other way, and a
 * place nothing reached is one nobody has looked at. Held on one axis, the gate read the counting
 * and let two of the three readings through.
 *
 * <p><b>Not a list of what is allowed.</b> While nothing has been shown not to cross, every place
 * under a finding is something the cut waits on. Taking the cut is what this counts down to rather
 * than what it permits.
 *
 * <p>Asked of the finding and not of the thing last put right. A place is taken out of one value at
 * a time, and a check rooted at whichever one that was cannot see the rest — which is how a place
 * came to be left under {@code APointOfADeclaredBorder} while everything about the arms was green.
 * So the walk starts where the answer is.
 *
 * <p><b>The reading is checked, not asserted.</b> Written as a sentence, a reason is whatever
 * whoever added the line believed when they added it. So each reading is a thing the models either
 * show or do not, and a carrier that is none of the three fails. That check is
 * {@link WhatStillHoldsAPlaceUnderAFindingIsWhatTheModelsShowTest}: its subjects are the models this
 * repository carries and sweeping them is what it costs, so it is asked in the run those are asked
 * in. What is asked in every run is here — that each place under a finding is registered at all,
 * that the cut waits on every one nobody has shown does not cross, and that each says what it says
 * it for — and it reads this compiler's own classes, which costs nothing.
 */
class WhatStillHoldsAPlaceUnderAFindingIsReadOnTwoAxesTest {

    /** What a report points with, which is what a finding may not hold. */
    private static final Set<String> A_PLACE = Set.of(
            "souther.compiler.diag.SourcePos",
            "souther.compiler.diag.Citation",
            "souther.compiler.diag.Region",
            "souther.compiler.diag.DiagnosticPlace");

    /**
     * What was observed of a place still under a finding.
     *
     * <p>Three readings and no word for one nobody has read. What would go under a fourth is a
     * carrier whose reason is somebody's opinion, and that is what this exists to stop.
     *
     * <p><b>Named for what was seen and not for what follows from it.</b> A corpus shows that two
     * of these differ only in their place, or that nothing reached one, or that no such pair turned
     * up — and the third of those is not the same statement as "the place is decoration". What a
     * place is for is a question about the values, answered by reading them; a corpus can refute
     * that a place is spare and cannot establish it. A word here that said what a carrier <em>is</em>
     * would be this register doing again what it exists to stop, one level up.
     *
     * <p><b>This axis does not say whether the cut may be taken.</b> Whether a location crosses the
     * module boundary is a different question from what a corpus saw of it, and neither a place
     * that tells two values apart nor a place nothing reached is thereby safe: the first is
     * identity a semantic cut has to carry across some other way, and the second is a carrier
     * nothing has looked at. What the cut waits on is {@link AcrossTheCut}, and reading it off this
     * is what this register was written to stop.
     */
    sealed interface Because {

        /**
         * Two of these were seen differing only in their place, so the place is doing work nothing
         * beside it does: taking it away would merge values this compiler tells apart.
         *
         * <p>The one of the three that settles anything. A pair that differs only there is a
         * refutation of the place being spare, and one is enough.
         */
        record TwoOfThemDifferOnlyThere() implements Because {}

        /** Nothing the models reach is one of these, so nothing has been observed about it at all
         *  and moving it would be acting on nothing. */
        record NothingReachesOne() implements Because {}

        /**
         * The models reach these, and no two of them were seen differing only in their place.
         *
         * <p><b>What was not seen, and not what that means.</b> A pair that would have refuted the
         * place being spare did not turn up in these models; another model may hold one, and what a
         * place is for here is settled by reading the values rather than by counting them. So this
         * says a question is open, not that it has been answered.
         *
         */
        record ReachedAndNotObservedToDiscriminate() implements Because {}
    }

    /**
     * Whether the module-boundary cut of issue #1472 has to wait on a carrier.
     *
     * <p>The other axis, and the one the cut asks. A location under a finding reaches whoever reads
     * the finding, so the cut leaves it holding where a helper used to be — unless somebody has
     * shown that this particular location never crosses. That is a claim about where the value
     * goes, and nothing a corpus counted about the value's places answers it: a place that tells
     * two values apart is identity the cut has to carry across some other way, and a place nothing
     * reached is one nobody has looked at.
     *
     * <p>Held apart from {@link Because} so that the gate cannot be read off the counting. The two
     * were one sum once, and what came of that is that the gate quietly let two of the three
     * readings through.
     */
    sealed interface AcrossTheCut {

        /**
         * It waits.
         *
         * @param owed what has to happen before it stops waiting, said so that a reader meets the
         *             work rather than the excuse
         */
        record BlocksIt(String owed) implements AcrossTheCut {}

        /**
         * Somebody showed this location does not cross the boundary the cut is taken at.
         *
         * @param shown what was shown and how, which is what makes this different from having
         *              counted nothing
         */
        record ShownNotToCrossIt(String shown) implements AcrossTheCut {}
    }

    /** What a place still under a finding was seen to be, and what the cut makes of it. */
    record Standing(Because observed, AcrossTheCut across) {}

    /**
     * Every place still under a finding, and where each stands on both axes.
     *
     * <p>Read from beside it as well as here. What a reading says is checked against the models by
     * {@link WhatStillHoldsAPlaceUnderAFindingIsWhatTheModelsShowTest}, which is a class of its own
     * because sweeping them is what decides which run it lands in; the registry is one all the same,
     * and a carrier is entered in it once.
     */
    static final Map<String, Standing> WHAT_STILL_HOLDS_A_PLACE = whatStillHoldsAPlace();

    private static Map<String, Standing> whatStillHoldsAPlace() {
        Map<String, Standing> out = new TreeMap<>();
        // Nothing the models reach is one of these. That is a count of what was looked at and not
        // a fact about where the value goes, so the cut waits on somebody looking.
        String lookAtIt = "somebody builds a model that reaches one and reads what its place is"
                + " doing, or shows that nothing carrying it crosses a module boundary";
        out.put("souther.compiler.observe.Incompleteness$Met.citations",
                new Standing(new Because.NothingReachesOne(),
                        new AcrossTheCut.BlocksIt(lookAtIt)));
        out.put("souther.compiler.query.About$AnUnansweredRow.at",
                new Standing(new Because.NothingReachesOne(),
                        new AcrossTheCut.BlocksIt(lookAtIt)));
        return out;
    }

    @Test
    void everyPlaceLeftUnderAFindingIsOneOfTheThree() {
        assertEquals(WHAT_STILL_HOLDS_A_PLACE.keySet(), placesUnder(Adequacy.Finding.class),
                "a place under a finding is one this reads, or one to take out");
    }

    /**
     * What the module-boundary cut is waiting on, named so that whoever takes it can ask.
     *
     * <p>Read off {@link AcrossTheCut} and off nothing else. A carrier leaves this when somebody
     * has shown its location does not cross, or when the location is gone; it does not leave by
     * having been counted, however the counting came out.
     *
     * <p>Not asserted empty here, because it is not: what it holds is the work this change found
     * and did not do. It is a method rather than a line in a document so that the question "is
     * anything still going to go stale when the cut is taken" has one answer, and the change that
     * takes the cut is the one that asserts this is empty.
     */
    static Set<String> stillBlockingTheCut() {
        Set<String> blocking = new TreeSet<>();
        WHAT_STILL_HOLDS_A_PLACE.forEach((carrier, standing) -> {
            if (standing.across() instanceof AcrossTheCut.BlocksIt) {
                blocking.add(carrier);
            }
        });
        return blocking;
    }

    /**
     * Nothing leaves the gate by having been counted.
     *
     * <p>The two axes are held apart by the types, and this is what says the register is using
     * them that way: while nothing has been shown not to cross, every place under a finding is
     * something the cut waits on — whichever of the three a corpus saw. Written out because the
     * fault it guards against is invisible otherwise, the gate having once let two of the three
     * readings through and stayed green.
     */
    @Test
    void everyPlaceLeftUnderAFindingIsSomethingTheCutWaitsOn() {
        Set<String> shownNotToCross = new TreeSet<>();
        WHAT_STILL_HOLDS_A_PLACE.forEach((carrier, standing) -> {
            if (standing.across() instanceof AcrossTheCut.ShownNotToCrossIt) {
                shownNotToCross.add(carrier);
            }
        });
        Set<String> waitedOn = new TreeSet<>(WHAT_STILL_HOLDS_A_PLACE.keySet());
        waitedOn.removeAll(shownNotToCross);
        assertEquals(waitedOn, stillBlockingTheCut(),
                "a carrier leaves the gate by being shown not to cross, and by nothing else");
    }

    /**
     * And what either axis says of a carrier is something somebody wrote down.
     *
     * <p>A carrier the cut waits on says what it is waiting for; one said not to cross says what
     * showed that. Either with nothing beside it is a line whose reason nobody gave, which is the
     * state this whole register exists to keep out.
     */
    @Test
    void everythingOnTheCutAxisSaysWhatItIsSayingItFor() {
        Set<String> saidNothing = new TreeSet<>();
        WHAT_STILL_HOLDS_A_PLACE.forEach((carrier, standing) -> {
            boolean blank = switch (standing.across()) {
                case AcrossTheCut.BlocksIt(String owed) -> owed.isBlank();
                case AcrossTheCut.ShownNotToCrossIt(String shown) -> shown.isBlank();
            };
            if (blank) {
                saidNothing.add(carrier);
            }
        });
        assertEquals(Set.of(), saidNothing,
                "what the cut waits on is work somebody named, and what it does not wait on is"
                        + " something somebody showed");
    }

    /**
     * The walk says no because there is none, and not because it says no.
     *
     * <p>A site of a comparison still carries where it is, which is a question of its own and not
     * this one. Named here so that the answer above is an answer: a walk that had stopped finding
     * places would give it whatever it was asked.
     */
    @Test
    void andTheWalkFindsOneWhereThereIsOne() {
        assertEquals(Set.of("souther.compiler.coverage.CoverageSites$ComparisonSite.at"),
                placesUnder(CoverageSites.ComparisonSite.class),
                "a comparison's site is where a report about it points, and still holds it");
    }

    /**
     * Every place declared anywhere under {@code root}, by where it sits.
     *
     * <p>The tree the author wrote is not walked into. Every node of it carries where it is, which
     * is the source saying where it is rather than an answer carrying a caret, and walking in would
     * bury the question this asks under the whole of the language.
     */
    private static Set<String> placesUnder(Class<?> root) {
        Set<String> found = new TreeSet<>();
        Set<Class<?>> seen = new LinkedHashSet<>();
        Deque<Type> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            Class<?> at = raw(queue.poll());
            if (at == null || !seen.add(at) || at.getName().startsWith("java.")
                    || at.getName().startsWith("souther.compiler.ast.")
                    || at.getName().startsWith("souther.compiler.core.")
                    || isAPlace(at)) {
                continue;
            }
            Class<?>[] cases = at.getPermittedSubclasses();
            if (cases != null) {
                queue.addAll(List.of(cases));
            }
            for (Field field : at.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                for (Type each : withArguments(field.getGenericType())) {
                    Class<?> of = raw(each);
                    if (of != null && isAPlace(of)) {
                        found.add(at.getName() + "." + field.getName());
                    }
                    queue.add(each);
                }
            }
        }
        return found;
    }

    /**
     * Whether {@code of} is one of the things a report points with.
     *
     * <p>What one is made of is not walked into. A citation holds the position it projects, so a
     * walk that went in would report a place inside every place and say nothing about which values
     * hold one.
     */
    private static boolean isAPlace(Class<?> of) {
        if (A_PLACE.contains(of.getName())) {
            return true;
        }
        for (Class<?> each : of.getInterfaces()) {
            if (A_PLACE.contains(each.getName())) {
                return true;
            }
        }
        return false;
    }

    /** A written type and whatever was written inside it, so a place in a list is a place. */
    private static List<Type> withArguments(Type type) {
        return type instanceof ParameterizedType wrote
                ? Stream.concat(Stream.of(type),
                        java.util.Arrays.stream(wrote.getActualTypeArguments())).toList()
                : List.of(type);
    }

    private static Class<?> raw(Type type) {
        return switch (type) {
            case Class<?> at -> at;
            case ParameterizedType wrote -> raw(wrote.getRawType());
            case WildcardType any -> any.getUpperBounds().length == 0
                    ? null : raw(any.getUpperBounds()[0]);
            case TypeVariable<?> letter -> letter.getBounds().length == 0
                    ? null : raw(letter.getBounds()[0]);
            default -> null;
        };
    }
}
