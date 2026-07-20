package net.unit8.souther.compiler.cst;

import java.util.List;

/** A green internal node: a kind, its total width, and its ordered children (nodes and tokens,
 * trivia included). Immutable and position-independent. */
public record GreenNode(SyntaxKind kind, int width, List<Green> children) implements Green {

    @Override
    public void appendText(StringBuilder sb) {
        for (Green c : children) {
            c.appendText(sb);
        }
    }
}
