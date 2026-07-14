package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;
import net.unit8.souther.runtime.Decoder;
import net.unit8.souther.runtime.Encoder;
import net.unit8.souther.runtime.Raw;
import net.unit8.souther.runtime.Result;

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

    private Object decode(BytesClassLoader loader, String type, Raw raw) throws Exception {
        Decoder<?> d = (Decoder<?>) loader.loadClass("demo." + type).getMethod("decoder").invoke(null);
        return ((Result.Ok<?, ?>) d.decode(raw)).value();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void bodyCallsRequiredBehaviorAndBindsResult() throws Exception {
        BytesClassLoader loader = loader();
        Decoder<?> memberDecoder = (Decoder<?>) loader.loadClass("demo.Member")
                .getMethod("decoder").invoke(null);

        // the injected required behavior returns a Member value directly
        Behavior findMember = id -> ((Result.Ok<?, ?>) memberDecoder.decode(
                Raw.object(Map.of("id", Raw.text("m-1"))))).value();
        Object handle = loader.loadClass("demo.handle").getConstructor(Behavior.class).newInstance(findMember);

        Object r = ((Behavior) handle).apply(decode(loader, "Id", Raw.text("q")));
        Encoder enc = (Encoder) loader.loadClass("demo.Resp").getMethod("encoder").invoke(null);
        Raw.ObjectValue resp = (Raw.ObjectValue) enc.encode(r);
        assertEquals(Raw.text("m-1"), resp.value().get("id"));
    }
}
