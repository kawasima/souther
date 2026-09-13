package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;
import souther.compiler.values.Value;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * How many values a declaration has, given how many the declarations it reaches have.
 *
 * <p>One step and not the answer. What every type has is settled by rising from "nothing shown" until
 * nothing moves, and this is the step that rises: handed what is known so far, it says what this one
 * declaration comes to. Kept apart from the rising so that a wrong answer is one or the other and
 * never both at once.
 *
 * <p>Every arm is an upper bound, and the one an arm may not reach for is a count of none without a
 * proof — {@link Cardinality} makes that unwritable, so the places below that name an
 * {@link Emptiness} are the whole list of ways a type comes to have none. A shape this does not decide
 * — a name that resolved to nothing, a type standing for another, a case nothing answered, a form
 * written for expressions and never for a field — answers {@link Cardinality#UNKNOWN}, because
 * refusing a declaration is what a count of none does and a shape nobody read is no reason to refuse
 * anything. The switch over types carries no {@code default} for the same reason: a form added later
 * stops the build here rather than being swallowed into whichever answer this one happened to give.
 *
 * <p>Where a declaration is shown to have none in more than one way at once, the proof it carries is
 * the nearest one and not the first one reached. Rules of its own before a position's own shape, and
 * a position's own shape before something another declaration lacks; among positions equally near,
 * the order they are declared in. So the same model gives the same proof however the reading is
 * arranged, which is what makes a refusal reproducible.
 *
 * <p>A position is read where it sits. The rules a record wrote about a field reach the value
 * standing there and are read at that field's own path, which is what makes a list holding at least
 * one a different question from the same list holding none. A value a collection holds is not that
 * position — nothing was written about it there — so the reading is put down at a collection's
 * element and the element is asked about its type alone. What that loses is precision: rules do reach
 * an element, and taking the type's answer gives a collection more distinct values to draw on than it
 * has, which admits where a narrower reading would refuse.
 */
final class CardinalityTransfer {

    /**
     * How far a collection's sizes are enumerated before the answer is given up on.
     *
     * <p>Filling a set from an element with {@code n} values means asking about every size up to
     * {@code n}, and the point of asking is to tell a small number of values from a smaller
     * collection. Past a size nothing in the model asks about, the sum is a number no comparison
     * needs, and stopping is losing precision rather than soundness.
     */
    static final int ENUMERATION_LIMIT = 16;

    private CardinalityTransfer() {}

    /**
     * What {@code def}, declared as {@code named}, comes to under {@code answers}.
     *
     * @param granted the declarations taken to have values whatever their own rules say. Their
     *                clauses are left out of every reading here, because a declaration said to have
     *                no value is one whose rules say so, and those rules are read wherever it is
     *                reached: a record holding it would otherwise be told it holds nothing by the
     *                very rules the supposing was about.
     */
    static Cardinality upperOf(TypeSymbol named, Hir.Def def, RuleReadingSource source,
                               ReadingPolicy policy, Answers answers,
                               Set<TypeSymbol> granted) {
        return upperOf(named, def, source, policy, answers, granted, DeclarationReadings.NONE);
    }

    /** The same, asking {@code machines} for what somebody has already made of the declaration's
     *  string rules before building any of it. */
    static Cardinality upperOf(TypeSymbol named, Hir.Def def, RuleReadingSource source,
                               ReadingPolicy policy, Answers answers,
                               Set<TypeSymbol> granted, DeclarationReadings machines) {
        return switch (def) {
            case Hir.UnitData _ -> Cardinality.atMost(1);
            case Hir.SumData sum -> ofCases(namedCases(sum), answers);
            // A data is a declaration a module wrote. What the language gives never answers with
            // one, so a name reaching here that is not a module's is a declaration world and a
            // graph of names that have stopped agreeing.
            case Hir.Data data -> named instanceof TypeSymbol.AtModule at
                    ? ofData(at, data, source, policy, answers, granted, machines)
                    : Declared.notAModules(named, data);
        };
    }

    /**
     * The declarations a sum's cases name, or null where one of them names nothing.
     *
     * <p>A case resolution found no declaration for is a case this reading never took in. It is
     * reported where it is written, and counting the sum without it would answer for a shape that
     * was never read — so the sum is answered the way every other unread shape is.
     */
    private static List<TypeSymbol> namedCases(Hir.SumData sum) {
        List<TypeSymbol> named = new ArrayList<>();
        for (Hir.Name each : sum.cases()) {
            if (!(each instanceof Hir.Name.Denoting denoting)) {
                return null;
            }
            named.add(denoting.type());
        }
        return named;
    }

    /**
     * What a sum of {@code cases}, or a union of them, comes to.
     *
     * <p>A case bottoming out is the whole sum bottoming out, so these are added: what has no value
     * adds none, and the sum has none only where every one of them has none. Read as a whole rather
     * than folded from a count of none, because "all of them" is the proof and no one of them speaks
     * for the rest.
     */
    private static Cardinality ofCases(List<TypeSymbol> cases, Answers answers) {
        if (cases == null || cases.isEmpty()) {
            return Cardinality.UNKNOWN;   // nothing here was read
        }
        List<Emptiness> without = new ArrayList<>();
        Cardinality.Standing across = null;
        for (TypeSymbol each : cases) {
            switch (answers.of(each)) {
                case Cardinality.None it -> without.add(it.why());
                case Cardinality.Standing it -> across = across == null ? it : across.plus(it);
            }
        }
        return across != null ? across : Cardinality.none(new Emptiness.AcrossEveryCase(without));
    }

    private static Cardinality ofData(TypeSymbol.AtModule named, Hir.Data data, RuleReadingSource source,
                                      ReadingPolicy policy, Answers answers,
                                      Set<TypeSymbol> granted, DeclarationReadings machines) {
        // Rules that cannot all hold leave nothing to count, and the ends they would have been
        // counted between are gone with them. Asked before the positions, which have nothing to say
        // about a value the declaration as a whole refuses, and nearer than anything they could say.
        Optional<Emptiness> contradiction =
                FieldDomains.granting(named, data, source, policy, granted, machines)
                        .holdsNothing(machines.of(named.key()));
        if (contradiction.isPresent()) {
            return Cardinality.none(contradiction.get());
        }
        OccurrenceCounts counts = OccurrenceCounts.of(named, source, policy, granted, machines);
        OccurrenceValues values = OccurrenceValues.of(named, source, policy, granted, machines);
        Map<String, Type> fields = TypeOps.fieldTypes(data, source.symbols());
        if (data.newtype()) {
            // A newtype is one value under a name, so its value sits where it sits: the rules written
            // on the name are about what the value holds, and the value is at no path of its own.
            Type representation = fields.get("value");
            return representation == null ? Cardinality.UNKNOWN
                    : at(RuleKey.THE_VALUE, upperAt(representation, RuleKey.THE_VALUE,
                            counts, values, source, answers, granted, new HashSet<>(Set.of(named))));
        }
        RuleKey emptiestAt = null;
        Emptiness emptiest = null;
        Cardinality.Standing across = Cardinality.atMost(1);   // a record of no fields is one value
        for (Map.Entry<String, Type> each : fields.entrySet()) {
            // Every field, and not up to the first one with nothing in it. Which proof the record
            // carries is settled by how near the proofs are and by the order the fields are declared
            // in, and a reading that stopped at the first would answer with whichever the traversal
            // reached — the same model refused for a different reason each time the fields moved.
            switch (upperAt(each.getValue(), RuleKey.of(each.getKey()), counts, values, source,
                    answers, granted, new HashSet<>())) {
                case Cardinality.None it -> {
                    if (emptiest == null || it.why().category().compareTo(emptiest.category()) < 0) {
                        emptiest = it.why();
                        emptiestAt = RuleKey.of(each.getKey());
                    }
                }
                case Cardinality.Standing it -> across = across.times(it);
            }
        }
        return emptiest == null ? across
                : Cardinality.none(new Emptiness.AtAField(where(emptiestAt), emptiest));
    }

    /**
     * How many values {@code type} has, where they are something that can be written out.
     *
     * <p>Nothing proven where they cannot be, which is the answer every shape this does not read
     * gets. A type whose values can be written out has at least one of them: a list with nothing in
     * it is not a type, and the count of none is a claim that carries a proof.
     */
    private static Cardinality howManyValues(Type type, NewtypeInners inners, Symbols symbols,
                                             DeclarationKinds kinds,
                                             PublishedDeclarations published) {
        List<Value> every = ValueUniverse.of(type, inners, symbols, kinds, published);
        return every == null || every.isEmpty() ? Cardinality.UNKNOWN
                : Cardinality.atMost(every.size());
    }

    /** {@code count} as it stands at {@code path}, which is where a proof of none says it sits. */
    private static Cardinality at(RuleKey path, Cardinality count) {
        return count instanceof Cardinality.None it
                ? Cardinality.none(new Emptiness.AtAField(where(path), it.why())) : count;
    }

    /** The place a proof names, said as the reading has it rather than as text a reader takes
     *  apart again. */
    private static Emptiness.AtAField.Where where(RuleKey path) {
        return path.isTheValueItself() ? new Emptiness.AtAField.Where.TheValueItself()
                : new Emptiness.AtAField.Where.In(path.toString());
    }

    /**
     * How many values may stand at one position.
     *
     * @param path  where the position sits in the value {@code counts} and {@code values} were read
     *              from
     * @param worn  the names this value is already wearing, so that a newtype reached from inside
     *              itself is answered from {@code answers} rather than unwrapped again
     */
    static Cardinality upperAt(Type type, RuleKey path, OccurrenceCounts counts,
                               OccurrenceValues values, RuleReadingSource source,
                               Answers answers, Set<TypeSymbol> granted,
                               Set<TypeSymbol> worn) {
        return switch (type) {
            case Type.Prim prim -> switch (prim) {
                // As many as it has values, which is a question with an answer of its own. Written
                // here as a number, the count and the values would be two records of one fact with
                // nothing holding them together.
                case BOOL ->
                        howManyValues(type, source.inners(), source.symbols(), source.kinds(),
                                source.published());
                case INT -> values.wholeValuesAt(path);
                // Spaced too finely to count between two ends, or not spaced at all. A string bounded
                // in length and a date bounded at both ends are finite and are not counted here: what
                // it would take is a reading of each carrier's own values, and nothing asks yet.
                case STRING, DECIMAL, DATE, TIME, DATETIME, INSTANT, RAW -> Cardinality.UNKNOWN;
            };
            case Type.Ref ref -> ofRef(ref, path, counts, values, source, answers, granted, worn);
            // A `None` is a value of it whatever it wraps, so this is the one position that is never
            // empty. What it wraps is a value of its own type and nothing was written about it here.
            case Type.OptionOf option ->
                    ofType(option.element(), source, answers, granted, worn)
                            instanceof Cardinality.Standing wrapped
                            ? Cardinality.atMost(1).plus(wrapped) : Cardinality.atMost(1);
            case Type.ListOf list -> ofList(list.element(), path, counts, source, answers, granted, worn);
            case Type.SetOf set -> ofSet(set.element(), path, counts, source, answers, granted, worn);
            case Type.MapOf map -> ofMap(map, path, counts, source, answers, granted, worn);
            // Several values carried together, which is a product like a record's fields. Written only
            // inside a computation — a field of one is refused — so nothing in a declaration reaches
            // this, and a part with no value is carried up as it stands: the parts sit at no path of
            // their own for a proof to name.
            case Type.TupleOf tuple -> {
                Cardinality.Standing across = Cardinality.atMost(1);
                Cardinality.None without = null;
                for (Type each : tuple.elements()) {
                    switch (ofType(each, source, answers, granted, worn)) {
                        case Cardinality.None it -> {
                            if (without == null
                                    || it.why().category().compareTo(without.why().category()) < 0) {
                                without = it;
                            }
                        }
                        case Cardinality.Standing it -> across = across.times(it);
                    }
                }
                yield without != null ? without : across;
            }
            case Type.Union union -> ofCases(List.copyOf(union.members()), answers);
            // A type standing for another, a name that resolved to nothing, a function, and the two
            // that only an expression reaches: `Nothing` is what an empty list literal's element is
            // waiting to be told, and `Never` is where an abort leaves off. None of them is written
            // in a declaration, and a count of none read off one would refuse a type on the strength
            // of a form this never decided.
            case Type.Open _, Type.Erroneous _, Type.FnOf _, Type.Nothing _, Type.Never _ ->
                    Cardinality.UNKNOWN;
        };
    }

    /**
     * A name, unwrapped while it is one this value is not already wearing.
     *
     * <p>The one place anything is learned about a name beyond what the answers say of it. A sum
     * reads its cases and a union its members, and both read the answer and nothing else; here the
     * name is opened and what it wraps is read again. So this is where a name that was granted a
     * value has to be left alone: opening it reaches the very rules and the very shape that leave it
     * without one, and the granting would be undone one step in.
     */
    private static Cardinality ofRef(Type.Ref ref, RuleKey path, OccurrenceCounts counts,
                                     OccurrenceValues values, RuleReadingSource source,
                                     Answers answers, Set<TypeSymbol> granted,
                                     Set<TypeSymbol> worn) {
        Cardinality named = answers.of(ref.name());
        if (granted.contains(ref.name())
                || !(source.symbols().declaredNode(ref.name()) instanceof Hir.Data data)
                || !data.newtype()
                || !worn.add(ref.name())) {
            return named;
        }
        // The name is not a step of the path: a rule the record wrote about this field reaches what
        // the name wraps, and reading the wrapped type without it would leave a floor written here
        // saying nothing. Both readings bound the same values, so the narrower of them holds.
        Type representation = TypeOps.fieldTypes(data, source.symbols()).get("value");
        if (representation == null) {
            return named;
        }
        Cardinality unwrapped =
                upperAt(representation, path, counts, values, source, answers, granted, worn);
        if (named instanceof Cardinality.Standing here
                && unwrapped instanceof Cardinality.Standing there) {
            return Cardinality.Standing.narrower(here, there);
        }
        // Both readings cannot be wider than none, so what is left to choose is which proof is
        // carried. The unwrapped one is about the rules written at this position and the named one
        // stops at the name, and the nearer of the two is the one that says something here.
        if (named instanceof Cardinality.None it && unwrapped instanceof Cardinality.None other) {
            return other.why().category().compareTo(it.why().category()) <= 0 ? other : it;
        }
        return named instanceof Cardinality.None ? named : unwrapped;
    }

    /** A value a collection holds, which no rule of the collection's own was written about. */
    private static Cardinality ofType(Type type, RuleReadingSource source, Answers answers,
                                      Set<TypeSymbol> granted, Set<TypeSymbol> worn) {
        return upperAt(type, RuleKey.THE_VALUE, OccurrenceCounts.NOTHING_READ,
                OccurrenceValues.NOTHING_READ, source, answers, granted, worn);
    }

    /**
     * What a collection the rules will not let hold anything comes to.
     *
     * <p>The empty collection is a value of its own, so this is one wherever the rules leave room for
     * it. Where they do not, there is nothing left for the collection to be, and which of the two
     * refusals it is depends on what left it nothing to hold: rules that admit no size at all, or an
     * element there is no value of. Both were one answer once, which is a count of none reached two
     * ways and said one way.
     */
    private static Cardinality noSizeLeft(OccurrenceCounts counts, RuleKey path) {
        return counts.mayHoldExactly(path, 0) ? Cardinality.atMost(1)
                : Cardinality.none(new Emptiness.NoAllowedCollectionSize());
    }

    private static Cardinality nothingToHold(OccurrenceCounts counts, RuleKey path,
                                             Emptiness element) {
        return counts.mayHoldExactly(path, 0) ? Cardinality.atMost(1)
                : Cardinality.none(new Emptiness.NonEmptyCollectionWithNoElement(element));
    }

    private static Cardinality ofSet(Type element, RuleKey path, OccurrenceCounts counts,
                                     RuleReadingSource source, Answers answers,
                                     Set<TypeSymbol> granted, Set<TypeSymbol> worn) {
        if (!counts.mayHoldAtLeast(path, 1)) {
            return noSizeLeft(counts, path);
        }
        Cardinality.Standing each;
        switch (ofType(element, source, answers, granted, worn)) {
            case Cardinality.None it -> { return nothingToHold(counts, path, it.why()); }
            case Cardinality.Standing it -> each = it;
        }
        long distinct = each.boundOr(-1);
        if (distinct < 0) {
            return Cardinality.UNKNOWN;
        }
        // A set holds each of its element's values once, so a size above how many there are is a size
        // nothing fills. Asked whatever the number, because it is one question and not an
        // enumeration.
        if (!counts.mayHoldAtMost(path, distinct)) {
            return Cardinality.none(new Emptiness.SetRequiresTooManyDistinctValues(distinct));
        }
        if (distinct > ENUMERATION_LIMIT) {
            return Cardinality.UNKNOWN;
        }
        Cardinality.Standing across = null;
        for (long size = 0; size <= distinct; size++) {
            if (counts.mayHoldExactly(path, size)) {
                Cardinality.Standing here = each.choose(size);
                across = across == null ? here : across.plus(here);
            }
        }
        // Nothing reaches the second of these. A collection got this far by admitting a size of one
        // or more and by not admitting one past where the walk stops, so a size it admits lies
        // inside what was walked and some step finds it — unless one position was asked two ways
        // round and the answers disagreed, which is not something this decided. Answered the way
        // every undecided shape is, because refusing a declaration is what a count of none does.
        return across == null ? Cardinality.UNKNOWN : across;
    }

    private static Cardinality ofList(Type element, RuleKey path, OccurrenceCounts counts,
                                      RuleReadingSource source, Answers answers,
                                      Set<TypeSymbol> granted, Set<TypeSymbol> worn) {
        if (!counts.mayHoldAtLeast(path, 1)) {
            return noSizeLeft(counts, path);
        }
        Cardinality.Standing each;
        switch (ofType(element, source, answers, granted, worn)) {
            case Cardinality.None it -> { return nothingToHold(counts, path, it.why()); }
            case Cardinality.Standing it -> each = it;
        }
        // A list holds its element's values over again, so length and not the element is what bounds
        // it. Left long enough and the lists are past counting however few values the element has.
        if (counts.mayHoldAtLeast(path, ENUMERATION_LIMIT + 1L)) {
            return Cardinality.UNKNOWN;
        }
        Cardinality.Standing across = null;
        for (long length = 0; length <= ENUMERATION_LIMIT; length++) {
            if (counts.mayHoldExactly(path, length)) {
                Cardinality.Standing here = each.toThe(length);
                across = across == null ? here : across.plus(here);
            }
        }
        return across == null ? Cardinality.UNKNOWN : across;   // as in `ofSet`, and as unreachable
    }

    private static Cardinality ofMap(Type.MapOf map, RuleKey path, OccurrenceCounts counts,
                                     RuleReadingSource source, Answers answers,
                                     Set<TypeSymbol> granted, Set<TypeSymbol> worn) {
        if (!counts.mayHoldAtLeast(path, 1)) {
            return noSizeLeft(counts, path);
        }
        if (!Type.STRING.equals(map.key())) {
            return Cardinality.UNKNOWN;   // keyed by something this has not read
        }
        // A key is a string and there is no end of those, so a map holding anything at all holds it
        // under more keys than can be counted. Only a map with nothing to hold is finite here, and
        // one that must hold something has no value.
        return ofType(map.value(), source, answers, granted, worn) instanceof Cardinality.None it
                ? nothingToHold(counts, path, it.why()) : Cardinality.UNKNOWN;
    }
}
