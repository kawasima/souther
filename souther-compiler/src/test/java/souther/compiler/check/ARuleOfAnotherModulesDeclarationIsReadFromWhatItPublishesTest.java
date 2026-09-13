package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.core.Core;
import souther.compiler.diag.SourcePos;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Compilation;
import souther.compiler.query.Scopes;
import souther.compiler.query.Shapes;
import souther.compiler.types.BindingId;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a body is checked against, for a declaration another module wrote, is what that declaration
 * publishes — both which rules there are and what each of them states.
 *
 * <p>Said by refusing the other thing. Every reading below is assembled over a lookup of expanded
 * clauses that throws whenever it is asked, and reads the imported rules anyway — which is a claim
 * about where a rule came from that no comparison of two equal answers can make. A tree the
 * declaring module expanded is that module's own reading of what it wrote; read again here, every
 * answer built on it would differ whenever anything above the declaration moved, and the answers
 * travel to every module that imports the declaration.
 *
 * <p><b>Two things are read that way and both are held.</b> Which rules govern a value is a walk
 * down what each declaration spreads, and what each rule states is the declaration's own answer
 * about what it wrote. The walk is the half that moved furthest — it used to read the declaration
 * nodes out of the reader's own world — so it is held at a depth the reader cannot have reached by
 * accident: a rule written two modules away, reached through a spread that is itself imported.
 *
 * <p>The control is a reading told nothing about the declarations: the rules are then ones this
 * check has no form for. Without it this is met by a reading that never looked at the declaration
 * at all, which is the other way to be wrong about a boundary.
 *
 * <p><b>What this does not say.</b> Not that no tree of another module's declaration is reached: a
 * reading still asks the world which fields a declaration has, and the world answers out of the
 * tree. What is held here is the rules.
 */
class ARuleOfAnotherModulesDeclarationIsReadFromWhatItPublishesTest {

    private static final TypeSymbol.AtModule AMOUNT =
            TypeSymbols.declared(new TypeKey("shop.prices", "Amount"));

    private static final TypeSymbol.AtModule MIDDLE =
            TypeSymbols.declared(new TypeKey("shop.middle", "Middle"));

    private static final TypeKey BASE = new TypeKey("shop.base", "Base");

    /** The declaration the importer names, which writes a rule of its own. */
    private static final String PRICES = """
            module shop.prices exposing ( Amount )

            data Amount = Int
                invariant value >= 0
            """;

    /** And one two modules down, which nothing the importer writes names. */
    private static final String BASE_MODULE = """
            module shop.base exposing ( Base )

            data Base = { n: Int }
                invariant n >= 1
            """;

    /** Spread into a declaration of a third module, which writes no rule of its own. */
    private static final String MIDDLE_MODULE = """
            module shop.middle exposing ( Middle )

            import shop.base ( Base )

            data Middle = { ...Base, m: Bool }
            """;

    private static final String CART = """
            module shop.cart exposing ( Basket )

            import shop.prices ( Amount )
            import shop.middle ( Middle )

            data Basket = { paid: Amount, held: Middle }
            """;

    /** The rule of the imported declaration, read where its tree is not on offer. */
    @Test
    void aReadingThatCannotReachTheTreeStillReadsTheRule() {
        Compilation c = compiled();
        Clauses.StatedClauses read = readOf(c, AMOUNT, Shapes.publishedDeclarations(c.db()));

        assertEquals(1, read.clauses().size(),
                () -> "the imported declaration writes one rule and the reading has it: " + read);
        assertTrue(read.everyClauseStated(),
                () -> "and none of them was dropped: " + read.lost());
    }

    /**
     * And the rule of a declaration reached only through what an imported one spreads.
     *
     * <p>Which declarations a value's rules are written on is what the walk answers, and it answers
     * it from what each declaration publishes about itself. The rule here is written in a module the
     * reader neither imports nor names: it is reached because the declaration the reader does name
     * says what it spreads, and that one says what it spreads. A walk reading the declaration nodes
     * instead would be reading the trees of two modules to answer it.
     */
    @Test
    void andOneWrittenOnWhatAnImportedDeclarationSpreads() {
        Compilation c = compiled();
        Clauses.StatedClauses read = readOf(c, MIDDLE, Shapes.publishedDeclarations(c.db()));

        assertEquals(1, read.clauses().size(),
                () -> "the spread brings in the one rule written under it: " + read);
        assertEquals(BASE, read.clauses().getFirst().clause().id().declaredOn().key(),
                "and it is named after the declaration that wrote it, not the one that spreads it");
        assertTrue(read.everyClauseStated(),
                () -> "and none of them was dropped: " + read.lost());
    }

    /** And a reading told nothing about the declarations has no rule of either, which is the
     *  control. */
    @Test
    void andAReadingToldNothingAboutThemHasNoRuleOfEither() {
        Compilation c = compiled();

        assertEquals(List.of(), readOf(c, AMOUNT, PublishedDeclarations.NONE).clauses(),
                "nothing said what the imported declaration states, so nothing states it");
        assertEquals(List.of(), readOf(c, MIDDLE, PublishedDeclarations.NONE).clauses(),
                "and nothing said what it spreads, so the walk goes nowhere");
    }

    /**
     * The importing module's reading of {@code named}, told by {@code said} what the declarations
     * say and refused the trees their modules expanded them into.
     *
     * <p>Every field given a value, so that a clause is not left to its run-time check for want of
     * one — which is the answer a reading that could not tell which fields are read would fall
     * into, and which would pass this whichever way the rule went missing.
     */
    private static Clauses.StatedClauses readOf(Compilation c, TypeSymbol.AtModule named,
                                                PublishedDeclarations said) {
        Clauses reading = new Clauses(new RuleReadingSource(
                Scopes.resolved(c.db(), "shop.cart").value(), noTreeIsOnOffer(), said,
                Shapes.declarationKinds(c.db()), Shapes.declarationNewtypes(c.db()),
                ClauseLocations.NONE));
        Map<BindingId, Core> given = new LinkedHashMap<>();
        reading.bindingsOf(named).values()
                .forEach(each -> given.put(each, new Core.Int(1, Type.INT, new SourcePos(1, 1))));
        return reading.statedAt(named, given);
    }

    /** A lookup of expanded clauses that answers nothing and says so by being asked. */
    private static ExpandedClauseLookup noTreeIsOnOffer() {
        return named -> {
            throw new AssertionError("a reading of another module's declaration asked for the tree"
                    + " that module expanded `" + named + "` into");
        };
    }

    private static Compilation compiled() {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("prices.sou", PRICES);
        byId.put("base.sou", BASE_MODULE);
        byId.put("middle.sou", MIDDLE_MODULE);
        byId.put("cart.sou", CART);
        Compilation c = Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY);
        c.answerEverything();
        assertTrue(c.db().allReports().isEmpty(),
                () -> "the workspace compiles: " + c.db().allReports());
        return c;
    }
}
