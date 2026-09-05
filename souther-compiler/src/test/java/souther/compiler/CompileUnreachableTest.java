package souther.compiler;

import souther.compiler.diag.CompileException;
import souther.runtime.UnreachableReached;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An arm that answers {@code unreachable} where the model has no value to give: the combination it
 * covers cannot arise for a reason the callee cannot see, and reaching it aborts (spec §match).
 */
class CompileUnreachableTest {

    private static final String MODULE = """
            module demo

            data UnderThirty
            data ThirtyOrOver
            data AgeBand = UnderThirty | ThirtyOrOver

            data UnderOneYear
            data TwentyYearsOrMore
            data ServiceBand = UnderOneYear | TwentyYearsOrMore

            data Days = Int invariant value >= 90 && value <= 330

            behavior daysFor : (age: AgeBand, service: ServiceBand) -> Days
                constructs Days

            let daysFor (age, service) =
                match age with
                    | UnderThirty ->
                        match service with
                            | UnderOneYear      -> Days(90)
                            | TwentyYearsOrMore ->
                                unreachable "twenty insured years cannot precede the age of thirty"
                    | ThirtyOrOver ->
                        match service with
                            | UnderOneYear      -> Days(120)
                            | TwentyYearsOrMore -> Days(240)
            """;

    private Object daysFor(String age, String service) throws Throwable {
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(MODULE), getClass().getClassLoader());
        Object ageBand = Codecs.decoded(loader, "demo.AgeBand", age);
        Object serviceBand = Codecs.decoded(loader, "demo.ServiceBand", service);
        Object impl = Emitted.behavior(loader, "demo", "daysFor").getConstructor().newInstance();
        Object out;
        try {
            out = impl.getClass()
                    .getMethod("apply", Object.class, Object.class)
                    .invoke(impl, ageBand, serviceBand);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
        return Codecs.encode(loader, "demo.Days", out);
    }

    @Test
    void anArmBesideAnUnreachableOneStillAnswers() throws Throwable {
        assertEquals(90L, daysFor("UnderThirty", "UnderOneYear"));
        assertEquals(240L, daysFor("ThirtyOrOver", "TwentyYearsOrMore"));
    }

    @Test
    void anExampleRowReachingAnUnreachableArmIsE1911() {
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(MODULE + """

                example daysFor
                  | (UnderThirty, TwentyYearsOrMore) -> Days(90)
                """));

        assertEquals("E1911", e.diagnostics().get(0).code(), e.getMessage());
        assertTrue(e.getMessage().contains("twenty insured years cannot precede the age of thirty"),
                e.getMessage());
        // the line and column the reason was written at, and no file name: this compiler is given
        // none, so one derived here would name a file that need not exist
        assertTrue(e.getMessage().contains("(22:21)"), e.getMessage());
        assertFalse(e.getMessage().contains(".sou"), e.getMessage());
    }

    @Test
    void anExampleRowThatStaysOffTheUnreachableArmHolds() {
        Compiler.compile(MODULE + """

                example daysFor
                  | (ThirtyOrOver, TwentyYearsOrMore) -> Days(240)
                """);
    }

    @Test
    void anUnreachableInAPrimitivePositionAborts() throws Throwable {
        // The position holds an Int, which is a long on the JVM, so the abort has to leave the stack
        // in the shape the arm beside it leaves: a verified method, not one the verifier rejects.
        //
        // Matched on a field of a pair one of whose rules is stated as an alternative to a rule
        // nothing here reads. The premise is one nothing here settles (spec
        // §unreachable-is-for-what-the-model-rules-out), which is what leaves a `B` both writable
        // at the boundary and able to reach the abort.
        String module = """
                module demo

                data A
                data B
                data AB = A | B
                data Pair = { l: AB, s: String } invariant either = UNREAD_S || l == A
                data Out = Int

                behavior run : (v: Pair) -> Out constructs Out

                let run (v) = Out(
                    match v.l with
                        | A -> 1
                        | B -> unreachable "a B never reaches this rule"
                )
                """.replace("UNREAD_S", ARuleNoReadingTakesIn.about("s"));
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(module), getClass().getClassLoader());
        Object impl = Emitted.behavior(loader, "demo", "run").getConstructor().newInstance();

        assertEquals(1L, Codecs.encode(loader, "demo.Out", Codecs.apply(impl,
                Codecs.decoded(loader, "demo.Pair", java.util.Map.of("l", "A", "s", "x")))));
        UnreachableReached aborted = assertThrows(UnreachableReached.class,
                () -> Codecs.apply(impl, Codecs.decoded(loader, "demo.Pair",
                        java.util.Map.of("l", "B", "s", "x"))));
        assertTrue(aborted.getMessage().contains("a B never reaches this rule"), aborted.getMessage());
    }

    @Test
    void anUnreachableInTailPositionAborts() throws Throwable {
        // The whole body is the abort, so it is emitted where a `return` would be rather than as a
        // value another instruction consumes.
        String module = """
                module demo

                data A
                data Out = Int

                behavior run : (v: A) -> Out

                let run (v) = unreachable "this rule is never called"
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(module), getClass().getClassLoader());
        Object impl = Emitted.behavior(loader, "demo", "run").getConstructor().newInstance();

        UnreachableReached aborted = assertThrows(UnreachableReached.class,
                () -> Codecs.apply(impl, Codecs.decoded(loader, "demo.A", "A")));
        assertTrue(aborted.getMessage().contains("this rule is never called"), aborted.getMessage());
    }

    @Test
    void anUnreachableInAnIfBranchLeavesTheOtherBranchAnswering() throws Throwable {
        String module = """
                module demo

                data N = Int
                data Out = Int

                behavior run : (n: N) -> Out constructs Out

                let run (n) =
                    if n.value >= 0 then Out(n.value)
                    else unreachable "the count is never negative"
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(module), getClass().getClassLoader());
        Object impl = Emitted.behavior(loader, "demo", "run").getConstructor().newInstance();

        assertEquals(7L, Codecs.encode(loader, "demo.Out",
                Codecs.apply(impl, Codecs.decoded(loader, "demo.N", 7L))));
        assertThrows(UnreachableReached.class,
                () -> Codecs.apply(impl, Codecs.decoded(loader, "demo.N", -1L)));
    }

    @Test
    void aTotalRecursiveHelperMayAnswerUnreachable() throws Throwable {
        // It adds no recursive call, so the size-change check has nothing further to prove.
        String module = """
                module demo

                data Item = { rank: Int, parent: Item? }
                data Out = Int

                let depth (i: Item) : Int =
                    match i.parent with
                        | Some p -> 1 + depth(p)
                        | None   -> if i.rank == 0 then 0
                                    else unreachable "only the root is ranked zero"

                behavior run : (i: Item) -> Out constructs Out

                let run (i) = Out(depth(i))
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(module), getClass().getClassLoader());
        Object impl = Emitted.behavior(loader, "demo", "run").getConstructor().newInstance();

        assertEquals(1L, Codecs.encode(loader, "demo.Out", Codecs.apply(impl,
                Codecs.decoded(loader, "demo.Item", java.util.Map.of(
                        "rank", 1L, "parent", java.util.Map.of("rank", 0L))))));
    }

    @Test
    void anUnreachableBoundByALetAborts() throws Throwable {
        // Nothing states a type here, so the binding takes the one thing `unreachable` has: no value.
        // The premise is one nothing settles, as above — an alternative to a rule nothing here
        // reads — which is what keeps the `B` writable.
        String module = """
                module demo

                data A
                data B
                data AB = A | B
                data Pair = { l: AB, s: String } invariant either = UNREAD_S || l == A
                data Out = Int

                behavior run : (v: Pair) -> Out constructs Out

                let run (v) = {
                    let n = match v.l with
                        | A -> 1
                        | B -> unreachable "a B never reaches this rule"
                    Out(n)
                }
                """.replace("UNREAD_S", ARuleNoReadingTakesIn.about("s"));
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(module), getClass().getClassLoader());
        Object impl = Emitted.behavior(loader, "demo", "run").getConstructor().newInstance();

        assertEquals(1L, Codecs.encode(loader, "demo.Out", Codecs.apply(impl,
                Codecs.decoded(loader, "demo.Pair", java.util.Map.of("l", "A", "s", "x")))));
        assertThrows(UnreachableReached.class,
                () -> Codecs.apply(impl, Codecs.decoded(loader, "demo.Pair",
                        java.util.Map.of("l", "B", "s", "x"))));
    }

    @Test
    void unreachableWhereThePositionStatesNoTypeIsE1307() {
        // The binding says nothing about what it holds and neither does the abort, so the code that
        // reads `n` has no shape to read. Reported, not emitted as a class nothing can load.
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile("""
                module demo

                data A
                data Out = Int

                behavior run : (v: A) -> Out constructs Out

                let run (v) = {
                    let n = unreachable "never"
                    Out(n)
                }
                """));

        assertEquals("E1307", e.diagnostics().get(0).code(), e.getMessage());
    }

    @Test
    void unreachableAsAnArithmeticOperandIsE1307() {
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile("""
                module demo

                data A
                data Out = Int

                behavior run : (v: A) -> Out constructs Out

                let run (v) = Out(1 + unreachable "never")
                """));

        assertEquals("E1307", e.diagnostics().get(0).code(), e.getMessage());
    }

    @Test
    void unreachableInAnInvariantIsRejected() {
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile("""
                module demo

                data Days = Int
                    invariant if value >= 90 then true else unreachable "the table has no such cell"
                """));

        assertTrue(e.getMessage().contains("unreachable"), e.getMessage());
    }

    @Test
    void unreachableAsAnExampleExpectationIsRejected() {
        // The expectation's position contributes the output's type, so `unreachable` stands where
        // a type is stated — admissible, as beside a declared return — and aborts when the row
        // computes it: a row whose expectation aborts states nothing to compare.
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(MODULE + """

                example daysFor
                  | (UnderThirty, TwentyYearsOrMore) -> unreachable "the table has no such cell"
                """));

        assertEquals("E1903", e.diagnostics().get(0).code(), e.getMessage());
        assertTrue(e.getMessage().contains("the table has no such cell"), e.getMessage());
    }

    @Test
    void reachingAnUnreachableArmAborts() {
        UnreachableReached aborted = assertThrows(
                UnreachableReached.class, () -> daysFor("UnderThirty", "TwentyYearsOrMore"));
        assertTrue(aborted.getMessage().contains("twenty insured years cannot precede the age of thirty"),
                aborted.getMessage());
    }
}
