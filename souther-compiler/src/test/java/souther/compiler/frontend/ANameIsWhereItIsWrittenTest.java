package souther.compiler.frontend;

import souther.compiler.WhereItSits;
import souther.compiler.source.SourceId;

import org.junit.jupiter.api.Test;
import souther.compiler.ast.Ast;
import souther.compiler.cst.CstParser;
import souther.compiler.cst.LineIndex;
import souther.compiler.cst.SyntaxElement;
import souther.compiler.cst.SyntaxKind;
import souther.compiler.cst.SyntaxNode;
import souther.compiler.cst.SyntaxToken;
import souther.compiler.diag.SourcePos;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A binder that says where its name is written is claiming a place in the source, and this reads the
 * claim back against the source it was parsed from.
 *
 * <p>The claim is two halves handed over separately — a spelling and a position — and nothing in the
 * tree can check they came from the same token, because the tree has no text. So it is checked here,
 * over every binding form the language has, rather than by each of the twenty-odd places that build
 * a binder remembering to be careful.
 *
 * <p>Both ways of being wrong have been shipped on this branch. A desugaring binds what it is
 * rewriting to a name of its own and anchors it on the form it came from — {@code .v} becomes a
 * parameter called {@code $g0} anchored at the {@code .} — and a reader answered with one of those is
 * answered about a name that is not in the file, at a width that is not its: renaming from it wrote
 * {@code .v,} over. The other way round, a lowering that drops the token it had leaves a name the
 * author did write with no place to be found at, and go-to-definition on it answers nothing.
 */
class ANameIsWhereItIsWrittenTest {

    /** Every form that binds a name, in one module: parameters written and patterned, a lambda's
     * parameters, a block `let`, a destructuring `let`, an attempted construction's `as`, and a
     * `match` arm's `as`, field destructuring and constructor destructuring. */
    private static final String EVERY_BINDING_FORM = """
            module a exposing ( Amount, Pair, Shape, Round, Flat, sum, pick, tag, take, held )

            data Amount = Int
                invariant value >= 0

            data Pair = { left: Int, right: Int }
            data Round = { r: Int }
            data Flat = { f: Int }
            data Shape = Round | Flat

            behavior sum : (ps: List<Pair>) -> Int
            let sum (ps) = List.fold((acc, p) -> acc + p.left + p.right, 0, ps)

            behavior pick : (p: Pair) -> Int
            let pick ({ left = l, right }) = l + right

            behavior tag : (s: Shape) -> Int
            let tag (s) = match s with
                | Round as w -> w.r
                | Flat { f } -> f

            behavior take : (n: Int) -> Amount
            let take (n) = {
                let doubled = n * 2
                if Amount(doubled) as ok then ok.value else 0
            }

            behavior held : (a: Amount) -> Int
            let held (Amount(inner)) = inner
            """;

    private static Ast.Module parsed() {
        return CstFrontend.parseWithSlices(EVERY_BINDING_FORM, "a", new SourceId("a.sou")).module();
    }

    private static SyntaxNode tree() {
        return CstParser.parse(EVERY_BINDING_FORM).root();
    }

    /** Every binder anywhere in the tree, found by walking the records rather than by a visitor each
     * new node kind would have to be added to. */
    private static List<Ast.Binder> everyBinder(Object node) {
        List<Ast.Binder> out = new ArrayList<>();
        collect(node, out, new IdentityHashMap<>());
        return out;
    }

    private static void collect(Object node, List<Ast.Binder> out, Map<Object, Boolean> seen) {
        if (node == null || seen.put(node, Boolean.TRUE) != null) {
            return;
        }
        if (node instanceof Ast.Binder binder) {
            out.add(binder);
        }
        switch (node) {
            case List<?> list -> list.forEach(e -> collect(e, out, seen));
            case Optional<?> maybe -> maybe.ifPresent(e -> collect(e, out, seen));
            // every record, not only the ones that are `Ast`: a body is held in an `FnBody`, an
            // example's rows in their own records, and a walk that stopped at `Ast` would report a
            // module full of bindings as a module with five
            case Record record -> {
                for (RecordComponent c : record.getClass().getRecordComponents()) {
                    try {
                        collect(c.getAccessor().invoke(record), out, seen);
                    } catch (ReflectiveOperationException e) {
                        throw new AssertionError("could not read " + c.getName(), e);
                    }
                }
            }
            default -> { /* a spelling, a number, a flag: nothing that holds a binder */ }
        }
    }

    /** The identifier written at {@code at}, or null where no identifier starts there. */
    private static SyntaxToken identAt(SyntaxNode node, LineIndex lines, SourcePos at) {
        int offset = lines.offsetOf(WhereItSits.in(EVERY_BINDING_FORM, at).line() - 1, WhereItSits.in(EVERY_BINDING_FORM, at).column() - 1);
        for (SyntaxElement e : node.children()) {
            if (e instanceof SyntaxNode child) {
                if (child.start() <= offset && offset < child.end()) {
                    SyntaxToken found = identAt(child, lines, at);
                    if (found != null) {
                        return found;
                    }
                }
            } else if (e instanceof SyntaxToken t
                    && t.kind() == SyntaxKind.IDENT && t.start() == offset) {
                return t;
            }
        }
        return null;
    }

    @Test
    void theModuleReallyHasEveryFormInIt() {
        assertTrue(everyBinder(parsed()).size() >= 14,
                "a fixture that binds little checks little: " + everyBinder(parsed()));
    }

    @Test
    void everyBinderThatNamesAPlaceIsOnAnIdentifierSpelledThatWay() {
        LineIndex lines = new LineIndex(EVERY_BINDING_FORM);
        SyntaxNode root = tree();
        List<String> wrong = new ArrayList<>();
        for (Ast.Binder binder : everyBinder(parsed())) {
            if (binder.namePos() == null) {
                continue;   // says it is written nowhere, and claims no place to be wrong about
            }
            SyntaxToken written = identAt(root, lines, binder.namePos());
            if (written == null || !written.text().equals(binder.name())) {
                wrong.add("`" + binder.name() + "` claims " + binder.namePos() + ", which holds "
                        + (written == null ? "no identifier" : "`" + written.text() + "`"));
            }
        }
        assertEquals(List.of(), wrong,
                "a binding that names a place has to be the name written there");
    }

    @Test
    void aNameNoOneWroteSaysSo() {
        List<String> claiming = new ArrayList<>();
        for (Ast.Binder binder : everyBinder(parsed())) {
            if (binder.name().startsWith("$") && binder.namePos() != null) {
                claiming.add(binder.name() + " at " + binder.namePos());
            }
        }
        assertEquals(List.of(), claiming,
                "the names a desugaring makes for itself are written nowhere");
    }

    @Test
    void andEveryNameTheAuthorDidWriteSaysWhere() {
        List<String> silent = new ArrayList<>();
        for (Ast.Binder binder : everyBinder(parsed())) {
            if (!binder.name().startsWith("$") && binder.namePos() == null) {
                silent.add(binder.name() + " anchored at " + binder.pos());
            }
        }
        assertEquals(List.of(), silent,
                "a name the author wrote is somewhere, and a reader has to be able to be sent there");
    }
}
