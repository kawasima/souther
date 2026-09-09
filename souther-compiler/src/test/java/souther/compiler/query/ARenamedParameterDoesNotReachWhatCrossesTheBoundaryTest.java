package souther.compiler.query;

import souther.compiler.check.DeclaredSig;
import souther.compiler.check.Sig;
import souther.compiler.jvm.ClassFileImage;
import souther.compiler.meta.ModulePath;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

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

    /**
     * And the store stops there rather than answering the same thing again.
     *
     * <p>Asked of the answer and not of the value. Two values that are equal are what a reader gets
     * either way; what decides whether a reading below is asked again is whether the store kept the
     * answer it had, and that is the claim a rename is supposed to be stopped by.
     */
    @Test
    void whatCrossesIsTheAnswerTheStoreAlreadyHad() {
        Compilation compilation = Compilation.ofDocuments(
                Map.of("a.sou", written("userId")), Set.of(), ModulePath.EMPTY);
        compilation.answerEverything();
        Answer<?> crossing = compilation.db().ask(new Bodies.Signatures("m.a"));
        Answer<?> declaring = compilation.db().ask(new Bodies.DeclaredSignatures("m.a"));

        compilation.update(Map.of("a.sou", written("id")), Set.of());
        compilation.answerEverything();

        assertNotSame(declaring, compilation.db().ask(new Bodies.DeclaredSignatures("m.a")),
                "the rename is an edit to the declaration, so that answer is not the one it was —"
                        + " an instrument that said otherwise would say it of anything");
        assertSame(crossing, compilation.db().ask(new Bodies.Signatures("m.a")),
                "the rename left what the module publishes as signatures where it was");
    }

    /**
     * And what the module publishes is the declaration, so the rename reaches that.
     *
     * <p>The other side of the same boundary, said here so that neither half can be read as the
     * whole. A jar carries the declaration and not the witness, and a declaration is what its author
     * wrote — so an importing compilation reads the new name and admits it for itself. A reading of
     * this that only pinned what does not move would be met by a compiler that had stopped
     * publishing what a behavior is written as.
     */
    @Test
    void whatTheModulePublishesIsTheDeclarationAndMovesWithIt() {
        Compilation compilation = Compilation.ofDocuments(
                Map.of("a.sou", written("userId")), Set.of(), ModulePath.EMPTY);
        compilation.answerEverything();
        Map<String, ClassFileImage> published = classes(compilation);

        compilation.update(Map.of("a.sou", written("id")), Set.of());
        compilation.answerEverything();

        assertNotEquals(published, classes(compilation),
                "what a module publishes carries the declaration as it is written");
    }

    private static Map<String, DeclaredSig> declarations(Compilation compilation) {
        Map<String, DeclaredSig> declared =
                compilation.db().ask(new Bodies.DeclaredSignatures("m.a")).value();
        assertNotNull(declared, "the module under test compiles");
        return declared;
    }

    private static Map<String, ClassFileImage> classes(Compilation compilation) {
        Map<String, ClassFileImage> published =
                compilation.db().ask(new Output.Classes("m.a")).value();
        assertNotNull(published, "the module under test is emitted");
        return published;
    }

    private static Map<String, Sig> boundaries(Compilation compilation) {
        Map<String, Sig> crossing = compilation.db().ask(new Bodies.Signatures("m.a")).value();
        assertNotNull(crossing, "the module under test compiles");
        return crossing;
    }
}
