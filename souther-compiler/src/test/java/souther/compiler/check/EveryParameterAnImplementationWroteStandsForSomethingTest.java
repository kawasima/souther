package souther.compiler.check;

import souther.compiler.Compiler;
import souther.compiler.ast.Hir;
import souther.compiler.diag.CompileException;
import souther.compiler.query.Compilation;
import souther.compiler.query.Names;
import souther.compiler.types.ValueName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@code let} implementing a behavior writes its inputs and then the behaviors it depends on, in
 * one list. Where that list stops being inputs is what the behavior declares and nothing about the
 * parameters themselves, and it is said here — so a reader wanting to know which parameter is which
 * asks rather than measures the two lists against each other.
 *
 * <p>One binding per parameter the author wrote, whatever the declaration asks for. A definition is
 * read while it is being typed, and a reading that handed back only the parameters that lined up
 * would be handing back a list whose positions are not the positions in the source. So a parameter
 * the declaration accounts for nothing for is an arm, and it is a different arm from one standing
 * where a clause that reaches no declaration would have named a behavior: the first has no place in
 * the declaration at all, and the second has a place and no name.
 */
class EveryParameterAnImplementationWroteStandsForSomethingTest {

    private static final String TWO_OF_EACH = """
            module example.shape

            data MemberId = String
            data Found = { id: MemberId }
            data Missing = { why: String }

            behavior findMember : (id: MemberId) -> Found | Missing

            behavior logLookup : (id: MemberId) -> Found | Missing

            behavior place : (id: MemberId, other: MemberId) -> Found | Missing
                depends on findMember, logLookup

            let place (id, other, findMember, logLookup) = match findMember(id) with
                | Found   -> logLookup(other)
                | Missing -> findMember(other)
            """;

    /**
     * The inputs are the parameters the behavior declares inputs for, and the ones a
     * {@code depends on} clause fills are not among them.
     *
     * <p>The whole of what this issue was about. A reading that told them apart by comparing the
     * length of the signature with the length of the {@code let} found the two differing for every
     * behavior that depends on anything, and left all four of these parameters unaccounted for.
     */
    @Test
    void theInputsAreTheOnesTheBehaviorDeclaresAndTheInjectedOnesAreNot() {
        SpecImplementation.Implemented implemented = alignmentOf(TWO_OF_EACH, "place");

        assertEquals(List.of("id", "other"), namesOf(implemented.inputs()),
                "the behavior declares two inputs, so the first two parameters are them");
        assertEquals(List.of("id", "other"), declaredNamesOf(implemented),
                "and each of them stands for the input of that name on the `behavior` line");
        assertEquals(List.of(0, 1), positionsOf(implemented),
                "at the position the signature holds its type at");
        assertEquals(List.of("findMember", "logLookup"), injectedNamesOf(implemented),
                "the rest are the behaviors the clause names, in the order it names them");
    }

    /** And the facts about the whole shape say it is one a caller may go on from. */
    @Test
    void anImplementationTheDeclarationAccountsForIsCompleteInShape() {
        SpecImplementation.Implemented implemented = alignmentOf(TWO_OF_EACH, "place");

        assertTrue(implemented.hasExactArity());
        assertTrue(implemented.hasAnsweredDependencies());
        assertTrue(implemented.hasCompleteShape());
    }

    /**
     * A parameter the declaration asks for no position for is one of these, and says so.
     *
     * <p>Written where an author has typed one parameter too many and not yet been told. What is
     * asked here is what the parameter is before anything refuses it, which is what an editor
     * reading the buffer has — and the refusal is asserted beside it, so which diagnostic answers
     * for the arity is read off the compiler rather than off a comment.
     */
    @Test
    void aParameterThePositionsRunOutBeforeIsExtraneous() {
        String tooMany = TWO_OF_EACH.replace(
                "let place (id, other, findMember, logLookup)",
                "let place (id, other, findMember, logLookup, stray)");
        SpecImplementation.Implemented implemented = alignmentOf(tooMany, "place");

        assertEquals("E1615",
                assertThrows(CompileException.class, () -> Compiler.compile(tooMany)).code(),
                "the arity is what refuses it, at the definition");

        assertEquals(5, implemented.bindings().size(),
                "one for each parameter the author wrote, including the one nothing asked for");
        assertInstanceOf(SpecImplementation.ParameterBinding.Extraneous.class,
                implemented.bindings().get(4));
        assertFalse(implemented.hasExactArity());
        assertTrue(implemented.hasAnsweredDependencies(),
                "every clause still reaches a declaration; it is the list that is too long");
        assertFalse(implemented.hasCompleteShape());
    }

    /**
     * And a definition too short to fill the positions is divided as far as it goes.
     *
     * <p>Not refused here. What a reader does about a definition the declaration is not satisfied by
     * is its own — the checker reports it, an emitter will not run on it, and an editor answers about
     * the parameters that did line up. A reading that threw would be deciding that for all three.
     */
    @Test
    void aDefinitionShortOfThePositionsIsDividedAsFarAsItGoes() {
        SpecImplementation.Implemented implemented = alignmentOf(TWO_OF_EACH.replace(
                "let place (id, other, findMember, logLookup)", "let place (id, other)"), "place");

        assertEquals(List.of("id", "other"), namesOf(implemented.inputs()));
        assertEquals(List.of(), injectedNamesOf(implemented),
                "no parameter was written where the clause's behaviors go");
        assertFalse(implemented.hasExactArity());
        assertTrue(implemented.hasAnsweredDependencies(),
                "the clause reaches its declarations whether or not a parameter was written for it");
    }

    /**
     * A clause that reaches no declaration leaves the parameter under it standing for nothing, and
     * that is not the same answer as the parameter being one nothing asked for.
     */
    @Test
    void aClauseThatReachesNothingLeavesItsParameterUnanswered() {
        SpecImplementation.Implemented implemented = alignmentOf(
                TWO_OF_EACH.replace("depends on findMember, logLookup",
                        "depends on findMember, noSuchBehavior")
                        .replace("logLookup(other)", "findMember(other)")
                        .replace("let place (id, other, findMember, logLookup)",
                                "let place (id, other, findMember, noSuchBehavior)"), "place");

        assertInstanceOf(SpecImplementation.ParameterBinding.Unanswered.class,
                implemented.bindings().get(3),
                "the clause names nothing, so nothing names this parameter");
        assertTrue(implemented.hasExactArity(), "a parameter was written for every position");
        assertFalse(implemented.hasAnsweredDependencies());
        assertFalse(implemented.hasCompleteShape());
    }

    /**
     * Every behavior a module implements, in one walk of it.
     *
     * <p>A behavior with no {@code let} is absent rather than present with nothing: an injected
     * behavior has no parameters to divide, and a caller handed an empty division for it would have
     * to decide which of the two it was looking at.
     */
    @Test
    void aModuleIsDividedOnceAndABehaviorWithNoLetIsNotInIt() {
        Map<String, SpecImplementation.Implemented> implementations =
                SpecImplementation.implementationsOf(moduleOf(TWO_OF_EACH));

        assertEquals(List.of("place"), List.copyOf(implementations.keySet()),
                "`findMember` and `logLookup` are Java's to supply and have no `let` here");
        assertNull(implementations.get("findMember"));
    }

    private static List<String> namesOf(List<Hir.FnParam> parameters) {
        List<String> names = new ArrayList<>();
        for (Hir.FnParam parameter : parameters) {
            names.add(parameter.name());
        }
        return names;
    }

    /** What the `behavior` line calls each input the implementation's parameters stand for. */
    private static List<String> declaredNamesOf(SpecImplementation.Implemented implemented) {
        List<String> names = new ArrayList<>();
        for (SpecImplementation.ParameterBinding.AnInput input : implemented.declaredInputs()) {
            names.add(input.declared().name());
        }
        return names;
    }

    private static List<Integer> positionsOf(SpecImplementation.Implemented implemented) {
        List<Integer> at = new ArrayList<>();
        for (SpecImplementation.ParameterBinding.AnInput input : implemented.declaredInputs()) {
            at.add(input.at());
        }
        return at;
    }

    /** The behaviors the injected parameters stand for, named as they are declared. */
    private static List<String> injectedNamesOf(SpecImplementation.Implemented implemented) {
        List<String> names = new ArrayList<>();
        for (SpecImplementation.ParameterBinding binding : implemented.bindings()) {
            if (binding instanceof SpecImplementation.ParameterBinding.AnInjection injected) {
                ValueName.Behavior behavior = injected.behavior();
                names.add(behavior.name());
            }
        }
        return names;
    }

    private static SpecImplementation.Implemented alignmentOf(String source, String behavior) {
        SpecImplementation.Implemented implemented =
                SpecImplementation.implementationsOf(moduleOf(source)).get(behavior);
        assertNotNull(implemented, "`" + behavior + "` is implemented by a `let` of its name");
        return implemented;
    }

    /**
     * The module as its names resolved, which is before anything refuses what is written in it.
     *
     * <p>Where these are asked from. Half of what is asked here is about a definition the check
     * would refuse, and a model read after the check would have none of them in it.
     */
    private static Hir.Module moduleOf(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Hir.Module resolved = compilation.db().ask(new Names.Resolved(module)).value();
        assertNotNull(resolved, "the source under test resolves");
        return resolved;
    }
}
