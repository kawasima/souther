package souther.compiler.query;

import souther.compiler.partition.ReadingGap;
import souther.compiler.publish.CanonicalSelection;
import souther.compiler.publish.PublicationOrders;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * What became of one obligation the model owes a row at.
 *
 * <p>Derived from the evidence and never held beside it. What the readings came to
 * ({@link ObligationCoverage}) and what has shown a row can be written here
 * ({@link ItemAssessment.WritabilityEvidence}) are answers to two questions, and this is the one
 * question a count, a finding and a build's refusal all put: is this row written, owed, or neither.
 * Asked of the evidence at each of those places instead, the three had three chances to read one
 * pair of answers differently.
 *
 * <p><b>Three states, and every one of them is counted.</b> Whether a row is owed at a point is the
 * model's answer and is settled before anything here: a border that owes no row at a point says so
 * with a reason read off the rules ({@link souther.compiler.partition.NotOwedReason}), and a point
 * the rules leave nothing at never becomes an obligation. Nothing this compiler failed to read,
 * compose or represent reaches that decision, so nothing here subtracts. What the three say is what
 * is known about a point that is owed: a row stands at it, no row does and something showed one
 * could, or nobody can say which.
 *
 * <p>There was a fourth. A point every row was read against and none was at, with nothing to show a
 * row could be written there, was left out of the account entirely — on the reading that asking for
 * a row nothing promises is asking for work nobody can do. That reads a limit of this compiler as an
 * answer about the model. Nothing composed and every candidate refused are facts about the composer;
 * the model's own refusal has its own way of being said, one paragraph up, and is not any of them.
 * What the fourth state cost is that a field nobody could compose a value for took its siblings'
 * obligations out of the denominator with it — the row is written against the whole value, so one
 * unreadable rule anywhere under a parameter emptied the grounds for every point beneath it
 * (issue #1249).
 *
 * <p><b>And an open question carries what it is open on.</b> A name for the question alone sends
 * every reader back to the evidence beside it to work out what to say, and each of them works it
 * out on its own terms — so what one point is open on is as many answers as there are readers
 * ({@link Uncertainty}). Which is also why the two questions are families rather than names: a
 * reading that stopped short and no reading at all are one question open for two reasons, and so
 * are a showing a budget ended and a showing nothing ever made.
 */
public sealed interface ObligationDisposition {

    /** A row this compilation observed stands at the point. */
    record Met() implements ObligationDisposition {}

    /**
     * No row is at the point, the readings ran to the end, and something has shown a row can be
     * written there. The one state a finding is made of and a build can be told to refuse over.
     */
    record Unmet() implements ObligationDisposition {}

    /**
     * No row was seen, and something this compiler could not do is why nobody can say more.
     *
     * <p>Counted and never a finding. Whether a row is at the point is what nobody can say, so an
     * author told to write one may be told to write one they have written; and left out of the
     * count, an obligation the rows may already answer would go unsaid.
     *
     * <p><b>Two questions can be the one nobody can answer, and they are told apart.</b> A reading
     * that could have been holding a row did not run to the end; or the rows were read to the end
     * and it was the showing that a row can be written here that was stopped. Both leave the point
     * undecided and they leave different work: the first is answered by reading more of what is
     * written, the second by this compiler being able to keep more of what it builds. Held as one
     * state, a reader is told a verdict and left to work out which question it is about — from the
     * verdict, which is the one thing that does not say.
     *
     * <p>Both, because one obligation can be both, and either one alone would be a choice of which
     * to tell. At most one of each, and in the order they are published in
     * ({@link PublicationOrders#OPEN_QUESTIONS}): what a reader is shown cannot come out of the
     * order a fold happened to put them in.
     */
    record Undecided(CanonicalSelection<Uncertainty> because) implements ObligationDisposition {

        public Undecided {
            if (because == null || because.isEmpty()) {
                throw new IllegalArgumentException(
                        "an obligation nobody can decide says which question is open");
            }
        }

        /** The questions that are open about one obligation, in the order they are said in. */
        public static Undecided about(Collection<Uncertainty> open) {
            return new Undecided(PublicationOrders.OPEN_QUESTIONS.keep(open));
        }
    }

    /**
     * Which question about an obligation is the one nothing answered, and what it is open on.
     *
     * <p>The answer travels with the question. A name for the question alone sends every reader
     * back to the evidence beside it to work out what to say, and each of them works it out on its
     * own terms — so what one point is open on is as many answers as there are readers, and which
     * of them a document says comes out of which reader wrote that line.
     */
    sealed interface Uncertainty {

        /**
         * Which of the questions this is.
         *
         * <p>Said by the value and not worked out from the class it arrived in. A question is a
         * family — whether a row is there is open where a reading stopped and where nothing was
         * read — so a reader that keyed on the concrete answer would hold two things where the
         * order and the document hold one, and would be deciding which question a point is open on
         * from what happened to leave it open.
         */
        Class<? extends Uncertainty> question();

        /**
         * Whether a row that is written stands at the point: a reading of the rows stopped short.
         *
         * <p>With what the readings met, each reason once ({@link ReadingReasons}). Which reading
         * met which is no part of this: the readings are named under the point, one to a line, and
         * an answer that paired them would be answering about a reading in the sentence about the
         * line.
         */
        sealed interface WhetherARowIsThere extends Uncertainty {

            @Override
            default Class<? extends Uncertainty> question() {
                return WhetherARowIsThere.class;
            }

            /**
             * A reading that could have been holding a row did not run to the end, and this is what
             * the readings met.
             *
             * <p>Each reason once ({@link ReadingReasons}). Which reading met which is no part of
             * this: the readings are named under the point, one to a line, and an answer that
             * paired them would be answering about a reading in the sentence about the line.
             */
            record ReadingsStopped(ReadingReasons met) implements WhetherARowIsThere {

                public ReadingsStopped {
                    Objects.requireNonNull(met, "a reading that stopped short says what it met");
                }
            }

            /**
             * Nothing was read against the point at all, and this is why nobody looked.
             *
             * <p>The other half of the same question, and it is open for a reason of the build's
             * rather than of a reading's: no row names the behavior, or this build asked for no
             * measurement over rows. The obligation is the model's either way — what the rows were
             * not read against is not something the model stopped owing (issue #1249) — so the
             * question is open and says which of the two left it so.
             *
             * <p>Every reason that leaves the point unmeasured, as the coverage holds them. A count
             * and a build's refusal read this beside the document, and asking for one reason here
             * would put the refusal of an account too wide for a sentence in front of all three —
             * where what is too wide is the sentence, which is written somewhere else.
             */
            record NothingWasRead(UnaskedReasons why) implements WhetherARowIsThere {

                public NothingWasRead {
                    Objects.requireNonNull(why, "a point nobody read against says why nobody did");
                }
            }
        }

        /**
         * Whether a row can be written at the point: the showing of it stopped short, and this is
         * what stopped it.
         */
        sealed interface WhetherARowCanBeWritten extends Uncertainty {

            @Override
            default Class<? extends Uncertainty> question() {
                return WhetherARowCanBeWritten.class;
            }

            /** The showing of it stopped short, and this is what stopped it. */
            record Stopped(WritabilityKnowledge.Prevented by) implements WhetherARowCanBeWritten {

                public Stopped {
                    Objects.requireNonNull(by, "a showing that was stopped says what stopped it");
                }
            }

            /**
             * Nothing showed a row can be written here, and nothing was stopped from showing it.
             *
             * <p>Nothing composed, or every value composed was refused — both of them things this
             * compiler did, and neither of them the model refusing a row. What the model refuses is
             * said where a border declines to owe the point at all, so an absence here leaves the
             * obligation exactly where it was and leaves this question open (issue #1249).
             *
             * <p>No payload, and that is the answer rather than a field nobody filled: what was
             * tried is the point's own to say, one line per reading, and a copy here would be a
             * second account of the same searches.
             */
            record NothingShowedIt() implements WhetherARowCanBeWritten {}
        }
    }

    /**
     * What the readings of a point met, out of what the measurement of it went without.
     *
     * <p>The one crossing from an accounting of facts to what a reader is told. What weakened the
     * measurement is keyed on the border each reading was made at, because a module counting what
     * it could not read counts one fact per line; this point's own explanation is not, because the
     * readings are named under it and a clause said once per reading counts the paths a fact
     * arrived by.
     *
     * <p>Exhaustive, with no {@code default}. A way of weakening a measurement added is a compile
     * error here rather than one that silently says nothing about a point it leaves open — the
     * arms that contribute nothing say so because their reason is written where it happened, and a
     * new arm has to be put on one side or the other before it can be built.
     */
    private static ReadingReasons whatTheReadingsMet(WeakeningSet by) {
        return ReadingReasons.of(readingGapsIn(by));
    }

    /** The gaps the readings met, as they were met. */
    private static List<ReadingGap> readingGapsIn(WeakeningSet by) {
        List<ReadingGap> met = new ArrayList<>();
        for (Weakening each : by.causes()) {
            switch (each) {
                // The reading's own reason, and the only one this sentence carries.
                case Weakening.BorderValueUnreadable it -> met.add(it.why());
                // Said where it happened. A row that never ran, a body nothing elaborated, a
                // boundary nothing derived and an input nothing read are each one fact about a
                // behavior or a module, and every line under it is short of that same one thing —
                // so a sentence about one point that repeated them would say of this line what is
                // true of all of them, and say it once per line.
                case Weakening.ObservationIncomplete _,
                     Weakening.OutputCasesUnreadable _,
                     Weakening.InputCasesUnreadable _,
                     Weakening.ModelReadingIncomplete _,
                     Weakening.BodiesNotElaborated _,
                     Weakening.BoundaryNotDerived _,
                     Weakening.InputNotRead _,
                     Weakening.PairSpaceTruncated _,
                     Weakening.ProofContradicted _,
                     Weakening.ArmsUnsettled _,
                     // A run this compiler could not place among the rules of a decision says
                     // nothing about a point of a line: which rule a row took and where its value
                     // stands are two questions, and this sentence is about the second.
                     Weakening.DecisionOfRowUnreadable _,
                     // The same of a run nothing watched at all, which is short of the run rather
                     // than of the rules and is about neither of this sentence's questions.
                     Weakening.DecisionRunNotWatched _,
                     // And a decision whose ways could not all be written down, which is about
                     // what obligations there are and not about a point of a line.
                     Weakening.DecisionReadingIncomplete _ -> { }
            }
        }
        return met;
    }




    /**
     * Where {@code coverage} and {@code knowledge} put the obligation.
     *
     * <p>The one place the pair is read, and read coverage first. What the rows came to is what
     * decides which question is even open: a row found is met whatever else is so, and a reading
     * that came to nothing leaves the point undecided whatever anybody built.
     *
     * <p><b>What has shown a row can be written is a refinement of one of those and not a gate over
     * all of them.</b> It is what tells a miss the rows ran out on from a finding — an author told
     * to write a row at a point nothing promises may be told to write one this compiler cannot show
     * exists. What it never does is decide whether the point is owed: that is the model's answer and
     * is settled before anything here (issue #1249).
     *
     * <p>Every answer is counted. A point where the showing was stopped by a budget of this
     * compiler's, and one where nothing was composed at all, and one nobody read against, are three
     * things this compiler did or did not do — and none of them is the model taking a row back.
     */
    static ObligationDisposition of(ObligationCoverage coverage, WritabilityKnowledge knowledge) {
        return switch (coverage) {
            case ObligationCoverage.Witnessed _ -> new Met();
            // What the rows left open, and beside it whatever else is open about the same point. A
            // reading that came to nothing and a showing that came to nothing are two questions,
            // and a point where both happened is undecided about both.
            case ObligationCoverage.Undecided it -> Undecided.about(alsoWritability(
                    new Uncertainty.WhetherARowIsThere.ReadingsStopped(whatTheReadingsMet(it.by())),
                    knowledge));
            case ObligationCoverage.Missed _ -> switch (knowledge) {
                case WritabilityKnowledge.Established _ -> new Unmet();
                // The rows are read out and no row is at the point, and what would have shown a row
                // can be written did not arrive. No finding is made of it, because nothing here can
                // say the row an author would write is one that exists — and the obligation stays,
                // because what did not arrive is a showing and not the model's answer.
                case WritabilityKnowledge.Prevented stopped -> Undecided.about(
                        List.of(new Uncertainty.WhetherARowCanBeWritten.Stopped(stopped)));
                case WritabilityKnowledge.NoEvidence _ -> Undecided.about(
                        List.of(new Uncertainty.WhetherARowCanBeWritten.NothingShowedIt()));
            };
            // Nothing was read against it, so there is nothing to have found. Counted all the same:
            // whether a row is owed here is the model's answer, and no row naming the behavior is a
            // setting of this build (issue #1249). What is known about a row being writable there
            // is said beside that rather than instead of it.
            case ObligationCoverage.NotMeasured it -> Undecided.about(alsoWritability(
                    new Uncertainty.WhetherARowIsThere.NothingWasRead(it.why()), knowledge));
        };
    }

    /**
     * The question the rows left open, and the one about writing a row where that is open too.
     *
     * <p>In the order the two are said in, which {@link Undecided} holds them to. Written at each
     * of the two places the pair can arise, the order would be written twice.
     */
    private static List<Uncertainty> alsoWritability(Uncertainty rows,
                                                     WritabilityKnowledge knowledge) {
        List<Uncertainty> open = new ArrayList<>();
        open.add(rows);
        switch (knowledge) {
            case WritabilityKnowledge.Established _ -> { }
            case WritabilityKnowledge.Prevented stopped ->
                    open.add(new Uncertainty.WhetherARowCanBeWritten.Stopped(stopped));
            case WritabilityKnowledge.NoEvidence _ ->
                    open.add(new Uncertainty.WhetherARowCanBeWritten.NothingShowedIt());
        }
        return open;
    }
}
