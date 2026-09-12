package souther.compiler.check;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * What decided a choice contributes to the positions it depends on, and to nothing it is made of.
 *
 * <p>The two questions a choice is asked. Which positions the expression names includes what it
 * turned on — a reading of that position is one nothing about the expression is outside of — and
 * where its value came from does not, since none of the values the expression comes to are the ones
 * standing there.
 *
 * <p>Written over the algebra rather than over a model, because this is the law the arms of
 * {@link ValueOrigin} are built to state. What a reader of a body does with it is its own, and is
 * said where that reader is.
 */
class AChoiceDependsOnWhatDecidedItAndIsMadeOfWhatItChoosesBetweenTest {

    private static ValueOrigin<String> choiceOn(ValueOrigin<String> decidedBy,
                                                List<ValueOrigin<String>> alternatives) {
        return new ValueOrigin.OneOf<>(List.of(decidedBy), alternatives);
    }

    /** What it turned on is a position the expression names. */
    @Test
    void whatDecidedTheChoiceIsAmongThePositionsItNames() {
        assertEquals(List.of("flag", "a", "b"),
                List.copyOf(choiceOn(new ValueOrigin.IsAPosition<>("flag"),
                        List.of(new ValueOrigin.IsAPosition<>("a"),
                                new ValueOrigin.IsAPosition<>("b"))).positions()));
    }

    /** And it is not where the value came from, however the value it decided between was made. */
    @Test
    void whatDecidedTheChoiceIsNotWhereTheValueCameFrom() {
        assertNull(choiceOn(new ValueOrigin.MadeFromAPosition<>("xs[*]"),
                List.of(new ValueOrigin.IsAPosition<>("a"),
                        new ValueOrigin.IsAPosition<>("b"))).madeFrom());
    }

    /** A choice is made from the one position every value it may be came from. */
    @Test
    void aChoiceBetweenValuesFromOnePositionIsMadeFromThatPosition() {
        assertEquals("xs[*]", choiceOn(new ValueOrigin.IsAPosition<>("flag"),
                List.of(new ValueOrigin.MadeFromAPosition<>("xs[*]"),
                        new ValueOrigin.MadeFromAPosition<>("xs[*]"))).madeFrom());
    }

    /**
     * And from none where they came from two, the value being one of them and nothing saying which.
     */
    @Test
    void aChoiceBetweenValuesFromTwoPositionsIsMadeFromNeither() {
        assertNull(choiceOn(new ValueOrigin.IsAPosition<>("flag"),
                List.of(new ValueOrigin.MadeFromAPosition<>("xs[*]"),
                        new ValueOrigin.MadeFromAPosition<>("ys[*]"))).madeFrom());
    }

    /** And from none where one of them came from nowhere this can name. */
    @Test
    void aChoiceWithOneAlternativeFromNowhereIsMadeFromNowhere() {
        assertNull(choiceOn(new ValueOrigin.IsAPosition<>("flag"),
                List.of(new ValueOrigin.MadeFromAPosition<>("xs[*]"),
                        new ValueOrigin.Written<>())).madeFrom());
    }

    /** A choice between nothing is not a choice. */
    @Test
    void aChoiceStatesWhatItIsBetween() {
        assertThrows(IllegalArgumentException.class,
                () -> choiceOn(new ValueOrigin.IsAPosition<>("flag"), List.of()));
    }

    /**
     * And a path that comes to no value is not one of the values it may be.
     *
     * <p>Refused here rather than dropped, so that a walk which gathers the arms of a fork has to
     * say what it does with one that departs. Held as an alternative, it would answer for the value
     * beside the arms that have one: what the whole was made from and whether every value it may be
     * was made by an operation are both questions about the values, and a departure is the absence
     * of one.
     */
    @Test
    void aPathThatComesToNoValueIsNotOneOfTheValues() {
        assertThrows(IllegalArgumentException.class,
                () -> choiceOn(new ValueOrigin.IsAPosition<>("flag"),
                        List.of(new ValueOrigin.IsAPosition<>("a"), new ValueOrigin.NoValue<>())));
    }

    /** A path with no value on it names no position and came from none. */
    @Test
    void aPathWithNoValueNamesNothing() {
        assertEquals(List.of(), List.copyOf(new ValueOrigin.NoValue<String>().positions()));
        assertNull(new ValueOrigin.NoValue<String>().madeFrom());
    }
}
