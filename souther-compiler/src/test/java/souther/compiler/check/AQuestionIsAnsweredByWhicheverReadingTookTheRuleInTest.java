package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.query.Compilation;
import souther.compiler.query.Scopes;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A question is answered by whichever reading took the rule in, and by no other test.
 *
 * <p>Which is what issue #842 is about, and the trap it sets. The reading that turns a clause into a
 * set of values has no word for a range, so it is short of the rules at every numeric position an
 * invariant bounds — and the report was printing that as the model having gone unread, two lines
 * above the boundary drawn from one of the very rules the sentence was about.
 *
 * <p>The trap is that there are more readings than the two a reader notices. {@code value * 2 >= 4}
 * is beyond the reading of ends and beyond the reading of values, and the reading that builds the
 * numeric constraints takes it in whole — so a model carrying it is fully read, and an accounting
 * that consulted two readings would report it exactly as #842 reports a bound. The table below is
 * what separates them, and every row of it was measured before it was written down.
 */
class AQuestionIsAnsweredByWhicheverReadingTookTheRuleInTest {

    /** One clause beside a bound, so that what changes is the clause and nothing else. */
    private static String beside(String clause) {
        return """
                module example.rooms

                data Length = Int
                    invariant floor = value >= 1
                    invariant said = %s
                """.formatted(clause);
    }

    private static Map<RuleRef.Invariant, RuleAccounting> accountingOf(String source, String type) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Symbols symbols = Scopes.derived(compilation.db(), module).value();
        assertNotNull(symbols);
        TypeSymbol.AtModule named = TypeSymbols.declared(new TypeKey(module, type));
        assertNotNull(symbols.declaredNode(named.key()), "no `" + type + "` declared");
        return FieldDomains.of(named, RuleReadings.of(compilation, module),
                souther.compiler.query.ReadAs.THE_COMPILATION_DOES).accounting();
    }

    /** What the author called the clause, of a rule that is a declaration's invariant. */
    private static java.util.Optional<String> nameOf(RuleRef rule) {
        return rule instanceof RuleRef.Invariant invariant
                ? invariant.clause().name().map(ClauseName::value) : java.util.Optional.empty();
    }

    private static RuleAccounting rule(Map<RuleRef.Invariant, RuleAccounting> accounting, String clause) {
        return accounting.entrySet().stream()
                .filter(e -> nameOf(e.getKey()).filter(clause::equals).isPresent())
                .map(Map.Entry::getValue).findFirst()
                .orElseThrow(() -> new AssertionError("no clause called `" + clause + "`; had "
                        + accounting.keySet()));
    }

    /** Whether anything took the clause written beside the bound in. */
    private static boolean answered(String clause) {
        return rule(accountingOf(beside(clause), "Length"), "said").unaccounted().isEmpty();
    }

    /**
     * A clause the reading of values takes in is answered, though it places no end.
     *
     * <p>{@code value /= 5} takes 5 out of what the position admits and draws no line. An accounting
     * that asked only the reading of ends would call it a rule this compiler could not read.
     */
    @Test
    void aDenialIsAnsweredThoughItPlacesNoEnd() {
        assertTrue(answered("value /= 5"), "the reading of values holds it");
    }

    /**
     * The clauses nothing takes in.
     *
     * <p>Neither moves the position's floor and neither narrows the values it admits, measured
     * through a record holding one: {@code Int.abs(value) >= 2} and {@code value * value >= 4} leave
     * the floor where {@code floor} put it. So nothing read them, which is the one thing a report
     * may say about a rule.
     */
    @Test
    void aClauseNothingTookInIsUnanswered() {
        assertEquals(List.of(false, false),
                List.of(answered("Int.abs(value) >= 2"), answered("value * value >= 4")),
                "a call and a product of the position with itself");
    }

    /**
     * A clause only the numeric constraints hold is answered, and its near neighbour is not.
     *
     * <p>The pair the accounting has to get right, and the reason it cannot be got right from the
     * clause's spelling. {@code value * 2 >= 4} and {@code value * value >= 4} are one shape — a
     * comparison of a product against a number — and the first moves the position's floor to 2 while
     * the second leaves it where {@code floor} put it. What separates them is what the reading
     * produced: a form over the position's own atom, or one over an atom standing for the whole
     * product, which is not the position.
     *
     * <p>Both halves are asserted, the accounting's answer and the measurement it has to agree with,
     * so that a change moving one without the other fails here.
     */
    @Test
    void aClauseOnlyTheConstraintsHoldIsAnswered() {
        assertEquals("2", floorOfAFieldOf("value * 2 >= 4"),
                "the constraints took it in: the floor moved from 1 to 2");
        assertTrue(answered("value * 2 >= 4"), "so the question it raises is answered");

        assertEquals("1", floorOfAFieldOf("value * value >= 4"),
                "and left this one where `floor` put it");
        assertFalse(answered("value * value >= 4"), "so its question stands");
    }

    /** Where a field of type {@code Length} stops from below, which is where the clauses reaching it
     * leave it. Read through a record, since a newtype's own value is at no path of its own. */
    private static String floorOfAFieldOf(String clause) {
        String source = beside(clause) + "\ndata Holder = { len: Length }\n";
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        TypeSymbol.AtModule holder = TypeSymbols.declared(new TypeKey(module, "Holder"));
        return FieldDomains.of(holder, RuleReadings.of(compilation, module),
                souther.compiler.query.ReadAs.THE_COMPILATION_DOES)
                .at(RuleKey.of("len")).bounds().min().at().toString();
    }

    /**
     * One clause's failure is not the account of the clause beside it.
     *
     * <p>The granularity of a question is the clause. Asked per position, the reading of values
     * being short because of {@code floor} made {@code seven} — which it took in whole, down to the
     * single value — a rule nothing had read.
     */
    @Test
    void aFailureAtAPositionIsNotTheAccountOfTheClausesBesideIt() {
        Map<RuleRef.Invariant, RuleAccounting> accounting = accountingOf("""
                module example.rooms

                data Length = Int
                    invariant floor = value >= 1
                    invariant seven = value == 7
                """, "Length");

        assertEquals(Set.of(), rule(accounting, "seven").unaccounted(),
                "the reading of values took `value == 7` in, whatever it made of `value >= 1`");
        assertEquals(Set.of(), rule(accounting, "floor").unaccounted(),
                "and the bound is answered by the reading that placed its end");
    }

    /**
     * A bound's two questions are about two subjects, and both are answered.
     *
     * <p>A length bound says which strings may stand at the position and draws a line on the count.
     * Held under one subject, a report names the count where it means the string — which is the
     * other half of what #842 found.
     */
    @Test
    void aBoundOnACountRaisesTwoQuestionsAboutTwoSubjects() {
        RuleAccounting nonEmpty = rule(accountingOf("""
                module example.rooms

                data Code = String
                    invariant nonEmpty = String.length(value) >= 1
                """, "Code"), "nonEmpty");

        assertEquals(Set.of("ADMITTED_VALUES at the value",
                        "BOUNDARY at String.length(the value)"),
                nonEmpty.answers().keySet().stream()
                        .map(o -> o.obligation() + " at " + o)
                        .collect(java.util.stream.Collectors.toSet()));
        assertEquals(Set.of(), nonEmpty.unaccounted());
    }

    /**
     * A clause read whole that narrows nothing is read.
     *
     * <p>What a reading adopted and what it ends up leaving a position are different facts.
     * {@code value == 5 || value /= 5} is read at both leaves and joins to every value there is;
     * {@code value >= 1 || value <= 0} is read at both and leaves the whole order. Adoption taken
     * from what the reading left, both come back as rules nothing read — which is #842's mistake
     * again, with the lattice value standing in for the reading's own account. The readings say
     * where they took a leaf in, at the point they take it.
     */
    @Test
    void aClauseThatNarrowsNothingIsStillRead() {
        assertTrue(answered("value == 5 || value /= 5"),
                "read at both leaves, and every value is left");
        assertTrue(answered("value >= 1 || value <= 0"),
                "and the same of an order neither half narrows");
    }

    /**
     * A position the alternatives leave differently is one the choice stands open at.
     *
     * <p>{@code x == 7 || f(y)} says nothing about {@code x}, and what says so is what the two
     * branches leave: the branch nothing could read leaves {@code x} at every value, the choice
     * leaves it there too, and dropping the unread branch would not. So the choice is open at
     * {@code x} and the clause is not one that was read at it.
     *
     * <p><b>Not because a branch went unread.</b> Two alternatives holding a position to the same
     * values hold it there whether or not either could be read to the end, and an account that
     * answered this from its own flag for an unread clause would say they do not
     * ({@code WhetherAConstraintStillBindsIsReadOffWhatTheAlternativesLeaveTest}). The flag says
     * which alternative to ask about; what the alternatives leave says whether it matters, and the
     * two are settled in different places ({@code Settlement.WidthDependency}).
     *
     * <p>Which makes a choice and a conjunction two operations rather than one. Under
     * {@code x >= 1 && f(y)} the bound on {@code x} still holds, because all of it holds.
     */
    @Test
    void aPositionTheAlternativesLeaveDifferentlyIsOneTheChoiceStandsOpenAt() {
        assertEquals(Set.of("x", "y"), unansweredAbout("x == 7 || Int.abs(y) >= 2"),
                "neither position is one this clause was read at");
        assertEquals(Set.of(), unansweredAbout("x == 7 || y == 2"),
                "and a choice both branches were read at leaves nothing standing");
        assertEquals(Set.of("y"), unansweredAbout("x >= 1 && Int.abs(y) >= 2"),
                "while a conjunct nothing read leaves the one beside it saying what it said");
    }

    /**
     * And a position they leave alike is one it does not, however little of the clause was read.
     *
     * <p>The other side of the rule above, and the one an account answering from its own flag gets
     * wrong. Both alternatives hold {@code x} to the same values; the clause standing beside that
     * constraint in each of them is one no reading has a word for, and the position is held there
     * all the same.
     */
    @Test
    void andAPositionTheyLeaveAlikeIsOneItDoesNot() {
        assertEquals(Set.of(), unansweredAbout("x >= 1 || x >= 1"),
                "the alternatives leave the position where the other does, so nothing stands open");
        assertEquals(Set.of("y"), unansweredAbout(
                        "(x >= 1 && Int.abs(y) >= 2) || (x >= 1 && Int.abs(y) >= 2)"),
                "and the same beside a clause nothing reads, which stands open at its own position"
                        + " and takes nothing back at the one held down");
    }

    /**
     * A branch shown to admit nothing settles the positions it named.
     *
     * <p>The other side of the rule above, and not a special case of it. {@code s < ""} admits
     * nothing — no string is below the empty one, which the reading of order has whole — so the
     * choice is the branch beside it, and what this clause does to {@code s} is nothing at all.
     * That is an answer, and one only a reading that got to the end of the branch could give;
     * dropping the dead branch's evidence with its state turned it into a rule nothing had read.
     *
     * <p>What the surviving branch missed still outranks it. A dead branch beside one nothing could
     * read leaves that branch's positions open, however dead the first was.
     */
    @Test
    void aBranchThatAdmitsNothingSettlesWhatItNamed() {
        assertEquals(Set.of(), unansweredOf("""
                module example.pair

                data R = { s: String, b: Bool }
                    invariant said = s < "" || b == true
                """, "R"));

        assertEquals(Set.of(), unansweredOf("""
                module example.pair

                data R = { s: String, b: Bool }
                    invariant said = s < "" || (b == true && b == false)
                """, "R"),
                "and where every branch admits nothing, so does the choice — at every position"
                        + " either of them named");

        assertEquals(Set.of("x"), unansweredOf("""
                module example.pair

                data R = { s: String, x: Int }
                    invariant said = s < "" || Int.abs(x) >= 2
                """, "R"),
                "while the branch nothing could read leaves its own positions open");
    }

    /** The positions of {@code type} that the clauses written over it were not read at. */
    private static Set<String> unansweredOf(String source, String type) {
        return accountingOf(source, type).values().stream()
                .flatMap(each -> each.unaccounted().stream())
                .map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());
    }

    /** The positions of a two-field record that the clause written over them was not read at. */
    private static Set<String> unansweredAbout(String clause) {
        return accountingOf("""
                module example.pair

                data Pair = { x: Int, y: Int }
                    invariant said = %s
                """.formatted(clause), "Pair").values().stream()
                .flatMap(each -> each.unaccounted().stream())
                .map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * An alternative nothing read leaves the clause unread, and the connective does not decide it.
     *
     * <p>{@code value == 7 || Int.abs(value) >= 2} is read on the left and not on the right, and a
     * position the clause was read at on one branch is not a position the clause was read at. The
     * conjunction was already right — a clause is taken apart a conjunct at a time — so a set of the
     * branches that succeeded made correctness turn on which connective was written.
     *
     * <p>The two languages are two accounts, and either will do. {@code value == 7 || value >= 5} is
     * a comparison the reading of values has no word for beside one it does, and the reading of
     * order has both — so the clause is read, and asking for one language to have all of it would
     * report a model that was read.
     */
    @Test
    void anAlternativeNothingReadLeavesTheClauseUnread() {
        assertFalse(answered("value == 7 || Int.abs(value) >= 2"),
                "read on one branch is not read");
        assertTrue(answered("value == 7 || value >= 5"),
                "and one language having the whole of it is enough");
    }

    /**
     * A clause half of which nothing read is a clause nothing read.
     *
     * <p>A conjunction is one rule the author wrote and is read a conjunct at a time, so the
     * questions its parts raise about one subject are one question. Answered on the strength of the
     * part that was read, `value >= 1 && Int.abs(value) >= 2` came back with nothing to say while
     * the same two rules written apart were reported — which is what #842 is about, one level down:
     * a part of the clause standing for the whole of it.
     *
     * <p>The line the first half draws is owed a row all the same. What the two halves settle are
     * different questions, and only one of them is left standing.
     */
    @Test
    void aClauseHalfOfWhichNothingReadIsUnanswered() {
        assertFalse(answered("value >= 1 && Int.abs(value) >= 2"),
                "a bound beside a call nothing reads");
        assertFalse(answered("value == 7 && Int.abs(value) >= 2"),
                "and the values named beside it do not answer for it either");
        assertTrue(answered("value >= 1 && value <= 100"),
                "while a conjunction both halves of which were read is answered");

        assertEquals(Set.of("BOUNDARY at the value"),
                rule(accountingOf(beside("value >= 1 && Int.abs(value) >= 2"), "Length"), "said")
                        .answers().entrySet().stream()
                        .filter(e -> e.getValue() instanceof RuleAccounting.Outcome.Accounted)
                        .map(e -> e.getKey().obligation() + " at " + e.getKey())
                        .collect(java.util.stream.Collectors.toSet()),
                "the line the readable half drew is answered by the reading that placed it");
    }

    /**
     * A clause can be answered without the bounds being able to state it.
     *
     * <p>The short circuit this is here to stop. Whether a rule was answered is about the model —
     * some reading took it in — and whether the bounds state it is about the projection that was
     * made; {@code value == 3 || value == 5} is held whole by the reading of values and reaches the
     * interval algebra as a fact keyed on the comparison, so the bounds are {@code [3, 5]} and the 4
     * between them is a row nobody can write. Reaching for one to say the other reports a rule as
     * unread on the strength of a question nobody asked, or promises a row at an edge on the
     * strength of a reading that was never about edges.
     */
    @Test
    void aClauseCanBeAnsweredWithoutTheBoundsStatingIt() {
        Compilation compilation = Compilation.ofSource(beside("value == 3 || value == 5"), "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        TypeSymbol.AtModule named = TypeSymbols.declared(new TypeKey(module, "Length"));
        FieldDomains read = FieldDomains.of(named, RuleReadings.of(compilation, module),
                souther.compiler.query.ReadAs.THE_COMPILATION_DOES);

        assertFalse(read.projection().isCertified(), "the bounds hold no hole");
        assertEquals(Set.of(), rule(read.accounting(), "said").unaccounted(),
                "and it was read all the same");
    }
}
