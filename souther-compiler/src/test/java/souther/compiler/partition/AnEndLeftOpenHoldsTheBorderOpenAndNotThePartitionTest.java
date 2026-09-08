package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.Clause;
import souther.compiler.check.RuleRef;
import souther.compiler.inputs.TermPath;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * An end a choice left open leaves the border measure short and leaves the partition where it found
 * it.
 *
 * <p>The one thing a reading of the model can be short of that is the border's alone. Everything
 * else {@link MeasureClosure} reads is a rule or a position nothing got to, which leaves both
 * measures open; here every alternative's values were read and what nothing worked out is where they
 * stop. Held against the partition as well, a behavior whose classes were read in full would come
 * back divided into something less than the rules leave it, on the strength of a line.
 */
class AnEndLeftOpenHoldsTheBorderOpenAndNotThePartitionTest {

    private static final RuleRef.Invariant NAMED = new RuleRef.Invariant(new Clause.Ref(
            new Clause.Id(TypeSymbols.declared(new TypeKey("m", "N")), 0), Optional.empty()));

    @Test
    void theBorderIsShortOfItAndThePartitionIsNot() {
        MeasureClosure.Both closed = MeasureClosure.of(
                List.of(new PositionAccount("f", TermPath.of("v").then("n"), Type.INT,
                        ReadingResidue.NOTHING, souther.compiler.values.ValueSet.ANY,
                        null, List.of(), List.of(NAMED))),
                List.of(), new LinesRead());

        assertInstanceOf(MeasureClosure.OfTheBorder.Open.class, closed.border(),
                "the line at the position rests on an alternative nothing read, so the reading"
                        + " that draws lines did not run out");
        assertInstanceOf(MeasureClosure.OfThePartition.Closed.class, closed.partition(),
                "and what each alternative admits was read, which is what the classes are made"
                        + " of");
    }
}
