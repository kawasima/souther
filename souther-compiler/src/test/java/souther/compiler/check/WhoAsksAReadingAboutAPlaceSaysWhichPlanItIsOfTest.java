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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Who asks a reading what arrives somewhere, written down with what says the reading is of the plan
 * the place came from.
 *
 * <p>A reading files its answers under the places of one plan. Asked about a place of another, it
 * has nothing filed and says so the way it says a place its own walk never came to — unsettled for
 * an arm, and nothing known for a comparison. Both are the fail-open answer every reader below acts
 * on, so a reader handed the wrong reading is told that nothing arrives anywhere and that nothing
 * is known about any comparison, which is what a body with nothing to measure looks like.
 *
 * <p><b>The pairing is what is held, and it is held by the reader.</b> A place cannot be asked which
 * plan it is of: an arm no run can be recorded in carries no numbering, and giving it one would put
 * the plan's own address inside the identity the reading exists to keep apart from it. So
 * {@link PathReachability.Answers#requireNumbering} is asked once where a reader puts a plan and a
 * reading together — not at each lookup, which would make an instrument that works in proportion to
 * what it measures.
 *
 * <p><b>Which is why this list exists.</b> The invariant is the reading's and the check is the
 * reader's, so nothing in the reading says who the readers are. Found by searching, the population
 * is whatever the search turned up — and a reader added afterwards is a plan and a reading put
 * together with nothing holding them to each other. This is the population, taken off the compiled
 * classes, and a new reader arriving in it is one to look at rather than one this can decide about.
 *
 * <p><b>A licence answers a second question beside that one.</b> An answer here is about a place,
 * and a construct the author wrote stands at a place per call site of whatever carries it — so a
 * reader turning one of these answers into a fact about the construct is reading one copy for all
 * of them, and which copy it met is the order the walk went in. What may be said of the construct
 * is what holds of every copy, and where the count of those constructs is already made that way
 * ({@link souther.compiler.query.Adequacy.BranchEvidence#unreached}) the answer is there rather than
 * in a second walk. A licence that does not say which of the two a reader is doing is a licence for
 * the wrong one.
 *
 * <p>{@code asRunWith} is not here. What it is handed is a set of probes rather than a place, and it
 * holds them to this reading's numbering itself — one pass over what it was given, which is a set it
 * already walks.
 *
 * <p>Read off the compiled classes, so what is counted is what a method does rather than what a
 * reading of the sources makes of it. The tests are not in {@code target/classes}, and what is read
 * is this module's classes.
 */
class WhoAsksAReadingAboutAPlaceSaysWhichPlanItIsOfTest {

    private static final String A_READING =
            "souther/compiler/check/PathReachability$Answers";

    /** The two ways a reading is asked about a place of a plan. Both answer an absence as an
     *  ordinary fact, so neither can tell a caller it was handed the wrong reading. */
    private static final Set<String> ASKS_ABOUT_A_PLACE = Set.of("at", "arrivalAt");

    /** A method that asks, how many times it does, and what says the reading is the plan's. */
    private record Licence(String who, int asks, String why) { }

    private static final List<Licence> MAY_ASK = List.of(
            new Licence("souther.compiler.claims.Claims.of -> at", 1,
                    "the claims name arms of one plan and say which, and this holds the reading to"
                            + " being that plan's before it looks any of them up. Both halves are"
                            + " handed in, so a caller is what could put two modules' together"),
            new Licence("souther.compiler.partition.ProducedCases.produce -> at", 1,
                    "a private step of a walk whose way in takes the plan and the reading together"
                            + " and holds them to each other there — before the shortcut for a"
                            + " reading that proves nothing unreached, which a foreign reading"
                            + " would otherwise leave by"),
            new Licence("souther.compiler.partition.GuardThresholds.of -> arrivalAt", 1,
                    "a step of the walk over one behavior's guards, whose way in takes the plan and"
                            + " the reading together and holds them to each other there. What an"
                            + " absence means here is that nothing is known about the comparison,"
                            + " which restricts nothing — the widest answer there is, and the one a"
                            + " reading of another module would give about every comparison"),
            new Licence("souther.compiler.query.Adequacy.BranchEvidence.owed -> at", 1,
                    "the arms and the reading both come out of the store under one module name,"
                            + " inside the one method that asks for either. Nothing hands this pair"
                            + " over, so there is no pair for a caller to get wrong and nothing"
                            + " here for a check to be asked about"));

    @Test
    void everyReaderThatAsksAReadingAboutAPlaceIsWrittenDownWithWhatMakesItSafe() {
        assertEquals(declared(), asked(),
                "a reading asked about a place of another plan answers that nothing arrives there,"
                        + " which is the answer it gives about a place its own walk never came to."
                        + " So a reader arriving here is one to look at rather than one this can"
                        + " decide about. Who asks today, and what makes each safe: " + why());
    }

    private static Map<String, Integer> declared() {
        Map<String, Integer> out = new TreeMap<>();
        MAY_ASK.forEach(each -> out.put(each.who(), each.asks()));
        return out;
    }

    private static Map<String, String> why() {
        Map<String, String> out = new LinkedHashMap<>();
        MAY_ASK.forEach(each -> out.put(each.who(), each.why()));
        return out;
    }

    /**
     * The method a person wrote, for a name javac may have made up.
     *
     * <p>A lambda is compiled to a method named after the one it was written inside and numbered by
     * where it fell among that class's lambdas. The number moves when a lambda is added anywhere in
     * the class, which has nothing to do with who asks a reading about a place — and a list that
     * went stale for that would be one people learned to re-fit rather than read.
     */
    private static String wroteIt(String compiled) {
        if (!compiled.startsWith("lambda$")) {
            return compiled;
        }
        return compiled.substring("lambda$".length(), compiled.lastIndexOf('$'));
    }

    /** How many times each method of the compiler asks a reading about a place. */
    private static Map<String, Integer> asked() {
        Map<String, Integer> calls = new TreeMap<>();
        for (ClassModel model : WhatWasCompiled.compiled().all()) {
            String from = model.thisClass().asInternalName().replace('/', '.').replace('$', '.');
            for (MethodModel method : model.methods()) {
                method.code().ifPresent(code -> code.forEach(element -> {
                    if (element instanceof InvokeInstruction call
                            && call.owner().asInternalName().equals(A_READING)
                            && ASKS_ABOUT_A_PLACE.contains(call.name().stringValue())) {
                        calls.merge(from + "." + wroteIt(method.methodName().stringValue())
                                + " -> " + call.name().stringValue(), 1, Integer::sum);
                    }
                }));
            }
        }
        return calls;
    }
}
