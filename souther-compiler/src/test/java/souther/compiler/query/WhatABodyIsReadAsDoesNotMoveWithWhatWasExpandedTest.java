package souther.compiler.query;

import souther.compiler.core.Core;
import souther.compiler.types.BindingId;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.ApplicationOrigin;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A module's own helper is the same expansion in both readings of one body.
 *
 * <p>A body is read two ways. The tree a backend emits has the language's own operations expanded
 * into what they do; the tree an analysis reads has them standing. So one of the two expands strictly
 * more, and anything an expansion is named by that counts what a walk met would name the helper they
 * share differently in each — which is a fact about which reading is being looked at, offered as a
 * fact about the model.
 *
 * <p>So the two are compared — over what both of them hold. The model under test writes a call of
 * the language's own operations and hands its helper to one of them, so the helper stands twice: at
 * a call both readings have, and inside an operation's own body, which only one of them has.
 *
 * <p><b>What is held is not that the two trees are alike.</b> An expansion is what a copy of a body
 * actually is in the reading it stands in, and one reading has a subtree the other has not — so the
 * helper handed to an operation is somewhere else there, and saying otherwise would be a copy tree
 * describing a tree that is not the one it is in. What may not move is what both readings hold: a
 * call the author wrote is the same expansion in each.
 */
class WhatABodyIsReadAsDoesNotMoveWithWhatWasExpandedTest {

    private static final String MODULE = """
            module demo

            data X = Int

            let bump (n: Int) : Int = n + 1

            behavior go : (xs: List<Int>, m: Int) -> Int
            let go (xs, m) = List.length(List.map(bump, xs)) + bump(m)
            """;

    @Test
    void aCallBothReadingsHoldIsOneExpansionInEach() {
        Bodies.Elaborated checked = checked();

        Set<BindingOwner.Expansion> emitted =
                written(expansionsOf(checked.behaviorBodies().get("go"), "bump"));
        Set<BindingOwner.Expansion> analysed =
                written(expansionsOf(checked.analysisBodies().get("go").core(), "bump"));

        assertFalse(emitted.isEmpty(), "the model under test calls its own helper");
        assertEquals(analysed, emitted,
                "a call the author wrote is the same expansion, ancestry and all, in either"
                        + " reading of the body it is written in");
    }

    /**
     * And the helper handed to an operation stands where that reading puts it.
     *
     * <p>Not a difference to be smoothed over. One reading expanded the operation and the helper is
     * inside what it expanded to; the other left the operation standing and has no such body at all.
     * An expansion says what a copy of a body is in the reading it stands in, so the two are two —
     * and a check that asked them to agree would be asking one of the trees to describe the other.
     */
    @Test
    void andTheHelperHandedToAnOperationStandsWhereEachReadingPutsIt() {
        Bodies.Elaborated checked = checked();

        Set<BindingOwner.Expansion> emitted =
                eta(expansionsOf(checked.behaviorBodies().get("go"), "bump"));
        Set<BindingOwner.Expansion> analysed =
                eta(expansionsOf(checked.analysisBodies().get("go").core(), "bump"));

        assertEquals(1, emitted.size(), "the helper is handed to one operation");
        assertEquals(1, analysed.size(), "in both readings");
        assertNotEquals(analysed, emitted,
                "and the two stand in different places, which is what the readings differ by");
        assertTrue(under(emitted.iterator().next()).stream()
                        .anyMatch(each -> each instanceof BindingOwner.Expansion it
                                && it.expanded().name().equals("map")),
                "the emitted one is inside what the operation was expanded to: " + emitted);
        assertEquals(Set.of(), expansionsOf(checked.analysisBodies().get("go").core(), "map"),
                "and the analysis has no such body to be inside, rather than a shorter way there");
    }

    /**
     * And the readings are two, which is what makes the equality above a property.
     *
     * <p>Compared against a body the two readings agree about, the check would hold while nothing
     * was being held to anything. What differs is the language's own operations: one reading expands
     * them and the other leaves them standing.
     */
    @Test
    void andTheTwoReadingsAreNotTheSameTree() {
        Bodies.Elaborated checked = checked();

        assertEquals(Set.of(), expansionsOf(checked.analysisBodies().get("go").core(), "map"),
                "the analysis reads a tree where the language's own operations stand");
        assertFalse(expansionsOf(checked.behaviorBodies().get("go"), "map").isEmpty(),
                "and the backend emits one where they have been expanded");
    }

    /**
     * And what the helper's expansion is written under is the same in both.
     *
     * <p>Said apart from the equality above so that a difference is legible where it is. The
     * ancestry is what an expansion is told from its copies by, and its root is the writing that
     * placed it — which is numbered among the writings into a body rather than among the expansions
     * met while walking one.
     */
    @Test
    void andSoIsWhatItIsWrittenUnder() {
        Bodies.Elaborated checked = checked();

        assertEquals(
                rootsOf(written(expansionsOf(checked.analysisBodies().get("go").core(), "bump"))),
                rootsOf(written(expansionsOf(checked.behaviorBodies().get("go"), "bump"))),
                "a writing into a body is counted among the writings into it, and how much was"
                        + " expanded inside one is no part of that");
    }

    /** What each of {@code expansions} is written under, at the bottom of the chain. */
    private static Set<BindingOwner> rootsOf(Set<BindingOwner.Expansion> expansions) {
        Set<BindingOwner> out = new LinkedHashSet<>();
        for (BindingOwner.Expansion each : expansions) {
            BindingOwner at = each;
            while (at instanceof BindingOwner.Expansion it) {
                at = it.within();
            }
            out.add(at);
        }
        return out;
    }

    /** Those of an application the author wrote. */
    private static Set<BindingOwner.Expansion> written(Set<BindingOwner.Expansion> these) {
        return these.stream().filter(each -> each.at() instanceof ApplicationOrigin.Written)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    /** And those of a name handed over as a value. */
    private static Set<BindingOwner.Expansion> eta(Set<BindingOwner.Expansion> these) {
        return these.stream().filter(each -> each.at() instanceof ApplicationOrigin.Eta)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    /** Everything {@code owner} is written under, innermost first. */
    private static List<BindingOwner> under(BindingOwner owner) {
        List<BindingOwner> out = new ArrayList<>();
        BindingOwner at = owner;
        while (true) {
            BindingOwner next = switch (at) {
                case BindingOwner.Expansion it -> it.within();
                case BindingOwner.Synthesized it -> it.within();
                default -> null;
            };
            if (next == null) {
                return out;
            }
            out.add(next);
            at = next;
        }
    }

    /** Every expansion of {@code helper} a binding in {@code body} belongs to. */
    private static Set<BindingOwner.Expansion> expansionsOf(Core body, String helper) {
        Set<BindingOwner.Expansion> out = new LinkedHashSet<>();
        for (BindingId each : bindingsOf(body)) {
            BindingOwner at = each.owner();
            while (at != null) {
                if (at instanceof BindingOwner.Expansion it) {
                    if (it.expanded().name().equals(helper)) {
                        out.add(it);
                    }
                    at = it.within();
                } else if (at instanceof BindingOwner.Synthesized it) {
                    at = it.within();
                } else {
                    at = null;
                }
            }
        }
        return out;
    }

    private static List<BindingId> bindingsOf(Core body) {
        List<BindingId> out = new ArrayList<>();
        binders(body, binder -> {
            if (binder != null && binder.binding() != null) {
                out.add(binder.binding());
            }
        });
        return out;
    }

    private static void binders(Core e, Consumer<Core.Binder> at) {
        switch (e) {
            case Core.LetIn it -> at.accept(it.binder());
            case Core.Block it -> it.params().forEach(at);
            case Core.Match it -> it.cases().forEach(one -> at.accept(one.binder()));
            case Core.IfConstructed it -> at.accept(it.binder());
            default -> { }
        }
        Core.forEachChild(e, child -> binders(child, at));
    }

    private static Bodies.Elaborated checked() {
        Compilation compilation = Compilation.ofSource(MODULE, "Main");
        compilation.answerEverything();
        assertEquals(List.of(), compilation.errors().stream()
                        .map(each -> each.diagnostic().code() + " " + each.diagnostic().primary())
                        .toList(),
                "the model under test compiles");
        Bodies.Elaborated checked = compilation.db()
                .ask(new Bodies.Checked(compilation.modules().get(0))).value();
        assertNotNull(checked, "the model under test was checked");
        assertNotNull(checked.analysisBodies().get("go"),
                "and the behavior has a body for the analysis to read");
        return checked;
    }
}
