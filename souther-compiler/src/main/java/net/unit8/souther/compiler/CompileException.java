package net.unit8.souther.compiler;

/**
 * A compile error with a source position. Carries an error code (e.g. {@code E1101})
 * when one applies (spec section 22), otherwise a bare message for lex/parse errors.
 */
public class CompileException extends RuntimeException {

    private final SourcePos pos;
    private final String code;

    public CompileException(SourcePos pos, String message) {
        this(pos, null, message);
    }

    public CompileException(SourcePos pos, String code, String message) {
        super(format(pos, code, message));
        this.pos = pos;
        this.code = code;
    }

    public SourcePos pos() {
        return pos;
    }

    public String code() {
        return code;
    }

    private static String format(SourcePos pos, String code, String message) {
        String where = pos == null ? "" : pos + " ";
        String c = code == null ? "" : code + ": ";
        return where + c + message;
    }
}
