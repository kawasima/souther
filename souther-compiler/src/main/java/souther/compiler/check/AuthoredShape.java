package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.core.Core;

import java.util.ArrayList;
import java.util.List;

/**
 * How an author wrote a clause as several rules, kept as the shape they wrote rather than as a list.
 *
 * <p>Which parts a clause has, and where each of them stands in it, is settled once — where the
 * clause is split ({@link ClauseHelpers}) — and this is that answer. What it is for is
 * the one thing a list cannot do: find, in a tree the clause was read into, the very subtree each
 * part became.
 *
 * <p><b>It says where to go and never asks.</b> A reading that recovered the parts from a typed tree
 * would be a second answer to how many parts there are, and two answers to that is what numbering a
 * clause in two walks came to. So the descent below is driven by this: where the author wrote two
 * rules, the tree read from them has two sides, and this walks into them. It never asks the tree
 * whether it is a conjunction — the author already said so — and a tree that has no two sides where
 * this says there are is this compiler disagreeing with itself, which is what it stops on.
 *
 * <p>The parts are not discovered, numbered, merged or split here. They were issued where the clause
 * was split, and this recovers what each of them was read into.
 */
public sealed interface AuthoredShape {

    /** Two rules, written as one clause — with the node they were joined at, which is what a
     *  reading of the clause is written back into. */
    record Both(Hir.Binary written, AuthoredShape left, AuthoredShape right)
            implements AuthoredShape {

        public Both {
            if (written == null || left == null || right == null) {
                throw new IllegalArgumentException("a clause of two rules is written of both");
            }
        }
    }

    /** One rule, written as itself. */
    record One(ClauseHelpers.AuthoredPart part) implements AuthoredShape {

        public One {
            if (part == null) {
                throw new IllegalArgumentException("a rule an author wrote is some part of a clause");
            }
        }
    }

    /**
     * Each part of the clause with the subtree of {@code read} it was read into, as a part of
     * {@code rule}.
     *
     * <p>{@code read} is what the whole clause came to, and every part of it is a subtree of that
     * one reading — not a tree of its own read alongside it. What a clause states is read as one
     * thing (a rule's conjuncts meet inside that reading, and a choice one of them rules out is
     * ruled out there), and what an author is answerable for is the parts; both come out of the one
     * reading, which is why this recovers them from it rather than reading them apart.
     */
    default List<Clauses.StatedPart> onto(Core read, RuleRef.Invariant rule) {
        List<Clauses.StatedPart> out = new ArrayList<>();
        found(read, rule, out);
        return List.copyOf(out);
    }

    /**
     * One part of a clause with the tree an expansion made of it.
     *
     * <p>What a reader of an expanded clause wants of a part: the name it was issued under, and the
     * tree to read. Both come from the shape, so a reader has nothing to split and nothing to
     * number.
     */
    record Written(PartId<RuleRef.Invariant> id, Hir.Expr read) {

        public Written {
            if (id == null || read == null) {
                throw new IllegalArgumentException("a part of an expanded clause is named and is a"
                        + " tree");
            }
        }
    }

    /**
     * Each part of the clause with the subtree of the expanded {@code read} it became, as a part of
     * {@code rule}.
     *
     * <p>The same recovery {@link #onto(Core, RuleRef.Invariant)} does of a reading, done of the
     * tree an expansion left. What a helper's body joined stands under a binding there, so a reader
     * splitting that tree for itself would find parts this shape never issued — which is why the
     * shape drives the descent here as well.
     */
    default List<Written> onto(Hir.Expr read, RuleRef.Invariant rule) {
        List<Written> out = new ArrayList<>();
        wrote(read, rule, out);
        return List.copyOf(out);
    }

    private void wrote(Hir.Expr read, RuleRef.Invariant rule, List<Written> out) {
        switch (this) {
            case One it -> out.add(new Written(it.part().idFor(rule), read));
            case Both it -> {
                if (!(read instanceof Hir.Binary bin)) {
                    throw new IllegalStateException("an author wrote two rules where the expansion"
                            + " left one " + read.getClass().getSimpleName() + ", so the clause was"
                            + " written into a shape it was not read in");
                }
                it.left().wrote(bin.left(), rule, out);
                it.right().wrote(bin.right(), rule, out);
            }
        }
    }

    private void found(Core read, RuleRef.Invariant rule, List<Clauses.StatedPart> out) {
        switch (this) {
            case One it -> out.add(new Clauses.StatedPart(it.part().idFor(rule), read));
            case Both it -> {
                // The author wrote two rules here, so what they were read into has two sides. Asked
                // of the tree instead — whether this node joins two conditions — this would be a
                // second reading of what a clause is made of, and the reading it agreed with until
                // somebody changed one of them.
                if (!(read instanceof Core.Binary bin)) {
                    throw new IllegalStateException("an author wrote two rules where this reading"
                            + " has one " + read.getClass().getSimpleName() + ", so the clause was"
                            + " read into a shape it was not written in");
                }
                it.left().found(bin.left(), rule, out);
                it.right().found(bin.right(), rule, out);
            }
        }
    }
}
