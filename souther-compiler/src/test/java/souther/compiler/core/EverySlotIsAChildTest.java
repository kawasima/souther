package souther.compiler.core;

import souther.compiler.check.CoreBinders;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.types.WrittenOwner;
import souther.compiler.ast.Hir;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.BindingId;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;
import souther.compiler.types.TypeSymbol;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The same three questions of the Core walk: what it reaches, and which operator each slot goes
 * through.
 *
 * <p>Core has three kinds of slot rather than two. Besides an expression and a name, an attempt
 * holds a construction — the only thing whose invariant can fail — and that is a kind of its own for
 * the same reason a name is: nothing else may stand there.
 *
 * <p>Every way a binding is reached is one node here. Reading it, applying it and spreading it all
 * hold a {@link Core.Read}, so a pass that counts how often a binding is used has one thing to
 * count.
 */
class EverySlotIsAChildTest {

    private static final SourcePos POS = new SourcePos(1, 1);
    private static final SourceConstructOrigin ORIGIN = SourceConstructOrigin.written(
            new WrittenOwner.Body("t", "b"), 0,
            souther.compiler.types.SourceConstruct.IF);
    private static final BindingOwner OWNER = new BindingOwner.OfValue("demo", "go");

    private static final Hir.Binders BINDERS = new Hir.Binders(OWNER);

    /** A binding this test writes. Nothing runs after a pass that writes into a resolved body to
     *  answer a binder it left, so one is minted with its binding rather than a spelling. */
    private static Core.Binder binder(String name) {
        return CoreBinders.of(BINDERS.binder(name, POS));
    }
    private static final TypeSymbol.AtModule PERSON = TypeSymbols.declared(new TypeKey("demo", "Person"));

    private static Core.Read read(String name, int ordinal) {
        return new Core.Read(name, new BindingId(OWNER, ordinal), Type.INT, POS);
    }

    /** {@code Person { ...base, age = 1 }} as a construction holds it: the spread resolved into the
     * read of the field it supplies. */
    private static Core.Construct construction() {
        return new Core.Construct(PERSON,
                List.of(new Core.FieldValue("name",
                                new Core.FieldAccess(read("base", 0), "name", Type.STRING, POS), POS),
                        new Core.FieldValue("age", new Core.Int(1, Type.INT, POS), POS)),
                Type.ref(PERSON), POS);
    }

    private static List<Core> childrenOf(Core e) {
        List<Core> out = new ArrayList<>();
        Core.forEachChild(e, out::add);
        return out;
    }

    private static List<String> namesIn(List<Core> children) {
        return children.stream().filter(c -> c instanceof Core.Read)
                .map(c -> ((Core.Read) c).name()).toList();
    }

    /** What each field is given is a child, and a binding one of them reads is reached through it.
     * A pass that asks which bindings a body reaches has to be given them, or it undercounts — which
     * is what a construction holding a spread beside its fields used to be about. */
    @Test
    void whatEachFieldIsGivenIsAChild() {
        List<Core> children = childrenOf(construction());

        assertEquals(List.of("FieldAccess", "Int"),
                children.stream().map(c -> c.getClass().getSimpleName()).toList());
        assertEquals(List.of("base"), namesIn(childrenOf(children.get(0))));
    }

    /**
     * What an application applies is a binding holding a function, and the backend loads its slot —
     * the same position a spread is in. A pass that asks which bindings a body reaches has to be
     * given it, or it undercounts.
     */
    @Test
    void anAppliedFunctionIsAChild() {
        Core.Apply applied = new Core.Apply(read("f", 1),
                List.of(new Core.Int(2, Type.INT, POS)), Type.INT, POS);

        assertEquals(List.of("f"), namesIn(childrenOf(applied)));
    }

    /**
     * An attempt's construction is a direct child, not a node the walk descends past. A visitor
     * looking for constructions finds the one an attempt tests the same way it finds any other.
     */
    @Test
    void anAttemptsConstructionIsAChild() {
        Core.IfConstructed attempt = new Core.IfConstructed(construction(),
                binder("p"), new Core.Int(0, Type.INT, POS),
                List.of(new Core.ElseArm(Optional.empty(), new Core.Int(1, Type.INT, POS))),
                Core.ForkPlace.asWritten(ConstructOccurrence.asWritten(ORIGIN)), Type.INT, POS);

        assertTrue(childrenOf(attempt).stream().anyMatch(c -> c instanceof Core.Construct),
                "the construction itself, rather than the field values inside it");
    }

    @Test
    void eachSlotGoesThroughTheOperatorForItsKind() {
        Core.IfConstructed attempt = new Core.IfConstructed(construction(),
                binder("p"),
                new Core.Apply(read("f", 1), List.of(), Type.INT, POS),
                List.of(), Core.ForkPlace.asWritten(ConstructOccurrence.asWritten(ORIGIN)),
                Type.INT, POS);

        List<String> asExpressions = new ArrayList<>();
        List<String> asNames = new ArrayList<>();
        List<String> asConstructions = new ArrayList<>();

        Core.mapChildren(attempt, child -> {
            asExpressions.add(child.getClass().getSimpleName());
            return child;
        }, name -> {
            asNames.add(name.name());
            return name;
        }, built -> {
            asConstructions.add(built.typeName().name());
            return built;
        });

        assertEquals(List.of("Apply"), asExpressions, "the success branch, and nothing else");
        assertEquals(List.of("Person"), asConstructions);
        assertEquals(List.of(), asNames,
                "the applied name is a slot of the `Apply`, not of the attempt");
    }

    @Test
    void aWalkThatChangesNothingKeepsTheNodeItWalked() {
        Core.Construct built = construction();

        assertSame(built, Core.mapChildren(built, c -> c, n -> n, b -> b));
        assertSame(built, Core.mapChildren(built, c -> c));
    }
}
