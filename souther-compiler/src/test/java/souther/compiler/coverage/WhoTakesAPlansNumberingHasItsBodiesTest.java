package souther.compiler.coverage;

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
 * Who takes a plan's numbering, written down with what makes it safe where it stands.
 *
 * <p>{@link CoverageSites.Plan#NONE} is the plan of nothing, and two different callers reach for
 * it: the emitter told to write no probes, and a measure of a module whose bodies did not come out.
 * For the first it is what it says. For the second it is a stand-in — and a stand-in has a
 * numbering, which is nobody's: not of that module, and over no places. A recording of the module
 * aligned against that is refused as a run of somewhere read under a numbering of nowhere, which is
 * right and is not the answer wanted. A module with no bodies has no numbering, and what a reader
 * without one has is no account of any run.
 *
 * <p>Which is why a reader that wants a numbering asks the check for the one it issued rather than
 * for a plan of its own to take one off. The check answers off the plan it made and kept, which is
 * of the bodies it holds; a reader reaching for any other plan is one branch away from the stand-in,
 * and where the bodies are there is no answer at all rather than an empty one.
 *
 * <p><b>Two things a taker does with one, and the stand-in is dangerous to one of them.</b> A
 * numbering read as an address space says a run happened at these places, and taken off the
 * stand-in it says that of nowhere. A numbering read as a name says which plan a value is of, and
 * is only ever compared with another taken the same way — two readers of the plan of nothing agree
 * they hold the plan of nothing, which is true. The reason beside each entry says which of the two
 * it is doing.
 *
 * <p><b>What this holds is the population, and not that each of them is right.</b> A walk over the
 * compiled classes sees which method takes a numbering off a plan; it does not see which plan, so
 * it cannot tell a reader holding the checked bodies from one holding the stand-in. That is what
 * the reason beside each entry is for, and it is read by a person. What the check itself stops is a
 * new reader appearing without one — which is how the reader this was written for got in.
 *
 * <p>Read off the compiled classes, so what is counted is what a method does rather than what a
 * reading of the sources makes of it. The tests are not in {@code target/classes}, and what is read
 * is this module's classes.
 */
class WhoTakesAPlansNumberingHasItsBodiesTest {

    private static final String A_PLAN = "souther/compiler/coverage/CoverageSites$Plan";

    /** The two ways a plan hands its numbering over. */
    private static final Set<String> ITS_NUMBERING = Set.of("numbering", "identity");

    /** A method that may take it, how many times it does, and what says it has the bodies. */
    private record Licence(String who, int calls, String why) { }

    private static final List<Licence> MAY_TAKE = List.of(
            new Licence("souther.compiler.query.Bodies.Elaborated.numberingIdentity -> identity", 1,
                    "the plan this takes it off is the one the check walked these bodies to make"
                            + " and kept, so it is the numbering that check issued and there is no"
                            + " stand-in to have taken it from: a module whose bodies did not come"
                            + " out is answered for by there being no answer here at all. Taken"
                            + " rather than held beside the plan, so a reader cannot be handed two"
                            + " answers about one module's arms"),
            new Licence("souther.compiler.codegen.Backend.generating -> identity", 1,
                    "what the classes being written are numbered by, taken off the plan the check"
                            + " holds for the bodies it is emitting — and taken only where coverage"
                            + " was asked for, so it is never the stand-in's. The emission answers"
                            + " with it, rather than a caller working out a numbering of its own"
                            + " beside the one the probes were written from"),
            // The four below take one for the other reason there is to take one. None of them
            // aligns a recording against it: each stamps or compares a reading of a plan with the
            // plan it is being read against, so what a numbering says here is "these two are the
            // same plan's" and never "this run happened at these places". The stand-in cannot make
            // one of them answer about somewhere else — two readers of the plan of nothing agree
            // that they hold the plan of nothing, which is what they hold.
            new Licence("souther.compiler.check.PathReachability.of -> identity", 1,
                    "the reading stamps itself with the plan whose places it files its answers"
                            + " under, taken off the plan it was handed and walked. Absent, a"
                            + " reading would be a map of places with nothing saying whose, and a"
                            + " reader pairing it with another plan would be told every place of"
                            + " the body is one nothing reached"),
            new Licence("souther.compiler.claims.UnreachableClaims.of -> identity", 1,
                    "the claims name arms of the plan they were read against, and say so, taken"
                            + " off that same plan. What judges them looks each arm up in a"
                            + " reading, and this is what says which reading answers about them"),
            new Licence("souther.compiler.partition.ProducedCases.of -> identity", 1,
                    "the walk asks a reading about the arms of the plan it was handed, so the two"
                            + " are held to being one plan's before either is read. Both are"
                            + " parameters of one call, which is where a caller could put two"
                            + " modules' together"),
            new Licence("souther.compiler.query.Adequacy.BranchCoverage.lambda$compute$0"
                            + " -> identity", 1,
                    "the arms of one behavior and the reading that says what arrives at them are"
                            + " put together here, and are held to being one plan's. Taken only"
                            + " where the checked bodies came back, so the plan is theirs and never"
                            + " the stand-in this file is about"));

    @Test
    void everyReaderOfAPlansNumberingIsWrittenDownWithWhatMakesItSafe() {
        assertEquals(declared(), taken(),
                "a numbering taken off a plan that stands in for absent bodies is nobody's, and a"
                        + " reader aligning a recording against it is told the run was of somewhere"
                        + " else. So a reader arriving here is one to look at rather than one this"
                        + " can decide about. What takes one today, and what makes each safe: "
                        + why());
    }

    private static Map<String, Integer> declared() {
        Map<String, Integer> out = new TreeMap<>();
        MAY_TAKE.forEach(each -> out.put(each.who(), each.calls()));
        return out;
    }

    private static Map<String, String> why() {
        Map<String, String> out = new LinkedHashMap<>();
        MAY_TAKE.forEach(each -> out.put(each.who(), each.why()));
        return out;
    }

    /** How many times each method of the compiler takes a plan's numbering. */
    private static Map<String, Integer> taken() {
        Map<String, Integer> calls = new TreeMap<>();
        for (ClassModel model : WhatWasCompiled.compiled().all()) {
            String from = model.thisClass().asInternalName().replace('/', '.').replace('$', '.');
            for (MethodModel method : model.methods()) {
                method.code().ifPresent(code -> code.forEach(element -> {
                    if (element instanceof InvokeInstruction call
                            && call.owner().asInternalName().equals(A_PLAN)
                            && ITS_NUMBERING.contains(call.name().stringValue())) {
                        calls.merge(from + "." + method.methodName().stringValue()
                                + " -> " + call.name().stringValue(), 1, Integer::sum);
                    }
                }));
            }
        }
        return calls;
    }
}
