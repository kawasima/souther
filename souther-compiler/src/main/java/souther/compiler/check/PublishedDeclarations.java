package souther.compiler.check;

import souther.compiler.types.TypeKey;

/**
 * Where what a declaration says is answered from, for a reader that means only that.
 *
 * <p>A capability and not a table, for the reason {@link ClauseLocations} is one: which declarations
 * a reader asks about is settled by what it reads, so a reader that asks about one depends on that
 * one and a reader that asks about none depends on nothing. Handed the answers instead, every reader
 * would depend on every declaration any of them named.
 *
 * <p><b>Beside {@link Symbols} and not inside it.</b> A carrier of symbols answers what a
 * declaration is in the form that carrier reads declarations in, and answering the same question in
 * two forms is what that interface exists to refuse. This is not that question asked again: where a
 * declaration is written and what it is made of are things the tree has and this does not, and a
 * reader holding one of these is holding what a module elsewhere observes.
 */
public interface PublishedDeclarations {

    /** What {@code declaration} says, or null where nothing declares it. */
    DeclarationMeaning of(TypeKey declaration);

    /** Nothing declared anywhere — for a reading over primitives, which asks of no declaration. */
    PublishedDeclarations NONE = _ -> null;

    /**
     * The reading that makes these, which consults none.
     *
     * <p>Refused rather than answering nothing. A reading that is working out what a declaration
     * says cannot also be reading it — asking would be asking for the answer being worked out — and
     * an answer of nothing would say instead that nothing declares it, which is a different thing
     * and one every clause of it would then be reported under.
     */
    PublishedDeclarations THE_ONE_THAT_MAKES_THEM = declaration -> {
        throw new IllegalStateException("the reading that works out what `" + declaration
                + "` says is being asked what it says");
    };
}
