package souther.compiler.codegen;

import souther.compiler.check.Boundary;
import souther.compiler.check.DerivedSymbols;
import souther.compiler.ast.Hir;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;
import souther.compiler.check.Ordering;
import souther.compiler.check.ReadableFields;
import souther.compiler.check.Shape;
import souther.compiler.check.TypeOps;
import souther.compiler.check.TypeView;
import souther.compiler.core.ValueShape;

import souther.compiler.jvm.DecoderKind;
import souther.compiler.jvm.GeneratedClass;
import java.lang.classfile.Attribute;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassSignature;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.MethodSignature;
import java.lang.classfile.Signature;
import java.lang.classfile.TypeAnnotation;
import java.lang.classfile.attribute.PermittedSubclassesAttribute;
import java.lang.classfile.attribute.RecordAttribute;
import java.lang.classfile.attribute.RecordComponentInfo;
import java.lang.classfile.attribute.RuntimeVisibleTypeAnnotationsAttribute;
import java.lang.classfile.attribute.SignatureAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static souther.compiler.codegen.Descriptors.*;
import static souther.compiler.codegen.JvmTypes.*;

/**
 * Generates a data/sum/unit value class: its fields, package-private constructor, accessors, value
 * equality/hashCode, and the invariant-checking {@code __construct} (spec §builtin-types, §jvm-output). Each
 * type's codecs are emitted by the {@link CodecGen} it holds; body expressions through a {@link BodyGen}
 * built per method.
 */
final class ValueClassGen {

    private final CodegenContext ctx;
    private final DerivedSymbols symbols;
    private final CodecGen codec;

    ValueClassGen(CodegenContext ctx, CodecGen codec) {
        this.ctx = ctx;
        this.symbols = ctx.symbols;
        this.codec = codec;
    }

    private ClassDesc cd(GeneratedClass generated) { return ctx.cd(generated); }
    private GeneratedClass.Value valueOf(Hir.Def def) { return new GeneratedClass.Value(def.declares()); }
    private ClassDesc cd(Hir.Def def) { return ctx.cd(def); }
    private ClassDesc cd(TypeSymbol typeName) { return ctx.cd(typeName); }
    private ClassDesc[] caseInterfaces(String name) { return ctx.caseInterfaces(name); }
    private Map<String, Type> fieldTypes(Hir.Data data) { return ctx.fieldTypes(data); }
    private int pub(String name) { return ctx.pub(name); }
    private ClassDesc jvmType(Type type) { return JvmTypes.jvmType(type, ctx); }
    private ClassDesc[] fieldDescs(Map<String, Type> fields) { return JvmTypes.fieldDescs(fields, ctx); }

    /**
     * What a value of {@code data} is made of and what must hold of one, as the check answered it.
     *
     * <p>Not worked out here. Where a field is read through and what a clause comes to are one
     * answer of the checker's ({@code Shapes.ValueShapes}), and a data of this module being emitted
     * with none is this compilation having decided to emit something it never checked.
     */
    private ValueShape shapeOf(Hir.Data data) {
        ValueShape shape = ctx.shapeOf(data.declares());
        if (shape == null) {
            throw new IllegalStateException("`" + data.name() + "` is being emitted and the check"
                    + " answered nothing about what a value of it is");
        }
        return shape;
    }

    /**
     * The fields into the slots they arrive in, each under the binding a clause reads it through.
     *
     * <p>The bindings are the check's and the order is the layout, both off one answer: a field
     * bound under a binding worked out here would be a second walk of the declarations, and a
     * clause reading the other one would read a slot nothing put its value in.
     */
    private void bindFields(BodyGen gen, Hir.Data data) {
        int slot = 0;
        for (ValueShape.Field field : shapeOf(data).fields()) {
            gen.bind(field.binding(), field.name(), slot, field.type());
            slot += width(field.type());
        }
    }

    void generateData(Hir.Data data, Emissions out) {
        ClassDesc cdName = cd(data);
        Map<String, Type> fields = fieldTypes(data);

        out.put(valueOf(data), build(cdName, cb -> {
            cb.withFlags(pub(data.name()) | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            cb.withSuperclass(CD_Record);
            cb.with(recordComponents(fields));
            List<ClassDesc> ifaces = new ArrayList<>(List.of(caseInterfaces(data.name())));
            boolean ordered = isOrderedNewtype(data, fields);
            if (ordered) {
                ifaces.add(CD_Comparable);
            }
            if (!ifaces.isEmpty()) {
                cb.withInterfaceSymbols(ifaces);
            }
            if (ordered) {
                cb.with(SignatureAttribute.of(ClassSignature.parseFrom(classSignature(cdName, ifaces))));
            }
            for (Map.Entry<String, Type> f : fields.entrySet()) {
                emitField(cb, f.getKey(), f.getValue());
            }
            emitCtor(cb, cdName, fields);
            emitValueEquality(cb, cdName, fields);
            emitToString(cb, cdName, data.name(), fields);
            if (ordered) {
                emitCompareTo(cb, cdName, fields.entrySet().iterator().next());
            }
            emitConstructMethod(cb, cdName, data, fields);
            emitAccessors(cb, cdName, fields);
            boolean mapInput = codec.isMapInput(data);
            codec.emitFactory(cb, "decoder", CD_RDecoder, data, new GeneratedClass.Decoder(valueOf(data), DecoderKind.VALUE));
            codec.emitSourceFactory(cb, data, CodecGen.Src.JSON, mapInput);
            if (codec.recordCompatible(data)) codec.emitSourceFactory(cb, data, CodecGen.Src.JOOQ, mapInput);
            codec.emitFactory(cb, "encoder", CD_REncoder, data, new GeneratedClass.Encoder(valueOf(data)));
        }));

        Hir.DecoderDef dec = symbols.derived(data).decoder();
        out.put(new GeneratedClass.Decoder(valueOf(data), DecoderKind.VALUE),
                codec.generateDecoderClass(cdName, data, dec, fields, CodecGen.Src.NEUTRAL));
        out.put(new GeneratedClass.Decoder(valueOf(data), DecoderKind.JSON),
                codec.generateDecoderClass(cdName, data, dec, fields, CodecGen.Src.JSON));
        if (codec.recordCompatible(data)) {
            out.put(new GeneratedClass.Decoder(valueOf(data), DecoderKind.RECORD),
                    codec.generateDecoderClass(cdName, data, dec, fields, CodecGen.Src.JOOQ));
        }
        out.put(new GeneratedClass.Encoder(valueOf(data)),
                codec.generateEncoderClass(cdName, data, symbols.derived(data).encoder()));

        // A helper for an invariant-bearing newtype: a Raoh-free `boolean check(value)` that runs the
        // same invariant bytecode as __construct — the checker's, which both read. Two callers: a constant construction
        // is verified at compile time through it — 金額(-5) is a compile error, not a runtime abort
        // (ADR-0032) — and the derived decoder passes it to Raoh's `refine` for an invariant no
        // constraint states exactly (issue #83).
        if (data.newtype() && !shapeOf(data).invariants().isEmpty()) {
            emitCtfeCheck(data, fields, out);
        }
    }

    /**
     * The Raoh-free checks of an invariant-bearing newtype: {@code check} for the whole invariant, and
     * {@code check$i} for the clause declared {@code i}th. Both run the same bytecode
     * {@code __construct} does: the clause the checker elaborated, read here and there.
     *
     * <p>Two callers want the whole invariant — a constant construction verified at compile time
     * (ADR-0032) — and one wants a clause on its own: the derived decoder hands each clause its own
     * predicate, so a rule no Raoh constraint states exactly is still reported as the rule it is
     * rather than as the whole invariant (issue #83, spec §decoder-error).
     */
    private void emitCtfeCheck(Hir.Data data, Map<String, Type> fields, Emissions out) {
        ClassDesc cdName = cd(data);
        ClassDesc cdCtfe = cd(new GeneratedClass.Ctfe(valueOf(data)));
        List<ValueShape.Invariant> clauses = shapeOf(data).invariants();
        out.put(new GeneratedClass.Ctfe(valueOf(data)), build(cdCtfe, cb -> {
            cb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            emitClauseCheck(cb, "check", cdName, data, fields, clauses);
            for (int i = 0; i < clauses.size(); i++) {
                emitClauseCheck(cb, ctfeClauseCheck(i), cdName, data, fields,
                        List.of(clauses.get(i)));
            }
        }));
    }

    /** The name of the {@code $Ctfe} method checking the clause declared {@code i}th. */
    static String ctfeClauseCheck(int index) {
        return "check$" + index;
    }

    private void emitClauseCheck(ClassBuilder cb, String method, ClassDesc cdName, Hir.Data data,
                                 Map<String, Type> fields, List<ValueShape.Invariant> clauses) {
        cb.withMethodBody(method, MethodTypeDesc.of(ConstantDescs.CD_boolean, fieldDescs(fields)),
                ClassFile.ACC_STATIC | ClassFile.ACC_PUBLIC, code -> {
                    BodyGen gen = new BodyGen(ctx, code, data, cdName, 0);
                    bindFields(gen, data);
                    for (ValueShape.Invariant clause : clauses) {
                        gen.genExpr(clause.condition());   // the same boolean __construct checks
                        Label ok = code.newLabel();
                        code.ifne(ok);
                        code.iconst_0();
                        code.ireturn();                // a clause is false
                        code.labelBinding(ok);
                    }
                    code.iconst_1();
                    code.ireturn();                    // all held
                });
    }

    void generateSum(Hir.SumData sum, Emissions out) {
        ClassDesc cdX = cd(sum);
        List<ClassDesc> caseCds = new ArrayList<>();
        for (Hir.Name caseName : sum.cases()) {
            // Every name in a module the backend generates was answered: `Bodies.Checked` hands an
            // elaboration over only where `Names.Sound` holds of the module, which resolution makes
            // false as soon as it reports a name denoting nothing.
            caseCds.add(cd(Backend.names(caseName)));
        }
        // How this sum's alternatives are written is settled once, here, and handed to everything
        // that generates from it. Each of them holding the type and the symbols instead would be
        // each of them able to work the form and the tag out again, which is what five of them did.
        Boundary.Alternatives alternatives =
                Boundary.of(Type.ref(sum.declares()), ctx.kinds, ctx.published);
        boolean enumeration = alternatives.representation() instanceof Boundary.Representation.Enumeration;
        out.put(valueOf(sum), build(cdX, cb -> {
            cb.withFlags(pub(sum.name()) | ClassFile.ACC_INTERFACE | ClassFile.ACC_ABSTRACT);
            // A sum may itself be a case of another sum (spec §sum-data), and then it carries that sum's
            // interface as a product or unit case does. Only the direct link is recorded, which is
            // all that is needed: interface inheritance carries it the rest of the way, so a leaf of
            // this sum is a value of the outer one without being named there.
            ClassDesc[] ifaces = caseInterfaces(sum.name());
            if (ifaces.length > 0) {
                cb.withInterfaceSymbols(ifaces);
            }
            cb.with(PermittedSubclassesAttribute.ofSymbols(caseCds));
            // A field every case spreads is readable on the sum (issue #160): declared here, and
            // implemented by each case record's accessor of the same name and descriptor.
            if (TypeView.asWritten(Type.ref(sum.declares()), symbols, ctx.published).shape()
                    instanceof Shape.Sum shape) {
                for (Map.Entry<String, Type> e : ReadableFields.of(shape).declaredFields().entrySet()) {
                    cb.withMethod(e.getKey(), MethodTypeDesc.of(jvmType(e.getValue())),
                            ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT, mb -> { });
                }
            }
            // An enumeration travels as its case's name (issue #161): one place says which name that
            // is, and the codecs on both sides read it from here.
            if (enumeration) {
                emitTagMethod(cb, alternatives.wireCases());
            }
            // Where a case stands in the declaration is the language's order (ADR-0069) and not how
            // a value is written, so it is gated on what answers that — the same `Ordering` every
            // reader of a comparison asks, which is what sends them to this class's `__order`. The
            // two gates hold of the same sums today. Written as one, a wire form that stopped being
            // a bare tag would take the ordering methods with it and leave a comparison calling a
            // method nothing emitted.
            if (Ordering.of(Type.ref(sum.declares()), symbols, ctx.kinds, ctx.published)
                    instanceof Ordering.Places places
                    && places.enumeration().equals(sum.declares())) {
                emitOrderMethods(cb, cdX, alternatives.atoms());
            }
            codec.emitCodecFactory(cb, "decoder", CD_RDecoder, cd(new GeneratedClass.Decoder(valueOf(sum), DecoderKind.VALUE)),
                    CodecGen.decoderSig(cdX, !enumeration));
            codec.emitSourceFactory(cb, sum, CodecGen.Src.JSON, !enumeration);
            if (codec.recordCompatible(sum)) codec.emitSourceFactory(cb, sum, CodecGen.Src.JOOQ, true);
            codec.emitCodecFactory(cb, "encoder", CD_REncoder, cd(new GeneratedClass.Encoder(valueOf(sum))),
                    CodecGen.encoderSig(cdX, enumeration ? CD_String : CD_Map));
        }));
        out.put(new GeneratedClass.Decoder(valueOf(sum), DecoderKind.VALUE), enumeration
                ? codec.generateEnumSumDecoder(sum, alternatives, CodecGen.Src.NEUTRAL)
                : codec.generateSumDecoder(sum, alternatives, CodecGen.Src.NEUTRAL));
        out.put(new GeneratedClass.Decoder(valueOf(sum), DecoderKind.JSON), enumeration
                ? codec.generateEnumSumDecoder(sum, alternatives, CodecGen.Src.JSON)
                : codec.generateSumDecoder(sum, alternatives, CodecGen.Src.JSON));
        if (codec.recordCompatible(sum)) {
            out.put(new GeneratedClass.Decoder(valueOf(sum), DecoderKind.RECORD),
                    codec.generateSumDecoder(sum, alternatives, CodecGen.Src.JOOQ));
        }
        out.put(new GeneratedClass.Encoder(valueOf(sum)), enumeration
                ? codec.generateEnumSumEncoder(sum)
                : codec.generateSumEncoder(sum, alternatives));
    }

    /**
     * Emits {@code static int __order(Object)} — where a value's case stands in the declaration —
     * and {@code static Comparator __ordering()} over it. The order sits on the sum rather than on
     * the case records because one unit data may be a case of two sums, which place it differently;
     * a {@code Comparable} on the record would have to answer for both (issue #161).
     */
    private void emitOrderMethods(ClassBuilder cb, ClassDesc cdX, List<TypeSymbol> cases) {
        cb.withMethod(ORDER_METHOD, MTD_order, ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC
                | ClassFile.ACC_SYNTHETIC, mb -> mb.withCode(code -> {
            int i = 0;
            for (TypeSymbol c : cases) {
                code.aload(0);
                code.instanceOf(cd(c));
                Label next = code.newLabel();
                code.ifeq(next);
                code.loadConstant(i);
                code.ireturn();
                code.labelBinding(next);
                i++;
            }
            code.new_(CD_IllegalStateException);
            code.dup();
            code.invokespecial(CD_IllegalStateException, "<init>", MTD_void);
            code.athrow();
        }));
        cb.withMethod(ORDERING_METHOD, MTD_ordering, ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC
                | ClassFile.ACC_SYNTHETIC, mb -> mb.withCode(code -> {
            code.invokedynamic(DynamicCallSiteDesc.of(
                    BSM_METAFACTORY, "applyAsInt",
                    MethodTypeDesc.of(CD_ToIntFunction),
                    MethodTypeDesc.of(ConstantDescs.CD_int, CD_Object),
                    MethodHandleDesc.ofMethod(DirectMethodHandleDesc.Kind.INTERFACE_STATIC,
                            cdX, ORDER_METHOD, MTD_order),
                    MTD_order));
            code.invokestatic(CD_Comparator, "comparingInt",
                    MethodTypeDesc.of(CD_Comparator, CD_ToIntFunction), true);
            code.areturn();
        }));
    }

    /** Emits {@code static String __tag(Object)}: which case a value of this enumeration is. */
    private void emitTagMethod(ClassBuilder cb, List<Boundary.WireCase> cases) {
        cb.withMethod(TAG_METHOD, MTD_tag, ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC
                | ClassFile.ACC_SYNTHETIC, mb -> mb.withCode(code -> {
            for (Boundary.WireCase c : cases) {
                code.aload(0);
                code.instanceOf(cd(c.atom()));
                Label next = code.newLabel();
                code.ifeq(next);
                code.loadConstant(c.tag());
                code.areturn();
                code.labelBinding(next);
            }
            code.new_(CD_IllegalStateException);
            code.dup();
            code.invokespecial(CD_IllegalStateException, "<init>", MTD_void);
            code.athrow();
        }));
    }

    /**
     * The bridge case a non-local union member reaches its result unions through: a record of one
     * component, {@code value}, holding the member as it is, implementing every result union of this
     * module the member belongs to (spec §jvm-anonymous-union). A member this module declared carries those
     * interfaces on itself; a primitive is the JDK's class and an imported type is a class another
     * module already emitted, so neither can be given one from here.
     *
     * <p>It has no codec. Belonging to a union does not change a member's external representation, so
     * a consumer that has switched to this case takes the value out and uses the member's own codec.
     */
    byte[] generateBridgeCase(TypeSymbol member, List<GeneratedClass.BehaviorResult> unions) {
        ClassDesc cdB = ctx.bridgeCaseClass(member);
        Map<String, Type> held = Map.of("value", TypeOps.caseBindType(member));
        List<ClassDesc> ifaces = new ArrayList<>();
        for (GeneratedClass.BehaviorResult union : unions) {
            ifaces.add(cd(union));
        }
        return build(cdB, cb -> {
            cb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            cb.withSuperclass(CD_Record);
            cb.with(recordComponents(held));
            cb.withInterfaceSymbols(ifaces);
            emitField(cb, "value", held.get("value"));
            // Public, unlike a case class's: a bridge case carries no invariant of its own and the
            // value it holds was already built through that type's own checking path, so there is
            // nothing here for a non-public constructor to guard. A Java implementation of an
            // injected behavior has to be able to answer with this member.
            emitCtor(cb, cdB, held, ClassFile.ACC_PUBLIC);
            emitValueEquality(cb, cdB, held);
            emitToString(cb, cdB, cdB.displayName(), held);
            emitAccessors(cb, cdB, held);
        });
    }

    void generateUnit(Hir.UnitData unit, Emissions out) {
        ClassDesc cdU = cd(unit);
        ClassDesc cdDec = cd(new GeneratedClass.Decoder(valueOf(unit), DecoderKind.VALUE));
        ClassDesc cdEnc = cd(new GeneratedClass.Encoder(valueOf(unit)));
        out.put(valueOf(unit), build(cdU, cb -> {
            cb.withFlags(pub(unit.name()) | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            // a unit is a field-less data, so it is a record with no components: `case 承認済み()`
            // deconstructs it in a Java switch as its sibling product cases do (spec §jvm-product)
            cb.withSuperclass(CD_Record);
            cb.with(recordComponents(Map.of()));
            ClassDesc[] ifaces = caseInterfaces(unit.name());
            if (ifaces.length > 0) {
                cb.withInterfaceSymbols(ifaces);
            }
            emitDefaultCtor(cb, CD_Record);
            emitValueEquality(cb, cdU, Map.of());   // all units of a type are the same value
            emitToString(cb, cdU, unit.name(), Map.of());
            // A unit has no fields, no invariant and so no `__construct` (spec §unit-data), so the
            // type has exactly one value. The field is public because another module's generated
            // code loads it, and on an exposed unit that puts the value within reach of hand-written
            // Java too — which costs nothing a unit was protecting: it carries no invariant, and
            // construction is governed by `constructs` rather than by visibility (ADR-0059).
            emitSharedInstance(cb, cdU);
            // a unit is a field-less data: its codec reads/writes nothing but the tag the sum adds
            // A unit ignores its input, so it decodes from every source. Generate all three so
            // unit cases of a JSON/record sum have a matching decoder to dispatch to.
            codec.emitCodecFactory(cb, "decoder", CD_RDecoder, cdDec, CodecGen.decoderSig(cdU, false));
            codec.emitCodecFactory(cb, "jsonDecoder", CD_RDecoder, cd(new GeneratedClass.Decoder(valueOf(unit), DecoderKind.JSON)),
                    CodecGen.decoderSigFor(CodecGen.Src.JSON, cdU, false));
            codec.emitCodecFactory(cb, "recordDecoder", CD_RDecoder, cd(new GeneratedClass.Decoder(valueOf(unit), DecoderKind.RECORD)),
                    CodecGen.decoderSigFor(CodecGen.Src.JOOQ, cdU, false));
            codec.emitCodecFactory(cb, "encoder", CD_REncoder, cdEnc, CodecGen.encoderSig(cdU, CD_Map));
        }));
        out.put(new GeneratedClass.Decoder(valueOf(unit), DecoderKind.VALUE), codec.generateUnitDecoder(cdU, cdDec));
        out.put(new GeneratedClass.Decoder(valueOf(unit), DecoderKind.JSON), codec.generateUnitDecoder(cdU, cd(new GeneratedClass.Decoder(valueOf(unit), DecoderKind.JSON))));
        out.put(new GeneratedClass.Decoder(valueOf(unit), DecoderKind.RECORD), codec.generateUnitDecoder(cdU, cd(new GeneratedClass.Decoder(valueOf(unit), DecoderKind.RECORD))));
        out.put(new GeneratedClass.Encoder(valueOf(unit)), codec.generateUnitEncoder(cdEnc));
    }

    /**
     * Emits {@code equals} / {@code hashCode} comparing every field.
     *
     * <p>A data is an immutable value, so two of them are the same when their fields are — which
     * is what {@code ==} means on a data (spec §equality) and what Java callers expect of a value
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
                            } else {
                                // What a field's sameness means is the runtime's to say (spec §equality):
                                // an amount ignores its scale wherever it sits, including inside a
                                // collection this field holds.
                                emitValueEquals(code, t == Type.DECIMAL);
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
                        } else {
                            // The hash the equality above agrees with, from the same place. A field
                            // whose equality ignores a scale and whose hash did not would land 1.0
                            // and 1.00 in different buckets, and a Map keyed by this data would stop
                            // working. Groovy changed `==` and left hashCode alone; that bug is open.
                            emitValueHash(code, t == Type.DECIMAL);
                        }
                        code.iadd();
                    }
                    code.ireturn();
                });
    }

    /** A single-value newtype over an ordered type — ordered by the value it wraps (ADR-0047), which
     * the class carries as {@link Comparable} so {@code sort} / {@code max} / {@code min} compare it
     * by natural order, and a Java reader can put it in a {@code TreeSet}. The order it carries is
     * {@link #orderOfWrapped}: claiming one here that the {@code compareTo} below cannot emit is
     * what left {@code data StageN = Stage} declaring {@code Comparable} and throwing on the first
     * Java reader that compared two (issue #856). */
    private boolean isOrderedNewtype(Hir.Data data, Map<String, Type> fields) {
        return data.newtype() && fields.size() == 1
                && orderOfWrapped(fields.values().iterator().next()) != null;
    }

    /** How the value a newtype wraps compares, as the newtype's own field holds it. */
    private Ordering orderOfWrapped(Type value) {
        Ordering how = Ordering.of(value, symbols, ctx.kinds, ctx.published);
        return how == null ? null : how.asHeld();
    }

    /** {@code Record} plus each interface, with {@code Comparable} bound to the class itself, so a
     * Java reader sees {@code Comparable<金額>} rather than the raw form. */
    private static String classSignature(ClassDesc cdName, List<ClassDesc> ifaces) {
        StringBuilder sig = new StringBuilder(CD_Record.descriptorString());
        for (ClassDesc iface : ifaces) {
            if (iface.equals(CD_Comparable)) {
                String raw = CD_Comparable.descriptorString();
                sig.append(raw, 0, raw.length() - 1)      // drop the ';' to insert the type argument
                        .append('<').append(cdName.descriptorString()).append(">;");
            } else {
                sig.append(iface.descriptorString());
            }
        }
        return sig.toString();
    }

    /**
     * Emits {@code toString} in the form a record prints — {@code 金額[value=500]}, the type name and
     * each component. {@code java.lang.Record} declares it abstract, so a data without one resolves to
     * that declaration and throws {@code AbstractMethodError} when anything prints the value.
     */
    private void emitToString(ClassBuilder cb, ClassDesc cdName, String typeName,
                              Map<String, Type> fields) {
        cb.withMethodBody("toString", MTD_toString, ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL, code -> {
            code.new_(CD_StringBuilder);
            code.dup();
            code.invokespecial(CD_StringBuilder, "<init>", MTD_void);
            append(code, typeName + "[");
            boolean first = true;
            for (Map.Entry<String, Type> f : fields.entrySet()) {
                append(code, (first ? "" : ", ") + f.getKey() + "=");
                first = false;
                Type t = f.getValue();
                code.aload(0);
                code.getfield(cdName, f.getKey(), jvmType(t));
                if (t == Type.INT) {
                    code.invokevirtual(CD_StringBuilder, "append", MTD_SB_appendLong);
                } else if (t == Type.BOOL) {
                    code.invokevirtual(CD_StringBuilder, "append", MTD_SB_appendBoolean);
                } else {
                    code.invokevirtual(CD_StringBuilder, "append", MTD_SB_appendObject);
                }
            }
            append(code, "]");
            code.invokevirtual(CD_StringBuilder, "toString", MTD_toString);
            code.areturn();
        });
    }

    /** Appends a literal to the {@code StringBuilder} on the stack, leaving it there. */
    private static void append(CodeBuilder code, String literal) {
        code.loadConstant(literal);
        code.invokevirtual(CD_StringBuilder, "append", MTD_SB_appendString);
    }

    /**
     * Emits {@code compareTo}, from the order the wrapped value has rather than from a guess at its
     * representation. An {@code Int} newtype compares its {@code long} carrier; a value the JVM
     * carries as {@link Comparable} — a {@code String} / {@code BigDecimal} / {@code LocalDate} /
     * {@code LocalTime} / {@code LocalDateTime} / {@code Instant}, or a newtype over one — compares
     * itself; a value of an enumeration has no {@code compareTo} of its own, because the order lives
     * on the sum and one unit data may be a case of two (ADR-0069), so its place is read off the sum.
     *
     * <p>That last arm is the one that was missing. "Ordered and not an {@code Int}" was read as
     * "{@code Comparable}", which every ordered value but an enumeration's is, and the class went out
     * declaring an interface it could not honour. The erased {@code compareTo(Object)} bridge is what
     * the runtime's natural-order compare calls.
     */
    private void emitCompareTo(ClassBuilder cb, ClassDesc cdName, Map.Entry<String, Type> value) {
        ClassDesc fd = jvmType(value.getValue());
        Ordering how = orderOfWrapped(value.getValue());
        cb.withMethodBody("compareTo", MethodTypeDesc.of(ConstantDescs.CD_int, cdName),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL, code -> {
                    code.aload(0);
                    code.getfield(cdName, value.getKey(), fd);
                    if (how instanceof Ordering.Places places) {
                        code.invokestatic(cd(places.enumeration()), ORDER_METHOD, MTD_order, true);
                    }
                    code.aload(1);
                    code.getfield(cdName, value.getKey(), fd);
                    switch (how) {
                        case Ordering.Longs _ ->
                                code.lcmp();   // -1 / 0 / 1, which is compareTo's contract
                        case Ordering.Natural _ ->
                                code.invokeinterface(CD_Comparable, "compareTo", MTD_compareTo_Object);
                        case Ordering.Places places -> {
                            code.invokestatic(cd(places.enumeration()), ORDER_METHOD, MTD_order, true);
                            code.invokestatic(CD_Integer, "compare", MTD_Integer_compare, false);
                        }
                        // asHeld answers for the newtype as its own class holds it, and a newtype is
                        // Comparable, so nothing reaches here.
                        case Ordering.Wrapped _ -> throw new IllegalStateException(
                                "a wrapped order is never what a value is held as: " + value.getValue());
                        case null -> throw new IllegalStateException(
                                "compareTo is emitted only where isOrderedNewtype found an order: "
                                        + value.getValue());
                    }
                    code.ireturn();
                });
        cb.withMethodBody("compareTo", MethodTypeDesc.of(ConstantDescs.CD_int, CD_Object),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL
                        | ClassFile.ACC_BRIDGE | ClassFile.ACC_SYNTHETIC, code -> {
                    code.aload(0);
                    code.aload(1);
                    code.checkcast(cdName);
                    code.invokevirtual(cdName, "compareTo", MethodTypeDesc.of(ConstantDescs.CD_int, cdName));
                    code.ireturn();
                });
    }

    /**
     * Emits the public record-style read accessor {@code <field>()} for each component (spec §field-visibility,
     * 19.2). Every data has them, not only an exposed one: a component of the {@code Record} attribute
     * is read through its accessor, the generated code of this module reads a field the same way, and
     * a class the module keeps to itself is out of a Java caller's reach anyway. Reading never enables
     * construction — the constructor stays non-public (spec §asymmetric-interop).
     *
     * <p>The return type carries {@code @NonNull} because Kotlin types a component from the record and
     * does not apply the class's {@code @NullMarked} there; without it every read is a platform type
     * again (spec §jvm-nullness).
     */
    private void emitAccessors(ClassBuilder cb, ClassDesc cdName, Map<String, Type> fields) {
        for (Map.Entry<String, Type> f : fields.entrySet()) {
            Type ft = f.getValue();
            ClassDesc fd = jvmType(ft);
            String sig = JvmTypes.genericSig(ft, ctx);
            List<TypeAnnotation> nonNull =
                    JvmTypes.nonNullPositions(ft, TypeAnnotation.TargetInfo.ofMethodReturn());
            cb.withMethod(f.getKey(), MethodTypeDesc.of(fd),
                    ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL, mb -> {
                        if (sig != null) mb.with(SignatureAttribute.of(MethodSignature.parseFrom("()" + sig)));
                        if (!nonNull.isEmpty()) {
                            mb.with(RuntimeVisibleTypeAnnotationsAttribute.of(nonNull));
                        }
                        mb.withCode(code -> {
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
                    });
        }
    }

    /**
     * The {@code Record} attribute naming each field as a component, in constructor order, so the
     * class is a record to everything that reads class files: javac deconstructs it in a record
     * pattern, Kotlin reads each component as a property, and {@code Class.getRecordComponents}
     * answers (spec §jvm-product). Each component repeats what its accessor states — a container's generic
     * {@code Signature}, and {@code @NonNull} — because reflection reads the component, not the
     * accessor.
     */
    private RecordAttribute recordComponents(Map<String, Type> fields) {
        List<RecordComponentInfo> components = new ArrayList<>();
        for (Map.Entry<String, Type> f : fields.entrySet()) {
            Type type = f.getValue();
            String sig = JvmTypes.genericSig(type, ctx);
            List<Attribute<?>> attrs = new ArrayList<>();
            if (sig != null) attrs.add(SignatureAttribute.of(Signature.parseFrom(sig)));
            List<TypeAnnotation> nonNull =
                    JvmTypes.nonNullPositions(type, TypeAnnotation.TargetInfo.ofField());
            if (!nonNull.isEmpty()) attrs.add(RuntimeVisibleTypeAnnotationsAttribute.of(nonNull));
            components.add(RecordComponentInfo.of(f.getKey(), jvmType(type), attrs));
        }
        return RecordAttribute.of(components);
    }

    /**
     * Emits a {@code private final} backing field, as a record's is: every read goes through the
     * accessor, including the generated code of this module. A container field
     * ({@code List}/{@code Set}/{@code Map}/{@code Option}) also gets a generic {@code Signature} so
     * its element type survives to a Java reader; a field whose raw descriptor already names it fully
     * gets none.
     */
    private void emitField(ClassBuilder cb, String name, Type type) {
        String sig = JvmTypes.genericSig(type, ctx);
        cb.withField(name, jvmType(type), fb -> {
            fb.withFlags(ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL);
            if (sig != null) fb.with(SignatureAttribute.of(Signature.parseFrom(sig)));
        });
    }

    /**
     * Emits the canonical constructor: the components in declaration order, package-private, so a
     * value is built inside the module or through the invariant-checking {@code __construct} and not
     * by a Java caller writing {@code new} (spec §field-visibility).
     */
    private void emitCtor(ClassBuilder cb, ClassDesc cdName, Map<String, Type> fields) {
        emitCtor(cb, cdName, fields, 0);
    }

    private void emitCtor(ClassBuilder cb, ClassDesc cdName, Map<String, Type> fields, int flags) {
        cb.withMethodBody("<init>", MethodTypeDesc.of(ConstantDescs.CD_void, fieldDescs(fields)), flags, code -> {
            code.aload(0);
            code.invokespecial(CD_Record, "<init>", MTD_void);
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

    private void emitConstructMethod(ClassBuilder cb, ClassDesc cdName, Hir.Data data,
                                     Map<String, Type> fields) {
        // Public for an exposed type: a behavior of another module may declare `constructs T`
        // (ADR-0002 never restricted that to T's own module), and this is the path it takes — the one
        // that runs the invariant. A type this module keeps to itself keeps its entry package-private.
        cb.withMethod("__construct", MethodTypeDesc.of(CD_Result, fieldDescs(fields)),
                ClassFile.ACC_STATIC | ctx.pub(data.name()), mb -> {
                    mb.with(SignatureAttribute.of(
                            MethodSignature.parseFrom(constructSignature(fields, cdName))));
                    mb.withCode(code -> {
                        BodyGen gen = new BodyGen(ctx, code, data, cdName, 0);
                        bindFields(gen, data);

                        // Clause by clause, in the order they are declared, stopping at the first that
                        // does not hold: what the failure carries is that clause, so a reordering of
                        // the declaration changes which one a caller is told about.
                        for (ValueShape.Invariant clause : shapeOf(data).invariants()) {
                            gen.genExpr(clause.condition());
                            Label ok = code.newLabel();
                            code.ifne(ok);
                            code.loadConstant(ctx.module());
                            code.loadConstant(data.name());
                            if (clause.name().isPresent()) {
                                code.loadConstant(clause.name().get());
                                code.invokestatic(CD_InvariantFailure, "of", MTD_failureOf, false);
                            } else {
                                code.invokestatic(CD_InvariantFailure, "unnamed",
                                        MTD_failureUnnamed, false);
                            }
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
                });
    }

    /**
     * The generic {@code Signature} of {@code __construct}: {@code Result<T, InvariantFailure>}, whose
     * failure side names the clause that did not hold. Unlike a factory's, it is written whether or not
     * a field is a container — the raw {@code Result} the descriptor names carries no type at all, and
     * a Kotlin caller reads a raw type as a platform type, which is the one thing the rest of the class
     * is marked to avoid (issue #150).
     */
    private String constructSignature(Map<String, Type> fields, ClassDesc cdName) {
        StringBuilder sb = new StringBuilder("(");
        for (Type t : fields.values()) {
            String g = JvmTypes.genericSig(t, ctx);
            sb.append(g != null ? g : jvmType(t).descriptorString());
        }
        return sb.append(")Lsouther/runtime/Result<")
                .append(cdName.descriptorString())
                .append(CD_InvariantFailure.descriptorString())
                .append(">;").toString();
    }
}
