package souther.compiler.check;

import souther.compiler.DefaultStdlib;
import org.junit.jupiter.api.Test;
import souther.compiler.ast.Ast;
import souther.compiler.ast.Hir;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.EveryShippedMessageCatalogIsCompleteAndValidTest;
import souther.compiler.frontend.CstFrontend;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A module in one of these states got there by having the thing the state says happen to it.
 *
 * <p>{@link Expandable} and {@link InvariantSettled} are two claims about a module, and they used to
 * be one type with the tree: whichever question you asked, what came back said only that resolution
 * had been over it. A reader that wanted an expandable module and one that wanted a settled one were
 * handed the same value, and what told them apart was which query they happened to ask.
 *
 * <p>So the propositions here are about the ways in. Each state is reached by a step that performs
 * what the state claims — the cycle check, the settling — and there is no other way to reach it.
 * What that forbids is the shape this replaces: a carrier with an operation that takes a payload and
 * hands the claim back, which is a way to assert anything about anything.
 *
 * <p>That a tree is what the first of them is handed is not a hole. {@code check} answers about the
 * tree it is given, so a rewritten module is checked again rather than inheriting an answer given
 * about the tree it was rewritten from. A state saying "resolution produced this" would be a
 * record of where a value came from, and {@link Hir} already says the one thing such a state could
 * claim: no occurrence of it is one nothing has read.
 *
 * <p>The second half is about what a state hands out. Closing the ways in leaves the other end open:
 * a rung can hold every claim below it and still answer a route with a node, and then the claim is
 * gone with nothing saying so. That is what happened between {@code Derived} and {@code Prepared}
 * (#714) — the answers were per declaration and per definition, the assemblies poured them into a
 * tree, and above that there was nothing but nodes to hand over.
 *
 * <p>So the states hold their parts and project a tree, rather than holding a tree and being asked
 * for parts of it. Three propositions keep it that way, and each reads the production types rather
 * than a description of them kept here: the claim about a part is still made one rung up, the way
 * that proves a claim again refuses a value it is not true of, and no reader in the compiler takes
 * the projection where it wanted a part.
 */
class AStateIsReachedOnlyThroughWhatEstablishesItTest {

    /**
     * The states, worked out from the topmost one rather than listed.
     *
     * <p>A state is a final class of this package that cannot be built from outside it, and the
     * family is what {@link Prepared} and {@link Expandable} reach through their fields and through
     * what their routes answer with. Listing them would be the thing these propositions are about,
     * written a second time: a rung added tomorrow and left off the list would be a rung nothing
     * here says anything about, and the list would still be green.
     */
    private static final List<Class<?>> STATES = states();

    private static List<Class<?>> states() {
        List<Class<?>> found = new ArrayList<>();
        Set<Class<?>> walked = new LinkedHashSet<>();
        Deque<Class<?>> pending = new ArrayDeque<>(List.of(Prepared.class, Expandable.class));
        while (!pending.isEmpty()) {
            Class<?> one = pending.poll();
            // An assembly is walked through and not counted: it holds states and is not one, which
            // is a thing the class says of itself ({@link Assembly}) rather than a thing this
            // reading works out from its shape. Skipping it whole would take the states it holds
            // out of the family with it.
            boolean assembly = Assembly.class.isAssignableFrom(one);
            if (!(assembly || isState(one)) || !walked.add(one)) {
                continue;
            }
            if (!assembly) {
                found.add(one);
            }
            for (Field f : one.getDeclaredFields()) {
                mentioned(f.getGenericType(), pending);
            }
            for (Method m : one.getDeclaredMethods()) {
                mentioned(m.getGenericReturnType(), pending);
            }
            pending.addAll(List.of(one.getDeclaredClasses()));
        }
        found.sort(Comparator.comparing(Class::getName));
        return List.copyOf(found);
    }

    /**
     * An assembly is not among the states, and what it holds still is.
     *
     * <p>Held because of what the reading below it is worth. A state is found here by its shape — a
     * final class of this package nobody outside can build — and an assembly is written that way
     * too, so without the distinction the family would carry a value that claims nothing and a rule
     * about states failing on it would say neither which. Walked through all the same: the states an
     * assembly holds are states wherever they are reached from.
     */
    @Test
    void anAssemblyIsNotAStateAndTheStatesItHoldsAre() {
        assertFalse(STATES.contains(CheckSurface.class),
                "an assembly claims nothing, so what a state is held to is not asked of it");
        assertTrue(STATES.contains(Desugared.Fn.class),
                "and a state it holds is one, or the assembly took it out of the family");
    }

    /**
     * Arrived at rather than built: a type of this package with no way in from outside but the
     * operations these propositions are about.
     *
     * <p>Two shapes qualify, and for one reason. A final class with no public constructor is one,
     * and so is a sealed interface every case of which is — a state that says which kind of thing it
     * is has to be several classes, and closing the set is what makes "no way in but the operation"
     * true of all of them at once. Read as a conjunction over the cases rather than asserted of the
     * interface: a case that could be built from outside would be a way into the state, and the
     * interface being sealed would not say so.
     */
    private static boolean isState(Class<?> c) {
        if (c == null || !"souther.compiler.check".equals(c.getPackageName())) {
            return false;
        }
        if (c.isSealed() && c.isInterface()) {
            for (Class<?> permitted : c.getPermittedSubclasses()) {
                if (!isState(permitted)) {
                    return false;
                }
            }
            return c.getPermittedSubclasses().length > 0;
        }
        return Modifier.isFinal(c.getModifiers())
                && c.getConstructors().length == 0
                && !Modifier.isAbstract(c.getModifiers());
    }

    private static void mentioned(java.lang.reflect.Type t, Deque<Class<?>> into) {
        if (t instanceof Class<?> c) {
            into.add(c);
        }
        if (t instanceof java.lang.reflect.ParameterizedType p) {
            mentioned(p.getRawType(), into);
            for (java.lang.reflect.Type a : p.getActualTypeArguments()) {
                mentioned(a, into);
            }
        }
    }

    private static Hir.Module resolved(String source) {
        Ast.Module parsed = CstFrontend.parse(source);
        return Resolve.resolving(parsed, SyntaxSymbols.of(parsed, DefaultStdlib.get())).module();
    }

    private static String signature(Method m) {
        StringBuilder sb = new StringBuilder(m.getName()).append('(');
        for (int i = 0; i < m.getParameterTypes().length; i++) {
            sb.append(i == 0 ? "" : ", ").append(m.getParameterTypes()[i].getSimpleName());
        }
        return sb.append(')').toString();
    }

    /** Nothing outside the pass that establishes a state can put one together. */
    @Test
    void noStateHasAPublicConstructor() {
        for (Class<?> state : STATES) {
            assertEquals(0, state.getConstructors().length,
                    state.getSimpleName() + " is arrived at, not built");
        }
    }

    /**
     * The ways in, named. Each one performs what the state it answers with claims — the cycle check,
     * the settling — and that is the requirement rather than which value it is handed: a tree is a
     * fine thing to be handed by an operation that goes on to check it, and a state that took only
     * the state below would be recording where a value came from instead.
     *
     * <p>One that appears later is a way of reaching a state that someone was given, and it fails
     * here until it is either narrowed or written down as these are.
     */
    @Test
    void everyWayIntoAStateIsTheOperationThatEstablishesIt() {
        assertEquals(Set.of("check(Module, Map, Stdlib)"), waysInto(Expandable.class));
        assertEquals(Set.of("settle(Expandable, Symbols, DeclarationKinds, Map)"), waysInto(InvariantSettled.class));
        assertEquals(Set.of(), waysInto(InvariantSettled.Def.class),
                "a settled declaration is projected from the module it is one of");
        assertEquals(Set.of("of(Def, ResolvedSymbols)", "ofLanguage(Def)"),
                waysInto(Normalized.Def.class),
                "the second is for what the language declares, whose clauses hold no construction "
                        + "left to write as one. Every kind of declaration goes through it, a "
                        + "product among them: what a product needs derived is a representation, "
                        + "which is the rung below and not this one");
        assertEquals(
                Set.of("derive(Def, ResolvedSymbols, DeclarationKinds, PublishedDeclarations)",
                        "ofLanguage(Def)"),
                waysInto(Derived.Def.class),
                "the second is for what the language declares, where there is no representation to "
                        + "derive: a sum's is worked out where it is read and a unit has none, so "
                        + "the proposition holds of the declaration as it stands. It refuses a "
                        + "product, which would need one derived like any other");
        assertEquals(Set.of("assemble(InvariantSettled, Map)"), waysInto(Derived.Module.class));
        assertEquals(Set.of("desugar(FnDef, Symbols)", "reestablish(FnDef, Symbols)"),
                waysInto(Desugared.Fn.class),
                "the second is for a rung that rewrote a definition this state already held: it "
                        + "proves the proposition again of what came out, and refuses where the "
                        + "rewrite would have changed it");
        assertEquals(Set.of("assemble(Module, Map)"), waysInto(Desugared.Module.class));
        assertEquals(Set.of("prepare(Module, CheckSurface)"), waysInto(Prepared.class),
                "the assembly and the witness that every declaration and definition came out. The "
                        + "parts are joined once, where a best-effort reading of the module is "
                        + "given them too, and this is that assembly beside the claim it does not "
                        + "make");
        assertEquals(Set.of(), waysInto(Prepared.Example.class),
                "an example block is projected from the module it is one of");
        assertEquals(Set.of("classify(Module)", "namesWrittenOut(FakeTables, String)"),
                waysInto(FakeTables.class),
                "what a module's fake blocks declare, made from the resolved module; the second is "
                        + "for a rung that wrote the names in them out, and it carries the "
                        + "classification across rather than making it again of what the rewrite "
                        + "produced");
        assertEquals(Set.of(), waysInto(Prepared.ForExamples.class),
                "a run is asked for of the module, which is what pairs the blocks with the "
                        + "artifact");
    }

    /**
     * And the way that proves a proposition again refuses where proving it would have changed the
     * value.
     *
     * <p>The positive control for {@code prepare} calling it. Today the desugaring of an already
     * desugared definition answers with the same definition every time — 21,206 of them over a
     * compile of the suite — so the refusal never fires there, and a check that never fires is one
     * nothing has shown to be about anything. Handed a definition nothing desugared, it fires.
     */
    @Test
    void reestablishingRefusesADefinitionTheClaimIsNotTrueOf() {
        Hir.Module resolved = resolved("""
                module m exposing ( Amount, go )

                data Amount = Int

                behavior go : (n: Int) -> Amount
                    constructs Amount
                let go (n) = Amount(n)
                """);
        ResolvedSymbols scope = TypeChecker.symbols(resolved, DefaultStdlib.get());
        Hir.FnDef written = resolved.fns().stream()
                .filter(f -> f.name().equals("go")).findFirst().orElseThrow();

        assertEquals(1, applications(bodyOf(written)),
                "the construction is written as an application until the desugaring runs");
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Desugared.Fn.reestablish(written, scope));
        assertTrue(refused.getMessage().contains("go"), refused.getMessage());

        assertEquals(written.name(),
                Desugared.Fn.reestablish(Desugared.Fn.desugar(written, scope).read(), scope).name(),
                "and answers for one the claim is true of");
    }

    /**
     * The bodies a check produced are minted where the check is, and nowhere a caller could reach.
     *
     * <p>It is not a state of the module and does not belong to this family: whether the check
     * established anything is the answer being present, which is what {@code Answer} says already.
     * What is here is the other half — the value that only a check which did establish it produces —
     * and the conjunction that makes it true is evaluated in the query that answers with it.
     */
    @Test
    void whatACheckProducedIsMintedWhereTheCheckIs() {
        Class<?> elaborated = souther.compiler.query.Bodies.Elaborated.class;

        assertEquals(0, elaborated.getConstructors().length,
                "a caller that could build one would be a caller the conjunction does not hold for");
        for (Method m : elaborated.getMethods()) {
            assertFalse(Modifier.isStatic(m.getModifiers())
                            && m.getReturnType() == elaborated,
                    "Bodies.Elaborated." + signature(m) + " is a way in that is not the check");
        }
    }

    /**
     * What a module prepares is not asked per output file.
     *
     * <p>The classes are emitted once per module, and every example row attached to it runs against
     * them, so the helpers its artifact must carry are gathered over all of those rows. Which rows
     * are reported on is asked later, where the source file is in the key. A state taking one would
     * pull an output's concern into what the module is — and would make every reader of it, the
     * checks and the adequacy report among them, answer per file.
     */
    @Test
    void whatAModulePreparesDoesNotDependOnAnOutputFile() {
        for (Method m : Prepared.class.getMethods()) {
            boolean takesAName = false;
            for (Class<?> takes : m.getParameterTypes()) {
                takesAName |= takes == String.class;
            }
            if (!takesAName) {
                continue;
            }
            // A run over one source's rows may be asked for, and is not what the module prepared:
            // which rows are reported on is `Output.Examples`' question and its key carries the
            // source. What must not take one is the way in, or a route to a part — either would be
            // a module whose contents are a function of which file is being reported on.
            assertEquals(Prepared.ForExamples.class, m.getReturnType(),
                    "Prepared." + signature(m) + " takes a name, and the only one it could be "
                            + "is an output's");
        }
    }

    /**
     * A claim made about a part of a module is still being made about it one rung up.
     *
     * <p>This is the proposition #714 was: {@code Derived} answered per declaration, the assembly
     * poured the answers into an {@link Hir.Module}, and from {@code Prepared} there was nothing
     * left to hand over but nodes. Nothing said so.
     *
     * <p>About the routes a state has, and not about the ones it has not. A state that answers a part
     * is a state saying something about that part, and what it says may not be less than what was
     * said below it. One with no route for a part is not answering about it at all — an example run
     * hands over no declarations, and a reader that wants them asks the module or the declaration
     * world, neither of which this is. What stops that becoming a way out is the other side of it:
     * a reader that goes to {@code tree()} for the part instead is what
     * {@link #noReaderInTheCompilerTakesAStatesPayloadInsteadOfItsParts} refuses.
     *
     * <p>Read off the production types and nothing else. What the parts are is what {@link Hir.Module}
     * is made of; which rung is below which is which state a state holds; and what a rung says about
     * a part is what its route for that part answers with. A table here saying which rung claims what
     * would be the same knowledge written twice, and the copy is what goes stale.
     */
    @Test
    void aClaimAboutAPartIsStillMadeAboutItOneRungUp() {
        List<String> lost = new ArrayList<>();
        for (Class<?> above : STATES) {
            for (Class<?> below : rungsBelow(above)) {
                for (String part : parts()) {
                    Set<Class<?>> here = answeredBy(above, part);
                    if (here.isEmpty()) {
                        continue;
                    }
                    for (Class<?> said : answeredBy(below, part)) {
                        if (!STATES.contains(said) || keeps(here, said)) {
                            continue;
                        }
                        lost.add(named(above) + "." + part + "() answers "
                                + answered(above, part) + ", where "
                                + named(below) + "." + part + "() answers " + named(said));
                    }
                }
            }
        }
        assertEquals(List.of(), lost,
                "a rung below said something about that part, and this one hands over a value that "
                        + "no longer carries it");
    }

    /** Whether one of the answers here says everything {@code said} said. */
    private static boolean keeps(Set<Class<?>> here, Class<?> said) {
        for (Class<?> answered : here) {
            if (STATES.contains(answered) && saysAtLeastWhat(answered, said)) {
                return true;
            }
        }
        return false;
    }

    /** What a module is made of, which is what there is to say something about. */
    private static Set<String> parts() {
        Set<String> named = new LinkedHashSet<>();
        for (java.lang.reflect.RecordComponent c : Hir.Module.class.getRecordComponents()) {
            if (!elementsOf(c.getGenericType()).isEmpty()) {
                named.add(c.getName());
            }
        }
        return named;
    }

    /** The states this one holds, and the ones those hold: the pipeline, read off the fields. */
    private static Set<Class<?>> rungsBelow(Class<?> state) {
        Set<Class<?>> below = new LinkedHashSet<>();
        Deque<Class<?>> pending = new ArrayDeque<>(List.of(state));
        while (!pending.isEmpty()) {
            for (Field f : pending.poll().getDeclaredFields()) {
                Deque<Class<?>> mentioned = new ArrayDeque<>();
                mentioned(f.getGenericType(), mentioned);
                for (Class<?> one : mentioned) {
                    if (STATES.contains(one) && below.add(one)) {
                        pending.add(one);
                    }
                }
            }
        }
        below.remove(state);
        return below;
    }

    private static boolean answersWithAState(Class<?> state, String part) {
        for (Class<?> answered : answeredBy(state, part)) {
            if (STATES.contains(answered)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Which state says at least as much as which, read off the operations that establish them.
     *
     * <p>A way into one that is handed another is what orders the two: {@code Derived.Def} is
     * reached by deriving an {@code InvariantSettled.Def}, so it says what that one says and the
     * thing derivation established. Asking only whether a route answers <em>a</em> state would let
     * the parts go back down the ladder — every rung answering the settled declaration keeps
     * something at each step and loses what deriving it established, and nothing would say so.
     *
     * <p>The order is the same material {@link #waysInto} reads, so there is no second description
     * of the ladder here to fall out of step with the first.
     */
    private static final Map<Class<?>, Set<Class<?>>> SAYS_AT_LEAST = strengths();

    private static Map<Class<?>, Set<Class<?>>> strengths() {
        Map<Class<?>, Set<Class<?>>> under = new LinkedHashMap<>();
        for (Class<?> state : STATES) {
            under.put(state, new LinkedHashSet<>());
        }
        for (Class<?> declaring : STATES) {
            for (Method m : declaring.getDeclaredMethods()) {
                if (!Modifier.isStatic(m.getModifiers()) || !STATES.contains(m.getReturnType())) {
                    continue;
                }
                for (Class<?> takes : m.getParameterTypes()) {
                    if (STATES.contains(takes)) {
                        under.get(m.getReturnType()).add(takes);
                    }
                }
            }
        }
        for (boolean grew = true; grew; ) {
            grew = false;
            for (Class<?> state : STATES) {
                Set<Class<?>> reached = new LinkedHashSet<>();
                for (Class<?> under1 : under.get(state)) {
                    reached.addAll(under.get(under1));
                }
                grew |= under.get(state).addAll(reached);
            }
        }
        return Map.copyOf(under);
    }

    /** Whether {@code above} says everything {@code below} says — the same state, or one reached by
     *  an operation that was handed it. */
    private static boolean saysAtLeastWhat(Class<?> above, Class<?> below) {
        return above == below || SAYS_AT_LEAST.getOrDefault(above, Set.of()).contains(below);
    }

    private static String answered(Class<?> state, String part) {
        Set<Class<?>> answers = answeredBy(state, part);
        return answers.isEmpty() ? "nothing — there is no route for it"
                : named(answers.iterator().next());
    }

    /** {@code Derived.Module} rather than {@code Module}: the states are nested and their simple
     *  names collide with each other and with the nodes they stand for. */
    private static String named(Class<?> c) {
        String binary = c.getName();
        return binary.substring(binary.lastIndexOf('.') + 1).replace('$', '.');
    }

    /** What the route named for {@code part} answers with, or nothing where the state has none. */
    private static Set<Class<?>> answeredBy(Class<?> state, String part) {
        for (Method m : state.getDeclaredMethods()) {
            if (Modifier.isPublic(m.getModifiers()) && !Modifier.isStatic(m.getModifiers())
                    && m.getParameterCount() == 0 && m.getName().equals(part)) {
                return elementsOf(m.getGenericReturnType());
            }
        }
        return Set.of();
    }

    /** The types a list-shaped part is a list of — the node it holds, or the state that stands for
     *  one. A route answering something that is neither says nothing about the part. */
    private static Set<Class<?>> elementsOf(java.lang.reflect.Type t) {
        Set<Class<?>> out = new LinkedHashSet<>();
        if (!(t instanceof java.lang.reflect.ParameterizedType p)
                || p.getRawType() != List.class) {
            return out;
        }
        Deque<Class<?>> mentioned = new ArrayDeque<>();
        for (java.lang.reflect.Type a : p.getActualTypeArguments()) {
            mentioned(a, mentioned);
        }
        for (Class<?> one : mentioned) {
            if (STATES.contains(one) || Hir.class.isAssignableFrom(one)) {
                out.add(one);
            }
        }
        return out;
    }

    /**
     * A declaration's clauses get their spelling where they are settled, and nowhere above it.
     *
     * <p>This is what lets {@link Prepared} hand on the answers the rung below gave rather than
     * establishing anything of its own about the declarations: nothing between the settling and the
     * preparing puts a foreign name back into a clause, so there is nothing that could have made the
     * claim false. Measured — over a compile of the suite, qualifying the declarations of a prepared
     * module changed none of 12,963 of them — and held here rather than remembered, because a rewrite
     * added above the settling would make it false in silence.
     *
     * <p>With the control beside it. A rule that says a rewrite changes nothing is a rule that reads
     * the same whether or not the rewrite works at all, so the same call is made where it does have
     * something to do.
     */
    @Test
    void aPreparedModulesInvariantsAreAlreadyWrittenTheWayItReachesThem() {
        String lib = """
                module lib exposing ( atLeast )

                let atLeast (n: Int) : Bool = n >= 0
                """;
        String uses = """
                module shop exposing ( Amount )

                import lib ( atLeast )

                data Amount = Int
                    invariant atLeast(value)
                """;
        souther.compiler.query.Compilation compilation = souther.compiler.query.Compilation
                .ofSources(List.of(lib, uses), souther.compiler.meta.ModulePath.EMPTY);
        Hir.Module resolved = compilation.db()
                .ask(new souther.compiler.query.Names.Resolved("shop")).value();
        assertNotEquals(resolved, HelperNames.withQualifiedInvariants(resolved),
                "the clause names an imported definition bare, so there is something to write out");

        Prepared prepared = compilation.db()
                .ask(new souther.compiler.query.Shapes.Prepared("shop")).value();
        assertEquals(prepared.tree(), HelperNames.withQualifiedInvariants(prepared.tree()),
                "and by the time the module is prepared there is nothing left to write out");
    }

    /**
     * A definition that has been desugared says that and no more.
     *
     * <p>The helper parameter types it carries were settled before it, by a pass whose contract is
     * best effort: a parameter its body does not determine is left as it was, for the check below to
     * report. So a state claiming they are settled would be claiming something no pass established —
     * naming a pass that ran rather than a fact a reader may lean on. What is asserted here is the
     * shape of the claim: the way in takes a definition and the symbols, and asks nothing about what
     * the definition has been through.
     */
    @Test
    void desugaringClaimsNothingAboutTheTypesADefinitionCarries() {
        Hir.Module resolved = resolved("""
                module m exposing ( Wrapped, go )

                data Wrapped = Int

                behavior go : (n: Int) -> Wrapped
                    constructs Wrapped
                let go (n) = wrap(n)
                let wrap (n) = Wrapped(n)
                """);
        ResolvedSymbols scope = TypeChecker.symbols(resolved, DefaultStdlib.get());
        Hir.FnDef unsettled = resolved.fns().stream()
                .filter(f -> f.name().equals("wrap")).findFirst().orElseThrow();

        assertTrue(unsettled.params().get(0).type() == null,
                "the parameter is open, which is a definition this state admits");
        Desugared.Fn desugared = Desugared.Fn.desugar(unsettled, scope);
        assertEquals(0, applications(bodyOf(desugared.read())),
                "and its constructions are constructions all the same");
    }

    private static Hir.Expr bodyOf(Hir.FnDef fn) {
        return fn.body() instanceof Hir.FnBody.Written written ? written.expr() : null;
    }

    /**
     * Every route from a settled module to one of its declarations answers with a settled
     * declaration.
     *
     * <p>The constructor being closed is not enough on its own. A module handing out
     * {@link Hir.Def} would put the claim back where it was — carried by nobody — one level down,
     * and the reader that needs it is the measured one: the rewrite of what a declaration says does
     * different work depending on whether its clauses have been expanded, and says nothing about the
     * difference.
     */
    @Test
    void noRouteToADeclarationOfASettledModuleHandsOverANode() {
        for (Method m : InvariantSettled.class.getMethods()) {
            assertFalse(mentions(m.getGenericReturnType(), Hir.Def.class),
                    "InvariantSettled." + signature(m) + " hands over a declaration node");
        }
    }

    private static boolean mentions(java.lang.reflect.Type type, Class<?> named) {
        if (type == named) {
            return true;
        }
        if (type instanceof java.lang.reflect.ParameterizedType parameterized) {
            for (java.lang.reflect.Type argument : parameterized.getActualTypeArguments()) {
                if (mentions(argument, named)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * And deriving one is what writes the constructions in what it says as constructions — the
     * reading that made the declaration its own state.
     */
    @Test
    void theWayToADerivedDeclarationIsTheRewrite() {
        Hir.Module resolved = resolved("""
                module m exposing ( Amount, Wrapped )

                data Wrapped = Int
                data Amount = Int
                    invariant isOk(value)

                let isOk (n: Int) : Bool = Wrapped(n) == Wrapped(0)
                """);
        ResolvedSymbols scope = TypeChecker.symbols(resolved, DefaultStdlib.get());
        InvariantSettled settled =
                InvariantSettled.settle(Expandable.check(resolved, Map.of(), DefaultStdlib.get()),
                        scope, ScopedDeclarations.kindsOf(scope), Map.of());
        InvariantSettled.Def amount = settled.defs().stream()
                .filter(d -> d.name().equals("Amount")).findFirst().orElseThrow();

        assertEquals(2, applications(clauseOf(List.of(amount.def()), "Amount")),
                "the constructions are written as applications until this rewrites them");
        assertEquals(0, applications(clauseOf(
                        List.of(Normalized.Def.of(amount, scope).node()), "Amount")),
                "and none is left as one afterwards");
    }

    /**
     * And nothing the compiler does reads a state's payload instead of asking it.
     *
     * <p>{@code Prepared.tree}, {@code Derived.Module.tree} and {@code Desugared.Module.tree} are
     * public and stay so: a test auditing what a module carries at each stage is asking about the
     * tree, which is what those are for. What must not appear is a reader in the compiler, because
     * one that writes {@code prepared.tree().behaviors()} has thrown the claim away rather than
     * asked for the part — and the accessor beside it says nothing about which it did.
     *
     * <p>Held from the source and not from the API, because the accessor is not what is banned. The
     * failure is a production reader choosing the payload, and the moment to decide against it is
     * the change that adds one. Written as a call or handed on as a reference — {@code prepared::tree}
     * puts the same value in the same hands and spells neither parenthesis.
     */
    @Test
    void noReaderInTheCompilerTakesAStatesPayloadInsteadOfItsParts() throws IOException {
        List<String> reading = new ArrayList<>();
        for (Path source : EveryShippedMessageCatalogIsCompleteAndValidTest.mainSources()) {
            String text = Files.readString(source);
            if (text.contains(".tree()") || text.contains("::tree")) {
                reading.add(String.valueOf(source.getFileName()));
            }
        }
        assertEquals(List.of(), reading,
                "a state answers the part a reader wants; ask it for that rather than for the tree");
    }

    /**
     * And the projection under its other name is reachable from nowhere new.
     *
     * <p>{@code tree} is public and no reader in the compiler writes it. The states also project for
     * their own use, through a package-private accessor, and that one a class of this package could
     * call — {@code prepared.module().fns()} says exactly what {@code prepared.tree().fns()} is
     * banned for saying. Reading the source for it does not work: {@code module} is what
     * {@code CstFrontend.Parsed}, {@code Resolve.Resolution} and an import all call their own
     * accessor, so the spelling finds seven readers of other types and none of this one.
     *
     * <p>What can be read exactly is who is in a position to call it at all. Java has it down to this
     * package; within it, a class that can reach a projection is one that holds a state or is handed
     * one. The states themselves are governed by the routes they answer with; what is left is the
     * boundary where the module leaves the ladder, and there is one class it is in —
     * {@code CheckSurface}, which is handed the settling, keeps it as what it was joined over, and
     * projects a tree from it. Three entries and one place: taking it, holding it, and the field it
     * is held in. What is done with the payload afterwards drops no claim, because there was none on
     * it to drop.
     *
     * <p>It keeps the settling rather than the tree because of what it is compared against.
     * {@code Prepared} pairs this assembly with the witness that the module is whole, and the two
     * belong together when they were made from one settling — which a tree does not say, two
     * settlings that left different recursive calls standing having the same one.
     *
     * <p>Of the states that say something about a part, because those are the ones with something to
     * lose. {@code Expandable} answers whether a body of the module may be expanded and claims
     * nothing about what is in it, so a reader handed one and taking its tree has dropped no claim —
     * which is why the discharge representation is built from one and is not a second boundary.
     *
     * <p>A second one appearing here is a reader that could take the payload where it wanted a part,
     * and it fails until it is either given the part or written down as this one is.
     */
    @Test
    void theModuleLeavesTheLadderInOnePlace() throws IOException {
        List<String> handling = new ArrayList<>();
        for (Path source : EveryShippedMessageCatalogIsCompleteAndValidTest.mainSources()) {
            if (!source.toString().contains("/souther/compiler/check/")) {
                continue;   // Java has the accessor down to this package already
            }
            Deque<Class<?>> pending = new ArrayDeque<>();
            pending.add(classOf(source));
            while (!pending.isEmpty()) {
                Class<?> in = pending.poll();
                if (in == null || STATES.contains(in)) {
                    continue;   // a state projecting for its own use is what the routes are about
                }
                // A class written inside another one is a class of this package too, and holding a
                // state in a field of it reaches the projection exactly as the outer one would.
                pending.addAll(List.of(in.getDeclaredClasses()));
                for (java.lang.reflect.Executable e : taking(in)) {
                    // The generic parameters and not the erasures: a `List<Prepared>` is handed
                    // every state in it, and `List` is what the erasure says it was handed.
                    if (holdsAStateThatSpeaks(e.getGenericParameterTypes())) {
                        handling.add(named(in) + "." + signature(e));
                    }
                }
                for (Field f : in.getDeclaredFields()) {
                    if (holdsAStateThatSpeaks(f.getGenericType())) {
                        handling.add(named(in) + "." + f.getName());
                    }
                }
            }
        }
        assertEquals(List.of("CheckSurface.assemble(InvariantSettled, Map, Map, Symbols, Map,"
                                + " FakeTables)",
                        "CheckSurface.<init>(InvariantSettled, List, List, List, List, FakeTables,"
                                + " List, Map)",
                        "CheckSurface.settling"),
                handling,
                "a class here that is handed a state can reach its projection, and taking a part "
                        + "off that is the claim thrown away with nothing saying so");
    }

    /** Everything of {@code in} that can be handed a value. */
    private static List<java.lang.reflect.Executable> taking(Class<?> in) {
        List<java.lang.reflect.Executable> all = new ArrayList<>(List.of(in.getDeclaredMethods()));
        all.addAll(List.of(in.getDeclaredConstructors()));
        return all;
    }

    private static String signature(java.lang.reflect.Executable e) {
        StringBuilder sb = new StringBuilder(
                e instanceof java.lang.reflect.Constructor<?> ? "<init>" : e.getName()).append('(');
        for (int i = 0; i < e.getParameterTypes().length; i++) {
            sb.append(i == 0 ? "" : ", ").append(e.getParameterTypes()[i].getSimpleName());
        }
        return sb.append(')').toString();
    }

    /** Whether any of these types holds a state that says something about a part — wherever in the
     *  type it is written, since a state inside a collection is one the holder was handed. */
    private static boolean holdsAStateThatSpeaks(java.lang.reflect.Type... types) {
        Deque<Class<?>> found = new ArrayDeque<>();
        for (java.lang.reflect.Type t : types) {
            mentioned(t, found);
        }
        for (Class<?> one : found) {
            if (saysSomethingAboutAPart(one)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the state answers any part of the module with a state — whether, that is, its
     *  projection has a claim in it to lose. */
    private static boolean saysSomethingAboutAPart(Class<?> state) {
        if (!STATES.contains(state)) {
            return false;
        }
        for (String part : parts()) {
            if (answersWithAState(state, part)) {
                return true;
            }
        }
        return false;
    }

    private static Class<?> classOf(Path source) {
        String name = String.valueOf(source.getFileName());
        try {
            return Class.forName("souther.compiler.check."
                    + name.substring(0, name.length() - ".java".length()));
        } catch (ClassNotFoundException _) {
            return null;
        }
    }

    /**
     * And no state hands over the rung it was built from.
     *
     * <p>A state holds the one below it because that is what it was made from and what carries the
     * parts it did not touch. What it must not do is offer it: a reader given the lower rung reads
     * the parts as they were before this one rewrote them, and they are the same types, so nothing
     * says which of the two it got. {@code Prepared} holds the definitions it re-established beside
     * a {@code Desugared.Module} holding the ones it re-established them from, and only the first is
     * what the module is.
     *
     * <p>The rung it was built from, and not the parts it holds. A part is held as a list of states
     * and answering it is the whole point; what is asked here is about the single state a rung keeps
     * beside those, which is its provenance rather than a value on offer.
     */
    @Test
    void noStateHandsOverTheRungItWasBuiltFrom() {
        List<String> offered = new ArrayList<>();
        for (Class<?> state : STATES) {
            Set<Class<?>> from = builtFrom(state);
            for (Method m : state.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers())) {
                    continue;
                }
                Deque<Class<?>> answers = new ArrayDeque<>();
                mentioned(m.getGenericReturnType(), answers);
                for (Class<?> one : answers) {
                    if (from.contains(one)) {
                        offered.add(named(state) + "." + signature(m) + " answers " + named(one));
                    }
                }
            }
        }
        assertEquals(List.of(), offered,
                "the rung below is what this one was built from, not a rung a reader may pick");
    }

    /** The rungs this one was made from: the states it keeps one of, and the ones those keep. A
     *  state kept a list of is a part rather than a rung. */
    private static Set<Class<?>> builtFrom(Class<?> state) {
        Set<Class<?>> from = new LinkedHashSet<>();
        Deque<Class<?>> pending = new ArrayDeque<>(List.of(state));
        while (!pending.isEmpty()) {
            for (Field f : pending.poll().getDeclaredFields()) {
                if (STATES.contains(f.getType()) && from.add(f.getType())) {
                    pending.add(f.getType());
                }
            }
        }
        from.remove(state);
        return from;
    }

    /**
     * And an assembly is handed the answers for its own parts, which it checks rather than reads off
     * the key they arrived under.
     *
     * <p>The map is keyed by bare name, and a bare name is a name in some module: two modules writing
     * `Amount` write two declarations, and an answer for one of them put under `"Amount"` would
     * otherwise be built into the other. What comes out would say `module a` and hold `b.Amount`,
     * which is the proposition — every declaration this module writes has an answer — made of a
     * declaration this module does not write.
     *
     * <p>The query graph asks {@code Shapes.Derived} and {@code Shapes.DerivedDeclarations} with the
     * same module name, so nothing here goes wrong today. That is the reason to hold it: a claim that
     * stands because its callers are careful is a claim nobody is keeping.
     */
    @Test
    void anAssemblyRefusesAnAnswerForAnotherModulesDeclaration() {
        ResolvedSymbols scopeA = TypeChecker.symbols(resolvedA(), DefaultStdlib.get());
        InvariantSettled a = InvariantSettled.settle(
                Expandable.check(resolvedA(), Map.of(), DefaultStdlib.get()), scopeA,
                ScopedDeclarations.kindsOf(scopeA), Map.of());
        ResolvedSymbols scopeB = TypeChecker.symbols(resolvedB(), DefaultStdlib.get());
        InvariantSettled b = InvariantSettled.settle(
                Expandable.check(resolvedB(), Map.of(), DefaultStdlib.get()), scopeB,
                ScopedDeclarations.kindsOf(scopeB), Map.of());

        Derived.Def ofA = Derived.Def.derive(Normalized.Def.of(defNamed(a, "Amount"), scopeA),
                scopeA, ScopedDeclarations.kindsOf(scopeA), ScopedDeclarations.of(scopeA));
        Derived.Def ofB = Derived.Def.derive(Normalized.Def.of(defNamed(b, "Amount"), scopeB),
                scopeB, ScopedDeclarations.kindsOf(scopeB), ScopedDeclarations.of(scopeB));
        assertEquals("Amount", ofB.name(), "the same bare name, so the map key does not tell them apart");
        assertNotEquals(ofA.declaredKey(), ofB.declaredKey());

        assertEquals("a", Derived.Module.assemble(a, Map.of("Amount", ofA)).name());
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Derived.Module.assemble(a, Map.of("Amount", ofB)));
        assertTrue(refused.getMessage().contains("Amount"), refused.getMessage());
    }

    /** The same of the definitions, which a module carries several modules' of under names of one
     *  shape — so the key tells them apart even less there. */
    @Test
    void anAssemblyRefusesAnAnswerForAnotherModulesDefinition() {
        ResolvedSymbols scopeA = TypeChecker.symbols(resolvedA(), DefaultStdlib.get());
        Derived.Module a = Derived.Module.assemble(
                InvariantSettled.settle(Expandable.check(resolvedA(), Map.of(), DefaultStdlib.get()),
                        scopeA, ScopedDeclarations.kindsOf(scopeA), Map.of()),
                Map.of("Amount", Derived.Def.derive(Normalized.Def.of(defNamed(InvariantSettled.settle(
                        Expandable.check(resolvedA(), Map.of(), DefaultStdlib.get()), scopeA,
                        ScopedDeclarations.kindsOf(scopeA), Map.of()), "Amount"), scopeA),
                        scopeA, ScopedDeclarations.kindsOf(scopeA),
                        ScopedDeclarations.of(scopeA))));
        Hir.FnDef ofA = a.fns().get(0);
        Hir.FnDef ofB = new Hir.FnDef(ofA.written(), "b", ofA.params(), ofA.declaredReturn(),
                ofA.body(), ofA.modifiers(), ofA.pos());

        assertEquals(ofA.name(), ofB.name(), "the same bare name, so the map key does not tell them apart");
        assertEquals("a", Desugared.Module.assemble(a,
                Map.of(ofA.name(), Desugared.Fn.desugar(ofA, scopeA))).name());
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Desugared.Module.assemble(a,
                        Map.of(ofA.name(), Desugared.Fn.desugar(ofB, scopeA))));
        assertTrue(refused.getMessage().contains(ofA.name()), refused.getMessage());
    }

    private static Hir.Module resolvedA() {
        return resolved("""
                module a exposing ( Amount )

                data Amount = Int

                let ok (n: Int) : Bool = n > 0
                """);
    }

    private static Hir.Module resolvedB() {
        return resolved("""
                module b exposing ( Amount )

                data Amount = Int
                """);
    }

    private static InvariantSettled.Def defNamed(InvariantSettled settled, String name) {
        return settled.defs().stream().filter(d -> d.name().equals(name)).findFirst().orElseThrow();
    }

    private static int applications(Hir.Expr e) {
        if (e == null) {
            return 0;
        }
        int[] found = {e instanceof Hir.Apply ? 1 : 0};
        Hir.forEachChild(e, c -> found[0] += applications(c));
        return found[0];
    }

    private static Set<String> waysInto(Class<?> state) {
        Set<String> ways = new LinkedHashSet<>();
        for (Class<?> declaring : STATES) {
            for (Method m : declaring.getMethods()) {
                if (Modifier.isStatic(m.getModifiers()) && m.getReturnType() == state) {
                    ways.add(signature(m));
                }
            }
        }
        return ways;
    }

    /**
     * None of them answers with its own state. That is what {@code ResolvedModule.with} was — a
     * method on the claim that takes a payload and hands the claim back — and it is the one shape
     * that lets a state be asserted of a tree nothing established it of.
     */
    @Test
    void noStateAnswersWithItself() {
        for (Class<?> state : STATES) {
            for (Method m : state.getMethods()) {
                assertFalse(!Modifier.isStatic(m.getModifiers()) && m.getReturnType() == state,
                        state.getSimpleName() + "." + signature(m) + " hands the claim back");
            }
        }
    }

    /** The way to an expandable module is the cycle check, so a module with a value defined in terms
     * of itself has no way through — and one without it does. */
    @Test
    void theWayToAnExpandableModuleIsTheCycleCheck() {
        Hir.Module wellFounded = resolved("""
                module m exposing ( n )

                let n = 1
                """);
        assertEquals("m", Expandable.check(wellFounded, Map.of(), DefaultStdlib.get()).name());

        Hir.Module reachesItself = resolved("""
                module m exposing ( a )

                let a = b
                let b = a
                """);
        CompileException refused =
                assertThrows(CompileException.class, () -> Expandable.check(reachesItself, Map.of(), DefaultStdlib.get()));
        assertTrue(refused.getMessage().contains("a"), refused.getMessage());
    }

    /**
     * The way to a settled module is the settling, so a clause naming a helper is the rule that
     * helper writes afterwards and is a call to it before. A reader of the settled state is reading
     * what the clause says; a reader of the state below it would have had to expand the call to find
     * out.
     */
    @Test
    void theWayToASettledModuleIsTheSettling() {
        Hir.Module resolved = resolved("""
                module m exposing ( Amount )

                data Amount = Int
                    invariant positive(value)

                let positive (n: Int) : Bool = n > 0
                """);
        Expandable expandable = Expandable.check(resolved, Map.of(), DefaultStdlib.get());

        assertTrue(clauseOf(expandable.module().defs(), "Amount") instanceof Hir.Apply,
                "the clause is a call to the helper until something expands it");

        ResolvedSymbols scope = TypeChecker.symbols(expandable.module(), DefaultStdlib.get());
        InvariantSettled settled = InvariantSettled.settle(expandable, scope,
                ScopedDeclarations.kindsOf(scope), Map.of());

        assertFalse(clauseOf(settled.defs().stream().map(InvariantSettled.Def::def).toList(),
                        "Amount") instanceof Hir.Apply,
                "settling is what expands it, and it is what the state is named for");
    }

    private static Hir.Expr clauseOf(List<Hir.Def> defs, String named) {
        for (Hir.Def def : defs) {
            if (def.name().equals(named) && def instanceof Hir.Data data
                    && !data.invariants().isEmpty()) {
                return data.invariants().get(0).expr();
            }
        }
        return null;
    }
}
