package net.unit8.souther.compiler.cst;

/**
 * A syntax problem recorded during lexing or parsing, positioned by absolute offset so it does not
 * depend on a {@link LineIndex} yet. A driver turns it into a
 * {@link net.unit8.souther.compiler.diag.Diagnostic} once the line index is known.
 *
 * <p>{@code messageKey}/{@code args} feed the localized catalog; {@code legacyMessage} preserves
 * the English text the pre-CST throw sites produced, so existing callers and tests see the same
 * {@code getMessage()}.
 */
public record CstError(int offset, int width, String messageKey, String legacyMessage, Object[] args) {

    public static CstError of(int offset, int width, String messageKey, String legacyMessage,
                              Object... args) {
        return new CstError(offset, width, messageKey, legacyMessage, args);
    }
}
