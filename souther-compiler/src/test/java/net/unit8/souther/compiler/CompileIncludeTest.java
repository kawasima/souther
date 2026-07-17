package net.unit8.souther.compiler;

import net.unit8.raoh.Err;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.encode.Encoder;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end test for {@code include} (flattened fields + inherited invariants) and spread {@code ..src} (spec 8.2, 12.4). */
class CompileIncludeTest {

    // include flattens Common's fields into each state; a behavior transitions Draft -> Submitted by spreading it.
    private static final String FLOW = """
            module demo

            data Common = {
                applicant: String
                cost: Int
            }

            data Draft = {
                include Common
            }

            data Submitted = {
                include Common
                submittedAt: String
            }

            behavior submit = (d: Draft, at: String) -> Submitted constructs Submitted

            fn submit (d, at) = Submitted { ..d, submittedAt: at }
            """;

    @Test
    void spreadCarriesIncludedFieldsThroughATransition() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(FLOW), getClass().getClassLoader());

        Decoder draftDecoder = (Decoder) loader.loadClass("demo.Draft").getMethod("decoder").invoke(null);
        Result r = draftDecoder.decode(Map.of("applicant", "bob", "cost", 100L), Path.ROOT);
        assertTrue(r instanceof Ok);
        Object draft = ((Ok) r).value();

        Object submit = loader.loadClass("demo.Submit").getConstructor().newInstance();
        Object submitted = submit.getClass()
                .getMethod("apply", Object.class, Object.class)
                .invoke(submit, draft, "2026");

        Encoder enc = (Encoder) loader.loadClass("demo.Submitted").getMethod("encoder").invoke(null);
        Map<?, ?> out = (Map<?, ?>) enc.encode(submitted);
        assertEquals("bob", out.get("applicant"));  // from ..d (included field)
        assertEquals(100L, out.get("cost"));         // from ..d (included field)
        assertEquals("2026", out.get("submittedAt"));
    }

    @Test
    void includedInvariantRunsOnConstruction() throws Exception {
        String src = """
                module demo
                data Common = { applicant: String  cost: Int  invariant cost >= 0 }
                data Draft = { include Common }
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());
        Decoder decoder = (Decoder) loader.loadClass("demo.Draft").getMethod("decoder").invoke(null);

        Result good = decoder.decode(Map.of("applicant", "bob", "cost", 5L), Path.ROOT);
        assertTrue(good instanceof Ok);

        Result bad = decoder.decode(Map.of("applicant", "bob", "cost", -5L), Path.ROOT);
        assertTrue(bad instanceof Err, "Common's invariant is inherited by Draft");
        assertEquals("invariant_violation",
                ((Err) bad).issues().asList().get(0).code());
    }
}
