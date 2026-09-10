package souther.compiler.query;

import org.junit.jupiter.api.Test;

import souther.compiler.ast.Hir;
import souther.compiler.check.ResolvedFieldTypes;
import souther.compiler.check.Sig;
import souther.compiler.check.Symbols;
import souther.compiler.partition.Generator;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Which values a generation may write a row against, and how the {@code let} that states one was
 * spelt.
 *
 * <p>A value of a parameter's type that the module states is an origin: a row written against it
 * reads as that value with one class moved, which is what makes it readable at all. What states one
 * is the declaration, and {@code let vip = Customer { ... }} and {@code let vip = makeCustomer(...)}
 * are the same value of the same type declared two ways.
 *
 * <p>Read by a walk with no step for applying anything, the second stated nothing — so which rows a
 * person was offered turned on how the value beside them happened to be written. That is what this
 * holds: the same module with the same values, spelt both ways, offers the same origins.
 */
class AValueTheModuleStatesIsOneHoweverItsLetWasWrittenTest {

    private static final String CONSTRUCTED = model("""
            let vip = Customer { grade = Gold }
            """);

    private static final String CALLED = model("""
            let of (g: Grade) = Customer { grade = g }

            let vip = of(Gold)
            """);

    private static String model(String values) {
        return """
                module example.member

                data Bronze
                data Gold
                data Grade = Bronze | Gold

                data Customer = { grade: Grade }

                data Accepted = { at: String }

                behavior admit : (customer: Customer) -> Accepted
                    constructs Accepted

                let admit (customer) = Accepted { at = "now" }

                """ + values;
    }

    /** A value written as a construction is an origin of the position its type stands at. */
    @Test
    void aValueWrittenAsAConstructionIsAnOrigin() {
        assertEquals(List.of(originAt("customer", "vip")), originsOf(CONSTRUCTED));
    }

    /** And so is the same value written as a call of something that constructs one. */
    @Test
    void andSoIsOneWrittenAsACallOfSomethingThatConstructsOne() {
        assertEquals(List.of(originAt("customer", "vip")), originsOf(CALLED));
    }

    private static Generator.Baseline originAt(String parameter, String value) {
        return new Generator.Baseline(Map.of(parameter,
                new Generator.Baseline.Named("example.member", value)));
    }

    /** The origins the module states for the behavior under test, one per value of a parameter's
     *  own type. */
    private static List<Generator.Baseline> originsOf(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Db db = compilation.db();
        Symbols symbols = Scopes.derived(db, module).value();
        Sig sig = db.ask(new Bodies.Signatures(module)).value().get("admit");
        assertNotNull(sig, "the behavior under test has a signature");

        return Adequacy.Generated.named(module, specOf(db, module), sig,
                db.ask(new Bodies.ModuleDefinitions(module)).value(),
                db.ask(new Bodies.Reachable(module)).value(),
                symbols, new ResolvedFieldTypes(symbols));
    }

    private static Hir.SpecBehavior specOf(Db db, String module) {
        for (Hir.BehaviorDef behavior : db.ask(new Names.Resolved(module)).value().behaviors()) {
            if (behavior instanceof Hir.SpecBehavior spec && spec.written().canonical()
                    .equals("admit")) {
                return spec;
            }
        }
        throw new AssertionError("the module declares `admit`");
    }
}
