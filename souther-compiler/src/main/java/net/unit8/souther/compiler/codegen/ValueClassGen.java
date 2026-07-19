package net.unit8.souther.compiler.codegen;

import net.unit8.souther.compiler.ast.Ast;
import net.unit8.souther.compiler.check.Type;
import net.unit8.souther.compiler.check.TypeChecker;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.Label;
import java.lang.classfile.attribute.PermittedSubclassesAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static net.unit8.souther.compiler.codegen.Descriptors.*;
import static net.unit8.souther.compiler.codegen.JvmTypes.*;

/**
 * Generates a data/sum/unit value class: its fields, package-private constructor, accessors, value
 * equality/hashCode, and the invariant-checking {@code __construct} (spec 7, 19). Each type's codecs
 * are emitted by the {@link CodecGen} it holds; body expressions through a {@link BodyGen} built per
 * method.
 */
final class ValueClassGen {

    private final CodegenContext ctx;
    private final String pkg;
    private final Map<String, Ast.Def> symbols;
    private final CodecGen codec;

    ValueClassGen(CodegenContext ctx, CodecGen codec) {
        this.ctx = ctx;
        this.pkg = ctx.pkg;
        this.symbols = ctx.symbols;
        this.codec = codec;
    }

    private ClassDesc cd(String typeName) { return ctx.cd(typeName); }
    private ClassDesc[] caseInterfaces(String name) { return ctx.caseInterfaces(name); }
    private Map<String, Type> fieldTypes(Ast.Data data) { return ctx.fieldTypes(data); }
    private int pub(String name) { return ctx.pub(name); }
    private ClassDesc jvmType(Type type) { return JvmTypes.jvmType(type, ctx); }
    private ClassDesc[] fieldDescs(Map<String, Type> fields) { return JvmTypes.fieldDescs(fields, ctx); }

    void generateData(Ast.Data data, Map<String, byte[]> out) {
        ClassDesc cdName = cd(data.name());
        Map<String, Type> fields = fieldTypes(data);

        out.put(pkg + "." + data.name(), build(cdName, cb -> {
            cb.withFlags(pub(data.name()) | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            ClassDesc[] ifaces = caseInterfaces(data.name());
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
            // A String-backed newtype always exposes its bare `value()`: the encoder reads it to
            // render a newtype-keyed map's keys bare, even when the newtype itself is unexposed.
            if (pub(data.name()) != 0 || isStringBackedNewtype(data, fields)) {
                emitAccessors(cb, cdName, fields);
            }
            data.decoder().ifPresent(d -> {
                boolean mapInput = codec.isMapInput(data.name());
                codec.emitFactory(cb, "decoder", CD_RDecoder, data, "$Dec");
                if (codec.jsonCompatible(data.name())) codec.emitSourceFactory(cb, data.name(), CodecGen.Src.JSON, mapInput);
                if (codec.recordCompatible(data.name())) codec.emitSourceFactory(cb, data.name(), CodecGen.Src.JOOQ, mapInput);
            });
            data.encoder().ifPresent(e -> codec.emitFactory(cb, "encoder", CD_REncoder, data, "$Enc"));
        }));

        data.decoder().ifPresent(dec -> {
            out.put(pkg + "." + data.name() + "$Dec",
                    codec.generateDecoderClass(cdName, data, dec, fields, CodecGen.Src.NEUTRAL));
            if (codec.jsonCompatible(data.name())) {
                out.put(pkg + "." + data.name() + "$DecJson",
                        codec.generateDecoderClass(cdName, data, dec, fields, CodecGen.Src.JSON));
            }
            if (codec.recordCompatible(data.name())) {
                out.put(pkg + "." + data.name() + "$DecRecord",
                        codec.generateDecoderClass(cdName, data, dec, fields, CodecGen.Src.JOOQ));
            }
        });
        data.encoder().ifPresent(enc ->
                out.put(pkg + "." + data.name() + "$Enc", codec.generateEncoderClass(cdName, data, enc)));

        // A CTFE helper for an invariant-bearing newtype: a Raoh-free `boolean check(value)` that
        // runs the same invariant bytecode as __construct (via gen.expr), so a constant construction
        // can be verified at compile time — 金額(-5) is a compile error, not a runtime abort (ADR-0032).
        if (data.newtype() && !TypeChecker.effectiveInvariants(data, symbols).isEmpty()) {
            emitCtfeCheck(data, fields, out);
        }
    }

    private void emitCtfeCheck(Ast.Data data, Map<String, Type> fields, Map<String, byte[]> out) {
        ClassDesc cdName = cd(data.name());
        ClassDesc cdCtfe = cd(data.name() + "$Ctfe");
        out.put(pkg + "." + data.name() + "$Ctfe", build(cdCtfe, cb -> {
            cb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            cb.withMethodBody("check", MethodTypeDesc.of(ConstantDescs.CD_boolean, fieldDescs(fields)),
                    ClassFile.ACC_STATIC | ClassFile.ACC_PUBLIC, code -> {
                        BodyGen gen = new BodyGen(ctx, code, data, cdName, 0);
                        int slot = 0;
                        for (Map.Entry<String, Type> f : fields.entrySet()) {
                            gen.bind(f.getKey(), slot, f.getValue());
                            slot += width(f.getValue());
                        }
                        for (Ast.Expr inv : TypeChecker.effectiveInvariants(data, symbols)) {
                            gen.expr(inv);                 // the same boolean __construct checks
                            Label ok = code.newLabel();
                            code.ifne(ok);
                            code.iconst_0();
                            code.ireturn();                // an invariant is false
                            code.labelBinding(ok);
                        }
                        code.iconst_1();
                        code.ireturn();                    // all held
                    });
        }));
    }

    void generateSum(Ast.SumData sum, Map<String, byte[]> out) {
        ClassDesc cdX = cd(sum.name());
        List<ClassDesc> caseCds = new ArrayList<>();
        for (String caseName : sum.cases()) {
            caseCds.add(cd(caseName));
        }
        out.put(pkg + "." + sum.name(), build(cdX, cb -> {
            cb.withFlags(pub(sum.name()) | ClassFile.ACC_INTERFACE | ClassFile.ACC_ABSTRACT);
            cb.with(PermittedSubclassesAttribute.ofSymbols(caseCds));
            sum.decoder().ifPresent(disc -> {
                codec.emitCodecFactory(cb, "decoder", CD_RDecoder, cd(sum.name() + "$Dec"), codec.decoderSig(cdX, true));
                if (codec.jsonCompatible(sum.name())) codec.emitSourceFactory(cb, sum.name(), CodecGen.Src.JSON, true);
                if (codec.recordCompatible(sum.name())) codec.emitSourceFactory(cb, sum.name(), CodecGen.Src.JOOQ, true);
            });
            sum.encoder().ifPresent(enc ->
                    codec.emitCodecFactory(cb, "encoder", CD_REncoder, cd(sum.name() + "$Enc"),
                            codec.encoderSig(cdX, CD_Map)));
        }));
        sum.decoder().ifPresent(disc -> {
            out.put(pkg + "." + sum.name() + "$Dec", codec.generateSumDecoder(sum, disc, CodecGen.Src.NEUTRAL));
            if (codec.jsonCompatible(sum.name())) {
                out.put(pkg + "." + sum.name() + "$DecJson", codec.generateSumDecoder(sum, disc, CodecGen.Src.JSON));
            }
            if (codec.recordCompatible(sum.name())) {
                out.put(pkg + "." + sum.name() + "$DecRecord", codec.generateSumDecoder(sum, disc, CodecGen.Src.JOOQ));
            }
        });
        sum.encoder().ifPresent(enc ->
                out.put(pkg + "." + sum.name() + "$Enc", codec.generateSumEncoder(sum, enc)));
    }

    void generateUnit(Ast.UnitData unit, Map<String, byte[]> out) {
        ClassDesc cdU = cd(unit.name());
        ClassDesc cdDec = cd(unit.name() + "$Dec");
        ClassDesc cdEnc = cd(unit.name() + "$Enc");
        out.put(pkg + "." + unit.name(), build(cdU, cb -> {
            cb.withFlags(pub(unit.name()) | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            ClassDesc[] ifaces = caseInterfaces(unit.name());
            if (ifaces.length > 0) {
                cb.withInterfaceSymbols(ifaces);
            }
            emitDefaultCtor(cb);
            emitValueEquality(cb, cdU, Map.of());   // all units of a type are the same value
            // a unit is a field-less data: its codec reads/writes nothing but the tag the sum adds
            // A unit ignores its input, so it decodes from every source. Generate all three so
            // unit cases of a JSON/record sum have a matching decoder to dispatch to.
            codec.emitCodecFactory(cb, "decoder", CD_RDecoder, cdDec, codec.decoderSig(cdU, false));
            codec.emitCodecFactory(cb, "jsonDecoder", CD_RDecoder, cd(unit.name() + "$DecJson"),
                    codec.decoderSigFor(CodecGen.Src.JSON, cdU, false));
            codec.emitCodecFactory(cb, "recordDecoder", CD_RDecoder, cd(unit.name() + "$DecRecord"),
                    codec.decoderSigFor(CodecGen.Src.JOOQ, cdU, false));
            codec.emitCodecFactory(cb, "encoder", CD_REncoder, cdEnc, codec.encoderSig(cdU, CD_Map));
        }));
        out.put(pkg + "." + unit.name() + "$Dec", codec.generateUnitDecoder(cdU, cdDec));
        out.put(pkg + "." + unit.name() + "$DecJson", codec.generateUnitDecoder(cdU, cd(unit.name() + "$DecJson")));
        out.put(pkg + "." + unit.name() + "$DecRecord", codec.generateUnitDecoder(cdU, cd(unit.name() + "$DecRecord")));
        out.put(pkg + "." + unit.name() + "$Enc", codec.generateUnitEncoder(cdEnc));
    }

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
    /** A newtype over a single {@code String} field ({@code data X = String}) — the shape a Map key
     * may take, whose bare {@code value()} the boundary encoder renders. */
    private static boolean isStringBackedNewtype(Ast.Data data, Map<String, Type> fields) {
        return data.newtype() && fields.size() == 1 && fields.values().iterator().next() == Type.STRING;
    }

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

    /**
     * Reads a field onto the stack. A local data's field is private but same-package, so a direct
     * {@code getfield} works; an imported data's field is private across the module = package
     * boundary, so the read goes through the public accessor the exposed data generates (spec 8.5,
     * 19.2).
     */
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
                    BodyGen gen = new BodyGen(ctx, code, data, cdName, 0);
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
}
