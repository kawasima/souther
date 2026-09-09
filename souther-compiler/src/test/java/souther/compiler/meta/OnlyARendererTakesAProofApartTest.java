package souther.compiler.meta;

import org.junit.jupiter.api.Test;
import souther.compiler.WhatWasCompiled;

import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeModel;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.constant.DirectMethodHandleDesc;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * What decides a policy reads the answer; what writes a sentence writes one payload's words; and
 * what makes an answer is the reading that made it.
 *
 * <p>{@code Reachability} has three arms and each carries something: why nothing arrives, what says
 * something does, why nothing settled it. A reader that took one of those apart would be deciding
 * an obligation, a diagnostic or a claim on a distinction inside the payload — and a distinction
 * added there later would silently become a change to that decision.
 *
 * <p><b>Read off the compiled classes.</b> The arms being package-private stops the
 * {@code instanceof} and nothing else: {@code Words} and the factories are public, so a static
 * import or a qualified call reaches them, and a scan over source text answers about spellings
 * rather than about what a class does. What is checked here is what the bytecode says — who calls
 * {@code said}, who implements a {@code Words}, who calls a factory, and whose code runs an
 * answer's constructor — which no way of writing the call can dress up as something else.
 *
 * <p>Both directions are asserted. A check that only counts violations passes when it reads nothing
 * at all, so every rule below also names what it expects to find and fails where that is missing.
 *
 * <p><b>This module's classes only, and the repository's are held elsewhere.</b> At the time these
 * tests run nothing downstream has been compiled, so a walk from here cannot cover a module that
 * depends on this one. What it is, is the fast local answer — a violation written in the compiler
 * fails here in seconds. The rule about the repository is the same one, owned in the module the
 * reactor finishes with, where every module's classes exist to be read.
 */
class OnlyARendererTakesAProofApartTest {

    private static final String REACH = "souther.compiler.reach.";

    /**
     * Who may ask each payload what it says, and write the words it says it in.
     *
     * <p>Every payload is named, including the one nothing reads: a payload left out of this map
     * would be one the rules below say nothing about, and adding words to it later would open a
     * reader that nothing here would notice. {@code Witness} has no words today and the expected
     * set for it is empty, which is a claim this check makes rather than a case it skips.
     */
    private static final Map<String, List<String>> WRITES_THE_WORDS_OF = Map.of(
            REACH + "Proof",
            List.of("souther.compiler.query.Adequacy$DeadBranches$DeadBranchProofWords"),
            REACH + "WhyUnsettled",
            List.of("souther.compiler.query.ClaimAnnotations$UnsettledWords"),
            REACH + "Witness", List.of());

    /**
     * Who may make an answer.
     *
     * <p>By nest rather than by class: the reading is written as a class with helpers inside it,
     * and which of them holds a given construction is its own business. What matters is that
     * nothing outside the reading makes one.
     */
    private static final String THE_READING = "souther.compiler.check.PathReachability";

    /** The answers themselves, whose constructors are as good as a factory to a consumer. */
    private static final List<String> ANSWERS = List.of(
            REACH + "Reachability$Unreachable",
            REACH + "Reachability$Reachable",
            REACH + "Reachability$Unsettled");

    /** One compiled call or construction, and the class whose code holds it. */
    private record Use(String from, String owner, String member, boolean isStatic) {}

    @Test
    void onlyItsOwnWordsAskAPayloadWhatItSays() {
        Map<String, List<String>> asked = new LinkedHashMap<>();
        for (Use use : uses()) {
            if (WRITES_THE_WORDS_OF.containsKey(use.owner()) && use.member().equals("said")) {
                asked.computeIfAbsent(use.owner(), _ -> new ArrayList<>()).add(use.from());
            }
        }
        WRITES_THE_WORDS_OF.forEach((payload, words) -> assertEquals(words,
                asked.getOrDefault(payload, List.of()).stream().distinct().toList(),
                payload + " is asked what it says by something that is not its words"));
        assertFalse(asked.isEmpty(),
                "no payload is asked what it says at all; this check is reading no calls");
    }

    @Test
    void andOnlyThoseWordsAreWritten() {
        Map<String, List<String>> implementors = new LinkedHashMap<>();
        for (ClassModel model : WhatWasCompiled.compiled().all()) {
            for (var face : model.interfaces()) {
                String name = face.asInternalName().replace('/', '.');
                WRITES_THE_WORDS_OF.forEach((payload, words) -> {
                    if (name.equals(payload + "$Words")) {
                        implementors.computeIfAbsent(payload, _ -> new ArrayList<>())
                                .add(model.thisClass().asInternalName().replace('/', '.'));
                    }
                });
            }
        }
        WRITES_THE_WORDS_OF.forEach((payload, words) -> assertEquals(words,
                implementors.getOrDefault(payload, List.of()),
                payload + "'s words are written somewhere other than by its own renderer"));
        assertFalse(implementors.isEmpty(),
                "no payload's words are written at all; this check is reading no classes");
    }

    @Test
    void andNothingButTheReadingMakesAnAnswer() {
        List<String> outside = new ArrayList<>();
        List<Use> made = new ArrayList<>();
        boolean sawAConstructor = false;
        for (Use use : uses()) {
            // A factory is static; reading a component off an answer already in hand is not one,
            // and is what a policy consumer does when it passes a reason along.
            boolean isFactory = use.owner().startsWith(REACH) && use.isStatic();
            boolean isAnswer = ANSWERS.contains(use.owner()) && use.member().equals("<init>");
            if (!isFactory && !isAnswer) {
                continue;
            }
            sawAConstructor |= isAnswer;
            made.add(use);
            if (!nestOf(use.from()).equals(THE_READING)) {
                outside.add(use.from() + " -> " + use.owner() + "." + use.member());
            }
        }
        assertFalse(made.isEmpty(), "nothing makes an answer at all; this check is reading nothing");
        assertFalse(!sawAConstructor,
                "no answer's constructor was seen; the check would miss one built directly");
        assertEquals(List.of(), outside, "these make an answer the reading did not make");
    }

    /**
     * What a method handle in a bootstrap argument comes to, said the way a call is.
     *
     * <p>A reference is the call it stands for. Which kind of call it is decides the two things the
     * rules ask: a static handle is a factory, a constructor handle makes the value, and a virtual
     * one on a payload is asking it what it says.
     */
    private static Use referenced(String from, DirectMethodHandleDesc handle) {
        String owner = handle.owner().descriptorString();
        owner = owner.startsWith("L") && owner.endsWith(";")
                ? owner.substring(1, owner.length() - 1).replace('/', '.') : owner;
        return switch (handle.kind()) {
            case STATIC, INTERFACE_STATIC -> new Use(from, owner, handle.methodName(), true);
            case CONSTRUCTOR -> new Use(from, owner, "<init>", false);
            default -> new Use(from, owner, handle.methodName(), false);
        };
    }

    /** The nest a class belongs to: what is written inside the reading is the reading. */
    private static String nestOf(String binaryName) {
        int nested = binaryName.indexOf('$');
        return nested < 0 ? binaryName : binaryName.substring(0, nested);
    }

    /** Every call and construction the compiled classes hold, the answers' own aside. */
    private static List<Use> uses() {
        List<Use> found = new ArrayList<>();
        for (ClassModel model : WhatWasCompiled.compiled().all()) {
            String from = model.thisClass().asInternalName().replace('/', '.');
            if (from.startsWith(REACH)) {
                continue;   // what the answers do among themselves is their own business
            }
            for (var method : model.methods()) {
                CodeModel code = method.code().orElse(null);
                if (code == null) {
                    continue;
                }
                for (var element : code) {
                    if (element instanceof InvokeInstruction call) {
                        found.add(new Use(from, call.owner().asInternalName().replace('/', '.'),
                                call.name().stringValue(),
                                call.opcode() == java.lang.classfile.Opcode.INVOKESTATIC));
                    } else if (element instanceof NewObjectInstruction made) {
                        found.add(new Use(from,
                                made.className().asInternalName().replace('/', '.'), "<init>",
                                false));
                    } else if (element instanceof InvokeDynamicInstruction lambda) {
                        // A method or constructor reference is neither of the above. What it names
                        // is a handle in the bootstrap arguments, and a factory reached that way is
                        // a factory called — `Proof::conditionsThatCannotAllHold` and
                        // `Reachability.Unreachable::new` put nothing else in the caller's code.
                        for (var argument : lambda.bootstrapArgs()) {
                            if (argument instanceof DirectMethodHandleDesc handle) {
                                found.add(referenced(from, handle));
                            }
                        }
                    }
                }
            }
        }
        assertFalse(found.isEmpty(), "no compiled call was read at all");
        return found;
    }

}
