package net.unit8.souther.compiler.diag;

/**
 * A message argument that is itself localized at render time. A throw site has no locale, so a
 * sub-phrase that must follow the reader's language — a token category like "a name", not a literal
 * symbol like {@code `:`} — is passed as a {@code Localizable} and resolved by {@link Messages#get}
 * against the selected locale before it is substituted into the surrounding message.
 */
public record Localizable(String key, Object[] args) {

    public static Localizable of(String key, Object... args) {
        return new Localizable(key, args);
    }
}
