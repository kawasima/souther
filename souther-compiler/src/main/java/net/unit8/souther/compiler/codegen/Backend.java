package net.unit8.souther.compiler.codegen;

import net.unit8.souther.compiler.CompileException;
import net.unit8.souther.compiler.ast.Ast;
import net.unit8.souther.compiler.check.Type;
import net.unit8.souther.compiler.check.TypeChecker;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
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
    private static final ClassDesc CD_LinkedHashMap = ClassDesc.of("java.util.LinkedHashMap");
    private static final ClassDesc CD_Raw = ClassDesc.of("net.unit8.souther.runtime.Raw");
    private static final ClassDesc CD_Result = ClassDesc.of("net.unit8.souther.runtime.Result");
    private static final ClassDesc CD_ResultOk = CD_Result.nested("Ok");
    private static final ClassDesc CD_ResultErr = CD_Result.nested("Err");
    private static final ClassDesc CD_NonEmptyList = ClassDesc.of("net.unit8.souther.runtime.NonEmptyList");
    private static final ClassDesc CD_Decoder = ClassDesc.of("net.unit8.souther.runtime.Decoder");
    private static final ClassDesc CD_Encoder = ClassDesc.of("net.unit8.souther.runtime.Encoder");
    private static final ClassDesc CD_Decoders = ClassDesc.of("net.unit8.souther.runtime.Decoders");
    private static final ClassDesc CD_Behavior = ClassDesc.of("net.unit8.souther.runtime.Behavior");
    private static final ClassDesc CD_Boolean = ClassDesc.of("java.lang.Boolean");
    private static final ClassDesc CD_IllegalStateException = ClassDesc.of("java.lang.IllegalStateException");

    private static final MethodTypeDesc MTD_void = MethodTypeDesc.of(ConstantDescs.CD_void);
    private static final MethodTypeDesc MTD_Result_Raw = MethodTypeDesc.of(CD_Result, CD_Raw);
    private static final MethodTypeDesc MTD_Result_Object = MethodTypeDesc.of(CD_Result, CD_Object);
    private static final MethodTypeDesc MTD_Result_String_String =
            MethodTypeDesc.of(CD_Result, CD_String, CD_String);
    private static final MethodTypeDesc MTD_Object = MethodTypeDesc.of(CD_Object);
    private static final MethodTypeDesc MTD_field = MethodTypeDesc.of(CD_Result, CD_Raw, CD_String);
    private static final MethodTypeDesc MTD_objectField =
            MethodTypeDesc.of(CD_Result, CD_Raw, CD_String, CD_Decoder);
    private static final MethodTypeDesc MTD_decoder = MethodTypeDesc.of(CD_Decoder);
    private static final MethodTypeDesc MTD_encoder = MethodTypeDesc.of(CD_Encoder);
    private static final MethodTypeDesc MTD_encode = MethodTypeDesc.of(CD_Raw, CD_Object);
    private static final MethodTypeDesc MTD_mergeErrors =
            MethodTypeDesc.of(CD_NonEmptyList, CD_Result.arrayType());
    private static final MethodTypeDesc MTD_Map_put = MethodTypeDesc.of(CD_Object, CD_Object, CD_Object);
    private static final MethodTypeDesc MTD_Raw_object = MethodTypeDesc.of(CD_Raw, CD_Map);
    private static final MethodTypeDesc MTD_variant =
            MethodTypeDesc.of(CD_Result, CD_Raw, CD_String, CD_String, CD_Decoder);
    private static final MethodTypeDesc MTD_noVariant = MethodTypeDesc.of(CD_Result, CD_String);
    private static final MethodTypeDesc MTD_apply = MethodTypeDesc.of(CD_Result, CD_Object);
    private static final MethodTypeDesc MTD_Long_valueOf = MethodTypeDesc.of(CD_Long, ConstantDescs.CD_long);
    private static final MethodTypeDesc MTD_Boolean_valueOf =
            MethodTypeDesc.of(CD_Boolean, ConstantDescs.CD_boolean);

    private final String pkg;
    private final Map<String, Ast.Def> symbols;
    private final Map<String, List<String>> armToSums;

    private Backend(String pkg, Map<String, Ast.Def> symbols, Map<String, List<String>> armToSums) {
        this.pkg = pkg;
        this.symbols = symbols;
        this.armToSums = armToSums;
    }

    public static Map<String, byte[]> generate(Ast.Module module) {
        Map<String, List<String>> armToSums = new HashMap<>();
        for (Ast.Def def : module.defs()) {
            if (def instanceof Ast.SumData sum) {
                for (String arm : sum.arms()) {
                    armToSums.computeIfAbsent(arm, k -> new ArrayList<>()).add(sum.name());
                }
            }
        }
        Backend b = new Backend(module.name(), TypeChecker.symbols(module), armToSums);
        Map<String, byte[]> out = new LinkedHashMap<>();
        for (Ast.Def def : module.defs()) {
            switch (def) {
                case Ast.Data data -> b.generateData(data, out);
                case Ast.SumData sum -> b.generateSum(sum, out);
                case Ast.UnitData unit -> b.generateUnit(unit, out);
            }
        }
        Set<String> requiredNames = new HashSet<>();
        for (Ast.RequiredBehavior r : module.requireds()) {
            requiredNames.add(r.name());
        }
        for (Ast.BehaviorDef bd : module.behaviors()) {
            switch (bd) {
                case Ast.BodyBehavior body ->
                        out.put(module.name() + "." + body.name(), b.generateBodyBehavior(body));
                case Ast.PipeBehavior pipe ->
                        out.put(module.name() + "." + pipe.name(), b.generatePipe(pipe, requiredNames));
            }
        }
        return out;
    }

    private Type resolveType(Ast.TypeRef ref) {
        return TypeChecker.resolveType(ref, symbols);
    }

    private ClassDesc[] armInterfaces(String name) {
        List<ClassDesc> ifaces = new ArrayList<>();
        for (String sum : armToSums.getOrDefault(name, List.of())) {
            ifaces.add(cd(sum));
        }
        return ifaces.toArray(new ClassDesc[0]);
    }

    private Map<String, Type> fieldTypes(Ast.Data data) {
        return TypeChecker.fieldTypes(data, symbols);
    }

    private ClassDesc cd(String typeName) {
        return ClassDesc.of(pkg + "." + typeName);
    }

    private void generateData(Ast.Data data, Map<String, byte[]> out) {
        ClassDesc cdName = cd(data.name());
        Map<String, Type> fields = fieldTypes(data);

        out.put(pkg + "." + data.name(), ClassFile.of().build(cdName, cb -> {
            cb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            ClassDesc[] ifaces = armInterfaces(data.name());
            if (ifaces.length > 0) {
                cb.withInterfaceSymbols(ifaces);
            }
            for (Map.Entry<String, Type> f : fields.entrySet()) {
                cb.withField(f.getKey(), jvmType(f.getValue()), ClassFile.ACC_FINAL);
            }
            emitCtor(cb, cdName, fields);
            emitConstructMethod(cb, cdName, data, fields);
            data.decoder().ifPresent(d -> emitFactory(cb, "decoder", CD_Decoder, data, "$Dec"));
            data.encoder().ifPresent(e -> emitFactory(cb, "encoder", CD_Encoder, data, "$Enc"));
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
        out.put(pkg + "." + sum.name(), ClassFile.of().build(cdX, cb -> {
            cb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_INTERFACE | ClassFile.ACC_ABSTRACT);
            cb.with(PermittedSubclassesAttribute.ofSymbols(armCds));
            sum.decoder().ifPresent(disc -> {
                ClassDesc cdDec = cd(sum.name() + "$Dec");
                cb.withMethodBody("decoder", MTD_decoder,
                        ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, code -> {
                            code.new_(cdDec);
                            code.dup();
                            code.invokespecial(cdDec, "<init>", MTD_void);
                            code.areturn();
                        });
            });
        }));
        sum.decoder().ifPresent(disc ->
                out.put(pkg + "." + sum.name() + "$Dec", generateSumDecoder(sum, disc)));
    }

    private byte[] generateSumDecoder(Ast.SumData sum, Ast.Discriminate disc) {
        ClassDesc cdDec = cd(sum.name() + "$Dec");
        return ClassFile.of().build(cdDec, cb -> {
            cb.withFlags(ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            cb.withInterfaceSymbols(CD_Decoder);
            emitDefaultCtor(cb);
            cb.withMethodBody("decode", MTD_Result_Raw, ClassFile.ACC_PUBLIC, code -> {
                for (Ast.Variant v : disc.variants()) {
                    code.aload(1);
                    code.loadConstant(disc.key());
                    code.loadConstant(v.tag());
                    code.invokestatic(cd(v.armType()), "decoder", MTD_decoder);
                    code.invokestatic(CD_Decoders, "variant", MTD_variant);
                    code.astore(2);
                    code.aload(2);
                    Label next = code.newLabel();
                    code.ifnull(next);
                    code.aload(2);
                    code.areturn();
                    code.labelBinding(next);
                }
                code.loadConstant(disc.key());
                code.invokestatic(CD_Decoders, "noVariant", MTD_noVariant);
                code.areturn();
            });
        });
    }

    private void generateUnit(Ast.UnitData unit, Map<String, byte[]> out) {
        ClassDesc cdU = cd(unit.name());
        out.put(pkg + "." + unit.name(), ClassFile.of().build(cdU, cb -> {
            cb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            ClassDesc[] ifaces = armInterfaces(unit.name());
            if (ifaces.length > 0) {
                cb.withInterfaceSymbols(ifaces);
            }
            emitDefaultCtor(cb);
        }));
    }

    // --- behaviors ---

    private byte[] generateBodyBehavior(Ast.BodyBehavior b) {
        ClassDesc cdB = cd(b.name());
        Type paramType = resolveType(b.paramType());
        return ClassFile.of().build(cdB, cb -> {
            cb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            cb.withInterfaceSymbols(CD_Behavior);
            emitPublicCtor(cb);
            cb.withMethodBody("apply", MTD_apply, ClassFile.ACC_PUBLIC, code -> {
                Gen gen = new Gen(code, null, cdB, 2);
                code.aload(1);
                int pslot = gen.slot(paramType);
                unbox(code, paramType, pslot);
                gen.bind(b.paramName(), pslot, paramType);
                for (Ast.BStmt stmt : b.stmts()) {
                    switch (stmt) {
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
                            gen.expr(guard.failure());
                            code.invokestatic(CD_Result, "err", MTD_Result_Object, true);
                            code.areturn();
                            code.labelBinding(cont);
                        }
                    }
                }
                Type rt = gen.expr(b.result());
                box(code, rt);
                code.invokestatic(CD_Result, "ok", MTD_Result_Object, true);
                code.areturn();
            });
        });
    }

    private byte[] generatePipe(Ast.PipeBehavior pipe, Set<String> requiredNames) {
        ClassDesc cdP = cd(pipe.name());
        // required stages become injected fields, in first-seen order
        List<String> reqStages = new ArrayList<>(new LinkedHashSet<>(
                pipe.stages().stream().filter(requiredNames::contains).toList()));

        return ClassFile.of().build(cdP, cb -> {
            cb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            cb.withInterfaceSymbols(CD_Behavior);
            for (String req : reqStages) {
                cb.withField(req, CD_Behavior, ClassFile.ACC_FINAL);
            }

            // constructor(Behavior... injected required behaviors)
            ClassDesc[] ctorParams = new ClassDesc[reqStages.size()];
            for (int i = 0; i < reqStages.size(); i++) {
                ctorParams[i] = CD_Behavior;
            }
            cb.withMethodBody("<init>", MethodTypeDesc.of(ConstantDescs.CD_void, ctorParams),
                    ClassFile.ACC_PUBLIC, code -> {
                        code.aload(0);
                        code.invokespecial(CD_Object, "<init>", MTD_void);
                        for (int i = 0; i < reqStages.size(); i++) {
                            code.aload(0);
                            code.aload(i + 1);
                            code.putfield(cdP, reqStages.get(i), CD_Behavior);
                        }
                        code.return_();
                    });

            cb.withMethodBody("apply", MTD_apply, ClassFile.ACC_PUBLIC, code -> {
                // slot 1 holds the running success value; slot 2 the last stage's Result.
                List<String> stages = pipe.stages();
                for (int i = 0; i < stages.size(); i++) {
                    String stage = stages.get(i);
                    if (requiredNames.contains(stage)) {
                        code.aload(0);
                        code.getfield(cdP, stage, CD_Behavior);
                    } else {
                        ClassDesc cdStage = cd(stage);
                        code.new_(cdStage);
                        code.dup();
                        code.invokespecial(cdStage, "<init>", MTD_void);
                    }
                    code.aload(1);
                    code.invokeinterface(CD_Behavior, "apply", MTD_apply);
                    code.astore(2);
                    // short-circuit on failure
                    code.aload(2);
                    code.instanceOf(CD_ResultErr);
                    Label ok = code.newLabel();
                    code.ifeq(ok);
                    code.aload(2);
                    code.areturn();
                    code.labelBinding(ok);
                    if (i < stages.size() - 1) {
                        code.aload(2);
                        code.checkcast(CD_ResultOk);
                        code.invokevirtual(CD_ResultOk, "value", MTD_Object);
                        code.astore(1);
                    }
                }
                code.aload(2);
                code.areturn();
            });
        });
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

                    data.invariant().ifPresent(inv -> {
                        gen.expr(inv);
                        Label ok = code.newLabel();
                        code.ifne(ok);
                        code.loadConstant("invariant_violation");
                        code.loadConstant("invariant violated on " + data.name());
                        code.invokestatic(CD_Decoders, "fail", MTD_Result_String_String);
                        code.areturn();
                        code.labelBinding(ok);
                    });

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
        return ClassFile.of().build(cdDec, cb -> {
            cb.withFlags(ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            cb.withInterfaceSymbols(CD_Decoder);
            emitDefaultCtor(cb);
            cb.withMethodBody("decode", MTD_Result_Raw, ClassFile.ACC_PUBLIC, code -> {
                Gen gen = new Gen(code, data, cdName, 2);
                switch (dec) {
                    case Ast.PrimDecoder prim -> emitPrimDecode(code, gen, cdName, prim, fields);
                    case Ast.ObjectDecoder obj -> emitObjectDecode(code, gen, cdName, obj, fields);
                }
            });
        });
    }

    private void emitPrimDecode(CodeBuilder code, Gen gen, ClassDesc cdName, Ast.PrimDecoder prim,
                                Map<String, Type> fields) {
        Type inputType = prim.from() == Ast.RawKind.TEXT ? Type.STRING : Type.INT;
        String readMethod = prim.from() == Ast.RawKind.TEXT ? "text" : "integer";

        code.aload(1);
        code.invokestatic(CD_Decoders, readMethod, MTD_Result_Raw);
        int rSlot = gen.slot(Type.STRING);
        code.astore(rSlot);
        code.aload(rSlot);
        code.instanceOf(CD_ResultErr);
        Label notErr = code.newLabel();
        code.ifeq(notErr);
        code.aload(rSlot);
        code.areturn();
        code.labelBinding(notErr);
        code.aload(rSlot);
        code.checkcast(CD_ResultOk);
        code.invokevirtual(CD_ResultOk, "value", MTD_Object);
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
            code.aload(1);
            code.loadConstant(bind.key());
            switch (bind.ref()) {
                case Ast.PrimDecRef p -> {
                    String reader = p.kind() == Ast.PrimKind.STRING ? "stringField" : "longField";
                    code.invokestatic(CD_Decoders, reader, MTD_field);
                }
                case Ast.DataDecRef d -> {
                    code.invokestatic(cd(d.typeName()), "decoder", MTD_decoder);
                    code.invokestatic(CD_Decoders, "objectField", MTD_objectField);
                }
            }
            int rSlot = gen.slot(Type.STRING);
            code.astore(rSlot);
            resultSlots[i] = rSlot;
        }

        pushInt(code, binds.size());
        code.anewarray(CD_Result);
        for (int i = 0; i < binds.size(); i++) {
            code.dup();
            pushInt(code, i);
            code.aload(resultSlots[i]);
            code.aastore();
        }
        code.invokestatic(CD_Decoders, "mergeErrors", MTD_mergeErrors);
        int errSlot = gen.slot(Type.STRING);
        code.astore(errSlot);
        code.aload(errSlot);
        Label ok = code.newLabel();
        code.ifnull(ok);
        code.aload(errSlot);
        code.invokestatic(CD_Result, "err", MTD_Result_Object, true);
        code.areturn();
        code.labelBinding(ok);

        for (int i = 0; i < binds.size(); i++) {
            Ast.Bind bind = binds.get(i);
            Type t = bindType(bind.ref());
            code.aload(resultSlots[i]);
            code.checkcast(CD_ResultOk);
            code.invokevirtual(CD_ResultOk, "value", MTD_Object);
            int vSlot = gen.slot(t);
            unbox(code, t, vSlot);
            gen.bind(bind.name(), vSlot, t);
        }
        emitConstructCall(code, gen, cdName, obj.result(), fields);
    }

    private Type bindType(Ast.DecRef ref) {
        return switch (ref) {
            case Ast.PrimDecRef p -> p.kind() == Ast.PrimKind.STRING ? Type.STRING : Type.INT;
            case Ast.DataDecRef d -> Type.ref(d.typeName());
        };
    }

    private void emitRequire(CodeBuilder code, Gen gen, Ast.Require req) {
        gen.expr(req.cond());
        Label cont = code.newLabel();
        code.ifne(cont);
        code.loadConstant(req.errorCode());
        code.loadConstant(req.errorCode());
        code.invokestatic(CD_Decoders, "fail", MTD_Result_String_String);
        code.areturn();
        code.labelBinding(cont);
    }

    private void emitConstructCall(CodeBuilder code, Gen gen, ClassDesc cdName, Ast.Construct construct,
                                   Map<String, Type> fields) {
        Map<String, Ast.FieldInit> byName = new HashMap<>();
        for (Ast.FieldInit init : construct.inits()) {
            byName.put(init.name(), init);
        }
        for (String field : fields.keySet()) {
            gen.expr(byName.get(field).value());
        }
        code.invokestatic(cdName, "__construct", MethodTypeDesc.of(CD_Result, fieldDescs(fields)));
        code.areturn();
    }

    // --- $Enc class ---

    private byte[] generateEncoderClass(ClassDesc cdName, Ast.Data data, Ast.EncoderDef enc) {
        ClassDesc cdEnc = cd(data.name() + "$Enc");
        return ClassFile.of().build(cdEnc, cb -> {
            cb.withFlags(ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            cb.withInterfaceSymbols(CD_Encoder);
            emitDefaultCtor(cb);
            cb.withMethodBody("encode", MTD_encode, ClassFile.ACC_PUBLIC, code -> {
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
            case Ast.TextRaw t -> {
                gen.expr(t.arg());
                code.invokestatic(CD_Raw, "text", MethodTypeDesc.of(CD_Raw, CD_String), true);
            }
            case Ast.IntRaw i -> {
                gen.expr(i.arg());
                code.invokestatic(CD_Raw, "integer",
                        MethodTypeDesc.of(CD_Raw, ConstantDescs.CD_long), true);
            }
            case Ast.EncodeRaw e -> {
                code.invokestatic(cd(e.typeName()), "encoder", MTD_encoder);
                gen.expr(e.arg());
                code.invokeinterface(CD_Encoder, "encode", MTD_encode);
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
                code.invokestatic(CD_Raw, "object", MTD_Raw_object, true);
            }
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

        Gen(CodeBuilder code, Ast.Data data, ClassDesc cdName, int firstSlot) {
            this.code = code;
            this.data = data;
            this.cdName = cdName;
            this.nextSlot = firstSlot;
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
                    if (var == null) {
                        throw new CompileException(v.pos(), "unbound identifier `" + v.name() + "`");
                    }
                    load(code, var.slot(), var.type());
                    yield var.type();
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
            };
        }

        private Type match(Ast.Match m) {
            Type st = expr(m.scrutinee());
            int sSlot = slot(st);
            store(code, sSlot, st);
            Label end = code.newLabel();
            Type branchType = null;
            for (Ast.Case c : m.cases()) {
                ClassDesc armCd = cd(c.armType());
                code.aload(sSlot);
                code.instanceOf(armCd);
                Label nextCase = code.newLabel();
                code.ifeq(nextCase);
                code.aload(sSlot);
                code.checkcast(armCd);
                int bslot = slot(Type.ref(c.armType()));
                code.astore(bslot);
                bind(c.binding(), bslot, Type.ref(c.armType()));
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
            Map<String, Ast.FieldInit> byName = new HashMap<>();
            for (Ast.FieldInit init : nd.inits()) {
                byName.put(init.name(), init);
            }
            code.new_(cdType);
            code.dup();
            for (String field : flds.keySet()) {
                expr(byName.get(field).value());
            }
            code.invokespecial(cdType, "<init>",
                    MethodTypeDesc.of(ConstantDescs.CD_void, fieldDescs(flds)));
            return Type.ref(nd.typeName());
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
                default -> throw new CompileException(call.pos(), "unknown function `" + call.fn() + "`");
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
        } else {
            code.checkcast(jvmType(type));
            code.astore(slot);
        }
    }

    private ClassDesc jvmType(Type type) {
        if (type == Type.INT) return ConstantDescs.CD_long;
        if (type == Type.STRING) return CD_String;
        if (type == Type.BOOL) return ConstantDescs.CD_boolean;
        return cd(((Type.Ref) type).name());
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
