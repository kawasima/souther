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
     * One condition this path consulted: what it came out as, where a run through it is seen, and
     * what it states about the input.
     *
     * <p>Only the first is part of what the path is. Which distinctions a rule turns on is what
     * tells it from another rule; where each of them is written is what joins it to a run, and what
     * it states is what a search composes a row against.
     *
     * @param states the condition in the words a composer of a row already works from, so that a
     *               column of a decision table and the region a row for it is looked for in cannot
     *               be read off two accounts of one comparison
     */
    record Consulted(DecidedCondition answer, ShownBy shown, OnTheWay states) {

        Consulted {
            if (answer == null || shown == null || states == null) {
                throw new IllegalArgumentException("a condition a path consulted is an answer,"
                        + " where it is seen, and what it states");
            }
        }
    }

    /** This path with {@code answer} on it, or null where the path already answers that column the
     *  other way. */
    DecisionPath and(DecidedCondition answer, ShownBy shown, OnTheWay states) {
        return and(new DecisionPath(List.of(new Consulted(answer, shown, states))));
    }

    /**
     * Two paths are one where they consulted the same distinctions and got the same answers.
     *
     * <p>Which is what the reading of the ways asks of a path: two that stand for the same way are
     * equal, so a way found twice is one way. What a rule is is its columns, and where each of them
     * is written is not one of them — two ways this reading cannot tell apart by what they turn on
     * are one rule, and holding them apart by an anchor would count what the reading did rather
     * than what the body does.
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof DecisionPath that && answers().equals(that.answers());
    }

    @Override
    public int hashCode() {
        return answers().hashCode();
    }

    private List<DecidedCondition> answers() {
        return consulted.stream().map(Consulted::answer).toList();
    }

    /** What this path states about the input, which is what a search composes a row against. */
    WayToTheBorder states() {
        return new WayToTheBorder(consulted.stream().map(Consulted::states).toList());
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
