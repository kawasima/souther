package souther.architecture;


import org.junit.jupiter.api.Test;

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.reflect.AccessFlag;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * <p>Which is why this is asked of the packages rather than left to each value. Every one of these
 * hashes was written by somebody deciding afresh how to gather two things symmetrically, and the
 * one that decided on the plainest answer — the ends added — was the one that lost the pairing.
 *
 * <p><b>And of more than one package, because where a value is written says nothing about how it is
 * hashed.</b> A relation between positions and the identity of a binding are both handed to
 * something that adds hashes up. A rule the values of one package keep and the values of the next
 * do not would be two answers to one question, and the second of them is the one that has the
 * defect. What is left outside is a value written somewhere else again, and this says nothing about
 * one.
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
class AValueSpellsItsHashWithTheOneAlgebraThereIsForItTest {

    private static final List<String> WHERE =
            List.of("souther/compiler/values/", "souther/compiler/types/");

    private static final String THE_ALGEBRA = "souther/compiler/hash/ValueHash";

    /** What a value implements where it names the value standing for it. */
    private static final String NAMES_ONE = "souther/compiler/hash/SaysWhatStandsForIt";

    /** And where the number it is asked for is one it worked out once and kept. */
    private static final String KEEPS_ONE = "souther/compiler/hash/KeepsTheNumberItIsAskedFor";

    /** What a record's own equality and hash are left to, which is nobody here deciding anything. */
    private static final String DERIVED = "java/lang/runtime/ObjectMethods";

    /** Asked of the number a hash hands back where which shape it was asked for is not the
     *  question. */
    private static final String ANY_SHAPE = "";

    private static final CompiledOutputs COMPILED = CompiledOutputs.ofWhatThisRepositoryPublishes();

    @Test
    void everyValueThereThatWorksOutItsOwnHashTakesItFromTheAlgebra() {
        List<String> gatheringItThemselves = new ArrayList<>();
        for (ClassModel read : theValues()) {
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
        for (ClassModel read : theValues()) {
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
        List<ClassModel> read = theValues();

        assertTrue(read.size() > 1, "the classes about relations were not built here");
        assertTrue(read.stream().anyMatch(each -> spelledOut(each, "hashCode", "()I").isPresent()),
                "no value there works out a hash, which is not what these hold");
        assertTrue(read.stream().anyMatch(
                        each -> each.findAttribute(Attributes.record()).isPresent()),
                "no value there is a record, which is not what these hold either");
    }

    /**
     * And a number a value keeps is over the value it says stands for it.
     *
     * <p>A value asked its number far more often than one is made works it out once and hands back
     * what it kept, and the algebra is then in the making rather than in the answer. What that
     * leaves open is which number was kept: a class can ask the algebra about anything at all and
     * put that away, and every rule above reads a hash that hands back a field as settled.
     *
     * <p>So what is followed here is where the number handed to the algebra came from. Between the
     * value putting away what stands for it and putting away its number, nothing of the value is
     * read but that one thing, and what is put away is what the algebra answered. A number gathered
     * from something else — a part held beside what stands for the value, a number handed in — is
     * one the equality does not read, and two values equal by what stands for them would be two
     * numbers.
     */
    @Test
    void andANumberAValueKeepsIsOverWhatItSaysStandsForIt() {
        List<String> failing = new ArrayList<>();
        List<ClassModel> keeping = implementers(KEEPS_ONE);
        for (ClassModel value : keeping) {
            String what = value.thisClass().name().stringValue();
            Optional<String> kept = spelledOut(value, "hashCode", "()I")
                    .flatMap(spelled -> handedBack(value, spelled));
            Optional<String> named = fieldNamed(value);
            if (kept.isEmpty()) {
                failing.add(what + " says it keeps a number and hands one back it worked out");
                continue;
            }
            if (named.isEmpty()) {
                failing.add(what + " hands back what stands for it without holding it");
                continue;
            }
            if (!keptOverWhatStandsForIt(value, named.get(), kept.get())) {
                failing.add(what + " keeps a number over something else it read");
            }
        }

        assertEquals(List.of(), failing,
                "a number kept over anything but what stands for the value is one two values equal"
                        + " by what stands for them can differ in");
        assertFalse(keeping.isEmpty(), "no value says it keeps the number it is asked for, which is"
                + " not what this compiler holds");
    }

    /**
     * And such a value holds what stands for it, the number it worked out, and nothing else.
     *
     * <p>What a record gave away when one of these stopped being one is the compiler's guarantee
     * that a component it is given is part of what it is. Held in one value again, that guarantee
     * is back — a component joins what stands for the value and the equality, the number and the
     * walk all read it — but only for as long as what stands for the value is where a part is put.
     * A field beside it is a part of the value that its equality does not read and its number is
     * not over, and nothing about writing one would say so.
     */
    @Test
    void andItHoldsWhatStandsForItAndTheNumberAndNothingElse() {
        List<String> holdingMore = new ArrayList<>();
        List<ClassModel> keeping = implementers(KEEPS_ONE);
        for (ClassModel value : keeping) {
            Optional<String> kept = spelledOut(value, "hashCode", "()I")
                    .flatMap(spelled -> handedBack(value, spelled));
            Optional<String> named = fieldNamed(value);
            if (kept.isEmpty() || named.isEmpty()) {
                // What each of those is is the rule above, which reports it.
                continue;
            }
            for (FieldModel field : value.fields()) {
                String held = field.fieldName().stringValue();
                if (!field.flags().has(AccessFlag.STATIC) && !held.equals(kept.get())
                        && !held.equals(named.get())) {
                    holdingMore.add(value.thisClass().name().stringValue() + " holds " + held);
                }
            }
        }

        assertEquals(List.of(), holdingMore,
                "a part held beside what stands for the value is one the equality does not read,"
                        + " which is what putting the parts in a value of their own is against");
        assertFalse(keeping.isEmpty(), "no value says it keeps the number it is asked for, which is"
                + " not what this compiler holds");
    }

    /**
     * And what a value says stands for it is a value it holds, whether or not it keeps a number.
     *
     * <p>Asked of every value that names one, and not only of the ones a number is kept by. A value
     * is asked this once for every number a term takes of it — far more often than one is made — so
     * one worked out at the ask puts back the walk that naming it is for. And it hands a different
     * object out each time, to a reader with no way to know that the two are one answer.
     */
    @Test
    void andWhatStandsForAValueIsSomethingItHolds() {
        List<String> built = new ArrayList<>();
        List<ClassModel> naming = implementers(NAMES_ONE);
        for (ClassModel value : naming) {
            if (fieldNamed(value).isEmpty()) {
                built.add(value.thisClass().name().stringValue());
            }
        }

        assertEquals(List.of(), built,
                "what stands for a value is worked out there rather than held, so a reader asking"
                        + " twice is answered twice and pays the walk that naming it is against");
        assertFalse(naming.isEmpty(), "no value names what stands for it, which is not what this"
                + " compiler holds");
    }

    /**
     * Every class this repository publishes that reaches {@code named}, through its own interfaces
     * or through one of theirs.
     *
     * <p>The population of a rule about these values comes from what they declare and not from the
     * shape the rule is about: a value found by looking for a number handed back out of a field is
     * a value that leaves the rule by no longer handing one back, which is the defect the rule is
     * against.
     *
     * <p>Classes, since what is asked of them is what their code does. An interface that carries
     * one of these on to another says which values are held to it and does nothing itself.
     */
    private static List<ClassModel> implementers(String named) {
        List<ClassModel> found = new ArrayList<>();
        for (ClassModel each : COMPILED.all()) {
            if (!each.flags().has(AccessFlag.INTERFACE) && reaches(each, named)) {
                found.add(each);
            }
        }
        return found;
    }

    /** Whether {@code read} declares {@code named} or declares something that reaches it. */
    private static boolean reaches(ClassModel read, String named) {
        for (ClassEntry each : read.interfaces()) {
            String spelled = each.name().stringValue();
            if (spelled.equals(named)) {
                return true;
            }
            if (COMPILED.find(spelled).filter(above -> reaches(above, named)).isPresent()) {
                return true;
            }
        }
        return false;
    }

    /** The value it hands back as standing for it, where that is something it holds. */
    private static Optional<String> fieldNamed(ClassModel value) {
        for (MethodModel method : value.methods()) {
            if (!"standsFor".equals(method.methodName().stringValue())) {
                continue;
            }
            List<Instruction> body = instructionsOf(method);
            if (body.size() == 3 && body.get(0) instanceof LoadInstruction
                    && body.get(1) instanceof FieldInstruction field
                    && field.opcode() == Opcode.GETFIELD
                    && value.thisClass().name().equals(field.owner().name())
                    && body.get(2) instanceof ReturnInstruction) {
                return Optional.of(field.name().stringValue());
            }
        }
        return Optional.empty();
    }

    /**
     * Whether every number put into {@code kept} was worked out from {@code named} and nothing
     * else.
     *
     * <p>Read as the stretch between the two puts. What a value does before it has what stands for
     * it is making that, and what it does after is not this; in between it reads one thing and
     * hands what it read to the algebra. A read of anything else there is a part of the number that
     * the equality does not read.
     */
    private static boolean keptOverWhatStandsForIt(ClassModel value, String named, String kept) {
        boolean put = false;
        for (MethodModel method : value.methods()) {
            List<Instruction> body = instructionsOf(method);
            int from = -1;
            for (int at = 0; at < body.size(); at++) {
                if (!(body.get(at) instanceof FieldInstruction field)
                        || field.opcode() != Opcode.PUTFIELD
                        || !value.thisClass().name().equals(field.owner().name())) {
                    continue;
                }
                if (named.equals(field.name().stringValue())) {
                    from = at;
                } else if (kept.equals(field.name().stringValue())) {
                    put = true;
                    if (from < 0 || !overNothingElse(value, body.subList(from + 1, at), named)) {
                        return false;
                    }
                }
            }
        }
        return put;
    }

    /** Whether the stretch reads {@code named}, reads nothing else the value holds, and hands the
     *  algebra's answer over. */
    private static boolean overNothingElse(ClassModel value, List<Instruction> stretch,
            String named) {
        boolean readIt = false;
        for (int at = 0; at < stretch.size(); at++) {
            Instruction instruction = stretch.get(at);
            if (instruction instanceof FieldInstruction field
                    && value.thisClass().name().equals(field.owner().name())) {
                if (!named.equals(field.name().stringValue())) {
                    return false;
                }
                readIt = true;
            }
            if (instruction instanceof InvokeInstruction call
                    && !"hashCode".equals(call.name().stringValue())
                    && !THE_ALGEBRA.equals(call.owner().name().stringValue())) {
                return false;
            }
        }
        return readIt && !stretch.isEmpty()
                && stretch.getLast() instanceof InvokeInstruction handing
                && THE_ALGEBRA.equals(handing.owner().name().stringValue());
    }

    /** The values these rules are about, wherever they are written. */
    private static List<ClassModel> theValues() {
        List<ClassModel> found = new ArrayList<>();
        for (String where : WHERE) {
            found.addAll(COMPILED.inTheClassesOf(where));
        }
        return found;
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
                        AValueSpellsItsHashWithTheOneAlgebraThereIsForItTest::handedOver);
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




}
