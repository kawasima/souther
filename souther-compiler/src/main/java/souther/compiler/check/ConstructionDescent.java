package souther.compiler.check;

import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.SequencedMap;

/**
 * Where a value at a position has to be built, one step down.
 *
 * <p>A record is composed out of its fields, and a value at every other shape is chosen rather than
 * composed: a sum is one of its cases and which one is settled before anything under it is, what a
 * sequence holds is placed inside it and is not a field of it, and a primitive is a value already.
 * So this answers about a record and answers nothing anywhere else, which is the whole of what the
 * question has to say.
 *
 * <p><b>Not what is readable at the position.</b> {@link ReadableFields} answers that. The two are
 * the same map at a record, and a sum whose cases share a spread is where they part: those names
 * are readable at every value of the sum and a product of them is a value of none of its cases. A
 * plan taking the readable answer here would compose a value of no case at all.
 *
 * <p><b>One step and no walk.</b> How far down to follow it, where to stop because the caller has
 * already settled something, and what type stands at a path once a construction recipe has chosen
 * one belong to whoever is asking. Handed here as settings they would make this the thing that
 * decides which positions there are, with a caller's argument deciding which set it answers.
 */
public final class ConstructionDescent {

    /**
     * What a value at a position is composed out of, and what the composition is written as.
     *
     * @param constructor what the record is called, which is the name a value built out of these
     *                    fields is written under
     * @param fields      the fields, in the order the declaration writes them — which is the order
     *                    they are composed and reported in
     */
    public record ProductBuild(TypeSymbol constructor, SequencedMap<String, Type> fields) {

        public ProductBuild {
            fields = Collections.unmodifiableSequencedMap(new LinkedHashMap<>(fields));
        }
    }

    /**
     * What a value of this shape is built out of, or null where a value here is not composed at all.
     *
     * <p>Null and not an empty {@link ProductBuild}: a record declaring no fields is composed and
     * has nothing to put in it, and a value at a shape that is not a record is chosen whole. The two
     * are different answers and each reader treats them differently.
     */
    public static ProductBuild toBuild(Shape shape) {
        return shape instanceof Shape.Product product
                ? new ProductBuild(product.name(), product.fields()) : null;
    }

    private ConstructionDescent() {}
}
