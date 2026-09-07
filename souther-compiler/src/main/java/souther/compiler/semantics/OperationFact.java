package souther.compiler.semantics;

/**
 * One thing that is true of one of the language's operations.
 *
 * <p>Kinds are kept apart because they are different statements, not because a reader wants them
 * apart. That an operation answers the number it was given, that it moves a value by an amount, and
 * that the sign of its answer states which of its arguments is the greater are three propositions,
 * and one operation may carry several: {@code Date.addDays} both answers a date shifted by its
 * amount and states how far the two stand apart. Folded into one case per operation, a fact could
 * only be added by widening whatever case was already there.
 *
 * <p>Sealed, so the procedures that hold these to the library's declarations answer for a kind
 * added rather than passing over it.
 *
 * <p><b>The authoring vocabulary, and nothing below the binding reads it.</b> An argument is named
 * here as {@link ArgumentRef}, a word; another operation as a {@link souther.compiler.types.ValueName},
 * a name. Neither says the library has such an operation or such an argument. What holds these to
 * the library ({@code check.OperationFactBinder}) answers with a value of another kind, in which
 * every argument and every operation has been read against its declaration, and every reader of a
 * fact reads that one. So a kind added here is a kind the binder must say what it comes to bound,
 * which the exhaustive switch there refuses to leave unsaid.
 */
public sealed interface OperationFact {

    /**
     * What the operation answers, counted, is this much of what its arguments are counted as.
     *
     * <p>Exact and unconditional: {@code count(result) = Σ cᵢ·count(argᵢ) + k} at every call.
     * {@code Decimal.fromInt(n)} is {@code n}; {@code Date.daysBetween(from, to)} is
     * {@code -from + to}; {@code Date.addDays(days, date)} is {@code date + days}. Such a call is
     * read into that form rather than given an atom of its own, so a rule about the arguments
     * settles one about the call.
     *
     * <p><b>Counted, so a date takes part.</b> What a date's count is belongs to its carrier, and
     * the arithmetic here is over counts and not over values — which is what lets a difference of
     * two dates be a number of days while neither of them is a number. Written as a relation
     * between values, the two dates would have had nothing to say and the fact would have been
     * unstateable for the operations it exists for.
     *
     * <p>Not a choice among arguments. What a choice answers is one of two values, decided by the
     * arguments, and which one it is has to be reasoned about case by case; this answers one value
     * unconditionally. Read as a choice with one candidate, every value-preserving conversion would
     * be filed under selection, and the two stop being one question the moment the library gains a
     * conversion that is not a widening.
     *
     * <p><b>Not for arithmetic the language composes.</b> {@code Int.add(a, b)} answers
     * {@code a + b} and is not declared here: {@code Terms.asOperator} reads such a call as the
     * operator it stands for, so a fact would be a second path saying what the grammar already
     * says.
     */
    record AnswersAFormOfItsArguments(
            souther.compiler.numeric.LinearForm<ArgumentRef> form)
            implements OperationFact {

        public AnswersAFormOfItsArguments {
            java.util.Objects.requireNonNull(form, "this one says what it answers");
            if (form.coefs().isEmpty()) {
                throw new IllegalArgumentException(
                        "a form of its arguments names one: a result that is a constant whatever it"
                                + " was given is a bound and not this");
            }
        }
    }

    /**
     * The sign of what the operation answers states which of its two arguments is the greater.
     *
     * <p>Zero states the equality and a negative answer the relation the other way, so one of these
     * is the whole of what such an operation says about the pair. Which relation a rule writing one
     * states then follows from where the sign stands against zero and from nothing else.
     *
     * <p>The direction is not the same for all of them: {@code compare(a, b)} is positive where
     * {@code a} is the greater, and {@code daysBetween(from, to)} counts forward from its first
     * argument, so it is positive where the second one is.
     */
    record StatesTheOrderOfItsArguments(PositiveOrder order) implements OperationFact {

        public StatesTheOrderOfItsArguments {
            java.util.Objects.requireNonNull(order, "this one says which way round");
        }
    }

    /**
     * The operation answers the value at {@code of} moved by {@code amount}, and how far it moved is
     * {@code per} of what {@code measure} counts.
     *
     * <p>What a shift states is not a bound on what it answers — a date is not a number — so it is
     * written in the one language there is about such values, which is the number a measure answers
     * of two of them. That number is what a rule over a pair of dates is written in as well, so the
     * two meet without either being restated.
     */
    record ShiftsBy(souther.compiler.types.ValueName.Stdlib.Operation measure, ArgumentRef of,
                    ArgumentRef amount, java.math.BigDecimal per) implements OperationFact {

        public ShiftsBy {
            java.util.Objects.requireNonNull(measure, "a shift is stated through a measure");
            java.util.Objects.requireNonNull(of, "and moves something");
            java.util.Objects.requireNonNull(amount, "by something");
            java.util.Objects.requireNonNull(per, "at some rate");
        }
    }

    /**
     * Something that holds of the number the operation answers, wherever it is called.
     *
     * <p>One fact per bound rather than a list in one: an operation with two bounds carries two
     * statements, and they are added and read one at a time.
     */
    record BoundsItsResult(ResultBound<ArgumentRef> bound) implements OperationFact {

        public BoundsItsResult {
            java.util.Objects.requireNonNull(bound, "this one states a bound");
        }
    }

    /**
     * The operation builds a container out of another, and this says where its elements came from
     * and how many of them there are.
     */
    record BuildsItsResultFrom(BuiltFrom<ArgumentRef> built) implements OperationFact {

        public BuildsItsResultFrom {
            java.util.Objects.requireNonNull(built, "this one says what it was built from");
        }
    }

    /**
     * The operation's result is never smaller than what {@code container} holds.
     *
     * <p>One fact per container it is no smaller than: {@code a ++ b} is as long as either half, and
     * that is two statements about one operation rather than a list inside one.
     *
     * <p>Not a statement about the elements. {@code List.append} keeps every element of both and
     * holds neither's alone, so neither is what it was built from; an insert puts in something the
     * container it read did not hold. The count survives either way, which is why these had been
     * filed among the constructions nothing is known of and their bound discarded with an element
     * statement that was never the same one.
     */
    record ResultIsNoSmallerThan(ArgumentRef container) implements OperationFact {

        public ResultIsNoSmallerThan {
            java.util.Objects.requireNonNull(container, "this one names a container");
        }
    }

    /**
     * The operation answers what {@code container} holds accumulated: started from an identity and
     * carried through one step over what it has so far and an element, both of the type it answers.
     *
     * <p>Which argument holds them is named rather than looked for. A signature says which one it
     * could be — an argument whose elements are of the type the operation answers — and says it of
     * as many arguments as fit, so an operation given two such containers has a signature that
     * admits two readings and a fact that admits one. Held to the signature where the declarations
     * are bound, so a name that does not fit is refused where it is written.
     */
    record AccumulatesItsContainer(ArgumentRef container, Accumulation how)
            implements OperationFact {

        public AccumulatesItsContainer {
            java.util.Objects.requireNonNull(container, "this one names a container");
            java.util.Objects.requireNonNull(how, "and what walking it comes to");
        }
    }

    /**
     * The operation is a predicate over what {@code container} holds, and its statement survives a
     * construction of the shapes in {@code through}.
     *
     * <p>{@code List.all} holds of any sublist of a list it holds of; {@code List.contains} does
     * not, and neither survives a mapping — what a mapped element is, the mapping alone does not
     * say.
     */
    record ReadsItsContainer(ArgumentRef container, java.util.Set<ElementShape> through)
            implements OperationFact {

        public ReadsItsContainer {
            java.util.Objects.requireNonNull(container, "this one names a container");
            through = java.util.Set.copyOf(through);
        }
    }

    /**
     * What the closure answered decides {@code aspect} of what the operation answers.
     *
     * <p><b>Said, and never read off a size.</b> That {@code List.filter} answers at most as many
     * as it walked is one fact ({@link BuiltFrom}); that the closure's truth is <em>why</em> it
     * answers fewer is another, and the first does not state the second. An operation whose result
     * is smaller for a reason of its own — a take, a distinct — is one whose closure decides
     * nothing, and a reading that took the size for the cause would say a rule inside such a call
     * decides what the call comes to.
     *
     * <p>Which is what a reader wants it for. A rule written inside a closure reaches a fork
     * testing the call only along an edge declared here: {@code List.isEmpty(List.filter(p, xs))}
     * turns on what {@code p} answered, and {@code List.isEmpty(List.map(p, xs))} does not — the
     * mapping answers one per element whatever the closure said, so a rule inside it says nothing
     * about whether the answer is empty.
     *
     * <p><b>Nothing here is about the type of what stands there.</b> What is stated is about the
     * answer, and a closure and a truth are both things that answer; which argument may carry the
     * fact is what naming it holds it to.
     *
     * <p><b>Silence is the answer for everything else.</b> An operation with no such fact is one
     * this compiler cannot follow to the answer, and a reader stops there rather than
     * guessing — which leaves a fork stating a rule of its own, and is the safe way round: a rule
     * credited to nobody leaves a measure open, and one credited to the wrong owner reports a model
     * nothing read as one read to the end.
     */
    record TurnsOnWhetherAnArgumentHolds(AnswerAspect aspect, ArgumentRef argument)
            implements OperationFact {

        public TurnsOnWhetherAnArgumentHolds {
            java.util.Objects.requireNonNull(aspect, "this one names a side of the answer");
            java.util.Objects.requireNonNull(argument, "this one names an argument");
        }
    }

    /**
     * The predicate is stated over a projection of each element, and {@code projection} is where it
     * is written.
     *
     * <p>A mapping keeps a projection when the closure copies that field from the element
     * unchanged, so the predicate holds of the mapped container exactly when it holds of what was
     * mapped, over the field it came from.
     */
    record IsStatedOverAProjection(ArgumentRef projection) implements OperationFact {

        public IsStatedOverAProjection {
            java.util.Objects.requireNonNull(projection, "this one names where it is written");
        }
    }

    /**
     * The operation states its predicate of <em>every</em> element, so what it says of a container
     * is what holds of each element a closure is handed.
     *
     * <p>The name and nothing else. Which argument is the predicate and which the container the
     * signature already answers, and how far the statement travels {@link ReadsItsContainer}
     * already does.
     */
    record StatesItsPredicateOfEveryElement() implements OperationFact {}

    /**
     * The operation asks whether a container is empty, and says the same thing as {@code size}
     * against nought.
     *
     * <p>Not what an operation does to a property but what a predicate <em>says</em>:
     * {@code List.isEmpty(xs)} and {@code List.length(xs) == 0} are one statement, so a rule writing
     * either settles a clause writing the other. Without it the two would be unrelated, which is an
     * accident of which one the author reached for.
     */
    record MeansTheSameAsASizeOfNought(souther.compiler.types.ValueName size)
            implements OperationFact {

        public MeansTheSameAsASizeOfNought {
            java.util.Objects.requireNonNull(size, "this one names the size it means");
        }
    }

    /** The operation computes a number, and this says which arithmetic and where it answers it. */
    record ComputesANumber(NumericResult<ArgumentRef> result) implements OperationFact {

        public ComputesANumber {
            java.util.Objects.requireNonNull(result, "this one says what it computes");
        }
    }

    /**
     * The operation answers one of the values it was given, and this is one case of the definition
     * it is written in: which argument it answers there, and what has to hold of the arguments for
     * that case to be reached.
     *
     * <p>The cases of one operation are exhaustive between them: their conditions cover everything
     * it can be given, so what holds in every case holds of the result. That is what makes reading
     * them sound, and it is a claim about the set rather than about any one of them — a case left
     * out does not make the others wrong, it makes a clause provable that the values can fail. So
     * they are declared as the library writes them, in the order it writes them.
     */
    record IsDefinedByCases(DefinitionCase<ArgumentRef> one) implements OperationFact {

        public IsDefinedByCases {
            java.util.Objects.requireNonNull(one, "this one states a case");
        }
    }

    /**
     * The operation answers a number taken of the one value it is given: how long a string is, how
     * many a container holds, which hour of its day a time falls in.
     *
     * <p>One list, because a reader that answers "does this rule bound a number" has to give the
     * same answer wherever it is asked. The discharge procedure keys an atom on one of these over
     * its argument's path and a partition draws a boundary on one — and where those two disagreed,
     * a rule discharged in one place was reported in the other as a rule the model does not state.
     *
     * <p><b>What it is taken as, and nothing else.</b> A size is never negative, a count is a whole
     * number, and a string of any length exists: three propositions about {@code String.length} that
     * are declared as themselves — {@link BoundsItsResult}, the operation's own result type, {@link
     * EveryAnswerItCanGiveHasASourceValue} — rather than read off the arm. Written into the arm,
     * each would be true of the operations that share it and of no others, which is what a term
     * standing for one operation and answering for a kind of operation already was (#1027).
     *
     * <p>The one value is the whole of what such a term can be about. A number taken of two
     * locations is not one of these: what it would be read off is a pair, and a term names one
     * path. An operation over several whose result the model can state says so as the form it
     * answers ({@link AnswersAFormOfItsArguments}) and is read into that form instead, which is why
     * the two cannot both be declared of one operation.
     */
    record AnswersANumberTakenOfTheOneValueItIsGiven(TakenAs how) implements OperationFact {

        public AnswersANumberTakenOfTheOneValueItIsGiven {
            java.util.Objects.requireNonNull(how, "this one says what the number is taken as");
        }
    }

    /**
     * Every number this operation could answer is one some value it could be given answers.
     *
     * <p>Not a property of how the number is taken. A string of any length is written by repeating
     * a character and a character is always to be had; every hour of the day is an hour some time
     * falls in. Two different accounts of why, and one proposition — which is why it is asked of the
     * operation rather than derived from {@link TakenAs}, where the answer would have to be the same
     * for every operation sharing an arm: a {@code List.length} and a {@code String.length} share
     * one and only the second of them has it.
     *
     * <p>What is left out is what a count over an element the language may have none of leaves. A
     * {@code Set<Bool>} is capped at two by how many booleans there are; a {@code List<T>} of one
     * needs a {@code T}, and a {@code T} nothing inhabits has none. Whether such a value exists is
     * a question about the element and not about the number, so those operations declare nothing
     * here and an edge on one of them is settled by a row rather than by an argument.
     *
     * <p>Beside the building and not the same statement as it. That a value answering the number
     * exists is this; that the generator can write one down is what the generator answers, and an
     * operation may satisfy the first while the second is held back by how much it is worth
     * building.
     */
    record EveryAnswerItCanGiveHasASourceValue() implements OperationFact {}

    /**
     * There is nothing to say of this operation under {@code subject}.
     *
     * <p>A decision and not a gap. An operation the library declares is in range of whatever its
     * signature puts it in range of, and a silence there says two things at once — that nothing is
     * true of it, and that nobody looked. {@code List.distinctBy} was credited by neither check for
     * exactly that reason, with nothing said about the missing row.
     *
     * <p>So the absence is declared beside the presences, and the reason is written where it is
     * declared. What the reason is about is the operation: a map's keys are not its values, a
     * whole-minute count between two moments does not state their order, what {@code a + b} answers
     * may be anywhere.
     */
    record SaysNothingOf(OperationSubject subject) implements OperationFact {

        public SaysNothingOf {
            java.util.Objects.requireNonNull(subject, "a silence is about something");
        }
    }
}
