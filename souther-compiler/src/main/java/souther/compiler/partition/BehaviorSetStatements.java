package souther.compiler.partition;

import souther.compiler.check.AnalysisBody;
import souther.compiler.check.RuleCitation;
import souther.compiler.check.RuleRef;
import souther.compiler.check.UnreadComparison;
import souther.compiler.core.Core;
import souther.compiler.check.ElementBindings;
import souther.compiler.check.PredicateStatement;
import souther.compiler.check.StatedContract;
import souther.compiler.check.Symbols;
import souther.compiler.check.StringPredicates;
import souther.compiler.check.ValueOrigin;
import souther.compiler.inputs.BlockReason;
import souther.compiler.inputs.FilingCoordinate;
import souther.compiler.inputs.InputReading;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.PathResolution;
import souther.compiler.inputs.StandingQuestion;
import souther.compiler.inputs.TermPath;
import souther.compiler.regex.PatternPlan;
import souther.compiler.values.AdmittedPlan;
import souther.compiler.values.Allowance;
import souther.compiler.values.Realizations;
import souther.compiler.values.Sameness;
import souther.compiler.types.BindingId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.Set;

/**
 * What a behavior's rules about the strings at its positions state of those positions.
 *
 * <p>The one crossing from a plan to a set on this side. What a reading hands on is the rule and the
 * strings it names; what a partition works with is the values on either side of it, and turning the
 * first into the second is making a machine. Done anywhere a reader felt like it, the machine would
 * be built under whatever allowance that reader happened to hold, and the same rule would come to a
 * different set depending on who asked.
 *
 * <p><b>Stating and dividing are two answers.</b> A rule states a set of the position's values and
 * the rest; whether the position holds values on both sides of that is a fact about the position,
 * settled where its values are known ({@link Classing}). So nothing here says a position was
 * divided — what leaves is what the rules state, and every reader of it is one that has the
 * position's own values in hand.
 *
 * <p><b>One rule at a time, and what a position comes to is not asked here.</b> What several rules
 * leave a position between them is a question about every rule that reached it, and the rules of a
 * behavior's body are not all of them — a value singled out by a comparison divides the same
 * position. Answered here, the denominator would be completed by the reader that can see least of
 * it, out of the rules it happened to read.
 *
 * <p><b>The position is still the unit of what is built.</b> Both sides of every rule of a position
 * go to the allowance as one group, so which of them a reader hears about does not follow the order
 * they were walked in ({@link Allowance#realizeAll}).
 *
 * <p><b>Both sides are asked for, and neither is derived from the other.</b> The values a rule does
 * not admit are a plan like the values it does ({@link PatternPlan#notMatching}), so they are built
 * where everything else is and are charged for there. Left to a caller, the complement would be the
 * one expensive operation done outside the arrangement that exists to bound it.
 *
 * <p><b>On its own allowance, and not the one a declaration's answer draws on.</b> What a behavior
 * tells apart is not a projection of what a position admits: a position whose own answer stopped
 * short still has its body's rules read here, and raising what a declaration may build must not make
 * a class appear ({@link AdequacyPolicy.OfTheMeasures#allowanceForBehaviorDistinctions}).
 *
 * <p>And every rule handed in comes back as exactly one outcome ({@link Outcome}), which is a
 * classification and not what a walk had left over: it states a distinction of a position, or one
 * this compiler did not get, or nothing about any position it can name.
 *
 * <p>The last of those leaves without a sentence, and that is the one thing here nobody is told.
 * A rule about a value that came from no position the reading can name has nowhere to be shown —
 * the same answer the reading of a comparison gives the same shape — so what is claimed is that
 * every rule is classified, and not that every rule is reported.
 */
public final class BehaviorSetStatements {

    private BehaviorSetStatements() {
    }

    /**
     * What the rules came to.
     *
     * @param statements what each rule states of a position, as evidence a partition reads like any
     *                   other. Whether it divides the position turns on the values the position
     *                   holds, which is settled where those are known and not here
     * @param blocked the distinctions of a position this compiler did not get, which is what keeps
     *                its classes from being composed out of the ones it did
     * @param nothingClassifies the rules that reached here and whose subject this reading could not
     *                place at a position, each as the question standing where it was filed. Nothing
     *                works out what such a rule states of the values there — reading it back
     *                through the operation that made them is a capability this has not — so what is
     *                undecided is what the rule does at all, which is what the question says
     */
    public record Read(List<RuleEvidence> statements, List<ClassingBlocker> blocked,
                       List<StandingQuestion.NothingClassifiesIt> nothingClassifies,
                       List<ForkOfItsOwn> forks) {

        public Read {
            statements = List.copyOf(statements);
            blocked = List.copyOf(blocked);
            nothingClassifies = List.copyOf(nothingClassifies);
            forks = List.copyOf(forks);
        }
    }

    /**
     * A fork of the model that states a rule of its own, because nothing in what it tests does.
     *
     * <p><b>The fallback owner and not a fourth kind of condition.</b> A condition raises a
     * question about the input, and which rule of the model that question is filed under is settled
     * by whichever reader owns what the condition states: a comparison of the values, the position
     * the condition is, or a predicate over the strings there. Where all of them say nothing, what
     * the author wrote is a fork and the question is the fork's — so this is the answer of last
     * resort rather than a shape recognised on its own.
     *
     * <p>Which means an owner added later takes forks out of this list and needs nothing here
     * changed. A reading that learns to say what {@code List.isEmpty} does to the position it walks
     * makes that fork a rule of whatever reader learned it, and the definition of what belongs here
     * — no owner claimed it — is the same definition it was.
     *
     * @param filed where the reading of what the fork tests got to. A part naming a position is
     *              filed there and not at a number of it: which number of the position such a fork
     *              is about is exactly what was not read
     */
    public record ForkOfItsOwn(RuleCitation cited,
                               SequencedMap<FilingCoordinate,
                                       BlockReason.RuleReadingStopped> filed) {

        public ForkOfItsOwn {
            if (cited == null || filed == null) {
                throw new IllegalArgumentException(
                        "a fork that states a rule is one of the model's, written somewhere,"
                                + " with what went unread of it");
            }
            filed = Collections.unmodifiableSequencedMap(
                    new LinkedHashMap<>(filed));
            if (filed.isEmpty()) {
                // A fork every part of whose condition a reader took in states no rule of its own,
                // and neither does one whose parts name no position of the input. Built with
                // nothing filed, it would be a question about a condition this compiler read from
                // end to end, or one about an input the fork says nothing of.
                throw new IllegalArgumentException(
                        "a fork states a rule about some position it left unread");
            }
        }

        /** Which rule of the model this is. */
        public RuleRef.Fork rule() {
            return (RuleRef.Fork) cited.rule();
        }
    }

    /**
     * The purse {@code term}'s machines are bought from, which is the position on its own.
     *
     * <p>An allowance is per block of positions the model holds one value across, because two
     * positions an equality ties together have one set to build and one purse to build it from. A
     * rule about the strings at a position ties it to nothing — what it states is true of the values
     * standing here and says no word about any other position — so each term is its own block, and
     * two positions that happen to be written with the same predicate pay for their machines apart.
     *
     * <p>Said here once, so that a reader is not left working out from two call sites whether the
     * grouping this stage does is the same grouping the allowance does. It is not: the group here
     * is every rule about one position, and the block is every position that holds one value.
     */
    private static Sameness.Block<NumericTerm.FromOnePosition> purseOf(
            NumericTerm.FromOnePosition term) {
        return Sameness.Block.of(term);
    }

    /** One rule read as far as the plans for its two sides, waiting on its position's group. */
    private record Asked(PredicateOrigin by, PredicateStatement states,
                         NumericTerm.FromOnePosition term,
                         AdmittedPlan whenTrue, AdmittedPlan whenFalse) {}

    /**
     * What {@code read} states, built under {@code allowance}.
     *
     * <p>{@code symbols} and the reading each rule carries are what turn its subject into a
     * position: a rule inside an expanded helper is about the argument the call handed it, so where
     * it stands is asked of the reading that stands there and never of the body as a whole.
     *
     * <p>A behavior writes such a rule in its body and in its {@code ensures}, and both are read
     * here so that what is stated of one position is what the two come to between them. Read apart,
     * a term written about in both places would be measured twice and the second measure would be
     * told nothing of the first's classes.
     */
    public static Read of(String behavior, AnalysisBody body, StatedContract stated,
                          InputReading read,
                          Map<BindingId, String> parameters, ElementBindings elements,
                          Allowance<NumericTerm.FromOnePosition> allowance,
                          List<ComparisonReadings.ForkMet> forks,
                          RuleReachNumbering reaches) {
        return of(behavior,
                PredicateReadings.of(behavior, body, stated, read, parameters, elements, reaches),
                read.symbols(), allowance, forks, reaches);
    }

    /**
     * The same, of a reading already made.
     *
     * <p>Not the way in from outside. Which tree a body's rules are read off is settled here, and a
     * caller given the choice could hand over a reading of the tree a backend emits — where every
     * one of these rules has been expanded into what it does, so the behavior would come back
     * stating nothing about the strings at any of its positions.
     */
    static Read of(String behavior, PredicateReadings read, Symbols symbols,
                   Allowance<NumericTerm.FromOnePosition> allowance,
                   List<ComparisonReadings.ForkMet> forks,
                   RuleReachNumbering reaches) {
        List<Asked> asked = new ArrayList<>();
        List<ClassingBlocker> blocked = new ArrayList<>();
        List<StandingQuestion.NothingClassifiesIt> nothingClassifies = new ArrayList<>();
        for (PredicateReadings.Reading each : read.predicates()) {
            switch (ask(each, symbols)) {
                case Outcome.OfADistinction(Asked it) -> asked.add(it);
                case Outcome.NotGot(var at, var why) ->
                        blocked.add(new ClassingBlocker(at, each.origin(), why));
                // At every place it may be about. A rule this could not place is one sentence, and
                // where the reading could not settle which position it was of, each of the places
                // it may have been of is owed it.
                //
                // As a question and not as a finding. What such a rule states of the values there
                // is what nothing worked out, so a measure that closed over it would be closing
                // over a reading that stopped — while the report went on naming the rule.
                case Outcome.SayingNothing(var at, var why) -> at.forEach(where ->
                        nothingClassifies.add(StandingQuestion.NothingClassifiesIt
                                .of(each.origin().cited(), where, why)));
                // Nothing places it, so there is nobody to say it to. Which is the answer the
                // reading of a comparison gives the same shape, and not this walk being quiet.
                case Outcome.Nowhere _ -> { }
            }
        }
        // Every plan of a position, gathered before anything is built. A set written into two rules
        // is one plan and is charged once, which is what taking them as a group comes to.
        Map<NumericTerm.FromOnePosition, Set<AdmittedPlan>> byTerm = new LinkedHashMap<>();
        for (Asked each : asked) {
            Set<AdmittedPlan> plans =
                    byTerm.computeIfAbsent(each.term(), _ -> new LinkedHashSet<>());
            plans.add(each.whenTrue());
            plans.add(each.whenFalse());
        }
        Map<NumericTerm.FromOnePosition, Realizations> answers = new LinkedHashMap<>();
        byTerm.forEach((term, plans) ->
                answers.put(term, allowance.realizeAll(purseOf(term), plans)));
        List<RuleEvidence> statements = new ArrayList<>();
        for (Asked each : asked) {
            state(each, answers.get(each.term()), statements, blocked);
        }
        return new Read(statements, blocked, nothingClassifies,
                ofTheirOwn(behavior, read, symbols, forks, reaches));
    }

    /**
     * What one rule of a body turned out to be, over the position it is about.
     *
     * <p><b>Named outcomes and not what a walk had left over.</b> Three things can be true of such a
     * rule and they are not one another: it states a distinction of this position, it states one
     * this compiler did not get, or its subject stands at no position this reading can place. Only
     * the second keeps a position's classes from being composed — the first is one of them, and the
     * third is a reading that stopped before it reached a position, so a denominator held open by
     * it would be held open by a rule that never got there.
     *
     * <p>Filled from the branches a reading fell through, the three were one list: everything that
     * did not become a statement kept the position's classes shut.
     */
    private sealed interface Outcome {

        /** A distinction of the position, waiting on its group to be built. */
        record OfADistinction(Asked asked) implements Outcome {}

        /**
         * A distinction of this position that this compiler did not get.
         *
         * <p>The one outcome that holds the position's classes shut. What is missing is one of the
         * distinctions the classes would have been composed from, so a list composed without it is a
         * denominator short of a class the model draws.
         */
        record NotGot(NumericTerm.FromOnePosition at,
                      BlockReason.RuleWithoutLineReason why) implements Outcome {}

        /**
         * A rule of the model whose subject this could not place at a position, and where to say
         * so.
         *
         * <p>A reading that stopped, and the reason says which way. A value an operation made out
         * of what stands somewhere is about that value, and what the rule says about the values it
         * was made from would take reading the operation backwards; a value that is what stands at
         * one of several places is about the input at whichever of them this run is. Neither is a
         * distinction gone missing, and neither is a rule read to the end — so what stands where
         * this is filed is a question, and what a measure there rests on is that nothing worked out
         * what the rule states.
         */
        record SayingNothing(List<FilingCoordinate> at,
                             BlockReason.RuleReadingStopped why) implements Outcome {

            public SayingNothing {
                at = List.copyOf(at);
                if (at.isEmpty()) {
                    throw new IllegalArgumentException(
                            "a rule this could not place is said somewhere: " + why);
                }
            }
        }

        /** And a rule about a value that came from no position the reading can name, which has
         *  nowhere to be said. */
        record Nowhere() implements Outcome {}
    }

    /**
     * What {@code each} turned out to be.
     *
     * <p>Exhaustive with no {@code default}: an outcome the table of predicates learns is one
     * somebody decides about here rather than one that quietly takes its neighbour's answer.
     */
    private static Outcome ask(PredicateReadings.Reading each, Symbols symbols) {
        // Where the rule's subject stands, read where the rule stands.
        PathResolution stands = each.reads().pathOf(each.subject(), symbols);
        if (!(stands instanceof PathResolution.At at)) {
            return saidWithoutADenominator(each, stands, symbols);
        }
        NumericTerm.FromOnePosition term = new NumericTerm.ValueOf(at.path());
        return switch (each.reading()) {
            case StringPredicates.Reading.Accepting it ->
                    new Outcome.OfADistinction(new Asked(each.origin(), each.statement(), term,
                            new AdmittedPlan.Pattern(PatternPlan.of(it.accepts())),
                            new AdmittedPlan.Pattern(PatternPlan.notMatching(it.accepts()))));
            case StringPredicates.Reading.PatternNotRead it ->
                    new Outcome.NotGot(term, BlockReason.forAPatternNotRead(it.why()));
            // A rule whose text this compiler did not work out is a rule it did not read. Said as
            // anything about the values, it would be a distinction reported as absent from the model
            // when what is absent is this compiler's reading of it.
            case StringPredicates.Reading.WrittenArgumentNotKnown _ ->
                    new Outcome.NotGot(term, new BlockReason.UnreadValueRule());
        };
    }

    /**
     * What a rule whose subject stands at no one position comes to, and where to say it.
     *
     * <p>Two ways for that, and they are different sentences. A value an operation made out of what
     * stands somewhere is about that value, and what the rule says about the values at the position
     * it came from would take reading the operation backwards. A value that <em>is</em> what stands
     * somewhere, in a block handed to more than one walk, is about the input at whichever of the
     * places it may stand at this run is — nothing was made out of it and there is no operation to
     * read backwards.
     *
     * <p>Both are said at every place they may be about, and neither holds a position's classes
     * open: what is missing is not a distinction the classes would have been composed from, it is
     * which position the rule was of.
     */
    private static Outcome saidWithoutADenominator(PredicateReadings.Reading each,
                                                   PathResolution stands, Symbols symbols) {
        return switch (stands) {
            case PathResolution.MayStandAt(var among) -> mayStandAt(among);
            case PathResolution.NotAPosition _ -> whereItsValueCameFrom(each, symbols);
            // The caller asks this only where the subject stands at no one place.
            case PathResolution.At at -> throw new IllegalArgumentException(
                    "a rule whose subject stands at " + at.path() + " has a denominator");
        };
    }

    /**
     * The same for a subject that is at no one position, read out of what its value is made of.
     *
     * <p>Asked of {@link ValueOrigin}, which is where what an expression is made of is worked out
     * for every reader of a body, and read off the arms of it that say where a value came from
     * ({@link #positionsItCameFrom}). A subject an operation answered names no position and came
     * from the ones its arguments name; a walk that stopped at the operation reported a model
     * stating nothing where an author wrote a rule.
     *
     * <p>At every position the value came from. {@code String.append(a, b)} is made out of both,
     * and an author who wrote a rule about the joined string is owed the sentence at each — filed
     * at one of them, the other comes back as a position the model says nothing about.
     *
     * <p>A subject that <em>is</em> what stands at more than one place is asked first, and of the
     * reading rather than of what the value is made of. That is the other sentence — nothing was
     * made out of it and there is no operation to read backwards — and what an expression is made
     * of has no way to say it: a value that is one of several places is not one built out of all
     * of them.
     */
    private static Outcome whereItsValueCameFrom(PredicateReadings.Reading each, Symbols symbols) {
        if (each.reads().cameFrom(each.subject(), symbols)
                instanceof PathResolution.MayStandAt(var among)) {
            return mayStandAt(among);
        }
        Set<TermPath> from = positionsItCameFrom(
                GuardThresholds.originOf(each.subject(), each.reads(), symbols));
        // And a rule about a value that came from no position the reading can name, which has
        // nowhere to be said.
        return from.isEmpty() ? new Outcome.Nowhere()
                : new Outcome.SayingNothing(from.stream().map(FilingCoordinate::at).toList(),
                        new BlockReason.RuleAboutADerivedValue());
    }

    /**
     * Every position {@code origin}'s value came from, out of the arms that say where a value came
     * from.
     *
     * <p>Read here and not asked of what an expression is made of as a whole, because that answer
     * does not hold the question. What is made out of several things and what is one of several
     * things arrive in one arm: the parts of a choice are the values it chooses between and what it
     * turns on, side by side. A union over that arm files a rule at what decided which value the
     * subject is, and a reader sent there is sent to a position the rule says nothing about.
     *
     * <p>So the arms that do state where a value came from are read, and the one that does not is
     * left. What that costs is a rule under a choice shown nowhere, which is what such a rule comes
     * to already; what reading it would cost is a rule shown at the wrong position, which is worse
     * and is the thing an author cannot tell from a rule their model states.
     *
     * <p>A switch with no default, so an arm added to what an expression is made of is one somebody
     * says the provenance of before this compiles.
     */
    private static Set<TermPath> positionsItCameFrom(ValueOrigin<TermPath> origin) {
        return switch (origin) {
            case ValueOrigin.IsAPosition<TermPath> it -> Set.of(it.at());
            case ValueOrigin.MadeFromAPosition<TermPath> it -> Set.of(it.at());
            // What an operation answered came from whatever its arguments came from, each of them:
            // a string joined out of two positions is made out of both, and an author who wrote a
            // rule about the joined value is owed the sentence at each.
            case ValueOrigin.Applied<TermPath> it -> across(it.arguments());
            // A value written where it stands came from no position, and one nothing here can name
            // came from none this can name.
            case ValueOrigin.Written<TermPath> _, ValueOrigin.Unnameable<TermPath> _ -> Set.of();
            case ValueOrigin.Composed<TermPath> _ -> Set.of();
        };
    }

    /** The positions everything in {@code of} came from, in the order they were met. */
    private static Set<TermPath> across(List<ValueOrigin<TermPath>> of) {
        Set<TermPath> out = new LinkedHashSet<>();
        for (ValueOrigin<TermPath> each : of) {
            out.addAll(positionsItCameFrom(each));
        }
        return out;
    }

    /** A rule about a value that is what stands at one of {@code among}, said at each of them. */
    private static Outcome mayStandAt(List<TermPath> among) {
        return new Outcome.SayingNothing(among.stream().map(FilingCoordinate::at).toList(),
                new BlockReason.RuleAboutAnElementOfSeveralSequences());
    }

    /**
     * One rule as the statement it came to, out of what its position's group was built to.
     *
     * <p>Whether the two sides hold anything is not asked here. What a rule leaves is a set of every
     * string there is, and what the position holds is what its declarations left it — so the two
     * sides of a rule can be inhabited among the strings and one of them empty at the position. That
     * is a question about the position's values and is asked where they are known.
     */
    private static void state(Asked each, Realizations answer,
                              List<RuleEvidence> statements, List<ClassingBlocker> blocked) {
        if (!(answer instanceof Realizations.Exact built)) {
            blocked.add(new ClassingBlocker(each.term(), each.by(),
                    new BlockReason.BehaviorDistinctionsTooCostly()));
            return;
        }
        statements.add(new RuleEvidence.BySet(new SetStatement(each.term(),
                built.of(each.whenTrue()), built.of(each.whenFalse()), each.states(), each.by())));
    }

    /**
     * The forks among {@code forks} that state a rule of their own.
     *
     * <p>Where the three readers of a condition meet. Two of the answers were found by the walk of
     * the comparisons — whether a comparison came out of the condition, and whether the condition
     * is a position — and the third is this reading's, which is the reason the join is here: a
     * predicate is what this walk reads, and a reader that asked it from outside would be reading
     * the body a second time to find out.
     *
     * <p>Said as no owner claiming it rather than as a shape. {@link ForkOfItsOwn} says why.
     *
     * <p><b>And a fork among the owners, once the rules are known.</b> An operation may say its
     * answer turns on what a closure decided, and what the closure decided may be a fork of its own
     * — so the fork around the operation states that rule rather than a second one, exactly as it
     * states a comparison written there. Which fork is a rule is what this works out, so it is
     * asked here and of the answer rather than of the source: a condition nobody answers for that
     * is about no position of the input states nothing, and an outer fork owned by it would be
     * owned by a rule nobody wrote and its own question would go with it.
     *
     * <p><b>Taken part by part, as every other owner is.</b> A fork owned for one part of its
     * condition still states the other one, and a fork rule owns what it decides rather than the
     * fork around it: {@code List.any(closure, xs) && List.isEmpty(ys)} states the closure's rule
     * at the first part and something nobody read at the second, and dropping the whole fork for
     * the first would take the second's question with it.
     *
     * <p>Over the rules found rather than over the forks met on the way, so where the two stand
     * relative to each other says nothing: a walk records a fork before it descends its arms, and
     * an owner looked up among the forks already met would leave one written under the arm of the
     * other owning nothing.
     *
     * <p>And once, rather than until it settles. Taking a part away can leave a fork stating
     * nothing, and a part that turned on that fork's condition is not left without an owner by it:
     * the fork stated nothing because its own parts turn on some other rule's condition, and a walk
     * that reached the first condition goes on through it to the second along the same edges.
     */
    private static List<ForkOfItsOwn> ofTheirOwn(String behavior, PredicateReadings read,
                                                 Symbols symbols,
                                                 List<ComparisonReadings.ForkMet> forks,
                                                 RuleReachNumbering reaches) {
        List<Standing> standing = standingRules(behavior, read, symbols, forks, reaches);
        List<ForkOfItsOwn> out = new ArrayList<>();
        for (Standing each : standing) {
            List<Unread> left = new ArrayList<>();
            for (Unread was : each.unread()) {
                List<Core> parts = was.parts().stream()
                        .filter(part -> standing.stream().noneMatch(other -> other != each
                                && other.states(part)))
                        .toList();
                if (!parts.isEmpty()) {
                    left.add(new Unread(was.atom(), parts));
                }
            }
            if (left.isEmpty()) {
                continue;
            }
            ForkOfItsOwn asked = asked(behavior, each.fork(), left, symbols, reaches);
            if (asked != null) {
                out.add(asked);
            }
        }
        return out;
    }

    /** One part of a condition that no reader answers for, and the parts of what it decides that
     *  the reading was left with. */
    private record Unread(Core atom, List<Core> parts) {}

    /** One fork that states a rule of its own, before any of its parts is asked whether another
     *  one's rule is what it states. */
    private record Standing(ComparisonReadings.ForkMet fork, List<Unread> unread,
                            ForkOfItsOwn rule) {

        /**
         * Whether the rule this fork states is what {@code part} decides.
         *
         * <p>Asked of the part, so that a fork owned for one part of what it decides still states
         * another: an owner taken for the whole would carry off the parts nobody claimed, which is
         * the same partial ownership every other reader is asked at.
         *
         * <p><b>Of what this fork's own condition is written out of, and not of what it turns
         * on.</b> A fork whose answer turns on another's condition holds that condition among its
         * parts as much as the fork it was written in does, and a reader that took either for the
         * owner would have the two owning each other — both would go, and the rule written in the
         * first would go with them. What tells them apart is which of the two the source wrote it
         * inside, which is the condition it is a part of.
         */
        boolean states(Core part) {
            for (Unread each : unread) {
                if (each.atom() == part) {
                    return true;
                }
            }
            return false;
        }
    }

    private static List<Standing> standingRules(String behavior, PredicateReadings read,
                                                Symbols symbols,
                                                List<ComparisonReadings.ForkMet> forks,
                                                RuleReachNumbering reaches) {
        List<Standing> out = new ArrayList<>();
        for (ComparisonReadings.ForkMet each : forks) {
            // The parts of what it tests that no reader answers for. Asked part by part and not of
            // the fork: `a > 0 && List.isEmpty(xs)` states a comparison and something nothing read,
            // and a fork answered for by one owner having claimed one part would leave the other
            // part unsaid — a model reported as fully read over a condition half of which nobody
            // took in.
            List<Unread> unread = new ArrayList<>();
            for (Core atom : each.leftHere()) {
                List<Core> parts =
                        ComparisonReadings.leftUnread(atom, read, each.reads(), symbols);
                if (!parts.isEmpty()) {
                    unread.add(new Unread(atom, parts));
                }
            }
            if (unread.isEmpty()) {
                continue;
            }
            ForkOfItsOwn asked = asked(behavior, each, unread, symbols, reaches);
            if (asked != null) {
                out.add(new Standing(each, unread, asked));
            }
        }
        return out;
    }

    /**
     * One fork's question: why each part of it went unread, and where a reading got to.
     *
     * <p>Both answers come from the reading of what the part is made of
     * ({@link GuardThresholds#namesIn}), which is the same reading a comparison's stop is described
     * by. Written apart, a fork on {@code List.isEmpty(xs)} and a comparison against what the same
     * call answers would be two accounts of one shape, and an author reading them would be sent
     * after two different pieces of work.
     *
     * <p>Filed at the positions the walk met inside the part and never at a number of one. Which
     * number of the position such a fork is about is precisely what was not read, and a term made
     * up for it would be this compiler naming an operation the model never wrote
     * ({@link FilingCoordinate.AtPosition}).
     *
     * <p><b>Null where no part of it names a position of the input.</b> Such a fork is not a rule
     * this compiler failed to read: it is a rule about something the input has no part in —
     * {@code List.isEmpty([1, 2, 3])} says nothing about any position, so there is no position for
     * a question to be about. The same threshold a comparison is held to, decided by the same
     * reading ({@link ComparisonAssessment.NoInput}), so a fork and a comparison over one shape do
     * not disagree about whether the model states anything.
     *
     * <p>Which is not a question disappearing for want of a coordinate. What decides it is the
     * subject — whether the rule is about the input at all — and never how far the reading got: a
     * part that does name a position is filed there, however little else was worked out about it.
     */
    private static ForkOfItsOwn asked(String behavior, ComparisonReadings.ForkMet fork,
                                      List<Unread> unread, Symbols symbols,
                                      RuleReachNumbering reaches) {
        SequencedMap<FilingCoordinate, BlockReason.RuleReadingStopped> filed =
                new LinkedHashMap<>();
        for (Unread each : unread) {
            // Where the unread question is, which is the parts of what the atom decides that
            // nobody answers for. A reader sent to the whole atom would be sent to the positions
            // an owned part is about as well — a comparison beside it inside one closure — for a
            // question that comparison already asks.
            //
            // And the atom itself where those parts are about nothing of the input. What such a
            // fork turns on is still what the atom reaches: a closure that says nothing about the
            // element leaves the fork turning on the sequence it walks, and that is where a reader
            // is owed the question.
            List<Core> places = each.parts().stream()
                    .anyMatch(one -> namesSomething(one, fork, symbols))
                    ? each.parts() : List.of(each.atom());
            for (Core part : places) {
            // Where the part stands at places this could not choose between, those are the places,
            // and they are what the walk below cannot give: it reads a part as one term over the
            // positions it names, and a term over a place nothing settled is a term at whichever
            // place a reader picked. Asked first, so a fork over such a part is filed at each of
            // them rather than at none — which is where the rule the author wrote would go.
            if (fork.reads().pathOf(part, symbols)
                    instanceof PathResolution.MayStandAt(var among)) {
                among.forEach(at -> filed.putIfAbsent(FilingCoordinate.at(at),
                        new BlockReason.RuleAboutAnElementOfSeveralSequences()));
                continue;
            }
            GuardThresholds.Names names = GuardThresholds.namesIn(part, fork.reads(), symbols);
            BlockReason.RuleReadingStopped why =
                    UnreadComparison.notAboutOwnValues(names.origin());
            names.met().keySet().forEach(at -> filed.putIfAbsent(FilingCoordinate.at(at), why));
            }
        }
        return filed.isEmpty() ? null : new ForkOfItsOwn(new RuleCitation.Written(
                new RuleRef.Fork(behavior, fork.occurrence().origin()),
                reaches.anchorOf(fork.occurrence().origin(), fork.at())), filed);
    }

    /** Whether {@code part} names a position of the input, however the reading gets there. */
    private static boolean namesSomething(Core part, ComparisonReadings.ForkMet fork,
                                          Symbols symbols) {
        return fork.reads().pathOf(part, symbols) instanceof PathResolution.MayStandAt
                || !GuardThresholds.namesIn(part, fork.reads(), symbols).met().isEmpty();
    }
}
