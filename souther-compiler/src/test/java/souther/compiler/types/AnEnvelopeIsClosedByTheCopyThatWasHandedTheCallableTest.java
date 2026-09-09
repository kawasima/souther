package souther.compiler.types;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An operation's envelope is closed by the copy the caller's callable was handed to.
 *
 * <p>Held here against lineages built by hand rather than against models, because what is being
 * asked is a question about the identity and not about any one model: which copy a supplied site
 * names, and what a walk over the copies does with it. A model reaches these shapes through a
 * library the tests do not write, so the shape a model happens to reach is a sample of this and not
 * a statement of it.
 *
 * <p>Three shapes, and what tells them apart is the copy the site names and nothing else — not
 * which operation was expanded, and not where the application of the callable stands.
 */
class AnEnvelopeIsClosedByTheCopyThatWasHandedTheCallableTest {

    private static final WrittenOwner.Body CALLER = new WrittenOwner.Body("model", "pick");

    private static SourceConstructOrigin call(int ordinal) {
        return SourceConstructOrigin.written(CALLER, ordinal, SourceConstruct.CALL);
    }

    private static ValueName.Stdlib.Operation operation(String name) {
        return new ValueName.Stdlib.Operation("List", name);
    }

    /** A copy of {@code operation}'s body, made at a call the author wrote, inside {@code within}. */
    private static ExpansionLineage.Expansion expanded(ExpansionLineage within, String operation,
                                                       int ordinal) {
        return within.copiedInto(operation(operation), new ExpansionSite.Written(call(ordinal)));
    }

    /**
     * A call written in the body {@code copy} is a copy of.
     *
     * <p>Where an application of a supplied block stands: the code applying it is the code being
     * copied into, and that body's own constructs are what tell one application of the block from
     * another.
     */
    private static ExpansionSite.Direct applicationIn(ExpansionLineage.Expansion copy,
                                                      int ordinal) {
        WrittenOwner.Body body = switch (copy.expanded()) {
            case ValueName.Stdlib.Operation op -> new WrittenOwner.Body("souther.list", op.name());
            case ValueName.Helper helper -> new WrittenOwner.Body(helper.module(), helper.name());
            default -> throw new IllegalArgumentException(
                    "a copy of something with no body: " + copy.expanded());
        };
        return new ExpansionSite.Written(
                SourceConstructOrigin.written(body, ordinal, SourceConstruct.CALL));
    }

    /** A copy of the caller's block, made where {@code within}'s body applies what {@code copy} was
     *  handed. */
    private static ExpansionLineage.Expansion applied(ExpansionLineage.Expansion within,
                                                      ExpansionLineage.Expansion copy, int slot) {
        return within.copiedInto(
                new ValueName.Local("keep", new BindingId(new BindingOwner.OfValue("model", "pick"), 0)),
                new ExpansionSite.Supplied(
                        new ExpansionSite.Supplied.Handover(copy.step(), new ParameterSlot(slot)),
                        applicationIn(within, 0)));
    }

    /** Whether the model states a construct standing in {@code lineage}, and in which copy. */
    private static Optional<ExpansionLineage> statedIn(ExpansionLineage lineage) {
        return ModelOccurrence
                .statedAt(new ConstructOccurrence(call(9), lineage))
                .map(ModelOccurrence::lineage);
    }

    /**
     * A callable one operation hands to another leaves both.
     *
     * <p>The caller's block crossed into {@code filter}, so {@code filter}'s envelope is the one it
     * left — and the copies {@code filter} made on the way to handing it on are its own. A reader
     * that closed only the copy applying the block would leave the outer one open, and a comparison
     * the author wrote inside their block would come out as a construct the model does not state.
     */
    @Test
    void aCallableHandedOnLeavesEveryCopyItCrossed() {
        ExpansionLineage.Expansion outer = expanded(ExpansionLineage.ORIGINAL, "filter", 0);
        ExpansionLineage.Expansion inner = expanded(outer, "any", 1);
        assertEquals(Optional.of(ExpansionLineage.ORIGINAL), statedIn(applied(inner, outer, 0)));
    }

    /**
     * And a callable an operation wrote itself leaves only the copy it was handed to.
     *
     * <p>The block is the outer operation's own code, so nothing of the caller's is reached by
     * running it: the inner envelope closes and the outer stays open. Read as "supplied, therefore
     * the caller's", this would come back as a construct of the model standing where the author
     * wrote nothing.
     */
    @Test
    void aCallableAnOperationWroteItselfLeavesOnlyWhereItWasHanded() {
        ExpansionLineage.Expansion outer = expanded(ExpansionLineage.ORIGINAL, "filter", 0);
        ExpansionLineage.Expansion inner = expanded(outer, "any", 1);
        assertEquals(Optional.empty(), statedIn(applied(inner, inner, 0)));
    }

    /**
     * And a copy the model itself makes is left by a crossing like any other.
     *
     * <p>A helper of the model taking a callable and handing it to an operation is two copies
     * between where the callable was written and where it runs, and neither is a copy of it. Left
     * only as far as the operation, what the callable holds would come out standing in a copy of
     * the helper — which is a copy of the helper's own code and not of the caller's.
     */
    @Test
    void aCopyTheModelMakesIsLeftTooWhereTheCallableCrossedOutOfIt() {
        ExpansionLineage.Expansion helper = ExpansionLineage.ORIGINAL.copiedInto(
                new ValueName.Helper("model", "through"), new ExpansionSite.Written(call(0)));
        ExpansionLineage.Expansion operation = expanded(helper, "any", 1);
        assertEquals(Optional.of(ExpansionLineage.ORIGINAL),
                statedIn(applied(operation, helper, 0)));
        // And what the helper's own body writes stands in the helper's copy, which is a copy the
        // model makes: the same walk keeps it exactly where the crossing takes the other away.
        assertEquals(Optional.of(helper), statedIn(helper));
    }

    /**
     * And two copies of one operation are two copies.
     *
     * <p>An operation calling itself has two of its copies open at once, and the caller's callable
     * was handed to one of them. Told apart by which operation was expanded, the site would leave
     * the innermost of the two and the outer would stay open — so the construct the author wrote
     * would be lost at exactly the shape a library writes when it defines an operation over itself.
     */
    @Test
    void twoCopiesOfOneOperationAreToldApart() {
        ExpansionLineage.Expansion first = expanded(ExpansionLineage.ORIGINAL, "any", 0);
        ExpansionLineage.Expansion second = expanded(first, "any", 1);
        assertEquals(Optional.of(ExpansionLineage.ORIGINAL), statedIn(applied(second, first, 0)));
        assertEquals(Optional.empty(), statedIn(applied(second, second, 0)));
    }
}
