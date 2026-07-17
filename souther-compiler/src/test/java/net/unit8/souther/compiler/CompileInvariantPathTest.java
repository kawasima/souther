package net.unit8.souther.compiler;

import net.unit8.raoh.Err;
import net.unit8.raoh.Issue;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.Result;
import net.unit8.raoh.decode.Decoder;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When a nested value breaks its own invariant during decoding, the reported issue must point at
 * that value's path — not collapse to the document root. Two sibling fields that both violate an
 * invariant used to be indistinguishable because the decoder minted every invariant failure at
 * {@code Path.ROOT}; each now carries the path it was decoded at (spec 9.4, 15).
 */
class CompileInvariantPathTest {

    private static final String MODULE = """
            module demo

            data Amount = Int
                invariant value > 0

            data Order = {
                first: Amount
                second: Amount
            }
            """;

    private Decoder orderDecoder() throws Exception {
        Map<String, byte[]> classes = Compiler.compile(MODULE);
        Class<?> order = new BytesClassLoader(classes, getClass().getClassLoader()).loadClass("demo.Order");
        return (Decoder) order.getMethod("decoder").invoke(null);
    }

    @Test
    void twoSiblingInvariantViolationsCarryDistinctPaths() throws Exception {
        Result r = orderDecoder().decode(Map.of("first", -1L, "second", -3L), Path.ROOT);
        assertTrue(r instanceof Err, "both fields break value > 0, so decoding must fail");

        List<Issue> issues = ((Err) r).issues().asList();
        Set<String> codes = issues.stream().map(Issue::code).collect(Collectors.toSet());
        assertEquals(Set.of("invariant_violation"), codes);

        // The two failures must be told apart by their path — first vs second, not both root.
        Set<List<String>> paths = issues.stream()
                .map(i -> i.path().segments())
                .collect(Collectors.toSet());
        assertEquals(Set.of(List.of("first"), List.of("second")), paths,
                "each invariant failure must carry the field it was decoded at");
    }

    @Test
    void aSingleNestedViolationPointsAtItsField() throws Exception {
        Result r = orderDecoder().decode(Map.of("first", 5L, "second", -3L), Path.ROOT);
        assertTrue(r instanceof Err);

        List<Issue> issues = ((Err) r).issues().asList();
        assertEquals(1, issues.size(), "only second breaks the invariant");
        assertEquals("invariant_violation", issues.get(0).code());
        assertEquals(List.of("second"), issues.get(0).path().segments());
    }

    @Test
    void aRootValueStillReportsAtRoot() throws Exception {
        Map<String, byte[]> classes = Compiler.compile("""
                module demo
                data Amount = Int
                    invariant value > 0
                """);
        Decoder dec = (Decoder) new BytesClassLoader(classes, getClass().getClassLoader())
                .loadClass("demo.Amount").getMethod("decoder").invoke(null);
        Result r = dec.decode(-1L, Path.ROOT);
        assertTrue(r instanceof Err);
        assertEquals(List.of(), ((Err) r).issues().asList().get(0).path().segments(),
                "a top-level value keeps the root path");
    }
}
