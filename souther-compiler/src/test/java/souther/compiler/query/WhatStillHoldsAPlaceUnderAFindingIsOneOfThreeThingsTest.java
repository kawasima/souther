package souther.compiler.query;

import org.junit.jupiter.api.Test;
import souther.compiler.coverage.CoverageSites;
import souther.compiler.meta.ModulePath;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Everything still holding a place under a finding is one of three things, and which one is what a
 * compile shows rather than what somebody wrote down.
 *
 * <p><b>Not a list of what is allowed.</b> Two of the three are things a corpus settles: a place
 * two values were seen to differ only in, and a place nothing reached. The third settles nothing —
 * the models reach it and no such pair turned up, which leaves open what the place is for. Every
 * one of those is a location still reaching a finding, so every one is a carrier the
 * module-boundary cut of issue #1472 would leave pointing at where a helper used to be, whichever
 * way that question comes out. Taking the cut is what this counts down to rather than what it
 * permits.
 *
 * <p>Asked of the finding and not of the thing last put right. A place is taken out of one value at
 * a time, and a check rooted at whichever one that was cannot see the rest — which is how a place
 * came to be left under {@code APointOfADeclaredBorder} while everything about the arms was green.
 * So the walk starts where the answer is.
 *
 * <p><b>The reading is checked, not asserted.</b> Written as a sentence, a reason is whatever
 * whoever added the line believed when they added it — and two of the ones first written here were
 * wrong about a census this same change had taken, while the census itself could not see a place
 * held in a collection. So each reading is a thing the models either show or do not, and a carrier
 * that is none of the three fails.
 */
class WhatStillHoldsAPlaceUnderAFindingIsOneOfThreeThingsTest {

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
     * <p>Two of them are answers and the third is a question nobody has put yet. Told apart here so
     * that a reader counting what is left to look into counts
     * {@link ReachedAndNotObservedToDiscriminate} and nothing else.
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
         * <p>Every one of these is a path by which a location still reaches a finding, so every one
         * is a carrier the module-boundary cut of issue #1472 would leave pointing at where a
         * helper used to be — whichever way the question comes out. The cut waits on this being
         * empty, and what empties it is somebody deciding, per carrier, whether the place is a
         * handle to be asked for or an identity nothing else supplies.
         *
         * @param owed what has to happen before it goes, said so that a reader meets the work
         *             rather than the excuse
         */
        record ReachedAndNotObservedToDiscriminate(String owed) implements Because {}
    }

    /** Every place still under a finding, and the reading each is here under. */
    private static final Map<String, Because> WHAT_STILL_HOLDS_A_PLACE = whatStillHoldsAPlace();

    private static Map<String, Because> whatStillHoldsAPlace() {
        Map<String, Because> out = new TreeMap<>();
        // The one place a place is doing work no identity beside it does: two conditions this
        // compiler declined to cut are told apart by where they are and by nothing else.
        out.put("souther.compiler.partition.OnTheWay$Declined.at",
                new Because.TwoOfThemDifferOnlyThere());
        out.put("souther.compiler.observe.Incompleteness$Met.citations",
                new Because.NothingReachesOne());
        out.put("souther.compiler.partition.PredicateOrigin.writtenAt",
                new Because.NothingReachesOne());
        out.put("souther.compiler.query.About$AnUnansweredRow.at",
                new Because.NothingReachesOne());
        // What a document prints for a rule the author gave no name, and the sets folded out of it.
        // A rule is beside the place in each of them, and no two were seen differing only in the
        // place — which leaves open whether the place is spare here, and that is the question.
        String splitTheHandle = "somebody reads what a published handle is, and says whether the"
                + " place is one to ask for or one nothing else supplies (issue #1485)";
        out.put("souther.compiler.check.RuleCitation$WrittenAt.at",
                new Because.ReachedAndNotObservedToDiscriminate(splitTheHandle));
        out.put("souther.compiler.partition.LineOrigin$ComparisonOrigin$Read.writtenAt",
                new Because.ReachedAndNotObservedToDiscriminate(splitTheHandle));
        out.put("souther.compiler.inputs.RuleWithoutALine.reachedAt",
                new Because.ReachedAndNotObservedToDiscriminate(splitTheHandle));
        out.put("souther.compiler.inputs.StandingQuestion$BoundaryUndetermined.reachedAt",
                new Because.ReachedAndNotObservedToDiscriminate(splitTheHandle));
        out.put("souther.compiler.inputs.StandingQuestion$Exact.reachedAt",
                new Because.ReachedAndNotObservedToDiscriminate(splitTheHandle));
        out.put("souther.compiler.inputs.StandingQuestion$NothingClassifiesIt.reachedAt",
                new Because.ReachedAndNotObservedToDiscriminate(splitTheHandle));
        String splitTheWay = "somebody reads what tells one condition on the way to a border from"
                + " its neighbours, the way the third arm beside these was read (issue #1486)";
        out.put("souther.compiler.partition.OnTheWay$Narrowed.at",
                new Because.ReachedAndNotObservedToDiscriminate(splitTheWay));
        out.put("souther.compiler.partition.OnTheWay$TakenIn.at",
                new Because.ReachedAndNotObservedToDiscriminate(splitTheWay));
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
     * <p>Not asserted empty here, because it is not: what it holds is the work this change found
     * and did not do. It is a method rather than a line in a document so that the question "is
     * anything still going to go stale when the cut is taken" has one answer, and the change that
     * takes the cut is the one that asserts this is empty.
     */
    static Set<String> stillBlockingTheCut() {
        Set<String> blocking = new TreeSet<>();
        WHAT_STILL_HOLDS_A_PLACE.forEach((carrier, because) -> {
            if (because instanceof Because.ReachedAndNotObservedToDiscriminate) {
                blocking.add(carrier);
            }
        });
        return blocking;
    }

    /**
     * And what is waiting is waiting on work, not on a decision.
     *
     * <p>Every one of them says what has to happen to it. A carrier here with nothing said is one
     * whose reason nobody wrote, which is the state this whole register exists to keep out.
     */
    @Test
    void everythingTheCutWaitsOnSaysWhatItIsWaitingFor() {
        Set<String> saidNothing = new TreeSet<>();
        WHAT_STILL_HOLDS_A_PLACE.forEach((carrier, because) -> {
            if (because instanceof Because.ReachedAndNotObservedToDiscriminate(String owed)
                    && owed.isBlank()) {
                saidNothing.add(carrier);
            }
        });
        assertEquals(Set.of(), saidNothing,
                "what the cut waits on is work somebody named");
    }

    /**
     * And each reading is what a compile shows, rather than what somebody wrote down.
     *
     * <p>The corpus is small and the readings are about what it reaches, so a carrier this says
     * nothing reaches is one nothing reaches <em>here</em>. That is the whole of the claim: it is
     * why the word is "nothing has been measured" and not "there are none".
     */
    @Test
    void andEachReadingIsWhatACompileShows() {
        Map<String, List<Object>> byCarrier = carriersInTheModels();
        Map<String, String> wrong = new TreeMap<>();
        WHAT_STILL_HOLDS_A_PLACE.forEach((carrier, because) -> {
            List<Object> held = byCarrier.getOrDefault(carrier, List.of());
            String said = says(because, held, carrier);
            if (said != null) {
                wrong.put(carrier, said);
            }
        });
        assertEquals(Map.of(), wrong,
                "each place left under a finding is here under a reading the compile shows");
    }

    /** What is wrong with {@code because} as a reading of {@code held}, or null where nothing is. */
    private static String says(Because because, List<Object> held, String carrier) {
        boolean apart = tellsThemApart(held, carrier);
        return switch (because) {
            case Because.NothingReachesOne _ -> held.isEmpty() ? null
                    : "something reaches one: " + held.size() + " of them";
            case Because.TwoOfThemDifferOnlyThere _ -> held.isEmpty()
                    ? "nothing reaches one, so no pair was seen at all"
                    : apart ? null : "no two of them were seen differing only in the place";
            case Because.ReachedAndNotObservedToDiscriminate _ -> held.isEmpty()
                    ? "nothing reaches one, so nothing was observed"
                    : apart ? "two of them differ only in the place, which is the other reading"
                            : null;
        };
    }

    /** Whether two of {@code held} agree on everything but where they are. */
    private static boolean tellsThemApart(List<Object> held, String carrier) {
        Map<List<Object>, Set<List<Object>>> byRest = new LinkedHashMap<>();
        for (Object each : held) {
            List<Object> rest = new ArrayList<>();
            List<Object> place = new ArrayList<>();
            for (Field field : each.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                try {
                    (carrier.endsWith("." + field.getName()) ? place : rest).add(field.get(each));
                } catch (IllegalAccessException unreadable) {
                    throw new IllegalStateException(unreadable);
                }
            }
            byRest.computeIfAbsent(rest, _ -> new LinkedHashSet<>()).add(place);
        }
        return byRest.values().stream().anyMatch(places -> places.size() > 1);
    }

    /** Every instance of a registered carrier this compile holds, by carrier. */
    private static Map<String, List<Object>> carriersIn(Db db) {
        Set<String> wanted = new LinkedHashSet<>();
        WHAT_STILL_HOLDS_A_PLACE.keySet().forEach(each -> wanted.add(each.substring(0, each.lastIndexOf('.'))));
        Map<String, List<Object>> out = new LinkedHashMap<>();
        Set<Object> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Object> queue = new ArrayDeque<>();
        db.everyAnswer().values().forEach(queue::add);
        while (!queue.isEmpty()) {
            Object at = queue.poll();
            if (at == null || !seen.add(at)) {
                continue;
            }
            switch (at) {
                case Collection<?> many -> {
                    many.forEach(each -> push(queue, each));
                    continue;
                }
                case Map<?, ?> map -> {
                    map.forEach((key, value) -> {
                        push(queue, key);
                        push(queue, value);
                    });
                    continue;
                }
                case Optional<?> maybe -> {
                    maybe.ifPresent(each -> push(queue, each));
                    continue;
                }
                default -> { }
            }
            Class<?> of = at.getClass();
            if (of.getName().startsWith("java.") || of.isEnum()) {
                continue;
            }
            if (wanted.contains(of.getName())) {
                out.computeIfAbsent(carrierOf(of), _ -> new ArrayList<>()).add(at);
            }
            for (Field field : of.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
                    continue;
                }
                field.setAccessible(true);
                try {
                    push(queue, field.get(at));
                } catch (IllegalAccessException unreadable) {
                    throw new IllegalStateException(unreadable);
                }
            }
        }
        return out;
    }

    /** The registered carrier {@code of} is the class of. */
    private static String carrierOf(Class<?> of) {
        return WHAT_STILL_HOLDS_A_PLACE.keySet().stream()
                .filter(each -> each.startsWith(of.getName() + "."))
                .findFirst().orElseThrow();
    }

    private static void push(Deque<Object> queue, Object each) {
        if (each != null) {
            queue.add(each);
        }
    }

    /**
     * The models this repository carries, which is what the readings above are readings of.
     *
     * <p>Every one of them and not the nearest. The readings are about what a compile reaches, so a
     * corpus left out is a carrier this would say nothing reaches — and the census these were taken
     * from was taken over these, so a check over fewer would be answering about a different set
     * than the one somebody measured.
     */
    private static final List<String> THE_MODELS = List.of(
            "src/test/resources/souther/compiler/conformance/catalog",
            "src/test/resources/souther/compiler/conformance/staffing",
            "../souther-bench/src/main/resources/souther/bench/corpus/crm",
            "../souther-bench/src/main/resources/souther/bench/corpus/issuetracker");

    /** Every instance of a registered carrier the models reach, by carrier. */
    private static Map<String, List<Object>> carriersInTheModels() {
        Map<String, List<Object>> out = new LinkedHashMap<>();
        for (String model : THE_MODELS) {
            carriersIn(compiled(model)).forEach((carrier, held) ->
                    out.computeIfAbsent(carrier, _ -> new ArrayList<>()).addAll(held));
        }
        return out;
    }

    /** A compile of one model, with its findings asked for. */
    private static Db compiled(String model) {
        Map<String, String> byId = new LinkedHashMap<>();
        Path root = Path.of(model);
        try (Stream<Path> files = Files.walk(root)) {
            for (Path each : files.filter(p -> p.toString().endsWith(".sou")).sorted().toList()) {
                byId.put(each.getFileName().toString(), Files.readString(each));
            }
        } catch (java.io.IOException unreadable) {
            throw new java.io.UncheckedIOException(unreadable);
        }
        Compilation c = Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY);
        c.answerEverything();
        c.modules().forEach(module -> c.db().ask(new Adequacy.Findings(module)));
        return c.db();
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
