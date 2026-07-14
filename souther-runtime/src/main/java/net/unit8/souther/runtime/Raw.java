package net.unit8.souther.runtime;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * The external representation ("Raw") that decoders read and encoders produce.
 *
 * <p>Corresponds to spec section 6. {@code Null} lives only in the Raw world; the
 * internal data model forbids null.
 */
public sealed interface Raw
        permits Raw.NullValue, Raw.BoolValue, Raw.IntValue, Raw.DecimalValue,
                Raw.TextValue, Raw.ListValue, Raw.ObjectValue {

    /** The absence of a value at the boundary. */
    record NullValue() implements Raw {}

    /** A boolean value. */
    record BoolValue(boolean value) implements Raw {}

    /** A 64-bit signed integer value. */
    record IntValue(long value) implements Raw {}

    /** An arbitrary-precision decimal value. */
    record DecimalValue(BigDecimal value) implements Raw {}

    /** A text value. */
    record TextValue(String value) implements Raw {}

    /** A list of Raw values. */
    record ListValue(List<Raw> value) implements Raw {}

    /** An object (string-keyed map) of Raw values. */
    record ObjectValue(Map<String, Raw> value) implements Raw {}

    NullValue NULL = new NullValue();

    static Raw nullValue() {
        return NULL;
    }

    static Raw bool(boolean value) {
        return new BoolValue(value);
    }

    static Raw integer(long value) {
        return new IntValue(value);
    }

    static Raw decimal(BigDecimal value) {
        return new DecimalValue(value);
    }

    static Raw text(String value) {
        return new TextValue(value);
    }

    static Raw list(List<Raw> value) {
        return new ListValue(value);
    }

    static Raw object(Map<String, Raw> value) {
        return new ObjectValue(value);
    }
}
