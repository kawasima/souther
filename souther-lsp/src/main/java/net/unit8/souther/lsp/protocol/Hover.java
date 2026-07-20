package net.unit8.souther.lsp.protocol;

/** An LSP hover: markdown {@code contents} and the {@code range} the hover applies to. */
public record Hover(String contents, Range range) {
}
