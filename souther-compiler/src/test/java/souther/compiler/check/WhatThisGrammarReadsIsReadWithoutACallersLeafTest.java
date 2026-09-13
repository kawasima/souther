package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.DefaultStdlib;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.ast.Ast;
import souther.compiler.ast.Hir;
import souther.compiler.core.Core;
import souther.compiler.diag.SourcePos;
import souther.compiler.frontend.CstFrontend;
import souther.compiler.numeric.LinearForm;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A construction the grammar takes apart is read without either caller being asked to name it.
 *
 * <p><b>Who owns a rule, and not whether two callers agree about it.</b> Two readers walk this
 * grammar with leaves of their own, and a rule one of them keeps is a rule the other is free not to
 * have: a newtype's construction was a number to the check that discharges a clause and an
 * expression nobody could read to the measure beside it, over the same construction and for no
 * reason the model states. A pair of fixtures showing the two now agreeing would say they agree
 * today; what says it cannot come apart again is that the reading needs no leaf at all.
 *
 * <p>So the leaf here fails when it is called. What is left composing the form is the grammar,
 * which is the thing being claimed to own these two eliminations — a construction of a newtype, and
 * a field read off a construction.
 *
 * <p>The values wrapped are written as arithmetic rather than as numbers. A number written out is a
 * constant of this grammar wherever it stands, and a carrier reads one through a newtype's
 * construction to find it, so a test over {@code Yen(100)} would pass with no rule for a
 * construction anywhere — and go on passing after the rule it is meant to hold was taken away.
 */
class WhatThisGrammarReadsIsReadWithoutACallersLeafTest {

    private static final SourcePos SOMEWHERE = new SourcePos(1, 1);

    private static final String MODULE = """
            module demo

            data Yen = Int
            data Big = { threshold: Int }
            """;

    private static final Symbols SYMBOLS = symbols();

    private static final TypeSymbol.AtModule YEN = TypeSymbols.declared(new TypeKey("demo", "Yen"));

    private static final TypeSymbol.AtModule BIG = TypeSymbols.declared(new TypeKey("demo", "Big"));

    /** {@code 99 + 1}, which no carrier reads as a written value. */
    private static Core computed() {
        return new Core.Binary(souther.compiler.types.BinOp.ADD,
                new Core.Int(99, Type.INT, SOMEWHERE), new Core.Int(1, Type.INT, SOMEWHERE),
                ConstructOccurrence.unwritten(), Type.INT, SOMEWHERE);
    }

    /** A newtype's construction is the value it wraps, and the grammar says so. */
    @Test
    void aNewtypesConstructionIsWhatItWraps() {
        Core wrapped = new Core.Construct(YEN,
                List.of(new Core.FieldValue("value", computed(), SOMEWHERE)),
                Type.ref(YEN), SOMEWHERE);

        assertEquals(BigDecimal.valueOf(100), constantOf(wrapped));
    }

    /** And a field read off a construction is what that construction gives the field. */
    @Test
    void aFieldReadOffAConstructionIsWhatItWasGiven() {
        Core built = new Core.Construct(BIG,
                List.of(new Core.FieldValue("threshold", computed(), SOMEWHERE)),
                Type.ref(BIG), SOMEWHERE);

        assertEquals(BigDecimal.valueOf(100),
                constantOf(new Core.FieldAccess(built, "threshold", Type.INT, SOMEWHERE)));
    }

    /** The constant {@code e} reads as, through a walk that can name nothing. */
    private static BigDecimal constantOf(Core e) {
        LinearForm<String> form = AffineForms.of(e, "nowhere", refusingToName());
        if (form == null) {
            throw new AssertionError("the grammar composed no form for " + e);
        }
        assertEquals(java.util.Map.of(), form.coefs(), "a written value stands over no position");
        return form.constant();
    }

    /**
     * A reading that answers only what depends on an environment, and has no leaf.
     *
     * <p>{@code readsThrough} is false throughout. It is the second proof a projection has, and
     * left on it would answer {@code Yen(...).value} whether or not the elimination this test is
     * about reads anything.
     */
    private static AffineForms.Reading<String, String> refusingToName() {
        return new AffineForms.Reading<>() {

            @Override
            public Symbols symbols() {
                return SYMBOLS;
            }

            @Override
            public PublishedDeclarations published() {
                return PublishedDeclarations.NONE;
            }

            @Override
            public DeclarationKinds kinds() {
                return DeclarationKinds.NONE;
            }

            @Override
            public NewtypeInners inners() {
                return NewtypeInners.NONE;
            }

            @Override
            public LinearForm<String> leafOf(Core e, String at) {
                fail("the grammar owns this reading and asked a caller's leaf about " + e);
                return null;
            }

            @Override
            public String inside(Core.LetIn li, String at) {
                return at;
            }

            @Override
            public AffineForms.ReadThrough<String> readThrough(Core.Read read, String at) {
                return null;
            }

            /** No name here stands for several values either: what is being shown is that the
             *  grammar reads these shapes with an environment that answers nothing. */
            @Override
            public java.util.List<AffineForms.ReadThrough<String>> alternativesOf(Core.Read read,
                                                                                  String at) {
                return null;
            }

            @Override
            public boolean readsThrough(Core.FieldAccess fa, String at) {
                return false;
            }
        };
    }

    private static Symbols symbols() {
        Ast.Module parsed = CstFrontend.parse(MODULE);
        Hir.Module resolved = Resolve.module(parsed, SyntaxSymbols.of(parsed, DefaultStdlib.get()));
        return Symbols.of(resolved, DefaultStdlib.get());
    }
}
