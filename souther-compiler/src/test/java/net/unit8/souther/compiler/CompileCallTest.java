package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;
import net.unit8.souther.runtime.Decoder;
import net.unit8.souther.runtime.Encoder;
import net.unit8.souther.runtime.Raw;
import net.unit8.souther.runtime.Result;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end test for calling an (injected) required behavior from a behavior body with a
 * railway-bound {@code let}, short-circuiting on failure (spec 12, 13.5, 14.7).
 */
class CompileCallTest {

    private static final String MODULE = """
            module demo

            data Id { value: String }
            data Member { id: Id }
            data NotFound { reason: String }
            data Resp { id: Id }

            required behavior findMember(Id) -> Result<Member, NotFound>

            behavior handle(id: Id) -> Result<Resp, NotFound>
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

        Behavior findMember = id -> memberDecoder.decode(Raw.object(Map.of("id", Raw.text("m-1"))));
        Object handle = loader.loadClass("demo.handle").getConstructor(Behavior.class).newInstance(findMember);

        Result<?, ?> r = ((Behavior) handle).apply(decode(loader, "Id", Raw.text("q")));
        assertTrue(r.isOk());
        Encoder enc = (Encoder) loader.loadClass("demo.Resp").getMethod("encoder").invoke(null);
        Raw.ObjectValue resp = (Raw.ObjectValue) enc.encode(((Result.Ok<?, ?>) r).value());
        assertEquals(Raw.text("m-1"), resp.value().get("id"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void bodyShortCircuitsWhenRequiredBehaviorFails() throws Exception {
        BytesClassLoader loader = loader();
        Object notFound = decode(loader, "NotFound", Raw.text("gone"));

        Behavior findMember = id -> Result.err(notFound);
        Object handle = loader.loadClass("demo.handle").getConstructor(Behavior.class).newInstance(findMember);

        Result<?, ?> r = ((Behavior) handle).apply(decode(loader, "Id", Raw.text("q")));
        assertTrue(r.isErr(), "findMember failed, so Resp is never constructed");
        assertEquals("demo.NotFound", ((Result.Err<?, ?>) r).error().getClass().getName());
    }
}
