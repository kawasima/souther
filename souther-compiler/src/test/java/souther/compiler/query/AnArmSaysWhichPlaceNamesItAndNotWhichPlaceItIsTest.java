package souther.compiler.query;

import org.junit.jupiter.api.Test;
import souther.compiler.coverage.ArmReportAnchor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An arm crosses a module boundary saying which fork it is one of, and which question places a
 * report about it — not where that fork is.
 *
 * <p>That it holds no place is {@link AFindingSaysWhatWasFoundAndNotWhereToPrintItTest}'s to say,
 * where the walk that answers it starts at the answer rather than at the value last put right. What
 * is here is the other half, which no walk over places can see: a value holding nothing passes
 * whether it says which of the two questions places it or says nothing at all, and the second is a
 * reader left to guess.
 */
class AnArmSaysWhichPlaceNamesItAndNotWhichPlaceItIsTest {

    @Test
    void thereAreTwoPlacesAReportAboutAnArmPointsAtAndNoThird() {
        assertTrue(ArmReportAnchor.class.isSealed(),
                "which of them it is, is settled where the arm is made and read where a sentence is"
                        + " written; a third would be a reader deciding");
        assertEquals(List.of("WhereItIsWritten", "WhereItWasReached"),
                List.of(ArmReportAnchor.class.getPermittedSubclasses()).stream()
                        .map(Class::getSimpleName).toList(),
                "the fork's own file, or the call this compilation came in through");
    }
}
