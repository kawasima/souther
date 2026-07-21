package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * End-to-end test for calling an (injected) required behavior from a behavior body with a
 * {@code let}; the required behavior's apply returns its output value directly (spec 12, 13.5).
 */
class CompileCallTest {

    private static final String MODULE = """
            module demo

            data Id = String
            data Member = { id: Id }
            data Resp = { id: Id }

            behavior findMember : (id: Id) -> Member

            behavior handle : (id: Id) -> Resp
                constructs Resp
                requires findMember

            let handle (id, findMember) = {
                let m = findMember(id)
                Resp { id = m.id }
            }
            """;

    private BytesClassLoader loader() {
        return new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
    }

    private Object decode(BytesClassLoader loader, String type, Object input) throws Exception {
        return Codecs.decoded(loader, "demo." + type, input);
    }

    @Test
    void bodyCallsRequiredBehaviorAndBindsResult() throws Exception {
        BytesClassLoader loader = loader();
        Decoder<Object, ?> memberDecoder = Codecs.decoder(loader, "demo.Member");

        // the injected required behavior returns a Member value directly
        Behavior<Object, Object> findMember = id -> ((Ok<?>) memberDecoder.decode(
                Map.of("id", "m-1"), Path.ROOT)).value();
        Object handle = loader.loadClass("demo.Handle" + "$Impl").getConstructor(Behavior.class).newInstance(findMember);

        Object r = Codecs.apply(handle, decode(loader, "Id", "q"));
        Map<?, ?> resp = (Map<?, ?>) Codecs.encode(loader, "demo.Resp", r);
        assertEquals("m-1", resp.get("id"));
    }
}
