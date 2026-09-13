package souther.compiler.inputs;

import org.junit.jupiter.api.Test;

import souther.compiler.DefaultStdlib;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.Symbols;
import souther.compiler.numeric.Count;
import souther.compiler.numeric.LinearForm;
import souther.compiler.query.ReadAs;
import souther.compiler.types.Type;
import souther.compiler.types.ValueName;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Every question a reading answers about a term or a position refuses one that is not of it.
 *
 * <p>Asked of the questions rather than of a list of them. Each of these answers about some place of
 * one behavior's input, and each has a way of coming back with something for a place of another: an
 * order that follows from the operation alone, a count nothing bounds, a range over rules that reach
 * nothing. A refusal is the only answer that is true of a reading that was never asked about that
 * place, and the check that says so has to be on every question rather than on the ones somebody
 * remembered — this file is written so that a question added later without it fails here.
 *
 * <p>What a question takes decides whether it is asked. A method that names a
 * {@link NumericTerm} or a {@link PositionId} is about a place and is held to the rule; one that
 * takes neither is about the reading as a whole and is listed below by name, so that a question
 * taking something new is a failure here rather than a silent exemption.
 */
class EveryQuestionAReadingAnswersIsAboutItsOwnInputTest {

    private static final Symbols SYMBOLS = Symbols.none(DefaultStdlib.get());

    private static final RuleReadingSource RULES = RuleReadings.ofNoClauseFiled(SYMBOLS);

    /** The questions that are about the reading itself and name no place in it. */
    private static final List<String> ABOUT_THE_WHOLE_READING = List.of("region", "emptiness");

    /** A reading of an input that takes one whole number, and nothing called {@code s}. */
    private static Quantities reading() {
        return InputDomain.of(List.of(new InputDomain.Parameter("n", null, Type.INT)),
                RULES, ReadAs.THE_COMPILATION_DOES).quantities(RULES);
    }

    /** A term of another input, whose root this reading takes nothing under. */
    private static NumericTerm foreignTerm() {
        NumericTerm.TakenOf made = NumericTerm.TakenOf.of(
                ValueName.Stdlib.operation("String", "length"), TermPath.of("s"), Type.STRING,
                souther.compiler.check.ScopedDeclarations.wrapsOf(SYMBOLS), SYMBOLS);
        assertNotNull(made, "a length is taken of a string");
        return made;
    }

    @Test
    void everyQuestionAboutAPlaceRefusesOneThisInputDoesNotHave() {
        Quantities reading = reading();
        List<String> asked = new ArrayList<>();
        List<String> other = new ArrayList<>();

        for (Method question : Quantities.class.getDeclaredMethods()) {
            if (question.isSynthetic()) {
                continue;
            }
            Object[] handed = foreignArgumentsFor(question);
            if (handed == null) {
                other.add(question.getName());
                continue;
            }
            asked.add(question.getName());
            InvocationTargetException raised = assertThrows(InvocationTargetException.class,
                    () -> question.invoke(reading, handed),
                    question.getName() + " answered about a place of another input");
            assertInstanceOf(IllegalArgumentException.class, raised.getCause(),
                    question.getName() + " refused it as something other than a question this"
                            + " reading has no answer for");
        }

        assertEquals(List.of(), other.stream().filter(each -> !ABOUT_THE_WHOLE_READING.contains(each))
                        .toList(),
                "a question was added that names neither a term nor a position: say which of the two"
                        + " it is about, or hold it to the rule above");
        assertEquals(true, asked.size() >= 4,
                "the questions about a place are being asked: " + asked);
    }

    /**
     * What to hand {@code question} so that everything it is about belongs to another input, or null
     * where it is about no place at all.
     *
     * <p>Built from the parameter types, so a question that starts taking a term is handed one
     * without anybody coming back here.
     */
    private static Object[] foreignArgumentsFor(Method question) {
        Object[] handed = new Object[question.getParameterCount()];
        boolean names = false;
        for (int at = 0; at < handed.length; at++) {
            Class<?> takes = question.getParameterTypes()[at];
            if (takes == NumericTerm.class || takes == NumericTerm.FromOnePosition.class) {
                handed[at] = foreignTerm();
                names = true;
            } else if (takes == PositionId.class) {
                handed[at] = new PositionId(TermPath.of("s"));
                names = true;
            } else if (takes == LinearForm.class) {
                handed[at] = LinearForm.atom(foreignTerm());
                names = true;
            } else if (takes == Map.class) {
                handed[at] = Map.of(foreignTerm(), Count.of(1));
                names = true;
            } else if (takes == Count.class) {
                handed[at] = Count.of(1);
            } else {
                return null;   // something this does not know how to make foreign
            }
        }
        return names ? handed : null;
    }
}
