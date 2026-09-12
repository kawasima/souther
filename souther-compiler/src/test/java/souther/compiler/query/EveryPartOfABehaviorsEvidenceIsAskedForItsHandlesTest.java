package souther.compiler.query;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import souther.compiler.check.RuleCitation;
import souther.compiler.check.RuleCitations;
import souther.compiler.conformance.RepositoryModels;
import souther.compiler.report.AdequacyReport;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a behavior's evidence holds handles for is what its parts hold handles for.
 *
 * <p>The union over the parts and nothing else, which is the rule {@code weakening} already
 * follows. What it costs to get this wrong is what issue #996 cost one question over: the parts are
 * fields, the union is written out by hand, and a part left out of it reaches nobody while the whole
 * answers as though it had been asked. A page then names a rule with nowhere to point, or — as
 * happened here — never names it at all, and nothing fails either way.
 *
 * <p><b>Walked over the values, not over the field types.</b> A part is a measurement of some value
 * and the handles are the value's, so the type of the field says {@code Measure} and answers
 * nothing. What a run holds is what it holds: every model this repository carries is compiled, and
 * each part of each behavior is asked whether the value in it is one of the things that answers for
 * a rule it read.
 */
@Tag("population")
class EveryPartOfABehaviorsEvidenceIsAskedForItsHandlesTest {

    @Test
    void theWholeHoldsWhatEveryPartHolds() {
        int walked = 0;
        Set<String> holding = new LinkedHashSet<>();
        for (Compilation compilation : RepositoryModels.all()) {
            for (BehaviorEvidence evidence : behaviorsOf(compilation)) {
                Set<RuleCitation> held = new LinkedHashSet<>();
                for (RecordComponent part : BehaviorEvidence.class.getRecordComponents()) {
                    Set<RuleCitation> mine = handlesIn(valueOf(evidence, part));
                    if (!mine.isEmpty()) {
                        holding.add(part.getName());
                    }
                    held.addAll(mine);
                }
                assertEquals(held, evidence.ruleCitations(),
                        "a behavior's evidence holds the handles its parts hold");
                walked += held.size();
            }
        }
        // The walk found something to be about. Every assertion above holds of a walk that read no
        // value at all, which is what a check written from the field types would have been.
        assertTrue(walked > 0, "the models walked hold behaviors whose measures read rules");

        // And which parts those are, because the assertion above is only as wide as the parts that
        // hold a handle. A measure added to this record that reads rules is a part nothing asks
        // unless somebody writes it into the union, which is the failure this is here to raise —
        // and the union coming out equal says nothing about a part that holds nothing.
        assertEquals(Set.of("partition", "boundaryReadings", "account"), holding,
                "the parts of a behavior's evidence that hold a handle for a rule they read");
    }

    /**
     * Which of those parts the union would be short of if it were not asked.
     *
     * <p>Written down because it is not all of them. The account's handles are the ones its lines
     * already hold — a point is owed at a line, and the rule is the line's — so the union comes out
     * the same whether or not the account is asked, and a mutation dropping it is green. What that
     * means is that the assertion above has no subject at that part and not that the part is
     * exempt: the whole asks each of its parts because that is what a whole does, and the day the
     * account holds a handle of its own is the day the asking is what carries it.
     */
    @Test
    void andWhichOfThemTheUnionWouldBeShortOfWithoutTheAsking() {
        Set<String> alone = new LinkedHashSet<>();
        for (Compilation compilation : RepositoryModels.all()) {
            for (BehaviorEvidence evidence : behaviorsOf(compilation)) {
                Map<String, Set<RuleCitation>> held = new LinkedHashMap<>();
                for (RecordComponent part : BehaviorEvidence.class.getRecordComponents()) {
                    held.put(part.getName(), handlesIn(valueOf(evidence, part)));
                }
                held.forEach((name, mine) -> {
                    Set<RuleCitation> beside = new LinkedHashSet<>();
                    held.forEach((other, cited) -> {
                        if (!other.equals(name)) {
                            beside.addAll(cited);
                        }
                    });
                    if (!beside.containsAll(mine)) {
                        alone.add(name);
                    }
                });
            }
        }
        assertEquals(Set.of("partition", "boundaryReadings"), alone,
                "the parts that hold a handle no other part of the same behavior holds");
    }

    /**
     * The handles one part holds, which is the part's own answer wherever it has one.
     *
     * <p>Two shapes and both are what a field of this record can be: the value itself, and a
     * measurement of a list of them. Nothing deeper — a shape this cannot read is a part whose
     * handles the assertion above will find missing from the whole, which is the answer wanted
     * rather than a walk that goes looking.
     */
    private static Set<RuleCitation> handlesIn(Object value) {
        Set<RuleCitation> out = new LinkedHashSet<>();
        switch (value) {
            case null -> { }
            case RuleCitations it -> out.addAll(it.ruleCitations());
            case Measure<?> it -> it.made().ifPresent(made -> out.addAll(handlesIn(made)));
            case List<?> it -> it.forEach(each -> out.addAll(handlesIn(each)));
            default -> { }
        }
        return out;
    }

    private static Object valueOf(BehaviorEvidence evidence, RecordComponent part) {
        try {
            return part.getAccessor().invoke(evidence);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("a part of a behavior's evidence is readable", e);
        }
    }

    private static List<BehaviorEvidence> behaviorsOf(Compilation compilation) {
        List<BehaviorEvidence> out = new ArrayList<>();
        AdequacyReport.of(compilation).modules()
                .forEach(module -> module.behaviors()
                        .forEach(behavior -> out.add(behavior.evidence())));
        return out;
    }
}
