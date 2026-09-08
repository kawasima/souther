package souther.bench;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * What a position was left holding is an observation, and no account of a rule is built out of it.
 *
 * <p>An allowance is held per position and every rule reaching one pays into it. So a machine
 * refused while a position's answer was worked out is a fact about the pattern that asked, recorded
 * at the place the spending was arranged by — and a place has as many claimants as it has rules.
 * Projected onto the position and read back, the fact became one about every rule that named the
 * place: an author was sent to a clause that reads perfectly well, and the reason had no place among
 * the parts they wrote for anything downstream to put it back into.
 *
 * <p><b>The projection is not the defect and is not being removed.</b> A position is as wide as it
 * is because a machine was refused, and a reader of the place is owed that. What is held here is the
 * direction: down to a place, never back up to a rule.
 *
 * <p>Read off the call sites, because that is what the rule is about. A shortfall does carry what
 * it is about — the pattern whose machine it is and the position it was being built for — but
 * nothing in a type stops somebody reading the position's face and filing what they find under a
 * rule, which is what was being done.
 */
class APositionsStandingIsNotEvidenceOfWhoseRuleItIsTest {

    private static final String SIDED = "souther.compiler.check.Settlement$Sided";

    /**
     * The one projection down to a place, asked for only where a place is being described.
     *
     * <p>The caller hands it to {@code PlannedValues.alsoStanding}, which is what a reading holds
     * for a position to answer with. A second caller is a reader of a place's reasons somewhere
     * new, and the question it has to answer first is which of the two directions it is going in.
     *
     * <p>One and not two, because the account of a rule holds no description of a position to put
     * one into. What a rule's clauses took in is answered over the tree its author wrote, which
     * carries what each part adopted and nothing about what a position came to; the place is
     * described where the values are worked out, and that is the one caller here.
     */
    @Test
    void aPositionsFaceIsAskedForWhereAPositionIsDescribedAndNowhereElse() throws Exception {
        List<String> asked = new ArrayList<>();
        for (Compiled.Site site : Compiled.sites()) {
            if (site.owner().equals(SIDED) && site.member().equals("asPositionStanding")
                    && !site.from().equals(SIDED)) {
                asked.add(site.from() + "." + named(site.method()));
            }
        }

        assertEquals(List.of("souther.compiler.check.StatedByClauses$Reading.keptTogether"),
                asked.stream().sorted().toList(),
                "a place's own reasons are read somewhere new, or twice where they are read once,"
                        + " and what has to be answered there is whether an account of a rule is"
                        + " being built out of them");
    }

    /**
     * And what a pattern asked for goes to whoever asked, which is what routing it needs.
     *
     * <p>The other half of the same rule, held so that the check above cannot be satisfied by
     * nobody reading either. A shortfall that names the written thing is what an account of a rule
     * is made from, and it is read where the parts of a rule are.
     */
    @Test
    void whatAskedForAMachineIsReadWhereTheAccountOfARuleIsMade() throws Exception {
        List<String> read = new ArrayList<>();
        boolean reached = false;
        for (Compiled.Site site : Compiled.sites()) {
            if (site.owner().equals(SIDED) && site.member().equals("ruleShortfalls")) {
                reached = true;
                if (!site.from().equals(SIDED)) {
                    read.add(site.from() + "." + named(site.method()));
                }
            }
        }

        assertFalse(!reached,
                "nothing reads what asked for a machine, so the check above is passing because"
                        + " neither half is read rather than because the halves are apart");
        assertEquals(Set.of("souther.compiler.check.StatedByClauses$Taken.holding"),
                Set.copyOf(read),
                "what asked is routed to the part that asked, and read nowhere else");
    }

    /**
     * The method a site is in, with a lambda said as the method that holds it.
     *
     * <p>A lambda is where the code was written and is not a place of its own: what this rule is
     * about is which piece of work reads a half, and moving a loop body into a lambda is not a
     * change to that.
     */
    private static String named(String method) {
        if (!method.startsWith("lambda$")) {
            return method;
        }
        String rest = method.substring("lambda$".length());
        return rest.substring(0, rest.lastIndexOf('$'));
    }
}
