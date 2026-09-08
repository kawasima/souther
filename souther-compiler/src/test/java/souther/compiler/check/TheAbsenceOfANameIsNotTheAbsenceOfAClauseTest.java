package souther.compiler.check;

import souther.compiler.Compiler;
import souther.compiler.check.InvariantChecker.Judgment;
import souther.compiler.check.InvariantChecker.Said;
import souther.compiler.check.InvariantChecker.Verdict;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.Messages;
import souther.compiler.diag.msg.InvariantMessage;
import souther.compiler.diag.msg.Message;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A clause written without a name is a clause. What a name decides is whether a diagnostic can send
 * a reader to that clause, and nothing else — so the two sets of names a {@link Judgment} carries
 * are a projection for the diagnostic to write out, and neither of them being empty says anything
 * about whether the type declared such a clause.
 *
 * <p>Held at three layers, because the same conflation can be made again at each of them: what the
 * check decided, what it kept for a diagnostic to name, and which of E2011's spellings a reader is
 * answered in. The check was already holding the first when this was written — {@code verdictOf}
 * counts an unnamed clause as unsettled whether or not it can name it — and the conflation had come
 * back one layer down, where the warning asked the unsettled names whether there were settled ones.
 */
class TheAbsenceOfANameIsNotTheAbsenceOfAClauseTest {

    /** Both clauses named: one the guard establishes, one it leaves standing. */
    private static final String BOTH = """
            module demo

            data Bad

            data Bound =
                { low: Int
                , high: Int
                }
                invariant lowNonNegative = low >= 0
                invariant ordered = low <= high

            behavior widen : (low: Int, high: Int) -> Bound | Bad
                constructs Bound

            let widen (low, high) = {
                guard low >= 0
                    else Bad
                Bound { low = low, high = high }
            }
            """;

    /** One named clause, left standing, and nothing established to name beside it. */
    private static final String UNSETTLED_ONLY = """
            module demo

            data Bound =
                { low: Int
                , high: Int
                }
                invariant ordered = low <= high

            behavior widen : (low: Int, high: Int) -> Bound
                constructs Bound

            let widen (low, high) = Bound { low = low, high = high }
            """;

    /**
     * The clause left standing carries no name and the one the guard established does. The case the
     * two questions have to be asked separately for: one set is empty and the other is not, and
     * reading either as "there was no such clause" says something untrue.
     */
    private static final String SETTLED_ONLY = """
            module demo

            data Bad

            data Bound =
                { low: Int
                , high: Int
                }
                invariant lowNonNegative = low >= 0
                invariant low <= high

            behavior widen : (low: Int, high: Int) -> Bound | Bad
                constructs Bound

            let widen (low, high) = {
                guard low >= 0
                    else Bad
                Bound { low = low, high = high }
            }
            """;

    /** Neither side can be named: one unnamed clause, left standing. */
    private static final String NEITHER = """
            module demo

            data Bound =
                { low: Int
                , high: Int
                }
                invariant low <= high

            behavior widen : (low: Int, high: Int) -> Bound
                constructs Bound

            let widen (low, high) = Bound { low = low, high = high }
            """;

    // --- what the check decided ------------------------------------------------------------------

    /**
     * An unnamed clause nothing known there establishes leaves the invariant unproven, the same as a
     * named one. Reading the answer off the names alone discharged it.
     */
    @Test
    void anUnnamedClauseIsJudgedLikeAnyOther() {
        assertEquals(Verdict.UNKNOWN, judgmentOn(NEITHER).verdict(),
                "an unnamed clause is a clause the guards did not establish");
        assertEquals(Verdict.UNKNOWN, judgmentOn(SETTLED_ONLY).verdict(),
                "the unnamed one left standing decides this, not the named one that was settled");
    }

    /** The verdict is the same whichever of the clauses carry names: naming one changes what can be
     * written out about it and not what the check found. */
    @Test
    void namingAClauseDoesNotChangeTheVerdict() {
        assertEquals(judgmentOn(NEITHER).verdict(), judgmentOn(UNSETTLED_ONLY).verdict(),
                "the same construction, judged the same, with the clause named in one of them");
    }

    // --- what it kept for a diagnostic to name ---------------------------------------------------

    /** The names a message can write out are the ones that were written, on both sides
     *  independently. */
    @Test
    void onlyTheClausesThatWereNamedCanBeNamed() {
        assertEquals(List.of(), names(judgmentOn(NEITHER).unsettled()));
        assertEquals(List.of(), names(judgmentOn(NEITHER).settled()));

        assertEquals(List.of("ordered"), names(judgmentOn(UNSETTLED_ONLY).unsettled()));
        assertEquals(List.of(), names(judgmentOn(UNSETTLED_ONLY).settled()));

        assertEquals(List.of("ordered"), names(judgmentOn(BOTH).unsettled()));
        assertEquals(List.of("lowNonNegative"), names(judgmentOn(BOTH).settled()));
    }

    /**
     * And the sides themselves hold every clause, named or not. Which is the same distinction one
     * layer down from where {@link #onlyTheClausesThatWereNamedCanBeNamed} holds it: a side that
     * held only the clauses a message could name would have nothing left to say about the others,
     * and being unable to say what a clause is called is not being unable to say anything.
     */
    @Test
    void bothSidesHoldEveryClauseAndNotOnlyTheNamedOnes() {
        assertEquals(1, judgmentOn(NEITHER).unsettled().size(),
                "one clause, left standing, written with no name");
        assertEquals(0, judgmentOn(NEITHER).settled().size());

        assertEquals(1, judgmentOn(SETTLED_ONLY).unsettled().size(),
                "the unnamed clause the guards left standing is on this side");
        assertEquals(1, judgmentOn(SETTLED_ONLY).settled().size());
    }

    /**
     * The one that fixes the two questions apart. An unnamed clause the guards left standing keeps
     * the verdict unproven and leaves nothing in {@code unsettled} to name, while the clause they did
     * establish is there to be named — so a reader of either set alone reads it wrongly.
     */
    @Test
    void anUnnamedUnsettledClauseLeavesTheSettledOneNameable() {
        Judgment judgment = judgmentOn(SETTLED_ONLY);
        assertEquals(Verdict.UNKNOWN, judgment.verdict());
        assertEquals(List.of(), names(judgment.unsettled()),
                "the clause left standing was written without a name");
        assertEquals(List.of("lowNonNegative"), names(judgment.settled()),
                "the clause the guard established was written with one");
    }

    // --- which spelling a reader is answered in --------------------------------------------------

    @Test
    void aWarningNamingBothSaysBoth() {
        InvariantMessage.NothingKnownHereEstablishesButDoesEstablish said = assertInstanceOf(
                InvariantMessage.NothingKnownHereEstablishesButDoesEstablish.class, warningOn(BOTH));
        assertEquals("ordered", said.unsettled());
        assertEquals("lowNonNegative", said.settled());
        assertTrue(rendered(BOTH).contains("Established here: lowNonNegative."),
                "the clause the guards established is written out: " + rendered(BOTH));
    }

    /** Nothing was established that can be named, so the warning does not have that sentence. It
     * used to be written with the sentence in it and an empty list after the colon. */
    @Test
    void aWarningWithNothingEstablishedToNameDoesNotSayEstablished() {
        InvariantMessage.NothingKnownHereEstablishes said = assertInstanceOf(
                InvariantMessage.NothingKnownHereEstablishes.class, warningOn(UNSETTLED_ONLY));
        assertEquals("ordered", said.unsettled());
        assertFalse(rendered(UNSETTLED_ONLY).contains("Established here"),
                "nothing was established to name, so nothing is said of it: "
                        + rendered(UNSETTLED_ONLY));
    }

    /**
     * The established clause is written out whether or not the clause left standing had a name. The
     * warning used to drop it here: the spelling was chosen by asking the unsettled names, and that
     * spelling had nowhere to put what was established.
     */
    @Test
    void aWarningThatCannotNameWhatIsUnsettledStillSaysWhatIsEstablished() {
        InvariantMessage.NothingKnownHereEstablishesTheInvariantButDoesEstablish said = assertInstanceOf(
                InvariantMessage.NothingKnownHereEstablishesTheInvariantButDoesEstablish.class,
                warningOn(SETTLED_ONLY));
        assertEquals("lowNonNegative", said.settled());
        String text = rendered(SETTLED_ONLY);
        assertTrue(text.contains("its invariant"), text);
        assertTrue(text.contains("Established here: lowNonNegative."), text);
    }

    @Test
    void aWarningWithNeitherSideNameableSaysTheInvariant() {
        assertInstanceOf(InvariantMessage.NothingKnownHereEstablishesTheInvariant.class,
                warningOn(NEITHER));
        assertFalse(rendered(NEITHER).contains("Established here"), rendered(NEITHER));
    }

    /** Whatever is said, no spelling of E2011 ends with a list that has nothing in it. */
    @Test
    void noSpellingIsWrittenAroundAnEmptyList() {
        for (String source : List.of(BOTH, UNSETTLED_ONLY, SETTLED_ONLY, NEITHER)) {
            String text = rendered(source);
            assertFalse(text.contains("Established here: ."),
                    "a list with nothing in it, punctuated as if it were one: " + text);
        }
    }

    // --- reading the check -----------------------------------------------------------------------

    private static Judgment judgmentOn(String source) {
        List<Said> said = Collections.synchronizedList(new ArrayList<>());
        InvariantChecker.WATCHING = said;
        try {
            Compiler.compileWithWarnings(source);
        } finally {
            InvariantChecker.WATCHING = null;
        }
        assertFalse(said.isEmpty(), "nothing was checked, so nothing here is being held to anything");
        return said.stream().filter(s -> s.type().equals("Bound")).findFirst().orElseThrow()
                .judgment();
    }

    /** The names of the clauses on a side that carry one, which is what a message writes out. */
    private static List<String> names(java.util.SequencedMap<Clause.Id, Clause.Ref> clauses) {
        return clauses.values().stream().map(Clause.Ref::name)
                .flatMap(java.util.Optional::stream).map(ClauseName::value).toList();
    }

    private static Message warningOn(String source) {
        return warning(source).said();
    }

    private static String rendered(String source) {
        return Messages.render(warning(source).said(), Locale.ENGLISH);
    }

    private static Diagnostic warning(String source) {
        List<Diagnostic> found = Compiler.compileWithWarnings(source).warnings().stream()
                .filter(d -> "E2011".equals(d.code()))
                .toList();
        assertEquals(1, found.size(), "expected one E2011 to hold: " + found);
        return found.get(0);
    }
}
