package souther.compiler.query;

import souther.compiler.WhereItSits;
import souther.compiler.diag.Primary;

import souther.compiler.source.SourceId;

import souther.compiler.Compiler;
import souther.compiler.diag.msg.NameMessage;
import souther.compiler.diag.msg.ModuleMessage;
import souther.compiler.diag.Located;
import souther.compiler.diag.Diagnostic;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A name that denotes nothing is reported and the compiler carries on.
 *
 * <p>It denotes an error type instead of ending the module. That type absorbs, so the one mistake is
 * reported once rather than again at every position the value it produced flowed into — and the rest
 * of the module is read, so an author is told about every unknown name at once instead of one per
 * compile.
 */
class UnresolvedNamesTest {

    private static List<Diagnostic> diagnose(String source) {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("a.sou", source);
        return Located.diagnosticsOf(Compiler.diagnoseModules(byId, Set.of())).get(new SourceId("a.sou"));
    }

    @Test
    void everyUnknownNameIsReportedAtOnce() {
        List<Diagnostic> found = diagnose("""
                module m.a

                data A = { one: Nowhere, two: Elsewhere }
                """);

        assertEquals(2, found.size(), "both, not the first: " + found);
        assertTrue(found.stream().anyMatch(d -> List.copyOf(d.values().values()).contains("Nowhere")));
        assertTrue(found.stream().anyMatch(d -> List.copyOf(d.values().values()).contains("Elsewhere")));
    }

    @Test
    void oneMistakeIsNotReportedAtEveryPlaceTheValueWent() {
        // `total` has no type, so every position it flows into could disagree with it. None does:
        // an error type is assignable both ways.
        List<Diagnostic> found = diagnose("""
                module m.a exposing ( Order, price )

                data Order = { total: Nowhere }

                behavior price : (o: Order) -> Int
                let price (o) = o.total
                """);

        assertEquals(1, found.size(), "the unknown name, and nothing downstream of it: " + found);
    }

    @Test
    void aModuleWithATypeNobodyCanNameEmitsNothing() {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("a.sou", """
                module m.a

                data A = { one: Nowhere }
                """);
        Compilation c = Compilation.ofDocuments(byId, Set.of(),
                souther.compiler.meta.ModulePath.EMPTY);
        Located.diagnosticsOf(c.diagnostics());

        assertEquals(Map.of(), c.classes(),
                "there is no bytecode for a type nobody could name");
    }
    @Test
    void anUnknownNameInABodyAlsoStopsTheModuleBeingEmitted() {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("a.sou", """
                module m.a exposing ( A, f )

                data A = { n: Int }

                behavior f : (n: Int) -> A
                    constructs A
                let f (n) = Nowhere { n = n }
                """);
        Compilation c = Compilation.ofDocuments(byId, Set.of(),
                souther.compiler.meta.ModulePath.EMPTY);
        List<Diagnostic> found = Located.diagnosticsOf(c.diagnostics()).get(new SourceId("a.sou"));

        assertEquals(1, found.size(),
                "the name that denotes nothing, and nothing about constructing it: " + found);
        assertTrue(found.stream().anyMatch(d -> d.said() instanceof NameMessage.NoTypeOfThatName),
                "the construction names nothing, and that is said: " + found);
        assertEquals(Map.of(), c.classes(),
                "a construction of a type nobody could name is not emitted");
    }

    @Test
    void abandoningOneDefinitionStillChecksTheOthers() {
        // `f` rests on a name that denotes nothing, so there is nothing to say about what it does.
        // `g` is a different definition and is wrong in its own right.
        List<Diagnostic> found = diagnose("""
                module m.a exposing ( A, f, g )

                data A = { n: Int }

                behavior f : (n: Int) -> A
                    constructs A
                let f (n) = Nowhere { n = n }

                behavior g : (n: Int) -> Int
                let g (n) = "not an Int"
                """);

        assertEquals(2, found.size(),
                "the unknown name, and g's own mistake — nothing about what f does: " + found);
        assertTrue(found.stream().anyMatch(d -> d.said() instanceof NameMessage.NoTypeOfThatName),
                "the name that denotes nothing: " + found);
        assertTrue(found.stream().anyMatch(d -> d.diff() != null),
                "and a type mismatch in the definition that has one: " + found);
    }

    /**
     * The same, for a name in the value namespace. A stage that names no behavior is one definition's
     * mistake, and the definitions around it are checked as they would be without it.
     */
    @Test
    void anUnknownBehaviorInAPipelineStillLetsTheOtherDefinitionsBeChecked() {
        List<Diagnostic> found = diagnose("""
                module m.a exposing ( f, p, g )

                behavior f : (n: Int) -> Int
                let f (n) = n

                behavior p = f >-> nosuch

                behavior g : (n: Int) -> Int
                let g (n) = "not an Int"
                """);

        assertEquals(2, found.size(),
                "the unknown behavior, and g's own mistake: " + found);
        assertTrue(found.stream().anyMatch(d -> d.said() instanceof NameMessage.NoBehaviorOfThatNameInThisPipeline),
                "the stage that names no behavior: " + found);
        assertTrue(found.stream().anyMatch(d -> d.diff() != null),
                "and the type mismatch in the definition that has one: " + found);
    }

    /** Every unknown name in a body at once, as for a type — not the first one a check happened to
     * reach. */
    @Test
    void everyUnknownNameInABodyIsReportedAtOnce() {
        List<Diagnostic> found = diagnose("""
                module m.a exposing ( f, g )

                behavior f : (n: Int) -> Int
                let f (n) = nowhere

                behavior g : (n: Int) -> Int
                let g (n) = elsewhere
                """);

        assertEquals(2, found.size(), "both, not the first: " + found);
        assertTrue(found.stream().allMatch(d -> d.said() instanceof NameMessage.NoValueOfThatNameInScope),
                found.toString());
    }

    /** A definition resting on a name in the value namespace that denotes nothing says nothing
     * further, and the definitions around it are checked as they would be without it. */
    @Test
    void abandoningADefinitionOverAnUnknownValueNameStillChecksTheOthers() {
        List<Diagnostic> found = diagnose("""
                module m.a exposing ( f, g )

                behavior f : (n: Int) -> Int
                let f (n) = nowhere

                behavior g : (n: Int) -> Int
                let g (n) = "not an Int"
                """);

        assertEquals(2, found.size(),
                "the unknown name, and g's own mistake — nothing about what f does: " + found);
        assertTrue(found.stream().anyMatch(d -> d.said() instanceof NameMessage.NoValueOfThatNameInScope),
                "the name that denotes nothing: " + found);
        assertTrue(found.stream().anyMatch(d -> d.diff() != null),
                "and the type mismatch in the definition that has one: " + found);
    }

    /** A stage that names nothing is pointed at where it is written, not at the whole behavior. */
    @Test
    void anUnknownStageIsReportedAtTheStage() {
        String source = """
                module m.a exposing ( f, p )

                behavior f : (n: Int) -> Int
                let f (n) = n

                behavior p = f >-> nosuch
                """;
        List<Diagnostic> found = diagnose(source);

        assertEquals(1, found.size(), found.toString());
        assertEquals(6, WhereItSits.in(source, ((Primary.InSource) found.get(0).primary()).place().region()).start().line(), found.toString());
        assertEquals(20, WhereItSits.in(source, ((Primary.InSource) found.get(0).primary()).place().region()).start().column(),
                "the stage, not the behavior it is in: " + found);
    }

    /** A composition resting on a stage that names nothing has no meaning to emit, and neither has
     * the module it is in. */
    @Test
    void aModuleWithAStageThatNamesNothingEmitsNothing() {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("a.sou", """
                module m.a exposing ( f, p )

                behavior f : (n: Int) -> Int
                let f (n) = n

                behavior p = f >-> nosuch
                """);
        Compilation c = Compilation.ofDocuments(byId, Set.of(),
                souther.compiler.meta.ModulePath.EMPTY);
        Located.diagnosticsOf(c.diagnostics());

        assertEquals(Map.of(), c.classes(),
                "there is no bytecode for a composition nobody could resolve");
    }

    /** The same, where the stage names a module this compilation does not have. */
    @Test
    void aStageInAModuleNobodyHasStillLetsTheOtherDefinitionsBeChecked() {
        List<Diagnostic> found = diagnose("""
                module m.a exposing ( f, p, g )

                behavior f : (n: Int) -> Int
                let f (n) = n

                behavior p = nosuch.h >-> f

                behavior g : (n: Int) -> Int
                let g (n) = "not an Int"
                """);

        assertEquals(2, found.size(),
                "the stage nobody declares, and g's own mistake: " + found);
        assertTrue(found.stream().anyMatch(d -> d.diff() != null),
                "and the type mismatch in the definition that has one: " + found);
    }

    /**
     * The same as {@link #abandoningOneDefinitionStillChecksTheOthers}, with the unknown name in a
     * declaration rather than in a body. Where the name is written must not decide whether the rest
     * of the module is checked.
     */
    @Test
    void anUnknownTypeInADeclarationStillLetsTheOtherDefinitionsBeChecked() {
        List<Diagnostic> found = diagnose("""
                module m.a exposing ( A, g )

                data A = { value: Nowhere }

                behavior g : (n: Int) -> Int
                let g (n) = "not an Int"
                """);

        assertEquals(2, found.size(),
                "the unknown name, and g's own mistake: " + found);
        assertTrue(found.stream().anyMatch(d -> d.said() instanceof NameMessage.NoTypeOfThatName),
                "the name that denotes nothing: " + found);
        assertTrue(found.stream().anyMatch(d -> d.diff() != null),
                "and the type mismatch in the definition that has one: " + found);
    }

    /**
     * A stage naming a module this compilation has and cannot read leaves a hole nothing here
     * reported: what is wrong is that module's, and its author is the one who can act on it. The
     * module holding the hole must still not be emitted — its composition calls a behavior that will
     * not exist — and the compiler must say so rather than fail on the way to saying it.
     */
    @Test
    void aStageInAModuleThatCannotBeReadIsNotEmittedAndSaysNothingHere() {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("x.sou", """
                module souther.x exposing ( ship )

                behavior ship : (n: Int) -> Int
                let ship (n) = n
                """);
        byId.put("a.sou", """
                module m.a exposing ( f, p )

                behavior f : (n: Int) -> Int
                let f (n) = n

                behavior p = f >-> souther.x.ship
                """);
        Compilation c = Compilation.ofDocuments(byId, Set.of(),
                souther.compiler.meta.ModulePath.EMPTY);

        assertEquals(List.of(), Located.diagnosticsOf(c.diagnostics()).get(new SourceId("a.sou")),
                "the reserved module name is x's mistake, not this one's");
        assertTrue(Located.diagnosticsOf(c.diagnostics()).get(new SourceId("x.sou")).stream()
                        .anyMatch(d -> d.said() instanceof ModuleMessage.TheModuleIsInTheReservedNamespace),
                "and it is reported there: " + Located.diagnosticsOf(c.diagnostics()).get(new SourceId("x.sou")));
        assertEquals(Map.of(), c.classes(),
                "a composition whose stage nobody can name is not emitted");
    }

    /** One mistake in a `depends on`, one diagnostic: the fn's trailing parameters are named by the
     * clause, so a clause that names nothing leaves nothing to hold them against. */
    @Test
    void aRequiresNamingNothingIsReportedOnceNotTwice() {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("b.sou", """
                module m.b exposing ( ship )

                behavior ship : (n: Int) -> Int
                """);
        byId.put("a.sou", """
                module m.a exposing ( f )

                behavior f : (n: Int) -> Int
                    depends on m.b.charge
                let f (n, charge) = charge(n)
                """);
        List<Diagnostic> found = Located.diagnosticsOf(Compiler.diagnoseModules(byId, Set.of())).get(new SourceId("a.sou"));

        assertEquals(1, found.size(),
                "the clause that names nothing, and nothing about the parameters it names: " + found);
        assertEquals("E1607", found.get(0).code(), found.toString());
    }
}
