package net.unit8.souther.lsp.protocol;

/** An LSP range: a start position (inclusive) and an end position (exclusive). */
public record Range(Position start, Position end) {
}
