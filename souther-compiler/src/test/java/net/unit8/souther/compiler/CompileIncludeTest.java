package net.unit8.souther.compiler;

import net.unit8.souther.runtime.DecodeError;
import net.unit8.souther.runtime.Decoder;
import net.unit8.souther.runtime.Encoder;
import net.unit8.souther.runtime.NonEmptyList;
import net.unit8.souther.runtime.Raw;
import net.unit8.souther.runtime.Result;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end test for {@code include} (flattened fields + inherited invariants) and spread {@code ..src} (spec 8.2, 12.4). */
class CompileIncludeTest {

    // include flattens Common's fields into each state; a behavior transitions Draft -> Submitted by spreading it.
    private static final String FLOW = """
            module demo

            data Common {
                applicant: String
                cost: Int
            }

            data Draft {
                include Common
            }

            data Submitted {
                include Common
                submittedAt: String
            }

            behavior submit(d: Draft, at: String) -> Submitted constructs Submitted {
                Submitted { ..d, submittedAt: at }
            }
            """;

    @Test
    void spreadCarriesIncludedFieldsThroughATransition() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(FLOW), getClass().getClassLoader());

        Decoder<?> draftDecoder = (Decoder<?>) loader.loadClass("demo.Draft").getMethod("decoder").invoke(null);
        Object draft = ((Result.Ok<?, ?>) draftDecoder.decode(
                Raw.object(Map.of("applicant", Raw.text("bob"), "cost", Raw.integer(100))))).value();

        Object submit = loader.loadClass("demo.submit").getConstructor().newInstance();
        Result<?, ?> r = (Result<?, ?>) submit.getClass()
                .getMethod("apply", Object.class, Object.class)
                .invoke(submit, draft, "2026");
        assertTrue(r.isOk());

        Encoder enc = (Encoder) loader.loadClass("demo.Submitted").getMethod("encoder").invoke(null);
        Raw.ObjectValue out = (Raw.ObjectValue) enc.encode(((Result.Ok<?, ?>) r).value());
        assertEquals(Raw.text("bob"), out.value().get("applicant"));  // from ..d (included field)
        assertEquals(Raw.integer(100), out.value().get("cost"));      // from ..d (included field)
        assertEquals(Raw.text("2026"), out.value().get("submittedAt"));
    }

    @Test
    void includedInvariantRunsOnConstruction() throws Exception {
        String src = """
                module demo
                data Common { applicant: String  cost: Int  invariant cost >= 0 }
                data Draft { include Common }
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());
        Decoder<?> decoder = (Decoder<?>) loader.loadClass("demo.Draft").getMethod("decoder").invoke(null);

        Result<?, NonEmptyList<DecodeError>> good = decoder.decode(
                Raw.object(Map.of("applicant", Raw.text("bob"), "cost", Raw.integer(5))));
        assertTrue(good.isOk());

        Result<?, NonEmptyList<DecodeError>> bad = decoder.decode(
                Raw.object(Map.of("applicant", Raw.text("bob"), "cost", Raw.integer(-5))));
        assertTrue(bad.isErr(), "Common's invariant is inherited by Draft");
        assertEquals("invariant_violation",
                ((Result.Err<?, NonEmptyList<DecodeError>>) bad).error().head().code());
    }
}
