package souther.compiler;

import souther.runtime.ConstraintViolation;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Generated classes carry JVM debug info (a {@code SourceFile} attribute and a
 * {@code LineNumberTable}) derived from the {@code SourcePos} every {@code Core} node keeps, so a
 * runtime stack trace — most importantly an invariant abort ({@code ConstraintViolation}) — points
 * back to the {@code .sou} source line rather than "Unknown Source". The source file name is the
 * module's simple name plus {@code .sou}.
 *
 * <p>One file and one line, and they are a pair: the line is a line of the file the class names.
 * A helper written in another file of the same compile and expanded here keeps the positions it was
 * written at, and those are positions in a file this class does not name — so what the table is
 * given for them is the call, which is the line of this file the author can act on.
 */
class CompileSourceMapTest {

    @Test
    void anInvariantAbortStackTraceNamesTheSouSourceAndLine() throws Exception {
        // 金額(x - 100) sits on line 5 of the module; make(50) builds 金額(-50), which aborts there.
        String src = """
                module demo
                data 金額 = Int
                    invariant value >= 0
                behavior make : (x: Int) -> 金額 constructs 金額
                let make (x) = 金額(x - 100)
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());
        Object impl = Emitted.behavior(loader, "demo", "make").getConstructor().newInstance();

        ConstraintViolation v = assertThrows(ConstraintViolation.class, () -> Codecs.apply(impl, 50L));

        StackTraceElement frame = Arrays.stream(v.getStackTrace())
                .filter(f -> f.getClassName().startsWith("demo."))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no generated frame: " + Arrays.toString(v.getStackTrace())));
        assertEquals("demo.sou", frame.getFileName(), "the stack frame names the .sou source file");
        assertEquals(5, frame.getLineNumber(), "the stack frame points to the construction's line");
    }

    @Test
    void aMultiLineConstructionAbortPointsAtTheConstructionNotTheLastField() throws Exception {
        // 金額( opens on line 6; its argument x - 100 sits on line 7. The abort must be pinned to the
        // construction (line 6), not the last field-init line, which emitting fields would otherwise
        // leave bound.
        String src = """
                module demo
                data 金額 = Int
                    invariant value >= 0
                behavior make : (x: Int) -> 金額 constructs 金額
                let make (x) =
                    金額(
                        x - 100
                    )
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());
        Object impl = Emitted.behavior(loader, "demo", "make").getConstructor().newInstance();

        ConstraintViolation v = assertThrows(ConstraintViolation.class, () -> Codecs.apply(impl, 50L));

        StackTraceElement frame = Arrays.stream(v.getStackTrace())
                .filter(f -> f.getClassName().startsWith("demo."))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no generated frame: " + Arrays.toString(v.getStackTrace())));
        assertEquals(6, frame.getLineNumber(), "the abort points at the construction, not the field-init line");
    }

    /** What {@code app.uses} calls, whose body constructs the value that aborts. The construction is
     *  on line 9 of this text, and {@code uses.sou} is shorter than that. */
    private static final String RULE = """
            module lib.rule exposing ( 金額, shrink )

            data 金額 = Int
                invariant value >= 0




            let shrink (x: Int): 金額 = 金額(x - 100)
            """;

    @Test
    void aSplicedHelpersAbortPointsAtTheCallInTheFileTheClassNames() throws Exception {
        // The class is app.uses's, so its SourceFile is uses.sou; the construction that aborts is
        // written in rule.sou. A line of the declaring file in this class's table would be a line
        // of a file this class does not name, and uses.sou has no line to be read as it.
        String uses = """
                module app.uses
                import lib.rule ( 金額, shrink )

                behavior make : (x: Int) -> 金額
                    constructs 金額
                let make (x) = shrink(x)
                """;
        BytesClassLoader loader = new BytesClassLoader(
                Compiler.compileModules(List.of(RULE, uses)), getClass().getClassLoader());
        Object impl = Emitted.behavior(loader, "app.uses", "make").getConstructor().newInstance();

        ConstraintViolation v = assertThrows(ConstraintViolation.class, () -> Codecs.apply(impl, 50L));

        StackTraceElement frame = Arrays.stream(v.getStackTrace())
                .filter(f -> f.getClassName().startsWith("app.uses."))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no generated frame: " + Arrays.toString(v.getStackTrace())));
        assertEquals("uses.sou", frame.getFileName(), "the frame names the class's own source file");
        assertEquals(6, frame.getLineNumber(), "the frame points at the call, which is what uses.sou has");
    }

    @Test
    void aSplicedHelpersAbortPointsAtTheCallAndNotAtTheArgumentBelowIt() throws Exception {
        // The call opens on line 6 and its argument sits on line 7. Between the call's own line and
        // the body it stands for, the argument is emitted and writes a line of its own, so the last
        // line written before the copy is not the call unless the call is written again after it.
        String uses = """
                module app.spread
                import lib.rule ( 金額, shrink )

                behavior make : (x: Int) -> 金額
                    constructs 金額
                let make (x) = shrink(
                    x + 1
                )
                """;
        BytesClassLoader loader = new BytesClassLoader(
                Compiler.compileModules(List.of(RULE, uses)), getClass().getClassLoader());
        Object impl = Emitted.behavior(loader, "app.spread", "make").getConstructor().newInstance();

        ConstraintViolation v = assertThrows(ConstraintViolation.class, () -> Codecs.apply(impl, 50L));

        StackTraceElement frame = Arrays.stream(v.getStackTrace())
                .filter(f -> f.getClassName().startsWith("app.spread."))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no generated frame: " + Arrays.toString(v.getStackTrace())));
        assertEquals("spread.sou", frame.getFileName(), "the frame names the class's own source file");
        assertEquals(6, frame.getLineNumber(), "the frame points at the call, not at the argument below it");
    }

    @Test
    void theSameHoldsForACallTheBodyDoesNotAnswerWith() throws Exception {
        // The same copy, reached where a value is wanted rather than in tail position: bound to a
        // name the block goes on to answer with. It is emitted by the other of the two emitters,
        // which binds the call and its argument in the same order.
        String uses = """
                module app.inner
                import lib.rule ( 金額, shrink )

                behavior make : (x: Int) -> 金額
                    constructs 金額
                let make (x) = {
                    let z = shrink(
                        x + 1
                    )
                    z
                }
                """;
        BytesClassLoader loader = new BytesClassLoader(
                Compiler.compileModules(List.of(RULE, uses)), getClass().getClassLoader());
        Object impl = Emitted.behavior(loader, "app.inner", "make").getConstructor().newInstance();

        ConstraintViolation v = assertThrows(ConstraintViolation.class, () -> Codecs.apply(impl, 50L));

        StackTraceElement frame = Arrays.stream(v.getStackTrace())
                .filter(f -> f.getClassName().startsWith("app.inner."))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no generated frame: " + Arrays.toString(v.getStackTrace())));
        assertEquals("inner.sou", frame.getFileName(), "the frame names the class's own source file");
        assertEquals(7, frame.getLineNumber(), "the frame points at the call, not at the argument below it");
    }

    @Test
    void aLetTheAuthorWroteLeavesItsBodysOwnLineInFront() throws Exception {
        // The other side of the re-pin. This `let` binds a value written below it too, so the call
        // is bound again before its body — and the body is written in the file the class names, so
        // it binds a line of its own at that offset and that is the one the offset keeps.
        String src = """
                module demo
                data 金額 = Int
                    invariant value >= 0
                behavior make : (x: Int) -> 金額 constructs 金額
                let make (x) = {
                    let y =
                        x + 1
                    金額(y - 100)
                }
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());
        Object impl = Emitted.behavior(loader, "demo", "make").getConstructor().newInstance();

        ConstraintViolation v = assertThrows(ConstraintViolation.class, () -> Codecs.apply(impl, 50L));

        StackTraceElement frame = Arrays.stream(v.getStackTrace())
                .filter(f -> f.getClassName().startsWith("demo."))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no generated frame: " + Arrays.toString(v.getStackTrace())));
        assertEquals(8, frame.getLineNumber(), "the abort points at the construction, not at the `let` above it");
    }

    @Test
    void aHelperSplicedWithinOneFileKeepsItsOwnLine() throws Exception {
        // The same splice with nothing crossed: the helper's body is written in the file the class
        // names, so its own line is a line that file has, and it is the more useful of the two.
        String src = """
                module demo
                data 金額 = Int
                    invariant value >= 0
                let shrink (x: Int): 金額 = 金額(x - 100)
                behavior make : (x: Int) -> 金額 constructs 金額
                let make (x) = shrink(x)
                """;
        BytesClassLoader loader = new BytesClassLoader(Compiler.compile(src), getClass().getClassLoader());
        Object impl = Emitted.behavior(loader, "demo", "make").getConstructor().newInstance();

        ConstraintViolation v = assertThrows(ConstraintViolation.class, () -> Codecs.apply(impl, 50L));

        StackTraceElement frame = Arrays.stream(v.getStackTrace())
                .filter(f -> f.getClassName().startsWith("demo."))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no generated frame: " + Arrays.toString(v.getStackTrace())));
        assertEquals("demo.sou", frame.getFileName(), "the frame names the .sou source file");
        assertEquals(4, frame.getLineNumber(), "the frame points at the helper's construction");
    }
}
