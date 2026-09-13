package souther.compiler.check;

import souther.compiler.types.BinOp;
import souther.compiler.core.Core;
import souther.compiler.numeric.Count;
import souther.compiler.numeric.LinearForm;
import souther.compiler.numeric.Place;
import souther.compiler.types.BindingId;
import souther.compiler.types.Type;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Reading an expression as {@code const + Σ coef·atom}, over whatever a caller counts as an atom.
 *
 * <p>One walk over one grammar. The check reads a clause this way to discharge it, and the measure
 * reads a rule this way to find the line it draws; the two ask about different atoms — the check
 * names a subject of its own, the measure names a position of a behavior's input — and they are the
 * same arithmetic over the same tree. Written twice, the second copy read fewer shapes than the
 * first, and a rule the check enforced was one the measure reported as unread.
 *
 * <p><b>The grammar and the walk belong here.</b> Which nodes compose — a literal, a negation,
 * {@code +}, {@code -}, a scalar multiply, a binding, a construction of a newtype, and an
 * elimination standing against the introduction that wrote what it reads — is a fact about the
 * language. A caller supplies only the answers that depend on its
 * environment: what an otherwise-uncomposed value is called, what environment lies inside a binding,
 * and which source value a name denotes transparently.
 *
 * <p><b>A caller answers with a {@link Core} and never with a {@link LinearForm}.</b> That is what
 * keeps the environment's answers from becoming a second account of the arithmetic. This walk once
 * took a leaf rule that could answer a binder's reads with the form its value had — an account of
 * arithmetic standing in for an account of meaning, weaker than the environment's by exactly the
 * values the arithmetic cannot read, and inside a reduction's step the only account there was
 * (ADR-0106). {@link Reading#readThrough} puts back a way to reach what a name was given, and its
 * answer is an expression: a caller can say which value a name denotes and cannot say what the
 * arithmetic of it comes to, so reading that expression is this walk's and no one else's
 * (ADR-0111).
 *
 * <p>Two things are the caller's throughout, and every reading and every walk below is written over
 * them: {@code A} is what the caller calls an atom, and {@code E} is what it carries as it goes
 * inside a binding. Neither is declared here — this class holds no state — so each of
 * {@link Reading} and the walks says them for itself.
 */
public final class AffineForms {

    /**
     * What a name was given, and what to read it in.
     *
     * <p>The environment comes with the value because the two belong together: a value stands for
     * the name in the environment the binding was made in, which is not always the one the name was
     * read in. Where a caller cannot tell the two apart it hands back the environment it was given
     * and says why — carried in the type either way, so the day they come apart there is one place
     * that answers for it.
     */
    public record ReadThrough<E>(Core value, E at) {}

    /**
     * The answers this walk asks its caller for: what depends on the caller's environment, and
     * nothing else.
     */
    public interface Reading<A, E> {

        /** The symbols the expression being read was resolved against. A call folds to a number
         *  when it computes one, which is a fact about the library, so the walk asks the caller
         *  rather than reaching for a library of its own. */
        Symbols symbols();

        /** What the declarations the expression was written against say. Asked beside the symbols
         *  because what a carrier is depends on which of them is a sum and what its cases are, and
         *  that is the declaration's own answer rather than the tree it was written in. */
        PublishedDeclarations published();

        /** Which form each of those declarations was written in, for the carrier's question about
         *  whether one is a sum. */
        DeclarationKinds kinds();

        /** What each of them that wears one value wraps, for the walk that takes the names off a
         *  position before the carrier is read. */
        NewtypeInners inners();

        /** {@code e} as a form, where nothing here composes one: an atom, a value read through, or
         *  null where the caller can say nothing about it. */
        LinearForm<A> leafOf(Core e, E at);

        /** What {@code li}'s body is read in. The one place a binding is entered, so that what a
         *  name means is settled once and no reader interprets a binder for itself. */
        E inside(Core.LetIn li, E at);


        /**
         * The value {@code read}'s name denotes, where the name and that value are one value — or
         * null where the name stands for something of its own and reading through it would say of
         * one value what was written about another.
         *
         * <p>Answered from the environment, which is what knows. A name is a place the caller's
         * seeding wrote about, or a position of a behavior's input, or an element an operation
         * handed out, or the expression it was given, and which of those it is decides whether
         * anything may stand where it stands. What is answered is the expression alone: what its
         * arithmetic comes to is read here, once, so no caller keeps a second account of it.
         */
        ReadThrough<E> readThrough(Core.Read read, E at);

        /**
         * Every value {@code read}'s name can stand for, where the environment has all of them — or
         * null where it has not.
         *
         * <p>Beside {@link #readThrough} and answering the other half of one question. That one says
         * the name and one value are one value; this says the name is one of these and names them
         * all. A name has at most one of the two answers, and which it is is the environment's to
         * say: a reader that took a plurality for a denotation would state of one value what the
         * model says of several, and one that took a denotation for a plurality would be asking what
         * a single value agrees with.
         *
         * <p><b>Exhaustive, or null.</b> What this walk does with the answer is keep what every
         * member comes to, and a member left out makes that a statement about a value the name can
         * take with nothing said. So an environment that can write out some of them answers none.
         */
        java.util.List<ReadThrough<E>> alternativesOf(Core.Read read, E at);

        /**
         * Whether a field access is a newtype's value read off something that is not a place.
         *
         * <p>What it wraps is what it is, so such a read is the target itself. Whether the target is
         * a place is asked of what it denotes and not of how it is spelled: a name given a computed
         * value is no more a place than the call it was given, and asked by the spelling it came out
         * one — so a guard over the name settled nothing about a construction over the value.
         *
         * <p><b>The second proof a projection has, and asked second.</b> Where the value read from
         * was written down, this walk takes the field off the construction that gave it and never
         * asks here. What is left for this is a projection with no construction in sight — a name a
         * call was given — which is a different piece of evidence and not a weaker copy of the
         * first. Asked first, it would answer a projection the grammar reads, and the grammar's
         * rule could be taken away without anything saying so.
         */
        boolean readsThrough(Core.FieldAccess fa, E at);
    }

    /**
     * Whether two readings came to the same form.
     *
     * <p>On the numbers and not on the records' own equality. A form is a constant and a coefficient
     * apiece, and {@code 0.10} and {@code 0.1} are one number — two readings differing in nothing
     * but a scale are not two answers, and compared by {@code equals} they would be.
     *
     * <p><b>Equal and never near.</b> What this decides is whether several values state one thing,
     * which is either so or not: a rule that answered with whatever two forms have in common would
     * be reading a weaker rule than the model states and reporting it as the model's. So there is no
     * order here for a caller to relax into, and a form that is not this one is no answer at all.
     */
    public static <A> boolean sameForm(LinearForm<A> a, LinearForm<A> b) {
        if (a.constant().compareTo(b.constant()) != 0
                || !a.coefs().keySet().equals(b.coefs().keySet())) {
            return false;
        }
        return a.coefs().entrySet().stream()
                .allMatch(one -> one.getValue().compareTo(b.coefs().get(one.getKey())) == 0);
    }

    /**
     * {@code raw} as an affine form, or null where this composes none and the caller's leaf rule
     * names nothing either.
     *
     * <p>A node this has a rule for and cannot compose is read as a leaf as well. Reading the
     * structure of a value and naming the value are two questions: a variable product is outside the
     * fragment, and it is still one value, so what a rule states of it is still about the thing the
     * clause reads. Answering the first question with {@code null} and never asking the second is
     * what made {@code a * b} name nothing where it is written and something where it is bound —
     * which is a name changing what can be said of an expression.
     */
    public static <A, E> LinearForm<A> of(Core raw, E at, Reading<A, E> reading) {
        return outcome(raw, at, reading) instanceof Outcome.Composed<A, E> composed
                ? composed.form() : null;
    }

    /**
     * What reading {@code raw} as a form came to, which is a form or the node that stopped it.
     *
     * <p>The node, because this walk is the only thing that knows. It met the expression that has no
     * rule here and gave up there; a reader handed nothing back had to work out afterwards what the
     * difficulty had been, and the only material it had was the shape of the whole side — so a rule
     * over an operation this reads perfectly well was blamed for a form it could not read beside it.
     *
     * <p>The deepest one, and the first met where a form has two parts. What a reader is sent to fix
     * is the expression nothing composed, and naming the sum above it names an expression that would
     * compose the moment its part did.
     */
    public static <A, E> Outcome<A, E> outcome(Core raw, E at, Reading<A, E> reading) {
        return of(raw, at, reading, new java.util.HashSet<>());
    }

    /**
     * A form, or where the reading of one stopped.
     *
     * <p>Two cases and not a form that may be missing. Whether an expression is arithmetic this
     * reads is an answer about the expression; handed back as an absence it became an answer about
     * this walk, which whoever asked then had to interpret.
     */
    public sealed interface Outcome<A, E> {

        /** The arithmetic, over whatever the caller calls an atom. */
        record Composed<A, E>(LinearForm<A> form) implements Outcome<A, E> {

            public Composed {
                java.util.Objects.requireNonNull(form, "a reading that composed one has a form");
            }
        }

        /**
         * The expression this has no rule for and the caller could not name either, and what it was
         * being read in.
         *
         * <p>The environment travels with the expression, for the reason {@link ReadThrough} says:
         * a value stands for a name in the environment the binding was made in, which is not always
         * the one the name was read in. A caller handed the expression alone read it again in
         * whatever it happened to hold, which is that reading being done twice and the second one
         * free to disagree — and the day the two environments come apart, silently.
         */
        record StoppedAt<A, E>(Core node, E at) implements Outcome<A, E> {

            public StoppedAt {
                java.util.Objects.requireNonNull(node, "a reading that stopped stopped somewhere");
                java.util.Objects.requireNonNull(at, "and was reading it in something");
            }
        }
    }

    /**
     * The same, through the names already being read through on the way here.
     *
     * <p>Three stages, in this order. What the language composes is read first; then what a name
     * denotes, which the environment answers and this reads; then what the caller calls the value.
     * The middle one is not composition — whether a name may be read through is the environment's
     * answer and not a rule of the grammar — and it is not a leaf either, since a leaf is what is
     * left when nothing can be read. Folded into either neighbour, the boundary between what the
     * language says and what a caller says stops being one a reader can see.
     */
    private static <A, E> Outcome<A, E> of(Core raw, E at, Reading<A, E> reading,
                                           java.util.Set<BindingId> following) {
        Core e = Terms.asOperator(raw);
        if (e instanceof Core.PreservedCall || e instanceof Core.Call) {
            // A call that folds is the number it folds to. `String.length("1A")` is 2, and a clause
            // about it is decided rather than owed — the run-time check is not what should answer a
            // question the compiler has already computed.
            BigDecimal folded = Terms.constantNumber(e, reading.symbols());
            if (folded != null) {
                return new Outcome.Composed<>(LinearForm.constant(folded));
            }
        }
        // Where the reading stopped inside what this walk does compose, kept while the questions
        // below are still asked. A name over an expression nothing reads is still a name the caller
        // may have an atom for, and taking the stop as the answer here would put the leaf question
        // out of reach — which is a rule about what a name may stand for, not about arithmetic.
        Stop<A, E> stopped = new Stop<>();
        LinearForm<A> composed = composed(e, at, reading, following, stopped);
        if (composed != null) {
            return new Outcome.Composed<>(composed);
        }
        Outcome<A, E> denoted = read(e, at, reading, following);
        if (denoted instanceof Outcome.Composed<A, E> composedName) {
            return composedName;
        }
        // A name whose value this could not read is still a name the caller may have an atom for,
        // so the leaf question is asked either way. Where it has none, what stopped the reading is
        // what was found inside the name rather than the name — that is the expression with no rule
        // here, and the one an author would have to change.
        LinearForm<A> leaf = reading.leafOf(e, at);
        if (leaf != null) {
            return new Outcome.Composed<>(leaf);
        }
        // Nothing named it, so this is a stop — reported at the most particular expression that has
        // no rule here. A sum whose left term nothing reads is not what an author would change.
        if (stopped.at != null) {
            return stopped.at;
        }
        return denoted != null ? denoted : new Outcome.StoppedAt<>(e, at);
    }

    /** The first stop met inside what this walk composes, kept while the questions after it are
     *  still asked. */
    private static final class Stop<A, E> {
        private Outcome.StoppedAt<A, E> at;
    }

    /** The form {@code e} came to, or null where the reading stopped inside it. For the parts of a
     *  composition, whose own stop is the whole one. */
    private static <A, E> LinearForm<A> formOf(Core e, E at, Reading<A, E> reading,
                                               java.util.Set<BindingId> following,
                                               Stop<A, E> stopped) {
        Outcome<A, E> read = of(e, at, reading, following);
        if (read instanceof Outcome.StoppedAt<A, E> here) {
            if (stopped.at == null) {
                stopped.at = here;
            }
            return null;
        }
        return ((Outcome.Composed<A, E>) read).form();
    }

    /**
     * The form of what a name denotes, or null where it denotes nothing this may read.
     *
     * <p>A name is read through once on the way to a form. A binding holds one value, so a chain of
     * them ends on its own where each was made inside the last; where a caller's environment reaches
     * further than that — over a whole body rather than down a path — nothing about a binding says
     * so, and this is what says the walk stops rather than a comment claiming it cannot go round.
     * Lifted again once the name is behind the walk, so a form adding one name to itself still reads
     * both of them.
     *
     * <p>And a name the reading names every value of is read as what those values agree on. The same
     * step as the one above with the count changed: one value is what the name comes to, and several
     * come to whatever all of them come to.
     *
     * <p>Where a member stopped, that is where the reading stopped — as it is for the one value a
     * name denotes, since a member of a written list is an expression an author wrote and is the one
     * they would have to change. Where every member was read and they came to different forms,
     * nothing stopped and this answers nothing, and the stop is reported at the name.
     */
    private static <A, E> Outcome<A, E> read(Core e, E at, Reading<A, E> reading,
                                             java.util.Set<BindingId> following) {
        if (!(e instanceof Core.Read r)) {
            return null;
        }
        ReadThrough<E> through = reading.readThrough(r, at);
        if (through != null) {
            if (through.value() == e || !following.add(r.binding())) {
                return null;
            }
            Outcome<A, E> form = of(through.value(), through.at(), reading, following);
            following.remove(r.binding());
            return form;
        }
        java.util.List<ReadThrough<E>> alternatives = reading.alternativesOf(r, at);
        if (alternatives == null || alternatives.isEmpty() || !following.add(r.binding())) {
            return null;
        }
        Stop<A, E> stopped = new Stop<>();
        LinearForm<A> agreed = commonForm(membersOf(alternatives, reading), following, stopped);
        following.remove(r.binding());
        if (agreed != null) {
            return new Outcome.Composed<>(agreed);
        }
        return stopped.at;
    }

    /**
     * An occurrence resolved as far as one value can be followed, what to read it in, and what to
     * read it with.
     *
     * <p>The environment travels with the value for the reason {@link ReadThrough} gives: what a
     * name was given is written in the environment the binding was made in, and a projection out of
     * it is read in that one rather than in the one the projection was written in.
     *
     * <p>And the reading travels with it for the same kind of reason. A value reached by taking one
     * of several is read with less than the value the walk started from: the plurality it came out
     * of is the one this walk reads, and a second one inside it is not read at all
     * ({@link #membersOf}). Left behind, the restriction would hold over the step that produced the
     * members and be gone by the time their parts were read — which is the whole of what it is for.
     */
    private record Standing<A, E>(Core value, E at, Reading<A, E> reading) {}

    /**
     * The values a name stands for, each to be read without a plurality of its own.
     *
     * <p><b>One plurality per rule, said here and nowhere else.</b> What this walk answers about a
     * position several values stand at is what all of them support, and reading a member that is
     * itself several means asking that of every pairing: two members whose members have two each is
     * four readings, and a rule written down a chain of them is two to the power of its depth. That
     * is a capability with a cost, and it is not the one this rule is: a container written out in
     * the source is what a model states its numbers in.
     *
     * <p>Said as what the members are read with rather than as a count or a depth. A reading with no
     * plurality in it cannot expand one however far down a member the walk goes, so the boundary
     * holds over the parts of a member as well as over the member — which a guard at the point of
     * expansion would not, since the parts are read after it.
     */
    private static <A, E> java.util.List<Standing<A, E>> membersOf(
            java.util.List<ReadThrough<E>> alternatives, Reading<A, E> reading) {
        java.util.List<Standing<A, E>> out = new java.util.ArrayList<>();
        Reading<A, E> once = new OneAtATime<>(reading);
        alternatives.forEach(each -> out.add(new Standing<>(each.value(), each.at(), once)));
        return out;
    }

    /** {@code of} with no plurality in it, which is what the members of one are read with. */
    private record OneAtATime<A, E>(Reading<A, E> of) implements Reading<A, E> {

        @Override
        public Symbols symbols() {
            return of.symbols();
        }

        @Override
        public PublishedDeclarations published() {
            return of.published();
        }

        @Override
        public DeclarationKinds kinds() {
            return of.kinds();
        }

        @Override
        public NewtypeInners inners() {
            return of.inners();
        }

        @Override
        public LinearForm<A> leafOf(Core e, E at) {
            return of.leafOf(e, at);
        }

        @Override
        public E inside(Core.LetIn li, E at) {
            return of.inside(li, at);
        }


        @Override
        public ReadThrough<E> readThrough(Core.Read read, E at) {
            return of.readThrough(read, at);
        }

        @Override
        public java.util.List<ReadThrough<E>> alternativesOf(Core.Read read, E at) {
            return null;
        }

        @Override
        public boolean readsThrough(Core.FieldAccess fa, E at) {
            return of.readsThrough(fa, at);
        }
    }

    /**
     * {@code e} under the reductions this reading licenses, as far as they go.
     *
     * <p><b>A normal form and never a failure.</b> What comes back is where the reductions run out:
     * a written construction where the occurrence resolves to one, and the occurrence itself where
     * nothing licensed applies. Reaching a {@code Core.If} is such a result and not an absence —
     * what stands there is known perfectly well, and it is a choice rather than a value — so a
     * caller reads what it was handed and never treats getting its own expression back as an answer
     * about this walk.
     *
     * <p><b>Nothing here chooses.</b> The reductions that leave one value are the ones with a single
     * successor: a name the reading says denotes one value, a binding's body, and an elimination
     * standing against the introduction that wrote it. Where the reading names every value a name
     * can stand for, all of them come back — none of them is picked, and what a caller may do with
     * several is state what they agree on. An arm of a choice is neither: the reading gives it no
     * plurality and there is no rule here for one, which is what keeps a rule about
     * {@code Big { threshold = 100000 }} from being answered for a position where a second
     * construction can stand as well. That boundary is the absence of a rule and not a refusal, so
     * nothing has to be kept in step with it.
     *
     * <p><b>Closed under its own eliminations.</b> A projection resolves through whatever the target
     * resolves to, so a construction inside a construction is reached the same way a construction
     * behind a name is. Left at one step, this would cross a single introduction and no more, and
     * which spellings that admits is nothing either the language or a reader states.
     *
     * <p><b>The authority is the reading's and none is added here.</b> A name is followed only
     * where {@link Reading#readThrough} licenses that occurrence, and a binding is entered only
     * through {@link Reading#inside}. What is derived is how far those answers reach, which is not
     * the same kind of thing as the answers — so this is not the place to ask what an environment
     * happens to hold. {@code Terms.given} is that question and peels every name a binding gave a
     * value to; a name it would peel is one this leaves alone unless the reading says the two are
     * one value.
     */
    private static <A, E> java.util.List<Standing<A, E>> standing(
            Core e, E at, Reading<A, E> reading, java.util.Set<BindingId> following) {
        return switch (e) {
            case Core.Read r -> {
                ReadThrough<E> through = reading.readThrough(r, at);
                if (through != null) {
                    if (through.value() == e || !following.add(r.binding())) {
                        yield java.util.List.of(new Standing<>(e, at, reading));
                    }
                    java.util.List<Standing<A, E>> denoted =
                            standing(through.value(), through.at(), reading, following);
                    following.remove(r.binding());
                    yield denoted;
                }
                java.util.List<ReadThrough<E>> alternatives = reading.alternativesOf(r, at);
                if (alternatives == null || alternatives.isEmpty()
                        || !following.add(r.binding())) {
                    yield java.util.List.of(new Standing<>(e, at, reading));
                }
                java.util.List<Standing<A, E>> each = new java.util.ArrayList<>();
                for (Standing<A, E> one : membersOf(alternatives, reading)) {
                    each.addAll(standing(one.value(), one.at(), one.reading(), following));
                }
                following.remove(r.binding());
                yield each;
            }
            case Core.LetIn li -> standing(li.body(), reading.inside(li, at), reading, following);
            case Core.FieldAccess _, Core.TupleGet _ -> {
                java.util.List<Standing<A, E>> written = eliminated(e, at, reading, following);
                if (written == null) {
                    yield java.util.List.of(new Standing<>(e, at, reading));
                }
                java.util.List<Standing<A, E>> each = new java.util.ArrayList<>();
                for (Standing<A, E> one : written) {
                    each.addAll(standing(one.value(), one.at(), one.reading(), following));
                }
                yield each;
            }
            default -> java.util.List.of(new Standing<>(e, at, reading));
        };
    }

    /**
     * What this elimination stands against, or null where nothing written stands against it.
     *
     * <p><b>One question with one answer, asked by the two readings that have it.</b> Resolving an
     * occurrence needs to know what an elimination came to so it can go on, and reading a form
     * needs to know it so it can compose — and where it comes to nothing, the second has another
     * proof to try. Written out at each of them, one copy would come to read a shape the other did
     * not, and which shapes a rule about eliminations covers would depend on which reader met it.
     *
     * <p><b>Null is the rule not applying, and never the kind of node.</b> A construction that does
     * not give the field asked for has nothing written to stand against, exactly as an expression
     * that is no construction has not — so a caller asking whether the structural reading produced
     * a successor is asking one thing. Read off the node instead, a construction missing the field
     * would count as a reduction this never made.
     *
     * <p>The target resolves before the field is taken, which is what closes this under itself: the
     * value a construction gives a field is reached whether the construction was written where the
     * projection is or stands behind a name and another projection.
     *
     * <p><b>Taken of every value the target can stand at, or of none of them.</b> A projection out
     * of a name that stands for several is that projection out of each of them, and one member with
     * nothing written to stand against takes the answer away for all of them: what a caller is going
     * to state is what the members agree on, and a member this could not eliminate is one it has
     * nothing to compare.
     */
    private static <A, E> java.util.List<Standing<A, E>> eliminated(
            Core e, E at, Reading<A, E> reading, java.util.Set<BindingId> following) {
        return switch (e) {
            case Core.FieldAccess fa -> {
                java.util.List<Standing<A, E>> out = new java.util.ArrayList<>();
                for (Standing<A, E> target : standing(fa.target(), at, reading, following)) {
                    if (!(target.value() instanceof Core.Construct nd)) {
                        yield null;
                    }
                    Standing<A, E> given = null;
                    for (Core.FieldValue each : nd.values()) {
                        if (each.field().equals(fa.field())) {
                            // What a member gives a field is read with what the member is read
                            // with, which is how one plurality stays one over the parts of it.
                            given = new Standing<>(each.value(), target.at(), target.reading());
                            break;
                        }
                    }
                    if (given == null) {
                        yield null;
                    }
                    out.add(given);
                }
                yield out;
            }
            case Core.TupleGet get -> {
                java.util.List<Standing<A, E>> out = new java.util.ArrayList<>();
                for (Standing<A, E> tuple : standing(get.tuple(), at, reading, following)) {
                    if (!(tuple.value() instanceof Core.Tuple written) || get.index() < 0
                            || get.index() >= written.elements().size()) {
                        yield null;
                    }
                    out.add(new Standing<>(written.elements().get(get.index()), tuple.at(),
                            tuple.reading()));
                }
                yield out;
            }
            default -> null;
        };
    }

    /**
     * The one form every one of {@code these} comes to, or null where they do not all come to one.
     *
     * <p>What a rule about a position several values can stand at is entitled to say. Every value is
     * read, and what comes back is the form they support between them — which is a form they all
     * have or nothing. A reading that answered with one of them would state a hundred thousand for a
     * model that says a hundred thousand or two hundred thousand, and one that answered with what
     * two forms have in common would state a rule weaker than the model's and report it as the
     * model's.
     *
     * <p>Not a meet. There is no order being descended here and no weaker answer to fall back on:
     * the values agree or this walk has nothing to say about the position.
     */
    private static <A, E> LinearForm<A> commonForm(java.util.List<Standing<A, E>> these,
                                                   java.util.Set<BindingId> following,
                                                   Stop<A, E> stopped) {
        LinearForm<A> agreed = null;
        for (Standing<A, E> each : these) {
            LinearForm<A> here =
                    formOf(each.value(), each.at(), each.reading(), following, stopped);
            if (here == null) {
                return null;
            }
            if (agreed == null) {
                agreed = here;
            } else if (!sameForm(agreed, here)) {
                return null;
            }
        }
        return agreed;
    }

    /** {@code e} read as arithmetic over what its parts answer, or null where this has no rule for
     *  it or the rule it has does not compose. */
    private static <A, E> LinearForm<A> composed(Core e, E at, Reading<A, E> reading,
                                                 java.util.Set<BindingId> following,
                                                 Stop<A, E> stopped) {
        LinearForm<A> written = literal(e, reading);
        if (written != null) {
            return written;
        }
        return switch (e) {
            case Core.Neg n -> Terms.negate(formOf(n.operand(), at, reading, following, stopped));
            case Core.Binary b when b.op() == BinOp.ADD ->
                    Terms.add(formOf(b.left(), at, reading, following, stopped),
                            formOf(b.right(), at, reading, following, stopped), false);
            case Core.Binary b when b.op() == BinOp.SUB ->
                    Terms.add(formOf(b.left(), at, reading, following, stopped),
                            formOf(b.right(), at, reading, following, stopped), true);
            // A scalar multiply by a constant (`Amount * 2`) is linear; `/` and a variable product
            // are not — a divide truncates for `Int`, and a variable factor is non-linear — so those
            // come back here as one value rather than as arithmetic over two.
            case Core.Binary b when b.op() == BinOp.MUL ->
                    Terms.scale(formOf(b.left(), at, reading, following, stopped),
                            formOf(b.right(), at, reading, following, stopped));
            // An operation the library says answers arithmetic over what it was given is that
            // arithmetic here: `Decimal.fromInt(n)` is `n`, `Date.daysBetween(a, b)` is `b - a`,
            // and a rule about the arguments is a rule about the call. Composed here beside the
            // operators, because that is what such an operation is — read at either caller's leaf
            // instead, one of the two would have it and a statement the model makes would be
            // measured by one reader and not the other.
            case Core.PreservedCall _ when formSaidOf(e) != null ->
                    answered(e, at, reading, following, stopped);
            case Core.Call _ when formSaidOf(e) != null ->
                    answered(e, at, reading, following, stopped);
            // A newtype's construction is the value it wraps. Whether the name is one is asked of
            // the reading of the position, which says the names a value is written under: a
            // newtype puts one there and a data of one field does not — that one wraps its value
            // rather than being it, and its construction is a value of its own. Asked of the
            // declarations again instead, this would be a second answer to how far a name reaches.
            // A carrier takes the same names off to find a value written down, and answers above
            // for `Yen(100)` before this is reached. The two agree where they overlap and are not
            // one rule: that one asks what a written value counts as and stops where nothing is
            // written, and this one asks what the arithmetic under the name comes to.
            case Core.Construct nd when !nd.values().isEmpty()
                    && TypeView.asWritten(Type.ref(nd.typeName()), reading.symbols(),
                            reading.published())
                            .isWrapped() ->
                    formOf(nd.values().get(0).value(), at, reading, following, stopped);
            // One arm, holding two proofs that this projection is the value it reads. The
            // structural one is asked first and is asked as whether it produced a successor rather
            // than as what kind of node was standing there: a construction without the field asked
            // for has proved nothing, and read as a node kind it would take the answer away from
            // the proof that can still be made. Second, and only where nothing was written to
            // eliminate, the reading's own evidence that the projection keeps what it reads —
            // which is how a name a call was given is read through with no construction in sight.
            case Core.FieldAccess fa -> {
                java.util.List<Standing<A, E>> eliminated =
                        eliminated(fa, at, reading, following);
                if (eliminated != null) {
                    yield commonForm(eliminated, following, stopped);
                }
                yield reading.readsThrough(fa, at)
                        ? formOf(fa.target(), at, reading, following, stopped) : null;
            }
            // The same elimination against the introduction beside it. A tuple's element is reached
            // this way and no other — the language writes no projection of one, so what comes here
            // is what a binding over a tuple was taken apart into — and there is no second proof to
            // fall back on, because nothing declares a tuple transparent the way a newtype's
            // declaration does.
            case Core.TupleGet get -> {
                java.util.List<Standing<A, E>> eliminated =
                        eliminated(get, at, reading, following);
                yield eliminated == null ? null : commonForm(eliminated, following, stopped);
            }
            // A binding an expansion introduced (`let $0_n = n.value in $0_n * 2`) is what a helper
            // becomes, so reading through it is reading what the author wrote at the call. Whether
            // what it holds is a number is not asked: a binding denotes what it was given whatever
            // kind of value that is, which is what makes the facts a walk recorded about the places
            // under it reach the step that reads them (#867).
            case Core.LetIn li -> formOf(li.body(), reading.inside(li, at), reading, following,
                    stopped);
            default -> null;
        };
    }

    /**
     * {@code e} as the count it writes on a carrier that counts, or null where it writes none.
     *
     * <p><b>A literal on a counted carrier is a constant of this arithmetic.</b> Which counts a
     * value of a type stands on is the carrier's one answer, and a date written out stands on a day
     * as surely as a number written out stands on itself. Read as an arm per scalar, a name would
     * carry what the value that name stands for does not — so substituting a concrete value into a
     * proposition would take its proof away, which is an answer about how a model is spelled.
     *
     * <p><b>Nothing about any carrier is read here.</b> What a written value counts as is
     * {@link Carrier#literalOf}'s, which is where every reader of a written value asks it and where
     * a newtype's construction around one is taken off. Answered here instead, this walk would hold
     * a second account of what a date is written as.
     *
     * <p>A carrier that counts nothing has no constant to be: a string is ordered and stands no
     * measurable distance from another, which is the carrier's own answer and not a case here.
     *
     * <p>Null where the carrier writes no literal at this expression, and the walk goes on to read
     * it as arithmetic or as a leaf. Treated as "not affine" instead, an expression a carrier does
     * not recognise would stop a reading the grammar below can still take apart.
     */
    private static <A, E> LinearForm<A> literal(Core e, Reading<A, E> reading) {
        Carrier carrier =
                Carrier.ofValue(e.type(), reading.inners(), reading.symbols(), reading.kinds(),
                        reading.published());
        if (carrier == null || !carrier.counts()) {
            return null;
        }
        Place at = carrier.literalOf(e, reading.symbols());
        return at == null ? null : LinearForm.constant(Count.number(at).at());
    }

    /**
     * {@code call} read as the form the library says it answers, or null where one of the arguments
     * it is written over does not compose.
     *
     * <p>Over what each argument is counted as, which is the form that argument itself reads as
     * here. So a shift of a position by a written number and a shift of one position by another are
     * one rule with two readings, and neither is a case anybody wrote.
     */
    private static <A, E> LinearForm<A> answered(Core call, E at, Reading<A, E> reading,
                                                 java.util.Set<BindingId> following,
                                                 Stop<A, E> stopped) {
        LinearForm<DeclaredArgument> says = formSaidOf(call);
        java.util.List<Core> args = Terms.argsOf(call);
        // The expansion's own stops, kept off the walk's. What is inside a declared form is not
        // what an author wrote: the arguments stand where they stand because the library says the
        // operation answers this much of them, and a reader that cannot carry one of them has not
        // met an expression an author would change — it has met this call.
        Stop<A, E> inside = new Stop<>();
        LinearForm<A> form = LinearForm.constant(says.constant());
        for (Map.Entry<DeclaredArgument, BigDecimal> each : says.coefs().entrySet()) {
            // The call here may be the runnable tree's and not a kept one, so its argument count
            // is checked here rather than by a kept call's own constructor.
            int position = CallArguments.positionOf(each.getKey(), Terms.operationOf(call));
            if (position < 0 || position >= args.size()) {
                return stoppedAtTheCall(call, at, stopped);
            }
            LinearForm<A> argument = formOf(args.get(position), at, reading, following, inside);
            if (argument == null) {
                return stoppedAtTheCall(call, at, stopped);
            }
            form = form.plus(argument.times(each.getValue()));
        }
        return form;
    }

    /**
     * No form, and the call recorded as where the reading stopped.
     *
     * <p>The call and not what was found inside it. A stop is reported at the most particular
     * expression with no rule here, which is what an author would change — and inside a form the
     * library declares there is no such expression: the author wrote the call. Reported from
     * within, a rule over what {@code Date.daysBetween} answers came back as a rule about the field
     * the expansion reached, which is a rule the model does not state and a spelling nobody wrote.
     */
    private static <A, E> LinearForm<A> stoppedAtTheCall(Core call, E at, Stop<A, E> stopped) {
        if (stopped.at == null) {
            stopped.at = new Outcome.StoppedAt<>(call, at);
        }
        return null;
    }

    /**
     * What the library says {@code e} answers in what it was given, or null where it says nothing —
     * including where {@code e} is no call at all.
     *
     * <p>Asked of the operation the call resolved to and not of which of the two shapes of call node
     * it is. A body that runs holds a library call one way and the tree a declaration's rules are
     * read in holds it another, and the fact is about the operation either way; read off one shape,
     * the same statement would be composed in one representation and left a leaf in the other.
     */
    private static LinearForm<DeclaredArgument> formSaidOf(Core e) {
        souther.compiler.types.ValueName operation = Terms.operationOf(e);
        return operation == null ? null : DischargeRules.answersAFormOf(operation);
    }

    private AffineForms() {}
}
