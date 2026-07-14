package net.unit8.souther.compiler.syntax;

/** The lexical token kinds recognised by the slice-1 lexer. */
public enum TokenType {
    // keywords
    MODULE, DATA, INVARIANT, DECODER, ENCODER, FROM, AS, LET, REQUIRE, ELSE, TRUE, FALSE,
    BEHAVIOR, REQUIRED, CONSTRUCTS, MATCH, CASE,
    // literals and identifiers
    IDENT, INT_LIT, STRING_LIT,
    // punctuation
    LBRACE, RBRACE, LPAREN, RPAREN, COLON, COMMA, DOT, ASSIGN, PIPE, ARROW, LARROW, FATARROW, GTGT,
    // operators
    EQ, NE, LT, LE, GT, GE, AND, OR, NOT,
    // end of input
    EOF
}
