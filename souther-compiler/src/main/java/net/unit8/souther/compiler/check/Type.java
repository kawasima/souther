package net.unit8.souther.compiler.check;

/**
 * The Souther value types. Either a primitive ({@code Int}/{@code String}/{@code Bool})
 * or a reference to a named data type. {@code Type.INT} etc. remain usable as constants.
 */
public sealed interface Type
        permits Type.Prim, Type.Ref, Type.ListOf, Type.MapOf, Type.SetOf, Type.OptionOf, Type.Union,
                Type.FnOf, Type.Var, Type.Nothing, Type.TupleOf {

    enum Prim implements Type { INT, STRING, BOOL, DECIMAL, DATE, DATETIME, RAW }

    /** The element type of the empty-list literal {@code []} (ADR-0028): a bottom that unifies with
     * any element type. It only ever appears as {@code ListOf(NOTHING)} — the empty list — whose type
     * is fixed by context ({@code ++}, an {@code if}/{@code match} case, a {@code fold} seed, or the
     * {@code List<T>} a position expects). It never reaches codegen: an empty list is element-agnostic
     * at runtime. */
    record Nothing() implements Type {}

    /** A type variable ({@code 'a}), written only in the shipped core (ADR-0028). It stands for any
     * type; a non-recursive core helper carrying one is monomorphised by inline expansion, so the
     * variable is resolved to the concrete argument type at each call site. */
    record Var(String name) implements Type {}

    /** A reference to a named data type (product or sum). */
    record Ref(String name) implements Type {}

    /** A homogeneous list of {@code element}. */
    record ListOf(Type element) implements Type {}

    /** A {@code Map<String, value>} — string-keyed, per spec 7.2. */
    record MapOf(Type value) implements Type {}

    /** A {@code Set<element>} — an unordered collection with no duplicate elements, compared by value
     * equality (ADR-0009). Its external representation is a JSON array, deduplicated on decode. */
    record SetOf(Type element) implements Type {}

    /** An optional value {@code Option<element>} — the desugaring of a {@code T?} field (spec 7.4). */
    record OptionOf(Type element) implements Type {}

    /** An anonymous union of data types (a behavior's multi-success output). */
    record Union(java.util.Set<String> members) implements Type {}

    /** A function type {@code (params...) -> result}. Written only on a helper {@code fn}'s
     * parameter (spec §fn-declaration); a value of this type is never stored in a data field, so it
     * never crosses a codec boundary. */
    record FnOf(java.util.List<Type> params, Type result) implements Type {}

    /** A tuple {@code (A, B, ...)} of two or more element types (ADR-0036). Expression-level only —
     * like {@link FnOf}, a tuple is never stored in a data field or a behavior's I/O, so it never
     * crosses a codec boundary; it only carries several values through a computation. */
    record TupleOf(java.util.List<Type> elements) implements Type {}

    Type INT = Prim.INT;
    Type STRING = Prim.STRING;
    Type BOOL = Prim.BOOL;
    Type DECIMAL = Prim.DECIMAL;
    Type DATE = Prim.DATE;
    Type DATETIME = Prim.DATETIME;
    /** The external (encoded) representation type: an encoder's raw output at a railway's edge,
     * unioned with propagated error cases as the case {@code "Raw"} (spec 24). Reserved — no stage
     * produces it yet; {@code >->} composes behaviors, not codecs (spec 14.1). */
    Type RAW = Prim.RAW;
    /** The bottom element type of the empty-list literal (see {@link Nothing}). */
    Type NOTHING = new Nothing();
    /** The type of the empty-list literal {@code []}: a list whose element type is not yet fixed. */
    Type EMPTY_LIST = new ListOf(NOTHING);

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

    static Type set(Type element) {
        return new SetOf(element);
    }

    static Type union(java.util.Set<String> members) {
        return new Union(members);
    }

    static Type fn(java.util.List<Type> params, Type result) {
        return new FnOf(params, result);
    }

    static Type tuple(java.util.List<Type> elements) {
        return new TupleOf(elements);
    }

    static Type var(String name) {
        return new Var(name);
    }
}
