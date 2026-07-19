package net.unit8.souther.compiler;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spec 8.5 / 19.2: an {@code exposing} data gets a public record-style read accessor
 * {@code <field>()} per field, so its fields are readable across the module (package) boundary
 * and from Java. A non-exposed data gets none — its fields are visible only within the module.
 * The constructor stays non-public either way, so a read never enables construction.
 */
class CompileFieldAccessorTest {

    private static Object decodeMember(BytesClassLoader loader, Class<?> member) throws Exception {
        return Codecs.decoded(member, Map.of("id", "m-1", "age", 30L));
    }

    @Test
    void exposedDataGetsPublicFieldAccessors() throws Exception {
        String src = """
                module demo
                exposing ( Member )

                data Id = String
                data Member = { id: Id, age: Int }
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());
        Class<?> member = loader.loadClass("demo.Member");
        Object m = decodeMember(loader, member);

        Method age = member.getMethod("age");
        assertTrue(Modifier.isPublic(age.getModifiers()), "accessor is public");
        assertEquals(30L, age.invoke(m));

        Object id = member.getMethod("id").invoke(m);
        assertEquals("demo.Id", id.getClass().getName());
    }

    @Test
    void nonExposedDataHasNoAccessors() {
        String src = """
                module demo
                exposing ( Member )

                data Id = String
                data Member = { id: Id }
                data Secret = { code: String }
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());
        assertThrows(NoSuchMethodException.class,
                () -> loader.loadClass("demo.Secret").getMethod("code"));
    }
}
