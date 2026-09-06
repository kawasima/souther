package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.types.TypeKey;

import java.util.List;

/**
 * One declaration's invariant clauses with everything its own module defines substituted, and the
 * operations the language defines left standing ({@link InliningPolicy#DISCHARGE}).
 *
 * <p>That is the whole of what this value says, and it is a property of the tree rather than of who
 * reads it. Two readers already take this form — the invariant-discharge analysis and the codec's
 * constraint mapping — so a name taken from either would be the other's to misread, and a third
 * reader would arrive at a name that was never about it.
 *
 * <p><b>Whose it is.</b> The clauses of a declaration, worked out in the environment of the module
 * that wrote it. Nothing about it depends on who is asking: a module that imports the declaration
 * has no substitution of its own to make, and could not make this one — what a clause names may be
 * a definition its declaring module never exposed, which no importer has a name for
 * (spec §invariant-discharge-representation).
 *
 * <p><b>Why this is not a record.</b> A record's canonical constructor is as public as the record,
 * and what this carries is a list of clauses that anything could supply — which is the whole of what
 * the written tree is too. A reader handed a bare list has nothing to tell the two apart, and that is
 * how a declaration came to be read in whichever representation the reader happened to hold. So the
 * value is minted where it is made and nowhere else: {@link ClauseHelpers} performs the expansion and
 * is the one production site, the way {@link souther.compiler.types.TypeSymbol.AtModule} is minted
 * only by {@code TypeSymbols}.
 */
public final class ExpandedClauses {

    private final TypeKey declaration;
    private final List<Expanded> clauses;

    /** Closed, for the reason written above. */
    ExpandedClauses(TypeKey declaration, List<Expanded> clauses) {
        if (declaration == null) {
            throw new IllegalArgumentException("expanded clauses are some declaration's");
        }
        this.declaration = declaration;
        this.clauses = List.copyOf(clauses);
    }

    /**
     * One clause as its own expansion produced it.
     *
     * <p>Per clause and not per declaration, because that is what the answer is about: an expansion
     * is run over one tree and leaves calls standing in that one. Held at the declaration's grain,
     * a clause would be told that a call standing in a sibling stands in it — which is the same
     * mistake as reading it off the inliner, one level down.
     *
     * <p>And the shape its author wrote it in, with each part expanded where it stands, so that
     * which parts a clause has comes from the text and not from the tree the expansion left
     * ({@link AuthoredShape}).
     */
    public record Expanded(Hir.InvariantClause clause, CallsLeftStanding standing,
                           AuthoredShape shape) {

        public Expanded {
            if (clause == null || standing == null || shape == null) {
                throw new IllegalArgumentException(
                        "an expanded clause is a tree, what its expansion left standing, and the"
                                + " shape its author wrote it in");
            }
        }
    }

    /**
     * A named way in for a declaration kind with no {@code invariant} to write — {@link Hir.SumData}
     * and {@link Hir.UnitData}.
     *
     * <p>The producer's, and nothing the value carries. What a reader needs is that every rule about
     * the declaration has been read and there are none; why there were none is why the producer
     * called this rather than the other, and a value that remembered it would let a reader ask a
     * declaration's kind through a question about its rules.
     */
    static ExpandedClauses nothingToExpand(TypeKey declaration) {
        return new ExpandedClauses(declaration, List.of());
    }

    /** Which declaration these are the clauses of. */
    public TypeKey declaration() {
        return declaration;
    }

    /**
     * Its clauses, in the order they were written, empty where it wrote none — each with what the
     * expansion that produced it left standing.
     *
     * <p>Not read off the tree. A helper applied in one of them is a call the expansion meant to
     * leave or one it was supposed to remove, and the tree is the same either way; which it is was
     * settled here and is carried rather than guessed at ({@link CallsLeftStanding}).
     */
    public List<Expanded> clauses() {
        return clauses;
    }

    /**
     * Two of these are one where they are the same clauses of the same declaration.
     *
     * <p>An answer of a query, so what settles it decides what downstream is asked to do again:
     * read as one thing, a declaration whose clauses are what they were leaves every reading of them
     * where it was.
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof ExpandedClauses each
                && declaration.equals(each.declaration) && clauses.equals(each.clauses);
    }

    @Override
    public int hashCode() {
        return declaration.hashCode() * 31 + clauses.hashCode();
    }

    @Override
    public String toString() {
        return declaration.qualified() + " " + clauses.size();
    }
}
