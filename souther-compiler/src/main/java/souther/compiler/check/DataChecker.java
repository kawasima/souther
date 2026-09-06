package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.core.Core;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.msg.InvariantMessage;
import souther.compiler.diag.msg.BehaviorMessage;
import souther.compiler.diag.msg.TypeMessage;
import souther.compiler.diag.msg.CodecMessage;
import souther.compiler.diag.msg.DataMessage;
import souther.compiler.diag.Region;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.BindingId;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The declaration-level checks: a {@code data}'s fields and invariant, a sum's cases, the decoder
 * and encoder written against them, and every construction of an invariant-bearing type.
 *
 * <p>These run per definition, so a failure in one is collected and the next is still checked
 * (see {@code TypeChecker.collect}).
 */
public final class DataChecker {

    private DataChecker() {}

    /** The no-argument methods of {@code Object}: a field of one of these names would generate an
     * accessor that collides with a method the class already has, so none can be a field name. This is
     * the same list the JLS keeps a record component off. */
    private static final Set<String> OBJECT_METHOD_NAMES = Set.of(
            "clone", "finalize", "getClass", "hashCode", "notify", "notifyAll", "toString", "wait");

    /** A constant newtype construction to verify after codegen: its wrapped constant and its site.
     * {@code type} says which module declared it — the check runs that module's generated class, not
     * one named after the module the construction is written in. {@code typeName} is what was
     * written, which is what the message quotes. */
    public record ConstCheck(String typeName, TypeSymbol.AtModule type, Object value,
                             SourcePos pos) {}

    /**
     * Every {@code 金額(constant)} in the module: a newtype construction whose argument folds to a
     * compile-time constant. The compiler runs each through the generated {@code $Ctfe.check}
     * (CTFE) so a violation becomes a compile error rather than a run-time abort (ADR-0032).
     *
     * <p>Desugared definitions, because that is what this finds them by. A construction is matched
     * as {@link Hir.NewData}, and a body that has not been desugared writes {@code 金額(500)} as an
     * application — so the walk matches nothing, the list comes back empty, and the compile goes on
     * with no check made and nothing said. Over a compile of the suite that is 174 constructions
     * found against 1: the state in the signature is what stops a caller handing over the bodies
     * from a rung below and being told the module is clean.
     */
    public static List<ConstCheck> constNewtypeChecks(List<Desugared.Fn> fns, Symbols symbols) {
        List<ConstCheck> out = new ArrayList<>();
        for (Desugared.Fn fn : fns) {
            collectConstChecks(fn.read().writtenBody(), symbols, out);
        }
        return out;
    }

    private static void collectConstChecks(Hir.Expr e, Symbols symbols, List<ConstCheck> out) {
        if (e instanceof Hir.NewData nd
                && nd.typeName() instanceof Hir.Name.Denoting built
                && built.type() instanceof TypeSymbol.AtModule constructed
                && symbols.declaredNode(constructed) instanceof Hir.Data nt
                && nt.newtype() && isInvariantBearing(constructed, symbols)) {
            CallElaborator.newtypeConstantArg(nd, symbols).ifPresent(v ->
                    out.add(new ConstCheck(nd.typeName().written(), constructed, v, nd.pos())));
        }
        TypeChecker.forEachChild(e, c -> collectConstChecks(c, symbols, out));
    }

    public static boolean isInvariantBearing(TypeSymbol.AtModule typeName, Symbols symbols) {
        return typeName != null
                && !TypeOps.invariantHeadersGoverning(typeName, symbols).isEmpty();
    }

    /**
     * No two clauses that apply to a data share a name. A spread carries the clauses of what it takes
     * in, names included, so the clash this reports is between a clause written here and one arriving
     * by spread as often as between two written here — and either way an arm naming it would answer
     * neither rule in particular.
     */
    private static void checkClauseNames(Hir.Data data, Symbols symbols) {
        Set<String> seen = new HashSet<>();
        for (InvariantHeader clause : TypeOps.invariantHeadersGoverning(data.declares(), symbols)) {
            String name = clause.name().orElse(null);
            if (name != null && !seen.add(name)) {
                throw CompileException.of(Diagnostic
                                .at(clause.pos()).say(new InvariantMessage.TwoClausesShareOneName(name, data.name())).build());
            }
        }
    }

    /**
     * What a body builds <b>that a construction set is made of</b>, in the two kinds a permission
     * check tells apart: {@code originated} is what this body is answerable for, {@code carried}
     * what another module's published value or helper built and this body was handed. Each maps the
     * type to the spelling it was written with, so a report quotes the source.
     *
     * <p><b>Not every construction is in here, and the two questions are not the same one</b> (spec
     * §constructs-excludes-unit-data). {@code let f (_) = Domestic} constructs the unit and this
     * answers {@code builds(Domestic) == false}, because a unit carries no construction authority to
     * be declared or checked. A reader wanting every construction — for a walk over what a body
     * makes, rather than over what its clause must name — wants its own reader and not this one.
     *
     * <p>The split is what makes the carried ones invisible to the requirement and still visible to
     * the redundancy check: a behavior does not have to declare `constructs` for a construction that
     * came in with a published body — it may have no name for the type — and a behavior that declares
     * one anyway is not told it builds nothing.
     */
    public record Constructs(Map<TypeSymbol, String> originated, Map<TypeSymbol, String> carried) {

        public static Constructs empty() {
            return new Constructs(new LinkedHashMap<>(), new LinkedHashMap<>());
        }

        /** Whether {@code built} is in the set this body contributes, however it got here.
         *  A unit data never is, whatever the body writes (spec §constructs-excludes-unit-data). */
        public boolean builds(TypeSymbol built) {
            return originated.containsKey(built) || carried.containsKey(built);
        }

        public Constructs copy() {
            return new Constructs(new LinkedHashMap<>(originated), new LinkedHashMap<>(carried));
        }

        /** The same set with everything counted as carried — what a body reached through something
         * it was handed rather than wrote, and so answers for none of. */
        public Constructs allCarried() {
            Map<TypeSymbol, String> all = new LinkedHashMap<>(carried);
            originated.forEach(all::putIfAbsent);
            return new Constructs(new LinkedHashMap<>(), all);
        }

        /** Takes on everything {@code other} builds, each kind staying the kind it is, and answers
         * whether that added anything. */
        public boolean absorb(Constructs other) {
            boolean added = false;
            for (Map.Entry<TypeSymbol, String> e : other.originated.entrySet()) {
                added |= originated.putIfAbsent(e.getKey(), e.getValue()) == null;
            }
            for (Map.Entry<TypeSymbol, String> e : other.carried.entrySet()) {
                added |= carried.putIfAbsent(e.getKey(), e.getValue()) == null;
            }
            return added;
        }
    }

    /**
     * What {@code e} contributes to the construction set of the behavior it belongs to.
     *
     * <p>Narrower than "the data types {@code e} constructs", and the difference is the unit data
     * (spec §constructs-excludes-unit-data). A bare unit name still constructs its value and is not
     * collected.
     *
     * <p>{@code bound} carries the names in scope, because a bare identifier is a unit data's
     * construction only when nothing has bound it — a local of the same name wins (spec §unit-data).
     * Without it, a parameter named after a unit data was read as constructing that unit.
     */
    static void collectConstructs(Hir.Expr e, Map<TypeSymbol, String> out, Symbols symbols,
                                  Map<String, Constructs> recConstructs) {
        Constructs all = Constructs.empty();
        collectConstructs(e, all, symbols, recConstructs);
        out.putAll(all.originated());
    }

    static void collectConstructs(Hir.Expr e, Constructs out, Symbols symbols,
                                          Map<String, Constructs> recConstructs) {
        switch (e) {
            case Hir.LetIn li -> {
                collectConstructs(li.value(), out, symbols, recConstructs);
                collectConstructs(li.body(), out, symbols, recConstructs);
            }
            // An expansion builds what its arguments build and what the callee's body builds. What a
            // function argument builds is counted from the body, where the callee applies it; counted
            // here as well, one lambda's construction would be recorded twice.
            case Hir.Expansion ex -> {
                for (Hir.Bound b : ex.bound()) {
                    collectConstructs(b.value(), out, symbols, recConstructs);
                }
                collectConstructs(ex.body(), out, symbols, recConstructs);
            }
            case Hir.NewData nd -> {
                // A construction this body was handed is not this body's. It is handed one two ways.
                // A module's published value or helper carries its own: publishing the definition is
                // what states that origination, and the reader has no name to declare a type the
                // module keeps to itself by. What that does not cover is a type of some *other*
                // module the published body happened to build — that origination is neither the
                // reader's nor the publisher's to state, so it stays this behavior's to declare,
                // which it can. A value carries the construction its definition made, whatever module
                // declares the type: the definition is where the value is made, and a body that names
                // it compares against a limit rather than setting one.
                // A construction naming nothing builds no type to record; it is reported where the
                // name is written, and the fields written under it are still walked.
                if (nd.typeName() instanceof Hir.Name.Denoting built) {
                    Map<TypeSymbol, String> side =
                            built.type() instanceof TypeSymbol.AtModule made && nd.wasCarried(made)
                                    ? out.carried() : out.originated();
                    side.putIfAbsent(built.type(), nd.typeName().name().quoted());
                }
                for (Hir.FieldInit init : nd.inits()) {
                    collectConstructs(init.value(), out, symbols, recConstructs);
                }
            }
            case Hir.FieldAccess fa -> collectConstructs(fa.target(), out, symbols, recConstructs);
            case Hir.Tuple tup -> tup.elements().forEach(el -> collectConstructs(el, out, symbols, recConstructs));
            case Hir.TupleGet tg -> collectConstructs(tg.tuple(), out, symbols, recConstructs);
            case Hir.Apply call -> {
                // a recursive helper is not inlined, so its own (transitive) constructions are
                // attributed to the behavior that calls it, exactly as an inlined helper's would be —
                // each as the kind it already is, so one another module published stays that
                // module's here as it does when the body is expanded. Where the call itself came in
                // on a value, none of them are this body's: the value's definition is what reached
                // the helper, and the call is standing in for constructions that would have been
                // marked had the helper been expandable.
                Constructs viaHelper = call.answered() == null
                        ? null : recConstructs.get(call.answered().reaches());
                if (viaHelper != null) {
                    out.absorb(call.wasCarriedByValue() ? viaHelper.allCarried() : viaHelper);
                }
                call.args().forEach(a -> collectConstructs(a, out, symbols, recConstructs));
            }
            case Hir.Binary bin -> {
                collectConstructs(bin.left(), out, symbols, recConstructs);
                collectConstructs(bin.right(), out, symbols, recConstructs);
            }
            case Hir.Neg neg -> collectConstructs(neg.operand(), out, symbols, recConstructs);
            case Hir.Match m -> {
                collectConstructs(m.scrutinee(), out, symbols, recConstructs);
                for (Hir.Case c : m.cases()) {
                    collectConstructs(c.body(), out, symbols, recConstructs);
                }
            }
            case Hir.If iff -> {
                collectConstructs(iff.cond(), out, symbols, recConstructs);
                collectConstructs(iff.then(), out, symbols, recConstructs);
                collectConstructs(iff.els(), out, symbols, recConstructs);
            }
            // an attempt builds the value on its success branch, so it needs the same permission a
            // plain construction does — what it does not do is abort when it fails
            case Hir.IfConstructed ic -> {
                collectConstructs(ic.construct(), out, symbols, recConstructs);
                collectConstructs(ic.then(), out, symbols, recConstructs);
                ic.els().forEach(arm ->
                        collectConstructs(arm.body(), out, symbols, recConstructs));
            }
            case Hir.ListLit lit -> lit.elements().forEach(el -> collectConstructs(el, out, symbols, recConstructs));
            // Whichever collection the brackets turn out to be, what a row writes in them is what it
            // builds: the notation decides the container and not what is put in it.
            case Hir.RowCollection row ->
                    row.elements().forEach(el -> collectConstructs(el, out, symbols, recConstructs));
            case Hir.ListComp comp -> {
                collectConstructs(comp.element(), out, symbols, recConstructs);
                comp.guards().forEach(g -> collectConstructs(g, out, symbols, recConstructs));
            }
            // a block builds under the enclosing behavior's permission (spec §blocks)
            case Hir.Block block -> collectConstructs(block.body(), out, symbols, recConstructs);
            case Hir.IntLit _ -> { }
            case Hir.DecimalLit _ -> { }
            case Hir.StringLit _ -> { }
            case Hir.BoolLit _ -> { }
            // A bare name denoting a unit data constructs that unit's value (spec §unit-data) and
            // is still not collected here. A construction set records what a behavior holds authority to
            // build, and a unit needs none: it has no fields, can carry no invariant, and has one
            // value, so minting another is not tellable from passing the existing one through — which
            // is the distinction the clause exists to record (spec §constructs-excludes-unit-data).
            // Collected, it made the position a unit name is written in decide the clause: comparing
            // against a case (`r.kind == Domestic`) demanded an entry that opening the same case
            // with `match` did not, since only the first is an expression.
            case Hir.Var _ -> { }
            // it builds nothing: no value is made where it stands
            case Hir.Unreachable _ -> { }
        }
    }

    /** Rejects a name listed more than once in a declaration ({@code where}). The duplicate is
     * meaningless and, left to codegen, would emit a duplicate JVM member — a duplicate method,
     * field, or implemented interface, i.e. a malformed class file. */
    static void rejectDuplicateNames(List<String> names, String where, SourcePos pos) {
        Set<String> seen = new HashSet<>();
        for (String n : names) {
            if (!seen.add(n)) {
                throw duplicate(n, where, pos);
            }
        }
    }

    private static CompileException duplicate(String written, String where, SourcePos pos) {
        return CompileException.of(Diagnostic.at(pos)
                .say(new DataMessage.NameIsListedMoreThanOnce(written, where))
                .build());
    }

    /**
     * As above for a list of type names. Two entries are the same when they denote one type, so
     * `Amount` an import brings in and `up.Amount` are the duplicate a spelling comparison misses.
     */
    static void rejectDuplicateTypes(List<Hir.Name> names, String where, SourcePos pos) {
        Set<TypeSymbol> seen = new HashSet<>();
        for (Hir.Name n : names) {
            // A name nothing declares denotes no type, so there is no type here for another to be
            // the same as. It was reported where it is written; calling it a duplicate as well
            // would be a second report about the one mistake.
            if (n.answered() == null) {
                continue;
            }
            if (!seen.add(n.answered().type())) {
                throw duplicate(n.written(), where, pos);
            }
        }
    }

    /** Where to point at a field the data declares — the field's own name — or at the data header for
     * one that arrived through a {@code ...} spread, which is written in the data it came from. */
    private static Region fieldRegion(Hir.Data data, String field) {
        for (Hir.Field f : data.fields()) {
            if (f.name().equals(field)) {
                return Region.ofWidth(f.pos(), field.length());
            }
        }
        return Region.point(data.pos());
    }

    /** The path back to {@code target} through sum cases, or null when it does not reach itself. A
     * sum names the values it can be, so a case that is (or reaches) the sum itself describes nothing:
     * it has no leaf to dispatch a codec over and no case list a {@code match} could be exhaustive
     * against. Reported here rather than left to the walks, which would recurse until the stack ran
     * out — codec derivation runs before this check and stops at the repeat for the same reason. */
    private static List<String> sumCycle(TypeSymbol target, Symbols symbols,
                                         SequencedSet<TypeSymbol> path) {
        if (!(symbols.declaredNode(
                path.isEmpty() ? target : last(path)) instanceof Hir.SumData s)) {
            return null;
        }
        for (Hir.Name caseName : s.cases()) {
            // A case naming nothing is no step of a cycle, and it is reported on the declaration
            // that writes it — which may be another module's, and not one this check was handed.
            if (!(caseName instanceof Hir.Name.Denoting names)) {
                continue;
            }
            if (target.equals(names.type())) {
                List<String> out = new ArrayList<>();
                for (TypeSymbol seen : path) {
                    out.add(seen.name());
                }
                out.add(caseName.written());
                return out;
            }
            if (symbols.declaredNode(names.type()) instanceof Hir.SumData
                    && path.add(names.type())) {
                List<String> found = sumCycle(target, symbols, path);
                if (found != null) {
                    return found;
                }
                path.remove(names.type());
            }
        }
        return null;
    }

    private static TypeSymbol last(SequencedSet<TypeSymbol> path) {
        TypeSymbol out = null;
        for (TypeSymbol t : path) {
            out = t;
        }
        return out;
    }

    /**
     * The declaration a name written in the declaration being checked names.
     *
     * <p>Every name written in one of these was answered. A declaration holding a name that names
     * nothing has no meaning to check — {@code Names.Unbuilt} collects it and {@code Names.Definition}
     * hands it to nobody — and {@code TypeChecker} checks only the declarations it was handed. One
     * arriving here is this compiler checking a declaration that was not among them.
     */
    private static TypeSymbol names(Hir.Name name) {
        return switch (name) {
            case Hir.Name.Denoting d -> d.type();
            case Hir.Name.Unanswered u -> throw u.unexpectedHere();
        };
    }

    static void checkSum(Hir.SumData sum, Symbols symbols) {
        rejectDuplicateTypes(sum.cases(), "the sum `" + sum.name() + "`", sum.pos());
        // A generated sum is a sealed interface, and its `permits` is settled when its own module is
        // generated — a case of another module cannot implement it, so it would be permitted without
        // being a member: no value satisfies the type and an exhaustive switch over it has no arms
        // (ADR-0057, the declared-sum counterpart of E1606).
        for (Hir.Name c : sum.cases()) {
            // A case the language gives is declared by no module and so by none of this one's;
            // the module a case is declared in is what the message is about, and it has one.
            if (names(c) instanceof TypeSymbol.AtModule at && symbols.scope().isForeign(at)) {
                throw CompileException.of(Diagnostic
                                .at(c.name().reportedAt())
                                
                                .hint(new BehaviorMessage.ASumsCasesAreDeclaredWithIt(c.written())).say(new BehaviorMessage.ACaseIsDeclaredInAnotherModule(c.written(), sum.name(), at.module())).build());
            }
        }
        List<String> cycle = sumCycle(sum.declares(), symbols, new LinkedHashSet<>());
        if (cycle != null) {
            throw CompileException.of(Diagnostic
                            .at(sum.pos()).say(new BehaviorMessage.ASumContainsItself(sum.name(), String.join(" | ", cycle))).build());
        }
        // A case lays its fields flatly beside the discriminator the sum writes, so a case declaring
        // a field of that name and the tag want one key. Refused here rather than written over where
        // it is encoded, which would lose the value with nothing said.
        //
        // The key is read off the settled representation and not written here. A behavior's output
        // union is under the same rule and reads it from the same place (`SpecChecker`), so the two
        // cannot come to be checked against different keys — and an enumeration, which writes no key,
        // is not asked.
        if (Boundary.of(Type.ref(sum.declares()), symbols).representation()
                instanceof Boundary.Representation.Discriminated(String key)) {
            TypeSymbol carrying = TypeOps.memberCarryingField(Type.ref(sum.declares()), key, symbols);
            if (carrying != null) {
                throw CompileException.of(Diagnostic
                                .at(sum.pos())
                                .hint(new DataMessage.TheTagAndTheFieldWantOneKey(key)).say(new DataMessage.ACaseDeclaresTheDiscriminatorField(carrying.name(), key, sum.name())).build());
            }
        }
    }

    /**
     * The declarations of {@code module} that have no value, said of each group that has none of its
     * own.
     *
     * <p>Not a walk for a recursion with nowhere to stop. That is one of the ways a type comes to
     * have no value and reading for it was reading for the shape rather than for the answer: a sum
     * whose every case recurses has none, a set asked for more values than its element has has none,
     * and rules that cannot all hold leave none. All of them are one count coming to nothing.
     *
     * <p>What that count is and how it is reached is {@link TypeCardinality}; which of the
     * declarations with no value to say so about, and what showed it of the one the report sits at,
     * is {@link UninhabitableTypes}, asked for as an answer of its own. What is left here is saying
     * it: the groups arrive worked out, and this writes the sentence and places it.
     *
     * <p>The proof arrives with the group and nothing here reads the declaration again to work out
     * which sentence to write. That is the whole of why it arrives: a reader that picked between two
     * sentences by looking at the declaration a second time would be a second reader of a question
     * the count already answered, free to pick the sentence the count did not mean.
     *
     * <p>What arrives says whether there was a count at all ({@link UninhabitableTypes.WithNoValue}),
     * and what stopped one that was tried is reported where it was tried. Nothing is concluded here
     * from there being no count: a module whose declarations could not be counted is one this says
     * nothing about, and it still has everything else about it to report.
     */
    static List<CompileException> typesWithNoValue(
            UninhabitableTypes.WithNoValue counted, Symbols symbols) {
        // A count that found nothing and no count at all are one empty list of sentences and two
        // different facts. Nothing here needs to tell them apart — what would be written is nothing
        // either way — and the difference is kept because the reader that does need it is the one
        // deciding whether a declaration may be refused for having no value.
        List<UninhabitableTypes.UninhabitableGroup> groups = switch (counted) {
            case UninhabitableTypes.WithNoValue.Counted(List<UninhabitableTypes
                    .UninhabitableGroup> found) -> found;
            case UninhabitableTypes.WithNoValue.NotCounted _ -> List.of();
        };
        // How many of the lacks reported here each declaration is part of. A declaration in one of
        // them is a declaration whose lack the group accounts for entirely, and a suggestion about
        // that group is a way out. A declaration in two is in neither's: what a group is established
        // by is a reading with every other lack granted, so undoing one leaves the other where it
        // was, and a suggestion read off either would be an untrue way out written as a certainty.
        Map<TypeSymbol, Integer> lacks = new HashMap<>();
        for (UninhabitableTypes.UninhabitableGroup group : groups) {
            group.members().forEach(each -> lacks.merge(each, 1, Integer::sum));
        }
        List<CompileException> found = new ArrayList<>();
        for (UninhabitableTypes.UninhabitableGroup group : groups) {
            Hir.Def at = symbols.declaredNode(group.reportedAt());
            found.add(CompileException.of(told(Diagnostic.at(at.pos()), at.name(),
                    new Emptiness.AtAField.Where.TheValueItself(), false, group.why(),
                    lacks.get(group.reportedAt()) == 1).build()));
        }
        return found;
    }

    /**
     * The one place a proof of having no value is read as a sentence.
     *
     * <p>Exhaustive over {@link Emptiness}, which is what makes a proof added later a build that
     * stops here rather than a refusal quietly told one of the sentences already written.
     *
     * <p>{@link Emptiness.AtAField} is descended through and the rest are not. It says where a lack
     * sits and states nothing of its own, so the sentence is its child's and the position it names is
     * carried down for the child to write; every other shape asserts something — a sum has none
     * because all of its cases do, and picking one of them to speak for the rest would answer a
     * question about which case is at fault that nothing asked.
     *
     * <p>Three of the arms have no model that reaches them. A declaration is reported for a group no
     * smaller group holds inside, and a proof of its that stops at a name — on its own, under a
     * collection, or across every case — names only declarations in the group; had any of those been
     * shown something, the group without this one would hold and this group would not be the
     * smallest. So what reaches those arms today is nothing, and they are written because being
     * exhaustive over the proofs is what makes the next proof a build that stops here. Do not read
     * the switch as a list of sentences anybody has seen.
     *
     * @param path where in the declaration the proof so far has reached
     * @param placed whether the proof named that place or the caller assumed it. A proof that names
     *               none was shown of the whole value and not of a position in it, and a sentence
     *               that filled the place in would name whatever the reader happened to be at
     * @param alone whether the declaration this is said at has no other lack reported of it, which
     *              is what a suggestion has to be true of
     */
    private static Diagnostic.Builder told(Diagnostic.Builder at, String data,
                                           Emptiness.AtAField.Where path, boolean placed,
                                           Emptiness why, boolean alone) {
        return switch (why) {
            case Emptiness.AtAField it -> told(at, data, it.where(), true, it.under(), alone);
            // The places together, which is what the proof names. Read one at a time, an author
            // would be sent to a position whose own rules leave it something.
            //
            // And what was shown of them, which is the same question as at one place: the values
            // and the range sharing nothing is not the values leaving nothing, and a sentence for
            // both would send an author to read a half that is fine on its own.
            case Emptiness.AtEqualPositions it -> switch (it.under()) {
                case Emptiness.NoAllowedValueInRange _ ->
                        at.say(new DataMessage.NoValueTheseAllowIsInTheRangeTheyShare(
                                data, written(it.where())));
                case Emptiness.NoAllowedValueWithinRequiredBounds _ ->
                        at.say(new DataMessage.NoValueTheseAllowIsWithinTheBoundsTheyRequire(
                                data, written(it.where())));
                case Emptiness.NoCommonValueForEqualPositions _ ->
                        at.say(new DataMessage.NoValueTheseCanAllHold(data, written(it.where())));
                // Every other proof, named rather than gathered under a default: a proof added
                // later is one somebody has to say what several places make of, and a default
                // would hand it whichever of these two was written first. None of them reaches
                // here today — a range is one position's own answer and the rest are about a
                // declaration rather than a place — so what is said is the general form.
                case Emptiness.ConflictingRules _, Emptiness.EmptyNumericInterval _,
                     Emptiness.EmptyOrderedInterval _, Emptiness.NoAllowedCollectionSize _,
                     Emptiness.SetRequiresTooManyDistinctValues _,
                     Emptiness.NonEmptyCollectionWithNoElement _, Emptiness.AcrossEveryCase _,
                     Emptiness.TheNameHasNone _, Emptiness.NoBaseInComponent _,
                     Emptiness.AtAField _, Emptiness.AtEqualPositions _,
                     Emptiness.AtPositionsHeldApart _,
                     Emptiness.NoDistinctValuesForPositionsHeldApart _,
                     Emptiness.PositionsHeldAsOneAreHeldApart _ ->
                        at.say(new DataMessage.ItsRulesCannotAllHold(data));
            };
            // The places together, the same way, and a sentence of its own: these positions are
            // stated to differ and there is no way for them to. Said as what they cannot all
            // differ in rather than as a value they cannot all hold, which is the rule turned
            // round and is what the places beside them do hold.
            case Emptiness.AtPositionsHeldApart it -> switch (it.under()) {
                case Emptiness.NoDistinctValuesForPositionsHeldApart _ ->
                        at.say(new DataMessage.NoValuesTheseCanAllDifferIn(
                                data, written(it.where())));
                // And the two rules where what refuses is reading them: one value is not two,
                // whatever those positions may hold. An author told there were too few values
                // would go looking for more, and there is no number of them that would do.
                case Emptiness.PositionsHeldAsOneAreHeldApart _ ->
                        at.say(new DataMessage.TheseAreHeldAsOneValueAndStatedToDiffer(
                                data, written(it.where())));
                // Every other proof, named rather than gathered under a default, for the reason
                // the arm above gives. None of them reaches here today: this place is written
                // only under the proof above it.
                case Emptiness.ConflictingRules _, Emptiness.EmptyNumericInterval _,
                     Emptiness.EmptyOrderedInterval _, Emptiness.NoAllowedCollectionSize _,
                     Emptiness.SetRequiresTooManyDistinctValues _,
                     Emptiness.NonEmptyCollectionWithNoElement _, Emptiness.AcrossEveryCase _,
                     Emptiness.TheNameHasNone _, Emptiness.NoBaseInComponent _,
                     Emptiness.NoAllowedValueInRange _,
                     Emptiness.NoAllowedValueWithinRequiredBounds _,
                     Emptiness.NoCommonValueForEqualPositions _,
                     Emptiness.AtAField _, Emptiness.AtEqualPositions _,
                     Emptiness.AtPositionsHeldApart _ ->
                        at.say(new DataMessage.ItsRulesCannotAllHold(data));
            };
            case Emptiness.NoBaseInComponent it -> {
                Diagnostic.Builder said = at.say(new DataMessage.DataCannotBeConstructed(data));
                yield alone ? suggested(said, data, it.through()) : said;
            }
            // Not reachable, and written for the reason the three arms below it are: this proof is
            // made only inside the one that says which positions it is about, and where there is no
            // block to name, what is carried is the general form instead. Being exhaustive over the
            // proofs is what makes the next one a build that stops here.
            case Emptiness.NoCommonValueForEqualPositions _,
                 Emptiness.NoDistinctValuesForPositionsHeldApart _,
                 Emptiness.PositionsHeldAsOneAreHeldApart _ ->
                    at.say(new DataMessage.ItsRulesCannotAllHold(data));
            case Emptiness.ConflictingRules _, Emptiness.EmptyNumericInterval _ ->
                    at.say(new DataMessage.ItsRulesCannotAllHold(data));
            case Emptiness.EmptyOrderedInterval _ ->
                    at.say(new DataMessage.NothingIsLeftForThatPositionToHold(
                            data, written(path)));
            // With a place only where the proof named one. Where the alternatives are refused at
            // different positions there is none, and the sentence about a position would name one
            // the rules are fine with — so what is said there is that the rules contradict, which
            // is what this reading could show and no more.
            case Emptiness.NoAllowedValueInRange _ -> placed
                    ? at.say(new DataMessage.NoValueItsRulesAllowIsInThatRange(data, written(path)))
                    : at.say(new DataMessage.ItsRulesCannotAllHold(data));
            // And with a place on the same terms. What was shown of a whole product is said of the
            // product: the bounds one position is required to be within are met with what an
            // alternative allows it, so where the alternatives were refused at different positions
            // there is no one of them the author can be sent to.
            case Emptiness.NoAllowedValueWithinRequiredBounds _ -> placed
                    ? at.say(new DataMessage.NoValueItsRulesAllowIsWithinTheBoundsTheyRequire(
                            data, written(path)))
                    : at.say(new DataMessage.ItsRulesCannotAllHold(data));
            case Emptiness.SetRequiresTooManyDistinctValues it ->
                    at.say(new DataMessage.ASetCannotBeFilledFromItsElement(
                            data, written(path), it.available()));
            case Emptiness.NoAllowedCollectionSize _ ->
                    at.say(new DataMessage.NoSizeItsRulesAdmit(data, written(path)));
            case Emptiness.NonEmptyCollectionWithNoElement _ ->
                    at.say(new DataMessage.ACollectionThatCannotBeEmptyHasNothingToHold(
                            data, written(path)));
            case Emptiness.AcrossEveryCase _ -> at.say(new DataMessage.NoCaseOfItHasAValue(data));
            case Emptiness.TheNameHasNone it ->
                    at.say(new DataMessage.ItHoldsATypeWithNoValue(data, it.name().name()));
        };
    }

    /**
     * What would give a self-referring declaration a value, where anything can be said.
     *
     * <p>Read off how this one reaches the others and not off the refusal. What makes a recursion
     * bottom out depends on the way round it runs, and one suggestion written for all of them told
     * an author holding a non-empty list of itself to write a list. Where the shape is one nothing
     * safe can be said about, nothing is: a refusal owes the author the reason and does not owe an
     * invented way out.
     *
     * <p>What a newtype wraps is one of those. It is at no position of its own, and writing the `?`
     * there is refused where a newtype is read — so the way out is at whatever uses the name, which
     * is not something this can say without reading declarations it is not looking at.
     */
    private static Diagnostic.Builder suggested(Diagnostic.Builder at, String data,
                                                Emptiness through) {
        return switch (through) {
            case Emptiness.AtAField(Emptiness.AtAField.Where.In(String spelled), var under)
                    when under instanceof Emptiness.TheNameHasNone ->
                    at.hint(new DataMessage.ItWouldHaveOneIfTheFieldCouldBeAbsent(data, spelled));
            case Emptiness.AtAField it
                    when it.under() instanceof Emptiness.NonEmptyCollectionWithNoElement ->
                    at.hint(new DataMessage.ItWouldHaveOneIfTheCollectionCouldBeEmpty(
                            data, written(it.where())));
            case Emptiness.AcrossEveryCase _ ->
                    at.hint(new DataMessage.ACaseThatDoesNotHoldItWouldGiveItOne(data));
            default -> at;
        };
    }

    /** How a place is written in the model, what a newtype wraps being spelled `value` — which is
     *  what the model calls it, and is a fact about the newtype form rather than about the proof. */
    private static String written(Emptiness.AtAField.Where where) {
        return switch (where) {
            case Emptiness.AtAField.Where.TheValueItself _ -> "value";
            case Emptiness.AtAField.Where.In(String spelled) -> spelled;
        };
    }

    /**
     * Several places, in the order the value declares them.
     *
     * <p>The order is the proof's and not this reader's. Which places a lack is at is settled where
     * the proof is made, off the map the declaration's own positions are read from — sorted here,
     * a sentence would list them by a rule of this compiler's and the same model would read two
     * ways under two spellings.
     */
    private static String written(List<Emptiness.AtAField.Where> where) {
        return where.stream().map(each -> "`" + written(each) + "`")
                .collect(Collectors.joining(", "));
    }

    /**
     * The declaration, and the boundary representation derived for it.
     *
     * <p>The two together because the second is checked against the first. It arrives as
     * {@link Derived.Data} rather than being looked for: a product that reached this stage has a
     * decoder and an encoder, and taking them from what says so is what leaves no state here in
     * which a check is skipped because a declaration turned out to have none.
     */
    static void checkData(Derived.Data derived, CheckContext ctx) {
        Map<String, Type> fields = TypeOps.fieldTypes(ctx.data(), ctx.symbols());

        // A newtype wraps one value and takes its representation, so there is nothing for it to be
        // when the value is absent. Whether a value is present is a property of the place it is
        // used, written there as `f: X?`. Read on the resolved type, so a data the author happens
        // to have named `Option` is an ordinary named data here.
        if (ctx.data().newtype() && fields.get("value") instanceof Type.OptionOf o) {
            throw CompileException.of(Diagnostic
                            .at(fieldRegion(ctx.data(), "value"))
                            
                            .hint(new DataMessage.WrapTheValueAndWriteTheQuestionMarkOnTheField(ctx.data().name())).say(new DataMessage.ANewtypeMayNotWrapAnOptional(ctx.data().name(), Type.show(o.element()))).build());
        }

        for (Map.Entry<String, Type> e : fields.entrySet()) {
            // A field is read through an accessor of the same name, and a data is a record over its
            // fields (spec §jvm-product). A no-argument method of Object is therefore taken: `toString` would
            // emit a second `toString()` and the class would not load, and the rest cannot be a record
            // component either. Reported here rather than left to codegen, as a duplicate name is.
            if (OBJECT_METHOD_NAMES.contains(e.getKey())) {
                throw CompileException.of(Diagnostic
                                .at(fieldRegion(ctx.data(), e.getKey()))
                                .say(new DataMessage.AFieldTakesAMethodOfObject(ctx.data().name(), e.getKey())).build());
            }
            if (TypeOps.withoutExternalForm(e.getValue(), ctx.symbols()) instanceof Type.TupleOf) {
                throw CompileException.of(Diagnostic
                                .at(fieldRegion(ctx.data(), e.getKey()))
                                .say(new DataMessage.ATupleCannotBeAField(ctx.data().name(), e.getKey())).build());
            }
            // A field is written to and read from the outside, so a map it holds is a JSON object and
            // its keys are strings. Inside a body the same map may be keyed by anything (ADR-0040).
            Type badKey = TypeOps.nonBoundaryMapKey(e.getValue(), ctx.symbols());
            if (badKey != null) {
                throw CompileException.of(Diagnostic
                                .at(fieldRegion(ctx.data(), e.getKey()))
                                
                                .hint(new TypeMessage.AMapIsAJsonObjectKeyedByStrings()).say(new TypeMessage.AFieldsMapCannotBeKeyedByThat(ctx.data().name() + "." + e.getKey(), Type.show(badKey))).build());
            }
        }

        // That a clause is a condition is not asked here. Elaborating it is what answers that, and
        // the elaboration a clause has is `Shapes.ValueShapes`'s — which is the one that runs
        // (issue #1080). Asked here as well, this would be the second reading of the clause, which
        // is what having one owner is for.
        checkClauseNames(ctx.data(), ctx.symbols());

        checkDecoder(derived.decoder(), ctx, fields);
        checkEncoder(derived.encoder(), ctx);
    }

    /**
     * The bindings a declaration's own invariant reads: its fields, each as the binding it is.
     *
     * <p>{@code declared} is which declaration these fields belong to, which is not always the module
     * asking — the discharge check reads the clauses of types other modules declared. A field is
     * bound where it was written, so that is what the scope offers, and the clause carried in with the
     * declaration finds the very bindings it names.
     */
    static Scope fieldScope(TypeSymbol.AtModule declared, Hir.Data data, Symbols symbols) {
        Map<String, Type> types = TypeOps.fieldTypes(data, symbols);
        Map<BindingId, Scope.Binding> bindings = new LinkedHashMap<>();
        TypeOps.fieldBindings(declared, symbols).forEach((name, binding) ->
                bindings.put(binding, new Scope.Binding(name, types.get(name))));
        return Scope.of(bindings);
    }

    private static void checkDecoder(Hir.DecoderDef dec, CheckContext ctx, Map<String, Type> fields) {
        switch (dec) {
            case Hir.PrimDecoder prim -> {
                Type inputType = TypeOps.primType(prim.from());
                Scope env = Scope.NONE.with(prim.input(), inputType);
                for (Hir.DecStmt stmt : prim.stmts()) {
                    switch (stmt) {
                        case Hir.Let let ->
                                env = env.with(let.binder(), Elaborator.typeOf(let.value(), env, ctx));
                    }
                }
                checkConstruct(prim.result(), ctx, fields, env);
            }
            case Hir.ObjectDecoder obj -> {
                Scope env = Scope.NONE;
                for (Hir.Bind bind : obj.binds()) {
                    env = env.with(bind.binder(), decRefType(bind.ref(), ctx.symbols()));
                }
                checkConstruct(obj.result(), ctx, fields, env);
            }
            case Hir.NewtypeDecoder nt -> {
                Scope env = Scope.NONE.with(nt.input(), decRefType(nt.inner(), ctx.symbols()));
                checkConstruct(nt.result(), ctx, fields, env);
            }
        }
    }

    private static Type decRefType(Hir.DecRef ref, Symbols symbols) {
        return switch (ref) {
            case Hir.SetDecRef s -> Type.set(decRefType(s.element(), symbols));
            case Hir.PrimDecRef p -> TypeOps.primType(p.kind());
            // What a bind reads through is the named type, and that it is read at all is settled by
            // the stage that made this reference: a declaration that came out has a decoder, and a
            // reference to one is minted from the shape a declaration was found to have. Nothing is
            // asked about the name here — whose vocabulary it is, is asked before this and
            // elsewhere ({@code CrossingNominal}).
            case Hir.DataDecRef d -> Type.ref(names(d.typeName()));
            case Hir.ListDecRef l -> Type.list(decRefType(l.element(), symbols));
            case Hir.OptionDecRef o -> Type.option(decRefType(o.element(), symbols));
            case Hir.MapDecRef mp -> Type.map(mp.key().type(), decRefType(mp.value(), symbols));
        };
    }

    private static void checkConstruct(Hir.Construct c, CheckContext ctx, Map<String, Type> fields,
                                       Scope env) {
        if (!names(c.typeName()).equals(ctx.data().declares())) {
            throw CompileException.of(Diagnostic.at(c.pos())
                    .say(new CodecMessage.TheDecoderBuildsAnotherType(ctx.data().name(),
                            c.typeName().written()))
                    .build());
        }
        // nothing builds a decoder's construction with a spread, so there is no binding to copy from
        // here; whether a field left out is one it had to write is the node's answer, as it is for
        // the construction a body writes
        checkConstruction(c.typeName().written(), c.inits(), List.of(), c.pos(), fields, env, ctx,
                c.mayOmitOptionalFields());
    }

    /**
     * What each declared field of a construction is given, in declaration order — the one place that
     * answers it. A field written out is given what was written; one no field init names is given the
     * read of that field off the value spread into the construction, which is a field read like the
     * one an author writes. So a construction carries no spread past here, and every reader of it
     * asks the same values in the same order rather than working the spread out again.
     *
     * <p>Where several spreads carry one field, the first of them supplies it — one field is given
     * one value, and which is decided here and not by whichever reader looks.
     *
     * <p>{@code mayOmitOptionals} is the construction's own answer and is asked of it, not worked
     * out here: whether a field left out is the absent value the declaration holds was settled when
     * the source was read, and both kinds of construction that reach this answer it for themselves.
     */
    static List<Core.FieldValue> checkConstruction(String typeName, List<Hir.FieldInit> inits,
                                          List<Core.Read> spreads,
                                          SourcePos pos, Map<String, Type> fields, Scope env,
                                          CheckContext ctx, boolean mayOmitOptionals) {
        Map<String, Core.FieldValue> written = new LinkedHashMap<>();
        for (Hir.FieldInit init : inits) {
            if (written.containsKey(init.name())) {
                throw CompileException.of(Diagnostic.at(init.pos())
                        .say(new DataMessage.FieldIsDefinedMoreThanOnce(init.name()))
                        .build());
            }
            Type ft = fields.get(init.name());
            if (ft == null) {
                throw CompileException.of(Diagnostic.at(init.written().reportedAt())
                        .say(new DataMessage.NotAFieldOf(init.name(), typeName))
                        .build());
            }
            // push the field's declared type into the value expression, so a field initialised from a
            // fold over an empty-collection seed has its result pinned by the field type (issue #70).
            // This is also the one place an optional may be made (ADR-0011), which the context carries
            // rather than the expected type: a model may write `Option<T>` where it reads one, so an
            // expected optional no longer means a field asked for it (issue #202).
            CheckContext making = ctx.makingAnOptional(ft instanceof Type.OptionOf);
            Core value = Elaborator.liftIntoOption(
                    Elaborator.elaborate(init.value(), env, making, ft), ft, ctx.symbols());
            written.put(init.name(), new Core.FieldValue(init.name(), value, init.pos()));
            Type vt = value.type();
            // a case value widens to its sum-typed field (spec §sum-data)
            if (!TypeOps.assignable(vt, ft, ctx.symbols())) {
                throw CompileException.of(Diagnostic
                                .at(init.written().reportedAt())
                                
                                .diff(Type.show(vt, ft), Type.show(ft, vt)).say(new DataMessage.AFieldExpectsAnotherType(init.name(), Type.show(ft), Type.show(vt))).build());
            }
        }
        // the sums spread here, which a field the construction still wants was not in the shared part
        // of — all of them, because naming one of several would pick by position and send the author
        // to open a sum whose cases never had the field
        Set<String> fromSums = new LinkedHashSet<>();
        List<Spread> spread = new ArrayList<>();
        for (Core.Read read : spreads) {
            String sp = read.name();
            Type bound = env.typeOf(read.binding());
            if (bound instanceof Type.Ref ref
                    && ctx.symbols().declaredNode(ref.name()) instanceof Hir.SumData sum) {
                fromSums.add(Type.show(bound));
                spread.add(new Spread(read, spreadOfSum(sp, sum, bound, pos, ctx)));
            } else if (bound instanceof Type.Ref ref
                    && ctx.symbols().declaredNode(ref.name()) instanceof Hir.Data sd) {
                spread.add(new Spread(read, TypeOps.fieldTypes(sd, ctx.symbols())));
            } else {
                Diagnostic.Builder d = Diagnostic.at(pos)
                        .say(new DataMessage.SpreadIsNotADataValue(sp));
                if (bound instanceof Type.Union) {
                    d = d.hint(new DataMessage.NameTheUnionWithADeclaration(sp));
                }
                throw CompileException.of(d.build());
            }
        }
        List<Core.FieldValue> values = new ArrayList<>();
        for (Map.Entry<String, Type> f : fields.entrySet()) {
            Core.FieldValue own = written.get(f.getKey());
            if (own != null) {
                values.add(own);
                continue;
            }
            Spread from = supplying(spread, f.getKey());
            if (from == null && mayOmitOptionals && f.getValue() instanceof Type.OptionOf) {
                // A fixture writes the value a field holds and writes nothing where it holds none,
                // so a field left out is the absent value it declares rather than one with no value.
                values.add(new Core.FieldValue(f.getKey(),
                        new Core.OptionNone(f.getValue(), pos), pos));
                continue;
            }
            if (from == null) {
                Diagnostic.Builder d = Diagnostic.at(pos)
                        .say(new DataMessage.ConstructionIsMissingAField(typeName, f.getKey()));
                // one rule broken in one of several ways, and the hint is where the way is said. What
                // was written decides it: `fromSums` counts the sums spread, which says nothing about
                // whether anything was spread at all, so a construction with no spread is asked about
                // separately rather than read off an empty count.
                if (spreads.isEmpty()) {
                    d = d.hint(new DataMessage.GiveTheFieldAValue(f.getKey()));
                } else {
                    d = switch (fromSums.size()) {
                        case 0 -> d.hint(new DataMessage.SupplyTheFieldExplicitly(f.getKey()));
                        case 1 -> d.hint(new DataMessage.TheFieldIsNotInWhatTheSumShares(
                                f.getKey(), fromSums.iterator().next()));
                        default -> d.hint(new DataMessage.TheFieldIsInTheSharedPartOfNoneOfThese(
                                f.getKey(), String.join(", ", fromSums)));
                    };
                }
                throw CompileException.of(d.build());
            }
            Type pv = from.fields().get(f.getKey());
            if (!TypeOps.assignable(pv, f.getValue(), ctx.symbols())) {
                throw CompileException.of(Diagnostic.at(pos)
                        .say(new DataMessage.SpreadSuppliesTheWrongType(f.getKey(), Type.show(pv),
                                typeName, Type.show(f.getValue())))
                        .diff(Type.show(pv, f.getValue()), Type.show(f.getValue(), pv)).build());
            }
            // The value is read at the type the source declares the field, which is the type the
            // backend loads it at; that it fits the field being given it was decided just above.
            values.add(new Core.FieldValue(f.getKey(),
                    new Core.FieldAccess(from.read(), f.getKey(), pv, from.read().pos()),
                    from.read().pos()));
        }
        return values;
    }

    /** A value a construction spreads, and the fields it carries. */
    private record Spread(Core.Read read, Map<String, Type> fields) {}

    /** The first of {@code spreads} carrying {@code field}, or null where none does. */
    private static Spread supplying(List<Spread> spreads, String field) {
        for (Spread s : spreads) {
            if (s.fields().containsKey(field)) {
                return s;
            }
        }
        return null;
    }

    /** What a spread of a sum copies: the fields of the data every one of its cases spreads. This is
     * the construction side of reading such a field off the sum, and takes its shared part from the
     * same place the read does, so the two answer alike. A sum whose cases share no spread has nothing
     * to copy — cases that merely declare a field of the same name have not shared it — and that is
     * reported here rather than left to the missing-field report, which would name a field the author
     * can see in every case. */
    private static Map<String, Type> spreadOfSum(String name, Hir.SumData sum, Type bound,
                                                 SourcePos pos, CheckContext ctx) {
        Map<String, Type> shared =
                TypeView.of(Type.ref(sum.declares()), ctx.symbols()).shape() instanceof Shape.Sum s
                        ? ReadableFields.of(s).declaredFields() : Map.of();
        if (shared.isEmpty()) {
            throw CompileException.of(Diagnostic.at(pos)
                    .say(new DataMessage.SpreadOfASumWhoseCasesShareNothing(name, Type.show(bound)))
                    .build());
        }
        return shared;
    }

    private static void checkEncoder(Hir.EncoderDef enc, CheckContext ctx) {
        Scope env = Scope.NONE.with(enc.self(), Type.ref(ctx.data().declares()));
        checkRawExpr(enc.result(), env, ctx);
    }

    private static void checkRawExpr(Hir.RawExpr raw, Scope env, CheckContext ctx) {
        switch (raw) {
            case Hir.TextRaw t -> Elaborator.requireType(t.arg(), Type.STRING, env, ctx,
                    "argument of Text");
            case Hir.IntRaw i -> Elaborator.requireType(i.arg(), Type.INT, env, ctx,
                    "argument of Int");
            case Hir.BoolRaw b -> Elaborator.requireType(b.arg(), Type.BOOL, env, ctx,
                    "argument of Bool");
            case Hir.DecimalRaw d -> Elaborator.requireType(d.arg(), Type.DECIMAL, env, ctx,
                    "argument of Decimal");
            case Hir.IsoTextRaw t -> {
                Type at = Elaborator.typeOf(t.arg(), env, ctx);
                // Asked of each primitive, so a temporal added later is admitted where it is
                // declared rather than being refused here by a comparison written before it existed.
                boolean temporal = at instanceof Type.Prim p && switch (p) {
                    case DATE, TIME, DATETIME, INSTANT -> true;
                    case INT, STRING, BOOL, DECIMAL, RAW -> false;
                };
                if (!temporal) {
                    throw CompileException.of(Diagnostic.at(t.pos())
                            .say(new CodecMessage.AnIsoTextEncoderTakesATemporalValue(Type.show(at)))
                            .build());
                }
            }
            case Hir.OptionRaw o -> {
                Type at = Elaborator.typeOf(o.access(), env, ctx);
                if (!(at instanceof Type.OptionOf oo)) {
                    throw CompileException.of(Diagnostic.at(o.pos())
                            .say(new CodecMessage.AnOptionalEncoderTakesAnOptional(Type.show(at)))
                            .build());
                }
                checkRawExpr(o.inner(), env.with(o.elem(), oo.element()), ctx);
            }
            case Hir.ObjectRaw o -> {
                for (Hir.RawEntry entry : o.entries()) {
                    checkRawExpr(entry.value(), env, ctx);
                }
            }
            case Hir.EncodeRaw e -> {
                Elaborator.requireType(e.arg(), Type.ref(names(e.typeName())), env, ctx,
                        "argument of " + e.typeName().written() + ".encode");
            }
            case Hir.ListEnc le -> {
                Type st = Elaborator.typeOf(le.source(), env, ctx);
                if (!(st instanceof Type.ListOf lo)) {
                    throw CompileException.of(Diagnostic.at(le.pos())
                            .say(new CodecMessage.AListEncoderTakesAList(Type.show(st)))
                            .build());
                }
                checkEncElem(le.elem(), lo.element(), le.pos(), ctx.symbols());
            }
            case Hir.SetEnc se -> {
                Type st = Elaborator.typeOf(se.source(), env, ctx);
                if (!(st instanceof Type.SetOf so)) {
                    throw CompileException.of(Diagnostic.at(se.pos())
                            .say(new CodecMessage.ASetEncoderTakesASet(Type.show(st)))
                            .build());
                }
                checkEncElem(se.elem(), so.element(), se.pos(), ctx.symbols());
            }
            case Hir.MapEnc me -> {
                Type st = Elaborator.typeOf(me.source(), env, ctx);
                if (!(st instanceof Type.MapOf mo)) {
                    throw CompileException.of(Diagnostic.at(me.pos())
                            .say(new CodecMessage.AMapEncoderTakesAMap(Type.show(st)))
                            .build());
                }
                checkEncElem(me.elem(), mo.value(), me.pos(), ctx.symbols());
            }
        }
    }

    private static void checkEncElem(Hir.EncElem elem, Type elemType, SourcePos pos,
                                     Symbols symbols) {
        switch (elem) {
            case Hir.PrimEnc p -> {
                if (!elemType.equals(TypeOps.primType(p.kind()))) {
                    throw elemEncMismatch(Type.show(TypeOps.primType(p.kind())), elemType, pos);
                }
            }
            case Hir.DataEnc d -> {
                // Which kind of declaration it is, and not whether one wrote a representation: the
                // element may be a product or a sum (`List<事前承認理由>` holds a sum, spec
                // §encoder-derivation), and a unit writes nothing of its own to stand as an element.
                boolean writesAnElement = switch (symbols.declaredNode(names(d.typeName()))) {
                    case Hir.Data _, Hir.SumData _ -> true;
                    case Hir.UnitData _ -> false;
                    // The reference was minted from a shape a declaration was found to have, so
                    // there is one. Reported as this compiler's own rather than as a disagreement
                    // between the encoder and the element, which is what it is not.
                    case null -> throw new IllegalStateException(
                            "nothing declares `" + d.typeName().written()
                                    + "`, which an element encoder was written against");
                };
                if (!elemType.equals(Type.ref(names(d.typeName()))) || !writesAnElement) {
                    throw elemEncMismatch(d.typeName().written(), elemType, pos);
                }
            }
            // a collection element is itself a collection: descend both the encoder and the type
            case Hir.ListElemEnc l -> {
                if (!(elemType instanceof Type.ListOf lo)) {
                    throw elemEncMismatch("List", elemType, pos);
                }
                checkEncElem(l.elem(), lo.element(), pos, symbols);
            }
            case Hir.SetElemEnc s -> {
                if (!(elemType instanceof Type.SetOf so)) {
                    throw elemEncMismatch("Set", elemType, pos);
                }
                checkEncElem(s.elem(), so.element(), pos, symbols);
            }
            case Hir.MapElemEnc m -> {
                if (!(elemType instanceof Type.MapOf mo)) {
                    throw elemEncMismatch("Map", elemType, pos);
                }
                checkEncElem(m.value(), mo.value(), pos, symbols);
            }
            // an absent member is written null, so the element encoder is one level above what the
            // option holds, as the type is
            case Hir.OptionElemEnc o -> {
                if (!(elemType instanceof Type.OptionOf oo)) {
                    throw elemEncMismatch("Option", elemType, pos);
                }
                checkEncElem(o.elem(), oo.element(), pos, symbols);
            }
        }
    }

    /** The element encoder and the element type disagree, both named as they are written — the
     * encoder by the type it encodes (`String`, `商品ID`, `List`), the element by {@link Type#show}. */
    private static CompileException elemEncMismatch(String encoder, Type elemType, SourcePos pos) {
        return CompileException.of(Diagnostic.at(pos)
                .say(new CodecMessage.TheElementEncoderIsNotForTheElementType(
                        "`" + encoder + "`", Type.show(elemType)))
                .build());
    }

}
