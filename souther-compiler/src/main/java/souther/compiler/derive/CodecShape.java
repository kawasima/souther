package souther.compiler.derive;

import souther.compiler.ast.Hir;
import souther.compiler.check.CrossingMapKey;
import souther.compiler.check.CrossingNominal;
import souther.compiler.check.DeclarationKinds;
import souther.compiler.check.PublishedDeclarations;
import souther.compiler.check.Symbols;
import souther.compiler.check.TypeOps;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.SourcePos;
import souther.compiler.diag.msg.DataMessage;
import souther.compiler.diag.msg.TypeMessage;
import souther.compiler.types.LeafScalar;
import souther.compiler.types.MapKeyRepresentation;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;

/**
 * What a field carries across the boundary, as the shape a codec is built out of.
 *
 * <p>Reading a type and admitting it are one walk, for the reason {@code SignatureBoundary} gives on
 * the behavior's side: asked separately, what the checker admits and what the builders can construct
 * agree only by coincidence, and a builder missing an arm then reports the type it did not implement
 * as a type the language refuses. A shape exists because {@link #of} built it, and both the decoder
 * and the encoder are lowered from that one shape rather than each deciding again.
 *
 * <p>This walk and a behavior's are not one walk, and are not asked to be. What an optional, a union
 * or a type variable means differs by position — a field may hold an optional and a parameter may
 * not — and each walk owns how it words a refusal, which is what makes the report the one the author
 * can act on. What they share is a single proposition: a named type that crosses is one a model
 * declares, held by {@link CrossingNominal} and established once, so a field cannot reach a codec
 * through a name a signature would have refused.
 *
 * <p>The split between this and {@link Bare} is the one rule the tree itself holds: an optional's
 * value is a {@code Bare}, so an optional under an optional cannot be constructed. Absence has one
 * form wherever it stands (spec {@code [#absence-is-written-as-null]}), so {@code None},
 * {@code Some(None)} and {@code Some(Some(v))} are three values facing two forms; leaving the
 * recursion open would give this tree an admitted set wider than the boundary has representations
 * for, which is the disagreement the type is here to make unwritable.
 */
sealed interface CodecShape {

    /**
     * A shape that is not an optional. Everything but an optional is one, and what the distinction
     * is for is the single position that requires it: what an optional holds.
     */
    sealed interface Bare extends CodecShape {}

    /** A primitive written as itself. */
    record Scalar(LeafScalar kind) implements Bare {}

    /** A named data, read and written by its own derived codec. Holds the admission rather than the
     *  name: a field crosses, so the name in it is one a model declares, and a shape that could be
     *  built from any {@link TypeSymbol} would name a codec by hoping one is there. */
    record Named(CrossingNominal admitted) implements Bare {

        /** Which declaration the codec is reached through. */
        TypeSymbol name() {
            return admitted.name();
        }
    }

    /** A {@code List<T>}, written as an array. */
    record ListOf(CodecShape element) implements Bare {}

    /** A {@code Set<T>}, written as an array its own members order (spec {@code [#collections]}). */
    record SetOf(CodecShape element) implements Bare {}

    /** A {@code Map<K, V>}, written as an object whose keys are {@code key}'s bare text. */
    record MapOf(CrossingMapKey key, CodecShape value) implements Bare {}

    /** An {@code Option<T>}. Where it stands decides how absence is written — a field omits its key,
     *  and an element or a map's value writes {@code null} — so the shape says only that absence is
     *  possible and the lowering says which form it takes. */
    record OptionOf(Bare present) implements CodecShape {}

    /**
     * The shape {@code t} carries, or a refusal naming what had no external representation.
     *
     * @param t      the field's type, as the checker resolved it
     * @param d      the data the field belongs to, for naming the refusal
     * @param field  the field's name
     * @param pos    where the field is written, for the caret
     */
    static CodecShape of(Type t, Hir.Data d, String field, SourcePos pos, Symbols symbols,
                         DeclarationKinds kinds, PublishedDeclarations published) {
        return switch (t) {
            case Type.Prim p -> scalar(p, t, d, field, pos);
            case Type.Ref r -> new Named(nominal(r.name(), d, field, pos, symbols));
            case Type.ListOf l -> new ListOf(of(l.element(), d, field, pos, symbols, kinds, published));
            case Type.SetOf s -> new SetOf(of(s.element(), d, field, pos, symbols, kinds, published));
            case Type.MapOf m -> new MapOf(mapKey(m, d, field, pos, symbols, kinds, published),
                    of(m.value(), d, field, pos, symbols, kinds, published));
            case Type.OptionOf o -> new OptionOf(present(o, d, field, pos, symbols, kinds, published));
            case Type.TupleOf _ -> throw aTuple(t, d, field, pos);
            // Not a refusal: a type nobody could name was reported where the name is written, and
            // a shape cannot be built over it. Met here and not before the walk, so that what stands
            // outside it in the type — a tuple, a map's key — is refused in the order the walk
            // refuses everything else, and only this leaf is left unsaid.
            case Type.Erroneous _ -> throw new Unnamed();
            case Type.FnOf _, Type.Union _,
                 Type.Var _, Type.MetaVar _, Type.Nothing _, Type.Never _ ->
                    throw noRepresentation(t, d, field, pos);
        };
    }

    /**
     * The walk met a type nobody could name. Carries nothing and no stack: it is how the absence of
     * a shape leaves the walk, and {@link Deriver#derive} is where it lands and becomes the absence
     * of a representation.
     */
    final class Unnamed extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Unnamed() {
            super(null, null, false, false);
        }
    }

    /** What an optional holds, which is not another optional. */
    private static Bare present(Type.OptionOf o, Hir.Data d, String field, SourcePos pos,
                                Symbols symbols, DeclarationKinds kinds,
                                PublishedDeclarations published) {
        // The inner type is read before it is judged, so a tuple under an optional is reported as
        // the tuple it is rather than as the optional carrying one.
        if (of(o.element(), d, field, pos, symbols, kinds, published) instanceof Bare b) {
            return b;
        }
        throw noRepresentation(o, d, field, pos);
    }

    /** The scalar a primitive is written as. {@code Raw} is the language's own vocabulary and is
     *  written as itself nowhere, so it has no leaf codec to be given one. */
    private static Scalar scalar(Type.Prim prim, Type t, Hir.Data d, String field, SourcePos pos) {
        LeafScalar leaf = LeafScalar.of(prim);
        if (leaf == null) {
            throw noRepresentation(t, d, field, pos);
        }
        return new Scalar(leaf);
    }

    /**
     * The name a field is written from, where a model declares it.
     *
     * <p>A field crosses, so the rule is the one a behavior's boundary is held to and the words are
     * this position's: what the author has to change is the field, and a codec for the name would not
     * make the name theirs to publish (spec {@code [#a-boundary-carries-the-models-own-vocabulary]}).
     */
    private static CrossingNominal nominal(TypeSymbol name, Hir.Data d, String field, SourcePos pos,
                                           Symbols symbols) {
        CrossingNominal admitted = CrossingNominal.admitted(name, symbols);
        if (admitted == null) {
            throw foreignName(name, d, field, pos);
        }
        return admitted;
    }

    /**
     * How a map's keys cross. A JSON object's keys are strings, so a key is decoded from and encoded
     * to a bare string, and which string is the checker's answer rather than one worked out here. A
     * key with no classification never had a boundary representation (ADR-0040).
     *
     * <p>Two questions and two refusals. Having a representation is not being admitted: an
     * enumeration the language declares of its own operations classifies as a key and is still not a
     * word this model publishes, so a key that names a type is asked the crossing rule as well.
     */
    private static CrossingMapKey mapKey(Type.MapOf m, Hir.Data d, String field, SourcePos pos,
                                         Symbols symbols, DeclarationKinds kinds,
                                         PublishedDeclarations published) {
        MapKeyRepresentation key =
                TypeOps.classifyConcreteMapKey(m.key(), symbols, kinds, published);
        if (key == null) {
            throw badMapKey(m.key(), d, field, pos);
        }
        return switch (key) {
            case MapKeyRepresentation.NamedKey n ->
                    CrossingMapKey.named(nominal(n.name(), d, field, pos, symbols));
            case MapKeyRepresentation.Lexical l -> CrossingMapKey.lexical(l);
        };
    }

    /** A tuple names what is unrepresentable about it — it has no field names to write — rather than
     *  naming the codec that gave up. */
    private static CompileException aTuple(Type t, Hir.Data d, String field, SourcePos pos) {
        return CompileException.of(Diagnostic.at(pos)
                .say(new DataMessage.ATupleHasNoExternalRepresentation(where(d, field), Type.showInside(t)))
                .build());
    }

    /** The one report for a field whose type has no boundary representation, wherever in the type it
     *  sits. It is positioned on the field rather than on the data, so the caret lands on what has to
     *  change, and it prints the part that had no representation — which is not always the whole
     *  field type, and is what the author has to replace. */
    private static CompileException noRepresentation(Type t, Hir.Data d, String field, SourcePos pos) {
        return CompileException.of(Diagnostic.at(pos)
                .say(new DataMessage.NoCodecCanBeDerived(where(d, field), Type.showInside(t)))
                .build());
    }

    /** What to write instead, said of the name rather than of the codec that would have read it: the
     *  name is refused for whose vocabulary it is, and deriving a codec for it would not change that. */
    private static CompileException foreignName(TypeSymbol foreign, Hir.Data d, String field,
                                                SourcePos pos) {
        return CompileException.of(Diagnostic.at(pos)
                .hint(new TypeMessage.DeclareItAsATypeOfTheModel())
                .say(new TypeMessage.AFieldTakesATypeTheLanguageDeclares(where(d, field),
                        foreign.name()))
                .build());
    }

    /**
     * A key that cannot key a JSON object. This runs ahead of the checker that makes the same
     * judgement on a data's fields, so it reports the same way rather than as a codec that gave up:
     * the reader has to change the key type either way, and being told twice in two vocabularies
     * helps nobody.
     */
    private static CompileException badMapKey(Type key, Hir.Data d, String field, SourcePos pos) {
        return CompileException.of(Diagnostic.at(pos)
                .hint(new TypeMessage.AMapIsAJsonObjectKeyedByStrings())
                .say(new TypeMessage.AFieldsMapCannotBeKeyedByThat(where(d, field), Type.show(key)))
                .build());
    }

    /** How to name the place a boundary complaint belongs to. A newtype's single field is implicit —
     *  the author wrote {@code data X = Y}, never a field called {@code value} — so it is named by
     *  the type alone. */
    private static String where(Hir.Data d, String field) {
        return d.newtype() ? d.name() : d.name() + "." + field;
    }
}
