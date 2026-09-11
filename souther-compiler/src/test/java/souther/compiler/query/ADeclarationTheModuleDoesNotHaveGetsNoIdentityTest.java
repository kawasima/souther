package souther.compiler.query;

import souther.compiler.WhereItSits;
import souther.compiler.source.SourceId;

import org.junit.jupiter.api.Test;
import souther.compiler.Compiler;
import souther.compiler.ast.Hir;
import souther.compiler.check.Resolve;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.Located;
import souther.compiler.diag.PhysicalPos;
import souther.compiler.diag.SourcePos;
import souther.compiler.diag.msg.BehaviorMessage;
import souther.compiler.diag.msg.DataMessage;
import souther.compiler.meta.ModulePath;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Text the declaration world does not hold gets no identity, and so no semantic answer.
 *
 * <p>Which declarations a module has is settled where they are indexed: a name declared twice keeps
 * the first, and a built-in case name is refused outright. Resolution reads what the module
 * declares rather than what its text writes, so a declaration that is not one of them is never met
 * — and nothing has to make up an identity for it to be resolved under.
 *
 * <p>What used to happen instead is the state this fixes: the module did not declare the second
 * {@code D}, and the names written inside it were resolved all the same, so the resolution index
 * held semantic uses belonging to a declaration nothing else in the compilation had.
 *
 * <p>Every assertion here is about the semantic world — what is resolved, what identities exist,
 * what the index holds. What an editor shows is a separate question: answering a rejected
 * declaration's inside from a recovery pass of its own would leave every proposition here true, and
 * nothing in this test is a claim that such an answer must not exist.
 */
class ADeclarationTheModuleDoesNotHaveGetsNoIdentityTest {

    private static final String ID = "m.sou";

    /** `D` is declared on line 4 and again on line 5, and `Amount` is at column 15 of both. */
    private static final String TWICE = """
            module m exposing ( D, Amount )

            data Amount = { v: Int }
            data D = { a: Amount }
            data D = { b: Amount }
            """;

    /** `Some` cannot be declared, and writes `Amount` at column 18 of line 5. */
    private static final String RESERVED = """
            module m exposing ( D, Amount )

            data Amount = { v: Int }
            data D = { a: Amount }
            data Some = { b: Amount }
            """;

    private static final TypeSymbol.AtModule AMOUNT = TypeSymbols.declared(new TypeKey("m", "Amount"));

    private static Db db(String source) {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put(ID, source);
        return Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY).db();
    }

    /**
     * The declarations of the tree resolution answers with, read off that tree.
     *
     * <p>Not {@link Names.ResolvedDeclarations}, which indexes it by name and so settles a second
     * time what the module declares — asked there, a copy left in the tree would be dropped on the
     * way out and the proposition would hold whatever this pass did.
     */
    private static List<Hir.Def> resolvedDefs(String source) {
        return db(source).ask(new Names.Resolved("m")).value().defs();
    }

    private static List<String> namesOf(List<Hir.Def> defs) {
        return defs.stream().map(Hir.Def::name).toList();
    }

    private static List<SourcePos> semanticUsesOfAmount(String source) {
        Resolve.ResolutionIndex index = db(source).ask(new Names.Facts("m")).value();
        return index.types().stream().filter(use -> AMOUNT.equals(use.denotes()))
                .map(Resolve.TypeUse::pos).toList();
    }

    private static List<Diagnostic> diagnostics(String source) {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put(ID, source);
        return Located.diagnosticsOf(Compiler.diagnoseModules(byId, Set.of())).get(new SourceId(ID));
    }

    /** The copy is not a declaration, so what is resolved is the one the module has — the first. */
    @Test
    void aCopiedDeclarationIsNotOneOfWhatIsResolved() {
        List<Hir.Def> resolved = resolvedDefs(TWICE);

        assertEquals(List.of("Amount", "D"), namesOf(resolved));
        assertEquals(4, WhereItSits.in(TWICE, resolved.get(1).pos()).line(),
                "the declaration the module has is the first");
    }

    @Test
    void aDeclarationTakingABuiltInCaseNameIsNotOneOfWhatIsResolved() {
        assertEquals(List.of("Amount", "D"), namesOf(resolvedDefs(RESERVED)));
    }

    /** No identity is issued for it, so nothing can be asked about it as a declaration. */
    @Test
    void nothingTheModuleDoesNotDeclareHasAnIdentityToBeAskedAbout() {
        Db db = db(RESERVED);
        TypeKey some = new TypeKey("m", "Some");

        assertFalse(db.ask(new Names.ResolvedDeclaration(some)).present());
        assertFalse(db.ask(new Names.Definition(some)).present());
        assertTrue(db.ask(new Names.Definition(new TypeKey("m", "D"))).present(),
                "the declaration the module does have is answered for");
    }

    /**
     * The index records what the module's declarations name, and nothing else. A use written inside
     * the copy would belong to a declaration nothing else in the compilation holds — the state this
     * fixes — so the two writings of `Amount` come to one semantic use.
     */
    @Test
    void theResolutionIndexHoldsOnlyWhatTheModulesDeclarationsName() {
        assertEquals(List.of(new PhysicalPos(4, 15)), sitsIn(TWICE, semanticUsesOfAmount(TWICE)));
        assertEquals(List.of(new PhysicalPos(4, 15)),
                sitsIn(RESERVED, semanticUsesOfAmount(RESERVED)));
    }

    /** Where each of {@code places} sits in {@code source}, which is what the line above names. */
    private static List<PhysicalPos> sitsIn(String source, List<SourcePos> places) {
        return places.stream().map(each -> WhereItSits.in(source, each)).toList();
    }

    /**
     * One mistake, one diagnostic. A copy holding a name nothing declares used to be reported for
     * the name as well, which sends the author into a block they are being told is a copy of the one
     * above it — and the report was about a declaration the module does not have.
     */
    @Test
    void aCopiedDeclarationIsReportedForBeingOneAndNotForWhatIsWrittenInIt() {
        List<Diagnostic> found = diagnostics("""
                module m exposing ( D, Amount )

                data Amount = { v: Int }
                data D = { a: Amount }
                data D = { b: Nowhere }
                """);

        assertEquals(1, found.size(), "one copied declaration, one diagnostic: " + said(found));
        assertInstanceOf(DataMessage.ADataIsAlreadyDefined.class, found.get(0).said());
    }

    @Test
    void aDeclarationTakingABuiltInCaseNameIsReportedForThatAndNotForWhatIsWrittenInIt() {
        List<Diagnostic> found = diagnostics("""
                module m exposing ( D, Amount )

                data Amount = { v: Int }
                data D = { a: Amount }
                data Some = { b: Nowhere }
                """);

        assertEquals(1, found.size(), "one refused declaration, one diagnostic: " + said(found));
        assertInstanceOf(BehaviorMessage.ABuiltInOptionCaseCannotBeDeclared.class,
                found.get(0).said());
    }

    private static List<String> said(List<Diagnostic> found) {
        return found.stream().map(d -> d.said().getClass().getSimpleName()).toList();
    }
}
