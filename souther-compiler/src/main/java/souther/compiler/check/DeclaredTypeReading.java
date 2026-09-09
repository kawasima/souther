package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.diag.CompileException;
import souther.compiler.stdlib.Stdlib;
import souther.compiler.types.BindingId;
import souther.compiler.types.Type;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
     */
    private record Specialization(ValueName callee, List<Type> parameterTypes) {}

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
                case BindingEvidence.Carried(Hir.RetType declared, Hir.Expr value) -> {
                    Type arrived = of(value);
                    Type parameter = TypeOps.resolveParamType(declared);
                    // With nothing said about what arrived, the callee's own parameter type is all
                    // there is, and it answers where it is a type at all rather than a variable this
                    // application was to decide.
                    yield arrived == null ? closed(parameter)
                            : Elaborator.carriedType(declared, arrived, symbols());
                }
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

        /** What one expansion answers: what the callee declared, or what the body it left states. */
        private Type ofExpansion(Hir.Expansion ex) {
            Type declared = ex.declaredReturn() == null
                    ? null : TypeOps.resolveParamType(ex.declaredReturn());
            // Its variables are this application's, and one still open says only that this
            // application decides it — which the bindings it wrote may state.
            Type settled = closed(declared);
            return settled != null ? settled : of(ex.asBindings());
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

        /** What the binding a {@code let} writes says about itself: the type the author wrote, the
         *  parameter type an inlining carried here, or the expression it stands for. */
        private BindingEvidence evidenceOf(Hir.LetIn let) {
            if (let.annotation() != null) {
                return new BindingEvidence.DeclaredAs(
                        TypeOps.resolveParamType(let.annotation()));
            }
            return let.declaredType() == null
                    ? new BindingEvidence.BoundTo(let.value())
                    : new BindingEvidence.Carried(let.declaredType(), let.value());
        }

        // --- what an application answers ---------------------------------------------------------

        /**
         * What applying something answers, by what the callee names.
         *
         * <p>What is applied is a name or it is nothing this reads: an expression in the callee
         * position answers a function at run time, and no declaration says which.
         */
        private Type applied(Hir.Apply call) {
            if (call.answered() == null) {
                return null;
            }
            String reachedBy = call.answered().name();
            return switch (call.answered().denotes()) {
                // A function in force — a helper's function parameter, or the behavior an
                // implementation was injected with, which arrives as what it takes and answers.
                case ValueName.Local local -> appliedSignature(asFn(ofBinding(local)), call);
                case ValueName.Helper helper -> appliedDefinition(helper, reachedBy, call);
                case ValueName.Behavior behavior ->
                        appliedSignature(fnOf(behaviors.get(behavior)), call);
                // The namespace itself applied builds a value of the primitive it names —
                // `Date("2026-09-30")` — and only the temporals build anything.
                case ValueName.Stdlib.Namespace namespace -> namespace.constructs();
                case ValueName.Stdlib.Operation operation ->
                        appliedSignature(fnOf(operation), call);
                // `AmountN(100)` is the newtype's construction written in call form (ADR-0032). A
                // name of anything else applied is refused where it is written.
                case ValueName.OfType named -> facts.isNewtype(named.type())
                        ? Type.ref(named.type()) : null;
                // A name the language gives is not a function (spec §algebraic-types), so nothing
                // states what applying one answers.
                case ValueName.Builtin _ -> null;
            };
        }

        /**
         * A declared signature applied to what the arguments state.
         *
         * <p>Null where the signature is not in reach, where the call was written with another
         * number of arguments than it declares, and where what the arguments state leaves a variable
         * of the result open. The last of those is the one that matters: a variable a function
         * argument decides is decided by typing that argument, which this reading does not do, so
         * the answer would be a type nothing here settled.
         */
        private Type appliedSignature(Type.FnOf signature, Hir.Apply call) {
            if (signature == null || signature.params().size() != call.args().size()) {
                return null;
            }
            List<Type> declared = new ArrayList<>();
            List<Type> stated = new ArrayList<>();
            for (int i = 0; i < call.args().size(); i++) {
                Type at = of(call.args().get(i));
                // A position nothing states a type at settles nothing, and is left out rather than
                // stood in for: a type put here would settle a variable off a value this reading
                // does not have.
                if (at != null) {
                    declared.add(signature.params().get(i));
                    stated.add(at);
                }
            }
            Type answered = TypeOps.substitute(signature.result(), settled(declared, stated,
                    signature.result()));
            return closed(answered);
        }

        /** What a definition of this module answers, applied to the arguments of {@code call}. */
        private Type appliedDefinition(ValueName.Helper helper, String reachedBy, Hir.Apply call) {
            Hir.FnDef definition = values.get(reachedBy);
            if (definition == null || definition.params().size() != call.args().size()) {
                return null;
            }
            List<Type> declared = new ArrayList<>();
            List<Type> stated = new ArrayList<>();
            List<Type> at = new ArrayList<>();
            for (int i = 0; i < call.args().size(); i++) {
                Hir.RetType written = definition.params().get(i).type();
                Type parameter = written == null ? null : TypeOps.resolveParamType(written);
                Type argument = of(call.args().get(i));
                if (parameter != null && argument != null) {
                    declared.add(parameter);
                    stated.add(argument);
                }
                at.add(parameter == null ? argument : parameter);
            }
            Type answers = definition.declaredReturn() == null
                    ? null : TypeOps.resolveParamType(definition.declaredReturn());
            Map<String, Type> bind = settled(declared, stated, answers);
            for (int i = 0; i < at.size(); i++) {
                if (at.get(i) != null) {
                    at.set(i, TypeOps.substitute(at.get(i), bind));
                }
            }
            // A definition that declares what it answers says it here — a shipped kernel does, and
            // its body names a primitive rather than stating anything.
            return answers != null
                    ? closed(TypeOps.substitute(answers, bind))
                    : ofDefinition(helper, definition, at);
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

        /**
         * What the arguments settle of a declaration's variables, asked of the one step every reader
         * of a declared signature takes.
         *
         * <p>Nothing where they disagree with it. An argument that does not fit the parameter it was
         * given to is what the check reports where the call is written, and a reading that answered
         * a type from the disagreement would be stating something no declaration does.
         */
        private Map<String, Type> settled(List<Type> declared, List<Type> stated, Type result) {
            try {
                return SignatureApplication.settledByValues(declared, result, null, stated::get,
                        symbols());
            } catch (CompileException _) {
                return Map.of();
            }
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
