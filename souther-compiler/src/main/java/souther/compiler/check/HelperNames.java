package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.types.ReachName;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * How a definition is named outside the module that declares it, and what a body carries out of the
 * module it was written in.
 *
 * <p>A reader writes an imported value or helper bare, and the pair (module, name) is what it
 * denotes. Writing the qualified spelling into the tree settles the name a reader reaches it by, so
 * everything downstream — the table a call is expanded against, the method a recursive helper is
 * emitted as — asks and answers with one name, and two modules publishing a {@code tally} stay two
 * entries. Every rewrite here reads what a name denotes and never how it was spelled, so running one
 * twice says what running it once said.
 *
 * <p>What that spelling settles is the reach name, and it is not the declaration's identity. The two
 * coincide for a helper another module published and part ways for the standard library, which is
 * reached under an alias: {@code List.foldFrom} is declared in {@code souther.list}, and no reader of
 * the name can get there from it. Which module declared a definition is carried on the declaration
 * ({@link Hir.FnDef#declaredIn}), and every rule about the declaring module reads it there.
 *
 * <p>The marks a construction carries belong here for the same reason. What a published body builds
 * is built where that body was written; expanding it puts the construction in the reader's body,
 * where nothing left in the tree would say otherwise. The mark is what says so, and it is written
 * where the name is.
 *
 * <p>Nothing here reads a helper table or writes a binding: these are questions about names, and the
 * answers are the same whichever module is asking.
 */
public final class HelperNames {

    private HelperNames() {}

    /** How a definition of {@code module} is named outside it. */
    public static String qualified(String module, String name) {
        return module + "." + name;
    }

    /**
     * {@code fn} with every name in its body that denotes another module's definition written
     * qualified.
     *
     * <p>Done once, at the rung that says so, because the spelling travels as far as the emitted
     * method name; deciding it at each reader is how one of them comes to disagree.
     *
     * <p>A part at a time and not a module. What comes back is a definition, and the rung that
     * asked for it has to say again what it holds of one. A whole-module rewrite would hand back a
     * tree with nobody saying anything about the parts in it, which is how the claims the rungs
     * below had established came to be dropped here (#714).
     */
    static Hir.FnDef qualifyImportsIn(Hir.FnDef fn, String self) {
        return fn.body() instanceof Hir.FnBody.Written w
                ? fn.withBody(new Hir.FnBody.Written(qualifyForeign(w.expr(), self)))
                : fn;
    }

    /** The same of one example block: its inputs, its stand-ins and what it expects. */
    static Hir.Example qualifyImportsIn(Hir.Example ex, String self) {
        List<Hir.ExampleRow> rows = new ArrayList<>();
        for (Hir.ExampleRow row : ex.rows()) {
            List<Hir.Expr> inputs = new ArrayList<>();
            for (Hir.Expr in : row.inputs()) {
                inputs.add(qualifyForeign(in, self));
            }
            List<Hir.With> withs = new ArrayList<>();
            for (Hir.With w : row.withs()) {
                withs.add(new Hir.With(w.dep(), qualifyForeign(w.value(), self), w.pos()));
            }
            rows.add(new Hir.ExampleRow(row.identity(), inputs, withs,
                    row.expected().map(e -> qualifyForeign(e, self)), row.pos()));
        }
        return new Hir.Example(ex.target(), rows, ex.pos());
    }

    /** The same of one fake table: what each row matches on and what it answers with. */
    static Hir.Fake qualifyImportsIn(Hir.Fake fake, String self) {
        List<Hir.FakeRow> rows = new ArrayList<>();
        for (Hir.FakeRow row : fake.rows()) {
            rows.add(new Hir.FakeRow(row.matched().map(in -> qualifyForeign(in, self)),
                    qualifyForeign(row.output(), self), row.pos()));
        }
        return new Hir.Fake(fake.target(), rows, fake.pos());
    }

    /**
     * {@code m} with the foreign names in its invariants written qualified, and nothing else
     * changed.
     *
     * <p>This is where a declaration's clauses get their spelling, and there is no second place: the
     * rungs above rewrite the definitions and the rows and leave the declarations alone, which is
     * measured rather than arranged, and held by
     * {@code AStateIsReachedOnlyThroughWhatEstablishesItTest}.
     */
    static Hir.Module withQualifiedInvariants(Hir.Module m) {
        List<Hir.Def> defs = qualifiedInvariants(m);
        List<Hir.BehaviorDef> behaviors = qualifiedEnsures(m);
        Hir.Module out = defs.equals(m.defs()) ? m : m.withDefs(defs);
        return behaviors.equals(m.behaviors()) ? out : out.withBehaviors(behaviors);
    }

    private static List<Hir.BehaviorDef> qualifiedEnsures(Hir.Module m) {
        List<Hir.BehaviorDef> out = new ArrayList<>();
        for (Hir.BehaviorDef behavior : m.behaviors()) {
            if (behavior instanceof Hir.SpecBehavior spec && !spec.ensures().isEmpty()) {
                List<Hir.EnsuresClause> clauses = new ArrayList<>();
                for (Hir.EnsuresClause clause : spec.ensures()) {
                    List<Hir.EnsuresArm> arms = new ArrayList<>();
                    for (Hir.EnsuresArm arm : clause.arms()) {
                        arms.add(arm.with(qualifyForeign(arm.expr(), m.name())));
                    }
                    clauses.add(new Hir.EnsuresClause(clause.name(), List.copyOf(arms),
                            clause.pos(), clause.region()));
                }
                out.add(new Hir.SpecBehavior(spec.written(), spec.params(), spec.ret(),
                        spec.constructs(), spec.dependsOn(), List.copyOf(clauses), spec.pos()));
            } else {
                out.add(behavior);
            }
        }
        return out;
    }

    /** {@code m}'s declarations with every name in an invariant that denotes another module's
     * definition written qualified. */
    private static List<Hir.Def> qualifiedInvariants(Hir.Module m) {
        List<Hir.Def> defs = new ArrayList<>();
        for (Hir.Def def : m.defs()) {
            defs.add(def instanceof Hir.Data d && !d.invariants().isEmpty()
                    ? new Hir.Data(d.written(), d.declares(), d.newtype(), d.includes(), d.fields(),
                            Hir.mapClauses(d.invariants(), inv -> qualifyForeign(inv, m.name())),
                            d.pos())
                    : def);
        }
        return defs;
    }

    /** {@code e} with every name denoting a helper of a module other than {@code self} written
     * qualified. */
    private static Hir.Expr qualifyForeign(Hir.Expr e, String self) {
        return qualifyHelpers(e, helper -> !helper.module().equals(self));
    }

    /** {@code e} with every name still denoting a helper of {@code module} written qualified. Only a
     * recursive helper survives closing, so this is what those calls become. */
    static Hir.Expr qualifyHelpersOf(Hir.Expr e, String module) {
        return qualifyHelpers(e, helper -> helper.module().equals(module));
    }

    /**
     * {@code e} with every name denoting a helper {@code which} accepts written qualified.
     *
     * <p>It reads what a name denotes rather than how it is spelled: a binding of the same spelling is
     * a binding, and a prelude helper belongs to the prelude and keeps the qualified name it already
     * has. The new spelling is read off the same answer, so running this twice says what running it
     * once said.
     */
    private static Hir.Expr qualifyHelpers(Hir.Expr e, Predicate<ValueName.Helper> which) {
        // a name slot takes the same rewrite as a name standing on its own: a spread names a value
        // the way any other position does
        Hir.Expr rebuilt = alsoInGiven(Hir.mapChildren(e, c -> qualifyHelpers(c, which),
                s -> qualified(s, which)), c -> qualifyHelpers(c, which));
        return switch (rebuilt) {
            // The name is this pass's and the place is the callee's: only the spelling changes, so
            // what is underlined for it is the stretch the name it replaced was read over — not the
            // application's, which takes in arguments this pass did not touch. What the author
            // applied is neither, and the rewrite carries it: a reader reaching this helper writes
            // it qualified because that is how a reader reaches it, and the author of the call
            // wrote it bare.
            case Hir.Apply call when call.answered() != null
                    && foreign(call.answered().denotes(), which) ->
                    call.replacedBy(
                            Hir.Var.respelled(qualifiedName(call.answered().denotes()),
                                    ofModule(call.answered().denotes()),
                                    call.answered().origin(), call.function().pos(),
                                    call.function().region()));
            case Hir.Var v -> qualified(v, which);
            default -> rebuilt;
        };
    }

    /** {@code name} written qualified where it denotes a helper {@code which} accepts. */
    private static Hir.Var qualified(Hir.Var name, Predicate<ValueName.Helper> which) {
        return name instanceof Hir.Var.Denoting named
                && foreign(named.denotes(), which)
                ? name.respelledAs(qualifiedName(named.denotes()), ofModule(named.denotes()))
                : name;
    }

    /**
     * {@code e} with {@code rewrite} also applied to what a combinator was handed.
     *
     * <p>A function argument stands in {@link Hir.Expansion#given} and that is not a slot, so no
     * generic walk reaches it: a callee that applies its argument holds the same lambda inside its
     * body, and a walk taking both would read one lambda twice. What is written here is not a
     * general walk. It is the rewrite a body takes when it leaves the module it was written in, and
     * a body leaves whole — an argument the callee never applies stands in {@code given} and nowhere
     * else, and one it does apply still says, there, what it said where it was written. Left alone,
     * it goes on naming the declaring module's helpers by the names that module reached them by,
     * inside a body being read against a reader's.
     */
    private static Hir.Expr alsoInGiven(Hir.Expr e, java.util.function.UnaryOperator<Hir.Expr> rewrite) {
        if (!(e instanceof Hir.Expansion ex)) {
            return e;
        }
        List<Hir.Given> given = new ArrayList<>();
        boolean any = false;
        for (Hir.Given g : ex.given()) {
            Hir.Expr value = rewrite.apply(g.value());
            any |= value != g.value();
            given.add(value == g.value() ? g
                    : new Hir.Given(g.declaredType(), value, g.applied(), g.arrivesAs()));
        }
        return any ? new Hir.Expansion(ex.callee(), ex.application(), ex.at(), ex.bound(), given,
                ex.declaredReturn(), ex.body(), ex.pos(), ex.region()) : e;
    }

    /** Whether {@code denotes} is a helper {@code which} accepts. */
    private static boolean foreign(ValueName denotes, Predicate<ValueName.Helper> which) {
        return denotes instanceof ValueName.Helper helper && which.test(helper);
    }

    /**
     * {@code e} with every construction in it marked as {@code module}'s.
     *
     * <p>What a published body builds is built where that body was written, and the reader is handed
     * the result. Expanding it puts the construction in the reader's body, where the permission check
     * would ask the reader to declare it — for a type the declaring module may keep to itself, under a
     * name the reader has none of. The mark is what tells the two apart afterwards, and it names the
     * module rather than saying only that the construction came from somewhere: a body may build a
     * type of a third module, and that one is nobody's to hand over (ADR-0059).
     *
     * <p>A unit data is not marked. It is constructed by being named and the permission check
     * collects no unit (spec §constructs-excludes-unit-data), so a mark on the name would be one
     * nothing reads.
     *
     * <p>Neither is a call left standing, which is where this and the mark a value leaves differ.
     * What a recursive helper builds is counted from its own body, and that body was marked when it
     * was published, so the check absorbs those constructions as the kinds they already are. A value
     * turns all of them into the value's whatever they were, and there is nothing on the way to say
     * it but the call.
     */
    static Hir.Expr publishedBy(Hir.Expr e, String module) {
        // a spread names a value, and a value is not a construction: what it built was built where it
        // was defined, so the mark is already on it
        Hir.Expr rebuilt = alsoInGiven(Hir.mapChildren(e, c -> publishedBy(c, module), s -> s),
                c -> publishedBy(c, module));
        return rebuilt instanceof Hir.NewData nd ? nd.publishedBy(module) : rebuilt;
    }

    /**
     * {@code e} with every construction in it marked as one a value made.
     *
     * <p>A value is substituted at each reference, so what its definition built ends up standing in
     * the body that named it, as the node that body's own construction would be. The mark is what
     * keeps the permission check reading the model rather than the substitution: a behavior stating a
     * rule against a named limit compares against a value, and originates none of it. A limit is
     * written as a value so that the figure has one place to live and one comment saying where it
     * comes from, and naming it is not supposed to cost every rule that reads it an authority it does
     * not use.
     *
     * <p>Unlike a published body's mark this names no module. What the value built is the value
     * definition's however the type got its declaration, and a behavior reading the name is not the
     * one that made it either way. A helper is the other case and stays the other case: its body is
     * checked as though it had been written inline, which is what tells a helper from a behavior.
     *
     * <p>Two things stand for a construction the permission check reads, and each takes the mark. A
     * construction node carries its own; and a recursive helper is lowered to a method rather than
     * expanded, so what it builds stays behind a call, and the call carries it. Without the second,
     * whether a value's constructions belonged to the value would turn on whether a helper on the
     * way could be expanded — the substitution showing through the rule again, in the one place
     * expansion cannot reach.
     */
    static Hir.Expr carriedByValue(Hir.Expr e) {
        Hir.Expr rebuilt = alsoInGiven(Hir.mapChildren(e, HelperNames::carriedByValue, s -> s),
                HelperNames::carriedByValue);
        return switch (rebuilt) {
            case Hir.NewData nd -> nd.carriedByValue();
            case Hir.Apply call -> call.carriedByValue();
            default -> rebuilt;
        };
    }

    /**
     * The helpers {@code e} still reaches — what a body closed by
     * {@link HelperInliner#closeAcross} could not expand away, which is the recursive ones.
     *
     * <p>Each is given as what it denotes rather than as a spelling, because the two questions a
     * caller then has differ: which module declares it decides how it is keyed, and a binding that
     * shares a helper's spelling is not one of these at all.
     */
    public static Set<ValueName.Helper> helpersReached(Hir.Expr e) {
        Set<ValueName.Helper> out = new LinkedHashSet<>();
        collectHelpersOf(e, out);
        return out;
    }

    private static void collectHelpersOf(Hir.Expr e, Set<ValueName.Helper> out) {
        if (e == null) {
            return;
        }
        // A name nothing declares reaches no helper: it was reported where it is written, and a
        // walk that asks what a name stands for has no edge to add for it.
        ValueName denotes = switch (e) {
            case Hir.Apply call when call.answered() != null -> call.answered().denotes();
            case Hir.Var.Denoting v -> v.denotes();
            default -> null;
        };
        if (denotes instanceof ValueName.Helper helper) {
            out.add(helper);
        }
        Hir.forEachChild(e, c -> collectHelpersOf(c, out));
    }

    /** How a helper written qualified is reached: under the module that declares it, which is what
     * writing it qualified says. Read off what the name denotes, like the spelling beside it. */
    private static ReachName.Declaration ofModule(ValueName denotes) {
        return new ReachName.OfModule((ValueName.Helper) denotes);
    }

    /** The name a helper is reached by outside the module that declares it. Read off what the name
     * denotes, so applying it to a name already written this way answers the same thing. */
    private static String qualifiedName(ValueName denotes) {
        ValueName.Helper helper = (ValueName.Helper) denotes;
        return qualified(helper.module(), helper.name());
    }
}
