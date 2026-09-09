package souther.compiler.check;

import souther.compiler.core.Core;

import java.util.List;

/**
 * What one clause of a declaration guarantees of the value at one position, read as an assumption.
 *
 * <p>The answer a type gives about a value, and nothing about who asked. A walk seeding a path and a
 * recipe settling a choice want the same thing of a declaration, and what they do with it differs;
 * this is the thing, and what is done with it is the consumer's. So nothing here mentions a
 * {@link Known}, a path, or what is being measured.
 *
 * <p>{@code owed} and {@code quantified} are one answer and not two lists to be read side by side.
 * A quantified relation is one this very clause states of the elements of a container it names, so
 * pairing them by index somewhere downstream is an account of the pairing that can come apart from
 * the reading that made it.
 *
 * @param clause    the clause as it stands at this position, with the declaration's fields rebased
 *                  onto the value they are about
 * @param written   the parts the clause was written in, each a subtree of {@code clause} and each
 *                  carrying which part of the rule it is
 * @param owed      what reading that clause came to: a relation, a fact, both, or that it could not
 *                  be read at all. Not narrowed to a numeric constraint — a clause states what it
 *                  states, and a reader wanting only the numbers can ask {@link Predicates.Owed}
 *                  for them
 * @param quantified what the clause states of every element of a container it names
 * @param parts      what each part of the clause came to as it was read. A conjunction is read a
 *                   conjunct at a time, and what each conjunct came to is the reading's own answer
 *                   about that conjunct — kept here because asking it again afterwards is a second
 *                   reader, and the two agree only until somebody changes one of them
 */
record TypeGuarantee(Core clause, List<Clauses.StatedPart> written,
                     Predicates.Owed owed, List<Quantified> quantified, List<Part> parts) {

    TypeGuarantee {
        if (written.isEmpty()) {
            throw new IllegalArgumentException("a clause guarantees what its parts guarantee");
        }
        written = List.copyOf(written);
        quantified = List.copyOf(quantified);
        parts = List.copyOf(parts);
    }

    /**
     * Which rule of the model this is, read off the parts the clause was written in.
     *
     * <p>Every part of a clause is a part of that clause, so the rule is the parts' answer and not
     * a second thing to carry: held beside them, a guarantee about one rule could be built about
     * two.
     */
    RuleRef.Invariant rule() {
        return written.get(0).id().rule();
    }

    /**
     * What one part of a clause came to, beside the part it was read from.
     *
     * @param of    which of the clause's authored parts this was read below
     * @param shape the shape of the clause this was read at, which says which occurrence it is and
     *              which nodes it was spelled as, so that whoever keeps this reading keeps what it
     *              made of each occurrence without having to read the tree again
     * @param part  the node itself, for a reader that has something to do with it
     */
    record Part(PartId<RuleRef.Invariant> of, ClauseExpr shape, Core part,
                Predicates.Owed owed) {}
}
