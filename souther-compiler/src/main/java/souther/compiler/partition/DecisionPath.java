package souther.compiler.partition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The conditions one way through a body consulted, in the order it met them.
 *
 * <p>What a decision rule is made of, as the reading of the ways writes a path. Held as a value
 * because two ways that consulted the same distinctions and got the same answers are one way — which
 * is what lets the reading count what the body does rather than how it got there.
 *
 * <p><b>One column per distinction, however often the path meets it.</b> A body asking one thing
 * twice states one distinction, and a column apiece would admit an assignment where it holds and
 * does not. So a second reading of a column already on the path is dropped where it agrees, and the
 * whole path is refused where it does not — a path assuming a proposition both ways is a
 * contradiction rather than a rule, and refusing it here is what the reading of the ways does with
 * one.
 */
record DecisionPath(List<Consulted> consulted) {

    /** A path that has consulted nothing, which is what a value nothing forks arrives by. */
    static final DecisionPath NOWHERE = new DecisionPath(List.of());

    DecisionPath {
        consulted = List.copyOf(consulted);
    }

    /**
     * One condition this path consulted: what it came out as, and where a run through it is seen.
     *
     * <p>The second is not part of what the path is. Which distinctions a rule turns on is what
     * tells it from another rule; where each of them is written is what joins it to a run, and one
     * body stating one rule in two places states one rule.
     */
    record Consulted(DecidedCondition answer, ShownBy shown) {

        Consulted {
            if (answer == null || shown == null) {
                throw new IllegalArgumentException(
                        "a condition a path consulted is an answer and where it is seen");
            }
        }
    }

    /** This path with {@code answer} on it, or null where the path already answers that column the
     *  other way. */
    DecisionPath and(DecidedCondition answer, ShownBy shown) {
        return and(new DecisionPath(List.of(new Consulted(answer, shown))));
    }

    /** Both paths' conditions, or null where between them they answer one column two ways. */
    DecisionPath and(DecisionPath more) {
        List<Consulted> out = new ArrayList<>(consulted);
        for (Consulted each : more.consulted) {
            Consulted already = at(out, each.answer().condition());
            if (already == null) {
                out.add(each);
            } else if (!already.answer().equals(each.answer())) {
                return null;
            }
        }
        return new DecisionPath(out);
    }

    /** What this path says about {@code condition}, or null where it never consulted it. */
    private static Consulted at(List<Consulted> consulted, DecisionCondition condition) {
        for (Consulted each : consulted) {
            if (each.answer().condition().equals(condition)) {
                return each;
            }
        }
        return null;
    }

    /** The rule this path is, which is its columns and what each came out as. */
    DecisionRule rule() {
        Map<DecisionCondition, DecidedCondition> vector = new LinkedHashMap<>();
        consulted.forEach(each -> vector.put(each.answer().condition(), each.answer()));
        return new DecisionRule(vector);
    }

    /** Where a run down this path is seen, one entry per column in the order it met them. */
    List<ShownBy> shownBy() {
        return consulted.stream().map(Consulted::shown).toList();
    }
}
