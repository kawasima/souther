package souther.compiler.check;

import souther.compiler.values.AdmissibleValues;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * What the clauses of one value say, with every choice in them decided.
 *
 * <p>The other side of {@link StatedByClauses}, which is the same four things while one question
 * about them is still open: whether each branch of a choice is one anybody can take. That question
 * is answered by working the values out, and until it is answered none of the four has its final
 * form — what the positions admit, where they stop, and what each language is recorded as having
 * taken in are all different for a branch that survives and one that does not.
 *
 * <p><b>So the queries that need the answer live here and nowhere else.</b> A caller holding one of
 * these cannot ask about a branch that has not been decided, because there is no such thing to hold.
 * Held on the planned side as well, a reader could ask what a clause adopted before the branch it
 * was in was known to be dead, and the account would name a rule of a branch nothing satisfies —
 * sending an author to look at something that is not there.
 *
 * <p>Working the values out is also what settles which positions this compiler could not build, and
 * that is a second thing the resolved side owes: the account of what a clause adopted is written
 * before any machine is made, so a position whose answer was not built is one it still calls taken
 * in. What arrives here has been given up on at those positions.
 *
 * <p><b>What a rule is answerable for is not read off that.</b> A position says what it was left
 * holding and says it for every reason there is, so filtering its reasons for the ones a rule could
 * be answerable for gives a list of reasons and no rule — every rule that named the place, taken
 * for the one that asked. Which rule asked is carried from where the asking is
 * ({@code OfAPart.aboutARule}, filled by routing a shortfall to the part that asked for the machine
 * it names), and there is nothing here that turns a place's reasons back into an account of a rule.
 */
record ReadByClauses(Confinement.Worked<FactSubject> confinement,
                     Adoption<FactSubject, ReadingLanguage.Values> byValues,
                     Adoption<FactSubject, ReadingLanguage.Order> byOrder,
                     java.util.Map<souther.compiler.core.Core, OfAPart> parts) {

    /** What every position of this reading may hold. */
    AdmissibleValues<FactSubject> values() {
        return confinement.values();
    }

    /**
     * What one rule came to on its own tree, once every branch of the value is decided.
     *
     * <p>Its own tree is the whole of it. What a neighbouring rule of the same declaration narrowed
     * is no part of what this rule did, so a reader asking what this rule left has to be given an
     * answer met from this rule's clauses alone — read off the whole, {@code invariant value == 7}
     * beside a rule that says nothing would lend the second its narrowing, and a reason filed
     * against the second would name a rule that holds the position to nothing.
     *
     * @param narrowed the positions this rule leaves holding less than every value, and whose
     *                 answer was built. Free of any allowance: what each position holds is a
     *                 description the reading already has, and being narrowed is that description
     *                 being one shape rather than another ({@code PlannedValues#adoptedAt}). A
     *                 position the rule leaves holding nothing is not one of these — the rules
     *                 leaving no value is a fact about emptiness and is said where emptiness is
     * @param account  what the rule's own clause took in, which is the part keyed by its root
     */
    record OfARule(java.util.Set<FactSubject> narrowed, OfAPart account) {

        public OfARule {
            narrowed = java.util.Set.copyOf(narrowed);
        }

        /** Whether this rule leaves {@code position} holding less than every value. */
        boolean narrows(FactSubject position) {
            return narrowed.contains(position);
        }
    }

    /**
     * What one part of one clause came to, once every branch of the value is decided.
     *
     * <p>Answered over the part's own rule's tree, with the branches decided by the fates the
     * settlement handed back ({@code Settlement}). The branch a part is in is dropped or kept by
     * the rules of every clause together, so a part deciding that for itself would answer about a
     * branch this declaration has already dropped — and would pay for machines the whole answer
     * never needed to find out.
     *
     * @param aboutARule what a rule of this part is answerable for, each saying the written place
     *                   the reading decided it at. Not sifted out of what the positions were left
     *                   holding: a place holds the reasons of every rule that reached it and names
     *                   none of them, so what came back from sifting it was a list of reasons and
     *                   no clause
     * @param aboutStrings which strings this part admits at each position it states a rule about.
     *                     Beside the adoptions and not among them: what a part took a position in
     *                     at is what the readings settled, and this is what the clause states — a
     *                     rule whose written text nothing worked out is still a rule about the
     *                     position it names, and a reader deciding which of the position's numbers
     *                     it is measured at wants exactly that.
     *                     <p>The sets and not the plans that name them. Everything on this side of
     *                     the reading builds nothing, and a reader handed a plan would be making
     *                     the machine for a rule under an allowance of its own — a second answer to
     *                     what the model admits at a position, made by whoever asked second
     * @param endsLeftOpen where this part states an end the reading of ends did not work out, and
     *                     which choice an author is sent to for it ({@link EndsLeftOpen}). Crosses
     *                     unchanged: what a branch is left open by was settled while the branches
     *                     were, and nothing decided since bears on it
     */
    record OfAPart(Adoption<FactSubject, ReadingLanguage.Values> byValues,
                   Adoption<FactSubject, ReadingLanguage.Order> byOrder,
                   Set<RuleShortfall> aboutARule,
                   java.util.Map<FactSubject, AdmittedStrings> aboutStrings,
                   EndsLeftOpen endsLeftOpen) {

        /** The positions some reading took the whole of this part in at. */
        java.util.Set<FactSubject> adopted() {
            return ReadByClauses.adopted(byValues, byOrder);
        }

        /**
         * Whether this part put a constraint on which values stand at {@code position}.
         *
         * <p>Narrower than {@link #adopted}, and the difference is the whole of what a reader
         * asking this wants. That one is every position either reading settled what the part does
         * to, which a dead branch settles by imposing nothing and which the ordered reading settles
         * by placing an end. Neither of those is a rule holding the values at the position down,
         * and a reader told they were would say a position is restricted by a clause that leaves it
         * exactly as wide as it was.
         */
        boolean restricts(FactSubject position) {
            return byValues.constrains(position);
        }

        /**
         * Whether this part put one on where those values stop.
         *
         * <p>Asked of the ordered reading, because that is what an end is read by. A part that
         * placed one has a line at the position and is accounted for by whoever draws lines, so it
         * is not a part a reader is owed a second sentence about.
         */
        boolean bounds(FactSubject position) {
            return byOrder.constrains(position);
        }
    }

    /** Both maps, each position saying what either of them said of it. */
    static java.util.Map<FactSubject, java.util.List<souther.compiler.values.UnreadReason>>
            alsoSaying(java.util.Map<FactSubject,
                    java.util.List<souther.compiler.values.UnreadReason>> these,
                    java.util.Map<FactSubject,
                            java.util.List<souther.compiler.values.UnreadReason>> those) {
        if (those.isEmpty()) {
            return these;
        }
        java.util.Map<FactSubject,
                java.util.List<souther.compiler.values.UnreadReason>> out =
                new java.util.LinkedHashMap<>(these);
        those.forEach((position, why) -> out.merge(position, why, ReadByClauses::alsoSaying));
        return out;
    }

    /** Both lists, the second's entries after the first's, each reason once. */
    static java.util.List<souther.compiler.values.UnreadReason> alsoSaying(
            java.util.List<souther.compiler.values.UnreadReason> these,
            java.util.List<souther.compiler.values.UnreadReason> those) {
        java.util.List<souther.compiler.values.UnreadReason> out = new java.util.ArrayList<>(these);
        those.forEach(each -> {
            if (!out.contains(each)) {
                out.add(each);
            }
        });
        return out;
    }

    /**
     * The positions some reading took the whole of this clause in at.
     *
     * <p>Some, and not both: the two languages are short of different things, and a bound one of
     * them has no word for is read whole by the other. What neither took in is what is left
     * standing.
     */
    Set<FactSubject> adopted() {
        return adopted(byValues, byOrder);
    }

    static Set<FactSubject> adopted(Adoption<FactSubject, ReadingLanguage.Values> byValues,
                                    Adoption<FactSubject, ReadingLanguage.Order> byOrder) {
        Set<FactSubject> out = new LinkedHashSet<>();
        // Everything either account is about, and not what it put a constraint on: a position a
        // dead branch settled is one the reading answered for and put no constraint on, which is
        // what `took` is asked rather than told.
        byValues.mentions().forEach(each -> {
            if (byValues.took(each)) {
                out.add(each);
            }
        });
        byOrder.mentions().forEach(each -> {
            if (byOrder.took(each)) {
                out.add(each);
            }
        });
        return out;
    }
}
