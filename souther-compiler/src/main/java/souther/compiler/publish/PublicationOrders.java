package souther.compiler.publish;

import souther.compiler.check.RuleCitation;
import souther.compiler.diag.Citation;
import souther.compiler.diag.SourcePos;
import souther.compiler.observe.Incompleteness;
import souther.compiler.observe.RunSensitivity;
import souther.compiler.partition.CompositionBudget;
import souther.compiler.partition.CompositionRepertoire;
import souther.compiler.partition.Generator;
import souther.compiler.partition.ReadingGap;
import souther.compiler.partition.RulesTaken;
import souther.compiler.query.EstablishmentGap;
import souther.compiler.query.ItemAssessment;
import souther.compiler.query.ObligationDisposition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The order a kind of reason is published in, for the kinds that arrive without one.
 *
 * <p>Not every kind. Where a plurality comes with the order the model has — the reasons a question
 * stands are the parts of the rule that raised it, and a document promises that order — nothing
 * here has anything to decide, and an order written for such a kind would answer by a precedence
 * nothing in the model decides ({@link SourceOrdered}). What is written here is for the kinds that
 * cross saying which of them hold and nothing more.
 *
 * <p>Of those, one kind, one order. A kind said in two places — an observation's code is said of a
 * reading that stopped short and again of a value nothing could read back — has one order all the
 * same, or the two lines of one block would put the same pair of reasons in two orders and nothing
 * would say which was meant.
 *
 * <p>Written here rather than read off how the constants are declared. A declaration is arranged
 * for whoever reads the code, and moving one is a change nobody expects to see in a document; read
 * off {@code values()} or off an {@code EnumSet}, it is one, and every consumer of the report has
 * to be compared against the last run to find it.
 *
 * <p>None of these orders is a rank. What they are for is that a report comes out the same twice,
 * and what a reader does with two reasons is do both.
 */
public final class PublicationOrders {

    private PublicationOrders() {}

    /**
     * The repeated fields of a document whose order this compiler decides.
     *
     * <p>Written down and not worked out from the shape of the schema. That a field holds an array
     * says nothing about where its order comes from: the behaviors of a module and the declarations
     * under one are in the order somebody wrote them, and a document that sorted those would be
     * answering by a precedence nothing in the model decides. What puts a field here is the other
     * thing — that what it holds arrives with no order of its own, so a document either decides one
     * or takes whichever a walk had.
     *
     * <p>So this is a decision about the published contract and belongs beside the orders. The
     * check that a writer of one of these goes through a crossing reads it, and a field added to a
     * document is outside that check until somebody says which of the two kinds it is.
     */
    public static final Set<String> CANONICALLY_ARRANGED_FIELDS =
            Set.of("keptOpenBy", "incompleteness");

    /**
     * What an observation met instead of a value, from what was nearest an answer to what never
     * started.
     *
     * <p>A value in hand that could not be read comes first, then the two about the row that would
     * have held one, then what the row was to be run against, then the run itself, and last the two
     * that say nothing was observed at all. So the reasons a wider budget would change are said
     * before the ones nothing about this compiler's own limits would.
     */
    private static final List<Incompleteness.Code> OBSERVATION_CODES_IN_ORDER = List.of(
            Incompleteness.Code.VALUE_UNREADABLE,
            Incompleteness.Code.VALUE_TRUNCATED,
            Incompleteness.Code.ROW_UNDECIDED,
            Incompleteness.Code.ROW_EVALUATION_LIMIT_REACHED,
            Incompleteness.Code.ANSWERER_NOT_ESTABLISHED,
            Incompleteness.Code.LINKAGE_FAILED,
            Incompleteness.Code.OBSERVATION_ABSENT,
            Incompleteness.Code.INSTRUMENTATION_ABSENT);

    /** What an observation met, wherever a document says one. */
    public static final CanonicalSelection.Order<Incompleteness.Code> OBSERVATION_CODES =
            CanonicalSelection.Order.overValues(OBSERVATION_CODES_IN_ORDER);

    /**
     * What a reason is about, from the smallest thing it can be about to the largest.
     *
     * <p>A reader working through a module reads what is true of one row before what is true of
     * everything in the file it is in, because the narrower of the two is the one they can act on
     * without reading the rest of the report.
     */
    private static final CanonicalSelection.Order<Incompleteness.Scope> SCOPES =
            CanonicalSelection.Order.overValues(List.of(
                    Incompleteness.Scope.ROW,
                    Incompleteness.Scope.POSITION,
                    Incompleteness.Scope.BEHAVIOR,
                    Incompleteness.Scope.SOURCE,
                    Incompleteness.Scope.MODULE));

    /**
     * Where one place a document writes comes in front of another: by the source it is in, then by
     * how far down and how far across, and last by whether the code is at it.
     *
     * <p>Nothing here is a rank. Two places in one file are read in the order the file is read in,
     * and the identities of two files are compared as the text they are written as — which says
     * nothing about either file except that a run comparing them again compares them the same way.
     *
     * <p>Last is the one thing a place says that is not where it is: whether the code is written
     * here, or reached from here and written where this compile holds no file. Two places alike
     * but for that are one position a reader is sent to for two reasons, and the nearer of the two
     * is said first.
     */
    static final Comparator<PublishedAt> PLACES = Comparator
            .comparing((PublishedAt each) -> each.source().value())
            .thenComparing(PublishedAt::at, SourcePos.IN_WRITTEN_ORDER)
            .thenComparingInt(PublicationOrders::whereRank)
            .thenComparing(PublicationOrders::declarationOf);

    private static int whereRank(PublishedAt place) {
        return switch (place.writtenAt()) {
            case PublishedAt.Where.Here _ -> 0;
            case PublishedAt.Where.OutOfSight _ -> 1;
        };
    }

    private static String declarationOf(PublishedAt place) {
        return switch (place.writtenAt()) {
            case PublishedAt.Where.Here _ -> "";
            case PublishedAt.Where.OutOfSight it -> it.declaration();
        };
    }

    /**
     * The one place a document sends a reader to for a fact met at several, or nothing where none
     * of them is a place.
     *
     * <p>The schema has room for one, and a fact is one fact however many readers met it — so a
     * choice is made, and it is made here rather than by whichever of them a walk reached first.
     * The first in the order above, which is the one nearest the top of the first file.
     *
     * <p>Citations that send a reader nowhere take no part. They are not places, so there is
     * nothing about them for an order to say, and a fact with none of them is a fact the document
     * writes no place for.
     */
    public static Optional<PublishedAt> placeFor(Collection<Citation> met) {
        return met.stream().map(PublishedAt::of).flatMap(Optional::stream).min(PLACES);
    }

    /**
     * The one handle a document writes for a rule met with several, or nothing where none was
     * offered.
     *
     * <p>The schema has room for one and a rule is one rule however many readers found it, so a
     * choice is made and it is made here rather than by whichever reader a walk reached first.
     *
     * <p><b>Chosen over what a document writes and never over what a citation is.</b> The order is
     * taken over the handles the offered citations come to, which is what the whole of
     * {@link PublishedRuleHandle} exists to be taken over: two that a document writes alike are one
     * value there, and two it writes apart are ordered by what it writes. Compared as citations —
     * by which reading offered one and in what order that reading met it — the choice would be the
     * one the walk registered first, which is the defect said in other words.
     *
     * <p>So {@code places} is asked, once per handle offered. A rule met at one call by two readers
     * is one citation and is asked about once; one met at two calls is two, and a document choosing
     * between them is choosing between two places it could send a reader to.
     *
     * <p><b>Which is why each is projected before any of them are compared.</b> A comparison built
     * out of a projection reads it afresh on both sides of every comparison it makes, so the
     * handle that survives a fold is resolved once per step of the fold rather than once at all —
     * and a caller whose answer to where a rule is takes a question of its own would ask it that
     * many times. What is folded here is the pairs, so what is asked is what the sentence above
     * says is asked.
     */
    public static Optional<RuleCitation> handleFor(Collection<RuleCitation> offered,
                                                   PublishedRuleHandle.WhereARuleIs places) {
        return offered.stream()
                .map(cited -> Map.entry(cited, PublishedRuleHandle.of(cited, places)))
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey);
    }

    /**
     * What a document says a module could not read, from the narrowest thing that went unread to
     * the widest, and within one word from the first place in the model to the last.
     *
     * <p>The array's unit is the fact, so two entries a reader can tell apart are two entries and
     * an order over them has to tell them apart as well. What tells two of these apart is what a
     * reader is shown: what happened, what it happened to, and where to look — so that is the key,
     * whole, and nothing that went into deciding it is part of the comparison.
     *
     * <p>A fact the document writes no place for comes before one it does, at the same word about
     * the same thing. There is only ever one of each such pair, since the two would be one fact.
     */
    public static final CanonicalArrangement.Order<PublishedIncompleteness> WHAT_WENT_UNREAD =
            CanonicalArrangement.Order.by(Comparator
                    .comparingInt((PublishedIncompleteness each) ->
                            SCOPES.rankOf(each.fact().scope()))
                    .thenComparingInt(each -> OBSERVATION_CODES.rankOf(each.fact().code()))
                    .thenComparing(each -> each.fact().subject())
                    .thenComparing(PublishedIncompleteness::at,
                            Comparator.comparing(at -> at.orElse(null),
                                    Comparator.nullsFirst(PLACES))));

    /**
     * What a reading of a number met instead of one.
     *
     * <p>Composed from the order above and not written again. A reading that met an observation's
     * code is that code, so the two orders agreeing is not something to keep in step — there is one
     * order, and this is it with the one reason that is no observation's put after them. A walk
     * that reached no value is last for the same reason the codes are in the order they are: it is
     * the furthest from an answer. The two that never reached a value to begin with follow it, a
     * step further out again — a position that was read and holds nothing is nearer a number than a
     * position nothing arrived at, and a walk that was refused is nearer than a row that never came.
     */
    public static final CanonicalSelection.Order<ReadingGap> READING_GAPS =
            CanonicalSelection.Order.overValues(everyReadingGap());

    private static List<ReadingGap> everyReadingGap() {
        List<ReadingGap> out = new ArrayList<>();
        for (Incompleteness.Code code : OBSERVATION_CODES_IN_ORDER) {
            out.add(ReadingGap.of(code));
        }
        out.add(ReadingGap.NO_VALUE);
        out.add(ReadingGap.COULD_NOT_WALK);
        out.add(ReadingGap.COULD_NOT_READ_ROW);
        return out;
    }

    /**
     * What this compiler declined to do, from what bounds one value to what bounds a whole search.
     *
     * <p>Nearest the value a reader wanted first. What one proposed value is worth building comes
     * before what one total is offered as, and both before what the search spends over everything
     * it tries — so the figure they would raise to get the value in front of them is said before
     * the ones that bound the work around it. The two that bound no search come last, as the ones
     * furthest from the value a reader wanted: how far a body is read for what it decides, and how
     * far this compiler looks before it has anything at all.
     */
    public static final CanonicalSelection.Order<CompositionBudget> COMPOSITION_BUDGETS =
            CanonicalSelection.Order.overValues(List.of(
                    CompositionBudget.ELEMENTS_A_PROPOSAL_HOLDS,
                    CompositionBudget.CHARACTERS_A_PROPOSAL_HOLDS,
                    CompositionBudget.PAIRINGS_BUILT_AT_ONCE,
                    CompositionBudget.ELEMENTS_A_TOTAL_IS_SPREAD_OVER,
                    CompositionBudget.SHAPES_OF_A_TOTAL_OFFERED,
                    CompositionBudget.WAYS_DOWN_TO_A_TOTAL_TRIED,
                    CompositionBudget.VALUES_OF_AN_UNBOUNDED_PROGRESSION_TRIED,
                    CompositionBudget.PLACES_A_PAIR_IS_TRIED_AT,
                    CompositionBudget.VALUES_A_POSITION_ON_THE_WAY_IS_TRIED_AT,
                    CompositionBudget.LEVELS_A_SIDE_IS_ASKED_AT,
                    CompositionBudget.ASSIGNMENTS_A_SEARCH_COMPOSES,
                    CompositionBudget.TIMES_THE_RULES_ARE_ASKED_AGAIN,
                    CompositionBudget.STEPS_A_SEARCH_MAY_TAKE,
                    CompositionBudget.PATHS_OF_A_DECISION_READ,
                    CompositionBudget.DEPTH_A_CONSTRUCTION_PLAN_DESCENDS));

    /**
     * What this compiler writes some of rather than all of, in the order a reader meets them.
     *
     * <p>Its own order and not the figures'. Reaching a figure and writing some of a population are
     * different things to be told — one is a number to raise and the other is work nobody has done
     * — so an order over the two together would be arranging a sentence out of two vocabularies.
     */
    public static final CanonicalSelection.Order<CompositionRepertoire> COMPOSITION_REPERTOIRES =
            CanonicalSelection.Order.overValues(
                    List.of(CompositionRepertoire.WAYS_A_TOTAL_IS_SPREAD));

    /**
     * What stopped this compiler showing a row can be written, by how far it had got.
     *
     * <p>A value that was built and did not come back whole is nearer an answer than one that was
     * never built, which is the order the reasons inside each of them are in as well.
     *
     * <p>The arms and not what they hold. Which observation codes an arm says, which figures and
     * which populations, are the orders above; said again here they would be a second order over
     * kinds that have one.
     */
    public static final CanonicalSelection.Order<EstablishmentGap> ESTABLISHMENT_GAPS =
            CanonicalSelection.Order.overFamilies(List.<Class<? extends EstablishmentGap>>of(
                    EstablishmentGap.Observation.class,
                    EstablishmentGap.Composition.class));

    /**
     * What is open about an obligation nobody can decide.
     *
     * <p>What a reader does about the two differs — the first is answered by reading more of what
     * is written and the second is not work an author can do — and the first is said first because
     * it is the one they can act on.
     *
     * <p>The questions and not the answers. Each of the two is open for more than one reason, and a
     * place per reason would say twice what the sentence says once.
     */
    public static final CanonicalSelection.Order<ObligationDisposition.Uncertainty> OPEN_QUESTIONS =
            CanonicalSelection.Order.overFamilies(
                    List.<Class<? extends ObligationDisposition.Uncertainty>>of(
                            ObligationDisposition.Uncertainty.WhetherARowIsThere.class,
                            ObligationDisposition.Uncertainty.WhetherARowCanBeWritten.class));

    /**
     * What a document says one measurement went without.
     *
     * <p>Two vocabularies in one array ({@link WeakeningVocabulary}), and one order over the pair.
     * The observation codes come first and in the order they are said in everywhere else, then the
     * words this document has of its own — a value that was read and did not come back whole is
     * nearer an answer than a reading that never happened, which is the principle the codes are
     * already in the order of.
     *
     * <p>The words of this document's own are in the order the things they are about are met: what
     * a row came back with, then what was read of the model, then what the rules left, then what a
     * proof or an arm came to.
     */
    public static final CanonicalSelection.Order<WeakeningVocabulary> WEAKENING_WORDS =
            CanonicalSelection.Order.overValues(everyWeakeningWord());

    private static List<WeakeningVocabulary> everyWeakeningWord() {
        List<WeakeningVocabulary> out = new ArrayList<>();
        for (Incompleteness.Code code : OBSERVATION_CODES_IN_ORDER) {
            out.add(new WeakeningVocabulary.AnObservationCode(code));
        }
        for (WeakeningWord word : List.of(
                WeakeningWord.OUTPUT_CASES_UNREADABLE,
                WeakeningWord.INPUT_CASES_UNREADABLE,
                WeakeningWord.BORDER_VALUE_UNREADABLE,
                WeakeningWord.BORDER_VALUE_ABSENT,
                WeakeningWord.BORDER_OBSERVATION_UNAVAILABLE,
                WeakeningWord.BODIES_NOT_ELABORATED,
                WeakeningWord.BEHAVIOR_INPUT_NOT_READ,
                WeakeningWord.BEHAVIOR_BOUNDARY_NOT_DERIVED,
                WeakeningWord.RULE_UNREAD,
                WeakeningWord.POSITION_NOT_READ,
                WeakeningWord.RULES_NOT_REACHED,
                WeakeningWord.QUESTION_UNANSWERED,
                WeakeningWord.PAIR_SPACE_TRUNCATED,
                WeakeningWord.PROOF_CONTRADICTED,
                WeakeningWord.ARMS_UNSETTLED,
                WeakeningWord.DECISION_NOT_FULLY_READ,
                WeakeningWord.DECISION_OF_ROW_UNREADABLE,
                WeakeningWord.DECISION_RUN_NOT_WATCHED)) {
            out.add(new WeakeningVocabulary.AWordOfThisDocuments(word));
        }
        return out;
    }

    /**
     * The ways a verdict stays open that no weakening covers, from the measure nobody made to the
     * point nothing was even attempted at.
     *
     * <p>A measure that was never made comes first because it is the one an author acts on by
     * asking for it. Then the point a row is owed at, and of the three words for that, the two that
     * say something was tried before the one that says nothing was: a reader sent after what
     * stopped a showing has something to find, and a reader told nothing showed it has not.
     */
    private static final CanonicalSelection.Order<AdequacyOpeningWord> OPENING_WORDS =
            CanonicalSelection.Order.overValues(List.of(
                    AdequacyOpeningWord.NOT_MEASURED,
                    AdequacyOpeningWord.SHOWING_STOPPED,
                    AdequacyOpeningWord.NOTHING_WAS_COMPOSED,
                    AdequacyOpeningWord.NOTHING_SHOWED_IT));

    /**
     * Why a measure the verdict rests on was never made, from what an author can do about it to
     * what this build decided.
     *
     * <p>The words the schema allows and not the arms that produce them: several measures say
     * {@code no_rows}, and a place per measure would be an order over which of them a walk reached.
     * Rows the model does not have are a change an author makes, and a measure this build did not
     * ask for is a change to how it was run, so the first is said first.
     */
    private static final CanonicalSelection.Order<NotMeasuredWord> NOT_MEASURED_REASONS =
            CanonicalSelection.Order.overValues(List.of(
                    NotMeasuredWord.NO_ROWS,
                    NotMeasuredWord.NOT_ASKED,
                    NotMeasuredWord.ARMS_NOT_ASKED));

    /**
     * Where one kind of subject sits, in the order the words are declared.
     *
     * <p>Any order over them would do, since no reader acts on which kind sorts first. What it is
     * for is that two entries alike in everything else are told apart by what they are about, and
     * an order that stopped short of them would leave that to whichever was handed over first.
     */
    private static final CanonicalSelection.Order<SubjectWord> SUBJECT_WORDS =
            CanonicalSelection.Order.overValues(List.of(SubjectWord.values()));

    /** Whether a wider run could answer it: the one it could, first. */
    private static final CanonicalSelection.Order<RunSensitivity> RUN_SENSITIVITIES =
            CanonicalSelection.Order.overValues(List.of(
                    RunSensitivity.MAY_CHANGE, RunSensitivity.UNAFFECTED));

    /**
     * What a document says holds a verdict open, by what kind of thing each is.
     *
     * <p>The words a measurement went without first, in the order that array is already written in,
     * and then the words of this array's own. A reader works through what was measured and fell
     * short before what was never measured at all, because the first is the compiler saying how far
     * it got and the second is it saying it did not start.
     *
     * <p>Then the reason, where the kind has one, and last whether a wider run could answer it. Two
     * entries alike in all three are two entries a document writes identically, and which of them
     * comes first is nothing a reader can see.
     */
    public static final CanonicalArrangement.Order<PublishedOpening> WHAT_HOLDS_A_VERDICT_OPEN =
            CanonicalArrangement.Order.by(Comparator
                    .comparingInt((PublishedOpening each) -> kindRank(each.kind()))
                    .thenComparingInt(each -> each.reason()
                            .map(NOT_MEASURED_REASONS::rankOf).orElse(-1))
                    .thenComparingInt(each -> RUN_SENSITIVITIES.rankOf(each.runSensitivity()))
                    // And what it is about, which is what tells apart entries alike in all of the
                    // above. Taken over the identity and never over a reader-facing spelling: two
                    // positions one path spells alike are two subjects, and an order reading the
                    // path would leave them to whichever arrived first.
                    .thenComparingInt(each -> SUBJECT_WORDS.rankOf(each.about().kind()))
                    .thenComparing(each -> each.about().identity()));

    /** Where one kind sits, over the two vocabularies as one sequence. */
    private static int kindRank(PublishedOpening.Kind kind) {
        return switch (kind) {
            case PublishedOpening.Kind.AWeakening it -> WEAKENING_WORDS.rankOf(it.said());
            case PublishedOpening.Kind.AnOpening it ->
                    WEAKENING_WORDS.slots().size() + OPENING_WORDS.rankOf(it.said());
        };
    }

    /**
     * What has shown a row can be written at a point.
     *
     * <p>The one ground that is about the model first, then the two that are about this run, and of
     * those the one a row already answers before the one a value was built for. So a reader sees
     * what stands whatever this run did before what this run happened to reach.
     */
    public static final CanonicalSelection.Order<ItemAssessment.WritabilityEvidence.Ground>
            WRITABILITY_GROUNDS = CanonicalSelection.Order.overValues(List.of(
                    ItemAssessment.WritabilityEvidence.Ground.THE_RULES_PROVE_IT,
                    ItemAssessment.WritabilityEvidence.Ground.A_ROW_IS_AT_IT,
                    ItemAssessment.WritabilityEvidence.Ground.A_VALUE_WAS_BUILT));

    /**
     * Why nothing was read against a point, over every reading of the line it is on.
     *
     * <p>What the run asked for before what one behavior turned out to have. A reader told that
     * this build measured nothing has been told why every line of it says so, and what a single
     * behavior has no rows for is the narrower fact under it.
     */
    public static final CanonicalSelection.Order<ItemAssessment.Coverage.NotAsked> UNASKED_REASONS =
            CanonicalSelection.Order.overValues(List.of(
                    ItemAssessment.Coverage.NotAsked.NOT_ASKED,
                    ItemAssessment.Coverage.NotAsked.ARMS_NOT_ASKED,
                    ItemAssessment.Coverage.NotAsked.NO_ROWS));

    /**
     * Where one of the words a composing that came to nothing gives is said among the others.
     *
     * <p>A page gathers what it will not write out under these, so several of them stand together
     * and the order they stand in is a decision like every other one here. Said as a switch rather
     * than as a list, because the compiler is then what holds it total: a word added to the
     * vocabulary is one somebody places, not one that arrives wherever the enumeration puts it.
     *
     * <p>From what was tried and came to nothing, through what this compiler does not write, to
     * what was never asked for at all. So a reader who could act on something is told it before the
     * ones nothing of theirs reaches. It is not a rank: what a reader does with two of these is
     * look at both.
     */
    public static int positionOf(Generator.UnresolvedCombination.Reason reason) {
        return switch (reason) {
            case ALL_CANDIDATES_REJECTED -> 0;
            // Beside it, because it is the same news less one claim, and a reader meeting both is
            // reading about the same refusals: these were refused too, and what they were is not
            // everything the position had.
            case NOT_ALL_CANDIDATES_COULD_BE_OFFERED -> 1;
            case THE_RULES_LEAVE_NOTHING_THERE -> 2;
            case ONE_POSITION_CANNOT_BE_BOTH -> 3;
            case NOTHING_COMPOSES_ONE -> 4;
            case NOTHING_STANDS_IN_FOR_A_DEPENDENCY -> 5;
            case A_TABLE_IS_WHAT_THIS_NEEDS -> 6;
            case NOTHING_TO_BUILD_AGAINST -> 7;
            case THE_WAY_IN_PLACES_AT_NO_CLASS -> 8;
            case THE_SEARCH_LEFT_SOMETHING_UNTRIED -> 9;
            case THE_BLOCK_IS_AS_LONG_AS_IT_MAY_BE -> 10;
            case THE_GROUP_WAS_NOT_OFFERED -> 11;
            case THE_POSITION_WAS_WITHHELD -> 12;
            case NO_VALUES_WERE_ASKED_FOR -> 13;
            case NO_CANDIDATE_WAS_OFFERED -> 14;
            case NO_CERTIFIED_WITNESS -> 15;
            case NO_READING_OF_THE_LINE_COULD_BE_SEARCHED -> 16;
            case THE_ROWS_WERE_NOT_READ -> 17;
            case LINKAGE_FAILED -> 18;
        };
    }

    /**
     * Where one of the ways a run's rule could not be told is said among the others.
     *
     * <p>From the reading having nothing to recognise a run by, through a run it recognised and no
     * rule matched, to one that matched more than one. The same decision as the order above and
     * held the same way.
     */
    public static int positionOf(RulesTaken.WhichRule.Why why) {
        return switch (why) {
            case NO_RULE_IS_RECOGNISABLE -> 0;
            case NO_RECOGNISABLE_RULE_MATCHES -> 1;
            case MORE_THAN_ONE_RULE_MATCHES -> 2;
        };
    }
}
