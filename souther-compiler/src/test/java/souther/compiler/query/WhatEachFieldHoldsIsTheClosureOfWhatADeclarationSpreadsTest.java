package souther.compiler.query;

import souther.compiler.meta.ModulePath;
import souther.compiler.types.BindingId;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What each field a declaration reaches holds, and what moves it.
 *
 * <p>The closure over what the declaration spreads, answered as one thing: a value of the type is
 * made of the fields its spreads bring in as much as of the ones it writes, and a reader working
 * that out for itself would walk the spread declarations again.
 *
 * <p>What is held beside the closure itself is what it is read off. A field's type moves this and a
 * declaration's place does not, which is the whole of why a module that reads another's fields is
 * left alone by an edit that only moves them.
 *
 * <p>And what it answers is a name to a type, not a sequence of fields. Nothing here holds the
 * order the answer iterates in, because the answer does not state one: two of these that hold the
 * same names for the same types are equal, so a reader that took the order off one would be
 * reading what no edit to that order will ever wake it about. Held below as the edit that does not
 * reach a reader rather than left unsaid, so the day somebody wants the order they find out here
 * that this is not where it is answered.
 */
class WhatEachFieldHoldsIsTheClosureOfWhatADeclarationSpreadsTest {

    private static final TypeKey LINE = new TypeKey("shop.prices", "Line");
    private static final TypeKey MONEY = new TypeKey("shop.prices", "Money");
    private static final TypeKey CENTS = new TypeKey("shop.prices", "Cents");
    private static final TypeKey PICKED = new TypeKey("shop.prices", "Picked");
    private static final TypeKey NOWHERE = new TypeKey("shop.prices", "Nowhere");
    private static final TypeKey PAIR = new TypeKey("shop.prices", "Pair");

    /** Two fields of one declaration, and the same two written the other way round. */
    private static final String TWO_OF_ITS_OWN = """
            module shop.prices exposing ( Pair )

            data Pair = { qty: Int, note: String }
            """;

    private static final String TWO_OF_ITS_OWN_SWAPPED = """
            module shop.prices exposing ( Pair )

            data Pair = { note: String, qty: Int }
            """;

    private static final String SPREADING = """
            module shop.prices exposing ( Line )

            data Cents = { amount: Int }

            data Money = { ...Cents, currency: String }

            data Line = { ...Money, qty: Int }

            data Picked = One | Two
            """;

    /** The same declarations with the innermost field written as something else. */
    private static final String ANOTHER_TYPE = """
            module shop.prices exposing ( Line )

            data Cents = { amount: Decimal }

            data Money = { ...Cents, currency: String }

            data Line = { ...Money, qty: Int }

            data Picked = One | Two
            """;

    /** The same declarations, written the other way round. Nothing is added or taken away. */
    private static final String MOVED = """
            module shop.prices exposing ( Line )

            data Picked = One | Two

            data Line = { ...Money, qty: Int }

            data Money = { ...Cents, currency: String }

            data Cents = { amount: Int }
            """;

    /** What a spread brings in is reached through however many declarations it takes. */
    @Test
    void whatASpreadBringsInIsReachedThroughHoweverManyDeclarationsItTakes() {
        Compilation c = compiling(SPREADING);

        assertEquals(Type.INT, fields(c, LINE).get("amount"),
                "a field two spreads down is one of the fields a value of the type has");
        assertEquals(Set.of("amount", "currency", "qty"), fields(c, LINE).keySet(),
                "together with everything reached on the way and the declaration's own");
    }

    /**
     * The order the answer iterates in is no part of it, and writing the fields in another order
     * reaches nothing that read it.
     *
     * <p>What this answers is which type stands at each name. Two answers holding the same names
     * for the same types are equal, so a declaration whose fields are written the other way round
     * and changed in no other way leaves every reader of this alone.
     *
     * <p>Held because the answer is carried in something that iterates, which invites a reader to
     * take the order off it. Such a reader would be reading what the store does not watch: the
     * edit below would go unnoticed by it, with nothing to say so. The order a value is laid out
     * in is a different question and is asked where it is answered.
     */
    @Test
    void writingTheFieldsInAnotherOrderIsNotAChangeToWhatTheyHold() {
        Compilation c = compiling(TWO_OF_ITS_OWN);
        Map<String, Type> before = fields(c, PAIR);
        assertEquals(Set.of("qty", "note"), before.keySet(), "the names it answers about");
        Map<String, BindingId> bound = bindings(c, PAIR);

        edit(c, TWO_OF_ITS_OWN_SWAPPED);

        // The control, and the reason this is not an edit that failed to arrive: the same two
        // fields written the other way round are numbered the other way round, which the answer
        // about bindings says because a field's number is what that answer is made of.
        assertNotEquals(bound, bindings(c, PAIR),
                "the edit reached the declaration: which field of its owner each one is moved");

        assertEquals(before, fields(c, PAIR),
                "the same names hold the same types, so this is the same answer — and an edit that"
                        + " only moves a field among its siblings wakes nothing that read it");
    }

    /** Retyping a field the spread brings in is what moves this. */
    @Test
    void retypingAFieldASpreadBringsInMovesIt() {
        Compilation c = compiling(SPREADING);
        Map<String, Type> before = fields(c, LINE);

        edit(c, ANOTHER_TYPE);

        assertNotEquals(before, fields(c, LINE),
                "what a value of the type holds is what this answers");
        assertEquals(Type.DECIMAL, fields(c, LINE).get("amount"));
    }

    /** Moving the declarations does not. */
    @Test
    void movingTheDeclarationsLeavesEveryFieldHoldingWhatItHeld() {
        Compilation c = compiling(SPREADING);
        Map<String, Type> before = fields(c, LINE);

        edit(c, MOVED);

        assertEquals(before, fields(c, LINE),
                "where a declaration stands is not what its fields hold");
    }

    /** A declaration that reaches no field answers with none, and a name nothing declares answers
     *  nothing at all. */
    @Test
    void aDeclarationThatReachesNoFieldIsNotANameNothingDeclares() {
        Compilation c = compiling(SPREADING);

        assertTrue(c.db().ask(new Shapes.EffectiveFieldTypesOf(PICKED)).present(),
                "a sum is declared, and reaches no field");
        assertEquals(Map.of(), fields(c, PICKED));
        assertFalse(c.db().ask(new Shapes.EffectiveFieldTypesOf(NOWHERE)).present(),
                "and a name nothing declares has no fields to answer about");
    }

    /**
     * What it is read off: the declaration it was asked about and the ones it spreads, with their
     * names resolved.
     *
     * <p>Nothing about where any of them is written, and nothing about what any of them says. A
     * reader of this depends on the types the walk reached and on nothing else, which is what lets
     * an edit that only moves a declaration stop here.
     */
    @Test
    void itIsReadOffTheDeclarationsTheWalkReachesAndNothingElse() {
        Compilation c = compiling(SPREADING);
        fields(c, LINE);

        Set<String> asked =
                c.db().dependenciesOf(new Shapes.EffectiveFieldTypesOf(LINE)).stream()
                        .map(Object::toString)
                        .collect(Collectors.toCollection(LinkedHashSet::new));

        assertEquals(Set.of("ResolvedDeclaration[named=shop.prices.Line]",
                        "ResolvedDeclaration[named=shop.prices.Money]",
                        "ResolvedDeclaration[named=shop.prices.Cents]"), asked,
                "the declaration it was asked about and the ones the spreads reach");
    }

    /** A declaration that spreads nothing reads only itself. */
    @Test
    void aDeclarationThatSpreadsNothingReadsOnlyItself() {
        Compilation c = compiling(SPREADING);
        fields(c, CENTS);

        assertEquals(Set.of("ResolvedDeclaration[named=shop.prices.Cents]"),
                c.db().dependenciesOf(new Shapes.EffectiveFieldTypesOf(CENTS)).stream()
                        .map(Object::toString)
                        .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    /** Which binding each field of {@code named} is — the answer beside this one, which a field's
     *  order does move. */
    private static Map<String, BindingId> bindings(Compilation c, TypeKey named) {
        Answer<Map<String, BindingId>> answer = c.db().ask(new Shapes.FieldBindingsOf(named));
        return answer.present() ? answer.value() : Map.of();
    }

    private static Map<String, Type> fields(Compilation c, TypeKey named) {
        Answer<Map<String, Type>> answer = c.db().ask(new Shapes.EffectiveFieldTypesOf(named));
        return answer.present() ? answer.value() : Map.of();
    }

    private static Compilation compiling(String source) {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("prices.sou", source);
        Compilation c = Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY);
        c.answerEverything();
        assertTrue(c.db().allReports().isEmpty(), "the workspace compiles to begin with: "
                + c.db().allReports());
        return c;
    }

    private static void edit(Compilation c, String source) {
        Map<String, String> edited = new LinkedHashMap<>();
        edited.put("prices.sou", source);
        c.update(edited, Set.of());
        c.answerEverything();
        assertTrue(c.db().allReports().isEmpty(), "the edited workspace compiles: "
                + c.db().allReports());
    }
}
