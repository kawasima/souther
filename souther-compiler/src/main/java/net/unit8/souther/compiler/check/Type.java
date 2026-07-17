package net.unit8.souther.compiler.check;

/**
 * The Souther value types. Either a primitive ({@code Int}/{@code String}/{@code Bool})
 * or a reference to a named data type. {@code Type.INT} etc. remain usable as constants.
 */
public sealed interface Type
        permits Type.Prim, Type.Ref, Type.ListOf, Type.MapOf, Type.OptionOf, Type.Union, Type.FnOf {

    enum Prim implements Type { INT, STRING, BOOL, DECIMAL, DATE, DATETIME, RAW }

    /** A reference to a named data type (product or sum). */
    record Ref(String name) implements Type {}

    /** A homogeneous list of {@code element}. */
    record ListOf(Type element) implements Type {}

    /** A {@code Map<String, value>} — string-keyed, per spec 7.2. */
    record MapOf(Type value) implements Type {}

    /** An optional value {@code Option<element>} — the desugaring of a {@code T?} field (spec 7.4). */
    record OptionOf(Type element) implements Type {}

    /** An anonymous union of data types (a behavior's multi-success output). */
    record Union(java.util.Set<String> members) implements Type {}

    /** A function type {@code (params...) -> result}. Written only on a helper {@code fn}'s
     * parameter (spec §fn-declaration); a value of this type is never stored in a data field, so it
     * never crosses a codec boundary. */
    record FnOf(java.util.List<Type> params, Type result) implements Type {}

    Type INT = Prim.INT;
    Type STRING = Prim.STRING;
    Type BOOL = Prim.BOOL;
    Type DECIMAL = Prim.DECIMAL;
    Type DATE = Prim.DATE;
    Type DATETIME = Prim.DATETIME;
    /** The external (encoded) representation type: an encoder's raw output at a railway's edge,
     * unioned with propagated error arms as the arm {@code "Raw"} (spec 24). Reserved — no stage
     * produces it yet; {@code >->} composes behaviors, not codecs (spec 14.1). */
    Type RAW = Prim.RAW;

    static Type ref(String name) {
        return new Ref(name);
    }

    static Type list(Type element) {
        return new ListOf(element);
    }

    static Type option(Type element) {
        return new OptionOf(element);
    }

    static Type map(Type value) {
        return new MapOf(value);
    }

    static Type union(java.util.Set<String> members) {
        return new Union(members);
    }

    static Type fn(java.util.List<Type> params, Type result) {
        return new FnOf(params, result);
    }
}
