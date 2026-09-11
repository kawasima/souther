package souther.compiler;

import souther.compiler.diag.Primary;

import souther.compiler.source.SourceId;

import souther.compiler.diag.msg.ExampleMessage;
import souther.compiler.diag.CompileException;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * An {@code examples for} file declares the values its own rows name.
 *
 * <p>The file exists so that rows which would make the model source long can live beside it, and a row may
 * name a value rather than restate everything inside it. Those two did not meet: the file could hold only
 * examples, so a fixture a row named had to be declared in the model file — the file the rows exercise
 * rather than the one they are written in, which is the opposite of what the companion is for (issue #210).
 *
 * <p>A value is a {@code let} with no parameter list. A {@code let} with parameters is a helper, which is a
 * method the target module emits, and a companion file does not add to what the model compiles to.
 */
class ExampleFileValuesTest {

    private static final String MODEL = """
            module f25

            data In  = { n: Int }
            data Out = { m: Int }

            behavior run : (i: In) -> Out
                constructs Out

            let run (i) = Out { m = i.n }
            """;

    @Test
    void aRowNamesAValueItsOwnFileDeclares() {
        assertDoesNotThrow(() -> Compiler.compileModules(List.of(MODEL, """
                examples for f25

                let base = In { n = 1 }

                example run
                    | "a fixture beside the rows that name it" : (base) -> Out { m = 1 }
                """)));
    }

    @Test
    void aRowVariesAFieldOfAValueItsOwnFileDeclares() {
        assertDoesNotThrow(() -> Compiler.compileModules(List.of(MODEL, """
                examples for f25

                let base = In { n = 1 }

                example run
                    | "unchanged" : (base) -> Out { m = 1 }
                    | "one field varied" : (In { ...base, n = 7 }) -> Out { m = 7 }
                """)));
    }

    @Test
    void aRowInTheModelFileNamesOneToo() {
        // The values join the module the rows join, as the fakes do.
        assertDoesNotThrow(() -> Compiler.compileModules(List.of(MODEL + """

                example run
                    | "the model's own row" : (base) -> Out { m = 1 }
                """, """
                examples for f25

                let base = In { n = 1 }
                """)));
    }

    @Test
    void aRowThatDoesNotHoldStillFails() {
        CompileException e = assertThrows(CompileException.class,
                () -> Compiler.compileModules(List.of(MODEL, """
                        examples for f25

                        let base = In { n = 1 }

                        example run
                            | "wrong" : (base) -> Out { m = 2 }
                        """)));
        assertEquals("E1905", e.diagnostic().code());
    }

    @Test
    void aHelperIsStillNotSomethingAnExamplesFileHolds() {
        CompileException e = assertThrows(CompileException.class,
                () -> Compiler.compileModules(List.of(MODEL, """
                        examples for f25

                        let twice (n: Int): Int = n * 2

                        example run
                            | "x" : (In { n = 1 }) -> Out { m = 1 }
                        """)));
        assertEquals("E1906", e.diagnostic().code());
        assertInstanceOf(ExampleMessage.AnExamplesFileHoldsOnlyExamples.class, e.diagnostic().said());
    }

    @Test
    void aDataIsStillNotSomethingAnExamplesFileHolds() {
        CompileException e = assertThrows(CompileException.class,
                () -> Compiler.compileModules(List.of(MODEL, """
                        examples for f25

                        data Extra = { x: Int }

                        example run
                            | "x" : (In { n = 1 }) -> Out { m = 1 }
                        """)));
        assertEquals("E1906", e.diagnostic().code());
        assertInstanceOf(ExampleMessage.AnExamplesFileHoldsOnlyExamples.class, e.diagnostic().said());
    }

    @Test
    void aNameTheModuleAlreadyDeclaresIsReportedOnTheFileThatWroteIt() {
        // A position is a line and a column, so a diagnostic quoted against the wrong file quotes an
        // unrelated line. The module's own duplicate check can only be quoted against the module's file,
        // and this declaration is in the attached one — so the attached file's own key reports it.
        CompileException e = assertThrows(CompileException.class,
                () -> Compiler.compileModules(List.of(MODEL + """

                        let base = In { n = 5 }
                        """, """
                        examples for f25

                        let base = In { n = 1 }

                        example run
                            | "which base?" : (base) -> Out { m = 1 }
                        """)));

        assertEquals("E1906", e.diagnostic().code());
        assertInstanceOf(ExampleMessage.TheNameIsAlreadyDeclared.class, e.diagnostic().said());
        assertEquals(new SourceId("1"), e.sourceId(), "the declaration is in the attached file");
        assertEquals(3, WhereItSits.in(MODEL, ((Primary.InSource) e.diagnostic().primary()).place().region()).start().line(), "and at its own line");
    }
}
