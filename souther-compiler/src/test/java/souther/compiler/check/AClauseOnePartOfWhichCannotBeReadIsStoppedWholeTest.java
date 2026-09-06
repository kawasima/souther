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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * A clause one part of which this reading cannot read leaves nothing, including for the parts it
 * could have read.
 *
 * <p>The clause is typed and read as a clause, so what a limit stops is the clause and not the
 * conjunct the limit was met in. A conjunct written beside one holding a call the expansion left
 * standing therefore draws no line either, and the position is left as wide as the whole clause
 * being unread leaves it.
 *
 * <p>Held here so that reading a clause a part at a time does not quietly change it. Preparing the
 * parts apart and answering for the clause whole are two decisions, and only the first is what
 * telling a clause's authored parts from its semantic shape is about.
 */
class AClauseOnePartOfWhichCannotBeReadIsStoppedWholeTest {

    /** A helper this compiler leaves standing, so a conjunct naming it is one the reading stops on. */
    private static final String STANDING =
            "partial let step (n: Int): Int = if n <= 0 then n - 1 else step(n - 1)";

    private static FieldDomains read(String helpers, String clause) {
        return of("""
                module demo

                %s

                data N = Int
                    invariant %s
                """.formatted(helpers, clause));
    }

    /** The same declaration with no rule written on it at all, which is what a clause going unread
     *  leaves the position with. */
    private static FieldDomains readNoRule(String helpers) {
        return of("""
                module demo

                %s

                data N = Int
                """.formatted(helpers));
    }

    private static FieldDomains of(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        Symbols symbols = Scopes.derived(compilation.db(), "demo").value();
        TypeSymbol.AtModule name = TypeSymbols.declared(new TypeKey(symbols.module(), "N"));
        return FieldDomains.of(name, RuleReadings.of(compilation, "demo"),
                ReadAs.THE_COMPILATION_DOES);
    }

    /** The part that could be read draws no line when the part beside it stopped the clause. */
    @Test
    void thePartThatCouldBeReadDrawsNoLineEither() {
        assertEquals(List.of(1, 1, 0), List.of(
                        read("", "value >= 1").placed().size(),
                        read(STANDING, "value >= 1").placed().size(),
                        read(STANDING, "value >= 1 && step(value) >= 0").placed().size()),
                "the rule alone, the rule beside a helper nothing names, and the rule written"
                        + " beside a part this reading stops on");
    }

    /**
     * And the position is left as wide as it is with no rule written on it, said as a rule that
     * went unread rather than as nothing to read.
     *
     * <p>Which is the whole of what such a stop leaves anybody: the same values, and an account of
     * why the answer is no narrower. Read as the same answer, a declaration whose rule this could
     * not read would be one that wrote no rule.
     */
    @Test
    void thePositionIsLeftAsWideAsTheWholeClauseGoingUnread() {
        assertEquals(
                "AdmissibleSet[approximation=Cofinite[excluded=[]], completeness=Complete[]]",
                String.valueOf(readNoRule(STANDING).admits(RuleKey.THE_VALUE)),
                "what the position admits where no rule was written");
        assertEquals(
                "AdmissibleSet[approximation=Cofinite[excluded=[]],"
                        + " completeness=Wider[why=[RuleUnread[why=NOT_REACHED]]]]",
                String.valueOf(read(STANDING, "value >= 1 && step(value) >= 0")
                        .admits(RuleKey.THE_VALUE)),
                "and where a clause one part of which could not be read stopped");
    }

    /**
     * And the stop is recorded once for the clause, however many parts it was written in.
     *
     * <p>Preparing the parts and answering for the clause are two things, and only the second is
     * what a reader sees. A clause read a part at a time and recorded a part at a time would say
     * this reading met its limit twice where an author wrote one rule it could not read.
     *
     * <p>Held as the two counts being one number rather than as the number. How many times a
     * declaration is read at all is the compilation's business and is not what this is about; that
     * a clause of two parts stops as often as a clause of one is.
     */
    @Test
    void theStopIsRecordedAsOftenForAClauseOfTwoPartsAsForOne() {
        int one = stopsIn(() -> read(STANDING, "step(value) >= 0"));
        int two = stopsIn(() -> read(STANDING, "value >= 1 && step(value) >= 0"));

        assertNotEquals(0, one, "no limit was recorded at all, so nothing here is being compared");
        assertEquals(one, two,
                "how many limits were recorded, for a clause of one part and for one of two");
    }

    /** How many limits the reading of {@code domains} met, as the check records them. */
    private static int stopsIn(java.util.function.Supplier<FieldDomains> domains) {
        List<InvariantChecker.GaveUp> met = new java.util.ArrayList<>();
        InvariantChecker.GAVE_UP = met;
        try {
            domains.get();
        } finally {
            InvariantChecker.GAVE_UP = null;
        }
        return met.size();
    }

    /** And what the clause is recorded as having raised and answered. */
    @Test
    void whatTheStoppedClauseIsRecordedAsRaising() {
        FieldDomains stopped = read(STANDING, "value >= 1 && step(value) >= 0");

        assertEquals(List.of(0, 0), List.of(
                        stopped.required().size(), stopped.accounting().size()),
                "how many rules the reading raised a question for, and wrote an account of");
        assertEquals(List.of(1, 1), List.of(
                        read(STANDING, "value >= 1").required().size(),
                        read(STANDING, "value >= 1").accounting().size()),
                "and the same for a clause of one part this reading does read");
    }
}
