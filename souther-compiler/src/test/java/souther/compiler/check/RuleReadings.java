package souther.compiler.check;

import souther.compiler.query.Compilation;
import souther.compiler.query.Db;
import souther.compiler.query.Shapes;
import souther.compiler.types.TypeSymbol;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * What a test hands a reading of a declaration's rules.
 *
 * <p>The same answer the compilation gives itself, asked the same way. A test that built one out of
 * a scope and an empty representation would be reading the tree the backend emits from and calling
 * it the declaration's rules, which is the reading these tests are about.
 */
public final class RuleReadings {

    private RuleReadings() {}

    /** The reading {@code module} is read under in {@code compilation}. */
    public static RuleReadingSource of(Compilation compilation, String module) {
        return of(compilation.db(), module);
    }

    /** The reading of the one module {@code source} writes, for a test that has the text and no
     *  compilation of its own. */
    public static RuleReadingSource ofSource(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        return of(compilation, compilation.modules().get(0));
    }

    /** The same, for a test that holds the database rather than the compilation. */
    public static RuleReadingSource of(Db db, String module) {
        RuleReadingSource source = Shapes.ruleReading(db, module).value();
        assertNotNull(source, "no reading of `" + module + "`'s rules");
        return source;
    }

    /** A reading with nothing expanded anywhere, for a test whose model states no rules. Asking it
     *  about a declaration is asking for a reading this never had, and it answers that nothing
     *  declares one rather than the tree the declaration happens to carry. */
    public static RuleReadingSource ofNoClauseFiled(Symbols symbols) {
        return new RuleReadingSource(symbols, noClauseFiled(), ClauseMeanings.NONE,
                ClauseLocations.NONE);
    }

    /** Where a reading with nothing expanded anywhere gets its clauses. */
    public static ExpandedClauseLookup noClauseFiled() {
        return ExpandedClauseLookup.NONE;
    }

    /** Where a reading of {@code module} gets its clauses, for a test that holds a scope of its own
     *  beside it. */
    public static ExpandedClauseLookup declaredBy(Db db, String module) {
        return of(db, module).invariants();
    }

    /**
     * What the one position of {@code source}'s {@code N} is left, as its own reading says it.
     *
     * <p>Rendered rather than handed over, so a test outside this package can hold two declarations
     * to answering alike without reaching into what a reading is made of.
     */
    public static String admittedByTheValueOf(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        Symbols symbols = souther.compiler.query.Scopes.derived(compilation.db(), "demo").value();
        TypeSymbol.AtModule name = souther.compiler.types.TypeSymbols.declared(
                new souther.compiler.types.TypeKey(symbols.module(), "N"));
        return String.valueOf(FieldDomains.of(name, of(compilation, "demo"),
                souther.compiler.query.ReadAs.THE_COMPILATION_DOES).admits(RuleKey.THE_VALUE));
    }

    /** A reading over the discharge tree of a scope no declaration of which wrote a clause, for a
     *  test that reads terms and states no rules. Named here and not offered by {@link Terms}, so
     *  that a reading of a module's declarations is built by saying which representation it reads
     *  and never by handing over a scope alone. */
    static Terms termsOfNoClauseFiled(Symbols symbols, ReadingPolicy policy) {
        return new Terms(Terms.Of.THE_DISCHARGE_TREE, policy,
                new Clauses(ofNoClauseFiled(symbols), DeclarationReadings.NONE));
    }
}
