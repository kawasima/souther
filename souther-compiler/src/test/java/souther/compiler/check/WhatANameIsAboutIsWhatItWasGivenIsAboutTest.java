package souther.compiler.check;

import souther.compiler.DefaultStdlib;
import souther.compiler.types.BinOp;
import souther.compiler.ast.Hir;
import souther.compiler.core.Core;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.BindingId;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.types.ReachName;
import souther.compiler.types.Type;
import souther.compiler.types.ValueName;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A name is an alias for what it was given, so a fact about the name is a fact about that value.
 *
 * <p>What each binding is about is recorded where the binding is entered ({@link Denotations.Means}),
 * and what an expression is about is worked out from the expression ({@link Terms#subjectOf}). Both
 * answer for a name, so they have to answer alike — a name recorded as being about one value and read
 * as being about another is one value with two subjects, which is the thing a subject exists to stop.
 *
 * <p>Asked at each kind of thing a name can be given, because the recording and the reading take
 * different routes through {@link Denotes} and only agree if the four line up: a place, a value the
 * term grammar computes, a value written out, and an answer the grammar can name nothing of. The last
 * is the one that matters — it is the only kind whose denotation is nothing at all, so it is the only
 * kind where the recorded subject cannot be read back off what the binding denotes.
 */
class WhatANameIsAboutIsWhatItWasGivenIsAboutTest {

    private static final SourcePos POS = new SourcePos(1, 1);
    private static final BindingOwner OWNER = new BindingOwner.OfValue("demo", "f");
    private static final ValueName.Behavior FIND =
            new ValueName.Behavior("demo", "findIt");

    private final Hir.Binders binders = new Hir.Binders(OWNER);
    private final PathEngine engine =
            new PathEngine(Symbols.none(DefaultStdlib.get()),
                RuleReadings.noClauseFiled(), ClauseMeanings.NONE, ClauseLocations.NONE,
                DeclarationReadings.NONE, Terms.Of.THE_DISCHARGE_TREE,
                souther.compiler.query.ReadAs.THE_COMPILATION_DOES);

    @Test
    void aNameGivenAPlaceIsAboutThatPlace() {
        BindingId x = CoreBinders.of(binders.binder("x", POS)).binding();
        Denotations outer = Denotations.none().location(x, engine.terms().placeSubject(x), engine.terms().placeTerm(x));

        heldOf(new Core.Read("x", x, Type.INT, POS), outer);
    }

    @Test
    void aNameGivenSomethingComputedIsAboutWhatComputesIt() {
        BindingId x = CoreBinders.of(binders.binder("x", POS)).binding();
        Denotations outer = Denotations.none().location(x, engine.terms().placeSubject(x), engine.terms().placeTerm(x));

        heldOf(new Core.Binary(BinOp.ADD, new Core.Read("x", x, Type.INT, POS),
                new Core.Int(1, Type.INT, POS), ConstructOccurrence.unwritten(), Type.INT, POS),
                outer);
    }

    @Test
    void aNameGivenSomethingWrittenIsAboutWhatWasWritten() {
        heldOf(new Core.Int(3, Type.INT, POS), Denotations.none());
    }

    /** The one that cannot be read back off the denotation: what a behavior answered denotes nothing,
     * and its subject is an atom of its own. */
    @Test
    void aNameGivenAnAnswerIsAboutThatAnswer() {
        heldOf(answer(), Denotations.none());
    }

    @Test
    void twoNamesForOneAnswerAreAboutTheOneAnswer() {
        Core call = answer();
        Core.Binder first = CoreBinders.of(binders.binder("a", POS));
        Core.Binder second = CoreBinders.of(binders.binder("b", POS));

        Denotations at = engine.bindLet(letting(first, call), Known.top(), Denotations.none()).at();
        at = engine.bindLet(letting(second, new Core.Read("a", first.binding(), Type.INT, POS)),
                Known.top(), at).at();

        assertNotNull(at.subject(first.binding()));
        assertEquals(at.subject(first.binding()), at.subject(second.binding()),
                "a name for a name is a name for the value, and one value is one subject");
    }

    /** What {@code value} was recorded as being about is what reading the name it was given says it
     * is about. */
    private void heldOf(Core value, Denotations outer) {
        Core.Binder binder = CoreBinders.of(binders.binder("y", POS));
        Denotations at = engine.bindLet(letting(binder, value), Known.top(), outer).at();

        FactSubject recorded = at.subject(binder.binding());
        FactSubject read = engine.terms()
                .subjectOf(new Core.Read("y", binder.binding(), value.type(), POS), at);

        assertNotNull(recorded, "a name entered is a name something is known about");
        assertEquals(recorded, read,
                "what the walk wrote down and what a reader works out are one subject");
        assertEquals(engine.terms().subjectOf(value, outer), recorded,
                "and both are what the value it was given is about");
    }

    private Core.LetIn letting(Core.Binder binder, Core value) {
        return new Core.LetIn(binder, value, new Core.Read(binder.name(), binder.binding(), value.type(),
                POS), value.type(), POS);
    }

    private static Core answer() {
        return new Core.Call(new Core.Reached.OfDeclaration(
                new ReachName.Own(FIND)), List.of(), Type.INT, POS);
    }
}
