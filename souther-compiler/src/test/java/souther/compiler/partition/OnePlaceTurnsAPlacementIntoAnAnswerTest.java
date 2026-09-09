package souther.compiler.partition;

import org.junit.jupiter.api.Test;
import souther.compiler.WhatWasCompiled;

import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeModel;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.util.Map;
import java.util.TreeSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A placement becomes an answer in one place, so that the last step cannot be written per shape.
 *
 * <p>What a search hands back is the same thing whatever it was searching for: one position at a
 * place of its carrier, two of them a distance apart, a form at a level. Each of those searches is
 * written on its own, and each also had to remember to hold what it found against the rules before
 * offering it — a placement inside every range can still be one the rules refuse, and offered as a
 * row it comes back refused where it is built, which a report says as every value having been
 * tried.
 *
 * <p>Two of the three did not remember. They were noticed one at a time, which is what a per-shape
 * obligation costs; the third would have been noticed the day a fourth shape of line was drawn. So
 * the obligation is not per shape: there is one place a placement becomes an answer, and a search
 * that goes round it fails here.
 *
 * <p>Read off the compiled classes, because what is checked is whose code runs the constructor and
 * no way of writing the call can dress that up as something else.
 */
class OnePlaceTurnsAPlacementIntoAnAnswerTest {

    /**
     * Each answer that carries a proof, and the one method that may make one.
     *
     * <p>Two shapes of the same obligation, so one table and one reading of the classes rather than
     * a check per shape. Which methods of the owning type derive one is the table's to say: a coset
     * whose values step and one whose values fill are different sets and are answered apart. What
     * the check is for is that nothing outside the type answers at all. What a placement is offered as is one; and what a search may conclude from
     * finding nothing is the other — {@link souther.compiler.partition.CandidateDomain.None} says a
     * position has no value left and {@code One} says it has exactly one, and a walk that comes back
     * empty-handed off either of them is a proof that reaches {@code Impossible}. Derived somewhere
     * else, from a range that could not name a value or from a search that gave up, they would take
     * a coverage item away.
     */
    private static final Map<String, Set<String>> MAY_MAKE_ONE = Map.of(
            "souther.compiler.partition.Realization$Found",
            Set.of("souther.compiler.partition.LevelRealizer.found"),
            "souther.compiler.partition.CandidateDomain$None",
            Set.of("souther.compiler.partition.CandidateDomain.of",
                    "souther.compiler.partition.CandidateDomain.stepping",
                    "souther.compiler.partition.CandidateDomain.filling"),
            "souther.compiler.partition.CandidateDomain$One",
            Set.of("souther.compiler.partition.CandidateDomain.stepping",
                    "souther.compiler.partition.CandidateDomain.filling"));

    @Test
    void nothingElseTurnsAPlacementIntoAnAnswer() {
        Map<String, Set<String>> made = new java.util.TreeMap<>();
        for (ClassModel model : WhatWasCompiled.compiled().all()) {
            String from = model.thisClass().asInternalName().replace('/', '.');
            for (var method : model.methods()) {
                CodeModel code = method.code().orElse(null);
                if (code == null) {
                    continue;
                }
                for (var element : code) {
                    if (element instanceof NewObjectInstruction made0) {
                        String what = made0.className().asInternalName().replace('/', '.');
                        if (MAY_MAKE_ONE.containsKey(what)) {
                            made.computeIfAbsent(what, ignored -> new TreeSet<>())
                                    .add(from + "." + method.methodName().stringValue());
                        }
                    }
                }
            }
        }
        // Every one of them is read, so an answer nothing makes at all cannot pass by being absent.
        assertEquals(new TreeSet<>(MAY_MAKE_ONE.keySet()), new TreeSet<>(made.keySet()),
                "answers this is reading the code for");
        MAY_MAKE_ONE.forEach((what, who) -> assertEquals(new TreeSet<>(who), made.get(what),
                "who hands back a " + what));
    }

}
