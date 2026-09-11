package souther.compiler;

import souther.compiler.diag.Primary;

import souther.compiler.source.SourceId;

import souther.compiler.diag.CompileException;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A definition is held to what its own module wrote, and to nothing a row in another module says
 * about it.
 *
 * <p>A helper whose body answers no value — {@code unreachable} on its own — is a definition that
 * can only stand where a position states a type. Inlined into a field, a declared return or an
 * annotated binding it is fine, and its module compiles. What a row does with it is the row's
 * question, and the answer belongs at the row: it is the row that wrote a supply position with
 * nothing to supply.
 *
 * <p>The three ways of reaching it are one reading. A row in the declaring module, a row in an
 * importing module, and the same helper with its return type declared are all the same claim about
 * the same expression, so they are refused for the same reason at the same kind of place. They were
 * three answers: two of them named a line in a file that had done nothing wrong.
 */
class AModuleIsNotJudgedForWhatAnotherModulesRowWritesTest {

    /** A helper that can only stand where a type is stated, and a module that exports it. */
    private static final String LIB = """
            module probe.lib exposing ( boom )
            let boom (x: Int) = unreachable "not yet"
            """;

    /** The same, with its return type declared — which states the type instead. */
    private static final String LIB_DECLARING_ITS_RETURN = """
            module probe.lib exposing ( boom )
            let boom (x: Int): Int = unreachable "not yet"
            """;

    /** A module whose row supplies a value built from that helper. The row is line 8. */
    private static final String IMPORTER = """
            module probe.user
            import probe.lib ( boom )
            data Amount = Int
            behavior keep : (a: Amount) -> Amount
                constructs Amount
            let keep (a) = Amount(1)
            example keep
              | "x" : (Amount(boom(1))) -> Amount(1)
            """;

    @Test
    void aModuleThatCompilesAloneIsNotRefusedForAnImportersRow() {
        assertDoesNotThrow(() -> Compiler.compileModules(List.of(LIB)),
                "the helper stands where its own module writes it");

        CompileException e = assertThrows(CompileException.class,
                () -> Compiler.compileModules(List.of(LIB, IMPORTER)));

        assertEquals(new SourceId("1"), e.sourceId(),
                "the row that made the claim is in the importer, and so is the report");
    }

    @Test
    void theRowIsToldAtTheLineThatWroteIt() {
        CompileException e = assertThrows(CompileException.class,
                () -> Compiler.compileModules(List.of(LIB, IMPORTER)));

        assertEquals("E1903", e.diagnostic().code(),
                "a supply position with no value to supply is the row's own fixture error");
        assertEquals(8, WhereItSits.in(IMPORTER,
                ((Primary.InSource) e.diagnostic().primary()).place().region()).start().line(), "the row");
    }

    @Test
    void declaringTheReturnTypeChangesNothingAboutWhereTheRowIsTold() {
        CompileException bare = assertThrows(CompileException.class,
                () -> Compiler.compileModules(List.of(LIB, IMPORTER)));
        CompileException declared = assertThrows(CompileException.class,
                () -> Compiler.compileModules(List.of(LIB_DECLARING_ITS_RETURN, IMPORTER)));

        assertEquals(declared.diagnostic().code(), bare.diagnostic().code());
        assertEquals(declared.sourceId(), bare.sourceId());
        assertEquals(WhereItSits.in(IMPORTER,
                        ((Primary.InSource) declared.diagnostic().primary()).place().region())
                        .start().line(), WhereItSits.in(IMPORTER,
                        ((Primary.InSource) bare.diagnostic().primary()).place().region())
                        .start().line());
    }

    @Test
    void aRowInTheDeclaringModuleIsToldAtItsOwnRowToo() {
        String source = """
                module probe.own
                data Amount = Int
                behavior keep : (a: Amount) -> Amount
                    constructs Amount
                let boom (x: Int) = unreachable "not yet"
                let keep (a) = Amount(1)
                example keep
                  | "x" : (Amount(boom(1))) -> Amount(1)
                """;
        // Nothing crosses a module boundary here, and the reading is the same one: the row wrote a
        // supply position holding no value, and the row is where that is said. The helper's own
        // line is where the abort was written, which the report carries as the second place.
        CompileException e = assertThrows(CompileException.class,
                () -> Compiler.compileModules(List.of(source)));

        assertEquals("E1903", e.diagnostic().code());
        assertEquals(8, WhereItSits.in(source, ((Primary.InSource) e.diagnostic().primary()).place().region()).start().line(), "the row, not the helper on line 5");
    }
}
