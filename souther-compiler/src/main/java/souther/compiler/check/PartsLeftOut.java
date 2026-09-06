package souther.compiler.check;

import souther.compiler.core.Core;

import java.util.Set;

/**
 * Which authored part of which rule a reading was asked to leave out.
 *
 * <p>Beside {@link RulesLeftOut} and not a finer setting of it. That one leaves out every rule some
 * declaration wrote, and what it is asked with is a declaration; this leaves out one conjunct of one
 * rule, and what it is asked with is that rule together with the node the conjunct was written as.
 * The two are different questions about different things, and a reading told to leave out "a part"
 * with no rule beside it would leave out whatever else happened to be written the same way.
 *
 * <p>What such a reading is for is not here. A reader comparing what a value's rules leave with and
 * without one conjunct is asking what that conjunct was holding; nothing about the comparison
 * belongs to the reading that answers it.
 */
sealed interface PartsLeftOut permits PartsLeftOut.Nothing, PartsLeftOut.Some {

    /** Every part of every rule is read. */
    PartsLeftOut NONE = new Nothing();

    /** What the parts a reading takes in come to for one rule. */
    Predicates.PartsToRead of(RuleRef.Invariant rule);

    /** Whether anything at all is left out, which is what a reading standing in for a
     *  counterfactual is. Asked of the arms rather than of one of them, so that an arm added is a
     *  case here and not a reading that quietly counts as the whole one. */
    boolean leavesAnythingOut();

    /**
     * Whether this reading was asked to leave {@code part} of {@code rule} out.
     *
     * <p>Off {@link #of} and not beside it. Two walks over one clause skip the part together — a
     * part one reached that the other never read is a value whose rules were not gathered — so
     * asking in two ways is two answers that have to agree, and there is one.
     */
    default boolean excludes(RuleRef.Invariant rule, Core part) {
        return !of(rule).includes(part);
    }

    /** Nothing is left out. */
    record Nothing() implements PartsLeftOut {

        @Override
        public Predicates.PartsToRead of(RuleRef.Invariant rule) {
            return Predicates.PartsToRead.ALL;
        }

        @Override
        public boolean leavesAnythingOut() {
            return false;
        }
    }

    /**
     * Some conjuncts of some rules are left out.
     *
     * <p>A set, because that is what a counterfactual reading is asked. Which of the candidates
     * account for an end is three questions and two of them take more than one away at a time — one
     * candidate is missed on its own, and one holds the end with every other candidate gone — so a
     * scope that could only name one would answer the first and stop.
     *
     * <p>The node and not a number. Which conjunct of a clause a part is is counted by more than one
     * reading, and they count alike only where a clause is read positively — so what is named here
     * is the node itself, which is the identity a part already has everywhere it is recorded. Held
     * as a value: a reading asked to leave a part out types the clause again, and what it walks is
     * a node equal to the one named here rather than the same one.
     */
    record Some(Set<AuthoredPart> parts) implements PartsLeftOut {

        public Some {
            parts = Set.copyOf(parts);
            if (parts.isEmpty()) {
                throw new IllegalArgumentException("leaving nothing out is `NONE`");
            }
        }

        @Override
        public Predicates.PartsToRead of(RuleRef.Invariant of) {
            Set<Core> here = parts.stream().filter(each -> each.part().rule().equals(of))
                    .map(AuthoredPart::read).collect(java.util.stream.Collectors.toSet());
            return here.isEmpty() ? Predicates.PartsToRead.ALL
                    : Predicates.PartsToRead.without(here);
        }

        @Override
        public boolean leavesAnythingOut() {
            return true;
        }
    }

    /**
     * One part of one rule, which is what a counterfactual reading is asked without.
     *
     * <p>Named by what the split that wrote the parts down called it, and holding the tree the
     * reading of predicates matches on. The tree is not the identity: it is how the reading that
     * walks a clause recognises the part it was told to leave out, and a reading asked without a
     * part types the clause again, so what it walks is a node equal to this one rather than the
     * same one.
     */
    record AuthoredPart(PartId part, Core read) {

        public AuthoredPart {
            if (part == null || read == null) {
                throw new IllegalArgumentException("a part left out is some rule's own");
            }
        }
    }

    /** Every part but these. */
    static PartsLeftOut without(Set<AuthoredPart> parts) {
        return parts.isEmpty() ? NONE : new Some(parts);
    }
}
