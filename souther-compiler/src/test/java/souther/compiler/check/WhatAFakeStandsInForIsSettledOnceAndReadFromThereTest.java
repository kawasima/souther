package souther.compiler.check;

import souther.compiler.WhatWasCompiled;
import souther.compiler.ast.Hir;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@code fake} block's stand-in semantics are made in one place, and are not worked out again
 * from the block.
 *
 * <p>Two things can be re-derived from a block, and they fail differently. The association — which
 * behavior this block stands in for — is resolution's answer, and a reader taking it off the block
 * again holds an answer that agrees with resolution's for as long as nobody changes either. The
 * cardinality — how many blocks of the module name one behavior — is over the whole module, and a
 * reader walking the module's blocks to pick one for a behavior is making that count again, under
 * whatever order the compile happened to hand it its files. The second is what let a table be
 * chosen by being written first.
 *
 * <p>So the two are counted apart. Reading a block is not what is refused: a reader that walks
 * {@link FakeTables#written} and holds one block to what is written in it is doing what that
 * projection is for. What is refused is taking the association or the count back out of the raw
 * blocks.
 *
 * <p>Counted from what javac made of the module, so that a call spelled shorter, written in a
 * lambda, or handed over as a method reference is still in the caller's constant pool. Only
 * {@code Hir.Fake}'s accessor is named: {@code Hir.With} carries one of the same name, and a
 * {@code with} on a row is read for what it supplies wherever a row runs — a different question,
 * settled row by row, and no part of what a module declares a table for.
 */
class WhatAFakeStandsInForIsSettledOnceAndReadFromThereTest {

    private static final String CLASSIFIER = FakeTables.class.getName();

    /**
     * The walk over what a module has written in it, which reads no block for what it stands in
     * for.
     *
     * <p>It visits every authored expression there is, a fake row's among them, and a block is one
     * more place expressions are written. Nothing it does turns on which behavior a block names or
     * on how many blocks name one, so it takes the blocks as text and is where they are text.
     */
    private static final String THE_WALK_OVER_WHAT_IS_WRITTEN =
            "souther.compiler.sites.AuthoredSites$Walk";

    @Test
    void onlyTheClassifierAsksABlockWhatItStandsInFor() {
        assertEquals(Set.of(CLASSIFIER), WhatWasCompiled.callersOf(Hir.Fake.class, "standsInFor"),
                "a reader taking the association off the block is resolution's question asked"
                        + " again, and its answer agrees with resolution's for exactly as long as"
                        + " nobody changes either");
    }

    @Test
    void andTheClassifierDoesAskIt() {
        assertTrue(WhatWasCompiled.callersOf(Hir.Fake.class, "standsInFor").contains(CLASSIFIER),
                CLASSIFIER + " does not ask a block what it stands in for, so the census above is"
                        + " counting nothing");
    }

    /**
     * Who reaches the module's blocks, the module itself not counted.
     *
     * <p>A record's own methods read the component beside the accessor, under one name and one
     * owner, so the declaration is in this census whatever anybody else does. What the rule is
     * about is a reader outside it.
     */
    private static Set<String> readersOfTheModulesBlocks() {
        Set<String> found = new TreeSet<>(WhatWasCompiled.callersOf(Hir.Module.class, "fakes"));
        found.remove(Hir.Module.class.getName());
        return found;
    }

    @Test
    void onlyTheClassifierWalksTheModulesBlocks() {
        assertEquals(Set.of(CLASSIFIER, THE_WALK_OVER_WHAT_IS_WRITTEN), readersOfTheModulesBlocks(),
                "a reader walking the module's blocks to find the one that answers for a behavior"
                        + " is making the count again, and the answer it gets is decided by the"
                        + " order this compile was handed its files");
    }
}
