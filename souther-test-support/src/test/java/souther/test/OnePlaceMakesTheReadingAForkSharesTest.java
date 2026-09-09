package souther.test;

import org.junit.jupiter.api.Test;

import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * That a fork reads a compiled output once rests on there being one reading for it to share.
 *
 * <p>A check counting what a reading touches says what that reading does; it says nothing about
 * whether the checks of a fork are all asking it. A second reading made anywhere would be a second
 * fork-wide store, filled from the same files, and every count either of them kept would go on
 * saying that <em>it</em> read once.
 *
 * <p><b>Read from the class files rather than from the sources.</b> A construction does not have to
 * be spelled {@code new}: a constructor named as a reference is a method handle in the bootstrap
 * arguments of an {@code invokedynamic} and puts no {@code new} in the caller's code at all, so a
 * search of the text is passed by exactly the spelling a second maker would most easily be written
 * in.
 *
 * <p>The population is what this module publishes. The type a fork's reading is made of is not
 * public, so a class that could name it is a class of this package, and those are here. A test
 * makes one of its own with a reading that counts what it touched, which is what asking the cost of
 * something requires and is not a second store for the fork to share.
 */
class OnePlaceMakesTheReadingAForkSharesTest {

    private static final List<ClassModel> PUBLISHED =
            CompiledClasses.ofModule(RepositoryLayout.class).all();

    private static final ClassDesc THE_READINGS =
            CompiledClassReadings.class.describeConstable().orElseThrow();

    private static final ClassDesc THE_BOUNDARY =
            ClassFiles.class.describeConstable().orElseThrow();

    @Test
    void is_made_in_one_place() {
        assertEquals(Set.of("souther.test.CompiledClassReadings#<clinit>"), whatMakesTheReadings(),
                "a fork's reading is made somewhere else as well, so the checks of one fork read"
                        + " the same files into more than one store");
    }

    @Test
    void is_the_only_way_a_class_file_is_touched() {
        Set<String> answering = new LinkedHashSet<>();
        for (ClassModel each : PUBLISHED) {
            if (reaches(each, new LinkedHashSet<>())) {
                answering.add(named(each));
            }
        }
        assertEquals(Set.of("souther.test.ReadClassFiles"), answering,
                "a second way of opening a class file is published here, so where compiled output"
                        + " is reached is no longer one place");
    }

    /**
     * Whether {@code model} touches class files, through however many types in between.
     *
     * <p>Directly or not. A type between the two is a way of being one of these and not a way of
     * not being one, and a rule reading only what the class itself declares would be met by a
     * second reading written as a subclass of the first.
     */
    private static boolean reaches(ClassModel model, Set<ClassDesc> seen) {
        List<ClassDesc> above = new ArrayList<>();
        model.superclass().ifPresent(each -> above.add(each.asSymbol()));
        model.interfaces().forEach(each -> above.add(each.asSymbol()));
        for (ClassDesc each : above) {
            if (each.equals(THE_BOUNDARY)) {
                return true;
            }
            if (!seen.add(each)) {
                continue;
            }
            ClassModel further = PUBLISHED.stream()
                    .filter(one -> one.thisClass().asSymbol().equals(each))
                    .findFirst().orElse(null);
            if (further != null && reaches(further, seen)) {
                return true;
            }
        }
        return false;
    }

    /** Every method that makes a reading, however the source spelled the construction. */
    private static Set<String> whatMakesTheReadings() {
        Set<String> found = new LinkedHashSet<>();
        for (ClassModel each : PUBLISHED) {
            for (MethodModel method : each.methods()) {
                CodeModel code = method.code().orElse(null);
                if (code == null) {
                    continue;
                }
                String at = named(each) + "#" + method.methodName().stringValue();
                for (var element : code) {
                    switch (element) {
                        case NewObjectInstruction made
                                when made.className().asSymbol().equals(THE_READINGS) ->
                                found.add(at);
                        case InvokeDynamicInstruction reference -> {
                            for (var argument : reference.bootstrapArgs()) {
                                if (argument instanceof DirectMethodHandleDesc handle
                                        && handle.kind() == DirectMethodHandleDesc.Kind.CONSTRUCTOR
                                        && handle.owner().equals(THE_READINGS)) {
                                    found.add(at);
                                }
                            }
                        }
                        default -> { }
                    }
                }
            }
        }
        return found;
    }

    private static String named(ClassModel of) {
        return of.thisClass().asInternalName().replace('/', '.');
    }
}
