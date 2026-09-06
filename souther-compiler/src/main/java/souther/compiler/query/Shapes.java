package souther.compiler.query;

import souther.compiler.ast.Hir;
import souther.compiler.check.ClauseDischarge;
import souther.compiler.check.ExpandedClauseLookup;
import souther.compiler.check.ExpandedClauseResult;
import souther.compiler.check.ExpandedClauses;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.InvariantSettled;
import souther.compiler.check.Lower;
import souther.compiler.check.UninhabitableTypes;
import souther.compiler.check.ClauseHelpers;
import souther.compiler.check.ClausesForDischarge;
import souther.compiler.check.ExecutableInvariants;
import souther.compiler.check.InliningPolicy;
import souther.compiler.check.Unanswerable;
import souther.compiler.check.InvariantChecker;
import souther.compiler.check.DerivedSymbols;
import souther.compiler.check.ResolvedSymbols;
import souther.compiler.core.ValueShape;
import souther.compiler.diag.CompileException;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What each declaration becomes before anything is checked against it, one achievement to a rung:
 * the clauses it wrote expanded to the rules they state ({@link Settling}), the constructions in
 * those clauses written as constructions ({@link NormalizedDeclarations}), and a product's boundary
 * representation read off its declared shape ({@link DerivedDeclarations}).
 *
 * <p>Three rungs and not one, because the three have different preconditions and a reader wants
 * different ones. Normalizing is declaration-local and is answered for every declaration a module
 * writes; deriving a representation reads what the fields name, and a product one of whose fields
 * names no type has none. So a declaration is read as the normalized one whether or not a
 * representation came out — the answers a reader gets do not turn on a question it did not ask.
 *
 * <p>These used to be passes over a whole module in a fixed order, and getting that order wrong was
 * its own class of defect. Here the order is not written anywhere: each answer names what it reads,
 * and reading it is what makes it happen first.
 */
public final class Shapes {

    private Shapes() {}


    /**
     * A module with every invariant clause saying the rule it states — the form each declaration is
     * read from, before what one of them wrote is normalized.
     *
     * <p>Not what a later stage reads. This is what {@link NormalizedDeclarations} works its answers
     * out from, and a mistake reached here is a mistake in the module rather than in any one
     * declaration: the settling reads every declaration to answer about each.
     *
     * <p>Which clauses govern a declaration is a separate question and is not answered here:
     * a clause of a type this one spreads stays that type's, and
     * {@link souther.compiler.check.TypeOps#expandedInvariants} composes them where one is asked
     * for.
     *
     * <p>Settling substitutes what the modules this one imports publish to it, as lowering a body
     * does. The dependency runs the other way from the rest of this file — a shape reaching into
     * bodies — and it is the imported module's bodies it reaches, never this one's: what a module
     * imports is read off its resolved form, so nothing here is asked through itself.
     */
    record Settling(String name) implements Key<InvariantSettled> {
        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<InvariantSettled> compute(Db db) {
            // The module to expand, which is the resolved one where its values are well founded.
            // Everything below here expands a body of it.
            Answer<souther.compiler.check.Expandable> expandable = db.ask(new Expandable(name));
            if (!expandable.present()) {
                return Answer.absent();
            }
            Answer<ResolvedSymbols> scope = Names.resolvedSymbols(db, name);
            if (!scope.present()) {
                return Answer.absent();
            }
            Answer<Map<String, Hir.FnDef>> imported = db.ask(new Bodies.ImportedDefinitions(name));
            // A module whose imports form a cycle takes nothing from them. The cycle is reported where
            // it is found; an invariant naming an imported definition is left unsettled and reported
            // as the unknown name it then is, which is the same answer every other stage gives there.
            Map<String, Hir.FnDef> published = imported.present() ? imported.value() : Map.of();
            try {
                return Answer.of(
                        InvariantSettled.settle(expandable.value(), scope.value(), published));
            } catch (CompileException e) {
                return Answer.absent(e);
            }
        }
    }

    /**
     * The module, where a body of it may be expanded — which is where no value of it is defined in
     * terms of itself.
     *
     * <p>A value is substituted at each of its references (ADR-0072), so one that reaches itself is
     * substituted into itself and there is no body to reach the end of. Everything that expands a body
     * of this module needs that to be false, and needs to know before it expands anything: left until
     * the expansion runs out of stack, what comes back names a nesting the author did not write.
     *
     * <p>It hands over the module rather than answering whether. The rule used to be checked wherever
     * an expansion table was built, which is eleven places in a compile, so the refusal was raised by
     * whichever of them ran first — from inside whatever question that was, which passed it on as a
     * failure of its own. Answering whether moved the problem rather than removing it: three questions
     * read the module and expand it, each said the condition over again, and one of them said it and
     * two did not. A condition a reader has to remember is a condition a reader can forget.
     *
     * <p>So there is one thing to ask for and it is the thing they want. A question that expands a
     * body asks for a module to expand and gets one or gets nothing; there is no answer here that
     * hands over a module without having checked it, and nothing left to remember beside it.
     *
     * <p>What it answers with says so. The check is {@link souther.compiler.check.Expandable#check},
     * which is the only way to that state, so this question is where the check is asked for and not
     * where it is remembered.
     *
     * <p>Read off the resolved module, which is the earliest form that says what each name denotes —
     * and what a name denotes is what decides whether it is an edge at all.
     */
    public record Expandable(String name) implements Key<souther.compiler.check.Expandable> {
        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<souther.compiler.check.Expandable> compute(Db db) {
            Answer<Hir.Module> resolved = db.ask(new Names.Resolved(name));
            if (!resolved.present()) {
                return Answer.absent();
            }
            Answer<Map<String, Hir.FnDef>> imported = db.ask(new Bodies.ImportedDefinitions(name));
            try {
                return Answer.of(souther.compiler.check.Expandable.check(resolved.value(),
                        imported.present() ? imported.value() : Map.of(),
                        db.ask(new Front.Library()).value()));
            } catch (CompileException e) {
                return Answer.absent(e);
            }
        }
    }

    /**
     * One declaration with the boundary representation derived for it.
     *
     * <p>Its own question, so its failure is the named declaration's and not the ones beside it: a
     * product whose field names no type costs itself this answer and costs the rest nothing. A
     * module still derives its declarations together, and this is read through that, so what it
     * depends on is still the module — what it is about is the one declaration.
     *
     * <p>What a later stage resolves a type to is {@link NormalizedDef}, which is answered for every
     * declaration. This is what a reader asking how a value of one crosses is answered from.
     */
    public record DerivedDef(TypeKey named) implements Key<souther.compiler.check.Derived.Def> {
        @Override
        public String module() {
            return named.module();
        }

        @Override
        public Answer<souther.compiler.check.Derived.Def> compute(Db db) {
            Answer<Map<String, souther.compiler.check.Derived.Def>> defs =
                    db.ask(new DerivedDeclarations(named.module()));
            if (!defs.present()) {
                return Answer.absent();
            }
            souther.compiler.check.Derived.Def def = defs.value().get(named.name());
            return def == null ? Answer.absent() : Answer.of(def);
        }
    }

    /**
     * A module's declarations by name, each with the constructions in its clauses written as
     * constructions.
     *
     * <p>This is where a newtype construction written {@code 金額(500)} becomes the construction it
     * is. A construction reaching an invariant through a helper is written in that helper's body,
     * which this module has not desugared yet, so normalizing here rather than with the bodies is
     * what leaves one spelling for every check over an invariant to read.
     *
     * <p><b>The one producer of the normalized form.</b> What reads a declaration and what derives a
     * representation for one are both answered from this, so a declaration is written one way
     * whichever of them is asking. Worked out again by either, the two would be two producers of one
     * form and a declaration could come back from them differently.
     *
     * <p>A declaration at a time, so what is wrong with one clause is wrong with the declaration
     * that wrote it. Every declaration is worked out whether or not the one before it came out —
     * stopping at the first would leave the declarations after it without an answer, and each of
     * them owns what it has to say.
     */
    public record NormalizedDeclarations(String name)
            implements Key<Map<String, souther.compiler.check.Normalized.Def>> {
        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Map<String, souther.compiler.check.Normalized.Def>> compute(Db db) {
            Answer<InvariantSettled> settling = db.ask(new Settling(name));
            Answer<ResolvedSymbols> scope = Names.resolvedSymbols(db, name);
            if (!settling.present() || !scope.present()) {
                return Answer.absent();
            }
            Map<String, souther.compiler.check.Normalized.Def> out = new LinkedHashMap<>();
            for (InvariantSettled.Def def : settling.value().defs()) {
                out.put(def.name(), souther.compiler.check.Normalized.Def.of(def, scope.value()));
            }
            return Answer.of(Map.copyOf(out));
        }
    }

    /**
     * One normalized declaration, asked for by name.
     *
     * <p>What the registry a reader of declarations is answered from reads. Its own key so that a
     * reader wanting one declaration depends on that declaration, the way {@link DerivedDef} does.
     */
    public record NormalizedDef(TypeKey named)
            implements Key<souther.compiler.check.Normalized.Def> {
        @Override
        public String module() {
            return named.module();
        }

        @Override
        public Answer<souther.compiler.check.Normalized.Def> compute(Db db) {
            Answer<Map<String, souther.compiler.check.Normalized.Def>> defs =
                    db.ask(new NormalizedDeclarations(named.module()));
            if (!defs.present()) {
                return Answer.absent();
            }
            souther.compiler.check.Normalized.Def def = defs.value().get(named.name());
            return def == null ? Answer.absent() : Answer.of(def);
        }
    }

    /**
     * The same declarations with a boundary representation derived for each, and only the ones that
     * came out.
     *
     * <p>Read off the normalized declarations rather than worked out from the settled ones, so the
     * one thing this adds is the representation. A declaration missing here is missing for that
     * reason alone, and what reads a declaration is answered from the rung above whether or not this
     * could answer for it.
     */
    public record DerivedDeclarations(String name)
            implements Key<Map<String, souther.compiler.check.Derived.Def>> {
        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Map<String, souther.compiler.check.Derived.Def>> compute(Db db) {
            Answer<Map<String, souther.compiler.check.Normalized.Def>> declarations =
                    db.ask(new NormalizedDeclarations(name));
            Answer<ResolvedSymbols> scope = Names.resolvedSymbols(db, name);
            if (!declarations.present() || !scope.present()) {
                return Answer.absent();
            }
            Map<String, souther.compiler.check.Derived.Def> out = new LinkedHashMap<>();
            declarations.value().forEach((declared, def) -> {
                // A product a field of which does not name a type has no representation to read off
                // its shape, and it is left out rather than entered as one with nothing in it. What
                // that costs is the readers that ask how a value of it crosses; what the declaration
                // says about itself is read from the normalized declarations and is there either
                // way.
                souther.compiler.check.Derived.Def derived =
                        souther.compiler.check.Derived.Def.derive(def, scope.value());
                if (derived != null) {
                    out.put(declared, derived);
                }
            });
            return Answer.of(Map.copyOf(out));
        }
    }

    /**
     * The module every later stage reads, where every declaration in it came out.
     *
     * <p>An assembly and not a stage of its own: each declaration is answered on its own and says
     * what it has to say there, and this is the conjunction of those answers. One that did not come
     * out leaves no module to hand over — a module missing a declaration it writes would be read as
     * one that does not declare it, which is a different thing to say and not a true one — while the
     * declarations beside it keep the answers they have.
     */
    public record Derived(String name) implements Key<souther.compiler.check.Derived.Module> {
        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<souther.compiler.check.Derived.Module> compute(Db db) {
            Answer<InvariantSettled> settling = db.ask(new Settling(name));
            Answer<Map<String, souther.compiler.check.Derived.Def>> declarations =
                    db.ask(new DerivedDeclarations(name));
            if (!settling.present() || !declarations.present()) {
                return Answer.absent();
            }
            souther.compiler.check.Derived.Module assembled =
                    souther.compiler.check.Derived.Module.assemble(settling.value(),
                            declarations.value());
            return assembled == null ? Answer.absent() : Answer.of(assembled);
        }
    }

    /**
     * A module with each newtype construction — {@code 金額(500)} — rewritten to a construction of
     * that type. Only the module's fns change; what it declares is what {@link Derived} left, which
     * is why every stage below reads its declarations from there and not from here.
     */
    public record Desugared(String name) implements Key<souther.compiler.check.Desugared.Module> {
        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<souther.compiler.check.Desugared.Module> compute(Db db) {
            Answer<souther.compiler.check.Derived.Module> derived = db.ask(new Derived(name));
            Answer<Map<String, souther.compiler.check.Desugared.Fn>> fns =
                    db.ask(new DesugaredFns(name));
            if (!derived.present() || !fns.present()) {
                return Answer.absent();
            }
            souther.compiler.check.Desugared.Module assembled =
                    souther.compiler.check.Desugared.Module.assemble(derived.value(), fns.value());
            return assembled == null ? Answer.absent() : Answer.of(assembled);
        }
    }

    /**
     * A module's definitions by name, each with the newtype constructions written in its body
     * rewritten to the constructions they are.
     *
     * <p>Every one of them. The rewrite writes what is a construction as one and leaves what is not
     * as it was, so there is no body it comes back with nothing for — and the module above is
     * assembled from all of them, so one that came back with nothing would take the reading away
     * from the definitions beside it.
     */
    public record DesugaredFns(String name)
            implements Key<Map<String, souther.compiler.check.Desugared.Fn>> {
        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Map<String, souther.compiler.check.Desugared.Fn>> compute(Db db) {
            Answer<InvariantSettled> settling = db.ask(new Settling(name));
            Answer<DerivedSymbols> scope = Names.derivedSymbols(db, name);
            if (!settling.present() || !scope.present()) {
                return Answer.absent();
            }
            Map<String, souther.compiler.check.Desugared.Fn> out = new LinkedHashMap<>();
            for (Hir.FnDef fn : settling.value().fns()) {
                out.put(fn.name(),
                        souther.compiler.check.Desugared.Fn.desugar(fn, scope.value()));
            }
            return Answer.of(Map.copyOf(out));
        }
    }

    /**
     * The module a best-effort reading runs over: the parts joined, and no claim that every
     * declaration came out.
     *
     * <p>What a diagnostic or a measurement is given. A module one of whose declarations has no
     * representation still has definitions of its own, and what is wrong with the one costs the
     * readers that name it and no others — so this is answered where {@link Prepared} is not, and
     * the reading goes on.
     *
     * <p>Its declarations are every one the module writes, as {@link NormalizedDeclarations}
     * answered for them, so a declaration no representation could be derived for is here in the same
     * spelling as the ones beside it.
     */
    public record CheckSurface(String name) implements Key<souther.compiler.check.CheckSurface> {
        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<souther.compiler.check.CheckSurface> compute(Db db) {
            Answer<InvariantSettled> settling = db.ask(new Settling(name));
            Answer<Map<String, souther.compiler.check.Normalized.Def>> normalized =
                    db.ask(new NormalizedDeclarations(name));
            Answer<ResolvedSymbols> resolved = Names.resolvedSymbols(db, name);
            Answer<DerivedSymbols> scope = Names.derivedSymbols(db, name);
            Answer<Map<String, souther.compiler.check.Desugared.Fn>> fns =
                    db.ask(new DesugaredFns(name));
            Answer<Map<souther.compiler.types.ValueName.Behavior, souther.compiler.check.Sig>>
                    signatures = db.ask(new Bodies.Reachable(name));
            Answer<souther.compiler.check.FakeTables> declared =
                    db.ask(new Names.FakeTables(name));
            if (!settling.present() || !normalized.present() || !resolved.present()
                    || !scope.present() || !fns.present() || !declared.present()) {
                return Answer.absent();
            }
            try {
                souther.compiler.check.CheckSurface assembled =
                        souther.compiler.check.CheckSurface.assemble(
                                settling.value(), normalized.value(), fns.value(), scope.value(),
                                signatures.present() ? signatures.value() : Map.of(),
                                declared.value());
                // A definition that did not desugar is missing from what was handed in, and a
                // surface without it would be this module read as one that does not write it.
                return assembled == null ? Answer.absent() : Answer.of(assembled);
            } catch (CompileException e) {
                return Answer.absent(e);
            }
        }
    }

    /**
     * The module a codegen and everything that needs a whole one run over: the same assembly a
     * check reads, beside the witness that every declaration the module writes came out.
     *
     * <p>Absent where one did not. There is nothing to emit for a module holding a declaration with
     * no boundary representation, and an example run over it would be running against classes that
     * were never written — while the diagnostics and the measurements that read
     * {@link CheckSurface} go on saying what they can.
     */
    public record Prepared(String name) implements Key<souther.compiler.check.Prepared> {
        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<souther.compiler.check.Prepared> compute(Db db) {
            Answer<souther.compiler.check.Desugared.Module> desugared = db.ask(new Desugared(name));
            Answer<souther.compiler.check.CheckSurface> surface = db.ask(new CheckSurface(name));
            if (!desugared.present() || !surface.present()) {
                return Answer.absent();
            }
            return Answer.of(souther.compiler.check.Prepared.prepare(
                    desugared.value(), surface.value()));
        }
    }

    /**
     * How each clause of each invariant this module declares can be discharged at compile time (spec
     * §invariant-discharge-capability), in the order the clauses are written.
     *
     * <p>Only this module's own declarations. The classification is the clause's, and a clause is
     * written where its type is declared; a reader in another module asks that module.
     */
    public record InvariantCapabilities(String name)
            implements Key<Map<TypeSymbol, List<ClauseDischarge>>> {
        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Map<TypeSymbol, List<ClauseDischarge>>> compute(Db db) {
            Answer<souther.compiler.check.Expandable> expandable = db.ask(new Expandable(name));
            Answer<ResolvedSymbols> scope = Names.resolvedSymbols(db, name);
            Answer<RuleReadingSource> reading =
                    ruleReading(db, name);
            if (!expandable.present() || !scope.present() || !reading.present()) {
                return Answer.absent();
            }
            Answer<Map<String, Hir.FnDef>> imported = db.ask(new Bodies.ImportedDefinitions(name));
            Map<String, Hir.FnDef> published = imported.present() ? imported.value() : Map.of();
            // What the clause says is what the check reads, so an imported bound is substituted here
            // as it is where the invariant is settled. A clause left naming it would be classified as
            // a rule this analysis cannot read, and a construction the bound rejects would compile.
            try {
                // The one reader of a clause: it holds the expansion, and what it hands back is a
                // conjunct that knows where it was written and what it comes to. Nothing here places
                // an answer, so nothing here can place one wrongly.
                ClausesForDischarge declaring =
                        ClausesForDischarge.of(expandable.value(), scope.value(), published);
                Map<TypeSymbol, List<ClauseDischarge>> out = new LinkedHashMap<>();
                for (Hir.Data data : declaring.declarationsThatState()) {
                    List<ClauseDischarge> clauses = new ArrayList<>();
                    TypeSymbol.AtModule named = data.declares();
                    // A declared clause is one rule to depart by and may still be several conjuncts to
                    // discharge, so `a && b` under one name is classified twice under that name: what
                    // discharges each half is what an author needs, and the name is what a caller reads.
                    for (Hir.InvariantClause declared : data.invariants()) {
                        for (ClausesForDischarge.ClauseReading written
                                : declaring.conjunctsOf(declared.expr(), new BindingOwner.OfData(named))) {
                            clauses.add(InvariantChecker.capabilityOf(written, named,
                                    reading.value(),
                                    db.ask(new Front.Reading()).value()).named(declared.name()));
                        }
                    }
                    out.put(named, List.copyOf(clauses));
                }
                return Answer.of(Map.copyOf(out));
            } catch (CompileException e) {
                return Answer.absent(e);
            }
        }
    }

    /**
     * Which of this module's declarations no value satisfies, and what shows it.
     *
     * <p>An answer of its own so that what a body's check depends on is this and not the clauses it
     * was worked out from. A body is refused where a type it names has no value, so the fact is one
     * a body's check reads; the clauses of every declaration beside it are not, and a check that
     * reached for them would be re-run by a declaration that cannot change its answer.
     *
     * <p>What is answered is the groups and not the diagnostics they are rendered as. A group is
     * some names and what showed them empty, which two readings of the same module settle the same
     * way; a diagnostic carries a position and a sentence, and comparing those would make this
     * answer differ whenever the file moved.
     */
    public record TypesWithNoValue(String name) implements Key<UninhabitableTypes.WithNoValue> {
        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<UninhabitableTypes.WithNoValue> compute(Db db) {
            Answer<Lower.Lowered> lowering = db.ask(new Bodies.Lowering(name));
            Answer<RuleReadingSource> reading = ruleReading(db, name);
            Answer<souther.compiler.check.ReadingPolicy> policy = db.ask(new Front.Reading());
            if (!lowering.present() || !policy.present()) {
                return Answer.absent();
            }
            // Answered either way, because what a reader of this does about a count it has not been
            // given is that reader's: a module whose declarations could not be checked still has
            // everything else about it to report, and going absent here would take that with it.
            if (!reading.present()) {
                return Answer.of(new UninhabitableTypes.WithNoValue.NotCounted());
            }
            List<Hir.Def> declarations = lowering.value().settled().defs();
            try {
                souther.compiler.check.TypeCardinality.Cardinalities counted =
                        souther.compiler.check.TypeCardinality.solve(
                                declarations, reading.value(), policy.value());
                // Not counted where a rule the count read could not be read at all. What makes a
                // type have no value is what its rules leave, so a count short of one of them may
                // have missed the rule that empties a type — and would report it as inhabited.
                // Asked of what the count reached and not of the module it started in: the rule
                // that empties a type can be written on a declaration of any module it walks into.
                if (!counted.everyRuleReached()) {
                    return Answer.of(new UninhabitableTypes.WithNoValue.NotCounted());
                }
                return Answer.of(new UninhabitableTypes.WithNoValue.Counted(
                        UninhabitableTypes.withNoValueOfTheirOwn(declarations, counted)));
            } catch (CompileException e) {
                // Said here, as what this attempt found, and not handed on in the answer: a reader
                // of the answer is told there was no count and concludes nothing from it, which is
                // the whole of what it may do with a count that did not happen.
                return Answer.of(new UninhabitableTypes.WithNoValue.NotCounted(), Report.of(e));
            }
        }
    }

    /**
     * What reading this module's declarations as a static analysis takes: its scope, and its clauses
     * in the representation that analysis reads.
     *
     * <p>Where the two meet. The representation's own answer is computed from the scope, so a scope
     * that carried it would be a query depending on itself; asked separately, a reader that needs
     * both can be given one. What a reader below is handed is the pair or nothing.
     *
     * <p>Paired here and not memoised as an answer of its own. What a query answers has to say when
     * two of them are the same thing, and a scope does not: made an answer, this pair would compare
     * by identity, every recomputation would look like a change, and everything downstream of a
     * module's clauses would be re-checked on a blank line. The two halves are answers and settle
     * that between them; the pair is what a caller holds while it reads.
     */
    public static Answer<RuleReadingSource> ruleReading(Db db, String name) {
        Answer<ResolvedSymbols> scope = Names.resolvedSymbols(db, name);
        return scope.present()
                ? Answer.of(new RuleReadingSource(scope.value(), expandedClauses(db)))
                : Answer.absent();
    }

    /**
     * Where a reading of any module's rules gets a declaration's expanded clauses.
     *
     * <p>One of these for the whole compilation and not one per reader, because which declaration is
     * being asked about is the only input there is. A lookup made for a module would be a lookup
     * that could answer that module's way, which is the arrangement this replaces.
     */
    public static ExpandedClauseLookup expandedClauses(Db db) {
        return named -> db.ask(new ClausesExpandedFor(named)).value();
    }

    /**
     * The invariants this module declares, in the representation the invariant-discharge analysis
     * reads ({@link souther.compiler.check.InliningPolicy#DISCHARGE}) — beside the settled form that
     * every other stage sees on the declaration itself.
     *
     * <p>Only the clauses this module declares. What another module publishes arrives settled, which
     * is what makes an imported clause fall outside the statically dischargeable fragment: the
     * analysis reads what it is given, and there the operations have already become the folds they
     * are. A clause declared here that names an imported definition is this module's clause and stays
     * inside the fragment — the definition is substituted, as it is everywhere the invariant is read.
     *
     * <p><b>Not what a reader asks for.</b> Expanding is done a module at a time because that is
     * what the environment a clause is expanded in belongs to, and that is the whole of why this
     * key exists. What a reading is answered from is {@link ClausesExpandedFor}, one declaration at
     * a time: a reader able to name a module here is a reader that could ask for its own module's
     * answer about somebody else's declaration.
     */
    record ExpandedDeclarationClauses(String name)
            implements Key<Map<TypeKey, ExpandedClauses>> {
        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Map<TypeKey, ExpandedClauses>> compute(Db db) {
            Answer<souther.compiler.check.Expandable> expandable = db.ask(new Expandable(name));
            Answer<ResolvedSymbols> scope = Names.resolvedSymbols(db, name);
            if (!expandable.present() || !scope.present()) {
                return Answer.absent();
            }
            Answer<Map<String, Hir.FnDef>> imported = db.ask(new Bodies.ImportedDefinitions(name));
            Map<String, Hir.FnDef> published = imported.present() ? imported.value() : Map.of();
            try {
                return Answer.of(ClauseHelpers.expandedClausesOf(
                        expandable.value(), scope.value(), published));
            } catch (CompileException e) {
                return Answer.absent(e);
            }
        }
    }

    /**
     * One declaration's clauses in the representation a reading of rules takes, answered by the
     * module that wrote it.
     *
     * <p>The key is the declaration and nothing else. Where the clauses are worked out follows from
     * the declaration's own address, so two modules asking about one declaration are asking one
     * question and get one answer — which is what
     * spec §invariant-discharge-representation requires and what reading them off whatever tree the
     * asker held did not give.
     *
     * <p><b>A present batch answers for every declaration its module wrote.</b> So a declaration
     * missing from one is this compiler having failed to hand its own reading over, and is refused
     * rather than read as a declaration stating nothing. A batch that is not there at all is the
     * other thing — a module that does not compile, or whose imports form a cycle — and is passed
     * on as the absence it is.
     */
    public record ClausesExpandedFor(TypeKey named) implements Key<ExpandedClauseResult> {
        @Override
        public String module() {
            return named.module();
        }

        @Override
        public Answer<ExpandedClauseResult> compute(Db db) {
            // The kind first, and the module that would expand it second. Which kinds have an
            // `invariant` to write is the HIR's answer and holds whoever declared one, so a sum is
            // answered before there is any question of an environment to expand in — asked the other
            // way round, the language's own declarations, which are the only ones no compilation
            // module wrote, would come back as clauses nobody could work out.
            Hir.Def declared = declarationOf(db, named);
            if (declared == null) {
                return Answer.of(new ExpandedClauseResult.NotDeclared(named));
            }
            if (!(declared instanceof Hir.Data)) {
                return Answer.of(new ExpandedClauseResult.Found(
                        ClauseHelpers.noClausesToExpand(named)));
            }
            Answer<Map<TypeKey, ExpandedClauses>> expanded =
                    db.ask(new ExpandedDeclarationClauses(named.module()));
            if (!expanded.present()) {
                // What the module was told about itself travels with the absence. Flattened to a
                // bare `absent`, the reports its expansion produced would be dropped here and the
                // reader would be short of the clauses and of the reason both.
                return Answer.of(new ExpandedClauseResult.Unavailable(named), expanded.reports());
            }
            ExpandedClauses clauses = expanded.value().get(named);
            if (clauses == null) {
                throw new NothingWasExpandedFor(named);
            }
            return Answer.of(new ExpandedClauseResult.Found(clauses), expanded.reports());
        }

        /** The declaration {@code named} is, whether a module of this compilation wrote it or the
         *  language declares it, or null where nothing does. */
        private static Hir.Def declarationOf(Db db, TypeKey named) {
            Answer<Hir.Def> mine = db.ask(new Names.ResolvedDeclaration(named));
            if (mine.present()) {
                return mine.value();
            }
            Answer<souther.compiler.stdlib.Stdlib> library = db.ask(new Front.Library());
            return library.present() ? library.value().languageDeclaration(named) : null;
        }
    }

    /**
     * Raised where a module's expansion came out and says nothing about a declaration it wrote.
     *
     * <p>The expansion of a module is what every reader of that module's clauses is answered from,
     * so an expansion that came out and says nothing about a declaration it wrote is two of this
     * compiler's answers disagreeing. Read as an ordinary answer it would say the declaration was
     * expanded and found to state little, which is what a declaration stating little says.
     */
    public static final class NothingWasExpandedFor extends IllegalStateException {

        private static final long serialVersionUID = 1L;

        NothingWasExpandedFor(TypeKey named) {
            super("the expansion of " + named.module() + " says nothing about "
                    + named.qualified() + ", which it declares");
        }
    }

    /**
     * What a value of each of this module's declared data is made of, and what must hold of one.
     *
     * <p>The reading that runs, made where the check is. A clause is elaborated once here and read
     * by the check that holds it to being a condition, by the emitter that refuses a construction
     * with it, and by a checked program — none of which elaborates one of its own (issue #1080).
     *
     * <p>Both halves together ({@link ValueShape}): the fields a clause reads and the clauses that
     * read them. Handed over apart, whoever ran a clause would work out where a field is read
     * through, and that walk and this one would have to be kept answering alike.
     *
     * <p>Only the declarations that have a meaning. What could not be settled is not here and
     * nothing here asks why — the same reading the module check makes of the same key, so a
     * declaration with no meaning is not reported twice.
     *
     * <p>Clause by clause and declaration by declaration: two data each carrying a clause that is
     * not a condition are two things for an author to fix.
     */
    public record ValueShapes(String name) implements Key<Map<TypeSymbol.AtModule, ValueShape>> {
        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Map<TypeSymbol.AtModule, ValueShape>> compute(Db db) {
            Answer<Hir.Module> settled = db.ask(new Bodies.Settled(name));
            Answer<DerivedSymbols> scope = Names.derivedSymbols(db, name);
            Answer<Map<String, souther.compiler.types.Type>> helpers =
                    db.ask(new Bodies.RecursiveCallSigs(name, InliningPolicy.FULL));
            if (!settled.present() || !scope.present() || !helpers.present()) {
                return Answer.absent();
            }
            Map<TypeSymbol.AtModule, ValueShape> shapes = new LinkedHashMap<>();
            List<Report> reports = new ArrayList<>();
            for (Hir.Def def : settled.value().defs()) {
                if (!(def instanceof Hir.Data data)
                        || !db.ask(new Names.Definition(def.declaredKey())).present()) {
                    continue;
                }
                try {
                    shapes.put(data.declares(),
                            ExecutableInvariants.of(data, scope.value(), helpers.value()));
                } catch (Unanswerable _) {
                    // Rests on something already reported where it went wrong.
                } catch (CompileException e) {
                    reports.addAll(Report.of(e));
                }
            }
            return Answer.of(Ordered.map(shapes), reports);
        }
    }
}
