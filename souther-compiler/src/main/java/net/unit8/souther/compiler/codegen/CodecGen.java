package net.unit8.souther.compiler.codegen;

import net.unit8.souther.compiler.diag.CompileException;
import net.unit8.souther.compiler.ast.Ast;
import net.unit8.souther.compiler.check.Type;
import net.unit8.souther.compiler.check.TypeChecker;
import net.unit8.souther.compiler.core.Core;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.MethodSignature;
import java.lang.classfile.attribute.SignatureAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.unit8.souther.compiler.codegen.Descriptors.*;
import static net.unit8.souther.compiler.codegen.JvmTypes.*;

/**
 * Generates a data/sum/unit type's decoders and encoders at the Raoh boundary (spec 10.6, 15, 27.7):
 * the three input sources (neutral/JSON/jOOQ), object/leaf/newtype/sum decoding, the require and
 * construct checks, and the encoder raw expressions. Name resolution and the synthetic-class sink come
 * from {@link CodegenContext}; body expressions are emitted through a {@link BodyGen} built per method.
 */
final class CodecGen {

    private final CodegenContext ctx;
    private final Map<String, Ast.Def> symbols;
    /** The $Dec class currently being generated — the owner of the {@code __rekey} helpers a
     * newtype-keyed map decoder references. Set per {@link #generateDecoderClass}. */
    private ClassDesc decoderClass;

    CodecGen(CodegenContext ctx) {
        this.ctx = ctx;
        this.symbols = ctx.symbols;
    }

    /** The three boundary input sources a decoder can read from (spec 6, 10.6). */
    enum Src { NEUTRAL, JSON, JOOQ }

    private ClassDesc cd(String typeName) { return ctx.cd(typeName); }
    private Map<String, Type> fieldTypes(Ast.Data data) { return ctx.fieldTypes(data); }
    private ClassDesc[] fieldDescs(Map<String, Type> fields) { return JvmTypes.fieldDescs(fields, ctx); }
    private void unbox(CodeBuilder code, Type type, int slot) { JvmTypes.unbox(code, type, slot, ctx); }

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

    /** The {@code invokedynamic} call site that produces a {@code Function} wrapping
     *  {@code Sets::fromList} (a {@code List -> Set} dedup), for {@code Decoder.map} in a Set decoder. */
    private static DynamicCallSiteDesc setFromListCallSite() {
        DirectMethodHandleDesc impl = MethodHandleDesc.ofMethod(
                DirectMethodHandleDesc.Kind.STATIC, CD_Sets, "fromList",
                MethodTypeDesc.of(CD_Set, CD_List));
        return DynamicCallSiteDesc.of(
                BSM_METAFACTORY, "apply",
                MethodTypeDesc.of(CD_Function),               // no captures: () -> Function
                MethodTypeDesc.of(CD_Object, CD_Object),      // samMethodType: (Object) -> Object
                impl,                                         // implMethod: Sets.fromList(List) -> Set
                MethodTypeDesc.of(CD_Set, CD_List));          // instantiatedMethodType: (List) -> Set
    }

    /** The name of the generated per-$Dec helper that remaps a decoded {@code Map<String, V>}'s keys
     *  into the String-backed newtype {@code keyType}, invariant-checked. */
    private static String rekeyMethod(String keyType) {
        return "__rekey$" + keyType;
    }

    /** {@code invokedynamic} producing a {@code BiFunction<Map, Path, Result>} over the current $Dec
     *  class's {@code __rekey$<keyType>}, for {@code Decoder.flatMapWithPath} in a newtype-keyed map. */
    private static DynamicCallSiteDesc rekeyCallSite(ClassDesc cdDec, String keyType) {
        DirectMethodHandleDesc impl = MethodHandleDesc.ofMethod(
                DirectMethodHandleDesc.Kind.STATIC, cdDec, rekeyMethod(keyType), MTD_rekey);
        return DynamicCallSiteDesc.of(
                BSM_METAFACTORY, "apply",
                MethodTypeDesc.of(CD_BiFunction),                        // no captures: () -> BiFunction
                MethodTypeDesc.of(CD_Object, CD_Object, CD_Object),      // samMethodType: (Object,Object) -> Object
                impl,                                                    // implMethod: __rekey(Map,Path) -> Result
                MTD_rekey);                                              // instantiatedMethodType: (Map,Path) -> Result
    }

    /** {@code invokedynamic} producing a {@code Function<K, String>} over the key newtype's bare
     *  {@code value()} accessor, for {@code Maps.mapKeys} when encoding a newtype-keyed map. */
    private DynamicCallSiteDesc keyValueCallSite(String keyType) {
        DirectMethodHandleDesc impl = MethodHandleDesc.ofMethod(
                DirectMethodHandleDesc.Kind.VIRTUAL, cd(keyType), "value", MTD_value);
        return DynamicCallSiteDesc.of(
                BSM_METAFACTORY, "apply",
                MethodTypeDesc.of(CD_Function),                          // no captures: () -> Function
                MethodTypeDesc.of(CD_Object, CD_Object),                 // samMethodType: (Object) -> Object
                impl,                                                    // implMethod: K.value() -> String
                MethodTypeDesc.of(CD_String, cd(keyType)));              // instantiatedMethodType: (K) -> String
    }

    /** Invokes a type's static {@code decoder()}/{@code encoder()} factory, as an interface
     * method reference when the type is a sum (its factory lives on a sealed interface). */
    private void invokeCodec(CodeBuilder code, String typeName, String method, MethodTypeDesc mtd) {
        code.invokestatic(cd(typeName), method, mtd, symbols.get(typeName) instanceof Ast.SumData);
    }

    byte[] generateSumEncoder(Ast.SumData sum, Ast.SumEncoder enc) {
        ClassDesc cdEnc = cd(sum.name() + "$Enc");
        return build(cdEnc, cb -> {
            cb.withFlags(ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            cb.withInterfaceSymbols(CD_REncoder);
            emitDefaultCtor(cb);
            // Dispatch on the runtime case type, encode that case to a Map, then inject the
            // discriminator key = case tag (spec 11.2).
            cb.withMethodBody("encode", MTD_Rencode, ClassFile.ACC_PUBLIC, code -> {
                for (Ast.EncVariant v : enc.variants()) {
                    ClassDesc caseCd = cd(v.caseType());
                    code.aload(1);
                    code.instanceOf(caseCd);
                    Label next = code.newLabel();
                    code.ifeq(next);
                    invokeCodec(code, v.caseType(), "encoder", MTD_Rencoder);
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

    byte[] generateSumDecoder(Ast.SumData sum, Ast.Discriminate disc, Src src) {
        ClassDesc cdDec = cd(sum.name() + srcSuffix(src));
        return build(cdDec, cb -> {
            cb.withFlags(ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            cb.withInterfaceSymbols(CD_RDecoder);
            emitDefaultCtor(cb);
            // Build a Raoh discriminate decoder and delegate: the tag is read from the
            // discriminator key of the source, each case dispatches to that case's decoder for the
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
                    invokeCodec(code, v.caseType(), srcFactory(src), MTD_Rdecoder);
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

    /** Decodes a unit: ignore the input (a unit carries no data) and build the singleton value. */
    byte[] generateUnitDecoder(ClassDesc cdU, ClassDesc cdDec) {
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
    byte[] generateUnitEncoder(ClassDesc cdEnc) {
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

    void emitFactory(ClassBuilder cb, String name, ClassDesc returnIface, Ast.Data data,
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
    void emitCodecFactory(ClassBuilder cb, String name, ClassDesc returnIface, ClassDesc impl,
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
    static MethodSignature decoderSig(ClassDesc type, boolean mapInput) {
        String in = mapInput
                ? "Ljava/util/Map<Ljava/lang/String;Ljava/lang/Object;>;"
                : "Ljava/lang/Object;";
        return MethodSignature.parseFrom(
                "()Lnet/unit8/raoh/decode/Decoder<" + in + type.descriptorString() + ">;");
    }

    /** {@code Encoder<T,O>}: {@code O} is {@code Map<String,Object>} for objects/sums/units, or the
     * bare (boxed) scalar for a newtype — a newtype encodes to a plain value, not a map. */
    static MethodSignature encoderSig(ClassDesc type, ClassDesc output) {
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
            case Ast.TextRaw _ -> CD_String;
            case Ast.IsoTextRaw _ -> CD_String;
            case Ast.IntRaw _ -> CD_Long;
            case Ast.BoolRaw _ -> CD_Boolean;
            case Ast.DecimalRaw _ -> CD_BigDecimal;
            case Ast.ObjectRaw _ -> CD_Map;
            case Ast.EncodeRaw _ -> CD_Object;
            case Ast.OptionRaw _ -> CD_Object;
            case Ast.ListEnc _ -> CD_Object;
            case Ast.SetEnc _ -> CD_Object;
            case Ast.MapEnc _ -> CD_Object;
        };
    }

    /** Source-specific decoder factory signature: {@code Decoder<In,T>} with In per source. */
    static MethodSignature decoderSigFor(Src src, ClassDesc type, boolean mapInput) {
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
    void emitSourceFactory(ClassBuilder cb, String typeName, Src src, boolean mapInput) {
        emitCodecFactory(cb, srcFactory(src), CD_RDecoder, cd(typeName + srcSuffix(src)),
                decoderSigFor(src, cd(typeName), mapInput));
    }

    /** JSON supports nested/list/map/optional but has no temporal leaf, so a type is JSON-decodable
     * iff no Date/DateTime appears anywhere in its shape. */
    boolean jsonCompatible(String typeName) {
        return jsonOk(typeName, new HashSet<>());
    }

    private boolean jsonOk(String typeName, Set<String> seen) {
        if (!seen.add(typeName)) {
            return true;
        }
        Ast.Def def = symbols.get(typeName);
        if (def instanceof Ast.SumData sum) {
            for (String caseName : sum.cases()) {
                if (!jsonOk(caseName, seen)) return false;
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
        if (t instanceof Type.OptionOf o) return jsonOkType(o.element(), seen);
        if (t instanceof Type.ListOf l) return jsonOkType(l.element(), seen);
        if (t instanceof Type.SetOf s) return jsonOkType(s.element(), seen);
        if (t instanceof Type.MapOf m) return jsonOkType(m.value(), seen);
        if (t instanceof Type.Ref r) return jsonOk(r.name(), seen);
        return true;
    }

    /** jOOQ rows are flat: a type is Record-decodable iff it is an object (or a sum of objects/units)
     * whose every field is a scalar column — a primitive, a newtype, or an optional of those; no
     * nested object, list, map, or sum. */
    boolean recordCompatible(String typeName) {
        Ast.Def def = symbols.get(typeName);
        if (def instanceof Ast.SumData sum) {
            for (String caseName : sum.cases()) {
                Ast.Def caseDef = symbols.get(caseName);
                if (caseDef instanceof Ast.UnitData) continue;
                if (!(caseDef instanceof Ast.Data d) || !isFlatObject(d)) return false;
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
        if (t instanceof Type.ListOf || t instanceof Type.MapOf || t instanceof Type.SetOf
                || t instanceof Type.Union) return false;
        if (t instanceof Type.Ref r) {
            return symbols.get(r.name()) instanceof Ast.Data d
                    && d.decoder().orElse(null) instanceof Ast.PrimDecoder;   // newtype column only
        }
        return true;   // primitive scalar
    }

    byte[] generateDecoderClass(ClassDesc cdName, Ast.Data data, Ast.DecoderDef dec,
                                        Map<String, Type> fields, Src src) {
        ClassDesc cdDec = cd(data.name() + srcSuffix(src));
        decoderClass = cdDec;
        return build(cdDec, cb -> {
            cb.withFlags(ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            cb.withInterfaceSymbols(CD_RDecoder);
            emitDefaultCtor(cb);
            // Raoh Decoder SAM: decode(Object in, Path path) -> Result. this=0, in=1, path=2.
            cb.withMethodBody("decode", MTD_Rdecode, ClassFile.ACC_PUBLIC, code -> {
                BodyGen gen = new BodyGen(ctx, code, data, cdName, 3);
                switch (dec) {
                    case Ast.PrimDecoder prim -> emitPrimDecode(code, gen, cdName, prim, fields, src);
                    case Ast.ObjectDecoder obj -> emitObjectDecode(code, gen, cdName, obj, fields, src);
                    case Ast.NewtypeDecoder nt -> emitNewtypeDecode(code, gen, cdName, nt, fields, src);
                }
            });
            // One key-remap helper per String-backed newtype used as a map key anywhere in this
            // decoder; the decode body's flatMapWithPath call sites reference them.
            Set<String> keyTypes = new LinkedHashSet<>();
            collectKeyedMapTypes(dec, keyTypes);
            for (String keyType : keyTypes) {
                emitRekeyHelper(cb, keyType);
            }
        });
    }

    /** Collects the String-backed newtypes used as map keys anywhere in a derived decoder. */
    private void collectKeyedMapTypes(Ast.DecoderDef dec, Set<String> out) {
        switch (dec) {
            case Ast.ObjectDecoder obj -> {
                for (Ast.Bind bind : obj.binds()) {
                    collectKeyedMapTypes(bind.ref(), out);
                }
            }
            case Ast.NewtypeDecoder nt -> collectKeyedMapTypes(nt.inner(), out);
            case Ast.PrimDecoder _ -> { }
        }
    }

    private void collectKeyedMapTypes(Ast.DecRef ref, Set<String> out) {
        switch (ref) {
            case Ast.MapDecRef mp -> {
                if (mp.keyType() != null) {
                    out.add(mp.keyType());
                }
                collectKeyedMapTypes(mp.value(), out);
            }
            case Ast.ListDecRef l -> collectKeyedMapTypes(l.element(), out);
            case Ast.SetDecRef s -> collectKeyedMapTypes(s.element(), out);
            case Ast.OptionDecRef o -> collectKeyedMapTypes(o.element(), out);
            case Ast.PrimDecRef _ -> { }
            case Ast.DataDecRef _ -> { }
        }
    }

    /**
     * Emits {@code static Result __rekey$K(Map src, Path path)}: it remaps a decoded
     * {@code Map<String, V>}'s keys into the String-backed newtype {@code K}, running {@code K}'s own
     * decoder (which applies K's invariant) on each key. Key issues accumulate across the whole map
     * (spec 15) and a failure lands at the key's path; on success it returns a {@code Map<K, V>} in
     * iteration order. Materialised as a {@code BiFunction} for {@code Decoder.flatMapWithPath}.
     */
    private void emitRekeyHelper(ClassBuilder cb, String keyType) {
        cb.withMethodBody(rekeyMethod(keyType), MTD_rekey, ClassFile.ACC_STATIC | ClassFile.ACC_SYNTHETIC,
                code -> {
            // locals: src=0, path=1, keyDec=2, out=3, issues=4, it=5, entry=6, key=7, kr=8
            invokeCodec(code, keyType, "decoder", MTD_Rdecoder);
            code.astore(2);                                              // keyDec = K.decoder()
            code.new_(CD_LinkedHashMap);
            code.dup();
            code.invokespecial(CD_LinkedHashMap, "<init>", MTD_void);
            code.astore(3);                                             // out = new LinkedHashMap()
            code.getstatic(CD_RIssues, "EMPTY", CD_RIssues);
            code.astore(4);                                            // issues = Issues.EMPTY
            code.aload(0);
            code.invokeinterface(CD_Map, "entrySet", MTD_entrySet);
            code.invokeinterface(CD_Set, "iterator", MTD_iterator);
            code.astore(5);                                            // it = src.entrySet().iterator()

            Label loop = code.newLabel();
            Label done = code.newLabel();
            code.labelBinding(loop);
            code.aload(5);
            code.invokeinterface(CD_Iterator, "hasNext", MTD_hasNext);
            code.ifeq(done);
            code.aload(5);
            code.invokeinterface(CD_Iterator, "next", MTD_getKeyValue);
            code.checkcast(CD_MapEntry);
            code.astore(6);                                            // entry = it.next()
            code.aload(6);
            code.invokeinterface(CD_MapEntry, "getKey", MTD_getKeyValue);
            code.astore(7);                                            // key = entry.getKey()
            // kr = keyDec.decode(key, path.append((String) key))
            code.aload(2);
            code.aload(7);
            code.aload(1);
            code.aload(7);
            code.checkcast(CD_String);
            code.invokevirtual(CD_RPath, "append", MTD_Path_append);
            code.invokeinterface(CD_RDecoder, "decode", MTD_Rdecode);
            code.astore(8);                                            // kr
            code.aload(8);
            code.instanceOf(CD_RErr);
            Label ok = code.newLabel();
            code.ifeq(ok);
            // Err: issues = issues.merge(((Err) kr).issues())
            code.aload(4);
            code.aload(8);
            code.checkcast(CD_RErr);
            code.invokevirtual(CD_RErr, "issues", MTD_Err_issues);
            code.invokevirtual(CD_RIssues, "merge", MTD_Issues_merge);
            code.astore(4);
            code.goto_(loop);
            code.labelBinding(ok);
            // out.put(((Ok) kr).value(), entry.getValue())
            code.aload(3);
            code.aload(8);
            code.checkcast(CD_ROk);
            code.invokevirtual(CD_ROk, "value", MTD_Object);
            code.aload(6);
            code.invokeinterface(CD_MapEntry, "getValue", MTD_getKeyValue);
            code.invokeinterface(CD_Map, "put", MTD_Map_put);
            code.pop();
            code.goto_(loop);

            code.labelBinding(done);
            code.aload(4);
            code.invokevirtual(CD_RIssues, "isEmpty", MTD_Issues_isEmpty);
            Label fail = code.newLabel();
            code.ifeq(fail);
            code.aload(3);
            code.invokestatic(CD_RResult, "ok", MTD_Rok, true);       // Result.ok(out)
            code.areturn();
            code.labelBinding(fail);
            code.aload(4);
            code.invokestatic(CD_RResult, "err", MTD_Rerr, true);    // Result.err(issues)
            code.areturn();
        });
    }

    /** True when the type's decoder reads from a {@code Map} (object/sum), false for a bare
     * value (newtype/unit). Used to bridge nested field-value decoders with {@code nested()}. */
    boolean isMapInput(String typeName) {
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
            case DATE -> emitTemporalLeaf(code, src, "date");
            case DATETIME -> emitTemporalLeaf(code, src, "dateTime");
        }
    }

    /** Emits a temporal leaf decoder. {@code JsonDecoders} has no {@code date()}/{@code dateTime()}
     * factory — a JSON temporal is a string that is then parsed (Raoh's {@code string().date()}),
     * whereas the neutral/map source has a direct static factory. */
    private void emitTemporalLeaf(CodeBuilder code, Src src, String method) {
        if (src == Src.JSON) {
            code.invokestatic(CD_JsonDecoders, "string", MTD_leafString);
            code.invokevirtual(CD_StringDecoder, method, MTD_leafTemporal);
        } else {
            code.invokestatic(srcLeafOwner(src), method, MTD_leafTemporal);
        }
    }

    private void emitPrimDecode(CodeBuilder code, BodyGen gen, ClassDesc cdName, Ast.PrimDecoder prim,
                                Map<String, Type> fields, Src src) {
        Type inputType = TypeChecker.primType(prim.from());
        ClassDesc leaf = srcLeafOwner(src);
        switch (prim.from()) {
            case TEXT -> code.invokestatic(leaf, "string", MTD_leafString);
            case INT -> code.invokestatic(leaf, "long_", MTD_leafLong);
            case BOOL -> code.invokestatic(leaf, "bool", MTD_leafBool);
            case DECIMAL -> code.invokestatic(leaf, "decimal", MTD_leafDecimal);
            case DATE -> emitTemporalLeaf(code, src, "date");
            case DATETIME -> emitTemporalLeaf(code, src, "dateTime");
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
    private void emitNewtypeDecode(CodeBuilder code, BodyGen gen, ClassDesc cdName, Ast.NewtypeDecoder dec,
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

    private void emitObjectDecode(CodeBuilder code, BodyGen gen, ClassDesc cdName, Ast.ObjectDecoder obj,
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
            case Ast.SetDecRef s -> Type.set(bindType(s.element()));
            case Ast.OptionDecRef o -> Type.option(bindType(o.element()));
            case Ast.MapDecRef mp -> Type.map(
                    mp.keyType() == null ? Type.STRING : Type.ref(mp.keyType()), bindType(mp.value()));
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
            case Ast.SetDecRef s -> {
                emitDecoderObject(code, s.element(), src);
                code.invokestatic(srcListOwner(src), "list", MTD_listDec);   // Decoder<I, List<T>>
                code.invokedynamic(setFromListCallSite());                   // Function: List -> Set
                code.invokeinterface(CD_RDecoder, "map", MTD_Rdecoder_map);  // Decoder<I, Set<T>> (dedup)
            }
            case Ast.OptionDecRef o -> throw new CompileException(o.pos(),
                    "optional is only supported as a direct object field");
            case Ast.MapDecRef mp -> {
                emitDecoderObject(code, mp.value(), src);
                code.invokestatic(srcListOwner(src), "map", MTD_mapDec);   // Decoder<I, Map<String,V>>
                if (mp.keyType() != null) {
                    // Remap the String keys into the key newtype, invariant-checked.
                    code.invokedynamic(rekeyCallSite(decoderClass, mp.keyType()));   // BiFunction<Map,Path,Result>
                    code.invokeinterface(CD_RDecoder, "flatMapWithPath", MTD_flatMapWithPath);
                }
            }
        }
    }

    private void emitRequire(CodeBuilder code, BodyGen gen, Ast.Require req) {
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

    /**
     * Emits the {@code __construct} call for a decoded value and maps an invariant failure to a Raoh
     * failure at the value's path. Must be emitted inside a {@code decode(Object, RPath)} body: it
     * reads the path from local slot 2 (the {@code RPath} parameter). Its three callers —
     * {@code emitPrimDecode}, {@code emitNewtypeDecode}, {@code emitObjectDecode} — are all such
     * bodies whose {@code BodyGen} locals start above slot 2, so slot 2 always holds the path.
     */
    private void emitConstructCall(CodeBuilder code, BodyGen gen, ClassDesc cdName, Ast.Construct construct,
                                   Map<String, Type> fields) {
        // The decoder is still AST-level; translate its field inits to Core so the shared
        // emitFieldValues consumes one representation (ADR-0021).
        List<Core.FieldInit> inits = new ArrayList<>();
        for (Ast.FieldInit init : construct.inits()) {
            inits.add(new Core.FieldInit(init.name(), Core.of(init.value()), init.pos()));
        }
        gen.emitFieldValues(fields, inits, construct.spreads());
        code.invokestatic(cdName, "__construct", MethodTypeDesc.of(CD_Result, fieldDescs(fields)));
        // Souther construction Result -> Raoh boundary Result. An invariant failure becomes a
        // Raoh failure (spec 9.4, 10.1); success wraps the constructed value.
        int srSlot = gen.slot(Type.STRING);
        code.astore(srSlot);
        code.aload(srSlot);
        code.instanceOf(CD_ResultErr);
        Label okL = code.newLabel();
        code.ifeq(okL);
        code.aload(2);   // the path this value was decoded at (spec 9.4, 15) — not the document root
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

    byte[] generateEncoderClass(ClassDesc cdName, Ast.Data data, Ast.EncoderDef enc) {
        ClassDesc cdEnc = cd(data.name() + "$Enc");
        return build(cdEnc, cb -> {
            cb.withFlags(ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            cb.withInterfaceSymbols(CD_REncoder);
            emitDefaultCtor(cb);
            cb.withMethodBody("encode", MTD_Rencode, ClassFile.ACC_PUBLIC, code -> {
                BodyGen gen = new BodyGen(ctx, code, data, cdName, 2);
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

    private void emitRawExpr(CodeBuilder code, BodyGen gen, Ast.RawExpr raw) {
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
            case Ast.SetEnc se -> {
                pushElemEncoder(code, se.elem());
                code.invokestatic(CD_MapEncoders, "list", MTD_Rencode_list);   // Encoder for an array
                gen.expr(se.source());                                          // the Set value
                code.invokestatic(CD_Sets, "toList", MethodTypeDesc.of(CD_List, CD_Set));   // Set -> List
                code.invokeinterface(CD_REncoder, "encode", MTD_Rencode);      // encode the array
            }
            case Ast.MapEnc me -> {
                pushElemEncoder(code, me.elem());
                code.invokestatic(CD_MapEncoders, "mapOf", MTD_Rencode_list);   // Encoder<Map<String,V>,Object>
                gen.expr(me.source());                                          // Map<K,V>
                if (me.keyType() != null) {
                    // Render the newtype keys bare before the String-keyed map encoder.
                    code.invokedynamic(keyValueCallSite(me.keyType()));         // Function<K,String>
                    code.invokestatic(CD_Maps, "mapKeys", MTD_mapKeys);         // Map<String,V>
                }
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
    private void emitOptionalEntry(CodeBuilder code, BodyGen gen, String key, Ast.OptionRaw o) {
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
}
