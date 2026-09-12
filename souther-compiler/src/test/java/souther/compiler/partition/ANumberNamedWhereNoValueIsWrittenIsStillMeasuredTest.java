package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.ast.Hir;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.Carrier;
import souther.compiler.check.Sig;
import souther.compiler.check.Prepared;
import souther.compiler.inputs.InputDomain;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.Quantities;
import souther.compiler.inputs.TermPath;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;
import souther.compiler.query.Shapes;
import souther.compiler.types.Type;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A number named where the reading has no position is measured on the order the declarations put
 * there.
 *
 * <p>Both cases of {@code Req} spread {@code Base}, so {@code r.deadline} is a name a value of the
 * sum is read at and is not a place a value is composed for: the reading enumerates positions under
 * each case and has none at the sum's own name, and the walk that follows a written value stops at
 * the sum because what is built there is one of the cases.
 *
 * <p>Which is the case that tells the two questions apart. What a number named there is counted on
 * follows from what the declarations put at the name, and every case spreads the same declaration —
 * so the order is there to be had whether or not anything is written at that path. Worked out from
 * the positions alone, or from the traversal that follows a written value, the answer is that
 * nothing orders it, and a rule about that number would come to no line.
 */
class ANumberNamedWhereNoValueIsWrittenIsStillMeasuredTest {

    private static final String SPREAD = """
            module example.spread

            data Base = { deadline: Int }
            data P = { ...Base, x: Int }
            data T = { ...Base, y: Int }
            data Req = P | T

            data Ok
            data No

            behavior check : (r: Req) -> Ok | No

            let check (r) = {
                guard r.deadline > 10 else No
                Ok
            }
            """;

    /** The name every case spreads, which is where a value of the sum is read and not written. */
    private static final TermPath DEADLINE = TermPath.of("r").then("deadline");

    /**
     * The reading has no position at the shared name, which is what makes this the case worth
     * asking about.
     */
    @Test
    void theReadingHasNoPositionAtTheSharedName() {
        Read read = of();

        assertNull(read.domain().at(DEADLINE),
                "the reading goes under each case, so the sum's own name holds no position");
        assertNotNull(read.domain().at(TermPath.of("r")),
                "and the parameter it is a name of is a position like any other");
    }

    /** And the order is answered all the same. */
    @Test
    void theOrderIsAnsweredWhereThereIsNoPosition() {
        Read read = of();

        assertEquals(Carrier.ofValue(Type.INT, read.rules().symbols(), read.rules().kinds(),
                        read.rules().published()),
                read.quantities().ordersOf(new NumericTerm.ValueOf(DEADLINE)).answered(),
                "the declarations put a whole number at the name every case spreads");
    }

    /**
     * The traversal that follows a written value has nothing there, and that is the right answer to
     * its own question.
     *
     * <p>Beside the one above rather than instead of it. A row writes one of the cases at
     * {@code r}, so there is no value to compose at the sum's own name — which is what this walk
     * says, and is not what the number is measured on.
     */
    @Test
    void theWrittenValueTraversalHasNothingAtTheSharedName() {
        Read read = of();

        assertNull(read.written().typeAtWrittenPath(DEADLINE),
                "a value is written at one of the cases, not at the name they share");
        assertEquals(Type.INT, read.written().typeAtWrittenPath(underACase(read)),
                "and under the case it is written, where the same field stands");
    }

    /** The same field under one of the cases, taken from the reading rather than spelled here: what
     *  a row writes at {@code r} is a value of a case, and that is where the field is composed. */
    private static TermPath underACase(Read read) {
        return read.domain().positions().stream().map(souther.compiler.inputs.Position::path)
                .filter(each -> each.steps().size() == 2
                        && each.steps().get(0) instanceof TermPath.Step.Refine
                        && each.steps().get(1) instanceof TermPath.Step.Field field
                        && field.name().equals("deadline"))
                .findFirst().orElseThrow();
    }

    /** What the model under test comes to: the reading of the input, what it answers about numbers,
     *  and the traversal a value is written by. */
    private record Read(InputDomain domain, Quantities quantities, BehaviorInputs written,
                        RuleReadingSource rules) {}

    private static Read of() {
        Compilation compilation = Compilation.ofSource(SPREAD, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Prepared prepared = compilation.db().ask(new Shapes.Prepared(module)).value();
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        Map<String, Sig> sigs = compilation.db().ask(new Bodies.Signatures(module)).value();
        Hir.SpecBehavior spec = (Hir.SpecBehavior) prepared.behaviors().stream()
                .filter(each -> each.name().equals("check")).findFirst().orElseThrow();
        InputDomain domain = compilation.db()
                .ask(new souther.compiler.query.Adequacy.Inputs(module)).value().get("check");
        assertNotNull(domain, "the model under test compiles and its input is read");
        return new Read(domain, domain.quantities(rules),
                new BehaviorInputs(spec.params().stream().map(Hir.Param::name).toList(),
                        sigs.get("check").inputTypes(), rules, ReadAs.THE_COMPILATION_DOES),
                rules);
    }
}
