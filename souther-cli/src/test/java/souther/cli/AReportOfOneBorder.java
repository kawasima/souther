package souther.cli;

import souther.compiler.query.WeakeningSet;
import souther.compiler.types.WrittenOwner;
import souther.compiler.query.Weakening;
import souther.compiler.query.Measurement;
import souther.compiler.check.Carrier;
import souther.compiler.coverage.ComparisonEmissionSite;
import souther.compiler.coverage.CorePath;
import souther.compiler.coverage.NodeAddress;
import souther.compiler.coverage.NumberingIdentity;
import souther.compiler.coverage.SiteAddress;
import souther.compiler.coverage.SiteNumbering;
import souther.compiler.check.Clause;
import souther.compiler.check.ClauseName;
import souther.compiler.check.DeclaredLine;
import souther.compiler.check.InvariantStatementId;
import souther.compiler.check.PartId;
import souther.compiler.check.RuleRef;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.TermPath;
import souther.compiler.numeric.Count;
import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.partition.Border;
import souther.compiler.partition.BorderQuantity;
import souther.compiler.partition.BoundaryTarget;
import souther.compiler.check.ComparisonClaim;
import souther.compiler.partition.Demand;
import souther.compiler.partition.DomainPoint;
import souther.compiler.partition.LineOrigin;
import souther.compiler.partition.PointRole;
import souther.compiler.query.Adequacy;
import souther.compiler.query.BorderAssessment;
import souther.compiler.query.ItemAssessment;
import souther.compiler.query.PartitionDerivation;
import souther.compiler.query.PartitionEvidence;
import souther.compiler.report.AdequacyReport;
import souther.compiler.source.SourceId;
import souther.compiler.diag.Citation;
import souther.compiler.diag.SourcePos;
import souther.compiler.numeric.Towards;
import souther.compiler.partition.LineFacts;
import souther.compiler.types.ExpansionLineage;
import souther.compiler.types.ModelOccurrence;
import souther.compiler.types.SourceConstruct;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * A report of one behavior whose partition is one border, written from the evidence.
 *
 * <p>Shared because two tests ask what a verdict makes of a border and each would otherwise carry
 * its own way of building one. Two fixtures for one shape is two things to keep in step, and the
 * one that was not kept would be a test asserting a verdict about a border the report no longer
 * builds that way.
 *
 * <p>Written from the evidence rather than from a source on purpose: what these are about is what a
 * verdict does with a point that came to an answer and one that did not, and a model that produces
 * that pair says less about it than these fifteen lines do.
 */
final class AReportOfOneBorder {

    /**
     * Where the comparison the lines below come off is recorded.
     *
     * <p>Stated rather than compiled, like the rest of this fixture. A place is a place of a
     * numbering and of nothing else, so what is written here is the numbering — one comparison, in
     * the body this fixture stands for — and the place is read out of it.
     */
    private static final ComparisonEmissionSite WHERE =
            SiteNumbering.of(new NumberingIdentity("example.rate", Map.of(),
                            List.of(new SiteAddress.Comparison(
                                    new NodeAddress("example.rate", Set.of(CorePath.ROOT))))))
                    .comparison(0);

    /** The number the lines below are on, which their orders are the orders of. */
    private static final NumericTerm.ValueOf AT_W_A =
            new NumericTerm.ValueOf(TermPath.of("w").then("a"));

    private AReportOfOneBorder() {}

    /**
     * A bound at 100 over a position the rules run from 100 to 1000: a point against the line and a
     * point away from it, both owed.
     *
     * <p>The line is where what the bound leaves stops, which is what makes it this position's lower
     * end rather than a value inside it. Written as a bound at 100 over a run from 1, the fixture
     * asked for a border no model draws — nothing is below a bound — and got one with an {@code ON}
     * point at the line and an {@code IN} point over the whole run including the values the bound
     * refuses.
     */
    static Border aBoundedBorder() {
        LineOrigin origin = new LineOrigin.InvariantOrigin(
                new DeclaredLine.OfAStatement(new InvariantStatementId(
                        new PartId<>(new RuleRef.Invariant(new Clause.Ref(
                                new Clause.Id(TypeSymbols.declared(
                                        new TypeKey("example.rate", "Amount")), 0),
                                java.util.Optional.of(new ClauseName("cap")))), 0),
                        0)),
                souther.compiler.numeric.EndSide.LOWER, true);
        return Border.at(
                BoundaryTarget.at(
                        new BorderQuantity.OfACoordinate("weigh", AT_W_A,
                                souther.compiler.inputs.TermOrdersFixtures
                                        .itself(AT_W_A, Carrier.WHOLE)),
                        new souther.compiler.partition.Level.OnACarrier(Carrier.WHOLE,
                                Count.of(100))),
                origin,
                new NumericDomain.Bounds(Endpoint.inclusive(Count.of(100)),
                        Endpoint.inclusive(Count.of(1000))));
    }

    /**
     * The same line, drawn by a comparison in a body rather than by a clause of a type.
     *
     * <p>For a test about what a behavior's own account rests on. A run beside a clause's line stops
     * where the clause leaves the quantity, so it is owed to the type and is answered once for the
     * module; a run beside a comparison exists in the body that wrote it and is that behavior's.
     */
    static Border aBorderABodyDrew() {
        SourceConstructOrigin wrote = new SourceConstructOrigin(
                new WrittenOwner.Body("example.rate", "weigh"), 2, 0, SourceConstruct.BINARY);
        LineOrigin origin = new LineOrigin.ComparisonOrigin(
                new LineOrigin.ComparisonOrigin.Read(
                        new RuleRef.Comparison("weigh", wrote),
                        new ModelOccurrence(wrote, ExpansionLineage.ORIGINAL),
                        new souther.compiler.check.RuleReportAnchor.ByTheModuleThatWroteIt(),
                        List.of(WHERE)),
                new LineFacts(new ComparisonClaim.Cut(Towards.BELOW, true)));
        return Border.at(
                BoundaryTarget.at(
                        new BorderQuantity.OfACoordinate("weigh", AT_W_A,
                                souther.compiler.inputs.TermOrdersFixtures
                                        .itself(AT_W_A, Carrier.WHOLE)),
                        new souther.compiler.partition.Level.OnACarrier(Carrier.WHOLE,
                                Count.of(100))),
                origin,
                new NumericDomain.Bounds(Endpoint.inclusive(Count.of(1)),
                        Endpoint.inclusive(Count.of(1000))));
    }

    /** The same border a rule leaves at 100 and up, where the ON point is the whole of what it owes. */
    static Border aBorderAtTheEdgeOfItsDomain() {
        LineOrigin origin = new LineOrigin.InvariantOrigin(
                new DeclaredLine.OfAStatement(new InvariantStatementId(
                        new PartId<>(new RuleRef.Invariant(new Clause.Ref(
                                new Clause.Id(TypeSymbols.declared(
                                        new TypeKey("example.rate", "Amount")), 0),
                                java.util.Optional.of(new ClauseName("cap")))), 0),
                        0)),
                souther.compiler.numeric.EndSide.LOWER, true);
        return Border.at(
                BoundaryTarget.at(
                        new BorderQuantity.OfACoordinate("weigh", AT_W_A,
                                souther.compiler.inputs.TermOrdersFixtures
                                        .itself(AT_W_A, Carrier.WHOLE)),
                        new souther.compiler.partition.Level.OnACarrier(Carrier.WHOLE,
                                Count.of(100))),
                origin,
                new NumericDomain.Bounds(Endpoint.inclusive(Count.of(100)), null));
    }

    /** A point measured to the end, whichever way it came out. */
    static Measurement<ItemAssessment.Coverage> settled(ItemAssessment.Coverage verdict) {
        return new Measurement.Complete<>(verdict);
    }

    /** A point whose reading was not whole, so what it did not find is undecided rather than
     *  absent. What weakened it is a row that never finished. */
    static Measurement<ItemAssessment.Coverage> undecided() {
        return new Measurement.Partial<>(new ItemAssessment.Coverage.NoHit(),
                WeakeningSet.of(Weakening.ObservationIncomplete.of(
                        new souther.compiler.observe.Incompleteness(
                                souther.compiler.observe.Incompleteness.Code.ROW_UNDECIDED,
                                new souther.compiler.observe.Target.OfRow(
                                        new souther.compiler.observe.RowRef("take",
                                                new souther.compiler.source.SourceId("0"),
                                                new souther.compiler.observe.RowIdentity.Unnamed(1))),
                                java.util.Optional.empty()))));
    }

    /**
     * {@code border} assessed point by point, with {@code coverage} saying what each point came to.
     *
     * <p>A point the border does not owe is not one a coverage answer is invented for: what it is,
     * is not owed, and a fixture saying anything else about it would be describing a border other
     * than the one handed in.
     */
    static BorderAssessment assessed(Border border,
                                     Function<PointRole, Measurement<ItemAssessment.Coverage>>
                                             coverage) {
        Map<DomainPoint, ItemAssessment> items = new LinkedHashMap<>();
        for (DomainPoint point : border.answers().keySet()) {
            if (border.demand(point) instanceof Demand.NotOwed not) {
                items.put(point, new ItemAssessment.NotOwed(not.reason()));
                continue;
            }
            // Proven by the rules, which is the one way of being writable that does not depend on
            // what the coverage beside it says. Written as a verdict, this fixture could claim a row
            // was at the point while handing in a coverage that says none is.
            items.put(point, new ItemAssessment.Owed(border.demand(point).criterion(),
                    coverage.apply(border.roleOf(point)),
                    ItemAssessment.WritabilityProjection.PROVEN,
                    souther.compiler.query.SearchOutcomes.none()));
        }
        return new BorderAssessment(border, items);
    }

    /** A row is at every point the border owes. */
    static Measurement<ItemAssessment.Coverage> hit(PointRole role) {
        return settled(new ItemAssessment.Coverage.Hit());
    }

    /**
     * One behavior's measures, as a verdict reads them.
     *
     * <p>The measures' own answers and not the entries beside them. What a reading was short of is
     * settled where the reading is, and reaches a verdict as the measure saying it was not made in
     * full — so a fixture here says which answer it is putting in front of the verdict, and the
     * step that decides that answer is tested where it happens
     * (souther-compiler, {@code AMeasureIsShortOfWhateverItsReadingDidNotReachTest}).
     */
    static PartitionEvidence partition(Measurement<List<BorderAssessment>> border) {
        return new PartitionEvidence(
                new Measurement.FailedToMeasure<>(
                        PartitionDerivation.TheReadingDidNotRunOut.THE_READING_DID_NOT_RUN_OUT,
                        WeakeningSet.of(new Weakening.ModelReadingIncomplete(
                                new souther.compiler.partition.ClosureGap.RulesNotReached("b",
                                        new souther.compiler.inputs.PositionId(
                                                souther.compiler.inputs.TermPath.of("t")))))),
                PartitionEvidence.PairSpace.NONE,
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of());
    }

    /** The border measure made in full, over the one border. */
    static Measurement<List<BorderAssessment>> measured(BorderAssessment boundary) {
        return new Measurement.Complete<>(List.of(boundary));
    }

    /** And the same border, from a reading that was short of something — which is what a position
     *  the walk never reached the rules of leaves behind. */
    static Measurement<List<BorderAssessment>> shortOfSomething(BorderAssessment boundary) {
        return new Measurement.Partial<>(List.of(boundary),
                WeakeningSet.of(new Weakening.ModelReadingIncomplete(
                        new souther.compiler.partition.ClosureGap.RulesNotReached("b",
                                new souther.compiler.inputs.PositionId(
                                        souther.compiler.inputs.TermPath.of("m"))))));
    }

    /**
     * What one behavior's lines make of the whole report, held to {@code held}.
     *
     * <p>The lines and nothing else, because the account is read off them: handed both, a fixture
     * could put a verdict in front of an account made from other lines than the ones beside it, and
     * what came back would be about neither.
     */
    static AdequacyReport.AdequacyStatus verdictOf(Measurement<List<BorderAssessment>> lines,
                                                   Adequacy.AdequacyBar held) {
        PartitionEvidence partition = partition(lines);
        // The account is the module's one relation projected to this behavior, which over one
        // behavior's lines is the points its own rules settled, gathered across their readings.
        AdequacyReport.BehaviorReport behavior = new AdequacyReport.BehaviorReport(
                "weigh", souther.compiler.check.BehaviorImplementation.IMPLEMENTED,
                new souther.compiler.query.BehaviorEvidence(
                        Adequacy.RowReading.NONE, null, partition, lines,
                        lines.readAs(read -> souther.compiler.query
                                .BorderObligationPointAssessment.across(read).stream()
                                .filter(point -> point.belongsToBehaviorAccount("weigh"))
                                .toList()),
                        null),
                souther.compiler.query.ClaimAnnotations.NONE, List.of(), java.util.Map.of(),
                java.util.Map.of(), rulePlaces(lines));
        return new AdequacyReport(AdequacyReport.SCHEMA_VERSION, "test",
                held, WeakeningSet.none(),
                List.of(new AdequacyReport.ModuleReport("example.wide",
                        new SourceId("wide.sou"), List.of(behavior), List.of(),
                        // Nothing this module's declarations are owed, and nothing that finding
                        // them went without: the fixture is about one behavior's own lines.
                        new AdequacyReport.DeclarationsShown(
                                new Adequacy.DeclaredBoundaries(List.of(), java.util.Map.of()),
                                java.util.Map.of(), java.util.Map.of()))))
                .adequacy();
    }

    /**
     * Where this fixture's page shows each rule its lines name.
     *
     * <p>Stood up here because there is no compile to ask. The rules are written in the source this
     * fixture is about, so the module that wrote them places them, and the place is the one the
     * line was built at.
     */
    private static java.util.Map<souther.compiler.check.RuleCitation.Written,
            Citation> rulePlaces(Measurement<List<BorderAssessment>> lines) {
        java.util.Map<souther.compiler.check.RuleCitation.Written, Citation> places =
                new java.util.LinkedHashMap<>();
        for (BorderAssessment line : lines.made().orElse(List.of())) {
            if (line.origin().cited()
                    instanceof souther.compiler.check.RuleCitation.Written written) {
                places.put(written, Citation.of(new SourcePos(3, 5)));
            }
        }
        return places;
    }
}
