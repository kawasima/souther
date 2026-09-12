package souther.compiler.check;

import org.junit.jupiter.api.Test;
import souther.compiler.WhatWasCompiled;

import souther.compiler.core.CompleteSignature;
import souther.compiler.core.DeclaredOperation;

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
 * Who reads a declaration into a signature, and who hands one on.
 *
 * <p>A {@link DeclaredOperation} is a name that has been read against the declaration it reaches,
 * and everything downstream of it stands on that having happened: a call keeps the arguments its
 * declaration takes, and a reader finding one of them by a position some rule names is finding a
 * position that declaration has. So what may say a declaration was read is the question, and it is
 * asked here rather than answered by an access modifier — a constructor a package can reach is one
 * anything in that package can call.
 *
 * <p><b>Two steps, and both are counted.</b> Counting what builds a {@code DeclaredOperation} alone
 * would leave the step above it open: everything that builds one goes through
 * {@link CompleteSignature}, so a caller that made a signature out of a name and a list of its own
 * choosing would reach a {@code DeclaredOperation} through the one path this admits. What has to be
 * known is every way a signature comes to be, which is the first test below.
 *
 * <p><b>Assembling and asking are not the same finding.</b> A method handing back a signature is
 * either making one out of a declaration or handing on one that was made — {@link Preserved} keeps
 * the ones the library was read into and answers with them. One more of the second is one more
 * reader and says nothing about who may read a declaration; one more of the first is a new
 * authority, which is what this is watching for.
 *
 * <p>Read off the compiled classes. A constructor call is in the calling class's constant pool
 * whatever it is spelled like, and a lambda's body is compiled into the class that wrote it, so a
 * caller cannot get out of this by writing the call somewhere shorter.
 */
class WhatReadsADeclarationIntoASignatureIsWrittenDownTest {

    private static final String SIGNATURE = "souther/compiler/core/CompleteSignature";

    private static final String DECLARED = "souther/compiler/core/DeclaredOperation";

    /** What hands back a complete signature: the class it is declared on, and its name. */
    private record Producer(String owner, String name) implements Comparable<Producer> {

        /** As a reader of a failure reads it, and as a licence below names it. */
        String shown() {
            return owner.replace('/', '.').replace('$', '.') + "." + name;
        }

        /** Made where it is declared on the signature itself, and handed on anywhere else. */
        boolean assembles() {
            return owner.equals(SIGNATURE);
        }

        @Override
        public int compareTo(Producer other) {
            return shown().compareTo(other.shown());
        }
    }

    /** A method that hands one back, and what it is doing with it. */
    private record Licence(String who, String why) { }

    /**
     * Every way to come by a complete signature, and what each is for.
     *
     * <p>Declared because the tests below are only as wide as this is. A second factory on the
     * signature, or a reader that answers with one it kept, lands here first and is said to be one
     * of the two things before anything asks who may call it.
     */
    private static final List<Licence> WAYS_IN = List.of(
            new Licence("souther.compiler.core.CompleteSignature.ofDeclaration",
                    "a declaration that states what it takes and what it answers"),
            new Licence("souther.compiler.core.CompleteSignature.ofSettledValue",
                    "a value: no parameters, and what its own check settled it as"),
            new Licence("souther.compiler.check.OperationFactBinder.declaredSignature",
                    "reads a library declaration where a fact about that operation is held to it"),
            new Licence("souther.compiler.check.Preserved.signatureOf",
                    "hands on the one the library was read into, for an operation this"
                            + " representation keeps standing"),
            new Licence("souther.compiler.check.Preserved.valueKept",
                    "the same for a value it keeps a reference to"),
            new Licence("souther.compiler.check.Preserved.Settling.settledAs",
                    "hands on what a value's own check recorded, under the name that recording"
                            + " carried"));

    /**
     * The ones that read a declaration. What may add to this is a decision about who is allowed to
     * say that a name has been read against what declares it.
     */
    private static final List<Licence> MAY_ASSEMBLE = List.of(
            new Licence("souther.compiler.check.Preserved.readTheLibrary",
                    "reads the library's own declarations, which state both halves"),
            new Licence("souther.compiler.check.OperationFactBinder.declaredSignature",
                    "the same declarations, read where a fact about an operation is held to them"),
            new Licence("souther.compiler.check.HelperTyping.checkHelpers",
                    "a value of this module, written with no parameters and just checked for what"
                            + " it answers"));

    @Test
    void everyWayToComeByACompleteSignatureIsWrittenDown() {
        assertEquals(declared(WAYS_IN), shown(producers()),
                "a method handing back a complete signature is a way to come by one, and what may"
                        + " call it is decided below. What each of these is for: " + why(WAYS_IN));
    }

    /**
     * And that the ways in are all of them.
     *
     * <p>Every one of these comes from the one constructor, so a method that made one and handed it
     * back as something else — inside an optional, inside a map — would be a way in the enumeration
     * above cannot see, its own type having been erased from what it returns. It would be seen
     * here, as a maker that is not one of the ways in.
     */
    @Test
    void nothingMakesOneExceptTheWaysInToMakingOne() {
        assertEquals(shown(assembling()),
                callers(Set.of(new Producer(SIGNATURE, "<init>"))),
                "a complete signature made anywhere else is one whose making nothing decided");
    }

    /** Who reads a declaration into a signature. */
    @Test
    void onlyAReaderOfADeclarationAssemblesOne() {
        assertEquals(declared(MAY_ASSEMBLE), callersOf(Producer::assembles),
                "a signature is made where a declaration is being read and nowhere else. What each"
                        + " of these reads: " + why(MAY_ASSEMBLE));
    }

    /**
     * And that a declared operation is minted nowhere but on a signature coming to be.
     *
     * <p>The expected answer is one method, so it is worth saying what the pair of assertions is
     * for: a refactoring that left nothing building one at all would satisfy "nobody unauthorized
     * builds one" perfectly.
     */
    @Test
    void onlyASignatureComingToBeMintsADeclaredOperation() {
        assertEquals(Set.of("souther.compiler.core.CompleteSignature.<init>"),
                callers(Set.of(new Producer(DECLARED, "<init>"))).keySet(),
                "a name said to have been read against a declaration, where no declaration was");
    }

    @Test
    void andSomethingDoesMintOne() {
        assertFalse(callers(Set.of(new Producer(DECLARED, "<init>"))).isEmpty(),
                "nothing mints one, so the rule above saw no classes");
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
                        .equals("L" + SIGNATURE + ";")) {
                    out.add(new Producer(owner, method.methodName().stringValue()));
                }
            }
        }
        assertFalse(out.isEmpty(), "nothing hands one back at all, so this says nothing");
        return out;
    }

    /** The ways in declared on the signature itself, which is how one is made. */
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
