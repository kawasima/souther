package souther.compiler.coverage;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Who takes a plan's numbering, written down with what makes it safe where it stands.
 *
 * <p>{@link CoverageSites.Plan#NONE} is the plan of nothing, and two different callers reach for
 * it: the emitter told to write no probes, and a measure of a module whose bodies did not come out.
 * For the first it is what it says. For the second it is a stand-in — and a stand-in has a
 * numbering, which is nobody's: not of that module, and over no places. A recording of the module
 * aligned against that is refused as a run of somewhere read under a numbering of nowhere, which is
 * right and is not the answer wanted. A module with no bodies has no numbering, and what a reader
 * without one has is no account of any run.
 *
 * <p>Which is why a reader that wants a numbering asks the check for the one it issued rather than
 * for a plan of its own to take one off. The check answers off the plan it made and kept, which is
 * of the bodies it holds; a reader reaching for any other plan is one branch away from the stand-in,
 * and where the bodies are there is no answer at all rather than an empty one.
 *
 * <p><b>What this holds is the population, and not that each of them is right.</b> A walk over the
 * compiled classes sees which method takes a numbering off a plan; it does not see which plan, so
 * it cannot tell a reader holding the checked bodies from one holding the stand-in. That is what
 * the reason beside each entry is for, and it is read by a person. What the check itself stops is a
 * new reader appearing without one — which is how the reader this was written for got in.
 *
 * <p>Read off the compiled classes, so what is counted is what a method does rather than what a
 * reading of the sources makes of it. The tests are not in {@code target/classes}, and what is read
 * is this module's classes.
 */
class WhoTakesAPlansNumberingHasItsBodiesTest {

    private static final String A_PLAN = "souther/compiler/coverage/CoverageSites$Plan";

    /** The two ways a plan hands its numbering over. */
    private static final Set<String> ITS_NUMBERING = Set.of("numbering", "identity");

    /** A method that may take it, how many times it does, and what says it has the bodies. */
    private record Licence(String who, int calls, String why) { }

    private static final List<Licence> MAY_TAKE = List.of(
            new Licence("souther.compiler.query.Bodies.Elaborated.numberingIdentity -> identity", 1,
                    "the plan this takes it off is the one the check walked these bodies to make"
                            + " and kept, so it is the numbering that check issued and there is no"
                            + " stand-in to have taken it from: a module whose bodies did not come"
                            + " out is answered for by there being no answer here at all. Taken"
                            + " rather than held beside the plan, so a reader cannot be handed two"
                            + " answers about one module's arms"),
            new Licence("souther.compiler.codegen.Backend.generating -> identity", 1,
                    "what the classes being written are numbered by, taken off the plan the check"
                            + " holds for the bodies it is emitting — and taken only where coverage"
                            + " was asked for, so it is never the stand-in's. The emission answers"
                            + " with it, rather than a caller working out a numbering of its own"
                            + " beside the one the probes were written from"));

    @Test
    void everyReaderOfAPlansNumberingIsWrittenDownWithWhatMakesItSafe() throws IOException {
        assertEquals(declared(), taken(),
                "a numbering taken off a plan that stands in for absent bodies is nobody's, and a"
                        + " reader aligning a recording against it is told the run was of somewhere"
                        + " else. So a reader arriving here is one to look at rather than one this"
                        + " can decide about. What takes one today, and what makes each safe: "
                        + why());
    }

    private static Map<String, Integer> declared() {
        Map<String, Integer> out = new TreeMap<>();
        MAY_TAKE.forEach(each -> out.put(each.who(), each.calls()));
        return out;
    }

    private static Map<String, String> why() {
        Map<String, String> out = new LinkedHashMap<>();
        MAY_TAKE.forEach(each -> out.put(each.who(), each.why()));
        return out;
    }

    /** How many times each method of the compiler takes a plan's numbering. */
    private static Map<String, Integer> taken() throws IOException {
        Map<String, Integer> calls = new TreeMap<>();
        int read = 0;
        for (Path each : classes()) {
            ClassModel model = ClassFile.of().parse(Files.readAllBytes(each));
            read++;
            String from = model.thisClass().asInternalName().replace('/', '.').replace('$', '.');
            for (MethodModel method : model.methods()) {
                method.code().ifPresent(code -> code.forEach(element -> {
                    if (element instanceof InvokeInstruction call
                            && call.owner().asInternalName().equals(A_PLAN)
                            && ITS_NUMBERING.contains(call.name().stringValue())) {
                        calls.merge(from + "." + method.methodName().stringValue()
                                + " -> " + call.name().stringValue(), 1, Integer::sum);
                    }
                }));
            }
        }
        assertFalse(read == 0, "no compiled class was read at all, so this says nothing");
        return calls;
    }

    private static List<Path> classes() throws IOException {
        Path root = Path.of("target", "classes").toAbsolutePath();
        try (Stream<Path> walk = Files.walk(root)) {
            return new ArrayList<>(walk.filter(p -> p.toString().endsWith(".class")).toList());
        }
    }
}
