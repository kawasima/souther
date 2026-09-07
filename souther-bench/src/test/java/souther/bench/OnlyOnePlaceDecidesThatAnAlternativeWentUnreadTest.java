package souther.bench;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * That a choice offered an alternative nothing could read is decided in one place.
 *
 * <p>Two things are owed about it and they are not the same thing. A position has to be told that
 * it may be wider than the rules leave it, and the account of a rule has to send an author to the
 * choice they wrote. Both are projections of one decision — which alternative went unread and which
 * of them the width of the choice rests on — and the decision can only be made where the
 * alternatives are what stands between the brackets, because a conjunction written beside a choice
 * is distributed into both of them before the values are worked out.
 *
 * <p>So each side receiving the answer is the shape being held. Read off the call sites, because
 * that is what the rule is about: nothing in a type stops a carrier that holds two branches and a
 * flag from working out for itself which positions a choice opened, and that is the arrangement
 * this closes.
 */
class OnlyOnePlaceDecidesThatAnAlternativeWentUnreadTest {

    private static final String OPENING = "souther.compiler.check.StatedByClauses$AlternativeOpening";

    private static final String OUTCOME = "souther.compiler.check.Settlement$OfAChoice";

    /** The one method that answers what a choice left open, out of what its alternatives took in. */
    private static final String AUTHORITY =
            "souther.compiler.check.StatedByClauses#opens"
                    + "(Lsouther/compiler/check/ChoiceId;"
                    + "Lsouther/compiler/check/Settlement$WidthDependency;"
                    + "Lsouther/compiler/check/Adoption;Lsouther/compiler/check/Adoption;)"
                    + "Lsouther/compiler/check/StatedByClauses$AlternativeOpening;";

    /**
     * One method decides one, and it is that one.
     *
     * <p>The class is not the boundary. Two methods of it, each working the answer out for the
     * caller in front of them, are the arrangement this closes — the position and the account of
     * the rule had exactly that, and agreed only until a conjunction stood beside a choice.
     */
    @Test
    void oneMethodDecidesWhatAChoiceLeftOpen() throws Exception {
        assertEquals(List.of(AUTHORITY), whatMakes(OPENING),
                "which positions a choice left open is answered somewhere else as well, or nowhere"
                        + " — and a second answer holds only until a conjunction stands beside a"
                        + " choice and the two are asked of different branches");
    }

    /**
     * And the fate of a choice's branches and what its width rests on are made together.
     *
     * <p>The two are read by one caller about one pair of branches, and a place making one of them
     * without the other is a place that has decided something about a choice on its own. Made at
     * one site, a reader holding an outcome holds both — including in the branch of the settlement
     * where the descriptions decide a choice before anything is worked out, which is where the
     * second answer would have been.
     *
     * <p>Taking one more occurrence of the same choice in is the other, and it decides nothing: it
     * is handed two outcomes about the two branches and joins each side with the same side.
     */
    @Test
    void aFateAndTheWidthItGoesWithAreMadeAtOneSite() throws Exception {
        assertEquals(List.of(
                        "souther.compiler.check.Settlement$OfAChoice#alsoSeen"
                                + "(Lsouther/compiler/check/Settlement$OfAChoice;)"
                                + "Lsouther/compiler/check/Settlement$OfAChoice;",
                        "souther.compiler.check.StatedByClauses$Reading#outcome"
                                + "(Lsouther/compiler/check/StatedTogether$Said;"
                                + "Lsouther/compiler/check/Settlement$Sided;"
                                + "Lsouther/compiler/check/StatedTogether$Said;"
                                + "Lsouther/compiler/check/Settlement$Sided;)"
                                + "Lsouther/compiler/check/Settlement$OfAChoice;"),
                whatMakes(OUTCOME),
                "a choice's outcome is made somewhere else as well, and there is nothing to make"
                        + " one of them carry the other's answer about the same two branches");
    }

    /**
     * And nothing outside the answer itself works out which alternative a width rests on.
     *
     * <p>The comparison is four lines over two descriptions, which is exactly what a caller holding
     * two branches would write for itself — and a second one of them would be read as the same fact
     * while resting on whichever pair of branches its writer had in hand. Held here, the account
     * takes the answer and asks nothing about values.
     */
    @Test
    void whatAWidthRestsOnIsWorkedOutNowhereElse() throws Exception {
        assertEquals(List.of(
                        "souther.compiler.check.Settlement$WidthDependency#alsoSeen"
                                + "(Lsouther/compiler/check/Settlement$WidthDependency;)"
                                + "Lsouther/compiler/check/Settlement$WidthDependency;",
                        "souther.compiler.check.Settlement$WidthDependency#none"
                                + "()Lsouther/compiler/check/Settlement$WidthDependency;",
                        "souther.compiler.check.Settlement$WidthDependency#of"
                                + "(Lsouther/compiler/values/Emptiness;"
                                + "Lsouther/compiler/values/PlannedValues;"
                                + "Lsouther/compiler/values/Emptiness;"
                                + "Lsouther/compiler/values/PlannedValues;)"
                                + "Lsouther/compiler/check/Settlement$WidthDependency;"),
                whatMakes("souther.compiler.check.Settlement$WidthDependency"),
                "somewhere else makes one, and what it made is not what the settlement compared");
    }

    /** Every method that makes a value of {@code type}, each named once. */
    private static List<String> whatMakes(String type) throws Exception {
        List<String> made = new ArrayList<>();
        for (Compiled.Site site : Compiled.sites()) {
            if (site.makesA(type)) {
                made.add(site.at());
            }
        }
        return made.stream().distinct().sorted().toList();
    }
}
