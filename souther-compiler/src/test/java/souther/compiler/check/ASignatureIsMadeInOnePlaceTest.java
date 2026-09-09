package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.test.RepositoryLayout;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A signature is admitted where what it is made from is written, and each origin is admitted once.
 *
 * <p>{@link Sig}'s constructor is package-private, so nothing outside {@code check} can assemble one
 * out of shapes nothing admitted. That closes the wrong kind of signature; it does not close a
 * second walk. Two callers of the walk would each build a correct answer, and the phase below the
 * check would read one of them while the check read the other — the boundary's question answered
 * twice, which is what carrying the answer was for. Nothing an ordinary test can observe would
 * change: the two walks are the same walk, so their answers agree, and they agree until the day the
 * trees they are given stop being the same tree.
 *
 * <p>There are two origins and they are not the same question. A written declaration is admitted
 * with the parameters it names, and what comes out carries both ({@link DeclaredSig}); a
 * composition's answer is a type nobody wrote, and what comes out is a {@link Sig} with no
 * parameters to name. What has to hold is that each is reached from one place, not that both go
 * through one door.
 *
 * <p>So it is asked of the sources. This is a tripwire and not a proof — a helper in between defeats
 * it — but the call that would have to be added first is the one this fails on.
 */
class ASignatureIsMadeInOnePlaceTest {

    /** Read once: what this asks of it does not change between its checks. */
    private static final RepositoryLayout REPOSITORY = RepositoryLayout.ofWorkingDirectory();

    /** Every main source, with its documentation comments left out, read once for all the calls
     *  asked about here. Reading the tree per question costs a pass over it per question, and the
     *  questions are what this grows by. */
    private static final List<Source> SOURCES = sources();

    /** One source as this reads it: where it is, and what it says outside its comments. */
    private record Source(Path at, String code) {}

    @Test
    void aWrittenDeclarationIsAdmittedInOnePlace() {
        assertEquals(List.of("check/SignatureDeclarations.java"),
                callersOf("SignatureBoundary.of("),
                "a declaration is admitted by one walk; these admit their own");
    }

    @Test
    void aCompositionsAnswerIsAdmittedInOnePlace() {
        assertEquals(List.of("check/PipelineSigs.java"),
                callersOf("SignatureBoundary.composedOutput("),
                "what a composition answers is admitted where the composition is worked out");
    }

    @Test
    void theQueryThatOwnsTheAnswerIsTheOnlyCaller() {
        assertEquals(List.of("query/Bodies.java"), callersOf("SignatureDeclarations.of("),
                "the query owns the declarations; these ask for a second set");
        assertEquals(List.of("query/Bodies.java"), callersOf("PipelineSigs.signatures("),
                "the query owns the signatures; these build their own");
    }

    /**
     * Which sources call {@code what}, by the directory and file they are in.
     *
     * <p>The file that declares it is not a call, and neither is prose naming it: a mention inside a
     * documentation comment reads as the call it describes, so a source is a caller only where the
     * text appears outside one.
     */
    private static List<String> callersOf(String what) {
        assertFalse(SOURCES.isEmpty(), "found no sources at all — the scan missed the tree");
        List<String> callers = new ArrayList<>();
        for (Source source : SOURCES) {
            if (declares(source.at(), what) || !source.code().contains(what)) {
                continue;
            }
            callers.add(source.at().getParent().getFileName() + "/" + source.at().getFileName());
        }
        return callers;
    }

    /** The tree, read and stripped of its comments. */
    private static List<Source> sources() {
        try {
            List<Source> read = new ArrayList<>();
            for (Path source : REPOSITORY.mainJavaSources()) {
                read.add(new Source(source,
                        code(Files.readString(source, StandardCharsets.UTF_8))));
            }
            return List.copyOf(read);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Whether this source is the one the call names, which mentions it as its own declaration. */
    private static boolean declares(Path source, String what) {
        String owner = what.substring(0, what.indexOf('.'));
        return source.endsWith(Path.of("check", owner + ".java"));
    }

    /** The source with its documentation comments left out. */
    private static String code(String text) {
        StringBuilder out = new StringBuilder(text.length());
        int at = 0;
        while (at < text.length()) {
            int opened = text.indexOf("/*", at);
            if (opened < 0) {
                out.append(text, at, text.length());
                break;
            }
            out.append(text, at, opened);
            int closed = text.indexOf("*/", opened + 2);
            at = closed < 0 ? text.length() : closed + 2;
        }
        return out.toString();
    }
}
