package net.unit8.souther.lsp.protocol;

/**
 * An LSP diagnostic: a range, a severity (1 = error, 2 = warning, 3 = information, 4 = hint), a
 * stable code (the compiler's {@code E1301}-style identifier, or {@code null}), and the message.
 */
public record LspDiagnostic(Range range, int severity, String code, String message) {

    public static final int ERROR = 1;
    public static final int WARNING = 2;
    public static final int INFORMATION = 3;
    public static final int HINT = 4;
}
