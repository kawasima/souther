package souther.compiler.examples;

import souther.compiler.jvm.SoutherJvmAbi;
import souther.compiler.ast.Hir;
import souther.compiler.check.Boundary;
import souther.compiler.check.DeclarationFacts;
import souther.compiler.check.Symbols;
import souther.compiler.observe.FieldTypes;
import souther.compiler.observe.Position;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a value looks like in the form a derived decoder reads — the *neutral* form an {@code example}
 * fixture is written in, and the one thing both directions of a row have to agree on.
 *
 * <p>A row reaches that form two ways. From what the author wrote, which {@link ExampleVerifier} walks:
 * a literal, a record, a collection, a newtype application. And from a live value, which {@link #of}
 * re-materialises — what a row's operand answered with, on the way into a fixture, and a value
 * this compile built, on the way to an answerer whose classes are not this compile's
 * ({@link Handed}). The rules that decide the form itself — which
 * discriminator a case of a sum carries, when a unit case travels as a bare name, which written list is
 * a map's entries — are the same rules whichever direction arrives at them, so they are here rather
 * than restated on one side and read from the other.
 *
 * <p>Running an example is {@link OperandRunner}'s, and diagnostics are the row's: a form that cannot
 * be reached is a {@link FixtureException}, which the row reports. What is here is the one reading of
 * the classes a run answers with — {@link #typeOf} for which declaration a value is and
 * {@link #simpleName} for what a report quotes — because a live value's type is a question every
 * reader of a run asks and only one answer to it is right.
 */
final class NeutralForm {

    private final Symbols symbols;
    /** What a declaration's fields hold, as the check settled it. Read and never worked out here:
     *  the same answer decides what a comparison reads at a place inside a value. */
    private final FieldTypes fields;

    NeutralForm(Symbols symbols, FieldTypes fields) {
        if (fields == null) {
            throw new IllegalArgumentException("a value's parts are read against what its"
                    + " declaration was checked to hold");
        }
        this.symbols = symbols;
        this.fields = fields;
    }

    // --- a live value, re-materialised -------------------------------------------------------------

    /**
     * A live value, in the neutral form a fixture is written in.
     *
     * <p>The inverse of writing the fixture, directed by the declared types: a scalar and a temporal are
     * already their own neutral form, a collection is re-materialised element by element, and a data is
     * read field by field through the accessor every data has (ADR-0065), each field against its
     * declared type, carrying the discriminator its sum's decoder reads — which is what a written record
     * fixture is built as, so one decoder reads both. An empty optional is left out, as writing
     * {@code None} leaves it out.
     *
     * <p>Not the derived {@code encoder()}: that answers what the boundary reads, which is a different
     * question. A {@code Date}'s neutral form is the parsed temporal while its encoder writes ISO text,
     * so a newtype over a date came back as text its own decoder refuses.
     *
     * <p>Two things reach this. A value a row's operand answered with, on the way into a fixture,
     * and a value this compile built, on the way to an answerer whose classes are not this compile's
     * ({@link Handed}). One walk, because the form is decided by the rules and not by which side asked.
     *
     * @param what a noun phrase naming the value, for the reason a row is given when it cannot be read
     *             back. Said by the caller because only the caller knows what the value is to the row —
     *             what an operand answered with, or an input the row handed over
     */
    Object of(Object live, Position position, String what) {
        if (live == null) {
            return live;
        }
        if (isScalar(live)) {
            // A scalar is already its own neutral form, except that a temporal's is the parsed value
            // and text is what a boundary carries one as. Asked here rather than left to the decoder,
            // which reads that text too and would take a helper's `String` for a date-time.
            if (live instanceof String text && temporalUnder(position) instanceof Type.Prim temporal) {
                throw notWrittenAsATemporal(temporal, text);
            }
            return live;
        }
        Position opened = position.opened();
        if (live instanceof Map<?, ?> m) {
            Position key = opened instanceof Position.At(Type type) && type instanceof Type.MapOf map
                    ? Position.at(map.key()) : Position.UNREAD;
            Position value = opened instanceof Position.At(Type type) && type instanceof Type.MapOf map
                    ? Position.at(map.value()) : Position.UNREAD;
            Map<Object, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                out.put(of(e.getKey(), key, what), of(e.getValue(), value, what));
            }
            return out;
        }
        if (live instanceof Iterable<?> it) {
            Position element = opened.element();
            List<Object> out = new ArrayList<>();
            for (Object e : it) {
                out.add(of(e, element, what));
            }
            return out;
        }
        String name = simpleName(live);
        // An optional field holds an `Option`: `None` is the absent value a fixture writes by leaving
        // the field out, and `Some` holds what the position's own type reads (spec §absence-is-written-as-null).
        if (name.equals("Option$None")) {
            return null;
        }
        if (name.equals("Option$Some")) {
            return of(field(live, "value", what), opened, what);
        }
        TypeSymbol caseName = typeOf(live);
        if (caseName == null) {
            throw new FixtureException(what + " is a " + name
                    + ", which is not a type this example can read");
        }
        if (!(symbols.declaredNode(caseName) instanceof Hir.Data data)) {
            // a unit case: its name where the position reads one, else the tag its sum's decoder reads
            if (readsABareName(position)) {
                return caseName.name();
            }
            Map<String, Object> unit = new LinkedHashMap<>();
            tagged(position, caseName, unit);
            return unit;
        }
        if (data.newtype()) {
            Position base = declaredBy(newtypeBaseType(caseName));
            return newtypeAt(position, caseName,
                    shaped(of(field(live, NEWTYPE_FIELD, what), base, what), base));
        }
        Map<String, Type> declared = fieldTypes(caseName);
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Type> f : declared.entrySet()) {
            Position at = declaredBy(f.getValue());
            Object value = shaped(of(field(live, f.getKey(), what), at, what), at);
            // an absent optional is left out, the same neutral form a fixture writes for `None`
            if (value != null) {
                out.put(f.getKey(), value);
            }
        }
        tagged(position, caseName, out);
        return out;
    }

    /** One field of a live data, read through the accessor every data has (ADR-0065). Its class may be
     * package-private, so the declared method is taken and opened, as a codec is. */
    private Object field(Object live, String name, String what) {
        try {
            java.lang.reflect.Method accessor = live.getClass().getDeclaredMethod(name);
            accessor.setAccessible(true);
            return accessor.invoke(live);
        } catch (ReflectiveOperationException _) {
            throw new FixtureException(what + " cannot be read back as a fixture: `"
                    + simpleName(live) + "` has no `" + name + "` to read");
        }
    }

    // --- the form itself, read by both directions -------------------------------------------------

    /**
     * Writes the discriminator the type declared at this position reads the case under, where it
     * reads it through a sum at all.
     *
     * <p>What is written is decided by the type the position declares (spec §encode-law): a case's own
     * decoder reads no discriminator and its sum's reads one, so `承認 { id = 1 }` is `{"id":1}` where
     * `承認` is what is declared and `{"id":1,"type":"承認"}` where `承認 | 却下` is. A fixture names the
     * case it means — `予算枠 = 未定予算`, or a whole `プロジェクト依頼 { … }` where the parameter is the
     * sum `依頼` — the same way the domain writes a construction, and the sum it is read through is
     * what puts the tag on a key beside it.
     *
     * <p>The key and the tag are the sum's own, read off {@link souther.compiler.check.Boundary} —
     * the same answer the sum's generated decoder and encoder are built from, and not decided again
     * here. What that settles is the leaves of the sums the position names (spec
     * §sum-discrimination), so a case reached through a nested sum is one of them and there is
     * nothing to walk. Issue #683 was this being answered by searching every visible sum for one
     * that lists the case, which is a question about the case rather than about the position it
     * stands at.
     *
     * <p>{@link Position.Unread} writes nothing, and nothing is worked out for it. A place no type
     * reads asks for nothing beside the case, so what stands there is the case's own form — which is
     * what a position typed by the case itself writes. Asking the sums that list the case instead is
     * the same question as above with the position dropped from it, and it makes a value's form move
     * when a sum nothing here reads it through is declared.
     *
     * <p>A behavior's answer union does not arrive here. It has no declaration to read a
     * discriminator off, and where its members are all units it is written as a bare tag before this
     * is reached ({@link #readsABareName}) — which is what its generated encoder writes.
     *
     * <p>A field the fixture wrote itself is never replaced. A case whose own field is named like its
     * sum's discriminator is already ambiguous at the boundary — the case encoder and the sum encoder
     * both claim that key — and overwriting here would hide that behind an example that passes while
     * decoding something the author did not write. Leaving the written value in place makes the row
     * fail on the tag it cannot match, which is the honest outcome.
     */
    void tagged(Position position, TypeSymbol caseName, Map<String, Object> map) {
        if (caseName == null) {
            return;
        }
        // Anything but a sum reads the case as itself: its own type, a union — which is written only
        // as a behavior's own answer and has no decoder — and a place nothing reads.
        if (!(position.opened() instanceof Position.At(Type type))
                || !(type instanceof Type.Ref ref)
                || !(symbols.declaredNode(ref.name()) instanceof Hir.SumData)) {
            return;
        }
        // What the sum's own decoder reads, read from where that is settled rather than from a copy
        // of it kept on the declaration. A fixture that wrote a tag of its own would be a value the
        // generated decoder cannot read.
        Boundary.Alternatives alternatives = Boundary.of(type, symbols);
        if (!(alternatives.representation() instanceof Boundary.Representation.Discriminated(String key))) {
            return;
        }
        for (Boundary.WireCase wire : alternatives.wireCases()) {
            if (caseName.equals(wire.atom())) {
                map.putIfAbsent(key, wire.tag());
                return;
            }
        }
    }

    /** Gives a value the neutral shape its declared type decodes from. A {@code Map} is written as a
     * list of {@code (key, value)} pairs — Elm's {@code Dict.fromList}, and the same list literal a
     * {@code Set} takes — while the decoder wants a map, so the pairs are collected here. Everything
     * else passes through; a {@code List} written as a list stays one, since the declared type, not the
     * literal, decides. Read on the checked type, so it applies wherever a value is decoded: a field of
     * a record fixture, and a behavior's argument or output. */
    Object shaped(Object v, Position position) {
        if (v == null || !(position instanceof Position.At(Type type))) {
            return v;
        }
        if (type instanceof Type.MapOf map && v instanceof List<?> entries) {
            Map<Object, Object> m = new LinkedHashMap<>();
            for (Object entry : entries) {
                if (!(entry instanceof List<?> pair) || pair.size() != 2) {
                    throw new FixtureException("a `Map` fixture is a list of (key, value) pairs,"
                            + " e.g. [ (\"apple\", 3) ]");
                }
                // Both sides. A key is a position of the same kind as a value, so an inner
                // collection written in key position is the neutral form of that collection
                // and not the list of pairs it was written as.
                m.put(shaped(pair.get(0), Position.at(map.key())),
                        shaped(pair.get(1), Position.at(map.value())));
            }
            return m;
        }
        if (v instanceof List<?> elements) {
            Type element = switch (type) {
                case Type.ListOf l -> l.element();
                case Type.SetOf s -> s.element();
                default -> null;
            };
            if (element != null) {
                List<Object> out = new ArrayList<>(elements.size());
                for (Object e : elements) {
                    out.add(shaped(e, Position.at(element)));
                }
                return out;
            }
        }
        return v;
    }

    /**
     * A newtype's neutral form at the position it is written in: its inner value, wearing the envelope
     * only where the position reads it through a sum that lists it.
     *
     * <p>A newtype case read through its sum decodes from the adjacent form that sum's decoder reads —
     * the inner value under {@code value}, next to the discriminator (spec §sum-discrimination) — while
     * a fixture names the case the way the domain constructs it,
     * {@code アクティベート済み(メールアドレス("a@example.com"))}.
     *
     * <p>The {@code "value"} envelope is not part of a newtype's representation — it is what
     * membership adds, which is why a standalone newtype is bare (spec §sum-discrimination). So the
     * position decides it, the way it decides whether a unit case travels as a bare name, and what
     * some other declaration does with the type does not reach a fixture written at the type itself.
     *
     * <p>Takes a case already resolved: a name spelled here would have to be one
     * {@link Symbols#declaredNode} answers to, which an imported type's declared name is not.
     */
    Object newtypeAt(Position position, TypeSymbol caseName, Object inner) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        tagged(position, caseName, envelope);
        if (envelope.isEmpty()) {
            return inner;   // read as itself: the newtype's own form is its inner value
        }
        envelope.put("value", inner);
        return envelope;
    }

    /**
     * Whether this place reads a unit case as a bare name: it is typed as an enumeration, whose
     * external form is the case's name (spec §sum-discrimination).
     *
     * <p>A question about the place and not about the case standing there, which is why the case is
     * not asked for. Where nothing reads the value the answer is no: a name is what a sum of units
     * writes a case as, and nothing here is that sum. Answered by asking whether every sum that lists
     * the case is an enumeration, it moved with a declaration nothing here reads the value through —
     * a case listed only by an enumeration wrote its bare name until another sum listed it too.
     */
    boolean readsABareName(Position position) {
        return position.opened() instanceof Position.At(Type type)
                && Boundary.of(type, symbols).representation()
                        instanceof Boundary.Representation.Enumeration;
    }

    /** Every field a value of {@code typeName} holds, in the order it is laid out. */
    Map<String, Type> fieldTypes(TypeSymbol typeName) {
        return fields.of(typeName);
    }

    /**
     * A place a value of {@code declared} stands at, and {@link Position#UNREAD} where nothing
     * declares one.
     *
     * <p>Here rather than on {@link Position}, which takes a type and has nothing to say about a
     * field a declaration does not have.
     */
    static Position declaredBy(Type declared) {
        return declared == null ? Position.UNREAD : Position.at(declared);
    }

    /** Whether {@code name} is a newtype — asked of a name resolution settled, never of a spelling:
     * an imported value's body names its own module's types, which the module reading the row need
     * not have imported, and a module of its own may declare something else of that spelling. */
    boolean isNewtype(TypeSymbol name) {
        return DeclarationFacts.isNewtype(name, symbols);
    }

    /** What a newtype wraps: the one field it is written with (spec §newtype), read like any other
     *  field, so a generic base ({@code data 在庫 = Map<商品ID, Int>}) keeps its type arguments. */
    Type newtypeBaseType(TypeSymbol name) {
        return fields.of(name).get(NEWTYPE_FIELD);
    }

    /** What a newtype's one field is called (spec §newtype). */
    static final String NEWTYPE_FIELD = "value";

    // --- reading a position's type ----------------------------------------------------------------

    static Type open(Type t) {
        return t instanceof Type.OptionOf o ? open(o.element()) : t;
    }

    /**
     * The temporal {@code position} is, through whatever stands between — an optional holds the value
     * itself, a newtype wraps what it is written from — or null where it is not a temporal at all.
     *
     * <p>A date is written {@code Date("2026-07-25")} and a date-time {@code DateTime("…")}, and the
     * compiler parses the text where it stands, so a temporal is already parsed by the time it is a
     * fixture. The derived decoders read ISO text as well, because that is how one crosses a boundary,
     * and what a boundary carries a value as is not a second way to write a fixture.
     *
     * <p>Here because both directions a row reaches the neutral form by have to ask it: what the
     * author wrote, and what a helper returned. Stated on one side and read from the other, the two
     * would answer differently about the same position.
     */
    Type.Prim temporalUnder(Position position) {
        Set<TypeSymbol> through = new LinkedHashSet<>();
        Type at = position.opened() instanceof Position.At(Type type) ? type : null;
        while (true) {
            if (at instanceof Type.Prim prim) {
                // Which primitives are temporal is asked of each one, so a primitive added later is
                // answered at its declaration rather than falling into the "not a temporal" side of
                // a comparison nobody revisited.
                return switch (prim) {
                    case DATE, TIME, DATETIME, INSTANT -> prim;
                    case INT, STRING, BOOL, DECIMAL, RAW -> null;
                };
            }
            if (!(at instanceof Type.Ref ref) || !through.add(ref.name())) {
                return null;
            }
            Type base = newtypeBaseType(ref.name());
            if (base == null) {
                return null;
            }
            at = open(base);
        }
    }

    /**
     * Refuses a value that is not in the form a fixture writes at {@code position}, without reading it
     * back.
     *
     * <p>For where nothing encloses a helper's application — the whole of an expected value, or the
     * whole of a stand-in — and the value it answered with is taken as it stands rather than
     * re-materialised ({@link #of}). Being unable to read a value back is not the same as its form
     * being wrong, and only the second is a rule, so this holds the rule and asks nothing else.
     *
     * <p>The rule is {@link #temporalUnder}'s, and a container is walked because a position's element
     * type is part of what it says: a written {@code [ "…" ]} at a {@code List<DateTime>} is refused
     * where the element type travels with it, and a helper answering with that same list is the same
     * fixture. The walk stops at anything nominal — a generated value's fields are the types they were
     * declared as, so there is nothing there a fixture could have written wrongly.
     */
    void requireWrittenForm(Object live, Position position) {
        if (live instanceof String text && temporalUnder(position) instanceof Type.Prim temporal) {
            throw notWrittenAsATemporal(temporal, text);
        }
        Position opened = position.opened();
        if (live instanceof Map<?, ?> entries
                && opened instanceof Position.At(Type type) && type instanceof Type.MapOf map) {
            for (Map.Entry<?, ?> e : entries.entrySet()) {
                requireWrittenForm(e.getKey(), Position.at(map.key()));
                requireWrittenForm(e.getValue(), Position.at(map.value()));
            }
            return;
        }
        if (opened.element() instanceof Position.At element && live instanceof Iterable<?> elements) {
            for (Object e : elements) {
                requireWrittenForm(e, element);
            }
        }
    }

    /** What a fixture that wrote text where a temporal stands is told, wherever it wrote it. */
    static FixtureException notWrittenAsATemporal(Type.Prim temporal, String text) {
        return new FixtureException("a `" + temporal.shown() + "` is written `" + temporal.shown()
                + "(\"" + text + "\")`; a string is how one arrives from outside, not how a fixture"
                + " writes it");
    }

    /** Whether a live value is the absent optional. */
    static boolean isAbsent(Object live) {
        return live != null && simpleName(live).equals("Option$None");
    }

    /** What a live optional holds. A `?` position takes the value itself as well as a `Some` around
     *  it, so anything that is not one is what it stands for. */
    static Object heldBy(Object live) {
        if (live == null || !simpleName(live).equals("Option$Some")) {
            return live;
        }
        try {
            java.lang.reflect.Method accessor = live.getClass().getDeclaredMethod("value");
            accessor.setAccessible(true);
            return accessor.invoke(live);
        } catch (ReflectiveOperationException _) {
            throw new FixtureException("an optional's value cannot be read back: `"
                    + simpleName(live) + "` has no `value` to read");
        }
    }

    /** Whether a value is a neutral scalar, so it can be shown as written rather than by class name. */
    static boolean isScalar(Object v) {
        return v instanceof String || v instanceof Long || v instanceof Boolean
                || v instanceof BigDecimal || v instanceof java.time.LocalDate
                || v instanceof java.time.LocalTime || v instanceof java.time.LocalDateTime
                || v instanceof java.time.Instant;
    }

    /**
     * Which declaration a live value is, read off the class it was generated as. A binary name is a
     * {@link TypeSymbol}'s qualified form, so the class the run answered with says both the module and
     * the name, and this answers for the type the value is.
     *
     * <p>Not its simple name resolved here. A helper a fixture applies may be one another module
     * published, and resolving the spelling in this module's scope answers for whatever this module
     * has under it: nothing, where the type is reached through an alias and no bare name of this
     * module spells it, and the wrong declaration where this module spells something else the same.
     * The class carries the module, and dropping it is what makes those two answers possible.
     */
    TypeSymbol typeOf(Object live) {
        if (live == null) {
            return null;
        }
        TypeKey candidate = SoutherJvmAbi.valueTypeCandidate(live.getClass().getName());
        // The identity the declaration carries, and not one minted from the address. A class name
        // read off a live value is a spelling from outside, and what says there is a type behind it
        // is that something declares one there — which is the declaration this reaches.
        Hir.Def declared = candidate == null ? null : symbols.declaredNode(candidate);
        return declared == null ? null : declared.declares();
    }

    /** What a report quotes a live value's class as. Its own name, and not the type's identity —
     *  {@link #typeOf} is that, and the two are separate answers because a report about a value of a
     *  type this module cannot name still has to say what it was. */
    static String simpleName(Object o) {
        if (o == null) {
            return "null";
        }
        String n = o.getClass().getName();
        int dot = n.lastIndexOf('.');
        return dot < 0 ? n : n.substring(dot + 1);
    }
}
