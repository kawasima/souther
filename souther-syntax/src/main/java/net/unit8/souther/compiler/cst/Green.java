package net.unit8.souther.compiler.cst;

import java.util.List;

/**
 * A node in the immutable, position-independent green tree (the rowan/Roslyn model). A green
 * element knows its {@link #kind()} and its {@link #width()} in source characters, but not its
 * absolute position — that is supplied lazily by the red layer ({@link SyntaxNode}). Green trees
 * are shareable and, in a later slice, reusable across incremental re-parses.
 */
public sealed interface Green permits GreenNode, GreenToken {

    SyntaxKind kind();

    /** The width of this element in source characters (UTF-16 code units, i.e. Java {@code char}s). */
    int width();

    /** Appends this element's source text (including trivia) to {@code sb}. */
    void appendText(StringBuilder sb);

    /** The full source text this element spans, trivia included. Concatenating the root's text
     * reproduces the original source exactly — the lossless invariant. */
    default String text() {
        StringBuilder sb = new StringBuilder();
        appendText(sb);
        return sb.toString();
    }

    /** A green internal node: a kind plus ordered children (nodes, tokens, and trivia). */
    static GreenNode node(SyntaxKind kind, List<Green> children) {
        int w = 0;
        for (Green c : children) {
            w += c.width();
        }
        return new GreenNode(kind, w, List.copyOf(children));
    }

    /** A green leaf: a kind plus the exact source text it covers. */
    static GreenToken token(SyntaxKind kind, String text) {
        return new GreenToken(kind, text);
    }
}
