package souther.compiler.observe;

import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Whether two values are the same value, and where they are not.
 *
 * <p>Two questions are asked of this and they are not one question. Whether an answer is what a text
 * stated holds an {@link Asserted} against an {@link ObservedValue}: the first carries the names and
 * the forms a text wrote them under, the second is what a value turned out to be, and the two are
 * not one kind of thing. Whether two values that were both arrived at are the same holds an
 * {@link ObservedValue} against another, and nothing about it is a statement.
 *
 * <p>What being the same value means is one answer all the same — a set is its elements whichever
 * side asked, a decimal is the amount it stands for, a value is of its type before it is anything
 * else — so the two questions enter here and walk one walk. {@link Compared} is what that walk
 * reads, and each side is projected into it; a second walk beside this one would be the place a
 * {@code Set} came to be compared in order on one of the two routes.
 *
 * <p>{@code position} is what the declaration says stands here, so it says what the value on the
 * right is: an {@link ObservedValue.Sequence} is a {@code List} and a {@code Set} alike, and which
 * one it is comes from the type that produced it. A text that wrote which collection it meant is
 * held to that, and one that did not — {@code [ 1 ]} is how both are written — is read at what the
 * right-hand value is, since there is nothing else for it to be. An observation states no
 * container of its own, so a value arriving from one is read that way too.
 *
 * <p>What it reads of the declarations is one question ({@link ValueTypes}), so this is the same
 * comparison wherever it is made: by the compile that read the text, and by an output holding what
 * that compile decided.
 */
final class ValueMatch {

    private final ValueTypes types;

    ValueMatch(ValueTypes types) {
        this.types = types;
    }

    /**
     * Whether {@code answered} is what {@code stated} states.
     *
     * <p>The two grains in one place, so that what being the same answer means is settled once: a
     * reader that told them apart itself would have the other grain's comparison somewhere else,
     * and two comparisons of one statement can disagree.
     */
    Verdict verdict(Expectation.Asserts stated, ObservedValue answered, Position answers) {
        return switch (stated) {
            case Expectation.TheValue(Asserted value) -> {
                Mismatch differs = compare(value, answered, answers);
                yield differs == null ? Verdict.HELD : new Verdict.NotHeld(differs);
            }
            // The case, and nothing under it: there is no value under a case to compare, and
            // holding a whole value against one would report a difference nobody stated. Which case
            // an answer is is the declaration it is of, which the reading that produced the answer
            // already settled.
            case Expectation.TheCase(TypeSymbol name) -> {
                if (answered.unread() != null) {
                    yield new Verdict.NotHeld(new Mismatch(List.of(), Mismatch.Reason.UNREADABLE,
                            stated, answered, answers));
                }
                yield name.equals(answered.declaredAs()) ? Verdict.HELD
                        : new Verdict.NotHeld(new Mismatch(List.of(), Mismatch.Reason.TYPE, stated,
                                answered, answers));
            }
        };
    }

    /** Null where the two are the same value. */
    Mismatch compare(Asserted stated, ObservedValue observed, Position position) {
        Difference differs = at(List.of(), stated(stated), observed, position);
        return differs == null ? null : mismatch(differs);
    }

    /**
     * Whether two values that were both arrived at are the same value.
     *
     * <p>Answered as a yes or a no. Where a difference is, and what stood at either end of it, is
     * what a text is told when an answer did not keep what it stated; two values neither of which
     * states anything have nothing to be told about, and a {@link Mismatch} made of them would name
     * one of them as what was expected.
     */
    boolean same(ObservedValue left, ObservedValue right, Position position) {
        return at(List.of(), observed(left), right, position) == null;
    }

    /**
     * What one side of a comparison is, as the walk reads it.
     *
     * <p>Four shapes, which is what deciding whether two values are the same takes: a value with no
     * parts, a construction, a sequence and a mapping. Not a value: nothing here is built to be
     * held, passed on or compared against anything but the walk that made it, and what {@link #from}
     * carries is the value it was made from rather than a value of its own. Kept inside this class
     * for that reason — reachable from the package it would be a third vocabulary beside the two
     * {@link Asserted} and {@link ObservedValue} are, which are held apart on purpose.
     */
    private sealed interface Compared {

        /** The value this was projected from, which a report about a difference quotes. */
        Origin from();
    }

    /** A value with no parts, as the value it is. */
    private record Leaf(ObservedValue value, Origin from) implements Compared {}

    /** A construction, under the name it is of and with the parts it holds. */
    private record Built(TypeSymbol type, Map<String, Compared> fields,
                         Origin from) implements Compared {}

    /** A sequence, and which sequence its side said it was: a text may say, an observation does
     *  not. */
    private record Elements(Asserted.Container container, List<Compared> elements,
                            Origin from) implements Compared {}

    /** A mapping, as the pairs it holds in the order its side holds them. */
    private record Entries(List<Pair> entries, Origin from) implements Compared {}

    /** One pair of a mapping. */
    private record Pair(Compared key, Compared value) {}

    /**
     * Which of the two a side was projected from.
     *
     * <p>Carried through the walk rather than worked out at the end, because what a report quotes
     * is the value at the place the two parted and not the value the comparison started from.
     */
    private sealed interface Origin {

        /** A text stated it. */
        record Stated(Asserted value) implements Origin {}

        /** An execution turned out to hold it. */
        record Observed(ObservedValue value) implements Origin {}
    }

    /**
     * Where two values parted, as the walk found it.
     *
     * <p>Everything a report needs, taken where the difference was found. A difference said as a
     * reason and a path alone would have whoever writes the report walk the two values again to
     * reach what stood there — which is the second walk this one traversal exists to do without.
     */
    private record Difference(List<PathElement> path, Mismatch.Reason reason, Compared left,
                              ObservedValue right, Position position) {}

    /** What a text stated, as the walk reads it. */
    private static Compared stated(Asserted stated) {
        Origin from = new Origin.Stated(stated);
        return switch (stated) {
            case Asserted.Value(ObservedValue value) -> new Leaf(value, from);
            case Asserted.Built built -> {
                Map<String, Compared> fields = new LinkedHashMap<>();
                built.fields().forEach((name, field) -> fields.put(name, stated(field)));
                yield new Built(built.type(), fields, from);
            }
            case Asserted.Elements elements -> {
                List<Compared> parts = new ArrayList<>();
                for (Asserted element : elements.elements()) {
                    parts.add(stated(element));
                }
                yield new Elements(elements.stated(), parts, from);
            }
            case Asserted.Entries entries -> {
                List<Pair> pairs = new ArrayList<>();
                for (Asserted.Entry entry : entries.entries()) {
                    pairs.add(new Pair(stated(entry.key()), stated(entry.value())));
                }
                yield new Entries(pairs, from);
            }
        };
    }

    /**
     * What an execution turned out to hold, as the walk reads it.
     *
     * <p>A sequence states no container. Which one it is is what the declaration reading it says,
     * and an observation that answered for it would be answering with the type that produced it —
     * which is the reading the position is here to make.
     */
    private static Compared observed(ObservedValue observed) {
        Origin from = new Origin.Observed(observed);
        return switch (observed) {
            case ObservedValue.Constructed constructed -> {
                Map<String, Compared> fields = new LinkedHashMap<>();
                constructed.fields().forEach((name, field) -> fields.put(name, observed(field)));
                yield new Built(constructed.type(), fields, from);
            }
            case ObservedValue.Sequence sequence -> {
                List<Compared> parts = new ArrayList<>();
                for (ObservedValue element : sequence.elements()) {
                    parts.add(observed(element));
                }
                yield new Elements(Asserted.Container.UNSTATED, parts, from);
            }
            case ObservedValue.Mapping mapping -> {
                List<Pair> pairs = new ArrayList<>();
                for (ObservedValue.Entry entry : mapping.entries()) {
                    pairs.add(new Pair(observed(entry.key()), observed(entry.value())));
                }
                yield new Entries(pairs, from);
            }
            // Said arm by arm rather than as what is left over, so that a value with a new shape is
            // read here rather than arriving as one with no parts.
            case ObservedValue.Bool _, ObservedValue.Integer _, ObservedValue.Decimal _,
                 ObservedValue.Text _, ObservedValue.Temporal _, ObservedValue.Unit _,
                 ObservedValue.Absent _, ObservedValue.Unknown _, ObservedValue.Truncated _ ->
                    new Leaf(observed, from);
        };
    }

    /** The difference, as a text that stated one side is told about it. */
    private static Mismatch mismatch(Difference differs) {
        if (!(differs.left().from() instanceof Origin.Stated(Asserted value))) {
            // The walk keeps each side's origin through every step, so a comparison entered with a
            // statement parts from the answer at a stated value. Reaching here is those two having
            // come apart, and what would be written instead is an observation reported as what a
            // text expected.
            throw new IllegalStateException("a comparison against what a text stated parted from it"
                    + " at a value no text stated");
        }
        return new Mismatch(differs.path(), differs.reason(), new Expectation.TheValue(value),
                differs.right(), differs.position());
    }

    private Difference at(List<PathElement> path, Compared left, ObservedValue right,
                          Position position) {
        if (right.unread() != null) {
            return differs(path, Mismatch.Reason.UNREADABLE, left, right, position);
        }
        return switch (left) {
            case Leaf leaf -> leaf(path, leaf, right, position);
            case Built built -> constructed(path, built, right, position);
            case Elements elements -> sequence(path, elements, right, position);
            case Entries entries -> mapping(path, entries, right, position);
        };
    }

    private static Difference differs(List<PathElement> path, Mismatch.Reason reason, Compared left,
                                      ObservedValue right, Position position) {
        return new Difference(path, reason, left, right, position);
    }

    private static List<PathElement> into(List<PathElement> path, PathElement step) {
        List<PathElement> out = new ArrayList<>(path);
        out.add(step);
        return List.copyOf(out);
    }

    /** A value with no parts against one. Absence is neither a type nor a value of one: a position
     *  holds nothing or holds something, and that is its own answer. */
    private Difference leaf(List<PathElement> path, Leaf left, ObservedValue right,
                            Position position) {
        ObservedValue value = left.value();
        if (value.unread() != null) {
            return differs(path, Mismatch.Reason.UNREADABLE, left, right, position);
        }
        if (value instanceof ObservedValue.Absent || right instanceof ObservedValue.Absent) {
            return value instanceof ObservedValue.Absent && right instanceof ObservedValue.Absent
                    ? null : differs(path, Mismatch.Reason.ABSENCE, left, right, position);
        }
        // Of one type before anything else, and which type a value with no parts is has one answer
        // for every reader of it. A text writing `1` where a `Decimal` came out wrote an `Int` — the
        // difference is written in the language, and reading a whole number as an amount is what a
        // boundary does and not what the text did.
        Type.Prim stated = value.primitive();
        Type.Prim answered = right.primitive();
        if (stated == null || stated != answered) {
            return value instanceof ObservedValue.Unit x && right instanceof ObservedValue.Unit y
                    && x.type().equals(y.type())
                    ? null : differs(path, Mismatch.Reason.TYPE, left, right, position);
        }
        return switch (value) {
            case ObservedValue.Bool x ->
                    differsUnless(path, x.value() == ((ObservedValue.Bool) right).value(), left,
                            right, position);
            case ObservedValue.Integer x ->
                    differsUnless(path, x.value() == ((ObservedValue.Integer) right).value(), left,
                            right, position);
            // A decimal is the amount it stands for, so two that differ only in scale are one amount
            // — the rule `Values.equal` states for the run-time values.
            case ObservedValue.Decimal x -> differsUnless(path,
                    x.value().compareTo(((ObservedValue.Decimal) right).value()) == 0, left, right,
                    position);
            case ObservedValue.Text x ->
                    differsUnless(path, x.value().equals(((ObservedValue.Text) right).value()), left,
                            right, position);
            case ObservedValue.Temporal x ->
                    differsUnless(path, x.iso().equals(((ObservedValue.Temporal) right).iso()), left,
                            right, position);
            default -> differs(path, Mismatch.Reason.UNREADABLE, left, right, position);
        };
    }

    private static Difference differsUnless(List<PathElement> path, boolean equal, Compared left,
                                            ObservedValue right, Position position) {
        return equal ? null : differs(path, Mismatch.Reason.VALUE, left, right, position);
    }

    /**
     * Two constructions. The names first, because a value is of its type before it is anything else:
     * a {@code Receipt} whose {@code total} was written as a number and one whose {@code total} is
     * an {@code AmountN} are not one value written two ways.
     */
    private Difference constructed(List<PathElement> path, Built left, ObservedValue right,
                                   Position position) {
        if (!(right instanceof ObservedValue.Constructed b) || !left.type().equals(b.type())) {
            return differs(path, Mismatch.Reason.TYPE, left, right, position);
        }
        Set<String> names = new LinkedHashSet<>(left.fields().keySet());
        names.addAll(b.fields().keySet());
        for (String name : names) {
            Compared x = left.fields().get(name);
            ObservedValue y = b.field(name);
            if (x == null || y == null) {
                // At the construction and not at the field: what differs is which places the two
                // hold, which is a fact about them rather than about a place one of them has not
                // got. Said at the field, the path would name somewhere the values beside it are
                // not from — and there is no value there to name it with.
                return differs(path, Mismatch.Reason.SHAPE, left, right, position);
            }
            List<PathElement> inside = into(path, new PathElement.Field(name));
            // The child's position comes from what this value's own type declares that field to be,
            // so nothing outside the value supplies one for what stands under it.
            Difference under = at(inside, x, y, types.field(left.type(), name));
            if (under != null) {
                return under;
            }
        }
        return null;
    }

    /**
     * Two sequences, ordered or not as what they are says. A {@code List} is its elements in order and
     * a {@code Set} is its elements; the value on the right is whichever its declaration made it, and
     * the one on the left is whichever it said it was — a side that said neither is read at the
     * right's, since {@code [ 1 ]} is how both are written and states no preference between them.
     */
    private Difference sequence(List<PathElement> path, Elements left, ObservedValue right,
                                Position position) {
        if (!(right instanceof ObservedValue.Sequence ys)) {
            return differs(path, Mismatch.Reason.TYPE, left, right, position);
        }
        Type open = position.opened() instanceof Position.At(Type type) ? type : null;
        Asserted.Container answered = open instanceof Type.SetOf ? Asserted.Container.SET
                : open instanceof Type.ListOf ? Asserted.Container.LIST : Asserted.Container.UNSTATED;
        if (left.container() != Asserted.Container.UNSTATED
                && answered != Asserted.Container.UNSTATED && left.container() != answered) {
            return differs(path, Mismatch.Reason.TYPE, left, right, position);
        }
        Asserted.Container reading =
                left.container() != Asserted.Container.UNSTATED ? left.container() : answered;
        Position element = switch (open) {
            case Type.ListOf l -> Position.at(l.element());
            case Type.SetOf s -> Position.at(s.element());
            case null, default -> Position.UNREAD;
        };
        if (left.elements().size() != ys.elements().size()) {
            return differs(path, Mismatch.Reason.SHAPE, left, right, position);
        }
        if (reading == Asserted.Container.SET) {
            return unordered(path, left, ys.elements(), element, right, position);
        }
        for (int i = 0; i < left.elements().size(); i++) {
            Difference under = at(into(path, new PathElement.Index(i)), left.elements().get(i),
                    ys.elements().get(i), element);
            if (under != null) {
                return under;
            }
        }
        return null;
    }

    /** A set: each element of one stands for one of the other, and which one is not part of the value. */
    private Difference unordered(List<PathElement> path, Elements left, List<ObservedValue> ys,
                                 Position element, ObservedValue right, Position position) {
        List<ObservedValue> remaining = new ArrayList<>(ys);
        for (Compared x : left.elements()) {
            int found = standing(path, x, remaining, Function.identity(), element);
            if (found < 0) {
                return differs(path, Mismatch.Reason.SHAPE, left, right, position);
            }
            remaining.remove(found);
        }
        return null;
    }

    /**
     * Which of {@code among} stands for {@code x}, or {@code -1} where none does.
     *
     * <p>The one step a set and a map are both matched by, and the one thing either of them asks
     * beyond what everything else is compared by: which value of the answer is the one the
     * statement is about, where nothing but the values says. Written once because it is one
     * question — a second way of finding it would be a second answer to what being the same value
     * means, held somewhere the first one is not.
     *
     * <p>{@code of} says how to reach the value from what the caller is holding: a set holds the
     * values and a map holds pairs whose keys are matched. Said that way rather than by having each
     * caller hand over a list of what is to be compared, because building one is work done once per
     * member of a scan that is already one per member — the same question, asked of what the caller
     * has, and nothing made to ask it.
     */
    private <T> int standing(List<PathElement> path, Compared x, List<T> among,
                             Function<T, ObservedValue> of, Position at) {
        for (int i = 0; i < among.size(); i++) {
            if (at(path, x, of.apply(among.get(i)), at) == null) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Which part of {@code answered} stands for which part of what {@code stated} states.
     *
     * <p>The correspondence the comparison settles, said out so that a report writing the two side
     * by side does not settle it again. A map's entries are paired by key and a set's elements
     * without an order, and both are answered by {@link #standing} — the same step, asked of the
     * same values at the same positions, so that two entries the comparison matched are two a
     * report puts together.
     *
     * <p><b>All of it, where a comparison stops at the first difference.</b> What is written out is
     * the whole answer, so the pairing is wanted under the parts that differ as much as under the
     * ones that do not. Nothing here decides whether they are the same, which is why walking on
     * says nothing it should not: a part that stands against nothing is recorded as standing
     * against nothing.
     */
    Alignment align(Asserted stated, ObservedValue answered, Position position) {
        return align(stated(stated), answered, position);
    }

    private Alignment align(Compared left, ObservedValue right, Position position) {
        return switch (left) {
            case Built built when right instanceof ObservedValue.Constructed b
                    && built.type().equals(b.type()) -> {
                Map<String, Alignment> fields = new LinkedHashMap<>();
                for (Map.Entry<String, Compared> each : built.fields().entrySet()) {
                    ObservedValue under = b.field(each.getKey());
                    if (under != null) {
                        fields.put(each.getKey(), align(each.getValue(), under,
                                types.field(built.type(), each.getKey())));
                    }
                }
                yield new Alignment.Built(fields);
            }
            case Elements elements when right instanceof ObservedValue.Sequence s ->
                    aligned(elements, s, position);
            case Entries entries when right instanceof ObservedValue.Mapping m ->
                    aligned(entries, m, position);
            // Two shapes that do not line up. What stands under one of them stands under nothing of
            // the other, which is what there is to say about it.
            case Leaf _, Built _, Elements _, Entries _ -> new Alignment.Leaf();
        };
    }

    /** A sequence, element by element: where it is read as a set, each of the answer's stands for
     *  whichever of the statement's it was found to be; otherwise for the one in its place. */
    private Alignment aligned(Elements left, ObservedValue.Sequence s, Position position) {
        Type open = position.opened() instanceof Position.At(Type type) ? type : null;
        Position element = switch (open) {
            case Type.ListOf l -> Position.at(l.element());
            case Type.SetOf set -> Position.at(set.element());
            case null, default -> Position.UNREAD;
        };
        boolean asASet = left.container() == Asserted.Container.SET || open instanceof Type.SetOf;
        List<Alignment> byElement = new ArrayList<>();
        if (!asASet) {
            for (int i = 0; i < s.elements().size(); i++) {
                byElement.add(i < left.elements().size()
                        ? align(left.elements().get(i), s.elements().get(i), element)
                        : new Alignment.Nothing());
            }
            return new Alignment.Elements(byElement);
        }
        // Each of the statement's elements takes the one of the answer's it stands for, asked the
        // way a set is matched everywhere else. Filled in by where the answer holds it, because what
        // is written out is the answer and an element nothing was found for is one nothing states.
        Alignment[] found = new Alignment[s.elements().size()];
        List<Integer> remaining = new ArrayList<>(found.length);
        for (int i = 0; i < found.length; i++) {
            remaining.add(i);
        }
        for (Compared x : left.elements()) {
            int at = standing(List.of(), x, remaining, i -> s.elements().get(i), element);
            if (at < 0) {
                continue;
            }
            int where = remaining.remove(at);
            found[where] = align(x, s.elements().get(where), element);
        }
        for (Alignment each : found) {
            byElement.add(each == null ? new Alignment.Nothing() : each);
        }
        return new Alignment.Elements(byElement);
    }

    /** A mapping: the answer's entries the statement wrote, in the order it wrote them, then the
     *  rest. */
    private Alignment aligned(Entries left, ObservedValue.Mapping m, Position position) {
        Position key = position.key();
        Position value = position.value();
        List<ObservedValue.Entry> remaining = new ArrayList<>(m.entries());
        List<Alignment.Placed> written = new ArrayList<>();
        for (Pair each : left.entries()) {
            int found = standing(List.of(), each.key(), remaining, ObservedValue.Entry::key, key);
            if (found < 0) {
                continue;
            }
            ObservedValue.Entry taken = remaining.remove(found);
            written.add(new Alignment.Placed(taken, align(each.value(), taken.value(), value)));
        }
        return new Alignment.Entries(written, List.copyOf(remaining));
    }

    /**
     * Two maps, matched by key rather than looked up by one. A key is the same key under the same rule
     * everything else here is compared by, which a hash lookup would answer with Java's equality — and
     * a key written under a name is not the base it wraps.
     */
    private Difference mapping(List<PathElement> path, Entries left, ObservedValue right,
                               Position position) {
        if (!(right instanceof ObservedValue.Mapping ys)) {
            return differs(path, Mismatch.Reason.TYPE, left, right, position);
        }
        Position key = position.key();
        Position value = position.value();
        if (left.entries().size() != ys.entries().size()) {
            return differs(path, Mismatch.Reason.SHAPE, left, right, position);
        }
        List<ObservedValue.Entry> remaining = new ArrayList<>(ys.entries());
        for (Pair entry : left.entries()) {
            int found = standing(path, entry.key(), remaining, ObservedValue.Entry::key, key);
            if (found < 0) {
                return differs(path, Mismatch.Reason.SHAPE, left, right, position);
            }
            ObservedValue.Entry match = remaining.remove(found);
            // The key the entry was found by, which says which entry this is. Every leaf of it was
            // compared to get here, so it is a value that is there — which is what a step naming an
            // entry has to be. A key that no text stated names no step: what a path is read for is a
            // report about a statement, and the comparison of two arrived-at values makes none.
            List<PathElement> inside = entry.key().from() instanceof Origin.Stated(Asserted stated)
                    ? into(path, new PathElement.Key(stated)) : path;
            Difference under = at(inside, entry.value(), match.value(), value);
            if (under != null) {
                return under;
            }
        }
        return null;
    }
}
