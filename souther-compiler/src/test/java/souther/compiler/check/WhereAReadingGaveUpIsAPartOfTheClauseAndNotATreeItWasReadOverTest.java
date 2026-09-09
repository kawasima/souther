package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.diag.SourcePos;
import souther.compiler.query.Compilation;
import souther.compiler.query.Scopes;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Where a rule was given up on is a part of the clause an author wrote, and not the tree a reading
 * happened to be over.
 *
 * <p>A clause is read once for every place the walk opens a value at, over whatever tree the
 * substitution built there, and two compiles of one source build two trees again. Told apart by the
 * tree, one written part comes back as several things for an author to look at, and which of them a
 * reader is handed is a fact about how the compiler allocated.
 *
 * <p>The two sides of that are held here together, because either alone passes with the wrong
 * answer: sites that are all equal would satisfy the first and sites that are never equal would
 * satisfy the second.
 */
class WhereAReadingGaveUpIsAPartOfTheClauseAndNotATreeItWasReadOverTest {

    private static final String UNREAD_A = souther.compiler.ARuleNoReadingTakesIn.about("a");

    /**
     * Two parts nothing reads, spelled the same way, about the one position.
     *
     * <p>Spelled the same on purpose: what tells them apart is where an author put them and nothing
     * else, so a site that stopped saying where it stands would have them come out as one.
     */
    private static final String TWO_PARTS_NOTHING_READS = """
            module demo

            data N = { a: String }
                invariant r = UNREAD_A && UNREAD_A
            """.replace("UNREAD_A", UNREAD_A);

    /** One source, compiled twice, is one set of places. */
    @Test
    void twoCompilesOfOneSourceGiveUpAtTheSamePlaces() {
        assertEquals(sitesIn(TWO_PARTS_NOTHING_READS), sitesIn(TWO_PARTS_NOTHING_READS),
                "which part of a clause an author is sent to is the clause's answer, and two"
                        + " compiles of one source are two trees of it");
    }

    /** And two parts of one clause are two places, however alike they are spelled. */
    @Test
    void andTwoPartsOfOneClauseAreTwoPlaces() {
        assertEquals(2, sitesIn(TWO_PARTS_NOTHING_READS).size(),
                "an author wrote two of them and has two things to look at");
    }

    /**
     * And each of them stands where it stood, which the sites above do not say.
     *
     * <p>Said separately because a site is told from another by which part of the clause it is and
     * by nothing else ({@code AtALeaf.equals}), so comparing sites compares the identities and
     * leaves what an author is shown unasked. The two halves of a site are two claims and one
     * comparison would carry only the first.
     */
    @Test
    void andEachOfThemStandsWhereItStood() {
        assertEquals(placesIn(TWO_PARTS_NOTHING_READS), placesIn(TWO_PARTS_NOTHING_READS),
                "where a part was written is the source's answer, and two compiles read one"
                        + " source");
    }

    /** Where each of them stands, in the order the clause numbers them. */
    private static List<SourcePos> placesIn(String source) {
        return sitesIn(source).stream()
                .map(RuleShortfall.Site.AtALeaf.class::cast)
                .sorted(Comparator.comparingInt(each -> each.at().ordinal()))
                .map(RuleShortfall.Site::writtenAt)
                .toList();
    }

    /** Everywhere the reading of values gave up, of every rule of {@code N}. */
    private static Set<RuleShortfall.Site> sitesIn(String source) {
        Set<RuleShortfall.Site> out = new LinkedHashSet<>();
        read(source).accounting().values().forEach(accounting ->
                accounting.answers().forEach((_, outcome) -> {
                    if (outcome instanceof RuleAccounting.Outcome.Unaccounted it
                            && it.why() instanceof RuleAccounting.Why.TheValueReadingSays says) {
                        says.shortfalls().forEach(each -> out.add(each.site()));
                    }
                }));
        return out;
    }

    private static FieldDomains read(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        assertEquals(List.of(), compilation.diagnostics().values().stream()
                .flatMap(List::stream).map(each -> each.diagnostic().code()).toList(),
                "the model this reads has to be one somebody could write");
        Symbols symbols = Scopes.derived(compilation.db(), "demo").value();
        TypeSymbol.AtModule name = TypeSymbols.declared(new TypeKey(symbols.module(), "N"));
        return FieldDomains.of(name, RuleReadings.of(compilation, "demo"),
                souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
    }
}
