package souther.compiler.check;

import souther.compiler.numeric.LinearForm;
import souther.compiler.semantics.Accumulation;
import souther.compiler.semantics.BuiltFrom;
import souther.compiler.semantics.DefinitionCase;
import souther.compiler.semantics.NumericResult;
import souther.compiler.semantics.OperationSubject;
import souther.compiler.semantics.ResultBound;
import souther.compiler.semantics.TakenAs;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * What one binding of the declarations came to: every fact about the language's operations, held to
 * a library, keyed by the operation each is about.
 *
 * <p>The one place a fact is looked up. The declarations ({@link
 * souther.compiler.semantics.OperationFacts}) publish a list and no index, so that a reader cannot
 * reach a fact except through what the binder made of it; this is what the binder makes, and a
 * reader that wants a fact asks here. Every value here is a {@link BoundOperationFact}, in which
 * every operation and every argument has already been read against its declaration, so what a
 * reader does with one is use it.
 *
 * <p><b>Keyed by the fact's own operation, and by a name.</b> The key under which a fact is filed is
 * read off the fact — {@code fact.operation().operation()} — and never written beside it, so no key
 * can say the fact is about one operation while the fact says another. And it is the name, because
 * that is the identity of a declaration ({@link souther.compiler.core.DeclaredOperation#equals}) and
 * because a reader holding a call the language resolved but did not keep standing has the name and
 * nothing else. Asking by name is a lookup among values the binding made and not a second reading of
 * the authored word: nothing here reads a declaration, a signature, or an {@link
 * souther.compiler.semantics.ArgumentRef}, and nothing here can be answered for an operation the
 * binding did not hold.
 *
 * <p><b>Bound to bound, and nothing else.</b> The two answers here that are not a plain lookup —
 * what an operation takes of its argument, which operations answer a number taken of one — are put
 * together from bound facts and the derivation those facts carry themselves
 * ({@link BoundOperationFact.AccumulatesItsContainer#takenAs}). No library and no authoring
 * vocabulary is read to answer them, so what they say cannot come apart from what was held.
 *
 * <p>Collected by family. A {@link BoundOperationFact.OneAboutAnOperation} is filed under its
 * operation once and a second of the same kind is refused: it would be two answers to a question
 * that has one, and a map written into keeps whichever arrived last. A
 * {@link BoundOperationFact.SeveralAboutAnOperation} is appended, in the order declared. Which of
 * the two a kind is was chosen where the arm was written, so nothing here has to be told.
 *
 * <p>Filed once, at construction, into what each query answers with. A query is asked per
 * expression per pass, so what it hands back is the list that was filed and not a projection made
 * for the ask.
 */
public final class BoundOperationFacts {

    private final List<BoundOperationFact> held;
    private final Map<Class<? extends BoundOperationFact.OneAboutAnOperation>,
            Map<ValueName, BoundOperationFact.OneAboutAnOperation>> ones = new LinkedHashMap<>();
    private final Map<Class<? extends BoundOperationFact.SeveralAboutAnOperation>,
            Map<ValueName, List<BoundOperationFact.SeveralAboutAnOperation>>> several =
            new LinkedHashMap<>();
    private final Map<ValueName, List<ResultBound<DeclaredArgument>>> bounds;
    private final Map<ValueName, List<DeclaredArgument>> noSmallerThan;
    private final Map<ValueName, List<DefinitionCase<DeclaredArgument>>> cases;
    private final Map<OperationSubject, Set<ValueName>> silences;

    /** Made by the binder and by nothing else: what these are is what a binding came to, and a
     *  set of facts gathered anywhere else would say so of facts nothing bound. Counted from the
     *  class files, as the arms' own constructors are. */
    BoundOperationFacts(List<BoundOperationFact> bound) {
        this.held = List.copyOf(bound);
        for (BoundOperationFact fact : held) {
            ValueName key = fact.operation().operation();
            // No default. A family added is a family this has to say how to collect.
            switch (fact) {
                case BoundOperationFact.OneAboutAnOperation one -> {
                    Map<ValueName, BoundOperationFact.OneAboutAnOperation> byOperation =
                            ones.computeIfAbsent(one.getClass(), _ -> new LinkedHashMap<>());
                    if (byOperation.put(key, one) != null) {
                        throw new IllegalStateException(key + " is declared to "
                                + one.getClass().getSimpleName() + " twice");
                    }
                }
                case BoundOperationFact.SeveralAboutAnOperation many ->
                        several.computeIfAbsent(many.getClass(), _ -> new LinkedHashMap<>())
                                .computeIfAbsent(key, _ -> new ArrayList<>()).add(many);
            }
        }
        // What each query over a family answers with, projected once from what was filed.
        bounds = projected(BoundOperationFact.BoundsItsResult.class,
                BoundOperationFact.BoundsItsResult::bound);
        noSmallerThan = projected(BoundOperationFact.ResultIsNoSmallerThan.class,
                BoundOperationFact.ResultIsNoSmallerThan::container);
        cases = projected(BoundOperationFact.IsDefinedByCases.class,
                BoundOperationFact.IsDefinedByCases::one);
        silences = silences();
    }

    /** The facts of {@code kind} an operation carries, each read as {@code part}, by operation. */
    private <F extends BoundOperationFact.SeveralAboutAnOperation, V> Map<ValueName, List<V>>
            projected(Class<F> kind, Function<F, V> part) {
        Map<ValueName, List<BoundOperationFact.SeveralAboutAnOperation>> byOperation =
                several.getOrDefault(kind, Map.of());
        Map<ValueName, List<V>> out = new LinkedHashMap<>();
        byOperation.forEach((operation, facts) -> {
            List<V> parts = new ArrayList<>(facts.size());
            facts.forEach(each -> parts.add(part.apply(kind.cast(each))));
            out.put(operation, List.copyOf(parts));
        });
        return Collections.unmodifiableMap(out);
    }

    /**
     * The silences by what they are about, read off the filed facts.
     *
     * <p>A silence is a fact an operation carries several of — one per subject — so it is filed
     * with that family and this is a second reading of the same list, by the subject a reader asks
     * under. Two silences of one operation under one subject are refused here: the family admits
     * several of a kind, and this is where what several may not be is said.
     */
    private Map<OperationSubject, Set<ValueName>> silences() {
        Map<OperationSubject, Set<ValueName>> out = new LinkedHashMap<>();
        several.getOrDefault(BoundOperationFact.SaysNothingOf.class, Map.of())
                .forEach((operation, facts) -> facts.forEach(each -> {
                    OperationSubject subject =
                            ((BoundOperationFact.SaysNothingOf) each).subject();
                    if (!out.computeIfAbsent(subject, _ -> new LinkedHashSet<>())
                            .add(operation)) {
                        throw new IllegalStateException(operation
                                + " is declared to say nothing under " + subject + " twice");
                    }
                }));
        out.replaceAll((_, named) -> Collections.unmodifiableSet(named));
        return Collections.unmodifiableMap(out);
    }

    /**
     * Every bound fact, in the order the declarations were held.
     *
     * <p>This package's and not everybody's, and read by two callers — the binder, which asks what
     * it has just bound before publishing it, and {@link NumericReadings}, which counts the
     * representations of a number across every kind of fact — and
     * {@code OnlyTheBinderReadsTheAuthoringVocabularyTest} counts them. Published, this would be
     * a way for a reader to sort the facts for itself and arrive at a second reading of what one
     * of the queries above already answers: a reader that picked the walks out of the list and
     * decided which of them add would own, beside {@link #takenAs}, the question of what a sum
     * is.
     */
    List<BoundOperationFact> all() {
        return held;
    }

    private <F extends BoundOperationFact.OneAboutAnOperation> F one(Class<F> kind,
                                                                    ValueName operation) {
        Map<ValueName, BoundOperationFact.OneAboutAnOperation> byOperation = ones.get(kind);
        return byOperation == null || operation == null ? null
                : kind.cast(byOperation.get(operation));
    }

    private Set<ValueName> ones(Class<? extends BoundOperationFact.OneAboutAnOperation> kind) {
        Map<ValueName, BoundOperationFact.OneAboutAnOperation> byOperation = ones.get(kind);
        return byOperation == null ? Set.of() : Collections.unmodifiableSet(byOperation.keySet());
    }

    /** What {@code operation} answers, counted, in what its arguments are counted as — or null
     *  where it states no such form. */
    public LinearForm<DeclaredArgument> answersAFormOfItsArguments(ValueName operation) {
        BoundOperationFact.AnswersAFormOfItsArguments held =
                one(BoundOperationFact.AnswersAFormOfItsArguments.class, operation);
        return held == null ? null : held.form();
    }

    /** The operations declared to answer a form of their arguments. */
    public Set<ValueName> answersAFormOfItsArguments() {
        return ones(BoundOperationFact.AnswersAFormOfItsArguments.class);
    }

    /** Which of {@code operation}'s two arguments a positive answer names as the greater, or null
     *  where the sign of what it answers is not their order. */
    public BoundOperationFact.StatesTheOrderOfItsArguments statesTheOrderOfItsArguments(
            ValueName operation) {
        return one(BoundOperationFact.StatesTheOrderOfItsArguments.class, operation);
    }

    /** The operations whose answer states the order of their arguments. */
    public Set<ValueName> statesTheOrderOfItsArguments() {
        return ones(BoundOperationFact.StatesTheOrderOfItsArguments.class);
    }

    /** How {@code operation} moves the value it is given, or null where it moves none. */
    public BoundOperationFact.ShiftsBy shiftsBy(ValueName operation) {
        return one(BoundOperationFact.ShiftsBy.class, operation);
    }

    /** The operations that move a value by an amount. */
    public Set<ValueName> shiftsBy() {
        return ones(BoundOperationFact.ShiftsBy.class);
    }

    /** What holds of the number {@code operation} answers, wherever it is called, in the order
     *  declared. */
    public List<ResultBound<DeclaredArgument>> boundsOnTheResult(ValueName operation) {
        return operation == null ? List.of() : bounds.getOrDefault(operation, List.of());
    }

    /** The operations something holds of the result of. */
    public Set<ValueName> boundsOnTheResult() {
        return bounds.keySet();
    }

    /** What {@code operation} builds its result from, or null where it builds none. */
    public BuiltFrom<DeclaredArgument> buildsItsResultFrom(ValueName operation) {
        BoundOperationFact.BuildsItsResultFrom held =
                one(BoundOperationFact.BuildsItsResultFrom.class, operation);
        return held == null ? null : held.built();
    }

    /** The operations that build a container out of another. */
    public Set<ValueName> buildsItsResultFrom() {
        return ones(BoundOperationFact.BuildsItsResultFrom.class);
    }

    /** The containers {@code operation}'s result is never smaller than, in the order declared. */
    public List<DeclaredArgument> resultIsNoSmallerThan(ValueName operation) {
        return operation == null ? List.of() : noSmallerThan.getOrDefault(operation, List.of());
    }

    /** Where {@code operation} reads the container its predicate is about, or null where it is no
     *  such predicate. */
    public BoundOperationFact.ReadsItsContainer readsItsContainer(ValueName operation) {
        return one(BoundOperationFact.ReadsItsContainer.class, operation);
    }

    /** The operations that are predicates over what a container holds. */
    public Set<ValueName> readsItsContainer() {
        return ones(BoundOperationFact.ReadsItsContainer.class);
    }

    /** Which argument {@code aspect} of {@code operation}'s answer turns on, or null where the
     *  library says nothing about what decides it. */
    public BoundOperationFact.TurnsOnWhatAnArgumentAnswers turnsOnWhatAnArgumentAnswers(
            ValueName operation, souther.compiler.semantics.AnswerAspect aspect) {
        BoundOperationFact.TurnsOnWhatAnArgumentAnswers held =
                one(BoundOperationFact.TurnsOnWhatAnArgumentAnswers.class, operation);
        return held == null || held.aspect() != aspect ? null : held;
    }

    /** Where {@code operation}'s predicate is stated over a projection, or null where it is stated
     *  over the element itself. */
    public DeclaredArgument isStatedOverAProjection(ValueName operation) {
        BoundOperationFact.IsStatedOverAProjection held =
                one(BoundOperationFact.IsStatedOverAProjection.class, operation);
        return held == null ? null : held.projection();
    }

    /** The operations whose predicate is stated over a projection. */
    public Set<ValueName> isStatedOverAProjection() {
        return ones(BoundOperationFact.IsStatedOverAProjection.class);
    }

    /** Whether {@code operation} states its predicate of every element. */
    public boolean statesItsPredicateOfEveryElement(ValueName operation) {
        return one(BoundOperationFact.StatesItsPredicateOfEveryElement.class, operation) != null;
    }

    /** The operations that do. */
    public Set<ValueName> statesItsPredicateOfEveryElement() {
        return ones(BoundOperationFact.StatesItsPredicateOfEveryElement.class);
    }

    /** The rewrite an emptiness check stands for, or null where {@code operation} is not one. */
    public BoundOperationFact.MeansTheSameAsASizeOfNought meansTheSameAsASizeOfNought(
            ValueName operation) {
        return one(BoundOperationFact.MeansTheSameAsASizeOfNought.class, operation);
    }

    /** The emptiness checks. */
    public Set<ValueName> meansTheSameAsASizeOfNought() {
        return ones(BoundOperationFact.MeansTheSameAsASizeOfNought.class);
    }

    /** What {@code operation} computes and where it answers it, or null where it computes no
     *  arithmetic of its own. */
    public NumericResult<DeclaredArgument> computesANumber(ValueName operation) {
        BoundOperationFact.ComputesANumber held =
                one(BoundOperationFact.ComputesANumber.class, operation);
        return held == null ? null : held.result();
    }

    /** The operations that compute arithmetic of their own. */
    public Set<ValueName> computesANumber() {
        return ones(BoundOperationFact.ComputesANumber.class);
    }

    /** The cases {@code operation}'s definition is written in, in the order declared, or an empty
     *  list where it answers none of the values it was given. */
    public List<DefinitionCase<DeclaredArgument>> isDefinedByCases(ValueName operation) {
        return operation == null ? List.of() : cases.getOrDefault(operation, List.of());
    }

    /** The operations that answer one of the values they were given. */
    public Set<ValueName> isDefinedByCases() {
        return cases.keySet();
    }

    /** The operations declared to say nothing under {@code subject}. */
    public Set<ValueName> saysNothingOf(OperationSubject subject) {
        return silences.getOrDefault(subject, Set.of());
    }

    /** What walking {@code operation}'s container comes to, and over which argument — or null where
     *  it accumulates nothing, including where the name is no operation of the library. */
    public BoundOperationFact.AccumulatesItsContainer accumulates(ValueName operation) {
        return one(BoundOperationFact.AccumulatesItsContainer.class, operation);
    }

    /** What walking {@code operation}'s container comes to, or null where it accumulates nothing. */
    public Accumulation accumulation(ValueName operation) {
        BoundOperationFact.AccumulatesItsContainer held = accumulates(operation);
        return held == null ? null : held.how();
    }

    /** The operations that answer what a container holds accumulated. */
    public Set<ValueName> accumulates() {
        return ones(BoundOperationFact.AccumulatesItsContainer.class);
    }

    /**
     * What {@code operation} takes of the one value it is given, or null where the number it
     * answers is not taken of one value.
     *
     * <p>Declared, or read off the walk where the operation is one — and the second is the walk's
     * own reading ({@link BoundOperationFact.AccumulatesItsContainer#takenAs}), put beside the first
     * here so that a reader asks one question. That the two cannot both hold of one operation is
     * held where the declarations are bound ({@link NumericReadings}).
     */
    public TakenAs takenAs(ValueName operation) {
        BoundOperationFact.AnswersANumberTakenOfTheOneValueItIsGiven declared =
                one(BoundOperationFact.AnswersANumberTakenOfTheOneValueItIsGiven.class, operation);
        if (declared != null) {
            return declared.how();
        }
        BoundOperationFact.AccumulatesItsContainer walk = accumulates(operation);
        return walk == null ? null : walk.takenAs();
    }

    /**
     * The operations that answer a number taken of the one value they are given.
     *
     * <p>The ones {@link #takenAs} answers for, which is not the same as the ones an account is
     * declared of: a walk that adds up a container is read as one and its account is that walk.
     */
    public Set<ValueName> answersANumberTakenOfItsArgument() {
        Set<ValueName> out = new LinkedHashSet<>(
                ones(BoundOperationFact.AnswersANumberTakenOfTheOneValueItIsGiven.class));
        for (ValueName operation : accumulates()) {
            if (takenAs(operation) != null) {
                out.add(operation);
            }
        }
        return Collections.unmodifiableSet(out);
    }

    /** The operations that count what they are given, which is the narrower vocabulary a size is
     *  asked for under. Read off the account rather than declared beside it. */
    public Set<ValueName> countsWhatItIsGiven() {
        Set<ValueName> out = new LinkedHashSet<>();
        for (ValueName operation
                : ones(BoundOperationFact.AnswersANumberTakenOfTheOneValueItIsGiven.class)) {
            if (takenAs(operation) instanceof TakenAs.HowManyItHolds) {
                out.add(operation);
            }
        }
        return Collections.unmodifiableSet(out);
    }

    /** Whether every number {@code operation} could answer is one some value it could be given
     *  answers. */
    public boolean everyAnswerItCanGiveHasASourceValue(ValueName operation) {
        return one(BoundOperationFact.EveryAnswerItCanGiveHasASourceValue.class, operation) != null;
    }
}
