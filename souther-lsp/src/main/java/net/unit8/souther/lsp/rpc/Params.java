package net.unit8.souther.lsp.rpc;

import net.unit8.souther.lsp.protocol.Position;

/** The inbound LSP request/notification payloads the server decodes (only the fields it uses). */
public final class Params {

    private Params() {
    }

    /** {@code textDocument/didOpen}: the opened document's uri and full text. */
    public record DidOpen(String uri, String text) {
    }

    /** {@code textDocument/didChange} under full-sync: the uri and the whole new text. */
    public record DidChange(String uri, String text) {
    }

    /** A request that names a document ({@code didClose}, {@code documentSymbol},
     * {@code semanticTokens/full}). */
    public record DocRef(String uri) {
    }

    /** A position-bearing request ({@code hover}, {@code definition}). */
    public record PositionParams(String uri, Position position) {
    }
}
