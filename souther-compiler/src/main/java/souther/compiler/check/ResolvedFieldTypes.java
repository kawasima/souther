package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.observe.FieldTypes;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the declarations a text has resolved so far say its fields hold.
 *
 * <p>The reading for a program that has not been accepted: an editor asks what may be written after
 * a {@code .} while the module around it is still being typed, and what it can be told is what the
 * declarations denote at this revision. Nothing here is a decision of the check's, and nothing here
 * waits for one — a declaration whose clause does not elaborate still has fields, and an author
 * reading its name deserves them.
 *
 * <p>Not the reading a checked program is compared against. What a value of an accepted declaration
 * is made of is what the check settled, and a reader in that world takes it from there; this one is
 * handed to readers whose world has no such answer, so that neither of them has to decide what to
 * do when it is missing.
 *
 * <p>A written type this cannot read as a reference to a declaration is a field this says nothing
 * about. It denotes something — a function type denotes a function — but what it denotes is not a
 * place a value crosses a boundary at, and a declaration writing one is refused before it is ever
 * accepted.
 *
 * <p><b>What a name written over one value holds is asked and not walked.</b> This world answers it
 * like any other name at any other declaration, so a reader of a field crossing a {@code .} still
 * reads the world it was handed; what changed is where this world gets the answer. A newtype has
 * nothing the walk below is for — no spread to follow and no second field — so walking it read a
 * declaration to be told what {@link NewtypeInners} already says.
 */
public final class ResolvedFieldTypes implements FieldTypes {

    private final Symbols symbols;

    private final NewtypeInners inners;

    public ResolvedFieldTypes(Symbols symbols, NewtypeInners inners) {
        if (symbols == null || inners == null) {
            throw new IllegalArgumentException("what a declaration denotes is read against a world,"
                    + " and what a name wraps is read where that was settled");
        }
        this.symbols = symbols;
        this.inners = inners;
    }

    @Override
    public Map<String, Type> of(TypeSymbol owner) {
        // A name written over one value holds that value under `value`, and what it holds is what it
        // wraps — asked where that is settled rather than walked out of the declaration here. The
        // walk below is the machinery a product's fields need, and a newtype has none of it: nothing
        // to spread in, and one field the author did not write.
        Type wraps = owner instanceof TypeSymbol.AtModule at ? inners.of(at.key()) : null;
        if (wraps != null) {
            return Map.of(NewtypeInners.THE_ONE_VALUE, wraps);
        }
        Map<String, Type> out = new LinkedHashMap<>();
        written(owner, symbols).forEach((field, declared) -> {
            Type is = declared.denotes();
            if (is != null) {
                out.put(field, is);
            }
        });
        return out;
    }

    /**
     * A data's fields as they are written, following the {@code ...includes} it composes in (spec
     * §data).
     *
     * <p>A spread naming nothing brings in no fields. What a name repeated between two spreads
     * comes to is not decided here and nothing may read an answer off it: a declaration writing one
     * is refused where it is checked, and what this walk hands back for a text that has not reached
     * that check is whichever of them it read last.
     */
    static Map<String, Hir.TypeRef> written(TypeSymbol typeName, Symbols symbols) {
        Map<String, Hir.TypeRef> out = new LinkedHashMap<>();
        if (symbols.declaredNode(typeName) instanceof Hir.Data d) {
            for (Hir.Name inc : d.includes()) {
                if (inc instanceof Hir.Name.Denoting named) {
                    out.putAll(written(named.type(), symbols));
                }
            }
            for (Hir.Field f : d.fields()) {
                if (f.type() instanceof Hir.TypeRef ref) {
                    out.put(f.name(), ref);
                }
            }
        }
        return out;
    }
}
