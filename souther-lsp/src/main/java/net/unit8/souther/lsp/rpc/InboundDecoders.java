package net.unit8.souther.lsp.rpc;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Result;
import net.unit8.raoh.decode.Decoder;
import net.unit8.souther.lsp.protocol.Position;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Optional;

import static net.unit8.raoh.json.JsonDecoders.combine;
import static net.unit8.raoh.json.JsonDecoders.field;
import static net.unit8.raoh.json.JsonDecoders.int_;
import static net.unit8.raoh.json.JsonDecoders.list;
import static net.unit8.raoh.json.JsonDecoders.string;

/**
 * Raoh decoders for the inbound LSP payloads. This is where the language server dogfoods Raoh: each
 * client message is validated and shaped by a declarative decoder over the Jackson JSON tree, rather
 * than by ad-hoc {@code node.get(...)} navigation.
 */
public final class InboundDecoders {

    private InboundDecoders() {
    }

    /** {@code { textDocument: { uri, text } }} */
    public static final Decoder<JsonNode, Params.DidOpen> DID_OPEN =
            field("textDocument",
                    combine(field("uri", string()), field("text", string())).map(Params.DidOpen::new));

    /** {@code { textDocument: { uri }, contentChanges: [ { text }, ... ] }} — full sync, so the last
     * change carries the whole document. */
    public static final Decoder<JsonNode, Params.DidChange> DID_CHANGE =
            combine(field("textDocument", field("uri", string())),
                    field("contentChanges", list(field("text", string()))))
                    .map((uri, texts) -> new Params.DidChange(uri, texts.get(texts.size() - 1)));

    /** {@code { textDocument: { uri } }} */
    public static final Decoder<JsonNode, Params.DocRef> DOC_REF =
            field("textDocument", field("uri", string())).map(Params.DocRef::new);

    /** {@code { textDocument: { uri }, position: { line, character } }} */
    public static final Decoder<JsonNode, Params.PositionParams> POSITION_PARAMS =
            combine(field("textDocument", field("uri", string())),
                    field("position",
                            combine(field("line", int_()), field("character", int_())).map(Position::new)))
                    .map(Params.PositionParams::new);

    /** Decodes {@code node} with {@code decoder}, or empty on any validation failure — a malformed
     * request is dropped rather than crashing the server. */
    public static <T> Optional<T> decode(Decoder<JsonNode, T> decoder, JsonNode node) {
        if (node == null) {
            return Optional.empty();
        }
        Result<T> result = decoder.decode(node);
        if (result instanceof Ok<T> ok) {
            return Optional.of(ok.value());
        }
        return Optional.empty();
    }
}
