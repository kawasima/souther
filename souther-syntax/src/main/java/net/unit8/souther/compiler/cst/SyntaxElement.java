package net.unit8.souther.compiler.cst;

/**
 * A red-tree element: a positioned view over a {@link Green} node or token. The red layer adds
 * what green deliberately omits — an absolute source offset and a parent pointer — computed lazily
 * as the tree is walked, so the underlying green tree stays shareable.
 */
public sealed interface SyntaxElement permits SyntaxNode, SyntaxToken {

    SyntaxKind kind();

    /** The parent node, or {@code null} at the root. */
    SyntaxNode parent();

    /** The absolute start offset in source characters (0-based). */
    int start();

    /** The absolute end offset (exclusive). */
    int end();

    /** The source text this element covers, trivia included. */
    String text();
}
