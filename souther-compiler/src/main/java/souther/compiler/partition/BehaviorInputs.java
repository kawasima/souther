package souther.compiler.partition;

import souther.compiler.check.NewtypeInners;
import souther.compiler.check.PublishedDeclarations;
import souther.compiler.check.ReadableFields;
import souther.compiler.check.Shape;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.Symbols;
import souther.compiler.check.TypeView;
import souther.compiler.inputs.Refinement;
import souther.compiler.inputs.TermPath;
import souther.compiler.observe.ObservedValue;
import souther.compiler.types.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What a behavior takes: what its inputs are called, what they are declared to be, and what those
 * names denote.
 *
 * <p>One value because reading a row at a position needs all three. A path names a parameter and
 * the fields under it, a row's values arrive in the order the parameters are declared, and how a
 * value at a position is written — the names it wears — is what the declared types say. Given only
 * the first two, a reader walks a row by taking fields off values, which is right until a field
 * sits under a name.
 *
 * <p>And because the same three are what a row is generated from. Two spellings of what a behavior
 * takes are two chances to read a position differently, which is the shape of every defect this
 * package has been fixing: a {@link MeasuredInput} is these inputs and the axes derived at them,
 * both taken from the one reading they were made from.
 *
 * <p><b>The walk itself does not leave this package.</b> What a behavior takes can be said from a
 * signature and is a fact about the declaration, so this is built wherever that is what is wanted.
 * Walking a row with it is the other thing, and it is only ever right beside geometry measured
 * against the same reading — a walk from one reading and classes from another place a row in
 * classes nothing measured at the position it was read by. So {@link #valuesAt} and
 * {@link #occurrencesAt} are this package's, and a reader outside it asks whatever holds both.
 */
public record BehaviorInputs(List<String> parameters, List<Type> types, RuleReadingSource rules,
                             souther.compiler.check.ReadingPolicy policy) {

    public BehaviorInputs {
        parameters = List.copyOf(parameters);
        types = List.copyOf(types);
    }

    /** The names the reading was made against. */
    public Symbols symbols() {
        return rules.symbols();
    }

    /** What the declarations the reading was made against say. */
    public PublishedDeclarations published() {
        return rules.published();
    }

    /**
     * The walk into what a row writes, of the input {@code read} was made of.
     *
     * <p>Taken from the reading rather than assembled beside it. What a behavior takes is what its
     * reading was made from, so a caller building this from a signature would be writing down a
     * second answer to that — and a row would be walked by one of them and measured by the other.
     */
    public static BehaviorInputs of(souther.compiler.inputs.InputReading read) {
        List<String> parameters = new ArrayList<>();
        List<Type> types = new ArrayList<>();
        for (souther.compiler.inputs.InputDomain.Parameter each : read.domain().parameters()) {
            parameters.add(each.name());
            types.add(each.type());
        }
        return new BehaviorInputs(parameters, types, read.rules(), read.domain().policy());
    }

    /** Which input {@code path} starts at, or -1 where the behavior has no such parameter. */
    int indexOf(TermPath path) {
        int at = parameters.indexOf(path.head());
        return at < types.size() ? at : -1;
    }

    /**
     * The values read at {@code path} off what a row wrote, which is one value at most positions and
     * however many were written at a position inside a sequence.
     *
     * <p>Read at the path and not written at it. Where a row puts a value and where a reading names
     * one part at a sum every case of which spreads a declaration: the name is readable at the sum
     * and a row writes one of the cases, so a walk asking where a value is written reaches nothing
     * at a name every reading of the model may use.
     *
     * <p>{@link WalkResult.Reached} holding none where the walk was taken and no value stands
     * there, and {@link WalkResult.CouldNotWalk} where it could not be taken at all. A caller
     * asking what is covered at a position is answered with what was written, and a list of none is
     * nothing written there — which the walk not having been made is not.
     *
     * <p>The one walk into a row's values, done with the declared types beside them. A field of a
     * record is reached through the names the record is written under: {@code data SlotN = Slot} is
     * one position whose fields a partition is derived at, and a row writes
     * {@code SlotN(Slot { flag = true })}. The path never spells those names — a newtype is not a
     * step — so what reads the path takes them off. Walked on the values alone, the derivation
     * reached a field that the reading of a row could not, and every row at such a position came
     * back unreadable.
     *
     * <p><b>On the way and not at the end.</b> What comes back is the value as the position wears
     * it, names and all. Which names the position itself is written under is what tells a class
     * from another there ({@link Classifier#inside}), so a walk that went on peeling would answer a
     * classifier with a value it no longer recognises — and the reading of what a position is would
     * have lost how it is written, one layer down from where this branch put it back.
     *
     * <p>An observation that stopped is handed on rather than walked into. It is not a record and
     * there is nothing under it, but it is also not a chain that leads nowhere: it is the reason
     * this position has no value, and it says that itself.
     *
     * <p>Which leaves nothing for the walk's own answer, and only that: the standing type and value
     * cannot take the step named next. At a field that is the reading not exposing the name at this
     * position, or a value that is not the construction the reading says stands there. Neither is
     * something an observation did, and no observation says why because nothing went wrong with one.
     */
    WalkResult<List<ObservedValue>> valuesAt(List<ObservedValue> inputs, TermPath path) {
        return switch (occurrencesAt(inputs, path)) {
            case WalkResult.Reached(List<Occurrence> found) ->
                    WalkResult.reached(found.stream().map(Occurrence::value).toList());
            case WalkResult.CouldNotWalk<List<Occurrence>> _ -> WalkResult.couldNotWalk();
        };
    }

    /**
     * One value a row put at {@code path}, and which element was taken to reach it at each step
     * inside a sequence.
     *
     * <p>The element and not only the value, because a relation between two positions is about one
     * element and not about the two sets. A row holding one person under a line and another over it
     * has values on both sides at {@code people[*].age} and values in both classes at
     * {@code people[*].status}, and which went with which is the whole of what a pair says — read
     * off the sets, the pairs one row covers are every combination of them, and a row is counted as
     * evidence for a combination none of its elements is in.
     *
     * @param at which element was taken, by the step it was taken at — the path up to and
     *           including that step inside a sequence. Empty where the path enters no sequence,
     *           which is one occurrence and stands with every other
     */
    public record Occurrence(Map<TermPath, Integer> at, ObservedValue value) {

        public Occurrence {
            at = Map.copyOf(at);
            // An occurrence is a value the walk arrived at. Where none did, the walk says so with
            // its own answer and hands back no occurrences at all.
            Objects.requireNonNull(value, "an occurrence is a value standing at the path");
        }

        /**
         * Whether this and {@code other} can be one reading of the row.
         *
         * <p>Every step the two took together was taken at the same element, and the steps they did
         * not take together are free. Keyed by the step rather than counted, so the rule is one
         * sentence and every case follows from it: two positions under one person agree about the
         * person; a zip code and a phone number under one person agree about the person and not
         * about the address or the phone; two positions under different parameters share nothing
         * and stand with each other however they are spelled; and a position inside no sequence
         * takes no step, so it stands with everything.
         *
         * <p>Counted instead — the first so many elements of one list against the first so many of
         * another — two sibling collections would be zipped, which is a relation neither the row
         * nor the model states.
         */
        public boolean agreesWith(Occurrence other) {
            for (Map.Entry<TermPath, Integer> each : at.entrySet()) {
                Integer beside = other.at().get(each.getKey());
                if (beside != null && !beside.equals(each.getValue())) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * The values at {@code path}, each with the elements taken to reach it.
     *
     * <p>Asked of the values and not of a row. What falls where is a question about a tuple of
     * values, and rows are not the only things that have one: a value a {@code let} binds and a
     * candidate this package composed are both read at these positions, and both have to be read
     * the way a written row is or the classes they land in are two readings rather than one.
     */
    WalkResult<List<Occurrence>> occurrencesAt(List<ObservedValue> inputs, TermPath path) {
        int at = indexOf(path);
        if (at < 0 || at >= inputs.size()) {
            return WalkResult.couldNotWalk();
        }
        List<Standing> standing = List.of(new Standing(inputs.get(at), types.get(at),
                TermPath.of(path.head()), Map.of()));
        for (TermPath.Step step : path.steps()) {
            List<Standing> next = new ArrayList<>();
            int took = 0;
            for (Standing each : standing) {
                if (each.step(step, rules.inners(), symbols(), published(), next)) {
                    took++;
                }
            }
            // A step is taken by everything standing here or the reading stops. What decides it is
            // the shape of a value against the type declared for it, and everything standing at one
            // position of one row was written under one declaration -- so this is all or none, and
            // a value nothing could read is carried down as one rather than failing the step. Kept
            // as one flag for the whole walk, what survived was answered with and whatever could
            // not be reached was left out with nothing saying so.
            if (took != standing.size()) {
                return WalkResult.couldNotWalk();
            }
            standing = next;
        }
        // Nothing here is a row that wrote no element, which is a reading that arrived: a step that
        // could not be taken has already said so above. Answered alike, a row writing the empty
        // list would be reported as one nothing could be read from.
        return WalkResult.reached(
                standing.stream().map(each -> new Occurrence(each.at(), each.value())).toList());
    }

    /**
     * What the declarations put where a value at {@code path} is written, or null where a value is
     * not written there at all.
     *
     * <p>The path read as where a new value goes, and not the walk {@link #occurrencesAt} takes. A
     * position's type is a fact about the declarations and a row is not needed to ask it — which is
     * what a caller composing a value at a position wants, since there is no row yet.
     *
     * <p><b>For composing a value and for nothing else.</b> This walk stops where a value is built,
     * so a sum whose cases share a spread answers nothing here — right for a caller writing a value
     * at the sum, and not an answer about what a number named there is measured on. That question
     * has an owner ({@link souther.compiler.inputs.Quantities#ordersOf}), and it is asked of the
     * reading of the input rather than worked out from what this returns.
     *
     * <p>Which is why reading a row takes its own steps rather than these. The two relations a path
     * step stands for — where a written value has a part, and what a value standing here may be read
     * as — are one answer at a record and part at that sum, and a walk over an observation that took
     * these would reach nothing at every name a model reads through a sum.
     *
     * <p>Null where the path and the declarations disagree, and null for a path this behavior has no
     * parameter for. Neither is a position with a type nothing could name: they are paths that name
     * no position of these inputs at all.
     */
    Type typeAtWrittenPath(TermPath path) {
        int at = indexOf(path);
        if (at < 0) {
            return null;
        }
        Type here = types.get(at);
        for (TermPath.Step step : path.steps()) {
            here = stepWrittenValue(step, here, rules.inners(), symbols(), published());
            if (here == null) {
                return null;
            }
        }
        return here;
    }

    /**
     * The type one step of a written value reaches from {@code from}, or null where a value written
     * here is at no such place.
     *
     * <p>Exhaustive over {@link TermPath.Step}, with no {@code default}. A step added later stops
     * this compiling, and stops {@link Standing#step} compiling as well, so neither relation is left
     * taking a new step by a rule the other one wrote.
     *
     * <p>A field is where a value written here put one, which is a field of the record it was
     * written as. A name every case of a sum spreads is somewhere else: a row writes one of the
     * cases, so what stands at that name is under whichever case was written and nothing stands at
     * the name itself. What is readable off such a value is a question with an owner
     * ({@link ReadableFields}), and it is asked of the reading rather than worked out from this.
     *
     * <p><b>Answered for whoever writes a value, and for nobody else.</b> Reading a row is the other
     * relation and takes its own steps ({@link Standing#step}), which is not a duplicate of this:
     * the two are one answer at a record and part at a sum whose cases share a spread. Where they
     * agree, each is written down on its own rather than one being read out of the other, so a
     * reader that means to move one is not moving both. The one caller is
     * {@link #typeAtWrittenPath}, and it is watched.
     */
    static Type stepWrittenValue(TermPath.Step step, Type from, NewtypeInners inners,
                                 Symbols symbols, PublishedDeclarations published) {
        TypeView view = TypeView.of(from, inners, symbols, published);
        return switch (step) {
            case TermPath.Step.Field named -> view.shape() instanceof Shape.Product product
                    ? product.fields().get(named.name()) : null;
            case TermPath.Step.Element _ -> view.shape() instanceof Shape.Sequence sequence
                    ? sequence.element() : null;
            // What a sum's case holds is the value the sum held, and what an optional holds is at
            // no name of its own — so both narrow the type at this position and nothing is
            // descended into.
            case TermPath.Step.Refine refine -> switch (refine.refinement()) {
                case Refinement.SumCase one -> view.shape() instanceof Shape.Sum
                        ? Type.ref(one.leaf()) : null;
                case Refinement.Presence presence ->
                        !(view.shape() instanceof Shape.Optional optional) ? null
                                : presence.present() ? optional.element() : view.declared();
            };
        };
    }

    /**
     * One observed value on the way down a path, with the type the reading exposes at that position.
     *
     * <p>A list of these and not one, because a step into what a sequence holds turns one value
     * into as many as it holds. Everything else keeps the count it had.
     *
     * <p><b>How an observed value is read, which is not where a written one has its parts.</b> The
     * steps here are this walk's own and none of them is taken by asking
     * {@link BehaviorInputs#stepWrittenValue}. A field every case of a sum spreads is where the two
     * relations part: it is readable at every value of the sum and a row writes one of the cases, so
     * a walk taking the written relation reached nothing at a name every reading of the model uses.
     *
     * <p>The steps beside a field come to one answer under both today, and each says so on its own:
     * what this walk admits at an element and at a narrowing is written down where the walk is
     * tested and what the written relation lands on is written down where that is, rather than
     * either being read out of the other. Nothing holds the two together, deliberately — the day
     * one of them means to move, the other has to go on saying what it said.
     */
    private record Standing(ObservedValue value, Type type, TermPath reached,
                            Map<TermPath, Integer> at) {

        /**
         * Takes {@code step}, adding what stands below. False where this type and value cannot take
         * it.
         *
         * <p>False and standing nowhere are two answers. False is a step this walk cannot take at
         * all — the reading does not expose the name here, or what stands here is not the
         * construction the reading says does — and adding nothing is a step taken by a value that
         * turns out to stand nowhere below it: the empty list at an element, a case the row is not
         * at under a refinement. A caller reads the first as a walk it could not make and the
         * second as a row that is somewhere else.
         */
        boolean step(TermPath.Step step, NewtypeInners inners, Symbols symbols,
                     PublishedDeclarations published, List<Standing> out) {
            if (value.unread() != null) {
                out.add(this);
                return true;
            }
            TypeView view = TypeView.of(type, inners, symbols, published);
            ObservedValue here = Classifier.inside(view.wrappers(), value);
            if (here.unread() != null) {
                out.add(new Standing(here, type, reached, at));
                return true;
            }
            switch (step) {
                // Two things and in this order: the reading says whether the name may be read at
                // this position, and the value in hand says what is there. A concrete case carries
                // its own fields as well as the ones it spreads, so a walk that took the name off
                // the value it happens to hold would read `method.cardNumber` on the rows that are
                // cards and refuse it on the rest — a readability decided per row, which is not
                // something the model states. The name is admitted by what every value of the
                // position carries, and then the case is where it is taken from.
                case TermPath.Step.Field named -> {
                    Type next = ReadableFields.at(view.shape(), named.name());
                    if (next == null || !(here instanceof ObservedValue.Constructed made)) {
                        return false;
                    }
                    ObservedValue held = made.field(named.name());
                    if (held == null) {
                        return false;
                    }
                    out.add(new Standing(held, next, reached.then(named.name()), at));
                }
                // Every element the row wrote, and no choice among them. Which of them a rule is
                // about is not something the coordinate says, so what stands here is all of them
                // and what a class comes to over them is the caller's to decide. A list holding
                // none is a step taken: the walk arrived and the row wrote nothing there.
                case TermPath.Step.Element _ -> {
                    Type element = view.shape() instanceof Shape.Sequence sequence
                            ? sequence.element() : null;
                    if (element == null || !(here instanceof ObservedValue.Sequence written)) {
                        return false;
                    }
                    // Keyed by the step, which is this path with the step taken. Two positions
                    // that took it took the same one or they are not one reading of the row.
                    TermPath inside = reached.element();
                    for (int i = 0; i < written.elements().size(); i++) {
                        Map<TermPath, Integer> deeper = new java.util.LinkedHashMap<>(at);
                        deeper.put(inside, i);
                        out.add(new Standing(written.elements().get(i), element, inside, deeper));
                    }
                }
                // The value stays where it is and what may stand there narrows. A row whose value
                // does not meet the narrowing takes the step and stands nowhere below it — the
                // same answer a row writing the empty list gives at an element, and not the answer
                // a row nothing could read gives. What refuses the step is the type and the path
                // disagreeing about what is at this position, which is nothing about the row.
                // And the position it narrows to is where a case's own field becomes readable: a
                // path that names the case may read what only that case declares, which is the
                // model saying so rather than a row happening to be one.
                case TermPath.Step.Refine refine -> {
                    Type narrowed = switch (refine.refinement()) {
                        case Refinement.SumCase one -> view.shape() instanceof Shape.Sum
                                ? Type.ref(one.leaf()) : null;
                        case Refinement.Presence presence ->
                                !(view.shape() instanceof Shape.Optional optional) ? null
                                        : presence.present() ? optional.element() : view.declared();
                    };
                    if (narrowed == null) {
                        return false;
                    }
                    if (stands(refine.refinement(), here)) {
                        out.add(new Standing(here, narrowed, reached.refine(refine.refinement()),
                                at));
                    }
                }
            }
            return true;
        }

        /** Whether the value written here is one {@code refinement} leaves standing. */
        private static boolean stands(Refinement refinement, ObservedValue here) {
            return switch (refinement) {
                case Refinement.SumCase one -> switch (here) {
                    case ObservedValue.Unit unit -> one.leaf().equals(unit.type());
                    case ObservedValue.Constructed made -> one.leaf().equals(made.type());
                    default -> false;
                };
                case Refinement.Presence presence ->
                        presence.present() != (here instanceof ObservedValue.Absent);
            };
        }
    }
}
