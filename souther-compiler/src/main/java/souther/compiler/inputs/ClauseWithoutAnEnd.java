package souther.compiler.inputs;

import souther.compiler.check.PartId;
import souther.compiler.check.RuleRef;
import souther.compiler.core.Core;
import souther.compiler.types.TypeSymbol;

/**
 * One conjunct of a declaration's clause that placed no end, and where the value it is written about
 * stands.
 *
 * <p>What the reading of a behavior's input hands to the reading that draws lines. A clause bounding
 * one coordinate leaves an end behind and is read through that; one relating two coordinates leaves
 * nothing, and it is still a rule about where this behavior's values part — so it is handed over as
 * the clause it is, to be read where a line is drawn.
 *
 * <p><b>Nothing here says what the clause comes to.</b> Which of these draws a line is settled by
 * the reading that draws it, in that reading's own atoms, and the word the reading of ends had for
 * why it placed none is no part of the question: the two read one clause with different atoms, and a
 * clause over a number one of them has no atom for is one the other names two positions in.
 *
 * @param part     which part of which rule this is, as the split that wrote the parts down named
 *                 it. What tells one authored line from another is this name, and a reader holding
 *                 the expression alone cannot tell two identical conjuncts apart
 * @param read     the conjunct itself
 * @param at       where the value the clause is written about stands. The clause binds each field of
 *                 the declaration that wrote it, and a field an include brought in keeps that
 *                 declaration's binding, so the names under this path are what the clause reads
 *                 whichever declaration wrote it
 * @param readUnder the declaration this reading was made under, which is not always the one the
 *                 clause was written on. A name wrapped round a record is a governing declaration of
 *                 its own and the record's clauses are read under it, so what its reads are bound to
 *                 is that name's — matched against the writing declaration's bindings alone, a
 *                 clause under a name names no position at all
 */
public record ClauseWithoutAnEnd(PartId<RuleRef.Invariant> part, Core read, TermPath at,
                                 TypeSymbol.AtModule readUnder) {

    public ClauseWithoutAnEnd {
        if (part == null || read == null || at == null || readUnder == null) {
            throw new IllegalArgumentException(
                    "a clause is one of a declaration's, is written, and is about a value somewhere");
        }
    }

    /** Which clause of which declaration this is a part of. */
    public RuleRef.Invariant rule() {
        return part.rule();
    }
}
