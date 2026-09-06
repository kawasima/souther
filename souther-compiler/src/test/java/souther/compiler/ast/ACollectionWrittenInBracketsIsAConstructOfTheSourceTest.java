package souther.compiler.ast;

import souther.compiler.diag.Region;
import souther.compiler.diag.SourcePos;
import souther.compiler.frontend.CstFrontend;
import souther.compiler.types.SourceConstruct;
import souther.compiler.types.SourceConstructOrigin;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A collection the author wrote in brackets is one construct of the source, and stays one wherever
 * it is carried.
 *
 * <p>What the brackets stand for is settled long after the source is gone — a list in a body, the
 * rows of a table in a fixture, and in either case library operations no source wrote. Each of
 * those operations is a reference derived from the collection, so the collection has to be
 * something to derive from: an identity, and not the place it happens to sit. A helper holding one
 * is expanded at each of its calls, which puts several nodes at one place.
 *
 * <p>The brackets a pass composes are the other answer. A comprehension is lowered to an {@code if}
 * over two lists the author never wrote, and a fixture composes a list out of values a table
 * supplied. Those say that no source wrote them, which is not a permission anyone needs: it hands
 * out no construct of the author's for a copy to take twice.
 */
class ACollectionWrittenInBracketsIsAConstructOfTheSourceTest {

    private static final SourcePos POS = new SourcePos(1, 1);

    /** Two collections the author wrote are two constructs, however alike they are spelled. */
    @Test
    void twoCollectionsTheAuthorWroteAreTwoConstructs() {
        List<Ast.ListLit> written = collectionsOf("""
                module demo

                let first = [1, 2]

                let second = [1, 2]
                """);

        assertEquals(2, written.size(), written.toString());
        assertNotEquals(written.get(0).origin(), written.get(1).origin(),
                "two collections the author wrote are two, and the brackets read alike");
    }

    /** And what each carries says the author wrote a collection, not something else. */
    @Test
    void aCollectionTheAuthorWroteSaysThatIsWhatWasWritten() {
        List<Ast.ListLit> written = collectionsOf("""
                module demo

                let only = [1, 2, 3]
                """);

        assertEquals(1, written.size(), written.toString());
        assertTrue(written.get(0).origin().isWritten(),
                "the author wrote these brackets");
        assertEquals(SourceConstruct.COLLECTION_LITERAL, written.get(0).origin().kind());
    }

    /**
     * And one collection stays one through the nodes below. The reading turns it into a list or into
     * the rows of a table, and every rewrite between here and Core rebuilds nodes — so what is asked
     * is that the identity arrives unchanged, which is what a reference derived from it is derived
     * from.
     */
    @Test
    void oneCollectionIsOneConstructThroughTheTreesBelow() {
        SourceConstructOrigin written = collectionsOf("""
                module demo

                let only = [1]
                """).get(0).origin();

        Hir.ListLit asAList = new Hir.ListLit(List.of(), written, POS, null);
        Hir.RowCollection asRows = new Hir.RowCollection(List.of(), written, POS, null);

        assertSame(written, asAList.origin());
        assertSame(written, asRows.origin());
        assertSame(written,
                ((Hir.ListLit) Hir.withRegion(asAList, Region.point(POS))).origin(),
                "carried over another region, it is the same collection of the source");
        assertSame(written,
                ((Hir.RowCollection) Hir.withRegion(asRows, Region.point(POS))).origin());
    }

    /** A collection a pass composed says no source wrote it, and says it without asking anyone. */
    @Test
    void aCollectionAPassComposedSaysNoSourceWroteIt() {
        Hir.ListLit composed =
                new Hir.ListLit(List.of(), SourceConstructOrigin.unwritten(), POS, null);

        assertFalse(composed.origin().isWritten(),
                "the two lists a comprehension lowers to are this pass's, not the author's");
    }

    /** Every collection the source wrote, in the order the reading met them. */
    private static List<Ast.ListLit> collectionsOf(String source) {
        List<Ast.ListLit> found = new ArrayList<>();
        for (Ast.FnDef each : CstFrontend.parse(source).fns()) {
            if (each.body() instanceof Ast.FnBody.Written written) {
                collect(written.expr(), found);
            }
        }
        return found;
    }

    private static void collect(Ast.Expr e, List<Ast.ListLit> found) {
        if (e == null) {
            return;
        }
        if (e instanceof Ast.ListLit lit) {
            found.add(lit);
        }
        Ast.forEachChild(e, c -> collect(c, found));
    }
}
