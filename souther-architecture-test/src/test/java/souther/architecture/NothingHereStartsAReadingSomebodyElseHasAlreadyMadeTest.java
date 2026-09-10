package souther.architecture;

import org.junit.jupiter.api.Test;

import java.lang.classfile.ClassModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nothing this compiler runs reads a declaration that somebody has already read.
 *
 * <p>Reading a declaration is what a question about its rules costs, and the readings a question
 * needs beyond the first are readings of the same declaration again: attributing an end reads it
 * once per conjunct that could be holding one. So a reading is lent — made once for a declaration
 * under a revision, and handed to whoever asks next — and where the lending is reached is where the
 * reader says where it borrows from ({@code DeclarationReadings}).
 *
 * <p><b>A reader that says nothing gets nothing, and nothing about that fails.</b> The entry points
 * come in pairs: one takes the lending and one does not, and the second reads for itself and
 * answers the same. A caller that reached for the shorter one loses every reading the lending had
 * to give and is told by nothing at all — which is how a boundary search came to build a
 * declaration's string machines again for each value it probed.
 *
 * <p>So the pairs are what this walks, and the rule is about the callers rather than about the
 * shapes. The shorter entry points stay, because a test standing one declaration up to look at it
 * is a reader with no store and saying so is not a defect; what may not happen is this compiler
 * reaching for one while a store is answering.
 *
 * <p><b>Both halves of the pair are found rather than listed.</b> A name with two static entry
 * points, one taking the lending and one not, is a pair wherever it is written — so an entry point
 * added later is in the population the day it is written, and one whose partner is deleted leaves
 * the population the same day. A list of names would be a second answer to which entry points these
 * are, kept up by whoever remembered.
 *
 * <p>Read off the compiled classes, because what is being asked is which method a call site
 * resolved to. The overloads differ by one argument and the shorter is reached by leaving it out,
 * which is a fact about resolution rather than about the text: a walk over spellings would be
 * deciding overload resolution again, and getting it wrong quietly.
 */
class NothingHereStartsAReadingSomebodyElseHasAlreadyMadeTest {

    private static final CompiledOutputs COMPILED = CompiledOutputs.ofWhatThisRepositoryPublishes();

    private static final String LENDING = "souther/compiler/check/DeclarationReadings";

    private static final String LENDING_TYPE = "L" + LENDING + ";";

    /**
     * The readers that say outright that they read for themselves.
     *
     * <p>Each is a place where the lending cannot be reached rather than one where it would not
     * help, and they are all the same place: a walk over what an author wrote, handed what it is
     * reading and nothing that says where a reading comes from. Two are the named ways in that a
     * reader under such a walk takes; the other three build the walk itself, and what they build
     * has nowhere to put a lending. Borrowing in any of them would take a capability carried
     * through that walk, and whether it belongs there — and what a borrower asking for a reading
     * that is already being made should be handed — is not settled.
     *
     * <p>Named here so that what is unsettled can be counted. Whoever settles it takes these rows
     * away; until then they are what stands between this compiler and the shorter entry points, and
     * nothing else may say it reads for itself.
     */
    private static final List<String> READS_FOR_ITSELF = List.of(
            "souther/compiler/check/FieldDomains#unshared",
            "souther/compiler/check/InvariantChecker#seedFieldsUnshared",
            "souther/compiler/check/InvariantChecker#capabilityOf",
            "souther/compiler/check/ContractDischarge#of",
            "souther/compiler/check/PathReachability#of");

    /**
     * Every pair: a static method taking the lending, and one of the same name on the same class
     * that does not.
     *
     * <p>The shorter of each pair is what nothing here may call. Which they are is read off the
     * compiled classes and not decided here — a name with both shapes is a reader that could borrow
     * and a way of reaching it that does not.
     */
    private static Map<String, Set<MethodTypeDesc>> theShorterOfEachPair() {
        Map<String, Set<MethodTypeDesc>> found = new LinkedHashMap<>();
        for (ClassModel read : COMPILED.all()) {
            String owner = read.thisClass().name().stringValue();
            Map<String, List<MethodModel>> byName = new LinkedHashMap<>();
            for (MethodModel method : read.methods()) {
                if (method.flags().has(java.lang.reflect.AccessFlag.STATIC)) {
                    byName.computeIfAbsent(method.methodName().stringValue(),
                            each -> new ArrayList<>()).add(method);
                }
            }
            byName.forEach((name, overloads) -> {
                Set<MethodTypeDesc> shorter = new LinkedHashSet<>();
                for (MethodModel each : overloads) {
                    if (!lends(each) && overloads.stream()
                            .anyMatch(other -> reachedByLeavingTheLendingOut(each, other))) {
                        shorter.add(each.methodTypeSymbol());
                    }
                }
                if (!shorter.isEmpty()) {
                    found.put(owner + "#" + name, shorter);
                }
            });
        }
        return found;
    }

    /**
     * Whether {@code method} is itself one of the ways in that read for themselves.
     *
     * <p>Asked of what it takes and not of what it is called. Two overloads of one name are two
     * methods, and the one that takes a lending is a reader like any other — exempting it because
     * something of that name reads for itself would leave the reader this is about unread.
     */
    private static boolean readsForItself(Map<String, Set<MethodTypeDesc>> pairs, String from,
                                          MethodModel method) {
        Set<MethodTypeDesc> shorter = pairs.get(from);
        return shorter != null && shorter.contains(method.methodTypeSymbol());
    }

    /**
     * Whether {@code shorter} is what a caller gets by leaving the lending out of {@code lending}.
     *
     * <p>What tells the pairs from the overloads that merely differ. A reader handed something that
     * carries a lending of its own is not reading for itself, and the two would look alike to a
     * rule that only asked whether one of the shapes names the lending: a meaning read off clauses
     * already holds where those clauses borrow from, and the shape beside it that takes a source and
     * a lending is where the clauses are made.
     *
     * <p>So the shapes have to line up: everything the longer one takes before the lending, in the
     * order it takes them. A shorter one that takes something else takes something else, whatever
     * the two are called.
     */
    private static boolean reachedByLeavingTheLendingOut(MethodModel shorter, MethodModel lending) {
        if (!lends(lending)) {
            return false;
        }
        List<java.lang.constant.ClassDesc> taken = lending.methodTypeSymbol().parameterList().stream()
                .filter(each -> !LENDING_TYPE.equals(each.descriptorString()))
                .toList();
        List<java.lang.constant.ClassDesc> without = shorter.methodTypeSymbol().parameterList();
        return without.size() <= taken.size() && without.equals(taken.subList(0, without.size()));
    }

    /** Whether {@code method} is handed somewhere to borrow a reading from. */
    private static boolean lends(MethodModel method) {
        return method.methodTypeSymbol().parameterList().stream()
                .anyMatch(each -> LENDING_TYPE.equals(each.descriptorString()));
    }

    /**
     * The pairs exist, so that a walk finding none would not read as a walk finding no callers.
     *
     * <p>Named rather than counted. What this is about is that the population is derived, and a
     * derivation that came back empty because the shapes had been renamed would pass every row
     * below without looking at anything.
     */
    @Test
    void theEntryPointsThatReadForThemselvesAreFound() {
        Map<String, Set<MethodTypeDesc>> pairs = theShorterOfEachPair();

        assertTrue(pairs.containsKey("souther/compiler/check/FieldDomains#of"),
                () -> "what a record's rules leave has a way in that reads for itself: " + pairs);
        assertTrue(pairs.containsKey("souther/compiler/check/InvariantChecker#seedFields"),
                () -> "and so does the seeding it is read off: " + pairs);
    }

    /**
     * Nothing this compiler runs calls the shorter of a pair.
     *
     * <p>Every caller is listed rather than the first one found, because what a reader wants when
     * this fails is which readers lost their lending — the same list javac would have given had the
     * shorter entry points been deleted instead.
     */
    @Test
    void nothingHereReachesForTheEntryPointThatReadsForItself() {
        Map<String, Set<MethodTypeDesc>> pairs = theShorterOfEachPair();
        Set<String> reaching = new TreeSet<>();
        for (ClassModel read : COMPILED.all()) {
            String owner = read.thisClass().name().stringValue();
            for (MethodModel method : read.methods()) {
                String from = owner + "#" + method.methodName().stringValue();
                // The shorter entry point itself, which is a way in and not a reader. Told apart
                // from the one beside it by what it takes: a name is exempt here only in the shape
                // that leaves the lending out, so the partner that takes one is read like anything
                // else.
                if (readsForItself(pairs, from, method)) {
                    continue;
                }
                for (Instruction instruction : instructionsOf(method)) {
                    if (instruction instanceof InvokeInstruction call) {
                        String to = call.owner().name().stringValue() + "#"
                                + call.name().stringValue();
                        Set<MethodTypeDesc> shorter = pairs.get(to);
                        if (shorter != null && shorter.contains(call.typeSymbol())) {
                            reaching.add(from + " -> " + to + call.typeSymbol().descriptorString());
                        }
                    }
                }
            }
        }

        assertEquals(Set.of(), reaching,
                () -> "these read a declaration for themselves where a store was answering, and"
                        + " each of them pays every reading the lending had to give:\n  "
                        + String.join("\n  ", reaching)
                        + "\nTake the entry point that is handed somewhere to borrow from, or say"
                        + " outright that this reader has nowhere to borrow from.");
    }

    /**
     * And saying so outright is the two readers that say it.
     *
     * <p>The other half of the rule. Left at the first alone, a reader that had lost its lending
     * could say so and be done, and the count of what is unsettled would move whenever somebody
     * found the shorter way blocked.
     *
     * <p>The shorter entry points name it too, because saying that a reader has nowhere to borrow
     * from is the whole of what they are.
     */
    @Test
    void onlyTheTwoThatSaySoNameTheLendingThereIsNothingToBorrowFrom() {
        Map<String, Set<MethodTypeDesc>> pairs = theShorterOfEachPair();
        Set<String> naming = new TreeSet<>();
        for (ClassModel read : COMPILED.all()) {
            String owner = read.thisClass().name().stringValue();
            for (MethodModel method : read.methods()) {
                String from = owner + "#" + method.methodName().stringValue();
                if (readsForItself(pairs, from, method) || READS_FOR_ITSELF.contains(from)) {
                    continue;
                }
                for (Instruction instruction : instructionsOf(method)) {
                    if (instruction instanceof FieldInstruction field
                            && field.opcode() == Opcode.GETSTATIC
                            && LENDING.equals(field.owner().name().stringValue())
                            && "NONE".equals(field.name().stringValue())) {
                        naming.add(from);
                    }
                }
            }
        }

        assertEquals(Set.of(), naming,
                () -> "these say they have nowhere to borrow a reading from, which is a thing to"
                        + " settle rather than to write:\n  " + String.join("\n  ", naming)
                        + "\nHand the reader somewhere to borrow from, or add it to the readers"
                        + " that say outright that they read for themselves.");
    }

    /**
     * And each of those two is reached, so that a name left behind by a rename reads as a row here
     * rather than as a licence nobody uses.
     */
    @Test
    void eachReaderThatSaysSoIsThere() {
        Set<String> written = new LinkedHashSet<>();
        for (ClassModel read : COMPILED.all()) {
            String owner = read.thisClass().name().stringValue();
            for (MethodModel method : read.methods()) {
                written.add(owner + "#" + method.methodName().stringValue());
            }
        }

        List<String> gone = READS_FOR_ITSELF.stream().filter(each -> !written.contains(each))
                .toList();
        assertEquals(List.of(), gone,
                () -> "these are licensed to read for themselves and are not written: " + gone);
    }

    /** Whether anything at all was read, so that an empty population fails rather than passes. */
    @Test
    void theCompiledClassesWereRead() {
        assertFalse(COMPILED.all().isEmpty(), "this repository compiled nothing to read");
    }

    private static List<Instruction> instructionsOf(MethodModel method) {
        Optional<java.lang.classfile.CodeModel> code = method.code();
        return code.map(each -> each.elementList().stream()
                .filter(Instruction.class::isInstance)
                .map(Instruction.class::cast)
                .toList()).orElse(List.of());
    }
}
