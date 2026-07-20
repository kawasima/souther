package net.unit8.souther.compiler.frontend;

import net.unit8.souther.compiler.ast.Ast;
import net.unit8.souther.compiler.cst.CstError;
import net.unit8.souther.compiler.cst.CstParser;
import net.unit8.souther.compiler.cst.LineIndex;
import net.unit8.souther.compiler.diag.CompileException;
import net.unit8.souther.compiler.diag.Diagnostic;

/**
 * The front-end facade: source → lossless CST → the compiler's {@link Ast}. This is the single
 * entry the compiler pipeline calls in place of the older {@code syntax.Parser}. Syntax errors are
 * accumulated by the CST parser rather than thrown; at this batch boundary the first one is raised
 * as a {@link CompileException}, so callers keep their fail-fast behaviour while the same CST feeds
 * the formatter and the LSP without a throw.
 */
public final class CstFrontend {

    private CstFrontend() {
    }

    /** Parses one compilation unit, naming a header-less source {@code defaultModuleName} (a
     * {@code null} default makes the {@code module} header required). */
    public static Ast.Module parse(String source, String defaultModuleName) {
        CstParser.Result result = CstParser.parse(source);
        if (!result.errors().isEmpty()) {
            throw firstError(source, result.errors().get(0));
        }
        return AstBuilder.build(result.root(), source, defaultModuleName);
    }

    /** As {@link #parse(String, String)} with the default module name {@code Main}. */
    public static Ast.Module parse(String source) {
        return parse(source, "Main");
    }

    private static CompileException firstError(String source, CstError e) {
        LineIndex lines = new LineIndex(source);
        Diagnostic diag = Diagnostic.of(null, e.messageKey()).title("parse.title")
                .at(lines.posOf(e.offset()), e.width()).args(e.args()).build();
        return CompileException.of(diag, e.legacyMessage());
    }
}
