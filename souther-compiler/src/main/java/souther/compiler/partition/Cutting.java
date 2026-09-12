package souther.compiler.partition;

import souther.compiler.check.StatedComparison;
import souther.compiler.check.ComparisonClaim;
import souther.compiler.inputs.InputReading;
import souther.compiler.inputs.InputReads;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.Quantities;
import souther.compiler.inputs.TermOrders;
import souther.compiler.numeric.Count;
import souther.compiler.numeric.Place;
import souther.compiler.numeric.LinearForm;
import souther.compiler.numeric.Towards;

/**
 * What one comparison cuts, and where — the one place that decides it.
 *
 * <p><b>One decision, so that a rule is read the same way wherever it is written.</b> Which quantity
 * a comparison cuts was settled in three places, each reading a little more of the language than the
 * last and each turning the form round or not on its own: a rule written {@code 48 >= 3a + 6b} drew
 * its border on {@code -3a - 6b}, and the {@code ensures} side called two of the three readings and
 * so read no form at all. That is the shape this whole reading was written to stop, one level up
 * from where it was found.
 *
 * <p><b>The arithmetic first, and how it was written only where the arithmetic stopped.</b> The
 * canonical form says which quantity the rule cuts; what is left to decide is whether this compiler
 * can realize a line on the order that quantity is on. A reading of the operands is reached only
 * where the arithmetic had no answer at all — a date against a written date, a case of an
 * enumeration — so a spelling never settles a question the form has already settled. Tried first,
 * it did: {@code a <= a} came back a distance between two positions while the form had already
 * cancelled them, and the tautology was owed a row where the two hold one count.
 *
 * <p>Which levels the order has a place at is the order's own answer ({@link LevelSpace#canCutAt}).
 * Asked of the carrier instead — "do these values count" — two strings, which stand no measurable
 * distance apart and are still one above the other, were left with no line at the place they meet.
 *
 * @param of     what the rule cuts
 * @param at     where on it
 * @param claim  what the operator states about the threshold's own value
 * @param within what the rules leave the quantity itself. Three times a length is never negative,
 *               and a threshold outside where a quantity runs is one the rule draws no border at.
 *               Asked of every quantity alike, since what a quantity runs between is a question
 *               about the quantity
 */
record Cutting(BorderQuantity of, Level at, ComparisonClaim claim,
               souther.compiler.numeric.NumericDomain.Bounds within) {

    /**
     * What reading {@code comparison} as a line came to.
     *
     * <p>Four answers, and the three that are not a line are not one absence. A comparison whose
     * positions cancel was read from end to end and there was no line in it; one this compiler
     * could not take apart leaves whatever it states unknown; and one whose quantity was read and
     * stands on no order this counts is neither. Told apart by a {@code null}, whoever asked had to
     * work out which — and worked it out by reading the comparison a second time, which is how a
     * rule read in full came to be described as one whose spelling defeated this compiler.
     *
     * <p>Each of them says what was found rather than what came of it. An arm named for the absence
     * of a line holds every way of failing to draw one, so the next one added goes out under the
     * word the last one earned.
     */
    sealed interface Read {

        /** The line it draws. */
        record Cuts(Cutting cutting) implements Read {

            public Cuts {
                java.util.Objects.requireNonNull(cutting, "a comparison that cuts has a line");
            }
        }

        /** Read to the end, and the quantity it cuts is nothing. {@code read} is what the reading
         *  named on the way, which is what the rule is about however much of it cancelled
         *  ({@link AffineReading.OfAComparison.CutsNothing}). */
        record CutsNothing(java.util.Set<souther.compiler.inputs.NumericTerm> read)
                implements Read {

            public CutsNothing {
                read = java.util.Set.copyOf(read);
            }
        }

        /**
         * The arithmetic stopped, and this is what it leaves at each place it is filed at.
         *
         * <p>A map and not one reason, because the places are not one subject. Where the arithmetic
         * stopped, each place the walk met is a separate question — a position met inside an
         * expression this did not take apart says nothing about what that position carries — and
         * one answer handed to all of them told a position about the carrier of another.
         */
        record Stopped(java.util.SequencedMap<souther.compiler.inputs.FilingCoordinate,
                souther.compiler.inputs.BlockReason.RuleReadingStopped> why) implements Read {

            public Stopped {
                why = java.util.Collections.unmodifiableSequencedMap(
                        new java.util.LinkedHashMap<>(why));
            }
        }

        /**
         * The quantity was read, and there is no order this compiler can realize it as a count on.
         *
         * <p><b>Named for what was found and not for what came of it.</b> "No line was built" is
         * true of every way of failing to build one, so an arm carrying that name takes the next
         * one added to it — and the word a document then prints was decided by whichever answers
         * happened to be absent rather than by anything anybody established.
         *
         * <p>What is established here is one thing: some term of the form stands on no order, or on
         * one whose values do not count, so a sum over the terms has nothing to be spaced by
         * ({@link AffineReading#carriers}). A line on a single position and a distance between two
         * are asked before this and reach orders this does not — two strings stand no measurable
         * distance apart and are still one above the other — so what is left here is the general
         * form, and the general form is what needs every term to count.
         *
         * <p>Its own answer and not {@link Stopped}. Nothing about the form fell short — it is
         * right here — so the quantity is the subject and the places are its own, which is what
         * every read comparison's are. Said as a reading that stopped, the places would come from a
         * walk over the operands, and which of two authorities a comparison's places came from
         * would turn on which producer built the answer.
         *
         * @param over the coordinates of the quantity, which is where a reader is sent
         */
        record NoOrderToCountOn(
                java.util.List<souther.compiler.inputs.FilingCoordinate> over) implements Read {

            public NoOrderToCountOn {
                over = java.util.List.copyOf(over);
            }
        }
    }

    /**
     * The same, said as which of the four it is.
     *
     * <p>The reason a reading stopped is settled here, where it stopped, and not asked for
     * afterwards by whoever met the absence. Worked out later, the reason came from a second walk
     * over the comparison and answered about the shape rather than about what this reading could do
     * with it.
     */
    static Read read(String behavior, StatedComparison comparison,
                     InputReading read, InputReads reads,
                     souther.compiler.coverage.Arrivals answering) {
        AffineReading.OfAComparison canonical =
                AffineReading.read(comparison, read.domain(), reads, read.rules());
        return switch (canonical) {
            // Nothing was missing: the form was read from end to end, and the quantity in it is
            // empty. Unconditional, and before anything about how the comparison was spelled —
            // `a <= a` names one position on either side and the arithmetic has already said the
            // two are one, so a reading of the operands finding a distance there is that reading
            // being wrong about the rule.
            case AffineReading.OfAComparison.CutsNothing over ->
                    new Read.CutsNothing(over.read());
            // The quantity is what the arithmetic says it is, and the realization is the only thing
            // left to try. Read the other way round, a spelling that produced a line took the
            // comparison before the canonical form was consulted at all.
            case AffineReading.OfAComparison.Cuts cuts ->
                    realized(behavior, cuts.read(), read.quantities());
            // And only here does how it was written decide anything. The arithmetic stopped, which
            // is what a date against a written date and a case of an enumeration do: the values are
            // ones it cannot count, and the comparison still states a line.
            case AffineReading.OfAComparison.Stopped stopped ->
                    asWritten(behavior, comparison, stopped, read, reads, answering);
        };
    }

    /**
     * The same line, on the quantity taken at another position — or null where the quantity cannot
     * be taken there.
     *
     * <p>For a name that stands at more than one position. The comparison is read once and stays one
     * comparison; what moves is where its quantity is taken, and it is taken under each case a value
     * of the sum can turn out to be.
     *
     * <p>What the quantity runs between is worked out again here and not carried over. It is what
     * the rules leave the quantity, so a quantity taken somewhere else runs between whatever the
     * rules leave it there — kept as it was, a line would be held inside the values of the position
     * it came from.
     */
    Cutting movedTo(NumericTerm from, NumericTerm to, Quantities quantities) {
        // What the term it moves to is measured on, asked of the reading that is here anyway. Taken
        // as an argument beside the term, the two were free to be about two terms — and the reading
        // that would have settled it was being handed over in the same call.
        BorderQuantity moved = of.movedTo(from, quantities.ordersOf(to));
        if (moved == null || !moved.levels().canCutAt(at)) {
            return null;
        }
        return new Cutting(moved, at, claim, quantities.runsBetween(direction(moved)));
    }

    /**
     * The line the canonical quantity draws, or the reading stopping on the order it is on.
     *
     * <p>Three shapes and one order among them, which is the arithmetic's: one position's own
     * values, two positions held apart, and a form over several. Nothing here reads the comparison
     * again — what each of them is handed is the form the reading already came to.
     *
     * <p><b>A shape declining and a recognised shape failing to draw are two different things.</b>
     * A recogniser answers nothing where the form is not its shape and where the orders that shape
     * would need are not there. The second of those is a fact about the model — an order is missing,
     * and that is worth saying — but it is not this reading's to classify, because a reading further
     * down may still answer: a form on an order that counts nothing is turned away by both narrower
     * shapes and gets its line from neither, and what it is left with is settled at the one place
     * that knows it has run out of readings.
     *
     * <p>What must not happen is the other one: a shape that was recognised and then could not be
     * realized falling to the next reading, where whatever that reading is short of becomes the
     * reason. So a recogniser is asked first and its answer decides which reading owns the form,
     * and only then is a line built.
     *
     * <p>And that one place is after the narrower shapes, not before them. They reach orders this
     * counts nothing on: two strings stand no measurable distance apart and the place they meet is
     * still a line, so a reader asking for counting orders first refuses lines the model draws.
     */
    private static Read realized(String behavior, AffineReading read,
                                 Quantities quantities) {
        // Which shape this quantity is, asked narrowest first, and each answered before anything is
        // built. A recogniser answering nothing is this form not being that shape, and the next
        // shape is asked; a recogniser that answered and a line that then could not be built are
        // two facts about one shape, and falling from the second to the next shape is how the
        // first came to be reported as the last one's absence.
        ComparedLine line = ComparedLine.fromTheForm(read, quantities);
        if (line != null) {
            return new Read.Cuts(realizedAt(atAPosition(behavior, line, quantities),
                    "a line on one position", read));
        }
        ComparedTerms pair = ComparedTerms.fromTheForm(read, quantities);
        if (pair != null) {
            return new Read.Cuts(realizedAt(apart(behavior, pair, read.claim(), quantities),
                    "a distance between two positions", read));
        }
        // And what the general form needs, asked once and here. Not before the two above: they
        // reach orders that do not count — two strings stand no measurable distance apart and the
        // place they meet is a line — so a reader asking this first would refuse lines the model
        // draws.
        java.util.Map<NumericTerm, TermOrders> on = read.carriers(quantities);
        if (on == null) {
            return new Read.NoOrderToCountOn(
                    AffineReading.filedAt(read.form().coefs().keySet()));
        }
        return new Read.Cuts(overAForm(behavior, read, on, quantities));
    }

    /**
     * The line a recognised shape draws, which is one it draws.
     *
     * <p>What is watched here is a shape this reading recognised and could not put a line on. Only
     * one thing refuses past recognition — an order with no place at the level the rule wrote
     * ({@link LevelSpace#canCutAt}) — and it is a fact about the model rather than a limit of this
     * compiler: a rule holding two strings three apart asks for a line at a distance the order has
     * no place for at all, and there is none to find.
     *
     * <p>Unreachable while the language admits no arithmetic over an order that counts nothing, so
     * a distance on such an order is only ever the place the two meet, which every such order has.
     * That is a fact about what can be written and not about these types, so it stops here rather
     * than falling to the reading below — where a shape that was recognised would have been
     * answered for by whether the general form's orders were there, and gone out as values nothing
     * draws a line on.
     */
    private static Cutting realizedAt(Cutting drawn, String shape, AffineReading read) {
        if (drawn == null) {
            throw new IllegalStateException("this reading recognised " + shape
                    + " and its order has no place at the level the rule wrote: "
                    + read.form() + " at " + read.cut());
        }
        return drawn;
    }

    /**
     * The line the comparison draws as it was written, where the arithmetic could not read it.
     *
     * <p>The two readings that reach carriers the arithmetic cannot: a date against a written date,
     * a case of an enumeration, one string against another. Reached only from a reading that
     * stopped, so a spelling never answers a question the canonical form has already answered.
     */
    private static Read asWritten(String behavior, StatedComparison comparison,
                                  AffineReading.OfAComparison.Stopped canonical,
                                  InputReading read, InputReads reads,
                                  souther.compiler.coverage.Arrivals answering) {
        Quantities quantities = read.quantities();
        Cutting drawn = atAPosition(behavior,
                ComparedLine.asWritten(comparison, read, reads), quantities);
        if (drawn == null) {
            // What the rule placed, carried from where the comparison was recognised. Read off the
            // operator again here, this reading would be a second answer to a question the value in
            // hand already answers.
            drawn = apart(behavior, ComparedTerms.asWritten(comparison, read, reads),
                    comparison.claim(), quantities);
        }
        if (drawn != null) {
            return new Read.Cuts(drawn);
        }
        return new Read.Stopped(GuardThresholds.whatEachPlaceIsLeftWith(
                comparison, canonical, read, reads, answering));
    }

    /** One position's own values, cut where the reading found the line, or null where that reading
     *  found none and where the order has no place for the one it found. */
    private static Cutting atAPosition(String behavior, ComparedLine drawn,
                                       Quantities quantities) {
        if (drawn == null) {
            return null;
        }
        return made(new BorderQuantity.OfACoordinate(behavior, drawn.term(), drawn.orders()),
                new Level.OnACarrier(drawn.orders().answered(), drawn.value()), drawn.claim(),
                quantities);
    }

    /** How far two positions stand apart, cut where the reading found the line, or null on the same
     *  two counts. */
    private static Cutting apart(String behavior, ComparedTerms drawn, ComparisonClaim claim,
                                 Quantities quantities) {
        if (drawn == null) {
            return null;
        }
        return made(new BorderQuantity.Apart(behavior, drawn.on(), drawn.against()),
                new Level.ACount(drawn.stepsApart()), claim, quantities);
    }

    /**
     * One line, with what the rules leave the quantity it is on.
     *
     * <p>Asked of every quantity and not of the one shape that used to ask. What a quantity runs
     * between is a question about the quantity, which {@link #direction} answers for all three
     * alike; asked only where the quantity was a form, a rule cutting a length at a negative drew a
     * border where a length never goes, and a row was owed at a value no row can carry.
     *
     * <p>Asked of the reading of the input rather than composed from what each of the form's
     * positions runs between: a product of per-position answers cannot carry a rule relating them,
     * so two fields a record holds at five together came to ten and a border was drawn where the
     * model has nothing.
     */
    private static Cutting made(BorderQuantity of, Level at, ComparisonClaim claim,
                                Quantities quantities) {
        // Whether the order has a place at that level for a line to be, which is the order's answer
        // and not the carrier's. An order whose only number is where two positions meet has one
        // place and no others; every order that counts is parted anywhere, whether or not it takes
        // the level itself.
        if (!of.levels().canCutAt(at)) {
            return null;
        }
        return new Cutting(of, at, claim, quantities.runsBetween(direction(of)));
    }

    /**
     * The line an arithmetic form over several positions draws.
     *
     * <p>Read where neither of the narrower readings could be, and about the same comparison. This
     * is the case domain testing exists for — a partition defined by a condition over more than one
     * variable — and the four sides of the box its positions sit in are not it.
     *
     * <p>Each position on the order it is written back on, which the reading answers per position.
     * The orders are the parameter and not something asked for here: a position with no number is
     * one a sum has nothing to add, and whether every term has one is what the caller settled
     * before choosing this shape at all. So this draws a line and never declines — left able to,
     * the next refusal added to it would arrive where a missing order is reported and be described
     * as one.
     *
     * <p><b>Whatever the operator states, and not orders alone.</b> {@code 2 * a == 8} names four
     * and {@code a + b == 10} names the place their sum reaches ten, and both are quantities this
     * reads. Refused here for not ordering its values, an equality over a form was reported as a
     * rule written in a form this compiler cannot take apart — a sentence about this compiler, and
     * the form is right here.
     */
    private static Cutting overAForm(String behavior, AffineReading read,
                                     java.util.Map<NumericTerm, TermOrders> on,
                                     Quantities quantities) {
        Cutting drawn = made(new BorderQuantity.OverAForm(behavior, read.form(), on),
                new Level.ACount(new Count(read.cut())), read.claim(), quantities);
        if (drawn == null) {
            // What this watches: `OverAForm.levels()` answers `steppingBy` or `overFiniteDecimals`,
            // and neither of those parts anywhere but everywhere — the one order that names a
            // single place is the one two values meet on, and every term here counts, so that order
            // is not reachable from this shape. An override added to either of those two is what
            // would bring it here, and it should stop rather than be reported as a rule about
            // values nothing draws a line on, which is the opposite of what would have happened.
            throw new IllegalStateException(
                    "a form over orders that count produced a line its order has no place for: "
                            + read.form() + " at " + read.cut());
        }
        return drawn;
    }

    /**
     * What this cuts, as the direction it runs.
     *
     * <p>Asked of the quantity rather than of which variant of quantity it is. A rule written
     * {@code 2 * n > 40} arrives as a form over twice a position and cuts the position, and a
     * reading that told those apart by the shape it was holding reported the model as drawing one
     * line through {@code n} where it draws two.
     */
    QuantityKey quantity() {
        return QuantityKey.of(direction(of));
    }

    /** How much of the quantity this rule wrote, which is what a level of one reads as on the
     *  other. */
    java.math.BigDecimal per() {
        return QuantityKey.per(direction(of));
    }

    /**
     * Where it parts that quantity's values, in the quantity's own units.
     *
     * <p>Found on the order the rule was written on, which is the order that knows which levels the
     * written form attains — {@code 2 * n <= 9} cuts the even numbers and nine is not one of them,
     * so the two sides part between eight and ten. Read back afterwards, which is exact: a level the
     * written form attains is a multiple of how much of the quantity it wrote.
     */
    Seam seam() {
        ComparisonClaim.Cut order = ordering();
        // The place under the value, where the rule names one. Such a rule parts the values twice —
        // under what it names and over it — and this is the lower of the two, which is a place they
        // genuinely part rather than a side chosen for a rule that has none. Which of the two bounds
        // which run is asked where the runs are ({@link Border#partedBy}), and is no part of this.
        Towards belongsTo = order == null ? Towards.ABOVE : order.valueBelongs();
        LinearForm<NumericTerm> direction = direction(of);
        java.math.BigDecimal per = QuantityKey.per(direction);
        return Seam.of(of.levels(), at, belongsTo,
                new Seam.Scale(per, direction.coefs().size() == 1
                        ? of.carrierOf(direction.coefs().keySet().iterator().next()) : null));
    }

    /**
     * The form a quantity runs along, whichever of the three it is.
     *
     * <p>One position's own values are that position with a coefficient of one; how far two
     * positions stand apart is their difference; and a form is itself. The three used to be told
     * apart by every reader that wanted to know what a rule divided, and only one of them was
     * treated as dividing anything.
     */
    private static LinearForm<NumericTerm> direction(BorderQuantity of) {
        return switch (of) {
            case BorderQuantity.OfACoordinate one -> LinearForm.atom(one.term());
            case BorderQuantity.Apart two -> LinearForm.<NumericTerm>atom(two.on().term())
                    .minus(LinearForm.atom(two.against().term()));
            case BorderQuantity.OverAForm many -> many.form();
        };
    }

    /**
     * The position this cuts, where what it cuts is one position's own values. Null for every other
     * quantity, which is what tells a caller whether an axis is divided by this rule.
     *
     * <p><b>Asked of the canonical quantity and not of the shape the comparison arrived as.</b>
     * {@code 2 * n > 40} reaches this as a form over twice a position and divides that position at
     * twenty; read off the shape, it divided nothing, and a report counted two equivalence
     * partitions where the model states three.
     *
     * <p>Whether the line falls on a value of the position is a different question and not this
     * one. {@code 3 * d <= 1} cuts at a third, which no decimal this language writes, and the
     * behavior still answers one way below it and another way above — so the position has two
     * classes and no number to name the line by. Asked here, that answer made an equivalence
     * partition a thing this compiler can write a boundary for rather than a thing the model
     * distinguishes (issue #880).
     */
    NumericTerm.FromOnePosition dividedPosition() {
        java.util.Map<NumericTerm, java.math.BigDecimal> direction = quantity().direction();
        // And only where one position answers that number. A quantity read from somewhere else
        // divides no position however few terms it is over, so there is nothing here for a class
        // to be a class of.
        return direction.size() == 1
                ? direction.keySet().iterator().next().atOnePosition() : null;
    }

    /**
     * The one value of the position this rule names, or null where the position has none there.
     *
     * <p>Apart from {@link #dividedValue}, and the two are different questions that one answer had
     * been serving. A rule that orders the values around its line owes a row at the value beside the
     * line — {@code 2 * n <= 9} cuts between four and five and the row is written at four. A rule
     * that names a value names the line itself, and {@code 2 * n == 9} names no whole number at all
     * because nine halved is not one. Answered as the value beside the line, such a rule would put
     * four in a class of its own and four does not satisfy it.
     *
     * <p>Which is a fact about the position and not about the rule: that the canonical quantity is
     * one coordinate says the rule cuts that position, and whether the position has a value where
     * the line falls is asked of the order it sits on.
     */
    Place singledValue() {
        // On the order the position it divides is written on. A quantity used to answer with one
        // order for everything under it; a form may now be over positions written back differently,
        // and the value named here is a value of one of them.
        NumericTerm divides = dividedPosition();
        return seam().at().asAValueOf(divides == null ? null : of.carrierOf(divides));
    }

    /**
     * The value of that position the classes either side of this line meet at.
     *
     * <p>Read off the seam rather than off the threshold, so a rule that wrote a multiple of the
     * position names a value the position holds: {@code 2 * n <= 9} parts the whole numbers between
     * four and five, and nine halved is not a whole number at all. Which of the two the classes meet
     * at is which side the threshold's own value belongs to, and that is the rule's to say.
     */
    Place dividedValue() {
        ComparisonClaim.Cut order = ordering();
        if (order == null) {
            throw new IllegalStateException("which value a rule divides at, asked of one that names"
                    + " a value and divides at neither side of it: " + at);
        }
        Seam seam = seam();
        Level side = order.valueBelongs() == Towards.BELOW ? seam.below() : seam.above();
        return side instanceof Level.OnACarrier on ? on.at() : null;
    }

    /**
     * Whether the quantity takes the level this rule names.
     *
     * <p>Asked of the quantity, which is what knows. {@code 2 * a == 8} names four and takes it;
     * {@code 2 * a == 9} takes the even numbers and nine is not one, so it names no value the
     * quantity holds; {@code a + b == 10} takes ten and {@code 2 * a + 2 * b == 9} does not. One
     * question, and the same answer whether the quantity is one position's own values or a form
     * over several.
     *
     * <p>Read off whether a value of a <em>position</em> could be written instead, this was the
     * wrong question wearing the right answer: a form over several positions has no value of a
     * position at all, so every rule singling one out on such a quantity came back naming nothing —
     * which is true of {@code 2 * a + 2 * b == 9} and false of {@code a + b == 10}.
     */
    boolean takesTheValueItNames() {
        return of.levels().attainable(at);
    }

    /**
     * The positions the canonical quantity is over, as a reader is sent to them.
     *
     * <p>What a rule that was read is filed at. The quantity is what the rule is about, so a
     * position the arithmetic cancelled is not one it says anything about: {@code a + b - b + c <=
     * 10} is {@code a + c <= 10}, and a note filed at {@code b} would say the rule relates a
     * position it does not mention.
     */
    java.util.List<souther.compiler.inputs.FilingCoordinate> over() {
        // Where a reading that reached the numbers files them, which is one answer for every such
        // reading ({@link AffineReading#filedAt}): the terms themselves, in the order a document
        // names them. Written out here, a reader that reached the numbers by another way would
        // write it out again, and the two would file one rule at two coordinates.
        return AffineReading.filedAt(direction(of).coefs().keySet());
    }

    /**
     * What the rules leave this quantity, narrowed by what actually arrives at the comparison —
     * where the arrival is an interval of this quantity at all, and {@link #within} untouched where
     * it is not.
     *
     * <p>Whether it is lives here, because it is a reading of the quantity and the quantity is this
     * record's one answer. Asked by the caller instead, a second reader of the direction would stand
     * beside this one, free to disagree with it about what the rule is about.
     *
     * <p><b>The quantity is the position's own value, and nothing else is taken.</b> An arrival
     * states an interval of the value at one path, so it is an interval of this quantity exactly
     * where the two are the same value — a quantity that is some multiple of the position is on
     * another order, and its line is a level of that one. Taken with a change of units, the
     * arithmetic would be written for a shape no reader produces: what publishes an arrival is one
     * side of the comparison being that position, and the quantity such a comparison cuts is the
     * position itself. So the narrower rule is the whole of what is reachable, and what a multiple
     * costs is precision on a line that stands rather than an answer that is wrong.
     *
     * <p>Total over what it takes. An arrival this cannot project restricts nothing — not being
     * able to read a fact is not a proof — so the answer is what the declarations leave, unchanged.
     * The whole-state proof that nothing arrives is not taken here at all: that arm of the arrival
     * is no interval of anything, and a caller holds it apart ({@code ComparisonAssessment}).
     *
     * <p>Sound as a meet of two over-approximations: every arriving row is inside both, so a line
     * outside the meet is a line no arriving row reaches.
     */
    souther.compiler.numeric.NumericDomain.Bounds withinGiven(
            souther.compiler.reach.ComparisonArrival.Values arriving) {
        LinearForm<NumericTerm> direction = direction(of);
        if (direction.coefs().size() != 1 || direction.constant().signum() != 0) {
            return within;
        }
        java.util.Map.Entry<NumericTerm, java.math.BigDecimal> only =
                direction.coefs().entrySet().iterator().next();
        if (only.getValue().compareTo(java.math.BigDecimal.ONE) != 0
                || !(only.getKey() instanceof NumericTerm.ValueOf(var position))
                // The one thing this cannot read off its own quantity: which position the interval
                // is of. Both are the position the comparison turns on today and the check is what
                // says so — the day the two readings part, a line would be held inside the values of
                // somewhere else.
                || !position.equals(arriving.path())) {
            return within;
        }
        return within == null ? arriving.bounds() : within.meet(arriving.bounds());
    }

    /** Whether the rule singles a value out rather than ordering the values around it. */
    boolean singles() {
        return claim instanceof ComparisonClaim.Singled;
    }

    /**
     * The order this rule placed on the values either side of its line, or null where it placed
     * none.
     *
     * <p>Which side the value it wrote belongs to is an order's answer and only an order's. A rule
     * that names a value parts that value from every other one and has no side of its own, so a
     * caller wanting one is holding a line whose shape it has to have established.
     */
    ComparisonClaim.Cut ordering() {
        return claim instanceof ComparisonClaim.Cut cut ? cut : null;
    }

    /** The line this draws, as a border reads it. */
    BoundaryTarget target() {
        return BoundaryTarget.at(of, at);
    }
}
