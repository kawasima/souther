package souther.compiler.query;

import souther.compiler.ast.Hir;
import souther.compiler.check.BehaviorRequirement;
import souther.compiler.check.CheckedDeclarations;
import souther.compiler.check.CheckedEnsures;
import souther.compiler.check.Prepared;
import souther.compiler.check.Sig;
import souther.compiler.check.DerivedSymbols;
import souther.compiler.core.Contract;
import souther.compiler.core.ValueShape;
import souther.compiler.execute.ExampleExecution;
import souther.compiler.observe.FieldTypes;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.ValueName;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What an evaluation of one module's examples reads, put together in one place.
 *
 * <p>Four readers wanted the same thing and each worked it out again: running a source's rows,
 * building the fakes it wrote, comparing what two written statements say, and handing the rows to a
 * caller that runs them one at a time. The same six questions asked of the same module, and the
 * same lines deciding whether the answers were there yet, written four times.
 *
 * <p>What that cost is not the repetition. It is that "this module can have its examples evaluated"
 * had four definitions and nothing said they were the same one — a reader added later got whichever
 * of them it was copied from, and a condition tightened in one place stayed loose in the other
 * three. Asked here, there is one.
 *
 * <p><b>Not a key, and it must not become one.</b> What this hands back is not a value: a
 * {@code Symbols} closes over the store it resolves against, so an answer holding one would be tied
 * to the session it was taken from and could never equal its own recomputation — which is the one
 * thing this store's answers may not do. It is why the symbols are reached through a helper rather
 * than a key of their own. Each of the six parts is memoised where it is answered; putting them
 * side by side is the cheap part and is done afresh.
 *
 * <p>Null where any part of it is missing, and it does not say which. What a caller does about a
 * module that cannot be read yet differs — one goes absent, one has nothing to report, one raises —
 * and each of those is the caller's answer to its own question rather than a shade of this one.
 */
public final class ExampleExecutions {

    private ExampleExecutions() {}

    /** The environment {@code module}'s examples are evaluated in, or null where this compile has
     *  not settled it. */
    public static ExampleExecution of(Db db, String module) {
        return of(db, module, true);
    }

    /**
     * The same, {@code withDeclaring} saying whether to take the readings of the modules this one
     * writes stand-ins for behaviors of.
     *
     * <p>False for those readings themselves. What a module borrows statements about is its own
     * question, and following it further would assemble a reading nothing asks anything of — one
     * level is what a stand-in written here reaches.
     */
    private static ExampleExecution of(Db db, String module, boolean withDeclaring) {
        Answer<Prepared> prepared = db.ask(new Shapes.Prepared(module));
        Answer<DerivedSymbols> scope = Names.derivedSymbols(db, module);
        // Every behavior the rows may name, keyed by the declaration it is. A row targets one of
        // this module's own and a stand-in may name one another module declares, and both are asked
        // of this one answer — the narrower question, this module's own signatures under their bare
        // names, cannot tell a borrowed dependency from a namesake declared here.
        Answer<Map<ValueName.Behavior, Sig>> sigs = db.ask(new Bodies.Reachable(module));
        if (!prepared.present() || !scope.present() || !sigs.present()) {
            return null;
        }
        if (!db.ask(new Bodies.Checked(module)).present()) {
            return null;   // a module that did not check has nothing to run
        }
        // Asked for as a readiness condition rather than as an input to reading them: a module
        // whose requirements are not settled is not one to read statements off yet.
        Map<String, List<BehaviorRequirement>> requirements =
                db.ask(new Bodies.Requirements(module)).value();
        // Every behavior a row or a stand-in here may state something about, its clause under the
        // declaration it belongs to. A stand-in for a dependency another module declares is held to
        // that module's clause, by the check that module emitted.
        Map<ValueName.Behavior, CheckedEnsures> declared =
                db.ask(new Bodies.ReachableContracts(module)).value();
        if (requirements == null || declared == null) {
            return null;
        }
        // The reading that runs. What decides a row is the emitted check (spec §example-ensures),
        // and this is what that check was emitted from.
        Map<ValueName.Behavior, Contract> contracts = CheckedEnsures.executableOf(declared);
        // The one part that may be empty rather than missing. A module defining no value of its own
        // reaches none by name, which is a module rather than an unanswered question.
        Map<String, Hir.FnDef> values = db.ask(new Bodies.ModuleDefinitions(module)).value();
        return new ExampleExecution(prepared.value(), scope.value(),
                checkedFieldTypes(db), sigs.value(),
                requirements, values == null ? Map.of() : values, contracts,
                Output.policyOf(db),
                withDeclaring ? declaringOf(db, prepared.value()) : Map.of());
    }

    /**
     * What the check settled the declarations hold, for a reading that is going to run rows.
     *
     * <p>Made here and nowhere else. A reading that runs a row is downstream of the module having
     * checked — which is what {@link #of(Db, String, boolean)} establishes before anything is built
     * — and that is the warrant for the one thing this answer does that no other reading may: a
     * declaration it can see that the check settled nothing about is a broken world rather than a
     * value with no fields. Handed out to whoever asked for it with a {@code Db} and a scope, that
     * warrant would be nobody's to hold, and the first reading of a text that had not been accepted
     * would turn what it could not measure into a fault.
     *
     * <p>Reached by the declaration's own identity: a data names the module that wrote it, and that
     * module's shapes say what a value of it is made of. So there is no order in which one module's
     * declarations are tried before another's, and nothing is gathered into a table beforehand.
     */
    private static FieldTypes checkedFieldTypes(Db db) {
        return FieldTypes.over(new CheckedDeclarations(Shapes.publishedDeclarations(db), declared -> {
            Map<TypeSymbol.AtModule, ValueShape> shapes =
                    db.ask(new Shapes.ValueShapes(declared.module())).value();
            return shapes == null ? null : shapes.get(declared);
        }));
    }

    /**
     * The reading of each module {@code prepared} writes a stand-in for a behavior of.
     *
     * <p>Read off the stand-ins, so a module is here because a statement written here is about one
     * of its behaviors — not because an import line reached it. Both forms: a {@code with} states
     * what a dependency answers as a {@code fake} does, and taking the tables for the whole of what
     * is written here would pass over every behavior a row supplies without one. The module's own is
     * not among them: its rows are what the reading already has.
     *
     * <p>A module whose reading this compile has not settled is left out. Nothing was compared, and
     * saying so by absence is what every other unsettled reading here says.
     */
    private static Map<String, ExampleExecution> declaringOf(Db db, Prepared prepared) {
        Map<String, ExampleExecution> out = new LinkedHashMap<>();
        for (ValueName.Behavior each : prepared.forExamples().standsInFor()) {
            if (each.module().equals(prepared.name()) || out.containsKey(each.module())) {
                continue;
            }
            ExampleExecution reading = of(db, each.module(), false);
            if (reading != null) {
                out.put(each.module(), reading);
            }
        }
        return out;
    }
}
