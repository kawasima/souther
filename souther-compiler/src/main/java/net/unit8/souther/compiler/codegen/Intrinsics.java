package net.unit8.souther.compiler.codegen;

import net.unit8.souther.compiler.CompileException;
import net.unit8.souther.compiler.check.Type;
import net.unit8.souther.compiler.core.Core;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static net.unit8.souther.compiler.codegen.Descriptors.*;

/**
 * The data-driven registry mapping each stdlib intrinsic key to its bytecode emission.
 *
 * <p>A runtime-backed primitive (a call to {@code Maps}/{@code Sets}/{@code Strings}/{@code Lists}/
 * {@code Temporals}) is emitted from one table row: the row names the runtime class, method, the
 * argument order (Souther puts the subject last for pipe reading; the runtime method takes it in a
 * fixed position, so the row carries the permutation), the argument slots the runtime method erases
 * to {@code Object} (map keys/values, set elements), and how to type the result. The JVM descriptor
 * is <em>derived</em> from the observed argument types and the result type — it is not restated, so
 * adding an intrinsic is a {@code .sou} signature, a runtime method, and one row here.
 *
 * <p>The JDK-native calls ({@code String} instance methods, {@code java.time} {@code plus*}) keep an
 * explicit descriptor because JDK signatures use {@code CharSequence}/{@code int}, which the boundary
 * derivation does not model. That set is stable — the JDK method surface does not grow with the
 * standard library.
 */
final class Intrinsics {

    private Intrinsics() {
    }

    /** Emits the intrinsic named by {@code key}, leaving its result on the stack, and returns the
     * result's Souther type (spec §stdlib). Throws on an unknown key — the checker admits only keys
     * that were declared {@code = intrinsic "..."} in a prelude module, so this is a compiler bug. */
    static Type emit(BodyGen g, String key, Core.Call call) {
        Emit e = TABLE.get(key);
        if (e == null) {
            throw new CompileException(call.pos(), "unknown intrinsic `" + key + "`");
        }
        return e.emit(g, call);
    }

    sealed interface Emit permits RuntimeStatic, JdkVirtual {
        Type emit(BodyGen g, Core.Call call);
    }

    /**
     * An {@code invokestatic} to a Souther runtime class. The descriptor is derived: each parameter is
     * {@code Object} when its argument occupies an erased slot ({@code objectSlots}), otherwise the
     * boundary form of the observed argument type; the return is the boundary form of the result type.
     * Arguments are emitted in {@code argOrder} (a permutation over all arguments); an erased slot is
     * boxed, which is a no-op for a reference.
     */
    record RuntimeStatic(ClassDesc owner, String method, int[] argOrder,
                         Set<Integer> objectSlots, Function<List<Type>, Type> result) implements Emit {
        public Type emit(BodyGen g, Core.Call call) {
            int n = argOrder.length;
            Type[] byArg = new Type[n];
            ClassDesc[] params = new ClassDesc[n];
            for (int j = 0; j < n; j++) {
                int src = argOrder[j];
                Type t = g.genExpr(call.args().get(src));
                boolean erased = objectSlots.contains(src);
                if (erased) {
                    g.emitBox(t);
                }
                byArg[src] = t;
                params[j] = erased ? CD_Object : boundaryDesc(t);
            }
            Type resultType = result.apply(List.of(byArg));
            g.emitInvokeStatic(owner, method, MethodTypeDesc.of(boundaryDesc(resultType), params));
            return resultType;
        }
    }

    /**
     * An {@code invokevirtual} to a JDK / {@code java.time} class with an explicit descriptor. The
     * first {@code argOrder} entry is the receiver; the rest are stack arguments. An argument listed in
     * {@code l2iArgs} is narrowed from Souther's {@code Int} (a {@code long}) to a JVM {@code int}.
     */
    record JdkVirtual(ClassDesc owner, String method, MethodTypeDesc desc, int[] argOrder,
                      Set<Integer> l2iArgs, Type result) implements Emit {
        public Type emit(BodyGen g, Core.Call call) {
            for (int src : argOrder) {
                g.genExpr(call.args().get(src));
                if (l2iArgs.contains(src)) {
                    g.emitL2i();
                }
            }
            g.emitInvokeVirtual(owner, method, desc);
            return result;
        }
    }

    /** The JVM type a value takes at a runtime-method boundary: primitives unboxed, containers as
     * their raw interface, everything else (references, type variables, tuples) as {@code Object}. */
    private static ClassDesc boundaryDesc(Type t) {
        if (t instanceof Type.Prim p) {
            return switch (p) {
                case INT -> ConstantDescs.CD_long;
                case BOOL -> ConstantDescs.CD_boolean;
                case DECIMAL -> CD_BigDecimal;
                case STRING -> CD_String;
                case DATE -> CD_LocalDate;
                case DATETIME -> CD_LocalDateTime;
                case RAW -> CD_Object;
            };
        }
        if (t instanceof Type.ListOf) {
            return CD_List;
        }
        if (t instanceof Type.MapOf) {
            return CD_Map;
        }
        if (t instanceof Type.SetOf) {
            return CD_Set;
        }
        return CD_Object;   // Ref, Var, Tuple, Option, Union, Nothing
    }

    // --- the registry ---

    private static final Map<String, Emit> TABLE = buildTable();

    private static int[] order(int... a) {
        return a;
    }

    private static Emit rt(ClassDesc owner, String method, int[] argOrder, Function<List<Type>, Type> result) {
        return new RuntimeStatic(owner, method, argOrder, Set.of(), result);
    }

    private static Emit rtErased(ClassDesc owner, String method, int[] argOrder, Set<Integer> objectSlots,
                                 Function<List<Type>, Type> result) {
        return new RuntimeStatic(owner, method, argOrder, objectSlots, result);
    }

    private static Emit jdk(ClassDesc owner, String method, MethodTypeDesc desc, int[] argOrder, Type result) {
        return new JdkVirtual(owner, method, desc, argOrder, Set.of(), result);
    }

    private static MethodTypeDesc mtd(ClassDesc ret, ClassDesc... params) {
        return MethodTypeDesc.of(ret, params);
    }

    private static Map<String, Emit> buildTable() {
        ClassDesc bool = ConstantDescs.CD_boolean;
        ClassDesc lng = ConstantDescs.CD_long;
        ClassDesc intCd = ConstantDescs.CD_int;
        Map<String, Emit> t = new java.util.LinkedHashMap<>();

        // String — JDK-native instance methods (explicit descriptor); receiver is the last Souther arg.
        t.put("string.trim", jdk(CD_String, "trim", mtd(CD_String), order(0), Type.STRING));
        t.put("string.lowercase", jdk(CD_String, "toLowerCase", mtd(CD_String), order(0), Type.STRING));
        t.put("string.uppercase", jdk(CD_String, "toUpperCase", mtd(CD_String), order(0), Type.STRING));
        t.put("string.contains", jdk(CD_String, "contains", mtd(bool, CD_CharSequence), order(1, 0), Type.BOOL));
        t.put("string.startsWith", jdk(CD_String, "startsWith", mtd(bool, CD_String), order(1, 0), Type.BOOL));
        t.put("string.endsWith", jdk(CD_String, "endsWith", mtd(bool, CD_String), order(1, 0), Type.BOOL));
        t.put("string.substring", new JdkVirtual(CD_String, "substring", mtd(CD_String, intCd, intCd),
                order(2, 0, 1), Set.of(0, 1), Type.STRING));
        t.put("string.concat", jdk(CD_String, "concat", mtd(CD_String, CD_String), order(0, 1), Type.STRING));
        // String — Strings runtime statics (descriptor derived).
        t.put("string.split", rt(CD_Strings, "split", order(1, 0), ts -> Type.list(Type.STRING)));
        t.put("string.join", rt(CD_Strings, "join", order(1, 0), ts -> Type.STRING));
        t.put("string.replace", rt(CD_Strings, "replace", order(2, 0, 1), ts -> Type.STRING));
        t.put("string.words", rt(CD_Strings, "words", order(0), ts -> Type.list(Type.STRING)));
        t.put("string.fromInt", rt(CD_Strings, "fromInt", order(0), ts -> Type.STRING));

        // List
        t.put("list.sort", rt(CD_Lists, "sort", order(0), ts -> ts.get(0)));
        t.put("list.reverse", rt(CD_Lists, "reverse", order(0), ts -> ts.get(0)));

        // Map — keys/values are erased to Object; the map argument stays a raw Map.
        t.put("map.containsKey", rtErased(CD_Maps, "containsKey", order(1, 0), Set.of(0), ts -> Type.BOOL));
        t.put("map.keys", rt(CD_Maps, "keys", order(0), ts -> Type.list(mapOf(ts, 0).key())));
        t.put("map.values", rt(CD_Maps, "values", order(0), ts -> Type.list(mapOf(ts, 0).value())));
        t.put("map.singleton", rtErased(CD_Maps, "singleton", order(0, 1), Set.of(0, 1),
                ts -> Type.map(ts.get(0), ts.get(1))));
        t.put("map.insert", rtErased(CD_Maps, "insert", order(0, 1, 2), Set.of(0, 1),
                Intrinsics::mapInsertResult));
        t.put("map.remove", rtErased(CD_Maps, "remove", order(0, 1), Set.of(0), ts -> ts.get(1)));
        t.put("map.isEmpty", rt(CD_Maps, "isEmpty", order(0), ts -> Type.BOOL));
        t.put("map.size", rt(CD_Maps, "size", order(0), ts -> Type.INT));
        t.put("map.toList", rt(CD_Maps, "toList", order(0), ts -> {
            Type.MapOf m = mapOf(ts, 0);
            return Type.list(Type.tuple(List.of(m.key(), m.value())));
        }));
        t.put("map.fromList", rt(CD_Maps, "fromList", order(0), Intrinsics::mapFromListResult));

        // Set — the element is erased to Object; a set argument stays a raw Set.
        t.put("set.singleton", rtErased(CD_Sets, "singleton", order(0), Set.of(0), ts -> Type.set(ts.get(0))));
        t.put("set.insert", rtErased(CD_Sets, "insert", order(0, 1), Set.of(0), Intrinsics::setInsertResult));
        t.put("set.remove", rtErased(CD_Sets, "remove", order(0, 1), Set.of(0), ts -> ts.get(1)));
        t.put("set.contains", rtErased(CD_Sets, "contains", order(0, 1), Set.of(0), ts -> Type.BOOL));
        t.put("set.union", rt(CD_Sets, "union", order(0, 1), ts -> setUnionType(ts.get(0), ts.get(1))));
        t.put("set.intersect", rt(CD_Sets, "intersect", order(0, 1), ts -> ts.get(0)));
        t.put("set.difference", rt(CD_Sets, "difference", order(0, 1), ts -> ts.get(0)));
        t.put("set.isEmpty", rt(CD_Sets, "isEmpty", order(0), ts -> Type.BOOL));
        t.put("set.size", rt(CD_Sets, "size", order(0), ts -> Type.INT));
        t.put("set.toList", rt(CD_Sets, "toList", order(0), ts -> Type.list(setOf(ts, 0).element())));
        t.put("set.fromList", rt(CD_Sets, "fromList", order(0), ts -> Type.set(listOf(ts, 0).element())));

        // Date / DateTime — the temporal is the receiver (emitted first); the count is a long.
        t.put("date.addDays", jdk(CD_LocalDate, "plusDays", mtd(CD_LocalDate, lng), order(1, 0), Type.DATE));
        t.put("date.addMonths", jdk(CD_LocalDate, "plusMonths", mtd(CD_LocalDate, lng), order(1, 0), Type.DATE));
        t.put("date.addYears", jdk(CD_LocalDate, "plusYears", mtd(CD_LocalDate, lng), order(1, 0), Type.DATE));
        t.put("date.daysBetween", rt(CD_Temporals, "daysBetween", order(0, 1), ts -> Type.INT));
        t.put("datetime.addMinutes",
                jdk(CD_LocalDateTime, "plusMinutes", mtd(CD_LocalDateTime, lng), order(1, 0), Type.DATETIME));
        t.put("datetime.addHours",
                jdk(CD_LocalDateTime, "plusHours", mtd(CD_LocalDateTime, lng), order(1, 0), Type.DATETIME));
        t.put("datetime.addDays",
                jdk(CD_LocalDateTime, "plusDays", mtd(CD_LocalDateTime, lng), order(1, 0), Type.DATETIME));
        t.put("datetime.minutesBetween", rt(CD_Temporals, "minutesBetween", order(0, 1), ts -> Type.INT));
        t.put("datetime.toDate",
                jdk(CD_LocalDateTime, "toLocalDate", mtd(CD_LocalDate), order(0), Type.DATE));

        return Map.copyOf(t);
    }

    // --- result-type formulas for the intrinsics whose result is learned from argument types ---

    private static Type.MapOf mapOf(List<Type> ts, int i) {
        return (Type.MapOf) ts.get(i);
    }

    private static Type.SetOf setOf(List<Type> ts, int i) {
        return (Type.SetOf) ts.get(i);
    }

    private static Type.ListOf listOf(List<Type> ts, int i) {
        return (Type.ListOf) ts.get(i);
    }

    /** {@code insert(k, v, m)} keeps the map's element types, filling either side from the inserted
     * key/value when the map is the empty-map bottom (a fresh {@code Map.empty}). */
    private static Type mapInsertResult(List<Type> ts) {
        Type kt = ts.get(0);
        Type vt = ts.get(1);
        Type.MapOf m = mapOf(ts, 2);
        Type ek = m.key();
        Type ev = m.value();
        return Type.map(ek instanceof Type.Nothing ? kt : ek, ev instanceof Type.Nothing ? vt : ev);
    }

    /** {@code fromList(entries)} reads the (key, value) types off the list's tuple element. */
    private static Type mapFromListResult(List<Type> ts) {
        Type elem = listOf(ts, 0).element();
        if (elem instanceof Type.TupleOf tp) {
            return Type.map(tp.elements().get(0), tp.elements().get(1));
        }
        return Type.map(Type.NOTHING, Type.NOTHING);
    }

    /** {@code insert(e, s)} keeps the set's element type, filling it from the inserted element when
     * the set is the empty-set bottom. */
    private static Type setInsertResult(List<Type> ts) {
        Type vt = ts.get(0);
        Type existing = setOf(ts, 1).element();
        return Type.set(existing instanceof Type.Nothing ? vt : existing);
    }

    /** The element type of a set union: the concrete side when the other is the empty-set bottom,
     * else the left (the checker has already required the two to agree). */
    private static Type setUnionType(Type a, Type b) {
        Type ae = ((Type.SetOf) a).element();
        Type be = ((Type.SetOf) b).element();
        return Type.set(ae instanceof Type.Nothing ? be : ae);
    }
}
