package souther.architecture;


import org.junit.jupiter.api.Test;

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.reflect.AccessFlag;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who holds an allowance, and where an operation says which answer is paying.
 *
 * <p>An {@link souther.compiler.values.Allowance} is what one answer being built may spend. Held by
 * something that is building one, it is that answer's own account of itself. Held by a value that
 * answers, it is a fact about no reading in the value — and a composition of two such values has two
 * purses to be charged to, so which of them pays, and with it how exactly the composition comes out,
 * is settled by which side the call was written on rather than by anything either reading says.
 *
 * <p>So everywhere one can be reached from is written down, and every one of them is a reading that
 * has not finished answering or something holding one.
 *
 * <p><b>Reached from and not held in.</b> What a reader can spend is what it can reach, so a purse
 * put in a carrier that several readers keep is a purse those readers have. Asked one field deep,
 * the carrier would be the only row and the readers keeping it would hold an allowance with nothing
 * saying so — which is what this walk missed while the rule was being written, and is why it is
 * transitive.
 *
 * <p>Read off the compiled classes, so a record component is a row as readily as a field: the two
 * are one thing to a reader that can spend what it finds. A row that is new is a finding — either a
 * reading is being made somewhere new, or an answer has taken a purse on.
 */
class AnAllowanceIsHeldByWhoeverIsBuildingAnAnswerTest {

    private static final String ALLOWANCE = "souther/compiler/values/Allowance";

    private static final String CONJUNCTION = "souther/compiler/values/ConjoinedAdmissibleValues";

    private static final CompiledOutputs COMPILED = CompiledOutputs.ofWhatThisRepositoryPublishes();

    /**
     * Every production class an allowance can be reached from, which is every reading still
     * answering and everything holding one.
     *
     * <p>Two of them are the readers. {@code AdmissibleReading} turns one declaration's clauses into
     * the sets they leave; {@code PlacedRules} answers what the rules leave a value's positions and
     * builds the sets as they are asked for, so it is a reading that has not finished. The rest hold
     * one of those and reach the purse through it.
     *
     * <p>What is not here is the list's point. A reading's published answers —
     * {@code AdmissibleValues}, {@code ConjoinedAdmissibleValues}, {@code ConstraintState}, and the
     * seeded reading a later reader keeps — carry sets and no way to buy another, so two of them met
     * have no purse of their own to be composed under.
     */
    private static final List<String> HOLDING_AN_ALLOWANCE = List.of(
            "souther/compiler/check/AdmissibleReading",
            "souther/compiler/check/StatedByClauses$Reading",
            "souther/compiler/inputs/OpenedRules",
            "souther/compiler/inputs/PlacedRules",
            "souther/compiler/inputs/PlacedRules$Reaching",
            "souther/compiler/inputs/ReadQuantities",
            "souther/compiler/inputs/ReadRegion");

    /**
     * And what a conjunction of readings names one for.
     *
     * <p>{@link souther.compiler.values.ConjoinedAdmissibleValues} is the value the rule above is
     * about: two of them are met, so a purse it held would be the one the meet spent. It holds none,
     * and the allowance reaches it as the argument of the one operation that may build — the meet of
     * two readings over a shared vocabulary, which comes to a set neither of them holds.
     *
     * <p>Every other operation of it reads what is already there, and a second name in this row is
     * one of those having grown a way to spend. A getter is a row too, since what it hands back is
     * the purse itself.
     */
    private static final List<String> NAMING_AN_ALLOWANCE = List.of("meet");

    @Test
    void everyHolderOfAnAllowanceIsBuildingAnAnswer() {
        assertEquals(HOLDING_AN_ALLOWANCE, holdingAnAllowance(),
                "an allowance is what one answer being built may spend, so it is held by whoever is"
                        + " building one: held by a value that answers, a composition of two of them"
                        + " has two purses and picks by which side the call was written on");
    }

    @Test
    void andAConjunctionNamesOneOnlyWhereItComposes() {
        assertEquals(NAMING_AN_ALLOWANCE, namingAnAllowance(CONJUNCTION),
                "a conjunction is composed under the caller's allowance and holds none of its own:"
                        + " another operation naming one is either a second place that spends or a"
                        + " purse handed back for somebody else to spend");
    }

    /**
     * And the walk sees a holder that is there.
     *
     * <p>Matched on a descriptor nothing has, both lists would be empty and equal to an empty
     * expectation. So the same walk is asked for something it must find: the reading of a
     * declaration's clauses holds the purse it spends.
     */
    @Test
    void andTheWalkSeesAHolderThatIsThere() {
        assertTrue(holdingAnAllowance().contains("souther/compiler/check/AdmissibleReading"),
                "the reading that spends an allowance holds it, so a walk that cannot find that is"
                        + " finding nothing at all");
    }

    /**
     * And it sees the two ways a purse is held without being spelled at the holder.
     *
     * <p>The row above is a class with a field of that type, which the plainest walk finds. These
     * two are what a plain walk misses: a holder that reaches one through what it holds, and a
     * declared type the erasure throws away. Left unasked, both lists would match a walk that had
     * quietly stopped looking for either.
     */
    @Test
    void andItSeesAPurseHeldThroughAnotherAndOneInsideAContainer() {
        assertEquals(List.of(), fieldTypesOf("souther/compiler/inputs/OpenedRules").stream()
                        .filter(ALLOWANCE::equals).toList(),
                "this one names no allowance of its own");
        assertTrue(holdingAnAllowance().contains("souther/compiler/inputs/OpenedRules"),
                "and it holds a reading that has one, so it can spend what that reading has —"
                        + " a walk that stops at the first field cannot say so");

        assertTrue(typesIn("Ljava/util/List<L" + ALLOWANCE + "<TA;>;>;").contains(ALLOWANCE),
                "a purse inside a collection is a purse; the erasure leaves `java/util/List` and"
                        + " the declaration is where it is still named");
    }

    /** Every type named by a field of {@code owner}, for asking what it names of itself. */
    private static Set<String> fieldTypesOf(String owner) {
        Set<String> out = new LinkedHashSet<>();
        for (Path module : COMPILED.modules()) {
            for (ClassModel each : COMPILED.classesOf(module)) {
                if (each.thisClass().asInternalName().equals(owner)) {
                    each.fields().forEach(field -> out.addAll(typesIn(declared(field))));
                }
            }
        }
        return out;
    }

    /**
     * Every class that can reach an allowance through what it holds.
     *
     * <p><b>Through what it holds and not only in it.</b> A class holding a value that holds one has
     * the purse: what a reader of it can spend is what it can reach, however many fields away that
     * is. Read one field deep, a purse moved into a carrier several readers keep would be a row for
     * the carrier alone, and the readers keeping it would be holding an allowance with nothing
     * saying so — which is what happened while this rule was being written.
     *
     * <p><b>And what a field says of itself, not what the erasure left.</b> A field of {@code
     * List<Allowance>} is a field of {@code List}, so the type is gone from the descriptor and reads
     * here as no allowance at all. The declared type is in the signature where there is one, and
     * that is what is asked.
     */
    private static List<String> holdingAnAllowance() {
        Map<String, Set<String>> holds = new LinkedHashMap<>();
        for (Path module : COMPILED.modules()) {
            for (ClassModel each : COMPILED.classesOf(module)) {
                Set<String> reaching = new LinkedHashSet<>();
                for (FieldModel field : each.fields()) {
                    reaching.addAll(typesIn(declared(field)));
                }
                holds.put(each.thisClass().asInternalName(), reaching);
            }
        }
        // What reaches an allowance, and then what reaches that, until nothing new arrives.
        Set<String> reaches = new TreeSet<>(Set.of(ALLOWANCE));
        boolean growing = true;
        while (growing) {
            growing = false;
            for (Map.Entry<String, Set<String>> each : holds.entrySet()) {
                if (!reaches.contains(each.getKey())
                        && each.getValue().stream().anyMatch(reaches::contains)) {
                    reaches.add(each.getKey());
                    growing = true;
                }
            }
        }
        reaches.remove(ALLOWANCE);
        return new ArrayList<>(reaches);
    }

    /** What a field says its type is: the signature where the declaration has one, and the
     *  descriptor where it does not. */
    private static String declared(FieldModel field) {
        return field.findAttribute(Attributes.signature())
                .map(each -> each.signature().stringValue())
                .orElseGet(() -> field.fieldType().stringValue());
    }

    /** Every class named anywhere in a descriptor or signature, arguments of a generic type
     *  included. */
    private static Set<String> typesIn(String descriptor) {
        Set<String> out = new LinkedHashSet<>();
        Matcher found = NAMED.matcher(descriptor);
        while (found.find()) {
            out.add(found.group(1));
        }
        return out;
    }

    private static final Pattern NAMED = Pattern.compile("L([^;<>]+)[;<]");

    /**
     * The methods of {@code owner} a caller can reach whose signature mentions an allowance, by
     * name.
     *
     * <p>What a caller can reach, because the rule is about which of a value's questions come with
     * a purse. A private helper is part of how one of them is written and spends what that
     * operation was handed; what it must not do is make an allowance of its own, and that is a
     * different rule with a walk of its own
     * ({@code WhoMayBuildALanguageAboutAPositionTest} counts every making and every asking).
     */
    private static List<String> namingAnAllowance(String owner) {
        TreeSet<String> out = new TreeSet<>();
        for (Path module : COMPILED.modules()) {
            for (ClassModel each : COMPILED.classesOf(module)) {
                String here = each.thisClass().asInternalName();
                // The type and everything declared inside it. What may be asked of a conjunction
                // includes what its own nested types offer, and one of those taking a purse is the
                // same capability under another name.
                if (!here.equals(owner) && !here.startsWith(owner + "$")) {
                    continue;
                }
                String within = here.equals(owner) ? "" : here.substring(owner.length() + 1) + ".";
                for (MethodModel method : each.methods()) {
                    if (!method.flags().has(AccessFlag.PRIVATE)
                            && typesIn(declared(method)).contains(ALLOWANCE)) {
                        out.add(within + method.methodName().stringValue());
                    }
                }
            }
        }
        return new ArrayList<>(out);
    }

    /** What a method says its signature is: the declaration where it has one, and the descriptor
     *  where it does not — so an allowance inside a generic type is still named. */
    private static String declared(MethodModel method) {
        return method.findAttribute(Attributes.signature())
                .map(each -> each.signature().stringValue())
                .orElseGet(() -> method.methodType().stringValue());
    }








}
