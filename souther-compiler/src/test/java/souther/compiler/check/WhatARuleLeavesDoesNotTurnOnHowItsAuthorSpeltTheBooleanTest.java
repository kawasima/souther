package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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
        assertEquals(endsOf("n <= 3 && n >= 0"), endsOf("Bool.not(n > 3 || n < 0)"));
        assertEquals(boundsOf("n <= 3 && n >= 0"), boundsOf("Bool.not(n > 3 || n < 0)"));
    }

    /** And a choice written as a denied conjunction. */
    @Test
    void aChoiceWrittenAsADeniedConjunctionLeavesWhatTheChoiceLeaves() {
        assertEquals(endsOf("n <= 3 || n >= 10"), endsOf("Bool.not(n > 3 && n < 10)"));
        assertEquals(boundsOf("n <= 3 || n >= 10"), boundsOf("Bool.not(n > 3 && n < 10)"));
    }

    /** The pair that tier would pass without: a conjunction and a choice leave different things. */
    @Test
    void aConjunctionAndAChoiceDoNotLeaveTheSameThing() {
        assertNotEquals(boundsOf("n <= 3 && n >= 0"), boundsOf("n <= 3 || n >= 10"));
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
        FieldDomains domains = domainsOf(clause);
        return new Reading(domains.placed(), domains.stated(), domains.aboutOneCoordinate(),
                domains.withoutAnEnd().stream().map(each -> each.states().claim()).toList(),
                String.valueOf(domains.at(RuleKey.of("n"))),
                String.valueOf(domains.accounting()));
    }

    /** The ends the rules leave, with the conjunct each is filed against dropped. */
    private static Set<String> endsOf(String clause) {
        return domainsOf(clause).placed().stream()
                .map(each -> each.at() + (each.lower() ? " from " : " to ") + each.end())
                .collect(java.util.stream.Collectors.toSet());
    }

    private static String boundsOf(String clause) {
        return String.valueOf(domainsOf(clause).at(RuleKey.of("n")));
    }

    private static FieldDomains domainsOf(String clause) {
        String source = """
                module example.spelling

                data Box =
                    { n: Int
                    }
                    invariant capped = %s
                """.formatted(clause);
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        assertEquals(List.of(), compilation.diagnostics().values().stream()
                        .flatMap(List::stream).map(each -> each.diagnostic().code()).toList(),
                "the model under test is a program that can be written: " + clause);
        String module = compilation.modules().get(0);
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        TypeSymbol.AtModule named = TypeSymbols.declared(new TypeKey(module, "Box"));
        return FieldDomains.of(named, rules, ReadAs.THE_COMPILATION_DOES);
    }
}
