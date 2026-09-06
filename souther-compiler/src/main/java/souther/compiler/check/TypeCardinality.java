package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How many values every type has at most, over the declarations a module reaches.
 *
 * <p>An upper bound throughout, and the answer it exists to give is the one at the bottom: a type
 * this puts at none is a type nothing can build. Everything else it says is what makes that answer
 * reachable — a set cannot be filled from an element with fewer values than it holds, and knowing
 * that means counting the element.
 *
 * <p>Rising from nothing shown rather than narrowing from anything. A value of a type is built, and a
 * type written in terms of itself with nowhere to stop is one no building ever finishes — so the
 * reading starts by granting nothing and grants a value only where one is shown. Each round bounds
 * the values built in one more step than the last, and where nothing moves, nothing further can be
 * built.
 *
 * <p>Two kinds of place, told apart because only one of them needs the rising. Declarations written
 * in terms of each other are answered together and their answers are rounded to the counts some rule
 * asks about, which is what makes the rising stop. Everywhere else the order of the declarations
 * settles the answers one at a time, and nothing is rounded: a sum of two values is two, and rounding
 * it up to the next count anybody asked about would leave a set drawing on it unable to say it is too
 * small.
 *
 * <p>The bottom the rising starts from is not an answer and never becomes one. A member the rising
 * has not finished with is at the bottom in {@link Answers}, and a reading resting on one of those is
 * a reading resting on an assumption: writing it down as a proof would have {@code A} shown to have
 * no value because {@code B} has none and {@code B} because {@code A} has, which shows neither.
 * What is true once the rising stops is that the least fixed point was reached with those members
 * still at nothing, so no value of any of them is built in finitely many steps — and that is
 * {@link Emptiness.NoBaseInComponent}, written here and nowhere else.
 *
 * <p>This says nothing about which declaration is at fault for an answer of none. That is a question
 * about the graph rather than about any one type, and it is asked of these answers rather than while
 * they are found.
 */
public final class TypeCardinality {

    private TypeCardinality() {}

    /** How many values each declaration {@code declarations} reaches has at most. */
    public static Cardinalities solve(List<Hir.Def> declarations, RuleReadingSource source,
                                      ReadingPolicy policy) {
        Symbols symbols = source.symbols();
        // What this count is short of is worked out from the clauses it actually asked for, which
        // is why the reading it runs against is wrapped rather than the declarations it walked
        // being counted afterwards. The two are not the same set: a rule reaches a declaration
        // through a spread, which the graph below does not follow, so a set taken from the graph
        // would leave out exactly the rules that arrive from somewhere else.
        Asked asked = new Asked(source.invariants());
        source = new RuleReadingSource(symbols, asked);
        Map<TypeSymbol, Hir.Def> declared = reached(declarations, symbols);
        Map<TypeSymbol, Set<TypeSymbol>> edges = new LinkedHashMap<>();
        declared.forEach((name, def) -> edges.put(name, read(def, symbols, declared.keySet())));
        // Fixed before the rising starts. What makes it stop is that there are finitely many answers
        // to rise through, and a count discovered part way would give it somewhere new to go.
        CardinalityCuts cuts = CardinalityCuts.keeping(asked(declared, source, policy));
        List<List<TypeSymbol>> components = TypeComponents.of(edges);
        return new Cardinalities(
                Map.copyOf(pass(components, declared, edges, cuts, source, policy, Set.of())),
                components, declared, edges, cuts, source, policy, asked.everyRuleReached());
    }

    /**
     * The reading a count runs against, remembering whether every clause it was asked for could be
     * given.
     *
     * <p>What makes a type have no value is what its rules leave, so a count taken where one of
     * them was never worked out is a count over fewer rules than the model states. It may still be
     * right; what it may not do is say so.
     *
     * <p>It answers as the reading it wraps and adds nothing to the answer. What it holds is about
     * the run and not about any declaration, so a caller reads it once the run is over.
     */
    private static final class Asked implements ExpandedClauseLookup {

        private final ExpandedClauseLookup reading;
        private boolean everyRuleReached = true;

        Asked(ExpandedClauseLookup reading) {
            this.reading = reading;
        }

        @Override
        public ExpandedClauseResult of(TypeKey declaration) {
            ExpandedClauseResult result = reading.of(declaration);
            if (result instanceof ExpandedClauseResult.Unavailable) {
                everyRuleReached = false;
            }
            return result;
        }

        boolean everyRuleReached() {
            return everyRuleReached;
        }
    }

    /**
     * What every declaration came to, and what it would take to ask again.
     *
     * <p>The second is here because one question is asked of these answers afterwards and cannot be
     * asked of the numbers alone: whether a declaration came to none of its own or came to none
     * because something it reads did. Answering it means reading the same declarations again under a
     * different assumption, which is this reading and not another one.
     */
    public static final class Cardinalities {

        private final Map<TypeSymbol, Cardinality> upper;
        private final List<List<TypeSymbol>> components;
        private final Map<TypeSymbol, Hir.Def> declared;
        private final Map<TypeSymbol, Set<TypeSymbol>> edges;
        private final CardinalityCuts cuts;
        private final RuleReadingSource source;
        private final ReadingPolicy policy;
        private final boolean everyRuleReached;

        private Cardinalities(Map<TypeSymbol, Cardinality> upper, List<List<TypeSymbol>> components,
                              Map<TypeSymbol, Hir.Def> declared,
                              Map<TypeSymbol, Set<TypeSymbol>> edges, CardinalityCuts cuts,
                              RuleReadingSource source, ReadingPolicy policy,
                              boolean everyRuleReached) {
            this.everyRuleReached = everyRuleReached;
            this.upper = upper;
            this.components = components;
            this.declared = declared;
            this.edges = edges;
            this.cuts = cuts;
            this.source = source;
            this.policy = policy;
        }

        /** Whether every rule this count read could be read. A count that was short of one says
         *  nothing about a type having no value: the rule it did not get may be the rule that
         *  empties it. */
        public boolean everyRuleReached() {
            return everyRuleReached;
        }

        /** How many values every declaration reached has at most. */
        public Map<TypeSymbol, Cardinality> all() {
            return upper;
        }

        /** How many values {@code name} has at most, nothing being known of a name not reached. */
        public Cardinality of(TypeSymbol name) {
            return upper.getOrDefault(name, Cardinality.UNKNOWN);
        }

        /** The declarations with no value, in the order they were reached. */
        Set<TypeSymbol> withNoValue() {
            Set<TypeSymbol> none = new LinkedHashSet<>();
            declared.keySet().forEach(each -> {
                if (of(each).none()) {
                    none.add(each);
                }
            });
            return none;
        }

        /** The declarations that had to be answered together, each before any that reads it. */
        List<List<TypeSymbol>> components() {
            return components;
        }

        /** What each declaration reads. */
        Map<TypeSymbol, Set<TypeSymbol>> edges() {
            return edges;
        }

        /**
         * Every declaration answered again with {@code granted} taken as having values.
         *
         * <p>Read again rather than kept a record of. What a count was is not what it would have
         * been: a record whose only field is an absent value has one value because what the field
         * would hold has none, and one is too few to fill a set of two — so a set with no value can
         * be a set nothing is the matter with, and no reading of which names it reads directly finds
         * that out. Granting the names and taking the answers afresh does. The proofs come back with
         * the counts, so what a declaration is shown by under one supposing is not read off what it
         * was shown by under another.
         */
        Map<TypeSymbol, Cardinality> granting(Set<TypeSymbol> granted) {
            return pass(components, declared, edges, cuts, source, policy, granted);
        }
    }

    /**
     * Every declaration answered in an order that has what each reads answered before it, with
     * {@code granted} taken as having values rather than read.
     *
     * <p>Granting is how the question "would this still have no value if that one had one" is asked.
     * The names granted are not read again; everything else is, so a count that was what it was only
     * because of a granted name is answered afresh — a record holding nothing but an absent value has
     * one value, and once the value it lacks is granted it has as many as anything.
     */
    private static Map<TypeSymbol, Cardinality> pass(List<List<TypeSymbol>> components,
                                                   Map<TypeSymbol, Hir.Def> declared,
                                                   Map<TypeSymbol, Set<TypeSymbol>> edges,
                                                   CardinalityCuts cuts, RuleReadingSource source,
                                                   ReadingPolicy policy,
                                                   Set<TypeSymbol> granted) {
        Answers answers = Answers.empty();
        for (List<TypeSymbol> component : components) {
            List<TypeSymbol> asked = new ArrayList<>();
            for (TypeSymbol each : component) {
                if (granted.contains(each)) {
                    answers.settle(each, Cardinality.UNKNOWN);
                } else {
                    asked.add(each);
                }
            }
            if (asked.isEmpty()) {
                continue;
            }
            if (asked.size() == 1 && !TypeComponents.recurses(component, edges)) {
                TypeSymbol one = asked.get(0);
                answers.settle(one, CardinalityTransfer.upperOf(
                        one, declared.get(one), source, policy, answers, granted::contains));
                continue;
            }
            rise(asked, declared, policy, source, cuts, answers, granted);
        }
        return answers.everySettled();
    }

    /**
     * The declarations answered by granting them nothing and granting only what is shown.
     *
     * <p>What they read outside themselves is already answered and is read as it stands. Rounding
     * those too would put the loss of precision at every edge of the graph rather than at the one
     * place the rising needs it.
     */
    private static void rise(List<TypeSymbol> component, Map<TypeSymbol, Hir.Def> declared,
                             ReadingPolicy policy,
                             RuleReadingSource source, CardinalityCuts cuts,
                             Answers answers, Set<TypeSymbol> granted) {
        component.forEach(answers::atBottom);
        boolean moved = true;
        while (moved) {
            moved = false;
            for (TypeSymbol each : component) {
                Cardinality before = answers.settledAt(each);
                Cardinality next = round(cuts, CardinalityTransfer.upperOf(
                        each, declared.get(each), source, policy, answers, granted::contains));
                // Written every round, and the rising is over the counts alone. Two readings that
                // come to none are the same answer to rise through however they were shown, so
                // comparing the proofs would keep a settled rising moving; and taking the earlier
                // proof would leave a declaration carrying what it was shown by before the answers
                // it rests on were what they are.
                answers.settle(each, next);
                if (before == null || !sameCount(before, next)) {
                    moved = true;
                }
            }
        }
        discharge(component, answers);
    }

    /** Whether two answers are the same one to rise through, which the proofs have no part in. */
    private static boolean sameCount(Cardinality one, Cardinality other) {
        return one instanceof Cardinality.None
                ? other instanceof Cardinality.None : one.equals(other);
    }

    private static Cardinality round(CardinalityCuts cuts, Cardinality of) {
        return of instanceof Cardinality.Standing standing ? cuts.round(standing) : of;
    }

    /**
     * The members left with nothing to bottom out, told what showed it once the rising has stopped.
     *
     * <p>A member whose proof rests on another member of the same component was, while the rising
     * ran, resting on an assumption. Which of them were shown something is asked here: a member with
     * a count is one, and so is a member whose proof reaches outside the component or stops at rules
     * of its own — and then any member resting only on those, and so on until nothing more is added.
     * What is left is a set every member of which needs the others, which is a lack no finite
     * building bottoms out of, and the least fixed point having been reached is the proof of it.
     */
    private static void discharge(List<TypeSymbol> component, Answers answers) {
        Set<TypeSymbol> shown = new HashSet<>();
        Set<TypeSymbol> within = Set.copyOf(component);
        boolean added = true;
        while (added) {
            added = false;
            for (TypeSymbol each : component) {
                if (shown.contains(each)) {
                    continue;
                }
                Cardinality count = answers.settledAt(each);
                if (!(count instanceof Cardinality.None it) || restsOn(it.why(), within, shown)) {
                    shown.add(each);
                    added = true;
                }
            }
        }
        if (shown.size() == component.size()) {
            return;
        }
        List<TypeSymbol> without = component.stream().filter(each -> !shown.contains(each)).toList();
        for (TypeSymbol each : without) {
            answers.settle(each, Cardinality.none(new Emptiness.NoBaseInComponent(
                    without, answers.settledAt(each).why())));
        }
    }

    /** Whether every member of {@code within} this proof reaches has been shown something. */
    private static boolean restsOn(Emptiness why, Set<TypeSymbol> within, Set<TypeSymbol> shown) {
        return switch (why) {
            case Emptiness.ConflictingRules _, Emptiness.EmptyNumericInterval _,
                 Emptiness.EmptyOrderedInterval _, Emptiness.NoAllowedValueInRange _,
                 Emptiness.NoAllowedValueWithinRequiredBounds _,
                 Emptiness.NoCommonValueForEqualPositions _,
                 Emptiness.NoDistinctValuesForPositionsHeldApart _,
                 Emptiness.PositionsHeldAsOneAreHeldApart _,
                 Emptiness.SetRequiresTooManyDistinctValues _,
                 Emptiness.NoAllowedCollectionSize _ -> true;
            case Emptiness.TheNameHasNone it ->
                    !within.contains(it.name()) || shown.contains(it.name());
            // Not reachable. A proof read here was built from what a name answers, which is the
            // proof that stops at the name, and this is the only writer of the other one. Answered
            // the way that keeps a member out of the set that was shown something, which is what it
            // would mean if it ever were reached.
            case Emptiness.NoBaseInComponent _ -> false;
            case Emptiness.AtAField it -> restsOn(it.under(), within, shown);
            case Emptiness.AtEqualPositions it -> restsOn(it.under(), within, shown);
            case Emptiness.AtPositionsHeldApart it -> restsOn(it.under(), within, shown);
            case Emptiness.NonEmptyCollectionWithNoElement it ->
                    restsOn(it.element(), within, shown);
            case Emptiness.AcrossEveryCase it ->
                    it.cases().stream().allMatch(each -> restsOn(each, within, shown));
        };
    }

    /**
     * Everything {@code declarations} reach, themselves included.
     *
     * <p>A type of another module is one of these. What a declaration comes to is settled by what it
     * is written in terms of wherever that was declared, and stopping at the edge of the module would
     * answer a record by the module its field's type happens to sit in.
     */
    private static Map<TypeSymbol, Hir.Def> reached(List<Hir.Def> declarations, Symbols symbols) {
        Map<TypeSymbol, Hir.Def> declared = new LinkedHashMap<>();
        List<TypeSymbol> left = new ArrayList<>();
        for (Hir.Def def : declarations) {
            left.add(def.declares());
        }
        while (!left.isEmpty()) {
            TypeSymbol name = left.remove(left.size() - 1);
            if (declared.containsKey(name) || !(symbols.declaredNode(name) instanceof Hir.Def def)) {
                continue;
            }
            declared.put(name, def);
            left.addAll(read(def, symbols, null));
        }
        return declared;
    }

    /**
     * The declarations {@code def} is written in terms of.
     *
     * <p>Every name written anywhere in its types, at whatever depth. A record naming another record
     * reads that one's answer and no further, but what that one reads it reads in turn, so a name
     * gathered from a depth this one does not itself descend to adds no edge the graph did not have.
     *
     * @param among the names to keep, or null to keep them all
     */
    private static Set<TypeSymbol> read(Hir.Def def, Symbols symbols, Set<TypeSymbol> among) {
        Set<TypeSymbol> named = new LinkedHashSet<>();
        switch (def) {
            case Hir.UnitData _ -> { }
            // A case naming nothing names no declaration for this to have read.
 	    case Hir.SumData sum -> sum.cases().forEach(each -> {
                if (each instanceof Hir.Name.Denoting names) {
                    named.add(names.type());
                }
            });
            case Hir.Data data ->
                    TypeOps.fieldTypes(data, symbols).values().forEach(each -> names(each, named));
        }
        if (among != null) {
            named.retainAll(among);
        }
        return named;
    }

    private static void names(Type type, Set<TypeSymbol> into) {
        if (type instanceof Type.Ref ref) {
            into.add(ref.name());
            return;
        }
        if (type instanceof Type.Union union) {
            into.addAll(union.members());
            return;
        }
        Type.forEachChild(type, each -> names(each, into));
    }

    /**
     * The counts the rules ask collections to hold, which is what decides the answers worth telling
     * apart.
     *
     * <p>Read off the domain rather than the clauses. A floor arrives in more ways than a number
     * written at the position, and a count missed here is precision lost and nothing else: the
     * answers still tell apart everything the questions found.
     */
    private static Set<Long> asked(Map<TypeSymbol, Hir.Def> declared, RuleReadingSource source,
                                   ReadingPolicy policy) {
        Symbols symbols = source.symbols();
        Set<Long> counts = new HashSet<>();
        declared.forEach((name, def) -> {
            // A data is a declaration a module wrote, so the second half never decides anything;
            // it is how the name says so rather than a reader assuming it.
            if (!(def instanceof Hir.Data data) || !(name instanceof TypeSymbol.AtModule at)) {
                return;
            }
            OccurrenceCounts held = OccurrenceCounts.of(at, source, policy);
            for (RuleKey path : data.newtype() ? Set.of(RuleKey.THE_VALUE)
                    : TypeOps.fieldTypes(data, symbols).keySet().stream()
                            .map(RuleKey::of).collect(java.util.stream.Collectors.toSet())) {
                long least = held.leastHeldAt(path);
                if (least > 0) {
                    counts.add(least);
                }
            }
        });
        return counts;
    }
}
