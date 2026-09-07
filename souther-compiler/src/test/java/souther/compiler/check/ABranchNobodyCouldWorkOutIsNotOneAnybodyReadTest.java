package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.query.Compilation;
import souther.compiler.query.Scopes;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;
import souther.compiler.values.AdmissibleSet;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A branch this could not work out is kept, and what it left is not claimed as read.
 *
 * <p>Two patterns that share no string leave a branch nothing satisfies, and nothing knows that
 * until both machines are made and met. Where the meet is more than this compiler will build, the
 * position comes back holding every value — and the branch, asked whether it admits anything, looks
 * like one somebody can be in.
 *
 * <p>Keeping it is right: nothing showed it empty, so dropping it would answer a model from
 * something this compiler failed to work out. What is not right is keeping it quietly. The account
 * of what each clause took in is written before any machine is made, so the branch arrives claiming
 * to have read its rules — and the reading beside it is then answered as though the choice had been
 * read from end to end, at a position where the truth is that nobody looked.
 *
 * <p>So the three facts settle together. What the position admits, whether the branch admits
 * anything, and which limit stopped the work are one piece of work and travel as one.
 */
class ABranchNobodyCouldWorkOutIsNotOneAnybodyReadTest {

    /**
     * A choice whose left branch is empty and expensive to show empty.
     *
     * <p>Each pattern is small; their meet is past what one machine may be. The two repeat over
     * runs of one length and of another that share no factor, so a walk over both at once is in a
     * different pair at every step until the lengths come round together. So the left branch
     * neither survives being worked out nor comes back empty.
     */
    private static final String MODEL = """
            module demo

            data Pair = { x: String, y: String, p: String }
                invariant r =
                    (String.matches("(a{251})*b", y) && String.matches("(a{223})*c", y))
                    || x == "A"
                invariant wide =
                    (p == "1" || p == "2")
                    && (p == "1" || p == "3")
                    && (p == "1" || p == "4")
                    && (p == "1" || p == "5")
                    && (p == "1" || p == "6")
                    && (p == "1" || p == "7")
                    && (p == "1" || p == "8")
            """;

    private static FieldDomains read() {
        return read(MODEL, "Pair");
    }

    private static FieldDomains read(String model, String declared) {
        Compilation compilation = Compilation.ofSource(model, "Main");
        compilation.answerEverything();
        assertEquals(List.of(), compilation.diagnostics().values().stream()
                .flatMap(List::stream).map(each -> each.diagnostic().code()).toList(),
                "the model this reads has to be one somebody could write");
        Symbols symbols = Scopes.derived(compilation.db(), "demo").value();
        TypeSymbol.AtModule name = TypeSymbols.declared(new TypeKey(symbols.module(), declared));
        return FieldDomains.of(name,
                RuleReadings.of(compilation, "demo"),
                souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
    }

    /**
     * The position the unworkable branch names is left open, and says so.
     *
     * <p>Open because the branch is kept, and said because nobody worked it out. A reading that
     * kept the branch and dropped the reason would answer that {@code y} is every string with
     * nothing standing against it, which is what a position no rule ever reached looks like.
     */
    @Test
    void whatCouldNotBeWorkedOutIsSaidAtThePositionItIsAbout() {
        AdmissibleSet y = read().admits(RuleKey.of("y"));

        assertNotEquals(AdmissibleSet.READ_IN_FULL, y.completeness(),
                "a branch nobody could work out leaves the position short of what its rules say");
    }

    /**
     * And the rule of that branch is not one this reading answered for.
     *
     * <p>The account is written from the leaves, before any machine exists, so the pattern rules
     * arrive as rules the reading took in whole. They were recognised — and what they leave was not
     * worked out, which is the difference between reading a rule and answering with it.
     */
    @Test
    void theRuleOfSuchABranchIsNotOneTheReadingAnsweredFor() {
        assertEquals(List.of("y"), unanswered(),
                "the position of the branch nobody could work out, and only that one");
    }

    /**
     * And a rule with no branch at all is not both taken in and stopped.
     *
     * <p>One pattern, larger than any machine this holds. The account is written from the leaf, so
     * the rule arrives as one this reading recognised and took in whole; the machine for it is made
     * later and is refused. A reader asking what answered the rule at that position was told this
     * reading did, beside a set widened because it had not.
     */
    @Test
    void aRuleWhoseOwnMachineIsRefusedIsNotOneTheReadingAnsweredFor() {
        FieldDomains read = read("""
                module demo

                data Big = String
                    invariant huge = String.matches("a{60000}", value)
                """, "Big");

        assertTrue(assertInstanceOf(AdmissibleSet.Completeness.Wider.class,
                        read.admits(RuleKey.THE_VALUE).completeness(),
                        "a pattern this will not make a machine of leaves the position short")
                        .why().contains(new AdmissibleSet.Widening.RuleUnread(
                                souther.compiler.values.UnreadReason.PATTERN_TOO_COSTLY)),
                "and says which of the two ways of running out it was");
        // The one position the declaration has. Counted rather than named, since what a question is
        // filed under prints itself and a test that pinned the printing would be about that.
        assertEquals(1, unanswered(read).size(),
                "and the rule is not one this reading answered with");
    }

    private static List<String> unanswered() {
        return unanswered(read());
    }

    private static List<String> unanswered(FieldDomains read) {
        return read.accounting().values().stream()
                .flatMap(each -> each.answers().entrySet().stream())
                .filter(each -> each.getValue() instanceof RuleAccounting.Outcome.Unaccounted)
                .map(each -> String.valueOf(each.getKey()))
                .distinct()
                .toList();
    }
}
