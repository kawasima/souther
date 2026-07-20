package net.unit8.souther.lsp;

import net.unit8.souther.lsp.analysis.Analyzer;
import net.unit8.souther.lsp.analysis.DocumentStore;
import net.unit8.souther.lsp.protocol.DocumentSymbol;
import net.unit8.souther.lsp.protocol.Hover;
import net.unit8.souther.lsp.protocol.LspDiagnostic;
import net.unit8.souther.lsp.protocol.Position;
import net.unit8.souther.lsp.protocol.Range;
import net.unit8.souther.lsp.rpc.InboundDecoders;
import net.unit8.souther.lsp.rpc.Params;
import net.unit8.souther.lsp.transport.MessageConnection;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A hand-rolled LSP server over a {@link MessageConnection}. It reads JSON-RPC messages, dispatches
 * by method, and answers requests / publishes diagnostics. Inbound payloads are decoded with Raoh
 * ({@link InboundDecoders}); outbound trees are built as maps and serialised with Jackson. The
 * language work is delegated to the {@link Analyzer}, which knows nothing of the protocol.
 */
public final class LspServer {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final MessageConnection conn;
    private final DocumentStore documents = new DocumentStore();
    private final Analyzer analyzer = new Analyzer();

    public LspServer(MessageConnection conn) {
        this.conn = conn;
    }

    public static void main(String[] args) {
        new LspServer(new MessageConnection(System.in, System.out)).run();
    }

    /** Reads and dispatches messages until end of input or an {@code exit} notification. */
    public void run() {
        String message;
        while ((message = conn.read()) != null) {
            JsonNode m;
            try {
                m = JSON.readTree(message);
            } catch (RuntimeException e) {
                continue;   // a malformed frame is dropped, not fatal
            }
            JsonNode methodNode = m.get("method");
            if (methodNode == null || methodNode.isNull()) {
                continue;   // a response to a server-initiated request; nothing to do
            }
            if (dispatch(methodNode.asString(), m.get("id"), m.get("params"))) {
                return;     // exit
            }
        }
    }

    /** Returns true when the server should stop (on {@code exit}). */
    private boolean dispatch(String method, JsonNode id, JsonNode params) {
        switch (method) {
            case "initialize" -> respond(id, initializeResult());
            case "initialized", "$/setTrace", "workspace/didChangeConfiguration" -> { /* no-op */ }
            case "textDocument/didOpen" -> InboundDecoders.decode(InboundDecoders.DID_OPEN, params)
                    .ifPresent(p -> { documents.open(p.uri(), p.text()); publishDiagnostics(p.uri()); });
            case "textDocument/didChange" -> InboundDecoders.decode(InboundDecoders.DID_CHANGE, params)
                    .ifPresent(p -> { documents.change(p.uri(), p.text()); publishDiagnostics(p.uri()); });
            case "textDocument/didClose" -> InboundDecoders.decode(InboundDecoders.DOC_REF, params)
                    .ifPresent(p -> { documents.close(p.uri()); clearDiagnostics(p.uri()); });
            case "textDocument/documentSymbol" -> respond(id, documentSymbols(params));
            case "textDocument/semanticTokens/full" -> respond(id, semanticTokens(params));
            case "textDocument/hover" -> respond(id, hover(params));
            case "textDocument/definition" -> respond(id, definition(params));
            case "shutdown" -> respond(id, null);
            case "exit" -> { return true; }
            default -> {
                if (id != null && !id.isNull()) {
                    respondError(id, -32601, "method not found: " + method);
                }
            }
        }
        return false;
    }

    // --- capabilities ---

    private Map<String, Object> initializeResult() {
        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("textDocumentSync", 1);   // 1 = full document sync
        capabilities.put("documentSymbolProvider", true);
        capabilities.put("hoverProvider", true);
        capabilities.put("definitionProvider", true);
        Map<String, Object> semanticTokens = new LinkedHashMap<>();
        semanticTokens.put("legend", Map.of("tokenTypes", Analyzer.TOKEN_TYPES,
                "tokenModifiers", List.of()));
        semanticTokens.put("full", true);
        capabilities.put("semanticTokensProvider", semanticTokens);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("capabilities", capabilities);
        result.put("serverInfo", Map.of("name", "souther-lsp", "version", "0.1.0"));
        return result;
    }

    // --- document symbols ---

    private List<Object> documentSymbols(JsonNode params) {
        String uri = InboundDecoders.decode(InboundDecoders.DOC_REF, params)
                .map(Params.DocRef::uri).orElse(null);
        String text = uri == null ? null : documents.get(uri);
        if (text == null) {
            return List.of();
        }
        List<Object> out = new ArrayList<>();
        for (DocumentSymbol s : analyzer.documentSymbols(text)) {
            out.add(symbolJson(s));
        }
        return out;
    }

    private Object symbolJson(DocumentSymbol s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", s.name());
        m.put("kind", s.kind());
        m.put("range", rangeJson(s.range()));
        m.put("selectionRange", rangeJson(s.selectionRange()));
        if (!s.children().isEmpty()) {
            List<Object> children = new ArrayList<>();
            for (DocumentSymbol child : s.children()) {
                children.add(symbolJson(child));
            }
            m.put("children", children);
        }
        return m;
    }

    // --- hover / definition ---

    private Object hover(JsonNode params) {
        Params.PositionParams p = InboundDecoders.decode(InboundDecoders.POSITION_PARAMS, params)
                .orElse(null);
        String text = p == null ? null : documents.get(p.uri());
        if (text == null) {
            return null;
        }
        Hover h = analyzer.hover(text, p.position()).orElse(null);
        if (h == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("contents", Map.of("kind", "markdown", "value", h.contents()));
        m.put("range", rangeJson(h.range()));
        return m;
    }

    private Object definition(JsonNode params) {
        Params.PositionParams p = InboundDecoders.decode(InboundDecoders.POSITION_PARAMS, params)
                .orElse(null);
        String text = p == null ? null : documents.get(p.uri());
        if (text == null) {
            return null;
        }
        return analyzer.definition(text, p.position())
                .<Object>map(range -> Map.of("uri", p.uri(), "range", rangeJson(range)))
                .orElse(null);
    }

    // --- semantic tokens ---

    private Object semanticTokens(JsonNode params) {
        String uri = InboundDecoders.decode(InboundDecoders.DOC_REF, params)
                .map(Params.DocRef::uri).orElse(null);
        String text = uri == null ? null : documents.get(uri);
        if (text == null) {
            return Map.of("data", List.of());
        }
        List<Integer> data = new ArrayList<>();
        for (int value : analyzer.semanticTokens(text)) {
            data.add(value);
        }
        return Map.of("data", data);
    }

    // --- diagnostics ---

    private void publishDiagnostics(String uri) {
        String text = documents.get(uri);
        if (text == null) {
            return;
        }
        List<Object> items = new ArrayList<>();
        for (LspDiagnostic d : analyzer.diagnostics(text)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("range", rangeJson(d.range()));
            item.put("severity", d.severity());
            if (d.code() != null) {
                item.put("code", d.code());
            }
            item.put("source", "souther");
            item.put("message", d.message());
            items.add(item);
        }
        notify("textDocument/publishDiagnostics", Map.of("uri", uri, "diagnostics", items));
    }

    private void clearDiagnostics(String uri) {
        notify("textDocument/publishDiagnostics", Map.of("uri", uri, "diagnostics", List.of()));
    }

    private static Map<String, Object> rangeJson(Range r) {
        return Map.of("start", positionJson(r.start()), "end", positionJson(r.end()));
    }

    private static Map<String, Object> positionJson(Position p) {
        return Map.of("line", p.line(), "character", p.character());
    }

    // --- JSON-RPC framing of responses / notifications ---

    private void respond(JsonNode id, Object result) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("jsonrpc", "2.0");
        message.put("id", id);
        message.put("result", result);
        conn.write(JSON.writeValueAsString(message));
    }

    private void respondError(JsonNode id, int code, String text) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("jsonrpc", "2.0");
        message.put("id", id);
        message.put("error", Map.of("code", code, "message", text));
        conn.write(JSON.writeValueAsString(message));
    }

    private void notify(String method, Object params) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("jsonrpc", "2.0");
        message.put("method", method);
        message.put("params", params);
        conn.write(JSON.writeValueAsString(message));
    }
}
