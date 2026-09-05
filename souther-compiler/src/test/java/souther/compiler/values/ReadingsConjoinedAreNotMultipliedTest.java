package souther.compiler.values;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Readings that share no position are held together rather than multiplied together.
 *
 * <p>A reading is a union of products, so a conjunction of two distributes. Where the two speak
 * about the same positions that is the answer and there is nothing cheaper to have. Where they share
 * nothing, no pair is ever dropped and every product is the two put side by side — the alternatives
 * multiply and say what the pair already said.
 *
 * <p>Which is what a behavior's input is made of: one reading per parameter, each over positions the
 * others do not name. Multiplied, ten parameters of a two-alternative record are a thousand and
 * twenty-four alternatives, and the budget that admitted each reading was counted per declaration.
 */
class ReadingsConjoinedAreNotMultipliedTest {

    /**
     * What puts the sets of these readings together.
     *
     * <p>One for the file, and one for every conjunction in it: a conjunction is the one place two
     * readings of a declaration come together, so the composer that paid for the sets in one is the
     * composer the next meet spends from. Every set here is values written out, so nothing is built
     * and no allowance is spent.
     */
    private static final Allowance<String> SETS = AsACompilationAllows.forAdmittedValues();

    /** Two readings over positions neither shares, each holding two alternatives. */
    @Test
    void readingsOverDisjointPositionsStayApart() {
        ConjoinedAdmissibleValues<String> both =
                ConjoinedAdmissibleValues.of(twoAlternatives("a", "b"), SETS)
                        .meet(ConjoinedAdmissibleValues.of(twoAlternatives("c", "d"), SETS));

        assertEquals(2, both.factors().size(), "one factor per reading");
        both.factors().forEach(each -> assertEquals(2, alternativesOf(each),
                "and each holds what it held"));
    }

    /** And the answers are the ones the product would have given. */
    @Test
    void andEachPositionIsAnsweredAsItWas() {
        ConjoinedAdmissibleValues<String> both =
                ConjoinedAdmissibleValues.of(twoAlternatives("a", "b"), SETS)
                        .meet(ConjoinedAdmissibleValues.of(twoAlternatives("c", "d"), SETS));

        assertEquals(twoAlternatives("a", "b").at("a"), both.at("a"));
        assertEquals(twoAlternatives("c", "d").at("d"), both.at("d"));
        assertEquals(Emptiness.NONEMPTY, both.anyAlternativeAdmits((_, _) -> Emptiness.NONEMPTY,
                (_, _) -> new Apartness.Reduction.Standing<>()));
    }

    /**
     * Merged over connected components and not over pairs.
     *
     * <p>{@code {a,b}} beside {@code {c}}, met with {@code {b,c}}: merging the first against the
     * third makes a factor over {@code {a,b,c}}, which now meets the second. Merged pairwise in one
     * pass, two factors would be left sharing {@code c}, and every answer that rests on the
     * vocabularies being disjoint would be read off a factor that is not the only one naming its
     * subject.
     */
    @Test
    void factorsReachingEachOtherThroughAThirdAreOneFactor() {
        ConjoinedAdmissibleValues<String> apart =
                ConjoinedAdmissibleValues.of(twoAlternatives("a", "b"), SETS)
                        .meet(ConjoinedAdmissibleValues.of(
                                AdmissibleValues.at("c", just("x")), SETS));
        assertEquals(2, apart.factors().size());

        ConjoinedAdmissibleValues<String> joined = apart.meet(ConjoinedAdmissibleValues.of(
                AdmissibleValues.at("b", just("y"))
                        .meet(AdmissibleValues.at("c", just("x")), SETS), SETS));

        assertEquals(1, joined.factors().size(), "all three reach each other");
        assertEquals(Set.of("a", "b", "c"), joined.subjects());
        assertEquals(just("y"), joined.at("b"));
        assertEquals(just("x"), joined.at("c"));
    }

    /** A factor holding nothing leaves the conjunction nothing, whatever the others admit. */
    @Test
    void oneFactorHoldingNothingIsTheWholeHoldingNothing() {
        ConjoinedAdmissibleValues<String> both =
                ConjoinedAdmissibleValues.of(twoAlternatives("a", "b"), SETS)
                        .meet(ConjoinedAdmissibleValues.of(
                                AdmissibleValues.at("c", just("x"))
                                        .meet(AdmissibleValues.at("c", just("y")), SETS), SETS));

        assertEquals(Emptiness.EMPTY, both.anyAlternativeAdmits((_, _) -> Emptiness.NONEMPTY,
                (_, _) -> new Apartness.Reduction.Standing<>()));
    }

    /**
     * The readings of a component are met in the order they arrived, not in the order they were
     * reached.
     *
     * <p>{@link AdmissibleValues#meet} does not answer the same either way. Every reason two
     * readings gave at one position is kept, and they are kept in the order the readings were met —
     * so which order that is decides the order an author is shown their own rules in, and it has to
     * be the order the readings arrived, since that is the order a conjunction holds them in.
     *
     * <p>Three readings met at once, in a shape where the two orders differ. Held apart are one over
     * {@code {a, b}} and one over {@code {x}}, which share nothing; met with them is one over
     * {@code {b, x}}, which reaches both. Walking out of the first, {@code b} leads to the bridge and
     * the bridge leads to the third — so the bridge is reached second and the third last, while the
     * order they arrived in is the other way round. Both of the last two say why {@code x} went
     * unread, and the order they are said in is the whole difference.
     */
    @Test
    void aComponentIsMetInTheOrderItsReadingsArrived() {
        AdmissibleValues<String> named = AdmissibleValues.at("a", just("x"))
                .meet(AdmissibleValues.at("b", just("y")), SETS);
        AdmissibleValues<String> arrivedSecond =
                AdmissibleValues.unreadable(Set.of("x"), UnreadReason.FORM_NOT_READ);
        AdmissibleValues<String> bridge =
                AdmissibleValues.unreadable(Set.of("b", "x"), UnreadReason.RELATES_TWO_POSITIONS);

        ConjoinedAdmissibleValues<String> apart = ConjoinedAdmissibleValues.of(named, SETS)
                .meet(ConjoinedAdmissibleValues.of(arrivedSecond, SETS));
        assertEquals(2, apart.factors().size(), "these two share nothing");

        ConjoinedAdmissibleValues<String> joined =
                apart.meet(ConjoinedAdmissibleValues.of(bridge, SETS));

        assertEquals(1, joined.factors().size(), "and the third reaches both of them");
        assertEquals(List.of(UnreadReason.FORM_NOT_READ, UnreadReason.RELATES_TWO_POSITIONS),
                joined.whyUnread("x"),
                "both readings say why, in the order they arrived");
    }

    /**
     * A reading says nothing about a subject it does not name.
     *
     * <p>Which is what makes the two answers above exact rather than approximate: a factor that does
     * not name a position admits every value at it, so leaving it out of the answer leaves out
     * nothing. Fixed here because the whole design rests on it — a reading that constrained a
     * subject outside its own vocabulary could not be conjoined without being expanded.
     */
    @Test
    void aReadingSaysNothingAboutASubjectItDoesNotName() {
        AdmissibleValues<String> read = twoAlternatives("a", "b");

        assertFalse(read.subjects().contains("elsewhere"));
        assertTrue(read.at("elsewhere").isAny(), "every value stands at a position nothing named");
        assertTrue(read.projectionExactAt("elsewhere"));
        assertEquals(List.of(), read.whyUnread("elsewhere"));

        assertTrue(ConjoinedAdmissibleValues.of(read, SETS).at("elsewhere").isAny());
        assertTrue(ConjoinedAdmissibleValues.<String>top().at("elsewhere").isAny(),
                "and a conjunction of no readings names nothing at all");
    }

    /**
     * Whether a reading was taken is not how many factors there are.
     *
     * <p>Two readings that name the same position are one factor, since the exact conjunction of
     * those is their product — so counting the factors counts the vocabularies the readings fell
     * into and not the readings. What a caller taking a reading in once needs to know is whether one
     * was taken, and normalising never leaves nothing where a reading went in, so that question is
     * the same of both.
     */
    @Test
    void whetherAReadingWasTakenIsNotHowManyFactorsThereAre() {
        ConjoinedAdmissibleValues<String> nothing = ConjoinedAdmissibleValues.top();
        assertFalse(nothing.hasReadings());

        ConjoinedAdmissibleValues<String> overOneVocabulary =
                ConjoinedAdmissibleValues.of(AdmissibleValues.at("x", just("A")), SETS)
                        .meet(ConjoinedAdmissibleValues.of(
                                AdmissibleValues.at("x", just("B")), SETS));

        assertEquals(1, overOneVocabulary.factors().size(),
                "two readings of one position are one factor");
        assertTrue(overOneVocabulary.hasReadings(), "and two readings were taken all the same");
    }

    /**
     * A position a reading knows only as one a choice opened is still one it is about.
     *
     * <p>The proof the tripwire below is a tripwire for. What is filed under a subject is what
     * {@link AdmissibleValues#subjects} has to reach, and a reading shown to hold nothing keeps
     * what a choice opened while it keeps nothing else — so a position can be there and nowhere
     * else. Missed, the conjunction reads it as a name this reading never heard of and answers
     * about it from a factor that never named it.
     */
    @Test
    void aPositionAChoiceOpenedIsOneTheReadingIsAbout() {
        AdmissibleValues<String> opened = AdmissibleValues.at("x", just("A"))
                .join(AdmissibleValues.unreadable(Set.of(), UnreadReason.FORM_NOT_READ), SETS)
                .alsoOpenedAt(Set.of("x"))
                .leavingNothing();

        assertEquals(Set.of("x"), opened.subjects(),
                "the reading holds `x` nowhere but among the positions a choice opened, and it is"
                        + " a position the reading is about all the same");
        assertEquals(opened.whyUnread("x"),
                ConjoinedAdmissibleValues.of(opened, SETS).whyUnread("x"),
                "so a conjunction of it answers about `x` out of the factor that names it");
    }

    /**
     * A tripwire, and not the proof above it.
     *
     * <p>{@link AdmissibleValues#subjects} is what says which positions a reading is about, and
     * everything here rests on it being all of them. A place a subject is filed under that it does
     * not reach leaves a reading naming a position {@code subjects()} does not — so the conjunction
     * calls two overlapping factors disjoint and answers one of them from the other's silence.
     *
     * <p>The same list {@link AdmissibleValues#renamed} rewrites, for the same reason.
     *
     * <p><b>Every component named and classified, rather than counted.</b> A count says nothing
     * about a component whose type changed under its name: turning a flag about the whole reading
     * into something the positions are filed under adds a place a subject comes from and leaves the
     * count where it was. So each of them is written down here as one that holds subjects or one
     * that does not, and a component that changes its mind about which is a change to this list.
     *
     * <p>{@link Standing} holds both kinds of evidence there are — a rule of the positions that
     * went unread, and a position an alternative nothing could read left open — so a subject
     * reaching this reading through either arrives under the same name.
     */
    @Test
    void everyPlaceASubjectIsFiledUnderIsOneSubjectsKnowsAbout() {
        List<String> named = java.util.Arrays.stream(AdmissibleValues.class.getRecordComponents())
                .map(RecordComponent::getName).toList();

        assertEquals(List.of("held", "perPosition", "standing", "guaranteed", "defaultGuaranteed",
                        "guaranteedTogether", "tangled", "widened"),
                named,
                "a reading holds something this list does not name. If it is filed under a subject,"
                        + " `subjects()` and `renamed()` both have to reach it; if it is not, say so"
                        + " below");
        assertEquals(List.of("held", "perPosition", "standing", "guaranteed", "tangled", "widened"),
                named.stream().filter(each -> !NAMES_NO_SUBJECT.contains(each)).toList(),
                "which of them file a position under a subject, which is what `subjects()` and"
                        + " `renamed()` are the two readings of");
    }

    /** What a reading holds about itself rather than about any position of the value. */
    private static final Set<String> NAMES_NO_SUBJECT =
            Set.of("defaultGuaranteed", "guaranteedTogether");

    /** A reading of two positions leaving two alternatives, which is what a choice written across
     *  two positions comes to. */
    private static AdmissibleValues<String> twoAlternatives(String one, String other) {
        return AdmissibleValues.at(one, just("x"))
                .meet(AdmissibleValues.at(other, just("y")), SETS)
                .joinApart(AdmissibleValues.at(one, just("p"))
                        .meet(AdmissibleValues.at(other, just("q")), SETS), SETS);
    }

    private static ValueSet just(String text) {
        return ValueSet.just(Value.text(text));
    }

    private static int alternativesOf(AdmissibleValues<String> read) {
        return read.held() instanceof AdmissibleValues.Held.Alternatives<String> it
                ? it.boxes().size() : 0;
    }
}
