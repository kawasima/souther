package souther.compiler.publish;

import org.junit.jupiter.api.Test;

import souther.compiler.check.BehaviorContract;
import souther.compiler.check.Clause;
import souther.compiler.check.ClauseName;
import souther.compiler.check.RuleCitation;
import souther.compiler.check.RuleReportAnchor;
import souther.compiler.check.RuleRef;
import souther.compiler.diag.Citation;
import souther.compiler.diag.SourceNameResolver;
import souther.compiler.Compiler;
import souther.compiler.diag.SourcePos;
import souther.compiler.jvm.ClassFileImage;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;
import souther.compiler.source.SourceId;
import souther.compiler.types.SourceConstruct;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;
import souther.compiler.types.ValueName;
import souther.compiler.types.WrittenOwner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two handles a document writes apart are two values, and two it writes alike are one.
 *
 * <p>What this type is for. Choosing one of several takes a comparison, and a comparison that comes
 * out equal for two handles a reader can tell apart leaves the choice to whichever the set of them
 * iterated first — a document whose sentence turns on the order a walk happened to take.
 *
 * <p>So the property is the one the projection has to keep, in both directions. Two handles that are
 * one value are written the same way, which is what lets either be chosen; two that are written
 * differently are not one value, which is what stops the choice from being arbitrary. A rule's kind
 * is in the sentence, so it is in the projection — left out, a comparison and a rule about the
 * strings at a position written in one place came to one value and were printed two ways.
 *
 * <p>And the order agrees with the equality, because both are asked of the same value. A comparison
 * answering zero where {@code equals} answers false is a pair the sort calls interchangeable and the
 * reader does not.
 */
class TwoHandlesADocumentWritesApartAreTwoValuesTest {

    private static final SourceId WHERE = new SourceId("0");

    private static final Citation AT = Citation.of(new SourcePos(9, 1, WHERE));

    /**
     * Where each way in this population uses leads, which is what a citation no longer carries.
     *
     * <p>The places are addressed rather than held, as the readings that make one address them: a
     * rule a reader can open is placed by whoever wrote it and has one way in, and a rule out of
     * sight has one per call. So the population varies the place by varying which way in it is,
     * which is the only way a document can be given two places for one rule.
     */
    private static final List<Citation> WAYS_IN = waysIn();

    private static final PublishedRuleHandle.WhereARuleIs PLACES = cited -> switch (cited.anchor()) {
        case RuleReportAnchor.ByTheModuleThatWroteIt _ -> AT;
        case RuleReportAnchor.ByTheReadingThatMetIt(String _, String _, int reach) ->
                WAYS_IN.get(reach);
    };

    /** A place in the file this holds, another line of it, a position in a text nothing names, and
     *  the ones a compilation reached — every shape of place a sentence is written from. */
    private static List<Citation> waysIn() {
        List<Citation> out = new ArrayList<>(List.of(
                AT,
                Citation.of(new SourcePos(10, 1, WHERE)),
                Citation.of(new SourcePos(9, 1))));
        out.addAll(reachedFromHere());
        return List.copyOf(out);
    }

    /** The way in {@code reach} names, as a citation of {@code rule}. */
    private static RuleCitation metAt(RuleRef.Written rule, int reach) {
        return new RuleCitation.Written(rule,
                new RuleReportAnchor.ByTheReadingThatMetIt("m", "b", reach));
    }

    /**
     * Two rules with no name, written at one place, of the two kinds there are.
     *
     * <p>One place, because what is being asked is whether the kind survives the projection. Told
     * apart by where they are, this pair would say nothing about the word.
     */
    private static final RuleRef.Comparison COMPARISON = new RuleRef.Comparison("b",
            new SourceConstructOrigin(new WrittenOwner.Body("m", "b"), 0, 0,
                    SourceConstruct.BINARY));

    private static final RuleRef.Predicate PREDICATE = new RuleRef.Predicate("b",
            new SourceConstructOrigin(new WrittenOwner.Body("m", "b"), 1, 0,
                    SourceConstruct.CALL));

    @Test
    void aComparisonAndAPredicateAtOnePlaceAreTwoHandles() {
        PublishedRuleHandle comparison =
                PublishedRuleHandle.of(metAt(COMPARISON, 0), PLACES);
        PublishedRuleHandle predicate =
                PublishedRuleHandle.of(metAt(PREDICATE, 0), PLACES);

        assertNotEquals(comparison, predicate,
                "a reader sent to a line and a reader sent to a set are told two things");
        assertNotEquals(0, Integer.signum(comparison.compareTo(predicate)),
                "so the order tells them apart rather than calling either the one to write");
    }

    /**
     * And which of the two a caller had first decides nothing.
     *
     * <p>The failure this is about, said as what a run does: the same pair, offered the other way
     * round, comes back with the same answer.
     */
    @Test
    void whichOfThemACallerHadFirstDecidesNothing() {
        RuleCitation comparison = metAt(COMPARISON, 0);
        RuleCitation predicate = metAt(PREDICATE, 0);

        assertEquals(PublicationOrders.handleFor(List.of(comparison, predicate), PLACES),
                PublicationOrders.handleFor(List.of(predicate, comparison), PLACES),
                "the handle a document writes is not the one a set of them iterated first");
    }

    /**
     * Two handles that are equal are written the same way, and two that are not are not.
     *
     * <p>The property in full, over a population that varies each part of each kind of sentence in
     * turn. A part left out of the projection shows up as two of these being one value while the
     * two sentences differ.
     */
    @Test
    void twoHandlesAreOneValueExactlyWhereADocumentWritesThemAlike() {
        List<PublishedRuleHandle> population = everyHandle();
        for (PublishedRuleHandle here : population) {
            for (PublishedRuleHandle there : population) {
                boolean written = said(here).equals(said(there));

                assertEquals(written, here.equals(there),
                        () -> "one value exactly where a document writes them alike: "
                                + said(here) + " and " + said(there));
                assertEquals(written, here.compareTo(there) == 0,
                        () -> "and the order agrees with the equality: "
                                + said(here) + " and " + said(there));
            }
        }
    }

    /**
     * The handles the property above is asked over.
     *
     * <p>What a compilation offers, and beside it the one form no compilation here writes: code out
     * of sight with no position at all is met where a body is put back together out of what a module
     * published, which none of these fixtures does. Left to the fixtures, that arm would be the one
     * whose ordering nothing asks about — and it was, while it was an arm of the place instead of a
     * sentence of its own and the checks below were satisfied by the placed one beside it.
     */
    private static List<PublishedRuleHandle> everyHandle() {
        List<PublishedRuleHandle> out = new ArrayList<>(everyShapeOfSentence().stream()
                .map(cited -> PublishedRuleHandle.of(cited, PLACES)).toList());
        for (PublishedRuleKind kind : PublishedRuleKind.values()) {
            out.add(new PublishedRuleHandle.ReachedOutOfSight(kind, "Int.clamp"));
            out.add(new PublishedRuleHandle.ReachedOutOfSight(kind, "Int.abs"));
        }
        return List.copyOf(out);
    }

    /**
     * One handle of every shape of sentence a document writes.
     *
     * <p>Varied one part at a time: the kind of rule, whose rule it is, and where it is. Two of
     * these that a document writes alike are the pair either of which may be chosen, and every
     * other pair is one it has to tell apart.
     *
     * <p>Code reached from here is among them, taken from a compilation rather than written. A
     * citation of it is made where a source is placed and that is the one way there is, on purpose —
     * so what this needs is one of the real ones, and a handle is then made of it and each kind of
     * rule. Left out because it could not be written, the arm this type has for it would be the one
     * arm whose reason for existing nothing checks.
     */
    private static List<RuleCitation> everyShapeOfSentence() {
        RuleRef.Named named = new RuleRef.Invariant(new Clause.Ref(
                new Clause.Id(TypeSymbols.declared(new TypeKey("m", "Amount")), 0),
                Optional.of(new ClauseName("cap"))));
        RuleRef.Named alsoNamed = new RuleRef.Invariant(new Clause.Ref(
                new Clause.Id(TypeSymbols.declared(new TypeKey("m", "Amount")), 1),
                Optional.of(new ClauseName("floor"))));
        // A clause the author named nothing, which a reader counts to, and the two sentences an
        // `ensures` has: the words the author gave the clause, and a clause over every answer that
        // the behavior's name is the whole of.
        RuleRef.Named counted = new RuleRef.Invariant(new Clause.Ref(
                new Clause.Id(TypeSymbols.declared(new TypeKey("m", "Amount")), 2),
                Optional.empty()));
        RuleRef.Named clauseOfAnEnsures = new RuleRef.Ensures(
                new BehaviorContract.RuleId(new ValueName.Behavior("m", "b"), 0, 0, null), "c");
        RuleRef.Named everyAnswer = new RuleRef.Ensures(
                new BehaviorContract.RuleId(new ValueName.Behavior("m", "b"), 0, 0, null), "");
        List<RuleCitation> out = new ArrayList<>(List.of(
                new RuleCitation.Named(named),
                new RuleCitation.Named(alsoNamed),
                new RuleCitation.Named(counted),
                new RuleCitation.Named(clauseOfAnEnsures),
                new RuleCitation.Named(everyAnswer),
                new RuleCitation.Written(COMPARISON,
                        new RuleReportAnchor.ByTheModuleThatWroteIt()),
                metAt(COMPARISON, 0),
                metAt(PREDICATE, 0),
                metAt(COMPARISON, 1),
                metAt(COMPARISON, 2)));
        // And every way in a compilation offered, of each kind of rule — which is where a sentence
        // about code out of sight comes from.
        for (int reach = 3; reach < WAYS_IN.size(); reach++) {
            out.add(metAt(COMPARISON, reach));
            out.add(metAt(PREDICATE, reach));
        }
        return List.copyOf(out);
    }

    /**
     * That the population varies each part of a sentence about code reached from here.
     *
     * <p>Said out loud because the sentence has three parts and only one of them varies by being
     * put there: a handle is made of a citation and a rule, so the kind varies by pairing the same
     * citation with both, and the place and what reaches it vary only if the compilation happened to
     * offer two that differ. Left to happen, a comparison of that part could be dropped and this
     * would stay green on whatever the fixture came back with.
     */
    @Test
    void thePopulationVariesEachPartOfASentenceAboutCodeReachedFromHere() {
        List<PublishedRuleHandle.Reached> reached = everyShapeOfSentence().stream()
                .map(cited -> PublishedRuleHandle.of(cited, PLACES))
                .filter(PublishedRuleHandle.Reached.class::isInstance)
                .map(PublishedRuleHandle.Reached.class::cast).toList();

        assertTrue(variedAlone(reached, Part.WHAT_THE_RULE_IS),
                () -> "two that differ in what the rule is and in nothing else: " + reached);
        assertTrue(reached.stream().map(Part.WHAT_REACHES_IT::of).distinct().count() > 1,
                () -> "and two reached by different code: " + reached);
    }

    /**
     * And each part of such a sentence on its own tells two handles apart.
     *
     * <p>Built here rather than taken from a compilation. Where the code is and what reaches it move
     * together in what a compile offers — a second declaration is a second place — so a pair varying
     * one of them alone is not something a model can be written to produce, and a property left to
     * what a fixture happens to emit is a property about the fixture.
     *
     * <p>Which is the whole of what this adds: the parts a document writes are the parts the order
     * is over, so dropping any one of them from the comparison makes two sentences a reader can tell
     * apart into one value.
     */
    @Test
    void eachPartOfSuchASentenceTellsTwoHandlesApart() {
        PublishedRuleHandle.Place here =
                new PublishedRuleHandle.Place.Unplaced(1, 1);
        PublishedRuleHandle.Place there =
                new PublishedRuleHandle.Place.Unplaced(2, 1);
        PublishedRuleHandle.Reached said = new PublishedRuleHandle.Reached(
                PublishedRuleKind.COMPARISON, here, "Int.clamp");

        assertNotEquals(0, Integer.signum(said.compareTo(new PublishedRuleHandle.Reached(
                        PublishedRuleKind.PREDICATE, here, "Int.clamp"))),
                "what the rule is");
        assertNotEquals(0, Integer.signum(said.compareTo(new PublishedRuleHandle.Reached(
                        PublishedRuleKind.COMPARISON, there, "Int.clamp"))),
                "where the code is");
        assertNotEquals(0, Integer.signum(said.compareTo(new PublishedRuleHandle.Reached(
                        PublishedRuleKind.COMPARISON, here, "Int.abs"))),
                "and what reaches it");
        assertEquals(0, said.compareTo(new PublishedRuleHandle.Reached(
                        PublishedRuleKind.COMPARISON, here, "Int.clamp")),
                "and two alike in every part are one value");
    }

    /**
     * The parts of a sentence about code reached from here.
     *
     * <p>Named rather than passed as functions, so that "the rest" is the rest: two lambdas reading
     * one part are two objects, and a walk that told them apart by identity would be asking whether
     * every part differs.
     */
    private enum Part {

        WHAT_THE_RULE_IS,

        WHERE_THE_CODE_IS,

        WHAT_REACHES_IT;

        String of(PublishedRuleHandle.Reached said) {
            return switch (this) {
                case WHAT_THE_RULE_IS -> said.kind().word();
                case WHERE_THE_CODE_IS -> said.at().toString();
                case WHAT_REACHES_IT -> said.reachedBy();
            };
        }
    }

    /**
     * Whether {@code these} hold two that differ in {@code part} and agree on the rest.
     *
     * <p>Agreeing on the rest is what makes the pair say something about that part. Two that differ
     * everywhere are told apart by whichever part is compared first, so a projection that had
     * dropped one of them would tell them apart all the same.
     */
    private static boolean variedAlone(List<PublishedRuleHandle.Reached> these, Part part) {
        for (PublishedRuleHandle.Reached one : these) {
            for (PublishedRuleHandle.Reached other : these) {
                if (part.of(one).equals(part.of(other))) {
                    continue;
                }
                boolean restAgrees = true;
                for (Part each : Part.values()) {
                    if (each != part && !each.of(one).equals(each.of(other))) {
                        restAgrees = false;
                    }
                }
                if (restAgrees) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The citations of code this compile reaches rather than holds, taken from a compilation.
     *
     * <p>Made where a source is placed, and only there. So this reads a model whose rules are in a
     * module the compile did not open, and keeps what the readings offered — which is the one way
     * to have such a citation out here, and is what makes the arm this type has for one something
     * the property below is asked over.
     *
     * <p><b>A module put on the path, and not the standard library.</b> Which code stands in for
     * somewhere else is decided by the text a body was quoted from being one this compile cannot
     * show ({@code HelperInliner.whereTheBodyIs}) — any published module, not the one this compiler
     * ships. A population taken from a model that only calls the library says nothing about the
     * rest of that domain: the library writes its operations in this language, a reading of rules
     * leaves those standing rather than taking them in, and a model built only of those has no such
     * citation at all. So the module is built here and put on the path, which is the shape the
     * domain actually has.
     */
    private static List<Citation> reachedFromHere() {
        Map<String, ClassFileImage> published = Compiler.compile("""
                module lib exposing ( big, small )

                let big (n: Int): Bool = n > 10

                let small (n: Int): Bool = n < 3
                """);
        String model = """
                module m

                import lib ( big, small )

                data Low
                data Accepted = { at: Int }

                behavior classify : (n: Int, m: Int) -> Accepted | Low
                    constructs Accepted

                let classify (n, m) = {
                    guard big(n) else Low
                    guard small(m) else Low
                    Accepted { at = n }
                }
                """;
        Compilation compilation =
                Compilation.ofSources(List.of(model), ModulePath.of(published));
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        // Read off the page rather than off a reading, because a reading holds no place: what a
        // sentence about one of these is written from is what the page was assembled with, and
        // this is asking that.
        List<Citation> out = new ArrayList<>();
        AdequacyReport.of(compilation).modules().get(0).behaviors().get(0).rulePlaces()
                .forEach((_, where) -> {
                    if (where instanceof Citation.Elsewhere && !out.contains(where)) {
                        out.add(where);
                    }
                });
        return out;
    }

    private static String said(PublishedRuleHandle handle) {
        return RuleHandleProse.said(handle, SourceNameResolver.identity(), null);
    }

    /**
     * That the population above is one a document writes more than one sentence for, and that every
     * kind of sentence it has is in it.
     *
     * <p>Both, because the property is over pairs and a population of one shape is a population of
     * one pair. An arm missing here is an arm whose reason for existing nothing asks about, which is
     * how the third one came to be left out the first time.
     */
    @Test
    void thePopulationHoldsEveryKindOfSentenceADocumentWrites() {
        List<PublishedRuleHandle> handles = everyHandle();

        assertTrue(handles.stream().map(TwoHandlesADocumentWritesApartAreTwoValuesTest::said)
                .distinct().count() > 1,
                "a population a document writes one sentence for says nothing about telling two"
                        + " apart");
        assertEquals(
                Set.of(PublishedRuleHandle.NamedInvariant.class,
                        PublishedRuleHandle.NumberedInvariant.class,
                        PublishedRuleHandle.NamedEnsures.class,
                        PublishedRuleHandle.WholeEnsures.class,
                        PublishedRuleHandle.Written.class,
                        PublishedRuleHandle.Reached.class,
                        PublishedRuleHandle.ReachedOutOfSight.class),
                Set.of(PublishedRuleHandle.class.getPermittedSubclasses()),
                "the kinds of sentence this type has");
        for (Class<?> each : PublishedRuleHandle.class.getPermittedSubclasses()) {
            assertTrue(handles.stream().anyMatch(each::isInstance),
                    () -> "and each of them is in what the property above is asked over: " + each);
        }
    }
}
