package net.unit8.souther.compiler.cst;

/** A green leaf: its kind and the exact source text it covers (trivia tokens included). */
public record GreenToken(SyntaxKind kind, String text) implements Green {

    @Override
    public int width() {
        return text.length();
    }

    @Override
    public void appendText(StringBuilder sb) {
        sb.append(text);
    }
}
