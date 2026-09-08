package souther.compiler.values;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every reading is held against the models it could be about.
 *
 * <p>The other tests here compare answers this code produced with answers this code produced. They
 * catch a reading that contradicts itself and cannot catch one that agrees with itself about the
 * wrong thing. This one writes down what a rule means — a set of the values a whole record may take
 * — composes those sets by the connectives, and asks whether the reading is true of the result.
 *
 * <p>Three things are asked of every reading over every model it could be about:
 *
 * <pre>
 *     at(p)             holds every value the model leaves at p          an upper approximation
 *     guaranteedAt(p)   holds none the model does not leave at p         a lower one
 *     speaksFor(p)      only where at(p) is exactly what the model leaves
 * </pre>
 *
 * <p>A rule this reading has no word for is not one model but every model it could be: any reading
 * that narrows only the positions the rule names. Two positions over a carrier of two values give
 * sixteen sets of records, so the whole of that can be enumerated rather than sampled.
 *
 * <p><b>One assumption of the reading is written into the model, because it is what a conjunction's
 * account of an unread rule rests on.</b> A rule this could not read narrows only the positions it
 * names. Without it a rule naming nothing could narrow everything, and no conjunction could speak
 * for any position. It is what {@code AdmissibleValues} says of itself, and a model that let it go
 * would be testing a different language.
 *
 * <p><b>That the type is left with a value in it is asked of the whole and not of a rule.</b> The
 * three questions above are about what stands at a position, which is a question about a type that
 * has a value — a model leaving none is what {@code isBottom} is for. Required of each rule
 * instead, a choice would be read only against models in all of which both its alternatives stand,
 * and what a reading says on the strength of a branch nobody may be in would never be asked about.
 */
class WhatIsReadIsHeldAgainstTheModelsItCouldBeTest {

    private static final String VALUE = "value";
    private static final String OTHER = "other";
    private static final Value A = Value.text("A");
    private static final Value B = Value.text("B");

    /** What puts the sets of these readings together. Every set here is values written out, so
     *  nothing is built and no allowance is spent. */
    private static final Allowance<String> SETS = AsACompilationAllows.forAdmittedValues();

    /**
     * A record of this model is one of four, and a set of them is a bit of a nibble.
     *
     * <pre>
     *     bit 0   value = A, other = A
     *     bit 1   value = A, other = B
     *     bit 2   value = B, other = A
     *     bit 3   value = B, other = B
     * </pre>
     */
    private static final int EVERY_RECORD = 0b1111;

    /**
     * A reading of some rules, beside every set of records those rules could leave.
     *
     * <p>The two are worked out apart. What the models say is put together out of the sets of
     * records above by {@code &} and {@code |}; what this compiler says is put together by the
     * connectives it composes descriptions with. A reading taken from either side to make the
     * other would leave this asking whether the compiler agrees with itself.
     *
     * @param planned what this compiler read the rules into, before any of it is worked out
     * @param opened what the alternatives nothing could read left open, gathered up the clause and
     *                  told to the answer once
     * @param readAbout the positions the rules this could read are about
     * @param choicesOverOnePosition whether every choice in it is between alternatives the reading
     *                  took in about no more than one position between them. Where it is not, what
     *                  a position holds is read across a choice one position at a time and comes
     *                  out wider than the model with every rule read — which is a defect of its
     *                  own and not one an unread rule is answerable for
     * @param holdsSomethingUnread whether any clause of it is one this reading has no word for.
     *                  What a choice between such a clause and one that was read leaves open is a
     *                  fact about the clause as it is written here, so it is decided here and
     *                  handed to the join rather than worked out from what the readings hold
     */
    private record Rule(String wrote, PlannedValues<String> planned, Set<String> opened,
                        List<Integer> leaves, Set<String> readAbout,
                        boolean choicesOverOnePosition, boolean holdsSomethingUnread) {

        /** What a reader is handed: the description worked out, and told what the alternatives
         *  nothing could read left open. Said once, over the whole of what the clauses came to. */
        AdmissibleValues<String> answer() {
            return planned.resolve(SETS).values().alsoOpenedAt(opened);
        }
    }

    /** Which values stand at {@code atom} in {@code records}, as a pair of bits. */
    private static int standingAt(String atom, int records) {
        int out = 0;
        for (int record = 0; record < 4; record++) {
            if ((records & (1 << record)) != 0) {
                out |= 1 << (atom.equals(VALUE) ? record >> 1 : record & 1);
            }
        }
        return out;
    }

    /** The same for what a reading says, over the two values this model has. */
    private static int read(ValueSet set) {
        int out = 0;
        if (holds(set, A)) {
            out |= 1;
        }
        if (holds(set, B)) {
            out |= 0b10;
        }
        return out;
    }

    private static boolean holds(ValueSet set, Value value) {
        return set.has(value);
    }

    /** A rule read in full: one set of records and no doubt about it. */
    private static Rule read(String wrote, ValueSet leaves, String about, int records) {
        return new Rule(wrote, PlannedValues.at(about, AdmittedPlan.of(leaves)), Set.of(),
                List.of(records), Set.of(about), true, false);
    }

    /**
     * A rule this reading has no word for: every set of records that narrows only what it names,
     * the empty one among them.
     *
     * <p>A rule nothing could read may be one nothing satisfies, and where it is an alternative of
     * a choice the branch beside it is what the rules leave. Left out, every choice would be read
     * against models in all of which both its alternatives stand, and what a reading says on the
     * strength of a branch nobody may be in would never be asked about. What is asked of the
     * positions is still asked of models that leave a value — {@link #heldAgainstItsModels} skips
     * the empty one, since what stands at a position is a question about a type that has one.
     */
    private static Rule unread(String wrote, Set<String> names, UnreadReason why) {
        List<Integer> could = new ArrayList<>();
        for (int records = 0; records <= EVERY_RECORD; records++) {
            if (narrowsOnly(names, records)) {
                could.add(records);
            }
        }
        return new Rule(wrote, PlannedValues.unreadable(names, why), Set.of(), could, Set.of(),
                true, true);
    }

    /** Whether {@code records} is settled by the named positions alone. */
    private static boolean narrowsOnly(Set<String> names, int records) {
        for (int record = 0; record < 4; record++) {
            for (int against = 0; against < 4; against++) {
                boolean sameWhereNamed = (!names.contains(VALUE) || (record >> 1) == (against >> 1))
                        && (!names.contains(OTHER) || (record & 1) == (against & 1));
                if (sameWhereNamed
                        && ((records >> record) & 1) != ((records >> against) & 1)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static List<Rule> rules() {
        return List.of(
                read("value == A", ValueSet.just(A), VALUE, 0b0011),
                read("value == B", ValueSet.just(B), VALUE, 0b1100),
                read("other == A", ValueSet.just(A), OTHER, 0b0101),
                read("value /= A", ValueSet.allBut(A), VALUE, 0b1100),
                unread("f(value)", Set.of(VALUE), UnreadReason.FORM_NOT_READ),
                unread("f(other)", Set.of(OTHER), UnreadReason.FORM_NOT_READ),
                unread("value /= other", Set.of(VALUE, OTHER), UnreadReason.RELATES_TWO_POSITIONS),
                unread("f()", Set.of(), UnreadReason.FORM_NOT_READ));
    }

    /**
     * The positions a choice between these two is left narrower at without {@code alternative},
     * which is what an unread one takes back.
     *
     * <p>Out of the record sets and nothing else. What the compiler does with this question it
     * does by comparing descriptions, so a model side that compared descriptions too would be the
     * compiler agreeing with itself — which is the one thing this file exists not to do. Here it
     * is what the models say: a choice between two sets of records is their union, so the question
     * is whether taking one side away leaves fewer values standing at the position.
     *
     * <p>Over every pair a model could be, since what is opened is said of the clause and not of
     * one model of it. A pair leaving no record at all is passed over, as it is where the answers
     * are asked: what stands at a position is a question about a type that has a value.
     */
    private static Set<String> widthRestingOn(Rule alternative, Rule beside) {
        Set<String> out = new LinkedHashSet<>();
        for (int mine : alternative.leaves()) {
            for (int theirs : beside.leaves()) {
                if ((mine | theirs) == 0) {
                    continue;
                }
                for (String atom : List.of(VALUE, OTHER)) {
                    if (standingAt(atom, mine | theirs) != standingAt(atom, theirs)) {
                        out.add(atom);
                    }
                }
            }
        }
        return out;
    }

    private static Rule both(Rule left, Rule right) {
        return compose(left, right, "&&");
    }

    private static Rule either(Rule left, Rule right) {
        return compose(left, right, "||");
    }

    /**
     * The half of a rule that is this compiler's answer, as one value.
     *
     * <p>Held together because a choice settles all of it or none of it. What an alternative
     * leaves and whether it holds a clause nothing read are what the next choice out reads to
     * decide what it opened, so a settlement that took the branch that stands for the values and
     * left these as the two branches together would answer the outer choice out of a branch nobody
     * can be in. Built nowhere but in the two below, so a part of it added later has to be settled
     * rather than composed beside them.
     */
    private record Answer(PlannedValues<String> planned, Set<String> opened, Set<String> readAbout,
                          boolean choicesOverOnePosition, boolean holdsSomethingUnread) {}

    private static Rule compose(Rule left, Rule right, String by) {
        Set<Integer> could = new LinkedHashSet<>();
        left.leaves().forEach(here -> right.leaves().forEach(there ->
                could.add(by.equals("&&") ? here & there : here | there)));
        Answer answer = by.equals("&&") ? conjoined(left, right) : settled(left, right);
        return new Rule("(" + left.wrote() + " " + by + " " + right.wrote() + ")",
                answer.planned(), answer.opened(), List.copyOf(could), answer.readAbout(),
                answer.choicesOverOnePosition(), answer.holdsSomethingUnread());
    }

    /**
     * Both readings holding at once.
     *
     * <p>Every clause of both is one somebody satisfying the whole is under, so all of what either
     * side is answerable for is what the conjunction is answerable for. Nothing is opened: a
     * conjunction has no alternative for a position to be open in, and what either side already had
     * opened travels up with it, to be told to the answer once where the whole of what the clauses
     * came to is in hand.
     */
    private static Answer conjoined(Rule left, Rule right) {
        Set<String> about = new LinkedHashSet<>(left.readAbout());
        about.addAll(right.readAbout());
        Set<String> opened = new LinkedHashSet<>(left.opened());
        opened.addAll(right.opened());
        return new Answer(left.planned().meet(right.planned()), opened, about,
                left.choicesOverOnePosition() && right.choicesOverOnePosition(),
                left.holdsSomethingUnread() || right.holdsSomethingUnread());
    }

    /**
     * What a choice comes to, settled the way the holder of both languages settles one.
     *
     * <p>Four cases and not one. A branch nobody can be in is not composed: where one is, the
     * choice is the branch that stands, and where neither is, the settlement says so of the two of
     * them. Composed instead, this would be asking the description algebra a question its contract
     * says it is not asked — and then what is compared against the models below would be this
     * compiler's arithmetic reached a way no compile reaches it.
     *
     * <p>Which branches those are is asked of this compiler's own reading and never of the models:
     * taken from the sets a rule was written down as leaving, the answer would be the expected side
     * deciding what the compiler does, and the two would agree because one of them was made out of
     * the other.
     *
     * <p><b>The whole of the answer follows the four cases and not the values alone.</b> Where one
     * branch stands the choice is that branch, so what it leaves, whether it holds a clause
     * nothing read, and what its own choices reached are the standing branch's — these are what the
     * next choice out reads to decide what it opened, and taken from the two branches together it
     * would be answering out of a branch nobody can be in.
     *
     * <p>A choice neither branch of which stands is neither of them. It leaves nothing and its
     * alternatives lost nothing, and what showed it empty is what showed both — which is why that
     * one keeps what either branch could not read.
     */
    private static Answer settled(Rule left, Rule right) {
        return switch (Emptiness.Alternatives.of(said(left), said(right))) {
            case NEITHER_STANDS -> new Answer(
                    left.planned().bothDead(right.planned()),
                    Set.of(), Set.of(), true,
                    left.holdsSomethingUnread() || right.holdsSomethingUnread());
            case ONLY_THE_RIGHT -> standing(right);
            case ONLY_THE_LEFT -> standing(left);
            case BOTH_STAND -> bothStanding(left, right);
        };
    }

    /** A branch's fate, in the words the classification is read in, and asked of this compiler's
     *  own reading rather than of the models — see {@link #settled}. */
    private static Emptiness said(Rule branch) {
        return branch.planned().holdsNothingAsBuilt(SETS) ? Emptiness.EMPTY : Emptiness.NONEMPTY;
    }

    /** What a choice both branches of which stand comes to, which is the one case the values
     *  compose. */
    private static Answer bothStanding(Rule left, Rule right) {
        Set<String> about = new LinkedHashSet<>(left.readAbout());
        about.addAll(right.readAbout());
        Set<String> opened = new LinkedHashSet<>(left.opened());
        opened.addAll(right.opened());
        // The positions the choice is as wide as it is at because of an alternative nothing could
        // read, said where the two of them are what was written.
        if (left.holdsSomethingUnread()) {
            opened.addAll(widthRestingOn(left, right));
        }
        if (right.holdsSomethingUnread()) {
            opened.addAll(widthRestingOn(right, left));
        }
        return new Answer(left.planned().joinLive(right.planned()), opened, about,
                left.choicesOverOnePosition() && right.choicesOverOnePosition()
                        && about.size() <= 1,
                left.holdsSomethingUnread() || right.holdsSomethingUnread());
    }

    /** What a choice one branch of which nobody can be in comes to, which is that branch — all of
     *  it, and not its values with the two branches' account beside them. */
    private static Answer standing(Rule branch) {
        return new Answer(branch.planned(), branch.opened(), branch.readAbout(),
                branch.choicesOverOnePosition(), branch.holdsSomethingUnread());
    }

    /**
     * Every answer a rule's reading gives is true of every model that rule could be about.
     *
     * <p>Of the models that leave a value, since what stands at a position is asked of a type that
     * has one — {@code isBottom} is the question about the other kind, and a rule this could not
     * read is one the reading cannot tell an empty type from a narrowed one by.
     *
     * <p>What it speaks for is asked of the readings whose choices the alternatives were read
     * across one position at a time without loss — {@link Rule#choicesOverOnePosition}. A choice
     * between alternatives about two positions is read here as the pair of what each position holds
     * on its own, which is wider than the model with every rule read, and that is a defect of the
     * representation rather than one an unread rule is answerable for. The bounds above are asked
     * of every reading all the same, since neither of them may be wrong for any reason.
     */
    private static void heldAgainstItsModels(Rule rule) {
        for (int records : rule.leaves()) {
            if (records == 0) {
                continue;
            }
            for (String atom : List.of(VALUE, OTHER)) {
                int stands = standingAt(atom, records);
                int holds = read(rule.answer().at(atom));
                int promised = read(rule.answer().guaranteedAt(atom));
                assertTrue((stands & ~holds) == 0, () -> rule.wrote()
                        + ": at " + atom + " the model leaves " + stands + " and the reading holds "
                        + holds + ", which is short of it");
                assertTrue((promised & ~stands) == 0, () -> rule.wrote()
                        + ": at " + atom + " the reading promises " + promised
                        + " and the model leaves " + stands + ", which is less than promised");
                assertTrue(!rule.choicesOverOnePosition() || !rule.answer().speaksFor(atom)
                                || holds == stands,
                        () -> rule.wrote() + ": at " + atom + " the reading speaks for " + holds
                                + " and the model leaves " + stands);
            }
        }
    }

    /** One rule, and two of them stated together and as alternatives. */
    @Test
    void oneRuleAndTwo() {
        rules().forEach(WhatIsReadIsHeldAgainstTheModelsItCouldBeTest::heldAgainstItsModels);
        rules().forEach(left -> rules().forEach(right -> {
            heldAgainstItsModels(both(left, right));
            heldAgainstItsModels(either(left, right));
        }));
    }

    /** And three of them, every way of composing and bracketing them. */
    @Test
    void andThreeOfThemHoweverComposed() {
        rules().forEach(left -> rules().forEach(middle -> rules().forEach(right -> {
            heldAgainstItsModels(either(either(left, middle), right));
            heldAgainstItsModels(either(left, either(middle, right)));
            heldAgainstItsModels(both(either(left, middle), right));
            heldAgainstItsModels(both(left, either(middle, right)));
            heldAgainstItsModels(either(both(left, middle), right));
            // A conjunction of two rules is where a branch nobody can be in comes from, and the
            // enumeration above only ever puts one on the left of a choice. Without this, what a
            // choice with a dead branch leaves would be held one way round out of two.
            heldAgainstItsModels(either(left, both(middle, right)));
            heldAgainstItsModels(both(both(left, middle), right));
        })));
    }

    /**
     * And a choice neither branch of which anybody can be in.
     *
     * <p>The fourth of the four cases a choice is settled by, which the enumerations above do not
     * reach: they compose one dead branch at a time, and this one needs two. Written out of rules
     * that were read, so that what makes each branch impossible is something this compiler worked
     * out rather than something a model was told.
     */
    @Test
    void andAChoiceNeitherBranchOfWhichAnybodyCanBeIn() {
        Rule isA = rules().get(0);
        Rule isB = rules().get(1);
        Rule notA = rules().get(3);

        heldAgainstItsModels(either(both(isA, isB), both(isA, notA)));
        heldAgainstItsModels(either(both(isA, notA), both(isA, isB)));
    }

    /**
     * And a choice whose dead branch is the only one holding a clause nothing read.
     *
     * <p>What the choice comes to is the branch that stands, which read everything it was given.
     * Answered with the two branches' accounts put together, it would say a clause of it went
     * unread — and the next choice out reads that to decide what an alternative beside an unread
     * one leaves, so the reading would take back a position on the strength of a branch nobody can
     * be in.
     *
     * <p>Three rules deep on one side, which is what it takes: a conjunction of two is dead only
     * where both were read, so the clause nothing read is the third.
     */
    @Test
    void andAChoiceWhoseDeadBranchIsTheOnlyOneThatMissedARule() {
        Rule isA = rules().get(0);
        Rule isB = rules().get(1);
        Rule otherIsA = rules().get(2);
        Rule unreadAboutValue = rules().get(4);
        Rule dead = both(both(unreadAboutValue, isA), isB);

        assertTrue(dead.holdsSomethingUnread(), "the dead branch is the one that missed a rule");
        assertFalse(otherIsA.holdsSomethingUnread(), "and the standing one read what it was given");

        heldAgainstItsModels(either(dead, otherIsA));
        heldAgainstItsModels(either(otherIsA, dead));
        // And with one more choice around it, which is what reads the account the settlement left.
        heldAgainstItsModels(either(either(dead, otherIsA), isB));
        heldAgainstItsModels(either(isB, either(otherIsA, dead)));
    }
}
