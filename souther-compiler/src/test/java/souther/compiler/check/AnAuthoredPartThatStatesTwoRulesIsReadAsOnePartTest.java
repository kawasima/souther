package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;
import souther.compiler.query.Scopes;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What one authored part that states two rules leaves, as every reader of it answers today.
 *
 * <p>The conjuncts of a clause are what its author wrote, and a rule named through a helper is one
 * of them however many rules its body joins. The reading that draws lines crosses the binding and
 * arrives at a conjunction, where the reader below wants one part — so it draws neither line, while
 * the reading that says what the position admits reads both rules and narrows by both.
 *
 * <p>Held as the answers themselves and not as a verdict. Which of these change is the whole of what
 * telling a clause's semantic decomposition from its authored-part decomposition is worth, and a
 * reading that came to the same verdict by another route would hide it.
 */
class AnAuthoredPartThatStatesTwoRulesIsReadAsOnePartTest {

    /** The rule written out as two conjuncts, and the same rules named as one. */
    private static final String WRITTEN_OUT = "value >= 1 && value <= 9";
    private static final String NAMED = "inRange(value)";
    private static final String HELPER = "let inRange (n: Int) = n >= 1 && n <= 9";

    private static FieldDomains read(String helpers, String clause) {
        String source = """
                module demo

                %s

                data N = Int
                    invariant %s
                """.formatted(helpers, clause);
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        assertEquals(List.of(), compilation.diagnostics().values().stream()
                        .flatMap(List::stream).map(each -> each.diagnostic().code()).toList(),
                "the model this reads has to be one somebody could write");
        Symbols symbols = Scopes.derived(compilation.db(), "demo").value();
        TypeSymbol.AtModule name = TypeSymbols.declared(new TypeKey(symbols.module(), "N"));
        return FieldDomains.of(name, RuleReadings.of(compilation, "demo"),
                ReadAs.THE_COMPILATION_DOES);
    }

    private static FieldDomains writtenOut() {
        return read("", WRITTEN_OUT);
    }

    private static FieldDomains named() {
        return read(HELPER, NAMED);
    }

    /** What the position admits: both rules narrow it, whichever way they were written. */
    @Test
    void thePositionIsNarrowedByBothRulesEitherWay() {
        assertEquals(String.valueOf(writtenOut().admits(RuleKey.THE_VALUE)),
                String.valueOf(named().admits(RuleKey.THE_VALUE)),
                "what the values reading leaves the position");
    }

    /** And the lines: an end each where the rules were written out, and neither where they were
     *  named — the reading arrives at a conjunction where one part is wanted. */
    @Test
    void theLinesAreDrawnWhereTheRulesWereWrittenOutAndNotWhereTheyWereNamed() {
        assertEquals(List.of(2, 0), List.of(
                        writtenOut().placed().size(), named().placed().size()),
                "how many ends each spelling places");
    }

    /**
     * Which conjunct each end is named by, which is what a report prints and what a reader holding
     * a line against the clause it came from matches on.
     *
     * <p>Written out, the two ends are the two conjuncts the author wrote. Named, there are no ends
     * to number.
     */
    @Test
    void eachEndIsNamedByTheConjunctItsAuthorWrote() {
        assertEquals(List.of(0, 1), writtenOut().placed().stream()
                        .map(each -> each.part().ordinal()).sorted().toList(),
                "which conjunct each end written out is named by");
        assertEquals(List.of(), named().placed().stream()
                        .map(each -> each.part().ordinal()).toList(),
                "and the same where the rules were named");
    }

    /**
     * Which ends the two rules place, as the ends themselves.
     *
     * <p>A count says two ends were placed and not which two, so a reading that came to the same
     * number by another route would answer alike. What the rules state is one end at each side of
     * the run, and that is what the named spelling has to come to as well if it is the same rules
     * being read.
     */
    @Test
    void theEndsTheTwoRulesPlaceAreTheSidesOfTheRunTheyState() {
        assertEquals(List.of("true Endpoint[at=1, inclusive=true]",
                        "false Endpoint[at=9, inclusive=true]"), writtenOut().placed().stream()
                        .map(each -> each.lower() + " " + each.end()).toList(),
                "each end written out, as the side it is on and where it sits");
        assertEquals(List.of(), named().placed().stream()
                        .map(each -> each.lower() + " " + each.end()).toList(),
                "and the same where the rules were named");
    }

    /**
     * And the part that placed neither end is not recorded as having placed none.
     *
     * <p>What such a record is for is a sentence to an author about a conjunct that drew no line,
     * and the reading never reached a conjunct to write one about. So the named spelling leaves
     * nothing here as well as no line — the difference between the two spellings is silence and not
     * a report of it.
     */
    @Test
    void thePartThatPlacedNoEndIsNotRecordedAsHavingPlacedNone() {
        assertEquals(List.of(0, 0), List.of(
                        writtenOut().withoutAnEnd().size(), named().withoutAnEnd().size()),
                "how many parts placed no end, written out and named");
    }

    /**
     * How many records a part that placed no end leaves, which is what a reader keying by the part
     * collapses.
     *
     * <p>A conjunct that tells one value from the rest is about the number and places no end on it,
     * so it is one of these; the reader that hands them on keeps one per conjunct
     * ({@code PlacedRules.clausesWithoutAnEnd}). What that collapse is worth depends on whether one
     * conjunct leaves more than one, and today it leaves one.
     */
    @Test
    void aPartThatPlacedNoEndLeavesOneRecordPerConjunct() {
        List<FieldDomains.WithoutAnEnd> left = read("", "value >= 1 && value /= 5").withoutAnEnd();

        assertEquals(List.of(1),
                left.stream().map(each -> each.part().ordinal()).toList(),
                "which conjunct each record is of");
        assertEquals(1, left.stream().map(FieldDomains.WithoutAnEnd::part).distinct().count(),
                "how many conjuncts the records are spread over");
    }

    /** What is left of the other readings of the same clause. */
    @Test
    void whatTheOtherReadingsOfTheClauseLeave() {
        assertEquals(List.of(2, 0), List.of(
                        writtenOut().aboutOneCoordinate().size(),
                        named().aboutOneCoordinate().size()),
                "how many parts are read as being about one number");
        assertEquals(List.of(0, 0), List.of(
                        writtenOut().noLines().size(), named().noLines().size()),
                "how many parts left no line at a number they are about");
        assertEquals(List.of(0, 0), List.of(
                        writtenOut().movedEnds().size(), named().movedEnds().size()),
                "how many ends a counterfactual reading found another part holding");
    }

    /**
     * And what the clause raises is one question per authored part, which is where the two
     * spellings part.
     *
     * <p>Written out, each conjunct raises its own; named, the one part raises one. The questions
     * are the same question about the same position, so what differs is how many of them there are
     * to answer and not what any of them asks.
     */
    @Test
    void whatTheClauseRaisesIsOneQuestionPerAuthoredPart() {
        assertEquals(List.of(2, 1), List.of(
                        writtenOut().required().values().stream()
                                .mapToInt(each -> each.obligations().size()).sum(),
                        named().required().values().stream()
                                .mapToInt(each -> each.obligations().size()).sum()),
                "how many questions the clause raises of the position");
    }
}
