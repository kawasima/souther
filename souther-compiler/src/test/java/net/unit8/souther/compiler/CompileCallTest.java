package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.encode.Encoder;

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

            data Id { value: String }
            data Member { id: Id }
            data Resp { id: Id }

            required behavior findMember(Id) -> Member

            behavior handle(id: Id) -> Resp
                constructs Resp
            {
                let m = findMember(id)
                Resp { id: m.id }
            }
            """;

    private BytesClassLoader loader() {
        return new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
    }

    private Object decode(BytesClassLoader loader, String type, Object input) throws Exception {
        Decoder d = (Decoder) loader.loadClass("demo." + type).getMethod("decoder").invoke(null);
        return ((Ok) d.decode(input, Path.ROOT)).value();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void bodyCallsRequiredBehaviorAndBindsResult() throws Exception {
        BytesClassLoader loader = loader();
        Decoder memberDecoder = (Decoder) loader.loadClass("demo.Member")
                .getMethod("decoder").invoke(null);

        // the injected required behavior returns a Member value directly
        Behavior findMember = id -> ((Ok) memberDecoder.decode(
                Map.of("id", "m-1"), Path.ROOT)).value();
        Object handle = loader.loadClass("demo.handle").getConstructor(Behavior.class).newInstance(findMember);

        Object r = ((Behavior) handle).apply(decode(loader, "Id", "q"));
        Encoder enc = (Encoder) loader.loadClass("demo.Resp").getMethod("encoder").invoke(null);
        Map<?, ?> resp = (Map<?, ?>) enc.encode(r);
        assertEquals("m-1", resp.get("id"));
    }
}
