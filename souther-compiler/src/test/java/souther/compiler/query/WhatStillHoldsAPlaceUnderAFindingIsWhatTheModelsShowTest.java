package souther.compiler.query;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import souther.compiler.conformance.ConformanceCorpus;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.WhatStillHoldsAPlaceUnderAFindingIsReadOnTwoAxesTest.Because;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static souther.compiler.query.WhatStillHoldsAPlaceUnderAFindingIsReadOnTwoAxesTest.WHAT_STILL_HOLDS_A_PLACE;

/**
 * Each reading of a place still under a finding is what a compile shows, rather than what somebody
 * wrote down.
 *
 * <p>The registry is {@link WhatStillHoldsAPlaceUnderAFindingIsReadOnTwoAxesTest}'s, and so are the
 * questions a build asks of it: that each place under a finding is registered at all, that the cut
 * waits on every one nobody has shown does not cross, and that each says what it says it for. Those
 * read the registry against this compiler's own classes and cost nothing.
 *
 * <p>This one is here on its own because its subjects are the models this repository carries, and
 * that is what decides which run it lands in. Sweeping them is most of what it costs, and a tag on
 * one method of a class the rest of which is free is a fact about the run that only surefire can
 * see: a check reading the compiled tests for what sweeps a population reads the class. So the
 * sweeping question is a class.
 *
 * <p>A carrier added without a reading fails a build; a reading that has gone out of date with the
 * models fails the run that reads them.
 */
@Tag("population")
class WhatStillHoldsAPlaceUnderAFindingIsWhatTheModelsShowTest {

    /**
     * And each reading is what a compile shows.
     *
     * <p>The readings are about what the models reach, so a carrier the registry says nothing
     * reaches is one nothing reaches <em>in them</em>. That is the whole of the claim: it is why the
     * word is "nothing has been observed" and not "there are none".
     */
    @Test
    void andEachReadingIsWhatACompileShows() {
        Map<String, List<Object>> byCarrier = carriersInTheModels();
        Map<String, String> wrong = new TreeMap<>();
        WHAT_STILL_HOLDS_A_PLACE.forEach((carrier, standing) -> {
            List<Object> held = byCarrier.getOrDefault(carrier, List.of());
            String said = says(standing.observed(), held, carrier);
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
        WHAT_STILL_HOLDS_A_PLACE.keySet()
                .forEach(each -> wanted.add(each.substring(0, each.lastIndexOf('.'))));
        Map<String, List<Object>> out = new LinkedHashMap<>();
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
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
     * Every instance of a registered carrier the models this repository carries reach, by carrier.
     *
     * <p>Every one of them and not the nearest. The readings are about what a compile reaches, so a
     * corpus left out is a carrier this would say nothing reaches — and the census these were taken
     * from was taken over all of them, so a check over fewer would be answering about a different
     * set than the one somebody measured. Which corpora those are is asked of the manifest: a list
     * written here would answer that a second time, and go on answering it after a corpus was
     * added.
     */
    private static Map<String, List<Object>> carriersInTheModels() {
        Map<String, List<Object>> out = new LinkedHashMap<>();
        for (String model : ConformanceCorpus.manifest().keySet()) {
            carriersIn(compiled(model)).forEach((carrier, held) ->
                    out.computeIfAbsent(carrier, _ -> new ArrayList<>()).addAll(held));
        }
        return out;
    }

    /**
     * A compile of one model, with its findings asked for.
     *
     * <p>Handed over as documents named by their files, in the order the corpus declares — which is
     * what a compile of it is, and not what a walk of a directory happened to sort into. An
     * {@code examples for} file read before the module it is attached to is a different compile.
     */
    private static Db compiled(String model) {
        Map<String, String> byId = new LinkedHashMap<>();
        List<String> files = ConformanceCorpus.filesOf(model);
        List<String> sources = ConformanceCorpus.sourcesOf(model);
        for (int i = 0; i < files.size(); i++) {
            byId.put(files.get(i), sources.get(i));
        }
        Compilation c = Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY);
        c.answerEverything();
        c.modules().forEach(module -> c.db().ask(new Adequacy.Findings(module)));
        return c.db();
    }
}
