package net.unit8.souther.runtime;

import java.util.List;

/**
 * A single decode failure with a machine-readable code, a message, and the path at
 * which it occurred (spec section 10.5).
 *
 * @param path    the location in the Raw input where the error occurred
 * @param code    a machine-readable error code (e.g. {@code "MissingAtSign"})
 * @param message a human-readable message
 */
public record DecodeError(List<PathElement> path, String code, String message) {

    public DecodeError {
        path = List.copyOf(path);
    }

    /** A path element: either an object field name or a list index. */
    public sealed interface PathElement permits PathElement.Field, PathElement.Index {
        record Field(String name) implements PathElement {}
        record Index(int index) implements PathElement {}
    }

    /** An error at the root path (no field/index), typical of single-value decoders. */
    public static DecodeError atRoot(String code, String message) {
        return new DecodeError(List.of(), code, message);
    }
}
