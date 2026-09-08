package souther.compiler.program;

import souther.compiler.ast.Ast;
import souther.compiler.ast.Hir;
import souther.compiler.check.AtomSpace;
import souther.compiler.check.BehaviorImplementation;
import souther.compiler.check.CoreBinders;
import souther.compiler.check.Derived;
import souther.compiler.check.Lower;
import souther.compiler.check.Sig;
import souther.compiler.check.SpecImplementation;
import souther.compiler.check.Symbols;
import souther.compiler.check.TypeOps;
import souther.compiler.core.Composition;
import souther.compiler.core.Core;
import souther.compiler.core.EnsuresEnforcement;
import souther.compiler.core.ValueShape;
import souther.compiler.meta.ModulePath;
import souther.compiler.observe.Expectation;
import souther.compiler.observe.FieldTypes;
import souther.compiler.observe.Position;
import souther.compiler.observe.RowOutcome;
import souther.compiler.observe.RowStatement;
import souther.compiler.observe.StoodIn;
import souther.compiler.observe.ValueTypes;
import souther.compiler.query.Acceptance;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.Compositions;
import souther.compiler.query.Db;
import souther.compiler.query.Front;
import souther.compiler.query.Names;
import souther.compiler.query.Output;
import souther.compiler.query.Shapes;
import souther.compiler.stdlib.Stdlib;
import souther.compiler.types.ReachName;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Takes the snapshot: reads what the compiler decided and writes it down as a {@link
 * CheckedProgram}.
 *
 * <p>The one place in this package that knows how this compiler answers its own questions. Which
 * key held a checked body, a signature, a composition's routing is how {@code souther-compiler}
 * computes and invalidates, and it is a thing to keep away from the model the snapshot is: a reader
 * of a checked program that had to name a query would be reading a compiler rather than a program.
 *
 * <p>Nothing read here is carried into what is made. The compilation, its {@link Db}, and every
 * answer object still tied to one are gone by the time this returns; what is left is values. A
 * snapshot holding the session it was taken from would answer differently after the session was
 * edited, which is the one thing a snapshot may not do.
 */
final class CheckedProgramAssembler {

    private CheckedProgramAssembler() {}

    static CheckedProgram of(List<String> sources, ModulePath path) {
        Compilation compilation = Compilation.ofSources(sources, path);
        // The language's verdict, asked where a batch compile asks it. Stopping at the check would
        // make this the one reading that accepts a program with a row that disagrees — and an
        // output built on it would ship an artifact for what another output refuses to build.
        Acceptance.of(compilation);
        Db db = compilation.db();
        List<CheckedData> language = languageDataOf(db);
        List<CheckedData> onThePath = declaredOnThePath(db);
        // What each module was checked to be, read before anything is made of it. Every declaration
        // this compile resolved has to be in hand before a row can be written down, because what a
        // row states may hold a value of a data another module declares — and a comparison that
        // could not read that data would read its parts as whatever they happen to look like.
        List<ModuleReading> read = new ArrayList<>();
        for (String module : compilation.modules()) {
            read.add(readingOf(db, module));
        }
        List<CheckedData> everyDeclaration = new ArrayList<>(language);
        everyDeclaration.addAll(onThePath);
        for (ModuleReading module : read) {
            everyDeclaration.addAll(module.data());
        }
        ValueTypes types =
                ValueTypes.over(FieldTypes.over(DeclaredFields.over(everyDeclaration)));
        // Made before the modules, because a module cannot be made without them: a row of one
        // states what it stood in for a dependency with, and where that dependency's arguments
        // stand is read off the dependency's own boundary — which is a behavior of another module
        // as often as one of this.
        Map<ValueName.Behavior, BehaviorTarget> targets = new LinkedHashMap<>();
        List<ModuleBoundaries> boundaries = new ArrayList<>();
        for (ModuleReading module : read) {
            boundaries.add(fileWhatIsChecked(targets, module));
        }
        fileWhatIsOnThePath(targets, db);
        List<CheckedModule> modules = new ArrayList<>();
        for (ModuleBoundaries module : boundaries) {
            modules.add(moduleOf(module, types, targets));
        }
        return new CheckedProgram(modules, language, onThePath, targets,
                libraryOf(db).kernelSignatures());
    }

    /**
     * What was read of a module, and the call boundary of each behavior it declares.
     *
     * <p>The boundaries in the order the module declares them, which is the order its behaviors are
     * made in. Carried from where they were filed to where the module is made, rather than looked
     * up there: the module is made off the same declarations they were made off, and a second walk
     * of those declarations would be two statements of what the module declares held together by a
     * check.
     */
    private record ModuleBoundaries(ModuleReading read,
                                    Map<ValueName.Behavior, BehaviorTarget> declared) {}

    /**
     * Files the call boundary of every behavior {@code module} declares.
     *
     * <p>What the module declares says which behaviors there are, and the answers this compile
     * worked out say what each of them is — the same three readings a module on the path is read in
     * ({@link #fileWhatIsOnThePath}), and read the same way here. A walk of the signatures instead
     * would let a behavior whose signature this compile failed to answer for leave the program
     * without anything having missed it: what a module declares would be whatever the answer
     * happened to hold.
     *
     * <p>Both answers are read here, beside the name they belong to, and a missing one is refused
     * here. Read further in, at the place that decides what an implementation is made of, a missing
     * answer arrives where a state is being chosen — and it is chosen as one of them.
     */
    private static ModuleBoundaries fileWhatIsChecked(
            Map<ValueName.Behavior, BehaviorTarget> targets, ModuleReading module) {
        Map<ValueName.Behavior, BehaviorTarget> declares = new LinkedHashMap<>();
        Map<String, SpecImplementation.Implemented> implementations =
                SpecImplementation.implementationsOf(module.bodies());
        for (Hir.BehaviorDef declared : module.bodies().behaviors()) {
            ValueName.Behavior named = new ValueName.Behavior(module.name(), declared.name());
            Sig signature = module.signatures().get(declared.name());
            BehaviorImplementation state = module.implementations().get(declared.name());
            if (signature == null || state == null) {
                // The module was taken as checked and one of the behaviors it declares has no
                // signature, or no reading of where its body comes from. A caller reaches it, so
                // letting it through hands an output a call it cannot emit and says nothing about
                // why.
                throw new IllegalStateException("`" + named + "` was taken as checked and this"
                        + " compile has no reading of it");
            }
            BehaviorTarget target = new BehaviorTarget(signatureOf(signature),
                    implementedAs(state, named, declared, implementations, module.checked(),
                            module.compositions()));
            file(targets, named, target);
            declares.put(named, target);
        }
        return new ModuleBoundaries(module, declares);
    }

    /**
     * Files the call boundary of every behavior every module this compile read off the path
     * declares.
     *
     * <p>The whole of what each declares, and not the part something here calls. Which behaviors a
     * body reaches is a walk, and a snapshot carrying only what one walk found would be right about
     * the calls that walk thought to visit — and an output emitting those bodies would be the
     * second place that decided what belongs.
     *
     * <p>What the module declares says which behaviors there are, and the answers this compile
     * worked out say what each of them is. Three readings and not one: a module read off the path
     * is available here because a module of this compile was checked against it, so a name it
     * declares that this compile has no signature or no implementation state for is this
     * compilation's two readings of that artifact having come apart, and is refused rather than
     * quietly left out of the program.
     */
    private static void fileWhatIsOnThePath(Map<ValueName.Behavior, BehaviorTarget> targets,
                                            Db db) {
        for (String module : readOffThePath(db)) {
            Ast.Module declares = db.ask(new Front.Available(module)).value();
            Map<String, Sig> signatures = db.ask(new Bodies.Signatures(module)).value();
            Map<String, BehaviorImplementation> implementations =
                    db.ask(new Bodies.Implementation(module)).value();
            if (declares == null || signatures == null || implementations == null) {
                throw new IllegalStateException("`" + module + "` was read off the path and this"
                        + " compile has nothing to say about the behaviors it declares");
            }
            for (Ast.BehaviorDef declared : declares.behaviors()) {
                ValueName.Behavior named = new ValueName.Behavior(module, declared.name());
                Sig signature = signatures.get(declared.name());
                BehaviorImplementation state = implementations.get(declared.name());
                if (signature == null || state == null) {
                    throw new IllegalStateException("`" + named + "` is declared by a module this"
                            + " compile read off the path and this compile has no reading of it");
                }
                file(targets, named,
                        new BehaviorTarget(signatureOf(signature), publishedAs(state)));
            }
        }
    }

    /**
     * Where the implementation of a behavior another compile published comes from.
     *
     * <p>Its three states are the three a behavior is in anywhere. What a call reaches differs in
     * one of them: an implementation this program does not hold, because the build that published
     * the module emitted it. The other two are what they are wherever the behavior was declared —
     * an injected behavior's implementation comes from outside Souther on either side of a path,
     * and an unwritten one is nowhere at all.
     */
    private static CheckedImplementation publishedAs(BehaviorImplementation state) {
        return switch (state) {
            case IMPLEMENTED -> new CheckedImplementation.ImplementedElsewhere();
            case INJECTION_TARGET -> new CheckedImplementation.Injected();
            case UNIMPLEMENTED -> new CheckedImplementation.Unwritten();
        };
    }

    /**
     * Files one call boundary, and refuses a second under the same identity.
     *
     * <p>An identity belongs to one declaration. A module of this compile takes the name over one
     * of the same name on the path, so the two worlds never meet here — and one that did would be
     * called through whichever was filed last with nothing saying the other had been there.
     */
    private static void file(Map<ValueName.Behavior, BehaviorTarget> targets,
                             ValueName.Behavior named, BehaviorTarget target) {
        if (targets.put(named, target) != null) {
            throw new IllegalStateException("`" + named + "` is declared twice");
        }
    }

    /**
     * The modules this compile read off the path.
     *
     * <p>{@link Front.FromPath}'s answer: the ones this compilation may read declarations from,
     * which is not every module on the path and never one it refused. Asked once for everything
     * taken off them, so that what a dependency declares and what its behaviors are are read off
     * one set of modules — two walks with a rule each would agree until either was written again.
     *
     * <p>Empty where nothing was read off a path, which is a compilation given none.
     */
    private static Set<String> readOffThePath(Db db) {
        Front.FromPath.Of read = db.ask(new Front.FromPath()).value();
        return read == null ? Set.of() : read.modules().keySet();
    }

    /** The library this compilation was checked against. Asked for here rather than fetched: what a
     *  name in a checked body denotes was settled against that one, and a second copy could be a
     *  different version of the language. */
    private static Stdlib libraryOf(Db db) {
        Stdlib stdlib = db.ask(new Front.Library()).value();
        if (stdlib == null) {
            throw new IllegalStateException("this compilation was checked against no library");
        }
        return stdlib;
    }

    /**
     * What the language itself declares, as a value of one is laid out.
     *
     * <p>Read off the library this compilation was checked against, and not off a library fetched
     * here: what a name in a checked body denotes was settled against that one, and a second copy
     * could be a different version of the language.
     *
     * <p>Read against a world with no module in it. Which cases a sum descends to is answered by
     * {@link AtomSpace}, which asks the declarations it is given — and the language's own resolve
     * against the library alone, so reading them beside any one module's declarations would be
     * reading them somewhere they could have come out differently.
     */
    private static List<CheckedData> languageDataOf(Db db) {
        Stdlib stdlib = libraryOf(db);
        Symbols language = Symbols.none(stdlib);
        List<CheckedData> declared = new ArrayList<>();
        for (Hir.Def def : stdlib.languageDeclarations().values()) {
            if (def instanceof Hir.Data product) {
                // The language declares sums and the units under them, and a product of its own
                // would be one nothing here has the fields and clauses of: what a value of a
                // declaration is made of is derived over a compilation's modules, and derivation
                // does not run over the library. Said as what it is, rather than reached as an
                // absent shape — which is also what an assembler that forgot to read the shapes
                // would look like.
                throw new IllegalStateException("the language declares `" + product.declares()
                        + "` as a product, and what a value of one is made of is not derived here");
            }
            declared.add(declaredAs(def, language, Map.of()));
        }
        return declared;
    }

    /**
     * What every module this compile read off the path declares, as a value of one is laid out.
     *
     * <p>Read here and not left to the compile that built the dependency. This one read those
     * declarations already — it had to, to check a module that constructs one of them or reads a
     * field off one — so what is handed over is the reading the checker itself used, and an output
     * laying such a value out places its fields exactly where the check placed them.
     *
     * <p>The whole of what each declares, and not the part something here happens to name. Which
     * declarations a body reaches is a walk, and a snapshot carrying only what one walk found would
     * be right about the names that walk thought to visit.
     */
    private static List<CheckedData> declaredOnThePath(Db db) {
        List<CheckedData> declared = new ArrayList<>();
        for (String module : readOffThePath(db)) {
            Map<String, Derived.Def> defs = db.ask(new Shapes.DerivedDeclarations(module)).value();
            Map<TypeSymbol.AtModule, ValueShape> shapes =
                    db.ask(new Shapes.ValueShapes(module)).value();
            Symbols symbols = Names.derivedSymbols(db, module).value();
            if (defs == null || shapes == null || symbols == null) {
                // This compile read the module and checked a module against it, so what it declares
                // is something this compile already worked out. Letting it through would hand an
                // output a program whose identities it cannot all lay out, which is the thing this
                // is here to end.
                throw new IllegalStateException("`" + module + "` was read off the path and this"
                        + " compile has nothing to say about what it declares");
            }
            for (Derived.Def def : defs.values()) {
                declared.add(declaredAs(def.declaration().node(), symbols, shapes));
            }
        }
        return declared;
    }

    /**
     * What this compiler answered about one module, and what those answers were made into.
     *
     * <p>Read in one pass so that what a module declares is in hand before what its rows state is
     * written down: the two are made in that order and not in the order the modules were given.
     *
     * @param rowsByBehavior what each of the module's behaviors' rows turned out to be, by behavior
     *             name, in the order the sources were read and the rows written
     */
    private record ModuleReading(String name, Hir.Module bodies, Bodies.Elaborated checked,
                                 Map<String, Sig> signatures,
                                 Map<String, BehaviorImplementation> implementations,
                                 Map<ValueName.Behavior, Composition> compositions,
                                 Map<ValueName.Behavior, EnsuresEnforcement> checks,
                                 List<CheckedData> data,
                                 Map<String, List<Output.RowsRead.ReadRow>> rowsByBehavior) {}

    /**
     * The rows this compile read for {@code module}, by the behavior each is a row of.
     *
     * <p>Asked for rather than gathered. Which rows a behavior has is an answer over every source
     * the module's rows are written in, and it is one answer: a caller assembling it again decides
     * for itself what a source that did not answer means, and what it decided would be a second
     * statement of one fact. It is also what says a row was not read at all rather than not written.
     *
     * <p>Nothing is evaluated by asking. Running a row applies the helpers its fixtures name, and a
     * second run would apply them again — counted twice against the row and doing whatever they do
     * twice. What comes back is what the compile already answered.
     */
    private static Map<String, List<Output.RowsRead.ReadRow>> rowsOf(Db db, String module) {
        Output.RowsRead.Of read = db.ask(new Output.RowsRead(module)).value();
        if (read == null) {
            throw new IllegalStateException("`" + module + "` was taken as checked and its rows"
                    + " were not read");
        }
        Map<String, List<Output.RowsRead.ReadRow>> byBehavior = new LinkedHashMap<>();
        read.byBehavior().forEach((behavior, its) -> byBehavior.put(behavior, its.rows()));
        return byBehavior;
    }

    private static ModuleReading readingOf(Db db, String module) {
        Bodies.Elaborated checked = db.ask(new Bodies.Checked(module)).value();
        Lower.Lowered lowering = db.ask(new Bodies.Lowering(module)).value();
        Map<String, Sig> signatures = db.ask(new Bodies.Signatures(module)).value();
        Map<String, BehaviorImplementation> implementations =
                db.ask(new Bodies.Implementation(module)).value();
        Map<ValueName.Behavior, Composition> compositions =
                db.ask(new Compositions.Of(module)).value();
        // What a value of each declared data is made of and must satisfy, and where each behavior's
        // clause is checked. Both are what the language decided and both are read here rather than
        // worked out: the JVM is handed the same two answers, so an output reading this program and
        // the bytecode beside it hold one another's decisions.
        Map<TypeSymbol.AtModule, ValueShape> shapes =
                db.ask(new Shapes.ValueShapes(module)).value();
        Map<ValueName.Behavior, EnsuresEnforcement> checks =
                db.ask(new Bodies.EnsuresChecks(module)).value();
        // What the module's names mean over the derived declarations, which is what a declaration's
        // fields and a sum's cases are read against. It is a way of reaching the compiler's answers
        // and not one of them: it holds a registry that asks `db` for each declaration, so it is
        // read here and dropped here, and nothing it was reached through is carried into what is
        // made.
        Symbols symbols = Names.derivedSymbols(db, module).value();
        if (checked == null || lowering == null || signatures == null
                || implementations == null
                || compositions == null || symbols == null || shapes == null || checks == null) {
            // Not a report: the failure above is what a caller is told, and reaching here past it
            // means the two readings of whether this program checked have come apart.
            throw new IllegalStateException("`" + module + "` was taken as checked and is not");
        }
        // Two trees and each is read for what it is the tree for: a declaration comes off the
        // settled one and a body off the lowered one, which is the division `Lower.Lowered` states.
        // They are both `Hir.Module`, so nothing but the name at the call below says which is
        // being handed over — and a declaration read off the tree the backend emits from would
        // agree with the checker only for as long as lowering left declarations alone.
        Hir.Module declarations = lowering.settled();
        Hir.Module bodies = lowering.lowered();
        return new ModuleReading(module, bodies, checked, signatures, implementations,
                compositions, checks, dataOf(declarations, symbols, shapes), rowsOf(db, module));
    }

    /**
     * The module, made from what was read of it.
     *
     * <p>{@code types} is how a value's parts are read wherever one is compared, and it is the whole
     * program's rather than this module's: a row written here may state a value of a data declared
     * elsewhere.
     */
    private static CheckedModule moduleOf(ModuleBoundaries module, ValueTypes types,
                                          Map<ValueName.Behavior, BehaviorTarget> targets) {
        ModuleReading read = module.read();
        List<CheckedBehavior> behaviors = new ArrayList<>();
        module.declared().forEach((named, target) ->
                behaviors.add(new CheckedBehavior(named, target,
                        EnsuresEnforcement.in(read.checks(), read.name(), named),
                        rowsOf(read.rowsByBehavior().getOrDefault(named.name(), List.of()), types,
                                target.signature(), targets))));
        return new CheckedModule(read.name(), behaviors,
                helpersOf(read.name(), read.bodies(), read.checked()), read.data());
    }

    /**
     * One behavior's rows, as an output reads them.
     *
     * <p>Written down rather than worked out: what each row states was read where the row was read,
     * and what is made here is the handle a reader holds it by. Each is given what answering
     * {@link CheckedRow.SelfContained#holds} takes — how a value's parts are read, and where this
     * behavior's answer stands — so that asking is not a question about the program the row came
     * from. A row that stands something in for a dependency is given where that dependency's
     * arguments stand as well, for the same reason.
     */
    private static List<CheckedRow> rowsOf(List<Output.RowsRead.ReadRow> read, ValueTypes types,
                                           CheckedSignature signature,
                                           Map<ValueName.Behavior, BehaviorTarget> targets) {
        List<CheckedRow> rows = new ArrayList<>();
        for (Output.RowsRead.ReadRow row : read) {
            rows.add(new CheckedRow(row.identity(), row.at(),
                    statementOf(row, types, signature, targets)));
        }
        return rows;
    }

    /**
     * What the row states, as a reader of a checked program may act on it.
     *
     * <p>A row that stated values is given what asking takes — how a value's parts are read here,
     * and where this behavior's answer stands — and the reading it is given is this program's. The
     * one the compile read the text with is not carried: it is a way of reaching this compiler's
     * answers rather than one of them, and a snapshot holding it would hold the compilation it came
     * from.
     *
     * <p>A switch, so a way of stating a row added later is written down here rather than falling
     * into whichever arm it happens to reach.
     */
    private static CheckedRow.Statement statementOf(Output.RowsRead.ReadRow row, ValueTypes types,
                                                    CheckedSignature signature,
                                                    Map<ValueName.Behavior, BehaviorTarget> targets) {
        // A switch over both sums, so a row nothing came back for is written down here rather than
        // being whatever falls out of reading a list of the ones that did — which is a row an output
        // would never hear of, and a behavior reading as having said nothing about an input someone
        // wrote down.
        return switch (row) {
            case Output.RowsRead.ReadRow.Ran(RowOutcome outcome) -> switch (outcome.statement()) {
                // A row whose answer is owed first, because what a reader can do with a row turns on
                // whether there is anything to hold before it turns on what the row needs to run.
                // Sorted the other way, such a row would arrive as one an output applies and asks,
                // and what it asked would be answered against no statement at all.
                case RowStatement.Stated stated
                        when stated.expects() instanceof Expectation.Owed ->
                        new CheckedRow.AnswerOwed(stated);
                case RowStatement.Stated stated -> stated.standIns().isEmpty()
                        ? new CheckedRow.SelfContained(stated, types,
                                Position.at(signature.answers()))
                        : new CheckedRow.WithStandIns(stated, types,
                                Position.at(signature.answers()),
                                whereArgumentsStand(stated, targets));
                case RowStatement.NotStated why -> new CheckedRow.NotReproducible(why);
                // What acceptance guarantees, asserted where the guarantee is relied on. A row an
                // evaluation stopped before the values of is one the language refuses the program
                // for, so meeting one here is that guarantee having moved — and the row would
                // otherwise be published as a state whose reason is in a compile nobody outside
                // this one can read.
                case RowStatement.StoppedBeforeItsValues _ -> throw new IllegalStateException(
                        "a program the language accepted holds a row its evaluation stopped before"
                                + " the values of: " + outcome.target() + " "
                                + outcome.identity().shown() + " at " + outcome.at());
            };
            case Output.RowsRead.ReadRow.NotRun notRun ->
                    new CheckedRow.NotReproducible(new RowStatement.NotRead(notRun.why()));
        };
    }

    /**
     * Where the arguments of each dependency the row stood in for stand.
     *
     * <p>Off the dependency's own call boundary, which is the one thing this snapshot says about
     * what that behavior takes — so what a stand-in's entries were built against and what a reader
     * compares an argument at are one reading of one declaration, rather than the same declaration
     * written down twice and held to itself by a law.
     *
     * <p>It is what says how a comparison is made as well as how many are: whether an argument that
     * is a sequence is the same one written in another order is what the type reading it says.
     */
    private static Map<ValueName.Behavior, List<Position>> whereArgumentsStand(
            RowStatement.Stated stated, Map<ValueName.Behavior, BehaviorTarget> targets) {
        Map<ValueName.Behavior, List<Position>> stands = new LinkedHashMap<>();
        for (StoodIn stoodIn : stated.standIns()) {
            BehaviorTarget declared = targets.get(stoodIn.dependency());
            if (declared == null) {
                // A row stood in for a behavior no module this compile checked or read declares.
                // Whether the module the row is written in may name that behavior is settled where
                // the names are resolved and is not asked again here; what is asked is whether the
                // program the row is being written into declares it at all.
                throw new IllegalStateException("`" + stoodIn.dependency() + "` was stood in for by"
                        + " a row and no module this compile read declares it");
            }
            List<Position> arguments = new ArrayList<>();
            for (Type takes : declared.signature().takes()) {
                arguments.add(Position.at(takes));
            }
            stands.put(stoodIn.dependency(), arguments);
        }
        return stands;
    }

    /**
     * What the module declares.
     *
     * <p>Each of the four forms is materialised from the answer this compiler already has for it,
     * and neither walk is written again here. A product's fields are
     * {@link TypeOps#fieldTypes}, which flattens what an include brought in; a sum's cases are
     * {@link AtomSpace#subjectAtoms}, which descends a case that is itself a sum and reaches one
     * case once. Both are decisions the language made, and an assembler with a walk of its own
     * would be the second place that made them — which is the thing a reader outside this compiler
     * is being given these to avoid.
     *
     * <p>{@code fieldTypes} answers in the order a value lays its fields out, and that order is
     * carried straight into the list. Nothing between the two holds the fields as a set: an order
     * the answer decided, passed through something that does not keep one, comes out as an order
     * nothing decided.
     *
     * <p>The list is what the module holds and its order is not answered for. A declaration written
     * on its own and one a sum's case list declares reach {@code defs} by different routes, so
     * where either stands among them is how the front end put them there rather than something the
     * language decided. The two orders that are decided are inside a declaration —
     * {@link CheckedData.WithFields#fields} and {@link CheckedData.Sum#cases} — and those are the
     * ones said out loud.
     */
    private static List<CheckedData> dataOf(Hir.Module declarations, Symbols symbols,
                                           Map<TypeSymbol.AtModule, ValueShape> shapes) {
        List<CheckedData> declared = new ArrayList<>();
        for (Hir.Def def : declarations.defs()) {
            declared.add(declaredAs(def, symbols, shapes));
        }
        return declared;
    }

    /**
     * One declaration, in whichever of the four forms it was written.
     *
     * <p>One reading for both worlds. A module's declaration and the language's are the same kind
     * of thing — they resolve and type alike and a value of either lays out alike — and this is
     * where that stops being something two readings agree about.
     */
    private static CheckedData declaredAs(Hir.Def def, Symbols symbols,
                                          Map<TypeSymbol.AtModule, ValueShape> shapes) {
        return switch (def) {
            case Hir.Data data -> checkedDataOf(data, shapes);
            case Hir.SumData sum -> new CheckedData.Sum(sum.declares(),
                    AtomSpace.subjectAtoms(Type.ref(sum.declares()), symbols));
            case Hir.UnitData unit -> new CheckedData.Unit(unit.declares());
        };
    }

    /**
     * One data built field by field, as the check answered what a value of it is made of and as the
     * author wrote it.
     *
     * <p>Handed over and not rebuilt. The fields, the binding each is read through and the clauses
     * that must hold of a value are one answer of the checker's, and the JVM emits a construction
     * from that same answer — so what an output outside this compiler reads and what the bytecode
     * refuses a value by cannot come apart.
     *
     * <p>Which of the two arms it is, is what the author wrote. A newtype and a one-field product
     * reach here with the same shape, and the shape is what the language says does not decide
     * between them (spec §newtype), so it is read off the declaration — the same answer a derived
     * codec is generated from.
     */
    private static CheckedData checkedDataOf(Hir.Data data,
                                             Map<TypeSymbol.AtModule, ValueShape> shapes) {
        ValueShape shape = shapes.get(data.declares());
        if (shape == null) {
            // The module was taken as checked, and a declaration of it has no answer for what a
            // value of it is. Handing over a product with no clauses would be saying that anything
            // its fields admit is one of it, which is the opposite of what the author wrote.
            throw new IllegalStateException("`" + data.declares() + "` was taken as checked and"
                    + " the check said nothing about what a value of it is");
        }
        return data.newtype() ? new CheckedData.Newtype(shape) : new CheckedData.Product(shape);
    }

    /**
     * What the behavior takes and answers, as types.
     *
     * <p>The reading of a boundary the compiler's own signature carries as well — which of a
     * {@code Map}'s key readings admitted it, and the witness that says so — is left behind here.
     * That witness offers the module as it was parsed, so a signature handed over whole would put
     * the syntax tree two hops from a behavior's declared output.
     */
    private static CheckedSignature signatureOf(Sig signature) {
        return new CheckedSignature(signature.inputTypes(), signature.outputType());
    }

    /**
     * Where this behavior's implementation comes from, for a behavior this compile checked.
     *
     * <p>Never {@link CheckedImplementation.ImplementedElsewhere}: this compile holds what a module
     * it checked is implemented as, and a behavior published by another compile is read by
     * {@link #publishedAs}.
     *
     * <p>Given the state rather than the table it is in, as {@link #publishedAs} is. What a
     * behavior this compile has no reading of comes to is not a state to choose between here — it
     * is a program with nothing to say about one of its own behaviors, said where a behavior's
     * answers are read.
     *
     * <p>What is decided here is the form: the state says a body was written and the check says
     * whether it settled a composition, and a reader handed the two separately would be deciding
     * for itself what an implemented behavior with no Core is.
     */
    private static CheckedImplementation implementedAs(
            BehaviorImplementation state, ValueName.Behavior named, Hir.BehaviorDef declared,
            Map<String, SpecImplementation.Implemented> implementations, Bodies.Elaborated checked,
            Map<ValueName.Behavior, Composition> compositions) {
        String name = declared.name();
        return switch (state) {
            case UNIMPLEMENTED -> new CheckedImplementation.Unwritten();
            case INJECTION_TARGET -> new CheckedImplementation.Injected();
            // A composition is what the behavior is declared as, so what says it is one is that the
            // check settled stages for it and not the absence of a body.
            case IMPLEMENTED -> {
                Composition composed = compositions.get(named);
                yield composed != null
                        ? new CheckedImplementation.Composed(composed)
                        : new CheckedImplementation.Body(
                                inputBindersOf(named, implementations.get(name)),
                                checked.behaviorBodies().get(name));
            }
        };
    }

    /**
     * The bindings this behavior's body reads its declared inputs through.
     *
     * <p>Read off the definition that implements it, and divided from what that definition takes by
     * {@link SpecImplementation} — which is the same reading the JVM emitter binds its parameters
     * from, so the snapshot and the emitted program cannot come to disagree about which local an
     * input arrives in. The division is that rule's and not this one's: a definition's trailing
     * parameters are the behaviors it depends on, and an assembler slicing the list itself would be
     * a second place that knew so.
     *
     * <p>Sliced by what the behavior declares and not by how long its signature is. Sliced by the
     * signature the two lengths would be equal by construction, and {@link BehaviorTarget}'s check
     * of them would be comparing an answer with itself — the disagreement it is there to refuse
     * would arrive instead as a dependency's binder handed over as an input's.
     */
    private static List<Core.Binder> inputBindersOf(ValueName.Behavior named,
                                                    SpecImplementation.Implemented implemented) {
        if (implemented == null) {
            // This behavior was taken as implemented here and by a body of its own, and the module
            // has no definition to read one from. Nothing here can put that right, and letting it
            // through would hand an output a body whose reads resolve to nothing it was given.
            throw new IllegalStateException("`" + named.module() + "." + named.name()
                    + "` was taken as having a body and has no definition to read it from");
        }
        if (!implemented.hasCompleteShape()) {
            // What this assembler takes, said here rather than by the reading that divided the
            // parameters: an editor reads a definition whose parameters do not line up and answers
            // what it can about it, and a checked program holds none.
            throw new IllegalStateException("`" + named.module() + "." + named.name()
                    + "` was taken as checked and its implementation writes parameters the"
                    + " declaration does not account for");
        }
        List<Core.Binder> binders = new ArrayList<>();
        for (Hir.FnParam input : implemented.inputs()) {
            binders.add(CoreBinders.of(input.binder()));
        }
        return binders;
    }

    /**
     * The helpers this module emits as definitions of their own.
     *
     * <p>A helper's body is the check's; what it takes is the definition's, which the check did not
     * rewrite. Both are read here so that a call reaching a helper reaches something the snapshot
     * holds.
     */
    private static List<CheckedHelper> helpersOf(String module, Hir.Module lowered,
                                                 Bodies.Elaborated checked) {
        Map<String, Hir.FnDef> defined = new LinkedHashMap<>();
        for (Hir.FnDef fn : lowered.fns()) {
            defined.put(fn.name(), fn);
        }
        for (Hir.FnDef fn : lowered.takenOn()) {
            defined.put(fn.name(), fn);
        }
        List<CheckedHelper> helpers = new ArrayList<>();
        checked.emittedHelpers().forEach((name, body) -> {
            Hir.FnDef fn = defined.get(name);
            if (fn == null) {
                // A call in a body reaches this helper by name, so a snapshot without it hands an
                // output a call to something it was never given. Nothing here can put that right,
                // and letting it through is what makes it the reader's problem.
                throw new IllegalStateException("the checked helper `" + module + "." + name
                        + "` has no definition to read what it takes from");
            }
            List<CheckedHelper.Parameter> parameters = new ArrayList<>();
            for (Hir.FnParam parameter : fn.params()) {
                parameters.add(new CheckedHelper.Parameter(CoreBinders.of(parameter.binder()),
                        TypeOps.resolveParamType(parameter.type())));
            }
            // What the calls in this module reach it by. A definition this module took on says so
            // itself; one it declared it reaches as it stands. Neither is worked out from the name
            // it is filed under here — that name is where the module holds the method, and the
            // alias a library operation is carried under says nothing about who declared it.
            ReachName.Declaration reachedAs = fn.takenOnAs() != null ? fn.takenOnAs()
                    : new ReachName.Own(new ValueName.Helper(module, fn.name()));
            helpers.add(new CheckedHelper(reachedAs, parameters, body));
        });
        return helpers;
    }
}
