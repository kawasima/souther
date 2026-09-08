package souther.compiler.coverage;

import org.junit.jupiter.api.Test;

import souther.compiler.core.Core;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.types.BindingId;
import souther.compiler.types.BindingOwner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Re-issuing the numbers the compiler minted for a body's names leaves what the body does alone.
 *
 * <p>The one thing {@link ExecutableIdentity} exists to be. A {@code BindingId} is an owner and a
 * count over that owner's bindings, and the count is minted while the body is being built. So a
 * reading that carried it would say two builds of one source built different things as soon as the
 * inliner allocated differently — which is the dependency the whole of this is moving off, not a
 * dependency to move one level down.
 *
 * <p>The owner is a different matter and is held to more. An expansion is named by the call it
 * expands and by what it is inside, both of which the source settles, so it does not move with the
 * order the copies were made in at all — and a pass's own bindings are numbered per pass and per
 * owner, which this moves.
 *
 * <p>So the numbers are re-issued here and the answer must not move. Done to the bindings a body
 * binds, which are the ones minted: a parameter is bound by the signature, in the order it was
 * written, and moving one of those is a different body — which the last test below is.
 *
 * <p>The rewrite is a fixture and not a pass. What it does to the tree is what a differently
 * allocating inliner would have left, and nothing downstream sees it.
 */
class WhatABodyDoesDoesNotMoveWithTheNumbersMintedForItsNamesTest {

    private static final String SPLICED = """
            module demo

            let picked (n: Int, cap: Int): List<Int> = {
                let ceiling = cap + 1
                [ n | n >= 240, n <= ceiling ]
            }

            behavior over : (a: Int, b: Int) -> List<Int>
            let over (a, b) = picked(a, b) ++ picked(b, a)
            """;

    private static final String SHAPES = """
            module demo

            data Warm = Int invariant hot = value >= 240
            data Low
            data UnderThirty
            data ThirtyOrOver
            data Band = UnderThirty | ThirtyOrOver

            behavior attempt : (t: Int) -> Warm | Low
                constructs Warm
            let attempt (t) = if Warm(t) as w then w else Low

            behavior matched : (band: Band, n: Int) -> Int
            let matched (band, n) =
                match band with
                    | UnderThirty -> n + 1
                    | ThirtyOrOver -> n + 2

            behavior kept : (xs: List<Int>) -> List<Int>
            let kept (xs) = List.filter(x -> x >= 240, xs)
            """;

    @Test
    void reIssuingTheNumbersMintedForABodysNamesLeavesWhatItDoesAlone() {
        int reIssued = 0;
        for (String source : List.of(SPLICED, SHAPES)) {
            for (Body body : bodiesOf(source)) {
                // A body that binds nothing has nothing to re-issue and is still a body this holds
                // of. What would make the whole of it say nothing is no body binding anything,
                // which is counted below rather than demanded of each.
                Map<BindingId, BindingId> again = reIssued(body.body());
                Core moved = renamed(body.body(), again);

                assertEquals(identityOf(body.module(), body.behavior(), body.body()),
                        identityOf(body.module(), body.behavior(), moved),
                        () -> "what `" + body.behavior() + "` does, after its names were minted"
                                + " again");
                reIssued += again.size();
            }
        }
        assertTrue(reIssued > 0, "no name was re-issued at all, so this says nothing");
    }

    /** And two compiles of one source, which is the same claim about the ordinary route. */
    @Test
    void twoCompilesOfOneSourceDoTheSameThing() {
        for (String source : List.of(SPLICED, SHAPES)) {
            assertEquals(identitiesOf(source), identitiesOf(source),
                    "what a source's bodies do does not move between two compiles of it");
        }
    }

    /**
     * And it is not equality on everything: a body reading another parameter does another thing.
     *
     * <p>The control the test above needs. Re-issuing a name leaves the answer alone because a name
     * is written as where it is bound; a parameter is bound by the signature at the place the
     * signature put it, so reading the second where the first was read is a different body — and an
     * identity that shrugged at that would shrug at the rest.
     */
    @Test
    void aBodyThatReadsAnotherParameterDoesAnotherThing() {
        Body matched = bodiesOf(SHAPES).stream()
                .filter(each -> each.behavior().equals("matched")).findFirst().orElse(null);
        assertNotNull(matched, "the model under test has the behavior this is about");

        BindingOwner.OfValue signature = new BindingOwner.OfValue(matched.module(), "matched");
        Map<BindingId, BindingId> swapped = new LinkedHashMap<>();
        swapped.put(new BindingId(signature, 0), new BindingId(signature, 1));
        swapped.put(new BindingId(signature, 1), new BindingId(signature, 0));

        assertNotEquals(identityOf(matched.module(), matched.behavior(), matched.body()),
                identityOf(matched.module(), matched.behavior(),
                        renamed(matched.body(), swapped)),
                "a body reading its other parameter is not the body that reads this one");
    }

    // --- the fixture ------------------------------------------------------------------------

    private static ExecutableIdentity identityOf(String module, String behavior, Core body) {
        return ExecutableIdentity.of(body,
                Binders.of(module, NodeAddresses.of(behavior, body)));
    }

    private static List<ExecutableIdentity> identitiesOf(String source) {
        List<ExecutableIdentity> out = new ArrayList<>();
        bodiesOf(source).forEach(body ->
                out.add(identityOf(body.module(), body.behavior(), body.body())));
        return out;
    }

    /** A new number for every name {@code body} binds, of a new copy as well as a new count — which
     *  is what an inliner that had allocated differently would have left. */
    private static Map<BindingId, BindingId> reIssued(Core body) {
        Map<BindingId, BindingId> out = new LinkedHashMap<>();
        binders(body, binder -> {
            if (binder != null && binder.binding() != null) {
                out.computeIfAbsent(binder.binding(),
                        id -> new BindingId(elsewhere(id.owner()), id.ordinal() + 500));
            }
        });
        return out;
    }

    /**
     * The same owner, minted at another point in a run that made the copies in another order.
     *
     * <p>An expansion comes back as itself, and that is not this fixture declining to move it.
     * There is no number a run mints for it: it is named by the call it expands, which the source
     * settled, and by what it is inside. A run that made the copies in another order names them the
     * same way.
     */
    private static BindingOwner elsewhere(BindingOwner owner) {
        return switch (owner) {
            case BindingOwner.Synthesized it ->
                    new BindingOwner.Synthesized(it.within(), it.pass(), it.ordinal() + 500);
            default -> owner;
        };
    }

    private static void binders(Core e, Consumer<Core.Binder> at) {
        switch (e) {
            case Core.LetIn it -> at.accept(it.binder());
            case Core.Block it -> it.params().forEach(at);
            case Core.Match it -> it.cases().forEach(one -> at.accept(one.binder()));
            case Core.IfConstructed it -> at.accept(it.binder());
            default -> { }
        }
        CoreStructure.childrenOf(e).forEach(child -> binders(child.node(), at));
    }

    /** {@code e} with every binding {@code subst} names replaced, binders and reads alike. */
    private static Core renamed(Core e, Map<BindingId, BindingId> subst) {
        Core rebuilt = Core.mapAll(e,
                child -> renamed(child, subst),
                read -> (Core.Read) renamedHere(read, subst));
        return renamedHere(rebuilt, subst);
    }

    /** The node's own bindings, the children having been done already. */
    private static Core renamedHere(Core e, Map<BindingId, BindingId> subst) {
        return switch (e) {
            case Core.Read it -> new Core.Read(it.name(), moved(it.binding(), subst), it.type(),
                    it.pos());
            case Core.LetIn it -> new Core.LetIn(moved(it.binder(), subst), it.value(), it.body(),
                    it.type(), it.pos());
            case Core.Block it -> new Core.Block(
                    it.params().stream().map(each -> moved(each, subst)).toList(),
                    it.body(), it.type(), it.pos());
            case Core.IfConstructed it -> new Core.IfConstructed(it.construct(),
                    moved(it.binder(), subst), it.then(), it.els(), it.place(), it.type(),
                    it.pos());
            case Core.Match it -> new Core.Match(it.scrutinee(),
                    it.cases().stream()
                            .map(one -> new Core.Case(one.pattern(), moved(one.binder(), subst),
                                    one.body(), one.pos()))
                            .toList(),
                    it.place(), it.type(), it.pos());
            default -> e;
        };
    }

    private static Core.Binder moved(Core.Binder binder, Map<BindingId, BindingId> subst) {
        return binder == null ? null
                : new Core.Binder(binder.name(), moved(binder.binding(), subst));
    }

    private static BindingId moved(BindingId id, Map<BindingId, BindingId> subst) {
        return id == null ? null : subst.getOrDefault(id, id);
    }

    private record Body(String module, String behavior, Core body) {}

    private static List<Body> bodiesOf(String source) {
        Compilation compilation = Compilation.ofSources(List.of(source), ModulePath.EMPTY);
        compilation.answerEverything();
        List<Body> out = new ArrayList<>();
        for (String module : compilation.modules()) {
            Bodies.Elaborated checked =
                    compilation.db().ask(new Bodies.Checked(module)).value();
            if (checked != null) {
                checked.behaviorBodies().forEach((behavior, body) ->
                        out.add(new Body(module, behavior, body)));
            }
        }
        assertTrue(!out.isEmpty(),
                () -> "the model under test compiled to nothing: " + compilation.errors());
        return out;
    }
}
