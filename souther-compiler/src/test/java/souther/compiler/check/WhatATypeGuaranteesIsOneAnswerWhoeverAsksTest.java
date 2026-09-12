package souther.compiler.check;

import org.junit.jupiter.api.Test;
import souther.compiler.ast.Hir;
import souther.compiler.core.Core;
import souther.compiler.diag.SourcePos;
import souther.compiler.query.Compilation;
import souther.compiler.types.BindingId;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a declaration guarantees, asked of the reading itself.
 *
 * <p>{@link TypeGuarantees} answers one question for every reader there is: a walk seeding a path,
 * and a recipe settling a choice. Each of those has tests of its own about what it does with the
 * answer, and those are the tests that pass while the answer is wrong — a walk can agree with itself
 * about a declaration it reads two levels too shallowly. So the answer is held here, on its own.
 *
 * <p>What is fixed is what a position guarantees and where the positions are. How far a reader goes
 * is not fixed and may not be: two readers walking to different depths are still reading one model,
 * and requiring one depth would put a reader's affordance inside what a declaration means. What is
 * required instead is the last test here — where two scopes both looked, they were told the same
 * thing.
 */
class WhatATypeGuaranteesIsOneAnswerWhoeverAsksTest {

    private static final SourcePos POS = new SourcePos(1, 1);

    private static final String SOURCE = """
            module demo exposing ( Money, Wrapped, Chain, Bounded, Deeper, Plain, keep )

            data NonNegInt = Int
                invariant value >= 0

            data Money = { amount: NonNegInt }

            data Wrapped = Money

            data Chain = { here: NonNegInt, next: List<Chain> }

            data Bounded = { cap: NonNegInt }
                invariant cap.value <= 100

            data Middle = { inner: Money }

            data Deeper = { mid: Middle }

            data Plain = { label: String }

            behavior keep : (m: Money) -> Money

            let keep (m) = m
            """;

    private final RuleReadingSource rules = rules();

    private final Symbols symbols = rules.symbols();

    private final PathEngine engine = new PathEngine(
            RuleReadingContext.unshared(rules, souther.compiler.query.ReadAs.THE_COMPILATION_DOES),
            Terms.Of.THE_DISCHARGE_TREE);

    private final GuaranteeWalk walk = new GuaranteeWalk(engine.guarantees(), symbols);

    private static RuleReadingSource rules() {
        Compilation compilation = Compilation.ofSource(SOURCE, "Main");
        compilation.answerEverything();
        return RuleReadings.of(compilation, compilation.modules().get(0));
    }

    private static TypeSymbol named(String name) {
        return TypeSymbols.declared(new TypeKey("demo", name));
    }

    /** A place of type {@code name}, and the reading that has it named. */
    private Read place(String name) {
        BindingId binding = CoreBinders
                .of(new Hir.Binders(new BindingOwner.OfValue("demo", "keep")).binder("v", POS))
                .binding();
        Core.Read root = new Core.Read("v", binding, Type.ref(named(name)), POS);
        Denotations at = Denotations.none().location(binding, engine.terms().placeSubject(binding),
                engine.terms().placeTerm(binding));
        return new Read(root, at);
    }

    private record Read(Core.Read root, Denotations at) {}

    /** Every clause the walk was told about, by the position it was told at. */
    private Map<String, List<String>> guaranteedUnder(String name, GuaranteeWalk.Scope scope) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        Read read = place(name);
        walk.from(read.root(), RuleKey.THE_VALUE, read.at(), scope,
                (path, guarantee) -> out.computeIfAbsent(path.toString(), _ -> new ArrayList<>())
                        .add(guarantee.rule().clause().toString()));
        return out;
    }

    /** Every position the walk stopped at, and why. */
    private Map<String, GuaranteeWalk.Stop> stoppedIn(String name, GuaranteeWalk.Scope scope) {
        Map<String, GuaranteeWalk.Stop> out = new LinkedHashMap<>();
        Read read = place(name);
        walk.from(read.root(), RuleKey.THE_VALUE, read.at(), scope,
                new GuaranteeWalk.Reader() {
                    @Override
                    public void guaranteed(RuleKey path, TypeGuarantee guarantee) {}

                    @Override
                    public void stopped(RuleKey path, Type type, GuaranteeWalk.Stop why) {
                        out.put(path.toString(), why);
                    }
                });
        return out;
    }

    /** Every position whose rules the walk said belong to a reading opened elsewhere. */
    private List<String> handedOnIn(String name, GuaranteeWalk.Scope scope) {
        List<String> out = new ArrayList<>();
        Read read = place(name);
        walk.from(read.root(), RuleKey.THE_VALUE, read.at(), scope,
                new GuaranteeWalk.Reader() {
                    @Override
                    public void guaranteed(RuleKey path, TypeGuarantee guarantee) {}

                    @Override
                    public void handedOn(RuleKey path, Type type) {
                        out.add(path.toString());
                    }
                });
        return out;
    }

    /**
     * A clause written on a field's type is a rule about that field of this value, at that field's
     * position.
     */
    @Test
    void aFieldIsAPositionOfItsOwn() {
        assertEquals(List.of("amount"),
                List.copyOf(guaranteedUnder("Money", GuaranteeWalk.Scope.asFarAs(2)).keySet()),
                "Money writes no clause of its own; NonNegInt's is about the amount");
    }

    /**
     * A newtype's value is the same location as the newtype, so what its base guarantees is
     * guaranteed of this very atom. Wearing a name is not being somewhere else.
     */
    @Test
    void aNewtypeCarriesWhatItsBaseGuaranteesAtItsOwnPosition() {
        assertEquals(List.of("amount"),
                List.copyOf(guaranteedUnder("Wrapped", GuaranteeWalk.Scope.asFarAs(2)).keySet()),
                "Wrapped is Money, which is at no path of its own, so its field is the first step");
    }

    /** A declaration writing nothing, and nothing written under it, guarantees nothing. */
    @Test
    void aDeclarationWithNoRuleUnderItGuaranteesNothing() {
        assertTrue(guaranteedUnder("Plain", GuaranteeWalk.Scope.asFarAs(2)).isEmpty(),
                "a record of a String writes no rule and holds nothing that does");
    }

    /**
     * A record holding another of its own kind does not descend for ever, and what it leaves is not
     * lost: the list under it is read by whoever opens an element.
     *
     * <p>{@code Chain} reaches itself through a {@code List}, and a list is a position no value of
     * this reading stands at — so the rules under it are handed to a reading opened at an element
     * rather than left short here. A position this walk stopped at and a position whose rules are
     * somebody else's are two answers, and this is the second.
     */
    @Test
    void aRecordHoldingItsOwnKindIsReadWhereItWasMet() {
        Map<String, List<String>> guaranteed =
                guaranteedUnder("Chain", GuaranteeWalk.Scope.asFarAs(6));

        assertEquals(List.of("here"), List.copyOf(guaranteed.keySet()),
                "the chain's own field is read once, and the list under it reaches no further");
        assertEquals(List.of("next"), handedOnIn("Chain", GuaranteeWalk.Scope.asFarAs(6)),
                "and the walk says whose the rules under it are rather than going round again");
    }

    /**
     * A depth a reader could not afford is a stop and not an answer.
     *
     * <p>Which is why the depth may not live in the reading: a position left unread is one this
     * reader did not reach, and saying nothing about it would be saying no rule stands there.
     */
    @Test
    void aDepthTheReaderCouldNotAffordIsAStop() {
        assertTrue(guaranteedUnder("Money", GuaranteeWalk.Scope.asFarAs(0)).isEmpty(),
                "the amount is one position down, and this reader asked for none");
        assertEquals(GuaranteeWalk.Stop.PAST_THE_DEPTH,
                stoppedIn("Money", GuaranteeWalk.Scope.asFarAs(0)).get("amount"),
                "and it is told that it stopped there rather than that nothing was written");
    }

    /** A name a reader supposes holds values is not descended into, and nothing under it is read. */
    @Test
    void aNameTheReaderStopsAtIsNotDescendedInto() {
        GuaranteeWalk.Scope stopping =
                new GuaranteeWalk.Scope(new GuaranteeWalk.Extent.AsFarAs(4),
                        named("NonNegInt")::equals, RulesLeftOut.NONE, PartsLeftOut.NONE);

        assertTrue(guaranteedUnder("Money", stopping).isEmpty(),
                "the only clause under Money is NonNegInt's, and this reader stops there");
        assertEquals(GuaranteeWalk.Stop.ASKED_TO_STOP, stoppedIn("Money", stopping).get("amount"));
    }

    /**
     * A declaration's own clauses may be left out while what is under it is still read.
     *
     * <p>Which is the whole difference between this and a name the reader stops at. {@code Bounded}
     * writes a rule of its own and holds a field whose type writes another; leaving the first out
     * leaves the second standing.
     */
    @Test
    void aDeclarationsOwnClausesCanBeLeftOutWithoutLeavingWhatIsUnderIt() {
        assertEquals(List.of(RuleKey.THE_VALUE.toString(), "cap"),
                List.copyOf(guaranteedUnder("Bounded", GuaranteeWalk.Scope.asFarAs(2)).keySet()),
                "both rules stand: Bounded's own, and NonNegInt's about the field");

        GuaranteeWalk.Scope without =
                new GuaranteeWalk.Scope(new GuaranteeWalk.Extent.AsFarAs(2), _ -> false,
                        RulesLeftOut.writtenOn(named("Bounded")::equals), PartsLeftOut.NONE);

        assertEquals(List.of("cap"),
                List.copyOf(guaranteedUnder("Bounded", without).keySet()),
                "the one left out is Bounded's own, and the field's is read as it was");
    }

    /**
     * A rule is where it is written, and a reader asking for every position gets it there.
     *
     * <p>The failure this is written against: a reader whose question has no depth in it borrowing
     * the number a reader with a cost bound uses. Held as one {@code int}, "as far as I can afford"
     * and "as far as the model goes" are the same kind of answer and the first gets copied into the
     * second — which makes what a declaration is taken to say depend on how deeply an author nested
     * a field. {@code Deeper} writes nothing, and the rule is three names under it.
     */
    @Test
    void aReaderAskingForEveryNameIsNotBoundedByWhatAnotherCanAfford() {
        assertEquals(List.of("mid.inner.amount"),
                List.copyOf(guaranteedUnder("Deeper", GuaranteeWalk.Scope.everyName()).keySet()),
                "the rule three names down is read, and read at the value it governs");
        assertTrue(guaranteedUnder("Deeper",
                        new GuaranteeWalk.Scope(new GuaranteeWalk.Extent.AsFarAs(2), _ -> false,
                                RulesLeftOut.NONE, PartsLeftOut.NONE)).isEmpty(),
                "a reader that can afford two names does not reach it — which is what makes"
                        + " borrowing its number a change to what the model says");
    }

    /**
     * Nothing a consumer holds may be handed to the reading.
     *
     * <p>The wall the rest of this rests on. Every test above says what the answer is; this one says
     * the answer cannot come to depend on who asked. A {@link Known} the reading could read would
     * let one caller's knowledge decide what a declaration says, a {@link InvariantChecker.Gathering}
     * would put what is being measured inside what is meant, and a {@link PathEngine} would make the
     * walk that seeds a path the owner of a question the recipe reader asks too.
     *
     * <p>Read off the signatures rather than promised in prose, because a parameter added in a hurry
     * is added to a signature and not to a comment.
     */
    @Test
    void theReadingIsHandedNothingAConsumerHolds() {
        List<String> theirs =
                List.of("Known", "Gathering", "PathEngine", "GuaranteeWalk", "Reach", "Borne");
        List<String> found = new ArrayList<>();
        for (Class<?> each : List.of(TypeGuarantees.class, TypeGuarantees.At.class,
                TypeGuarantees.At.Readable.class,
                TypeGuarantees.At.HandedOn.ToAnotherReading.class,
                TypeGuarantees.At.Coverage.Incomplete.class,
                TypeGuarantee.class, TypeGuarantee.Part.class)) {
            for (java.lang.reflect.Field field : each.getDeclaredFields()) {
                mentioning(theirs, each.getSimpleName() + "." + field.getName(),
                        field.getType(), found);
            }
            for (java.lang.reflect.Method method : each.getDeclaredMethods()) {
                String where = each.getSimpleName() + "." + method.getName();
                mentioning(theirs, where, method.getReturnType(), found);
                for (Class<?> taken : method.getParameterTypes()) {
                    mentioning(theirs, where, taken, found);
                }
            }
        }
        assertEquals(List.of(), found,
                "what a type guarantees is read from the declaration and the value, and from nothing"
                        + " a consumer of the answer happens to be holding");
    }

    private static void mentioning(List<String> theirs, String where, Class<?> type,
                                   List<String> found) {
        for (String name : theirs) {
            if (type.getSimpleName().equals(name)) {
                found.add(where + " takes or answers a " + name);
            }
        }
    }

    /**
     * One reading and many readers.
     *
     * <p>The contract itself: two scopes are two walks, and where both of them looked they were told
     * the same thing. A reader that could afford less is short of positions, never of a different
     * answer about one.
     */
    @Test
    void twoScopesAreToldTheSameThingWhereBothLooked() {
        Map<String, List<String>> shallow =
                guaranteedUnder("Chain", GuaranteeWalk.Scope.asFarAs(1));
        Map<String, List<String>> deep = guaranteedUnder("Chain", GuaranteeWalk.Scope.asFarAs(6));

        assertFalse(shallow.isEmpty(), "a reading saying nothing would agree with anything");
        shallow.forEach((path, clauses) -> assertEquals(clauses, deep.get(path),
                "what stands at " + path + " is what stands there, however far the reader went"));
    }
}
