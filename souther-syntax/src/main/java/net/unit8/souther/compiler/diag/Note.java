package net.unit8.souther.compiler.diag;

/**
 * A hint line under a diagnostic (Elm's {@code Hint:}). {@code messageKey} is a catalog key resolved
 * against the selected locale; {@code args} fill its placeholders.
 */
public record Note(String messageKey, Object[] args) {
}
