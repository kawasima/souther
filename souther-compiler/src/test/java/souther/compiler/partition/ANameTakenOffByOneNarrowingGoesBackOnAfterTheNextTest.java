package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredBounds;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.DeclaredSig;
import souther.compiler.inputs.InputDomain;
import souther.compiler.inputs.TermPath;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;
import souther.compiler.types.TypeSymbol;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * A name one narrowing takes off goes back on after the next one.
 *
 * <p>Every name a position wears is put back on the value chosen there — a row that dropped one
 * carries a value of a type the parameter does not declare, which is what
 * {@code ANameGoesBackOnTheWayItCameOff} holds. A narrowing takes one off, and a narrowing may
 * stand where another left the position (ADR-0114), so the name the first one uncovered is taken
 * off by the second and is owed all the same.
 *
 * <p>The settling read the names once, off the declaration, and went on narrowing — so a case that
 * is a newtype over an optional lost the case's own name at the step that reached what the optional
 * holds. Written on the model rather than on the loop: what a value has to wear is a fact about the
 * declarations, and a test on how far a variable was carried would pass over the same value coming
 * out wrong for another reason.
 */
class ANameTakenOffByOneNarrowingGoesBackOnAfterTheNextTest {

    /** The rules asking a collection to hold one and capping it in no way. */
    private static final ConstructionPlan.HowManyItHolds ONE_AT_LEAST =
            (_, _) -> new DeclaredBounds.CountRange(1, Integer.MAX_VALUE);


    /** A case of a sum that is a newtype over an optional: the name is uncovered by the case and
     *  taken off again by the presence. */
    private static final String A_CASE_THAT_WRAPS_AN_OPTIONAL = """
            module example.wrapping

            data Tag = String
                invariant String.length(value) >= 1

            data MaybeTag = Tag?
            data Rejected
            data Decision = Rejected | MaybeTag
            data Page = { count: Int }

            behavior look : (d: Decision) -> Page
            """;

    /** And the other way round: an optional over a newtype of a sum. */
    private static final String AN_OPTIONAL_OVER_A_WRAPPED_SUM = """
            module example.wrapped

            data Tag = String
                invariant String.length(value) >= 1

            data NoTag
            data Filter = NoTag | Tag
            data FilterN = Filter
            data Query = { tag: FilterN? }
            data Page = { count: Int }

            behavior look : (query: Query) -> Page
            """;

    @Test
    void aCaseThatWrapsAnOptionalKeepsItsOwnName() {
        assertEquals(List.of("MaybeTag"),
                wornAt(A_CASE_THAT_WRAPS_AN_OPTIONAL, "d@MaybeTag", "Some"),
                "a value chosen under the presence is still missing the case's own name");
        assertEquals(List.of("MaybeTag"),
                wornAt(A_CASE_THAT_WRAPS_AN_OPTIONAL, "d@MaybeTag", "None"),
                "and so is the absence the presence settled");
    }

    @Test
    void anOptionalOverAWrappedSumKeepsTheNameThePresenceUncovered() {
        assertEquals(List.of("FilterN"),
                wornAt(AN_OPTIONAL_OVER_A_WRAPPED_SUM, "query.tag@Some", "Tag"),
                "the name the presence uncovered is taken off by the case and owed all the same");
    }

    /** The one narrowing that uncovers no name owes none, so nothing is worn twice. */
    @Test
    void aNarrowingThatUncoversNoNameOwesNone() {
        assertEquals(List.of(),
                wornAt(AN_OPTIONAL_OVER_A_WRAPPED_SUM, "query.tag", "Some"),
                "a `FilterN?` wears no name of its own at the field");
    }

    /** The names a value chosen for {@code classId} at {@code path} is still missing. */
    private static List<String> wornAt(String source, String path, String classId) {
        Read read = read(source);
        Partitions.Partitioning partitioning =
                Partitions.of("look", read.domain(), read.rules(), ReadAs.THE_COMPILATION_DOES);
        Axis axis = partitioning.axes().stream()
                .filter(each -> each.path().toString().equals(path)).findFirst()
                .orElseThrow(() -> new AssertionError("no axis at " + path + ": "
                        + partitioning.axes().stream().map(a -> a.path().toString()).toList()));
        PartitionClass cls = axis.classes().stream()
                .filter(each -> each.id().equals(classId)).findFirst()
                .orElseThrow(() -> new AssertionError("no class " + classId + " at " + path));

        ConstructionPlan plan = assertInstanceOf(ConstructionPlan.Result.Planned.class,
                ConstructionPlan.of(read.sig().inputs().get(0).type(),
                        TermPath.of(read.parameter()), read.rules().inners(),
                        read.rules().symbols(),
                        read.rules().published(), Set.of(),
                        axis.requiring(cls), ONE_AT_LEAST),
                "nothing here asks one position to be two things").plan();

        return names(under(plan.root(), axis.path().refine(cls.selects())));
    }

    /** What the plan built at {@code at}, wherever it stands under the root. */
    private static ConstructionPlan.Node under(ConstructionPlan.Node node, TermPath at) {
        return switch (node) {
            case ConstructionPlan.Slot slot -> slot.at().equals(at) ? slot : null;
            case ConstructionPlan.Exact exact -> exact.at().equals(at) ? exact : null;
            case ConstructionPlan.Held held -> under(held.under(), at);
            case ConstructionPlan.Built built -> built.under().values().stream()
                    .map(each -> under(each, at)).filter(each -> each != null).findFirst()
                    .orElse(null);
        };
    }

    private static List<String> names(ConstructionPlan.Node node) {
        List<TypeSymbol> worn = switch (node) {
            case ConstructionPlan.Slot slot -> slot.worn();
            case ConstructionPlan.Exact exact -> exact.worn();
            case null -> throw new AssertionError("the plan builds nothing at that position");
            default -> throw new AssertionError("not a position a value is put at: " + node);
        };
        return worn.stream().map(TypeSymbol::name).toList();
    }

    private record Read(String parameter, DeclaredSig sig, RuleReadingSource rules,
                        InputDomain domain) {}

    private static Read read(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        DeclaredSig declared = sigs.get("look");
        return new Read(declared.inputs().get(0).name(), declared, rules,
                InputDomain.of(declared, rules, ReadAs.THE_COMPILATION_DOES));
    }
}
