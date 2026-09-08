package souther.compiler.check;

import souther.compiler.semantics.NumericResult;
import souther.compiler.core.Contract;
import souther.compiler.core.Contract.Guard;
import souther.compiler.core.Core;
import souther.compiler.types.BindingId;
import souther.compiler.types.CaseSelector;
import souther.compiler.types.ResolvedCase;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.ValueName;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * What holds where a walk stands, and the three acts that move it.
 *
 * <p>The rules and not the check. Introducing a binding, taking a condition as holding, and reading
 * what a type guarantees of a value are answers about a program, and the question they add up to —
 * can anything at all stand here — is asked by more than the check they were written for. Held
 * apart so that there is one reading of it: a second walk that threaded its own narrowing would be
 * a second set of rules, and the two would part company at the first clause a domain other than the
 * numbers has a word for ({@code value == "A"} beside {@code value /= "A"} reaches no number and
 * leaves the predicates contradictory).
 *
 * <p>Nothing here reports anything. What is done with an answer — a clause discharged, an
 * obligation left standing, an arm named as one nothing reaches — is the caller's, and each of them
 * wants a different one. An engine that also decided would settle those before either caller was
 * written.
 *
 * <p>Threaded functionally. Every act answers a new {@link Entered} and leaves what it was given as
 * it was, so a branch read under one condition says nothing about the branch beside it.
 */
final class PathEngine {

    /**
     * Where a test in this package reads the answers this took a guarantee from, one entry per
     * seeding, and null everywhere else.
     *
     * <p>Beside {@link InvariantChecker#WATCHING} and for a reason of its own. Seeding one answer
     * twice lands on the subjects it landed on the first time, so a reading that seeds once per node
     * and one that seeds once per region say the same thing about every program — what separates
     * them is what they cost, and no diagnostic is about that. So the property has nowhere else to be
     * read, and one that nothing reads stops being true without anything failing.
     */
    static List<Core> SEEDED;


    private final Symbols symbols;
    /** The declarations' invariants, typed where they are declared and read where a value is built. */
    private final Clauses clauses;
    /** Where a value is, what it is called, and what can be said of it. */
    private final Terms terms;
    /** What a clause owes and what a guard settles. */
    private final Predicates predicates;

    /** The one reading of what a declaration guarantees, which this reading owns and this walk is
     * one consumer of. */
    private final TypeGuarantees guarantees;

    /** Getting to the positions that reading is asked about, which is nobody's semantics. */
    private final GuaranteeWalk walk;
    /** What each behavior a body may call states about its answer, by the name it is called under. */
    private final Map<ValueName.Behavior, AssumedContract> contracts;

    PathEngine(Symbols symbols, ExpandedClauseLookup dischargeInvariants,
               DeclarationReadings machines, ReadingPolicy policy) {
        this(symbols, dischargeInvariants, machines, Map.of(), Terms.Of.THE_DISCHARGE_TREE,
                policy);
    }

    PathEngine(Symbols symbols, ExpandedClauseLookup dischargeInvariants,
               DeclarationReadings machines, Map<ValueName.Behavior, AssumedContract> contracts,
               ReadingPolicy policy) {
        this(symbols, dischargeInvariants, machines, contracts, Terms.Of.THE_DISCHARGE_TREE,
                policy);
    }

    /**
     * The same rules, told which tree they are being read over.
     *
     * <p>The rules do not change with the tree; what changes is what a shape they cannot name means.
     * Over the tree that runs, {@code List.map} is the fold it lowers to, and a reading that
     * recorded the fold as a shape this compiler has no term for would be answering about the
     * representation under the name of a gap.
     */
    PathEngine(Symbols symbols, ExpandedClauseLookup dischargeInvariants,
               DeclarationReadings machines, Terms.Of reading, ReadingPolicy policy) {
        this(symbols, dischargeInvariants, machines, Map.of(), reading, policy);
    }

    PathEngine(Symbols symbols, ExpandedClauseLookup dischargeInvariants,
               DeclarationReadings machines, Map<ValueName.Behavior, AssumedContract> contracts,
               Terms.Of reading, ReadingPolicy policy) {
        this.symbols = symbols;
        this.clauses = new Clauses(symbols, dischargeInvariants, machines);
        this.terms = new Terms(symbols, reading, policy, clauses);
        this.predicates = terms.predicates();
        this.guarantees = terms.guarantees();
        this.walk = terms.walk();
        this.contracts = Map.copyOf(contracts);
    }

    Symbols symbols() {
        return symbols;
    }

    /** The one reading of what a declaration guarantees, for a reader that wants the answer without
     * the walk. */
    TypeGuarantees guarantees() {
        return guarantees;
    }

    Clauses clauses() {
        return clauses;
    }

    Terms terms() {
        return terms;
    }

    Predicates predicates() {
        return predicates;
    }

    /**
     * A place and what is known of it, answered together.
     *
     * <p>Both halves or neither: a value's place and what is known of it are separate questions with
     * separate readers, and introducing a location answers both at once. Returning only one of them
     * is what let a binding be named without being seeded.
     */
    record Entered(Known known, Denotations at) {

        static Entered nothing() {
            return new Entered(Known.top(), Denotations.none());
        }
    }

    // --- the three acts ------------------------------------------------------------------------

    /**
     * Introduces {@code root} as a location: somewhere nothing else names, holding a value of its
     * type. Entering it and seeding it are one act, so there is no state where a reading names a
     * place it knows nothing about — which is a clause owed with nothing to establish it, and a
     * warning an author cannot clear.
     *
     * <p>Every value a walk reaches this way was built through its type's checked constructor, so
     * what that type guarantees holds of it. That is the same argument for a behavior's parameter,
     * for what a {@code match} arm binds, and for what a combinator hands its closure — one rule,
     * asked here.
     */
    Entered enter(Core.Read root, Known known, Denotations at) {
        Denotations next = at.location(root.binding(), terms.placeSubject(root.binding()),
                terms.placeTerm(root.binding()));
        return new Entered(seedAt(root, known, next), next);
    }

    /**
     * The environment a binding's body is read in. Every place a body is read reaches it through
     * here: a walk on its way into one, and a conditional hoisted out of one. The bug this answers
     * came from those working the scope rule out separately, so there is one of it.
     *
     * <p>The initializer is not read here. A walk reads it before it gets here, and a hoisted
     * conditional was found past it.
     *
     * <p>The name is an alias for what its initializer denotes, so what is recorded about it is
     * recorded under that denotation and not under the binding. Recording it under the binding is
     * what made a named subexpression a term of its own, answering differently from the very
     * expression it was given.
     *
     * <p>Nothing is recorded of the name itself. A name is read as the expression it was given —
     * {@link Terms#affineOf} reads through it, and reads through a field taken off it the same way —
     * so there is no second reading for a fact about the name to be needed by. Recording one meant
     * giving the name's own atom the bounds of the form it was given, which is what a guard read one
     * way and a construction read the other had between them, and a bound is not a relation: it left
     * the guard settling nothing about the construction (#676).
     *
     * <p>What the binder means is {@link Terms#inside}'s answer and not this walk's. A reader that
     * decides for itself what a binder denotes is a second account of it, and the second account is
     * weaker than the first wherever it was written for a narrower purpose — which is what a
     * reduction's step read by (#867). Nothing else here is owed: the knowledge is what stood before
     * the binding, because the initializer was read before this.
     */
    Entered bindLet(Core.LetIn li, Known k, Denotations at) {
        return new Entered(k, terms.inside(li, at));
    }

    /**
     * {@code k} with {@code cond} taken as holding, or as failing where {@code positive} is false —
     * and whether any of it was taken in at all.
     *
     * <p>Sound where the condition is of a shape no rule here reads: what cannot be taken in is
     * left out, and a reading that took nothing in is one that ruled nothing out. Which of those
     * happened is the second half of the answer, because the state alone cannot say: a condition
     * nothing could read and one read to no effect leave it identical.
     */
    Predicates.Assumed assuming(Core cond, Known k, Denotations at, boolean positive) {
        return predicates.assumeCond(cond, k, at, positive);
    }

    /**
     * What a {@code match} arm's body is read under.
     *
     * <p>A sum has no fields of its own, so the scrutinee is not a location any clause could have
     * named — the case's value names only itself. What the arm binds is a place, which is what lets
     * a clause be read against it and what makes it carry what its type guarantees. Which value it
     * is is a second answer and comes from what was opened ({@link #opening}).
     *
     * <p>An arm that binds nothing introduces nothing, and its body is read as the arm's own. Held
     * here rather than at each walk: two readings deciding it apart is two chances to forget, and
     * the one that forgot fell over on every ordinary unit case.
     */
    Entered enteringArm(Core.Case arm, Core scrutinee, Known k, Denotations at) {
        Entered in = arm.binder() == null || arm.bindType() == null
                ? new Entered(k, at)
                : opening(arm, scrutinee, k, at);
        return whatTakingThisCaseSays(arm, scrutinee,
                assuming(answeredBy(scrutinee, in.at()), answered(arm, scrutinee),
                        guard -> impliedBy(guard, arm.pattern()), in));
    }

    /**
     * {@code in} with what taking this arm's case says about what the operation was given.
     *
     * <p>An operation answering its number as one case of a union comes back as the other case under
     * a condition it declares ({@link NumericResult.TheOtherCaseWhen}), and which arm was taken
     * settles that condition both ways: the arm carrying the number was taken because it does not
     * hold, and the other arm because it does. So a {@code DivisionByZero} arm has established that
     * the divisor is zero, which is a fact about a value the caller handed over and not about the
     * case.
     *
     * <p>About which case came back and not about whether a number was answered. An operation may
     * answer nothing at all — {@code Int.divide} aborts on the one pair whose quotient no {@code
     * Int} holds (spec §stdlib-int) — and an abort comes back as no case, so no arm is reached and
     * there is nothing here for it to say.
     *
     * <p>Taken in through the door a written condition goes through, which is what keeps this one
     * statement rather than two: what a comparison establishes is {@link Predicates}' to say, and a
     * reader that asserted the relation itself would be a second account of what {@code == 0} means.
     *
     * <p>An arm naming several cases says neither thing where the number's case is among them: it
     * may have been taken for that one or for another, and a rule about a value this arm may not
     * have is a rule about nothing.
     */
    private Entered whatTakingThisCaseSays(Core.Case arm, Core scrutinee, Entered in) {
        Core called = terms.originating(scrutinee, in.at(), new HashSet<>());
        Core condition = TheOtherCase.conditionAt(called);
        Type answersIn = TheOtherCase.theCaseItAnswersIn(called);
        if (condition == null || answersIn == null) {
            return in;
        }
        Boolean answered = whetherItAnswered(arm, answersIn);
        if (answered == null) {
            return in;
        }
        return new Entered(assuming(condition, in.known(), in.at(), !answered).known(), in.at());
    }

    /** Whether {@code arm} was taken because the union came back carrying the number, or because it
     * came back as the other case — and null where the arm says neither. */
    private static Boolean whetherItAnswered(Core.Case arm, Type answersIn) {
        boolean itsCase = false;
        boolean another = false;
        for (CaseSelector selector : arm.pattern().selectors()) {
            if (answersIn.equals(selector.refinement().bound())) {
                itsCase = true;
            } else {
                another = true;
            }
        }
        return itsCase == another ? null : itsCase;
    }


    /**
     * The arm's binding entered as the value it opens, and seeded.
     *
     * <p>What the binding denotes is {@link Terms#choosing}'s answer and not this walk's, for the
     * reason {@link #bindLet} gives about a {@code let}: a reader that decides for itself what a
     * binder denotes is a second account of it, and the second account is weaker than the first
     * wherever it was written for a narrower purpose. This walk was that reader until #973, and the
     * naming of an expression could not reach what it decided.
     *
     * <p>What is this walk's is the seeding. Every value reached this way was built through its
     * type's checked constructor, so what that type guarantees holds of it — the same argument
     * {@link #enter} rests on, and one that needs a {@link Known} to be written into.
     */
    private Entered opening(Core.Case arm, Core scrutinee, Known k, Denotations at) {
        Core.Read root = Terms.read(arm.binder(), arm.bindType(), arm.pos());
        Denotations next = terms.choosing(new Choice.Decides.ACase(arm, scrutinee), at);
        return new Entered(seedAt(root, k, next), next);
    }


    // --- what a call's answer was declared to be ------------------------------------------------

    /**
     * What the behavior that produced {@code value} states about its answer, or null where nothing
     * did.
     *
     * <p>The call is followed through the names it was given. {@code let r = findTodo(id)} and
     * {@code match findTodo(id)} are the same program to an author, and asking whether the scrutinee
     * <em>is</em> a call answers only the second — the binding it went through is not a step away
     * from the call, it is a name for it. Derived from what the walk already recorded rather than
     * remembered beside it: {@link Denotations} holds what each binding was given, and holding an
     * origin as well would be two records of one fact to keep agreeing.
     */
    private Answered answeredBy(Core value, Denotations at) {
        Core.Call call = value == null ? null : originatingCall(value, at, new HashSet<>());
        if (call == null || !(call.fn() instanceof Core.Reached reached)
                || !(reached.denotes() instanceof ValueName.Behavior behavior)) {
            return null;
        }
        AssumedContract assumed = contracts.get(behavior);
        return assumed == null ? null : new Answered(assumed, call);
    }

    /** An answer and what was declared about it: the rules, and the call they are read at — a rule
     * names the behavior's own parameters, and what those are here is what this call handed over. */
    private record Answered(AssumedContract stated, Core.Call call) {}

    /** The call {@code value} came from, through however many names it was given, or null where it
     * came from something else. {@code seen} stops a binding given itself. */
    private Core.Call originatingCall(Core value, Denotations at, Set<BindingId> seen) {
        if (value instanceof Core.Call call) {
            return call;
        }
        if (value instanceof Core.Read read && seen.add(read.binding())) {
            Core given = at.valueOf(read.binding());
            return given == null ? null : originatingCall(given, at, seen);
        }
        return null;
    }

    /**
     * Whether an arm's pattern says the answer is what {@code guard} is about.
     *
     * <p>Read off the pattern, which is the proof the checker already has: an arm is taken because
     * the value is one of the cases it names. So the question is whether every value that could
     * have taken this arm is one the rule is about — {@code armCoverage} inside {@code ruleCoverage}
     * — and not whether the two were written under one name. An arm naming a case of a case answers
     * for less than a rule naming the case above it, and the rule holds of it; the other way round
     * it does not, and reading the inclusion the wrong way would carry a rule into an arm that has
     * values it says nothing about.
     *
     * <p>An arm naming several answers for their union, so a rule holds of it exactly where it
     * holds of every one of them. Nothing special is done about that: the union is what the arm
     * covers.
     *
     * <p>What the arm covers is taken off the arm. {@code Core} carries each case as this compile
     * resolved it, so the atoms are already there; resolved again here they would be the same
     * answer worked out twice, and the day the two differed one side of an inclusion would be
     * reading a case the other side never saw.
     *
     * <p>A rule under no case applies to every answer, so any arm reaching it is an arm it holds of.
     *
     * <p>Visible to this package so the inclusion can be held in both directions. Reversed, it
     * assumes a rule about one case throughout an arm that has values the rule says nothing about,
     * which is unsound and answers no differently on any program where the two coincide — so what
     * pins it has to ask the question directly.
     */
    boolean impliedBy(Guard guard, Core.ResolvedPattern pattern) {
        if (guard instanceof Guard.Always) {
            return true;
        }
        if (!(guard instanceof Guard.Case(ResolvedCase selected))) {
            return false;
        }
        Set<TypeSymbol> ruleCovers = new LinkedHashSet<>(selected.atoms());
        for (ResolvedCase armCase : pattern.cases()) {
            if (!ruleCovers.containsAll(armCase.atoms())) {
                return false;
            }
        }
        return true;
    }

    /** What the arm holds of the answer: what it binds where it binds one, and the answer itself
     * where it does not — an arm may state a relation about a case that carries nothing. */
    private static Core answered(Core.Case arm, Core scrutinee) {
        return arm.binder() == null || arm.bindType() == null ? scrutinee
                : Terms.read(arm.binder(), arm.bindType(), arm.pos());
    }

    /**
     * {@code in} with every rule the arm's pattern implies taken as holding of the answer.
     *
     * <p>Conjunct by conjunct, as everything else about a clause is. A rule one half of which names
     * something this cannot read still says the other half: dropping the rule for the half that got
     * away would leave a caller with less than the declaration gives them, and taking the half that
     * was read is what the seeding does everywhere else.
     */
    private Entered assuming(Answered answered, Core answer, Predicate<Guard> reached, Entered in) {
        if (answered == null || answer == null) {
            return in;
        }
        Known out = in.known();
        for (AssumedContract.AssumedRule rule : answered.stated().rules()) {
            if (!reached.test(rule.guard())) {
                continue;
            }
            Map<BindingId, Core> given = handedOver(answered, rule, answer);
            if (given == null) {
                continue;
            }
            for (AssumedContract.Conjunct conjunct : rule.conjuncts()) {
                if (conjunct.means().isEmpty()) {
                    continue;
                }
                TermMeaning here = conjunct.means().get().substituted(given);
                answered.stated().takenIn();
                out = predicates.assume(here.assumedBy(predicates, in.at(), false), out,
                        Known.Held.OF_THE_VALUE);
            }
        }
        return new Entered(out, in.at());
    }

    /**
     * What each name a rule reads stands for here: the parameters as this call handed them over, and
     * {@code value} as what the arm holds.
     *
     * <p>A rule is written in the declaration's names and read at the caller's values, so the two are
     * put together before anything is read of it — which is the same substitution a declaration's
     * clause gets where a value is built ({@link Clauses#statedAt}).
     *
     * <p>Null where the call and the declaration disagree about how many values were handed over.
     * That is a program this compiler is refusing elsewhere, and a rule read against the wrong
     * argument would be a relation nobody declared.
     */
    private static Map<BindingId, Core> handedOver(Answered answered,
                                                   AssumedContract.AssumedRule rule, Core answer) {
        List<Core> args = answered.call().args();
        if (args.size() != answered.stated().params().size()) {
            return null;
        }
        Map<BindingId, Core> given = new HashMap<>();
        List<Contract.Param> params = answered.stated().params();
        for (int i = 0; i < params.size(); i++) {
            // Which parameter this is, is where it stands: a signature takes them in that order and
            // a call hands its arguments over in it.
            given.put(params.get(i).binding(), args.get(i));
        }
        given.put(rule.value(), answer);
        return given;
    }

    /**
     * What an attempted construction's success branch is read under.
     *
     * <p>Reaching it is the construction having held, so the binding carries the type's invariant
     * exactly as an input of that type does — which is a location, and not the construction read
     * again. What the construction denotes is what a reading could say of the attempt, and an
     * attempt is written where it could not say enough: an expression it cannot name denotes
     * nothing, and inheriting that would drop the one thing reaching this branch established.
     *
     * <p>Its departures stand where the invariant did not hold and nothing was built, so none of
     * them is entered with anything the attempt would have guaranteed. That is the caller's to
     * honour by not asking.
     */
    Entered enteringBuilt(Core.IfConstructed ic, Known k, Denotations at) {
        Core.Read root = Terms.read(ic.binder(), ic.construct().type(), ic.pos());
        Denotations next = terms.choosing(new Choice.Decides.ItWasBuilt(ic), at);
        return new Entered(seedAt(root, k, next), next);
    }

    // --- seeding -------------------------------------------------------------------------------

    /**
     * {@code k} with what {@code e}, having answered, guarantees taken as holding of it: what its
     * type states of any value of that type, and what its behavior declared of every answer it
     * gives.
     *
     * <p>An answer is a value of its type, built through that type's checked constructor — the same
     * argument {@link #enter} rests on for a parameter, for what a {@code match} arm binds, and for
     * what a combinator hands its closure. It was not asked of an answer only because an answer had
     * nothing to be asked about: no subject, so nothing to write the guarantee under. It has one now.
     *
     * <p>What makes an answer one of these is not the shape of the node. It is that whoever produced
     * it had already established what its type states before handing it over — a boundary this check
     * read on its own, or one another compile read and published. A call to a behavior is that today,
     * and if another way of invoking one turns out to carry the same guarantee it belongs here too. A
     * construction written here is not: it is the very thing being judged, and seeding it assumes
     * what the judgement is about. Written that way first, a construction over a value nothing was
     * known of came out proved — and came out proved with the clause meant to establish it taken
     * away, which is the shape of a check that has stopped checking.
     *
     * <p>Only what holds of every answer. A rule under a case is about an answer that is that case,
     * and nothing here has opened one; that rule is taken in where the case is known, which is at the
     * arm ({@link #enteringArm}). A rule under no case holds of whatever the behavior answered, so
     * the call is where it is known.
     *
     * <p>Sound for a behavior that reaches outside the language, and for the same reason it is sound
     * for one that does not. Two writings of such a call are two evaluations and so two subjects, but
     * each invocation answers something its own declaration holds of it. What the effect decides is
     * whether the two may be identified, not whether either of them was declared about.
     *
     * <p>Held of the value and not of the path. What is guaranteed of a value is true wherever that
     * value is named, and an evaluation is named at one place, so there is no branch on which it is
     * less true.
     *
     * <p>Of this expression and of nothing inside it. What a subexpression guarantees is taken in
     * where the walk evaluates that subexpression, which is before it gets here: an evaluation
     * answers after the values it was computed from have answered, and a guarantee read the other
     * way round is a fact about this answer standing where the operands that produce it are still
     * being judged. Read over the whole subtree from out here, that is what it was — a call's
     * guarantee constrains the arguments it relates the answer to, so a construction written in an
     * argument was being discharged by what the call around it promises about a value it has not
     * been given yet.
     *
     * <p>Which also puts the walk and this in one order rather than two. The walk knows what an
     * expression evaluates and when ({@link souther.compiler.core.Evaluated}); a second reading that
     * descended on its own would have to know it again, and the two would answer for a construction
     * and for the answers it is built from separately.
     */
    Known answeredHere(Core e, Known k, Denotations at) {
        if (e instanceof Core.Block) {
            return k;
        }
        Known out = k;
        if (isACheckedProducer(e)) {
            seeded(e);
            out = seedAt(e, out, at);
        }
        return assuming(answeredBy(e, at), e, guard -> guard instanceof Guard.Always,
                new Entered(out, at)).known();
    }

    /** Records an answer this read a guarantee off, where a test is reading them. */
    private static void seeded(Core answer) {
        List<Core> watching = SEEDED;
        if (watching != null) {
            watching.add(answer);
        }
    }

    /**
     * Whether reaching {@code e} means reaching a value whose type's invariant something already
     * established.
     *
     * <p>Asked of what produced the value, and of nothing else. Not of the subject it was given: what
     * kind of subject a value has says whether two writings of it may be identified, which is a
     * different question with a different answer, and reading one as the other would take the
     * guarantee away from any answer that turned out to be shareable. Not of whether a contract was
     * declared either — that is what an {@code ensures} is, and a behavior with none still answers a
     * value of its type.
     *
     * <p>A call to a behavior is one: its implementation is a body this check read, or one another
     * compile read and published, and either way the construction was checked there. A construction
     * written here is not — it is the very thing being judged.
     */
    private boolean isACheckedProducer(Core e) {
        return e instanceof Core.Call call && call.fn() instanceof Core.Reached reached
                && reached.denotes() instanceof ValueName.Behavior;
    }

    /**
     * Seeds a reading with what the type of the value at {@code root} guarantees: a numeric
     * newtype's own invariant on its value, a predicate its invariant states of it, or a product
     * data's invariant over its fields (and one level of fields), each read at that very value.
     * Sound by closed construction — a value of type T was built through T's checked constructor.
     *
     * <p>Which is the same reading a construction gets, over field reads instead of field values: the
     * clause is the declaration's either way, and where it is established and where it is owed differ
     * only in direction.
     */
    Known seedAt(Core root, Known k, Denotations at) {
        return seedAt(root, RuleKey.THE_VALUE, k, at,
                new GuaranteeWalk.Extent.AsFarAs(GuaranteeWalk.FIELDS_SEEDED), null,
                InvariantChecker.Reach.EVERYTHING);
    }

    /**
     * The same, as far as {@code limit} levels down, with the types on the way recorded.
     *
     * <p>How far to seed is not one number. What a walk over a body can afford to read of a
     * parameter is a cost bound and stops at {@link GuaranteeWalk#FIELDS_SEEDED}; what a construction has to
     * satisfy has no depth at all, since a rule four records down refuses the outermost value
     * exactly as one on the top does. A projection that stopped at two and was then classified by a
     * walk that did not would call a bound complete that a rule below it moves.
     *
     * @param gathering told what this walk gathers, or null where nobody is collecting. A clause
     *                  governs a position from wherever it is written — the record the position is a
     *                  field of, and the declarations under that record it sits inside — and this
     *                  walk is where it is rebased onto the position it governs. A reader wanting
     *                  that list has to be told here or walk the same descent again and rebase it a
     *                  second way.
     */
    Known seedAt(Core root, RuleKey path, Known k, Denotations at, GuaranteeWalk.Extent extent,
                 InvariantChecker.Gathering gathering, InvariantChecker.Reach reach) {
        Seeding seeding = new Seeding(k, gathering);
        walk.from(root, path, at,
                new GuaranteeWalk.Scope(extent, reach.stopAt(), reach.withoutClauses(),
                        reach.withoutParts()),
                seeding);
        return seeding.known;
    }

    /**
     * The reading a seeding is: every guarantee taken as holding of the value it was read at, and
     * every stop accounted for.
     *
     * <p>What a stop costs is this reader's question and not the walk's. Most of them stop where
     * there was nothing to read, so what is asked is whether any rule stands under what is being
     * left, and only then is anything said. A walk that reported every stop would have a record with
     * one plain string field speaking for none of its positions.
     */
    private final class Seeding implements GuaranteeWalk.Reader {

        private Known known;

        private final InvariantChecker.Gathering gathering;

        private Seeding(Known known, InvariantChecker.Gathering gathering) {
            this.known = known;
            this.gathering = gathering;
        }

        @Override
        public void guaranteed(RuleKey path, TypeGuarantee guarantee) {
            known = taking(guarantee, known, gathering);
        }

        @Override
        public void stopped(RuleKey path, Type type, GuaranteeWalk.Stop why) {
            stopping(type, path, gathering, why);
        }

        @Override
        public void handedOn(RuleKey path, Type type) {
            // Whether a rule stands under what is being left is the reading's answer and was asked
            // there. Nothing is re-derived here from the type.
            if (gathering != null) {
                gathering.handedOn(path);
            }
        }

        @Override
        public void lostAClause(RuleKey path, List<RuleRef.Invariant> lost) {
            // Said whatever stands under the position, because the clause was read and lost rather
            // than never reached: a reader answering for the clauses it was handed would otherwise
            // answer for a rule it never saw.
            if (gathering != null) {
                gathering.missed(path, new RulesMissed.ClauseLost());
            }
        }

    }

    /**
     * {@code k} with what {@code guarantee} says taken as holding of the value it was read at.
     *
     * <p>The one step that turns an answer about a declaration into knowledge on this path, kept
     * apart from the reading that produced it. What a type guarantees is the same wherever it is
     * read; that it is held {@link Known.Held#OF_THE_VALUE} rather than of the path is this reader's
     * account of it, and the reader settling a choice has no use for it.
     *
     * <p>{@code gathering} is told here for the same reason: what was gathered is a fact about this
     * measurement and not about the model.
     */
    private Known taking(TypeGuarantee guarantee, Known k, InvariantChecker.Gathering gathering) {
        if (gathering != null) {
            for (TypeGuarantee.Part part : guarantee.parts()) {
                gathering.constrained(guarantee.rule(), part.part(),
                        InvariantChecker.partRead(part.owed()));
            }
            gathering.gathered(guarantee.rule(), guarantee.clause(), guarantee.written(),
                    Predicates.subjectsIn(guarantee.owed()));
        }
        return predicates.assume(guarantee.owed(), k, Known.Held.OF_THE_VALUE)
                .and(guarantee.quantified());
    }

    /**
     * Tells the reading where it stopped, and how much of what stands there the rules it left are
     * about.
     *
     * <p>Every way the walk stops short comes here, and every one of them leaves the rules unread.
     * Whether a stop is worth saying anything about is a question about the model — is any rule
     * written under what is being left — and asking it of the walk's own reach would answer that
     * whatever was not read had nothing in it.
     *
     * <p><b>Nothing here hands anything on.</b> A position whose rules belong to a reading opened
     * elsewhere is not a position this walk stopped at: the walk reads it, says what it states, and
     * says separately that something below it is somebody else's ({@link Seeding#handedOn}).
     */
    private void stopping(Type type, RuleKey path, InvariantChecker.Gathering gathering,
                          GuaranteeWalk.Stop why) {
        if (gathering == null || type == null || !guarantees.anyRuleUnder(type)) {
            return;
        }
        gathering.missed(path, new RulesMissed.WalkStopped(why));
    }

    /**
     * How much of what stands at a position the rules a stop left unread are about.
     *
     * <p>Exhaustive with no {@code default}, so a way of stopping added later is a compile error
     * here rather than a stop quietly counted as borne by every value.
     */
    static InvariantChecker.Borne leftBy(GuaranteeWalk.Stop why) {
        return switch (why) {
            // A depth this reader could not afford, and a name it was told to suppose holds values.
            // Each stops where a construction still has to make the value, so a rule under it can
            // refuse the construction.
            case PAST_THE_DEPTH, ASKED_TO_STOP -> InvariantChecker.Borne.BY_EVERY_VALUE;
            // Read where the name was met, and nothing is opened here — so nobody takes the rules
            // over. Counted as a handing on, a type holding its own kind would be discharged by the
            // reading made of it further up.
            case ALREADY_ENTERED -> InvariantChecker.Borne.BY_SOME_VALUES;
        };
    }

}
