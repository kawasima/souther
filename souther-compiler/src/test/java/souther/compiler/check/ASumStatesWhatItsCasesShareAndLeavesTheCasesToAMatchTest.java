package souther.compiler.check;

import org.junit.jupiter.api.Test;
import souther.compiler.ast.Hir;
import souther.compiler.core.Core;
import souther.compiler.diag.SourcePos;
import souther.compiler.query.Compilation;
import souther.compiler.types.BindingId;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A sum is a common product times a choice of case, and the reading of one says both.
 *
 * <p>What every case spreads is stated of every value standing at the sum: a value of the sum was
 * built through one case's checked constructor, and every case carries the shared declaration, so
 * what that declaration writes holds here. What one case writes does not — it refuses values of that
 * case and not every value here — so it stays for the reading a {@code match} opens.
 *
 * <p><b>The two are independent, and this is the test that says so.</b> Each row below fixes one
 * combination of the two, and the pair in {@code aSumCanBothStateARuleAndLeaveOneToItsCases} is the
 * one an answer with a single arm cannot hold: dropping what is read here and dropping what is left
 * to the cases each turn a different assertion red.
 */
class ASumStatesWhatItsCasesShareAndLeavesTheCasesToAMatchTest {

    private static final SourcePos POS = new SourcePos(1, 1);

    private static final String SOURCE = """
            module demo exposing ( Shared, Marked, Both, Quiet, Apart, Alone, keep )

            data NonNegInt = Int
                invariant value >= 0

            data Common = { amount: NonNegInt }
                invariant capped = amount.value <= 100

            data Plain = { ...Common }

            data Stamped = { ...Common, mark: NonNegInt }
                invariant stamped = mark.value >= 1

            data Shared = Plain | Plain

            data Both = Plain | Stamped

            data Quiet = { ...Common }

            data Marked = Quiet | Plain

            data OneWay = { a: NonNegInt }

            data OtherWay = { b: NonNegInt }

            data Apart = OneWay | OtherWay

            data Alone = { ...Common }

            behavior keep : (n: NonNegInt) -> NonNegInt

            let keep (n) = n
            """;

    private final RuleReadingSource rules = rules();

    private final Symbols symbols = rules.symbols();

    private final PathEngine engine = new PathEngine(symbols, rules.invariants(), rules.written(),
            DeclarationReadings.NONE,
            Terms.Of.THE_DISCHARGE_TREE, souther.compiler.query.ReadAs.THE_COMPILATION_DOES);

    private final GuaranteeWalk walk = new GuaranteeWalk(engine.guarantees(), symbols);

    private static RuleReadingSource rules() {
        Compilation compilation = Compilation.ofSource(SOURCE, "Main");
        compilation.answerEverything();
        return RuleReadings.of(compilation, compilation.modules().get(0));
    }

    private Core.Read place(String name) {
        BindingId binding = CoreBinders
                .of(new Hir.Binders(new BindingOwner.OfValue("demo", "keep")).binder("v", POS))
                .binding();
        return new Core.Read("v", binding,
                Type.ref(TypeSymbols.declared(new TypeKey("demo", name))), POS);
    }

    /** What the walk was told, by the position it was told it at. */
    private record Told(Map<String, List<String>> guaranteed, List<String> handedOn) {}

    private Told reading(String name) {
        return reading(name, GuaranteeWalk.Scope.everyName());
    }

    private Told reading(String name, GuaranteeWalk.Scope scope) {
        Core.Read root = place(name);
        Denotations at = Denotations.none().location(root.binding(),
                engine.terms().placeSubject(root.binding()),
                engine.terms().placeTerm(root.binding()));
        Map<String, List<String>> guaranteed = new LinkedHashMap<>();
        List<String> handedOn = new ArrayList<>();
        walk.from(root, RuleKey.THE_VALUE, at, scope,
                new GuaranteeWalk.Reader() {
                    @Override
                    public void guaranteed(RuleKey path, TypeGuarantee guarantee) {
                        guaranteed.computeIfAbsent(path.toString(), _ -> new ArrayList<>())
                                .add(guarantee.rule().clause().toString());
                    }

                    @Override
                    public void handedOn(RuleKey path, Type type) {
                        // The value itself is at no name of its own, which reads as nothing in a
                        // failure message. Named here so a diff says which value it was.
                        handedOn.add(path.isTheValueItself() ? "the value" : path.toString());
                    }
                });
        return new Told(guaranteed, handedOn);
    }

    /** A record states its own rules here and leaves nothing to anybody. */
    @Test
    void aRecordStatesItsRulesHereAndLeavesNothing() {
        Told told = reading("Alone");

        assertTrue(states(told, "capped"), "the spread's rule is a rule about this value");
        assertEquals(List.of(), told.handedOn(),
                "nothing under a record waits for a reading somebody else opens");
    }

    /** A sum whose cases share nothing states nothing here, and every rule under it is a case's. */
    @Test
    void aSumSharingNothingStatesNothingAndLeavesEverything() {
        Told told = reading("Apart");

        assertEquals(Map.of(), told.guaranteed(),
                "a rule written on one case refuses values of that case, not every value here");
        assertEquals(List.of("the value"), told.handedOn(),
                "so the rules under it are for the reading a match opens");
    }

    /** A sum whose cases share a spread and write nothing of their own states the shared rules, and
     *  leaves nothing over: what a case carries is what was read here. */
    @Test
    void aSumWhoseCasesWriteNothingOfTheirOwnLeavesNothingOver() {
        Told told = reading("Marked");

        assertTrue(states(told, "capped"), "every case spreads Common, so Common holds here");
        assertEquals(List.of(), told.handedOn(),
                "the cases carry the shared rule and nothing besides, and it was read here");
    }

    /**
     * And the row a single answer cannot hold: rules read here, and rules left to the cases.
     *
     * <p>Both assertions are about one reading of one position. Stop reading what the cases share
     * and the first fails; stop saying that a case writes something of its own and the second does.
     * Neither failure implies the other, which is what makes the two independent rather than two
     * spellings of one.
     */
    @Test
    void aSumCanBothStateARuleAndLeaveOneToItsCases() {
        Told told = reading("Both");

        assertTrue(states(told, "capped"),
                "Plain and Stamped both spread Common, so what Common writes holds of either");
        assertFalse(states(told, "stamped"),
                "and what Stamped writes holds of a Stamped, not of every value here");
        assertEquals(List.of("the value"), told.handedOn(),
                "so Stamped's own rule is left for the reading a match opens");
    }

    /** The shared part is a position of its own, so what its type writes is read there. */
    @Test
    void theSharedFieldIsAPositionOfTheSum() {
        Told told = reading("Both");

        assertTrue(told.guaranteed().containsKey("amount"),
                "a field every case spreads is readable on the sum, and so are its rules: "
                        + told.guaranteed());
    }

    /** The reading of the value itself, without walking anywhere. */
    private TypeGuarantees.At at(String name) {
        Core.Read root = place(name);
        return engine.guarantees().at(root, Denotations.none().location(root.binding(),
                engine.terms().placeSubject(root.binding()),
                engine.terms().placeTerm(root.binding())));
    }

    /** Reading everything but the rules {@code declaration} wrote. */
    private static GuaranteeWalk.Scope without(String declaration) {
        return new GuaranteeWalk.Scope(new GuaranteeWalk.Extent.EveryName(), _ -> false,
                RulesLeftOut.writtenOn(each -> each.name().equals(declaration)),
                PartsLeftOut.NONE);
    }

    /**
     * A rule is left out by the declaration that wrote it, and a sum writes none of what it states.
     *
     * <p>The two identities this reading tells apart, asked of one position. What stands at the sum
     * is the sum; what wrote the rule read there is the declaration its cases spread. A reader
     * naming the first leaves the rule in, because the sum wrote nothing, and a reader naming the
     * second takes it out although the sum is what it was read at.
     */
    @Test
    void aRuleIsLeftOutByWhoWroteItAndNotByWhereItIsRead() {
        assertTrue(states(reading("Marked"), "capped"),
                "read in full, what the cases share is stated at the sum");
        assertFalse(states(reading("Marked", without("Common")), "capped"),
                "Common wrote it, so leaving Common's rules out leaves it out");
        assertTrue(states(reading("Marked", without("Marked")), "capped"),
                "the sum wrote no rule, so leaving its rules out leaves this one standing");
    }

    /**
     * The same of a record that spreads, which is where the two identities first came apart.
     *
     * <p>A record's own name and the name that wrote a rule it carries are two names, exactly as a
     * sum's are. That they are the same name for a declaration writing its own clauses is what let
     * one predicate stand for both.
     */
    @Test
    void aRuleASpreadCarriesIntoARecordIsLeftOutByWhoWroteIt() {
        assertTrue(states(reading("Alone"), "capped"));
        assertFalse(states(reading("Alone", without("Common")), "capped"),
                "Common wrote it, wherever it is read");
        assertTrue(states(reading("Alone", without("Alone")), "capped"),
                "Alone wrote no rule of its own");
    }

    /**
     * What is left to the cases is what was not read here, and no more.
     *
     * <p>Said of the residual itself rather than of the fact that there is one. Handing the cases on
     * whole would name a case whose every rule was read at the sum, and nothing below could settle
     * a rule that was already settled above.
     */
    @Test
    void whatIsLeftToTheCasesIsWhatWasNotReadHere() {
        assertEquals(List.of("Stamped"), residualOf("Both"),
                "Plain carries only what the sum states; Stamped writes a rule of its own");
        assertEquals(List.of(), residualOf("Marked"),
                "neither case writes anything the sum did not state");
    }

    /** What the reading at {@code name} says another reading answers for. */
    private List<String> residualOf(String name) {
        return at(name).handedOn() instanceof TypeGuarantees.At.HandedOn.ToAnotherReading passed
                ? passed.under().stream().map(each -> Type.show(each)).toList()
                : List.of();
    }

    private static boolean states(Told told, String clause) {
        return told.guaranteed().values().stream().flatMap(List::stream)
                .anyMatch(each -> each.contains(clause));
    }
}
