package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredBounds;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.DeclaredSig;
import souther.compiler.inputs.Case;
import souther.compiler.inputs.Refinement;
import souther.compiler.inputs.Requirements;
import souther.compiler.inputs.TermPath;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An optional's classes state which narrowing they are, and what the absence narrows to is a value.
 *
 * <p>A class that narrows a position states the narrowing and does not also fix a value there
 * (ADR-0114). The classes of an optional stated neither: they were built with no narrowing at all,
 * so their values went into the caller's table like any unnarrowed class's. Nothing said the two
 * did not go together — a row could be asked to hold nothing at a position and to hold a value in a
 * class beneath it at the same time, and no reader could see that no value is both.
 */
class AnOptionalsClassesStateWhichNarrowingTheyAreTest {

    /** The rules asking a collection to hold one and capping it in no way. */
    private static final ConstructionPlan.HowManyItHolds ONE_AT_LEAST =
            (_, _) -> new DeclaredBounds.CountRange(1, Integer.MAX_VALUE);


    private static final String FLAGGED = """
            module example.flagged

            data On
            data Off
            data Flag = On | Off

            data Query = { f: Flag?, n: Int }
            data Page = { count: Int }

            behavior look : (query: Query) -> Page
            """;

    /**
     * Holding nothing here and holding a class under {@code Some} is no row.
     *
     * <p>The requirement the second states is written in its own path, and the first is what the
     * class selects — so the two meet in one merge and it says which position could not be both.
     */
    @Test
    void holdingNothingAndHoldingAClassBeneathItAreNoRow() {
        Partitions.Partitioning partitioning = partitioningOf();
        Axis optional = axisAt(partitioning, "query.f");
        Axis beneath = axisAt(partitioning, "query.f@Some");

        Requirements none = optional.requiring(classNamed(optional, "None"));
        Requirements under = beneath.requiring(beneath.classes().get(0));

        assertFalse(none.compatibleWith(under),
                "no value at `query.f` holds nothing and is an `On` as well");
        Requirements.Merge.Conflict against = assertInstanceOf(Requirements.Merge.Conflict.class,
                none.merge(under), "and the answer says which position it is");
        assertEquals(TermPath.of("query").then("f"), against.at());
        assertEquals(Set.of("None", "Some"),
                Set.of(against.one().spelled(), against.other().spelled()));
    }

    /** And holding something here goes with a class beneath it, which is the row the model has. */
    @Test
    void holdingSomethingGoesWithAClassBeneathIt() {
        Partitions.Partitioning partitioning = partitioningOf();
        Axis optional = axisAt(partitioning, "query.f");
        Axis beneath = axisAt(partitioning, "query.f@Some");

        assertTrue(optional.requiring(classNamed(optional, "Some"))
                        .compatibleWith(beneath.requiring(beneath.classes().get(0))),
                "a `Some` holding an `On` is a row the model has");
    }

    /**
     * The absence inside a collection is built there, and the collection is built around it.
     *
     * <p>The narrowing settles the value rather than narrowing to something to be chosen, so the
     * position under it is an {@link ConstructionPlan.Exact} and no slot; what the list holds is
     * still something asked of the list, so the list is composed around it rather than proposed
     * whole. Read off the plan rather than off the paths: whether one position is under another is
     * a fact about the steps between them.
     *
     * <p>Written {@code List<Option<Tag>>}, because a {@code ?} is where an optional is made and no
     * position inside a type is one — {@code List<Tag?>} is refused (E1403).
     */
    @Test
    void anAbsenceInsideACollectionIsBuiltThereAndTheCollectionAroundIt() {
        TermPath element = TermPath.of("query").then("many").element();

        ConstructionPlan plan = planFor(Requirements.NONE.and(element,
                Refinement.of(new Case.Presence(false))));

        ConstructionPlan.Built root =
                assertInstanceOf(ConstructionPlan.Built.class, plan.root(), "a `Held` is composed");
        ConstructionPlan.Held many = assertInstanceOf(ConstructionPlan.Held.class,
                root.under().get("many"),
                "the list is built around what it was asked to hold, and not proposed whole");
        ConstructionPlan.Exact exact = assertInstanceOf(ConstructionPlan.Exact.class, many.under(),
                "and what it holds is the value the narrowing settled");
        assertEquals(element.refine(Refinement.of(new Case.Presence(false))), exact.at());
        assertEquals("None", exact.exact().text());
        assertTrue(plan.slots().stream().noneMatch(each -> each.at().isAtOrUnder(element)),
                "nothing under the element is searched for: " + plan.slots());
    }

    private static final String HOLDING = """
            module example.holding

            data Tag = String

            data Query = { many: List<Option<Tag>> }
            data Page = { count: Int }

            behavior look : (query: Query) -> Page
            """;

    private static ConstructionPlan planFor(Requirements required) {
        Read read = read(HOLDING);
        return assertInstanceOf(ConstructionPlan.Result.Planned.class,
                ConstructionPlan.of(read.sig().inputs().get(0).type(), TermPath.of("query"),
                        read.rules().symbols(), read.rules().published(), Set.of(), required,
                        ONE_AT_LEAST),
                "nothing here asks one position to be two things").plan();
    }

    private static Axis axisAt(Partitions.Partitioning partitioning, String path) {
        return partitioning.axes().stream()
                .filter(each -> each.path().toString().equals(path)).findFirst()
                .orElseThrow(() -> new AssertionError("no axis at " + path + ": "
                        + partitioning.axes().stream().map(a -> a.path().toString()).toList()));
    }

    private static PartitionClass classNamed(Axis axis, String id) {
        return axis.classes().stream().filter(each -> each.id().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError("no class " + id + " at " + axis.path()));
    }

    private static Partitions.Partitioning partitioningOf() {
        Read read = read(FLAGGED);
        return Partitions.of("look",
                souther.compiler.inputs.InputDomain.of(read.sig(), read.rules(),
                        souther.compiler.query.ReadAs.THE_COMPILATION_DOES),
                read.rules(), souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
    }

    private record Read(DeclaredSig sig, RuleReadingSource rules) {}

    private static Read read(String source) {
        Compilation compilation =
                Compilation.ofSources(List.of(source), souther.compiler.meta.ModulePath.EMPTY);
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        return new Read(sigs.get("look"), RuleReadings.of(compilation, module));
    }
}
