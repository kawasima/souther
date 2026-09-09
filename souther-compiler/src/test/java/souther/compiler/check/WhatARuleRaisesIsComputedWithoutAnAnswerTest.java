package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.test.WhatAModuleDeclares;

import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which coverage questions a rule raises is computed without asking anything that answers one.
 *
 * <p>For a fixed model and a fixed reading of what that model means, what a rule raises does not
 * depend on the readers that answer: not on what they can do, not on whether they succeeded, and
 * not on any value they produced. A reading of the model growing able to understand more may change
 * the classification — proving that a clause restricts no value is understanding the clause better,
 * and a question that goes away because the model turned out to say nothing is a question nobody
 * was owed. What may never change it is a reader having failed.
 *
 * <p><b>Why it is the computation and not the value.</b> A question carries nothing a reading
 * produced, and that is said elsewhere over the types those values are made of
 * ({@link ARaisedQuestionCarriesNoReaderProducedValueTest}). It is not enough here: a
 * classification could hold nothing but an arm and still have chosen that arm by asking whether a
 * bound could be read. The value would be clean and the answer would have decided the question. So
 * what is walked is what the classification computes with — every method reachable from the ones
 * that produce it, through the calls a class file records.
 *
 * <p><b>And the roots are the producers, not the entry.</b> What a rule raises is
 * {@link Required#ofInvariant}'s of what a clause was found to state, and that finding is handed in
 * rather than made there. Rooted at the entry alone, everything that made its arguments would be
 * outside the walk — which is where an answer would get in, and the check would pass by looking at
 * the wrong half.
 *
 * <p><b>What is forbidden is what answering produces.</b> Not every type an answer also uses: a
 * carrier is the order a declared type's values live on, and the readings of ends use it exactly as
 * the reading of what a clause states does — refusing it would be refusing a shared vocabulary
 * rather than a dependency. The list below is values that exist because something answered a
 * question or read the evidence for one.
 */
class WhatARuleRaisesIsComputedWithoutAnAnswerTest {

    /**
     * What computes the classification a rule's questions are read off.
     *
     * <p>The classification itself, the arithmetic whose answer it is handed, and the names it is
     * given — each of them makes part of what {@link Required#ofInvariant} is called with.
     */
    private static final List<String> ROOTS = List.of(
            "souther/compiler/check/InvariantChecker#states",
            "souther/compiler/check/InvariantChecker#canonicalFormOf",
            "souther/compiler/check/InvariantChecker#namedIn",
            "souther/compiler/check/Required#ofInvariant");

    /**
     * The values that exist because something answered a coverage question, or read what would.
     *
     * <p>Each of them was reachable from this computation before, or is what a reader would reach
     * for next: where a rule placed an end, what a quantity a reading found cuts, what the rules
     * leave a number, the parts a reading took in, and the terms and states built to answer with.
     */
    private static final List<String> WHAT_ANSWERING_PRODUCES = List.of(
            "souther/compiler/check/InvariantBound",
            "souther/compiler/check/UnreadComparison$Quantity",
            "souther/compiler/check/InvariantChecker$RulesRead",
            "souther/compiler/check/InvariantChecker$PartRead",
            "souther/compiler/check/ConstraintState",
            "souther/compiler/check/ReadingEvidence",
            "souther/compiler/check/FieldDomains",
            "souther/compiler/inputs/NumericTerm",
            "souther/compiler/numeric/NumericDomain");

    @Test
    void nothingAnAnswerProducedIsReachedByTheComputation() {
        Reached reached = walk();
        List<String> found = new ArrayList<>();
        for (String each : WHAT_ANSWERING_PRODUCES) {
            for (String type : reached.types()) {
                if (type.equals(each) || type.startsWith(each + "$")) {
                    found.add(type + " first reached from " + reached.cameFrom().get(type));
                }
            }
        }
        assertEquals(List.of(), found,
                "what a rule raises is computed without asking anything that answers one");
    }

    /**
     * Every name this check is configured with names something.
     *
     * <p>The two lists above are strings, and a string that matches nothing makes this check pass
     * about a type nobody forbade and a root nobody walked. Both were true of it at once: a
     * forbidden entry named a top-level type where the type is a record inside a class, so the
     * prefix could never match and the answer it names could have been read all along.
     *
     * <p>Resolved against the classes the module actually declares, so a name that has moved is a
     * failure here rather than a silence everywhere else.
     */
    @Test
    void everyNameThisIsConfiguredWithNamesSomething() {
        Map<String, ClassModel> byName = compilerClasses();
        for (String each : WHAT_ANSWERING_PRODUCES) {
            assertTrue(byName.keySet().stream()
                            .anyMatch(one -> one.equals(each) || one.startsWith(each + "$")),
                    each + " is not a class this module declares, so forbidding it forbids nothing");
        }
        for (String each : ROOTS) {
            String owner = each.substring(0, each.indexOf('#'));
            String name = each.substring(each.indexOf('#') + 1);
            ClassModel of = byName.get(owner);
            assertTrue(of != null && of.methods().stream()
                            .anyMatch(one -> one.methodName().stringValue().equals(name)
                                    && one.code().isPresent()),
                    each + " names no method with a body, so the walk starts nowhere");
        }
    }

    /**
     * And the walk enters every root and gets past them.
     *
     * <p>A closure is a set of names, and the walk marks one seen whether or not it found anything
     * to enter. So the roots are asserted one at a time and against what was entered rather than
     * against what was queued: a count over the whole walk is satisfied by the roots that still
     * resolve, and says nothing about the one that stopped resolving.
     */
    @Test
    void theWalkReachesWhatItIsWalking() {
        Reached reached = walk();
        for (String each : ROOTS) {
            assertTrue(reached.entered().contains(each), each + " was not entered by the walk");
        }
        assertTrue(reached.methods().size() > ROOTS.size(),
                "the roots call something: " + reached.methods().size() + " methods");
        for (String each : List.of(
                // The classification itself, and the two answers it is read from.
                "souther/compiler/check/ClauseStates",
                "souther/compiler/check/Required",
                // The arithmetic the canonical form comes out of, which is where an answer would
                // get in if one did.
                "souther/compiler/check/AffineForms",
                "souther/compiler/check/Terms",
                // And the vocabulary a model is read in, which is what is left when the answers go.
                "souther/compiler/check/Carrier",
                "souther/compiler/numeric/LinearForm",
                // The walk over a clause, which is what says which places it names. Named here
                // because it is reached through a reading built where it is used, and a check that
                // stops at whatever declares an interface says nothing about the class answering
                // for it.
                "souther/compiler/check/ValueOrigin")) {
            assertTrue(reaches(reached, each), each + " is not in the walk");
        }
    }

    /** Whether the walk got to {@code named} or to one of the arms under it: a family is reached
     *  wherever one of its cases is, and which case a walk meets is not what is being asked. */
    private static boolean reaches(Reached reached, String named) {
        return reached.types().stream()
                .anyMatch(each -> each.equals(named) || each.startsWith(named + "$"));
    }

    /** How a class constructed inside the walk is named, meaning every method it declares. */
    private static final String EVERYTHING_IT_DECLARES = "*";

    /**
     * What the walk got to.
     *
     * @param methods  every name it took off the queue, whether or not it found code under it
     * @param entered  every name it found a body for and read, which is what it actually walked
     * @param types    every type it saw
     * @param cameFrom where each was first reached from, so a failure names a path and not a set
     */
    private record Reached(Set<String> methods, Set<String> entered, Set<String> types,
                           Map<String, String> cameFrom) {}

    private static Reached walk() {
        Map<String, ClassModel> byName = compilerClasses();
        Map<String, String> cameFrom = new LinkedHashMap<>();
        Set<String> methods = new LinkedHashSet<>();
        Set<String> entered = new LinkedHashSet<>();
        Set<String> types = new LinkedHashSet<>();
        Deque<String> todo = new ArrayDeque<>(ROOTS);
        while (!todo.isEmpty()) {
            String at = todo.removeFirst();
            if (!methods.add(at)) {
                continue;
            }
            ClassModel of = byName.get(at.substring(0, at.indexOf('#')));
            if (of == null) {
                // A class of another module, or of the language itself. What it computes with is
                // not this module's to answer for, and the types it names arrive here anyway
                // through the call that reached it.
                continue;
            }
            String name = at.substring(at.indexOf('#') + 1);
            for (MethodModel method : of.methods()) {
                if (!name.equals(EVERYTHING_IT_DECLARES)
                        && !method.methodName().stringValue().equals(name)) {
                    continue;
                }
                CodeModel code = method.code().orElse(null);
                if (code == null) {
                    continue;
                }
                entered.add(at);
                for (CodeElement element : code) {
                    for (String each : referenced(element)) {
                        if (!each.startsWith("souther/")) {
                            continue;
                        }
                        String type = each.contains("#")
                                ? each.substring(0, each.indexOf('#')) : each;
                        types.add(type);
                        cameFrom.putIfAbsent(type, at);
                        if (each.contains("#")) {
                            cameFrom.putIfAbsent(each, at);
                            todo.add(each);
                        }
                    }
                }
            }
        }
        return new Reached(methods, entered, types, cameFrom);
    }

    /**
     * This compiler's own classes, by the name a class file spells.
     */
    private static Map<String, ClassModel> compilerClasses() {
        Map<String, ClassModel> byName = new LinkedHashMap<>();
        for (ClassModel each : WhatAModuleDeclares.of(Required.class).classes()) {
            byName.put(each.thisClass().asInternalName(), each);
        }
        return byName;
    }

    /**
     * Every class and method one instruction names.
     *
     * <p>Read off the instructions rather than the constant pool, so that what is followed is what
     * the method does and not what its file happens to mention: a pool holds the names of every
     * signature in the class, and walking those would call a type reached wherever it is written
     * down.
     */
    private static List<String> referenced(CodeElement element) {
        List<String> out = new ArrayList<>();
        switch (element) {
            case InvokeInstruction it -> {
                out.add(internal(it.owner().asSymbol()) + "#" + it.name().stringValue());
                out.add(internal(it.owner().asSymbol()));
            }
            case FieldInstruction it -> {
                out.add(internal(it.owner().asSymbol()));
                out.add(internal(it.typeSymbol()));
            }
            // Everything the constructed class declares, and not the constructor alone. A value
            // made here is handed on, and what runs of it is whichever method a caller reaches
            // through whatever it implements — an interface method has no code for a walk to
            // follow, so a walk that stopped at the constructor would call a classification clean
            // while the class answering for it read whatever it liked.
            case NewObjectInstruction it -> {
                out.add(internal(it.className().asSymbol()));
                out.add(internal(it.className().asSymbol()) + "#" + EVERYTHING_IT_DECLARES);
            }
            case TypeCheckInstruction it -> out.add(internal(it.type().asSymbol()));
            // A lambda's body is a method of the class that wrote it, named by the handle the call
            // site is given. Left out, everything a classification does inside one would be
            // outside this walk, which is most of what it does.
            case InvokeDynamicInstruction it -> {
                for (java.lang.classfile.constantpool.LoadableConstantEntry arg
                        : it.invokedynamic().bootstrap().arguments()) {
                    if (arg.constantValue() instanceof DirectMethodHandleDesc direct) {
                        out.add(internal(direct.owner()) + "#" + direct.methodName());
                        out.add(internal(direct.owner()));
                    }
                }
            }
            default -> { }
        }
        return out;
    }

    private static String internal(ClassDesc of) {
        String descriptor = of.descriptorString();
        return descriptor.startsWith("L")
                ? descriptor.substring(1, descriptor.length() - 1) : descriptor;
    }
}
