package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.inputs.RuleSite;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.types.SourceConstruct;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;
import souther.compiler.types.WrittenOwner;
import souther.compiler.values.UnreadReason;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a rule is answerable for is one fact per thing its author wrote, and not one per reading that
 * met it.
 *
 * <p>A clause is read once for every place the walk opens a value at, over whatever tree the
 * substitution built there, and a helper is copied into every call that reaches it. Told apart by
 * either, one thing an author wrote comes back as several things to look at, and how many depends on
 * how the declaration happens to be constructed and how often the helper is called.
 *
 * <p>The three claims are held together because each alone passes with the wrong answer. A site that
 * collapsed everything would satisfy the first; one that collapsed nothing would satisfy the second
 * and the third.
 */
class WhatARuleIsAnswerableForIsToldApartByThePartItsAuthorWroteTest {

    private static final Term.Interner NAMES = new Term.Interner();
    private static final FactSubject POSITION = FactSubject.of(NAMES.written("position"));
    private static final FactSubject BESIDE_IT = FactSubject.of(NAMES.written("beside"));

    private static final PartId<RuleRef.Invariant> FIRST = part(0);
    private static final PartId<RuleRef.Invariant> SECOND = part(1);

    /**
     * One choice copied into two calls is one fact, whichever copy met it.
     *
     * <p>The operator is written once, in the helper, and rewriting it there answers both calls —
     * so the copy is dropped where the reading is filed and the construct is what travels.
     */
    @Test
    void twoCopiesOfOneChoiceAreOneFact() {
        Set<RuleShortfall> filed = filed(
                met(new ClauseOccurrence(1), wrote(3)).of(FIRST),
                met(new ClauseOccurrence(4), wrote(3)).of(SECOND));

        assertEquals(1, filed.size(),
                "one operator an author wrote is one thing to look at, however many calls an"
                        + " expansion copied it into");
    }

    /** And two choices an author wrote in one part are two facts, however alike. */
    @Test
    void andTwoChoicesInOnePartAreTwoFacts() {
        Set<RuleShortfall> filed = filed(
                met(new ClauseOccurrence(1), wrote(3)).of(FIRST),
                met(new ClauseOccurrence(1), wrote(7)).of(FIRST));

        assertEquals(2, filed.size(),
                "an author wrote two of them and has two things to look at, and the part around"
                        + " them says neither");
    }

    /**
     * And a shape no author wrote is sent to the part it stands in, and invents nothing.
     *
     * <p>A construction giving a field an expression of its own puts shapes in the tree its author
     * did not write, and a choice among those has no operator anybody can edit. What the reader
     * gets is the part, which is the finest thing an author did write there — so two of them in one
     * part are one fact, and that is the truth about how many places there are to go.
     */
    @Test
    void andAShapeNobodyWroteIsSentToThePartItStandsIn() {
        Set<RuleShortfall> filed = filed(
                met(new ClauseOccurrence(1), ConstructOccurrence.unwritten()).of(FIRST),
                met(new ClauseOccurrence(4), ConstructOccurrence.unwritten()).of(FIRST));

        assertEquals(Set.of(RuleSite.at(FIRST)),
                Set.of(filed.iterator().next().site()),
                "nothing fabricates a construct for a shape nobody wrote");
        assertEquals(1, filed.size(),
                "and the part is the whole of what they are told apart by");
    }

    /** And one thing short of two reasons, or short at two positions, is two facts. */
    @Test
    void andOneThingShortOfTwoIsTwoFacts() {
        Set<RuleShortfall> reasons = filed(
                shortfall(RuleSite.at(FIRST), UnreadReason.ALTERNATIVE_NOT_READ, POSITION),
                shortfall(RuleSite.at(FIRST), UnreadReason.FORM_NOT_READ, POSITION));
        Set<RuleShortfall> positions = filed(
                shortfall(RuleSite.at(FIRST), UnreadReason.ALTERNATIVE_NOT_READ, POSITION),
                shortfall(RuleSite.at(FIRST), UnreadReason.ALTERNATIVE_NOT_READ, BESIDE_IT));

        assertEquals(2, reasons.size(),
                "two things one part left are two things to lift, and lifting one leaves the"
                        + " other");
        assertEquals(2, positions.size(),
                "one part leaving two positions short is two facts, and a reader is owed both");
    }

    /** What crosses out of a reading, as the set that files it holds them. */
    private static Set<RuleShortfall> filed(RuleShortfall... these) {
        return new LinkedHashSet<>(List.of(these));
    }

    private static RuleShortfall shortfall(RuleSite site, UnreadReason why, FactSubject at) {
        return new RuleShortfall(wrote(3), site, RuleShortfall.Kind.CHOICE, why, at);
    }

    /** One position left open by a choice, met at {@code at} and written as {@code writtenAs}. */
    private static ReadingShortfall met(ClauseOccurrence at, ConstructOccurrence writtenAs) {
        return new ReadingShortfall(at, writtenAs, RuleShortfall.Kind.CHOICE,
                UnreadReason.ALTERNATIVE_NOT_READ, POSITION);
    }

    /** One operator of the declaration, counted where its author wrote it. */
    private static ConstructOccurrence wrote(int ordinal) {
        return ConstructOccurrence.asWritten(new SourceConstructOrigin(
                new WrittenOwner.Declaration(new TypeKey("demo", "Held")), ordinal, 0,
                SourceConstruct.BINARY));
    }

    private static PartId<RuleRef.Invariant> part(int ordinal) {
        return new PartId<>(new RuleRef.Invariant(new Clause.Ref(
                new Clause.Id(TypeSymbols.declared(new TypeKey("demo", "Held")), 0),
                Optional.empty())), ordinal);
    }
}
