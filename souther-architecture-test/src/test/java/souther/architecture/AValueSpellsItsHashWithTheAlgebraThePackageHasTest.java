package souther.architecture;

import souther.test.RepositoryLayout;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A value about a relation that works its own hash out takes it from the one place that says how,
 * and a value the compiler works one out for is left alone.
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
 * <p><b>Asked of the number the hash hands back, and not of what the class mentions.</b> A class
 * that names the algebra somewhere and gathers its own parts in {@code hashCode} is the defect this
 * is about, written next to its own remedy. So what is followed here is where the number comes
 * from: the hash asks the algebra, or it hands back a field nothing puts a number into but the
 * algebra. The second is what a value that is asked its number far more often than one is made
 * does, and it is not a way around the first.
 *
 * <p><b>And what a record leaves to the compiler is left there.</b> A hash written out by hand is
 * one a component added later can be left out of, and an equality written out by hand is one a
 * component added later is not part of at all — the first costs a collision, the second takes the
 * component out of what the value is. So a record here spells its own number where the number is
 * what this is about, and never its own equality: the generated one is over every component it has
 * and over every component it is given.
 */
class AValueSpellsItsHashWithTheAlgebraThePackageHasTest {

    private static final String WHERE = "souther/compiler/values/";

    private static final String THE_ALGEBRA = WHERE + "ValueHash";

    /** What a record's own equality and hash are left to, which is nobody here deciding anything. */
    private static final String DERIVED = "java/lang/runtime/ObjectMethods";

    /** Asked of the number a hash hands back where which shape it was asked for is not the
     *  question. */
    private static final String ANY_SHAPE = "";

    private static final RepositoryLayout REPOSITORY = RepositoryLayout.ofWorkingDirectory();

    @Test
    void everyValueThereThatWorksOutItsOwnHashTakesItFromTheAlgebra() {
        List<String> gatheringItThemselves = new ArrayList<>();
        for (ClassModel read : valuesClasses()) {
            Optional<MethodModel> spelled = spelledOut(read, "hashCode", "()I");
            if (spelled.isPresent() && !fromTheAlgebra(read, spelled.get())) {
                gatheringItThemselves.add(read.thisClass().name().stringValue());
            }
        }

        assertEquals(List.of(), gatheringItThemselves,
                "a value that gathers its own parts and hands the number up as gathered is one"
                        + " whose parts the sum above it can still take apart");
    }

    /**
     * And a record spells its own equality where, and only where, its equality is not what its
     * components in their places come to.
     *
     * <p>Which the number says: a record asks the algebra for the shape its equality has, and one
     * of those shapes is a pair with no order between its ends. That one the compiler cannot write
     * — the generated equality is component by component, so a pair stated the other way round
     * would be another value while being one number, and the pair would not be unordered at all.
     * Every other shape here is what the generated one already says, and writing it out again is
     * writing something the next component this record is given is not part of.
     */
    @Test
    void andARecordThereSpellsItsOwnEqualityWhereAndOnlyWhereTheGeneratedOneWouldSayAnother() {
        List<String> disagreeing = new ArrayList<>();
        for (ClassModel read : valuesClasses()) {
            if (read.findAttribute(Attributes.record()).isEmpty()) {
                continue;
            }
            boolean spellsIt = spelledOut(read, "equals", "(Ljava/lang/Object;)Z").isPresent();
            boolean unordered = spelledOut(read, "hashCode", "()I")
                    .filter(hash -> handsBack(hash, "ofAnUnorderedPair")).isPresent();
            if (spellsIt != unordered) {
                disagreeing.add(read.thisClass().name().stringValue()
                        + (spellsIt ? " writes an equality the compiler would have written"
                                : " leaves an equality that says another thing than its number"));
            }
        }

        assertEquals(List.of(), disagreeing,
                "an equality written out by hand is one the next component this record is given is"
                        + " not part of, and one left generated beside an unordered number is one"
                        + " that reads the ends in the order they were written");
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
        assertTrue(read.stream().anyMatch(each -> spelledOut(each, "hashCode", "()I").isPresent()),
                "no value there works out a hash, which is not what these hold");
        assertTrue(read.stream().anyMatch(
                        each -> each.findAttribute(Attributes.record()).isPresent()),
                "no value there is a record, which is not what these hold either");
    }

    /**
     * The method of that name the class wrote itself, where it wrote one.
     *
     * <p>A record has both of these whatever it does, and the ones the compiler writes are a
     * handing-over: the whole of each is an {@code invokedynamic} that leaves the work to the
     * runtime and names the components it is over. That is nobody here deciding anything.
     */
    private static Optional<MethodModel> spelledOut(ClassModel read, String named, String taking) {
        for (MethodModel method : read.methods()) {
            if (named.equals(method.methodName().stringValue())
                    && taking.equals(method.methodType().stringValue())) {
                boolean handedOver = instructionsOf(method).stream().anyMatch(
                        AValueSpellsItsHashWithTheAlgebraThePackageHasTest::handedOver);
                return handedOver ? Optional.empty() : Optional.of(method);
            }
        }
        return Optional.empty();
    }

    /** Whether the number {@code hash} hands back is one the algebra answered. */
    private static boolean fromTheAlgebra(ClassModel read, MethodModel hash) {
        if (handsBack(hash, ANY_SHAPE)) {
            return true;
        }
        return handedBack(read, hash).filter(field -> putThereByTheAlgebra(read, field)).isPresent();
    }

    /**
     * Whether the method hands back what the algebra answered, of {@code shape} where one is named.
     *
     * <p>Read as the answer being returned and not as the call being made. A method that asks the
     * algebra and hands back something else has asked and answered separately, which is the defect
     * this is about with its own remedy standing beside it.
     */
    private static boolean handsBack(MethodModel method, String shape) {
        List<Instruction> body = instructionsOf(method);
        for (int at = 0; at + 1 < body.size(); at++) {
            if (body.get(at) instanceof InvokeInstruction call
                    && THE_ALGEBRA.equals(call.owner().name().stringValue())
                    && (ANY_SHAPE.equals(shape) || shape.equals(call.name().stringValue()))
                    && body.get(at + 1) instanceof ReturnInstruction) {
                return true;
            }
        }
        return false;
    }

    /** The field a hash hands back where the whole of it is handing one back, which is what a value
     *  asked its number far more often than one is made holds. */
    private static Optional<String> handedBack(ClassModel read, MethodModel hash) {
        List<Instruction> body = instructionsOf(hash);
        if (body.size() == 3 && body.get(0) instanceof LoadInstruction
                && body.get(1) instanceof FieldInstruction field
                && field.opcode() == Opcode.GETFIELD
                && read.thisClass().name().equals(field.owner().name())
                && body.get(2) instanceof ReturnInstruction) {
            return Optional.of(field.name().stringValue());
        }
        return Optional.empty();
    }

    /**
     * Whether nothing but the algebra puts a number into {@code field}.
     *
     * <p>Read as the instruction before each of the puts, which is where the number being put comes
     * from when it comes from a call. A put that took its number from anywhere else — a gathering
     * written out beside the call, a number handed in — is one this does not admit, and the class
     * that wrote it is reported as gathering its own.
     */
    private static boolean putThereByTheAlgebra(ClassModel read, String field) {
        boolean put = false;
        for (MethodModel method : read.methods()) {
            Instruction before = null;
            for (Instruction instruction : instructionsOf(method)) {
                if (instruction instanceof FieldInstruction it && it.opcode() == Opcode.PUTFIELD
                        && read.thisClass().name().equals(it.owner().name())
                        && field.equals(it.name().stringValue())) {
                    put = true;
                    if (!(before instanceof InvokeInstruction call)
                            || !THE_ALGEBRA.equals(call.owner().name().stringValue())) {
                        return false;
                    }
                }
                before = instruction;
            }
        }
        return put;
    }

    /** Whether {@code instruction} is the handing-over a compiler writes for a record. */
    private static boolean handedOver(Instruction instruction) {
        return instruction instanceof InvokeDynamicInstruction dynamic
                && DERIVED.equals(dynamic.invokedynamic().bootstrap().bootstrapMethod()
                        .reference().owner().name().stringValue());
    }

    private static List<Instruction> instructionsOf(MethodModel method) {
        return method.code().map(code -> code.elementList().stream()
                .filter(Instruction.class::isInstance)
                .map(Instruction.class::cast)
                .toList()).orElse(List.of());
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
