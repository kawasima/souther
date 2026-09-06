package souther.compiler.check;

import souther.compiler.DefaultStdlib;
import souther.compiler.stdlib.Stdlib;
import souther.compiler.ast.Hir;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.ReachName;
import souther.compiler.types.ApplicationOrigin;
import souther.compiler.types.SourceConstruct;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.types.SourceReferenceOrigin;
import souther.compiler.types.ValueName;
import souther.compiler.types.WrittenOwner;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import souther.compiler.types.ReachName;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A sugar is a call to something else, and the call graph has to know which something else.
 *
 * <p>{@code List.fold} has no declaration of its own: it is {@code List.foldFrom} with the index the
 * walk starts from already supplied. So a body that folds reaches the library's one recursion, and an
 * edge to it is what says the module holding that body needs the method. Read from the library, which
 * is where the rewrite is declared — written out beside it, the two agreed until a second sugar was
 * added, and the disagreement is an edge quietly missing from a graph nothing else re-derives.
 *
 * <p>The condition is the rewrite's own. A sugar written with a different number of arguments than
 * it stands for is not that call, the rewrite is not taken, and no edge is owed — crediting one
 * would put a declaration into what a module has to emit on the strength of a call that never
 * reaches it.
 *
 * <p>Every sugar the library declares is asked about, so this covers the next one on the day it is
 * added rather than the day someone remembers this file.
 */
class TheCallGraphReadsASugarFromTheLibraryThatDeclaresItTest {

    private static final SourcePos POS = new SourcePos(1, 1);

    /** A call of {@code name} applied to {@code args} integers. Taken as the operation, so this
     *  does not split a spelling to find out which part of it is the alias. */
    private static Hir.Expr callTo(ValueName.Stdlib.Operation name, int args) {
        List<Hir.Expr> given = new ArrayList<>();
        for (int i = 0; i < args; i++) {
            given.add(new Hir.IntLit(i, POS, null));
        }
        return Hir.Apply.synthetic(name.qualified(), new ReachName.OfLibrary(name),
                new SourceReferenceOrigin(new WrittenOwner.Body("m", "b"), 0),
                new ApplicationOrigin.Written(SourceConstructOrigin.written(
                        new WrittenOwner.Body("m", "b"), 0, SourceConstruct.CALL)),
                given, POS, null);
    }

    /** The library's helpers as a table is keyed: under the operation each is the body of, which
     *  the library says rather than this splitting a qualified name. */
    private static Map<ReachName.Declaration, HelperEntry> libraryHelpers() {
        Map<ReachName.Declaration, HelperEntry> reachable = new LinkedHashMap<>();
        DefaultStdlib.get().helpers().forEach((operation, def) -> {
            ReachName.Declaration reference = new ReachName.OfLibrary(operation);
            reachable.put(reference, HelperEntry.reached(reference, def));
        });
        return reachable;
    }

    private static Set<String> callsIn(Hir.Expr e) {
        Set<ReachName.Declaration> out = new LinkedHashSet<>();
        HelperInliner.helperCallsIn(DefaultStdlib.get(), e, libraryHelpers(), out);
        Set<String> rendered = new LinkedHashSet<>();
        out.forEach(reference -> rendered.add(reference.rendered()));
        return rendered;
    }

    @Test
    void theLibraryDeclaresAtLeastOneSugarForThisToBeAbout() {
        assertFalse(DefaultStdlib.get().rewrites().isEmpty(),
                "a claim about every sugar says nothing where there are none");
    }

    @Test
    void aSugarWrittenWithWhatItStandsForReachesWhatItRewritesTo() {
        DefaultStdlib.get().rewrites().forEach((sugar, rewrite) -> {
            Set<String> reached = callsIn(callTo(sugar, rewrite.keptArgs()));

            assertEquals(Set.of(rewrite.target().qualified()), reached,
                    sugar + " is " + rewrite.target().qualified()
                            + " with the rest of its arguments supplied");
        });
    }

    @Test
    void andOneWrittenWithAnotherNumberOfArgumentsReachesNothing() {
        DefaultStdlib.get().rewrites().forEach((sugar, rewrite) -> {
            assertEquals(Set.of(), callsIn(callTo(sugar, rewrite.keptArgs() + 1)),
                    sugar + " written with one argument too many is not the call it stands for");
            if (rewrite.keptArgs() > 0) {
                assertEquals(Set.of(), callsIn(callTo(sugar, rewrite.keptArgs() - 1)),
                        sugar + " written with one too few is not it either");
            }
        });
    }

    /**
     * And why the edge decides anything, for the one sugar the library has: {@code List.fold}
     * rewrites to a recursion, so a body that folds holds a call nothing can expand away and the
     * module holding it emits a method.
     *
     * <p>Of that sugar and not of every sugar. Nothing in {@link Stdlib.Rewrite} says a rewrite
     * target recurses — one onto an ordinary helper would be a rewrite like any other — so a claim
     * made of all of them would refuse the next sugar for being unlike this one.
     */
    @Test
    void theFoldSugarRewritesToSomethingAModuleWouldHaveToEmit() {
        HelperTable table = HelperTable.of("probe", Map.of(), Map.of(), Map.of(),
                InliningPolicy.FULL, DefaultStdlib.get());
        HelperGraph graph = HelperGraph.of(table);
        Stdlib.Rewrite fold =
                DefaultStdlib.get().rewriteOf(ValueName.Stdlib.operation("List", "fold"));

        assertNotNull(fold, "`List.fold` is sugar for the fold the combinators are derived from");
        assertEquals("List.foldFrom", fold.target().qualified());
        assertTrue(graph.recurses(new ReachName.OfLibrary(fold.target())),
                "a module emits it as a method only because it recurses");
    }
}
