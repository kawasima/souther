package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.diag.CompileException;
import souther.compiler.stdlib.Stdlib;
import souther.compiler.types.BindingId;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * What declarations already say about the type of a value expression.
 *
 * <p>One walk. Every consumer that needs the question reads this rather than putting declaration-led
 * typing together again: the reading that builds a row, the pass that decides which methods a row's
 * calls need emitted, the measure that asks what a fixture states, and the editor asking what may be
 * written after a {@code .}. Two walks would be two answers about the same declarations, and the one
 * that answered later would find nothing.
 *
 * <p><b>Total over the forms of an expression.</b> Every case of {@link Hir.Expr} is named here and
 * there is no {@code default}, because the two things a {@code default} answered are not one fact: a
 * form nobody wrote an arm for and a form whose type no declaration states both came back as
 * "nothing states this", and no consumer could tell them apart. Naming the forms makes the second a
 * statement — this reading does not join the arms of a {@code match}, and says so — and makes a form
 * added to the language stop the build here instead of quietly widening the first.
 *
 * <p>The same closure over what an application applies. Which declaration a callee names decides how
 * it is read, and the cases of {@link ValueName} are named rather than fallen through.
 *
 * <p>How the evidence flows through an expression is this walk's, and what a {@code .} on a value
 * may name is not. Which names a position makes readable, how far the names it wears come off, and
 * what one of those declarations holds under a name are {@link FieldRead}'s — one reading, the same
 * one an elaboration types a text by. So a caller in a world where the check has settled what a
 * value is made of gets that answer here, the walk cannot reach a second one by reading the
 * declaration itself, and a name every case of a sum spreads is answered for because it is
 * readable, rather than left unanswered because a sum lays out no field of its own.
 *
 * <p>What a declared signature is settled to by what stands at its parameters is not this walk's
 * either: it is {@link SignatureApplication}'s, the step the elaboration takes at every call. A rule
 * of its own here would be a second answer about what a polymorphic declaration answers, and the
 * two would part at whichever forgot a case.
 *
 * <p>Nothing is run. Every step reads a name {@code Resolve} already settled or a declaration a
 * module already made. Reading what an application answers means reading the callee's declaration,
 * and where the callee is a definition of this module's own that means reading its body — once per
 * {@link Specialization} of it, which is what the expansion below already counts as one application.
 *
 * @param facts     what the declarations say about a type, in the world this walk is being made in —
 *                  handed over rather than made here, so which world that is, in both halves of it,
 *                  is settled by whoever is doing the reading and not once per walk
 * @param values    the definitions a body of this module may name, under the name it reaches each of
 *                  them by
 * @param behaviors what a behavior this module can name declares it takes and answers. Required, and
 *                  not defaulted to none: a reader that left it out would answer nothing for a call
 *                  of a behavior that another reader answers for, and two answers about one
 *                  expression is the state this walk exists to remove
 * @param bound     what the bindings in force where the walk starts have to say about themselves,
 *                  which the walk adds to as it enters more of them
 */
public record DeclaredTypeReading(DeclarationFacts facts,
                                  Map<String, Hir.FnDef> values,
                                  Map<ValueName.Behavior, Sig> behaviors,
                                  Map<BindingId, BindingEvidence> bound) {

    public DeclaredTypeReading {
        if (facts == null || values == null || behaviors == null || bound == null) {
            throw new IllegalArgumentException("a reading is made against declarations, the"
                    + " definitions a body may name, the behaviors it may call, and the bindings in"
                    + " force where it starts");
        }
    }

    public DeclaredTypeReading(DeclarationFacts facts, Map<String, Hir.FnDef> values,
                               Map<ValueName.Behavior, Sig> behaviors) {
        this(facts, values, behaviors, Map.of());
    }

    /** What the names in a position denote, which is the declarations'. */
    public Symbols symbols() {
        return facts.symbols();
    }

    /**
     * What {@code e} is declared to be, or null where no declaration says.
     *
     * <p>Null is this walk's word for one thing, and every caller reads it as that and nothing else:
     * the declarations state no type for this expression. A name resolution answered with nothing is
     * one of the ways — it names no declaration to read a type off, and the mistake in it is
     * reported where it is written.
     *
     * <p>An expression is required rather than admitted as null. That a caller has no expression is
     * not a fact about any declaration, and answering it here would put back the silence the forms
     * above were named to remove.
     */
    public Type declaredTypeOf(Hir.Expr e) {
        Objects.requireNonNull(e, "what a declaration states is asked about an expression");
        return new Reading().of(e);
    }

    /**
     * A definition of a module read at the parameter types one application gives it.
     *
     * <p><b>Not the identity of an application.</b> Two calls of one helper are two applications and
     * carry two sets of variables ({@link Type.MetaVar}), and what tells them apart is the occurrence
     * each is written at — which is what the expansion below keeps and what nothing here may
     * overwrite. This is the coarser thing: the equivalence under which two applications have the
     * same answer about what the callee is declared to be. A reader that took it for the first would
     * hand two applications one set of variables.
     *
     * <p>The parameter types as this application reads them, and not the arguments it was written
     * with. What a body states about its answer follows from what its parameters are, so two
     * applications whose parameters read the same have one answer however they were written — and an
     * argument that states nothing leaves the position it stands at stating nothing, which two
     * applications may also share.
     *
     * <p>Two of them really are two. A module writes no generics, but a definition of one is not
     * monomorphic for that: what a parameter is is worked out from the body, and a use saying only
     * that two positions hold the same thing leaves a variable, which is written back onto the
     * declaration for each call to decide. So a definition is read at as many parameter types as it
     * is applied to, and a table keyed by the callee alone would answer the second reading with the
     * first one's.
     */
    private record Specialization(ValueName callee, List<Type> parameterTypes) {}

    /**
     * What the declaration a callee names states about applying it.
     *
     * <p>Two things a declaration can be, and they differ in where the answer is: one states what it
     * takes and what it answers, and one states what it takes and leaves its answer to the body it
     * was written with. Said as a type so that a callee added to {@link ValueName} has to be one of
     * them or say it is neither — and so that what holds an application to what a declaration takes
     * is one step both go through rather than a thing each arm remembers.
     */
    private sealed interface Applied {

        /** A declaration that says what it takes and what it answers. */
        record OfASignature(Type.FnOf declared) implements Applied {}

        /** A definition of a module: it says what it takes, and its body says what it answers. */
        record OfADefinition(ValueName.Helper named, Hir.FnDef definition) implements Applied {}

        /** Nothing states what applying this answers. */
        record StatesNothing() implements Applied {}
    }

    /**
     * What the arguments of one application settle of the declaration's variables.
     *
     * <p>Two answers and not a map that stands for both. A monomorphic declaration settles nothing
     * and is applied perfectly well, so an empty substitution is an answer — read as a refusal, or a
     * refusal read as one, every such application would have come back with the wrong one of the
     * two.
     */
    private sealed interface Settlement {

        /** What the arguments decided, which may be nothing. */
        record Settled(Map<String, Type> bindings) implements Settlement {}

        /** The declaration does not admit this application. */
        record Disagrees() implements Settlement {}
    }

    /** What a reading has already worked out about one specialization. Absence is not one of these:
     *  a specialization nothing has been asked about and one the declarations state nothing for are
     *  two answers, and a table that had only the second could not tell them apart. */
    private sealed interface Answered {

        /** The declarations state this. */
        record Known(Type type) implements Answered {}

        /** They state nothing about it. */
        record StatesNothing() implements Answered {}
    }

    /**
     * One walk, with what it has entered and what it has already worked out.
     *
     * <p>It lives as long as one question. A reading is built where it is asked and dropped there —
     * nothing keeps one across revisions — so a table held any longer would be a table of what a
     * source used to say.
     */
    private final class Reading {

        /** What each specialization came to, so that a declaration reached from two places is read
         *  once. This is the whole of what keeps reading an application from costing what expanding
         *  it costs. */
        private final Map<Specialization, Answered> applications = new HashMap<>();

        /** The definitions this walk is inside. A name reached again while it is being read is the
         *  recursion the reading itself reports, and this walk only stops: a body that answers by
         *  calling itself states nothing here, whatever its parameters came to. Held by callee
         *  rather than by specialization so that a recursion whose parameter types grow stops too. */
        private final Set<ValueName> entered = new HashSet<>();

        /** What the bindings this walk has entered say about themselves. */
        private final Map<BindingId, BindingEvidence> inForce = new HashMap<>(bound);

        /** How many answers a recursion cut short. An answer reached over one of those is about
         *  where it was asked from and not about the declaration, so it is not written down. */
        private int cut;

        private Type of(Hir.Expr e) {
            return switch (e) {
                // What a literal is is written in it.
                case Hir.IntLit _ -> Type.INT;
                case Hir.DecimalLit _ -> Type.DECIMAL;
                case Hir.StringLit _ -> Type.STRING;
                case Hir.BoolLit _ -> Type.BOOL;
                case Hir.NewData nd -> nd.typeName().answered() == null
                        ? null : Type.ref(nd.typeName().answered().type());
                case Hir.FieldAccess fa -> {
                    Type target = of(fa.target());
                    yield target == null ? null : facts.read().of(target, fa.field());
                }
                case Hir.Apply call -> applied(call);
                case Hir.Expansion ex -> ofExpansion(ex);
                case Hir.LetIn let -> ofLet(let);
                case Hir.Var v -> ofVar(v);
                // It answers no value, which is a type and is this one.
                case Hir.Unreachable _ -> Type.NEVER;
                // What an operator answers is decided by the operands together, and that rule is
                // the elaboration's. Read here it would be a second one, agreeing until either
                // moved.
                case Hir.Binary _, Hir.Neg _ -> null;
                // What a fork answers is the join of what its arms answer, which is the
                // elaboration's for the same reason. This reading crosses no arm.
                case Hir.Match _, Hir.If _, Hir.IfConstructed _ -> null;
                // What a collection holds is the join of its elements, and an empty one holds a
                // bottom the position it stands in decides (ADR-0028). Neither is a declaration.
                case Hir.ListLit _, Hir.RowCollection _, Hir.ListComp _ -> null;
                // A tuple carries several values through a computation and is written in no
                // declaration (ADR-0036), so nothing states what one is.
                case Hir.Tuple _, Hir.TupleGet _ -> null;
                // A block is second-class: it is an argument and never a value, so no declaration
                // states a type for one standing here.
                case Hir.Block _ -> null;
            };
        }

        /** What a name is declared to stand for. */
        private Type ofVar(Hir.Var v) {
            // A name resolution answered with nothing states no type: there is no declaration to
            // read one off, and what is wrong with the name is reported where it is written.
            if (v.answered() == null) {
                return null;
            }
            return switch (v.answered().denotes()) {
                case ValueName.Local local -> ofBinding(local);
                case ValueName.Helper helper -> ofValue(helper, v.name());
                // A behavior named where a value goes is what it takes and answers.
                case ValueName.Behavior behavior -> fnOf(behaviors.get(behavior));
                // A type written where a value goes. What is wrong with it is reported there.
                case ValueName.OfType _ -> null;
                // `Some` and `None`, which are written where a `?` field is given a value and are
                // values of nothing on their own (spec §algebraic-types).
                case ValueName.Builtin _ -> null;
                // A library operation standing where a value goes is expanded into the block it
                // stands for before anything reads it, so a name still here states nothing.
                case ValueName.Stdlib _ -> null;
            };
        }

        /** What the binding in force says about itself. */
        private Type ofBinding(ValueName.Local local) {
            return switch (inForce.get(local.id())) {
                // One more step of this walk, where the binding stands for an expression.
                case BindingEvidence.BoundTo(Hir.Expr value) -> of(value);
                // The answer outright, where a declaration gave it one.
                case BindingEvidence.DeclaredAs(Type declared) -> declared;
                case null -> null;
            };
        }

        /** A module-level value, read as the definition it names applied to nothing. */
        private Type ofValue(ValueName.Helper named, String reachedBy) {
            Hir.FnDef value = values.get(reachedBy);
            // A definition taking parameters, standing where a value goes. What it answers is its
            // application's, and there is no application here.
            return value == null || !value.params().isEmpty()
                    ? null : ofDefinition(named, value, List.of());
        }

        /**
         * What one expansion answers: what its body states, read with each binding it wrote.
         *
         * <p>Reached from inside rather than handed in. A definition another module publishes
         * arrives with the helpers it calls already expanded into it, and this reading walks those
         * bodies — so an expansion is an ordinary thing to meet, not a tree somebody passed by
         * mistake.
         *
         * <p>A binding an expansion wrote names two things, and both are read: the argument it
         * stands for, and the type the callee declared for the parameter it fills. Which of them
         * the binding takes is the elaboration's rule — a declared sum is what the body was written
         * against and is wider than the case that arrived, while a declared type that stands for
         * whatever this application decides says less than the argument does. Asked there rather
         * than decided again here, so a name in an expanded body reads as the same type the
         * elaboration binds it at.
         */
        private Type ofExpansion(Hir.Expansion ex) {
            // The variables of what an expansion holds are this one application's, and the
            // application is what decides them. Not the step a written call takes: there a
            // declaration's own variables are settled for one call and left where they were, and
            // here the copy in hand already names variables minted for it. Two questions with two
            // owners, and each is asked of the one that owns it.
            Substitution decided = new Substitution(ex.application(), null);
            List<Type> declared = new ArrayList<>();
            List<Type> arrived = new ArrayList<>();
            // Every value argument first, and every one of them read once. What one decides stands
            // at every position the declaration wrote it at, so a parameter read before the
            // argument that decides its variable would be read at a variable and not at a type —
            // which is why the deciding is finished before anything is held or bound. It is the
            // order a written call is read in, and the two are one reading of one declaration.
            for (Hir.Bound bound : ex.bound()) {
                declared.add(bound.declaredType() == null
                        ? null : TypeOps.resolveParamType(bound.declaredType()));
                arrived.add(of(bound.value()));
            }
            if (!settles(decided, declared, arrived) || !admitted(ex, decided, declared, arrived)) {
                return null;
            }
            Map<BindingId, BindingEvidence> outer = new LinkedHashMap<>();
            try {
                for (int i = 0; i < ex.bound().size(); i++) {
                    Hir.Bound bound = ex.bound().get(i);
                    // The parameter type as this application settled it, which is what the body was
                    // written against. Read as it was written, a variable another argument decided
                    // would still be standing here.
                    Type required = declared.get(i) == null
                            ? null : decided.zonk(declared.get(i));
                    outer.put(bound.binder().id(), inForce.put(bound.binder().id(),
                            boundBy(bound, required, arrived.get(i))));
                }
                Type answers = ex.declaredReturn() == null
                        ? null : TypeOps.resolveParamType(ex.declaredReturn());
                if (answers == null) {
                    return of(ex.body());
                }
                // What the callee declared it answers, where this application has decided it. Where
                // the arguments left it open it is a function argument that would decide it, and
                // this reading does not type one — so it states nothing, which is what it states
                // for the same call written rather than expanded. Falling to the body here would
                // make one declaration answer by how the call reached this reading.
                return decided.open(answers) ? null : closed(decided.zonk(answers));
            } finally {
                outer.forEach(this::restore);
            }
        }

        /** What each argument says about the variables the declaration carries. Nothing where two
         *  readings of one variable do not go together, which leaves an application nothing can be
         *  built from — what the deciding says of itself. */
        private boolean settles(Substitution decided, List<Type> declared, List<Type> arrived) {
            for (int i = 0; i < declared.size(); i++) {
                if (declared.get(i) != null && arrived.get(i) != null
                        && decided.decide(declared.get(i), arrived.get(i), symbols())
                                instanceof Fit.Disagrees) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Whether the declaration admits what this application was given, once its variables are
         * settled.
         *
         * <p>Deciding is not admitting: what a variable is read at is one question, and whether a
         * value of some other shape may stand at a position is another, which the deciding says it
         * does not answer. Held apart here for the same reason they are held apart there — an
         * argument of a type the declaration never wrote carries no variable to disagree about.
         *
         * <p>The functions it was given as well, where the expansion carries what arrives — a
         * function passed under a name whose declaration says what it takes and answers. Both sides
         * are a statement each and the boundary is the one place holding them together, since a
         * function argument leaves no binding to read it at. A lambda written at the call carries
         * no such statement, and one the callee applies is read where it applies it, which this
         * walk reaches as an expansion of its own; what either decides of this application's
         * variables is not read here, which is the same silence a written call keeps about a block.
         */
        private boolean admitted(Hir.Expansion ex, Substitution decided, List<Type> declared,
                                 List<Type> arrived) {
            for (int i = 0; i < declared.size(); i++) {
                if (declared.get(i) != null && arrived.get(i) != null
                        && decided.hold(declared.get(i), arrived.get(i), symbols())
                                instanceof Fit.Disagrees) {
                    return false;
                }
            }
            for (Hir.Given given : ex.given()) {
                Type takes = given.declaredType() == null
                        ? null : TypeOps.resolveParamType(given.declaredType());
                Type arrives = given.arrivesAs() == null
                        ? null : TypeOps.resolveParamType(given.arrivesAs());
                if (takes != null && arrives != null
                        && decided.hold(takes, arrives, symbols()) instanceof Fit.Disagrees) {
                    return false;
                }
            }
            return true;
        }

        /** What a binding an expansion wrote says about itself: the parameter type as this
         *  application settled it, and what arrived there. */
        private BindingEvidence boundBy(Hir.Bound bound, Type required, Type arrived) {
            if (required == null || closed(required) == null) {
                // Nothing the declaration wrote there, or a variable this application was to decide
                // and nothing did. Either way what is left is the argument.
                return new BindingEvidence.BoundTo(bound.value());
            }
            return new BindingEvidence.DeclaredAs(arrived == null ? required
                    : Elaborator.carriedType(required, arrived, symbols()));
        }

        /** What a {@code let} puts in force while its body is read. */
        private Type ofLet(Hir.LetIn let) {
            BindingId binding = let.binder().id();
            BindingEvidence outer = inForce.put(binding, evidenceOf(let));
            try {
                return of(let.body());
            } finally {
                restore(binding, outer);
            }
        }

        /**
         * What the binding a {@code let} writes says about itself.
         *
         * <p>The type the author wrote on it, where they wrote one: an annotation is what a reader
         * of the name is looking at, and the value only says what it happens to be — a case where
         * the annotation says the sum.
         *
         * <p>Only what an author wrote, which {@link Hir.LetIn#annotation()} is and the field beside
         * it is not. A type on a binding that no author wrote is a parameter type an inlining
         * carried there, and a binding an inlining wrote is read where the expansion that wrote it
         * is — with the argument beside it, which is what deciding between the two needs.
         */
        private BindingEvidence evidenceOf(Hir.LetIn let) {
            return let.annotation() == null
                    ? new BindingEvidence.BoundTo(let.value())
                    : new BindingEvidence.DeclaredAs(TypeOps.resolveParamType(let.annotation()));
        }

        // --- what an application answers ---------------------------------------------------------

        /**
         * What applying something answers, by what the callee names.
         *
         * <p>What is applied is a name or it is nothing this reads: an expression in the callee
         * position answers a function at run time, and no declaration says which.
         *
         * <p>Every case goes through {@link #admits}, which is where an application is held to what
         * the declaration takes. Answered by each arm on its own, the holding was the arm's to
         * remember: the ones reading a signature counted the arguments and the ones reading a
         * construction did not, and none of them asked whether what arrived is what the position
         * takes — so a call the check refuses came back with a type.
         */
        private Type applied(Hir.Apply call) {
            return call.answered() == null ? null : answerOf(whatIsApplied(call.answered()), call);
        }

        /** What the declaration a callee names states about applying it. */
        private Applied whatIsApplied(Hir.Var.Denoting callee) {
            return switch (callee.denotes()) {
                // A function in force — a helper's function parameter, or the behavior an
                // implementation was injected with, which arrives as what it takes and answers.
                case ValueName.Local local -> ofASignature(asFn(ofBinding(local)));
                case ValueName.Helper helper -> {
                    Hir.FnDef definition = values.get(callee.name());
                    yield definition == null ? new Applied.StatesNothing()
                            : new Applied.OfADefinition(helper, definition);
                }
                case ValueName.Behavior behavior -> ofASignature(fnOf(behaviors.get(behavior)));
                // The namespace itself applied builds a value of the primitive it names, off the one
                // written string it takes — `Date("2026-09-30")` — and only the temporals build
                // anything. That the string is written out rather than computed is the
                // elaboration's to require; what it takes and answers is what is read here.
                case ValueName.Stdlib.Namespace namespace ->
                        ofASignature(namespace.constructs() == null ? null
                                : new Type.FnOf(List.of(Type.STRING), namespace.constructs()));
                case ValueName.Stdlib.Operation operation -> ofASignature(fnOf(operation));
                // `AmountN(100)` is the newtype's construction written in call form (ADR-0032): it
                // takes what the newtype is written with and answers the newtype. A name of anything
                // else applied is refused where it is written.
                case ValueName.OfType named -> ofASignature(!facts.isNewtype(named.type()) ? null
                        : fnOf(wraps(named.type()), Type.ref(named.type())));
                // A name the language gives is not a function (spec §algebraic-types), so nothing
                // states what applying one answers.
                case ValueName.Builtin _ -> new Applied.StatesNothing();
            };
        }

        /**
         * What the declaration states, applied to the arguments of {@code call}.
         *
         * <p>Null where the declaration does not admit this application, and null where what the
         * arguments state leaves a variable of the answer open. The last of those is the one that is
         * easy to miss: a variable a function argument decides is decided by typing that argument,
         * which this reading does not do, so the answer would be a type nothing here settled.
         */
        private Type answerOf(Applied applied, Hir.Apply call) {
            return switch (applied) {
                case Applied.StatesNothing _ -> null;
                case Applied.OfASignature(Type.FnOf declared) ->
                        admits(declared.params(), declared.result(), call)
                                instanceof Settlement.Settled(var bindings)
                                ? closed(TypeOps.substitute(declared.result(), bindings)) : null;
                case Applied.OfADefinition(ValueName.Helper named, Hir.FnDef definition) ->
                        answerOfDefinition(named, definition, call);
            };
        }

        /**
         * What a definition of this module answers, applied to the arguments of {@code call}.
         *
         * <p>A definition declares its parameters where it wrote them and nothing about its answer,
         * so what it answers is what its body states — read with each parameter at what this
         * application gives it, which is the type the definition wrote or, where it wrote none, what
         * the argument states.
         */
        private Type answerOfDefinition(ValueName.Helper named, Hir.FnDef definition,
                                        Hir.Apply call) {
            List<Type> written = new ArrayList<>();
            for (Hir.FnParam parameter : definition.params()) {
                written.add(parameter.type() == null ? null
                        : TypeOps.resolveParamType(parameter.type()));
            }
            Type answers = definition.declaredReturn() == null
                    ? null : TypeOps.resolveParamType(definition.declaredReturn());
            if (!(admits(written, answers, call) instanceof Settlement.Settled(var bindings))) {
                return null;
            }
            List<Type> at = new ArrayList<>();
            for (int i = 0; i < written.size(); i++) {
                at.add(written.get(i) == null ? of(call.args().get(i))
                        : TypeOps.substitute(written.get(i), bindings));
            }
            // A definition that declares what it answers says it here — a shipped kernel does, and
            // its body names a primitive rather than stating anything.
            return answers == null ? ofDefinition(named, definition, at)
                    : closed(TypeOps.substitute(answers, bindings));
        }

        /**
         * What the arguments settle of {@code declared}, or that the declaration does not admit
         * them.
         *
         * <p>Two things the declaration says about being applied, and both are refusals rather than
         * answers about a type: it takes as many values as it takes, and each position takes what it
         * takes. Held here rather than at each caller, because a caller that had to remember was a
         * caller that could forget — and what it forgets is a type stated for an application that
         * cannot happen, which is worse than stating nothing.
         *
         * <p>Whether an argument fits is asked after the variables are settled and not before: a
         * position written as a variable takes whatever the arguments decide it to be, and asking of
         * the unsettled parameter would refuse every polymorphic declaration. It is asked with the
         * one rule the elaboration holds an argument to, so a call this reading states a type for is
         * one that reading would accept.
         *
         * <p>A position where the declaration states nothing, or where the arguments do, settles
         * nothing and is held to nothing. Standing a type in for either would settle a variable off
         * a value this reading does not have.
         *
         * @param declared what the declaration takes at each position, aligned with the arguments,
         *                 null where it states nothing there
         */
        private Settlement admits(List<Type> declared, Type answers, Hir.Apply call) {
            if (declared.size() != call.args().size()) {
                return new Settlement.Disagrees();
            }
            List<Type> settling = new ArrayList<>();
            List<Type> stated = new ArrayList<>();
            for (int i = 0; i < declared.size(); i++) {
                Type at = declared.get(i) == null ? null : of(call.args().get(i));
                if (at != null) {
                    settling.add(declared.get(i));
                    stated.add(at);
                }
            }
            Map<String, Type> bindings;
            try {
                // No position is read: what the declaration answers is what this reading is asking
                // about, and there is nothing above the call requiring anything of it.
                bindings = SignatureApplication.settledByValues(settling, answers, null,
                        stated::get, symbols());
            } catch (CompileException _) {
                // What a variable cannot be settled to at once is a disagreement between two
                // arguments, and what is wrong with it is reported where the call is written.
                return new Settlement.Disagrees();
            }
            for (int i = 0; i < settling.size(); i++) {
                if (!TypeOps.assignable(stated.get(i),
                        TypeOps.substitute(settling.get(i), bindings), symbols())) {
                    return new Settlement.Disagrees();
                }
            }
            return new Settlement.Settled(bindings);
        }

        /**
         * What the body of {@code definition} states, read with its parameters at {@code at}.
         *
         * <p>Once per specialization. A definition reached from two places with the same parameter
         * types has one answer, and reading it again would be the expansion below done twice for
         * one of them.
         */
        private Type ofDefinition(ValueName.Helper helper, Hir.FnDef definition, List<Type> at) {
            if (!(definition.body() instanceof Hir.FnBody.Written written)) {
                return null;   // a named kernel; what it answers its declaration says
            }
            Specialization key = new Specialization(helper, at);
            switch (applications.get(key)) {
                case Answered.Known(Type known) -> {
                    return known;
                }
                case Answered.StatesNothing _ -> {
                    return null;
                }
                case null -> { }
            }
            if (!entered.add(helper)) {
                cut++;
                return null;
            }
            int before = cut;
            Map<BindingId, BindingEvidence> outer = new HashMap<>();
            for (int i = 0; i < definition.params().size(); i++) {
                if (at.get(i) == null) {
                    continue;   // nothing states what arrives here, and the body reads that
                }
                // What a declaration's parameter is is the application's to say, so the binding is
                // in force for this reading of the body and no longer. Two readings of one
                // definition are two things it was applied to, and a parameter left standing would
                // hand the second what the first was given.
                BindingId parameter = definition.params().get(i).binder().id();
                outer.put(parameter,
                        inForce.put(parameter, new BindingEvidence.DeclaredAs(at.get(i))));
            }
            try {
                Type states = of(written.expr());
                if (cut == before) {
                    applications.put(key, states == null
                            ? new Answered.StatesNothing() : new Answered.Known(states));
                }
                return states;
            } finally {
                entered.remove(helper);
                outer.forEach(this::restore);
            }
        }

        /** What a newtype is written with (spec §newtype) — the one name a value of it makes
         *  readable, asked of the reading that says which names those are rather than spelt again
         *  here. Null where the declaration does not read, which states nothing about what
         *  constructing one takes. */
        private Type wraps(TypeSymbol newtype) {
            Map<String, Type> readable = facts.read().at(Type.ref(newtype));
            return readable.size() == 1 ? readable.values().iterator().next() : null;
        }

        private void restore(BindingId binding, BindingEvidence outer) {
            if (outer == null) {
                inForce.remove(binding);
            } else {
                inForce.put(binding, outer);
            }
        }
    }

    // --- what stands at a callee position ---------------------------------------------------------

    /** {@code type} where every variable in it is settled, and null where one is still open. A type
     *  holding one says that whoever applies the declaration decides it, which is a statement about
     *  an application and not about what a value here is. */
    private static Type closed(Type type) {
        return type != null && !Type.mentions(type, open -> open instanceof Type.Open)
                ? type : null;
    }

    /** {@code type} where it is a function, and null where a name in force stands for anything else
     *  — a value applied as though it were one, which is refused where it is written. */
    private static Type.FnOf asFn(Type type) {
        return type instanceof Type.FnOf fn ? fn : null;
    }

    /** A declaration that states what it takes and what it answers, or that nothing in reach states
     *  either — a signature this revision has not settled, a name in force that is no function. */
    private static Applied ofASignature(Type.FnOf declared) {
        return declared == null ? new Applied.StatesNothing()
                : new Applied.OfASignature(declared);
    }

    /** What takes one value and answers another, or null where what it takes is not in reach. */
    private static Type.FnOf fnOf(Type takes, Type answers) {
        return takes == null ? null : new Type.FnOf(List.of(takes), answers);
    }

    /** What a behavior takes and answers, or null where this revision settles no signature for it —
     *  the module declaring it still being read. */
    private static Type.FnOf fnOf(Sig sig) {
        return sig == null ? null : new Type.FnOf(sig.inputTypes(), sig.outputType());
    }

    /** What a library operation is declared to take and answer. A shipped kernel says so in its
     *  kernel signature and a Souther-bodied operation in the {@code let} the library published;
     *  both are declarations, and which of the two it is the library says. */
    private Type.FnOf fnOf(ValueName.Stdlib.Operation operation) {
        Stdlib library = symbols().library();
        Stdlib.Intrinsic kernel = library.intrinsicOf(operation);
        if (kernel != null) {
            return new Type.FnOf(kernel.signature().parameters(), kernel.signature().result());
        }
        Stdlib.Entry entry = library.entry(operation);
        return entry == null ? null
                : new Type.FnOf(entry.signature().params(), entry.signature().result());
    }
}
