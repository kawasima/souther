package net.unit8.souther.compiler.diag;

/**
 * A compile error with a source position. Carries an error code (e.g. {@code E1101})
 * when one applies (spec section 22), otherwise a bare message for lex/parse errors.
 *
 * <p>The exception now wraps a structured {@link Diagnostic}. {@link #getMessage()} still returns the
 * one-line {@code line:col code: message} form (so existing callers and tests are unchanged), while a
 * renderer can take {@link #diagnostic()} and produce the Elm-style snippet or JSON. A site that has
 * not been moved onto a catalog key throws with a literal message, wrapped as a
 * {@link Diagnostic#literal literal} diagnostic.
 */
public class CompileException extends RuntimeException {

    private final transient Diagnostic diagnostic;

    public CompileException(SourcePos pos, String message) {
        this(pos, null, message);
    }

    public CompileException(SourcePos pos, String code, String message) {
        this(Diagnostic.literal(pos, code, message), format(pos, code, message));
    }

    /** Throws with a fully structured diagnostic (a migrated site). */
    public CompileException(Diagnostic diagnostic, String legacyMessage) {
        super(legacyMessage);
        this.diagnostic = diagnostic;
    }

    /**
     * A migrated throw site: the structured {@code diagnostic} drives the Elm-style / JSON rendering,
     * while {@code legacyBody} keeps {@link #getMessage()} returning the same one-line form as before
     * (so callers and tests that read the message text are unchanged).
     */
    public static CompileException of(Diagnostic diagnostic, String legacyBody) {
        return new CompileException(diagnostic,
                format(diagnostic.pos(), diagnostic.code(), legacyBody));
    }

    public SourcePos pos() {
        return diagnostic == null ? null : diagnostic.pos();
    }

    public String code() {
        return diagnostic == null ? null : diagnostic.code();
    }

    /** The structured diagnostic behind this error, for a renderer. */
    public Diagnostic diagnostic() {
        return diagnostic;
    }

    private static String format(SourcePos pos, String code, String message) {
        String where = pos == null ? "" : pos + " ";
        String c = code == null ? "" : code + ": ";
        return where + c + message;
    }
}
