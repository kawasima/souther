package net.unit8.souther.compiler;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Result;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With an {@code exposing} clause, only listed types are public; the rest are package-private,
 * so the module boundary is enforced at the JVM level (spec 4, 8.5, 19.6). A module without an
 * {@code exposing} clause keeps everything public.
 */
class CompileVisibilityTest {

    @Test
    void unexposedTypeIsPackagePrivate() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo exposing ( Public )

                data Internal = Int
                data Public = { inner: Internal }
                """), getClass().getClassLoader());

        assertTrue(Modifier.isPublic(loader.loadClass("demo.Public").getModifiers()),
                "an exposed type is public");
        assertFalse(Modifier.isPublic(loader.loadClass("demo.Internal").getModifiers()),
                "a type absent from exposing is package-private");

        // the module still works internally: Public's derived decoder reads Internal
        assertTrue(Codecs.decode(loader, "demo.Public", Map.of("inner", 5L)) instanceof Ok);
    }

    @Test
    void exposedSumWithHiddenCasesStillDecodes() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo exposing ( Contact )

                data EmailC = { email: String }
                data PhoneC = { phone: String }
                data Contact = EmailC | PhoneC
                """), getClass().getClassLoader());

        assertTrue(Modifier.isPublic(loader.loadClass("demo.Contact").getModifiers()));
        assertFalse(Modifier.isPublic(loader.loadClass("demo.EmailC").getModifiers()),
                "a non-exposed case of a public sealed sum is package-private");

        // a public sealed interface with package-private permitted subclasses still verifies and decodes
        Result<?> r = Codecs.decode(loader, "demo.Contact", Map.of("type", "EmailC", "email", "a@b"));
        assertTrue(r instanceof Ok);
        assertTrue(((Ok<?>) r).value().getClass().getName().equals("demo.EmailC"));
    }

    @Test
    void noExposingKeepsEverythingPublic() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                data A = { v: Int }
                """), getClass().getClassLoader());
        assertTrue(Modifier.isPublic(loader.loadClass("demo.A").getModifiers()),
                "without exposing, types stay public");
    }
}
