package souther.compiler.inputs;

import souther.compiler.check.DeclarationNewtypes;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.Symbols;

/**
 * One behavior's input as it was read: where its positions are, what its rules leave the numbers at
 * them, and the names both were read against.
 *
 * <p><b>One value because the three are one reading.</b> Which position a name stands at is the
 * first's to say and what a number there is measured on is the second's, and a reader that holds
 * both uses them together — a term made against one reading and measured on another passes every
 * question either of them asks about itself. Two behaviors can take a parameter spelled the same
 * way, so the term is under a root the second reading has, and what comes back is the order of a
 * position the reader is not asking about. Handed as three arguments, that pairing is the caller's
 * to keep; handed as this, there is nothing to keep.
 *
 * <p>The names are here for the same reason. What a name means is theirs to say, and the quantities
 * were made against one particular answer to that — so the same positions read under another
 * library, or another set of aliases, are a different reading of the same declarations.
 *
 * <p><b>Made by {@link InputDomain#reading} and by nothing else</b>, which is what makes the parts
 * agree. A constructor a caller could reach would be somewhere to put three values back together,
 * and there would be nothing to say they came from one reading — which is the state this exists to
 * remove rather than to check for.
 */
public final class InputReading {

    private final InputDomain domain;
    private final Quantities quantities;
    private final RuleReadingSource rules;

    InputReading(InputDomain domain, Quantities quantities, RuleReadingSource rules) {
        if (domain == null || quantities == null) {
            throw new IllegalArgumentException(
                    "an input is read as its positions and what its rules leave them");
        }
        this.domain = domain;
        this.quantities = quantities;
        this.rules = rules;
    }

    /** Where this input's positions are and what stands at each of them. */
    public InputDomain domain() {
        return domain;
    }

    /** What the rules reaching them leave the numbers there. */
    public Quantities quantities() {
        return quantities;
    }

    /** The scope and the clause representation the two were read against. */
    public RuleReadingSource rules() {
        return rules;
    }

    /** The names the two were read against. */
    public Symbols symbols() {
        return rules.symbols();
    }

    /** Which declarations wear one value, which is what says whether reading a field reaches
     *  somewhere else ({@link souther.compiler.check.Location#isStep}). */
    public DeclarationNewtypes newtypes() {
        return rules.newtypes();
    }
}
