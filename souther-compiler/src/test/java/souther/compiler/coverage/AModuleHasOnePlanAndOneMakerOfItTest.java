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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A module's places are walked once, by the answer that holds its bodies.
 *
 * <p>A plan is filed by which {@link souther.compiler.core.Core} objects were put in it. Two checks
 * of one source build trees a record compares as equal, so a plan of one and a plan of the other
 * hold everything alike and answer for different objects — and a reader handed the wrong one gets
 * nothing back from every lookup, which is indistinguishable from a place that was never numbered.
 * So there is one plan of a module, kept by the check that walked the bodies for it, and everything
 * else is handed that one.
 *
 * <p><b>Both doors, because either alone leaves the other open.</b> The walk is one way in and the
 * constructor is the other: a caller assembling a plan out of another plan's parts never calls the
 * walk, and a caller walking the bodies a second time never calls the constructor. The second door
 * is shut by the language — the constructor is not public — and this counts the invocations left
 * inside the package, which is where a new one would appear.
 *
 * <p>Read off the compiled classes, so what is counted is what a method does rather than what a
 * reading of the sources makes of it. The tests are not in {@code target/classes}, so a plan built
 * by a fixture to drive a reader into a state no source reaches is not among these.
 */
class AModuleHasOnePlanAndOneMakerOfItTest {

    private static final String SITES = "souther/compiler/coverage/CoverageSites";

    private static final String A_PLAN = "souther/compiler/coverage/CoverageSites$Plan";

    /** Who walks a module's bodies for its places, and what makes it the one that may. */
    private static final Map<String, String> MAY_WALK = Map.of(
            "souther.compiler.query.Bodies.Checked.compute", "the check that holds the bodies. It"
                    + " walks them once, judges the claims against what that walk found, and keeps"
                    + " the plan on the answer, so every reader downstream is looking at that one");

    /** Who puts a plan together field by field, and what makes it the one that may. */
    private static final Map<String, String> MAY_CONSTRUCT = Map.of(
            "souther.compiler.coverage.CoverageSites.asPlan", "the end of the walk, where what it"
                    + " found becomes a plan. The checks in the constructor are for this caller:"
                    + " the fields are handed over one at a time and can be handed over out of"
                    + " step",
            "souther.compiler.coverage.CoverageSites.Plan.<clinit>", "the plan of nothing, which is"
                    + " what a reader is given where a module's bodies did not come out. It numbers"
                    + " no place and its numbering is nobody's, so a recording aligned against it"
                    + " is refused");

    @Test
    void onlyTheCheckThatHoldsTheBodiesWalksThemForAPlan() throws IOException {
        assertEquals(MAY_WALK.keySet(), calling(SITES, "of").keySet(),
                () -> "a second walk of one module's bodies makes a second plan of it, addressing"
                        + " objects the first plan does not. What walks them today, and what makes"
                        + " it the one that may: " + MAY_WALK);
    }

    @Test
    void andOnlyTheEndOfThatWalkPutsOneTogether() throws IOException {
        assertEquals(MAY_CONSTRUCT.keySet(), calling(A_PLAN, "<init>").keySet(),
                () -> "a plan assembled out of parts is an index into a graph its assembler does"
                        + " not own. What builds one today, and what makes it the one that may: "
                        + MAY_CONSTRUCT);
    }

    /** Which methods of this compiler invoke {@code name} on {@code owner}, and how often. */
    private static Map<String, Integer> calling(String owner, String name) throws IOException {
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
