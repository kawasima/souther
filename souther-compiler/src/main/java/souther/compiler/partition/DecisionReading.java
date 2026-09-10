package souther.compiler.partition;

import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.Symbols;
import souther.compiler.core.Core;
import souther.compiler.inputs.InputDomain;
import souther.compiler.inputs.InputReading;
import souther.compiler.inputs.InputReads;
import souther.compiler.inputs.TermPath;
import souther.compiler.semantics.ConditionJoin;
import souther.compiler.types.ModelOccurrence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The rules of the decision one body states.
 *
 * <p>The paths through the body, each carrying the conditions it consulted. Read straight off the
 * evaluation and never assembled from a table of every assignment: what makes a condition absent
 * from a rule is the path having settled before reaching it, so the short-circuit is the rule and
 * there is nothing left to fold afterwards.
 *
 * <p><b>The grammar is {@link Condition}'s and the fold is not.</b> {@link ReachingCuts#stating}
 * answers what may safely narrow a search, and to stay safe it gives up whole facts: {@code A && B}
 * coming out false says one of them failed and names neither. A rule set that took that answer
 * would lose the distinction between {@code A} failing and {@code B} failing on the first
 * conjunction, which is the one distinction this reading exists to keep.
 *
 * <p><b>Two paths asking the same distinctions are one rule.</b> A distinction met twice on one
 * path contributes one column, and where the two readings of it disagree the path assumes a
 * proposition both holds and does not — which is a contradiction and not a rule. Those are shown
 * impossible rather than left unsettled, which is the one thing a reading may say about a path
 * nothing can stand in.
 *
 * <p>Nothing here says a rule can be reached. A vector says what a row would have to satisfy;
 * whether anything stands there is settled against the rows and a stated search, and is no part of
 * reading the body.
 *
 * @param behavior     whose decision this is
 * @param found        the rules, in the order the walk took the paths, each with what a run down
 *                     its path would be seen doing
 * @param shownImpossible paths whose conditions contradict each other, which are not rules and are
 *                        not owed anything
 * @param enumeration  whether every path was taken
 */
public record DecisionReading(String behavior, List<Ruled> found, int shownImpossible,
                              Enumeration enumeration) {

    /** The figure this walk stops at, read here rather than written here. */
    private static final CompositionBudget PATHS_READ = CompositionBudget.PATHS_OF_A_DECISION_READ;

    public DecisionReading {
        found = List.copyOf(found);
    }

    /**
     * One rule, and where a run that took its path would be recorded.
     *
     * <p>The two apart because a rule is told apart by the distinctions it consulted and not by
     * where they are written. Held inside the rule, one body stating one rule in two places would
     * state two.
     *
     * @param shownBy every condition the path consulted, in the order it met them, said as what a
     *                run through it would be seen doing. A condition met twice is one column and
     *                two things to be seen, since a run down the path passes both
     */
    public record Ruled(DecisionRule rule, List<ShownBy> shownBy) {

        public Ruled {
            shownBy = List.copyOf(shownBy);
        }

        /** Whether every condition on the path is one a run through can be recognised. */
        public boolean everyConditionIsRecorded() {
            return shownBy.stream().noneMatch(ShownBy.NothingIsRecorded.class::isInstance);
        }
    }

    /** The rules themselves, for a reader that asks what the body decides and not where. */
    public List<DecisionRule> rules() {
        return found.stream().map(Ruled::rule).toList();
    }

    /** Whether the walk took every path of the body, or stopped short of some. */
    public sealed interface Enumeration {

        /** Every path of the body was taken. */
        record Complete() implements Enumeration {}

        /**
         * The walk stopped at a figure this compiler holds itself to, so the rules are some of the
         * body's and the ones it did not reach are neither covered nor gaps.
         *
         * @param figure which figure it reached, so that a reader is told what to raise
         * @param after  how many paths were read before it stopped
         */
        record StoppedAtAFigure(CompositionBudget figure, int after) implements Enumeration {}
    }

    /**
     * The decision {@code body} states, read under {@code reads}.
     *
     * <p>Whose body it is is asked for, because a rule is reported as a rule of a behavior and a
     * condition takes its name from the reading of one.
     */
    public static DecisionReading of(String behavior, Core body, InputReading read,
                                     InputReads reads) {
        // The conditions of this body take their names here, and this reading is the one counting
        // them. A register handed in from another reading would number what that walk met, which is
        // the same conditions under an order this walk does not take.
        ConditionNumbering numbering = new ConditionNumbering(read.symbols().module(), behavior);
        Walk walk = new Walk(read, LiveFlow.of(body), numbering);
        List<List<Consulted>> ways = walk.through(body, reads);
        List<Ruled> rules = new ArrayList<>();
        int contradictory = 0;
        for (List<Consulted> way : ways) {
            Map<DecisionCondition, DecidedCondition> vector = vectorOf(way);
            if (vector == null) {
                contradictory++;
            } else {
                rules.add(new Ruled(new DecisionRule(vector),
                        way.stream().map(Consulted::shown).toList()));
            }
        }
        return new DecisionReading(behavior, rules, contradictory,
                walk.stopped
                        ? new Enumeration.StoppedAtAFigure(PATHS_READ, walk.taken)
                        : new Enumeration.Complete());
    }

    /**
     * The vector one path comes to, or null where the path assumes a proposition both ways.
     *
     * <p>Where one distinction is met twice with one answer, the two readings are one column: a
     * body asking the same thing twice states one distinction, and a column apiece would admit an
     * assignment where it holds and does not.
     */
    private static Map<DecisionCondition, DecidedCondition> vectorOf(List<Consulted> way) {
        Map<DecisionCondition, DecidedCondition> vector = new LinkedHashMap<>();
        for (Consulted each : way) {
            DecidedCondition already = vector.putIfAbsent(each.answer().condition(), each.answer());
            if (already != null && !already.equals(each.answer())) {
                return null;
            }
        }
        return vector;
    }

    /**
     * One condition a path consulted: what it came out as, and where a run through it is seen.
     *
     * <p>The walk's own value and no reader's. What a rule is made of is the first half and what
     * joins it to a run is the second, and they are collected together because they are one act —
     * recognising a condition is where both are known.
     */
    private record Consulted(DecidedCondition answer, ShownBy shown) {}

    /**
     * The walk, and what is the same at every step of it.
     *
     * <p>A way through an expression is a list of the conditions consulted on it, so one way
     * consulting nothing is the empty list and an expression with no fork in it has exactly that
     * one way. Never no ways at all: an expression nothing can be read of still runs, and a
     * reading that answered with nothing would erase every path through whatever encloses it.
     */
    private static final class Walk {

        private final InputReading read;
        private final LiveFlow flow;
        private final ConditionNumbering numbering;
        private final Map<Site, List<List<Consulted>>> walked = new HashMap<>();
        private int taken;
        private boolean stopped;

        private Walk(InputReading read, LiveFlow flow, ConditionNumbering numbering) {
            this.read = read;
            this.flow = flow;
            this.numbering = numbering;
        }

        private Symbols symbols() {
            return read.symbols();
        }

        private RuleReadingSource rules() {
            return read.rules();
        }

        private InputDomain inputs() {
            return read.domain();
        }

        /**
         * The ways through {@code e}, in evaluation order.
         *
         * <p>A short-circuit operator is not one of the shapes. Standing where a value is wanted it
         * computes rather than decides, and the decision is where that value is consulted — read as
         * a fork here as well, one distinction would be a column in the condition it is read as and
         * a path of its own beside it, and the two would cross into rules no row can be written at.
         *
         * <p><b>Once per subtree, however many ways reach it.</b> A subtree stands under a product
         * of the ways into it, and walking it inside that product would read its conditions once
         * per way — which is the reading meeting one condition several times and naming it several
         * times. The ways through a subtree do not depend on how it was arrived at, so they are
         * asked for and filed.
         */
        private List<List<Consulted>> through(Core e, InputReads reads) {
            Site site = new Site(e, reads);
            List<List<Consulted>> already = walked.get(site);
            if (already != null) {
                return already;
            }
            List<List<Consulted>> made = waysOut(e, reads);
            walked.put(site, made);
            return made;
        }

        /** One subtree under the names in force at it, which is what the ways through it are of. */
        private record Site(Core node, InputReads reads) {

            @Override
            public boolean equals(Object other) {
                return other instanceof Site that && node == that.node && reads.equals(that.reads);
            }

            @Override
            public int hashCode() {
                return System.identityHashCode(node) * 31 + reads.hashCode();
            }
        }

        private List<List<Consulted>> waysOut(Core e, InputReads reads) {
            return switch (e) {
                case Core.If iff -> {
                    List<List<Consulted>> out = new ArrayList<>();
                    Condition condition = Condition.of(iff.cond(), reads, symbols(), numbering);
                    for (boolean holding : new boolean[] {true, false}) {
                        // The arm walked once, whatever the condition's ways of coming out this
                        // way. A subtree walked once per way is a subtree whose own conditions are
                        // met once per way, and a condition is met once however many paths reach
                        // it.
                        List<List<Consulted>> after =
                                through(holding ? iff.then() : iff.els(), reads);
                        for (List<Consulted> way : waysThrough(condition, holding)) {
                            for (List<Consulted> rest : after) {
                                add(out, and(way, rest));
                            }
                        }
                    }
                    yield out;
                }
                case Core.Match match -> {
                    List<List<Consulted>> out = new ArrayList<>();
                    List<List<Consulted>> before = through(match.scrutinee(), reads);
                    for (int part = 0; part < match.cases().size(); part++) {
                        Core.Case arm = match.cases().get(part);
                        // Asked once per arm, however many ways lead to the fork. Reaching this arm
                        // is one thing the fork says, and asking again for each way in would be the
                        // reading naming one condition once per path that meets it.
                        Consulted selected = selecting(match, arm, part, reads);
                        InputReads inside = reads.insideArm(match, arm, symbols());
                        List<List<Consulted>> after = through(arm.body(), inside);
                        for (List<Consulted> way : before) {
                            for (List<Consulted> rest : after) {
                                add(out, and(and(way, selected), rest));
                            }
                        }
                    }
                    yield out;
                }
                // A value nothing reads decides nothing about the answer, so a fork inside it
                // divides no row — the rule a comparison there draws no line under.
                case Core.LetIn let -> {
                    List<List<Consulted>> out = new ArrayList<>();
                    List<List<Consulted>> value = flow.reads(let)
                            ? through(let.value(), reads) : List.of(List.of());
                    for (List<Consulted> first : value) {
                        for (List<Consulted> body
                                : through(let.body(), reads.and(let.binder(), let.value()))) {
                            add(out, and(first, body));
                        }
                    }
                    yield out;
                }
                default -> {
                    List<Core> children = new ArrayList<>();
                    Core.forEachChild(e, children::add);
                    List<List<Consulted>> out = new ArrayList<>();
                    out.add(List.of());
                    for (Core child : children) {
                        List<List<Consulted>> longer = new ArrayList<>();
                        for (List<Consulted> so : out) {
                            for (List<Consulted> more : through(child, reads)) {
                                add(longer, and(so, more));
                            }
                        }
                        out = longer;
                    }
                    yield out;
                }
            };
        }

        /**
         * The ways {@code node} comes out {@code holding}, each carrying what it consulted to get
         * there.
         *
         * <p>Where the connective gives both halves, one way is a way through each half. Where it
         * gives either, the left settling it is one way and the left leaving it unsettled followed
         * by the right settling it is another — which is what makes {@code A} failing and
         * {@code B} failing two rules rather than one answer naming neither.
         */
        private List<List<Consulted>> waysThrough(Condition node, boolean holding) {
            if (!(node instanceof Condition.Joined joined)) {
                return List.of(List.of(decided(node, holding)));
            }
            List<List<Consulted>> out = new ArrayList<>();
            if (joined.how().under(holding) == ConditionJoin.BOTH) {
                for (List<Consulted> left : waysThrough(joined.left(), holding)) {
                    for (List<Consulted> right : waysThrough(joined.right(), holding)) {
                        add(out, and(left, right));
                    }
                }
                return out;
            }
            // The left settling it, which is the left coming out the way the whole node did.
            for (List<Consulted> left : waysThrough(joined.left(), holding)) {
                add(out, left);
            }
            for (List<Consulted> left : waysThrough(joined.left(), !holding)) {
                for (List<Consulted> right : waysThrough(joined.right(), holding)) {
                    add(out, and(left, right));
                }
            }
            return out;
        }

        /**
         * What one condition of a boolean subtree coming out {@code holding} contributes.
         *
         * <p>Asked of {@link ReachingCuts#stating}, which is where a comparison is turned into what
         * it states and is the reading every other reader of one takes. A shape below the joins is
         * one condition, so what comes back is one answer.
         */
        private Consulted decided(Condition node, boolean holding) {
            DecidedCondition answer =
                    read(ReachingCuts.stating(node, inputs(), holding, rules()).get(0), holding);
            // Where a run through it is recorded is the comparison's own construct of the model,
            // which is what the tree the rules are read off and the tree that runs agree about. A
            // condition of any other shape has none to be seen at, and says so.
            ModelOccurrence states = node instanceof Condition.Compares one
                    ? one.states().orElse(null) : null;
            return new Consulted(answer, states == null
                    ? new ShownBy.NothingIsRecorded(answer.condition())
                    : new ShownBy.AtAComparison(states, holding));
        }

        /** What entering {@code arm} of {@code match} says the scrutinee turned out to be. */
        private Consulted selecting(Core.Match match, Core.Case arm, int part, InputReads reads) {
            DecidedCondition answer = read(ReachingCuts.entering(match, arm, part, inputs(), reads,
                    rules(), numbering), true);
            ModelOccurrence fork =
                    ModelOccurrence.statedAt(match.place().occurrence()).orElse(null);
            return new Consulted(answer, fork == null
                    ? new ShownBy.NothingIsRecorded(answer.condition())
                    : new ShownBy.AtAnArm(fork, part));
        }

        /**
         * One thing on the way, read as a column and an answer about it.
         *
         * <p>The polarity comes out of the column rather than being carried beside it: a cut is
         * held under the relation the walk gave it, and a column is one of the two relations over
         * one form, so which of them the walk arrived at is what the path answered.
         */
        private DecidedCondition read(OnTheWay one, boolean holding) {
            return switch (one) {
                case OnTheWay.TakenIn taken -> {
                    var proposition = DecisionCondition.AComparison.canonical(taken.cut().rel());
                    yield new DecidedCondition.Compared(
                            new DecisionCondition.AComparison(taken.cut().form(), proposition),
                            taken.cut().rel() == proposition);
                }
                case OnTheWay.Narrowed narrowed -> {
                    TermPath at = narrowed.position();
                    yield new DecidedCondition.Narrowed(
                            new DecisionCondition.APosition(at.narrowedFrom()), at.narrowing());
                }
                case OnTheWay.Declined declined -> new DecidedCondition.Unread(
                        new DecisionCondition.AConditionNotRead(
                                declined.condition(), declined.why()), holding);
            };
        }

        /**
         * One more way, unless the figure this walk was given is spent.
         *
         * <p>Counted over the walk and not over the list being built. A body forks in more places
         * than one, and a figure spent per list would let the product of two lists each just under
         * it run to the square of the figure.
         */
        private void add(List<List<Consulted>> out, List<Consulted> way) {
            if (taken >= PATHS_READ.maximum()) {
                stopped = true;
                return;
            }
            taken++;
            out.add(way);
        }

        private static List<Consulted> and(List<Consulted> way,
                                                  List<Consulted> more) {
            List<Consulted> out = new ArrayList<>(way);
            out.addAll(more);
            return List.copyOf(out);
        }

        private static List<Consulted> and(List<Consulted> way, Consulted more) {
            return and(way, List.of(more));
        }
    }
}
