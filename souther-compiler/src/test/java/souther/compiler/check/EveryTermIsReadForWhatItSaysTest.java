package souther.compiler.check;

import souther.compiler.DefaultStdlib;
import souther.compiler.KeptCalls;
import souther.compiler.core.Core;
import souther.compiler.diag.SourcePos;
import souther.compiler.query.ReadAs;
import souther.compiler.types.ApplicationOrigin;
import souther.compiler.types.BinOp;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.types.FixtureReferenceOrigin;
import souther.compiler.types.ReferenceOrigin;
import souther.compiler.types.SourceConstruct;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.types.SourceReferenceOrigin;
import souther.compiler.types.Type;
import souther.compiler.types.ValueName;
import souther.compiler.types.WrittenOwner;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A term read for what it says, over every kind of term there is.
 *
 * <p>{@link TermMeaning} is a projection written a case per node kind, and a case is wrong in two
 * ways a compiler cannot see: it can read something that says where the node stands, and it can
 * leave out something the node says. The first makes a caller depend on an edit it cannot see; the
 * second makes two terms that say different things one dependency, which is the worse of the two —
 * a projection that read nothing at all would pass every test that only asks what it ignores.
 *
 * <p>So both are asked of each of the five things that say where a node stands: the position, the
 * occurrence a comparison or a fork is of the model, the copy of the body a fork stands in, and the
 * name and application a kept call was written as. Each is asked twice — moving it leaves the
 * reading alone, and moving what the node says does not.
 *
 * <p>Of fixtures, so that a build runs it. Every one of these is a pair of terms differing in one
 * thing, which is what a mutation of the projection has to be caught by; asking it of a corpus
 * instead would only reach the things two compilations of one model can be made to differ in, and
 * moving every line of a file reaches the positions and no ordinal. What a corpus does answer —
 * which kinds of term anything writes at all — is
 * {@code EveryKindOfTermACorpusWritesIsReadForWhatItSaysTest}.
 */
class EveryTermIsReadForWhatItSaysTest {

    private static final SourcePos POS = new SourcePos(1, 1);
    private static final SourcePos ELSEWHERE = new SourcePos(9, 4);
    private static final WrittenOwner OWNER = new WrittenOwner.Body("demo", "b");

    @Test
    void aPositionIsNotRead() {
        assertEquals(TermMeaning.of(new Core.Int(1, Type.INT, POS)),
                TermMeaning.of(new Core.Int(1, Type.INT, ELSEWHERE)),
                "one term written twice over says one thing");
    }

    @Test
    void andWhatTheTermSaysIs() {
        assertNotEquals(TermMeaning.of(new Core.Int(1, Type.INT, POS)),
                TermMeaning.of(new Core.Int(2, Type.INT, POS)),
                "two terms saying different things are two readings");
    }

    @Test
    void whichComparisonOfTheModelAComparisonIsIsNotRead() {
        assertEquals(TermMeaning.of(compared(BinOp.GT, ConstructOccurrence.unwritten())),
                TermMeaning.of(compared(BinOp.GT, written(SourceConstruct.BINARY))),
                "a comparison states what it states wherever the module counted it");
    }

    @Test
    void andWhatItComparesWithIs() {
        assertNotEquals(TermMeaning.of(compared(BinOp.GT, ConstructOccurrence.unwritten())),
                TermMeaning.of(compared(BinOp.LT, ConstructOccurrence.unwritten())),
                "two operators are two comparisons");
    }

    @Test
    void whichCopyOfTheBodyAForkStandsInIsNotRead() {
        assertEquals(TermMeaning.of(forked(List.of())),
                TermMeaning.of(forked(List.of(new BindingOwner.OfValue("demo", "helper")))),
                "a fork states what it states in whichever copy it was read out of");
    }

    @Test
    void andWhatTheForkAsksDoesIs() {
        assertNotEquals(TermMeaning.of(forked(List.of())),
                TermMeaning.of(new Core.If(new Core.Bool(false, Type.BOOL, POS),
                        new Core.Int(1, Type.INT, POS), new Core.Int(0, Type.INT, POS),
                        Core.ForkPlace.asWritten(ConstructOccurrence.unwritten()), Type.INT, POS)),
                "two forks asking different things are two readings");
    }

    /**
     * A kept call's two, which go together and go together here: what it applies and why it is here
     * are both about where the call stands, and a reading that took either would move with an edit
     * above the declaration.
     */
    @Test
    void whatAKeptCallWasWrittenAsIsNotRead() {
        assertEquals(TermMeaning.of(kept(new FixtureReferenceOrigin(0),
                        new ApplicationOrigin.ComposedFixture())),
                TermMeaning.of(kept(new SourceReferenceOrigin(OWNER, 0),
                        new ApplicationOrigin.Written(written(SourceConstruct.CALL).origin()))),
                "a call applies the operation it applies however the module reached it");
    }

    @Test
    void andWhatItAppliesIs() {
        assertNotEquals(TermMeaning.of(kept(new FixtureReferenceOrigin(0),
                        new ApplicationOrigin.ComposedFixture())),
                TermMeaning.of(KeptCalls.to(ValueName.Stdlib.operation("List", "length"),
                        List.of(new Core.Str("", Type.STRING, POS)), Type.INT, POS)),
                "two operations are two calls");
    }

    @Test
    void whatAWrittenTemporalWasSpelledAsIsNotRead() {
        assertEquals(TermMeaning.of(new Core.Temporal(Type.Prim.DATE, "2026-01-01",
                        new ApplicationOrigin.ComposedFixture(), POS)),
                TermMeaning.of(new Core.Temporal(Type.Prim.DATE, "2026-01-01",
                        new ApplicationOrigin.Written(written(SourceConstruct.CALL).origin()),
                        ELSEWHERE)),
                "a temporal denotes the value it denotes however it was constructed");
    }

    @Test
    void andWhichTemporalItIsIs() {
        assertNotEquals(TermMeaning.of(new Core.Temporal(Type.Prim.DATE, "2026-01-01",
                        new ApplicationOrigin.ComposedFixture(), POS)),
                TermMeaning.of(new Core.Temporal(Type.Prim.DATE, "2026-01-02",
                        new ApplicationOrigin.ComposedFixture(), POS)),
                "two days are two values");
    }

    /**
     * And two readings that come to one value are assumed alike.
     *
     * <p>The property the projection is for. Comparing equal is worth nothing on its own: what it
     * has to mean is that everything a caller may do with one of these answers the same of both, or
     * the store has decided two answers are one and left a reader able to tell them apart. The
     * reading a caller takes goes to the predicates, and what came back used to be the node the walk
     * stopped on — a term, with the place it was written at on it.
     *
     * <p>What the reading carries is a term of the check's own, which names a value by how it is
     * built and not by where it was written. That is what makes this hold, and it is a fact about
     * that naming rather than about this reading — so it is asked here, where breaking it would make
     * two readings the store called one answer with different things.
     */
    @Test
    void twoReadingsOfOneTermAreAssumedAlike() {
        PathEngine engine = new PathEngine(Symbols.none(DefaultStdlib.get()),
                RuleReadings.noClauseFiled(), ClauseMeanings.NONE, ClauseLocations.NONE,
                DeclarationReadings.NONE, Terms.Of.THE_DISCHARGE_TREE,
                ReadAs.THE_COMPILATION_DOES);
        Predicates predicates = engine.predicates();

        Predicates.Owed one = TermMeaning.of(containment(POS))
                .assumedBy(predicates, Denotations.none(), false);
        Predicates.Owed other = TermMeaning.of(containment(ELSEWHERE))
                .assumedBy(predicates, Denotations.none(), false);

        assertEquals(TermMeaning.of(containment(POS)), TermMeaning.of(containment(ELSEWHERE)),
                "one rule written twice over is one reading");
        assertTrue(one.parts().get(0) instanceof Predicates.Part.Carried,
                "this asks about the answer a rule the reading carries comes to");
        assertEquals(one, other, "and what the predicates make of one reading is one answer");
    }

    /**
     * {@code List.contains(1, [2])}, which this check reads as a value and not as a term.
     *
     * <p>Over literals and not over a name: a rule naming a value states something of that value,
     * and what this wants is a rule the reading makes nothing at all of.
     */
    private static Core containment(SourcePos pos) {
        return KeptCalls.to(ValueName.Stdlib.operation("List", "contains"),
                List.of(new Core.Int(1, Type.INT, pos),
                        new Core.ListLit(List.of(new Core.Int(2, Type.INT, pos)),
                                Type.list(Type.INT), pos)),
                Type.BOOL, pos);
    }

    private static ConstructOccurrence written(SourceConstruct kind) {
        return ConstructOccurrence.asWritten(SourceConstructOrigin.written(OWNER, 0, kind));
    }

    private static Core compared(BinOp op, ConstructOccurrence occurrence) {
        return new Core.Binary(op, new Core.Int(1, Type.INT, POS), new Core.Int(2, Type.INT, POS),
                occurrence, Type.BOOL, POS);
    }

    private static Core forked(List<BindingOwner> expansion) {
        return new Core.If(new Core.Bool(true, Type.BOOL, POS),
                new Core.Int(1, Type.INT, POS), new Core.Int(0, Type.INT, POS),
                new Core.ForkPlace(ConstructOccurrence.unwritten(), expansion), Type.INT, POS);
    }

    private static Core kept(ReferenceOrigin reference, ApplicationOrigin application) {
        Core.PreservedCall call = KeptCalls.to(ValueName.Stdlib.operation("List", "isEmpty"),
                List.of(new Core.Str("", Type.STRING, POS)), Type.BOOL, POS);
        return new Core.PreservedCall(call.declared(), call.args(),
                new Core.KeptCallPlace(reference, application), call.type(), POS);
    }
}
