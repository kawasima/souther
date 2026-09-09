package souther.bench;

import org.junit.jupiter.api.Test;
import souther.test.CompiledClasses;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What {@link Compiled.Invocation} reads at a call is the text written at it, and nothing else.
 *
 * <p>The rules over a document's named fields find their writers by that text, so a call this reads
 * a name at that was not written there is a writer counted for a field it does not write — and a
 * call it reads nothing at is a writer no rule about that field ever sees. The second is the worse
 * of the two: a check whose population can shrink in silence goes on looking complete.
 *
 * <p>So this holds the reading to both halves, over calls written here for the purpose. What it is
 * not is a claim that an argument can be recovered in general: running the program is what does
 * that, and a rule written as though this did it would be a rule about a guess.
 */
class TheTextReadAtACallIsTheOneWrittenThereTest {

    /** A call whose text is written at it. Called by nothing and read by name: what these two
     *  bodies are is what the tests below look up in this file's source. */
    @SuppressWarnings("UnusedMethod")
    private static int written() {
        return "incompleteness".length();
    }

    /** And one whose text is put somewhere first, so it is not written at the call. Read by name,
     *  as the one above is. */
    @SuppressWarnings("UnusedMethod")
    private static int throughALocal() {
        String name = "keptOpenBy";
        return name.length();
    }

    @Test
    void theTextWrittenAtACallIsReadAtIt() {
        assertEquals(List.of("incompleteness"), saidAt("written"));
    }

    @Test
    void textThatWentThroughSomethingElseIsNotReadAtTheCall() {
        assertEquals(List.of(), saidAt("throughALocal"),
                "the name reached the call through a local, so it is not the text written there —"
                        + " and a rule about a named field must not count this call for that name");
    }

    /** What was read at the one call {@code method} makes. */
    private static List<String> saidAt(String method) {
        for (Compiled.Invocation each : read()) {
            if (each.site().from().equals(TheTextReadAtACallIsTheOneWrittenThereTest.class.getName())
                    && each.site().at().contains("#" + method + "(")
                    && each.site().member().equals("length")) {
                return each.said();
            }
        }
        throw new AssertionError("the call this is about was not read: " + method);
    }

    /** This class as it was compiled, which is where the two calls above are. */
    private static List<Compiled.Invocation> read() {
        String named = TheTextReadAtACallIsTheOneWrittenThereTest.class.getName();
        return Compiled.invocationsIn(List.of(CompiledClasses
                .ofModule(TheTextReadAtACallIsTheOneWrittenThereTest.class)
                .find(named)
                .orElseThrow(() -> new AssertionError(named + " was not read back"))));
    }
}
