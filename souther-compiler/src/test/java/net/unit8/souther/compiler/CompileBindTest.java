package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;
import net.unit8.souther.runtime.Decoder;
import net.unit8.souther.runtime.Encoder;
import net.unit8.souther.runtime.Raw;
import net.unit8.souther.runtime.Result;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Test for the named required-behavior interface and the {@code bind(...)} factory (spec 13.3, 19.5). */
class CompileBindTest {

    private static final String MODULE = """
            module demo

            data Id { value: String }
            data Member { id: Id }
            data Resp { id: Id }

            required behavior findMember(Id) -> Member

            behavior handle(id: Id) -> Resp constructs Resp {
                let m = findMember(id)
                Resp { id: m.id }
            }
            """;

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void generatesANamedInterfaceAndBindFactory() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());

        Class<?> findMember = loader.loadClass("demo.findMember");
        assertTrue(findMember.isInterface());
        assertTrue(Arrays.asList(findMember.getInterfaces()).contains(Behavior.class),
                "the named required interface extends Behavior");

        Class<?> handleClass = loader.loadClass("demo.handle");
        var bind = handleClass.getMethod("bind", findMember); // bind(findMember) -> handle

        // a Java-side implementation of findMember, injected through bind; returns a Member value
        Decoder<?> memberDecoder = (Decoder<?>) loader.loadClass("demo.Member")
                .getMethod("decoder").invoke(null);
        Behavior impl = id -> ((Result.Ok<?, ?>) memberDecoder.decode(
                Raw.object(Map.of("id", Raw.text("m-1"))))).value();
        Object findMemberImpl = Proxy.newProxyInstance(loader, new Class[]{findMember},
                (p, m, args) -> m.getName().equals("apply") ? impl.apply(args[0]) : null);

        Object handle = bind.invoke(null, findMemberImpl);

        Decoder<?> idDecoder = (Decoder<?>) loader.loadClass("demo.Id").getMethod("decoder").invoke(null);
        Object id = ((Result.Ok<?, ?>) idDecoder.decode(Raw.text("q"))).value();
        Object r = ((Behavior) handle).apply(id);

        Encoder enc = (Encoder) loader.loadClass("demo.Resp").getMethod("encoder").invoke(null);
        Raw.ObjectValue out = (Raw.ObjectValue) enc.encode(r);
        assertEquals(Raw.text("m-1"), out.value().get("id"));
    }
}
