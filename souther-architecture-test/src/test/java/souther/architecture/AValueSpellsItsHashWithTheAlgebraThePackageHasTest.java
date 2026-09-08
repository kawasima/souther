package souther.architecture;

import souther.test.RepositoryLayout;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A value about a relation that works its own hash out takes it from the one place that says how.
 *
 * <p>These values are handed to things that add hashes up: a set sums what it holds, a map sums its
 * entries, a record carries its last component into its own number unchanged. So a hash gathered
 * from a value's parts and handed up as gathered leaves those parts separable there, and the sum
 * above cancels whatever the value was arranged to say about which part went with which. What
 * closes that is one finishing at the boundary of the value that owns the parts, and where the
 * finishing happens matters as much as that it happens — finishing a total after the sum has been
 * taken finishes a number the pairing has already left.
 *
 * <p>Which is why this is asked of the package rather than left to each value. Every one of these
 * hashes was written by somebody deciding afresh how to gather two things symmetrically, and the
 * one that decided on the plainest answer — the ends added — was the one that lost the pairing.
 *
 * <p><b>What this does not reach.</b> A value whose hash the compiler derives for it is not one
 * this can ask anything of, so a record here that leaves its hash alone is outside this population
 * however it is held. That is the right default: a hash written out by hand is one a component
 * added later can be left out of, which is a worse fault than the one this is about. What a record
 * here is held in is a question for whoever adds it to a set.
 */
class AValueSpellsItsHashWithTheAlgebraThePackageHasTest {

    private static final String WHERE = "souther/compiler/values/";

    private static final String THE_ALGEBRA = WHERE + "ValueHash";

    /** What a record's own hash is left to, which is nobody here deciding anything. */
    private static final String DERIVED = "java/lang/runtime/ObjectMethods";

    private static final RepositoryLayout REPOSITORY = RepositoryLayout.ofWorkingDirectory();

    @Test
    void everyValueThereThatWorksOutItsOwnHashTakesItFromTheAlgebra() {
        List<String> spellingItThemselves = new ArrayList<>();
        for (ClassModel read : valuesClasses()) {
            if (declaresAHash(read) && !reaches(read, THE_ALGEBRA)) {
                spellingItThemselves.add(read.thisClass().name().stringValue() + " " + shapeOf(read));
            }
        }

        assertEquals(List.of(), spellingItThemselves,
                "a value that gathers its own parts and hands the number up as gathered is one"
                        + " whose parts the sum above it can still take apart");
    }

    /**
     * And the population is the classes, not what a build happened to leave.
     *
     * <p>A walk finding no class would find no class spelling its own hash, and would pass while
     * answering about nothing.
     */
    @Test
    void andTheValuesThoseRulesAreAboutWereRead() {
        List<ClassModel> read = valuesClasses();

        assertTrue(read.size() > 1, "the classes about relations were not built here");
        assertTrue(read.stream().anyMatch(AValueSpellsItsHashWithTheAlgebraThePackageHasTest
                ::declaresAHash), "no value there works out a hash, which is not what these hold");
    }

    /**
     * Whether the class works out a number of its own rather than being given one.
     *
     * <p>A record has a {@code hashCode} whatever it does, and the one the compiler writes is a
     * handing-over: the whole of it is an {@code invokedynamic} that leaves the work to the runtime
     * and names the components it is over. That is not a hash anybody here decided, so it is not
     * one this is about.
     */
    private static boolean declaresAHash(ClassModel read) {
        for (MethodModel method : read.methods()) {
            if ("hashCode".equals(method.methodName().stringValue())
                    && "()I".equals(method.methodType().stringValue())) {
                return method.code().map(code -> code.elementList().stream()
                        .noneMatch(AValueSpellsItsHashWithTheAlgebraThePackageHasTest::handedOver))
                        .orElse(false);
            }
        }
        return false;
    }

    /** What the class's hash is made of, for saying so where one is reported. */
    private static String shapeOf(ClassModel read) {
        for (MethodModel method : read.methods()) {
            if ("hashCode".equals(method.methodName().stringValue())
                    && "()I".equals(method.methodType().stringValue())) {
                return method.code().map(code -> code.elementList().stream()
                        .map(element -> element instanceof InvokeDynamicInstruction dynamic
                                ? "indy:" + dynamic.invokedynamic().bootstrap().bootstrapMethod()
                                        .reference().owner().name().stringValue()
                                : element.getClass().getSimpleName())
                        .toList().toString()).orElse("(no code)");
            }
        }
        return "(no hash)";
    }

    /** Whether {@code element} is the handing-over a compiler writes for a record. */
    private static boolean handedOver(CodeElement element) {
        return element instanceof InvokeDynamicInstruction dynamic
                && DERIVED.equals(dynamic.invokedynamic().bootstrap().bootstrapMethod()
                        .reference().owner().name().stringValue());
    }

    /** Whether the class names {@code named} anywhere — a hash held in a field is worked out in a
     *  constructor, so the question is about the class and not about the method. */
    private static boolean reaches(ClassModel read, String named) {
        for (PoolEntry entry : read.constantPool()) {
            if (entry instanceof ClassEntry it && named.equals(it.name().stringValue())) {
                return true;
            }
        }
        return false;
    }

    private static List<ClassModel> valuesClasses() {
        List<ClassModel> out = new ArrayList<>();
        for (Path module : REPOSITORY.modules()) {
            Path where = module.resolve("target").resolve("classes").resolve(WHERE);
            if (!Files.isDirectory(where)) {
                continue;
            }
            try (Stream<Path> found = Files.list(where)) {
                for (Path each : found.filter(p -> p.toString().endsWith(".class")).toList()) {
                    out.add(parse(each));
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return out;
    }

    private static ClassModel parse(Path compiled) {
        try {
            return ClassFile.of().parse(Files.readAllBytes(compiled));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
