package souther.compiler.inputs;

import souther.compiler.observe.RunSensitivity;
import souther.compiler.regex.PatternRead;
import souther.compiler.values.UnreadReason;

/**
 * Why a derivation did not finish, in this compiler's own terms.
 *
 * <p>Not {@link UndividedPosition.Reason}. That one is the vocabulary an adequacy document writes,
 * which is a promise to whoever reads the document about which cases they can tell apart; this one
 * is a record of what this compiler could not do. The two are deliberately not the same set, and
 * keeping them the same type is what froze this one: a reason could not be made more precise
 * without widening a published vocabulary, so the pressure was always back towards a word coarse
 * enough to already exist.
 *
 * <p>Split apart, the two move at their own speeds. A capability gained here removes a case here
 * and need not remove a word out there, which other cases may still be reaching; and a distinction
 * worth recording here need not be a distinction the document promises.
 *
 * <p>This knows nothing about how it is reported. Which word a document writes for one of these is
 * {@link ReportedReason}'s, so a second surface may say more of the same reason without this having
 * an opinion, and so the direction stays one way: a published vocabulary does not reach back into
 * what this compiler is allowed to know.
 */
public sealed interface BlockReason {

    /**
     * This compiler fell short of something, whatever it was short of.
     *
     * <p>Two questions and not one tree. "Is there a rule to name?" and "did this compiler get to
     * the end of what it was reading?" are asked by different callers about the same set of
     * reasons, and the answers cut across each other: a rule this got partway through is both a
     * rule with no line and a shortfall, a rule read from end to end that divides nothing is the
     * first and not the second, and a position whose rules were never reached is the second and not
     * the first. Written as one hierarchy, whichever question the tree was shaped by, the other one
     * has to be asked by reading the reason and deciding — and a caller that decides wrong cannot
     * be stopped by anything.
     *
     * <p>So the two are capabilities and a reason implements what is true of it. A parameter typed
     * here takes exactly the reasons a reading that stopped can produce, and
     * {@link RuleWithoutLineReason} takes exactly the ones that leave a rule without a line. What
     * used to be a comment saying which reasons a caller must not be handed is now the parameter
     * type.
     */
    sealed interface ReadingStopReason extends BlockReason {

        /**
         * Whether a run of this compiler that allows more could get past this.
         *
         * <p>Asked of a stop and of nothing else. These are exactly the reasons a measurement can
         * be left open by, and the ones that are not among them were read from end to end — they
         * leave no measure short of anything and so have nothing here to be asked about. A reason
         * answering for a measure it does not weaken is an answer a report could reach for.
         *
         * <p>The question is the allowances and never the person: did a figure this compiler
         * compared something against stop it. What an author or an operator may go on to do is what
         * the reasons themselves say, one word at a time.
         */
        RunSensitivity runSensitivity();
    }

    /**
     * A reading that ran to the end of the rules and could not work out what they leave.
     *
     * <p>The third kind of stop, beside a rule this could not use and a position whose rules it
     * never reached. Every rule arrived and every one of them was understood; what was not worked
     * out is the answer they come to between them, and no rule is answerable for that — two that
     * are cheap apart can have an answer that is not.
     *
     * <p>Its own capability because what a caller may do with it differs. One of these carries no
     * rule and never will: a reader asking which rule to name gets nothing, and that is the fact
     * rather than a gap. Put among the reasons that do name one, every such reader would have to
     * remember which arms are the exception, and the day one forgot it would name a rule that
     * nothing was wrong with.
     */
    sealed interface AnswerRealizationStopped extends QuestionStandingReason {

        /** All three are an allowance a reading was granted running out, which is a figure a run
         *  may allow more of. */
        @Override
        default RunSensitivity runSensitivity() {
            return switch (this) {
                case ExactValuesTooCostly _, RulesNotHandedOnAsSets _,
                     BehaviorDistinctionsTooCostly _ -> RunSensitivity.MAY_CHANGE;
            };
        }
    }

    /**
     * A rule of the model that is no line at a position it is about, however that came about.
     *
     * <p>Whose rule it is, is the producer's to carry. What this says is that there is one: a
     * comparison was written, a reader took it apart, and what came of it is a fact about that rule
     * — either about how far this compiler got with it, or about what the rule itself places. So a
     * finding built on one of these owes an identity for the rule, and the type is what makes owing
     * it unavoidable.
     *
     * <p><b>Named for what is true of every one of them, and not for either cause.</b> The two
     * halves under this are opposite sentences about this compiler, and a name taken from one of
     * them made the other unsayable: carried as rules this could not read, a rule read from end to
     * end went out under a word meaning nobody had managed to read it. What they share is the
     * observable predicate — this rule has no line here — and that is what the name says.
     */
    sealed interface RuleWithoutLineReason extends BlockReason {
    }

    /**
     * A rule with no line here where this compiler is what fell short, whether or not the shortfall
     * is about the rule.
     *
     * <p>The half of {@link RuleWithoutLineReason} that is a stop, beside {@link
     * ReadToEndWithoutLine}, which is the half that is not. What a caller asks this for is whether
     * anything is outstanding at the place a finding sits — a rule read to the end says the model
     * states something, and one of these says nobody knows yet.
     *
     * <p>Wider than {@link RuleReadingStopped} by exactly what does not name a rule. A rule whose
     * own reading stopped is answerable as that rule; a rule whose position could not hand its sets
     * on is not, and both leave the same thing outstanding here. Held as the narrower type, a
     * shortfall no rule is answerable for could only be carried by pretending one is.
     */
    sealed interface StoppedWithoutALine extends RuleWithoutLineReason, ReadingStopReason {
    }

    /**
     * A shortfall that leaves a question a rule raised standing.
     *
     * <p>The third capability, and it cuts across the other two the way they cut across each other.
     * A rule a reading gave up on is one of these and is also a rule with no line; an answer the
     * rules come to that was not built is one of these and is neither of those — it names no rule,
     * and no reading drew a line it could be the absence of.
     *
     * <p><b>Two ways for one question to stand, and both may hold of it at once.</b> The rule the
     * question is of may be one a reading gave up on, and the answer its position finally admits
     * may be one nothing built. The two are recorded in different places — the first under the
     * rule, the second at the position, because no rule is answerable for it — and a rule with a
     * conjunct nothing reads beside a choice whose meet ran past the allowance stands on both. A
     * caller handed one of them where two hold is told to rewrite a form, and the position stays as
     * wide as it was for a reason nothing said.
     *
     * <p>What is promised here is that a reading fell short, and not that there is a rule to name.
     * {@link souther.compiler.inputs.StandingQuestion} carries the rule it is of already: which
     * rule raised the question and what stopped the answer are two facts, and a type that made the
     * second promise the first is what sent an answer-level limit out as a rule nobody interpreted.
     *
     * <p>Not every stop. A position whose rules were never reached raises no question at all, so
     * {@link AboutThePosition} is the half of {@link ReadingStopReason} that is not this.
     */
    sealed interface QuestionStandingReason extends ReadingStopReason {
    }

    /**
     * A rule a reading stopped on, which is the half of {@link RuleWithoutLineReason} that says
     * this compiler fell short.
     *
     * <p>Told apart by the type because the two halves are opposite sentences and were one set. A
     * reading that stopped leaves whatever the rule states unknown; a rule read to the end that
     * divides no one position states what it states, and nothing is missing. Held alike, a rule
     * about a pair of positions the carrier could not be read for was described as a rule that
     * relates two positions — which is true of it, and says nothing fell short, and something did.
     *
     * <p>Which is why a reading's own answer for having stopped may only be one of these. What such
     * a rule would have raised is exactly the part that was not read, so an obligation cannot be
     * built from it — and a rule filed under one of these travels as
     * {@link StandingQuestion.Unclassified}, which says which question nothing worked out and names
     * no subject for it.
     *
     * <p><b>The one reason in all three capabilities.</b> A rule this got partway through is a rule
     * with no line here, it is this compiler having fallen short, and it leaves the question that
     * rule raised standing — so it is the only member of {@link RuleWithoutLineReason} a caller
     * asking about a stop may be handed, and the only member of {@link QuestionStandingReason}
     * that names a rule.
     */
    sealed interface RuleReadingStopped extends StoppedWithoutALine, QuestionStandingReason {

        /**
         * Where this stands among these, for a reader putting some of them in a steady order.
         *
         * <p>Beside the members and not inside whoever sorts them. A sealed type is a set and not a
         * sequence — {@code getPermittedSubclasses} says so itself, answering in no order it
         * specifies — so a walk reading its array as a sequence takes an order from something that
         * has none. Written here, whoever adds a member places it; written where a carrier sorts,
         * the next carrier to need an order writes a second one.
         *
         * <p><b>Steady and nothing else.</b> What the numbers mean is nothing beyond which comes
         * first, and they say nothing about what an author wrote: that is a fact about where the
         * rules stand and is asked where the places are ({@code AdequacyReport}). This is what
         * keeps one compiler over one source publishing one document where nobody wrote an order.
         *
         * <p>A switch and no {@code default}, so a reason added to the vocabulary is placed by
         * whoever adds it rather than arriving wherever the runtime happened to put it.
         */
        static int inASteadyOrder(RuleReadingStopped reason) {
            return switch (reason) {
                case UnreadComparisonForm _ -> 0;
                case UnreadComparisonDomain _ -> 1;
                case ValueRuleRelatingTwoPositions _ -> 2;
                case CasePairingNotDetermined _ -> 3;
                case RuleAboutADerivedValue _ -> 4;
                case UnreadValueRule _ -> 5;
                case PatternTooDeeplyNested _ -> 6;
                case PatternTooCostly _ -> 7;
                case OrderedExtentTooCostly _ -> 8;
                case RuleAboutAnElementOfSeveralSequences _ -> 9;
                case EndLeftOpenByAChoice _ -> 10;
                case ValueRuleLeftOpenByAChoice _ -> 11;
            };
        }

        /**
         * Whether a position holding this rule has values nothing can claim are what the rules
         * leave.
         *
         * <p>What a caller means by "a reading stopped here" and what this type is
         * ({@link RulesWithNoLine#aReadingThatStopped}). They were one question while every rule
         * this compiler got partway through was one the reading of values got partway through: a
         * rule that reaches a position and is not taken in leaves the values there an upper bound,
         * and a reader deciding whether the position was answered exactly asked which type the
         * reason had.
         *
         * <p>They are not one question. A rule can be read to the end by the reading that says
         * which values may stand at a position and be one the reading that says where they stop
         * could not follow — and then the values are exactly what the rules leave, and only the
         * line is missing. Answered off the type, such a position comes back as one whose cases
         * are all unsettled because a rule went unread, on the strength of a border.
         *
         * <p>One switch, as {@link #runSensitivity} is one, and for the same reason: the answers
         * are only reviewable together. Every arm that was here before this question was asked
         * answers as it did — each of them is a stop of the reading that turns clauses into sets of
         * values — so nothing about what a position admits moves by this being asked.
         */
        default boolean widensWhatThePositionAdmits() {
            return switch (this) {
                // The reading of ends could not follow an alternative, and every alternative's
                // values were read. What the position admits is what the rules leave; the line
                // through them is what nobody worked out.
                case EndLeftOpenByAChoice _ -> false;
                // And the same operator read the other way does widen it, which is the whole
                // difference between the two: what the alternatives admit was not read here, so
                // the position holds whatever a value taking the unread branch may hold.
                case PatternTooCostly _, PatternTooDeeplyNested _, OrderedExtentTooCostly _,
                     UnreadComparisonForm _, UnreadComparisonDomain _, RuleAboutADerivedValue _,
                     RuleAboutAnElementOfSeveralSequences _, UnreadValueRule _,
                     ValueRuleLeftOpenByAChoice _,
                     ValueRuleRelatingTwoPositions _, CasePairingNotDetermined _ -> true;
            };
        }

        /**
         * One switch over the twelve, and the reason for it being one: a division of these into two
         * is only reviewable where all twelve answers are visible together.
         */
        @Override
        default RunSensitivity runSensitivity() {
            return switch (this) {
                // Three figures this compiler compared a rule against: the states a pattern is
                // built into, how deeply one may be bracketed, and the machines that say where the
                // strings it admits stop. A run allowed more of any of them need not stop at the
                // same rule.
                case PatternTooCostly _, PatternTooDeeplyNested _,
                     OrderedExtentTooCostly _ -> RunSensitivity.MAY_CHANGE;
                // And eight where nothing was compared against anything. A form nothing takes
                // apart, values no line can be drawn on, a rule about a value made from this one, a
                // rule about an element of one of several sequences, a relation between two
                // positions and a pairing nothing worked out are all met again by a run allowed
                // more of everything. So is an end a choice left open: what the reading of ends
                // stops on is a form it does not enter, and there is no figure it stopped at.
                case UnreadComparisonForm _, UnreadComparisonDomain _, RuleAboutADerivedValue _,
                     RuleAboutAnElementOfSeveralSequences _, UnreadValueRule _,
                     ValueRuleRelatingTwoPositions _, EndLeftOpenByAChoice _,
                     ValueRuleLeftOpenByAChoice _,
                     CasePairingNotDetermined _ -> RunSensitivity.UNAFFECTED;
            };
        }
    }

    /**
     * A rule read to the end that left no position divided, which is the other half.
     *
     * <p><b>The reading of the rule succeeded.</b> That is the line between this and
     * {@link ReadingStopReason} and it is a line about the reading, not about how sure anybody is:
     * a rule reaches here having been taken in through this position, and what is absent is an
     * ordered line coming out of what it says. A reader that cannot tell which half it is in is
     * holding a rule it did not finish, and that is the other half — there is no arm here for
     * "read far enough to guess", and one added would be this compiler's uncertainty filed as a
     * fact about the model.
     *
     * <p>Ways for that to happen, and they are their own: the quantity the rule cuts is empty, the
     * quantity is a form over several positions and divides none of them on its own, the line falls
     * where the quantity never runs, and the rule divides the position by something that is not an
     * order at all. Each is a fact about the rule rather than about this compiler — the reading
     * finished — and each is still worth saying, because a position nothing is said about comes
     * back as one the model states nothing about, and the model states this.
     *
     * <p>No measure is short of anything here, and that is what makes them one half rather than
     * three reasons that happen to agree. A rule that was read has had whatever it places placed by
     * the reading that placed it, and where it places none there is none to be owed.
     *
     * <p><b>Not a {@link ReadingStopReason}, and that is the whole of what the type is for.</b>
     * Nothing here is a stop, so nothing here may reach a caller asking what stopped a reading —
     * an accounting that calls such a rule unanswered, a claim that calls it unread, a value
     * reading that reports itself partial on its account. Each of those was reachable while one
     * type stood for both halves.
     */
    sealed interface ReadToEndWithoutLine extends RuleWithoutLineReason {
    }


    /**
     * The reading did not get to the rules of the position, so there is no rule to name.
     *
     * <p>Not a rule read and found wanting. A depth, a shape nothing reaches into, a type that
     * could not be worked out, a gathering that stopped — none of them is about any one thing an
     * author wrote, and a finding built on one names the position and nothing else. Told apart from
     * the above by the type, because the two used to be one set and a report could name a rule for
     * some of them and not the rest with nothing saying which was which.
     *
     * <p>Every one of these is a stop. There is no reading that arrives at the end of a position's
     * rules and reports one of these, so this whole half sits inside {@link ReadingStopReason} and
     * the two questions meet only at {@link RuleReadingStopped}.
     */
    sealed interface AboutThePosition extends ReadingStopReason {

        /** The same switch over the five ways a reading never got to a position's rules. */
        @Override
        default RunSensitivity runSensitivity() {
            return switch (this) {
                // A type nothing could interpret, a path returning to a declaration it has been
                // through, and a place this does not reach into. None of them is a figure anything
                // was compared against.
                case TypeUnresolved _, RecursiveExpansion _, UnsupportedTraversal _,
                     ValueRulesNotReached _ -> RunSensitivity.UNAFFECTED;
                // And the one figure among them: a reading that stopped at the depth it could
                // afford, which a run allowed to read further need not stop at.
                case ValueRulesNotReachedPastDepthLimit _ -> RunSensitivity.MAY_CHANGE;
            };
        }
    }

    /**
     * What a value reading's account of a rule it could not use comes to here.
     *
     * <p><b>Every one of them is a stop, and that is a fact about this reading rather than about
     * the rule.</b> What is held at a position is a set of its own values, and every arm of
     * {@link souther.compiler.values.UnreadReason} widens it — a position carrying one of them
     * admits what the reading arrived at and may admit fewer. So a caller here is being told this
     * compiler fell short, whichever rule shape it fell short on.
     *
     * <p>Which is why a relation between two positions is {@link ValueRuleRelatingTwoPositions}
     * here and {@link ComparisonBetweenPositions} where a line is drawn. One rule, two readings,
     * two answers, and both are true: the reading of ends took {@code a < b} in completely and
     * placed no line, and the reading of values could not turn it into a set of one position's
     * values at all. Written as one reason, the second was reported in the words of the first —
     * that nothing fell short — over a position whose values are an upper bound. A word out there
     * is not what splits: {@link ReportedReason} sends both to the one the document has, so a
     * reader holding either still meets one vocabulary.
     *
     * <p>{@link souther.compiler.values.UnreadReason#NOT_REACHED} is refused rather than answered.
     * A reading that never arrived at the rules of a position is holding no rule for this to be
     * about, so a caller here would be naming one it does not have — which is the whole of what
     * {@link AboutThePosition} is beside this for.
     */
    static RuleReadingStopped ofARuleTheValueReadingLeft(souther.compiler.values.UnreadReason why) {
        // Whether there is a rule to name is the reason's own answer and not a list kept here.
        // Kept here, this was a third place saying which reasons are about a rule, and three
        // answers to one question are three chances for two of them to disagree.
        if (why.about() != souther.compiler.values.UnreadReason.About.A_RULE) {
            throw new IllegalArgumentException(
                    "a reason about " + why.about() + " holds no rule to say this of: " + why);
        }
        return switch (why) {
            case RELATES_TWO_POSITIONS -> new ValueRuleRelatingTwoPositions();
            case FORM_NOT_READ -> new UnreadValueRule();
            // Its own, and not the one above. Both leave what may stand at the position open and
            // an author does different work about them: one is a clause to rewrite and one is a
            // branch beside a clause that reads.
            case ALTERNATIVE_NOT_READ -> new ValueRuleLeftOpenByAChoice();
            case PATTERN_TOO_COSTLY -> new PatternTooCostly();
            case PATTERN_TOO_DEEPLY_NESTED -> new PatternTooDeeplyNested();
            // Refused above, each of them, and named here so that a reason added to the vocabulary
            // stops this rather than arriving as whichever arm is nearest.
            case EXACT_VALUES_TOO_COSTLY, NOT_REACHED, NOT_REACHED_PAST_DEPTH_LIMIT ->
                    throw new IllegalStateException("refused above: " + why);
        };
    }

    /**
     * The same, for a caller that has to answer for every way a value reading can be short.
     *
     * <p>What a position is left with, and not a finding: both authorities are legitimate answers
     * to "why is there nothing here", and which of them it is decides what a report may go on to
     * say rather than whether the reading stopped. Written in terms of the one above, so the
     * classification is stated once.
     *
     * <p>A stop either way, which the type says. A value reading has no way of finishing and
     * leaving a rule without a line — that is the reading of ends' answer to give — so nothing this
     * returns may reach a caller as a rule read from end to end.
     */
    static ReadingStopReason of(souther.compiler.values.UnreadReason why) {
        return switch (why) {
            case NOT_REACHED -> new ValueRulesNotReached();
            case NOT_REACHED_PAST_DEPTH_LIMIT -> new ValueRulesNotReachedPastDepthLimit();
            // And every other way this reading is short by the one below, since all of them leave a
            // question of a rule standing. Written out rather than defaulted to: a reason added to
            // the vocabulary is a decision about which of the two halves it is, and a default takes
            // that decision by arriving at whichever arm was written last.
            case EXACT_VALUES_TOO_COSTLY, RELATES_TWO_POSITIONS, FORM_NOT_READ,
                 ALTERNATIVE_NOT_READ, PATTERN_TOO_COSTLY, PATTERN_TOO_DEEPLY_NESTED ->
                    ofAQuestionStandingOn(why);
        };
    }

    /**
     * The same, for a caller holding a question a rule raised that nothing answered.
     *
     * <p>Two kinds of reason reach a standing question and one does not. A rule this reading gave
     * up on and an answer it could not build both leave the question where they found it; a reading
     * that never arrived at the position raises no question for anything to stand on, so one of
     * those here is a caller answering a question out of a place it never looked.
     *
     * <p>Refused by naming the two rather than by asking what came back from the one above. Which
     * capability a reason arrives in is what this decides, so reading it off a reason this made is
     * deciding it twice — and a reason added to the vocabulary would be classified by whichever arm
     * it happened to land in instead of stopping the compile here.
     */
    static QuestionStandingReason ofAQuestionStandingOn(souther.compiler.values.UnreadReason why) {
        return switch (why) {
            case EXACT_VALUES_TOO_COSTLY -> new ExactValuesTooCostly();
            case RELATES_TWO_POSITIONS, FORM_NOT_READ, ALTERNATIVE_NOT_READ, PATTERN_TOO_COSTLY,
                 PATTERN_TOO_DEEPLY_NESTED -> ofARuleTheValueReadingLeft(why);
            case NOT_REACHED, NOT_REACHED_PAST_DEPTH_LIMIT -> throw new IllegalArgumentException(
                    "a reason about " + why.about() + " leaves no question of a rule standing: "
                            + why);
        };
    }

    /**
     * The same, for a caller holding what the answer at a position was short of.
     *
     * <p>The other half of the pair above, and refused the same way: a reason about a rule is filed
     * under that rule and does not come this way, and a reason about neither is a reading that never
     * arrived. What is left is a fact about what the rules of a position come to between them, which
     * names no rule and sends a reader to no clause.
     */
    static AnswerRealizationStopped ofTheAnswerTheReadingCouldNotBuild(UnreadReason why) {
        if (why.about() != UnreadReason.About.THE_ANSWER) {
            throw new IllegalArgumentException(
                    "a reason about " + why.about() + " is not one the answer was short of: " + why);
        }
        return switch (why) {
            case EXACT_VALUES_TOO_COSTLY -> new ExactValuesTooCostly();
            // Refused above, each of them, and named here so that a reason added to the vocabulary
            // stops this rather than arriving as whichever arm is nearest.
            case RELATES_TWO_POSITIONS, FORM_NOT_READ, ALTERNATIVE_NOT_READ, PATTERN_TOO_COSTLY,
                 PATTERN_TOO_DEEPLY_NESTED, NOT_REACHED, NOT_REACHED_PAST_DEPTH_LIMIT ->
                    throw new IllegalStateException("refused above: " + why);
        };
    }

    /**
     * The type at the position could not be interpreted: a name denoting no declaration, or a
     * newtype whose {@code value} the walk over the names could not reach. A report is written
     * about such a model, so this is a position a report is asked about and cannot be answered for.
     */
    record TypeUnresolved() implements AboutThePosition {}

    /**
     * Reading on would open a declaration this path has already opened, so what is under the
     * position is not unfolded again.
     *
     * <p><b>Not that the declaration is recursive.</b> A type that names itself is an ordinary
     * thing to declare and an ordinary thing to hold a value of; what this says is that one
     * derivation does not unfold one declaration twice on one path, which is a bound this reading
     * puts on itself. Named the other way, an author would read a report of their model where there
     * is a report of this compiler.
     *
     * @param declaration what the input returned to
     * @param openedAt    where the path opened it the first time, which is the far end of the cycle
     */
    record RecursiveExpansion(souther.compiler.types.TypeSymbol declaration, TermPath openedAt)
            implements AboutThePosition {

        public RecursiveExpansion {
            if (declaration == null || openedAt == null) {
                throw new IllegalArgumentException(
                        "a return to nowhere is not one: " + declaration + " at " + openedAt);
            }
        }
    }

    /**
     * The shape at the position holds values this cannot reach into, and which reaching is missing
     * is which of {@link Traversal} it is.
     *
     * <p>Held apart rather than made one word, because what would lift each is a different piece of
     * work: choosing among however many elements a sequence holds, choosing whether an optional
     * holds one, and deciding what part of a mapping a rule is even about. Reporting them alike
     * would let one of them being implemented read as all three.
     */
    record UnsupportedTraversal(Traversal traversal) implements AboutThePosition {}

    /**
     * A comparison naming the position is written in a form no reader here takes apart: the
     * position inside an expression the terms do not name, or a threshold written as something
     * other than a constant.
     *
     * <p>Whichever rule wrote it. An invariant's clause and a {@code guard}'s comparison are two
     * producers of one kind of evidence (spec §example-partition), and what stopped each of them is
     * the same fact about this compiler.
     */
    record UnreadComparisonForm() implements RuleReadingStopped {}

    /** A comparison naming the position is against values no line is drawn on here — the carrier,
     *  asked of the carrier. */
    record UnreadComparisonDomain() implements RuleReadingStopped {}

    /**
     * A rule is written about a value that came from the position rather than about the position.
     *
     * <p>An operation answered what a closure made of what stands here, and a rule was written about
     * that. Where the values came from is known; what the rule says about the values <em>here</em>
     * is not, and working it out means inverting whatever the closure did — which is a different
     * capability and not one this compiler has.
     *
     * <p>Its own case and not {@link UnreadComparisonForm}. The form was read perfectly well and the
     * position it is about was found; an author told the first would go looking for a syntax that
     * is not the difficulty. It is here at all so that such a rule is not silent: read as nothing,
     * a model whose predicate this cannot follow is one that states no rule.
     */
    record RuleAboutADerivedValue() implements RuleReadingStopped {}

    /**
     * A rule is written about an element of a sequence, in a block handed to more than one walk.
     *
     * <p>One block written once and given to two operations: the name it reads the element under
     * holds an element of a different sequence on each run, so a rule inside it is about one of
     * them and nothing here says which. It is filed at every one of them, because that is what is
     * known — the rule is about one of these places and each is a place a reader can be sent to.
     *
     * <p>And filed at each of the ones there are where a run through the block stands at no
     * position of the input at all — a block handed to a walk over a list written in the body as
     * well as over a parameter. What that run says about the input is nothing, and what the others
     * say is still owed where they say it.
     *
     * <p>Its own case and not {@link RuleAboutADerivedValue}. Nothing was made out of the element
     * and there is no operation to invert; an author told that one would go looking for a
     * computation that is not there. What lifts this is telling the two walks apart, and what an
     * author can do about it today is write the block twice.
     */
    record RuleAboutAnElementOfSeveralSequences() implements RuleReadingStopped {}

    /**
     * A rule naming which values the position may hold is written in a form no reader here takes
     * apart as a set of them: a call, a pattern, a comparison against something other than a value
     * written out.
     *
     * <p>Its own case beside {@link UnreadComparisonForm}, which is about a rule stating where the
     * values stop. The two are read by different readers of the same clause and would be lifted by
     * different work — one wants a wider fragment of comparison forms, and one wants a reading of
     * values that follows a rule into a shape it does not enter today.
     */
    record UnreadValueRule() implements RuleReadingStopped {}

    /**
     * A choice in the rule offers an alternative this compiler does not read, and where the values
     * stop here is what the two alternatives leave together.
     *
     * <p>The rule was read. What stopped is the reading of one branch of it, and a value satisfying
     * that branch owes the branch beside it nothing — so the end at this position is as far out as
     * whatever the unread alternative allows, which is not known.
     *
     * <p>Its own case beside {@link UnreadComparisonForm}, and the difference is what an author can
     * do about it. That one says the comparison at this position is written in a shape no reader
     * here takes apart, and an author sent after it would rewrite a bound that reads perfectly
     * well. What they can act on is the branch written beside it.
     *
     * <p><b>Which choice it was is not here, and a document does not say it yet.</b> A rule may
     * hold two of them that each leave one end open, and they are two things an author has to do —
     * so this is one sentence about two, exactly as the same shape is on the reading of values,
     * whose two shortfalls at one position come to one line as well. What would tell them apart is
     * the operator each was written at, and a rule an author named is found by that name
     * ({@link souther.compiler.check.RuleCitation}): there is nowhere in what a document says about
     * such a rule to put a second place. Split without one, the two are the same sentence twice.
     *
     * <p>So the choices are kept where they are told apart — the construct each was written as,
     * and the copy of it ({@code ChoiceToLift}) — and what reaches a position says how many
     * ({@link EndLeftOpen}). What is missing is a way for a document to name a place inside a named
     * rule, and it is the same thing missing wherever the two readings' accounts of one choice are
     * to be put together.
     */
    record EndLeftOpenByAChoice() implements RuleReadingStopped {}

    /**
     * A choice offering an alternative nothing read, leaving what may stand at a position open.
     *
     * <p>The other reading's half of {@link EndLeftOpenByAChoice}, and its own case for the reason
     * that one is: {@link UnreadValueRule} promises the rule at this position is written in a form
     * nothing here takes apart, and an author acting on it rewrites a clause that was read from end
     * to end. What went unread is the branch beside it.
     *
     * <p>Which is what the value reading has recorded all along ({@code UnreadReason
     * .ALTERNATIVE_NOT_READ}) and lost here, arriving at a document as the form nobody could read.
     * The distinction was worth making inside the reading and was worth nothing to the reader it
     * was made for.
     *
     * <p>What a document writes for this and for the one above is one word. They are two readings
     * of one operator saying two things about it — what may stand at the position, and where those
     * values stop — and which of them a reader is being told is what the section they are in says.
     * Two words would put that in the vocabulary as well, and a reader joining the pair would have
     * to know they meant the same thing.
     */
    record ValueRuleLeftOpenByAChoice() implements RuleReadingStopped {}

    /**
     * A rule naming a set of strings whose machine is more than this compiler will make.
     *
     * <p>Its own case beside {@link UnreadValueRule}, and the difference is what an author can do
     * about it. That one says a rule is written in a shape no reader here enters, and what lifts it
     * is a wider reading. This says the shape was entered and the rule is simply large — a pattern
     * is a machine as big as it is written — so an author told the other would go looking for a
     * syntax that was never the difficulty.
     *
     * <p>A rule all the same, which is why it is here and not beside the one below. Somebody wrote
     * this pattern and could write it differently.
     */
    record PatternTooCostly() implements RuleReadingStopped {}

    /**
     * A rule written more deeply nested than this compiler reads.
     *
     * <p>Its own case beside the two above, and the difference is again what an author does about
     * it. {@link UnreadValueRule} sends them to the construct nothing here enters, and every
     * construct in this one is entered; {@link PatternTooCostly} says the machine would be too
     * large, and this never reached one. What is left is the brackets, which is something they can
     * write differently.
     */
    record PatternTooDeeplyNested() implements RuleReadingStopped {}

    /**
     * What a pattern nothing read is, in the words a rule left unread is said in.
     *
     * <p>Here because two readings ask it. A declaration's clauses and a behavior's body both write
     * rules whose text is a pattern, and both are stopped by the same things — so what an author is
     * told is a fact about the pattern and about this compiler's reader, and not about which of the
     * two tree walks met it. Written once per reader, the day one of them learned that a construct
     * is the reading's own limit rather than one it has no word for, an author would be sent to the
     * brackets by one reading and to a construct that was never the trouble by the other.
     *
     * <p>Two answers and not one per code. A pattern written more deeply than this reads is the
     * reading's own limit and is said as itself; every other construct is one the subset does not
     * hold, which is a rule this could not read like any other.
     */
    static RuleReadingStopped forAPatternNotRead(PatternRead.Unsupported why) {
        return why == PatternRead.Unsupported.NESTED_TOO_DEEPLY
                ? new PatternTooDeeplyNested() : new UnreadValueRule();
    }

    /**
     * A rule whose strings this read, and whose place on the order they are measured on would take
     * more machines than this compiler will make.
     *
     * <p>Its own case beside {@link PatternTooCostly}, and the difference is what was too large. That
     * one is the pattern an author wrote turned into the strings it accepts, which is a machine as
     * big as the pattern is written and is something they can write differently. This is the further
     * work of asking where those strings begin and end — the strings above the first of them, the
     * ones the rule leaves out, and the two put together — and none of those is a machine anybody
     * wrote. Told the other, an author would go looking at a pattern that was read perfectly.
     *
     * <p>Which limit refused it is kept. A machine larger than one machine may be is a shape
     * somebody wrote and could write smaller; an answer that had already spent what it was allowed
     * is not, and the same rule asked first would have been read.
     */
    record OrderedExtentTooCostly(souther.compiler.regex.Meter.Stopped stopped)
            implements RuleReadingStopped {

        public OrderedExtentTooCostly {
            if (stopped == null) {
                throw new IllegalArgumentException(
                        "a construction that stopped was stopped by a limit");
            }
        }
    }

    /**
     * The rules about this position were followed, every one of them became the set it names, and
     * what those sets come to between them is more than this compiler will build.
     *
     * <p><b>About the answer and about none of the rules.</b> Two patterns each small on its own
     * have a meet the size of their product, and two rules of one declaration meet at a position as
     * surely as two halves of one clause do. So what ran out is the allowance for what the position
     * finally admits, and naming one of the rules would say that rule is why — which for a product
     * of two is false of each of them.
     *
     * <p>So it carries no rule and is not among the reasons that do ({@link
     * AnswerRealizationStopped}). A reader here has nothing to send an author to, and that is the
     * answer rather than something missing from it.
     */
    record ExactValuesTooCostly() implements AnswerRealizationStopped {}

    /**
     * A behavior's rules about the strings at this position were read, and building what they tell
     * apart is more than this compiler will do.
     *
     * <p><b>About the position's distinctions and about none of the rules.</b> The rules of one
     * position are built as one group, because what a reader is told about the position must not
     * turn on which of them the building reached first — so what ran out is the allowance for the
     * group, and naming one rule would say that rule is why when any of the others being cheaper
     * would have left it affordable.
     *
     * <p>Its own case beside {@link ExactValuesTooCostly}, which is what a declaration's answer ran
     * out on. That one bounds what may stand at a position and this one bounds what a behavior
     * tells apart there, and they are separate on purpose: a run allowed more of one must not
     * change the other's answer. Said as that one, a position whose declaration was answered
     * exactly would be reported as one whose values could not be worked out.
     *
     * <p><b>And a rule with no line at the position all the same</b> ({@link StoppedWithoutALine},
     * as {@link RulesNotHandedOnAsSets} is). What ran out is the group, and the rules of the group
     * are written at the position — so a reader of the position is owed the sentence about each of
     * them, and what it says is that the position's distinctions were not built rather than that
     * this one rule was the expensive one.
     */
    record BehaviorDistinctionsTooCostly()
            implements AnswerRealizationStopped, StoppedWithoutALine {}

    /**
     * The rules about the strings at this position were read, what the position admits was worked
     * out, and what each of those rules leaves on its own was more than this compiler would build.
     *
     * <p>Two questions and this is the second. What a position admits is every rule of it met
     * together; what a reader drawing lines needs is what each of them leaves on its own, and a
     * rule met with its neighbours can be settled without ever making its machine — a pattern
     * beside a value the rules write out is a question about that value. So the sets handed on are
     * not the sets the answer needed, and where they cannot be made the answer stands exactly while
     * nothing says where the strings of one rule stop.
     *
     * <p><b>About the position and about none of its rules.</b> They are made as a group out of one
     * allowance, so a rule cheap enough on its own goes unmade beside one that was not — and which
     * of them was which may not be told, because it would send an author to rewrite whichever rule
     * the building reached last. Which is why this carries no rule and is not among the reasons
     * that do ({@link RuleReadingStopped}).
     *
     * <p>Beside {@link ExactValuesTooCostly} rather than the same thing said twice: that one is the
     * position's own answer coming out wider than the rules leave it, and here that answer is what
     * the rules leave and a reader is short of something else.
     */
    record RulesNotHandedOnAsSets() implements AnswerRealizationStopped, StoppedWithoutALine {}

    /**
     * A rule about the position says how it stands against another position, and what is held here
     * is a set of this position's own values.
     *
     * <p>The reading of values could not turn the rule into one, so what it arrived at is an upper
     * bound: a value it holds may be one no row can take, and a value it does not hold is still
     * one no rule admits. That is a stop, and every arm of
     * {@link souther.compiler.values.UnreadReason} is one for the same reason.
     *
     * <p><b>Its own case and not {@link ComparisonBetweenPositions}, which is the other reading's
     * answer for the same rule.</b> Nothing about {@code a < b} was beyond the reading of ends: it
     * took the rule in whole and placed no line, and no measure is short of anything on its
     * account. The reading of values met the same rule and got nothing it could hold. One reason
     * for both said the second in the words of the first — that the rule was read and nothing is
     * missing — over a position whose values this compiler cannot state.
     */
    record ValueRuleRelatingTwoPositions() implements RuleReadingStopped {}

    /**
     * The reading of what the position may hold never reached the rules about it.
     *
     * <p>Not a rule it read and could not use. The walk that gathers a value's clauses stopped
     * somewhere — at a type it had already been through, at one with no declaration to read — or a
     * clause could not be typed and so arrived nowhere. None of those is a fact about the rule, and
     * all of them leave the same hole: what is written about this position is not known to have
     * been read.
     *
     * <p>And none of them is a figure this compiler stopped at, which is what
     * {@link ValueRulesNotReachedPastDepthLimit} is beside this for.
     */
    record ValueRulesNotReached() implements AboutThePosition {}

    /**
     * The same, where what stopped the reading was how far down it could afford to read.
     *
     * <p>Its own reason and the same hole. A depth this compiler could not afford is a figure it
     * compared something against, so a run allowed to read further need not stop at this position;
     * every other way of never reaching one is met again whatever a run allows. Held as one reason,
     * nothing could say which of the two a reader was looking at.
     *
     * <p><b>And the same published word.</b> Which figure stopped a walk is this compiler's
     * business — a document promises a reader the hole and not the route to it — so
     * {@link ReportedReason} takes both to {@code RULES_NOT_READ_AT_ALL}. That is what this
     * vocabulary being apart from the published one is for: the precision is recorded without a
     * reader being promised it.
     */
    record ValueRulesNotReachedPastDepthLimit() implements AboutThePosition {}

    /**
     * The rule was read, it draws a line, and where each of the names it is between stands is known
     * — and which of those positions go together is not.
     *
     * <p>This compiler getting partway and no further, which is what puts it here rather than beside
     * a rule read to the end that divides nothing. The comparison was taken apart, a line came out
     * of it, and every name it is between reached positions; what was not reached is the pairing.
     *
     * <p><b>Not about how many sums are on the way.</b> Two names narrowed by one value are narrowed
     * together and two names under separate choices are not, and which of the two this is is a fact
     * about the model. Said as a shape this compiler does not read, an author would go looking for
     * another way to write a comparison it reads perfectly well.
     */
    record CasePairingNotDetermined() implements RuleReadingStopped {}

    /**
     * The comparison was read to the end and cuts no quantity at all.
     *
     * <p>{@code a - a > 0} names a position and states nothing about its values: what it compares
     * is a number the position does not appear in. Nothing here fell short — the form was read
     * completely, and there was no line in it to draw.
     *
     * <p>Its own case beside {@link UnreadComparisonForm}, which is the answer for a form that was
     * not read. The two were one absence and so one word, and a rule this compiler had read from
     * end to end was reported as one whose spelling defeated it.
     */
    record ComparisonCuttingNothing() implements ReadToEndWithoutLine {}

    /**
     * The comparison was read to the end, the quantity it cuts was found, and the line it draws
     * falls where that quantity never runs.
     *
     * <p>Three times a length is never negative, so {@code List.length(xs) <= -1} has no value
     * either side of its line for a row to be owed at and divides the position into nothing.
     *
     * <p>Its own case beside {@link ComparisonCuttingNothing}, which is the answer where the
     * quantity itself is empty. The two are opposite halves of one shape: there the rule has no
     * quantity to cut, and here it has one and cuts outside it. Read as a rule this compiler could
     * not take in, an author was sent after a limit that is not there.
     */
    record ComparisonCuttingOutsideDomain() implements ReadToEndWithoutLine {}

    /**
     * The comparison was read to the end, the quantity runs as far as the line it draws — and no
     * row that arrives at the comparison holds a value at the line.
     *
     * <p>{@code guard value >= 10} above {@code guard value >= 5} leaves the second guard's line
     * with nothing against it: every row that gets there is already past it. The line is inside
     * what the declarations leave, so this is not {@link ComparisonCuttingOutsideDomain} — an
     * author reading that one would look at the rule for a line their declarations refuse, and
     * what refuses it is the conditions on the way to the comparison.
     *
     * <p>Said only on a proof. The values that arrive were read off the paths, and either nothing
     * arrives at all or what arrives stops short of the line; a comparison whose arrival nothing
     * could read keeps its line and its rows.
     */
    record ComparisonNothingArrivesAtItsLine() implements ReadToEndWithoutLine {}

    /**
     * The comparison relates two positions rather than dividing one.
     *
     * <p>Nothing is missing from the carrier: both sides are ordered, and a line drawn on either
     * against a number would be read. What is missing is a class about two positions, which a
     * partition of one is not — so a line like this is settled beside the partition rather than in
     * it, and the position it names is left with no class of its own from this rule.
     */
    record ComparisonBetweenPositions() implements ReadToEndWithoutLine {}

    /**
     * The comparison draws its line on a number taken over a run of values, which divides no
     * position.
     *
     * <p>Its own word and not {@link ComparisonBetweenPositions}. Nothing here is between two
     * positions: there is one number and one line, and the values it is read from stand at a place
     * inside a sequence. Two lines of sixty and forty are on the boundary of a hundred as surely as
     * one of a hundred is, so there is no class of that place for the rule to have drawn — and a
     * reader told the rule relates two positions would go looking for the pair.
     *
     * <p>Not a limit of this compiler either. The rule was read to the end and its border is drawn;
     * what is absent is a partition, because the model divides no position by it.
     */
    record ComparisonOverARun() implements ReadToEndWithoutLine {}

    /**
     * The rule holds this position to the values it admits, and places no end on them.
     *
     * <p>What a reader is owed, and it is a fact they act on: the value written here has to be one
     * of the ones the rule admits, because everything outside them is refused at construction
     * (E1903). A bounded newtype gets the same restraint said as an edge; this is the same fact
     * about a rule whose admitted values are not a run of the order, and it is why there is no
     * class away from them to cover (ADR-0090).
     *
     * <p><b>Not a division.</b> That a rule tells the values it admits from the rest is a fact
     * about those values, and whether the position is divided is a different question, answered by
     * what the rule is written in. Under an invariant the other side is no class of the position, so
     * a reader told the model divides the position here is told the opposite of what the declaration
     * says.
     *
     * <p><b>And it says nothing either way about whether the position is divided.</b> A rule may
     * restrict and divide at once — {@code invariant value == "A" || value == "B"} admits two values
     * and every other string is no class of the position — so the classes are read where they are
     * read ({@link Distinctions#ofValues}) and this stays a statement about what may stand here.
     *
     * <p>Nor a limit of the reading. What a rule leaves a position is worked out by the reading
     * that turns clauses into sets, and one it could not work out is not one of these: a set left
     * wide because something went unread is this compiler falling short, and said from here it
     * would go out as a fact about the model.
     */
    record RuleRestrictingToAdmittedValues() implements ReadToEndWithoutLine {}

    /**
     * A rule of a behavior about the strings at a position, read to the end, with nothing on one of
     * its sides.
     *
     * <p>A predicate divides a position by putting the values that satisfy it on one side and the
     * rest on the other, and that is two classes only where both sides hold a value. A pattern no
     * string satisfies leaves the first empty and one every string satisfies leaves the second, and
     * either way a run of the model is on the same side however it is written.
     *
     * <p>Nothing here fell short: the predicate was read, the text was worked out and both sides
     * were built. What came of it is that the model draws no distinction, which is a fact about the
     * rule and something an author can see in it.
     */
    record PredicateTellingNothingApart() implements ReadToEndWithoutLine {}

    /**
     * Every rule about this position was read, and what they say cannot all be said as one list of
     * classes.
     *
     * <p>A rule can put a line on the order the position's values are counted on, or tell a set of
     * them from the rest, and the two are not one vocabulary: a run of values has a least and a
     * next, and a set has neither, so a class written in one of them cannot be written in the
     * other. A position both kinds reach is one this compiler has no single denominator for.
     *
     * <p><b>Nothing here fell short of a rule.</b> Every one of them was read and each says what it
     * says — which is what keeps this apart from {@link RulesNotHandedOnAsSets} and from
     * {@link BehaviorDistinctionsTooCostly}. Those are a set the rules leave that was not worked
     * out; here every set was worked out and the classes are what they will not compose into. A run
     * allowed more of everything meets this again.
     *
     * <p>And it is the classes and not the position. What the lines cut and where the rules part it
     * are still what they were, because those are separate observations rather than a projection of
     * the classes.
     */
    record ClassesNotComposed() implements ReadToEndWithoutLine {}

    /**
     * What a derivation would have to be able to reach into.
     *
     * <p>What it can reach into is not here. The elements of a {@code List} or a {@code Set} were,
     * and are positions of the input now; so was the value an {@code Option} holds, which is a
     * branch of the position under the narrowing that it holds one. A word for a reaching that is
     * made says a reader can still meet it, and the next one to read this would take its presence
     * for evidence that a sequence, or an optional, is where the walk stops.
     */
    enum Traversal {

        /**
         * What a {@code Map} holds. One case and not two, because which of a key and a value a rule
         * would be about has not been decided — and a distinction invented here would be a promise
         * about a semantics nobody has written.
         */
        MAPPING_CONTENT
    }

}
