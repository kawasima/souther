package souther.compiler.check;

import souther.compiler.semantics.ConditionJoin;
import souther.compiler.ast.Hir;
import souther.compiler.core.Core;
import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.LinearForm;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.numeric.OrderedInterval;
import souther.compiler.numeric.Rel;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.values.AdmissibleSet;
import souther.compiler.values.KnownExtents;
import souther.compiler.values.StringFacts;
import souther.compiler.values.StringMachineAnswers;
import souther.compiler.values.UnreadReason;
import souther.compiler.values.ValueSet;

import souther.compiler.numeric.Count;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import souther.compiler.types.ValueName;

import java.util.Optional;
import java.util.SequencedMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a record leaves each of its fields able to hold.
 *
 * <p>Not what the field's own type admits. A clause relating two fields — {@code startsAt < endsAt} —
 * is a rule about pairs, and what one field can hold is that rule projected onto it: with both ends
 * of a day bounded at 1440, {@code startsAt} stops at 1439, because a 1440 would need an
 * {@code endsAt} the day has no room for. Reading the field's type alone names values the record
 * refuses.
 *
 * <p>The bounds are as wide as what the rules could be read as, and only that. {@link #projection}
 * says how much of them that was: a clause outside the fragment — a call, a pattern, a sum of three
 * terms — narrows nothing here, so where one is present these bounds admit values nothing can build.
 * Wide is the safe direction for deciding a value is impossible and the wrong direction for deciding
 * that an edge can be written, which is why the two answers are handed over together.
 *
 * <p><b>Asked by what this value's own rules call a place ({@link RuleKey}), and never by where a
 * row writes a value.</b> The two part at a sum whose cases share a spread: what the cases share is
 * named at the sum, and a row writes it under whichever case it turned out to be. So a place a rule
 * of this value cannot name — inside a sequence, under a case — has no answer here rather than an
 * answer nothing was written at, and taking a name to the places it stands at is somebody else's
 * ({@code InputDomain}).
 */
public final class FieldDomains {

    /** No name anywhere, for the reading that reached none. Unmodifiable, as every other part
     * of {@link #NONE} is: a shared constant handing out a map anybody could add to is a value one
     * caller can change under the rest. */
    private static final SequencedMap<FactSubject, RuleKey> NOTHING_NAMED =
            java.util.Collections.unmodifiableSequencedMap(new LinkedHashMap<>());

    /**
     * Nothing known of any field.
     *
     * <p>Which is not the same as a value with nothing written about it, and answers as the first:
     * no clause of anything was gathered here, so {@link #admits} says of every name that the
     * reading never reached the rules about it ({@link UnreadReason#NOT_REACHED}). A caller holding
     * this holds it because it chose not to read a declaration or had none to read, and neither of
     * those is a reading that found no rules.
     */
    public static final FieldDomains NONE =
            new FieldDomains(Map.of(), Map.of(), Map.of(), Map.of(), Set.of(), List.of(), List.of(),
                    List.of(), List.of(), PartsLeftOut.NONE, Map.of(),
                    Map.of(), Map.of(), new ReadingEvidence(), Map.of(),
                    Map.of(RuleKey.THE_VALUE, Set.of(new RulesMissed.NoReadingWasMade())), Set.of(),
                    NOTHING_NAMED,
                    ConstraintState.<FactSubject>top(), null, null, null, null, Map.of(),
                    Set.of(RuleKey.THE_VALUE),
                    Map.of(), Map.of(), List.of(), Map.of(), StringFacts.NONE, KnownExtents.NONE,
                    Map.of(), Map.of(), BoundaryState.nothing(),
                    SettledOrderEnvelope.nothing());

    private final Map<RuleKey, NumericDomain.Bounds> byName;
    /** The ends the record's own clauses place, which is a different question from the range they
     * leave — see {@link #placedAt}. */
    private final List<InvariantChecker.Direct> directs;
    /** The rules saying where a coordinate's values stop that no end came out of — see
     * {@link #noLineAt}. */
    private final List<NoLine> noLines;
    /** The conjuncts this reading got no end out of, for whoever reads them next — see
     * {@link #withoutAnEnd}. */
    private final List<WithoutAnEnd> withoutAnEnd;
    /** The conjuncts whose quantity is over one number — see {@link #aboutOneCoordinate}. */
    private final List<AboutOneCoordinate> aboutOneCoordinate;
    /**
     * Which conjunct this reading was asked to leave out, so that a reading standing in for a
     * counterfactual is not asked one of its own.
     *
     * <p>What a conjunct was holding is read by comparing two readings, and the one being compared
     * against has no such question of its own to answer: asked, it would read itself again without
     * one of its conjuncts, and again, and never come back.
     */
    private final PartsLeftOut withoutParts;
    /** The surviving ends attributed to the conjuncts that account for them, worked out once — see
     *  {@link #movedEnds}. */
    private volatile List<Placed> moved;
    /** What each clause reaching this value raises, keyed on the rule it is. */
    private final Map<RuleRef.Invariant, Required> raised;
    /** The same per part of each clause. A reader that found one conjunct wanting names what that
     *  conjunct is about, and not what the conjunct written beside it raised. */
    private final Map<RuleRef.Invariant, Map<ClauseExpr.Occurrence, Required>> raisedByPart;

    /** What the reading answered for each boundary question it raised and left standing. */
    private final Map<BoundaryQuestion, BoundaryStanding> standing;
    /** Where a choice of a rule left an end of it open — see {@link #endsLeftOpenAt}. */
    private final Map<RuleRef.Invariant, EndsLeftOpen> endsLeftOpen;
    /**
     * Where the rules leave the numbers this value's operations answer, with every choice settled.
     *
     * <p>Beside the interval algebra and not inside it. That one never enters an alternative — what
     * a construction owes is asked of the clause as written — so a choice between two bounds on a
     * length leaves it nothing, and where those two branches together stop the length is known only
     * to the reading that composed them.
     *
     * <p><b>Read where a line is looked for and nowhere else.</b> It is an envelope: two branches
     * naming one size each leave the run between them, and no value has the sizes in between. So
     * {@link #leftAt} takes it — a line falls at the outermost end either way — and
     * {@link #projection} does not, because being inside the envelope is not being a value the
     * rules admit.
     */
    private final BoundaryState derived;
    /**
     * And where the same reading left the positions themselves, which the interval algebra cannot
     * reach for the same reason ({@link SettledOrderEnvelope}).
     *
     * <p>The two are one arrangement asked of two kinds of number, and which of them answers is
     * decided by the number and never by the caller: a position's own order is settled where the
     * branches have their fate and a count's is settled beside it, so each kind has one reader
     * holding it and {@link #leftAt} picks between them off the coordinate it was handed.
     */
    private final SettledOrderEnvelope settledOrder;
    /** Which choice an author is sent to for a line on one of those numbers that nothing placed —
     *  see {@link #endsLeftOpenAt}. */
    private final Map<RuleRef.Invariant, Map<OpenEnd, EndsLeftOpen.Behind>> boundsLeftOpen;
    /** Which readings took each clause in, as each of them said so. */
    private final ReadingEvidence took;
    /** The accounting, worked out once. Every name of a value asks the same question of it. */
    private volatile Map<RuleRef.Invariant, RuleAccounting> accounting;
    /** Which declarations relate each coordinate to something else, and so could have moved where it
     * stops — see {@link #narrowedBy}. */
    private final Map<RuleKey, List<TypeSymbol.AtModule>> narrowers;
    /** What each name has to hold, kept apart from what each name is. Same numbers, different
     * question — see {@link Held}. */
    private final Map<RuleKey, NumericDomain.Bounds> heldByName;
    /** Which values may stand at each name — see {@link #admits}. */
    private final Map<RuleKey, ValueSet> admittedByName;
    /** Everything that stopped the reading from speaking for a name, for the ones it could not
     * speak for. A name not here is one the reading took every rule about into the set — see
     * {@link #admits}. Every reason and not the first: a name is written by as many parts of as
     * many clauses as the author wrote about it, and two of them stop this reading in two ways that
     * are lifted by different work. */
    private final Map<RuleKey, List<UnreadReason>> unreadByName;

    /** The names the reading of values could not show hold exactly, resolved onto names as the
     *  values are. Asked of each name rather than of the reading: the proposition is quantified
     *  over them, and a name a lost correlation never reached keeps its own answer. */
    private final Set<RuleKey> notSeparatedByName;
    /** Where a clause of this value did not reach the readings at all, as the names the stops
     * happened at and what stopped there — see {@link #admits}. */
    private final Map<RuleKey, Set<RulesMissed>> notGathered;
    /** Where this reading ended with a declaration still to be read under the name, which is an
     * obligation on whoever walks them rather than anything wrong here — see
     * {@link #handedOn()}. */
    private final Set<RuleKey> handedOn;
    /** And of those, the names a construction has to make a value at. What a name admits is
     *  short wherever a rule about it went unread; whether an edge of it may be promised is about
     *  what every value of this has to satisfy, and a rule inside an optional is not that. */
    private final Set<RuleKey> unreadOfEveryValue;
    /** What each subject of this value's reading is called, in the order the value declares them.
     * What a domain holds is a subject; which name of the value that is, is known here. */
    private final SequencedMap<FactSubject, RuleKey> namedBy;
    /** Everything the clauses were read as, kept whole. Whether any value of this exists is a
     * question about all of it and is asked of it; the numbers are read out of it where a bound is
     * what a caller is after. */
    private final ConstraintState<FactSubject> constraints;
    /** What this was read from, so that it can be read again without one declaration's clauses. */
    private final TypeSymbol.AtModule named;
    private final Hir.Data data;
    /** The scope and the representation together, so that a second reading of this declaration reads
     *  the same tree. Held apart, a counterfactual could be taken against the other form and what
     *  moved would be read as what a rule did. */
    private final RuleReadingSource source;
    private final Map<NumberAt<RuleKey>, Count> settled;
    /** What this value was read under, so that reading it again for what one rule did reads it the
     *  same way. A second reading of one declaration under another policy would answer a name
     *  differently while both stayed sound, and what moved would be read as what the rule did. */
    private final ReadingPolicy policy;
    /** The atom a range is taken of at each name: what stands there, and the count of it where a
     *  count is taken. A name with neither has no range to be exact about. */
    private final Map<RuleKey, FactSubject> atomAt;
    private final Map<RuleKey, Counted> countAt;
    /** What the reading that builds the bounds made of each part of each rule. Per part, because a
     *  rule is represented where every part of it is. */
    private final List<InvariantChecker.Written> readings;
    /** How each atom's values are spaced, so that settling one afterwards states the same equality
     *  the reading would have stated for it. */
    private final Map<FactSubject, souther.compiler.numeric.Granularity> spacing;
    /**
     * The string machines this reading answered from and made.
     *
     * <p>A value, and the one a store keeps under the declaration. What it holds is a fact about a
     * plan, a set or a language beside a stretch, and which reading built it does not enter into
     * what it says — so a counterfactual of this reading is handed it rather than building the same
     * machines again ({@link #counterfactual}). Leaving rules out changes which plans a reading
     * meets, not what any one of them admits.
     *
     * <p>The facts and not the lender they came from. A lender asks a store, and this is reachable
     * from an answer: what a {@link NarrowedBounds} defers its names to is a reading of this, so a
     * store's capability kept here would be kept in an answer.
     */
    private final StringFacts stringMachines;

    /**
     * Where the sets met under this revision were found to stop, for the readings this one makes
     * of what its rules would leave without a clause.
     *
     * <p>A capability and not facts, and here rather than in what a reading came to: it is the
     * revision's and is dropped with it, while what a reading came to is a value a store keeps.
     */
    private final KnownExtents known;

    /**
     * The counterfactual readings this one has been asked for, kept under what each leaves out
     * ({@link #counterfactual}).
     *
     * <p>Beside the state rather than part of it. Each is the reading this already is, read out for
     * what one rule did, so nothing here answers anything the rules of this value do not already
     * say.
     */
    private final Map<LeftOut, FieldDomains> counterfactuals = new HashMap<>();

    private FieldDomains(Map<RuleKey, NumericDomain.Bounds> byName,
                         Map<RuleKey, NumericDomain.Bounds> heldByName,
                         Map<RuleKey, ValueSet> admittedByName,
                         Map<RuleKey, List<UnreadReason>> unreadByName,
                         Set<RuleKey> notSeparatedByName,
                         List<InvariantChecker.Direct> directs, List<NoLine> noLines,
                         List<WithoutAnEnd> withoutAnEnd, List<AboutOneCoordinate> aboutOneCoordinate,
                         PartsLeftOut withoutParts,
                         Map<RuleRef.Invariant, Required> raised,
                         Map<RuleRef.Invariant, Map<ClauseExpr.Occurrence, Required>> raisedByPart,
                         Map<BoundaryQuestion, BoundaryStanding> standing, ReadingEvidence took,
                         Map<RuleKey, List<TypeSymbol.AtModule>> narrowers,
                         Map<RuleKey, Set<RulesMissed>> notGathered, Set<RuleKey> handedOn,
                         SequencedMap<FactSubject, RuleKey> namedBy,
                         ConstraintState<FactSubject> constraints, TypeSymbol.AtModule named,
                         Hir.Data data, RuleReadingSource source, ReadingPolicy policy,
                         Map<NumberAt<RuleKey>, Count> settled,
                         Set<RuleKey> unreadOfEveryValue,
                         Map<RuleKey, FactSubject> atomAt, Map<RuleKey, Counted> countAt,
                         List<InvariantChecker.Written> readings,
                         Map<FactSubject, souther.compiler.numeric.Granularity> spacing,
                         StringFacts stringMachines, KnownExtents known,
                         Map<RuleRef.Invariant, EndsLeftOpen> endsLeftOpen,
                         Map<RuleRef.Invariant, Map<OpenEnd, EndsLeftOpen.Behind>>
                                 boundsLeftOpen,
                         BoundaryState derived, SettledOrderEnvelope settledOrder) {
        this.endsLeftOpen = endsLeftOpen;
        this.boundsLeftOpen = boundsLeftOpen;
        this.derived = derived;
        this.settledOrder = settledOrder;
        this.stringMachines = stringMachines;
        this.known = known;
        this.byName = byName;
        this.heldByName = heldByName;
        this.admittedByName = admittedByName;
        this.unreadByName = unreadByName;
        this.notSeparatedByName = notSeparatedByName;
        this.directs = directs;
        this.noLines = noLines;
        this.withoutAnEnd = List.copyOf(withoutAnEnd);
        this.aboutOneCoordinate = List.copyOf(aboutOneCoordinate);
        this.withoutParts = withoutParts;
        this.raised = raised;
        this.raisedByPart = raisedByPart;
        this.standing = standing;
        this.took = took;
        this.narrowers = narrowers;
        this.notGathered = notGathered;
        this.handedOn = handedOn;
        this.namedBy = namedBy;
        this.constraints = constraints;
        this.named = named;
        this.data = data;
        this.source = source;
        this.policy = policy;
        this.settled = settled;
        this.unreadOfEveryValue = unreadOfEveryValue;
        this.atomAt = atomAt;
        this.countAt = countAt;
        this.readings = readings;
        this.spacing = spacing;
    }

    /**
     * Whether the rules contradict, so that no value of this type exists at all.
     *
     * <p>A separate answer from a field nothing bounds. Both leave no bounds to read, and one of them
     * means every name here holds anything while the other means none of them holds anything: a
     * report that took the second for the first would ask for rows at edges of a value nobody can
     * build.
     *
     * <p>Asked of the whole state and not of the numbers in it. A rule reaches whichever domain has
     * a word for it, so a contradiction can be held entirely by a domain that has nothing to do with
     * intervals — and a reading that asked the numbers alone answered that a value exists because
     * the domain it asked had never heard of the rules.
     */
    public boolean infeasible() {
        return constraints.isBottom();
    }

    /** The same, borrowing what {@code machines} has already made where deciding takes one. */
    public boolean infeasible(StringMachineAnswers machines) {
        return constraints.isBottom(machines);
    }

    /**
     * Why the rules leave no value, or empty where they may leave one.
     *
     * <p>{@link #infeasible} with the answer instead of the fact. Asked of the same state and never
     * assembled beside it: a caller reading one of them and deciding the other for itself would have
     * two accounts of one reading to keep in step.
     *
     * <p>The places are this value's, in the order it declares them. What a state holds is a
     * subject, and what this value's rules call the place it is at is known here and nowhere else.
     */
    public Optional<Emptiness> holdsNothing() {
        return constraints.holdsNothing(spelled(namedBy));
    }

    /** The same, borrowing what {@code machines} has already made where deciding takes one. */
    public Optional<Emptiness> holdsNothing(StringMachineAnswers machines) {
        return constraints.holdsNothing(spelled(namedBy), machines);
    }

    /**
     * Where each subject sits, as a proof of emptiness says it.
     *
     * <p>A proof names a place to a reader, so what it carries out of here is the spelling — except
     * for the one thing every reading agrees on, which is whether the place is the value itself.
     * That is a case and not an empty spelling, so no reader recovers it by comparing text.
     */
    private static <A> SequencedMap<A, Emptiness.AtAField.Where> spelled(
            SequencedMap<A, RuleKey> named) {
        SequencedMap<A, Emptiness.AtAField.Where> out = new LinkedHashMap<>();
        named.forEach((subject, name) -> out.put(subject,
                name.isTheValueItself() ? new Emptiness.AtAField.Where.TheValueItself()
                        : new Emptiness.AtAField.Where.In(name.toString())));
        return out;
    }

    /**
     * What the record declared as {@code named} leaves its fields able to hold.
     *
     * <p>The declaration is read here rather than handed in. What is written about a record's
     * fields is this reading's question, so the body it is written on is this reading's to fetch —
     * a caller made to fetch one has a declaration in its hands for a question that was never its
     * own, and can read the record's structure back out of it.
     */
    public static FieldDomains of(TypeSymbol.AtModule named, RuleReadingSource source,
                                  ReadingPolicy policy) {
        return of(named, source, policy, Map.of());
    }

    /** The same, asking {@code machines} for what somebody has already made of the declaration's
     *  string rules before building any of it. */
    public static FieldDomains of(TypeSymbol.AtModule named, RuleReadingSource source,
                                  ReadingPolicy policy, DeclarationReadings machines) {
        return of(named, source, policy, Map.of(), machines);
    }


    /**
     * The same, with some fields already settled at a value.
     *
     * <p>Projecting a range and completing an assignment are two questions of one rule set. A row at
     * {@code startsAt = 1439} needs an {@code endsAt} the record will accept beside it, and that is
     * not read off {@code endsAt}'s own range — which still runs from 1 — but off what is left of it
     * once the other end is fixed, which is 1440 and nothing else.
     */
    public static FieldDomains of(TypeSymbol.AtModule named, RuleReadingSource source,
                                  ReadingPolicy policy, Map<RuleKey, Count> settled) {
        return of(named, source, policy, settled, DeclarationReadings.NONE);
    }

    /** The same, asking {@code machines} first. */
    public static FieldDomains of(TypeSymbol.AtModule named, RuleReadingSource source,
                                  ReadingPolicy policy, Map<RuleKey, Count> settled,
                                  DeclarationReadings machines) {
        // A name declaring no record leaves nothing about fields it has not got, which is what
        // nothing written comes to here. The same answer the other readers of a declaration give
        // when handed such a name, because it is the same fact about the name rather than three
        // opinions about the caller.
        return source.symbols().declaredNode(named.key()) instanceof Hir.Data data
                ? of(named, data, source, policy, atValues(settled),
                        InvariantChecker.Reach.EVERYTHING, machines)
                : NONE;
    }

    /** Settlings written as names, read as what stands at each. What a caller naming a place means
     *  is the value there; a count taken of one is a coordinate it has to name. */
    private static Map<NumberAt<RuleKey>, Count> atValues(Map<RuleKey, Count> settled) {
        Map<NumberAt<RuleKey>, Count> out = new LinkedHashMap<>();
        settled.forEach((path, at) -> out.put(NumberAt.valueOf(path), at));
        return out;
    }

    /**
     * The same, with the declarations {@code granted} names supposed to hold values.
     *
     * <p>What a reader asking "would this hold anything if that one did" needs. A value said to have
     * none is one whose rules say so, and those rules are read wherever it is reached — its own and
     * the ones under whatever it wraps — so supposing it has a value is not reading it at all. A
     * record holding it is otherwise told it holds nothing by the very rules the supposing was
     * about.
     */
    static FieldDomains granting(TypeSymbol.AtModule named, Hir.Data data, RuleReadingSource source,
                                 ReadingPolicy policy,
                                 Set<TypeSymbol> granted,
                                 DeclarationReadings machines) {
        return of(named, data, source, policy, Map.of(),
                InvariantChecker.Reach.stoppingAt(granted), machines);
    }

    /** The same, reading only as far as {@code reach} says — see {@link #narrowedBy}. */
    private static FieldDomains of(TypeSymbol.AtModule named, Hir.Data data, RuleReadingSource source,
                                   ReadingPolicy policy, Map<NumberAt<RuleKey>, Count> settled,
                                   InvariantChecker.Reach reach, DeclarationReadings machines) {
        // A newtype is read the same way, and only its bounds are not worth handing back: its value
        // is the value it is, so there are no siblings to relate. Everything else is the same
        // question — its own rules can hold a hole no range keeps, and they can contradict, and both
        // answers were being given away by treating it as a value with nothing to say.
        InvariantChecker.Seeded seeded =
                InvariantChecker.seedFields(named, source, policy, settled, reach, machines);
        Map<RuleKey, NumericDomain.Bounds> out = new LinkedHashMap<>();
        seeded.atoms().forEach((field, atom) -> {
            // The value itself is at no name of its own, and its range is the one thing not worth
            // handing back: it is the same value this is of, so there is no sibling to relate it
            // to. What sits under it is another matter — a record inside a newtype has fields, and
            // they are named with ranges like any other.
            if (field.isTheValueItself()) {
                return;
            }
            NumericDomain.Bounds bounds = seeded.numbers().boundsOf(atom);
            if (!bounds.saysNothing()) {
                out.put(field, bounds);
            }
        });
        // Which values may stand at each name is read off the reading and not worked out here. What
        // a name admits is the sets of the subjects it is filed under met, which takes a machine —
        // and the purse that pays for it is the one the clauses were read under, which is the
        // reading's and stays there.
        //
        // Every one of them and not only the fields: what a name wraps is at no name of its own,
        // and it is what a reader of a newtype asks about.
        //
        // Resolved here rather than handed over as atoms. An atom is a name the seeding gave a shape
        // and means nothing once the reading that named it is gone, so a caller holding one could
        // only ask the domain it came from — which is this one, while it is still here.
        Map<RuleKey, NumericDomain.Bounds> holds = new LinkedHashMap<>();
        seeded.heldAtoms().forEach((field, atom) -> {
            if (field.isTheValueItself()) {
                return;
            }
            NumericDomain.Bounds bounds = seeded.numbers().boundsOf(atom);
            if (!bounds.saysNothing()) {
                holds.put(field, bounds);
            }
        });
        // Classifying the rules is a second reading of every one of them, and the bounds are the
        // whole of what a caller filling a row needs. Asked when the answer is, and not before.
        // Every subject a name answers to, filed under the name, in the order the value declares
        // them. A proof that names a place is settled by this order: read off a domain's own map,
        // the place named would be the one whose clause was read first.
        SequencedMap<FactSubject, RuleKey> placeOf = new LinkedHashMap<>();
        seeded.written().forEach(field ->
                seeded.named(field).forEach(term -> placeOf.putIfAbsent(term, field)));
        return new FieldDomains(Map.copyOf(out), Map.copyOf(holds), Map.copyOf(seeded.admitted()),
                Map.copyOf(seeded.unreadAt()), Set.copyOf(seeded.notSeparated()),
                seeded.reading().directs(), seeded.reading().noLines(),
                seeded.reading().withoutAnEnd(), seeded.reading().aboutOneCoordinate(),
                reach.withoutParts(),
                seeded.reading().raised(), seeded.reading().raisedByPart(),
                seeded.reading().standing(), seeded.took(),
                seeded.reading().narrowers(),
                seeded.notGathered(), seeded.handedOn(), placeOf,
                seeded.constraints(), named, data, source, policy, settled,
                seeded.unreadOfEveryValue(), seeded.atoms(), seeded.held(),
                seeded.readings(), seeded.spacing(), seeded.stringMachines(), machines.extents(),
                seeded.endsLeftOpen(), seeded.boundsLeftOpen(), seeded.derived(),
                seeded.settledOrder());
    }

    /**
     * An end one clause of this record places on one coordinate of it, and the rule that placed
     * it.
     *
     * <p>Not a bound the range happens to have. {@link #at} answers what may stand at a name, which
     * every rule reaching it takes part in; this answers which clause said where it stops, which only
     * a clause naming that one coordinate and a constant does. A line may be drawn at one of these
     * and at nothing else (ADR-0090), so handing back the range instead would make a relational rule
     * into a partition of a place it never mentioned.
     *
     * @param at    which number at which name the end is on. The number and not a name beside a
     *              flag: one name carries more than one, and which of them an end is on is what
     *              the operation beside the name says
     * @param part  which part of which rule placed the end, which is what names the line. A part
     *              of a declaration's clause: these are the ends the clauses of a declaration
     *              place, and no other kind of rule reaches this reading
     * @param lower whether this bounds the coordinate below; otherwise above
     */
    public record Placed(NumberAt<RuleKey> at, PartId<RuleRef.Invariant> part, boolean lower,
                         Endpoint end) {

        /** What the value's rules call where the end sits. Never which number it is on: that is
         *  {@link #at}, and reading one off the other is what the pair exists to stop. */
        public RuleKey path() {
            return at.position();
        }
    }

    /**
     * One conjunct this reading recognised as a comparison and got no end out of, handed on for
     * another reading to make what it can of.
     *
     * <p><b>Not {@link NoLine}, and neither one implies the other.</b> That is a finding: this
     * reading owed a line, could not draw one, and says so to an author. This is a hand-over: a
     * conjunct leaves here with nothing settled about it, and what it comes to is the next
     * reading's answer. A rule that names a value is the case that tells them apart — an equality
     * and a disequality place no end and are no failure of anything, so they are handed on and
     * nothing is reported. Built from the findings, the hand-over carried whatever the report
     * happened to have a sentence for, and a rule this reading owes nothing about could not be
     * passed along at all.
     *
     * <p>The conjunct as it was written. Which number it is about, and what it does to that
     * number, are the next reading's to establish in its own vocabulary — said here, this would be
     * the reading that placed no end answering the question it just failed to answer.
     *
     * @param part which part of which rule it is, as the split that wrote the parts down named it
     * @param read the part itself
     */
    public record WithoutAnEnd(PartId<RuleRef.Invariant> part, Core read) {

        public WithoutAnEnd {
            if (part == null || read == null) {
                throw new IllegalArgumentException("a conjunct handed on is some clause's text");
            }
        }
    }

    /**
     * One authored conjunct whose quantity is over exactly one coordinate.
     *
     * <p><b>Read off the canonical quantity and not off how a side was spelled.</b> Which number a
     * rule is about is what its arithmetic came to: {@code value * 2 >= 4} is about the value and
     * leaves it at two, and a reader looking for a bare name on one side finds none and calls it a
     * rule about nothing. The same reading was already made for a {@code guard}'s comparison, where
     * {@code a + 1 <= 10} had been classified as naming no position.
     *
     * <p><b>Whether an end was read from it is no part of what this is.</b> A rule ordering the
     * values, one naming a value, one holding the value away from one and one whose arithmetic no
     * end could be read from are four ways of leaving a coordinate somewhere, and which of them is
     * which decides nothing about who accounts for where it stops: {@code value >= 2} and
     * {@code value * 2 >= 4} each put the values at two, and a population split by end-shape can
     * attribute the end to one of them and not the other. Written into the identity, that split
     * comes back as "this one is direct, so it is no candidate".
     *
     * <p>Exactly one coordinate. A rule over a form on two of them is about the pair, and an end
     * attributed to it at either would be an end of a number the rule does not divide — that rule
     * draws its line as a relation and is owed a row there instead.
     *
     * <p>What such a rule does to the number is not here and is not this reading's: it is read by
     * asking what the rules leave the coordinate without it, and comparing ({@link #movedEndsOf}).
     *
     * <p><b>The part and not what inside it was read.</b> This is what a counterfactual reading is
     * asked without, and what such a reading can be asked without is a part its author wrote: an
     * author told a rule holds an end rewrites the conjunct they typed, and half of a rule named
     * through a helper is not something they can take away. So a part stating two rules that both
     * reach one number is one candidate here — carrying the subtree each was read from, it was two,
     * and a reading asked without either of them was asked without the part they share, which named
     * the same part twice as holding an end it holds once.
     *
     * @param at   the number its quantity is over
     * @param part which part of which rule it is
     */
    public record AboutOneCoordinate(NumberAt<RuleKey> at, PartId<RuleRef.Invariant> part) {

        public AboutOneCoordinate {
            if (at == null || part == null) {
                throw new IllegalArgumentException("a quantity over one number is some rule's");
            }
        }

        /** Which authored line this is a part of, which is what tells a candidate from an end
         *  the reading of comparisons already placed. */
        Line line() {
            return new Line(part);
        }
    }

    /** One authored line: which part of which rule drew it. */
    record Line(PartId<RuleRef.Invariant> part) {}

    /**
     * A rule about where one coordinate's values stop that this reading placed no end from, and
     * what stopped it.
     *
     * <p>The other half of {@link Placed} and produced by the same reading of the same clause, which
     * is what keeps the two from disagreeing about what a rule is. Read by a walk of its own, a
     * second reader answered for the clauses on the type standing at a name and knew nothing of the
     * ones written on the value it sits in — so a clause of a record was dropped without a word
     * while a {@code guard} of the same shape named both the places it compared (ADR-0090).
     *
     * <p>One per name the rule writes, since a rule relating two coordinates is filed under
     * neither of them alone.
     *
     * @param at   where the coordinate sits, and which of the numbers there the end was to be on.
     *             One name carries both — a {@code String} bounded on its length has an end on the
     *             count and values of its own — and a rule stopped at one of them is no account of
     *             the other, so the two travel together
     * @param part which part of which rule this is. A rule is read a part at a time and a reason
     *             belongs to the one it came out of: asked of the rule and the name alone,
     *             {@code x <= y && x <= 10 * 2} said its bound went unread because a comparison
     *             relates two places, which is what the part beside it does. Named rather than read
     *             back off the text: a reader holding the expression alone has no way to say which
     *             of two identical parts it is
     * @param read the part itself
     * @param why  what would have to change before this rule could be a line, in this compiler's
     *             own terms
     */
    public record NoLine(NumberAt<RuleKey> at, PartId<RuleRef.Invariant> part, Core read,
                         souther.compiler.inputs.BlockReason.RuleWithoutLineReason why) {

        /** What the value's rules call where the end was to have been placed. */
        public RuleKey path() {
            return at.position();
        }
    }

    /**
     * One rule, one coordinate: the question of where the values there stop.
     *
     * <p>What a reader of an accounting asks about, and the key its answer is held under. A rule is
     * read a conjunct at a time and any number of them may draw the same line, so the parts are not
     * this — held under them, an answer had to be put back together from whatever rows a finer key
     * happened to hold.
     */
    public record BoundaryQuestion(RuleRef.Invariant from, NumberAt<RuleKey> at) {}

    /**
     * A boundary question the reading of ends did not answer, and everything behind it.
     *
     * <p><b>One reason and several conjuncts, which are two different multiplicities.</b> Which
     * limit stopped the reading is read off the coordinate — a carrier lines are drawn on wants a
     * reader for the form, and one nothing draws a line on wants the carrier
     * ({@link UnreadComparison#whereALineWouldFall}) — so every part of the rule that raises this
     * question comes to the same word. How many parts are standing behind it is what an author has
     * left to lift, and that is its own count.
     *
     * <p>Made where the question is raised and not gathered from the findings afterwards. Gathered,
     * the answer was whatever the rows filed under a finer key came to, and the day two of them
     * differed a reader would have been handed both with nothing saying which the question's is.
     *
     * <p><b>A part met afterwards adds itself and cannot bring a reason.</b> {@link #and} takes the
     * part and nothing else, so there is never a second word to reconcile — and no place where one
     * could be dropped for being second. Written the other way, with each part making a whole
     * answer and the two merged, the merge had to choose, and choosing quietly is a worse version
     * of the gathering this replaced: it turns a producer that has come apart into one word decided
     * by the order the conjuncts are written in.
     *
     * @param conjuncts the parts of the rule still standing behind it, in the order they are
     *                  written
     */
    public record BoundaryStanding(
            souther.compiler.inputs.BlockReason.RuleReadingStopped why,
            List<PartId<RuleRef.Invariant>> conjuncts) {

        public BoundaryStanding {
            if (why == null) {
                throw new IllegalArgumentException("a question left standing says why");
            }
            if (conjuncts.isEmpty()) {
                throw new IllegalArgumentException(
                        "and says which part of the rule is standing behind it");
            }
            conjuncts = List.copyOf(conjuncts);
        }

        /** The same answer, with {@code part} standing behind it too. */
        BoundaryStanding and(PartId<RuleRef.Invariant> part) {
            if (conjuncts.contains(part)) {
                return this;
            }
            List<PartId<RuleRef.Invariant>> both = new ArrayList<>(conjuncts);
            both.add(part);
            return new BoundaryStanding(why, both);
        }
    }

    /**
     * The declarations that moved where the coordinate at {@code path} stops, and none where the
     * ones relating it to something else left it where it would be without them.
     *
     * <p>Which declaration wrote the relation, and not which value the coordinate sits in. The same
     * relation can be written on the record, on a record inside it, or on a name wrapped round
     * either, and an edge said to have been taken in by a declaration holding no clause about the
     * pair sends a reader to a line that is not there.
     *
     * <p>Several where several hold it, in one order whoever found them. Which of them settled the
     * number is not always a question this can answer — a bound arrives along a path through the
     * differences and clauses can reach an end only together — and {@link EndNarrowing} says which
     * of its questions an answer came out of, including the one where none of them told the
     * candidates apart.
     */
    private List<TypeSymbol.AtModule> narrowedBy(RuleKey path, boolean lower) {
        List<TypeSymbol.AtModule> candidates = narrowers.get(path);
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        NumericDomain.Bounds here = byName.get(path);
        Endpoint end = here == null ? null : lower ? here.min() : here.max();
        if (end == null) {
            return List.of();
        }
        return EndNarrowing.read(end, candidates,
                removed -> endWithout(removed, path, lower)).names();
    }

    /**
     * Where the coordinate at {@code path} stops on the side asked for, read again without the
     * clauses of the declarations {@code removed} names.
     *
     * <p>The one way a counterfactual reading is asked for here. Which reading an end is compared
     * against is the whole of what {@link EndNarrowing} means by an answer, and a second way of
     * standing one up is a second place for that comparison to be written a different way round.
     */
    private Endpoint endWithout(Set<TypeSymbol.AtModule> removed, RuleKey path, boolean lower) {
        NumericDomain.Bounds bounds = withoutClausesOf(removed).byName.get(path);
        return bounds == null ? null : lower ? bounds.min() : bounds.max();
    }

    /** This value read again without the clauses of the declarations {@code removed} names. */
    private FieldDomains withoutClausesOf(Set<TypeSymbol.AtModule> removed) {
        return counterfactual(new LeftOut.ClausesOf(removed));
    }

    /**
     * What a counterfactual reading leaves out, which is the whole of what decides what it comes
     * to.
     *
     * <p>Two kinds of omission and one identity. A declaration's clauses are left out to ask which
     * declaration is holding an end, and authored conjuncts are left out to ask what those
     * conjuncts were holding — and what either reading comes to is decided by which rules are gone:
     * not by the name the question was asked at, and not by which side of a coordinate is being
     * attributed. Said as a value, that is what a reading is kept under, and a third thing to leave
     * out has to say what it leaves out before it can be one of these at all.
     */
    private sealed interface LeftOut {

        /** How far the reading that leaves this out reaches. */
        InvariantChecker.Reach reach();

        /** Everything these declarations wrote, wherever it was read. */
        record ClausesOf(Set<TypeSymbol.AtModule> declarations) implements LeftOut {

            public ClausesOf {
                declarations = Set.copyOf(declarations);
            }

            @Override
            public InvariantChecker.Reach reach() {
                return InvariantChecker.Reach.withoutClausesOf(declarations::contains);
            }
        }

        /** These conjuncts of the rules, everything else the declaration says being read. */
        record Conjuncts(Set<PartId<RuleRef.Invariant>> parts) implements LeftOut {

            public Conjuncts {
                parts = Set.copyOf(parts);
            }

            @Override
            public InvariantChecker.Reach reach() {
                return InvariantChecker.Reach.withoutParts(parts);
            }
        }
    }

    /**
     * This value read again with {@code omitted} left out, made when the first question asks for
     * it.
     *
     * <p>The one place a counterfactual of this reading is stood up, so that what one is — the
     * declaration, its source, what it may spend, what it leaves out, and what it is handed rather
     * than builds ({@link #borrowingMachines}) — is settled once. The
     * questions that leave the same rules out get the reading that was made: both ends of a
     * coordinate leave the same rules out, a declaration reaching several of a record's names
     * leaves them out again at each of them, and the questions {@link EndNarrowing} puts ask for
     * the reading without every candidate and the one without a single candidate, which where
     * there is one candidate are the same reading.
     */
    private FieldDomains counterfactual(LeftOut omitted) {
        return counterfactuals.computeIfAbsent(omitted,
                left -> of(named, data, source, policy, settled, left.reach(), borrowingMachines()));
    }

    /**
     * What a counterfactual of this reading borrows: this declaration's machines as this reading
     * came to them, and nothing else.
     *
     * <p>This declaration's, because they are what is here. Another declaration's are a store's
     * answer about it, and asking for one takes a store, which a reading standing inside a
     * comparison has no way to reach — so a reading of one of those borrows nothing and keeps what
     * it builds, which is what {@link StringMachineAnswers#unborrowed} is.
     *
     * <p>What the revision has worked out about sets goes with it either way. A counterfactual
     * meets the sets its reading met wherever what it leaves out is about something else, and where
     * a set stops is settled by the set — so a walk here is one the revision has already made.
     *
     * <p>Nothing of the reading itself is lent. What a counterfactual is depends on what it leaves
     * out, so no counterfactual is the declaration's canonical reading and none is kept as one —
     * which is what {@link InvariantChecker#seedFields} settles by looking at the reach it was
     * handed.
     */
    private DeclarationReadings borrowingMachines() {
        return new DeclarationReadings() {

            @Override
            public StringMachineAnswers of(TypeKey declaration) {
                return declaration.equals(named.key())
                        ? StringMachineAnswers.borrowing(stringMachines, known)
                        : StringMachineAnswers.unborrowed(known);
            }

            @Override
            public KnownExtents extents() {
                return known;
            }
        };
    }

    /**
     * The same rules with these coordinates settled at these values, for the questions that read
     * the constraints themselves.
     *
     * <p><b>The clauses are not read again.</b> A settling is an equality on an atom taken onto
     * everything else the clauses came to, which is exactly what the reading does with one at the
     * end of its own work ({@link ConstraintState#settling}) — so stating it here and stating it
     * there are the same statement, and reading a declaration once per settled number is paying
     * for the clauses over again to arrive where this already is.
     *
     * <p>What comes back answers about the constraints and not about a reading. Where a form runs
     * and whether anything is left are read off the rules themselves; what a reading derives beside
     * them — which values may stand at a name, what it must hold, which rule placed an end — is not
     * recomputed and is not offered, so nothing can read a settled state for an answer that was
     * worked out before the settling.
     */
    public Settled given(Map<NumberAt<RuleKey>, Count> fixed) {
        ConstraintState<FactSubject> taken = constraints;
        for (Map.Entry<NumberAt<RuleKey>, Count> each : fixed.entrySet()) {
            NumberAt<RuleKey> where = each.getKey();
            FactSubject atom = subjectAt(where.position(), where.of());
            souther.compiler.numeric.Granularity spaced =
                    atom == null ? null : spacing.get(atom);
            // A coordinate no range is taken of here settles nothing, which is less than the caller
            // said and is the safe direction: what is left is wider than what they asked about.
            if (spaced != null) {
                taken = ConstraintState.settling(taken, atom, each.getValue(), spaced);
            }
        }
        return new Settled(taken, namedBy, atomAt, countAt);
    }

    /**
     * The rules of one value with some of its coordinates settled.
     *
     * <p>Not a {@link FieldDomains}. What a reading of a declaration hands over is derived from the
     * constraints and would have to be derived again under a settling; what a caller settling one
     * wants is the constraints themselves. Kept apart so that the derived answers cannot be read
     * off a state they were not worked out under.
     */
    public static final class Settled {

        private final ConstraintState<FactSubject> constraints;
        private final SequencedMap<FactSubject, RuleKey> namedBy;
        private final Map<RuleKey, FactSubject> atomAt;
        private final Map<RuleKey, Counted> countAt;

        private Settled(ConstraintState<FactSubject> constraints,
                        SequencedMap<FactSubject, RuleKey> namedBy,
                        Map<RuleKey, FactSubject> atomAt, Map<RuleKey, Counted> countAt) {
            this.constraints = constraints;
            this.namedBy = namedBy;
            this.atomAt = atomAt;
            this.countAt = countAt;
        }

        /**
         * The rules these came to, about the same subjects under a caller's names.
         *
         * <p>For a caller that holds the readings of several values at once and has to say what they
         * leave together. What a rule says is a relation between subjects, and it says the same
         * thing whatever they are called — so what is handed over is these rules renamed and never a
         * fresh reading of the declaration.
         *
         * <p><b>The whole state and not the numbers alone.</b> Which values a name admits, which
         * predicates hold and where an ordering stops are as much a part of what the rules leave as
         * the arithmetic is, and a caller given the numbers alone would have to ask this reading
         * whether anything is left — which makes two answerers of one question, the weaker of them
         * the one with a place to name, and which of them speaks settled by the order they are asked
         * in. Handed over whole, the caller has one state to ask and this has none.
         *
         * <p><b>Every subject a rule is about is carried, whether or not the caller has a coordinate
         * for it.</b> A reading relates subjects at coordinates and subjects at none, and a rule
         * reaching one of the latter still holds two of the former apart: left behind, that rule
         * would be gone and what the rules leave would come back wider than it is. So a subject with
         * no coordinate is named by {@code otherwise}, out of the subject itself, and the caller is
         * given something to be equal to rather than something to read — what makes two of them one
         * subject was settled here, and a caller inventing its own answer to that would put two
         * apart or two together.
         *
         * <p>The naming is held to naming two subjects two subjects, across every domain of
         * <em>this</em> reading at once ({@link InjectiveRenaming}). A caller whose {@code named}
         * and {@code otherwise} send two of these subjects to one name is told so rather than handed
         * a state where a predicate of one subject settles another and an ordering of one bounds
         * another. What keeps two readings apart is not this — each of them is renamed under a
         * renaming of its own — and is whatever the caller's names carry of where a subject came
         * from.
         */
        public <B> Carried<B> constraintsOver(
                java.util.function.Function<NumberAt<RuleKey>, B> named,
                                              java.util.function.Function<Object, B> otherwise) {
            Map<FactSubject, NumberAt<RuleKey>> where = new LinkedHashMap<>();
            atomAt.forEach((path, atom) -> at(where, atom, NumberAt.valueOf(path)));
            countAt.forEach((path, counted) -> at(where, counted.atom(),
                    NumberAt.takenOf(path, counted.by())));
            // And every other subject this reading knows a name for, which is what a caller can
            // name and what these two maps are narrower than: they hold the numbers, and a name
            // holds whatever stands there. Left to `otherwise`, a subject of a name would be
            // carried as something to be equal to and nothing more — so a rule of one value about a
            // name its cases share and a rule of the case about the same name would arrive as two
            // subjects, and every reading that has no word for a number would stop meeting at the
            // narrowing.
            namedBy.forEach((atom, path) -> {
                if (!where.containsKey(atom)) {
                    at(where, atom, NumberAt.valueOf(path));
                }
            });
            InjectiveRenaming<FactSubject, B> naming = InjectiveRenaming.of(atom -> {
                NumberAt<RuleKey> claim = where.get(atom);
                return claim == null ? otherwise.apply(atom) : named.apply(claim);
            });
            // Spelled, because what a caller does with these is name a place to a reader. The
            // names themselves belong to the value whose rules these are, and a caller holding the
            // state has renamed its subjects to its own.
            SequencedMap<B, String> carried = new java.util.LinkedHashMap<>();
            namedBy.forEach((atom, path) -> carried.put(naming.apply(atom), path.toString()));
            return new Carried<>(constraints.renamed(naming), carried);
        }

        /**
         * One number filed at one coordinate.
         *
         * <p>Refused rather than overwritten where a subject arrives at two of them. Two coordinates
         * that are one number is this reading saying something a caller has two names for, and
         * whichever of them went unnamed would be a coordinate the constraints say nothing about —
         * so what the caller was told the rules leave there would be everything.
         */
        private static void at(Map<FactSubject, NumberAt<RuleKey>> where, FactSubject atom,
                               NumberAt<RuleKey> claim) {
            NumberAt<RuleKey> had = where.put(atom, claim);
            if (had != null && !had.equals(claim)) {
                throw new IllegalStateException("one number is at `" + had.position() + "` and at `"
                        + claim.position() + "`, so neither name is the whole of it");
            }
        }
    }

    /**
     * One value's rules in a caller's vocabulary, and what each of its subjects is called there.
     *
     * <p>The two together because they are read together and would disagree apart. A proof that
     * nothing is left names a place by looking a subject up in these, so a caller holding a state
     * renamed one way and these renamed another would have a proof pointing at a place the state
     * has no rule about — and nothing would say so, because both halves are well formed.
     *
     * @param named what the declaration's own rules call the place each subject is at, spelled.
     *              What a caller out here calls the same place is that caller's to write, since it
     *              is the one that knows what the value it read is a part of
     */
    public record Carried<B>(ConstraintState<B> constraints, SequencedMap<B, String> named) {

        public Carried {
            named = java.util.Collections.unmodifiableSequencedMap(
                    new java.util.LinkedHashMap<>(named));
        }
    }

    /**
     * What each clause reaching this value raises, keyed on the rule it is.
     *
     * <p>Questions and not answers. A clause is here whether or not anything took it in, which is
     * what makes it a list of what has to be settled rather than a list of what this compiler
     * managed — the second is what a completeness written per reader amounts to, and it says the
     * model was read in full for exactly as long as nobody adds a reader.
     */
    public Map<RuleRef.Invariant, Required> required() {
        return raised;
    }

    /**
     * Whether any rule at all was written about this value.
     *
     * <p>The declarations it spreads included, because a clause of one of those is a rule of every
     * value that spreads it — asked of the declaration's own clauses, a case that writes none of its
     * own would come back having said nothing while a spread clause of its was being read here.
     *
     * <p>Its own answer and not one read off something else. That a declaration is one clauses may
     * be written on is a different question, and every {@code data} answers it yes; that the rules
     * leave the bounds exactly representable is another, and a value with no rules answers it yes as
     * well, having lost nothing on the way to a box. Either taken for this says a value spoke when
     * it did not.
     */
    public boolean anythingWasWritten() {
        return !accounting().isEmpty();
    }

    /**
     * Every rule reaching this value, every question it raises, and what answered each.
     *
     * <p>The questions come from the rules and the answers from whichever reading took the rule in.
     * Which is the whole arrangement: an ordering bound and an equality raise the same question
     * about which values may stand at a name, and it is answered by the reading of ends in the
     * first case and by the reading of values in the second — so a completeness read off either
     * reading alone reports a model that was read in full as one this compiler could not read.
     */
    public Map<RuleRef.Invariant, RuleAccounting> accounting() {
        Map<RuleRef.Invariant, RuleAccounting> had = accounting;
        if (had != null) {
            return had;
        }
        Map<RuleRef.Invariant, RuleAccounting> out = new LinkedHashMap<>();
        raised.forEach((rule, required) ->
                out.put(rule,
                        RuleAccounting.of(rule, required, owed -> answered(rule, owed))));
        // Insertion order, which is the order the declaration writes its clauses. `Map.copyOf`
        // iterates in an order salted once per JVM run, and these reach a checked-in document.
        accounting = java.util.Collections.unmodifiableMap(out);
        return accounting;
    }

    /**
     * What answered one question of one rule.
     *
     * <p>One arm each, and no arm to fence off. A clause of a `data` is written about a name of
     * it or about a number at one, which is what the readings here answer about; a place between two
     * numbers is a comparison's and reaches no accounting of one value's clauses. That was a throw
     * here while the question was an obligation beside a subject and the pair admitted combinations
     * nothing raises.
     */
    private RuleAccounting.Outcome answered(RuleRef.Invariant rule, Owed owed) {
        return switch (owed) {
            case Owed.AdmittedValues it -> admissionAnswered(rule, it.path());
            case Owed.Boundary it -> boundaryAnswered(rule, it.on());
        };
    }

    /**
     * What answered "where does the line fall" for one rule at one name.
     *
     * <p>The reading that turns a clause into an end, asked for its own account. It keeps one where
     * a rule says where the values stop and no end came of it ({@link NoLine}), so the absence of
     * one is the reading having got there: either it placed the end, or it read the rule to the end
     * and found the order stops past where the rule points. The second is the line understood and
     * not a reading that fell short — whether a value can be written at it is a question about
     * composing a row, and no rule answers for that (#854).
     *
     * <p>Asked per rule and per name, as the admission question is. A bound on a field's own
     * type and a clause of the record about the same field are two rules, and an end read for one
     * says nothing about the other.
     *
     * <p><b>Looked up and not worked out.</b> The reading made this answer where it raised the
     * question, so what is here is a lookup under the question's own key. Put together from the
     * findings instead, an answer keyed at {@code (rule, coordinate)} was rebuilt out of rows keyed
     * at {@code (rule, conjunct, coordinate)} and came to whatever those rows held — which made a
     * question's word depend on a table nobody had asked it of, and left every reader downstream
     * looking at a list where the model has one answer.
     */
    private RuleAccounting.Outcome boundaryAnswered(RuleRef.Invariant rule,
                                                    NumberAt<RuleKey> where) {
        BoundaryStanding said = rule instanceof RuleRef.Invariant invariant
                ? standing.get(new BoundaryQuestion(invariant, where)) : null;
        return said == null
                ? new RuleAccounting.Outcome.Accounted(RuleAccounting.Reader.THE_END_READING)
                : new RuleAccounting.Outcome.Unaccounted(
                        new RuleAccounting.Why.TheEndReadingSays(said));
    }

    /**
     * What answered "which values may stand here" for one rule at one name.
     *
     * <p>Any reading that took the rule in will do, and that is the whole of it. A question is
     * unanswered exactly where no reading adopted the clause — not where the reading that names the
     * question was short of the rules at that name, which is a fact about that reading and is true
     * at every number an invariant bounds.
     *
     * <p>Asked per rule and never per name. One clause's failure is not the account of the
     * clause beside it: {@code value >= 1} leaves the reading of values short at a name, and
     * {@code value == 7} written beside it was taken in whole.
     */
    private RuleAccounting.Outcome admissionAnswered(RuleRef.Invariant rule, RuleKey at) {
        List<FactSubject> named = named(at);
        // A part of the rule nothing took in outranks everything else about it. An end placed by
        // one conjunct is not an account of the conjunct written beside it.
        if (took.anyLeftStanding(rule, named)) {
            return new RuleAccounting.Outcome.Unaccounted(
                    stoppedBy(rule, at, named));
        }
        // The reading that turns this clause into where the values stop, said by the end it placed.
        if (directs.stream()
                .anyMatch(d -> d.part().rule().equals(rule) && d.path().equals(at))) {
            return new RuleAccounting.Outcome.Accounted(RuleAccounting.Reader.THE_END_READING);
        }
        // And the readings that hold what a clause says about the values themselves, each said by
        // that reading at the point it adopted the clause.
        if (took.tookIn(rule, named)) {
            return new RuleAccounting.Outcome.Accounted(RuleAccounting.Reader.THE_VALUE_READING);
        }
        return new RuleAccounting.Outcome.Unaccounted(stoppedBy(rule, at, named));
    }

    /**
     * Everything the reading of values was stopped by, of {@code rule} at {@code named}.
     *
     * <p>What a rule is answerable for is asked of the rule and never of the name. Two clauses
     * reach one name and are short of this reading in two ways, and the name holds what both of
     * them came to — so a rule answered from there is named beside a limit that belongs to its
     * neighbour, which is the misattribution the whole accounting is asked per rule to avoid.
     *
     * <p><b>And what the name holds that no rule is answerable for, which is not a fallback.</b>
     * An allowance run down by everything a position admits is a fact about the answer and not
     * about any rule that paid into it ({@link UnreadReason.About#THE_ANSWER}), so
     * {@link ReadingEvidence#stoppedBy} refuses such a reason rather than filing it under a rule
     * and it is read off the name instead. It still
     * accounts for this question: the rule was read and the values its position may hold were not
     * worked out, so the question stands whatever else does. Taken only where the rule had nothing
     * of its own, a rule short in both ways went out short in one — an author rewrites the form and
     * the position is as wide as it was, for a limit nothing named.
     *
     * <p>Each in its own carrier, and neither made into the other. What a rule is answerable for
     * stands at a place an author wrote and what the answer was short of stands at no place at all,
     * so the two are held apart all the way out and a reader is never offered a source to look at
     * for the half that has none.
     *
     * <p>Both empty is the accounting coming apart. The rule was met by the walk that asks and by
     * nothing that reads, and neither the rule nor the position has a word for it.
     */
    private RuleAccounting.Why stoppedBy(RuleRef.Invariant rule, RuleKey at,
                                         List<FactSubject> named) {
        // What a rule is answerable for, as the facts it is answerable for. Asked for the reasons
        // alone here, the written places they were decided at would be gone one call before the
        // account that names the rule, and two facts about two clauses would arrive as one.
        Set<RuleShortfall> why = took.stoppedBy(rule, named);
        // And what the answer this question waited on was short of, which is the name's and is
        // taken beside the rule's rather than where the rule has none of its own.
        Set<UnreadReason> answered = new LinkedHashSet<>();
        unreadByName.getOrDefault(at, List.of()).stream()
                .filter(FieldDomains::standsBesideARulesOwnAccount)
                .forEach(answered::add);
        if (why.isEmpty() && answered.isEmpty()) {
            throw new AStandingQuestionWithNoAccount(rule, named);
        }
        return new RuleAccounting.Why.TheValueReadingSays(why, new AnswerShortfalls(answered));
    }

    /**
     * Whether {@code why} accounts for a question a rule left standing without being filed under
     * the rule.
     *
     * <p>Named and asked once, because it is the whole of what the name is read for above: written
     * inline as a test of the shape a reason is not, a reason of the third kind would account for a
     * question out of a place nothing looked at. Its three answers are the three kinds a reason is
     * about, so a reason added to any of them is decided here rather than by where it happens to be
     * written.
     *
     * <p>A reason about the answer does: an allowance run down by everything a position admits is a
     * fact about what the rules come to and about none of them, so no rule is answerable for it and
     * none is filed — and the question of every rule whose position waited on that answer stands on
     * it. A reason about a rule does not come this way, being filed under its rule already. A reason
     * about neither accounts for nothing, which is what it says: the reading never got to the
     * position, so there is no question of a rule there for it to be an account of.
     */
    static boolean standsBesideARulesOwnAccount(UnreadReason why) {
        return switch (why.about()) {
            case THE_ANSWER -> true;
            case A_RULE, NEITHER -> false;
        };
    }

    /**
     * A question left standing that neither a rule nor the answer has an account of.
     *
     * <p>Two walks and one clause. A rule reaches this reading, which either adopts it or records
     * where it gave up; a question stands where nothing adopted it. So a question standing with no
     * account under the rule and none at the name either is the two coming apart — the rule was met
     * by the walk that asks and by nothing that reads.
     *
     * <p>Not a rule this has no words for. A question stands and nothing accounts for it, which is
     * two of this compiler's accounts disagreeing about what it asked. Answered as a rule it cannot
     * read, an author is told that this compiler has no word for what they wrote — a sentence about
     * their model, printed because of something that happened here.
     */
    static final class AStandingQuestionWithNoAccount extends IllegalStateException {

        private static final long serialVersionUID = 1L;

        AStandingQuestionWithNoAccount(RuleRef rule, List<FactSubject> named) {
            super("a question stands that nothing accounts for: " + rule + " at " + named);
        }
    }

    /** Every subject the place at {@code path} answers to. */
    private java.util.List<FactSubject> named(RuleKey path) {
        return namedBy.entrySet().stream().filter(e -> e.getValue().equals(path))
                .map(Map.Entry::getKey).toList();
    }

    /**
     * Every end the rules place, wherever it is.
     *
     * <p>The ends read off an ordering, and the surviving ends the conjuncts about a number account
     * for. Both are ends the declaration's rules put where they are, and which of the two an end
     * came from is not a difference a reader of ends has any use for:
     * {@code String.length(value) /= 0} leaves the length starting at one exactly as
     * {@code String.length(value) >= 1} does, and a row at one is owed to whichever of them the
     * author wrote.
     *
     * <p>Not read off the comparison, which is what tells them apart at the other end of the
     * question. Where an ordering places an end is in the rule; which conjuncts account for where
     * the values actually stop is in everything the rules say together, and it is read by asking
     * what they leave without them ({@link #movedEnds}).
     *
     */
    public List<Placed> placed() {
        List<Placed> out = new ArrayList<>(stated());
        out.addAll(movedEnds());
        return List.copyOf(out);
    }

    /**
     * The ends the conjuncts state, each against the conjunct that states it.
     *
     * <p>Apart from {@link #movedEnds}, and the difference is where the answer comes from. An end
     * here is in the conjunct: an ordering places one, and a rule about the strings at a position
     * leaves them running from one place to another. Those are read off the rule alone, so which
     * conjunct they belong to needs no working out. What the other list holds is the ends the rules
     * leave together, attributed by asking what they would leave without each conjunct.
     *
     * <p>For a reader that has its own way to the first kind and wants only what that way cannot
     * see. A newtype's own comparisons are read off the clauses as they are written
     * ({@link DeclaredBounds#of}), and a rule stating no comparison is invisible there — so such a
     * reader takes these and drops what it already has, which two ends at one value with one rule
     * behind them come to anyway ({@link DeclaredBounds.End#tighter}).
     */
    public List<Placed> stated() {
        return directs.stream()
                .map(each -> new Placed(each.at(), each.part(),
                        each.bound().lower(), each.bound().end()))
                .toList();
    }

    /**
     * The surviving ends, each written down against the conjuncts that account for it.
     *
     * <p>Apart from the ends an ordering placed, because one reader wants them apart. A newtype's
     * own value has its ends read off the clauses as they are written ({@link DeclaredBounds#of}),
     * and an end that reader cannot see is exactly one no comparison places — so what is handed to
     * it is these, and handing it the whole list would state every other end twice.
     *
     * <p>Worked out once. Each of them costs a reading of the declaration, and both the readers
     * that want them apart and the ones that want them together ask through here.
     */
    public List<Placed> movedEnds() {
        List<Placed> had = moved;
        if (had != null) {
            return had;
        }
        List<Placed> out = new ArrayList<>();
        // A reading standing in for a counterfactual answers none of these. It exists to say what
        // the conjuncts of the reading above it were holding; asked the same question, it would
        // read itself again without some of its own and never come back.
        if (!withoutParts.leavesAnythingOut()) {
            byCoordinate().forEach((at, candidates) -> {
                if (!needsAttributing(candidates)) {
                    return;
                }
                NumericDomain.Bounds with = leftAt(at.position(), at.of());
                if (with == null) {
                    return;
                }
                accountedFor(at, candidates, with.min(), true, out);
                accountedFor(at, candidates, with.max(), false, out);
            });
        }
        List<Placed> answer = List.copyOf(out);
        moved = answer;
        return answer;
    }

    /** Every conjunct about one number, gathered by the number it is about. */
    private Map<NumberAt<RuleKey>, List<AboutOneCoordinate>> byCoordinate() {
        Map<NumberAt<RuleKey>, List<AboutOneCoordinate>> byNumber = new LinkedHashMap<>();
        aboutOneCoordinate.forEach(each ->
                byNumber.computeIfAbsent(each.at(), _ -> new ArrayList<>()).add(each));
        return byNumber;
    }

    /**
     * Whether the conjuncts about one number are attributed here at all.
     *
     * <p><b>A dispatch and not a shortcut.</b> Where every one of them placed an end, where the
     * values stop and who put them there is what the reading of ends already answers, and this
     * change does not take that over: {@code value >= 5} beside {@code value > 4} is two rules at
     * one value and two rows to write, which {@link DeclaredBounds.End#tighter} has always said.
     *
     * <p>What it could not say is what an end owes to a conjunct it never saw. A rule that placed
     * no end can move where the values stop — a hole at an edge, an arithmetic no end was read from
     * — and it is invisible to a projection of ends, so where one of those is about the same number
     * the whole set is attributed here instead. Split by end-shape, the two kinds of conjunct are
     * attributed by two mechanisms with two principles and neither can see the other's candidates.
     *
     * <p>Not read as the two agreeing wherever they are both asked. They do not: a length is never
     * negative, so {@code String.length(value) >= 0} places an end at nought that the rules leave
     * there whether or not anybody wrote it, and taking the clause away moves nothing. The reading
     * of ends names it and a counterfactual names nobody — which is why the case where the ends
     * answer for themselves is left with them.
     */
    private boolean needsAttributing(List<AboutOneCoordinate> candidates) {
        Set<Line> ends = directs.stream()
                .map(each -> new Line(each.part()))
                .collect(java.util.stream.Collectors.toSet());
        return candidates.stream().anyMatch(each -> !ends.contains(each.line()));
    }

    /**
     * Which of {@code candidates} account for the end on one side, written down as ends they placed.
     *
     * <p>The three questions {@link EndNarrowing} asks, put to authored conjuncts. Only the first
     * of them was asked before — whether the end moves when this conjunct alone is taken away —
     * and a model writing one rule twice answered no to it twice: neither copy is missed on its
     * own, and the end came back owed to nobody.
     *
     * <p>Every candidate the answer names, whatever the reading of ends made of it. A conjunct that
     * placed an end of its own is named here at the end the rules actually leave, which is not
     * always the end it placed: {@code value >= 0} beside {@code value /= 0} put its own end at
     * nought and holds the one at one, and a row at nought is a row at a value the rules refuse.
     * The two ends meet in {@link DeclaredBounds.End#tighter}, which keeps the tighter and merges
     * the rules that drew it — so a candidate named here and placing an end there comes out as one
     * debt at one place.
     */
    private void accountedFor(NumberAt<RuleKey> at, List<AboutOneCoordinate> candidates,
                              Endpoint end, boolean lower, List<Placed> out) {
        if (end == null) {
            return;
        }
        for (AboutOneCoordinate each : EndNarrowing.read(end, candidates,
                removed -> sideWithout(removed, at, lower), inWrittenOrder()).names()) {
            out.add(new Placed(at, each.part(), lower, end));
        }
    }

    /** Where the coordinate stops on one side with these conjuncts taken away. */
    private Endpoint sideWithout(Set<AboutOneCoordinate> removed, NumberAt<RuleKey> at,
                                 boolean lower) {
        NumericDomain.Bounds without = withoutConjuncts(removed).leftAt(at.position(), at.of());
        return without == null ? null : lower ? without.min() : without.max();
    }

    /**
     * The order these are answered in, which is the order the author wrote them.
     *
     * <p>What identifies a clause and never what a report calls it. A clause is the declaration it
     * is written on and which of that declaration's clauses it is ({@link Clause.Id}); the name a
     * report prints holds neither the module nor the ordinal, so two modules each declaring a
     * {@code Span} give their clauses one key — and a comparator that answers nought leaves a
     * stable sort holding the order the walk collected them in, which is the one thing this order
     * exists to keep out of the answer.
     *
     * <p>The declaration, then which clause of it, then which conjunct of that. The last is what
     * tells two lines of one rule apart everywhere else
     * ({@link souther.compiler.partition.AuthoredLine}), and the first two are what tell two
     * clauses apart wherever they are written.
     */
    private static java.util.Comparator<AboutOneCoordinate> inWrittenOrder() {
        return java.util.Comparator
                .comparing((AboutOneCoordinate each) -> each.part().rule().clause().id()
                        .declaredOn())
                .thenComparingInt(each -> each.part().rule().clause().id().ordinal())
                .thenComparingInt(each -> each.part().ordinal());
    }

    /** The ends the rules place on the coordinates at {@code path}, in the order they were read. */
    public List<Placed> placedAt(RuleKey path) {
        return placed().stream().filter(each -> each.path().equals(path)).toList();
    }

    /**
     * The rules about where the coordinates at {@code path} stop that no end came out of, in the
     * order they were read.
     *
     * <p>Beside {@link #placedAt} and not instead of it. One name carries more than one
     * statement, so a rule here says nothing about whether an end was placed at the same name
     * and an end there says nothing about this — read as one answer, a bound on a field's own type
     * silenced the record's clause about the same field.
     */
    public List<NoLine> noLineAt(RuleKey path) {
        return noLines.stream().filter(each -> each.path().equals(path)).toList();
    }

    /**
     * The rules whose end at {@code path} a choice in them left open, in the order they were read.
     *
     * <p>Beside {@link #noLineAt} and answering something it cannot. That one is a rule the walk
     * over the written clause reached and got no end out of, and the walk stops at a choice — so a
     * comparison written under one is a rule it never had in hand. This is what the reading of ends
     * says became of the same clause once the branches were settled: an end it did not work out, at
     * a position a choice was shown to leave as wide as the branch nobody could read.
     *
     * <p><b>Only where a choice is answerable.</b> An end left open under a conjunction is one the
     * rule's own account already says was not reached, and a second sentence about it would be one
     * finding for one stop said twice. What a choice leaves open has no other sentence, which is
     * what this is for.
     */
    public List<EndLeftOpen> endsLeftOpenAt(RuleKey path) {
        List<EndLeftOpen> out = new ArrayList<>();
        endsLeftOpen.forEach((rule, open) -> open.byNumber().forEach((position, behind) -> {
            // Only the ends nothing else reaches. An end left open with no choice between it and
            // the walk that raises a rule's questions is one those questions already leave
            // standing, and a second account of it is one stop said twice.
            if (!path.equals(namedBy.get(position)) || !behind.underAChoice()) {
                return;
            }
            said(numberOf(path, position), rule, behind, out);
        }));
        // And the lines on the numbers this value's operations answer that nothing placed, which
        // is the other reading's answer arriving by the same road. Where the choice left one open
        // is that reading's ({@link BoundaryState}); which choice to send an author to is what the
        // account of the rule kept, and the two are met before either reaches here.
        boundsLeftOpen.forEach((rule, open) -> open.forEach((end, behind) -> {
            if (!path.equals(end.number().position()) || !behind.underAChoice()) {
                return;
            }
            said(end.number().asNumber(), rule, behind, out);
        }));
        return List.copyOf(out);
    }

    /** One end left open, said once per choice an author can be sent to and once where none can
     *  be named. */
    private static void said(NumberAt<RuleKey> at, RuleRef.Invariant rule,
                             EndsLeftOpen.Behind behind, List<EndLeftOpen> out) {
        if (behind.named().isEmpty()) {
            out.add(new EndLeftOpen(at, rule, null));
            return;
        }
        behind.named().forEach(each -> out.add(new EndLeftOpen(at, rule, each)));
    }

    /**
     * A rule whose end at one of a name's numbers this reading did not work out, and the choice an
     * author is sent to for it.
     *
     * <p>At the number and not at the name. A {@code String} has its own order and the length of
     * it, and an end left open at one of them says nothing about the other — filed at the name, a
     * report would say where the values stop was left undecided at whichever number it happened to
     * ask about.
     *
     * <p><b>One of these per choice, and one where no choice is answerable.</b> Two choices of one
     * rule leaving one end open are two things to lift, and lifting either leaves the end where it
     * was; the measure they leave short is the one line, which is what folds them again
     * ({@code ClosureGap.LineNotDerived}). Filed as one entry per end, the count an author acts on
     * would be a fact about which choice the walk met first.
     *
     * @param byChoice the choice to send an author to, or null where none is answerable — the end
     *                 was left open under a conjunction, or beside an alternative nobody can be in,
     *                 and there is no branch for an author to look at. Not a reason to say nothing:
     *                 the line at the position was still not derived, and that is the measure's
     *                 business rather than the author's
     */
    public record EndLeftOpen(NumberAt<RuleKey> at, RuleRef.Invariant rule,
                              ChoiceSite byChoice) {}

    /** Which of {@code path}'s numbers {@code position} is, as this reading named them. */
    private NumberAt<RuleKey> numberOf(RuleKey path, FactSubject position) {
        Counted counted = countAt.get(path);
        return counted != null && counted.atom().equals(position)
                ? NumberAt.takenOf(path, counted.by()) : NumberAt.valueOf(path);
    }

    /**
     * The same, wherever they are filed.
     *
     * <p>For a reader whose subject is the clause rather than a name. A rule relating two
     * coordinates is filed at each of them, so a reader after the rule meets it once per coordinate
     * and a reader after a name meets each of its rules once — and neither can be had by asking
     * the other and putting the answers back together, since which coordinates a rule was filed at
     * is not what the rule is.
     */
    public List<NoLine> noLines() {
        return List.copyOf(noLines);
    }

    /**
     * The conjuncts this reading got no end out of, in the order they were read.
     *
     * <p>What is handed on, and not what is reported. Which of these the next reading makes
     * something of is its answer and no part of this list: a rule naming a value is here because
     * nothing about it was settled, not because anything about it fell short.
     */
    public List<WithoutAnEnd> withoutAnEnd() {
        return withoutAnEnd;
    }

    /**
     * The conjuncts whose quantity is over one number, in the order they were read.
     *
     * <p>Every shape of rule alike, whatever the reading of ends made of it. Which of them accounts
     * for where the values stop is not here: it is a fact about everything the rules say together,
     * read by {@link #movedEnds} and not written down as each conjunct arrives.
     */
    public List<AboutOneCoordinate> aboutOneCoordinate() {
        return aboutOneCoordinate;
    }

    /**
     * The surviving ends {@code over} accounts for, which is what its conjunct was holding.
     *
     * <p>The answer {@link #movedEnds} came to, read back for one conjunct. What it rests on is
     * there: which of the conjuncts about a number account for where its values stop, asked as the
     * three questions {@link EndNarrowing} puts to a declaration.
     *
     * <p><b>Against the rules and not against the ends they placed.</b> A conjunct can place no end
     * and still hold one — a hole at an edge, an arithmetic no end was read from — so a
     * counterfactual over the ends would take nothing away from it and answer that nothing moved.
     * What is left out is the conjunct itself, and what is compared is what the whole reading
     * leaves.
     *
     * <p>Empty where this conjunct accounts for nothing: a hole with values either side of it is a
     * hole and no end of a range says where it is, and a clause restating what the carrier already
     * states leaves the values where they were.
     */
    public List<InvariantBound> movedEndsOf(AboutOneCoordinate over) {
        return movedEnds().stream()
                .filter(each -> each.part().equals(over.part()) && each.at().equals(over.at()))
                .map(each -> new InvariantBound(each.lower(), each.end()))
                .toList();
    }

    /**
     * This value read again without some conjuncts of its rules.
     *
     * <p>Which conjuncts, and not which candidates named them: two sets of candidates writing the
     * same conjuncts leave the same rules out, and are one reading.
     */
    private FieldDomains withoutConjuncts(Set<AboutOneCoordinate> removed) {
        return counterfactual(new LeftOut.Conjuncts(removed.stream()
                .map(AboutOneCoordinate::part)
                .collect(java.util.stream.Collectors.toUnmodifiableSet())));
    }

    /**
     * The declaration this reading was made under.
     *
     * <p>Which is not always the one a clause it holds was written on. A name wrapped round a record
     * is read as a governing declaration of its own, and the clauses of the record beneath it are
     * read under that name — so the bindings its reads carry are this declaration's, and a caller
     * matching them against the writing declaration's alone finds none of them.
     */
    public TypeSymbol.AtModule named() {
        return named;
    }

    /**
     * Whether the rules leave the value at {@code path} in {@code data} able to hold nothing.
     *
     * <p>Asked of the domain the rules seed rather than read off the clauses. A rule removes the
     * empty value in more ways than a floor written at the name: {@code List.length(kids.value)}
     * counts the same thing under another spelling, {@code >= least} beside {@code least >= 1} says
     * it through a second field, and an equality says it without stating an end a range would keep.
     * Reading the clauses for the shapes one reader thought of leaves the rest of them saying
     * nothing, and there is no end to the shapes. The seeding already relates all of them, so the
     * question goes there: settle the count at none and see whether anything is left.
     *
     * <p>Both the record's rules and the field's own type's reach the same domain — the seeding puts
     * each field's type in beside the clauses — so this is one reading and not two agreeing.
     *
     * <p>Yes where the seeding could not read the rules, and yes where what stands there is counted by
     * nothing. Wide is the safe direction: what this decides is that a recursion has nowhere to
     * bottom out, and a reader that guessed would refuse a type somebody can write.
     *
     */
    public static boolean mayHoldNothingAt(TypeSymbol.AtModule named, Hir.Data data, RuleKey path,
                                           RuleReadingSource source, ReadingPolicy policy) {
        // A count is never below none, so leaving it no room above none is leaving it at none.
        return OccurrenceCounts.of(named, source, policy).mayHoldAtMost(path, 0);
    }

    /**
     * How much the value at a name has to hold, which is not what the value there is.
     *
     * <p>Its own type because the numbers are the same numbers. {@code >= 2} at a field is a range of
     * that field's values where the field is a number, and a count of what it holds where a rule
     * counts it — and a caller handed a bare {@link NumericDomain.Bounds} has nothing to stop it
     * reading one as the other. There is one such mistake in this already: the atom a size rule
     * bounds is the size's, and writing it under the field is how a list would come to be told it
     * must be at least 2.
     */
    public record Held(NumericDomain.Bounds bounds) {

        public Held {
            if (bounds == null) {
                throw new IllegalArgumentException("a floor with no bounds is no floor");
            }
        }
    }

    /**
     * What the rules say the value at {@code path} holds, or {@code null} where they count it in no
     * way this read.
     *
     * <p>Read off the measure the type standing there names ({@link NumericMeasures#takenOf}), so a
     * field this can answer for is one whose values are counted by something. A field of a number has
     * no such measure and is answered by {@link #at} instead; the two never speak about one field.
     */
    public Held heldAt(RuleKey path) {
        NumericDomain.Bounds bounds = heldByName.get(path);
        return bounds == null ? null : new Held(bounds);
    }

    /**
     * Which number of {@code path} the rules take as a count of what it holds, or null where they
     * take none.
     *
     * <p><b>The owner of which number that is.</b> A caller holding a place and wanting the count
     * there has two things it could ask: what a reading of the input decided to measure the position
     * at, and what the rules of the value it sits in are about. Those are not the same — a rule can
     * relate the length of a list to a field beside it while the position itself is read by its own
     * value — and a caller asking the first for the second is told there is no count wherever the
     * clause that mentions it is written about something else as well.
     */
    public NumberAt<RuleKey> countedAt(RuleKey path) {
        Counted counted = countAt.get(path);
        return counted == null ? null : NumberAt.takenOf(path, counted.by());
    }

    /**
     * Which values may stand at {@code path}, and how much of its rules was read.
     *
     * <p>{@link ValueSet#ANY} where the rules leave it open, which is also what a name nothing
     * was written about comes to — told apart from a name this could not read by the
     * completeness beside it, which is why the two are handed over as one value
     * ({@link AdmissibleSet}).
     *
     * <p>{@code path} is read from the value these are of, as {@link #at} is, and what a name wraps
     * is at {@link RuleKey#THE_VALUE}. A range is not handed back there and this is: a newtype's
     * value is the value the newtype is, so it is what a reader of one asks about.
     *
     * <p>The name's own reason comes first where there is one. A rule written about this name that
     * could not be read is what an author would act on; that the gathering stopped somewhere else
     * in the value is true as well and is the coarser of the two.
     */
    public AdmissibleSet admits(RuleKey path) {
        ValueSet values = admittedByName.getOrDefault(path, ValueSet.ANY);
        // What the reading could not hold together, at this name and not at every one of them.
        // A choice reaching across two names leaves those two unable to show their projections
        // once something is met with it; a third the choice never named is answered by its own
        // clauses and keeps them. Beside whatever the name's own rules came to rather than
        // instead of it — a rule went unread or it did not, and that question is answered the same
        // whichever way this one is.
        Set<AdmissibleSet.Widening> spread = notSeparatedByName.contains(path)
                ? Set.of(new AdmissibleSet.Widening.AlternativesNotSeparated()) : Set.of();
        List<UnreadReason> here = unreadByName.getOrDefault(path, List.of());
        if (!here.isEmpty()) {
            // One widening per reason. A set of them is what {@link AdmissibleSet.Completeness} is
            // for — a reader looking for either finds it — and folding the several a name was
            // stopped by into one would choose among an author's rules here, where the only thing
            // to choose by is which was met first.
            return AdmissibleSet.wider(values, with(spread, here.stream()
                    .<AdmissibleSet.Widening>map(AdmissibleSet.Widening.RuleUnread::new).toList()));
        }
        // A clause that never reached the readings cannot have spoiled the name it was about,
        // because no reading here ever saw which name that was. A walk that fell over and a
        // clause nothing could type are both that, and both leave maps that read exactly like a
        // value with no rules — so what the gathering knows about itself is asked, rather than
        // guessed from the maps being empty.
        //
        // Not `projection`, which is what the bounds state of the rules. A clause the interval
        // algebra holds nothing of may be one this reading took in whole, so borrowing that answer
        // would settle this reading's completeness by a reading that is not this one.
        if (!everyRuleReachedAt(path)) {
            return AdmissibleSet.wider(values, with(spread,
                    List.of(new AdmissibleSet.Widening.RuleUnread(whyNothingReached(path)))));
        }
        return spread.isEmpty() ? AdmissibleSet.complete(values)
                : AdmissibleSet.wider(values, spread);
    }


    /** {@code these} and the rule's own reasons, in the order they are written here: a rule's own
     *  reason is what an author acts on, and what the reading could not hold together is beside
     *  them. */
    private static Set<AdmissibleSet.Widening> with(Set<AdmissibleSet.Widening> these,
                                                    List<AdmissibleSet.Widening> rules) {
        Set<AdmissibleSet.Widening> out = new LinkedHashSet<>(rules);
        out.addAll(these);
        return out;
    }

    /**
     * Whether the gathering reached whatever rules are written at {@code path}.
     *
     * <p>A stop reaches the name it happened at and every name under it, and no further. A rule
     * that narrows what stands at a name writes that name, and a clause written inside one field
     * writes no name outside that field — so a walk that declined to enter a regex-bounded code has
     * said nothing about the plain {@code Int} beside it.
     *
     * <p>A stop at {@link RuleKey#THE_VALUE} is different in kind and is why the names are compared
     * rather than counted: the declaration's own clause can write any name of it, so a clause of it
     * that never arrived leaves every name short of its rules.
     *
     * <p>Asked here rather than read off what a reading came back short of. A name can be both
     * — a rule that arrived and could not be read, beside a subtree the walk never entered — and
     * {@link #admits} answers with the first of the two because it has one slot to answer in, so
     * reach taken from there is lost wherever another reason won it. A caller that wants to know
     * whether anything is out of sight wants this.
     */
    public boolean everyRuleReachedAt(RuleKey path) {
        return reaches(notGathered.keySet(), path);
    }

    /**
     * Which of the two ways of never reaching a position's rules this one is.
     *
     * <p>Every stop that reaches the position is read and not the first of them. A stop past the
     * depth this reading could afford is one a run allowed to read further would go past; every
     * other stop is met again however much a run allows — so a position two stops reach is short
     * after the depth is raised, and saying otherwise would send a person to measure the same thing
     * twice. Which makes the depth answer the one that has to hold of all of them.
     *
     * <p>Asked only where {@link #everyRuleReachedAt} has already said something stopped, so the
     * set walked here is never empty and the answer is never the depth by vacuity.
     */
    private UnreadReason whyNothingReached(RuleKey path) {
        Set<RulesMissed> reaching = new LinkedHashSet<>();
        notGathered.forEach((stopped, why) -> {
            if (path.isAtOrUnder(stopped)) {
                reaching.addAll(why);
            }
        });
        return whyNothingReached(reaching);
    }

    /**
     * The same, of the stops themselves, which is where the answer is decided.
     *
     * <p>Taken apart from the walk over the paths so that what the coarsening is can be held to a
     * table. Which stops reach a position is arithmetic on paths; which reason a set of stops comes
     * to is the decision, and it is the one worth writing down.
     *
     * <p>Two switches and no {@code default} on either, rather than one figure picked out and
     * everything else falling past it. A way of going ungathered added later, or a fourth way for
     * the walk to stop, would otherwise be answered here as one no allowance changes — silently,
     * and by the arm nobody wrote.
     */
    static UnreadReason whyNothingReached(Set<RulesMissed> stops) {
        boolean depth = false;
        for (RulesMissed why : stops) {
            boolean afford = switch (why) {
                case RulesMissed.WalkStopped(GuaranteeWalk.Stop stop) -> switch (stop) {
                    case PAST_THE_DEPTH -> true;
                    case ASKED_TO_STOP, ALREADY_ENTERED -> false;
                };
                case RulesMissed.ClauseNotTyped _, RulesMissed.ClauseLost _,
                     RulesMissed.PositionNotOpened _, RulesMissed.ClauseNotAsked _,
                     RulesMissed.ClausesNotExpanded _,
                     RulesMissed.NoReadingWasMade _, RulesMissed.ReadingFellOver _ -> false;
            };
            if (!afford) {
                return UnreadReason.NOT_REACHED;
            }
            depth = true;
        }
        return depth ? UnreadReason.NOT_REACHED_PAST_DEPTH_LIMIT : UnreadReason.NOT_REACHED;
    }

    /**
     * The names this reading ended at with a declaration still to be read under them.
     *
     * <p>Not something wrong with the reading, and not an answer about the name either. What
     * stands at one of these is a container, an optional, or a choice between declarations, and what
     * is written under it is written about a value one step down — so the rules pass to whatever
     * reading is opened there, and whether one was is a fact about the walk rather than about this
     * reading of a declaration.
     *
     * <p>Handed over so that the walk can discharge them, one at a time and against the readings it
     * actually opened. Answered here instead, the only thing this could say is whether the type
     * graph has a rule somewhere below, which leaves the name above short of a rule no row could
     * ever supply.
     */
    public Set<RuleKey> handedOn() {
        return handedOn;
    }

    /** Whether {@code path} is out from under every stop in {@code stops}. A stop reaches the name
     *  it happened at and every name under it, and a stop at {@link RuleKey#THE_VALUE} is the
     *  declaration's own clause and reaches every name of it. */
    private static boolean reaches(Set<RuleKey> stops, RuleKey path) {
        for (RuleKey stopped : stops) {
            if (path.isAtOrUnder(stopped)) {
                return false;
            }
        }
        return true;
    }

    /**
     * What stands at {@code path} can hold, with the declarations holding each end.
     *
     * <p>{@code path} is read from the value these are of: {@code startsAt} for a field, and
     * {@code interval.startsAt} for a field of a field. A clause on the outer record names places
     * at any depth it can, so what it leaves them is read at the depth it left it at rather than at
     * the record each of them happens to sit in.
     *
     * <p>The ends and the names together, because which declaration holds an end is worked out
     * against that end and is true of no other ({@link NarrowedBounds}). Handed out apart, a caller
     * meeting these with another reading's kept both sets of names and one of the two ends.
     *
     */
    public NarrowedBounds at(RuleKey path) {
        NumericDomain.Bounds here = byName.get(path);
        // The ends now and the names when they are asked for. Answering who holds an end reads this
        // declaration again without the clauses of every declaration that wrote a relation about
        // the coordinate, and again per candidate where taking them away moved the end, and the
        // callers standing a fixture in a field's range ask a field at a time and read only the
        // ends.
        return here == null ? NarrowedBounds.NOTHING
                : NarrowedBounds.deferred(here,
                        () -> narrowedBy(path, true), () -> narrowedBy(path, false));
    }

    /**
     * That the reading names the rule the algebra could not prove, since only the reading can.
     *
     * <p>An assertion beside the cause that is filed when it does not hold, and not instead of it.
     * The two lists are walked by different code — the algebra's own rules, and the constraints each
     * reading handed over — and a drift between them shows up nowhere else, which is worth stopping
     * a build over where assertions are on. Where they are off there is still an answer, because
     * declining to promise an edge is sound whatever went wrong.
     */
    private static void assertSomethingWentUnstated(Set<ProjectionEvidence.Cause.Lossy> lossy) {
        assert !lossy.isEmpty()
                : "the algebra proved no rule of its own and this reading names none";
    }

    /**
     * A rule as a sort key, which is the author's word for it or what it is where they wrote none.
     *
     * <p>Spelled here because what it is for is here. Nobody is shown this: what a reader is sent to
     * a rule by is a citation, which carries a place for the rules that have no name, and an order
     * has no place to put one.
     */
    private static String orderOf(RuleRef rule) {
        return switch (rule) {
            case RuleRef.Named it -> it.citedName();
            case RuleRef.Written it -> "the " + it.whatItIs();
        };
    }

    /** What a cause is filed under, so that two runs print them the same way round. */
    private static String orderOf(ProjectionEvidence.Cause cause) {
        return switch (cause) {
            case ProjectionEvidence.Cause.Unavailable it -> "1 " + it.path();
            case ProjectionEvidence.Cause.Unrepresented it ->
                    "2 " + orderOf(it.rule()) + " " + it.path();
            case ProjectionEvidence.Cause.Lossy it ->
                    "3 " + orderOf(it.rule()) + " " + it.atom() + " " + it.unstated();
            case ProjectionEvidence.Cause.Rounded it -> "4 " + it.atom();
            case ProjectionEvidence.Cause.NothingIsLeft _ -> "5";
            case ProjectionEvidence.Cause.PositionsSpacedDifferently _ -> "6";
            case ProjectionEvidence.Cause.ARuleTheReadingCannotName _ -> "7";
            // Last, because it is the one cause about the pair of readings rather than about
            // anything either of them met.
            case ProjectionEvidence.Cause.TwoValuesStateRulesAboutIt _ -> "8";
        };
    }

    /**
     * What every rule reaching this value leaves what stands at {@code path}, the value itself
     * included.
     *
     * <p>A different question from {@link #at}, which is what the value a name sits in projects
     * onto it — a sibling's business, and a newtype's value has no siblings, which is why that one
     * has nothing to say about it. This is where the number stops once everything written about it
     * has been taken in, and a caller that has to know where a line actually falls wants this: a
     * clause placing an end at 0 beside one that takes the 0 away leaves a number whose first
     * value is 1, and the end as written is not where it starts.
     */
    public NumericDomain.Bounds leftAt(RuleKey path, NumberAt.OfWhatNumber kind) {
        // The axis the caller is on, and not whichever of the two this name happens to have. A
        // `String` is measured two ways — its own order, and the length of it — and answering with
        // the wrong one clamps a line drawn on one axis by the range of the other.
        FactSubject atom = subjectAt(path, kind);
        OrderedInterval settled = settledAt(path, kind);
        // A position ordered on something the interval algebra has no words for is at no atom of
        // its own, and the rules stop it all the same: what a choice of two bounds on a string
        // leaves is settled by the reading that composes the connectives, and where there is no
        // algebra to meet with, that answer is the whole of what is known.
        //
        // Of a position and not of what an operation answers. A count is a whole number whatever it
        // counts, so the algebra has words for every one of them and a count with no atom is one no
        // range was taken of here — which is what a caller reads a missing answer as, and is not
        // something this has anything to add to.
        if (atom == null) {
            return kind instanceof NumberAt.OfWhatNumber.OfItsOwnValue && settled != null
                    ? new NumericDomain.Bounds(settled.low(), settled.high()) : null;
        }
        NumericDomain.Bounds held = constraints.numbers().boundsOf(atom);
        // And what the choices leave it, which the algebra has no way to: it reads a clause as
        // written and never enters an alternative, so a number bounded in both branches of a
        // choice comes back from it unbounded. Met rather than preferred — the two are readings of
        // the same rules and each holds what the other cannot.
        //
        // Which of the two readings holds that answer is decided by the number and not here. A
        // position's own order is settled where the branches have their fate and a count's is
        // settled beside it, so each kind has one reader holding it: asked of one of them for both,
        // whichever kind that reader has no word for comes back from the algebra alone, which is
        // what left a bound written under a choice out of every line this compiler draws.
        return settled == null ? held
                : held.meet(new NumericDomain.Bounds(settled.low(), settled.high()));
    }

    /**
     * Where the reading that composed this value's connectives stops one of its numbers, or null
     * where no line may be drawn on it.
     *
     * <p>Null for a number no rule spoke of and for one the rules leave no value at, which are the
     * two neither reader writes down: the first runs as far as it ever did, and what has been read
     * in the second is that the rules contradict — which is said by whoever answers whether a value
     * exists, and a line off those ends would fall where the order does not reach.
     */
    private OrderedInterval settledAt(RuleKey path, NumberAt.OfWhatNumber kind) {
        return switch (kind) {
            case NumberAt.OfWhatNumber.OfItsOwnValue _ -> settledOrder.knownAt(path);
            case NumberAt.OfWhatNumber.OfWhatAnOperationAnswers _ -> {
                // Nothing to look for where no choice settled a number of this value, which is most
                // of them. Asked all the same, every lookup builds a number to find nothing under,
                // and this one is asked once per candidate per counterfactual.
                if (derived.byNumber().isEmpty()) {
                    yield null;
                }
                DerivedNumber number = DerivedNumber.of(new NumberAt<>(path, kind));
                yield number == null ? null : derived.knownAt(number);
            }
        };
    }

    /**
     * A count one path has: the atom the clauses name it by, and the operation it is a count of.
     *
     * <p>One value and not two maps. The atom is what a rule about the count is recorded against and
     * the operation is what a coordinate names it by, and they are true of the same count — held
     * apart, a path could have one and not the other, and the pairing would be whatever the two
     * fill sites remembered. That is the shape of defect this whole change is about (#1027), and it
     * arrived here as part of the repair for it.
     */
    public record Counted(FactSubject atom, ValueName by) {

        public Counted {
            java.util.Objects.requireNonNull(atom, "a count is recorded against an atom");
            java.util.Objects.requireNonNull(by, "and is a count of some operation");
        }
    }

    /** The atom of a count this reading may not have, which is what every lookup of one wants. */
    private static FactSubject atomOf(Counted counted) {
        return counted == null ? null : counted.atom();
    }

    /**
     * The subject the clauses of a value write one coordinate of it under, or null where they write
     * none.
     *
     * <p>Null for an operation the clause vocabulary has no word for, which is not a gap. What a
     * clause is written about is the value at a name or how much it holds; a guard bounding
     * {@code Time.hour(t)} names a number the declarations never mention, so what they say about it
     * is nothing — and nothing is what a lookup finding no subject already means everywhere here.
     */
    private FactSubject subjectAt(RuleKey path, NumberAt.OfWhatNumber kind) {
        return switch (kind) {
            case NumberAt.OfWhatNumber.OfItsOwnValue _ -> atomAt.get(path);
            case NumberAt.OfWhatNumber.OfWhatAnOperationAnswers taken ->
                    souther.compiler.check.NumericMeasures.isMeasure(taken.operation())
                            ? atomOf(countAt.get(path)) : null;
        };
    }

    /**
     * The tightest bounds the rules prove on an arithmetic form over several of these coordinates.
     *
     * <p><b>Where a product of {@link #leftAt} cannot go.</b> Two fields each running from none to
     * five come to ten taken one at a time, and a clause holding their sum at five is the whole
     * reason the pair was written down — so what the form runs between is asked of the relations
     * that reach it rather than composed from what each of them projects. Composed, a rule cutting
     * the sum at eight drew a border on a quantity that never arrives there.
     *
     * <p>Asked in the vocabulary a caller here already has. What the reading called a subject means
     * nothing once the reading that named it is gone, so what crosses is a name and a form of names
     * — the same translation {@link #leftAt} makes, of a question with several coordinates in it.
     *
     * <p>Null where a coordinate of the form is one no range is taken of here, which is an answer
     * about this reading rather than about the form: the atom that would carry it does not exist, so
     * there is no relation to project and the caller is left with whatever it knows beside this.
     */
    public NumericDomain.Bounds boundsOf(Map<NumberAt<RuleKey>, java.math.BigDecimal> form) {
        return boundsOfForm(constraints, atomAt, countAt, form);
    }

    private static NumericDomain.Bounds boundsOfForm(ConstraintState<FactSubject> constraints,
                                                     Map<RuleKey, FactSubject> atomAt,
                                                     Map<RuleKey, Counted> countAt,
                                                     Map<NumberAt<RuleKey>, java.math.BigDecimal> form) {
        if (form.isEmpty()) {
            return null;
        }
        Map<FactSubject, java.math.BigDecimal> coefs = new LinkedHashMap<>();
        for (Map.Entry<NumberAt<RuleKey>, java.math.BigDecimal> each : form.entrySet()) {
            NumberAt<RuleKey> at = each.getKey();
            FactSubject atom = switch (at.of()) {
                case NumberAt.OfWhatNumber.OfItsOwnValue _ -> atomAt.get(at.position());
                case NumberAt.OfWhatNumber.OfWhatAnOperationAnswers taken ->
                        NumericMeasures.isMeasure(taken.operation())
                                ? atomOf(countAt.get(at.position())) : null;
            };
            if (atom == null) {
                return null;
            }
            // Two coordinates of one form can be one atom — a form is written over the names a
            // rule writes, and a rule may write one of them twice.
            coefs.merge(atom, each.getValue(), java.math.BigDecimal::add);
        }
        return constraints.numbers().boundsOf(
                new LinearForm<>(java.math.BigDecimal.ZERO, coefs));
    }

    /**
     * How much of what the rules say these bounds are able to state.
     *
     * <p>Asked of the value and not of one name in it, because what it licenses is existential:
     * a row at an edge is a whole value with that edge in it, and a rule about some other name
     * can refuse to be part of any such value. Two labels on one record that cannot both be written
     * leave every number beside them with edges nothing can reach, however plainly the numbers
     * themselves were read.
     *
     * <p>Read off the reading that made the bounds and not off a second walk of the declarations.
     * A walk classifying each rule by its shape asks what could have become a bound, which is an
     * answer about this compiler's interval algebra wearing the model's words — and it went on
     * saying a rule was unread where the algebra had taken it in whole.
     *
     * <p>Where this is not exact the bounds still hold: every value they exclude is truly excluded,
     * and a value they admit may be one nothing can build. What settles such an edge is a witness.
     */
    public ProjectionEvidence projection() {
        List<ProjectionEvidence.Cause> causes = new ArrayList<>();
        // A rule that never arrived first. Which rule it was is not known here and there is nothing
        // to say: what a stop leaves is a name and every name under it.
        // Spelled, since what a cause carries is read by an author rather than looked up.
        for (RuleKey stopped : unreadOfEveryValue) {
            causes.add(new ProjectionEvidence.Cause.Unavailable(stopped.toString()));
        }
        // Every name of this value, by the atom a range of it is taken under. A rule that
        // narrowed one of these is in the bounds; a rule that narrowed only an atom standing for an
        // arithmetic this cannot carry narrowed nothing anybody reads off them.
        Set<FactSubject> ranged = new LinkedHashSet<>(atomAt.values());
        countAt.values().forEach(counted -> ranged.add(counted.atom()));
        // Asked of every rule the reading was handed, and not of the questions the rules raise. A
        // pattern raises none — which values may stand somewhere and where a line falls are not what
        // it is about — and it is still a way the value can be refused at an edge of the number
        // beside it.
        // What each rule leaves unrepresented, met from every reading of it and held by the rule.
        // A clause is read once per place the walk opens a value at, so a set held by the reading
        // would say a rule left a position without a representation once per reading that met it —
        // and what a rule left a position without is one fact about the rule however many readings
        // there were.
        Map<RuleRef.Invariant, Set<RuleKey>> unrepresented = new LinkedHashMap<>();
        readings.forEach(reading -> {
            RuleRef.Invariant rule = reading.from();
            // Where in the clause this reading recorded a shape, which is what a conjunction below
            // asks about its two halves.
            Set<ClauseExpr.Occurrence> recorded = new LinkedHashSet<>();
            reading.constrained().values().forEach(byOccurrence ->
                    byOccurrence.values().forEach(one -> recorded.add(one.of().at())));
            // A part at a time, and any one of them is enough. A conjunct the bounds hold nothing of
            // leaves the range wider than the rule however well the conjunct written beside it went,
            // and a set unioned over the whole clause answers for the failing half with the other
            // one — which is the same shape as reading a clause's evidence for one of its parts.
            Set<RuleKey> said = unrepresented.computeIfAbsent(rule, _ -> new LinkedHashSet<>());
            reading.constrained().values().forEach(byOccurrence ->
                    byOccurrence.values().forEach(one -> {
                ClauseExpr shape = one.of();
                InvariantChecker.PartRead read = one.said();
                // A conjunction says what its conjuncts say, and they are here beside it. Asked of
                // the conjunction as well, a rule whose halves are each held in a language of their
                // own — a date bounded at both ends, read by the comparison rather than by the
                // interval algebra — answers for neither half and fails on the node above them.
                //
                // Which shapes are conjunctions is read off the shape and never off the operator
                // it was written with: what a connective composes is settled where a clause is read
                // out of the tree ({@link ClauseExpr}), and a denial written above one turns the
                // conjunction its author wrote into a choice between the denials of its halves.
                if (shape instanceof ClauseExpr.Joined it && it.how() == ConditionJoin.BOTH
                        && it.positive()
                        && recorded.contains(it.left().at())
                        && recorded.contains(it.right().at())) {
                    return;
                }
                if (read.narrowable().stream().anyMatch(ranged::contains)) {
                    return;
                }
                // Or the end this part placed, which is the same part in the bounds said by the
                // other reading of it: an ordered value that is not a number takes its range from
                // the comparison rather than from the interval algebra, and counting only the
                // algebra calls a bounded `Date` a rule the bounds do not hold — and takes every
                // boundary beside it down with it.
                // The rule as well as the place: a place is a coordinate of one clause and every
                // clause has a first one, so an end another rule placed at its own opening would
                // answer here for a conjunct that placed nothing. The place first, because it is
                // a number and a rule is a declaration, a clause of it and a number of that.
                ClauseExpr.Occurrence stands = shape.at();
                if (directs.stream().anyMatch(d -> d.stands().equals(stands)
                        && d.part().rule().equals(rule))) {
                    return;
                }
                // What this part is about, and not what the rule is. A conjunction is one rule the
                // author wrote and what it raises is what its conjuncts raise together, so a reader
                // reaching for the rule's questions here would name the places of the conjunct
                // written beside this one — the half the bounds do hold — among the ones they do
                // not.
                Map<ClauseExpr.Occurrence, Required> byPartRaised = raisedByPart.get(rule);
                Required required = byPartRaised == null ? null : byPartRaised.get(shape.at());
                if (required != null) {
                    required.obligations().forEach(owed -> {
                        switch (owed) {
                            case Owed.AdmittedValues it -> said.add(it.path());
                            case Owed.Boundary it -> said.add(it.on().position());
                        }
                    });
                }
                // A part that raised no question is about the value it is written on, which is the
                // name of no steps.
                if (required == null || required.obligations().isEmpty()) {
                    said.add(RuleKey.THE_VALUE);
                }
            }));
        });
        // And said once per rule, after every reading of it has been met.
        unrepresented.forEach((rule, said) -> said.forEach(path ->
                causes.add(new ProjectionEvidence.Cause.Unrepresented(rule, path.toString()))));
        // And what the algebra was given and what it projects does not hold.
        //
        // Asked of what was derived and of nothing else. Asking the whole state whether it holds a
        // rule is asking the rule to stand on itself — every rule that went unstated comes back
        // proven — so what is asked is whether the box and the relations its closure holds between
        // its subjects state it, which is less than the rules and more than the ranges by
        // themselves.
        //
        // And asked of the rules after everything has been worked out, rather than read back from
        // marks left as each rule arrived. A mark left at that moment is a history: a rule that
        // narrowed nothing when it was read left one behind after a later rule made it bite, so a
        // range that had become the whole of what the rules say still carried a note that it was
        // not. What could not be stated is a property of the rule and of what the rules were found
        // to leave, and it is worked out from those.
        Set<ProjectionEvidence.Cause.Lossy> lossy = new LinkedHashSet<>();
        readings.forEach(reading -> reading.constrained().values().forEach(byOccurrence ->
                byOccurrence.values().forEach(one -> {
            RuleRef.Invariant rule = reading.from();
            for (NumericConstraint each : one.said().stated()) {
                if (constraints.numbers()
                        .provenByTheBoxAndItsDifferences(each.form(), each.rel())) {
                    continue;
                }
                for (FactSubject atom : each.atoms()) {
                    lossy.add(new ProjectionEvidence.Cause.Lossy(rule, atom,
                            Set.of(each.rel() == Rel.NE
                                    ? ProjectionEvidence.Cause.Unstated.A_HOLE
                                    : ProjectionEvidence.Cause.Unstated.A_RELATION)));
                }
            }
        })));
        causes.addAll(lossy);
        // And whether the ends handed over are the ends the rules drew. Asked of every subject the
        // algebra speaks of, because this is not about any one rule: the reasoning reached the edge
        // exactly and the writing could not carry it.
        constraints.numbers().atomsSpokenOf().stream()
                .filter(atom -> !constraints.numbers().endsAreWrittenExactly(atom))
                .forEach(atom -> causes.add(new ProjectionEvidence.Cause.Rounded(atom)));
        // And what the algebra made of the ranges it derived. Asked of it rather than worked out
        // here, refusals included: the derivation is there and so is the theorem an answer rests on,
        // and a reason recovered on this side of the boundary from what was left over names the
        // wrong one wherever two things are in the way at once.
        souther.compiler.numeric.ProjectionCertification certification =
                constraints.numbers().projectionCertification();
        switch (certification) {
            case souther.compiler.numeric.ProjectionCertification.Certified _ -> { }
            case souther.compiler.numeric.ProjectionCertification.NothingIsLeft _ ->
                    causes.add(new ProjectionEvidence.Cause.NothingIsLeft());
            case souther.compiler.numeric.ProjectionCertification.PositionsSpacedDifferently _ ->
                    causes.add(new ProjectionEvidence.Cause.PositionsSpacedDifferently());
            // Which rule it was is this side's to say, and it is already said: the algebra holds the
            // rules as it read them, and the name an author would recognise is on the reading that
            // handed them over. Asserted rather than defended against, because the two walk
            // different lists — the algebra's own rules, and the constraints each reading handed
            // over — and a list that has drifted shows up nowhere else.
            case souther.compiler.numeric.ProjectionCertification.NotEveryRuleIsProven _ -> {
                assertSomethingWentUnstated(lossy);
                if (lossy.isEmpty()) {
                    causes.add(new ProjectionEvidence.Cause.ARuleTheReadingCannotName());
                }
            }
        }
        // In an order that does not move between runs. Parts are keyed by the node the tree holds,
        // which is an identity, and a map keyed on one iterates by where the addresses landed — so
        // a value with two conjuncts short of the bounds printed its two causes in whichever order
        // this run happened to give them. Sorted rather than kept in insertion order, because the
        // causes come from several producers and there is no one order they arrive in.
        causes.sort(java.util.Comparator.comparing(FieldDomains::orderOf));
        if (certification instanceof souther.compiler.numeric.ProjectionCertification.Certified(
                var by) && causes.isEmpty()) {
            return new ProjectionEvidence.CertifiedExact(by);
        }
        return new ProjectionEvidence.NotCertified(causes);
    }

}
