package souther.compiler.derive;

import souther.compiler.check.DeclarationKinds;
import souther.compiler.check.PublishedDeclarations;
import souther.compiler.check.Symbols;
import souther.compiler.diag.SourcePos;
import souther.compiler.ast.Hir;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.LeafScalar;
import souther.compiler.types.Type;
import souther.compiler.check.TypeOps;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Derives the default boundary representation of a product from the shape of the domain data.
 * Decoders/encoders are not part of the domain syntax (they are a boundary concern); this reads one
 * off field names and types so a domain definition needs only {@code data}/{@code invariant}/
 * {@code behavior}. Conventions: JSON key = field name; single-primitive-field data is a newtype
 * (bare primitive).
 *
 * <p>A product only. What a sum's alternatives are called at the boundary and the form they travel
 * in are derived too, but they are derived where they are read ({@code check.Boundary}) rather than
 * carried on the declaration, and a unit has none to carry (#994).
 *
 * <p>The algorithm and not the stage. What holds a derived representation is
 * {@code check.Derived.Data}, which is reached by deriving a declaration; this answers the question
 * that operation asks.
 */
public final class Deriver {

    private Deriver() {}

    /**
     * The decoder and the encoder of one product, which are two halves of one reading and are
     * carried together for that reason.
     */
    public record Codecs(Hir.DecoderDef decoder, Hir.EncoderDef encoder) {

        public Codecs {
            java.util.Objects.requireNonNull(decoder, "a derived representation reads");
            java.util.Objects.requireNonNull(encoder, "a derived representation writes");
        }
    }

    /**
     * The boundary representation of {@code d}, or null where a field of it carries, anywhere in
     * its type, a type nobody could name.
     *
     * <p>Null and not a report. A field whose type nobody could name was reported where the name is
     * written, and saying it again here would say the same thing twice; what a caller does with the
     * absence is answer nothing about the declaration, which is what a module holding one is worth.
     *
     * <p>Only that leaf is left unsaid. The walk meets the unnamed type where it sits, so what
     * stands outside it — a tuple around it, a map keyed by something no map that crosses may be
     * keyed by — is refused first, in the order the walk refuses everything else: {@code (T, Int)}
     * with {@code T} unnamed is refused as a tuple, and {@code Map<Int, T>} for its key. What is
     * absorbed is the one report there would be about {@code T} itself, which was already made.
     */
    public static Codecs derive(Hir.Data d, Symbols symbols, DeclarationKinds kinds,
                                PublishedDeclarations published) {
        Map<String, Type> fields = TypeOps.fieldTypes(d, symbols);
        // One walk decides what each field carries, and the decoder and the encoder are both lowered
        // from it. Asked separately they would agree only by coincidence: a builder with an arm the
        // other lacks reports the shape it did not implement as one the language refuses, which is
        // how an unimplemented case comes to look like a rule (see CodecShape).
        Map<String, CodecShape> shapes = new LinkedHashMap<>();
        try {
            for (Map.Entry<String, Type> f : fields.entrySet()) {
                shapes.put(f.getKey(), CodecShape.of(f.getValue(), d, f.getKey(),
                        fieldPos(d, f.getKey()), symbols, kinds, published));
            }
        } catch (CodecShape.Unnamed _) {
            return null;
        }
        // the decoder and the encoder each read the value under a name of their own, so each owns
        // the bindings it writes rather than sharing the declaration's
        BindingOwner declared = new BindingOwner.OfData(d.declares());
        Hir.DecoderDef decoder = deriveDecoder(d, shapes,
                new Hir.Binders(new BindingOwner.Synthesized(declared,
                        BindingOwner.Pass.DERIVER, 0)));
        Hir.EncoderDef encoder = deriveEncoder(d, shapes,
                new Hir.Binders(new BindingOwner.Synthesized(declared,
                        BindingOwner.Pass.DERIVER, 1)));
        return new Codecs(decoder, encoder);
    }

    // --- decoder derivation ---

    private static Hir.DecoderDef deriveDecoder(Hir.Data d, Map<String, CodecShape> shapes,
                                               Hir.Binders binders) {
        SourcePos pos = d.pos();
        Hir.Name self = Hir.Name.resolved(d.declares(), pos);
        // only an explicit newtype `data X = Y` is bare; a braced record is always an object, even
        // with one field (spec §newtype).
        Map.Entry<String, CodecShape> single = bareField(d, shapes);
        if (single != null) {
            Hir.RawKind kind = rawKind(((CodecShape.Scalar) single.getValue()).kind());
            Hir.Binder input = binders.binder("__in", pos);
            Hir.Construct result = new Hir.Construct(self,
                    List.of(new Hir.FieldInit(single.getKey(), Hir.Var.local(input, pos), pos)), pos);
            return new Hir.PrimDecoder(kind, input, List.of(), result, pos);
        }
        // a newtype over a non-primitive Y delegates the whole input to Y's decoder (spec §newtype)
        if (d.newtype()) {
            Map.Entry<String, CodecShape> only = shapes.entrySet().iterator().next();
            Hir.Binder input = binders.binder("__in", pos);
            Hir.Construct result = new Hir.Construct(self,
                    List.of(new Hir.FieldInit(only.getKey(), Hir.Var.local(input, pos), pos)), pos);
            return new Hir.NewtypeDecoder(decRef(only.getValue(), fieldPos(d, only.getKey())),
                    input, result, pos);
        }
        List<Hir.Bind> binds = new ArrayList<>();
        List<Hir.FieldInit> inits = new ArrayList<>();
        for (Map.Entry<String, CodecShape> f : shapes.entrySet()) {
            Hir.Binder took = binders.binder(f.getKey(), pos);
            binds.add(new Hir.Bind(took, f.getKey(),
                    decRef(f.getValue(), fieldPos(d, f.getKey())), pos));
            inits.add(new Hir.FieldInit(f.getKey(), Hir.Var.local(took, pos), pos));
        }
        return new Hir.ObjectDecoder(binds, new Hir.Construct(self, inits, pos), pos);
    }

    /** How a bare newtype's single scalar is read straight off the input. */
    private static Hir.RawKind rawKind(LeafScalar kind) {
        return switch (kind) {
            case STRING -> Hir.RawKind.TEXT;
            case BOOL -> Hir.RawKind.BOOL;
            case DECIMAL -> Hir.RawKind.DECIMAL;
            case DATE -> Hir.RawKind.DATE;
            case TIME -> Hir.RawKind.TIME;
            case DATETIME -> Hir.RawKind.DATETIME;
            case INSTANT -> Hir.RawKind.INSTANT;
            case INT -> Hir.RawKind.INT;
        };
    }

    // --- lowering a shape to the decoder IR ---

    /**
     * The decoder for a shape. This and {@link #bareDecRef} are the same walk split where
     * {@link CodecShape} splits, so the two trees agree by construction rather than by a cast: what
     * an optional holds is a {@code Bare} shape and lowers to a {@code Bare} reference.
     */
    private static Hir.DecRef decRef(CodecShape s, SourcePos pos) {
        return switch (s) {
            case CodecShape.Bare b -> bareDecRef(b, pos);
            case CodecShape.OptionOf o -> new Hir.OptionDecRef(bareDecRef(o.present(), pos), pos);
        };
    }

    private static Hir.DecRef.Bare bareDecRef(CodecShape.Bare s, SourcePos pos) {
        return switch (s) {
            case CodecShape.Scalar sc -> new Hir.PrimDecRef(sc.kind(), pos);
            case CodecShape.Named n -> new Hir.DataDecRef(Hir.Name.resolved(n.name(), pos), pos);
            case CodecShape.ListOf l -> new Hir.ListDecRef(decRef(l.element(), pos), pos);
            case CodecShape.SetOf st -> new Hir.SetDecRef(decRef(st.element(), pos), pos);
            case CodecShape.MapOf m -> new Hir.MapDecRef(decRef(m.value(), pos), m.key().representation(), pos);
        };
    }

    // --- encoder derivation ---

    private static Hir.EncoderDef deriveEncoder(Hir.Data d, Map<String, CodecShape> shapes,
                                                Hir.Binders binders) {
        SourcePos pos = d.pos();
        Hir.Binder self = binders.binder("self", pos);
        Map.Entry<String, CodecShape> single = bareField(d, shapes);
        if (single != null) {
            Hir.Expr access = new Hir.FieldAccess(Hir.Var.local(self, pos), single.getKey(), pos);
            return new Hir.EncoderDef(self,
                    primRaw(((CodecShape.Scalar) single.getValue()).kind(), access, pos), pos);
        }
        // a newtype over a non-primitive Y encodes self.value as Y writes itself — Y's
        // representation, not `{value: ...}` (spec §newtype). Y is a named data, a sum, or a collection,
        // so this is the same choice a field of type Y makes.
        if (d.newtype()) {
            Map.Entry<String, CodecShape> only = shapes.entrySet().iterator().next();
            Hir.Expr access = new Hir.FieldAccess(Hir.Var.local(self, pos), only.getKey(), pos);
            return new Hir.EncoderDef(self,
                    rawForAccess(only.getValue(), access, fieldPos(d, only.getKey()), binders), pos);
        }
        List<Hir.RawEntry> entries = new ArrayList<>();
        for (Map.Entry<String, CodecShape> f : shapes.entrySet()) {
            Hir.Expr access = new Hir.FieldAccess(Hir.Var.local(self, pos), f.getKey(), pos);
            entries.add(new Hir.RawEntry(f.getKey(),
                    rawForAccess(f.getValue(), access, fieldPos(d, f.getKey()), binders), pos));
        }
        return new Hir.EncoderDef(self, new Hir.ObjectRaw(entries, pos), pos);
    }

    private static Hir.RawExpr primRaw(LeafScalar kind, Hir.Expr access, SourcePos pos) {
        return switch (kind) {
            case STRING -> new Hir.TextRaw(access, pos);
            case BOOL -> new Hir.BoolRaw(access, pos);
            case DECIMAL -> new Hir.DecimalRaw(access, pos);
            case DATE, TIME, DATETIME, INSTANT -> new Hir.IsoTextRaw(access, pos);
            case INT -> new Hir.IntRaw(access, pos);
        };
    }

    /**
     * What a field writes. An optional here stands under a key, so absence omits the key rather than
     * writing {@code null} — the form a value position takes is {@link #encElem}'s
     * (spec {@code [#absence-is-written-as-null]}).
     */
    private static Hir.RawExpr rawForAccess(CodecShape s, Hir.Expr access, SourcePos pos,
                                            Hir.Binders binders) {
        return switch (s) {
            case CodecShape.Scalar sc -> primRaw(sc.kind(), access, pos);
            case CodecShape.Named n -> new Hir.EncodeRaw(Hir.Name.resolved(n.name(), pos), access, pos);
            case CodecShape.ListOf l -> new Hir.ListEnc(access, encElem(l.element(), pos), pos);
            case CodecShape.SetOf st -> new Hir.SetEnc(access, encElem(st.element(), pos), pos);
            case CodecShape.MapOf m -> new Hir.MapEnc(access, encElem(m.value(), pos), m.key().representation(), pos);
            case CodecShape.OptionOf o -> {
                Hir.Binder elem = binders.binder("$opt", pos);
                yield new Hir.OptionRaw(access,
                        rawForAccess(o.present(), Hir.Var.local(elem, pos), pos, binders), elem, pos);
            }
        };
    }

    /**
     * The encoder for one member of a collection, where there is no key to omit: an absent one is
     * written {@code null}. A collection may hold a collection
     * ({@code Map<String, List<商品ID>>}), so this recurses the way {@link #decRef} does on the
     * decoding side — the two stay symmetric, and what decodes in encodes back out.
     */
    private static Hir.EncElem encElem(CodecShape s, SourcePos pos) {
        return switch (s) {
            case CodecShape.Bare b -> bareEncElem(b, pos);
            case CodecShape.OptionOf o -> new Hir.OptionElemEnc(bareEncElem(o.present(), pos), pos);
        };
    }

    private static Hir.EncElem.Bare bareEncElem(CodecShape.Bare s, SourcePos pos) {
        return switch (s) {
            case CodecShape.Scalar sc -> new Hir.PrimEnc(sc.kind(), pos);
            case CodecShape.Named n -> new Hir.DataEnc(Hir.Name.resolved(n.name(), pos), pos);
            case CodecShape.ListOf l -> new Hir.ListElemEnc(encElem(l.element(), pos), pos);
            case CodecShape.SetOf st -> new Hir.SetElemEnc(encElem(st.element(), pos), pos);
            case CodecShape.MapOf m -> new Hir.MapElemEnc(encElem(m.value(), pos), m.key().representation(), pos);
        };
    }

    /** Where the field was written, for a diagnostic to point at. A field a data takes in through
     * {@code ...Included} was written elsewhere, so that one falls back to the data itself. */
    private static SourcePos fieldPos(Hir.Data d, String field) {
        for (Hir.Field f : d.fields()) {
            if (f.name().equals(field)) {
                return f.pos();
            }
        }
        return d.pos();
    }

    /**
     * The bare inner field of an explicit newtype {@code data X = Y} whose {@code Y} is primitive
     * (spec §newtype). A braced record is always an object — even a single-field one — so newtype-ness
     * is decided by the {@code = Y} syntax, not the shape.
     *
     * <p>What some other declaration does with the type is not asked. A derived codec is the
     * standalone representation, and the {@code "value"} envelope a sum's case wears is what the
     * sum's own encoding adds to it.
     */
    private static Map.Entry<String, CodecShape> bareField(Hir.Data d, Map<String, CodecShape> shapes) {
        if (!d.newtype()) {
            return null;
        }
        Map.Entry<String, CodecShape> only = shapes.entrySet().iterator().next();
        return only.getValue() instanceof CodecShape.Scalar ? only : null;
    }
}
