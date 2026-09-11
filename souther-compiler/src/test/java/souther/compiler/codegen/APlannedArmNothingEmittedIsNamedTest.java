package souther.compiler.codegen;

import souther.compiler.diag.SourceLayouts;
import org.junit.jupiter.api.Test;

import souther.compiler.check.DerivedSymbols;
import souther.compiler.coverage.CoverageSites;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.Scopes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which of the arms a plan numbered never reached the bytecode.
 *
 * <p>The half of the numbering that nothing at the site can see. An emitter that walks a body
 * without counting its arms does not fail where it walks it — it writes a class that works, one
 * probe short, and every arm it left out is then reported as an arm no row goes through. An author
 * reads that as a gap in their model and goes looking for a row to write against a branch their rows
 * already take. So what was planned and what was emitted are compared once, at the end, and the
 * comparison is what this is about.
 *
 * <p>Asked of the context that holds both sides rather than of a generation. A generation reaches
 * this state only by an emitter forgetting to count, which is not something a caller can ask for —
 * so what a test can hold is that the comparison names exactly the arms nothing wrote.
 */
class APlannedArmNothingEmittedIsNamedTest {

    private static final String MODULE = "example.trip";

    private static final String MODEL = """
            module example.trip

            data Submitted = { cost: Int }
            data Waiting = { cost: Int }

            behavior submit : (cost: Int) -> Submitted | Waiting
                constructs Submitted, Waiting

            let submit (cost) = {
                guard cost <= 100 else Waiting { cost = cost }
                Submitted { cost = cost }
            }
            """;

    private final Compilation compilation = Compilation.ofSources(List.of(MODEL), ModulePath.EMPTY);

    /** The numbering of this module's bodies, which is the one a generation of them would be given. */
    private CoverageSites.Plan planned() {
        Bodies.Elaborated checked = compilation.db().ask(new Bodies.Checked(MODULE)).value();
        assertNotNull(checked, "the model under test compiles");
        CoverageSites.Plan plan = checked.plan();
        assertTrue(!plan.sites().isEmpty(), "the model under test has arms to number");
        return plan;
    }

    /** A context holding {@code plan}, with nothing emitted against it yet. */
    private CodegenContext counting(CoverageSites.Plan plan) {
        DerivedSymbols symbols = Scopes.derived(compilation.db(), MODULE).value();
        CodegenContext ctx = new CodegenContext(MODULE, symbols,
                symbols.library().kernelSignatures(), Map.of(), Map.of(), true, Set.of(), Map.of(),
                SourceLayouts.NONE);
        ctx.setCoveragePlan(plan);
        return ctx;
    }

    private static List<Integer> numbersOf(List<CoverageSites.Site> sites) {
        List<Integer> out = new ArrayList<>();
        for (CoverageSites.Site site : sites) {
            out.add(site.index().raw());
        }
        return out;
    }

    @Test
    void everyPlannedArmIsNamedWhereNothingWasEmitted() {
        CoverageSites.Plan plan = planned();

        assertEquals(numbersOf(plan.sites()), counting(plan).plannedButNotEmitted(),
                "nothing was written, so every arm the plan numbered is one nothing wrote");
    }

    @Test
    void nothingIsNamedWhereEveryPlannedArmWasEmitted() {
        CoverageSites.Plan plan = planned();
        CodegenContext ctx = counting(plan);
        for (CoverageSites.Site site : plan.sites()) {
            ctx.emitted(site.index().raw());
        }

        assertEquals(List.of(), ctx.plannedButNotEmitted(),
                "every arm the plan numbered was written");
    }

    /** And it names the one that is missing rather than answering all-or-nothing, which is what a
     *  body walked without counting leaves behind. */
    @Test
    void theOneArmLeftOutIsTheOneNamed() {
        CoverageSites.Plan plan = planned();
        CodegenContext ctx = counting(plan);
        CoverageSites.Site left = plan.sites().get(0);
        for (CoverageSites.Site site : plan.sites().subList(1, plan.sites().size())) {
            ctx.emitted(site.index().raw());
        }

        assertEquals(List.of(left.index().raw()), ctx.plannedButNotEmitted());
    }
}
