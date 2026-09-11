package souther.compiler.partition;

import souther.compiler.coverage.CoverageSites;

import java.util.Objects;

/**
 * What tells one thing a row is owed for from every other, over the derivations that state such
 * things.
 *
 * <p>Closed, so that a surface writing one writes every shape there is: a derivation added arrives
 * at each of them as a case to decide about rather than as a value that falls through. What the
 * shapes have in common is what they are for and not what they hold — a line's point is a point of
 * an authored line at a level, an arm's is the fork and which of its ways, a class's is the axis and
 * which class of it, a decision rule's is what the path consulted — so there is nothing here to lift
 * out of them.
 *
 * <p><b>Not a handle a search steers by.</b> What a proposal targets is one of these; how a search
 * reaches it is the search's own — {@link Generator.ArmOwed} names an occurrence a run is recorded
 * at, and what a requirement search came back standing at is values. Held as one value, a proposal
 * merged with another because their stimuli agreed would lose which obligations it was for, and the
 * account would be keyed on whatever the search happened to name.
 *
 * <p>Below the account rather than in it. The generator, the readings and the account all join on
 * this, and the two of them that cannot see the account would each have had a vocabulary of their
 * own — which is the parallel bookkeeping this is here to have none of.
 */
public sealed interface ObligationIdentity {

    /** A point of a line, which is what the border accounts are owed at. */
    record OfALine(BorderObligationPoint point) implements ObligationIdentity {

        public OfALine {
            Objects.requireNonNull(point, "an obligation is told apart by something");
        }
    }

    /**
     * An arm of a body, which is what the arm account is owed at.
     *
     * <p>The obligation and not an occurrence of it. A non-recursive helper is spliced into each
     * body that calls it, so one arm the author wrote stands in the running tree several times;
     * covering it through a second call site establishes nothing the first did not.
     */
    record OfAnArm(CoverageSites.Obligation arm) implements ObligationIdentity {

        public OfAnArm {
            Objects.requireNonNull(arm, "an obligation is told apart by something");
        }
    }

    /** A class of a position, which is what the domain account is owed at. */
    record OfAClass(ClassOfAPosition classOfAPosition) implements ObligationIdentity {

        public OfAClass {
            Objects.requireNonNull(classOfAPosition, "an obligation is told apart by something");
        }
    }

    /**
     * A rule of the decision a body states, which is what the decision account is owed at.
     *
     * <p>The behavior beside the rule. A rule is told apart by the distinctions it consulted, and
     * those are written in the terms of a body's own positions — so two behaviors comparing
     * same-named positions the same way state equal rules, and an identity that left the behavior
     * out would have one of them discharged by the other's row.
     */
    record OfADecisionRule(String behavior, DecisionRule rule) implements ObligationIdentity {

        public OfADecisionRule {
            Objects.requireNonNull(behavior, "a rule of a decision is some body's");
            Objects.requireNonNull(rule, "an obligation is told apart by something");
        }
    }
}
