package net.unit8.souther.compiler.cst;

/** A positioned leaf: a {@link GreenToken} with its absolute offset and parent. */
public final class SyntaxToken implements SyntaxElement {

    private final GreenToken green;
    private final SyntaxNode parent;
    private final int offset;

    SyntaxToken(GreenToken green, SyntaxNode parent, int offset) {
        this.green = green;
        this.parent = parent;
        this.offset = offset;
    }

    public GreenToken green() {
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

    public boolean isTrivia() {
        return green.kind().isTrivia();
    }

    @Override
    public String toString() {
        return kind() + "(" + green.text() + ")@" + offset;
    }
}
