package souther.compiler.check;

import souther.compiler.semantics.Accumulation;
import souther.compiler.semantics.NumericResult;
import souther.compiler.types.BinOp;
import souther.compiler.ast.Hir;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.Granularity;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.numeric.LinearForm;
import souther.compiler.numeric.OrderedInterval;
import souther.compiler.core.Core;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.BindingId;
import souther.compiler.types.Refinement;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.ReachName;
import souther.compiler.types.ValueName;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a value is to the invariant-discharge check: where it is, what it is called, and whether
 * anything can be said of it.
 *
 * <p>One question asked in one place. A guard states something of a value and a clause is read
 * against a value, and the two meet only by being named alike — so the naming is here rather than at
 * either end of it, and asking it of a name gives the same answer as asking it of the expression the
 * name was bound to. That is what makes naming an expression not change what is known of it.
 *
 * <p>Three answers, and they are not the same question. Where a value is, is a {@link Location} and
 * is what the seeding writes about. What a value is called, is a key: two expressions with one key
 * compute one value, which is the whole of what the fact set knows. What can be said of a value, is
 * whether a clause read against it could ever be discharged — a location always can, and so does a
 * number this grammar names, whatever computes it; a value of another kind only where a rule or a
 * guard reaches it — and it is what decides whether a construction is reported at all.
 *
 * <p>The middle question does not wait on the third. A value is called something because it can be
 * pointed at, and what is known of it is a separate matter that may well be nothing. Deciding the
 * one by the other is circular where it matters most: the first guard about a value would be read
 * under a naming that guard is what establishes.
 */
final class Terms {

    /** What a declaration read from here is read under. Handed down rather than made: two readings
     *  of one declaration under different policies answer a position differently, and nothing that
     *  reads one is in a position to know which the compilation asked for. */
    private final ReadingPolicy policy;

    private final Symbols symbols;

    /** The scope and the clause representation this reading was made with, for the readings below
     *  that ask what a declaration states. Held rather than rebuilt: a reader composing its own
     *  would be free to compose a different one. */
    private final RuleReadingSource rules;

    /** The symbols this reading was made against — what a reader holding this reading folds an
     *  expression of it against, rather than reaching for a library of its own. */
    Symbols symbols() {
        return symbols;
    }

    /**
     * What a clause states, read through this very reading.
     *
     * <p>Here and not built by each caller, so that there is one of it. {@link Predicates} holds
     * nothing but the reading it was given, so two of them would answer alike today — and the day
     * one of them remembers something, two of them are two readers.
     */
    private final Predicates predicates;

    /**
     * What a type guarantees of a value.
     *
     * <p>The one there is. A reader that built its own would be a second owner of an answer this
     * whole reading exists to have one of, and the day it remembers anything the two are two
     * readers — the argument {@link #predicates} is held here for, and the same one.
     */
    private final TypeGuarantees guarantees;

    /** Getting to the positions that reading is asked about, which is nobody's semantics. */
    private final GuaranteeWalk walk;

    private final Map<TypeSymbol, java.util.Optional<Type>> affineScalarBases = new HashMap<>();
    /** How the values of each atom this has named are spaced. Kept here because this is where an
     * atom's name is made: the key and the kind of number behind it are decided in one step, and
     * anywhere else would be a second place that has to agree about which is which. */
    private final Map<FactSubject, Granularity> atomKinds = new HashMap<>();

    /**
     * Every value each atom's own kind of number can take, for the atoms named at an expression.
     *
     * <p>Beside the spacing and for the reason the spacing is here: both are what an atom's type
     * says of it, and the type is known where the atom is named. What it is for is arithmetic: a
     * form is composed over numbers of any size, so what a recipe derives can be a number the value
     * never is — {@code Long.MIN_VALUE / -1} works out to one past the whole-number range, and the
     * operation that would have answered it aborts (spec §stdlib-int). Held against the atom rather
     * than in each recipe, so that a recipe added later is held to it without being asked.
     */
    private final Map<FactSubject, NumericDomain.Bounds> atomExtents = new HashMap<>();
    /**
     * The terms this reading has built, each held under the one instance standing for it.
     *
     * <p>What a term is made of is a graph and not a tree: a name read twice is one value read twice,
     * and {@code let (a, b) = t} reads {@code t} twice by itself. A term holds its parts rather than
     * a copy of them, and carries the hash they were hashed into, so a chain of them costs a link per
     * link. Sharing is what makes the comparison stop at the first line; it is not what makes two
     * terms equal ({@link Term}).
     */
    private final Term.Interner interned = new Term.Interner();
    /**
     * What this reading knows about each atom it has named by knowing which value it is: how the
     * value was reached, and what it carries whatever reached it ({@link AtomKnowledge}).
     *
     * <p>One table and not one per kind of thing worth knowing. Held as a table of recipes beside a
     * table of walks, the two were enumerated by name wherever an atom was read, and what a reader
     * did not enumerate an atom silently did not have — which is how a size named inside a
     * reduction's step came out with nothing known of it whatever the step did with it (#988). It
     * also let one atom stand in both tables at once, which is two answers to what one value is, and
     * neither table could see it.
     *
     * <p>What is held is what was read at naming time, as numbers. Nothing in it is a tree to be
     * read again and nothing in it holds what the names around a call denoted — a walk whose parts
     * could not be read then is not recorded at all, rather than recorded as somewhere to go back
     * to.
     */
    private final Map<FactSubject, AtomKnowledge> knowledge = new HashMap<>();

    /** The subject each evaluation this could not name is, made once per occurrence. Identity-keyed:
     * an occurrence is a node, and two nodes are two evaluations however alike they are written. */
    private final java.util.IdentityHashMap<Core, EvaluationId> evaluations =
            new java.util.IdentityHashMap<>();

    /** What each node a rewrite built stands for, so an occurrence keeps its identity through one. */
    private final java.util.IdentityHashMap<Core, Core> builtFrom = new java.util.IdentityHashMap<>();

    /**
     * What this knows about {@code atom}, which is never null: an atom nothing was recorded about is
     * one known to be reached no way and to carry nothing.
     *
     * <p>One lookup and three questions. What a reader wants of an atom is not one thing — which
     * atoms it reads, whether a recipe has to be put through a reading, what holds of it in every
     * reading — and answering them from one record is what keeps a reader from having to know how
     * many tables there are ({@link AtomKnowledge}).
     */
    AtomKnowledge knowledgeOf(FactSubject atom) {
        AtomKnowledge had = knowledge.get(atom);
        return had == null ? AtomKnowledge.nothing() : had;
    }

    /** Whether anything at all has been recorded about any atom, which is what says a reading has
     * nothing to derive rather than nothing to derive it from. */
    boolean knowsAnything() {
        return !knowledge.isEmpty();
    }

    /**
     * Records that {@code atom} was reached by {@code how}.
     *
     * <p>One value is reached one way. An atom recorded as reached two ways is the naming and the
     * reading disagreeing about which value the atom is, and every bound derived under that name is
     * about neither of them — which is as true of a walk recorded beside a piece of arithmetic as of
     * two pieces of arithmetic, and was checked only within each table until they were one.
     */
    void computedBy(FactSubject atom, AtomKnowledge.Computation how) {
        AtomKnowledge had = knowledgeOf(atom);
        if (!(had.computation() instanceof AtomKnowledge.Computation.None)
                && !sameComputation(had.computation(), how)) {
            throw new OneTermTwoDerivations("atom `" + atom.rendered() + "` was reached as "
                    + had.computation() + " and as " + how);
        }
        knowledge.put(atom, had.computedBy(how));
    }

    /**
     * Records that {@code atom} carries {@code facts} whatever reached it.
     *
     * <p>One answer whoever named it. What a value carries is a function of which value it is, so
     * two namings of one atom that came to different answers is one of them having read a rule the
     * other did not — and merging the two would make what an atom carries depend on how many readers
     * had got to it, with the reader that asked first answered from half of it.
     *
     * <p>Saying nothing is not one of the two answers. An expression may be named while the reading
     * cannot make out the operation behind it — a library operation applied bare reaches the naming
     * as an application and reaches {@link #asOperator} as nothing — and what comes back then is not
     * "this value carries nothing" but "this reader had nothing to read". Both writings are one
     * value and one atom, so the reading that did make out the operation answers for it, and nothing
     * about the order they were named in shows through: an absence never replaces an answer and
     * never disagrees with one. Two answers that are both answers still disagree, which is the case
     * this is here for.
     */
    void carrying(FactSubject atom, List<NumericConstraint> facts) {
        AtomKnowledge had = knowledgeOf(atom);
        if (facts.isEmpty()) {
            return;
        }
        if (!had.intrinsic().isEmpty() && !sameFacts(had.intrinsic(), facts)) {
            throw new OneTermTwoIntrinsicAnswers("atom `" + atom.rendered() + "` carries "
                    + had.intrinsic() + " and carries " + facts);
        }
        knowledge.put(atom, had.carrying(facts));
    }

    /**
     * Every atom {@code form} reaches: the ones it names, and the ones a recipe filed against those
     * is read from, however deep.
     *
     * <p>Naming one is not the same as being about one. {@code acc * x.value} is a product the
     * fragment cannot carry, so the form is a single atom and the two it was computed from are under
     * the recipe filed against that atom — and a reader that took the form's own atoms for what the
     * expression is about would miss both of them. Answered here because both tables are here.
     *
     * <p>What is followed is what the recipe is <em>read from</em> and not what it is arithmetic
     * over. The two are the same for a product, whose operands are its arithmetic, and they come
     * apart the moment a recipe holds something that decides which of its parts answers rather than
     * being one of them. Which is which is the recipe's own answer ({@link Derivation#formsRead})
     * and not this walk's: a reader deciding it by which components happen to be forms would be
     * answering a semantic question by a naming convention, and this is the reader that would go
     * wrong quietly — a form it does not reach leaves the places under it unbounded and nothing else
     * says so ({@link StepInputFacts}).
     *
     * <p>Both kinds of edge, and a closure taken with a visited set. What an atom carries relates it
     * to other values as surely as a recipe reads from them — a size no greater than the size of
     * what it was built from is a statement about two atoms, and reaching one of them without the
     * other leaves the relation saying nothing. Repetition is where this stops and is not an error:
     * intrinsic relations carry no ordering, so an atom reachable from itself through them is two
     * true statements rather than a value built out of itself. That is the opposite of what
     * repetition means over computation edges alone, which is why the two walks are separate and
     * neither is written in terms of the other ({@link DerivedNumericFacts}).
     */
    Set<FactSubject> reached(LinearForm<FactSubject> form) {
        return reachedFrom(form.coefs().keySet());
    }

    /**
     * Every atom reading {@code from} reaches by what those values carry, and no further.
     *
     * <p>The narrower of the two reachings and a different question from {@link #reachedFrom}. What
     * is wanted where a recipe is put through a reading is the values a relation drags in —
     * {@code Decimal.toInt} carries that its answer is within one of what it rounded, and what it
     * rounded may be arithmetic nothing else names. The values under a recipe are not wanted there:
     * reading a recipe reads its operands itself, and asking for them again multiplies with every
     * arm a reading is copied into ({@link ContextMultiplicity}).
     */
    Set<FactSubject> carriedReachFrom(java.util.Collection<FactSubject> from) {
        Set<FactSubject> out = new java.util.LinkedHashSet<>();
        java.util.Deque<FactSubject> todo = new java.util.ArrayDeque<>(from);
        while (!todo.isEmpty()) {
            FactSubject atom = todo.poll();
            if (!out.add(atom)) {
                continue;
            }
            for (NumericConstraint fact : knowledgeOf(atom).intrinsic()) {
                todo.addAll(fact.atoms());
            }
        }
        return out;
    }

    /** Every atom reading {@code from} reaches, the ones named among them. */
    Set<FactSubject> reachedFrom(java.util.Collection<FactSubject> from) {
        Set<FactSubject> out = new java.util.LinkedHashSet<>();
        java.util.Deque<FactSubject> todo = new java.util.ArrayDeque<>(from);
        while (!todo.isEmpty()) {
            FactSubject atom = todo.poll();
            if (!out.add(atom)) {
                continue;
            }
            todo.addAll(knowledgeOf(atom).directlyReads(atom));
        }
        return out;
    }

    /**
     * What every value reachable from {@code from} carries, as relations.
     *
     * <p>The closure and not the atoms themselves. What a size carries relates it to the size of
     * what it was built from, and that one carries its own being at or above nought — so a reader
     * taking in only what the atoms it named carry would take in the relation and leave the value at
     * the other end of it unspoken for.
     */
    List<NumericConstraint> carriedBy(java.util.Collection<FactSubject> from) {
        List<NumericConstraint> out = new ArrayList<>();
        for (FactSubject atom : reachedFrom(from)) {
            out.addAll(knowledgeOf(atom).intrinsic());
        }
        return out;
    }

    /** What this reading may spend, which the compilation set and nothing here makes. */
    ReadingPolicy policy() {
        return policy;
    }

    /** What a clause states, read through this reading. */
    Predicates predicates() {
        return predicates;
    }

    /** What a type guarantees of a value, read through this reading. */
    TypeGuarantees guarantees() {
        return guarantees;
    }

    /** The positions under a value, walked for a reader of this reading. */
    GuaranteeWalk walk() {
        return walk;
    }

    /**
     * A reading over {@code reading}'s tree, told where the declarations' invariants are.
     *
     * <p>{@code clauses} is what lets a recipe say what choosing an arm settles where the arm binds
     * a value: the answer is what that value's type guarantees, read through the one reading of a
     * declaration there is ({@link TypeGuarantees}).
     */
    Terms(Symbols symbols, Of reading, ReadingPolicy policy, Clauses clauses) {
        this.symbols = symbols;
        this.rules = new RuleReadingSource(symbols, clauses.expandedClauses());
        this.reading = reading;
        this.policy = policy;
        this.predicates = new Predicates(this);
        this.guarantees = new TypeGuarantees(symbols, clauses, predicates);
        this.walk = new GuaranteeWalk(guarantees, symbols);
    }

    /**
     * Which tree a reading is over, which decides whether a shape with no term says anything about
     * this compiler.
     *
     * <p>The discharge reader is handed a tree where the language's own operations are still
     * operations, so a shape it has no term for is a shape nothing here has got round to. The tree
     * that runs has those operations expanded into the folds they are, and meeting one of those is
     * the representation rather than a gap — recorded as a gap it would say this compiler cannot
     * name {@code List.map} when what it cannot name is a fold nobody wrote.
     */
    enum Of {
        /** The tree the invariant-discharge analysis reads. */
        THE_DISCHARGE_TREE,
        /** The tree the backend emits, which every measure is taken over. */
        THE_TREE_THAT_RUNS
    }

    private final Of reading;

    /**
     * Where a test in this package reads the shapes this had no term for, and null everywhere else.
     *
     * <p>Beside {@link InvariantChecker#WATCHING} and for the same reason. A shape with no term is
     * silent, and silence is what a value nothing can be said of produces as well — so the difference
     * between this compiler being unfinished and a value being unnameable has nowhere else to be
     * read, and a difference nothing can read stops being true without anything failing.
     */
    static List<String> UNSUPPORTED;

    /** The operator {@code e} is, where it is a library call written as a function, or {@code e}
     * itself. Reading it as the operator is what puts it on the one path the operator already has,
     * rather than on a second path that would have to be kept saying the same thing. */
    static Core asOperator(Core e) {
        ValueName operation = operationOf(e);
        List<Core> args = argsOf(e);
        if (operation == null || args.size() != 2) {
            return e;
        }
        BinOp op = DischargeRules.operator(operation);
        // Not a comparison any source wrote: a call read as the operator it stands for.
        return op == null ? e : new Core.Binary(op, args.get(0), args.get(1),
                SourceConstructOrigin.unwritten(), e.type(), e.pos());
    }

    /**
     * The library operation {@code e} calls, or null where it calls none.
     *
     * <p>Asked of the operation the call resolved to and not of the representation it is in. A body
     * that runs holds {@code Int.add} as a call to a library name and the tree a declaration's rules
     * are read in holds it standing, and the arithmetic is the same arithmetic — read in one
     * representation and not the other, a rule the check enforced was one the measure reported as
     * unread. {@link NumericMeasures} already asks about a size call this way.
     */
    static ValueName operationOf(Core e) {
        return switch (e) {
            case Core.Call call when call.fn() instanceof Core.Reached reached
                    && reached.name() instanceof souther.compiler.types.ReachName.OfLibrary library ->
                    library.denotes();
            case Core.PreservedCall preserved -> preserved.operation();
            case null, default -> null;
        };
    }

    /** What {@code e} hands over, where it is a call, and nothing where it is not. */
    static List<Core> argsOf(Core e) {
        return switch (e) {
            case Core.Call call -> call.args();
            case Core.PreservedCall preserved -> preserved.args();
            case null, default -> List.of();
        };
    }

    /**
     * The affine walk: literals and {@code +}/{@code -} compose; every other node is read as a leaf
     * ({@link #leafOf}, which decides whether it is an atom, a location, or opaque).
     *
     * <p>A node this has a rule for and cannot compose is read as a leaf as well. Reading the
     * structure of a value and naming the value are two questions: a variable product is outside the
     * fragment, and it is still one value, so what a guard states of it is still about the thing the
     * clause reads. Answering the first question with {@code null} and never asking the second is
     * what made {@code a * b} name nothing where it is written and something where it is bound —
     * which is a name changing what can be said of an expression.
     *
     * <p>The environment is what carries a binding, and what this walk is told about one is an
     * expression. It once answered a binder's reads with the form its value had — a second account
     * of what a name means, beside the one {@link Denotations} keeps, and weaker than it by exactly
     * the values the arithmetic cannot read. Inside a reduction's step, where nothing else enters a
     * binding, that weaker account was the only one there was and a helper taking a record ended the
     * read (ADR-0106). What is handed in now is {@link #readThrough}, which says which value a name
     * denotes and cannot say what its arithmetic comes to, so there is nowhere for such an account
     * to be written.
     */
    LinearForm<FactSubject> affineOf(Core raw, Denotations at) {
        return AffineForms.of(raw, at, affineReading);
    }

    /**
     * The same, saying where the reading stopped where it did.
     *
     * <p>Beside the above and carrying what it discards. Which expression had no rule here is a
     * fact about that expression, and a caller handed nothing back had to reconstruct it from the
     * shape of what it asked about — which is a second account of this walk, written by whoever
     * needed one. The environment travels with the expression for the reason
     * {@link AffineForms.ReadThrough} gives.
     */
    AffineForms.Outcome<FactSubject, Denotations> outcomeOf(Core raw, Denotations at) {
        return outcomeOf(raw, at, subject -> true);
    }

    /**
     * The same, naming only the atoms {@code names} accepts.
     *
     * <p>For a reader whose subjects are narrower than this one's. What may be named here is what
     * the discharge procedure can carry a fact about, which is every number it can identify; what a
     * measure may name is a coordinate of the value a clause is written about, which is fewer. A
     * reader that took this one's atoms and then found it had no coordinate for one of them was
     * holding a form it could not use and had thrown away the expression that made it — after which
     * what it said about the rule came from the shape of the whole side.
     *
     * <p>So the narrowing goes in rather than the failure coming out. An expression whose value this
     * reader cannot name is one the walk stops at, and a stop comes back with the expression and the
     * environment it was read in, like every other.
     */
    AffineForms.Outcome<FactSubject, Denotations> outcomeOf(Core raw, Denotations at,
            java.util.function.Predicate<FactSubject> names) {
        return AffineForms.outcome(raw, at, new AffineForms.Reading<FactSubject, Denotations>() {

            @Override
            public Symbols symbols() {
                return Terms.this.symbols;
            }

            @Override
            public LinearForm<FactSubject> leafOf(Core e, Denotations where) {
                LinearForm<FactSubject> named = affineReading.leafOf(e, where);
                return named == null || named.coefs().keySet().stream().allMatch(names)
                        ? named : null;
            }

            @Override
            public Denotations inside(Core.LetIn li, Denotations where) {
                return affineReading.inside(li, where);
            }

            @Override
            public AffineForms.ReadThrough<Denotations> readThrough(Core.Read read,
                                                                    Denotations where) {
                return affineReading.readThrough(read, where);
            }

            @Override
            public java.util.List<AffineForms.ReadThrough<Denotations>> alternativesOf(
                    Core.Read read, Denotations where) {
                return affineReading.alternativesOf(read, where);
            }

            @Override
            public boolean readsThrough(Core.FieldAccess fa, Denotations where) {
                return affineReading.readsThrough(fa, where);
            }
        });
    }

    /**
     * What this reader answers about its own environment, which is the whole of what is its own
     * about the walk.
     *
     * <p>Which nodes compose is a fact about the language and is {@link AffineForms}'s; what a leaf
     * is called, what a name means inside a binding, and which value a name denotes are this
     * reader's. The measure that finds the line a rule draws reads the same tree through the same
     * walk with its own answers to these.
     */
    private final AffineForms.Reading<FactSubject, Denotations> affineReading =
            new AffineForms.Reading<>() {

                @Override
                public Symbols symbols() {
                    return Terms.this.symbols;
                }

                @Override
                public LinearForm<FactSubject> leafOf(Core e, Denotations at) {
                    return Terms.this.leafOf(e, at);
                }

                @Override
                public Denotations inside(Core.LetIn li, Denotations at) {
                    return Terms.this.inside(li, at);
                }

                @Override
                public AffineForms.ReadThrough<Denotations> readThrough(Core.Read read,
                                                                        Denotations at) {
                    return Terms.this.readThrough(read, at);
                }

                /**
                 * No name here stands for several values.
                 *
                 * <p>What this reads is a clause a declaration wrote, whose names are the fields of
                 * the declaration and the bindings a body gave values to. An operation that hands a
                 * closure the elements of a container is what puts several values under one name,
                 * and the elements a rule of a declaration is written over are not bound by one:
                 * such a clause is read once for the value in front of it.
                 */
                @Override
                public java.util.List<AffineForms.ReadThrough<Denotations>> alternativesOf(
                        Core.Read read, Denotations at) {
                    return null;
                }

                @Override
                public boolean readsThrough(Core.FieldAccess fa, Denotations at) {
                    // A newtype's `.value` read off something that is not a place: what it wraps is
                    // what it is, which is the rule a location is keyed by ({@link #pathKey}) read of
                    // a computed value too. Without it `f(x).value` is one value where the same call
                    // given a name is the arithmetic its body wrote.
                    return !isAPlace(fa.target(), at)
                            && !Location.isStep(fa.target().type(), fa.field(), symbols);
                }
            };

    /**
     * The environment {@code li}'s body is read in: {@code li}'s binder entered as what its
     * initializer denotes.
     *
     * <p>The one place a {@code let} is entered. Every reader that goes inside one comes through
     * here — the region walk on its way into a body ({@link PathEngine#bindLet}), and this class
     * reading the arithmetic a helper's expansion became — so what a name means is settled once and
     * no reader interprets a binder for itself. Two accounts of it is what #867 was: the arithmetic
     * reader's own account could not carry a binding holding a record, and inside a reduction's step
     * it was the only account there was.
     *
     * <p>The initializer is read in the environment outside the binding, and the binder is entered
     * as what that reading found. What is recorded about the name is recorded under that denotation
     * and not under the binding; recording it under the binding is what made a named subexpression a
     * term of its own, answering differently from the very expression it was given (#676).
     *
     * <p>Nothing here branches on what kind of value the initializer is. A number, a record, a sum,
     * a value written into the source: the binder denotes it, and which of those it is decides what
     * can be said about it later and not whether the binding may be entered at all.
     */
    Denotations inside(Core.LetIn li, Denotations at) {
        // Entering a binding a walk is already inside is not a second binding of it. A branch is
        // read from where its conditional stood, which is inside these, over a tree that still holds
        // them.
        if (at.valueOf(li.binder().binding()) == li.value()) {
            return at;
        }
        // What the name is about is what it was given is about. Where even the identity reading has
        // nothing to name — an expression answering nothing at all — the name is what there is, and
        // it is one value however many times it is read.
        FactSubject about = subjectOf(li.value(), at);
        return at.binding(li.binder().binding(), li.value(),
                about != null ? about : placeSubject(li.binder().binding()),
                locationOf(li.value(), at), bodyKey(li.value(), at),
                numericMeaningOf(li.value(), at));
    }

    /**
     * What {@code e} folds to where every part of it is written out, or {@code null} where any part
     * of it is computed at run time and there is nothing to fold.
     *
     * <p>Folded against {@code symbols}, because which operations fold is a fact about the library
     * the expression was resolved against and not about the expression.
     *
     * <p>And read where it stands, which is what {@code at} is for. A helper call is expanded as a
     * binding over the helper's body, so a value an author wrote at the call is read through a name;
     * asked of the tree alone, {@code atLeast(value, 1)} states a rule about no value this can name
     * where {@code value >= 1} states one. The readings that ask this are the ones saying which
     * values a position holds and which strings a format admits, and both are about the values an
     * author wrote.
     */
    static Object folded(Core e, Symbols symbols, Denotations at) {
        Hir.Expr written = asWrittenValue(e, at);
        return written == null ? null : ConstEval.against(symbols).eval(written).orElse(null);
    }

    /**
     * The number {@code e} folds to at compile time, or {@code null} where it folds to none.
     *
     * <p>Over the tree alone, for a walk that carries an environment of its own and has entered the
     * bindings above what it hands over ({@link AffineForms.Reading#inside}). A binding standing
     * <em>inside</em> what is handed over is not read here, and the walks that meet one are named in
     * the issue on which readings cross a binding.
     */
    static BigDecimal constantNumber(Core e, Symbols symbols) {
        Object folded = folded(e, symbols, Denotations.none());
        if (folded instanceof Long n) {
            return BigDecimal.valueOf(n);
        }
        return folded instanceof BigDecimal d ? d : null;
    }

    /** A linear form scaled by a constant, when one side is a bare constant (a scalar multiply); null
     * when neither side is constant (a non-linear product). */
    static <A> LinearForm<A> scale(LinearForm<A> a, LinearForm<A> b) {
        if (a == null || b == null) {
            return null;
        }
        if (a.coefs().isEmpty()) {
            return b.times(a.constant());
        }
        return b.coefs().isEmpty() ? a.times(b.constant()) : null;
    }

    /** A node the affine walk composes nothing out of, as a form: a numeric atom, what a name was
     * given, or {@code null}. */
    private LinearForm<FactSubject> leafOf(Core n, Denotations at) {
        Core written = writtenValue(n, at);
        if (written != null && written != n) {
            return affineOf(written, at);
        }
        // A list written out has as many elements as it is written with, whatever they are.
        BigDecimal counted = writtenSize(n, at);
        if (counted != null) {
            return LinearForm.constant(counted);
        }
        FactSubject atom = atomOf(n, at);
        return atom == null ? null : LinearForm.atom(atom);
    }

    /**
     * What a name denotes, where the name stands for a term of its own and what it was given is
     * arithmetic this can read (spec §invariant-discharge-terms). The name and the expression it was
     * given are one value, and reading the name as an atom of its own leaves a guard on it saying
     * nothing about the value it was built from.
     *
     * <p>A name given a location is not this — {@link #atomOf} answers that with the location, which
     * is what the seeding wrote about — and neither is a name given a value written in the source,
     * which {@link #writtenValue} follows whatever the name denotes.
     *
     * <p>What is answered is the expression, and the environment it is read in. Reading it is
     * {@link AffineForms}'s: this once did the reading itself, and a caller that can answer with a
     * form is a caller that can keep an account of the arithmetic beside the one walk that has one.
     */
    AffineForms.ReadThrough<Denotations> readThrough(Core.Read read, Denotations at) {
        if (!computesAsWhatItWasGiven(read.binding(), at) || !carriesANumber(read.type())) {
            return null;
        }
        Core given = at.valueOf(read.binding());
        // The environment at the read. A binding is entered where its body is walked, so what a
        // name was given is read under the bindings that were in scope where it was made and under
        // whatever was bound after it; a binding tells itself from every other, so nothing bound
        // later answers for a name this one holds.
        return given == null || given == read ? null
                : new AffineForms.ReadThrough<>(given, at);
    }

    /**
     * How many elements a size call over a list written out counts, or {@code null} where its
     * argument is not one.
     *
     * <p>What the elements are is not asked. Whether a value is <em>written</em>
     * ({@link #isWritten}) is a question about the whole of it, down to every part, and it is the
     * right question where a construction is read as the value it builds — there a computed part is
     * a value the source does not hold. Counting reads the line the list is written on, and three
     * elements written there are three however each was arrived at. Asking the stronger question
     * left a list of parameters with a length nothing knew, and an invariant over it with no guard
     * an author could write: the length is not a value any condition could settle.
     */
    BigDecimal writtenSize(Core e, Denotations at) {
        Core container = DischargeRules.sizeArgOf(e);
        if (container == null) {
            return null;
        }
        return given(container, at).value() instanceof Core.ListLit list
                ? BigDecimal.valueOf(list.elements().size()) : null;
    }

    /** An expression a value is written at, and the environment that expression is read in — which
     * travel together, since following a name into a binding is what makes them differ. */
    record Given(Core value, Denotations at) {}

    /**
     * The expression {@code e}'s value is written at, and where it is read.
     *
     * <p>A name is not a way of building a value, and neither is a binding. {@code xs} given
     * {@code List.map(f, ys)} is that call, and a helper the discharge tree expanded is
     * {@code let $0 = ys in List.map(f, $0)} and is that call too — so a reader asking what a value
     * is asks here, and what comes back is the expression that built it beside the environment its
     * names mean something in.
     *
     * <p>One peeler and not one per reader. Every reader of a value meets the same two ways of
     * re-naming one, and each that answered for itself answered for the shapes its author had met:
     * a container read through its name and not through a binding, a closure's answer read through
     * neither. What is left to the reader is what is genuinely about the value — the fields a
     * construction is built from, the arithmetic a number is — and never how it was written down.
     *
     * <p><b>This is what the environment holds and not what a reading is licensed to follow.</b>
     * Every name a binding gives a value to is peeled here, which is the question a reader asking
     * what was built wants answered. What one occurrence may be read as another is a narrower
     * answer with an owner of its own ({@link AffineForms.Reading#readThrough}), and the walk that
     * resolves an occurrence under it is held to that owner rather than to this one — so the two
     * are not made one, however alike the shapes they peel look.
     */
    Given given(Core e, Denotations at) {
        if (e instanceof Core.Read r) {
            Core given = at.valueOf(r.binding());
            return given == null || given == e ? new Given(e, at) : given(given, at);
        }
        if (e instanceof Core.LetIn li) {
            return given(li.body(), inside(li, at));
        }
        return new Given(e, at);
    }

    static <A> LinearForm<A> negate(LinearForm<A> f) {
        return f == null ? null : f.negate();
    }

    static <A> LinearForm<A> add(LinearForm<A> a, LinearForm<A> b, boolean subtract) {
        if (a == null || b == null) {
            return null;
        }
        return subtract ? a.minus(b) : a.plus(b);
    }

    /**
     * The canonical atom key of a numeric value: a location ({@code x}, {@code p.a}, a newtype's
     * value), a size call over a nameable container, or anything else {@link #termKey} names — and
     * {@code null} where the value is not a number the domain carries, or where the term grammar
     * names it nothing. A call the representation did not keep standing is the second of those: what
     * a behavior answered is outside that grammar, so it is no more an atom than it was.
     *
     * <p>An atom exists because a value can be pointed at, not because anything is known of it. Two
     * writings of one value are one atom and so one unknown, which is the whole of what an atom
     * asserts; what is known of it is what a guard states and nothing else. Requiring something to
     * have been said before a value could be an atom made the first guard about a value the one that
     * could not be read, since it was read to decide whether that value had a name at all.
     */
    FactSubject atomOf(Core e, Denotations at) {
        FactSubject size = sizeAtomOf(e, at);
        if (size != null) {
            return size;
        }
        // Read where it is going to be an answer, and not before: what a value is called is a walk
        // of the whole expression, and a value of a type the domain carries nothing of takes no atom
        // however it was written.
        return carriesANumber(e) ? atomOfIdentity(subjectOf(e, at), e.type(), e, at) : null;
    }

    /**
     * The atom of the number {@code e} answers, where it answers it as the case carrying
     * {@code carried} rather than as its own value.
     *
     * <p>Same identity, different type. An operation answering {@code Int | DivisionByZero} carries
     * no number itself, so {@link #atomOf} names none for it — the number is what one of its cases
     * holds, and the arm that opens that case names it. Which is right, and it leaves a caller
     * standing at the call with no way to ask about the number the call would answer: the arm is
     * further on than the question is.
     *
     * <p>So the atom is asked for here at the type the case carries. It is the atom the arm names
     * and not one beside it — an arm's binding is an alias for what the operation answered, so both
     * are the operation's own identity held to that type's spacing, and whichever asks first is
     * where it is recorded.
     */
    FactSubject atomOfTheCaseCarrying(Core e, Type carried, Denotations at) {
        return carriesANumber(carried) ? atomOfIdentity(subjectOf(e, at), carried, e, at) : null;
    }

    /**
     * What a position is called, and the atom it is where the numeric domain carries one.
     *
     * <p>Both from one reading. Where a value is a number the domain carries, its atom <em>is</em>
     * its subject — the same identity, recorded with how that type's values are spaced. Asked
     * separately the expression is read once for each, and a caller asking it of every level of a
     * field chain pays more than the chain is long (#826).
     *
     * <p>Not every position is both. A size takes an atom the symbolic reader builds over the
     * container it counts, which is not what the position is called; and a position of a type the
     * domain carries nothing of — an enumeration, a string — is called something and is no number.
     */
    Position positionOf(Core e, Denotations at) {
        FactSubject size = sizeAtomOf(e, at);
        FactSubject key = subjectOf(e, at);
        if (size != null) {
            return new Position(key, size);
        }
        return new Position(key, carriesANumber(e) ? atomOfIdentity(key, e.type(), e, at) : null);
    }

    /** What a position is called, and the atom it is — either may be absent. */
    record Position(FactSubject key, FactSubject atom) {}

    /**
     * Whether the numeric domain carries values of what {@code e} answers at all. A number the term
     * grammar cannot read is still a number, which is why an atom asks this of the type and not of
     * whether the expression has a symbolic key.
     *
     * <p><b>Asked of the carrier, which is the one authority for what counts.</b> A date is counted
     * in days and a time of day in seconds, and a rule relating two of them states a distance as
     * readily as a rule over two whole numbers does. Which of the two scalars a type reaches is a
     * narrower question and answers about fewer carriers than the arithmetic here can hold.
     *
     * <p>A string is a carrier and counts nothing: its values are ordered and stand no measurable
     * distance apart, which is a difference the carrier already answers.
     */
    private boolean carriesANumber(Core e) {
        return carriesANumber(e.type());
    }

    /** The same, of a type a caller already holds. */
    private boolean carriesANumber(Type t) {
        Carrier carrier = Carrier.ofValue(t, symbols);
        return carrier != null && carrier.counts();
    }

    /** {@code identity} as the atom of a value of {@code type} computed at {@code e}: held to how
     * that type's values are spaced, and recorded against the arithmetic it was built by. The type
     * is a parameter because it is not always {@code e}'s own — an operation answering a union
     * computes a number one of its cases carries. */
    private FactSubject atomOfIdentity(FactSubject identity, Type type, Core e, Denotations at) {
        FactSubject atom = named(identity, granularityOf(type));
        if (atom != null) {
            // Of the type itself, which is what the carrier is asked of. An atom named with nothing
            // said about where its counts run starts every rule about it from a range that admits
            // what the order does not hold.
            atomExtents.putIfAbsent(atom, extentOf(type));
            recording(atom, e, type, at);
            // What the value carries is filed where the value is named, so that a reader holding the
            // atom and no tree has it. Asked of the operation the value came from, whichever surface
            // it was written on — the same normalisation the recipe is read through.
            if (asOperator(e) instanceof Core.PreservedCall call) {
                carrying(atom, IntrinsicNumericFacts.ofCall(call, atom, at, this));
            }
        }
        return atom;
    }

    /**
     * Every value {@code atom} can be, or null where nothing said.
     *
     * <p>What the kind of number holds and not what a declaration narrows it to. A value of a
     * newtype has satisfied that newtype's invariant, and a construction being checked is the
     * question of whether it does — so reading the declared range here would answer the question
     * with itself. What is read is the carrier under it, which is the machine's range and is true of
     * every value that exists at all.
     */
    NumericDomain.Bounds extentOf(FactSubject atom) {
        return atomExtents.get(atom);
    }


    /**
     * Records how {@code atom} was computed, where it stands for arithmetic the affine fragment
     * cannot carry.
     *
     * <p>Here because this is where such an atom is named, and the name is what everything else
     * about it is filed under: a second place deciding which expressions are products would be a
     * second answer to keep in step with this one. It is the same reason the spacing of an atom is
     * recorded here ({@link #named}).
     *
     * <p>What is recorded is how the value was computed and not what it lies between. What it lies
     * between depends on what the path assumed, and the path is not something the naming of an
     * expression knows — which is why the walk that reads the operands is not handed one.
     */
    private void recording(FactSubject atom, Core e, Type answered, Denotations at) {
        // What was computed first, and the representation only where nothing was. Asked the other
        // way round, a value the table states arithmetic for reached the walk reader because the
        // reading had kept its call standing — so which of the two answered turned on how the tree
        // was written, which is what a meaning read once exists to stop deciding.
        NumericMeaning meaning = numericMeaningOf(e, answered, at);
        Derivation made;
        if (meaning != null) {
            made = recipeFor(meaning, at);
        } else if (asOperator(e) instanceof Core.PreservedCall call
                && Reductions.reducing(call, at) instanceof Reductions.Reducing walk) {
            recordingWalk(atom, walk, e, at);
            return;
        } else if (asOperator(e) instanceof Core.PreservedCall accumulation
                && Accumulations.accumulating(accumulation)
                        instanceof Accumulations.Accumulating accumulating) {
            recordingAccumulation(atom, accumulating, e, at);
            return;
        } else {
            // What is left is a value that is one of several. Asked last because being one of
            // several is what a value is where nothing else says what it was computed from — a
            // choice between two quotients is a choice, and each of its arms is a quotient.
            //
            // What sends a reading here is that the value was none of the above, and not that the
            // reading above it came to nothing: a walk this cannot read every part of is still a
            // walk, and reading it a second way as the arms of a choice would answer about a value
            // by whichever reader happened to get furthest. That is the same order the arithmetic is
            // in — a meaning with no recipe stops rather than falling through — and it is why the
            // question asked of a call is whether it is a walk rather than whether one was recorded.
            made = chosen(Choice.of(asOperator(e)), at);
        }
        if (made == null) {
            return;
        }
        computedBy(atom, new AtomKnowledge.Computation.Derived(made));
    }

    /**
     * Records the walk {@code atom} is the answer of, where every part of it is a number this reads.
     *
     * <p>Whether the call is a reduction at all is settled before this is called and not inside it.
     * That question decides which reading owns the value, and a reader answering it here could only
     * report the answer by declining to record — which the caller would have to read as "not a
     * walk", and it is not.
     *
     * <p>Here for the reason {@link #recording} is here: an atom's name is made in this class, and
     * what is filed against a name belongs where the name is made. What is filed is numbers — a form
     * for the seed, a form for what the step answers, an atom for the accumulator, and what holds of
     * everything else the step is handed. Read now, because now is when what the names around the
     * call denote is known; kept as values, because a table that outlives a reading may not hold one.
     *
     * <p>The step's parameters are entered as places of their own before the step is read. They are
     * places: a walk hands its step a value the step may state things about, and reading it as an
     * evaluation of the node it is written at would give one parameter two atoms where the step reads
     * it twice.
     *
     * <p>A step this cannot read as a form is recorded as nothing rather than as a walk with a part
     * missing. What it cannot read is narrower than the arithmetic: a step is one value, and a value
     * this composes nothing out of is named all the same, so a step that branches is a form over the
     * choice it is and is bounded by what its arms answer ({@link Derivation.Chosen}). What stays
     * outside is a seed that is what some behavior answered.
     */
    private void recordingWalk(FactSubject atom, Reductions.Reducing walk, Core e, Denotations at) {
        LinearForm<FactSubject> seed = affineOf(walk.seed(), at);
        if (seed == null) {
            return;
        }
        // Named against the walk and not against the bindings the step was written with: the walk's
        // own atom normalises those bindings, so two readings of one walk are one atom and would
        // otherwise carry two accumulators under it (Term.Shape.HANDED).
        List<Core.Binder> params = walk.step().params();
        FactSubject accumulator = null;
        Denotations inside = at;
        for (int i = 0; i < params.size(); i++) {
            FactSubject handed = FactSubject.of(interned.handed(atom.identity(), i));
            inside = inside.location(params.get(i).binding(), handed, handed.identity());
            if (params.get(i) == walk.accumulator()) {
                accumulator = handed;
            }
        }
        LinearForm<FactSubject> step = affineOf(walk.step().body(), inside);
        if (step == null) {
            return;
        }
        // The accumulator is a number of the kind the walk answers, whether or not the step read it:
        // a step that ignores it names it nowhere, and a range is still asserted about it here.
        named(accumulator, granularityOf(e.type()));
        // What the step reaches is asked after the step has been read, which is what names the atoms
        // inside it and files what each of them carries. A place a relation of theirs names is one
        // this has to keep, so the reaching is taken over both kinds of edge and not over the
        // recipes alone ({@link #reached}).
        InductiveBounds.Walk made = new InductiveBounds.Walk(seed, accumulator, step,
                StepInputFacts.of(walk, inside, this, rules, policy, reached(step)));
        computedBy(atom, new AtomKnowledge.Computation.Reduction(made));
    }

    /**
     * Records the walk {@code atom} is the answer of, where the operation is handed no step and no
     * seed and what it repeats is what it means ({@link Accumulations}).
     *
     * <p>The same walk, made of the same numbers. {@link InductiveBounds} is written against a seed,
     * an accumulator, a step and what holds of what the step is handed, and asks for no tree and no
     * operation's name — so an accumulation is one of its walks as soon as those four are made, and
     * a total written {@code List.sum(ns)} is proved by what proves the fold that spells it out.
     *
     * <p>Nothing is expanded to get them. The accumulator and the element are places of this walk,
     * as a reduction's parameters are, and the seed is the identity the operation starts from read
     * at the type the call answers — not a literal written into a tree for the types to be inferred
     * off again, which is the reading ADR-0082 has going the other way.
     *
     * <p>Both places are named with how their values are spaced, for the reason
     * {@link #recordingWalk} names its accumulator: {@link InductiveBounds} asks the domain to
     * assume a range for the accumulator, and a range asserted about an atom whose spacing was never
     * recorded is one the domain refuses — which would be a walk of the right shape that settles
     * nothing.
     */
    private void recordingAccumulation(FactSubject atom, Accumulations.Accumulating accumulating,
                                       Core e, Denotations at) {
        Granularity spacing = granularityOf(e.type());
        FactSubject accumulator =
                named(FactSubject.of(interned.handed(atom.identity(), 0)), spacing);
        FactSubject element = named(FactSubject.of(interned.handed(atom.identity(), 1)), spacing);
        LinearForm<FactSubject> seed = startedFrom(accumulating.what().identity());
        LinearForm<FactSubject> step =
                repeating(accumulating.what().combine(), accumulator, element, spacing);
        if (seed == null || step == null) {
            return;
        }
        UniversalElementFacts elements =
                UniversalElementFacts.of(accumulating.container(), at, this, rules, policy);
        computedBy(atom, new AtomKnowledge.Computation.Reduction(new InductiveBounds.Walk(
                seed, accumulator, step,
                StepInputFacts.ofTheElement(elements, element, this, reached(step)))));
    }

    /** The value an accumulation starts from, as a number — or null where the domain carries no such
     * value. The empty list a {@code List.concat} starts from is a value the library states and this
     * reading has no number for, which is a fact about this reading. */
    private LinearForm<FactSubject> startedFrom(Accumulation.Identity identity) {
        return switch (identity) {
            case ZERO -> LinearForm.constant(java.math.BigDecimal.ZERO);
            case ONE -> LinearForm.constant(java.math.BigDecimal.ONE);
            case EMPTY -> null;
        };
    }

    /**
     * The step an accumulation repeats, as a form over the two places it is applied to — or null
     * where the domain carries no such arithmetic.
     *
     * <p>A product is not a form, so it is an atom that stands for one, recorded against the recipe
     * that says what it is — the same recipe the naming makes of a product written in a fold's step,
     * so what proves one proves the other. Under the induction hypothesis the two operands are both
     * bounded and the recipe answers a range; read with nothing assumed of the accumulator it
     * answers none, which is what leaves the unprovable candidates unproved.
     */
    private LinearForm<FactSubject> repeating(Accumulation.Combine combine,
                                              FactSubject accumulator, FactSubject element,
                                              Granularity spacing) {
        return switch (combine) {
            case ADD -> LinearForm.atom(accumulator).plus(LinearForm.atom(element));
            case MULTIPLY -> {
                FactSubject product = named(FactSubject.of(interned.operator(
                        BinOp.MUL, accumulator.identity(), element.identity())), spacing);
                computedBy(product, new AtomKnowledge.Computation.Derived(new Derivation.Product(
                        LinearForm.atom(accumulator), LinearForm.atom(element))));
                yield LinearForm.atom(product);
            }
            case APPEND -> null;
        };
    }

    /**
     * The recipe {@code meaning} is, or null where the numeric fragment derives nothing in it.
     *
     * <p>Two questions and not one. What was computed is {@link NumericMeaning} and is the
     * operation's own semantics; what of it this can prove in is a recipe, and it is less — a
     * meaning with no recipe is arithmetic this reads and derives nothing from, which is what
     * {@code /} over {@code Decimal} has always been.
     */
    private Derivation recipeFor(NumericMeaning meaning, Denotations at) {
        if (meaning == null) {
            return null;
        }
        return switch (meaning) {
            case NumericMeaning.Operator(BinOp op, Core left, Core right) ->
                    op == BinOp.MUL ? product(left, right, at) : null;
            case NumericMeaning.TruncatingQuotient(Core dividend, Core divisor) ->
                    divided(dividend, divisor, at, Derivation.TruncatingQuotient::new);
            case NumericMeaning.TruncatingRemainder(Core dividend, Core divisor) ->
                    divided(dividend, divisor, at, Derivation.TruncatingRemainder::new);
            case NumericMeaning.RoundedQuotient(Core dividend, Core divisor, Core scale, Core _) ->
                    rounded(dividend, divisor, scale, at);
        };
    }

    /** The product of {@code left} and {@code right}, or null where either factor is a value nothing
     * can be said of. A factor that is a written constant is not this: that product is a scalar
     * multiply and the fragment carries it ({@link #scale}). */
    private Derivation product(Core left, Core right, Denotations at) {
        LinearForm<FactSubject> over = affineOf(left, at);
        LinearForm<FactSubject> by = affineOf(right, at);
        return over == null || by == null ? null : new Derivation.Product(over, by);
    }

    /**
     * The environment an arm's answer is read in: {@code at} with what choosing that arm binds
     * entered.
     *
     * <p>Here for the reason {@link #inside} is here. What a binder denotes is this class's answer,
     * and a reader deciding it for itself is a second account of it — weaker than the first wherever
     * it was written for a narrower purpose. An arm's binder was the one binder without that rule:
     * the walk over a region worked it out in {@link PathEngine} and the naming of an expression
     * could not reach it, which is why an arm whose body read what the arm bound was named against
     * an environment the name was not in and got no recipe at all.
     *
     * <p>Denotations and nothing else. What choosing an arm <em>settles</em> — the constraints of a
     * condition, what a construction guarantees, what a case refines the scrutinee to — is a
     * different question, needs a domain to settle it against, and is answered where that domain is
     * ({@link DerivedBounds}). A {@link Known} or a {@link NumericConstraint} coming back from
     * here would be this reader answering it a second way.
     */
    Denotations choosing(Choice.Decides decidedBy, Denotations at) {
        return chose(decidedBy, at).at();
    }

    /**
     * Where a reading stands once an arm is chosen, and the value that arm opened.
     *
     * <p>Both halves from one reader. Which value an arm opens and where the reading then stands are
     * one answer about one node, and a second method working the first out for itself would be a
     * second account of a node that has an owner — the thing the sum
     * ({@link Choice.Decides}) is a sum for. So this is the only reader here that names the ways of
     * deciding, and what wants either half asks it.
     *
     * @param opened the value the arm brought into being, or null where it brought none: a
     *               condition names values that stand whatever arm is chosen, a departure was taken
     *               where nothing was built, and an operation whose arms are chosen by how its
     *               arguments stand relates arguments that were already there
     */
    record Chose(Denotations at, Core.Read opened) {}

    /** The same question {@link #choosing} answers, with what it opened said beside it. */
    Chose chose(Choice.Decides decidedBy, Denotations at) {
        return switch (decidedBy) {
            // A condition binds nothing. What it settles is read where the arm is read.
            case Choice.Decides.ACondition ignored -> new Chose(at, null);
            // A departure is taken where nothing was built, so it has nothing to enter.
            case Choice.Decides.ItDeparted ignored -> new Chose(at, null);
            case Choice.Decides.ACase(Core.Case arm, Core scrutinee) ->
                    new Chose(opening(arm, scrutinee, at), openedByArm(arm));
            case Choice.Decides.ItWasBuilt(Core.IfConstructed ic) -> {
                Core.Read root = read(ic.binder(), ic.construct().type(), ic.pos());
                yield new Chose(entering(root, at), root);
            }
            // An operation defined by cases answers a value the call was already given, written
            // where the call is. It introduces no name, so there is nothing to enter.
            case Choice.Decides.ByArgumentRelations ignored -> new Chose(at, null);
        };
    }

    /** What a {@code match} arm binds, or null where it binds nothing — which is the same condition
     * {@link #opening} leaves the reading where it found it under. */
    private static Core.Read openedByArm(Core.Case arm) {
        return arm.binder() == null || arm.bindType() == null
                ? null : read(arm.binder(), arm.bindType(), arm.pos());
    }

    /**
     * The arm's binding entered as the value it opens.
     *
     * <p>An arm that names one case of a declared sum, and one that names several, bind the value
     * they were given — the case's own class is what is tested and the value read is that instance
     * ({@link Refinement.Direct}). So the arm is not introducing a value; it is saying which case the
     * one already there is, and what it binds is about that same value. Entered as a place all the
     * same, because a place is what a clause may be read against and what the seeding writes about,
     * and which value it is and what may be done with it are two answers.
     *
     * <p>Introduced afresh where the two are really different values. An optional's present carrier
     * binds what stands under it, which is not the optional.
     *
     * <p>Held one way and not two: an arm that made a second subject for the value it opened had
     * every fact about the answer filed under one and every fact the arm added under the other, and
     * the two agreed only for as long as nothing could tell them apart (#824).
     */
    private Denotations opening(Core.Case arm, Core scrutinee, Denotations at) {
        if (arm.binder() == null || arm.bindType() == null) {
            return at;
        }
        Core.Read root = read(arm.binder(), arm.bindType(), arm.pos());
        Opens opens = opens(arm, scrutinee, at);
        return opens == null
                ? entering(root, at)
                : at.opened(root.binding(), opens.value(), opens.subject(),
                        placeTerm(root.binding()), opens.numeric());
    }

    /** {@code at} with {@code root} entered as a location: somewhere nothing else names, holding a
     * value of its type. */
    private Denotations entering(Core.Read root, Denotations at) {
        return at.location(root.binding(), placeSubject(root.binding()),
                placeTerm(root.binding()));
    }

    /** What an arm's binding stands for: the value the walk reached where the two are one value, and
     * the subject facts about it are filed under, and which arithmetic it is.
     *
     * <p>Taken together because they are one answer about one binding, and handing them over apart is
     * how one of them was left behind. Three answers and not one: the value is where it came from,
     * which is how a rule declared about an answer is found through however many names the answer
     * went by, and the arithmetic is what was computed to make it. An arm opening the number a
     * library operation answered as a case is where the two are furthest apart — the value it stands
     * for is the union, and the number it is is a quotient of two operands the union does not carry.
     *
     * <p>What the term grammar names it by is not among them, and is the place it is. A binding an
     * arm opens is a place — that is what lets a clause be read against it — and a place is named by
     * where it is: the two readers of what a binding's term is ({@link #keyOfNowhere},
     * {@link #computesAsWhatItWasGiven}) are the readers of a binding that is <em>not</em> one.
     * A term written here would be a second account of what names a binding, and the one nobody
     * reads. */
    private record Opens(Core value, FactSubject subject, NumericMeaning numeric) {}

    /**
     * What the arm's binding opens, or null where nothing here says.
     *
     * <p>Asked of what the pattern binds and not of what the arm looks like. A case whose carrier is
     * the value binds that value, so the binding stands for the scrutinee and is about it. An
     * optional's present carrier binds what stands under it: a different value, named as what that
     * optional holds, and one no expression here is — so it is about something while standing for
     * nothing. An absent carrier binds nothing at all. That is the whole of it — {@link Refinement}
     * has three answers and each one settles this.
     */
    private Opens opens(Core.Case arm, Core scrutinee, Denotations at) {
        FactSubject of = subjectOf(scrutinee, at);
        if (of == null) {
            return null;
        }
        return switch (arm.pattern().binding()) {
            case Refinement.Direct(Type carried) -> arithmetic(carried, scrutinee, at);
            case Refinement.OptionPresent ignored -> new Opens(null, heldBy(of), null);
            case Refinement.OptionAbsent ignored -> null;
        };
    }

    /**
     * The arm's binding as the number a library operation computed, where the case it opened is
     * where that operation answers one — and as the value the walk reached otherwise.
     *
     * <p>Which case was opened is decided here and the arithmetic is not. What an operation computes
     * and where it answers it is one row of one table (spec §invariant-discharge-arithmetic), and
     * this reads the row: a reader that recognised {@code divide} for itself would be a second place
     * deciding which spellings are divisions, and the next operation answering a number as a case
     * would be a third.
     *
     * <p>Read through the names the call was given, as everything else about a scrutinee is: {@code
     * let q = Int.divide(a, b)} and a {@code match} written straight over the call are the same
     * program, and a binding between the two is a name for the call rather than a step away from it.
     */
    private Opens arithmetic(Type carried, Core scrutinee, Denotations at) {
        Core called = originating(scrutinee, at, new HashSet<>());
        NumericResult<DeclaredArgument> result = called == null ? null
                : DischargeRules.numericResult(operationOf(called));
        if (result == null || !(result.at() instanceof NumericResult.Answered.InTheCaseCarrying(
                Type answersIn)) || !answersIn.equals(carried)) {
            return new Opens(scrutinee, subjectOf(scrutinee, at), null);
        }
        NumericMeaning meaning = computedBy(result, argsOf(called), carried);
        FactSubject subject = subjectOpenedAs(meaning, carried, called, at);
        // A call the term grammar cannot name leaves the number it answers named by nothing this can
        // relate to anything else, and a binding standing for the value it opened is what an arm has
        // always given. Nothing is lost by declining here; what is lost by naming it anyway is the
        // one thing an atom asserts, which is that two writings of it are one value.
        return subject == null ? new Opens(scrutinee, subjectOf(scrutinee, at), null)
                : new Opens(scrutinee, subject, meaning);
    }

    /** The call {@code value} came from, through however many names it was given, or null where it
     * came from something else. What an operation computes is a question about the operation, not
     * about which tree is being read, so a call in either representation is one. */
    Core originating(Core value, Denotations at, Set<BindingId> seen) {
        if (operationOf(value) != null) {
            return value;
        }
        if (value instanceof Core.Read read && seen.add(read.binding())) {
            Core given = at.valueOf(read.binding());
            return given == null || given == value ? null : originating(given, at, seen);
        }
        return null;
    }

    /**
     * The recipe {@code choice} is, or null where it is no choice or an arm of it is one this cannot
     * read.
     *
     * <p>Every arm or none. What the value is, is one of these, so a recipe over a subset of them is
     * a range the value can be outside of.
     *
     * <p>Which values those are is {@link Choice}'s answer and not this method's. Asked there so
     * that the readers of that question cannot come to disagree about it, which they had — an
     * attempted construction answers one of several and three spellings of the question left it out.
     *
     * <p>Each arm is read in the environment choosing it puts the reading in ({@link #choosing}), so
     * an arm whose body reads what the arm itself bound reads a name that is entered. What choosing
     * it states is recorded beside it as relations ({@link Conditions#settledBy}), which the naming
     * of an expression may hold: a relation is the same wherever it is read, and what it comes to
     * against a domain is not ({@link #recording}).
     *
     * <p>The decider itself is read where it stands, outside the arm: a condition and a scrutinee
     * are written where the choice is, and the binder an arm introduces scopes over the arm alone.
     */
    private Derivation chosen(Choice choice, Denotations at) {
        if (choice == null) {
            return null;
        }
        List<Derivation.Chosen.Arm> arms = new ArrayList<>();
        for (Choice.Arm arm : choice.arms()) {
            Terms.Chose chose = chose(arm.decidedBy(), at);
            LinearForm<FactSubject> form = affineOf(arm.answers(), chose.at());
            if (form == null) {
                return null;
            }
            arms.add(new Derivation.Chosen.Arm(form, settledBy(arm.decidedBy(), at, chose)));
        }
        return new Derivation.Chosen(arms);
    }

    /**
     * Everything choosing {@code decidedBy} settles, as relations.
     *
     * <p>Two sources and one question. What a condition states is read where the condition is
     * written, which is {@code outside} the arm; what a type guarantees of what the arm bound is
     * read where that name stands, which is {@code inside} it. Both are relations, and a relation
     * is the same statement wherever it was read, so they stand together beside the arm.
     */
    private List<NumericConstraint> settledBy(Choice.Decides decidedBy, Denotations outside,
                                              Chose chose) {
        List<NumericConstraint> out =
                new ArrayList<>(Conditions.settledBy(this, decidedBy, outside));
        out.addAll(guaranteedBy(chose.opened(), chose.at()));
        return out;
    }

    /**
     * What the type of the value choosing {@code decidedBy} opens guarantees of it, as relations.
     *
     * <p>The other half of what choosing an arm settles. A {@code match} arm binds the scrutinee
     * refined to the case it names, and an attempt binds what it built; either way the value was
     * built through its type's checked constructor, so what that type states holds of it. That is
     * the same argument a seeding rests on for a parameter, and it is the same reading — asked here
     * of a value a recipe names rather than of a place a walk stands at.
     *
     * <p>Under the arm and not beside it. The value only exists because that arm was chosen, so what
     * it guarantees is stated of that arm and of no other.
     *
     * <p>Nothing for an arm that opened no value, which is {@link #chose}'s answer and not this
     * method's: asking the node a second time here would be a second account of it.
     *
     * <p>Only the relations. A clause states what it states, and what a recipe can record beside an
     * arm is a relation ({@link NumericConstraint}); a fact about a value is settled where facts
     * are, and leaving it out costs precision where taking it in as something else would not be
     * sound.
     */
    private List<NumericConstraint> guaranteedBy(Core.Read root, Denotations inside) {
        if (root == null) {
            return List.of();
        }
        List<NumericConstraint> out = new ArrayList<>();
        // Every position, because this question has no depth in it. What the value guarantees is
        // what its type states wherever the rule is written, and a bound here would make a
        // derivation depend on how deeply an author nested a field rather than on what was declared.
        walk.from(root, RuleKey.THE_VALUE, inside, GuaranteeWalk.Scope.everyName(),
                (path, guarantee) -> out.addAll(guarantee.owed().relations()));
        return out;
    }

    /**
     * The quotient {@code b} is, or null where there is no rule about it.
     *
     * <p>Only over whole numbers, which is what having a {@link NumericMeaning.TruncatingQuotient}
     * at all says: {@code /} on {@code Int} truncates toward zero, and on {@code Decimal} it rounds
     * to a precision the run time sets (spec §stdlib-decimal), which is other arithmetic and not a
     * quotient this reads. That choice is the operation's and not the path's.
     *
     * <p>The divisor is a form, as the factors of a product are. Whether the path holds it away from
     * zero, and whether it is the kind of value the operator's divisor could be at all, are asked
     * where the recipe is read ({@link DerivedNumericFacts}): the first because the answer is the path's
     * and one expression is read under more than one, and the second because it is a question about
     * a range and no range is known here. Held as a written number, this had to refuse every divisor
     * with a coefficient in it, and a day count guarded above zero went unread.
     */
    private Derivation divided(Core over, Core by, Denotations at, Divided made) {
        LinearForm<FactSubject> numerator = affineOf(over, at);
        LinearForm<FactSubject> divisor = affineOf(by, at);
        NumericDomain.Bounds extent = extentOf(by.type());
        if (numerator == null || divisor == null || extent == null) {
            return null;
        }
        return made.of(numerator, divisor, extent);
    }

    /** The recipe a divide rounded to a scale is, or null where the fragment derives nothing in it.
     * The scale is a form, as the divisor is: whether it comes to one number is what a reading
     * proves of it, and one expression is read under more than one reading. */
    private Derivation rounded(Core over, Core by, Core places, Denotations at) {
        LinearForm<FactSubject> numerator = affineOf(over, at);
        LinearForm<FactSubject> divisor = affineOf(by, at);
        NumericDomain.Bounds extent = extentOf(by.type());
        if (numerator == null || divisor == null || extent == null) {
            return null;
        }
        LinearForm<FactSubject> scale = affineOf(places, at);
        return scale == null ? null
                : new Derivation.RoundedQuotient(numerator, divisor, extent, scale);
    }

    /** Which of the two answers of one division a recipe is about. Both are read off the same three
     * parts, so what tells them apart is which recipe is made and nothing else. */
    private interface Divided {
        Derivation of(LinearForm<FactSubject> numerator, LinearForm<FactSubject> divisor,
                      NumericDomain.Bounds divisorExtent);
    }

    /**
     * Every value a position of {@code type} can take, or null where nothing orders its values.
     *
     * <p>What the operator divides by is a value of its own type, and the arithmetic a form is
     * composed of runs over numbers of any size — so a form can name a number the operand never is.
     * Read off the carrier, which is where what a type's values run between is written down and the
     * one place it is ({@link Carrier#extent}).
     *
     * <p>A type with no carrier is asked about rather than assumed away. Which operands {@code /}
     * has is settled where it is typed and not here, so a divisor of a type nothing orders is a
     * rule this declines — which is what it does with everything else it cannot read — rather than a
     * dereference of a null. A rule written about an operand nothing orders is a rule about the
     * program, and stopping the compile over one would answer a question about the model with a
     * failure of this compiler.
     */
    private NumericDomain.Bounds extentOf(Type type) {
        Carrier carrier = Carrier.ofValue(type, symbols);
        if (carrier == null) {
            return null;
        }
        OrderedInterval extent = carrier.extent();
        return new NumericDomain.Bounds(extent.low(), extent.high());
    }

    /**
     * Whether two readings computed a value the same way.
     *
     * <p>Asked of the numbers and not of how they are written: a coefficient of {@code 0.10} and one
     * of {@code 0.1} are one number, and a record's own equality says they are two. What this is for
     * is catching the check naming two values alike, and a difference in scale is not that.
     *
     * <p>Asked of the recipe ({@link Derivation#sameAs}) and not worked out here from what it is
     * read from. Those are two questions: what a recipe is read from is a set of forms to reach, and
     * a reader comparing recipes by that list takes two recipes with the same forms grouped
     * differently for one. A choice is where the grouping carries meaning — a relation states what
     * it states of the arm it stands beside — so a recipe added later answers for its own shape
     * rather than being compared by a case somebody remembered to write.
     */
    private static boolean sameDerivation(Derivation a, Derivation b) {
        return a.sameAs(b, SAME_NUMBERS);
    }

    /** Whether two readings reached a value the same way. A walk is compared by the numbers it was
     * read as, which is what it is here. */
    private static boolean sameComputation(AtomKnowledge.Computation a, AtomKnowledge.Computation b) {
        return switch (a) {
            case AtomKnowledge.Computation.None ignored ->
                    b instanceof AtomKnowledge.Computation.None;
            case AtomKnowledge.Computation.Derived(Derivation recipe) ->
                    b instanceof AtomKnowledge.Computation.Derived it
                            && sameDerivation(recipe, it.recipe());
            case AtomKnowledge.Computation.Reduction(InductiveBounds.Walk walk) ->
                    b instanceof AtomKnowledge.Computation.Reduction it && walk.equals(it.walk());
        };
    }

    /** Whether two readings came to the same answer about what a value carries. Compared on the
     * numbers and not on the records' own equality, for the reason a recipe is: {@code 0.10} and
     * {@code 0.1} are one number, and two readings differing in nothing but a scale are not this
     * check disagreeing with itself. */
    private static boolean sameFacts(List<NumericConstraint> a, List<NumericConstraint> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i).rel() != b.get(i).rel()
                    || !AffineForms.sameForm(a.get(i).form(), b.get(i).form())) {
                return false;
            }
        }
        return true;
    }

    /** How the numbers in a recipe are compared, which is this reading's answer and not the
     * recipe's: what is being caught is the check naming two values alike, and a difference in
     * scale is not that. */
    private static final Derivation.Same SAME_NUMBERS = new Derivation.Same() {

        @Override
        public boolean forms(LinearForm<FactSubject> a, LinearForm<FactSubject> b) {
            return AffineForms.sameForm(a, b);
        }

        @Override
        public boolean extents(NumericDomain.Bounds a, NumericDomain.Bounds b) {
            return (a == null || b == null) ? a == b : sameExtent(a, b);
        }
    };

    /** Whether two extents run between the same places. Asked on the order and not of the record's
     * own equality, for the reason the numbers are: {@code 0.00} and {@code 0} are one place, and
     * two readings differing in nothing but a scale are not this check disagreeing with itself. */
    private static boolean sameExtent(NumericDomain.Bounds a, NumericDomain.Bounds b) {
        return sameEnd(a.min(), b.min()) && sameEnd(a.max(), b.max());
    }

    private static boolean sameEnd(Endpoint a, Endpoint b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.inclusive() == b.inclusive() && a.at().sameAs(b.at());
    }

    /**
     * One atom this said was computed two ways.
     *
     * <p>Beside {@link OneTermTwoKinds} and for its reason. A term is a value, and a value is
     * computed by whatever computes it — so an atom recorded as two different pieces of arithmetic
     * is the naming and the reading disagreeing about which value the atom is, and every bound
     * derived under that name is about neither of them.
     */
    static final class OneTermTwoDerivations extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        OneTermTwoDerivations(String message) {
            super(message);
        }
    }

    /**
     * One atom this said carries two different things.
     *
     * <p>Beside {@link OneTermTwoDerivations} and for its reason. What a value carries is a function
     * of which value it is, so two namings answering differently is one of them having read a rule
     * the other did not — and the reader that asked between them was answered from half of it.
     */
    static final class OneTermTwoIntrinsicAnswers extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        OneTermTwoIntrinsicAnswers(String message) {
            super(message);
        }
    }

    /** The atom of the size {@code e} takes of a container this can name, or null where it takes
     * none or names none. */
    FactSubject sizeAtomOf(Core e, Denotations at) {
        Core container = DischargeRules.sizeArgOf(e);
        return container == null ? null
                : sizeAtomFor(((Core.PreservedCall) e).operation(), container, at);
    }

    /**
     * The atom the size {@code size} of {@code container} is, named and with what it carries filed.
     *
     * <p>The one route to a size's atom, so that a size named while reading a clause and a size named
     * while relating one container to another are one value that carries one thing. Reached from
     * {@link IntrinsicNumericFacts} as well, which states one edge of a chain and leaves the rest to
     * the naming of the atom at the other end of it — so the chain is walked by this, once per atom,
     * rather than by a reader that had to remember to walk it.
     *
     * <p>Two readings of the container and they are not one. What the atom is <em>called</em> is read
     * off the container as written, which is how it has always been named and is what makes a size
     * over a name and a size over the expression it was given one atom ({@link #bodyKey} is what
     * resolves the name). What the atom <em>carries</em> is read off the value that name was given,
     * because the rules are about how the container was built and a name is not a way of building
     * one. Resolving before naming makes naming fail where the value has no key of its own though
     * the name has; asking the rules of the name instead states nothing where the expression states
     * something, and one atom then has two answers.
     */
    FactSubject sizeAtomFor(ValueName size, Core container, Denotations at) {
        Term counted = bodyKey(DischargeRules.sizeSource(container), at);
        if (counted == null) {
            return null;
        }
        FactSubject atom = sizeKeyOf(size, counted);
        if (atom != null) {
            // The rules are about how the container was built, so they are read of the expression
            // that built it — and in the environment that expression's own names mean something in,
            // which is not the one the name was read in where a binding stands between them.
            Given built = given(container, at);
            carrying(atom, IntrinsicNumericFacts.ofSize(size,
                    DischargeRules.sizeSource(built.value()), atom, built.at(), this));
        }
        return atom;
    }

    /** The size {@code size} takes of {@code container}, named as the whole number it is. A size
     * counts elements, so there is nothing to decide about how its values are spaced. It is the call
     * that takes it and nothing besides, which is the term a clause reading one builds and the term a
     * guard stating one builds — so the two are one value rather than two writings that have to keep
     * spelling each other alike. */
    private FactSubject sizeKeyOf(ValueName size, Term container) {
        return named(interned.called(size, List.of(container)), Granularity.DISCRETE);
    }


    /**
     * The atom a rule counting {@code e} would have bounded, or null where there is no such atom.
     *
     * <p>Written because the identity is not the field's name. A size is keyed on the shape
     * {@code length(<what the container keys as>)}, and what the container keys as is a location —
     * {@code demo.Bag.fields.0} and not {@code xs} — so a caller spelling that shape itself would be
     * agreeing with this by hand at every place it asked. What a value of a type is counted by is
     * {@link NumericMeasures}' answer, and the container's key is {@link #bodyKey}'s, and this is the
     * two of them put together in the one order they go together in.
     *
     * <p>Recognised and not made. {@link #sizeKeyOf} names a shape whether or not anything has
     * spoken about it, which is what a clause reading one needs; a reader asking what was said about
     * a field wants the name a clause already gave it, and answering with a fresh one would put an
     * atom nothing bounds into the domain and call that an answer.
     */
    FactSubject takenAtomOf(Core e, Type type, Denotations at) {
        ValueName.Stdlib counts = NumericMeasures.takenOf(type, symbols);
        if (counts == null) {
            return null;
        }
        Term container = bodyKey(e, at);
        return container == null ? null : FactSubject.of(interned.calledIfBuilt(counts, List.of(container)));
    }

    /**
     * One term this gave two kinds of number.
     *
     * <p>Apart from the shapes this check declines, and for a reason. Declining is about a rule it
     * has no words for, and the run-time check stands for what it declined. This is about one of the
     * check's own terms: a term is a value, and a value is one kind of number, so every relation
     * recorded against a term that is two of them relates the wrong things — and a body that came
     * back with nothing to say for that reason is a body whose invariants all discharge, said in the
     * same words.
     *
     * <p>What it is about has narrowed. A term was a string written out of its parts, and two values
     * could write one — a collision this would catch only where the two happened to be numbers spaced
     * differently. Terms are held by what they are made of now, so that route is gone and what is
     * left is the check handing one value two spacings.
     */
    static final class OneTermTwoKinds extends IllegalStateException {
         private static final long serialVersionUID = 1L;

        OneTermTwoKinds(String message) {
            super(message);
        }
    }

    /** {@code key}, with how its values are spaced recorded against it. A key is what a value is
     * called and a kind is what the value is, so one key is one kind: two would mean this named two
     * values alike, and everything recorded under the name would be about neither of them. */
    private FactSubject named(Term key, Granularity g) {
        return key == null ? null : named(FactSubject.of(key), g);
    }

    /** The same, of a subject already made. Both spellings record here, so an atom reached by either
     * is held to the one kind. */
    private FactSubject named(FactSubject subject, Granularity g) {
        if (subject == null) {
            return null;
        }
        Granularity had = atomKinds.putIfAbsent(subject, g);
        if (had != null && had != g) {
            throw new OneTermTwoKinds("atom `" + subject.rendered() + "` is " + had
                    + " and " + g);
        }
        return subject;
    }

    /**
     * How the values of a numeric type are spaced.
     *
     * <p>The carrier's answer, which is where the spacing of every order this language has is
     * written. An atom cannot be named without one, so a spacing decided here from whichever of two
     * scalars a type reaches would leave every other counted carrier out of the domain rather than
     * in it.
     */
    Granularity granularityOf(Type t) {
        Carrier carrier = Carrier.ofValue(t, symbols);
        if (carrier == null || !carrier.counts()) {
            throw new IllegalStateException("not a number the domain carries: " + Type.show(t));
        }
        return carrier.spacing();
    }

    /** The spacing of every atom {@code f} is written over, for the domain to record. Every one of
     * them was named here, so one that is not is a form built somewhere this cannot answer for. */
    Map<FactSubject, Granularity> kindsOf(LinearForm<FactSubject> f) {
        return kindsOfAtoms(f.coefs().keySet());
    }

    /** The same, for a name being given a form: the name is an atom too, and its own type says how
     * its values are spaced. */
    Map<FactSubject, Granularity> kindsOf(LinearForm<FactSubject> f, FactSubject atom, Type type) {
        Map<FactSubject, Granularity> out = new HashMap<>(kindsOf(f));
        Granularity g = granularityOf(type);
        named(atom, g);
        out.put(atom, g);
        return out;
    }

    private Map<FactSubject, Granularity> kindsOfAtoms(Set<FactSubject> atoms) {
        Map<FactSubject, Granularity> out = new HashMap<>();
        for (FactSubject atom : atoms) {
            Granularity g = atomKinds.get(atom);
            if (g == null) {
                throw new IllegalStateException("atom `" + atom.rendered() + "` was not named here");
            }
            out.put(atom, g);
        }
        return out;
    }

    /** An expression's canonical key: a location names itself, and everything else is read
     * structurally. */
    Term bodyKey(Core e, Denotations at) {
        return termKey(e, at, Map.of(), 0, Leaf.SYMBOLIC);
    }

    /** The identity a fact about {@code e} is filed under: the same algebra, taking an atom of its
     * own where the grammar runs out rather than answering nothing. */
    private Term identityOf(Core e, Denotations at) {
        return termKey(e, at, Map.of(), 0, Leaf.AN_EVALUATION);
    }

    /**
     * The subject a fact about {@code e} is about: the atom where the numeric domain carries one, and
     * the canonical key of the expression otherwise.
     *
     * <p>One place answers it. Three readers worked the same fallback out for themselves, and a
     * reader that decides for itself which of the two a value is named by is a reader that can decide
     * it differently from the one beside it.
     */
    FactSubject subjectOf(Core e, Denotations at) {
        return FactSubject.of(identityOf(e, at));
    }

    /**
     * Which arithmetic the value at {@code e} is, or null where it is arithmetic this does not read.
     *
     * <p>One reading of that question, whatever surface the value arrived on. An expression written
     * as an operator, a call to the library's function form of one, and a name an arm bound to the
     * number a library operation answered as a case all reach the same meaning, so a term, an atom
     * and a recipe are built off one answer rather than off three readings of three shapes (#959).
     *
     * <p>An arm's binding is answered from the environment, which is where a binder's meaning is
     * (ADR-0106): the arm decided which case it opened and wrote what that case carries down, and
     * this reads it rather than working the match out again from here.
     */
    NumericMeaning numericMeaningOf(Core e, Denotations at) {
        return numericMeaningOf(e, e.type(), at);
    }

    /**
     * The same, where the value being asked about is not {@code e}'s own but the one a case of it
     * carries.
     *
     * <p>{@code answered} is which of the two: an operation stating its arithmetic answers it
     * directly or as one case of a union ({@link NumericResult.Answered}), and reading the row only
     * at the first left a divide unreadable at the call and readable at the arm that opens it. Which
     * is right for what an arm binds and wrong for a reader standing where the call is — the same
     * arithmetic, asked before there is an arm.
     */
    NumericMeaning numericMeaningOf(Core e, Type answered, Denotations at) {
        if (e instanceof Core.Read read) {
            return at.numericOf(read.binding());
        }
        // The table, of a call, whatever the row says it computes. Asked through `asOperator`
        // instead, this read the rows that are operators and no others — so a row the library was
        // answered for was a row this could not see, which is the silence the table exists to
        // remove. `asOperator` still writes a call as the operator it stands for, which is what
        // names the value; what it computes is answered here.
        NumericResult<DeclaredArgument> result = DischargeRules.numericResult(operationOf(e));
        if (result != null && answersIn(result, answered)) {
            return computedBy(result, argsOf(e), answered);
        }
        if (e instanceof Core.Binary b && b.op().answersANumber()) {
            return theOneOf(new NumericMeaning.Operator(b.op(), b.left(), b.right()), b.type());
        }
        return null;
    }

    /** Whether {@code result} says the operation answers a value of {@code answered}: its own, or
     * the one the case carrying that type holds. */
    private static boolean answersIn(NumericResult<DeclaredArgument> result, Type answered) {
        return result.at() instanceof NumericResult.Answered.Directly
                || (result.at() instanceof NumericResult.Answered.InTheCaseCarrying(Type carried)
                        && carried.equals(answered));
    }

    /**
     * The number {@code result} says a call handing over {@code args} computes, where it answers it
     * as a value of {@code answered}.
     *
     * <p>The one place a row is turned into a meaning. Which arithmetic a row states does not depend
     * on where the operation answers it — a row is a pair of those two answers and every pair of
     * them is writable — so a reader that made the meaning itself would be a reader that had to be
     * told about the next pair. Both the call read where it stands and the arm that opens a case
     * come through here, which is what keeps one value from being two meanings depending on which
     * of the two reached it.
     */
    NumericMeaning computedBy(NumericResult<DeclaredArgument> result, List<Core> args,
                              Type answered) {
        return theOneOf(NumericMeanings.of(result.computes(), args), answered);
    }

    /** {@code meaning}, as the arithmetic it is where the language writes that arithmetic two ways.
     * A divide of whole numbers is a truncating quotient however it was spelled, so the operator and
     * the value case of {@code Int.divide} are one meaning and one recipe; over {@code Decimal} the
     * operator rounds at a precision the run time sets, which is arithmetic of its own. */
    private NumericMeaning theOneOf(NumericMeaning meaning, Type answered) {
        if (meaning instanceof NumericMeaning.Operator(BinOp op, Core left, Core right)
                && op == BinOp.DIV && granularityOf(answered) == Granularity.DISCRETE) {
            return new NumericMeaning.TruncatingQuotient(left, right);
        }
        return meaning;
    }

    /**
     * What the value {@code meaning} computes is about, where a case of {@code scrutinee} opened it.
     *
     * <p>Named by the arithmetic where the language writes that arithmetic another way, and by the
     * case otherwise. A truncating quotient is the first: {@code a / b} is a spelling of the very
     * value the {@code Int} case of {@code Int.divide(a, b)} carries, so the two are one term and a
     * guard about either is about both — which is the whole of what naming a value says. An
     * operation whose value case carries what an operator computes is the same, whichever operator
     * it is: where the operation answers a sum as one case of a union, that case carries the very
     * value {@code a + b} is. A remainder and a quotient rounded to a scale are the other: no
     * operator writes them, so what they are is the value that case opens out of that call, and
     * naming them by the call itself would file the union and the number it carries under one key.
     */
    FactSubject subjectOpenedAs(NumericMeaning meaning, Type carried, Core scrutinee,
                                Denotations at) {
        Term key = openedKey(meaning, carried, scrutinee, at);
        return key == null ? null : FactSubject.of(key);
    }

    /** The arithmetic written as an operator, as an identity. */
    private Term written(BinOp op, Core left, Core right, Denotations at) {
        Term over = identityOf(left, at);
        Term by = identityOf(right, at);
        return over == null || by == null ? null : interned.operator(op, over, by);
    }

    /**
     * What the value one case opens is filed under, as a term.
     *
     * <p>Built with an atom of its own where the grammar runs out, as every identity is: what is
     * wanted is which value a fact is about, and a value the symbolic reader cannot read is still
     * one value. The symbolic reading of an arm's binding is the place it is, and is not this
     * ({@link PathEngine}).
     */
    private Term openedKey(NumericMeaning meaning, Type carried, Core scrutinee, Denotations at) {
        return switch (meaning) {
            case NumericMeaning.TruncatingQuotient(Core dividend, Core divisor) ->
                    written(BinOp.DIV, dividend, divisor, at);
            case NumericMeaning.Operator(BinOp op, Core left, Core right) ->
                    written(op, left, right, at);
            case NumericMeaning.TruncatingRemainder _, NumericMeaning.RoundedQuotient _ -> {
                Term of = identityOf(scrutinee, at);
                yield of == null ? null : interned.opened(of, carried);
            }
        };
    }

    /**
     * The subject the place {@code binding} is: what a fact about a binding nothing else names is
     * about.
     *
     * <p>Here because identity is built here and nowhere else. {@link Denotations} records what each
     * binding's subject is and is handed it; asked to work it out from what the binding denotes, it
     * would be a second authority on which value something is, and the two would answer differently
     * the moment a binding names a value that is not a place.
     */
    FactSubject placeSubject(BindingId binding) {
        return FactSubject.of(placeTerm(binding));
    }

    /** What the term grammar names the place {@code binding} is by: the place it is. Said here for
     * the same reason every other term is. */
    Term placeTerm(BindingId binding) {
        return interned.at(Location.of(binding));
    }

    /**
     * The place {@code path} names under {@code root} — the value itself where the name has no
     * steps.
     *
     * <p>Here because the fields of a chain are read onto a term here and nowhere else. What answers
     * by name is {@link InvariantChecker#seedFields}, and what a walk names is a term; putting the
     * one back on the other is this, so a reader of both does not spell the join itself.
     */
    FactSubject under(FactSubject root, RuleKey path) {
        return root == null ? null
                : FactSubject.of(interned.on(root.identity(), path.steps()));
    }

    /**
     * The subject of what the present optional {@code optional} holds.
     *
     * <p>An optional and what stands under it are two values, so opening one is the only place a
     * {@code match} arm names something other than the value it was given. Which value that is
     * follows from the optional's own — two arms opening one optional open one value — and it is
     * asked here for the same reason every other identity is.
     */
    FactSubject heldBy(FactSubject optional) {
        return optional == null ? null : FactSubject.of(interned.held(optional.identity()));
    }

    /**
     * The subject one evaluation of {@code e} is — the same one every time this occurrence is asked
     * about, and one no other occurrence can be given.
     *
     * <p>Kept in a table rather than made afresh, because a subject made twice is two subjects and a
     * fact filed under the first is then about neither. The table is keyed by the node, which is what
     * an occurrence is here: two writings of one call are two nodes and so two evaluations, which is
     * the answer for a value nothing may share and the safe answer for one that may.
     */
    private EvaluationId evaluationIdOf(Core e) {
        if (e == null) {
            return null;
        }
        return evaluations.computeIfAbsent(asWritten(e),
                node -> new EvaluationId(shapeOf(node), node.pos(), evaluations.size()));
    }

    /**
     * Records that {@code made} is {@code from} built again — the same evaluation, reached through a
     * tree this check rewrote rather than through the one the author wrote.
     *
     * <p>Held here because which occurrence a node is, is an identity question, and identity has one
     * authority. A reading that replaces a conditional rebuilds every node on the way to it
     * ({@code Core.mapAll} makes a new parent whenever a child changed), so the very same call
     * arrives as a different object in each reading. Left unrecorded, each reading would give it an
     * evaluation of its own, and a fact taken in one would be about nothing in the next.
     *
     * <p>A rebuild is not a second evaluation. Something that really does evaluate twice — two calls
     * written out, one call inside a fold — is two nodes and never comes through here, so the two
     * stay apart.
     */
    void rebuilt(Core made, Core from) {
        if (made != from) {
            builtFrom.put(made, from);
        }
    }

    /** The node {@code e} was built from, however many rewrites ago — and {@code e} itself where it
     * is the one that was written. */
    Core asWritten(Core e) {
        Core from = e;
        Core next;
        while ((next = builtFrom.get(from)) != null) {
            from = next;
        }
        return from;
    }

    /** What to call an evaluation in a message: the kind of expression it is. */
    private static String shapeOf(Core e) {
        return switch (e) {
            case Core.Call _ -> "an answer";
            case Core.Apply _ -> "what a function value answered";
            default -> "a value";
        };
    }

    /**
     * How a value a construction is being given is named where a clause reads it: a location names
     * itself, and a container built from one by an operation the table covers names that
     * construction. Anything else names nothing.
     *
     * <p>What is named. A location is: the seeding writes about locations, so a clause reading one
     * reads something. A container built by an operation the table covers is, since the table is a
     * rule about it. A number the term grammar names is, whatever computes it — the domain carries
     * it, and a clause reading it reads an unknown the guards may or may not have settled. A number
     * that grammar names nothing, such as what a behavior answered, is not. A value of any other kind
     * is named
     * only where a guard on this path spoke about it, since a clause reading it has otherwise nothing
     * to be read against.
     *
     * <p>What is not named is not thereby discharged. Its construction is silent, and the silence is
     * this check's flagging policy rather than a proof: the run-time check stands for the whole of
     * such an invariant. Widening it is a matter of naming more values here.
     */
    FactSubject reportableSite(Core e, Denotations at, Known k) {
        // A value written out is not a site at all. There is nothing to state of `"xyz"` that the
        // text does not already say, so there is no guard an author could add — and this is a rule
        // about what is worth reporting, not about what is known. Asked of the text rather than of
        // which arm a denotation is, since what was written is a fact about the value however it is
        // reached. Kept out of the judgment below rather than answered as "not readable": a written
        // value's key is one a guard naming a literal puts in `spoken` (`x == "xyz"` speaks of both
        // sides), so folded into the judgment it would come back readable through the second half.
        if (writtenValue(e, at) != null) {
            return null;
        }
        FactSubject subject = subjectOf(e, at);
        if (subject == null) {
            return null;
        }
        return intrinsicallyReadable(e, at) || k.speaksOf(subject) ? subject : null;
    }

    /**
     * Whether the check's own semantics make {@code e} something a clause can be read against, before
     * anything a path has said.
     *
     * <p>A place is: the seeding writes about places, whatever their type states. A computed value is
     * where the numeric domain built a form for it or a rule says how it was made. Anything else is
     * not, and stays not until a guard on the path speaks of it — which is the other half of the
     * question and is asked of {@link Known}, not here.
     *
     * <p>Asked of the expression, so a name for it answers the same. That is what makes naming an
     * expression not change what is known of it.
     */
    boolean intrinsicallyReadable(Core e, Denotations at) {
        return isAPlace(e, at)
                || (bodyKey(e, at) != null && (affineOf(e, at) != null || namedByRule(e, at)));
    }

    /**
     * The canonical key of an expression as a term, or {@code null} when nothing here can be named.
     * Two expressions with one key compute one value, which is the whole of what the fact set knows:
     * a guard and an invariant clause state the same predicate exactly when their calls key alike.
     *
     * <p>A location keys as the location it is, so which value a term is about is the binding it is
     * rooted at. A closure parameter is keyed by where it is bound rather than by its binding, so
     * {@code r -> r.a} and {@code row -> row.a} are one term while a free name inside the closure is
     * still the value it is and so is part of the term. Anything outside this grammar keys as
     * {@code null}, and the clause reading it is left opaque.
     */
    private Term termKey(Core raw, Denotations at, Map<BindingId, Term> bound, int depth,
                         Leaf leaf) {
        return naming(raw, at, bound, depth, leaf).term();
    }

    /**
     * What a walk over an expression does where the term grammar runs out.
     *
     * <p>Two readings, one walk. The symbolic domain wants to know whether it can read a value's
     * structure, and an answer of "no" is what leaves a clause over it opaque. Asking what a fact
     * about that value would be filed under is a different question with a different right answer:
     * the value is still one value, and it takes an atom equal to itself and to nothing else. Written
     * as two walks they would be two structural rules to keep agreeing, which is what a second
     * identity algebra would have been.
     */
    enum Leaf {
        /** Runs out: the shape has no term, and neither has anything built from it. */
        SYMBOLIC,
        /** Takes an atom of its own, so that what is built over it composes. */
        AN_EVALUATION
    }

    /** {@code c}'s arguments, with a size call's container peeled back to the one whose size it is.
     * Only where an identity is being built: what the symbolic reader keys a size as is its own
     * question, and answering it here would move a key nothing asked to be moved. */
    private List<Core> sizedOver(Core.PreservedCall c, Leaf leaf) {
        Core container = leaf == Leaf.AN_EVALUATION ? DischargeRules.sizeArgOf(c) : null;
        return container == null ? c.args() : List.of(DischargeRules.sizeSource(container));
    }

    /** The naming to answer with where the grammar runs out, which is nothing or the evaluation
     * itself. */
    private Naming ranOut(Core raw, Leaf leaf, Naming absent) {
        return leaf == Leaf.AN_EVALUATION
                ? new Naming.Named(interned.evaluated(evaluationIdOf(raw))) : absent;
    }

    /**
     * What {@code raw} is called, or why it is called nothing.
     *
     * <p>Exhaustive over {@link Core} and carrying no default: a shape added to the language is a
     * compile error here rather than a value the check silently has nothing to say about. Which is
     * what the shapes below were — a call this reading kept standing fell through to nothing, so a
     * construction over one was left to the run-time check while the same construction over the same
     * helper expanded into the body was reported (#722).
     *
     * <p>A value is named by what computes it, where what computes it is a function of named parts.
     * So the parts decide most of this, and what decides the rest is what is being called: a module's
     * own helper is pure, the language's own operations are, and what an injected behavior answers is
     * not. That a call is still standing here is not one of the questions — whether a helper was
     * expanded into this body is a fact about the reading, not about the value it answers.
     */
    private Naming naming(Core raw, Denotations at, Map<BindingId, Term> bound, int depth,
                          Leaf leaf) {
        Core e = asOperator(raw);
        BindingId root = rootBinding(e);
        if (root != null) {
            Term here = bound.get(root);
            return here != null ? new Naming.Named(interned.on(here, chainOf(e)))
                    : Naming.of(pathKey(e, at, leaf), Naming.Reason.A_BINDING_STANDS_FOR_NOTHING);
        }
        return switch (e) {
            case Core.Read _, Core.FieldAccess _ ->
                    Naming.of(pathKey(e, at, leaf), Naming.Reason.A_BINDING_STANDS_FOR_NOTHING);
            case Core.Int i -> new Naming.Named(interned.written(i.value()));
            case Core.Decimal d -> new Naming.Named(interned.written(d.value()));
            case Core.Str str -> new Naming.Named(interned.written(str.value()));
            case Core.Bool b -> new Naming.Named(interned.written(b.value()));
            // Named as the construction it is written as, which is the term two writings of one
            // date already shared when this arrived here as a call. The text alone would be the term
            // the string of it has, and a `Date` is not the text of one.
            case Core.Temporal t -> new Naming.Named(interned.called(
                    ValueName.Stdlib.namespace(t.kind().shown()),
                    List.of(interned.written(t.text()))));
            case Core.UnitValue u -> new Naming.Named(interned.unit(u.data()));
            case Core.Neg n -> over(List.of(n.operand()), at, bound, depth, leaf,
                    ps -> interned.negated(ps.get(0)));
            case Core.Binary b -> binary(b, at, bound, depth, leaf);
            case Core.ListLit l -> over(l.elements(), at, bound, depth, leaf, interned::list);
            case Core.Tuple t -> over(t.elements(), at, bound, depth, leaf, interned::tuple);
            case Core.TupleGet g -> over(List.of(g.tuple()), at, bound, depth, leaf,
                    ps -> interned.part(ps.get(0), g.index()));
            case Core.If iff -> over(List.of(iff.cond(), iff.then(), iff.els()), at, bound, depth, leaf,
                    ps -> interned.choice(ps.get(0), ps.get(1), ps.get(2)));
            case Core.OptionSome s -> over(List.of(s.value()), at, bound, depth, leaf,
                    ps -> interned.some(ps.get(0)));
            case Core.OptionNone none -> new Naming.Named(interned.none(none.type()));
            case Core.Block b -> {
                Map<BindingId, Term> inner = binding(bound, b.params(), depth);
                yield named(naming(b.body(), at, inner, depth + 1, leaf),
                        body -> interned.closure(b.params().size(), body));
            }
            // A binding is named by what its body is named, read inside it. What a name means is the
            // environment's answer (ADR-0106), and {@link #inside} is where that is settled — so
            // `id(lo)`, which an expansion leaves as a binding over a read of it, is the term `lo`
            // is, and a rule written through a helper is about the position the same rule written
            // out is about.
            //
            // Named as a shape of its own instead, the binding was a term nothing else equalled:
            // the very defect the environment already avoids one level down (#676), reappearing
            // where the expression is named rather than where the fact is filed. The parameter's
            // reads went through the de Bruijn map below, which is what a closure needs — a lambda's
            // parameter stands for no value — and what a binding does not.
            case Core.LetIn li -> {
                // Two environments, because they answer two questions. What a name denotes is the
                // environment's ({@link #inside}, ADR-0106), and the body is read inside it. What a
                // name is called is this walk's own, and a binder is called what it was given —
                // read under the names in scope here, so a closure's parameter keeps the index it
                // was bound at. Entered in the first alone, `x -> let y = x in y` lost `x`'s place
                // in the closure and stopped being the term `x -> x` is.
                Naming value = naming(li.value(), at, bound, depth, leaf);
                if (value instanceof Naming.Unnamed absent) {
                    yield absent;
                }
                Map<BindingId, Term> inner = new HashMap<>(bound);
                inner.put(li.binder().binding(), value.term());
                yield naming(li.body(), inside(li, at), inner, depth, leaf);
            }
            // A construction is a pure function of its fields, and a closure that builds one is what a
            // mapping usually is. The fields are held in declaration order, so two sites writing them
            // in different orders — or one of them through a spread — write one term.
            case Core.Construct nd -> over(nd.values().stream().map(Core.FieldValue::value).toList(),
                    at, bound, depth, leaf,
                    ps -> interned.built(nd.typeName(),
                            nd.values().stream().map(Core.FieldValue::field).toList(), ps));
            case Core.Match m -> {
                Map<BindingId, Term> outer = bound;
                List<Naming> answers = new ArrayList<>();
                for (Core.Case arm : m.cases()) {
                    Map<BindingId, Term> inner = arm.binder() == null ? outer
                            : binding(outer, List.of(arm.binder()), depth);
                    answers.add(naming(arm.body(), at, inner, depth + 1, leaf));
                }
                Naming scrutinee = naming(m.scrutinee(), at, bound, depth, leaf);
                yield joined(scrutinee, answers,
                        parts -> interned.matched(parts.get(0),
                                m.cases().stream().map(Core.Case::caseTypes).toList(),
                                parts.subList(1, parts.size())));
            }
            case Core.IfConstructed ic -> {
                Naming built = naming(ic.construct(), at, bound, depth, leaf);
                Map<BindingId, Term> inner = binding(bound, List.of(ic.binder()), depth);
                List<Naming> answers = new ArrayList<>();
                answers.add(naming(ic.then(), at, inner, depth + 1, leaf));
                for (Core.ElseArm arm : ic.els()) {
                    answers.add(naming(arm.body(), at, bound, depth, leaf));
                }
                yield joined(built, answers,
                        parts -> interned.attempted(parts.get(0),
                                ic.els().stream().map(arm -> arm.clause().orElse("")).toList(),
                                parts.subList(1, parts.size())));
            }
            // What the language's own operations answer, and what a module's own helper answers, are
            // functions of what they were given: the first because the language defines them, the
            // second because a helper is pure (spec §fn-rules). What an injected behavior answers is
            // neither, and a call to one is named by nothing.
            // A size is keyed over the container it is really the size of. An operation that answers
            // exactly as many as it was given does not change the number, which the discharge table
            // states as a relation between the two values (`Cardinality.SAME`) rather than as
            // something this check happens to follow — so it is an equality identity may read.
            case Core.PreservedCall c -> over(sizedOver(c, leaf), at, bound, depth, leaf,
                    ps -> interned.called(c.operation(), ps));
            case Core.Call c -> switch (c.fn()) {
                case Core.Reached reached -> switch (answersOf(reached.denotes())) {
                    case Naming.OfAName.AnswersNothing none ->
                            ranOut(raw, leaf, new Naming.Opaque(none.reason()));
                    case Naming.OfAName.Answers ignored -> over(c.args(), at, bound, depth, leaf,
                            ps -> interned.called(reached.denotes(), ps));
                };
                // A walk this compiler minted for a shape the backend lowers as a whole. The reading
                // this check is given keeps no such call, so one arriving is a pass having run over a
                // tree it was not written for rather than a value nothing can be said of.
                case Core.Emitted emitted -> ranOut(raw, leaf, unsupported(emitted.rendered()));
            };
            case Core.Apply _ -> ranOut(raw, leaf,
                    new Naming.Opaque(Naming.Reason.A_FUNCTION_VALUE_WAS_APPLIED));
            case Core.Unreachable _ -> new Naming.Opaque(Naming.Reason.NOTHING_IS_ANSWERED);
        };
    }

    /**
     * Whether {@code callee} answers a value two writings of a call to it share.
     *
     * <p>Asked of what the name was resolved to and of nothing else. A module's own helper is pure
     * and total, a value definition's body obeys a helper's rules, and the language's own operations
     * are what the language says they are — so a call to any of them answers one value wherever it is
     * written with the same arguments. Whether the reading this check is given expanded that helper
     * into the body is a fact about the reading and not about the value, which is why it is not asked
     * here: a helper that recurses is left standing and a helper that does not is expanded, and the
     * two answer the same question about the same call.
     *
     * <p>What a behavior answered is named by nothing (spec §invariant-discharge-terms). An injected
     * one could not be: its implementation is outside the language and may read the outside world, so
     * two asks are two answers. What is applied through a binding is the same — the binding may hold
     * an injected behavior, and what it holds is not a question about the name.
     */
    private Naming.OfAName answersOf(ValueName callee) {
        return switch (callee) {
            case ValueName.Behavior _ ->
                    new Naming.OfAName.AnswersNothing(Naming.Reason.A_BEHAVIOR_ANSWERED);
            case ValueName.Local _ ->
                    new Naming.OfAName.AnswersNothing(Naming.Reason.A_FUNCTION_VALUE_WAS_APPLIED);
            case ValueName.Helper _, ValueName.Stdlib _, ValueName.Builtin _, ValueName.OfType _ ->
                    new Naming.OfAName.Answers();
        };
    }

    /** A shape this has no term for, recorded where a test can read that it happened. */
    private Naming unsupported(String form) {
        List<String> watching = reading == Of.THE_DISCHARGE_TREE ? UNSUPPORTED : null;
        if (watching != null) {
            watching.add(form);
        }
        return new Naming.Unsupported(form);
    }

    /** {@code made} of what {@code first} and {@code rest} name, or the first of them that names
     * nothing. */
    private Naming joined(Naming first, List<Naming> rest,
                          java.util.function.Function<List<Term>, Term> made) {
        List<Term> terms = new ArrayList<>();
        if (first instanceof Naming.Unnamed absent) {
            return absent;
        }
        terms.add(first.term());
        for (Naming one : rest) {
            if (one instanceof Naming.Unnamed absent) {
                return absent;
            }
            terms.add(one.term());
        }
        return new Naming.Named(made.apply(terms));
    }

    /** {@code made} of what {@code naming} names, or why it names nothing. */
    private Naming named(Naming naming, java.util.function.UnaryOperator<Term> made) {
        return naming instanceof Naming.Unnamed absent ? absent
                : new Naming.Named(made.apply(naming.term()));
    }

    /**
     * What a binary is named by: what the comparison it is states, or the operator over its two
     * operands.
     *
     * <p>Recognised here, where the node is. A comparison of two values is named by what it placed
     * ({@link ComparisonClaim#canonical}), so the six ways to write one come to what they state and
     * two clauses comparing the same two values meet as one term. Handed the
     * operator instead, what a comparison states would be read a second time below the point where
     * it was already settled. What is left is an operator the interner takes as written.
     */
    private Naming binary(Core.Binary b, Denotations at, Map<BindingId, Term> bound, int depth,
                          Leaf leaf) {
        List<Core> sides = List.of(b.left(), b.right());
        ComparisonClaim placed = Comparison.of(b).map(Comparison::claim).orElse(null);
        if (placed != null) {
            return over(sides, at, bound, depth, leaf,
                    ps -> interned.comparison(placed.canonical(ps.get(0), ps.get(1))));
        }
        return over(sides, at, bound, depth, leaf,
                ps -> interned.operator(b.op(), ps.get(0), ps.get(1)));
    }

    /** {@code made} of the terms {@code parts} are, or null where any of them is named by nothing. */
    private Naming over(List<Core> parts, Denotations at, Map<BindingId, Term> bound, int depth,
                        Leaf leaf, java.util.function.Function<List<Term>, Term> made) {
        List<Term> terms = new ArrayList<>();
        for (Core part : parts) {
            Naming one = naming(part, at, bound, depth, leaf);
            if (one instanceof Naming.Unnamed absent) {
                return absent;
            }
            terms.add(one.term());
        }
        return new Naming.Named(made.apply(terms));
    }

    /** {@code bound} with each of {@code binders} keyed by where it is bound rather than by which
     * binding it is, so two expressions that differ only in what they bound are one term. */
    Map<BindingId, Term> binding(Map<BindingId, Term> bound, List<Core.Binder> binders, int depth) {
        Map<BindingId, Term> inner = new HashMap<>(bound);
        for (int i = 0; i < binders.size(); i++) {
            inner.put(binders.get(i).binding(), interned.bound(depth, i));
        }
        return inner;
    }

    /** The binding at the head of a {@code x}/{@code x.a.b} chain, or {@code null} if {@code e} is not
     * one. */
    static BindingId rootBinding(Core e) {
        return switch (e) {
            case Core.Read r -> r.binding();
            case Core.FieldAccess fa -> rootBinding(fa.target());
            default -> null;
        };
    }

    /** The fields read along a {@code x.a.b} chain, from the head down. */
    static List<String> chainOf(Core e) {
        List<String> out = new ArrayList<>();
        chainInto(e, out);
        return out;
    }

    private static void chainInto(Core e, List<String> out) {
        if (e instanceof Core.FieldAccess fa) {
            chainInto(fa.target(), out);
            out.add(fa.field());
        }
    }

    /**
     * The key of a chain rooted at a binding: what the binding at its head is about, with the fields
     * read from it.
     *
     * <p>Which value the head is, is the walk's answer and not this one's. A binding is entered with
     * the subject facts about it are filed under ({@link Denotations.Means}), and reading it back is
     * all that happens here — where working it out again from what the binding denotes was a second
     * authority on identity, and one that cannot answer for a binding that names a value the grammar
     * has no term for. That is what a {@code match} arm needs: the value it opens is the one the
     * scrutinee already is, and a reading that derives identity from how the binding was introduced
     * has no way to say so.
     *
     * <p>Asked of the identity reading only. What the term grammar can name without taking an atom
     * is a different question with a different answer — a call to a behavior is named by nothing —
     * and {@link Leaf#SYMBOLIC} is where it is asked, so that reading still works from what the
     * binding denotes.
     *
     * <p>A newtype's {@code .value} is the same value as the newtype, which is {@link Location}'s
     * rule and is read here of any subject, so a value keyed one way through a binding and the other
     * way through a field is one value.
     */
    Term pathKey(Core e, Denotations at, Leaf leaf) {
        if (leaf == Leaf.AN_EVALUATION) {
            return subjectKey(e, at);
        }
        Location located = locationOf(e, at);
        return located != null ? interned.at(located) : keyOfNowhere(e, at);
    }

    /**
     * The subject of a chain: what its head was entered as, with the fields read off it.
     *
     * <p>A head nothing entered is a value this reading knows nothing about, and it takes an atom of
     * its own — the same answer a shape outside the grammar gets, for the same reason.
     *
     * <p>Always answers. Every value has an identity, whether or not anything can be said about it,
     * so there is no reading here that comes back with nothing and no step that has to allow for one.
     * The reading beside this one does — what the term grammar can name runs out, and says so with
     * {@code null} — and the two are not the same question.
     */
    private Term subjectKey(Core e, Denotations at) {
        return switch (e) {
            case Core.Read r -> {
                FactSubject subject = at.subject(r.binding());
                yield subject != null ? subject.identity()
                        : interned.evaluated(evaluationIdOf(e));
            }
            case Core.FieldAccess fa ->
                    Location.isStep(fa.target().type(), fa.field(), symbols)
                            ? interned.on(subjectKey(fa.target(), at), List.of(fa.field()))
                            : subjectKey(fa.target(), at);
            default -> interned.evaluated(evaluationIdOf(e));
        };
    }

    /**
     * The same, for a chain already found to be nowhere — so what it is read from is nowhere too.
     *
     * <p>A chain names a location exactly when the binding at its head does ({@link Location#of}
     * answers {@code x.a.b} by answering {@code x} and then reading the fields off it, and reading a
     * field off somewhere never reaches nowhere). So a chain that is nowhere is one whose every step
     * is nowhere, and the question is answered once for the whole of it rather than again at each
     * step — asked at each step it walks what is left of the chain each time, and a chain costs more
     * than the chain is long (#826).
     */
    private Term keyOfNowhere(Core e, Denotations at) {
        return switch (e) {
            // What the walk recorded the term grammar names it by, and null where it names it by
            // nothing. A chain is asked of this only once it has been found not to be a place.
            case Core.Read r -> at.termOf(r.binding());
            case Core.FieldAccess fa -> {
                if (!Location.isStep(fa.target().type(), fa.field(), symbols)) {
                    yield keyOfNowhere(fa.target(), at);
                }
                Term base = keyOfNowhere(fa.target(), at);
                yield base == null ? null : interned.on(base, List.of(fa.field()));
            }
            default -> null;
        };
    }

    /**
     * Whether {@code binding}'s name may be read as the workings of the expression it was given: what
     * that expression is arithmetic over, what rule names it, read of the name as well.
     *
     * <p>Asked here rather than left as which arm a denotation is. Two readers tested one arm of
     * one classification for it, and an arm of a sum is a poor way to ask: the arm they tested said
     * what a binding is not — not a place, not text, not nameless — so what those readers meant held
     * by falling through the others, and removing an arm silently widened what they read. Named, the
     * question is one thing to answer and one thing to get wrong.
     *
     * <p>Not the same as following what a name was given. {@link #writtenValue} does that too, and
     * does it whatever the name denotes, because what was written is a fact about the value however
     * it is reached. This is about carrying a computation across a name, which a name given text or
     * given a place does not do: the text folds and the place is the atom, and neither is arithmetic
     * to read through to.
     *
     * <p>Worked out from what is asked elsewhere: where the binding is, what names it, and what it
     * was written as. Each of those is a question of its own with an answer of its own, and this one
     * is the three of them together — where a classification holding all three at once made the
     * answer to one of them depend on which other answers were left.
     */
    boolean computesAsWhatItWasGiven(BindingId binding, Denotations at) {
        Core given = at.valueOf(binding);
        return at.locationOf(binding) == null && at.termOf(binding) != null
                && (given == null || writtenValue(given, at) == null);
    }

    /**
     * Whether {@code e} is a place: somewhere the seeding writes about, whatever value happens to be
     * there.
     *
     * <p>Asked as a question about what may be done with it, and not by taking the location and
     * reading nothing off it but whether there was one. The two came apart when an arm stopped making
     * a value of its own: what a {@code match} opens is a place, so a clause may be read against it
     * and the seeding writes about it, while which value it is, is the one the scrutinee already was.
     * A reader wanting the second asks {@link #subjectOf}, and a reader holding a {@link Location} to
     * decide the first is one step away from deciding the second from it too.
     */
    boolean isAPlace(Core e, Denotations at) {
        return locationOf(e, at) != null;
    }

    /** The location {@code e} is, or {@code null} where it is a computed value rather than a place.
     *
     * <p>For the two readings that want the location itself: what a binding denotes, and what the
     * term grammar names a chain by. A reader that only wants to know whether there is one asks
     * {@link #isAPlace}. */
    Location locationOf(Core e, Denotations at) {
        return Location.of(e, symbols, at::locationOf);
    }

    /**
     * What {@code e} is written as, where it is a written value or a name given one — and
     * {@code null} where it is computed from anything.
     *
     * <p>Found by following what a name was given, however many names deep, and asked of what the
     * following ends at. A name is what it was given whatever kind of thing that is, so this is one
     * rule and not one per kind of binding: a {@code match} arm opening a value written into the
     * source opens that written value, for the same reason a {@code let} given it does.
     *
     * <p>{@code seen} ends a chain that has no end. Nothing the walk records is one — a binding is
     * entered under what stood before it — but a reading that follows what it is handed says what it
     * does with a chain it was handed, rather than leaving it to whoever built it.
     */
    Core writtenValue(Core e, Denotations at) {
        return writtenValue(e, at, new HashSet<>());
    }

    /** Where a test counts the steps this takes following what a name was given, and null everywhere
     * else. What a chain of names costs is not something an answer says. */
    static long[] FOLLOWED;

    /**
     * What the end of the chain from {@code binding} was written as, and the value it was given when
     * that was worked out.
     *
     * <p>Kept because the answer is about the binding and not about the ask. A reading of arithmetic
     * follows a name, then follows what that name was given, and so on down, so a chain of names was
     * walked once per link and cost the square of its length. What is remembered is the following and
     * not the fact — a binding still stands for what it was given, and this says only that the
     * following of it has been done.
     *
     * <p>Held against the environment it was worked out in, and thrown away when that is not the one
     * being asked about. What a name was given is the walk's answer, and the walk answers differently
     * as it goes: a name whose chain reaches a binding the walk had not entered yet reaches nothing,
     * and the same name reaches text once it has. Remembered across the two, the earlier answer would
     * stand where the later one is owed — and it did, over three constructions, until the environment
     * became part of what is remembered.
     */
    private record Followed(Denotations at, Core given, Core written) {}

    private final Map<BindingId, Followed> followed = new HashMap<>();

    private Core writtenValue(Core e, Denotations at, Set<BindingId> seen) {
        long[] counting = FOLLOWED;
        if (counting != null) {
            counting[0]++;
        }
        if (e instanceof Core.Read r) {
            Core given = at.valueOf(r.binding());
            if (given == null || given == e) {
                return null;
            }
            Followed had = followed.get(r.binding());
            if (had != null && had.at() == at && had.given() == given) {
                return had.written();
            }
            if (!seen.add(r.binding())) {
                return null;
            }
            Core written = writtenValue(given, at, seen);
            followed.put(r.binding(), new Followed(at, given, written));
            return written;
        }
        return isWritten(e) ? e : null;
    }

    /**
     * Whether {@code e} is a value written out rather than computed from anything: a literal, and a
     * list or a construction whose every part is one. A table written into the source is this — every
     * row of it is there to read — and there is no guard an author could add about it, which is what
     * naming it at a construction site would ask for.
     */
    static boolean isWritten(Core e) {
        return isWritten(e, Set.of());
    }

    /** The same, where {@code written} names the bindings an expansion introduced for values that
     * were themselves written. A helper called on written arguments is a written value: what it
     * expands to binds each argument and reads it back, which is the source's own text moved. */
    static boolean isWritten(Core e, Set<BindingId> written) {
        return switch (e) {
            // A temporal is one of the literals the language has (spec
            // §a-temporal-value-is-written-as-a-literal), so it is written wherever it stands. It
            // was carried here as a call and answered `false` in this switch's default while
            // `asWrittenValue` was writing it back out — one value, two answers about whether the
            // source holds it.
            case Core.Int _, Core.Decimal _, Core.Str _, Core.Bool _, Core.Temporal _,
                 Core.UnitValue _ -> true;
            case Core.Neg n -> isWritten(n.operand(), written);
            case Core.Read r -> written.contains(r.binding());
            case Core.ListLit list -> list.elements().stream().allMatch(x -> isWritten(x, written));
            case Core.Tuple t -> t.elements().stream().allMatch(x -> isWritten(x, written));
            case Core.OptionSome s -> isWritten(s.value(), written);
            case Core.Construct nd ->
                    nd.values().stream().allMatch(v -> isWritten(v.value(), written));
            case Core.LetIn li -> {
                if (!isWritten(li.value(), written)) {
                    yield false;
                }
                Set<BindingId> inner = new HashSet<>(written);
                inner.add(li.binder().binding());
                yield isWritten(li.body(), inner);
            }
            default -> false;
        };
    }

    /** Whether {@code e} is a container built by an operation the preservation table covers, over an
     * argument that is itself named. */
    boolean builtByRule(Core e, Denotations at) {
        if (!(e instanceof Core.PreservedCall call)) {
            return false;
        }
        DischargeRules.Source built = DischargeRules.builtFrom(call);
        return built != null && namedByRule(built.container(), at);
    }

    /**
     * Whether {@code e} names something without a guard having to have spoken about it: it is read all
     * the way down. A location is; so is a value composed of ones by a shape the term grammar reads —
     * a concatenation, a conditional, arithmetic, a container the preservation table covers. A call
     * the check has no rule about is not, and neither is anything built from one: nothing follows from
     * naming it, and its construction is left to the run-time check.
     */
    boolean namedByRule(Core e, Denotations at) {
        if (isAPlace(e, at)) {
            return true;
        }
        if (e instanceof Core.Read r) {
            // The name is the expression it was given, so the question is asked of that expression.
            // It was a flag recorded when the binding was entered, which is a second record of what
            // the initializer already answers.
            Core given = at.valueOf(r.binding());
            return computesAsWhatItWasGiven(r.binding(), at) && given != null && given != e
                    && (affineOf(given, at) != null || namedByRule(given, at));
        }
        Core read = asOperator(e);
        if (read instanceof Core.PreservedCall call
                && !DischargeRules.readsAsATerm(call.operation())) {
            return builtByRule(read, at);
        }
        // A call the representation did not keep standing answers something this check has no rule
        // about, whatever the operation behind it was.
        if (read instanceof Core.Call || read instanceof Core.Apply) {
            return false;
        }
        // Arithmetic is the numeric domain's to name. Where the domain builds a form, that form is
        // what a clause is read against; where it declines to — a variable product, an integer divide
        // — declining is a decision about what this check reasons over, and reporting a construction
        // it has decided not to reason over would be reporting the decision as the author's to fix.
        if (read instanceof Core.Binary bin && bin.op().answersANumber()) {
            return false;
        }
        // A conditional is one of its branches and which one is not decided here. Saying only that it
        // is that term reports every construction over one, including where both branches satisfy the
        // clause. Reading it is checking the construction on each branch under its own condition,
        // which this walk does not do, so it is not named until it does.
        if (read instanceof Core.If) {
            return false;
        }
        boolean[] all = {true};
        // A closure is not part of what names the value: the tables are rules about how many elements
        // there are and where they came from, and how each one is made has no bearing on either.
        Core.forEachChild(read, child -> all[0] = all[0]
                && (child instanceof Core.Block || namedByRule(child, at)));
        return all[0];
    }

    // --- what the two representations share ----------------------------------------------------

    /**
     * {@code e} as the value it is written as, for the one reader that is defined over written values:
     * the constant folder. A value written out is the same value in either representation, so this is
     * a rendering and not a second tree — everything computed answers with nothing, and the fold then
     * has nothing to fold.
     *
     * <p><b>Read where it stands.</b> A helper call is expanded as a binding over the helper's body
     * (spec §invariant-discharge-representation), so a value an author wrote at a call stands under
     * one — and a rule stating its value through a helper said nothing where the same rule written
     * out said what it says. A binding is the value its body is, with the binder standing for what
     * it was given.
     *
     * <p>Which is asked of two things, because a binding reaches a reading two ways. One is still in
     * the tree, where the value is in the node beside the name. The other was consumed as the shape
     * of the clause ({@link ClauseExpr.Scoped}) and is left in {@code at}, which is the reading's
     * own environment and answers what a name was given. Neither is a second account of what a name
     * means: what a name <em>denotes</em> is {@link #inside}'s, and this is the written value's own
     * question.
     */
    static Hir.Expr asWrittenValue(Core e, Denotations at) {
        return asWrittenValue(e, at, Map.of());
    }

    /** {@code given} with {@code li}'s binder standing for what it was given. */
    private static Map<BindingId, Core> withGiven(Map<BindingId, Core> given, Core.LetIn li) {
        Map<BindingId, Core> out = new HashMap<>(given);
        out.put(li.binder().binding(), li.value());
        return out;
    }

    private static Hir.Expr asWrittenValue(Core e, Denotations at, Map<BindingId, Core> given) {
        // Written over nothing, every one of them. A value rendered back out of what was computed is
        // the value and not the characters any of it came from: the fold has already been over them,
        // and what it arrived at may be a number no line of the file spells.
        return switch (e) {
            // A binding is the value its body is, with the binder standing for what it was given.
            // A binding is the value its body is, with the binder standing for what it was given.
            case Core.LetIn li -> asWrittenValue(li.body(), at, withGiven(given, li));
            // And a name whose binding the clause's shape already consumed stands for what the
            // environment says it was given — which is the same rule, asked where the binding is no
            // longer in the tree ({@link ClauseExpr.Scoped}).
            case Core.Read r when given.containsKey(r.binding()) ->
                    asWrittenValue(given.get(r.binding()), at, given);
            case Core.Read r when at.valueOf(r.binding()) != null ->
                    asWrittenValue(at.valueOf(r.binding()), at, given);
            case Core.Int i -> new Hir.IntLit(i.value(), i.pos(), null);
            case Core.Decimal d -> new Hir.DecimalLit(d.value(), d.pos(), null);
            case Core.Str s -> new Hir.StringLit(s.value(), s.pos(), null);
            case Core.Bool b -> new Hir.BoolLit(b.value(), b.pos(), null);
            case Core.Neg n -> {
                Hir.Expr operand = asWrittenValue(n.operand(), at, given);
                yield operand == null ? null : new Hir.Neg(operand, n.pos(), null);
            }
            case Core.Binary b -> {
                Hir.Expr left = asWrittenValue(b.left(), at, given);
                Hir.Expr right = asWrittenValue(b.right(), at, given);
                yield left == null || right == null ? null
                        : new Hir.Binary(b.op(), left, right, b.origin(), b.pos(), null);
            }
            case Core.PreservedCall call -> {
                List<Hir.Expr> args = written(call.args(), at, given);
                yield args == null ? null
                        : Hir.Apply.synthetic(call.operation().name(), reachOf(call.operation()), args,
                                call.pos(), null);
            }
            // A temporal is written as a literal with its text spelled out (spec
            // §a-temporal-value-is-written-as-a-literal). Rendered here for the same reason every
            // other written form is: it is a value the author wrote, and a reader handed nothing for
            // it reads the rule around it as a rule about something unknown. Written back as the
            // construction it was written as, which is the form a report and `ConstEval` read.
            //
            // This used to be held to the calls answering a temporal — which took any behavior
            // answering a `Date` over written arguments for a date the source spells out, and let a
            // line be drawn where the compiler knows no value. What is written is the node now, and
            // nothing here decides it.
            case Core.Temporal t -> {
                ValueName.Stdlib.Namespace namespace =
                        ValueName.Stdlib.namespace(t.kind().shown());
                yield Hir.Apply.synthetic(namespace.qualified(),
                        new ReachName.TheNamespace(namespace),
                        List.of(new Hir.StringLit(t.text(), t.pos(), null)), t.pos(), null);
            }
            // A case of an enumeration is written by naming it, so the value is the name.
            case Core.UnitValue unit -> {
                ValueName.OfType named = new ValueName.OfType(unit.data().name(), unit.data());
                // Read back out of a checked tree, where no reference of the source is left to
                // carry: what this stands for is the case the value is.
                yield Hir.Var.respelled(unit.data().name(), new ReachName.InScope(named), null,
                        unit.pos(), null);
            }
            case null, default -> null;
        };
    }

    /** Every argument as it was written, or null where any of them was computed. */
    private static List<Hir.Expr> written(List<Core> args, Denotations at,
                                          Map<BindingId, Core> given) {
        List<Hir.Expr> out = new ArrayList<>();
        for (Core arg : args) {
            Hir.Expr each = asWrittenValue(arg, at, given);
            if (each == null) {
                return null;
            }
            out.add(each);
        }
        return out;
    }

    // --- helpers -------------------------------------------------------------------------------


    /** A read of {@code binder}, as the expression naming the value it holds. */
    static Core.Read read(Core.Binder binder, Type type, SourcePos pos) {
        return new Core.Read(binder.name(), binder.binding(), type, pos);
    }

    /** The block {@code e} is, following a name given one: a lambda handed to an operation the
     * representation keeps standing is never applied here, so it is bound to a name like any other
     * value and reaches the call as that name. */
    static Core.Block blockOf(Core e, Denotations at) {
        if (e instanceof Core.Block b) {
            return b;
        }
        return e instanceof Core.Read r && at.valueOf(r.binding()) instanceof Core.Block b ? b : null;
    }


    /** The type of {@code field} read from {@code owner}, or null where nothing of that name is
     * readable there. What a value has of its own is the one reading's answer, so a field every case
     * of a sum spreads is read off the sum here exactly as it is where a body reads one. */
    Type fieldType(Type owner, String field) {
        return ValueReading.of(owner, symbols).named().get(field);
    }

    /** What a container hands its closure: a list's or set's element, a map's value (the key is the
     * other closure parameter and is not the one the table credits), an option's payload, and a
     * function's first parameter where what is held is the closure itself. */
    static Type elementType(Type t) {
        return Type.elementOf(t);
    }

    /**
     * The number the affine domain can carry a value of {@code t} as, or null where it can carry
     * none.
     *
     * <p>This is the analyser's own capability and is named for the domain that has it. Two other
     * questions read like it and are not it: whether arithmetic is closed over the type, which the
     * language answers only for a newtype directly over a number ({@link
     * TypeOps#directNumericNewtypeBase}, spec §newtype-arithmetic), and whether the type is an
     * ordered number, which reaches the recursive base ({@link TypeOps#base}, ADR-0047). Answering
     * this one with the first is how a comparison the language accepts stopped reaching the
     * reasoning.
     *
     * <p>The carrier and not a yes: a caller that has to know whether it is stepping through whole
     * numbers or dense ones would otherwise classify the type a second time, and two classifications
     * of one type are two chances to disagree.
     *
     * <p>Every number the language calls one, today: the domain holds a scalar, and a value the
     * language compares as a number is one it can hold. The two are answered by one call for that
     * reason and not because they are one question — an equality reaches values no domain carries,
     * and a type being comparable is no promise that an affine domain can hold it. Where those come
     * apart this stops delegating; until then the delegation is what says why the answers agree.
     *
     * <p>Remembered by the type it names — asked at every leaf of every affine walk, and answering it
     * walks the declaration's fields.
     */
    Type affineScalarBase(Type t) {
        if (t == Type.INT || t == Type.DECIMAL) {
            return t;
        }
        if (!(t instanceof Type.Ref ref)) {
            return TypeOps.numericBase(t, symbols);
        }
        return affineScalarBases.computeIfAbsent(ref.name(),
                _ -> java.util.Optional.ofNullable(TypeOps.numericBase(t, symbols)))
                .orElse(null);
    }

    /** How a preserved call's operation is reached: it is the library's, named under the alias the
     * library publishes it under. A call this representation kept standing applies an operation —
     * the namespace applied is a construction, and is written back as one. */
    private static ReachName.Declaration reachOf(ValueName operation) {
        return new ReachName.OfLibrary((ValueName.Stdlib.Operation) operation);
    }

}
