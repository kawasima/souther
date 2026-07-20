package net.unit8.souther.compiler.diag;

/**
 * A 1-based source position (line and column). Every AST node and token carries one so
 * that compile errors can point at the source (spec section 28).
 */
public record SourcePos(int line, int column) {
    @Override
    public String toString() {
        return line + ":" + column;
    }
}
