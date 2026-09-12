package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;

/**
 * What the declarations say about a type, in the world a reader is asking from.
 *
 * <p>Asked with a type in hand and nothing else. What a {@code .} on it may name, what one of those
 * names holds, whether a value of it is held to a rule, whether the name is a newtype — none of
 * those needs a module's definitions or the signatures a body can reach, so a reader that only has
 * these questions is handed only this. That is the whole reason it is apart from
 * {@link DeclaredTypeReading}: what an expression is declared to be needs the definitions and the
 * signatures, and a reader asking whether a type has an invariant was making an empty table of
 * definitions to get at it.
 *
 * <p>Which world, from the caller. An accepted program's fields are what its check settled and a
 * text that has not checked has what its declarations resolve to so far; the two are
 * {@link FieldRead}'s to hold, and a reader here is in whichever one it built.
 *
 * @param read the reading of a {@code .}, in the world these facts are being asked in
 */
public record DeclarationFacts(FieldRead read) {

    public DeclarationFacts {
        if (read == null) {
            throw new IllegalArgumentException("declarations are read in a world that says so");
        }
    }

    /** What the names in a position denote, which is the reading's. One of them and not one here
     *  beside it: two that could differ are two answers about what a name means. */
    public Symbols symbols() {
        return read.symbols();
    }

    /** What the declarations a reading is made against say, which is the reading's for the reason
     *  above: two that could differ are two answers about what a declaration states. */
    public PublishedDeclarations published() {
        return read.published();
    }

    /** Which form each of those declarations was written in. */
    public DeclarationKinds kinds() {
        return read.kinds();
    }

    /**
     * Whether a value of {@code type} is held to a rule its declarations wrote.
     *
     * <p>Every rule that applies to it, and not the ones written on it. A spread flattens the fields
     * of what it brings in and inherits its invariants with them (ADR-0030), so a data that writes
     * no clause of its own is held to whatever it spread in — and a reading that looked at the
     * declaration's own clauses would say a value of it is held to nothing while the compiler
     * refuses one that breaks a rule.
     *
     * <p>Asked of the walk that settles which clauses apply. Following the spreads here would be
     * that walk written again, and the one that forgot a step would disagree with the checker about
     * what a value has to hold.
     */
    public boolean heldToARule(Type type) {
        return type instanceof Type.Ref(TypeSymbol named)
                && named instanceof TypeSymbol.AtModule declared
                && !TypeOps.invariantHeadersGoverning(declared, symbols()).isEmpty();
    }

    /** Whether {@code name} is a newtype, in this world. */
    public boolean isNewtype(TypeSymbol name) {
        return isNewtype(name, symbols());
    }

    /**
     * Whether {@code name} is a newtype. Asked of a name resolution settled, never of a spelling: an
     * imported value's body names its own module's types, which the module reading the row need not
     * have imported, and a module of its own may declare something else of that spelling.
     */
    public static boolean isNewtype(TypeSymbol name, Symbols symbols) {
        return name != null
                && symbols.declaredNode(name) instanceof Hir.Data d && d.newtype();
    }
}
