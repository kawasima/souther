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
final class DecisionPath {

    /** A path that has consulted nothing, which is what a value nothing forks arrives by. */
    static final DecisionPath NOWHERE = new DecisionPath(List.of());

    private final List<Consulted> consulted;

    /**
     * The rule this path is, made once.
     *
     * <p>Held rather than worked out per call, which is why this is a class and not a record. What
     * tells two paths apart is the rule, so the walk that gathers the ways asks for it once per
     * candidate per way already gathered — and a rule built from the columns each time made the
     * cost of reading one body's ways grow with their square.
     *
     * <p>Made in the constructor rather than on first use. The value is what this is, and a field
     * filled later is a value that answers differently before and after somebody asks.
     */
    private final DecisionRule rule;

    DecisionPath(List<Consulted> consulted) {
        this.consulted = List.copyOf(consulted);
        Map<DecisionCondition, DecidedCondition> vector = new LinkedHashMap<>();
        this.consulted.forEach(each -> vector.put(each.answer().condition(), each.answer()));
        this.rule = new DecisionRule(vector);
    }

    /** The conditions this path consulted, in the order it met them. */
    List<Consulted> consulted() {
        return consulted;
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
     * Two paths are one where they are the same rule.
     *
     * <p>Which is what the reading of the ways asks of a path: two that stand for the same way are
     * equal, so a way found twice is one way. What a rule is is its columns and what each came out
     * as — {@link DecisionRule}'s answer, and asked of it rather than worked out again here, so
     * that a path and the rule it is cannot be told apart by different rules.
     *
     * <p>Which makes it the order-independent answer the rule is. The order the walk met the
     * conditions in is kept for a reader and is no part of what a rule is: two ways that consulted
     * the same distinctions and got the same answers are one rule whatever order they met them in,
     * and holding them apart by that order would count what the reading did rather than what the
     * body does.
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof DecisionPath that && rule.equals(that.rule);
    }

    @Override
    public int hashCode() {
        return rule.hashCode();
    }

    /** What this path states about the input, which is what a search composes a row against. */
    WayToTheBorder states() {
        return new WayToTheBorder(consulted.stream().map(Consulted::states).toList());
    }

    /**
     * What this path asks of the answers a row stands the dependencies in with.
     *
     * <p>Read here, where what a condition came out as and where a report about it asks for its
     * place are one entry. Worked out from the rule alone, a demand would have no anchor and the
     * one it was given would be whichever the two lists happened to line up at.
     */
    AnswersDemanded demands() {
        List<AnswerDemand> stated = new ArrayList<>();
        List<ConditionReportAnchor> declined = new ArrayList<>();
        for (Consulted each : consulted) {
            AnswerDemand demand = demanded(each);
            if (demand != null) {
                stated.add(demand);
            } else if (asksSomethingOfAnAnswer(each.answer())) {
                declined.add(each.states().anchor());
            }
        }
        return stated.isEmpty() && declined.isEmpty()
                ? AnswersDemanded.NOTHING : new AnswersDemanded(stated, declined);
    }

    /**
     * What one consulted condition asks of an answer, or null where it asks nothing this can state.
     *
     * <p>Null covers two things a reader tells apart elsewhere: a condition about the input alone,
     * which asks nothing of any answer, and a condition about an answer this has no way of stating.
     * Which of the two it is is {@link #asksSomethingOfAnAnswer}'s, asked of the column rather than
     * worked out from the absence here.
     */
    private static AnswerDemand demanded(Consulted one) {
        ConditionReportAnchor anchor = one.states().anchor();
        return switch (one.answer()) {
            case DecidedCondition.Narrowed(var condition, var to) ->
                    condition.of() instanceof DecisionSubject.AnAnswer at
                            ? new AnswerDemand.ACase(at.answered(), anchor, at.steps(), to) : null;
            case DecidedCondition.Stood(var condition, var held) ->
                    condition.of() instanceof DecisionSubject.AnAnswer at
                            ? new AnswerDemand.ATruth(at.answered(), anchor, at.steps(), held)
                            : null;
            case DecidedCondition.Compared(var condition, var held) -> compared(condition, held,
                    anchor);
            case DecidedCondition.Unread _ -> null;
        };
    }

    /**
     * A comparison as a demand on one answer, or null where it is about anything else.
     *
     * <p>Of one answer and of nothing beside it. A form over two answers, or over an answer and a
     * number of the input, is one statement about the pair and nothing here composes two values to
     * it together — so it is left unstated, which is what a way carries as a condition a row may
     * not satisfy rather than as one that is not there.
     */
    private static AnswerDemand compared(DecisionCondition.AComparison condition, boolean held,
                                         ConditionReportAnchor anchor) {
        InjectedAnswer only = null;
        for (DecisionAtom atom : condition.form().coefs().keySet()) {
            if (!(atom instanceof DecisionAtom.OfAnAnswer(var at))) {
                return null;
            }
            if (only != null && !only.equals(at.answered())) {
                return null;
            }
            only = at.answered();
        }
        // The side the path took, which is what a row has to satisfy. The column faces the way its
        // proposition does whichever side was taken, so the denial is taken here rather than left
        // for a composer to work out from a flag travelling beside the form.
        return only == null ? null : new AnswerDemand.AComparison(only, anchor, condition.form(),
                held ? condition.proposition() : condition.proposition().denied());
    }

    /**
     * Whether a column is about an answer at all.
     *
     * <p>Asked of the column and not of what a demand came to. A condition about the input alone is
     * one this has nothing to say about, and counting it as declined would report every body that
     * decides on its own input as a body whose answers could not be stated.
     */
    private static boolean asksSomethingOfAnAnswer(DecidedCondition answer) {
        return switch (answer.condition()) {
            case DecisionCondition.ACase(var of) -> of instanceof DecisionSubject.AnAnswer;
            case DecisionCondition.ATruth(var of) -> of instanceof DecisionSubject.AnAnswer;
            case DecisionCondition.AComparison(var form, var _) -> form.coefs().keySet().stream()
                    .anyMatch(DecisionAtom.OfAnAnswer.class::isInstance);
            case DecisionCondition.AConditionNotRead _ -> false;
        };
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
        return rule;
    }

    /** Where a run down this path is seen, one entry per column in the order it met them. */
    List<ShownBy> shownBy() {
        return consulted.stream().map(Consulted::shown).toList();
    }
}
