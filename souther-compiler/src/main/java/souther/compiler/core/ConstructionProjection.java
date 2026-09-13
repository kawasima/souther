package souther.compiler.core;

/**
 * What a projection out of a construction reads: the value that field was given.
 *
 * <p>The language's elimination of the construction beside it, written once. A construction writes
 * what it builds a value out of where it stands, so a field taken back out of one is the expression
 * that field was handed — and every reader that has resolved a projection's target to a construction
 * has the same successor to go on with.
 *
 * <p><b>The pair and nothing around it.</b> Which value a name holds, and whether a reader may follow
 * it there, is a question about that reader's environment and each of them answers it in its own
 * vocabulary — a reading of occurrences, a reading of what a binding is of the input. What a
 * construction gave a field is a fact about the node, and no environment changes it. Written out at
 * each reader instead, a shape one of them reduced would be one the others did not, and which
 * projections a rule covers would depend on which reader met it.
 */
public final class ConstructionProjection {

    private ConstructionProjection() {
    }

    /**
     * The expression {@code construct} was given for {@code field}, or null where it was given none.
     *
     * <p>Null is this rule not applying, and never a value standing in for the one that is missing.
     * What a caller does about it is the caller's: a reading with a second proof to try has one, and
     * a reader that has already settled that the field is one of the construction's is looking at
     * this compiler disagreeing with itself.
     */
    public static Core given(Core.Construct construct, String field) {
        for (Core.FieldValue each : construct.values()) {
            if (each.field().equals(field)) {
                return each.value();
            }
        }
        return null;
    }
}
