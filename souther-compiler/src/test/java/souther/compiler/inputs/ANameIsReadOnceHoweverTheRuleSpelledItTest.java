package souther.compiler.inputs;

import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredSig;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.NumberAt;
import souther.compiler.check.Owed;
import souther.compiler.check.RuleKey;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;
import souther.compiler.types.TypeSymbol;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Where a rule wrote a name is one question, and it is asked once.
 *
 * <p>A rule reaches a location in four spellings — the term an axis is measured at, the term a
 * comparison drew, the coordinate a clause placed an end on, the position a clause admits values at
 * — and what differs between them is what the rule says, never where it says it. So the address
 * comes out the same from all four, and what is left over keeps each of them apart.
 */
class ANameIsReadOnceHoweverTheRuleSpelledItTest {

    /** Two records of one field, one of which is bounded on a number taken of that field. */
    private static final String TWO_WAYS = """
            module g

            data Measured = { name: String }
                invariant long = String.length(name) >= 3
            data Plain = { name: String }

            data Ok

            behavior byLength : (h: Measured) -> Ok
            behavior byValue : (h: Plain) -> Ok
            """;

    private static final String SHARED = """
            module g

            data Paging = { limit: Int }
            data A = { ...Paging, x: Int }
            data B = { ...Paging, y: Int }
            data Q = A | B

            data Ok

            behavior read : (q: Q) -> Ok
            """;

    /**
     * The number a rule is about does not move where it was written.
     *
     * <p>{@code String.length(h.name)} and {@code h.name} are two numbers of one location. A reader
     * that took the address off the number would have two names for that location, and a rule about
     * one of them would be filed where the other was.
     */
    @Test
    void aNumberTakenOfALocationIsWrittenWhereTheLocationIs() {
        List<NumericTerm.FromOnePosition> both = numbersAt(TWO_WAYS, "byLength", "h", "name");
        NumericTerm taken = only(both, NumericTerm.TakenOf.class);
        NumericTerm own = only(both, NumericTerm.ValueOf.class);
        assertNotNull(taken, "a string is counted by its length");
        assertNotNull(own, "and holds its own values besides");

        TermPath root = TermPath.of("h");
        souther.compiler.check.RuleRef.Invariant rule = someRule(measuredIn(TWO_WAYS));
        assertEquals(seedOf(root, own, rule).address(), seedOf(root, taken, rule).address());
        assertNotEquals(seedOf(root, own, rule).placed(), seedOf(root, taken, rule).placed(),
                "and what each says about the location is what tells them apart");
    }

    /**
     * A question about the values at a location and a line on a number of it are written at the one
     * address.
     *
     * <p>Two questions and one place. What is left of each once the address is out of it is what a
     * reader has to keep apart, and it is the whole of what a reader has to keep apart.
     */
    @Test
    void twoQuestionsAboutOneLocationAreAtOneAddress() {
        TermPath root = TermPath.of("h");
        souther.compiler.check.RuleRef.Invariant rule = someRule(measuredIn(TWO_WAYS));
        PlacementSeed values = PlacementSeed.of(root,
                new Owed.AdmittedValues(RuleKey.of("name")), someCitation(rule));
        PlacementSeed line = PlacementSeed.of(root,
                new Owed.Boundary(NumberAt.valueOf(RuleKey.of("name"))), someCitation(rule));

        assertEquals(values.address(), line.address());
        assertEquals(new PlacementSeed.Placed.TheValuesThere(), values.placed());
        assertEquals(new PlacementSeed.Placed.ANumberOfIt(
                        new NumberAt.OfWhatNumber.OfItsOwnValue()), line.placed());
    }

    /**
     * One name under two values is two names.
     *
     * <p>A clause is not written across a narrowing, so what a {@code Q} calls {@code limit} and
     * what an {@code A} calls {@code limit} are written in different declarations. Held as the name
     * alone, a rule of one case would answer for the same field of every other.
     */
    @Test
    void oneKeyUnderTwoValuesIsTwoAddresses() {
        InputDomain read = reading(SHARED, "read");
        TermPath sum = TermPath.of("q");
        TermPath aCase = sum.refine(narrowingTo(read, sum, "A"));

        assertNotEquals(new RuleAddress(sum, RuleKey.of("limit")),
                new RuleAddress(aCase, RuleKey.of("limit")));
    }

    /**
     * A path no rule of the value can name places nothing, and is not a placement that reached
     * nowhere.
     *
     * <p>The reading of one value has nothing to say about a position in another, and that is what
     * this answers. Turned into a placement here, every clause that keeps to its own value would be
     * a rule somebody failed to file.
     */
    @Test
    void aPathUnderAnotherValuePlacesNothing() {
        InputDomain read = reading(SHARED, "read");
        TermPath sum = TermPath.of("q");
        TermPath a = sum.refine(narrowingTo(read, sum, "A"));
        TermPath b = sum.refine(narrowingTo(read, sum, "B"));

        assertNull(RuleAddress.of(a, b.then("limit")),
                "no rule of `A` names a position in `B`");
        assertNull(RuleAddress.of(sum, a.then("limit")),
                "and the sum's own rules do not reach into a case either: what relates a name at "
                        + "the sum to that position is the crossing, and an address that stepped "
                        + "through the narrowing would be a second way to say it");
        souther.compiler.check.RuleRef.Invariant rule = someRule(caseNamedAtModule(read, sum, "A"));
        assertNull(RuleAddress.of(a, b.then("limit")),
                "so there is no address there, which is not the same as a placement with nowhere "
                        + "to go");
        assertNotNull(seedOf(a, new NumericTerm.ValueOf(a.then("limit")), rule),
                "and the same rule about its own value does place something");
    }

    /** The numbers the reading gives one position. */
    private static List<NumericTerm.FromOnePosition> numbersAt(String source, String behavior,
                                                               String parameter, String field) {
        Position at = reading(source, behavior).at(TermPath.of(parameter).then(field));
        assertNotNull(at, "the field is a position of the input");
        return at.numbers();
    }

    /** The one of {@code numbers} of that kind, or null where there is none. */
    private static NumericTerm only(List<NumericTerm.FromOnePosition> numbers,
                                    Class<? extends NumericTerm> kind) {
        return numbers.stream().filter(kind::isInstance).findFirst().orElse(null);
    }

    /** The case's own name, taken off the reading that holds it. */
    private static TypeSymbol caseNamed(InputDomain read, TermPath sum, String name) {
        return distinctionAt(read, sum, name).leaf();
    }

    /** The narrowing to that case, spelled by the distinction the reading holds rather than by the
     *  case's name — which is how every reader of this path spells it. */
    private static Refinement narrowingTo(InputDomain read, TermPath sum, String name) {
        return Refinement.of(distinctionAt(read, sum, name));
    }

    private static Case.SumCase distinctionAt(InputDomain read, TermPath sum, String name) {
        for (Case each : read.at(sum).obligationCases()) {
            if (each instanceof Case.SumCase one && one.leaf().name().equals(name)) {
                return one;
            }
        }
        throw new IllegalStateException("no case named " + name);
    }

    /** A declaration of the model to hang a made-up rule on. */
    private static TypeSymbol.AtModule measuredIn(String source) {
        souther.compiler.types.Type type =
                reading(source, "byLength").at(TermPath.of("h")).view().declared();
        if (type instanceof souther.compiler.types.Type.Ref ref
                && ref.name() instanceof TypeSymbol.AtModule at) {
            return at;
        }
        throw new IllegalStateException("the parameter is a declaration of the module");
    }

    /** The case's own name, where a declaration is what is wanted. */
    private static TypeSymbol.AtModule caseNamedAtModule(InputDomain read, TermPath sum,
                                                         String name) {
        return (TypeSymbol.AtModule) caseNamed(read, sum, name);
    }

    /** A rule to hang a placement on, so that a seed made here is one some rule placed. */
    private static souther.compiler.check.RuleRef.Invariant someRule(
            souther.compiler.types.TypeSymbol.AtModule on) {
        return new souther.compiler.check.RuleRef.Invariant(
                new souther.compiler.check.Clause.Ref(
                        new souther.compiler.check.Clause.Id(on, 0),
                        java.util.Optional.of(new souther.compiler.check.ClauseName("here"))));
    }

    /** The seed for a term of the value at {@code root}, at the address that value's rules write
     *  it at. */
    private static PlacementSeed seedOf(TermPath root, NumericTerm term,
                                        souther.compiler.check.RuleRef.Invariant rule) {
        return PlacementSeed.of(RuleAddress.of(root, term.subjectPath()), term,
                someCitation(rule));
    }

    /** How a report would send a reader to it. */
    private static souther.compiler.check.RuleCitation someCitation(
            souther.compiler.check.RuleRef.Invariant rule) {
        return new souther.compiler.check.RuleCitation.Named(rule);
    }

    private static InputDomain reading(String source, String behavior) {
        Compilation compilation =
                Compilation.ofSources(List.of(source), souther.compiler.meta.ModulePath.EMPTY);
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        return InputDomain.of(sigs.get(behavior), rules, ReadAs.THE_COMPILATION_DOES);
    }
}
