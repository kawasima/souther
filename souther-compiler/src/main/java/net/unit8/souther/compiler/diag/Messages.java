package net.unit8.souther.compiler.diag;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * The diagnostic message catalog. Prose lives in {@code messages_ja.properties} (the default) and
 * {@code messages.properties} (English base); a key missing from the Japanese bundle falls back to
 * the English base automatically. A key missing from both renders as the key itself, so a
 * not-yet-migrated site never crashes the compiler.
 *
 * <p>Locale is resolved once, highest precedence first: an explicit {@code --lang} value, the
 * {@code SOUTHER_LANG} environment variable, the JVM default locale, then Japanese.
 */
public final class Messages {

    private static final String BUNDLE = "net.unit8.souther.compiler.diag.messages";

    private Messages() {
    }

    /** Resolves the locale from an explicit language tag (from {@code --lang}); null means "not set". */
    public static Locale resolveLocale(String explicit) {
        String tag = explicit;
        if (tag == null || tag.isBlank()) {
            tag = System.getenv("SOUTHER_LANG");
        }
        if (tag != null && !tag.isBlank()) {
            return Locale.forLanguageTag(tag.replace('_', '-'));
        }
        Locale def = Locale.getDefault();
        if (def != null && !def.getLanguage().isBlank()) {
            return def;
        }
        return Locale.JAPANESE;
    }

    /** The default locale when none is chosen: Japanese. */
    public static Locale defaultLocale() {
        return Locale.JAPANESE;
    }

    /** Looks up {@code key} for {@code locale} and fills {@code args}. Missing key → the key itself. */
    public static String get(String key, Locale locale, Object... args) {
        String template = lookup(key, locale);
        if (template == null) {
            return key;
        }
        if (args == null || args.length == 0) {
            return template;
        }
        Object[] resolved = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            resolved[i] = args[i] instanceof Localizable l ? get(l.key(), locale, l.args()) : args[i];
        }
        return new MessageFormat(template, locale).format(resolved);
    }

    /** Whether the catalog defines {@code key} for {@code locale} (or its English base). */
    public static boolean has(String key, Locale locale) {
        return lookup(key, locale) != null;
    }

    // No-fallback control so an explicit `--lang en` resolves to the English base rather than being
    // diverted to the JVM default locale's bundle (which ResourceBundle would otherwise insert into
    // the candidate chain). The base bundle stays the final candidate, so a key missing from a
    // locale still falls back to English.
    private static final ResourceBundle.Control CONTROL =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

    private static String lookup(String key, Locale locale) {
        try {
            ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE,
                    locale == null ? defaultLocale() : locale, CONTROL);
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return null;
        }
    }
}
