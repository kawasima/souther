package souther.compiler;

import souther.compiler.source.SourceId;
import souther.compiler.types.WrittenOwner;

import org.junit.jupiter.api.Test;

import souther.compiler.diag.Citation;
import souther.compiler.diag.SourcePos;
import souther.compiler.diag.SourceProvenance;
import souther.compiler.observe.Incompleteness;
import souther.compiler.observe.MeasurementStatus;
import souther.compiler.observe.RunSensitivity;
import souther.compiler.coverage.DecidedBy;
import souther.compiler.coverage.SuppliedRules;
import souther.compiler.query.Adequacy;
import souther.compiler.query.NotMeasuredReason;
import souther.compiler.query.ArmDisposition;
import souther.compiler.query.ArmExclusion;
import souther.compiler.query.ItemAssessment;
import souther.compiler.query.ObligationDisposition;
import souther.compiler.query.Compilation;
import souther.compiler.query.EstablishmentGap;
import souther.compiler.query.PartitionEvidence;
import souther.compiler.query.ReadingReasons;
import souther.compiler.query.UnaskedReasons;
import souther.compiler.query.WritabilityKnowledge;
import souther.compiler.partition.ReadingGap;
import souther.compiler.check.BehaviorContract;
import souther.compiler.check.BehaviorImplementation;
import souther.compiler.check.Clause;
import souther.compiler.check.RuleRef;
import souther.compiler.report.AdequacyReport;
import souther.compiler.types.SourceConstruct;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.types.RuleOrigin;
import souther.compiler.types.TypeKey;
import souther.compiler.types.ValueName;

import java.util.Optional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every word the shipped schema allows is one somebody accounted for.
 *
 * <p>Every enumerated field of the shipped schema is a second spelling of a Java enum.
 * The two are edited in different files by different hands, and until this test nothing noticed when
 * one moved: `ROW_TIMED_OUT` became `ROW_UNDECIDED` when a row stopped being held to a clock, the
 * rename was right, and the schema went on promising a word that had not been emitted since.
 *
 * <p>Held as a correspondence rather than by generating one side from the other. A published contract
 * generated from an internal enum would change whenever the internal enum did, which is the opposite
 * mistake: renaming a constant is a decision about the compiler, and widening what a consumer must
 * handle is a decision about the contract. This makes the second decision impossible to take by
 * accident, and leaves both deliberate.
 *
 * <p>Which is why a word may outlive the constant it spelled. A version says what its documents may
 * carry, and a report written before a rename carries the old word for as long as anyone keeps it. So
 * the schema is what the compiler writes now together with what it wrote then, and the second half is
 * named here — a word that quietly stopped being emitted still fails, and one kept on purpose is one
 * somebody wrote down.
 *
 * <p>The correspondence was an equality until a code was renamed after the field it sits in had
 * reached a published report. Before that every word taken out had never been emitted, so there was
 * nothing for a document to be holding, and the schema was corrected rather than versioned.
 */
class EverySchemaWordIsAccountedForTest {

    private static final String SCHEMA = AdequacyReport.SCHEMA_RESOURCE;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /**
     * One enumerated field, and the enum whose constants it spells.
     *
     * @param at the field, as the keys leading to it from the root of the schema
     */
    private record Vocabulary(String label, List<String> at, List<Class<?>> source,
                              Set<String> written, Set<String> retired) {

        /**
         * One field's words, from the reasons that spell them.
         *
         * <p>More than one class per field since #953. Which kind of no-number a reason is, is its
         * type, so one measure's reasons live in two or three enums — and a field carries the words
         * of all of them, because a document does not say which Java type a word came from.
         */
        Vocabulary(String label, List<String> at, Class<?>... source) {
            this(label, at, List.of(source), null, Set.of());
        }

        Vocabulary(String label, List<String> at, Set<String> retired, Class<?>... source) {
            this(label, at, List.of(source), null, retired);
        }

        /**
         * One field whose words are every reason a measure can give, asked of the measure.
         *
         * <p>Named by its owner rather than by its reasons. A measure's reasons are enums nested in
         * it — which kinds of no-number it has is its own business — so listing them here is a copy
         * of that list, and a reason type added to the measure is one this never hears about. It
         * happened: {@code BODIES_NOT_ELABORATED} was added to the arm measure and the schema went
         * on promising the five words it had, so the compiler could write a document the shipped
         * schema refused, and the check written to catch exactly that saw nothing (issue #996).
         *
         * @param owner  the measure, whose nested reason enums are its vocabulary
         * @param shared reason types it uses that are not its own — a word more than one measure
         *               gives lives beside them rather than inside any of them
         */
        static Vocabulary of(String label, List<String> at, Class<?> owner, Class<?>... shared) {
            return of(label, at, Set.of(), owner, shared);
        }

        /** The same, for a field that also carries a word no measure writes any more. */
        static Vocabulary of(String label, List<String> at, Set<String> retired, Class<?> owner,
                             Class<?>... shared) {
            List<Class<?>> source = new ArrayList<>(reasonsOf(owner));
            source.addAll(List.of(shared));
            return new Vocabulary(label, at, source, null, retired);
        }

        /** A field whose words are not an enum's own names. {@link #source} is what they are
         * projected from, named so a reader knows where to look, and not what they are spelled by. */
        Vocabulary(String label, List<String> at, Set<String> written) {
            this(label, at, List.of(MeasurementStatus.class), written, Set.of());
        }

        /** Every word a document of this version may carry: what is written now, and what was. */
        Set<String> allowed() {
            Set<String> out = new LinkedHashSet<>();
            if (written == null) {
                source.forEach(each -> out.addAll(wordsOf(each)));
            } else {
                out.addAll(written);
            }
            out.addAll(retired);
            return out;
        }
    }

    /**
     * Every reason enum nested in {@code owner}, which is what its reasons are.
     *
     * <p>Asked of the type rather than listed. Which kinds of no-number a measure has is the
     * measure's own business, and a list of them written anywhere else is a second copy that the
     * next one added is missing from.
     */
    private static List<Class<?>> reasonsOf(Class<?> owner) {
        List<Class<?>> out = new ArrayList<>();
        for (Class<?> each : owner.getDeclaredClasses()) {
            // Not only the enums. A reason that costs a proof to say carries it as an argument and
            // is a record, which spells its word in a constant `wordsOf` reads — so which shape a
            // reason has is not what decides whether the schema is held to it.
            if (souther.compiler.observe.MeasureReason.class.isAssignableFrom(each)) {
                out.add(each);
            }
        }
        assertTrue(!out.isEmpty(), owner.getName() + " gives no reason, so this names the wrong"
                + " type: a measure's reasons are the enums nested in it");
        return out;
    }

    /**
     * The word each thing that may write a block is published as.
     *
     * <p>The set is the owners themselves, so one added to the language leaves this without a word
     * for it and fails here. What is written down is the spelling, which a document carries and a
     * Java name is not.
     */
    private static Set<String> ownerWords() {
        Map<String, String> spelling = new LinkedHashMap<>();
        spelling.put("Declaration", "declaration");
        spelling.put("Stated", "stated");
        spelling.put("Body", "body");
        spelling.put("Examples", "example_rows");
        spelling.put("Fake", "stand_in");
        Set<String> words = new LinkedHashSet<>();
        for (Class<?> owner : armsOf(souther.compiler.types.WrittenOwner.class)) {
            String word = spelling.get(owner.getSimpleName());
            assertNotNull(word, owner.getSimpleName() + " may write a block and this document has"
                    + " no word for it");
            words.add(word);
        }
        return words;
    }

    /**
     * The words a rule's identity gives one of its conditions, one per shape of the reading.
     *
     * <p>Spelled here and held against the arms, the way the owners above are: which words a
     * consumer joining on a rule must handle is a decision about the contract, and a shape renamed
     * inside the compiler is not one. What this keeps out is a shape added to the reading and
     * written into the document under no word at all.
     */
    private static Set<String> conditionWords() {
        Map<String, String> spelling = new LinkedHashMap<>();
        spelling.put("AComparison", "comparison");
        spelling.put("APosition", "position");
        spelling.put("AConditionNotRead", "not_read");
        Set<String> words = new LinkedHashSet<>();
        for (Class<?> shape
                : armsOf(souther.compiler.partition.DecisionCondition.class)) {
            String word = spelling.get(shape.getSimpleName());
            assertNotNull(word, shape.getSimpleName() + " may be a column of a rule and this"
                    + " document has no word for it");
            words.add(word);
        }
        return words;
    }

    /** The names a branch measure can give an arm, spelled by the writer's own encoder. */
    private static Set<String> armWords() {
        return Arrays.stream(souther.compiler.coverage.OutcomeName.values())
                .filter(souther.compiler.coverage.OutcomeName::isArm)
                .map(AdequacyReport::word)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * The constructs an arm can be an outcome of.
     *
     * <p>Fewer than the kinds an origin carries, and the difference is the point: what a source
     * wrote is a wider vocabulary than what this report has a word for. A binary expression is
     * inside a fork rather than being one, an application is not a fork at all — a rule read off
     * one divides a position into classes and has no arm to be an outcome of — a collection is a
     * value and forks nothing, and nothing wrote the last. Each of those has an origin all the
     * same, because an origin says which construct of a source it is and not what a report calls
     * it.
     *
     * <p>Written out rather than read off {@code OutcomeName.of}. That mapping is what decides which
     * pairs exist, and a population taken from it would move whenever it did — which is the one
     * thing this is here to notice.
     */
    private static Set<String> constructWords() {
        return Arrays.stream(SourceConstruct.values())
                .filter(c -> c != SourceConstruct.BINARY
                        && c != SourceConstruct.CALL
                        && c != SourceConstruct.COLLECTION_LITERAL
                        && c != SourceConstruct.NOT_WRITTEN)
                .map(AdequacyReport::word)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * What a citation can say about a place, spelled by the one writer of the field.
     *
     * <p>Both arms built rather than listed. These words are not an enum's — they are what the
     * citation writes — so a list here would be a second copy of them, and the schema would go on
     * agreeing with the copy after the writer had stopped saying it.
     */
    private static Set<String> writtenAtWords() {
        SourcePos here = new SourcePos(1, 1, new SourceId("s"));
        return java.util.stream.Stream
                .of(here, here.standingInFor(new souther.compiler.diag.DeclaringCode(
                        new SourceProvenance.TheStandardLibrary("List.filter"))))
                .map(pos -> Citation.of(pos).writtenAtFields().get("kind"))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    /** What a document may say a status is. Two of the compiler's four states share one of them. */
    private static final Set<String> STATUS_WORDS =
            new LinkedHashSet<>(List.of("complete", "partial", "unavailable"));

    /**
     * Every enumerated field the schema has.
     *
     * <p>Written out rather than discovered. A test that walked the schema for `enum` and looked for
     * something to compare each against would pass over a field it could not place, which is the
     * field most worth knowing about — one nobody has said where the words come from.
     */
    private static final List<Vocabulary> VOCABULARIES = List.of(
            new Vocabulary("adequacy", List.of("properties", "adequacy"),
                    AdequacyReport.AdequacyStatus.class),
            // What one thing keeping that verdict open says about a wider run. Its own enum and its
            // own field: the words are the compiler's, and which of them a fact answers is decided
            // where the fact is made rather than read back off the kind beside it here — one kind
            // covers facts that answer differently.
            new Vocabulary("keptOpenBy[].runSensitivity", List.of("$defs", "runSensitivity"),
                    RunSensitivity.class),
            // What kind of thing is keeping the verdict open. Two vocabularies and not one: an
            // opening that is a measure going without something writes that weakening's own word,
            // and everything else writes one of its own. Registered against both, so a word added
            // to either side alone fails here — which is what `keptOpenBy` had instead of, while
            // the writer spelled its words as literals and nothing could be pointed at them.
            new Vocabulary("keptOpenBy[].kind",
                    List.of("$defs", "adequacyOpening", "properties", "kind"),
                    Set.of("probe_mapping_lost", "row_did_not_finish"),
                    Incompleteness.Code.class,
                    souther.compiler.publish.WeakeningWord.class,
                    souther.compiler.publish.AdequacyOpeningWord.class),
            // And what a measure nobody made was waiting for, which the document writes from its
            // own vocabulary rather than from the reasons that produce it. Held against that
            // vocabulary, because that is what is written: read off the reasons instead, a word
            // renamed here would leave the schema and the document disagreeing with every check
            // green. That the vocabulary covers every reason a verdict can rest on is
            // `everyReasonAMeasureNobodyMadeCanGiveHasAWord`'s.
            new Vocabulary("keptOpenBy[].reason", List.of("$defs", "notMeasuredReason"),
                    souther.compiler.publish.NotMeasuredWord.class),
            // `status` is the one enumerated field written through a projection rather than off an
            // enum's own names. The compiler tells a measure with nothing to be about from one nobody
            // made; a document says `unavailable` for both and leaves which to the `reason` beside it.
            // So the words here are the projection's image, which
            // `theStatusWordsAreWhatTheWriterCanWrite` holds against the writer — naming a state
            // inside the compiler is not a change to the contract, and would be one if this read
            // the constants.
            new Vocabulary("status", List.of("$defs", "status"), STATUS_WORDS),
            Vocabulary.of("branch.reason", List.of("$defs", "branch", "properties", "reason"),
                    Adequacy.BranchEvidence.class),
            // Why nobody read which rules of a body's decision the rows took. Its own field beside
            // the branch's, because the two measures fall short of different things: an arm is one
            // branch of the body and a rule is one way through it, and a reading that placed no run
            // has said nothing about the rules while the arms may be counted in full.
            Vocabulary.of("decision.coverage.reason",
                    List.of("$defs", "decision", "properties", "coverage", "properties", "reason"),
                    souther.compiler.query.DecisionEvidence.class),
            new Vocabulary("findings[].kind",
                    List.of("$defs", "findings", "items", "properties", "kind"),
                    Adequacy.Kind.class),
            // What wrote a block a caller handed in. The set comes from the owners a source can
            // write, so a sixth of them has to teach this its word before the schema will pass;
            // only the spelling is written down here, the report's own switch being exhaustive over
            // the same sealed type.
            // One shape and one place, since a fork's subject names what wrote it too.
            new Vocabulary("writtenBy.kind",
                    List.of("$defs", "writtenBy", "properties", "kind"),
                    List.of(souther.compiler.types.WrittenOwner.class), ownerWords(), Set.of()),
            // Which of the measurements a behavior has one of, where a subject names one. Which
            // kind of place a subject is, is not a field of this shape: a subject is written as the
            // union it is, so each kind is the constant one branch turns on, and the words are held
            // against the branches where the union is.
            new Vocabulary("keptOpenBy[].about.measure",
                    List.of("$defs", "subject", "oneOf", "13", "properties", "measure"),
                    souther.compiler.publish.MeasureWord.class),
            // Which distinction one column of a rule's identity is. Projected off the shapes of
            // the reading rather than listed, so a shape added to what a body can decide by has to
            // teach this document its word before the schema will pass.
            new Vocabulary("findings[].obligationId.conditions[].kind",
                    List.of("$defs", "ruleObligationId", "properties", "conditions", "items",
                            "properties", "kind"),
                    List.of(souther.compiler.partition.DecisionCondition.class),
                    conditionWords(), Set.of()),
            new Vocabulary("findings[].disposition",
                    List.of("$defs", "findings", "items", "properties", "disposition"),
                    Adequacy.Finding.Disposition.class),
            // The arms, which is fewer than the kinds a site has. A comparison of a guard's condition
            // is a site and not a fork a row is in or out of, so it never reaches this field —
            // projected off the same predicate the measure uses rather than listed here, so that an
            // arm kind added later still has to teach the schema its word.
            new Vocabulary("branch.obligations[].kind",
                    List.of("$defs", "branch", "properties", "obligations", "items", "properties",
                            "kind"),
                    List.of(souther.compiler.coverage.OutcomeName.class), armWords(), Set.of()),
            // The other half of what an arm is. Held apart from the outcome because they vary on
            // their own: an `else` is written under an `if` and under a `guard`, and a construct
            // added to the language does not add an outcome.
            new Vocabulary("branch.obligations[].construct",
                    List.of("$defs", "branch", "properties", "obligations", "items", "properties",
                            "construct"),
                    List.of(SourceConstruct.class), constructWords(), Set.of()),
            // Where the arm account puts an arm, and why it leaves one out. Spelled by the writer
            // for the reason the obligations' are: which states a consumer must handle is a
            // decision about the contract, and a state renamed inside the compiler is not.
            new Vocabulary("branch.obligations[].disposition",
                    List.of("$defs", "branch", "properties", "obligations", "items", "properties",
                            "disposition"),
                    List.of(ArmDisposition.class), armDispositionWords(), Set.of()),
            new Vocabulary("branch.obligations[].notCountedBecause",
                    List.of("$defs", "branch", "properties", "obligations", "items", "properties",
                            "notCountedBecause"),
                    List.of(ArmExclusion.class), armExclusionWords(), Set.of()),
            // What settles which rule a fork decides by, which is part of what one arm is.
            new Vocabulary("armObligationId.decidedBy",
                    List.of("$defs", "armObligationId", "properties", "decidedBy"),
                    List.of(DecidedBy.class), decidedByWords(), Set.of()),
            // What a rule of the model raises. Only the questions this compiler issues today: a
            // word arrives here in the same change that starts raising it, so the enum and the
            // schema move together or the compile stops.
            // `singleton` and `partition` are what a comparison owes by having been read — the row at
            // the value it singles out, and the rows in the classes its line makes. Those are the
            // partition's own geometry and never a question standing against an answer, so the
            // compiler stopped raising them. Retired rather than gone: reports of this version were
            // written carrying them.
            // Not an enum's own names any more: a question that asks something says what its
            // obligation is called, and one nothing classified says that instead. So the words are
            // asked of the writer, arm by arm, rather than copied from either type.
            new Vocabulary("coverageQuestion", List.of("$defs", "coverageQuestion"),
                    List.of(souther.compiler.inputs.StandingQuestion.class),
                    questionWords(), Set.of("singleton", "partition")),
            // `no_axis_derived` is what `the_reading_did_not_run_out` was called while it also
            // stood for a reading that ran out and found nothing to divide. Retired rather than
            // gone: reports of this version were written carrying it.
            Vocabulary.of("partition.axesMeasure.reason",
                    List.of("$defs", "partition", "properties", "axesMeasure", "properties",
                            "reason"),
                    Set.of("no_axis_derived"),
                    souther.compiler.query.PartitionDerivation.class,
                    // Not one of this measure's own reasons: an input the rules leave empty is a
                    // fact about the behavior, and every measure of that input gives it. Named on
                    // each surface that admits it rather than inferred from its being shared —
                    // which measures publish a word is what a vocabulary is for, and a type sitting
                    // beside them says nothing about that.
                    souther.compiler.query.NoFeasibleInput.class),
            // The vocabularies a reason is written in are not here. Which words a surface of the
            // document admits is decided by the capabilities its producers hold, and each is held
            // against those in `WhatEachWayOfDrawingNoLineLeavesIsWrittenDownOnce` — a list here of
            // which word belongs to which surface would be that answer kept a second time by hand,
            // and it is what let one surface admit everything the other did.
            // And `no_lines_derived` likewise.
            Vocabulary.of("partition.boundariesMeasure.reason",
                    List.of("$defs", "partition", "properties", "boundariesMeasure", "properties",
                            "reason"),
                    Set.of("no_lines_derived"),
                    souther.compiler.query.BoundaryDerivation.class,
                    souther.compiler.query.NoFeasibleInput.class),
            Vocabulary.of("partition.axes[].reason",
                    List.of("$defs", "partition", "properties", "axes", "items", "properties",
                            "reason"),
                    PartitionEvidence.AxisCoverage.class,
                    souther.compiler.query.NothingWasAsked.class),
            new Vocabulary("partition.axes[].unprovenClaims[].why",
                    List.of("$defs", "partition", "properties", "axes", "items", "properties",
                            "unprovenClaims", "items", "properties", "why"),
                    souther.compiler.query.ClaimAnnotations.Why.class),
            new Vocabulary("partition.claimsOffAxis[].why",
                    List.of("$defs", "partition", "properties", "claimsOffAxis", "items",
                            "properties", "why"),
                    souther.compiler.query.ClaimAnnotations.Why.class),
            new Vocabulary("partition.boundaries[].items[].point",
                    List.of("$defs", "partition", "properties", "boundaries", "items", "properties",
                            "items", "items", "properties", "point"),
                    souther.compiler.partition.PointRole.class),
            new Vocabulary("partition.boundaries[].items[].notOwed",
                    List.of("$defs", "partition", "properties", "boundaries", "items", "properties",
                            "items", "items", "properties", "notOwed"),
                    souther.compiler.partition.NotOwedReason.class),
            new Vocabulary("partition.boundaries[].kind",
                    List.of("$defs", "partition", "properties", "boundaries", "items", "properties",
                            "kind"),
                    souther.compiler.partition.BoundaryTarget.Shape.class),
            // `no_arm_witnesses_it` was what a guard's line came back as where the arms could not
            // separate the rows that reached its comparison from the rows that did not. The
            // comparison is observed where it runs now, so nothing produces the word — and reports of
            // this version were written carrying it, and a version says what its documents may carry.
            new Vocabulary("partition.boundaries[].items[].reason",
                    List.of("$defs", "partition", "properties", "boundaries", "items", "properties",
                            "items", "items", "properties", "reason"),
                    Set.of("no_arm_witnesses_it"),
                    ItemAssessment.Coverage.NotAsked.class,
                    ItemAssessment.Coverage.CouldNotAsk.class),
            new Vocabulary("partition.pairs.reason",
                    List.of("$defs", "partition", "properties", "pairs", "properties", "reason"),
                    PartitionEvidence.PairSpace.NoRows.class, souther.compiler.query.NothingWasAsked.class),
            new Vocabulary("signature.reason", List.of("$defs", "signature", "properties", "reason"),
                    Adequacy.SignatureEvidence.NotASum.class,
                    Adequacy.SignatureEvidence.NoRows.class, souther.compiler.query.NothingWasAsked.class),
            // Two vocabularies under one key. An observation that went missing writes the code it
            // already has, and everything else writes a word of its own — one field, because a
            // consumer reading what weakened a measurement does not care which of the two a word
            // came from.
            // The two leaves of a signature, which have a measurement of their own since #953: a
            // position that is not a sum has nothing to count, and one no row names was not counted.
            new Vocabulary("signature.output.reason",
                    List.of("$defs", "signature", "properties", "output", "properties", "reason"),
                    souther.compiler.query.OutputCaseEvidence.NotASum.class,
                    souther.compiler.query.OutputCaseEvidence.NoRows.class, souther.compiler.query.NothingWasAsked.class),
            new Vocabulary("signature.inputs[].reason",
                    List.of("$defs", "signature", "properties", "inputs", "items", "properties",
                            "reason"),
                    souther.compiler.query.InputCaseEvidence.NotASum.class,
                    souther.compiler.query.InputCaseEvidence.NoRows.class, souther.compiler.query.NothingWasAsked.class),
            // `row_did_not_finish` said in this vocabulary what `row_undecided` says in the
            // observation codes, and named the row without its source, so two rows of one behavior
            // were one word. Retired rather than gone, for the reason `probe_mapping_lost` is.
            new Vocabulary("weakening[]", List.of("$defs", "weakening", "items"),
                    Set.of("probe_mapping_lost", "row_did_not_finish"),
                    Incompleteness.Code.class,
                    souther.compiler.publish.WeakeningWord.class),
            new Vocabulary("incompleteness.scope",
                    List.of("$defs", "incompleteness", "properties", "scope"),
                    Incompleteness.Scope.class),
            // `probe_mapping_lost` is what `instrumentation_absent` was called. It is retired
            // rather than gone: reports of this version were written carrying it, and a version
            // says what its documents may carry. Naming it here is what keeps that a decision about
            // the contract — a word that stops being emitted and is not written down still fails.
            new Vocabulary("incompleteness.code",
                    List.of("$defs", "incompleteness", "properties", "code"),
                    Set.of("probe_mapping_lost"), Incompleteness.Code.class),
            // Whether a place is where the code it names is written. Not an enum: the two words
            // are the citation's own, and are read off it rather than listed here.
            new Vocabulary("at.writtenAt.kind",
                    List.of("$defs", "writtenAt", "properties", "kind"),
                    List.of(Citation.class), writtenAtWords(), Set.of()),
            // What showed a row can be written at a point. Spelled by the writer rather than by the
            // constants, the way `status` is: which grounds a consumer must handle is a decision
            // about the contract, and renaming one inside the compiler is not.
            new Vocabulary("partition.boundaries[].items[].writableBecause",
                    List.of("$defs", "partition", "properties", "boundaries", "items", "properties",
                            "items", "items", "properties", "writableBecause", "items"),
                    List.of(ItemAssessment.WritabilityEvidence.Ground.class), groundWords(),
                    Set.of()),
            // The account beside the geometry: one entry per point a behavior is owed, with every
            // reading of it. The same words as a border's items, since an obligation's measurement
            // is folded from its readings' and each reading's is one of those items.
            new Vocabulary("obligations[].point",
                    List.of("$defs", "obligations", "items", "properties", "point"),
                    souther.compiler.partition.PointRole.class),
            new Vocabulary("obligations[].reason",
                    List.of("$defs", "obligations", "items", "properties", "reason"),
                    Set.of("no_arm_witnesses_it"),
                    ItemAssessment.Coverage.NotAsked.class,
                    ItemAssessment.Coverage.CouldNotAsk.class),
            new Vocabulary("obligations[].writableBecause",
                    List.of("$defs", "obligations", "items", "properties",
                            "writableBecause", "items"),
                    List.of(ItemAssessment.WritabilityEvidence.Ground.class), groundWords(),
                    Set.of()),
            // How an account treats the obligation, beside the evidence that is why. Spelled by the
            // writer for the reason the grounds are: which dispositions a consumer must handle is a
            // decision about the contract, and a state renamed inside the compiler is not.
            new Vocabulary("obligations[].disposition",
                    List.of("$defs", "obligations", "items", "properties", "disposition"),
                    List.of(ObligationDisposition.class), dispositionWords(), Set.of()),
            new Vocabulary("obligations[].undecidedAbout",
                    List.of("$defs", "obligations", "items", "properties",
                            "undecidedAbout", "items"),
                    List.of(ObligationDisposition.Uncertainty.class), undecidedWords(), Set.of()),
            new Vocabulary("obligations[].readings[].reason",
                    List.of("$defs", "obligations", "items",
                            "properties", "readings", "items", "properties", "reason"),
                    Set.of("no_arm_witnesses_it"),
                    ItemAssessment.Coverage.NotAsked.class,
                    ItemAssessment.Coverage.CouldNotAsk.class),
            // What tells one thing a row is owed for from another. Which of the four points it is
            // comes off the role, as everywhere else; the rest are constants, the way the grounds
            // above are — what a consumer must handle is a decision about the contract, and what
            // this compiler calls the arms of a sum to itself is not.
            new Vocabulary("location.kind",
                    List.of("$defs", "location", "properties", "kind"),
                    List.of(souther.compiler.partition.DomainPoint.class),
                    Set.of("at_the_line", "beside_the_line", "in_the_region"), Set.of()),
            new Vocabulary("location.side",
                    List.of("$defs", "location", "properties", "side"),
                    souther.compiler.numeric.Towards.class),
            new Vocabulary("obligationId.stops.kind",
                    List.of("$defs", "obligationId", "properties", "stops", "properties", "kind"),
                    List.of(souther.compiler.partition.FarEnd.class),
                    Set.of("at_a_line", "at_the_domain", "at_the_order_end"), Set.of()),
            new Vocabulary("obligationId.stops.towards",
                    List.of("$defs", "obligationId", "properties", "stops", "properties",
                            "towards"),
                    souther.compiler.numeric.Towards.class),
            new Vocabulary("level.kind", List.of("$defs", "level", "properties", "kind"),
                    List.of(souther.compiler.partition.Level.class),
                    Set.of("on_a_carrier", "a_count"), Set.of()),
            new Vocabulary("typeId.kind", List.of("$defs", "typeId", "properties", "kind"),
                    List.of(souther.compiler.types.TypeSymbol.class),
                    Set.of("declared", "primitive", "language_case"), Set.of()),
            new Vocabulary("carrier.kind", List.of("$defs", "carrier", "properties", "kind"),
                    List.of(souther.compiler.check.Carrier.class),
                    Set.of("whole", "dense", "days", "seconds", "seconds_of_day", "nanos", "text",
                            "ordinal"),
                    Set.of()));

    /**
     * One disposition of each kind there is, which is what the words are asked of.
     *
     * <p>A value apiece rather than a list of words, so that the words come off the writer. Which
     * kinds there are is checked against the type below: a disposition added and not sampled here
     * would leave the schema promising the words of the ones before it, and the compiler could write
     * a document the shipped schema refuses.
     */
    private static List<ObligationDisposition> dispositions() {
        return List.of(
                new ObligationDisposition.Met(),
                new ObligationDisposition.Unmet(),
                ObligationDisposition.Undecided.about(List.of(
                        new ObligationDisposition.Uncertainty.WhetherARowIsThere.ReadingsStopped(
                                ReadingReasons.of(List.of(ReadingGap.NO_VALUE))))),
                ObligationDisposition.Undecided.about(List.of(
                        new ObligationDisposition.Uncertainty.WhetherARowIsThere.NothingWasRead(
                                UnaskedReasons.of(
                                        ItemAssessment.Coverage.NotAsked.NO_ROWS)))),
                ObligationDisposition.Undecided.about(List.of(
                        new ObligationDisposition.Uncertainty.WhetherARowCanBeWritten.Stopped(
                                WritabilityKnowledge.Prevented.by(EstablishmentGap.Observation.of(
                                        Set.of(Incompleteness.Code.VALUE_UNREADABLE)))))),
                ObligationDisposition.Undecided.about(List.of(
                        new ObligationDisposition.Uncertainty
                                .WhetherARowCanBeWritten.NothingShowedIt())));
    }

    /** Every kind of disposition is sampled above, so the words below are all the words there are. */
    @Test
    void everyDispositionHasASample() {
        Set<Class<?>> kinds = new LinkedHashSet<>();
        for (ObligationDisposition each : dispositions()) {
            kinds.add(each.getClass());
        }
        assertEquals(leavesOf(ObligationDisposition.class), kinds,
                "a disposition the writer can write is one this asks for a word");
    }

    /** The kinds a sealed hierarchy bottoms out in, which are the values that can be written. */
    private static Set<Class<?>> leavesOf(Class<?> sealedType) {
        Set<Class<?>> out = new LinkedHashSet<>();
        Class<?>[] permitted = sealedType.getPermittedSubclasses();
        if (permitted == null) {
            out.add(sealedType);
            return out;
        }
        for (Class<?> each : permitted) {
            out.addAll(leavesOf(each));
        }
        return out;
    }

    /** The dispositions a document may name, spelled by the one writer of the field. */
    private static Set<String> dispositionWords() {
        return dispositions().stream()
                .map(AdequacyReport::wire)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    /** Where the arm account puts an arm, spelled by the one writer of the field. */
    private static Set<String> armDispositionWords() {
        return Arrays.stream(ArmDisposition.values())
                .map(AdequacyReport::wire)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * The reasons the arm account leaves an arm out, likewise spelled by the writer.
     *
     * <p>Built from a value of each rather than from a list of names: what a fork carries is not
     * part of the word, and an exclusion added to the sum arrives here as a case the writer has no
     * word for rather than as a word this test forgot.
     */
    private static Set<String> armExclusionWords() {
        SourceConstructOrigin fork = SourceConstructOrigin.written(
                new WrittenOwner.Body("m", "b"), 0, SourceConstruct.IF);
        return java.util.stream.Stream
                .<ArmExclusion>of(new ArmExclusion.OccurrencesNotToldApart(fork))
                .map(AdequacyReport::wire)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    /** What settles which rule a fork decides by, spelled by the writer for the same reason. */
    private static Set<String> decidedByWords() {
        return java.util.stream.Stream.of(DecidedBy.THE_DECLARATION,
                        new DecidedBy.BySupplied(List.of(new SuppliedRules.RuleIdentity.Written(
                                RuleOrigin.written(new WrittenOwner.Body("m", "b"), 0)))),
                        DecidedBy.NOT_SAID)
                .map(AdequacyReport::wire)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * The questions an obligation may be undecided about, likewise spelled by the writer.
     *
     * <p>Read off the answers sampled above rather than off the order they are said in. The word a
     * document carries is the question's and not what left it open, so any member of a family
     * spells it — which {@code AnObligationsExplanationNamesEachReasonOnceTest} holds the writer
     * to — and a sample missing a question is what {@code everyDispositionHasASample} is about.
     */
    private static Set<String> undecidedWords() {
        Set<String> out = new LinkedHashSet<>();
        for (ObligationDisposition each : dispositions()) {
            if (each instanceof ObligationDisposition.Undecided open) {
                open.because().written().forEach(question -> out.add(AdequacyReport.wire(question)));
            }
        }
        return out;
    }

    /** The grounds a document may name, spelled by the one writer of the field. */
    private static Set<String> groundWords() {
        return Arrays.stream(ItemAssessment.WritabilityEvidence.Ground.values())
                .map(AdequacyReport::wire)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * The status words the schema allows are the ones the writer can write.
     *
     * <p>Held against the writer because that is what a document carries. The list above is what a
     * consumer must handle, and a projection that started answering a fourth word would leave that
     * list true of nothing — the schema and the enum would still agree, and the documents would
     * agree with neither.
     */
    @Test
    void theStatusWordsAreWhatTheWriterCanWrite() {
        Set<String> written = new LinkedHashSet<>();
        for (MeasurementStatus status : MeasurementStatus.values()) {
            written.add(AdequacyReport.wire(status));
        }
        assertEquals(STATUS_WORDS, written);
    }

    /**
     * The two places the schema keys a condition on a status word, and where those words come from.
     *
     * <p>Not a vocabulary a document carries: these are the guards that say which keys an object has
     * when its measure produced a value — {@code branch} writes its counts and a border's point
     * writes {@code hit} exactly there (issue #997). So the words are not checked against an enum's
     * spellings but against the statuses a measure <em>with a value</em> comes out as, worked out by
     * asking each state of a measure whether it made one. A guard listing a word for a state that
     * has no value would demand a count of a measurement that has none, which is the shape this
     * whole field is here to keep out; one missing a word for a state that has one would forbid a
     * count the writer goes on writing.
     *
     * <p>Which is why they are not in {@link #VOCABULARIES}. A vocabulary is what a reader must
     * handle; this is a condition, and the check that matters is that it selects the right states.
     */
    @Test
    void theWordsAGuardKeysOnAreTheStatusesOfAMeasurementWithAValue() {
        souther.compiler.query.WeakeningSet by = souther.compiler.query.WeakeningSet.of(
                new souther.compiler.query.Weakening.ArmsUnsettled(
                        new SourceConstructOrigin(new WrittenOwner.Body("m", "b"), 0, 0,
                                SourceConstruct.IF)));
        Set<String> withAValue = new LinkedHashSet<>();
        for (souther.compiler.query.Measure<String> each : List.<
                souther.compiler.query.Measure<String>>of(
                new souther.compiler.query.Measurement.Complete<>("a"),
                new souther.compiler.query.Measurement.Partial<>("a", by),
                new souther.compiler.query.Measurement.NotMeasured<>(
                        Adequacy.BranchEvidence.NotAsked.NO_ROWS),
                new souther.compiler.query.Measurement.FailedToMeasure<>(
                        Adequacy.BranchEvidence.Unreadable.UNREADABLE, by),
                new souther.compiler.query.Measure.NotApplicable<>(
                        Adequacy.BranchEvidence.NoArms.NO_BODY))) {
            if (each.made().isPresent()) {
                withAValue.add(AdequacyReport.wire(AdequacyReport.statusOf(each)));
            }
        }

        for (List<String> guard : List.of(
                List.of("$defs", "branch", "if", "properties", "status"),
                List.of("$defs", "partition", "properties", "boundaries", "items",
                        "properties", "items", "items", "if", "properties", "status"))) {
            assertEquals(withAValue, allowedAt(schema(), guard),
                    guard + ": the guard selects states other than the ones with a value");
        }
    }

    @Test
    void everyEnumeratedFieldNamesCurrentOrRetiredWords() {
        JsonNode schema = schema();
        for (Vocabulary each : VOCABULARIES) {
            assertEquals(each.allowed(), allowedAt(schema, each.at()),
                    each.label() + ": the schema and " + each.source().stream()
                            .map(Class::getSimpleName).toList()
                            + " name different words");
        }
    }

    /**
     * Where a behavior's body comes from, which the schema and the enum have to spell alike.
     *
     * <p>Read off {@link BehaviorImplementation#values()} rather than listed here, so a state added
     * to the enum and not to the schema is this test failing and not a document nothing validates.
     */
    @Test
    void theWordsForWhereABodyComesFromAreTheStatesThereAre() {
        assertEquals(java.util.Arrays.stream(BehaviorImplementation.values())
                        .map(BehaviorImplementation::written).collect(java.util.stream.Collectors.toSet()),
                allowedAt(schema(), List.of("$defs", "behavior", "properties", "implementation")));
    }

    /**
     * And the other field with no enum behind it: whether anything this measure reads about a
     * position is left standing, which is derived from two things rather than enumerated.
     *
     * <p>Both of the things, since either alone leaves the other's word unwritten: a position can
     * have a question about its values that nothing answered, or a subtree the walk never entered,
     * or both, and all three are the same word here.
     */
    @Test
    void theOtherFieldWithNoEnumBehindItIsWrittenFromWhatAReadingLeavesStanding() {
        assertEquals(Set.copyOf(List.of(
                        AdequacyReport.readingWord(PartitionEvidence.AxisCoverage.ANSWERED),
                        AdequacyReport.readingWord(
                                new PartitionEvidence.AxisCoverage.Reading(
                                        PartitionEvidence.AxisCoverage.Reach.EVERY_RULE, false)),
                        AdequacyReport.readingWord(
                                new PartitionEvidence.AxisCoverage.Reading(
                                        PartitionEvidence.AxisCoverage.Reach.SOME_OUT_OF_SIGHT,
                                        true)),
                        AdequacyReport.readingWord(
                                new PartitionEvidence.AxisCoverage.Reading(
                                        PartitionEvidence.AxisCoverage.Reach.SOME_OUT_OF_SIGHT,
                                        false)))),
                allowedAt(schema(), List.of("$defs", "partition", "properties", "axes", "items",
                        "properties", "read", "properties", "extent")));
    }

    /**
     * The places this schema says which version it is agree with the writer.
     *
     * <p>The number a document carries and the identifier a resolver keys on. They are edited in
     * different places and one of them was left behind: a copy raised to 2 kept the `$id` of the
     * first, so two schemas claimed one canonical name and a consumer holding a cache would be
     * handed whichever it fetched first.
     *
     * <p>The file's own name is no longer one of them. What is opened is
     * {@link AdequacyReport#SCHEMA_RESOURCE}, which the writer derives from the version it emits —
     * so a copy raised and left under the old name is a file this does not find, and the
     * {@code assertNotNull} where it is read says so. Held as a third literal beside the two below,
     * the name was one more thing to raise by hand, and five other tests opened theirs and never
     * checked it.
     */
    @Test
    void theVersionIsTheSameInBothPlacesTheSchemaWritesIt() {
        assertEquals(AdequacyReport.SCHEMA_VERSION,
                schema().get("properties").get("schemaVersion").get("const").asInt(),
                "what a document carries is what this schema demands");
        assertTrue(schema().get("$id").asString()
                        .endsWith("adequacy-" + AdequacyReport.SCHEMA_VERSION + ".json"),
                "and so is the identifier a resolver keys on: " + schema().get("$id"));
    }

    /**
     * And the third: which kind of thing a question is about, written from the arms of a sealed type
     * rather than from an enum.
     *
     * <p>Both arms of {@code Owed} say `position` — a position of an input, or a number of one — and
     * they are still asked of the writer rather than assumed, so an arm added and not given a word
     * stops the compile. A question nothing classified says `filedAt` instead, which is a place and
     * not a subject.
     *
     * <p>`comparison` is beside them and is retired. It was to have been the place a comparison of
     * two moving things draws; nothing ever raised such a question, because a comparison this
     * compiler reads owes its rows by having been read. The word stays because this version of the
     * schema promised it.
     */
    @Test
    void theThirdFieldWithNoEnumBehindItIsWrittenFromWhatAQuestionIsAbout() {
        // A set built up rather than `Set.of`, because the two arms say one word: which values may
        // stand at a position and where a line on a number of it falls are both about the position,
        // and a document says so once.
        Set<String> written = new LinkedHashSet<>();
        written.add(AdequacyReport.subjectWord(asking(new souther.compiler.inputs.InputQuestion
                .AboutAPosition(souther.compiler.inputs.TermPath.of("x")))));
        written.add(AdequacyReport.subjectWord(asking(new souther.compiler.inputs.InputQuestion
                .AboutANumber(souther.compiler.check.NumberAt.valueOf(
                        souther.compiler.inputs.TermPath.of("x"))))));
        written.add(AdequacyReport.subjectWord(unclassified()));
        written.add("comparison");

        assertEquals(written,
                allowedAt(schema(), List.of("$defs", "partition", "properties", "unanswered",
                        "items", "properties", "subject", "properties", "kind")));
    }

    /** What a document calls each thing a rule can leave open, asked of the writer of the word. */
    private static Set<String> questionWords() {
        Set<String> out = new LinkedHashSet<>();
        out.add(AdequacyReport.questionWord(asking(new souther.compiler.inputs.InputQuestion
                .AboutAPosition(souther.compiler.inputs.TermPath.of("x")))));
        out.add(AdequacyReport.questionWord(asking(new souther.compiler.inputs.InputQuestion
                .AboutANumber(souther.compiler.check.NumberAt.valueOf(
                        souther.compiler.inputs.TermPath.of("x"))))));
        out.add(AdequacyReport.questionWord(boundaryUndetermined()));
        out.add(AdequacyReport.questionWord(unclassified()));
        return out;
    }

    /** A rule read far enough to say it restricts the values, and no further. */
    private static souther.compiler.inputs.StandingQuestion boundaryUndetermined() {
        return souther.compiler.inputs.StandingQuestion.BoundaryUndetermined.of(
                new souther.compiler.check.RuleCitation.Written(
                        new RuleRef.Comparison("f",
                                new SourceConstructOrigin(
                                        new WrittenOwner.Body("m", "b"), 0, 0,
                                        SourceConstruct.IF)),
                        new souther.compiler.check.RuleReportAnchor.ByTheModuleThatWroteIt()),
                souther.compiler.inputs.FilingCoordinate.at(
                        souther.compiler.inputs.TermPath.of("x")),
                new souther.compiler.inputs.BlockReason.UnreadComparisonForm());
    }

    /** A rule this compiler did not read far enough to classify. */
    private static souther.compiler.inputs.StandingQuestion unclassified() {
        return souther.compiler.inputs.StandingQuestion.NothingClassifiesIt.of(
                new souther.compiler.check.RuleCitation.Written(
                        new RuleRef.Comparison("f",
                                new SourceConstructOrigin(
                                        new WrittenOwner.Body("m", "b"), 0, 0,
                                        SourceConstruct.IF)),
                        new souther.compiler.check.RuleReportAnchor.ByTheModuleThatWroteIt()),
                souther.compiler.inputs.FilingCoordinate.at(
                        souther.compiler.inputs.TermPath.of("x")),
                new souther.compiler.inputs.BlockReason.UnreadComparisonForm());
    }

    /** A question that asks {@code about}, made the one way there is to make one. */
    private static souther.compiler.inputs.StandingQuestion asking(
            souther.compiler.inputs.InputQuestion about) {
        return souther.compiler.inputs.StandingQuestion.Exact.of(
                new souther.compiler.check.RuleCitation.Written(
                        new RuleRef.Comparison("f",
                                new SourceConstructOrigin(
                                        new WrittenOwner.Body("m", "b"), 0, 0,
                                        SourceConstruct.IF)),
                        new souther.compiler.check.RuleReportAnchor.ByTheModuleThatWroteIt()),
                about,
                new souther.compiler.inputs.WhatAQuestionStandsOn(
                        souther.compiler.inputs.RuleReasons.one(
                                new souther.compiler.inputs.BlockReason.UnreadComparisonForm()),
                        Optional.empty()));
    }

    /**
     * And the fourth: which kind of rule an identity is of, written from the arms of a sealed type.
     *
     * <p>The arms are told apart by different coordinates — a clause of an invariant by where it is
     * written among its declaration's clauses, a comparison by the site it was numbered at — so
     * which one a document is carrying decides which of its keys are written.
     */
    @Test
    void theFourthFieldWithNoEnumBehindItIsWrittenFromWhichKindOfRuleItIs() {
        assertEquals(
                oneRuleOfEachKind().stream().map(AdequacyReport::schemaRuleKind)
                        .collect(java.util.stream.Collectors.toSet()),
                allowedAt(schema(), List.of("$defs", "ruleId", "properties", "kind")));
    }

    /**
     * One rule of every kind the seal has, held to being that.
     *
     * <p>The words above are read off these, so a kind missing here is a word the schema is never
     * asked about — and the schema is not asked about it either, which is two lists agreeing by
     * both being short. Held to the seal, a kind of rule added to the model is one this stops at
     * until somebody says which word a document writes for it.
     */
    private static List<RuleRef> oneRuleOfEachKind() {
        souther.compiler.types.TypeSymbol.AtModule on =
                souther.compiler.types.TypeSymbols.declared(
                        new TypeKey("m", "L"));
        WrittenOwner.Body body = new WrittenOwner.Body("m", "b");
        List<RuleRef> out = List.of(
                new RuleRef.Invariant(
                        new Clause.Ref(
                                new Clause.Id(on, 0),
                                Optional.empty())),
                new RuleRef.Ensures(
                        new BehaviorContract.RuleId(
                                new ValueName.Behavior("m", "f"),
                                0, 0, on), "Found"),
                new RuleRef.Comparison("f",
                        new SourceConstructOrigin(body, 0, 0,
                                SourceConstruct.BINARY)),
                // A rule a body writes as one of the language's own operations over the values at a
                // position, which tells a set of them from the rest and draws no line. Its own word
                // beside a comparison because what a reader does about them differs.
                new RuleRef.Predicate("f",
                        new SourceConstructOrigin(body, 1, 0,
                                SourceConstruct.CALL)),
                // And a fork whose condition states none of those, which is the model dividing on
                // something this compiler did not read.
                new RuleRef.Fork("f",
                        new SourceConstructOrigin(body, 2, 0,
                                SourceConstruct.IF)));

        assertEquals(leavesOf(RuleRef.class),
                out.stream().map(each -> (Class<?>) each.getClass())
                        .collect(java.util.stream.Collectors.toSet()),
                "one rule of each kind the seal has, and no other");
        return out;
    }

    /**
     * The reasons a measure can give that no {@code reason} field of the document carries.
     *
     * <p>Each of them belongs to a measure whose whole section is left out where it holds, and the
     * fact is written under {@code weakening} instead — which the schema says itself, at
     * {@code partition} and at {@code signature}: absent where the behavior has no signature to
     * read, said as {@code behavior_boundary_not_derived}. So there is no {@code reason} key for a
     * word to go in, and a vocabulary naming them would be a field promising words nothing writes.
     *
     * <p>Written out because the alternative is worse in both directions. Left off the check below,
     * a reason really nobody published looks exactly like these; folded into a vocabulary, the
     * schema would carry three words the writer never emits. Naming them makes the third case — a
     * reason a document says nothing about — a thing somebody decided rather than a thing nobody
     * noticed.
     */
    private static Set<Class<?>> saidToAReaderAndNotToADocument() {
        return Set.of(
            // A reading of the rows, which the human report writes as `rows not read` and the
            // document does not carry as a measure at all.
            Adequacy.RowReading.NotAsked.class,
            Adequacy.RowReading.Unavailable.class,
            // A behavior whose signature could not be read. Every measure that needs the boundary
            // is short of it, and the document leaves those sections out rather than writing each
            // of them a reason: `behavior_boundary_not_derived` is a `weakening` word, and is held
            // as one above.
            souther.compiler.query.BoundaryForMeasurement.NotDerived.class);
    }

    /**
     * Every reason a measure the verdict rests on can give for never having been made.
     *
     * <p>The seal, less the ones no {@code reason} field carries. Computed rather than listed for
     * the reason every population here is: a list would be a second copy of the membership, and the
     * next arm added would be missing from it.
     *
     * <p>The subtraction is not a convenience. A reading of the rows nobody asked for is a measure
     * this build was never going to make, and a verdict is not held open by one — so it is left out
     * of what the verdict rests on before {@code keptOpenBy} ever sees it, and a schema promising
     * its word would promise one nothing writes.
     */
    private static Class<?>[] everyReasonAMeasureNobodyMadeCanGive() {
        List<Class<?>> out = new ArrayList<>();
        for (Class<?> arm : armsOf(NotMeasuredReason.class)) {
            if (!saidToAReaderAndNotToADocument().contains(arm)) {
                out.add(arm);
            }
        }
        return out.toArray(new Class<?>[0]);
    }

    /**
     * And every one of those reasons has a word in the vocabulary the document writes.
     *
     * <p>The other half of holding {@code keptOpenBy[].reason} against that vocabulary. The schema
     * and the vocabulary agreeing says the words are the same words; this says the vocabulary
     * covers every reason a verdict can rest on, so that neither check is passed by a reason
     * nothing has a word for.
     *
     * <p>Asked of the reason and not of the arm, because two constants of one arm can be two words
     * — a measure nobody asked for and one there were no rows for come out of one enum.
     */
    @Test
    void everyReasonAMeasureNobodyMadeCanGiveHasAWord() {
        List<String> without = new ArrayList<>();
        for (Class<?> arm : everyReasonAMeasureNobodyMadeCanGive()) {
            for (Object constant : arm.getEnumConstants()) {
                try {
                    souther.compiler.publish.NotMeasuredWord.of((NotMeasuredReason) constant);
                } catch (RuntimeException refused) {
                    without.add(arm.getSimpleName() + "." + constant + ": "
                            + refused.getMessage());
                }
            }
        }

        assertEquals(List.of(), without,
                "a reason a verdict can rest on that the document has no word for, so a report"
                        + " that met it would have nothing to write");
    }

    /**
     * And every reason a measure can give is either registered with some field or named as one no
     * field carries.
     *
     * <p>The other direction of the test below, and it takes a different hole. That one asks whether
     * every field of the schema has somebody saying where its words come from; this asks whether
     * every producer of a word has been given a field to say it in. A reason nobody registered is in
     * neither list, so both passed while the compiler wrote a word its own schema refused.
     *
     * <p>What let that happen is where a vocabulary gets its sources: a measure's reasons are the
     * types nested in it, and {@link souther.compiler.query.NoFeasibleInput} is nested in no measure
     * because more than one measure gives it. So it was a producer with no surface, and neither list
     * had a place to notice.
     *
     * <p><b>All three families, because the writer does not tell them apart.</b> One door writes a
     * measure's reason — {@code word(said.reason())} — and what it is handed is a
     * {@link souther.compiler.observe.MeasureReason}, so which of the three a reason implements
     * decides nothing about whether a document carries its word. Held over
     * {@code NotApplicableReason} alone, a shared {@code FailureReason} or {@code NotMeasuredReason}
     * put beside its producers would reopen exactly the hole this closed; {@code NothingWasAsked}
     * shows that shared reasons outside any measure are the ordinary case rather than a one-off.
     *
     * <p>Held over the <em>types</em> rather than over the words. Two reasons may spell one word —
     * that is what a shared field is — so a check that a word turns up somewhere passes for a reason
     * that got its spelling by coincidence, which is the orphan this is here to catch.
     */
    @Test
    void everyMeasureReasonProducerIsRegisteredWithSomeFieldOrNamedAsCarriedByNone() {
        Set<Class<?>> registered = new LinkedHashSet<>();
        for (Vocabulary each : VOCABULARIES) {
            registered.addAll(each.source());
        }

        List<String> unaccounted = new ArrayList<>();
        Set<Class<?>> leaves = new LinkedHashSet<>();
        for (Class<?> family : List.of(souther.compiler.query.NotApplicableReason.class,
                souther.compiler.query.NotMeasuredReason.class,
                souther.compiler.query.FailureReason.class)) {
            for (Class<?> arm : armsOf(family)) {
                leaves.add(arm);
                if (!registered.contains(arm)
                        && !saidToAReaderAndNotToADocument().contains(arm)) {
                    unaccounted.add(arm.getSimpleName());
                }
            }
        }

        assertEquals(List.of(), unaccounted,
                "a reason no field of the schema was told about and nothing says a field never"
                        + " carries, so the compiler can write a word the shipped schema refuses");

        // And the exceptions are still exceptions. One that got a field, or one whose type went
        // away, leaves a reason exempted from the check above for a fact that stopped being true —
        // which is the same silence one more turn along.
        for (Class<?> said : saidToAReaderAndNotToADocument()) {
            assertTrue(leaves.contains(said),
                    said.getSimpleName() + " is no longer a reason any measure gives");
            assertTrue(!registered.contains(said),
                    said.getSimpleName() + " is registered with a field, so a document does carry"
                            + " its word and it is not one of these");
        }
    }

    /**
     * The reasons a sealed interface stands for, which are its leaves and not its permitted names.
     *
     * <p>A permitted type may be sealed in turn, and an intermediate is not something a document
     * carries — what a report writes is a constant, so what has to have been registered is what a
     * constant is of.
     */
    private static List<Class<?>> armsOf(Class<?> of) {
        Class<?>[] permitted = of.getPermittedSubclasses();
        if (permitted == null) {
            return List.of(of);
        }
        List<Class<?>> out = new ArrayList<>();
        for (Class<?> each : permitted) {
            out.addAll(armsOf(each));
        }
        return out;
    }

    /** Every enumerated field of the schema is either held above or named as the exception. */
    @Test
    void noEnumeratedFieldIsUnaccountedFor() {
        List<String> paths = new ArrayList<>();
        collectEnums(schema(), "", paths);
        Set<String> held = new LinkedHashSet<>();
        for (Vocabulary each : VOCABULARIES) {
            held.add("/" + String.join("/", each.at()));
        }
        // The words each surface of the document may carry, held against the capabilities its
        // producers hold rather than against an enum, in
        // `WhatEachWayOfDrawingNoLineLeavesIsWrittenDownOnce`. What decides them is which reasons
        // reach the surface, and a list of words here would say the same thing without saying why.
        held.add("/$defs/ruleStoppedReadingReason");
        held.add("/$defs/answerRealizationStoppedReason");
        held.add("/$defs/notReadReason/anyOf/1");
        held.add("/$defs/behavior/properties/implementation");
        held.add("/$defs/partition/properties/axes/items/properties/read/properties/extent");
        held.add("/$defs/partition/properties/unanswered/items/properties/subject/properties/kind");
        held.add("/$defs/ruleId/properties/kind");
        // What wrote the application a predicate is. Its words are the owners such a rule may be
        // written by, which is decided where one is made rather than by an enum — so they are held
        // against what the constructor admits, in
        // `EveryKindOfRuleADocumentCanNameHasAnIdentityAndAWord`.
        held.add("/$defs/ruleId/properties/writtenIn");
        // The two guards, held by the test above rather than against a vocabulary. They say which
        // keys an object has where its measure produced a value, so what has to be true of them is
        // that they name the states that did — not that a reader knows the words.
        held.add("/$defs/branch/if/properties/status");
        held.add("/$defs/partition/properties/boundaries/items/properties/items/items/if"
                + "/properties/status");

        List<String> unaccounted = paths.stream().filter(p -> !held.contains(p)).toList();
        assertEquals(List.of(), unaccounted,
                "an enumerated field nothing says where the words come from");
    }

    /**
     * A key added since is optional, which is what keeps an older document a document of this version.
     *
     * <p>The schema says so in its own description: a change that removes or renames anything raises
     * the number, and something added does not, so a report written before a key existed is still one
     * of this version. Requiring an added key breaks that in the file that states it — every earlier
     * document is refused by the schema it was written against.
     *
     * <p>Held as the keys a boundary written before {@code kind} carried, which is the shape the
     * defect took. A key added later and required would fail here rather than on somebody's stored
     * report, and the word test beside this one cannot see it: the words were right, and the document
     * carrying none of them was the one refused.
     */
    @Test
    void aBoundaryWrittenBeforeAKeyExistedIsStillOfThisVersion() {
        Set<String> required = new LinkedHashSet<>();
        for (JsonNode each : nodeAt(schema(),
                List.of("$defs", "partition", "properties", "boundaries", "items", "required"))) {
            required.add(each.asString());
        }

        assertEquals(Set.of("axis", "origin", "value", "items"),
                required, "a boundary object written before `kind` existed carries these and no more");
    }

    /**
     * A report that carries an incompleteness says it in a word the schema allows.
     *
     * <p>The end of the same defect, from the other side. What the schema promised and what the
     * compiler wrote could disagree for as long as they did partly because the report the schema is
     * checked against is a clean compile: `incompleteness` is an empty array in it, so the field
     * where the two had come apart was never written at all.
     */
    @Test
    void anUndecidedRowIsReportedInAWordTheSchemaAllows() {
        Compilation compilation = Compilation.ofSource("""
                module example.loop

                data N = Int
                    invariant value >= 0

                data Ok = { n: Int }

                behavior go : (n: N) -> Ok
                    constructs Ok

                let go (n) = Ok { n = n.value }

                example go
                    | "one" : (N(1)) -> Ok { n = 1 }
                """, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.withJvmExampleDeadlines(
                DoesNotComeBack.overrunningOn(DoesNotComeBack.everythingAboutRowsOf("go")));
        compilation.answerEverything();

        JsonNode report = JSON.readTree(AdequacyReport.of(compilation)
                .json(souther.compiler.diag.SourceNameResolver.identity()));
        Set<String> allowed =
                allowedAt(schema(), List.of("$defs", "incompleteness", "properties", "code"));

        List<String> written = new ArrayList<>();
        for (JsonNode module : report.get("modules")) {
            for (JsonNode gap : module.get("incompleteness")) {
                written.add(gap.get("code").asString());
            }
        }
        // The word itself, and not merely a word the schema happens to allow. This is here to keep
        // one reproduction alive: a fixture that stopped producing an undecided row and produced
        // some other legitimate gap instead would go on passing while covering nothing.
        //
        // The row here is stopped by the clock it is evaluated under, which is one of the three
        // figures this compiler compares a row against, so the word is that one and not the word
        // for a row the evaluation had no answer for.
        assertEquals(List.of("row_evaluation_limit_reached"), written,
                "the row ran past its deadline, and this is what the report says about it");
        assertTrue(allowed.containsAll(written),
                "the schema allows " + allowed + " and the report writes " + written);
    }

    /**
     * The words the report writes for one enum, put through the writer's own encoder.
     *
     * <p>{@link AdequacyReport#word} rather than a second spelling rule written here. A rule written
     * here would agree with the schema while the report wrote something else entirely: what a
     * consumer reads is what the encoder produces, so that is the side a contract has to be held
     * against.
     */
    @SuppressWarnings("unchecked")
    private static Set<String> wordsOf(Class<?> source) {
        Object[] constants = source.getEnumConstants();
        if (constants == null) {
            // A reason that costs a proof to say cannot be an enum — the proof is its argument — so
            // it holds its word in a constant a reader with no instance can find. Read here rather
            // than written out here, so the schema is still held against what the writer spells.
            try {
                return Set.of(AdequacyReport.word(
                        (String) source.getField("WORD").get(null)));
            } catch (NoSuchFieldException | IllegalAccessException absent) {
                // Said below, in the words the rule is written in.
            }
            // The type stopped saying this when a field turned up whose words are a writer's and
            // not an enum's. Said here instead, because the alternative was a vocabulary with
            // neither an enum nor a written set failing as a null somewhere inside a stream.
            throw new IllegalArgumentException(source.getSimpleName()
                    + " is not an enum, so a vocabulary over it has to write its words out");
        }
        return Arrays.stream(constants)
                .map(constant -> AdequacyReport.word((Enum<?>) constant))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * The words a field is constrained to, however the schema spells the constraint.
     *
     * <p>A list of them, or a vocabulary written as another one and the words it adds. The second
     * is how a surface that admits everything another admits and one word besides says so, and
     * reading only the first would hold such a field against nothing.
     */
    private static Set<String> allowedAt(JsonNode schema, List<String> at) {
        return wordsOf(schema, nodeAt(schema, at));
    }

    private static Set<String> wordsOf(JsonNode schema, JsonNode node) {
        Set<String> out = new LinkedHashSet<>();
        if (node.has("enum")) {
            for (JsonNode each : node.get("enum")) {
                out.add(each.asString());
            }
        }
        if (node.has("const")) {
            out.add(node.get("const").asString());
        }
        if (node.has("anyOf")) {
            for (JsonNode each : node.get("anyOf")) {
                out.addAll(wordsOf(schema, each));
            }
        }
        if (node.has("$ref")) {
            String ref = node.get("$ref").asString();
            assertTrue(ref.startsWith("#/"), "a reference this cannot follow: " + ref);
            out.addAll(wordsOf(schema,
                    nodeAt(schema, List.of(ref.substring(2).split("/")))));
        }
        return out;
    }

    /** The node the keys lead to, which the schema is asserted to have. */
    private static JsonNode nodeAt(JsonNode schema, List<String> at) {
        JsonNode node = schema;
        for (String key : at) {
            // A step into a branch of a union is a number, and a union is where a shape that
            // discriminates writes its arms — so a field of one is reached the way the shape is
            // written rather than only where a shape is an object all the way down.
            node = node.isArray() ? node.get(Integer.parseInt(key)) : node.get(key);
            assertNotNull(node, "the schema has no " + String.join("/", at));
        }
        return node;
    }

    /** Every path in the schema that constrains a field to a list of words. */
    private static void collectEnums(JsonNode node, String path, List<String> out) {
        if (node.isObject()) {
            if (node.has("enum")) {
                out.add(path);
            }
            for (String name : node.propertyNames()) {
                collectEnums(node.get(name), path + "/" + name, out);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                collectEnums(node.get(i), path + "/" + i, out);
            }
        }
    }

    private static JsonNode schema() {
        try (InputStream in = AdequacyReport.class.getResourceAsStream(SCHEMA)) {
            assertNotNull(in, SCHEMA + " ships beside the compiler");
            return JSON.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }
}
