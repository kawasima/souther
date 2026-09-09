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
}
