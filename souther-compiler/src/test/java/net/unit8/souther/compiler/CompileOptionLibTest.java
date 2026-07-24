package net.unit8.souther.compiler;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The Option standard library ([#stdlib-option]): {@code map} transforms the contained value and
 *  {@code withDefault} reads it or falls back, so an optional produced by {@code List.get} /
 *  {@code Map.get} / {@code List.find} flows through the pipe instead of dead-ending in a two-arm
 *  {@code match}. {@code Option} stays non-surface (ADR-0011): the test derives every Option from a
 *  standard-library call — it never names {@code Option<T>} nor builds a {@code Some}. */
class CompileOptionLibTest {

    /** map then withDefault over Options from get / find: the Some path transforms and unwraps, the
     *  None path passes through map untouched and lands on the default. */
    @Test
    void mapAndWithDefaultOverIntOptions() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                data In = { xs: List<Int>, empty: List<Int>, m: Map<String, Int> }
                data Out = {
                    firstPlusOne: Int
                    , fromEmpty: Int
                    , lookupHit: Int
                    , lookupMiss: Int
                    , foundOverTwo: Int
                }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = {
                    Out {
                        firstPlusOne = List.get(0, i.xs) |> Option.map(n -> n + 1) |> Option.withDefault(0),
                        fromEmpty = List.get(0, i.empty) |> Option.map(n -> n + 1) |> Option.withDefault(-1),
                        lookupHit = Map.get("a", i.m) |> Option.withDefault(0),
                        lookupMiss = Map.get("z", i.m) |> Option.withDefault(-1),
                        foundOverTwo = List.find(n -> n > 2, i.xs) |> Option.map(n -> n * 10) |> Option.withDefault(0)
                    }
                }
                """), getClass().getClassLoader());

        Object in = Codecs.decoded(loader, "demo.In", Map.of(
                "xs", List.of(1L, 2L, 5L),
                "empty", List.of(),
                "m", Map.of("a", 10L, "b", 20L)));
        Object behavior = loader.loadClass("demo.Run$Impl").getConstructor().newInstance();
        Object out = Codecs.apply(behavior, in);

        Map<?, ?> r = (Map<?, ?>) Codecs.encode(loader, "demo.Out", out);
        assertEquals(2L, r.get("firstPlusOne"), "Some: map transforms the head, withDefault unwraps");
        assertEquals(-1L, r.get("fromEmpty"), "None passes through map and lands on the default");
        assertEquals(10L, r.get("lookupHit"), "withDefault alone unwraps a present key");
        assertEquals(-1L, r.get("lookupMiss"), "withDefault alone falls back on an absent key");
        assertEquals(50L, r.get("foundOverTwo"), "find result mapped then unwrapped");
    }

    /** The issue's own example: read an optional String, optionally transform it, fall back on a
     *  default — all in the pipe. map also changes the element type (String -> Int) for {@code nameLen}. */
    @Test
    void withDefaultAndMapChangingType() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                data In = { names: Map<String, String> }
                data Out = {
                    greetingHit: String
                    , greetingMiss: String
                    , nameLenHit: Int
                    , nameLenMiss: Int
                }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = {
                    Out {
                        greetingHit = Map.get("x", i.names) |> Option.withDefault("なし"),
                        greetingMiss = Map.get("z", i.names) |> Option.withDefault("なし"),
                        nameLenHit = Map.get("x", i.names) |> Option.map(s -> String.length(s)) |> Option.withDefault(0),
                        nameLenMiss = Map.get("z", i.names) |> Option.map(s -> String.length(s)) |> Option.withDefault(0)
                    }
                }
                """), getClass().getClassLoader());

        Object in = Codecs.decoded(loader, "demo.In", Map.of("names", Map.of("x", "hi")));
        Object behavior = loader.loadClass("demo.Run$Impl").getConstructor().newInstance();
        Object out = Codecs.apply(behavior, in);

        Map<?, ?> r = (Map<?, ?>) Codecs.encode(loader, "demo.Out", out);
        assertEquals("hi", r.get("greetingHit"), "withDefault unwraps a present String");
        assertEquals("なし", r.get("greetingMiss"), "withDefault falls back on an absent key");
        assertEquals(2L, r.get("nameLenHit"), "map changes String -> Int, withDefault unwraps");
        assertEquals(0L, r.get("nameLenMiss"), "None passes through map to the default");
    }

    /** Two chained maps: the second {@code Option.map} consumes the Option the first one produced, so
     *  the map-produces-Option / Option-consumed-by-map path is exercised end to end. */
    @Test
    void chainedMapConsumesTheOptionMapProduces() throws Exception {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile("""
                module demo

                data In = { xs: List<Int>, empty: List<Int> }
                data Out = { chained: Int, chainedEmpty: Int }

                behavior run : (i: In) -> Out constructs Out

                let run (i) = {
                    Out {
                        chained = List.get(0, i.xs) |> Option.map(n -> n + 1) |> Option.map(n -> n * 10) |> Option.withDefault(0),
                        chainedEmpty = List.get(0, i.empty) |> Option.map(n -> n + 1) |> Option.map(n -> n * 10) |> Option.withDefault(-1)
                    }
                }
                """), getClass().getClassLoader());

        Object in = Codecs.decoded(loader, "demo.In", Map.of("xs", List.of(3L), "empty", List.of()));
        Object behavior = loader.loadClass("demo.Run$Impl").getConstructor().newInstance();
        Object out = Codecs.apply(behavior, in);

        Map<?, ?> r = (Map<?, ?>) Codecs.encode(loader, "demo.Out", out);
        assertEquals(40L, r.get("chained"), "(3 + 1) * 10 through two chained maps");
        assertEquals(-1L, r.get("chainedEmpty"), "None threads through both maps to the default");
    }
}
