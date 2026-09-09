package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.WhatWasCompiled;
import souther.compiler.observe.FieldTypes;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Each question about a shape is asked of the one that answers it, and of nothing else.
 *
 * <p>Four are asked of a shape: what is readable off a value standing at a position, what a value
 * there is composed out of, which positions a reading has under it, and what a written value has
 * under a step. Two more stand either side of the first — crossing a {@code .} on a type, which is
 * the reading a text is typed by, and what one declaration holds under its own names, which is the
 * layout a value already narrowed to a declaration is walked by. A reader that took the second where
 * it wanted the first is told a name every case of a sum spreads is a field nothing declares.
 *
 * <p>The four come to the same fields at a record and part at a sum whose cases share a spread, and
 * that agreement is a law over the answers — checked as one in
 * {@code WhatIsReadableAndWhatIsBuiltAgreeAtARecordAndPartAtASumTest}, which holds however the four
 * are worked out.
 *
 * <p>Four and not five. How an observed value is read down a path consumes a value and answers with
 * as many standings as it finds, so it is not one of these and no equality holds between it and
 * them; what it owes is that it admits a field by what is readable and never by what the value in
 * hand turns out to carry, which is checked where that walk is.
 *
 * <p>Which is why that law is not enough on its own. A question answered out of another's answer
 * satisfies it exactly while the two agree at a record: the reading of a value or the walk into a
 * written one could take the plan's answer again and nothing about the results would say so. So each
 * of the four has an owner and says who asks it — an exact set apiece, which is a rule about the
 * others as much as about itself.
 *
 * <p>The pair that part at a sum is the pair to watch. What is readable off a value there is what
 * its cases share, and the positions under it are under each of the cases; a reader that wanted the
 * first and took the second, or the other way about, is a reader for whom a name a clause writes and
 * a place a row writes a value at are one word.
 *
 * <p>Read off what javac made of the module rather than off the source. A call is in the constant
 * pool however it was written — under an import, through a variable of any name, from inside a
 * lambda — where a text scan reads the spellings that are there today and answers about every
 * variable that happens to be named alike.
 *
 * <p>An entry added to one of these lists is a finding and not a formality: it says a reader of that
 * question arrived, and whether it wanted the question or wanted one of its own is the thing to
 * answer before the list is edited.
 */
class NoQuestionAboutAShapeIsAnsweredOutOfAnothersAnswerTest {

    /** Who asks what is readable off a value: the reading of a field access, the accessor a sum's
     *  interface declares, what a construction may spread, what the model writes where a value
     *  stands, the reading of a behavior's inputs, and the walk that reads a row at a position —
     *  which asks whether a name may be read there before taking it off the value it holds, so that
     *  a case carrying a name of its own does not make it readable for the rows that are that case.
     *
     *  <p>The first of those is {@link FieldRead} and not the elaboration. Reading a field access is
     *  one question — which names a position makes readable, and how far the names a value wears
     *  come off — and the elaboration is one of the readers that asks it rather than the one that
     *  answers it. */
    private static final List<String> ASK_WHAT_IS_READABLE = List.of(
            "souther.compiler.check.DataChecker",
            "souther.compiler.check.FieldRead",
            "souther.compiler.check.ValueReading",
            "souther.compiler.codegen.ValueClassGen",
            "souther.compiler.inputs.InputDomain",
            "souther.compiler.partition.BehaviorInputs$Standing");

    /** Who crosses a {@code .} on a type: the elaboration that types what a text wrote, the walk
     *  that says what declarations already state about an expression, and the snapshot an editor
     *  asks what may follow a {@code .}. Readers of one answer, so what a compiler accepts, what a
     *  measurement of a written value reads, and what an author is offered are the same names. */
    private static final List<String> CROSS_A_DOT = List.of(
            "souther.compiler.check.DeclaredTypeReading",
            "souther.compiler.check.Elaborator",
            "souther.compiler.sites.SemanticSnapshot");

    /** Who asks what one declaration holds under its names. A caller here has already settled which
     *  declaration it is walking — the case a value turned out to be, the name a newtype is written
     *  under, the declarations a readable surface is written on. A reader with a type in hand and a
     *  {@code .} to cross belongs in {@link #CROSS_A_DOT}: asked here, a sum answers that a name
     *  every one of its cases spreads is a field nothing declares. */
    private static final List<String> ASK_WHAT_A_DECLARATION_HOLDS = List.of(
            "souther.compiler.check.FieldRead",
            "souther.compiler.check.ReadableFields",
            "souther.compiler.examples.NeutralForm",
            "souther.compiler.observe.ValueTypes");

    /** Who asks what a value is composed out of. Composing one is what the question is for, so there
     *  is one asker, and a second is a reader with some other question. */
    private static final List<String> ASK_WHAT_IS_BUILT =
            List.of("souther.compiler.partition.ConstructionPlan");

    /** Who asks which positions stand under one. The derivation of a partition, which is what walks
     *  them; a second asker is a reader that wanted one of the other three. */
    private static final List<String> ASK_WHAT_STANDS_UNDER =
            List.of("souther.compiler.inputs.InputDomain");

    /** Who asks what a written value has under a step. Nobody outside the class that answers it — a
     *  reader working it out again turns a row's own path into a second reading of the declarations.
     *
     *  <p>Named for the written relation and not for a step's landing place. A step has two of them
     *  — where a written value has a part, and what a value standing here may be read as — and a
     *  list called after the step rather than after the question is one a reader satisfies by
     *  taking whichever answer is nearest. Which is what happened: the walk over a row's values
     *  took this one, and every name a model reads through a sum came back unread. */
    private static final List<String> ASK_WHAT_A_WRITTEN_VALUE_HAS_UNDER_A_STEP = List.of();

    /**
     * Both ways in, counted together.
     *
     * <p>Every name at a position and one name of it are the same question asked twice over, so who
     * asks it is one list. Counted apart, a reader could move from one to the other and leave the
     * list it was on — which reads as a reader having gone away.
     */
    @Test
    void whatIsReadableIsAskedOfTheOneThatAnswersIt() {
        assertEquals(ASK_WHAT_IS_READABLE, callersOf(ReadableFields.class, "of", "at"),
                "these ask what is readable off a value, and this is who does");
    }

    /**
     * And crossing a {@code .} is asked of the one that answers it.
     *
     * <p>Both halves: that somebody crosses one at all, and that nobody crosses one anywhere else.
     * The first matters because the readers of this answer are what the question exists for — a
     * reading nobody asks is a reading that has been worked out again beside it.
     *
     * <p>Both ways in are counted together. The surface a position makes readable and the one name a
     * text wrote are one question asked at two widths, and a reader moving from the narrow entry to
     * the wide one would leave a census that watched only the first looking like a reader that had
     * stopped asking.
     */
    @Test
    void crossingADotIsAskedOfTheOneThatAnswersIt() {
        assertFalse(callersOf(FieldRead.class, "at", "of").isEmpty(),
                "a field access is typed somewhere, or this is watching a name nothing calls");
        assertEquals(CROSS_A_DOT, callersOf(FieldRead.class, "at", "of"),
                "these cross a `.` on a type, and this is who answers what one may name");
    }

    /**
     * And what a declaration holds is asked by readers that have already settled which declaration.
     *
     * <p>The other side of the one above, and the reason it holds. There is no step from a type to a
     * field on {@link FieldTypes} — a caller names a declaration — so a reader with a position in
     * hand has to say which declaration it means, and the only thing that turns a position into
     * declarations is {@link ReadableFields}. That is what keeps the answer about a sum from being
     * "no fields" where the question was what a value of it makes readable.
     */
    @Test
    void whatADeclarationHoldsIsAskedByReadersThatSettledWhichDeclaration() {
        assertEquals(ASK_WHAT_A_DECLARATION_HOLDS, callersOf(FieldTypes.class, "of"),
                "these read one declaration's own layout, and each names the declaration it means");
    }

    @Test
    void whatAValueIsBuiltOutOfIsAskedOfTheOneThatAnswersIt() {
        assertEquals(ASK_WHAT_IS_BUILT, callersOf(ConstructionDescent.class, "toBuild"),
                "a reader here either composes a value or answered its own question out of how one "
                        + "is composed");
    }

    /**
     * And the two that had no owner named.
     *
     * <p>Which positions stand under one is a sum's cases and what they put there, and it is
     * answered where a partition is derived. A reading that wants the names a clause may write asks
     * what is readable and never this: a sum states what its cases share at the sum, and the
     * positions those names stand at are under each case.
     */
    @Test
    void whatStandsUnderAPositionIsAskedOfTheOneThatAnswersIt() {
        assertEquals(ASK_WHAT_STANDS_UNDER,
                callersOf(souther.compiler.inputs.StructuralInspection.class, "of"),
                "these ask which positions stand under one, and this is who does");
    }

    /**
     * What a written value has under a step, asked inside the class that answers it.
     *
     * <p>Both halves, because the expected set is empty and an empty set is what a question nobody
     * can name comes to as well: the first says the question is asked at all, the second that it is
     * asked nowhere but inside its owner.
     *
     * <p>And only that far. This helper leaves out the owner and everything nested in it, so the
     * two readers that live inside {@code BehaviorInputs} are invisible here however they ask —
     * which is how a walk over a row's values came to take this answer without anything saying so.
     * Who asks it inside the class is a separate invariant, in
     * {@code OnlyTypeAtWrittenPathStepsIntoAWrittenValueTest}.
     */
    @Test
    void whatAWrittenValueHasUnderAStepIsAskedOfTheOneThatAnswersIt() {
        Class<?> owner = souther.compiler.partition.BehaviorInputs.class;
        assertFalse(WhatWasCompiled.callersOf(owner, "stepWrittenValue").isEmpty(),
                "a step into a written value is turned into a type somewhere, or this is watching a"
                        + " name nothing calls");
        assertEquals(ASK_WHAT_A_WRITTEN_VALUE_HAS_UNDER_A_STEP, callersOf(owner, "stepWrittenValue"),
                "a step into what a row wrote is turned into a type in one place");
    }

    /**
     * And the fact the readable surface is made of is reached where it is made.
     *
     * <p>A tripwire over {@link Shape.CommonProduct.Shared} and not a claim that a sum's shared
     * spread may only ever be read for this one question: it says the cases spread a declaration,
     * which a question about provenance or about how a shared part is written could read with every
     * right. An owner of one of those belongs in this list; a reader that wanted
     * {@link ReadableFields} and worked the answer out again does not.
     */
    @Test
    void nothingElseWorksTheSharedSpreadOutForItself() {
        assertEquals(List.of(ReadableFields.class.getName()),
                callersOf(Shape.CommonProduct.Shared.class, "fields"),
                "the fields a sum's cases share are read where the readable surface is made");
    }

    /** Who calls any of {@code methods} on {@code on}, leaving out the class that declares them and
     *  anything nested inside it — a record holds its own accessor, a lambda is compiled into the
     *  class that wrote it, and naming itself is not reading itself. */
    private static List<String> callersOf(Class<?> on, String... methods) {
        Set<String> found = new java.util.LinkedHashSet<>();
        for (String method : methods) {
            found.addAll(WhatWasCompiled.callersOf(on, method));
        }
        return found.stream()
                .filter(each -> !each.equals(on.getName()) && !each.startsWith(on.getName() + "$"))
                .sorted().toList();
    }
}
