package net.unit8.souther.compiler.diag;

import java.util.Locale;

/** Turns a {@link Diagnostic} into text: {@link HumanRenderer} for people, {@link JsonRenderer} for
 * tools. The source snippet comes from {@code src}, which may be null when the source is unavailable
 * (e.g. a multi-file build); the renderer then omits the quoted line. */
public interface DiagnosticRenderer {

    String render(Diagnostic d, SourceContext src, Locale locale);

    /** The message body, from the catalog key or the compatibility literal. */
    static String body(Diagnostic d, Locale locale) {
        if (d.literalMessage() != null) {
            return d.literalMessage();
        }
        if (d.messageKey() != null) {
            return Messages.get(d.messageKey(), locale, d.args());
        }
        return "";
    }
}
