package souther.compiler.query;

import souther.compiler.check.BoundaryInput;
import souther.compiler.partition.AnAnswerComposed;
import souther.compiler.partition.AnswerDemand;
import souther.compiler.partition.AnswersDemanded;
import souther.compiler.partition.DecisionArgument;
import souther.compiler.partition.DecisionSubject;
import souther.compiler.partition.FixtureTemplate;
import souther.compiler.partition.Generator;
import souther.compiler.partition.InjectedAnswer;
import souther.compiler.partition.StoodInAnswer;
import souther.compiler.types.LeafScalar;
import souther.compiler.types.ValueName;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a row takes to stand a behavior's dependencies in for one way through its body.
 *
 * <p>Every dependency the behavior requires, and not only the ones the way turns on. A row that
 * answers what the way asks and leaves a clock unanswered is a row nothing applies, so the two are
 * composed together — the first against what the way demands, the rest against what their own
 * answer type leaves.
 *
 * <p><b>What each answer is for is carried and not decided here.</b> One value serving every asking
 * of a dependency is what a row writes as a {@code with}; askings wanting different values are what
 * a table beside the block is keyed on. Which of the two a block prints is a projection of what
 * comes back, and the identity such a table is keyed by is the one the decision read.
 *
 * <p><b>What could not be composed is an answer and never an absence.</b> A row short of a stand-in
 * its target requires is a row nothing applies, and one whose askings want two answers at one call
 * it makes is a row that cannot be written — both are said in the words a search comes back with,
 * so that no reader downstream has to work out what an empty list meant.
 *
 * @param requires   what the behavior has to stand in for, in the order it requires them
 * @param standing   one subject per dependency a value can be composed for, and no entry for one
 *                   whose answer no position stands at. Handed in rather than made: where a reading
 *                   of a type is made is where every reading of a behavior's input is made
 * @param parameters the behavior's positions, in the order it takes them, which is how an argument
 *                   naming one is written down
 */
record AnswersForARule(RequiredDependencies requires,
                       Map<ValueName.Behavior, AnswerSubjects> standing,
                       List<String> parameters) {

    /**
     * What a row taking {@code demanded} stands the dependencies in with, given the values it
     * writes.
     *
     * <p>The row's own values, because an argument of an asking may be a position the row writes
     * at: which call a table's row answers for is the value that reached the dependency, and that
     * is what this row put there.
     */
    AnswersStoodIn of(AnswersDemanded demanded, List<FixtureTemplate> inputs) {
        if (!demanded.whole()) {
            // The way asks something of an answer that nothing here states, so a value composed
            // against the rest would be composed against part of what the row has to be.
            return new AnswersStoodIn.NothingComposed(
                    Generator.UnresolvedCombination.Reason.NOTHING_COMPOSES_ONE);
        }
        Map<ValueName.Behavior, Map<InjectedAnswer, List<AnswerDemand>>> asked = byDependency(
                demanded.byAnswer());
        List<StoodInAnswer> out = new ArrayList<>();
        for (RequiredDependencies.Required each : requires.inOrder()) {
            AnswersStoodIn here =
                    standingIn(each, asked.getOrDefault(each.dependency(), Map.of()), inputs);
            if (!(here instanceof AnswersStoodIn.Stood(var answers))) {
                return here;
            }
            out.addAll(answers);
        }
        return new AnswersStoodIn.Stood(List.copyOf(out));
    }

    /**
     * What one dependency is stood in with.
     *
     * <p>A dependency the way asks nothing of is answered for every call: there is no asking to
     * name and no arguments to key a table on, and what such a row needs is something to run
     * against. So is one whose askings all want the same value, for the other reason — one line of
     * source answers them all, and a table of rows that say the same thing is that line repeated.
     */
    private AnswersStoodIn standingIn(RequiredDependencies.Required required,
                                      Map<InjectedAnswer, List<AnswerDemand>> asked,
                                      List<FixtureTemplate> inputs) {
        Map<InjectedAnswer, FixtureTemplate> values = new LinkedHashMap<>();
        for (Map.Entry<InjectedAnswer, List<AnswerDemand>> each : asked.entrySet()) {
            FixtureTemplate value = composed(required, each.getValue());
            if (value == null) {
                return nothingStandsIn();
            }
            values.put(each.getKey(), value);
        }
        FixtureTemplate one = oneValueForAll(required, values);
        if (one != null) {
            return new AnswersStoodIn.Stood(List.of(new StoodInAnswer(required.dependency(),
                    new StoodInAnswer.Asking.ForEveryCall(), one)));
        }
        List<StoodInAnswer> out = new ArrayList<>(values.size());
        Map<List<String>, String> byCall = new LinkedHashMap<>();
        for (Map.Entry<InjectedAnswer, FixtureTemplate> each : values.entrySet()) {
            List<FixtureTemplate> appliedTo = written(each.getKey(), required, inputs);
            if (appliedTo == null) {
                // What the row would have to write at an argument is a value this reading has no
                // way of writing down, so a table cannot be keyed on it — which is the stand-in
                // not being composable rather than anything about the way.
                return nothingStandsIn();
            }
            StoodInAnswer.Asking.OfOne asking =
                    new StoodInAnswer.Asking.OfOne(each.getKey(), appliedTo);
            String already = byCall.putIfAbsent(asking.writtenAs(), each.getValue().text());
            if (already != null) {
                if (!already.equals(each.getValue().text())) {
                    // Two askings this row writes the same arguments at, wanting different
                    // answers. They are two questions the body asks and one call this row makes,
                    // and a table answers by what it was applied to.
                    return new AnswersStoodIn.NothingComposed(
                            Generator.UnresolvedCombination.Reason.TWO_ANSWERS_AT_ONE_CALL);
                }
                // One call, answered alike by both askings. One row of the table, because that is
                // what the table has: a second stating the same arguments is a row its own
                // dispatch would never reach.
                continue;
            }
            out.add(new StoodInAnswer(required.dependency(), asking, each.getValue()));
        }
        return new AnswersStoodIn.Stood(List.copyOf(out));
    }

    private static AnswersStoodIn nothingStandsIn() {
        return new AnswersStoodIn.NothingComposed(
                Generator.UnresolvedCombination.Reason.NOTHING_STANDS_IN_FOR_A_DEPENDENCY);
    }

    /**
     * The value that serves every asking, or null where the askings want different ones.
     *
     * <p>Asked of what the row would write, which is what a {@code with} is. It is not a question
     * about which askings are one — that is {@link InjectedAnswer}'s and was settled where the body
     * was read — but about whether the answers a row states come to one line of source.
     */
    private FixtureTemplate oneValueForAll(RequiredDependencies.Required required,
                                           Map<InjectedAnswer, FixtureTemplate> values) {
        if (values.isEmpty()) {
            return composed(required, List.of());
        }
        FixtureTemplate first = values.values().iterator().next();
        return values.values().stream().allMatch(each -> each.text().equals(first.text()))
                ? first : null;
    }

    /** A value of the dependency's answer meeting {@code demands}, or null where none was composed. */
    private FixtureTemplate composed(RequiredDependencies.Required required,
                                     List<AnswerDemand> demands) {
        AnswerSubjects subjects = standing.get(required.dependency());
        AnswerSubjects.Chosen chosen = subjects == null ? null : subjects.against(demands);
        return chosen != null
                && AnAnswerComposed.of(chosen.standing(), chosen.demands())
                        instanceof AnAnswerComposed.Outcome.Composed(var value) ? value : null;
    }

    /**
     * What the row writes at each argument of {@code asking}, or null where it cannot be written.
     *
     * <p>An argument naming a position of the behavior is the value this row put there, taken from
     * the row rather than composed again — the call a table's row answers for is the one that
     * reached the dependency, and a second value would key it to a call nothing makes. A number the
     * model settles is written as the number.
     *
     * <p>Null where an argument is neither: a position read through fields, whose value is inside
     * something the row wrote rather than beside it, and a number at a position that wears a name.
     * Both are values a row could hold and this reading cannot spell, and a table keyed on a guess
     * answers a call nothing makes.
     */
    private List<FixtureTemplate> written(InjectedAnswer asking,
                                          RequiredDependencies.Required required,
                                          List<FixtureTemplate> inputs) {
        List<BoundaryInput> takes = required.signature().ins();
        if (takes.size() != asking.arguments().size()) {
            return null;
        }
        List<FixtureTemplate> out = new ArrayList<>(takes.size());
        for (int i = 0; i < takes.size(); i++) {
            FixtureTemplate one = switch (asking.arguments().get(i)) {
                case DecisionArgument.OfASubject(DecisionSubject.AnInput(var at))
                        when at.steps().isEmpty() -> at(at.head(), inputs);
                case DecisionArgument.OfANumber(var value) -> number(value, takes.get(i));
                case DecisionArgument.OfASubject _ -> null;
            };
            if (one == null) {
                return null;
            }
            out.add(one);
        }
        return List.copyOf(out);
    }

    /** What this row writes at the position named {@code head}, or null where it writes nothing
     *  there. */
    private FixtureTemplate at(String head, List<FixtureTemplate> inputs) {
        int position = parameters.indexOf(head);
        return position < 0 || position >= inputs.size() ? null : inputs.get(position);
    }

    /**
     * A number the model settles, as the dependency's position takes it.
     *
     * <p>Which literal a number is written as is what the position admits: a whole number and a
     * decimal are written differently and a position wearing a name takes neither on its own. Asked
     * of the position rather than of the value, so that a number written for an {@code Int} is not
     * offered to a position that declares a scale.
     */
    private static FixtureTemplate number(BigDecimal value, BoundaryInput takes) {
        if (!(takes instanceof BoundaryInput.Scalar(LeafScalar scalar))) {
            return null;
        }
        return switch (scalar) {
            case INT -> value.stripTrailingZeros().scale() <= 0
                    ? FixtureTemplate.integer(value.longValueExact()) : null;
            case DECIMAL -> FixtureTemplate.decimal(value);
            case STRING, BOOL, DATE, TIME, DATETIME, INSTANT -> null;
        };
    }

    /** The askings of each dependency, in the order they were first asked about. */
    private static Map<ValueName.Behavior, Map<InjectedAnswer, List<AnswerDemand>>> byDependency(
            Map<InjectedAnswer, List<AnswerDemand>> byAnswer) {
        Map<ValueName.Behavior, Map<InjectedAnswer, List<AnswerDemand>>> out =
                new LinkedHashMap<>();
        byAnswer.forEach((answer, demands) -> out
                .computeIfAbsent(answer.dependency(), _ -> new LinkedHashMap<>())
                .put(answer, demands));
        return out;
    }
}
