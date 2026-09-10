package souther.compiler.check;

import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.types.WrittenOwner;

/**
 * Which rule of the model, and nothing about how anybody came to be holding it.
 *
 * <p>One answer per way a rule is written. An author writes a rule as a clause of a {@code data}'s
 * invariant, as a comparison in a body, as a predicate applied in a body, as a fork whose condition
 * is none of those, or as a clause of a behavior's {@code ensures}; a question about coverage is
 * raised by one of those, and what answers it is a fact about that rule and not about the reading
 * that reached it. So this is what a question is filed under, and it is the same value however many
 * times the rule is read.
 *
 * <p><b>A comparison and a predicate are two of them and not one "rule of a body".</b> They divide a
 * position differently — one places a line on an order and the other tells a set of values from the
 * rest — and a reader is shown a different construct for each. Held as one arm, the two would be
 * told apart by looking at what the rule turned out to do, which is the thing a key must not be
 * built out of.
 *
 * <p>Nothing here says where a rule was met. A guard inside a helper is read once per call of that
 * helper: the calls are different occurrences, they carry different comparison sites, and they are
 * one rule. Carrying the occurrence, two readings of one rule are two keys — the question would be
 * raised twice and answered twice, which is a reading raising a question rather than the model, and
 * is what a coverage obligation is written against. Where a rule was met is
 * {@link souther.compiler.partition.LineOrigin}'s, beside this rather than inside it.
 *
 * <p>Nothing here says what a rule <em>did</em>, either. Which side of a line the cut value falls
 * on, whether the comparison holds at it, which arms witness it, which declarations took an end in —
 * those are answers about a boundary, they are the same rule's whichever way they come out, and a
 * key holding them files one rule under several.
 */
public sealed interface RuleRef permits RuleRef.Named, RuleRef.Written {

    /**
     * A rule the author wrote a name beside, which is how a reader finds it.
     *
     * <p>Which of the two ways a rule is found is a property of the kind of rule it is, and this is
     * that property made a type. A clause of an invariant and a clause of an {@code ensures} always
     * have something the author called them; a comparison and a predicate never do. Left implicit,
     * the division was a thing every caller had to know and none could be held to — a citation was
     * free to be built the wrong way round for the rule it was of, and what refused it was a
     * {@code switch} throwing at whichever build first wrote such a rule.
     */
    sealed interface Named extends RuleRef permits Invariant, Ensures {

        /**
         * What a report calls this rule, which is the name the author gave it.
         *
         * <p>A name and not a place. A diagnostic is built where no reader is — nothing there knows
         * what to call a source — so a place written into its text would be a line and a column with
         * no file. A rule with no name is not here at all: it is found by where it is written, which
         * is {@link Written}'s side of the seal.
         *
         * <p>The words of both kinds together, so a reader holding one can be held to them without
         * reading whoever writes the sentence. No {@code default} arm: a kind added to this seal and
         * left without words stops the compile.
         *
         * <p>English, like every other word this writes. What a diagnostic says instead is chosen in
         * the reader's language, from the same rule.
         */
        default String citedName() {
            return switch (this) {
                // What the author called it, and where they called it nothing, which of the
                // declaration's clauses it is — counted from one, as somebody reading the
                // declaration counts them. Two unnamed clauses of one declaration are two rules,
                // and rendered by the declaration alone they are one word twice.
                case Invariant i -> "invariant " + i.clause().id().declaredOn().name()
                        + i.clause().name().map(n -> " (" + n + ")")
                                .orElse(" #" + (i.clause().id().ordinal() + 1));
                // The behavior, and the words that tell one of its rules from another. A clause
                // belongs to a behavior, so there is always something to call it; only a clause
                // stating one rule over every answer has neither a name nor a case, and the
                // behavior's own name is then the whole of it.
                case Ensures e -> "ensures " + e.rule().behavior().name()
                        + (e.clause().isEmpty() ? "" : " (" + e.clause() + ")");
            };
        }
    }

    /**
     * A rule the author wrote rather than named, which is found by where it is written.
     *
     * <p>The other half of the seal, and what tells the two halves apart is what a reader is given
     * to act on. Nothing here has a name to be looked up by, so a report writes what the rule is
     * and where it stands, and the place is one no reader can invent.
     */
    sealed interface Written extends RuleRef permits Comparison, Fork, Predicate {

        /**
         * Which construct of which module the author wrote, which is what tells one of these from
         * every other wherever it is met.
         *
         * <p>On the seal because it is what the half has in common and what a reader of the half
         * asks: where such a rule is written is the writing module's answer, and this is the
         * identity that question is put by ({@code Sites.WhereARuleIsWritten}). Read off each arm
         * instead, every caller would say which kinds it knew about, and a kind added later would
         * be one they silently did not.
         */
        SourceConstructOrigin origin();

        /**
         * What a report calls a rule that has no name, which is one word per kind of them.
         *
         * <p>A word about the rule and not about the construct it stands in. A comparison may stand
         * in the condition of an {@code if} or a {@code guard}, be given a name a line above the
         * fork that tests it, or be what the behavior answers with, and it is one rule in all of
         * those — so a word for the thing around it is a word the rule can lose.
         *
         * <p>One word per kind and not one word over the seal, because the kind is what a reader
         * acts on. Sent to a comparison they are sent to a line on the order the values are counted
         * on and owed a row either side of it; sent to a predicate they are sent to a set told from
         * the rest, where there is no line and no side. That division is the one
         * {@link souther.compiler.partition.RuleEvidenceOrigin} is split along.
         *
         * <p>The words of both kinds together, and no {@code default} arm, for the reason
         * {@link Named#citedName} gives.
         *
         * <p>English, like every other word this writes.
         */
        default String whatItIs() {
            return switch (this) {
                case Comparison _ -> "comparison";
                case Fork _ -> "fork";
                case Predicate _ -> "predicate";
            };
        }
    }

    /**
     * A clause of a {@code data}'s invariant.
     *
     * <p>The clause and not the declaration it is on. Two clauses of one declaration are two rules,
     * and a report owes a line to each ({@link Clause}).
     */
    record Invariant(Clause.Ref clause) implements Named {

        public Invariant {
            if (clause == null) {
                throw new IllegalArgumentException("an invariant's rule is one of its clauses");
            }
        }
    }

    /**
     * A comparison a definition wrote, read for the answer of some behavior.
     *
     * <p>The comparison and not the fork testing it. A condition can be an application of a
     * function parameter, so one predicate handed to two calls is one rule and two predicates
     * written apart are two — neither of which the fork can say. Which fork this comparison was
     * read under is where it was met, and belongs beside this.
     *
     * <p><b>Read off the source and never off a measurement.</b> The two components are what the
     * author wrote: which behavior, and which construct of which module. A run's numbering of
     * comparisons is a fact about instrumentation — a condition both of whose arms can record
     * nothing is not numbered at all — and taking an identity from it says a rule exists because
     * something could be measured about it. A model states its rules whether or not this compiler
     * arranged to watch them.
     *
     * @param behavior whose body it is written in. Two behaviors calling one helper each read its
     *                 comparison, and the readings are what a coverage question is raised per
     * @param origin   which construct of which module the author wrote, which is what tells one
     *                 comparison from another wherever it is met
     */
    record Comparison(String behavior, SourceConstructOrigin origin)
            implements Written {

        public Comparison {
            if (behavior == null || origin == null) {
                throw new IllegalArgumentException("a comparison of a body is one of some behavior's");
            }
            // Which definition wrote it is half of what tells this comparison from every other, and
            // it is held where the value is made rather than where one is published. A comparison of
            // a type's clause or of a behavior's `ensures` is answered for by the clause and the arm
            // it is in and never reaches here; one an analysis rebuilt was written by nobody.
            if (!(origin.owner() instanceof WrittenOwner.Body)) {
                throw new IllegalStateException("a comparison of a body was written by a"
                        + " definition, and this was written by " + origin.owner());
            }
        }

        /** The definition whose body wrote the comparison — not the behavior reading it, which a
         *  helper's comparison has one of per caller. */
        public WrittenOwner.Body writtenIn() {
            return (WrittenOwner.Body) origin.owner();
        }
    }

    /**
     * A fork a definition wrote, for the part of what it tests that states no rule anything read.
     *
     * <p>The fork itself and not what it tests, which is where this parts from {@link Comparison}.
     * That one is the rule and the fork around it is where the rule was met; here there is no such
     * rule to be met — the condition is a value the analysis keeps standing, or a shape no reading
     * takes apart — and what the author wrote is a fork all the same. A model that forks states
     * something about its input by forking, so the question the fork raises is raised whether or not
     * anything worked out what it says.
     *
     * <p><b>Only for the parts of a condition nothing else answers for.</b> {@code if x > 0} states
     * its rule as a comparison, and that comparison is what a question about it is filed under; a
     * fork there as well would be one construct raising two questions and a report telling a reader
     * twice. A condition is several things at once — {@code a > 0 && List.isEmpty(xs)} states a
     * comparison and something nothing read — so one fork may stand beside a comparison of the same
     * condition, each answering for its own part of it.
     *
     * <p><b>And only about the input.</b> Where no part of what such a fork tests names a position,
     * the fork states nothing about the input and is no rule of it — which is the answer a
     * comparison of the same shape already gets. Where a part does name one, the question is filed
     * there and not at a number of it: which number of the position the fork is about is exactly
     * what went unread ({@link souther.compiler.inputs.FilingCoordinate.AtPosition}).
     *
     * @param behavior whose body it is written in, as a comparison's is: two behaviors calling one
     *                 helper each read its fork, and the readings are what a question is raised per
     * @param origin   which construct of which module the author wrote, which is what tells one fork
     *                 from another wherever it is met
     */
    record Fork(String behavior, SourceConstructOrigin origin) implements Written {

        public Fork {
            if (behavior == null || origin == null) {
                throw new IllegalArgumentException("a fork of a body is one of some behavior's");
            }
            // A rule is something an author wrote. A fork this compiler composed — the arm a
            // `guard` supplies, a lowering's test — states nothing about the model, and a question
            // filed under one would send a reader to a construct they never wrote.
            if (!origin.isWritten()) {
                throw new IllegalArgumentException(
                        "no source wrote this fork, so it states no rule: " + origin);
            }
            // Written by a definition's body, which is the only place a fork stands. A behavior's
            // own clauses state their rules as clauses and are answered for as such; an ordinal
            // means nothing outside what it was counted in, so what may be held here is said here.
            if (!(origin.owner() instanceof WrittenOwner.Body)) {
                throw new IllegalStateException("a fork of a body was written by a definition,"
                        + " and this was written by " + origin.owner());
            }
        }

        /** The definition whose body wrote the fork — not the behavior reading it, which a helper's
         *  fork has one of per caller. */
        public WrittenOwner.Body writtenIn() {
            return (WrittenOwner.Body) origin.owner();
        }
    }

    /**
     * A predicate a behavior applies — a rule about the strings at a position, written as a call
     * rather than as a comparison.
     *
     * <p>The application and the whole of it, the way a comparison is. Which predicate it is and
     * what the author wrote in it are read off the call by whoever reads rules; what tells one of
     * these from another is which application of which source it is, so that a helper holding one
     * is the same rule at each of its calls.
     *
     * <p><b>Written in a body or in a clause.</b> Which of the two is not narrowed here, which is
     * where this parts from {@link Comparison}: a behavior states such a rule about one of its
     * inputs in an {@code ensures} as much as in a condition, and what the position is told apart
     * into is what the two come to between them. A predicate held to a body would be a rule of the
     * model with nowhere to be filed.
     *
     * @param behavior whose rule it is. Two behaviors calling one helper each read its predicate,
     *                 and the readings are what a coverage question is raised per
     * @param origin   which application of which module the author wrote, which is what tells one
     *                 from another wherever it is met
     */
    record Predicate(String behavior, SourceConstructOrigin origin)
            implements Written {

        public Predicate {
            if (behavior == null || origin == null) {
                throw new IllegalArgumentException(
                        "a predicate of a body is one of some behavior's");
            }
            // A rule is something an author wrote. An application this compiler composed states
            // nothing about the model, so a key made from one would file a rule under a construct
            // no reader can be sent to.
            if (!origin.isWritten()) {
                throw new IllegalArgumentException(
                        "no source wrote this application, so it states no rule: " + origin);
            }
            // And written in one of the two places a behavior states such a rule. Held here rather
            // than left to the doc: an ordinal means nothing outside what it was counted in, so
            // what publishes one of these has to say which owner — and it can only be asked to say
            // that for the owners this may hold.
            if (!(origin.owner() instanceof WrittenOwner.Body)
                    && !(origin.owner() instanceof WrittenOwner.Stated)) {
                throw new IllegalArgumentException("a behavior states a rule about its strings in"
                        + " its body or in its own clauses, and this was written by "
                        + origin.owner());
            }
            // And a rule the behavior's own clauses state is that behavior's. The two are one fact
            // written twice — the owner is the behavior stating it, and `behavior` is whose rule it
            // is — so they are held together here rather than left to agree. A document publishes
            // the name once because they are one name; two that disagreed would be two rules under
            // one identity, and nothing downstream could tell which of them it had.
            if (origin.owner() instanceof WrittenOwner.Stated stated
                    && !stated.behavior().equals(behavior)) {
                throw new IllegalArgumentException("a rule stated in a behavior's own clauses is"
                        + " that behavior's, and this is " + behavior + "'s written by "
                        + stated.behavior());
            }
        }

        /**
         * What wrote the application — a definition's body, or the behavior's statement of itself.
         *
         * <p>Part of what tells this rule from every other, because {@link #origin}'s ordinal is
         * counted within it: a behavior's first clause predicate and its body's first are two
         * constructs and both are numbered zero. A reader given the number alone has an identity
         * two rules answer to.
         */
        public WrittenOwner writtenIn() {
            return origin.owner();
        }
    }

    /**
     * A clause of a behavior's {@code ensures}.
     *
     * @param rule   which rule of which clause, which is what tells one from another. Two arms of
     *               one clause may name the same case, so the author's words for it are not enough
     * @param clause the author's words for it: the name they gave the clause, or the case the arm
     *               names where they gave none, or empty where the clause states one rule over every
     *               answer. Part of the identity as well as the source of
     *               {@link Named#citedName}'s words for this rule — two of these agreeing on
     *               {@code rule} and differing here are two values and not one, so this is not
     *               presentation a reader may drop from the equality
     */
    record Ensures(BehaviorContract.RuleId rule, String clause) implements Named {

        public Ensures {
            if (rule == null) {
                throw new IllegalArgumentException("an ensures rule belongs to a behavior");
            }
            // Absent is spelled as the empty string, which is what {@link #named} reads. A clause
            // stating one rule over every answer is called by the behavior's name alone, and that
            // is an answer about the clause rather than the absence of one.
            if (clause == null) {
                throw new IllegalArgumentException(
                        "a clause the author named nothing is named by nothing, not by null");
            }
        }
    }
}
