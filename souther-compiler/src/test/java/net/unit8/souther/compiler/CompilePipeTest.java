package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.encode.Encoder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end test for {@code >->} composition and required-behavior injection : (spec 14, 13, 19.5). */
class CompilePipeTest {

    private static final String MODULE = """
            module demo

            data Wrap = String
            data Mid = String
            data Out = String

            behavior a : (w: Wrap) -> Mid constructs Mid
            let a (w) = Mid { value: w.value }
            behavior b : (m: Mid) -> Out constructs Out
            let b (m) = Out { value: m.value }
            behavior ab = a >-> b

            behavior fetch : (w: Wrap) -> Mid
            behavior handle = fetch >-> b
            """;

    private BytesClassLoader loader() {
        return new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
    }

    private Object decode(BytesClassLoader loader, String type, String value) throws Exception {
        Decoder d = (Decoder) loader.loadClass("demo." + type).getMethod("decoder").invoke(null);
        return ((Ok) d.decode(value, Path.ROOT)).value();
    }

    @Test
    @SuppressWarnings("unchecked")
    void composesDependencyFreeBehaviors() throws Exception {
        BytesClassLoader loader = loader();
        Object ab = loader.loadClass("demo.Ab").getConstructor().newInstance();
        // apply returns the output arm value directly
        Object out = ((Behavior<Object, Object>) ab).apply(decode(loader, "Wrap", "hi"));

        // Out is a single-field newtype, so its encoder yields the bare String value.
        Encoder enc = (Encoder) loader.loadClass("demo.Out").getMethod("encoder").invoke(null);
        assertEquals("hi", enc.encode(out));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void injectsRequiredBehaviorIntoPipeline() throws Exception {
        BytesClassLoader loader = loader();
        Decoder midDecoder = (Decoder) loader.loadClass("demo.Mid")
                .getMethod("decoder").invoke(null);

        // The Java-side implementation of `fetch` returns the Mid arm value directly.
        Behavior fetch = w -> ((Ok) midDecoder.decode("hello", Path.ROOT)).value();

        Object handle = loader.loadClass("demo.Handle")
                .getConstructor(Behavior.class).newInstance(fetch);
        Object out = ((Behavior) handle).apply(decode(loader, "Wrap", "ignored"));

        Encoder enc = (Encoder) loader.loadClass("demo.Out").getMethod("encoder").invoke(null);
        assertEquals("hello", enc.encode(out));
    }

    @Test
    void mismatchedCompositionIsE1701() {
        // a: Wrap -> Mid; feeding Mid into a second `a` (which wants Wrap) accepts no arm.
        String src = """
                module demo
                data Wrap = String
                data Mid = String
                behavior a : (w: Wrap) -> Mid constructs Mid
                let a (w) = Mid { value: w.value }
                behavior bad = a >-> a
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertEquals("E1701", e.code());
    }

    /**
     * Only stages after the first take one input (spec 14.1): {@code >->} hands one value along.
     */
    @Test
    void aMultiInputBehaviorCannotFollowAnArrow() {
        String src = """
                module demo
                data Wrap = String
                data Mid = String
                behavior a : (w: Wrap) -> Mid constructs Mid
                let a (w) = Mid { value: w.value }
                behavior two : (m: Mid, k: String) -> Mid constructs Mid
                let two (m, k) = Mid { value: k }
                behavior bad = a >-> two
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(e.getMessage().contains("takes 2 inputs"), e.getMessage());
        assertTrue(e.getMessage().contains("14.1"), "the diagnostic should name the rule: " + e.getMessage());
    }

    /**
     * The first stage may take several — the pipeline then takes them too. 14.1 restricted the
     * whole chain, which rejected the spec DSL line it cited as its own justification:
     * `behavior 却下して差し戻す = 却下する >-> 差し戻す`, where `却下する` reads
     * `事前承認待ち AND 却下者ID`. Nothing in the routing rule needs the left operand to be unary.
     */
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void theFirstStageMayTakeSeveralInputs() throws Exception {
        String src = """
                module demo
                data Id = String
                data Pending = { boss: Id }
                data Rejected = { v: Int }
                data Draft = { v: Int }
                data NoRight

                behavior reject : (p: Pending, by: Id) -> Rejected | NoRight
                    constructs Rejected, NoRight

                let reject (p, by) = {
                    require by == p.boss else NoRight
                    Rejected { v: 1 }
                }
                behavior sendBack : (r: Rejected) -> Draft constructs Draft
                let sendBack (r) = Draft { v: r.v }

                behavior rejectAndSendBack = reject >-> sendBack
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());
        Class<?> flow = loader.loadClass("demo.RejectAndSendBack");
        // the pipeline takes what its first stage takes, so it is not a one-input Behavior
        var apply = flow.getMethod("apply", Object.class, Object.class);

        Decoder idDec = (Decoder) loader.loadClass("demo.Id").getMethod("decoder").invoke(null);
        Object boss = ((Ok) idDec.decode("boss", Path.ROOT)).value();
        Object other = ((Ok) idDec.decode("other", Path.ROOT)).value();
        java.lang.reflect.Constructor<?> pc = loader.loadClass("demo.Pending").getDeclaredConstructors()[0];
        pc.setAccessible(true);
        Object pending = pc.newInstance(boss);

        Object o = flow.getConstructor().newInstance();
        assertEquals("demo.Draft", apply.invoke(o, pending, boss).getClass().getName());
        assertEquals("demo.NoRight", apply.invoke(o, pending, other).getClass().getName(),
                "NoRight leaves the main line at sendBack and comes out as the pipeline's output");
    }

    @Test
    void anUndefinedStageIsStillReportedAsUnknown() {
        String src = """
                module demo
                data Wrap = String
                data Mid = String
                behavior a : (w: Wrap) -> Mid constructs Mid
                let a (w) = Mid { value: w.value }
                behavior bad = a >-> nosuch
                """;
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertTrue(e.getMessage().contains("unknown behavior"), e.getMessage());
    }
}
