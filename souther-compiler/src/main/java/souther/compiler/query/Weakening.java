package souther.compiler.query;

import souther.compiler.coverage.CoverageSites;
import souther.compiler.observe.Incompleteness;
import souther.compiler.observe.RunSensitivity;
import souther.compiler.partition.ClosureGap;
import souther.compiler.types.SourceConstructOrigin;

/**
 * One thing that leaves a measurement weaker than it looks.
 *
 * <p>What a measure kept as a local variable and threw away. Every measure that came back
 * {@code PARTIAL} worked out a boolean, and every reader above it worked the same boolean out again
 * from the fields beside the answer — a flag on the combinations, the rules nothing took in, the
 * rows that could not be classified. Each of those was a second representation of one assertion, and
 * each was that measure's own business to know about (issue #953).
 *
 * <p><b>A sum and not a shared shape.</b> Every arm holds what the reader that found it produced,
 * whole. Written as one record with a code and a subject wide enough to hold a rule, an axis, a
 * probe and a source, every reader wanting the fact would take the subject apart again by the code
 * beside it — which is the reconstruction this type exists to stop, one level down.
 *
 * <p>So what the arms share is that they weaken a measurement and travel up to whoever assembles
 * one. That is the only operation anything performs on them, and it is the only thing worth having
 * in common: {@link WeakeningSet#union}.
 *
 * <p><b>Every arm is identified well enough to survive that union.</b> Two facts that are one fact
 * collapse — one rule this compiler could not read, found from three behaviors, is one thing to tell
 * an author — and two that are not must not. A bare probe number is not a fact, which is why the
 * arms that are about one behavior say which.
 *
 * <p><b>What a set of these is.</b> Not every true thing that follows from a measurement being
 * weaker than it looks: the causes a reader is to be told about, each said once. A stop this
 * compiler made has consequences that are true sentences about the model — a position nothing could
 * be read into is a position whose rules nothing read — and a reader given both has to work out that
 * the second follows from the first (issue #1084).
 *
 * <p>So one of two findings is left out exactly where a provenance says it is derived from the
 * other, and never because the two arrive with the same path on them. A path is where something is
 * and not what caused it: at one position a reading can enter and lose a clause of its own while a
 * rule about the value from outside goes unanswered, and those are two causes an author acts on
 * separately. Which findings stand in that relation is written down where the arms are
 * ({@link souther.compiler.partition.MeasureClosure}), so a fold is a case of a {@code switch} with
 * no {@code default} rather than a rule anybody applies by eye.
 */
public sealed interface Weakening {

    /**
     * Whether a run of this compiler that allows more could come to a different answer about this.
     *
     * <p>What a wider run is, is written where the answer is
     * ({@link RunSensitivity}). What matters here is which arms decide and
     * which pass the question on: an arm that holds what stopped it asks that, and only an arm that
     * <em>is</em> the first place the fact exists answers for itself. Written the other way round —
     * a switch over the arms with an answer per arm — this would be the reconstruction the type
     * exists to stop, one level up: an observation's code already knows, and a second reading of it
     * here is a second thing to keep in step.
     *
     * <p>So three ask — {@link ObservationIncomplete} asks its code, {@link BorderValueUnreadable}
     * asks its reading, {@link ModelReadingIncomplete} asks its gap — and the other eight answer,
     * because for those there is nothing further in to ask.
     */
    RunSensitivity runSensitivity();

    /**
     * Something the rows were to be measured from was not observed.
     *
     * <p>The vocabulary already existed and was already collected, a list at a time, beside the
     * measures rather than inside them — which is why the report had to join the two by hand and
     * why a behavior's status was decided from a list its measures never saw.
     */
    record ObservationIncomplete(Incompleteness.Met met) implements Weakening {

        public ObservationIncomplete {
            if (met == null) {
                throw new IllegalArgumentException("something went unobserved, and this is what");
            }
        }

        /**
         * One occurrence of it, as the reader that met it produced one.
         *
         * <p>Where it was met is evidence and not the fact — a module's classes failing to be
         * instrumented is one fact however many sources went looking for them — so it arrives as a
         * place this may be cited at and never as part of what tells two of these apart.
         */
        public static ObservationIncomplete of(Incompleteness occurrence) {
            return new ObservationIncomplete(Incompleteness.Met.of(occurrence));
        }

        /** The code's own answer, which is the code's to give because every producer of one agrees
         *  about it. */
        @Override
        public RunSensitivity runSensitivity() {
            return met.fact().code().runSensitivity();
        }
    }

    /**
     * Rows were observed and what a behavior answered with could not be read back as a case.
     *
     * <p>Not an observation that went missing: the row ran and came back. How many there were is the
     * measurement's own count, and is not repeated here — this says which position could not be
     * read, which is what nothing else says.
     */
    record OutputCasesUnreadable(String behavior) implements Weakening {

        /** The row ran and came back, and what it answered with could not be read as a case. A run
         *  that allows more reads it exactly as well. */
        @Override
        public RunSensitivity runSensitivity() {
            return RunSensitivity.UNAFFECTED;
        }
    }

    /** The same at one of a behavior's inputs, counted from zero. */
    record InputCasesUnreadable(String behavior, int at) implements Weakening {

        /** The same, for the same reason. */
        @Override
        public RunSensitivity runSensitivity() {
            return RunSensitivity.UNAFFECTED;
        }
    }

    /**
     * A row's value at one border could not be read, so what is not found at that border is
     * undecided rather than absent.
     *
     * <p>With what stopped the reading, one of these per reason, so that a border stopped in two
     * ways says both — which a set does for free, and a record holding the reasons would leave to
     * whoever wrote the sentence. The reasons are the reading's own ({@link
     * souther.compiler.partition.ReadingGap}) and are carried rather than folded: a value a limit
     * shortened, a value nothing could decode and a place the walk never reached leave the same
     * hole and are three different pieces of news.
     */
    record BorderValueUnreadable(souther.compiler.partition.Border border,
                                 souther.compiler.partition.ReadingGap why)
            implements Weakening {

        /** The reading's own answer, which is the whole of why the reason travels rather than being
         *  folded here. */
        @Override
        public RunSensitivity runSensitivity() {
            return why.runSensitivity();
        }
    }

    /** The reading of the model that a measure depends on did not run out. */
    record ModelReadingIncomplete(ClosureGap cause) implements Weakening {

        /** What was still open when it stopped is what says whether a wider run closes it. */
        @Override
        public RunSensitivity runSensitivity() {
            return cause.runSensitivity();
        }
    }

    /**
     * The elaborated bodies a measure counts inside were not made, so what they hold was not read.
     *
     * <p>Not a reading of the model that stopped and not an observation that went missing: the
     * declarations are here and say a body is written, and what did not come back is the checked
     * body. The measure that needed it has no number, and what it needed to get one is this.
     *
     * <p>Named by the module, because that is what the answer is of: one compile that did not get
     * that far is one fact however many behaviors went looking for it, and naming the behavior
     * would make it as many facts as the module has.
     *
     * <p>It had no arm, and what it cost is what #996 was found through. A behavior whose body was
     * not elaborated was answered as a behavior with no body — which is a claim about the model, is
     * false, and is contradicted by the {@code implemented} on the line above it in the same
     * report.
     */
    record BodiesNotElaborated(String module) implements Weakening {

        /** Nothing was compared against a figure. The compile did not get that far, and a run under
         *  wider allowances does not get further — a build that compiles is a different run's
         *  input, not a wider run of this one. */
        @Override
        public RunSensitivity runSensitivity() {
            return RunSensitivity.UNAFFECTED;
        }
    }

    /**
     * The boundary of one behavior could not be worked out, so every measure that reads one is
     * short of it.
     *
     * <p>Named by the behavior and by nothing else. Both measures of a behavior's boundary go
     * without this one thing, and which of them was asking is not part of the fact — a measure
     * named beside it would turn one cause into as many facts as there are measures that read it,
     * which is counting the paths a fact arrived by ({@link WeakeningSet}).
     *
     * <p>Not the reason there is no boundary. A name that resolved to nothing is reported where it
     * was written, and this says only what that left unmeasurable.
     */
    record BoundaryNotDerived(String behavior) implements Weakening {

        /** There was no boundary to work out, which a wider run does not change: what a name
         *  resolves to is not a figure anything was compared against. */
        @Override
        public RunSensitivity runSensitivity() {
            return RunSensitivity.UNAFFECTED;
        }
    }

    /**
     * The input of the behavior was not read, so no measure that reads a position of it could be
     * finished.
     *
     * <p>Beside the one above rather than folded into it, because the two send a reader to
     * different places. A behavior whose boundary was not derived has a name in its own declaration
     * that resolved to nothing. This one's declaration is whole: what refused the reading is a hole
     * somewhere in the module, and the behavior it stops is any behavior the module declares.
     *
     * <p>Named by the behavior for the reason the one above is: every measure that reads a position
     * is short of this one thing, and which of them was asking is not part of the fact.
     */
    record InputNotRead(String behavior) implements Weakening {

        /** A hole in the module refused the reading, which is not a figure anything was compared
         *  against: a run that allows more meets the same hole. */
        @Override
        public RunSensitivity runSensitivity() {
            return RunSensitivity.UNAFFECTED;
        }
    }

    /**
     * The space of two-class combinations was too large to walk, so the counts describe part of it.
     *
     * <p>Not an observation that went missing and not a reading of the model that stopped: the model
     * was read and the rows were seen, and this measure's own enumeration is what ran out. It was
     * the one warrant a measure did carry — a {@code truncated} flag beside the status, which #951
     * had to add a constructor check to keep the two in step.
     */
    record PairSpaceTruncated(String behavior, long total, int limit) implements Weakening {

        /** The one arm that is a figure of its own. {@code limit} is what the walk was compared
         *  against, and it is a number the query graph hands the analysis, so a run under a wider
         *  {@code AdequacyPolicy} walks further. */
        @Override
        public RunSensitivity runSensitivity() {
            return RunSensitivity.MAY_CHANGE;
        }
    }

    /**
     * A row went through an arm this compiler had proven nothing arrives at.
     *
     * <p>Nothing about the model is wrong here — the proof is. So this is not missing evidence: it
     * is evidence that an analysis the numbers were computed with does not hold, which is why it is
     * an arm of its own and never one of {@link ObservationIncomplete}.
     *
     * <p><b>The arm the source wrote, and not the number a run through it was recorded at.</b> Those
     * are two identities: a probe is what a recording is written in and says of itself that it
     * reaches no further, and which arm it is about is what the sites hold. The two are told apart
     * where both are in hand, which is where the arms of the behavior are, and what travels from
     * there is the one a reader can be sent to. Carried as the number, every later projection had a
     * token nobody outside the numbering could read.
     */
    record ProofContradicted(CoverageSites.Obligation arm) implements Weakening {

        /** An analysis that does not hold is one that does not hold however much a run allows. */
        @Override
        public RunSensitivity runSensitivity() {
            return RunSensitivity.UNAFFECTED;
        }
    }

    /**
     * Two decisions of one body could not be told apart, so the arms counted as one arm are more
     * than one.
     *
     * <p>What the numbers then hold is more than they say. {@link SourceConstructOrigin} names the fork
     * within its module, so this needs nothing beside it to be a fact.
     */
    record ArmsUnsettled(SourceConstructOrigin fork) implements Weakening {

        /** Two decisions that could not be told apart are not told apart by allowing more. */
        @Override
        public RunSensitivity runSensitivity() {
            return RunSensitivity.UNAFFECTED;
        }
    }
}
