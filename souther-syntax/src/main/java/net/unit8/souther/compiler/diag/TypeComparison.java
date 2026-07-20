package net.unit8.souther.compiler.diag;

/**
 * A found-vs-expected pair for a type error, rendered as two indented blocks (Elm's "This value is:
 * / But it needs to be:"). The type strings are locale-independent — only the framing prose around
 * them follows the locale — so both the human and JSON renderers show the same types.
 */
public record TypeComparison(String actualType, String expectedType) {
}
