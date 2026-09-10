package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * What a declaration's rules leave a position is the same however the boolean was spelt.
 *
 * <p>A denial is one of the language's ways of saying a thing and not a rule of its own. {@code not
 * (n > 3)}, {@code (n > 3) == false} and {@code if n > 3 then false else true} state what {@code n
 * <= 3} states, and a reading that answers about the spelling answers about a clause nobody wrote.
 *
 * <p><b>Two tiers, because the two questions have different owners.</b> Where the spellings differ
 * only in how one leaf was written, everything a reading of the declaration comes to is the same —
 * the ends, which conjunct placed them, what accounts for the rule, and what the position is left
 * with. Where the spellings differ by a De Morgan law, the rules are the same rules and the
 * conjuncts an author wrote are not: {@code a && b} is two authored parts and {@code not (a || b)}
 * is one. Which of them a line belongs to is the split's answer and not this one's, so what is held
 * here is what the rules leave and not how it is filed.
 *
 * <p>Each tier carries the pair that makes it say something. A property over a projection that lost
 * the distinction holds whatever the projection does, so the spellings that state different rules
 * are asserted to come out different.
 */
class WhatARuleLeavesDoesNotTurnOnHowItsAuthorSpeltTheBooleanTest {

    /** One leaf, said four ways. Everything a reading comes to is the same. */
    @Test
    void oneLeafSaidFourWaysIsOneReading() {
        Reading held = readingOf("n <= 3");
        assertEquals(held, readingOf("Bool.not(n > 3)"));
        assertEquals(held, readingOf("(n > 3) == false"));
        assertEquals(held, readingOf("if n > 3 then false else true"));
    }

    /** And the two shapes that name a value, which a denial exchanges. */
    @Test
    void aValueNamedAndTheValueRuledOutAreEachOneReading() {
        assertEquals(readingOf("n == 3"), readingOf("Bool.not(n /= 3)"));
        assertEquals(readingOf("n /= 3"), readingOf("Bool.not(n == 3)"));
    }

    /**
     * And a rule named through a helper, denied.
     *
     * <p>A helper's expansion is a binding, and what a reading meets under a denial there is the
     * binding with the denial above it. Crossed only where nothing stood above it, a rule an author
     * named and denied was a form this reading had no word for — and since almost every binding it
     * meets is one an expansion made, that is most of the rules stated through a helper.
     */
    @Test
    void aRuleNamedThroughAHelperAndDeniedIsTheRuleItDenies() {
        assertEquals(readingOf("n <= 3"), readingOf("Bool.not(aboveThree(n))", HELPER));
        assertEquals(readingOf("n > 3"), readingOf("aboveThree(n)", HELPER));
    }

    /** A helper naming the rule the fixtures above deny. */
    private static final String HELPER = """
            let aboveThree (n: Int): Bool = n > 3
            """;

    /**
     * And the fixtures are held to being programs somebody could write.
     *
     * <p>The control on how these models are built, and it is over the driving rather than beside
     * it. A property over spellings is worth what its fixtures are worth, and a spelling this
     * compiler refuses reads as a declaration with no rule at all — which agrees with every other
     * spelling about everything. The reading these rows are of makes a leaf of an ill-typed clause
     * and says nothing about it, so what refuses one is somewhere else entirely, and how far a
     * fixture is driven is the whole of whether it is refused at all.
     */
    @Test
    void aModelThatCannotBeWrittenIsRefused() {
        for (String clause : List.of("n > \"three\"", "nope > 3", "Bool.not(n)")) {
            assertThrows(AssertionError.class, () -> domainsOf(clause),
                    () -> "a fixture this compiler refuses is no fixture: " + clause);
        }
    }

    /** The pair the tier above would pass without: a rule and its denial are different rules. */
    @Test
    void aRuleAndItsDenialAreNotOneReading() {
        assertNotEquals(readingOf("n <= 3"), readingOf("n > 3"));
        assertNotEquals(readingOf("n == 3"), readingOf("n /= 3"));
    }

    /**
     * And a conjunction written as a denied choice leaves what the conjunction leaves.
     *
     * <p>What the rules leave, and not which authored conjunct each end belongs to. The split that
     * wrote the parts down reads the {@code &&} an author typed, so one of these is two parts and
     * the other is one; whether that is the filing these ends deserve is a question about the split
     * and is asked where the split is.
     */
    @Test
    void aConjunctionWrittenAsADeniedChoiceLeavesWhatTheConjunctionLeaves() {
        assertEquals(leavesOf("n <= 3 && n >= 0"), leavesOf("Bool.not(n > 3 || n < 0)"));
    }

    /** And a choice written as a denied conjunction. */
    @Test
    void aChoiceWrittenAsADeniedConjunctionLeavesWhatTheChoiceLeaves() {
        assertEquals(leavesOf("n <= 3 || n >= 10"), leavesOf("Bool.not(n > 3 && n < 10)"));
    }

    /** The pair that tier would pass without: a conjunction and a choice leave different things. */
    @Test
    void aConjunctionAndAChoiceDoNotLeaveTheSameThing() {
        assertNotEquals(leavesOf("n <= 3 && n >= 0"), leavesOf("n <= 3 || n >= 10"));
    }

    /**
     * Everything a reading of one declaration comes to, less where it was written.
     *
     * <p>What the spellings must agree about. Where each of them was written is what they must not:
     * a report about {@code not (n > 3)} points at the denial, which is what is on the line, and
     * the same report about {@code n <= 3} points somewhere else — so a position is no part of this.
     *
     * <p>A conjunct handed on is compared by what it claims of its two sides and not by the sides.
     * The sides are expressions, and an expression carries where it was written; what a denial can
     * exchange is the claim, and it exchanges nothing else — the two sides of a comparison are put
     * the other way round by turning it towards a number, which is not on this path.
     */
    private record Reading(List<FieldDomains.Placed> placed, List<FieldDomains.Placed> stated,
                           List<FieldDomains.AboutOneCoordinate> about,
                           List<ComparisonClaim> handedOn, String bounds, String accounting) {}

    private static Reading readingOf(String clause) {
        return readingOf(clause, "");
    }

    private static Reading readingOf(String clause, String helpers) {
        FieldDomains domains = domainsOf(clause, helpers);
        return new Reading(domains.placed(), domains.stated(), domains.aboutOneCoordinate(),
                domains.withoutAnEnd().stream().map(each -> each.states().claim()).toList(),
                String.valueOf(domains.at(RuleKey.of("n"))),
                String.valueOf(domains.accounting()));
    }

    /**
     * What the rules leave the position, with the conjunct each end is filed against dropped.
     *
     * <p>What the tier below holds. One reading, projected twice: asked as two questions of two
     * readings, the two spellings are each read again for the second half, and a test that reads a
     * model twice is a test that could be comparing two of them.
     */
    private record Leaves(Set<String> ends, String bounds) {}

    private static Leaves leavesOf(String clause) {
        FieldDomains domains = domainsOf(clause);
        return new Leaves(
                domains.placed().stream()
                        .map(each -> each.at() + (each.lower() ? " from " : " to ") + each.end())
                        .collect(java.util.stream.Collectors.toSet()),
                String.valueOf(domains.at(RuleKey.of("n"))));
    }

    private static FieldDomains domainsOf(String clause) {
        return domainsOf(clause, "");
    }

    /**
     * The reading of each model this test names, built once.
     *
     * <p>A property held over spellings names the same spelling from several of its rows — one
     * spelling is the thing another is being held against, and the row that says two of them differ
     * names both again. Built per naming, a model is built as many times as it is mentioned, and
     * which reading of it a row was about is decided by the order the rows ran in.
     *
     * <p>The model and not the reading, because building it is what costs: what a reading projects
     * out of one is a walk over what is already in hand.
     */
    private static final Map<String, FieldDomains> READ = new HashMap<>();

    private static FieldDomains domainsOf(String clause, String helpers) {
        String source = """
                module example.spelling

                %s
                data Box =
                    { n: Int
                    }
                    invariant capped = %s
                """.formatted(helpers, clause);
        return READ.computeIfAbsent(source, each -> read(each, clause));
    }

    private static FieldDomains read(String source, String clause) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        // Everything there is to answer, which is what holding a fixture to being writable costs
        // and not what these rows read.
        //
        // Driven as far as the rows read, an ill-typed clause reads perfectly well: what refuses
        // `Bool.not(n)` is not the reading of ends, which makes a leaf of it, but the typing the
        // code this compiler writes goes through — so a fixture stopped short of that comes back a
        // declaration with no rule, and a declaration with no rule agrees with every spelling here
        // about everything. Which is the one way a property over spellings passes while saying
        // nothing, so the fixtures are held first and read afterwards.
        compilation.answerEverything();
        assertEquals(List.of(), compilation.reports().stream()
                        .map(each -> each.report().diagnostic().code()).toList(),
                "the model under test is a program that can be written: " + clause);
        String module = compilation.modules().get(0);
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        TypeSymbol.AtModule named = TypeSymbols.declared(new TypeKey(module, "Box"));
        return FieldDomains.of(named, rules, ReadAs.THE_COMPILATION_DOES);
    }
}
