package net.unit8.souther.compiler;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A behavior takes a named-sum parameter and consumes it by matching each case (spec 12.2, 16.3):
 * {@code data SubPre = Sub | Pre} taken as {@code (app: SubPre) -> ...} with a `match app`. An
 * anonymous union may not sit in a parameter (spec 8.6) — see {@link CompileUnionParamRejectTest}.
 */
class CompileUnionParamTest {

    private static final String MODULE = """
            module demo

            data Sub = Int
            data Pre = Int
            data SubPre = Sub | Pre
            data Done = Int

            behavior finish : (app: SubPre) -> Done constructs Done

            let finish (app) =
                match app with
                    | Sub as s -> Done { value = s.value }
                    | Pre as p -> Done { value = p.value }
            """;

    private long finish(String caseType, long n) throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
        // SubPre is a named sum; its cases are adjacently tagged ({type, value}), so the input to
        // finish is decoded through the sum decoder rather than a bare case value (spec 10.3, 12.2).
        Object arg = Codecs.decoded(loader, "demo.SubPre", Map.of("type", caseType, "value", n));
        Object done = Codecs.apply(loader.loadClass("demo.Finish" + "$Impl")
                .getConstructor().newInstance(), arg);
        // Done is a single-field newtype, so its encoder yields the bare Long.
        return (Long) Codecs.encode(loader, "demo.Done", done);
    }

    @Test
    void matchesTheSubCase() throws Exception {
        assertEquals(3, finish("Sub", 3));
    }

    @Test
    void matchesThePreCase() throws Exception {
        assertEquals(7, finish("Pre", 7));
    }
}
