package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.numeric.OrderedIntervals;
import souther.compiler.values.AdmittedPlan;
import souther.compiler.values.Allowance;
import souther.compiler.values.AsACompilationAllows;
import souther.compiler.values.Lacks;
import souther.compiler.values.PlannedValues;
import souther.compiler.values.Refusal;
import souther.compiler.values.RelationalLack;
import souther.compiler.values.Sameness;
import souther.compiler.values.Value;
import souther.compiler.values.ValueSet;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A state left with no value at more than one block, reported at one of them.
 *
 * <p>Each block says that the positions in it are one value and that value has none. Two of them
 * are two lacks: a sentence over both would say all of their positions are one value, which no rule
 * of the model states. So one is named, and which one is settled by the order the value declares
 * its positions rather than by the order the reading met them.
 *
 * <p><b>Asked of the proof and not of a state built to carry it.</b> Which proof a walk of a state
 * reaches is that walk's business and moves as this compiler learns to show more; how a proof is
 * named is about what a proof can say, and a {@link Refusal} carrying two blocks is a value the
 * refusals of two clauses conjoined come to. Asked through a state, these would need one built out
 * of parts nothing read — which is the shape a reading of values is no longer come by, and a test
 * that wrote one would be fixing the naming to whatever a walk happens to reach today.
 *
 * <p>What a walk does reach is asked once, at the end, through the reading a declaration's clauses
 * are worked out into.
 */
class AProofNamesOneBlockAndNotAllOfThemTest {

    private static final Term.Interner NAMES = new Term.Interner();
    private static final FactSubject P = FactSubject.of(NAMES.written("p"));
    private static final FactSubject Q = FactSubject.of(NAMES.written("q"));
    private static final FactSubject R = FactSubject.of(NAMES.written("r"));
    private static final FactSubject S = FactSubject.of(NAMES.written("s"));

    private static final Value A = Value.text("A");
    private static final Value B = Value.text("B");

    /** Two positions held as one and stated to admit values that share none. */
    private static PlannedValues<FactSubject> emptiedAt(FactSubject one, FactSubject other) {
        return PlannedValues.<FactSubject>holdingAsOne(one, other)
                .meet(says(one, A))
                .meet(says(other, B));
    }

    /** One rule about one position. */
    private static PlannedValues<FactSubject> says(FactSubject atom, Value value) {
        return PlannedValues.at(atom, AdmittedPlan.of(ValueSet.just(value)));
    }

    /**
     * A description worked out, which is how a confinement whose values are in hand is come by.
     *
     * <p>The clauses are conjoined while they are still descriptions and worked out once, which is
     * what a declaration's reading does. Worked out one at a time and met afterwards, the fixture
     * would be paying for a machine per clause and asking a question this compiler never asks.
     */
    private static Confinement.Worked<FactSubject> worked(PlannedValues<FactSubject> planned,
                                                          Allowance<FactSubject> sets) {
        return new Confinement.Planned<>(planned, OrderedIntervals.top(), Map.of()).resolve(sets);
    }

    /**
     * A block left with no value, said the way a reading of a clause says it.
     *
     * <p>What a proof of this kind is: the rules hold these positions as one value, and that value
     * has none.
     */
    private static Confinement.Admission<FactSubject> emptyAt(
            Set<Sameness.Block<FactSubject>> blocks) {
        return Confinement.Admission.eachOf(souther.compiler.values.Emptiness.EMPTY,
                Confinement.EmptyBy.POSITIONS_HELD_AS_ONE, blocks,
                Confinement.Shown.BY_THE_READINGS);
    }

    /** The block those positions are one value in. */
    private static Sameness.Block<FactSubject> block(FactSubject one, FactSubject other) {
        return Sameness.of(one, other).blockOf(one);
    }

    /** What a proof comes to in a value declaring these positions, which is the question. */
    private static Emptiness named(Confinement.Admission<FactSubject> shown,
                                   SequencedMap<FactSubject, Emptiness.AtAField.Where> positions) {
        return ProofOfEmptiness.named(shown, positions, new Emptiness.ConflictingRules())
                .orElseThrow();
    }

    /** Where the value declares each of its positions, in the order it declares them. */
    private static SequencedMap<FactSubject, Emptiness.AtAField.Where> declared() {
        SequencedMap<FactSubject, Emptiness.AtAField.Where> out = new LinkedHashMap<>();
        out.put(P, new Emptiness.AtAField.Where.In("p"));
        out.put(Q, new Emptiness.AtAField.Where.In("q"));
        out.put(R, new Emptiness.AtAField.Where.In("r"));
        out.put(S, new Emptiness.AtAField.Where.In("s"));
        return out;
    }

    /** One block left with no value is named, and it is named as the places together. */
    @Test
    void oneBlockLeftWithNoValueIsNamedAsThePlacesTogether() {
        Allowance<FactSubject> sets = AsACompilationAllows.forAdmittedValues();
        ConstraintState<FactSubject> state = ConstraintState.<FactSubject>top()
                .takingRead(worked(emptiedAt(P, Q), sets), sets);

        Optional<Emptiness> why = state.holdsNothing(declared());

        Emptiness.AtEqualPositions at =
                assertInstanceOf(Emptiness.AtEqualPositions.class, why.orElseThrow());
        assertEquals(2, at.where().size());
        assertInstanceOf(Emptiness.NoCommonValueForEqualPositions.class, at.under());
    }

    /**
     * Two of them are still one sentence, about the block the value declares first.
     *
     * <p>What must not happen is the union: {@code p}, {@code q}, {@code r} and {@code s} named
     * together says the rules hold all four as one value, and nothing here does.
     */
    @Test
    void twoBlocksLeftWithNoValueAreNamedOneAtATime() {
        Emptiness.AtEqualPositions at = assertInstanceOf(Emptiness.AtEqualPositions.class,
                named(emptyAt(Set.of(block(R, S), block(P, Q))), declared()));

        assertEquals(2, at.where().size(), "one block, not the four positions of both");
        assertEquals(new Emptiness.AtAField.Where.In("p"), at.where().getFirst(),
                "and the one whose places the value declares first, whichever was shown first");
    }

    /**
     * And the sentence is the same whichever order the blocks and their positions were reached in.
     *
     * <p>Which is what the naming being the declaration's own comes to. A block is a set and holds
     * its positions in no order; a refusal holds its blocks in the order the readings were met. So
     * the same proof arrives written several ways, and every one of them names the same places in
     * the same order — the value's.
     *
     * <p>Asked over the arrangements rather than at one of them. The line above says "whichever was
     * shown first" and one arrangement cannot say it, since either of them passes a naming that
     * reads whatever it was handed.
     */
    @Test
    void andOneProofIsOneSentenceHoweverItWasReached() {
        List<Emptiness> said = List.of(
                named(emptyAt(ordered(List.of(block(P, Q), block(R, S)))), declared()),
                named(emptyAt(ordered(List.of(block(R, S), block(P, Q)))), declared()),
                named(emptyAt(ordered(List.of(block(Q, P), block(S, R)))), declared()),
                named(emptyAt(ordered(List.of(block(S, R), block(Q, P)))), declared()));

        said.forEach(each -> assertEquals(said.getFirst(), each,
                "one proof of one model is one sentence"));
        assertEquals(List.of(new Emptiness.AtAField.Where.In("p"),
                        new Emptiness.AtAField.Where.In("q")),
                assertInstanceOf(Emptiness.AtEqualPositions.class, said.getFirst()).where(),
                "and it names the places where the value declares them");
    }

    /** These blocks, in the order they are given, which is what a refusal met the other way round
     *  would not have. */
    private static Set<Sameness.Block<FactSubject>> ordered(
            List<Sameness.Block<FactSubject>> these) {
        return new LinkedHashSet<>(these);
    }

    /**
     * A state refused both ways is reported at the block that holds nothing.
     *
     * <p>One side leaves the value {@code p} and {@code q} are no value at all; the other states
     * {@code r} and {@code s} to be one value and to differ, which is a lack about them together
     * while each of them is left everything on its own. A conjunction of the two is refused both
     * ways, and neither is what it is instead of the other.
     *
     * <p>What the sentence about a place is about is the first of those. Read off both, a proof
     * could name {@code r} — a block whose own rules leave it everything — and which of the two it
     * named would be settled by which the value declares first, which is why this declares the
     * other pair first.
     */
    @Test
    void aStateRefusedBothWaysIsReportedAtTheBlockThatHoldsNothing() {
        // A conjunction keeps what either side showed, which is how one proof comes to carry both.
        Refusal<FactSubject> shown = Refusal.eitherShown(
                Refusal.atEachOf(Set.of(block(P, Q))),
                Refusal.ofThemTogether(Lacks.of(
                        new RelationalLack.ABlockApartFromItself<>(block(R, S)))));
        Confinement.Admission<FactSubject> both = new Confinement.Admission<>(
                souther.compiler.values.Emptiness.EMPTY,
                Confinement.EmptyBy.POSITIONS_HELD_AS_ONE, shown,
                Confinement.Shown.BY_THE_READINGS);

        assertEquals(Set.of(block(P, Q)), shown.atEachOf(),
                "one side leaves this block nothing");
        assertTrue(!shown.together().isEmpty(), "and the other refuses two together");

        SequencedMap<FactSubject, Emptiness.AtAField.Where> declared = new LinkedHashMap<>();
        declared.put(R, new Emptiness.AtAField.Where.In("r"));
        declared.put(S, new Emptiness.AtAField.Where.In("s"));
        declared.put(P, new Emptiness.AtAField.Where.In("p"));
        declared.put(Q, new Emptiness.AtAField.Where.In("q"));

        Emptiness.AtEqualPositions at = assertInstanceOf(Emptiness.AtEqualPositions.class,
                named(both, declared));
        assertEquals(List.of(new Emptiness.AtAField.Where.In("p"),
                        new Emptiness.AtAField.Where.In("q")), at.where(),
                "the places whose one value has none, and not the ones declared first");
        assertInstanceOf(Emptiness.NoCommonValueForEqualPositions.class, at.under());
    }

    /**
     * Two blocks beginning at one position are told apart by the places after it.
     *
     * <p>What is carried is one witness per way the rules were shown empty, so two of them may
     * overlap — and both of these begin at {@code p}. A reader that chose by the first declared
     * position they hold would pick whichever a set happened to iterate to, which is an order
     * salted per run of the machine: one model would be refused two ways.
     */
    @Test
    void twoBlocksBeginningAtOnePositionAreToldApartByThePlacesAfterIt() {
        for (boolean reversed : new boolean[] {false, true}) {
            Refusal<FactSubject> one = Refusal.atEachOf(Set.of(block(P, R)));
            Refusal<FactSubject> other = Refusal.atEachOf(Set.of(block(P, Q)));
            Confinement.Admission<FactSubject> both = new Confinement.Admission<>(
                    souther.compiler.values.Emptiness.EMPTY,
                    Confinement.EmptyBy.POSITIONS_HELD_AS_ONE,
                    reversed ? Refusal.eitherShown(other, one) : Refusal.eitherShown(one, other),
                    Confinement.Shown.BY_THE_READINGS);
            assertEquals(2, both.site().blocks().size(),
                    "or the two witnesses are not both here");

            Emptiness.AtEqualPositions at = assertInstanceOf(Emptiness.AtEqualPositions.class,
                    named(both, declared()));
            assertEquals(List.of(new Emptiness.AtAField.Where.In("p"),
                            new Emptiness.AtAField.Where.In("q")), at.where(),
                    "p with q is declared before p with r, shown either way round");
        }
    }

    /**
     * Two branches left with no value at blocks that are not the same block have shown nothing
     * about the positions those blocks share.
     *
     * <p>The counterexample the proof is carried as blocks for. Read as the positions each of them
     * named, the two would meet at {@code p} and {@code q}, and the choice would be refused for a
     * pair neither branch says has no value.
     */
    @Test
    void twoBranchesShownAtOverlappingBlocksShowNothingAboutWhatTheyShare() {
        Confinement.Admission<FactSubject> one = new Confinement.Admission<>(
                souther.compiler.values.Emptiness.EMPTY,
                Confinement.EmptyBy.POSITIONS_HELD_AS_ONE,
                souther.compiler.values.Refusal.atEachOf(
                        Set.of(souther.compiler.values.Sameness.of(P, Q).joining(Q, R).blockOf(P))),
                Confinement.Shown.BY_THE_READINGS);
        Confinement.Admission<FactSubject> other = new Confinement.Admission<>(
                souther.compiler.values.Emptiness.EMPTY,
                Confinement.EmptyBy.POSITIONS_HELD_AS_ONE,
                souther.compiler.values.Refusal.atEachOf(
                        Set.of(souther.compiler.values.Sameness.of(P, Q).joining(Q, S).blockOf(P))),
                Confinement.Shown.BY_THE_READINGS);

        assertTrue(Confinement.Admission.bothShown(one, other).site().atEachOf().isEmpty(),
                "neither branch says the value p and q share has none");
    }

    /** And a block whose places this value does not declare is one no sentence can be written
     *  about, so what is carried is the general proof. */
    @Test
    void aBlockOfPositionsThisValueDoesNotDeclareIsNotNamed() {
        Allowance<FactSubject> sets = AsACompilationAllows.forAdmittedValues();
        ConstraintState<FactSubject> state = ConstraintState.<FactSubject>top()
                .takingRead(worked(emptiedAt(P, Q), sets), sets);

        SequencedMap<FactSubject, Emptiness.AtAField.Where> elsewhere = new LinkedHashMap<>();
        elsewhere.put(R, new Emptiness.AtAField.Where.In("r"));

        assertTrue(state.isBottom());
        assertInstanceOf(Emptiness.ConflictingRules.class,
                state.holdsNothing(elsewhere).orElseThrow());
    }
}
