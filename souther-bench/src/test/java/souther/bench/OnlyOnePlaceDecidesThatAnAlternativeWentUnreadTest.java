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
                    + "Lsouther/compiler/check/StatedByClauses$Part;"
                    + "Lsouther/compiler/check/StatedByClauses$Part;)"
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
                                + "Lsouther/compiler/check/Confinement$Planned;"
                                + "Lsouther/compiler/values/Emptiness;"
                                + "Lsouther/compiler/check/Confinement$Planned;)"
                                + "Lsouther/compiler/check/Settlement$WidthDependency;"),
                whatMakes("souther.compiler.check.Settlement$WidthDependency"),
                "somewhere else makes one, and what it made is not what the settlement compared");
    }

    /**
     * And one method turns an alternative going unread into what it left open.
     *
     * <p>Both halves of that meet in one place and both are one reading's: which alternative it had
     * no word for, and what it could not show the alternatives preserve. Made anywhere else, one of
     * the halves has to be fetched, and what is fetched is either a second answer or the other
     * reading's — which composes without a complaint and reports a clause the reading did not see.
     *
     * <p>Beside it the empty one, which is what a choice shown to leave every position where it was
     * comes to. It decides nothing and is here because a maker is a maker.
     */
    @Test
    void oneMethodTurnsAnUnreadAlternativeIntoWhatItLeftOpen() throws Exception {
        assertEquals(List.of("souther.compiler.check.Opening#nothing()"
                                + "Lsouther/compiler/check/Opening;",
                        "souther.compiler.check.Settlement$Width#opened(ZZ)"
                                + "Lsouther/compiler/check/Opening;"),
                whatMakes("souther.compiler.check.Opening"),
                "an opening made somewhere else is a second answer to what an alternative left"
                        + " open, and the two agree only until one of them changes");
    }

    /**
     * And one method compares what two branches leave, whichever reading is asking.
     *
     * <p>The comparison is the same few lines over whatever a reading leaves a position, and each
     * reading reaches it by handing in its own descriptions. Written once per reading instead, the
     * two would be two rules about one question, and a third reading would arrive with nowhere
     * obvious to be added.
     *
     * <p>Beside it the empty one and the join over occurrences, which compare nothing.
     */
    @Test
    void oneMethodComparesWhatTwoBranchesLeave() throws Exception {
        assertEquals(List.of("souther.compiler.check.Settlement$Width#alsoSeen"
                                + "(Lsouther/compiler/check/Settlement$Width;)"
                                + "Lsouther/compiler/check/Settlement$Width;",
                        "souther.compiler.check.Settlement$Width#comparing"
                                + "(Ljava/util/Set;Ljava/util/function/Function;"
                                + "Ljava/util/function/Function;Ljava/util/function/Function;)"
                                + "Lsouther/compiler/check/Settlement$Width;",
                        "souther.compiler.check.Settlement$Width#none()"
                                + "Lsouther/compiler/check/Settlement$Width;"),
                whatMakes("souther.compiler.check.Settlement$Width"),
                "what a choice is as wide as it is because of is compared in one place, and a"
                        + " second comparison rests on whichever pair of branches its writer held");
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
