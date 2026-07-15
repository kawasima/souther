package net.unit8.souther.compiler.syntax;

/** The lexical token kinds recognised by the slice-1 lexer. */
public enum TokenType {
    // keywords
    MODULE, IMPORT, EXPOSING, DATA, INVARIANT, DECODER, ENCODER, FROM, AS, LET, REQUIRE, ELSE,
    TRUE, FALSE, IF, THEN, BEHAVIOR, REQUIRED, CONSTRUCTS, MATCH, CASE, INCLUDE,
    // literals and identifiers
    IDENT, INT_LIT, STRING_LIT,
    // punctuation
    LBRACE, RBRACE, LPAREN, RPAREN, COLON, COMMA, DOT, DOTDOT, ASSIGN, PIPE, ARROW, LARROW, FATARROW, GTGT,
    QUESTION,
    // operators
    EQ, NE, LT, LE, GT, GE, AND, OR, NOT, PLUS, MINUS, STAR,
    // end of input
    EOF
}
