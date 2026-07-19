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
 * sum discriminator field = "type", tag = case name.
 */
public final class Deriver {

    private Deriver() {}

    public static Ast.Module derive(Ast.Module module) {
        return derive(module, TypeChecker.symbols(module));
    }

    /** Derives codecs using {@code symbols} for type resolution (own definitions plus any
     * imported ones, for cross-module fields — spec 4). */
    public static Ast.Module derive(Ast.Module module, Map<String, Ast.Def> symbols) {
        java.util.Set<String> caseNames = new java.util.HashSet<>();
        for (Ast.Def def : symbols.values()) {
            if (def instanceof Ast.SumData s) {
                caseNames.addAll(s.cases());
            }
        }
        List<Ast.Def> defs = new ArrayList<>();
        for (Ast.Def def : module.defs()) {
            defs.add(switch (def) {
                case Ast.Data d -> deriveData(d, symbols, caseNames.contains(d.name()));
                case Ast.SumData s -> deriveSum(s, symbols);
                case Ast.UnitData u -> u;
            });
        }
        return new Ast.Module(module.name(), module.exposing(), module.exposedOutputs(),
                module.imports(), defs, module.behaviors(), module.fns(), module.pos());
    }

    private static Ast.Data deriveData(Ast.Data d, Map<String, Ast.Def> symbols, boolean isCase) {
        Map<String, Type> fields = TypeChecker.fieldTypes(d, symbols);
        Optional<Ast.DecoderDef> decoder = d.decoder().isPresent()
                ? d.decoder() : Optional.of(deriveDecoder(d, fields, isCase));
        Optional<Ast.EncoderDef> encoder = d.encoder().isPresent()
                ? d.encoder() : Optional.of(deriveEncoder(d, fields, isCase));
        return new Ast.Data(d.name(), d.newtype(), d.includes(), d.fields(), d.invariant(),
                decoder, encoder, d.pos());
    }

    // --- decoder derivation ---

    private static Ast.DecoderDef deriveDecoder(Ast.Data d, Map<String, Type> fields, boolean isCase) {
        SourcePos pos = d.pos();
        // only an explicit newtype `data X = Y` is bare; a braced record is always an object, even
        // with one field (spec 8.7). A sum case is embedded in the discriminated object, never bare.
        Map.Entry<String, Type> single = bareField(d, fields, isCase);
        if (single != null) {
            Ast.RawKind kind = rawKind(single.getValue());
            Ast.Construct result = new Ast.Construct(d.name(),
                    List.of(new Ast.FieldInit(single.getKey(), new Ast.Var("__in", pos), pos)),
                    List.of(), pos);
            return new Ast.PrimDecoder(kind, "__in", List.of(), result, pos);
        }
        // a newtype over a non-primitive Y delegates the whole input to Y's decoder (spec 8.7)
        if (d.newtype() && !isCase) {
            Map.Entry<String, Type> only = fields.entrySet().iterator().next();
            Ast.Construct result = new Ast.Construct(d.name(),
                    List.of(new Ast.FieldInit(only.getKey(), new Ast.Var("__in", pos), pos)),
                    List.of(), pos);
            return new Ast.NewtypeDecoder(decRef(only.getValue(), d, pos), "__in", result, pos);
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
        if (t instanceof Type.OptionOf oo) {
            return new Ast.OptionDecRef(decRef(oo.element(), d, pos), pos);
        }
        if (t instanceof Type.MapOf mo) {
            return new Ast.MapDecRef(decRef(mo.value(), d, pos), pos);
        }
        if (t instanceof Type.TupleOf) {
            throw new CompileException(pos, "a tuple cannot be a data field in `" + d.name()
                    + "`: a tuple has no external representation, so no decoder can be derived"
                    + " (ADR-0036). Use a named data.");
        }
        throw new CompileException(pos,
                "cannot derive a decoder for field type " + t + " in `" + d.name() + "`");
    }

    // --- encoder derivation ---

    private static Ast.EncoderDef deriveEncoder(Ast.Data d, Map<String, Type> fields, boolean isCase) {
        SourcePos pos = d.pos();
        Map.Entry<String, Type> single = bareField(d, fields, isCase);
        if (single != null) {
            Ast.Expr access = new Ast.FieldAccess(new Ast.Var("self", pos), single.getKey(), pos);
            return new Ast.EncoderDef("self", primRaw(single.getValue(), access, pos), pos);
        }
        // a newtype over a non-primitive Y encodes self.value via Y's encoder — Y's representation,
        // not `{value: ...}` (spec 8.7)
        if (d.newtype() && !isCase) {
            Map.Entry<String, Type> only = fields.entrySet().iterator().next();
            Ast.Expr access = new Ast.FieldAccess(new Ast.Var("self", pos), only.getKey(), pos);
            String inner = ((Type.Ref) only.getValue()).name();
            return new Ast.EncoderDef("self", new Ast.EncodeRaw(inner, access, pos), pos);
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
        return rawForAccess(t, new Ast.FieldAccess(new Ast.Var("self", pos), field, pos), d, pos);
    }

    private static Ast.RawExpr rawForAccess(Type t, Ast.Expr access, Ast.Data d, SourcePos pos) {
        if (isPrim(t)) {
            return primRaw(t, access, pos);
        }
        if (t instanceof Type.Ref r) {
            return new Ast.EncodeRaw(r.name(), access, pos);
        }
        if (t instanceof Type.ListOf lo) {
            return new Ast.ListEnc(access, encElem(lo.element(), d, pos), pos);
        }
        if (t instanceof Type.OptionOf oo) {
            String elemVar = "$opt";
            Ast.RawExpr inner = rawForAccess(oo.element(), new Ast.Var(elemVar, pos), d, pos);
            return new Ast.OptionRaw(access, inner, elemVar, pos);
        }
        if (t instanceof Type.MapOf mo) {
            return new Ast.MapEnc(access, encElem(mo.value(), d, pos), pos);
        }
        if (t instanceof Type.TupleOf) {
            throw new CompileException(pos, "a tuple cannot be a data field in `" + d.name()
                    + "`: a tuple has no external representation, so no encoder can be derived"
                    + " (ADR-0036). Use a named data.");
        }
        throw new CompileException(pos,
                "cannot derive an encoder for field type " + t + " in `" + d.name() + "`");
    }

    private static Ast.EncElem encElem(Type t, Ast.Data d, SourcePos pos) {
        if (isPrim(t)) {
            return new Ast.PrimEnc(primKind(t), pos);   // every primitive has a leaf encoder
        }
        if (t instanceof Type.Ref r) {
            return new Ast.DataEnc(r.name(), pos);
        }
        throw new CompileException(pos,
                "cannot derive a list-element encoder for " + t + " in `" + d.name() + "`");
    }

    // --- sum derivation ---

    private static Ast.SumData deriveSum(Ast.SumData s, Map<String, Ast.Def> symbols) {
        List<String> leaves = leafCases(s, symbols);
        Optional<Ast.Discriminate> decoder = s.decoder().isPresent()
                ? s.decoder()
                : Optional.of(new Ast.Discriminate("type", tagVariants(s, leaves), s.pos()));
        Optional<Ast.SumEncoder> encoder = s.encoder().isPresent()
                ? s.encoder()
                : Optional.of(new Ast.SumEncoder("type", encVariants(s, leaves), s.pos()));
        return new Ast.SumData(s.name(), s.cases(), decoder, encoder, s.pos());
    }

    /**
     * The cases a derived codec dispatches over, with nested sums folded to their leaves —
     * `費用負担区分 = 自社負担 | 先方負担` where `自社負担 = 立替 | 仮払い | 会社カード` dispatches over
     * 立替 / 仮払い / 会社カード / 先方負担 (spec 8.3, 10.3).
     *
     * <p>Folding is what makes a nested sum round-trip. Tagging the direct case instead would put
     * two levels on one `"type"` key: the outer encoder wrote {@code {type: 自社負担}}, losing
     * which leaf it was, and the inner decoder then rejected that same tag.
     */
    private static List<String> leafCases(Ast.SumData s, Map<String, Ast.Def> symbols) {
        List<String> leaves = new ArrayList<>();
        collectLeafCases(s, symbols, leaves);
        return leaves;
    }

    private static void collectLeafCases(Ast.SumData s, Map<String, Ast.Def> symbols, List<String> out) {
        for (String caseName : s.cases()) {
            if (symbols.get(caseName) instanceof Ast.SumData nested) {
                collectLeafCases(nested, symbols, out);
            } else if (!out.contains(caseName)) {
                out.add(caseName);
            }
        }
    }

    private static List<Ast.Variant> tagVariants(Ast.SumData s, List<String> cases) {
        List<Ast.Variant> variants = new ArrayList<>();
        for (String caseName : cases) {
            variants.add(new Ast.Variant(caseName, caseName, s.pos()));
        }
        return variants;
    }

    private static List<Ast.EncVariant> encVariants(Ast.SumData s, List<String> cases) {
        List<Ast.EncVariant> variants = new ArrayList<>();
        for (String caseName : cases) {
            variants.add(new Ast.EncVariant(caseName, caseName, s.pos()));
        }
        return variants;
    }

    /**
     * The bare inner field of an explicit newtype {@code data X = Y} whose {@code Y} is primitive
     * (spec 8.7). A braced record is always an object — even a single-field one — so newtype-ness
     * is decided by the {@code = Y} syntax, not the shape; a sum case is never bare either.
     */
    private static Map.Entry<String, Type> bareField(Ast.Data d, Map<String, Type> fields, boolean isCase) {
        if (!d.newtype() || isCase) {
            return null;
        }
        Map.Entry<String, Type> only = fields.entrySet().iterator().next();
        return isPrim(only.getValue()) ? only : null;
    }
}
