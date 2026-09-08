package souther.compiler.inputs;

import souther.compiler.check.ProjectionEvidence;
import souther.compiler.check.RuleRef;
import souther.compiler.check.TypeView;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;
import souther.compiler.values.AdmissibleSet;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The one reading of a position, and what it came to.
 *
 * <p>Package-private, so that {@link Position} is an answer and not a shape anything can fill in.
 * A public record is a constructor anything can call, and an artifact whose point is that it was
 * made once cannot be one.
 *
 * <p>The fields are what the reading saw on the way; {@link #reading} and {@link #obligations} are
 * what follows from them. Held together because they are one reading: copied apart, the day
 * somebody works out {@link #bounds} differently for a position that has distinctions is a day the
 * compiler contradicts itself about where the same values stop.
 *
 * @param declared what the position's type states before any rule was crossed with it, kept so that
 *                 a widening can hand it back and so that a distinction this position does not have
 *                 can be told from one the rules refused
 */
record ReadPosition(TermPath path, TypeView view, List<PositionBounds> bounds,
                    boolean nothingExists,
                    ProjectionEvidence projection, List<Case> declared, ReadingResult reading,
                    ObligationDomain obligations, AdmissibleSet admitted,
                    List<RuleWithoutALine> rulesWithoutALine,
                    List<RuleRef> endsLeftOpenByAChoice,
                    List<StandingQuestion> unansweredQuestions,
                    Set<RulesLeftUnread> rulesLeftUnread,
                    StructuralInspection structure) implements Position {

    ReadPosition {
        bounds = List.copyOf(bounds);
        // The numbers are what a reader looks these up by, so two of them under one name is one
        // silently standing for the other. And every one of them is a number of this position:
        // bounds of somewhere else held here would answer a question about this place with a range
        // read off another.
        Set<NumericTerm.FromOnePosition> named = new LinkedHashSet<>();
        for (PositionBounds each : bounds) {
            if (!each.term().position().equals(path)) {
                throw new IllegalArgumentException(each.term() + " is a number of "
                        + each.term().position() + ", and is held under " + path);
            }
            if (!named.add(each.term())) {
                throw new IllegalArgumentException(
                        path + " has two answers about " + each.term());
            }
        }
        declared = List.copyOf(declared);
        rulesWithoutALine = List.copyOf(rulesWithoutALine);
        endsLeftOpenByAChoice = List.copyOf(endsLeftOpenByAChoice);
        unansweredQuestions = List.copyOf(unansweredQuestions);
        // Kept in the order the readers found them, so that two runs over one model produce the
        // same value — the reason `MeasureClosure` keeps its gaps that way too.
        rulesLeftUnread = Collections.unmodifiableSet(new LinkedHashSet<>(rulesLeftUnread));
    }

    @Override
    public Type type() {
        return view.declared();
    }

    /**
     * The widening first, because it makes the reading's own answer unusable.
     *
     * <p>Where the rules leave the position nothing, every distinction is refused and every one of
     * them is counted anyway. Read in the other order, that position would answer {@code Refused}
     * about a distinction a report is asking for a row at — the two halves of one position saying
     * opposite things, which is what putting the widening here is for.
     */
    @Override
    public Admits admissionOf(TypeSymbol leaf) {
        return admissionOf(distinction(
                each -> each instanceof Case.SumCase sum && sum.leaf().equals(leaf)));
    }

    @Override
    public Admits admissionOf(Refinement narrowing) {
        return admissionOf(distinction(each -> narrowing.equals(Refinement.of(each))));
    }

    /**
     * Which distinction of this position a key names, or null where it names none.
     *
     * <p>One lookup and two keys. A leaf and a narrowing pick out the same distinction where they
     * pick out one at all, and worked out twice the two would answer differently on the day the
     * distinctions changed — a caller asking by leaf being told a case stands while one asking by
     * narrowing was told nothing was read about it.
     */
    private Case distinction(java.util.function.Predicate<Case> named) {
        for (Case each : declared) {
            if (named.test(each)) {
                return each;
            }
        }
        return null;
    }

    @Override
    public Admits admissionOf(Case one) {
        if (one == null) {
            // A key that names no distinction of this position. Said as the reading stating no such
            // distinction, which is what it is: a fact about the two vocabularies not being about
            // the same values, and not about how far the rules were read.
            return new Admits.Unsettled(new Unsettlement.NoSuchDistinction());
        }
        if (obligations instanceof ObligationDomain.Conservative) {
            return new Admits.Unsettled(new Unsettlement.RulesLeaveNothing());
        }
        if (reading.refused().contains(one)) {
            return new Admits.Refused();
        }
        if (!reading.kept().contains(one)) {
            // Not a distinction this reading has. Nothing was read about it, which is a different
            // answer from the rules having refused it.
            return new Admits.Unsettled(new Unsettlement.NoSuchDistinction());
        }
        return switch (reading) {
            case ReadingResult.Complete _ -> new Admits.Admitted();
            case ReadingResult.NotSeparated _ ->
                    new Admits.Unsettled(new Unsettlement.AlternativesNotSeparated());
            case ReadingResult.Partial partial ->
                    new Admits.Unsettled(new Unsettlement.ReadingStopped(partial.why()));
            case ReadingResult.Unsupported unsupported ->
                    new Admits.Unsettled(new Unsettlement.ReadingStopped(unsupported.why()));
        };
    }
}
