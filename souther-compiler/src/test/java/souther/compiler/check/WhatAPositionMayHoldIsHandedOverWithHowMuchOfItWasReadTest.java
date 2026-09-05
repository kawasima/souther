package souther.compiler.check;

import souther.compiler.query.ReadAs;
import org.junit.jupiter.api.Test;

import souther.compiler.query.Scopes;
import souther.compiler.query.Compilation;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;
import souther.compiler.values.AdmissibleSet;
import souther.compiler.values.UnreadReason;
import souther.compiler.values.Value;
import souther.compiler.values.ValueSet;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which values a position is left, and whether that is the whole of what its rules leave it.
 *
 * <p>Two answers, and a reader needs both. A set of two values is a distinction the model draws at
 * that position, and the same set beside a rule this could not read is a distinction the model may
 * draw more finely. Told apart, a position nothing narrowed and a position this could not follow
 * are different reports; run together, the second is told the model divides it no way at all.
 *
 * <p>What refuses a declaration reads only the first, and reaching nothing is sound whatever the
 * second says: everything a set excludes is excluded by the rules, whether or not the rules say
 * more. So the answers below are held for the reader that divides a position rather than for the
 * one that refuses it.
 */
class WhatAPositionMayHoldIsHandedOverWithHowMuchOfItWasReadTest {

    /** One declaration read, and the module it was read in, so that a case of an enumeration can be
     * named the way the reading names it. */
    private record Read(FieldDomains domains, Symbols symbols) {

        Value caseNamed(String data) {
            return Value.of(TypeSymbols.declared(new TypeKey(symbols.module(), data)));
        }
    }

    private static Read read(String source, String named) {
        return read(source, named, ReadAs.THE_COMPILATION_DOES);
    }

    /**
     * The models above with their stand-in for a rule nothing reads written out.
     *
     * <p>Named rather than written, because which spelling this compiler cannot read is a fact
     * about this compiler and moves ({@link souther.compiler.ARuleNoReadingTakesIn}). Written out
     * six times, the day one of them became readable was the day these tests went on passing about
     * a model with nothing unread in it.
     */
    private static String unreadable(String source) {
        return source
                .replace("UNREAD_VALUE", souther.compiler.ARuleNoReadingTakesIn.about("value"))
                .replace("UNREAD_LEFT", souther.compiler.ARuleNoReadingTakesIn.about("left"))
                .replace("UNREAD_RIGHT", souther.compiler.ARuleNoReadingTakesIn.about("right"));
    }

    private static Read read(String source, String named, ReadingPolicy policy) {
        Compilation compilation = Compilation.ofSource(unreadable(source), "Main");
        compilation.answerEverything();
        assertEquals(List.of(), compilation.diagnostics().values().stream()
                .flatMap(List::stream)
                .map(each -> each.diagnostic().code())
                .toList(), "the model this reads has to be one somebody could write");
        Symbols symbols = Scopes.derived(compilation.db(), "demo").value();
        TypeSymbol.AtModule name = TypeSymbols.declared(new TypeKey(symbols.module(), named));
        return new Read(FieldDomains.of(name,
                RuleReadings.of(compilation, "demo"), policy), symbols);
    }

    private static FieldDomains of(String source, String named) {
        return read(source, named).domains();
    }

    /** The same, read with no choice held apart. What a reading owes when it cannot hold what a
     *  choice leaves is reached no other way: nothing written here expands far enough for the
     *  limit a compilation sets to fall back to it. */
    private static FieldDomains merging(String source, String named) {
        return read(source, named, ReadAs.MERGING_WHAT_A_CHOICE_LEAVES).domains();
    }

    /** The same, for a model the compiler refuses: what this hands over afterwards is still read by
     * whatever ran before the refusal reached the caller. */
    private static FieldDomains ofRefused(String source, String named) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        assertFalse(compilation.diagnostics().values().stream().flatMap(List::stream).toList()
                .isEmpty(), "this model is meant to be refused");
        Symbols symbols = Scopes.derived(compilation.db(), "demo").value();
        TypeSymbol.AtModule name = TypeSymbols.declared(new TypeKey(symbols.module(), named));
        return FieldDomains.of(name,
                RuleReadings.of(compilation, "demo"),
                ReadAs.THE_COMPILATION_DOES);
    }

    private static final Value A = Value.text("A");
    private static final Value B = Value.text("B");

    /** {@code values} are what the position holds, and are the whole of what its rules leave it. */
    private static void wholly(ValueSet values, FieldDomains read, RuleKey path) {
        assertEquals(AdmissibleSet.complete(values), read.admits(path));
    }

    /** The same, of the field the record's own rules call {@code field}. */
    private static void wholly(ValueSet values, FieldDomains read, String field) {
        wholly(values, read, RuleKey.of(field));
    }

    /** {@code values} are what the position holds, the rules may leave fewer, and {@code why} is
     *  what stopped the reading short of them. */
    private static void asFarAsRead(ValueSet values, UnreadReason why, FieldDomains read,
                                    RuleKey path) {
        assertEquals(AdmissibleSet.partial(values, why), read.admits(path));
    }

    /** The same, of the field the record's own rules call {@code field}. */
    private static void asFarAsRead(ValueSet values, UnreadReason why, FieldDomains read,
                                    String field) {
        asFarAsRead(values, why, read, RuleKey.of(field));
    }

    /**
     * A choice reaching across two positions is read one position at a time, so what a second
     * clause meets it with can leave the values wider than the rules are with every rule read.
     *
     * <p>Issue #877. Only {@code (a = "5", b = "0")} satisfies both invariants — {@code (6, 1)} is
     * refused by {@code two} and {@code (6, 0)} by {@code one} — so {@code a} is left {@code "5"}
     * and nothing else. The reading meets {@code {5, 6}} with {@code {5, 6}} and comes back with
     * both, which is sound as an upper bound and is not the whole of what the model leaves.
     */
    @Test
    void aChoiceAcrossTwoPositionsIsNotTheWholeOfWhatTheRulesLeave() {
        FieldDomains read = merging("""
                module demo

                data R = { a: String, b: String }
                    invariant one = (a == "5" && b == "0") || (a == "6" && b == "1")
                    invariant two = (a == "5" && b == "0") || (a == "6" && b == "0")
                """, "R");

        ValueSet reported = ValueSet.oneOf(Set.of(Value.text("5"), Value.text("6")));
        assertEquals(reported, read.admits(RuleKey.of("a")).approximation(),
                "the values are the upper bound they always were");
        assertNotEquals(AdmissibleSet.complete(reported), read.admits(RuleKey.of("a")),
                "and the reading may not call them the whole of what the rules leave");
    }

    /** An equality names the one value the position may hold. */
    @Test
    void anEqualityLeavesTheValueItNames() {
        FieldDomains read = of("""
                module demo

                data Gender = String
                    invariant only = value == "A"
                """, "Gender");
        wholly(ValueSet.just(A), read, RuleKey.THE_VALUE);
    }

    /**
     * A pattern names the strings it accepts, and denied it names the ones it does not.
     *
     * <p>Both, because a denial of a form this follows is not a form this cannot follow. Asserted
     * on the set rather than on a report: a denied pattern leaves no question standing either way,
     * so a document says the same thing whether the rule was read or not, and a test written there
     * would pass for a reading that had dropped it.
     */
    @Test
    void aPatternNamesWhatItAcceptsAndDeniedWhatItDoesNot() {
        AdmissibleSet stated = of("""
                module demo

                data Code = String
                    invariant shape = String.matches("[a-z]+", value)
                """, "Code").admits(RuleKey.THE_VALUE);

        assertEquals(AdmissibleSet.READ_IN_FULL, stated.completeness());
        assertTrue(stated.approximation().has(Value.text("ab")));
        assertFalse(stated.approximation().has(Value.text("A")));
        assertFalse(stated.approximation().has(Value.text("")), "one letter at least");

        AdmissibleSet denied = of("""
                module demo

                data Code = String
                    invariant shape = Bool.not(String.matches("[a-z]+", value))
                """, "Code").admits(RuleKey.THE_VALUE);

        assertEquals(AdmissibleSet.READ_IN_FULL, denied.completeness());
        assertFalse(denied.approximation().has(Value.text("ab")), "what the pattern takes is out");
        assertTrue(denied.approximation().has(Value.text("A")));
        assertTrue(denied.approximation().has(Value.text("")),
                "and the empty string, which the pattern does not accept");
    }

    /** Alternatives name both, which is the distinction a model writes this way. */
    @Test
    void alternativesLeaveEveryValueTheyName() {
        FieldDomains read = of("""
                module demo

                data Gender = String
                    invariant either = value == "A" || value == "B"
                """, "Gender");
        wholly(ValueSet.oneOf(Set.of(A, B)), read, RuleKey.THE_VALUE);
    }

    /**
     * An alternative to a rule this cannot read leaves the position open, and says so.
     *
     * <p>Both halves matter and neither is the other. The values are open because a value
     * satisfying the second alternative is under no obligation from the first — and they are open
     * because of this reading, not because the model is silent about the position.
     */
    @Test
    void anAlternativeThatCannotBeReadLeavesThePositionOpenAndSaysWhy() {
        FieldDomains read = of("""
                module demo

                data Gender = String
                    invariant either = value == "A" || UNREAD_VALUE
                """, "Gender");
        asFarAsRead(ValueSet.ANY, UnreadReason.FORM_NOT_READ, read, RuleKey.THE_VALUE);
    }

    /**
     * And where the alternative this cannot read never mentions the position at all.
     *
     * <p>The case the rule above is written for, and the one the case above does not reach: there
     * the unreadable alternative names {@code value}, so the position is spoiled by being named and
     * nothing about the alternative's being an alternative is asserted. Here nothing names
     * {@code left} but the branch that was read, and it is still a position this cannot speak for —
     * a value satisfying the other branch is under no obligation from this one, so what
     * {@code left} holds is open however plainly the first branch was read.
     */
    @Test
    void anAlternativeThatCannotBeReadSpoilsAPositionItNeverMentions() {
        FieldDomains read = of("""
                module demo

                data Pair = { left: String, right: String }
                    invariant either = left == "A" || UNREAD_RIGHT
                """, "Pair");
        asFarAsRead(ValueSet.ANY, UnreadReason.ALTERNATIVE_NOT_READ, read, "left");
    }

    /**
     * And what such a position is told is that an alternative took it back, whatever the unread
     * branch was about.
     *
     * <p>{@code left < right} relates two positions and {@code code} is neither of them, so the
     * reason the branch stopped is not a reason about {@code code}. Lent across, a report would
     * tell an author that a rule compares {@code code} with another position, and no rule does.
     *
     * <p>The two positions that rule does name are left whole. {@code code == "A"} says nothing
     * about either of them, so a {@code Triple} with any {@code left} at all satisfies the choice
     * by its second alternative — and what a position may hold is every value, which is the whole
     * of what the rules leave it. That the relating rule went unread is a fact about the rules and
     * is answered by the accounting, not by what a position may hold.
     */
    @Test
    void aPositionAnAlternativeTookBackIsNotToldWhatTheOtherBranchWasAbout() {
        FieldDomains read = of("""
                module demo

                data Triple = { left: String, right: String, code: String }
                    invariant either = left < right || code == "A"
                """, "Triple");

        asFarAsRead(ValueSet.ANY, UnreadReason.ALTERNATIVE_NOT_READ, read, "code");
        wholly(ValueSet.ANY, read, "left");
        wholly(ValueSet.ANY, read, "right");
    }

    /**
     * A clause nothing could read costs the whole value its cover, and not only the positions it
     * names.
     *
     * <p>What a conjunction promises is what its parts promise together, and a part nothing could
     * read promises nothing — it may be a rule nothing satisfies, and then the value has no values
     * and no position is at anything. So a position covered inside one clause is reported short of
     * its rules once any clause of the same value goes unread, whether or not that clause could
     * have reached it.
     *
     * <p>Written down because it is a decision and not an accident. {@code n} below holds every
     * value: {@code n == 5 || n /= 5} covers it and {@code Int.abs(m) >= 3} is about {@code m}. The
     * reading answers that it is short of its rules all the same, and telling the two apart wants a
     * reading that remembers why it promises nothing.
     */
    @Test
    void aClauseNothingCouldReadCostsTheWholeValueItsCover() {
        FieldDomains read = of("""
                module demo

                data R = { n: Int, m: Int }
                    invariant said = (n == 5 || n /= 5) || Int.abs(n) >= 2
                    invariant apart = Int.abs(m) >= 3
                """, "R");

        asFarAsRead(ValueSet.ANY, UnreadReason.FORM_NOT_READ, read, "n");
        asFarAsRead(ValueSet.ANY, UnreadReason.FORM_NOT_READ, read, "m");

        FieldDomains alone = of("""
                module demo

                data R = { n: Int, m: Int }
                    invariant said = (n == 5 || n /= 5) || Int.abs(n) >= 2
                """, "R");
        wholly(ValueSet.ANY, alone, "n");
    }

    /**
     * What covered a position is an alternative, and a rule beside the choice may leave none of it.
     *
     * <p>{@code a == 5} admits every {@code b}, so the choice does and nothing about {@code b} went
     * unread — while that alternative stands. {@code a == 7} refuses every value it admits, and what
     * is left is {@code a < b} with {@code a} at 7, which is a rule this cannot read about a
     * {@code b} that is now not every value. A reading that had struck the rule off where the
     * choice was read would answer that the model leaves {@code b} every value and that this was
     * read in full.
     */
    @Test
    void whatCoveredAPositionCanBeRefusedByARuleBesideTheChoice() {
        FieldDomains read = of("""
                module demo

                data R = { a: Int, b: Int }
                    invariant one = a == 5 || a < b
                    invariant two = a == 7
                """, "R");

        asFarAsRead(ValueSet.just(Value.number(7)), UnreadReason.RELATES_TWO_POSITIONS, read, "a");
        asFarAsRead(ValueSet.ANY, UnreadReason.RELATES_TWO_POSITIONS, read, "b");
    }

    /**
     * A choice over two positions leaves nothing for an alternative beside it to rest on.
     *
     * <p>Read one position at a time, each of the two choices leaves {@code a} open, and so does
     * what they leave together — but the pairs they agree on are only {@code a = 5, b = 0}, since
     * an {@code a} of anything else is asked for with {@code b = 1} by one and with {@code b = 0}
     * by the other. What the rules leave {@code a} is {@code 5} and whatever the third alternative
     * admits, and no reading here can say the third alternative admits anything.
     *
     * <p>So the position is one this cannot speak for. A reading that read what each position holds
     * on its own and offered that as cover would answer that the model divides {@code a} no way at
     * all, on the strength of a pair no value of this type takes.
     */
    @Test
    void aChoiceOverTwoPositionsCoversNothingForTheAlternativeBesideIt() {
        FieldDomains read = merging("""
                module demo

                data R = { a: Int, b: Int }
                    invariant said =
                        (((a == 5 && b == 0) || (a /= 5 && b == 1))
                         && ((a == 5 && b == 0) || (a /= 5 && b == 0)))
                        || Int.abs(a) >= 2
                """, "R");

        // Both stand in the way at once, and each is lifted by its own work: one wants a reader
        // for `Int.abs`, and one wants the alternatives of the choice kept apart. Said together
        // rather than by a precedence, so an author looking for either finds it.
        assertEquals(AdmissibleSet.wider(ValueSet.ANY, Set.of(
                        new AdmissibleSet.Widening.RuleUnread(UnreadReason.FORM_NOT_READ),
                        new AdmissibleSet.Widening.AlternativesNotSeparated())),
                read.admits(RuleKey.of("a")));
    }

    /**
     * A rule this cannot read stated beside one it can takes nothing away from what the read one
     * says, and leaves the position one this cannot speak for.
     *
     * <p>Both halves again, and the second is not the first being cautious. What is left is every
     * value the rules admit and may be more: the same shape with {@code "[0-9]"} in it admits
     * nothing at all, and this reading answers {@code "A"} to both. Sound for refusing — nothing is
     * excluded that the rules allow — and not a division of the position, which is what saying so
     * is for.
     */
    @Test
    void aRuleThatCannotBeReadBesideOneThatCanCostsTheValuesNothingAndTheDivisionEverything() {
        FieldDomains read = of("""
                module demo

                data Gender = String
                    invariant both = value == "A" && UNREAD_VALUE
                """, "Gender");
        asFarAsRead(ValueSet.just(A), UnreadReason.FORM_NOT_READ, read, RuleKey.THE_VALUE);
    }

    /** And a rule it cannot read that names another position costs this one nothing at all.
     *
     * <p>Nothing here relates one position to another, so a rule that narrows a position names it —
     * which is what makes the answer above local rather than a whole declaration's worth of
     * caution. */
    @Test
    void aRuleThatCannotBeReadAboutAnotherPositionCostsThisOneNothing() {
        FieldDomains read = of("""
                module demo

                data Pair = { left: String, right: String }
                    invariant both = left == "A" && UNREAD_RIGHT
                """, "Pair");
        wholly(ValueSet.just(A), read, "left");
        asFarAsRead(ValueSet.ANY, UnreadReason.FORM_NOT_READ, read, "right");
    }

    /**
     * A rule relating two positions is told apart from one written in a form this cannot read.
     *
     * <p>Both leave the positions open and neither is the other. Nothing about {@code left < right}
     * was beyond this reading — both sides were recognised, and what it says is a fact about the
     * pair, which a set of one position's values is not. A regex over one of them is a form this
     * reading does not take apart, which is a fact about the reading and is lifted by different
     * work.
     *
     * <p>An ordering and not a denial, which is the relation this reading now holds: a denial
     * between two positions is read, and what it leaves them is a relation beside the product
     * rather than a rule that reached nothing.
     *
     * <p>Told apart where the reading gave up, since that is the only place both sides are still in
     * hand. Recovered afterwards from the spoiled positions alone, the two would be one answer.
     */
    @Test
    void aRuleRelatingTwoPositionsIsNotARuleWrittenInAFormThisCannotRead() {
        FieldDomains related = of("""
                module demo

                data Pair = { left: String, right: String }
                    invariant differ = left < right
                """, "Pair");
        asFarAsRead(ValueSet.ANY, UnreadReason.RELATES_TWO_POSITIONS, related, "left");
        asFarAsRead(ValueSet.ANY, UnreadReason.RELATES_TWO_POSITIONS, related, "right");

        FieldDomains shaped = of("""
                module demo

                data Pair = { left: String, right: String }
                    invariant shape = UNREAD_LEFT
                """, "Pair");
        asFarAsRead(ValueSet.ANY, UnreadReason.FORM_NOT_READ, shaped, "left");
    }

    /**
     * A position compared with itself is related to nothing, and is not said to be.
     *
     * <p>Two operands and one position. The reading recognises the comparison and finds a position
     * on each side, which is not the same as finding two — and the word it would be projected to
     * says the rule relates this position to another, when the model wrote no other. What is true
     * of it is that this reading did not take the rule in.
     */
    @Test
    void aPositionComparedWithItselfIsRelatedToNothing() {
        asFarAsRead(ValueSet.ANY, UnreadReason.FORM_NOT_READ, of("""
                module demo

                data Code = String
                    invariant same = value == value
                """, "Code"), RuleKey.THE_VALUE);

        FieldDomains fields = of("""
                module demo

                data Pair = { left: String, right: String }
                    invariant same = left /= left
                """, "Pair");
        asFarAsRead(ValueSet.ANY, UnreadReason.FORM_NOT_READ, fields, "left");
    }

    /**
     * And a call over two positions is a form this could not read, not a relation between them.
     *
     * <p>What is known of {@code validPair(left, right)} is that two positions appear in something
     * this reading could not interpret. Whether the rule says anything about how they stand against
     * each other is exactly what was not worked out, so counting the positions and calling it a
     * relation says more than the reading found — and the word it is projected to tells the author
     * their rule compares one position with another.
     */
    @Test
    void aCallOverTwoPositionsIsAFormNotARelation() {
        FieldDomains read = of("""
                module demo

                let validPair (a: String, b: String) = String.length(a) == String.length(b)

                data Pair = { left: String, right: String }
                    invariant paired = validPair(left, right)
                """, "Pair");

        asFarAsRead(ValueSet.ANY, UnreadReason.FORM_NOT_READ, read, "left");
        asFarAsRead(ValueSet.ANY, UnreadReason.FORM_NOT_READ, read, "right");
    }

    /** A rule this cannot read on its own leaves the position open, and says that it did. */
    @Test
    void aRuleThatCannotBeReadAtAllLeavesThePositionOpenAndSaysWhy() {
        FieldDomains read = of("""
                module demo

                data Gender = String
                    invariant shape = UNREAD_VALUE
                """, "Gender");
        asFarAsRead(ValueSet.ANY, UnreadReason.FORM_NOT_READ, read, RuleKey.THE_VALUE);
    }

    /** A position with no rules at all is open, and this can say so: the model divides it in no
     * way, which is a different answer from the one above and reads the same here. */
    @Test
    void aPositionWithNoRulesIsOpenAndSpokenFor() {
        FieldDomains read = of("""
                module demo

                data Gender = String
                """, "Gender");
        wholly(ValueSet.ANY, read, RuleKey.THE_VALUE);
    }

    /** A denial over an enumeration leaves the cases it did not deny. */
    @Test
    void aDenialOverAnEnumerationLeavesTheCasesItDidNotDeny() {
        Read read = read("""
                module demo

                data Red
                data Green
                data Blue
                data Colour = Red | Green | Blue

                data Painted = { colour: Colour }
                    invariant notRed = colour /= Red
                """, "Painted");
        wholly(ValueSet.oneOf(Set.of(read.caseNamed("Green"), read.caseNamed("Blue"))),
                read.domains(), "colour");
        // And in the order the model declares them. What is written out of these is a class per
        // value, and a reader listing them takes the order it is given — so the order has to be one
        // the model settled rather than one a set happened to store them in, which for an immutable
        // copy is settled afresh on every run of the compiler.
        assertEquals(List.of(read.caseNamed("Green"), read.caseNamed("Blue")),
                List.copyOf(((ValueSet.Finite) read.domains().admits(RuleKey.of("colour"))
                        .approximation())
                        .values()));
    }

    /**
     * A reading that never ran says of every position that a rule about it may be unread.
     *
     * <p>Which is the answer that costs something to get wrong. A reading that fell over, and one a
     * caller never asked for, leave the same empty maps a value with no rules leaves — and the
     * empty answer read as "the model divides this position in no way at all" is the widest claim
     * there is, made where nothing was read.
     */
    @Test
    void aReadingOfNothingSpeaksForNoPosition() {
        asFarAsRead(ValueSet.ANY, UnreadReason.NOT_REACHED, FieldDomains.NONE,
                RuleKey.THE_VALUE);
        asFarAsRead(ValueSet.ANY, UnreadReason.NOT_REACHED, FieldDomains.NONE, "anything");
    }

    /**
     * And a clause nothing could type leaves every position of its declaration the same way.
     *
     * <p>Such a clause reaches no reading at all, so no reading here knows which position it was
     * about — and a position it might have divided is one this cannot claim to have read. The
     * declaration is refused where the clause is written; what is asserted here is that a reader
     * asking this one afterwards is not told something untrue.
     */
    @Test
    void aClauseNothingCouldTypeLeavesNoPositionSpokenFor() {
        FieldDomains read = ofRefused("""
                module demo

                data Pair = { left: String, right: Int }
                    invariant no = left == "A" && right == "B"
                """, "Pair");
        asFarAsRead(ValueSet.ANY, UnreadReason.NOT_REACHED, read, "left");
        asFarAsRead(ValueSet.ANY, UnreadReason.NOT_REACHED, read, "right");
    }

    /**
     * And a clause nothing could type inside a field's own type leaves that field the same way.
     *
     * <p>A rule reaches a position from wherever it is written — the record the position is a field
     * of, and the declarations under that record it sits inside — so a clause lost anywhere on that
     * descent is a clause no reading here saw. Which position it was about is exactly what is not
     * known, so the field it was lost under is one this cannot speak for.
     */
    @Test
    void aClauseNothingCouldTypeInsideAFieldsTypeLeavesThatFieldSpokenForByNothing() {
        FieldDomains read = ofRefused("""
                module demo

                data Inner = String
                    invariant no = value == 1

                data Outer = { inner: Inner }
                """, "Outer");
        asFarAsRead(ValueSet.ANY, UnreadReason.NOT_REACHED, read, "inner");
    }

    /**
     * A type the walk does not enter is a position whose own values this reading is not short of.
     *
     * <p>The walk goes through a field's own type and stops at what wraps one — an optional, a
     * collection, a sum of records. A rule written inside one of those is not a rule about the
     * position holding the wrapper: it is about a value one position down, which is a value that may
     * not be there at all. So what this reading says of the position above is the whole of what its
     * own rules leave it, and the rules under it are handed on rather than missed (#1072).
     *
     * <p>Said as a widening, the position above was short of a rule no row could reach: what a
     * {@code Some} holds is measured where the narrowing puts it, and the measure that reported the
     * gap was the same one that had already read the rule (#1063).
     */
    @Test
    void aRuleUnderSomethingTheWalkDoesNotEnterIsHandedOnAndNotMissed() {
        handsOn(of("""
                module demo

                data Inner = String
                    invariant only = value == "A"

                data Outer = { inner: Inner? }
                """, "Outer"), "inner");
        handsOn(of("""
                module demo

                data Inner = String
                    invariant only = value == "A"

                data Outer = { inners: List<Inner> }
                """, "Outer"), "inners");
        handsOn(of("""
                module demo

                data Yes = { n: Int }
                    invariant only = n == 1
                data No = { n: Int }
                data Answer = Yes | No

                data Outer = { answer: Answer }
                """, "Outer"), "answer");
    }

    /** Every value the position holds is left to it, and the rules under it are somebody else's to
     *  read. Both, because either alone is half the claim: a position said to be handed on and
     *  narrowed anyway would be one this reading spoke for after all, and one read in full with no
     *  handoff recorded would be a subtree nobody is ever asked about. */
    private static void handsOn(FieldDomains read, RuleKey path) {
        wholly(ValueSet.ANY, read, path);
        assertTrue(read.handedOn().contains(path),
                () -> path + " hands its rules on, and " + read.handedOn() + " says who does");
        assertTrue(read.everyRuleReachedAt(path),
                () -> path + " is short of nothing: what is under it was never addressed to it");
    }

    /** The same, of the field the record's own rules call {@code field}. */
    private static void handsOn(FieldDomains read, String field) {
        handsOn(read, RuleKey.of(field));
    }

    /**
     * A stop under one field says nothing about the field beside it.
     *
     * <p>Where the walk stopped and not that it stopped. A rule that narrows a position names it,
     * and a clause written inside one field can name no position outside that field — so a walk
     * that declined to go into a list of constrained values has said nothing about the plain
     * {@code Int} next to it, and the {@code Int} is a position nothing was written about.
     *
     * <p>Recorded as one answer for the whole value, this was the common shape in the corpus: a
     * record with one field the walk stops at leaves every other field of it reported as one whose
     * rules may have gone unread, when nothing was written about them at all.
     */
    @Test
    void aStopUnderOneFieldDoesNotSpoilTheFieldBesideIt() {
        FieldDomains read = ofRefused("""
                module demo

                data Inner = String
                    invariant no = value == 1

                data Forecast = { inner: Inner, dealCount: Int }
                """, "Forecast");

        asFarAsRead(ValueSet.ANY, UnreadReason.NOT_REACHED, read, "inner");
        wholly(ValueSet.ANY, read, "dealCount");
    }

    /** And a clause of the value's own declaration reaches every position of it, so a stop there
     *  leaves all of them short of their rules. */
    @Test
    void aStopAtTheValueItselfLeavesEveryPositionOfItShort() {
        FieldDomains read = ofRefused("""
                module demo

                data Pair = { left: String, right: Int }
                    invariant no = left == "A" && right == "B"
                """, "Pair");

        asFarAsRead(ValueSet.ANY, UnreadReason.NOT_REACHED, read, "left");
        asFarAsRead(ValueSet.ANY, UnreadReason.NOT_REACHED, read, "right");
    }

    /**
     * And a type it does not enter that no rule is written under costs nothing.
     *
     * <p>Which is what keeps the answer worth having. A record with a plain field, or with a field
     * of an enumeration, is a record the walk stops at twice and loses nothing at either stop — a
     * unit data may write no rule (spec §unit-data), and a primitive has none to write. A reading
     * that said so at every stop would speak for no position of any record that has a field.
     */
    @Test
    void aTypeTheWalkDoesNotEnterCostsNothingWhereNoRuleIsWrittenUnderIt() {
        wholly(ValueSet.ANY, of("""
                module demo

                data Outer = { plain: String }
                """, "Outer"), "plain");
        wholly(ValueSet.ANY, of("""
                module demo

                data Red
                data Green
                data Colour = Red | Green

                data Outer = { colour: Colour }
                """, "Outer"), "colour");
    }

    /** And the rules of one field say nothing about another. */
    @Test
    void whatOneFieldMayHoldIsNotWhatTheFieldBesideItMayHold() {
        FieldDomains read = of("""
                module demo

                data Pair = { left: String, right: String }
                    invariant one = left == "A"
                """, "Pair");
        wholly(ValueSet.just(A), read, "left");
        wholly(ValueSet.ANY, read, "right");
    }
}
