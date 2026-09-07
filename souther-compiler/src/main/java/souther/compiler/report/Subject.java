package souther.compiler.report;

import souther.compiler.coverage.CoverageSites;
import souther.compiler.inputs.PositionId;
import souther.compiler.inputs.StandingQuestion;
import souther.compiler.observe.RowRef;
import souther.compiler.partition.AxisId;
import souther.compiler.partition.Border;
import souther.compiler.partition.BorderObligationPoint;
import souther.compiler.publish.MeasureWord;
import souther.compiler.source.SourceId;
import souther.compiler.types.SourceConstructOrigin;

/**
 * What one thing a report says is about, as the thing a reader would be sent back to.
 *
 * <p>The referent and not the fact. A fact is what happened together with what it happened to, and
 * this is the second half of that: {@code souther.compiler.observe.Incompleteness} declares the
 * split itself — a code for what happened, a target for what it happened to — and everything here
 * follows it. So two facts may share a subject, and that is not a collision: the same source read
 * twice for two reasons is one place and two pieces of news, and what tells them apart travels
 * beside this rather than inside it.
 *
 * <p><b>Deliberately not {@code souther.compiler.observe.Target}.</b> That answers whose
 * measurement an incompleteness counts against, which is an attribution question, and it says so:
 * it carries a scope, it answers which behavior contains it, and it refused once already to become
 * the partition's identity for an axis. This answers a different question — which fact a published
 * assessment sends its reader back to — and some arms carry the same coordinates because one domain
 * thing takes part in both answers. Folding the two into {@code OfATarget(Target)} would put an
 * attribution vocabulary where a referent belongs, and the attribution would then have to be true
 * of every subject here, including the ones no incompleteness ever raises.
 *
 * <p><b>Two of these cannot be recovered from the fact that raised the opening.</b> A measurement
 * nobody made carries only what it was waiting for, and an obligation's disposition carries only
 * what is undecided about it — neither holds the measure or the point it is of. Those identities
 * belong to the walk that found them and have to be handed in. A {@code subjectOf} taking a
 * {@code Measurement.NotMeasured} or an {@code ObligationDisposition} alone cannot be written, and
 * that is a property of those types rather than an omission here.
 */
public sealed interface Subject {

    /** One module, and so everything in it. */
    record OfAModule(String module) implements Subject {}

    /**
     * One behavior, named as the facts about it name it.
     *
     * <p>The name and not the module beside it, because that is what the facts carry: a reason
     * about a behavior is one fact however many readers met it, and the fold that makes it one is
     * over the name. Where a walk knows the module as well, what it knows is said by the arms that
     * are the walk's own.
     */
    record OfABehavior(String behavior) implements Subject {}

    /** One source, as the compilation that was handed it identifies it. */
    record OfASource(SourceId source) implements Subject {}

    /**
     * One row.
     *
     * <p>Its own arm rather than the behavior it is a row of. Two rows of one behavior that did not
     * come back are two facts, and named by the behavior they would be one.
     */
    record OfARow(RowRef rowRef) implements Subject {}

    /**
     * A position inside a behavior's input, as a path an author would recognise.
     *
     * <p>Beside {@link AtAPosition} and not folded into it. A path is how a reason spells a place
     * for a person; a {@link PositionId} is what the reading of the model works in. The two are
     * different address spaces that happen to describe the same kind of thing, and one recovered
     * from the other would be this type deciding a question the two owners answer.
     */
    record AtASpelledPosition(String behavior, String path) implements Subject {}

    /** The same place as the reading of the model addresses it. */
    record AtAPosition(String behavior, PositionId at) implements Subject {}

    /** One of a behavior's declared inputs, counted from zero. */
    record AtAnInput(String behavior, int at) implements Subject {}

    /** One rule of the model, with the position it was read at, which is what the question holds. */
    record AtARule(StandingQuestion question) implements Subject {}

    /**
     * One line the rules drew.
     *
     * <p>The line and not a point of it. What a value at a border could not be read for is about
     * the line; what a row is owed at is one of the things that line asks for, and those are two
     * levels ({@link AtAPoint}).
     */
    record AtABorder(Border border) implements Subject {}

    /** One thing a line asks a row at. */
    record AtAPoint(BorderObligationPoint point) implements Subject {}

    /** One decision of a body, named within its module. */
    record AtAFork(SourceConstructOrigin fork) implements Subject {}

    /**
     * One arm of a body, as the source wrote it.
     *
     * <p>Three things name an arm and none of them is the others. A probe is the number a run
     * through it was recorded at; a site address is where the emitter put it; and this is the
     * construct the author wrote, which is what a document already names an arm by and the only one
     * of the three a reader can be sent to.
     */
    record AtAnArm(CoverageSites.Obligation arm) implements Subject {}

    /**
     * One of the measurements a behavior has exactly one of.
     *
     * <p>The module as well as the behavior, because this is the walk's own answer and the walk has
     * both: what it names is a measurement of this compilation and not a fact folded across it.
     */
    record OfAMeasure(String module, String behavior, MeasureWord measure) implements Subject {}

    /**
     * What the rows reach of one position, which a behavior has one of per position.
     *
     * <p>Named by the position and not by a word for the kind of measure. A behavior whose every
     * position went unmeasured has as many of these as it has positions, and one word over them
     * would say all of them were one — which is what {@link MeasureWord} says it has no constant
     * for.
     *
     * <p>The measurement and not the axis. What a reader is sent back to here is what was or was
     * not counted at that position, which is why this is beside {@link OfAMeasure} rather than
     * among the places above.
     */
    record OfAnAxisMeasure(String module, String behavior, AxisId at) implements Subject {}
}
