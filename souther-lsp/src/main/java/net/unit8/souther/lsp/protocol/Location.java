package net.unit8.souther.lsp.protocol;

/** An LSP location: a {@code range} within the document named by {@code uri}. Go-to-definition and
 * references return these, so a target in another file carries its own URI. */
public record Location(String uri, Range range) {
}
