package souther.architecture;

import org.junit.jupiter.api.Test;

import java.lang.classfile.ClassModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.constantpool.LoadableConstantEntry;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.MethodHandleEntry;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Where the lender is taken out of the world a reading is made in.
 *
 * <p>A walk over what an author wrote is handed the world it reads in — the rules, what a reading
 * may spend, and where it borrows what has already been made of a declaration — and hands the same
 * one on. That is what keeps a reading under way from being reached past: the world a producer
 * hands down is already bounded for the answer it is making
 * ({@code RuleReadingContext#whileTheAnswerIsMade}), and a reader below has no other to hand on.
 *
 * <p>Taking the lender out of the world is how that stops being true. Handed the lender, a reader
 * may put a world of its own together around it, or hand it to something that takes the three
 * apart — and the bound the producer put on it is not in either.
 *
 * <p><b>Being package-private is not what holds this.</b> Every class beside {@code
 * RuleReadingContext} in {@code souther.compiler.check} may read it, and the walk this is about
 * lives in that package. The rows below are what says which methods do.
 *
 * <p>Written per method and not per class. What is entitled is a bridge to the shape that still
 * takes the three apart, and it is entitled because it hands all three on unchanged. A class
 * holding one such bridge is not a class whose every method may reach the lender — least of all the
 * ones that are the walk.
 *
 * <p>Read off the compiled classes, and a reader that hands the accessor over to be called later is
 * one of these: a method handed to something that will call it reads the lender as surely as
 * calling it.
 *
 * <p>The rows are named rather than counted, which is what stands in for a control here. A walk
 * that stopped reading instructions comes back with nothing, and nothing is not what this expects —
 * so the comparison fails rather than passing on an extraction that reads nothing.
 */
class WhoMayTakeTheLenderOutOfAWorldIsWrittenDownTest {

    private static final String CHECK = "souther/compiler/check/";

    private static final String OWNER = CHECK + "RuleReadingContext";

    private static final String THE_LENDER = "readings";

    private static final CompiledOutputs COMPILED = CompiledOutputs.ofWhatThisRepositoryPublishes();

    /**
     * Every method that reaches it, and why each of them is entitled to.
     *
     * <p>Both are ways in to the shape that takes the rules, the budget and the lender apart, and
     * both hand over the three they were given. A reader calling one of these is reading in the
     * world it was handed, which is what the shape taking three arguments cannot say on its own.
     *
     * <p>The seeding's way in also reads it for what the revision worked out about where a set's
     * strings stop, which is a fact the revision has rather than a place to borrow a reading from.
     * It is a row all the same: what it holds is the lender, and a method that has the lender can
     * do anything with it that this is about.
     */
    private static final List<String> TAKING_IT_OUT = List.of(
            CHECK + "FieldDomains#of -> " + OWNER + "#" + THE_LENDER,
            CHECK + "InvariantChecker#<init> -> " + OWNER + "#" + THE_LENDER,
            CHECK + "InvariantChecker#seedFields -> " + OWNER + "#" + THE_LENDER);

    @Test
    void everyMethodThatTakesTheLenderOutOfAWorldIsWrittenDown() {
        assertEquals(TAKING_IT_OUT, List.copyOf(takingItOut()),
                () -> "the methods that take the lender out of a world are not the ones written"
                        + " down.\n  found: " + takingItOut() + "\n  written down: " + TAKING_IT_OUT
                        + "\nRead in the world rather than out of it, or say here why this method"
                        + " hands the lender on unchanged.");
    }

    /** Every method naming the accessor, as the method and what it named. */
    private static Set<String> takingItOut() {
        Set<String> found = new TreeSet<>();
        for (ClassModel model : COMPILED.all()) {
            String owner = model.thisClass().name().stringValue();
            for (MethodModel method : model.methods()) {
                for (Instruction instruction : instructionsOf(method)) {
                    if (namesTheLender(instruction)) {
                        found.add(owner + "#" + method.methodName().stringValue()
                                + " -> " + OWNER + "#" + THE_LENDER);
                    }
                }
            }
        }
        return found;
    }

    /**
     * Whether an instruction names it: by calling it, or by handing it over to be called later.
     *
     * <p>The second arrives as a handle among the arguments a bootstrap is given, which is what a
     * method reference comes to. A reader passing one along reaches the lender the same way a caller
     * does.
     */
    private static boolean namesTheLender(Instruction instruction) {
        return switch (instruction) {
            case InvokeInstruction call -> OWNER.equals(call.owner().name().stringValue())
                    && THE_LENDER.equals(call.name().stringValue());
            case InvokeDynamicInstruction handed -> {
                for (LoadableConstantEntry each : handed.invokedynamic().bootstrap().arguments()) {
                    if (each instanceof MethodHandleEntry handle) {
                        MemberRefEntry member = handle.reference();
                        if (OWNER.equals(member.owner().name().stringValue())
                                && THE_LENDER.equals(member.name().stringValue())) {
                            yield true;
                        }
                    }
                }
                yield false;
            }
            default -> false;
        };
    }

    private static List<Instruction> instructionsOf(MethodModel method) {
        Optional<java.lang.classfile.CodeModel> code = method.code();
        return code.map(each -> each.elementList().stream()
                .filter(Instruction.class::isInstance)
                .map(Instruction.class::cast)
                .toList()).orElse(List.of());
    }
}
