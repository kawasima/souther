package souther.compiler;

import souther.compiler.ast.Hir;
import souther.compiler.doc.PublishedCaseOrder;
import souther.compiler.stdlib.Stdlib;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The standard library's published surface, written down. Every qualified name a module outside the
 * reserved namespace may write, with the parameter names and types it takes, checked against a
 * recorded copy.
 *
 * <p>What this catches is vocabulary drifting back in. The naming grammar the library follows is a
 * set of rules about words — the same word for the same operation, a settled term where one exists,
 * the behaviour in the name where languages disagree — and rules about words are enforced by reading
 * them, which nothing does on every commit. A snapshot does: adding, renaming or reordering anything
 * on the surface fails here, and the diff is the whole surface before and after, which is the form a
 * reader can judge the new name against its neighbours in.
 *
 * <p>It is not a test of behaviour and does not stand in for one. Update it by running with
 * {@code -Dsouther.surface.update=true} once the change is the one intended.
 */
class ThePublishedSurfaceIsFixedTest {

    private static final String SNAPSHOT = "/souther/published-surface.txt";
    private static final Path SOURCE = Path.of("src/test/resources/souther/published-surface.txt");

    @Test
    void thePublishedSurfaceMatchesTheRecordedOne() throws IOException {
        String current = render();
        if (Boolean.getBoolean("souther.surface.update")) {
            Files.createDirectories(SOURCE.getParent());
            Files.writeString(SOURCE, current, StandardCharsets.UTF_8);
        }
        assertEquals(recorded(), current,
                "the standard library's published surface changed. If that is the intention, rerun"
                        + " with -Dsouther.surface.update=true and read the diff as a whole: a new"
                        + " name has to sit beside the ones already there.");
    }

    @Test
    void theSnapshotHoldsWhatIsPublishedAndNothingElse() {
        String current = render();
        assertTrue(current.contains("List.fold("), "the `List.fold` sugar is part of the surface");
        assertFalse(current.contains("List.foldFrom"),
                "a private declaration is not on the surface, so it is not in the snapshot");
        assertEquals(DefaultStdlib.get().published().size(), current.lines().filter(l -> !l.isBlank()).count(),
                "one line per published name");
    }

    /** One line per published name, in qualifier then declaration order. */
    private static String render() {
        List<String> lines = new ArrayList<>();
        for (String qualified : DefaultStdlib.get().published()) {
            Stdlib.Entry entry =
                    DefaultStdlib.get().entry(DefaultStdlib.get().operation(qualified));
            if (entry == null) {
                // `List.fold` is sugar: it is written and reached, and has no declaration of its own.
                lines.add(qualified + "(step, seed, xs)");
                continue;
            }
            lines.add(qualified + signature(entry));
        }
        return String.join("\n", lines) + "\n";
    }

    /** The parameters as the declaration writes them, and the return type where it states one. */
    private static String signature(Stdlib.Entry entry) {
        Hir.FnDef fn = entry.declaration();
        String result = entry.signature().result() == null ? ""
                : " : " + shown(entry.signature().result(), fn.declaredReturn());
        if (fn.params().isEmpty()) {
            return result;   // a value, written with no parameter list
        }
        StringJoiner params = new StringJoiner(", ", "(", ")");
        for (int i = 0; i < fn.params().size(); i++) {
            params.add(fn.params().get(i).name() + ": "
                    + Type.show(entry.signature().params().get(i)));
        }
        return params + result;
    }

    /** A result of more than one case, in the order its declaration writes it — the surface is a
     *  declaration, as the parameter names beside it already are. */
    private static String shown(Type result, Hir.RetType declaredReturn) {
        if (!(result instanceof Type.Union union)) {
            return Type.show(result);
        }
        return PublishedCaseOrder.asDeclared(union.members(), declaredReturn).stream()
                .map(TypeSymbol::name).collect(java.util.stream.Collectors.joining(" | "));
    }

    private static String recorded() {
        try (InputStream in = ThePublishedSurfaceIsFixedTest.class.getResourceAsStream(SNAPSHOT)) {
            if (in == null) {
                throw new IllegalStateException("no recorded surface at " + SNAPSHOT
                        + " — run with -Dsouther.surface.update=true to write it");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
