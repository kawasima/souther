package souther.compiler;

import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.query.PartitionEvidence;
import souther.compiler.report.AdequacyReport;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A rule this could not turn into a line is reported as the rule it is.
 *
 * <p>What the accounting was made to say, asked of the measure beside it. An author told that a
 * rule about {@code r.cost} went unread, with nothing saying which rule, has to go and find it; and
 * two rules stopped by one limit at one position came out as one line, because the finding was
 * keyed on the position and the limit. What a finding of this kind is asked of is the rule, so it
 * names one — by what tells it from its neighbours and by what a reader looks it up with, which
 * are two questions and not one.
 *
 * <p>Three producers write these, and a reader of the list is told the same thing about any of them
 * (spec §example-partition). All three are here, because what each of them had in hand was a
 * different value and each dropped it at its own seam.
 */
class ARuleWithoutALineIsNamedByTheRuleTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Two clauses of one declaration, each read to the end and each drawing no line at one
     *  position. */
    private static final String TWO_INVARIANTS = """
            module m

            data Ok
            data R = { a: Int, b: Int }
                invariant first  = a < b
                invariant second = b < a

            behavior f : (r: R) -> Ok
                constructs Ok
            let f (r) = Ok
            """;

    /** Two comparisons of one condition, about one position. */
    private static final String TWO_COMPARISONS = """
            module m

            data Ok
            data No

            behavior f : (n: Int) -> Ok | No
            let f (n) =
                if Int.multiply(n, n) < 4 || Int.multiply(n, n) > 9 then Ok else No
            """;

    /** Two clauses of one {@code ensures}, about one position. */
    private static final String TWO_ENSURES = """
            module m

            data Ok = { v: Int }

            behavior f : (a: Int, b: Int) -> Ok
                constructs Ok
                ensures low  = a < b
                ensures high = b < a
            let f (a, b) = Ok { v = a }
            """;

    /**
     * Two clauses of a declaration are two findings, and each says which clause.
     *
     * <p>The invariant producer had the rule all along: the reading of ends records which clause it
     * gave up on, and the seam onto the position kept the position and the reason and dropped the
     * rest. So the two clauses became one line and the line named neither.
     */
    @Test
    void twoInvariantClausesWithNoLineAreTwoFindings() {
        // Two rules about two positions, so four findings: a reader looking up either position is
        // owed both rules. What was one line is the pair at one position.
        assertEquals(4, rulesNotRead(TWO_INVARIANTS).size(), () -> withoutALine(TWO_INVARIANTS));
        assertEquals(2, ruleIdsNotRead(TWO_INVARIANTS).size(), () -> withoutALine(TWO_INVARIANTS));
        assertTrue(namesRule(human(TWO_INVARIANTS), "invariant R (first)"),
                human(TWO_INVARIANTS));
        assertTrue(namesRule(human(TWO_INVARIANTS), "invariant R (second)"),
                human(TWO_INVARIANTS));
    }

    /**
     * Whether the report names {@code rule} on a line saying it left the position with no line.
     *
     * <p>Either word the report writes for that: a reading that stopped is `+not read+` and a rule
     * read to the end that divided no position is `+no line+`. Which of them this rule gets is its
     * reason's business; what is asked here is that the rule is named at all, which is what a
     * reader looking for it is owed.
     */
    private static boolean namesRule(String report, String rule) {
        return report.contains("not read: " + rule) || report.contains("no line: " + rule);
    }

    /** And two comparisons of one condition, which the guard producer kept one of. */
    @Test
    void twoComparisonsStoppedAlikeAreTwoFindings() {
        assertEquals(2, rulesNotRead(TWO_COMPARISONS).size(), () -> withoutALine(TWO_COMPARISONS));
        assertEquals(2, ruleIdsNotRead(TWO_COMPARISONS).size(), () -> withoutALine(TWO_COMPARISONS));
    }

    /** And two clauses of an {@code ensures}. */
    @Test
    void twoEnsuresClausesWithNoLineAreTwoFindings() {
        assertEquals(2, ruleIdsNotRead(TWO_ENSURES).size(), () -> withoutALine(TWO_ENSURES));
        assertTrue(namesRule(human(TWO_ENSURES), "ensures f (low)"), human(TWO_ENSURES));
        assertTrue(namesRule(human(TWO_ENSURES), "ensures f (high)"), human(TWO_ENSURES));
    }

    /**
     * A rule about two positions is a finding at each of them.
     *
     * <p>{@code a < b} divides neither, and a reader looking up either is owed the same answer.
     * Filed at the first position the reading names, which position was told about would turn on
     * which side the author wrote it — so the same rule written the other way round would answer
     * about the other one.
     */
    @Test
    void aRuleAboutTwoPositionsIsAFindingAtEach() {
        assertEquals(List.of("r.a", "r.b"),
                rulesNotRead(TWO_INVARIANTS).stream()
                        .map(PartitionEvidence.NotRead::at).distinct().sorted().toList(),
                () -> withoutALine(TWO_INVARIANTS));
    }

    /**
     * A comparison written in code the author cannot open is not one of these.
     *
     * <p>The author wrote {@code > 70} here and the comparisons inside {@code Int.clamp} in a
     * library this compile has no file for. A report naming the second sends them to edit a
     * function they do not have — while a helper of their own has a file, is cited where it is
     * written, and is theirs to rewrite.
     *
     * <p>The accounting beside this still raises questions for the library's own forks. That is
     * where it stood before this change and is not changed here; that the two ought to draw the
     * line in one place is its own question.
     */
    @Test
    void aComparisonWrittenOutOfSightIsNotOneOfThese() {
        String model = """
                module m

                data Low
                data Accepted = { at: Int }

                behavior classify : (n: Int) -> Accepted | Low
                    constructs Accepted

                let classify (n) = {
                    guard Int.clamp(0, 100, n) > 70 else Low
                    Accepted { at = n }
                }
                """;

        List<PartitionEvidence.NotRead> said = rulesNotRead(model);

        assertEquals(1, said.size(), () -> withoutALine(model));
        assertTrue(human(model).contains("not read: comparison@"), human(model));
        assertTrue(human(model).lines()
                        .filter(line -> line.contains("not read:"))
                        .noneMatch(line -> line.contains("Int.clamp")),
                human(model));
    }

    /**
     * One rule under both measures, and the same identity under each.
     *
     * <p>This measure says the reading that draws lines could not adopt the rule; the accounting
     * says no reading at all took it in. A rule can be either without the other, and where both are
     * true a reader is told both — in different words, about the same rule. So the two entries
     * carry one identity and a consumer can join them; deduplicated, they would be back to one
     * answer for two questions.
     *
     * <p>Asked of the identities and not of the words. Two entries that happen to mention a rule
     * apiece say nothing about whether it is the same rule, which is the whole of what a consumer
     * wants here.
     */
    @Test
    void oneRuleUnderBothMeasuresCarriesOneIdentity() {
        // A bound this reads, and beside it a clause the reading of ends could not turn into a line
        // and the reading of values has no word for. The second is the rule under both measures.
        String model = """
                module m

                data Length = Int
                    invariant min    = value >= 1
                    invariant square = value * value >= 4

                behavior price : (length: Length) -> Int
                let price (length) = if length.value >= 5 then 1 else 2
                """;

        JsonNode partition = JSON.readTree(json(model))
                .get("modules").get(0).get("behaviors").get(0).get("partition");
        Set<String> unread = ruleIdsOf(partition.get("notRead"));
        Set<String> standing = ruleIdsOf(partition.get("unanswered"));

        assertEquals(1, standing.size(), () -> partition.toString());
        assertTrue(unread.containsAll(standing), () -> partition.toString());
    }

    /** And a finding carries it too, so the two surfaces join. */
    @Test
    void aFindingAboutARuleCarriesTheIdentityAsWell() {
        JsonNode findings = JSON.readTree(json(TWO_INVARIANTS))
                .get("modules").get(0).get("behaviors").get(0).get("findings");

        Set<String> said = new LinkedHashSet<>();
        int counted = 0;
        for (JsonNode each : findings) {
            if (!"partition_not_read".equals(each.get("kind").asString())) {
                continue;
            }
            counted++;
            said.add(each.get("ruleId").toString());
        }
        assertEquals(4, counted, findings::toString);
        assertEquals(2, said.size(), findings::toString);
    }

    /** The rules named by every entry of {@code entries}, as the document identifies them. */
    private static Set<String> ruleIdsOf(JsonNode entries) {
        Set<String> out = new LinkedHashSet<>();
        if (entries == null) {
            return out;   // absent where the measure found none, which is not an empty array
        }
        entries.forEach(each -> {
            if (each.has("ruleId")) {
                out.add(each.get("ruleId").toString());
            }
        });
        return out;
    }

    /**
     * A comparison written inside a fork of its own is a rule of its own, cited where it is.
     *
     * <p>A condition can hold a fork — {@code guard Int.add(if a < b then 1 else 2, 0) > 0} — so
     * two comparisons stand under one outer construct and are two rules. Told apart by the
     * construct each was written in, the inner one's entry was dropped as a repeat of the outer's
     * whenever the two constructs agreed; told apart by where each is written, they are two
     * wherever they are two.
     */
    @Test
    void aComparisonInsideAForkOfItsOwnIsARuleOfItsOwn() {
        String model = """
                module m

                data Ok
                data No

                behavior f : (a: Int, b: Int) -> Ok | No
                let f (a, b) = {
                    guard Int.add(if Int.multiply(a, a) < b then 1 else 2, 0) > 0 else No
                    Ok
                }
                """;

        List<java.util.Set<souther.compiler.check.RuleCitation>> cited = rulesNotRead(model).stream()
                .map(ARuleWithoutALineIsNamedByTheRuleTest::citedIn)
                .distinct().toList();

        assertEquals(2, cited.size(),
                () -> "the comparison in the condition and the one inside the fork it holds are "
                        + "two rules at two places: " + cited + "\n" + human(model));
    }

    /**
     * One rule stopped twice at one position is two findings, told apart by the limit.
     *
     * <p>A clause is read a conjunct at a time and each conjunct stops for its own reason: here one
     * relates two positions and the one beside it is written in a form nothing reads. Both are the
     * same rule about the same position, so the rule and the place say nothing about which is
     * which — and a finding carrying only those came out as one object twice, with the entry beside
     * it in `notRead` keyed on the very thing that was missing.
     */
    @Test
    void oneRuleStoppedTwiceAtOnePositionIsTwoFindings() {
        String model = """
                module m

                data Ok
                data R = { a: Int, b: Int }
                    invariant mixed = a < b && Int.multiply(a, a) < 10

                behavior f : (r: R) -> Ok
                    constructs Ok
                let f (r) = Ok
                """;

        JsonNode findings = JSON.readTree(json(model))
                .get("modules").get(0).get("behaviors").get(0).get("findings");

        Set<String> whole = new LinkedHashSet<>();
        Set<String> withoutTheReason = new LinkedHashSet<>();
        for (JsonNode each : findings) {
            if (!"partition_not_read".equals(each.get("kind").asString())
                    || !"r.a".equals(each.get("subject").asString())) {
                continue;
            }
            whole.add(each.toString());
            withoutTheReason.add(each.get("subject").asString() + each.get("ruleId").toString());
        }

        assertEquals(2, whole.size(), findings::toString);
        assertEquals(1, withoutTheReason.size(),
                () -> "one rule and one position, so the reason is the whole of the difference: "
                        + findings);
    }

    /**
     * Every rule of the behavior that this could not turn into a line.
     *
     * <p>Both of the shapes that name one. A rule read to the end that draws no line and a rule
     * nothing worked out the questions of are opposite sentences about this compiler and are one
     * thing here: what is asked is that a reader is told which rule, which is owed either way.
     */
    private static List<PartitionEvidence.NotRead> rulesNotRead(String model) {
        List<PartitionEvidence.NotRead> out = new ArrayList<>();
        for (PartitionEvidence.NotRead each : measured(model).notRead()) {
            if (ruleOf(each) != null) {
                out.add(each);
            }
        }
        return out;
    }

    /** And how many rules those are, which is what a document keys them by. */
    private static Set<souther.compiler.check.RuleRef> ruleIdsNotRead(String model) {
        Set<souther.compiler.check.RuleRef> out = new LinkedHashSet<>();
        for (PartitionEvidence.NotRead each : rulesNotRead(model)) {
            out.add(ruleOf(each));
        }
        return out;
    }

    /** How a reader is sent to the rule one of these names. */
    private static java.util.Set<souther.compiler.check.RuleCitation> citedIn(
            PartitionEvidence.NotRead said) {
        return switch (said) {
            case PartitionEvidence.NotRead.ARule it -> it.cited();
            case PartitionEvidence.NotRead.AnUnclassifiedRule it -> it.cited();
            case PartitionEvidence.NotRead.APosition _ -> java.util.Set.of();
        };
    }

    /** Which rule one of these names, or null where it names a position and no rule. */
    private static souther.compiler.check.RuleRef ruleOf(PartitionEvidence.NotRead said) {
        return switch (said) {
            case PartitionEvidence.NotRead.ARule it -> it.rule();
            case PartitionEvidence.NotRead.AnUnclassifiedRule it -> it.rule();
            case PartitionEvidence.NotRead.APosition _ -> null;
        };
    }

    private static PartitionEvidence measured(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).modules().get(0).behaviors().get(0).partition();
    }

    private static String human(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).human(SourceRendering.namedByIdentity(compilation.texts()));
    }

    private static String withoutALine(String model) {
        JsonNode document = JSON.readTree(json(model));
        return document.get("modules").get(0).get("behaviors").get(0)
                .get("partition").get("notRead").toString();
    }

    private static String json(String model) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation).json(SourceRendering.namedByIdentity(compilation.texts()));
    }
}
