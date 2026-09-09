package souther.compiler.check;

import souther.compiler.stdlib.Stdlib;
import souther.compiler.check.ReadingPolicy;
import souther.compiler.ast.Hir;
import souther.compiler.core.Core;
import souther.compiler.core.ValueShape;
import souther.compiler.types.TypeSymbol;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.DiagnosticRenderer;
import souther.compiler.diag.msg.ExampleMessage;
import souther.compiler.diag.msg.DataMessage;
import souther.compiler.diag.msg.BehaviorMessage;
import souther.compiler.diag.msg.ModuleMessage;
import souther.compiler.types.Type;
import souther.compiler.types.ValueName;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The slice-3 type checker. Adds a module symbol table so fields, decoders, and encoders
 * can reference other data types (e.g. {@code id: MemberId}, {@code field("id",
 * MemberId.decoder)}, {@code MemberId.encode(self.id)}). Exposes {@link #symbols} and
 * {@link Elaborator#typeOf} for the backend; the type-level operations live in {@link TypeOps}.
 */
public final class TypeChecker {

    private TypeChecker() {}

    /** The bodies elaborated so far, filled as the check walks them. */
    static final class Elaborated {
        final Map<String, Core> helpers = new LinkedHashMap<>();
        /** What each of the module's own definitions turned out to be, by name — a value's type and a
         * helper's return type, settled from the body (ADR-0066). Recorded because the exposed-surface
         * check asks what a published definition is, and the answer is a type: reading the body for
         * the constructions in it answers a different question, and misses a definition whose type
         * names something it does not build. A definition that returns a function has none: there is
         * no application here to settle the lambda from (spec §blocks). */
        final Map<String, Type> definitionTypes = new LinkedHashMap<>();
    }

    /**
     * A recovering check that keeps each error as the exception it was raised as, together with
     * what the check elaborated.
     *
     * <p>The only entry point. A caller decides what to do with what was found: raise the first, or
     * report them all. It hands back the exceptions rather than the diagnostics because a diagnostic
     * does not carry the English body a throw site passed alongside it, and a caller that has to
     * raise one should raise what the pass raised.
     *
     * @param errors what the check found wrong
     * @param abandoned the units it could not read at all, each resting on a name that denotes
     *                  nothing — reported already, and each a reason the module cannot be emitted
     * @param stopped whether the check stopped rather than finished. A structural check builds what a
     *                later phase reads — the {@code fns} map, the {@code exposed} set — so when one
     *                fails there is nothing left to check the rest against, and a body checked all
     *                the same reports being unable to see what was already reported missing.
     * @param emittedHelpers the recursive helper bodies it elaborated, which the backend emits as
     *                         methods
     */
    public record Reported(List<CompileException> errors, List<Unanswerable> abandoned,
                           boolean stopped, Map<String, Core> emittedHelpers) {}

    /**
     * Everything the check has to say about a module that is not one behavior's body: its
     * declarations, its helpers, its {@code exposing} line, its compositions. A behavior's body is
     * asked for on its own ({@link #checkBehavior}), so what one of them says is not in here.
     *
     * <p>{@code reqSigs} and {@code recursiveHelperFns} are handed over rather than worked out here,
     * because the body check reads the same two and they must be the same two.
     */
    public static Reported checkModule(Hir.Module module, DerivedSymbols symbols,
                                       UninhabitableTypes.WithNoValue withNoValue,
                                       DeclarationLocations declaredAt,
                                       ReadingPolicy policy,
                                       Map<String, Sig> sigs,
                                       Set<ValueName.Behavior> importedInjected,
                                       Set<ValueName.Behavior> importedUnwritten,
                                       Hir.Module lowered, Map<ValueName.Behavior, ReqSig> reqSigs,
                                       Map<ValueName.Behavior, ReqSig> calleeSigs,
                                       Map<String, Type> recursiveHelperFns,
                                       Map<String, Hir.FnDef> imported, Set<String> settled,
                                       Map<TypeSymbol.AtModule, ValueShape> shapes) {
        Elaborated elaborated = new Elaborated();
        List<Unanswerable> abandoned = new ArrayList<>();
        List<CompileException> errors = new ArrayList<>();
        boolean stopped = false;
        try {
            checkRecovering(module, symbols, withNoValue, declaredAt, policy, sigs, importedInjected,
                    importedUnwritten,
                    lowered, calleeSigs, errors,
                    elaborated, abandoned, reqSigs, recursiveHelperFns, imported, settled, shapes);
        } catch (Unanswerable e) {
            abandoned.add(e);
            stopped = true;
        } catch (CompileException e) {
            // A structural / prerequisite check (a duplicate name, an `exposing` violation, a module
            // cycle) is fail-fast: it can leave later phases without the state they read, so its first
            // error is recorded and the rest of the module is abandoned. Per-definition and
            // per-behavior errors are collected instead, so one broken body does not hide another.
            // An unresolvable type no longer gets here: it denotes the error type, which absorbs, so
            // the module is checked past it and never reaches codegen.
            errors.add(e);
            stopped = true;
        }
        return new Reported(deduped(errors), List.copyOf(abandoned), stopped, elaborated.helpers);
    }

    /**
     * One behavior's body against the behavior it implements (spec §fn-declaration), in both the
     * representation the backend emits and the one an analysis reads. Its own question: what it
     * reads is the behavior, its {@code let}, and what the module around it means — never another
     * body.
     */
    public static SpecChecker.Checked checkBehavior(Hir.SpecBehavior spec, Hir.FnDef fn,
                                     Hir.Expr loweredBody,
                                    ReadingPolicy policy,
                                     InvariantChecker.Source discharge,
                                     Symbols symbols, Map<ValueName.Behavior, ReqSig> calleeSigs,
                                     Map<ValueName.Behavior, ReqSig> reqSigs, HelperInliner inliner,
                                     Map<String, Type> recursiveHelperFns,
                                     Map<String, DataChecker.Constructs> recHelperConstructs,
                                     List<Diagnostic> warnings) {
        return SpecChecker.checkSpecFn(spec, fn, loweredBody, discharge, symbols, policy, calleeSigs, reqSigs,
                inliner, recursiveHelperFns, recHelperConstructs, warnings);
    }

    /**
     * Whether every declaration being checked here had its clauses read.
     *
     * <p>Absence and not a count: a data is in {@code shapes} when its clauses elaborated, so a
     * declaration missing from it is one whose rules this reading may not be built on.
     */
    private static boolean everyClauseWasRead(Hir.Module module, Set<String> settled,
                                              Map<TypeSymbol.AtModule, ValueShape> shapes) {
        for (Hir.Def def : module.defs()) {
            if (def instanceof Hir.Data data && settled.contains(def.name())
                    && !shapes.containsKey(data.declares())) {
                return false;
            }
        }
        return true;
    }

    /** The signatures every recursive helper a representation can reach would be called under —
     *  what typing a call left standing needs, whether or not this module turned out to reach it. */
    public static Map<String, Type> recursiveCallSigs(
            HelperTable table,
            java.util.Collection<souther.compiler.types.ReachName.Declaration> references,
            Symbols symbols) {
        return HelperTyping.recursiveCallSigs(table, references, symbols);
    }

    /** What each recursive helper constructs, transitively. A recursive helper is not inlined, so its
     * constructions are attributed to the behavior that calls it (spec §blocks). */
    public static Map<String, DataChecker.Constructs> recursiveHelperConstructs(
            Set<String> names, Map<String, Hir.Expr> loweredBodies, HelperInliner inliner,
            Symbols symbols) {
        return HelperTyping.recursiveHelperConstructs(names, loweredBodies, inliner, symbols);
    }

    /**
     * Runs one unit's check, recording its first error instead of throwing so the next unit is still
     * checked — the recovery boundary that lets a module report more than one error.
     *
     * <p>A unit that was abandoned rather than found wrong is recorded in {@code abandoned}. It has
     * no error of its own: the name it rested on was reported where it was written. But the module
     * cannot be emitted, because this unit has no meaning to emit, and that is what the list says.
     */
    static void collect(List<CompileException> errors, List<Unanswerable> abandoned,
                        Runnable unitCheck) {
        try {
            unitCheck.run();
        } catch (CompileException e) {
            errors.add(e);
        } catch (Unanswerable e) {
            abandoned.add(e);
        }
    }

    /** Collected errors as a stable set in first-seen order: one per problem, so the same underlying
     * error found by two phases is shown once. What makes two of them one problem is the diagnostic's
     * own {@link Diagnostic#identity()} — the same comparison a caller collecting from several
     * questions makes. */
    static List<CompileException> deduped(List<CompileException> errors) {
        Map<Object, CompileException> unique = new LinkedHashMap<>();
        for (CompileException e : errors) {
            Diagnostic d = e.diagnostic();
            unique.putIfAbsent(d == null ? "null" : d.identity(), e);
        }
        return new ArrayList<>(unique.values());
    }


    /**
     * The check phases, appending to {@code errors} rather than returning them. Contract: each
     * independent per-definition or per-behavior check MUST be run through {@link #collect} so its
     * failure is recorded and the next unit is still checked. Only a phase that builds state a later
     * phase reads (the {@code fns} map, the {@code exposed} set, {@code reqSigs}, {@code sigs}) may
     * throw straight out — its caller treats that as fail-fast and abandons the module.
     */
    static void checkRecovering(Hir.Module module, DerivedSymbols symbols,
                                        UninhabitableTypes.WithNoValue withNoValue,
                                        DeclarationLocations declaredAt,
                                       ReadingPolicy policy,
                                        Map<String, Sig> sigs,
                                       Set<ValueName.Behavior> importedInjected,
                                       Set<ValueName.Behavior> importedUnwritten,
                                        Hir.Module lowered, Map<ValueName.Behavior, ReqSig> calleeSigs,
                                        List<CompileException> errors,
                                        Elaborated elaborated, List<Unanswerable> abandoned,
                                        Map<ValueName.Behavior, ReqSig> reqSigs,
                                        Map<String, Type> recursiveHelperFns,
                                        Map<String, Hir.FnDef> publishedToHere,
                                        Set<String> settled,
                                        Map<TypeSymbol.AtModule, ValueShape> shapes) {
        // Both components, because what reads this walks both: a helper is checked whether the module
        // declared it or took it on to emit, and one missing here is a helper checked against a body
        // it does not have.
        Map<String, Hir.Expr> loweredBodies = new HashMap<>();
        // Which definitions this module processes, read off what it lowered rather than off what it
        // was predicted to have to lower. A recursion an expansion of this module left standing is
        // one this module emits, so it is one this module checks — and the two answers were allowed
        // to differ while the second was a walk over the places a module writes expressions.
        // Which definitions this module checks standing on their own: every helper it declared,
        // whether or not anything survives to call it, and what it emits beyond them. The second is
        // read off what it lowered rather than off what it was predicted to have to lower — a
        // recursion an expansion of this module left standing is one this module emits, so it is one
        // this module checks, and the two answers were allowed to differ while the second was a walk
        // over the places a module writes expressions. A helper is a fn whose name is no behavior's,
        // which is the reading both of these are keyed by.
        Map<String, Hir.FnDef> toCheck =
                new java.util.LinkedHashMap<>(HelperInliner.helpersOf(module));
        toCheck.putAll(HelperInliner.takenOnBy(lowered));
        for (Hir.FnDef fn : lowered.fns()) {
            loweredBodies.put(fn.name(), fn.writtenBody());
        }
        for (Hir.FnDef fn : lowered.takenOn()) {
            loweredBodies.put(fn.name(), fn.writtenBody());
        }
        // The imported definitions join the table this module's bodies are expanded against: a
        // published helper is expanded at its call sites here exactly as one of this module's own is,
        // and it is not one of this module's own — which is why a recursive one of them is emitted
        // here rather than declared here.
        // A helper is checked standing on its own as well as expanded into what calls it, and both
        // readings answer the same about a behavior's name: it becomes the function value it names,
        // which a helper may then not apply (E1818). Told nothing, the standalone reading would
        // refuse the name for being a name rather than for being a behavior a helper cannot reach.
        HelperInliner inliner = HelperInliner.forModule(module, publishedToHere, symbols.library())
                .namingBehaviors(InjectionSigs.arities(calleeSigs));
        // Which declarations reach a `partial` helper, asked once for the two checks that ask it: the
        // invariant rule just below and the totality rule further down.
        PartialReachability reachability = PartialReachability.of(inliner);
        // An invariant runs on every construction and must terminate (spec §invariant-expressions).
        // A total recursive helper does terminate, so it is admissible — including the stdlib fold
        // (`List.foldFrom`) that backs the list quantifiers `List.all`/`any`/`member`/`distinct`,
        // which are inlined down to it here (`withInlinedInvariants` runs before this check). What is
        // barred is reaching a `partial` helper, which carries no termination guarantee. Reaching, not
        // calling: a recursive helper is left standing by the inlining, so what a clause names is not
        // all it runs, and deciding this on what inlining left visible is what let an invariant through
        // to a `partial` helper behind a recursive one.
        // The invariant checks run before the data check, so a partial call or a construction is named
        // before it is otherwise reported as an unknown function or type-checked. A clause is a unit
        // of its own — it is what an attempt answers by name and what discharge answers about — so a
        // wrong one is recorded and the next is still checked, and a declaration with two wrong
        // clauses says so about both. Within one clause the first construction is the answer: naming
        // every one of them tells the author nothing the first does not.
        for (Hir.Def def : module.defs()) {
            if (!settled.contains(def.name())) {
                continue;
            }
            if (def instanceof Hir.Data data) {
                for (Hir.InvariantClause clause : data.invariants()) {
                    collect(errors, abandoned, () -> {
                        HelperTyping.rejectPartialHelperInInvariant(
                                clause.expr(), data.name(), reachability);
                        HelperTyping.rejectConstructionInInvariant(clause.expr(), data.name(), clause);
                        HelperTyping.rejectUnreachableInInvariant(clause.expr(), data.name(), clause);
                    });
                }
            }
        }
        for (Hir.BehaviorDef behavior : module.behaviors()) {
            if (behavior instanceof Hir.SpecBehavior spec) {
                for (Hir.EnsuresClause clause : spec.ensures()) {
                    for (Hir.EnsuresArm arm : clause.arms()) {
                        collect(errors, abandoned, () -> {
                            HelperTyping.rejectPartialHelperInEnsures(
                                    arm.expr(), spec.name(), reachability);
                            HelperTyping.rejectConstructionInEnsures(
                                    arm.expr(), spec.name(), clause);
                            HelperTyping.rejectUnreachableInEnsures(arm.expr(), spec.name());
                        });
                    }
                }
            }
        }
        // Only a declaration whose meaning was settled is checked, and `settled` is what says which
        // those are. What a check over one of the others would find is the mistake that stopped it
        // being settled, said again from further down — that the type it is made of has no decoder,
        // or no fields, or no value — against a line the author has no reason to look at. The caller
        // holds the declarations that have a meaning and hands them over; there is nothing here that
        // asks why one of the others has none.
        for (Hir.Def def : module.defs()) {
            if (!settled.contains(def.name())) {
                continue;
            }
            collect(errors, abandoned, () -> {
                switch (def) {
                    // The declarations that came out, which is what the derived world has. One that
                    // did not was reported where it was derived, and a check over it would find
                    // that mistake again from further down — against a line the author has no
                    // reason to look at.
                    case Hir.Data data -> {
                        if (symbols.declarations().declaration(data.declares())
                                instanceof Derived.Data derived) {
                            DataChecker.checkData(derived,
                                    CheckContext.of(symbols).forData(data));
                        }
                    }
                    case Hir.SumData sum -> DataChecker.checkSum(sum, symbols);
                    case Hir.UnitData _ -> { }
                }
            });
        }
        // Asked only where every declaration stated rules this checker could read. The reading
        // decides that no value exists from what the rules say, and a rule reported as wrong says
        // nothing -- a count read off one refuses a type on the strength of a clause the author has
        // already been told to fix. Only the declarations have been checked at this point, so a
        // mistake in a body further down does not silence this.
        //
        // What says a clause was read is `shapes`: a declaration whose clause could not be
        // elaborated has no shape there, and elaborating one is what says it states a condition.
        // That reading is not made here — it is made once, where the clause a construction runs
        // comes from — so this asks it rather than repeating it.
        if (errors.isEmpty() && everyClauseWasRead(module, settled, shapes)) {
            List<CompileException> said = new ArrayList<>();
            collect(errors, abandoned,
                    () -> said.addAll(DataChecker.typesWithNoValue(withNoValue, declaredAt)));
            errors.addAll(said);
        }
        Map<String, Hir.FnDef> fns = new HashMap<>();
        for (Hir.FnDef fn : module.fns()) {
            if (fns.put(fn.name(), fn) != null) {
                throw CompileException.of(Diagnostic
                                .at(fn.pos()).say(new DataMessage.ALetIsAlreadyDefined(fn.name())).build());
            }
        }
        Set<String> allBehaviors = new HashSet<>();
        Set<String> specNames = new HashSet<>();
        for (Hir.BehaviorDef b : module.behaviors()) {
            allBehaviors.add(b.name());
            if (b instanceof Hir.SpecBehavior spec) {
                specNames.add(b.name());
                List<String> outputCases = new ArrayList<>();
                for (Hir.TypeTerm t : spec.ret().cases()) {
                    // a function output is refused as unrepresentable; it names no output case
                    if (t instanceof Hir.TypeRef ref) {
                        outputCases.add(ref.name());
                    }
                }
                DataChecker.rejectDuplicateNames(outputCases, "the behavior output", spec.pos());
                List<String> required = new ArrayList<>();
                for (Hir.Var req : spec.dependsOn()) {
                    // A name nothing answered is no name for another to be a duplicate of, and it
                    // was reported where it is written.
                    if (req instanceof Hir.Var.Denoting named) {
                        // The bare name, and deliberately not the declaration. Two dependencies are
                        // two behaviors whatever modules declared them, and the clause is still
                        // refused when they go by one spelling: what it decides is the implementing
                        // `let`'s trailing parameters, which are named after these (§depends-on), so
                        // a pair here that shared a spelling would be a `let` taking one name twice.
                        // Comparing the declarations would admit `up.now, other.now` and leave that
                        // `let` unwritable.
                        required.add(named.denotes().name());
                    }
                }
                DataChecker.rejectDuplicateNames(required, "`depends on`", spec.pos());
                DataChecker.rejectDuplicateTypes(spec.constructs(), "`constructs`", spec.pos());
            }
        }
        // A data is Java-buildable from outside iff the whole module is public (no `exposing`) or
        // its name is exposed. Used by the injection constructs check (E1305).
        boolean exposeAll = module.exposing().isEmpty();
        // `exposing` lists a module's own public surface. A module's own type names, as opposed to
        // `symbols`, which also holds the data it imports — an imported name is not re-exported.
        Set<String> ownTypes = new HashSet<>();
        for (Hir.Def d : module.defs()) {
            ownTypes.add(d.name());
        }
        Set<String> exposed = new HashSet<>();
        for (String e : module.exposing()) {
            int dot = e.indexOf('.');
            // `exposing` is type-granular: a data's decoder/encoder are always public API once the
            // data itself is exposed (spec §jvm-codec), so there is nothing a `.decoder`/`.encoder` member
            // could narrow. Reject it rather than accept a form that reads as a granularity that
            // does not exist.
            if (dot >= 0) {
                throw CompileException.of(Diagnostic.say(new ModuleMessage.ExposingIsTypeGranular(e.substring(0, dot), e))
                                .at(module.pos()).build());
            }
            // an exposed name must be one of this module's own definitions. An imported name that is
            // merely visible here is not re-exported — importers reach it from its declaring module.
            if (!ownTypes.contains(e) && !allBehaviors.contains(e)) {
                // A value and a helper are both part of what a module offers: a limit a rule is
                // written against, and the rule itself. A behavior's own `let` is not — what a reader
                // reaches there is the behavior, which it calls, and the module publishes its
                // specification rather than the body it was given (ADR-0005).
                Hir.FnDef helper = HelperInliner.helpersOf(module).get(e);
                if (helper != null) {
                    // The same rule a body is held to (spec §an-attached-files-values-are-for-its-rows),
                    // read here because an `exposing` list is a list of names and not an expression, so
                    // it is not answered where a name written in one is. A value an attached file
                    // declares is there for the rows beside it; published, it would be a name an
                    // importer reaches with no source of it in the jar.
                    if (!helper.role().isTheModels()) {
                        throw CompileException.of(Diagnostic.at(module.pos())
                                .say(new ExampleMessage.TheModelNamesAValueAnAttachedFileDeclares(e))
                                .hint(new ExampleMessage.MoveTheValueIntoTheModuleItself(e))
                                .build());
                    }
                    exposed.add(e);
                    continue;
                }
                boolean imported = symbols.scope().inScope(e);

                throw CompileException.of(Diagnostic.at(module.pos())
                        .say(imported
                                ? new ModuleMessage.ExposingNamesAnImportedName(e)
                                : new ModuleMessage
                                        .ExposingNamesSomethingThisModuleDoesNotDeclare(e))
                        .build());
            }
            exposed.add(e);
        }
        // Injection targets (spec §injected-behavior): a SpecBehavior with no matching fn. Its name and
        // success type let a fn call it inline (spec §unmarked-output); it is the "required" behavior of the
        // old form. An imported injection target is one here too (spec §composition-with-requirements): the
        // module that names it injects and binds it, whether it named it as a `>->` stage or as a `depends
        // on` dependency. Its signature comes from the module that declared it; a local behavior of the same
        // name wins. The same map is built before the module is lowered, where a helper's parameter type is
        // settled from a call to an injected behavior (issue #178), and read again by every body check. It
        // arrives here rather than being built again because it is one question.
        Set<String> injectionTargets = new HashSet<>();
        // Asked of every clause, implemented or injected: what may be named in one is a question
        // about the clause and not about who implements it (spec §constructs-excludes-unit-data).
        // Every behavior's, before the first is raised — a wrong clause is one thing to rewrite, and
        // an author with two of them should not learn the second by building again. This is what
        // E1002 and E1006 do within one clause, one frame out.
        List<Diagnostic> unitEntries = new ArrayList<>();
        for (Hir.BehaviorDef b : module.behaviors()) {
            if (b instanceof Hir.SpecBehavior any) {
                unitEntries.addAll(SpecChecker.unitDataNamedInConstructs(any, symbols));
            }
        }
        if (!unitEntries.isEmpty()) {
            throw CompileException.ofAll(unitEntries, DiagnosticRenderer.legacyBody(unitEntries.get(0)));
        }
        for (Hir.BehaviorDef b : module.behaviors()) {
            // `depends on` names what an implementation calls (§depends-on), so a behavior that
            // writes one takes those dependencies as arguments of a `let` — which makes it Souther's
            // to implement whether or not the `let` has been written yet (§unwritten-behavior). What
            // Java supplies is the behavior that writes no clause and no `let` (§injected-behavior),
            // and only that one gets a base for an implementation to extend.
            if (b instanceof Hir.SpecBehavior spec
                    && Requirements.implementationOf(b, fns.keySet()).isInjectionTarget()) {
                SpecChecker.checkInjectionConstructs(spec, symbols, exposeAll, exposed);
                injectionTargets.add(spec.name());
            }
        }
        collect(errors, abandoned, () -> SpecChecker.checkStagesAreSingleInput(module));
        // The signatures arrive rather than being built here. Making one is what admits what a
        // boundary carries, and it happens in one place, once; a second construction would be the
        // same question answered again, which is what carrying the answer was for. Where it did not
        // build, the module is abandoned from this point on: everything below rests on a behavior
        // having a signature, and the reason there is none was reported where they are made.
        // Abandoned here rather than earlier, so the checks that do not read a signature — a
        // declaration, an `exposing` line, a stage's arity — still say what they found.
        if (sigs == null) {
            throw new Unanswerable(module.pos());
        }
        // What a behavior declares about its answer is not read here. `Bodies.Contracts` owns that
        // reading and the reports it produces, so a reader asks for the contracts and what the
        // reading found comes with them. Read here as well, the same clause would be walked twice
        // and whichever caller ran first would be the reason the diagnostics existed.
        // Fail-fast with the reqSigs it reads: a `depends on` that named something else leaves the call
        // untypeable, and the body check would report it as a call to an unknown name (E1023).
        SpecChecker.checkRequiresAreInjectionTargets(module, reqSigs, calleeSigs);
        // Nothing that is built here may hold a behavior nobody has written (spec §unwritten-behavior).
        SpecChecker.checkNothingBuiltHereRestsOnAnUnwrittenBehavior(module, importedUnwritten);
        // Fail-fast too: a behavior reaching itself has no first element to build, and the code that
        // works out requirement sets and emits classes would walk the loop.
        SpecChecker.checkBehaviorsDoNotRecurse(module);
        // A binding whose value is a lambda takes no annotation (spec §let). Read on the surface bodies:
        // lowering has already expanded such a binding away at each of its applications.
        for (Hir.FnDef fn : module.fns()) {
            collect(errors, abandoned, () -> Elaborator.checkAnnotatedLambdaBindings(fn.writtenBody(), symbols));
        }
        // Helper fns (no matching behavior) are expanded inline at each call site (spec §blocks); a
        // helper is checked standalone against its own parameter types, which its body settles
        // (spec §fn-declaration). Recovered so a broken helper does not hide the behavior-body errors below.
        // A row's operands are among this module's definitions by now, so what each of them is was
        // settled with the rest — and held to the position it stands at by the type its wrapper
        // declares, which is the same check every other definition of this module gets. There is
        // nothing left here for a reading of its own to ask.
        collect(errors, abandoned, () -> HelperTyping.checkHelpers(inliner, toCheck, symbols, reqSigs,
                recursiveHelperFns, loweredBodies, elaborated));
        // Recursion is total by default (spec §fn-declaration): a non-`partial` recursive helper must
        // be structurally recursive, so its examples terminate at compile time.
        collect(errors, abandoned, () -> TotalityChecker.check(inliner));
        // And the guarantee covers what a helper reaches, not only its own descent: an unmarked helper
        // may reach no `partial` one, and a `partial` one may not be written where a value goes. Asked
        // per declaration, so a module needing the word in several places says so in one build. Which
        // declarations each rule is about is the rule's own to say (see PartialHelperUse): the fns here
        // hold what this module took on to emit as well as what it wrote.
        for (HelperEntry entry : inliner.held().values()) {
            collect(errors, abandoned,
                    () -> PartialHelperUse.rejectReachingPartial(entry, module.name(), reachability));
        }
        // Every fn and not only the helpers: a behavior's `let` may call a `partial` helper and may not
        // hand it over either.
        for (Hir.FnDef fn : module.fns()) {
            collect(errors, abandoned,
                    () -> PartialHelperUse.rejectNamedAsValue(fn, module.name(), reachability));
        }
        // A fn matching a pipeline is rejected (a pipeline is already its own implementation, so it
        // cannot also have a fn body — spec §fn-declaration). A fn matching a SpecBehavior is that behavior's
        // implementation, which is checked as its own question; any other fn is a helper (checked by
        // checkHelpers). These terminal validations build no state, so each is recovered
        // independently.
        collect(errors, abandoned, () -> {
            for (Hir.FnDef fn : module.fns()) {
                if (!specNames.contains(fn.name()) && allBehaviors.contains(fn.name())) {
                    throw CompileException.of(Diagnostic
                                    .at(fn.pos()).say(new BehaviorMessage.ACompositionIsAlreadyItsOwnImplementation(fn.name())).build());
                }
            }
        });
        // an exposed composition must declare its output in `exposing`, matching the inferred one
        // (spec §declared-composition-output, ADR-0024), so a far-away change cannot grow a published output silently.
        collect(errors, abandoned, () -> SpecChecker.checkUnionMemberNames(module, sigs, symbols));
        collect(errors, abandoned, () -> SpecChecker.checkUnionMemberFields(module, sigs, symbols));
        collect(errors, abandoned, () -> SpecChecker.checkExposedPipeOutputs(module,
                exposed, sigs, symbols));
        // What this module reaches out with may not rest on what it keeps to itself — a name in
        // `exposing`, and an injection target, whose base is public whatever `exposing` says. After
        // the exposing signature checks: a signature that should not be there at all (E1605), or one
        // that disagrees with the pipeline (E1604), is the more particular thing to say.
        collect(errors, abandoned, () -> SpecChecker.checkExposedSurface(module, injectionTargets,
                sigs, symbols, exposeAll, exposed, elaborated.definitionTypes));
    }

    /** Applies {@code f} to every direct subexpression of {@code e}. Delegates to the one
     * exhaustive walk on the AST: written out here again, a node kind added later would fall
     * into a default and be skipped silently by every pass that recurses through this. */
    static void forEachChild(Hir.Expr e, java.util.function.Consumer<Hir.Expr> f) {
        Hir.forEachChild(e, f);
    }

    /** The symbol table of a module compiled on its own: bare names are its own definitions. */
    public static ResolvedSymbols symbols(Hir.Module module, Stdlib stdlib) {
        return Symbols.of(module, stdlib);
    }

}
