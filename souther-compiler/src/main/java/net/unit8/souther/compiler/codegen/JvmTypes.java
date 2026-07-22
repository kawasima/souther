package net.unit8.souther.compiler.codegen;

import net.unit8.souther.compiler.check.Type;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static net.unit8.souther.compiler.codegen.Descriptors.*;

/**
 * Type-shaped bytecode helpers. The first group depends on nothing but the {@link Type} and the
 * descriptors: how a value is boxed, loaded, stored, and how wide its JVM slot is. The second group
 * ({@code jvmType}/{@code fieldDescs}/{@code unbox}/{@code castFromObject}) resolves a data name to a
 * class through the module's package map, so each takes a {@link CodegenContext} to reach
 * {@code caseClass}.
 */
final class JvmTypes {

    private JvmTypes() {
    }

    /** Boxes an {@code Int}/{@code Bool} on the stack; a no-op for a reference, already an Object. */
    static void box(CodeBuilder code, Type type) {
        if (type == Type.INT) {
            code.invokestatic(CD_Long, "valueOf", MTD_Long_valueOf);
        } else if (type == Type.BOOL) {
            code.invokestatic(CD_Boolean, "valueOf", MTD_Boolean_valueOf);
        }
    }

    /** The boxed JVM class for a primitive type, or {@code null} for a non-primitive. */
    static ClassDesc boxedPrim(Type t) {
        if (t == Type.INT) return CD_Long;
        if (t == Type.BOOL) return CD_Boolean;
        if (t == Type.DECIMAL) return CD_BigDecimal;
        if (t == Type.STRING) return CD_String;
        if (t == Type.DATE) return CD_LocalDate;
        if (t == Type.DATETIME) return CD_LocalDateTime;
        return null;
    }

    /** True when {@code t} is carried as a reference on the JVM (everything but Int and Bool). */
    static boolean isReference(Type t) {
        return t != Type.INT && t != Type.BOOL;
    }

    static void load(CodeBuilder code, int slot, Type type) {
        if (type == Type.INT) {
            code.lload(slot);
        } else if (type == Type.BOOL) {
            code.iload(slot);
        } else {
            code.aload(slot); // String or a data reference
        }
    }

    static void store(CodeBuilder code, int slot, Type type) {
        if (type == Type.INT) {
            code.lstore(slot);
        } else if (type == Type.BOOL) {
            code.istore(slot);
        } else {
            code.astore(slot);
        }
    }

    static void pushInt(CodeBuilder code, int v) {
        switch (v) {
            case 0 -> code.iconst_0();
            case 1 -> code.iconst_1();
            case 2 -> code.iconst_2();
            case 3 -> code.iconst_3();
            case 4 -> code.iconst_4();
            case 5 -> code.iconst_5();
            default -> code.loadConstant(Integer.valueOf(v));
        }
    }

    /** The JVM slot width of {@code type}: an {@code Int} is a {@code long} (two slots), else one. */
    static int width(Type type) {
        return type == Type.INT ? 2 : 1;
    }

    /** Emits a package-private default constructor that chains to {@code Object.<init>}. */
    static void emitDefaultCtor(ClassBuilder cb) {
        cb.withMethodBody("<init>", MTD_void, 0, code -> {
            code.aload(0);
            code.invokespecial(CD_Object, "<init>", MTD_void);
            code.return_();
        });
    }

    /** Emits a {@code public} default constructor that chains to {@code Object.<init>}. */
    static void emitPublicCtor(ClassBuilder cb) {
        cb.withMethodBody("<init>", MTD_void, ClassFile.ACC_PUBLIC, code -> {
            code.aload(0);
            code.invokespecial(CD_Object, "<init>", MTD_void);
            code.return_();
        });
    }

    /**
     * Emits value equality for two {@code Decimal}s on the stack, leaving a boolean.
     *
     * <p>{@code BigDecimal.equals} also compares the scale, so it calls 1.0 and 1.00 different.
     * Scale is how a number was written, not what it is (spec 7.1): the same amount arrives with
     * a different scale depending on whether it was read from JSON or a DB column, and a money
     * type whose equality turns on that is a trap. Compare by value instead — as Clojure, Scala
     * and Ceylon all chose for the same reason.
     */
    static void emitDecimalEquals(CodeBuilder code) {
        code.invokevirtual(CD_BigDecimal, "compareTo", MTD_BD_compareTo);
        Label eq = code.newLabel();
        Label done = code.newLabel();
        code.ifeq(eq);
        code.iconst_0();
        code.goto_(done);
        code.labelBinding(eq);
        code.iconst_1();
        code.labelBinding(done);
    }

    // --- reference-resolving members: these reach the module's package map through the context ---

    /** The JVM class carrying a value of {@code type}: primitives unboxed, containers as their raw
     * interface, a data reference through {@link CodegenContext#caseClass}. */
    static ClassDesc jvmType(Type type, CodegenContext ctx) {
        if (type == Type.INT) return ConstantDescs.CD_long;
        if (type == Type.STRING) return CD_String;
        if (type == Type.BOOL) return ConstantDescs.CD_boolean;
        if (type == Type.DECIMAL) return CD_BigDecimal;
        if (type == Type.DATE) return CD_LocalDate;
        if (type == Type.DATETIME) return CD_LocalDateTime;
        if (type instanceof Type.OptionOf) return CD_Option;
        if (type instanceof Type.ListOf) return CD_List;
        if (type instanceof Type.MapOf) return CD_Map;
        if (type instanceof Type.SetOf) return CD_Set;
        if (type instanceof Type.Union) return CD_Object;
        if (type instanceof Type.Var) return CD_Object;   // a type variable is erased to Object
        if (type instanceof Type.Nothing) return CD_Object;   // an empty collection's element bottom
        if (type instanceof Type.FnOf) return CD_Fn;
        if (type instanceof Type.TupleOf) return CD_Object.arrayType();   // a tuple is an Object[]
        return ctx.caseClass(((Type.Ref) type).name());
    }

    static ClassDesc[] fieldDescs(Map<String, Type> fields, CodegenContext ctx) {
        List<ClassDesc> descs = new ArrayList<>();
        for (Type t : fields.values()) {
            descs.add(jvmType(t, ctx));
        }
        return descs.toArray(new ClassDesc[0]);
    }

    /**
     * The generic type signature for a container-typed field or accessor
     * ({@code List<E>} / {@code Set<E>} / {@code Map<K,V>} / {@code Option<E>}), or {@code null} for a
     * type whose raw descriptor already names it fully (a scalar, a data reference, a union). Attached
     * as a {@code Signature} attribute so a Java consumer recovers the element type instead of a raw
     * {@code List} it has to cast out of.
     */
    static String genericSig(Type type, CodegenContext ctx) {
        return switch (type) {
            case Type.ListOf l -> "Ljava/util/List<" + refSig(l.element(), ctx) + ">;";
            case Type.SetOf s -> "Ljava/util/Set<" + refSig(s.element(), ctx) + ">;";
            case Type.OptionOf o -> "Lnet/unit8/souther/runtime/Option<" + refSig(o.element(), ctx) + ">;";
            case Type.MapOf m -> "Ljava/util/Map<" + refSig(m.key(), ctx) + refSig(m.value(), ctx) + ">;";
            default -> null;
        };
    }

    /**
     * An element type's signature as a reference (generic arguments are never primitive, so a
     * primitive element takes its boxed class — {@code Int} is {@code Long}, {@code Bool} is
     * {@code Boolean}). A nested container recurses ({@code List<List<E>>}); any other type's raw
     * descriptor is already a reference.
     */
    private static String refSig(Type element, CodegenContext ctx) {
        String nested = genericSig(element, ctx);
        if (nested != null) return nested;
        ClassDesc boxed = boxedPrim(element);
        return (boxed != null ? boxed : jvmType(element, ctx)).descriptorString();
    }

    /** Unboxes/casts the {@code Object} on the stack to {@code type}'s JVM form and stores it in
     * {@code slot}. */
    static void unbox(CodeBuilder code, Type type, int slot, CodegenContext ctx) {
        if (type == Type.INT) {
            code.checkcast(CD_Long);
            code.invokevirtual(CD_Long, "longValue", MethodTypeDesc.of(ConstantDescs.CD_long));
            code.lstore(slot);
        } else if (type == Type.BOOL) {
            code.checkcast(CD_Boolean);
            code.invokevirtual(CD_Boolean, "booleanValue", MethodTypeDesc.of(ConstantDescs.CD_boolean));
            code.istore(slot);
        } else {
            code.checkcast(jvmType(type, ctx));
            code.astore(slot);
        }
    }

    /** Casts the {@code Object} on the stack to {@code type}'s JVM form, unboxing Int/Bool — the
     * stack-only counterpart of {@link #unbox}, used to read a boxed tuple element (ADR-0036). */
    static void castFromObject(CodeBuilder code, Type type, CodegenContext ctx) {
        if (type == Type.INT) {
            code.checkcast(CD_Long);
            code.invokevirtual(CD_Long, "longValue", MethodTypeDesc.of(ConstantDescs.CD_long));
        } else if (type == Type.BOOL) {
            code.checkcast(CD_Boolean);
            code.invokevirtual(CD_Boolean, "booleanValue", MethodTypeDesc.of(ConstantDescs.CD_boolean));
        } else {
            code.checkcast(jvmType(type, ctx));
        }
    }
}
