package net.unit8.souther.compiler.codegen;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.attribute.SourceFileAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.function.Consumer;

/**
 * The JVM class and method descriptors the backend emits against (spec sections 19, 20). These name
 * the runtime classes ({@code Result}/{@code Behavior}/{@code Maps}/{@code Sets}/...), the Raoh
 * decode/encode targets (spec 10.6), and the boxed-primitive and collection interfaces. Shared by
 * every generator in this package; a call site references one through a static import.
 */
final class Descriptors {

    private Descriptors() {
    }

    // Generated classes aren't loadable while we compile, so stack-map merging (e.g. an
    // `if` producing a union of two data types) can't resolve their common supertype.
    // Treat any class we can't resolve as a plain class extending Object.
    private static final ClassFile CF = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(
            desc -> {
                ClassHierarchyResolver.ClassHierarchyInfo info =
                        ClassHierarchyResolver.defaultResolver().getClassInfo(desc);
                return info != null
                        ? info
                        : ClassHierarchyResolver.ClassHierarchyInfo.ofClass(ConstantDescs.CD_Object);
            }));

    /**
     * Builds a class targeting Java 21 (spec 19.1).
     *
     * <p>The version is pinned rather than left to {@link ClassFile#of}, which defaults to the
     * JDK running the compiler: the generated code would then track whatever JDK built it, and
     * two developers on different JDKs would emit mutually incompatible artifacts.
     */
    static byte[] build(ClassDesc cd, Consumer<ClassBuilder> handler) {
        return CF.build(cd, cb -> {
            cb.withVersion(ClassFile.JAVA_21_VERSION, 0);
            cb.with(SourceFileAttribute.of(sourceFileName(cd)));
            handler.accept(cb);
        });
    }

    /**
     * The {@code .sou} source file a generated class came from, for its {@code SourceFile} attribute:
     * the module's simple name plus {@code .sou}. A class's package is its module name (a class is
     * {@code module + "." + name}), so the file name is derived from {@link ClassDesc#packageName()}
     * without threading the original path down. A single-file module named after its file (ADR-0043)
     * makes this exact; a multi-segment module resolves to its last segment.
     */
    private static String sourceFileName(ClassDesc cd) {
        String pkg = cd.packageName();
        if (pkg.isEmpty()) {
            return "source.sou";
        }
        int dot = pkg.lastIndexOf('.');
        return (dot >= 0 ? pkg.substring(dot + 1) : pkg) + ".sou";
    }

    static final ClassDesc CD_Object = ConstantDescs.CD_Object;
    static final ClassDesc CD_String = ConstantDescs.CD_String;
    static final ClassDesc CD_Long = ClassDesc.of("java.lang.Long");
    static final ClassDesc CD_CharSequence = ClassDesc.of("java.lang.CharSequence");
    static final ClassDesc CD_Map = ClassDesc.of("java.util.Map");
    static final ClassDesc CD_List = ClassDesc.of("java.util.List");
    static final ClassDesc CD_Set = ClassDesc.of("java.util.Set");
    static final ClassDesc CD_LinkedHashMap = ClassDesc.of("java.util.LinkedHashMap");
    static final ClassDesc CD_ArrayList = ClassDesc.of("java.util.ArrayList");
    static final ClassDesc CD_Collection = ClassDesc.of("java.util.Collection");
    static final ClassDesc CD_Result = ClassDesc.of("net.unit8.souther.runtime.Result");
    static final ClassDesc CD_ResultOk = CD_Result.nested("Ok");
    static final ClassDesc CD_ResultErr = CD_Result.nested("Err");
    static final ClassDesc CD_Behavior = ClassDesc.of("net.unit8.souther.runtime.Behavior");
    static final ClassDesc CD_Fn = ClassDesc.of("net.unit8.souther.runtime.Fn");
    static final MethodTypeDesc MTD_Fn_apply =
            MethodTypeDesc.of(ClassDesc.of("java.lang.Object"), ClassDesc.of("java.lang.Object").arrayType());
    static final ClassDesc CD_ConstraintViolation =
            ClassDesc.of("net.unit8.souther.runtime.ConstraintViolation");
    static final ClassDesc CD_IntMath = ClassDesc.of("net.unit8.souther.runtime.IntMath");
    /** {@code (long, long) -> long}: overflow-checked Int arithmetic (spec 18.2). */
    static final MethodTypeDesc MTD_intExact =
            MethodTypeDesc.of(ConstantDescs.CD_long, ConstantDescs.CD_long, ConstantDescs.CD_long);
    static final ClassDesc CD_DivisionByZero = ClassDesc.of("net.unit8.souther.runtime.DivisionByZero");
    static final ClassDesc CD_Boolean = ClassDesc.of("java.lang.Boolean");
    static final ClassDesc CD_BigDecimal = ClassDesc.of("java.math.BigDecimal");
    static final ClassDesc CD_DecimalMath = ClassDesc.of("net.unit8.souther.runtime.DecimalMath");
    /** {@code BigDecimal add/subtract/multiply(BigDecimal) -> BigDecimal}: the Decimal `+ - *` ops. */
    static final MethodTypeDesc MTD_bdArith = MethodTypeDesc.of(CD_BigDecimal, CD_BigDecimal);
    /** {@code DecimalMath.divide(BigDecimal, BigDecimal) -> BigDecimal}: the Decimal `/` operator. */
    static final MethodTypeDesc MTD_bdDivideOp =
            MethodTypeDesc.of(CD_BigDecimal, CD_BigDecimal, CD_BigDecimal);
    static final ClassDesc CD_RoundingMode = ClassDesc.of("java.math.RoundingMode");
    /** {@code BigDecimal.divide(BigDecimal, int, RoundingMode)} (spec 18.3). */
    static final MethodTypeDesc MTD_bdDivide =
            MethodTypeDesc.of(CD_BigDecimal, CD_BigDecimal, ConstantDescs.CD_int, CD_RoundingMode);
    static final ClassDesc CD_LocalDate = ClassDesc.of("java.time.LocalDate");
    static final ClassDesc CD_LocalDateTime = ClassDesc.of("java.time.LocalDateTime");
    static final ClassDesc CD_Lists = ClassDesc.of("net.unit8.souther.runtime.Lists");
    static final ClassDesc CD_Strings = ClassDesc.of("net.unit8.souther.runtime.Strings");
    static final ClassDesc CD_Maps = ClassDesc.of("net.unit8.souther.runtime.Maps");
    static final ClassDesc CD_Sets = ClassDesc.of("net.unit8.souther.runtime.Sets");
    static final ClassDesc CD_Temporals = ClassDesc.of("net.unit8.souther.runtime.Temporals");
    static final ClassDesc CD_Option = ClassDesc.of("net.unit8.souther.runtime.Option");
    static final ClassDesc CD_OptionSome = CD_Option.nested("Some");
    static final ClassDesc CD_OptionNone = CD_Option.nested("None");
    static final ClassDesc CD_IllegalStateException = ClassDesc.of("java.lang.IllegalStateException");

    static final MethodTypeDesc MTD_void = MethodTypeDesc.of(ConstantDescs.CD_void);
    static final MethodTypeDesc MTD_Result_Object = MethodTypeDesc.of(CD_Result, CD_Object);
    static final MethodTypeDesc MTD_Object = MethodTypeDesc.of(CD_Object);
    static final MethodTypeDesc MTD_size = MethodTypeDesc.of(ConstantDescs.CD_int);
    static final MethodTypeDesc MTD_ArrayList_add = MethodTypeDesc.of(ConstantDescs.CD_boolean, CD_Object);
    static final MethodTypeDesc MTD_List_copyOf = MethodTypeDesc.of(CD_List, CD_Collection);
    static final MethodTypeDesc MTD_Lists_concat = MethodTypeDesc.of(CD_List, CD_List, CD_List);
    static final MethodTypeDesc MTD_Lists_sort = MethodTypeDesc.of(CD_List, CD_List);
    static final MethodTypeDesc MTD_Strings_split = MethodTypeDesc.of(CD_List, CD_String, CD_String);
    static final MethodTypeDesc MTD_Strings_join = MethodTypeDesc.of(CD_String, CD_List, CD_String);
    static final MethodTypeDesc MTD_Strings_replace =
            MethodTypeDesc.of(CD_String, CD_String, CD_String, CD_String);
    static final MethodTypeDesc MTD_Strings_words = MethodTypeDesc.of(CD_List, CD_String);
    static final MethodTypeDesc MTD_Strings_fromInt = MethodTypeDesc.of(CD_String, ConstantDescs.CD_long);
    static final ClassDesc CD_Comparable = ClassDesc.of("java.lang.Comparable");
    static final MethodTypeDesc MTD_compareTo_Object = MethodTypeDesc.of(ConstantDescs.CD_int, CD_Object);
    static final MethodTypeDesc MTD_Map_put = MethodTypeDesc.of(CD_Object, CD_Object, CD_Object);
    static final MethodTypeDesc MTD_apply = MethodTypeDesc.of(CD_Object, CD_Object);
    static final MethodTypeDesc MTD_orThrow = MethodTypeDesc.of(CD_Object, CD_Result);
    static final MethodTypeDesc MTD_Long_valueOf = MethodTypeDesc.of(CD_Long, ConstantDescs.CD_long);
    static final MethodTypeDesc MTD_Boolean_valueOf =
            MethodTypeDesc.of(CD_Boolean, ConstantDescs.CD_boolean);

    // --- Raoh 0.6.0 decode/encode targets (generated code depends on Raoh directly; spec 10.6) ---
    static final ClassDesc CD_Class = ClassDesc.of("java.lang.Class");
    static final ClassDesc CD_RDecoder = ClassDesc.of("net.unit8.raoh.decode.Decoder");
    static final ClassDesc CD_REncoder = ClassDesc.of("net.unit8.raoh.encode.Encoder");
    static final ClassDesc CD_RResult = ClassDesc.of("net.unit8.raoh.Result");
    static final ClassDesc CD_ROk = ClassDesc.of("net.unit8.raoh.Ok");
    static final ClassDesc CD_RErr = ClassDesc.of("net.unit8.raoh.Err");
    static final ClassDesc CD_RIssues = ClassDesc.of("net.unit8.raoh.Issues");
    static final ClassDesc CD_RPath = ClassDesc.of("net.unit8.raoh.Path");
    static final ClassDesc CD_ObjectDecoders = ClassDesc.of("net.unit8.raoh.decode.ObjectDecoders");
    static final ClassDesc CD_MapDecoders = ClassDesc.of("net.unit8.raoh.decode.map.MapDecoders");
    static final ClassDesc CD_JsonDecoders = ClassDesc.of("net.unit8.raoh.json.JsonDecoders");
    static final ClassDesc CD_JooqDecoders = ClassDesc.of("net.unit8.raoh.jooq.JooqRecordDecoders");
    static final ClassDesc CD_RDecoders = ClassDesc.of("net.unit8.raoh.decode.Decoders");
    static final ClassDesc CD_RVariant = CD_RDecoders.nested("Variant");
    static final ClassDesc CD_FieldDecoder = ClassDesc.of("net.unit8.raoh.decode.FieldDecoder");
    static final ClassDesc CD_StringDecoder = ClassDesc.of("net.unit8.raoh.decode.builtin.StringDecoder");
    static final ClassDesc CD_LongDecoder = ClassDesc.of("net.unit8.raoh.decode.builtin.LongDecoder");
    static final ClassDesc CD_BoolDecoder = ClassDesc.of("net.unit8.raoh.decode.builtin.BoolDecoder");
    static final ClassDesc CD_DecimalDecoder = ClassDesc.of("net.unit8.raoh.decode.builtin.DecimalDecoder");
    static final ClassDesc CD_TemporalDecoder = ClassDesc.of("net.unit8.raoh.decode.builtin.TemporalDecoder");
    static final ClassDesc CD_ListDecoder = ClassDesc.of("net.unit8.raoh.decode.builtin.ListDecoder");
    static final ClassDesc CD_RecordDecoder = ClassDesc.of("net.unit8.raoh.decode.builtin.RecordDecoder");
    static final ClassDesc CD_ObjectEncoders = ClassDesc.of("net.unit8.raoh.encode.ObjectEncoders");
    static final ClassDesc CD_MapEncoders = ClassDesc.of("net.unit8.raoh.encode.MapEncoders");
    static final ClassDesc CD_MapEncVariant = CD_MapEncoders.nested("Variant");

    // Souther Result stays for the behavior/__construct side (Raoh-free)
    static final MethodTypeDesc MTD_Result_ok = MethodTypeDesc.of(CD_Result, CD_Object);
    static final MethodTypeDesc MTD_Result_err = MethodTypeDesc.of(CD_Result, CD_Object);
    // Raoh decode/encode SAMs, factories, combinators
    static final MethodTypeDesc MTD_Rdecode = MethodTypeDesc.of(CD_RResult, CD_Object, CD_RPath);
    static final MethodTypeDesc MTD_Rencode = MethodTypeDesc.of(CD_Object, CD_Object);
    static final MethodTypeDesc MTD_Rdecoder = MethodTypeDesc.of(CD_RDecoder);
    static final MethodTypeDesc MTD_Rencoder = MethodTypeDesc.of(CD_REncoder);
    static final MethodTypeDesc MTD_leafString = MethodTypeDesc.of(CD_StringDecoder);
    static final MethodTypeDesc MTD_leafLong = MethodTypeDesc.of(CD_LongDecoder);
    static final MethodTypeDesc MTD_leafBool = MethodTypeDesc.of(CD_BoolDecoder);
    static final MethodTypeDesc MTD_leafDecimal = MethodTypeDesc.of(CD_DecimalDecoder);
    static final MethodTypeDesc MTD_leafTemporal = MethodTypeDesc.of(CD_TemporalDecoder);
    static final MethodTypeDesc MTD_field = MethodTypeDesc.of(CD_FieldDecoder, CD_String, CD_RDecoder);
    static final MethodTypeDesc MTD_optionalField = MethodTypeDesc.of(CD_RDecoder, CD_String, CD_RDecoder);
    static final MethodTypeDesc MTD_listDec = MethodTypeDesc.of(CD_ListDecoder, CD_RDecoder);
    static final MethodTypeDesc MTD_mapDec = MethodTypeDesc.of(CD_RecordDecoder, CD_RDecoder);
    static final MethodTypeDesc MTD_Rvariant = MethodTypeDesc.of(CD_RVariant, CD_String, CD_RDecoder);
    static final MethodTypeDesc MTD_Rdiscriminate =
            MethodTypeDesc.of(CD_RDecoder, CD_String, CD_RDecoder, CD_RVariant.arrayType());
    static final MethodTypeDesc MTD_Rok = MethodTypeDesc.of(CD_RResult, CD_Object);
    static final MethodTypeDesc MTD_Rerr = MethodTypeDesc.of(CD_RResult, CD_RIssues);
    static final MethodTypeDesc MTD_Rfail = MethodTypeDesc.of(CD_RResult, CD_RPath, CD_String, CD_String);
    static final MethodTypeDesc MTD_Rencode_leaf = MethodTypeDesc.of(CD_REncoder);
    static final MethodTypeDesc MTD_Rencode_list = MethodTypeDesc.of(CD_REncoder, CD_REncoder);
    static final ClassDesc CD_Function = ClassDesc.of("java.util.function.Function");
    // Newtype-keyed Map codec: decode remaps String keys into the key newtype via
    // flatMapWithPath + a generated $Dec.__rekey helper; encode renders keys bare via Maps.mapKeys.
    static final ClassDesc CD_BiFunction = ClassDesc.of("java.util.function.BiFunction");
    static final ClassDesc CD_Iterator = ClassDesc.of("java.util.Iterator");
    static final ClassDesc CD_MapEntry = CD_Map.nested("Entry");
    static final MethodTypeDesc MTD_flatMapWithPath = MethodTypeDesc.of(CD_RDecoder, CD_BiFunction);
    static final MethodTypeDesc MTD_rekey = MethodTypeDesc.of(CD_RResult, CD_Map, CD_RPath);
    static final MethodTypeDesc MTD_entrySet = MethodTypeDesc.of(CD_Set);
    static final MethodTypeDesc MTD_iterator = MethodTypeDesc.of(CD_Iterator);
    static final MethodTypeDesc MTD_hasNext = MethodTypeDesc.of(ConstantDescs.CD_boolean);
    static final MethodTypeDesc MTD_getKeyValue = MethodTypeDesc.of(CD_Object);
    static final MethodTypeDesc MTD_Path_append = MethodTypeDesc.of(CD_RPath, CD_String);
    static final MethodTypeDesc MTD_mapKeys = MethodTypeDesc.of(CD_Map, CD_Map, CD_Function);
    static final MethodTypeDesc MTD_value = MethodTypeDesc.of(CD_String);
    /** {@code Decoder.map(Function)}: turns a {@code Decoder<I, List<T>>} into a {@code Decoder<I, Set<T>>}. */
    static final MethodTypeDesc MTD_Rdecoder_map = MethodTypeDesc.of(CD_RDecoder, CD_Function);
    /** {@code LambdaMetafactory.metafactory} — the bootstrap that materialises a method reference as a
     *  functional-interface instance, so {@code Sets::fromList} becomes a {@code Function} for {@code map}. */
    static final DirectMethodHandleDesc BSM_METAFACTORY = MethodHandleDesc.ofMethod(
            DirectMethodHandleDesc.Kind.STATIC,
            ClassDesc.of("java.lang.invoke.LambdaMetafactory"), "metafactory",
            MethodTypeDesc.of(
                    ClassDesc.of("java.lang.invoke.CallSite"),
                    ClassDesc.of("java.lang.invoke.MethodHandles").nested("Lookup"),
                    ClassDesc.of("java.lang.String"),
                    ClassDesc.of("java.lang.invoke.MethodType"),
                    ClassDesc.of("java.lang.invoke.MethodType"),
                    ClassDesc.of("java.lang.invoke.MethodHandle"),
                    ClassDesc.of("java.lang.invoke.MethodType")));
    static final MethodTypeDesc MTD_Rencode_variant =
            MethodTypeDesc.of(CD_MapEncVariant, CD_Class, CD_String, CD_REncoder);
    static final MethodTypeDesc MTD_Rencode_discriminate =
            MethodTypeDesc.of(CD_REncoder, CD_String, CD_MapEncVariant.arrayType());
    static final MethodTypeDesc MTD_Issues_merge = MethodTypeDesc.of(CD_RIssues, CD_RIssues);
    static final MethodTypeDesc MTD_Issues_isEmpty = MethodTypeDesc.of(ConstantDescs.CD_boolean);
    static final MethodTypeDesc MTD_Err_issues = MethodTypeDesc.of(CD_RIssues);
    static final MethodTypeDesc MTD_nested = MethodTypeDesc.of(CD_RDecoder, CD_RDecoder);
    static final ClassDesc CD_JavaOptional = ClassDesc.of("java.util.Optional");
    static final MethodTypeDesc MTD_ofOptional = MethodTypeDesc.of(CD_Option, CD_JavaOptional);
    static final MethodTypeDesc MTD_error = MethodTypeDesc.of(CD_Object);
    // Per-source (JSON / jOOQ) decode targets (spec 10.6). JooqRecordDecoders.field returns a
    // JooqRecordDecoder (not FieldDecoder); JsonDecoders.field returns FieldDecoder like the map source.
    static final ClassDesc CD_JooqRecordDecoder = ClassDesc.of("net.unit8.raoh.jooq.JooqRecordDecoder");
    static final MethodTypeDesc MTD_fieldJooq =
            MethodTypeDesc.of(CD_JooqRecordDecoder, CD_String, CD_RDecoder);
    static final MethodTypeDesc MTD_optFieldJooq =
            MethodTypeDesc.of(CD_JooqRecordDecoder, CD_String, CD_RDecoder);

    // Value-equality / hashCode targets, shared by the value-class equality codegen and the `==`
    // match arm (a Decimal compares by value, a reference through Objects.equals).
    static final ClassDesc CD_Objects = ClassDesc.of("java.util.Objects");
    static final MethodTypeDesc MTD_BD_compareTo =
            MethodTypeDesc.of(ConstantDescs.CD_int, CD_BigDecimal);
    static final MethodTypeDesc MTD_BD_strip = MethodTypeDesc.of(CD_BigDecimal);
    static final MethodTypeDesc MTD_Objects_equals =
            MethodTypeDesc.of(ConstantDescs.CD_boolean, CD_Object, CD_Object);
    static final MethodTypeDesc MTD_Objects_hashCode =
            MethodTypeDesc.of(ConstantDescs.CD_int, CD_Object);
    static final MethodTypeDesc MTD_Long_hashCode =
            MethodTypeDesc.of(ConstantDescs.CD_int, ConstantDescs.CD_long);
}
