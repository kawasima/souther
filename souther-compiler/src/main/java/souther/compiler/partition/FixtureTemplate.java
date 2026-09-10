package souther.compiler.partition;

import souther.compiler.ast.Hir;
import souther.compiler.ast.WrittenName;
import souther.compiler.check.Carrier;
import souther.compiler.diag.Region;
import souther.compiler.numeric.Count;
import souther.compiler.numeric.Place;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.ApplicationOrigin;
import souther.compiler.types.FixtureReferenceOrigin;
import souther.compiler.types.ReachName;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.types.TypeReachName;
import souther.compiler.types.ValueName;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A value written the way a row writes it — {@code Amount(0)}, {@code None}, {@code Overseas} — held
 * both as the text a person reads and as the tree a decoder builds.
 *
 * <p>Two forms of one value, because the two questions asked of it are different. What goes into a
 * generated row is text, and only text: the row is printed for someone to complete, so nothing about
 * it is a built object. Whether it can be built at all is a different question, and the only honest way
 * to answer it is to build it, which needs the tree. Deriving one from the other would mean either a
 * parser for the text or a printer for what a decoder saw, and both would be a second spelling of the
 * same value with its own way of disagreeing.
 *
 * <p>The tree carries what its names denote, because that is what a fixture is read by. A construction
 * is reached by the type it names, not by the spelling — so the type is put there when the value is
 * made, where it is known, rather than looked up again from the text.
 *
 * <p>Every name a value here wears arrives as a {@link TypeReachName}, so that the two forms are
 * written from one answer. What the tree denotes and what the text spells are the two halves of one
 * reference, and nothing here decides either: a type declared as {@code Amount} is written
 * {@code up.Amount} by a module that reached it through an alias, and a generator that spelled the
 * declaration's own name offered a row nobody could paste while handing the reader a name it
 * resolved to something else (issue #696).
 */
public record FixtureTemplate(String text, Hir.Expr value) {

    /** Nothing generated is anywhere, and a fixture built here is never quoted back at a source. */
    private static final SourcePos NOWHERE = new SourcePos(0, 0);

    /** And nothing generated was written over anything: a row is text this builds, not text anyone
     *  typed, so there is no stretch of any file to underline. */
    private static final Region NO_SOURCE = null;

    public FixtureTemplate {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("a fixture template is written text");
        }
    }

    public static FixtureTemplate integer(long value) {
        Hir.Expr magnitude = new Hir.IntLit(Math.abs(value), NOWHERE, NO_SOURCE);
        return new FixtureTemplate(Long.toString(value),
                value < 0 ? new Hir.Neg(magnitude, NOWHERE, NO_SOURCE) : magnitude);
    }

    public static FixtureTemplate decimal(BigDecimal value) {
        String written = value.stripTrailingZeros().toPlainString();
        BigDecimal magnitude = value.abs();
        Hir.Expr literal = new Hir.DecimalLit(magnitude, NOWHERE, NO_SOURCE);
        return new FixtureTemplate(written + "m",
                value.signum() < 0 ? new Hir.Neg(literal, NOWHERE, NO_SOURCE) : literal);
    }

    /**
     * A string, written the way the language reads one back.
     *
     * <p>Every escape a string literal has, because a row is offered as text to paste into a model and
     * has to come back as the value it was made from. A tab or a newline written as itself would end
     * the line the row is on as well.
     */
    public static FixtureTemplate string(String value) {
        return new FixtureTemplate(quoted(value), new Hir.StringLit(value, NOWHERE, NO_SOURCE));
    }

    /**
     * {@code value} as the language writes a string literal, quotes and escapes and all.
     *
     * <p>Here and not at each place that writes one. Every writer of a literal wants the same
     * escapes for the same reason — what it writes is pasted back and read as the value it was made
     * from — and a second spelling of the rule is a place where a value with a quote in it comes
     * out as source that says something else, or does not parse at all.
     */
    public static String quoted(String value) {
        StringBuilder written = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> written.append("\\\\");
                case '"' -> written.append("\\\"");
                case '\n' -> written.append("\\n");
                case '\t' -> written.append("\\t");
                case '\r' -> written.append("\\r");
                default -> written.append(c);
            }
        }
        return written.append('"').toString();
    }

    public static FixtureTemplate bool(boolean value) {
        return new FixtureTemplate(Boolean.toString(value), new Hir.BoolLit(value, NOWHERE, NO_SOURCE));
    }

    /** A date, written the way a row writes one: the constructor applied to an ISO 8601 string. */
    public static FixtureTemplate date(String iso) {
        return temporal("Date", iso);
    }

    public static FixtureTemplate time(String iso) {
        return temporal("Time", iso);
    }

    public static FixtureTemplate dateTime(String iso) {
        return temporal("DateTime", iso);
    }

    public static FixtureTemplate instant(String iso) {
        return temporal("Instant", iso);
    }

    /**
     * Every temporal is written the same way — the type's name applied to its ISO 8601 text — so the
     * four share this rather than repeating it and letting one of them drift.
     *
     * <p>A temporal is the language's own vocabulary rather than any module's declaration, so the
     * name is the library namespace applied, which is what the same call written in a source reaches
     * ({@link ValueName.Stdlib#namespace}). No module renames it and none has to be asked how it
     * writes it.
     */
    private static FixtureTemplate temporal(String type, String iso) {
        // The namespace applied, which builds a primitive rather than reaching an operation — so
        // the reference says namespace, and no reader that emits calls can be handed it.
        ValueName.Stdlib.Namespace namespace = ValueName.Stdlib.namespace(type);
        return new FixtureTemplate(type + "(\"" + iso + "\")",
                Hir.Apply.synthetic(type, new ReachName.TheNamespace(namespace), null,
                        new ApplicationOrigin.ComposedFixture(),
                        List.of(new Hir.StringLit(iso, NOWHERE, NO_SOURCE)), NOWHERE, NO_SOURCE));
    }

    /** The absent optional, which the language names rather than any module. */
    public static FixtureTemplate none() {
        ValueName.Builtin none = new ValueName.Builtin("None");
        return new FixtureTemplate("None",
                Hir.Var.denoting(WrittenName.synthetic("None", NOWHERE),
                        new ReachName.InScope(none)));
    }

    /** A case that carries nothing: naming it is constructing it. */
    public static FixtureTemplate unitCase(TypeReachName.Written type) {
        String written = type.rendered();
        ValueName.OfType named = new ValueName.OfType(written, type.denotes());
        return new FixtureTemplate(written,
                Hir.Var.denoting(WrittenName.synthetic(written, NOWHERE),
                        new ReachName.InScope(named)));
    }

    /**
     * A count written as the value it stands for, wrapped where the position wears a name.
     *
     * <p>The one place a count becomes something a row carries. Which literal a count is written as
     * is the carrier's answer and is taken from it — spelled out per caller instead, a date-time's
     * second count reached a row as an {@code Int}, and the decoder turned it down with nothing said
     * about why.
     *
     * <p>Null where the case at {@code at} is one this module cannot name — a case the module
     * declaring it does not expose, which a value can be of and no author can write down. Every
     * other carrier writes a literal, which needs no name from anyone.
     *
     * <p>Null too where no value of the order stands at {@code at}. A count is arithmetic and the
     * order it is a count on may stop: a time of day counts seconds from midnight and has 86400 of
     * them, so the count below its first is a count no time has. Asked of the order's own ends
     * ({@link Carrier#extent()}) rather than restated here, since a second statement of where an
     * order stops is a second thing to keep true. What such a point leaves is a point nothing
     * composes a value at, which is what the callers already read a null as.
     *
     * <p>Bare. Which names the value wears at the position it is going to is a different question
     * with its own answer ({@link WornNames#under}), taken from the reading of that position;
     * answered here as well, it was answered one layer deep, and a value of a newtype over a newtype
     * came back missing the name in the middle.
     */
    public static FixtureTemplate on(Carrier carrier, Place at, TypeReachName.Naming naming) {
        if (!carrier.extent().admits(at)) {
            return null;
        }
        return switch (carrier) {
            case Carrier.Whole _ -> integer(Count.number(at).at().longValueExact());
            case Carrier.Dense _ -> decimal(Count.number(at).at());
            // Written by the carrier, so the text on a row and the text in a report are the same
            // text. Spelled here as well, the two could differ at midnight and nowhere else.
            case Carrier.Days _ -> date(carrier.written(at));
            case Carrier.Seconds _ -> dateTime(carrier.written(at));
            case Carrier.SecondsOfDay _ -> time(carrier.written(at));
            case Carrier.Nanos _ -> instant(carrier.written(at));
            // Naming a case builds it, which is what a row writes at such a position — never the
            // place the case takes in its declaration.
            case Carrier.Ordinal ordinal ->
                    naming.of(ordinal.caseAt(at)) instanceof TypeReachName.Written written
                            ? unitCase(written) : null;
            // A string stands for itself, so what a row carries is the string, escaped the way the
            // language reads one back.
            case Carrier.Text _ -> string(at.key());
        };
    }

    /**
     * A newtype around one value, written in the call form a row writes it in (ADR-0032).
     *
     * <p>The application says where the construction came from and the name says what is applied,
     * which is how the same call reads when a source wrote it: applying a type is the newtype taking
     * what it wraps.
     */
    public static FixtureTemplate newtype(TypeReachName.Written type, FixtureTemplate inner) {
        String written = type.rendered();
        ValueName.OfType named = new ValueName.OfType(written, type.denotes());
        return new FixtureTemplate(written + "(" + inner.text() + ")",
                Hir.Apply.synthetic(written, new ReachName.InScope(named), null,
                        new ApplicationOrigin.ComposedFixture(), List.of(inner.value()), NOWHERE,
                        NO_SOURCE));
    }

    /** No elements. A list, a set and a map are all written this way in a fixture: what the position
     * is decides what the empty brackets become. */
    public static FixtureTemplate emptyCollection() {
        return collection(List.of());
    }

    /**
     * A collection of what it holds, in the order the elements were chosen.
     *
     * <p>The same brackets for all three, for the same reason the empty one has them: a fixture writes
     * a set as its elements and a map as its entry pairs, and which of them the brackets are is what
     * the position declares rather than anything spelled here.
     */
    public static FixtureTemplate collection(List<FixtureTemplate> elements) {
        List<Hir.Expr> values = new ArrayList<>();
        List<String> written = new ArrayList<>();
        for (FixtureTemplate each : elements) {
            values.add(each.value());
            written.add(each.text());
        }
        return new FixtureTemplate("[" + String.join(", ", written) + "]",
                new Hir.ListLit(List.copyOf(values), SourceConstructOrigin.unwritten(), NOWHERE,
                        NO_SOURCE));
    }

    /** One entry of a map: the pair a fixture writes a key and its value as. */
    public static FixtureTemplate entry(FixtureTemplate key, FixtureTemplate value) {
        return new FixtureTemplate("(" + key.text() + ", " + value.text() + ")",
                new Hir.Tuple(List.of(key.value(), value.value()), NOWHERE, NO_SOURCE));
    }

    /**
     * A value the module already states, written the way a row writes it: by its name.
     *
     * <p>The name and what it denotes, which is what a fixture is read by. A row naming a
     * module-level {@code let} is a row an author writes today — the value is expanded where the
     * row is read — and this is that same row, composed.
     *
     * <p>{@code occurrence} is which reference of the helper this is, and the run that composed it
     * says so. The name reaches a declaration, so it is some reference of one; no source wrote it
     * and no construct a source wrote is behind it, so nothing here could work one out — which is
     * why it is taken and not minted. What the name reaches is {@code module} and {@code name}'s to
     * answer, and the occurrence does not repeat it.
     *
     * @param module     what the name belongs to, which is what a reader of the name resolves it
     *                   through
     * @param name       the name as this module writes it
     * @param occurrence which reference this run composed, from {@link FixtureReferences}
     */
    public static FixtureTemplate named(String module, String name,
                                        FixtureReferenceOrigin occurrence) {
        ValueName.Helper helper = new ValueName.Helper(module, name);
        return new FixtureTemplate(name,
                Hir.Var.respelled(name, new ReachName.Own(helper), occurrence, NOWHERE, NO_SOURCE));
    }

    /**
     * {@code T { ...base, field = value }} — a value the model already states, with one field
     * moved.
     *
     * <p>The one row a reader of a table recognises. Where the gap is a class at one position, the
     * row that says something about it is that class against values the model already puts beside
     * it — and a row that also moved the positions beside it says nothing about which of them the
     * answer turned on (issue #967).
     *
     * <p>The spread is the value and not a way of printing it. What a row is written as and what a
     * decoder builds it from are two forms of one value here, never derived from each other
     * ({@link FixtureTemplate}), so the tree carries the spread the text spells and both go through
     * the reading a written row goes through. Printed as a spread over a tree that had the record
     * written out, the two would be one value under two spellings with their own way of
     * disagreeing.
     *
     * <p>More than one field, because a row about one class can need another moved beside it to be
     * a value the model allows at all. Which of them the row is <em>for</em> is not here: that is
     * what it was composed for, and the rest are what it took to write one (issue #967).
     *
     * @param type  the record being built, written the way this module reaches it
     * @param base  the value being spread, which has to be one a row can name
     * @param moved the fields this writes over what the base holds, in the order they are written
     */
    public static FixtureTemplate spreading(TypeReachName.Written type, FixtureTemplate base,
                                            Map<String, FixtureTemplate> moved) {
        if (!(base.value() instanceof Hir.Var spread)) {
            throw new IllegalArgumentException("a spread names a value: " + base.text());
        }
        if (moved.isEmpty()) {
            throw new IllegalArgumentException("a spread that moves nothing is the value itself");
        }
        List<String> written = new ArrayList<>();
        List<Hir.FieldInit> inits = new ArrayList<>();
        for (Map.Entry<String, FixtureTemplate> field : moved.entrySet()) {
            written.add(field.getKey() + " = " + field.getValue().text());
            inits.add(new Hir.FieldInit(field.getKey(), field.getValue().value(), NOWHERE));
        }
        return new FixtureTemplate(
                type.rendered() + " { ..." + base.text() + ", " + String.join(", ", written) + " }",
                Hir.NewData.syntheticWithEveryFieldWritten(Hir.Name.reached(type, NOWHERE), inits,
                        List.of(spread), NOWHERE, NO_SOURCE));
    }

    /** A record, field by field, in the order the fields were declared. */
    public static FixtureTemplate record(TypeReachName.Written type, Map<String, FixtureTemplate> fields) {
        List<String> written = new ArrayList<>();
        List<Hir.FieldInit> inits = new ArrayList<>();
        for (Map.Entry<String, FixtureTemplate> field : fields.entrySet()) {
            written.add(field.getKey() + " = " + field.getValue().text());
            inits.add(new Hir.FieldInit(field.getKey(), field.getValue().value(), NOWHERE));
        }
        return new FixtureTemplate(type.rendered() + " { " + String.join(", ", written) + " }",
                Hir.NewData.syntheticWithEveryFieldWritten(Hir.Name.reached(type, NOWHERE), inits,
                        List.of(), NOWHERE, NO_SOURCE));
    }
}
