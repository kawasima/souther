package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.DefaultStdlib;
import souther.compiler.ast.Hir;
import souther.compiler.check.RuleReadingContext;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.Carrier;
import souther.compiler.check.DefaultBoundOperationFacts;
import souther.compiler.check.NumericAnswers;
import souther.compiler.check.ReadingPolicy;
import souther.compiler.check.Symbols;
import souther.compiler.inputs.NumericTerm;
import souther.compiler.inputs.TermPath;
import souther.compiler.numeric.Count;
import souther.compiler.numeric.Place;
import souther.compiler.observe.ObservedValue;
import souther.compiler.semantics.TakenAs;
import souther.compiler.types.Type;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading a number off a row and writing a value that answers it are the two directions of one
 * account, and this is what holds them to each other.
 *
 * <p><b>Not that they are inverse.</b> Neither direction is injective: many strings are five long,
 * and {@code Int.abs} answers three at three and at minus three. So what is asked is one way round —
 * every value built for a number reads back as that number. The other way round is not asked and
 * would be wrong to: which of the values answering a number a realizer offers is its business, and
 * an operation whose values are chosen from a wide set would fail a test that demanded the one it
 * started from.
 *
 * <p>Asked over the declarations rather than over a list written here. Every operation that declares
 * what it takes of the one value it is given is walked, so an operation declared tomorrow is inside
 * this without a line being added — which is the whole point of the answers having moved onto the
 * operation (#1027).
 */
class WhatIsRealizedForANumberReadsBackAsThatNumberTest {

    private static final Symbols SYMBOLS = Symbols.none(DefaultStdlib.get());

    private static final RuleReadingSource RULES = RuleReadings.ofNoClauseFiled(SYMBOLS);
    private static final ReadingPolicy POLICY = new ReadingPolicy(64, 12,
            souther.compiler.values.AsACompilationAllows.admittedValues(),
            souther.compiler.values.AsACompilationAllows.whatARuleLeaves());
    private static final RuleReadingContext READING =
            RuleReadingContext.unshared(RULES, POLICY);
    private static final TermPath AT = TermPath.of("x");

    /** The two orders an hour of a time stands on: seconds of a day at the position, a count by one
     *  for what the operation answers. */
    private static final souther.compiler.inputs.TermOrders AS_AN_HOUR =
            souther.compiler.inputs.TermOrdersFixtures.orders(
                    term("Time", "hour"), Carrier.TIME, Carrier.WHOLE);

    /**
     * Numbers the operation actually answers, which is where its own bound runs.
     *
     * <p>Asked of the operation rather than written here. An hour of the day stops at twenty-three
     * and a count does not stop at all, and a walk over numbers of its own would be testing one of
     * them outside what it answers — where building nothing is the right answer and reading nothing
     * back proves nothing.
     */
    private static List<Long> answerable(NumericTerm.FromOnePosition term) {
        List<Long> out = new ArrayList<>();
        for (long each : new long[] {0, 1, 3, 7, 12, 23, 28, 31}) {
            if (term.intrinsicBounds().admits(Count.of(each))) {
                out.add(each);
            }
        }
        return out;
    }

    /** The type each operation is applied to, for the walk below to have a position to stand at. */
    private static Type sourceOf(ValueName operation) {
        Type declared = DefaultStdlib.get()
                .entry((ValueName.Stdlib.Operation) operation).signature().params().get(0);
        // A signature over a type variable is a signature over anything, and a walk needs one thing.
        // `Int` is the element every collection below is asked to hold, so what is built is a value
        // of a type the language certainly has.
        return switch (declared) {
            case Type.ListOf _ -> new Type.ListOf(Type.INT);
            case Type.SetOf _ -> new Type.SetOf(Type.INT);
            case Type.MapOf _ -> new Type.MapOf(Type.INT, Type.INT);
            default -> declared;
        };
    }

    /**
     * Every account of what an operation takes is one some operation declares.
     *
     * <p>The runtime half of a compile-time closure. That an account added is one the reader and the
     * realizer cannot compile without is the switches' to say, and Java says it at build time; what
     * it cannot say is that the account was ever declared of anything, and an account nothing
     * declares is an arm nothing below would exercise.
     */
    @Test
    void everyAccountOfWhatIsTakenIsDeclaredOfSomeOperation() {
        List<Class<?>> arms = List.of(TakenAs.class.getPermittedSubclasses());
        assertFalse(arms.isEmpty(), "the accounts are a sealed set and there is at least one");
        for (Class<?> arm : arms) {
            assertTrue(DefaultBoundOperationFacts.get().answersANumberTakenOfItsArgument().stream()
                            .anyMatch(each -> arm.isInstance(DefaultBoundOperationFacts.get().takenAs(each))),
                    arm.getSimpleName() + " is an account no operation is declared under, so"
                            + " nothing reads or writes it");
        }
    }

    /**
     * What is built for a number reads back as that number, for every operation there is.
     *
     * <p>Over the numbers a caller of either direction actually holds: nought, because an empty
     * value is a value like any other and a magnitude of nought is one number rather than two, and
     * upwards from there. A count below nought is not a number any of these answers and is asked of
     * neither direction here.
     */
    @Test
    void everyValueBuiltForANumberReadsBackAsIt() {
        int checked = 0;
        for (ValueName operation : DefaultBoundOperationFacts.get().answersANumberTakenOfItsArgument()) {
            Type source = sourceOf(operation);
            NumericTerm.TakenOf term = NumericTerm.TakenOf.of(
                    (ValueName.Stdlib) operation, AT, source,
                    souther.compiler.check.NewtypeInners.asWritten(SYMBOLS), SYMBOLS);
            assertNotNull(term, operation + " is taken of what its own signature says it takes");
            souther.compiler.inputs.TermOrders orders =
                    souther.compiler.inputs.TermOrdersFixtures.at(term, source, SYMBOLS);
            assertNotNull(orders.answered(),
                    operation + " answers a number, so there is an order for it");
            for (long each : answerable(term)) {
                Place asked = Count.of(each);
                TermRealizations.Realization made =
                        TermRealizations.at(source,
                                orders, asked, NothingTheRulesSay.REGION, READING);
                // An operation that builds nothing at a number is not a failure of this: whether
                // anything answers it is `EveryAnswerItCanGiveHasASourceValue`, asked below.
                if (!(made instanceof TermRealizations.Realization.Built built)) {
                    continue;
                }
                for (FixtureTemplate value : built.values()) {
                    assertEquals(new NumericTerm.Reading.Number(asked),
                            orders.read(observed(value)),
                            operation + " built " + value.text() + " for " + each
                                    + ", and it does not read back as that");
                    checked++;
                }
            }
        }
        // So that a walk which built nothing anywhere cannot pass as a walk that found nothing
        // wrong. What the number is does not matter and is not asserted; that there was one does.
        assertTrue(checked > 0, "nothing was built, so nothing was read back");
    }

    /**
     * And where the operation says every number it answers is one some value answers, something is
     * built for each of them.
     *
     * <p>Beside the reading above and not folded into it. That what was built reads back is one
     * statement and that anything is built is another: an operation counting things the language may
     * have none of satisfies the first at every number and the second at none, which is why the
     * second is a fact about the operation rather than something a realizer is trusted for.
     */
    @Test
    void everyNumberAnOperationSaysHasAValueIsOneSomethingIsBuiltFor() {
        for (ValueName operation : DefaultBoundOperationFacts.get().answersANumberTakenOfItsArgument()) {
            if (!DefaultBoundOperationFacts.get().everyAnswerItCanGiveHasASourceValue(operation)) {
                continue;
            }
            Type source = sourceOf(operation);
            NumericTerm.TakenOf term = NumericTerm.TakenOf.of(
                    (ValueName.Stdlib) operation, AT, source,
                    souther.compiler.check.NewtypeInners.asWritten(SYMBOLS), SYMBOLS);
            assertNotNull(term, operation + " is taken of what its own signature says it takes");
            souther.compiler.inputs.TermOrders orders =
                    souther.compiler.inputs.TermOrdersFixtures.at(term, source, SYMBOLS);
            for (long each : answerable(term)) {
                assertInstanceOf(TermRealizations.Realization.Built.class,
                        TermRealizations.at(source,
                                orders, Count.of(each), NothingTheRulesSay.REGION, READING),
                        operation + " says every number it answers is one some value answers, and"
                                + " nothing was built for " + each);
            }
        }
    }

    /**
     * What each account answers, at the values that tell it from a plausible neighbour.
     *
     * <p>The layer the two above cannot reach. A realizer that wrote only ASCII and a reader that
     * counted UTF-16 units agree with each other at every string either of them would offer, so a
     * round trip is green while a boundary drawn on a string outside the basic plane stands one
     * place away from the rule that drew it. The oracle is written here, in the values themselves.
     */
    @Test
    void whatEachAccountAnswersIsWhatTheLibraryAnswers() {
        assertEquals(Count.of(1),
                number(souther.compiler.inputs.TermOrdersFixtures.orders(
                        term("String", "length"), Carrier.TEXT, Carrier.WHOLE)
                        .read(new ObservedValue.Text("😀"))),
                "a string counts in code points, and one emoji is one of them");
        assertEquals(Count.of(13),
                number(AS_AN_HOUR.read(new ObservedValue.Temporal("13:45:12"))),
                "a quarter to two in the afternoon falls in the thirteenth hour");
        assertEquals(Count.of(0),
                number(AS_AN_HOUR.read(new ObservedValue.Temporal("00:45:12"))),
                "and three quarters of an hour past midnight falls in the noughth");
    }

    /**
     * What such a term is measured by is the operation's result and not the position's type.
     *
     * <p>The two abs operations are the whole of this: one argument type each, one arm between them,
     * and two different orders for a boundary to be drawn on. Read off the arm, or off the kind of
     * term, both would be whole numbers and every strict bound on a decimal magnitude would be
     * sharpened onto a count the term never takes.
     */
    @Test
    void whatATakenNumberIsMeasuredByIsTheOperationsResult() {
        souther.compiler.inputs.TermOrders anHour = souther.compiler.inputs.TermOrdersFixtures
                .at(term("Time", "hour"), Type.Prim.TIME, SYMBOLS);
        souther.compiler.inputs.TermOrders aLength = souther.compiler.inputs.TermOrdersFixtures
                .at(term("String", "length"), Type.STRING, SYMBOLS);

        assertEquals(Carrier.WHOLE, anHour.answered(), "an hour is counted by one");
        assertEquals(Carrier.TIME, anHour.observed(),
                "while the value it is read off counts the seconds of its day");
        assertEquals(Carrier.WHOLE, aLength.answered(),
                "and a count is whole however the thing counted is ordered");
        assertEquals(Carrier.TEXT, aLength.observed(),
                "while what is read at the position is still a string");
    }

    /**
     * A number an operation answers is read off the value's own order, and a boundary on it is drawn
     * on the answer's.
     *
     * <p>The assertion the whole separation turns on, and the one the size operations cannot make:
     * a string has no count of its own, so nothing could tell which of the two orders a single
     * carrier meant. A time has one, and the two orders are different — thirteen hours is
     * forty-six thousand eight hundred seconds — so a reader handed the wrong one answers a number
     * rather than nothing, and nothing about it looks like a failure (#1027).
     */
    @Test
    void theOrderAValueIsReadOnIsNotTheOrderItsAnswerIsMeasuredOn() {
        NumericTerm.FromOnePosition hour = term("Time", "hour");
        assertEquals(Count.of(13),
                number(AS_AN_HOUR.read(new ObservedValue.Temporal("13:00:00"))));
        assertInstanceOf(NumericTerm.Reading.NotNumber.class,
                souther.compiler.inputs.TermOrdersFixtures.itself(hour, Carrier.WHOLE)
                        .read(new ObservedValue.Temporal("13:00:00")),
                "and read on the order the answer is measured on — which is what a caller handing"
                        + " one carrier used to be able to do — the same value reads as no number at"
                        + " all");
    }

    /**
     * The parts of a date, at the numbers a calendar is where the trouble is.
     *
     * <p>Beside the walk above and not instead of it. That one asks every account at a handful of
     * numbers chosen to suit all of them, and the numbers a calendar goes wrong at are the calendar's
     * own: the last day of the longest month, the day February has only every fourth year, the last
     * month, a year either side of a leap one. A date offered for the thirty-first in a month with
     * thirty days is not a date, and a walk that never asks for the thirty-first would not find out.
     *
     * <p>Read back and not compared to a date written here. Which date answers a year is the
     * realizer's to choose, and naming one would make this a test of the choice rather than of the
     * law it has to satisfy.
     */
    @Test
    void everyPartOfADateReadsBackFromTheDateOfferedForIt() {
        readsBackAt("Date.year", Type.Prim.DATE, 1999, 2000, 2001, 1, 0);
        readsBackAt("Date.month", Type.Prim.DATE, 1, 2, 11, 12);
        readsBackAt("Date.day", Type.Prim.DATE, 1, 28, 29, 30, 31);
    }

    /** Every one of those numbers has a value built for it, and it reads back as that number. */
    private static void readsBackAt(String qualified, Type source, long... numbers) {
        ValueName.Stdlib operation = DefaultStdlib.get().operation(qualified);
        NumericTerm.TakenOf term = NumericTerm.TakenOf.of(operation, AT, source,
                    souther.compiler.check.ScopedDeclarations.wrapsOf(SYMBOLS), SYMBOLS);
        assertNotNull(term, qualified + " is taken of what its own signature says it takes");
        souther.compiler.inputs.TermOrders orders =
                    souther.compiler.inputs.TermOrdersFixtures.at(term, source, SYMBOLS);
        for (long each : numbers) {
            Place asked = Count.of(each);
            TermRealizations.Realization made =
                    TermRealizations.at(source,
                            orders, asked, NothingTheRulesSay.REGION, READING);
            TermRealizations.Realization.Built built = assertInstanceOf(
                    TermRealizations.Realization.Built.class, made,
                    qualified + " answers " + each + " of some date, so there is one to offer");
            for (FixtureTemplate value : built.values()) {
                assertEquals(new NumericTerm.Reading.Number(asked),
                        orders.read(observed(value)),
                        qualified + " built " + value.text() + " for " + each
                                + ", and it does not read back as that");
            }
        }
    }

    /**
     * An operation the language gives no account of has no term.
     *
     * <p>Refused where the term is built rather than at whichever reader met it first. Every other
     * answer about such a term comes from the declaration, so one without a declaration is a term
     * read by whatever each reader's default happened to be.
     *
     * <p>Asked of an operation the language writes out, and not of one that has simply not been
     * declared yet. {@code Int.abs} is an ordinary {@code let}, so a reading takes its body and a
     * term standing for the call is refused where an account would be declared — which makes it an
     * operation that cannot come to have one. Asked of a gap instead, this test would hold only
     * until somebody filled the gap, and what it is about would leave with it.
     */
    @Test
    void aTermCannotBeBuiltForAnOperationThatDeclaresNoAccount() {
        assertTrue(DefaultBoundOperationFacts.get().takenAs(ValueName.Stdlib.operation("Int", "abs")) == null,
                "the premise: what it answers is read by reading its body, so no account is"
                        + " declared of it and none may be");
        assertNull(NumericTerm.TakenOf.of(ValueName.Stdlib.operation("Int", "abs"), AT,
                        Type.Prim.INT, souther.compiler.check.ScopedDeclarations.wrapsOf(SYMBOLS), SYMBOLS),
                "so there is no term for what it answers");
    }

    /**
     * And neither is one whose operation is not taken of what stands at the location.
     *
     * <p>The half a check on the declaration alone cannot make. {@code String.length} declares an
     * account and answers a number, so nothing about the operation keeps it from a path holding a
     * time; what keeps it out is the account itself, which is taken of something that holds things.
     * Carried as a premise about who builds one — "the operation and the location agree, by
     * construction" — this was a term whose reading would count whatever happened to be at the path
     * (#1027).
     */
    @Test
    void aTermCannotBeBuiltWhereTheOperationIsNotTakenOfWhatIsThere() {
        assertNull(NumericTerm.TakenOf.of(ValueName.Stdlib.operation("String", "length"), AT,
                        Type.Prim.TIME, souther.compiler.check.ScopedDeclarations.wrapsOf(SYMBOLS), SYMBOLS),
                "a length is taken of what holds things, and a time holds none");
        assertNull(NumericTerm.TakenOf.of(ValueName.Stdlib.operation("Time", "hour"), AT,
                        Type.STRING, souther.compiler.check.ScopedDeclarations.wrapsOf(SYMBOLS), SYMBOLS),
                "and an hour is taken of a time");
        assertNotNull(NumericTerm.TakenOf.of(ValueName.Stdlib.operation("String", "length"), AT,
                        Type.STRING, souther.compiler.check.ScopedDeclarations.wrapsOf(SYMBOLS), SYMBOLS),
                "while the pair the library declares goes together");
    }

    /** What the type of the number an operation answers is, asked in the one place that says. */
    @Test
    void theNumberAnOperationAnswersIsTypedByTheLibrary() {
        assertEquals(Type.INT,
                NumericAnswers.typeOf(ValueName.Stdlib.operation("String", "length"), SYMBOLS));
        assertEquals(Type.INT,
                NumericAnswers.typeOf(ValueName.Stdlib.operation("Time", "hour"), SYMBOLS));
        // Not among the terms this change makes, and asked all the same: what an operation answers
        // is a question about the operation, and the answer here is the one that would have been
        // taken from the argument's order by a reader that assembled it itself.
        assertEquals(Type.INT,
                NumericAnswers.typeOf(ValueName.Stdlib.operation("Decimal", "toInt"), SYMBOLS),
                "a decimal rounded to a whole number answers a whole number");
    }

    /** A term for an operation, taken of what that operation's own signature says it takes. */
    private static NumericTerm.FromOnePosition term(String module, String name) {
        ValueName.Stdlib operation = ValueName.Stdlib.operation(module, name);
        NumericTerm.TakenOf made =
                NumericTerm.TakenOf.of(operation, AT, sourceOf(operation),
                        souther.compiler.check.ScopedDeclarations.wrapsOf(SYMBOLS), SYMBOLS);
        assertNotNull(made, operation + " is taken of what it takes");
        return made;
    }

    private static Place number(NumericTerm.Reading read) {
        return assertInstanceOf(NumericTerm.Reading.Number.class, read).value();
    }

    /**
     * A built value as a row holds it.
     *
     * <p>The oracle and not a second decoder. What crosses from a fixture to a row is the run time's
     * to do, and running one to test a boundary would put the whole example pipeline between a
     * question about two functions and its answer. What is written here is the mapping from the
     * literals these realizers actually build, and it is deliberately the simplest reading of them:
     * a test whose oracle repeated the reader's own cleverness would agree with it about being
     * wrong.
     */
    private static ObservedValue observed(FixtureTemplate value) {
        return observed(value.value());
    }

    private static ObservedValue observed(Hir.Expr written) {
        return switch (written) {
            case Hir.IntLit lit -> new ObservedValue.Integer(lit.value());
            case Hir.DecimalLit lit -> new ObservedValue.Decimal(lit.value());
            case Hir.StringLit lit -> new ObservedValue.Text(lit.value());
            case Hir.Neg neg -> negated(observed(neg.operand()));
            case Hir.ListLit list -> collection(list.elements());
            // A temporal is written as the namespace applied to an ISO string, and a row holds it
            // as that text. Which temporal it is the position says, not the text — so the oracle
            // keeps the text and lets the carrier handed to the reader decide, which is the whole
            // arrangement being tested.
            case Hir.Apply built when built.args().size() == 1
                    && built.args().get(0) instanceof Hir.StringLit iso ->
                    new ObservedValue.Temporal(iso.value());
            default -> throw new IllegalStateException(
                    "a realizer built something this oracle does not read: " + written);
        };
    }

    /** A list literal as a row holds it: a sequence, or a mapping where its elements are pairs. */
    private static ObservedValue collection(List<Hir.Expr> elements) {
        List<ObservedValue.Entry> entries = new ArrayList<>();
        for (Hir.Expr each : elements) {
            if (!(each instanceof Hir.Tuple pair) || pair.elements().size() != 2) {
                entries = null;
                break;
            }
            entries.add(new ObservedValue.Entry(observed(pair.elements().get(0)),
                    observed(pair.elements().get(1))));
        }
        if (entries != null && !elements.isEmpty()) {
            return new ObservedValue.Mapping(entries);
        }
        List<ObservedValue> out = new ArrayList<>();
        for (Hir.Expr each : elements) {
            out.add(observed(each));
        }
        return new ObservedValue.Sequence(out);
    }

    private static ObservedValue negated(ObservedValue value) {
        return switch (value) {
            case ObservedValue.Integer whole -> new ObservedValue.Integer(-whole.value());
            case ObservedValue.Decimal dense -> new ObservedValue.Decimal(dense.value().negate());
            default -> throw new IllegalStateException("nothing else is written negated: " + value);
        };
    }
}
