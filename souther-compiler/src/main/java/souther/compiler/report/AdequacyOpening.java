package souther.compiler.report;

import souther.compiler.observe.RunSensitivity;
import souther.compiler.query.EstablishmentGap;
import souther.compiler.query.NotMeasuredReason;
import souther.compiler.query.Weakening;

/**
 * One thing keeping an adequacy verdict undetermined.
 *
 * <p>Not a {@link Weakening}, and that is the whole of why this type exists. A verdict is settled
 * by everything it rests on having come to an answer, and only one of the ways that fails is a
 * measure going without something. A measure that could have found a gap and was never made weakens
 * nothing — {@link souther.compiler.query.Measurement.NotMeasured} carries no {@code WeakeningSet}
 * on purpose, which is what separates it from a measurement that was asked for and could not be
 * finished. Neither does a point the rows are read out at where nothing could show a row can be
 * written: whether one can be is a second question, settled from what showed it and not from the
 * coverage. So a list of weakenings said nothing at all about a model nobody has written rows for,
 * or about a point nothing promised, while the verdict over each stayed open.
 *
 * <p>Read off the verdict's own predicate rather than assembled beside it. Every arm below is what
 * one of the things {@code adequacy()} walks contributes, so {@code undetermined} and this being
 * empty cannot both be true — which is a property worth having and is the one the conformance
 * corpus produced a counterexample to.
 *
 * <p><b>What it is not.</b> Not everything that left the report weaker than it looks: a measure
 * no answer here rests on may be as partial as it likes without the verdict being any less settled
 * by it. And not a second vocabulary — each arm is projected to a word the document already has.
 */
public sealed interface AdequacyOpening {

    /** Whether a run of this compiler that allows more could get past this. */
    RunSensitivity runSensitivity();

    /**
     * What this is about, as a reader is sent back to it.
     *
     * <p>Answered by the arm and not worked out from the payload. One of these carries a fact that
     * names its own subject; the other three carry what a measurement or an obligation went
     * without, and neither of those holds the measure or the point it is of. Those subjects belong
     * to the walk that found them and arrive with the arm, which is why they are fields here rather
     * than a projection somebody could write later ({@link Subject}).
     */
    Subject subject();

    /**
     * A measure was made and went without something.
     *
     * <p>One per fact, and the facts have already been folded: two measures that went without the
     * same thing went without one thing, which is {@code WeakeningSet}'s. Nothing folds them again
     * on what they are printed as — two rules this compiler could not read are two of these, and a
     * document calls them by one word.
     */
    record ByWeakening(Weakening cause) implements AdequacyOpening {

        public ByWeakening {
            if (cause == null) {
                throw new IllegalArgumentException("a verdict held open by nothing is settled");
            }
        }

        @Override
        public RunSensitivity runSensitivity() {
            return cause.runSensitivity();
        }

        /** The fact's own, which every weakening names for itself. */
        @Override
        public Subject subject() {
            return Subjects.of(cause);
        }
    }

    /**
     * A measure the verdict rests on was never made, and this is what it says about that.
     *
     * <p>One per measure and never folded on the reason. Two measures that were both waiting on
     * rows are two measures nobody made, and a set keyed on {@code no_rows} would say a model with
     * one behavior unmeasured and a model with forty were the same news.
     *
     * <p>{@link RunSensitivity#UNAFFECTED}, and the word means what it says rather than what a
     * reader might hope. Widening an allowance does not make a measurement nobody asked for; what
     * does is a row, and a row is a change to the model rather than a wider run of this compiler
     * over it. What a person may go on to do is the reason's, which is why it travels.
     */
    record NotMeasured(Subject subject, NotMeasuredReason why) implements AdequacyOpening {

        public NotMeasured {
            if (subject == null) {
                throw new IllegalArgumentException("a measure nobody made is some measure");
            }
            if (why == null) {
                throw new IllegalArgumentException("a measure nobody made says why");
            }
        }

        @Override
        public RunSensitivity runSensitivity() {
            return RunSensitivity.UNAFFECTED;
        }
    }

    /**
     * Nothing could show a row can be written at a point, and this is what stopped the showing.
     *
     * <p>Its own arm because it is about neither a measure nor a measurement. The rows were read
     * out and no row is at the point; whether one could be written there is a second question, and
     * a point where nothing answered it is owed a row still. Nothing weakened a measure over it,
     * so a list of what fell short says nothing about a verdict this holds open.
     *
     * <p>One per gap and not one per point. A showing may be stopped in more than one way — a value
     * read for it that did not come back, and a composing for another that never started — and what
     * a reader wants is everything that would have to give.
     */
    record ShowingStopped(Subject subject, EstablishmentGap by) implements AdequacyOpening {

        public ShowingStopped {
            if (subject == null) {
                throw new IllegalArgumentException("a showing that was stopped was of some point");
            }
            if (by == null) {
                throw new IllegalArgumentException("a showing that was stopped says what stopped it");
            }
        }

        /**
         * The gap's own answer, and it is read rather than assumed.
         *
         * <p>An observation that stopped holds the codes it met, and those do not agree: a value a
         * limit shortened is one a run allowed more keeps and a value nothing could read back is
         * not. So the codes are asked, and it takes all of them — a showing stopped by both is
         * stopped again after the limit is raised.
         */
        @Override
        public RunSensitivity runSensitivity() {
            return switch (by) {
                // What this compiler declined to build, and the figures that decided it.
                case EstablishmentGap.Composition _ -> RunSensitivity.MAY_CHANGE;
                case EstablishmentGap.Observation it -> it.causes().written().stream()
                        .allMatch(code -> code.runSensitivity() == RunSensitivity.MAY_CHANGE)
                        ? RunSensitivity.MAY_CHANGE : RunSensitivity.UNAFFECTED;
            };
        }
    }

    /**
     * Nothing showed a row can be written at a point, and nothing stopped anything either.
     *
     * <p>Beside {@link ShowingStopped} and not folded into it. There a showing was made and met
     * something; here nothing arrived at all, and a reader told a limit stopped it would go looking
     * for a limit nobody hit. Nothing was compared against a figure, so no allowance changes it.
     */
    record NothingShowedARowCanBeWritten(Subject subject) implements AdequacyOpening {

        public NothingShowedARowCanBeWritten {
            if (subject == null) {
                throw new IllegalArgumentException("nothing showed a row at some point");
            }
        }

        @Override
        public RunSensitivity runSensitivity() {
            return RunSensitivity.UNAFFECTED;
        }
    }
}
