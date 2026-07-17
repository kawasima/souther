package net.unit8.souther.compiler.codegen;

import net.unit8.souther.compiler.CompileException;
import net.unit8.souther.compiler.ast.Ast;
import net.unit8.souther.compiler.check.HelperInliner;
import net.unit8.souther.compiler.check.Type;
import net.unit8.souther.compiler.check.TypeChecker;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassSignature;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.MethodSignature;
import java.lang.classfile.attribute.PermittedSubclassesAttribute;
import java.lang.classfile.attribute.SignatureAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The ClassFile-API backend (spec sections 19, 20). Emits JVM bytecode directly for each
 * {@code data}: the value class (package-private ctor + invariant-checking
 * {@code __construct}) and nested {@code $Dec}/{@code $Enc} classes. Fields may reference
 * other data types; object decoders accumulate every field error (spec sections 15, 27.7).
 */
public final class Backend {

    private static final ClassDesc CD_Object = ConstantDescs.CD_Object;
    private static final ClassDesc CD_String = ConstantDescs.CD_String;
    private static final ClassDesc CD_Long = ClassDesc.of("java.lang.Long");
    private static final ClassDesc CD_CharSequence = ClassDesc.of("java.lang.CharSequence");
    private static final ClassDesc CD_Map = ClassDesc.of("java.util.Map");
    private static final ClassDesc CD_List = ClassDesc.of("java.util.List");
    private static final ClassDesc CD_LinkedHashMap = ClassDesc.of("java.util.LinkedHashMap");
    private static final ClassDesc CD_ArrayList = ClassDesc.of("java.util.ArrayList");
    private static final ClassDesc CD_Collection = ClassDesc.of("java.util.Collection");
    private static final ClassDesc CD_Result = ClassDesc.of("net.unit8.souther.runtime.Result");
    private static final ClassDesc CD_ResultOk = CD_Result.nested("Ok");
    private static final ClassDesc CD_ResultErr = CD_Result.nested("Err");
    private static final ClassDesc CD_Behavior = ClassDesc.of("net.unit8.souther.runtime.Behavior");
    private static final ClassDesc CD_ConstraintViolation =
            ClassDesc.of("net.unit8.souther.runtime.ConstraintViolation");
    private static final ClassDesc CD_IntMath = ClassDesc.of("net.unit8.souther.runtime.IntMath");
    /** {@code (long, long) -> long}: overflow-checked Int arithmetic (spec 18.2). */
    private static final MethodTypeDesc MTD_intExact =
            MethodTypeDesc.of(ConstantDescs.CD_long, ConstantDescs.CD_long, ConstantDescs.CD_long);
    private static final ClassDesc CD_DivisionByZero = ClassDesc.of("net.unit8.souther.runtime.DivisionByZero");
    private static final ClassDesc CD_Boolean = ClassDesc.of("java.lang.Boolean");
    private static final ClassDesc CD_BigDecimal = ClassDesc.of("java.math.BigDecimal");
    private static final ClassDesc CD_RoundingMode = ClassDesc.of("java.math.RoundingMode");
    /** {@code BigDecimal.divide(BigDecimal, int, RoundingMode)} (spec 18.3). */
    private static final MethodTypeDesc MTD_bdDivide =
            MethodTypeDesc.of(CD_BigDecimal, CD_BigDecimal, ConstantDescs.CD_int, CD_RoundingMode);
    private static final ClassDesc CD_LocalDate = ClassDesc.of("java.time.LocalDate");
    private static final ClassDesc CD_LocalDateTime = ClassDesc.of("java.time.LocalDateTime");
    private static final ClassDesc CD_Lists = ClassDesc.of("net.unit8.souther.runtime.Lists");
    private static final ClassDesc CD_Maps = ClassDesc.of("net.unit8.souther.runtime.Maps");
    private static final ClassDesc CD_Option = ClassDesc.of("net.unit8.souther.runtime.Option");
    private static final ClassDesc CD_OptionSome = CD_Option.nested("Some");
    private static final ClassDesc CD_OptionNone = CD_Option.nested("None");
    private static final ClassDesc CD_IllegalStateException = ClassDesc.of("java.lang.IllegalStateException");

    private static final MethodTypeDesc MTD_void = MethodTypeDesc.of(ConstantDescs.CD_void);
    private static final MethodTypeDesc MTD_Result_Object = MethodTypeDesc.of(CD_Result, CD_Object);
    private static final MethodTypeDesc MTD_Object = MethodTypeDesc.of(CD_Object);
    private static final MethodTypeDesc MTD_size = MethodTypeDesc.of(ConstantDescs.CD_int);
    private static final MethodTypeDesc MTD_ArrayList_add = MethodTypeDesc.of(ConstantDescs.CD_boolean, CD_Object);
    private static final MethodTypeDesc MTD_List_copyOf = MethodTypeDesc.of(CD_List, CD_Collection);
    private static final MethodTypeDesc MTD_Lists_concat = MethodTypeDesc.of(CD_List, CD_List, CD_List);
    private static final MethodTypeDesc MTD_Map_put = MethodTypeDesc.of(CD_Object, CD_Object, CD_Object);
    private static final MethodTypeDesc MTD_apply = MethodTypeDesc.of(CD_Object, CD_Object);
    private static final MethodTypeDesc MTD_orThrow = MethodTypeDesc.of(CD_Object, CD_Result);
    private static final MethodTypeDesc MTD_Long_valueOf = MethodTypeDesc.of(CD_Long, ConstantDescs.CD_long);
    private static final MethodTypeDesc MTD_Boolean_valueOf =
            MethodTypeDesc.of(CD_Boolean, ConstantDescs.CD_boolean);

    // --- Raoh 0.6.0 decode/encode targets (generated code depends on Raoh directly; spec 10.6) ---
    private static final ClassDesc CD_Class = ClassDesc.of("java.lang.Class");
    private static final ClassDesc CD_RDecoder = ClassDesc.of("net.unit8.raoh.decode.Decoder");
    private static final ClassDesc CD_REncoder = ClassDesc.of("net.unit8.raoh.encode.Encoder");
    private static final ClassDesc CD_RResult = ClassDesc.of("net.unit8.raoh.Result");
    private static final ClassDesc CD_ROk = ClassDesc.of("net.unit8.raoh.Ok");
    private static final ClassDesc CD_RErr = ClassDesc.of("net.unit8.raoh.Err");
    private static final ClassDesc CD_RIssues = ClassDesc.of("net.unit8.raoh.Issues");
    private static final ClassDesc CD_RPath = ClassDesc.of("net.unit8.raoh.Path");
    private static final ClassDesc CD_ObjectDecoders = ClassDesc.of("net.unit8.raoh.decode.ObjectDecoders");
    private static final ClassDesc CD_MapDecoders = ClassDesc.of("net.unit8.raoh.decode.map.MapDecoders");
    private static final ClassDesc CD_JsonDecoders = ClassDesc.of("net.unit8.raoh.json.JsonDecoders");
    private static final ClassDesc CD_JooqDecoders = ClassDesc.of("net.unit8.raoh.jooq.JooqRecordDecoders");
    private static final ClassDesc CD_RDecoders = ClassDesc.of("net.unit8.raoh.decode.Decoders");
    private static final ClassDesc CD_RVariant = CD_RDecoders.nested("Variant");
    private static final ClassDesc CD_FieldDecoder = ClassDesc.of("net.unit8.raoh.decode.FieldDecoder");
    private static final ClassDesc CD_StringDecoder = ClassDesc.of("net.unit8.raoh.decode.builtin.StringDecoder");
    private static final ClassDesc CD_LongDecoder = ClassDesc.of("net.unit8.raoh.decode.builtin.LongDecoder");
    private static final ClassDesc CD_BoolDecoder = ClassDesc.of("net.unit8.raoh.decode.builtin.BoolDecoder");
    private static final ClassDesc CD_DecimalDecoder = ClassDesc.of("net.unit8.raoh.decode.builtin.DecimalDecoder");
    private static final ClassDesc CD_TemporalDecoder = ClassDesc.of("net.unit8.raoh.decode.builtin.TemporalDecoder");
    private static final ClassDesc CD_ListDecoder = ClassDesc.of("net.unit8.raoh.decode.builtin.ListDecoder");
    private static final ClassDesc CD_RecordDecoder = ClassDesc.of("net.unit8.raoh.decode.builtin.RecordDecoder");
    private static final ClassDesc CD_ObjectEncoders = ClassDesc.of("net.unit8.raoh.encode.ObjectEncoders");
    private static final ClassDesc CD_MapEncoders = ClassDesc.of("net.unit8.raoh.encode.MapEncoders");
    private static final ClassDesc CD_MapEncVariant = CD_MapEncoders.nested("Variant");

    // Souther Result stays for the behavior/__construct side (Raoh-free)
    private static final MethodTypeDesc MTD_Result_ok = MethodTypeDesc.of(CD_Result, CD_Object);
    private static final MethodTypeDesc MTD_Result_err = MethodTypeDesc.of(CD_Result, CD_Object);
    // Raoh decode/encode SAMs, factories, combinators
    private static final MethodTypeDesc MTD_Rdecode = MethodTypeDesc.of(CD_RResult, CD_Object, CD_RPath);
    private static final MethodTypeDesc MTD_Rencode = MethodTypeDesc.of(CD_Object, CD_Object);
    private static final MethodTypeDesc MTD_Rdecoder = MethodTypeDesc.of(CD_RDecoder);
    private static final MethodTypeDesc MTD_Rencoder = MethodTypeDesc.of(CD_REncoder);
    private static final MethodTypeDesc MTD_leafString = MethodTypeDesc.of(CD_StringDecoder);
    private static final MethodTypeDesc MTD_leafLong = MethodTypeDesc.of(CD_LongDecoder);
    private static final MethodTypeDesc MTD_leafBool = MethodTypeDesc.of(CD_BoolDecoder);
    private static final MethodTypeDesc MTD_leafDecimal = MethodTypeDesc.of(CD_DecimalDecoder);
    private static final MethodTypeDesc MTD_leafTemporal = MethodTypeDesc.of(CD_TemporalDecoder);
    private static final MethodTypeDesc MTD_field = MethodTypeDesc.of(CD_FieldDecoder, CD_String, CD_RDecoder);
    private static final MethodTypeDesc MTD_optionalField = MethodTypeDesc.of(CD_RDecoder, CD_String, CD_RDecoder);
    private static final MethodTypeDesc MTD_listDec = MethodTypeDesc.of(CD_ListDecoder, CD_RDecoder);
    private static final MethodTypeDesc MTD_mapDec = MethodTypeDesc.of(CD_RecordDecoder, CD_RDecoder);
    private static final MethodTypeDesc MTD_Rvariant = MethodTypeDesc.of(CD_RVariant, CD_String, CD_RDecoder);
    private static final MethodTypeDesc MTD_Rdiscriminate =
            MethodTypeDesc.of(CD_RDecoder, CD_String, CD_RDecoder, CD_RVariant.arrayType());
    private static final MethodTypeDesc MTD_Rok = MethodTypeDesc.of(CD_RResult, CD_Object);
    private static final MethodTypeDesc MTD_Rerr = MethodTypeDesc.of(CD_RResult, CD_RIssues);
    private static final MethodTypeDesc MTD_Rfail = MethodTypeDesc.of(CD_RResult, CD_RPath, CD_String, CD_String);
    private static final MethodTypeDesc MTD_Rencode_leaf = MethodTypeDesc.of(CD_REncoder);
    private static final MethodTypeDesc MTD_Rencode_list = MethodTypeDesc.of(CD_REncoder, CD_REncoder);
    private static final MethodTypeDesc MTD_Rencode_variant =
            MethodTypeDesc.of(CD_MapEncVariant, CD_Class, CD_String, CD_REncoder);
    private static final MethodTypeDesc MTD_Rencode_discriminate =
            MethodTypeDesc.of(CD_REncoder, CD_String, CD_MapEncVariant.arrayType());
    private static final MethodTypeDesc MTD_Issues_merge = MethodTypeDesc.of(CD_RIssues, CD_RIssues);
    private static final MethodTypeDesc MTD_Issues_isEmpty = MethodTypeDesc.of(ConstantDescs.CD_boolean);
    private static final MethodTypeDesc MTD_Err_issues = MethodTypeDesc.of(CD_RIssues);
    private static final MethodTypeDesc MTD_nested = MethodTypeDesc.of(CD_RDecoder, CD_RDecoder);
    private static final ClassDesc CD_JavaOptional = ClassDesc.of("java.util.Optional");
    private static final MethodTypeDesc MTD_ofOptional = MethodTypeDesc.of(CD_Option, CD_JavaOptional);
    private static final MethodTypeDesc MTD_error = MethodTypeDesc.of(CD_Object);
    // Per-source (JSON / jOOQ) decode targets (spec 10.6). JooqRecordDecoders.field returns a
    // JooqRecordDecoder (not FieldDecoder); JsonDecoders.field returns FieldDecoder like the map source.
    private static final ClassDesc CD_JooqRecordDecoder = ClassDesc.of("net.unit8.raoh.jooq.JooqRecordDecoder");
    private static final MethodTypeDesc MTD_fieldJooq =
            MethodTypeDesc.of(CD_JooqRecordDecoder, CD_String, CD_RDecoder);
    private static final MethodTypeDesc MTD_optFieldJooq =
            MethodTypeDesc.of(CD_JooqRecordDecoder, CD_String, CD_RDecoder);

    /** The three boundary input sources a decoder can read from (spec 6, 10.6). */
    private enum Src { NEUTRAL, JSON, JOOQ }

    private static String srcSuffix(Src s) {
        return switch (s) { case NEUTRAL -> "$Dec"; case JSON -> "$DecJson"; case JOOQ -> "$DecRecord"; };
    }
    private static String srcFactory(Src s) {
        return switch (s) { case NEUTRAL -> "decoder"; case JSON -> "jsonDecoder"; case JOOQ -> "recordDecoder"; };
    }
    private static ClassDesc srcFieldOwner(Src s) {
        return switch (s) {
            case NEUTRAL -> CD_MapDecoders;
            case JSON -> CD_JsonDecoders;
            case JOOQ -> CD_JooqDecoders;
        };
    }
    private static MethodTypeDesc srcFieldMtd(Src s) { return s == Src.JOOQ ? MTD_fieldJooq : MTD_field; }
    private static MethodTypeDesc srcOptFieldMtd(Src s) { return s == Src.JOOQ ? MTD_optFieldJooq : MTD_optionalField; }
    /** Leaf value decoders: JSON reads a JsonNode, the map/jOOQ column value is an Object. */
    private static ClassDesc srcLeafOwner(Src s) { return s == Src.JSON ? CD_JsonDecoders : CD_ObjectDecoders; }
    /** list()/map() combinator owner (JSON has its own; map/jOOQ leaf values are Objects). */
    private static ClassDesc srcListOwner(Src s) { return s == Src.JSON ? CD_JsonDecoders : CD_ObjectDecoders; }

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
    private static byte[] build(ClassDesc cd, java.util.function.Consumer<ClassBuilder> handler) {
        return CF.build(cd, cb -> {
            cb.withVersion(ClassFile.JAVA_21_VERSION, 0);
            handler.accept(cb);
        });
    }

    private final String pkg;
    private final Map<String, Ast.Def> symbols;
    private final Map<String, List<String>> armToSums;
    private final Map<String, String> typePackage;
    /** True when the module has no {@code exposing} clause: everything stays public. */
    private final boolean exposeAll;
    /** Base names the module exposes (only these are public when {@link #exposeAll} is false). */
    private final Set<String> exposed;

    private Backend(String pkg, Map<String, Ast.Def> symbols, Map<String, List<String>> armToSums,
                    Map<String, String> typePackage, boolean exposeAll, Set<String> exposed) {
        this.pkg = pkg;
        this.symbols = symbols;
        this.armToSums = armToSums;
        this.typePackage = typePackage;
        this.exposeAll = exposeAll;
        this.exposed = exposed;
    }

    /** {@code ACC_PUBLIC} when the name is exposed (or the module exposes all), else 0. */
    private int pub(String name) {
        return (exposeAll || exposed.contains(name)) ? ClassFile.ACC_PUBLIC : 0;
    }

    public static Map<String, byte[]> generate(Ast.Module module) {
        return generate(module, TypeChecker.symbols(module), Map.of());
    }

    /** Generates a module's classes. {@code symbols} covers own plus imported definitions;
     * {@code typePackage} maps an imported type name to its declaring module (spec 4). */
    public static Map<String, byte[]> generate(Ast.Module module, Map<String, Ast.Def> symbols,
                                               Map<String, String> typePackage) {
        Map<String, List<String>> armToSums = new HashMap<>();
        for (Ast.Def def : module.defs()) {
            if (def instanceof Ast.SumData sum) {
                for (String arm : sum.arms()) {
                    armToSums.computeIfAbsent(arm, k -> new ArrayList<>()).add(sum.name());
                }
            }
        }
        Set<String> exposed = new HashSet<>();
        for (String e : module.exposing()) {
            int dot = e.indexOf('.');
            exposed.add(dot < 0 ? e : e.substring(0, dot));
        }
        Backend b = new Backend(module.name(), symbols, armToSums, typePackage,
                module.exposing().isEmpty(), exposed);
        // A behavior whose output is an anonymous union gets a generated sealed interface
        // <behavior名>結果 that its arms implement (spec 19.8). Register those arm->interface links
        // in armToSums before the data classes are generated, so each arm class picks the interface
        // up in withInterfaceSymbols. The interface classes themselves are emitted below.
        Map<String, List<String>> behaviorResults = b.behaviorResultInterfaces(module);
        behaviorResults.forEach((resultName, arms) -> {
            for (String arm : arms) {
                armToSums.computeIfAbsent(arm, k -> new ArrayList<>()).add(resultName);
            }
        });
        Map<String, byte[]> out = new LinkedHashMap<>();
        behaviorResults.forEach((resultName, arms) ->
                out.put(module.name() + "." + resultName, b.generateBehaviorResult(resultName, arms)));
        for (Ast.Def def : module.defs()) {
            switch (def) {
                case Ast.Data data -> b.generateData(data, out);
                case Ast.SumData sum -> b.generateSum(sum, out);
                case Ast.UnitData unit -> b.generateUnit(unit, out);
            }
        }
        Map<String, Ast.FnDef> fns = new HashMap<>();
        for (Ast.FnDef fn : module.fns()) {
            fns.put(fn.name(), fn);
        }
        // Helper fns are expanded inline into each behavior body (spec 12.5); the backend never
        // lowers a helper on its own.
        HelperInliner inliner = HelperInliner.forModule(module);
        // Injection targets (spec 13.2): a SpecBehavior with no matching fn. Each becomes an
        // abstract base class a Java implementation extends (13.3).
        Set<String> requiredNames = new HashSet<>();
        Map<String, Type> requiredSuccess = new HashMap<>();
        for (Ast.BehaviorDef bd : module.behaviors()) {
            if (bd instanceof Ast.SpecBehavior spec && !fns.containsKey(spec.name())) {
                requiredNames.add(spec.name());
                requiredSuccess.put(spec.name(), b.successType(spec.ret()));
                List<String> unitArms = new ArrayList<>();
                for (Ast.TypeRef t : spec.ret().arms()) {
                    if (b.symbols.get(t.name()) instanceof Ast.UnitData) {
                        unitArms.add(t.name());
                    }
                }
                ClassDesc inputRef = spec.params().size() == 1
                        ? b.refTypeOrNull(b.successType(spec.params().get(0).type()), spec.name())
                        : null;
                ClassDesc outputRef = b.refTypeOrNull(b.successType(spec.ret()), spec.name());
                out.put(module.name() + "." + spec.name(),
                        b.generateRequiredBase(spec.name(), unitArms, inputRef, outputRef));
            }
        }
        Map<String, TypeChecker.Sig> sigs = TypeChecker.signatures(module, b.symbols);
        Map<String, List<String>> behaviorDeps = requirementSets(module, requiredNames);
        Map<String, List<String>> pipeStages = TypeChecker.pipelineStages(module);
        for (Ast.BehaviorDef bd : module.behaviors()) {
            switch (bd) {
                case Ast.SpecBehavior spec -> {
                    Ast.FnDef fn = fns.get(spec.name());
                    if (fn != null) {
                        Ast.FnDef inlined = new Ast.FnDef(
                                fn.name(), fn.params(), inliner.inline(fn.body()), fn.pos());
                        out.put(module.name() + "." + spec.name(),
                                b.generateSpecFn(spec, inlined, requiredNames, requiredSuccess));
                    }
                    // else: injection target — its abstract base was generated above (spec 13.3)
                }
                case Ast.PipeBehavior pipe ->
                        out.put(module.name() + "." + pipe.name(),
                                b.generatePipe(pipe, requiredNames, sigs, behaviorDeps, pipeStages));
            }
        }
        return out;
    }

    /** Emits injected required-behavior fields plus the matching constructor (or a no-arg ctor). */
    private void emitInjection(ClassBuilder cb, ClassDesc cdX, List<String> requireds) {
        if (requireds.isEmpty()) {
            emitPublicCtor(cb);
            return;
        }
        for (String req : requireds) {
            cb.withField(req, CD_Behavior, ClassFile.ACC_FINAL);
        }
        ClassDesc[] params = new ClassDesc[requireds.size()];
        for (int i = 0; i < requireds.size(); i++) {
            params[i] = CD_Behavior;
        }
        MethodTypeDesc ctorDesc = MethodTypeDesc.of(ConstantDescs.CD_void, params);
        cb.withMethodBody("<init>", ctorDesc, ClassFile.ACC_PUBLIC, code -> {
            code.aload(0);
            code.invokespecial(CD_Object, "<init>", MTD_void);
            for (int i = 0; i < requireds.size(); i++) {
                code.aload(0);
                code.aload(i + 1);
                code.putfield(cdX, requireds.get(i), CD_Behavior);
            }
            code.return_();
        });

        // bind(<named required interfaces>) -> this behavior (spec 19.5)
        ClassDesc[] bindParams = new ClassDesc[requireds.size()];
        for (int i = 0; i < requireds.size(); i++) {
            bindParams[i] = cd(requireds.get(i));
        }
        cb.withMethodBody("bind", MethodTypeDesc.of(cdX, bindParams),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, code -> {
                    code.new_(cdX);
                    code.dup();
                    for (int i = 0; i < requireds.size(); i++) {
                        code.aload(i);
                    }
                    code.invokespecial(cdX, "<init>", ctorDesc);
                    code.areturn();
                });
    }

    /**
     * Generates the abstract base class for a required behavior (spec 13.3): an abstract
     * {@code Behavior} that a Java implementation extends. The base exposes a {@code protected}
     * factory for each declared unit-data output arm — the only sanctioned way for the
     * implementation to mint those arms (spec 2.1). The data constructors stay non-public, so
     * a subclass can build exactly this behavior's declared arms and nothing else, from any
     * package (no in-package placement required).
     *
     * <p>When both the input and output map to a concrete reference type, the base carries a
     * generic {@code Behavior<In, Out>} signature (spec 19.8, 24) — {@code Out} is the {@code <名>結果}
     * interface for an anonymous union output — so a Java author writes the real return type rather
     * than {@code Object}. If either side is a list/option/map (no single reference class), the
     * signature is omitted and the raw interface stands.
     */
    private byte[] generateRequiredBase(String name, List<String> unitArms,
                                        ClassDesc inputRef, ClassDesc outputRef) {
        ClassDesc cdR = cd(name);
        return build(cdR, cb -> {
            cb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT | ClassFile.ACC_SUPER);
            if (inputRef != null && outputRef != null) {
                String beh = CD_Behavior.descriptorString();
                beh = beh.substring(0, beh.length() - 1); // drop trailing ';' to insert type args
                String sig = CD_Object.descriptorString() + beh + "<"
                        + inputRef.descriptorString() + outputRef.descriptorString() + ">;";
                cb.with(SignatureAttribute.of(ClassSignature.parseFrom(sig)));
            }
            cb.withInterfaceSymbols(CD_Behavior);
            // protected no-arg ctor so subclasses in any package can call super()
            cb.withMethodBody("<init>", MTD_void, ClassFile.ACC_PROTECTED, code -> {
                code.aload(0);
                code.invokespecial(CD_Object, "<init>", MTD_void);
                code.return_();
            });
            for (String arm : unitArms) {
                ClassDesc armCd = cd(arm);
                cb.withMethodBody(arm, MethodTypeDesc.of(armCd),
                        ClassFile.ACC_PROTECTED | ClassFile.ACC_FINAL, code -> {
                            code.new_(armCd);
                            code.dup();
                            code.invokespecial(armCd, "<init>", MTD_void);
                            code.areturn();
                        });
            }
        });
    }

    /**
     * Behavior-result interfaces to generate (spec 19.8): for each behavior whose output is an
     * anonymous union, maps {@code <behavior名>結果} to its leaf arms — the {@code permits} list and
     * the set of arm classes that {@code implements} it. A named-sum output is already a sealed
     * interface (19.3) and a single-arm output uses that arm's own type, so neither gets one. Arm
     * order is sorted for deterministic bytecode.
     */
    private Map<String, List<String>> behaviorResultInterfaces(Ast.Module module) {
        Map<String, TypeChecker.Sig> sigs = TypeChecker.signatures(module, symbols);
        Map<String, List<String>> results = new LinkedHashMap<>();
        for (Ast.BehaviorDef bd : module.behaviors()) {
            TypeChecker.Sig sig = sigs.get(bd.name());
            if (sig == null || !(sig.out() instanceof Type.Union)) {
                continue;
            }
            List<String> arms = new ArrayList<>(TypeChecker.leafArms(sig.out(), symbols));
            Collections.sort(arms);
            results.put(bd.name() + "結果", arms);
        }
        return results;
    }

    /**
     * Generates the sealed interface for a behavior's anonymous union output (spec 19.8). The body
     * is empty: Java consumers receive a value and {@code switch} over the permitted arms, each of
     * which carries its own codec, so the interface itself needs no members.
     */
    private byte[] generateBehaviorResult(String resultName, List<String> arms) {
        ClassDesc cdR = cd(resultName);
        List<ClassDesc> armCds = new ArrayList<>();
        for (String arm : arms) {
            armCds.add(cd(arm));
        }
        return build(cdR, cb -> {
            cb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_INTERFACE | ClassFile.ACC_ABSTRACT);
            cb.with(PermittedSubclassesAttribute.ofSymbols(armCds));
        });
    }

    /**
     * The single reference class a behavior's input or output success type maps to, for a generic
     * {@code Behavior<In, Out>} signature: the {@code <名>結果} interface for an anonymous union, the
     * named data/sum for a single arm, the boxed class for a primitive. Returns {@code null} for a
     * list/option/map, which has no single reference class to name here.
     */
    private ClassDesc refTypeOrNull(Type t, String behaviorName) {
        if (t instanceof Type.Union) {
            return cd(behaviorName + "結果");
        }
        if (t instanceof Type.Ref r) {
            return cd(r.name());
        }
        return boxedPrim(t);
    }

    /** The boxed JVM class for a primitive success type, or {@code null} for a non-primitive. */
    private static ClassDesc boxedPrim(Type t) {
        if (t == Type.INT) return CD_Long;
        if (t == Type.BOOL) return CD_Boolean;
        if (t == Type.DECIMAL) return CD_BigDecimal;
        if (t == Type.STRING) return CD_String;
        if (t == Type.DATE) return CD_LocalDate;
        if (t == Type.DATETIME) return CD_LocalDateTime;
        return null;
    }

    private Type resolveType(Ast.TypeRef ref) {
        return TypeChecker.resolveType(ref, symbols);
    }

    private Type successType(Ast.RetType ret) {
        return TypeChecker.successType(ret, symbols);
    }

    private ClassDesc[] armInterfaces(String name) {
        List<ClassDesc> ifaces = new ArrayList<>();
        for (String sum : armToSums.getOrDefault(name, List.of())) {
            ifaces.add(cd(sum));
        }
        return ifaces.toArray(new ClassDesc[0]);
    }

    /** The class a match arm is tested against: a boxed/reference class for a primitive arm,
     * otherwise the arm's data or built-in class. */
    private ClassDesc matchArmClass(String armName) {
        return switch (armName) {
            case "Int" -> CD_Long;
            case "Bool" -> CD_Boolean;
            case "Decimal" -> CD_BigDecimal;
            case "String" -> CD_String;
            case "Date" -> CD_LocalDate;
            case "DateTime" -> CD_LocalDateTime;
            default -> armClass(armName);
        };
    }

    private Map<String, Type> fieldTypes(Ast.Data data) {
        return TypeChecker.fieldTypes(data, symbols);
    }

    private ClassDesc cd(String typeName) {
        return ClassDesc.of(typePackage.getOrDefault(typeName, pkg) + "." + typeName);
    }

    /** Invokes a type's static {@code decoder()}/{@code encoder()} factory, as an interface
     * method reference when the type is a sum (its factory lives on a sealed interface). */
    private void invokeCodec(CodeBuilder code, String typeName, String method, MethodTypeDesc mtd) {
        code.invokestatic(cd(typeName), method, mtd, symbols.get(typeName) instanceof Ast.SumData);
    }

    /** The JVM class for an output arm: the built-in {@code DivisionByZero}, otherwise the
     * generated data class in this module. An invariant violation is no longer an arm — it aborts
     * (spec 7.3, 9.4) — so there is no 制約違反 case here. */
    private ClassDesc armClass(String typeName) {
        return switch (typeName) {
            case "DivisionByZero" -> CD_DivisionByZero;
            default -> cd(typeName);
        };
    }

    private void generateData(Ast.Data data, Map<String, byte[]> out) {
        ClassDesc cdName = cd(data.name());
        Map<String, Type> fields = fieldTypes(data);

        out.put(pkg + "." + data.name(), build(cdName, cb -> {
            cb.withFlags(pub(data.name()) | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            ClassDesc[] ifaces = armInterfaces(data.name());
            if (ifaces.length > 0) {
                cb.withInterfaceSymbols(ifaces);
            }
            for (Map.Entry<String, Type> f : fields.entrySet()) {
                cb.withField(f.getKey(), jvmType(f.getValue()), ClassFile.ACC_FINAL);
            }
            emitCtor(cb, cdName, fields);
            emitValueEquality(cb, cdName, fields);
            emitConstructMethod(cb, cdName, data, fields);
            // An exposed data gets public read accessors so its fields are readable across the
            // module (package) boundary and from Java (spec 8.5, 19.2). The ctor stays non-public.
            if (pub(data.name()) != 0) {
                emitAccessors(cb, cdName, fields);
            }
            data.decoder().ifPresent(d -> {
                boolean mapInput = isMapInput(data.name());
                emitFactory(cb, "decoder", CD_RDecoder, data, "$Dec");
                if (jsonCompatible(data.name())) emitSourceFactory(cb, data.name(), Src.JSON, mapInput);
                if (recordCompatible(data.name())) emitSourceFactory(cb, data.name(), Src.JOOQ, mapInput);
            });
            data.encoder().ifPresent(e -> emitFactory(cb, "encoder", CD_REncoder, data, "$Enc"));
        }));

        data.decoder().ifPresent(dec -> {
            out.put(pkg + "." + data.name() + "$Dec",
                    generateDecoderClass(cdName, data, dec, fields, Src.NEUTRAL));
            if (jsonCompatible(data.name())) {
                out.put(pkg + "." + data.name() + "$DecJson",
                        generateDecoderClass(cdName, data, dec, fields, Src.JSON));
            }
            if (recordCompatible(data.name())) {
                out.put(pkg + "." + data.name() + "$DecRecord",
                        generateDecoderClass(cdName, data, dec, fields, Src.JOOQ));
            }
        });
        data.encoder().ifPresent(enc ->
                out.put(pkg + "." + data.name() + "$Enc", generateEncoderClass(cdName, data, enc)));
    }

    // --- sum data (sealed interface) ---

    private void generateSum(Ast.SumData sum, Map<String, byte[]> out) {
        ClassDesc cdX = cd(sum.name());
        List<ClassDesc> armCds = new ArrayList<>();
        for (String arm : sum.arms()) {
            armCds.add(cd(arm));
        }
        out.put(pkg + "." + sum.name(), build(cdX, cb -> {
            cb.withFlags(pub(sum.name()) | ClassFile.ACC_INTERFACE | ClassFile.ACC_ABSTRACT);
            cb.with(PermittedSubclassesAttribute.ofSymbols(armCds));
            sum.decoder().ifPresent(disc -> {
                emitCodecFactory(cb, "decoder", CD_RDecoder, cd(sum.name() + "$Dec"), decoderSig(cdX, true));
                if (jsonCompatible(sum.name())) emitSourceFactory(cb, sum.name(), Src.JSON, true);
                if (recordCompatible(sum.name())) emitSourceFactory(cb, sum.name(), Src.JOOQ, true);
            });
            sum.encoder().ifPresent(enc ->
                    emitCodecFactory(cb, "encoder", CD_REncoder, cd(sum.name() + "$Enc"),
                            encoderSig(cdX, CD_Map)));
        }));
        sum.decoder().ifPresent(disc -> {
            out.put(pkg + "." + sum.name() + "$Dec", generateSumDecoder(sum, disc, Src.NEUTRAL));
            if (jsonCompatible(sum.name())) {
                out.put(pkg + "." + sum.name() + "$DecJson", generateSumDecoder(sum, disc, Src.JSON));
            }
            if (recordCompatible(sum.name())) {
                out.put(pkg + "." + sum.name() + "$DecRecord", generateSumDecoder(sum, disc, Src.JOOQ));
            }
        });
        sum.encoder().ifPresent(enc ->
                out.put(pkg + "." + sum.name() + "$Enc", generateSumEncoder(sum, enc)));
    }

    private byte[] generateSumEncoder(Ast.SumData sum, Ast.SumEncoder enc) {
        ClassDesc cdEnc = cd(sum.name() + "$Enc");
        return build(cdEnc, cb -> {
            cb.withFlags(ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            cb.withInterfaceSymbols(CD_REncoder);
            emitDefaultCtor(cb);
            // Dispatch on the runtime arm type, encode that arm to a Map, then inject the
            // discriminator key = arm tag (spec 11.2).
            cb.withMethodBody("encode", MTD_Rencode, ClassFile.ACC_PUBLIC, code -> {
                for (Ast.EncVariant v : enc.variants()) {
                    ClassDesc armCd = cd(v.armType());
                    code.aload(1);
                    code.instanceOf(armCd);
                    Label next = code.newLabel();
                    code.ifeq(next);
                    invokeCodec(code, v.armType(), "encoder", MTD_Rencoder);
                    code.aload(1);
                    code.invokeinterface(CD_REncoder, "encode", MTD_Rencode);
                    code.checkcast(CD_Map);
                    code.dup();
                    code.loadConstant(enc.key());
                    code.loadConstant(v.tag());
                    code.invokeinterface(CD_Map, "put", MTD_Map_put);
                    code.pop();
                    code.areturn();
                    code.labelBinding(next);
                }
                code.new_(CD_IllegalStateException);
                code.dup();
                code.invokespecial(CD_IllegalStateException, "<init>", MTD_void);
                code.athrow();
            });
        });
    }

    private byte[] generateSumDecoder(Ast.SumData sum, Ast.Discriminate disc, Src src) {
        ClassDesc cdDec = cd(sum.name() + srcSuffix(src));
        return build(cdDec, cb -> {
            cb.withFlags(ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            cb.withInterfaceSymbols(CD_RDecoder);
            emitDefaultCtor(cb);
            // Build a Raoh discriminate decoder and delegate: the tag is read from the
            // discriminator key of the source, each arm dispatches to that arm's decoder for the
            // same source (spec 10.3). discriminate/variant are the core (input-generic) combinators.
            cb.withMethodBody("decode", MTD_Rdecode, ClassFile.ACC_PUBLIC, code -> {
                code.loadConstant(disc.key());
                code.loadConstant(disc.key());
                code.invokestatic(srcLeafOwner(src), "string", MTD_leafString);
                code.invokestatic(srcFieldOwner(src), "field", srcFieldMtd(src));
                pushInt(code, disc.variants().size());
                code.anewarray(CD_RVariant);
                int i = 0;
                for (Ast.Variant v : disc.variants()) {
                    code.dup();
                    pushInt(code, i);
                    code.loadConstant(v.tag());
                    invokeCodec(code, v.armType(), srcFactory(src), MTD_Rdecoder);
                    code.invokestatic(CD_RDecoders, "variant", MTD_Rvariant);
                    code.aastore();
                    i++;
                }
                code.invokestatic(CD_RDecoders, "discriminate", MTD_Rdiscriminate);
                code.aload(1);
                code.aload(2);
                code.invokeinterface(CD_RDecoder, "decode", MTD_Rdecode);
                code.areturn();
            });
        });
    }

    private void generateUnit(Ast.UnitData unit, Map<String, byte[]> out) {
        ClassDesc cdU = cd(unit.name());
        ClassDesc cdDec = cd(unit.name() + "$Dec");
        ClassDesc cdEnc = cd(unit.name() + "$Enc");
        out.put(pkg + "." + unit.name(), build(cdU, cb -> {
            cb.withFlags(pub(unit.name()) | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            ClassDesc[] ifaces = armInterfaces(unit.name());
            if (ifaces.length > 0) {
                cb.withInterfaceSymbols(ifaces);
            }
            emitDefaultCtor(cb);
            emitValueEquality(cb, cdU, Map.of());   // all units of a type are the same value
            // a unit is a field-less data: its codec reads/writes nothing but the tag the sum adds
            // A unit ignores its input, so it decodes from every source. Generate all three so
            // unit arms of a JSON/record sum have a matching decoder to dispatch to.
            emitCodecFactory(cb, "decoder", CD_RDecoder, cdDec, decoderSig(cdU, false));
            emitCodecFactory(cb, "jsonDecoder", CD_RDecoder, cd(unit.name() + "$DecJson"),
                    decoderSigFor(Src.JSON, cdU, false));
            emitCodecFactory(cb, "recordDecoder", CD_RDecoder, cd(unit.name() + "$DecRecord"),
                    decoderSigFor(Src.JOOQ, cdU, false));
            emitCodecFactory(cb, "encoder", CD_REncoder, cdEnc, encoderSig(cdU, CD_Map));
        }));
        out.put(pkg + "." + unit.name() + "$Dec", generateUnitDecoder(cdU, cdDec));
        out.put(pkg + "." + unit.name() + "$DecJson", generateUnitDecoder(cdU, cd(unit.name() + "$DecJson")));
        out.put(pkg + "." + unit.name() + "$DecRecord", generateUnitDecoder(cdU, cd(unit.name() + "$DecRecord")));
        out.put(pkg + "." + unit.name() + "$Enc", generateUnitEncoder(cdEnc));
    }

    /** Decodes a unit: ignore the input (a unit carries no data) and build the singleton value. */
    private byte[] generateUnitDecoder(ClassDesc cdU, ClassDesc cdDec) {
        return build(cdDec, cb -> {
            cb.withFlags(ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            cb.withInterfaceSymbols(CD_RDecoder);
            emitDefaultCtor(cb);
            cb.withMethodBody("decode", MTD_Rdecode, ClassFile.ACC_PUBLIC, code -> {
                code.new_(cdU);
                code.dup();
                code.invokespecial(cdU, "<init>", MTD_void);
                code.invokestatic(CD_RResult, "ok", MTD_Rok, true);
                code.areturn();
            });
        });
    }

    /** Encodes a unit to an empty Map; the sum encoder adds the discriminator tag. */
    private byte[] generateUnitEncoder(ClassDesc cdEnc) {
        return build(cdEnc, cb -> {
            cb.withFlags(ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            cb.withInterfaceSymbols(CD_REncoder);
            emitDefaultCtor(cb);
            cb.withMethodBody("encode", MTD_Rencode, ClassFile.ACC_PUBLIC, code -> {
                code.new_(CD_LinkedHashMap);
                code.dup();
                code.invokespecial(CD_LinkedHashMap, "<init>", MTD_void);
                code.areturn();
            });
        });
    }

    /** Emits a {@code static} factory that returns a fresh instance of {@code impl}. */
    // --- behaviors ---

    /**
     * Generates a behavior implemented by a {@code fn} (spec 13.1). The behavior's inputs are the
     * {@code apply} arguments; its {@code requires} are injected fields (12.6). The {@code fn}'s
     * leading parameters name the inputs (their types come from the behavior); the trailing ones
     * name the injected behaviors and are resolved as inline calls, not bound as locals.
     */
    private byte[] generateSpecFn(Ast.SpecBehavior spec, Ast.FnDef fn, Set<String> requiredNames,
                                  Map<String, Type> requiredSuccess) {
        ClassDesc cdB = cd(spec.name());
        int n = spec.params().size();
        // declared requires, validated to equal what the fn calls (E1602/E1603); the same order is
        // used by pipeline callers (requirementSets), so the injected fields line up.
        List<String> injected = spec.requires();
        ClassDesc[] applyParams = new ClassDesc[n];
        for (int i = 0; i < n; i++) {
            applyParams[i] = CD_Object;
        }
        MethodTypeDesc mtdApply = MethodTypeDesc.of(CD_Object, applyParams);
        return build(cdB, cb -> {
            cb.withFlags(pub(spec.name()) | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            if (n == 1) {
                cb.withInterfaceSymbols(CD_Behavior); // single-input behaviors compose with >>
            }
            emitInjection(cb, cdB, injected);
            cb.withMethodBody("apply", mtdApply, ClassFile.ACC_PUBLIC, code -> {
                Gen gen = new Gen(code, null, cdB, n + 1);
                gen.requireds(requiredNames, requiredSuccess);
                for (int i = 0; i < n; i++) {
                    // the fn's leading param names the input; its type comes from the behavior
                    Type pt = successType(spec.params().get(i).type());
                    code.aload(i + 1);
                    int slot = gen.slot(pt);
                    unbox(code, pt, slot);
                    gen.bind(fn.params().get(i).name(), slot, pt);
                }
                emitBodyTail(gen, code, fn.body(), cdB, requiredNames, requiredSuccess);
            });
        });
    }

    /**
     * Emits {@code e} in tail position: every path ends in an {@code areturn}.
     *
     * <p>Constructing an invariant-bearing data goes through {@code __construct}, which checks the
     * invariant and returns a {@code Result}; {@code ConstraintViolation.orThrow} turns that into
     * either the value (returned) or a thrown {@code ConstraintViolation} — an invariant violation
     * aborts rather than riding an output arm (spec 7.3, 9.4).
     * Because a desugared {@code require} (spec 16.4) is an {@code if} whose branches are tail,
     * this is reached for constructions on both sides of a guard — there is no second, unchecked
     * construction path.
     */
    private void emitBodyTail(Gen gen, CodeBuilder code, Ast.Expr e, ClassDesc cdB,
                              Set<String> requiredNames, Map<String, Type> requiredSuccess) {
        switch (e) {
            case Ast.LetIn li -> {
                if (li.value() instanceof Ast.Call call && requiredNames.contains(call.fn())) {
                    // call an injected required behavior; its apply returns the value directly
                    code.aload(0);
                    code.getfield(cdB, call.fn(), CD_Behavior);
                    if (call.args().isEmpty()) {
                        code.aconst_null();    // `() -> R` (spec 13.1)
                    } else {
                        Type at = gen.expr(call.args().get(0));
                        box(code, at);
                    }
                    code.invokeinterface(CD_Behavior, "apply", MTD_apply);
                    Type letType = requiredSuccess.get(call.fn());
                    int vSlot = gen.slot(letType);
                    unbox(code, letType, vSlot);
                    gen.bind(li.name(), vSlot, letType);
                } else {
                    Type t = gen.expr(li.value());
                    int slot = gen.slot(t);
                    store(code, slot, t);
                    gen.bind(li.name(), slot, t);
                }
                emitBodyTail(gen, code, li.body(), cdB, requiredNames, requiredSuccess);
            }
            case Ast.If iff -> {
                gen.expr(iff.cond());
                Label elseL = code.newLabel();
                code.ifeq(elseL);
                emitBodyTail(gen, code, iff.then(), cdB, requiredNames, requiredSuccess);
                code.labelBinding(elseL);
                emitBodyTail(gen, code, iff.els(), cdB, requiredNames, requiredSuccess);
            }
            case Ast.NewData nd when TypeChecker.isInvariantBearing(nd.typeName(), symbols) -> {
                ClassDesc cdType = cd(nd.typeName());
                Map<String, Type> flds = fieldTypes((Ast.Data) symbols.get(nd.typeName()));
                emitFieldValues(gen, flds, nd.inits(), nd.spreads());
                code.invokestatic(cdType, "__construct", MethodTypeDesc.of(CD_Result, fieldDescs(flds)));
                code.invokestatic(CD_ConstraintViolation, "orThrow", MTD_orThrow);
                code.areturn();
            }
            default -> {
                Type rt = gen.expr(e);
                box(code, rt);
                code.areturn();
            }
        }
    }

    /**
     * Every behavior's requirement set, in constructor (first-seen) order (spec 13.6, 14.3).
     *
     * <p>A required behavior requires itself; a body behavior requires what it calls; a pipeline
     * requires the union of its stages'. Pipelines are resolved too, and transitively: a pipeline
     * used as a stage of another pipeline still needs its own dependencies injected, and leaving
     * it out produced a class whose {@code apply} called a constructor that does not exist.
     */
    private static Map<String, List<String>> requirementSets(Ast.Module module, Set<String> requiredNames) {
        Map<String, Ast.BehaviorDef> byName = new HashMap<>();
        for (Ast.BehaviorDef bd : module.behaviors()) {
            byName.put(bd.name(), bd);
        }
        Map<String, List<String>> memo = new HashMap<>();
        for (Ast.BehaviorDef bd : module.behaviors()) {
            resolveDeps(bd.name(), byName, requiredNames, memo, new LinkedHashSet<>());
        }
        return memo;
    }

    private static List<String> resolveDeps(String name, Map<String, Ast.BehaviorDef> byName,
                                            Set<String> requiredNames, Map<String, List<String>> memo,
                                            LinkedHashSet<String> inProgress) {
        if (requiredNames.contains(name)) {
            return List.of(name);
        }
        List<String> cached = memo.get(name);
        if (cached != null) {
            return cached;
        }
        Ast.BehaviorDef bd = byName.get(name);
        if (bd == null) {
            return List.of();
        }
        if (!inProgress.add(name)) {
            throw new CompileException(bd.pos(),
                    "cyclic behavior composition: " + String.join(" >> ", inProgress) + " >> " + name);
        }
        LinkedHashSet<String> deps = new LinkedHashSet<>();
        switch (bd) {
            // an injection target short-circuits above, so a SpecBehavior here is fn-implemented:
            // its dependencies are its declared requires, in that order (spec 12.6, 13.6)
            case Ast.SpecBehavior spec -> deps.addAll(spec.requires());
            case Ast.PipeBehavior pipe -> {
                for (String stage : pipe.stages()) {
                    deps.addAll(resolveDeps(stage, byName, requiredNames, memo, inProgress));
                }
            }
        }
        inProgress.remove(name);
        List<String> out = new ArrayList<>(deps);
        memo.put(name, out);
        return out;
    }

    private byte[] generatePipe(Ast.PipeBehavior pipe, Set<String> requiredNames,
                                Map<String, TypeChecker.Sig> sigs, Map<String, List<String>> behaviorDeps,
                                Map<String, List<String>> pipeStages) {
        ClassDesc cdP = cd(pipe.name());
        // Flatten nested pipeline stages so the routing is over leaf behaviors (spec 14.2): a named
        // intermediate `half = split >> work` inlines to `split, work`, which keeps a retired arm
        // retired across the composition, making `>>` associative.
        List<String> flat = TypeChecker.flattenStages(pipe.stages(), pipeStages, pipe.pos());
        // the pipeline's injected fields are the union of its stages' requirements (spec 14.3)
        List<String> reqStages = behaviorDeps.getOrDefault(pipe.name(), List.of());
        // the pipeline takes whatever its first stage takes (spec 14.1)
        int arity = TypeChecker.stageSig(flat.get(0), sigs, symbols, pipe.pos()).ins().size();
        ClassDesc[] applyParams = new ClassDesc[arity];
        java.util.Arrays.fill(applyParams, CD_Object);
        MethodTypeDesc mtdApply = MethodTypeDesc.of(CD_Object, applyParams);

        return build(cdP, cb -> {
            cb.withFlags(pub(pipe.name()) | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            if (arity == 1) {
                cb.withInterfaceSymbols(CD_Behavior);   // only single-input pipelines compose
            }
            emitInjection(cb, cdP, reqStages);

            cb.withMethodBody("apply", mtdApply, ClassFile.ACC_PUBLIC, code -> {
                // slot 1 always holds the running value (an output arm, as an Object).
                List<String> stages = flat;
                // stage 0 consumes the pipeline's arguments unconditionally
                applyFirstStage(code, cdP, stages.get(0), arity, requiredNames, behaviorDeps);
                Type mainline = TypeChecker.stageSig(stages.get(0), sigs, symbols, pipe.pos()).out();
                Label end = code.newLabel();
                for (int i = 1; i < stages.size(); i++) {
                    String stage = stages.get(i);
                    TypeChecker.Sig g = TypeChecker.stageSig(stage, sigs, symbols, pipe.pos());
                    if (TypeChecker.isDataLike(mainline)) {
                        // Apply g only when the running value is one of the main-line arms it
                        // accepts. Anything else has left the main line: jump to the end rather
                        // than offering it to the stages after this one (spec 14.2). Branching to
                        // the end is what makes a retired arm unreachable without tagging it — the
                        // same arm type may legitimately reappear on the main line downstream.
                        List<String> accepted = TypeChecker.mainlineArms(mainline, g, symbols);
                        Label doApply = code.newLabel();
                        for (String arm : accepted) {
                            code.aload(1);
                            code.instanceOf(armClass(arm));
                            code.ifne(doApply);
                        }
                        code.goto_(end);
                        code.labelBinding(doApply);
                        applyStage(code, cdP, stage, requiredNames, behaviorDeps);
                    } else {
                        applyStage(code, cdP, stage, requiredNames, behaviorDeps);
                    }
                    mainline = TypeChecker.stageOut(mainline, g, symbols, pipe.pos());
                }
                code.labelBinding(end);
                code.aload(1);
                code.areturn();
            });
        });
    }

    /**
     * Applies the first stage to the pipeline's own arguments, leaving the result in slot 1.
     *
     * <p>Only this stage may take several inputs (spec 14.1). A multi-input behavior does not
     * implement {@code Behavior} — that interface takes one value — so it is called on its own
     * class rather than through the interface.
     */
    private void applyFirstStage(CodeBuilder code, ClassDesc cdP, String stage, int arity,
                                 Set<String> requiredNames, Map<String, List<String>> behaviorDeps) {
        if (arity == 1) {
            applyStage(code, cdP, stage, requiredNames, behaviorDeps);
            return;
        }
        pushStage(code, cdP, stage, requiredNames, behaviorDeps);
        for (int i = 0; i < arity; i++) {
            code.aload(i + 1);
        }
        ClassDesc[] params = new ClassDesc[arity];
        java.util.Arrays.fill(params, CD_Object);
        code.invokevirtual(cd(stage), "apply", MethodTypeDesc.of(CD_Object, params));
        code.astore(1);
    }

    /** Applies one pipeline stage to the running value in slot 1, storing the result back. A stage
     * is a behavior, or a {@code Type.decoder}/{@code Type.encoder} boundary codec (spec 14.1). */
    private void applyStage(CodeBuilder code, ClassDesc cdP, String stage, Set<String> requiredNames,
                            Map<String, List<String>> behaviorDeps) {
        // decode/encode are boundary edges, not pipeline stages (spec 14.1): `>>` composes
        // behaviors only.
        pushStage(code, cdP, stage, requiredNames, behaviorDeps);
        code.aload(1);
        code.invokeinterface(CD_Behavior, "apply", MTD_apply);
        code.astore(1);
    }

    /** Pushes the behavior object for a pipeline stage: an injected required field, or a fresh
     * body-behavior instance constructed with the required dependencies it declares (spec 14.3). */
    private void pushStage(CodeBuilder code, ClassDesc cdP, String stage, Set<String> requiredNames,
                           Map<String, List<String>> behaviorDeps) {
        if (requiredNames.contains(stage)) {
            code.aload(0);
            code.getfield(cdP, stage, CD_Behavior);
            return;
        }
        ClassDesc cdStage = cd(stage);
        code.new_(cdStage);
        code.dup();
        List<String> deps = behaviorDeps.getOrDefault(stage, List.of());
        ClassDesc[] ctorParams = new ClassDesc[deps.size()];
        for (int i = 0; i < deps.size(); i++) {
            code.aload(0);
            code.getfield(cdP, deps.get(i), CD_Behavior);   // reuse the pipeline's injected field
            ctorParams[i] = CD_Behavior;
        }
        code.invokespecial(cdStage, "<init>", MethodTypeDesc.of(ConstantDescs.CD_void, ctorParams));
    }

    private void box(CodeBuilder code, Type type) {
        if (type == Type.INT) {
            code.invokestatic(CD_Long, "valueOf", MTD_Long_valueOf);
        } else if (type == Type.BOOL) {
            code.invokestatic(CD_Boolean, "valueOf", MTD_Boolean_valueOf);
        }
    }

    private void emitPublicCtor(ClassBuilder cb) {
        cb.withMethodBody("<init>", MTD_void, ClassFile.ACC_PUBLIC, code -> {
            code.aload(0);
            code.invokespecial(CD_Object, "<init>", MTD_void);
            code.return_();
        });
    }

    // --- value class members ---

    /** True when {@code t} is carried as a reference on the JVM (everything but Int and Bool). */
    private static boolean isReference(Type t) {
        return t != Type.INT && t != Type.BOOL;
    }

    private static final ClassDesc CD_Objects = ClassDesc.of("java.util.Objects");
    private static final MethodTypeDesc MTD_BD_compareTo =
            MethodTypeDesc.of(ConstantDescs.CD_int, CD_BigDecimal);
    private static final MethodTypeDesc MTD_BD_strip = MethodTypeDesc.of(CD_BigDecimal);

    /**
     * Emits value equality for two {@code Decimal}s on the stack, leaving a boolean.
     *
     * <p>{@code BigDecimal.equals} also compares the scale, so it calls 1.0 and 1.00 different.
     * Scale is how a number was written, not what it is (spec 7.1): the same amount arrives with
     * a different scale depending on whether it was read from JSON or a DB column, and a money
     * type whose equality turns on that is a trap. Compare by value instead — as Clojure, Scala
     * and Ceylon all chose for the same reason.
     */
    private void emitDecimalEquals(CodeBuilder code) {
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
    private static final MethodTypeDesc MTD_Objects_equals =
            MethodTypeDesc.of(ConstantDescs.CD_boolean, CD_Object, CD_Object);
    private static final MethodTypeDesc MTD_Objects_hashCode =
            MethodTypeDesc.of(ConstantDescs.CD_int, CD_Object);
    private static final MethodTypeDesc MTD_Long_hashCode =
            MethodTypeDesc.of(ConstantDescs.CD_int, ConstantDescs.CD_long);

    /**
     * Emits {@code equals} / {@code hashCode} comparing every field.
     *
     * <p>A data is an immutable value, so two of them are the same when their fields are — which
     * is what {@code ==} means on a data (spec 16.2) and what Java callers expect of a value
     * class. A unit data has no fields, so all of its values are equal.
     */
    private void emitValueEquality(ClassBuilder cb, ClassDesc cdName, Map<String, Type> fields) {
        cb.withMethodBody("equals", MethodTypeDesc.of(ConstantDescs.CD_boolean, CD_Object),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL, code -> {
                    Label same = code.newLabel();
                    Label differs = code.newLabel();
                    code.aload(0);
                    code.aload(1);
                    code.if_acmpeq(same);
                    code.aload(1);
                    code.instanceOf(cdName);
                    code.ifeq(differs);
                    if (!fields.isEmpty()) {
                        code.aload(1);
                        code.checkcast(cdName);
                        code.astore(2);
                        for (Map.Entry<String, Type> f : fields.entrySet()) {
                            Type t = f.getValue();
                            code.aload(0);
                            code.getfield(cdName, f.getKey(), jvmType(t));
                            code.aload(2);
                            code.getfield(cdName, f.getKey(), jvmType(t));
                            if (t == Type.INT) {
                                code.lcmp();
                                code.ifne(differs);
                            } else if (t == Type.BOOL) {
                                code.if_icmpne(differs);
                            } else if (t == Type.DECIMAL) {
                                emitDecimalEquals(code);   // by value, not by scale (spec 7.1)
                                code.ifeq(differs);
                            } else {
                                code.invokestatic(CD_Objects, "equals", MTD_Objects_equals);
                                code.ifeq(differs);
                            }
                        }
                    }
                    code.labelBinding(same);
                    code.iconst_1();
                    code.ireturn();
                    code.labelBinding(differs);
                    code.iconst_0();
                    code.ireturn();
                });

        cb.withMethodBody("hashCode", MethodTypeDesc.of(ConstantDescs.CD_int),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL, code -> {
                    code.iconst_1();
                    for (Map.Entry<String, Type> f : fields.entrySet()) {
                        Type t = f.getValue();
                        code.loadConstant(31);
                        code.imul();
                        code.aload(0);
                        code.getfield(cdName, f.getKey(), jvmType(t));
                        if (t == Type.INT) {
                            code.invokestatic(CD_Long, "hashCode", MTD_Long_hashCode);
                        } else if (t == Type.BOOL) {
                            // already an int 0/1
                        } else if (t == Type.DECIMAL) {
                            // equality ignores scale, so the hash must too, or 1.0 and 1.00 land
                            // in different buckets and a Map keyed by this data stops working.
                            // Groovy changed `==` and left hashCode alone; that bug is still open.
                            code.invokevirtual(CD_BigDecimal, "stripTrailingZeros", MTD_BD_strip);
                            code.invokestatic(CD_Objects, "hashCode", MTD_Objects_hashCode);
                        } else {
                            code.invokestatic(CD_Objects, "hashCode", MTD_Objects_hashCode);
                        }
                        code.iadd();
                    }
                    code.ireturn();
                });
    }

    /**
     * Emits a public record-style read accessor {@code <field>()} for each field (spec 8.5, 19.2).
     * Only called for exposed data; the constructor stays non-public, so a read never enables
     * construction (spec 2.7).
     */
    private void emitAccessors(ClassBuilder cb, ClassDesc cdName, Map<String, Type> fields) {
        for (Map.Entry<String, Type> f : fields.entrySet()) {
            Type ft = f.getValue();
            ClassDesc fd = jvmType(ft);
            cb.withMethodBody(f.getKey(), MethodTypeDesc.of(fd),
                    ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL, code -> {
                        code.aload(0);
                        code.getfield(cdName, f.getKey(), fd);
                        if (ft == Type.INT) {
                            code.lreturn();
                        } else if (ft == Type.BOOL) {
                            code.ireturn();
                        } else {
                            code.areturn();
                        }
                    });
        }
    }

    private void emitCtor(ClassBuilder cb, ClassDesc cdName, Map<String, Type> fields) {
        cb.withMethodBody("<init>", MethodTypeDesc.of(ConstantDescs.CD_void, fieldDescs(fields)), 0, code -> {
            code.aload(0);
            code.invokespecial(CD_Object, "<init>", MTD_void);
            int slot = 1;
            for (Map.Entry<String, Type> f : fields.entrySet()) {
                code.aload(0);
                load(code, slot, f.getValue());
                code.putfield(cdName, f.getKey(), jvmType(f.getValue()));
                slot += width(f.getValue());
            }
            code.return_();
        });
    }

    private void emitConstructMethod(ClassBuilder cb, ClassDesc cdName, Ast.Data data,
                                     Map<String, Type> fields) {
        cb.withMethodBody("__construct", MethodTypeDesc.of(CD_Result, fieldDescs(fields)),
                ClassFile.ACC_STATIC, code -> {
                    Gen gen = new Gen(code, data, cdName, 0);
                    int slot = 0;
                    for (Map.Entry<String, Type> f : fields.entrySet()) {
                        gen.bind(f.getKey(), slot, f.getValue());
                        slot += width(f.getValue());
                    }

                    for (Ast.Expr inv : TypeChecker.effectiveInvariants(data, symbols)) {
                        gen.expr(inv);
                        Label ok = code.newLabel();
                        code.ifne(ok);
                        code.loadConstant("invariant violated on " + data.name());
                        code.invokestatic(CD_Result, "err", MTD_Result_err, true);
                        code.areturn();
                        code.labelBinding(ok);
                    }

                    code.new_(cdName);
                    code.dup();
                    int s = 0;
                    for (Map.Entry<String, Type> f : fields.entrySet()) {
                        load(code, s, f.getValue());
                        s += width(f.getValue());
                    }
                    code.invokespecial(cdName, "<init>",
                            MethodTypeDesc.of(ConstantDescs.CD_void, fieldDescs(fields)));
                    code.invokestatic(CD_Result, "ok", MTD_Result_Object, true);
                    code.areturn();
                });
    }

    private void emitFactory(ClassBuilder cb, String name, ClassDesc returnIface, Ast.Data data,
                             String suffix) {
        ClassDesc impl = cd(data.name() + suffix);
        ClassDesc self = cd(data.name());
        MethodSignature sig = name.equals("decoder")
                ? decoderSig(self, isMapInput(data.name()))
                : encoderSig(self, encoderOutput(data));
        emitCodecFactory(cb, name, returnIface, impl, sig);
    }

    /** Emits a static {@code decoder()}/{@code encoder()} factory returning a fresh {@code impl},
     * with a generic {@code Signature} so callers get {@code Decoder<..,T>} / {@code Encoder<T,..>}
     * rather than a raw type. */
    private void emitCodecFactory(ClassBuilder cb, String name, ClassDesc returnIface, ClassDesc impl,
                                  MethodSignature sig) {
        cb.withMethod(name, MethodTypeDesc.of(returnIface),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, mb -> {
                    mb.with(SignatureAttribute.of(sig));
                    mb.withCode(code -> {
                        code.new_(impl);
                        code.dup();
                        code.invokespecial(impl, "<init>", MTD_void);
                        code.areturn();
                    });
                });
    }

    /** {@code Decoder<Map<String,Object>,T>} for objects/sums, {@code Decoder<Object,T>} for newtypes/units. */
    private static MethodSignature decoderSig(ClassDesc type, boolean mapInput) {
        String in = mapInput
                ? "Ljava/util/Map<Ljava/lang/String;Ljava/lang/Object;>;"
                : "Ljava/lang/Object;";
        return MethodSignature.parseFrom(
                "()Lnet/unit8/raoh/decode/Decoder<" + in + type.descriptorString() + ">;");
    }

    /** {@code Encoder<T,O>}: {@code O} is {@code Map<String,Object>} for objects/sums/units, or the
     * bare (boxed) scalar for a newtype — a newtype encodes to a plain value, not a map. */
    private static MethodSignature encoderSig(ClassDesc type, ClassDesc output) {
        String out = output.equals(CD_Map)
                ? "Ljava/util/Map<Ljava/lang/String;Ljava/lang/Object;>;"
                : output.descriptorString();
        return MethodSignature.parseFrom(
                "()Lnet/unit8/raoh/encode/Encoder<" + type.descriptorString() + out + ">;");
    }

    /** The runtime type a data's {@code encode} returns: a {@code Map} for objects/sums, the bare
     * boxed scalar (or {@code Object} for a nested/list/optional value) for a newtype. */
    private static ClassDesc encoderOutput(Ast.Data data) {
        return data.encoder().map(enc -> rawOutputType(enc.result())).orElse(CD_Map);
    }

    private static ClassDesc rawOutputType(Ast.RawExpr raw) {
        return switch (raw) {
            case Ast.TextRaw ignored -> CD_String;
            case Ast.IsoTextRaw ignored -> CD_String;
            case Ast.IntRaw ignored -> CD_Long;
            case Ast.BoolRaw ignored -> CD_Boolean;
            case Ast.DecimalRaw ignored -> CD_BigDecimal;
            case Ast.ObjectRaw ignored -> CD_Map;
            case Ast.EncodeRaw ignored -> CD_Object;
            case Ast.OptionRaw ignored -> CD_Object;
            case Ast.ListEnc ignored -> CD_Object;
            case Ast.MapEnc ignored -> CD_Object;
        };
    }

    /** Source-specific decoder factory signature: {@code Decoder<In,T>} with In per source. */
    private static MethodSignature decoderSigFor(Src src, ClassDesc type, boolean mapInput) {
        String in = switch (src) {
            case NEUTRAL -> mapInput
                    ? "Ljava/util/Map<Ljava/lang/String;Ljava/lang/Object;>;"
                    : "Ljava/lang/Object;";
            case JSON -> "Ltools/jackson/databind/JsonNode;";
            case JOOQ -> "Lorg/jooq/Record;";
        };
        return MethodSignature.parseFrom(
                "()Lnet/unit8/raoh/decode/Decoder<" + in + type.descriptorString() + ">;");
    }

    /** Emits a source's decoder factory ({@code jsonDecoder()} / {@code recordDecoder()}). */
    private void emitSourceFactory(ClassBuilder cb, String typeName, Src src, boolean mapInput) {
        emitCodecFactory(cb, srcFactory(src), CD_RDecoder, cd(typeName + srcSuffix(src)),
                decoderSigFor(src, cd(typeName), mapInput));
    }

    // --- source compatibility: which extra source decoders a type's shape supports (spec 10.6) ---

    /** JSON supports nested/list/map/optional but has no temporal leaf, so a type is JSON-decodable
     * iff no Date/DateTime appears anywhere in its shape. */
    private boolean jsonCompatible(String typeName) {
        return jsonOk(typeName, new HashSet<>());
    }

    private boolean jsonOk(String typeName, Set<String> seen) {
        if (!seen.add(typeName)) {
            return true;
        }
        Ast.Def def = symbols.get(typeName);
        if (def instanceof Ast.SumData sum) {
            for (String arm : sum.arms()) {
                if (!jsonOk(arm, seen)) return false;
            }
            return true;
        }
        if (def instanceof Ast.Data data) {
            for (Type t : fieldTypes(data).values()) {
                if (!jsonOkType(t, seen)) return false;
            }
        }
        return true;
    }

    private boolean jsonOkType(Type t, Set<String> seen) {
        if (t == Type.DATE || t == Type.DATETIME) return false;
        if (t instanceof Type.OptionOf o) return jsonOkType(o.element(), seen);
        if (t instanceof Type.ListOf l) return jsonOkType(l.element(), seen);
        if (t instanceof Type.MapOf m) return jsonOkType(m.value(), seen);
        if (t instanceof Type.Ref r) return jsonOk(r.name(), seen);
        return true;
    }

    /** jOOQ rows are flat: a type is Record-decodable iff it is an object (or a sum of objects/units)
     * whose every field is a scalar column — a primitive, a newtype, or an optional of those; no
     * nested object, list, map, or sum. */
    private boolean recordCompatible(String typeName) {
        Ast.Def def = symbols.get(typeName);
        if (def instanceof Ast.SumData sum) {
            for (String arm : sum.arms()) {
                Ast.Def armDef = symbols.get(arm);
                if (armDef instanceof Ast.UnitData) continue;
                if (!(armDef instanceof Ast.Data d) || !isFlatObject(d)) return false;
            }
            return true;
        }
        return def instanceof Ast.Data data && isFlatObject(data);
    }

    private boolean isFlatObject(Ast.Data data) {
        if (!(data.decoder().orElse(null) instanceof Ast.ObjectDecoder)) {
            return false;   // a newtype is a bare column, not a whole-row object
        }
        for (Type t : fieldTypes(data).values()) {
            if (!flatColumn(t)) return false;
        }
        return true;
    }

    private boolean flatColumn(Type t) {
        if (t instanceof Type.OptionOf o) return flatColumn(o.element());
        if (t instanceof Type.ListOf || t instanceof Type.MapOf || t instanceof Type.Union) return false;
        if (t instanceof Type.Ref r) {
            return symbols.get(r.name()) instanceof Ast.Data d
                    && d.decoder().orElse(null) instanceof Ast.PrimDecoder;   // newtype column only
        }
        return true;   // primitive scalar
    }

    // --- $Dec class ---

    private byte[] generateDecoderClass(ClassDesc cdName, Ast.Data data, Ast.DecoderDef dec,
                                        Map<String, Type> fields, Src src) {
        ClassDesc cdDec = cd(data.name() + srcSuffix(src));
        return build(cdDec, cb -> {
            cb.withFlags(ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            cb.withInterfaceSymbols(CD_RDecoder);
            emitDefaultCtor(cb);
            // Raoh Decoder SAM: decode(Object in, Path path) -> Result. this=0, in=1, path=2.
            cb.withMethodBody("decode", MTD_Rdecode, ClassFile.ACC_PUBLIC, code -> {
                Gen gen = new Gen(code, data, cdName, 3);
                switch (dec) {
                    case Ast.PrimDecoder prim -> emitPrimDecode(code, gen, cdName, prim, fields, src);
                    case Ast.ObjectDecoder obj -> emitObjectDecode(code, gen, cdName, obj, fields, src);
                    case Ast.NewtypeDecoder nt -> emitNewtypeDecode(code, gen, cdName, nt, fields, src);
                }
            });
        });
    }

    /** True when the type's decoder reads from a {@code Map} (object/sum), false for a bare
     * value (newtype/unit). Used to bridge nested field-value decoders with {@code nested()}. */
    private boolean isMapInput(String typeName) {
        Ast.Def def = symbols.get(typeName);
        if (def instanceof Ast.SumData) {
            return true;
        }
        if (def instanceof Ast.Data data) {
            Ast.DecoderDef d = data.decoder().orElse(null);
            if (d instanceof Ast.ObjectDecoder) {
                return true;
            }
            // a newtype reads whatever its inner type reads: a Map for an object/sum inner, a bare
            // value for a primitive one
            if (d instanceof Ast.NewtypeDecoder nt && nt.inner() instanceof Ast.DataDecRef inner) {
                return isMapInput(inner.typeName());
            }
        }
        return false;
    }

    /** Pushes a Raoh leaf {@code Decoder} for a primitive value from the given source. */
    private void emitLeafDecoder(CodeBuilder code, Ast.PrimKind kind, Src src) {
        ClassDesc owner = srcLeafOwner(src);
        switch (kind) {
            case STRING -> code.invokestatic(owner, "string", MTD_leafString);
            case INT -> code.invokestatic(owner, "long_", MTD_leafLong);
            case BOOL -> code.invokestatic(owner, "bool", MTD_leafBool);
            case DECIMAL -> code.invokestatic(owner, "decimal", MTD_leafDecimal);
            case DATE -> code.invokestatic(owner, "date", MTD_leafTemporal);
            case DATETIME -> code.invokestatic(owner, "dateTime", MTD_leafTemporal);
        }
    }

    private void emitPrimDecode(CodeBuilder code, Gen gen, ClassDesc cdName, Ast.PrimDecoder prim,
                                Map<String, Type> fields, Src src) {
        Type inputType = TypeChecker.primType(prim.from());
        ClassDesc leaf = srcLeafOwner(src);
        switch (prim.from()) {
            case TEXT -> code.invokestatic(leaf, "string", MTD_leafString);
            case INT -> code.invokestatic(leaf, "long_", MTD_leafLong);
            case BOOL -> code.invokestatic(leaf, "bool", MTD_leafBool);
            case DECIMAL -> code.invokestatic(leaf, "decimal", MTD_leafDecimal);
            case DATE -> code.invokestatic(leaf, "date", MTD_leafTemporal);
            case DATETIME -> code.invokestatic(leaf, "dateTime", MTD_leafTemporal);
        }
        code.aload(1);                                                 // in (bare value)
        code.aload(2);                                                 // path
        code.invokeinterface(CD_RDecoder, "decode", MTD_Rdecode);      // Result
        int rSlot = gen.slot(Type.STRING);
        code.astore(rSlot);
        code.aload(rSlot);
        code.instanceOf(CD_RErr);
        Label notErr = code.newLabel();
        code.ifeq(notErr);
        code.aload(rSlot);                                            // Err -> return as-is
        code.areturn();
        code.labelBinding(notErr);
        code.aload(rSlot);
        code.checkcast(CD_ROk);
        code.invokevirtual(CD_ROk, "value", MTD_Object);
        int inputSlot = gen.slot(inputType);
        unbox(code, inputType, inputSlot);
        gen.bind(prim.inputName(), inputSlot, inputType);

        for (Ast.DecStmt stmt : prim.stmts()) {
            switch (stmt) {
                case Ast.Let let -> {
                    Type t = gen.expr(let.value());
                    int slot = gen.slot(t);
                    store(code, slot, t);
                    gen.bind(let.name(), slot, t);
                }
                case Ast.Require req -> emitRequire(code, gen, req);
            }
        }
        emitConstructCall(code, gen, cdName, prim.result(), fields);
    }

    /**
     * A newtype over a non-primitive Y: decode the whole input with Y's decoder, then wrap the
     * result in X (spec 8.7). Same Err short-circuit as {@link #emitPrimDecode}, but the leaf is
     * Y's decoder rather than a primitive one.
     */
    private void emitNewtypeDecode(CodeBuilder code, Gen gen, ClassDesc cdName, Ast.NewtypeDecoder dec,
                                   Map<String, Type> fields, Src src) {
        emitDecoderObject(code, dec.inner(), src);                    // Y's decoder (for this source)
        code.aload(1);                                               // in
        code.aload(2);                                               // path
        code.invokeinterface(CD_RDecoder, "decode", MTD_Rdecode);   // Result
        int rSlot = gen.slot(Type.STRING);
        code.astore(rSlot);
        code.aload(rSlot);
        code.instanceOf(CD_RErr);
        Label notErr = code.newLabel();
        code.ifeq(notErr);
        code.aload(rSlot);                                          // Err -> return as-is
        code.areturn();
        code.labelBinding(notErr);
        code.aload(rSlot);
        code.checkcast(CD_ROk);
        code.invokevirtual(CD_ROk, "value", MTD_Object);
        Type innerType = bindType(dec.inner());
        int inSlot = gen.slot(innerType);
        unbox(code, innerType, inSlot);                             // cast Object -> Y, store
        gen.bind(dec.inputName(), inSlot, innerType);
        emitConstructCall(code, gen, cdName, dec.result(), fields);
    }

    private void emitObjectDecode(CodeBuilder code, Gen gen, ClassDesc cdName, Ast.ObjectDecoder obj,
                                  Map<String, Type> fields, Src src) {
        List<Ast.Bind> binds = obj.binds();
        int[] resultSlots = new int[binds.size()];
        for (int i = 0; i < binds.size(); i++) {
            Ast.Bind bind = binds.get(i);
            code.loadConstant(bind.key());
            if (bind.ref() instanceof Ast.OptionDecRef opt) {
                emitDecoderObject(code, opt.element(), src);
                code.invokestatic(srcFieldOwner(src), "optionalField", srcOptFieldMtd(src));
            } else {
                emitDecoderObject(code, bind.ref(), src);
                code.invokestatic(srcFieldOwner(src), "field", srcFieldMtd(src));
            }
            code.aload(1);   // in (Map)
            code.aload(2);   // path
            code.invokeinterface(CD_RDecoder, "decode", MTD_Rdecode);
            int rSlot = gen.slot(Type.STRING);
            code.astore(rSlot);
            resultSlots[i] = rSlot;
        }

        // Accumulate every field's issues (applicative), then fail once if any (spec 15).
        int accSlot = gen.slot(Type.STRING);
        code.getstatic(CD_RIssues, "EMPTY", CD_RIssues);
        code.astore(accSlot);
        for (int i = 0; i < binds.size(); i++) {
            code.aload(resultSlots[i]);
            code.instanceOf(CD_RErr);
            Label notErr = code.newLabel();
            code.ifeq(notErr);
            code.aload(accSlot);
            code.aload(resultSlots[i]);
            code.checkcast(CD_RErr);
            code.invokevirtual(CD_RErr, "issues", MTD_Err_issues);
            code.invokevirtual(CD_RIssues, "merge", MTD_Issues_merge);
            code.astore(accSlot);
            code.labelBinding(notErr);
        }
        code.aload(accSlot);
        code.invokevirtual(CD_RIssues, "isEmpty", MTD_Issues_isEmpty);
        Label ok = code.newLabel();
        code.ifne(ok);
        code.aload(accSlot);
        code.invokestatic(CD_RResult, "err", MTD_Rerr, true);
        code.areturn();
        code.labelBinding(ok);

        for (int i = 0; i < binds.size(); i++) {
            Ast.Bind bind = binds.get(i);
            Type t = bindType(bind.ref());
            code.aload(resultSlots[i]);
            code.checkcast(CD_ROk);
            code.invokevirtual(CD_ROk, "value", MTD_Object);
            if (bind.ref() instanceof Ast.OptionDecRef) {
                code.checkcast(CD_JavaOptional);
                code.invokestatic(CD_Option, "ofOptional", MTD_ofOptional, true);
                int vSlot = gen.slot(t);
                code.astore(vSlot);
                gen.bind(bind.name(), vSlot, t);
            } else {
                int vSlot = gen.slot(t);
                unbox(code, t, vSlot);
                gen.bind(bind.name(), vSlot, t);
            }
        }
        emitConstructCall(code, gen, cdName, obj.result(), fields);
    }

    private Type bindType(Ast.DecRef ref) {
        return switch (ref) {
            case Ast.PrimDecRef p -> TypeChecker.primType(p.kind());
            case Ast.DataDecRef d -> Type.ref(d.typeName());
            case Ast.ListDecRef l -> Type.list(bindType(l.element()));
            case Ast.OptionDecRef o -> Type.option(bindType(o.element()));
            case Ast.MapDecRef mp -> Type.map(bindType(mp.value()));
        };
    }

    /** Pushes a {@code Decoder} for the given field-value reference, for the given source. */
    private void emitDecoderObject(CodeBuilder code, Ast.DecRef ref, Src src) {
        switch (ref) {
            case Ast.PrimDecRef p -> emitLeafDecoder(code, p.kind(), src);
            case Ast.DataDecRef d -> {
                switch (src) {
                    case NEUTRAL -> {
                        invokeCodec(code, d.typeName(), "decoder", MTD_Rdecoder);
                        if (isMapInput(d.typeName())) {
                            code.invokestatic(CD_MapDecoders, "nested", MTD_nested);   // Decoder<Map> -> Decoder<Object>
                        }
                    }
                    // JSON field value is a JsonNode: the nested type's json decoder reads it directly.
                    case JSON -> invokeCodec(code, d.typeName(), "jsonDecoder", MTD_Rdecoder);
                    // jOOQ rows are flat: only a newtype (a bare column) is nestable; objects/sums are
                    // gated out of record generation, so this only ever pushes a newtype's Object decoder.
                    case JOOQ -> invokeCodec(code, d.typeName(), "decoder", MTD_Rdecoder);
                }
            }
            case Ast.ListDecRef l -> {
                emitDecoderObject(code, l.element(), src);
                code.invokestatic(srcListOwner(src), "list", MTD_listDec);
            }
            case Ast.OptionDecRef o -> throw new CompileException(o.pos(),
                    "optional is only supported as a direct object field");
            case Ast.MapDecRef mp -> {
                emitDecoderObject(code, mp.value(), src);
                code.invokestatic(srcListOwner(src), "map", MTD_mapDec);
            }
        }
    }

    private void emitRequire(CodeBuilder code, Gen gen, Ast.Require req) {
        gen.expr(req.cond());
        Label cont = code.newLabel();
        code.ifne(cont);
        code.getstatic(CD_RPath, "ROOT", CD_RPath);
        code.loadConstant(req.errorCode());
        code.loadConstant(req.errorCode());
        code.invokestatic(CD_RResult, "fail", MTD_Rfail, true);
        code.areturn();
        code.labelBinding(cont);
    }

    private void emitConstructCall(CodeBuilder code, Gen gen, ClassDesc cdName, Ast.Construct construct,
                                   Map<String, Type> fields) {
        emitFieldValues(gen, fields, construct.inits(), construct.spreads());
        code.invokestatic(cdName, "__construct", MethodTypeDesc.of(CD_Result, fieldDescs(fields)));
        // Souther construction Result -> Raoh boundary Result. An invariant failure becomes a
        // Raoh failure (spec 9.4, 10.1); success wraps the constructed value.
        int srSlot = gen.slot(Type.STRING);
        code.astore(srSlot);
        code.aload(srSlot);
        code.instanceOf(CD_ResultErr);
        Label okL = code.newLabel();
        code.ifeq(okL);
        code.getstatic(CD_RPath, "ROOT", CD_RPath);
        code.loadConstant("invariant_violation");
        code.aload(srSlot);
        code.checkcast(CD_ResultErr);
        code.invokevirtual(CD_ResultErr, "error", MTD_error);
        code.checkcast(CD_String);
        code.invokestatic(CD_RResult, "fail", MTD_Rfail, true);
        code.areturn();
        code.labelBinding(okL);
        code.aload(srSlot);
        code.checkcast(CD_ResultOk);
        code.invokevirtual(CD_ResultOk, "value", MTD_Object);
        code.invokestatic(CD_RResult, "ok", MTD_Rok, true);
        code.areturn();
    }

    /** Pushes each field value in declaration order, sourced from an explicit init or a spread. */
    private void emitFieldValues(Gen gen, Map<String, Type> fields, List<Ast.FieldInit> inits,
                                 List<String> spreads) {
        Map<String, Ast.FieldInit> byName = new HashMap<>();
        for (Ast.FieldInit init : inits) {
            byName.put(init.name(), init);
        }
        for (String field : fields.keySet()) {
            Ast.FieldInit init = byName.get(field);
            if (init != null) {
                gen.expr(init.value());
                continue;
            }
            for (String sp : spreads) {
                Ast.Data src = (Ast.Data) symbols.get(((Type.Ref) gen.varType(sp)).name());
                if (fieldTypes(src).containsKey(field)) {
                    gen.spreadField(sp, field);
                    break;
                }
            }
        }
    }

    // --- $Enc class ---

    private byte[] generateEncoderClass(ClassDesc cdName, Ast.Data data, Ast.EncoderDef enc) {
        ClassDesc cdEnc = cd(data.name() + "$Enc");
        return build(cdEnc, cb -> {
            cb.withFlags(ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            cb.withInterfaceSymbols(CD_REncoder);
            emitDefaultCtor(cb);
            cb.withMethodBody("encode", MTD_Rencode, ClassFile.ACC_PUBLIC, code -> {
                Gen gen = new Gen(code, data, cdName, 2);
                code.aload(1);
                code.checkcast(cdName);
                int selfSlot = gen.slot(Type.ref(data.name()));
                code.astore(selfSlot);
                gen.bind(enc.selfName(), selfSlot, Type.ref(data.name()));
                emitRawExpr(code, gen, enc.result());
                code.areturn();
            });
        });
    }

    private void emitRawExpr(CodeBuilder code, Gen gen, Ast.RawExpr raw) {
        switch (raw) {
            case Ast.TextRaw t -> gen.expr(t.arg());                 // String is a neutral value
            case Ast.IntRaw i -> {
                gen.expr(i.arg());
                box(code, Type.INT);                                 // long -> Long
            }
            case Ast.BoolRaw b -> {
                gen.expr(b.arg());
                box(code, Type.BOOL);                                // boolean -> Boolean
            }
            case Ast.DecimalRaw d -> gen.expr(d.arg());              // BigDecimal is neutral
            case Ast.IsoTextRaw t -> {
                gen.expr(t.arg());
                code.invokevirtual(CD_Object, "toString", MethodTypeDesc.of(CD_String));
            }
            case Ast.EncodeRaw e -> {
                invokeCodec(code, e.typeName(), "encoder", MTD_Rencoder);
                gen.expr(e.arg());
                code.invokeinterface(CD_REncoder, "encode", MTD_Rencode);
            }
            case Ast.OptionRaw o -> {
                Type at = gen.expr(o.access());            // Option on the stack
                Type elemType = ((Type.OptionOf) at).element();
                code.dup();
                code.instanceOf(CD_OptionNone);
                Label none = code.newLabel();
                Label end = code.newLabel();
                code.ifne(none);
                code.checkcast(CD_OptionSome);
                code.invokevirtual(CD_OptionSome, "value", MTD_Object);
                int slot = gen.slot(elemType);
                unbox(code, elemType, slot);
                gen.bind(o.elemVar(), slot, elemType);
                emitRawExpr(code, gen, o.inner());          // Some(v) -> encode v
                code.goto_(end);
                code.labelBinding(none);
                code.pop();                                 // discard the None value
                code.aconst_null();                         // null in the neutral tree
                code.labelBinding(end);
            }
            case Ast.ListEnc le -> {
                pushElemEncoder(code, le.elem());
                code.invokestatic(CD_MapEncoders, "list", MTD_Rencode_list);
                gen.expr(le.source());
                code.invokeinterface(CD_REncoder, "encode", MTD_Rencode);
            }
            case Ast.MapEnc me -> {
                pushElemEncoder(code, me.elem());
                code.invokestatic(CD_MapEncoders, "mapOf", MTD_Rencode_list);
                gen.expr(me.source());
                code.invokeinterface(CD_REncoder, "encode", MTD_Rencode);
            }
            case Ast.ObjectRaw o -> {
                code.new_(CD_LinkedHashMap);
                code.dup();
                code.invokespecial(CD_LinkedHashMap, "<init>", MTD_void);
                for (Ast.RawEntry entry : o.entries()) {
                    if (entry.value() instanceof Ast.OptionRaw opt) {
                        emitOptionalEntry(code, gen, entry.key(), opt);
                        continue;
                    }
                    code.dup();
                    code.loadConstant(entry.key());
                    emitRawExpr(code, gen, entry.value());
                    code.invokeinterface(CD_Map, "put", MTD_Map_put);
                    code.pop();
                }
                // the LinkedHashMap is itself the neutral object value
            }
        }
    }

    /**
     * Puts an optional field into the object map only when it is {@code Some}: {@code None} omits
     * the key entirely rather than writing {@code null} (spec 11.2). The map is on the stack on
     * entry and left on the stack on exit, so both the Some and None branches converge on it.
     */
    private void emitOptionalEntry(CodeBuilder code, Gen gen, String key, Ast.OptionRaw o) {
        Type at = gen.expr(o.access());                 // map, opt
        Type elemType = ((Type.OptionOf) at).element();
        code.dup();                                     // map, opt, opt
        code.instanceOf(CD_OptionNone);                 // map, opt, isNone
        Label none = code.newLabel();
        Label end = code.newLabel();
        code.ifne(none);                                // map, opt
        code.checkcast(CD_OptionSome);
        code.invokevirtual(CD_OptionSome, "value", MTD_Object);   // map, valueObj
        int slot = gen.slot(elemType);
        unbox(code, elemType, slot);                    // map (value bound to local)
        gen.bind(o.elemVar(), slot, elemType);
        code.dup();                                     // map, map
        code.loadConstant(key);                         // map, map, key
        emitRawExpr(code, gen, o.inner());              // map, map, key, encoded
        code.invokeinterface(CD_Map, "put", MTD_Map_put);
        code.pop();                                     // map
        code.goto_(end);
        code.labelBinding(none);                        // map, opt
        code.pop();                                     // map (drop the None, write nothing)
        code.labelBinding(end);
    }

    /** Pushes a Raoh {@link net.unit8.raoh.encode.Encoder} for a list/map element. */
    private void pushElemEncoder(CodeBuilder code, Ast.EncElem elem) {
        switch (elem) {
            case Ast.PrimEnc p -> code.invokestatic(CD_ObjectEncoders, leafEncoderName(p.kind()),
                    MTD_Rencode_leaf);
            case Ast.DataEnc d -> invokeCodec(code, d.typeName(), "encoder", MTD_Rencoder);
        }
    }

    /** The Raoh {@code ObjectEncoders} leaf method for each primitive (matches the leaf decoders). */
    private static String leafEncoderName(Ast.PrimKind kind) {
        return switch (kind) {
            case STRING -> "string";
            case INT -> "long_";
            case BOOL -> "bool";
            case DECIMAL -> "decimal";
            case DATE -> "date";
            case DATETIME -> "dateTime";
        };
    }

    private void emitDefaultCtor(ClassBuilder cb) {
        cb.withMethodBody("<init>", MTD_void, 0, code -> {
            code.aload(0);
            code.invokespecial(CD_Object, "<init>", MTD_void);
            code.return_();
        });
    }

    // --- expression code generation ---

    private final class Gen {
        private final CodeBuilder code;
        private final Ast.Data data;
        private final ClassDesc cdName;
        private final Map<String, Var> env = new HashMap<>();
        private int nextSlot;
        private Set<String> reqNames = Set.of();
        private Map<String, Type> reqSuccess = Map.of();

        Gen(CodeBuilder code, Ast.Data data, ClassDesc cdName, int firstSlot) {
            this.code = code;
            this.data = data;
            this.cdName = cdName;
            this.nextSlot = firstSlot;
        }

        /** Makes injected required behaviors callable inline from this body (spec 12.2, 13). */
        void requireds(Set<String> names, Map<String, Type> success) {
            this.reqNames = names;
            this.reqSuccess = success;
        }

        void bind(String name, int slot, Type type) {
            env.put(name, new Var(slot, type));
            nextSlot = Math.max(nextSlot, slot + width(type));
        }

        int slot(Type type) {
            int s = nextSlot;
            nextSlot += width(type);
            return s;
        }

        Type expr(Ast.Expr e) {
            return switch (e) {
                case Ast.LetIn li -> {
                    // a `let` reached outside tail position: bind, then value the body
                    Type vt = expr(li.value());
                    int s = slot(vt);
                    store(code, s, vt);
                    bind(li.name(), s, vt);
                    yield expr(li.body());
                }
                // a block has no value of its own; it is inlined by the call it is passed to
                case Ast.Block b -> throw new CompileException(b.pos(), "a block is not a value");
                case Ast.IntLit lit -> {
                    code.loadConstant(lit.value());
                    yield Type.INT;
                }
                case Ast.DecimalLit lit -> {
                    code.new_(CD_BigDecimal);
                    code.dup();
                    code.loadConstant(lit.value().toString());
                    code.invokespecial(CD_BigDecimal, "<init>",
                            MethodTypeDesc.of(ConstantDescs.CD_void, CD_String));
                    yield Type.DECIMAL;
                }
                case Ast.Neg neg -> {
                    Type t = expr(neg.operand());
                    if (t == Type.DECIMAL) {
                        code.invokevirtual(CD_BigDecimal, "negate", MethodTypeDesc.of(CD_BigDecimal));
                    } else {
                        code.lneg();               // Int is carried as a long
                    }
                    yield t;
                }
                case Ast.StringLit lit -> {
                    code.loadConstant(lit.value());
                    yield Type.STRING;
                }
                case Ast.BoolLit lit -> {
                    if (lit.value()) code.iconst_1(); else code.iconst_0();
                    yield Type.BOOL;
                }
                case Ast.Var v -> {
                    Var var = env.get(v.name());
                    if (var != null) {
                        load(code, var.slot(), var.type());
                        yield var.type();
                    }
                    if (symbols.get(v.name()) instanceof Ast.UnitData) {
                        ClassDesc cdU = cd(v.name());
                        code.new_(cdU);
                        code.dup();
                        code.invokespecial(cdU, "<init>", MTD_void);
                        yield Type.ref(v.name());
                    }
                    throw new CompileException(v.pos(), "unbound identifier `" + v.name() + "`");
                }
                case Ast.FieldAccess fa -> {
                    Type targetType = expr(fa.target());
                    Ast.Data owner = (Ast.Data) symbols.get(((Type.Ref) targetType).name());
                    Type ft = fieldTypes(owner).get(fa.field());
                    code.getfield(cd(owner.name()), fa.field(), jvmType(ft));
                    yield ft;
                }
                case Ast.Call call -> call(call);
                case Ast.Not not -> {
                    expr(not.operand());
                    code.iconst_1();
                    code.ixor();
                    yield Type.BOOL;
                }
                case Ast.Binary bin -> binary(bin);
                case Ast.NewData nd -> newData(nd);
                case Ast.Match m -> match(m);
                case Ast.If iff -> {
                    expr(iff.cond());
                    Label elseL = code.newLabel();
                    Label end = code.newLabel();
                    code.ifeq(elseL);
                    Type tt = expr(iff.then());
                    code.goto_(end);
                    code.labelBinding(elseL);
                    expr(iff.els());
                    code.labelBinding(end);
                    yield tt;
                }
                case Ast.ListLit lit -> listLit(lit);
                case Ast.ListComp comp -> listComp(comp);
            };
        }

        /** Builds an ArrayList of the literal's elements and returns it immutably. */
        private Type listLit(Ast.ListLit lit) {
            code.new_(CD_ArrayList);
            code.dup();
            code.invokespecial(CD_ArrayList, "<init>", MTD_void);
            Type elem = null;
            for (Ast.Expr el : lit.elements()) {
                code.dup();
                Type t = expr(el);
                box(code, t);
                code.invokevirtual(CD_ArrayList, "add", MTD_ArrayList_add);
                code.pop();
                elem = t;
            }
            code.invokestatic(CD_List, "copyOf", MTD_List_copyOf, true);
            return Type.list(elem);
        }

        /**
         * Emits a list combinator that takes a block (spec 18.4), inlining the block's body into
         * an index loop.
         *
         * <p>No closure is built: a block is second-class (spec 12.5), so it cannot outlive the
         * call and there is nothing to capture it into. A required behavior called from the body
         * reads the enclosing behavior's injected field, which is why the requirement simply
         * belongs to that behavior.
         */
        private Type listBlockOp(Ast.Call call) {
            String op = call.fn();
            boolean folding = op.equals("fold");
            Ast.Block block = (Ast.Block) call.args().get(folding ? 2 : 1);

            Type srcType = expr(call.args().get(0));
            Type elemType = ((Type.ListOf) srcType).element();
            int srcSlot = slot(Type.STRING);
            code.astore(srcSlot);

            // acc: the growing list (map/filter), the running value (fold), or the answer (all/any)
            Type accType = null;
            int accSlot;
            switch (op) {
                case "map", "filter" -> {
                    code.new_(CD_ArrayList);
                    code.dup();
                    code.invokespecial(CD_ArrayList, "<init>", MTD_void);
                    accSlot = slot(Type.STRING);
                    code.astore(accSlot);
                }
                case "fold" -> {
                    accType = expr(call.args().get(1));
                    box(code, accType);
                    accSlot = slot(Type.STRING);
                    code.astore(accSlot);
                }
                default -> {                       // all starts true, any starts false
                    code.loadConstant(op.equals("all") ? 1 : 0);
                    accSlot = slot(Type.BOOL);
                    code.istore(accSlot);
                }
            }
            Type resultType = accType;

            int iSlot = slot(Type.BOOL);
            code.iconst_0();
            code.istore(iSlot);
            Label test = code.newLabel();
            Label done = code.newLabel();
            code.labelBinding(test);
            code.iload(iSlot);
            code.aload(srcSlot);
            code.invokeinterface(CD_List, "size", MTD_size);
            code.if_icmpge(done);

            // bind the block's parameters for this element
            code.aload(srcSlot);
            code.iload(iSlot);
            code.invokeinterface(CD_List, "get", MethodTypeDesc.of(CD_Object, ConstantDescs.CD_int));
            int elemSlot = slot(elemType);
            unbox(code, elemType, elemSlot);
            if (folding) {
                int fAccSlot = slot(accType);
                code.aload(accSlot);
                unbox(code, accType, fAccSlot);
                bind(block.params().get(0), fAccSlot, accType);
                bind(block.params().get(1), elemSlot, elemType);
                Type bt = expr(block.body());
                box(code, bt);
                code.astore(accSlot);
            } else {
                bind(block.params().get(0), elemSlot, elemType);
                switch (op) {
                    case "map" -> {
                        code.aload(accSlot);
                        Type bt = expr(block.body());
                        resultType = bt;
                        box(code, bt);
                        code.invokevirtual(CD_ArrayList, "add", MTD_ArrayList_add);
                        code.pop();
                    }
                    case "filter" -> {
                        expr(block.body());
                        Label skip = code.newLabel();
                        code.ifeq(skip);
                        code.aload(accSlot);
                        load(code, elemSlot, elemType);
                        box(code, elemType);
                        code.invokevirtual(CD_ArrayList, "add", MTD_ArrayList_add);
                        code.pop();
                        code.labelBinding(skip);
                    }
                    default -> {
                        // all: a false answer ends it; any: a true one does
                        expr(block.body());
                        Label keepGoing = code.newLabel();
                        if (op.equals("all")) {
                            code.ifne(keepGoing);
                            code.iconst_0();
                        } else {
                            code.ifeq(keepGoing);
                            code.iconst_1();
                        }
                        code.istore(accSlot);
                        code.goto_(done);
                        code.labelBinding(keepGoing);
                    }
                }
            }
            code.iinc(iSlot, 1);
            code.goto_(test);
            code.labelBinding(done);

            switch (op) {
                case "map", "filter" -> {
                    code.aload(accSlot);
                    code.invokestatic(CD_List, "copyOf", MTD_List_copyOf, true);
                }
                case "fold" -> {
                    code.aload(accSlot);
                    int outSlot = slot(accType);
                    unbox(code, accType, outSlot);
                    load(code, outSlot, accType);
                }
                default -> code.iload(accSlot);
            }
            return switch (op) {
                case "map" -> Type.list(resultType);
                case "filter" -> srcType;
                case "fold" -> resultType;
                default -> Type.BOOL;
            };
        }

        /** Adds the comprehension's element once, only if every guard holds; returns an
         * immutable 0-or-1 element list. */
        private Type listComp(Ast.ListComp comp) {
            code.new_(CD_ArrayList);
            code.dup();
            code.invokespecial(CD_ArrayList, "<init>", MTD_void);
            int listSlot = slot(Type.STRING);          // holds the ArrayList (a reference)
            code.astore(listSlot);
            Label skip = code.newLabel();
            for (Ast.Expr g : comp.guards()) {
                expr(g);
                code.ifeq(skip);                       // a false guard skips the add
            }
            code.aload(listSlot);
            Type elem = expr(comp.element());
            box(code, elem);
            code.invokevirtual(CD_ArrayList, "add", MTD_ArrayList_add);
            code.pop();
            code.labelBinding(skip);
            code.aload(listSlot);
            code.invokestatic(CD_List, "copyOf", MTD_List_copyOf, true);
            return Type.list(elem);
        }

        private Type match(Ast.Match m) {
            Type st = expr(m.scrutinee());
            int sSlot = slot(st);
            store(code, sSlot, st);
            Type element = st instanceof Type.OptionOf oo ? oo.element() : null;
            Label end = code.newLabel();
            Type branchType = null;
            for (Ast.Case c : m.cases()) {
                ClassDesc armCd = element != null
                        ? (c.armType().equals("Some") ? CD_OptionSome : CD_OptionNone)
                        : matchArmClass(c.armType());
                code.aload(sSlot);
                code.instanceOf(armCd);
                Label nextCase = code.newLabel();
                code.ifeq(nextCase);
                if (element != null && c.armType().equals("Some")) {
                    // unwrap Some(v) -> v, bound to the element type
                    code.aload(sSlot);
                    code.checkcast(CD_OptionSome);
                    code.invokevirtual(CD_OptionSome, "value", MTD_Object);
                    int bslot = slot(element);
                    unbox(code, element, bslot);
                    if (c.binding() != null) {
                        bind(c.binding(), bslot, element);
                    }
                } else if (element == null && c.binding() != null) {
                    // a data arm binds the instance; a primitive arm (e.g. Int) unboxes the value
                    Type bt = TypeChecker.armBindType(c.armType());
                    code.aload(sSlot);
                    int bslot = slot(bt);
                    unbox(code, bt, bslot);
                    bind(c.binding(), bslot, bt);
                }
                branchType = expr(c.body());
                code.goto_(end);
                code.labelBinding(nextCase);
            }
            // Exhaustive by construction; this fallback is unreachable.
            code.new_(CD_IllegalStateException);
            code.dup();
            code.invokespecial(CD_IllegalStateException, "<init>", MTD_void);
            code.athrow();
            code.labelBinding(end);
            return branchType;
        }

        private Type newData(Ast.NewData nd) {
            Ast.Data owner = (Ast.Data) symbols.get(nd.typeName());
            Map<String, Type> flds = fieldTypes(owner);
            ClassDesc cdType = cd(nd.typeName());
            code.new_(cdType);
            code.dup();
            emitFieldValues(this, flds, nd.inits(), nd.spreads());
            code.invokespecial(cdType, "<init>",
                    MethodTypeDesc.of(ConstantDescs.CD_void, fieldDescs(flds)));
            return Type.ref(nd.typeName());
        }

        Type varType(String name) {
            return env.get(name).type();
        }

        void spreadField(String spreadVar, String field) {
            Var v = env.get(spreadVar);
            String srcName = ((Type.Ref) v.type()).name();
            Ast.Data src = (Ast.Data) symbols.get(srcName);
            load(code, v.slot(), v.type());
            code.getfield(cd(srcName), field, jvmType(fieldTypes(src).get(field)));
        }

        private Type call(Ast.Call call) {
            switch (call.fn()) {
                case "length" -> {
                    Type t = expr(call.args().get(0));
                    if (t instanceof Type.ListOf) {
                        code.invokeinterface(CD_List, "size", MTD_size);
                    } else {
                        code.invokevirtual(CD_String, "length", MethodTypeDesc.of(ConstantDescs.CD_int));
                    }
                    code.i2l();
                    return Type.INT;
                }
                case "contains" -> {
                    expr(call.args().get(0));
                    expr(call.args().get(1));
                    code.invokevirtual(CD_String, "contains",
                            MethodTypeDesc.of(ConstantDescs.CD_boolean, CD_CharSequence));
                    return Type.BOOL;
                }
                case "trim" -> {
                    expr(call.args().get(0));
                    code.invokevirtual(CD_String, "trim", MethodTypeDesc.of(CD_String));
                    return Type.STRING;
                }
                case "lowercase" -> {
                    expr(call.args().get(0));
                    code.invokevirtual(CD_String, "toLowerCase", MethodTypeDesc.of(CD_String));
                    return Type.STRING;
                }
                case "uppercase" -> {
                    expr(call.args().get(0));
                    code.invokevirtual(CD_String, "toUpperCase", MethodTypeDesc.of(CD_String));
                    return Type.STRING;
                }
                case "concat" -> {
                    expr(call.args().get(0));
                    expr(call.args().get(1));
                    code.invokevirtual(CD_String, "concat", MethodTypeDesc.of(CD_String, CD_String));
                    return Type.STRING;
                }
                case "startsWith", "endsWith" -> {
                    expr(call.args().get(0));
                    expr(call.args().get(1));
                    code.invokevirtual(CD_String,
                            call.fn().equals("startsWith") ? "startsWith" : "endsWith",
                            MethodTypeDesc.of(ConstantDescs.CD_boolean, CD_String));
                    return Type.BOOL;
                }
                case "substring" -> {
                    expr(call.args().get(0));
                    expr(call.args().get(1));
                    code.l2i();                          // Int is a long; substring takes int indices
                    expr(call.args().get(2));
                    code.l2i();
                    code.invokevirtual(CD_String, "substring",
                            MethodTypeDesc.of(CD_String, ConstantDescs.CD_int, ConstantDescs.CD_int));
                    return Type.STRING;
                }
                case "map", "filter", "fold", "all", "any" -> {
                    return listBlockOp(call);
                }
                case "get" -> {
                    Type ct = expr(call.args().get(0));      // List or Map on stack
                    expr(call.args().get(1));                // long index / String key
                    if (ct instanceof Type.MapOf mo) {
                        code.invokestatic(CD_Maps, "get", MethodTypeDesc.of(CD_Option, CD_Map, CD_String));
                        return Type.option(mo.value());
                    }
                    code.invokestatic(CD_Lists, "get",
                            MethodTypeDesc.of(CD_Option, CD_List, ConstantDescs.CD_long));
                    return Type.option(((Type.ListOf) ct).element());
                }
                case "containsKey" -> {
                    expr(call.args().get(0));
                    expr(call.args().get(1));
                    code.invokestatic(CD_Maps, "containsKey",
                            MethodTypeDesc.of(ConstantDescs.CD_boolean, CD_Map, CD_String));
                    return Type.BOOL;
                }
                case "keys" -> {
                    expr(call.args().get(0));
                    code.invokestatic(CD_Maps, "keys", MethodTypeDesc.of(CD_List, CD_Map));
                    return Type.list(Type.STRING);
                }
                case "values" -> {
                    Type mt = expr(call.args().get(0));
                    code.invokestatic(CD_Maps, "values", MethodTypeDesc.of(CD_List, CD_Map));
                    return Type.list(((Type.MapOf) mt).value());
                }
                case "add", "subtract", "multiply" -> {
                    Type t = expr(call.args().get(0));
                    expr(call.args().get(1));
                    if (t == Type.DECIMAL) {
                        code.invokevirtual(CD_BigDecimal, call.fn(),
                                MethodTypeDesc.of(CD_BigDecimal, CD_BigDecimal));
                    } else {
                        // Int arithmetic aborts on overflow rather than wrapping (spec 18.2)
                        String exact = switch (call.fn()) {
                            case "add" -> "addExact";
                            case "subtract" -> "subtractExact";
                            default -> "multiplyExact";
                        };
                        code.invokestatic(CD_IntMath, exact, MTD_intExact);
                    }
                    return t;
                }
                case "compare" -> {
                    Type t = expr(call.args().get(0));
                    expr(call.args().get(1));
                    if (t == Type.DECIMAL) {
                        code.invokevirtual(CD_BigDecimal, "compareTo",
                                MethodTypeDesc.of(ConstantDescs.CD_int, CD_BigDecimal));
                    } else {
                        code.invokestatic(CD_Long, "compare",
                                MethodTypeDesc.of(ConstantDescs.CD_int, ConstantDescs.CD_long,
                                        ConstantDescs.CD_long));
                    }
                    code.i2l();
                    return Type.INT;
                }
                case "divide" -> {
                    if (call.args().size() == 4) {
                        return decimalDivide(call);
                    }
                    return intDivide(call, true);
                }
                case "remainder" -> {
                    return intDivide(call, false);
                }
                default -> {
                    if (reqNames.contains(call.fn())) {
                        return requiredCall(call);
                    }
                    throw new CompileException(call.pos(), "unknown function `" + call.fn() + "`");
                }
            }
        }

        /** {@code divide}/{@code remainder} on Int: a zero divisor takes the DivisionByZero arm,
         * otherwise the quotient/remainder is boxed (spec 18.2). */
        private Type intDivide(Ast.Call call, boolean divide) {
            expr(call.args().get(0));
            int aSlot = slot(Type.INT);
            code.lstore(aSlot);
            expr(call.args().get(1));
            int bSlot = slot(Type.INT);
            code.lstore(bSlot);
            code.lload(bSlot);
            code.lconst_0();
            code.lcmp();
            Label zero = code.newLabel();
            Label end = code.newLabel();
            code.ifeq(zero);                       // b == 0 -> DivisionByZero arm
            code.lload(aSlot);
            code.lload(bSlot);
            if (divide) {
                code.ldiv();
            } else {
                code.lrem();
            }
            code.invokestatic(CD_Long, "valueOf", MTD_Long_valueOf);   // box the quotient
            code.goto_(end);
            code.labelBinding(zero);
            code.getstatic(CD_DivisionByZero, "INSTANCE", CD_DivisionByZero);
            code.labelBinding(end);
            return Type.union(new java.util.LinkedHashSet<>(java.util.List.of("Int", "DivisionByZero")));
        }

        /** {@code divide(a, b, scale, mode)} on Decimal: a zero divisor takes the DivisionByZero
         * arm, otherwise {@code a.divide(b, scale, RoundingMode.mode)} (spec 18.3). */
        private Type decimalDivide(Ast.Call call) {
            expr(call.args().get(0));
            int aSlot = slot(Type.DECIMAL);
            code.astore(aSlot);
            expr(call.args().get(1));
            int bSlot = slot(Type.DECIMAL);
            code.astore(bSlot);
            code.aload(bSlot);
            code.invokevirtual(CD_BigDecimal, "signum", MethodTypeDesc.of(ConstantDescs.CD_int));
            Label zero = code.newLabel();
            Label end = code.newLabel();
            code.ifeq(zero);                       // signum == 0 -> DivisionByZero arm
            code.aload(aSlot);
            code.aload(bSlot);
            expr(call.args().get(2));              // scale (Int, a long)
            code.l2i();
            String mode = ((Ast.Var) call.args().get(3)).name();
            code.getstatic(CD_RoundingMode, mode, CD_RoundingMode);
            code.invokevirtual(CD_BigDecimal, "divide", MTD_bdDivide);
            code.goto_(end);
            code.labelBinding(zero);
            code.getstatic(CD_DivisionByZero, "INSTANCE", CD_DivisionByZero);
            code.labelBinding(end);
            return Type.union(new java.util.LinkedHashSet<>(java.util.List.of("Decimal", "DivisionByZero")));
        }

        /** Emits an inline call to an injected required behavior, leaving its success value on
         * the stack cast to the success type (spec 12.2, 13). */
        private Type requiredCall(Ast.Call call) {
            code.aload(0);
            code.getfield(cdName, call.fn(), CD_Behavior);
            if (call.args().isEmpty()) {
                code.aconst_null();        // `() -> R`: the implementation ignores the input
            } else {
                Type at = expr(call.args().get(0));
                box(code, at);
            }
            code.invokeinterface(CD_Behavior, "apply", MTD_apply);
            Type success = reqSuccess.get(call.fn());
            stackCast(success);
            return success;
        }

        /** Casts the {@code Object} on the stack to {@code type}, unboxing primitives. */
        private void stackCast(Type type) {
            if (type == Type.INT) {
                code.checkcast(CD_Long);
                code.invokevirtual(CD_Long, "longValue", MethodTypeDesc.of(ConstantDescs.CD_long));
            } else if (type == Type.BOOL) {
                code.checkcast(CD_Boolean);
                code.invokevirtual(CD_Boolean, "booleanValue", MethodTypeDesc.of(ConstantDescs.CD_boolean));
            } else if (!(type instanceof Type.Union)) {
                code.checkcast(jvmType(type));
            }
        }

        private Type binary(Ast.Binary bin) {
            switch (bin.op()) {
                case AND -> {
                    expr(bin.left());
                    expr(bin.right());
                    code.iand();
                    return Type.BOOL;
                }
                case OR -> {
                    expr(bin.left());
                    expr(bin.right());
                    code.ior();
                    return Type.BOOL;
                }
                // +, -, * are Int-only (Decimal uses the add/subtract/multiply calls) and abort on
                // overflow rather than wrapping (spec 18.2).
                case ADD -> {
                    expr(bin.left());
                    expr(bin.right());
                    code.invokestatic(CD_IntMath, "addExact", MTD_intExact);
                    return Type.INT;
                }
                case SUB -> {
                    expr(bin.left());
                    expr(bin.right());
                    code.invokestatic(CD_IntMath, "subtractExact", MTD_intExact);
                    return Type.INT;
                }
                case MUL -> {
                    expr(bin.left());
                    expr(bin.right());
                    code.invokestatic(CD_IntMath, "multiplyExact", MTD_intExact);
                    return Type.INT;
                }
                case CONCAT -> {
                    Type lt = expr(bin.left());
                    expr(bin.right());
                    code.invokestatic(CD_Lists, "concat", MTD_Lists_concat);
                    return lt;
                }
                default -> {
                    Type lt = expr(bin.left());
                    expr(bin.right());
                    if (lt == Type.STRING) {
                        code.invokevirtual(CD_String, "equals",
                                MethodTypeDesc.of(ConstantDescs.CD_boolean, CD_Object));
                        if (bin.op() == Ast.BinOp.NE) {
                            code.iconst_1();
                            code.ixor();
                        }
                        return Type.BOOL;
                    }
                    if (lt == Type.DECIMAL) {
                        emitDecimalEquals(code);          // by value, ignoring scale (spec 7.1)
                        if (bin.op() == Ast.BinOp.NE) {
                            code.iconst_1();
                            code.ixor();
                        }
                        return Type.BOOL;
                    }
                    if (isReference(lt)) {
                        // a data (or any boxed value) compares by its fields — the generated
                        // equals (spec 7.1). Objects.equals keeps it null-tolerant.
                        code.invokestatic(CD_Objects, "equals", MTD_Objects_equals);
                        if (bin.op() == Ast.BinOp.NE) {
                            code.iconst_1();
                            code.ixor();
                        }
                        return Type.BOOL;
                    }
                    comparisonMaterialize(bin.op(), lt == Type.INT);
                    return Type.BOOL;
                }
            }
        }

        private void comparisonMaterialize(Ast.BinOp op, boolean isLong) {
            Label t = code.newLabel();
            Label end = code.newLabel();
            if (isLong) {
                code.lcmp();
                switch (op) {
                    case LT -> code.iflt(t);
                    case LE -> code.ifle(t);
                    case GT -> code.ifgt(t);
                    case GE -> code.ifge(t);
                    case EQ -> code.ifeq(t);
                    case NE -> code.ifne(t);
                    default -> throw new IllegalStateException();
                }
            } else {
                switch (op) {
                    case LT -> code.if_icmplt(t);
                    case LE -> code.if_icmple(t);
                    case GT -> code.if_icmpgt(t);
                    case GE -> code.if_icmpge(t);
                    case EQ -> code.if_icmpeq(t);
                    case NE -> code.if_icmpne(t);
                    default -> throw new IllegalStateException();
                }
            }
            code.iconst_0();
            code.goto_(end);
            code.labelBinding(t);
            code.iconst_1();
            code.labelBinding(end);
        }
    }

    private record Var(int slot, Type type) {}

    // --- helpers ---

    private void unbox(CodeBuilder code, Type type, int slot) {
        if (type == Type.INT) {
            code.checkcast(CD_Long);
            code.invokevirtual(CD_Long, "longValue", MethodTypeDesc.of(ConstantDescs.CD_long));
            code.lstore(slot);
        } else if (type == Type.BOOL) {
            code.checkcast(CD_Boolean);
            code.invokevirtual(CD_Boolean, "booleanValue", MethodTypeDesc.of(ConstantDescs.CD_boolean));
            code.istore(slot);
        } else {
            code.checkcast(jvmType(type));
            code.astore(slot);
        }
    }

    private ClassDesc jvmType(Type type) {
        if (type == Type.INT) return ConstantDescs.CD_long;
        if (type == Type.STRING) return CD_String;
        if (type == Type.BOOL) return ConstantDescs.CD_boolean;
        if (type == Type.DECIMAL) return CD_BigDecimal;
        if (type == Type.DATE) return CD_LocalDate;
        if (type == Type.DATETIME) return CD_LocalDateTime;
        if (type instanceof Type.OptionOf) return CD_Option;
        if (type instanceof Type.ListOf) return CD_List;
        if (type instanceof Type.MapOf) return CD_Map;
        if (type instanceof Type.Union) return CD_Object;
        return armClass(((Type.Ref) type).name());
    }

    private ClassDesc[] fieldDescs(Map<String, Type> fields) {
        List<ClassDesc> descs = new ArrayList<>();
        for (Type t : fields.values()) {
            descs.add(jvmType(t));
        }
        return descs.toArray(new ClassDesc[0]);
    }

    private static void load(CodeBuilder code, int slot, Type type) {
        if (type == Type.INT) {
            code.lload(slot);
        } else if (type == Type.BOOL) {
            code.iload(slot);
        } else {
            code.aload(slot); // String or a data reference
        }
    }

    private static void store(CodeBuilder code, int slot, Type type) {
        if (type == Type.INT) {
            code.lstore(slot);
        } else if (type == Type.BOOL) {
            code.istore(slot);
        } else {
            code.astore(slot);
        }
    }

    private static void pushInt(CodeBuilder code, int v) {
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

    private static int width(Type type) {
        return type == Type.INT ? 2 : 1;
    }
}
