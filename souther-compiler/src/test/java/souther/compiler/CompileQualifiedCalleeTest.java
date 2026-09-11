package souther.compiler;

import souther.compiler.diag.Primary;

import souther.compiler.diag.msg.NameMessage;
import org.junit.jupiter.api.Test;
import souther.compiler.diag.CompileException;
import souther.compiler.jvm.ClassFileImage;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An application is an argument list written after an expression, whatever the expression is, so a
 * qualified callee reaches resolution as the field read the parser made of it (issue #274). What a
 * chain of names means — a member of a namespace, or a field taken off a binding — is decided here,
 * once, whether or not an argument list follows.
 */
class CompileQualifiedCalleeTest {

    private static final String UP = """
            module up exposing ( Amount, Defaults, defaults )

            data Amount = Int invariant value >= 0
            data Defaults = { limit: Int }

            let defaults : Defaults = Defaults { limit = 7 }
            """;

    private static final String DOTTED = """
            module probe.a exposing ( Amount )

            data Amount = Int invariant value >= 0
            """;

    private static Map<String, ClassFileImage> compile(String... srcs) {
        return Compiler.compileModules(List.of(srcs));
    }

    private static CompileException refused(String... srcs) {
        return assertThrows(CompileException.class, () -> compile(srcs));
    }

    /** A module-qualified construction, which reached the library ladder only because the parser
     *  flattened `up.Amount(` into one name. */
    @Test
    void aQualifiedConstructionIsStillReached() {
        compile(UP, """
                module down exposing ( In, Out, run )
                data In = { n: Int }
                data Out = { m: Int }
                behavior run : (i: In) -> Out constructs Out, up.Amount
                let run (i) = Out { m = up.Amount(i.n).value }
                """);
    }

    /**
     * A module whose own name is dotted, in a value position. The parser looked three tokens ahead
     * for a `(` and so read only one dot as part of a callee, which left this spelling working in a
     * type position and refused in a value one.
     */
    @Test
    void aModuleWhoseNameIsDottedIsReachedInAValuePositionToo() {
        compile(DOTTED, """
                module down exposing ( In, Out, run )
                data In = { n: Int }
                data Out = { m: Int }
                behavior run : (i: In) -> Out constructs Out, probe.a.Amount
                let run (i) = Out { m = probe.a.Amount(i.n).value }
                """);
    }

    /**
     * A chain that answers nothing as a whole is taken apart, and what is reported is the member of
     * the namespace rather than the whole chain: {@code up.defaults} is where the answer runs out —
     * a module reaches another module's values through an import, not through a qualifier — and
     * {@code .limit} would have been an ordinary read off whatever it answered.
     */
    @Test
    void aChainThatAnswersNothingIsReportedWhereTheAnswerRanOut() {
        String source = """
                module down exposing ( In, Out, run )
                data In = { n: Int }
                data Out = { m: Int }
                behavior run : (i: In) -> Out constructs Out
                let run (i) = Out { m = i.n + up.defaults.limit }
                """;
        CompileException e = refused(UP, source);

        assertTrue(e.getMessage().contains("`up.defaults`"), e.getMessage());
        assertEquals("up.defaults".length(),
                WhereItSits.in(source,
                        ((Primary.InSource) e.diagnostic().primary()).place().region())
                        .end().column()
                        - WhereItSits.in(source,
                                ((Primary.InSource) e.diagnostic().primary()).place().region())
                        .start().column());
    }

    /** A chain rooted at a binding is field reads all the way down, however long it is: the root is
     *  bound, so nothing in it is a namespace and no fold is attempted. */
    @Test
    void aChainRootedAtABindingIsReadsAllTheWayDown() throws Exception {
        Map<String, ClassFileImage> classes = compile("""
                module demo exposing ( In, Out, go )
                data Inner = { n: Int }
                data Mid = { inner: Inner }
                data In = { n: Int }
                data Out = { m: Int }
                behavior go : (i: In) -> Out constructs Out, Mid, Inner
                let go (i) = {
                    let d = Mid { inner = Inner { n = i.n } }
                    Out { m = d.inner.n }
                }
                """);

        BytesClassLoader loader = new BytesClassLoader(classes, getClass().getClassLoader());
        Object b = Emitted.behavior(loader, "demo", "go").getConstructor().newInstance();
        Object out = Codecs.apply(b, Codecs.decoded(loader, "demo.In", Map.of("n", 5L)));
        assertEquals(5L, out.getClass().getMethod("m").invoke(out));
    }

    /** A binding in force wins over a namespace of the same name, applied or not. */
    @Test
    void aBindingWinsOverANamespaceOfTheSameName() {
        compile("""
                module demo
                data Out = { m: Int }
                behavior go : (n: Int) -> Out constructs Out
                let go (n) = {
                    let Map = Out { m = n }
                    Out { m = Map.m }
                }
                """);
    }

    /**
     * A member a namespace has not got is named in full. Reporting the root would send the author
     * after a module name that is right, and the root is what a chain rooted at an unknown name is
     * reported at — see below.
     */
    @Test
    void aMemberAKnownNamespaceHasNotGotIsNamedInFull() {
        String source = """
                module down exposing ( In, Out, run )
                data In = { n: Int }
                data Out = { m: Int }
                behavior run : (i: In) -> Out constructs Out
                let run (i) = Out { m = probe.a.NoSuch(i.n).value }
                """;
        CompileException e = refused(DOTTED, source);

        assertTrue(e.getMessage().contains("probe.a.NoSuch"), e.getMessage());
        assertEquals("probe.a.NoSuch".length(),
                WhereItSits.in(source,
                        ((Primary.InSource) e.diagnostic().primary()).place().region())
                        .end().column()
                        - WhereItSits.in(source,
                                ((Primary.InSource) e.diagnostic().primary()).place().region())
                        .start().column(),
                "the report underlines exactly the name that was written");
    }

    /** A chain rooted at a name nothing declares is reported at that name: nothing in front of the
     *  dot is a namespace, so there is no qualified name here to name. */
    @Test
    void aChainRootedAtAnUnknownNameIsReportedAtTheRoot() {
        String source = """
                module demo
                data In = { n: Int }
                data Out = { m: Int }
                behavior go : (i: In) -> Out constructs Out
                let go (i) = Out { m = unknown.member(i.n) }
                """;
        CompileException e = refused(source);

        assertInstanceOf(NameMessage.NoValueOfThatNameInScope.class, e.diagnostic().said());
        assertTrue(e.getMessage().contains("`unknown`"), e.getMessage());
        assertEquals("unknown".length(),
                WhereItSits.in(source, ((Primary.InSource) e.diagnostic().primary()).place().region()).end().column() - WhereItSits.in(source, ((Primary.InSource) e.diagnostic().primary()).place().region()).start().column());
    }

    /**
     * A field read applied, where the field is not a function. The application is lowered to a
     * binding that is applied, and the report quotes what the author wrote rather than the binding
     * the compiler made — which is nowhere in the source.
     */
    @Test
    void aFieldThatIsNotAFunctionIsReportedByTheSpellingTheAuthorWrote() {
        String source = """
                module demo
                data Deps = { count: Int }
                data In = { n: Int }
                data Out = { m: Int }
                behavior go : (i: In) -> Out constructs Out, Deps
                let go (i) = {
                    let d = Deps { count = 1 }
                    Out { m = d.count(i.n) }
                }
                """;
        CompileException e = refused(source);

        assertInstanceOf(NameMessage.ItIsNotAFunctionHere.class, e.diagnostic().said());
        assertTrue(e.getMessage().contains("`d.count`"), e.getMessage());
        assertEquals("d.count".length(),
                WhereItSits.in(source, ((Primary.InSource) e.diagnostic().primary()).place().region()).end().column() - WhereItSits.in(source, ((Primary.InSource) e.diagnostic().primary()).place().region()).start().column(),
                "the report underlines the read, not the binding the lowering introduced");
    }

    /** `e |> Mod.name` hands the read over as the callee it is rather than reassembling a name from
     *  its parts, so the pipe and the call reach the same answer for the same spelling. */
    @Test
    void aPipeToAQualifiedNameIsTheSameCallAsWritingIt() throws Exception {
        Map<String, ClassFileImage> classes = compile("""
                module demo exposing ( In, Out, go )
                data In = { s: String }
                data Out = { t: String, u: String }
                behavior go : (i: In) -> Out constructs Out
                let go (i) = Out { t = i.s |> String.trim, u = String.trim(i.s) }
                """);

        BytesClassLoader loader = new BytesClassLoader(classes, getClass().getClassLoader());
        Object b = Emitted.behavior(loader, "demo", "go").getConstructor().newInstance();
        Object out = Codecs.apply(b, Codecs.decoded(loader, "demo.In", Map.of("s", "  a  ")));
        assertEquals("a", out.getClass().getMethod("t").invoke(out));
        assertEquals("a", out.getClass().getMethod("u").invoke(out));
    }
}
