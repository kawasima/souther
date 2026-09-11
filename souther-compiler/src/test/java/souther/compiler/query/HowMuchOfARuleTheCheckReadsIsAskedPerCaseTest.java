package souther.compiler.query;

import souther.compiler.WhereItSits;
import souther.compiler.check.CapabilityResult;
import souther.compiler.check.StaticRoute;
import souther.compiler.check.ContractDischarge;
import souther.compiler.check.ContractDischarge.RuleDischarge;
import souther.compiler.meta.ModulePath;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * What the compiler can make of what a behavior declares, asked rule by rule.
 *
 * <p>The classification a data's clause gets ({@link Shapes.InvariantCapabilities}), asked of the
 * other kind of clause. A rule is already about one case, and what {@code value} is differs between
 * cases, so two arms written under one arrow can be read to different depths — an answer per clause
 * would put them under one entry and leave a reader to guess which case it was told about.
 */
class HowMuchOfARuleTheCheckReadsIsAskedPerCaseTest {

    private static ContractDischarge of(String source, String behavior) {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("a.sou", source);
        Compilation c = Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY);
        Map<String, ContractDischarge> byName =
                c.db().ask(new Bodies.ContractCapabilities("m.a")).value();
        assertNotNull(byName, "the module reads, so its declarations are classified");
        return byName.get(behavior);
    }

    private static TypeSymbol named(String type) {
        return TypeSymbols.declared(new TypeKey("m.a", type));
    }

    private static List<TypeSymbol> casesOf(ContractDischarge discharge) {
        List<TypeSymbol> cases = new ArrayList<>();
        for (RuleDischarge rule : discharge.rules()) {
            cases.add(rule.rule().selector());
        }
        return cases;
    }

    private static final String FINDS = """
            module m.a exposing ( Id, Found, Missing, findIt )

            data Id      = Int
            data Found   = { id: Id }
            data Missing = { asked: Id }

            behavior findIt : (id: Id) -> Found | Missing
                constructs Found, Missing
                ensures answersTheRequest = Found   -> value.id == id
                                          | Missing -> value.asked == id

            let findIt (id) = if id.value > 0 then Found { id = id } else Missing { asked = id }
            """;

    @Test
    void aRuleIsClassifiedUnderTheCaseItIsAbout() {
        ContractDischarge discharge = of(FINDS, "findIt");

        assertEquals(List.of(named("Found"), named("Missing")),
                casesOf(discharge), "one answer per rule, named by the case the rule is about");
        for (RuleDischarge rule : discharge.rules()) {
            assertEquals("answersTheRequest", rule.capability().owed().name().orElse(null),
                    "under the name a violation of it is reported by");
        }
    }

    /** Two cases sharing one expression are two rules, and each is read for the case it is about —
     *  what `value` holds differs between them, so what the check can make of the same words can. */
    @Test
    void twoCasesUnderOneArrowAreTwoAnswers() {
        ContractDischarge discharge = of("""
                module m.a exposing ( Id, Found, Missing, findIt )

                data Id      = Int
                data Found   = { id: Id }
                data Missing = { id: Id }

                behavior findIt : (id: Id) -> Found | Missing
                    constructs Found, Missing
                    ensures Found | Missing -> value.id == id

                let findIt (id) = if id.value > 0 then Found { id = id } else Missing { id = id }
                """, "findIt");

        assertEquals(List.of(named("Found"), named("Missing")),
                casesOf(discharge));
    }

    /** The unit under the rule is the conjunct, as it is for a data's clause: what the check makes of
     *  one half of `a && b` is not what it makes of the other, and the half is what an author acts on. */
    @Test
    void eachConjunctOfARuleIsAnsweredOnItsOwn() {
        ContractDischarge discharge = of("""
                module m.a exposing ( Id, Found, findIt )

                data Id    = Int
                data Found = { id: Id, rank: Int }

                behavior findIt : (id: Id) -> Found
                    constructs Found
                    ensures value.id == id && value.rank >= 0

                let findIt (id) = Found { id = id, rank = 0 }
                """, "findIt");

        assertEquals(2, discharge.rules().size(), "one answer per conjunct");
        assertEquals(CapabilityResult.Analyzed.routed(new StaticRoute.AsABound(), new StaticRoute.AsATerm()),
                discharge.rules().get(1).capability().capability(),
                "`value.rank >= 0` is a relation the numeric domain reasons over");
    }

    /**
     * A rule is read in the representation the check has rules about, not in the one that runs.
     *
     * <p>An operation the language defines the meaning of stands here as the operation it is written
     * as. Read from the tree that runs, {@code List.allDistinctBy} would be the fold it becomes, and
     * the check has nothing to say about a fold.
     */
    @Test
    void aRuleIsReadAsWhatItIsWrittenAs() {
        ContractDischarge discharge = of("""
                module m.a exposing ( Id, Row, Rows, findIt )

                data Id   = Int
                data Row  = { name: String }
                data Rows = List<Row>

                behavior findIt : (id: Id) -> Rows
                    constructs Rows
                    ensures List.allDistinctBy(.name, value.value) && id.value > 0

                let findIt (id) = Rows { value = [] }
                """, "findIt");

        assertEquals(CapabilityResult.Analyzed.routed(new StaticRoute.AsATerm()),
                discharge.rules().get(0).capability().capability(),
                "a term the check can name and compare, and nothing weaker states it");
        assertEquals(CapabilityResult.Analyzed.routed(new StaticRoute.AsABound(), new StaticRoute.AsATerm()),
                discharge.rules().get(1).capability().capability());
    }

    /**
     * A rule naming a helper is answered where the rule is written, not where the helper is.
     *
     * <p>The helper is expanded before the rule is read, and an expansion of a module's own helper
     * carries the positions its body has. Answered from the expanded tree, the rule would be placed
     * inside the {@code let} — which for the reader that asks this is a rule belonging to no clause
     * of the behavior at all.
     */
    @Test
    void aRuleThroughAHelperIsAnsweredWhereItIsWritten() {
        String stands = """
                module m.a exposing ( Id, Found, findIt )

                let ranked (rank: Int, id: Id): Bool = rank > 0 && rank > id.value

                data Id    = Int
                data Found = { id: Id, rank: Int }

                behavior findIt : (id: Id) -> Found
                    constructs Found
                    ensures ranked(value.rank, id)

                let findIt (id) = Found { id = id, rank = 1 }
                """;
        ContractDischarge discharge = of(stands, "findIt");

        assertEquals(1, discharge.rules().size(),
                "one rule, because the author wrote one — the `&&` is the helper's, not theirs");
        assertEquals(10,
                WhereItSits.in(stands, discharge.rules().get(0).capability().owed().clause()).line(),
                "the `ensures` line, not the `let` on line 3");
    }

    /**
     * A case no rule names carries no stated relation, and that is the declaration speaking rather
     * than a mistake in it. It is answered here so a reader can be shown it, not reported.
     */
    @Test
    void theCasesNothingIsSaidAboutAreAnswered() {
        ContractDischarge discharge = of(FINDS
                .replace("                              | Missing -> value.asked == id\n", ""), "findIt");

        assertEquals(List.of(named("Missing")),
                discharge.casesNothingIsSaidAbout());
    }

    @Test
    void aBehaviorWhoseEveryCaseIsSpokenForHasNoneLeft() {
        assertEquals(List.of(), of(FINDS, "findIt").casesNothingIsSaidAbout());
    }

    /**
     * A declaration with an arm that cannot be read is not classified at all.
     *
     * <p>The arm contributes no rule, so what is left says nothing about the cases that arm named —
     * and a reader of it would be told that the author stated nothing about them, which is the
     * opposite of what they wrote. The refusal is reported where the declaration is held to its
     * rules; here there is simply no answer.
     */
    @Test
    void aDeclarationOneArmOfWhichCannotBeReadIsNotClassified() {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("a.sou", """
                module m.a exposing ( Id, Found, Missing, Other, findIt )

                data Id      = Int
                data Found   = { id: Id }
                data Missing = { asked: Id }
                data Other   = { x: Int }

                behavior findIt : (id: Id) -> Found | Missing
                    constructs Found, Missing
                    ensures Found | Other -> value.id == id

                let findIt (id) = if id.value > 0 then Found { id = id } else Missing { asked = id }
                """);
        Compilation c = Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY);

        Map<String, ContractDischarge> byName =
                c.db().ask(new Bodies.ContractCapabilities("m.a")).value();

        assertNotNull(byName);
        assertFalse(byName.containsKey("findIt"),
                "`Other` is not a case of the answer, so the arm naming `Found` beside it was not "
                        + "read — and nothing here may say `Found` was left unspoken for");
    }

    /** A behavior stating nothing has no classification: there is nothing to classify, and an empty
     *  answer would be a second way to say what absence says. */
    @Test
    void aBehaviorThatDeclaresNothingIsNotThere() {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("a.sou", """
                module m.a exposing ( Id, echo )

                data Id = Int

                behavior echo : (id: Id) -> Id
                let echo (id) = id
                """);
        Compilation c = Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY);
        Map<String, ContractDischarge> byName =
                c.db().ask(new Bodies.ContractCapabilities("m.a")).value();

        assertNotNull(byName);
        assertFalse(byName.containsKey("echo"));
    }
}
