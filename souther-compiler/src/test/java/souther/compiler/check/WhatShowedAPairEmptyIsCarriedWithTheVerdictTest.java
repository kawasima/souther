package souther.compiler.check;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * An admission saying nothing emptied the pair cannot say the pair is empty.
 *
 * <p>{@link Confinement.Admission} carries a verdict beside what showed it, and the entry for a
 * pair nothing emptied fixes the other three words: shown by nothing, at no position, out of the
 * readings. Those are what makes it that entry, so the verdict it is handed is the one thing left
 * free — and the settled answer that nothing is admitted is the one value that contradicts them.
 *
 * <p>Reachable only by writing it. Every caller that has a verdict in hand asks this entry for it
 * where the verdict is not that one, and composes what showed it from the readings where it is. So
 * what this refuses is not a case some walk produces; it is the way a caller with no verdict of its
 * own gets one, and the answer such a caller hands a choice is a branch nothing was shown about.
 *
 * <p>The name of the verdict is written out because this package declares one of its own.
 */
class WhatShowedAPairEmptyIsCarriedWithTheVerdictTest {

    @Test
    void aPairNothingEmptiedMayNotBeSaidToAdmitNothing() {
        assertThrows(IllegalArgumentException.class,
                () -> Confinement.Admission.left(souther.compiler.values.Emptiness.EMPTY));
    }

    /** And the verdicts this entry is for, which say the same three things about the proof. */
    @Test
    void aPairNothingEmptiedIsRefusedNowhereAndByNothing() {
        for (souther.compiler.values.Emptiness verdict
                : new souther.compiler.values.Emptiness[] {
                    souther.compiler.values.Emptiness.NONEMPTY,
                    souther.compiler.values.Emptiness.UNDECIDED}) {
            Confinement.Admission<String> left = Confinement.Admission.left(verdict);

            assertEquals(verdict, left.emptiness());
            assertEquals(Confinement.EmptyBy.NOTHING_SHOWN, left.by());
            assertTrue(left.site().isNowhere());
        }
    }
}
