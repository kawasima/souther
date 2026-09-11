package souther.compiler;

import org.junit.jupiter.api.Test;

import souther.compiler.query.InputCaseEvidence;
import souther.compiler.query.OutputCaseEvidence;
import souther.compiler.query.About;
import souther.compiler.query.Adequacy;
import souther.compiler.query.PartitionEvidence;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The states a finding's subject may not be in, refused where the value is made.
 *
 * <p>Two of them are what the list of a message's arguments left to whoever produced it: which input
 * a case is of lived in the index of the list the evidence arrived in, and nothing said the evidence
 * agreed. The third is newer than that. A finding about nothing was not constructible while the
 * subject was a list, because copying one refuses a null element; the shapes that replaced it had to
 * be told to keep what the copy was doing by accident, and this is where they are held to it.
 */
class WhatAFindingIsAboutIsHeldByTheTypeTest {

    /**
     * A finding is about something, and the shape says so.
     *
     * <p>Not defensive. The reader that would find out is whichever surface first asks the subject a
     * question — the human line, a document's {@code subject}, the sentence a build prints — so a
     * finding about nothing is one that survives being made, being counted, being published, and
     * fails on whichever of the three a reader happens to be looking at.
     *
     * <p>The list this replaced refused a null element as a side effect of being copied. Saying it
     * per shape keeps what the copy was doing by accident.
     */
    @Test
    void aSubjectThatIsNotThereIsRefusedWhereTheFindingIsMade() {
        assertThrows(NullPointerException.class, () -> new About.ACaseNoRowExpects(null));
        assertThrows(NullPointerException.class, () -> new About.AClassNoRowIsIn(null));
        assertThrows(NullPointerException.class, () -> new About.APointOfABorder(null));
        assertThrows(NullPointerException.class, () -> new About.AQuestionNothingAnswered(null));
        assertThrows(NullPointerException.class, () -> new About.AnArmNoRowGoesThrough(null));
        assertThrows(NullPointerException.class,
                () -> new About.ACaseNoRowAppliesItTo(InputCaseEvidence.none(0), null, null));
        assertThrows(NullPointerException.class,
                () -> new About.ACaseNoRowAppliesItTo(null, null, null));
        // And the class of the position, which is what says this and the domain finding are about
        // one thing. A case with no such class is a finding nothing could join to the account.
        assertThrows(NullPointerException.class,
                () -> new About.ACaseNoRowAppliesItTo(InputCaseEvidence.none(0),
                        TypeSymbols.declared(new TypeKey("souther.decimal", "Kept")), null));
    }

    /**
     * And a class is a class of a position, which the value itself says.
     *
     * <p>One step under the shape above it. A finding refusing an absent class would still take one
     * that named no position, and the reader that found out would be whichever sentence names the
     * position — which is the whole of what this class was added for.
     */
    @Test
    void aClassOfNoPositionIsRefused() {
        assertThrows(NullPointerException.class,
                () -> new PartitionEvidence.AxisClass(null, "No"));
    }

    /**
     * Which input a piece of evidence is about, and where it sits, may not differ.
     *
     * <p>Two things say it: the order of {@code signature.inputs}, which is what the document
     * publishes as the position, and the evidence's own answer, which is what a finding names a
     * position by. They are read by different surfaces. Out of step, the document would call an
     * entry the first input while a finding made from that same entry called it the second, and
     * both surfaces would go on being right about the one they read.
     *
     * <p>The evidence carries the position so that one of them means something away from this list.
     * The price of that is that the two can be said to differ, and this is where they are said to
     * agree.
     */
    @Test
    void evidenceOutOfStepWithWhereItSitsIsRefused() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> signature(List.of(InputCaseEvidence.none(1), InputCaseEvidence.none(0))));

        assertEquals("the evidence at input 0 says it is input 1", e.getMessage());
    }

    /** And in step, it is the same list. */
    @Test
    void evidenceInStepWithWhereItSitsIsKept() {
        Adequacy.SignatureEvidence signature =
                signature(List.of(InputCaseEvidence.none(0), InputCaseEvidence.none(1)));

        assertEquals(List.of(0, 1), signature.positions().stream().map(InputCaseEvidence::at).toList());
    }

    /** A position that is not one is not a position an input can be at. */
    @Test
    void anInputAtNoPositionIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> InputCaseEvidence.none(-1));
    }

    private static Adequacy.SignatureEvidence signature(List<InputCaseEvidence> inputs) {
        return Adequacy.SignatureEvidence.of(OutputCaseEvidence.none(), inputs);
    }
}
