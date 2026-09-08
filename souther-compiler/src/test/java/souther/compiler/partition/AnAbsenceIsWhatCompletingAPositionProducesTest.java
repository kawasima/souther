package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.Clause;
import souther.compiler.check.RuleCitation;
import souther.compiler.check.RuleRef;
import souther.compiler.diag.EveryShippedMessageCatalogIsCompleteAndValidTest;
import souther.compiler.inputs.BlockReason;
import souther.compiler.inputs.InputQuestion;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.RuleReasons;
import souther.compiler.inputs.StandingQuestion;
import souther.compiler.inputs.StructuralInspection;
import souther.compiler.inputs.WhatAQuestionStandsOn;
import souther.compiler.inputs.TermPath;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "The model divides this position no way" is the end of a chain, not a value anything can write.
 *
 * <p>It was a value anything could write, and everything that could not answer wrote it: a walk
 * that fell out of the bottom of a chain of conditions, a reader that stopped at a name, a rule
 * nothing could turn into a line. What all of those have in common is that they say something about
 * this compiler, and the sentence they produced says something about the model.
 *
 * <p>So the chain is the type. A position is read; where the producers of local evidence came back
 * with nothing it becomes a {@link PendingPosition}; and completing one of those with what the
 * rules came to is what produces the absence. It is the one arm where nothing stopped and nothing
 * was found.
 *
 * <p>What is held here is that arm and its neighbours. That nothing outside this package can start
 * the chain halfway through is the visibility of {@link PendingPosition}, which no test in this
 * package can show — the scan below is a tripwire over where the one absence is named, not a proof
 * that it cannot be reached another way.
 */
class AnAbsenceIsWhatCompletingAPositionProducesTest {

    private static final TermPath AT = TermPath.of("x");

    private static final AxisId ID = AxisId.of("run", new NumericTerm.ValueOf(AT));

    /** What the rules came to where every one of them was read and none drew a line. The chain
     *  below is about what a position is pending on, which is asked before this is read. */
    private static final BodyCutInspection READ_TO_THE_END = new BodyCutInspection.Exhausted();

    /** A rule filed at the position, read from end to end, that came to no line. */
    private static final BodyCutInspection READ_TO_THE_END_AND_NO_LINE =
            new BodyCutInspection.NoLine(false, true);

    /** And one a reading did not get through, which is a different thing about the same position. */
    private static final BodyCutInspection A_READING_STOPPED =
            new BodyCutInspection.NoLine(true, false);

    private static PositionMeasurements measured() {
        return at(new Axis(ID, new NumericTerm.ValueOf(AT),
                List.of(PartitionClass.of("true", "true", new Recognition.Nothing(),
                                RepresentativeSource.of(FixtureTemplate.bool(true)))
                        .ofTheNumber(new NumericTerm.ValueOf(AT))),
                List.of()));
    }

    /** One position with nothing left to answer for, measured at the one number. Its declarations
     *  answered for it, so there is no fallback for it to be waiting on. */
    private static PositionMeasurements at(Axis axis) {
        return new PositionMeasurements(
                new PositionAccount("run", AT, Type.BOOL, ReadingResidue.NOTHING, souther.compiler.values.ValueSet.ANY, null, List.of(), List.of()),
                List.of(axis), READ_TO_THE_END);
    }

    /** What is still to be answered for at this position. */
    private static PendingPosition of(PositionMeasurements at) {
        return PendingPosition.of(at.position(), at.hasMeasures());
    }

    private static PositionMeasurements pending(StructuralInspection.Continuation found) {
        return pending(found, List.of());
    }

    /** The same, with the questions the rules of the position raise that nothing answered — which
     *  is the other way a position is left unable to reach an absence. */
    private static PositionMeasurements pending(StructuralInspection.Continuation found,
                                                List<StandingQuestion> standing) {
        return new PositionMeasurements(
                new PositionAccount("run", AT, Type.BOOL, ReadingResidue.NOTHING, souther.compiler.values.ValueSet.ANY, found, standing, List.of()),
                List.of(), READ_TO_THE_END);
    }

    /** A question of a rule of this position that nothing answered. */
    private static StandingQuestion standing() {
        return StandingQuestion.Exact.of(
                new RuleCitation.Named(new RuleRef.Invariant(new Clause.Ref(
                        new Clause.Id(TypeSymbols.declared(new TypeKey("probe", "N")), 0),
                        java.util.Optional.empty()))),
                new InputQuestion.AboutAPosition(AT),
                new WhatAQuestionStandsOn(RuleReasons.one(new BlockReason.UnreadValueRule()),
                        Optional.empty()));
    }

    /**
     * A leaf whose rules leave a question standing cannot reach an absence.
     *
     * <p>The other way a position is left unable to say the model divides it no way. Nothing under
     * it stopped the walk — it is a leaf — and a rule of it raises a question nothing answered, so
     * what a body says next decides whether it is measured, and never that the model states nothing
     * here.
     */
    @Test
    void aLeafWithAQuestionStandingCompletesAsThatQuestion() {
        UndividedPosition said =
                of(pending(new StructuralInspection.Continuation.None(), List.of(standing())))
                        .complete(new BodyCutInspection.Exhausted());

        assertEquals(new UndividedPosition.Why.CannotDerive(), said.why());
        // And the verdict says only that: what stands is the rule's, said by the accounting that
        // holds the question and naming which rule raised it.
        assertNull(of(pending(new StructuralInspection.Continuation.None(), List.of(standing())))
                        .reportable(),
                "a question a rule raises is not a position nothing was reached at");
    }

    /**
     * And a leaf every question of which was answered says the other thing where a rule is filed at
     * it.
     *
     * <p>Not an absence: the model states something at this position. Not a derivation this
     * compiler could not make either — a question stands only where no reading took the rule that
     * raised it in, so a reading short of a rule another one read leaves nothing standing. Read off
     * one reading's own completeness, every consumer of this chain went on saying the first of
     * those about the second.
     */
    @Test
    void aLeafWhoseQuestionsWereAllAnsweredSaysNeitherOfThose() {
        UndividedPosition said = of(pending(new StructuralInspection.Continuation.None()))
                .complete(READ_TO_THE_END_AND_NO_LINE);

        assertFalse(said.why() instanceof UndividedPosition.Why.Absent, said.toString());
        assertInstanceOf(UndividedPosition.Why.StatedWithoutALine.class, said.why(),
                said.toString());
    }

    /**
     * The position carries no account of its own of why anything stands.
     *
     * <p>There used to be one line here and a precedence deciding which of two reasons it carried —
     * which is the shape of it: an author was told one limit and never learnt that the other rule
     * existed. What the position itself has to say is that nothing was established, and no more.
     */
    @Test
    void thePositionHoldsNoAccountOfItsOwn() {
        PendingPosition pending = of(pending(new StructuralInspection.Continuation.None(),
                List.of(standing())));

        assertFalse(pending.complete(READ_TO_THE_END_AND_NO_LINE).why()
                instanceof UndividedPosition.Why.Absent);
        assertNull(pending.reportable(), "each question is said with its rule, not as this position");
    }

    // --- what can be pending at all -------------------------------------------------------------

    /** A position with evidence is not pending anything, so there is nothing to complete and no way
     *  to an absence from it. */
    @Test
    void aPositionWithEvidenceIsNotPending() {
        assertNull(of(measured()));
    }

    /** And a position with no evidence that nothing read is not answered for at all: what would be
     *  said of it is this compiler's state, written down as what the model divides. */
    @Test
    void aPositionNothingReadIsNotAnsweredFor() {
        assertThrows(IllegalStateException.class, () -> of(new PositionMeasurements(
                new PositionAccount("run", AT, Type.BOOL, ReadingResidue.NOTHING, souther.compiler.values.ValueSet.ANY, null, List.of(), List.of()),
                List.of(), READ_TO_THE_END)));
    }

    @Test
    void aPositionWithoutEvidenceIsPendingWhatItsStructureFound() {
        assertEquals(new PendingPosition.Leaf(AT),
                of(pending(new StructuralInspection.Continuation.None())));
        assertEquals(new PendingPosition.Blocked(AT, new BlockReason.TypeUnresolved()),
                of(pending(
                        new StructuralInspection.Continuation.Blocked(new BlockReason.TypeUnresolved()))));
    }

    // --- and what completing one comes to -------------------------------------------------------

    /** The one arm that reaches an absence: nothing under the position, and every rule about it
     *  read and drawing nothing. */
    @Test
    void aLeafWhoseRulesWereAllReadAndDrewNothingIsAnAbsence() {
        UndividedPosition done = new PendingPosition.Leaf(AT)
                .complete(new BodyCutInspection.Exhausted());

        assertTrue(done.why() instanceof UndividedPosition.Why.Absent);
        assertEquals(AT, done.at());
    }

    /**
     * A rule the body wrote that came to no line says the model states something here.
     *
     * <p>The phase a rule was written in is no part of what it says, and neither is how far a
     * reading of it got. This used to carry which of those it was, and a verdict read a
     * {@code guard} this compiler understood completely as a position nothing had looked at.
     */
    @Test
    void aLeafWhoseBodyRuleCameToNoLineStatesSomething() {
        UndividedPosition done = new PendingPosition.Leaf(AT)
                .complete(READ_TO_THE_END_AND_NO_LINE);

        assertFalse(done.why() instanceof UndividedPosition.Why.Absent);
        assertEquals(new UndividedPosition.Why.StatedWithoutALine(), done.why());
    }

    /**
     * A rule filed here that a reading did not get through is the same news as a question standing.
     *
     * <p>The half an accounting cannot answer. A question is raised by a rule of a declaration and
     * answered by whichever reading took it in; a comparison a body writes raises and answers in
     * one breath, so a reading that stopped on one leaves no question anywhere. Read as "a rule is
     * filed here", such a rule came out as the model stating something, over a reading that never
     * found out what it says.
     */
    @Test
    void aLeafWhoseBodyRuleAReadingStoppedOnIsUnderivable() {
        UndividedPosition done = new PendingPosition.Leaf(AT).complete(A_READING_STOPPED);

        assertEquals(new UndividedPosition.Why.CannotDerive(), done.why());
    }

    /** And a stop beside a rule read to the end is still a stop, whichever was written first. */
    @Test
    void aStopBesideARuleReadToTheEndIsStillAStop() {
        assertEquals(new UndividedPosition.Why.CannotDerive(),
                new PendingPosition.Leaf(AT)
                        .complete(new BodyCutInspection.NoLine(true, true)).why());
        assertEquals(new UndividedPosition.Why.CannotDerive(),
                new PendingPosition.Leaf(AT).complete(BodyCutInspection.combined(
                        READ_TO_THE_END_AND_NO_LINE, A_READING_STOPPED)).why());
        assertEquals(new UndividedPosition.Why.CannotDerive(),
                new PendingPosition.Leaf(AT).complete(BodyCutInspection.combined(
                        A_READING_STOPPED, READ_TO_THE_END_AND_NO_LINE)).why());
    }

    /**
     * A question standing at the position outranks a rule of the body that came to no line.
     *
     * <p>Both are true of such a position and the verdict is one. A rule is filed here, so the
     * model states something; a question of one of its rules is unanswered, so nothing follows from
     * there being no class — and the second is the one a reader can act on.
     */
    @Test
    void aQuestionStandingOutranksARuleTheBodyCameToNoLineOn() {
        UndividedPosition done = new PendingPosition.AQuestionStands(AT)
                .complete(READ_TO_THE_END_AND_NO_LINE);

        assertEquals(new UndividedPosition.Why.CannotDerive(), done.why());
    }

    /**
     * A walk that did not reach into the position outranks whatever the rules came to.
     *
     * <p>Both rows, because the precedence is only visible where the other side has something to
     * say: where the walk could not reach into what a position holds, a rule naming something
     * inside it describes the same stop from the other end, and the first is the cause.
     */
    @Test
    void aWalkThatDidNotReachInOutranksWhatTheRulesCameTo() {
        PendingPosition blocked = new PendingPosition.Blocked(AT,
                new BlockReason.UnsupportedTraversal(BlockReason.Traversal.MAPPING_CONTENT));
        UndividedPosition.Why expected = new UndividedPosition.Why.CannotDerive();

        assertEquals(expected, blocked.complete(new BodyCutInspection.Exhausted()).why());
        assertEquals(expected, blocked.complete(READ_TO_THE_END_AND_NO_LINE).why());
        // And the finding is the stop, whatever the rules came to: a rule naming something inside a
        // position the walk could not enter describes that same stop from the other end.
        assertEquals(new souther.compiler.inputs.PositionReadingBlocked(AT,
                        new BlockReason.UnsupportedTraversal(
                                BlockReason.Traversal.MAPPING_CONTENT)),
                blocked.reportable());
    }

    /** A line drawn at a position whose axis says it has none is two readings disagreeing, and
     *  neither of them is a thing to report about a model. */
    @Test
    void aLineDrawnAtAPositionWithNoEvidenceIsNotAnswered() {
        assertThrows(IllegalStateException.class, () -> new PendingPosition.Leaf(AT)
                .complete(new BodyCutInspection.Evidence()));
        assertThrows(IllegalStateException.class, () -> new PendingPosition
                .Blocked(AT, new BlockReason.RecursiveExpansion(
                        souther.compiler.types.TypeSymbols.declared(
                                new souther.compiler.types.TypeKey("g", "Chain")),
                        souther.compiler.inputs.TermPath.of("c")))
                .complete(new BodyCutInspection.Evidence()));
    }

    // --- and nothing else makes one -------------------------------------------------------------

    /**
     * Held over the sources, because what it says cannot be said in a test: outside this package
     * the absence has no constructor to call and no instance to name, and inside it the one
     * instance is reached through the completion.
     *
     * <p>A tripwire. Naming the field from a third place in this package defeats it, and that is
     * the line this fails on.
     */
    @Test
    void theAbsenceIsNamedOnlyWhereItIsProduced() throws IOException {
        List<Path> sources = EveryShippedMessageCatalogIsCompleteAndValidTest.mainSources();
        assertFalse(sources.isEmpty(), "found no sources at all — the scan missed the tree");

        Set<String> naming = new TreeSet<>();
        Set<String> producing = new TreeSet<>();
        for (Path source : sources) {
            String text = Files.readString(source, StandardCharsets.UTF_8);
            if (text.contains("Absent.PROVEN")) {
                naming.add(source.getFileName().toString());
            }
            if (text.contains("absentAfter(")) {
                producing.add(source.getFileName().toString());
            }
        }

        assertEquals(Set.of("UndividedPosition.java"), naming,
                "the one absence is named where it is declared");
        assertEquals(Set.of("UndividedPosition.java", "PendingPosition.java"), producing,
                "and is produced by completing a position, which is what makes it a conclusion");
    }
}
