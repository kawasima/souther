package souther.compiler.check;

import souther.compiler.WhereItSits;
import souther.compiler.DefaultStdlib;
import souther.compiler.ast.Ast;
import souther.compiler.ast.Hir;
import souther.compiler.ast.WrittenName;
import souther.compiler.diag.Region;
import souther.compiler.frontend.CstFrontend;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A name that comes out of an expansion is written where the copy says it is.
 *
 * <p>An expansion rebuilds every node of the callee's body, and a name rebuilt from what it denotes
 * — the canonical spelling and a position — is a name whose place was worked out rather than
 * carried. The two answers agree for a name of one code unit per character and part company for a
 * decomposed one, which is the same defect a report measured in the canonical name has, moved to the
 * other side of a rewrite.
 *
 * <p>So the property here is over the source and not over a length: for every name the copy says the
 * author wrote, the characters it points at have to be that name. A copy stamped with a call site is
 * held to it as well — the occurrence it was read from is in the callee's file, and a name claiming
 * to be written at the caller's is claiming characters that spell something else.
 */
class AnExpansionKeepsTheSpellingItCopiedTest {

    /** A hiragana ka followed by a combining voiced sound mark — two UTF-16 units, one glyph. */
    private static final String NFD = new String(new int[] {0x304b, 0x3099}, 0, 2);

    /**
     * A helper whose parameter is written decomposed, applied from a behavior. The copy reads that
     * parameter, and what it reads is one name spelled over two units.
     */
    @Test
    void aDecomposedNameSurvivesBeingCopiedIntoACaller() {
        String source = """
                module demo

                let twice (%s: Int): Int = %s + %s

                let outer (i: Int): Int = twice(i)
                """.formatted(NFD, NFD, NFD);

        assertEquals(List.of(), misspelled(source, "outer"),
                "a name came out of the expansion pointing at characters that do not spell it");
    }

    /** The same, over a name written as far from its qualifier as the source puts it. */
    @Test
    void aQualifiedNameSurvivesBeingCopiedIntoACaller() {
        String source = """
                module demo

                let sized (s: String): Int = String
                    .length(s)

                let outer (s: String): Int = sized(s)
                """;

        assertEquals(List.of(), misspelled(source, "outer"),
                "a name came out of the expansion pointing at characters that do not spell it");
    }

    /**
     * A body copied out of another module is stamped with the call site, so the names in it are read
     * against the caller's file. None of them may claim to be written there.
     */
    @Test
    void aCopyStampedWithTheCallSiteClaimsNoOccurrenceOfItsOwn() {
        String source = """
                module demo

                let outer (ns: List<Int>): List<Int> = List.map(n -> n, ns)
                """;

        assertEquals(List.of(), misspelled(source, "outer"),
                "a name copied out of another module claims to be written in this one");
    }

    /** Every name in {@code helper}'s expanded body that points at characters not spelling it. */
    private static List<String> misspelled(String source, String helper) {
        Ast.Module parsed = CstFrontend.parse(source);
        HelperInliner inliner = HelperInliner.forModule(Resolve.module(parsed, SyntaxSymbols.of(parsed, DefaultStdlib.get())), DefaultStdlib.get());
        Hir.Expr expanded =
                inliner.inline(inliner.held().get(new souther.compiler.ast.DefinitionName(helper))
                        .definition().writtenBody(), inliner.bodyOf(helper));

        List<String> wrong = new ArrayList<>();
        names(expanded, name -> {
            if (!name.authored()) {
                return;                     // a name a pass minted claims no characters
            }
            List<Region> segments = name.segments();
            assertTrue(!segments.isEmpty(),
                    "`" + name.canonical() + "` is spelled somewhere or is not a spelling");
            // Part by part, not over the whole stretch: what a report underlines runs from the first
            // part to the last whatever the author wrote between them, and only the parts are the
            // name. A qualified name written over a line break has a region holding the break.
            String[] parts = name.spelling().split("\\.", -1);
            assertEquals(parts.length, segments.size(),
                    "`" + name.spelling() + "` is spelled in " + segments.size() + " places");
            for (int i = 0; i < parts.length; i++) {
                String cut = cut(source, segments.get(i));
                if (!parts[i].equals(cut)) {
                    wrong.add("`" + parts[i] + "` of `" + name.spelling() + "` points at `" + cut
                            + "` (" + segments.get(i).start() + ")");
                }
            }
        });
        return wrong;
    }

    /** Every name held by an expression and the expressions under it. */
    private static void names(Hir.Expr e, java.util.function.Consumer<WrittenName> f) {
        if (e instanceof Hir.Var v) {
            f.accept(v.written());
        }
        if (e instanceof Hir.FieldAccess fa) {
            f.accept(fa.name());
        }
        if (e instanceof Hir.NewData nd) {
            nd.inits().forEach(i -> f.accept(i.written()));
        }
        Hir.forEachChild(e, c -> names(c, f));
    }

    /** The characters {@code at} covers, cut out of the source it was read from. */
    private static String cut(String source, Region at) {
        List<String> lines = List.of(source.split("\n", -1));
        if (WhereItSits.in(source, at).start().line() == WhereItSits.in(source, at).end().line()) {
            return lines.get(WhereItSits.in(source, at).start().line() - 1)
                    .substring(WhereItSits.in(source, at).start().column() - 1, WhereItSits.in(source, at).end().column() - 1);
        }
        StringBuilder out =
                new StringBuilder(lines.get(WhereItSits.in(source, at).start().line() - 1).substring(WhereItSits.in(source, at).start().column() - 1));
        for (int line = WhereItSits.in(source, at).start().line() + 1; line < WhereItSits.in(source, at).end().line(); line++) {
            out.append('\n').append(lines.get(line - 1));
        }
        return out.append('\n').append(lines.get(WhereItSits.in(source, at).end().line() - 1), 0, WhereItSits.in(source, at).end().column() - 1)
                .toString();
    }
}
