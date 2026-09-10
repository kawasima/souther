package souther.compiler.inputs;

import souther.compiler.check.CoverageObligation;
import souther.compiler.check.RuleCitation;
import souther.compiler.check.RuleRef;
import souther.compiler.check.RuleReportAnchor;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * A rule of the model that leaves a measure of coverage open, as everything past the input boundary
 * carries it.
 *
 * <p><b>What tells them apart is how far the reading of the rule got.</b> Where this compiler
 * worked out what the rule raises, the question names that obligation and the subject it is about
 * ({@link Exact}). Where it worked out which obligation was left undecided, the question says which
 * one and holds that measure alone open ({@link BoundaryUndetermined}). Where nothing worked out
 * what the rule raises at all, the question says exactly that and invents neither obligation nor
 * subject ({@link NothingClassifiesIt}). All of them hold a measure open, and none is another's
 * answer: a reader told that a question about a position stands is told which position and what
 * about it, and one told that a rule's reading did not finish is told to go and look at the rule.
 *
 * <p>Written as one, the second would have to wear the first's clothes — a subject it does not have
 * and an obligation nobody worked out — or travel outside the questions altogether, as a flag on
 * why a reading stopped that says which measures are thereby short of something. Either way what
 * holds a measure open is decided somewhere other than by a question about a rule.
 */
public sealed interface StandingQuestion {

    /** Which rule of the model left it, as everything that names a rule names it. */
    RuleRef rule();

    /**
     * How a reader finds that rule, which is not what tells it from another.
     *
     * <p>Every handle offered, because a rule found by two readers is one thing each of them says
     * how to reach; which of them a document writes is that document's to decide.
     */
    Set<RuleCitation> cited();

    /**
     * What this compiler was short of, which is why the measure stays open.
     *
     * <p>In its own terms and not a document's, so a reader out here is told what was missing
     * without a published word reaching back into what a reading may record; which word a document
     * writes for one of these is {@link ReportedReason}'s.
     *
     * <p>Which rule raised the question is {@link #rule()}, and what is short is this. The two are
     * different facts: a rule read from end to end whose position's values were never worked out
     * leaves its question standing on a limit no rule is answerable for, so a list that promised a
     * rule to name for each of these would have to invent one. What every entry does promise is
     * that a reading fell short — a rule read to the end that draws no line leaves nothing standing
     * and belongs to whoever asks about lines.
     *
     * <p><b>Two facts and not one sequence.</b> What the parts of the rule left is in the order
     * they were written; what its position's answer was short of belongs to no part of the rule and
     * has no place among them. {@link WhatAQuestionStandsOn} is what holds them apart, and the one
     * order there is lives inside it — so nothing here can be read as a precedence between the two,
     * because there is none to read.
     */
    WhatAQuestionStandsOn stopped();

    /**
     * Whether {@code measure} stays open while this stands.
     *
     * <p>Here, where the question is, and read by everything that has to know: the measure that
     * says whether its reading ran out, and the document that files the question under a section.
     * Written twice, the day a question is added is the day one of them files it somewhere and the
     * other silently answers for it.
     *
     * <p>A question about a subject is the business of whichever measure answers that subject's
     * kind. A rule nothing classified may have divided the position or bounded it, and neither
     * measure knows which, so both stay open — which is what such a question says and not a flag
     * anybody set beside it.
     *
     * <p><b>Two switches and no {@code default} on either.</b> A measure added fails the inner
     * switch and a question added fails the outer, and whichever axis grows has to be answered for.
     * Answered with a set of measures, a measure added later would be one every question had
     * silently answered "closed" — a new measure inheriting what the others happened to share.
     */
    default boolean holdsOpen(CoverageObligation.Measure measure) {
        return switch (this) {
            case Exact it -> it.obligation().answeredBy() == measure;
            // The measure that answers where a line falls, and only that one. What is undecided is
            // whether the rule places an end, so which values may stand there is settled and the
            // classes rest on nothing here — held open in both, a rule undecided about its line
            // would keep them open as well.
            //
            // Per measure and no `default`, so a third has to be answered for.
            case BoundaryUndetermined _ -> switch (measure) {
                case PARTITION -> false;
                case BOUNDARY -> true;
            };
            // And a comparison the reading did not finish, which is undecided about what it does at
            // all. A line it comes to divides the position and owes the rows either side, so both
            // measures rest on a reading that stopped — asked per measure rather than answered with
            // a set of them, so a third has to be answered for here.
            case NothingClassifiesIt _ -> switch (measure) {
                case PARTITION -> true;
                case BOUNDARY -> true;
            };
        };
    }

    /**
     * What makes two of these one, with the handles left out.
     *
     * <p>Each arm's own, because they are not identified alike, and neither may be compared with
     * the other's: two values of this that are equal are the same rule leaving the same thing open,
     * and an arm's identity is what its own declaration says.
     */
    Object fact();

    /**
     * Both accounts of one of these, as one: the same fact, with every handle either offered.
     *
     * <p>Of one fact, and it says so rather than taking the caller's word — two that are not one
     * fact would come out as one of them cited where the other was. Two arms are never one, however
     * much else they have in common.
     */
    StandingQuestion mergedWith(StandingQuestion other);

    /**
     * One question a rule of the model raises that nothing answered.
     *
     * <p>The other side of {@code check.RuleAccounting.Unanswered}, which is the same question in
     * the vocabulary of the declaration whose clauses raised it. What crosses is what a reader out
     * here asks: which rule, how a reader finds it, and what the question is about.
     *
     * <p><b>The obligation it came from is not carried beside it.</b> Nothing downstream reads
     * anything of it that is not here — the rule, the citation, and what it asks — and holding it
     * would leave a second identity for the same question reachable, so that every comparison
     * downstream had two answers to choose between. That choice is what the crossing exists to take
     * away.
     *
     * @param fact    which rule raised it and what it asks, which is what tells one question from
     *                another ({@link Fact})
     * @param reachedBy every question a reader was offered for that rule, empty exactly where the
     *                author named it and it is found by that name from anywhere
     * @param stopped every reason it stands. A question is answered when every part that asked it
     *                has been read, so a part standing behind another is a second thing to lift.
     *                What the parts of the rule left keeps the order the author wrote them in and
     *                is neither joined with another reading's nor chosen between; what its
     *                position's answer was short of stands beside them under no order at all
     *                ({@link WhatAQuestionStandsOn})
     */
    record Exact(Fact fact, Set<RuleReportAnchor> reachedBy,
                 WhatAQuestionStandsOn stopped) implements StandingQuestion {

        public Exact {
            if (fact == null) {
                throw new IllegalArgumentException("a standing question names a rule and what it"
                        + " asks");
            }
            reachedBy = reachedBy == null ? Set.of() : Set.copyOf(reachedBy);
            RuleCitation.requireReached(fact.rule(), reachedBy);
            if (stopped == null) {
                throw new IllegalArgumentException(
                        "a question stands because something was short of it");
            }
        }

        /** One reader's account of it, as that reader produced it. */
        public static Exact of(RuleCitation cited, InputQuestion asks,
                               WhatAQuestionStandsOn stopped) {
            return new Exact(new Fact(cited.rule(), asks), RuleCitation.anchorOf(cited), stopped);
        }

        @Override
        public RuleRef rule() {
            return fact.rule();
        }

        @Override
        public Set<RuleCitation> cited() {
            return RuleCitation.handlesFor(fact.rule(), reachedBy);
        }

        /** What it asks and what it asks it about. */
        public InputQuestion asks() {
            return fact.asks();
        }

        /** What it asks, which follows from what it is about. */
        public CoverageObligation obligation() {
            return asks().obligation();
        }

        /**
         * <p>What the parts of the rule left is the author's order over them, which is one answer
         * about the model — so two accounts that disagree about it are two accounts one of which is
         * wrong, and taking either would publish a precedence nothing in the model decides.
         *
         * <p><b>What the position's answer was short of is asked of neither.</b> It is a fact about
         * what the rules of that position come to between them, so two accounts of one rule can
         * honestly differ about it: a reading whose neighbours left the answer buildable met no
         * such limit, and one reached where they did. Held to agreement, a model somebody could
         * write refuses to compile because two readings of one rule found different things about a
         * position neither of them is answerable for. So it is taken from whichever met it, and two
         * that met different ones are disagreeing about the position and are refused there.
         *
         * <p>Compared as the two, and never as one sequence. A difference in the order across the
         * halves is no disagreement about anything the model says — there is no order there — so a
         * comparison of the joined lists would refuse a pair of accounts over a precedence neither
         * of them claimed.
         */
        @Override
        public StandingQuestion mergedWith(StandingQuestion other) {
            if (!(other instanceof Exact it)) {
                throw new IllegalArgumentException("a question that asks something and " + other
                        + " are not two accounts of one thing");
            }
            if (!fact.equals(it.fact)) {
                throw new IllegalArgumentException("two accounts put together are of one question: "
                        + fact + " and " + it.fact);
            }
            if (!stopped.itsRuleLeft().equals(it.stopped.itsRuleLeft())) {
                throw new TwoAccountsOfOneQuestion(fact, stopped, it.stopped);
            }
            Optional<BlockReason.AnswerRealizationStopped> mine = stopped.itsPositionWasShortOf();
            Optional<BlockReason.AnswerRealizationStopped> theirs =
                    it.stopped.itsPositionWasShortOf();
            if (mine.isPresent() && theirs.isPresent() && !mine.equals(theirs)) {
                throw new TwoAccountsOfOneQuestion(fact, stopped, it.stopped);
            }
            Optional<BlockReason.AnswerRealizationStopped> answer =
                    mine.isPresent() ? mine : theirs;
            Set<RuleReportAnchor> both = new HashSet<>(reachedBy);
            both.addAll(it.reachedBy);
            return new Exact(fact, both,
                    new WhatAQuestionStandsOn(stopped.itsRuleLeft(), answer));
        }

        @Override
        public String toString() {
            return obligation() + " at " + asks();
        }

        /**
         * What makes two of these one question: which rule raised it, and what it asks.
         *
         * <p>Both of the others are left out, and each says so where it is declared. The citation
         * is how a reader finds the rule and not what tells it from another. What the question is
         * short of is why it stands rather than which question it is.
         */
        public record Fact(RuleRef rule, InputQuestion asks) {

            public Fact {
                if (rule == null || asks == null) {
                    throw new IllegalArgumentException("a standing question names a rule and what"
                            + " it asks");
                }
            }
        }
    }

    /**
     * A rule this compiler did not read far enough to say what it raises.
     *
     * <p>No obligation and no subject, because neither was worked out. {@code Decimal.compare(a, b)
     * <= 0} says what it says whether or not this compiler can follow it back through the
     * operation; what it costs a measure of coverage is what nothing here determined. Given an
     * obligation, such a rule would be reported as one nothing answered — a claim about the model —
     * and given a subject it would name whichever position the walk was passing when it stopped.
     *
     * <p><b>Which is why the place is where this is filed and never what it is about.</b> A reading
     * that stopped has no quantity for its places to be one subject of, so each of them is a place
     * a reader is sent to look ({@link FilingCoordinate}) and a position's own account of what its
     * rules came to is read off them. What the rule divides is not.
     *
     * <p>Both measures stay open while one of these stands, and that follows from what it says: a
     * rule nobody could classify may have divided the position or bounded it, and neither measure
     * knows which. Where a measure's own answer is written is {@code MeasureClosure}'s, which asks
     * the question rather than the reason.
     *
     * <p>What makes two of these one is the whole of which rule it is, where it was filed and what
     * stopped the reading. How a reader finds it is a separate thing — a name where the author gave
     * one, a place where they did not — and is not part of that identity.
     */
    sealed interface Unclassified extends StandingQuestion {

        /** Where a reader is sent to look, which is not what the rule is about. */
        FilingCoordinate at();

        /** What stopped the reading, in this compiler's own terms. */
        BlockReason.RuleReadingStopped why();
    }

    /**
     * A rule nothing works out the questions of, whose reading did not finish.
     *
     * <p>What a body's comparison and a clause of an {@code ensures} have in common: neither is
     * classified anywhere. A line a comparison comes to owes the rows either side by having been
     * read, and a clause states a relation the behavior is held to — so no obligation of either
     * ever stands against an answer, and there is nothing to have been decided question by
     * question. Where the reading of one stopped, what is undecided is what the rule does at all,
     * and both measures rest on it.
     *
     * <p>Beside {@link BoundaryUndetermined} and not among it. There a reading did classify the
     * rule and one of its questions came out undecided, which names that question and holds open
     * only the measure answering it.
     */
    record NothingClassifiesIt(NothingClassifiesIt.Filed filed,
                                  Set<RuleReportAnchor> reachedBy) implements Unclassified {

        public NothingClassifiesIt {
            if (filed == null) {
                throw new IllegalArgumentException(
                        "a rule nothing classified is one rule, filed somewhere, for a reason");
            }
            reachedBy = reachedBy == null ? Set.of() : Set.copyOf(reachedBy);
            RuleCitation.requireReached(filed.rule(), reachedBy);
        }

        /** One reader's account of it, as that reader produced it. */
        public static NothingClassifiesIt of(RuleCitation cited, FilingCoordinate at,
                                             BlockReason.RuleReadingStopped why) {
            return new NothingClassifiesIt(
                    new NothingClassifiesIt.Filed(cited.rule(), at, why),
                    RuleCitation.anchorOf(cited));
        }

        @Override
        public RuleRef rule() {
            return filed.rule();
        }

        @Override
        public Set<RuleCitation> cited() {
            return RuleCitation.handlesFor(filed.rule(), reachedBy);
        }

        /** Where a reader is sent to look, which is not what the rule is about. */
        @Override
        public FilingCoordinate at() {
            return filed.at();
        }

        /** What stopped the reading, in this compiler's own terms. */
        @Override
        public BlockReason.RuleReadingStopped why() {
            return filed.why();
        }

        /** The one reason, as the list every one of these is read through. */
        @Override
        public WhatAQuestionStandsOn stopped() {
            return new WhatAQuestionStandsOn(RuleReasons.one(filed.why()), Optional.empty());
        }

        @Override
        public Object fact() {
            return filed;
        }

        @Override
        public StandingQuestion mergedWith(StandingQuestion other) {
            if (!(other instanceof NothingClassifiesIt it)) {
                throw new IllegalArgumentException("a rule nothing classified and " + other
                        + " are not two accounts of one thing");
            }
            if (!filed.equals(it.filed)) {
                throw new IllegalArgumentException("two accounts put together are of one rule: "
                        + filed + " and " + it.filed);
            }
            Set<RuleReportAnchor> both = new HashSet<>(reachedBy);
            both.addAll(it.reachedBy);
            return new NothingClassifiesIt(filed, both);
        }

        @Override
        public String toString() {
            return "unclassified " + filed.rule() + " at " + filed.at();
        }

        /**
         * Which rule, where it was filed and what stopped the reading, which is the whole of what
         * makes two of these one.
         *
         * <p>The citation is no part of it, for the reason {@link Exact.Fact} gives. The reason is:
         * two limits met at one place are two things for a reader to lift, and one rule filed under
         * the first of them would leave the second unsaid.
         */
        public record Filed(RuleRef rule, FilingCoordinate at,
                            BlockReason.RuleReadingStopped why) {

            public Filed {
                if (rule == null || at == null || why == null) {
                    throw new IllegalArgumentException(
                            "a rule nothing classified is one rule, filed somewhere, for a reason");
                }
            }
        }
    }

    /**
     * A rule of a declaration nothing worked out whether it puts an end at.
     *
     * <p>Beside {@link NothingClassifiesIt} and not among it, because what is undecided is a
     * different size. A clause is classified question by question — {@code Decimal.compare(total,
     * subtotal) <= 0} restricts what may stand at {@code total}, and whether it also puts a line
     * there is what inverting the operation would answer — so only the border measure rests on this
     * and the classes are settled beside it.
     *
     * <p>Named for the one question a classification comes out undecided about. Whether a rule
     * restricts the values at a name is settled by its writing one, so there is no such thing here
     * as an undecided admitted-values question; the day there is, it arrives as an arm of its own
     * and everything that reads one of these has to say what it does about it.
     *
     * @param filed which rule, where it was filed and what stopped the reading
     * @param reachedBy every question a reader was offered for that rule, empty exactly where the
     *                  author named it
     */
    record BoundaryUndetermined(BoundaryUndetermined.Filed filed,
                                Set<RuleReportAnchor> reachedBy) implements Unclassified {

        public BoundaryUndetermined {
            if (filed == null) {
                throw new IllegalArgumentException("a question nothing worked out is one question,"
                        + " of one rule, filed somewhere");
            }
            reachedBy = reachedBy == null ? Set.of() : Set.copyOf(reachedBy);
            RuleCitation.requireReached(filed.rule(), reachedBy);
        }

        /** One reader's account of it, as that reader produced it. */
        public static BoundaryUndetermined of(RuleCitation cited, FilingCoordinate at,
                                              BlockReason.RuleReadingStopped why) {
            return new BoundaryUndetermined(
                    new BoundaryUndetermined.Filed(cited.rule(), at, why),
                    RuleCitation.anchorOf(cited));
        }

        @Override
        public RuleRef rule() {
            return filed.rule();
        }

        @Override
        public Set<RuleCitation> cited() {
            return RuleCitation.handlesFor(filed.rule(), reachedBy);
        }

        @Override
        public FilingCoordinate at() {
            return filed.at();
        }

        @Override
        public BlockReason.RuleReadingStopped why() {
            return filed.why();
        }

        @Override
        public WhatAQuestionStandsOn stopped() {
            return new WhatAQuestionStandsOn(RuleReasons.one(filed.why()), Optional.empty());
        }

        @Override
        public Object fact() {
            return filed;
        }

        @Override
        public StandingQuestion mergedWith(StandingQuestion other) {
            if (!(other instanceof BoundaryUndetermined it)) {
                throw new IllegalArgumentException("a question nothing worked out and " + other
                        + " are not two accounts of one thing");
            }
            if (!filed.equals(it.filed)) {
                throw new IllegalArgumentException("two accounts put together are of one question: "
                        + filed + " and " + it.filed);
            }
            Set<RuleReportAnchor> both = new HashSet<>(reachedBy);
            both.addAll(it.reachedBy);
            return new BoundaryUndetermined(filed, both);
        }

        @Override
        public String toString() {
            return "undetermined BOUNDARY of " + filed.rule() + " at " + filed.at();
        }

        /** Which rule, where and what stopped it — the whole of what makes two of these one, for
         *  the reason {@link Exact.Fact} gives about the citation. */
        public record Filed(RuleRef rule, FilingCoordinate at,
                            BlockReason.RuleReadingStopped why) {

            public Filed {
                if (rule == null || at == null || why == null) {
                    throw new IllegalArgumentException("a question nothing worked out is one of"
                            + " one rule, filed somewhere, for a reason");
                }
            }
        }
    }
}
