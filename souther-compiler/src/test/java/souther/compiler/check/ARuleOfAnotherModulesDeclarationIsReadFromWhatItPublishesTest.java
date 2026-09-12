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
 * publishes.
 *
 * <p>Said by refusing the other thing. The reading below is assembled over a lookup of expanded
 * clauses that throws whenever it is asked, and it reads the imported declaration's rule anyway —
 * which is a claim about where the rule came from that no comparison of two equal answers can make.
 * A tree the declaring module expanded is that module's own reading of what it wrote; read again
 * here, every answer built on it would differ whenever anything above the declaration moved, and
 * the answers travel to every module that imports the declaration.
 *
 * <p>The control is the same reading told nothing about the declaration: the rule is then one this
 * check has no form for, and the clause comes back lost. Without it this is met by a reading that
 * never looked at the declaration at all, which is the other way to be wrong about a boundary.
 *
 * <p><b>What this does not say.</b> Not that no tree of another module's declaration is reached: a
 * reading still asks the world which fields the declaration has, and the world answers out of the
 * tree. What is held here is the rules — which clauses there are and what each of them states.
 */
class ARuleOfAnotherModulesDeclarationIsReadFromWhatItPublishesTest {

    private static final TypeSymbol.AtModule AMOUNT =
            TypeSymbols.declared(new TypeKey("shop.prices", "Amount"));

    private static final String DECLARING = """
            module shop.prices exposing ( Amount )

            data Amount = Int
                invariant value >= 0
            """;

    private static final String IMPORTING = """
            module shop.cart exposing ( Basket )

            import shop.prices ( Amount )

            data Basket = { paid: Amount }
            """;

    /** The rule of the imported declaration, read where its tree is not on offer. */
    @Test
    void aReadingThatCannotReachTheTreeStillReadsTheRule() {
        Compilation c = compiled();
        Clauses.StatedClauses read = readOf(c, Shapes.publishedDeclarations(c.db()));

        assertEquals(1, read.clauses().size(),
                () -> "the imported declaration writes one rule and the reading has it: " + read);
        assertTrue(read.everyClauseStated(),
                () -> "and none of them was dropped: " + read.lost());
    }

    /** And a reading told nothing about the declaration has no rule of it, which is the control. */
    @Test
    void andAReadingToldNothingAboutItHasNoRuleOfIt() {
        Compilation c = compiled();
        Clauses.StatedClauses read = readOf(c, PublishedDeclarations.NONE);

        assertEquals(List.of(), read.clauses(),
                () -> "nothing said what the declaration states, so nothing states it: " + read);
    }

    /**
     * The importing module's reading of {@code Amount}, told by {@code said} what it says and
     * refused the tree its module expanded it into.
     *
     * <p>Every field given a value, so that a clause is not left to its run-time check for want of
     * one — which is the answer a reading that could not tell which fields are read would fall
     * into, and which would pass this whichever way the rule went missing.
     */
    private static Clauses.StatedClauses readOf(Compilation c, PublishedDeclarations said) {
        Clauses reading = new Clauses(new RuleReadingSource(
                Scopes.resolved(c.db(), "shop.cart").value(), noTreeIsOnOffer(), said,
                ClauseLocations.NONE));
        Map<BindingId, Core> given = new LinkedHashMap<>();
        reading.bindingsOf(AMOUNT).values()
                .forEach(each -> given.put(each, new Core.Int(1, Type.INT, new SourcePos(1, 1))));
        return reading.statedAt(AMOUNT, given);
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
        byId.put("prices.sou", DECLARING);
        byId.put("cart.sou", IMPORTING);
        Compilation c = Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY);
        c.answerEverything();
        assertTrue(c.db().allReports().isEmpty(),
                () -> "the workspace compiles: " + c.db().allReports());
        return c;
    }
}
