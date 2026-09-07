package souther.compiler.query;

import org.junit.jupiter.api.Test;

import souther.compiler.check.BehaviorContract;
import souther.compiler.coverage.CoverageSites;
import souther.compiler.coverage.DecidedBy;
import souther.compiler.types.WrittenOwner;
import souther.compiler.check.Carrier;
import souther.compiler.check.ComparisonClaim;
import souther.compiler.check.RuleRef;
import souther.compiler.inputs.BlockReason;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.PositionId;
import souther.compiler.inputs.RulesLeftUnread;
import souther.compiler.inputs.TermOrdersFixtures;
import souther.compiler.inputs.TermPath;
import souther.compiler.numeric.Count;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.numeric.Towards;
import souther.compiler.observe.Incompleteness;
import souther.compiler.observe.RunSensitivity;
import souther.compiler.partition.Border;
import souther.compiler.partition.BorderQuantity;
import souther.compiler.partition.BoundaryTarget;
import souther.compiler.partition.ClosureGap;
import souther.compiler.partition.Level;
import souther.compiler.partition.LineFacts;
import souther.compiler.partition.LineOrigin;
import souther.compiler.partition.ReadingGap;
import souther.compiler.types.SourceConstruct;
import souther.compiler.types.SourceConstructOrigin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every way a measurement is left weaker than it looks, and what each says about a wider run.
 *
 * <p>The seal refuses an arm nobody answered for: {@link Weakening#runSensitivity} is abstract, so
 * a twelfth arm does not compile until somebody writes an answer. What it cannot refuse is an arm
 * answering for itself where something further in already knows — which is the reconstruction this
 * whole type exists to stop, and it would be as invisible here as it was there.
 *
 * <p>So the rows say which arms decide and which pass the question on. An arm that holds what
 * stopped it must ask that thing, and only an arm that is the first place the fact exists may
 * answer. A row moved from {@code asks} to {@code answers} is somebody deciding to hold a second
 * copy of a fact, and this is where they have to write it down.
 */
class WhatEachWeakeningSaysAboutAWiderRunTest {

    /**
     * Every arm, and what it comes to.
     *
     * <p>Written as {@code answers|asks <what>/sensitivity}: whether the arm is the first place the
     * fact exists or hands the question further in, and what the arm below then says.
     */
    private static Map<String, String> theWeakenings() {
        Map<String, String> table = new LinkedHashMap<>();
        // The four that hold what stopped them. Each is the reason this type is a sum rather than a
        // code and a subject: what the reader that found it produced travels whole, so the question
        // can be put to it.
        table.put("ObservationIncomplete", "asks the code/MAY_CHANGE");
        table.put("BorderValueUnreadable", "asks the reading/MAY_CHANGE");
        table.put("ModelReadingIncomplete", "asks the gap/UNAFFECTED");
        // And the seven where there is nothing further in to ask. Six of them are this compiler
        // meeting something it has no reading for; one is a figure of its own.
        table.put("OutputCasesUnreadable", "answers/UNAFFECTED");
        table.put("InputCasesUnreadable", "answers/UNAFFECTED");
        table.put("BodiesNotElaborated", "answers/UNAFFECTED");
        table.put("BoundaryNotDerived", "answers/UNAFFECTED");
        table.put("InputNotRead", "answers/UNAFFECTED");
        table.put("ProofContradicted", "answers/UNAFFECTED");
        table.put("ArmsUnsettled", "answers/UNAFFECTED");
        // The one arm that is a figure the query graph hands the analysis.
        table.put("PairSpaceTruncated", "answers/MAY_CHANGE");
        return table;
    }

    /** The table, held against what the arms answer, with the population read off the seal. */
    @Test
    void everyWeakeningSaysWhetherAWiderRunCouldAnswerIt() {
        Map<String, String> said = new LinkedHashMap<>();
        for (Weakening each : everyWeakening()) {
            said.put(each.getClass().getSimpleName(),
                    asks(each) + "/" + each.runSensitivity().name());
        }

        assertEquals(theWeakenings(), said);
    }

    /** Every member of the seal has a row, read from the seal rather than from the list. */
    @Test
    void everyWeakeningThereIsHasARowAbove() {
        Set<String> sealed = new LinkedHashSet<>();
        for (Class<?> each : Weakening.class.getPermittedSubclasses()) {
            sealed.add(each.getSimpleName());
        }
        Set<String> listed = new LinkedHashSet<>();
        for (Weakening each : everyWeakening()) {
            listed.add(each.getClass().getSimpleName());
        }

        assertEquals(sealed, listed, "a weakening no row above answers for");
    }

    /**
     * An arm that asks says what it was asked, and not something of its own.
     *
     * <p>What the rows above call {@code asks} is checked here rather than described. Each of the
     * three is made twice over the two answers the thing it holds can give, and has to come back
     * with each — an arm that quietly decided for itself would agree with one of them and not the
     * other.
     */
    @Test
    void anArmThatHoldsWhatStoppedItSaysWhatThatSays() {
        for (RunSensitivity each : RunSensitivity.values()) {
            Incompleteness.Code code = each == RunSensitivity.MAY_CHANGE
                    ? Incompleteness.Code.VALUE_TRUNCATED : Incompleteness.Code.VALUE_UNREADABLE;

            assertEquals(each, Weakening.ObservationIncomplete.of(Incompleteness.of(
                    code, Incompleteness.Scope.BEHAVIOR, "b")).runSensitivity());
            assertEquals(each, new Weakening.BorderValueUnreadable(
                    border(), ReadingGap.of(code)).runSensitivity());
            assertEquals(each, new Weakening.ModelReadingIncomplete(
                    new ClosureGap.PositionNotReachedInto("b", new PositionId(TermPath.of("x")),
                            each == RunSensitivity.MAY_CHANGE
                                    ? new BlockReason.ValueRulesNotReachedPastDepthLimit()
                                    : new BlockReason.ValueRulesNotReached())).runSensitivity());
        }
    }

    /**
     * The one gap that answers for itself is right to, and this is what says so.
     *
     * <p>{@link ClosureGap.RulesNotReached} holds no reason: what a document says about a position
     * whose rules nothing enumerated is the hole, and which of the ways it was is kept inside
     * ({@link RulesLeftUnread}). So it answers {@code UNAFFECTED} from what can reach it, and a
     * third way of leaving rules unread that a wider run does get past would make that wrong with
     * nothing in the file to say so.
     *
     * <p>Asked of the type and not of a list: an arm added to {@code RulesLeftUnread} arrives here
     * whether or not anybody remembered this check.
     */
    @Test
    void nothingThatLeavesARulesUnreadIsSomethingAWiderRunGetsPast() {
        for (RulesLeftUnread each : everyWayRulesAreLeftUnread()) {
            assertEquals(RunSensitivity.UNAFFECTED, sensitivityOf(each),
                    () -> each + " reaches a `RulesNotReached`, which answers UNAFFECTED for all"
                            + " of them");
        }
    }

    /**
     * What a way of leaving rules unread says about a wider run.
     *
     * <p>Read off what it is rather than off a field, because {@link RulesLeftUnread} carries no
     * such answer and should not: it is a fact about how this compiler traverses a model, and the
     * one question asked of it here is whether any of its arms is a figure. None is — a clause this
     * reading lost, and a handing over nobody took over.
     */
    private static RunSensitivity sensitivityOf(RulesLeftUnread why) {
        return switch (why) {
            case RulesLeftUnread.ClauseOfThisReadingWasUnread _ -> RunSensitivity.UNAFFECTED;
            case RulesLeftUnread.Handoff handoff -> switch (handoff.why()) {
                case RulesLeftUnread.HandoffUnread.FromBlockedDescent _,
                     RulesLeftUnread.HandoffUnread.NotFullyAccepted _ ->
                        RunSensitivity.UNAFFECTED;
            };
        };
    }

    /** One of each way rules are left unread, which is what a `RulesNotReached` is written from. */
    private static List<RulesLeftUnread> everyWayRulesAreLeftUnread() {
        return List.of(new RulesLeftUnread.ClauseOfThisReadingWasUnread(),
                new RulesLeftUnread.Handoff(
                        new RulesLeftUnread.HandoffUnread.FromBlockedDescent()),
                new RulesLeftUnread.Handoff(
                        new RulesLeftUnread.HandoffUnread.NotFullyAccepted()));
    }

    /** Whether the arm holds something to put the question to, which is what the rows record. */
    private static String asks(Weakening each) {
        return switch (each) {
            case Weakening.ObservationIncomplete _ -> "asks the code";
            case Weakening.BorderValueUnreadable _ -> "asks the reading";
            case Weakening.ModelReadingIncomplete _ -> "asks the gap";
            case Weakening.OutputCasesUnreadable _, Weakening.InputCasesUnreadable _,
                 Weakening.BodiesNotElaborated _, Weakening.BoundaryNotDerived _,
                 Weakening.InputNotRead _, Weakening.PairSpaceTruncated _,
                 Weakening.ProofContradicted _, Weakening.ArmsUnsettled _ -> "answers";
        };
    }

    /**
     * One of each arm, made with whatever it takes.
     *
     * <p>The three that ask are made with a value that answers {@code MAY_CHANGE} where one can, so
     * the rows show the answer travelling rather than a constant. That each of them really does
     * travel both ways is {@link #anArmThatHoldsWhatStoppedItSaysWhatThatSays}'s.
     */
    private static List<Weakening> everyWeakening() {
        List<Weakening> out = new ArrayList<>();
        out.add(Weakening.ObservationIncomplete.of(Incompleteness.of(
                Incompleteness.Code.VALUE_TRUNCATED, Incompleteness.Scope.BEHAVIOR, "b")));
        out.add(new Weakening.BorderValueUnreadable(border(),
                ReadingGap.of(Incompleteness.Code.VALUE_TRUNCATED)));
        out.add(new Weakening.ModelReadingIncomplete(new ClosureGap.RulesNotReached(
                "b", new PositionId(TermPath.of("x")))));
        out.add(new Weakening.OutputCasesUnreadable("b"));
        out.add(new Weakening.InputCasesUnreadable("b", 0));
        out.add(new Weakening.BodiesNotElaborated("m"));
        out.add(new Weakening.BoundaryNotDerived("b"));
        out.add(new Weakening.InputNotRead("b"));
        out.add(new Weakening.ProofContradicted(new CoverageSites.Obligation("b",
                new SourceConstructOrigin(new WrittenOwner.Body("m", "b"), 2, 0,
                        SourceConstruct.IF),
                1, new DecidedBy.NotSaid())));
        out.add(new Weakening.ArmsUnsettled(
                new SourceConstructOrigin(new WrittenOwner.Body("m", "b"), 1, 0,
                        SourceConstruct.IF)));
        out.add(new Weakening.PairSpaceTruncated("b", 9, 4));
        return out;
    }

    /** One border, since which border it is is no part of what this is about. */
    private static Border border() {
        Carrier carrier = new Carrier.Whole();
        NumericTerm.ValueOf value = new NumericTerm.ValueOf(TermPath.of("x"));
        BoundaryTarget target = BoundaryTarget.at(
                new BorderQuantity.OfACoordinate("cap", value,
                        TermOrdersFixtures.itself(value, carrier)),
                new Level.OnACarrier(carrier, Count.of(100)));
        LineOrigin origin = new LineOrigin.EnsuresOrigin(
                new souther.compiler.partition.WhichLine.OfAComparisonOfAPart(
                        new souther.compiler.partition.ClauseStatementId(
                                new souther.compiler.check.PartId<>(new RuleRef.Ensures(
                                        new BehaviorContract.RuleId(null, 0, 0, null), "cap"), 0),
                                0)),
                new LineFacts(new ComparisonClaim.Cut(Towards.BELOW, true)));
        return Border.at(target, origin, new NumericDomain.Bounds(null, null));
    }
}
