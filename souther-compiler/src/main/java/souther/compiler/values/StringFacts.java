package souther.compiler.values;

import souther.compiler.numeric.OrderedInterval;
import souther.compiler.regex.Language;

import java.util.Map;

/**
 * The machines one reading of a declaration made: what each plan it realized admits, where the
 * strings of each set it walked stop, and whether each language it met has a string inside the
 * stretch it was met with.
 *
 * <p>A value, and one a store keeps under the declaration. Each entry is a fact about a plan, a
 * set or a pair and about nothing else — which reading built it does not enter into what it says —
 * so a second reading of the same declaration, or of another whose rules come to the same strings,
 * is handed the entry rather than building it again. What is not here is not refused: a reading
 * that meets a plan this holds nothing for builds it under its own allowance, as it always did.
 *
 * @param realized what each plan admits, for the plans that came to a set
 * @param extents  where the strings each set holds stop on the order
 * @param inside   whether each language has a string inside the stretch it was asked about
 */
public record StringFacts(Map<AdmittedPlan, ValueSet> realized,
                          Map<ValueSet, TextExtent> extents,
                          Map<Stretch, Emptiness> inside) {

    /** A language beside a stretch of the order whose ends are strings: the question
     *  {@link #inside} answers. */
    public record Stretch(Language language, OrderedInterval held) {}

    /** Nothing made anywhere. */
    public static final StringFacts NONE = new StringFacts(Map.of(), Map.of(), Map.of());

    public StringFacts {
        realized = Map.copyOf(realized);
        extents = Map.copyOf(extents);
        inside = Map.copyOf(inside);
    }
}
