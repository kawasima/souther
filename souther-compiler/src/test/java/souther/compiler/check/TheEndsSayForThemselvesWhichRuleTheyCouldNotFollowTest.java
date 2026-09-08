package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.query.Compilation;
import souther.compiler.query.Scopes;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;
import souther.compiler.values.UnreadReason;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Whether the reading of ends could follow a rule is that reading's to say, and not read off the
 * ranges it produced.
 *
 * <p>Every rule that bounds nothing leaves the positions where they were, and they are not alike. A
 * denial states neither end: this reading has a word for that, what it leaves is every value the
 * order holds, and the rule is one it followed to the end. A comparison whose subject is a term it
 * cannot name leaves them there too, and it is a rule this reading lost the thread of — what such a
 * rule holds a value down to is unknown here.
 *
 * <p><b>Why the difference is worth a test.</b> A branch this reading could not follow is what a
 * choice needs in order to know that the alternative widened it, and a branch it followed is not.
 * Answered off the ranges, every denial written beside a bound made the choice one this reading
 * declined to speak for — so a question about the bounded position stood, with an account naming a
 * clause nothing failed at.
 *
 * <p>Asked at the accounting rather than at the flag, because that is where the difference is a
 * fact about a model. The pair below is the whole of it: the same shape twice, one alternative the
 * ends read and one they could not, and the answers are not the same.
 */
class TheEndsSayForThemselvesWhichRuleTheyCouldNotFollowTest {

    /**
     * A denial of one position beside a bound on another.
     *
     * <p>Nothing about {@code n} rests on this reading having failed at anything: the left
     * alternative admits every {@code n} there is, the right holds it above two, and where the
     * choice leaves {@code n} is every value — which is what the rule says and what this reading
     * arrived at.
     */
    private static final String A_DENIAL_BESIDE_A_BOUND = """
            module demo

            data N = { m: Int, n: Int }
                invariant r = m /= 5 || n >= 2
            """;

    /**
     * The same shape, with a subject the reading of ends cannot name.
     *
     * <p>{@code Int.abs(m)} is not a position this counts, so what the left alternative holds
     * {@code m} to is unknown here — and a value satisfying it owes the right one nothing. The
     * question at {@code n} stands, and this is what says the flag still fires where it should.
     */
    private static final String A_SUBJECT_THE_ENDS_CANNOT_NAME = """
            module demo

            data N = { m: Int, n: Int }
                invariant r = Int.abs(m) >= 5 || n >= 2
            """;

    /** A denial is a rule this reading followed, so the choice is one it can speak for. */
    @Test
    void aDenialBesideABoundLeavesNothingStandingAtTheBoundedPosition() {
        assertEquals(List.of(), standingAt(A_DENIAL_BESIDE_A_BOUND, "n"),
                "the ends read both alternatives and the choice leaves `n` every value, which is"
                        + " an answer — read off the ranges, the denial bounded nothing and the"
                        + " choice was one nothing could speak for");
    }

    /** And a rule whose subject it cannot name is one it did not, so the question stands. */
    @Test
    void andASubjectTheEndsCannotNameLeavesTheQuestionStanding() {
        assertFalse(standingAt(A_SUBJECT_THE_ENDS_CANNOT_NAME, "n").isEmpty(),
                "what the left alternative holds `m` to is unknown to this reading, so the choice"
                        + " is one it cannot speak for and `n` is left open by it");
    }

    /** What every question about {@code field} was left standing on, over every rule of the value. */
    private static List<UnreadReason> standingAt(String source, String field) {
        List<UnreadReason> out = new ArrayList<>();
        read(source).accounting().values().forEach(accounting ->
                accounting.answers().forEach((owed, outcome) -> {
                    if (owed.toString().equals(field)
                            && outcome instanceof RuleAccounting.Outcome.Unaccounted it
                            && it.why() instanceof RuleAccounting.Why.TheValueReadingSays says) {
                        out.addAll(says.why());
                    }
                }));
        return out;
    }

    private static FieldDomains read(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        assertEquals(List.of(), compilation.diagnostics().values().stream()
                .flatMap(List::stream).map(each -> each.diagnostic().code()).toList(),
                "the model this reads has to be one somebody could write");
        Symbols symbols = Scopes.derived(compilation.db(), "demo").value();
        return FieldDomains.of(
                TypeSymbols.declared(new TypeKey(symbols.module(), "N")),
                RuleReadings.of(compilation, "demo"),
                souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
    }
}
