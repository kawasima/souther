package souther.compiler.report;

import souther.compiler.query.ClaimAnnotations;
import souther.compiler.diag.QuotedFrom;
import souther.compiler.source.SourceId;

import souther.compiler.ast.Hir;
import souther.compiler.check.BehaviorImplementation;
import souther.compiler.check.Carrier;
import souther.compiler.check.ComparisonClaim;
import souther.compiler.check.CoverageObligation;
import souther.compiler.check.PartId;
import souther.compiler.types.CanonicalNameOrder;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.check.RuleCitation;
import souther.compiler.check.RuleCitations;
import souther.compiler.check.RuleRef;
import souther.compiler.numeric.Towards;
import souther.compiler.partition.AuthoredLine;
import souther.compiler.partition.BorderObligationPoint;
import souther.compiler.partition.ObligationIdentity;
import souther.compiler.partition.ClosureGap;
import souther.compiler.partition.ConditionReportAnchor;
import souther.compiler.partition.CompositionBudget;
import souther.compiler.partition.CompositionRepertoire;
import souther.compiler.partition.DecidedCondition;
import souther.compiler.partition.DecisionSubject;
import souther.compiler.partition.DecisionReading;
import souther.compiler.partition.DecisionRule;
import souther.compiler.partition.DomainPoint;
import souther.compiler.partition.FarEnd;
import souther.compiler.partition.Generator;
import souther.compiler.partition.Level;
import souther.compiler.partition.NotOwedReason;
import souther.compiler.partition.OnTheWay;
import souther.compiler.partition.ReachabilityGap;
import souther.compiler.partition.ReportedReason;
import souther.compiler.partition.RoleAnswer;
import souther.compiler.partition.RuleEvidenceOrigin;
import souther.compiler.partition.UndividedPosition;
import souther.compiler.diag.Citation;
import souther.compiler.diag.SourcePos;
import souther.compiler.diag.SourceRendering;
import souther.compiler.inputs.AuthoredOrder;
import souther.compiler.inputs.InputQuestion;
import souther.compiler.inputs.StandingQuestion;
import souther.compiler.inputs.RuleSite;
import souther.compiler.meta.ModuleMetadata;
import souther.compiler.check.CheckSurface;
import souther.compiler.observe.Disposition;
import souther.compiler.observe.Incompleteness;
import souther.compiler.observe.MeasureReason;
import souther.compiler.query.Bodies;
import souther.compiler.query.FindingSubject;
import souther.compiler.query.InputCaseEvidence;
import souther.compiler.query.Measure;
import souther.compiler.query.Sites;
import souther.compiler.query.Measurement;
import souther.compiler.query.RuleRequirement;
import souther.compiler.query.RuleSettlement;
import souther.compiler.query.SearchOutcomes;
import souther.compiler.query.Weakening;
import souther.compiler.query.WeakeningSet;
import souther.compiler.observe.MeasurementStatus;
import souther.compiler.query.OutputCaseEvidence;
import souther.compiler.coverage.ArmReportAnchor;
import souther.compiler.coverage.CoverageSites;
import souther.compiler.coverage.DecidedBy;
import souther.compiler.coverage.SuppliedRules;
import souther.compiler.query.About;
import souther.compiler.query.DecisionEvidence;
import souther.compiler.query.DecisionRuleReading;
import souther.compiler.query.Adequacy;
import souther.compiler.query.ArmDisposition;
import souther.compiler.query.ArmExclusion;
import souther.compiler.query.ArmObligation;
import souther.compiler.query.ArmSummary;
import souther.compiler.query.BorderAssessment;
import souther.compiler.query.BorderObligationPointAssessment;
import souther.compiler.query.ItemAssessment;
import souther.compiler.query.ObligationAssessment;
import souther.compiler.query.ObligationCoverage;
import souther.compiler.query.ObligationDisposition;
import souther.compiler.query.ObligationSummary;
import souther.compiler.query.ReadingReasons;
import souther.compiler.query.UnaskedReasons;
import souther.compiler.query.EstablishmentGap;
import souther.compiler.query.WritabilityKnowledge;
import souther.compiler.publish.CanonicalSelection;
import souther.compiler.publish.AdequacyOpeningWord;
import souther.compiler.publish.CanonicalArrangement;
import souther.compiler.publish.NoPlaceToWrite;
import souther.compiler.publish.NotMeasuredWord;
import souther.compiler.publish.PublicationOrders;
import souther.compiler.publish.PublicationOrders;
import souther.compiler.publish.PlaceProse;
import souther.compiler.publish.PublishedAt;
import souther.compiler.publish.PublishedIncompleteness;
import souther.compiler.observe.RowIdentity;
import souther.compiler.publish.MeasureWord;
import souther.compiler.publish.PublishedSubject;
import souther.compiler.publish.PublishedOpening;
import souther.compiler.publish.DocumentArray;
import souther.compiler.publish.DocumentItem;
import souther.compiler.publish.DocumentPart;
import souther.compiler.publish.PublishedRuleHandle;
import souther.compiler.publish.PublishedSentence;
import souther.compiler.publish.RuleHandleProse;
import souther.compiler.publish.RuleHandleSurface;
import souther.compiler.publish.WeakeningVocabulary;
import souther.compiler.publish.WeakeningWord;
import souther.compiler.partition.CompositionBudget;
import souther.compiler.partition.ReadingGap;
import souther.compiler.partition.UndividedPosition;
import souther.compiler.query.Compilation;
import souther.compiler.query.BehaviorEvidence;
import souther.compiler.query.PartitionEvidence;
import souther.compiler.text.DisplayColumns;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.WrittenOwner;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * How well a model's {@code example}s cover it, as something a person reads and a build reads.
 *
 * <p>This is the first version, and it answers only what needs no analysis: which behaviors there are,
 * which of them still have no {@code let}, how many rows each carries, and how many of those rows are
 * waiting rather than judging. The measures that need the model taken apart — which output cases the
 * rows witness, which equivalence classes and boundaries they reach, which branches they run — arrive
 * on top of these same observations.
 *
 * <p>{@code schemaVersion} is here from the first version because a build that reads this is written
 * against a shape, and a shape that changes without saying so breaks it silently. So is
 * {@code status}: an evaluation that could not read everything must not be read as one that found
 * nothing, and the difference is not visible in the numbers.
 *
 * <p>Nothing the request decides is carried. What a report marks as a gap is every obligation the
 * account derives ({@link Adequacy.Kind#isAboutAnObligation}), and how much was measured is not held either: what a
 * measure came to is the measure's own answer, and a report that kept the level beside the evidence
 * could read a measure's silence as something other than what the measure said (issue #955).
 */
public record AdequacyReport(int schemaVersion, String compilerVersion,
                             WeakeningSet weakenedBy, List<ModuleReport> modules) {

    /**
     * How far the whole measurement got, in the report's own word.
     *
     * <p>Derived and not held. A level with no measure of its own is complete exactly when nothing
     * beneath it went without anything, so a word kept beside the union would be a second thing to
     * keep true — which is what this report used to have at all three levels.
     */
    public MeasurementStatus status() {
        return ReportMeasurement.statusOf(weakenedBy);
    }

    /**
     * Where this report shows each rule any of it may send a reader to.
     *
     * <p>Read off what this report holds rather than kept beside it, so that a report narrowed to
     * one behavior shows the rules of that behavior and knows about no others. Kept at the top, a
     * narrowed report would go on holding places for pages it no longer has.
     *
     * <p>For the lines a reader is shown that belong to no one behavior — what keeps the whole
     * verdict open, which is folded over every module. What each of those names is a rule some
     * behavior of this report was measured on, so the union of what the pages hold is where it is.
     */
    private PublishedRuleHandle.WhereARuleIs rulePlaces() {
        Map<RuleCitation.Written, Citation> places = new LinkedHashMap<>();
        for (ModuleReport module : modules) {
            places.putAll(module.owedByDeclarations().rulePlaces());
            for (BehaviorReport behavior : module.behaviors()) {
                places.putAll(behavior.rulePlaces());
            }
        }
        return cited -> {
            Citation at = places.get(cited);
            if (at == null) {
                throw new IllegalStateException(
                        "this report was not assembled with the rule " + cited);
            }
            return at;
        };
    }

    public static final int SCHEMA_VERSION = 21;

    /**
     * Where the schema this writes documents ships.
     *
     * <p>Derived from the version rather than written beside it. Which schema describes what this
     * writer emits is the writer's own answer and there is one of it; spelled again wherever
     * something opens the file, raising the version is a hunt through however many spellings there
     * are, and the ones nothing checks go on opening the schema of the version before.
     *
     * <p>What is not derived is the schema's own account of which version it is — the number a
     * document must carry and the identifier a resolver keys on are the contract, and generating
     * them from this constant would leave the contract and the writer agreeing by construction and
     * checkable nowhere ({@code EverySchemaWordIsAccountedFor}).
     */
    public static final String SCHEMA_RESOURCE =
            "/souther/adequacy-schema-" + SCHEMA_VERSION + ".json";

    /**
     * Whether the rows meet what the account asks of them.
     *
     * <p>Apart from {@code status}, which says whether the measurement could be made at all. A
     * measurement that came back complete over a model with an arm nothing reaches is a measurement
     * that worked and a model that does not satisfy it, and one word cannot say both.
     */
    public enum AdequacyStatus {
        /** Every measure the verdict rests on came to an answer, and none of them found a gap. A
         *  model the account asks nothing of is here too: it was asked and had nothing to answer
         *  for. */
        SATISFIED,
        /** A measure found a gap a build refuses over. One is enough, whatever else could not be
         *  measured. */
        NOT_SATISFIED,
        /** A measure that could have found such a gap was not made, or could not be. */
        UNDETERMINED
    }

    /**
     * What a module's declarations are owed, with where this report shows the conditions on the way
     * to their lines.
     *
     * <p>The places are here, beside the account they are about, and not on the module. A module
     * says which of this compilation's sources it is written in, and these say where each condition
     * a sentence names is — including the ones another module wrote. Held side by side, the two
     * would be a value that answers "which file" twice, and the second answer would be about
     * whichever condition a reader happened to be looking at.
     *
     * @param owed what the declarations are owed, or null where the compile did not get far enough
     *             to be asked
     */
    public record DeclarationsShown(Adequacy.DeclaredBoundaries owed,
                                    Map<ConditionReportAnchor, Citation> conditionPlaces,
                                    Map<RuleCitation.Written, Citation> rulePlaces) {

        public DeclarationsShown {
            conditionPlaces = Map.copyOf(conditionPlaces);
            rulePlaces = Map.copyOf(rulePlaces);
        }

        /** Where this report shows the rules the declarations' own block names, raised where it
         *  was assembled with none, for the reason {@code BehaviorReport.rulePlace} gives. */
        public PublishedRuleHandle.WhereARuleIs rulePlace() {
            return cited -> {
                Citation at = rulePlaces.get(cited);
                if (at == null) {
                    throw new IllegalStateException("this report was not assembled with the rule "
                            + cited + " of the declarations");
                }
                return at;
            };
        }

        /** Nothing owed and nothing to point at, for a module nobody could ask. */
        public static final DeclarationsShown NONE =
                new DeclarationsShown(null, Map.of(), Map.of());
    }

    /**
     * What one module's compile came to, as this report says it.
     *
     * @param owedByDeclarations what this module's declarations are owed and how far the reading it
     *                           was made from got. An account and not a list of debts: a module
     *                           whose lines nobody could read holds no debts anybody found, and read
     *                           as a list that is the same answer as a module whose declarations owe
     *                           nothing
     */
    public record ModuleReport(String module, SourceId declaredIn,
                               List<BehaviorReport> behaviors,
                               List<ReportedFinding> declarations,
                               DeclarationsShown owedByDeclarations) {

        /**
         * What the module is short of that is not any behavior's.
         *
         * <p>A line an {@code invariant} drew is a fact about the type — whether a row standing at
         * the boundary of {@code UserId} is believed is a question about {@code UserId}, and the
         * behaviors carrying it say nothing about the length of a user id (issue #1062). Held in a
         * behavior's list, it had to be filed under whichever of them a walk reached first.
         *
         * <p>Here rather than left out of the report, so that a finding whose subject is not a
         * behavior cannot go missing between the measure and the page.
         */
        public ModuleReport {
            behaviors = List.copyOf(behaviors);
            declarations = List.copyOf(declarations);
        }

        /** The debts themselves, for a reader walking them. Empty where nobody could be asked,
         *  which {@link #declarationsWeakenedBy()} is what says. */
        public List<Adequacy.DeclaredDebt> debts() {
            return owedByDeclarations.owed() == null
                    ? List.of() : owedByDeclarations.owed().owed();
        }

        /**
         * Why the measures of this module could not read everything, as the reasons themselves.
         *
         * <p>Derived from what its behaviors went without, and not gathered beside it. These are the
         * lines a document prints under a behavior and the entries a build counts; the status above
         * them is the same union read another way, and the two were assembled separately — one from
         * the measures, one by walking the sources again. A list built the second way can hold a
         * reason no measure carried, which is a report saying something the measures beside it do
         * not (issue #996).
         *
         * <p>One entry per reason, which is what the account itself holds. A reason that counts
         * against every behavior is carried by every one of them and is one thing to tell an
         * author, and a module-wide failure found from each of three attached files is one failure
         * citing three places.
         *
         * <p><b>In order, and there is no way to have them out of it.</b> The account holds these
         * as facts and says nothing about which comes first, so a caller handed that set would be
         * publishing whatever it iterated in — and three surfaces publish these: this document,
         * the page a person reads, and the block a generator writes. Handing over the sequence
         * rather than the set is what makes the order one thing rather than three.
         *
         * <p>As the arrangement and not as the list inside it, so that a surface asks for the
         * order where it writes. What that costs is one call; what it buys is that the check over
         * a writer of a canonically ordered field asks of that writer, and can be held to a
         * control that asks the same thing of an array whose order is somebody else's.
         */
        public CanonicalArrangement<PublishedIncompleteness> incompleteness() {
            return PublishedIncompleteness.everyOne(weakenedBy().observationCauses());
        }

        /**
         * What this module went without: the union of its parts, and nothing of its own.
         *
         * <p>Its parts are its behaviors and what its declarations are owed. The second is a part
         * because a row owed to a declaration is answered once for the module and is short there: no
         * behavior carrying the type is short by it, so a module that read only its behaviors would
         * call itself measured in full over a debt nobody could measure.
         *
         * <p>Derived and not held. What a module could not read reaches it through the measures that
         * lost by it — a source none of whose rows were seen counts against every behavior the
         * module has, and the reading of each of them says so. Read a second time from a list of the
         * module's own, this report was giving a raw fact its meaning as a weakening, which is a
         * measure's answer and not a renderer's (issue #953).
         */
        public WeakeningSet weakenedBy() {
            WeakeningSet out = WeakeningSet.none();
            for (BehaviorReport behavior : behaviors) {
                out = out.union(behavior.weakenedBy());
            }
            return out.union(declarationsWeakenedBy());
        }

        /**
         * What the measurement of what this module's declarations are owed went without.
         *
         * <p>The other account of the same lines, and the module's own. A row owed to the
         * declaration that drew a line is answered once here, from every reading of it — so a
         * search that could not read a value at such a point leaves this short, and no behavior
         * carrying the type is short of anything by it. Counted under a behavior instead, an author
         * would be told their body was measured in part for a row nothing written for it is owed.
         *
         * <p>What found the lines is the behaviors' and is counted there: the two accounts rest on
         * one reading, and a reading that did not run out may have left either of them short of
         * entries.
         */
        public WeakeningSet declarationsWeakenedBy() {
            return owedByDeclarations.owed() == null
                    ? WeakeningSet.none() : owedByDeclarations.owed().weakening();
        }

        /** How far this module's measurement got. Derived, for the reason
         *  {@link AdequacyReport#status()} gives. */
        public MeasurementStatus status() {
            return ReportMeasurement.statusOf(weakenedBy());
        }
    }

    /**
     * One finding as this report shows it: what was found, and where it is shown.
     *
     * <p>The two are one thing here and two things upstream. A finding does not carry the caret a
     * report puts under it; where that belongs is worked out once, when this report is assembled,
     * by the rule {@link Adequacy#placeOf} owns and the warnings a build reads use as well.
     *
     * <p>Recombined here and not carried down as a second list beside the findings. Two collections
     * whose entries answer for each other is a correspondence somebody has to keep true, and a
     * report holding one finding and another's place would show a reader the wrong line with no
     * way to tell.
     */
    public record ReportedFinding(Adequacy.Finding finding, Citation at) {

        public ReportedFinding {
            java.util.Objects.requireNonNull(finding, "a reported finding is some finding");
            java.util.Objects.requireNonNull(at, "a reported finding is shown somewhere");
        }

        /** What it is about, for a reader that wants the fact and not the page. */
        public About about() {
            return finding.about();
        }

        /** Which kind of thing it is, as the finding says. */
        public Adequacy.Kind kind() {
            return finding.kind();
        }
    }

    /**
     * What one behavior's compile came to, as this report says it.
     *
     * @param claimed   what the body declared cannot arrive, beside the measures rather than in
     *                  them. The two are joined where this report is written and nowhere else,
     *                  which is what keeps a claim from reaching a denominator
     * @param reported  what the measures found and nothing filled, each with where this report
     *                  shows it — which is what the lines under this behavior print and what a
     *                  build is warned about
     * @param armPlaces where each arm of this behavior is shown, for the lines that name one no
     *                  finding is about
     * @param conditionPlaces where each condition on the way to one of this behavior's lines is
     *                  shown. Beside the searches rather than in them: what a search holds is which
     *                  condition it could not compose against, and where a reader is sent for one
     *                  is asked here, once per condition the page may name
     * @param rulePlaces where each rule this behavior's page may send a reader to is shown. Keyed
     *                  by the handle and not by the question it asks: a rule written where a reader
     *                  can open it says only that its writing module places it, so every one of
     *                  them would be one key — what makes the question whole is the rule it is
     *                  about, which is what a handle is
     * @param ruleSettlements what the search came to about each rule no row took, or empty where
     *                  nothing asked it. Beside the findings rather than among them: a finding is
     *                  an obligation and a rule nothing could show a row for is owed none, while a
     *                  page or a document that showed only the findings would say a rule count and
     *                  put fewer lines under it than the count
     */
    public record BehaviorReport(String name, BehaviorImplementation implementation,
                                 BehaviorEvidence evidence,
                                 ClaimAnnotations claimed,
                                 List<ReportedFinding> reported,
                                 Map<ArmReportAnchor, Citation> armPlaces,
                                 Map<ConditionReportAnchor, Citation> conditionPlaces,
                                 Map<RuleCitation.Written, Citation> rulePlaces,
                                 Map<RuleSite, Citation> partPlaces,
                                 Map<DecisionReading.Ruled, List<ShownCondition>> ruleReadings,
                                 Map<DecisionRule, RuleSettlement> ruleSettlements) {
        public BehaviorReport {
            reported = List.copyOf(reported);
            armPlaces = Map.copyOf(armPlaces);
            conditionPlaces = Map.copyOf(conditionPlaces);
            rulePlaces = Map.copyOf(rulePlaces);
            partPlaces = Map.copyOf(partPlaces);
            ruleReadings = Map.copyOf(ruleReadings);
            ruleSettlements = Map.copyOf(ruleSettlements);
        }

        /**
         * How this report tells a reader which rule a line is about, one note per condition.
         *
         * <p>Worked out when the report was assembled, for the reason {@link #placeOf} gives about
         * an arm: naming the arm a condition goes through takes the plan that numbered the places,
         * and a page is not where that is asked. A rule this report was not assembled with is one
         * nothing here can say anything about — which is two of this compiler's answers
         * disagreeing rather than a rule to describe by fewer conditions than it turns on.
         *
         * <p>Keyed by the rule and not by what is said about it. A rule owed a row and a rule
         * nothing could show one for are described the same way, and two keys would be one
         * description worked out twice.
         */
        public List<ShownCondition> readingsOf(DecisionReading.Ruled rule) {
            List<ShownCondition> read = ruleReadings.get(rule);
            if (read == null) {
                throw new IllegalStateException("this report was not assembled with a rule of `"
                        + name + "` that one of its lines is about");
            }
            return read;
        }

        /**
         * Where this report shows {@code arm}.
         *
         * <p>Worked out when the report was assembled, from the same question the warnings a build
         * reads ask. An arm carries which of the two places names it and not the place itself, so
         * an arm the report was not assembled with is one nothing here can show — which is two of
         * this compiler's answers disagreeing rather than a caret to leave empty.
         */
        public Citation placeOf(CoverageSites.ArmSite arm) {
            Citation at = armPlaces.get(arm.anchor());
            if (at == null) {
                throw new IllegalStateException("this report was not assembled with the arm "
                        + arm.anchor() + " of `" + name + "`");
            }
            return at;
        }

        /**
         * Where this report shows the rule {@code cited} sends a reader to.
         *
         * <p>Worked out when the report was assembled, for the reason {@link #placeOf} gives about
         * an arm. A handle this report was not assembled with is one nothing here can show — which
         * is two of this compiler's answers about what the page names disagreeing, and is raised
         * rather than left as a sentence pointing nowhere.
         */
        public PublishedRuleHandle.WhereARuleIs rulePlace() {
            return cited -> {
                Citation at = rulePlaces.get(cited);
                if (at == null) {
                    throw new IllegalStateException("this report was not assembled with the rule "
                            + cited + " of `" + name + "`");
                }
                return at;
            };
        }

        /** Where this report shows each part of a rule any of it points inside — see
         *  {@link WhereAPartIs}. */
        public WhereAPartIs partPlace() {
            return new WhereAPartIs() {
                @Override
                public Citation of(PartId<RuleRef.Invariant> part) {
                    return shown(RuleSite.at(part));
                }

                @Override
                public Citation of(SourceConstructOrigin origin) {
                    return shown(new RuleSite.AConstructTheAuthorWrote(origin));
                }

                private Citation shown(RuleSite site) {
                    Citation at = partPlaces.get(site);
                    if (at == null) {
                        throw new IllegalStateException("this report was not assembled with "
                                + site + " of `" + name + "`");
                    }
                    return at;
                }
            };
        }

        /** What was found about this behavior, without where any of it is shown. */
        public List<Adequacy.Finding> findings() {
            return reported.stream().map(ReportedFinding::finding).toList();
        }

        /** How far the reading of this behavior's rows got, and what it read. The counts a document
         *  prints are this measurement's value and are absent where it has none: a source nobody
         *  evaluated leaves no row to count, and printing {@code rows 0} for it says the author
         *  wrote none. */
        public Adequacy.RowReading reading() {
            return evidence.reading();
        }

        /** What the rows establish about the cases of its inputs and its output. */
        public Adequacy.SignatureEvidence signature() {
            return evidence.signature();
        }

        /** What they establish about the classes, and what this behavior is owed a row for at the
         *  lines its rules draw. */
        public PartitionEvidence partition() {
            return evidence.partition();
        }

        /** Every line its positions met, in every role — what a block that shows a border whole is
         *  written from. */
        public Measure<List<BorderAssessment>> boundaryReadings() {
            return evidence.boundaryReadings();
        }

        /**
         * What this behavior is owed a row for at the lines its own rules drew, each once however
         * many of its positions read it. Empty where the reading has none to show, for the reason
         * {@link #lines} is.
         */
        public List<BorderObligationPointAssessment> account() {
            return evidence.account() == null ? List.of()
                    : evidence.account().made().orElseGet(List::of);
        }

        /**
         * The same as the lines alone, empty where the reading has none to show.
         *
         * <p>The one place the absence of the measure itself is read, which is the compile not
         * having got far enough to be asked — the same absence the partition beside it answers with,
         * since the two arrive together ({@link BehaviorEvidence}).
         */
        public List<BorderAssessment> lines() {
            return boundaryReadings() == null ? List.of()
                    : boundaryReadings().made().orElseGet(List::of);
        }

        /** What they establish about the arms of its body. */
        public Adequacy.BranchEvidence branch() {
            return evidence.branch();
        }

        /**
         * What this behavior's measures went without.
         *
         * <p>Derived and not held. It is the union of what its parts went without, which the
         * evidence answers; kept here as well it would be a second thing to keep true, and the
         * report is what used to work it out — over a list of parts written where the document is
         * assembled, which the reading was missing from (issue #996).
         */
        public WeakeningSet weakenedBy() {
            return evidence.weakening();
        }

        /** How far this behavior's measurement got. Derived, for the reason
         *  {@link AdequacyReport#status()} gives. */
        public MeasurementStatus status() {
            return ReportMeasurement.statusOf(weakenedBy());
        }

        /**
         * How many {@code example} rows name this behavior, where its rows were read.
         *
         * <p>Absent where they were not. A count of what came back is not a count of what was
         * written, and a reader shown {@code 0} beside a source nobody evaluated is told the author
         * wrote no row — which sends them to write one that may already be there.
         */
        public OptionalInt rowCount() {
            return reading().measured().made()
                    .map(seen -> OptionalInt.of(seen.rows().size()))
                    .orElseGet(OptionalInt::empty);
        }

        /** How many of those are recorded rather than evaluated. Absent for the reason
         *  {@link #rowCount()} is. */
        public OptionalInt pending() {
            return reading().measured().made()
                    .map(seen -> OptionalInt.of((int) seen.rows().stream()
                            .filter(r -> r.disposition()
                                    == Disposition.PENDING)
                            .count()))
                    .orElseGet(OptionalInt::empty);
        }

        /** The findings of one kind, in the order the measure produced them. */
        public List<Adequacy.Finding> of(Adequacy.Kind kind) {
            return findings().stream().filter(f -> f.kind() == kind).toList();
        }
    }

    /** Reads a finished compile. {@link Compilation#answerEverything()} must have been asked first;
     * otherwise there is nothing to read and every behavior looks unexampled. */
    public static AdequacyReport of(Compilation compilation) {
        List<ModuleReport> modules = new ArrayList<>();
        WeakeningSet overall = WeakeningSet.none();
        for (String name : compilation.modules()) {
            // The assembly and not the state built on it. A report says what could and could not be
            // measured, so a module one of whose declarations did not come out is one it has
            // something to say about — left out, every measure of it would read as a measure nobody
            // asked for.
            CheckSurface module = compilation.db()
                    .ask(new souther.compiler.query.Shapes.CheckSurface(name)).value();
            if (module == null) {
                continue;   // a module that did not get far enough to have behaviors
            }
            ModuleReport report = moduleReport(compilation, name, module);
            modules.add(report);
            overall = overall.union(report.weakenedBy());
        }
        return new AdequacyReport(SCHEMA_VERSION, ModuleMetadata.compilerVersion(),
                overall, List.copyOf(modules));
    }

    private static ModuleReport moduleReport(Compilation compilation, String name,
                                             CheckSurface module) {
        // The same reading every measure beside them reads, asked for rather than made again. Two
        // evaluations of one model can disagree — a row that ran out of time under the instrumented
        // one and held under the other — and a report whose counts came from one while its coverage
        // came from the other would say a case is verified and its arm unreached in the same breath.
        // The findings `--strict` exits on come from these same rows, so the exit code and what is
        // printed agree. This walked the sources itself and built the second of those two readings
        // (issue #996).
        //
        // Held to answering, unlike the measures below. A module got this far because its shapes
        // are prepared, which is the one thing the reading needs to answer for every behavior of
        // it — so an absence here is this report and that query disagreeing about what a module is,
        // and there is no reading of it that is not a guess.
        Map<String, Adequacy.RowReading> readings = Objects.requireNonNull(
                compilation.db().ask(new Adequacy.RowReadings(name)).value(),
                () -> "the rows of `" + name + "` were not read for or against");
        Map<String, Adequacy.SignatureEvidence> signatures =
                compilation.db().ask(new Adequacy.Witnesses(name)).value();
        Map<String, PartitionEvidence> partitions =
                compilation.db().ask(new Adequacy.Coverage(name)).value();
        Map<String, Measure<List<BorderAssessment>>> lines =
                compilation.db().ask(new Adequacy.BoundaryReadings(name)).value();
        // What each behavior is owed at those lines, once per point: the behaviors' projection of
        // the module's one relation, which the findings and the verdict read as well.
        Map<String, Measure<List<BorderObligationPointAssessment>>> accounts =
                compilation.db().ask(new Adequacy.BodyBorders(name)).value();
        Map<String, Adequacy.BranchEvidence> branches =
                compilation.db().ask(new Adequacy.BranchCoverage(name)).value();
        // The rules of each body's decision and which of them the rows took, beside the arms and
        // never among them. Asked once for the module, the way the arms are: read per behavior,
        // what a page costs would grow with its behaviors reading one another's bodies.
        Map<String, DecisionEvidence> decisions =
                compilation.db().ask(new Adequacy.Decides(name)).value();
        // What each body declared, read where it was judged. Beside the measures and never inside
        // one: this report is where the two are put together.
        Map<String, ClaimAnnotations> claims =
                compilation.db().ask(new Bodies.Claimed(name)).value();
        // The lines this report prints and the warnings a build is given are the same list, asked for
        // once here. A second reading of the evidence would be a second statement of what a gap is.
        List<Adequacy.Finding> findings =
                Adequacy.accountOf(compilation.db(), name);
        List<BehaviorReport> behaviors = new ArrayList<>();
        for (Hir.BehaviorDef behavior : module.behaviors()) {
            // Asked of the answer, and not chosen between its states from what the answer did not
            // say. `NOT_ASKED` and `NONE` are both things the reading says — a build that reads no
            // rows, and a reading that finished and found none — so picking one from an absent key
            // is a reader deciding what the producer answered (issue #996).
            Adequacy.RowReading reading =
                    Adequacy.RowReadings.readingFor(readings, behavior.name());
            // Anything larger than a behavior holds this one: a source that could not be evaluated is
            Adequacy.SignatureEvidence signature =
                    signatures == null ? null : signatures.get(behavior.name());
            // Null where the coverage did not answer at all, which is the compile not having got
            // that far and is not this behavior having nothing to cover. Read and never worked out:
            // a coverage that answered answers for every behavior of the module, so what a key is
            // for is what the measure said about it — this used to reach `NONE` for a composition
            // by asking the declarations again, which is a reader settling what a measure means.
            PartitionEvidence partition = partitions == null ? null
                    : partitions.get(behavior.name());
            // Null where the compile did not get far enough to be asked, which is not a measure that
            // came back with nothing. Every measure that did run says why it has no number.
            Adequacy.BranchEvidence branch =
                    branches == null ? null : branches.get(behavior.name());
            // The lines this behavior's positions met, held beside the account made from them: what
            // a block shows of a border is its four points, and whose debt each of them is is a
            // different question.
            Measure<List<BorderAssessment>> read =
                    lines == null ? null : lines.get(behavior.name());
            // Read once and used twice: what this page shows, and the rules it may send a reader
            // to. Asked of the module's whole list a second time, what it costs to assemble a
            // module would grow with its behaviors times its findings, and every one of them is
            // about some other behavior.
            List<ReportedFinding> reported = ofBehavior(compilation, name, findings,
                    behavior.name());
            // What the search of the rules no row took came to. Asked where the account asks it
            // and under the same guard, so a page costs a module nothing the findings did not
            // already pay for.
            Map<DecisionRule, RuleSettlement> requirements = ruleSettlements(compilation, name,
                    behavior.name(),
                    decisions == null ? null : decisions.get(behavior.name()));
            // The measures this behavior was made of, held as one value before the page is: what
            // the page may send a reader to is asked of it, and a whole that answers for its parts
            // is the one thing that has to be in reach for that question to be asked at all.
            BehaviorEvidence evidence = new BehaviorEvidence(reading, signature, partition, read,
                    accounts == null ? null : accounts.get(behavior.name()), branch,
                    decisions == null ? null : decisions.get(behavior.name()));
            behaviors.add(new BehaviorReport(behavior.name(),
                    module.implementationOf(behavior),
                    evidence,
                    claims == null ? ClaimAnnotations.NONE
                            : claims.getOrDefault(behavior.name(), ClaimAnnotations.NONE),
                    reported,
                    armPlaces(compilation, branch),
                    conditionPlaces(compilation, linesOf(read)),
                    rulePlaces(compilation, citedBy(evidence, reported)),
                    partPlaces(compilation, partition, reported),
                    ruleReadings(compilation, name, behavior.name(),
                            decisions == null ? null : decisions.get(behavior.name()),
                            requirements),
                    requirements));
        }
        Adequacy.DeclaredBoundaries declared =
                compilation.db().ask(new Adequacy.DeclaredBorders(name)).value();
        // What this module's own block shows, read once for the same reason a behavior's is.
        List<ReportedFinding> owed = findings == null ? List.of()
                : findings.stream()
                        .filter(each -> !(each.subject() instanceof FindingSubject.OfABehavior))
                        .map(each -> reported(compilation, name, each))
                        .toList();
        return new ModuleReport(name, compilation.sourceIdOf(name), behaviors, owed,
                // The declarations' own block names conditions too, and the lines it names them
                // under are the debts' rather than any behavior's.
                new DeclarationsShown(declared,
                        conditionPlaces(compilation, declaredLines(declared)),
                        rulePlaces(compilation, citedByDeclarations(declared, owed))));
    }

    /** The lines a module's declarations were read at, which is where the block about them looks
     *  for what a search came to. */
    private static List<BorderAssessment> declaredLines(Adequacy.DeclaredBoundaries owed) {
        if (owed == null) {
            return List.of();
        }
        List<BorderAssessment> lines = new ArrayList<>();
        owed.owed().forEach(each -> lines.addAll(each.debt().met().values()));
        return lines;
    }

    /**
     * The declarations' findings that one of {@code shown} carries.
     *
     * <p>Asked of the debt, which knows which behaviors read the line. A line no behavior in this
     * report carries is work nobody reading it can do, and one that some behavior here carries is
     * work a row written in front of the reader settles.
     */
    private static List<ReportedFinding> carriedBy(List<ReportedFinding> declarations,
                                                   List<BehaviorReport> shown) {
        Set<String> names = shown.stream().map(BehaviorReport::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return declarations.stream()
                .filter(each -> !(each.about()
                        instanceof About.APointOfADeclaredBorder(var owed))
                        || names.stream().anyMatch(owed.debt()::carriedBy))
                .toList();
    }

    /**
     * The findings about one behavior, each with where this report shows it.
     *
     * <p>Grouped here, where a block per behavior is printed, and not by the measure: what each
     * finding is about is its own answer. Placed here too, and this is the only moment it happens —
     * the rule is {@link Adequacy#placeOf}'s, which is also what the warnings a build reads use, so
     * a finding cannot be shown in one place on the page and another on the command line.
     */
    private static List<ReportedFinding> ofBehavior(Compilation compilation, String module,
                                                    List<Adequacy.Finding> findings, String name) {
        return findings == null ? List.of()
                : findings.stream().filter(each -> each.subject().isBehavior(name))
                        .map(each -> reported(compilation, module, each)).toList();
    }

    /**
     * How each rule a finding of this behavior is about is told to a reader.
     *
     * <p>Resolved here because naming the arm a condition goes through takes the plan that
     * numbered the places, and a page is not where that is asked — the same reason the arm places
     * beside this are worked out at assembly.
     *
     * <p>One entry per finding about a rule and nothing else, so a page describes a rule by every
     * condition it turns on: read off whichever conditions a walk happened to have words for,
     * two rules of one behavior would be shown the same sentence with nothing under it.
     */
    private static Map<DecisionReading.Ruled, List<ShownCondition>> ruleReadings(
            Compilation compilation, String module, String behavior, DecisionEvidence decision,
            Map<DecisionRule, RuleSettlement> requirements) {
        if (decision == null) {
            return Map.of();
        }
        CoverageSites.Plan plan = placesOf(compilation, module);
        Map<DecisionReading.Ruled, List<ShownCondition>> out = new LinkedHashMap<>();
        for (DecisionReading.Ruled rule : decision.read().found()) {
            // The rules a finding is about, which are the ones a page describes condition by
            // condition. What is owed no row is counted under its reason rather than written out,
            // so describing one would be work for a line nobody reads.
            RuleSettlement came = requirements.get(rule.rule());
            if (came == null || !(came.requirement() instanceof RuleRequirement.Required)) {
                continue;
            }
            List<ShownCondition> shown = new ArrayList<>();
            for (DecisionRuleReading read : DecisionRuleReading.of(rule, plan, behavior)) {
                shown.add(new ShownCondition(read, whereItIsWritten(compilation, read)));
            }
            out.put(rule, List.copyOf(shown));
        }
        return out;
    }

    /**
     * What the search settled about each rule of {@code behavior} no row took.
     *
     * <p>Under the guard the account's own findings are under, and the same query answers both. A
     * search of the rules is what a decision account costs, so a page asking for one where the
     * findings did not would make reading a module dearer than deciding what it owes.
     *
     * <p>Empty where no reading of the runs was made. What is missing where nothing was read is
     * not a set of anything, and a rule this compiler never looked for a row at is not one it
     * looked for and did not find.
     */
    private static Map<DecisionRule, RuleSettlement> ruleSettlements(Compilation compilation,
                                                                      String module,
                                                                      String behavior,
                                                                      DecisionEvidence decision) {
        if (decision == null || decision.took().made().isEmpty()
                || decision.notTakenByRows().isEmpty()) {
            return Map.of();
        }
        Map<DecisionRule, RuleSettlement> settled = compilation.db()
                .ask(new Adequacy.DecisionSearch(module, behavior)).value();
        return settled == null ? Map.of() : settled;
    }

    /**
     * Where the construct one condition of a rule was drawn by is written, or null where there is
     * none to send a reader to.
     *
     * <p>Asked here and not off the arm places beside it. Those are the arms the branch measure
     * holds, and a rule turns on the arms of whatever its way went through — a body whose branch
     * measure came to no answer states rules all the same, and asking that table for one of their
     * arms is a page failing on an arm it was never assembled with.
     */
    private static Citation whereItIsWritten(Compilation compilation, DecisionRuleReading read) {
        return switch (read) {
            case DecisionRuleReading.AComparisonCameOut(var comparison, var _) -> comparison.at();
            case DecisionRuleReading.AForkTookAnArm(var arm) ->
                    Sites.placeOf(compilation.db(), arm.anchor());
            case DecisionRuleReading.AConditionIsNotShown _,
                    DecisionRuleReading.AComparisonIsNotPlaced _,
                    DecisionRuleReading.AForkIsNotPlaced _ -> null;
        };
    }

    /**
     * One condition of a rule, with where a reader is sent for it.
     *
     * <p>The place beside the reading and not inside it. What each condition of a rule is is the
     * same for a page and for a warning; where a page can send a reader is a question only a page
     * asks, and it is answered where the plan that numbered the places is in reach.
     *
     * @param at where the construct that drew it is written, or null where there is none
     */
    public record ShownCondition(DecisionRuleReading read, Citation at) {

        public ShownCondition {
            java.util.Objects.requireNonNull(read, "a condition shown is some condition");
        }
    }


    /** Where this compilation numbered the places a run through each construct is recorded. */
    private static CoverageSites.Plan placesOf(Compilation compilation, String module) {
        Bodies.Elaborated checked =
                compilation.db().ask(new Bodies.Checked(module)).value();
        return checked == null ? CoverageSites.Plan.NONE : checked.plan();
    }

    /**
     * Where this report shows each arm of one behavior.
     *
     * <p>Built from the branch evidence itself, so that every arm the page may name has an entry
     * and nothing else does — a lookup gathered from anywhere else would be a second collection
     * whose agreement with the arms is somebody's to keep true.
     *
     * <p>Asked once per arm and not once per mention: an arm is named on the summary line, under
     * the findings and again in the document, and the place is the same answer each time.
     */
    private static Map<ArmReportAnchor, Citation> armPlaces(Compilation compilation,
                                                            Adequacy.BranchEvidence branch) {
        ArmSummary counted = branch == null ? null : branch.measured().made().orElse(null);
        if (counted == null) {
            return Map.of();
        }
        Map<ArmReportAnchor, Citation> places = new LinkedHashMap<>();
        for (ArmObligation arm : counted.all()) {
            for (CoverageSites.ArmSite each : arm.occurrences()) {
                places.computeIfAbsent(each.anchor(),
                        anchor -> Sites.placeOf(compilation.db(), anchor));
            }
        }
        return places;
    }

    /**
     * Where this report shows each condition on the way to one of these lines.
     *
     * <p>Built from the searches themselves, so that every condition the page may name has an entry
     * and nothing else does. Asked once per condition and not once per mention: a condition is
     * named under whichever searches met it, and the place is the same answer each time.
     *
     * <p>Every attempt of every point, and not the ones a page happens to print. Which searches are
     * worth a sentence is the outcomes' answer and is asked where the sentence is written; a
     * gathering that asked it here would be that decision made twice, and the day the two disagreed
     * a sentence would be written about a condition with nowhere to point.
     */
    private static Map<ConditionReportAnchor, Citation> conditionPlaces(
            Compilation compilation, List<BorderAssessment> lines) {
        Map<ConditionReportAnchor, Citation> places = new LinkedHashMap<>();
        for (BorderAssessment line : lines) {
            for (BorderAssessment.Point point : line.points()) {
                // A point nobody is owed a row at ran no search, so there is nothing under it to
                // point at. Asked all the same, this would be reading an absence as an empty list.
                if (point.owed() == null) {
                    continue;
                }
                for (ItemAssessment.Attempt attempt : point.owed().searches().each()) {
                    for (ReachabilityGap gap : attempt.unaccountedFor()) {
                        places.computeIfAbsent(gap.anchor(),
                                anchor -> Sites.placeOf(compilation.db(), anchor));
                    }
                }
            }
        }
        return places;
    }

    /**
     * Where this report shows each rule its page may send a reader to.
     *
     * <p>Built from the evidence itself, so that every rule the page may name has an entry and
     * nothing else does, for the reason {@link #conditionPlaces} gives. Which of them is worth a
     * sentence is decided where the sentence is written; a gathering that asked it here would be
     * that decision made twice.
     *
     * <p><b>Asked of what read the rules, and never gathered from where their outcomes were
     * filed.</b> Every value holding a handle answers one question ({@link RuleCitations}), and a
     * whole answers by asking its parts — so what this takes in is already the closure, and there
     * is no list here to keep in step. Listed instead, a reading filed somewhere new is a reading
     * nothing points at, nothing fails, and the page names a class with no handle for the rule that
     * made it.
     *
     * <p>Only the rules with somewhere to be asked about get an entry: a rule the author named is
     * found by that name from anywhere. A rule two readers hold a handle for is one entry — what is
     * asked about is the handle, and two readers offering one handle offer one value.
     */
    private static Map<RuleCitation.Written, Citation> rulePlaces(
            Compilation compilation, Set<RuleCitation> cited) {
        Map<RuleCitation.Written, Citation> places = new LinkedHashMap<>();
        for (RuleCitation each : cited) {
            if (each instanceof RuleCitation.Written written) {
                places.computeIfAbsent(written, it -> Sites.placeOf(compilation.db(), it));
            }
        }
        return places;
    }

    /**
     * Every handle the page about one behavior may send a reader to.
     *
     * <p>Two, because a page shows two things: the measures this behavior was made of, which
     * answer for the rules they read, and the findings written under it, which answer for the rules
     * they are about. A finding is not a measure — it is what a measure came to, kept beside it —
     * so neither is recovered from the other.
     */
    private static Set<RuleCitation> citedBy(BehaviorEvidence evidence,
                                             List<ReportedFinding> found) {
        Set<RuleCitation> cited = new LinkedHashSet<>(evidence.ruleCitations());
        cited.addAll(citedBy(found));
        return cited;
    }

    /**
     * Every handle the block about a module's declarations may send a reader to.
     *
     * <p>The lines the declarations drew, which are read at no behavior's page, and the findings
     * about them. There is no behavior here and so no measures to ask: what a module's declarations
     * are owed is read off the lines themselves.
     */
    private static Set<RuleCitation> citedByDeclarations(Adequacy.DeclaredBoundaries declared,
                                                         List<ReportedFinding> found) {
        Set<RuleCitation> cited = new LinkedHashSet<>();
        declaredLines(declared).forEach(line -> cited.addAll(line.ruleCitations()));
        cited.addAll(citedBy(found));
        return cited;
    }

    /**
     * Every handle the findings hold.
     *
     * <p>Asked of what a finding is about, by the same question every value holding a handle
     * answers ({@link RuleCitations}). Matched against the kinds that happen to be about a rule
     * instead, a kind added later is a kind whose rules a page names with nowhere to point — which
     * is the fault this gathering was rewritten to keep out, one seal over.
     */
    private static Set<RuleCitation> citedBy(List<ReportedFinding> found) {
        Set<RuleCitation> cited = new LinkedHashSet<>();
        if (found != null) {
            found.stream().map(ReportedFinding::about)
                    .filter(RuleCitations.class::isInstance)
                    .map(RuleCitations.class::cast)
                    .forEach(each -> cited.addAll(each.ruleCitations()));
        }
        return cited;
    }

    /**
     * Where each part of a rule this report points inside is written.
     *
     * <p>Asked once, here, and by the same rule the rules' own places are: what a reading decided
     * says which part it was about and never where that part is, so the places are looked up where
     * the compilation is still in reach and travel with the report instead of with the judgment.
     *
     * <p>Every way a finding names a part is read, and each of them through the one thing that
     * says so ({@link RuleSite}). A part named by two of them is one entry — what is asked about is
     * the part, and two readers naming one part name one place.
     */
    private static Map<RuleSite, Citation> partPlaces(
            Compilation compilation, PartitionEvidence partition, List<ReportedFinding> found) {
        Map<RuleSite, Citation> places = new LinkedHashMap<>();
        Consumer<RuleSite> take = sentTo -> {
            switch (sentTo) {
                case RuleSite.TheRuleItself _ -> { }
                case RuleSite.APartOfIt it -> places.computeIfAbsent(it,
                        _ -> Sites.placeOf(compilation.db(), it.part()));
                case RuleSite.AConstructTheAuthorWrote it -> places.computeIfAbsent(it,
                        _ -> Sites.placeOf(compilation.db(), it.origin()));
            }
        };
        if (partition != null) {
            partition.unanswered().forEach(each ->
                    whyStanding(each).forEach(stop -> {
                        take.accept(stop.about());
                        take.accept(stop.sentTo());
                    }));
            partition.notRead().forEach(each -> {
                if (each instanceof PartitionEvidence.NotRead.ARule rule) {
                    take.accept(rule.finding().sentTo());
                }
            });
        }
        if (found != null) {
            found.stream().map(ReportedFinding::finding).map(Adequacy.Finding::about)
                    .filter(About.ARuleWithoutALine.class::isInstance)
                    .map(About.ARuleWithoutALine.class::cast)
                    .forEach(each -> take.accept(each.finding().finding().sentTo()));
        }
        return places;
    }

    /**
     * The handles each rule that composed a position's classes offers, one entry per rule.
     *
     * <p>Grouped by the rule and not by the reading. What tells two readings apart is which of them
     * divided what, and that is a fact about the readings; what a reader is sent to is the rule the
     * author wrote, and a sentence per reading of it is the same rule said as many times as this
     * compiler happened to meet it.
     *
     * <p>In the order the rules were read, which is the order the axis holds them in. An order of
     * this method's own would be a second answer to a question the reading already settled.
     */
    private static List<Set<RuleCitation>> dividedBy(PartitionEvidence.AxisCoverage axis) {
        Map<RuleRef, Set<RuleCitation>> byRule = new LinkedHashMap<>();
        for (RuleEvidenceOrigin origin : axis.divides()) {
            byRule.computeIfAbsent(origin.rule(), _ -> new LinkedHashSet<>()).add(origin.cited());
        }
        return List.copyOf(byRule.values());
    }

    /** The lines one behavior met, or none where the measure could not be made. */
    private static List<BorderAssessment> linesOf(Measure<List<BorderAssessment>> read) {
        return read == null ? List.of() : read.made().orElse(List.of());
    }

    /** One finding with where this report shows it. */
    private static ReportedFinding reported(Compilation compilation, String module,
                                            Adequacy.Finding finding) {
        return new ReportedFinding(finding,
                Adequacy.placeOf(compilation.db(), module, finding));
    }

    /**
     * What the module's declarations are short of, under the declaration that wrote the rule.
     *
     * <p>Under the declaration and not under a behavior, because that is where an author fixes it. A
     * line an {@code invariant} drew is a fact about the type — whether a row standing at the
     * boundary of {@code UserId} is believed is a question about {@code UserId} — and it is met by a
     * row written anywhere the type is carried. Printed under a behavior, it would have to be
     * printed under whichever one a walk reached first, and an author sent there would be sent to a
     * body that says nothing about the length of a user id (issue #1062).
     *
     * <p>The two points against the line and no others. What a row well inside the border shows is
     * about the region of one position, so it stays with that position's behavior — which is why
     * this block holds one kind of line and the block above still holds both.
     *
     * <p>After the behaviors, so that a reader who has just read what each body is short of reads
     * what the model itself is short of once.
     */
    private void declared(StringBuilder out, ModuleReport module, SourceRendering rendering,
                          PublishedRuleHandle.WhereARuleIs places) {
        // What the declarations are owed, folded the way a behavior's own account is and by the same
        // fold. Here rather than in the blocks above, because that is where the work is: a line a
        // `data` clause drew is owed once for the module, and a behavior carrying the type is short
        // of nothing on its account.
        List<Adequacy.DeclaredDebt> debts = module.debts();
        ObligationSummary<Adequacy.DeclaredDebt> account =
                ObligationSummary.of(debts, each -> each.debt().owed());
        if (!debts.isEmpty()) {
            out.append(String.format("  declarations   obligations %d/%d%n",
                    account.met().size(), account.counted()));
        }
        // Every obligation the count holds and no row is at, so that the difference between the two
        // numbers is a difference a reader can walk. A point nobody can say is missed is not a gap
        // and is under no finding, and this is the only place it can be said at all.
        //
        // Every one of them and not the ones whose question this block finds interesting. What a
        // reader does is walk from a number to the work it names, so an obligation inside the
        // denominator with no line under it is a difference nothing can be done about — which holds
        // of a point nobody read as much as of one nothing could show writable.
        for (Adequacy.DeclaredDebt each : account.undecided()) {
            for (String said : undecidedBecause(each.debt().owed().disposition(),
                    pointOf(each, RuleHandleProse.said(each.debt().describe(places),
                            rendering, null)))) {
                out.append(String.format("      ? %s%n", said));
            }
            readings(out, each.debt(), _ -> true, at -> whatWasTried(
                    at.owedAt(each.debt().at()).searches(),
                    module.owedByDeclarations().conditionPlaces(), rendering, null));
        }
        Map<String, List<Adequacy.Finding>> byDeclaration = new LinkedHashMap<>();
        for (ReportedFinding each : module.declarations()) {
            byDeclaration.computeIfAbsent(each.finding().named(), _ -> new ArrayList<>())
                    .add(each.finding());
        }
        byDeclaration.forEach((declaration, findings) -> {
            out.append(String.format("  %s%n", declaration));
            for (Adequacy.Finding f : findings) {
                if (f.about() instanceof About.APointOfADeclaredBorder(var owed)) {
                    // What the point asks, in its own words. A point against the line names a value
                    // and a point beside it names a run, and a sentence that wrote `=` for both said
                    // a run was one value.
                    out.append(String.format("      %s no row is at the %s%n",
                            mark(f), pointOf(owed, owed.debt().id().saidWithoutAPlace())));
                }
            }
        });
    }

    /**
     * One point of a declaration's line, as a sentence names it.
     *
     * <p>Two shapes, and which it is is the debt's answer rather than a reading of the words. A
     * point on a quantity the declaration has a name for says what a row there has to do and then
     * which rule drew the line; a point on a line between two positions has no such quantity — the
     * level is a distance from the other position, which is a reading's name for it — so the line
     * and the role on it is the whole of what there is to say (issue #1251).
     */
    private static String pointOf(Adequacy.DeclaredDebt owed, String rule) {
        return owed.namesItsQuantity()
                ? owed.debt().role() + " point" + owed.debt().whichSide() + " " + owed.said()
                        + " (" + rule + ")"
                : owed.debt().role() + " point of " + rule;
    }

    /** This report with only the modules and behaviors the caller asked about. A name that matches
     * nothing leaves an empty report rather than the whole one. */
    public AdequacyReport only(String module, String behavior) {
        List<ModuleReport> kept = new ArrayList<>();
        WeakeningSet overall = WeakeningSet.none();
        for (ModuleReport m : modules) {
            if (module != null && !module.equals(m.module())) {
                continue;
            }
            List<BehaviorReport> behaviors = behavior == null ? m.behaviors()
                    : m.behaviors().stream().filter(b -> behavior.equals(b.name())).toList();
            // What a filtered report says is about what it shows, and nothing here arranges that.
            // The reasons are what the behaviors shown went without, so dropping a behavior drops
            // what only it carried and keeps what a whole source cost every one of them. That was a
            // filter over a list of the module's own, which is a second statement of who a reason
            // counts against — asked of the reason where it belongs (issue #996).
            // What the module's declarations are short of, kept where a behavior that is shown
            // carries the line. A line an `invariant` drew is not any behavior's, and it is
            // discharged by a row written for any behavior carrying the type — so it is work the
            // reader of this report can do, and a verdict that kept a line none of the behaviors
            // shown carries would be a verdict about what the reader cannot see.
            // And the debts those behaviors carry, for the same reason and by the same question:
            // a verdict about a line none of them carries is a verdict about what the reader
            // cannot see.
            Set<String> names = behaviors.stream().map(BehaviorReport::name)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            // And the account of what its declarations are owed, as a reader shown these behaviors
            // is owed it. Which parts of it that is is the account's own answer: a debt none of them
            // carries and a reading none of them made are both about what this reader cannot see,
            // and deciding that here would be this view working out what an account means.
            ModuleReport one = new ModuleReport(m.module(), m.declaredIn(), behaviors,
                    carriedBy(m.declarations(), behaviors),
                    // The debts narrow with the behaviors shown; the places do not. A filtered view
                    // shows fewer debts and the conditions under the ones it does show are the same
                    // conditions, so narrowing the places would be one going missing from a
                    // sentence the view still writes.
                    new DeclarationsShown(m.owedByDeclarations().owed() == null ? null
                            : m.owedByDeclarations().owed().keptFor(names),
                            m.owedByDeclarations().conditionPlaces(),
                            m.owedByDeclarations().rulePlaces()));
            kept.add(one);
            overall = overall.union(one.weakenedBy());
        }
        return new AdequacyReport(schemaVersion, compilerVersion, overall, List.copyOf(kept));
    }

    /** How many rows are recorded and waiting for a {@code let}, across everything reported.
     *
     * <p>Reported and never gated on. Waiting is the normal state of a model being written, and a
     * build that refused one would refuse the practice of recording what an injected behavior owes. */
    public int pendingRows() {
        return modules.stream().flatMap(m -> m.behaviors().stream())
                .map(BehaviorReport::pending)
                .filter(OptionalInt::isPresent)
                .mapToInt(OptionalInt::getAsInt).sum();
    }

    /**
     * Everything the measures found, across everything reported.
     *
     * <p>What the declarations are short of as well as what the bodies are. A line an
     * {@code invariant} drew is not any behavior's, and a walk over the behaviors alone left it out
     * of the verdict — so a report printed a gap and said the rows covered the model under it
     * (issue #1062).
     */
    public List<Adequacy.Finding> findings() {
        return Stream.concat(
                        modules.stream().flatMap(m -> m.behaviors().stream())
                                .flatMap(b -> b.findings().stream()),
                        modules.stream().flatMap(m -> m.declarations().stream())
                                .map(ReportedFinding::finding))
                .toList();
    }

    /** The findings a build is entitled to refuse: a measure came to an answer and the answer was
     *  that something the rows are asked for is not there. */
    public List<Adequacy.Finding> adequacyGaps() {
        return findings().stream().filter(Adequacy.Finding::isAdequacyGap).toList();
    }

    /**
     * Whether the rows meet what the account asks of them.
     *
     * <p>Derived on every call rather than held, because {@link #only(String, String)} makes a report
     * of part of this one and a verdict about the whole would be a verdict about behaviors that report
     * does not show.
     *
     * <p>A gap is answered before anything about how much was measured: one is enough, whatever else
     * could not be measured, which is what {@link AdequacyStatus#NOT_SATISFIED} says it means. That
     * order is the whole of what a gap and a doubt are: {@code NOT_SATISFIED} is something shown and
     * {@code UNDETERMINED} is the absence of a showing, so a point nobody could decide does not
     * weaken a gap somebody found.
     *
     * <p><b>And an obligation nobody could decide is read from the obligation.</b> What the readings
     * came to is one of the two answers a point stands on; whether anything showed a row could be
     * written there is the other, and a verdict resting on the first alone called a model satisfied
     * over a point where every row was read, none was at it, and nothing could say whether one could
     * be (issue #1249). Which obligations the verdict is about is {@link #requiredObligations()},
     * and this reads the standing of each of them.
     *
     * <p>No measure to be short of is not a doubt. A model nothing can be refused about — every
     * measure it reads inapplicable — has been asked and has nothing to answer
     * for, so it is satisfied rather than undetermined; {@code undetermined} is for a measure that
     * could have found a gap and was not made. Answered the other way, this reported a doubt nobody
     * could act on and no row could settle, and it was doing it on the strength of a list that had
     * dropped exactly the measures nobody was going to make (issue #955).
     */
    public AdequacyStatus adequacy() {
        if (!adequacyGaps().isEmpty()) {
            return AdequacyStatus.NOT_SATISFIED;
        }
        // Every measure the verdict rests on came to an answer nothing weakened. A measurement made
        // in part is one whose gaps may not be gaps, and one that could not be finished came to no
        // answer at all — neither settles a verdict.
        //
        // The support evidence and the domain measures, which are two questions. This used to ask
        // the second and then reach past it for a list of reasons: a reason about a measure nothing
        // rests on held the verdict open, and a reason no measure carried held it open on nobody's
        // authority (issue #996).
        return Stream.concat(requiredSupport().stream(), requiredEvidence().stream())
                        .map(Owned::value)
                        .allMatch(m -> m instanceof Measurement.Complete<?>)
                        && requiredObligations().stream().map(Owned::value).noneMatch(
                                owed -> owed.disposition() instanceof ObligationDisposition.Undecided)
                ? AdequacyStatus.SATISFIED : AdequacyStatus.UNDETERMINED;
    }

    /**
     * What the verdict is open on: what the things it rests on went without, and the ones nobody
     * made.
     *
     * <p>Read from the same three lists {@link #adequacy()} is decided by, and never from
     * {@code weakenedBy}. What left this whole report weaker than it looks and what holds this
     * verdict open are not one set: a measure no answer here rests on may be as partial as it
     * likes without the verdict being any less settled, and the report says so of itself either
     * way. Taken from the wider list, a reader would be shown causes that no answer here rests on.
     *
     * <p>A set and so counted once per fact, however many measures went without it. One rule this
     * compiler could not read is one thing to tell a person whichever measure noticed, and counting
     * the measures would be counting the paths a fact arrived by ({@link WeakeningSet}).
     *
     * <p><b>Empty where the verdict is settled, and that is what the name says.</b> A gap outranks
     * everything about how much was measured, so a refused build has an answer and is open on
     * nothing — whatever else about it went unmeasured, which the measures themselves say. Answered
     * the other way, this said a settled verdict was held open by something; asked only where a
     * reader was rendering, the same report came to one answer on the page and another in the
     * document.
     */
    public List<AdequacyOpening> whatKeepsTheVerdictOpen() {
        if (adequacy() != AdequacyStatus.UNDETERMINED) {
            return List.of();
        }
        // The facts first, folded once. Two measures that went without the same thing went without
        // one thing, and putting them together is what says so.
        WeakeningSet facts = WeakeningSet.none();
        List<AdequacyOpening> rest = new ArrayList<>();
        for (Owned<Measurement<?>> each : requiredSupport()) {
            facts = facts.union(each.value().weakening());
            openedBy(rest, each.subject(), each.value());
        }
        for (Owned<Measure<?>> each : requiredEvidence()) {
            facts = facts.union(each.value().weakening());
            if (each.value() instanceof Measurement<?> measured) {
                openedBy(rest, each.subject(), measured);
            }
        }
        for (Owned<ObligationAssessment> each : requiredObligations()) {
            facts = facts.union(each.value().weakening());
            openedBy(rest, each.subject(), each.value().disposition());
        }
        List<AdequacyOpening> out = new ArrayList<>();
        facts.causes().forEach(each -> out.add(new AdequacyOpening.ByWeakening(each)));
        out.addAll(rest);
        return out;
    }

    /**
     * What one measurement opens the verdict on beside the facts it went without.
     *
     * <p>A {@code switch} over the states with no {@code default}, because this is the same question
     * {@link #adequacy()} asks of the same value and the two must not be able to answer differently.
     * Asked as "what did it go without", a measurement nobody made answered nothing while the
     * verdict over it stayed open — and the arm that says so is the one a fold would never reach.
     *
     * <p>The other three states need nothing here. A complete measurement opens nothing, and the
     * two that are short of something refuse an empty {@code WeakeningSet} at construction, so the
     * union above already holds at least one fact for each of them.
     */
    static void openedBy(List<AdequacyOpening> out, Subject subject, Measurement<?> measured) {
        switch (measured) {
            case Measurement.NotMeasured<?> never ->
                    out.add(new AdequacyOpening.NotMeasured(subject, never.why()));
            case Measurement.Complete<?> _, Measurement.Partial<?> _,
                 Measurement.FailedToMeasure<?> _ -> { }
        }
    }

    /**
     * And what one obligation opens it on, which is what it is undecided about.
     *
     * <p>Read from the disposition and never from the coverage beneath it. Those are two questions
     * and {@link #adequacy()} asks the first: whether a row can be written at a point is settled
     * from the coverage <em>and</em> what showed a row is writable, so three of the four ways an
     * obligation is undecided leave the coverage with nothing to have gone without. Asked of the
     * coverage, a point nothing could show a row for held the verdict open and named nothing.
     *
     * <p>A reading that stopped adds nothing here, and that is not an omission: what it met is the
     * coverage's own {@code WeakeningSet}, which the union above already holds. Counted again it
     * would be one fact said twice, which is what a set is for.
     *
     * <p>{@code Undecided} refuses an empty list at construction and every arm below yields an
     * entry, so an obligation that holds the verdict open cannot come back with nothing.
     */
    static void openedBy(List<AdequacyOpening> out, Subject subject,
                         ObligationDisposition disposition) {
        if (!(disposition instanceof ObligationDisposition.Undecided undecided)) {
            return;
        }
        for (ObligationDisposition.Uncertainty each : undecided.because().written()) {
            switch (each) {
                case ObligationDisposition.Uncertainty.WhetherARowIsThere.ReadingsStopped _ -> { }
                // One opening, which says one reason. What the readings gave is a set, and asking
                // for it as one is where an opening with no room for the second says so.
                case ObligationDisposition.Uncertainty.WhetherARowIsThere.NothingWasRead it ->
                        out.add(new AdequacyOpening.NotMeasured(subject, it.why().asOne()));
                case ObligationDisposition.Uncertainty.WhetherARowCanBeWritten.Stopped it ->
                        it.by().by().written().forEach(gap ->
                                out.add(new AdequacyOpening.ShowingStopped(subject, gap)));
                case ObligationDisposition.Uncertainty.WhetherARowCanBeWritten.NothingShowedIt _ ->
                        out.add(new AdequacyOpening.NothingShowedARowCanBeWritten(subject));
            }
        }
    }

    /**
     * How much of that a run of this compiler allowed more could answer, and how much it could not.
     *
     * <p>The question a reader of {@code undetermined} has and the report could not answer. The
     * word means a measure that could have found a gap and was not made, which is as true of a
     * space too large to walk as of a rule this compiler has no reading for — and the first is a
     * number away while nothing anybody writes reaches the second. Told apart nowhere, a person
     * reading a model that forks on a list it computed was left to work out for themselves that
     * measuring again would find exactly the same thing.
     *
     * <p>Counted over the facts and not over the words a document writes for them. Two facts a
     * document calls the same thing can differ here — a pattern too large to build and a rule about
     * a value made from this one are both {@code rule_unread} — so a count taken off the printed
     * words would be a count of something else.
     *
     * @param mayChange how many of them an allowance of this compiler's stopped
     * @param unaffected how many no allowance stopped, which is what allowing more does not reach.
     *                   Not "how many it had no reading for": a measure nobody made and a point
     *                   nothing showed a row writable at are both here and neither is a reading.
     *                   Which kind of thing each was, is what the kind beside it says
     */
    public record UnderAWiderRun(int mayChange, int unaffected) {

        /** Whether there is anything to say, which is whether anything is open at all. */
        public boolean isEmpty() {
            return mayChange == 0 && unaffected == 0;
        }
    }

    /** {@link #whatKeepsTheVerdictOpen()}, split by whether a wider run could answer it. */
    public UnderAWiderRun underAWiderRun() {
        int mayChange = 0;
        int unaffected = 0;
        for (AdequacyOpening each : whatKeepsTheVerdictOpen()) {
            // A switch with no default, so a third answer is a compile error here rather than one
            // more thing silently counted among what no allowance reaches.
            switch (each.runSensitivity()) {
                case MAY_CHANGE -> mayChange++;
                case UNAFFECTED -> unaffected++;
            }
        }
        return new UnderAWiderRun(mayChange, unaffected);
    }

    /**
     * What the verdict rests on that is not a measure of the model: the reading of the rows.
     *
     * <p>Every domain measure below is counted over the rows, so how far they were read is what
     * each of those answers is worth. A measure of the model that found no gap over rows that did
     * not all come back has found that no gap is <em>visible</em>, which is not the same answer and
     * is the one a verdict cannot be settled by.
     *
     * <p>Required whether or not any domain measure applies, which is where the two differ. "No
     * measure to be short of is not a doubt" is about the model: a behavior nothing can be refused
     * about has been asked and has nothing to answer for (issue #955). Its rows were still
     * read or not read, and that is a separate fact — a module every measure of which is
     * inapplicable and whose one row did not come back is undetermined, and every one of those
     * measures is entitled to say it went without nothing (issue #996).
     *
     * <p><b>Except where the build does not read rows at all.</b> That is not a reading that fell
     * short; it is this build saying it makes no measurement over rows, and every measure over them
     * says so too — so what holds such a verdict open is those measures, each on its own account.
     * Held open here as well, a build that measures nothing would be undetermined about a model
     * nothing can be refused about, which is the answer #955 took out.
     */
    private List<Owned<Measurement<?>>> requiredSupport() {
        List<Owned<Measurement<?>>> support = new ArrayList<>();
        for (ModuleReport module : modules) {
            for (BehaviorReport behavior : module.behaviors()) {
                Measurement<?> reading = behavior.reading().measured();
                if (!(reading instanceof Measurement.NotMeasured<?>)) {
                    support.add(new Owned<>(new Subject.OfAMeasure(module.module(),
                            behavior.name(), MeasureWord.READING), reading));
                }
            }
        }
        return support;
    }

    /**
     * The measures of the model this verdict rests on: the ones that could find a gap a build
     * refuses over.
     *
     * <p>Two questions and each asked of the one thing that answers it. Whether a measure was made,
     * and how much of it, is the measurement's own answer and is read from it. Which kinds of gap a
     * verdict needs an answer about is {@link Adequacy.Kind#isAboutAnObligation}, and is read from there — so a
     * measure that finds only what nobody is held to cannot leave the verdict undetermined for want
     * of an answer, and a measure that finds what they are held to cannot be left out.
     *
     * <p>Which is why each entry below names the kind it can find. Read as "everything that was
     * measured" instead, a verdict was undetermined for a position nobody had classified where no
     * row was owed at one, and settled while a position it did owe rows at went unread.
     *
     * <p>Whether a measure applies at all is the measure's own answer, and never the shape of what
     * came back. A behavior with no body has no arms, and a position whose rules the walk never
     * reached leaves no boundary behind — in both cases the numbers look exactly like a measure
     * that was made and found nothing, so a report reading them back would call the first adequate
     * and the second covered.
     */
    private List<Owned<Measure<?>>> requiredEvidence() {
        List<Owned<Measure<?>>> measures = new ArrayList<>();
        for (ModuleReport module : modules) {
            for (BehaviorReport behavior : module.behaviors()) {
                // The cases of the signature.
                if (behavior.signature() != null
                        && (owesARowAt(Adequacy.Kind.OUTPUT_CASE_UNSPECIFIED)
                                || owesARowAt(Adequacy.Kind.INPUT_CASE_UNSPECIFIED))) {
                    add(measures, new Subject.OfAMeasure(module.module(), behavior.name(),
                            MeasureWord.SIGNATURE), behavior.signature().counted());
                }
                if (behavior.branch() != null && owesARowAt(Adequacy.Kind.ARM_UNREACHED)) {
                    add(measures, new Subject.OfAMeasure(module.module(), behavior.name(),
                            MeasureWord.BRANCH), behavior.branch().measured());
                }
                // Which rules of the decision the rows took. A reading that could place none of
                // the rows has every rule of the
                // body left as one a row may already take, and a verdict resting on the findings
                // alone would call the model satisfied over exactly the rules nothing read.
                if (behavior.evidence().decision() != null
                        && owesARowAt(Adequacy.Kind.DECISION_RULE_UNCOVERED)) {
                    add(measures, new Subject.OfAMeasure(module.module(), behavior.name(),
                            MeasureWord.DECISION), behavior.evidence().decision().took());
                }
                if (behavior.partition() == null) {
                    continue;
                }
                // The measure answers for itself, and its entries answer for themselves. Read off
                // the entries alone, a measure that derived nothing contributed nothing and a
                // behavior whose every bound sits one type away from the position it takes came out
                // adequate on the strength of a measurement nobody made.
                //
                // The derivation of the lines, and not the derivation of the positions. A position
                // with a bound and no division — an `Int` a rule floors and nothing cuts — is an
                // ordinary shape whose boundary measure is made in full, and holding the verdict
                // open for it would say a model was unmeasured on the strength of the one measure
                // that was.
                if (owesARowAt(Adequacy.Kind.BOUNDARY_UNMET)
                        || owesARowAt(Adequacy.Kind.DOMAIN_POINT_UNCOVERED)) {
                    add(measures, new Subject.OfAMeasure(module.module(), behavior.name(),
                            MeasureWord.BOUNDARY), behavior.boundaryReadings());
                }
                // What the rows reach of each position, which finds a class no row is in.
                //
                // The derivation as well as the positions it produced, the way the lines are asked
                // for above. Which positions there are to cover is the first half of the answer: a
                // reading that did not run out produced the axes it reached and no others, so
                // walking those alone leaves a position nobody could derive looking exactly like a
                // position with nothing to cover — and a verdict was satisfied by the classes
                // nobody had found yet. A behavior the reading proved
                // divides nothing answers {@code NotApplicable} and is dropped below, so this holds
                // nothing open that was never going to be measured.
                if (owesARowAt(Adequacy.Kind.AXIS_CLASS_UNCOVERED)) {
                    add(measures, new Subject.OfAMeasure(module.module(), behavior.name(),
                            MeasureWord.PARTITION), behavior.partition().partitioned());
                    behavior.partition().axes().forEach(axis -> add(measures,
                            new Subject.OfAnAxisMeasure(module.module(), behavior.name(),
                                    axis.at()), axis.reached()));
                }
                // And of what this behavior is owed a row for, which is every point of it. A line
                // the declarations are owed is answered once for the module below, from every
                // reading of it, and is no part of this account: weighed here as well, a row
                // standing at it in one behavior would be weighed against another behavior having
                // no rows, and the verdict would hold open what the aggregation had settled.
                // One measurement per thing the behavior is owed a row for, since each of them is
                // an obligation: a place two of this body's rules drew a line at leaves a run owed
                // to each, and a verdict counting the role once would be short by the rest. How
                // many rows answer them is a different count and is the generator's.
                // All four of them, because all four are obligations: a build refusing over a
                // missing IN row and calling a model satisfied while the IN point could not be
                // measured would be answering two questions in one report.
                // A dropped axis is not asked after here. What it was carrying went with it and no
                // question stands for it, which is a fact about the measure's reading — so it
                // leaves the measure's own answer short of complete, and reading it back off the
                // list of what was dropped would be this report deciding a measure's status again.
            }
        }
        return measures;
    }

    /**
     * What the verdict rests on that is owed rather than measured: every obligation a row is owed
     * at.
     *
     * <p>Beside {@link #requiredEvidence()} and not among it, because an obligation's standing is a
     * fold of the readings and not a measurement ({@link ObligationCoverage}) — it has what it went
     * without and no status, so what "made in full" means of it is its own answer.
     *
     * <p>All four roles of a border, because all four are obligations. Which of them a verdict is
     * about used to be a bar's to select, and a verdict about part of the account is a verdict
     * about a question the account does not ask. What the model owes is settled where a border
     * decides whether to owe a point at all, which is where it stays.
     *
     * <p>One entry per thing a row is owed for, since each of them is an obligation: a place two of
     * a body's rules drew a line at leaves a run owed to each, and a verdict counting the role once
     * would be short by the rest. A behavior's own and its module's declarations' alike — the debts
     * and not their findings, since a line a row already stands at has no finding and a denominator
     * made of the findings is a denominator made of the gaps.
     */
    private List<Owned<ObligationAssessment>> requiredObligations() {
        List<Owned<ObligationAssessment>> owed = new ArrayList<>();
        for (ModuleReport module : modules) {
            for (BehaviorReport behavior : module.behaviors()) {
                if (behavior.partition() == null) {
                    continue;
                }
                behavior.account().forEach(point -> owed.add(new Owned<>(
                        new Subject.AtAPoint(point.point()), point.owed())));
            }
            for (Adequacy.DeclaredDebt debt : module.debts()) {
                owed.add(new Owned<>(new Subject.AtAPoint(debt.debt().point()),
                        debt.debt().owed()));
            }
        }
        return owed;
    }

    /** Whether {@code kind} is about something the model owes a row at, which is what puts the
     *  measure that finds one among the answers a verdict needs. */
    private boolean owesARowAt(Adequacy.Kind kind) {
        return kind.isAboutAnObligation();
    }

    /**
     * One measure's answer, where it is one the verdict is about.
     *
     * <p>{@link MeasurementStatus#NOT_APPLICABLE} is not. Nothing here was ever going to be measured
     * and no row anybody writes would change it, so counting it would leave every model undetermined
     * for having a behavior that answers a plain number. {@link MeasurementStatus#NOT_MEASURED} is
     * counted and is exactly what stops a verdict of satisfied: it is the case where a gap could have
     * been found and nobody looked.
     */
    private void add(List<Owned<Measure<?>>> measures, Subject subject, Measure<?> measure) {
        if (measure != null && !(measure instanceof Measure.NotApplicable<?>)) {
            measures.add(new Owned<>(subject, measure));
        }
    }

    /**
     * One thing the verdict rests on, together with what it is a thing about.
     *
     * <p>Handed over as a pair because the walk is where the second half is. A measurement carries
     * what it was waiting for and an obligation's disposition what is undecided about it; which
     * measure and which point they are of is known here, at the loop that reached them, and
     * nowhere afterwards. Passed on alone, they arrive at the reader as many facts a document
     * calls one thing and spells the same way (issue #1437).
     */
    private record Owned<T>(Subject subject, T value) {

        private Owned {
            if (subject == null || value == null) {
                throw new IllegalArgumentException("a thing the verdict rests on is about something");
            }
        }
    }

    // --- rendering --------------------------------------------------------------------------------

    /**
     * The report as a person reads it, with the sources under the names {@code rendering} gives them.
     *
     * <p>The names are asked for rather than held. What a report is about is identified by whatever
     * the caller handed its sources over as — an index under a build, a document URI under an editor
     * — and what to call one of them is neither of those: it is the shortest thing that tells this
     * reader's files apart, so it is a fact about the set in front of them. A caller with no names to
     * give says so with {@link SourceNameResolver#identity}, and the ids stand for themselves.
     */
    public String human(SourceRendering rendering) {
        StringBuilder out = new StringBuilder();
        Map<BehaviorImplementation, Integer> counted = new EnumMap<>(BehaviorImplementation.class);
        for (BehaviorImplementation each : BehaviorImplementation.values()) {
            counted.put(each, 0);
        }
        for (ModuleReport module : modules) {
            out.append(String.format("%s measurement: %s%n",
                    DisplayColumns.padRight(module.module(), 56),
                    wire(ReportMeasurement.statusOf(module.weakenedBy()))));
            for (BehaviorReport behavior : module.behaviors()) {
                counted.merge(behavior.implementation(), 1, Integer::sum);
                // A number where the rows were read, and what stopped them being read where they
                // were not. Written as `0` for both, this line said an author had written no row
                // for a behavior whose rows nobody had looked at.
                out.append(String.format("  %s %s %s%n",
                        DisplayColumns.padRight(behavior.name(), 24),
                        DisplayColumns.padRight(behavior.implementation().written(), 13),
                        behavior.rowCount().isPresent()
                                ? String.format("rows %-4d pending %d",
                                        behavior.rowCount().getAsInt(),
                                        behavior.pending().getAsInt())
                                : "rows not read"));
                signature(out, behavior);
                partition(out, behavior, module.declaredIn(), rendering, behavior.rulePlace());
                branch(out, behavior, module.declaredIn(), rendering);
                decision(out, behavior, module.declaredIn(), rendering);
                // Under the behavior it names, because a reason printed at the module's foot is
                // read as belonging to whichever behavior came last. That was survivable while the
                // only reasons naming one were rare; a position that could not be read is not.
                said(out, module.incompleteness().written().stream()
                        .filter(gap -> gap.fact().behavior()
                                .map(behavior.name()::equals).orElse(false))
                        .toList(), rendering);
            }
            declared(out, module, rendering, module.owedByDeclarations().rulePlace());
            said(out, module.incompleteness().written().stream()
                    .filter(gap -> gap.fact().behavior().isEmpty()).toList(), rendering);
        }
        int total = counted.values().stream().mapToInt(Integer::intValue).sum();
        out.append(String.format("%n%d %s: %d implemented, %d unimplemented, %d injected;"
                        + " %d %s waiting for a `let`.%n",
                total, total == 1 ? "behavior" : "behaviors",
                counted.get(BehaviorImplementation.IMPLEMENTED),
                counted.get(BehaviorImplementation.UNIMPLEMENTED),
                counted.get(BehaviorImplementation.INJECTION_TARGET),
                pendingRows(), pendingRows() == 1 ? "row" : "rows"));
        // Last, and its own line. What the measurement managed is said above, per module; this is the
        // other question, and the two were one word until they disagreed in front of a reader.
        out.append(String.format("adequacy: %s%n",
                adequacy().name().toLowerCase(Locale.ROOT).replace('_', ' ')));
        // And what it is open on, split by the one thing a reader of `undetermined` wants to know:
        // whether measuring again allowing more would answer any of it. The word covers a space too
        // large to walk and a rule this compiler has no reading for alike, and the first is a number
        // away while nothing anybody writes reaches the second.
        //
        // Under the verdict and not under the measurement line above, because it is read from what
        // that verdict rests on. What left the whole report weaker than it looks is the other
        // question and is said per module, where the measures are.
        UnderAWiderRun open = underAWiderRun();
        if (!open.isEmpty()) {
            // Read once for the lines below rather than per line: the places are a fold over every
            // page this report holds, and folding it again per opening is that walk once per thing
            // said about it.
            PublishedRuleHandle.WhereARuleIs places = rulePlaces();
            out.append("  what keeps it open\n");
            out.append(String.format("    may change in a wider run   %3d%n", open.mayChange()));
            out.append(String.format("    unaffected by a wider run   %3d%n", open.unaffected()));
            // And which they are. The counts alone told a reader how many things held the verdict
            // open and nothing about what they were, with no mark in the body to find them by; the
            // lines below name each one and say where it leaves them, both read from what the
            // measurement established rather than worked out again here.
            for (AdequacyOpening each : whatKeepsTheVerdictOpen()) {
                // What it is about and what to do with it, and not the word the document writes
                // for the kind: those are for a consumer keyed on this report, and a person reading
                // a line is owed a sentence. What kind of thing it is comes out in what is said to
                // do about it, which is read from the same fact the word is.
                out.append(String.format("      %s — %s%n",
                        said(each.subject(), rendering, places),
                        next(ReaderDisposition.of(each))));
            }
        }
        // What the mark above means, said by the report that wrote it. The count was said only by
        // `--strict`, on standard error, in a run a reader had to ask for — so a reader of the report
        // alone had a mark with nothing to read it by, and one who did ask got a number pointing at a
        // list with more entries in it than the number.
        //
        // What the mark means and not what a refusal decided. The two are printed one under the other
        // where a build asked to be strict and got a human report, and a legend repeating the verdict
        // would be the same sentence twice with nothing to tell a reader which surface said it.
        List<Adequacy.Finding> refused = adequacyGaps();
        if (!refused.isEmpty()) {
            out.append(String.format("%d %s marked `!`: what a strict build refuses over.%n",
                    refused.size(), refused.size() == 1 ? "gap" : "gaps"));
        }
        return out.toString();
    }

    /** The reasons, in the one shape a reason is printed in wherever it sits. */
    private void said(StringBuilder out, List<PublishedIncompleteness> gaps,
                             SourceRendering rendering) {
        for (PublishedIncompleteness gap : gaps) {
            out.append(String.format("    · %s%n", Reasons.said(gap.fact(), rendering)));
        }
    }

    /**
     * What the rows established about one behavior's signature, and what they left.
     *
     * <p>An unspecified case and an unverified one are printed apart because they ask different things
     * of the author. The first says nobody has written down that the model owes this answer; the
     * second says somebody has, and nothing has confirmed the model gives it. For a behavior with no
     * body only the first can be answered at all, so the second is not printed against one.
     */
    private void signature(StringBuilder out, BehaviorReport behavior) {
        Adequacy.SignatureEvidence signature = behavior.signature();
        if (signature == null) {
            return;
        }
        ReportMeasurement<Adequacy.SignatureEvidence.Counted> counted =
                ReportMeasurement.of(signature.counted());
        if (!counted.counted()) {
            // A measure with no number says why, the way the arms do. Leaving the line out instead
            // put two behaviors side by side in one report, one measured on four lines and one on
            // three, with nothing saying the fourth did not apply — and hid the fact worth reading,
            // which is that a behavior answering a bare primitive gets less scrutiny than one
            // answering a sum.
            sayWhy(out, "signature", counted.reason());
            return;
        }
        boolean decided = counted.inFull();
        OutputCaseEvidence output = signature.output();
        if (!output.declared().isEmpty() && output.cases().made().isPresent()) {
            OutputCaseEvidence.Cases cases = output.cases().made().orElseThrow();
            out.append(String.format("    signature   out specified %d/%d  observed %d/%d "
                            + " verified %d/%d%s%n",
                    cases.specified().size(), output.declared().size(),
                    cases.observed().size(), output.declared().size(),
                    cases.verified().size(), output.declared().size(),
                    decided ? "" : "   (partial)"));
            for (Adequacy.Finding f : behavior.findings()) {
                if (f.about() instanceof About.ACaseNoRowExpects(var missing)) {
                    out.append(String.format("      %s %sexpects `%s`%n",
                            mark(f), noRow(f), missing.name()));
                }
            }
            for (Adequacy.Finding f : behavior.findings()) {
                if (f.about() instanceof About.ACaseNothingWasSeenToProduce(var missing)) {
                    out.append(String.format("      %s %sconfirms `%s`%n",
                            mark(f), noRow(f), missing.name()));
                }
            }
        }
        // The positions where the measure counted them. This section is reached only where the
        // signature measure has a number, and a measure with one has read the boundary its
        // positions come off — so there is nothing here that a measure short of one would print,
        // and nothing to stand in for it either.
        for (InputCaseEvidence input : signature.positions()) {
            // Under the same condition as the output line above it, which is the whole of why it is
            // spelled here. It asked its measurement for a number and took a nought where there was
            // none, so an input nobody measured printed `specified 0/3` — a measurement, made by
            // this line rather than by anything that read a row (issue #997). Nothing in this suite
            // reaches it: a behavior no row names and a level that asks for nothing leave the whole
            // signature measure without a number, and this section returns above. Which is a reason
            // to write the condition and not a reason to leave it out — the one thing the two lines
            // must not do is differ.
            if (input.declared().isEmpty() || input.cases().made().isEmpty()) {
                continue;
            }
            // Counted against the cases a row can be written at. A case the body answers `unreachable`
            // for is one the compiler refuses a row for, so leaving it in the denominator would ask
            // for work that cannot be done and hold the model one case short for ever.
            out.append(String.format("                in #%d specified %d/%d%s%n", input.at() + 1,
                    input.cases().made().orElseThrow().specified().size(),
                    input.coverable().size(),
                    input.excluded().isEmpty() ? ""
                            : "   excluded " + input.excluded().size()));
            for (Adequacy.Finding f : behavior.findings()) {
                // Told apart by the input the finding is about rather than by a number written
                // beside it: which one it is, is the evidence's own answer on both sides.
                if (f.about() instanceof About.ACaseNoRowAppliesItTo(var at, var missing, var _)
                        && at.at() == input.at()) {
                    out.append(String.format("      %s %suses `%s`%n",
                            mark(f), noRow(f), missing.name()));
                }
            }
            for (TypeSymbol ruled : input.excluded()) {
                out.append(String.format("      · `%s` is declared unreachable%n", ruled.name()));
            }
        }
    }

    /**
     * The mark a finding is printed under, which says what a build does about it.
     *
     * <p>The one thing separating the two kinds of bullet a report prints. Without it four findings
     * of one shape were printed and three of them failed a build, and a reader deciding what to write
     * next either wrote rows for all four — more than the build asks, on a measure the language
     * deliberately chose not to gate — or wrote one and ran again to find out which.
     *
     * <p>Read off the finding's own answer. Reading the kinds again here would be a second
     * classification to keep in step with the one a build refuses on, and the two would agree until
     * a kind changed sides.
     */
    private String mark(Adequacy.Finding finding) {
        return finding.isAdequacyGap() ? "!" : "·";
    }

    /**
     * How a finding names what nothing did, given how far its measure got.
     *
     * <p>Where some rows could not be read, a case nothing here claims is a case nothing *seen*
     * claims. The summary already says partial; each line has to say it too, or the lines read as the
     * finding and the word in the margin as a footnote.
     */
    private String noRow(Adequacy.Finding finding) {
        return finding.weakenedBy().isEmpty()
                ? "no row " : "undecided whether a row ";
    }

    /** The positions this report has an axis for, which is what tells a claim it can print beside
     *  one from a claim it has to name a position for. */
    private List<String> measuredPaths(PartitionEvidence partition) {
        return partition.axes().stream().map(PartitionEvidence.AxisCoverage::path).toList();
    }

    /** The model's own words for a claim, where there is one to print. */
    private String because(List<String> reasons) {
        return reasons.size() == 1 ? ": " + reasons.get(0)
                : reasons.isEmpty() ? "" : " on every path";
    }

    /** What a reader is told about a claim nothing settled, in this report's own words. */
    private String unproven(ClaimAnnotations.Why why) {
        return switch (why) {
            case A_RULE_WENT_UNREAD -> "a rule about this position went unread";
            case THE_RULES_LEAVE_THE_POSITION_NOTHING ->
                    "the rules leave this position no value at all";
            case NOTHING_WAS_READ_ABOUT_THE_CASE -> "nothing was read about this case";
            case THE_FORK_IS_NOT_KNOWN_TO_BE_REACHED ->
                    "this arm is inside another, and what reaches it is not read here";
            case THE_ALTERNATIVES_WERE_NOT_KEPT_APART ->
                    "every rule here was read, and what they leave this position together is not"
                            + " what is held of it";
        };
    }

    /**
     * How much of what the model distinguishes the rows reach.
     *
     * <p>A boundary a guard drew is printed as not measured rather than as missed. Meeting it takes
     * more than writing the value — the comparison has to have run — and nothing counts that yet.
     */
    private void partition(StringBuilder out, BehaviorReport behavior,
                                  SourceId declaredIn, SourceRendering rendering,
                                  PublishedRuleHandle.WhereARuleIs places) {
        // Whether there is a section at all is settled once, for every surface, and asked of the
        // measurement rather than of the entries beside it. Asked of the entries, a behavior whose
        // measures both had something to say and whose lists happened to be empty was left out of
        // the page while the document wrote what they said (issue #1079).
        if (!(PartitionSection.of(behavior.partition())
                instanceof PartitionSection.Present(PartitionEvidence partition))) {
            return;
        }
        ReportMeasurement<List<PartitionEvidence.AxisCoverage>> partitioned =
                ReportMeasurement.of(partition.partitioned());
        if (!partitioned.counted()) {
            // A measure with no number says why, rather than showing a nought that reads as a
            // measurement. `axes 0   single-axis 0/0` was the same three characters a behavior gets
            // when every position it has was measured and every class covered.
            sayWhy(out, "partition", partitioned.reason());
        } else {
            // Counted over the positions that were measured. A position nothing was measured at
            // contributes no classes to the denominator: nought out of two reads as two gaps, and a
            // measure that was never made found none.
            List<PartitionEvidence.AxisCoverage> measuredAxes = partition.axes().stream()
                    .filter(a -> a.reached().made().isPresent()).toList();
            int classes = measuredAxes.stream().mapToInt(a -> a.classes().size()).sum();
            int covered = measuredAxes.stream()
                    .mapToInt(a -> a.reached().made().orElseThrow().covered().size()).sum();
            // Over the positions this line counts and no others. A claim about a position no axis
            // was derived at is said further down, under its own name — counted here it would be a
            // number taken out of a denominator that never held it.
            int excluded = (int) measuredAxes.stream()
                    .flatMap(each -> behavior.claimed().at(each.path()).stream())
                    .filter(ClaimAnnotations.Said::settled).count();
            out.append(String.format("    partition   axes %d   equivalence partitions %d/%d%s%s%s%n",
                    partition.axes().size(), covered, classes,
                    excluded == 0 ? "" : "   excluded " + excluded,
                    notes(partition.axes().stream()
                                    .filter(a -> a.reached().made().isEmpty()).toList(),
                            a -> ReasonProse.of(ReportMeasurement.of(a.reached()).reason())
                                    .clause()),
                    inFull(partitioned.status())));
            // The position as well as the class. A class name alone is the same words about two
            // positions of one behavior whose types divide into classes named after the same cases,
            // and a reader told one of them cannot say which position to write the row at. Which
            // name a position goes by is settled here and not by the class: the two the axis holds
            // are for different readers, and this one writes the term a row is written against.
            for (Adequacy.Finding f : behavior.findings()) {
                if (f.about() instanceof About.AClassNoRowIsIn(var missing)) {
                    out.append(String.format("      %s %s `%s` at %s%n", mark(f),
                            f.weakenedBy().isEmpty()
                                    ? "no row is in" : "undecided whether a row is in",
                            missing.name(), missing.axis().name()));
                }
            }
            // Not a finding: nothing is owed here, and what the line says is what the model already
            // decided rather than something the rows left undone.
            for (PartitionEvidence.AxisCoverage axis : partition.axes()) {
                // A position divided into more classes than this behavior's rules composed, whose
                // classes take part in a relation no row reaches. Asked of the one thing that
                // answers it, which is what the decision this leaves a reader is read from: worked
                // out again here, the line and the decision would be two answers to one question.
                if (ReaderDisposition.widerThanTheyAreSeparated(partition.pairs(), partition.axes())
                        .contains(axis)) {
                    out.append(String.format("      · %s holds %d classes and this behavior's rules"
                                    + " compose %d of them%n",
                            axis.name(), axis.classes().size(), axis.divides().size()));
                }
                // Which rule composed the classes, for a reader told that no row is in one of them.
                // The lines above name the class; this names what made it, so that a reader sent
                // after a row has somewhere to open rather than a name of the model's.
                //
                // One line per rule and not per reading. A helper is expanded at each call, so one
                // rule the author wrote is read at several places; what those offer is several
                // handles onto one rule, and which of them a document writes is settled where that
                // choice is made.
                for (Set<RuleCitation> rule : dividedBy(axis)) {
                    out.append(String.format("      · %s is divided by %s%n",
                            axis.name(), cited(rule, rendering, declaredIn, places)));
                }
                for (ClaimAnnotations.Said said : behavior.claimed().at(axis.path())) {
                    // A case out of the denominator says what the author wrote about it; one still
                    // counted says that too, and that nothing settled it — a reader is told both
                    // rather than left to find out by writing the row.
                    out.append(said.settled()
                            ? String.format("      · `%s` is declared unreachable%s%n",
                                    said.classId(), because(said.reasons()))
                            : String.format("      · `%s` is declared unreachable%s, and nothing"
                                            + " here proves it: %s%n",
                                    said.classId(), because(said.reasons()), unproven(said.why())));
                }
            }
        }
        // And the claims about positions this report has no axis for, named by their position since
        // there is no axis above them to have said which one it is. Outside the arm above, because
        // a claim is not a number: a behavior whose positions were all dropped or never read has
        // nothing to count and the same claims to answer for, and printing them only beside a count
        // is how a verdict came to be reached and then not said.
        for (ClaimAnnotations.Said said : behavior.claimed().notAt(measuredPaths(partition))) {
            out.append(said.settled()
                    ? String.format("      · `%s` at `%s` is declared unreachable%s%n",
                            said.classId(), said.at(), because(said.reasons()))
                    : String.format("      · `%s` at `%s` is declared unreachable%s, and nothing"
                                    + " here proves it: %s%n",
                            said.classId(), said.at(), because(said.reasons()),
                            unproven(said.why())));
        }
        undivided(out, behavior, rendering, declaredIn, places, behavior.partPlace());
        // On a line of its own, and this is the whole of why it has one. Counting combinations
        // across two positions is the neighbouring technique rather than this one, and printed at
        // the end of the partition line it sat beside the border counts where a reader could add
        // them up into a total neither of them is part of.
        //
        // Under the same condition as before and not a new one: these counts used to be the tail of
        // the partition line, which is written in the arm this tests for. Moving them out of that
        // arm is what makes the condition something to spell rather than something to inherit.
        if (partitioned.counted()) {
            String combinations = combinations(partition.pairs());
            List<String> relations = new ArrayList<>();
            if (partition.pairs().counted().made().isPresent()) {
                for (PartitionEvidence.PairSpace.AxisPair pair : partition.pairs().space()) {
                    if (partition.pairs().unknown(pair) > 0) {
                        relations.add(String.format("      · %s × %s: %d covered, %d unknown%n",
                                pair.between().one().term(), pair.between().other().term(),
                                partition.pairs().counts().covered(pair.between()),
                                partition.pairs().unknown(pair)));
                    }
                }
            }
            if (!combinations.isEmpty()) {
                out.append(String.format("    combination %s%n", combinations));
                // And which relations hold what no row reaches. Summed, the count says how much of
                // the product the rows cover and nothing about where it is: most of it sitting in
                // the two relations one position takes part in is the whole of what a reader acts
                // on, and it is invisible in one number. Only the relations with something unknown,
                // since a relation the rows cover has nothing here to say.
                relations.forEach(out::append);
            }
        }
        // Counted over the obligations and named as such. A border owes a row at up to four points,
        // so a count of borders would say a border with one point met and three missed was as
        // covered as one with nothing to owe but that point; and a line is owed once however many
        // positions read it, so a count of readings would say a guard on a name every case of a sum
        // spreads was as many rows as the sum has cases. `borders` stays the lines at coordinates,
        // which is what the block under it shows, and the two numbers are not one multiplied.
        //
        // What this behavior is owed is its account, which is a projection of the module's one
        // relation, and where each obligation of it stands is one fold shared with the block that
        // says what the declarations owe.
        ReportMeasurement<List<BorderAssessment>> bounded =
                ReportMeasurement.of(behavior.boundaryReadings());
        List<BorderAssessment> lines = behavior.lines();
        ObligationSummary<BorderObligationPointAssessment> owed = ObligationSummary.of(
                behavior.account(), BorderObligationPointAssessment::owed);
        if (!bounded.counted()) {
            // `0/0` said the rows were at every line there was. What it meant was that nobody found
            // a line to be at, which a model whose bounds sit one type away from the position the
            // behavior takes has, and which is the shape of every behavior that validates raw input.
            sayWhy(out, "border", bounded.reason());
        } else {
            // The points the model's own rules discharged are not on this line. They are not
            // obligations, so a count of them beside the obligations would be two units in one
            // sentence; each is said under the block, by the reading it is a point of.
            out.append(String.format("    border      borders %d   obligations %d/%d%s%n",
                    lines.size(), owed.met().size(), owed.counted(),
                    inFull(bounded.status())));
        }
        // Every obligation the count holds and no row is at, said here or under the findings below:
        // a point nobody can say is missed is not a gap and is no finding, and left to the number
        // alone a reader is told a difference with nothing under it to act on.
        for (BorderObligationPointAssessment point : owed.undecided()) {
            for (String said : undecidedBecause(point.owed().disposition(),
                    point.role() + " point ("
                            + RuleHandleProse.said(point.describe(places), rendering, declaredIn) + ")")) {
                out.append(String.format("      ? %s%n", said));
            }
            readings(out, point, _ -> true, at -> whatWasTried(
                    at.owedAt(point.at()).searches(), behavior.conditionPlaces(), rendering,
                    declaredIn));
        }
        // A border the model drew that nothing here answered for, said whether or not one came of
        // it. It is exactly where none did that the question stands, so this cannot be written by
        // walking the borders.
        unaccounted(out, behavior, rendering, declaredIn,
                asked -> asked.holdsOpen(CoverageObligation.Measure.BOUNDARY), places,
                behavior.partPlace());
        // The rule as this report writes it. The finding carries the rule and not words about it,
        // because what to say differs between here — where a file has a name — and the warning built
        // from the same finding, where nothing knows what to call one.
        //
        // The two kinds are printed alike and refuse under different criteria. A gap against the
        // line is refused by a build held to either of them and a gap away from it only by one held
        // to reliable domain coverage, which is a decision about what a build is held to and not
        // about what a reader is shown — printed apart, the second would read as a lesser finding
        // rather than as the second half of one technique.
        // The points against the line first and the ones away from it after, which is why this is
        // two passes over one list rather than one: the measure finds a border's four items
        // together, and printed in that order the two halves would be interleaved.
        for (boolean againstTheLine : List.of(true, false)) {
            for (Adequacy.Finding f : behavior.findings()) {
                if (f.about() instanceof About.APointOfABorder(var point)
                        && point.role().againstTheLine() == againstTheLine) {
                    // The point and the rule, and no word with a quantity in it. Writing where the
                    // point is takes a quantity, and a quantity is a reading's — so the mark says
                    // which of the four and which rule drew the line, and every word an author
                    // acts on is said under it by the reading whose word it is.
                    //
                    // `the` for a point and `an` for a run. Two of the four are one value and the
                    // other two are met anywhere in a run of them, so a reader told there is no row
                    // at `the IN point` is being sent after a value that does not exist.
                    //
                    // And which side of the line, where this line has two points in that role: a
                    // rule that names a value is owed a row outside it on each side, and the two
                    // are different work. Said with the role alone, the same sentence printed twice
                    // and neither of them said which value was being asked for.
                    out.append(String.format("      %s no row is at %s %s point%s (%s)%n",
                            mark(f), againstTheLine ? "the" : "an", point.role(),
                            point.whichSide(),
                            RuleHandleProse.said(point.describe(places), rendering, declaredIn)));
                    readings(out, point, _ -> true, _ -> "");
                }
            }
        }
        // A point some row answers, at a reading nothing stands at. Not a gap — the line is owed
        // once and a row stands at it — and still a fact about that position: a reader who wants
        // every case of the sum exercised is told which are not, and told it as diagnosis rather
        // than as work a strict build refuses over.
        for (BorderObligationPointAssessment point : owed.met()) {
            if (point.readings().stream().anyMatch(
                    at -> !at.owedAt(point.at()).hasRowWitness())) {
                out.append(String.format("      · the %s point (%s) is answered, and not at every"
                                + " reading of the line%n",
                        point.role(),
                        RuleHandleProse.said(point.describe(places), rendering, declaredIn)));
                readings(out, point, at -> !at.owedAt(point.at()).hasRowWitness(), _ -> "");
            }
        }
        // What the model itself answered, which is not a row anybody is behind on. Named by the
        // reason rather than left blank: a point the rules refuse and a point this language cannot
        // write down are counted out for opposite reasons, and a reader acts on them differently.
        for (BorderAssessment.Point p : BorderAssessment.pointsOf(lines)) {
            if (p.item() instanceof ItemAssessment.NotOwed not) {
                out.append(String.format("      · no %s point%s is owed at %s (%s): %s%n",
                        p.role(), p.border().border().whichSide(p.at()), p.border().label(),
                        RuleHandleProse.said(p.border().describe(places), rendering, declaredIn),
                        whyNotOwed(not.reason())));
            }
        }
        // And a word of the technique this line has no point in at all, which is not the same news.
        // A point the rules refuse is an item the model settled; a role with no point is a word for
        // something this line does not have, and a reader told nothing about it cannot tell the two
        // apart from the four words being four.
        for (BorderAssessment line : lines) {
            line.border().inEachRole().forEach((role, answer) -> {
                if (answer instanceof RoleAnswer.NoPoint none) {
                    out.append(String.format("      · no %s point exists at %s (%s): %s%n",
                            role, line.label(),
                            RuleHandleProse.said(line.describe(places), rendering, declaredIn),
                            whyNoPoint(none.why())));
                }
            });
        }
    }

    /** Why a line has no point in one of the four roles, in the report's own words. */
    private static String whyNoPoint(RoleAnswer.Reason why) {
        return switch (why) {
            case THE_CLASS_AT_THE_LINE_HOLDS_ONE_VALUE ->
                    "the class at the line holds the value the rule names and nothing else, so"
                            + " there is no row in it away from the line";
        };
    }

    /**
     * The readings of one point, one line each, under the line that names the point.
     *
     * <p>Where the quantity's name lives. The point's own line names none — a body's line is owed
     * once wherever it is read — so what a row has to be at each position that read it is said
     * here, in that position's terms. In the order the sentences sort and never the order the walk
     * took, and at most {@link BorderObligationPointAssessment#READINGS_SAID} of them: over
     * {@code crm} one clause is read at 133 positions, and what is left out is said as a count so
     * the readings shown do not read as all there are.
     *
     * @param shown  which readings to say — every one under a gap, and only the unwitnessed ones
     *               under a point some row answers
     * @param beside what to say after each, which is what that reading's search came to where one
     *               was made
     */
    private static void readings(StringBuilder out, BorderObligationPointAssessment point,
                                 Predicate<BorderAssessment> shown,
                                 Function<BorderAssessment, String> beside) {
        List<BorderObligationPointAssessment.ReadingSaid> said = point.readingsSaid().stream()
                .filter(each -> shown.test(point.met().get(each.where()))).toList();
        int say = Math.min(said.size(), BorderObligationPointAssessment.READINGS_SAID);
        for (BorderObligationPointAssessment.ReadingSaid each : said.subList(0, say)) {
            out.append(String.format("          · read as %s: %s%s%n", each.at(), each.asks(),
                    beside.apply(point.met().get(each.where()))));
        }
        if (said.size() > say) {
            out.append(String.format("          · %d more readings%n", said.size() - say));
        }
    }

    /** What settled a point nobody is owed a row at, in the words the report promises its reader. */
    private String whyNotOwed(NotOwedReason reason) {
        return switch (reason) {
            case THE_RULES_REFUSE_IT -> "excluded — the rules leave no value there";
            case THE_CARRIER_NAMES_NO_NEIGHBOUR ->
                    "this order names no value there, so the point cannot be written";
        };
    }

    /**
     * What the classes measure could not say, said under the classes measure.
     *
     * <p>Under the line these are about, which is the partition and not the boundary. A comparison
     * between two positions divides neither of them and draws a line all the same, so the note saying
     * the position went undivided sat two rows under a boundary count that was counting the line that
     * very comparison drew — one measure's silence printed as though it were the other's.
     */
    private void undivided(StringBuilder out, BehaviorReport behavior,
                                  SourceRendering rendering, SourceId declaredIn,
                                  PublishedRuleHandle.WhereARuleIs places, WhereAPartIs parts) {
        for (Adequacy.Finding f : behavior.findings()) {
            if (f.about() instanceof About.APositionNoLineDivides(var position)) {
                // What was found and not what was missed. This line is written from
                // {@code Why.Absent}, which is every reading having run to the end and none of them
                // dividing the position — a conclusion about the model. It said `not derivable`,
                // which is the word for a derivation this compiler could not make, so the sentence
                // said the opposite of the value it was written from (issue #1249).
                out.append(String.format("      %s divided no way: %s%n", mark(f), position.at()));
            }
        }
        // Said apart from the line above it, which is the whole of what this pair is for: one names
        // a position the model divides no way, and this one a rule nobody could turn into a line.
        //
        // Named by the rule, as the accounting's line is. A position was all a reader used to be
        // given, which sent them looking for a rule the sentence never named — and two rules
        // stopped by one limit at one position came out as one line.
        for (Adequacy.Finding f : behavior.findings()) {
            // Two sentences, because two opposite things are being said. A form no reader takes
            // apart is a limit of this compiler; a rule whose quantity is empty was read from end
            // to end and says what it says. Written under one word, a line read "not read: it was
            // read to the end and cuts nothing" — which is what the reader is left to make sense
            // of.
            if (f.about() instanceof About.ARuleWithoutALine(var it)) {
                // And where in the rule, for a reason about a part of it. Two choices of one
                // clause leave one rule, one position and one reason between them, so a line
                // without this is the same sentence twice and a reader lifting one of them cannot
                // tell which.
                out.append(String.format("      %s %s: %s — %s, about `%s`%s%n",
                        mark(f), it.readingStopped() ? "not read" : "no line",
                        cited(it.cited(), rendering, declaredIn, places),
                        whyUnread(it.reason()), it.at(),
                        sentTo(it.finding().sentTo(), parts, rendering, declaredIn)));
            }
            if (f.about() instanceof About.ARuleNothingClassified(var it)) {
                out.append(String.format("      %s not read: %s — %s, about `%s`%n",
                        mark(f), cited(it.cited(), rendering, declaredIn, places),
                        whyUnread(it.reason()), it.at()));
            }
        }
        // And a position whose rules this reading never arrived at, which names no rule because
        // nothing observed one. Its own line, so that a reader is not left reading an absent rule
        // to work out which of the two they are being told.
        for (Adequacy.Finding f : behavior.findings()) {
            if (f.about() instanceof About.APositionThisCouldNotRead(var it)) {
                out.append(String.format("      %s not read: %s (%s)%n",
                        mark(f), it.at(), whyUnread(it.reason())));
            }
        }
        // And a third thing, said apart from both: a rule written about a position the axes did
        // measure that nothing took in. The classes beside it are what the model was read to say,
        // and this rule may yet refuse one of them — which is a different thing to act on from a
        // position nothing established anything about. Named by the rule, since a position is not
        // what an author edits.
        for (Adequacy.Finding f : behavior.findings()) {
            if (f.about() instanceof About.APositionWhoseRulesWereNotReached(var gap)) {
                out.append(String.format("      %s rules not reached: %s%n",
                        mark(f), gap.at()));
            }
        }
        // The questions this measure answers: which values may stand where, which classes hold
        // them, and which value a rule tells from every other. A border is the section below's.
        unaccounted(out, behavior, rendering, declaredIn,
                asked -> asked.holdsOpen(CoverageObligation.Measure.PARTITION), places,
                behavior.partPlace());
    }

    /**
     * What stopped a derivation, in the words this document promises its reader.
     *
     * <p>Here and not where the position was found. The measure carries which kind of thing stopped
     * it, and the sentence for one is a report's — written where the finding was made, an English
     * phrase travelled inside the finding, which is how a value that was never words came to be
     * printed by whoever called {@code String.valueOf} on it.
     */
    private static String whyUnread(UndividedPosition.Reason reason) {
        return switch (reason) {
            // The three a rule reaches, written about the rule: the line these appear on names it,
            // so a sentence saying "a rule about it" would name the rule and then not say so.
            case UNSUPPORTED_SYNTAX -> "written in a form this compiler does not read";
            // Written about the choice and not about the rule at this position: that one was read,
            // and an author sent after its form would rewrite what is not the difficulty.
            case UNREAD_ALTERNATIVE_OF_A_CHOICE -> "left open by a choice in it whose other"
                    + " alternative this compiler does not read";
            // Written about the rules together and not about this one. What ran out is the
            // allowance for the values they leave between them, and two rules cheap on their own
            // can have an answer that is not — so a sentence naming this rule would be telling a
            // reader to change something that may not be why.
            case EXACT_VALUES_TOO_COSTLY -> "read to the end, and the values the rules about this"
                    + " position leave between them are more than this compiler will work out";
            case BEHAVIOR_DISTINCTIONS_TOO_COSTLY -> "read to the end, and what the behavior's rules"
                    + " about this position tell apart is more than this compiler will work out";
            case PATTERN_TOO_DEEPLY_NESTED ->
                    "written more deeply nested than this compiler reads";
            case UNSUPPORTED_DOMAIN -> "compared against values no line can be drawn on here";
            case UNRESOLVED_CASE_PAIRING -> "it reaches case-specific positions on both sides, and "
                    + "how those positions pair up is not worked out";
            case UNSUPPORTED_PARTITION_SHAPE ->
                    "it relates two positions rather than dividing one";
            case RULE_ABOUT_A_RUN ->
                    "it is about what the values here come to rather than about any one of them,"
                            + " so it draws its line and divides none of them";
            // What the rule does to the position, and not what this measure could not hold. An
            // author reading "this compiler does not read it" would go and rewrite a rule that was
            // read from end to end, and one told the position is divided would go looking for the
            // class on the other side of a rule that refuses everything there.
            case POSITION_RESTRICTED_TO_WHAT_A_RULE_ADMITS ->
                    "it restricts this position to the values it admits, and no other value can be"
                            + " built here";
            case RULE_ABOUT_A_DERIVED_VALUE ->
                    "it is about a value made from this one, and what it says about the values here"
                            + " is not worked out";
            case RULE_ABOUT_AN_ELEMENT_OF_SEVERAL_SEQUENCES ->
                    "it is written inside a block handed to more than one walk, so it is about an"
                            + " element of this position or of another and nothing says which";
            case RULE_CUTS_NOTHING ->
                    "it was read to the end and cuts nothing this position appears in";
            case RULE_TELLS_NOTHING_APART ->
                    "it was read to the end and every value this position holds comes out one side"
                            + " of it";
            case CLASSES_NOT_COMPOSED ->
                    "it was read to the end, and what it says and what the rules beside it say are"
                            + " not one list of classes";
            case RULE_CUTS_OUTSIDE_WHAT_THE_QUANTITY_HOLDS ->
                    "it was read to the end and draws its line outside what the quantity it cuts"
                            + " ever holds";
            case NOTHING_ARRIVES_AT_THE_RULES_LINE ->
                    "it was read to the end, and no row that arrives at it holds a value at its"
                            + " line — the conditions on the way there rule those values out";
            // And the four a position reaches, written about the position, because that is all
            // there is: nothing observed a rule to name. Which reasons reach which of the two is
            // settled by the authority a reason belongs to, so no reason is written both ways.
            case RULES_NOT_READ_AT_ALL -> "the rules written about it were not reached at all";
            case RETURNS_TO_A_DECLARATION_ALREADY_READ ->
                    "the input returns here to a declaration already read above it, and what is"
                            + " under it is not read again";
            case TYPE_UNRESOLVED -> "its type could not be worked out here";
            case UNSUPPORTED_TRAVERSAL ->
                    "its values are held inside something this does not reach into";
        };
    }

    /** What a rule left open, in the words this document promises its reader. Here for the reason
     *  {@link #whyUnread} gives. */
    private static String asked(StandingQuestion question) {
        return switch (question) {
            case StandingQuestion.Exact it -> switch (it.obligation()) {
                case ADMITTED_VALUES -> "which values may stand at";
                case BOUNDARY -> "where the values stop on";
            };
            // Neither phrase names the place, which is why they do not read like the two above:
            // what such a rule asks is what nothing worked out, and the place beside this is where
            // a reader is sent to look at the rule.
            //
            // Two phrases, because two different things are undecided and a reader acts on them
            // differently. One says the rule restricts the values there and nothing worked out
            // whether it also puts an end; the other says nothing worked out what the rule does at
            // all.
            case StandingQuestion.BoundaryUndetermined _ ->
                    "whether the values stop on";
            case StandingQuestion.NothingClassifiesIt _ ->
                    "what this rule states about";
        };
    }

    /**
     * Which of the names a question carries a reader is shown.
     *
     * <p>The reader's choice and not the measure's. What a line falls on is shown where there is
     * one, since that is what tells two questions at one position apart; the position is what is
     * left.
     */
    private static String subjectOf(PartitionEvidence.Unanswered asked) {
        return asked.measure() != null ? asked.measure() : asked.at();
    }

    /**
     * Which arms of the body the rows go through.
     *
     * <p>Called the arms, and never the paths. Going through both arms of two nested conditions is four
     * arms and says nothing about their combinations, and a report that said "paths covered" would
     * invite an author to stop looking exactly where there is more to find.
     */
    void branch(StringBuilder out, BehaviorReport behavior,
                               SourceId declaredIn, SourceRendering rendering) {
        Adequacy.BranchEvidence branch = behavior.branch();
        if (branch == null) {
            return;
        }
        ReportMeasurement<ArmSummary> measured = ReportMeasurement.of(branch.measured());
        if (!measured.counted()) {
            // The measure's own answer, translated. Nothing here works out why from the row count or
            // the kind of behavior: those correlate with the reason and are not it, and the line an
            // author reads is the one place that difference shows.
            sayWhy(out, "branch", measured.reason());
            return;
        }
        // The two numbers, and nothing beside them qualifying every arm at once. What is uncertain
        // about the arms is uncertain of particular ones, and each of those is said under this line
        // by the arm it is about: the difference between the numbers is walked here rather than
        // summarised. A word for the whole measure said "a row was not read" over a behavior whose
        // rows all ran and a fork nobody could tell apart, and the arms it certainly does not reach
        // went unnamed under it.
        ArmSummary arms = measured.get();
        out.append(String.format("    branch      %d/%d%n", arms.covered(), arms.counted()));
        // What the numbers are counted out of, where something has shown that set to be short of an
        // arm. Said before the arms themselves, since it qualifies the denominator rather than
        // adding to what is missing from it, and it says what it is: the model is not wrong here,
        // an analysis this compiler made is.
        if (!arms.census().settled()) {
            out.append("      · a row went through an arm this compiler had proven nothing reaches,"
                    + " so what these arms are counted out of may be short of one\n");
        }
        // Arms this counts as one that it cannot show are one, said once per fork rather than once
        // per arm of it: a count holding two predicates where it says one is what would otherwise
        // report a behavior complete over something nothing ran.
        for (ArmExclusion left : arms.exclusions()) {
            out.append(String.format(
                    "      · a fork `%s` wrote decides by a rule its caller supplies, and which"
                            + " rule decides here could not be worked out: what its arms come to is"
                            + " read over however many rules that is%n",
                    left.fork().module()));
        }
        // Every arm the count holds and no row goes through, said here or under the findings below.
        // An arm a row that never finished might have gone through is a question and not a gap, so
        // it is named as one: told to write a row for it, an author may be told to write one they
        // have written, and left to the number alone a reader is shown a difference with nothing
        // under it to act on.
        for (ArmObligation.Counted open : arms.undecided()) {
            out.append(String.format("      ? undecided whether a row goes through `%s` (%s)%n",
                    ArmVocabulary.label(open.display()),
                    behavior.placeOf(open.display()).said(rendering, declaredIn)));
        }
        // Whatever findings there are, and no second opinion about whether there may be any. Which
        // arms may be named is settled where they are collected, so a condition repeated here would
        // be the same rule kept in two places, which is how the reading and the numbers came to
        // disagree before.
        //
        // The position alone where the arm is in the module's own source, which the section this is
        // under already names. It is not always: a body is spliced into whatever calls it, so an arm
        // written in a helper another module declares is in that module's file, and there the file
        // is named with it.
        for (ReportedFinding f : behavior.reported()) {
            if (f.about() instanceof About.AnArmNoRowGoesThrough(var arm)) {
                out.append(String.format("      %s no row goes through `%s` (%s)%n",
                        mark(f.finding()), ArmVocabulary.label(arm),
                        f.at().said(rendering, declaredIn)));
            }
        }
    }

    /**
     * Which rules of the decision the body states the rows take.
     *
     * <p>Beside the arms and never among them. An arm is one branch the author wrote and a rule is
     * one way through the body: two rules can go through one arm, and a body whose arms answer
     * alike states two rules that one row through each arm covers. Folded into the branch line,
     * the second of those would have been reported as covered by the first.
     *
     * <p>The rules the body states and the ones some row was seen taking, and no ratio between
     * them and what is owed. A rule no row takes may be one nothing can stand in, which is settled
     * against the model and is settled for some of them and not others — so a denominator here
     * would be counting rules as gaps that nothing has shown to be gaps. What is owed a row is
     * said under this, one entry apiece, by the findings that established it, and what nothing
     * settled either way is said after them in the words its own search came back with.
     */
    void decision(StringBuilder out, BehaviorReport behavior,
                  SourceId declaredIn, SourceRendering rendering) {
        DecisionEvidence decision = behavior.evidence().decision();
        if (decision == null) {
            return;
        }
        // A reading that stopped comes back with none of the body's rules rather than some of
        // them, so an empty list is two facts — a body that decides nothing, and a body whose ways
        // this compiler would not hold apart. Said before the count, because a reader shown
        // `rules 0` under a body of many ways has been told the opposite of what happened.
        if (!decision.derivation().isEmpty()) {
            out.append("    decision    not fully read (the ways through this body could not all"
                    + " be written down)\n");
            return;
        }
        if (decision.rules().isEmpty()) {
            return;
        }
        // Absent where nothing was read, which is not a count of none: a build that ran no row did
        // not see the rows take none of the rules, it saw nothing. Said the way every other
        // measure here says it, in the words the reason itself carries.
        Measure<DecisionEvidence.RowsPlaced> took = decision.took();
        out.append(took.made()
                .map(placed -> String.format("    decision    rules %d   taken %d%n",
                        decision.rules().size(), placed.rules().size()))
                .orElseGet(() -> String.format("    decision    rules %d   %s%n",
                        decision.rules().size(), ReasonProse.of(took.why()).sentence())));
        for (ReportedFinding f : behavior.reported()) {
            if (!(f.finding().about() instanceof About.ARuleNoRowTakes rule)) {
                continue;
            }
            out.append(String.format("      %s no row takes a decision rule%n",
                    mark(f.finding())));
            // Every condition of it, so that two rules of one behavior are told apart by what a
            // reader is shown. The sentence above says only which behavior, because what tells the
            // rules apart is the proposition each condition is keyed on and that is written the
            // one way round an account needs rather than the way the author wrote it.
            for (ShownCondition read : behavior.readingsOf(rule.ruled())) {
                out.append(String.format("          · %s%n", said(read, declaredIn, rendering)));
            }
        }
        // And the rules no row took that no finding is about, which are the two ways a rule is owed
        // no row. Counted rather than listed: what a reader does with a finding is write a row, and
        // there is no such work at either of these — a body whose search settled nothing about a
        // hundred of its ways would put a hundred entries in front of somebody who can act on none
        // of them, which is the account being shown as a backlog. The numbers close over the count
        // all the same, so nothing is lost and the rules themselves are in the document.
        gathered(out, behavior, decision, RuleRequirement.Unsettled.class,
                "      ? nothing could show a row can be written at %d decision rule%s%n");
        gathered(out, behavior, decision, RuleRequirement.Excluded.class,
                "      · no row is owed at %d decision rule%s%n");
    }

    /**
     * How many of a body's ways came to one answer, and how many to each reason within it.
     *
     * <p>One opening line and a line per distinct reason under it, which is what the report does
     * wherever a plurality of reasons arrives with no order of its own. The reasons are said in the
     * order written down for them rather than in the order the rules were walked or in order of how
     * many there are of each: a page that differs between runs of one unchanged model is wrong
     * however the numbers add up.
     *
     * <p>Nothing where none of the body's rules came to this answer. A line saying none is a line a
     * reader is asked to read for nothing, and the count above already says how many there are.
     */
    private static void gathered(StringBuilder out, BehaviorReport behavior,
                                 DecisionEvidence decision,
                                 Class<? extends RuleRequirement> answer, String opening) {
        Set<Said> order = new java.util.TreeSet<>(Said.IN_ORDER);
        Map<String, Integer> counted = new LinkedHashMap<>();
        int all = 0;
        for (DecisionReading.Ruled ruled : decision.read().found()) {
            RuleSettlement came = behavior.ruleSettlements().get(ruled.rule());
            if (came == null || !answer.isInstance(came.requirement())) {
                continue;
            }
            Said said = said(came);
            order.add(said);
            counted.merge(said.text(), 1, Integer::sum);
            all++;
        }
        if (all == 0) {
            return;
        }
        String line = String.format(opening, all, all == 1 ? "" : "s");
        // One reason on the line it is about. Written under it, the count would be said twice for
        // the same ways — which is what a body with one answer for all of them has, and that is
        // most of them.
        if (counted.size() == 1) {
            out.append(line.stripTrailing()).append(" — ")
                    .append(order.iterator().next().text()).append('\n');
            return;
        }
        out.append(line);
        order.forEach(said -> out.append(String.format("          · %d — %s%n",
                counted.get(said.text()), said.text())));
    }

    /**
     * What one answer of the search says, and where it is said among the others.
     *
     * <p>The order travels with the sentence because the two are one decision. Kept beside it, a
     * shape added to what a search comes back with would get a sentence and take whatever place an
     * enumeration happened to give it.
     *
     * <p>Two numbers rather than one, so that neither order is arithmetic over the other. Which
     * kind of answer it is, is said here, and where a word stands among the words of its own kind
     * is that kind's own order to give — asked of {@link PublicationOrders} the way every other
     * plurality this report says together is.
     */
    private record Said(int family, int within, String text) {

        /**
         * The order these are said in: by what kind of answer, then within it.
         *
         * <p>And by the sentence last, which orders nothing a reader sees and is what keeps two
         * answers two. A comparator that called them one where their places agree would drop a
         * sentence the count beside it still holds, and the numbers under a block would stop
         * adding up to the block.
         */
        static final java.util.Comparator<Said> IN_ORDER =
                java.util.Comparator.comparingInt(Said::family)
                        .thenComparingInt(Said::within)
                        .thenComparing(Said::text);
    }

    /**
     * What a search's answer about one rule says, and where among the answers it is said.
     *
     * <p>Both axes, because what a reader is owed is one sentence. Where the composing came to
     * nothing, what the sentence says is the shortfall — which is this compiler's and is what
     * could be done about it — and the requirement's own word for it says only that there was
     * nothing to try the rule with, which no reader acts on.
     */
    private static Said said(RuleSettlement came) {
        return switch (came.requirement()) {
            // Composed and run first, because they are what a reader can tell this compiler about:
            // a row that went elsewhere is a way this steered wrong and the model may be fine.
            case RuleRequirement.Unsettled.AComposedRowWentElsewhere _ ->
                    new Said(0, 0, "a row composed for one took another rule of the same body");
            case RuleRequirement.Unsettled.NothingWasComposedToTry _ ->
                    new Said(1, PublicationOrders.positionOf(
                                    came.synthesisShortfall().reason()),
                            whyUnresolved(came.synthesisShortfall()));
            case RuleRequirement.Unsettled.CouldNotTellWhereTheRowWent(var reading) ->
                    new Said(2, PublicationOrders.positionOf(reading), switch (reading) {
                        case NO_RULE_IS_RECOGNISABLE ->
                                "a row was composed and run, and every rule of this body turns on"
                                        + " something no run through it is recorded at";
                        case NO_RECOGNISABLE_RULE_MATCHES ->
                                "a row was composed and run, and this reading found no rule the"
                                        + " run did all of";
                        case MORE_THAN_ONE_RULE_MATCHES ->
                                "a row was composed and run, and this reading placed it at more"
                                        + " than one rule, which one run cannot have taken";
                    });
            case RuleRequirement.Unsettled.NothingWatchedTheRow _ ->
                    new Said(3, 0,
                            "a row was composed and run, and nothing watched where it went");
            // The model's own answers, after the ones that are about what this compiler managed.
            case RuleRequirement.Excluded.OnePositionCannotBeBoth _ ->
                    new Said(4, 0, "its way would need one position to be two things at once,"
                            + " which no value is");
            case RuleRequirement.Excluded.AnArmNothingReaches _ ->
                    new Said(5, 0, "its way goes through an arm the rules leave nothing for, which"
                            + " is an arm the branch count is made without");
            case RuleRequirement.Required _ ->
                    throw new IllegalArgumentException(
                            "a rule owed a row is said as the finding it is");
        };
    }

    /**
     * One condition of a rule, as a page writes it.
     *
     * <p>Which construct and which way, and never the proposition the account keys the condition
     * on: a rule holding {@code n <= 100} denied is a body whose author wrote {@code n > 100}, and
     * a page spelling the first would be showing them a comparison they did not write.
     *
     * <p>Exhaustive with no {@code default}, so a shape added to the reading is one somebody words
     * rather than one that goes quiet.
     */
    private static String said(ShownCondition shown, SourceId declaredIn,
                               SourceRendering rendering) {
        return switch (shown.read()) {
            case DecisionRuleReading.AComparisonCameOut(var _, var held) ->
                    "the comparison at " + shown.at().said(rendering, declaredIn)
                            + (held ? " holds" : " does not hold");
            case DecisionRuleReading.AForkTookAnArm(var arm) ->
                    "it goes through `" + ArmVocabulary.label(arm) + "` ("
                            + shown.at().said(rendering, declaredIn) + ")";
            // Every shape with nothing to send a reader to. What differs between them is which
            // part of this compiler fell short, which is not something an author acts on — and a
            // line is written for each so that the rule is never described by fewer conditions
            // than it turns on.
            case DecisionRuleReading.AConditionIsNotShown _,
                    DecisionRuleReading.AComparisonIsNotPlaced _,
                    DecisionRuleReading.AForkIsNotPlaced _ ->
                    "one condition of it is one this compiler has nothing to send you to";
        };
    }

    /**
     * The pair numbers as counts, never as one ratio.
     *
     * <p>A ratio needs a denominator that is known, and this one is not: a combination no row sits in
     * has not been shown unreachable, only untried. Printing 3/8 would read as five gaps when it may
     * be five impossibilities.
     */
    private static String combinations(PartitionEvidence.PairSpace pairs) {
        if (pairs == null || pairs.total() == 0 || pairs.counted().made().isEmpty()) {
            return "";
        }
        // A space too large to walk says so, and says it from what weakened the measurement rather
        // than from a flag kept beside it that had to be held in step.
        if (pairs.counted().weakening().causes().stream()
                .anyMatch(Weakening.PairSpaceTruncated.class::isInstance)) {
            return String.format("pairs %d, too many to enumerate", pairs.total());
        }
        if (pairs.decided()) {
            return String.format("pairs %d/%d", pairs.counts().covered(), pairs.total());
        }
        // Two numbers, because there are two facts. What the rows reach is counted, and what is
        // left is not known: nothing tried to build a row for it, so it has not been shown
        // unreachable either. `unknown` is the word the document writes, and it is written here
        // too — `untried` reads as an instruction to try, which is what nobody is asked to do.
        //
        // And whose rows, where not all of them were read. A combination none of the rows seen
        // reaches is not one none of the rows reaches, and the same number means the smaller thing.
        boolean whole = pairs.counted() instanceof Measurement.Complete<?>;
        // And that nobody is behind on the second number. A count printed with no obligation
        // beside it is read as one, which is what sent an author after a row that bought a
        // combination and no evidence; a combination is not a thing this compiler finds a gap
        // about, and nothing refuses over one (PairCombinationsAreNotRowObligationsTest).
        return String.format("pairs %d covered, %d unknown%s%s",
                pairs.counts().covered(), pairs.unknown(),
                whole ? "" : " of the rows that were read",
                pairs.unknown() == 0 ? "" : "; no row is owed at one");
    }

    /**
     * How many of a measure's parts have no number, and why, one note per distinct reason.
     *
     * <p>Grouped rather than summed. Two lines left unmeasured for two different reasons are two
     * facts, and one count over both would be a number whose sentence is true of only some of what it
     * counts — which is the shape of the thing this report stopped doing.
     */
    private static <T> String notes(List<T> unmeasured,
                                    Function<T, String> why) {
        Map<String, Long> counted = new LinkedHashMap<>();
        for (T each : unmeasured) {
            counted.merge(why.apply(each), 1L, Long::sum);
        }
        StringBuilder out = new StringBuilder();
        counted.forEach((said, count) ->
                out.append("   (").append(count).append(" not measured: ").append(said).append(')'));
        return out.toString();
    }

    /**
     * What is undecided about one obligation, a sentence per open question.
     *
     * <p>Two questions wear one verdict, and an author acts on them differently. Whether a row that
     * is written stands at the point is answered by reading more of what the rows hold; whether a
     * row can be written there at all is answered by this compiler keeping more of what it builds,
     * and the second is not work an author can do. Said in one sentence, the second reads as the
     * first and sends a reader to look through their own rows for something that is not there.
     *
     * <p>Read off the disposition and off nothing beside it. What each question is open on is what
     * the disposition carries, and a second reading of the evidence here would be a second answer
     * about one point, free to differ from the one the disposition holds. So this is handed the
     * disposition and not the assessment it came from: the evidence is not in reach of the sentence
     * at all, rather than in reach and left alone.
     */
    private static List<String> undecidedBecause(ObligationDisposition disposition, String point) {
        if (!(disposition instanceof ObligationDisposition.Undecided it)) {
            return List.of();
        }
        List<String> said = new ArrayList<>();
        for (ObligationDisposition.Uncertainty each : it.because().written()) {
            said.add(switch (each) {
                // And what the readings met, which is what makes it undecided rather than missed.
                // The reading carried whether a value was stopped or never arrived all the way
                // here, and a sentence that stopped at "undecided" would be the last step of the
                // carrying dropping it.
                case ObligationDisposition.Uncertainty.WhetherARowIsThere.ReadingsStopped(
                        ReadingReasons met) ->
                        "undecided whether a row is at the " + point + whatTheReadingsMet(met);
                // And why nobody read, where nobody did. Off the question and not off the coverage
                // beside it: what a question is open on travels with the question, so a sentence
                // that reached past it for the evidence would be one more reader working the
                // answer out on its own terms.
                case ObligationDisposition.Uncertainty.WhetherARowIsThere.NothingWasRead(
                        UnaskedReasons why) ->
                        "undecided whether a row is at the " + point + " — "
                                + ReasonProse.of(why.asOne()).clause();
                // Named for what happened, which is not one thing. A reading that did not come
                // back is of a row this compiler composed; a composing that stopped never had one
                // — and an opening written for the first says a row was built at a point where
                // none was. So the sentence is the gap's, and the gaps say which they are.
                case ObligationDisposition.Uncertainty.WhetherARowCanBeWritten.Stopped(
                        WritabilityKnowledge.Prevented stopped) ->
                        "nothing could show a row can be written at the " + point
                                + " — " + why(stopped);
                // And where nothing was stopped there is no budget to name. What the searches came
                // to is said under the point, a line per reading, so a summary here would be that
                // sentence written twice with less in it.
                case ObligationDisposition.Uncertainty.WhetherARowCanBeWritten.NothingShowedIt _ ->
                        "nothing could show a row can be written at the " + point
                                + " — no search of it established one";
            });
        }
        return said;
    }

    /**
     * What the readings of a point met instead of a number, said after the verdict.
     *
     * <p>One clause per reason and never one per reading. How many readings met a reason is how
     * many places the line was read at, which is said under the point one reading to a line.
     *
     * <p>Empty where they met nothing of their own — a point left undecided by a row that never ran
     * has its reason said where the row stopped, and repeating it here would be one gap wearing two
     * sentences.
     */
    private static String whatTheReadingsMet(ReadingReasons met) {
        List<String> said = new ArrayList<>();
        for (ReadingGap each : met.eachKindOnce().written()) {
            said.add(atTheBorder(each));
        }
        return said.isEmpty() ? "" : ", and " + String.join(", and ", said);
    }

    /**
     * What one reading met at a border instead of a number, in the reading's own words.
     *
     * <p>Where the reasons stay apart. Several of them weaken a document the same way and are one
     * word to the vocabulary that sorts on that; a reader is still owed which of them happened, and
     * this is what says it. So it is open to the census that holds every reason to a sentence
     * nothing else writes.
     */
    static String atTheBorder(ReadingGap why) {
        return switch (why) {
            case ReadingGap.Observation(Incompleteness.Code code) -> whatStopped(code);
            // Nothing was observed here, so nothing about an observation is said. What a reader is
            // owed is that no value arrived at all, which no budget would have changed.
            case ReadingGap.NoValue _ -> "the walk reached no value there to read";
            // And where nothing was looked at, what a reader is owed is that the position was never
            // read -- said the way it happened, since "no value there" is a claim about the row and
            // nothing here found anything out about one.
            case ReadingGap.CouldNotWalk _ -> "the walk to that position could not be taken";
            case ReadingGap.CouldNotReadRow _ -> "no row came back to read there";
        };
    }

    /**
     * Why nothing could show a row can be written at a point, in the words of what was stopped.
     *
     * <p>Every one of them and not the first. A point may have been stopped in more than one way —
     * a reading of one value that did not come back, a composing for another that never started —
     * and what a reader wants is everything that would have to give for the point to be settled.
     */
    private static String why(WritabilityKnowledge.Prevented stopped) {
        List<String> out = new ArrayList<>();
        for (EstablishmentGap each : stopped.by().written()) {
            out.add(switch (each) {
                case EstablishmentGap.Observation(CanonicalSelection<Incompleteness.Code> causes) ->
                        "a row was built for it, and " + causes.written().stream()
                                .map(AdequacyReport::whatStopped)
                                .collect(Collectors.joining(", and "));
                // What this compiler declined to build, and which figure decided it. An author does
                // nothing about this; what it says is that the point is open because of a policy
                // here, which is what keeps it out of the work they are told they owe.
                //
                // Said without claiming a search stopped, because one of the two ways to get here
                // is a search that ran to the end of what it was handed while the thing it was
                // handed was short of the point. The gap holds figures and not which of the two
                // this was, so what is written is what both of them establish: nothing was
                // composed, and a figure of this compiler's is why. Which way it happened is said
                // per search, where the outcome that knows is still in hand.
                // Two sentences and not one list, because what a reader does about them differs. A
                // figure is a number to raise and reaching it is why the search went no further; a
                // population this writes some of is work nobody has done, and no number anybody
                // raises reaches the rest of it. Run together, an author reads the second as
                // something to raise and finds that raising it changes nothing.
                case EstablishmentGap.Composition(var budgets, var repertoires) ->
                        "nothing was composed for it"
                                + (budgets.isEmpty() ? ""
                                        : ", and a figure of this compiler's is why: "
                                                + said(budgets))
                                + (repertoires.isEmpty() ? ""
                                        : ", and this compiler writes some of "
                                                + writes(repertoires)
                                                + " rather than all of them");
            });
        }
        return String.join(", and ", out);
    }

    /** What one observation gap cost, said as what it stopped rather than as its code. */
    private static String whatStopped(Incompleteness.Code code) {
        return switch (code) {
            case VALUE_TRUNCATED -> "the observation of it was stopped by a limit, "
                    + "so where it stands could not be read";
            case VALUE_UNREADABLE -> "nothing could read it back, "
                    + "so where it stands could not be read";
            // Nothing else stops a value being read back at a point: the rest are about a row that
            // did not run or a module nothing observed, and neither reaches a value just composed.
            default -> throw new IllegalStateException(
                    "an observation of a composed value stopped for a reason it cannot have: "
                            + code);
        };
    }

    /**
     * The line a measure with no number gets, under the behavior it is of.
     *
     * <p>Whether there is a line at all is settled here, because one rule settles it: a line whose
     * whole subject is one measure of one behavior says what that behavior is short of, and a
     * reason that is a fact about the run said under each behavior says one fact as many times as
     * the module has behaviors. Asked at each of the lines instead, the rule holds where whoever
     * wrote that line remembered it, which is not the same rule.
     *
     * <p>A clause written into a line that is there for another reason is not one of these. An
     * obligation and an axis are printed because they are owed, and the reason is what that line is
     * short of whatever the reason is about.
     */
    private static void sayWhy(StringBuilder out, String measure, MeasureReason reason) {
        if (reason.about() == MeasureReason.About.THE_BEHAVIOR) {
            out.append(String.format("    %s%s%n",
                    DisplayColumns.padRight(measure, 12), ReasonProse.of(reason).sentence()));
        }
    }

    /**
     * That a measure with numbers was not made in full, where it was not.
     *
     * <p>The numbers alone read the same either way, which is the thing this whole measure-level
     * answer is against: a behavior one of whose rules this compiler could not read showed the line
     * it did draw and the same {@code borders 1   coverage items 4/4} a model read to the end gets.
     * Why it was not is beside it already — the rules nothing took in, the positions the walk
     * could not reach into — so this says which measure they cost rather than saying them again.
     */
    private static String inFull(MeasurementStatus status) {
        return status == MeasurementStatus.PARTIAL ? "   (not all of it was measured)" : "";
    }

    /** What the search for a value at an edge came to, where it ran and found none. */
    private static String whatWasTried(SearchOutcomes outcomes,
                                       Map<ConditionReportAnchor, Citation> shown,
                                       SourceRendering rendering, SourceId declaredIn) {
        // Which searches are worth a sentence is the outcomes' answer, not this one's. Told apart
        // here, two of them would be one piece of news whenever the words for them happened to
        // match — a report deciding what happened from what it was about to write.
        List<String> said = new ArrayList<>();
        for (ItemAssessment.Attempt each : outcomes.worthSaying()) {
            said.add(whatWasTried(each, shown, rendering, declaredIn));
        }
        // One sentence per search, with the readings' own separator between them. Run together,
        // two searches of one reading read as one clause that says two things.
        return String.join(";", said);
    }

    /** The same, of one search of the point. */
    private static String whatWasTried(ItemAssessment.Attempt attempt,
                                       Map<ConditionReportAnchor, Citation> shown,
                                       SourceRendering rendering, SourceId declaredIn) {
        // One opening per outcome, and not one for everything that came back without a row. A
        // search this compiler stopped and a search that had everything and reached nothing are
        // different news, and a proof is not a failure at all — read under one opening, an author
        // is sent looking for a row nothing can write, or told nothing was composed for a point
        // where the composing never started.
        if (attempt == null) {
            return "";   // nothing ran, and what a run would have said is not this line's to guess
        }
        return switch (attempt) {
            case ItemAssessment.Attempt.Certified _, ItemAssessment.Attempt.Unverified _,
                 ItemAssessment.Attempt.Unavailable _ -> "";
            // And what it separately writes some of, where it does. Both are things the offer is
            // short of and only one is a number, so the second is said in its own clause rather
            // than folded into the figures or left out because a figure was there to name.
            case ItemAssessment.Attempt.Stopped it ->
                    " — this compiler stopped at " + said(it.stoppedBy())
                            + andWritesSomeOf(it.notAllOf()) + ": "
                            + it.why().said().orElseGet(() -> whyUnresolved(it.why()))
                            + whatTheRegionLeftOut(it.unaccountedFor(), shown, rendering, declaredIn);
            // Said as what this compiler writes rather than as a number it stopped at, because
            // there is no number: an author told to raise one would raise it and get the same
            // offer. What would change this is somebody writing the rest of what it walks, and the
            // sentence says so.
            case ItemAssessment.Attempt.Unexhausted it ->
                    " — this compiler writes some of " + writes(it.notAllOf())
                            + " rather than all of them: "
                            + it.why().said().orElseGet(() -> whyUnresolved(it.why()))
                            + whatTheRegionLeftOut(it.unaccountedFor(), shown, rendering, declaredIn);
            // Both halves, because neither says what the other does. The word is what the search
            // itself came to; the figure is why that word is not about the whole of the point. Said
            // as the word alone, an author reads a proof about a value this compiler never planned
            // for; said as the figure alone, they go looking for a search that stopped.
            case ItemAssessment.Attempt.Limited it ->
                    " — as far as this compiler plans, which stops at "
                            + said(it.limitedBy()) + ": "
                            + it.why().said().orElseGet(() -> whyUnresolved(it.why()))
                            + whatTheRegionLeftOut(it.unaccountedFor(), shown, rendering, declaredIn);
            // No search to report on, which is what this says instead of saying what one found. The
            // figure is what an author would raise to get one made at all.
            case ItemAssessment.Attempt.Unplanned it ->
                    " — nothing was planned for it, because this compiler stops at "
                            + said(it.limitedBy())
                            + whatTheRegionLeftOut(it.unaccountedFor(), shown, rendering, declaredIn);
            case ItemAssessment.Attempt.Unresolved it ->
                    (it.why().reason().provesInfeasible() ? " — " : " — nothing composed one: ")
                            + it.why().said().orElseGet(() -> whyUnresolved(it.why()))
                            + whatTheRegionLeftOut(it.unaccountedFor(), shown, rendering, declaredIn);
        };
    }

    /**
     * What the search ran over that the way to the border does not account for, where anything did.
     *
     * <p>Which conditions those are, and whether this outcome is one they bear on, is
     * {@link ItemAssessment.Attempt#unaccountedFor()}'s — so what is left here is the wording. A
     * report deciding it would be a second reader of the same two facts, and the only way to ask
     * what it decided would be to compile a model that produces this sentence.
     *
     * <p>What is said is what is known: these conditions are not represented in the region. Not
     * that the region is wider than the rows that reach the line — a condition nothing could take
     * in may be implied by the ones that were, or may hold of every row — and a sentence claiming
     * the wider box would be this report deciding something it has not been shown.
     */
    private static String whatTheRegionLeftOut(
            List<ReachabilityGap> left, Map<ConditionReportAnchor, Citation> shown,
            SourceRendering rendering, SourceId declaredIn) {
        if (left.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder("; not every condition on the way to the line is one"
                + " the row was composed against: ");
        for (int i = 0; i < left.size(); i++) {
            out.append(i == 0 ? "" : ", ")
                    .append(whyLeftOut(left.get(i)))
                    // The place last and in brackets, as every other line of this report writes
                    // one, and looked up rather than held: what the condition carries is which
                    // question places it, and this is where that question was put.
                    .append(" (").append(shown.get(left.get(i).anchor()).said(rendering, declaredIn))
                    .append(")");
        }
        return out.toString();
    }

    /**
     * What became of one condition, in the words a reader acts on.
     *
     * <p>The stage first, because it is what an author would do something about: a condition this
     * reading has no words for is one to write differently, and one it read and could not compose a
     * value under is one this compiler is short of a way to build.
     */
    private static String whyLeftOut(ReachabilityGap gap) {
        return switch (gap) {
            case ReachabilityGap.Unstated(var condition) ->
                    whyDeclined(condition.why());
            case ReachabilityGap.Uncomposed(var _, var why) ->
                    switch (why) {
                        case ReachabilityGap.Why
                                .NoValueComposedForItsPositions _ ->
                                "a condition on positions nothing here composed a value at";
                        case ReachabilityGap.Why
                                .TwoNumbersAtOneLocation _ ->
                                "a condition on another number taken where this row is already"
                                        + " being written for one";
                        // What stopped the looking, and not that nothing was found. An author does
                        // nothing about the first and may do something about the second.
                        case ReachabilityGap.Why
                                .TheWalkForItsPositionsWasStopped(var by) ->
                                "a condition on positions this compiler stopped looking at ("
                                        + said(by) + ")";
                    };
        };
    }

    /**
     * What a search that met a figure also walked in part, said after the figure, or nothing.
     *
     * <p>Beside the figure and never among them. What a reader does about the two differs, and a
     * stop that also wrote some of a population is a point where raising the figure is worth doing
     * and is not the whole of what is missing.
     */
    private static String andWritesSomeOf(
            CanonicalSelection<CompositionRepertoire> repertoires) {
        return repertoires.isEmpty() ? ""
                : ", and writes some of " + writes(repertoires) + " rather than all of them";
    }

    /**
     * What a population this compiler writes some of is called where a reader meets one.
     *
     * <p>Its own sentence and not one of the figures'. Nothing here is a number, so what a reader
     * is told is what this compiler writes rather than how much of it — and a population added
     * arrives here as a compile error rather than as a name nobody wrote a sentence for.
     */
    private static String writes(CanonicalSelection<CompositionRepertoire> repertoires) {
        List<String> out = new ArrayList<>();
        for (CompositionRepertoire each : repertoires.written()) {
            out.add(switch (each) {
                case WAYS_A_TOTAL_IS_SPREAD ->
                        "the ways a total may be spread over what adds up to it";
            });
        }
        return String.join(", ", out);
    }

    /**
     * What a budget of this compiler's is called where a reader meets one.
     *
     * <p>Its own words and not the constant's name. What the compiler calls a figure is a name for
     * the code that reads it; what a reader wants is what this compiler declined to do, in a phrase
     * they can act on — and a budget added arrives here as a compile error rather than as a name
     * nobody wrote a sentence for.
     */
    private static String said(CanonicalSelection<CompositionBudget> budgets) {
        List<String> out = new ArrayList<>();
        for (CompositionBudget each : budgets.written()) {
            out.add(switch (each) {
                case ELEMENTS_A_PROPOSAL_HOLDS -> "how many elements a proposed collection holds";
                case CHARACTERS_A_PROPOSAL_HOLDS -> "how many characters a proposed string holds";
                case PAIRINGS_BUILT_AT_ONCE -> "how many of a map's pairings are built at once";
                case ELEMENTS_A_TOTAL_IS_SPREAD_OVER ->
                        "how many elements a total is spread over";
                case SHAPES_OF_A_TOTAL_OFFERED -> "how many containers are offered for one total";
                case WAYS_DOWN_TO_A_TOTAL_TRIED ->
                        "how many ways down to what a total adds up are tried";
                case PLACES_A_PAIR_IS_TRIED_AT -> "how many places a pair is tried at";
                case STEPS_A_SEARCH_MAY_TAKE -> "how many steps a search takes";
                case ASSIGNMENTS_A_SEARCH_COMPOSES -> "how many assignments a search composes";
                case VALUES_OF_AN_UNBOUNDED_PROGRESSION_TRIED ->
                        "how many values of an unbounded progression are tried";
                case LEVELS_A_SIDE_IS_ASKED_AT -> "how many levels a side is asked at";
                case TIMES_THE_RULES_ARE_ASKED_AGAIN -> "how often the rules are read again";
                case VALUES_A_POSITION_ON_THE_WAY_IS_TRIED_AT ->
                        "how many values a position on the way is tried at";
                case DEPTH_A_CONSTRUCTION_PLAN_DESCENDS -> "how deep a value is built";
                case PATHS_OF_A_DECISION_READ ->
                        "how many paths through one body a decision is read for";
            });
        }
        return String.join(", ", out);
    }

    /**
     * What stopped one condition on the way from narrowing the search, in the words a reader acts
     * on.
     *
     * <p>One phrase per shape rather than one for all of them. What an author does about a condition
     * this reading has no words for is not what they do about a comparison it could not turn into a
     * cut, and neither is what they do about an arm that states one of two things — a single "was
     * not read" for all of them is the vocabulary being kept apart in the compiler and put back
     * together on the way out.
     */
    private static String whyDeclined(OnTheWay.Why why) {
        // Noun phrases, because what the line above them says is "not every condition ... is
        // represented", and each of these names one of those conditions. Written as sentences, the
        // place in brackets after them lands after a verb and reads as part of what is being said
        // rather than as where to look.
        return switch (why) {
            case OnTheWay.Why.NoWordsForTheShape _ ->
                    "a condition that is neither a comparison nor a combination of them";
            // Not the words a comparison gets for drawing no line: that answers why there is no
            // boundary, and its answers are wrong about this — a comparison of two constants is a
            // form nothing reads there, and a form this arithmetic cannot carry is a relation
            // between positions there, which is something a cut carries perfectly well.
            case OnTheWay.Why.ComparisonNotRepresentedAsACut _ ->
                    "a comparison this reading could not turn into a cut";
            case OnTheWay.Why.OneOfTwoThings _ ->
                    "an outcome that states one of two things";
            case OnTheWay.Why.ForkArmNotReadAsANarrowing _ ->
                    "an arm of a fork this reading could not read as a narrowing of a position";
        };
    }

    /** The category a search came back with, where the class it was about said nothing itself. */
    private static String whyUnresolved(Generator.UnresolvedCombination why) {
        String at = why.subject();
        return switch (why.reason()) {
            case NOTHING_COMPOSES_ONE -> "nothing here could build a representative for " + at;
            case ALL_CANDIDATES_REJECTED -> "every value tried at " + at + " was refused";
            // What the row is short of, and not what the model is short of. A row that stands
            // nothing in for a dependency its target requires is one nothing applies, so it is
            // held back rather than handed over to be pasted and refused.
            case NOTHING_STANDS_IN_FOR_A_DEPENDENCY ->
                    "nothing here could answer for a behavior " + at + " depends on, and a row"
                            + " that stands none in is a row nothing applies";
            // What this compiler does not write, and not what the model cannot have. An author
            // writes such an environment by hand, so the sentence says what it would be.
            case A_TABLE_IS_WHAT_THIS_NEEDS ->
                    at + " needs a behavior it depends on to answer by what it was applied to,"
                            + " which is a table written for the module and not a line on a row";
            // Not "stopped", which is one of the two ways a search leaves something untried and is
            // the only one with a number in it. Said as a stop, a walk that went to the end of what
            // this compiler writes is reported as one that halted, and an author looks for the
            // figure that halted it.
            case THE_SEARCH_LEFT_SOMETHING_UNTRIED ->
                    "the search left something untried before reaching " + at;
            // What is missing here, and not what cannot exist. The combinations are there and this
            // declined to walk them, so a reader is told the offer was not made rather than that
            // nothing reaches the arm — and told that raising the row budget is not what lifts it.
            case THE_GROUP_WAS_NOT_OFFERED ->
                    "the decisions that settle " + at + " have more combinations together than this"
                            + " offers a row for, so none of them was looked in";
            // What the partition divides this body's positions into, and not whether a run gets
            // there. Some decision on the way places at no class, so there is nothing to put a
            // value at that steers a row along it — a row put at what the rest of the way leaves
            // may go the other way round that fork.
            case THE_WAY_IN_PLACES_AT_NO_CLASS ->
                    "the way to " + at + " holds a decision that no class of any position stands"
                            + " for, so nothing here can steer a row along it";
            // The value is in hand and the list is as long as one block gets. Not said as a search
            // that stopped: nothing about this one was left untried, and an author who raised what
            // the search may walk would see the same line again.
            case THE_BLOCK_IS_AS_LONG_AS_IT_MAY_BE ->
                    "a value was found for " + at + " and this block offers as many rows as it may";
            case THE_RULES_LEAVE_NOTHING_THERE ->
                    "the rules leave no value at " + at;
            // What the model settles, said as that. A class under one case of a sum and a class
            // under another are classes of positions that are not in one value, so there is no row
            // to go looking for.
            case ONE_POSITION_CANNOT_BE_BOTH ->
                    at + " would need one position to be two things at once, which no value is";
            case NOTHING_TO_BUILD_AGAINST -> "there was nothing to build a candidate against";
            case NO_VALUES_WERE_ASKED_FOR ->
                    "this build composed no values, so no row was written for " + at;
            case LINKAGE_FAILED -> "the generated classes would not link";
            // "it" and not "the combination": a point of a line gets this word too, and a row
            // composed for one is not composed for a combination of classes.
            case NO_CERTIFIED_WITNESS ->
                    "no row composed for " + at + " was seen reaching it, which does not make it"
                            + " unreachable";
            case THE_POSITION_WAS_WITHHELD ->
                    "a row's value at that position could not be read, so no class of it was"
                            + " looked for";
            case THE_ROWS_WERE_NOT_READ -> "the rows were not read, so nothing was looked for";
            case NO_CANDIDATE_WAS_OFFERED ->
                    "the walk over what could stand there put no value forward";
            // What this run could look at, and not what the line admits. A line is owed once over
            // every behavior carrying the type, and the search of every one this asked about had
            // no answer — so nothing was looked for at the point.
            case NO_READING_OF_THE_LINE_COULD_BE_SEARCHED ->
                    "no reading of the line this asked about could be searched, so nothing was"
                            + " looked for at " + at;
        };
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /**
     * One measurement in the word a document uses.
     *
     * <p>Public because it is the projection, and the projection is the only thing that turns the
     * states into words. A caller outside this package that wants the word asks here; one that
     * wants the state asks the measure, which is what the arms are for.
     */
    public static MeasurementStatus statusOf(Measure<?> measure) {
        return ReportMeasurement.of(measure).status();
    }

    /**
     * How this report spells an enumerated value on the wire.
     *
     * <p>Here rather than at each field, so that the words a consumer reads have one origin. The
     * shipped schema names the same words in its own file and is held against this — against what is
     * written, not against a second reading of the enum, because those are different things and only
     * one of them is what a consumer sees. A spelling rule applied at ten call sites is ten places for
     * the schema to stop describing the output while every one of them still agrees with the enum.
     */
    public static String word(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    /** The same of a word held as a constant, which is what a reason that costs a proof to say has
     *  instead of an enum name. */
    public static String word(String constant) {
        return constant.toLowerCase(Locale.ROOT);
    }

    /** The same of a reason, which is not always an enum: one that costs a proof to say carries it,
     *  and a record is what holds a proof. The word is the reason's own either way. */
    public static String word(MeasureReason reason) {
        return reason.name().toLowerCase(Locale.ROOT);
    }

    /**
     * The questions a section is the reader of, each named by the rule that raised it.
     *
     * <p>One finding kind, filed by what it asks. Which measure answers a question is settled where
     * the question is raised; a report chooses where to print it, and nothing here decides what the
     * model asked.
     */
    private void unaccounted(StringBuilder out, BehaviorReport behavior,
                                    SourceRendering rendering,
                                    SourceId declaredIn,
                                    Predicate<PartitionEvidence.Unanswered> mine,
                                    PublishedRuleHandle.WhereARuleIs places, WhereAPartIs parts) {

        for (Adequacy.Finding f : behavior.findings()) {
            if (f.about() instanceof About.AQuestionNothingAnswered(var asked)
                    && mine.test(asked)) {
                // The two said as two. What the parts of the rule left is a list and reads as one;
                // what the position's answer was short of is a clause of its own, because a
                // reader running their eye down a list takes the entries in order and there is no
                // order between the two. Written into the same list, the limit the rules ran into
                // together would be the last thing the author wrote, and it is nothing they wrote.
                out.append(String.format("      %s not accounted for: %s — %s %s: %s%s%n",
                        mark(f), cited(asked.cited(), rendering, declaredIn, places),
                        asked(asked.asked()), subjectOf(asked),
                        whyStanding(asked, parts).written().stream()
                                .map(stop -> whyUnread(stop.reason())
                                        + sentTo(stop.sentTo(), parts, rendering, declaredIn))
                                .collect(Collectors.joining("; ")),
                        whatItsPositionWasShortOf(asked).map(AdequacyReport::whyUnread)
                                .map(each -> ", and the answer at its position: " + each)
                                .orElse("")));
            }
        }
    }

    /**
     * Why a question stands, in the words this document promises its reader.
     *
     * <p>Every reason and not one of them, because a question stands until every part that asked it
     * has been read: a part standing behind another is a second thing to lift, and naming one would
     * send an author to lift it and find the question still there. Which is also why nothing here
     * chooses between them — the only thing there is to choose by is which the reading met first.
     *
     * <p><b>What the parts of its rule left, and nothing else.</b> A limit its position's answer ran
     * into belongs to what the rules of that position come to between them and to no part of any of
     * them, so it has no place in an order taken from the source and is written on its own
     * ({@link #whatItsPositionWasShortOf}). Held here as well, the sequence would say that a form
     * nothing reads comes before an allowance that ran out, or after it, and the model says
     * neither — what it would be saying is which of this compiler's stores a reason came out of.
     *
     * <p>So what comes back says which of the two orders it is in. Parts an author wrote in one
     * text are in the order they wrote them; parts written across texts are in no order of
     * anybody's, because nothing an author did says which file comes first. A writer that wants to
     * tell a reader the order is the model's has to look at which of the two it is holding.
     *
     * <p>Carried and not claimed here. What the order is is known where the reasons still are what
     * a reading recorded, beside the places they stand on; by the time they are words a document
     * writes, nothing left can tell the author's order from a walk's, and a claim made here would
     * be a claim about something this cannot see.
     */
    private static List<ReportedReason.Stop> whyStanding(PartitionEvidence.Unanswered asked) {
        return ReportedReason.wordsFor(asked.stopped().itsRuleLeft());
    }

    /**
     * The same, in the order their author wrote them where that is an order.
     *
     * <p><b>The one place the claim is made, and the only one that can make it.</b> Which of two
     * reasons an author wrote first is a fact about where they wrote them; what a reading publishes
     * holds no place, so the claim is made here, out of the places this report has just resolved
     * them to. Made upstream, it was read off positions carried inside every answer — which is what
     * made an edit that moved a declaration an edit that changed what the model says.
     *
     * <p><b>One text is asked before anything is compared.</b> A line and a column are a place
     * inside one text and two numbers outside it, so a walk that sorted first and asked afterwards
     * would have ordered the ones it had no order for and then said so. A reason this report cannot
     * place is one nothing can be ordered against either, and it takes the whole list with it:
     * ordering the rest around it would put it wherever the walk left it and call that an author's
     * doing.
     *
     * <p>One reason is in an order by there being nothing to order it against, which is why the
     * list is asked its size before it is asked anything else.
     */
    private static ReportedReason.Published whyStanding(PartitionEvidence.Unanswered asked,
                                                        WhereAPartIs parts) {
        List<ReportedReason.Stop> these = whyStanding(asked);
        if (these.size() <= 1) {
            return ReportedReason.asTheAuthorWroteThem(AuthoredOrder.asWritten(these));
        }
        Map<ReportedReason.Stop, SourcePos> at = new LinkedHashMap<>();
        Set<SourceId> texts = new LinkedHashSet<>();
        for (ReportedReason.Stop each : these) {
            PublishedAt where = placeInTheRule(each.about(), parts).orElse(null);
            if (where == null || !(where.at().quotedFrom()
                    instanceof QuotedFrom.ASourceThisCompileHolds(SourceId in))) {
                return ReportedReason.inNoAuthoredOrder(steady(these));
            }
            at.put(each, where.at());
            texts.add(in);
        }
        if (texts.size() != 1) {
            return ReportedReason.inNoAuthoredOrder(steady(these));
        }
        List<ReportedReason.Stop> sorted = steady(these);
        sorted.sort(Comparator.comparing(at::get, SourcePos.IN_WRITTEN_ORDER));
        return ReportedReason.asTheAuthorWroteThem(AuthoredOrder.asWritten(sorted));
    }

    /**
     * These in a steady order that is nobody's, which is what both arms above are built on.
     *
     * <p>Read twice for two reasons. Where nothing an author wrote puts these in an order, this is
     * the whole of what a document may say: a sequence left to the walk would have one compiler
     * over one source write two documents, and which one a reader saw would be the run they
     * happened to make. Where an author did put them in an order, two of them at one place are told
     * apart by nothing they wrote — so the sort by place is stable over this and the tie falls back
     * to it rather than to the walk.
     *
     * <p>The vocabulary's own order, which is a set of words and says nothing about a model. What
     * an author wrote is asked of the places.
     */
    private static List<ReportedReason.Stop> steady(List<ReportedReason.Stop> these) {
        List<ReportedReason.Stop> out = new ArrayList<>(these);
        out.sort(Comparator.comparing(ReportedReason.Stop::reason)
                .thenComparing(ReportedReason.Stop::about, RuleSite.IN_A_STEADY_ORDER)
                .thenComparing(ReportedReason.Stop::sentTo, RuleSite.IN_A_STEADY_ORDER));
        return out;
    }

    /**
     * The word for what the position a question stands at was short of, where it was short of
     * anything.
     *
     * <p>Its own line and its own field, because what a reader does about it is its own: there is
     * no rule to go and look at, and what would lift it is an allowance.
     *
     * <p>One word and not a list holding one. There is one such limit, and a list would ask what
     * order its members are in before there are two to put in one — which is the question a
     * question's account answered by claiming an order it did not have. A second is a decision to
     * take when there is something to decide about.
     */
    private static Optional<UndividedPosition.Reason> whatItsPositionWasShortOf(
            PartitionEvidence.Unanswered asked) {
        return asked.stopped().itsPositionWasShortOf().map(ReportedReason::of);
    }

    /**
     * How a report writes the rule a question is about.
     *
     * <p>A name where the author gave one and a place where they did not, which is the same handle
     * a border prints for the line a comparison drew — the two share the formatter and the words,
     * and nothing else. What they must not share is an identity: where a rule was read is the
     * partition's and one rule has as many of those as it has readings.
     */
    private static String cited(Set<RuleCitation> offered,
                                SourceRendering rendering,
                                SourceId declaredIn,
                                PublishedRuleHandle.WhereARuleIs places) {
        return RuleHandleProse.said(PublishedRuleHandle.of(handle(offered, places), places),
                rendering, declaredIn);
    }

    /**
     * What tells one thing a row is owed for from every other, written the way {@code ruleId} is:
     * every part of the identity and nothing that merely reads well.
     *
     * <p>A key within this document, and the one a boundary finding joins to its obligation by.
     * Written out rather than left to the words beside it, because those do not tell them apart: a
     * guard on a name two cases spread whose cases stop the run in different places owes two
     * {@code IN} points, and both are the same role of the same rule at the same level — what
     * differs is where the run stops, which is {@code region} here and is in no sentence.
     *
     * <p>Every part, for the reason a rule's identity is written whole. Which line of the rule is
     * {@code which}, which carries the rule and whatever names a line of that kind of rule: one
     * clause places as many lines as it has ends. What the line says about its own value is
     * {@code facts}: {@code value >= 5 && value <= 5} puts a minimum and a maximum at one place and
     * they are two lines. Which declarations took an end in is {@code narrowedWithin}: a bound
     * another type narrowed is not the bound it narrows.
     */
    private static void obligationId(ObjectNode into, ObligationIdentity identity,
                                     DocumentSources sources) {
        switch (identity) {
            case ObligationIdentity.OfALine(var point) -> obligationId(into, point);
            case ObligationIdentity.OfAnArm(var arm) -> armId(into, arm, sources);
            // The axis and which class of it, which is what an axis of the document is keyed by.
            // The words a report writes for a class are not it: two positions of one behavior can
            // divide into classes that read alike, and a consumer joining on the words would join
            // one behavior's finding to the other position's entry.
            case ObligationIdentity.OfAClass(var owed) -> {
                into.put("axis", owed.at().toString());
                into.put("class", owed.classId());
            }
            // The behavior, which of its inputs and which case — what a case of an input is owed at
            // where nothing divides that input into classes. The input by its number, because a
            // behavior that declares no parameters has no name to call it by.
            case ObligationIdentity.OfAnInputCase(var behavior, var at, var missing) -> {
                into.put("behavior", behavior);
                into.put("input", at);
                into.put("case", missing.name());
            }
            case ObligationIdentity.OfADecisionRule(var behavior, var rule) ->
                    ruleId(into, behavior, rule);
        }
    }

    /**
     * What tells one rule of a decision from every other, which is not what a reader is shown.
     *
     * <p>The behavior and what the path consulted. The propositions are the canonical ones — a
     * comparison and its denial are one column, so {@code n > 100} in a source is written here as
     * {@code n <= 100} denied — which is what makes the table exclusive and is the reason nothing
     * here goes into a sentence. What a person is shown is under {@code subject} and in the notes
     * beside it, where the construct the author wrote is pointed at instead.
     *
     * <p>Sorted by what each condition is written as, and not in the order a walk met them. Two
     * runs that read one body's ways in two orders state one rule, so an identity carrying the walk
     * order would have a consumer joining on it land on nothing after a change that moved nothing.
     * The order the author wrote them in is a thing a reader is shown and is in the notes.
     *
     * <p>A condition the path never consulted is absent, which is what a rule leaving it out means:
     * a short-circuit that settled before reaching a condition states nothing about it, and an
     * entry saying so would be a don't-care written as a value.
     */
    private static void ruleId(ObjectNode into, String behavior, DecisionRule rule) {
        into.put("behavior", behavior);
        ArrayNode conditions = into.putArray("conditions");
        rule.consulted().values().stream()
                .map(AdequacyReport::conditionId)
                .sorted(java.util.Comparator.comparing(each -> each.get("condition").asString()
                        + "/" + each.get("outcome").asString()))
                .forEach(conditions::add);
    }

    /** One column of the rule and what the path came out as, as the identity keys it. */
    private static ObjectNode conditionId(DecidedCondition decided) {
        ObjectNode out = JsonNodeFactory.instance.objectNode();
        switch (decided) {
            case DecidedCondition.Compared(var condition, var held) -> {
                out.put("kind", "comparison");
                out.put("condition",
                        condition.proposition() + " " + condition.form());
                out.put("outcome", held ? "held" : "denied");
            }
            case DecidedCondition.Stood(var condition, var held) -> {
                out.put("kind", "truth");
                out.put("condition", subjectOf(condition.of()));
                out.put("outcome", held ? "held" : "denied");
            }
            case DecidedCondition.Narrowed(var condition, var to) -> {
                out.put("kind", "case");
                out.put("condition", subjectOf(condition.of()));
                out.put("outcome", to.spelled());
            }
            // A condition this compiler had no words for, named by the reading that met it. Two
            // such conditions mean nothing to be told apart by, so the occurrence is the identity
            // — which is what the reading already decided and is not a second answer here.
            case DecidedCondition.Unread(var condition, var held) -> {
                out.put("kind", "not_read");
                out.put("condition", condition.met().toString());
                out.put("outcome", held ? "held" : "denied");
            }
        }
        return out;
    }

    /** What a truth is the truth of, as the identity spells it. */
    private static String subjectOf(DecisionSubject subject) {
        return switch (subject) {
            case DecisionSubject.AnInput _, DecisionSubject.AnAnswer _ -> subject.toString();
        };
    }

    private static void obligationId(ObjectNode into,
                                     BorderObligationPoint point) {
        authoredLineId(into.putObject("line"), point.line().line());
        level(into.putObject("level"), point.line().at());
        location(into.putObject("location"), point.point());
        // Absent for a point at a value of the quantity, which has no run to be stopped.
        if (point instanceof BorderObligationPoint.InRegion region) {
            farEnd(into.putObject("stops"), region.region());
        }
    }

    /**
     * Where on the quantity a point is, which is what identifies it within its line.
     *
     * <p>Which of the four it is is not written here. Two points of one line can be the same one of
     * the four — a rule that names a value owes a row outside it on each side — so an identity
     * carrying the role would have the two of them come out equal, and a consumer joining a finding
     * to its obligation would land on whichever the walk wrote first. The word is published beside
     * this, where a reader is being told what a point is rather than which one it is.
     */
    private static void location(ObjectNode into,
                                 DomainPoint at) {
        into.put("kind", switch (at) {
            case DomainPoint.AtTheLine _ -> "at_the_line";
            case DomainPoint.BesideTheLine _ -> "beside_the_line";
            case DomainPoint.InTheRegion _ -> "in_the_region";
        });
        if (at.side() != null) {
            into.put("side", word(at.side()));
        }
    }

    /**
     * One line of the model, by the rule that drew it and which of that rule's lines it is.
     *
     * <p>Which of the rule's lines says what named it, because the three kinds of rule do not
     * decompose alike: a declaration's clause is written in the parts its author joined, a part of a
     * behavior's clause states as many things as a reading of it finds, and a comparison in a body
     * is a rule apiece with nothing under it. Written as one number, a reader was handed a count
     * without being told which of the three made it, and no two of them mean the same.
     *
     * <p>The rule stands inside the same object as the numbers that are counted within it, so what
     * a document allows is what this compiler can build: a part number beside a body's comparison is
     * not a shape a reader has to decide what to do with, because the schema has no such shape.
     */
    private static void authoredLineId(ObjectNode into,
                                       AuthoredLine line) {
        ObjectNode which = into.putObject("which");
        switch (line.which()) {
            case souther.compiler.partition.WhichLine.OfADeclarationsLine it -> {
                which.put("kind", "part");
                ruleId(which.putObject("rule"), it.rule());
                which.put("part", it.part().ordinal());
                // And which of that part's lines, which the part does not say. A conjunct states as
                // many comparisons as a reading arrives at inside it, so a document naming the
                // conjunct alone says the same of the two ends of
                // `Bool.not(String.length(name) < 1 || String.length(code) < 1)` — one line where
                // the author drew two. Written from what the line already is rather than counted
                // here: a number this assigned would be a second answer to which line a line is.
                declaredLine(which.putObject("line"), it.drawnBy());
            }
            case souther.compiler.partition.WhichLine.OfAComparisonOfAPart it -> {
                which.put("kind", "statement_of_part");
                ruleId(which.putObject("rule"), it.rule());
                which.put("part", it.statement().part().ordinal());
                // Which of the things the part states, and not which of the comparisons in it. A
                // choice states neither of its sides and holds its place, so the second statement of
                // a part is not its second comparison — and a reader told "comparison" would count
                // the ones it can see and land somewhere else.
                which.put("statement", it.statement().ordinal());
            }
            case souther.compiler.partition.WhichLine.OfAComparison it -> {
                which.put("kind", "comparison");
                ruleId(which.putObject("rule"), it.rule());
            }
        }
        ObjectNode facts = into.putObject("facts");
        // Which side of the line the value it wrote belongs to is an order's own answer. A rule
        // that names a value orders nothing either side of it, so a document writing a side there
        // would be publishing an answer to a question the rule was never put — and readers telling
        // two lines apart by these facts would be telling them apart by it.
        if (line.facts().claim()
                instanceof ComparisonClaim.Cut order) {
            facts.put("valueBelongsBelow", order.valueBelongs() == Towards.BELOW);
        }
        facts.put("holdsAtTheValue", line.facts().holdsAtTheValue());
        facts.put("singles", line.facts().singles());
        ArrayNode within = into.putArray("narrowedWithin");
        line.narrowedWithin().forEach(each -> typeId(within.addObject(), each));
    }

    /**
     * Which line of a declaration's conjunct this is, as the reading that drew it named it.
     *
     * <p>Two shapes because a conjunct draws lines two ways, and a document that had one word for
     * both would be saying which of them a reader is holding by leaving it out. A comparison inside
     * the conjunct places its own end and the statement that read it names that line; what taking
     * the conjunct away moves is the conjunct's line, paired with whichever of its statements are
     * about the number so that the lines it draws on two numbers are two.
     *
     * <p>Translated and not counted. The statements are numbered where a conjunct is read for what
     * it states, and a number assigned here would be a second answer to which line a line is —
     * which is the mistake the conjunct alone already was.
     */
    private static void declaredLine(ObjectNode into,
                                     souther.compiler.check.DeclaredLine line) {
        switch (line) {
            case souther.compiler.check.DeclaredLine.OfAStatement it -> {
                into.put("kind", "statement");
                into.put("statement", it.statement().ordinal());
            }
            case souther.compiler.check.DeclaredLine.OfAConjunct it -> {
                into.put("kind", "conjunct");
                ArrayNode paired = into.putArray("pairedWith");
                it.pairedWith().stream()
                        .map(souther.compiler.check.InvariantStatementId::ordinal)
                        .sorted()
                        .forEach(paired::add);
            }
        }
    }

    /** A level, as the thing it is a level of writes it: a place on a carrier, or a number. */
    private static void level(ObjectNode into, Level at) {
        switch (at) {
            case Level.OnACarrier on -> {
                into.put("kind", "on_a_carrier");
                carrier(into.putObject("carrier"), on.of());
                into.put("at", on.at().key());
            }
            case Level.ACount count -> {
                into.put("kind", "a_count");
                into.put("at", count.at().key());
            }
        }
    }

    /**
     * Which declaration a type is, as this document carries it.
     *
     * <p>The pair, because that is what a type's identity is: two modules may each declare a
     * {@code Status} and they are two types. Written as one word — the name alone, or the two
     * joined — the two project onto one identity, and a module name has dots in it so a joined
     * spelling cannot even be taken apart again. One writer, because every place a published
     * identity names a type wants the same answer.
     *
     * <p>A type the language declares has no module to name. It is one of a closed set the
     * language fixes, so its name is its identity and {@code kind} says which sort it is.
     */
    private static void typeId(ObjectNode into, TypeSymbol type) {
        switch (type) {
            case TypeSymbol.AtModule at -> {
                into.put("kind", "declared");
                into.put("module", at.module());
                into.put("name", at.name());
            }
            case TypeSymbol.Primitive it -> {
                into.put("kind", "primitive");
                into.put("name", it.name());
            }
            case TypeSymbol.LanguageCase it -> {
                into.put("kind", "language_case");
                into.put("name", it.name());
            }
        }
    }

    /**
     * Which order a level is counted on, in this document's own words.
     *
     * <p>Said by a switch over the orders there are rather than by what this compiler calls one to
     * itself. A record's {@code toString} is a rendering of a Java class — {@code Whole[]} — and
     * publishing it as part of an identity makes the class's shape a contract: a field added to a
     * carrier, or a rename, would silently be a new identity for the same order.
     */
    private static void carrier(ObjectNode into, Carrier of) {
        switch (of) {
            case Carrier.Whole _ -> into.put("kind", "whole");
            case Carrier.Dense _ -> into.put("kind", "dense");
            case Carrier.Days _ -> into.put("kind", "days");
            case Carrier.Seconds _ -> into.put("kind", "seconds");
            case Carrier.SecondsOfDay _ ->
                    into.put("kind", "seconds_of_day");
            case Carrier.Nanos _ -> into.put("kind", "nanos");
            case Carrier.Text _ -> into.put("kind", "text");
            // The enumeration itself, because two enumerations are two orders however alike their
            // cases count. Which cases it has is what the order is made of and is written with it.
            case Carrier.Ordinal it -> {
                into.put("kind", "ordinal");
                // The enumeration and the places it declares, because two enumerations are two
                // orders however alike their cases count, and where a case comes in the
                // declaration is what the order is.
                typeId(into.putObject("enumeration"), it.enumeration());
                ArrayNode cases = into.putArray("cases");
                it.cases().forEach(each -> typeId(cases.addObject(), each));
            }
        }
    }

    /** Where the run beside a line stops, which is one of the three things that can have stopped
     *  it. */
    private static void farEnd(ObjectNode into, FarEnd end) {
        switch (end) {
            case FarEnd.AtALine(var line, var where) -> {
                into.put("kind", "at_a_line");
                authoredLineId(into.putObject("line"), line);
                into.put("where", where.key());
            }
            case FarEnd.AtTheDomain(var reaches) -> {
                into.put("kind", "at_the_domain");
                level(into.putObject("at"), reaches.at().written());
                into.put("per", reaches.at().per().toPlainString());
                into.put("inclusive", reaches.inclusive());
            }
            case FarEnd.AtTheOrderEnd(var towards) -> {
                into.put("kind", "at_the_order_end");
                into.put("towards", word(towards));
            }
        }
    }

    /**
     * What tells one rule of the model from another, as this document carries it.
     *
     * <p>The parts that are the identity and no more. A rule is a clause of an invariant, a
     * comparison written in a body, or a rule of an {@code ensures} clause, and each is told from
     * its neighbours by different coordinates — so this is an object per kind rather than one
     * spelling every kind is squeezed into. Not the internal value's own words: what a compiler
     * calls a rule to itself is not a contract, and this is.
     *
     * <p>Not a name and never shown to a reader. `rule` beside it is what an author is given, and
     * the two are different questions: a handle finds a rule and an identity distinguishes one.
     */
    static void ruleId(ObjectNode into, RuleRef rule) {
        into.put("kind", schemaRuleKind(rule));
        // Every part of the identity and not the parts that read well. A declaration is its module
        // and its name — two modules may each declare an `Amount` and they are two types — and a
        // construct is numbered from zero in each source, so the module is what tells one behavior's
        // twelfth construct from another's. Written without them, two rules of two modules project
        // onto one identity, which is the one thing this field may not do.
        switch (rule) {
            // The clause, by the declaration it is written on and its place among that
            // declaration's clauses — which is how somebody reading the declaration counts them.
            case RuleRef.Invariant it -> {
                into.put("declaredIn", it.clause().id().declaredOn().module());
                into.put("declaredOn", it.clause().id().declaredOn().name());
                into.put("clause", it.clause().id().ordinal());
            }
            // The rule of the clause, by the behavior it is declared on and where it sits among the
            // clauses and their arms. Two arms naming one case are two rules and differ here.
            case RuleRef.Ensures it -> {
                into.put("declaredIn", it.rule().behavior().module());
                into.put("behavior", it.rule().behavior().name());
                into.put("clause", it.rule().clause());
                into.put("arm", it.rule().arm());
            }
            // The comparison, by the definition that wrote it and the construct it was numbered as
            // there, and by the behavior reading it. The two are not one: a helper's comparison is
            // read by every behavior that calls it, and the numbering starts at zero in each
            // definition, so without the definition two helpers' first comparisons are one rule.
            case RuleRef.Comparison it -> {
                into.put("declaredIn", it.writtenIn().module());
                into.put("definition", it.writtenIn().definition());
                into.put("behavior", it.behavior());
                into.put("ordinal", it.origin().ordinal());
                into.put("lowered", it.origin().lowered());
            }
            // The fork, by the same coordinates a comparison is told apart by: a fork is a construct
            // of a definition, numbered there, and read by every behavior that calls it. Which
            // construct it is is the ordinal's to say, as a comparison's is — a document that also
            // wrote the keyword would be publishing what the source spells rather than what tells
            // one rule from another.
            case RuleRef.Fork it -> {
                into.put("declaredIn", it.writtenIn().module());
                into.put("definition", it.writtenIn().definition());
                into.put("behavior", it.behavior());
                into.put("ordinal", it.origin().ordinal());
                into.put("lowered", it.origin().lowered());
            }
            // An application the author wrote, told from the others by which application it is —
            // which takes what it was counted within, the way a comparison's does. What differs
            // from a comparison is that a behavior writes one of these in two places: a definition
            // wrote it, or the behavior's own statement of itself did, and both count from zero. So
            // which of the two is written, and the definition with it where a definition wrote it.
            // Told apart by whether `definition` is there, the two would rest on an absence that
            // already says something else in this document.
            case RuleRef.Predicate it -> {
                into.put("declaredIn", it.writtenIn().module());
                switch (it.writtenIn()) {
                    case WrittenOwner.Body body -> {
                        into.put("writtenIn", "body");
                        into.put("definition", body.definition());
                    }
                    // The behavior's own clauses, whose name is `behavior` below — one name and not
                    // two that happen to agree, which is what the rule refuses to be built
                    // otherwise ({@link RuleRef.Predicate}). Written twice it would be one fact in
                    // two fields, and a reader would have to say which of them it grouped by.
                    default -> into.put("writtenIn", "stated");
                }
                into.put("behavior", it.behavior());
                into.put("ordinal", it.origin().ordinal());
                into.put("lowered", it.origin().lowered());
            }
        }
    }

    /**
     * How schema 3 spells which kind of rule an identity is of.
     *
     * <p>A wire value and not a word for the rule, which is what the name used to say. What a rule
     * with no name is called is {@link RuleRef.Written#whatItIs}'s answer, and what one with a name
     * is called is the author's; the two surfaces say one thing and are still separate values,
     * because what a document groups by is a contract a version pins and what a reader is shown is
     * not. That they agree today is held over every kind of rule the seal has rather than by their
     * being one string.
     *
     * <p>Here rather than at the one place it is written, for the reason the others are. No
     * {@code default}, so a rule shape added and not given a spelling stops the compile rather than
     * arriving in a document as one that already existed.
     */
    public static String schemaRuleKind(RuleRef rule) {
        return switch (rule) {
            case RuleRef.Invariant _ -> "invariant";
            case RuleRef.Ensures _ -> "ensures";
            // The rule and not the construct it is written in. A comparison may stand in the
            // condition of an `if` or a `guard`, be given a name above the fork that tests it, or be
            // what the behavior answers with, and it is one rule in all of those — so `guard` was a
            // word for where some of them happen to be written, and false of the rest. Documents of
            // version 3 carry `guard` here; the word moved with the version rather than under one,
            // because a document already written groups by what it was told.
            case RuleRef.Comparison _ -> "comparison";
            // A fork whose condition states none of the others. Its own word because what a reader
            // does about it differs from all of them: there is no line and no set of values here,
            // only that the model forks on something this compiler did not read.
            case RuleRef.Fork _ -> "fork";
            // Its own word beside that one, and not the same word. Both are rules a body writes,
            // and what a reader does about them differs: a comparison puts a line on the order the
            // values are counted on, and this tells a set of them from the rest.
            case RuleRef.Predicate _ -> "predicate";
        };
    }

    /**
     * The word a document writes for which kind of thing a question is about.
     *
     * <p>Here rather than at the one place it is written, for the reason the others are: a reader
     * holding the arms can be held to the words without reading the writer. No {@code default}, so
     * an arm added and not given a word stops the compile rather than arriving in a document as one
     * that already existed.
     */
    public static String subjectWord(StandingQuestion asked) {
        return switch (asked) {
            case StandingQuestion.Exact it -> switch (it.asks()) {
                case InputQuestion.AboutAPosition _,
                     InputQuestion.AboutANumber _ -> "position";
            };
            // Not a position, and not a number of one: what such a rule is about is what nothing
            // worked out — of the end in the one case and of the whole rule in the other, and
            // neither has a subject. What the `path` beside this names is where a reader is sent to
            // look, which is one word because it is one thing.
            case StandingQuestion.BoundaryUndetermined _,
                 StandingQuestion.NothingClassifiesIt _ -> "filedAt";
        };
    }

    /**
     * The word a document writes for what a standing question asks.
     *
     * <p>Its own vocabulary and not the compiler's. A question asks one of the two coverage
     * obligations, or asks whether a rule places an end where nothing worked that out, or is a rule
     * nothing classified at all — and a document with a word only for the obligations would have to
     * leave the other two out or call them one of those.
     *
     * <p>Four words for four states, and the last two are not one. A rule whose end is undecided has
     * been classified far enough to say it restricts the values there, which is why the classes
     * close over it and only the border stays open; a rule nothing classified holds both open. Under
     * one word a reader of the document could not tell which of those a behavior is waiting on, and
     * the two are lifted by different work.
     */
    public static String questionWord(StandingQuestion asked) {
        return switch (asked) {
            case StandingQuestion.Exact it -> word(it.obligation());
            case StandingQuestion.BoundaryUndetermined _ -> "boundaryNotDetermined";
            case StandingQuestion.NothingClassifiesIt _ -> "notDetermined";
        };
    }

    /**
     * The word a document writes for how far a position's rules were read.
     *
     * <p>Here rather than at the one place it is written, so a reader holding the arms can be held
     * to the words without reading the writer. No {@code default}: an arm added and not given a
     * word stops the compile rather than arriving in a document as one that already existed.
     */
    public static String readingWord(PartitionEvidence.AxisCoverage.Reading read) {
        // Partial covers both, and what is written beside it says which. A reader keying on the
        // word is told the numbers rest on something unfinished, which is what the word is for; the
        // two facts under it are not exclusive and each has a key of its own.
        return read.answered() ? "complete" : "partial";
    }

    /**
     * The report as a build reads it, explaining the source identities it carries.
     *
     * <p>The names are asked for here for the reason {@link #human} asks for them, and are put to a
     * different use. What this document says about a source is said with the identity the caller
     * handed the source over as, because that is what this compilation refers to the source by, and
     * so what makes two reasons about one file the same reason. A name is not that: it is chosen from
     * the files in front of a reader, so it says what to show and not which source.
     * That leaves the document unreadable on its own — a position in a list says nothing to anyone who
     * does not also hold the list — so the identities are written and the {@code sources} table says
     * what each of them was, and a consumer holding neither the argument list nor the editor's
     * documents can still say which file a reason is about.
     *
     * <p>Which identities get an entry is not decided here. Everything that writes one asks
     * {@link DocumentSources} for the string to write, so the table is what the document turned out to
     * carry rather than a second list of the places an identity can appear.
     */
    public String json(SourceRendering rendering) {
        DocumentSources sources = new DocumentSources(rendering);
        ObjectNode root = JSON.createObjectNode();
        root.put("schemaVersion", schemaVersion);
        root.put("compilerVersion", compilerVersion);
        root.put("status", wire(status()));
        weakening(root, weakenedBy);
        root.put("adequacy", word(adequacy()));
        // Beside the verdict rather than inside it. What `adequacy` is has not changed — a word
        // every document since the fifth version has carried — and what is new is the facts that
        // word is open on, which is a second thing to read and not a different spelling of the
        // first.
        keptOpenBy(root, sources);
        ArrayNode modulesOut = root.putArray("modules");
        for (ModuleReport module : modules) {
            ObjectNode m = modulesOut.addObject();
            m.put("module", module.module());
            m.put("status", wire(module.status()));
            weakening(m, module.weakenedBy());
            incompleteness(m, module, sources);
            // What the module's declarations are short of, beside what its bodies are. A line an
            // `invariant` drew is not any behavior's, so publishing it under one would publish it
            // under whichever a walk reached first — and left out, a consumer counting what a build
            // refuses over would come up short of what the page shows (issue #1062).
            //
            // Under the declaration that owes it, the way a behavior's findings are under the
            // behavior. What a finding says of itself is what the line asks of a row, and two
            // declarations bounding a string's length at one say it the same way — so published as
            // one flat list they are two identical objects, and which declaration a reader is being
            // sent to is exactly what {@link FindingSubject} was introduced
            // to keep.
            declarations(m.putArray("declarations"), module.declarations(), module.debts(),
                    sources, module.owedByDeclarations().rulePlace());
            ArrayNode behaviors = m.putArray("behaviors");
            for (BehaviorReport behavior : module.behaviors()) {
                ObjectNode b = behaviors.addObject();
                b.put("name", behavior.name());
                b.put("implementation", behavior.implementation().written());
                // Written where the rows were read, and left out where they were not. A zero here
                // is a behavior whose rows were read and numbered none of them, which a consumer
                // acts on differently from rows nobody read.
                behavior.rowCount().ifPresent(count -> b.put("rows", count));
                behavior.pending().ifPresent(count -> b.put("pending", count));
                b.put("status", wire(behavior.status()));
                weakening(b, behavior.weakenedBy());
                signature(b, behavior.name(), behavior.signature(), sources);
                partition(b, behavior.partition(), behavior.boundaryReadings(),
                        behavior.account(), behavior.claimed(), sources,
                        behavior.rulePlace(), behavior.partPlace());
                branch(b, behavior, sources);
                decision(b, behavior);
                findings(b, behavior, sources);
            }
        }
        // Last, because what it explains is what was written above it. Where a field sits in an
        // object is nothing a reader of JSON reads, and collecting the identities first would mean
        // walking the report twice to learn what writing it says anyway.
        ObjectNode table = root.putObject("sources");
        sources.table().forEach(table::put);
        return root.toPrettyString();
    }

    /**
     * Where in which source, written once for everything that says it.
     *
     * <p>One writer for the shape, so that a source identity has one way into this document. It was
     * spelled out at each of the two places that point into a source, which is two places to write a
     * position and a line and two places to know that the id needs explaining — and a third would
     * have been written the way the first two were.
     *
     * <p>{@code writtenAt} says what the numbers beside it are. They are where this compile met the
     * code, which is where the code is written for everything read from a source this compile holds
     * and is a call in the caller's file for a body spliced in from one it does not. A consumer
     * handed the numbers alone was told an arm of {@code List.filter} is at {@code m.sou:15:23}. The
     * words come from the citation itself, so this document and the JSON a diagnostic is read from
     * say it the same way.
     */
    private static void at(ObjectNode into, Citation where, DocumentSources sources) {
        at(into, PublishedAt.of(where).orElseThrow(() -> new NoPlaceToWrite(where)), sources);
    }

    /**
     * What a module could not read, written under its own key.
     *
     * <p>Its own method, and it writes this array and no other. The order these come in is one this
     * compiler decides rather than one the model has, and what says so is that they are written
     * from a sequence somebody put in order — which is a property of the method that writes them
     * and is worth nothing if the same method writes an array that has an order already.
     *
     * <p>The places chosen and the entries put in order before any of it is written, because
     * writing is what records which sources this document owes an explanation of — so a comparison
     * made while writing would decide that table's order by how often it was asked a question.
     */
    private static void incompleteness(ObjectNode of, ModuleReport module,
                                       DocumentSources sources) {
        ArrayNode gaps = of.putArray("incompleteness");
        for (PublishedIncompleteness gap : module.incompleteness().written()) {
            ObjectNode g = gaps.addObject();
            g.put("code", word(gap.fact().code()));
            g.put("scope", word(gap.fact().scope()));
            // What the subject is, is the reason's answer; that this document has now written an
            // identity down and owes an account of it is this renderer's. The two are asked and
            // answered in that order, and neither side holds the other's half.
            g.put("subject", gap.fact().sourceIdentity()
                    .map(sources::written).orElseGet(gap.fact()::subject));
            // The one surface with a field a place is missing from, so the one that has anything to
            // refuse. A fact met nowhere a reader can be sent is written without a place, which the
            // schema allows; a fact met only where this document may not point is the other thing,
            // and the page and the generated block go on saying it because neither points anywhere.
            if (gap.metWhereNothingCanBeWritten()) {
                throw new NoPlaceToWrite(gap.fact());
            }
            gap.at().ifPresent(where -> at(g, where, sources));
        }
    }

    /**
     * The same, of a place already chosen out of the several a fact was met at.
     *
     * <p>The one writer of the field, so that a place a document points at is the same shape
     * wherever it is written. Which place it is has been settled by then: this records the source
     * as one the document owes an explanation of, and recording it is the last thing that happens
     * to a place.
     */
    private static void at(ObjectNode into, PublishedAt place, DocumentSources sources) {
        place(into.putObject("at"), place, sources);
    }

    /**
     * The same, into a node the caller has already put under whatever key it is writing.
     *
     * <p>The fields and not the key, because more than one key carries a place and the shape they
     * carry is one — a consumer reading a place under a second name should not have to learn a
     * second spelling of it.
     */
    private static void place(ObjectNode at, PublishedAt place, DocumentSources sources) {
        at.put("sourceId", sources.written(place.source()));
        souther.compiler.diag.PhysicalPos sits = sources.layouts().resolve(place.at());
        if (sits != null) {
            at.put("line", sits.line());
            at.put("column", sits.column());
        }
        ObjectNode writtenAt = at.putObject("writtenAt");
        place.writtenAt().fields().forEach(writtenAt::put);
    }

    /**
     * Where inside a rule a reader is sent, where that is not the whole of the rule.
     *
     * <p>Its own key and not {@code at}, which in this document is the position a rule is written
     * about. What this says is a place in a source; putting the two under one word would give a
     * consumer one name for a path through a value and a line in a file.
     *
     * <p>Written only for a reason about a part of the rule. A reader lifting the whole of a rule
     * is sent to it by the handle beside this, and a second place saying the same thing would be
     * two answers to one question — free, one day, to disagree.
     */
    private static void sentTo(ObjectNode into, RuleSite sentTo, WhereAPartIs parts,
                               DocumentSources sources) {
        placeInTheRule(sentTo, parts).ifPresent(at -> place(into.putObject("sentTo"), at, sources));
    }

    /**
     * The same for the report a person reads, as the words to put after the sentence.
     *
     * <p>Empty where the rule is the whole of what a reader lifts, and where the part is in a text
     * this compilation cannot point at — which is the state {@link PublishedAt} answers for and not
     * one decided again here.
     */
    private static String sentTo(RuleSite sentTo, WhereAPartIs parts, SourceRendering rendering,
                                 SourceId declaredIn) {
        return placeInTheRule(sentTo, parts)
                .map(at -> ", at " + PlaceProse.said(at, rendering, declaredIn))
                .orElse("");
    }

    /**
     * Where inside the rule a reader is sent, for the two surfaces that write it.
     *
     * <p>The one place the question is asked, so that the two readings short at one part answer it
     * alike. Asked twice, the day one of them treated a splice as a place inside the rule the other
     * would still be sending readers to the rule, and the addresses a consumer joins on would have
     * come apart for a reason nothing in the model says.
     *
     * <p>A place is one somebody can edit. A part whose code is written elsewhere — spliced in from
     * a module this compile holds no file for — points at the call rather than at what the author
     * wrote, and a reader sent there under a word meaning "inside the rule" would be looking for
     * something that is not in front of them and could not edit it if it were. There is nothing
     * inside such a rule to send them to, so what they get is the rule.
     */
    private static Optional<PublishedAt> placeInTheRule(RuleSite sentTo, WhereAPartIs parts) {
        Citation at = switch (sentTo) {
            case RuleSite.TheRuleItself _ -> null;
            case RuleSite.APartOfIt it -> parts.of(it.part());
            case RuleSite.AConstructTheAuthorWrote it -> parts.of(it.origin());
        };
        return at == null || at instanceof Citation.Elsewhere
                ? Optional.empty() : PublishedAt.of(at);
    }

    private static void signature(ObjectNode behavior, String named,
                                  Adequacy.SignatureEvidence signature, DocumentSources sources) {
        if (signature == null) {
            return;
        }
        // A behavior with no signature to read has no section, which is what this document has
        // always said the absence of one means. Written out instead, the section would owe an
        // `inputs` array of the positions — and how many there are is read off the very boundary
        // that was not worked out, so a `>->` composition would publish an empty one and say that
        // it takes nothing. Which behavior it happened to and what it cost is the behavior's
        // `weakening`, where it is one fact rather than one per measure that went without it.
        if (signature.notMeasurable()) {
            return;
        }
        ObjectNode out = behavior.putObject("signature");
        measured(out, signature.counted());
        ObjectNode output = out.putObject("output");
        // What the type declares is the model's and is written whether or not anybody wrote a row.
        // What the rows reached is the measurement's, and where there is none there is nothing to
        // write — four empty arrays and a zero used to say the same as a behavior every case of
        // which went uncovered.
        names(output.putArray("declared"), signature.output().declared());
        measured(output, signature.output().cases(), (node, cases) -> {
            names(node.putArray("specified"), cases.specified());
            names(node.putArray("observed"), cases.observed());
            names(node.putArray("verified"), cases.verified());
            node.put("unclassifiedRows", cases.unclassifiedRows());
        });
        ArrayNode inputs = out.putArray("inputs");
        // Every position, and this section is written only where they were counted. The one state
        // that has none to write is the one that returns above.
        for (InputCaseEvidence input : signature.positions()) {
            ObjectNode in = inputs.addObject();
            names(in.putArray("declared"), input.declared());
            names(in.putArray("excluded"), input.excluded());
            // What a row is owed at here, where this measure's account is the one that holds it: a
            // case of an input of a behavior with no position of its own. Where it has one, the
            // axes carry that entry and this array is empty — one obligation is one entry, and a
            // second array listing it would be the same thing for a consumer to reconcile.
            ArrayNode owed = in.putArray("obligations");
            for (ObligationIdentity each : signature.owned(named, input)) {
                obligationId(owed.addObject().putObject("obligationId"), each, sources);
            }
            measured(in, input.cases(), (node, cases) -> {
                names(node.putArray("specified"), cases.specified());
                names(node.putArray("executed"), cases.executed());
                names(node.putArray("verified"), cases.verified());
                node.put("unclassifiedRows", cases.unclassifiedRows());
            });
        }
    }

    private void partition(ObjectNode behavior, PartitionEvidence partition,
                                  Measure<List<BorderAssessment>> lines,
                                  List<BorderObligationPointAssessment> account,
                                  ClaimAnnotations claimed, DocumentSources sources,
                                  PublishedRuleHandle.WhereARuleIs places, WhereAPartIs parts) {
        // The one decision, the same one the page reads. Written here as well, the two surfaces
        // answered a reader differently about which behaviors have a section at all.
        if (!(PartitionSection.of(partition) instanceof PartitionSection.Present)) {
            return;
        }
        ObjectNode out = behavior.putObject("partition");
        // Each measure's own answer, beside the entries it answered with. Read off the arrays alone,
        // a reader has the same two empties this report used to confuse: a behavior with no
        // positions to divide and one whose positions could not be read both write `[]`, and only
        // this says which. `branch` has carried its own status from the first version; these two are
        // the same measure-level fact in the one place that had nowhere to put it.
        measured(out.putObject("axesMeasure"),
                partition.partitioned());
        measured(out.putObject("boundariesMeasure"), lines);
        ArrayNode axes = out.putArray("axes");
        for (PartitionEvidence.AxisCoverage axis : partition.axes()) {
            ObjectNode a = axes.addObject();
            a.put("axis", axis.at().toString());
            a.put("path", axis.path());
            // How far the rules about this position were read, beside the classes it came to. A
            // class arrived at from part of the rules is a value the model singled out, and a rule
            // that went unread may yet refuse it — so a consumer holding these classes is told what
            // they rest on rather than left to take them for a set every member of which stands.
            // Its own words, not the ones a measure uses. `status` and `reason` say elsewhere in
            // this document whether a number was arrived at and why there is none; this says how
            // far a reading got, which is a different question about a position that has numbers.
            // Under one pair of keys a consumer would read one as the other.
            ObjectNode read = a.putObject("read");
            read.put("extent", readingWord(axis.read()));
            if (axis.read().reach() == PartitionEvidence.AxisCoverage.Reach.SOME_OUT_OF_SIGHT) {
                read.put("rulesNotReached", true);
            }
            axis.classes().forEach(a.putArray("classes")::add);
            ArrayNode excluded = a.putArray("excluded");
            ArrayNode unproven = a.putArray("unprovenClaims");
            for (ClaimAnnotations.Said said : claimed.at(axis.path())) {
                ObjectNode e = (said.settled() ? excluded : unproven).addObject();
                e.put("class", said.classId());
                said.reasons().forEach(e.putArray("reasons")::add);
                if (!said.settled()) {
                    e.put("why", word(said.why()));
                }
            }
            // Only where there is a measurement. A position nothing was measured at used to write
            // an empty `covered` and a zero count, which reads exactly like one where every class
            // went unreached — the `status` beside them said which and nothing made a reader look.
            measured(a, axis.reached(), (node, reached) -> {
                reached.covered().stream().sorted().forEach(node.putArray("covered")::add);
                node.put("unclassifiedRows", reached.unclassifiedRows());
            });
        }
        // The questions the model raised that nothing answered, beside the measures rather than
        // inside one. Every measure here is a reader of them, and a position no axis came back for
        // still has whatever was written about it.
        if (!partition.unanswered().isEmpty()) {
            DocumentArray standing = DocumentPart.UNANSWERED.putArray(out);
            for (PartitionEvidence.Unanswered each : partition.unanswered()) {
                DocumentItem said = standing.addObject();
                ObjectNode one = said.node();
                one.put("at", each.at());
                // The rendered label, which is what this key has always been. What it is rendered
                // from is beside it: a name is a name, and a place is a place, and a consumer that
                // needs to open a file wants the second rather than a string to take apart.
                // The same handle the border prints for a line the comparison drew, through the
                // table of sources this document carries.
                RuleHandleSurface.UNANSWERED_RULE.put(
                        said, PublishedRuleHandle.of(handle(each.cited(), places), places),
                        sources.rendering(), null);
                // What tells one rule from another, beside the words for finding it. A handle is a
                // projection of the rule and not the rule: two arms of one `ensures` clause may
                // name the same case, so the author's words for them are the same words, and two
                // questions came out as one object twice. Within this document and not a name to
                // show a reader — which is what `rule` beside it is.
                ruleId(one.putObject("ruleId"), each.rule());
                one.put("question", questionWord(each.asked()));
                // What the question is about, as the question names it. The number a line falls on
                // is beside the position rather than in place of it, because a rule about the
                // length of a string is a rule at that string: an author looking for where it is
                // written looks at the position, and what the line is on is the other half of the
                // answer.
                ObjectNode about = one.putObject("subject");
                about.put("kind", subjectWord(each.asked()));
                about.put("path", each.at());
                if (each.measure() != null) {
                    about.put("measure", each.measure());
                }
                // Why answering it stopped, in the words this document promises and through the
                // one projection the human line is written from. Written from the same value and
                // not gathered a second way: a document saying less about a question than the
                // report beside it is the two disagreeing about one question, and nothing would
                // have said which of them to believe.
                // Two fields, because the two are two facts and no order runs between them. What
                // the parts of the rule left keeps the order they were written in; what the
                // position's answer was short of names no part of the rule, so it is written on
                // its own rather than given a place among things it is not one of.
                // Each with where inside the rule to go about it, because the word is coarser than
                // what produced it and two things to lift can arrive under one of them: a clause
                // whose ends two choices left open leaves two, and a list of words says one.
                if (!whyStanding(each).isEmpty()) {
                    ArrayNode stopped = one.putArray("stopped");
                    whyStanding(each, parts).written().forEach(stop -> {
                        ObjectNode standsOn = stopped.addObject();
                        standsOn.put("reason", word(stop.reason()));
                        sentTo(standsOn, stop.sentTo(), parts, sources);
                    });
                }
                whatItsPositionWasShortOf(each)
                        .ifPresent(reason -> one.put("answerStopped", word(reason)));
            }
        }
        ArrayNode offAxis = out.putArray("claimsOffAxis");
        for (ClaimAnnotations.Said said : claimed.notAt(measuredPaths(partition))) {
            ObjectNode c = offAxis.addObject();
            c.put("at", said.at());
            c.put("class", said.classId());
            said.reasons().forEach(c.putArray("reasons")::add);
            if (!said.settled()) {
                c.put("why", word(said.why()));
            }
        }
        DocumentArray boundaries = DocumentPart.BOUNDARIES.putArray(out);
        for (BorderAssessment boundary : lines.made().orElseGet(List::of)) {
            DocumentItem drawn = boundaries.addObject();
            ObjectNode b = drawn.node();
            b.put("axis", boundary.axis());
            // The identity, and never left out. This document says what it is about with the
            // ids the caller handed its sources over as, and `sources` explains each one; a
            // display name written here would be a file nothing in the document maps back.
            //
            // No section to leave it out against, either. A person reads a line under a heading
            // that names the module and takes the file from there; a document has no heading, so
            // a place written without its source is a line and a column belonging to nothing —
            // and where a boundary is the only place a report points at, the `sources` table has
            // no other entry to guess from.
            RuleHandleSurface.BOUNDARY_ORIGIN.put(
                    drawn, boundary.describe(places), sources.rendering(), null);
            // What the line is a line at, said rather than left to be inferred from the text beside
            // it. A line between two positions writes the other position where a line at a count
            // writes the count, and the two read alike.
            b.put("kind", word(boundary.shape()));
            b.put("value", boundary.value());
            // The four coverage items, under the border that owes them. Emitted flat, the two the
            // technique keys on the border and the two it keys on the same border were an entry each
            // and nothing said which border they belonged to — a consumer working to a coverage
            // criterion had to group them back by three fields and guess at the rest.
            ArrayNode items = b.putArray("items");
            for (BorderAssessment.Point point : boundary.points()) {
                ObjectNode i = items.addObject();
                i.put("point", word(point.role()));
                // And where the point is, which is what tells one item of this border from another:
                // a border can have two items in one role, and a document naming only the role
                // would carry the same entry twice.
                location(i.putObject("location"), point.at());
                switch (point.item()) {
                    // Why no row is owed, in the one word that says which of the two settled it.
                    // Absent, neither of them reads as anything but the report being short.
                    case ItemAssessment.NotOwed not -> i.put("notOwed", word(not.reason()));
                    case ItemAssessment.Owed owed -> {
                        // What a row here has to do, whole. Two of the four ask for a place and two
                        // ask for a side, so a document carrying a value for all four would name a
                        // witness of a side as though it were the side.
                        i.put("relation", point.border().operator(point.at()));
                        i.put("against", point.border().against(point.at()));
                        // Outside the measurement's gate, because it is not this measurement's
                        // answer. What settles it is its own body of evidence, only one ground of
                        // which the coverage measure reads. So a point nobody measured can carry
                        // `knownWritable: true` beside a status saying so, and the two are
                        // consistent: one says whether a row can be written here and the other
                        // whether anybody looked for one (issue #997).
                        //
                        // The grounds beside the verdict, and the verdict kept. Two of the three are
                        // nowhere else in this document — the attempt is the human surface's and
                        // `--generate`'s — so a reader given only the boolean cannot tell a point
                        // the rules prove inhabited, which stands whatever any search afterwards
                        // makes of it, from one a search happened to reach. The two license
                        // different sentences (issue #1036).
                        ItemAssessment.WritabilityEvidence evidence = owed.writabilityEvidence();
                        i.put("knownWritable", evidence.known());
                        ArrayNode because = i.putArray("writableBecause");
                        for (ItemAssessment.WritabilityEvidence.Ground ground
                                : evidence.grounds().written()) {
                            because.add(wire(ground));
                        }
                        // Inside it, because it is. `false` here is `NoHit` — what the rows this
                        // measurement read came to — and never a measurement that was not made.
                        measured(i, owed.coverage(), (node, coverage) ->
                                node.put("hit", ItemAssessment.Coverage.hit(coverage)));
                    }
                }
            }
        }
        // What this behavior is owed, once per point, with every reading of it. A second layer
        // and not a rewrite of `boundaries`: that array is the lines at coordinates, which is what
        // a border is, and this is the account — a guard on a name every case of a sum spreads is
        // two entries there and one here. A consumer joining a finding to what it is about joins
        // here, on the point, where on the line and the rule; and every reading is published, so
        // nothing a text report left out for room is missing from the document.
        obligations(DocumentPart.OBLIGATIONS.putArray(out), account, null, sources, places);
        ObjectNode pairs = out.putObject("pairs");
        // The size of the space is the model's and is written whether or not anybody counted. The
        // counts are the measurement's and are written only where one was made; `truncated` is gone
        // from here entirely, since a space too large to walk says so under `weakening`.
        pairs.put("total", partition.pairs().total());
        // Which two positions each of them is between, and how many of that relation the rows
        // reach. The sizes are the model's and are written whether or not anybody counted; the
        // counts are the measurement's and are written where one was made.
        ArrayNode between = pairs.putArray("between");
        for (PartitionEvidence.PairSpace.AxisPair pair : partition.pairs().space()) {
            ObjectNode said = between.addObject();
            said.put("one", pair.between().one().toString());
            said.put("other", pair.between().other().toString());
            said.put("total", pair.total());
            if (partition.pairs().counted().made().isPresent()) {
                said.put("covered", partition.pairs().counts().covered(pair.between()));
                said.put("unknown", partition.pairs().unknown(pair));
            }
        }
        // The two numbers over the whole space, and neither of them worked out here. What is left
        // needs the sizes and the counts together, and a writer that subtracted them would be the
        // second mechanism for one fact.
        measured(pairs, partition.pairs().counted(), (node, counts) -> {
            node.put("covered", counts.covered());
            node.put("unknown", partition.pairs().unknown());
        });
        // Both arrays either way. An absent one and an empty one read the same to a person and not
        // to a reader that checks whether the field is there, and this document's shape is what the
        // schema is written against.
        ArrayNode undivided = out.putArray("notDerivable");
        DocumentArray unread = DocumentPart.NOT_READ.putArray(out);
        // Only the positions the model divides no way. The list is what a consumer reads for that
        // claim, and the other two answers are about a reading that stopped and about a rule this
        // measure has no line for — neither of which is the model saying nothing.
        partition.notDerivable().forEach(each -> {
            switch (each.why()) {
                case UndividedPosition.Why.Absent _ -> undivided.add(each.at().toString());
                case UndividedPosition.Why.CannotDerive _,
                     UndividedPosition.Why.StatedWithoutALine _ -> { }
            }
        });
        // The position and what stopped it, kept as the product they are. Which limit a position is
        // waiting on is the thing this list was added to say, and a document that named only the
        // position would leave a consumer to guess it back.
        //
        // Asked of the one reading both surfaces write from. Written from the undivided positions
        // alone, this list was short of every rule left unread at a position the axes went on to
        // measure — which a person reading the report was shown and a consumer keyed on this
        // document was not.
        partition.notRead().forEach(each -> {
            DocumentItem row = unread.addObject();
            ObjectNode said = row.node();
            said.put("position", each.at());
            said.put("reason", word(each.reason()));
            // And which rule, where one was read and could not be used. Absent where the reading
            // never arrived at the rules of the position: there is nothing to name, and a field
            // holding a placeholder would say this compiler had looked at a rule it never saw.
            // Present, the pair is this: `rule` is the handle an author acts on and `ruleId` is
            // what tells one rule from another, and the two are not in step wherever a rule has no
            // name of its own.
            if (each instanceof PartitionEvidence.NotRead.ARule rule) {
                RuleHandleSurface.NOT_READ_RULE.put(
                        row, PublishedRuleHandle.of(handle(rule.cited(), places), places), sources.rendering(), null);
                ruleId(said.putObject("ruleId"), rule.rule());
                // And where inside the rule, for a reason that is about a part of it. The handle
                // above sends a reader to the rule, which is the whole answer while what they lift
                // is the rule; an end a choice left open is lifted at the `||`, and the clause the
                // handle names reads perfectly well. Absent where the rule is the whole of it,
                // rather than repeating the rule's own place under a second key.
                sentTo(said, rule.finding().sentTo(), parts, sources);
            }
            if (each instanceof PartitionEvidence.NotRead.AnUnclassifiedRule rule) {
                RuleHandleSurface.NOT_READ_RULE.put(
                        row, PublishedRuleHandle.of(handle(rule.cited(), places), places), sources.rendering(), null);
                ruleId(said.putObject("ruleId"), rule.rule());
            }
        });
    }

    static void branch(ObjectNode into, BehaviorReport behavior, DocumentSources sources) {
        Adequacy.BranchEvidence branch = behavior.branch();
        if (branch == null) {
            return;
        }
        ObjectNode out = into.putObject("branch");
        measured(out, branch.measured(), (node, arms) -> {
            // One entry per arm the author wrote, and the numbers are not written beside them. A
            // count of arms and a count of covered arms are both a fold of this array, and written
            // out as well they would be two answers about one body — which is what a consumer had
            // before: two numbers, a list of the ones nothing reached that was absent for either of
            // two reasons, and a list of forks, out of which the state of any one arm had to be
            // reassembled.
            ArrayNode all = node.putArray("obligations");
            for (ArmObligation arm : arms.all()) {
                ObjectNode a = all.addObject();
                armId(a.putObject("obligationId"), arm.id(), sources);
                a.put("label", ArmVocabulary.label(arm.display()));
                a.put("kind", word(arm.display().name()));
                // What the arm is an outcome of. Two fields because the meaning is the pair: an
                // `else` an author wrote under an `if` and one written under a `guard` are the
                // same outcome of two constructs, and a consumer told only the outcome cannot
                // tell them apart.
                a.put("construct", word(arm.display().construct()));
                at(a, behavior.placeOf(arm.display()), sources);
                // Where the arm stands, once. What the rows came to and how far the reading got are
                // what the account read to decide it, and an arm's reading is one reading — so a
                // status and a hit beside this would be the same answer in a second encoding, with
                // nothing holding the two in step. That is what a consumer would reimplement, and
                // what this measure stopped doing internally.
                a.put("disposition", wire(arm.disposition()));
                switch (arm) {
                    // And what left it open, where something did. Provenance and not a second
                    // answer: an arm a row that did not come back may have gone through is
                    // undecided, and which reading stopped is what a reader acts on.
                    case ArmObligation.Counted it -> weakening(a, it.coverage().weakening());
                    case ArmObligation.NotCounted it ->
                            a.put("notCountedBecause", wire(it.because()));
                }
            }
            // What the arms above are counted out of, where something has shown that set to be
            // short of an arm. A row through an arm this compiler had proven nothing reaches
            // leaves every arm standing where it stands and the denominator in doubt, so it is
            // said of the account and never of an entry.
            node.put("denominatorSettled", arms.census().settled());
        });
    }

    /**
     * The rules of one body's decision, and which of them a row was seen taking.
     *
     * <p>The account itself and not the findings about it. A finding names the rule it is about by
     * an identity, and a consumer acting on one looks the rule up here — published only as
     * findings, there would be nothing to look up, and a rule some row takes would be absent from
     * this document exactly as a rule this compiler never read is.
     *
     * <p>One entry per rule the body states, whatever the rows did. Which of them a row took is the
     * entry's own answer and is absent where the coverage has no value: a rule nothing was read
     * about is not a rule no row takes, and a consumer handed {@code false} for both could not tell
     * them apart.
     *
     * <p>How far the rules themselves were read is beside them, because it is about the list rather
     * than about any entry. A reading that stopped comes back with some of the body's ways, so the
     * entries here are of those and the ones it did not reach are in no document.
     */
    static void decision(ObjectNode into, BehaviorReport behavior) {
        DecisionEvidence decision = behavior.evidence().decision();
        if (decision == null) {
            return;
        }
        ObjectNode out = into.putObject("decision");
        measured(out.putObject("coverage"), decision.took());
        weakening(out, decision.derivation());
        ArrayNode all = out.putArray("obligations");
        Optional<DecisionEvidence.RowsPlaced> placed = decision.took().made();
        for (DecisionReading.Ruled ruled : decision.read().found()) {
            ObjectNode one = all.addObject();
            ruleId(one.putObject("obligationId"), behavior.name(), ruled.rule());
            placed.ifPresent(rows -> one.put("taken", rows.rules().contains(ruled.rule())));
            // Whether a row is owed here at all, where something asked. Beside `taken` and not
            // folded into it: a rule no row took and nothing could show a row for is not a gap,
            // and a consumer reading `taken` alone would count it as one.
            RuleSettlement came = behavior.ruleSettlements().get(ruled.rule());
            if (came != null) {
                one.put("requirement", wire(came.requirement()));
                // And which answer it was, rule by rule. The page counts these under their reason
                // rather than writing one line apiece, so a consumer that could not tell two
                // unsettled rules apart could not arrive at the page from this document — and a
                // projection nobody can take is the two surfaces agreeing by coincidence.
                String because = because(came.requirement());
                if (because != null) {
                    one.put("because", because);
                }
                // And what the composing managed, which is a different axis and gets a field of
                // its own. A row this compiler could not compose is its shortfall and says nothing
                // about whether the model owes one there, so a consumer reading `requirement` is
                // never handed a generator's failure as the answer to that question.
                if (came.synthesisShortfall() != null) {
                    one.put("synthesisShortfall", word(came.synthesisShortfall().reason()));
                }
            }
        }
    }

    /** How this document says what a search settled about one rule. */
    private static String wire(RuleRequirement settled) {
        return switch (settled) {
            case RuleRequirement.Excluded _ -> "excluded";
            case RuleRequirement.Required _ -> "required";
            case RuleRequirement.Unsettled _ -> "unsettled";
        };
    }

    /**
     * Which answer about the requirement this was, or null where the answer has no reason beside it.
     *
     * <p>One word per way a rule comes to be owed no row or left unsettled, and every one of them
     * about the inquiry or about the model. What the composing fell short on is not here: it is a
     * different question and has a field of its own, and a word from its vocabulary written into
     * this one would be the answer to "is a row owed" carrying the answer to "could one be built".
     *
     * <p>A rule owed a row has none. What settles it is the row that was seen standing in, and
     * there is nothing beside the word for that.
     */
    private static String because(RuleRequirement settled) {
        return switch (settled) {
            case RuleRequirement.Required _ -> null;
            case RuleRequirement.Excluded.OnePositionCannotBeBoth _ ->
                    "the_way_needs_one_position_to_be_two";
            case RuleRequirement.Excluded.AnArmNothingReaches _ -> "an_arm_nothing_reaches";
            case RuleRequirement.Unsettled.AComposedRowWentElsewhere _ ->
                    "a_composed_row_went_elsewhere";
            case RuleRequirement.Unsettled.CouldNotTellWhereTheRowWent _ ->
                    "the_rule_the_row_took_could_not_be_told";
            case RuleRequirement.Unsettled.NothingWatchedTheRow _ -> "nothing_watched_the_row";
            case RuleRequirement.Unsettled.NothingWasComposedToTry _ ->
                    "nothing_was_composed_to_try";
        };
    }

    /**
     * Which of a module's writings wrote a block, said as this document says such things.
     *
     * <p>Beside the number, which is counted within it: two writings' first blocks are one rule
     * without this. The word differs with what wrote it, as it does wherever this document names a
     * rule — and the two a module may write more than one of for a behavior say which source as
     * well, an attached file's rows being counted apart from the model file's.
     */
    private static void writtenBy(ObjectNode into, WrittenOwner owner, DocumentSources sources) {
        switch (owner) {
            case WrittenOwner.Declaration it -> {
                into.put("kind", "declaration");
                into.put("declaredOn", it.declaration().name());
            }
            case WrittenOwner.Stated it -> {
                into.put("kind", "stated");
                into.put("behavior", it.behavior());
            }
            case WrittenOwner.Body it -> {
                into.put("kind", "body");
                into.put("definition", it.definition());
            }
            case WrittenOwner.Examples it -> {
                into.put("kind", "example_rows");
                into.put("behavior", it.behavior());
                into.put("sourceId", sourceOf(it.text(), sources));
            }
            case WrittenOwner.Fake it -> {
                into.put("kind", "stand_in");
                into.put("behavior", it.target());
                into.put("sourceId", sourceOf(it.text(), sources));
            }
        }
    }

    /**
     * The source a text is, as this document names one.
     *
     * <p>Raises for a text this compile has no source for. What is published here is a rule of a
     * module this compile read, and a document about this compile names the sources it read; a text
     * it cannot show is one a reader could not be sent to, and answering with nothing would put two
     * sources' rows for one behavior under one identity.
     */
    private static String sourceOf(QuotedFrom text, DocumentSources sources) {
        if (text instanceof QuotedFrom.ASourceThisCompileHolds(SourceId source)) {
            return sources.written(source);
        }
        throw new IllegalStateException("a rule written in rows this compile has no source for is"
                + " being published: " + text);
    }

    /**
     * What tells one arm of a behavior from every other, which is not what a reader is shown.
     *
     * <p>A key within this document: a consumer joins the entries of one behavior's {@code branch}
     * against another run of the same source on it. What a reader is shown is {@code label} and
     * {@code at}, and neither is an identity — a body spliced in from out of sight has its copies at
     * as many places as there are call sites, and two arms of two forks are both spelled
     * {@code else}.
     *
     * <p>Which rule a fork decides by is part of it. A fork whose declaration decides is one arm
     * however many bodies it was spliced into; one whose caller decides is one per rule a caller
     * handed in, and two of those are the same construct at the same place.
     */
    private static void armId(ObjectNode into, CoverageSites.Obligation arm,
                              DocumentSources sources) {
        into.put("module", arm.writtenIn().module());
        into.put("definition", arm.writtenIn().definition());
        into.put("construct", arm.origin().ordinal());
        into.put("lowered", arm.origin().lowered());
        into.put("part", arm.part());
        into.put("decidedBy", wire(arm.decided()));
        if (arm.decided() instanceof DecidedBy.BySupplied supplied) {
            ArrayNode rules = into.putArray("rules");
            for (SuppliedRules.RuleIdentity rule : supplied.rules()) {
                ObjectNode one = rules.addObject();
                switch (rule) {
                    case SuppliedRules.RuleIdentity.Named it ->
                            one.put("declaration", it.declaration().toString());
                    case SuppliedRules.RuleIdentity.Written it -> {
                        one.put("module", it.rule().module());
                        writtenBy(one.putObject("writtenBy"), it.writtenBy(), sources);
                        one.put("block", it.rule().ordinal());
                    }
                }
            }
        }
    }

    /**
     * Everything the measures found about one behavior, and what a build does about each of it.
     *
     * <p>One array and not a field on each measure. Of the kinds a build refuses over, one was
     * already written here under a name of its own and the other three were left to be worked out
     * from the arrays — a case out of {@code declared} and not out of {@code specified}, a boundary
     * whose {@code hit} is false — so a consumer wanting the ones a build refuses over had to
     * reimplement the classification the compiler had already made. Marking each of those in its own
     * place would be the same field written in five, and a sixth kind would have been written the way
     * the five were.
     *
     * <p>Nothing here is a second reading of what is above it. {@code branch.obligations} and
     * {@code boundaries} keep saying what they say, in their own words and about their own measure;
     * this says which findings there are and what a build does about each, which is neither
     * measure's question and was nobody's.
     *
     * <p>A place is written where the finding has one of its own, which is the arms and nothing else.
     * The rest are cited at the behavior's own declaration, so writing it would repeat one coordinate
     * under every finding of a behavior the entry already names — and where the finding is about a
     * line or a class, that coordinate is not where the reader would go.
     */
    private void findings(ObjectNode behavior, BehaviorReport of, DocumentSources sources) {
        findings(DocumentPart.FINDINGS.putArray(behavior), of.reported(), sources,
                of.rulePlace());
    }

    /**
     * What a set of obligations comes to, written the one way wherever they are published.
     *
     * <p>One writer, because a behavior's account and the declarations' are two projections of one
     * relation and a consumer joins a finding to either by the same key. Written twice, the section
     * edited last would carry a field the other did not, and a reader would have to know which
     * account it was looking at to know what to expect.
     *
     * @param axes what each point's line is on in the words a declaration wrote, for the account
     *             that has such words. Null for a body's own lines, which have none: a reading
     *             names the position it met the line at and no reading can stand for the rest
     */
    private void obligations(DocumentArray out, List<BorderObligationPointAssessment> account,
                             Map<BorderObligationPoint, String> axes,
                             DocumentSources sources,
                             PublishedRuleHandle.WhereARuleIs places) {
        for (BorderObligationPointAssessment point : account) {
            DocumentItem owed = out.addObject();
            ObjectNode o = owed.node();
            // The identity first, because it is what a finding joins on. The words below are what
            // a person reads, and two obligations can share every one of them.
            obligationId(o.putObject("obligationId"), point.point());
            o.put("point", word(point.role()));
            RuleHandleSurface.OBLIGATION_RULE.put(
                    owed, point.handle(places), sources.rendering(), null);
            ruleId(o.putObject("ruleId"), point.id().provenance());
            o.put("relation", point.operator());
            // What the line is on, where the author wrote a word for it. A body's line has none,
            // and the readings below say what each position it was met at has to hold.
            String axis = axes == null ? null : axes.get(point.point());
            // Both or neither, which the schema holds them to. A line between two positions writes
            // its level as a distance from the other one — a reading's name for it and not the
            // declaration's — so there is nothing here in the author's words to put, and a document
            // carrying the rule's own name under `axis` would be answering with the wrong thing
            // (issue #1251).
            String against = axis == null ? null : point.against(axis);
            if (axis != null && against != null) {
                o.put("axis", axis);
                o.put("against", against);
            }
            owedIn(o, point.owed());
            // The readings, each as the position it met the line at and what a row there has to
            // do in that position's terms. In the order the sentences sort and never the walk's;
            // the order is not a contract, and a consumer reads these as a bag: two entries that
            // print alike are two readings, so they may be counted and may not be merged.
            ArrayNode readings = o.putArray("readings");
            for (BorderObligationPointAssessment.ReadingSaid said : point.readingsSaid()) {
                BorderAssessment at = point.met().get(said.where());
                ObjectNode r = readings.addObject();
                r.put("behavior", said.where().behavior());
                r.put("axis", said.at());
                r.put("relation", at.border().operator(point.at()));
                r.put("against", at.border().against(point.at()));
                measured(r, at.owedAt(point.at()).coverage(), (node, coverage) ->
                        node.put("hit", ItemAssessment.Coverage.hit(coverage)));
            }
        }
    }

    /**
     * What each declaration of the module is owed and is short of, under the declaration.
     *
     * <p>The grouping a human report prints, published. A finding carries which declaration it is
     * about, so that half is a rendering of the finding and not a second answer: read off the
     * entries alone, the subject a finding writes is what the line asks of a row, and the
     * declaration it belongs to would be gone.
     */
    private void declarations(ArrayNode out, List<ReportedFinding> written,
                              List<Adequacy.DeclaredDebt> owed, DocumentSources sources,
                              PublishedRuleHandle.WhereARuleIs places) {
        // What the declarations are owed, under the declaration each is owed to, and not only what
        // they are short of. A line a row already stands at has no finding, so a section written
        // from the findings alone publishes the gaps and nothing else — a reader could not tell a
        // module whose declarations owe nothing from one whose every line is answered, and a
        // finding's `obligationId` had nothing in the document to join to. The behavior blocks are
        // written from their account for the same reason; this is the declarations' side of it.
        // Keyed on who owes it, which is a list of declarations and not a word. A line two of the
        // module's declarations took an end in together is owed to both — `Cap or Held` is how a
        // report says that pair and not the name of a declaration — so a section keyed on the
        // words would put one entry under a name nothing declares, and two pairs a report happens
        // to write alike under one. The words are the last thing written and nothing is grouped by
        // them.
        Map<List<TypeSymbol.AtModule>, Entry> byOwners = new LinkedHashMap<>();
        for (Adequacy.DeclaredDebt each : owed) {
            byOwners.computeIfAbsent(each.subject().declarations(), _ -> new Entry())
                    .owed.add(each);
        }
        for (ReportedFinding each : written) {
            List<TypeSymbol.AtModule> owners =
                    each.finding().subject() instanceof FindingSubject.OfADeclaration it
                            ? it.declarations() : List.of();
            byOwners.computeIfAbsent(owners, _ -> new Entry()).found.add(each);
        }
        byOwners.forEach((owners, entry) -> {
            ObjectNode one = out.addObject();
            ArrayNode declared = one.putArray("owners");
            owners.forEach(each -> typeId(declared.addObject(), each));
            one.put("name", entry.named(owners));
            obligations(DocumentPart.OBLIGATIONS.putArray(one),
                    entry.owed.stream().map(Adequacy.DeclaredDebt::debt).toList(),
                    entry.owed.stream().collect(Collectors.toMap(
                            each -> each.debt().point(), Adequacy.DeclaredDebt::axis, (a, _) -> a)),
                    sources, places);
            findings(DocumentPart.FINDINGS.putArray(one), entry.found, sources, places);
        });
    }

    /** What one set of owners owes and is short of, gathered before anything is written. */
    private static final class Entry {
        private final List<Adequacy.DeclaredDebt> owed = new ArrayList<>();
        private final List<ReportedFinding> found = new ArrayList<>();

        /**
         * What a report calls this set, taken from whoever already says it.
         *
         * <p>A presentation of the owners and never the key they were gathered by: two sets a
         * report writes alike are two sets, and the words are what a reader sees rather than what
         * tells them apart.
         */
        String named(List<TypeSymbol.AtModule> owners) {
            if (!owed.isEmpty()) {
                return owed.getFirst().subject().named();
            }
            return found.isEmpty()
                    ? AuthoredLine.naming(owners) : found.getFirst().finding().named();
        }
    }

    /**
     * The same entries, wherever they are published.
     *
     * <p>One writer, because a finding about a declaration is published in the same fields as one
     * about a behavior — what it is about is the subject's answer and not a second shape of entry.
     * Written twice, a consumer joining on the fields would find them agreeing until one of the two
     * was edited.
     */
    private void findings(DocumentArray out, List<ReportedFinding> written,
                          DocumentSources sources,
                          PublishedRuleHandle.WhereARuleIs places) {
        for (ReportedFinding reported : written) {
            Adequacy.Finding finding = reported.finding();
            DocumentItem found = out.addObject();
            ObjectNode f = found.node();
            f.put("kind", word(finding.kind()));
            f.put("disposition", word(finding.disposition()));
            RuleHandleSurface.FINDING_SUBJECT.put(found, subject(finding, places),
                    sources.rendering(), null);
            // Which rule this is about, where the finding is about one. The words in `subject` are
            // how a reader finds it, and two rules an author named alike have the same words — so a
            // consumer joining findings to the questions they came from wants this.
            //
            // Asked of the subject rather than matched against the kinds that have one. Listed
            // here, a kind added and not listed wrote no identity, and one rule's findings came out
            // identical in every field with nothing to join them by.
            if (finding.about() instanceof About.OfARule about) {
                ruleId(f.putObject("ruleId"), about.rule());
            }
            // And which thing a row is owed for, where the finding is about one. The words in
            // `subject` do not tell two of them apart — a rule read at one level in one role owes
            // two points where two cases stop the run in different places, and a fork whose caller
            // supplies the rule owes one arm per rule at one place — so a consumer joining a
            // finding to the account's entry joins on this.
            //
            // Asked of the subject, for the reason the rule above is: the accounts that keep such
            // things are not a list this holds, and one written here would leave a finding about
            // the next of them named by words that tell two of its obligations apart nowhere.
            if (finding.about() instanceof About.OfAnObligation owed) {
                obligationId(f.putObject("obligationId"), owed.obligationIdentity(), sources);
            }
            // And which limit stopped it, where the finding is about one being in the way. Two
            // conjuncts of one clause about one position can stop for two different limits, so
            // written without this a rule's findings there are one object twice — and the entry
            // beside them in `notRead` is keyed on the reason, which leaves nothing to join by.
            if (finding.about() instanceof About.OfSomethingNotRead about) {
                f.put("reason", word(about.finding().reason()));
            }
            // Present where the kind has one. A finding a build is not told about under any code is
            // not one with an empty code, and a consumer joining these to the diagnostics a build
            // printed reads the difference.
            finding.code().ifPresent(code -> f.put("code", code.name()));
            if (hasAPlaceOfItsOwn(finding)) {
                at(f, reported.at(), sources);
            }
        }
    }

    /**
     * Where a finding is, for the kinds whose place is not the declaration they are under.
     *
     * <p>An arm's, and no other's. A behavior with two {@code guard}s writes two arms labelled
     * {@code else}, and the label is what a finding's subject is — so two findings of one behavior
     * came out identical in every field, and which of them a reader was being told about could not be
     * worked out from the document at all. The place is what {@code branch.obligations} already
     * tells them apart by, and it is written here in the same shape, so the two join.
     *
     * <p>The other eight are cited at the declaration the entry sits under. Writing that coordinate
     * would say where the behavior is, under a finding about a line the model draws or a class of an
     * input — neither of which is there.
     *
     * <p>A switch and not a look at the citation. Whether a finding's place is its own is a fact
     * about what it is about, and reading it back off a coordinate would be this report working out
     * something the measure already knew.
     */
    private static boolean hasAPlaceOfItsOwn(Adequacy.Finding finding) {
        return switch (finding.about()) {
            // Both arm findings, for the same reason: what tells two arms of one behavior apart is
            // where they are, and that is as true of an arm whose row is waiting as of one with no
            // row.
            case About.AnArmNoRowGoesThrough _, About.ARowAtAnArmAwaitsItsAnswer _ -> true;
            // And a row's, for the same reason one arm is told from another by where it is: a
            // behavior's rows are as many as somebody wrote, and the one this is about is the one
            // at this place.
            case About.AnUnansweredRow _ -> true;
            // A rule is a way through the whole body and stands at no one place in it. Where its
            // conditions are is said under the finding, one note apiece, so a coordinate here
            // would name whichever of them a walk reached first.
            case About.ARuleNoRowTakes _ -> false;
            case About.ACaseNoRowExpects _, About.ACaseNothingWasSeenToProduce _,
                    About.ACaseNoRowAppliesItTo _, About.AClassNoRowIsIn _,
                    About.APointOfABorder _, About.APointOfADeclaredBorder _,
                    About.APositionNoLineDivides _,
                    About.APositionThisCouldNotRead _, About.ARuleWithoutALine _,
                    About.ARuleNothingClassified _,
                    About.AQuestionNothingAnswered _,
                    About.APositionWhoseRulesWereNotReached _,
                    About.APositionReadWiderThanItsRules _ -> false;
        };
    }

    /**
     * What one finding is about, in the one field every kind writes it in.
     *
     * <p>Spelled here per shape rather than taken from a payload in order. What a finding is about
     * is a value the measure established, and a document handing a consumer that value's fields in
     * the order some sentence took them would publish the shape of the sentence — which changes when
     * the sentence is reworded, and says nothing about which of the fields is the subject.
     *
     * <p>Written to join what is already in the document: a class name and its position are one of
     * an axis's {@code classes} under that axis, an arm's label is one in
     * {@code branch.obligations},
     * and an axis and a value name a {@code boundaries} entry. An input's case carries its position
     * with it, because two parameters of one type give two findings a class name alone cannot tell
     * apart — and a class of a position carries its position for exactly that reason, which this
     * used to say of the inputs and not of the axes.
     *
     * <p>What the field says rather than the words it says, because some of these are about a rule
     * and the handle for one is not this method's to spell. Handed the finished words, this field
     * would be the one place a handle reached a reader without the layer that owns the sentence
     * being asked ({@link RuleHandleSurface}).
     */
    private static PublishedSentence subject(Adequacy.Finding finding,
                                             PublishedRuleHandle.WhereARuleIs places) {
        return switch (finding.about()) {
            // The label and not the arm, and the same label `branch.obligations` writes: this field
            // exists to join to that entry, and a value spelled a second way here would join to
            // nothing.
            case About.AnArmNoRowGoesThrough(var arm) -> words(ArmVocabulary.label(arm));
            // The arm's label again, and the same one. What the document joins on is which arm the
            // finding is about; which of the two things is wrong with it is the finding's kind and
            // not a second way of naming the arm.
            case About.ARowAtAnArmAwaitsItsAnswer(var arm) -> words(ArmVocabulary.label(arm));
            // What the row calls itself, which is what says which row is meant from outside the
            // file. A row that wrote no name answers to nothing outside it and is shown as the
            // place it is written, which the entry carries beside this.
            case About.AnUnansweredRow(var _, var row, var _) -> words(row.shown());
            // The behavior whose decision it is a rule of, and no more. What tells one rule from
            // another is the proposition each condition is keyed on, written the one way round
            // that makes a comparison and its denial one column — so a subject spelling it would
            // show an author a comparison they did not write. Two rules of one behavior are shown
            // alike here and are told apart by `obligationId`, which is what that field is for.
            case About.ARuleNoRowTakes(var behavior, var _) -> words(behavior);
            case About.ACaseNoRowExpects(var missing) -> words(missing.name());
            case About.ACaseNothingWasSeenToProduce(var missing) -> words(missing.name());
            case About.AClassNoRowIsIn(var missing) ->
                    words(missing.name() + " (at " + missing.axis().name() + ")");
            case About.APositionNoLineDivides(var position) -> words(position.at().toString());
            case About.APositionThisCouldNotRead(var it) -> words(it.at());
            case About.ARuleWithoutALine(var it) -> words(it.at());
            // Where a reader is sent to look at the rule, which is what such a finding has instead
            // of a subject: what the rule states there is what nothing worked out.
            case About.ARuleNothingClassified(var it) -> words(it.at());
            // The position and not a number measured of it: which of this position's rules went
            // unread is a fact about the location, and the measures on it are as many as the rules
            // name numbers there.
            case About.APositionWhoseRulesWereNotReached(var gap) -> words(gap.at().toString());
            // The position, as the two above write it. What is said of it is the kind's; there is
            // no rule to name and no class this is about, so the position is the whole subject.
            case About.APositionReadWiderThanItsRules(var it) -> words(it.at().toString());
            // The rule and what it was left saying. Named by the position alone, two rules nothing
            // took in at one position serialised as two identical objects, and the human line named
            // them while a consumer of the document could not tell them apart.
            // Written to join the `unanswered` entry this came out of, so the rule is named by the
            // one method that names it. Spelled a second way here, the join this exists for held
            // for a rule with a name and broke for every rule found by where it is written.
            // The handle, and what tells this rule from another beside it: two arms of one clause
            // may name the same case, so the words alone joined two questions into one row.
            case About.AQuestionNothingAnswered(var asked) -> new PublishedSentence.AroundAHandle(
                    "", PublishedRuleHandle.of(handle(asked.cited(), places), places),
                    " — " + asked(asked.asked()) + " " + subjectOf(asked));
            case About.ACaseNoRowAppliesItTo(var input, var missing, var _) ->
                    words(missing.name() + " (in #" + (input.at() + 1) + ")");
            // The point and the line, and no quantity: a body's line is owed once wherever it is
            // read, so what joins this to a `partition.obligations` entry is the role, where on
            // the line, and the rule — the same three that entry is keyed on.
            case About.APointOfABorder(var point) -> point.said(places);
            // The same sentence, on what the declaration wrote. A line owed once over every reading
            // of it is named by the terms the author used and not by the position some behavior met
            // it at, which is what the debt is (issue #1062).
            case About.APointOfADeclaredBorder(var debt) -> words(debt.said());
        };
    }

    /** What a finding about something other than a rule says, which is words and no handle. */
    private static PublishedSentence words(String said) {
        return new PublishedSentence.Words(said);
    }

    /**
     * What a measure managed, where it managed nothing why, and what it went without.
     *
     * <p>Three fields and not one. {@code status} says whether there is a number; {@code reason}
     * says why there is not, and is absent where there is; {@code weakening} says what left the
     * measurement weaker than it looks, and is absent where nothing did.
     *
     * <p><b>The third is what makes the projection lossless.</b> Four words stand for five states,
     * and {@code unavailable} covers both a measurement nobody asked for and one that was started
     * and could not be finished. A document carrying a weakening beside {@code not measured} is
     * saying the second — which is not a not-measured with more explanation, and is what a reader of
     * this document could not get at all while the difference lived in a boolean on the reason
     * (issue #953).
     */
    private static void measured(ObjectNode of, Measure<?> measure) {
        measured(of, measure, (_, _) -> { });
    }

    /**
     * The same, together with the fields this measure's own value supplies.
     *
     * <p><b>One door.</b> Every field a document writes off what a measure made goes through here,
     * and {@code value} is called only where there is something to call it with. Written the other
     * way — a status here and an {@code ifPresent} beside it at each call site — the rule was a
     * convention, kept at five of the seven measures and forgotten at the other two, which wrote a
     * count and a boolean for measurements nobody had made (issue #997). It is not a compile-time
     * prohibition: {@code Measure.made()} is public, and a writer that wants to reach past this can.
     * What it does is leave one place to look, and make the shape of a correct measure-writer the
     * shape the next one is copied from.
     *
     * <p><b>What belongs inside and what does not.</b> Inside goes what the measure's value
     * supplies, and what qualifies that value — a count, what the count is of, the words that say
     * how to read it. Outside stays what the model says, which is true whether or not anybody
     * measured ({@code pairs.total}, a border's {@code relation} and {@code against}), and what a
     * different body of evidence establishes ({@code knownWritable} and {@code writableBecause},
     * whose grounds are {@link ItemAssessment.WritabilityEvidence.Ground} and only one of which this
     * measurement reads). The question to ask of a field is which evidence supplies it, not which
     * object it sits in.
     */
    private static <T> void measured(ObjectNode of, Measure<T> measure,
                                     BiConsumer<ObjectNode, T> value) {
        ReportMeasurement<T> said = ReportMeasurement.of(measure);
        of.put("status", wire(said.status()));
        if (said.reason() != null) {
            of.put("reason", word(said.reason()));
        }
        weakening(of, said.weakenedBy());
        said.ifMade(it -> value.accept(of, it));
    }

    /**
     * What became of one obligation: the evidence, and where that evidence puts it.
     *
     * <p><b>Both, because they answer different questions.</b> The evidence says what was seen and
     * how far the seeing got; the disposition says how an account treats it. A consumer handed only
     * the evidence works the second out from the first — a status, a hit and a writability read
     * together — which is the derivation this report stopped doing internally, pushed onto whoever
     * reads the document.
     *
     * <p>The coverage is a fold of the readings and not a measurement ({@link ObligationCoverage}),
     * and the document says of it what it says of every other measure. Which word says how far it
     * got is {@link ReportMeasurement}'s, where the other measures ask it.
     */
    private static void owedIn(ObjectNode of, ObligationAssessment owed) {
        ItemAssessment.WritabilityEvidence evidence = owed.writabilityEvidence();
        of.put("knownWritable", evidence.known());
        ArrayNode because = of.putArray("writableBecause");
        for (ItemAssessment.WritabilityEvidence.Ground ground : evidence.grounds().written()) {
            because.add(wire(ground));
        }
        ObligationCoverage coverage = owed.coverage();
        of.put("status", wire(ReportMeasurement.statusOf(coverage)));
        // One word, which is what a boundary item promises. What the readings gave is a set, and
        // asking for it as one is where an account with no room in the document says so.
        coverage.theReasonAsOne().ifPresent(why -> of.put("reason", word(why)));
        weakening(of, coverage.weakening());
        if (coverage.hasAnswer()) {
            of.put("hit", coverage.hasRowWitness());
        }
        of.put("disposition", wire(owed.disposition()));
        if (owed.disposition() instanceof ObligationDisposition.Undecided open) {
            // In the order the questions are said in, which the disposition holds them to. A
            // document ordering them again would be a second answer to which comes first.
            ArrayNode left = of.putArray("undecidedAbout");
            for (ObligationDisposition.Uncertainty question : open.because().written()) {
                left.add(wire(question));
            }
        }
    }

    /**
     * What a measurement went without, where it went without anything.
     *
     * <p>Written under one key at every level that has one, so a consumer reads the same shape of a
     * measure and of a module.
     *
     * <p><b>The kinds, once each.</b> Two rules this compiler could not read are two facts and one
     * word, and the word is all this field carries — which rule, which position, which row is named
     * where the document already names that thing. Written per fact the array would say
     * {@code rule_unread} five times and leave a reader counting repetitions of a word that
     * identifies nothing.
     */
    private static void weakening(ObjectNode of, WeakeningSet weakenedBy) {
        if (weakenedBy.isEmpty()) {
            return;
        }
        List<WeakeningVocabulary> said = new ArrayList<>();
        for (Weakening each : weakenedBy.causes()) {
            said.add(vocabularyOf(each));
        }
        ArrayNode out = of.putArray("weakening");
        // In the order the words are published in, which is the same one whichever readers found
        // which weakenings. What a consumer compares against the last run is this array, so the
        // order it comes out in may not be the order a walk arrived at them.
        for (WeakeningVocabulary each : PublicationOrders.WEAKENING_WORDS.keep(said).written()) {
            out.add(word(each));
        }
    }

    /**
     * The published word for one weakening, whichever vocabulary it is written in.
     *
     * <p>An observation writes the {@code Incompleteness} code's own word rather than a second
     * spelling of it, which is what {@link #wordFor} refuses to answer for. Both surfaces that name
     * a weakening ask this, so a reader meeting one in either place meets the same word.
     */
    static String kindOf(Weakening weakening) {
        return word(vocabularyOf(weakening));
    }

    /**
     * Which vocabulary a weakening writes its word from, and the word inside it.
     *
     * <p>Named as a value rather than answered as a string, because the array these go into is a
     * sequence and a sequence takes an order — and an order is over one kind, which is what
     * {@link WeakeningVocabulary} is the name of. A surface that wants only the word asks
     * {@link #kindOf}, and the two cannot come apart because that one reads this.
     */
    static WeakeningVocabulary vocabularyOf(Weakening weakening) {
        return weakening instanceof Weakening.ObservationIncomplete gap
                ? new WeakeningVocabulary.AnObservationCode(gap.met().fact().code())
                : new WeakeningVocabulary.AWordOfThisDocuments(wordFor(weakening));
    }

    /** What a document calls one of them, whichever vocabulary it came from. */
    private static String word(WeakeningVocabulary said) {
        return switch (said) {
            case WeakeningVocabulary.AnObservationCode it -> word(it.code());
            case WeakeningVocabulary.AWordOfThisDocuments it -> word(it.word());
        };
    }

    /**
     * The facts holding the adequacy verdict open, one entry each, as what a document may say of
     * them.
     *
     * <p><b>Not the array beside it, and the difference is the unit.</b> A {@code weakening} is
     * about one measure or one module, its unit is the published kind, and it says each kind once —
     * which is right, because which rule or which position it was is named where the document
     * already names that thing. This is about the verdict, its unit is a fact, and it says each
     * fact once.
     *
     * <p><b>So nothing here is folded.</b> Two rules this compiler could not read are two entries
     * even where the two objects are equal, because the multiplicity is the fact's. Folded on the
     * pair written out, two facts one document calls the same thing would come back as one, which
     * is the collapse this whole field exists to have avoided — and the entries would then count
     * kinds, which the other array already does.
     *
     * <p>The one fold there is happens before this: {@link WeakeningSet#union} keeps one of two
     * equal facts, so a rule found from three behaviors is one thing to tell a person. That is a
     * fold on what the facts are and not on what they are printed as.
     *
     * <p>Written whether or not the verdict is open, and empty where it is not. What it holds is
     * what keeps the status undetermined, so a satisfied model has none and a refused one has none
     * either — a build refused over a gap has a verdict, whatever else went unmeasured.
     */
    private void keptOpenBy(ObjectNode root, DocumentSources sources) {
        ArrayNode out = root.putArray("keptOpenBy");
        List<PublishedOpening> said = new ArrayList<>();
        // Once for the fold below, for the reason the page's own line gives.
        PublishedRuleHandle.WhereARuleIs places = rulePlaces();
        for (AdequacyOpening each : whatKeepsTheVerdictOpen()) {
            // The reason beside it, where the kind is one that has one. A measure nobody made says
            // what it was waiting for, and that word is one this document already writes wherever a
            // measure has no number — so a reader meets one vocabulary and not two.
            Optional<NotMeasuredWord> why = each instanceof AdequacyOpening.NotMeasured it
                    ? Optional.of(NotMeasuredWord.of(it.why())) : Optional.empty();
            said.add(new PublishedOpening(kindOf(each), why, each.runSensitivity(),
                    publishedSubject(each.subject(), sources, places)));
        }
        for (PublishedOpening each : PublicationOrders.WHAT_HOLDS_A_VERDICT_OPEN
                .arrange(said).written()) {
            ObjectNode fact = out.addObject();
            fact.put("kind", kindWord(each.kind()));
            // What it is about, under a word of its own rather than under `subject`. This document
            // already calls two other things that: what a standing question asks about, and what an
            // incompleteness is attributed to. Neither is this, and neither is shaped like it.
            about(fact.putObject("about"), each.about(), sources);
            each.reason().ifPresent(reason -> fact.put("reason", word(reason)));
            fact.put("runSensitivity", word(each.runSensitivity()));
        }
    }

    /**
     * One subject, written out.
     *
     * <p>A {@code switch} with no {@code default}, for the reason the projection above has none.
     * What each arm writes is what that arm holds and no more: a shape with every field of every
     * kind would leave a consumer reading which of them are filled in to find out what it has.
     */
    static void about(ObjectNode into, PublishedSubject about, DocumentSources sources) {
        into.put("kind", word(about.kind()));
        switch (about) {
            case PublishedSubject.OfAModule it -> into.put("module", it.module());
            case PublishedSubject.OfABehavior it -> into.put("behavior", it.behavior());
            // Named as it is written, which is what puts it in the table of sources this document
            // owes an explanation of. Named while the entries were being arranged, the table would
            // follow the order they were projected in.
            case PublishedSubject.OfASource it ->
                    into.put("source", sources.written(it.source()));
            case PublishedSubject.OfARow it -> {
                into.put("behavior", it.behavior());
                into.put("source", it.source());
                if (it.name() == null) {
                    into.put("ordinal", it.ordinal());
                } else {
                    into.put("name", it.name());
                }
            }
            case PublishedSubject.AtASpelledPosition it -> {
                into.put("behavior", it.behavior());
                into.put("path", it.path());
            }
            case PublishedSubject.AtAPosition it -> {
                into.put("behavior", it.behavior());
                into.put("path", it.path());
                into.put("positionId", it.positionId());
            }
            case PublishedSubject.AtAnInput it -> {
                into.put("behavior", it.behavior());
                // `input` and not `at`: what a rule was read at is a position and what this names
                // is which of the behavior's inputs, and one key cannot be both a string and a
                // number in one object.
                into.put("input", it.at());
            }
            case PublishedSubject.AtARule it -> {
                into.put("at", it.at());
                into.set("ruleId", it.ruleId());
                // Through the surface, and told which object of the schema this is. Everything
                // that writes a handle says where it is writing it; a way in that worked the place
                // out for the caller would be the surface writing into itself, which is what this
                // document has one for.
                RuleHandleSurface.OPENING_RULE.put(
                        DocumentItem.at(into, "/$defs/subject/oneOf/8"), it.rule(),
                        sources.rendering(), null);
                if (!it.stopped().isEmpty()) {
                    ArrayNode stopped = into.putArray("stopped");
                    it.stopped().forEach(stopped::add);
                }
            }
            case PublishedSubject.AtABorder it -> {
                into.put("label", it.label());
                into.set("line", it.line());
            }
            case PublishedSubject.AtAPoint it -> into.set("obligationId", it.obligationId());
            case PublishedSubject.AtAFork it -> {
                into.put("module", it.module());
                into.set("writtenBy", it.writtenBy());
                into.put("construct", it.construct());
                into.put("lowered", it.lowered());
                into.put("said", it.said());
            }
            case PublishedSubject.AtAnArm it -> into.set("armId", it.armId());
            case PublishedSubject.OfAMeasure it -> {
                into.put("module", it.module());
                into.put("behavior", it.behavior());
                into.put("measure", word(it.measure()));
            }
            case PublishedSubject.OfAnAxisMeasure it -> {
                into.put("module", it.module());
                into.put("behavior", it.behavior());
                into.put("axis", it.axis());
            }
        }
    }

    /**
     * One subject, as a person is shown it.
     *
     * <p>Every phrase is the owner's. A line says what a report calls it, a rule says the position
     * it was read at, a source is named by whoever handed it over — none of it is worded here,
     * because a second wording is a second answer to what a thing is called and a reader meeting
     * both has no way to say which one moved.
     *
     * <p>A {@code switch} with no {@code default}, so a subject added later is a compile error
     * rather than an entry printed with nothing said about it.
     */
    private static String said(Subject subject, SourceRendering rendering,
                               PublishedRuleHandle.WhereARuleIs places) {
        return switch (subject) {
            case Subject.OfAModule it -> "module " + it.module();
            case Subject.OfABehavior it -> it.behavior();
            case Subject.OfASource it -> "source " + rendering.names().nameOf(it.source());
            case Subject.OfARow it -> switch (it.rowRef().identity()) {
                case RowIdentity.Named named -> "row `" + named.name() + "` of " + it.rowRef().behavior();
                case RowIdentity.Unnamed unnamed ->
                        "row " + unnamed.shown() + " of " + it.rowRef().behavior();
            };
            case Subject.AtASpelledPosition it -> it.behavior() + "/" + it.path();
            case Subject.AtAPosition it -> it.behavior() + "/" + it.at();
            case Subject.AtAnInput it -> "input " + it.at() + " of " + it.behavior();
            // The position and what stopped the reading there. A line telling a reader to read
            // the rule and what stopped it, and naming neither, sends them to another array for
            // both.
            case Subject.AtARule it -> {
                PartitionEvidence.Unanswered asked = new PartitionEvidence.Unanswered(it.question());
                yield RuleHandleProse.said(PublishedRuleHandle.of(handle(asked.cited(), places), places), rendering,
                                null)
                        + " at " + asked.at() + " (" + ReportedReason.words(whyStanding(asked))
                        .stream()
                        .map(AdequacyReport::whyUnread).collect(Collectors.joining("; ")) + ")";
            }
            case Subject.AtABorder it -> it.border().label();
            // Composed here and not by what writes a point beside a line, which takes the account
            // of the point: which of the four roles it is and which side it is on are the line's
            // answers about it and need the readings of the line to give them. A point on its own
            // carries where it stands, and that is what is said.
            //
            // The line as a report calls it, the level it falls at, and where on it the point is.
            // All three: what a report calls a line leaves out where it stands, on purpose, and two
            // points of one behavior's comparisons are told apart by nothing else — which is the
            // shape this whole array was written to stop repeating.
            case Subject.AtAPoint it -> it.point().line().saidWithoutAPlace()
                    + " = " + it.point().line().at() + placeOn(it.point().point());
            case Subject.AtAFork it -> "a fork of " + it.fork().module();
            case Subject.AtAnArm it -> "an arm of " + it.arm().writtenIn().definition();
            case Subject.OfAMeasure it ->
                    word(it.measure()) + " of " + it.behavior();
            case Subject.OfAnAxisMeasure it -> it.at().term() + " of " + it.behavior();
        };
    }

    /**
     * Where on a line a point stands, in the words this document writes for it.
     *
     * <p>The same three the location of an obligation is written under, spelled once and read from
     * there: two wordings of one place are two things to keep in step, and which of them a reader
     * had met would decide whether the two lines they were reading were about the same point.
     *
     * <p>Which of the four roles it is, is not this. That is the line's answer about the point and
     * needs the readings of the line to give it, which a point on its own does not carry.
     */
    private static String placeOn(DomainPoint point) {
        ObjectNode said = JSON.createObjectNode();
        location(said, point);
        return " (" + said.get("kind").asString().replace('_', ' ')
                + (said.has("side") ? ", " + said.get("side").asString() : "") + ")";
    }

    /** What a reader does next with one thing holding the verdict open. */
    private static String next(ReaderDisposition disposition) {
        return switch (disposition) {
            case ReaderDisposition.WidenTheRun _ -> "run this again allowing more";
            case ReaderDisposition.LookAtTheRule _ -> "read the rule and what stopped it";
            // Without the word for it. What the document calls a weakening is for a consumer keyed
            // on this report; a person reading a line is owed a sentence, and the words a build
            // matches on do not reach one (ASourceThatProducedNoObservationSaysSo).
            case ReaderDisposition.LookAtWhatTheMeasureWentWithout _ ->
                    "read what this measure went without";
            case ReaderDisposition.LookAtWhyNothingWasMeasured it ->
                    ReasonProse.of(it.why()).clause();
            case ReaderDisposition.LookAtWhatShowedNoRow _ ->
                    "read what was tried to show a row can be written there";
            case ReaderDisposition.LookAtTheFork _ -> "read the fork nothing told apart";
            case ReaderDisposition.LookAtThisCompilersProof _ ->
                    "read this compiler's proof, which is what does not hold";
            case ReaderDisposition.ReconsiderWhatThisBehaviorNeedsToDistinguish _ ->
                    "weigh what this behavior needs to tell apart";
            case ReaderDisposition.Settled _ -> "nothing";
        };
    }

    /**
     * What one subject is, as the document writes it.
     *
     * <p>A {@code switch} with no {@code default}, so a subject added later is a compile error here
     * rather than an entry a document quietly writes nothing about.
     *
     * <p>Every nested identity is spelled by whoever already spells it. A point is written as the
     * obligations are, an arm as the branch account is, a fork by what wrote it: none of them is
     * spelled a second way here, because a second spelling is a second thing to keep true and the
     * one a reader joins on would be whichever they happened to read.
     */
    private static PublishedSubject publishedSubject(Subject subject, DocumentSources sources,
                                                     PublishedRuleHandle.WhereARuleIs places) {
        return switch (subject) {
            case Subject.OfAModule it -> new PublishedSubject.OfAModule(it.module());
            case Subject.OfABehavior it -> new PublishedSubject.OfABehavior(it.behavior());
            case Subject.OfASource it -> new PublishedSubject.OfASource(it.source());
            // Named where the row has a name, and which of its behavior's rows in that source
            // where it has none: a row without a name says of itself that nothing outside this
            // compiler can address it by a number.
            case Subject.OfARow it -> switch (it.rowRef().identity()) {
                case RowIdentity.Named named -> new PublishedSubject.OfARow(it.rowRef().behavior(),
                        sources.written(it.rowRef().source()), named.name(), null);
                case RowIdentity.Unnamed unnamed -> new PublishedSubject.OfARow(
                        it.rowRef().behavior(), sources.written(it.rowRef().source()), null,
                        unnamed.ordinal());
            };
            case Subject.AtASpelledPosition it ->
                    new PublishedSubject.AtASpelledPosition(it.behavior(), it.path());
            // Both spellings. The first is what an author recognises and the second is what tells
            // apart two positions that one spells alike.
            case Subject.AtAPosition it -> new PublishedSubject.AtAPosition(it.behavior(),
                    it.at().toString(), it.at().at().discriminated());
            case Subject.AtAnInput it ->
                    new PublishedSubject.AtAnInput(it.behavior(), it.at());
            case Subject.AtARule it -> {
                PartitionEvidence.Unanswered asked = new PartitionEvidence.Unanswered(it.question());
                ObjectNode id = JSON.createObjectNode();
                ruleId(id, asked.rule());
                // And what stopped the reading, in the words the questions themselves are written
                // under. What a reader does with this entry is read the rule and what stopped it,
                // and an entry naming neither is one that says what to do and hands over none of
                // the material to do it with.
                yield new PublishedSubject.AtARule(asked.at(), id,
                        PublishedRuleHandle.of(handle(asked.cited(), places), places),
                        ReportedReason.words(whyStanding(asked)).stream()
                                .map(AdequacyReport::word).toList());
            }
            case Subject.AtABorder it -> {
                // The line the rules drew, as this document identifies one. Named by the rule
                // alone, two lines of one clause — the ends of what an invariant admits — come out
                // under one identity, and the arrangement is left to write two entries it cannot
                // tell apart.
                ObjectNode id = JSON.createObjectNode();
                authoredLineId(id, it.border().origin().authoredLine());
                yield new PublishedSubject.AtABorder(it.border().label(), id);
            }
            case Subject.AtAPoint it -> {
                ObjectNode id = JSON.createObjectNode();
                obligationId(id, it.point());
                yield new PublishedSubject.AtAPoint(id);
            }
            case Subject.AtAFork it -> {
                ObjectNode wrote = JSON.createObjectNode();
                writtenBy(wrote, it.fork().owner(), sources);
                yield new PublishedSubject.AtAFork(it.fork().module(), wrote,
                        it.fork().ordinal(), it.fork().lowered(), word(it.fork().kind()));
            }
            case Subject.AtAnArm it -> {
                ObjectNode id = JSON.createObjectNode();
                armId(id, it.arm(), sources);
                yield new PublishedSubject.AtAnArm(id);
            }
            case Subject.OfAMeasure it -> new PublishedSubject.OfAMeasure(it.module(),
                    it.behavior(), it.measure());
            case Subject.OfAnAxisMeasure it -> new PublishedSubject.OfAnAxisMeasure(it.module(),
                    it.behavior(), it.at().toString());
        };
    }

    /**
     * The published word for one thing keeping the verdict open.
     *
     * <p>Here rather than inside the writer, so that the words are a vocabulary and not a set of
     * literals spelled where they happen to be printed. What is written and what the schema allows
     * were kept in step by hand until this — the check that holds every other enumerated field of
     * the document against its enum had nothing to be pointed at.
     *
     * <p>A {@code switch} with no {@code default}, so an opening added later has to be given a
     * word; the word has to be one of {@link AdequacyOpeningWord}, which the schema is held
     * against. An opening that is a measure going without something writes that weakening's own
     * word instead, for the reason {@link AdequacyOpeningWord} gives.
     */
    static PublishedOpening.Kind kindOf(AdequacyOpening opening) {
        return switch (opening) {
            case AdequacyOpening.ByWeakening it ->
                    new PublishedOpening.Kind.AWeakening(vocabularyOf(it.cause()));
            case AdequacyOpening.NotMeasured _ ->
                    new PublishedOpening.Kind.AnOpening(AdequacyOpeningWord.NOT_MEASURED);
            // Two words for the two gaps, rather than one word and a reason beside it. Which of
            // them it was is what a reader acts on and what the sensitivity is read from, so it is
            // the kind: a value read for the point that did not come back, and a composing this
            // compiler declined to do, are not one kind of news.
            case AdequacyOpening.ShowingStopped it ->
                    new PublishedOpening.Kind.AnOpening(switch (it.by()) {
                        case EstablishmentGap.Observation _ ->
                                AdequacyOpeningWord.SHOWING_STOPPED;
                        case EstablishmentGap.Composition _ ->
                                AdequacyOpeningWord.NOTHING_WAS_COMPOSED;
                    });
            case AdequacyOpening.NothingShowedARowCanBeWritten _ ->
                    new PublishedOpening.Kind.AnOpening(AdequacyOpeningWord.NOTHING_SHOWED_IT);
        };
    }

    /**
     * The one handle this document sends a reader to a rule by, out of everywhere it was offered.
     *
     * <p>Chosen where the choosing is decided and not here. A rule found by two readers has a
     * handle from each, the schema has room for one, and which one is a decision about what a
     * reader is shown rather than about which reader a walk reached first.
     */
    private static RuleCitation handle(Set<RuleCitation> offered,
                                       PublishedRuleHandle.WhereARuleIs places) {
        return PublicationOrders.handleFor(offered, places)
                .orElseThrow(() -> new IllegalStateException(
                        "a rule a reader is sent to is one some reader said how to find"));
    }

    /** What a document calls one of them, whichever of the two vocabularies it comes from. */
    private static String kindWord(PublishedOpening.Kind kind) {
        return switch (kind) {
            case PublishedOpening.Kind.AWeakening it -> word(it.said());
            case PublishedOpening.Kind.AnOpening it -> word(it.said());
        };
    }

    /**
     * One weakening, as a word a consumer can count and match against the next run.
     *
     * <p>A {@code switch} with no {@code default}: an arm added is a compile error here rather than
     * a fact that quietly stops being said. What each arm is about is written where the document
     * already names that thing — a rule, a position, a row — so this says which kind it is and does
     * not restate the subject.
     */
    public static WeakeningWord wordFor(Weakening weakening) {
        return switch (weakening) {
            // Its own vocabulary, which is the observation codes. Asking this to spell it a second
            // way would be a second set of words for one fact.
            case Weakening.ObservationIncomplete _ ->
                    throw new IllegalArgumentException("an observation writes its own code");
            case Weakening.OutputCasesUnreadable _ -> WeakeningWord.OUTPUT_CASES_UNREADABLE;
            case Weakening.InputCasesUnreadable _ -> WeakeningWord.INPUT_CASES_UNREADABLE;
            // Read through to what the reading met. Both of these leave a point undecided and a
            // reader acts on them differently, so a projection that answered one word for the pair
            // would take the difference back out one step after the reading carried it here.
            case Weakening.BorderValueUnreadable it -> switch (it.why()) {
                case ReadingGap.Observation _ -> WeakeningWord.BORDER_VALUE_UNREADABLE;
                case ReadingGap.NoValue _ -> WeakeningWord.BORDER_VALUE_ABSENT;
                // One word, and the reason underneath keeps which of the two it was. A position
                // that was read and holds nothing is news about the row; neither of these is, and a
                // reader weighing the document acts on both the same way.
                case ReadingGap.CouldNotWalk _, ReadingGap.CouldNotReadRow _ ->
                        WeakeningWord.BORDER_OBSERVATION_UNAVAILABLE;
            };
            case Weakening.ModelReadingIncomplete it -> switch (it.cause()) {
                case ClosureGap.PositionNotReachedInto _ ->
                        WeakeningWord.POSITION_NOT_READ;
                // Two words for the one gap, because a reader acts on them differently: a question
                // about a subject wants an answer to that question, and a rule nothing classified
                // wants this compiler to read further. Which of the two it is, is what the question
                // is, asked of it here rather than read off which list it arrived in.
                case ClosureGap.QuestionUnanswered one -> switch (one.question()) {
                    case StandingQuestion.Exact _ ->
                            WeakeningWord.QUESTION_UNANSWERED;
                    // Both are this compiler not having read far enough, which is the one thing
                    // this word says and the one thing a build held to it acts on. Which of them
                    // it is, is what the question beside it says.
                    case StandingQuestion.BoundaryUndetermined _,
                         StandingQuestion.NothingClassifiesIt _ ->
                            WeakeningWord.RULE_UNREAD;
                };
                case ClosureGap.RulesNotReached _ ->
                        WeakeningWord.RULES_NOT_REACHED;
                // The same word as a rule nothing classified, and the same news: this compiler did
                // not read far enough. Which clause of the rule it stopped in is what the finding
                // at the position says, and is not what this word promises.
                case ClosureGap.LineNotDerived _ -> WeakeningWord.RULE_UNREAD;
            };
            case Weakening.BodiesNotElaborated _ -> WeakeningWord.BODIES_NOT_ELABORATED;
            case Weakening.BoundaryNotDerived _ -> WeakeningWord.BEHAVIOR_BOUNDARY_NOT_DERIVED;
            case Weakening.InputNotRead _ -> WeakeningWord.BEHAVIOR_INPUT_NOT_READ;
            case Weakening.PairSpaceTruncated _ -> WeakeningWord.PAIR_SPACE_TRUNCATED;
            case Weakening.ProofContradicted _ -> WeakeningWord.PROOF_CONTRADICTED;
            case Weakening.ArmsUnsettled _ -> WeakeningWord.ARMS_UNSETTLED;
            // One word for the three shortfalls the reading can meet. A consumer acts on all of
            // them the same way — a rule nothing was seen taking may be where an unplaced row went
            // — and which of them it was is the reason the fact carries.
            case Weakening.DecisionOfRowUnreadable _ -> WeakeningWord.DECISION_OF_ROW_UNREADABLE;
            case Weakening.DecisionRunNotWatched _ -> WeakeningWord.DECISION_RUN_NOT_WATCHED;
            // What stopped the reading is the figure beside it. One word, because what a consumer
            // acts on is that the rules are not known — which figure it was is this compiler's
            // policy and travels as the reason.
            case Weakening.DecisionReadingIncomplete _ -> WeakeningWord.DECISION_NOT_FULLY_READ;
        };
    }

    /**
     * What a document calls a status, which is not what the compiler calls it.
     *
     * <p>The schema's word for a measure with no number is {@code unavailable}, and which of the two
     * kinds it is, is what the {@code reason} beside it says (spec §example-report-vocabulary). Written
     * out here rather than taken off the enum's own name, so that naming a state inside the compiler
     * is never a change to what a document says. The last time these two were the same string, a
     * field of the schema was whatever the enum happened to be called that week.
     */
    public static String wire(MeasurementStatus status) {
        return switch (status) {
            case COMPLETE -> "complete";
            case PARTIAL -> "partial";
            case NOT_APPLICABLE, NOT_MEASURED -> "unavailable";
        };
    }

    /** What a document calls a ground a row can be written at a point on. Written out for the same
     *  reason as the status above: widening what a consumer must handle is a decision about the
     *  contract, and renaming a constant is a decision about the compiler. Taken off the name, the
     *  second would silently be the first. */
    public static String wire(ItemAssessment.WritabilityEvidence.Ground ground) {
        return switch (ground) {
            case THE_RULES_PROVE_IT -> "the_rules_prove_it";
            case A_ROW_IS_AT_IT -> "a_row_is_at_it";
            case A_VALUE_WAS_BUILT -> "a_value_was_built";
        };
    }

    /**
     * How an account treats one obligation, in the document's word for it.
     *
     * <p>Exhaustive, so a state added to {@link ObligationDisposition} arrives here as a compile
     * error rather than as an obligation the document has no word for.
     */
    public static String wire(ObligationDisposition disposition) {
        return switch (disposition) {
            case ObligationDisposition.Met _ -> "met";
            case ObligationDisposition.Unmet _ -> "unmet";
            case ObligationDisposition.Undecided _ -> "undecided";
        };
    }

    /**
     * Which question about an obligation nothing answered, likewise exhaustive.
     *
     * <p>The question and not what it is open on. What left it open is said in the sentence under
     * the point, where a reader can act on it; a document keys on which question stands, and the
     * two halves of one question are one word here because a consumer asking "is whether a row is
     * there open" is asking one thing.
     */
    public static String wire(ObligationDisposition.Uncertainty question) {
        return switch (question) {
            case ObligationDisposition.Uncertainty.WhetherARowIsThere _ ->
                    "whether_a_row_is_at_it";
            case ObligationDisposition.Uncertainty.WhetherARowCanBeWritten _ ->
                    "whether_a_row_can_be_written";
        };
    }

    /**
     * How the arm account treats one arm, in the document's word for it.
     *
     * <p>Its own words beside {@link #wire(ObligationDisposition)} although three of the four are
     * spelled the same. The two accounts answer for different things and are told apart by what
     * takes something out of the count, so a word shared between them would be one string standing
     * for two propositions the day either account grows a state.
     */
    public static String wire(ArmDisposition disposition) {
        return switch (disposition) {
            case MET -> "met";
            case UNMET -> "unmet";
            case UNDECIDED -> "undecided";
            case NOT_COUNTED -> "not_counted";
        };
    }

    /** Why the arm account leaves an arm out, likewise exhaustive. */
    public static String wire(ArmExclusion because) {
        return switch (because) {
            case ArmExclusion.OccurrencesNotToldApart _ -> "occurrences_not_told_apart";
        };
    }

    /** What settles which rule one occurrence of a fork decides by, as a word. An arm out of the
     *  count is the third of these, so all three are reachable in a document. */
    public static String wire(DecidedBy decided) {
        return switch (decided) {
            case DecidedBy.ByTheDeclaration _ -> "the_declaration";
            case DecidedBy.BySupplied _ -> "the_caller";
            case DecidedBy.NotSaid _ -> "not_said";
        };
    }

    /** Case names, in the order this compiler shows a set of names in. The sets these come from keep
     * the order the rows happened to arrive in, which is a fact about a run and not about the model,
     * and two reports that put the cases differently cannot be compared. Asked of the names rather
     * than of their spellings, so that two modules declaring one spelling are still told apart
     * somewhere and not left in whichever order they arrived. */
    private static void names(ArrayNode into, Set<TypeSymbol> cases) {
        CanonicalNameOrder.shown(cases).stream().map(TypeSymbol::name).forEach(into::add);
    }
}
