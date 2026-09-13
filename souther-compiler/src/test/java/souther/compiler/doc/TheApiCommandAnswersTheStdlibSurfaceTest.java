package souther.compiler.doc;

import souther.compiler.DefaultStdlib;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * `souther api` answers the standard library's published surface — each name with the signature
 * the checker itself resolved, so nothing here can drift from what a call is checked against.
 */
class TheApiCommandAnswersTheStdlibSurfaceTest {

    private record Answer(int code, String out, String err) {}

    /** The name a listed line stands for. The type it answers with comes after {@code " : "}, and an
     *  argument list before that is written only where the name declares parameters — a value is
     *  written with none, so a name is not what stands before a {@code (} the line may not have. */
    private static String nameIn(String line) {
        int type = line.indexOf(" : ");
        String called = type < 0 ? line : line.substring(0, type);
        int open = called.indexOf('(');
        return open < 0 ? called : called.substring(0, open);
    }

    private Answer run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = ApiCommand.run(args,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Answer(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    /**
     * A result of more than one case is answered in the order its declaration writes it.
     *
     * <p>Asked of the command rather than of what orders the cases, because what is published is
     * what this prints. A union is a set and the members reach the printing through one — so what
     * comes out here is the order the library was written in, or it is one nobody chose and the
     * reader is being told the reason there is no value before the value.
     */
    @Test
    void aResultOfSeveralCasesIsAnsweredAsItsDeclarationWritesIt() {
        Answer answer = run("Int.divide");

        assertEquals(0, answer.code());
        assertEquals("Int.divide(dividend: Int, divisor: Int) : Int | DivisionByZero",
                answer.out().strip());
    }

    @Test
    void withNoArgumentEveryPublishedNameIsListedWithItsSignature() {
        Answer answer = run();

        assertEquals(0, answer.code());
        List<String> lines = answer.out().lines().toList();
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("List.map(")), "List.map is listed:\n" + answer.out());
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("String.length(")), "String.length is listed");
    }

    @Test
    void withNoArgumentNamesFollowThePublishedModuleOrder() {
        Answer answer = run();

        assertEquals(0, answer.code());
        List<String> listed = answer.out().lines()
                .map(TheApiCommandAnswersTheStdlibSurfaceTest::nameIn)
                .toList();

        assertEquals(List.copyOf(DefaultStdlib.get().published()), listed,
                "the listing is one module's vocabulary at a time, and the library is what says"
                        + " which module a name reads as:\n" + answer.out());
        assertTrue(listed.indexOf("List.fold") < listed.indexOf("Set.empty"),
                "a name callable only through its sugar stays in the block of the module it is"
                        + " called through:\n" + answer.out());
    }

    @Test
    void aModuleQualifierNarrowsTheListToThatModule() {
        Answer answer = run("Option");

        assertEquals(0, answer.code());
        assertTrue(answer.out().lines().allMatch(l -> l.startsWith("Option.")), answer.out());
        assertTrue(answer.out().lines().anyMatch(l -> l.equals("Option.withDefault(default: 'a, opt: 'a?) : 'a")),
                "the parameter names travel with the signature, and the optional is rendered in surface syntax:\n"
                        + answer.out());
    }

    @Test
    void aNameCallableOnlyThroughItsSugarIsStillPartOfTheSurface() {
        Answer answer = run("List");

        assertTrue(answer.out().lines().anyMatch(l -> l.startsWith("List.fold(")),
                "`List.fold` is what the specification tells a reader to call, and it is listed"
                        + " even though it is written as sugar over a private helper:\n" + answer.out());
        assertTrue(answer.out().lines().noneMatch(l -> l.startsWith("List.foldFrom(")),
                "the private helper it rewrites to stays out of the surface:\n" + answer.out());
    }

    @Test
    void aSugaredNameCarriesOnlyTheArgumentsItsCallerWrites() {
        Answer answer = run("List.fold");

        assertEquals(0, answer.code(), answer.err());
        assertEquals("List.fold(step: ('acc, 'a) -> 'acc, seed: 'acc, xs: List<'a>) : 'acc",
                answer.out().strip(),
                "the index the rewrite supplies is not an argument the caller passes");
    }

    @Test
    void searchFindsAFunctionWithoutKnowingItsModule() {
        Answer answer = run("--search", "fold");

        assertEquals(0, answer.code());
        assertTrue(answer.out().lines().anyMatch(l -> l.startsWith("List.fold(")), answer.out());
        assertTrue(answer.out().lines().allMatch(l -> l.toLowerCase(Locale.ROOT).contains("fold")),
                "every line answers the term:\n" + answer.out());
    }

    @Test
    void aPrivateHelperIsNotPartOfTheAnswer() {
        Answer answer = run();

        assertFalse(answer.out().lines().anyMatch(l -> l.contains("private")), "nothing is listed as private");
    }

    @Test
    void sourceOfAModulePrintsItsSouSourceWithItsComments() {
        Answer answer = run("--source", "Option");

        assertEquals(0, answer.code());
        assertTrue(answer.out().contains("module souther.option"));
        assertTrue(answer.out().contains("//"), "the module's own comments come along");
    }

    @Test
    void anUnknownQualifierSaysSoAndNamesTheModules() {
        Answer answer = run("Maybe");

        assertEquals(2, answer.code());
        assertTrue(answer.err().contains("no stdlib module `Maybe`"), answer.err());
        assertTrue(answer.err().contains("Option"), "the real module names are offered: " + answer.err());
    }
}
