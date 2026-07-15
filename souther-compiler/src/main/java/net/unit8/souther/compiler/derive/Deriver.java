package net.unit8.souther.compiler.derive;

import net.unit8.souther.compiler.CompileException;
import net.unit8.souther.compiler.SourcePos;
import net.unit8.souther.compiler.ast.Ast;
import net.unit8.souther.compiler.check.Type;
import net.unit8.souther.compiler.check.TypeChecker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Derives default boundary codecs (decoders/encoders and sum discriminators) from the shape
 * of the domain data. Decoders/encoders are not part of the domain syntax (they are a
 * boundary concern); this pass fills them in from field names and types so a domain
 * definition needs only {@code data}/{@code invariant}/{@code behavior}. Conventions:
 * JSON key = field name; single-primitive-field data is a newtype (bare primitive);
 * sum discriminator field = "type", tag = arm name.
 */
public final class Deriver {

    private Deriver() {}

    public static Ast.Module derive(Ast.Module module) {
        Map<String, Ast.Def> symbols = TypeChecker.symbols(module);
        java.util.Set<String> armNames = new java.util.HashSet<>();
        for (Ast.Def def : module.defs()) {
            if (def instanceof Ast.SumData s) {
                armNames.addAll(s.arms());
            }
        }
        List<Ast.Def> defs = new ArrayList<>();
        for (Ast.Def def : module.defs()) {
            defs.add(switch (def) {
                case Ast.Data d -> deriveData(d, symbols, armNames.contains(d.name()));
                case Ast.SumData s -> deriveSum(s);
                case Ast.UnitData u -> u;
            });
        }
        return new Ast.Module(module.name(), defs, module.behaviors(), module.requireds(), module.pos());
    }

    private static Ast.Data deriveData(Ast.Data d, Map<String, Ast.Def> symbols, boolean isArm) {
        Map<String, Type> fields = TypeChecker.fieldTypes(d, symbols);
        Optional<Ast.DecoderDef> decoder = d.decoder().isPresent()
                ? d.decoder() : Optional.of(deriveDecoder(d, fields, isArm));
        Optional<Ast.EncoderDef> encoder = d.encoder().isPresent()
                ? d.encoder() : Optional.of(deriveEncoder(d, fields, isArm));
        return new Ast.Data(d.name(), d.includes(), d.fields(), d.invariant(), decoder, encoder, d.pos());
    }

    // --- decoder derivation ---

    private static Ast.DecoderDef deriveDecoder(Ast.Data d, Map<String, Type> fields, boolean isArm) {
        SourcePos pos = d.pos();
        // a sum arm is embedded in the discriminated object, so it must decode from Object,
        // not as a bare primitive newtype
        Map.Entry<String, Type> single = isArm ? null : singlePrimField(fields);
        if (single != null) {
            Ast.RawKind kind = rawKind(single.getValue());
            Ast.Construct result = new Ast.Construct(d.name(),
                    List.of(new Ast.FieldInit(single.getKey(), new Ast.Var("__in", pos), pos)),
                    List.of(), pos);
            return new Ast.PrimDecoder(kind, "__in", List.of(), result, pos);
        }
        List<Ast.Bind> binds = new ArrayList<>();
        List<Ast.FieldInit> inits = new ArrayList<>();
        for (Map.Entry<String, Type> f : fields.entrySet()) {
            binds.add(new Ast.Bind(f.getKey(), f.getKey(), decRef(f.getValue(), d, pos), pos));
            inits.add(new Ast.FieldInit(f.getKey(), new Ast.Var(f.getKey(), pos), pos));
        }
        return new Ast.ObjectDecoder(binds, new Ast.Construct(d.name(), inits, List.of(), pos), pos);
    }

    private static Ast.RawKind rawKind(Type t) {
        if (t == Type.STRING) {
            return Ast.RawKind.TEXT;
        }
        if (t == Type.BOOL) {
            return Ast.RawKind.BOOL;
        }
        if (t == Type.DECIMAL) {
            return Ast.RawKind.DECIMAL;
        }
        if (t == Type.DATE) {
            return Ast.RawKind.DATE;
        }
        if (t == Type.DATETIME) {
            return Ast.RawKind.DATETIME;
        }
        return Ast.RawKind.INT;
    }

    private static Ast.PrimKind primKind(Type t) {
        if (t == Type.STRING) {
            return Ast.PrimKind.STRING;
        }
        if (t == Type.BOOL) {
            return Ast.PrimKind.BOOL;
        }
        if (t == Type.DECIMAL) {
            return Ast.PrimKind.DECIMAL;
        }
        if (t == Type.DATE) {
            return Ast.PrimKind.DATE;
        }
        if (t == Type.DATETIME) {
            return Ast.PrimKind.DATETIME;
        }
        return Ast.PrimKind.INT;
    }

    private static Ast.DecRef decRef(Type t, Ast.Data d, SourcePos pos) {
        if (t == Type.STRING || t == Type.INT || t == Type.BOOL
                || t == Type.DECIMAL || t == Type.DATE || t == Type.DATETIME) {
            return new Ast.PrimDecRef(primKind(t), pos);
        }
        if (t instanceof Type.Ref r) {
            return new Ast.DataDecRef(r.name(), pos);
        }
        if (t instanceof Type.ListOf lo) {
            return new Ast.ListDecRef(decRef(lo.element(), d, pos), pos);
        }
        throw new CompileException(pos,
                "cannot derive a decoder for field type " + t + " in `" + d.name() + "`");
    }

    // --- encoder derivation ---

    private static Ast.EncoderDef deriveEncoder(Ast.Data d, Map<String, Type> fields, boolean isArm) {
        SourcePos pos = d.pos();
        Map.Entry<String, Type> single = isArm ? null : singlePrimField(fields);
        if (single != null) {
            Ast.Expr access = new Ast.FieldAccess(new Ast.Var("self", pos), single.getKey(), pos);
            return new Ast.EncoderDef("self", primRaw(single.getValue(), access, pos), pos);
        }
        List<Ast.RawEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Type> f : fields.entrySet()) {
            entries.add(new Ast.RawEntry(f.getKey(), rawFor(f.getValue(), f.getKey(), d, pos), pos));
        }
        return new Ast.EncoderDef("self", new Ast.ObjectRaw(entries, pos), pos);
    }

    private static Ast.RawExpr primRaw(Type t, Ast.Expr access, SourcePos pos) {
        if (t == Type.STRING) {
            return new Ast.TextRaw(access, pos);
        }
        if (t == Type.BOOL) {
            return new Ast.BoolRaw(access, pos);
        }
        if (t == Type.DECIMAL) {
            return new Ast.DecimalRaw(access, pos);
        }
        if (t == Type.DATE || t == Type.DATETIME) {
            return new Ast.IsoTextRaw(access, pos);
        }
        return new Ast.IntRaw(access, pos);
    }

    private static boolean isPrim(Type t) {
        return t == Type.STRING || t == Type.INT || t == Type.BOOL
                || t == Type.DECIMAL || t == Type.DATE || t == Type.DATETIME;
    }

    private static Ast.RawExpr rawFor(Type t, String field, Ast.Data d, SourcePos pos) {
        Ast.Expr access = new Ast.FieldAccess(new Ast.Var("self", pos), field, pos);
        if (isPrim(t)) {
            return primRaw(t, access, pos);
        }
        if (t instanceof Type.Ref r) {
            return new Ast.EncodeRaw(r.name(), access, pos);
        }
        if (t instanceof Type.ListOf lo) {
            return new Ast.ListEnc(access, encElem(lo.element(), d, pos), pos);
        }
        throw new CompileException(pos,
                "cannot derive an encoder for field type " + t + " in `" + d.name() + "`");
    }

    private static Ast.EncElem encElem(Type t, Ast.Data d, SourcePos pos) {
        if (t == Type.STRING) {
            return new Ast.PrimEnc(Ast.PrimKind.STRING, pos);
        }
        if (t == Type.INT) {
            return new Ast.PrimEnc(Ast.PrimKind.INT, pos);
        }
        if (t instanceof Type.Ref r) {
            return new Ast.DataEnc(r.name(), pos);
        }
        throw new CompileException(pos,
                "cannot derive a list-element encoder for " + t + " in `" + d.name() + "`");
    }

    // --- sum derivation ---

    private static Ast.SumData deriveSum(Ast.SumData s) {
        Optional<Ast.Discriminate> decoder = s.decoder().isPresent()
                ? s.decoder()
                : Optional.of(new Ast.Discriminate("type", tagVariants(s), s.pos()));
        Optional<Ast.SumEncoder> encoder = s.encoder().isPresent()
                ? s.encoder()
                : Optional.of(new Ast.SumEncoder("type", encVariants(s), s.pos()));
        return new Ast.SumData(s.name(), s.arms(), decoder, encoder, s.pos());
    }

    private static List<Ast.Variant> tagVariants(Ast.SumData s) {
        List<Ast.Variant> variants = new ArrayList<>();
        for (String arm : s.arms()) {
            variants.add(new Ast.Variant(arm, arm, s.pos()));
        }
        return variants;
    }

    private static List<Ast.EncVariant> encVariants(Ast.SumData s) {
        List<Ast.EncVariant> variants = new ArrayList<>();
        for (String arm : s.arms()) {
            variants.add(new Ast.EncVariant(arm, arm, s.pos()));
        }
        return variants;
    }

    private static Map.Entry<String, Type> singlePrimField(Map<String, Type> fields) {
        if (fields.size() != 1) {
            return null;
        }
        Map.Entry<String, Type> only = fields.entrySet().iterator().next();
        return isPrim(only.getValue()) ? only : null;
    }
}
