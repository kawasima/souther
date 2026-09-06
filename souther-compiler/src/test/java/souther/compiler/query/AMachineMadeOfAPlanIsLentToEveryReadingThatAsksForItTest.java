package souther.compiler.query;

import souther.compiler.check.FieldDomains;
import souther.compiler.check.ReadingPolicy;
import souther.compiler.check.RuleKey;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.DeclarationReadings;
import souther.compiler.meta.ModulePath;
import souther.compiler.regex.PatternPlan;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;
import souther.compiler.values.StringFacts;
import souther.compiler.values.ValueSet;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The string machines a declaration's rules come to are the declaration's answer, made once, and
 * every reading that asks for them borrows rather than builds.
 *
 * <p>A declaration with a pattern rule is read for every question that reaches it — every
 * construction in a body, every input a behavior takes, the count of what the module's types hold.
 * Each reading used to plan the pattern and build its machine again. What is held here is that
 * the machines are a question of the store keyed by the declaration, and that a reading handed the
 * answer takes the machine from it: read under an allowance that could build nothing, it still
 * comes to the set the pattern admits, which is the one thing a reading that built for itself
 * could not do.
 */
class AMachineMadeOfAPlanIsLentToEveryReadingThatAsksForItTest {

    private static final String MODULE = "example.lent";
    private static final TypeKey CODE = new TypeKey(MODULE, "Code");

    /**
     * One pattern-ruled declaration, constructed in two behaviors, taken as input by a third, and
     * standing in a case of a fourth's input — which is where a row makes the reading ask whether
     * the rule leaves a string where the case puts it.
     */
    private static final String PATTERNED = """
            module example.lent

            data Code = String
                invariant String.matches("[A-Z]{2}[0-9]{3}", value)

            data Tagged = { code: Code, n: Int }

            data Wanted = { code: Code }
            data Nothing
            data Intent = Wanted | Nothing

            data Request = { tag: Tagged, intent: Intent }

            behavior first : (n: Int) -> Tagged
                constructs Tagged, Code
            let first (n) = Tagged { code = Code("AB123"), n = n }

            behavior second : (n: Int) -> Tagged
                constructs Tagged, Code
            let second (n) = Tagged { code = Code("CD456"), n = n + 1 }

            behavior third : (t: Tagged) -> Int
            let third (t) = t.n

            behavior fourth : (r: Request) -> Int
            let fourth (r) = match r.intent with
                | Wanted { code } -> 1
                | Nothing -> 0

            example fourth
                | "wanted" : (Request { tag = Tagged { code = Code("AB123"), n = 1 },
                                        intent = Wanted { code = Code("CD456") } }) -> 1
            """;

    /** The same shape with nothing said about a string, so no machine is ever made. */
    private static final String UNPATTERNED = PATTERNED
            .replace("module example.lent", "module example.unlent")
            .replace("    invariant String.matches(\"[A-Z]{2}[0-9]{3}\", value)\n", "");

    /** An allowance that can build no machine at all: one state is fewer than any pattern here
     *  needs, so a reading under it comes to a set only by borrowing one. */
    private static final ReadingPolicy BUILDING_NOTHING = new ReadingPolicy(64, 12,
            new PatternPlan.Budget(1, 1), new PatternPlan.Budget(1, 1));

    @Test
    void theDeclarationsMachinesAreOneAnswerOfTheStore() {
        Compilation compilation = compiled(PATTERNED);
        Db db = compilation.db();

        StringFacts code = db.ask(new Machines.OfDeclaration(CODE)).value();
        assertFalse(code.realized().isEmpty(),
                "the pattern's plan is realized once, as the declaration's own answer");
        assertFalse(code.extents().isEmpty(),
                "and where the strings it admits stop is taken there");
        assertFalse(code.inside().isEmpty(),
                "and whether it leaves a string on the order at all is decided there");
        assertTrue(code.realized().values().stream().allMatch(ValueSet.Matching.class::isInstance),
                () -> "a plan of this declaration comes to a language: " + code.realized());
    }

    @Test
    void aDeclarationThatStatesNothingAboutAStringMakesNoMachine() {
        // The control for the assertion above: the answer exists for every declaration that is
        // read, and one that names no pattern has nothing in it.
        Compilation compilation = compiled(UNPATTERNED);
        Db db = compilation.db();

        List<Machines.OfDeclaration> asked = ofDeclarationsIn(db);
        assertFalse(asked.isEmpty(), "the declarations are read, and asked for their machines");
        for (Machines.OfDeclaration each : asked) {
            assertEquals(StringFacts.NONE, db.ask(each).value(),
                    () -> "nothing is made for a declaration with no rule about a string: " + each);
        }
    }

    @Test
    void aReadingHandedTheAnswerBorrowsTheMachineRatherThanBuildingIt() {
        Compilation compilation = compiled(PATTERNED);
        Db db = compilation.db();
        RuleReadingSource source = Shapes.ruleReading(db, MODULE).value();

        // The control: a reading that has to build for itself, under an allowance that can build
        // nothing, does not come to the set the pattern admits.
        ValueSet built = FieldDomains.of(TypeSymbols.declared(CODE), source, BUILDING_NOTHING,
                DeclarationReadings.NONE).admits(RuleKey.THE_VALUE).approximation();
        assertFalse(built instanceof ValueSet.Matching,
                () -> "with nothing lent and nothing affordable, the set is not the language: "
                        + built);

        // And the claim: the same reading handed the store's answer comes to the language, which
        // it can only have borrowed.
        ValueSet borrowed = FieldDomains.of(TypeSymbols.declared(CODE), source, BUILDING_NOTHING,
                db.readings()).admits(RuleKey.THE_VALUE).approximation();
        assertTrue(borrowed instanceof ValueSet.Matching,
                () -> "handed the declaration's answer, the reading comes to the language it"
                        + " could not have built: " + borrowed);
    }

    @Test
    void aBodyEditedLeavesTheDeclarationsAnswerWhereItWas() {
        // A declaration's answer is about its rules, and a body changes no rule. So an edit to a
        // body — which reads the declaration again for every construction it holds — finds the
        // answer it had and changes nothing in it.
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("f0", PATTERNED);
        Compilation compilation = Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY);
        assertTrue(clean(compilation), "the model under test compiles clean before the edit");
        Db db = compilation.db();
        Map<Machines.OfDeclaration, StringFacts> before = answersIn(db);
        assertFalse(before.isEmpty(), "the declarations were read and answered");

        byId.put("f0", PATTERNED.replace("n = n + 1 }", "n = n + 2 }"));
        compilation.update(byId, Set.of());
        assertTrue(clean(compilation), "and after it");

        assertEquals(before, answersIn(db),
                "the machines a module's declarations come to are the same after a body changes");
        for (Machines.OfDeclaration each : before.keySet()) {
            assertTrue(db.isComputed(each), () -> "still held: " + each);
        }
    }

    private static Compilation compiled(String source) {
        Compilation compilation = Compilation.ofSources(List.of(source), ModulePath.EMPTY);
        compilation.answerEverything();
        assertTrue(clean(compilation), "the model under test compiles clean");
        return compilation;
    }

    private static boolean clean(Compilation compilation) {
        return compilation.diagnostics().values().stream().allMatch(List::isEmpty);
    }

    private static List<Machines.OfDeclaration> ofDeclarationsIn(Db db) {
        return db.everyAnswer().keySet().stream()
                .filter(Machines.OfDeclaration.class::isInstance)
                .map(Machines.OfDeclaration.class::cast)
                .toList();
    }

    private static Map<Machines.OfDeclaration, StringFacts> answersIn(Db db) {
        Map<Machines.OfDeclaration, StringFacts> out = new LinkedHashMap<>();
        for (Machines.OfDeclaration each : ofDeclarationsIn(db)) {
            out.put(each, db.ask(each).value());
        }
        return out;
    }
}
