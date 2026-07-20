package net.unit8.souther.compiler.cst;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A positioned view over a {@link GreenNode}: the red layer of the tree. Absolute offsets and
 * parent pointers are computed here, lazily, from the position-independent green node. This is the
 * one type the formatter, LSP, and CST→AST lowering walk.
 */
public final class SyntaxNode implements SyntaxElement {

    private final GreenNode green;
    private final SyntaxNode parent;
    private final int offset;
    private List<SyntaxElement> children;   // computed on first access

    private SyntaxNode(GreenNode green, SyntaxNode parent, int offset) {
        this.green = green;
        this.parent = parent;
        this.offset = offset;
    }

    /** Wraps a green root as the red root of a tree (offset 0, no parent). */
    public static SyntaxNode root(GreenNode green) {
        return new SyntaxNode(green, null, 0);
    }

    public GreenNode green() {
        return green;
    }

    @Override
    public SyntaxKind kind() {
        return green.kind();
    }

    @Override
    public SyntaxNode parent() {
        return parent;
    }

    @Override
    public int start() {
        return offset;
    }

    @Override
    public int end() {
        return offset + green.width();
    }

    @Override
    public String text() {
        return green.text();
    }

    /** The child elements (nodes, tokens, trivia) in source order, each positioned. */
    public List<SyntaxElement> children() {
        if (children == null) {
            List<SyntaxElement> out = new ArrayList<>(green.children().size());
            int at = offset;
            for (Green c : green.children()) {
                if (c instanceof GreenNode n) {
                    out.add(new SyntaxNode(n, this, at));
                } else {
                    out.add(new SyntaxToken((GreenToken) c, this, at));
                }
                at += c.width();
            }
            children = List.copyOf(out);
        }
        return children;
    }

    /** The child nodes (skipping tokens and trivia). */
    public List<SyntaxNode> childNodes() {
        List<SyntaxNode> out = new ArrayList<>();
        for (SyntaxElement e : children()) {
            if (e instanceof SyntaxNode n) {
                out.add(n);
            }
        }
        return out;
    }

    /** The first child node of the given kind, if any. */
    public Optional<SyntaxNode> child(SyntaxKind kind) {
        for (SyntaxElement e : children()) {
            if (e instanceof SyntaxNode n && n.kind() == kind) {
                return Optional.of(n);
            }
        }
        return Optional.empty();
    }

    /** The first direct child token of the given kind (trivia included), if any. */
    public Optional<SyntaxToken> token(SyntaxKind kind) {
        for (SyntaxElement e : children()) {
            if (e instanceof SyntaxToken t && t.kind() == kind) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }

    @Override
    public String toString() {
        return kind() + "@" + start() + ".." + end();
    }
}
