package souther.compiler.inputs;

import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredSig;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a case of a sum declares is declared, so it is read.
 *
 * <p>Issue #1012. A behavior taking a sum was measured on the one axis the sum itself states: the
 * reading stopped there, and every field of every case was out of reach of the partition, of the
 * boundary and of generation. What a case holds is not chosen by whatever constructs one — a
 * {@code GlobalQuery} has a {@code tag} whether or not anything builds one — so those fields are
 * positions of the input, standing under the narrowing that says which case the value turned out to
 * be.
 */
class APositionUnderACaseIsAPositionTest {

    private static final String QUERIES = """
            module g

            data Limit = Int invariant value >= 1
            data Tag = String
            data Username = String
            data Paging = { limit: Limit }
            data GlobalQuery = { ...Paging, tag: Tag?, author: Username? }
            data FeedQuery = { ...Paging, followees: List<Username> }
            data ArticleQuery = GlobalQuery | FeedQuery
            data Page = { n: Int }

            behavior readArticles : (query: ArticleQuery) -> Page
            behavior readGlobal : (query: GlobalQuery) -> Page
            """;

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

    private static List<String> positionsOf(String source, String behavior) {
        return reading(source, behavior).positions().stream()
                .map(each -> each.path().toString()).toList();
    }

    /**
     * Every case's fields, each under the narrowing it stands beneath.
     *
     * <p>And what an optional field holds is one of them. {@code tag} is a position of its own —
     * the model divides it into holding something and holding nothing — and {@code tag@Some} is
     * where what it holds stands, which is where that type's rules are read and where its border is
     * owed. A narrowing takes no level either way, so both are at the same distance from the
     * parameter as a plain field would be.
     */
    @Test
    void theFieldsOfEveryCaseArePositionsOfTheInput() {
        assertEquals(List.of("query",
                        "query@GlobalQuery", "query@GlobalQuery.limit", "query@GlobalQuery.tag",
                        "query@GlobalQuery.tag@Some",
                        "query@GlobalQuery.author", "query@GlobalQuery.author@Some",
                        "query@FeedQuery", "query@FeedQuery.limit", "query@FeedQuery.followees",
                        "query@FeedQuery.followees[*]"),
                positionsOf(QUERIES, "readArticles"));
    }

    /**
     * A narrowing takes no level of the structure.
     *
     * <p>{@code query@GlobalQuery.limit} is the same distance from the parameter as
     * {@code query.limit} is where the same record is the parameter, because naming which case a
     * value turned out to be does not move into it. Counted as a level, the same field would be
     * measured or not according to whether an author wrote a sum around the record it is in.
     */
    @Test
    void aNarrowingIsNotALevel() {
        List<String> underTheSum = positionsOf(QUERIES, "readArticles").stream()
                .filter(each -> each.startsWith("query@GlobalQuery"))
                .map(each -> each.replace("@GlobalQuery", "")).toList();
        assertEquals(positionsOf(QUERIES, "readGlobal"), underTheSum);
    }

    /** And the sum itself is still the position it was, still divided into its cases. */
    @Test
    void theSumIsStillAPositionOfItsOwn() {
        InputDomain read = reading(QUERIES, "readArticles");
        assertNotNull(read.at(TermPath.of("query")));
        assertFalse(read.at(TermPath.of("query")).obligationCases().isEmpty(),
                "the sum divides into its cases, which reaching under it does not take away");
    }

    /**
     * A case that is the whole of a value puts no position anywhere.
     *
     * <p>Naming a unit case builds it, so there is nothing under one to read. A position for it
     * would be a report naming a place with nothing in it, once per case of every enumeration in
     * every model.
     */
    @Test
    void aUnitCasePutsNoPositionUnderTheSum() {
        assertEquals(List.of("s"), positionsOf("""
                module g

                data Red
                data Green
                data Colour = Red | Green
                data Ack = { at: String }

                behavior paint : (s: Colour) -> Ack
                """, "paint"));
    }

    /**
     * And a case the rules refuse puts none either.
     *
     * <p>Every field of such a case is a row nobody can write, which is the answer a value whose
     * own rules contradict already gets. Walked from what the type declares rather than from what
     * the position came back owing, the reading would measure positions the same reading has just
     * said the model cannot reach.
     */
    @Test
    void aCaseTheRulesRefusePutsNoPositionUnderTheSum() {
        assertEquals(List.of("s", "s@Present", "s@Present.note"), positionsOf("""
                module g

                data Missing
                data Present = { note: String }
                data State = Missing | Present

                data AvailableState = State
                    invariant here = value /= Missing

                data Ack = { at: String }

                behavior use : (s: AvailableState) -> Ack
                """, "use"));
    }

    /**
     * The rules under a case are the case's own.
     *
     * <p>A clause is not written across a narrowing: what a {@code Held} says about its fields is
     * written in {@code Held} and cannot be written in the sum, which carries no clause at all. So
     * a new reading of the rules begins at the case, and what it leaves is read at the positions
     * under it.
     */
    @Test
    void theRulesUnderACaseAreTheCasesOwn() {
        InputDomain read = reading("""
                module g

                data Empty
                data Held = { least: Int, most: Int } invariant ordered = least <= most
                data Box = Empty | Held
                data Ack = { at: String }

                behavior open : (b: Box) -> Ack
                """, "open");
        Position least = read.at(TermPath.of("b")
                .refine(toLeaf(caseNamed(read, "Held"))).then("least"));
        assertNotNull(least, "a field of the case is a position");
        assertTrue(least.rulesLeftUnread().isEmpty(),
                "and the clause relating it to the field beside it was reached");
    }

    /** The narrowing to one leaf, spelled the way the checker's resolution of an arm spells it: a
     *  leaf is a case that covers itself, so selecting it narrows to that one distinction. */
    private static Refinement toLeaf(souther.compiler.types.TypeSymbol leaf) {
        return Refinement.of(souther.compiler.types.ResolvedCase.of(
                souther.compiler.types.CaseSelector.direct(leaf), java.util.List.of(leaf)));
    }

    /** The case's own name, taken off the reading that holds it. */
    private static souther.compiler.types.TypeSymbol caseNamed(InputDomain read, String name) {
        for (Case each : read.at(TermPath.of("b")).obligationCases()) {
            if (each instanceof Case.SumCase one && one.leaf().name().equals(name)) {
                return one.leaf();
            }
        }
        throw new IllegalStateException("no case named " + name);
    }

    /**
     * Two narrowings with no step into a value between them are both taken.
     *
     * <p>A narrowing costs no level, so the walk under one has to stop somewhere: it stops where it
     * returns to a value it has already been at without a step into one. What that is keyed on is
     * the value reached and never the narrowing taken — a narrowing is an edge and what has to
     * terminate is a state, and the two agree only where the edge decides the state.
     *
     * <p>Every case of a sum names the type it carries, so no model written in sums alone tells the
     * two apart. What this holds is the neighbouring fact: a second narrowing is reached at all.
     */
    @Test
    void twoNarrowingsWithNoStepBetweenThemAreBothTaken() {
        assertEquals(List.of("x", "x@Wrap", "x@Wrap@A", "x@Wrap@A.n", "x@Wrap@B", "x@Wrap@B.m",
                        "x@C", "x@C.k"),
                positionsOf("""
                        module g

                        data A = { n: Int }
                        data B = { m: Int }
                        data Inner = A | B
                        data Wrap = Inner
                        data C = { k: Int }
                        data Outer = Wrap | C
                        data Page = { n: Int }

                        behavior use : (x: Outer) -> Page
                        """, "use"));
    }

    /** And a path a recipe would have written flat is not one of these positions. */
    @Test
    void aFieldOfACaseIsNotAtThePathTheSumWouldGiveIt() {
        assertNull(reading(QUERIES, "readArticles").at(TermPath.of("query").then("limit")),
                "the declaration puts nothing at a name that names no case");
    }
}
