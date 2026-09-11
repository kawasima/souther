package souther.compiler.report;

import org.junit.jupiter.api.Test;
import souther.compiler.execute.EvaluationPolicy;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.query.InputCaseEvidence;
import souther.compiler.query.PartitionEvidence;
import souther.compiler.query.Weakening;
import souther.compiler.query.WeakeningSet;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a whole went without holds everything its parts went without.
 *
 * <p>Stronger than the rule beside it. {@code AMeasurementIsNeverStrongerThanWhatItIsAssembledFrom}
 * asks whether the words agree — a complete whole over a partial part is a contradiction — and a
 * whole that kept its own word while dropping a part's account of why passes it: both say
 * {@code partial}, and the fact that made one of them so is gone. The word was never the point.
 *
 * <p>So this asks the union. A parent's weakening is the union of its parts' and nothing else, which
 * is the whole of the arithmetic #953 replaced a lattice with, and the one property that makes the
 * arithmetic worth having: a fact reaching a whole by any path arrives, and a producer that forgets
 * to hand one over is a whole that is short of it.
 *
 * <p>Held over the values rather than over the document, because the document is where a fact can go
 * missing without anything else moving. The projection is checked where it is written
 * ({@code AMeasureWeakerThanCompleteSaysWhatMadeItSo}); this is about what it is given.
 */
class WhatAWholeWentWithoutIsWhatItsPartsWentWithoutTest {

    /**
     * A model whose rows are seen in part, whose rules are read in part, and one of whose behaviors
     * nobody wrote a row for.
     *
     * <p>Each is a different way for a fact to arrive, and a whole that dropped one of them is what
     * this is looking for — so a model producing only one kind would hold the rule over one path
     * and say nothing about the rest.
     *
     * <p>The mapping is what leaves a reading short. What it holds is a value this compiler does not
     * name a position for, so {@code Amount}'s rule is written under a position no reading is ever
     * opened at — and nothing a row does reaches it.
     *
     * <p>It used to be an {@code Assignee?} with a clause behind the option. That clause is read at
     * the narrowing, where a row meets it and a border is owed against it, so the model went short of
     * nothing (#1072). What is wanted here is a reading that really did not happen, which is what a
     * mapping still is.
     */
    private static final String MODEL = """
            module example.whole

            data Draft = { n: Int }
            data Done = { n: Int }
            data Small = { n: Int }
            data Amount = Int
                invariant ranged = value >= 0
            data Issue = { cost: Map<String, Amount> }

            partial let spin (n: Int): Int = spin(n)

            behavior go : (request: Draft) -> Done | Small
                constructs Done, Small

            let go (request) = {
                guard request.n <= 0 else Done { n = spin(request.n) }
                Small { n = request.n }
            }

            behavior classify : (i: Issue) -> Done
            behavior unwritten : (request: Draft) -> Done | Small
                constructs Done, Small

            example go
                | (Draft { n = 1 }) -> Done { n = 1 }
            """;

    /**
     * Steps enough for every row of {@link #MODEL} that comes to an answer, and few enough that the
     * one that does not is done spending them at once.
     *
     * <p>{@code spin} is here to leave a measure short of a fact, not to spend the shipped limit of
     * a hundred million steps finding that out — which is nine seconds of interpreting for a
     * weakening that arrives the same at a thousandth of it. What the limit is set to is held to
     * something in {@code ARowIsHeldToStepsAndNotToTheClock}, not here.
     */
    private static final EvaluationPolicy BOUNDED =
            EvaluationPolicy.DEFAULT.withStepLimit(100_000L);

    private static AdequacyReport report() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.withEvaluationPolicy(BOUNDED);
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation);
    }

    @Test
    void everyWholeHoldsWhatEachOfItsPartsWentWithout() {
        AdequacyReport report = report();
        List<String> lost = new ArrayList<>();
        Set<Weakening> everything = new LinkedHashSet<>();
        int loadBearing = 0;

        WeakeningSet acrossModules = WeakeningSet.none();
        for (AdequacyReport.ModuleReport module : report.modules()) {
            WeakeningSet acrossBehaviors = WeakeningSet.none();
            for (AdequacyReport.BehaviorReport behavior : module.behaviors()) {
                // The reading of the rows is one of the parts. Left out, this passes as long as
                // some measure counted over those rows carries the same facts — which is a set
                // holding one value twice and not a rule being kept. A behavior every measure of
                // which has nothing to be about carries them nowhere else, and that is the case the
                // rule is for (issue #996).
                WeakeningSet parts = behavior.reading().measured().weakening();
                List<WeakeningSet> apart = new ArrayList<>();
                // Not among the parts counted below. Whether a part is load-bearing asks whether it
                // carries something the rest do not, and the reading holds every gap the measures
                // counted over those rows took from it — so counting it makes each of them
                // redundant and the count says nothing. That the reading is load-bearing is held
                // where it can be shown on its own: a behavior every measure of which has nothing
                // to be about carries its run's shortfall nowhere else
                // (`AModuleEveryMeasureOfWhichDoesNotApplyStillSaysWhatItWentWithout`).

                if (behavior.signature() != null) {
                    Adequacy.SignatureEvidence signature = behavior.signature();
                    WeakeningSet cases = signature.output().cases().weakening()
                            .union(signature.inputs().weakening());
                    for (InputCaseEvidence each
                            : signature.inputs().made().orElseGet(java.util.List::of)) {
                        cases = cases.union(each.cases().weakening());
                    }
                    holds(lost, "signature of " + behavior.name(),
                            signature.counted().weakening(), cases);
                    parts = parts.union(signature.counted().weakening());
                    apart.add(signature.counted().weakening());
                }
                if (behavior.branch() != null) {
                    parts = parts.union(behavior.branch().measured().weakening());
                    apart.add(behavior.branch().measured().weakening());
                }
                if (behavior.boundaryReadings() != null) {
                    parts = parts.union(behavior.boundaryReadings().weakening());
                    apart.add(behavior.boundaryReadings().weakening());
                }
                if (behavior.partition() != null) {
                    PartitionEvidence partition = behavior.partition();
                    parts = parts.union(partition.partitioned().weakening())
                            .union(partition.pairs().counted().weakening());
                    for (PartitionEvidence.AxisCoverage axis : partition.axes()) {
                        parts = parts.union(axis.reached().weakening());
                    }
                    apart.add(partition.partitioned().weakening());
                    apart.add(partition.pairs().counted().weakening());
                }
                if (behavior.evidence().decision() != null) {
                    // Which rules of the body's decision the rows took, which is a part of the
                    // behavior like the arms. A row nothing watched leaves every rule nothing was
                    // seen taking as one a row may already take, and that is what this part knows
                    // and no other does.
                    parts = parts.union(behavior.evidence().decision().took().weakening());
                    apart.add(behavior.evidence().decision().took().weakening());
                }
                if (behavior.evidence().account() != null) {
                    // This behavior's own account, which is what its weakening is over. A row owed
                    // to the declarations that drew a line is short or not short in the module's
                    // account of them.
                    for (souther.compiler.query.BorderObligationPointAssessment point
                            : behavior.account()) {
                        parts = parts.union(point.item().weakening());
                    }
                }
                loadBearing += loadBearing(apart);
                holds(lost, "behavior " + behavior.name(), behavior.weakenedBy(), parts);
                acrossBehaviors = acrossBehaviors.union(behavior.weakenedBy());
            }
            // What the module's declarations are owed is a part of the module the way a behavior is.
            // Left out, a module that went without something only its declaration account knows
            // would report itself measured in full — and the account is where a row owed to a
            // declaration is short, since no behavior carrying the type is short by it.
            holds(lost, "module " + module.module(), module.weakenedBy(),
                    acrossBehaviors.union(module.declarationsWeakenedBy()));
            acrossModules = acrossModules.union(module.weakenedBy());
            everything.addAll(module.weakenedBy().causes());
        }
        holds(lost, "the report", report.weakenedBy(), acrossModules);

        // And the run reached more than one way a fact arrives, so the rule above was held over
        // more than one path rather than over whichever one this model happened to take.
        //
        // Pinned rather than counted. A threshold is met by whatever happens to be produced, and
        // the arms are not fixed: `RowDidNotFinish` was one of these and is now an observation like
        // any other, said in the reasons' own vocabulary (issue #996). What the fixture reaches is
        // written down, so a path that stops arriving fails here instead of being made up for.
        Set<String> kinds = new LinkedHashSet<>();
        everything.forEach(each -> kinds.add(each.getClass().getSimpleName()));
        assertEquals(Set.of("ObservationIncomplete", "ModelReadingIncomplete",
                        "DecisionRunNotWatched"), kinds,
                () -> "the ways this model goes without something: " + kinds);

        // And at least one part carries a fact no other part of its behavior does, so the rule
        // above is not one a whole satisfies whatever it drops. A union is a set: a part left out
        // of it goes unnoticed for as long as something else happens to carry the same fact, and a
        // fixture in which no part is load-bearing holds this rule over nothing at all.
        assertTrue(loadBearing > 0,
                "no part of any behavior went without anything the rest did not, so dropping any"
                        + " one of them from the union would leave this saying nothing");

        assertEquals(List.of(), lost, "a whole and its parts disagreeing about what was gone without");
    }

    /**
     * A type whose clause draws a line, carried by two behaviors, one of whose rows is never read.
     *
     * <p>Both points of that line are owed to the declaration that drew it, so neither behavior is
     * owed a row at either — and the row nobody read leaves the value at that point unreadable. What
     * comes of that is a reason only the module's declaration account holds.
     */
    private static final String ONE_ROW_UNREAD = """
            module example.owed

            data Amount = Int
                invariant value >= 0

            data Draft = { n: Amount }
            data Ok = { n: Int }

            behavior seen : (d: Draft) -> Ok
                constructs Ok
            let seen (d) = Ok { n = d.n.value }

            behavior unread : (d: Draft) -> Ok
                constructs Ok
            let unread (d) = Ok { n = d.n.value }

            example seen
                | "inside the run" : (Draft { n = Amount(5) }) -> Ok { n = 5 }

            example unread
                | "never read" : (Draft { n = Amount(7) }) -> Ok { n = 7 }
            """;

    /**
     * And a module holds what its declaration account went without, where no behavior of it did.
     *
     * <p>The load-bearing half of the rule above for that part. Over most models the two accounts
     * are short of the same reading, so the union holds whether or not anybody adds the declarations
     * to it — and a part that is only ever carried by another part is a part this rule says nothing
     * about.
     *
     * <p>What is only ever the declaration account's is a row owed to a declaration that could not
     * be read at. The behaviors carrying the type are owed no row there, so no measure of theirs is
     * short by it: what they went without is that a row was not read, which is their own reading's
     * to say and is a different reason.
     */
    @Test
    void aModuleHoldsWhatOnlyItsDeclarationsWentWithout() {
        Compilation compilation = Compilation.ofSource(ONE_ROW_UNREAD, "Main");
        compilation.withJvmExampleDeadlines(souther.compiler.DoesNotComeBack.overrunningOn(
                souther.compiler.DoesNotComeBack.everythingAboutRowsOf("unread")));
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        AdequacyReport.ModuleReport module =
                AdequacyReport.of(compilation).modules().getFirst();

        WeakeningSet behaviors = WeakeningSet.none();
        for (AdequacyReport.BehaviorReport behavior : module.behaviors()) {
            behaviors = behaviors.union(behavior.weakenedBy());
        }
        Set<Weakening> onlyTheDeclarations =
                new LinkedHashSet<>(module.declarationsWeakenedBy().causes());
        onlyTheDeclarations.removeAll(behaviors.causes());

        assertFalse(onlyTheDeclarations.isEmpty(),
                () -> "nothing here is the declaration account's alone, so this holds the module to"
                        + " nothing: " + module.declarationsWeakenedBy().causes());
        assertTrue(module.weakenedBy().causes().containsAll(onlyTheDeclarations),
                () -> "a module that did not hear what only its declaration account went without: "
                        + module.weakenedBy().causes());
    }

    /** How many of {@code parts} hold a fact none of the others do. */
    private static int loadBearing(List<WeakeningSet> parts) {
        int carrying = 0;
        for (int i = 0; i < parts.size(); i++) {
            Set<Weakening> rest = new LinkedHashSet<>();
            for (int j = 0; j < parts.size(); j++) {
                if (i != j) {
                    rest.addAll(parts.get(j).causes());
                }
            }
            if (parts.get(i).causes().stream().anyMatch(one -> !rest.contains(one))) {
                carrying++;
            }
        }
        return carrying;
    }

    /**
     * And a module with a reason to give does not call its measurement complete.
     *
     * <p>The other side of the union, and the sentence issue #996 is titled with. The word above a
     * module and the reasons under it are one set read twice — the word is whether it is empty, the
     * reasons are its observations taken out — so a module cannot say it read everything while
     * naming something it could not read.
     *
     * <p>One direction only. A measurement can be short of something no observation covers — a space
     * of combinations too large to walk is not a reason a document lists — so {@code partial} with
     * nothing to name is a state the report is entitled to.
     */
    @Test
    void aModuleWithAReasonToGiveIsNotComplete() {
        for (AdequacyReport.ModuleReport module : report().modules()) {
            if (!module.incompleteness().isEmpty()) {
                assertEquals(souther.compiler.observe.MeasurementStatus.PARTIAL, module.status(),
                        () -> "`" + module.module() + "` names what it could not read and calls its"
                                + " measurement complete: " + module.incompleteness());
            }
        }
    }

    /**
     * What {@code whole} carries and what {@code parts} went without, held to being the same.
     *
     * <p>Both ways round, because the rule is a union and nothing else. Short of what its parts went
     * without, a whole has dropped a fact somebody is owed; over it, a whole has a fact none of its
     * parts has — which is the shape the report was in when it read the module's own list of what
     * could not be read and made weakenings out of it beside asking the measures. One of those is
     * the defect this issue is about and the other is how it hid.
     */
    private static void holds(List<String> lost, String what, WeakeningSet whole,
                              WeakeningSet parts) {
        for (Weakening each : parts.causes()) {
            if (!whole.causes().contains(each)) {
                lost.add(what + " does not carry " + each + ", which one of its parts went without");
            }
        }
        for (Weakening each : whole.causes()) {
            if (!parts.causes().contains(each)) {
                lost.add(what + " carries " + each + ", which none of its parts went without");
            }
        }
    }
}
