package souther.compiler.check;

import souther.compiler.diag.Primary;
import souther.compiler.WhereItSits;
import org.junit.jupiter.api.Test;
import souther.compiler.Compiler;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.Diagnostic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The surface and well-formedness rules of a behavior's postcondition. */
class EnsuresDeclarationTest {

    private static final String SINGLE = """
            module example.one

            data Id = Int

            behavior echo : (id: Id) -> Id
                ensures same = value == id

            let echo (id) = id
            """;

    private static final String SUM = """
            module example.sum

            data Id = Int
            data Found = { id: Id }
            data Missing = { id: Id }
            data Other = { id: Id }

            behavior lookup : (id: Id) -> Found | Missing
                ensures Found | Missing -> value.id == id
            """;

    @Test
    void bothSurfaceFormsCompile() {
        assertDoesNotThrow(() -> Compiler.compile(SINGLE));
        assertDoesNotThrow(() -> Compiler.compile(SUM));
    }

    @Test void aClauseNamesAParameter() {
        refused(SINGLE.replace("value == id", "value == value"), "E1617");
    }

    @Test void valueIsReservedForTheAnswer() {
        refused(SINGLE.replace("id: Id", "value: Id")
                .replace("(id) = id", "(value) = value"), "E1618");
    }

    @Test void aNamedArmIsAnOutputCase() {
        refused(SUM.replace("Found | Missing ->", "Other ->"), "E1619");
    }

    @Test void aSumClauseNamesArms() {
        refused(SUM.replace("Found | Missing -> ", ""), "E1620");
    }

    @Test void aSingleOutputClauseNamesNoArm() {
        refused(SINGLE.replace("value == id", "Id -> value == id"), "E1621");
    }

    @Test void clauseNamesAreDistinct() {
        refused(SINGLE.replace("let echo", "    ensures same = value == id\n\nlet echo"),
                "E1622");
    }

    @Test void underscoreIsNotAClauseName() {
        refused(SINGLE.replace("same =", "_ ="), "E1623");
    }

    /**
     * Both rules are about the name, so both are reported at it. The clause's own position is the
     * `ensures`, and pointing there leaves a reader to work out which word was meant — a data's
     * clause name is reported at the name for the same reason.
     */
    @Test void aClauseNameIsReportedWhereItIsWritten() {
        // column 13 is where the name begins: four of indent, then `ensures `
        assertEquals("8:13", where(SINGLE.replace(
                "let echo", "    ensures same = value == id\n\nlet echo")));
        assertEquals("6:13", where(SINGLE.replace("same =", "_ =")));
    }

    /** Where a refusal of {@code source} points, as a reader is sent there. */
    private static String where(String source) {
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(source));
        return String.valueOf(WhereItSits.in(source,
                ((Primary.InSource) e.diagnostic().primary()).place().region().start()));
    }

    @Test void aCompositionCarriesNoClause() {
        refused("""
                module example.pipe
                behavior one : (id: Int) -> Int
                behavior two : (id: Int) -> Int
                behavior both = one >-> two
                    ensures value == id
                """, "E1624");
    }

    /**
     * An output written under a sum's own name has that sum's cases, so a clause names them. The
     * cases came from the written return terms before, which read a named sum as one term and left
     * the form with no way to write a clause at all: an arm was refused, and the armless form bound
     * `value` to the sum, on which no field can be read.
     */
    @Test void anOutputWrittenAsASumHasItsCases() {
        assertDoesNotThrow(() -> Compiler.compile("""
                module example.named

                data Id = Int
                data Found = { id: Id }
                data Missing = { id: Id }
                data Answer = Found | Missing

                behavior find : (id: Id, tag: Id) -> Answer
                    ensures Found | Missing -> value.id == id
                """));
    }

    /**
     * And a case of a case is one of them, because a value of it is one of the answer.
     *
     * <p>A sum whose case is a sum is transparent as a value (spec §sum-data): anything of
     * {@code Failure} is of the output, and a run that answers {@code Missing} answered the output.
     * A clause about {@code Missing} is therefore about answers this behavior gives, and a caller
     * has an arm to assume it at — the same cases a {@code match} over this answer admits, and the
     * two still admit the same names (#966).
     */
    @Test void aCaseOfACaseIsAnOutputCase() {
        assertDoesNotThrow(() -> Compiler.compile("""
                module example.nested

                data Id = Int
                data Found = { id: Id }
                data Missing = { id: Id }
                data Refused = { id: Id }
                data Failure = Missing | Refused

                behavior find : (id: Id, tag: Id) -> Found | Failure
                    ensures Missing -> value.id == id
                """));
    }

    /** What `value` is read as comes from what the case holds, which for a primitive case is that
     *  primitive and not a reference to a type of its name. */
    @Test void aPrimitiveOutputCaseHoldsItsPrimitive() {
        assertDoesNotThrow(() -> Compiler.compile("""
                module example.prim

                data DivisionByZero = { of: Int }

                behavior divide : (n: Int, d: Int) -> Int | DivisionByZero
                    ensures Int -> value <= n
                """));
    }

    /** Two rules that are each wrong are two things to fix, so both are said. Stopping at the first
     *  turns one build into two. */
    @Test void everyRuleThatIsWrongIsSaid() {
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile("""
                module example.two

                data Id = Int
                data Found = { id: Id }
                data Missing = { id: Id }
                data Other = { id: Id }

                behavior find : (id: Id, tag: Id) -> Found | Missing
                    ensures first = Found -> value.id == id
                    ensures second = Missing -> true
                    ensures third = Other -> value.id == id
                """));
        assertEquals(2, e.diagnostics().size(), "the second names no input and the third names no case of the answer");
        assertEquals(List.of("E1617", "E1619"),
                e.diagnostics().stream().map(Diagnostic::code).sorted().toList());
    }

    /**
     * An arm refers to the answer, so a rule whose predicate reads only inputs still states a
     * relation: where the answer is `Missing`, the inputs stood so. Asking for the word `value`
     * instead reads the text for the relation rather than the rule, and refuses a rule that states
     * one.
     */
    @Test void anArmIsAReferenceToTheAnswer() {
        assertDoesNotThrow(() -> Compiler.compile("""
                module example.guarded

                data Id = Int
                data Found = { id: Id }
                data Missing = { id: Id }

                behavior find : (id: Id, tag: Id) -> Found | Missing
                    ensures Missing -> id == tag
                """));
    }

    /** With no arm there is nothing else to refer to the answer by, so the predicate names it. */
    @Test void anArmlessRuleNamesTheAnswer() {
        refused(SINGLE.replace("value == id", "id == id"), "E1616");
    }

    /**
     * A case named twice under one arrow is one rule, not a mistake.
     *
     * <p>What a clause states is a conjunction — every rule whose guard holds applies — so naming a
     * case twice states what naming it once stated. Redundant, and not ambiguous: there is no
     * reading under which the second naming means something the first did not, so there is nothing
     * to tell an author about. It was E1625, which is retired.
     *
     * <p>Collapsed rather than kept, because a rule is which case it is about: two rules under one
     * {@link souther.compiler.check.BehaviorContract.RuleId} would leave the readers that agree on a
     * rule by its identity each holding a different number of them.
     */
    @Test void aCaseNamedTwiceUnderOneArrowIsOneRule() {
        assertDoesNotThrow(() -> Compiler.compile(SUM.replace("Found | Missing ->", "Found | Found ->")));
    }

    /** Two arms naming one case is another matter: every rule whose arm names the case applies, so
     *  both are stated, and they are two rules because they are written as two. */
    @Test void twoArmsMayNameOneCase() {
        assertDoesNotThrow(() -> Compiler.compile("""
                module example.both

                data Id = Int
                data Found = { id: Id }
                data Missing = { id: Id }

                behavior find : (id: Id, tag: Id) -> Found | Missing
                    ensures Found -> value.id == id
                          | Found -> value.id /= tag
                          | Missing -> value.id == id
                """));
    }

    /** A name nothing declares is reported where it is written, once. That it is therefore not an
     *  output case is the same mistake from another angle, and is not said again. */
    @Test void anArmNamingNothingIsSaidOnce() {
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(
                SUM.replace("Found | Missing ->", "Nothing ->")));
        assertEquals(List.of("E1023"), e.diagnostics().stream().map(Diagnostic::code).toList());
    }

    private static void refused(String source, String code) {
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(source));
        assertEquals(code, e.code());
    }
}
