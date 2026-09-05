package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.numeric.OrderedIntervals;
import souther.compiler.values.AdmissibleValues;
import souther.compiler.values.Allowance;
import souther.compiler.values.AsACompilationAllows;
import souther.compiler.values.Value;
import souther.compiler.values.ValueSet;

import java.util.LinkedHashMap;
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
 * <p>Made here rather than from a declaration. A branch is settled by the first thing that shows it
 * empty and the proof is fixed there, so a clause never reaches the second block — the state that
 * holds both is one a caller builds by conjoining two readings that are each already empty, which
 * is what {@link ConstraintState} has to answer about however it arrives.
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
    private static AdmissibleValues<FactSubject> emptiedAt(FactSubject one, FactSubject other,
                                                           Allowance<FactSubject> sets) {
        return AdmissibleValues.<FactSubject>holdingAsOne(one, other)
                .meet(AdmissibleValues.at(one, ValueSet.just(A)), sets)
                .meet(AdmissibleValues.at(other, ValueSet.just(B)), sets);
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
                .takingRead(Confinement.Worked.of(emptiedAt(P, Q, sets), OrderedIntervals.top(),
                        Map.of()), sets);

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
        Allowance<FactSubject> sets = AsACompilationAllows.forAdmittedValues();
        AdmissibleValues<FactSubject> both =
                emptiedAt(R, S, sets).meet(emptiedAt(P, Q, sets), sets);
        assertEquals(2, both.emptiedBlocks().size(), "or this measures one block twice");

        ConstraintState<FactSubject> state = ConstraintState.<FactSubject>top()
                .takingRead(Confinement.Worked.of(both, OrderedIntervals.top(), Map.of()), sets);

        Emptiness.AtEqualPositions at = assertInstanceOf(Emptiness.AtEqualPositions.class,
                state.holdsNothing(declared()).orElseThrow());

        assertEquals(2, at.where().size(), "one block, not the four positions of both");
        assertEquals(new Emptiness.AtAField.Where.In("p"), at.where().getFirst(),
                "and the one whose places the value declares first, whichever was met first");
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
            Allowance<FactSubject> sets = AsACompilationAllows.forAdmittedValues();
            AdmissibleValues<FactSubject> one = emptiedAt(P, R, sets);
            AdmissibleValues<FactSubject> other = emptiedAt(P, Q, sets);
            AdmissibleValues<FactSubject> both = reversed
                    ? other.meet(one, sets) : one.meet(other, sets);
            assertEquals(2, both.emptiedBlocks().size(), "or the two witnesses are not both here");

            ConstraintState<FactSubject> state = ConstraintState.<FactSubject>top()
                    .takingRead(Confinement.Worked.of(both, OrderedIntervals.top(), Map.of()),
                            sets);

            Emptiness.AtEqualPositions at = assertInstanceOf(Emptiness.AtEqualPositions.class,
                    state.holdsNothing(declared()).orElseThrow());
            assertEquals(List.of(new Emptiness.AtAField.Where.In("p"),
                            new Emptiness.AtAField.Where.In("q")), at.where(),
                    "p with q is declared before p with r, met either way round");
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
                new souther.compiler.values.Refusal.AtEachOf<>(
                        Set.of(souther.compiler.values.Sameness.of(P, Q).joining(Q, R).blockOf(P))),
                Confinement.Shown.BY_THE_READINGS);
        Confinement.Admission<FactSubject> other = new Confinement.Admission<>(
                souther.compiler.values.Emptiness.EMPTY,
                Confinement.EmptyBy.POSITIONS_HELD_AS_ONE,
                new souther.compiler.values.Refusal.AtEachOf<>(
                        Set.of(souther.compiler.values.Sameness.of(P, Q).joining(Q, S).blockOf(P))),
                Confinement.Shown.BY_THE_READINGS);

        assertTrue(Confinement.Admission.bothShown(one, other).at().isEmpty(),
                "neither branch says the value p and q share has none");
    }

    /** And a block whose places this value does not declare is one no sentence can be written
     *  about, so what is carried is the general proof. */
    @Test
    void aBlockOfPositionsThisValueDoesNotDeclareIsNotNamed() {
        Allowance<FactSubject> sets = AsACompilationAllows.forAdmittedValues();
        ConstraintState<FactSubject> state = ConstraintState.<FactSubject>top()
                .takingRead(Confinement.Worked.of(emptiedAt(P, Q, sets), OrderedIntervals.top(),
                        Map.of()), sets);

        SequencedMap<FactSubject, Emptiness.AtAField.Where> elsewhere = new LinkedHashMap<>();
        elsewhere.put(R, new Emptiness.AtAField.Where.In("r"));

        assertTrue(state.isBottom());
        assertInstanceOf(Emptiness.ConflictingRules.class,
                state.holdsNothing(elsewhere).orElseThrow());
    }
}
