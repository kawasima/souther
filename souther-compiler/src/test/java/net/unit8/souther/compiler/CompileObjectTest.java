package net.unit8.souther.compiler;

import net.unit8.raoh.Err;
import net.unit8.raoh.Issue;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Result;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end test for multi-field product data: an {@code Object} decoder that
 * accumulates every field error (spec section 27.7) and an {@code Object} encoder.
 */
class CompileObjectTest {

    private static final String ACCOUNT = """
            module demo
            import String ( length )

            data Account = {
                id: String
                , balance: Int
                , owner: String
            } invariant length(id) > 0
            """;

    private Class<?> compileAccount() throws Exception {
        Map<String, byte[]> classes = Compiler.compile(ACCOUNT);
        return new BytesClassLoader(classes, getClass().getClassLoader()).loadClass("demo.Account");
    }

    @Test
    void decodesAndEncodesAnObject() throws Exception {
        Class<?> account = compileAccount();

        Result<?> r = Codecs.decode(account, Map.of(
                "id", "acc-1",
                "balance", 100L,
                "owner", "bob"));
        assertTrue(r instanceof Ok, "expected a valid account to decode");

        Map<?, ?> encoded = (Map<?, ?>) Codecs.encode(account, ((Ok<?>) r).value());
        assertEquals(100L, encoded.get("balance"));
        assertEquals("acc-1", encoded.get("id"));
    }

    @Test
    void accumulatesEveryFieldError() throws Exception {
        // id is an int (expected text), balance is text (expected int), owner is missing.
        Result<?> r = Codecs.decode(compileAccount(), Map.of(
                "id", 5L,
                "balance", "nope"));
        assertTrue(r instanceof Err);
        List<Issue> issues = ((Err<?>) r).issues().asList();
        assertEquals(3, issues.size(), "all three field errors should accumulate");

        Set<String> codes = issues.stream()
                .map(Issue::code)
                .collect(Collectors.toSet());
        // two type mismatches (id, balance) collapse to one code; owner missing -> required
        assertEquals(Set.of("type_mismatch", "required"), codes);
    }

    @Test
    void invariantRunsAfterAccumulation() throws Exception {
        Result<?> r = Codecs.decode(compileAccount(), Map.of(
                "id", "",
                "balance", 0L,
                "owner", "bob"));
        assertTrue(r instanceof Err);
        assertEquals("invariant_violation",
                ((Err<?>) r).issues().asList().get(0).code());
    }
}
