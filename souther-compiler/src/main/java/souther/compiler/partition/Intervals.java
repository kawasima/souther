package souther.compiler.partition;

import souther.compiler.check.RuleReadingContext;
import souther.compiler.check.Carrier;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.TypeView;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.Quantities;
import souther.compiler.inputs.TermOrders;
import souther.compiler.numeric.Place;
import souther.compiler.numeric.CountDomain;
import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.Towards;
import souther.compiler.types.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Turning the lines a model draws through one numeric position into the ranges between them.
 *
 * <p>Several rules can cut the same position, and they are one partition however they were written.
 * {@code x < 0} and {@code x < 10} are three ranges, not two overlapping pairs, and a report built on
 * the pairs would count a value twice and never reach a hundred percent.
 *
 * <p>The type's own domain comes into it. A newtype's invariant bounds what can be constructed at all,
 * so a range outside it holds no value a row could write and is not a range to cover:
 *
 * <pre>invariant x &gt;= 0   with   guard x &lt; 10   is   0 &lt;= x &lt; 10  and  10 &lt;= x</pre>
 *
 * and not a third range below zero.
 */
final class Intervals {

    /** One range of a position's counts. A null bound is the domain's own edge. */
    record Interval(Place lo, boolean loInclusive, Place hi, boolean hiInclusive, Band of) {

        Interval(Place lo, boolean loInclusive, Place hi, boolean hiInclusive) {
            this(lo, loInclusive, hi, hiInclusive, null);
        }

        boolean holds(Place v) {
            if (lo != null) {
                int c = v.compareTo(lo);
                if (c < 0 || (c == 0 && !loInclusive)) {
                    return false;
                }
            }
            if (hi != null) {
                int c = v.compareTo(hi);
                return c < 0 || (c == 0 && hiInclusive);
            }
            return true;
        }

        /** Whether any value at all is in here. Two cuts at one place leave nothing between them. */
        boolean inhabited() {
            if (lo == null || hi == null) {
                return true;
            }
            int c = lo.compareTo(hi);
            return c < 0 || (c == 0 && loInclusive && hiInclusive);
        }

        /**
         * The class this range is, as a report names it.
         *
         * <p>Both ends written, each in whatever names it exactly. A line the position has a value
         * at is written as that value; one it has none at is written as the rule that drew it, in
         * numbers this language has — {@code 3 * x <= 1} rather than a third rounded to something
         * it is not. A run with one end of each kind says both and joins them, because an end left
         * out is a class that reads as holding the values past the next line along.
         */
        String label(Carrier carrier) {
            String low = lo == null
                    ? ruleEnd(of == null ? null : of.lower().seam(), Towards.ABOVE)
                    : carrier.written(lo) + (loInclusive ? " <= x" : " < x");
            String high = hi == null
                    ? ruleEnd(of == null ? null : of.upper().seam(), Towards.BELOW)
                    : (hiInclusive ? "x <= " : "x < ") + carrier.written(hi);
            if (low == null && high == null) {
                return "any";
            }
            if (low == null) {
                return high;
            }
            if (high == null) {
                return low;
            }
            // One subject and two relations where both ends can name the same one, which is what
            // an author reads a range as. Two subjects are two conditions and are said as two.
            String subject = subjectOf();
            return subject == null ? low + " and " + high
                    : low.substring(0, low.length() - subject.length())
                            + subject + high.substring(subject.length());
        }

        /** What both ends of this run relate a row to, where they relate it to the same thing.
         *  Null where one end names the position and the other a multiple of it. */
        private String subjectOf() {
            if (lo != null && hi != null) {
                return "x";
            }
            if (lo != null || hi != null || of == null) {
                return null;
            }
            java.math.BigDecimal shared = of.sharedMultiple();
            return shared == null ? null : times(shared);
        }

        /** One end as the rule that drew it, or null where nothing parts the run there. */
        private static String ruleEnd(Seam parted, Towards side) {
            return parted == null ? null : parted.asARuleAbout(Interval::times, side);
        }

        private static String times(java.math.BigDecimal by) {
            return by.compareTo(java.math.BigDecimal.ONE) == 0 ? "x" : plain(by) + " * x";
        }

        private static String plain(java.math.BigDecimal number) {
            return number.stripTrailingZeros().toPlainString();
        }
    }

    /**
     * The ranges {@code thresholds} leave, inside what the position holds.
     *
     * <p>The outer ends are the position's own, and they are taken as they are: an end the position
     * stops short of is an end of the first range too, and rebuilding it as one the range holds puts
     * the value back that the rules had taken away. Thresholds outside what the position holds are
     * dropped — they cut a range no row can reach — and the end itself is outside it as much as
     * anything past it is.
     */
    static List<Band> of(List<Threshold> thresholds, Endpoint min, Endpoint max,
                         Carrier carrier) {
        // Keyed by where the values part, and not by the number a rule was written with. `x <= 4`
        // and `x < 5` divide the whole numbers once; keyed by their thresholds they are two splits,
        // and the range between them holds no value any row could be written at — a class a report
        // counts, tells an author no row is in, and asks the generator for (issue #880).
        //
        // Which subsumes the reason this was keyed by the number rather than by the count's own
        // equality: `0.00` and `0` are one number and part the values in one place.
        LevelSpace space = LevelSpace.onACarrier(carrier);
        List<Parting> parted = new ArrayList<>();
        for (Threshold each : thresholds) {
            // The division the rule made, taken as it was read rather than rebuilt from a number.
            // A rule that wrote a multiple of the position parts its values where the position may
            // hold none, and rebuilding the seam from a value of the position lost exactly those.
            parted.add(Parting.by(each.parts(), each.origin().authoredLine()));
        }
        // The one arrangement, which the points of every border on this position are read off as
        // well. Where the values part, in what order and with what left between them are questions
        // about the position, and deriving them twice is two chances to answer them differently —
        // which is how a border came to ask for a row inside a partition the classes had already
        // divided further along.
        List<Band> out = new ArrayList<>();
        for (Band run : QuantityArrangement.of(space, parted).bands()) {
            if (rangeOf(run, min, max).inhabited()) {
                out.add(run);
            }
        }
        return out.size() < 2 ? List.of() : List.copyOf(out);
    }

    /**
     * One run of the arrangement as a range of the position's counts.
     *
     * <p>The ends the rules leave rather than the ones the seams do: a run with nothing parting it
     * at one end stops where the position stops, and how far a bound reaches includes whether it
     * keeps its own value. A run parted at an end stops at the line, on the side the rule that drew
     * it keeps.
     */
    private static Interval rangeOf(Band run, Endpoint min, Endpoint max) {
        Endpoint low = run.lineBelow(min);
        Endpoint high = run.lineAbove(max);
        return new Interval(low == null ? null : low.at(), low == null || low.inclusive(),
                high == null ? null : high.at(), high == null || high.inclusive(), run);
    }

    /**
     * The classes those ranges are, on the term {@code of} at a position of {@code type}.
     *
     * <p>The orders say how a row's value is read into a number and how its numbers are spaced; the
     * type says what a value written at one of them looks like. A range of lengths has the first and
     * not the second: five is not what is written at the position, a string of five characters is,
     * and which values carry a count is asked of what builds them rather than settled here.
     *
     * <p>The orders are asked of the reading rather than handed in beside the term. Which order a
     * number is measured on follows from where the reading has that term standing, so a caller
     * working it out from whatever type reached it would be answering about wherever that type came
     * from — and a caller handing the answer over is handing two arguments that can be about two
     * terms.
     */
    static List<PartitionClass> classesOf(List<Band> runs, NumericTerm.FromOnePosition of,
                                          Type type, Quantities reading,
                                          RuleReadingContext ruleReading,
                                          Endpoint min, Endpoint max) {
        TermOrders orders = reading.ordersOf(of);
        // What the counts in a label stand for. A day count is a carrier and never a name for the
        // line, so the class an author reads is spelled in dates where the position holds them.
        Carrier carrier = orders.answered();
        List<PartitionClass> classes = new ArrayList<>();
        for (Band run : runs) {
            String label = rangeOf(run, min, max).label(carrier);
            String id = of + "/" + label;
            Place inside = representative(run, carrier, min, max);
            // The run's own answer about what is in it. Read off a range of the position's counts,
            // a class whose line falls at a place the position has no value for had no end to state
            // — so it held every value, and two such classes each held everything the other did.
            Recognition is = new Recognition.OfACount(of, orders,
                    new Recognition.CountIs.InARun(run));
            // Nothing composed here is what this compiler did not manage, and never what the run
            // holds. Above a string a rule stops short of, the order declines to name a value on
            // purpose — every string with that one as a prefix is greater, and choosing between
            // them puts a character nobody wrote into a row somebody reads. Which is why both empty
            // answers are said in the one sentence, and why that sentence is about this compiler
            // rather than about the model (ADR-0091).
            List<FixtureTemplate> values = inside == null ? List.of()
                    : standingIn(of, inside, type, carrier, ruleReading);
            classes.add(values.isEmpty()
                    ? PartitionClass.ungeneratable(id, label, is,
                            "nothing here writes a value whose " + measureOf(of) + " is in this range")
                    : PartitionClass.of(id, label, is,
                            RepresentativeSource.of(values.toArray(new FixtureTemplate[0]))));
        }
        // Classes of the number the runs are runs of, said here because here is where that is known.
        return classes.stream().map(each -> each.ofTheNumber(of)).toList();
    }

    /** What the range is a range of, in the words a reader of the report has: the operation where
     *  the number is what one answered, and the position's own value otherwise. */
    private static String measureOf(NumericTerm.FromOnePosition of) {
        // Exhaustive, with no `default`. What a range is a range of is a word per kind of number,
        // so a kind added is one a reader has to be given a word for rather than one that arrives
        // under whichever word the condition left it on.
        return switch (of) {
            case NumericTerm.TakenOf taken -> taken.operation().qualified();
            case NumericTerm.ValueOf _ -> "value";
        };
    }

    /**
     * A value inside a range, or null where nothing composed one. Asked of the ends, which is where
     * whether the range holds the value it stops at is written down.
     *
     * <p>Null says what came back and not what the range holds. Which values are in it is
     * {@link LevelSpace#inspect}'s answer and is asked where the runs are chosen; this asks the
     * other question, and a caller that read the two as one would put the order's restraint into a
     * sentence about the model.
     *
     * <p>How the values step is the carrier's to say and is asked of it. Carried as "is it a decimal"
     * it was a second spelling of the same fact, and a carrier that is dense without being the
     * decimal — a date-time — answered no to it: the range between two moments a nanosecond apart
     * came back as one holding no value, which is what a whole step would leave and not what the
     * values do.
     */
    private static Place representative(Band run, Carrier carrier, Endpoint min, Endpoint max) {
        // A class is the run itself and is named for no line, so it is read from its lower end the
        // way a range of counts is.
        return new Criterion.Within(run, null, Towards.ABOVE).somewhereInside(carrier, min, max);
    }

    /**
     * Values of the position that read as {@code inside} on this term, or none where the term is one
     * nothing here can put a value on.
     *
     * <p>A number the term reads out of the value is written into it; a number the term counts of the
     * value is a value carrying that many, which is {@link Witnesses}'s question rather than this
     * one's. Asked of it rather than answered here, so that this says a range has no representative
     * only when the thing that builds them has none to give.
     */
    private static List<FixtureTemplate> standingIn(NumericTerm.FromOnePosition of, Place inside,
                                                    Type type, Carrier carrier,
                                                    RuleReadingContext reading) {
        // Exhaustive, with no `default`. What a value reading as this number looks like is a
        // different construction per kind of number, so a kind added is one this has to be told
        // how to build for rather than one that falls to whichever branch it was not named in.
        RuleReadingSource ruleSource = reading.source();
        TypeView view = TypeView.of(type, ruleSource.symbols());
        // A name this module cannot write leaves no value to write, whichever number the value is
        // asked to read as. Asked of the position, once, before anything is built for it.
        if (!(WornNames.of(view.wrappers(), ruleSource) instanceof WornNames.Spelled worn)) {
            return List.of();
        }
        switch (of) {
            case NumericTerm.ValueOf _ -> {
                FixtureTemplate standing =
                        FixtureTemplate.on(carrier, inside, ruleSource.symbols().scope()::reach);
                return standing == null ? List.of()
                        : List.of(RepresentativeSource.under(worn.names(), standing));
            }
            case NumericTerm.TakenOf _ -> { }
        }
        int size = CountDomain.asCount(inside);
        if (size < 0) {
            return List.of();
        }
        List<FixtureTemplate> out = new ArrayList<>();
        for (FixtureTemplate each
                : Witnesses.ofSize(view.shape(), size, reading, Set.of()).values()) {
            out.add(RepresentativeSource.under(worn.names(), each));
        }
        return List.copyOf(out);
    }

    private Intervals() {}
}
