package souther.architecture;

import org.junit.jupiter.api.Test;

import java.lang.classfile.ClassModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.constantpool.LoadableConstantEntry;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.MethodHandleEntry;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
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
 * Every place this compiler starts a reading of a declaration that somebody else has already made
 * is written down here.
 *
 * <p>Reading a declaration is what a question about its rules costs, and the readings a question
 * needs beyond the first are readings of the same declaration again: attributing an end reads it
 * once per conjunct that could be holding one. So a reading is lent — made once for a declaration
 * under a revision and handed to whoever asks next — and where the lending is reached, a reader
 * says where it borrows from ({@code DeclarationReadings}).
 *
 * <p><b>A reader that says nothing gets nothing, and nothing about that fails.</b> The entry points
 * come in pairs: one takes the lending and one does not, and the second reads for itself and
 * answers the same. A caller that reached for the shorter one loses every reading the lending had
 * to give and is told by nothing at all — which is how a boundary search came to build a
 * declaration's string machines again for each value it probed.
 *
 * <p><b>What is written down is the edge and not the place it arrives at.</b> A reader that starts
 * such a reading is reached by a call, and a table of readers says nothing about who is calling
 * them: a way in named here as allowed would let a new caller of it arrive with nothing to fail.
 * So each row is one production instruction reaching one place, both ends said in full, and the
 * whole set is compared — a row that has gone is as much a finding as one that has appeared,
 * because a reader that stops reading for itself is what settles this and the row has to go with
 * it.
 *
 * <p>The shorter entry points stay, because a test standing one declaration up to look at it is a
 * reader with no store and saying so is not a defect. What may not happen is this compiler reaching
 * for one without a row here.
 *
 * <p>Read off the compiled classes, because what is being asked is which method a call site
 * resolved to. The overloads differ by one argument and the shorter is reached by leaving it out,
 * which is a fact about resolution rather than about the text: a walk over spellings would be
 * deciding overload resolution again, and getting it wrong quietly.
 *
 * <p><b>Every way an instruction names a method.</b> A call is one; handing the method over to be
 * called later is another, and that arrives as a handle among the arguments a bootstrap is given.
 * A reader that passes one of these along rather than calling it starts the same reading.
 */
class NothingHereStartsAReadingSomebodyElseHasAlreadyMadeTest {

    private static final CompiledOutputs COMPILED = CompiledOutputs.ofWhatThisRepositoryPublishes();

    private static final String LENDING = "souther/compiler/check/DeclarationReadings";

    private static final String LENDING_TYPE = "L" + LENDING + ";";

    /** What a row names when a reader takes nothing to borrow from rather than reaching a way in
     *  that does. */
    private static final String NOTHING_TO_BORROW_FROM = LENDING + "#NONE";

    /**
     * Every edge this compiler has into a reading nobody else made, and why each of them is one.
     *
     * <p>Two kinds, and both are the same fact said at a different distance. A reader that names
     * {@link #NOTHING_TO_BORROW_FROM} is starting such a reading itself; one that calls a way in is
     * starting it a call away. Written together because what is being counted is the places, and
     * which of the two shapes a place happens to have is not what anybody has to settle.
     *
     * <p>Each of them sits under a walk over what an author wrote, which is handed what it is
     * reading and nothing that says where a reading comes from. Some are the readers under such a
     * walk and some build the walk itself; borrowing in any of them would take a capability carried
     * through it, and whether it belongs there — and what a borrower asking for a reading that is
     * already being made should be handed — is not settled.
     *
     * <p>Settling it takes these rows away, and taking one away without settling it is what the
     * comparison below refuses in the other direction.
     */
    private static final String CHECK = "souther/compiler/check/";

    private static final String SOURCE_AND_POLICY =
            "L" + CHECK + "RuleReadingSource;L" + CHECK + "ReadingPolicy;";

    private static final Set<String> EDGES_INTO_A_READING_OF_ONES_OWN = Set.of(
            // The two ways in themselves, which is where a reading with nothing to borrow from is
            // started for whoever asked.
            CHECK + "FieldDomains#unshared(Lsouther/compiler/types/TypeSymbol$AtModule;"
                    + SOURCE_AND_POLICY + "Ljava/util/Map;)L" + CHECK + "FieldDomains; -> "
                    + NOTHING_TO_BORROW_FROM,
            CHECK + "InvariantChecker#seedFieldsUnshared("
                    + "Lsouther/compiler/types/TypeSymbol$AtModule;" + SOURCE_AND_POLICY + ")L"
                    + CHECK + "InvariantChecker$Seeded; -> " + NOTHING_TO_BORROW_FROM,
            // Choosing what stands at each field of a record, under the composing of a value: it is
            // handed a plan and a strategy for filling it, and neither carries a lending.
            "souther/compiler/partition/Partitions#fieldsOf("
                    + "Lsouther/compiler/types/TypeSymbol$AtModule;" + SOURCE_AND_POLICY
                    + "Ljava/util/Set;Ljava/util/Map;)Ljava/util/Map; -> "
                    + CHECK + "FieldDomains#unshared",
            // What a value's rules guarantee, asked from under a reading that is under way.
            CHECK + "ValueGuarantees#seededOf(Lsouther/compiler/types/TypeSymbol$AtModule;"
                    + SOURCE_AND_POLICY + ")L" + CHECK + "InvariantChecker$Seeded; -> "
                    + CHECK + "InvariantChecker#seedFieldsUnshared",
            // What a clause of a contract may take, read in the terms of the walk it stands in.
            // Both shapes, and the store question that asks for one: what a capability is read
            // against is the reading in hand, and there is nothing there that says where another
            // declaration's reading comes from.
            CHECK + "InvariantChecker#capabilityOf(L" + CHECK
                    + "ClausesForDischarge$ClauseReading;Lsouther/compiler/types/"
                    + "TypeSymbol$AtModule;" + SOURCE_AND_POLICY + ")L" + CHECK
                    + "ClauseDischarge; -> " + NOTHING_TO_BORROW_FROM,
            CHECK + "InvariantChecker#capabilityOf(L" + CHECK + "StatedContract$Conjunct;L"
                    + CHECK + "Denotations;" + SOURCE_AND_POLICY + "Ljava/lang/String;)L"
                    + CHECK + "ClauseDischarge; -> " + NOTHING_TO_BORROW_FROM,
            "souther/compiler/query/Shapes$InvariantCapabilities#compute("
                    + "Lsouther/compiler/query/Db;)Lsouther/compiler/query/Answer; -> "
                    + CHECK + "InvariantChecker#capabilityOf",
            // The clauses a contract's rules are read from, and the overload that reaches them.
            CHECK + "ContractDischarge#of(L" + CHECK + "StatedContract;L" + CHECK
                    + "StatedContract$StatedRule;" + SOURCE_AND_POLICY + ")Ljava/util/List; -> "
                    + NOTHING_TO_BORROW_FROM,
            CHECK + "ContractDischarge#of(L" + CHECK + "StatedContract;" + SOURCE_AND_POLICY
                    + ")L" + CHECK + "ContractDischarge; -> " + CHECK + "ContractDischarge#of",
            // The engine a body is run on, and the overload that reaches it.
            CHECK + "PathReachability#of(Lsouther/compiler/core/Core;L" + CHECK + "Scope;"
                    + "Lsouther/compiler/coverage/CoverageSites$Plan;"
                    + "Lsouther/compiler/inputs/InputDomain;" + SOURCE_AND_POLICY + ")L"
                    + CHECK + "PathReachability$Answers; -> " + NOTHING_TO_BORROW_FROM,
            CHECK + "PathReachability#of(Lsouther/compiler/core/Core;L" + CHECK + "ReadingPolicy;L"
                    + CHECK + "SpecImplementation$Implemented;"
                    + "Lsouther/compiler/coverage/CoverageSites$Plan;"
                    + "Lsouther/compiler/inputs/InputDomain;L" + CHECK + "RuleReadingSource;)L"
                    + CHECK + "PathReachability$Answers; -> " + CHECK + "PathReachability#of");

    /**
     * Every pair: a static method taking the lending, and one of the same name on the same class
     * reached by leaving it out.
     *
     * <p>Both halves are found rather than listed, so an entry point written later is in the
     * population the day it is written and one whose partner is deleted leaves it the same day. A
     * list of names would be a second answer to which entry points these are, kept up by whoever
     * remembered.
     */
    private static Map<String, Set<MethodTypeDesc>> theShorterOfEachPair() {
        Map<String, Set<MethodTypeDesc>> found = new LinkedHashMap<>();
        for (ClassModel read : COMPILED.all()) {
            String owner = read.thisClass().name().stringValue();
            Map<String, List<MethodModel>> byName = new LinkedHashMap<>();
            for (MethodModel method : read.methods()) {
                if (method.flags().has(AccessFlag.STATIC)) {
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
     * Whether {@code shorter} is what a caller gets by leaving the lending out of {@code lending}.
     *
     * <p>What tells the pairs from the overloads that merely differ. A reader handed something that
     * carries a lending of its own is not reading for itself, and the two would look alike to a
     * rule that only asked whether one of the shapes names the lending: a meaning read off clauses
     * already holds where those clauses borrow from, and the shape beside it that takes a source
     * and a lending is where the clauses are made.
     *
     * <p>So the shapes have to line up: everything the longer one takes before the lending, in the
     * order it takes them. A shorter one that takes something else takes something else, whatever
     * the two are called.
     */
    private static boolean reachedByLeavingTheLendingOut(MethodModel shorter, MethodModel lending) {
        if (!lends(lending)) {
            return false;
        }
        List<ClassDesc> taken = lending.methodTypeSymbol().parameterList().stream()
                .filter(each -> !LENDING_TYPE.equals(each.descriptorString()))
                .toList();
        List<ClassDesc> without = shorter.methodTypeSymbol().parameterList();
        return without.size() <= taken.size() && without.equals(taken.subList(0, without.size()));
    }

    /** Whether {@code method} is handed somewhere to borrow a reading from. */
    private static boolean lends(MethodModel method) {
        return method.methodTypeSymbol().parameterList().stream()
                .anyMatch(each -> LENDING_TYPE.equals(each.descriptorString()));
    }

    /**
     * The pairs exist, so that a walk finding none would not read as a walk finding no edges.
     *
     * <p>Named rather than counted. What this is about is that the population is derived, and a
     * derivation that came back empty because the shapes had been renamed would let every row below
     * pass without looking at anything.
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
     * Every edge into a reading nobody else made is one that is written down.
     *
     * <p>Compared whole and in both directions. An edge nobody wrote down is a reader that lost its
     * lending with nothing to say so; a row nothing reaches any more is a licence outliving what it
     * was for, and the day somebody settles where a lending may go the rows have to go with it.
     */
    @Test
    void everyEdgeIntoAReadingOfOnesOwnIsWrittenDown() {
        Map<String, Set<MethodTypeDesc>> pairs = theShorterOfEachPair();
        Set<Named> waysIn = theWaysInThatSayTheyReadForThemselves(pairs);
        Set<String> reaching = new TreeSet<>();
        for (ClassModel read : COMPILED.all()) {
            String owner = read.thisClass().name().stringValue();
            for (MethodModel method : read.methods()) {
                String from = owner + "#" + method.methodName().stringValue()
                        + method.methodTypeSymbol().descriptorString();
                // A way in reading for itself is how one of them is written rather than a place
                // this compiler starts such a reading, and what it names inside is its own.
                if (readsForItself(pairs, owner, method)) {
                    continue;
                }
                if (waysIn.contains(new Named(owner + "#" + method.methodName().stringValue(),
                        method.methodTypeSymbol()))) {
                    reaching.add(from + " -> " + NOTHING_TO_BORROW_FROM);
                    continue;
                }
                for (Instruction instruction : instructionsOf(method)) {
                    for (Named named : whatItNames(instruction)) {
                        if (startsAReadingOfItsOwn(pairs, waysIn, named)) {
                            reaching.add(from + " -> " + named.member());
                        }
                    }
                }
            }
        }

        assertFalse(reaching.isEmpty(), "this check is reading no edges at all");
        assertEquals(EDGES_INTO_A_READING_OF_ONES_OWN, reaching,
                () -> "the edges into a reading nobody else made are not the ones written down.\n"
                        + "  found and not written down:\n    "
                        + String.join("\n    ", minus(reaching, EDGES_INTO_A_READING_OF_ONES_OWN))
                        + "\n  written down and not found:\n    "
                        + String.join("\n    ", minus(EDGES_INTO_A_READING_OF_ONES_OWN, reaching))
                        + "\nTake the entry point that is handed somewhere to borrow from, or say"
                        + " here what this reader has nowhere to borrow from.");
    }

    private static List<String> minus(Set<String> these, Set<String> those) {
        return these.stream().filter(each -> !those.contains(each)).sorted().toList();
    }

    /**
     * What an instruction names: nothing, a member it calls, or a member it hands over to be called
     * later.
     *
     * <p>The last is what a method reference comes to. A reader passing one along starts the
     * reading the same way a caller does, and it is named in the arguments a bootstrap is given
     * rather than in an instruction of its own.
     */
    private static List<Named> whatItNames(Instruction instruction) {
        return switch (instruction) {
            case InvokeInstruction call -> List.of(new Named(
                    call.owner().name().stringValue() + "#" + call.name().stringValue(),
                    call.typeSymbol()));
            case FieldInstruction field when field.opcode() == Opcode.GETSTATIC -> List.of(
                    new Named(field.owner().name().stringValue() + "#"
                            + field.name().stringValue(), null));
            case InvokeDynamicInstruction handed -> {
                List<Named> named = new ArrayList<>();
                for (LoadableConstantEntry each
                        : handed.invokedynamic().bootstrap().arguments()) {
                    if (each instanceof MethodHandleEntry handle) {
                        MemberRefEntry member = handle.reference();
                        // A handle onto a field names a field, whose descriptor is not a method's.
                        // What it reaches is the member, and a member with nothing to take is not
                        // one of the entry points this is about.
                        String type = member.type().stringValue();
                        named.add(new Named(member.owner().name().stringValue() + "#"
                                + member.name().stringValue(),
                                type.startsWith("(") ? MethodTypeDesc.ofDescriptor(type) : null));
                    }
                }
                yield named;
            }
            default -> List.of();
        };
    }

    /** A member an instruction names: which one, and what it takes where that is a method. */
    private record Named(String member, MethodTypeDesc taking) {}

    /**
     * Whether reaching {@code named} is starting a reading nobody else made.
     *
     * <p>Which method, and not which name. The pairs differ by one argument, so a name says
     * nothing on its own: the entry point that takes the lending and the one beside it that does
     * not are the same name, and a rule reading the name alone would report every caller of the
     * first as a caller of the second.
     */
    private static boolean startsAReadingOfItsOwn(Map<String, Set<MethodTypeDesc>> pairs,
                                                  Set<Named> waysIn, Named named) {
        if (named.member().equals(NOTHING_TO_BORROW_FROM) || waysIn.contains(named)) {
            return true;
        }
        Set<MethodTypeDesc> shorter = pairs.get(named.member());
        return shorter != null && shorter.contains(named.taking());
    }

    /**
     * The ways in that say outright that they read for themselves.
     *
     * <p>Derived and not listed: a method that takes nothing to borrow from is one that names the
     * lending there is nothing to borrow from, and is not one of the pair of entry points reached
     * by leaving an argument out — those are what a caller with no store reaches, and are found
     * already. So a way in written later is one the day it is written, and one whose reason is
     * settled leaves the population when the naming goes.
     */
    private static Set<Named> theWaysInThatSayTheyReadForThemselves(
            Map<String, Set<MethodTypeDesc>> pairs) {
        Set<Named> found = new LinkedHashSet<>();
        for (ClassModel read : COMPILED.all()) {
            String owner = read.thisClass().name().stringValue();
            for (MethodModel method : read.methods()) {
                if (readsForItself(pairs, owner, method)) {
                    continue;
                }
                for (Instruction instruction : instructionsOf(method)) {
                    for (Named named : whatItNames(instruction)) {
                        if (named.member().equals(NOTHING_TO_BORROW_FROM)) {
                            found.add(new Named(owner + "#" + method.methodName().stringValue(),
                                    method.methodTypeSymbol()));
                        }
                    }
                }
            }
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
    private static boolean readsForItself(Map<String, Set<MethodTypeDesc>> pairs, String owner,
                                          MethodModel method) {
        Set<MethodTypeDesc> shorter = pairs.get(owner + "#" + method.methodName().stringValue());
        return shorter != null && shorter.contains(method.methodTypeSymbol());
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
