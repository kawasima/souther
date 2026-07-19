package net.unit8.souther.compiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * End-to-end test for {@code require ... else} and type-routed {@code >->} composition: a case
 * the next stage does not accept propagates through unchanged (spec 12.2, 14.2).
 */
class CompileRailwayTest {

    private static final String MODULE = """
            module demo

            data Amount = Int
            data TooLarge = { limit: Int }
            data Doubled = Int

            // over 100 leaves the main line as a TooLarge case. `require ... else` mints the
            // TooLarge, so the behavior declares it (spec 12.3).
            behavior guard : (a: Amount) -> Amount | TooLarge constructs TooLarge

            let guard (a) = {
                require a.value <= 100 else TooLarge { limit = 100 }
                a
            }

            // only accepts Amount; a TooLarge flowing in would bypass this stage
            behavior toDoubled : (a: Amount) -> Doubled constructs Doubled

            let toDoubled (a) = Doubled { value = a.value }

            behavior process = guard >-> toDoubled
            """;

    private BytesClassLoader loader() {
        return new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
    }

    private Object amount(BytesClassLoader loader, long n) throws Exception {
        return Codecs.decoded(loader, "demo.Amount", n);
    }

    @Test
    void amountFlowsThroughToDoubled() throws Exception {
        BytesClassLoader loader = loader();
        Object process = loader.loadClass("demo.Process").getConstructor().newInstance();
        Object r = Codecs.apply(process, amount(loader, 42));
        // 42 <= 100, so guard yields Amount, which toDoubled consumes into a Doubled
        assertEquals("demo.Doubled", r.getClass().getName());
    }

    @Test
    void tooLargeCasePropagatesPastToDoubled() throws Exception {
        BytesClassLoader loader = loader();
        Object process = loader.loadClass("demo.Process").getConstructor().newInstance();
        Object r = Codecs.apply(process, amount(loader, 500));
        // 500 > 100, so guard yields TooLarge, which toDoubled does not accept: it propagates
        assertEquals("demo.TooLarge", r.getClass().getName());
    }

    /**
     * Regression: a case that leaves the main line must stay off it. The router kept the passed
     * case in the running union and offered it to every later stage, so a stage that happened to
     * accept it pulled it back in — `A >-> B >-> C` returned C's output for a value B had already
     * dropped. That is not Railway (spec 14.2), and it made the meaning of a pipeline depend on
     * where it was split.
     */
    @Test
    void anCaseThatLeftTheMainLineIsNotOfferedToLaterStages() throws Exception {
        String src = """
                module demo
                data In = Int
                data Mid = { v: Int }
                data Off = { v: Int }
                data Out = { v: Int }
                data OffOut = Off | Out

                // over 100 leaves as Off
                behavior 判定 : (i: In) -> Mid | Off constructs Mid, Off
                let 判定 (i) = {
                    require i.value <= 100 else Off { v = i.value }
                    Mid { v = i.value }
                }
                // takes Mid only — Off passes it by
                behavior 加工 : (m: Mid) -> Out constructs Out
                let 加工 (m) = Out { v = m.v }
                // would accept Off, but Off already left the main line at 加工
                behavior 仕上げ : (x: OffOut) -> Out constructs Out
                let 仕上げ (x) = Out { v = 0 }

                behavior flow = 判定 >-> 加工 >-> 仕上げ
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());
        Object flow = loader.loadClass("demo.Flow").getConstructor().newInstance();

        Object off = Codecs.apply(flow, Codecs.decoded(loader, "demo.In", 500L));
        assertEquals("demo.Off", off.getClass().getName(),
                "Off left the main line at 加工, so 仕上げ must not pick it up");

        Object ok = Codecs.apply(flow, Codecs.decoded(loader, "demo.In", 5L));
        assertEquals("demo.Out", ok.getClass().getName(), "the main line still reaches 仕上げ");
    }

    @Test
    void requireElseValueMustBeAnOutputCase() {
        // `other` is a B, which is not one of `bad`'s output cases (just A), so this is rejected.
        String src = """
                module demo
                data A = Int
                data B = Int
                behavior bad : (a: A, other: B) -> A

                let bad (a, other) = {
                    require a.value <= 1 else other
                    a
                }
                """;
        assertThrows(CompileException.class, () -> Compiler.compile(src));
    }
}
