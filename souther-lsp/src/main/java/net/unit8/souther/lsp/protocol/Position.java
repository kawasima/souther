package net.unit8.souther.lsp.protocol;

/** An LSP text position: a 0-based line and a 0-based UTF-16 character offset within that line. */
public record Position(int line, int character) {
}
