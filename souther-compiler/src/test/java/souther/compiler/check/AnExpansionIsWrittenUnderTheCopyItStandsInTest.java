package souther.compiler.check;

import souther.compiler.DefaultStdlib;
import souther.compiler.ast.Ast;
import souther.compiler.ast.DefinitionName;
import souther.compiler.ast.Hir;
import souther.compiler.frontend.CstFrontend;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.ApplicationOrigin;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A copy of a body is written under the copy it stands in, all the way down.
 *
 * <p>One helper called at two places is two copies, and everything inside each of them belongs to
 * the copy it is in. So a call written once inside that helper is one call the author wrote and two
 * expansions in the tree that runs — the same site under two different parents — and the bindings
 * each of them writes are its own.
 *
 * <p><b>Told by what the source wrote and never by the order the copies were made.</b> Which calls a
 * pass expands is what the policy it runs under decides, so a count over the expansions of one body
 * runs differently in the tree a backend emits and the tree an analysis reads. What is here comes
 * from the source: which application it is, and which copy it is inside.
 *
 * <p>Both halves are checked, and neither says anything alone. That two expansions of one call are
 * different is only interesting while they agree about which call it is; that they agree about the
 * call is only a property while they are two.
 */
class AnExpansionIsWrittenUnderTheCopyItStandsInTest {

    /**
     * {@code top} calls {@code twice} at two places, and {@code twice} calls {@code bump} at two
     * places. So the tree holds four expansions of {@code bump} under two of {@code twice}, from
     * three calls the author wrote.
     */
    private static final String MODULE = """
            module demo

            data X = Int

            let bump (n: Int) : Int = n + 1
            let twice (n: Int) : Int = bump(n) + bump(n)
            let top (m: Int) : Int = twice(m) + twice(m)

            behavior f : (x: X) -> X
            let f (x) = x
            """;

    @Test
    void oneCallInsideAHelperExpandedTwiceIsOneCallAndTwoExpansions() {
        List<BindingOwner.Expansion> bumps = expansionsOf("bump");

        assertEquals(4, bumps.size(),
                "two calls of `bump`, inside a helper written into the body twice");
        assertEquals(2, bumps.stream().map(BindingOwner.Expansion::at).distinct().count(),
                "and two calls is what the author wrote, however many copies of them there are");
        assertEquals(4, bumps.stream().distinct().count(),
                "which is what the copy each stands in tells apart");
    }

    /** And what tells them apart is the copy, which is the other half of the same fact. */
    @Test
    void andWhatTellsThemApartIsTheCopyTheyStandIn() {
        List<BindingOwner.Expansion> twices = expansionsOf("twice");
        List<BindingOwner.Expansion> bumps = expansionsOf("bump");

        assertEquals(2, twices.size(), "the helper is written into the body twice");
        assertNotEquals(twices.get(0), twices.get(1),
                "and the two are two, because the author wrote two calls of it");
        assertTrue(bumps.stream().allMatch(each -> twices.contains(each.within())),
                "every call inside it belongs to one of those two copies: " + bumps);
    }

    /**
     * And an expansion owns what it wrote.
     *
     * <p>The half that would go missing if the owner an expansion says it is were worked out
     * anywhere but where its bindings are minted: the two would agree until one of them learned a
     * shape the other did not, and a copy would say it wrote into a place its bindings had not gone.
     */
    @Test
    void andAnExpansionOwnsTheBindingsItWrote() {
        List<String> astray = new ArrayList<>();
        eachExpansion(expanded(MODULE, "top"), ex -> ex.bound().forEach(bound -> {
            if (!ex.application().equals(bound.binder().id().owner())) {
                astray.add(bound.binder().name() + " of " + ex.callee()
                        + " belongs to " + bound.binder().id().owner()
                        + " and the expansion is " + ex.application());
            }
        }));

        assertEquals(List.of(), astray,
                "an expansion's arguments are bindings of that expansion");
    }

    /** Every application of {@code helper} in the expanded body, in the order the tree holds them. */
    private static List<BindingOwner.Expansion> expansionsOf(String helper) {
        return expansionsIn(expanded(MODULE, "top"), helper);
    }

    /** {@code helper} of {@code source}, with every helper it calls written into it. */
    private static Hir.Expr expanded(String source, String helper) {
        Ast.Module parsed = CstFrontend.parse(source);
        HelperInliner inliner = HelperInliner.forModule(
                Resolve.module(parsed, SyntaxSymbols.of(parsed, DefaultStdlib.get())),
                DefaultStdlib.get());
        return inliner.inline(
                inliner.held().get(new DefinitionName(helper)).definition().writtenBody(),
                inliner.bodyOf(helper));
    }

    private static void eachExpansion(Hir.Expr e, java.util.function.Consumer<Hir.Expansion> at) {
        if (e instanceof Hir.Expansion ex) {
            at.accept(ex);
        }
        Hir.forEachChild(e, child -> eachExpansion(child, at));
    }

    /** That the model under test is one this is about: a site the author wrote once, standing in
     *  two copies, is what every claim above is over. */
    @Test
    void andTheModelUnderTestWritesTheShapeThisIsAbout() {
        assertTrue(expansionsOf("bump").stream().map(BindingOwner.Expansion::at)
                        .allMatch(ApplicationOrigin.Written.class::isInstance),
                "the calls under test are ones the source wrote");
    }

    /**
     * {@code mapped} holds a call whose expansion is already built by the time {@code mapped} is
     * itself written into a body, and it is written into one twice.
     *
     * <p>The other way a copy is made. Above, a body is copied and the calls in it are expanded
     * afterwards; here the body already holds the expansion and the copy carries it. A copy that
     * kept what it carried would have the two copies say they wrote into one place, while the
     * bindings each of them made had gone to two.
     */
    private static final String ALREADY_EXPANDED = """
            module demo

            data X = Int

            let bump (n: Int) : Int = n + 1
            let mapped (xs: List<Int>) : List<Int> = List.map(bump, xs)
            let top (a: List<Int>, b: List<Int>) : List<Int> = mapped(a) ++ mapped(b)

            behavior f : (x: X) -> X
            let f (x) = x
            """;

    @Test
    void andACopyOfAnAlreadyBuiltExpansionIsWrittenUnderTheCopyToo() {
        Hir.Expr body = expanded(ALREADY_EXPANDED, "top");
        List<BindingOwner.Expansion> mappeds = expansionsIn(body, "mapped");
        List<BindingOwner.Expansion> maps = expansionsIn(body, "map");

        assertEquals(2, mappeds.size(),
                "the helper holding the call is written into the body twice");
        assertEquals(2, maps.size(), "so the call inside it stands twice");
        assertEquals(1, maps.stream().map(BindingOwner.Expansion::at).distinct().count(),
                "and it is one call the author wrote");
        assertTrue(maps.stream().allMatch(each -> mappeds.contains(each.within())),
                "each copy of it belongs to the copy of the helper it stands in: " + maps);
        assertEquals(2, maps.stream().distinct().count(),
                "so the two are two, told apart by that and by nothing else");
    }

    /** And the bindings a copied expansion made went where it says it wrote them. */
    @Test
    void andACopiedExpansionOwnsWhatItWrote() {
        List<String> astray = new ArrayList<>();
        eachExpansion(expanded(ALREADY_EXPANDED, "top"), ex -> ex.bound().forEach(bound -> {
            if (!ex.application().equals(bound.binder().id().owner())) {
                astray.add(bound.binder().name() + " of " + ex.callee()
                        + " belongs to " + bound.binder().id().owner()
                        + " and the expansion is " + ex.application());
            }
        }));

        assertEquals(List.of(), astray,
                "a copy of an expansion owns the bindings that copy wrote");
    }

    private static List<BindingOwner.Expansion> expansionsIn(Hir.Expr body, String helper) {
        List<BindingOwner.Expansion> out = new ArrayList<>();
        eachExpansion(body, ex -> {
            if (ex.callee().name().equals(helper)
                    && ex.application() instanceof BindingOwner.Expansion at) {
                out.add(at);
            }
        });
        return out;
    }
}
