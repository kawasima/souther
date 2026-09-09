package souther.compiler.check;

import souther.compiler.types.TypeKey;

import java.util.List;

/**
 * What each clause of a declaration states, as the module that wrote the declaration read it.
 *
 * <p>The pair of {@link ClauseLocations}, and the reason the two are separate. What a clause states
 * and where it is written are two facts about one clause: the first is what a reading is built on,
 * the second is what one sentence puts a caret under. Answered together, an edit that moves a
 * clause and changes nothing it states would be an edit that changes what the model says.
 *
 * <p>One question and one input: which declaration. Who is asking is not an input, for the reason
 * {@link ExpandedClauseLookup} gives — a reading whose answer could differ between two askers is a
 * reading where a declaration was read in whichever representation the asker happened to hold.
 *
 * <p><b>What crosses here is what a clause states, and never a tree.</b> The reading that consumes
 * this builds a term of its own out of what it is told and keeps it inside; nothing it publishes
 * carries one. That is what lets a clause be read at all without the authored tree crossing the
 * boundary with it, which is what carried a declaration's every move into every module that
 * imported it.
 */
@FunctionalInterface
public interface ClauseMeanings {

    /**
     * What {@code declaration} states, clause by clause, in the order it writes them — empty where
     * nothing here declares one, and empty where it writes none.
     *
     * <p>Its own clauses and not the ones it spreads in. A clause reaching a declaration through a
     * spread is written on the declaration it was written on, and that one answers for it: asked of
     * the reader instead, what a clause states would depend on which of the declarations that hold
     * it was asked.
     */
    List<ClauseMeaning> of(TypeKey declaration);

    /** Nothing declared anywhere — for a reading over primitives, which asks of no declaration. */
    ClauseMeanings NONE = _ -> List.of();

    /**
     * The reading that makes these, which consults none.
     *
     * <p>Refused rather than empty. A reading that is producing what a declaration states cannot
     * also be reading it — asking would be asking for the answer being worked out — and an empty
     * answer would say instead that the declaration states nothing, which is a different thing and
     * one every clause of it would then be reported as.
     */
    ClauseMeanings THE_ONE_THAT_MAKES_THEM = declaration -> {
        throw new IllegalStateException("the reading that works out what `" + declaration
                + "` states is being asked what it states");
    };
}
