package souther.bench;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A signature is what admits what its boundary carries, and every part of one is made where the
 * admitting happens.
 *
 * <p>What a closed constructor holds is the package: nothing outside {@code check} can assemble a
 * signature, or a shape inside one, out of what no walk admitted. Inside it nothing says who may,
 * and a witness is worth what it is only while its makers are the walks. Two of them would each
 * build a correct value and the phases below would read one while the check read the other — the
 * boundary's question answered twice, which is what carrying the answer was for. Nothing an ordinary
 * test can observe would change: the two walks are the same walk, so their values agree, and they
 * agree until the day the trees they are given stop being the same tree.
 *
 * <p>Asked of the bytecode through {@link Compiled}, because making a value is three things and not
 * one: a {@code new}, a constructor reference that runs somewhere else, and — for the walk itself —
 * a call. A rule written over the text of a {@code new} is passed by {@code Input::new}, and the
 * text is also where an import, a line break and a qualified name each read past a pattern written
 * for one spelling.
 *
 * <p>The population is what a signature is made of rather than a list kept here: the types
 * {@code Sig} and {@code DeclaredSig} can hold, down through the cases of every sum among them,
 * keeping the ones whose constructors are closed. A part added to the boundary vocabulary is
 * therefore counted the day it is written, and it fails this until somebody says where it is made.
 */
class ASignatureIsMadeInOnePlaceTest {

    /** What a declaration is admitted as, and what crosses a boundary. Everything below is what
     *  these two can hold. */
    private static final List<String> ROOTS = List.of(
            "souther.compiler.check.DeclaredSig",
            "souther.compiler.check.Sig");

    /** Where the boundary vocabulary is kept: a witness written elsewhere is not one of these. */
    private static final String CHECK = "souther.compiler.check.";

    /**
     * Each part of a signature, and where it is made how often.
     *
     * <p>Keyed by the part rather than by the maker, so that the population is what is compared: a
     * part nothing makes is one this answers an emptiness for, and a part written with no line here
     * fails whether or not anything makes one yet. Keyed the other way, a case added to the boundary
     * and left unbuilt would be a row on neither side.
     *
     * <p>One entry per place a walk admits something. {@code SignatureBoundary} is the walk: it
     * takes a written declaration apart and puts back what each position admits, so every closed
     * shape is made there, and the declaration that carries them is made there once. The projection
     * to what crosses is the declaration's own, made in its constructor out of the inputs it already
     * holds; a composition's is made where the composition is worked out, out of the stage's shapes
     * and the answer that walk admitted. The two names — a name a model declared, and a key a map is
     * written with — are made by the rule that admits a name, which is asked at every position that
     * crosses.
     *
     * <p>A position makes fewer of these than it has cases, and that is the decision rather than an
     * omission. A list, a set, a map and a scalar describe a shape and name nothing, so they are
     * records anyone may write; what is closed is a case that holds a name, because holding one is
     * the claim that the name was admitted. So an input contributes its nominal case and an output
     * that case and its union of them.
     */
    private static final Map<String, Map<String, Integer>> MADE_BY = Map.of(
            "souther.compiler.check.Sig",
            Map.of("souther.compiler.check.DeclaredSig#<init>", 1,
                    "souther.compiler.check.PipelineSigs#pipeSig", 1),
            "souther.compiler.check.DeclaredSig",
            Map.of("souther.compiler.check.SignatureBoundary#of", 1),
            "souther.compiler.check.DeclaredSig$Input",
            Map.of("souther.compiler.check.SignatureBoundary#of", 1),
            "souther.compiler.check.BoundaryInput$Nominal",
            Map.of("souther.compiler.check.SignatureBoundary#input", 1),
            "souther.compiler.check.BoundaryOutput$Nominal",
            Map.of("souther.compiler.check.SignatureBoundary#output", 1),
            "souther.compiler.check.BoundaryOutput$Cases",
            Map.of("souther.compiler.check.SignatureBoundary#output", 1),
            "souther.compiler.check.CrossingNominal",
            Map.of("souther.compiler.check.CrossingNominal#admitted", 1),
            "souther.compiler.check.CrossingMapKey",
            Map.of("souther.compiler.check.CrossingMapKey#lexical", 1,
                    "souther.compiler.check.CrossingMapKey#named", 1));

    /**
     * The entry points the query owns rather than the walk, and why each is one.
     *
     * <p>The walk's own are asked of {@code SignatureBoundary} — a method of it that is not private
     * is a way in, whoever wrote it — and these two are not its methods. A declaration is admitted
     * through one facade, and the map of them is worked out by one query; the signatures a module
     * can name are worked out by another. Both are a whole module's answer rather than one
     * declaration's, so a second caller would be a second table and not a second shape, which is the
     * same fault a rung further up.
     */
    private static final Set<String> OWNED_BY_A_QUERY = Set.of(
            "souther.compiler.check.SignatureDeclarations#of",
            "souther.compiler.check.PipelineSigs#signatures");

    /**
     * Every way in, and what reaches it how often.
     *
     * <p>Beside the makers because a caller of the walk is the other way a second answer is built:
     * the values would each be admitted, and there would be two of them. Counted rather than
     * gathered, because a method that reaches one of these twice has admitted the same declaration
     * twice — which is the second walk, written inside one method instead of two.
     */
    private static final Map<String, Map<String, Integer>> REACHED_BY = Map.of(
            "souther.compiler.check.SignatureBoundary#of",
            Map.of("souther.compiler.check.SignatureDeclarations#of", 1),
            "souther.compiler.check.SignatureBoundary#composedOutput",
            Map.of("souther.compiler.check.PipelineSigs#pipeSig", 1),
            "souther.compiler.check.SignatureDeclarations#of",
            Map.of("souther.compiler.query.Bodies$DeclaredSignatures#compute", 1),
            "souther.compiler.check.PipelineSigs#signatures",
            Map.of("souther.compiler.query.Bodies$Reachable#compute", 1));

    @Test
    void everyPartOfASignatureIsMadeWhereSomethingAdmittedIt() throws Exception {
        Set<String> witnesses = witnesses();
        assertFalse(witnesses.isEmpty(), "a signature is made of nothing — the walk of it missed");

        Map<String, Map<String, Integer>> made = eachOf(witnesses);
        for (Compiled.Site site : Compiled.sites()) {
            for (String witness : witnesses) {
                if (site.makesA(witness)) {
                    made.get(witness).merge(method(site), 1, Integer::sum);
                }
            }
        }
        assertEquals(sorted(MADE_BY), made,
                "what makes each part of a signature — a part with nothing under it is one nothing"
                        + " makes, which is a part no walk admits");
    }

    @Test
    void nothingElseReachesTheWalkThatAdmits() throws Exception {
        Set<String> ways = new TreeSet<>(waysIntoTheWalk());
        ways.addAll(OWNED_BY_A_QUERY);
        assertEquals(ways, new TreeSet<>(REACHED_BY.keySet()),
                "every way into the walk says who may take it");

        Map<String, Map<String, Integer>> reached = eachOf(ways);
        for (Compiled.Site site : Compiled.sites()) {
            String called = site.owner() + "#" + site.member();
            if (reached.containsKey(called)) {
                reached.get(called).merge(method(site), 1, Integer::sum);
            }
        }
        assertEquals(sorted(REACHED_BY), reached, "what reaches the walk that admits, and how often");
    }

    /** The ways into the walk: a method of it that something outside it could call. */
    private static Set<String> waysIntoTheWalk() throws ClassNotFoundException {
        Set<String> ways = new TreeSet<>();
        Class<?> walk = Class.forName("souther.compiler.check.SignatureBoundary");
        for (var each : walk.getDeclaredMethods()) {
            if (!Modifier.isPrivate(each.getModifiers()) && !each.isSynthetic()) {
                ways.add(walk.getName() + "#" + each.getName());
            }
        }
        return ways;
    }

    /** Each of {@code subjects} with nothing found for it yet, so that one nothing is found for
     *  answers with an emptiness rather than by being absent from the comparison. */
    private static Map<String, Map<String, Integer>> eachOf(Set<String> subjects) {
        Map<String, Map<String, Integer>> out = new TreeMap<>();
        subjects.forEach(each -> out.put(each, new TreeMap<>()));
        return out;
    }

    /** The written-down answer in the order the found one is read in. */
    private static Map<String, Map<String, Integer>> sorted(Map<String, Map<String, Integer>> of) {
        Map<String, Map<String, Integer>> out = new TreeMap<>();
        of.forEach((subject, sites) -> out.put(subject, new TreeMap<>(sites)));
        return out;
    }

    /** The method a site is in, without the parameters: what a rule here names is the method, and
     *  an overload of one of these would be a second maker whichever way it is spelled. */
    private static String method(Compiled.Site site) {
        return site.from() + "#" + site.method();
    }

    /**
     * The parts of a signature whose constructors are closed.
     *
     * <p>Walked from what a signature is: a field's type, what a collection of them holds, and every
     * case of a sum among them. A part with a public constructor is not here — anyone may describe a
     * shape, and what is closed is raising one to something the compiler stands behind.
     */
    private static Set<String> witnesses() throws ClassNotFoundException {
        Set<Class<?>> seen = new LinkedHashSet<>();
        Set<String> closed = new TreeSet<>();
        Deque<Class<?>> todo = new ArrayDeque<>();
        for (String root : ROOTS) {
            todo.add(Class.forName(root));
        }
        while (!todo.isEmpty()) {
            Class<?> each = todo.poll();
            if (!each.getName().startsWith(CHECK) || !seen.add(each)) {
                continue;
            }
            if (isClosed(each)) {
                closed.add(each.getName());
            }
            for (Class<?> permitted : each.isSealed()
                    ? each.getPermittedSubclasses() : new Class<?>[0]) {
                todo.add(permitted);
            }
            for (Field field : each.getDeclaredFields()) {
                held(field.getGenericType(), todo);
            }
        }
        return closed;
    }

    /** Whether the only way to make one is from inside the package that admits it. */
    private static boolean isClosed(Class<?> type) {
        var constructors = type.getDeclaredConstructors();
        return constructors.length > 0 && List.of(constructors).stream()
                .noneMatch(each -> Modifier.isPublic(each.getModifiers())
                        || Modifier.isProtected(each.getModifiers()));
    }

    /** What a field can hold: the type it is written as, and what a generic one is written over. */
    private static void held(Type type, Deque<Class<?>> todo) {
        switch (type) {
            case Class<?> named -> todo.add(named);
            case ParameterizedType generic -> {
                held(generic.getRawType(), todo);
                for (Type argument : generic.getActualTypeArguments()) {
                    held(argument, todo);
                }
            }
            default -> { }
        }
    }
}
