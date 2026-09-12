package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;

import java.util.List;

/**
 * How the alternatives a value can be are written at a boundary: which form the set travels as, what
 * tag each alternative wears, and — where the form has one — the key that tag stands under.
 *
 * <p>A named sum and a behavior's output union are one question asked of two spellings. Both are a
 * set of alternatives that has to cross, and both are answered here through the same call, so
 * neither can be given a form, a tag or a key the other was not. They used to be answered in five
 * places that read none of the others, and two of them disagreeing went unreported because every one
 * of them dispatches on the first alternative that answers (#990, #994).
 *
 * <p>Nothing of this is stored. The representation is derived (ADR-0004) and a declaration says
 * nothing about it, so a field on {@link Hir.SumData} holding it would be an answer that can drift
 * from the one a reader works out — and a union, which has no declaration to hold a field, could not
 * be given one at all. That is what made the union a separate path in the first place.
 *
 * <p>The boundary here is where a value crosses into or out of the program, which is what
 * {@link BoundaryInput} and {@link BoundaryOutput} are about. It is not the boundary of
 * {@code query.BoundaryDerivation} and {@code partition.BoundaryPolicy}, which is the line a rule
 * draws between two classes of input. The two never meet: this answers how a value is written, and
 * that one answers where a rule divides. Said because the word carries both in this compiler and a
 * reader arriving at one from the other has to be told which.
 *
 * <p>What is <em>not</em> here: which atoms there are, which is {@link AtomSpace}'s and is read from
 * it; what a case carries and therefore whether the tag can sit beside it, which is
 * {@link TypeOps#caseShape}'s; and whether a named sum is an enumeration in the language's sense,
 * which is {@link TypeOps#isUnitOnlySum}'s and is read by what counts a type's values and by what
 * orders them. Those are different questions about the same declarations, and folding them in here
 * would make one answer decide three things.
 */
public final class Boundary {

    private Boundary() {}

    /** The key a derived codec writes an alternative's tag under (spec §encoder-derivation). */
    private static final String DISCRIMINATOR = "type";

    /**
     * How {@code subject}'s alternatives are written at a boundary.
     *
     * <p>Answers for anything, not only for a sum or a union — {@link AtomSpace#subjectAtoms} does,
     * and a caller that has to know the shape before it may ask would be deciding here what a
     * boundary form is. What comes back for a type that is no alternative space at all is
     * {@link Representation.Discriminated}, which is the right answer to the one question such a
     * caller asks — a product, a newtype, a primitive and a standalone unit are none of them read as
     * a bare tag — and is not a claim that the type crosses as a discriminated object. A reader
     * wanting the whole external representation of an arbitrary type is not asking this.
     */
    public static Alternatives of(Type subject, DeclarationKinds kinds,
                                  PublishedDeclarations published) {
        List<TypeSymbol> atoms = AtomSpace.subjectAtoms(subject, published);
        return new Alternatives(atoms, isEnumerationForm(subject, atoms, kinds)
                ? new Representation.Enumeration()
                : new Representation.Discriminated(DISCRIMINATOR));
    }

    /**
     * Whether the set travels as a bare tag: it is an alternative space, and every alternative in it
     * carries nothing but which one it is (spec §sum-discrimination, issue #161).
     *
     * <p>Being an alternative space is a term of this and not a guard on {@link #of}. A standalone
     * unit is one atom and that atom is a unit, so the atoms alone would call it an enumeration —
     * and a unit crosses on its own as an empty object, the tag being what admitting it into a sum
     * adds ({@code CodecGen.generateUnitEncoder}). The atoms answer what the alternatives are; they
     * do not answer whether there is a set of them.
     */
    private static boolean isEnumerationForm(Type subject, List<TypeSymbol> atoms,
                                             DeclarationKinds kinds) {
        return TypeOps.isSumType(subject, kinds)
                && !atoms.isEmpty()
                && atoms.stream().allMatch(atom -> atom instanceof TypeSymbol.AtModule at
                        && kinds.of(at.key()) == DeclarationKind.UNIT);
    }

    /**
     * One alternative space, settled: what it is made of and how that is written.
     *
     * <p>Handed to what generates and what checks, rather than each of them being handed the type
     * and asking again. Both would answer alike — this is a function of the type — but a reader
     * holding the type and the symbols is a reader that can work the tag out itself, and five of
     * them did.
     */
    public record Alternatives(List<TypeSymbol> atoms, Representation representation) {

        public Alternatives {
            atoms = List.copyOf(atoms);
        }

        /**
         * The alternatives as they are written, in the order {@link AtomSpace} put them in.
         *
         * <p>Derived and not held beside {@link #atoms}. A second list of the same set is what #990
         * was: the two are written from one walk, agree on the day they are written, and the day one
         * of them is extended nothing says which is the set.
         */
        public List<WireCase> wireCases() {
            return atoms.stream().map(atom -> new WireCase(atom, atom.name())).toList();
        }
    }

    /**
     * One alternative as it crosses: which atom it is, and the tag it wears.
     *
     * <p>The tag is an atom's name. That it is unique is not a rule invented here — ADR-0081 has
     * every effective member of a union going by a name of its own, and a sum's cases are declared
     * with it — so the map from atom to tag is injective on arrival.
     */
    public record WireCase(TypeSymbol atom, String tag) {}

    /** The form the set of alternatives travels as. */
    public sealed interface Representation {

        /** Every alternative carries nothing but which one it is, so the value is the tag itself. */
        record Enumeration() implements Representation {}

        /** An alternative carries something of its own, so the tag stands under {@code key} beside
         *  it (spec §sum-discrimination). */
        record Discriminated(String key) implements Representation {}
    }
}
