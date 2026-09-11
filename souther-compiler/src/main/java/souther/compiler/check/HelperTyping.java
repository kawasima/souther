package souther.compiler.check;

import souther.compiler.stdlib.Stdlib;
import souther.compiler.ast.Hir;
import souther.compiler.ast.RowPosition;
import souther.compiler.core.CompleteSignature;
import souther.compiler.core.Core;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.msg.NameMessage;
import souther.compiler.diag.msg.HelperMessage;
import souther.compiler.diag.msg.InvariantMessage;
import souther.compiler.diag.msg.BehaviorMessage;
import souther.compiler.diag.Region;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.Type;
import souther.compiler.types.ValueName;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Deque;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Typing for {@code let} helpers: checking each one standalone against its parameter types, taking
 * the parameters that were left unwritten from the helper's own body, and working out what the
 * recursive ones construct.
 *
 * <p>A helper is typed by its body and never by its callers (spec §fn-declaration, issue #176), so what a
 * helper means is settled by reading the helper. A non-recursive helper is inlined into its caller,
 * so most of its checking happens there; what is here is what only makes sense about the helper on
 * its own.
 */
public final class HelperTyping {

    private HelperTyping() {}

    /**
     * Type-checks every helper fn standalone against its own declared parameter types (spec §fn-declaration).
     * Calls to other helpers in the body are expanded first, so what is left is builtins and
     * injected behaviors, which {@code reqSigs} resolves. The construction-permission and
     * {@code depends on} checks are the caller's (the helper is inlined there), so they are not
     * repeated here.
     */
    static void checkHelpers(HelperInliner inliner, Map<String, Hir.FnDef> toCheck, Symbols symbols,
                                     Map<ValueName.Behavior, ReqSig> reqSigs, Map<String, Type> recursiveHelperFns,
                                     Map<String, Hir.Expr> loweredBodies,
                                     TypeChecker.Elaborated elaborated) {
        // What each value of this module was settled as, filled in as they are checked. A value is
        // checked against these rather than against a copy of the body each of them stands for,
        // which is the same answer worked out once instead of once per name that reaches it.
        Preserved.Settling settledSignatures = new Preserved.Settling();
        Map<ValueName, Object> settledConstants = new HashMap<>();
        Preserved standing = Preserved.valuesAlreadySettled(settledSignatures);
        for (Hir.FnDef h : valuesBeforeTheValuesThatNameThem(inliner, symbols.library(), toCheck)) {
            boolean recursive = recursiveHelperFns.containsKey(h.name());
            // Where this definition stands, or null where it stands nowhere: the one thing every
            // rule below that is about a row's operand asks, read off the definition the rule is
            // holding rather than off a set of names travelling beside it.
            RowPosition standsAt = h.standsAt();
            // What a row operand may reach. A row supplies the values a behavior is applied to and
            // stands in for what that behavior depends on; it is not inside the application, so the
            // dependencies are not in force where it is computed — and the definition it is
            // compiled as is nullary and static, with nothing injected into it. Handed the same
            // requirement table a body gets, a call to a required behavior types here and reaches
            // no implementation at all.
            Map<ValueName.Behavior, ReqSig> reachable = standsAt != null ? Map.of() : reqSigs;
            // A helper reads a settled value as a value does. A helper's body is expanded into
            // whoever calls it, and a value it names is expanded into that expansion, so a chain of
            // values written through helpers reaches every link exactly as one written without them
            // — and would copy every link, once per helper, for the same reason.
            //
            // What settles one is narrower: a value of this module, which is what has an answer to
            // read. A helper settles nothing, and neither does a value the module took on to emit.
            boolean settles = h.params().isEmpty() && h.declaredBy(inliner.moduleName());
            ValueName settled = settles
                    ? new ValueName.Helper(inliner.moduleName(), h.name()) : null;
            Scope env = Scope.NONE;
            List<Integer> inferred = new ArrayList<>();
            for (int i = 0; i < h.params().size(); i++) {
                Hir.FnParam p = h.params().get(i);
                Elaborator.rejectBuiltinShadow(p.name(), p.pos());
                if (p.type() == null) {
                    if (recursive) {
                        // a recursive helper is lowered to a method, not inlined, so no call site
                        // expands it — its parameter types cannot be inferred and must be declared.
                        throw CompileException.of(Diagnostic.at(p.written().reportedAt())
                                .say(new HelperMessage.AParameterNeedsItsType(h.name(), p.name()))
                                .build());
                    }
                    // a parameter with no type beside it takes one from the body (spec §fn-declaration).
                    inferred.add(i);
                    continue;
                }
                env = env.with(p.binder(), TypeOps.resolveParamType(p.type()));
            }
            Elaborator.rejectBuiltinShadowing(h.writtenBody());
            // A definition the lowered module carries is one the backend emits — a recursive helper,
            // and a row's operand — so it is typed on the tree the backend emits from, and the Core
            // this check produces is what is emitted (issue #81). A helper that is only
            // inlined at its call sites has no body down there, so its standalone check expands its
            // body here; a recursive helper hides its own parameters from helper resolution while that
            // expansion runs (foldFrom's `step` is a parameter, not a same-named user helper).
            // Expanded once: the body an un-annotated parameter takes its type from is the same tree.
            Hir.Expr emitted = loweredBodies.get(h.name());
            // Not what the backend emits, and not a recursive helper's own body: a value is
            // substituted at each of its references, and those two are trees that run.
            boolean reading = emitted == null && !recursive;
            Hir.Expr body = emitted != null ? emitted
                    : inliner.readingSettledValues(reading ? standing : Preserved.NONE,
                                    reading ? settledConstants::get : _ -> null)
                            .inline(h.writtenBody(), inliner.bodyOf(h.name()));
            // Back for everything below, which reads a body with every value copied into it as it
            // always was: the arguments checked against the surface, and every other reader.
            inliner.readingSettledValues(Preserved.NONE, _ -> null);
            if (recursive && body == null) {
                // Lower keeps every recursive helper as a fn of the lowered module, and the backend
                // emits from that same list, so a recursive helper without a lowered body would leave
                // the backend a method to emit and no elaborated body to emit it from.
                throw new IllegalStateException(
                        "recursive helper `" + h.name() + "` has no lowered body");
            }
            if (!inferred.isEmpty()) {
                // Complete the env from the body, then run the same standalone check an annotated
                // helper gets — so a mis-declared return type or a mis-passed function argument in the
                // body is caught here, at the helper, not only where it is later inlined.
                typeFromBody(h, inferred, env, body, symbols, reachable, recursiveHelperFns);
            }
            // A recursive helper is lowered to a method, so a self- or mutual call is left standing
            // rather than expanded; its signature is what a call to it is typed against, so it goes
            // into the environment before anything reads the body (spec §fn-declaration). Every reader of the
            // body needs it, not just the elaboration below: a call to a method-lowered helper can sit
            // in a lambda handed to a combinator, and the check of that lambda reads the environment
            // it is given. A parameter of the same name wins: a binding in force wins over the
            // declaration it shadows (spec §fn-rules), so `let use (depth: Int)` reads its `depth` as
            // the Int it declares and not as the helper it is spelled like.
            Scope tenv = env.reaching(recursiveHelperFns);
            // a helper that returns a function (e.g. `let adder (n) = (x) -> x + n`) has no application
            // here to infer the lambda's parameter types from; it is checked where it is inlined and
            // applied (spec §blocks).
            checkFunctionArgs(h.writtenBody(), tenv, symbols, reachable, inliner);
            // push a declared return type into the body so an empty-collection body (Map.empty, [])
            // takes the declared element/value type rather than a bottom
            Type declaredReturn = h.declaredReturn() == null ? null : TypeOps.successType(h.declaredReturn());
            // Only one that declares nothing: a declaration is a claim about the body, so a body
            // that produces a function is elaborated against it and refused as a block is anywhere
            // it escapes — skipped, the claim would go unheld and the backend would be left a
            // method to emit with no elaborated body to emit it from.
            if (declaredReturn == null && Elaborator.producesFunction(body)) {
                continue;
            }

            if (recursiveHelperFns.containsKey(h.name())) {
                // a recursive helper is pure: it is a static method with no injected fields, so it
                // cannot reach an injected behavior — put the effect in the behavior that calls it.
                rejectInjectedCalls(body, h.name(), reqSigs.keySet());
            }
            Core elaboratedBody = Elaborator.elaborate(body, tenv,
                    new CheckContext(symbols, null, reachable)
                            .preserving(reading ? standing : Preserved.NONE),
                    declaredReturn);
            Type bodyType = elaboratedBody.type();
            // A definition standing at a row's position computes what the row writes there, and a
            // position holds a value. One that answers none — `unreachable` on its own — is refused
            // here, where the check fails the compile: the backend refuses the same shape when it
            // emits, but what ships carries no method for a row's operand, so a refusal left to
            // emission would surface in the evaluation build alone and fail nothing.
            //
            // Asked of the position and not of whether a source spelled the name. A definition
            // another module wrote is emitted here under the name this module reaches it by, which
            // no source spells either, and this rule refused it — for what a row in this module
            // said about it, at a line in a file that had written nothing wrong.
            if (standsAt != null && bodyType instanceof Type.Never
                    && (declaredReturn == null || standsAt.required() == null)) {
                throw CompileException.of(Diagnostic.at(h.pos(), "unreachable".length())
                        .hint(new NameMessage.WriteItWhereTheTypeIsStated())
                        .say(new NameMessage.NothingSaysWhatThisPositionHolds()).build());
            }
            elaborated.definitionTypes.put(h.name(), bodyType);
            if (settled != null) {
                // Both halves of what a reference to it is held to, said where both are in hand:
                // the empty parameter list is why this is a value at all, and the result is what
                // checking its body just answered. A reader given the type alone would have to
                // decide for itself that a value takes no arguments.
                settledSignatures.settled(CompleteSignature.ofSettledValue(settled, bodyType));
                // What it is a constant of, read off the body it was checked as. A reference to it
                // is written out as that constant, so every position that asks whether an
                // expression is known at compile time goes on reading a literal.
                ConstEval.against(symbols).eval(body)
                        .ifPresent(c -> settledConstants.put(settled, c));
            }
            if (emitted != null) {
                elaborated.helpers.put(h.name(), elaboratedBody);   // the backend emits this
            }
            // a declared return type — required on a recursive helper, allowed on any helper — must
            // match the body; a lying annotation is not silently ignored. What a row's operand
            // answers with is the position's contribution and not a claim of its own where the
            // position requires nothing: a row may state what the behavior does not answer with,
            // and reporting that disagreement is what the row is for.
            if (declaredReturn != null && (standsAt == null || standsAt.required() != null)) {
                Type declared = declaredReturn;
                if (!TypeOps.assignable(bodyType, declared, symbols)) {
                    // A definition standing at a position carries a claim the position made, so
                    // what is said leans on the place and quotes no name the author never wrote.
                    // A definition is named, whoever wrote it: one this module took on is another
                    // module's `let` and is named as one.
                    throw CompileException.of(Diagnostic.at(h.pos())
                            .say(standsAt == null
                                    ? new HelperMessage.TheBodyIsNotWhatTheHelperDeclares(h.name(),
                                            Type.show(declared), Type.show(bodyType))
                                    : new HelperMessage.WhatIsWrittenHereIsNotWhatItsPositionTakes(
                                            Type.show(declared), Type.show(bodyType)))
                            .build());
                }
            }
        }
    }

    /**
     * The declarations this pass checks, each value after the values it names.
     *
     * <p>A value is checked against what the values it names were settled as, so those have to be
     * settled first. The source may write them the other way round — nothing says a value must be
     * declared above the one that reads it — and the order they are checked in is not the order
     * they are written in for that reason alone.
     *
     * <p>Reaching is not only writing the name. A value reaches another by reading it and by
     * calling a helper that reads it — the two kinds of edge {@link ValueCycles} follows together,
     * for the same reason: a body's expansion goes through the helpers it calls, so what is behind
     * one is reached exactly as a name written outright is. Following only the first would leave a
     * chain written through helpers settling in the order it was declared, and a chain written
     * bottom to top copied again per link.
     *
     * <p>Every declaration is a node, a helper as much as a value, because a helper is what carries
     * the second kind of edge and is itself checked against what it reaches.
     *
     * <p>Stable otherwise: two declarations that reach neither the other stay in the order the
     * module wrote them. What is checked does not depend on the order — what is not yet settled is
     * copied, as it was before any of this — so this decides what is worked out twice and nothing
     * else.
     *
     * <p>A cycle has no such order, and there is none to find: {@link ValueCycles} refuses a value
     * that reaches itself before a body of this module is expanded, and a cycle of helpers alone is
     * a recursion the language allows. Either way its members are left in the order they were
     * written, each copying the other, which is the answer this pass gave before there was an order
     * at all.
     */
    private static List<Hir.FnDef> valuesBeforeTheValuesThatNameThem(
            HelperInliner inliner, Stdlib stdlib, Map<String, Hir.FnDef> held) {
        // The walk below finds edges by what a name reaches and records them at the addresses this
        // ordering is over. Where a reference is held is the table's answer, so neither coordinate
        // is read off the other's spelling and nothing here keeps a pairing of its own.
        Map<String, Set<String>> names = new LinkedHashMap<>();
        for (Map.Entry<String, Hir.FnDef> e : held.entrySet()) {
            if (!(e.getValue().body() instanceof Hir.FnBody.Written written)) {
                continue;
            }
            Set<souther.compiler.types.ReachName.Declaration> reached = new LinkedHashSet<>();
            HelperInliner.helperCallsIn(stdlib, written.expr(), inliner.reachable(), reached);
            Set<String> here = new LinkedHashSet<>();
            reached.forEach(reference -> {
                souther.compiler.ast.DefinitionName at = inliner.heldAt(reference);
                if (at != null && held.containsKey(at.text())) {
                    here.add(at.text());
                }
            });
            ValueCycles.valuesRead(written.expr(), held, inliner::heldAt, here);
            here.retainAll(held.keySet());
            names.put(e.getKey(), here);
        }
        // Depth-first with its own stack. A module's values chain as deeply as the author wrote
        // them, and the chain is what this is for, so the call stack is the wrong one to walk it on.
        List<Hir.FnDef> ordered = new ArrayList<>();
        Set<String> placed = new LinkedHashSet<>();
        for (String root : held.keySet()) {
            if (placed.contains(root)) {
                continue;
            }
            Deque<String> pending = new ArrayDeque<>();
            pending.push(root);
            Set<String> opened = new LinkedHashSet<>();
            while (!pending.isEmpty()) {
                String at = pending.peek();
                if (placed.contains(at)) {
                    pending.pop();
                    continue;
                }
                // Opened once: its names go on the stack above it, and when it comes round again
                // every one of them has been placed. A name already open is one this walk came
                // through, which is a cycle — refused elsewhere, and left in place here so that
                // this terminates whatever it is handed.
                if (opened.add(at)) {
                    for (String read : names.getOrDefault(at, Set.of())) {
                        if (!placed.contains(read) && !opened.contains(read)) {
                            pending.push(read);
                        }
                    }
                    continue;
                }
                pending.pop();
                placed.add(at);
                ordered.add(held.get(at));
            }
        }
        return ordered;
    }

    /**
     * Completes {@code env} with the type each un-annotated parameter takes from the helper's own
     * body (spec §fn-declaration). A parameter whose type the body leaves open is annotated: Souther has no user
     * generics, so there is nothing to generalise an open parameter into, and the annotation is what
     * states the type instead.
     *
     * <p>{@link HelperParams} settles these types before the module is lowered, and writes what it
     * settles back onto the parameter — so what reaches here open is what it could not settle. Reading
     * it again is what turns "not settled" into a report that names the use that named no type.
     */
    private static void typeFromBody(Hir.FnDef h, List<Integer> open, Scope env,
            Hir.Expr body, Symbols symbols, Map<ValueName.Behavior, ReqSig> reqSigs,
            Map<String, Type> recursiveHelperFns) {
        // A parameter used as a function is one, and neither applying it nor handing it to a
        // combinator determines its type; the inliner also needs the annotation to tell a function
        // parameter from a value one when it expands the call (spec §fn-declaration). The expanded body is what
        // is read, so a parameter handed to `List.map` — applied inside the expansion rather than
        // where it is written — is reported as the function it is.
        for (int idx : open) {
            Hir.FnParam p = h.params().get(idx);
            if (HelperParams.isApplied(body, p.binder())) {
                throw CompileException.of(Diagnostic.at(p.written().reportedAt())
                        .say(new HelperMessage.AFunctionTypedParameterNeedsItsType(h.name(),
                                p.name()))
                        .build());
            }
        }
        Map<Integer, HelperParams.OpenUse> openUses = new HashMap<>();
        HelperParams.determine(h, open, env, body, symbols, reqSigs, recursiveHelperFns, openUses);
        // What the body reaches for decides whether an annotation is what is missing. A helper does
        // not reach a behavior at all, and the type of an argument to a call that cannot be written is
        // nothing for the author to supply, so that call is what is reported.
        if (open.stream().anyMatch(idx -> !env.holds(h.params().get(idx).binder().id()))) {
            callToABehavior(body).ifPresent(call -> {
                throw CallElaborator.noCallee(call, symbols);
            });
        }
        for (int idx : open) {
            Hir.FnParam p = h.params().get(idx);
            if (env.holds(p.binder().id())) {
                continue;
            }
            // Two bodies leave a parameter with nothing, for two reasons, and an author who is told
            // only to annotate it cannot tell which. A field read off a value nothing names will
            // never determine one — reaching a type from a field is a question a nominal model does
            // not ask — where a body that says nothing at all might have said something.
            HelperParams.OpenUse left = openUses.get(idx);
            boolean field = left != null && left.readAField();
            Diagnostic.Builder d = Diagnostic.at(p.written().reportedAt())
                    .say(field
                            ? new HelperMessage.AParameterIsOnlyReadThroughAField(h.name(), p.name())
                            : new HelperMessage.AParameterIsNotDeterminedByTheBody(h.name(),
                                    p.name()));
            if (left != null) {
                d.secondary(left.use().reportedAt(),
                        field ? new HelperMessage.AFieldIsReadOffItAndThatNamesNoType()
                                : new HelperMessage.ThisUseNamesNoType());
            }
            throw CompileException.of(d.build());
        }
    }

    /** A call in {@code body} to a behavior, which no helper reaches (spec [#calling-a-behavior]). */
    private static Optional<Hir.Apply> callToABehavior(Hir.Expr body) {
        if (body instanceof Hir.Apply call && call.answered() != null
                && call.answered().denotes() instanceof ValueName.Behavior) {
            return Optional.of(call);
        }
        List<Hir.Apply> found = new ArrayList<>();
        TypeChecker.forEachChild(body, child -> callToABehavior(child).ifPresent(found::add));
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    /**
     * The same, for the recursive helpers a caller names and reads out of {@code table}.
     *
     * <p>Which recursive helpers a signature is wanted for is the caller's question and there is more
     * than one answer to it: what a module has taken on to emit is one, and what could stand
     * unexpanded anywhere in a representation is another and is wider. What a signature is is the
     * same either way, so it is worked out once here rather than beside each question — two readings
     * of one declaration would agree only until one of them was edited.
     */
    static Map<String, Type> recursiveCallSigs(HelperInliner inliner, Symbols symbols) {
        return sigsOf(inliner.recursiveInReach(), inliner::helper, inliner.moduleName());
    }

    /** The same, read off a table rather than off an inliner over it. */
    static Map<String, Type> recursiveCallSigs(
            HelperTable table, java.util.Collection<souther.compiler.types.ReachName.Declaration> references,
            Symbols symbols) {
        return sigsOf(references, table::reached, table.module());
    }

    /**
     * @param ownModule the module these are being read for. A declaration it wrote is held to
     *     declaring its types, and is told so at the line it was written on. One it did not write is
     *     another module's to hold to that, and is reported there while that module is compiled — so
     *     an unreadable one contributes no signature here rather than a report about a file this
     *     compile does not own. A call that needed it fails where the call is written, which is in
     *     this module and is a place its author can act on.
     */
    private static Map<String, Type> sigsOf(
            java.util.Collection<souther.compiler.types.ReachName.Declaration> references,
            java.util.function.Function<souther.compiler.types.ReachName.Declaration, Hir.FnDef>
                    declaring,
            String ownModule) {
        Map<String, Type> sigs = new HashMap<>();
        for (souther.compiler.types.ReachName.Declaration reference : references) {
            // Which declaration each is, the reference answers. What comes out is a scope — the
            // names a body may write for a call this representation kept standing — so it is keyed
            // as a scope is, by what the body writes. The resolution happened here, above it.
            String name = reference.rendered();
            Hir.FnDef h = declaring.apply(reference);
            boolean ours = h.declaredBy(ownModule);
            if (h.declaredReturn() == null) {
                if (!ours) {
                    continue;
                }
                throw CompileException.of(Diagnostic
                                .at(h.pos()).say(new NameMessage.ARecursiveHelperMustDeclareItsReturnType(name)).build());
            }
            List<Type> params = new ArrayList<>();
            boolean readable = true;
            for (Hir.FnParam p : h.params()) {
                if (p.type() == null) {
                    if (!ours) {
                        readable = false;
                        break;
                    }
                    throw CompileException.of(Diagnostic.at(p.written().reportedAt())
                            .say(new HelperMessage.AParameterNeedsItsType(name, p.name()))
                            .build());
                }
                // a recursive helper is a static method taking its parameters as values; a function
                // parameter is passed as a first-class Fn (a closure), applied inside the method.
                params.add(TypeOps.resolveParamType(p.type()));
            }
            if (!readable) {
                continue;
            }
            sigs.put(name, Type.fn(params, TypeOps.successType(h.declaredReturn())));
        }
        return sigs;
    }

    /**
     * Rejects an invariant that reaches a {@code partial} helper: an invariant is checked on every
     * construction and must terminate, so everything it runs has to carry the termination guarantee
     * (spec §invariant-expressions). A total helper — including the stdlib fold behind the list
     * quantifiers — carries it and is admissible.
     *
     * <p>Reaching, not calling. Inlining leaves a recursive helper standing, so what the clause names
     * is not all it runs; asking only about the names left in the clause let a `partial` helper through
     * behind a recursive one. The path is reported whole, so what the clause has to stop doing is named
     * rather than left to be found by reading the helpers.
     */
    static void rejectPartialHelperInInvariant(Hir.Expr e, String data,
                                               PartialReachability reachability) {
        List<souther.compiler.types.ReachName.Declaration> path =
                reachability.fromExpression(e).orElse(null);
        if (path == null) {
            return;
        }
        String reached = path.get(path.size() - 1).rendered();
        String rendered = "invariant -> " + PartialReachability.render(path);
        Hir.Apply at = firstCallTo(e, path.get(0));
        throw CompileException.of(Diagnostic.at(at == null ? null : at.applied().reportedAt())
                .say(new InvariantMessage.TheInvariantReachesAPartialHelper(data, reached, rendered))
                .build());
    }

    static void rejectPartialHelperInEnsures(Hir.Expr e, String behavior,
                                             PartialReachability reachability) {
        List<souther.compiler.types.ReachName.Declaration> path =
                reachability.fromExpression(e).orElse(null);
        if (path == null) {
            return;
        }
        String reached = path.get(path.size() - 1).rendered();
        String rendered = "ensures -> " + PartialReachability.render(path);
        Hir.Apply at = firstCallTo(e, path.get(0));
        throw CompileException.of(Diagnostic.at(at == null ? null : at.applied().reportedAt())
                .say(new BehaviorMessage.TheEnsuresReachesAPartialHelper(
                        behavior, reached, rendered)).build());
    }

    /** The first application of {@code name} in {@code e}, for the report to underline, or null where
     * the clause holds none (a lowering wrote the call without a name of its own). */
    private static Hir.Apply firstCallTo(Hir.Expr e, souther.compiler.types.ReachName.Declaration reference) {
        if (e instanceof Hir.Apply call && call.answered() != null
                && call.answered().reachedAs().equals(reference)) {
            return call;
        }
        Hir.Apply[] found = new Hir.Apply[1];
        TypeChecker.forEachChild(e, c -> {
            if (found[0] == null) {
                found[0] = firstCallTo(c, reference);
            }
        });
        return found[0];
    }

    /**
     * Rejects a data construction inside one invariant clause: an invariant is pure — it observes the
     * value being built, it does not build another (spec §invariant-expressions, "Forbidden: data
     * construction"). Walks the inlined clause, so a construction smuggled through a quantifier's
     * closure (e.g. {@code List.all(x -> Made { ... }.ok, xs)}) is caught too, and so is one written
     * in a helper the clause names however many helpers away it is — the clause arrives here with
     * them expanded into it, carrying each construction at the position it was written.
     *
     * <p>That is also why the diagnostic carries two places. What is wrong is at the construction,
     * which may be in a helper that reads perfectly well on its own; what makes it wrong is the
     * clause that reaches it, which may be pages away. The clause is named as the second only when it
     * is somewhere else — a construction written in the clause itself has one place, and labelling it
     * twice says nothing.
     */
    static void rejectConstructionInInvariant(Hir.Expr e, String data, Hir.InvariantClause clause) {
        if (e instanceof Hir.NewData nd) {
            String constructed = nd.typeName().written();
            String named = clause.name().orElse(null);
            Diagnostic.Builder b = Diagnostic
                    .at(nd.pos(), constructed.length())
                    .say(named == null
                            ? new InvariantMessage.TheInvariantConstructsAData(data, constructed)
                            : new InvariantMessage.TheNamedClauseConstructsAData(data, constructed,
                                    named));
            if (!writtenInside(nd.pos(), clause.region())) {
                b.secondary(clause.reportedAt(),
                        named == null
                                ? new InvariantMessage.ThisClauseReachesThatConstruction()
                                : new InvariantMessage.TheClauseReachesThatConstruction(named));
            }
            throw CompileException.of(b.build());
        }
        TypeChecker.forEachChild(e, c -> rejectConstructionInInvariant(c, data, clause));
    }

    static void rejectConstructionInEnsures(Hir.Expr e, String behavior,
                                            Hir.EnsuresClause clause) {
        if (e instanceof Hir.NewData nd) {
            String constructed = nd.typeName().written();
            String named = clause.name().orElse(null);
            Diagnostic.Builder b = Diagnostic.at(nd.pos(), constructed.length())
                    .say(named == null
                            ? new BehaviorMessage.TheEnsuresConstructsAData(behavior, constructed)
                            : new BehaviorMessage.TheNamedEnsuresConstructsAData(
                                    behavior, constructed, named));
            if (!writtenInside(nd.pos(), clause.region())) {
                b.secondary(clause.reportedAt(),
                        named == null
                                ? new InvariantMessage.ThisClauseReachesThatConstruction()
                                : new InvariantMessage.TheClauseReachesThatConstruction(named));
            }
            throw CompileException.of(b.build());
        }
        TypeChecker.forEachChild(e, c -> rejectConstructionInEnsures(c, behavior, clause));
    }

    /**
     * Whether the construction is written inside the clause that reaches it, which is when naming
     * the clause a second time says nothing.
     *
     * <p>Asked of the stretch the clause covers and not of a line number. What the report is deciding
     * is whether there are two places to show, and two places are two places whether or not they
     * happen to share a line — a clause written over three lines holds everything in it, and a
     * construction expanded in from a helper is elsewhere however the two files are laid out. A
     * clause with no stretch to compare against is not somewhere a construction can be shown to be
     * inside, so it is named.
     */
    private static boolean writtenInside(SourcePos construction, Region clause) {
        return clause != null && clause.start() != null
                && Region.encloses(clause, Region.point(construction));
    }

    /**
     * Rejects an {@code unreachable} inside an invariant clause. An invariant answers whether the
     * value being built is admissible, and every path through it has to answer that; a path that
     * aborts instead would decide a construction by ending the computation, which is the invariant's
     * own abort taken for a reason the clause never stated (spec §invariant-expressions).
     *
     * <p>Walks the inlined clause, so one written in a helper the clause names is caught where it
     * was written, as a construction is.
     */
    static void rejectUnreachableInInvariant(Hir.Expr e, String data, Hir.InvariantClause clause) {
        if (e instanceof Hir.Unreachable u) {
            throw CompileException.of(Diagnostic
                            .at(u.pos(), "unreachable".length())
                            .say(new InvariantMessage.AnInvariantAnswersOnEveryPath(data)).build());
        }
        TypeChecker.forEachChild(e, c -> rejectUnreachableInInvariant(c, data, clause));
    }

    static void rejectUnreachableInEnsures(Hir.Expr e, String behavior) {
        if (e instanceof Hir.Unreachable u) {
            throw CompileException.of(Diagnostic.at(u.pos(), "unreachable".length())
                    .say(new BehaviorMessage.AnEnsuresAnswersOnEveryPath(behavior)).build());
        }
        TypeChecker.forEachChild(e, c -> rejectUnreachableInEnsures(c, behavior));
    }

    /** Rejects a call to an injected behavior inside a recursive helper: it is pure (spec §fn-declaration). */
    private static void rejectInjectedCalls(Hir.Expr e, String helper,
                                            Set<ValueName.Behavior> injected) {
        if (e instanceof Hir.Apply call && call.answered() != null
                && call.answered().denotes() instanceof ValueName.Behavior reached
                && injected.contains(reached)) {
            throw CompileException.of(Diagnostic
                            .at(call.appliedAt()).say(new NameMessage.ARecursiveHelperIsPure(helper, call.written())).build());
        }
        TypeChecker.forEachChild(e, c -> rejectInjectedCalls(c, helper, injected));
    }

    /**
     * Checks each function passed to a helper's function-typed parameter against that parameter's
     * declared type, at the call site — before the helper is expanded inline. Without this, a bad
     * function argument to a combinator surfaces deep inside the {@code fold} it derives from (a
     * non-{@code Bool} {@code filter} predicate as the {@code if} the derivation expands to), which
     * names the derivation, not the mistake. Here the error names the parameter and the combinator.
     *
     * <p>It walks the un-inlined body. Every helper call binds its signature's type variables from
     * its collection arguments ({@code 'a} from a {@code List<'a>}), then checks each function
     * argument against the resulting concrete function type. The check is best-effort: when an
     * argument's type cannot be determined in the available scope (a value bound further out), it is
     * skipped and the ordinary inlined check still applies.
     */
    static void checkFunctionArgs(Hir.Expr e, Scope env, Symbols symbols,
                                          Map<ValueName.Behavior, ReqSig> reqs, HelperInliner inliner) {
        if (e instanceof Hir.Apply call) {
            checkHelperCallFnArgs(call, env, symbols, reqs, inliner);
        }
        TypeChecker.forEachChild(e, sub -> checkFunctionArgs(sub, env, symbols, reqs, inliner));
    }

    private static void checkHelperCallFnArgs(Hir.Apply call, Scope env, Symbols symbols,
                                              Map<ValueName.Behavior, ReqSig> reqs, HelperInliner inliner) {
        // what the call applies, which a binding of a helper's spelling is not: applying a
        // function-typed parameter is not a call to the helper it happens to be named after
        Hir.FnDef h = inliner.applied(call);
        if (h == null || call.args().size() != h.params().size()) {
            return;   // applies no body, or an arity mismatch the inliner reports
        }
        List<Type> declared = new ArrayList<>();
        boolean hasFn = false;
        for (Hir.FnParam p : h.params()) {
            // an unannotated parameter takes its type from the helper's body (spec §fn-declaration); it is never a
            // function parameter, so leave its slot null and treat it as a non-function argument here.
            Type pt = p.type() == null ? null : TypeOps.resolveParamType(p.type());
            declared.add(pt);
            hasFn |= pt instanceof Type.FnOf;
        }
        if (!hasFn) {
            return;
        }
        // the collection (non-function) arguments bind the signature's type variables — `'a` from a
        // `List<'a>` collection — so the function parameters become concrete before the check.
        Map<String, Type> bind = new HashMap<>();
        for (int i = 0; i < declared.size(); i++) {
            if (declared.get(i) == null || declared.get(i) instanceof Type.FnOf) {
                continue;
            }
            try {
                Type at = Elaborator.typeOf(inliner.inline(call.args().get(i), inliner.bodyOf(h.name())),
                        env, new CheckContext(symbols, null, reqs));
                if (TypeOps.unify(declared.get(i), at, bind, symbols) instanceof Fit.Disagrees) {
                    return;   // the argument does not fit; leave it to the inlined check
                }
            } catch (CompileException _) {
                return;   // can't type the argument here; leave it to the inlined check
            }
        }
        for (int i = 0; i < declared.size(); i++) {
            if (declared.get(i) instanceof Type.FnOf fn0) {
                Type.FnOf want = (Type.FnOf) TypeOps.substitute(fn0, bind);
                if (carriesBottom(want)) {
                    // An empty-collection seed ([], Map.empty) binds the accumulator to a bottom, and
                    // the step is what grows it to the concrete type — exactly the shape a fold over
                    // an empty seed has (`Map.fold(step, [], m)`). Nothing concrete to check against
                    // here; the inlined check, which sees the expected type pushed down, decides.
                    continue;
                }
                checkFunctionArg(h, h.params().get(i).name(), want,
                        call.args().get(i), env, symbols, reqs, inliner, bind);
            }
        }
    }

    /** Whether a step's signature still carries an empty-collection bottom in a parameter or in its
     * result — the accumulator of a fold seeded with {@code []} / {@code Map.empty}, which only the
     * step itself grows to a concrete type. */
    private static boolean carriesBottom(Type.FnOf fn) {
        if (Type.mentions(fn.result(), BottomInfer::isBottom)) {
            return true;
        }
        for (Type p : fn.params()) {
            if (Type.mentions(p, BottomInfer::isBottom)) {
                return true;
            }
        }
        return false;
    }

    /** The block a combinator was handed answers with the wrong type, in written types on both sides
     * (`'b?` / `String`), not in the checker's own spelling. It takes the block rather than a
     * position, so where the report is drawn is decided here from the value being refused and not by
     * whichever position a caller had to hand. */
    private static CompileException blockReturnMismatch(Hir.FnDef h, String paramName, Type want,
                                                        Type got, Hir.Expr block) {
        return CompileException.of(Diagnostic.at(Elaborator.answerRegion(block))
                .say(new HelperMessage.TheBlockAnswersAnotherType(paramName, h.name(),
                        Type.show(want), Type.show(got)))
                .build());
    }

    /** Whether a type still holds a type variable, so nothing concrete can be checked against it yet.
     * A bare {@code 'b} is the common case (`map`'s result); a variable can also sit inside a
     * constructor — {@code filterMap}'s step answers {@code 'b?} — and that is just as open. */
    private static boolean isOpen(Type t) {
        return Type.mentions(t, x -> x instanceof Type.Var);
    }

    private static void checkFunctionArg(Hir.FnDef h, String paramName, Type.FnOf want, Hir.Expr arg,
                                         Scope env, Symbols symbols,
                                         Map<ValueName.Behavior, ReqSig> reqs, HelperInliner inliner,
                                         Map<String, Type> bind) {
        if (arg instanceof Hir.Block lambda) {
            if (lambda.params().size() != want.params().size()) {
                throw CompileException.of(Diagnostic.at(arg.pos())
                        .say(new HelperMessage.TheBlockTakesAnotherNumberOfArguments(paramName,
                                h.name(), String.valueOf(want.params().size()),
                                String.valueOf(lambda.params().size())))
                        .build());
            }
            Scope lenv = env;
            for (int j = 0; j < lambda.params().size(); j++) {
                if (isOpen(want.params().get(j))) {
                    return;   // the parameter type is still open; nothing concrete to check
                }
                lenv = lenv.with(lambda.params().get(j), want.params().get(j));
            }
            Type got;
            try {
                got = Elaborator.typeOf(inliner.inline(lambda.body(), inliner.bodyOf(h.name())), lenv,
                        new CheckContext(symbols, null, reqs));
            } catch (CompileException _) {
                return;   // best-effort; the inlined check reports a genuine error with full context
            }
            if (isOpen(want.result())) {
                // The declared result still has a variable in it, so the shape around the variable is
                // what there is to check: `'b?` accepts a block answering with an optional and rejects
                // one answering with a plain value. Unifying also pins `'b` for the arguments after
                // this one. A failure is reported as the mismatch it is, in written types.
                if (TypeOps.unify(want.result(), got, bind, symbols) instanceof Fit.Disagrees) {
                    throw blockReturnMismatch(h, paramName, want.result(), got, lambda);
                }
                return;
            }
            if (!TypeOps.assignable(got, want.result(), symbols)) {
                throw blockReturnMismatch(h, paramName, want.result(), got, lambda);
            }
        } else if (arg instanceof Hir.Var.Denoting v
                && env.of(v.denotes(), v.name()) instanceof Type vt && !(vt instanceof Type.FnOf)) {
            throw CompileException.of(Diagnostic.at(arg.pos())
                    .say(new HelperMessage.AValueWhereAFunctionIsTaken(paramName, h.name(),
                            v.name()))
                    .build());
        }
    }

    /**
     * The data each recursive helper constructs, transitively. A recursive helper is lowered to a
     * method rather than inlined, so its constructions do not appear in a caller's body; this map lets
     * {@link DataChecker#collectConstructs} attribute them to the behavior that calls the helper (spec §blocks). The
     * closure follows recursive-helper calls: a helper's set includes what the recursive helpers it
     * calls construct. Non-recursive helper calls are already inlined into the bodies here.
     */
    static Map<String, DataChecker.Constructs> recursiveHelperConstructs(
            Set<String> recursive, Map<String, Hir.Expr> loweredBodies,
            HelperInliner inliner, Symbols symbols) {
        Map<String, DataChecker.Constructs> own = new HashMap<>();
        Map<String, Set<String>> calls = new HashMap<>();
        for (String h : recursive) {
            Hir.Expr body = loweredBodies.get(h);
            DataChecker.Constructs c = DataChecker.Constructs.empty();
            DataChecker.collectConstructs(body, c, symbols, Map.of());   // recursive calls opaque here
            own.put(h, c);
            Set<String> callees = new LinkedHashSet<>();
            collectCalls(body, callees, recursive);
            calls.put(h, callees);
        }
        Map<String, DataChecker.Constructs> full = new HashMap<>();
        for (String h : recursive) {
            full.put(h, own.get(h).copy());
        }
        // fixpoint: propagate each callee's constructions until nothing new is added (handles mutual
        // recursion, whose call graph has cycles). The two kinds travel apart the whole way: a
        // construction another module's published body carried into this helper is still that
        // module's where the behavior calling the helper reads it, and merging them here is how a
        // reader came to be told it declares a construction it does not make.
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String h : recursive) {
                for (String g : calls.get(h)) {
                    changed |= full.get(h).absorb(full.get(g));
                }
            }
        }
        return full;
    }

    /** Collects the names in {@code names} that {@code e} calls (a recursive-helper call graph edge). */
    private static void collectCalls(Hir.Expr e, Set<String> out, Set<String> names) {
        if (e instanceof Hir.Apply call && call.answered() != null
                && names.contains(call.answered().reaches())) {
            out.add(call.answered().reaches());
        }
        TypeChecker.forEachChild(e, c -> collectCalls(c, out, names));
    }

}
