package souther.compiler.inputs;

import org.junit.jupiter.api.Test;
import souther.compiler.WhatWasCompiled;

import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A number and a pair of orders do not travel as two things anybody can choose.
 *
 * <p>{@link TermOrders} says which number it is of, so a reader that has one needs nothing else to
 * know what it is an answer about. What it does not stop by itself is a caller writing the number
 * down beside it: two arguments are two things to get right, and {@code f(termA, ordersOfB)}
 * compiles wherever the two are taken apart — which is the defect this reading exists to stop, said
 * one call along.
 *
 * <p>So the rule is about every place that takes both, whether it is a constructor, a factory or a
 * reader: either it does not take both, or it proves they agree. Proving is
 * {@link TermOrders#areOf}, and a method that takes both without calling it is what this reports.
 *
 * <p><b>Enumerated by the machine and not by a reader.</b> The three rounds of review this rule went
 * through each found another spelling of it — a record here, a signature there, a package-private
 * entry that came in with a refactor — because the population was read off the sources by whoever
 * was looking. What a class file carries is every method that takes the two, whatever it is called
 * and wherever it was written.
 */
class NothingTakesANumberAndAnotherNumbersOrdersTest {

    private static final String ORDERS = "souther.compiler.inputs.TermOrders";

    /**
     * What names a number: the term itself, its cases, and the values built around one.
     *
     * <p>A wrapper counts. What makes two arguments a pairing is that each says which number it is
     * about, and {@code RealizationTarget} says it as plainly as a term does — so a reader handed
     * one of those and a pair of orders has the same two things to get right.
     */
    private static final List<String> TERM = List.of(
            "souther.compiler.inputs.NumericTerm",
            "souther.compiler.partition.RealizationTarget");

    /**
     * The one place the two are meant to be about two numbers, and it says so by its shape.
     *
     * <p>Moving a quantity is asked which number it is over and handed the answer for the one it
     * lands on, and those are two numbers on purpose. Everything else that takes both is a pairing
     * a caller chose. Named by what it is rather than by which classes do it, so an implementation
     * added is covered and a second reader of two numbers is not.
     */
    private static final String MOVES = "movedTo";

    @Test
    void nothingTakesBothWithoutProvingTheyAgree() {
        Set<String> takesBoth = new TreeSet<>();
        for (ClassModel model : WhatWasCompiled.compiled().all()) {
            String from = model.thisClass().asInternalName().replace('/', '.');
            if (from.equals(ORDERS)) {
                continue;   // what the pair says about itself is its own business
            }
            for (MethodModel method : model.methods()) {
                String named = method.methodName().stringValue();
                if (!takesBoth(method) || proves(method) || moves(named)) {
                    continue;
                }
                takesBoth.add(from + "#" + named);
            }
        }

        assertEquals(Set.of(), takesBoth,
                "a number and a pair of orders taken as two arguments are two arguments that can"
                        + " be about two numbers; take the pair, which names its number, or hold"
                        + " the two to each other");
    }

    /**
     * And the exception keeps the shape it was allowed for.
     *
     * <p>One number and one answer. A second number beside them would be the pairing again, under
     * the one name this does not report.
     */
    @Test
    void movingAQuantityIsAskedOneNumberAndHandedOneAnswer() {
        Set<String> shapes = new TreeSet<>();
        for (ClassModel model : WhatWasCompiled.compiled().all()) {
            for (MethodModel method : model.methods()) {
                // The declared move, not the lambdas inside one: a lambda's parameters are what it
                // captured, which is not a shape anybody wrote.
                if (method.methodName().stringValue().equals(MOVES) && takesBoth(method)) {
                    shapes.add(method.methodTypeSymbol().parameterList().stream()
                            .map(ClassDesc::displayName).toList().toString());
                }
            }
        }

        assertEquals(Set.of("[NumericTerm, TermOrders]"), shapes,
                "moving is asked the number it is over and handed the answer for the one it lands"
                        + " on, and nothing else");
    }

    /** Whether this is the move, including the lambdas written inside one. */
    private static boolean moves(String method) {
        return method.equals(MOVES) || method.startsWith("lambda$" + MOVES + "$");
    }

    /** That the scan is reading methods at all, so an empty answer means what it says. */
    @Test
    void theScanReadsTheMethodsItIsAbout() {
        int found = 0;
        for (ClassModel model : WhatWasCompiled.compiled().all()) {
            for (MethodModel method : model.methods()) {
                if (mentions(method, ORDERS)) {
                    found++;
                }
            }
        }

        int carrying = found;
        assertTrue(carrying > 20,
                () -> "the scan found only " + carrying + " methods taking a pair of orders,"
                        + " which is not this compiler");
    }

    /** Whether {@code method} is handed a number and a pair of orders as separate arguments. */
    private static boolean takesBoth(MethodModel method) {
        return mentions(method, ORDERS) && TERM.stream().anyMatch(each -> mentions(method, each));
    }

    /** Whether it holds the two to each other, which is what {@link TermOrders#areOf} is. */
    private static boolean proves(MethodModel method) {
        CodeModel code = method.code().orElse(null);
        if (code == null) {
            return false;
        }
        for (var element : code) {
            if (element instanceof InvokeInstruction call
                    && call.owner().asInternalName().replace('/', '.').equals(ORDERS)
                    && call.name().stringValue().equals("areOf")) {
                return true;
            }
        }
        return false;
    }

    /** A parameter of that type, or of one of its cases: a term arrives under several names. */
    private static boolean mentions(MethodModel method, String type) {
        for (ClassDesc each : method.methodTypeSymbol().parameterList()) {
            String named = each.isClassOrInterface()
                    ? each.packageName() + "." + each.displayName() : each.displayName();
            // A case of a term is written with a dollar in a class file, which is the spelling that
            // let this scan report nothing about the records holding one.
            if (named.equals(type) || named.startsWith(type + "$")) {
                return true;
            }
        }
        return false;
    }

}
