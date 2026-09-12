package souther.compiler.partition;

import souther.compiler.numeric.Towards;

/**
 * What a row has to do to be at one coverage item of a border.
 *
 * <p>Said of the border's {@link BorderQuantity} and of nothing else. Two shapes for every border
 * there is: a row is at one level of the quantity, or anywhere in one run of them. A point against
 * the line is the first, wherever the line falls and whatever kind of rule drew it; a point away
 * from it is the second, because every region beside a line is a run — a rule that names a value
 * leaves the run under it and the run over it, which the arrangement holds like any other pair.
 *
 * <p><b>Not one shape per kind of border.</b> A line at a place of one position and a line where two
 * positions stand apart used to ask for two vocabularies here, and every reader of an item had to
 * know which of the two it was holding — a criterion about a place handed to the reader of a pair was
 * an {@code IllegalStateException} rather than a build failure. What tells them apart is the
 * quantity, and the quantity is what the item is about; what it asks of a row is the same two
 * questions whichever quantity it is on.
 *
 * <p>Which of the four roles a criterion belongs to is the border's to say ({@link PointRole}) and is
 * no part of this: the same shape answers for two roles, and a criterion that carried the role would
 * have every reader deciding what to compare from a word rather than from what it was handed.
 *
 * <p>What a row has to reach beyond that is not here either. A line a fork of a body drew is met by
 * getting the comparison to answer as well as by standing at the level, and that is true of all four
 * of its points — it is a fact about the rule that drew the border, so it is asked of the border and
 * never of the item.
 */
public sealed interface Criterion {

    /** A row whose quantity is exactly this level. */
    record AtTheLevel(Level at) implements Criterion {

        @Override
        public LevelRegion region() {
            return LevelRegion.point(at);
        }

        @Override
        public String operator() {
            return "=";
        }
    }

    /**
     * A row whose quantity lies in one run of the values, other than at one level of it.
     *
     * <p>What both sides of a border are. A border parts two runs, and the {@code IN} point is a row
     * in the one it bounds while the {@code OUT} point is a row in the one it keeps out — in each
     * case away from the line, which is what {@code except} takes out.
     *
     * <p><b>A run and not a side.</b> Read as everything past the line, the point runs to the end of
     * the order, and a row past the next line along answers for it while the run this border bounds
     * has nothing in it. The two are the same only where the quantity has one line through it
     * (issue #880).
     *
     * @param except the value against the line, which is the border's own {@code ON} or {@code OFF}
     *               point and is not this one. Null where the order names no value there, and then
     *               the whole run is what is asked for
     * @param away   which way from the line this point is named for the run lies, which is to say
     *               which of the run's two ends that line is at. Told rather than worked out: two
     *               points of two different borders are the same run — the {@code IN} point above a
     *               line and the {@code OUT} point below the next one along are one set — and which
     *               line each is named for is not in the set. Read off the run instead, both of them
     *               were taken to start at the lower of the two, and a point that exists to sit
     *               beside its boundary was searched from the far side of the partition
     */
    record Within(Band band, Level except, Towards away) implements Criterion {

        public Within {
            if (band == null || away == null) {
                throw new IllegalArgumentException(
                        "a run is a band and the side of it the line is at: " + band + " / " + away);
            }
        }

        @Override
        public LevelRegion region() {
            return band.region().without(except);
        }

        @Override
        public String operator() {
            return "in";
        }

        /**
         * A place of {@code carrier} this item accepts, or null where nothing composed one.
         *
         * <p>The item's own values, held to what the rules leave the position, asked of the order
         * the position is on, from the end this point is named for. Which is the same question a border's point over a form asks of the
         * order its levels are on, and it is asked the same way — one run, and the order says what
         * it has inside it. Written twice instead, the two disagreed about a run whose ends are
         * lines the quantity stands at no value of: what was in it was decided exactly, and what was
         * looked for was found by stepping past it (issues #901, #903).
         */
        public souther.compiler.numeric.Place somewhereInside(
                souther.compiler.check.Carrier carrier,
                souther.compiler.numeric.Endpoint min, souther.compiler.numeric.Endpoint max) {
            LevelSpace space = LevelSpace.onACarrier(carrier);
            for (LevelInterval look : runsInside(carrier, min, max)) {
                Level found = space.witness(look, away).level();
                if (found instanceof Level.OnACarrier on) {
                    return on.at();
                }
            }
            return null;
        }

        /**
         * The runs this item leaves inside what the rules leave the position, in the order they are
         * to be looked in.
         *
         * <p>One reading of the geometry, because there is more than one thing to do with it. A
         * caller with a set of admitted values crosses each of these with that set rather than
         * taking a value out of one of them and putting it to the set afterwards
         * ({@link souther.compiler.check.Carrier#somewhereIn}), and it has to be looking in the same
         * runs and in the same order as the caller that wants a value and nothing else. Read twice,
         * the two would be free to disagree about which end of the item is the near one.
         */
        public java.util.List<LevelInterval> runsInside(
                souther.compiler.check.Carrier carrier,
                souther.compiler.numeric.Endpoint min, souther.compiler.numeric.Endpoint max) {
            LevelInterval leaves = new LevelInterval(
                    endOf(carrier, min), endOf(carrier, max));
            java.util.List<LevelInterval> out = new java.util.ArrayList<>();
            for (LevelInterval part : region().parts()) {
                LevelInterval look = part.intersect(leaves);
                if (look != null) {
                    out.add(look);
                }
            }
            return java.util.List.copyOf(out);
        }

        /** What the rules leave the position, as an end of a run of its values. */
        private static Bound endOf(souther.compiler.check.Carrier carrier,
                                   souther.compiler.numeric.Endpoint end) {
            return end == null ? null
                    : Bound.at(new Level.OnACarrier(carrier, end.at()), end.inclusive());
        }
    }

    /**
     * Which values of the quantity stand at this item.
     *
     * <p>On the interface because it is what a criterion is, and the only thing every shape has to
     * say about itself. Whether a value stands here and where to look for one are both read off it,
     * so they cannot come apart — asked separately, a witness composed for a side stood for it on
     * the strength of the arithmetic that composed it rather than on the item's own answer, and a
     * run whose ends the quantity stands at no value of was looked in by stepping past it.
     */
    LevelRegion region();

    /**
     * Whether a row whose quantity came to {@code value} is at this item.
     *
     * <p>The set's own answer and not a second reading of it.
     */
    default boolean holds(Level value) {
        return region().contains(value);
    }

    /**
     * The same demand on a row, with every level written the one way.
     *
     * <p>What an identity built out of a criterion is built from, and what two of them are compared
     * through. A level keeps the spelling the rule was written in, because that is what a report
     * writes back ({@link Level#canonical()}), so two readings of one line can arrive here holding
     * {@code 0} and {@code 0.00} — which is one demand and was two values.
     */
    default Criterion canonical() {
        return switch (this) {
            case AtTheLevel(Level at) -> new AtTheLevel(at.canonical());
            case Within(Band band, Level except, Towards away) -> new Within(band.canonical(),
                    except == null ? null : except.canonical(), away);
        };
    }

    /**
     * Whether two criteria ask a row for the same thing.
     *
     * <p>The same shape asking for the same values, and not the same set of rows: a level and a run
     * one value wide hold the same rows, and which of them a border owes is what says where a search
     * starts and what a report prints. So this asks whether the two are one demand, which is
     * narrower than whether one row answers both.
     */
    default boolean sameAs(Criterion other) {
        if (!(this instanceof Within in)) {
            return other != null && canonical().equals(other.canonical());
        }
        // A run is what it holds, and not how it came to stop there. The same values are left by a
        // line at the end and by the rules stopping the quantity at that same place, and a run
        // bounded both ways is bounded once — so a reading that has both and a reading that has one
        // ask a row for the same thing. Compared as the run was built, a rule that moves nothing
        // would have two readings of one point asking two things, which is what this check calls a
        // defect in the identity.
        return other instanceof Within also && in.away() == also.away()
                && (in.except() == null ? also.except() == null
                        : also.except() != null
                                && in.except().canonical().equals(also.except().canonical()))
                && in.region().canonical().equals(also.region().canonical());
    }

    /** How this relates a row's quantity to what it is against. */
    String operator();

    /**
     * The level this is written against, or null where what it is written against is a run rather
     * than a level.
     *
     * <p>One shape names a level and the other names a run, so a reader that wanted one level from
     * both was reading a witness of a run as though it were the run. What both do answer is
     * {@link #asked}.
     */
    default Level against() {
        return switch (this) {
            case AtTheLevel at -> at.at();
            case Within _ -> null;
        };
    }

    /**
     * The level a search starts from, which every shape has.
     *
     * <p><b>Where to start looking and never what satisfies this.</b> Whether a value is at this
     * item is {@link #holds}, and the two differ for a run: a search for a row inside a run starts
     * at the line and walks away from it, and the line is the one place in reach that the run does
     * not hold. Read as the second, a row on the line was offered for a point that lies past it.
     *
     * <p>Apart from {@link #against}, which is what a report writes: a run is not written against a
     * level and is still arranged around one.
     */
    default Level anchor() {
        if (!(this instanceof Within in)) {
            return against();
        }
        if (in.except() != null) {
            return in.except();
        }
        // The end the line is at, which the run says and this point names. A run's two ends are two
        // different lines, and which of them a search starts from is what tells one point from the
        // other where neither has a value against it.
        Level beside = in.away() == Towards.ABOVE ? in.band().first() : in.band().last();
        if (beside != null) {
            return beside;
        }
        // And the line itself where the run has no value at that end. A search starts there and
        // walks away from it — which is why this is not what the run holds: two decimals a rule
        // holds apart have every distance past the line and no first one.
        Seam edge = in.away() == Towards.ABOVE
                ? in.band().lower().seam() : in.band().upper().seam();
        return edge == null ? null : edge.at().asALevelOfTheQuantity();
    }

    /**
     * What this asks of a row, as a report writes it: the relation and what it is against.
     *
     * <p>Written whole rather than as a value beside an operator a reader supplies. Two of the four
     * items ask for a level and two ask for a side, and a report that printed a value for all four
     * would name a witness of a side as though it were the side — {@code = 99} where what is owed is
     * any row below the line.
     *
     * <p>What the level is written as is the quantity's, because only the quantity knows what its
     * numbers are of: a count of days is a date, and how far two positions stand apart is written
     * beside the position it is apart from rather than as the number it is.
     */
    default String asked(BorderQuantity of) {
        return operator() + " " + written(of);
    }

    /**
     * What this is written against, as a report writes it: a level for the shapes that name one, and
     * the run itself for the shape that names a run.
     *
     * <p>The run written whole. A run has two ends and a report that printed one of them would name
     * a value inside it as though it were the run — which is the reading this shape exists to stop.
     */
    default String written(BorderQuantity of) {
        if (!(this instanceof Within in)) {
            return of.writtenAt(against());
        }
        // Through the quantity's own spelling, which knows what it is made of: twice a form is that
        // form doubled and not the words `2 *` in front of it.
        return in.band().written(of, in.except());
    }

    /**
     * The same, with {@code left} in place of the name the quantity goes by at this reading.
     *
     * <p>What a debt writes. A run says the quantity's own name inside the sentence — {@code 1 <
     * String.length(value)} — so a debt written off a reading would put that reading's position into
     * a sentence about the line, which is the position a walk happened to reach first.
     *
     * <p><b>{@code left} names the whole of the quantity.</b> An end only the rule that drew it can
     * name says how much of the quantity that rule wrote, and this spells that by writing the
     * multiple in front — which is what a quantity of one coordinate does and is not what a form of
     * several does ({@link BorderQuantity#left(java.math.BigDecimal)} rebuilds the form instead). A
     * run bounded that way, on a line a declaration is owed over a relation between two positions,
     * would come out spelled as the multiple of the first of them. No model here writes one; the
     * limit is said rather than guessed at.
     */
    default String written(BorderQuantity of, String left) {
        if (!(this instanceof Within in)) {
            return of.writtenAt(against());
        }
        return in.band().written(of, in.except(),
                times -> BorderQuantity.times(times, left));
    }
}
