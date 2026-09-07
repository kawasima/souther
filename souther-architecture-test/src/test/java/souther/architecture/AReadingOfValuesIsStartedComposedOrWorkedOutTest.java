package souther.architecture;

import souther.test.RepositoryLayout;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Signature;
import java.lang.classfile.attribute.SignatureAttribute;
import java.lang.classfile.constantpool.MethodHandleEntry;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.reflect.AccessFlag;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every way a reading of worked-out values is made, and what each of them was handed to make it.
 *
 * <p>A rule of the model enters as a description ({@code PlannedValues}), and a description is
 * turned into values once, under an allowance, by {@code resolve}. That is where the rules of one
 * value's positions become sets — so a reading of values is either the empty one a reading starts
 * from, or one composed of readings already worked out, or one worked out from a description. What
 * there is no way to make is a leaf: a reading of values standing for a rule, made straight out of
 * a position, a set of values, or a reason a rule went unread, without a description having been
 * worked out.
 *
 * <p>A second vocabulary for that would be a second answer to what a rule of the values comes to,
 * and the two would not be the same answer. What {@code resolve} settles is what the allowance let
 * it build and what it went short of, and a reading minted beside it is one where those questions
 * were never asked — so a caller reads it as a reading whose every position was worked out. That is
 * the shape a compiler can never make and a test can, which is how tests came to be written against
 * readings the fold does not build.
 *
 * <p><b>What this does not say.</b> {@code AdmissibleValues} is a record with a public canonical
 * constructor, and this leaves that where it is: what is fixed here is the vocabulary a reading is
 * made by, not the representation. Composing two readings already worked out is not narrowed
 * either — a conjunction of them is what a declaration's clauses come to, and it is on this side of
 * {@code resolve} by design.
 *
 * <p>Read off the compiled classes, since a construction reached through a method reference makes a
 * reading as surely as one written out, and neither the enclosing declaration nor a switch over a
 * sealed type is visible to a scan of source text. Every module's, because a maker anywhere in the
 * repository is one of these.
 */
class AReadingOfValuesIsStartedComposedOrWorkedOutTest {

    private static final String READING = "souther/compiler/values/AdmissibleValues";

    private static final String DESCRIPTION = "souther/compiler/values/PlannedValues";

    private static final RepositoryLayout REPOSITORY = RepositoryLayout.ofWorkingDirectory();

    /** Handed nothing at all, which is what a reading starts from. */
    private static final String A_START = "handed nothing";

    /** Handed readings already worked out, which is what the connectives compose. */
    private static final String COMPOSED = "handed a reading";

    /** Handed a description, which is where the rules of a value become sets. */
    private static final String WORKED_OUT = "handed a description";

    /**
     * Every method that makes one, and what it was handed to make it with.
     *
     * <p>The four inside {@code AdmissibleValues} are the conjunction, the renaming, what a choice
     * left open and the writing down of the order the rules were read in, each handed the reading
     * it is composing — as {@code this}, which is a reading in hand as much as an argument is.
     * {@code top} is handed nothing. {@code resolved} is the one place a description becomes
     * values, and is the only maker outside the type.
     *
     * <p>{@code metAll} is not here, and that it is not is the reading holding: a conjunction of
     * several is written as one meet after another, so it makes nothing of its own.
     *
     * <p>A row whose warrant reads {@code handed nothing} and which takes an argument is the defect
     * this is about, and cannot be written: the warrant is read off the signature rather than
     * declared beside the row.
     */
    private static final List<String> MAKING_ONE = List.of(
            READING + "#alsoOpenedAt -> " + COMPOSED,
            READING + "#meet -> " + COMPOSED,
            READING + "#renamed -> " + COMPOSED,
            READING + "#sayingWhatWasReadInTheOrderOf -> " + COMPOSED,
            READING + "#top -> " + A_START,
            DESCRIPTION + "#resolved -> " + WORKED_OUT);

    @Test
    void everyMakerOfAReadingOfValuesWasHandedOneOrADescriptionOrNothing() {
        assertEquals(MAKING_ONE, new ArrayList<>(makers()),
                "a maker handed a position, a set of values or a reason a rule went unread is a"
                        + " second way of saying what a rule of the values comes to, and it says it"
                        + " without the allowance and the shortfall resolving one settles");
    }

    /**
     * And the walk found the maker outside the type, which is what it is for.
     *
     * <p>A reading of the class files that found only what {@code AdmissibleValues} does to itself
     * would be a reading that never reached the boundary the rule is about, and it would pass.
     */
    @Test
    void andTheOneMakerOutsideTheTypeWasReached() {
        assertTrue(makers().contains(DESCRIPTION + "#resolved -> " + WORKED_OUT),
                "the walk reaches the place a description becomes values");
        assertTrue(modulesRead() > 1,
                "the classes this reads are in more than the module that declares the reading");
    }

    /** Every method whose code makes a reading, as the method and the warrant its signature gives
     *  it. Sorted, so the rows do not turn on the order a walk of the file system took. */
    private static Set<String> makers() {
        Set<String> found = new TreeSet<>();
        for (Path module : REPOSITORY.modules()) {
            for (Path each : classesUnder(module)) {
                ClassModel owner = parse(each);
                for (MethodModel method : owner.methods()) {
                    if (makesAReading(method)) {
                        found.add(owner.thisClass().asInternalName() + "#"
                                + method.methodName().stringValue() + " -> "
                                + warrantOf(owner, method));
                    }
                }
            }
        }
        return found;
    }

    /**
     * What the method was handed, read off what its signature names.
     *
     * <p>An instance method of the reading is handed one as {@code this}, which is what makes the
     * connectives compositions rather than makers out of nothing. Everything else is read from the
     * parameters, through the generic signature where there is one: a list of readings is a reading
     * in hand, and the erasure that calls it a list would not say so.
     *
     * <p>A lambda is compiled to a method of its own, and this reads that method's own parameters —
     * what it captured and what it is applied to — rather than the ones the method around it took.
     * Which is the reading that holds: a lambda making a reading out of a set it captured has made
     * one out of a set, whatever was in scope where it was written.
     */
    private static String warrantOf(ClassModel owner, MethodModel method) {
        Set<String> handed = new LinkedHashSet<>();
        if (!method.flags().has(AccessFlag.STATIC)) {
            handed.add(owner.thisClass().asInternalName());
        }
        for (Signature each : parametersOf(method)) {
            names(each, handed);
        }
        if (handed.stream().anyMatch(each -> each.startsWith(DESCRIPTION))) {
            return WORKED_OUT;
        }
        if (handed.stream().anyMatch(each -> each.startsWith(READING))) {
            return COMPOSED;
        }
        // Handed nothing is a method with no parameters, and not one whose parameters name no
        // class of this repository. A maker taking two positions names only the variable they are
        // written as, and reading that as the empty hand would warrant the very leaf this is
        // about under the word a reading that starts from nothing is warranted by.
        return handed.isEmpty() ? A_START : "handed " + new TreeSet<>(handed);
    }

    /** The parameter types, generic where the method declares a signature of its own. */
    private static List<Signature> parametersOf(MethodModel method) {
        return method.findAttribute(Attributes.signature())
                .map(SignatureAttribute::asMethodSignature)
                .map(it -> List.<Signature>copyOf(it.arguments()))
                .orElseGet(() -> method.methodTypeSymbol().parameterList().stream()
                        .<Signature>map(Signature::of).toList());
    }

    /**
     * What one parameter is, as this reading names it: the classes its signature names, its type
     * arguments included, and the variable itself where it is written as one.
     *
     * <p>A variable is named rather than passed over, because what a hand holds is the question.
     * Passed over, a maker taking two positions would hold nothing this can name and would read as
     * one handed nothing — which is the warrant of the reading everything starts from, and would
     * cover the leaf this rule exists to refuse.
     */
    private static void names(Signature said, Set<String> out) {
        switch (said) {
            case Signature.ClassTypeSig it -> {
                out.add(internalNameOf(it.classDesc()));
                for (Signature.TypeArg argument : it.typeArgs()) {
                    if (argument instanceof Signature.TypeArg.Bounded bounded) {
                        names(bounded.boundType(), out);
                    }
                }
            }
            case Signature.ArrayTypeSig it -> names(it.componentSignature(), out);
            case Signature.TypeVarSig it -> out.add("a position written as " + it.identifier());
            case Signature.BaseTypeSig it -> out.add(it.signatureString());
            default -> { }
        }
    }

    private static String internalNameOf(ClassDesc said) {
        String descriptor = said.descriptorString();
        return descriptor.substring(1, descriptor.length() - 1);
    }

    /** Whether the method's own code makes a reading: written out, or handed to something that will
     *  make one, which a constructor reference is. */
    private static boolean makesAReading(MethodModel method) {
        return method.code().map(code -> code.elementStream().anyMatch(element -> switch (element) {
            case InvokeInstruction it -> READING.equals(it.owner().asInternalName())
                    && "<init>".equals(it.name().stringValue());
            case InvokeDynamicInstruction it -> it.invokedynamic().bootstrap().arguments().stream()
                    .anyMatch(AReadingOfValuesIsStartedComposedOrWorkedOutTest::makesAReading);
            default -> false;
        })).orElse(false);
    }

    private static boolean makesAReading(Object argument) {
        return argument instanceof MethodHandleEntry handle
                && handle.asSymbol() instanceof DirectMethodHandleDesc said
                && READING.equals(internalNameOf(said.owner()))
                && "<init>".equals(said.methodName());
    }

    private static int modulesRead() {
        int read = 0;
        for (Path module : REPOSITORY.modules()) {
            if (!classesUnder(module).isEmpty()) {
                read++;
            }
        }
        return read;
    }

    private static ClassModel parse(Path compiled) {
        try {
            return ClassFile.of().parse(Files.readAllBytes(compiled));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The compiled classes of one module that the compiler is made of. Its own tests are not among
     *  them: a test makes a reading to look at it and ships nothing. */
    private static List<Path> classesUnder(Path module) {
        Path where = module.resolve("target").resolve("classes");
        if (!Files.isDirectory(where)) {
            return List.of();
        }
        try (Stream<Path> found = Files.walk(where)) {
            return found.filter(p -> p.toString().endsWith(".class")).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
