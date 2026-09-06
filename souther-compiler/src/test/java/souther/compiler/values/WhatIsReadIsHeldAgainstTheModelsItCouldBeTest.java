package souther.compiler.values;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
 * <p><b>Two assumptions of the reading are written into the model, because they are what a
 * conjunction's account of an unread rule rests on.</b> A rule this could not read narrows only the
 * positions it names, and leaves the type with a value in it. Without the first, a rule naming
 * nothing could narrow everything and no conjunction could speak for any position; without the
 * second, one could empty the type and every answer about every position would be wrong. Both are
 * what {@code AdmissibleValues} says of itself, and a model that let them go would be testing a
 * different language.
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
     * A rule this reading has no word for: every set of records that narrows only what it names and
     * leaves the type with a value in it.
     */
    private static Rule unread(String wrote, Set<String> names, UnreadReason why) {
        List<Integer> could = new ArrayList<>();
        for (int records = 0; records <= EVERY_RECORD; records++) {
            if (records != 0 && narrowsOnly(names, records)) {
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
     * What an alternative promised, which is what an unread one beside it takes back.
     *
     * <p>Nothing where it holds a clause nothing read: what a rule promises is what it promises
     * having read everything it was given, so there is nothing there for the alternative beside it
     * to widen.
     */
    private static Set<String> promisedBy(Rule alternative) {
        return alternative.holdsSomethingUnread() ? Set.of() : alternative.readAbout();
    }

    private static Rule both(Rule left, Rule right) {
        return compose(left, right, "&&");
    }

    private static Rule either(Rule left, Rule right) {
        return compose(left, right, "||");
    }

    private static Rule compose(Rule left, Rule right, String by) {
        Set<Integer> could = new LinkedHashSet<>();
        left.leaves().forEach(here -> right.leaves().forEach(there ->
                could.add(by.equals("&&") ? here & there : here | there)));
        Set<String> about = new LinkedHashSet<>(left.readAbout());
        about.addAll(right.readAbout());
        boolean overOne = left.choicesOverOnePosition() && right.choicesOverOnePosition()
                && (by.equals("&&") || about.size() <= 1);
        // What a choice between these two leaves open, said where the two of them are what was
        // written: the positions the alternative beside an unread one reached. A conjunction leaves
        // nothing open, since both of its clauses hold.
        Set<String> opened = new LinkedHashSet<>();
        if (by.equals("||")) {
            if (left.holdsSomethingUnread()) {
                opened.addAll(promisedBy(right));
            }
            if (right.holdsSomethingUnread()) {
                opened.addAll(promisedBy(left));
            }
        }
        // What either side already had opened travels up with it: the positions are told to the
        // answer once, where the whole of what the clauses came to is in hand.
        opened.addAll(left.opened());
        opened.addAll(right.opened());
        return new Rule("(" + left.wrote() + " " + by + " " + right.wrote() + ")",
                by.equals("&&") ? left.planned().meet(right.planned())
                        : left.planned().joinLive(right.planned()),
                opened, List.copyOf(could), about, overOne,
                left.holdsSomethingUnread() || right.holdsSomethingUnread());
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
            heldAgainstItsModels(both(both(left, middle), right));
        })));
    }
}
