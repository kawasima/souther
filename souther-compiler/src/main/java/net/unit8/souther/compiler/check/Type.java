package net.unit8.souther.compiler.check;

/**
 * The Souther value types. Either a primitive ({@code Int}/{@code String}/{@code Bool})
 * or a reference to a named data type. {@code Type.INT} etc. remain usable as constants.
 */
public sealed interface Type permits Type.Prim, Type.Ref, Type.ListOf {

    enum Prim implements Type { INT, STRING, BOOL }

    /** A reference to a named data type (product or sum). */
    record Ref(String name) implements Type {}

    /** A homogeneous list of {@code element}. */
    record ListOf(Type element) implements Type {}

    Type INT = Prim.INT;
    Type STRING = Prim.STRING;
    Type BOOL = Prim.BOOL;

    static Type ref(String name) {
        return new Ref(name);
    }

    static Type list(Type element) {
        return new ListOf(element);
    }
}
