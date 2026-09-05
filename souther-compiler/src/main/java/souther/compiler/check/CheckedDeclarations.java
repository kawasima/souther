package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.core.ValueShape;
import souther.compiler.observe.Composed;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What this compile settled its declarations are made of.
 *
 * <p>The accepted program's reading, and the only one anything running its rows may have. What a
 * value of a declaration is made of is decided once, where the declaration is checked
 * ({@link ExecutableInvariants}); this hands that decision over and re-reads nothing. So a row
 * compared against a field and a text typed by the same field are held to one answer, and the JVM
 * refusing a construction is refusing it by that answer too.
 *
 * <p>Which shape to hand over is asked by the declaration's own identity, and a declaration this
 * compile resolved has one. Two ways of not having one, and each is said as what it is: a
 * declaration this reading cannot reach at all, and one it reaches that the check settled nothing
 * about. Neither is a data with no fields.
 */
public final class CheckedDeclarations implements souther.compiler.observe.Declarations {

    /** What each declaration was checked to be, asked for by the declaration's own identity. */
    public interface Shapes {

        /** What a value of {@code declared} is made of, or null where this compile settled nothing
         *  about it. */
        ValueShape of(TypeSymbol.AtModule declared);
    }

    private final Symbols symbols;
    private final Shapes shapes;

    public CheckedDeclarations(Symbols symbols, Shapes shapes) {
        if (symbols == null || shapes == null) {
            throw new IllegalArgumentException("a checked declaration is what a module wrote and"
                    + " what the check said about it: " + symbols + " " + shapes);
        }
        this.symbols = symbols;
        this.shapes = shapes;
    }

    @Override
    public Composed of(TypeSymbol.AtModule declared) {
        return switch (symbols.declaredNode(declared)) {
            // A module wrote this one and this reading cannot see it. Said as what it is: a
            // declaration out of reach is not a declaration with nothing under it.
            case null -> throw new IllegalStateException("`" + declared + "` is declared by a module"
                    + " and this reading cannot reach what it declares");
            case Hir.Data _ -> new Composed.OfFields(fieldsOf(declared));
            case Hir.SumData _, Hir.UnitData _ -> Composed.NOTHING;
        };
    }

    /** What the check settled a value of {@code declared} holds, in the order it is laid out. */
    private Map<String, Type> fieldsOf(TypeSymbol.AtModule declared) {
        ValueShape shape = shapes.of(declared);
        if (shape == null) {
            throw new IllegalStateException("`" + declared + "` is a data this compile resolved and"
                    + " the check said nothing about what a value of it is made of");
        }
        Map<String, Type> out = new LinkedHashMap<>();
        for (ValueShape.Field field : shape.fields()) {
            out.put(field.name(), field.type());
        }
        return out;
    }
}
