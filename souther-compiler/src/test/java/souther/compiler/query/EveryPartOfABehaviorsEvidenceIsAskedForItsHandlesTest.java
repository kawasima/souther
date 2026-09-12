package souther.compiler.query;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import souther.compiler.check.RuleCitation;
import souther.compiler.check.RuleCitations;
import souther.compiler.WhatWasCompiled;
import souther.compiler.conformance.RepositoryModels;
import souther.compiler.report.AdequacyReport;

import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.constant.ClassDesc;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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
        for (Compilation compilation : RepositoryModels.all()) {
            for (BehaviorEvidence evidence : behaviorsOf(compilation)) {
                Set<RuleCitation> held = new LinkedHashSet<>();
                for (RecordComponent part : BehaviorEvidence.class.getRecordComponents()) {
                    held.addAll(handlesIn(valueOf(evidence, part)));
                }
                assertEquals(held, evidence.ruleCitations(),
                        "a behavior's evidence holds the handles its parts hold");
                walked += held.size();
            }
        }
        // The walk found something to be about. Every assertion above holds of a walk that read no
        // value at all, which is what a check written from the field types would have been.
        assertTrue(walked > 0, "the models walked hold behaviors whose measures read rules");

        // And which parts those are, written down because the equality above is only as wide as
        // the parts that hold a handle: a part that holds none says nothing about the union either
        // way, and a part that starts holding one is what this names.
        assertEquals(Set.of("partition", "boundaryReadings", "account"), partsHoldingAHandle(),
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
     * <p><b>Nothing unknown is read as empty.</b> A shape this walk does not know is a shape it
     * cannot say holds no handle, and saying it anyway is how an oracle comes to share the omission
     * it is checking: a part added to the record and forgotten by the union would be nought here
     * and nought there, and the two would agree. So the kinds that hold no handle say so by being
     * written down, and anything else stops the check.
     */
    private static Set<RuleCitation> handlesIn(Object value) {
        Set<RuleCitation> out = new LinkedHashSet<>();
        switch (value) {
            case null -> { }
            case RuleCitations it -> out.addAll(it.ruleCitations());
            case Measure<?> it -> it.made().ifPresent(made -> out.addAll(handlesIn(made)));
            case List<?> it -> it.forEach(each -> out.addAll(handlesIn(each)));
            // Read and known to carry no handle for a rule: what the rows themselves came to, what
            // they establish about the cases, about the arms, and which rules of the body's
            // decision they took. None of them is a reading of a rule of the model.
            case Adequacy.RowReading _, Adequacy.SignatureEvidence _,
                 Adequacy.BranchEvidence _, DecisionEvidence _ -> { }
            default -> fail("a part of a behavior's evidence has not said whether it holds a handle"
                    + " for a rule it read: " + value.getClass());
        }
        return out;
    }

    /**
     * And the whole reads each of the parts that hold one, rather than coming out equal to them.
     *
     * <p>Two different things, and the set above only holds the first. The account's handles are
     * the ones its lines already hold — a point is owed at a line, and the rule is the line's — so
     * a union that never asked the account comes out the same, and the equality above is green. The
     * question this asks is the other one: which parts the method actually reads, taken off what
     * javac made of it rather than off what the answer came to.
     *
     * <p>Which parts it must read is not written down here either. It is the parts a run finds a
     * handle in, so the two answers are one fact — a part that starts holding one is a part the
     * whole has to be reading by then, and the failure names it.
     */
    @Test
    void andTheWholeReadsEachPartThatHoldsOne() {
        assertEquals(partsHoldingAHandle(), partsReadBy("ruleCitations"),
                "the parts a behavior's evidence reads when it is asked for its handles");
    }

    /** Which parts of the record {@code method} reads, as the compiled method reads them. */
    private static Set<String> partsReadBy(String method) {
        ClassDesc owner = BehaviorEvidence.class.describeConstable().orElseThrow();
        Set<String> read = new LinkedHashSet<>();
        ClassModel model = WhatWasCompiled.compiled()
                .find(BehaviorEvidence.class.getName()).orElseThrow();
        Set<String> components = new LinkedHashSet<>();
        for (RecordComponent each : BehaviorEvidence.class.getRecordComponents()) {
            components.add(each.getName());
        }
        for (MethodModel each : model.methods()) {
            if (!each.methodName().stringValue().equals(method)) {
                continue;
            }
            each.code().ifPresent(code -> {
                for (var element : code) {
                    if (element instanceof FieldInstruction field
                            && field.owner().asSymbol().equals(owner)
                            && components.contains(field.name().stringValue())) {
                        read.add(field.name().stringValue());
                    }
                }
            });
        }
        return read;
    }

    /** Which parts a run of every model this repository carries finds a handle in. */
    private static Set<String> partsHoldingAHandle() {
        Set<String> holding = new LinkedHashSet<>();
        for (Compilation compilation : RepositoryModels.all()) {
            for (BehaviorEvidence evidence : behaviorsOf(compilation)) {
                for (RecordComponent part : BehaviorEvidence.class.getRecordComponents()) {
                    if (!handlesIn(valueOf(evidence, part)).isEmpty()) {
                        holding.add(part.getName());
                    }
                }
            }
        }
        return holding;
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
