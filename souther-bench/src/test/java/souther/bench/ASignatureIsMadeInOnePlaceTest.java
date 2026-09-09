package souther.bench;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
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
     * Every method that makes a part of a signature, and how many it makes.
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
    private static final Map<String, Integer> MADE_BY = new LinkedHashMap<>(Map.ofEntries(
            Map.entry("souther.compiler.check.SignatureBoundary#of", 2),
            Map.entry("souther.compiler.check.SignatureBoundary#input", 1),
            Map.entry("souther.compiler.check.SignatureBoundary#output", 2),
            Map.entry("souther.compiler.check.DeclaredSig#<init>", 1),
            Map.entry("souther.compiler.check.PipelineSigs#pipeSig", 1),
            Map.entry("souther.compiler.check.CrossingNominal#admitted", 1),
            Map.entry("souther.compiler.check.CrossingMapKey#lexical", 1),
            Map.entry("souther.compiler.check.CrossingMapKey#named", 1)));

    /**
     * Every method that reaches the walk, and what it reaches.
     *
     * <p>Beside the makers because a caller of the walk is the other way a second answer is built:
     * the values would each be admitted, and there would be two of them. A declaration is admitted
     * from the one facade the query calls, a composition's answer where the composition is walked,
     * and the query that owns the answers is what calls either.
     */
    private static final Map<String, String> REACHED_BY = new LinkedHashMap<>(Map.of(
            "souther.compiler.check.SignatureBoundary#of",
            "souther.compiler.check.SignatureDeclarations#of",
            "souther.compiler.check.SignatureBoundary#composedOutput",
            "souther.compiler.check.PipelineSigs#pipeSig",
            "souther.compiler.check.SignatureDeclarations#of",
            "souther.compiler.query.Bodies$DeclaredSignatures#compute",
            "souther.compiler.check.PipelineSigs#signatures",
            "souther.compiler.query.Bodies$Reachable#compute"));

    @Test
    void everyPartOfASignatureIsMadeWhereSomethingAdmittedIt() throws Exception {
        Set<String> witnesses = witnesses();
        assertFalse(witnesses.isEmpty(), "a signature is made of nothing — the walk of it missed");

        Map<String, Integer> made = new TreeMap<>();
        for (Compiled.Site site : Compiled.sites()) {
            for (String witness : witnesses) {
                if (site.makesA(witness)) {
                    made.merge(method(site), 1, Integer::sum);
                }
            }
        }
        assertEquals(new TreeMap<>(MADE_BY), made,
                () -> "what makes a part of a signature, of " + witnesses + ": " + made);
    }

    @Test
    void nothingElseReachesTheWalkThatAdmits() throws Exception {
        Map<String, Set<String>> reached = new TreeMap<>();
        for (Compiled.Site site : Compiled.sites()) {
            String called = site.owner() + "#" + site.member();
            if (REACHED_BY.containsKey(called) && !method(site).equals(called)) {
                reached.computeIfAbsent(called, _ -> new TreeSet<>()).add(method(site));
            }
        }
        Map<String, Set<String>> expected = new TreeMap<>();
        REACHED_BY.forEach((what, by) -> expected.put(what, new TreeSet<>(Set.of(by))));
        assertEquals(expected, reached, () -> "what reaches the walk that admits: " + reached);
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
