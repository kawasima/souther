package souther.architecture;

import org.junit.jupiter.api.Test;

import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nothing that reads a union's members decides for itself what order they come in.
 *
 * <p>A union holds a set, so a reader that walks the members is handed them in whatever order the
 * container they were put in walks — a fact about which producer built the value and about nothing
 * a program says. There is one order they come in and one place that says what it is, and a reader
 * that puts an order on them has written a second answer to a question the type does not have: the
 * two agree while nobody looks, and part company the day the order somebody decided is changed.
 *
 * <p><b>Read from the reading side and not from the one place.</b> Asked the other way round, this
 * would say that the place that orders them orders them, and a reader added later with a sort of
 * its own would be outside the question rather than the answer to it.
 *
 * <p><b>Ordering is what is looked for, not sorting.</b> A comparison sorts, and so does putting
 * the members in a container that keeps them in order — the members compare, so a tree of them
 * needs nobody to say by what. Both say the same thing about the reader: it decided.
 *
 * <p>What this is not about is a sequence of cases that is already one. A behavior's boundary holds
 * its output cases as a list, and a walk that orders those is answering about a list somebody
 * handed it rather than about a set; whether an order made for readers should be deciding anything
 * there is a different question and not this one.
 */
class NobodyReadingAUnionsMembersPutsAnOrderOnThemTest {

    private static final CompiledOutputs COMPILED = CompiledOutputs.ofWhatThisRepositoryPublishes();

    /** What a union hands its members over as. */
    private static final String THE_MEMBERS = "souther/compiler/types/Type$Union.members";

    /** Putting an order on things that compare, by comparing them or by keeping them in one. */
    private static final Set<String> ORDERING = Set.of(
            "java/util/stream/Stream.sorted",
            "java/util/List.sort",
            "java/util/Collections.sort",
            "java/util/Arrays.sort");

    private static final Set<String> KEEPS_AN_ORDER = Set.of(
            "java/util/TreeSet", "java/util/TreeMap");

    @Test
    void noReaderOfAUnionsMembersOrdersThemItself() {
        List<String> found = new ArrayList<>();
        int reading = 0;
        for (ClassModel each : COMPILED.all()) {
            for (MethodModel method : each.methods()) {
                if (!reads(method, THE_MEMBERS)) {
                    continue;
                }
                reading++;
                String ordering = ordersItself(method);
                if (ordering != null) {
                    found.add(each.thisClass().name().stringValue() + "."
                            + method.methodName().stringValue() + " reads a union's members and puts"
                            + " an order on them with " + ordering);
                }
            }
        }

        // The walk reaches readers, said out loud. One that found none would name nothing and pass
        // for the reason a clean one does.
        int reached = reading;
        assertTrue(reached > 1, () -> "the walk found no reader of a union's members, so what it"
                + " found is not what this compiler does with one: " + reached + " reached");
        assertEquals(List.of(), found,
                "a reader of a union's members says for itself what order they come in, and what it"
                        + " says stops agreeing with what a reader is shown the day that changes");
    }

    /** Whether {@code method} asks for what {@code named} hands over. */
    private static boolean reads(MethodModel method, String named) {
        for (Instruction each : instructions(method)) {
            if (each instanceof InvokeInstruction call && named.equals(where(call))) {
                return true;
            }
        }
        return false;
    }

    /** How {@code method} puts an order on what it read, or nothing where it puts none. */
    private static String ordersItself(MethodModel method) {
        for (Instruction each : instructions(method)) {
            if (each instanceof InvokeInstruction call && ORDERING.contains(where(call))) {
                return where(call);
            }
            if (each instanceof NewObjectInstruction made
                    && KEEPS_AN_ORDER.contains(made.className().name().stringValue())) {
                return made.className().name().stringValue();
            }
        }
        return null;
    }

    private static String where(InvokeInstruction call) {
        return call.owner().name().stringValue() + "." + call.name().stringValue();
    }

    private static List<Instruction> instructions(MethodModel method) {
        List<Instruction> out = new ArrayList<>();
        for (CodeModel code : method.code().stream().toList()) {
            for (var element : code) {
                if (element instanceof Instruction it) {
                    out.add(it);
                }
            }
        }
        return out;
    }
}
