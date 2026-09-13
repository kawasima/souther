package souther.compiler.examples;

import souther.compiler.observe.Alignment;
import souther.compiler.observe.Asserted;
import souther.compiler.observe.Expectation;
import souther.compiler.observe.ObservedValue;
import souther.compiler.observe.PathElement;
import souther.compiler.observe.Position;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A structured value written the way a fixture writes one.
 *
 * <p>Not the encoder. An encoder writes a value as the representation it crosses a boundary in, and a
 * newtype's representation is the base it wraps — which is right for a boundary and wrong for a
 * diagnostic, where the whole question may be which of two names over one base a value wears. So a
 * mismatch is rendered from the structured value, where the name is still there to write.
 */
final class ValueRendering {

    private final NeutralForm neutral;

    ValueRendering(NeutralForm neutral) {
        this.neutral = neutral;
    }

    /** What a row wrote, as it wrote it. */
    String show(Asserted a) {
        return switch (a) {
            case Asserted.Value(ObservedValue v) -> show(v);
            case Asserted.Built built -> {
                List<String> names = new ArrayList<>(built.fields().keySet());
                names.sort(String::compareTo);
                if (names.equals(List.of("value")) && neutral.isNewtype(built.type())) {
                    yield built.type().name() + "(" + show(built.fields().get("value")) + ")";
                }
                List<String> out = new ArrayList<>();
                for (String name : names) {
                    out.add(name + " = " + show(built.fields().get(name)));
                }
                yield out.isEmpty() ? built.type().name()
                        : built.type().name() + " { " + String.join(", ", out) + " }";
            }
            case Asserted.Elements elements -> {
                List<String> out = new ArrayList<>();
                for (Asserted e : elements.elements()) {
                    out.add(show(e));
                }
                String written = out.isEmpty() ? "[]" : "[ " + String.join(", ", out) + " ]";
                // A row that said which collection it wrote is shown saying it, so a mismatch between
                // a set and a list of the same elements does not read as two of the same thing.
                yield elements.stated() == Asserted.Container.SET ? "Set.fromList(" + written + ")"
                        : written;
            }
            case Asserted.Entries entries -> {
                List<String> out = new ArrayList<>();
                for (Asserted.Entry e : entries.entries()) {
                    out.add("(" + show(e.key()) + ", " + show(e.value()) + ")");
                }
                String written = out.isEmpty() ? "[]" : "[ " + String.join(", ", out) + " ]";
                yield entries.stated() ? "Map.fromList(" + written + ")" : written;
            }
        };
    }

    /** What a row wrote is, named as the language names it. */
    String typeShown(Asserted a) {
        return switch (a) {
            case Asserted.Value(ObservedValue v) -> typeShown(v);
            case Asserted.Built built -> built.type().name();
            case Asserted.Elements elements -> switch (elements.stated()) {
                case SET -> "a set";
                case LIST -> "a list";
                case UNSTATED -> "a collection";
            };
            case Asserted.Entries _ -> "a map";
        };
    }

    /** What came out, written the way a row writes one. {@code position} is what the behavior
     *  declares here, which is the only thing that says whether a sequence is a list or a set — the
     *  same reading the comparison used, handed on rather than worked out a second time. */
    String show(ObservedValue v, Type position) {
        Type open = NeutralForm.open(position);
        if (v instanceof ObservedValue.Sequence s && open instanceof Type.SetOf set) {
            List<String> out = new ArrayList<>();
            for (ObservedValue e : s.elements()) {
                out.add(show(e, set.element()));
            }
            return "Set.fromList(" + (out.isEmpty() ? "[]" : "[ " + String.join(", ", out) + " ]") + ")";
        }
        if (v instanceof ObservedValue.Sequence s && open instanceof Type.ListOf list) {
            List<String> out = new ArrayList<>();
            for (ObservedValue e : s.elements()) {
                out.add(show(e, list.element()));
            }
            return out.isEmpty() ? "[]" : "[ " + String.join(", ", out) + " ]";
        }
        return show(v);
    }

    /**
     * What came out, written beside what a row stated and put where the correspondence says.
     *
     * <p>A map is a value with no order. What came out of a run holds its pairs in whatever the
     * runtime's table walks, which is a fact about the keys' numbers and about nothing a model
     * says — so writing them out that way shows a reader something the value does not hold, beside
     * a row that does hold an order. Putting them in an order of this compiler's settles the second
     * half of that and not the first: a total order is invented for a value that has none, and the
     * two columns still line up nowhere.
     *
     * <p><b>So the row's order is the one, as far as the row reaches.</b> A pair the row wrote is
     * written where the row wrote it; a pair it did not write has no place of the author's and
     * follows, in the one form two runs of it agree on.
     *
     * <p><b>Which of the answer's parts stands for which of the row's is not decided here.</b> A
     * map pairs its entries by key and a set pairs its elements without an order, and both are
     * questions about what being the same value means — the comparison settles them and hands the
     * correspondence over ({@link Alignment}). Found again from what the two render as, two entries
     * the comparison matched but a rendering could not tell apart would be written as though the
     * answer held neither: a decimal is the amount it stands for, and {@code 1.0} and {@code 1.00}
     * are one key written two ways.
     *
     * <p>A rule about showing two values together, and not about either of them. A map is no more
     * ordered for having been rendered, and nothing downstream may read this sequence as the
     * value's.
     */
    String show(ObservedValue v, Type position, Alignment against) {
        Type open = NeutralForm.open(position);
        if (v instanceof ObservedValue.Sequence s && open instanceof Type.SetOf set) {
            return "Set.fromList(" + elements(s, set.element(), against) + ")";
        }
        if (v instanceof ObservedValue.Sequence s && open instanceof Type.ListOf list) {
            return elements(s, list.element(), against);
        }
        return against(v, against);
    }

    /** The elements, each beside whichever of the row's the correspondence found for it. */
    private String elements(ObservedValue.Sequence s, Type element, Alignment against) {
        List<Alignment> by = against instanceof Alignment.Elements(List<Alignment> these)
                ? these : List.of();
        List<String> out = new ArrayList<>();
        for (int i = 0; i < s.elements().size(); i++) {
            out.add(i < by.size() ? show(s.elements().get(i), element, by.get(i))
                    : canonical(s.elements().get(i)));
        }
        return out.isEmpty() ? "[]" : "[ " + String.join(", ", out) + " ]";
    }

    /**
     * What came out, put where the row's is wherever the row reaches.
     *
     * <p>Carried down rather than applied at the top, because the map a row and an answer differ
     * inside may be under a field or an element. Where the correspondence found nothing of the
     * row's, nothing puts this anywhere and it is written in the one form.
     */
    private String against(ObservedValue v, Alignment against) {
        return switch (against) {
            case Alignment.Entries entries when v instanceof ObservedValue.Mapping _ ->
                    entries(entries);
            case Alignment.Built(Map<String, Alignment> fields)
                    when v instanceof ObservedValue.Constructed c -> constructed(c, fields);
            case Alignment.Elements(List<Alignment> by) when v instanceof ObservedValue.Sequence s -> {
                List<String> out = new ArrayList<>();
                for (int i = 0; i < s.elements().size(); i++) {
                    out.add(i < by.size() ? against(s.elements().get(i), by.get(i))
                            : canonical(s.elements().get(i)));
                }
                yield out.isEmpty() ? "[]" : "[ " + String.join(", ", out) + " ]";
            }
            case Alignment.Nothing _ -> canonical(v);
            // A value with no parts, and a part the correspondence lines up with nothing. Neither
            // has anything of the row's under it to put anywhere.
            case Alignment.Leaf _, Alignment.Built _, Alignment.Elements _, Alignment.Entries _ ->
                    canonical(v);
        };
    }

    /** The pairs, the row's first and in its order, then the rest in the one form. */
    private String entries(Alignment.Entries entries) {
        List<String> out = new ArrayList<>();
        for (Alignment.Placed each : entries.written()) {
            out.add("(" + canonical(each.entry().key()) + ", "
                    + against(each.entry().value(), each.under()) + ")");
        }
        List<String> rest = new ArrayList<>();
        for (ObservedValue.Entry each : entries.rest()) {
            rest.add("(" + canonical(each.key()) + ", " + canonical(each.value()) + ")");
        }
        rest.sort(String::compareTo);
        out.addAll(rest);
        return out.isEmpty() ? "[]" : "[ " + String.join(", ", out) + " ]";
    }

    /** The fields, each beside the one the row wrote under that name where it wrote one. */
    private String constructed(ObservedValue.Constructed c, Map<String, Alignment> fields) {
        ObservedValue inner = c.field("value");
        if (inner != null && neutral.isNewtype(c.type()) && c.fields().size() == 1) {
            Alignment under = fields.get("value");
            return c.type().name() + "("
                    + (under == null ? canonical(inner) : against(inner, under)) + ")";
        }
        List<String> names = new ArrayList<>(c.fields().keySet());
        names.sort(String::compareTo);
        List<String> out = new ArrayList<>();
        for (String name : names) {
            Alignment under = fields.get(name);
            out.add(name + " = " + (under == null ? canonical(c.fields().get(name))
                    : against(c.fields().get(name), under)));
        }
        return out.isEmpty() ? c.type().name()
                : c.type().name() + " { " + String.join(", ", out) + " }";
    }

    /**
     * A value nothing states an order for, written the one way.
     *
     * <p>What {@link #show(ObservedValue)} does, except that a map's pairs are put in the order they
     * are written out in rather than the order the value happens to hold them. That order says
     * nothing — nobody chose it and nothing may read it as a fact about the value — and what it is
     * for is that two runs of one report read alike.
     *
     * <p>All the way down. A pair nobody stated may hold a map of its own, and leaving that one in
     * the order the run's table walked would put the number back into the report one level below
     * where it was taken out.
     */
    private String canonical(ObservedValue v) {
        return switch (v) {
            case ObservedValue.Mapping m -> {
                List<String> out = new ArrayList<>();
                for (ObservedValue.Entry each : m.entries()) {
                    out.add("(" + canonical(each.key()) + ", " + canonical(each.value()) + ")");
                }
                out.sort(String::compareTo);
                yield out.isEmpty() ? "[]" : "[ " + String.join(", ", out) + " ]";
            }
            case ObservedValue.Sequence s -> {
                List<String> out = new ArrayList<>();
                for (ObservedValue each : s.elements()) {
                    out.add(canonical(each));
                }
                yield out.isEmpty() ? "[]" : "[ " + String.join(", ", out) + " ]";
            }
            case ObservedValue.Constructed c -> {
                ObservedValue inner = c.field("value");
                if (inner != null && neutral.isNewtype(c.type()) && c.fields().size() == 1) {
                    yield c.type().name() + "(" + canonical(inner) + ")";
                }
                List<String> names = new ArrayList<>(c.fields().keySet());
                names.sort(String::compareTo);
                List<String> out = new ArrayList<>();
                for (String name : names) {
                    out.add(name + " = " + canonical(c.fields().get(name)));
                }
                yield out.isEmpty() ? c.type().name()
                        : c.type().name() + " { " + String.join(", ", out) + " }";
            }
            default -> show(v);
        };
    }

    /** What came out is, named as the language names it, at the position that says what it is. */
    String typeShown(ObservedValue v, Type position) {
        return typeShown(v, Position.at(position));
    }

    /** The same, where what reads the value is a place rather than a written type. */
    String typeShown(ObservedValue v, Position position) {
        if (v instanceof ObservedValue.Sequence) {
            return position.opened() instanceof Position.At(Type type) && type instanceof Type.SetOf
                    ? "a set" : "a list";
        }
        return typeShown(v);
    }

    /** What the row stated, named as the language names it. */
    String typeShown(Expectation.Asserts stated) {
        return switch (stated) {
            case Expectation.TheValue(Asserted value) -> typeShown(value);
            case Expectation.TheCase(TypeSymbol name) -> name.name();
        };
    }

    /** The value as a row would write it, where nothing says what its sequences are. */
    String show(ObservedValue v) {
        return switch (v) {
            case ObservedValue.Bool b -> String.valueOf(b.value());
            case ObservedValue.Integer i -> String.valueOf(i.value());
            case ObservedValue.Decimal d -> d.value().toPlainString();
            case ObservedValue.Text t -> "\"" + t.value() + "\"";
            // Written as the construction a fixture writes one with, so it is never read as the text
            // that spells it — which is the difference a row writing a date as a string is told about.
            case ObservedValue.Temporal t -> t.primitive().shown() + "(\"" + t.iso() + "\")";
            case ObservedValue.Unit u -> u.type().name();
            case ObservedValue.Absent _ -> "None";
            case ObservedValue.Constructed c -> constructed(c);
            case ObservedValue.Sequence s -> {
                List<String> out = new ArrayList<>();
                for (ObservedValue e : s.elements()) {
                    out.add(show(e));
                }
                yield out.isEmpty() ? "[]" : "[ " + String.join(", ", out) + " ]";
            }
            case ObservedValue.Mapping m -> {
                List<String> out = new ArrayList<>();
                for (ObservedValue.Entry e : m.entries()) {
                    out.add("(" + show(e.key()) + ", " + show(e.value()) + ")");
                }
                yield out.isEmpty() ? "[]" : "[ " + String.join(", ", out) + " ]";
            }
            case ObservedValue.Unknown u -> "?(" + u.reason() + ")";
            case ObservedValue.Truncated _ -> "?";
        };
    }

    private String constructed(ObservedValue.Constructed c) {
        ObservedValue inner = c.field("value");
        if (inner != null && neutral.isNewtype(c.type()) && c.fields().size() == 1) {
            return c.type().name() + "(" + show(inner) + ")";
        }
        // A structured value holds its fields by name and not in an order, so the writer puts them in
        // one: two renderings of one value have to read alike, and two runs have to agree. Lexical,
        // and it says nothing — a diagnostic would rather show a record in the order it was declared,
        // and a structured value cannot be asked what that order was. Nothing may read this order as
        // the declaration's.
        List<String> names = new ArrayList<>(c.fields().keySet());
        names.sort(String::compareTo);
        List<String> out = new ArrayList<>();
        for (String name : names) {
            out.add(name + " = " + show(c.fields().get(name)));
        }
        return out.isEmpty() ? c.type().name()
                : c.type().name() + " { " + String.join(", ", out) + " }";
    }

    /**
     * The place inside a value a difference is at, as a reader of this compiler's reports reads one.
     *
     * <p>Written here and not carried as text. What names an entry is the key it was found by, and a
     * key is written the way any other value this reports is — so spelling a path needs what spells a
     * value, and a path spelled anywhere else would be a second answer to how a value is written.
     */
    String shown(List<PathElement> path) {
        StringBuilder out = new StringBuilder("$");
        for (PathElement step : path) {
            switch (step) {
                case PathElement.Field(String name) -> out.append('.').append(name);
                case PathElement.Index(int at) -> out.append('[').append(at).append(']');
                case PathElement.Key(Asserted key) ->
                        out.append('[').append(show(key)).append(']');
            }
        }
        return out.toString();
    }

    /**
     * What the value is, named as the language names it — what a mismatch says when the two sides
     * differ by their type rather than by their contents.
     */
    String typeShown(ObservedValue v) {
        Type.Prim primitive = v.primitive();
        if (primitive != null) {
            return primitive.shown();   // the one table a primitive is spelled from
        }
        return switch (v) {
            case ObservedValue.Unit u -> u.type().name();
            case ObservedValue.Constructed c -> c.type().name();
            case ObservedValue.Absent _ -> "None";
            case ObservedValue.Sequence _ -> "a collection";
            case ObservedValue.Mapping _ -> "a map";
            default -> "unread";
        };
    }
}
