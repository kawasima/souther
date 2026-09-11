package souther.compiler.query;

import souther.compiler.WhereItSits;
import souther.compiler.source.SourceId;

import souther.compiler.ast.Hir;
import souther.compiler.ast.WrittenName;
import souther.compiler.check.Resolve;
import souther.compiler.diag.SourcePos;
import souther.compiler.meta.ModulePath;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.ValueName;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a name in a body denotes is answered once, where the module's names are resolved, and the
 * answer is in the tree.
 *
 * <p>Before this, each reader worked it out in the order it happened to try: the checker asked its
 * type environment, then the unit data declarations, then the library, then the injected behaviors;
 * the newtype desugar asked only the type namespace, so a binding of the same spelling was invisible
 * to it. These pin the one answer, so a reader that disagrees with it is a reader that is wrong.
 */
class ResolvedValueNamesTest {

    private static Hir.Module resolve(String source) {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("a.sou", source);
        Compilation c = Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY);
        return c.db().ask(new Names.Resolved("m.a")).value();
    }

    /** Every name used as a value in the module's bodies, in the order it is written. */
    private static List<Hir.Expr> named(Hir.Module m) {
        List<Hir.Expr> out = new ArrayList<>();
        for (Hir.FnDef fn : m.fns()) {
            if (fn.body() instanceof Hir.FnBody.Written w) {
                collect(w.expr(), out);
            }
        }
        return out;
    }

    private static void collect(Hir.Expr e, List<Hir.Expr> out) {
        if (e instanceof Hir.Var || e instanceof Hir.Apply) {
            out.add(e);
        }
        Hir.forEachChild(e, child -> collect(child, out));
    }

    private static ValueName denotes(Hir.Expr e) {
        Hir.Var.Denoting named = e instanceof Hir.Var v
                ? v.answered() : ((Hir.Apply) e).answered();
        return named == null ? null : named.denotes();
    }

    /** The name written as {@code written}, whatever state resolution left it in. */
    private static Hir.Var nameOf(String source, String written) {
        for (Hir.Expr e : named(resolve(source))) {
            Hir.Var v = e instanceof Hir.Var name ? name
                    : ((Hir.Apply) e).function() instanceof Hir.Var f ? f : null;
            if (v != null && v.name().equals(written)) {
                return v;
            }
        }
        throw new AssertionError("nothing named `" + written + "` in the resolved module");
    }

    /** The one named {@code written}, which each of these writes once. */
    private static ValueName denotationOf(String source, String written) {
        for (Hir.Expr e : named(resolve(source))) {
            String spelled = e instanceof Hir.Var v ? v.name() : ((Hir.Apply) e).written();
            if (spelled.equals(written)) {
                return denotes(e);
            }
        }
        throw new AssertionError("nothing named `" + written + "` in the resolved module");
    }

    @Test
    void aParameterIsLocalToTheBindingThatIntroducedIt() {
        Hir.Module m = resolve("""
                module m.a exposing ( f )

                behavior f : (n: Int) -> Int
                let f (n) = n
                """);

        ValueName denoted = denotationOf("""
                module m.a exposing ( f )

                behavior f : (n: Int) -> Int
                let f (n) = n
                """, "n");
        ValueName.Local local = assertInstanceOf(ValueName.Local.class, denoted);
        assertEquals("n", local.name());
        assertEquals(m.fns().get(0).params().get(0).binder().id(), local.id(),
                "the name is the binding the parameter introduced");
    }

    /** A body may bind a name the module declares, and inside the binding that is what it means. */
    @Test
    void aBindingWinsOverTheDeclarationOfTheSameSpelling() {
        String source = """
                module m.a exposing ( Amount, f )

                data Amount = Int

                behavior f : (n: Int) -> Int
                let f (n) = {
                    let Amount = n
                    Amount
                }
                """;

        assertInstanceOf(ValueName.Local.class, denotationOf(source, "Amount"));
    }

    /** Two bindings of one spelling are two names, told apart by where each was bound. */
    @Test
    void twoBindingsOfOneSpellingAreTwoNames() {
        String source = """
                module m.a exposing ( f, g )

                behavior f : (n: Int) -> Int
                let f (n) = n

                behavior g : (n: Int) -> Int
                let g (n) = n
                """;
        List<ValueName> locals = new ArrayList<>();
        for (Hir.Expr e : named(resolve(source))) {
            locals.add(denotes(e));
        }

        assertEquals(2, locals.size(), locals.toString());
        assertNotEquals(locals.get(0), locals.get(1),
                "one spelling, two bindings, two names: " + locals);
    }

    @Test
    void aLibraryCallDenotesTheLibraryFunction() {
        String source = """
                module m.a exposing ( f )

                behavior f : (xs: List<Int>) -> Int
                let f (xs) = List.length(xs)
                """;

        assertEquals(ValueName.Stdlib.operation("List", "length"), denotationOf(source, "List.length"));
    }

    /**
     * A library name is reached under an alias, and the alias is no part of what declares the
     * operation: {@code souther.list} declares {@code foldFrom} and a reader reaches it as
     * {@code List.foldFrom}. The two are held apart so that no reader downstream has to split a
     * spelling to get at either — the alias to say which library was written, the name to say which
     * operation it is.
     */
    @Test
    void aLibraryNameHoldsItsAliasAndItsOperationApart() {
        String source = """
                module m.a exposing ( f )

                behavior f : (xs: List<Int>) -> Int
                let f (xs) = List.length(xs)
                """;

        ValueName.Stdlib denotes = assertInstanceOf(ValueName.Stdlib.class,
                denotationOf(source, "List.length"));

        assertEquals("List", denotes.alias());
        assertEquals("length", denotes.name());
    }

    /** And what it is reached by is what it was. Holding the two apart is a change to how the answer
     * is written down, not to the answer: a table keyed by the name a library call reaches is looked
     * up with the same key it was before. */
    @Test
    void aLibraryNameIsStillReachedByItsQualifiedSpelling() {
        String source = """
                module m.a exposing ( f )

                behavior f : (xs: List<Int>) -> Int
                let f (xs) = List.length(xs)
                """;

        for (Hir.Expr e : named(resolve(source))) {
            if (e instanceof Hir.Apply call && call.written().equals("List.length")) {
                assertEquals("List.length", call.answered().reaches());
                return;
            }
        }
        throw new AssertionError("nothing applied `List.length` in the resolved module");
    }

    @Test
    void aCallOfTheModulesOwnLetDenotesThatHelper() {
        String source = """
                module m.a exposing ( f )

                let double (n: Int): Int = n * 2

                behavior f : (n: Int) -> Int
                let f (n) = double(n)
                """;

        assertEquals(new ValueName.Helper("m.a", "double"), denotationOf(source, "double"));
    }

    @Test
    void aCallOfAnInjectedBehaviorDenotesThatBehavior() {
        String source = """
                module m.a exposing ( f )

                behavior rate : (n: Int) -> Int

                behavior f : (n: Int) -> Int
                    depends on rate
                let f (n, rate) = rate(n)
                """;

        // the trailing `depends on` parameter binds the name in the body (spec §depends-on)
        assertInstanceOf(ValueName.Local.class, denotationOf(source, "rate"));
    }

    @Test
    void aUnitDataUsedAsAValueDenotesThatType() {
        String source = """
                module m.a exposing ( Approved, f )

                data Approved

                behavior f : (n: Int) -> Approved
                let f (n) = Approved
                """;

        ValueName.OfType denoted = assertInstanceOf(ValueName.OfType.class,
                denotationOf(source, "Approved"));
        assertEquals("m.a", ((souther.compiler.types.TypeSymbol.AtModule) denoted.type()).module());
        assertEquals("Approved", denoted.type().name());
    }

    /** {@code Amount(500)} names a type, not a function; that it is a construction is settled here
     * and not by a later pass asking the type namespace again. */
    @Test
    void aNewtypeAppliedToAValueDenotesTheTypeItConstructs() {
        String source = """
                module m.a exposing ( Amount, f )

                data Amount = Int

                behavior f : (n: Int) -> Amount
                    constructs Amount
                let f (n) = Amount(n)
                """;

        ValueName.OfType denoted = assertInstanceOf(ValueName.OfType.class,
                denotationOf(source, "Amount"));
        assertEquals("Amount", denoted.type().name());
    }

    @Test
    void aNameNothingAnswersToDenotesNothing() {
        String source = """
                module m.a exposing ( f )

                behavior f : (n: Int) -> Int
                let f (n) = nosuch
                """;

        Hir.Var name = nameOf(source, "nosuch");
        assertInstanceOf(Hir.Var.Unanswered.class, name);
        // and it holds no stand-in for a reader below to take for a declaration: what a name names
        // is the answered form's to say, and this is not one
        assertNull(name.answered());
    }

    /** Every name in every body is answered — nothing is left as a spelling for a later reader to
     * work out for itself. */
    @Test
    void everyNameInABodyIsAnswered() {
        Hir.Module m = resolve("""
                module m.a exposing ( Amount, Approved, f )

                data Amount = Int
                data Approved

                let double (n: Int): Int = n * 2

                behavior f : (xs: List<Int>) -> Amount | Approved
                    constructs Amount
                let f (xs) = {
                    let total = List.length(xs)
                    if total > 0 then Amount(double(total)) else Approved
                }
                """);

        // That nothing here has been left unread is the representation's to say: `Hir.Var` has
        // no form for one. What is asked is the other half — that every one of them names
        // something.
        for (Hir.Expr e : named(m)) {
            assertTrue(denotes(e) != null, "unanswered name in " + e);
        }
    }

    /**
     * A name in a body that denotes a type is recorded as a use of that type, so everything that
     * asks where a type is named finds it — a rename that missed one would leave a body naming a
     * type that no longer exists.
     */
    @Test
    void aTypeNamedInABodyIsAUseOfThatType() {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("a.sou", """
                module m.a exposing ( Amount, Approved, f )

                data Amount = Int
                data Approved

                behavior f : (n: Int) -> Amount | Approved
                    constructs Amount
                let f (n) = if n > 0 then Amount(n) else Approved
                """);
        Compilation c = Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY);

        List<souther.compiler.check.Resolve.TypeUse> amount = c.db()
                .ask(new Names.UsesOf("m.a", souther.compiler.types.TypeSymbols.declared(new souther.compiler.types.TypeKey("m.a", "Amount"))))
                .value();
        List<souther.compiler.check.Resolve.TypeUse> approved = c.db()
                .ask(new Names.UsesOf("m.a", souther.compiler.types.TypeSymbols.declared(new souther.compiler.types.TypeKey("m.a", "Approved"))))
                .value();

        assertTrue(amount.stream().anyMatch(
                        d -> c.texts().resolve(d.pos()).line() == 8),
                "the construction `Amount(n)` in the body: " + amount);
        assertTrue(approved.stream().anyMatch(
                        d -> c.texts().resolve(d.pos()).line() == 8),
                "the unit value `Approved` in the body: " + approved);
    }

    /**
     * Two bindings that spell their name the same are two bindings, and each name under them is
     * answered with the one in force. Nothing here compares text, so the answer does not depend on
     * how either was spelled.
     */
    @Test
    void twoBindingsOfOneSpellingAreTwoBindings() {
        Hir.Module m = resolve("""
                module m.a exposing ( f )

                behavior f : (n: Int) -> Int
                let f (n) = {
                    let x = n + 1
                    let y = (m) -> {
                        let x = m + 2
                        x
                    }
                    x + y(n)
                }
                """);

        List<ValueName.Local> reads = new ArrayList<>();
        for (Hir.Expr e : named(m)) {
            if (e instanceof Hir.Var.Denoting v && v.name().equals("x")
                    && v.denotes() instanceof ValueName.Local local) {
                reads.add(local);
            }
        }

        assertEquals(2, reads.size(), "both reads of `x`: " + reads);
        assertNotEquals(reads.get(0).id(), reads.get(1).id(),
                "the inner `x` is not the outer one, though they are spelled alike");
    }

    /** A binding belongs to the definition whose text introduced it, so an edit to one definition
     * leaves the bindings of the definitions beside it where they were. */
    @Test
    void aBindingBelongsToTheDefinitionThatIntroducedIt() {
        ValueName denoted = denotationOf("""
                module m.a exposing ( f, g )

                behavior f : (n: Int) -> Int
                let f (n) = n

                behavior g : (m: Int) -> Int
                let g (m) = m
                """, "m");
        ValueName.Local local = assertInstanceOf(ValueName.Local.class, denoted);
        assertEquals(new BindingOwner.OfValue("m.a", "g"), local.id().owner(),
                "`m` is bound by `g`, so `g` is what it belongs to");
    }

    /** What a name in {@code source} denotes, and where the editor says it was declared. */
    private static SourcePos declaredAt(String source, String written) {
        WrittenName at = declaredNameOf(source, written);
        return at == null ? null : at.pos();
    }

    /** The occurrence an editor is sent to for the declaration of {@code written}. */
    private static WrittenName declaredNameOf(String source, String written) {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("a.sou", source);
        Compilation c = Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY);
        for (Resolve.ValueUse use : c.db().ask(new Names.Facts("m.a")).value().values()) {
            if (use.written().canonical().equals(written)
                    && use.denotes() instanceof ValueName.Local local) {
                return c.db().ask(new Names.ValueDeclaredAt(local)).value();
            }
        }
        throw new AssertionError("nothing named `" + written + "` is a binding here");
    }

    /**
     * An invariant reads its declaration's fields as the bindings they are, so an editor asked where
     * one is declared answers with the field.
     */
    @Test
    void aFieldAnInvariantReadsIsDeclaredWhereTheFieldIsWritten() {
        assertEquals(new SourcePos(4, 7, new SourceId("a.sou")), declaredAt("""
                module m.a exposing ( Amount )

                data Amount = {
                      value: Int
                }
                    invariant value >= 0
                """, "value"), "the field on line 4");
    }

    /**
     * A field written one way and read the other is one field, declared where it was written and
     * as wide as the characters that wrote it.
     *
     * <p>An editor is not told what a field read is — a rename answered from one would rewrite the
     * declaration and none of the reads the type settles — so this is where a field's occurrence is
     * checked at all.
     */
    @Test
    void aFieldReadComposedIsTheOneDeclaredDecomposedAndKeepsItsWidth() {
        String decomposed = "\u304b\u3099f";
        String composed = "\u304cf";
        String source = """
                module m.a exposing ( Amount )

                data Amount = {
                      %s: Int
                }
                    invariant %s >= 0
                """.formatted(decomposed, composed);
        WrittenName declared = declaredNameOf(source, composed);

        assertEquals(new SourcePos(4, 7, new SourceId("a.sou")), declared.pos(), "the field on line 4");
        assertEquals(decomposed, declared.spelling(), "quoted as the declaration writes it");
        assertEquals(decomposed.length(),
                WhereItSits.in(source, declared.region()).end().column()
                        - WhereItSits.in(source, declared.region()).start().column(),
                "an underline over the name would stop one character short");
    }

    /** A field an include brings in keeps the binding of the declaration that wrote it, so it is
     * declared there and not where it was spread in. */
    @Test
    void aFieldAnIncludeBringsInIsDeclaredWhereItWasWritten() {
        assertEquals(new SourcePos(4, 7, new SourceId("a.sou")), declaredAt("""
                module m.a exposing ( Priced )

                data Money = {
                      cost: Int
                }

                data Priced = {
                      ...Money
                }
                    invariant cost >= 0
                """, "cost"), "the field on line 4, in the declaration that wrote it");
    }
}
