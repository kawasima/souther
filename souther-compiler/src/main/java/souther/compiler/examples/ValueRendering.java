package souther.compiler.examples;

import souther.compiler.observe.Asserted;
import souther.compiler.observe.Expectation;
import souther.compiler.observe.ObservedValue;
import souther.compiler.observe.PathElement;
import souther.compiler.observe.Position;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;

import java.util.ArrayList;
import java.util.List;

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
