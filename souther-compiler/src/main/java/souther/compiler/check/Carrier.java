package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.core.Core;
import souther.compiler.numeric.Count;
import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.Place;
import souther.compiler.numeric.Dates;
import souther.compiler.numeric.DateTimes;
import souther.compiler.numeric.Granularity;
import souther.compiler.numeric.Instants;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.numeric.OrderedInterval;
import souther.compiler.numeric.Times;
import souther.compiler.numeric.Towards;
import souther.compiler.observe.ObservedValue;
import souther.compiler.regex.Language;
import souther.compiler.regex.Meter;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.ValueName;
import souther.compiler.values.Admits;
import souther.compiler.values.Emptiness;
import souther.compiler.values.Value;
import souther.compiler.values.ValueSet;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * An order a rule is read against and a value is written back at, and the only place either
 * direction is crossed.
 *
 * <p>A type takes part by having an order-preserving count for its values <em>and a way back</em>.
 * Whether a type has both is this question, asked here and nowhere else: it was answered in three
 * places that disagreed — a predicate deciding what a report said, a reader deciding what an
 * invariant bounded, and a table deciding how a value was written back — so a {@code Date} was a
 * carrier to one of them and not to another, and the disagreement showed up as a bound that
 * vanished without a word.
 *
 * <p><b>One type and not two.</b> Reading a rule and writing a value were separate types while a
 * {@code Time} and an {@code Instant} could be read and not written, and what kept them apart was
 * two conversions nobody had written rather than anything about the values. Written, the two are
 * carriers like the rest and the smaller type had no members left: a distinction no value takes is
 * one nothing can check, and the next reader would have had to guess what it claimed (issue #846).
 *
 * <p><b>Closed both ways.</b> Everything that turns a value into a {@link Count} and everything that
 * turns a {@link Count} back into a value is a method here. Outside them nothing may read a count as
 * a number a model wrote, or build a value out of one: those were the leaks, and each of them was a
 * reader that had a carrier available and did not ask it. A reader that sniffed a written temporal
 * for a {@code T} to decide whether it was counting days or seconds is the shape they all had — the
 * declared type says which, and guessing from the text is a second answer to a question already
 * answered.
 *
 * <p><b>Sealed, so a carrier added is one every reader has to answer for.</b> Each switch below is
 * over these eight and nothing else, which is what makes a ninth a build failure rather than a wrong
 * value. That mattered least while every carrier's counts looked like numbers a model might write;
 * an ordinal is a small integer, which is the most plausible-looking wrong value of them all and the
 * one a report is least likely to give away.
 *
 * <p><b>Which types have one</b> is {@link #ofValue}, and it is the one table. Deciding twice is
 * what left a {@code Date} a carrier to one reader and not to another.
 */
public sealed interface Carrier {

    /** A whole number: an {@code Int}, and every size. */
    record Whole() implements Carrier {}

    /** A decimal. */
    record Dense() implements Carrier {}

    /** A day count, standing for a date. */
    record Days() implements Carrier {}

    /** A second count, standing for a date-time. Apart from {@link Days}: two units in one carrier
     *  would leave a line drawn on a day beside one drawn on a second with nothing saying which. */
    record Seconds() implements Carrier {}

    /**
     * A second of the day, standing for a time of day.
     *
     * <p>A {@code Time} is held to the second (spec §a-local-temporal-is-held-to-the-second), so the
     * day runs from 0 to 86399 and the value beside a line is the second next to it.
     */
    record SecondsOfDay() implements Carrier {}

    /**
     * A nanosecond from the epoch, standing for a moment on the timeline.
     *
     * <p>Apart from {@link Seconds} because the units differ: an {@code Instant} is held to the
     * nanosecond (spec §an-instant-carries-what-a-timestamp-said) and a {@code DateTime} to the
     * second, and two units in one carrier would leave a line drawn at one of them with nothing
     * saying which.
     */
    record Nanos() implements Carrier {}

    /**
     * A string, standing for itself.
     *
     * <p>The one carrier with no count under it. What that costs is the value beside a line and
     * nothing else: a string has no predecessor, so the row just below a line cannot be written —
     * which is what {@link Granularity#DENSE} already says of a carrier, and is the same answer a
     * {@code Decimal} and a date-time get. What it does not cost is the line itself, the classes
     * either side of it, or the row at it, and those were being lost with it.
     */
    record Text() implements Carrier {}

    /**
     * The place a case takes in its enumeration's declaration, standing for the case.
     *
     * <p>Carries the cases because the count means nothing without them: 1 is a day, a second, a
     * number and {@code Qualified} depending on what is being counted, and only here is that ever
     * decided. Which enumeration a value is ordered by belongs to the sum rather than to the case —
     * a unit data may be listed by two sums that place it differently — so the sum is named as well.
     *
     * @param enumeration the sum whose declaration order this counts in
     * @param cases       its leaf cases, in that order
     */
    record Ordinal(TypeSymbol enumeration, List<TypeSymbol> cases) implements Carrier {

        public Ordinal {
            cases = List.copyOf(cases);
        }

        /** Where {@code name} comes in the declaration, or null where this enumeration has no such
         * case — which is a value of some other type and not a place on this order. */
        public Place at(TypeSymbol name) {
            int index = cases.indexOf(name);
            return index < 0 ? null : Count.of(index);
        }

        /** The case at a count. Only ever asked of a count this carrier holds, which is what
         * {@link Carrier#onTheGrid} is for. */
        public TypeSymbol caseAt(Place count) {
            return cases.get(Count.number(count).at().intValueExact());
        }
    }

    Carrier WHOLE = new Whole();
    Carrier DENSE = new Dense();
    Carrier DATE = new Days();
    Carrier MOMENT = new Seconds();
    Carrier TIME = new SecondsOfDay();
    Carrier INSTANT = new Nanos();

    Carrier TEXT = new Text();

    /**
     * The carrier of {@code type} where the type settles it without the declarations, and null where
     * answering would need them or nothing orders it.
     *
     * <p>Beside {@link #ofValue} and not instead of it — the same table, asked where there are no
     * declarations to hand. A primitive is what it is; a name is a declaration's question, since
     * which sum places a case and what a newtype wraps are answers only the declarations have. So a
     * name comes back unanswered here rather than guessed at.
     *
     * <p>So a question asked this way is not asked of an operation declared over a name, and what
     * that costs is measurable: the names in the library's own signatures are its error unions and
     * the sum a rounding mode is, and every operation carrying one carries a primitive beside it
     * that answers. None of them falls out of range on this account.
     */
    static Carrier ofPrimitive(Type type) {
        return type instanceof Type.Prim ? ofValue(type, null) : null;
    }

    /**
     * Whether values of {@code type} count to a number.
     *
     * <p>Wider than {@link NumericAnswers#isANumber} and asked where the arithmetic is over counts
     * rather than over numbers a model wrote: a date is not a number and counts days, so a
     * difference of two of them is a number of days. Answered here because this is the one table
     * saying which types have an order with counts under it. Answered beside a reader instead, the
     * reader owns a classification it also consumes, and the next reader either borrows from it or
     * writes a second table — and a type that is a carrier to one of them and not to the other is
     * what that costs.
     *
     * <p>Asked without the declarations, as {@link #ofPrimitive} is: the type settles it and a name
     * comes back unanswered. What that leaves out is measured there.
     */
    static boolean countsToANumber(Type type) {
        Carrier carrier = ofPrimitive(type);
        return carrier != null && carrier.counts();
    }

    /**
     * The carrier a location's own content is counted on, or null where nothing here orders it.
     *
     * <p>Asked of what the names wrap, so a newtype answers as the value it carries — which is what
     * makes {@code data Cutoff = Date} the same carrier as a bare {@code Date}, and
     * {@code data StageN = Stage} the same carrier as a bare {@code Stage}.
     */
    static Carrier ofValue(Type type, Symbols symbols) {
        // Which order a value of this type is compared on is {@link Ordering}'s, and this asks it
        // rather than deciding what an enumeration is a second time. Every one of its answers is
        // answered for here, so an order added there is one this has to place or say it has no
        // count for — the direction #856 went silent in was a reader measuring what another refused.
        Ordering how = Ordering.of(type, symbols);
        if (how == null) {
            return null;
        }
        Type base = TypeOps.base(type, symbols);
        return switch (how.opened()) {
            // Being ordered is not being counted: two dates order alike whatever a line on one is
            // counted in, and which count that is belongs here and is asked of the type.
            case Ordering.Longs _, Ordering.Natural _ -> countOf(base);
            // What is left is the one thing a carrier asks that an order does not: whether the
            // position's values range over the whole of it. A case and a union of cases are
            // comparable on their sum's order without ranging over it, and a position declared as
            // one case, given the sum's counts, was asked for a row at a value it cannot hold.
            case Ordering.Places places -> base instanceof Type.Ref ref
                    && ref.name().equals(places.enumeration()) ? ordinalOf(places, symbols) : null;
            // `opened` answers for the value with the names off, which is never one still wearing
            // them.
            case Ordering.Wrapped _ ->
                    throw new IllegalStateException("an opened order is never a wrapped one: " + type);
        };
    }

    /** The count a primitive's values are placed on. */
    private static Carrier countOf(Type base) {
        if (!(base instanceof Type.Prim prim)) {
            return null;
        }
        return switch (prim) {
            case INT -> WHOLE;
            case DECIMAL -> DENSE;
            case DATE -> Carrier.DATE;
            case DATETIME -> MOMENT;
            case TIME -> Carrier.TIME;
            case INSTANT -> Carrier.INSTANT;
            // `String` is ordered lexicographically and stands for itself, having no count to
            // embed into and needing none. `Bool` and `Raw` are not ordered at all.
            case STRING -> TEXT;
            case BOOL, RAW -> null;
        };
    }

    /** The cases in the order they are declared, which is the order itself and not a set. The
     *  declaration is read for that list alone: whether this is an enumeration is already answered. */
    private static Carrier ordinalOf(Ordering.Places places, Symbols symbols) {
        if (!(symbols.declaredNode(places.enumeration()) instanceof Hir.SumData sum)) {
            return null;
        }
        List<TypeSymbol> cases = AtomSpace.subjectAtoms(Type.ref(sum.declares()), symbols);
        return cases.isEmpty() ? null : new Ordinal(places.enumeration(), cases);
    }

    /**
     * Whether this order's places count to a number.
     *
     * <p>What makes the distance between two of its values a value itself. Seven of the eight count;
     * a string is ordered and has no number under it, so two strings a rule holds apart stand in an
     * order and stand no measurable distance apart. Asked here rather than by testing for the one
     * carrier, so a ninth added answers it the way it answers everything else about itself.
     */
    default boolean counts() {
        return !(this instanceof Text);
    }

    /**
     * Whether a value on this order stands somewhere relative to a value on that one.
     *
     * <p>What a rule comparing two positions needs of the orders they are written back on, and the
     * whole of it. Where the counts are one arithmetic the two stand a number apart; where the order
     * is one and has no counts at all they stand above or below or level, which is a comparison and
     * is not a distance. Both are lines a model draws and neither is a claim the other makes.
     *
     * <p>Written as the second disjunct rather than by making {@link #sharesCountSpaceWith}
     * reflexive. A string shares its counts with nothing because it has none, and saying otherwise
     * to make one caller shorter would leave that one meaning two things — the pair that has no
     * number between them is exactly the pair a caller must not go on to measure.
     */
    default boolean standsAgainst(Carrier other) {
        return sharesCountSpaceWith(other) || (equals(other) && !counts());
    }

    /**
     * Whether both carriers' counts are numbers of one origin and one scale, so that subtracting one
     * from the other with coefficients of one and minus one names a distance without converting
     * anything.
     *
     * <p>What a quantity has to answer before it is read as how far two positions stand apart
     * ({@code BorderQuantity.Apart}). A form over several positions needs nothing of the kind: its
     * coefficients are where a conversion is written, which is how a date-time built out of a date
     * and a time comes to {@code b - 86400 * d - t} over three carriers that share no counts at all.
     * A distance has no coefficients to carry one, so what it takes is asked here.
     *
     * <p><b>Not "the same kind of number".</b> A second count and a nanosecond count are both a
     * position on one timeline and are not one count: what a step of one means differs, so the
     * difference of a second and a nanosecond is a number in neither. A second of the day and a
     * second of an epoch are the same step from different origins, which is the same answer for the
     * other reason. Told apart by whether a value could be converted, both pairs would have come
     * back true and the distance would have been off by whatever the conversion was.
     *
     * <p><b>Not reflexive, and that is the definition rather than an omission.</b> A string has no
     * count, so there is no count space it shares with anything — including another string. Two
     * strings still stand in an order and still meet, which is a quantity's own answer
     * ({@link #counts}) and not this one's. Made reflexive to look tidier, this would say two
     * strings stand a measurable distance apart.
     */
    default boolean sharesCountSpaceWith(Carrier other) {
        return switch (this) {
            // Whole numbers and decimals are written on one line and step differently along it,
            // which is a question about the values beside a distance and not about the distance.
            case Whole _, Dense _ -> other instanceof Whole || other instanceof Dense;
            case Days _ -> other instanceof Days;
            case Seconds _ -> other instanceof Seconds;
            case SecondsOfDay _ -> other instanceof SecondsOfDay;
            case Nanos _ -> other instanceof Nanos;
            // The enumeration and not the cases. What a count means here is the place in one
            // declaration's order, so two sums are two spaces however alike their case lists are.
            case Ordinal ordinal -> other instanceof Ordinal them
                    && ordinal.enumeration().equals(them.enumeration());
            case Text _ -> false;
        };
    }

    /** How the counts on this carrier are spaced, which is what decides whether a strict bound has a
     * next count to step to. */
    default Granularity spacing() {
        return switch (this) {
            // A date-time steps too: it is held to the second (spec
            // §a-local-temporal-is-held-to-the-second), which is the decision `DateTimes` recorded
            // as nobody's, so a strict bound on one has a count to sharpen onto. A time of day and a
            // moment step for the same reason, each at its own unit.
            case Whole _, Days _, Ordinal _, Seconds _, SecondsOfDay _, Nanos _ ->
                    Granularity.DISCRETE;
            // No smallest step this language names. A strict bound then leaves its end on the count
            // it names and says that count is not one of its own, rather than inventing a step in.
            // A string has no next string this language names — naming it means choosing a
            // character this language does not name either — so a strict bound leaves its end on the
            // value it names and the row beside a line is not asked for.
            case Dense _, Text _ -> Granularity.DENSE;
        };
    }

    /**
     * Every value this order has, as the ends it runs between.
     *
     * <p>Where a reading of the rules starts. Neither the spacing nor the literals say it, and a
     * reading that started every position unbounded has no way to find out that a rule states an end
     * the order does not reach: {@code value < ""} leaves a range open below, and what is below the
     * empty string is nothing.
     */
    default OrderedInterval extent() {
        return switch (this) {
            case Whole _ -> between(Count.of(Long.MIN_VALUE), Count.of(Long.MAX_VALUE));
            // Every number, so no end either way.
            case Dense _ -> OrderedInterval.OPEN;
            case Days _ -> between(Count.of(java.time.LocalDate.MIN.toEpochDay()),
                    Count.of(java.time.LocalDate.MAX.toEpochDay()));
            case Seconds _ -> between(DateTimes.MIN, DateTimes.MAX);
            case SecondsOfDay _ -> between(Times.MIN, Times.MAX);
            case Nanos _ -> between(Instants.MIN, Instants.MAX);
            // Every string is at or above the empty one, and there is no longest string.
            case Text _ -> new OrderedInterval(
                    Endpoint.inclusive(souther.compiler.numeric.Text.of("")), null);
            case Ordinal ordinal -> between(Count.of(0), Count.of(ordinal.cases().size() - 1L));
        };
    }

    private static OrderedInterval between(Place low, Place high) {
        return new OrderedInterval(Endpoint.inclusive(low), Endpoint.inclusive(high));
    }

    /**
     * A range of this order with no value in it, which is what a rule stepping past its end leaves.
     *
     * <p>Written as ends of this order rather than as a flag on the range, so that a range holding
     * nothing is the same kind of thing however it was arrived at: two rules whose ends cross leave
     * one of these, and so does one rule naming an end the order does not reach.
     *
     * <p>Only a stepping order can be stepped off, and every stepping order this has stops at both
     * ends — there is no last decimal and no last string, and neither steps. So the end is there to
     * be named, and a carrier that reached here without one is a mistake in this compiler.
     */
    default OrderedInterval nothing() {
        OrderedInterval extent = extent();
        Endpoint end = extent.high() != null ? extent.high() : extent.low();
        if (end == null) {
            throw new IllegalStateException("an order with no end was stepped off: " + this);
        }
        // Open at one place, on both sides of it: nothing is above a value and below it at once.
        return new OrderedInterval(Endpoint.exclusive(end.at()), Endpoint.exclusive(end.at()));
    }

    /**
     * The count as this carrier can actually hold it, or null where it holds nothing there.
     *
     * <p>Not every number between two of this carrier's counts is one of them. Halfway between two
     * adjacent moments is a number and not a date-time, because what a date-time can be written as
     * sits on a grid at the second.
     *
     * <p>Asked wherever a count is about to stand for a value. Left unasked, a class open at both
     * ends between two adjacent moments offered the count between them, which was written back as one
     * of the ends — a row labelled for a class it is not in.
     */
    default Place onTheGrid(Place count) {
        if (count == null) {
            return null;
        }
        return switch (this) {
            // A decimal holds every number and a string is every string: the ranges and the values
            // are the same things. Its own, though — this is where a carrier says which places are
            // its, so a place of some other carrier is not one of them however little else would
            // have noticed.
            case Dense _ -> count instanceof Count ? count : null;
            case Text _ -> count instanceof souther.compiler.numeric.Text ? count : null;
            // A whole number, a day count, an ordinal, a second of the day and a nanosecond step, so
            // a number between two of them is neither, and each stops where what carries it stops.
            // Asked here rather than at each place that steps one, because a step off the end is the
            // same non-value however it was reached — and an enumeration's ends are the nearest of
            // them all, one step past its last case.
            case Whole _, Ordinal _, Days _, SecondsOfDay _, Nanos _ ->
                    countsWhole(count) && extent().admits(count) ? count : null;
            // Where the calendar stops first, and then on the grid inside it. A date-time is
            // bounded at both ends and spaced besides, and asking the writer alone would be asking
            // it to answer for a count it exists to write — which it does by throwing, out of a
            // question whose whole job is to answer no.
            //
            // Round-tripped and then held to itself. The writer floors a count onto the second, so
            // returning what came back would answer "the nearest count this carrier holds" to a
            // question that asks whether it holds this one. A caller reading that as a yes offers a
            // value between two moments as one of them.
            case Seconds _ -> {
                if (!(count instanceof Count) || !DateTimes.holds(count)) {
                    yield null;
                }
                Place written = DateTimes.secondOf(DateTimes.written(count));
                yield written != null && written.sameAs(count) ? count : null;
            }
        };
    }

    /** Whether a place counts to a whole number, which is what a stepping order is made of. A place
     * that is not a number is not one. */
    private static boolean countsWhole(Place at) {
        return at instanceof Count count && count.whole();
    }

    /**
     * The count a rule's literal names on this carrier, or null where the expression names none.
     *
     * <p>Which literals a rule may be bounded by is a fact about what carries the value and not about
     * the reader that wants one, so it is answered here. It was being answered separately by each
     * reader instead, and an invariant and a {@code guard} at one position admitted different rules
     * with only one of them saying so.
     *
     * <p><b>No names to take off.</b> The {@link #literalOf(Core, Symbols)} beside this one peels a
     * newtype's construction, and this one does not, which is a difference in what the two are handed
     * rather than one of them forgetting: a rule may not construct a data, so a bound is never one
     * (E1105 for an invariant, E1017 for an {@code ensures}). Peeling here would be a rule about
     * expressions this compiler refuses to have, and nothing would keep it right. Those refusals are
     * therefore load-bearing for a reader that does not name them, and
     * {@code AConstructionIsWrittenInABodyAndNotInARuleTest} is where they are held.
     */
    default Place literalOf(Hir.Expr e) {
        return switch (this) {
            case Whole _ -> Count.of(NumericLiterals.wholeLiteralOf(e));
            case Dense _ -> Count.of(NumericLiterals.literalOf(e));
            case Days _ -> temporal(e, Dates::dayOf);
            case Seconds _ -> temporal(e, DateTimes::secondOf);
            case SecondsOfDay _ -> temporal(e, Times::secondOf);
            case Nanos _ -> temporal(e, Instants::nanoOf);
            // A case is named rather than written, so the literal is a name and what it denotes says
            // which case it is. Read off the denotation and not the text: a case is reachable under
            // an alias, and two enumerations may declare cases spelled the same way.
            case Ordinal ordinal -> e instanceof Hir.Var.Denoting v
                    && v.denotes() instanceof ValueName.OfType named
                    ? ordinal.at(named.type()) : null;
            case Text _ -> e instanceof Hir.StringLit lit
                    ? souther.compiler.numeric.Text.of(lit.value()) : null;
        };
    }

    /**
     * The count a rule's literal names on this carrier, read from the body's own representation.
     *
     * <p>The pair of the reading above. A {@code guard}'s comparison reaches its reader as
     * {@code Core} and an invariant's bound as {@code Hir}, and a rule read at one and not the other
     * is a rule about the representation rather than about the model — which is what the two readers
     * deciding separately what a literal is already cost once.
     */
    default Place literalOf(Core e, Symbols symbols) {
        Core bare = bare(e, symbols);
        return switch (this) {
            case Whole _, Dense _ -> switch (bare) {
                case Core.Int i -> onTheGrid(Count.of(i.value()));
                case Core.Decimal d -> onTheGrid(Count.of(d.value()));
                // A minus in front of a value is part of the value written down, and these are the
                // only carriers with one to write: nothing negates a date, a case or a string.
                case Core.Neg n -> {
                    Place inner = literalOf(n.operand(), symbols);
                    yield inner == null ? null : Count.number(inner).negate();
                }
                case null, default -> null;
            };
            case Days _, Seconds _, SecondsOfDay _, Nanos _ ->
                    bare instanceof Core.Temporal written
                            ? placeOf(new ObservedValue.Temporal(written.text())) : null;
            // A case, which is named rather than written. Where the position counts in some other
            // enumeration's declaration this is a value of neither, and that is said by `at`.
            case Ordinal ordinal ->
                    bare instanceof Core.UnitValue unit ? ordinal.at(unit.data()) : null;
            case Text _ -> bare instanceof Core.Str str
                    ? souther.compiler.numeric.Text.of(str.value()) : null;
        };
    }

    /**
     * What {@code e} writes, with the names around it taken off.
     *
     * <p>A newtype's construction around a value is that value at this position, and it is taken off
     * here for the reason {@link #ofValue} reads through one to find the carrier at all: a name is
     * not a step, and what carries the value is what the name wraps. The two walks are the same walk
     * and have to reach the same place — a position sent to a carrier by one and left unreadable by
     * the other has a carrier and no way to read the values its own model writes at it.
     *
     * <p>Which is what it cost: the peeling asked whether the base was a number, so
     * {@code Amount < Amount(100)} drew a line and {@code Cutoff < Cutoff(Time("16:00:00"))} drew
     * none, over the same construction and for no reason either type states. Every carrier's
     * newtypes are read now, and a carrier added is read with them.
     *
     * <p>What makes something one is the declaration and never the shape: a data of one field that
     * is not a newtype wraps its value rather than being it, so its construction is a value of its
     * own and is left alone.
     */
    private static Core bare(Core e, Symbols symbols) {
        return e instanceof Core.Construct nd && !nd.values().isEmpty()
                && TypeOps.isSingleValueNewtype(Type.ref(nd.typeName()), symbols)
                ? bare(nd.values().get(0).value(), symbols) : e;
    }

    /** The count a written temporal is, or null where the expression is not one of that kind. A
     *  temporal is written as a literal with its text spelled out (spec
     *  §a-temporal-value-is-written-as-a-literal), so it is read here rather than run. Which
     *  construction it is comes from the callee, for the reason {@link #written} gives.
     *
     *  <p>Whether the callee builds anything is {@link ValueName.Stdlib#constructs}, which is where
     *  that is settled. Being the namespace rather than an operation of it is the wider question: a
     *  namespace that builds nothing is one of these too, and the text handed to {@code countOf}
     *  would then be parsed as a moment because of where it stands. */
    private static Count temporal(Hir.Expr e,
                                  java.util.function.Function<String, Count> countOf) {
        return e instanceof Hir.Apply call && call.answered() != null
                && call.answered().denotes() instanceof ValueName.Stdlib lib
                && lib.constructs() != null
                && call.args().size() == 1 && call.args().get(0) instanceof Hir.StringLit iso
                ? countOf.apply(iso.value()) : null;
    }

    /**
     * A place inside a pair of ends, or null where they hold none.
     *
     * <p>The one way a range gives up a value, so that nothing deciding what to write reads an end
     * and loses whether the range holds it. An end the range holds is the place taken: it is inside
     * whatever other end there is, and it is what a boundary wants written anyway. An end the range
     * stops short of is not one, and over a carrier whose values do not step there is nothing beside
     * it to take instead — so the place comes from inside, between the ends where both are known and
     * a step in from the only one where there is one.
     *
     * <p>Asked of the carrier because the answer is the carrier's arithmetic. It sat on the ends
     * themselves, which is where it could only ever be a number's answer, and a carrier whose values
     * are not numbers had nothing to say through it.
     *
     * <p><b>What comes back is a value this carrier holds.</b> Which is one answer and not two:
     * {@link #onTheGrid} says which places are this carrier's, and a value chosen without asking it
     * is a value a caller has to check and may find refused — with nothing to tell it whether the
     * range held another. A caller that filtered afterwards lost the value at the very end of the
     * order every time a rule reached past it.
     *
     * <p>Null is this composing none and never the range holding none. Above a strict bound on a
     * string it declines deliberately: every string with that one as a prefix is inside, which of
     * them is a choice, and a choice made here puts a character nobody wrote into a row somebody has
     * to read.
     *
     * <p>From the low end, which is what a range with no end named asks for.
     */
    default Place somethingInside(Endpoint low, Endpoint high) {
        return somethingInside(low, high, Towards.ABOVE);
    }

    /**
     * The same, from whichever end of the range the caller wants the value nearest.
     *
     * <p>Which end is a question about the range and is asked here, where the arithmetic that
     * answers it already is. Decided by the caller instead, a reader that wanted the value beside a
     * line reached for a second way of getting one — "some value other than the line's" — and the
     * two ways were one policy said twice, only one of them on purpose. That is the shape a run
     * bounded at both ends was already lost to once (issue #903).
     */
    default Place somethingInside(Endpoint low, Endpoint high, Towards from) {
        if (this instanceof Text) {
            return someStringInside(low, high, from);
        }
        Granularity spacing = spacing();
        if (spacing == Granularity.DISCRETE) {
            // Held inside what this order reaches before a count is taken from it. A range that runs
            // past an end of the order starts at a count this carrier holds nothing at, and
            // {@link #onTheGrid} refuses that count — so a range from before the first time of day
            // to a second inside the day offered the count it started at, which is no time, while
            // the first second of the day lay in the range and was never offered. Which is the
            // range giving up no value where it holds one, and it is this that decides what the
            // range gives up.
            OrderedInterval reaches = extent();
            Endpoint lo = whole(Endpoint.lower(low, reaches.low()), true);
            Endpoint hi = whole(Endpoint.upper(high, reaches.high()), false);
            if (!Endpoint.someValueLiesBetween(lo, hi)) {
                return null;
            }
            // Which end is taken is still the range's own, and only where it has one. Read off the
            // held ends instead, a range nothing bounds below is bounded by the order and the value
            // taken is the first count the order has — the least whole number there is, offered as
            // the row an author should write.
            Endpoint wanted = from == Towards.ABOVE ? low : high;
            Endpoint other = from == Towards.ABOVE ? high : low;
            Endpoint taken = from == Towards.ABOVE ? lo : hi;
            Endpoint away = from == Towards.ABOVE ? hi : lo;
            // And zero where nothing bounds it either way, whichever end was asked for: a range with
            // no end has no end to be near, and the order's own is not one -- reached for there, the
            // greatest whole number there is would be the row an author is handed.
            return wanted != null ? taken.at() : other != null ? away.at() : Count.ZERO;
        }
        if (!Endpoint.someValueLiesBetween(low, high)) {
            return null;
        }
        if (from == Towards.BELOW) {
            if (high == null) {
                return low == null ? Count.ZERO
                        : low.inclusive() ? low.at() : count(low).plus(1);
            }
            if (high.inclusive()) {
                return high.at();
            }
            // Open above, so the place is not the end. Halfway to the other end where there is one,
            // and a step in where there is not.
            return low == null ? count(high).minus(1)
                    : count(low).halfwayTo(count(high), Granularity.DENSE);
        }
        if (low == null) {
            return high == null ? Count.ZERO
                    : high.inclusive() ? high.at() : count(high).minus(1);
        }
        if (low.inclusive()) {
            return low.at();
        }
        // Open below, so the place is not the end. Halfway to the other end where there is one — a
        // count the dense carrier holds, and inside both — and a step in where there is not.
        return high == null ? count(low).plus(1)
                : count(low).halfwayTo(count(high), Granularity.DENSE);
    }

    /**
     * A string inside a pair of ends, or null where nothing here names one.
     *
     * <p>An end the range holds is the string taken: it is what a boundary wants written anyway.
     * Below an end the range stops short of, the empty string is under every other one and is taken
     * — the least of them, and not a string this made up.
     *
     * <p>Above an end the range stops short of, nothing. Every string with that one as a prefix is
     * greater, so a value exists; which one it is is a choice, and a choice made here would be a
     * character this compiler picked appearing in a row somebody has to read. That is the same
     * restraint a decimal and a date-time already get above a strict bound, reached for the same
     * reason: the language names no next value, and inventing one tests a rule the model never
     * stated.
     */
    private static Place someStringInside(Endpoint low, Endpoint high, Towards from) {
        if (!Endpoint.someValueLiesBetween(low, high)) {
            return null;
        }
        // An end the range holds is the string taken, and the one the caller asked to be near first.
        // Only the lower one was looked at, so `"a" < x <= "b"` came back with nothing while holding
        // a value the model itself wrote two characters away.
        Endpoint first = from == Towards.ABOVE ? low : high;
        Endpoint second = from == Towards.ABOVE ? high : low;
        if (first != null && first.inclusive()) {
            return first.at();
        }
        if (second != null && second.inclusive()) {
            return second.at();
        }
        // Open above a string, and every string with that one as a prefix is inside. Which of them
        // is a choice, and a choice made here puts a character nobody wrote into a row somebody has
        // to read — the restraint a decimal and a date-time already get above a strict bound.
        if (low != null) {
            return null;
        }
        // Open below one, and the empty string is under every other. The least there is, not one
        // this made up.
        return high == null || !high.at().key().isEmpty()
                ? souther.compiler.numeric.Text.of("") : null;
    }

    /** An end moved onto the nearest whole count the range holds, which is always one it holds. */
    private static Endpoint whole(Endpoint end, boolean lower) {
        if (end == null) {
            return null;
        }
        java.math.RoundingMode away = lower ? java.math.RoundingMode.CEILING
                : java.math.RoundingMode.FLOOR;
        if (end.inclusive()) {
            return Endpoint.inclusive(count(end).rounded(away));
        }
        java.math.RoundingMode into = lower ? java.math.RoundingMode.FLOOR
                : java.math.RoundingMode.CEILING;
        Count step = count(end).rounded(into);
        return Endpoint.inclusive(lower ? step.plus(1) : step.minus(1));
    }

    /** The count an end is at. Only reached from the arithmetic above, which every carrier that has
     * none skips before it gets here. */
    private static Count count(Endpoint end) {
        if (!(end.at() instanceof Count at)) {
            throw new IllegalStateException("a carrier with no counts reached the arithmetic: " + end);
        }
        return at;
    }

    /**
     * A place the position holds that is none of {@code singled}, or null where this found none.
     *
     * <p>Where the values step, the one beside a singled-out value is the nearest thing to it and is
     * tried first. Over a carrier whose values do not step there is no step to take: a value bounded
     * to `+[0, 1]+` and singled out at `+0.5+` steps to `+1.5+` and `+-0.5+`, both outside, and
     * neither says anything about whether the class has values — it holds `+0+`, `+1+` and everything
     * between. So the ends of what the position admits are asked, and a value between them, before
     * any step is.
     *
     * <p>Null is this having found none and never the class being empty. Nothing here enumerates a
     * range, so what a caller may say about an empty answer is that it composed nothing.
     */
    default Place somethingOtherThan(java.util.List<Place> singled, NumericDomain.Bounds within) {
        java.util.List<Place> stepped = new ArrayList<>();
        for (Place from : singled) {
            if (from instanceof Count count) {
                stepped.add(count.plus(1));
                stepped.add(count.minus(1));
            }
        }
        java.util.List<Place> inside = new ArrayList<>();
        if (within != null) {
            for (Endpoint end : java.util.Arrays.asList(within.min(), within.max())) {
                if (end != null && end.inclusive()) {
                    inside.add(end.at());
                }
            }
            inside.add(somethingInside(within.min(), within.max()));
            // Between the place singled out and each end, which is where a range with no step still
            // has room once the ends themselves are singled out too.
            for (Place from : singled) {
                for (Endpoint end : java.util.Arrays.asList(within.min(), within.max())) {
                    if (end != null) {
                        inside.add(somethingInside(Endpoint.exclusive(from), end));
                        inside.add(somethingInside(end, Endpoint.exclusive(from)));
                    }
                }
            }
        }
        java.util.List<Place> tried = new ArrayList<>();
        if (spacing() == Granularity.DENSE) {
            tried.addAll(inside);
            tried.addAll(stepped);
        } else {
            tried.addAll(stepped);
            tried.addAll(inside);
        }
        // A string has a least value and nothing beside one, so what stands for "none of these" is
        // the empty string wherever that is not one of them. Last, so a domain that names its own
        // ends is asked first — and refused by the filter below where it is itself singled out.
        if (this instanceof Text) {
            tried.add(souther.compiler.numeric.Text.of(""));
        }
        for (Place candidate : tried) {
            // On the carrier's grid before it is asked anything. Halfway between two adjacent moments
            // is neither of them as a number and is one of them once written, so a class of
            // everything else was offered one of the values it exists to exclude.
            Place each = onTheGrid(candidate);
            if (each != null && (within == null || within.admits(each))
                    && singled.stream().noneMatch(each::sameAs)) {
                return each;
            }
        }
        return null;
    }

    /**
     * The range of this order that {@code bounds} names, where {@code bounds} counts what this
     * order counts.
     *
     * <p>The one crossing between the two, and it is here because this is where a count and a value
     * of this order are the same fact said twice. What the numbers prove about a position is a pair
     * of ends in the count space its atom lives in; what a reading of an order holds is a pair of
     * ends on the order. The two are written the same way and are not the same thing — a day count
     * is a number and a date is not — so the crossing is a question about the carrier and never a
     * pair of ends passed across.
     *
     * <p>Nothing where this order counts nothing. A string stands in an order and no number stands
     * under it, so a bound in a count space is about some other position and not this one — and
     * saying it says nothing here is the difference between a reading that could not speak and one
     * that spoke and placed no edge.
     */
    default PositionRestriction within(NumericDomain.Bounds bounds) {
        return counts()
                ? new PositionRestriction.Within(new OrderedInterval(bounds.min(), bounds.max()))
                : new PositionRestriction.NotSpokenOf();
    }

    /**
     * Whether any value this order holds inside {@code range} is one {@code set} admits.
     *
     * <p>The one place a set of values and a range on an order are put together. Each of them is
     * read from the rules by a reading of its own, and neither has a word for what the other holds
     * — a pattern names no ends and an ordering names no finite set — so what they have in common
     * is a question about the order they are both about, and this is the order.
     *
     * <p><b>One position, and nothing about any other.</b> Which alternative of a reading survives
     * is a question about a whole product and belongs to whoever holds the alternatives
     * ({@link souther.compiler.values.AdmissibleValues#anyAlternativeAdmits}). What is answered here
     * is one position's, so that a carrier added later answers what a carrier has to answer and not
     * what a reading does.
     *
     * <p>{@link Emptiness#UNDECIDED} where this order cannot say. A pattern over a carrier that is
     * not the strings is a set belonging to no position of this order rather than one this refuses,
     * and a machine the allowance will not pay for is a question that waits — neither of them is a
     * value being ruled out.
     *
     * @param meter what may be built to answer, where answering takes a machine
     */
    default Emptiness meets(ValueSet set, OrderedInterval range, Meter meter) {
        if (range.holdsNothing()) {
            return Emptiness.EMPTY;
        }
        return switch (set) {
            case ValueSet.Finite it -> anyOf(it.values(), range);
            // Every value of the order is admitted, and the range holds one.
            case ValueSet.Cofinite it -> it.excluded().isEmpty()
                    ? Emptiness.NONEMPTY : somethingNotExcluded(it.excluded(), range);
            case ValueSet.Matching it -> this instanceof Text
                    ? stringsInside(it.language(), range, meter) : Emptiness.UNDECIDED;
        };
    }

    /**
     * Which values this order holds inside {@code range} that {@code set} admits.
     *
     * <p>{@link #meets} answering with the values rather than with whether there are any, for a
     * reader relating one position to another. Whether a denial between two blocks leaves anything
     * turns on which values each of them is left and not on whether it is left some, so the
     * question a relation asks is this one — and it is here, beside the answer about one position,
     * because a set and a range are put together in one place or in two that come to disagree.
     *
     * <p>Three answers, and the middle one is what keeps a relation cheap. A block left more values
     * than a caller will count is one that never runs out for want of a value to take, so a reader
     * relating a handful of blocks may stop counting rather than enumerate an order — and a block
     * this cannot say the values of is a block a relation says nothing about, which is what every
     * widening here is.
     *
     * @param atMost how many values a caller will count. Its own and not this order's: how many
     *               there have to be before the answer stops mattering is a question about what the
     *               caller is relating
     */
    default Admits valuesShared(ValueSet set, OrderedInterval range, int atMost) {
        OrderedInterval held = extent().meet(range);
        if (held.holdsNothing()) {
            return new Admits.These(Set.of());
        }
        return switch (set) {
            case ValueSet.Finite it -> named(it.values(), held, atMost);
            case ValueSet.Cofinite it -> placed(held, it.excluded(), atMost);
            // A language is a description of a machine rather than the set it comes to, and
            // building one to relate two positions would spend a position's allowance on a question
            // about a pair.
            case ValueSet.Matching _ -> new Admits.NotKnown();
        };
    }

    /** Which of {@code values} lie inside {@code range}, unknown where one of them is not a value
     *  this order places at all. */
    private Admits named(java.util.Set<Value> values, OrderedInterval range, int atMost) {
        Set<Value> out = new LinkedHashSet<>();
        for (Value each : values) {
            Place at = placeOf(each);
            if (at == null) {
                return new Admits.NotKnown();
            }
            if (range.admits(at)) {
                if (out.size() == atMost) {
                    return new Admits.MoreThanCounted();
                }
                out.add(each);
            }
        }
        return new Admits.These(out);
    }

    /** Which values {@code range} holds that {@code excluded} does not name, where they are few
     *  enough to write down. */
    private Admits placed(OrderedInterval range, Set<Value> excluded, int atMost) {
        List<Place> held = firstPlacesIn(range, atMost + excluded.size() + 1);
        if (held == null) {
            return new Admits.MoreThanCounted();
        }
        Set<Value> away = new LinkedHashSet<>();
        for (Value each : excluded) {
            Place at = placeOf(each);
            if (at == null) {
                return new Admits.NotKnown();
            }
            away.add(valueAt(at));
        }
        Set<Value> out = new LinkedHashSet<>();
        for (Place at : held) {
            Value value = valueAt(at);
            if (value == null) {
                return new Admits.NotKnown();
            }
            if (!away.contains(value) && out.add(value) && out.size() > atMost) {
                return new Admits.MoreThanCounted();
            }
        }
        return new Admits.These(out);
    }

    /**
     * The value at a place on this order, or null where this order has no way of writing one down.
     *
     * <p>{@link #placeOf} the other way round, and here for the reason that one is: a value and
     * where it sits are the same fact said twice, and the crossing between them is the carrier's.
     * The temporal carriers place no written value, so nothing here writes one back.
     */
    private Value valueAt(Place at) {
        return switch (this) {
            case Whole _, Dense _ -> Value.number(Count.number(at).at());
            case Text _ -> Value.text(((souther.compiler.numeric.Text) at).at());
            case Ordinal it -> Value.of(it.caseAt(at));
            case Days _, Seconds _, SecondsOfDay _, Nanos _ -> null;
        };
    }

    /** Whether one of {@code values} is inside {@code range}, undecided where a value of the set is
     *  not one this order places at all. */
    private Emptiness anyOf(java.util.Set<Value> values, OrderedInterval range) {
        boolean placed = true;
        for (Value each : values) {
            Place at = placeOf(each);
            if (at == null) {
                placed = false;
            } else if (range.admits(at)) {
                return Emptiness.NONEMPTY;
            }
        }
        return placed ? Emptiness.EMPTY : Emptiness.UNDECIDED;
    }

    /**
     * Whether {@code range} holds a value none of {@code excluded} is.
     *
     * <p>Answered by taking the values the range holds, and only as many of them as could be
     * excluded: a range holding more than that holds one of them whatever the exclusions are, and
     * counting further would be enumerating an order to answer a question about a handful of values.
     */
    private Emptiness somethingNotExcluded(Set<Value> excluded, OrderedInterval range) {
        List<Place> held = firstPlacesIn(range, excluded.size() + 1);
        if (held == null) {
            return Emptiness.NONEMPTY;
        }
        List<Place> away = new ArrayList<>(excluded.size());
        excluded.forEach(each -> {
            Place at = placeOf(each);
            if (at != null) {
                away.add(at);
            }
        });
        for (Place each : held) {
            if (away.stream().noneMatch(each::sameAs)) {
                return Emptiness.NONEMPTY;
            }
        }
        return Emptiness.EMPTY;
    }

    /**
     * The first {@code atMost} places {@code range} holds, or null where it holds more than that.
     *
     * <p>Null is the range holding more and never this declining to look: what it answers is a
     * question about how many, and a caller reads it as the range being wider than the values it
     * was asked about.
     */
    private List<Place> firstPlacesIn(OrderedInterval range, int atMost) {
        OrderedInterval held = extent().meet(range);
        Endpoint low = held.low();
        Endpoint high = held.high();
        if (low == null || high == null) {
            return null;
        }
        List<Place> out = new ArrayList<>();
        if (this instanceof Text) {
            souther.compiler.numeric.Text at = (souther.compiler.numeric.Text) low.at();
            if (!low.inclusive()) {
                at = at.justAbove();
            }
            while (Endpoint.someValueLiesBetween(Endpoint.inclusive(at), high)) {
                if (out.size() == atMost) {
                    return null;
                }
                out.add(at);
                at = at.justAbove();
            }
            return out;
        }
        if (spacing() == Granularity.DENSE) {
            // Between two places that are not one place there is always another, so a range holding
            // few values is a range holding one: both ends at one place, and both of them its own.
            return low.inclusive() && high.inclusive() && low.at().sameAs(high.at())
                    ? List.of(low.at()) : null;
        }
        Count first = Count.number(low.at());
        Count last = Count.number(high.at());
        if (!low.inclusive()) {
            first = first.plus(1);
        }
        if (!high.inclusive()) {
            last = last.minus(1);
        }
        for (Count at = first; at.compareTo(last) <= 0; at = at.plus(1)) {
            if (out.size() == atMost) {
                return null;
            }
            out.add(at);
        }
        return out;
    }

    /**
     * Whether any string {@code language} admits lies inside {@code range}.
     *
     * <p>The range said as the strings it holds, which is what makes this an answer about the
     * strings rather than about the ends: a language is its strings, and two of them share a value
     * or they do not. Said as a projection of the language onto the ends instead, a rule whose
     * strings are not one stretch of the order would have no ends to be compared with.
     */
    private Emptiness stringsInside(Language language, OrderedInterval range, Meter meter) {
        OrderedInterval held = extent().meet(range);
        Language inside = language;
        if (held.high() != null) {
            souther.compiler.numeric.Text at =
                    (souther.compiler.numeric.Text) held.high().at();
            Language below = Language.before(
                    held.high().inclusive() ? at.justAbove().at() : at.at(), meter);
            inside = below == null ? null : inside.and(below, meter);
        }
        if (inside != null && held.low() != null) {
            souther.compiler.numeric.Text at = (souther.compiler.numeric.Text) held.low().at();
            Language under = Language.before(
                    held.low().inclusive() ? at.at() : at.justAbove().at(), meter);
            Language above = under == null ? null : under.not(meter);
            inside = above == null ? null : inside.and(above, meter);
        }
        if (inside == null) {
            return Emptiness.UNDECIDED;
        }
        return inside.isEmpty() ? Emptiness.EMPTY : Emptiness.NONEMPTY;
    }

    /**
     * Where a written value sits on this order, or null where it is not a value of this order.
     *
     * <p>Beside {@link #placeOf(ObservedValue)} and not instead of it: that one places a value read
     * back from a run, and this one places a value a rule named. What they have in common is the
     * order, which is here, and what they differ in is where the value came from.
     *
     * <p>The temporal carriers place none. A date is ordered and has a count that stands for it, and
     * a rule naming one reaches the reading of the order rather than the reading of values — so
     * nothing ever asks this to place one, and answering would be a second way for a date to be
     * written down.
     */
    private Place placeOf(Value value) {
        return switch (this) {
            case Whole _, Dense _ -> value instanceof Value.Number it
                    ? onTheGrid(Count.of(it.value())) : null;
            case Text _ -> value instanceof Value.Text it
                    ? souther.compiler.numeric.Text.of(it.value()) : null;
            case Ordinal it -> value instanceof Value.Case one ? it.at(one.data()) : null;
            case Days _, Seconds _, SecondsOfDay _, Nanos _ -> null;
        };
    }

    /**
     * The count an observed value is on this carrier, or null where the value is not one of this
     * carrier's.
     *
     * <p>Null is about the value and not about this: a {@code Text} where a date was expected is a
     * value the position does not hold, and a caller with a reason to keep is the one that has it.
     *
     * <p>A newtype is not a step in a path, so what sits at a position may be the construction with
     * the value one inside it. Reached through rather than refused, which is how a wrapped number and
     * a bare one are the same count.
     */
    default Place placeOf(ObservedValue value) {
        ObservedValue at = value instanceof ObservedValue.Constructed c && c.field("value") != null
                ? c.field("value") : value;
        return switch (this) {
            // A whole number written as a decimal is the same count; whether the position admits a
            // fraction is the range's question and not this one's.
            case Whole _, Dense _ -> switch (at) {
                case ObservedValue.Integer i -> Count.of(i.value());
                case ObservedValue.Decimal d -> Count.of(d.value());
                case null, default -> null;
            };
            case Days _ -> at instanceof ObservedValue.Temporal t ? Dates.dayOf(t.iso()) : null;
            case Seconds _ ->
                    at instanceof ObservedValue.Temporal t ? DateTimes.secondOf(t.iso()) : null;
            case SecondsOfDay _ ->
                    at instanceof ObservedValue.Temporal t ? Times.secondOf(t.iso()) : null;
            case Nanos _ ->
                    at instanceof ObservedValue.Temporal t ? Instants.nanoOf(t.iso()) : null;
            // A case carries nothing but which case it is, so the observation is its name.
            case Ordinal ordinal ->
                    at instanceof ObservedValue.Unit u ? ordinal.at(u.type()) : null;
            case Text _ -> at instanceof ObservedValue.Text t
                    ? souther.compiler.numeric.Text.of(t.value()) : null;
        };
    }

    /**
     * The value a count stands for, as an observation of it would look.
     *
     * <p>The one place a count leaves the algebra. A date is a day count inside the ranges and a date
     * everywhere a person reads it, and the conversion sitting here is what keeps a cut, a report and
     * a fixture from disagreeing about which of the two a line is drawn at.
     */
    default ObservedValue valueOf(Place count) {
        return switch (this) {
            case Whole _ -> new ObservedValue.Integer(Count.number(count).at().longValueExact());
            case Dense _ -> new ObservedValue.Decimal(Count.number(count).at());
            case Days _ -> new ObservedValue.Temporal(Dates.written(count));
            case Seconds _ -> new ObservedValue.Temporal(DateTimes.written(count));
            case SecondsOfDay _ -> new ObservedValue.Temporal(Times.written(count));
            case Nanos _ -> new ObservedValue.Temporal(Instants.written(count));
            case Ordinal ordinal -> new ObservedValue.Unit(ordinal.caseAt(count));
            case Text _ -> new ObservedValue.Text(count.key());
        };
    }

    /**
     * A count as an author would write the value it stands for. A report that printed the count
     * itself would name a line at a number nobody wrote.
     *
     * <p>The number and not how many places it was written to, which is the same thing that makes
     * two cuts one cut ({@link Count#key()}). A line an invariant and a {@code guard} both draw is
     * one line recorded once, and the spelling it keeps is whichever rule reached it first — so a
     * label that preserved places would print one line two ways depending on the order the rules
     * were read in.
     */
    default String written(Place count) {
        return switch (this) {
            case Whole _, Dense _ -> count.key();
            case Days _ -> Dates.written(count);
            case Seconds _ -> DateTimes.written(count);
            case SecondsOfDay _ -> Times.written(count);
            case Nanos _ -> Instants.written(count);
            // The case's name, which is the only thing a person ever writes at such a position. An
            // ordinal in a report would name a line at a number the model does not contain.
            case Ordinal ordinal -> ordinal.caseAt(count).name();
            // Bare, as a date and a case are. A row's own description is quoted text, so a quote
            // here ends it early and the rest of the line lands where the input goes.
            case Text _ -> count.key();
        };
    }
}
