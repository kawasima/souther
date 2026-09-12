package souther.compiler.check;

import souther.compiler.stdlib.Stdlib;
import souther.compiler.types.BinOp;
import souther.compiler.ast.Hir;
import souther.compiler.diag.CompileException;
import souther.compiler.types.BindingId;
import souther.compiler.types.ResolvedCase;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Settles the type of every helper parameter the author left unwritten, reading it off the helper's
 * own body (spec §fn-declaration, issue #176) and writing it back onto the parameter (issue #178).
 *
 * <p>Writing it back is what makes the type reach the helper's expansion. A helper call is rewritten
 * to {@code let $k_p = arg in <body>} carrying the parameter's type onto the binding, so a value the
 * helper knows to be a sum is not narrowed to the case the caller passed when the body is re-checked
 * inline. That happens in {@link Lower}, before the type checker runs, so a type the checker settles
 * would arrive too late — the type is settled here instead, on the tree, and the inliner reads it
 * like any written one. The type is written as a reference with no surface text (see
 * {@link Hir.TypeRef#of}): what a parameter denotes is decided, and no source stands for it.
 *
 * <p>Settling is best-effort and reports nothing. A parameter its body does not determine is left as
 * it was, for {@link HelperTyping} to report where it reports today — so a helper that cannot be
 * typed still names the use that left it open, and one broken helper still does not hide the errors
 * in the rest of the module.
 */
final class HelperParams {

    private HelperParams() {}

    /**
     * {@code module} with each settleable helper parameter carrying the type its body gives it. One
     * helper's settled type can settle the next one's — {@code describe}'s parameter is determined
     * only by passing it to {@code hold}, whose own parameter the body settles — so the rounds run
     * until nothing new is settled.
     */
    static Hir.Module settle(Hir.Module module, Symbols symbols, PublishedDeclarations published,
                             DeclarationKinds kinds,
                             Map<ValueName.Behavior, ReqSig> reqSigs) {
        Hir.Module current = module;
        while (true) {
            Hir.Module next = settleOnce(current, symbols, published, kinds, reqSigs);
            if (next == current) {
                return current;
            }
            current = next;
        }
    }

    /** One round: returns {@code m} itself when no parameter was settled. */
    private static Hir.Module settleOnce(Hir.Module m, Symbols symbols,
                                         PublishedDeclarations published, DeclarationKinds kinds,
                                         Map<ValueName.Behavior, ReqSig> reqSigs) {
        if (!hasOpenParam(m)) {
            return m;   // nothing to settle: don't build the inliner (it scans the whole prelude)
        }
        HelperInliner inliner = HelperInliner.forModule(m, symbols.library());
        // The addresses this module holds its own recursions at, which is what the loop below has:
        // it walks the definitions and asks whether each is one of them.
        Set<String> recursive = new LinkedHashSet<>();
        inliner.recursiveHelpers().forEach(
                reference -> recursive.add(reference.rendered()));
        Map<String, Type> recursiveHelperFns;
        try {
            // Every recursion in reach, not only what this module declares: settling reads a body
            // with its calls expanded, and a call the expansion left standing has to be typeable
            // there whether or not this module has taken the callee on.
            recursiveHelperFns = HelperTyping.recursiveCallSigs(inliner, symbols);
        } catch (CompileException _) {
            // One recursive helper that does not declare its types costs the signatures of all of
            // them, which is not observable: the check builds this same map outside its recovery, so
            // the module is abandoned on that error before any helper is typed. If that map is ever
            // made recoverable, this has to be made per-helper along with it — otherwise a helper
            // this leaves unsettled is reported as undetermined on top of the real error.
            recursiveHelperFns = Map.of();
        }
        Map<String, Hir.FnDef> settled = new LinkedHashMap<>();
        for (HelperEntry entry : inliner.held().values()) {
            Hir.FnDef h = entry.definition();
            if (recursive.contains(h.name())) {
                continue;   // a recursive helper is not inlined and declares its parameters (spec §fn-declaration)
            }
            Hir.FnDef s = settle(h, inliner, symbols, published, kinds, reqSigs, recursiveHelperFns);
            if (s != null) {
                settled.put(h.name(), s);
            }
        }
        if (settled.isEmpty()) {
            return m;
        }
        // Both, and each back where it was. A helper the module took on to emit has parameters to
        // settle like any other, and one written back into the wrong component would be a
        // declaration this module never wrote.
        return m.withFns(written(m.fns(), settled)).withTakenOn(written(m.takenOn(), settled));
    }

    /** {@code fns} with each one that {@code settled} answered for replaced by what it settled to. */
    private static List<Hir.FnDef> written(List<Hir.FnDef> fns, Map<String, Hir.FnDef> settled) {
        List<Hir.FnDef> out = new ArrayList<>();
        for (Hir.FnDef fn : fns) {
            out.add(settled.getOrDefault(fn.name(), fn));
        }
        return out;
    }

    /**
     * Whether {@code m} has a helper parameter with no type written on it — the only thing there is
     * to settle. A behavior's implementation is not a helper: its parameters are typed from the
     * behavior and carry no type of their own, so counting them would answer yes for every module
     * that implements a behavior and this would never skip anything.
     */
    private static boolean hasOpenParam(Hir.Module m) {
        Set<String> behaviors = new HashSet<>();
        for (Hir.BehaviorDef b : m.behaviors()) {
            behaviors.add(b.name());
        }
        for (List<Hir.FnDef> fns : List.of(m.fns(), m.takenOn())) {
            for (Hir.FnDef fn : fns) {
                if (behaviors.contains(fn.name())) {
                    continue;
                }
                for (Hir.FnParam p : fn.params()) {
                    if (p.type() == null) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * The type {@code body} gives {@code param}, or null where it gives none — the same reading that
     * settles a helper's own parameter, for a lambda whose parameter type the signature it was given
     * to left open and no application will settle. Reports nothing: it is asked where a type is
     * wanted, not where one is required.
     */
    static Type readFromBody(Hir.Binder param, Hir.Expr body, Scope env, CheckContext ctx,
                             Type answers) {
        try {
            return new BodyTyping(ctx.symbols(), ctx.published(), ctx.kinds(), ctx.reqs(), Map.of())
                    .typeOf(param, body, env, answers);
        } catch (CompileException | Unanswerable _) {
            // The two ways a body has no type to give: it does not type, and it depends on a name
            // that denotes nothing. Anything else thrown here is this compiler being wrong about
            // itself, and reading it as "no answer" would leave a signature unchecked over it.
            return null;
        }
    }

    /** {@code h} with its determinable parameters typed, or null when none of them is. */
    private static Hir.FnDef settle(Hir.FnDef h, HelperInliner inliner, Symbols symbols,
                                    PublishedDeclarations published, DeclarationKinds kinds,
                                    Map<ValueName.Behavior, ReqSig> reqSigs, Map<String, Type> recursiveHelperFns) {
        List<Integer> open = new ArrayList<>();
        Scope env = Scope.NONE;
        Hir.Expr body;
        try {
            for (int i = 0; i < h.params().size(); i++) {
                Hir.FnParam p = h.params().get(i);
                if (p.type() == null) {
                    open.add(i);
                } else {
                    env = env.with(p.binder(), TypeOps.resolveParamType(p.type()));
                }
            }
            if (open.isEmpty()) {
                return null;
            }
            body = inliner.inline(h.writtenBody(), inliner.bodyOf(h.name()));
        } catch (CompileException _) {
            return null;   // a written type or a call that does not resolve; the check reports it
        }
        Map<Integer, Type> found;
        try {
            found = determine(h, open, env, body, symbols, published, kinds, reqSigs,
                    recursiveHelperFns, new HashMap<>());
        } catch (Unanswerable _) {
            // A body resting on a name that denotes nothing gives no type, which is an answer this
            // reading is allowed to have: settling reports nothing, and the name was reported where
            // it was written. One helper, so the helpers beside it are still settled — the same
            // granularity `PipelineSigs.signatures` keeps for one composition.
            //
            // Caught at the call rather than inside the reading, because the check reads this same
            // body through `determine` and needs the signal to reach it. There, a parameter left
            // open is a parameter to annotate, and a parameter this body never named
            // because the name beside it denotes nothing is that one mistake seen from another
            // angle, not a type the author has to supply.
            return null;
        }
        if (found.isEmpty()) {
            return null;
        }
        Map<Type.MetaVar, Type> generalized = new HashMap<>();
        List<Hir.FnParam> params = new ArrayList<>();
        for (int i = 0; i < h.params().size(); i++) {
            Hir.FnParam p = h.params().get(i);
            Type t = found.get(i);
            params.add(t == null ? p
                    : new Hir.FnParam(p.binder(),
                            new Hir.RetType(
                                    List.of(Hir.TypeRef.of(generalize(t, generalized), p.pos())),
                                    p.pos()),
                            p.typeFromPattern()));
        }
        return new Hir.FnDef(h.written(), h.declaredIn(), params, h.declaredReturn(), h.body(),
                h.modifiers(), h.role(), h.pos());
    }

    /**
     * {@code t} as this helper's own signature says it, with every variable an expansion inside the
     * body left open written as a variable of the helper.
     *
     * <p>Reading the body is what settles a parameter, and what the body settles it to may be open:
     * {@code let has (xs, y) = List.contains(y, xs)} learns that {@code xs} holds whatever {@code y} is
     * and no more. That is an answer — it says the two are one thing — but it is the helper's answer
     * now, not the expansion's. A variable of one application decides once, when that application is
     * typed; a variable of a declaration decides at each call of it, which is what {@code has} needs.
     *
     * <p>One map for the whole helper, so a variable two parameters share stays one variable and
     * {@code has([ 1 ], "x")} is still refused. The spelling keeps the application it came from, so
     * two expansions the body made contribute two variables rather than colliding on {@code 'a}.
     */
    private static Type generalize(Type t, Map<Type.MetaVar, Type> generalized) {
        if (!Type.mentions(t, x -> x instanceof Type.MetaVar)) {
            return t;
        }
        Type.mentions(t, x -> {
            if (x instanceof Type.MetaVar m) {
                generalized.computeIfAbsent(m,
                        v -> Type.inferredVar(v.spelling() + "." + v.application()));
            }
            return false;   // a collector, not a test: every position is visited
        });
        return TypeOps.substituteMetas(t, generalized);
    }

    /**
     * The type {@code body} gives each of {@code open}, by index. A parameter a later round settles
     * can settle an earlier one — {@code f(x, y)} where {@code y}'s type follows from {@code x}'s —
     * so the rounds run to a fixpoint. {@code env} is completed as they are found, and
     * {@code openUses} collects, for each parameter still open, a use of it that named no type.
     *
     * <p>What a body gets wrong is not decided here. A type the rest of the body disagrees with is
     * reported by the standalone check that follows, at the position of the disagreement, so a
     * mistake beside an open element is reported as the mistake it is rather than as a parameter
     * nothing determined.
     */
    static Map<Integer, Type> determine(Hir.FnDef h, List<Integer> open, Scope env,
                                        Hir.Expr body, Symbols symbols,
                                        PublishedDeclarations published, DeclarationKinds kinds,
                                        Map<ValueName.Behavior, ReqSig> reqSigs,
                                        Map<String, Type> recursiveHelperFns,
                                        Map<Integer, OpenUse> openUses) {
        BodyTyping typing =
                new BodyTyping(symbols, published, kinds, reqSigs, recursiveHelperFns);
        Type answers = declaredReturn(h);
        Map<Integer, Type> found = new LinkedHashMap<>();
        List<Integer> value = new ArrayList<>();
        for (int idx : open) {
            // a function-typed parameter is annotated, not settled (spec §fn-declaration); reading a value type
            // off one would hide the report that says so.
            if (!isApplied(body, h.params().get(idx).binder())) {
                value.add(idx);
            }
        }
        boolean progress = true;
        while (progress) {
            progress = false;
            for (int idx : value) {
                Hir.Binder param = h.params().get(idx).binder();
                if (env.holds(param.id())) {
                    continue;
                }
                Type t = typing.typeOf(param, body, env, answers, othersOf(h, idx, env));
                if (t == null) {
                    openUses.put(idx, typing.openUse());
                } else {
                    env = env.with(param, t);
                    found.put(idx, t);
                    progress = true;
                }
            }
        }
        // What the readings settled about a variable may be settled while a later parameter is read,
        // so what each of them is, is asked for once every parameter is in. `bothContain (xs, ys, y)`
        // learns that the two calls' elements are one thing only while `y` is being read, and `xs`
        // was answered before that.
        Map<Integer, Type> settled = new LinkedHashMap<>();
        for (Map.Entry<Integer, Type> e : found.entrySet()) {
            settled.put(e.getKey(), typing.asSettled(e.getValue()));
        }
        return settled;
    }

    /**
     * What every parameter of {@code h} other than {@code idx} is known to be — written beside it or
     * settled in an earlier round. A variable standing in one of these is one the body has tied to a
     * value this walk is not working out, which is what makes it evidence rather than the question.
     */
    private static List<Type> othersOf(Hir.FnDef h, int idx, Scope env) {
        List<Type> others = new ArrayList<>();
        for (int i = 0; i < h.params().size(); i++) {
            if (i == idx) {
                continue;
            }
            Type t = env.typeOf(h.params().get(i).binder().id());
            if (t != null) {
                others.add(t);
            }
        }
        return others;
    }

    /**
     * A use of a parameter that named no type, and how the body reached it. Reading a field off it
     * is one of the two ways a body can leave a parameter with nothing at all, and it is the one the
     * language decided elsewhere rather than one this rule could widen: reaching a type from a field
     * is a structural question a nominal model does not ask. The report says which it was.
     */
    record OpenUse(Hir.Var use, boolean readAField) {}


    /**
     * Whether {@code param} is applied in {@code e} — the shape only a function parameter has.
     */
    static boolean isApplied(Hir.Expr e, Hir.Binder param) {
        BindingId id = param.id();
        return exists(e, x -> x instanceof Hir.Apply call && refersTo(call.function(), id));
    }

    /** Whether {@code e} holds a use of {@code param} anywhere inside it. */
    static boolean mentions(Hir.Expr e, Hir.Binder param) {
        return mentions(e, param.id());
    }

    private static boolean mentions(Hir.Expr e, BindingId id) {
        return exists(e, x -> refersTo(x, id));
    }

    /**
     * Whether {@code e} is a use of the binding {@code id} — the binding it is, not the name it was
     * written with (ADR-0067). A binder that rebinds the name introduces another binding, so a
     * same-named lambda parameter, {@code let} or arm binding answers no without a walk that has to
     * know which node kinds bind: what the name resolved to already says it.
     */
    private static boolean refersTo(Hir.Expr e, BindingId id) {
        return e instanceof Hir.Var.Denoting v
                && v.denotes() instanceof ValueName.Local local && local.id().equals(id);
    }

    /** Whether anything inside {@code e}, or {@code e} itself, satisfies {@code leaf}. */
    private static boolean exists(Hir.Expr e, Predicate<Hir.Expr> leaf) {
        if (leaf.test(e)) {
            return true;
        }
        boolean[] found = {false};
        TypeChecker.forEachChild(e, c -> {
            if (!found[0]) {
                found[0] = exists(c, leaf);
            }
        });
        return found[0];
    }

    /** The return type {@code h} declares, or null where it declares none or names something unknown. */
    private static Type declaredReturn(Hir.FnDef h) {
        if (h.declaredReturn() == null) {
            return null;
        }
        try {
            return TypeOps.successType(h.declaredReturn());
        } catch (CompileException _) {
            return null;   // a return type that does not resolve; the check reports it
        }
    }

    /**
     * One parameter's type read out of the helper's body. The whole body is read, so a position after
     * a use that named no type still determines it; the order the body is written decides only which
     * of several determining positions wins.
     *
     * <p>It answers with a type or with nothing; it never reports a type error. A type the rest of the
     * body disagrees with is reported by the standalone check that follows, at the position of the
     * disagreement, so type errors keep coming from one place ({@link Elaborator}). Where nothing
     * names a type, {@link #openUse()} is a use that named none, for the report to point at.
     */
    private static final class BodyTyping {
        private final Symbols symbols;
        /** What the declarations this body names say about themselves. */
        private final PublishedDeclarations published;
        /** Which form each of those declarations was written in. */
        private final DeclarationKinds kinds;
        private final CheckContext ctx;
        private final Map<ValueName.Behavior, ReqSig> reqSigs;
        private final Map<String, Type> recursiveHelperFns;
        /** What each call in this body has decided for the variables its callee left open. One
         * decision per call, read by every parameter that reaches it — and by the walk one step
         * inside a closure, which reads calls of the same body. */
        private final Freshening freshening;
        private Type pinned;
        /** Whether the position now being read is a field read off its child. */
        private boolean readingAField;
        /** What every other parameter of this helper is known to be, for {@link #saysWhatAnotherHolds}. */
        private List<Type> otherParameters = List.of();
        /** Every reading of a parameter that leaves what it holds open, and what they settle. One
         * for the body: the shape is one parameter's, and what it settles about a variable is every
         * parameter's, because they are read in one body. */
        private final Readings readings = new Readings();
        private OpenUse openUse;

        BodyTyping(Symbols symbols, PublishedDeclarations published, DeclarationKinds kinds,
                   Map<ValueName.Behavior, ReqSig> reqSigs, Map<String, Type> recursiveHelperFns) {
            this(symbols, published, kinds, reqSigs, recursiveHelperFns, new Freshening());
        }

        /** The walk one step inside a closure reads the calls of the same body, so what those calls
         * decided is the same decision. Deciding again would name two applications alike — the count
         * a name carries starts over with the reader — and unifying them would say the two hold one
         * thing. */
        BodyTyping(Symbols symbols, PublishedDeclarations published, DeclarationKinds kinds,
                   Map<ValueName.Behavior, ReqSig> reqSigs, Map<String, Type> recursiveHelperFns,
                   Freshening freshening) {
            this.freshening = freshening;
            this.symbols = symbols;
            this.published = published;
            this.kinds = kinds;
            this.ctx = new CheckContext(symbols, published, kinds, null, reqSigs);
            this.reqSigs = reqSigs;
            this.recursiveHelperFns = recursiveHelperFns;
        }

        /**
         * The type {@code body} gives {@code target}, or null when it gives none. {@code answers} is
         * what the body as a whole is asked for — the helper's declared return type, where it wrote
         * one — so a body that answers the parameter itself takes the type written beside it.
         *
         * <p>A type stated outright wins over one that leaves what the value holds open, wherever
         * either was found: the walk keeps going past an open answer, and takes it only where nothing
         * states the whole type. Two open answers that do not agree leave the parameter as open as it
         * was — the body asked for two different things and neither is what it is.
         */
        Type typeOf(Hir.Binder target, Hir.Expr body, Scope env, Type answers) {
            return typeOf(target, body, env, answers, List.of());
        }

        Type typeOf(Hir.Binder target, Hir.Expr body, Scope env, Type answers, List<Type> others) {
            this.otherParameters = others;
            this.pinned = null;
            this.readings.forParameter();
            this.openUse = null;
            // A recursive helper's call is left standing rather than expanded, so the neighbouring
            // expression a parameter takes its type from can be one — `x + count(t)` reads `count(t)`
            // to type `x`. Its signature goes in here, once, and every inner scope is derived from
            // this one (spec §fn-declaration). What is bound wins over it, as it does everywhere else.
            visit(body, env.reaching(recursiveHelperFns), target.id(), answers);
            // What the readings settle is asked for once, here: a reading that says what a variable
            // is may arrive after the readings that used it, so nothing before this is the answer.
            return pinned != null ? pinned : readings.answer();
        }

        /**
         * One answer that leaves what the value holds open. Where the walk already has one, the two
         * are merged rather than the first winning: they are two readings of one parameter, so what
         * one of them states and the other leaves open is stated. Where they disagree about what the
         * value is, the parameter has no answer here. What settles them is unification, done locally
         * and symmetrically: nothing it settles reaches past this parameter, so a set of readings
         * that does not go through leaves nothing behind.
         */
        private void offer(Type t) {
            readings.add(t);
        }

        /** {@code t} with what the readings of this body have settled written through it. */
        Type asSettled(Type t) {
            return readings.asSettled(t);
        }

        /** A use of the parameter that named no type, from the last {@link #typeOf} that found none. */
        OpenUse openUse() {
            return openUse;
        }

        /**
         * Reads {@code e} for the type it gives {@code target}. {@code expected} is what this position
         * is asked for, which every arm passes on in its own terms: an operand names it for the
         * operand beside it, an arm for its sibling arms, a call for its arguments. A use of the
         * parameter standing at a position that names a type is what settles it, so the same rule
         * answers a bare use, a use one arm down, and a use inside a construction.
         */
        private void visit(Hir.Expr e, Scope env, BindingId target, Type expected) {
            if (pinned != null || !mentions(e, target)) {
                return;
            }
            switch (e) {
                case Hir.Var v when refersTo(v, target) -> {
                    pin(expected);
                    if (pinned == null) {
                        // The use to point at is the first one; whether the body only ever read a
                        // field off the parameter is decided by all of them, because that is what
                        // makes reading a field the reason rather than one of the things it did.
                        openUse = openUse == null ? new OpenUse(v, readingAField)
                                : new OpenUse(openUse.use(), openUse.readAField() && readingAField);
                    }
                }
                case Hir.LetIn li -> visitLet(li, env, target, expected);
                case Hir.Expansion ex -> visitExpansion(ex, env, target, expected);
                case Hir.Binary bin -> {
                    visitOperand(bin.left(), bin.right(), bin.op(), false, env, target);
                    visitOperand(bin.right(), bin.left(), bin.op(), true, env, target);
                }
                case Hir.If iff -> {
                    visit(iff.cond(), env, target, Type.BOOL);
                    visitShared(readings(List.of(iff.then(), iff.els()), env), target, expected);
                }
                case Hir.Match m -> visitMatch(m, env, target, expected);
                case Hir.ListLit list -> visitShared(readings(list.elements(), env), target,
                        expected instanceof Type.ListOf l ? l.element() : null);
                // A row's brackets never reach a helper's parameter inference: a helper is written in
                // the module and a row is not one of its call sites. Walked all the same, so that what
                // is inside is not skipped by a reader that only knows one of the two spellings.
                case Hir.RowCollection row -> visitShared(readings(row.elements(), env), target,
                        expected instanceof Type.ListOf l ? l.element() : null);
                case Hir.Apply call -> visitArgs(call, env, target, expected);
                case Hir.NewData nd -> visitInits(nd, env, target);
                case Hir.IfConstructed ic -> visitAttempt(ic, env, target, expected);
                case Hir.ListComp comp -> {
                    for (Hir.Expr guard : comp.guards()) {
                        visit(guard, env, target, Type.BOOL);
                    }
                    visit(comp.element(), env, target,
                            expected instanceof Type.ListOf list ? list.element() : null);
                }
                case Hir.Tuple tuple -> visitTuple(tuple, env, target, expected);
                case Hir.Block block -> {
                    // A closure is read against what it was handed: its parameters are in force over
                    // its body at the types the position gives them, and its body answers the result.
                    Type.FnOf want = expected instanceof Type.FnOf fn
                            && fn.params().size() == block.params().size() ? fn : null;
                    visit(block.body(), want == null ? env : walking(env, block, want), target,
                            want == null ? null : want.result());
                }
                // The operand of a unary minus is the number the whole expression is, so what the
                // position asks of one it asks of the other.
                case Hir.Neg neg -> visit(neg.operand(), env, target, expected);
                // A field is read off a value, and reading it says nothing about what that value is;
                // an element is taken out of a tuple the same way. Neither asks its child anything.
                // Which of the two a use was reached by is remembered, because reading a field is a
                // question a nominal model does not ask, and the report says so.
                case Hir.FieldAccess fa -> {
                    boolean outer = readingAField;
                    readingAField = true;
                    visit(fa.target(), env, target, null);
                    readingAField = outer;
                }
                case Hir.TupleGet tg -> visit(tg.tuple(), env, target, null);
                // Nothing inside to ask: a literal has no children, and `unreachable` carries a
                // reason rather than an expression.
                case Hir.IntLit _, Hir.DecimalLit _, Hir.StringLit _, Hir.BoolLit _,
                        Hir.Unreachable _, Hir.Var _ -> { }
            }
        }

        /**
         * The elements of a tuple, each asked for what the position gives that element. A tuple type
         * reaches its elements one by one, so a written {@code (String, Int)} says what the second
         * element is without anything else in the body saying it.
         */
        private void visitTuple(Hir.Tuple tuple, Scope env, BindingId target, Type expected) {
            List<Type> want = expected instanceof Type.TupleOf te
                    && te.elements().size() == tuple.elements().size() ? te.elements() : null;
            for (int i = 0; i < tuple.elements().size(); i++) {
                visit(tuple.elements().get(i), env, target, want == null ? null : want.get(i));
                if (pinned != null) {
                    return;
                }
            }
        }

        /**
         * An attempted construction: its arms answer what the whole attempt answers, and the success
         * arm reads the value that was built at the type it was built as. The construction itself is
         * asked nothing — what it builds is written on it.
         */
        private void visitAttempt(Hir.IfConstructed ic, Scope env, BindingId target, Type expected) {
            visit(ic.construct(), env, target, null);
            if (pinned != null) {
                return;
            }
            // the value the attempt built is in force over the success arm, at the type it was built as
            Scope built = ic.construct() instanceof Hir.NewData nd
                    && nd.typeName() instanceof Hir.Name.Denoting names
                    ? env.with(ic.binder(), Type.ref(names.type())) : env;
            List<Reading> arms = new ArrayList<>();
            arms.add(new Reading(ic.then(), built));
            for (Hir.ElseArm arm : ic.els()) {
                arms.add(new Reading(arm.body(), env));
            }
            visitShared(arms, target, expected);
        }

        /**
         * One operand of an operator, asked for what the operator asks of it. The two operands are not
         * always one type — scaling a numeric newtype asks the other side for the base it wraps — so
         * what is asked is answered where that rule is stated rather than restated here.
         */
        private void visitOperand(Hir.Expr operand, Hir.Expr beside, BinOp op, boolean onTheRight,
                                  Scope env, BindingId target) {
            if (pinned != null || !mentions(operand, target)) {
                return;
            }
            visit(operand, env, target, BinaryElaborator.operandBeside(
                    op, typed(beside, env), onTheRight, symbols));
        }

        /** An expression and the scope it is read in — an arm carries what its own pattern binds. */
        private record Reading(Hir.Expr expr, Scope scope) {}

        /** {@code expressions} read in one scope, in the order they are written. */
        private static List<Reading> readings(List<Hir.Expr> expressions, Scope scope) {
            List<Reading> out = new ArrayList<>();
            for (Hir.Expr e : expressions) {
                out.add(new Reading(e, scope));
            }
            return out;
        }

        /**
         * Reads positions that answer one type — the operands of an operator, the arms of an {@code if}
         * or a {@code match} or an attempted construction, the elements of a collection literal. Each is
         * asked for what the enclosing position states, and otherwise for what one of the others
         * answers: they share a type, so one of them answering is the answer for all of them.
         *
         * <p>What counts as answering is a type the parameter could take, which is the same question
         * asked of the answer itself. A position holding the parameter is not asked — this scope cannot
         * type it, which is the question being worked out — and one that answers no type does not end
         * the search: an empty collection and an {@code unreachable} stand in a position without saying
         * what it holds, and the position after them may say it.
         */
        private void visitShared(List<Reading> readings, BindingId target, Type expected) {
            if (readings.stream().noneMatch(r -> mentions(r.expr(), target))) {
                return;
            }
            // A type stated outright is the answer wherever it comes from, and only where nothing
            // states one does a reading that says what the value is and leaves what it holds open
            // become it. The enclosing position and the arms beside this one are both asked, in that
            // order, so `List.length(if b then xs else [1, 2, 3])` takes the `List<Int>` the other arm
            // states and `List.length(if b then xs else [])` takes the List the position asks for.
            Type answers = firstAnswer(expected, readings, target);
            for (Reading r : readings) {
                if (mentions(r.expr(), target)) {
                    visit(r.expr(), r.scope(), target, answers);
                    if (pinned != null) {
                        return;
                    }
                }
            }
        }

        /**
         * What positions answering one type answer, taking a stated answer over one that leaves what
         * the value holds open. Both the enclosing position and the arms beside the parameter are
         * asked; an arm holding the parameter is not, because this scope cannot type it.
         */
        private Type firstAnswer(Type expected, List<Reading> readings, BindingId target) {
            if (settles(expected)) {
                return expected;
            }
            Type stated = answered(readings, target, this::settles);
            if (stated != null) {
                return stated;
            }
            // An arm is asked before the position, because an arm that answers a variable already
            // attached to another parameter says these positions hold one thing, where the position
            // has only just minted one for this parameter and says nothing about the arms.
            Type carried = answered(readings, target, this::determinesOuterType);
            return carried != null ? carried : determinesOuterType(expected) ? expected : null;
        }

        /** The type one of {@code readings} answers that {@code takes} accepts, or null. */
        private Type answered(List<Reading> readings, BindingId target, Predicate<Type> takes) {
            for (Reading r : readings) {
                if (!mentions(r.expr(), target)) {
                    Type t = typed(r.expr(), r.scope());
                    if (takes.test(t)) {
                        return t;
                    }
                }
            }
            return null;
        }

        /**
         * A {@code let} demands a type of its value: the one written on it, or the callee's declared
         * parameter type, which the inliner carries onto the binding a helper call becomes. Where it
         * demands none, the constraint passes along the binding: {@code let y = x in y * 2} types
         * {@code x} through {@code y}, which is the shape a call to another body-typed helper inlines
         * to. A binding spelled like the parameter is another binding, and reads as one.
         *
         * <p>What the binding demands is read as it stands. The type on a binding a helper's
         * expansion writes is the callee's signature with what that application decided already
         * written into it, so two bindings of one expansion demand one variable and two expansions
         * demand two.
         */
        private void visitLet(Hir.LetIn li, Scope env, BindingId target, Type expected) {
            Type demanded = li.declaredType() == null ? null
                    : TypeOps.resolveParamType(li.declaredType());
            if (isParam(li.value(), target)) {
                pin(demanded);
                if (pinned != null) {
                    return;
                }
                OpenUse use = openUse;
                visit(li.body(), env, li.binder().id(), expected);   // the binding stands for the parameter
                openUse = use;                      // its uses are the binding's, not the parameter's
                if (pinned != null) {
                    return;
                }
            }
            visit(li.value(), env, target, demanded);
            if (pinned != null) {
                return;
            }
            visit(li.body(), bound(li, demanded, env), target, expected);
        }

        /**
         * An expansion demands of each argument what the callee declared for it. The declarations
         * arrive already instantiated into that one application's variables, so a variable the
         * signature wrote in two places is one variable here and a reading of either settles both —
         * which is what makes {@code let has (xs, y) = List.contains(y, xs)} say that {@code y} is an
         * element of {@code xs}.
         *
         * <p>The body is read for what the expansion as a whole is read for. Not for the callee's
         * declared result: this walk asks what a position demands of the parameter, and a result the
         * callee declared demands nothing of it — it is what the callee promises its caller. Reading
         * the body against it settles the parameter by a type the call site never asked for.
         */
        private void visitExpansion(Hir.Expansion ex, Scope env, BindingId target, Type expected) {
            Substitution decided = fromGiven(ex, env);
            List<Type> demands = new ArrayList<>();
            for (Hir.Bound b : ex.bound()) {
                demands.add(b.declaredType() == null ? null
                        : decided.zonk(TypeOps.resolveParamType(b.declaredType())));
            }
            Type answers = expected;
            for (int i = 0; i < ex.bound().size(); i++) {
                Hir.Bound b = ex.bound().get(i);
                if (isParam(b.value(), target)) {
                    pin(demands.get(i));
                    if (pinned != null) {
                        return;
                    }
                    OpenUse use = openUse;
                    // the binding stands for the parameter, so it is what the body is read for — and
                    // it is left out of the scope, or its uses would be answered by its own type
                    visit(ex.body(), inForce(ex, demands, env, i), b.binder().id(), answers);
                    openUse = use;   // its uses are the binding's, not the parameter's
                    if (pinned != null) {
                        return;
                    }
                }
                visit(b.value(), env, target, demands.get(i));
                if (pinned != null) {
                    return;
                }
            }
            visit(ex.body(), inForce(ex, demands, env, -1), target, answers);
        }

        /**
         * What the functions this call was given decide about the callee's variables.
         *
         * <p>A signature relates a function parameter to a value parameter — {@code witness (f: ('a)
         * -> Bool, x: 'a)} says {@code x} is what {@code f} takes — and a function argument leaves no
         * binding for that to survive on. Where the callee applies it, the relation is reproduced by
         * that application and this finds nothing new. Where it does not, this is the only reader
         * that has both the lambda and what the signature said about it (issue #320).
         *
         * <p>The lambda is read the way a helper's own parameters are: its body says what its
         * parameters are, one step in. What it decides is recorded through the same
         * {@link Substitution} the check uses, asked in its inference-only form — this walk works
         * out what a type is and reports nothing, and a disagreement it meets is the caller's error,
         * reported where the check reports one.
         */
        private Substitution fromGiven(Hir.Expansion ex, Scope env) {
            Substitution decided = new Substitution();
            for (Hir.Given g : ex.given()) {
                if (g.declaredType() == null
                        || !(TypeOps.resolveParamType(g.declaredType()) instanceof Type.FnOf step)
                        || !(g.value() instanceof Hir.Block lambda)
                        || lambda.params().size() != step.params().size()) {
                    continue;
                }
                Scope inner = walking(env, lambda, step);
                try {
                    for (int i = 0; i < lambda.params().size(); i++) {
                        Type t = new BodyTyping(symbols, published, kinds, reqSigs, recursiveHelperFns, freshening)
                                .typeOf(lambda.params().get(i), lambda.body(), inner, step.result());
                        if (t != null   // a position the lambda's body leaves open says nothing
                                && decided.decide(step.params().get(i), t, published)
                                        instanceof Fit.Disagrees) {
                            return new Substitution();   // it does not agree; settle nothing from it
                        }
                    }
                    // What it answers is a position of the signature too. `(f: (Int) -> 'a, x: 'a)`
                    // relates the argument to what the function answers, and reading only what it
                    // takes would carry one of those and drop the other.
                    Type answers = typed(lambda.body(), walking(env, lambda,
                            decided.zonk(step) instanceof Type.FnOf f ? f : step));
                    if (answers != null
                            && decided.decide(step.result(), answers, published)
                                    instanceof Fit.Disagrees) {
                        return new Substitution();   // it does not agree; settle nothing from it
                    }
                } catch (CompileException _) {
                    return new Substitution();   // it could not be typed; settle nothing from it
                }
            }
            return decided;
        }

        /** {@code env} with every binding of the expansion in force but the one at {@code except}. */
        private Scope inForce(Hir.Expansion ex, List<Type> demands, Scope env, int except) {
            Scope inner = env;
            for (int i = 0; i < ex.bound().size(); i++) {
                if (i != except) {
                    inner = bound(ex.bound().get(i), demands.get(i), inner);
                }
            }
            return inner;
        }

        /**
         * {@code env} with what the binding is in force as. The written type says it, except where it
         * names a type variable: the binding a helper's expansion writes carries the callee's declared
         * type, so a combinator's {@code List<'a>} would stand in front of the {@code List<Int>} the
         * argument actually is. A variable names nothing (spec §fn-declaration), so what the value is wins there.
         */
        private Scope bound(Hir.LetIn li, Type demanded, Scope env) {
            return bound(li.binder(), li.declaredType(), li.value(), demanded, env);
        }

        private Scope bound(Hir.Bound b, Type demanded, Scope env) {
            return bound(b.binder(), b.declaredType(), b.value(), demanded, env);
        }

        private Scope bound(Hir.Binder binder, Hir.RetType declared, Hir.Expr value, Type demanded,
                            Scope env) {
            Type type = demanded;
            if (type == null || Type.mentions(type, x -> x instanceof Type.Open)) {
                Type carried = carried(declared, value, env);
                if (carried != null) {
                    type = carried;
                }
            }
            return type == null ? env : env.with(binder, type);
        }

        /** The type a binding carries into its body, or null where this scope cannot type its value. */
        private Type carried(Hir.RetType declared, Hir.Expr value, Scope env) {
            Type is = typed(value, env);
            return is == null ? null : Elaborator.carriedType(declared, is, kinds, published);
        }

        /**
         * Each arm of a {@code match}, asked for what the match as a whole is asked for and otherwise
         * for what a sibling arm answers — the arms have one type. An arm is read in a scope its own
         * binding is in force over, which is what the scrutinee's type gives: the case a named-sum arm
         * binds, or the element an {@code Option} arm unwraps.
         */
        private void visitMatch(Hir.Match m, Scope env, BindingId target, Type expected) {
            visit(m.scrutinee(), env, target, null);
            if (pinned != null) {
                return;
            }
            Type scrutinee = typed(m.scrutinee(), env);
            List<Reading> arms = new ArrayList<>();
            for (Hir.Case c : m.cases()) {
                arms.add(new Reading(c.body(), armScope(env, c, scrutinee)));
            }
            visitShared(arms, target, expected);
        }

        /** {@code env} with what arm {@code c} binds, where the scrutinee's type says what that is. */
        private Scope armScope(Scope env, Hir.Case c, Type scrutinee) {
            if (c.binding() == null || c.caseTypes().size() != 1) {
                return env;
            }
            if (c.caseTypes().get(0).answered() == null) {
                return env;   // it names no case, so it binds nothing this can say the type of
            }
            TypeSymbol arm = c.caseTypes().get(0).answered().type();
            // What the case refines the value to, asked of the subject's cases rather than worked
            // out from the subject's shape a second time.
            ResolvedCase selected =
                    CaseSpace.of(scrutinee, kinds, published).selector(arm, published);
            Type bound = selected == null ? null : selected.bound();
            return bound == null ? env : MatchElaborator.bound(env, c.binding(), bound);
        }

        /**
         * Each argument of a call, asked for the type the callee declares of that argument. Where the
         * declaration carries type variables, they are solved at this call first — from the arguments
         * that do not hold the parameter and from what the call as a whole is asked for — so a
         * parameter passed where a signature says {@code 'a} takes what {@code 'a} turned out to be.
         * Nothing is reported: a call the checker will refuse leaves the variables unsolved, and the
         * parameter stays as open as it was.
         */
        private void visitArgs(Hir.Apply call, Scope env, BindingId target, Type expected) {
            Type.FnOf sig = calleeSignature(call, env);
            List<Type> params = sig == null || sig.params().size() != call.args().size()
                    ? null : solved(call, sig, env, target, expected);
            for (int i = 0; i < call.args().size(); i++) {
                // a closure argument is asked for the declared parameter type like any other, and
                // reading a closure against what it was handed is what that asking means
                visit(call.args().get(i), env, target, params == null ? null : params.get(i));
                if (pinned != null) {
                    return;
                }
            }
            visit(call.function(), env, target, null);
        }

        /**
         * The signature's variables that a closure argument settles. A combinator names its element only
         * through the closure it hands it to — nothing else in {@code List.map((v) -> v * 2, xs)} says
         * what {@code xs} holds — so the closure's parameters are read the way a helper's are, by the
         * same rule one step in. Only the positions its body determines are answered; the rest stay
         * the variables the signature wrote, which unify against anything.
         */
        private Fit solveFromClosure(Hir.Expr arg, Type.FnOf step, Scope env,
                                     Map<String, Type> bind) {
            if (!(arg instanceof Hir.Block lambda)
                    || lambda.params().size() != step.params().size()) {
                return Fit.FITS;
            }
            // What the arguments beside it have settled, as the closure sees it: its parameters are
            // in force over its body at those types, and its result is what the body is asked for.
            // `List.find((v) -> v, xs)` says the body answers a Bool, which is what settles `v` —
            // and `v` is the element, so it settles what `xs` holds.
            Type.FnOf known = TypeOps.substitute(step, bind) instanceof Type.FnOf f ? f : step;
            Scope inner = walking(env, lambda, known);
            for (int i = 0; i < lambda.params().size(); i++) {
                Type t = new BodyTyping(symbols, published, kinds, reqSigs, recursiveHelperFns, freshening)
                        .typeOf(lambda.params().get(i), lambda.body(), inner, known.result());
                if (t != null) {
                    // only a position the closure's body settled: unifying an undetermined one
                    // against the variable the signature wrote would bind that variable to itself
                    Fit fit = TypeOps.unify(step.params().get(i), t, bind, published);
                    if (fit instanceof Fit.Disagrees) {
                        return fit;
                    }
                }
            }
            return Fit.FITS;
        }

        /**
         * {@code env} with the lambda's own parameters bound to the types the call gives them. A
         * combinator says what it hands its closure — {@code List.map} over a {@code List<Int>} walks
         * Ints — so an operand beside the lambda's parameter is a value of known type, as it is
         * anywhere else. A parameter the call does not settle (an accumulator seeded with {@code []})
         * is left unbound rather than bound to a bottom nothing follows from.
         */
        private Scope walking(Scope env, Hir.Block lambda, Type.FnOf step) {
            Scope walked = env;
            for (int i = 0; i < lambda.params().size() && i < step.params().size(); i++) {
                Type t = step.params().get(i);
                if (settles(t)) {
                    walked = walked.with(lambda.params().get(i), t);
                }
            }
            return walked;
        }

        /**
         * {@code sig}'s parameter types with the variables this call site settles substituted in, and
         * the ones it does not settle minted as this parameter's own ({@link Freshening}) — the
         * declaration wrote them, so they are the declaration's rather than anything this body links.
         */
        private List<Type> solved(Hir.Apply call, Type.FnOf sig, Scope env, BindingId target,
                                  Type expected) {
            if (!Type.mentions(sig, x -> x instanceof Type.Open)) {
                return sig.params();
            }
            Map<String, Type> bind = new HashMap<>();
            try {
                if (determinesOuterType(expected)) {
                    // tolerantly: a result that does not fit leaves what the arguments settled
                    // standing, as it does where a call is typed for real. An expectation that
                    // carries a variable this walk minted says as much as a stated one: the variable
                    // is this parameter's, so solving the callee's against it is what links the two
                    // positions the body read together.
                    BottomInfer.pinResultTypeVars(sig.result(), expected, bind, published);
                }
                // The stages `CallElaborator.applySignature` types a call in, in that order and
                // for its reason: an argument that is not a closure binds the variables, and the
                // closure is read against what they turned out to be — `List.fold((acc, x) -> acc + x,
                // 0, xs)` reads its step knowing the seed said `acc` is an Int.
                for (int i = 0; i < call.args().size(); i++) {
                    Hir.Expr arg = call.args().get(i);
                    Type param = sig.params().get(i);
                    if (param instanceof Type.FnOf || mentions(arg, target)) {
                        continue;   // a closure waits for the stage below; this argument is the one
                    }               // being typed, and typing it is the question being worked out
                    Type actual = typed(arg, env);
                    if (actual != null
                            && TypeOps.unify(param, actual, bind, published) instanceof Fit.Disagrees) {
                        return sig.params();   // the call does not fit; the check reports it
                    }
                }
                for (int i = 0; i < call.args().size(); i++) {
                    Hir.Expr arg = call.args().get(i);
                    if (sig.params().get(i) instanceof Type.FnOf step && !mentions(arg, target)
                            && solveFromClosure(arg, step, env, bind) instanceof Fit.Disagrees) {
                        return sig.params();   // the call does not fit; the check reports it
                    }
                }
            } catch (CompileException _) {
                return sig.params();   // an argument could not be typed; the check reports it
            }
            // What this call decided for the variables it left open, and over it what this walk
            // solved. The decision belongs to the call, so every parameter reading it reads the same
            // one, and what the call solved outright wins over what it only named.
            Map<String, Type> decided = freshening.of(call, sig.params());
            Map<String, Type> settled = bind;
            if (!decided.isEmpty()) {
                settled = new HashMap<>(decided);
                settled.putAll(bind);
            }
            List<Type> out = new ArrayList<>();
            for (Type param : sig.params()) {
                out.add(TypeOps.substitute(param, settled));
            }
            return out;
        }

        /** Each field of a construction, asked for the type that field holds. */
        private void visitInits(Hir.NewData nd, Scope env, BindingId target) {
            Hir.Data data = nd.typeName() instanceof Hir.Name.Denoting names
                    && symbols.declaredNode(names.type()) instanceof Hir.Data d
                    ? d : null;
            for (Hir.FieldInit init : nd.inits()) {
                visit(init.value(), env, target,
                        data == null ? null : TypeOps.fieldType(data, init.name(), symbols));
                if (pinned != null) {
                    return;
                }
            }
        }

        /**
         * The parameter types {@code call}'s callee declares, or null when nothing here declares them.
         * What is applied may be something this body binds — a function-typed parameter, which is
         * always written (spec §fn-declaration), or a binding holding a function — and a written type is a
         * declaration wherever it stands, so it is read first. Beyond that: a helper that annotates its
         * parameters has already been inlined into this body, where its annotation is on the binding
         * the call became, and a newtype's constructor {@code X(v)} has already been desugared to a
         * construction; what is left to read is an injected behavior, a recursive helper and an
         * intrinsic. A built-in that is neither an intrinsic nor a self-hosted helper states its
         * parameter types only inside {@link CallElaborator}, so an argument of one is not read here —
         * the parameter is annotated instead.
         */
        private Type.FnOf calleeSignature(Hir.Apply call, Scope env) {
            // What is applied names no declaration, so there is no signature to read: the name is
            // reported where it is written, and the argument is left to be read on its own.
            if (call.answered() == null) {
                return null;
            }
            if (call.answered().denotes() instanceof ValueName.Local local) {
                // What is applied is this binding, whatever else carries its spelling. A declaration
                // of the same name is a different thing, so where the scope cannot say what the
                // binding holds, nothing here can.
                return env.typeOf(local.id()) instanceof Type.FnOf sig ? sig : null;
            }
            String fn = call.answered().reaches();
            // Asked of the declaration: two modules may declare a behavior of one name, and what is
            // applied here is the one this call was resolved to.
            if (call.answered().denotes() instanceof ValueName.Behavior behavior) {
                ReqSig req = reqSigs.get(behavior);
                if (req != null) {
                    return new Type.FnOf(req.params(), req.success());
                }
            }
            if (recursiveHelperFns.get(fn) instanceof Type.FnOf sig) {
                return sig;
            }
            // Only a kernel's signature: a Souther-bodied library callee here is a recursive
            // helper, and those are answered above with the types their call site instantiated.
            // Whether the name is a kernel is a fact about the library, asked of it and asked with
            // the operation this call was resolved to rather than with the name it renders as.
            if (!(call.answered().denotes() instanceof ValueName.Stdlib.Operation operation)) {
                return null;
            }
            Stdlib.Intrinsic kernel = symbols.library().intrinsicOf(operation);
            if (kernel == null) {
                return null;
            }
            return new Type.FnOf(kernel.signature().parameters(), kernel.signature().result());
        }

        private boolean isParam(Hir.Expr e, BindingId target) {
            return refersTo(e, target);
        }

        /**
         * {@code t} taken as the parameter's type. A type that states one outright is the answer and
         * ends the walk. One whose outer constructor the body decided and whose inside it left open
         * is kept aside: the walk goes on, because a position further down may state the whole type,
         * and a concrete answer is always preferred to an open one.
         */
        private void pin(Type t) {
            if (settles(t)) {
                pinned = t;
            } else if (determinesOuterType(t)) {
                offer(t);
            }
        }

        /**
         * Whether {@code t} is a type the parameter can take: not a function type (which must be
         * written), not a signature's type variable, and not one that answers no value — the bottom
         * an empty collection carries, the {@code Never} an {@code unreachable} arm answers with, the
         * type a reported error stands in for. Each of those leaves the parameter as open as it was:
         * an arm determines its sibling only where it answers a value (ADR-0066), and `Never` is
         * exactly the arm that answers none.
         */
        private boolean settles(Type t) {
            return determinesOuterType(t) && !Type.mentions(t, x -> x instanceof Type.Open);
        }

        /**
         * Whether {@code t} says what the value is, with only what it holds left open — its outermost
         * layer denotes a type constructor, and nothing inside it answers no value.
         *
         * <p>This is the one question, and {@link #settles} is it with what a value holds required to
         * be stated as well. Asking about the outermost layer is what tells the two apart: a variable
         * inside {@code List<'a>} leaves the element open, and a variable standing alone leaves
         * everything open. It is stated over what a type is rather than over a list of the
         * constructors there are, so a constructor added later means what this already says of it.
         *
         * <p>A function type is refused where the value is one: a parameter that is applied is written
         * (spec §fn-declaration). A collection of functions is not that — it is a value the expansion carries
         * like any other — so the question is asked of the outermost layer here as it is everywhere
         * else. A type that answers no value is refused at any depth, which is the existing rule
         * about the bottom an empty collection carries; that one says nothing about what it holds
         * either, and this is not the place to revisit it. The cheap question is asked first, because
         * a bare variable and a function type are the common answers here and neither needs a walk.
         */
        private boolean determinesOuterType(Type t) {
            if (t == null) {
                return false;
            }
            boolean outer = switch (t) {
                case Type.Open v -> saysWhatAnotherHolds(v);
                case Type.FnOf _, Type.Nothing _, Type.Never _, Type.Erroneous _ -> false;
                case Type.Prim _, Type.Ref _, Type.Union _, Type.ListOf _, Type.SetOf _,
                        Type.MapOf _, Type.OptionOf _, Type.TupleOf _ -> true;
            };
            return outer && !Type.mentions(t, BottomInfer::answersNoValue);
        }

        /**
         * Whether {@code v} standing alone says what this parameter holds: it stands in what another
         * parameter of this helper is known to be. That parameter's type was settled without reading
         * this one — a parameter is settled on its own, and an argument holding the parameter being
         * settled is not read — so it is evidence rather than the question asked back.
         *
         * <p>Where the variable stands is not enough on its own. A binding the body made from this
         * parameter carries it too, and taking that for evidence would let a parameter be settled by
         * what was built out of it: {@code let f (v) = let xs = [v] in List.contains(v, xs)} says
         * nothing about {@code v} that {@code v} did not say first. Only the other parameters count.
         *
         * <p>A variable the core wrote, and one standing only where this parameter is being worked
         * out, say nothing: {@code let id (v) = v} is open however the walk reached it.
         */
        private boolean saysWhatAnotherHolds(Type.Open v) {
            Type is = readings.asSettled(v);
            for (Type other : otherParameters) {
                if (Type.mentions(readings.asSettled(other), x -> x.equals(is))) {
                    return true;
                }
            }
            return false;
        }

        /** The type of a neighbouring expression, or null where this scope cannot type it. */
        private Type typed(Hir.Expr e, Scope env) {
            try {
                return Elaborator.typeOf(e, env, ctx);
            } catch (CompileException _) {
                return null;
            }
        }
    }
}
