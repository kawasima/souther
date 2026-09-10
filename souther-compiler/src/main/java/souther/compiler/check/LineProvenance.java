package souther.compiler.check;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * What a reading knows about which of a declaration's rules put an end where it is.
 *
 * <p>Two answers and not one, because two readings answer this and they know different things. A
 * comparison places an end of its own, and what placed it is the statement that was read. An end no
 * comparison placed is attributed by asking what the rules leave without a conjunct, and what that
 * establishes is about the conjunct: taking a part away takes away every statement it made, so an
 * answer that named one of them would be a claim the counterfactual never tested.
 *
 * <p>Held apart in the type rather than flattened to whichever of the two every reader could take.
 * Flattened upwards, the direct reading's answer is thrown away and two ends of one conjunct come
 * back as one thing; flattened downwards, the counterfactual is made to name a statement, and a
 * reader that met the two would have no way of telling a tested claim from an invented one.
 */
public sealed interface LineProvenance {

    /** The conjunct behind the end, which both answers have and which is what a rule is named by. */
    PartId<RuleRef.Invariant> part();

    /** Every statement this answer is about, which is one where a comparison placed the end. */
    Set<InvariantStatementId> statements();

    /**
     * The end a statement of the clause placed, read off that statement.
     *
     * @param statement which statement of which conjunct placed it
     */
    record Direct(InvariantStatementId statement) implements LineProvenance {

        public Direct {
            if (statement == null) {
                throw new IllegalArgumentException("an end a statement placed is some statement's");
            }
        }

        @Override
        public PartId<RuleRef.Invariant> part() {
            return statement.part();
        }

        @Override
        public Set<InvariantStatementId> statements() {
            return Set.of(statement);
        }
    }

    /**
     * The end a conjunct accounts for without stating it, which is what taking the conjunct away
     * moves.
     *
     * <p>Which conjunct was taken away is read off them and not held beside them. The statements of
     * one conjunct are that conjunct's, so a field for it would be a second way to the rule this is
     * about — and a value with two of those can be built about two rules, with whoever writes from
     * it filing an entry under one and describing the other.
     *
     * @param pairedWith the statements of the conjunct which are about the number this end is on.
     *                   These are what tells two ends of one conjunct apart, and none of them is
     *                   being said to have placed the end: the intervention was the conjunct's, and
     *                   what it establishes is the conjunct's too
     */
    record Counterfactual(Set<InvariantStatementId> pairedWith) implements LineProvenance {

        public Counterfactual {
            pairedWith = Set.copyOf(pairedWith);
            if (pairedWith.isEmpty()) {
                throw new IllegalArgumentException(
                        "an end a conjunct accounts for is about something the conjunct states");
            }
            if (pairedWith.stream().map(InvariantStatementId::part).distinct().count() != 1) {
                throw new IllegalArgumentException(
                        "a conjunct accounts for an end with statements of its own: " + pairedWith);
            }
        }

        @Override
        public PartId<RuleRef.Invariant> part() {
            return pairedWith.iterator().next().part();
        }

        @Override
        public Set<InvariantStatementId> statements() {
            return pairedWith;
        }
    }

    /**
     * The line this end is, which is where the evidence is spent.
     *
     * <p>One statement's where the evidence is about one of them, whichever reading established it:
     * a comparison that placed the end names its own statement, and a counterfactual that took a
     * conjunct away naming one statement about this number is that same line found a second way.
     * Folded here rather than at each reader, the two came out as two lines and every report counted
     * one line twice.
     *
     * <p>The conjunct's where the evidence is about several. Taking a part away takes away
     * everything it stated, so an end a conjunct accounts for through two of its statements is a
     * line neither of them drew — which is what a choice's line is, and what it is called
     * ({@link DeclaredLine.OfAChoice}).
     */
    default DeclaredLine line() {
        Set<InvariantStatementId> said = statements();
        return said.size() == 1
                ? new DeclaredLine.OfAStatement(said.iterator().next())
                : new DeclaredLine.OfAChoice(said);
    }

    /** Every statement of every one of them, which is what a reader asking whether a set of ends
     *  covers a candidate's statements wants. */
    static Set<InvariantStatementId> statementsOf(Iterable<? extends LineProvenance> from) {
        Set<InvariantStatementId> out = new LinkedHashSet<>();
        from.forEach(each -> out.addAll(each.statements()));
        return out;
    }
}
