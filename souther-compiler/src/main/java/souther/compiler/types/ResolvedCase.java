package souther.compiler.types;

import souther.compiler.hash.ValueHash;

import java.util.List;

/**
 * A case as this compile resolved it: what selects it, and which atoms selecting it covers.
 *
 * <p>Two facts of different phases, held together because one reader needs both and neither can be
 * worked out from the other. A {@link CaseSelector} is what a value is tested as and read as at run
 * time, and it says that much about itself wherever it is written — {@code Some} carries the present
 * carrier, a data-named case carries its data type — with no program around it. What it
 * <em>covers</em> is not like that: a case that is itself a sum stands for the leaves under it
 * (spec §sum-data), and which leaves those are is a fact about the declarations this compile read.
 *
 * <p>So the atoms are not a component of the selector. Putting them there would make a selector
 * whose name and carrier agree and whose atoms do not — the half-decided state
 * {@code CaseSelector}'s own constructor exists to refuse, and one it could not refuse, since
 * nothing in {@code types} can ask what a name reaches. The phase boundary is the type instead:
 * below it a case is a selector, and at this level it is a selector that has been resolved against
 * the declarations.
 *
 * <p>The value is here and the resolving is not. What a case covers is read off the declarations,
 * which this package holds none of, so the descent is {@code check.CaseSpace}'s and this holds what
 * it came to. A resolved case therefore carries no reading in it — no symbols, no way of asking
 * about a declaration — which is what lets an output that names none of this compiler hold one.
 *
 * <p>Made in one place for that reason and not because the pair cannot be written down. Two atoms
 * put beside a selector they do not belong to would be a value nothing here could tell from a
 * resolved one; what stops it is that the one caller is the descent itself, and a second maker
 * would be a second answer to what a case covers ({@code check.CaseSpace#resolve}).
 *
 * <p>What a backend emits is the selector — {@link #selector()} — and what {@code Core} carries is
 * this. The two are not the same need. A backend tests and reads a value, which the selector says
 * on its own; a reader asking which case of a subject an arm picked is asking about the leaves, and
 * below the checker there are no declarations left to work them out from. Carried as a selector
 * alone, that reader answered from the name, and one name over two leaves became a place nothing
 * else in the compiler holds.
 */
public final class ResolvedCase {

    private final CaseSelector selector;
    private final List<TypeSymbol> atoms;

    private ResolvedCase(CaseSelector selector, List<TypeSymbol> atoms) {
        this.selector = selector;
        this.atoms = List.copyOf(atoms);
    }

    /**
     * {@code selector}, with what the declarations say selecting it covers.
     *
     * <p>Called by the descent that worked the atoms out and by nothing else: which leaves a case
     * reaches is a question about a program, and this package holds no program to ask. So what
     * crosses into here is an answer, and the question stays where the declarations are.
     */
    public static ResolvedCase of(CaseSelector selector, List<TypeSymbol> atoms) {
        return new ResolvedCase(selector, atoms);
    }

    /** What tests and reads the value — the projection a backend emits. {@code Core} carries this
     *  whole value, because what it covers is the half a reader below the checker cannot rebuild. */
    public CaseSelector selector() {
        return selector;
    }

    /**
     * The atoms a value selected by this can be, in first-reach declaration order.
     */
    public List<TypeSymbol> atoms() {
        return atoms;
    }

    /** The name this case is written by. */
    public TypeSymbol name() {
        return selector.name();
    }

    /** What the value turns out to be once this case is selected. */
    public Refinement refinement() {
        return selector.refinement();
    }

    /** What a value selected by this is read as, or null where nothing readable stands under it. */
    public Type bound() {
        return selector.bound();
    }

    /**
     * Two resolved cases are one where they select the same case and cover the same atoms.
     *
     * <p>A value and not an identity. It is kept in what a behavior's declaration comes to, and two
     * compiles of one source have to answer alike — a reader comparing what was stated before an
     * edit with what is stated after would otherwise see every rule change because the objects are
     * new ({@code EverythingAnAnswerHoldsMeansSomethingTest}).
     *
     * <p>The atoms are compared as well as the selector. They follow from the selector and the
     * declarations, so within one compile the two never disagree; comparing both says that plainly
     * rather than resting on it, and two compiles of different sources that resolved one selector
     * differently are two different things.
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof ResolvedCase that
                && selector.equals(that.selector) && atoms.equals(that.atoms);
    }

    @Override
    public int hashCode() {
        return ValueHash.ofItsParts(ResolvedCase.class, selector.hashCode(), atoms.hashCode());
    }

    @Override
    public String toString() {
        return selector.name() + " covering " + atoms;
    }
}
