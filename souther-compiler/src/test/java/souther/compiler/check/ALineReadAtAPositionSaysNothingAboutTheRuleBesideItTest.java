package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.inputs.BlockReason;
import souther.compiler.query.Compilation;
import souther.compiler.query.Scopes;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A rule saying where a position's values stop that no end came out of, named at every position it
 * is about.
 *
 * <p>The invariant's half of what a {@code guard}'s comparison already answers (ADR-0090). Both
 * draw lines, both can be written in a form this compiler does not read, and a reader is told the
 * same thing about either — so an invariant's clause that placed no end has to be said, and said
 * once.
 *
 * <p>What made it silent was that a position carries more than one statement. The account was kept
 * as what the position was left with if nothing divided it, so a bound on a field's own type
 * answered for the record's clause about that same field and the clause was dropped without a word:
 * two declarations differing by one {@code invariant value >= 0} said opposite things about the
 * clause above them. That is what the pair below is for — the same relation, once where the fields'
 * types draw a line and once where they do not — and it is the pair, not either half, that holds
 * the fix.
 */
class ALineReadAtAPositionSaysNothingAboutTheRuleBesideItTest {

    private static FieldDomains readingOf(String source, String type) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Symbols symbols = Scopes.derived(compilation.db(), module).value();
        assertNotNull(symbols, "the model under test compiles");
        TypeSymbol.AtModule named = TypeSymbols.declared(new TypeKey(module, type));
        assertNotNull(symbols.declaredNode(named.key()), "no `" + type + "` declared");
        return FieldDomains.of(named, RuleReadings.of(compilation, module),
                souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
    }

    private static final String MEASURED = """
            module example.parcels

            data Cm = Int
                invariant value >= 0

            data Parcel = { length: Cm, width: Cm }
                invariant Int.add(length.value, width.value) <= 150
            """;

    private static final String BARE = """
            module example.parcels

            data Parcel = { length: Int, width: Int }
                invariant Int.add(length, width) <= 150
            """;

    /**
     * A clause comparing an arithmetic form over two fields, where nothing else divides them.
     *
     * <p>The half that always worked, here so that the half below cannot pass by accident: what
     * separates the two is one {@code invariant value >= 0} on the fields' own type, which is not a
     * fact about this clause.
     *
     * <p>What it is named as is that it relates the two rather than dividing either. A form over two
     * fields says nothing about where either of them stops on its own, which is the same thing
     * {@code length < width} says and is said in the same words — counted a side at a time, a rule
     * naming both of its positions on one side came back as a form nobody could read.
     */
    @Test
    void aClauseNoEndCameOutOfIsNamedAtEveryFieldItCompares() {
        FieldDomains read = readingOf(BARE, "Parcel");

        assertEquals(List.of(new BlockReason.ComparisonBetweenPositions()),
                reasonsAt(read, "length"));
        assertEquals(List.of(new BlockReason.ComparisonBetweenPositions()),
                reasonsAt(read, "width"));
    }

    /**
     * And says so where the fields' own type has already drawn a line through them.
     *
     * <p>Issue #868. {@code Cm}'s own bound places an end at each field, and the record's clause
     * about the pair is a second statement about the same positions — so the report owed both, and
     * said only the first while the {@code guard} of the same shape two declarations away named
     * both of the positions it compared.
     */
    @Test
    void andSaysSoWhereTheFieldsOwnTypeAlreadyDrewALine() {
        FieldDomains read = readingOf(MEASURED, "Parcel");

        assertFalse(read.placedAt(RuleKey.of("length")).isEmpty(), "`Cm` places an end here");
        assertEquals(List.of(new BlockReason.ComparisonBetweenPositions()),
                reasonsAt(read, "length"));
        assertEquals(List.of(new BlockReason.ComparisonBetweenPositions()),
                reasonsAt(read, "width"));
    }

    /**
     * A rule an end did come out of is named no way.
     *
     * <p>The reading that draws lines is what answers, and it read this one: a report naming it
     * would send an author after a boundary it is about to print two lines below.
     */
    @Test
    void anEndThisReadIsNotNamed() {
        FieldDomains read = readingOf("""
                module example.parcels

                data Cm = Int
                    invariant value >= 0

                data Parcel = { length: Cm, width: Cm }
                    invariant length.value <= 150
                """, "Parcel");

        assertFalse(read.placedAt(RuleKey.of("length")).isEmpty());
        assertEquals(List.of(), read.noLineAt(RuleKey.of("length")));
        assertEquals(List.of(), read.noLineAt(RuleKey.of("width")));
    }

    /**
     * A rule of another shape places no end, and what it did instead is what it is named for.
     *
     * <p>A denial says which values exist rather than where they stop, so a report has nowhere to
     * put it as a line and an author named one would be sent after a boundary nobody wrote. What it
     * does do is hold the position to what it admits — {@code length} is every whole number but
     * five, and five cannot be built there — and that is a fact a reader acts on. Said by nothing,
     * the position came back measured at nothing with no word for why.
     *
     * <p>Which values those are is not read here. The reading that turns clauses into sets answers
     * it, and this is only asked whether what it left is narrower than everything — so a denial and
     * a format arrive alike, as they do at every reader below.
     */
    @Test
    void aRuleThatIsNotAboutWhereTheValuesStopIsNamedForWhatItRestricts() {
        FieldDomains read = readingOf("""
                module example.parcels

                data Parcel = { label: String, length: Int }
                    invariant String.matches(label, "[A-Z]+")
                    invariant length /= 5
                """, "Parcel");

        assertTrue(read.placedAt(RuleKey.of("length")).isEmpty(),
                "a denial is no line, which is what a report would send an author after");
        assertEquals(List.of(new BlockReason.RuleRestrictingToAdmittedValues()),
                reasonsAt(read, "length"));
    }

    /**
     * A branch this rule's own clauses rule out is this rule's work, and counts as what it did.
     *
     * <p>Nothing is both {@code "A"} and {@code "B"}, so the branch asking for both is one nobody
     * can take — and the rule is what is left of it, which holds {@code s} to one string. Which
     * branch that is cannot be read off the clause: two languages are a machine, and whether their
     * meet holds anything is not known until the machine exists.
     *
     * <p>The other half of the rule beside it. There a branch stands until a neighbouring rule
     * refuses it, and crediting this rule with that is how one that states nothing was reported as
     * holding a position down; here the refusal is the rule's own, and dropping it loses a
     * restriction the model does state.
     */
    @Test
    void aBranchThisRulesOwnClausesRuleOutIsPartOfWhatItRestricts() {
        FieldDomains read = readingOf("""
                module example.parcels

                data Code = { t: String, s: String }
                    invariant r = (String.matches("A", t) && String.matches("B", t))
                        || String.matches("C", s)
                """, "Code");

        assertEquals(List.of(new BlockReason.RuleRestrictingToAdmittedValues()),
                reasonsAt(read, "s"),
                () -> "the branch that survives holds `s` to one string: "
                        + read.noLineAt(RuleKey.of("s")));
    }

    /**
     * And a branch its own clauses rule out by where the values stop counts the same way.
     *
     * <p>Whether anybody can be in a branch is asked of both languages, because each is short of
     * what the other holds. Here the rule's own conjunct puts {@code n} above one and the branch
     * asks for it below nothing, so no order admits it — and the branch that stands holds {@code s}
     * to one string. Asked of the values alone, nothing is wrong with a branch that says nothing
     * about values, and the restriction the rule does state is lost.
     *
     * <p>The pair with the branch ruled out by values above. One drop is a set with nothing in it
     * and the other is an order with nothing between its ends, and a fold that reads one language
     * finds only the branches that language could refuse.
     */
    @Test
    void aBranchItsOwnClausesRuleOutByAnOrderCountsAlike() {
        FieldDomains read = readingOf("""
                module example.parcels

                data Code = { n: Int, s: String }
                    invariant r = (n < 0 || String.matches("C", s)) && n > 1
                """, "Code");

        assertEquals(List.of(new BlockReason.RuleRestrictingToAdmittedValues()),
                reasonsAt(read, "s"),
                () -> "no order admits the first branch, so the second holds `s` to one string: "
                        + read.noLineAt(RuleKey.of("s")));
    }

    /**
     * A conjunct whose surviving branch is about another position does not hold this one down.
     *
     * <p>What a reading settled and what it constrained are two answers, and only the second is a
     * rule binding a value. No string is both {@code "A"} and {@code "B"}, so the branch asking for
     * both is one nothing satisfies — and a dead branch settles every position it named by imposing
     * nothing on it, which the account records as settled rather than as read. What is left of that
     * conjunct is a rule about {@code b}, and taken for a constraint on {@code a} it is filed as
     * holding {@code a} to what it admits: an author is sent to a clause about another field, and
     * to a branch that is not there.
     *
     * <p>The pair of fields is what makes the difference visible. Where the surviving branch names
     * the same position, the account reads it there anyway and the two answers agree; the one that
     * is settled and not read is a position only the dead branch spoke of.
     *
     * <p>Asked of the reading and not of a report. Two rules with no line at one position come out
     * as one finding, so a report shows one entry whichever answer the account gave.
     */
    @Test
    void aConjunctSettledByADeadBranchIsNotOneThatRestricts() {
        FieldDomains read = readingOf("""
                module example.parcels

                data Code = { a: String, b: Int }
                    invariant r = ((String.matches("A", a) && String.matches("B", a)) || b == 1)
                        && String.matches("T[0-9]{3}", a)
                """, "Code");

        assertEquals(List.of(new BlockReason.RuleRestrictingToAdmittedValues()),
                reasonsAt(read, "a"),
                () -> "the format holds `a` to what it admits and the conjunct beside it does not: "
                        + read.noLineAt(RuleKey.of("a")));
    }

    /**
     * A conjunct whose two bounds reach opposite ends of the order draws no line, and what it does
     * restrict is said.
     *
     * <p>Each alternative stops {@code n} and the choice between them stops it nowhere: what they
     * leave between them is every {@code Int}. So no line is drawn here — and the rule does hold
     * the position to what it admits, since neither one nor five can stand at it.
     *
     * <p>Asked which positions some rule of the conjunct bounded, this comes back as a part with a
     * line at {@code n}, accounted for by whoever draws lines. Nobody draws it, so the restriction
     * the rule states went out unsaid and the position came back measured at nothing with no word
     * for why.
     *
     * <p>The control beside it is the same rule with the two bounds reaching the same way, where
     * the choice does stop {@code n} and the line is drawn.
     */
    @Test
    void aConjunctWhoseBoundsCoverTheOrderIsNamedForWhatItRestricts() {
        FieldDomains covering = readingOf("""
                module example.parcels

                data Code = { n: Int }
                    invariant r = (n >= 2 && n /= 5) || (n <= 0 && n /= 5)
                """, "Code");
        FieldDomains reaching = readingOf("""
                module example.parcels

                data Code = { n: Int }
                    invariant r = (n >= 2 && n /= 5) || (n >= 0 && n /= 5)
                """, "Code");

        assertTrue(covering.placedAt(RuleKey.of("n")).isEmpty(),
                "the alternatives leave every value between them, so there is no line");
        assertEquals(List.of(new BlockReason.RuleRestrictingToAdmittedValues()),
                reasonsAt(covering, "n"),
                () -> "and neither one nor five can stand there: "
                        + covering.noLineAt(RuleKey.of("n")));
        assertFalse(reaching.placedAt(RuleKey.of("n")).isEmpty(),
                "the control stops at zero, and a line there is drawn");
        assertEquals(List.of(), reasonsAt(reaching, "n"),
                "so nothing is owed a second sentence about it");
    }

    /**
     * An equality least of all, though it reaches this reading as a comparison that placed no end.
     *
     * <p>It names a value rather than an end, and the reading of values holds it. Read off "no end
     * came out of it", every {@code == 5} in every model would be a line somebody was told to go
     * looking for — which is the failure this whole account is the other side of.
     */
    @Test
    void anEqualityIsNotNamedThoughNoEndCameOutOfIt() {
        FieldDomains read = readingOf("""
                module example.parcels

                data Parcel = { length: Int }
                    invariant length == 5
                """, "Parcel");

        assertEquals(List.of(), read.noLineAt(RuleKey.of("length")));
    }

    /**
     * A clause relating two fields says that it relates them, at both.
     *
     * <p>Which is a different thing for a reader to do about it: nothing is missing from the
     * carrier — a line drawn on either field against a number would be read — and what a partition
     * of one position is not is a class about two.
     */
    @Test
    void aClauseRelatingTwoFieldsSaysThatItRelatesThem() {
        FieldDomains read = readingOf("""
                module example.spans

                data Bound = Int
                    invariant value >= 0

                data Span = { low: Bound, high: Bound }
                    invariant low < high
                """, "Span");

        assertEquals(List.of(new BlockReason.ComparisonBetweenPositions()), reasonsAt(read, "low"));
        assertEquals(List.of(new BlockReason.ComparisonBetweenPositions()), reasonsAt(read, "high"));
    }

    /**
     * And a clause naming one coordinate on each side is one of those, however the sides are
     * written.
     *
     * <p>The form between the two above: {@code x} against {@code y + 1} is a rule about the pair
     * exactly as {@code x < y} is, and the arithmetic is on the far side of the relation rather
     * than in place of it. Read off whether a side <em>is</em> a coordinate, {@code y + 1} named
     * nothing and this came back as a form nobody could read — which is not what a {@code guard}
     * writing the same comparison is told.
     */
    @Test
    void andSoDoesOneWhereTheSecondPositionIsInsideAnExpression() {
        FieldDomains read = readingOf("""
                module example.spans

                data Pair = { x: Int, y: Int }
                    invariant x < y + 1
                """, "Pair");

        assertEquals(List.of(new BlockReason.ComparisonBetweenPositions()), reasonsAt(read, "x"));
        assertEquals(List.of(new BlockReason.ComparisonBetweenPositions()), reasonsAt(read, "y"));
    }

    /**
     * One rule gets one sentence, with or without arithmetic round the call that writes it.
     *
     * <p>What a clause counting two dates apart relates is the two dates: the operation states what
     * it answers in the counts of its arguments, so the quantity it cuts is over both positions and
     * neither is divided by it. Arithmetic written outside the call moves the threshold and not the
     * quantity, so each spelling is the same rule and is said the same way.
     *
     * <p>Read off the shape of the whole side rather than off the quantity, one token of arithmetic
     * outside the call is the difference between two sentences for one rule.
     */
    @Test
    void aClauseCountingTwoPositionsApartSaysSoHoweverItIsWrittenRound() {
        for (String clause : List.of("Date.daysBetween(from, to) <= 30",
                "Int.add(Date.daysBetween(from, to), 1) <= 30",
                "Int.add(1, Date.daysBetween(from, to)) <= 30")) {
            FieldDomains read = readingOf("""
                    module example.spans

                    data Span = { from: Date, to: Date }
                        invariant within = %s
                    """.formatted(clause), "Span");

            assertEquals(List.of(new BlockReason.ComparisonBetweenPositions()),
                    reasonsAt(read, "from"), clause);
            assertEquals(List.of(new BlockReason.ComparisonBetweenPositions()),
                    reasonsAt(read, "to"), clause);
        }
    }

    /**
     * But one position on both sides of a comparison is not two positions.
     *
     * <p>{@code x < x + 1} names {@code x} either side of it and there is no other position for a
     * class to be about. Told that the rule relates it to another position, a reader goes looking
     * for one the model never wrote.
     *
     * <p>Nor is anything here unread. The form is arithmetic this takes apart on both sides, and
     * what it comes to is a comparison of two numbers {@code x} does not appear in — so the rule
     * divides nothing and there was never a line in it to draw. Answered as a form nobody could
     * read, it sent an author to rewrite a spelling this compiler had read from end to end.
     */
    @Test
    void butOnePositionOnBothSidesIsNotTwoPositions() {
        FieldDomains read = readingOf("""
                module example.spans

                data Sole = { x: Int }
                    invariant x < x + 1
                """, "Sole");

        assertEquals(List.of(new BlockReason.ComparisonCuttingNothing()), reasonsAt(read, "x"));
    }

    /**
     * A position whose values carry no order to draw a line on says that, and says it once.
     *
     * <p>A field declared as one case of an enumeration is ordered — the comparison is on the sum's
     * order and typechecks — and the sum's places are not its own, so no line divides it. The
     * carrier, asked of the carrier, exactly as a {@code guard} comparing the same field is
     * answered.
     *
     * <p>The clause reaches the reading of ends all the same, which is what lets it be answered
     * for: a coordinate is a coordinate whether or not a line can be drawn on it. So the second
     * assertion is the one that matters — reading an end here would put a line through a position
     * that has no order to draw one on.
     */
    @Test
    void aPositionNoLineCanBeDrawnOnSaysThatAndIsGivenNoEnd() {
        FieldDomains read = readingOf("""
                module example.stages

                data Prospecting
                data Qualified
                data Won
                data Stage = Prospecting | Qualified | Won

                data Holder = { stage: Qualified }
                    invariant stage >= Prospecting
                """, "Holder");

        assertEquals(List.of(new BlockReason.UnreadComparisonDomain()), reasonsAt(read, "stage"));
        assertEquals(List.of(), read.placedAt(RuleKey.of("stage")), "no line is drawn where no order is");
    }

    /** And a newtype's own clause reaches the same account, at the position a name wraps. */
    @Test
    void aNewtypesOwnClauseIsNamedAtTheValueItWraps() {
        FieldDomains read = readingOf("""
                module example.stepped

                data Stepped = Int
                    invariant value >= 1 + 1
                """, "Stepped");

        List<FieldDomains.NoLine> said = read.noLineAt(RuleKey.THE_VALUE);
        assertEquals(1, said.size(), () -> "said " + said);
        assertInstanceOf(BlockReason.UnreadComparisonForm.class, said.getFirst().why());
        assertTrue(said.getFirst().part().rule() instanceof RuleRef.Invariant it
                        && it.clause().id().declaredOn().name().endsWith("Stepped"),
                "the rule a reader is sent to look at is the one that wrote the clause");
    }

    private static List<BlockReason.RuleWithoutLineReason> reasonsAt(FieldDomains read,
                                                                     String field) {
        return read.noLineAt(RuleKey.of(field)).stream().map(FieldDomains.NoLine::why).toList();
    }
}
