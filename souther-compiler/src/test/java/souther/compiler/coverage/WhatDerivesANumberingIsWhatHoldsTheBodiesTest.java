package souther.compiler.coverage;

import org.junit.jupiter.api.Test;
import souther.compiler.WhatWasCompiled;

import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A module's numbering is derived where its bodies are held, and nowhere else in the compiler.
 *
 * <p>{@link CoverageSites#of} is a function of the bodies, so two calls of it over one module come
 * to one numbering. That is what a reader takes on trust when it derives its own: the emitter wrote
 * numbers into probe calls under one plan, and a measure looks a hit up under another, and the two
 * are about one place only while the walk keeps agreeing with itself. Which is a property to hold
 * rather than a thing to rely on quietly — and the fewer places derive one, the less of the
 * compiler that property has to hold across.
 *
 * <p>So what is fixed here is not that the numbering is right; it is who may ask for one. A reader
 * that wants to know what a hit set means asks the check that holds the bodies, and there is one
 * such check per module. A second derivation somewhere else is a second answer that no later check
 * could tell from the first, since both are true of the same bodies until one of them stops being.
 *
 * <p><b>Who derives one, not who has one.</b> A method that asks the check for the module's
 * numbering is not counted here and is not meant to be: it takes the answer the bodies' own holder
 * gives, which is the whole of what this fixes. So a second way of <em>reaching</em> that answer —
 * a helper elsewhere that asks the check and hands the plan on — stays green under this. What is
 * wrong with such a helper is that a reader then has two routes to one answer, not that the two
 * could disagree.
 *
 * <p>Read off the compiled classes, so what is counted is what a method does rather than what a
 * reading of the sources makes of it — a call written inside a lambda belongs to the lambda, and
 * this says so.
 *
 * <p><b>What it does not see, said rather than left to be found.</b> The tests are not in
 * {@code target/classes}: a test deriving a plan straight from bodies is a fixture standing in for
 * a compile, and there is no reader downstream of it to disagree with. And what is read is this
 * module's classes — a caller written in the CLI or the language server would not be counted here.
 * Widening it is not free: those modules are built after this one, so a walk over their output
 * would read whatever the last build left and would say nothing at all on a clean one.
 */
class WhatDerivesANumberingIsWhatHoldsTheBodiesTest {

    private static final String SITES = "souther/compiler/coverage/CoverageSites";

    /** A method that may derive one, how many times it does, and why it is one of the ones that
     *  does. */
    private record Licence(String who, int calls, String why) { }

    private static final List<Licence> MAY_DERIVE = List.of(
            new Licence("souther.compiler.query.Bodies.Checked.compute", 1,
                    "the check holds the bodies, so this is where a numbering of them is a"
                            + " numbering of anything at all. What it decides here is carried by"
                            + " the answer, and every later walk of these bodies realizes it"));

    /**
     * A door a numbering comes out of, and who may go through it.
     *
     * <p>One row per way of coming by a numbering, and the row names the way's own callers rather
     * than the constructor's. A method that wraps the constructor is a way of its own: the call
     * inside it stays one however many callers it gains, so a table counting the constructor alone
     * says nothing about them. The constructor is a door like the others, and what it opens onto is
     * the two that wrap it — so a third wrapper is a caller of the constructor that this does not
     * name, and it turns the constructor's own row red before it can quietly widen anybody else's.
     *
     * @param door  the method a numbering comes out of, as the classes name it
     * @param who   what may go through it, and how many times each does
     * @param why   what makes those the ones
     */
    private record Door(String door, Map<String, Integer> who, String why) { }

    private static final String THE_CONSTRUCTOR =
            "souther.compiler.coverage.NumberingIdentity.<init>";

    private static final List<Door> DOORS = List.of(
            new Door(THE_CONSTRUCTOR,
                    Map.of("souther.compiler.coverage.SiteNumbering.Building.finish", 1,
                            "souther.compiler.coverage.NumberingIdentity.forThePlanOfNothing", 1),
                    "the two ways a numbering is come by, and the reason each is one is on its own"
                            + " row below. A caller here that is not one of them is a third way,"
                            + " and it would be a way nothing counts the callers of"),
            new Door("souther.compiler.coverage.SiteNumbering.Building.finish",
                    Map.of("souther.compiler.coverage.CoverageSites.of", 1),
                    "the walk that hands the numbers out is what knows what each addresses, and it"
                            + " is one act with the numbering being made. Which walk may decide one"
                            + " rather than realize one is the table above"),
            new Door("souther.compiler.coverage.NumberingIdentity.forThePlanOfNothing",
                    Map.of("souther.compiler.coverage.CoverageSites.Plan.<clinit>", 1),
                    "the plan of nothing, which numbers no place and is of nobody's module. It is"
                            + " not what a module whose bodies were not read has — that has no"
                            + " numbering — so a second caller here is a reader about to stand it"
                            + " in for one"));

    @Test
    void onlyTheCheckThatHoldsThemDerivesAModulesNumbering() {
        assertEquals(declared(MAY_DERIVE), derivations(),
                "a numbering derived anywhere else is a second answer about one module's arms, and"
                        + " a reader holding it is reading a run against numbers nothing wrote."
                        + " What may derive one, and why: " + why(MAY_DERIVE));
    }

    /**
     * And nothing else in the compiler makes one.
     *
     * <p>Beside the walk, because the two are told apart by nothing afterwards. A numbering put
     * together from parts a caller had to hand is a numbering, and an address of it is an address —
     * so a reader handed one would be reading a run against places by whatever the caller believed
     * the numbers meant. The walk is the only thing that knows.
     *
     * <p><b>The compiler, and not everywhere.</b> The constructor is public, because a fixture
     * stating a numbering outright is how the readings below it are tested, and a fixture of the
     * CLI's is not in this package. So what stops a caller elsewhere is not the type, and this walk
     * is not it either: it reads this module's classes, for the reason the derivation walk above
     * does — the other modules are built after this one, so a walk over their output would read
     * whatever the last build left and would say nothing at all on a clean one. A numbering made in
     * {@code souther-cli} or {@code souther-lsp} is outside what this can answer for.
     */
    @Test
    void nothingElseInTheCompilerMakesANumbering() {
        Map<String, Map<String, Integer>> declared = new TreeMap<>();
        Map<String, String> why = new LinkedHashMap<>();
        for (Door each : DOORS) {
            declared.put(each.door(), new TreeMap<>(each.who()));
            why.put(each.door(), each.why());
        }
        Map<String, Map<String, Integer>> found = new TreeMap<>();
        for (Door each : DOORS) {
            found.put(each.door(), new TreeMap<>(callersOf(each.door())));
        }

        assertEquals(declared, found,
                "a numbering made elsewhere in this module says what numbers mean without having"
                        + " handed any out, and a way of coming by one that nobody counts the"
                        + " callers of is where the next such caller goes unseen."
                        + " What each door is for: " + why);
    }

    private static Map<String, Integer> declared(List<Licence> licences) {
        Map<String, Integer> out = new TreeMap<>();
        licences.forEach(each -> out.put(each.who(), each.calls()));
        return out;
    }

    private static Map<String, String> why(List<Licence> licences) {
        Map<String, String> out = new LinkedHashMap<>();
        licences.forEach(each -> out.put(each.who(), each.why()));
        return out;
    }

    /** How many times each method of the compiler decides a numbering from bodies. Only the call
     *  that decides one: a walk taking a numbering already issued asks for it by another name, and
     *  hands out addresses of what it was given. */
    private static Map<String, Integer> derivations() {
        return calls(call -> call.owner().asInternalName().equals(SITES)
                && call.name().stringValue().equals("of"));
    }

    /** How many times each method of the compiler goes through {@code door}, which is written the
     *  way the classes name a method: the owning class, then the method. */
    private static Map<String, Integer> callersOf(String door) {
        int split = door.lastIndexOf('.');
        String owner = door.substring(0, split).replace('.', '/');
        String method = door.substring(split + 1);
        return calls(call -> call.owner().asInternalName().replace('$', '/').equals(owner)
                && call.name().stringValue().equals(method));
    }

    private static Map<String, Integer> calls(java.util.function.Predicate<InvokeInstruction> what)
            {
        Map<String, Integer> calls = new TreeMap<>();
        for (ClassModel model : WhatWasCompiled.compiled().all()) {
            String from = model.thisClass().asInternalName().replace('/', '.').replace('$', '.');
            for (MethodModel method : model.methods()) {
                method.code().ifPresent(code -> code.forEach(element -> {
                    if (element instanceof InvokeInstruction call && what.test(call)) {
                        calls.merge(from + "." + method.methodName().stringValue(), 1, Integer::sum);
                    }
                }));
            }
        }
        return calls;
    }
}
