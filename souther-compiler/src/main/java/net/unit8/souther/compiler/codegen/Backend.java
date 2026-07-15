package net.unit8.souther.compiler.codegen;

import net.unit8.souther.compiler.CompileException;
import net.unit8.souther.compiler.ast.Ast;
import net.unit8.souther.compiler.check.Type;
import net.unit8.souther.compiler.check.TypeChecker;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.attribute.PermittedSubclassesAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
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
    private static final ClassDesc CD_Raw = ClassDesc.of("net.unit8.souther.runtime.Raw");
    private static final ClassDesc CD_Result = ClassDesc.of("net.unit8.souther.runtime.Result");
    private static final ClassDesc CD_ResultOk = CD_Result.nested("Ok");
    private static final ClassDesc CD_ResultErr = CD_Result.nested("Err");
    private static final ClassDesc CD_NonEmptyList = ClassDesc.of("net.unit8.souther.runtime.NonEmptyList");
    private static final ClassDesc CD_Decoder = ClassDesc.of("net.unit8.souther.runtime.Decoder");
    private static final ClassDesc CD_Encoder = ClassDesc.of("net.unit8.souther.runtime.Encoder");
    private static final ClassDesc CD_Decoders = ClassDesc.of("net.unit8.souther.runtime.Decoders");
    private static final ClassDesc CD_Encoders = ClassDesc.of("net.unit8.souther.runtime.Encoders");
    private static final ClassDesc CD_Behavior = ClassDesc.of("net.unit8.souther.runtime.Behavior");
    private static final ClassDesc CD_Violation = ClassDesc.of("net.unit8.souther.runtime.Violation");
    private static final ClassDesc CD_DecodeFailure = ClassDesc.of("net.unit8.souther.runtime.DecodeFailure");
    private static final ClassDesc CD_DivisionByZero = ClassDesc.of("net.unit8.souther.runtime.DivisionByZero");
    private static final ClassDesc CD_Boolean = ClassDesc.of("java.lang.Boolean");
    private static final ClassDesc CD_BigDecimal = ClassDesc.of("java.math.BigDecimal");
    private static final ClassDesc CD_LocalDate = ClassDesc.of("java.time.LocalDate");
    private static final ClassDesc CD_LocalDateTime = ClassDesc.of("java.time.LocalDateTime");
    private static final ClassDesc CD_Lists = ClassDesc.of("net.unit8.souther.runtime.Lists");
    private static final ClassDesc CD_Maps = ClassDesc.of("net.unit8.souther.runtime.Maps");
    private static final ClassDesc CD_Option = ClassDesc.of("net.unit8.souther.runtime.Option");
    private static final ClassDesc CD_OptionSome = CD_Option.nested("Some");
    private static final ClassDesc CD_OptionNone = CD_Option.nested("None");
    private static final ClassDesc CD_IllegalStateException = ClassDesc.of("java.lang.IllegalStateException");

    private static final MethodTypeDesc MTD_void = MethodTypeDesc.of(ConstantDescs.CD_void);
    private static final MethodTypeDesc MTD_Result_Raw = MethodTypeDesc.of(CD_Result, CD_Raw);
    private static final MethodTypeDesc MTD_decode = MethodTypeDesc.of(CD_Object, CD_Raw);
    private static final MethodTypeDesc MTD_finish = MethodTypeDesc.of(CD_Object, CD_Result);
    private static final MethodTypeDesc MTD_decodeFailure = MethodTypeDesc.of(CD_Object, CD_NonEmptyList);
    private static final MethodTypeDesc MTD_Result_Object = MethodTypeDesc.of(CD_Result, CD_Object);
    private static final MethodTypeDesc MTD_Result_String_String =
            MethodTypeDesc.of(CD_Result, CD_String, CD_String);
    private static final MethodTypeDesc MTD_Object = MethodTypeDesc.of(CD_Object);
    private static final MethodTypeDesc MTD_objectField =
            MethodTypeDesc.of(CD_Result, CD_Raw, CD_String, CD_Decoder);
    private static final MethodTypeDesc MTD_decoder = MethodTypeDesc.of(CD_Decoder);
    private static final MethodTypeDesc MTD_listOf = MethodTypeDesc.of(CD_Decoder, CD_Decoder);
    private static final MethodTypeDesc MTD_size = MethodTypeDesc.of(ConstantDescs.CD_int);
    private static final MethodTypeDesc MTD_encoder = MethodTypeDesc.of(CD_Encoder);
    private static final MethodTypeDesc MTD_encode = MethodTypeDesc.of(CD_Raw, CD_Object);
    private static final MethodTypeDesc MTD_tagged = MethodTypeDesc.of(CD_Raw, CD_Raw, CD_String, CD_String);
    private static final MethodTypeDesc MTD_encodeList = MethodTypeDesc.of(CD_Raw, CD_List, CD_Encoder);
    private static final MethodTypeDesc MTD_ArrayList_add = MethodTypeDesc.of(ConstantDescs.CD_boolean, CD_Object);
    private static final MethodTypeDesc MTD_List_copyOf = MethodTypeDesc.of(CD_List, CD_Collection);
    private static final MethodTypeDesc MTD_Lists_concat = MethodTypeDesc.of(CD_List, CD_List, CD_List);
    private static final MethodTypeDesc MTD_encodeMap = MethodTypeDesc.of(CD_Raw, CD_Map, CD_Encoder);
    private static final MethodTypeDesc MTD_mergeErrors =
            MethodTypeDesc.of(CD_NonEmptyList, CD_Result.arrayType());
    private static final MethodTypeDesc MTD_Map_put = MethodTypeDesc.of(CD_Object, CD_Object, CD_Object);
    private static final MethodTypeDesc MTD_Raw_object = MethodTypeDesc.of(CD_Raw, CD_Map);
    private static final MethodTypeDesc MTD_variant =
            MethodTypeDesc.of(CD_Object, CD_Raw, CD_String, CD_String, CD_Decoder);
    private static final MethodTypeDesc MTD_noVariant = MethodTypeDesc.of(CD_Object, CD_String);
    private static final MethodTypeDesc MTD_apply = MethodTypeDesc.of(CD_Object, CD_Object);
    private static final MethodTypeDesc MTD_orValue = MethodTypeDesc.of(CD_Object, CD_Result);
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
        Map<String, byte[]> out = new LinkedHashMap<>();
        for (Ast.Def def : module.defs()) {
            switch (def) {
                case Ast.Data data -> b.generateData(data, out);
                case Ast.SumData sum -> b.generateSum(sum, out);
                case Ast.UnitData unit -> b.generateUnit(unit, out);
            }
        }
        Set<String> requiredNames = new HashSet<>();
        Map<String, Type> requiredSuccess = new HashMap<>();
        for (Ast.RequiredBehavior r : module.requireds()) {
            requiredNames.add(r.name());
            requiredSuccess.put(r.name(), b.successType(r.ret()));
            List<String> unitArms = new ArrayList<>();
            for (Ast.TypeRef t : r.ret().arms()) {
                if (b.symbols.get(t.name()) instanceof Ast.UnitData) {
                    unitArms.add(t.name());
                }
            }
            out.put(module.name() + "." + r.name(), b.generateRequiredBase(r.name(), unitArms));
        }
        Map<String, TypeChecker.Sig> sigs = TypeChecker.signatures(module, b.symbols);
        // each body behavior's own required dependencies, in constructor (first-seen) order
        Map<String, List<String>> behaviorDeps = new HashMap<>();
        for (Ast.BehaviorDef bd : module.behaviors()) {
            if (bd instanceof Ast.BodyBehavior body) {
                behaviorDeps.put(body.name(), TypeChecker.requiredCalls(body, requiredNames));
            }
        }
        for (Ast.BehaviorDef bd : module.behaviors()) {
            switch (bd) {
                case Ast.BodyBehavior body -> out.put(module.name() + "." + body.name(),
                        b.generateBodyBehavior(body, requiredNames, requiredSuccess));
                case Ast.PipeBehavior pipe ->
                        out.put(module.name() + "." + pipe.name(),
                                b.generatePipe(pipe, requiredNames, sigs, behaviorDeps));
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
     */
    private byte[] generateRequiredBase(String name, List<String> unitArms) {
        ClassDesc cdR = cd(name);
        return CF.build(cdR, cb -> {
            cb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT | ClassFile.ACC_SUPER);
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

    /** The JVM class for an output arm: the built-in runtime {@code Violation} for 制約違反,
     * otherwise the generated data class in this module. */
    private ClassDesc armClass(String typeName) {
        return switch (typeName) {
            case "制約違反" -> CD_Violation;
            case "DivisionByZero" -> CD_DivisionByZero;
            default -> cd(typeName);
        };
    }

    private void generateData(Ast.Data data, Map<String, byte[]> out) {
        ClassDesc cdName = cd(data.name());
        Map<String, Type> fields = fieldTypes(data);

        out.put(pkg + "." + data.name(), CF.build(cdName, cb -> {
            cb.withFlags(pub(data.name()) | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            ClassDesc[] ifaces = armInterfaces(data.name());
            if (ifaces.length > 0) {
                cb.withInterfaceSymbols(ifaces);
            }
            for (Map.Entry<String, Type> f : fields.entrySet()) {
                cb.withField(f.getKey(), jvmType(f.getValue()), ClassFile.ACC_FINAL);
            }
            emitCtor(cb, cdName, fields);
            emitConstructMethod(cb, cdName, data, fields);
            data.decoder().ifPresent(d -> emitFactory(cb, "decoder", CD_RDecoder, data, "$Dec"));
            data.encoder().ifPresent(e -> emitFactory(cb, "encoder", CD_REncoder, data, "$Enc"));
        }));

        data.decoder().ifPresent(dec ->
                out.put(pkg + "." + data.name() + "$Dec", generateDecoderClass(cdName, data, dec, fields)));
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
        out.put(pkg + "." + sum.name(), CF.build(cdX, cb -> {
            cb.withFlags(pub(sum.name()) | ClassFile.ACC_INTERFACE | ClassFile.ACC_ABSTRACT);
            cb.with(PermittedSubclassesAttribute.ofSymbols(armCds));
            sum.decoder().ifPresent(disc -> {
                ClassDesc cdDec = cd(sum.name() + "$Dec");
                cb.withMethodBody("decoder", MTD_Rdecoder,
                        ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, code -> {
                            code.new_(cdDec);
                            code.dup();
                            code.invokespecial(cdDec, "<init>", MTD_void);
                            code.areturn();
                        });
            });
            sum.encoder().ifPresent(enc -> {
                ClassDesc cdEnc = cd(sum.name() + "$Enc");
                cb.withMethodBody("encoder", MTD_Rencoder,
                        ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, code -> {
                            code.new_(cdEnc);
                            code.dup();
                            code.invokespecial(cdEnc, "<init>", MTD_void);
                            code.areturn();
                        });
            });
        }));
        sum.decoder().ifPresent(disc ->
                out.put(pkg + "." + sum.name() + "$Dec", generateSumDecoder(sum, disc)));
        sum.encoder().ifPresent(enc ->
                out.put(pkg + "." + sum.name() + "$Enc", generateSumEncoder(sum, enc)));
    }

    private byte[] generateSumEncoder(Ast.SumData sum, Ast.SumEncoder enc) {
        ClassDesc cdEnc = cd(sum.name() + "$Enc");
        return CF.build(cdEnc, cb -> {
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

    private byte[] generateSumDecoder(Ast.SumData sum, Ast.Discriminate disc) {
        ClassDesc cdDec = cd(sum.name() + "$Dec");
        return CF.build(cdDec, cb -> {
            cb.withFlags(ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            cb.withInterfaceSymbols(CD_RDecoder);
            emitDefaultCtor(cb);
            // Build a Raoh discriminate decoder and delegate: the tag is read from the
            // discriminator key, each arm dispatches to that arm's Raoh decoder (spec 10.3).
            cb.withMethodBody("decode", MTD_Rdecode, ClassFile.ACC_PUBLIC, code -> {
                code.loadConstant(disc.key());
                code.loadConstant(disc.key());
                code.invokestatic(CD_ObjectDecoders, "string", MTD_leafString);
                code.invokestatic(CD_MapDecoders, "field", MTD_field);
                pushInt(code, disc.variants().size());
                code.anewarray(CD_RVariant);
                int i = 0;
                for (Ast.Variant v : disc.variants()) {
                    code.dup();
                    pushInt(code, i);
                    code.loadConstant(v.tag());
                    invokeCodec(code, v.armType(), "decoder", MTD_Rdecoder);
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
        out.put(pkg + "." + unit.name(), CF.build(cdU, cb -> {
            cb.withFlags(pub(unit.name()) | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            ClassDesc[] ifaces = armInterfaces(unit.name());
            if (ifaces.length > 0) {
                cb.withInterfaceSymbols(ifaces);
            }
            emitDefaultCtor(cb);
            // a unit is a field-less data: its codec reads/writes nothing but the tag the sum adds
            emitNewFactory(cb, "decoder", CD_RDecoder, cdDec);
            emitNewFactory(cb, "encoder", CD_REncoder, cdEnc);
        }));
        out.put(pkg + "." + unit.name() + "$Dec", generateUnitDecoder(cdU, cdDec));
        out.put(pkg + "." + unit.name() + "$Enc", generateUnitEncoder(cdEnc));
    }

    /** Decodes a unit: ignore the input (a unit carries no data) and build the singleton value. */
    private byte[] generateUnitDecoder(ClassDesc cdU, ClassDesc cdDec) {
        return CF.build(cdDec, cb -> {
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
        return CF.build(cdEnc, cb -> {
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
    private void emitNewFactory(ClassBuilder cb, String name, ClassDesc returnIface, ClassDesc impl) {
        cb.withMethodBody(name, MethodTypeDesc.of(returnIface),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, code -> {
                    code.new_(impl);
                    code.dup();
                    code.invokespecial(impl, "<init>", MTD_void);
                    code.areturn();
                });
    }

    // --- behaviors ---

    private byte[] generateBodyBehavior(Ast.BodyBehavior b, Set<String> requiredNames,
                                        Map<String, Type> requiredSuccess) {
        ClassDesc cdB = cd(b.name());
        int n = b.params().size();
        List<String> injected = TypeChecker.requiredCalls(b, requiredNames);
        ClassDesc[] applyParams = new ClassDesc[n];
        for (int i = 0; i < n; i++) {
            applyParams[i] = CD_Object;
        }
        MethodTypeDesc mtdApply = MethodTypeDesc.of(CD_Object, applyParams);
        return CF.build(cdB, cb -> {
            cb.withFlags(pub(b.name()) | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            if (n == 1) {
                cb.withInterfaceSymbols(CD_Behavior); // single-input behaviors compose with >>
            }
            emitInjection(cb, cdB, injected);
            cb.withMethodBody("apply", mtdApply, ClassFile.ACC_PUBLIC, code -> {
                Gen gen = new Gen(code, null, cdB, n + 1);
                gen.requireds(requiredNames, requiredSuccess);
                for (int i = 0; i < n; i++) {
                    Ast.Param p = b.params().get(i);
                    Type pt = successType(p.type());
                    code.aload(i + 1);
                    int slot = gen.slot(pt);
                    unbox(code, pt, slot);
                    gen.bind(p.name(), slot, pt);
                }
                for (Ast.BStmt stmt : b.stmts()) {
                    switch (stmt) {
                        case Ast.Let let when let.value() instanceof Ast.Call call
                                && requiredNames.contains(call.fn()) -> {
                            // call an injected required behavior; its apply returns the value directly
                            code.aload(0);
                            code.getfield(cdB, call.fn(), CD_Behavior);
                            Type at = gen.expr(call.args().get(0));
                            box(code, at);
                            code.invokeinterface(CD_Behavior, "apply", MTD_apply);
                            Type letType = requiredSuccess.get(call.fn());
                            int vSlot = gen.slot(letType);
                            unbox(code, letType, vSlot);
                            gen.bind(let.name(), vSlot, letType);
                        }
                        case Ast.Let let -> {
                            Type t = gen.expr(let.value());
                            int slot = gen.slot(t);
                            store(code, slot, t);
                            gen.bind(let.name(), slot, t);
                        }
                        case Ast.Guard guard -> {
                            gen.expr(guard.cond());
                            Label cont = code.newLabel();
                            code.ifne(cont);
                            Type ft = gen.expr(guard.failure());
                            box(code, ft);
                            code.areturn();
                            code.labelBinding(cont);
                        }
                    }
                }
                if (b.result() instanceof Ast.NewData nd
                        && TypeChecker.isInvariantBearing(nd.typeName(), symbols)) {
                    // railway construct: __construct checks invariants and returns a Result
                    ClassDesc cdType = cd(nd.typeName());
                    Map<String, Type> flds = fieldTypes((Ast.Data) symbols.get(nd.typeName()));
                    emitFieldValues(gen, flds, nd.inits(), nd.spreads());
                    code.invokestatic(cdType, "__construct", MethodTypeDesc.of(CD_Result, fieldDescs(flds)));
                    code.invokestatic(CD_Violation, "orValue", MTD_orValue);
                    code.areturn();
                } else {
                    Type rt = gen.expr(b.result());
                    box(code, rt);
                    code.areturn();
                }
            });
        });
    }

    private byte[] generatePipe(Ast.PipeBehavior pipe, Set<String> requiredNames,
                                Map<String, TypeChecker.Sig> sigs, Map<String, List<String>> behaviorDeps) {
        ClassDesc cdP = cd(pipe.name());
        // the pipeline's injected fields are the union of every stage's requirements, first-seen
        // (spec 14.3): a required stage requires itself; a body stage requires its own deps.
        LinkedHashSet<String> fields = new LinkedHashSet<>();
        for (String stage : pipe.stages()) {
            if (requiredNames.contains(stage)) {
                fields.add(stage);
            } else {
                fields.addAll(behaviorDeps.getOrDefault(stage, List.of()));
            }
        }
        List<String> reqStages = new ArrayList<>(fields);

        return CF.build(cdP, cb -> {
            cb.withFlags(pub(pipe.name()) | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            cb.withInterfaceSymbols(CD_Behavior);
            emitInjection(cb, cdP, reqStages);

            cb.withMethodBody("apply", MTD_apply, ClassFile.ACC_PUBLIC, code -> {
                // slot 1 always holds the running value (an output arm, as an Object).
                List<String> stages = pipe.stages();
                // stage 0 consumes the whole input unconditionally
                applyStage(code, cdP, stages.get(0), requiredNames, behaviorDeps);
                Type running = TypeChecker.stageSig(stages.get(0), sigs, symbols, pipe.pos()).out();
                for (int i = 1; i < stages.size(); i++) {
                    String stage = stages.get(i);
                    TypeChecker.Sig g = TypeChecker.stageSig(stage, sigs, symbols, pipe.pos());
                    if (TypeChecker.isDataLike(running)) {
                        // route: apply g only if the running value is one of the arms it accepts
                        List<String> accepted = new ArrayList<>();
                        for (String arm : TypeChecker.namesOf(running)) {
                            if (TypeChecker.subtypeOf(Type.ref(arm), g.in())) {
                                accepted.add(arm);
                            }
                        }
                        Label doApply = code.newLabel();
                        Label after = code.newLabel();
                        for (String arm : accepted) {
                            code.aload(1);
                            code.instanceOf(armClass(arm));
                            code.ifne(doApply);
                        }
                        code.goto_(after);
                        code.labelBinding(doApply);
                        applyStage(code, cdP, stage, requiredNames, behaviorDeps);
                        code.labelBinding(after);
                    } else {
                        applyStage(code, cdP, stage, requiredNames, behaviorDeps);
                    }
                    running = TypeChecker.route(running, g, pipe.pos());
                }
                code.aload(1);
                code.areturn();
            });
        });
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
        cb.withMethodBody(name, MethodTypeDesc.of(returnIface),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, code -> {
                    code.new_(impl);
                    code.dup();
                    code.invokespecial(impl, "<init>", MTD_void);
                    code.areturn();
                });
    }

    // --- $Dec class ---

    private byte[] generateDecoderClass(ClassDesc cdName, Ast.Data data, Ast.DecoderDef dec,
                                        Map<String, Type> fields) {
        ClassDesc cdDec = cd(data.name() + "$Dec");
        return CF.build(cdDec, cb -> {
            cb.withFlags(ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            cb.withInterfaceSymbols(CD_RDecoder);
            emitDefaultCtor(cb);
            // Raoh Decoder SAM: decode(Object in, Path path) -> Result. this=0, in=1, path=2.
            cb.withMethodBody("decode", MTD_Rdecode, ClassFile.ACC_PUBLIC, code -> {
                Gen gen = new Gen(code, data, cdName, 3);
                switch (dec) {
                    case Ast.PrimDecoder prim -> emitPrimDecode(code, gen, cdName, prim, fields);
                    case Ast.ObjectDecoder obj -> emitObjectDecode(code, gen, cdName, obj, fields);
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
            return data.decoder().map(d -> d instanceof Ast.ObjectDecoder).orElse(false);
        }
        return false;
    }

    /** Pushes a Raoh leaf {@code Decoder} for a primitive value read from a bare Object. */
    private void emitLeafDecoder(CodeBuilder code, Ast.PrimKind kind) {
        switch (kind) {
            case STRING -> code.invokestatic(CD_ObjectDecoders, "string", MTD_leafString);
            case INT -> code.invokestatic(CD_ObjectDecoders, "long_", MTD_leafLong);
            case BOOL -> code.invokestatic(CD_ObjectDecoders, "bool", MTD_leafBool);
            case DECIMAL -> code.invokestatic(CD_ObjectDecoders, "decimal", MTD_leafDecimal);
            case DATE -> code.invokestatic(CD_ObjectDecoders, "date", MTD_leafTemporal);
            case DATETIME -> code.invokestatic(CD_ObjectDecoders, "dateTime", MTD_leafTemporal);
        }
    }

    private void emitPrimDecode(CodeBuilder code, Gen gen, ClassDesc cdName, Ast.PrimDecoder prim,
                                Map<String, Type> fields) {
        Type inputType = TypeChecker.primType(prim.from());
        switch (prim.from()) {
            case TEXT -> code.invokestatic(CD_ObjectDecoders, "string", MTD_leafString);
            case INT -> code.invokestatic(CD_ObjectDecoders, "long_", MTD_leafLong);
            case BOOL -> code.invokestatic(CD_ObjectDecoders, "bool", MTD_leafBool);
            case DECIMAL -> code.invokestatic(CD_ObjectDecoders, "decimal", MTD_leafDecimal);
            case DATE -> code.invokestatic(CD_ObjectDecoders, "date", MTD_leafTemporal);
            case DATETIME -> code.invokestatic(CD_ObjectDecoders, "dateTime", MTD_leafTemporal);
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

    private void emitObjectDecode(CodeBuilder code, Gen gen, ClassDesc cdName, Ast.ObjectDecoder obj,
                                  Map<String, Type> fields) {
        List<Ast.Bind> binds = obj.binds();
        int[] resultSlots = new int[binds.size()];
        for (int i = 0; i < binds.size(); i++) {
            Ast.Bind bind = binds.get(i);
            code.loadConstant(bind.key());
            if (bind.ref() instanceof Ast.OptionDecRef opt) {
                emitDecoderObject(code, opt.element());
                code.invokestatic(CD_MapDecoders, "optionalField", MTD_optionalField);
            } else {
                emitDecoderObject(code, bind.ref());
                code.invokestatic(CD_MapDecoders, "field", MTD_field);
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

    /** Pushes a {@code Decoder} object for the given bind reference onto the stack. */
    private void emitDecoderObject(CodeBuilder code, Ast.DecRef ref) {
        switch (ref) {
            case Ast.PrimDecRef p -> emitLeafDecoder(code, p.kind());
            case Ast.DataDecRef d -> {
                invokeCodec(code, d.typeName(), "decoder", MTD_Rdecoder);
                if (isMapInput(d.typeName())) {
                    code.invokestatic(CD_MapDecoders, "nested", MTD_nested);   // Decoder<Map> -> Decoder<Object>
                }
            }
            case Ast.ListDecRef l -> {
                emitDecoderObject(code, l.element());
                code.invokestatic(CD_ObjectDecoders, "list", MTD_listDec);
            }
            case Ast.OptionDecRef o -> throw new CompileException(o.pos(),
                    "optional is only supported as a direct object field");
            case Ast.MapDecRef mp -> {
                emitDecoderObject(code, mp.value());
                code.invokestatic(CD_ObjectDecoders, "map", MTD_mapDec);
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
        return CF.build(cdEnc, cb -> {
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

    /** Pushes a Raoh {@link net.unit8.raoh.encode.Encoder} for a list/map element. */
    private void pushElemEncoder(CodeBuilder code, Ast.EncElem elem) {
        switch (elem) {
            case Ast.PrimEnc p -> code.invokestatic(CD_ObjectEncoders,
                    p.kind() == Ast.PrimKind.STRING ? "string" : "long_", MTD_Rencode_leaf);
            case Ast.DataEnc d -> invokeCodec(code, d.typeName(), "encoder", MTD_Rencoder);
        }
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
                case Ast.IntLit lit -> {
                    code.loadConstant(lit.value());
                    yield Type.INT;
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
                    expr(call.args().get(0));
                    code.invokevirtual(CD_String, "length", MethodTypeDesc.of(ConstantDescs.CD_int));
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
                case "size" -> {
                    expr(call.args().get(0));
                    code.invokeinterface(CD_List, "size", MTD_size);
                    code.i2l();
                    return Type.INT;
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
                        switch (call.fn()) {
                            case "add" -> code.ladd();
                            case "subtract" -> code.lsub();
                            default -> code.lmul();
                        }
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
                case "divide", "remainder" -> {
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
                    if (call.fn().equals("divide")) {
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
                default -> {
                    if (reqNames.contains(call.fn())) {
                        return requiredCall(call);
                    }
                    throw new CompileException(call.pos(), "unknown function `" + call.fn() + "`");
                }
            }
        }

        /** Emits an inline call to an injected required behavior, leaving its success value on
         * the stack cast to the success type (spec 12.2, 13). */
        private Type requiredCall(Ast.Call call) {
            code.aload(0);
            code.getfield(cdName, call.fn(), CD_Behavior);
            Type at = expr(call.args().get(0));
            box(code, at);
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
                case ADD -> {
                    expr(bin.left());
                    expr(bin.right());
                    code.ladd();
                    return Type.INT;
                }
                case SUB -> {
                    expr(bin.left());
                    expr(bin.right());
                    code.lsub();
                    return Type.INT;
                }
                case MUL -> {
                    expr(bin.left());
                    expr(bin.right());
                    code.lmul();
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
        if (type == Type.RAW) return CD_Raw;
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
