package souther.compiler.check;

import souther.compiler.core.CompleteSignature;
import souther.compiler.core.DeclaredOperation;
import souther.compiler.numeric.LinearForm;
import souther.compiler.semantics.ArgumentRef;
import souther.compiler.semantics.ArgumentsStand;
import souther.compiler.semantics.Arithmetic;
import souther.compiler.semantics.BuiltFrom;
import souther.compiler.semantics.Combinator;
import souther.compiler.semantics.DefinitionCase;
import souther.compiler.semantics.NumericResult;
import souther.compiler.semantics.OperationFact;
import souther.compiler.semantics.OperationFacts;
import souther.compiler.semantics.ResultBound;
import souther.compiler.semantics.TakenAs;
import souther.compiler.stdlib.Stdlib;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.ValueName;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Holds what is declared of the language's operations to what the library declares, and answers
 * with the facts as bound.
 *
 * <p>A fact names an argument of an operation, and what an operation's arguments are is the
 * library's to say. Where the two disagree there is nothing to be done at a call — the fact is
 * about an argument that is not there, or is not the kind of thing the fact is about — so it is
 * said before any call is read rather than met as a missing answer at whichever reader arrives
 * first.
 *
 * <p><b>The one reader of the authoring vocabulary below the declarations, and the one place an
 * {@link ArgumentRef} becomes a position.</b> Everything below the binding holds a
 * {@link BoundOperationFact}, in which every operation is a {@link DeclaredOperation} and every
 * argument a {@link DeclaredArgument}; what those are made of — a word, a name, a signature, what
 * {@link Combinators} says an operation hands its closure — is read here and nowhere after.
 * {@code OnlyTheBinderReadsTheAuthoringVocabularyTest} counts that.
 *
 * <p><b>Over the declarations and not over what a reader asked for.</b> Bound one fact at a time as
 * it was looked up, a fact nothing looked up was a fact nothing checked, and how much of the
 * declaration was validated depended on which consumers a compilation happened to have. This walks
 * the whole list, so a fact declared is a fact held to the library whether or not anything reads
 * it.
 *
 * <p><b>Each declaration read once, before the switch.</b> The operation a fact is about is read
 * against the library into a {@link CompleteSignature} for every declaration, outside the switch,
 * and every arm is handed that reading. So being declared is what holds a fact to the library
 * rather than being a kind that happens to name an argument, and no arm reads the operation a
 * second time to ask where an argument is.
 *
 * <p><b>The switch is an expression.</b> Every arm yields the bound fact its kind comes to, so a
 * kind of fact added to the declarations is a kind that does not compile until this says what it
 * is once bound — an empty arm was how a kind went through held to nothing, and there is no empty
 * arm in an expression.
 *
 * <p>The second way into {@link CompleteSignature#ofDeclaration} beside {@link Preserved}, and on
 * the same warrant: a library declaration, read whole. {@code Preserved} is what a representation
 * keeps standing, which is a policy about representations and not about which operations the
 * library has, so a fact about an operation nothing keeps standing could not borrow its reading
 * from there.
 */
final class OperationFactBinder {

    /**
     * Holds every fact of {@code declared} to the library, and answers with what the walk came to.
     *
     * <p>The source is a parameter so that what this covers can be asked of it with a source of
     * one's own. Reading {@link OperationFacts#declarations()} directly, a test could show that the
     * facts there are valid and not that a fact added later would be visited at all.
     *
     * <p>Two passes and not one. Each fact is held to its own declaration as it is met; what one
     * fact may not say beside another — that a number is read by one representation — is asked of
     * all of them together once every one is bound, since a question about the set cannot be
     * answered from the order the declarations happen to come in.
     */
    static BoundOperationFacts bindAll(Stdlib stdlib, List<OperationFacts.Declared> declared) {
        List<BoundOperationFact> bound = new ArrayList<>();
        for (OperationFacts.Declared each : declared) {
            CompleteSignature declaration = declaredSignature(stdlib, each.operation());
            bound.add(bind(stdlib, declaration, each.fact()));
        }
        BoundOperationFacts facts = new BoundOperationFacts(bound);
        holdEachNumberToOneReading(stdlib, facts);
        return facts;
    }

    /** The same, over what the language declares. */
    static BoundOperationFacts bindAll(Stdlib stdlib) {
        return bindAll(stdlib, OperationFacts.declarations());
    }

    /** One fact held to the declaration it is about. No default: a kind of fact added is a kind
     *  this has to say how to hold and what it comes to, rather than one that passes through
     *  unchecked because nothing here mentions it. */
    private static BoundOperationFact bind(Stdlib stdlib, CompleteSignature declaration,
                                           OperationFact fact) {
        DeclaredOperation operation = declaration.declaring();
        return switch (fact) {
            case OperationFact.AnswersAFormOfItsArguments answers ->
                    new BoundOperationFact.AnswersAFormOfItsArguments(operation,
                            holdAFormOfItsArguments(declaration, answers.form()));
            // Both arguments, because which is the greater and which the lesser are one
            // statement and a signature could disagree with either half.
            case OperationFact.StatesTheOrderOfItsArguments states ->
                    new BoundOperationFact.StatesTheOrderOfItsArguments(operation,
                            holdToTheDeclaration(declaration, states.order().greater(), null,
                                    TypeRequirement.ANY,
                                    "the argument a positive answer names as greater"),
                            holdToTheDeclaration(declaration, states.order().lesser(), null,
                                    TypeRequirement.ANY,
                                    "the argument a positive answer names as lesser"));
            case OperationFact.ShiftsBy shifts -> holdShift(stdlib, declaration, shifts);
            case OperationFact.BoundsItsResult bounded ->
                    new BoundOperationFact.BoundsItsResult(operation,
                            holdBound(declaration, bounded.bound()));
            case OperationFact.BuildsItsResultFrom builds ->
                    new BoundOperationFact.BuildsItsResultFrom(operation,
                            holdBuilding(declaration, builds));
            case OperationFact.ResultIsNoSmallerThan bounded ->
                    new BoundOperationFact.ResultIsNoSmallerThan(operation,
                            holdToTheDeclaration(declaration, bounded.container(),
                                    new ArgumentRef.TheContainer(), TypeRequirement.CONTAINER,
                                    "a container the result is no smaller than"));
            case OperationFact.ReadsItsContainer reads ->
                    new BoundOperationFact.ReadsItsContainer(operation,
                            holdToTheDeclaration(declaration, reads.container(),
                                    new ArgumentRef.TheContainer(), TypeRequirement.CONTAINER,
                                    "the container a predicate reads"),
                            reads.through());
            case OperationFact.TurnsOnWhetherAnArgumentHolds turns ->
                    new BoundOperationFact.TurnsOnWhetherAnArgumentHolds(operation, turns.aspect(),
                            holdToTheDeclaration(declaration, turns.argument(),
                                    new ArgumentRef.TheClosure(), TypeRequirement.ANY,
                                    "the argument the answer turns on"));
            case OperationFact.IsStatedOverAProjection over ->
                    new BoundOperationFact.IsStatedOverAProjection(operation,
                            holdToTheDeclaration(declaration, over.projection(),
                                    new ArgumentRef.TheClosure(), TypeRequirement.CLOSURE,
                                    "the projection a predicate is stated over"));
            // Names no argument, but it does name another operation — and what a reader does
            // with it is write a call of that one where a call of this one stands. So the two
            // declarations are held to each other.
            case OperationFact.MeansTheSameAsASizeOfNought means ->
                    holdSizeEquivalence(stdlib, declaration, means.size());
            // Neither of these names anything beyond the operation it is about — a silence names
            // nothing by definition — so there is nothing about one to hold to a signature beyond
            // the declaration every fact is held to above. What each comes to bound is the fact
            // about that declaration.
            case OperationFact.StatesItsPredicateOfEveryElement _ ->
                    new BoundOperationFact.StatesItsPredicateOfEveryElement(operation);
            case OperationFact.SaysNothingOf silence ->
                    new BoundOperationFact.SaysNothingOf(operation, silence.subject());
            // Stated of the number an operation answers, so an operation that answers none is
            // one the proposition is not about. Waved through, it was a fact anything could
            // carry (#1027).
            case OperationFact.EveryAnswerItCanGiveHasASourceValue _ -> {
                holdTheResultToTheDeclaration(declaration, TypeRequirement.NUMBER,
                        "what every answer of it has a value for");
                yield new BoundOperationFact.EveryAnswerItCanGiveHasASourceValue(operation);
            }
            case OperationFact.AnswersANumberTakenOfTheOneValueItIsGiven taken ->
                    holdTakenOf(declaration, taken.how());
            case OperationFact.AccumulatesItsContainer accumulates ->
                    new BoundOperationFact.AccumulatesItsContainer(operation,
                            holdAccumulation(declaration, accumulates.container()),
                            accumulates.how());
            case OperationFact.ComputesANumber computes ->
                    new BoundOperationFact.ComputesANumber(operation,
                            holdNumericResult(stdlib, declaration, computes.result()));
            case OperationFact.IsDefinedByCases defined ->
                    new BoundOperationFact.IsDefinedByCases(operation,
                            holdCase(declaration, defined.one()));
        };
    }

    /**
     * Holds one rule to the operation it is about: the argument it names is one the declaration
     * has, and what stands there is what the rule requires. A rule naming a part of something the
     * signature says the operation does not hand is caught by the word itself; one that writes a
     * position the signature already answers is caught here, since two answers to one question are
     * what come apart later.
     *
     * <p><b>The one place a word becomes a position.</b> What {@link ArgumentRef.TheContainer} and
     * {@link ArgumentRef.TheClosure} are positions of is read off the library's own declaration
     * ({@link Combinators}), here and nowhere below: what comes back is a {@link DeclaredArgument},
     * which carries the position, and a reader below has that and no word to resolve.
     */
    static DeclaredArgument holdToTheDeclaration(CompleteSignature declaration, ArgumentRef at,
                                                 ArgumentRef derived, TypeRequirement required,
                                                 String role) {
        ValueName.Stdlib library = (ValueName.Stdlib) declaration.declaring().operation();
        List<Type> params = declaration.params();
        int position = positionIn(at, library);
        if (position < 0 || position >= params.size()) {
            throw new IllegalStateException(library.qualified() + " takes " + params.size()
                    + " argument(s), and the rule about " + role + " reads argument "
                    + (position + 1));
        }
        Type stands = params.get(position);
        if (!required.admits(stands)) {
            throw new IllegalStateException("argument " + (position + 1) + " of "
                    + library.qualified() + " is " + Type.show(stands) + ", not " + required
                    + "; it is named as " + role);
        }
        if (at instanceof ArgumentRef.At && derived != null && Combinators.of(library) != null
                && positionIn(derived, library) == position) {
            throw new IllegalStateException("the rule about " + role + " for "
                    + library.qualified()
                    + " writes the argument its signature already answers — say which part it is"
                    + " rather than where, so the two cannot come apart");
        }
        return new DeclaredArgument(declaration.declaring(), position, stands);
    }

    /**
     * Which parameter of {@code operation} {@code ref} names.
     *
     * <p>A written position is itself. The two that are named by the part they play are the
     * library's to say, and an operation whose signature hands its closure nothing a container
     * holds has neither — a fact naming one of them there is a fact about an argument that is not
     * there, and it is said rather than answered with a number that would be wrong.
     */
    private static int positionIn(ArgumentRef ref, ValueName operation) {
        return switch (ref) {
            case ArgumentRef.At at -> at.position();
            case ArgumentRef.TheContainer _ -> handing(operation, "the container").containerArg();
            case ArgumentRef.TheClosure _ -> handing(operation, "the closure").closureArg();
        };
    }

    private static Combinator handing(ValueName operation, String part) {
        Combinator handed = Combinators.of(operation);
        if (handed == null) {
            throw new IllegalStateException("a rule about " + operation + " names " + part
                    + " of what it hands its closure, and its signature says it hands one nothing"
                    + " a container holds");
        }
        return handed;
    }

    /**
     * Holds a declared form to the library: what it answers counts, and so does every argument it
     * is written over.
     *
     * <p>Both ends, because the fact is an equation between them —
     * {@code count(result) = Σ cᵢ·count(argᵢ) + k}. Held of the arguments alone it was half a
     * statement: {@code List.take(n, xs)} declared to answer the number of its first argument
     * passed, that argument being an {@code Int}, while what it answers is a list and has no count
     * for the equation to be about.
     *
     * <p>Counted rather than a number, because that is what the fact says. A date is no number and
     * counts days. And counted is what the discharge check needs of every part to carry a form —
     * a carrier that counts has the coordinate it reasons over — so a form held here is a form
     * that check carries, and it does not ask again.
     */
    private static LinearForm<DeclaredArgument> holdAFormOfItsArguments(
            CompleteSignature declaration, LinearForm<ArgumentRef> form) {
        holdTheResultToTheDeclaration(declaration, TypeRequirement.COUNTED,
                "what a form of its arguments is about");
        LinearForm<DeclaredArgument> bound = LinearForm.constant(form.constant());
        for (Map.Entry<ArgumentRef, BigDecimal> each : form.coefs().entrySet()) {
            DeclaredArgument argument = holdToTheDeclaration(declaration, each.getKey(), null,
                    TypeRequirement.COUNTED, "an argument the result is a form of");
            bound = bound.plus(LinearForm.<DeclaredArgument>atom(argument).times(each.getValue()));
        }
        return bound;
    }

    /**
     * As {@link #holdToTheDeclaration}, for a rule stating a shift through a measure: the amount is
     * a number, the value shifted is of the type the measure counts, and the measure counts two of
     * what the operation answers. A rule pairing an operation with a measure of something else
     * would state a relation between two values that have none.
     */
    private static BoundOperationFact.ShiftsBy holdShift(Stdlib stdlib,
                                                         CompleteSignature declaration,
                                                         OperationFact.ShiftsBy shift) {
        ValueName.Stdlib library = (ValueName.Stdlib) declaration.declaring().operation();
        DeclaredArgument amount = holdToTheDeclaration(declaration, shift.amount(), null,
                TypeRequirement.NUMBER, "the amount a shift moves by");
        Stdlib.Entry counts = stdlib.entry(shift.measure());
        if (counts == null) {
            throw new IllegalStateException("the rule about " + library.qualified()
                    + " counts through " + shift.measure().qualified()
                    + ", which the library does not declare");
        }
        List<Type> counted = counts.signature().params();
        if (counted.size() != 2 || !NumericAnswers.isANumber(counts.signature().result())
                || !counted.get(0).equals(declaration.result())
                || !counted.get(1).equals(declaration.result())) {
            throw new IllegalStateException(shift.measure().qualified()
                    + " does not count two of what " + library.qualified()
                    + " answers apart as a number");
        }
        DeclaredArgument moved = holdToTheDeclaration(declaration, shift.of(), null,
                TypeRequirement.ANY, "the value a shift moves from");
        // Not a requirement on the type, which is why it is stated here rather than passed as one.
        // What this asks is that two positions of one signature stand at the same type, and nothing
        // about a type on its own answers that — a requirement able to say it would be one carrying
        // the signature it was written for.
        if (!moved.stands().equals(declaration.result())) {
            throw new IllegalStateException("the value " + library.qualified()
                    + " shifts is " + Type.show(moved.stands()) + " and what it answers is "
                    + Type.show(declaration.result())
                    + ", so what it moves is not what the measure counts");
        }
        return new BoundOperationFact.ShiftsBy(declaration.declaring(),
                declaredSignature(stdlib, shift.measure()).declaring(), moved, amount, shift.per());
    }

    /** As {@link #holdToTheDeclaration}, for the arguments a case names: the one it answers, and
     *  the two sides of each condition it is reached under. */
    private static DefinitionCase<DeclaredArgument> holdCase(CompleteSignature declaration,
                                                             DefinitionCase<ArgumentRef> one) {
        holdTheResultToTheDeclaration(declaration, TypeRequirement.NUMBER,
                "what a case of the definition answers");
        DeclaredArgument answers = holdCaseArgument(declaration, one.answers());
        List<ArgumentsStand<DeclaredArgument>> given = new ArrayList<>();
        for (ArgumentsStand<ArgumentRef> stands : one.given()) {
            given.add(new ArgumentsStand<>(holdCaseArgument(declaration, stands.left()),
                    stands.rel(), holdCaseArgument(declaration, stands.right())));
        }
        return new DefinitionCase<>(answers, given);
    }

    private static DeclaredArgument holdCaseArgument(CompleteSignature declaration,
                                                     ArgumentRef named) {
        return holdToTheDeclaration(declaration, named, null, TypeRequirement.NUMBER,
                "an argument a case of the definition names");
    }

    /** As {@link #holdToTheDeclaration}, for the arguments a bound names: the one the result is
     *  bounded against, and the one a condition on the rule reads. Each is a separate claim about a
     *  separate argument. */
    private static ResultBound<DeclaredArgument> holdBound(CompleteSignature declaration,
                                                           ResultBound<ArgumentRef> bound) {
        holdTheResultToTheDeclaration(declaration, TypeRequirement.NUMBER,
                "what a bound on the result holds of");
        DeclaredArgument against = bound.against() == null ? null
                : holdBoundArgument(declaration, bound.against());
        ResultBound.Provided<DeclaredArgument> provided = switch (bound.provided()) {
            case ResultBound.Provided.Always<ArgumentRef> _ -> new ResultBound.Provided.Always<>();
            case ResultBound.Provided.ConstantAboveZero<ArgumentRef> constant ->
                    new ResultBound.Provided.ConstantAboveZero<>(
                            holdBoundArgument(declaration, constant.argument()));
        };
        return new ResultBound<>(against, bound.offset(), bound.rel(), provided);
    }

    private static DeclaredArgument holdBoundArgument(CompleteSignature declaration,
                                                      ArgumentRef named) {
        return holdToTheDeclaration(declaration, named, null, TypeRequirement.NUMBER,
                "an argument a bound on the result names");
    }

    /**
     * Holds a building to the library: every argument the lineage names is a container the
     * declaration has.
     *
     * <p>Every argument and not the one source. A lineage whose elements come from more than one
     * place names each of them, and each is a claim about an argument; held for the source alone,
     * a second argument named by an alternative was one nothing had read.
     */
    private static BuiltFrom<DeclaredArgument> holdBuilding(
            CompleteSignature declaration, OperationFact.BuildsItsResultFrom builds) {
        Map<ArgumentRef, DeclaredArgument> held = new HashMap<>();
        return builds.built().withArguments(named -> held.computeIfAbsent(named,
                each -> holdToTheDeclaration(declaration, each, new ArgumentRef.TheContainer(),
                        TypeRequirement.CONTAINER, "the container something is built from")));
    }

    /**
     * As {@link #holdToTheDeclaration}, for the arithmetic an operation computes: it takes as many
     * arguments as the row hands over, and it answers its number where the row says it does.
     *
     * <p>The result position is the half a signature can disagree with silently. A row saying the
     * number arrives in the case carrying {@code Int} is read at an arm, and an arm that never
     * matches is an arm that reports nothing — so a union that gained a case, or lost the one the
     * row names, would leave the operation with a meaning no program reaches and no diagnostic
     * anywhere. Held here, before any call is read.
     */
    private static NumericResult<DeclaredArgument> holdNumericResult(
            Stdlib stdlib, CompleteSignature declaration, NumericResult<ArgumentRef> rule) {
        DeclaredOperation operation = declaration.declaring();
        Type answers = NumericAnswers.in(declaration.result());
        List<Arithmetic.Reads> reads = rule.computes().reads();
        if (declaration.params().size() != reads.size()) {
            throw new IllegalStateException(operation + " takes " + declaration.params().size()
                    + " argument(s), and the arithmetic written for it reads " + reads.size());
        }
        for (int i = 0; i < reads.size(); i++) {
            if (!heldBy(stdlib, reads.get(i), declaration.params().get(i), answers)) {
                throw new IllegalStateException("argument " + (i + 1) + " of " + operation
                        + " is " + Type.show(declaration.params().get(i))
                        + ", which the arithmetic written for it reads as " + reads.get(i));
            }
        }
        switch (rule.at()) {
            case NumericResult.Answered.Directly _ ->
                    holdTheResultToTheDeclaration(declaration, TypeRequirement.NUMBER,
                            "where the arithmetic it computes is answered");
            case NumericResult.Answered.InTheCaseCarrying(Type carried) -> {
                if (!(declaration.result() instanceof Type.Union(Set<TypeSymbol> members))) {
                    throw new IllegalStateException(operation + " answers "
                            + Type.show(declaration.result())
                            + ", which has no case for the number it computes to arrive in");
                }
                if (!carried.equals(answers)) {
                    throw new IllegalStateException(operation + " answers no case carrying "
                            + Type.show(carried));
                }
                if (rule.unless() == null) {
                    throw new IllegalStateException(operation + " answers its number as one case"
                            + " of a union, so when the other case comes back is what that case"
                            + " means and is not written down");
                }
                // The condition names no case, so it says what every case that is not the
                // number's says — which is one statement only where there is one such case. A
                // union that gained a third would have an arm establishing a condition it was
                // not taken under, which is a wrong fact rather than a missing one, and nothing
                // downstream could tell: an arm is read the same way whichever case it names.
                // Where a second failure is wanted, the condition is what has to name its case.
                if (members.size() != 2) {
                    throw new IllegalStateException(operation + " answers "
                            + members.size() + " cases, and when it answers no number is"
                            + " written as one condition — which says what one other case"
                            + " means and cannot say what several do");
                }
            }
        }
        NumericResult.TheOtherCaseWhen<DeclaredArgument> unless = rule.unless() == null ? null
                : new NumericResult.TheOtherCaseWhen<>(
                        holdToTheDeclaration(declaration, rule.unless().argument(), null,
                                TypeRequirement.NUMBER, "the argument a failure is decided by"),
                        rule.unless().op(), rule.unless().than());
        return new NumericResult<>(rule.at(), rule.computes(), unless);
    }

    /**
     * Whether the argument declared {@code at} is what the row says that position reads, for an
     * operation answering {@code answered}.
     *
     * <p>Here and not on {@link Arithmetic.Reads}, which says what a position is and stops there.
     * Holding one of those to a declaration is a question about the library, and this is the reader
     * that has the library — the same reader that holds the operation's arity and its result to it.
     *
     * <p>Two of them are answered from the row itself: the number the operation answers is the one
     * its result carries, and a scale is a count. The third is answered from a declaration, because
     * there is nothing about a rounding policy that a type says of itself — and reading it off a
     * name written here would be a second answer to which type it is, which is what ADR-0087 ends.
     */
    private static boolean heldBy(Stdlib stdlib, Arithmetic.Reads reads, Type at, Type answered) {
        return switch (reads) {
            case THE_NUMBER_IT_ANSWERS -> at.equals(answered);
            case A_SCALE -> at == Type.Prim.INT;
            case A_ROUNDING_MODE -> at.equals(theRoundingPolicyTheLibraryDeclares(stdlib));
        };
    }

    /** Which library operation the rounding policy is read off, and where in its arguments. */
    private static final ValueName.Stdlib ROUNDING_POLICY_ANCHOR =
            ValueName.Stdlib.operation("Decimal", "round");

    /** {@code round(scale, mode, d)} — the second of them. */
    private static final int ROUNDING_POLICY_ARGUMENT = 1;

    /**
     * The type the library declares for a rounding policy, taken from the operation that declares
     * one and read as whatever that operation declares there.
     *
     * <p>Whatever it declares there, and never a type this checks against. A rule that said the
     * anchor's argument must be {@code RoundingMode} would be the spelling back again, one operation
     * further along. What is held is that two declarations agree: {@code Decimal.divide} takes at
     * its policy position the type {@code Decimal.round} takes at its own, and either of them
     * drifting alone fails this. Both moving to a new policy type together passes, and should —
     * that is the library being redesigned rather than the table and the library disagreeing.
     *
     * <p>The anchor is a choice and is written down as one. What it is not is a second definition
     * of which type the policy is: the library's declaration remains the only one.
     *
     * @throws IllegalStateException where the anchor no longer declares the argument it is read off
     */
    private static Type theRoundingPolicyTheLibraryDeclares(Stdlib stdlib) {
        List<Type> params = holdTheOperationToTheLibrary(stdlib, ROUNDING_POLICY_ANCHOR)
                .signature().params();
        if (params.size() <= ROUNDING_POLICY_ARGUMENT) {
            throw new IllegalStateException(ROUNDING_POLICY_ANCHOR.qualified() + " takes "
                    + params.size() + " argument(s), and the rounding policy every arithmetic over"
                    + " one is held to is read off argument " + (ROUNDING_POLICY_ARGUMENT + 1)
                    + " of it");
        }
        return params.get(ROUNDING_POLICY_ARGUMENT);
    }

    /** The library operation {@code operation} names. Every fact is declared of one, so the name
     *  says which library and which operation rather than a spelling a reader would have to take
     *  apart.
     *
     *  @throws IllegalStateException where the name is no library operation. */
    static ValueName.Stdlib.Operation theLibraryOperation(ValueName operation) {
        if (!(operation instanceof ValueName.Stdlib.Operation library)) {
            throw new IllegalStateException("a fact is declared of " + operation
                    + ", which is not a library operation");
        }
        return library;
    }

    /**
     * The library's declaration of {@code operation}, or a build that does not start.
     *
     * <p>What every fact owes, whatever else it says. A fact is a proposition about an operation,
     * so an operation the library does not have is a fact about nothing — and that is true of a
     * fact naming no argument as much as of one that names three.
     */
    static Stdlib.Entry holdTheOperationToTheLibrary(Stdlib stdlib, ValueName operation) {
        ValueName.Stdlib.Operation library = theLibraryOperation(operation);
        Stdlib.Entry entry = stdlib.entry(library);
        if (entry == null) {
            throw new IllegalStateException("a fact is declared of " + library.qualified()
                    + ", which the library does not declare");
        }
        return entry;
    }

    /** The library's declaration of {@code operation}, read whole. */
    static CompleteSignature declaredSignature(Stdlib stdlib, ValueName operation) {
        Stdlib.Entry entry = holdTheOperationToTheLibrary(stdlib, operation);
        return CompleteSignature.ofDeclaration(operation, entry.signature().params(),
                entry.signature().result());
    }

    /**
     * Holds an emptiness check and the size it is said to mean to each other's declarations, and
     * answers with the two as the library declares them.
     *
     * <p>What a reader does with this fact is write the second call where the first stands, keeping
     * the arguments. So what has to hold is that the arguments the first takes are the ones the
     * second takes, and that the two answer the kinds of thing the rewrite turns into each other: a
     * truth on one side, a number to compare against nought on the other. Two operations of one
     * argument each is not enough — a check on strings and a length of lists agree on how many
     * arguments they take and on nothing else.
     */
    private static BoundOperationFact.MeansTheSameAsASizeOfNought holdSizeEquivalence(
            Stdlib stdlib, CompleteSignature asks, ValueName size) {
        ValueName operation = asks.declaring().operation();
        CompleteSignature counts = declaredSignature(stdlib, size);
        if (asks.params().size() != 1 || asks.result() != Type.BOOL) {
            throw new IllegalStateException(theLibraryOperation(operation).qualified()
                    + " is declared to say whether one container is empty, and it takes "
                    + asks.params().size() + " argument(s) and answers "
                    + Type.show(asks.result()));
        }
        if (counts.params().size() != 1 || counts.result() != Type.INT) {
            throw new IllegalStateException(theLibraryOperation(size).qualified()
                    + " is named as the size " + theLibraryOperation(operation).qualified()
                    + " means, and it takes " + counts.params().size()
                    + " argument(s) and answers " + Type.show(counts.result()));
        }
        if (!sameShape(asks.params().get(0), counts.params().get(0),
                new HashMap<>(), new HashMap<>())) {
            throw new IllegalStateException(theLibraryOperation(operation).qualified() + " asks of "
                    + Type.show(asks.params().get(0)) + " and "
                    + theLibraryOperation(size).qualified() + " counts "
                    + Type.show(counts.params().get(0))
                    + ", so a call of the first is no call of the second");
        }
        return new BoundOperationFact.MeansTheSameAsASizeOfNought(asks.declaring(),
                counts.declaring());
    }

    /**
     * Whether the two are the same shape, telling type variables apart only by where they stand.
     *
     * <p>{@code List<'a>} and {@code List<'b>} are one shape and {@code List<'a>} and
     * {@code List<Int>} are not, which is what a rewrite between two declarations needs and what
     * unifying them does not answer: those two unify, by deciding that {@code 'a} is {@code Int},
     * and a rewrite is not free to decide anything. The pairing is carried both ways so that two
     * variables on one side cannot both stand for one on the other.
     */
    private static boolean sameShape(Type left, Type right, Map<String, String> paired,
                                     Map<String, String> back) {
        // A variable of an application, which no declaration holds: this compares what two
        // declarations state. One arriving here is a caller having handed over something else, and
        // answering "a different shape" would report that as the two operations disagreeing.
        if (left instanceof Type.MetaVar || right instanceof Type.MetaVar) {
            throw new IllegalStateException("a declared signature is being compared with "
                    + Type.show(left instanceof Type.MetaVar ? left : right)
                    + ", which belongs to an application rather than to a declaration");
        }
        return switch (left) {
            case Type.Var l when right instanceof Type.Var r ->
                    r.name().equals(paired.computeIfAbsent(l.name(), _ -> r.name()))
                            && l.name().equals(back.computeIfAbsent(r.name(), _ -> l.name()));
            case Type.ListOf l when right instanceof Type.ListOf r ->
                    sameShape(l.element(), r.element(), paired, back);
            case Type.SetOf l when right instanceof Type.SetOf r ->
                    sameShape(l.element(), r.element(), paired, back);
            case Type.OptionOf l when right instanceof Type.OptionOf r ->
                    sameShape(l.element(), r.element(), paired, back);
            case Type.MapOf l when right instanceof Type.MapOf r ->
                    sameShape(l.key(), r.key(), paired, back)
                            && sameShape(l.value(), r.value(), paired, back);
            case Type.FnOf l when right instanceof Type.FnOf r ->
                    sameShapes(l.params(), r.params(), paired, back)
                            && sameShape(l.result(), r.result(), paired, back);
            case Type.TupleOf l when right instanceof Type.TupleOf r ->
                    sameShapes(l.elements(), r.elements(), paired, back);
            // Everything else a declaration can hold stands for itself: a primitive, a declaration,
            // a union of them, and a variable that did not pair with one above. Being the same
            // shape is being the same type.
            case Type.Leaf _ -> left.equals(right);
            default -> false;
        };
    }

    private static boolean sameShapes(List<Type> left, List<Type> right, Map<String, String> paired,
                                      Map<String, String> back) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            if (!sameShape(left.get(i), right.get(i), paired, back)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Holds what a fact says of the result to the declaration.
     *
     * <p>Beside {@link #holdToTheDeclaration} and for the half it cannot reach. That one names an
     * argument, so a fact whose proposition mentions the result had nothing to say it with, and
     * each kind that needed the result said it its own way or not at all. Improvising a check per
     * kind defaults to omitting it.
     *
     * <p>Which requirement is the caller's, since what a fact says of the result is the fact's. A
     * form is about a count and a bound about a number, and the difference between those two is a
     * difference between the propositions and not between two ways of holding one.
     */
    private static void holdTheResultToTheDeclaration(CompleteSignature declaration,
                                                      TypeRequirement required, String role) {
        Type result = declaration.result();
        if (!required.admits(result)) {
            throw new IllegalStateException("what "
                    + ((ValueName.Stdlib) declaration.declaring().operation()).qualified()
                    + " answers is " + Type.show(result) + ", not " + required
                    + "; it is named as " + role);
        }
    }

    /**
     * Holds a declared account of what an operation takes of the one value it is given to the
     * operation: it takes exactly one value, since what such a term is read off is one location and
     * a term names one path; and it answers a number, since a boundary is drawn on one.
     *
     * <p>Two of the four things such an account is held to, the two that are about this fact and
     * this declaration alone. The other two are about the operation — that its number is read by
     * one representation, and that what it is taken of is the shape the account is written for —
     * and are asked once every fact is bound ({@link #holdEachNumberToOneReading}), in that order:
     * an account that does not fit the operation is still an account of a number some other
     * representation may already read, and asked the other way round the exclusivity would be
     * reachable only through accounts that happen to fit.
     */
    private static BoundOperationFact.AnswersANumberTakenOfTheOneValueItIsGiven holdTakenOf(
            CompleteSignature declaration, TakenAs how) {
        String named = ((ValueName.Stdlib) declaration.declaring().operation()).qualified();
        if (declaration.params().size() != 1) {
            throw new IllegalStateException(named + " takes " + declaration.params().size()
                    + " arguments, and a number taken of the one value an operation is given is"
                    + " taken of one");
        }
        // A number and not a number at one case of a union. A term names one path and stands for
        // what the operation answered there, and what an operation answering `Int | NotANumber`
        // answers at that path is the union — which case it is in is a question this account has no
        // room for. Narrower than the range of whatever asks for such an account, and deliberately:
        // what may be declared and what is asked about are two ranges.
        holdTheResultToTheDeclaration(declaration, TypeRequirement.NUMBER,
                "what a term of its answer is about");
        // The one value, as the declaration has it, carried so that what the account is held to
        // fit is read off the bound fact and not off the declaration a second time.
        DeclaredArgument of = holdToTheDeclaration(declaration, new ArgumentRef.At(0), null,
                TypeRequirement.ANY, "the one value a number is taken of");
        return new BoundOperationFact.AnswersANumberTakenOfTheOneValueItIsGiven(
                declaration.declaring(), of, declaration.result(), how);
    }

    /**
     * Holds every operation the library declares to having its number read by at most one
     * representation, and then each account of a number taken of one value to fitting what the
     * operation takes.
     *
     * <p>Asked of the bound facts together and after every one is bound, because the first is a
     * claim about the set: an account declared beside a form is two readings whichever was written
     * first. Asked as "is there already a form, an arithmetic, a body", it is one condition per
     * representation there is, so a fourth has to be named in each of them; asked as how many
     * readings the operation has ({@link NumericReadings}), a representation added is refused
     * against every existing one without being paired with any of them.
     *
     * <p><b>Over the library's operations, and not over the facts of one kind.</b> Two readings of
     * one number are an invalid definition of the operation whichever two they are — a form beside
     * an arithmetic, a walk that adds beside a form — so the population the claim is about is every
     * operation there is, and a walk that started from the accounts of one kind would hold only
     * the pairs that kind is half of. What is refused here is what
     * {@link NumericReadings.Resolution.Multiple} is defined to be, before any reader can meet it.
     *
     * <p>The shape second, since a shape that does not fit is a fact about one account, and the
     * exclusivity is a fact about the operation whatever account was written for it.
     */
    private static void holdEachNumberToOneReading(Stdlib stdlib, BoundOperationFacts facts) {
        for (ValueName.Stdlib.Operation operation : stdlib.entries().keySet()) {
            NumericReadings.Resolution read = NumericReadings.resolve(stdlib, facts, operation);
            if (read instanceof NumericReadings.Resolution.Multiple) {
                throw new IllegalStateException("the number " + operation.qualified()
                        + " answers is read as " + NumericReadings.describe(read)
                        + ", and one numeric call is read by one representation — which of them a"
                        + " report showed would be whichever reader arrived");
            }
        }
        for (BoundOperationFact fact : facts.all()) {
            if (!(fact instanceof BoundOperationFact.AnswersANumberTakenOfTheOneValueItIsGiven
                    taken)) {
                continue;
            }
            // What it takes it of is the shape the account is written for — a count is taken of
            // something that holds things, a magnitude of the operation's own kind of number.
            // Read off the bound fact, which carries the one argument and the answer as the
            // declaration had them.
            Type source = taken.of().stands();
            if (!taken.how().takenOf(source, taken.answers())) {
                throw new IllegalStateException(theLibraryOperation(taken.operation().operation())
                        .qualified() + " is declared to answer "
                        + taken.how().getClass().getSimpleName() + " of a " + Type.show(source)
                        + ", which is not what that is taken of");
            }
        }
    }

    /**
     * Holds an accumulation to the library: the argument it names holds elements, and they are of
     * the type the operation answers.
     *
     * <p>Both halves, because an accumulation carries what it has so far in the answer's own type.
     * A step over what it has and an element is written over two values of one type, so an argument
     * whose elements are something else is one this walk could not be over, and the identity it
     * starts from would be a value of a type nothing here names.
     *
     * <p>Which argument is named rather than searched for. A signature says of as many arguments as
     * fit that they could be the one, and an operation given two containers of what it answers has
     * a signature admitting two walks; the fact says which, and this says whether the signature
     * bears it out.
     */
    private static DeclaredArgument holdAccumulation(CompleteSignature declaration,
                                                     ArgumentRef container) {
        DeclaredArgument walked = holdToTheDeclaration(declaration, container, null,
                TypeRequirement.CONTAINER, "the container it accumulates");
        Type answers = declaration.result();
        Type element = Type.elementOfAContainer(walked.stands());
        if (!element.equals(answers)) {
            throw new IllegalStateException(
                    ((ValueName.Stdlib) declaration.declaring().operation()).qualified()
                    + " is declared to accumulate a container of " + Type.show(element)
                    + " and answers " + Type.show(answers)
                    + ", and a walk carries what it has so far in the type it answers");
        }
        return walked;
    }

    private OperationFactBinder() {}
}
