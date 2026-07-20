package net.unit8.souther.lsp.protocol;

import java.util.List;

/**
 * An LSP document symbol (an outline entry): its name, its {@code kind} (an LSP SymbolKind number),
 * the {@code range} covering the whole definition, the {@code selectionRange} covering just the
 * name, and any nested children (a data type's fields).
 */
public record DocumentSymbol(String name, int kind, Range range, Range selectionRange,
                             List<DocumentSymbol> children) {

    // The LSP SymbolKind numbers this server uses.
    public static final int CLASS = 5;
    public static final int FIELD = 8;
    public static final int INTERFACE = 11;
    public static final int FUNCTION = 12;
}
