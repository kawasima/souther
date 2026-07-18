package net.unit8.souther.compiler;

import net.unit8.souther.runtime.Behavior;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The value pipe {@code |>} feeds its left operand into the right-hand call as its last argument:
 * {@code e |> f(a)} is {@code f(a, e)} and {@code e |> f} is {@code f(e)} (F#/Elm reading, but the
 * operand lands last to match Souther's collection-first stdlib order). It is desugared in Lower.
 */
class CompileValuePipeTest {

    @SuppressWarnings({"unchecked", "rawtypes"})
    private long run(String body, long v) throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                data In = { v: Int }

                behavior run : (x: In) -> Int

                let inc (n: Int) = n + 1
                let add (a: Int, b: Int) = a + b

                let run (x) = %s
                """.formatted(body)), getClass().getClassLoader());
        Decoder d = (Decoder) loader.loadClass("demo.In").getMethod("decoder").invoke(null);
        Object in = ((Ok) d.decode(Map.of("v", v), Path.ROOT)).value();
        return (Long) ((Behavior<Object, Object>) loader.loadClass("demo.Run")
                .getConstructor().newInstance()).apply(in);
    }

    @Test
    void pipeIntoBareFunctionName() throws Exception {
        assertEquals(6L, run("x.v |> inc", 5));      // inc(x.v)
    }

    @Test
    void pipeFillsTheLastArgument() throws Exception {
        assertEquals(15L, run("x.v |> add(10)", 5)); // add(10, x.v)
    }

    @Test
    void pipeChainsLeftToRight() throws Exception {
        assertEquals(16L, run("x.v |> inc |> add(10)", 5)); // add(10, inc(x.v))
    }

    @Test
    void aPipeIntoANonCallableIsRejected() {
        CompileException e = assertThrows(CompileException.class, () -> run("x.v |> 3", 5));
        assertTrue(e.getMessage().contains("|>"), e.getMessage());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void pipeIntoAQualifiedNameWithoutParens() throws Exception {
        // `xs |> List.sum` — a qualified stdlib name with no parens (the idiomatic F#/Elm form). It
        // parses as a field access, but the pipe routes it to the call `List.sum(xs)`.
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                data In = { ns: List<Int> }

                behavior run : (x: In) -> Int

                let run (x) = x.ns |> List.sum
                """), getClass().getClassLoader());
        Decoder d = (Decoder) loader.loadClass("demo.In").getMethod("decoder").invoke(null);
        Object in = ((Ok) d.decode(Map.of("ns", List.of(1L, 2L, 3L)), Path.ROOT)).value();
        long r = (Long) ((Behavior<Object, Object>) loader.loadClass("demo.Run")
                .getConstructor().newInstance()).apply(in);
        assertEquals(6L, r);
    }
}
