package souther.compiler.partition;

import souther.compiler.check.FieldDomains;
import souther.compiler.check.RuleKey;
import souther.compiler.check.RuleReadingContext;
import souther.compiler.inputs.TermPath;
import souther.compiler.numeric.Count;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What one position can take, for a search choosing them one at a time.
 *
 * <p>The rules of the value being composed are read once and handed each settling as it arrives
 * ({@link FieldDomains#composing}), which is what a settling is: an equality taken onto everything
 * the clauses came to. A search that read the declaration at each position would be arriving at the
 * same state by reading every clause over again.
 *
 * <p><b>What a position can take is kept, and this is where keeping it belongs.</b> A depth-first
 * search reaches one settling by many routes and asks the same position about it at each of them,
 * which is a fact about how the search walks and not about what the rules mean. Kept in the reading,
 * it would be the rules holding a note about a caller that repeats itself; kept here, it is one
 * search's own working.
 *
 * <p>One of these per search. What it holds is true under the value being composed and the caller's
 * fixed positions, and both are settled when it is made.
 */
final class ConditionedCandidates {

    private final RuleReadingContext reading;
    private final FieldDomains rules;
    private final Map<Map<RuleKey, Count>, FieldDomains.Composing> states = new HashMap<>();
    private final Map<Asked, List<FixtureTemplate>> found = new HashMap<>();

    /**
     * @param rules the value's rules with nothing settled, read once for the whole search
     */
    ConditionedCandidates(RuleReadingContext reading, FieldDomains rules) {
        this.reading = reading;
        this.rules = rules;
    }

    /**
     * What can stand at {@code position} once {@code settled} is taken on.
     *
     * @param settled the numbers the positions before this one took, named as the rules of the value
     *                being composed name them. Never held on to: what is kept is a copy taken here
     */
    List<FixtureTemplate> at(ConstructionPlan.Slot position, Map<RuleKey, Count> settled) {
        Asked asked = new Asked(position.at(), settled);
        List<FixtureTemplate> known = found.get(asked);
        if (known != null) {
            return known;
        }
        RuleKey field = position.at().ruleKey();
        FieldDomains.ConstructionLimits limits = field == null
                ? FieldDomains.ConstructionLimits.NONE
                : state(asked.settled()).at(field);
        List<FixtureTemplate> stands = Partitions.displacedRepresentativesOf(position.type(),
                reading, limits.values(), limits.held());
        found.put(asked, stands);
        return stands;
    }

    /** The rules with these coordinates settled, worked out once however many positions ask under
     *  it. */
    private FieldDomains.Composing state(Map<RuleKey, Count> settled) {
        return states.computeIfAbsent(settled, at -> rules.composing(FieldDomains.atValues(at)));
    }

    /** One position under one settling, which is what the same answer is the answer to. */
    private record Asked(TermPath at, Map<RuleKey, Count> settled) {

        private Asked {
            settled = Map.copyOf(settled);
        }
    }
}
