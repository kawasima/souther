package souther.compiler.partition;

import souther.compiler.check.Carrier;
import souther.compiler.check.DeclaredBounds;
import souther.compiler.check.MatchedEndAttribution;
import souther.compiler.check.NarrowedBounds;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.Position;
import souther.compiler.inputs.PositionBounds;
import souther.compiler.numeric.EndSide;
import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.numeric.Place;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What a position's own declarations divide it into, derived from the one reading of it.
 *
 * <p>The derivation is one way: a {@link Position} is read, and the answer follows from it. That is
 * what {@link LocalPartition.Open} means and how it is held — {@code Open} has nothing of its own
 * with which to contradict a reading, and there is no way to pair one with a reading that did not
 * run to the end, because there is no way to write a reading down here at all.
 *
 * <p>Which distinctions the position has is not decided here. It is asked of the position, which is
 * what keeps this from being a second crossing of the rules: what is added here is how a value of
 * one is named, recognised in a row, and written down.
 */
final class LocalInspection {

    /**
     * What {@code position} comes to.
     *
     * <p>A position whose rules leave it nothing is asked for its distinctions like any other: the
     * widening that hands the declared ones back belongs to the position, so a reader applying one
     * of its own here would be making that decision a second time and in another place.
     */
    static LocalPartition of(Position position, RuleReadingSource ruleSource,
                             souther.compiler.check.ReadingPolicy policy) {
        // What stands at the position, which is the number the type's own distinctions divide. Said
        // here because here is where it is known: the cases come from the reading of what may stand
        // at this place, so a class made from one of them is a class of that value and of nothing
        // taken of it. Read back off the class's meaning, or taken from a number the position was
        // said to be measured at, this is the same answer arrived at by guessing.
        NumericTerm.FromOnePosition standing = new NumericTerm.ValueOf(position.path());
        // Nothing is being built here: a position is being inspected, so no value's own name is
        // already open.
        List<PartitionClass> classes =
                PartitionClasses.of(position.obligationCases(), position.view(), ruleSource, policy,
                                java.util.Set.of())
                        .stream().map(each -> each.ofTheNumber(standing)).toList();
        List<DeclaredMeasure> measures = new ArrayList<>();
        // One measure per number, each built from the evidence that names that number. A number the
        // classes are not about and no rule drew a line on is a number nothing measured, and it is
        // left out rather than published as a measure of nothing.
        for (PositionBounds at : position.bounds()) {
            List<PartitionClass> here = at.term().equals(standing) ? classes : List.of();
            List<Cut> cuts = position.nothingExists() ? List.of()
                    : cutsOf(axisBounds(at.ownEnds(), at.rangeLeft()), at.ownEnds(),
                            at.narrowedEnds());
            if (here.isEmpty() && cuts.isEmpty()) {
                continue;
            }
            // Whether a row can be written at an edge is a question about the whole value the
            // position sits in, so it is answered once for the parameter. A rule this could not
            // read is a way that value can be refused, wherever in it the rule is written.
            measures.add(new DeclaredMeasure(at.term(), here,
                    cuts.isEmpty() ? new CutEvidence.None()
                            : new CutEvidence.Present(cuts, position.projection()),
                    at.narrowedEnds()));
        }
        if (measures.isEmpty()) {
            // Nothing this reading found divides the position, which is all this says. Whether an
            // absence follows is answered where the position's standing questions and the body's
            // rules are, and a widening this reading recorded about its own set is no part of it —
            // read here, one reader being short of a rule another reader took in was written down
            // as the position being one nothing could read.
            return new LocalPartition.Open();
        }
        // What the reading was short of is not restated here. It is the position's own answer and
        // travels as one value from there (`ReadingResidue`), so a local inspection copying half of
        // it would be a second place the pair could come apart.
        return new LocalPartition.Divided(measures);
    }

    /**
     * Where the position is divided: the type's own bound, taken in to where the record it sits in
     * stops.
     *
     * <p>Only taken in. A record's rule moves an edge the type already has; it does not put one on a
     * position the type left open. An {@code Int} nobody bounded stays a position the model draws no
     * line through, which is what a report says of it (ADR-0090) — giving it an edge here would make
     * a rule relating two fields into a partition of one of them.
     *
     * <p>So this is not what the position can hold, and reading it as that is how a cap written on
     * the record alone became invisible: see {@code TypeBounds#admissible}.
     */
    private static DeclaredBounds.Bounds axisBounds(DeclaredBounds.Bounds own,
                                                    NumericDomain.Bounds left) {
        if (own == null || left == null) {
            return own;
        }
        // The value moves and the names do not: a record narrowing an edge does not take it away
        // from the rule that put one there, and which record did the narrowing is said beside it.
        return new DeclaredBounds.Bounds(
                own.min() == null ? null
                        : new DeclaredBounds.End(Endpoint.lower(own.min().at(), left.min()),
                                own.min().from()),
                own.max() == null ? null
                        : new DeclaredBounds.End(Endpoint.upper(own.max().at(), left.max()),
                                own.max().from()),
                own.carrier());
    }

    /**
     * The cuts of a position whose range is already settled.
     *
     * @param bounds   where the position stops, the record it sits in taken into account
     * @param own      where its own type stops, so that an end the record moved can say so
     * @param narrowed what the value this sits in leaves the position, and who is holding each end
     *                 of that. Read per end because they are separate answers: one declaration can
     *                 be holding a minimum while another holds the maximum, and one slot for both
     *                 names the wrong one for at least one of them. Not the ends {@code bounds}
     *                 carries — those are what the position's own type stops at, taken in to where
     *                 every rule reaching it leaves it, and these names were worked out against what
     *                 the value it sits in projects. Whether a name is about the end a cut stands
     *                 at is asked of this; whether a cut owes anything to a name that is, is what
     *                 {@code moved} below decides
     */
    private static List<Cut> cutsOf(DeclaredBounds.Bounds bounds, DeclaredBounds.Bounds own,
                                    NarrowedBounds narrowed) {
        // Nothing about the shape of the position's type. An end is here because some clause placed
        // it, and a clause naming a field of a record places one on a bare `Int` and on the length
        // of a bare `List<Int>` as readily as on a newtype over either.
        if (bounds == null || bounds.isEmpty()) {
            return List.of();
        }
        Map<String, Cut> byValue = new LinkedHashMap<>();
        // One side at a time, and everything about a side read from the side. Where the position
        // stops, where its own type stops, which of the reading's two answers is about it and which
        // end a bound placed are four answers to one choice, and a reader making that choice four
        // times can make it four ways.
        for (EndSide side : EndSide.values()) {
            cut(byValue, side, bounds.at(side), own == null ? null : own.at(side),
                    bounds.carrier(), narrowed);
        }
        return List.copyOf(byValue.values());
    }

    /**
     * One end as a cut, owed once to each rule that put it there.
     *
     * @param side which of the position's two ends this is. Known here because the ends are read one
     *             at a time, and nowhere below: a bound records where it stops, and everything past
     *             this holds the range the rules leave rather than the end that made it
     */
    private static void cut(Map<String, Cut> into, EndSide side, DeclaredBounds.End end,
                            DeclaredBounds.End own, Carrier carrier, NarrowedBounds narrowed) {
        if (end == null) {
            return;
        }
        // Whether what the value above holds is about this end at all. Where the position's own type
        // stops is taken into account below and the value above knows nothing of it, so the two can
        // leave the position in different places — and the names are worked out against one number
        // and are true of no other.
        Optional<MatchedEndAttribution> held = narrowed.matching(side, end.at());
        // Taken in, which a record can do by moving the end or by taking away the value it stops
        // at. `low < high` under one `[0, 1]` leaves `low` the same 1 and no longer holding it, and
        // that is the record's doing as much as a smaller number would have been — so this asks
        // whether the two are the same end, which is where a range stops and not what an end holds.
        //
        // Which declarations can move this cut, and not which are holding the end they were read
        // at. The two are different questions and this is the first. Where the type's own end is
        // the tighter, `axisBounds` answers with it and nothing the record leaves reaches the cut:
        // taking a declaration's clauses away only widens what the record leaves, and the tighter of
        // the two is still the type's. So a declaration named here would be one an author can
        // rewrite while the line stays where it is — and a line a declaration took in is that
        // declaration's to answer for rather than the type's
        // ({@link AuthoredLine#obligationOwners}), so writing it down does not add a name beside the
        // type's: it moves the row to somebody who cannot move the line.
        boolean moved = own != null && !own.at().sameAs(end.at());
        // Carried on where both hold. Being about this end is what says a name may be written here
        // at all; whether it should be is this reader's own question, and the answer travels as what
        // the reading gave rather than as the names it came to, so that nothing between here and the
        // origin can put it beside another end.
        MatchedEndAttribution took = moved ? held.orElse(null) : null;
        // Wrapped here, and this is not a consumer settling what a rule is: which rule drew the end
        // was settled where the clause was read and arrives as it was. What is added is a
        // boundary's own answer about that rule — that a reading of it drew this cut, taken in by
        // these declarations — which is nothing the rule says about itself.
        for (DeclaredBounds.Drawn drawn : end.from()) {
            put(into, carrier, end.value(),
                    new LineOrigin.InvariantOrigin(drawn.line(), side, end.at().inclusive()),
                    end.at(), took);
        }
    }

    private static void put(Map<String, Cut> into, Carrier carrier, Place at,
                            LineOrigin.InvariantOrigin drawnBy, Endpoint cutAt,
                            MatchedEndAttribution took) {
        // The rule as the end already names it. Narrowing is the one thing said here, and it is
        // said about the rule rather than in place of it: which declarations took the end in is a
        // fact about this reading of the position, and what drew the end is not.
        LineOrigin origin = LineOrigin.NarrowedOrigin.of(drawnBy, cutAt, took);
        Cut cut = Cut.at(carrier, at, origin);
        into.merge(cut.key(), cut, (had, _) -> had.and(origin));
    }

    private LocalInspection() {}
}
