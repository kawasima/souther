package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.numeric.OrderedIntervals;
import souther.compiler.regex.Meter;
import souther.compiler.regex.PatternPlan;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;
import souther.compiler.values.ValueSet;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which values the positions may take and where their orders stop are one answer, held by one type.
 *
 * <p>Two readings say what one declaration's clauses leave, and neither has a word for what the
 * other holds. Asked one at a time, they answer that a value exists whenever the one that was asked
 * had nothing to say about the rules — which is how {@code String.startsWith("JP", value)} beside
 * {@code value < "JA"} was accepted, the strings and the ends sharing none.
 *
 * <p>What was wrong was not any one of the places that asked. The sentence saying either language
 * could hold the whole answer on its own was written in five of them, so forbidding the spelling
 * would leave a sixth to be written. What is checked here is that there is no type left holding the
 * two apart: a reader with both halves in hand is a reader that can compose them, and the halves are
 * only ever in hand together.
 */
class WhatDecidesWhetherAValueExistsHoldsBothLanguagesTest {

    /**
     * Where the positions stop is held by {@link Confinement} and by nothing else.
     *
     * <p>Derived from what the types hold and not from what anybody wrote. A reading of an order is
     * half of the question, and a type holding that half beside the other is a type whose reader can
     * compose them — which is the sentence this whole arrangement is against, and it was written in
     * five places before any of them was called wrong.
     *
     * <p>So the halves are not separately in hand anywhere. What a reader wants of one of them is a
     * question with a name on this type, and the question about both has one implementation that no
     * holder can answer around.
     */
    @Test
    void whereThePositionsStopIsHeldByTheConfinementAndByNothingElse() {
        Set<String> apart = new TreeSet<>();
        int found = 0;
        for (Class<?> each : compiled()) {
            if (!holdsWhereThePositionsStop(each)) {
                continue;
            }
            found++;
            if (!Confinement.class.isAssignableFrom(each)) {
                apart.add(each.getName());
            }
        }
        assertTrue(found > 0, "found no type holding an order at all — the scan missed the tree");
        assertEquals(Set.of(), apart,
                "a type holding where the positions stop beside what they admit is one whose reader"
                        + " can ask each of them whether anything satisfies the rules");
    }

    /** Whether a class holds where a value's positions stop. */
    private static boolean holdsWhereThePositionsStop(Class<?> of) {
        for (Field each : of.getDeclaredFields()) {
            if (each.getType() == OrderedIntervals.class) {
                return true;
            }
        }
        return false;
    }

    /**
     * Every carrier answers whether a set of values and a range on it share one.
     *
     * <p>Over the carriers the language has and not over a list kept here: a ninth is a build
     * failure at the switch that answers, and a ninth that compiled by answering nothing at all
     * would leave every rule about its values and every rule about its ends read apart again.
     *
     * <p>What is asked of each is the two answers a carrier cannot be short of, and both of them
     * settled: every value of it is inside what its order reaches, and a rule admitting no value at
     * all shares none with any range. A carrier answering that it does not know either of those
     * would refuse nothing and admit nothing.
     */
    @Test
    void everyCarrierAnswersWhatASetOfValuesAndARangeShare() {
        List<Carrier> carriers = everyCarrier();
        assertEquals(Carrier.class.getPermittedSubclasses().length, carriers.size(),
                "a carrier this could not make is one the answer below is not asked of");
        for (Carrier each : carriers) {
            Meter meter = PatternPlan.Budget.OF_WHAT_A_SET_AND_A_RANGE_SHARE.meter();
            assertEquals(souther.compiler.values.Emptiness.NONEMPTY, each.meets(ValueSet.ANY, each.extent(), meter),
                    each + " holds values, and every one of them is admitted");
            assertEquals(souther.compiler.values.Emptiness.EMPTY, each.meets(ValueSet.NONE, each.extent(), meter),
                    each + " holds no value a rule admitting none allows");
        }
    }

    /** One of every carrier, built from what the language seals rather than listed here. */
    private static List<Carrier> everyCarrier() {
        List<Carrier> out = new ArrayList<>();
        for (Class<?> each : Carrier.class.getPermittedSubclasses()) {
            if (each == Carrier.Ordinal.class) {
                // The one that carries something: an enumeration counts its cases, so a carrier of
                // one is a carrier of some enumeration.
                out.add(new Carrier.Ordinal(
                        TypeSymbols.declared(new TypeKey("demo", "Stage")),
                        List.of(TypeSymbols.declared(new TypeKey("demo", "Ready")),
                                TypeSymbols.declared(new TypeKey("demo", "Done")))));
                continue;
            }
            try {
                out.add((Carrier) each.getDeclaredConstructor().newInstance());
            } catch (ReflectiveOperationException _) {
                // Left out, and the count above is what says so.
            }
        }
        return out;
    }

    /**
     * Every predicate over strings the library declares is one whose strings the values read.
     *
     * <p>The other half of the same arrangement. A set and a range are met here, so a predicate that
     * says which strings a position holds has to reach the values for the meeting to be about it —
     * reaching the predicates alone, it is a key that relates to nothing, and the rule it states is
     * one nothing about the order can ever be held against.
     *
     * <p>The population is the library's own declarations and not a list kept beside them, so a
     * predicate added to the language fails this until something reads what it admits.
     */
    @Test
    void everyPredicateOverStringsTheLibraryDeclaresIsOneTheValuesRead() {
        Set<String> read = new LinkedHashSet<>();
        for (StringPredicates each : StringPredicates.values()) {
            read.add(each.kernel().key());
        }
        Set<String> declared = predicatesOverStrings();
        assertFalse(declared.isEmpty(), "found no predicate over strings — the scan missed it");
        assertEquals(Set.of(), difference(declared, read),
                "a predicate over strings says which strings a position may hold, and what meets"
                        + " that against where the position stops is the reading of values");
    }

    /** What the library declares as an operation over strings answering a truth. */
    private static Set<String> predicatesOverStrings() {
        Pattern declared = Pattern.compile(
                "let\\s+\\w+\\s*\\([^)]*\\)\\s*:\\s*Bool\\s*=\\s*intrinsic\\s+\"([^\"]+)\"");
        Matcher found = declared.matcher(read(Path.of(
                "src/main/resources/souther/string.sou")));
        Set<String> out = new TreeSet<>();
        while (found.find()) {
            out.add(found.group(1));
        }
        return out;
    }

    private static Set<String> difference(Set<String> these, Set<String> those) {
        Set<String> out = new TreeSet<>(these);
        out.removeAll(those);
        return out;
    }

    private static String read(Path source) {
        try {
            return Files.readString(source, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Every class of this compiler, loaded for what it declares.
     *
     * <p>Not initialised: what is read here is the fields a type holds, and running its static
     * initialisers would make this test depend on what they touch.
     */
    private static List<Class<?>> compiled() {
        List<Class<?>> found = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            Path root = Path.of(entry);
            if (!entry.contains("souther-") || !Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(each -> each.toString().endsWith(".class"))
                        .map(each -> root.relativize(each).toString())
                        .forEach(name -> load(name, found));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return found;
    }

    private static void load(String path, List<Class<?>> into) {
        String named = path.substring(0, path.length() - ".class".length())
                .replace(File.separatorChar, '.').replace('/', '.');
        if (!named.startsWith("souther.") || named.endsWith("package-info")
                || named.endsWith("module-info")) {
            return;
        }
        try {
            into.add(Class.forName(named, false,
                    WhatDecidesWhetherAValueExistsHoldsBothLanguagesTest.class.getClassLoader()));
        } catch (ClassNotFoundException | LinkageError _) {
            // a class this test's path cannot resolve says nothing about the rule
        }
    }
}
