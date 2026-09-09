package souther.architecture;

import souther.test.RepositoryLayout;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.InvokeInstruction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A reading builds a refusal by looking for both of its witnesses, and never by naming one.
 *
 * <p>An alternative is a conjunction of its sides, so one refused at a block and refused about
 * blocks together is refused both ways. A maker that takes one half is a maker whose caller has
 * already decided which witness to look for, and the thing it had to decide with is the other half
 * — so the refusal it builds names whichever of the two its author asked about first, and the
 * other is gone with nothing saying it was there. The entry that looks for both takes the two
 * questions instead of their answers, and neither of them can be put to the other.
 *
 * <p><b>Which is a rule about the readings and not about the makers.</b> Something that genuinely
 * proves one half proves one half: a walk over the ranges that refuses positions has no relation in
 * hand to read, and a refusal about the blocks it names is the whole of what it showed. Those
 * makers stay, and what is closed is that no reading builds an alternative's refusal out of one of
 * them.
 *
 * <p>Read off the compiled calls, since what a reading may not do is call it. A rule kept as a
 * habit is one the next producer is written without, and there have been four of them.
 */
class WhoMayBuildARefusalFromOneOfItsHalvesTest {

    private static final String WHERE = "souther/compiler/values/";

    private static final String REFUSAL = WHERE + "Refusal";

    /** The makers that take one half, each of which states the whole of what its caller showed. */
    private static final Set<String> ONE_HALF = Set.of("atEachOf", "ofThemTogether");

    /** The one that looks for both, which is what a reading refusing an alternative calls. */
    private static final String BOTH = "ofAnAlternative";

    /** A walk over the ranges that proves positions refused and holds no relation to read, which
     *  is what says the rule above is about the readings rather than about the makers. */
    private static final String PROVING_ONE_HALF =
            "souther/compiler/check/Confinement$Admission";

    private static final RepositoryLayout REPOSITORY = RepositoryLayout.ofWorkingDirectory();

    /**
     * No reading builds a refusal out of one of its halves.
     *
     * <p>Refusal itself is where the halves are put together, so it is the one place that names
     * them.
     */
    @Test
    void noReadingBuildsARefusalOutOfOneOfItsHalves() {
        List<String> naming = new ArrayList<>();
        for (ClassModel read : classesUnder(WHERE)) {
            String named = read.thisClass().name().stringValue();
            if (named.equals(REFUSAL) || named.startsWith(REFUSAL + "$")) {
                continue;
            }
            naming.addAll(callsTo(read, ONE_HALF));
        }

        assertEquals(List.of(), naming.stream().sorted().toList(),
                "a refusal built from one half is one whose other half nothing looked for, and"
                        + " which witness it holds is then the order two questions were asked in");
    }

    /**
     * And the readings build one by looking for both.
     *
     * <p>Without this the rule above is kept by a package that builds no refusals at all.
     */
    @Test
    void andTheReadingsBuildOneByLookingForBoth() {
        List<String> looking = new ArrayList<>();
        for (ClassModel read : classesUnder(WHERE)) {
            looking.addAll(callsTo(read, Set.of(BOTH)));
        }

        assertTrue(looking.size() > 1,
                "the readings that refuse an alternative call the entry that looks for both, and"
                        + " these call it: " + looking);
    }

    /**
     * And the walk finds a call to a half where one is made.
     *
     * <p>The same finder over something outside the rule, which proves that the rule is kept by
     * the readings and not by a walk that reads nothing. A refusal about the positions a walk over
     * the ranges refused is the whole of what that walk showed, so this is where a half is named
     * on purpose.
     */
    @Test
    void andTheWalkFindsACallToAHalfWhereOneIsMade() {
        assertTrue(!callsTo(CompiledClasses.ofWhatThisRepositoryPublishes().read(PROVING_ONE_HALF),
                        ONE_HALF).isEmpty(),
                "the finder reports nothing where a half is named, so it would report nothing"
                        + " wherever a reading named one");
    }

    /**
     * Where {@code read} calls one of {@code named} to make a refusal, named by what it is in.
     *
     * <p>The makers and not the halves they are named after. A refusal answers what it holds under
     * the same words its makers take it in by, so what tells a reading building one from a half
     * apart from a reading reading one back is that the first is a call to the type and the second
     * a call to a value of it.
     */
    private static List<String> callsTo(ClassModel read, Set<String> named) {
        List<String> out = new ArrayList<>();
        for (MethodModel method : read.methods()) {
            for (Instruction instruction : instructionsOf(method)) {
                if (instruction instanceof InvokeInstruction call
                        && call.opcode() == Opcode.INVOKESTATIC
                        && REFUSAL.equals(call.owner().name().stringValue())
                        && named.contains(call.name().stringValue())) {
                    out.add(read.thisClass().name().stringValue() + "#"
                            + method.methodName().stringValue() + " calls "
                            + call.name().stringValue());
                }
            }
        }
        return out;
    }

    private static List<Instruction> instructionsOf(MethodModel method) {
        return method.code().map(code -> code.elementList().stream()
                .filter(Instruction.class::isInstance)
                .map(Instruction.class::cast)
                .toList()).orElse(List.of());
    }

    private static List<ClassModel> classesUnder(String where) {
        List<ClassModel> out = new ArrayList<>();
        for (Path module : REPOSITORY.modules()) {
            Path found = module.resolve("target").resolve("classes").resolve(where);
            if (!Files.isDirectory(found)) {
                continue;
            }
            try (Stream<Path> each = Files.list(found)) {
                for (Path compiled : each.filter(p -> p.toString().endsWith(".class")).toList()) {
                    out.add(ClassFile.of().parse(Files.readAllBytes(compiled)));
                }
            } catch (IOException unread) {
                throw new UncheckedIOException(unread);
            }
        }
        assertTrue(out.size() > 1, "no class was read under " + where);
        return out;
    }
}
