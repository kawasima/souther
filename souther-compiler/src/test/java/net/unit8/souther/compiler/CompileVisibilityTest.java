package net.unit8.souther.compiler;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;
import net.unit8.raoh.decode.Decoder;

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
                module demo exposing { Public }

                data Internal = { v: Int }
                data Public = { inner: Internal }
                """), getClass().getClassLoader());

        assertTrue(Modifier.isPublic(loader.loadClass("demo.Public").getModifiers()),
                "an exposed type is public");
        assertFalse(Modifier.isPublic(loader.loadClass("demo.Internal").getModifiers()),
                "a type absent from exposing is package-private");

        // the module still works internally: Public's derived decoder reads Internal
        Decoder d = (Decoder) loader.loadClass("demo.Public").getMethod("decoder").invoke(null);
        assertTrue(d.decode(Map.of("inner", 5L), Path.ROOT) instanceof Ok);
    }

    @Test
    void exposedSumWithHiddenArmsStillDecodes() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo exposing { Contact, Contact.decoder }

                data EmailC = { email: String }
                data PhoneC = { phone: String }
                data Contact = EmailC | PhoneC
                """), getClass().getClassLoader());

        assertTrue(Modifier.isPublic(loader.loadClass("demo.Contact").getModifiers()));
        assertFalse(Modifier.isPublic(loader.loadClass("demo.EmailC").getModifiers()),
                "a non-exposed arm of a public sealed sum is package-private");

        // a public sealed interface with package-private permitted subclasses still verifies and decodes
        Decoder d = (Decoder) loader.loadClass("demo.Contact").getMethod("decoder").invoke(null);
        Result r = d.decode(Map.of("type", "EmailC", "email", "a@b"), Path.ROOT);
        assertTrue(r instanceof Ok);
        assertTrue(((Ok) r).value().getClass().getName().equals("demo.EmailC"));
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
