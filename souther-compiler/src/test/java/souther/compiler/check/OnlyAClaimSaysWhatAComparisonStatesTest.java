package souther.compiler.check;

import org.junit.jupiter.api.Test;
import souther.compiler.WhatWasCompiled;

import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Who puts a canonical comparison together, and who asks for one.
 *
 * <p>Two questions, and they break differently. What a comparison of two values states is derived
 * from the two facts a claim holds, and that derivation is the claim's: a second one is a table of
 * six that agrees with this one only for as long as somebody keeps it so. Who may ask a claim for
 * the answer is a different matter — a reader that has to write a comparison down asks, and one
 * more of them is one more consumer and not one more derivation.
 *
 * <p><b>What is watched is not written here.</b> Everything that hands back a
 * {@link CanonicalComparison} is read off the compiled classes, and the two questions are the two
 * halves of that: what is declared on the value is how one is assembled, and what is declared
 * anywhere else is how one is asked for. A list of methods written out instead would have a
 * complement — an operation added to the value, an override reached through the arm that declares
 * it rather than through the interface — and what falls in the complement is answered by silence.
 * So the way in is enumerated and then declared, and a way in that nobody has written down is
 * itself the finding.
 *
 * <p><b>What the visibility does not do.</b> {@link CanonicalComparison} is this package's, which
 * keeps it out of the vocabulary anything outside reads. It does not decide who calls what: a
 * method whose answer nothing outside can name is still a method anything can call, so both
 * questions are answered here rather than by the language.
 *
 * <p>Read off the compiled classes, because what a method calls is what the class file says. A
 * reading of the sources would answer the same question a second way, would not see a call written
 * inside a lambda as the method that holds it, and would not see which of an override and the
 * method it overrides a call names.
 */
class OnlyAClaimSaysWhatAComparisonStatesTest {

    private static final String CANONICAL = "souther/compiler/check/CanonicalComparison";

    /** What hands back a canonical comparison: the class it is declared on, and its name. */
    private record Producer(String owner, String name) implements Comparable<Producer> {

        /** As a reader of a failure reads it, and as a licence below names it. */
        String shown() {
            return owner.replace('/', '.').replace('$', '.') + "." + name;
        }

        /** Assembled where it is declared on the value itself, and asked for anywhere else. */
        boolean assembles() {
            return owner.equals(CANONICAL);
        }

        @Override
        public int compareTo(Producer other) {
            return shown().compareTo(other.shown());
        }
    }

    /** A method that may call one, and why it is one that may. */
    private record Licence(String who, String why) { }

    /**
     * Every way to come by a canonical comparison, and what each is for.
     *
     * <p>Declared because the two tests below are only as wide as this is. An operation added to
     * the value, or a second method on a claim answering the same question, lands here first and is
     * said to be one of the two things before anything asks who may call it.
     */
    private static final List<Licence> WAYS_IN = List.of(
            new Licence("souther.compiler.check.CanonicalComparison.theSameValue",
                    "assembles the statement that the two sides are the same value"),
            new Licence("souther.compiler.check.CanonicalComparison.below",
                    "assembles the statement that the left side stands below the right"),
            new Licence("souther.compiler.check.CanonicalComparison.denied",
                    "assembles what holds exactly where a statement does not"),
            new Licence("souther.compiler.check.ComparisonClaim.canonical",
                    "what a claim states, asked of a claim whichever of the two it is"),
            new Licence("souther.compiler.check.ComparisonClaim.Cut.canonical",
                    "the same, reached through the arm an order is"),
            new Licence("souther.compiler.check.ComparisonClaim.Singled.canonical",
                    "the same, reached through the arm a value singled out is"));

    private static final List<Licence> MAY_ASSEMBLE = List.of(
            new Licence("souther.compiler.check.ComparisonClaim.Cut.canonical",
                    "an order, with the sides the way the canonical form wants them and denied"
                            + " where the comparison holds at the value it names"),
            new Licence("souther.compiler.check.ComparisonClaim.Singled.canonical",
                    "an equality, denied where the comparison does not hold at the value it"
                            + " names — which is the same fact read the other way round"));

    private static final List<Licence> MAY_ASK = List.of(
            new Licence("souther.compiler.check.Conditions.polar",
                    "keys a fact by the comparison a condition states, which is written as a node"
                            + " with what is asserted of it carried beside"),
            new Licence("souther.compiler.check.Terms.comparisonTerm",
                    "names the value a comparison in a body is, which is written as a term"));

    /**
     * Every way to come by one, which is what the two tests below are asked about.
     *
     * <p>A way in that nobody wrote down is not covered by either of them, and a check whose reach
     * is a list somebody keeps in step is a check that goes quiet exactly when the thing it guards
     * grows.
     */
    @Test
    void everyWayToComeByACanonicalComparisonIsWrittenDown() {
        assertEquals(declared(WAYS_IN), shown(producers()),
                "a method handing back a canonical comparison is a way to come by one, and what"
                        + " may call it is decided below. What each of these is for: "
                        + why(WAYS_IN));
    }

    /**
     * And that the ways in are all of them, which is what makes the two below say what they claim.
     *
     * <p>Every one of these values comes from the one constructor, so a method that makes one and
     * hands it back as something else — inside an optional, inside a list — would be a way in that
     * the enumeration above cannot see, its own type having been erased from what it returns. It
     * would be seen here, as a maker that is not one of the ways in.
     *
     * <p>Both sides are read off the classes, so this says the two agree and not what either is.
     * What they are is said above.
     */
    @Test
    void nothingMakesOneExceptTheWaysInToMakingOne() {
        assertEquals(shown(assembling()),
                callers(Set.of(new Producer(CANONICAL, "<init>"))),
                "a canonical comparison made anywhere else is one whose making nothing decided,"
                        + " and it is made where the derivation is not");
    }

    /**
     * The derivation from what a comparison placed to what it states, which is the claim's own.
     *
     * <p>Two facts decide it and neither decides the other, so a reader putting them together is a
     * reader that remembers the pairing — and one that pairs them the other way round answers every
     * one of its own questions consistently about the comparison that holds where this one does
     * not.
     */
    @Test
    void onlyTheTwoClaimsPutACanonicalComparisonTogether() {
        assertEquals(declared(MAY_ASSEMBLE), callersOf(Producer::assembles),
                "what a comparison states is derived where what it placed is held, and nowhere"
                        + " else. What each of these assembles: " + why(MAY_ASSEMBLE));
    }

    /**
     * And who asks for one, which is a consumer boundary and not the derivation.
     *
     * <p>Written down for the same reason the readers of an operator are: a reader appearing here
     * is one more place a comparison has to be written down in some representation, and what it
     * writes has to be a comparison of this compiler's own rather than one the source never wrote.
     */
    @Test
    void onlyAReaderWritingAComparisonDownAsksForOne() {
        assertEquals(declared(MAY_ASK), callersOf(producer -> !producer.assembles()),
                "asking a claim what it states is asking to write the comparison down somewhere,"
                        + " through the interface or through the arm that declares the answer."
                        + " What each of these writes it into: " + why(MAY_ASK));
    }

    private static Map<String, String> declared(List<Licence> licences) {
        Map<String, String> out = new TreeMap<>();
        licences.forEach(each -> out.put(each.who(), ""));
        return out;
    }

    private static Map<String, String> shown(Set<Producer> producers) {
        Map<String, String> out = new TreeMap<>();
        producers.forEach(each -> out.put(each.shown(), ""));
        return out;
    }

    private static Map<String, String> why(List<Licence> licences) {
        Map<String, String> out = new LinkedHashMap<>();
        licences.forEach(each -> out.put(each.who(), each.why()));
        return out;
    }

    /** Everything the compiler declares that hands one back. */
    private static Set<Producer> producers() {
        Set<Producer> out = new TreeSet<>();
        for (ClassModel model : WhatWasCompiled.compiled().all()) {
            String owner = model.thisClass().asInternalName();
            for (MethodModel method : model.methods()) {
                if (method.methodTypeSymbol().returnType().descriptorString()
                        .equals("L" + CANONICAL + ";")) {
                    out.add(new Producer(owner, method.methodName().stringValue()));
                }
            }
        }
        assertFalse(out.isEmpty(), "nothing hands one back at all, so this says nothing");
        return out;
    }

    /** The ways in that are declared on the value itself, which is how one is made. */
    private static Set<Producer> assembling() {
        return new TreeSet<>(producers().stream().filter(Producer::assembles).toList());
    }

    /** Which methods call any producer {@code wanted} admits, whichever way its receiver is
     *  typed. */
    private static Map<String, String> callersOf(java.util.function.Predicate<Producer> wanted)
            {
        return callers(new TreeSet<>(producers().stream().filter(wanted).toList()));
    }

    /** Which methods call any of {@code watched}. */
    private static Map<String, String> callers(Set<Producer> watched) {
        assertFalse(watched.isEmpty(), "no producer was watched, so this says nothing");
        Map<String, String> calls = new TreeMap<>();
        for (ClassModel model : WhatWasCompiled.compiled().all()) {
            String from = model.thisClass().asInternalName().replace('/', '.').replace('$', '.');
            for (MethodModel method : model.methods()) {
                method.code().ifPresent(code -> code.forEach(element -> {
                    if (element instanceof InvokeInstruction call
                            && watched.contains(new Producer(call.owner().asInternalName(),
                                    call.name().stringValue()))) {
                        calls.put(from + "." + method.methodName().stringValue(), "");
                    }
                }));
            }
        }
        return calls;
    }

}
