package souther.compiler.check;

import souther.compiler.inputs.BlockReason;
import souther.compiler.inputs.RuleReasons;
import souther.compiler.inputs.RuleSite;
import souther.compiler.inputs.WhatAQuestionStandsOn;
import souther.compiler.values.UnreadReason;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * One rule where it applies to one value, every question it raises there, and what answered each.
 *
 * <p>Of a rule at a value and not of a rule. What a rule raises is written in the vocabulary of the
 * value being read and differs in number between values ({@link Required}), so a rule the model
 * holds several values to has one of these at each of them. Which value this is of is the reading
 * these come from: a reading is of one declaration, and everything holding one of these holds it
 * beside that.
 *
 * <p>Closed over the questions rather than a list of answers somebody assembled. What can go wrong
 * with a list is that a question is missing from it, and a completeness read off a list with a
 * question missing says the model was accounted for because nobody asked. So the questions come
 * from {@link Required} and the answers are obtained for exactly those, one apiece: a question
 * without an answer, an answer without a question, and two answers to one question are all
 * unreachable rather than checked for.
 *
 * <p>No verdict about the rule. Whether the rule is settled follows from its answers and from the
 * answers to whatever its questions rest on, and that is a derivation over the whole accounting
 * rather than a field here — written as a field, a question blocked by another would have to be
 * marked blocked by whoever noticed, which is a third state anybody can write.
 */
public final class RuleAccounting {

    private final RuleCitation.Named cited;
    private final Required required;
    private final Map<Owed, Outcome> answers;

    private RuleAccounting(RuleCitation.Named cited, Required required,
                           Map<Owed, Outcome> answers) {
        this.cited = cited;
        this.required = required;
        this.answers = Collections.unmodifiableMap(answers);
    }

    /**
     * The accounting of {@code rule}, with {@code answered} asked once for each question it
     * raises.
     *
     * <p>The only way to one of these, and not one anything outside this package can take. What is
     * closed here is which questions there are — {@code answered} is asked for them and never
     * handed the map — and closing that while leaving the answers to whoever asked would let a
     * caller hold a genuine {@link Required} beside answers it wrote itself. A reader outside wants
     * a finished accounting, never a way to make one.
     */
    static RuleAccounting of(RuleRef.Named rule, Required required,
                             Function<Owed, Outcome> answered) {
        return new RuleAccounting(new RuleCitation.Named(rule), required,
                answers(rule, required, answered));
    }

    /**
     * {@code answered} asked once for each question the rule raises, and nothing else.
     *
     * <p>A place nothing classified is asked nothing. There is no question there for a reading to
     * have answered, so asking would be handing a reader a subject this compiler never worked out
     * and taking whatever came back as an answer about it.
     */
    private static Map<Owed, Outcome> answers(RuleRef rule, Required required,
                                              Function<Owed, Outcome> answered) {
        Map<Owed, Outcome> answers = new LinkedHashMap<>();
        for (Owed each : required.obligations()) {
            Outcome outcome = answered.apply(each);
            if (outcome == null) {
                throw new IllegalStateException(
                        "no reading answered for " + each + " of " + rule);
            }
            answers.put(each, outcome);
        }
        return answers;
    }

    /**
     * Which rule of the model, as everything that names a rule names it.
     *
     * <p>Asked of the handle, which is what holds it. A clause, either kind, and never a rule
     * written rather than named: what those raise is answered by the reading that raised it, so
     * there is no accounting of one for anybody to build, and {@link #of} is where the language
     * refuses it.
     */
    public RuleRef.Named rule() {
        return cited.rule();
    }

    /** How a reader finds it, which is not what tells it from another rule. */
    public RuleCitation.Named cited() {
        return cited;
    }

    /** What it raises. */
    public Required required() {
        return required;
    }

    /** What answered each question, keyed by the question. */
    public Map<Owed, Outcome> answers() {
        return answers;
    }

    /**
     * The places nothing worked out what this rule raises at, which is not a question nobody
     * answered.
     *
     * <p>Read off what the rule leaves rather than kept beside it. Nothing was asked about these —
     * there is no question to ask — so they are not among the answers, and a reader that counted
     * the answers would be counting what this compiler managed to classify.
     */
    public Set<Requirement.BoundaryUndetermined> undetermined() {
        return required.undetermined();
    }

    /** The questions nothing answered, which is what a report is about. */
    public Set<Owed> unaccounted() {
        return answers.entrySet().stream()
                .filter(e -> e.getValue() instanceof Outcome.Unaccounted)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    /**
     * The same, each carrying the rule that raised it and what stopped the reading.
     *
     * <p>What a reader downstream is owed. A question with no rule beside it can be reported at a
     * position and never named, which is the sentence #842 is about: an author was told that a rule
     * about the position went unread, with nothing saying which rule.
     */
    public List<Unanswered> unansweredQuestions() {
        return answers.entrySet().stream()
                .filter(e -> e.getValue() instanceof Outcome.Unaccounted)
                .map(e -> new Unanswered(cited, e.getKey(),
                        ((Outcome.Unaccounted) e.getValue()).why()))
                .toList();
    }

    /**
     * One question of one rule that nothing answered, and what it stands for.
     *
     * <p>The reason travels with the question. It is the reading's own account of what stopped it,
     * asked per rule at the subject the question is about — so it is a fact at this question's
     * granularity and not one borrowed from the position, which is what an earlier accounting could
     * not say and left out rather than get wrong.
     *
     * <p>The rule as {@link RuleRef}, all the way to the report that names it. Carried as the
     * clause reference the reading had in hand, the one reader of these built the identity at the
     * last moment — right while only invariants raise a question, and a decision about what a rule
     * is taken by whoever consumed one.
     */
    public record Unanswered(RuleCitation.Named cited, Owed owed, Why why) {

        public Unanswered {
            if (cited == null || why == null) {
                throw new IllegalArgumentException("a question nothing answered is of some rule and"
                        + " stands for a reason");
            }
        }
    }

    @Override
    public String toString() {
        return rule() + " " + answers;
    }

    /** What became of one question. */
    public sealed interface Outcome {

        /**
         * A reading took the rule in and answered this.
         *
         * <p>{@code by} is provenance and nothing else. Which reading answered is worth having for
         * a diagnostic and for anyone working out why an answer is what it is; nothing about
         * whether the model is covered may be read off it, because that is what tying a completeness
         * to the readers there happen to be amounts to.
         */
        record Accounted(Reader by) implements Outcome {

            public Accounted {
                if (by == null) {
                    throw new IllegalArgumentException("a question something answered was answered"
                            + " by one of the readings");
                }
            }
        }

        /**
         * Nothing took the rule in, so the question stands.
         *
         * <p>{@code why} is this compiler's own account of what stopped it, in the vocabulary of the
         * reading that would have answered. What a document writes for it is a projection made
         * elsewhere: a published word reaching back into what a reading is allowed to record is the
         * coupling this whole arrangement is written against.
         */
        record Unaccounted(Why why) implements Outcome {

            public Unaccounted {
                if (why == null) {
                    throw new IllegalArgumentException("a question nothing answered stands for a"
                            + " reason");
                }
            }
        }
    }

    /**
     * What stopped the reading that would have answered, in that reading's own words.
     *
     * <p>One arm per reading, because they do not share a vocabulary and neither is the other's. The
     * reading that turns clauses into sets of values says which form it could not take apart; the
     * reading that turns a clause into an end says what would have to change before the rule could
     * be a line. Held as one word, a line about an end was written in the words of a set of values —
     * which is the sentence #842 is about, one level down.
     *
     * <p><b>Split by reading and never by what a reason is a fact about.</b> One reading is short of
     * a rule in the rule's own words and short of an answer in nobody's, and both of those may hold
     * of one question at once: a rule with a conjunct nothing reads beside a choice whose meet ran
     * past the allowance is short in two ways that different work lifts. An arm per kind of fact
     * would be this type answering a question {@link UnreadReason#about()} already answers, and
     * would make a pair the model reaches unsayable.
     *
     * <p>A question with no account at all is the accounting disagreeing with itself and is refused
     * where it is made ({@link FieldDomains.AStandingQuestionWithNoAccount}).
     */
    public sealed interface Why {

        /**
         * The same, in the one vocabulary this compiler records what it could not do in.
         *
         * <p>Where the two readings' words become one, and the only place they do. A reader
         * downstream is owed what this compiler fell short of and has no business knowing which of
         * its readings was asked — that is provenance, and a document writing a different word for
         * one reading than for another would be reporting an arrangement of readers as a fact about
         * a model.
         *
         * <p>Not the word a document writes, which is {@link souther.compiler.partition
         * .ReportedReason}'s. Two vocabularies with a projection between them is what keeps a
         * published word from reaching back into what a reading is allowed to record.
         *
         * <p>Each said once, and the places they stand on go with them as far as the one thing that
         * reads places ({@link RuleReasons}): two parts one limit stopped are one thing for a
         * reader to lift, and which of two an author is sent to first is settled by where they wrote
         * them and not by where a walk met them. This is the last call that holds the places, so it
         * is the last that could hand them over.
         *
         * <p>Each half through the carrier it belongs in. What a rule is answerable for stands
         * somewhere and what its position's answer was short of stands nowhere, so a list holding
         * both would give a limit the rules ran into a place among the things somebody wrote.
         */
        default WhatAQuestionStandsOn stopped() {
            return switch (this) {
                case TheValueReadingSays it -> new WhatAQuestionStandsOn(
                        RuleReasons.from(it.shortfalls().stream()
                                .map(each -> new RuleReasons.Said(each.site(), sentTo(each),
                                        BlockReason.ofARuleTheValueReadingLeft(each.why())))
                                .toList()),
                        WhatAQuestionStandsOn.oneOf(it.aboutTheAnswer().reasons().stream()
                                .map(BlockReason::ofTheAnswerTheReadingCouldNotBuild).toList()));
                case TheEndReadingSays it -> new WhatAQuestionStandsOn(
                        RuleReasons.one(it.why()), Optional.empty());
            };
        }

        /**
         * Where inside the rule a reader goes about {@code shortfall}.
         *
         * <p>The two kinds of decision a reading makes are two answers here. A part it has no word
         * for is the thing an author rewrites, so the rule is the whole of it. A choice offering an
         * alternative nothing could read is not: the part at the position was read, and what they
         * act on is inside it — sent to the rule, they rewrite a bound that is not the difficulty.
         *
         * <p>The same value the reading of ends hands on for the same choice, so the two accounts
         * of one operator carry one address and a reader holding both knows they are one thing to
         * fix.
         */
        private static RuleSite sentTo(RuleShortfall shortfall) {
            return switch (shortfall.kind()) {
                case LEAF -> RuleSite.theRuleItself();
                case CHOICE -> shortfall.site();
            };
        }

        /**
         * The reading that turns a clause into a set of values.
         *
         * <p>Everything it was stopped by, as facts and not as an order. One position is named by
         * as many parts as the author wrote about it, and two of them stop this reading in two ways
         * that are lifted by different work — so a single reason here would be a choice among an
         * author's rules, and each of these says the written place it was decided at instead.
         *
         * <p><b>What a rule is answerable for and what the answer was short of, together.</b> The
         * two are kept in different places — a reason about a rule is filed under that rule, and a
         * reason about what the rules come to is filed at the position, since no rule is answerable
         * for it — and one question stands on as many of them as hold of it. Held apart here as
         * well, a rule whose form nothing reads and whose position's answer ran past the allowance
         * was reported as the first alone: an author rewrites the form and the position is as wide
         * as it was, for a reason nothing said.
         *
         * <p>Two carriers and one question. Only what a rule is answerable for stands at a place an
         * author wrote, so only that half can be put in the author's order or told from a second of
         * the same shape; what the answer was short of has no place to be asked about and is a set
         * of facts and nothing more ({@link AnswerShortfalls}). A single carrier holding both would
         * be offering a reader a source to look at for the half that has none.
         *
         * <p>The set of shortfalls is held in the order the facts were met, and that order is
         * asserted of nothing: it is what keeps one compiler over one source publishing one
         * document. What {@link #why()} answers with is a projection that loses the places, and
         * where a reader is owed the author's order it is the source that is asked.
         */
        record TheValueReadingSays(Set<RuleShortfall> shortfalls,
                                   AnswerShortfalls aboutTheAnswer) implements Why {

            public TheValueReadingSays {
                if (shortfalls == null || aboutTheAnswer == null) {
                    throw new IllegalArgumentException("a reading that stopped says why");
                }
                if (shortfalls.isEmpty() && aboutTheAnswer.isEmpty()) {
                    throw new IllegalArgumentException("a reading that stopped says why");
                }
                shortfalls = Collections.unmodifiableSet(new LinkedHashSet<>(shortfalls));
            }

            /**
             * What a rule of this is answerable for, as reasons alone.
             *
             * <p>The projection out of the shortfalls and never what is held. Two choices of one
             * rule each offering an alternative nothing could read leave the position open twice
             * and are two things an author can look at; asked as reasons they are one, and which of
             * the two a reader is sent to would be whichever the walk met first.
             *
             * <p>This half and not the other. What the answer was short of is no rule's, so a list
             * holding both would answer a reader asking what a rule is answerable for with a fact
             * about none of them.
             */
            public List<UnreadReason> why() {
                List<UnreadReason> out = new ArrayList<>();
                shortfalls.forEach(each -> {
                    if (!out.contains(each.why())) {
                        out.add(each.why());
                    }
                });
                return List.copyOf(out);
            }
        }

        /**
         * The reading that turns a clause into an end a line can be drawn at.
         *
         * <p>Only reasons that say that reading stopped. A rule it took in from end to end answered
         * the question by being read — where it places no line, there is no line to be owed, and
         * where it restricts no value there is nothing to be admitted. Held as either half, a rule
         * this compiler understood completely was one nobody had accounted for, and the measurement
         * went to partial on the strength of it.
         *
         * <p>Everything it was stopped by, in the order the parts of the clause were met. One
         * question about one line is asked by every conjunct that draws it, and it is answered when
         * every one of them has been read — so a part still standing behind another is a second
         * thing to lift and not a repeat of the first.
         */
        record TheEndReadingSays(FieldDomains.BoundaryStanding standing) implements Why {

            public TheEndReadingSays {
                if (standing == null) {
                    throw new IllegalArgumentException("a reading that stopped says why");
                }
            }

            /** Which limit stopped it, which is one word however many parts are behind it. */
            public BlockReason.RuleReadingStopped why() {
                return standing.why();
            }
        }
    }

    /** Which reading answered a question. */
    public enum Reader {

        /**
         * The reading that turns a rule into a line.
         *
         * <p>One word for it wherever the rule is written. A {@code guard}'s comparison and a
         * newtype's bound are two producers of one kind of evidence (spec §example-partition), and
         * a reader told which of them answered would be holding a fact about this compiler's
         * arrangement of readers rather than about the model.
         */
        THE_END_READING,

        /**
         * A reading that holds what a clause says about the values themselves — which values may
         * stand, what range they run over, what the numbers satisfy.
         *
         * <p>One word for however many of them there are. Which reading answered is provenance;
         * telling them apart here would make the set of readings this compiler happens to have into
         * something a reader downstream can act on, and a reading gained or lost would move what a
         * report says about a model nobody edited.
         */
        THE_VALUE_READING
    }
}
