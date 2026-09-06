package souther.compiler.check;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Who may make a {@link PartId}, and how often.
 *
 * <p>The identity of an authored part is the clause it is a part of and its place among that
 * clause's parts, and the place is assigned where a clause is split into the parts its author wrote.
 * A second place that made one would be a second place that decided which part a part is — which is
 * the pair being assembled by hand again, under a type this time.
 *
 * <p>Held here rather than by the type, because a record cannot hide the constructor its own
 * representation gives it and this one is read by a report and by a reading of inputs. What the type
 * does close is the other half: only {@link ClauseHelpers} can make an
 * {@link ClauseHelpers.AuthoredPart}, so nobody else can assign the number this is made from.
 *
 * <p>Read off the compiled classes, because what a method calls is what the class file says.
 */
class OnePlaceMakesThePartIdEverybodyElseHoldsTest {

    private static final String PART_ID = "souther/compiler/check/PartId";

    /** A method that may make one, how many times it does, and why. */
    private record Licence(String who, int calls, String why) { }

    private static final List<Licence> MAY_MAKE = List.of(
            new Licence("souther.compiler.check.ClauseHelpers.AuthoredPart.idFor", 1,
                    "the one place a part the author wrote is given the rule it is a part of,"
                            + " which is where the place it holds among that clause's parts was"
                            + " assigned"));

    @Test
    void onlyTheSplitThatNumberedThePartsNamesOne() throws IOException {
        assertEquals(declared(MAY_MAKE), callsTo(PART_ID, "<init>"),
                "a part named anywhere else is a number somebody counted for themselves put beside"
                        + " whichever rule they were holding. What may make one, and why: "
                        + why(MAY_MAKE));
    }

    private static Map<String, Integer> declared(List<Licence> licences) {
        Map<String, Integer> out = new TreeMap<>();
        licences.forEach(each -> out.put(each.who(), each.calls()));
        return out;
    }

    private static Map<String, String> why(List<Licence> licences) {
        Map<String, String> out = new TreeMap<>();
        licences.forEach(each -> out.put(each.who(), each.why()));
        return out;
    }

    /** How many times each method of the compiler calls {@code owner.name}. */
    private static Map<String, Integer> callsTo(String owner, String name) throws IOException {
        Map<String, Integer> calls = new TreeMap<>();
        int read = 0;
        for (Path each : classes()) {
            ClassModel model = ClassFile.of().parse(Files.readAllBytes(each));
            read++;
            String from = model.thisClass().asInternalName().replace('/', '.').replace('$', '.');
            for (MethodModel method : model.methods()) {
                method.code().ifPresent(code -> code.forEach(element -> {
                    if (element instanceof InvokeInstruction call
                            && call.owner().asInternalName().equals(owner)
                            && call.name().stringValue().equals(name)) {
                        calls.merge(from + "." + method.methodName().stringValue(), 1, Integer::sum);
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
