package souther.compiler.check;

import souther.compiler.numeric.CountDomain;
import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.Place;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What the rules written on a type leave a value of it between, and which of the names it wears
 * said so.
 *
 * <p>Read here rather than by whoever needs it, because more than one thing needs it and they are
 * not the same reader: what a position is divided and bounded at, and what values can be produced to
 * stand for it. Each working it out for itself is how a reading of one invariant came to mean two
 * things.
 *
 * <p>A range, and only what a range can hold. What the rules leave out between their ends is not
 * here — a caller asking whether some particular value is admitted is asking the domain the rules
 * seed ({@link FieldDomains#mayHoldNothingAt}) and not this.
 *
 * <p>Below whoever composes it with anything else. What a declaration's rules say is a fact about the
 * declaration, and a reader that had to reach a generator to ask it would be reaching past the
 * question.
 */
public final class DeclaredBounds {

    /**
     * One end of a range, and everything the readings established about what put it there.
     *
     * <p>Evidence, plural. Two layers can state the same bound — a wrapper repeating what it wraps —
     * and they are two rules a row could be owed to, which is the accounting a cut already keeps.
     * Holding one would drop an obligation rather than a line of text.
     *
     * <p>The clauses and not the declarations they are written on. Held as declarations, two clauses
     * of one declaration at one value came out as one rule, and a report owed one line for a
     * boundary two rules had drawn ({@link Clause}).
     *
     * <p><b>What was established, and not the lines it comes to.</b> Which lines an end is owed to
     * is a question about the end: a conjunct taken away moves an end that one of its own statements
     * placed, and what that shows is the statement's line found a second way rather than a line
     * beside it. Answered where each piece of evidence is made, that reading has only its own piece
     * in hand and there is no answer to give — so it is answered here, where every piece about this
     * end is ({@link #drawn}).
     */
    public record End(Endpoint at, List<LineProvenance> found) {

        public Place value() {
            return at.at();
        }

        /**
         * The lines this end is owed to, read off everything established about it.
         *
         * <p>A statement's line wherever the evidence is about one statement, whichever reading
         * established it: a comparison that placed the end, and a conjunct whose one statement on
         * this number accounts for it, are the same line found two ways and one row to write.
         *
         * <p>And a line of several statements together only where none of them is a line here
         * already. Taking a conjunct away takes away every statement it made, so where one of them
         * placed this end on its own the intervention was bound to move it — what the coarser
         * reading established is the line that is already here, and nothing beside it. Read as a
         * line of its own, {@code n >= 0 && n /= 100} written into one conjunct owes two rows at the
         * bottom of its range, where the author drew one.
         *
         * <p>Not a rule about which reading wins. Where the ends are apart there is no such
         * subsumption to make and both are lines: {@code n >= 0 && n /= 0} places one at nought and
         * leaves the values starting at one, and the second is a line neither statement drew.
         */
        public List<DeclaredLine> drawn() {
            Set<InvariantStatementId> here = new LinkedHashSet<>();
            for (LineProvenance each : found) {
                InvariantStatementId one = each.aboutOneStatement();
                if (one != null) {
                    here.add(one);
                }
            }
            List<DeclaredLine> out = new ArrayList<>();
            here.forEach(each -> out.add(new DeclaredLine.OfAStatement(each)));
            for (LineProvenance each : found) {
                if (each.aboutOneStatement() != null
                        || each.statements().stream().anyMatch(here::contains)) {
                    continue;
                }
                DeclaredLine together = new DeclaredLine.OfStatementsTogether(each.statements());
                if (!out.contains(together)) {
                    out.add(together);
                }
            }
            return List.copyOf(out);
        }

        /**
         * This end, or {@code other} where it is the stronger, or both where they agree.
         *
         * <p>Which number survives and whether the value is one of the range's own are the same
         * question asked of {@link Endpoint}, so that this and the domain cannot disagree about it.
         * Where the two are at one number both names are kept: each is a rule the line is owed to,
         * however far into the range each of them reaches.
         */
        public static End tighter(End had, End one, boolean upper) {
            if (one == null) {
                return had;
            }
            if (had == null) {
                return one;
            }
            Endpoint at = upper ? Endpoint.upper(had.at(), one.at()) : Endpoint.lower(had.at(), one.at());
            if (had.value().compareTo(one.value()) != 0) {
                return at == had.at() ? had : one;
            }
            List<LineProvenance> both = new ArrayList<>(had.found());
            one.found().stream().filter(n -> !both.contains(n)).forEach(both::add);
            return new End(at, List.copyOf(both));
        }
    }

    /**
     * What a newtype's rules leave the value between, and which of the names it wears said so.
     *
     * <p>The layer is kept because a boundary is reported by the rule that drew it, and a value
     * wearing two names is bounded by rules written on either. Read off the outermost name, an edge
     * that `Minute` drew would be reported as `StartMinute`'s.
     */
    public record Bounds(End min, End max, Carrier carrier) {

        public boolean isEmpty() {
            return min == null && max == null;
        }

        /** Where the value stops, with the lines left behind — for a caller asking how wide the
         *  value is rather than what the model draws through it. */
        public Range range() {
            return new Range(min == null ? null : min.at(), max == null ? null : max.at(), carrier);
        }

        /**
         * The end on one side, or null where nothing stops the values that way.
         *
         * <p>Asked by the side rather than by name, so that a reader walking both ends chooses which
         * it is on once. Everything an end is read beside — where the position's own type stops,
         * which declarations hold it, which way a bound keeps its values — is a second answer to
         * that same choice, and a reader making it once per lookup makes it as many times as it
         * looks.
         */
        public End at(souther.compiler.numeric.EndSide side) {
            return side == souther.compiler.numeric.EndSide.LOWER ? min : max;
        }
    }

    /**
     * Where the rules a value wears stop it, with nothing about which of them said so.
     *
     * <p>Two numbers and no line. What a caller asking how wide a value is wants is where it stops;
     * which rule stopped it there is a line of the model, and a line is owed a row. Held as ends
     * with names on them, this reading's names met the reading of lines' names at the same value and
     * the model owed two rows for one line.
     *
     * <p>Which is what this reading has to give. It hands a whole authored conjunct to the reading
     * of one comparison, so a conjunct written under a denial arrives as a shape it makes nothing
     * of — the statements inside it are not reached here and there is nothing to name a line by.
     * The reading that does reach them is the one lines are read from
     * ({@link FieldDomains#placed}).
     */
    public record Range(Endpoint min, Endpoint max, Carrier carrier) {

        public boolean saysNothing() {
            return min == null && max == null;
        }
    }

    /** What a numeric newtype's own rules leave its value between, for a caller that is asking about
     * the value and not about anything taken of it. */
    public static Range of(TypeView view, RuleReadingSource source) {
        return of(view, source, Carrier.ofValue(view.declared(), source.symbols()), null);
    }

    /**
     * What the rules a position wears leave {@code measure} of it between, or null where nothing
     * here reads the position's values.
     *
     * @param view    the position as it was read: the rules of every name it wears are the rules of
     *                its value, and which names those are is that reading's answer rather than one
     *                worked out again here
     * @param carrier what the clauses' values are read on, or null where nothing here reads them
     * @param measure the operation the number is taken by, or null where the number is the value
     *                itself
     */
    public static Range of(TypeView view, RuleReadingSource source, Carrier carrier,
                           ValueName measure) {
        if (carrier == null) {
            return null;
        }
        Endpoint min = null;
        Endpoint max = null;
        for (DeclaredClauses.Conjunct each : DeclaredClauses.allOf(view.wrappers(), source)) {
            // An end and nothing else. A rule this reads no end from narrows nothing here, and a
            // rule stepping past the last value of the order states an end no value is at — which
            // is a declaration with no value, answered where counts are and not by a bound written
            // at a place nothing can be.
            if (!((measure == null ? InvariantBound.of(each.expr(), carrier)
                    : InvariantBound.ofSize(each.expr(), measure))
                    instanceof InvariantBound.Read.AnEnd placed)) {
                continue;
            }
            InvariantBound read = placed.bound();
            if (read.lower()) {
                min = Endpoint.lower(min, read.end());
            } else {
                max = Endpoint.upper(max, read.end());
            }
        }
        return new Range(min, max, carrier);
    }

    /**
     * The ends {@code placed} puts on one coordinate, or null where it puts none there.
     *
     * <p>The clauses of the value a position sits in, read as what they are: a clause naming one
     * coordinate and a constant places an end exactly as one written on that coordinate's own type
     * does, and which declaration held it is what the line is named by (ADR-0090). Here with the
     * rules a type states rather than beside the reader of them, because an end is an end whichever
     * declaration wrote it and both come out as the same {@link Bounds}.
     *
     * @param kind which of the position's numbers these are wanted for — its own value, or what
     *             some operation answers of it. The operation and not a flag, since a path measured
     *             two ways by two operations has two of these and a flag brings them to one. The
     *             path is the caller's already, which is what {@code placed} is a list of
     */
    public static Bounds placed(List<FieldDomains.Placed> placed,
                                NumberAt.OfWhatNumber kind, Carrier carrier) {
        End min = null;
        End max = null;
        for (FieldDomains.Placed each : placed) {
            if (!each.at().of().equals(kind)) {
                continue;
            }
            End end = new End(each.end(), List.of(each.from()));
            if (each.lower()) {
                min = End.tighter(min, end, false);
            } else {
                max = End.tighter(max, end, true);
            }
        }
        return min == null && max == null ? null : new Bounds(min, max, carrier);
    }

    /**
     * Both, intersected, with every declaration that put an end where it is kept.
     *
     * <p>One coordinate can be bounded from either side of the same rule set — a newtype's own clause
     * and the record holding a field of it — and neither is the other's context. Which number
     * survives is {@link End#tighter}'s, the same answer two layers of newtype already get.
     */
    public static Bounds and(Bounds had, Bounds one) {
        if (one == null) {
            return had;
        }
        if (had == null) {
            return one;
        }
        return new Bounds(End.tighter(had.min(), one.min(), false),
                End.tighter(had.max(), one.max(), true),
                had.carrier() == null ? one.carrier() : had.carrier());
    }

    /**
     * How many of whatever counts a value the rules on its type require it to hold, or 0 where they
     * require none.
     *
     * <p>Which operation counts it is asked of {@link NumericMeasures}, the one list of them, so that
     * a rule this reads and a rule a boundary is drawn on are read off the same call. Not asked of the
     * decoder's constraints: Raoh has no entry for a set's size — a set crosses the boundary as a list
     * and a size chained after the mapping that drops duplicates would count the wrong things — and
     * that absence is a fact about the decoder rather than about what the rule says.
     *
     * <p>What is being counted follows from the type. A string's rule reaches this as readily as a
     * list's, and comes back a floor on characters; a caller reading every floor above zero as a
     * collection that cannot be empty would be answering a question this did not.
     *
     * <p>Of the position as it was read, like every other question about the rules on it. How many a
     * value holds is what the rules of every name it wears say, so this needs the same reading
     * {@link #of} does — and a caller that had already read the position and handed a type over here
     * would have it read a second time, which is one more answer to which names it wears.
     */
    public static int leastCountOf(TypeView view, RuleReadingSource source) {
        return countsHeld(view, source, null).least();
    }

    /**
     * The same, where the record the position sits in has a rule about it too.
     *
     * <p>The higher of the two, because both are rules the construction has to satisfy. A value
     * clearing one and not the other is refused as surely as one clearing neither, so a reader taking
     * either alone offers a position a value something refuses: ask only the type and a field whose
     * floor is its record's is handed the value that holds nothing.
     *
     * <p>Both readings end at {@link CountDomain#leastFrom}, so what a floor comes to as a count is
     * settled once. A second reading here could put a record's {@code > 3} at three while the type's
     * came to four, and the two would disagree about one rule written twice.
     */
    public static int leastCountOf(TypeView view, RuleReadingSource source, FieldDomains.Held held) {
        return countsHeld(view, source, held).least();
    }

    /**
     * How many of whatever counts a value of the position the rules on it allow it to hold, or every
     * number where they cap it in no way.
     *
     * <p>The dual of {@link #leastCountOf} and asked for the same reason: a rule capping a value at
     * none is written on the type as readily as on the record holding one, and a reader finding only
     * the second offered a value at a position the first leaves no room for.
     */
    public static int mostCountOf(TypeView view, RuleReadingSource source) {
        return countsHeld(view, source, null).most();
    }

    /**
     * The same, where the record the position sits in has a rule about it too.
     *
     * <p>The lower of the two, because both are rules the construction has to satisfy -- which is
     * {@link #leastCountOf}'s argument at the other end.
     */
    public static int mostCountOf(TypeView view, RuleReadingSource source, FieldDomains.Held held) {
        return countsHeld(view, source, held).most();
    }

    /**
     * How many a value of the position may hold, both ends of one reading of the rules.
     *
     * <p>Both together, because a caller choosing how many to build needs the pair and neither end
     * alone is safe to stand in for it. A reader holding only the floor builds the fewest a rule
     * allows and never asks whether that many can carry what it is for; one holding only the cap
     * never asks whether the rules leave room to go higher. The two ends are one answer about one
     * snapshot of the rules, and a caller taking them from two calls can be handed a floor above the
     * cap and read it as a range.
     *
     * <p>Empty where the rules leave no count at all, which is a value nothing holds and not a range
     * to walk. That is the model's answer, and a caller that walked an inverted range would step
     * over it silently.
     */
    public static CountRange countsHeld(TypeView view, RuleReadingSource source,
                                        FieldDomains.Held held) {
        ValueName.Stdlib counts = NumericMeasures.takenOf(view.declared(), source.symbols());
        Range sized = counts == null ? null : of(view, source, Carrier.WHOLE, counts);
        Endpoint least = sized == null ? null : sized.min();
        Endpoint most = sized == null ? null : sized.max();
        return new CountRange(
                Math.max(CountDomain.leastFrom(least),
                        held == null ? 0 : CountDomain.leastFrom(held.bounds().min())),
                Math.min(CountDomain.mostFrom(most),
                        held == null ? Integer.MAX_VALUE
                                : CountDomain.mostFrom(held.bounds().max())));
    }

    /**
     * How many a value may hold, from the fewest to the most.
     *
     * <p>Inclusive at both ends, and counted in values. {@code Integer.MAX_VALUE} at the top is the
     * rules capping it in no way, which is not a number to build up to — how far a search goes there
     * is the search's own budget and is nothing this says.
     */
    public record CountRange(int least, int most) {

        /** Whether {@code many} is a count the rules allow. */
        public boolean admits(int many) {
            return many >= least && many <= most;
        }

        /** Whether the rules leave no count at all. */
        public boolean empty() {
            return least > most;
        }
    }

    private DeclaredBounds() {}
}
