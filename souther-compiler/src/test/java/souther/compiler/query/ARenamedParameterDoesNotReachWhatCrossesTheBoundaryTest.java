package souther.compiler.query;

import souther.compiler.check.DeclaredSig;
import souther.compiler.check.Sig;
import souther.compiler.meta.ModulePath;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * What a behavior calls its inputs and what those inputs can arrive as are two answers.
 *
 * <p>Renaming a parameter changes the declaration and changes nothing about what crosses the
 * boundary. A reader that asked what an input is called — an editor writing a hint, a reading that
 * names a position — has a different answer afterwards; a reader that asked what a stage routes,
 * what a codec is built for or what an emitter writes has the same one, and the question it asked
 * is where that is decided rather than at each of those readers.
 */
class ARenamedParameterDoesNotReachWhatCrossesTheBoundaryTest {

    private static final String SOURCE = """
            module m.a exposing ( A )

            data A = Int

            behavior take : (%s: A) -> A
            let take (%s) = %s
            """;

    /** The module as it is written with its one parameter under {@code name}. */
    private static String written(String name) {
        return SOURCE.formatted(name, name, name);
    }

    @Test
    void theDeclarationsAnswerChangesAndTheBoundarysDoesNot() {
        Compilation compilation = Compilation.ofDocuments(
                Map.of("a.sou", written("userId")), Set.of(), ModulePath.EMPTY);
        compilation.answerEverything();
        Map<String, DeclaredSig> declared = declarations(compilation);
        Map<String, Sig> crossing = boundaries(compilation);

        compilation.update(Map.of("a.sou", written("id")), Set.of());
        compilation.answerEverything();

        assertEquals(List.of("id"),
                declarations(compilation).get("take").inputs().stream()
                        .map(DeclaredSig.Input::name).toList(),
                "the rename reaches what the declaration says its input is called");
        assertNotEquals(declared, declarations(compilation),
                "a declaration that renames a parameter is a different declaration");
        assertEquals(crossing, boundaries(compilation),
                "what crosses the boundary is what it was before the rename");
    }

    private static Map<String, DeclaredSig> declarations(Compilation compilation) {
        Map<String, DeclaredSig> declared =
                compilation.db().ask(new Bodies.DeclaredSignatures("m.a")).value();
        assertNotNull(declared, "the module under test compiles");
        return declared;
    }

    private static Map<String, Sig> boundaries(Compilation compilation) {
        Map<String, Sig> crossing =
                compilation.db().ask(new Bodies.DeclaredBoundaries("m.a")).value();
        assertNotNull(crossing, "the module under test compiles");
        return crossing;
    }
}
