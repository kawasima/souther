package souther.compiler.query;

import souther.compiler.diag.SourcePos;
import souther.compiler.cst.SourceLayout;
import souther.compiler.source.SourceId;

import souther.compiler.ast.Ast;
import souther.compiler.check.Symbols;
import souther.compiler.meta.ModulePath;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What an edit costs. A compilation kept across edits recomputes what the edit reached and nothing
 * else, which is issue #35 — not as a caching layer bolted on, but because an answer that comes out
 * the same as the one it replaces leaves everything that read it alone.
 *
 * <p>"Not recomputed" is observed as the same answer object coming back: the store hands out what it
 * kept, so a different instance means the work ran again.
 */
class IncrementalCompilationTest {

    private static final String PRICES = """
            module shop.prices exposing ( Amount )

            data Amount = Int
                invariant value >= 0
            """;

    /** Imports shop.prices, so an edit there can reach here. */
    private static final String CART = """
            module shop.cart exposing ( Total )

            import shop.prices ( Amount )

            data Total = { paid: Amount }
            """;

    /** Names nothing from the other two, so no edit there can reach here. */
    private static final String CUSTOMERS = """
            module shop.customers exposing ( Name )

            data Name = String
                invariant String.length(value) > 0
            """;

    private static Map<String, String> workspace(String prices, String cart, String customers) {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("prices.sou", prices);
        byId.put("cart.sou", cart);
        byId.put("customers.sou", customers);
        return byId;
    }

    private static Compilation started() {
        Compilation c = Compilation.ofDocuments(workspace(PRICES, CART, CUSTOMERS), Set.of(),
                ModulePath.EMPTY);
        c.answerEverything();
        assertTrue(c.db().allReports().isEmpty(), "the workspace compiles to begin with");
        return c;
    }

    /**
     * An importer names some of what a module declares, so a declaration it does not name is none of
     * its business. Reading a module's declarations as one map made every importer depend on all of
     * them, so declaring something new — which no importer can even see yet — reached every module
     * that imported anything from there.
     */
    @Test
    void declaringSomethingNewDoesNotReachAnImporterThatDoesNotNameIt() {
        Compilation c = started();
        Answer<?> cart = c.db().ask(new Output.Classes("shop.cart"));

        c.update(workspace(PRICES + """

                data Discount = Int
                """, CART, CUSTOMERS), Set.of());
        c.answerEverything();

        assertSame(cart, c.db().ask(new Output.Classes("shop.cart")),
                "shop.cart names `Amount`, and `Amount` says what it said");
    }

    /**
     * And an edit to a declaration an importer does not name leaves it alone as well — the importer
     * reads the one it named, so what the one beside it says is none of its business.
     */
    @Test
    void editingADeclarationAnImporterDoesNotNameDoesNotReachIt() {
        Compilation c = started();
        Answer<?> cart = c.db().ask(new Output.Classes("shop.cart"));

        c.update(workspace(PRICES + """

                data Discount = { off: Int }
                """, CART, CUSTOMERS), Set.of());
        c.answerEverything();
        Answer<?> once = c.db().ask(new Output.Classes("shop.cart"));
        assertSame(cart, once, "declaring `Discount` is not shop.cart's business");

        c.update(workspace(PRICES + """

                data Discount = { off: Int, until: Date }
                """, CART, CUSTOMERS), Set.of());
        c.answerEverything();

        assertSame(once, c.db().ask(new Output.Classes("shop.cart")),
                "and neither is changing what `Discount` says");
    }

    @Test
    void anEditReachesTheModuleItWasMadeIn() {
        Compilation c = started();
        Answer<?> before = c.db().ask(new Output.Classes("shop.customers"));

        c.update(workspace(PRICES, CART, CUSTOMERS + """
                data Email = String
                    invariant matches("[^@]+@[^@]+", value)
                """), Set.of());
        c.answerEverything();

        assertNotSame(before, c.db().ask(new Output.Classes("shop.customers")));
    }

    @Test
    void anEditDoesNotReachAModuleThatNamesNothingOfIt() {
        Compilation c = started();
        Answer<?> prices = c.db().ask(new Output.Classes("shop.prices"));
        Answer<?> cart = c.db().ask(new Output.Classes("shop.cart"));

        c.update(workspace(PRICES, CART, CUSTOMERS + """
                data Email = String
                    invariant matches("[^@]+@[^@]+", value)
                """), Set.of());
        c.answerEverything();

        assertSame(prices, c.db().ask(new Output.Classes("shop.prices")));
        assertSame(cart, c.db().ask(new Output.Classes("shop.cart")));
    }

    /**
     * The boundary the whole claim rests on. An importer builds against what a module declares, so
     * changing a body it cannot see must not reach it — and that only works because a module's
     * declarations compare by what they say.
     */
    @Test
    void changingOnlyABodyDoesNotReachTheModulesThatImportIt() {
        Compilation c = started();
        Answer<?> cart = c.db().ask(new Output.Classes("shop.cart"));

        c.update(workspace(PRICES + """

                behavior twice : (n: Amount) -> Amount
                    constructs Amount
                let twice (n) = Amount(n.value * 2)
                """, CART, CUSTOMERS), Set.of());
        c.answerEverything();

        assertNotSame(prices(c), null, "the edited module is still compiled");
        assertSame(cart, c.db().ask(new Output.Classes("shop.cart")),
                "shop.cart imports a type, and the type did not change");
    }

    private static Object prices(Compilation c) {
        return c.db().ask(new Output.Classes("shop.prices")).value();
    }

    /** A module that writes definitions of its own before the declaration an importer names — one
     *  the importer reads through and one nothing at all reads. */
    private static final String PRICES_WITH_HELPERS = """
            module shop.prices exposing ( Amount )

            let floor = 0

            let describe (n: Int): Int = n

            data Amount = Int
                invariant value >= floor
            """;

    /** Names `Amount` and nothing else of it. */
    private static final String CART_OF_AMOUNT = """
            module shop.cart exposing ( Total )

            import shop.prices ( Amount )

            data Total = { paid: Amount }

            let ten = Amount(10)
            """;

    /**
     * And an edit to a body written <em>before</em> the declaration leaves the importer alone as
     * well. What a construct was numbered as is part of the declaration the importer builds
     * against, so a count over the file put every definition of a module into every importer's
     * dependency: editing a helper no importer can name renumbered the comparison in `Amount`'s
     * invariant, `Amount` came out different, and every module that imported it was compiled again
     * to the same class files.
     */
    @Test
    void editingABodyWrittenBeforeADeclarationDoesNotReachAnImporterEither() {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("prices.sou", PRICES_WITH_HELPERS);
        byId.put("cart.sou", CART_OF_AMOUNT);
        Compilation c = Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY);
        c.answerEverything();
        assertTrue(c.db().allReports().isEmpty(), "the workspace compiles to begin with");
        Answer<?> cart = c.db().ask(new Output.Classes("shop.cart"));

        Map<String, String> edited = new LinkedHashMap<>();
        edited.put("prices.sou", PRICES_WITH_HELPERS.replace("Int = n\n", "Int = n + 1\n"));
        edited.put("cart.sou", CART_OF_AMOUNT);
        c.update(edited, Set.of());
        c.answerEverything();

        assertSame(cart, c.db().ask(new Output.Classes("shop.cart")),
                "`describe` is written before `Amount` and is none of shop.cart's business");
    }

    /** Two behaviors and a helper both of them call, in one module. */
    private static final String ORDERS = """
            module shop.orders exposing ( Amount )

            data Amount = Int
                invariant value >= 0

            let doubled (n) = n * 2

            behavior twice : (n: Amount) -> Amount
                constructs Amount
            let twice (n) = Amount(doubled(n.value))

            behavior thrice : (n: Amount) -> Amount
                constructs Amount
            let thrice (n) = Amount(n.value * 3)
            """;

    /**
     * Where a place is said from decides what an edit reaches, and these are the three shapes that
     * settle it.
     *
     * <p>A place is which meaningful token of which top-level construct it is at. So writing a
     * token moves the places after it <b>in that construct</b> and no further; writing whitespace,
     * a line break or a comment moves nothing at all; and writing a construct moves the constructs
     * after it. Held here because the first of the three was lost once: counted over the whole
     * text rather than from the construct, a token typed into one body moved every declaration
     * under it, which is the commonest edit an author makes and the one this is all for.
     */
    @Test
    void aTokenWrittenInOneBodyLeavesThePlacesOfTheNextDeclarationWhereTheyWere() {
        SourceLayout before = SourceLayout.of(ORDERS, new SourceId("orders.sou"));
        SourceLayout after = SourceLayout.of(
                twiceOver("doubled(n.value + 0)").get("orders.sou"), new SourceId("orders.sou"));

        assertEquals(placeOf(before, "n.value * 3"), placeOf(after, "n.value * 3"),
                "`thrice` is written after the edit and says what it said");
        assertNotEquals(placeOf(before, "doubled"), placeOf(after, "n.value + 0"),
                "and the edit did move what follows it inside `twice`");
    }

    /** A comment or a line break is not a token, so nothing in the file is anywhere else. */
    @Test
    void aCommentWrittenInOneBodyLeavesEveryPlaceInTheFileWhereItWas() {
        SourceLayout before = SourceLayout.of(ORDERS, new SourceId("orders.sou"));
        SourceLayout after = SourceLayout.of(
                ORDERS.replace("let twice (n)", "// doubling\n\nlet twice (n)"),
                new SourceId("orders.sou"));

        assertEquals(placeOf(before, "doubled(n.value)"), placeOf(after, "doubled(n.value)"),
                "the body the comment was written above");
        assertEquals(placeOf(before, "n.value * 3"), placeOf(after, "n.value * 3"),
                "and the declaration after it");
    }

    /** Writing a construct moves the constructs after it, which is the conservative half. */
    @Test
    void aDeclarationWrittenInTheMiddleMovesThePlacesOfTheOnesAfterIt() {
        SourceLayout before = SourceLayout.of(ORDERS, new SourceId("orders.sou"));
        SourceLayout after = SourceLayout.of(
                ORDERS.replace("behavior thrice", "data Other = Int\n\nbehavior thrice"),
                new SourceId("orders.sou"));

        assertNotEquals(placeOf(before, "n.value * 3"), placeOf(after, "n.value * 3"),
                "`thrice` is one construct further down than it was");
        assertEquals(placeOf(before, "doubled(n.value)"), placeOf(after, "doubled(n.value)"),
                "and what was written above it is where it was");
    }

    /**
     * And the tree the front end builds out of it is the same tree, regions and all.
     *
     * <p>The three above ask the layout what it answers. This asks what is made of those answers,
     * because a region has two ends and the checks above compare places a node begins at. The end
     * is the half that can be got wrong on its own: the offset a token ends at is the offset the
     * next one starts at wherever nothing separates them, so an end read as an offset belonged to
     * whichever token came next and moved back onto its own the moment a space was written between
     * them. Nothing about the module had changed and every region closing on such a token was a
     * different value.
     *
     * <p>Compared as the whole parsed module, so that every place and every region the tree carries
     * is in the claim rather than the ones a test thought to name.
     */
    @Test
    void aSpaceWrittenBetweenTwoTokensLeavesTheParsedModuleThatSameValue() {
        String glued = ORDERS;
        String spaced = ORDERS.replace("let twice (n)", "let twice  ( n )")
                .replace("Amount(doubled(n.value))", "Amount( doubled(n.value) )");
        assertNotEquals(glued, spaced, "the two texts differ, or this compares a text to itself");

        assertEquals(parsed(glued), parsed(spaced),
                "a space between two tokens leaves every place and every region as it was");
        assertNotEquals(parsed(glued), parsed(glued.replace("n.value * 3", "n.value * 3 + 0")),
                "and a token written in does move what follows it, which is the conservative half");
    }

    /** The module the front end reads out of {@code source}, places and regions and all. */
    private static Ast.Module parsed(String source) {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("orders.sou", source);
        Compilation c = Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY);
        c.answerEverything();
        assertTrue(c.db().allReports().isEmpty(), () -> "the model parses: " + c.db().allReports());
        return c.db().ask(new Front.Parsed(new SourceId("orders.sou"))).value().module();
    }

    /** The place the first character of {@code written} is at in {@code laidOut}'s text. */
    private static SourcePos placeOf(SourceLayout laidOut, String written) {
        return laidOut.placeAt(laidOut.text().indexOf(written));
    }

    /** One behavior calling another, both requiring nothing. */
    private static final String CALLS = """
            module shop.orders exposing ( Amount )

            data Amount = Int
                invariant value >= 0

            behavior twice : (n: Amount) -> Amount
                constructs Amount
            let twice (n) = Amount(n.value * 2)

            behavior viaTwice : (n: Amount) -> Amount
            let viaTwice (n) = twice(n)
            """;

    private static Compilation orders(String source) {
        Compilation c = Compilation.ofDocuments(Map.of("orders.sou", source), Set.of(),
                ModulePath.EMPTY);
        c.answerEverything();
        assertTrue(c.db().allReports().isEmpty(), "the module compiles to begin with");
        return c;
    }

    /**
     * The same workspace with {@code twice}'s body changed — an edit inside one definition, written
     * where it moves no line of the definitions after it.
     *
     * <p>{@code twice} and not {@code thrice}, and an operation more rather than a different number
     * written in one. What the two tests below hold is that a body is none of the business of the
     * one beside it, and the way that used to fail was a count over the whole file: an edit that
     * wrote one construct more renumbered every construct after it. Both halves are needed to reach
     * it — an edit to the last definition has nothing after it to renumber, and an edit that leaves
     * the count where it was renumbers nothing.
     */
    private static Map<String, String> twiceOver(String written) {
        return Map.of("orders.sou", ORDERS.replace("doubled(n.value)", written));
    }

    /**
     * A body is expanded from itself and the helpers around it, so what another body in the same
     * module says is none of its business. Expanding a module's bodies as one tree made every
     * behavior depend on every other one, so editing any body in a file re-expanded all of them.
     */
    @Test
    void editingOneBodyExpandsThatBodyAndNotTheOnesBesideIt() {
        Compilation c = orders(ORDERS);
        Answer<?> twice = c.db().ask(new Bodies.LoweredBody("shop.orders", new souther.compiler.ast.DefinitionName("twice")));
        Answer<?> thrice = c.db().ask(new Bodies.LoweredBody("shop.orders", new souther.compiler.ast.DefinitionName("thrice")));

        c.update(twiceOver("doubled(n.value + 0)"), Set.of());
        c.answerEverything();

        assertNotSame(twice, c.db().ask(new Bodies.LoweredBody("shop.orders", new souther.compiler.ast.DefinitionName("twice"))),
                "the edit is `twice`'s, so it is expanded again");
        assertSame(thrice, c.db().ask(new Bodies.LoweredBody("shop.orders", new souther.compiler.ast.DefinitionName("thrice"))),
                "`thrice` says what it said, and `twice` is not part of it");
    }

    /**
     * And a body is checked against the behavior it implements and what the module around it means,
     * so a mistake made in another body — or its being edited at all — is none of its business.
     */
    @Test
    void editingOneBodyChecksThatBodyAndNotTheOnesBesideIt() {
        Compilation c = orders(ORDERS);
        Answer<?> twice = c.db().ask(new Bodies.CheckedBehavior("shop.orders", "twice"));
        Answer<?> thrice = c.db().ask(new Bodies.CheckedBehavior("shop.orders", "thrice"));

        c.update(twiceOver("doubled(n.value + 0)"), Set.of());
        c.answerEverything();

        assertNotSame(twice, c.db().ask(new Bodies.CheckedBehavior("shop.orders", "twice")),
                "the edit is `twice`'s, so it is checked again");
        assertSame(thrice, c.db().ask(new Bodies.CheckedBehavior("shop.orders", "thrice")),
                "`thrice` is checked against `behavior thrice`, which says what it said");
    }

    /**
     * A body that calls a behavior reads the callee's declaration and not its body, so editing what
     * the callee does leaves the caller's check alone. This is the difference between calling a
     * behavior and calling a helper: a helper is expanded into its callers, so editing one reaches
     * them (below); a behavior is built and called, so only its signature is the caller's business.
     */
    @Test
    void editingACalledBehaviorsBodyDoesNotRecheckTheCaller() {
        Compilation c = orders(CALLS);
        Answer<?> callee = c.db().ask(new Bodies.CheckedBehavior("shop.orders", "twice"));
        Answer<?> caller = c.db().ask(new Bodies.CheckedBehavior("shop.orders", "viaTwice"));

        c.update(Map.of("orders.sou", CALLS.replace("n.value * 2)", "n.value * 20)")), Set.of());
        c.answerEverything();

        assertNotSame(callee, c.db().ask(new Bodies.CheckedBehavior("shop.orders", "twice")),
                "the edit is `twice`'s, so it is checked again");
        assertSame(caller, c.db().ask(new Bodies.CheckedBehavior("shop.orders", "viaTwice")),
                "`viaTwice` was checked against `behavior twice`, which says what it said");
    }

    /**
     * A helper is expanded into the bodies that call it, so editing one reaches them. It reaches the
     * ones that do not call it as well: a body reads the module's helpers, not the helpers it calls,
     * and narrowing that is per-helper work rather than per-body work.
     */
    @Test
    void editingAHelperReachesTheBodiesItIsExpandedInto() {
        Compilation c = orders(ORDERS);
        Answer<?> twice = c.db().ask(new Bodies.LoweredBody("shop.orders", new souther.compiler.ast.DefinitionName("twice")));

        c.update(Map.of("orders.sou", ORDERS.replace("let doubled (n) = n * 2",
                "let doubled (n) = n * 2 + 0")), Set.of());
        c.answerEverything();

        assertNotSame(twice, c.db().ask(new Bodies.LoweredBody("shop.orders", new souther.compiler.ast.DefinitionName("twice"))),
                "`twice` calls `doubled`, and `doubled` is part of what `twice` becomes");
    }

    @Test
    void changingWhatAModuleDeclaresReachesTheModulesThatImportIt() {
        Compilation c = started();
        Answer<?> cart = c.db().ask(new Output.Classes("shop.cart"));

        c.update(workspace("""
                module shop.prices exposing ( Amount )

                data Amount = Int
                    invariant value >= 1
                """, CART, CUSTOMERS), Set.of());
        c.answerEverything();

        assertNotSame(cart, c.db().ask(new Output.Classes("shop.cart")),
                "the imported type's invariant is part of what the importer builds against");
    }

    @Test
    void settingASourceToWhatItAlreadySaidCostsNothing() {
        Compilation c = started();
        Answer<?> cart = c.db().ask(new Output.Classes("shop.cart"));

        c.update(workspace(PRICES, CART, CUSTOMERS), Set.of());
        c.answerEverything();

        assertSame(cart, c.db().ask(new Output.Classes("shop.cart")));
    }

    @Test
    void aProblemThatWasFixedStopsBeingReported() {
        Compilation c = Compilation.ofDocuments(
                workspace(PRICES, CART, "module shop.customers\ndata Name = Nope\n"), Set.of(),
                ModulePath.EMPTY);
        c.answerEverything();
        assertTrue(c.db().allReports().stream().anyMatch(f -> f.report().isError()),
                "an unknown type is an error to begin with");

        c.update(workspace(PRICES, CART, CUSTOMERS), Set.of());
        c.answerEverything();

        assertTrue(c.db().allReports().isEmpty(), "the fixed error is not still listed");
    }

    @Test
    void aClosedDocumentIsForgotten() {
        Compilation c = started();
        assertTrue(c.db().isComputed(new Output.Classes("shop.customers")));

        Map<String, String> without = new LinkedHashMap<>();
        without.put("prices.sou", PRICES);
        without.put("cart.sou", CART);
        c.update(without, Set.of());
        c.answerEverything();

        assertFalse(c.db().isComputed(new Output.Classes("shop.customers")),
                "nothing will ask about a module that is not there any more");
        assertFalse(c.db().isComputed(new Front.Parsed(new SourceId("customers.sou"))),
                "nor is its parse tree");
        assertFalse(c.db().isComputed(new Front.Text(new SourceId("customers.sou"))),
                "nor its text");
        assertEquals(List.of("shop.prices", "shop.cart"), c.modules());
    }

    // --- what a body's check depends on of the contracts around it -------------------------------

    /**
     * Two behaviors stating a relation, and a third calling one of them. The two clauses are
     * written differently so that either can be edited on its own. The caller is written first and
     * the two it may call after it, so that an edit to either of those moves the other without
     * moving the caller — which is the arrangement the question is about.
     */
    private static final String STATING = """
            module shop.orders exposing ( Amount )

            data Amount = Int
                invariant value >= 0

            behavior caller : (n: Amount) -> Amount
            let caller (n) = called(n)

            behavior uncalled : (n: Amount) -> Amount
                constructs Amount
                ensures value.value > n.value - 1
            let uncalled (n) = Amount(n.value * 3)

            behavior called : (n: Amount) -> Amount
                constructs Amount
                ensures value.value >= n.value
            let called (n) = Amount(n.value * 2)
            """;

    private static Compilation stating() {
        Compilation c = Compilation.ofDocuments(Map.of("orders.sou", STATING), Set.of(),
                ModulePath.EMPTY);
        c.answerEverything();
        assertTrue(c.db().allReports().isEmpty(), "the module compiles to begin with");
        return c;
    }

    /**
     * What a caller may assume of an answer is what the behavior it called declared, so a relation
     * declared by a behavior it does not call is none of its business. Handing a body every contract
     * its module can see made each of them depend on all of them, so editing any `ensures` in a file
     * re-checked every body in it.
     */
    @Test
    void editingAnEnsuresDoesNotRecheckABodyThatDoesNotCallIt() {
        Compilation c = stating();
        Answer<?> caller = c.db().ask(new Bodies.CheckedBehavior("shop.orders", "caller"));

        c.update(Map.of("orders.sou", STATING.replace(
                "ensures value.value > n.value - 1", "ensures value.value >= n.value")), Set.of());
        c.answerEverything();

        assertSame(caller, c.db().ask(new Bodies.CheckedBehavior("shop.orders", "caller")),
                "`caller` calls `called`, and what `uncalled` states is no part of that");
    }

    /** And the other way round: a relation the body did call for is what it was checked against. */
    @Test
    void editingAnEnsuresRechecksTheBodiesThatCallIt() {
        Compilation c = stating();
        Answer<?> caller = c.db().ask(new Bodies.CheckedBehavior("shop.orders", "caller"));

        c.update(Map.of("orders.sou", STATING.replace(
                "ensures value.value >= n.value", "ensures value.value > n.value - 1")), Set.of());
        c.answerEverything();

        assertNotSame(caller, c.db().ask(new Bodies.CheckedBehavior("shop.orders", "caller")),
                "`caller` took what `called` states about its answer");
    }

    /** A module declaring two behaviors that state a relation, both of them borrowed below. */
    private static final String CALC = """
            module lib.calc exposing ( Amount, doubled, tripled )

            data Amount = Int
                invariant value >= 0

            behavior tripled : (n: Amount) -> Amount
                constructs Amount
                ensures value.value > n.value - 1
            let tripled (n) = Amount(n.value * 3)

            behavior doubled : (n: Amount) -> Amount
                constructs Amount
                ensures value.value >= n.value
            let doubled (n) = Amount(n.value * 2)
            """;

    /** One body per borrowed behavior, so both imports are named and neither body names both. */
    private static final String USES = """
            module app.use exposing ( Out )

            import lib.calc ( Amount, doubled, tripled )

            data Out = Int
                invariant value >= 0

            behavior viaDoubled : (n: Amount) -> Out
                constructs Out
            let viaDoubled (n) = Out(doubled(n).value)

            behavior viaTripled : (n: Amount) -> Out
                constructs Out
            let viaTripled (n) = Out(tripled(n).value)
            """;

    private static Map<String, String> borrowing(String calc) {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("calc.sou", calc);
        byId.put("use.sou", USES);
        return byId;
    }

    private static Compilation borrowing() {
        Compilation c = Compilation.ofDocuments(borrowing(CALC), Set.of(), ModulePath.EMPTY);
        c.answerEverything();
        assertTrue(c.db().allReports().isEmpty(), "the workspace compiles to begin with");
        return c;
    }

    /**
     * The same across a module boundary, which is where it is felt: a library behavior's `ensures`
     * is read by whoever calls it, so editing one re-checked every body of every module that
     * imported anything from there.
     */
    @Test
    void editingABorrowedEnsuresDoesNotRecheckABodyThatDoesNotCallIt() {
        Compilation c = borrowing();
        Answer<?> viaDoubled = c.db().ask(new Bodies.CheckedBehavior("app.use", "viaDoubled"));

        c.update(borrowing(CALC.replace(
                "ensures value.value > n.value - 1", "ensures value.value >= n.value")), Set.of());
        c.answerEverything();

        assertSame(viaDoubled, c.db().ask(new Bodies.CheckedBehavior("app.use", "viaDoubled")),
                "`viaDoubled` borrows `tripled` as its module does, and calls `doubled`");
    }

    @Test
    void editingABorrowedEnsuresRechecksTheBodiesThatCallIt() {
        Compilation c = borrowing();
        Answer<?> viaTripled = c.db().ask(new Bodies.CheckedBehavior("app.use", "viaTripled"));

        c.update(borrowing(CALC.replace(
                "ensures value.value > n.value - 1", "ensures value.value >= n.value")), Set.of());
        c.answerEverything();

        assertNotSame(viaTripled, c.db().ask(new Bodies.CheckedBehavior("app.use", "viaTripled")),
                "`viaTripled` took what `tripled` states about its answer");
    }

    /**
     * And an edit above a called behavior that states nothing at all. A contract carries where its
     * terms were written and the ordinals its module numbered them with, so a blank line above the
     * declaration moves the first and a clause above it gaining a term moves the second — both of
     * which reach every caller through a value nobody reads either of them from.
     */
    @Test
    void aBlankLineAboveACalledBehaviorDoesNotRecheckItsCallers() {
        Compilation c = stating();
        Answer<?> caller = c.db().ask(new Bodies.CheckedBehavior("shop.orders", "caller"));

        c.update(Map.of("orders.sou",
                STATING.replace("let uncalled (n) = Amount(n.value * 3)\n",
                        "let uncalled (n) = Amount(n.value * 3)\n\n")), Set.of());
        c.answerEverything();

        assertSame(caller, c.db().ask(new Bodies.CheckedBehavior("shop.orders", "caller")),
                "`called` says what it said, one line further down the file");
    }

    /** The same across a module boundary, where the caller cannot see the line at all. */
    @Test
    void aBlankLineAboveABorrowedBehaviorDoesNotRecheckItsCallers() {
        Compilation c = borrowing();
        Answer<?> viaDoubled = c.db().ask(new Bodies.CheckedBehavior("app.use", "viaDoubled"));

        c.update(borrowing(CALC.replace("let tripled (n) = Amount(n.value * 3)\n",
                "let tripled (n) = Amount(n.value * 3)\n\n")), Set.of());
        c.answerEverything();

        assertSame(viaDoubled, c.db().ask(new Bodies.CheckedBehavior("app.use", "viaDoubled")),
                "`doubled` says what it said, one line further down a file this module never reads");
    }

    /** A behavior reached by being injected rather than by being built and called. */
    private static final String INJECTING = """
            module shop.injecting exposing ( Amount, Out, findIt )

            data Amount = Int
                invariant value >= 0

            data Out = Int
                invariant value >= 0

            behavior findIt : (n: Amount) -> Amount
                ensures value.value >= n.value

            behavior use : (n: Amount) -> Out
                depends on findIt
                constructs Out
            let use (n, findIt) = Out(findIt(n).value)

            behavior beside : (n: Amount) -> Out
                constructs Out
            let beside (n) = Out(n.value)
            """;

    /**
     * An injected behavior is called too, and what it states reaches the body it is injected into.
     * The two ways a behavior is reached are a difference in how a call is typed and not in whose
     * contract is read, so the frontier a body depends on cannot be the callable behaviors alone.
     */
    @Test
    void editingAnInjectedEnsuresRechecksTheBodyItIsInjectedIntoAndNothingBeside() {
        Compilation c = Compilation.ofDocuments(Map.of("injecting.sou", INJECTING), Set.of(),
                ModulePath.EMPTY);
        c.answerEverything();
        assertTrue(c.db().allReports().isEmpty(), "the module compiles to begin with");
        Answer<?> use = c.db().ask(new Bodies.CheckedBehavior("shop.injecting", "use"));
        Answer<?> beside = c.db().ask(new Bodies.CheckedBehavior("shop.injecting", "beside"));

        c.update(Map.of("injecting.sou", INJECTING.replace(
                "ensures value.value >= n.value", "ensures value.value > n.value - 1")), Set.of());
        c.answerEverything();

        assertNotSame(use, c.db().ask(new Bodies.CheckedBehavior("shop.injecting", "use")),
                "`use` took what `findIt` states about its answer");
        assertSame(beside, c.db().ask(new Bodies.CheckedBehavior("shop.injecting", "beside")),
                "`beside` calls nothing, so nothing states anything to it");
    }

    /**
     * What the names written in a module mean is what a body is checked in, and declaring a behavior
     * does not change it: a behavior is a value and not a type, so no spelling here means anything
     * it did not mean before.
     *
     * <p>The whole assembly does change — the value namespace gains a name — and reading a scope off
     * the assembly is what made that edit arrive at every reader of what the names mean. What is
     * asked for here is the part of it that answers this question.
     */
    @Test
    void declaringABehaviorDoesNotChangeWhatTheNamesOfTheModuleMean() {
        Compilation c = stating();
        Answer<?> meant = c.db().ask(new Names.Meanings("shop.orders"));
        Answer<?> assembled = c.db().ask(new Names.ModuleScope("shop.orders"));

        c.update(Map.of("orders.sou", STATING + """

                behavior added : (n: Amount) -> Amount
                    constructs Amount
                let added (n) = Amount(n.value * 4)
                """), Set.of());
        c.answerEverything();

        assertEquals(meant, c.db().ask(new Names.Meanings("shop.orders")),
                "`added` is a name in the value namespace and no type is spelled differently");
        assertNotEquals(assembled, c.db().ask(new Names.ModuleScope("shop.orders")),
                "and the assembly it is read off did change, which is why it is not read off it");
    }

    /**
     * Declaring a behavior is none of the business of the bodies beside it. A body reads what the
     * names of its module mean and the signatures of what it calls; the first does not change when a
     * value name is added, and the second is what this body names rather than what the module has
     * callable in it. Both were read as whole tables, so adding a behavior nothing calls reached
     * every body in the file — issue #829.
     */
    @Test
    void declaringABehaviorDoesNotRecheckTheBodiesBesideIt() {
        Compilation c = stating();
        Answer<?> caller = c.db().ask(new Bodies.CheckedBehavior("shop.orders", "caller"));

        c.update(Map.of("orders.sou", STATING + """

                behavior added : (n: Amount) -> Amount
                    constructs Amount
                let added (n) = Amount(n.value * 4)
                """), Set.of());
        c.answerEverything();

        assertSame(caller, c.db().ask(new Bodies.CheckedBehavior("shop.orders", "caller")),
                "`caller` neither calls `added` nor names anything of it");
    }

    /**
     * Declaring a type is none of the business of the bodies that do not write it. What a body is
     * checked in is still everything its module's names mean — the scope is the module's, and no
     * narrower — and what changed is when that is asked for: {@link souther.compiler.check.Denoting}
     * is asked as the scope is read rather than fetched to build it, so a body whose check reads no
     * meaning depends on none of them.
     *
     * <p>Issue #835, and what was left of #829. Measured before it was built: over this suite a body
     * check reads a module's meanings once in 5446 checks, and that once is the report below.
     */
    @Test
    void declaringADataDoesNotRecheckTheBodiesBesideIt() {
        Compilation c = stating();
        Answer<?> caller = c.db().ask(new Bodies.CheckedBehavior("shop.orders", "caller"));

        c.update(Map.of("orders.sou", STATING + """

                data Discount = Int
                """), Set.of());
        c.answerEverything();

        assertSame(caller, c.db().ask(new Bodies.CheckedBehavior("shop.orders", "caller")),
                "`Discount` is a spelling `caller` does not write");
    }

    /**
     * And the other half of it. A body that writes the spelling is checked again, because what the
     * spelling means is what changed — it meant nothing there and now denotes a declaration. The
     * pair is what says the dependency was narrowed and not dropped: a cut carrying only the
     * spellings that already resolved would leave this one alone and be wrong to.
     */
    @Test
    void declaringADataRechecksTheBodyThatWritesItsSpelling() {
        Compilation c = Compilation.ofDocuments(Map.of("orders.sou", STATING + """

                behavior writing : (n: Amount) -> Amount
                    constructs Amount
                let writing (n) = Amount(Discount(n.value).value)
                """), Set.of(), ModulePath.EMPTY);
        c.answerEverything();
        assertFalse(c.db().allReports().isEmpty(), "`Discount` denotes nothing to begin with");
        Answer<?> writing = c.db().ask(new Bodies.CheckedBehavior("shop.orders", "writing"));
        Answer<?> caller = c.db().ask(new Bodies.CheckedBehavior("shop.orders", "caller"));

        c.update(Map.of("orders.sou", STATING + """

                data Discount = Int

                behavior writing : (n: Amount) -> Amount
                    constructs Amount
                let writing (n) = Amount(Discount(n.value).value)
                """), Set.of());
        c.answerEverything();

        assertNotSame(writing, c.db().ask(new Bodies.CheckedBehavior("shop.orders", "writing")),
                "`writing` names `Discount`, which meant nothing here and now denotes a declaration");
        assertSame(caller, c.db().ask(new Bodies.CheckedBehavior("shop.orders", "caller")),
                "and `caller` beside it still does not write it");
    }

    /**
     * A scope answers from what the module means now, however long the thing holding it has been
     * held.
     *
     * <p>A scope is built inside the question that reads it and goes no further, which is the rule
     * and not something a scope can check. What it can do is not depend on it: what a name means is
     * asked of the store each time and the reading built over that answer is kept only while it is
     * the answer, so a scope kept past its question is slower and not wrong. Held instead from the
     * first read, it would answer from that read for as long as it lived, and whether that could be
     * seen would be a fact about who kept a scope rather than one this module can be sure of.
     *
     * <p>So this test keeps one on purpose, which nothing in the compiler does.
     */
    @Test
    void aScopeHeldPastTheQuestionItWasBuiltForStillAnswersFromWhatTheModuleMeansNow() {
        Compilation c = stating();
        Symbols held = Scopes.derived(c.db(), "shop.orders").value();
        assertFalse(held.scope().inScope("Discount"), "nothing here is written that way yet");

        c.update(Map.of("orders.sou", STATING + """

                data Discount = Int
                """), Set.of());
        c.answerEverything();

        assertTrue(held.scope().inScope("Discount"),
                "the same scope, asked again after the declaration arrived");
    }

    /** A body whose report offers the sum an arm's name does belong to (E1203). What that report
     * says is worked out from every name in sight, so this is the one body check that reads what a
     * module's names mean. */
    private static final String OFFERING = """
            module shop.offering exposing ( Out, run )

            data A = { n: Int }
            data B = { n: Int }
            data Inner = A | B
            data Q
            data Outer = Inner | Q
            data Out = { n: Int }

            behavior run : (i: Inner) -> Out
                constructs Out
            let run (i) =
                match i with
                    | A { n } -> Out { n = n }
                    | Q -> Out { n = 0 }
            """;

    /**
     * The other side of {@link #declaringADataDoesNotRecheckTheBodiesBesideIt}, and what says the
     * dependency was moved rather than dropped. This body's report names the sum `Q` is a case of,
     * which is read off every name in sight — so declaring a type does change what this check
     * answers, and the check depends on it because it read it.
     *
     * <p>Written as a pair with the one above because either alone is passed by a mistake. A
     * dependency taken whenever a scope is built passes the first and not this one; a dependency
     * dropped altogether passes this one and not the first — and nothing else here would see it,
     * because what the report says is checked where reports are read and not where an edit is
     * costed.
     */
    @Test
    void declaringADataRechecksTheBodyWhoseReportIsReadOffEveryNameInSight() {
        Compilation c = Compilation.ofDocuments(Map.of("offering.sou", OFFERING), Set.of(),
                ModulePath.EMPTY);
        c.answerEverything();
        assertFalse(c.db().allReports().isEmpty(), "`Q` is not a case of `Inner`, which is reported");
        Answer<?> run = c.db().ask(new Bodies.CheckedBehavior("shop.offering", "run"));

        c.update(Map.of("offering.sou", OFFERING + "\ndata Discount = Int\n"), Set.of());
        c.answerEverything();

        assertNotSame(run, c.db().ask(new Bodies.CheckedBehavior("shop.offering", "run")),
                "what this body's report offers is read off every name in sight, `Discount` among "
                        + "them");
    }

    /**
     * And moving one. What a body is checked against is what the declarations say, so the order they
     * are written in is not part of it — a signature that arrived at a caller carrying where it was
     * written, or the position its module numbered it at, would reach every caller on an edit above
     * it.
     */
    @Test
    void reorderingTheDeclarationsDoesNotRecheckTheBodiesBesideThem() {
        Compilation c = stating();
        Answer<?> caller = c.db().ask(new Bodies.CheckedBehavior("shop.orders", "caller"));

        String uncalled = """
                behavior uncalled : (n: Amount) -> Amount
                    constructs Amount
                    ensures value.value > n.value - 1
                let uncalled (n) = Amount(n.value * 3)

                """;
        String called = """
                behavior called : (n: Amount) -> Amount
                    constructs Amount
                    ensures value.value >= n.value
                let called (n) = Amount(n.value * 2)
                """;
        assertTrue(STATING.contains(uncalled) && STATING.endsWith(called),
                "the fixture is the two declarations in this order");
        c.update(Map.of("orders.sou",
                STATING.replace(uncalled, "").replace(called, called + "\n" + uncalled.strip() + "\n")),
                Set.of());
        c.answerEverything();

        assertSame(caller, c.db().ask(new Bodies.CheckedBehavior("shop.orders", "caller")),
                "`called` takes what it took, wherever in the file it is written");
    }

    /** And the other way round: editing what a called behavior takes is what its callers read. */
    @Test
    void changingWhatACalledBehaviorTakesRechecksTheBodiesThatCallIt() {
        Compilation c = stating();
        Answer<?> caller = c.db().ask(new Bodies.CheckedBehavior("shop.orders", "caller"));

        c.update(Map.of("orders.sou", STATING.replace(
                "behavior called : (n: Amount) -> Amount",
                "behavior called : (n: Amount, m: Amount) -> Amount").replace(
                "let called (n) = Amount(n.value * 2)",
                "let called (n, m) = Amount(n.value * 2 + m.value)").replace(
                "let caller (n) = called(n)", "let caller (n) = called(n, n)")), Set.of());
        c.answerEverything();

        assertNotSame(caller, c.db().ask(new Bodies.CheckedBehavior("shop.orders", "caller")),
                "`caller` calls `called`, so what `called` takes is what it is typed against");
    }
}
