package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** End-to-end test for multi-success output {@code -> A | B} chosen by a branch (spec 12.2). */
class CompileMultiSuccessTest {

    private static final String MODULE = """
            module demo

            data Draft = { cost: Int }
            data Cheap = { cost: Int }
            data Pricey = { cost: Int }

            behavior classify = (d: Draft) -> Cheap | Pricey constructs Cheap, Pricey {
                if d.cost <= 100 then Cheap { cost: d.cost } else Pricey { cost: d.cost }
            }
            """;

    @SuppressWarnings("unchecked")
    private String classify(BytesClassLoader loader, long cost) throws Exception {
        Decoder dec = (Decoder) loader.loadClass("demo.Draft").getMethod("decoder").invoke(null);
        Object draft = ((Ok) dec.decode(cost, Path.ROOT)).value();
        Object behavior = loader.loadClass("demo.classify").getConstructor().newInstance();
        Object r = ((Behavior<Object, Object>) behavior).apply(draft);
        return r.getClass().getName();
    }

    @Test
    void branchSelectsWhichSuccessTypeToProduce() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
        assertEquals("demo.Cheap", classify(loader, 50));
        assertEquals("demo.Pricey", classify(loader, 500));
    }

    @Test
    void bodyMustProduceAMemberOfTheDeclaredUnion() {
        String src = """
                module demo
                data Draft = { cost: Int }
                data Cheap = { cost: Int }
                data Pricey = { cost: Int }
                data Other = { cost: Int }
                behavior bad = (d: Draft) -> Cheap | Pricey constructs Other {
                    Other { cost: d.cost }
                }
                """;
        assertThrows(CompileException.class, () -> Compiler.compile(src));
    }
}
