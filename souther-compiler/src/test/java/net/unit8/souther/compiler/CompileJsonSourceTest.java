package net.unit8.souther.compiler;

import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Result;
import net.unit8.raoh.decode.Decoder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Per-source decoder generation (spec 10.6): each data whose shape supports it gets a
 * {@code jsonDecoder()} (reads a Jackson {@code JsonNode}) and a {@code recordDecoder()}
 * (reads a jOOQ {@code Record}) beside the neutral {@code decoder()}. This exercises the
 * JSON source end-to-end; the jOOQ {@code $DecRecord} is loaded to verify its bytecode.
 */
class CompileJsonSourceTest {

    private static final String MODULE = """
            module demo
            import String ( length )

            data Account = {
                id: String
                , balance: Int
                , owner: String
            } invariant length(id) > 0

            data Submitted = { note: String }
            data Rejected = { reason: String }
            data Application = Submitted | Rejected
            """;

    private final JsonMapper mapper = JsonMapper.builder().build();

    private BytesClassLoader compile() {
        return new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
    }

    @Test
    void jsonDecoderReadsAJsonNodeObject() throws Exception {
        BytesClassLoader loader = compile();
        JsonNode node = mapper.readTree("{\"id\":\"acc-1\",\"balance\":100,\"owner\":\"bob\"}");
        Result<?> r = Codecs.decode(loader, "demo.Account", "jsonDecoder", node);

        assertInstanceOf(Ok.class, r, "a valid JSON object decodes to Ok");
    }

    @Test
    void jsonDecoderAccumulatesFieldErrors() throws Exception {
        BytesClassLoader loader = compile();
        // id is a number (expected string), balance is a string (expected long), owner is missing.
        JsonNode node = mapper.readTree("{\"id\":5,\"balance\":\"nope\"}");
        Result<?> r = Codecs.decode(loader, "demo.Account", "jsonDecoder", node);

        assertInstanceOf(Err.class, r);
        assertEquals(3, ((Err<?>) r).issues().asList().size(), "all three field errors accumulate");
    }

    @Test
    void jsonDecoderDiscriminatesASum() throws Exception {
        Result<?> r = Codecs.decode(compile(), "demo.Application", "jsonDecoder",
                mapper.readTree("{\"type\":\"Submitted\",\"note\":\"hi\"}"));

        assertInstanceOf(Ok.class, r);
        assertEquals("demo.Submitted", ((Ok<?>) r).value().getClass().getName());
    }

    @Test
    void jsonDecoderReadsTemporalFields() throws Exception {
        // A Date/DateTime field is read from a JSON string via Raoh's string().date()/dateTime(),
        // so a temporal-bearing type is JSON-decodable (previously its jsonDecoder was skipped).
        String src = """
                module demo
                data Event = { name: String, on: Date, at: DateTime }
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());
        JsonNode node = mapper.readTree(
                "{\"name\":\"launch\",\"on\":\"2020-01-15\",\"at\":\"2020-01-15T09:30:00\"}");
        Result<?> r = Codecs.decode(loader, "demo.Event", "jsonDecoder", node);

        assertInstanceOf(Ok.class, r, "a JSON temporal string decodes to Ok");
    }

    @Test
    void jsonDecoderReadsATemporalNewtypeField() throws Exception {
        // a newtype over Date (a bare ISO string in JSON) exercises the prim-decoder leaf path too
        String src = """
                module demo
                data BornOn = Date
                data Person = { name: String, born: BornOn }
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());
        Result<?> r = Codecs.decode(loader, "demo.Person", "jsonDecoder",
                mapper.readTree("{\"name\":\"amy\",\"born\":\"1990-05-01\"}"));
        assertInstanceOf(Ok.class, r);
    }

    @Test
    void recordDecoderBytecodeLoadsAndVerifies() throws Exception {
        // Constructing a standalone jOOQ Record needs the full jOOQ runtime, so here we just force
        // the $DecRecord class to load — that verifies its whole bytecode is valid.
        Object recordDecoder = Codecs.decoder(compile(), "demo.Account", "recordDecoder");
        assertInstanceOf(Decoder.class, recordDecoder);
    }
}
