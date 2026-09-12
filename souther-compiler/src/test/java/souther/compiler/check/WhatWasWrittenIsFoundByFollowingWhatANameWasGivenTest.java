package souther.compiler.check;

import souther.compiler.DefaultStdlib;
import souther.compiler.ast.Hir;
import souther.compiler.core.Core;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.CaseSelector;
import souther.compiler.types.ResolvedCase;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.types.ReachName;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;
import souther.compiler.types.ValueName;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * What a value was written as is found by following what its name was given, and not by remembering
 * it beside the name.
 *
 * <p>A name is what it was given, so the text behind it is however many names away it was written —
 * which the walk already records, one binding at a time. Kept beside the denotation as well, the same
 * fact was held twice: once as what the binding stands for and once as the text a reading of it
 * folded, and the two had to be made to agree at every place a binding is entered.
 *
 * <p>Followed by what a name was given and not by what it denotes. A binding is what it was given
 * whatever kind of thing that is, so the rule is one rule — a {@code match} arm opening a value
 * written into the source opens that written value, and it is written there for the same reason the
 * name it was given is.
 */
class WhatWasWrittenIsFoundByFollowingWhatANameWasGivenTest {

    private static final SourcePos POS = new SourcePos(1, 1);
    private static final BindingOwner OWNER = new BindingOwner.OfValue("demo", "f");
    private static final ValueName.Behavior FIND = new ValueName.Behavior("demo", "findIt");
    private static final TypeSymbol.AtModule FOUND = TypeSymbols.declared(new TypeKey("demo", "Found"));

    private final Hir.Binders binders = new Hir.Binders(OWNER);
    private final PathEngine engine =
            new PathEngine(RuleReadings.ofNoClauseFiled(Symbols.none(DefaultStdlib.get())),
                    Terms.Of.THE_DISCHARGE_TREE,
                    souther.compiler.query.ReadAs.THE_COMPILATION_DOES);

    @Test
    void aNameGivenTextIsThatText() {
        Core three = new Core.Int(3, Type.INT, POS);
        Core.Binder a = CoreBinders.of(binders.binder("a", POS));
        Denotations at = given(a, three, Denotations.none());

        assertSame(three, engine.terms().writtenValue(read(a), at));
    }

    /** However many names away. Each link records what it was given, so the text is at the end of the
     * chain and no link has to carry it. */
    @Test
    void aNameGivenANameGivenTextIsThatTextToo() {
        Core three = new Core.Int(3, Type.INT, POS);
        Core.Binder a = CoreBinders.of(binders.binder("a", POS));
        Core.Binder b = CoreBinders.of(binders.binder("b", POS));
        Core.Binder c = CoreBinders.of(binders.binder("c", POS));
        Denotations at = given(a, three, Denotations.none());
        at = given(b, read(a), at);
        at = given(c, read(b), at);

        assertSame(three, engine.terms().writtenValue(read(c), at));
    }

    /** A place stands for no value the walk reached, so there is no text behind it. */
    @Test
    void aPlaceWasGivenNothingSoItWasWrittenAsNothing() {
        Core.Binder x = CoreBinders.of(binders.binder("x", POS));
        Denotations at = engine.enter(Terms.read(x, Type.INT, POS), Known.top(),
                Denotations.none()).at();

        assertNull(engine.terms().writtenValue(read(x), at));
    }

    /** An arm opening a value written into the source opens that written value: the arm's name was
     * given the scrutinee, and following what a name was given is the whole of the rule. */
    @Test
    void anArmOpeningWrittenTextOpensThatText() {
        Core written = new Core.Construct(FOUND, List.of(), Type.ref(FOUND), POS);
        Core.Binder x = CoreBinders.of(binders.binder("x", POS));

        Denotations at = engine.enteringArm(
                arm(new Core.ResolvedPattern.Single(
                        ResolvedCase.of(CaseSelector.direct(FOUND), List.of(FOUND))), x),
                written, Known.top(), Denotations.none()).at();

        assertSame(written, engine.terms().writtenValue(read(x, Type.ref(FOUND)), at));
    }

    /** And an arm opening an answer opens nothing written, however the arm was entered. */
    @Test
    void anArmOpeningAnAnswerOpensNoText() {
        Core answer = new Core.Call(new Core.Reached.OfDeclaration(
                new ReachName.Own(FIND)), List.of(),
                ConstructOccurrence.unwritten(), Type.ref(FOUND), POS);
        Core.Binder x = CoreBinders.of(binders.binder("x", POS));

        Denotations at = engine.enteringArm(
                arm(new Core.ResolvedPattern.Single(
                        ResolvedCase.of(CaseSelector.direct(FOUND), List.of(FOUND))), x),
                answer, Known.top(), Denotations.none()).at();

        assertNull(engine.terms().writtenValue(read(x, Type.ref(FOUND)), at));
    }

    /** A name given itself is a chain with no end, and following it is still an answer. Nothing the
     * walk records is one, but the following is written to end whatever it is handed. */
    @Test
    @Timeout(5)
    void aNameGivenItselfIsFollowedToNothing() {
        Core.Binder a = CoreBinders.of(binders.binder("a", POS));
        Core.Binder b = CoreBinders.of(binders.binder("b", POS));
        Denotations at = Denotations.none()
                .binding(a.binding(), read(b), engine.terms().placeSubject(a.binding()), null, null, null)
                .binding(b.binding(), read(a), engine.terms().placeSubject(b.binding()), null, null, null);

        assertNull(engine.terms().writtenValue(read(a), at));
    }

    /** What the check reads it for: a written value folds where it is written and where it is bound
     * alike, so a name given text is that text at the construction it is handed to — and neither the
     * text nor the name it was given is a site an author could be asked to guard. */
    @Test
    void aNameGivenTextIsWrittenWhereverItIsRead() {
        Core three = new Core.Int(3, Type.INT, POS);
        Core.Binder a = CoreBinders.of(binders.binder("a", POS));
        Denotations at = given(a, three, Denotations.none());

        assertEquals(engine.terms().bodyKey(three, Denotations.none()),
                engine.terms().bodyKey(read(a), at),
                "the text and the name it was given are named alike");
        assertNull(engine.terms().reportableSite(read(a), at, Known.top()),
                "and neither is somewhere to ask an author for a guard");
        assertNull(engine.terms().reportableSite(three, Denotations.none(), Known.top()));
    }

    private Denotations given(Core.Binder binder, Core value, Denotations at) {
        return engine.bindLet(new Core.LetIn(binder, value,
                new Core.Read(binder.name(), binder.binding(), value.type(), POS), value.type(), POS),
                Known.top(), at).at();
    }

    private Core.Case arm(Core.ResolvedPattern pattern, Core.Binder binder) {
        return new Core.Case(pattern, binder, new Core.Read(binder.name(), binder.binding(),
                pattern.bindType(), POS), POS);
    }

    private static Core read(Core.Binder binder) {
        return read(binder, Type.INT);
    }

    private static Core read(Core.Binder binder, Type type) {
        return new Core.Read(binder.name(), binder.binding(), type, POS);
    }
}
