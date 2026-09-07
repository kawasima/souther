package souther.compiler.check;

import souther.compiler.numeric.LinearForm;
import souther.compiler.semantics.NumericResult;
import souther.compiler.semantics.TakenAs;
import souther.compiler.stdlib.Stdlib;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.List;

/**
 * Which representations read the number an operation answers, and how many of them there are.
 *
 * <p>Three sentences say what this is for, and each of them is a decision somebody will want to
 * undo:
 *
 * <ul>
 *   <li>This describes facts, not precedence. There is no first arm and no fallback: an operation
 *       carrying two accounts of its number gets two, and choosing between them here would be a
 *       fifth account nobody declared.
 *   <li>{@link Resolution.Multiple} is an invalid library definition, not a choice to resolve. What
 *       it means is that two representations would each read one call and which of them a report
 *       showed would be whichever reader arrived. Nothing recovers from it.
 *   <li>{@link Resolution.None} is valid globally; a question may make it invalid within its range.
 *       Most of the library answers numbers nothing reads, and that is ordinary. Where a check
 *       needs a reading, it is that check's range that says so and its silence
 *       ({@code BoundOperationFact.SaysNothingOf}) that is the other answer.
 * </ul>
 *
 * <p>The third is the one worth keeping. Made to answer {@code One} wherever a number is answered,
 * this would start carrying the deliberate silences — {@code String.toInt} answers a number at one
 * case of a union and no representation reads it there — and finding out what is declared would be
 * deciding what ought to be. Made to refuse {@code Multiple} only where some question asks, the
 * exclusivity would hold as far as that question's range and no further, and would move when the
 * range moved.
 *
 * <p><b>Counted, and not asked one representation at a time.</b> Written as a condition per
 * representation inside whatever holds a declaration, a representation added is one every existing
 * representation has to be told about, and the pair nobody writes down is the pair that goes
 * unchecked. Counted here, an account added is one arm on {@link NumericReading} and one line here,
 * and every pair it makes with an existing arm is refused without being named.
 *
 * <p><b>Over the bound facts.</b> What is counted is what the binding made, so the readings an
 * operation has are read off the same values every other reader holds. The binder asks this of the
 * facts it has just bound, before it publishes them ({@code OperationFactBinder}), and a question
 * about the range asks it of the published ones ({@link Question}); both are the one procedure over
 * one vocabulary.
 */
final class NumericReadings {

    /** How many representations read one operation's number. */
    sealed interface Resolution {

        /** Nothing reads it. */
        record None() implements Resolution {}

        /** Exactly one does, and this is it. */
        record One(NumericReading reading) implements Resolution {

            public One {
                java.util.Objects.requireNonNull(reading, "there is one, so it is named");
            }
        }

        /** More than one does, which the library must not declare. */
        record Multiple(List<NumericReading> readings) implements Resolution {

            public Multiple {
                readings = List.copyOf(readings);
                if (readings.size() < 2) {
                    throw new IllegalArgumentException(
                            "more than one is more than one: " + readings.size() + " named");
                }
            }
        }
    }

    /**
     * The representations that read the number {@code operation} answers, counted, over the bound
     * facts in {@code facts}.
     *
     * <p>Over facts that are handed in and not over the ones the process happens to hold, for the
     * reason the binder is given its source: the binder asks this of facts it has not yet
     * published, and a reader of the process's own would be asking that holder to finish building
     * while it was being built.
     *
     * @throws IllegalStateException where the library declares no such operation
     */
    static Resolution resolve(Stdlib stdlib, BoundOperationFacts facts, ValueName operation) {
        List<NumericReading> found = readingsOf(stdlib, facts, operation);
        return switch (found.size()) {
            case 0 -> new Resolution.None();
            case 1 -> new Resolution.One(found.get(0));
            default -> new Resolution.Multiple(found);
        };
    }

    /**
     * The same, listed.
     *
     * <p>The subject is the number, so an operation that answers none has no reading of one — a
     * date shifted by an amount is declared as a form of what its arguments are counted as, and
     * that form is about the date and not about a number the operation answered. Read without this,
     * every such operation would carry a reading of a number that is not there, and a proposition
     * with no subject is one nothing can be false of.
     *
     * <p>Answered at one case of a union counts, for the reason {@code Question.NUMERIC_RESULT}
     * counts it: what the shape of a result says is which inputs an operation declines, not what
     * it answers where it answers anything.
     *
     * <p>In the order the arms are written and not in the order the facts were declared, so a
     * message naming two readings names them the same way whichever of them was written first.
     */
    private static List<NumericReading> readingsOf(Stdlib stdlib, BoundOperationFacts facts,
                                                   ValueName operation) {
        Stdlib.Entry entry = OperationFactBinder.holdTheOperationToTheLibrary(stdlib, operation);
        ValueName.Stdlib.Operation named = OperationFactBinder.theLibraryOperation(operation);
        // Whether a number is answered for some value the operation could be given, which is what a
        // reader of declarations can ask. Asked as "is the declared result a number", an operation
        // whose answer is what its container holds was read as answering none — and a walk that
        // adds up whole numbers answers one at every call it is given whole numbers.
        if (!NumericAnswers.mayAnswerANumber(facts, named, entry.signature())) {
            return List.of();
        }
        List<NumericReading> terms = new ArrayList<>();
        List<NumericReading> forms = new ArrayList<>();
        List<NumericReading> arithmetic = new ArrayList<>();
        for (BoundOperationFact each : facts.all()) {
            if (!operation.equals(each.operation().operation())) {
                continue;
            }
            // No default. A kind of fact added is a kind this has to answer for — whether it is an
            // account of the number an operation answers is a question about the fact, and one
            // nobody would think to come here and ask. Answered by falling through a default, the
            // fifth representation would be exclusive with nothing.
            switch (each) {
                case BoundOperationFact.AnswersANumberTakenOfTheOneValueItIsGiven taken ->
                        terms.add(new NumericReading.AsATermTakenOfItsArgument(taken.how()));
                case BoundOperationFact.AnswersAFormOfItsArguments(
                        var _, LinearForm<DeclaredArgument> form) ->
                        forms.add(new NumericReading.AsAFormOfItsArguments(form));
                case BoundOperationFact.ComputesANumber(
                        var _, NumericResult<DeclaredArgument> result) ->
                        arithmetic.add(new NumericReading.AsTheArithmeticItComputes(result));
                // A walk that adds up what a container holds is a way of reading the number a call
                // answered, and it is read as the account it already is rather than as a second
                // one declared beside it. A walk of another kind carries an identity and a step
                // and answers no number this reads — a join of strings, a product — so what it
                // contributes here is nothing, and that it answers this question at all is said
                // where the silences are.
                case BoundOperationFact.AccumulatesItsContainer walk -> {
                    TakenAs how = walk.takenAs();
                    if (how != null) {
                        terms.add(new NumericReading.AsATermTakenOfItsArgument(how));
                    }
                }
                // The cases a definition is written in name which argument is answered under which
                // relation between the arguments. A reader still reads the argument, so what the
                // call answers has no account of its own here — and every operation carrying one
                // today is written in the language as well, so nothing here has to tell the two
                // apart. The rest say something else about the operation: where a number runs,
                // what an operation keeps of a container, what a predicate travels through, what a
                // shift is stated through, whether every answer has a value that gives it. None
                // of them is a way of reading the number a call answered.
                case BoundOperationFact.IsDefinedByCases _,
                     BoundOperationFact.BoundsItsResult _,
                     BoundOperationFact.StatesTheOrderOfItsArguments _,
                     BoundOperationFact.ShiftsBy _,
                     BoundOperationFact.BuildsItsResultFrom _,
                     BoundOperationFact.ResultIsNoSmallerThan _,
                     BoundOperationFact.ReadsItsContainer _,
                     BoundOperationFact.IsStatedOverAProjection _,
                     BoundOperationFact.StatesItsPredicateOfEveryElement _,
                     BoundOperationFact.TurnsOnWhetherAnArgumentHolds _,
                     BoundOperationFact.MeansTheSameAsASizeOfNought _,
                     BoundOperationFact.EveryAnswerItCanGiveHasASourceValue _,
                     BoundOperationFact.SaysNothingOf _ -> { }
            }
        }
        List<NumericReading> found = new ArrayList<>(terms);
        found.addAll(forms);
        found.addAll(arithmetic);
        // Whether the operation is a kernel is a fact about the library and is asked of it, rather
        // than read off the body of the declaration behind the name.
        if (stdlib.intrinsicOf(named) == null) {
            found.add(new NumericReading.ByTheBodyTheLanguageWritesOut());
        }
        return List.copyOf(found);
    }

    /** What reads it, for a message that names the readings there are. */
    static String describe(Resolution resolution) {
        return switch (resolution) {
            case Resolution.None _ -> "nothing";
            case Resolution.One(NumericReading one) -> one.describes();
            case Resolution.Multiple(List<NumericReading> readings) -> String.join(" and ",
                    readings.stream().map(NumericReading::describes).toList());
        };
    }

    private NumericReadings() {}
}
