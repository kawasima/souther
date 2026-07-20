package net.unit8.souther.compiler;

import net.unit8.souther.compiler.diag.CompileException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** End-to-end test for multi-success output {@code -> A | B} chosen by a branch (spec 12.2). */
class CompileMultiSuccessTest {

    private static final String MODULE = """
            module demo

            data Draft = Int
            data Cheap = { cost: Int }
            data Pricey = { cost: Int }

            behavior classify : (d: Draft) -> Cheap | Pricey constructs Cheap, Pricey

            let classify (d) =
                if d.value <= 100 then Cheap { cost = d.value } else Pricey { cost = d.value }
            """;

    private String classify(BytesClassLoader loader, long cost) throws Exception {
        Object draft = Codecs.decoded(loader, "demo.Draft", cost);
        Object behavior = loader.loadClass("demo.Classify").getConstructor().newInstance();
        Object r = Codecs.apply(behavior, draft);
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
                behavior bad : (d: Draft) -> Cheap | Pricey constructs Other

                let bad (d) = Other { cost = d.cost }
                """;
        assertThrows(CompileException.class, () -> Compiler.compile(src));
    }
}
