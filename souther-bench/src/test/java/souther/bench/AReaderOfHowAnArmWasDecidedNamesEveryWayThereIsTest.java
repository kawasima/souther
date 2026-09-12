package souther.bench;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A reader that interprets how an arm of a choice was decided names every way there is, and none
 * asks after the one it knows.
 *
 * <p>What decides an arm is a sum ({@code Choice.Decides}), and it is a sum so that a way of
 * deciding added later stops every reader that interprets one until somebody has said what it means
 * there. Nothing but the readers themselves carries that. An exhaustive {@code switch} over a sealed
 * type stops at compile time; the same reader with a {@code default} arm does not, and neither does
 * one written as a chain of {@code instanceof}. All three are legal Java, and only the first has the
 * property the sum was chosen for.
 *
 * <p>Which is not hypothetical. What choosing an arm settles was written as an {@code instanceof},
 * and the ways it did not name were answered "nothing is settled" — so a choice whose arms were read
 * and whose conditions were dropped would have been bounded by a span over values it cannot take,
 * with nothing anywhere saying a reader had been missed.
 *
 * <p><b>The cases named, and not the instruction.</b> This check was first written as "no
 * {@code instanceof} against a way of deciding", which is a rule about the wrong thing: a
 * {@code switch} with a {@code default} compiles to the same {@code invokedynamic} as one without,
 * so the reader that the sum cannot stop reads here exactly like the reader it can. What tells them
 * apart is which cases were named, and that is what is asked. The {@code instanceof} half is kept
 * beside it, because a reader written that way names no case at all and would otherwise be a reader
 * this never looks at.
 *
 * <p>Read off the bytecode rather than the sources ({@link Compiled}): a case is written in as many
 * ways as Java has patterns, and the class it names is one thing in the class file whichever was
 * written.
 */
class AReaderOfHowAnArmWasDecidedNamesEveryWayThereIsTest {

    private static final String DECIDES = "souther.compiler.check.Choice$Decides";

    /**
     * The methods that interpret how an arm was decided, and what each answers.
     *
     * <p>Written out so that the readers of this question are a thing somebody decided rather than
     * whatever the check happened to find. A reader lost — a {@code switch} replaced by something
     * that reads the arms another way — is a reader nothing would otherwise miss, and a reader
     * gained is a fourth account of the same node, which is the answer this whole shape exists to
     * keep to one.
     */
    private static final Map<String, String> READERS = new LinkedHashMap<>(Map.of(
            "souther.compiler.check.Terms#chose", "what choosing the arm binds, and the value it"
                    + " opened. Both from one reader: a recipe wanting to say what that value's type"
                    + " guarantees needs the second half, and working it out for itself would be a"
                    + " second account of this node",
            "souther.compiler.check.Conditions#settledBy", "what choosing the arm states, as"
                    + " relations",
            "souther.compiler.check.InvariantChecker#opening", "what a walk over paths reads of it:"
                    + " the node it asks, and where choosing the arm puts the reading",
            "souther.compiler.check.ValueOrigin#decidedBy", "which expressions a reading of the"
                    + " choice depends on. Not what choosing an arm binds or settles, and not read"
                    + " off the fork: the node a value is one of several at says which several, and"
                    + " what each of them turns on is said per arm",
            "souther.compiler.inputs.InputReads#choosing", "the same question Terms#chose answers,"
                    + " in the vocabulary a reading of the inputs speaks. Two readings of one body"
                    + " name a position in two ways and each owns what a name means to it, as they"
                    + " do for a let's binding"));

    @Test
    void everyReaderOfHowAnArmWasDecidedNamesEveryWayThereIs() throws IOException {
        Set<String> ways = waysOfDeciding();
        Map<String, Set<String>> named = new LinkedHashMap<>();
        Set<String> asking = new LinkedHashSet<>();
        for (Compiled.Site site : Compiled.sites()) {
            if (!site.owner().startsWith(DECIDES)) {
                continue;
            }
            if (site.how() == Compiled.How.NAMES) {
                named.computeIfAbsent(withoutItsDescriptor(site.at()), one -> new TreeSet<>())
                        .add(site.owner());
            }
            if (site.how() == Compiled.How.ASKS) {
                asking.add(site.at());
            }
        }

        assertEquals(READERS.keySet(), named.keySet(),
                "the methods reading how an arm was decided are not the ones written down here."
                        + " One gained is a second account of a node that has an owner; one lost is"
                        + " a reader that stopped switching, and stopped being held to this");
        for (Map.Entry<String, Set<String>> reader : named.entrySet()) {
            assertEquals(ways, reader.getValue(),
                    reader.getKey() + " reads " + READERS.get(reader.getKey())
                            + ", and names some of the ways an arm is decided rather than all of"
                            + " them. A way it does not name is one it answers for without anybody"
                            + " having said what it means there — which is what the sum was chosen"
                            + " to turn into a compile error, and a `default` arm gives that up");
        }
        assertEquals(Set.of(), asking,
                "a reader asks after one way of deciding an arm instead of naming them all. Written"
                        + " that way it answers for the ways it was told about and passes the rest"
                        + " through, and no case it failed to name is missing from anywhere this"
                        + " can see");
    }

    /** Every way there is of deciding an arm, read off the sum rather than listed. */
    private static Set<String> waysOfDeciding() {
        Class<?> decides;
        try {
            decides = Class.forName(DECIDES);
        } catch (ClassNotFoundException notThere) {
            throw new AssertionError("what decides an arm of a choice is not on the class path,"
                    + " so this check has nothing to hold anybody to", notThere);
        }
        Class<?>[] arms = decides.getPermittedSubclasses();
        assertTrue(arms != null && arms.length > 1,
                "what decides an arm is a sum with arms, and this read " + (arms == null ? "none"
                        : arms.length) + " — so the comparison below would hold whatever a reader"
                        + " named");
        Set<String> out = new TreeSet<>();
        for (Class<?> arm : arms) {
            out.add(arm.getName());
        }
        return out;
    }

    /** A method by its name alone. What is written down here is which method answers a question,
     * and a signature that changed is not a different answer. */
    private static String withoutItsDescriptor(String at) {
        return at.substring(0, at.indexOf('(') < 0 ? at.length() : at.indexOf('('));
    }
}
