package souther.compiler.types;

import souther.compiler.hash.SaysWhatStandsForIt;
import souther.compiler.hash.ValueHash;

/**
 * Which binding a name is. A parameter, a {@code let}, a lambda's parameter, a {@code match} arm's
 * binding: each is one of these, and two of them are the same binding when they are equal.
 *
 * <p>What a binding is cannot be worked out from how it was spelled — a body may bind a name a
 * module declares, and two bodies may bind one spelling — nor from where it was written, since a
 * pass that expands a helper stamps the call site over the positions in the copy. So the binder
 * carries this, every name that resolves to it carries the same value, and asking whether a name is
 * that binding is asking whether the two are equal. There is nothing else to compare.
 *
 * <p>It is a value, not a token: the query database decides that an answer is unchanged by comparing
 * it, and an {@code Ast.Module} is what many keys answer, so an identity that differed between two
 * readings of one source would make every edit look like a change to everything downstream.
 *
 * <p>{@code ordinal} counts the bindings of {@code owner} in the order they are written. It is not
 * stable against an edit — binding something ahead of it moves it — but the movement stops at the
 * definition, which is the unit the queries already ask in.
 *
 * <p><b>Its number is worked out when it is made and kept.</b> Nearly everything a compilation asks
 * about a name is filed under one of these, so a value of this kind is asked its number tens of
 * times for every one that is made — and an owner is a thing standing inside another, so working
 * one out walks the whole chain the copy is under, the call it was expanded at, and the construct
 * beneath that. What is walked is the same every time, because none of it can change.
 */
public final class BindingId implements SaysWhatStandsForIt {

    /**
     * The whole of what a binding identity is, in one value.
     *
     * <p>Written once, and read by the equality, by the number and by the walk that proves the
     * number is taken from values. A component given to a binding identity is given to it here,
     * which is what leaves the three unable to disagree about what one holds.
     */
    record Parts(BindingOwner owner, int ordinal) {
    }

    private final Parts parts;

    private final int hash;

    public BindingId(BindingOwner owner, int ordinal) {
        if (owner == null) {
            throw new IllegalArgumentException("a binding belongs to something: " + ordinal);
        }
        this.parts = new Parts(owner, ordinal);
        this.hash = ValueHash.ofOnePart(BindingId.class, parts.hashCode());
    }

    /** What the binding belongs to. */
    public BindingOwner owner() {
        return parts.owner();
    }

    /** Which binding of that owner this is. */
    public int ordinal() {
        return parts.ordinal();
    }

    @Override
    public Parts standsFor() {
        return parts;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BindingId that && hash == that.hash && parts.equals(that.parts);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return parts.owner() + "." + parts.ordinal();
    }
}
