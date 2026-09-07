package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.query.Compilation;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Why a line went unread is the conjunct's answer, and not the one written beside it.
 *
 * <p>A rule is read a conjunct at a time. {@code x <= 10 * 2 && x <= y} has one conjunct that draws
 * a line this compiler could not fold and one that relates two positions and draws none, and both
 * are recorded at the same position of the same rule. Asked of the rule and the position, the answer
 * was whichever the walk wrote first — so the same model said two different things about why its
 * line went unread depending on the order its conjuncts are written in.
 *
 * <p>Which is the reason the accounting asks per conjunct everywhere else: the evidence is per
 * conjunct, so what it says is too.
 */
class AReasonBelongsToTheConjunctItCameFromTest {

    /** Which limit the reading of ends was stopped by, of the one clause written here. */
    private static String whyTheLineStands(String clause) {
        return standing(clause).why().getClass().getSimpleName();
    }

    /** The whole answer to the one boundary question the clause written here leaves standing. */
    private static FieldDomains.BoundaryStanding standing(String clause) {
        Compilation compilation = Compilation.ofSource("""
                module m

                data Pair = { x: Int, y: Int }
                    %s
                """.formatted(clause), "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        TypeSymbol.AtModule named = TypeSymbols.declared(new TypeKey(module, "Pair"));
        return FieldDomains.of(named, RuleReadings.of(compilation, module),
                souther.compiler.query.ReadAs.THE_COMPILATION_DOES).accounting().values().stream()
                .flatMap(each -> each.answers().entrySet().stream())
                .filter(e -> e.getKey().obligation() == CoverageObligation.BOUNDARY)
                .map(e -> assertInstanceOf(RuleAccounting.Outcome.Unaccounted.class, e.getValue()))
                .map(e -> assertInstanceOf(RuleAccounting.Why.TheEndReadingSays.class, e.why()))
                .map(RuleAccounting.Why.TheEndReadingSays::standing)
                .findFirst().orElseThrow(() -> new AssertionError("the line was answered"));
    }

    /** The parts of the clause standing behind that answer, by where each stands among them. */
    private static List<Integer> partsBehindTheLine(String clause) {
        return standing(clause).conjuncts().stream()
                .map(PartId<RuleRef.Invariant>::ordinal).toList();
    }

    /**
     * One model, two orders, one answer: the bound this could not fold is why.
     *
     * <p>One word and not a list of them. Which limit stopped the reading is read off the
     * coordinate, so every part of the rule raising this question comes to the same one — and a
     * reader is owed the limit rather than a tally of the parts that met it.
     */
    @Test
    void theOrderTheConjunctsAreWrittenInDoesNotDecideWhy() {
        assertEquals("UnreadComparisonForm", whyTheLineStands(
                "invariant said = x <= 10 * 2 && x <= y"));
        assertEquals("UnreadComparisonForm", whyTheLineStands(
                "invariant said = x <= y && x <= 10 * 2"),
                "and not the reason of the conjunct beside it, which relates two positions");
    }

    /**
     * And which parts are behind it is its own count.
     *
     * <p>Both conjuncts bound {@code x} and neither placed an end, so both are standing: a part
     * behind another is a second thing an author has to lift. Read off the reason instead, two
     * parts one limit stopped were one thing to do, and a rule half of which was read came out
     * looking like a rule none of which was.
     */
    @Test
    void everyPartStoppedBehindTheLineIsCounted() {
        assertEquals(List.of(0, 1),
                partsBehindTheLine("invariant said = x <= 10 * 2 && x <= 3 * 7"));
        assertEquals(List.of(0),
                partsBehindTheLine("invariant said = x <= 10 * 2 && x <= y"),
                "the conjunct that relates two positions raises no question about this line");
    }
}
