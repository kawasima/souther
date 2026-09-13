package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;

/**
 * What each declaration that wears one value wraps, for a reader working out how far a name goes.
 *
 * <p>Beside {@link DeclarationNewtypes} and not part of it, because the two are answerable at
 * different times. Whether a declaration wears one value was settled when its module was indexed;
 * what it wraps is a written type denoting something, which is not settled until the names in the
 * declaration resolve. Held as one capability, a carrier that can answer the first would have to say
 * something about the second before there was anything to say — and the way that gets said is a
 * reader assembling an answer of its own out of whatever declarations it happens to hold.
 *
 * <p>So a reader that only has to tell a newtype from anything else asks the first and is answered
 * from the index; a reader that has to go through the name asks this one.
 */
@FunctionalInterface
public interface NewtypeInners {

    /** What a newtype's one value is called, which the parser writes for the author. */
    String THE_ONE_VALUE = "value";

    /** What {@code declaration} wraps, or null where it wears no one value — or wears one whose
     *  written type denotes nothing, which is reported where it is written. */
    Type of(TypeKey declaration);

    /** Nothing declared anywhere — for a reading over primitives, which asks of no declaration. */
    NewtypeInners NONE = _ -> null;

    /**
     * What a value of {@code type} wears one of, or null where it wears none.
     *
     * <p>The way in for a reader holding a type rather than an address, which is most of them.
     */
    default Type under(Type type) {
        return type instanceof Type.Ref ref && ref.name() instanceof TypeSymbol.AtModule at
                ? of(at.key())
                : null;
    }

    /**
     * The same question read off the declarations in {@code symbols}.
     *
     * <p>For the walks that have not been handed the compilation's answer and read the declaration
     * for other things besides. Called from where the question is owned and not from a reader
     * reaching for the declaration itself: a reader building one of these out of a scope it holds is
     * reading raw structure on its own side of a boundary it is held to, whatever the type of the
     * thing it built says.
     */
    static NewtypeInners asWritten(Symbols symbols) {
        return declaration -> symbols.declaredNode(declaration) instanceof Hir.Data data
                ? innerOf(data)
                : null;
    }

    /**
     * What {@code data} wraps, or null where it wraps nothing.
     *
     * <p>The one place a declaration is turned into what it wears one of, whichever rung the
     * declaration was read off. Its own {@code value} field and not the fields it reaches: a newtype
     * is written as one type under a name, so what it wraps is what its own declaration carries.
     */
    static Type innerOf(Hir.Data data) {
        if (!data.newtype()) {
            return null;
        }
        for (Hir.Field field : data.fields()) {
            if (field.name().equals(THE_ONE_VALUE)) {
                return TypeOps.fieldType(field);
            }
        }
        // Written as a newtype with nothing to wrap: the name its one value was written as denotes
        // nothing, which is reported where it is written.
        return null;
    }
}
