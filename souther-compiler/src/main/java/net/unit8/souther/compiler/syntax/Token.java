package net.unit8.souther.compiler.syntax;

import net.unit8.souther.compiler.SourcePos;

/**
 * A lexical token: its kind, the exact source text (for identifiers/literals), and
 * where it started.
 */
public record Token(TokenType type, String text, SourcePos pos) {
    @Override
    public String toString() {
        return type + "(" + text + ")@" + pos;
    }
}
