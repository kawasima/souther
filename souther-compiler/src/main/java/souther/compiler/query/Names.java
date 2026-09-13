package souther.compiler.query;

import souther.compiler.stdlib.Stdlib;
import souther.compiler.source.SourceId;

import souther.compiler.ast.Ast;
import souther.compiler.ast.Hir;
import souther.compiler.ast.WrittenName;
import souther.compiler.check.DeclarationKind;
import souther.compiler.check.DeclarationRefusals;
import souther.compiler.check.Derived;
import souther.compiler.check.DerivedSymbols;
import souther.compiler.check.Normalized;
import souther.compiler.check.ResolvedSymbols;
import souther.compiler.check.Denoting;
import souther.compiler.check.DeclaredNames;
import souther.compiler.check.ModuleUniverse;
import souther.compiler.check.Scoping;
import souther.compiler.check.Registry;
import souther.compiler.check.Requirements;
import souther.compiler.check.Resolve;
import souther.compiler.check.SyntaxSymbols;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.msg.DeclarationMessage;
import souther.compiler.diag.msg.ExampleMessage;
import souther.compiler.examples.ExampleStatements;
import souther.compiler.diag.msg.DataMessage;
import souther.compiler.diag.msg.ModuleMessage;
import souther.compiler.diag.msg.ImportMessage;
import souther.compiler.diag.Region;
import souther.compiler.diag.QuotedFrom;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.BindingId;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.Denotation;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the names in a module mean: which module a qualifier names, which declarations an import
 * brings in, and — once those are settled — the module with every written type name resolved to the
 * declaration it denotes.
 *
 * <p>Each of these is asked once per module and answered once. That is the point of putting them
 * here: {@link souther.compiler.check.Symbols} is built at several stages of a compile, and before
 * this every stage revalidated the same imports and rebuilt the same scope, so a bad import was
 * found two or three times and whichever came first decided what the author read.
 */
public final class Names {

    private Names() {}

    /**
     * What every registry over this store answers {@link Registry#declares} with.
     *
     * <p>One answer and not one per rung, because being declared is not a fact about a rung. Written
     * here rather than defaulted on the interface so that a registry reading this store cannot be
     * left answering it out of the declaration it happens to hold, which is what puts where a
     * declaration stands into a reader that asked only whether it was there.
     */
    private static boolean declared(Db db, TypeKey address) {
        return Boolean.TRUE.equals(db.ask(new CompilationDeclares(address)).value());
    }

    /**
     * A registry over this compilation, reading each module's declarations as resolution left them.
     *
     * <p>One of the two declaration worlds a module can be read against, and which one a reader gets
     * is which question it asked: this one answers {@link #resolvedSymbols}, and the derived one
     * answers {@link #derivedSymbols}. Neither is chosen by a value handed in — a reader that could
     * pass which world it wanted is a reader that could pass the wrong one, and nothing in what it
     * was holding would say so.
     */
    static Registry<Hir.Def> resolvedRegistry(Db db) {
        return new Registry<Hir.Def>() {
            @Override
            public Hir.Def declaration(TypeKey address) {
                Answer<Hir.Def> def = db.ask(new ResolvedDeclaration(address));
                return def.present() ? def.value() : null;
            }

            @Override
            public boolean declares(TypeKey address) {
                return declared(db, address);
            }

            @Override
            public Map<String, Hir.Def> declaredIn(String moduleName) {
                Answer<Map<String, Hir.Def>> defs = db.ask(new ResolvedDeclarations(moduleName));
                return defs.present() ? defs.value() : Map.of();
            }

            @Override
            public Set<String> exposedBy(String moduleName) {
                // `exposing` is written in the source and no pass rewrites it, so which stage this
                // registry reads makes no difference to the answer.
                Set<String> exposed = db.ask(new Front.Exposes(moduleName)).value();
                return exposed == null ? Set.of() : exposed;
            }

            @Override
            public Set<String> moduleNames() {
                Set<String> names = db.ask(new Front.ModuleNames()).value();
                return names == null ? Set.of() : names;
            }
        };
    }

    /**
     * A registry over this compilation, reading each module's declarations as they were written.
     *
     * <p>What {@code Resolve} reads other modules by. A name written there is that module's to
     * resolve, and asking for its resolved form here would be this module waiting on its own.
     */
    public static Registry<Ast.Def> writtenRegistry(Db db) {
        return new Registry<Ast.Def>() {
            @Override
            public Ast.Def declaration(TypeKey address) {
                Answer<Ast.Def> def = db.ask(new Declaration(address));
                return def.present() ? def.value() : null;
            }

            @Override
            public boolean declares(TypeKey address) {
                return declared(db, address);
            }

            @Override
            public Map<String, Ast.Def> declaredIn(String moduleName) {
                Answer<Map<String, Ast.Def>> defs = db.ask(new Declarations(moduleName));
                return defs.present() ? defs.value() : Map.of();
            }

            @Override
            public Set<String> exposedBy(String moduleName) {
                Set<String> exposed = db.ask(new Front.Exposes(moduleName)).value();
                return exposed == null ? Set.of() : exposed;
            }

            @Override
            public Set<String> moduleNames() {
                Set<String> names = db.ask(new Front.ModuleNames()).value();
                return names == null ? Set.of() : names;
            }
        };
    }

    /**
     * A registry over this compilation, reading each module's declarations as they were normalized.
     *
     * <p>What a reader of declarations below the settling is answered from. Every declaration a
     * module writes is here, because normalizing one is declaration-local and asks nothing of the
     * shapes its fields name — so which form a reader gets is not decided by whether a
     * representation could be derived for the declaration it asked about.
     */
    static Registry<Normalized.Def> normalizedRegistry(Db db) {
        return new Registry<Normalized.Def>() {
            @Override
            public Normalized.Def declaration(TypeKey address) {
                Answer<Normalized.Def> def = db.ask(new Shapes.NormalizedDef(address));
                return def.present() ? def.value() : null;
            }

            @Override
            public boolean declares(TypeKey address) {
                return declared(db, address);
            }

            @Override
            public Map<String, Normalized.Def> declaredIn(String moduleName) {
                Answer<Map<String, Normalized.Def>> defs =
                        db.ask(new Shapes.NormalizedDeclarations(moduleName));
                return defs.present() ? defs.value() : Map.of();
            }

            @Override
            public Set<String> exposedBy(String moduleName) {
                Set<String> exposed = db.ask(new Front.Exposes(moduleName)).value();
                return exposed == null ? Set.of() : exposed;
            }

            @Override
            public Set<String> moduleNames() {
                Set<String> names = db.ask(new Front.ModuleNames()).value();
                return names == null ? Set.of() : names;
            }
        };
    }

    /**
     * A registry over this compilation, reading each module's declarations with the representation
     * derived for each.
     *
     * <p>What it hands over is the derived declaration and not the node it was derived from. A table
     * of nodes would say of every declaration below the stage what nothing established of it. Both
     * sources a reader is answered from are at this rung: the compilation's, here, and the
     * language's own vocabulary, which is lifted to the same representation
     * ({@link Declarations.Vocabulary#ofDerived}) rather than left resolved with no derived
     * declaration to give.
     *
     * <p>Short of the declarations a module writes, by exactly the products whose representation
     * could not be derived. Which is why it is not what a declaration is read through:
     * {@link #normalizedRegistry} is, and it is missing none of them.
     */
    static Registry<Derived.Def> derivedRegistry(Db db) {
        return new Registry<Derived.Def>() {
            @Override
            public Derived.Def declaration(TypeKey address) {
                Answer<Derived.Def> def =
                        db.ask(new Shapes.DerivedDef(address));
                return def.present() ? def.value() : null;
            }

            @Override
            public boolean declares(TypeKey address) {
                return declared(db, address);
            }

            @Override
            public Map<String, Derived.Def> declaredIn(String moduleName) {
                Answer<Map<String, Derived.Def>> defs =
                        db.ask(new Shapes.DerivedDeclarations(moduleName));
                return defs.present() ? defs.value() : Map.of();
            }

            @Override
            public Set<String> exposedBy(String moduleName) {
                Set<String> exposed = db.ask(new Front.Exposes(moduleName)).value();
                return exposed == null ? Set.of() : exposed;
            }

            @Override
            public Set<String> moduleNames() {
                Set<String> names = db.ask(new Front.ModuleNames()).value();
                return names == null ? Set.of() : names;
            }
        };
    }

    /**
     * What a module declares, by the name written there — asked once, here, because the names are
     * the same at every stage and every later registry reads them through this.
     *
     * <p>Where the answer is worked out depends on where the module came from, and nothing above
     * has to know which: a source of this compilation is indexed here and a declaration it may not
     * have is reported against the file its author holds; a module off the class path was indexed
     * where it was read back, and one whose declarations could not be indexed never became a module
     * this compilation has. So the two are one question with one answer, and a caller holding the
     * answer cannot tell — which is the point, since a caller that could would be a second place the
     * rule is written.
     *
     * <p>Indexing an artifact here is what this replaces. The declarations came back as source, so
     * they were indexed like source, and a name a published module declared twice was reported to
     * the author of the project importing it — {@code E1011}, under the caret of their own
     * {@code import} line, about a file they do not have and did not write.
     */
    public record Declarations(String name) implements Key<Map<String, Ast.Def>> {
        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Map<String, Ast.Def>> compute(Db db) {
            if (cyclic(db, name)) {
                // What this module declares depends on the module it names, which depends on this
                // one. Reported by InCycle; nothing below here can be answered, and going on would
                // ask a question that is answering itself.
                return Answer.absent();
            }
            Answer<Ast.Module> mine = db.ask(new Front.Exposed(name));
            if (mine.present()) {
                // A declaration the module may not have is reported and left out; the ones it may
                // have are what it declares. So a name written twice does not take every other name
                // in the file with it.
                DeclaredNames.Index<Ast.Def> declared = Registry.indexed(mine.value());
                List<Report> reports = new ArrayList<>();
                for (DeclaredNames.Refusal<Ast.Def> refused : declared.refusals()) {
                    reports.add(Report.of(DeclarationRefusals.reportedAsWritten(refused)));
                }
                return Answer.of(declared.declarations(), reports);
            }
            Front.FromPath.OnThePath fromPath = Front.onThePath(db, name);
            return fromPath == null ? Answer.absent() : Answer.of(fromPath.declarations());
        }
    }

    /**
     * Whether a module takes part in an import cycle — absent, with the error, when it does.
     *
     * <p>{@link Cycles} finds them all in one walk; this is where one module's share of that is
     * reported, so the error lands on the source that closes the cycle like any other.
     */
    public record InCycle(String name) implements Key<Boolean> {
        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Boolean> compute(Db db) {
            Cycles.Of cycles = db.ask(new Cycles()).value();
            Report found = cycles == null ? null : cycles.reported().get(name);
            if (found != null) {
                return Answer.absent(found);
            }
            return cyclic(db, name) ? Answer.absent() : Answer.of(Boolean.FALSE);
        }
    }

    /**
     * One declaration, as the module wrote it.
     *
     * <p>Its own question, so that reading it is a dependency on it and not on everything declared
     * beside it. Whether the work behind it is done for one declaration or for the module is not
     * this key's business: what a reader depends on is the answer, and this answer says what one
     * declaration says.
     */
    public record Declaration(TypeKey named) implements Key<Ast.Def> {
        @Override
        public String module() {
            return named.module();
        }

        @Override
        public Answer<Ast.Def> compute(Db db) {
            Answer<Map<String, Ast.Def>> defs = db.ask(new Declarations(named.module()));
            if (!defs.present()) {
                return Answer.absent();
            }
            Ast.Def def = defs.value().get(named.name());
            return def == null ? Answer.absent() : Answer.of(def);
        }
    }

    /**
     * Whether a module of this compilation declares something at {@code named}.
     *
     * <p>A question of its own because of what its answer is, and {@link HasScope} is the same shape
     * for a module: read off a declaration, whose answer moves whenever the declaration is written
     * over or written somewhere else in its text, and answered as a yes or a no, which does not. A
     * reader that took the declaration to find out whether there was one would be told where it now
     * stands, and so would every reader of that in turn.
     *
     * <p>Not a fact about any representation. Which rung the declarations a reader holds have
     * reached decides what one says, and decides nothing about whether one is there — a product no
     * representation could be derived for is a name this compilation declares all the same. So this
     * is read off the declarations as they were indexed, which is where a name's being declared here
     * was settled, and every rung is answered the same.
     */
    public record CompilationDeclares(TypeKey named) implements Key<Boolean> {
        @Override
        public String module() {
            return named.module();
        }

        @Override
        public Answer<Boolean> compute(Db db) {
            return Answer.of(db.ask(new Declaration(named)).present());
        }
    }

    /**
     * Which form the declaration at {@code named} was written in.
     *
     * <p>Beside {@link CompilationDeclares} and not inside it, for the reason they are two
     * questions: a product rewritten as a sum keeps the presence answer and changes this one, so a
     * reader that only wanted to know there was a declaration is not told about a change it has no
     * use for.
     *
     * <p>Read off the declarations as they were indexed, which is where the form was settled. What
     * the declaration says is worked out further up and takes the names in it resolving with it; the
     * form does not, so a reader asking only which form it is depends on neither. That is what lets
     * this be asked while a declaration's own meaning is being made, which is where asking what it
     * says would be asking for the answer being worked out.
     */
    public record DeclarationKindOf(TypeKey named) implements Key<DeclarationKind> {
        @Override
        public String module() {
            return named.module();
        }

        @Override
        public Answer<DeclarationKind> compute(Db db) {
            Answer<Ast.Def> mine = db.ask(new Declaration(named));
            if (mine.present()) {
                return Answer.of(switch (mine.value()) {
                    case Ast.Data _ -> DeclarationKind.PRODUCT;
                    case Ast.SumData _ -> DeclarationKind.SUM;
                    case Ast.UnitData _ -> DeclarationKind.UNIT;
                });
            }
            // What the language declares, which no module of this compilation wrote and which is
            // indexed where the library is read. Left out, a reader asking the form of a library
            // name would be told nothing declares it — and a rule about how one crosses would go
            // unasked rather than being answered.
            Answer<Stdlib> library = db.ask(new Front.Library());
            Hir.Def declared =
                    library.present() ? library.value().languageDeclaration(named) : null;
            return declared == null ? Answer.absent() : Answer.of(switch (declared) {
                case Hir.Data _ -> DeclarationKind.PRODUCT;
                case Hir.SumData _ -> DeclarationKind.SUM;
                case Hir.UnitData _ -> DeclarationKind.UNIT;
            });
        }
    }

    /**
     * Whether the declaration at {@code named} is written as one value wearing a name.
     *
     * <p>Beside the form and not part of it. A product written over again as a sum changes which
     * form it is and says nothing different about whether it is a newtype — both are false — so a
     * reader that asks only this keeps its answer through an edit that changes only that. Answered
     * together as one four-valued form, every such reader would be worked out again.
     *
     * <p>Settled where the module was indexed, like the form: it is which way the declaration was
     * written, and resolution copies it rather than deciding it.
     */
    public record DeclarationIsNewtype(TypeKey named) implements Key<Boolean> {
        @Override
        public String module() {
            return named.module();
        }

        @Override
        public Answer<Boolean> compute(Db db) {
            Answer<Ast.Def> mine = db.ask(new Declaration(named));
            if (mine.present()) {
                return Answer.of(mine.value() instanceof Ast.Data data && data.newtype());
            }
            // What the language declares, read where the library is, for the reason the form is read
            // there: a reader asking this of a library name is asking about a declaration, and being
            // told nothing declares it is a different answer from being told it is not a newtype.
            Answer<Stdlib> library = db.ask(new Front.Library());
            Hir.Def declared =
                    library.present() ? library.value().languageDeclaration(named) : null;
            return declared == null
                    ? Answer.absent()
                    : Answer.of(declared instanceof Hir.Data data && data.newtype());
        }
    }

    /** The same, with every written name in it resolved. */
    public record ResolvedDeclaration(TypeKey named) implements Key<Hir.Def> {
        @Override
        public String module() {
            return named.module();
        }

        @Override
        public Answer<Hir.Def> compute(Db db) {
            Answer<Map<String, Hir.Def>> defs = db.ask(new ResolvedDeclarations(named.module()));
            if (!defs.present()) {
                return Answer.absent();
            }
            Hir.Def def = defs.value().get(named.name());
            return def == null ? Answer.absent() : Answer.of(def);
        }
    }

    /** The same declarations, with every written name in them resolved. */
    public record ResolvedDeclarations(String name) implements Key<Map<String, Hir.Def>> {
        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Map<String, Hir.Def>> compute(Db db) {
            Answer<Hir.Module> m = db.ask(new Resolved(name));
            return m.present() ? Answer.of(defsOf(m.value())) : Answer.absent();
        }
    }

    /** A module's definitions by name, taking the names as already checked. */
    static Map<String, Hir.Def> defsOf(Hir.Module m) {
        Map<String, Hir.Def> defs = new LinkedHashMap<>();
        for (Hir.Def def : m.defs()) {
            defs.putIfAbsent(def.name(), def);
        }
        return Ordered.map(defs);
    }

    /**
     * What the names a module writes mean here: what each bare name denotes in either namespace,
     * which module each {@code import ... as} alias names, and what an import line could not do.
     *
     * <p>Worked out by {@link Scoping}, against this compilation as a {@link ModuleUniverse}. This
     * is where a compilation reads the result, so this is where a refusal becomes something to tell
     * the author — the assembly itself says nothing to anyone, which is what lets a reader that is
     * only reading a module use the same rules.
     *
     * <p>Every import is validated here and nowhere else — that it names a module this compilation
     * has, that the module exposes what is asked for, that no two imports bring in the same name.
     */
    public record ModuleScope(String name) implements Key<Scoping.Scoped> {

        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Scoping.Scoped> compute(Db db) {
            Scoping.Subject subject = CompilationUniverse.subject(db, name);
            if (subject == null) {
                return Answer.absent();
            }
            Scoping.Scoped scoped = Scoping.of(CompilationUniverse.over(db), subject);
            List<Report> reports = new ArrayList<>();
            for (Scoping.Refusal refusal : scoped.refused()) {
                reports.add(said(refusal));
            }
            return reports.isEmpty() ? Answer.of(scoped) : Answer.of(scoped, reports);
        }
    }

    /**
     * What to tell the author about a name the scope could not hold.
     *
     * <p>A switch over every refusal there is, with nothing to fall through to: a rule added to the
     * assembly is a rule this compilation has to have something to say about, and one that reached
     * here with nothing to say would be a name quietly denoting nothing.
     */
    private static Report said(Scoping.Refusal refusal) {
        return switch (refusal) {
            case Scoping.Refusal.NoSuchModule(Ast.Import imp) ->
                    Report.raised(Diagnostic.say(new ModuleMessage.UnknownModule(imp.module()))
                            .at(imp.pos()).build());
            case Scoping.Refusal.NotExposed(Ast.Import imp, String named) ->
                    Report.raised(Diagnostic
                            .say(new ModuleMessage.TheModuleDoesNotExposeIt(named, imp.module()))
                            .at(imp.pos()).build());
            case Scoping.Refusal.NoSuchName(Ast.Import imp, String named) ->
                    Report.raised(Diagnostic
                            .say(new ModuleMessage.TheModuleDeclaresNoSuchName(named, imp.module()))
                            .at(imp.pos()).build());
            case Scoping.Refusal.AliasTaken(Ast.Import imp, String takenBy) ->
                    Report.raised(Diagnostic
                            .say(new ModuleMessage.TheAliasIsAlreadyTaken(imp.alias(), takenBy))
                            .at(imp.pos())
                            .hint(new ModuleMessage.AnAliasIsANameNothingElseAnswersTo()).build());
            case Scoping.Refusal.BroughtTwice(Ast.Import imp, String named, Ast.Import earlier) ->
                    broughtTwice(named, imp, earlier);
            case Scoping.Refusal.CollidesWithADeclaration(Ast.Import imp, String named) ->
                    Report.raised(Diagnostic.at(imp.pos())
                            .say(new ImportMessage.ImportedNameCollidesWithADeclaration(named))
                            .hint(new ImportMessage.RenameOrQualifyTheCollidingName()).build());
            case Scoping.Refusal.TakesTheLibraryQualifier(Ast.Def def) ->
                    Report.raised(Diagnostic.at(def.pos())
                            .say(new DataMessage.ADataTakesTheStandardLibraryQualifier(def.name()))
                            .build());
            case Scoping.Refusal.ALetAndADataShareASpelling(Ast.FnDef fn) ->
                    Report.raised(Diagnostic.at(fn.pos())
                            .say(new DataMessage.ALetAndADataShareOneSpelling(fn.name())).build());
        };
    }

    /**
     * What the names written in a module mean — the part of its scope that is a value.
     *
     * <p>The cutoff every reader of a scope stops at. A module is assembled once and the assembly
     * holds more than this: the value namespace, a way of asking the modules around it a further
     * question, and what its import lines could not do. A scope is built over none of those and over
     * this, so declaring a behavior — which adds a value name and no type name — comes out here as
     * the answer that was already there, and nothing that reads it runs again.
     *
     * <p>Who reads it is a second question, and not this one's. A scope asks for this the first time
     * it is read rather than to be built ({@link souther.compiler.check.Denoting}), so a reader that
     * reads no meaning has not asked — which is why declaring a type reaches the bodies that write
     * its spelling and the reports read off every name in sight, and no others.
     */
    public record Meanings(String name) implements Key<Scoping.Meanings> {
        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Scoping.Meanings> compute(Db db) {
            Answer<Scoping.Scoped> scoped = db.ask(new ModuleScope(name));
            return scoped.present() ? Answer.of(scoped.value().meanings()) : Answer.absent();
        }
    }

    /** The library this compilation resolves against, asked of the store rather than of the
     *  process: what a name means is a question about this compilation. */
    private static Stdlib library(Db db) {
        return db.ask(new Front.Library()).value();
    }

    /**
     * What names mean in a module, over the declaration world {@code registry} reads.
     *
     * <p>Built where it is used and never kept. What comes back reads declarations by asking this
     * store, so it is a way of asking rather than an answer: two of them are the same when the
     * store is, which says where they came from and not what they say. Kept as an answer, it would
     * be an answer that never equals the one it replaces, and everything that read it would run
     * again for as long as the compilation lived. Built here, the reads it makes are recorded
     * against whichever question was being answered when it made them — which is one declaration at
     * a time, and is the finer dependency this hands out.
     */
    private static <S> Answer<S> symbols(Db db, String name,
                                         java.util.function.BiFunction<Denoting, Stdlib, S> over) {
        if (!db.ask(new HasScope(name)).value()) {
            return Answer.absent();
        }
        return Answer.of(over.apply(asked(db, name), library(db)));
    }

    /**
     * Whether this compilation can assemble a scope for {@code name} — which is the only thing a
     * reader of one has to be told before it reads it.
     *
     * <p>A question of its own because of what its answer is. Read off {@link ModuleScope}, whose
     * answer moves whenever anything in the module is edited, and answered as a yes or a no, which
     * does not: a reader that took the assembly to find out whether there was one would be told
     * every edit to the module, and every reader of it in turn. What is left to ask for is what the
     * names mean, and {@link Denoting} is where that is asked.
     */
    public record HasScope(String name) implements Key<Boolean> {
        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Boolean> compute(Db db) {
            return Answer.of(db.ask(new ModuleScope(name)).present());
        }
    }

    /**
     * What a module's names mean, asked of this store as a scope reads them.
     *
     * <p>The other half of what {@link #symbols} hands out, and the half issue #835 is about.
     * Handing over the table meant asking {@link Meanings} to build a scope, so every reader of a
     * scope depended on every name in the module before reading one of them — and what a body check
     * reads of them is almost always nothing, because the names it is checked over were resolved a
     * representation earlier and it reads the declarations they denote one at a time. Asked here, a
     * body that reads no meaning depends on none of them, and the report that reads every name in
     * sight depends on every name in sight, which is what it is about.
     * {@code IncrementalCompilationTest} holds the pair.
     *
     * <p>Built where it is used and never kept, for the reason {@link Key} gives: it reads this
     * store, so two of them are the same when the store is, which says where they came from and not
     * what they say. The reads it makes land on whichever question was being answered when it made
     * them.
     *
     * <p>The store is asked every time and not once. What is kept is the reading built over the
     * answer, and it is kept only while the answer is the one it was built over — so a scope read
     * after an edit answers from what the module means now, and one read many times while a single
     * question is being answered builds the reading once. Fetched once instead, this would hold a
     * snapshot for as long as it lived, and whether that could be read after an edit would be a
     * fact about who kept a scope rather than one this can be sure of on its own.
     *
     * <p>Where this compilation has no such module, every spelling means nothing — the answer
     * {@link Denoting#NONE} gives. Nothing reading a scope learns that a compilation can be missing
     * a module, and nothing here has to hold an absence it would have to decide what to do with:
     * {@link HasScope} is what a reader is told, before it has a scope at all.
     */
    private static Denoting asked(Db db, String module) {
        return new Denoting() {
            private Scoping.Meanings over;
            private Denoting reading;

            private Denoting read() {
                Answer<Scoping.Meanings> meanings = db.ask(new Meanings(module));
                Scoping.Meanings now = meanings.present() ? meanings.value() : null;
                // The answer itself and not what it means: this is asking whether the reading in
                // hand was built over what the store just handed over, which is a question about
                // the two being the one object.
                if (reading == null || now != over) {
                    over = now;
                    reading = now == null ? Denoting.NONE : now.denoting();
                }
                return reading;
            }

            @Override
            public Denotation of(String spelling) {
                return read().of(spelling);
            }

            @Override
            public Set<String> spellings() {
                return read().spellings();
            }

            @Override
            public String moduleOfAlias(String alias) {
                return read().moduleOfAlias(alias);
            }

            @Override
            public Set<String> aliases() {
                return read().aliases();
            }
        };
    }

    /** What names mean in a module over the declarations as resolution left them — what
     * {@link Resolved} is resolved against. */
    static Answer<ResolvedSymbols> resolvedSymbols(Db db, String name) {
        return symbols(db, name, (names, stdlib) -> ResolvedSymbols
                .over(name, resolvedRegistry(db), names, stdlib));
    }

    /**
     * The same over the derived declarations — what everything below the check reads.
     *
     * <p>Public where its two neighbours are not, because {@link souther.compiler.program} takes
     * its snapshot of what a module declares through it. What is widened is this entry and nothing
     * under it: the registry it reads through stays this package's.
     *
     * <p>A way in and not a key. What comes back holds a registry that asks {@code db} for each
     * declaration, so it is how to reach an answer rather than an answer — it has no equality to be
     * memoised by, and a caller that kept one would be holding the compilation it was made from. It
     * is built where it is used and dropped there.
     */
    public static Answer<DerivedSymbols> derivedSymbols(
            Db db, String name) {
        return symbols(db, name, (names, stdlib) -> DerivedSymbols
                .over(name, derivedRegistry(db), normalizedRegistry(db), resolvedRegistry(db),
                        names, stdlib));
    }

    /** The same, over the declarations as they were written — what {@code Resolve} resolves
     * against. */
    static Answer<SyntaxSymbols> writtenSymbols(Db db, String name) {
        if (!db.ask(new HasScope(name)).value()) {
            return Answer.absent();
        }
        return Answer.of(SyntaxSymbols.of(name, writtenRegistry(db), asked(db, name),
                library(db)));
    }

    /**
     * Every bare spelling that reaches a definition in a module, and the definition it reaches —
     * this module's own plus the ones it imported.
     *
     * <p>The one question a scope answers that needs both of its halves, asked here so that what a
     * reader outside the compile gets is the answer and not the way of reading it. What a scope
     * hands out reads declarations by asking this store, and a reader holding one past the question
     * it was built for makes reads nothing records; the answer is a map of declarations, which is a
     * value, so there is nothing left to hold.
     */
    public record Reachable(String name) implements Key<Map<String, Hir.Def>> {
        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Map<String, Hir.Def>> compute(Db db) {
            Answer<ResolvedSymbols> symbols = resolvedSymbols(db, name);
            return symbols.present() ? Answer.of(symbols.value().reachable()) : Answer.absent();
        }
    }

    /**
     * The module with every written type name resolved to the declaration it denotes. A name that
     * denotes nothing is reported here, so nothing downstream ever reads an unresolved one.
     */
    public record Resolution(String name) implements Key<Resolve.Resolution> {
        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Resolve.Resolution> compute(Db db) {
            Answer<Ast.Module> available = db.ask(new Front.Available(name));
            if (!available.present()) {
                return Answer.absent();
            }
            // Resolution reads other modules' declarations as they were written, not as they will
            // be resolved: a name written there is that module's to resolve, and asking for its
            // resolved form here would be this module waiting on its own.
            Answer<Scoping.Scoped> scoped = db.ask(new ModuleScope(name));
            if (!scoped.present()) {
                return Answer.absent();
            }
            Resolve.Resolution resolution;
            try {
                resolution = Resolve.resolving(available.value(),
                        scoped.value().meanings().writtenSymbols(writtenRegistry(db),
                                library(db)),
                        scoped.value().values());
            } catch (CompileException e) {
                return Answer.absent(e);
            }
            // A name that denotes nothing is reported here and the tree carries on with the error
            // type in its place. The answer is present, so an editor can still say what the names
            // around the mistake mean; what must not happen — emitting a module with a hole in it —
            // is stopped where the module is checked.
            List<Report> reports = new ArrayList<>();
            for (CompileException unresolved : resolution.unresolved()) {
                reports.addAll(Report.of(unresolved));
            }
            return Answer.of(resolution, reports);
        }
    }

    /**
     * What resolution worked out about the names a module writes.
     *
     * <p>This is what a reader outside the compiler is answered from, and it is asked for on its
     * own so that such a reader never holds the tree. The two are not the same artifact and do not
     * come and go together: a module the compiler will not build on is still a module an editor has
     * to say things about, and every name that did resolve in it is a name it can be told about.
     */
    public record Facts(String name) implements Key<Resolve.ResolutionIndex> {
        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Resolve.ResolutionIndex> compute(Db db) {
            Answer<Resolve.Resolution> resolution = db.ask(new Resolution(name));
            return resolution.present() ? Answer.of(resolution.value().index()) : Answer.absent();
        }
    }

    /**
     * One declaration as its meaning was settled, or absent where it was not settled.
     *
     * <p>The unit a name is answered for is the declaration. A definition is here when every name
     * written in it was answered and every declaration it reaches has one of these too; otherwise
     * there is nothing to hand a later pass, and it is not built rather than built around what is
     * missing.
     */
    public record Definition(TypeKey named) implements Key<Hir.Def> {
        @Override
        public String module() {
            return named.module();
        }

        @Override
        public Answer<Hir.Def> compute(Db db) {
            Answer<Resolve.Resolution> resolution = db.ask(new Resolution(named.module()));
            Answer<Set<String>> unbuilt = db.ask(new Unbuilt(named.module()));
            if (!resolution.present() || !unbuilt.present()
                    || unbuilt.value().contains(named.name())) {
                return Answer.absent();
            }
            for (Hir.Def def : resolution.value().module().defs()) {
                if (def.name().equals(named.name())) {
                    return Answer.of(def);
                }
            }
            return Answer.absent();
        }
    }

    /**
     * The declarations of a module that have no meaning to give.
     *
     * <p>Two ways in, and both are about what a declaration is made of rather than about what was
     * reported. A name written in it was not answered, so what it is made of is not there; or what
     * it reaches is one of these, so what that is made of is not there either. A module that will
     * not be emitted for some other reason has declarations that mean what they say, and they are
     * not in here.
     *
     * <p>Asked of a whole module at once because the reaching is a relation among its own
     * declarations, and following it one declaration at a time would ask a question of itself where
     * two of them are made of each other. Across modules there is no such loop to close: an import
     * cycle is settled before this, so a module's declarations reach another's and stop.
     *
     * <p>Not part of what a reader outside this file asks. Whether a declaration has a meaning is
     * {@link Definition}'s to answer and is answered by handing one over or not; a reader that could
     * ask which ones have none would be a reader deciding for itself what to do about it, which is
     * how the question of what a name means came to be answered in several places at once.
     */
    record Unbuilt(String name) implements Key<Set<String>> {
        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Set<String>> compute(Db db) {
            if (cyclic(db, name)) {
                return Answer.absent();
            }
            Answer<Resolve.Resolution> resolution = db.ask(new Resolution(name));
            if (!resolution.present()) {
                return Answer.absent();
            }
            Map<String, Resolve.OfDeclaration> declarations = resolution.value().declarations();
            Set<String> unbuilt = new LinkedHashSet<>();
            Map<String, Set<String>> elsewhere = new LinkedHashMap<>();
            for (Map.Entry<String, Resolve.OfDeclaration> declared : declarations.entrySet()) {
                if (!declared.getValue().answered()) {
                    unbuilt.add(declared.getKey());
                    continue;
                }
                for (TypeSymbol reached : declared.getValue().reaches()) {
                    // What the language gives is declared by no module, so there is no module to
                    // ask whether it was built there.
                    if (!(reached instanceof TypeSymbol.AtModule at) || at.module().equals(name)) {
                        continue;
                    }
                    Set<String> there = elsewhere.computeIfAbsent(at.module(),
                            m -> unbuiltIn(db, m));
                    if (there.contains(at.name())) {
                        unbuilt.add(declared.getKey());
                        break;
                    }
                }
            }
            // What reaches one of these has nothing to stand on either, and so has what reaches
            // that. Held to the declarations of this module, which is where the relation is.
            boolean more = true;
            while (more) {
                more = false;
                for (Map.Entry<String, Resolve.OfDeclaration> declared : declarations.entrySet()) {
                    if (unbuilt.contains(declared.getKey())) {
                        continue;
                    }
                    for (TypeSymbol reached : declared.getValue().reaches()) {
                        if (reached instanceof TypeSymbol.AtModule at && at.module().equals(name)
                                && unbuilt.contains(at.name())) {
                            unbuilt.add(declared.getKey());
                            more = true;
                            break;
                        }
                    }
                }
            }
            return Answer.of(Set.copyOf(unbuilt));
        }

        /** What another module could not build, or nothing where it could not be read — what is
         * wrong there is reported there, and a name reaching into it is answered by its absence. */
        private static Set<String> unbuiltIn(Db db, String module) {
            Answer<Set<String>> there = db.ask(new Unbuilt(module));
            return there.present() ? there.value() : Set.of();
        }
    }

    /**
     * Whether everything the compiler worked out about a module's names came out.
     *
     * <p>One question, asked in one place, so that whether a module may be emitted does not become a
     * list of conditions appended to over time. Each of these has already said what was wrong where
     * it found it; this only asks whether any of them did.
     *
     * <p>It is transitive. A module built against one that was rejected is built against declarations
     * nothing will emit, so it cannot be emitted either — its classes would name a class that is not
     * there, and its examples would fail for a reason that is not its own. An import that could not be
     * followed counts the same way, whether or not anything was reported here about it. An import cycle
     * is settled before this recurses, so following imports terminates.
     */
    public record Sound(String name) implements Key<Boolean> {
        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Boolean> compute(Db db) {
            List<Answer<?>> asked = List.of(
                    db.ask(new Front.Exposed(name)),
                    db.ask(new Front.ShadowsPath(name)),
                    db.ask(new InCycle(name)),
                    db.ask(new Declarations(name)),
                    db.ask(new ModuleScope(name)),
                    db.ask(new Resolution(name)));
            for (Answer<?> answer : asked) {
                if (answer.hasError()) {
                    return Answer.of(Boolean.FALSE);
                }
            }
            // Whether anything was reported here is not the whole question. A name that denotes
            // nothing because the module it would have come from cannot be read is reported on that
            // module, and leaves a hole here that nothing said anything about — so the names are
            // asked as well as the reports.
            if (Boolean.TRUE.equals(db.ask(new Nameless(name)).value())) {
                return Answer.of(Boolean.FALSE);
            }
            Ast.Module m = db.ask(new Front.Available(name)).value();
            if (m != null) {
                for (Ast.Import imp : m.imports()) {
                    // An import that could not be followed at all — the module is not here, or the
                    // caller is holding its file back — leaves the names it was to bring denoting
                    // nothing, whether or not anything was reported here to say so.
                    if (!db.ask(new Front.Available(imp.module())).present()
                            || Boolean.FALSE.equals(db.ask(new Sound(imp.module())).value())) {
                        return Answer.of(Boolean.FALSE);
                    }
                }
            }
            return Answer.of(Boolean.TRUE);
        }
    }

    /**
     * Whether a module writes a name in the value namespace that denotes nothing.
     *
     * <p>Asked because a report is not the only way a hole gets there. A stage naming a module this
     * compilation has and cannot read is reported on that module — the author of this one has
     * nothing to fix — and this module is left with a composition that has no meaning, which nothing
     * here said. Emitting it would emit a call to a behavior that does not exist.
     */
    public record Nameless(String name) implements Key<Boolean> {
        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Boolean> compute(Db db) {
            Hir.Module m = db.ask(new Resolved(name)).value();
            if (m == null) {
                return Answer.of(Boolean.FALSE);   // there is no module here to have a hole in
            }
            for (Hir.BehaviorDef b : m.behaviors()) {
                List<Hir.Var> named = switch (b) {
                    case Hir.PipeBehavior pipe -> pipe.stages();
                    case Hir.SpecBehavior spec -> spec.dependsOn();
                };
                for (Hir.Var ref : named) {
                    if (ref.unresolved()) {
                        return Answer.of(Boolean.TRUE);
                    }
                }
            }
            return Answer.of(Boolean.FALSE);
        }
    }

    /**
     * A name on an import list that the module never writes bare — reported at the name, as a
     * warning.
     *
     * <p>The question is the reverse of {@link Imports}'. That one asks what each written name means
     * and answers it against the exporting module; this asks, of the importing module's own body,
     * whether anything said the name at all. Nothing else asks it, which is why an import list could
     * claim a vocabulary the module does not speak and no one was told.
     *
     * <p>Being written bare is the whole test, and it is narrower than being mentioned. What another
     * module declares is reachable qualified whether or not it is imported (spec §modules), so
     * {@code Stock.Sku} is a use of the declaration and not of the import: take {@code Sku} off the
     * list and that line still compiles. An entry earns its place by being the spelling something
     * below actually used, which is why a use is matched on what was written as well as on what it
     * denotes.
     */
    public record UnusedImports(String name) implements Key<Boolean> {

        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Boolean> compute(Db db) {
            // Said only of a module whose names all came out. A name that denotes nothing may be the
            // misspelt use of the very import in question, and "this import is unused" is the wrong
            // thing to tell someone whose mistake is a typo four lines down. Sound is where that
            // judgement already lives — it asks the imports, the resolution and the bound stages
            // together, and it asks nothing about types, rows or coverage, so a failing example does
            // not silence this.
            if (!Boolean.TRUE.equals(db.ask(new Sound(name)).value())) {
                return Answer.of(true);
            }
            List<Ast.Import> written = writtenImports(db, name);
            if (written.isEmpty()) {
                return Answer.of(true);
            }
            Set<Use> used = usesIn(db, name);
            if (used == null) {
                return Answer.of(true);
            }
            List<Report> reports = new ArrayList<>();
            for (Ast.Import imp : written) {
                for (Ast.ImportedName imported : imp.importedNames()) {
                    // A name with no position was not written on any list — it was synthesized from
                    // a qualified reference, and there is nothing for anyone to delete.
                    if (imported.pos() == null
                            || used.contains(new Use(imported.text(), imp.module(), imported.text()))) {
                        continue;
                    }
                    reports.add(Report.of(Diagnostic.say(new ModuleMessage.ImportedButNeverUsedUnderThisName(imported.written().quoted()))
                            .at(imported.written().reportedAt())
                            
                            .hint(new ModuleMessage.TakeItOffTheImportList())
                            .build()));
                }
            }
            return Answer.of(true, reports);
        }

        /**
         * The import lines as the source wrote them.
         *
         * <p>Read off the parsed source rather than off {@link Front.Available}, because the module
         * that arrives from there has had its standard-library import lines taken out
         * ({@link souther.compiler.check.Exposing#check}) — an unused
         * {@code import List ( fold )} would be invisible to a check that read the module. An
         * attached {@code examples for} file writes no imports, so the declaring source's lines are
         * all of them.
         */
        private static List<Ast.Import> writtenImports(Db db, String module) {
            Front.Layout.Of layout = db.ask(new Front.Layout()).value();
            SourceId id = layout == null ? null : layout.idOfModule().get(module);
            if (id == null) {
                return List.of();
            }
            Answer<souther.compiler.frontend.CstFrontend.Parsed> parsed = db.ask(new Front.Parsed(id));
            return parsed.present() ? parsed.value().module().imports() : List.of();
        }
    }

    /**
     * One name written in a module and the declaration it turned out to mean: the spelling, and the
     * qualifier and name a reference would have reached that declaration by written out in full.
     *
     * <p>The qualifier and not the declaring module, because that is what an import line writes and
     * an import line is what these are compared against. For a user module the two are the same
     * name; for the standard library they are not — {@code souther.list} declares {@code foldFrom}
     * and a line writes {@code import List ( foldFrom )} — and it is the alias every side of this
     * comparison holds.
     *
     * <p>The spelling is half of it because an import list entry is a claim about a spelling. Two
     * uses of one declaration — {@code Sku} and {@code Stock.Sku} — are the same declaration and
     * different claims, and only the first is what an import list entry buys.
     */
    private record Use(String written, String qualifier, String name) {}

    /** Every name {@code module} writes, paired with what it denotes, or null when the module could
     * not be read. */
    private static Set<Use> usesIn(Db db, String module) {
        Answer<Resolve.ResolutionIndex> facts = db.ask(new Facts(module));
        if (!facts.present()) {
            return null;
        }
        Set<Use> used = new HashSet<>();
        for (Resolve.TypeUse d : facts.value().types()) {
            // A name the language gives is in scope everywhere and comes in on no import line, so
            // writing one is not a use of anything a module brought in.
            if (d.denotes() instanceof TypeSymbol.AtModule at) {
                used.add(new Use(d.written().canonical(), at.module(), at.name()));
            }
        }
        for (Resolve.ValueUse v : facts.value().values()) {
            switch (v.denotes()) {
                case ValueName.Behavior b ->
                        used.add(new Use(v.written().canonical(), b.module(), b.name()));
                case ValueName.Helper h ->
                        used.add(new Use(v.written().canonical(), h.module(), h.name()));
                // `List.map` and a bare `map` an import brought in both denote the same library
                // function. Its qualifier is the alias the library publishes it under, asked of the
                // name — which holds it — rather than taken back out of the two rendered together.
                // A namespace applied is not a member of anything and no list entry names one.
                case ValueName.Stdlib.Operation s ->
                        used.add(new Use(v.written().canonical(), s.alias(), s.name()));
                default -> { }   // a local, a builtin, a type used as a value (recorded as a type)
            }
        }
        // A `>->` stage and a `depends on` name a behavior, and resolution answers both like any
        // other name in the value namespace — so they are in the record above, and reading the tree
        // for them again would be a second place that says which import is used.
        return used;
    }

    /**
     * The behaviors a module reaches by naming them through their module, as resolution answered
     * them.
     *
     * <p>A projection, so a reader wanting these is not put behind everything else resolution
     * worked out. What they are used for is what a composition borrows — a signature, an injected
     * field — and none of that changes when a body elsewhere does.
     */
    public record QualifiedBehaviors(String name) implements Key<List<Resolve.QualifiedUse>> {
        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<List<Resolve.QualifiedUse>> compute(Db db) {
            Answer<Resolve.Resolution> resolution = db.ask(new Resolution(name));
            return resolution.present() ? Answer.of(List.copyOf(resolution.value().qualified()))
                    : Answer.absent();
        }
    }

    /** The resolved module — {@link Resolution} without the record of how it got there. */
    public record Resolved(String name) implements Key<Hir.Module> {
        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<Hir.Module> compute(Db db) {
            Answer<Resolve.Resolution> resolution = db.ask(new Resolution(name));
            return resolution.present() ? Answer.of(resolution.value().module()) : Answer.absent();
        }
    }

    /**
     * What this module's {@code fake} blocks declare: which behavior each names, and how many name
     * one.
     *
     * <p>Asked of the resolved module, which is as early as it can be asked and as late as it needs
     * to be. What it takes is that the module has every block — its own source's and every attached
     * {@code examples for} file's, which are joined before resolution — and that each block's
     * target has been read against the names in scope. Nothing else about the module bears on it: a
     * block naming a behavior twice is written twice whether or not a representation was derived
     * for a declaration elsewhere, or a body desugared. Asked further down, that is what would
     * decide whether the refusal is said at all.
     */
    public record FakeTables(String name) implements Key<souther.compiler.check.FakeTables> {
        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<souther.compiler.check.FakeTables> compute(Db db) {
            Answer<Hir.Module> resolved = db.ask(new Resolved(name));
            return resolved.present()
                    ? Answer.of(souther.compiler.check.FakeTables.classify(resolved.value()))
                    : Answer.absent();
        }
    }

    /**
     * What {@code id} is told about the blocks it writes that stand in for a behavior another block
     * of the module also stands in for.
     *
     * <p>Counted over the module and said in the file: a module's blocks are its own source's and
     * its attached files', and a report is quoted from the file the block is written in. Every
     * block naming the behavior is reported, none of them marked as the one to keep — the blocks
     * are what they are whichever order the compile was handed its files in, so which is the one to
     * write differently is not the language's to answer.
     *
     * <p>Asked of the classification and of nothing further down. What is wrong is wrong about what
     * was written, and a module that failed to derive a representation or desugar a body has the
     * same two blocks in it.
     */
    public record StandInBlocks(SourceId id) implements Key<Boolean> {
        @Override
        public SourceId sourceId() {
            return id;
        }

        @Override
        public Answer<Boolean> compute(Db db) {
            String module = db.ask(new Front.ModuleOf(id)).value();
            if (module == null) {
                return Answer.of(Boolean.TRUE);   // said where the source is read
            }
            Answer<souther.compiler.check.FakeTables> declared = db.ask(new FakeTables(module));
            if (!declared.present()) {
                return Answer.of(Boolean.TRUE);
            }
            List<Report> reports = new ArrayList<>();
            for (souther.compiler.check.FakeTables.Declaration.Conflict conflict
                    : declared.value().conflicts()) {
                // The behavior, as this module writes it, and one spelling for every block of the
                // conflict. Each block's own target says how that block was typed, and two blocks
                // reaching one behavior may be typed differently — so a report naming what stands
                // beside the fact would say two names for one refusal, and tell each author to
                // merge blocks under a spelling only theirs is written in.
                String named = Requirements.writtenIn(module, conflict.behavior());
                for (souther.compiler.check.FakeTables.Occurrence.Resolved block
                        : conflict.tables()) {
                    Hir.Fake wrote = block.read();
                    if (!wrote.pos().isIn(id)) {
                        continue;   // written in another file, and reported by that file's own key
                    }
                    Diagnostic.Builder said =
                            Diagnostic.at(ExampleStatements.marked(wrote))
                                    .say(new ExampleMessage
                                            .MoreThanOneFakeStandsInForOneBehavior(named));
                    // Where the others are, so the blocks read as one refusal rather than as one
                    // report each. None of them is marked as the one to keep.
                    for (souther.compiler.check.FakeTables.Occurrence.Resolved other
                            : conflict.tables()) {
                        if (other != block) {
                            said.secondary(ExampleStatements.marked(other.read()),
                                    new ExampleMessage.AnotherFakeStandsInForItHere(named));
                        }
                    }
                    reports.add(Report.of(said
                            .hint(new ExampleMessage.WriteTheRowsAsOneFake(named))
                            .build()));
                }
            }
            return reports.isEmpty() ? Answer.of(Boolean.TRUE) : Answer.of(Boolean.TRUE, reports);
        }
    }

    /**
     * The module a question about a place is a question about, or null when this compilation does
     * not have the file — including a question that names no file at all, which names no place and
     * so has no module to be asked of.
     */
    private static String moduleAt(Db db, SourcePos at) {
        SourceId file = keyedOn(at);
        return file == null ? null : db.ask(new Front.ModuleOf(file)).value();
    }

    /**
     * The source a question about {@code at} is keyed on, or none.
     *
     * <p>None for a text this compilation has no name for, and for a position that is inside a module
     * this compile holds no file of. Neither is a file whose edit could change the answer, which is
     * what a key's source is for — and neither of them is a statement about whether a reader could be
     * sent there, which is {@link souther.compiler.diag.DiagnosticPlace}'s to make.
     */
    private static SourceId keyedOn(SourcePos at) {
        return at != null
                && at.quotedFrom() instanceof QuotedFrom.ASourceThisCompileHolds(SourceId file)
                ? file : null;
    }

    /**
     * What the name written at {@code at} denotes, or absent when nothing there is a name of a
     * declared type.
     *
     * <p>This is what an editor is asking when the cursor is on an identifier. It reads the answers
     * the resolve pass already gave, so a qualified reference names the module it names and not
     * whatever this module happens to declare by the same spelling.
     *
     * <p>Asked about a place, not about a module and a place. Which module answers is the file's to
     * settle — an attached {@code examples for} file declares none and is part of one all the same,
     * and a caller that had to name the module first was a caller that could name the wrong one.
     */
    public record DenotedAt(SourcePos at) implements Key<Resolve.TypeUse> {
        @Override
        public SourceId sourceId() {
            return keyedOn(at);
        }

        @Override
        public Answer<Resolve.TypeUse> compute(Db db) {
            String name = moduleAt(db, at);
            if (name == null) {
                return Answer.absent();
            }
            Answer<Resolve.ResolutionIndex> facts = db.ask(new Facts(name));
            if (!facts.present()) {
                return Answer.absent();
            }
            Resolve.TypeUse innermost = null;
            for (Resolve.TypeUse d : facts.value().types()) {
                if (!spans(d.written(), at)) {
                    continue;
                }
                // A container writes its element's name inside its own span, so the one written
                // inside the other is the one the cursor is actually on.
                if (innermost == null || d.written().within(innermost.written())) {
                    innermost = d;
                }
            }
            return innermost == null ? Answer.absent() : Answer.of(innermost);
        }
    }

    /**
     * The type the cursor is on at {@code at}: the one a name there denotes, or — when the cursor is
     * on a declaration's own name — that declaration.
     *
     * <p>One question, so an editor's go-to-definition, find-references and rename all agree about
     * what the cursor is on. They used to each decide for themselves, by spelling.
     */
    public record TypeAt(SourcePos at) implements Key<TypeSymbol> {
        @Override
        public SourceId sourceId() {
            return keyedOn(at);
        }

        @Override
        public Answer<TypeSymbol> compute(Db db) {
            String name = moduleAt(db, at);
            if (name == null) {
                return Answer.absent();
            }
            Answer<Map<String, Ast.Def>> defs = db.ask(new Declarations(name));
            if (defs.present()) {
                for (Ast.Def def : defs.value().values()) {
                    if (spans(def.written(), at)) {
                        // Asked of the declaration world this came out of, rather than made from the
                        // address it answers to.
                        return Answer.of(writtenRegistry(db).identify(def.declaredKey()));
                    }
                }
            }
            Resolve.TypeUse denoted = db.ask(new DenotedAt(at)).value();
            return denoted == null ? Answer.absent() : Answer.of(denoted.denotes());
        }
    }

    /** Every place a module names {@code denoted}, wherever it was declared. */
    public record UsesOf(String name, TypeSymbol denoted) implements Key<List<Resolve.TypeUse>> {
        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<List<Resolve.TypeUse>> compute(Db db) {
            Answer<Resolve.ResolutionIndex> facts = db.ask(new Facts(name));
            if (!facts.present()) {
                return Answer.of(List.of());
            }
            List<Resolve.TypeUse> uses = new ArrayList<>();
            for (Resolve.TypeUse d : facts.value().types()) {
                if (denoted.equals(d.denotes())) {
                    uses.add(d);
                }
            }
            return Answer.of(List.copyOf(uses));
        }
    }

    /**
     * What the name used as a value at {@code at} denotes, or absent when nothing there is one.
     *
     * <p>What {@link DenotedAt} answers for a type. An editor asking about a name in a body reads
     * the answer resolution already gave, so a binding is the binding it is and not whatever else
     * in the module happens to be spelled the same.
     */
    public record ValueDenotedAt(SourcePos at) implements Key<Resolve.ValueUse> {
        @Override
        public SourceId sourceId() {
            return keyedOn(at);
        }

        @Override
        public Answer<Resolve.ValueUse> compute(Db db) {
            String name = moduleAt(db, at);
            if (name == null) {
                return Answer.absent();
            }
            Answer<Resolve.ResolutionIndex> facts = db.ask(new Facts(name));
            if (!facts.present()) {
                return Answer.absent();
            }
            for (Resolve.ValueUse use : facts.value().values()) {
                if (spans(use.written(), at)) {
                    return Answer.of(use);
                }
            }
            return Answer.absent();
        }
    }

    /**
     * Every place a module names {@code denoted} as a value, wherever it was declared.
     *
     * <p>{@link UsesOf} for the value namespace. A local is answered here as readily as a top-level
     * name: what a use denotes is a binding and not a spelling, so the uses of one {@code let}'s
     * {@code x} are not the uses of another's.
     */
    public record ValueUsesOf(String name, ValueName denoted) implements Key<List<Resolve.ValueUse>> {
        @Override
        public String module() {
            return name;
        }

        @Override
        public Answer<List<Resolve.ValueUse>> compute(Db db) {
            Answer<Resolve.ResolutionIndex> facts = db.ask(new Facts(name));
            if (!facts.present()) {
                return Answer.of(List.of());
            }
            List<Resolve.ValueUse> uses = new ArrayList<>();
            for (Resolve.ValueUse use : facts.value().values()) {
                if (denoted.equals(use.denotes())) {
                    uses.add(use);
                }
            }
            return Answer.of(List.copyOf(uses));
        }
    }

    /**
     * The value the cursor is on at {@code at}: the one a name there denotes, or — when the cursor
     * is on a binding — the binding itself.
     *
     * <p>{@link TypeAt} for the value namespace, and for the same reason: a reader asking about a
     * binding is asking from one end or the other, and the two ends are one question. Answering
     * only where a name is read would put a local within reach from its uses and out of reach from
     * the {@code let} that binds it.
     */
    public record ValueAt(SourcePos at) implements Key<ValueName> {
        @Override
        public SourceId sourceId() {
            return keyedOn(at);
        }

        @Override
        public Answer<ValueName> compute(Db db) {
            String name = moduleAt(db, at);
            if (name == null) {
                return Answer.absent();
            }
            Answer<Resolve.ResolutionIndex> facts = db.ask(new Facts(name));
            if (!facts.present()) {
                return Answer.absent();
            }
            Map<BindingId, Resolve.BoundName> binders = facts.value().binders();
            for (Map.Entry<BindingId, Resolve.BoundName> bound : binders.entrySet()) {
                Resolve.BoundName written = bound.getValue();
                if (answerable(bound.getKey(), binders)
                        && spans(written.written(), at)) {
                    ValueName local =
                            new ValueName.Local(written.written().canonical(), bound.getKey());
                    return Answer.of(local);
                }
            }
            Resolve.ValueUse use = db.ask(new ValueDenotedAt(at)).value();
            if (use == null) {
                return Answer.absent();
            }
            if (use.denotes() instanceof ValueName.Local local
                    && !answerable(local.id(), binders)) {
                return Answer.absent();
            }
            return Answer.of(use.denotes());
        }

        /**
         * Whether a reader can be told about this binding at all.
         *
         * <p>Two things have to hold, and both are about what the resolve pass read rather than
         * about what an editor would like. The binding's name has to be one the author wrote —
         * absent from {@code binders} means a desugaring invented it, and a name nobody wrote is
         * under no cursor and cannot be renamed. And it has to be a binding whose uses this pass
         * records: a field's are not names in scope but reads resolved by the type of what they are
         * read from, and only the ones inside an {@code invariant} come through here. A field
         * answered from what is written here would be renamed at its declaration and nowhere else.
         */
        private static boolean answerable(BindingId id, Map<BindingId, Resolve.BoundName> binders) {
            return binders.containsKey(id) && !(id.owner() instanceof BindingOwner.OfFields);
        }
    }

    /**
     * Every place the name of what {@code denoted} names is written as a declaration.
     *
     * <p>More than one, because a behavior is declared twice: the {@code behavior} line says what it
     * is and the {@code let} line says what it does, and both write the name. A reader sent to the
     * declaration wants the first of them; a rename has to rewrite all of them, or the module goes
     * on naming something that is no longer there.
     */
    public record ValueDeclarationsOf(ValueName denoted) implements Key<List<WrittenName>> {
        @Override
        public String module() {
            return switch (denoted) {
                case ValueName.Helper h -> h.module();
                case ValueName.Behavior b -> b.module();
                case ValueName.Local l -> l.id().owner().module();
                case ValueName.Stdlib _, ValueName.OfType _, ValueName.Builtin _ -> null;
            };
        }

        @Override
        public Answer<List<WrittenName>> compute(Db db) {
            // a binding is not a position, so where it was written is asked of the pass that
            // answered it rather than read off the name
            if (denoted instanceof ValueName.Local local) {
                Answer<Resolve.ResolutionIndex> facts =
                        db.ask(new Facts(local.id().owner().module()));
                if (!facts.present()) {
                    return Answer.absent();
                }
                Resolve.BoundName binder = facts.value().binders().get(local.id());
                return binder == null ? Answer.absent() : Answer.of(List.of(binder.written()));
            }
            String in = module();
            if (in == null) {
                return Answer.absent();   // the library and the language declare their own names
            }
            Ast.Module m = db.ask(new Front.Available(in)).value();
            if (m == null) {
                return Answer.absent();
            }
            List<WrittenName> written = new ArrayList<>();
            for (Ast.BehaviorDef b : m.behaviors()) {
                if (b.name().equals(denoted.name()) && b.written().authored()) {
                    written.add(b.written());
                }
            }
            for (Ast.FnDef fn : m.fns()) {
                if (fn.name().equals(denoted.name()) && fn.written().authored()) {
                    written.add(fn.written());
                }
            }
            return written.isEmpty() ? Answer.absent() : Answer.of(List.copyOf(written));
        }
    }

    /**
     * Where what {@code denoted} names is written: the {@code behavior} that declares it, the
     * {@code let} where it has no signature, or the binding that introduced a local.
     *
     * <p>The first of {@link ValueDeclarationsOf}, which is the one to send a reader to. A behavior
     * has two, and its signature says more about it than its body does.
     */
    public record ValueDeclaredAt(ValueName denoted) implements Key<WrittenName> {
        @Override
        public String module() {
            return new ValueDeclarationsOf(denoted).module();
        }

        @Override
        public Answer<WrittenName> compute(Db db) {
            Answer<List<WrittenName>> written = db.ask(new ValueDeclarationsOf(denoted));
            return written.present() ? Answer.of(written.value().get(0)) : Answer.absent();
        }
    }

    /**
     * Whether the occurrence {@code written} covers {@code at}. A name is one line's worth of text,
     * so a position on another line is not on it — and a name in another file is not on it either,
     * however the line numbers happen to line up.
     *
     * <p>How far it reaches is the characters that spell it, not the name they spell. A decomposed
     * spelling is wider than the composed name it denotes, so measuring in the name puts the far end
     * of every such name out of reach of a cursor sitting on it.
     *
     * <p>A name nobody wrote is under no cursor. A desugaring's binding is anchored on the form it
     * was rewriting, and that form is holding something else — so a reader answered from one would
     * be answered about a name that is not there, at a width that is not its.
     *
     * <p>The file matters here for the reason it matters anywhere: what a module is made of is not
     * all written in one file. An attached {@code examples for} file's rows, tables and values join
     * the module they are for, and a question asked of the module reads all of them, so line 8
     * column 14 is two places and an editor asking about one of them was answered about whichever
     * came first. What was under the cursor and what the answer described were then two different
     * names.
     *
     * <p>A question that names no file names no place either. Every question about a cursor now
     * arrives as one, because the module it is answered about is read off the file it names, so
     * there is no longer a caller with only a line and a column to give.
     */
    static boolean spans(WrittenName written, SourcePos at) {
        return written != null && written.authored() && written.covers(at);
    }

    /** The occurrence of a type's own name, in the declaration that declares it. */
    public record DeclaredAt(TypeSymbol.AtModule denoted) implements Key<WrittenName> {
        @Override
        public String module() {
            return denoted.module();
        }

        @Override
        public Answer<WrittenName> compute(Db db) {
            Answer<Map<String, Ast.Def>> defs = db.ask(new Declarations(denoted.module()));
            if (!defs.present()) {
                return Answer.absent();
            }
            // The occurrence of the name, not where the declaration starts: a reader sent to a
            // declaration is being sent to the name it declares, the keyword in front of it is not
            // what they asked about, and how far the name reaches is the characters that spell it.
            Ast.Def def = defs.value().get(denoted.name());
            return def == null || !def.written().authored()
                    ? Answer.absent() : Answer.of(def.written());
        }
    }

    /**
     * Which modules take part in an import cycle, and the error to report at each. A cycle is
     * followed through imports and through qualified type references alike: a qualified reference
     * needs no import line, so reading only the import lines would let a cycle through unseen.
     */
    public record Cycles() implements Key<Cycles.Of> {

        /**
         * The cycles the module references close, and who is caught in them.
         *
         * @param reported the error for each module a cycle was closed at — one per cycle, on the
         *                 source that wrote the reference that closes it
         * @param members every module taking part in one, which is more: the error belongs to one
         *                of them, but none of them can be compiled
         */
        public record Of(Map<String, Report> reported, Set<String> members) {}

        @Override
        public Answer<Of> compute(Db db) {
            List<String> declared = db.ask(new Front.Declared()).value();
            if (declared == null) {
                return Answer.of(new Of(Map.of(), Set.of()));
            }
            Set<String> modules = db.ask(new Front.ModuleNames()).value();
            Map<String, List<Dependency>> deps = new LinkedHashMap<>();
            for (String name : declared) {
                Ast.Module m = db.ask(new Front.Available(name)).value();
                if (m != null) {
                    deps.put(name, dependencies(m, modules));
                }
            }
            Map<String, Report> found = new LinkedHashMap<>();
            Set<String> members = new LinkedHashSet<>();
            Set<String> done = new LinkedHashSet<>();
            List<String> stack = new ArrayList<>();
            for (String name : declared) {
                visit(name, deps, done, stack, found, members);
            }
            return Answer.of(new Of(Ordered.map(found), Ordered.set(members)));
        }

        private void visit(String name, Map<String, List<Dependency>> deps, Set<String> done,
                           List<String> stack, Map<String, Report> found, Set<String> members) {
            if (done.contains(name) || !deps.containsKey(name)) {
                return;
            }
            stack.add(name);
            for (Dependency dep : deps.get(name)) {
                int closes = stack.indexOf(dep.module());
                if (closes >= 0) {
                    // The reference that closes the cycle is written here, so this is the file to
                    // quote. Everything from the module it names round to this one is in the cycle,
                    // and none of them can be compiled — each needs an answer from the next.
                    found.putIfAbsent(name, Report.raised(Diagnostic.at(dep.pos()).say(new DeclarationMessage.CyclicModuleDependency()).build()));
                    members.addAll(stack.subList(closes, stack.size()));
                    continue;
                }
                visit(dep.module(), deps, done, stack, found, members);
            }
            stack.remove(stack.size() - 1);
            done.add(name);
        }
    }

    /** Whether {@code name} takes part in an import cycle, so nothing about it can be worked out. */
    static boolean cyclic(Db db, String name) {
        Cycles.Of cycles = db.ask(new Cycles()).value();
        return cycles != null && cycles.members().contains(name);
    }

    /** One module reaching another, and where it does so. */
    private record Dependency(String module, SourcePos pos) {}

    private static List<Dependency> dependencies(Ast.Module m, Set<String> modules) {
        List<Dependency> deps = new ArrayList<>();
        Map<String, String> qualifiers = Scoping.qualifiersWritten(m);
        for (Ast.Import imp : m.imports()) {
            deps.add(new Dependency(imp.module(), imp.pos()));
        }
        for (Ast.TypeRef ref : typeRefs(m)) {
            int dot = ref.name().lastIndexOf('.');
            if (dot < 0) {
                continue;
            }
            String qualifier = ref.name().substring(0, dot);
            String target = qualifiers.getOrDefault(qualifier, qualifier);
            if (modules.contains(target) && !target.equals(m.name())) {
                deps.add(new Dependency(target, ref.pos()));
            }
        }
        for (Ast.Var ref : Scoping.qualifiedBehaviorRefs(m)) {
            String target = Scoping.moduleNamedBy(ref.name(), qualifiers);
            if (modules.contains(target) && !target.equals(m.name())) {
                deps.add(new Dependency(target, ref.pos()));
            }
        }
        return deps;
    }

    /**
     * The imports a module has: the ones it wrote, and the ones a qualified reference in its header
     * asked for.
     *
     * <p>What a reader wanting "which modules does this reach, and which of their names" asks. The
     * second kind carries no import line, and a reader that read only the lines would miss the
     * borrowed signature it has to hold.
     *
     * <p>Answered by {@link Scoping}, which is where the scope those imports fill is assembled. A
     * reader here and the scope disagreeing about which imports a module has is a reader holding a
     * signature the scope never brought a name in for.
     *
     * <p>The module's own lines are read off what it wrote, so a module nothing may be built on
     * still has the imports it wrote. The ones a qualified reference asks for are not: which
     * behaviors another module declares is a question about that module, and a universe that will
     * not let it be built on does not answer it. Nothing is synthesized then — what the reference
     * names is answered where the reference is written, and an import invented for it would say it
     * a second time against a line nobody wrote.
     *
     * <p>Not what a cycle is found by. That is walked off the spellings a module writes
     * ({@link Cycles}), which is why a cycle written with no import line at all is still found.
     */
    public static List<Ast.Import> importsOf(Db db, String name) {
        Ast.Module m = db.ask(new Front.Available(name)).value();
        return m == null ? List.of() : Scoping.importsOf(CompilationUniverse.over(db), m);
    }

    /** The same, of a module resolution has been over. */
    static Set<String> behaviorNames(Hir.Module m) {
        Set<String> names = new LinkedHashSet<>();
        for (Hir.BehaviorDef b : m.behaviors()) {
            names.add(b.name());
        }
        return names;
    }

    /**
     * The name arrived twice, from two imports. Either way the way out is inside the module: keep at
     * most one of them bare and name the other through its module.
     */
    private static Report broughtTwice(String name, Ast.Import imp, Ast.Import earlier) {
        Diagnostic.Builder b = Diagnostic.at(imp.pos())
                .secondary(Region.point(earlier.pos()), new ModuleMessage.ItWasAlreadyImportedHere(name, earlier.module()));
        return Report.raised((earlier.module().equals(imp.module())
                        ? b.hint(new ModuleMessage.TheSameNameIsImportedTwiceFromOneModule())
                        : b.hint(new ModuleMessage.ImportAtMostOneAndQualifyTheOther(name, imp.module()))).say(new ModuleMessage.TheNameIsImportedFromTwoModules(name, earlier.module(), imp.module())).build());
    }

    /** Every type written in {@code m}: its data's fields, and its behaviors' and fns' signatures. */
    static List<Ast.TypeRef> typeRefs(Ast.Module m) {
        List<Ast.TypeRef> refs = new ArrayList<>();
        for (Ast.Def def : m.defs()) {
            if (def instanceof Ast.Data d) {
                for (Ast.Field f : d.fields()) {
                    collectTypeRefs(f.type(), refs);
                }
            }
        }
        for (Ast.BehaviorDef b : m.behaviors()) {
            if (b instanceof Ast.SpecBehavior spec) {
                for (Ast.Param p : spec.params()) {
                    collectRetType(p.type(), refs);
                }
                collectRetType(spec.ret(), refs);
            } else if (b instanceof Ast.PipeBehavior pipe) {
                collectRetType(pipe.declaredOut(), refs);
            }
        }
        for (Ast.FnDef fn : m.fns()) {
            for (Ast.FnParam p : fn.params()) {
                collectRetType(p.type(), refs);
            }
            collectRetType(fn.declaredReturn(), refs);
        }
        return refs;
    }

    private static void collectRetType(Ast.RetType ret, List<Ast.TypeRef> refs) {
        if (ret != null) {
            ret.cases().forEach(c -> collectTypeRefs(c, refs));
        }
    }

    private static void collectTypeRefs(Ast.TypeTerm term, List<Ast.TypeRef> refs) {
        if (term instanceof Ast.FnType fn) {
            fn.params().forEach(p -> collectRetType(p, refs));
            collectRetType(fn.result(), refs);
            return;
        }
        if (!(term instanceof Ast.TypeRef ref)) {
            return;
        }
        if (ref.name() != null) {
            refs.add(ref);
        }
        collectTypeRefs(ref.arg(), refs);
        if (ref.tupleElems() != null) {
            ref.tupleElems().forEach(e -> collectTypeRefs(e, refs));
        }
    }
}
