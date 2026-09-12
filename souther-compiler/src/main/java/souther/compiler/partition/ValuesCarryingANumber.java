package souther.compiler.partition;

import souther.compiler.check.RuleReadingContext;
import souther.compiler.check.TypeView;
import souther.compiler.inputs.TermPath;
import souther.compiler.types.TypeSymbol;

import java.util.Map;

/**
 * The values of a plan where one position inside it holds a number the caller has.
 *
 * <p>What a walk filling a container to a total needs: each element is a whole value of its own
 * type with the number at the path the total reads, and everything beside it is chosen the way any
 * value of that type is.
 *
 * <p>A source of values and not a second reading of the plan. Which positions are under a position,
 * which case a value is narrowed to, what a container holds and which names a value still has to
 * wear are {@link ConstructionPlan}'s answers, read by {@link PlanComposer} for every caller —
 * walked here from the declarations instead, this agreed wherever the way down was records and lost
 * the value at every sum, newtype and container.
 *
 * <p>So what is left here is one question: where a record's fields come from. The one the caller's
 * number is under comes from below, and the rest are chosen against the record's own rules, read
 * again once the number is in them — so what is offered is a value the model may well admit rather
 * than a shape that carries the number and breaks a rule about the field next to it.
 *
 * @param fixed the position the number is written at, which is the one the plan was made against
 * @param value the number, as the position's own carrier writes it
 */
record ValuesCarryingANumber(TermPath fixed, FixtureTemplate value, RuleReadingContext reading)
        implements PlanComposer.Values {

    @Override
    public FixtureTemplate at(ConstructionPlan.Slot slot) {
        // The value itself, under every name the position wears. What the caller hands over is a
        // number and not a value of the position, so the names its own type wears go on here — the
        // plan's `worn` is what a value already wearing those is still missing, which is what a
        // value chosen at a slot by a search is.
        return slot.at().equals(fixed)
                ? WornNames.under(TypeView.of(slot.type(), reading.source().symbols(), reading.source().published()).wrappers(),
                        value, reading.source())
                : null;
    }

    @Override
    public Map<String, FixtureTemplate> under(ConstructionPlan.Built built,
                                              PlanComposer.Under under) {
        Map.Entry<String, ConstructionPlan.Node> down = fieldTowards(built);
        FixtureTemplate inner = down == null ? null : under.of(down.getValue());
        // A record this module composes is one a module declares. A constructor this cannot name is
        // a value nothing here writes, which is what the empty answer says.
        if (inner == null || !(built.of() instanceof TypeSymbol.AtModule record)) {
            return null;
        }
        return Partitions.fieldsOf(record, reading, java.util.Set.of(),
                Map.of(down.getKey(), inner));
    }

    /**
     * The field of {@code built} the number's position is at or under, or null where none is.
     *
     * <p>Asked of the plan's own positions rather than of the path's steps. The two agree wherever
     * a field step is a field of a record, and part where a narrowing takes no level: the plan's
     * position for a case is written at the same remove as the sum it narrows, so a walk counting
     * steps would be one out from there down.
     */
    private Map.Entry<String, ConstructionPlan.Node> fieldTowards(ConstructionPlan.Built built) {
        for (Map.Entry<String, ConstructionPlan.Node> each : built.under().entrySet()) {
            if (fixed.isAtOrUnder(each.getValue().at())) {
                return each;
            }
        }
        return null;
    }
}
