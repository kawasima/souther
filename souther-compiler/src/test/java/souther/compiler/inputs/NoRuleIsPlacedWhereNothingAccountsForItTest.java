package souther.compiler.inputs;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredSig;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.conformance.RepositoryModels;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every rule that placed anything ends somewhere this compiler said out loud.
 *
 * <p>A rule is written in one value's words and a row is written at a position, so what a build has
 * to account for is not what it produced but what the model gave it. Counted the first way, a rule
 * this compiler had nowhere to put is a rule nobody wrote — which is how a field every case of a sum
 * spreads came to be bounded by a clause no report ever mentioned.
 *
 * <p><b>What the answers are allowed to be is written out below.</b> A cause this has not met is a
 * failure here rather than a line in a report nobody looked at: the day one turns up, somebody comes
 * and decides what a reader should be told about it. So a shorter list is a failure as much as a
 * longer one.
 */
@Tag("population")
class NoRuleIsPlacedWhereNothingAccountsForItTest {

    /**
     * Where a placement ends with nowhere to go, over every model this repository carries.
     *
     * <p>Empty, and that is the measurement. Every path these models write opens each declaration
     * at most once, so every one of them is followed to its end and every rule written along it is
     * placed at a position — a rule is not left unread because of how deeply an author nested the
     * value it is about.
     *
     * <p>A model whose input returns to a declaration it has already opened would put an entry here,
     * and it is a failure until somebody decides what a reader should be told about it. So a model
     * of that shape is not something this can absorb quietly.
     *
     * <p>Nothing else, and there is nothing else it could be. A name a rule wrote that reaches a
     * position this reading did not stop at and puts no such name under is this reading disagreeing
     * with the language about what may be written, and it is refused where it arises rather than
     * carried here.
     */
    private static final Map<String, Integer> ALLOWED = Map.of();

    /**
     * What a value's rules raised and what this build takes as placed are the same, one for one.
     *
     * <p>The half the outcomes cannot say anything about. A filing comes back with an answer for
     * every place its name reaches, so nothing that is taken as a placement is lost after that — but
     * a placement never taken is one no filing was ever made for, and counting the filings would
     * find nothing missing.
     *
     * <p>As a multiset and not as a total. One dropped and one counted twice come to the same
     * number, and the point of taking the account rule by rule was that two placements of one field
     * are two.
     */
    @Test
    void whatTheRulesRaisedIsWhatThisBuildTakesAsPlaced() throws Exception {
        for (PlacedRules rules : everyValueRead()) {
            Map<SourcePlacement, Integer> raised = new LinkedHashMap<>();
            rules.bounds().accounting().forEach((rule, accounting) ->
                    accounting.answers().keySet().forEach(owed ->
                            raised.merge(new SourcePlacement(rule, owed), 1, Integer::sum)));
            Map<SourcePlacement, Integer> taken = new LinkedHashMap<>();
            rules.placed().forEach(seed -> taken.merge(
                    new SourcePlacement(seed.by(), questionOf(seed)), 1, Integer::sum));

            assertEquals(raised, taken,
                    () -> "every question the rules of " + rules.root() + " raised is one this "
                            + "build takes as placed, and each of them once");
        }
    }

    /** One rule and one question it raised, which is what a placement is one of. */
    private record SourcePlacement(souther.compiler.check.RuleRef rule,
                                   souther.compiler.check.Owed owed) {}

    /**
     * The question a seed was made from, put back together.
     *
     * <p>The question and not how either side happens to print it: a seed is an address and what is
     * said at it, and putting the two back is what makes the comparison one about the questions
     * rather than about two spellings agreeing.
     */
    private static souther.compiler.check.Owed questionOf(PlacementSeed seed) {
        return switch (seed.placed()) {
            case PlacementSeed.Placed.TheValuesThere _ ->
                    new souther.compiler.check.Owed.AdmittedValues(seed.address().key());
            case PlacementSeed.Placed.ANumberOfIt it -> new souther.compiler.check.Owed.Boundary(
                    new souther.compiler.check.NumberAt<>(seed.address().key(),
                            it.which()));
        };
    }

    @Test
    void everyRuleThatPlacedAnEndIsInTheAccount() throws Exception {
        for (PlacedRules rules : everyValueRead()) {
            java.util.Set<souther.compiler.check.RuleRef.Invariant> counted =
                    rules.bounds().accounting().keySet();
            for (souther.compiler.check.FieldDomains.Placed each : rules.bounds().placed()) {
                assertTrue(counted.contains(each.part().rule()),
                        () -> "`" + each.part().rule() + "` placed an end at " + each.path()
                                + " and is not among the rules this build accounts for");
            }
        }
    }

    @Test
    void everyPlacementEndsInAnAnswerThisCompilerGave() throws Exception {
        Map<String, Integer> found = new TreeMap<>();
        for (InputDomain read : everyReading()) {
            for (PlacementFiling filing : read.placements()) {
                assertFalse(filing.outcomes().isEmpty(),
                        () -> "a placement with no outcome cannot be built: " + filing.seed());
                for (PlacementOutcome outcome : filing.outcomes()) {
                    if (outcome instanceof PlacementOutcome.Unresolved it) {
                        found.merge(spelled(it.why()), 1, Integer::sum);
                    }
                }
            }
        }
        assertEquals(new TreeMap<>(ALLOWED), found,
                "every way a rule this repository's models write ends up with nowhere to go. A "
                        + "cause not written down here is one nobody has decided what to tell a "
                        + "reader about");
    }

    /** How a report would have to tell them apart, which is what a new one has to be given. */
    private static String spelled(PlacementOutcome.Reason why) {
        return switch (why) {
            case PlacementOutcome.Reason.TheReadingStoppedThere it ->
                    "the reading stopped there: " + it.why().getClass().getSimpleName();
        };
    }

    /**
     * A model whose clauses name fields through a sum, which the corpora do not have.
     *
     * <p>Here so that the count above is answerable by the crossing rather than by there being
     * nothing to cross. Without it every rule in every model this repository carries names a
     * position directly or names something deeper than the reading goes, and the account would come
     * out the same whether or not a name written at a sum reached anywhere — which is a test that
     * holds for a reason other than the one it states.
     */
    private static final String NAMES_THROUGH_A_SUM = """
            module ledger.crossed

            data Paging = { limit: Int }
            data Named = { name: String }
            data A = { ...Paging, ...Named, x: Int }
            data B = { ...Paging, ...Named, y: Int }
            data Q = A | B

            data Colour = Red | Green
            data Red
            data Green

            data Holder = { q: Q, cap: Int }
                invariant capped = q.limit <= 10
                invariant long = String.length(q.name) >= 3

            data Ok

            behavior read : (h: Holder) -> Ok
            behavior atTheSum : (q: Q) -> Ok
            """;

    /**
     * The models asked about here: what the repository carries, and one this class writes.
     *
     * <p>The written one crosses a name through a sum, which the corpora do not, and the questions
     * below are about every model that reaches them either way. Answered once for the class, as the
     * repository's are, because each question here asks about all of them.
     */
    private static final List<Compilation> ASKED_ABOUT = askedAbout();

    private static List<Compilation> askedAbout() {
        List<Compilation> out = new ArrayList<>(RepositoryModels.all());
        Compilation crossed = Compilation.ofSources(List.of(NAMES_THROUGH_A_SUM), ModulePath.EMPTY);
        crossed.answerEverything();
        out.add(crossed);
        return List.copyOf(out);
    }

    /** The reading of every behavior of every model this repository carries. */
    private static List<InputDomain> everyReading() {
        List<InputDomain> out = new ArrayList<>();
        for (Compilation compilation : ASKED_ABOUT) {
            readings(compilation, out);
        }
        return out;
    }

    /** The rules of every value the models open, once they have been read. */
    private static List<PlacedRules> everyValueRead;

    /**
     * The rules of every value every reading of this repository's models opens.
     *
     * <p>Read once for the class. Two questions here walk these and neither changes one, so
     * reading them per question is the same work over — and the models are compiled once for the
     * JVM already, which is the same arrangement one step further down.
     *
     * <p>Read when a question asks rather than while the class is initialised: a population that
     * cannot be read is what this class is about, and an initialiser that threw reports it on every
     * method at once and names no cause.
     */
    private static synchronized List<PlacedRules> everyValueRead() {
        if (everyValueRead != null) {
            return everyValueRead;
        }
        List<PlacedRules> out = new ArrayList<>();
        for (Compilation compilation : ASKED_ABOUT) {
            valuesRead(compilation, out);
        }
        everyValueRead = List.copyOf(out);
        return everyValueRead;
    }

    /** And given back when the class is done with them, as the readings of a corpus are: a fork
     *  keeps its JVM, so what is held statically is held for every class after this one. */
    @AfterAll
    static void released() {
        everyValueRead = null;
    }

    private static void valuesRead(Compilation compilation, List<PlacedRules> out) {
        for (String module : compilation.modules()) {
            Map<String, DeclaredSig> sigs =
                    compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
            RuleReadingSource rules = RuleReadings.of(compilation, module);
            for (DeclaredSig declared : sigs.values()) {
                for (DeclaredSig.Input input : declared.inputs()) {
                    out.add(PlacedRules.of(TermPath.of("p"), input.type(), rules,
                            ReadAs.THE_COMPILATION_DOES));
                }
            }
        }
    }

    private static void readings(Compilation compilation, List<InputDomain> out) {
        for (String module : compilation.modules()) {
            Map<String, DeclaredSig> sigs =
                    compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
            RuleReadingSource rules = RuleReadings.of(compilation, module);
            for (DeclaredSig declared : sigs.values()) {
                out.add(InputDomain.of(declared, rules, ReadAs.THE_COMPILATION_DOES));
            }
        }
    }
}
