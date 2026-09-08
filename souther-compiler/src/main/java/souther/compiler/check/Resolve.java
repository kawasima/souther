package souther.compiler.check;

import souther.compiler.stdlib.Stdlib;
import souther.compiler.types.BinOp;
import souther.compiler.ast.Ast;
import souther.compiler.ast.Hir;
import souther.compiler.ast.Reading;
import souther.compiler.ast.WrittenName;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.msg.DeclarationMessage;
import souther.compiler.diag.msg.ExampleMessage;
import souther.compiler.diag.msg.NameMessage;
import souther.compiler.diag.msg.BehaviorMessage;
import souther.compiler.diag.msg.ModuleMessage;
import souther.compiler.diag.Region;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.BindingId;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.ReachName;
import souther.compiler.types.Type;
import souther.compiler.types.Denotation;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.ValueName;
import souther.compiler.Reserved;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Says once, for a whole module, what every written type name denotes.
 *
 * <p>Before this pass a name is a spelling: {@code 金額}, {@code billing.金額}, or an alias'd
 * {@code B.金額}, and whether the three mean one type is a question each consumer used to answer for
 * itself — which is why a capability reached the positions someone wired it to and no others
 * (issues #101, #113, #124, #132, #154). After it, every {@link Ast.Name} in the tree carries the
 * {@link TypeSymbol} it denotes, and a check that wants to know whether two names are the same type
 * compares what they denote. There is no spelling left to compare.
 *
 * <p>A name that denotes nothing is reported here and is {@link Hir.Name.Unanswered} from there on
 * — a state of the name and not a stand-in identity, so nothing below reads it as a declaration and
 * nothing below has a spelling to fall back to. Where a type reference stands on such a name, what
 * it denotes is {@link souther.compiler.types.Type#ERRONEOUS}. The pass does not stop: the rest of
 * the module is resolved
 * as if the mistake were not there, which is what lets an author be told about every unknown name at
 * once and lets an editor still say what the names around one mean. A name a pass synthesized
 * already knowing what it means (a codec {@code Deriver} builds from a field's type) is left as it
 * is.
 */
public final class Resolve {

    private final SyntaxSymbols symbols;
    private final Reachable reachable;
    /** {@link Reachable#byName()}, worked out once: a bare name is looked up for every name this
     * pass reads, and the table does not change while it reads. */
    private final Map<String, ValueName> reaches;
    private final Elsewhere elsewhere;
    /** Every name this pass answered, in the order it met them. */
    private final List<TypeUse> denotations = new ArrayList<>();
    /** The same, for the names used as values. */
    private final List<ValueUse> values0 = new ArrayList<>();
    /** What it has to say about the names it could not answer, as the errors it would once have
     * thrown. Not every one of them is in here: some are already reported elsewhere. */
    private final List<CompileException> unresolved = new ArrayList<>();
    /**
     * How many names it could not answer, by every route there is.
     *
     * <p>Counted apart from what was said about them, because they are two things. A name is left
     * unanswered and said nothing about where the reason is somewhere else and already reported
     * there — a module this compilation has and cannot use has its exported names put in an
     * importer's scope as identities nothing declares, so the importer is not told a second time
     * about a file that is fine. That is the same absence as a misspelling; only the report differs.
     * Whether a declaration's names came out is this, and never how many diagnostics were added
     * while it was being read.
     */
    private int failed;
    /** What each binding this pass gave an identity to is called, and where that is written. */
    private final Map<BindingId, BoundName> binders = new LinkedHashMap<>();
    /** How many bindings each definition has been given, so the next one gets the next number. */
    private final Map<BindingOwner, Integer> counts = new HashMap<>();
    /** The definition whose text is being read. Every binding met belongs to it. */
    private BindingOwner owner;

    /** Whether the definition being read is a value an attached file declares. */
    private boolean readingAnAttachedValue;
    /**
     * The behaviors this module named through another module's name, by that module.
     *
     * <p>A qualified stage reaches a behavior whether or not an import says so, and the borrowed
     * signature and the injected field are found by an import. So naming one asks for the import,
     * and the import is written here rather than left for a reader to work out from the spelling.
     */
    private final Map<String, Set<String>> borrowed = new LinkedHashMap<>();
    /** Where each of those was first named — the position the synthesized import stands at. */
    private final Map<String, SourcePos> borrowedAt = new LinkedHashMap<>();
    /** Every behavior a qualified reference reached, as this pass answered it. */
    private final List<QualifiedUse> qualified = new ArrayList<>();

    private Resolve(SyntaxSymbols symbols, Values values) {
        this.symbols = symbols;
        this.reachable = values.reachable();
        this.reaches = reachable.byName();
        this.elsewhere = values.elsewhere();
    }

    /** The value definition of this spelling in this module. */
    private BindingOwner ownerOfValue(String name) {
        return new BindingOwner.OfValue(reachable.module(), name);
    }

    /** A binder answered, and the bindings that hold under it. */
    private record Answered(Hir.Binder binder, InForce bound) {}

    /**
     * {@code binder} given a binding of its own, and {@code bound} extended with it.
     *
     * <p>The binding is what the names under it are answered with, so two bindings of one spelling
     * are two answers however they were written. Where it was written is kept aside, for a reader
     * that is asking about the source rather than about the program.
     */
    private Answered bind(InForce bound, Ast.Binder binder) {
        int ordinal = counts.merge(owner, 1, Integer::sum) - 1;
        BindingId id = new BindingId(owner, ordinal);
        if (binder.namePos() != null) {
            // Only a name the author wrote is a place a reader can be sent to or asked about. A
            // desugaring's binding is anchored on the form it came from, which is a place holding
            // something the author did write.
            binders.put(id, new BoundName(binder.written()));
        }
        return new Answered(
                new Hir.Binder(binder.written(), id, binder.pos()),
                bound.and(binder.name(), new ValueName.Local(binder.name(), id)));
    }

    /** Several binders answered, and the bindings that hold under all of them. */
    private record AnsweredAll(List<Hir.Binder> binders, InForce bound) {}

    /** The same as {@link #bind}, for the names one binder writes at once — a block's parameters. */
    private AnsweredAll bindAll(InForce bound, List<Ast.Binder> written) {
        List<Hir.Binder> out = new ArrayList<>();
        for (Ast.Binder b : written) {
            Answered a = bind(bound, b);
            bound = a.bound();
            out.add(a.binder());
        }
        return new AnsweredAll(out, bound);
    }

    /**
     * What a module can name in the value namespace without a binding: its own helpers, and the
     * behaviors it can reach.
     *
     * <p>The behaviors come from outside — an import brings one in — so they are given rather than
     * read off the module. A module resolved on its own reaches only what it declares.
     *
     * <p>A table and nothing else. What is here was settled when the scope was assembled and will
     * not be asked again, which is what lets a reader that only wants to know what is in scope —
     * an editor offering completions — hold one: it is a value, it is equal to the next one that
     * says the same thing, and holding it reaches nothing.
     *
     * <p>{@code attachedValues} names which of the helpers an attached file declares, which is not
     * a second table: those spellings are in {@code helpers} and mean what they mean, and this says
     * which of them only the rows may write (spec §an-attached-files-values-are-for-its-rows).
     * Here rather than worked out again by each reader, because two readers ask it — resolution
     * refuses one written in the model, and an editor does not offer one where it would be refused
     * — and two walks over the definitions would be that rule written twice.
     */
    public record Reachable(String module, Map<String, ValueName.Helper> helpers,
                            Map<String, ValueName.Behavior> behaviors,
                            Set<String> standingForNothing, boolean behaviorsWhole,
                            Map<String, ValueName.Stdlib> exposed,
                            Set<String> attachedValues) {

        /** Copied, because a reader holds this. A table something else can still write to is not a
         *  value, and the reader that held one would find what it read had changed under it — the
         *  scope a compilation remembers and the scope resolution ran against being the same
         *  object. Copied in order: what a name is offered against is answered from these, and a
         *  suggestion that came out in a different order each time would be a different answer. */
        public Reachable {
            helpers = Collections.unmodifiableMap(new LinkedHashMap<>(helpers));
            behaviors = Collections.unmodifiableMap(new LinkedHashMap<>(behaviors));
            standingForNothing =
                    Collections.unmodifiableSet(new LinkedHashSet<>(standingForNothing));
            exposed = Collections.unmodifiableMap(new LinkedHashMap<>(exposed));
            attachedValues =
                    Collections.unmodifiableSet(new LinkedHashSet<>(attachedValues));
        }

        /**
         * Every bare spelling this reaches something by, and what writing it would mean.
         *
         * <p>The order the three tables are consulted in is a rule, and it is written once — here.
         * An import brings a library name in, and everything the module already has is what that
         * spelling means instead. Resolving a bare name reads this, so a reader listing what may be
         * written here reads the answer resolution reads rather than a second assembly of the same
         * three tables: two of those would agree until one of them moved, and nothing would say
         * which had.
         *
         * <p>A binding in force is not here: it is answered from the bindings that hold at the
         * position, before this is read, and is no fact about the module. Nor is a type written as
         * a value — but that one is read <em>after</em> this rather than before it. What a spelling
         * reaches here is settled, and a type of that name is what to do when nothing here answers,
         * so a data an import brought in no longer takes a spelling this module writes a
         * {@code let} for.
         */
        public Map<String, ValueName> byName() {
            Map<String, ValueName> reached = new LinkedHashMap<>(helpers);
            behaviors.forEach(reached::putIfAbsent);
            exposed.forEach(reached::putIfAbsent);
            return Collections.unmodifiableMap(reached);
        }

        /**
         * What writing {@code name} here would mean.
         *
         * <p>Three answers, and the third is why this is asked rather than looked up. A name an
         * import line was to bring in and could not is in scope denoting nothing: what is wrong was
         * said on that line, so a use of it says nothing more. Absent from the table instead, every
         * use is reported as a name nothing declares, and the author is sent to a body where
         * nothing is wrong — which is the same reasoning the type namespace was written to
         * ({@link Denotation}), and the same three answers.
         */
        public Reach reach(String name) {
            return reachIn(byName(), name);
        }

        /** The same, over a table {@link #byName} already answered. Package-private, so the only
         *  readers are the ones that got the table from here: the rule about what a spelling means
         *  is this one, and a caller that assembled a table of its own would be asking it of
         *  something else. {@code Resolve} reads every name a module writes, and rebuilding the
         *  table for each of them is what this saves. */
        Reach reachIn(Map<String, ValueName> reached, String name) {
            ValueName named = reached.get(name);
            if (named != null) {
                return new Reach.Reaches(named);
            }
            return standingForNothing.contains(name) ? Reach.STANDS_FOR_NOTHING
                    : Reach.NOT_IN_SCOPE;
        }

        /**
         * What a module reaches when nothing else is in sight — the core modules, which the library
         * resolves as it loads. A core module imports nothing (it declares the library), so the
         * table of names an import would bring in is empty; a module that does import is resolved
         * with what the scope it was assembled into answered.
         */
        public static Reachable of(Ast.Module m) {
            Map<String, ValueName.Behavior> behaviors = new LinkedHashMap<>();
            for (Ast.BehaviorDef b : m.behaviors()) {
                behaviors.put(b.name(), new ValueName.Behavior(m.name(), b.name()));
            }
            Map<String, ValueName.Helper> helpers = new LinkedHashMap<>();
            Set<String> attached = new LinkedHashSet<>();
            for (Ast.FnDef fn : m.fns()) {
                if (HelperInliner.isHelperName(behaviors.keySet(), fn.name())) {
                    helpers.put(fn.name(), new ValueName.Helper(m.name(), fn.name()));
                    if (!fn.role().isTheModels()) {
                        attached.add(fn.name());
                    }
                }
            }
            return new Reachable(m.name(), helpers, behaviors, Set.of(), true, Map.of(), attached);
        }
    }

    /**
     * The table, and what can still be asked while a module is being resolved.
     *
     * <p>Two things and not six, because they are two kinds of thing. The table is settled; the
     * other is a way of putting a question to whatever supplied the modules, and it is here because
     * which module a qualified behavior reference names is decided while this pass runs — a
     * qualifier is whatever the author wrote, so the set cannot be worked out in front of it.
     *
     * <p>Told apart so that a reader wanting the first is not handed the second. One of these ends
     * up inside an answer a compilation remembers, and a reader holding the pair holds a way to
     * reach the whole compilation; a reader holding the table holds a table.
     */
    public static final class Values {

        private final Reachable reachable;
        private final Elsewhere elsewhere;

        /**
         * Made where a scope is assembled, and nowhere a caller can reach.
         *
         * <p>The two are not free of each other: the table says what the modules around this one
         * brought in, and the other is the way of asking those same modules a further question. Put
         * together by a caller, they could be a table from one set of modules beside a way of
         * asking a different set — what an import brought in decided by one and what a qualifier
         * names by the other, which is the disagreement this whole seam is here to make
         * unwritable.
         */
        Values(Reachable reachable, Elsewhere elsewhere) {
            this.reachable = reachable;
            this.elsewhere = elsewhere;
        }

        /** A module resolved on its own: what it declares, and nothing else in sight. */
        public static Values of(Ast.Module m) {
            return new Values(Reachable.of(m), Elsewhere.NONE);
        }

        /** What the module can name without a binding. */
        public Reachable reachable() {
            return reachable;
        }

        Elsewhere elsewhere() {
            return elsewhere;
        }

        /**
         * Two of these say the same thing when their parts do.
         *
         * <p>Written out because this is not a record, and it ends up inside an answer a
         * compilation remembers: an answer that never equals the last one is an answer nothing that
         * read it is ever kept past.
         */
        @Override
        public boolean equals(Object other) {
            return other instanceof Values values
                    && reachable.equals(values.reachable)
                    && elsewhere.equals(values.elsewhere);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(reachable, elsewhere);
        }

        @Override
        public String toString() {
            return "Values[reachable=" + reachable + ", elsewhere=" + elsewhere + "]";
        }
    }

    /**
     * What this module can be told about the behaviors of the modules around it, asked one question
     * at a time.
     *
     * <p>A stage may name a behavior of a module this one has not imported, which is what says the
     * import has to be synthesized; answering that needs the other module, and it is asked for by
     * name rather than handed over so that a compilation reaching no other module reads none.
     */
    public interface Elsewhere {

        /** Nothing else in sight — a module resolved on its own. */
        Elsewhere NONE = new Elsewhere() {
            @Override
            public boolean hasModule(String name) {
                return false;
            }

            @Override
            public Declares declaresBehavior(String module, String name) {
                return Declares.NO;
            }

            @Override
            public Set<String> behaviorNamesToSuggest(String module) {
                return Set.of();
            }
        };

        /** Whether this compilation has a module of that name. */
        boolean hasModule(String name);

        /** Whether that module declares a behavior of that name. */
        Declares declaresBehavior(String module, String name);

        /**
         * The behavior names a report may offer where nothing answered to one.
         *
         * <p>Told apart from {@link #declaresBehavior} because the two are different capabilities,
         * not because they read different things. That one settles what a name means; this one is
         * what a "did you mean" may say, and what belongs in it is a question about reports. One
         * method answering both is a set handed to a reader that only had a question, and a reader
         * holding the set is a reader that can write a rule of its own about what the module has.
         */
        Set<String> behaviorNamesToSuggest(String module);
    }

    /**
     * Whether a module declares something, where being unable to say is one of the answers.
     *
     * <p>Three and not two. A module this compilation has and cannot read declares nothing anybody
     * here can name, and that is not the same as its declaring none: whatever is wrong with it is
     * reported on its own source, so a name that may have come from there is left unanswered and
     * said nothing about. Answered as a set that was null, the reader that forgot the null was
     * told the module declares nothing.
     */
    public enum Declares {

        /** It declares one. */
        YES,

        /** It does not. */
        NO,

        /** This compilation has the module and cannot read it, so nothing here can say. */
        CANNOT_SAY
    }

    /**
     * One written name and what it turned out to denote, where it was written.
     *
     * <p>This is the pass's other product. Working out what a name means is a traversal of the whole
     * module, and an editor asking "what is under the cursor" or "where else is this named" is asking
     * about the answers that traversal already gave. Collecting them here is what keeps the editor
     * from walking the tree again with a rule of its own — which, before this, is how renaming a
     * type could rewrite the tail of a qualified reference to a different module's type.
     *
     * @param written the occurrence the name was read from — the name, the characters that spell
     *                it there, and where they are
     * @param denotes what it names
     */
    public record TypeUse(WrittenName written, TypeSymbol denotes) {

        /** Where the name is written. */
        public SourcePos pos() {
            return written.pos();
        }
    }

    /**
     * One written name in the value namespace and what it turned out to denote, where it was
     * written. What {@link TypeUse} is for a type, and collected for the same reason: an editor
     * asking what is under the cursor is asking about the answer this traversal already gave.
     */
    public record ValueUse(WrittenName written, ValueName denotes) {

        /** Where the name is written. */
        public SourcePos pos() {
            return written.pos();
        }
    }

    /**
     * What the pass worked out about the names a module writes, and nothing else.
     *
     * <p>Every entry is an answer it reached. A name it could not answer is in none of them, so a
     * reader asking about one is told there is nothing there rather than handed a declaration that
     * was never written — the identity the traversal carries past a mistake stands for the absence
     * and is not an identity anything declares. This is what a reader outside the compiler is
     * offered, which is why it is a partial record rather than a total one: an incomplete module has
     * incomplete answers about it, and the answers it does have are as good as any other module's.
     *
     * <p>{@code binders} says what each binding is called and where the author wrote that name. A
     * binding is not its position — a pass that expands a helper stamps the call site over the
     * positions in the copy — so the two are kept apart, and a reader asking about the source rather
     * than about the program reads this. The characters that spell it come with it, because a reader
     * asking what a cursor is on has to know how far the name reaches, and neither a position nor
     * the name alone says.
     *
     * <p>Only the bindings whose names the author wrote are in it. A desugaring binds a value to a
     * name of its own — the parameter {@code .field} becomes, the value a {@code match} is held in —
     * and anchors it on the form it was rewriting. That anchor is a place in the source holding
     * something else, so a reader answered with one of these would be answered about a name that is
     * not there, at a width that is not its.
     */
    public record ResolutionIndex(List<TypeUse> types, List<ValueUse> values,
                                  Map<BindingId, BoundName> binders) {}

    /**
     * A resolved module, what the pass worked out about the names in it, and the names it could not
     * answer.
     *
     * <p>A name that denotes nothing does not end the pass. It is {@link Hir.Name.Unanswered}, a
     * type reference standing on one denotes {@link souther.compiler.types.Type#ERRONEOUS}, and the
     * rest of the module is resolved as if the mistake were not there — so an author is told about
     * every unknown name at once instead of one per compile, and an editor can still say what the
     * names around it mean.
     */
    public record Resolution(Hir.Module module, ResolutionIndex index,
                             List<CompileException> unresolved,
                             Map<String, OfDeclaration> declarations,
                             List<QualifiedUse> qualified) {}

    /**
     * One behavior a qualified reference reached, and where the reference is written.
     *
     * <p>What this pass answered, handed on as what it answered. A reader wanting these used to
     * find them among the module's imports, because an import is synthesized for each module one
     * reaches — but an import records a dependency, and a dependency the module already has is not
     * recorded twice. So a behavior named through its module was invisible to that reader whenever
     * a line happened to name the same module and name, which is exactly when the bare spelling had
     * been refused and the qualified reference was the only way the behavior was reached at all.
     *
     * <p>The occurrence and not the module. An import stands where the first reference to that
     * module is, so a second one elsewhere was reported at the first one's line.
     */
    public record QualifiedUse(ValueName.Behavior named, SourcePos at) {}

    /**
     * What resolving one declaration came to.
     *
     * <p>{@code answered} is whether every name written in it was answered — the names met while it
     * was being read, and no others, so a mistake in the declaration beside it is not one of these.
     * {@code reaches} is what those names turned out to denote, which is what says whether this
     * declaration stands on one that has no meaning.
     */
    public record OfDeclaration(boolean answered, Set<TypeSymbol> reaches) {}

    /** What a binding is called, and the occurrence of that name the author wrote. */
    public record BoundName(WrittenName written) {

        /** Where the name is written. */
        public SourcePos pos() {
            return written.pos();
        }
    }

    /** {@code m} with every name it writes resolved against its own definitions — a module compiled
     * with nothing else in sight. */
    public static Hir.Module module(Ast.Module m, Stdlib stdlib) {
        return module(m, SyntaxSymbols.of(m, stdlib));
    }

    /** {@code m} with every name it writes resolved against {@code symbols}. */
    public static Hir.Module module(Ast.Module m, SyntaxSymbols symbols) {
        Resolution resolved = resolving(m, symbols);
        if (!resolved.unresolved().isEmpty()) {
            // This entry point answers with a module or not at all, which is what its one caller —
            // loading the shipped core — needs: a misspelled type in a prelude resource is a fault in
            // the compiler, not something an author can be told about and carry on past.
            throw resolved.unresolved().get(0);
        }
        return resolved.module();
    }

    /** As {@link #module(Ast.Module, SyntaxSymbols)}, keeping what each name was answered with. */
    public static Resolution resolving(Ast.Module m, SyntaxSymbols symbols) {
        return resolving(m, symbols, Values.of(m));
    }

    /** As {@link #resolving(Ast.Module, SyntaxSymbols)}, with what the module reaches in the value
     * namespace given rather than read off the module itself. */
    public static Resolution resolving(Ast.Module m, SyntaxSymbols symbols, Values values) {
        Resolve r = new Resolve(symbols, values);
        List<Hir.Def> defs = new ArrayList<>();
        // Which names were answered is settled per declaration: what one of them writes is nothing
        // the one beside it wrote, and the names met while it was being read are its own.
        Map<String, OfDeclaration> declarations = new LinkedHashMap<>();
        // What the module declares, rather than what its text writes. A second declaration of a name
        // and a built-in case name are refused where declarations are indexed, and this pass is not
        // where that is decided again: reading them here would mean answering for a declaration
        // nothing else in the compilation has, which can only be done by making up an identity for
        // it.
        for (Map.Entry<TypeSymbol.AtModule, Ast.Def> declared
                : symbols.declaredHere().entrySet()) {
            int failedBefore = r.failed;
            int denotedBefore = r.denotations.size();
            Hir.Def resolved = r.def(declared.getKey(), declared.getValue());
            defs.add(resolved);
            Set<TypeSymbol> reaches = new LinkedHashSet<>();
            for (TypeUse d : r.denotations.subList(denotedBefore, r.denotations.size())) {
                reaches.add(d.denotes());
            }
            declarations.put(resolved.name(),
                    new OfDeclaration(r.failed == failedBefore, Set.copyOf(reaches)));
        }
        List<Hir.BehaviorDef> behaviors = new ArrayList<>();
        for (Ast.BehaviorDef b : m.behaviors()) {
            behaviors.add(switch (b) {
                case Ast.SpecBehavior spec -> new Hir.SpecBehavior(spec.written(), r.params(spec.params()),
                        r.retType(spec.ret()), r.names(spec.constructs()),
                        r.required(spec.dependsOn(), spec.name()), r.ensures(spec), spec.pos());
                case Ast.PipeBehavior pipe -> new Hir.PipeBehavior(pipe.written(),
                        r.stages(pipe.stages()), r.retType(pipe.declaredOut()), pipe.pos());
            });
        }
        List<Hir.FnDef> fns = new ArrayList<>();
        for (Ast.FnDef fn : m.fns()) {
            fns.add(r.fn(fn));
        }
        List<Hir.Example> examples = new ArrayList<>();
        for (Ast.Example e : m.examples()) {
            r.owner = r.ownerOfValue(e.target());
            List<Hir.ExampleRow> rows = new ArrayList<>();
            for (Ast.ExampleRow row : e.rows()) {
                List<Hir.With> withs = new ArrayList<>();
                for (Ast.With w : row.withs()) {
                    withs.add(new Hir.With(r.standsInFor(w.dep()),
                            r.expr(w.value(), Reading.A_FIXTURE), w.pos()));
                }
                rows.add(new Hir.ExampleRow(row.identity(),
                        r.exprs(row.inputs(), Reading.A_FIXTURE), withs,
                        r.expected(row.expected(), Reading.A_FIXTURE), row.pos()));
            }
            examples.add(new Hir.Example(e.target(), rows, e.pos()));
        }
        List<Hir.Fake> fakes = new ArrayList<>();
        for (Ast.Fake f : m.fakes()) {
            // The spelling, because this names which definition the bindings under it belong to and
            // two tables written for two behaviors of one bare name are written differently.
            r.owner = r.ownerOfValue(f.target().name());
            Hir.Var target = r.standsInFor(f.target());
            List<Hir.FakeRow> rows = new ArrayList<>();
            for (Ast.FakeRow row : f.rows()) {
                Hir.Matched matched = switch (row.matched()) {
                    case Ast.Matched.Arguments(List<Ast.Expr> inputs) ->
                            new Hir.Matched.Arguments(r.exprs(inputs, Reading.A_FIXTURE));
                    case Ast.Matched.Anything _ -> new Hir.Matched.Anything();
                };
                rows.add(new Hir.FakeRow(matched, r.expr(row.output(), Reading.A_FIXTURE),
                        row.pos()));
            }
            fakes.add(new Hir.Fake(target, rows, f.pos()));
        }
        Map<String, Hir.RetType> exposedOutputs = new LinkedHashMap<>();
        for (Map.Entry<String, Ast.RetType> e : m.exposedOutputs().entrySet()) {
            exposedOutputs.put(e.getKey(), r.retType(e.getValue()));
        }
        if (!m.takenOn().isEmpty()) {
            // What a module takes on is worked out by `Shapes.Prepared`, which is far below here. A
            // module arriving with one has been through a pass that writes into the representation
            // this one answers with.
            throw new IllegalStateException(
                    "`" + m.name() + "` reached resolution having already taken helpers on");
        }
        return new Resolution(
                new Hir.Module(m.name(), m.exposing(), exposedOutputs,
                        r.imports(m), defs, behaviors, fns, List.of(), examples, fakes,
                        m.exampleFileTarget(), m.pos()),
                new ResolutionIndex(List.copyOf(r.denotations), List.copyOf(r.values0),
                        Map.copyOf(r.binders)),
                List.copyOf(r.unresolved), Map.copyOf(declarations),
                List.copyOf(r.qualified));
    }

    /**
     * The import lines, carried across unchanged.
     *
     * <p>An import writes a module name and the names it brings in, and neither is a reference
     * occurrence: what a name brought in denotes is decided where it is used, not here. So there is
     * nothing to answer, and this is the boundary copying a form that says the same thing on both
     * sides of it.
     */
    private List<Hir.Import> imports(Ast.Module m) {
        List<Hir.Import> out = new ArrayList<>();
        for (Ast.Import i : m.imports()) {
            List<Hir.ImportedName> names = new ArrayList<>();
            for (Ast.ImportedName n : i.importedNames()) {
                names.add(new Hir.ImportedName(n.written()));
            }
            out.add(new Hir.Import(i.module(), i.alias(), names, i.pos()));
        }
        for (Map.Entry<String, Set<String>> e : borrowed.entrySet()) {
            Set<String> already = new LinkedHashSet<>();
            for (Ast.Import i : m.imports()) {
                if (i.module().equals(e.getKey())) {
                    already.addAll(i.names());
                }
            }
            List<Hir.ImportedName> names = new ArrayList<>();
            for (String bare : e.getValue()) {
                if (!already.contains(bare)) {
                    // No position on a synthesized name: nobody wrote it on an import list. The
                    // qualified reference that asked for it is where it came from, and that is
                    // already the position of the import as a whole.
                    names.add(new Hir.ImportedName(bare, null));
                }
            }
            if (!names.isEmpty()) {
                out.add(new Hir.Import(e.getKey(), null, names, borrowedAt.get(e.getKey())));
            }
        }
        return out;
    }

    /** The stages of a {@code >->} composition, each answered against the behavior namespace. */
    private List<Hir.Var> stages(List<Ast.Var> stages) {
        List<Hir.Var> out = new ArrayList<>();
        for (Ast.Var stage : stages) {
            out.add(stage(stage));
        }
        return out;
    }

    /** The names a {@code depends on} clause writes. */
    private List<Hir.Var> required(List<Ast.Var> refs, String by) {
        List<Hir.Var> out = new ArrayList<>();
        for (Ast.Var ref : refs) {
            out.add(required(ref, by));
        }
        return out;
    }

    // --- the behavior namespace: a pipeline's stages and a spec's dependencies ---

    /**
     * A {@code >->} stage. {@code X.decoder} / {@code X.encoder} name a codec, which is a boundary
     * edge rather than a behavior (spec §sequential-composition) — said here, where the question is
     * what the name denotes, so nothing further down has a spelling to test for it.
     */
    private Hir.Var stage(Ast.Var ref) {
        if (namesABoundaryEdge(ref.name())) {
            return noBehavior(ref, CompileException.of(Diagnostic
                    .at(ref.pos()).say(new BehaviorMessage.ABoundaryEdgeIsNotAStage()).build()));
        }
        return behaviorNamed(ref, this::unknownBehavior);
    }

    /**
     * Whether a qualified spelling names a codec rather than a behavior — {@code X.decoder},
     * {@code X.encoder}, which are boundary edges (spec §sequential-composition).
     *
     * <p>Only a qualified one: {@code decoder} on its own is an ordinary name, and a module may
     * declare a behavior by it. Asked here and by whatever works out which modules a header reaches,
     * so that a codec's qualifier is not read as a module by one of them and as a type by the other.
     */
    public static boolean namesABoundaryEdge(String written) {
        int dot = written.lastIndexOf('.');
        String last = dot < 0 ? "" : written.substring(dot + 1);
        return last.equals("decoder") || last.equals("encoder");
    }

    /**
     * A name a {@code depends on} clause writes. It must name an injection target, and whether the
     * behavior it names is one is the check's to say (E1607); that nothing declares the name at all
     * is settled here, in the same message, because it is the same question — what does this name
     * denote — asked of a clause rather than of a stage.
     */
    private Hir.Var required(Ast.Var ref, String by) {
        return behaviorNamed(ref, (name, candidates) -> CompileException.of(Diagnostic
                .at(name.written().reportedAt())
                .suggestion(Suggest.candidate(name.name(), candidates))
                .hint(new DeclarationMessage.DeclareItHereOrImportIt(name.name()))
                .say(new DeclarationMessage.DependsOnNamesNoSuchBehavior(by, name.name())).build()));
    }

    /**
     * A name a {@code fake} or a {@code with} writes: the behavior it stands in for.
     *
     * <p>The same reading a {@code depends on} clause gets, which is what makes the two agree about
     * one dependency. A stand-in is written where the row is and the dependency may be declared
     * somewhere else, so the answer here is the behavior and not the spelling this module reached it
     * by — nothing below re-decides what the characters meant.
     */
    private Hir.Var standsInFor(Ast.Var ref) {
        return behaviorNamed(ref, (name, candidates) -> CompileException.of(Diagnostic
                .at(name.written().reportedAt())
                .suggestion(Suggest.candidate(name.name(), candidates))
                .hint(new DeclarationMessage.DeclareItHereOrImportIt(name.name()))
                .say(new ExampleMessage.AFakeNamesNoBehavior(name.written().quoted())).build()));
    }

    /** A name that must denote a behavior, with what to say when none does. */
    private Hir.Var behaviorNamed(Ast.Var ref, Unknown unknown) {
        String written = ref.name();
        int dot = written.lastIndexOf('.');
        if (dot < 0) {
            return bareBehavior(ref, written, unknown);
        }
        String bare = written.substring(dot + 1);
        String qualifier = written.substring(0, dot);
        if (Reserved.isQualifier(qualifier)) {
            // a standard-library qualifier names a function, and a function is not a behavior
            return noBehavior(ref, unknown.report(ref, Set.of()));
        }
        String target = symbols.scope().moduleOfQualifier(qualifier);
        if (target == null) {
            target = qualifier;
        }
        if (target.equals(reachable.module())) {
            return bareBehavior(ref, bare, unknown);   // this module, named through itself
        }
        if (!elsewhere.hasModule(target)) {
            return noBehavior(ref, CompileException.of(Diagnostic
                    .say(new ModuleMessage.NoModuleOfThatName(qualifier, bare))
                    .at(ref.pos()).build()));
        }
        switch (elsewhere.declaresBehavior(target, bare)) {
            case CANNOT_SAY -> {
                // The module is one this compilation has and could not read. What is wrong with it
                // is reported on its own source; saying anything here sends the author to a file
                // that is fine.
                return unanswered(ref);
            }
            case NO -> {
                return noBehavior(ref,
                        unknown.report(ref, elsewhere.behaviorNamesToSuggest(target)));
            }
            case YES -> { }
        }
        ValueName.Behavior named = new ValueName.Behavior(target, bare);
        // A behavior named through its module is reached through an import, whether or not the
        // author wrote one: the borrowed signature and the injected field are found by it.
        borrowed.computeIfAbsent(target, k -> new LinkedHashSet<>()).add(bare);
        borrowedAt.putIfAbsent(target, ref.pos());
        qualified.add(new QualifiedUse(named, ref.pos()));
        return behaviorReached(ref, named);
    }

    /** A bare name: this module's own behavior, or one an import brought in. */
    private Hir.Var bareBehavior(Ast.Var ref, String written, Unknown unknown) {
        ValueName.Behavior named = reachable.behaviors().get(written);
        if (named != null) {
            return behaviorReached(ref, named);
        }
        if (reachable.standingForNothing().contains(written)) {
            // A name an import line was to bring in and could not. Said on that line, so a stage
            // that writes it says nothing more — the same answer a name in a body gets, and for
            // the same reason: a reader sent here is sent to a composition that is right.
            return unanswered(ref);
        }
        if (!reachable.behaviorsWhole()) {
            // An import that could not be followed may have been where this name came from.
            // Whatever is wrong with that module is reported there.
            return unanswered(ref);
        }
        return noBehavior(ref, unknown.report(ref, reachable.behaviors().keySet()));
    }

    /**
     * {@code ref} denoting {@code name}, and reached as this module reaches it.
     *
     * <p>Both answers together, from the one place that has them. A behavior is reached by the name
     * written here — bare, or under the module a qualified reference names.
     */
    private Hir.Var behaviorReached(Ast.Var ref, ValueName.Behavior name) {
        answered(ref.written(), name);
        return new Hir.Var.Denoting(ref.written(),
                ReachName.of(name, ref.name(), reachable.module()), ref.origin(), ref.region());
    }

    /** What to say about a name no behavior answers to, given the names that were reachable. */
    private interface Unknown {
        CompileException report(Ast.Var ref, Set<String> candidates);
    }

    private CompileException unknownBehavior(Ast.Var ref, Set<String> candidates) {
        WrittenName written = ref.written();
        String name = written.canonical();
        return CompileException.of(Diagnostic
                .at(written.reportedAt())
                .suggestion(Suggest.candidate(name, candidates))
                .say(new NameMessage.NoBehaviorOfThatNameInThisPipeline(written.quoted())).build());
    }

    /** Records why a name denotes no behavior, and gives it the name that says so. */
    private Hir.Var noBehavior(Ast.Var ref, CompileException why) {
        nothing(why);
        return unanswered(ref);
    }

    /** A reference resolution read and found nothing for, keeping where it was written. */
    private static Hir.Var unanswered(Ast.Var ref) {
        return new Hir.Var.Unanswered(ref.written(), ref.origin(), ref.region());
    }

    private Hir.FnDef fn(Ast.FnDef f) {
        owner = ownerOfValue(f.name());
        readingAnAttachedValue = !f.role().isTheModels();
        List<Hir.FnParam> params = new ArrayList<>();
        InForce bound = InForce.of(Reading.THE_MODELS_OWN);
        for (Ast.FnParam p : f.params()) {
            Answered a = bind(bound, p.binder());
            params.add(new Hir.FnParam(a.binder(), paramType(p.type()), p.typeFromPattern()));
            bound = a.bound();
        }
        Hir.FnBody body = switch (f.body()) {
            case Ast.FnBody.Written w -> new Hir.FnBody.Written(expr(w.expr(), bound));
            case Ast.FnBody.Intrinsic i -> new Hir.FnBody.Intrinsic(i.key());
        };
        return new Hir.FnDef(f.written(), f.declaredIn(), params, retType(f.declaredReturn()), body,
                new Hir.Modifiers(f.modifiers().partial(), f.modifiers().isPrivate()), f.role(),
                f.pos());
    }

    // --- written types ---

    private List<Hir.Param> params(List<Ast.Param> params) {
        List<Hir.Param> out = new ArrayList<>();
        for (Ast.Param p : params) {
            out.add(new Hir.Param(p.written(), retType(p.type())));
        }
        return out;
    }

    private List<Hir.EnsuresClause> ensures(Ast.SpecBehavior behavior) {
        owner = new BindingOwner.OfSignature(
                new ValueName.Behavior(reachable.module(), behavior.name()));
        InForce params = InForce.of(Reading.THE_MODELS_OWN);
        for (Ast.Param p : behavior.params()) {
            params = bind(params, Ast.Binder.of(Ast.Name.written(p.written()))).bound();
        }
        List<Hir.EnsuresClause> out = new ArrayList<>();
        for (Ast.EnsuresClause clause : behavior.ensures()) {
            List<Hir.EnsuresArm> arms = new ArrayList<>();
            for (Ast.EnsuresArm arm : clause.arms()) {
                Answered answer = bind(params, Ast.Binder.desugared("value", arm.pos()));
                // Resolved as the case names they are, through what a `match` arm reads them with.
                // A case is not always a type name — `Int` stands as a case of `Int |
                // DivisionByZero`, and an optional's two carriers name no type at all — so reading
                // them as types admits a narrower set than the answer actually has cases.
                arms.add(new Hir.EnsuresArm(caseNames(arm.cases()), expr(arm.expr(), answer.bound()),
                        arm.pos(), arm.region()));
            }
            out.add(new Hir.EnsuresClause(clause.name(), List.copyOf(arms),
                    clause.pos(), clause.region()));
        }
        return List.copyOf(out);
    }

    /** A declaration's invariant clauses, each read against the fields it constrains. */
    private List<Hir.InvariantClause> clauses(List<Ast.InvariantClause> clauses, InForce bound) {
        List<Hir.InvariantClause> out = new ArrayList<>();
        for (Ast.InvariantClause clause : clauses) {
            out.add(new Hir.InvariantClause(clause.name(), expr(clause.expr(), bound),
                    clause.pos(), clause.region()));
        }
        return out;
    }

    private List<Hir.Field> fields(List<Ast.Field> fields) {
        List<Hir.Field> out = new ArrayList<>();
        for (Ast.Field f : fields) {
            out.add(new Hir.Field(f.written(), typeTerm(f.type())));
        }
        return out;
    }

    private Hir.RetType paramType(Ast.RetType t) {
        return retType(t);
    }

    private Hir.RetType retType(Ast.RetType ret) {
        if (ret == null) {
            return null;
        }
        List<Hir.TypeTerm> cases = new ArrayList<>();
        for (Ast.TypeTerm c : ret.cases()) {
            cases.add(typeTerm(c));
        }
        return new Hir.RetType(cases, ret.pos());
    }

    private Hir.TypeTerm typeTerm(Ast.TypeTerm t) {
        return switch (t) {
            case null -> null;
            case Ast.TypeRef ref -> typeRef(ref);
            case Ast.FnType ft -> {
                List<Hir.RetType> ps = new ArrayList<>();
                for (Ast.RetType p : ft.params()) {
                    ps.add(retType(p));
                }
                yield new Hir.FnType(ps, retType(ft.result()), ft.pos());
            }
        };
    }

    /** A written type reference, with what it denotes decided here — once, and in the module that
     * wrote it, so no later reader has to know where it was written. */
    private Hir.TypeRef typeRef(Ast.TypeRef ref) {
        if (ref == null) {
            return null;
        }
        Hir.TypeTerm arg = typeTerm(ref.arg());
        List<Hir.TypeTerm> elems = null;
        if (ref.tupleElems() != null) {
            elems = new ArrayList<>();
            for (Ast.TypeTerm e : ref.tupleElems()) {
                elems.add(typeTerm(e));
            }
        }
        TypeOps.Reference parts = new TypeOps.Reference(ref.written(), arg, elems, ref.anchor());
        Hir.TypeRef denoted = new Hir.TypeRef(ref.written(), arg, elems, typeOf(parts), ref.anchor());
        // A reference with no name is a tuple or a container shape, which names no declaration.
        if (denoted.name() != null && denoted.pos() != null) {
            switch (symbols.scope().resolve(denoted.written())) {
                case Denotation.Denotes d ->
                        denotations.add(new TypeUse(denoted.written(), d.type()));
                case Denotation.StandsForNothing ignored -> failed++;
                case Denotation.NotInScope ignored -> { }
            }
        }
        return denoted;
    }

    // --- definitions ---

    /** {@code def} resolved as {@code declared}, which is the identity the module's declarations
     * were indexed under and is handed in rather than worked out here. */
    private Hir.Def def(TypeSymbol.AtModule declared, Ast.Def def) {
        owner = new BindingOwner.OfData(declared);
        return switch (def) {
            case Ast.UnitData u -> new Hir.UnitData(u.written(), declared, u.pos());
            // an invariant reads the fields of the data it belongs to, which are what bind its
            // names — `value > 0` is about this declaration's `value`, whatever else is in scope
            case Ast.Data d -> {
                declareFields(d, declared);
                yield new Hir.Data(d.written(), declared, d.newtype(), names(d.includes()),
                        fields(d.fields()),
                        clauses(d.invariants(), boundFields(d, declared)),
                        d.pos());
            }
            case Ast.SumData s -> new Hir.SumData(s.written(), declared, sumCases(s), s.pos());
        };
    }

    /** A sum's cases keep their own message: {@code data X = A | B} names the cases of one type, so a
     * name nothing declares is answered against that sum rather than as a bare unknown type. */
    private List<Hir.Name> sumCases(Ast.SumData s) {
        List<Hir.Name> out = new ArrayList<>();
        for (Ast.Name c : s.cases()) {
            Denotation answer = symbols.scope().resolve(c.name());
            if (answer instanceof Denotation.NotInScope) {
                throw CompileException.of(Diagnostic
                                .at(s.pos()).say(new BehaviorMessage.UnknownCaseInASum(c.written(), s.name())).build());
            }
            if (answer instanceof Denotation.StandsForNothing) {
                out.add(answered(unanswered(c)));
                continue;
            }
            TypeSymbol denoted = answer.type();
            // Recorded like any other written name. A case is a name this module wrote and this pass
            // answered, so leaving it out made it a use nothing could see — an editor asked about it
            // had no answer, and a reader asking which imports are written found the name missing.
            out.add(answered(denoting(c, denoted)));
        }
        return out;
    }

    /**
     * Where each field this declaration writes is written, against the binding it introduces inside
     * an invariant.
     *
     * <p>Recorded whether or not this declaration has an invariant of its own: a declaration that
     * includes it reads these fields in <em>its</em> invariant, and the binding a field is stays the
     * declaring declaration's, so this is where an editor is answered from either way.
     */
    private void declareFields(Ast.Data d, TypeSymbol.AtModule declared) {
        Map<String, BindingId> bindings = TypeOps.fieldBindingsAsWritten(declared, d, symbols);
        for (Ast.Field field : d.fields()) {
            BindingId binding = bindings.get(field.name());
            if (binding != null) {
                binders.put(binding, new BoundName(field.written()));   // OfFields
            }
        }
    }

    /**
     * The fields of a declaration, as the names its own invariant reads — the ones written here and
     * the ones a spread brings in, which are as much this declaration's fields as the written ones
     * (and are what a spread-in invariant was written against).
     */
    private InForce boundFields(Ast.Data d, TypeSymbol.AtModule declared) {
        InForce bound = InForce.of(Reading.THE_MODELS_OWN);
        // which binding each field is is answered in one place, so the pass that emits this
        // invariant reaches the same ones without working them out again
        for (Map.Entry<String, BindingId> f
                : TypeOps.fieldBindingsAsWritten(declared, d, symbols).entrySet()) {
            bound = bound.and(f.getKey(), new ValueName.Local(f.getKey(), f.getValue()));
        }
        return bound;
    }


    // --- expressions ---

    /** An expression written where nothing is bound over it, read under {@code reading} — which a
     *  fixture is, a row writing its values where no parameter of anything stands over them. */
    private Hir.Expr expr(Ast.Expr e, Reading reading) {
        return expr(e, InForce.of(reading));
    }

    /**
     * Rewrites the names {@code e} itself writes, against the bindings in force where it is written.
     *
     * <p>Every kind is named here. Between the two representations there is no rewrite that carries
     * a node across on its own, so a node this pass does not know how to answer is a node it cannot
     * answer at all — and an expression kind added later stops the build here, which is the one
     * place it has to be accounted for.
     */
    private Hir.Expr expr(Ast.Expr e, InForce bound) {
        return switch (e) {
            case Ast.Var v -> reached(v, bound);
            // Applying a name is answered as a name: which of a binding, a helper, a library
            // function or a type it is decides what the application means. Applying anything else
            // is answered as the expression it is, and what may be applied is the check's to say.
            case Ast.Apply call when call.function() instanceof Ast.Var callee ->
                    applied(call, callee, bound);
            case Ast.Apply call -> Hir.Apply.read(call, appliedCallee(call),
                    callee(call.function(), bound), exprs(call.args(), bound));
            // `Map.empty`, `String.isEmpty`, `up.Amount` — a namespace and a member of it, which
            // the parser read as a field taken off a name because it reads no case at all. Folded
            // here and nowhere earlier: `Map` may be a parameter, and a binding in force wins over
            // everything else — which is a fact the parser and the AST builder do not have.
            case Ast.FieldAccess fa -> {
                Hir.Var member = qualifiedName(fa, bound);
                yield member != null ? member
                        : new Hir.FieldAccess(expr(fa.target(), bound), fa.name(), fa.pos(),
                                fa.region());
            }
            // the type being built is this case's business; everything under it is a slot like any
            // other
            case Ast.NewData nd -> Hir.NewData.read(nd, type(nd.typeName()),
                    inits(nd.inits(), bound), vars(nd.spreads(), bound), bound.reading());
            // a binding's pattern may write Option's `Some`, which the binding check then rejects
            // for what it is — a name that opens nothing — rather than as a name nothing declares
            case Ast.LetIn li -> {
                Hir.Expr value = expr(li.value(), bound);
                Answered a = bind(bound, li.binder());
                yield new Hir.LetIn(a.binder(), value,
                        paramType(li.declaredType()), li.annotated(),
                        li.opens() == null ? null : caseName(li.opens()),
                        expr(li.body(), a.bound()), li.pos(), li.region());
            }
            case Ast.Block b -> {
                AnsweredAll ps = bindAll(bound, b.params());
                yield new Hir.Block(ps.binders(), expr(b.body(), ps.bound()), b.rule(), b.pos(),
                        b.region());
            }
            // an attempt's binder names the value only where there is one to name — the success
            // branch. The construction and the else value are outside it.
            case Ast.IfConstructed ic -> {
                Answered a = bind(bound, ic.binder());
                yield new Hir.IfConstructed(expr(ic.construct(), bound), a.binder(),
                        expr(ic.then(), a.bound()), arms(ic.els(), bound), ic.origin(), ic.pos(),
                        ic.region());
            }
            case Ast.Match m -> {
                List<Hir.Case> cases = new ArrayList<>();
                for (Ast.Case c : m.cases()) {
                    Answered a = c.binding() == null ? null : bind(bound, c.binding());
                    InForce inArm = a == null ? bound : a.bound();
                    cases.add(new Hir.Case(caseNames(c.caseTypes()),
                            a == null ? null : a.binder(), expr(c.body(), inArm),
                            c.unwrapAsserts() == null ? null : names(c.unwrapAsserts()), c.pos()));
                }
                yield new Hir.Match(expr(m.scrutinee(), bound), cases, m.origin(), m.pos(), m.region());
            }
            case Ast.IntLit x -> new Hir.IntLit(x.value(), x.pos(), x.region());
            case Ast.DecimalLit x -> new Hir.DecimalLit(x.value(), x.pos(), x.region());
            case Ast.StringLit x -> new Hir.StringLit(x.value(), x.pos(), x.region());
            case Ast.BoolLit x -> new Hir.BoolLit(x.value(), x.pos(), x.region());
            case Ast.Unreachable x -> new Hir.Unreachable(x.reason(), x.pos(), x.region());
            case Ast.Neg x -> new Hir.Neg(expr(x.operand(), bound), x.pos(), x.region());
            case Ast.Binary x -> new Hir.Binary(binOp(x.op()),
                    expr(x.left(), bound), expr(x.right(), bound), x.origin(), x.pos(), x.region());
            case Ast.If x -> new Hir.If(expr(x.cond(), bound), expr(x.then(), bound),
                    expr(x.els(), bound), x.origin(), x.pos(), x.region());
            case Ast.ListLit x -> bound.readingAFixture()
                    ? new Hir.RowCollection(exprs(x.elements(), bound), x.origin(), x.pos(),
                            x.region())
                    : new Hir.ListLit(exprs(x.elements(), bound), x.origin(), x.pos(), x.region());
            case Ast.ListComp x -> new Hir.ListComp(expr(x.element(), bound),
                    exprs(x.guards(), bound), x.origin(), x.pos(), x.region());
            case Ast.Tuple x -> new Hir.Tuple(exprs(x.elements(), bound), x.pos(), x.region());
            case Ast.TupleGet x -> new Hir.TupleGet(expr(x.tuple(), bound), x.index(), x.arity(),
                    x.pos(), x.region());
        };
    }

    /**
     * Which operator a written one is, in the vocabulary below this boundary.
     *
     * <p>An operator the parser can write and an operator the rest of the compiler gives a meaning
     * to are separate types on purpose, and a written one becomes a meant one here or nowhere. Both
     * sides are spelled out so that an operator added to what may be written stops the compile
     * until somebody says which meaning it denotes — including when the two are given the same
     * name, which says how they are typed and nothing about what they denote. The same reason
     * {@code AstBuilder} writes out what each piece of syntax is an operator for.
     *
     * <p>The switch is an expression and has no {@code default} for that reason. A {@code default}
     * would answer for an operator nobody had decided about, which is the whole of what this stops.
     */
    private static BinOp binOp(Ast.BinOp op) {
        return switch (op) {
            case EQ -> BinOp.EQ;
            case NE -> BinOp.NE;
            case LT -> BinOp.LT;
            case LE -> BinOp.LE;
            case GT -> BinOp.GT;
            case GE -> BinOp.GE;
            case AND -> BinOp.AND;
            case OR -> BinOp.OR;
            case ADD -> BinOp.ADD;
            case SUB -> BinOp.SUB;
            case MUL -> BinOp.MUL;
            case DIV -> BinOp.DIV;
            case CONCAT -> BinOp.CONCAT;
        };
    }

    /** A construction's field values, each a slot like any other. */
    private List<Hir.FieldInit> inits(List<Ast.FieldInit> inits, InForce bound) {
        List<Hir.FieldInit> out = new ArrayList<>();
        for (Ast.FieldInit i : inits) {
            out.add(new Hir.FieldInit(i.written(), expr(i.value(), bound)));
        }
        return out;
    }

    /** The names in a construction's spreads — a name slot, where only a name may stand. */
    private List<Hir.Var> vars(List<Ast.Var> vars, InForce bound) {
        List<Hir.Var> out = new ArrayList<>();
        for (Ast.Var v : vars) {
            out.add(name(v, bound));
        }
        return out;
    }

    /**
     * One name in the value namespace, answered against the bindings in force where it is written —
     * what this pass does at a name slot, wherever a node has one. A binding in force wins over a
     * declaration in a spread as everywhere else.
     *
     */
    private Hir.Var name(Ast.Var written, InForce bound) {
        return reached(written, bound);
    }

    /** An application of a name, with what the name denotes and how this module reaches it answered
     * here — the same pair, from the same place, as a name standing on its own. */
    private Hir.Expr applied(Ast.Apply call, Ast.Var callee, InForce bound) {
        // Answered rather than rebuilt: what the callee means is settled here and where it is
        // written is not this pass's to decide. Building one from the name would take its extent
        // from the characters that spell it, which is short of what a parenthesized callee covers.
        WrittenName written = callee.written();
        ValueName denotes = calledName(written, bound);
        Hir.Var name;
        if (denotes == null) {
            name = new Hir.Var.Unanswered(written, callee.origin(), callee.region());
        } else {
            answered(written, denotes);
            name = new Hir.Var.Denoting(written,
                    ReachName.of(denotes, written.canonical(), reachable.module()),
                    callee.origin(), callee.region());
        }
        return Hir.Apply.read(call, appliedCallee(call), name,
                exprs(call.args(), bound));
    }

    /**
     * What the author applied, as a report says it — the name they wrote in the callee position and
     * the characters they wrote it over.
     *
     * <p>Read here and nowhere later. A lowering replaces what is applied, and every one of them
     * leaves the characters where they are: a report that asked the callee afterwards would quote
     * the binding, the operation or the qualified spelling a pass put there. Which of the shapes
     * spells a name is {@link #dottedName}'s answer, which is the same one a field read written on
     * its own gets.
     *
     * <p>The name is taken from the surface and not from what the callee resolved to. A member of a
     * namespace is folded into a name and a field of a value is not, so the same source shape would
     * be read two ways depending on what it turned out to reach — and what the author wrote is not
     * a thing that turns on that.
     */
    private static Hir.AppliedCallee appliedCallee(Ast.Apply call) {
        Ast.Expr callee = call.function();
        // Where the callee is written, or where the application is where the callee says nowhere at
        // all. A report about what is applied points somewhere either way, and an application the
        // parser read is somewhere — so this is the choice between two answers already held, and
        // not a place worked out from one of them.
        Region at = callee.reportedAt();
        return new Hir.AppliedCallee(dottedName(callee),
                at != null ? at : Region.point(call.pos()));
    }

    /**
     * {@code v} with what it denotes and the name this module reaches it by, both answered here.
     *
     * <p>The two together, and only here: which module is doing the reading is what decides the reach
     * name, and this pass is the last place that has it. A pass downstream working it out from the
     * spelling would answer differently depending on which rewrites had run — which is the defect
     * this carries the answer to avoid.
     */
    private Hir.Var reached(Ast.Var v, InForce bound) {
        ValueName denotes = valueName(v.written(), bound);
        if (denotes == null) {
            return new Hir.Var.Unanswered(v.written(), v.origin(), v.region());
        }
        answered(v.written(), denotes);
        return new Hir.Var.Denoting(v.written(),
                ReachName.of(denotes, v.name(), reachable.module()), v.origin(), v.region());
    }

    private List<Hir.ElseArm> arms(List<Ast.ElseArm> arms, InForce bound) {
        List<Hir.ElseArm> out = new ArrayList<>();
        for (Ast.ElseArm arm : arms) {
            out.add(new Hir.ElseArm(arm.clause(), expr(arm.body(), bound), arm.pos()));
        }
        return out;
    }

    /** The names in a row's answer, rewritten where there is an answer to rewrite. What the row put
     *  there is carried across as it is: whether an answer is owed or was not read is the source's
     *  own state and nothing resolution has anything to say about. */
    private Hir.Expected expected(Ast.Expected written, Reading reading) {
        return switch (written) {
            case Ast.Expected.Asserted(Ast.Expr e) ->
                    new Hir.Expected.Asserted(expr(e, reading));
            case Ast.Expected.Unanswered(SourcePos at) -> new Hir.Expected.Unanswered(at);
            case Ast.Expected.Unwritten(SourcePos at) -> new Hir.Expected.Unwritten(at);
        };
    }

    private List<Hir.Expr> exprs(List<Ast.Expr> es, Reading reading) {
        return exprs(es, InForce.of(reading));
    }

    private List<Hir.Expr> exprs(List<Ast.Expr> es, InForce bound) {
        List<Hir.Expr> out = new ArrayList<>();
        for (Ast.Expr e : es) {
            out.add(expr(e, bound));
        }
        return out;
    }

    // --- names in a body ---

    /**
     * What a name written in the value namespace denotes, or null where nothing here does.
     *
     * <p>One ladder, in one order, so a name means the same thing under an application as beside one.
     * It was two — a bare name tried the declared types before this module's helpers, an applied one
     * tried the library first and the types last — and which rung answered therefore depended on
     * whether a `(` followed.
     *
     * <p>The value namespace is read before a type is read as one. A spelling reaching two of these
     * is refused where the namespace is assembled, so in a module that compiles the order decides
     * nothing — but a refusal is reported and recovered from, and what each name means afterwards
     * should not follow from which rung happened to be tried first. So a type read as a value is
     * what to do when the value namespace has no answer, and not a rung above it.
     *
     * <p>{@code applied} is the one thing the position still says. A type written as a value is the
     * construction of a unit data and records where it came from; applied, it is a newtype taking
     * what it wraps, and the application is what says that.
     */
    private Reach lookup(WrittenName name, InForce bound) {
        String written = name.canonical();
        // a binding in force wins over everything else: a body may bind a name a module declares,
        // and the binding is what the name means there
        ValueName.Local binding = bound.binderOf(written);
        if (binding != null) {
            return new Reach.Reaches(binding);
        }
        // names the language itself gives: Option's two cases
        if (written.equals("None") || written.equals("Some")) {
            return new Reach.Reaches(new ValueName.Builtin(written));
        }
        // A library qualifier makes this a library reference — `Date(...)`, whose namespace is the
        // whole name, included. Whether the library has a member of that name is the check's to say:
        // asking here would tie the answer to how much of the library has been loaded, and the
        // library resolves its own sources while it loads.
        //
        // A `private` declaration is the exception, and the names it covers arrived with the symbol
        // table rather than being asked of a library this may be in the middle of reading: while the
        // library's own sources are resolved that set is empty, which is what is true of them.
        // Split off what the author wrote, which is where a library name enters the compiler as two
        // values: the qualifier they typed and the operation they asked of it. Nothing downstream
        // splits it again — from here it is carried as the pair.
        int dot = written.lastIndexOf('.');
        if (Reserved.isQualifier(dot < 0 ? written : written.substring(0, dot))) {
            if (Reserved.isNamespace(reachable.module())
                    || !symbols.library().privateOperations().contains(written)) {
                return new Reach.Reaches(dot < 0 ? ValueName.Stdlib.namespace(written)
                        : ValueName.Stdlib.operation(written.substring(0, dot),
                                written.substring(dot + 1)));
            }
            return Reach.NOT_IN_SCOPE;
        }

        // A helper or a value of this module, a behavior it reaches, or a name an import let it
        // write without a qualifier — asked of the one table that says which, so that a reader
        // listing what may be written here reads the same answer this does.
        // What this module can name in the value namespace, which is the settled answer: its own
        // definitions and what the import lines were left with.
        Reach reached = reachable.reachIn(reaches, written);
        if (!(reached instanceof Reach.NotInScope)) {
            refuseAnAttachedValueOutsideTheRows(name, reached, bound);
            return reached;
        }
        // A type written as a value, which is the construction of what it denotes. Read after the
        // value namespace and not before it, because it is what to do when nothing there answers
        // rather than a rung of its own: a spelling the value namespace settled is settled, and a
        // type of that name is a second answer to a question already answered. Before this, a data
        // an import brought in beat a `let` written here under the same name — the collision
        // between them is reported, and what each means afterwards was decided by the order these
        // were consulted rather than by anything either says.
        if (symbols.scope().resolve(name) instanceof Denotation.Denotes d) {
            return new Reach.Reaches(new ValueName.OfType(written, d.type()));
        }
        return reached;
    }

    /**
     * Refuses a value an attached file declares where what is being read is the model.
     *
     * <p>An attached file holds the rows, the fakes they run against, and the values those rows
     * name (spec §example-placement). Its values join the module its rows join, so from resolution
     * onwards they are reachable under the same names as the model's own — and the model reaching
     * one is a model whose invariants, clauses and bodies are held up by a file of fixtures. The
     * module then does not compile without it, and a clause carrying such a name travels to an
     * importer naming something the jar has no source for.
     *
     * <p>Here rather than in a walk over the declarations that can name a value, because this is
     * the one place a name written anywhere in an expression is answered. Written as a list of the
     * positions the model can write — an invariant, an {@code ensures}, a body — it would be a
     * rule the next position to be added has to be remembered into. The {@code exposing} list is
     * the one thing this does not cover, because it is a list of names and not an expression, and
     * it is held to the same rule where it is read.
     *
     * <p>Reported and answered with all the same. What the name means is what it means, and a
     * reader told a second time that it reaches nothing would be sent after a spelling that is
     * right; the module is one whose names did not all come out, which is what stops it being
     * emitted.
     */
    private void refuseAnAttachedValueOutsideTheRows(WrittenName written, Reach reached,
                                                     InForce bound) {
        if (bound.readingAFixture() || readingAnAttachedValue
                || !(reached instanceof Reach.Reaches(ValueName.Helper helper))
                || !reachable.attachedValues().contains(helper.name())) {
            return;
        }
        unresolved.add(CompileException.of(Diagnostic.at(written.reportedAt())
                .say(new ExampleMessage.TheModelNamesAValueAnAttachedFileDeclares(helper.name()))
                .hint(new ExampleMessage.MoveTheValueIntoTheModuleItself(helper.name()))
                .build()));
        failed++;
    }

    /**
     * The callee of an application that does not apply a bare name.
     *
     * <p>A field read in this position may be a qualified name — {@code Map.empty(k)},
     * {@code up.Amount(n)} — and it is answered here rather than as a value, because applied is
     * what the position says: a type written as a value is a unit data's construction, and applied
     * it is a newtype taking what it wraps. Anything else is the expression it is, and what may be
     * applied is the check's to say.
     */
    private Hir.Expr callee(Ast.Expr function, InForce bound) {
        if (function instanceof Ast.FieldAccess fa) {
            Hir.Var name = qualifiedName(fa, bound);
            if (name != null) {
                return name;
            }
        }
        return expr(function, bound);
    }

    /**
     * A chain of names read as one qualified name — {@code Map.empty}, {@code up.Amount},
     * {@code probe.a.Amount}, where the module's own name is dotted — or null where it is an
     * ordinary field read.
     *
     * <p>Only where the chain's root is unbound. A parameter or a {@code let} named {@code Map}
     * makes {@code Map.empty} that binding's {@code empty} field, resolved the ordinary way, and no
     * qualified name is produced.
     *
     * <p>The whole chain is asked first, and a chain that answers nothing is left for the caller to
     * take apart — so {@code probe.a.defaultAmount.value} folds {@code probe.a.defaultAmount} and
     * reads {@code .value} off it. What is reported, and where, is
     * {@link #unknownMember}'s to say: a member of a namespace that has no such member is named in
     * full, and a chain rooted at a name nothing declares is reported at that name.
     *
     * <p>Positioned at the root, so what a reader asks about covers every token of the name.
     */
    private Hir.Var qualifiedName(Ast.FieldAccess fa, InForce bound) {
        Ast.Var root = rootName(fa);
        if (root == null || bound.binderOf(root.name()) != null) {
            return null;
        }
        WrittenName written = dottedName(fa);
        switch (lookup(written, bound)) {
            case Reach.Reaches(ValueName denotes) -> {
                ValueName resolved = answered(written, denotes);
                // The chain is one reference of the source and the root is where it starts, so
                // which reference it is is the root's answer.
                return new Hir.Var.Denoting(written,
                        ReachName.of(resolved, written.canonical(), reachable.module()),
                        root.origin(), written.region());
            }
            // A qualified spelling an import line was to bring in and could not. Said on that line
            // already, so the chain is answered here rather than taken apart and reported again.
            case Reach.StandsForNothing _ -> {
                unanswered();
                return new Hir.Var.Unanswered(written, root.origin(), written.region());
            }
            case Reach.NotInScope _ -> {
                return unknownMember(fa, written, bound);
            }
        }
    }

    /**
     * The report for a chain whose spelling denotes nothing, or null where there is nothing to say
     * here and the chain is taken apart instead.
     *
     * <p>Something is said only where the part in front is a namespace: {@code probe.a.NoSuch}
     * names a module that exists and a member it has not got, and reporting the root {@code probe}
     * as an unknown identifier would send the author after a module name that is right. Where the
     * front is not a namespace — {@code unknown.member} — nothing is said, and the root is reported
     * as the unknown identifier it is once the chain is read as the field access it turned out to
     * be.
     */
    private Hir.Var unknownMember(Ast.FieldAccess fa, WrittenName written, InForce bound) {
        WrittenName qualifier = dottedName(fa.target());
        if (qualifier == null || !isNamespace(qualifier.canonical())) {
            return null;
        }
        nothing(unknownIdentifier(written, bound));
        Ast.Var root = rootName(fa);
        return new Hir.Var.Unanswered(written, root == null ? null : root.origin(),
                written.region());
    }

    /** Whether {@code qualifier} names a namespace a member may be reached through: a
     *  standard-library one, or a module of this compilation (or an alias for one). */
    private boolean isNamespace(String qualifier) {
        return Reserved.isQualifier(qualifier) || symbols.scope().moduleOfQualifier(qualifier) != null;
    }

    /** The name a chain of field reads is rooted at, or null where it is rooted at anything else —
     *  a call's result, a parenthesised expression — which no qualified name can be. */
    private static Ast.Var rootName(Ast.Expr e) {
        return switch (e) {
            case Ast.Var v -> v;
            case Ast.FieldAccess fa -> rootName(fa.target());
            default -> null;
        };
    }

    /** The dotted spelling of a chain of names, or null where it is not one. */
    private static WrittenName dottedName(Ast.Expr e) {
        return switch (e) {
            case Ast.Var v -> v.written();
            case Ast.FieldAccess fa -> {
                WrittenName target = dottedName(fa.target());
                yield target == null ? null : target.then(fa.name());
            }
            default -> null;
        };
    }

    /** What a name used as a value denotes, or null where nothing does — reported here. */
    private ValueName valueName(WrittenName written, InForce bound) {
        return switch (lookup(written, bound)) {
            case Reach.Reaches(ValueName named) -> named;
            // Already accounted for on the import line that could not bring it in. Counted, so the
            // module is not emitted, and said nothing about, so the author is not sent to a body
            // where nothing is wrong.
            case Reach.StandsForNothing _ -> unanswered();
            case Reach.NotInScope _ -> nothing(unknownIdentifier(written, bound));
        };
    }

    /**
     * The same, for the name an application applies.
     *
     * <p>How the lookup is made differs — an application may reach what a bare name may not — and
     * what a miss means does not. A name that resolved to nothing resolved to nothing, and the
     * position it was written in is a fact about the source rather than about the name.
     */
    private ValueName calledName(WrittenName applied, InForce bound) {
        return switch (lookup(applied, bound)) {
            case Reach.Reaches(ValueName named) -> named;
            case Reach.StandsForNothing _ -> unanswered();
            case Reach.NotInScope _ -> nothing(unknownIdentifier(applied, bound));
        };
    }

    /**
     * Records what a name used as a value was answered with, and hands it back.
     *
     * <p>A name that denotes a type — a unit data written as a value, a newtype applied to what it
     * wraps — is a use of that type as much as one written in a field's type is, and is recorded as
     * one too. Otherwise renaming the type would rewrite every other mention of it and leave these,
     * which is a rename that stops the workspace compiling.
     *
     * <p>A name nothing answered to is recorded nowhere. What this collects is what the pass worked
     * out, and it did not work that one out: the name it carries stands for the absence so that the
     * traversal can go on past it, and a reader handed that name back would be told a binding is
     * there under a spelling nothing binds.
     */
    private ValueName answered(WrittenName written, ValueName denotes) {
        if (written.pos() == null) {
            return denotes;
        }
        values0.add(new ValueUse(written, denotes));
        if (denotes instanceof ValueName.OfType named) {
            denotations.add(new TypeUse(written, named.type()));
        }
        return denotes;
    }

    /**
     * Records that a name in a body denotes nothing, and answers with nothing.
     *
     * <p>The name that carries this is {@link Ast.Var.Unanswered}, built where the reference is:
     * a stand-in identity handed back here would say a binding is there under a spelling nothing
     * binds, and every reader below would have to know not to believe it.
     */
    private ValueName nothing(CompileException why) {
        unresolved.add(why);
        failed++;
        return null;
    }

    /**
     * Records that a name in a body reached nothing, where what is wrong has already been said.
     *
     * <p>Counted and not reported. A name an import line was to bring in is in scope reaching
     * nothing, and the line it came from is where the author was told — so the module is one whose
     * names did not all come out, which is what {@code failed} is, and nothing further is said
     * about a body that is not what is wrong.
     */
    private ValueName unanswered() {
        failed++;
        return null;
    }

    /** The names a body could have written where it wrote one nothing answers to. */
    private List<String> reachable(InForce bound) {
        List<String> names = new ArrayList<>(bound.byName().keySet());
        names.addAll(reachable.helpers().keySet());
        names.addAll(reachable.behaviors().keySet());
        return names;
    }

    /**
     * The report for a spelling under a library qualifier that the library has no member for, or
     * null where the spelling is not one. A {@code private} declaration lands here: from outside the
     * reserved namespace the library has no such member, which is what a caller is told — the same
     * answer a misspelling gets, because from where the caller stands they are the same thing.
     */
    private CompileException notALibraryMember(WrittenName written) {
        String name = written.canonical();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || !Reserved.isQualifier(name.substring(0, dot))) {
            return null;
        }
        return CompileException.of(Diagnostic
                        .at(written.reportedAt()).say(new NameMessage.NotAStandardLibraryFunction(written.quoted())).build());
    }

    private CompileException unknownIdentifier(WrittenName written, InForce bound) {
        String name = written.canonical();
        if (name.equals("null")) {
            return CompileException.of(Diagnostic.at(written.reportedAt()).say(new DeclarationMessage.NullIsNotPartOfTheLanguage()).build());
        }
        CompileException notALibraryMember = notALibraryMember(written);
        if (notALibraryMember != null) {
            return notALibraryMember;
        }
        // Asked here as well as of a call, because what the library publishes is not only functions:
        // `Map.empty` is a value, and a function's own name is a value where it is handed over. A
        // nearby binding is the wrong answer for any of them — the name exists and is reached.
        CompileException bareLibraryName = StdlibNames.writtenBare(symbols.library(),
                written.quoted(), name, written.region());
        if (bareLibraryName != null) {
            return bareLibraryName;
        }
        List<String> candidates = reachable(bound);
        Diagnostic.Builder report = Diagnostic
                .at(written.reportedAt())
                .suggestion(Suggest.candidate(name, candidates));
        // A name another module of this compilation exposes is the one kind of unresolved name that
        // has somewhere to go, and it is what a name left off an import list looks like from here.
        // Said as what is known — that module has it — rather than as an instruction, since reaching
        // it qualified needs no import at all.
        String elsewhere = written.canonical().indexOf('.') < 0
                ? symbols.scope().moduleExposing(name) : null;
        if (elsewhere != null) {
            report = report.hint(new NameMessage.ItIsExposedByAnotherModule(elsewhere, name));
        }
        return CompileException.of(report.say(new NameMessage.NoValueOfThatNameInScope(written.quoted())).build());
    }


    /**
     * What is in force where an expression is written: the names bound over it, each with the
     * binding it is, and the reading it is being read under. Persistent: extending it leaves the
     * outer scope as it was, which is what an inner binding shadowing an outer one is.
     *
     * <p>The reading travels with the bindings because it is in force the same way — it holds over
     * everything written under the position that settled it, and nothing written there changes it.
     * It is carried rather than remembered so that what a value is read as is an argument of the
     * read and not a state the reader is left in: three rules turn on it, and each of them asks the
     * value it was handed.
     */
    record InForce(Map<String, ValueName.Local> byName, Reading reading) {

        /** Nothing bound yet, under this reading — where a read of a written value starts. */
        static InForce of(Reading reading) {
            return new InForce(Map.of(), reading);
        }

        InForce and(String name, ValueName.Local binding) {
            Map<String, ValueName.Local> next = new HashMap<>(byName);
            next.put(name, binding);
            return new InForce(Map.copyOf(next), reading);
        }

        ValueName.Local binderOf(String name) {
            return byName.get(name);
        }

        /** Whether what is being read is a fixture. */
        boolean readingAFixture() {
            return reading == Reading.A_FIXTURE;
        }
    }

    // --- names ---

    private List<Hir.Name> names(List<Ast.Name> names) {
        List<Hir.Name> out = new ArrayList<>();
        for (Ast.Name n : names) {
            out.add(type(n));
        }
        return out;
    }

    private static Hir.Name denoting(Ast.Name n, TypeSymbol type) {
        return new Hir.Name.Denoting(n.name(), type);
    }

    private static Hir.Name unanswered(Ast.Name n) {
        return new Hir.Name.Unanswered(n.name());
    }

    /** A name that must denote a declared type. */
    private Hir.Name type(Ast.Name n) {
        return answered(switch (symbols.scope().resolve(n.name())) {
            case Denotation.Denotes d -> denoting(n, d.type());
            // In scope standing for nothing: a name an import line could not bring in takes the
            // error type rather than being reported as an unknown name at every use. The import
            // line is where that was reported, so nothing more is said here.
            case Denotation.StandsForNothing ignored -> unanswered(n);
            case Denotation.NotInScope ignored -> nothingDenotes(n);
        });
    }

    /** The names a {@code match} arm may write: a declared case, a primitive heading a union
     * ({@code Int} in {@code Int | DivisionByZero}), a runtime error case, or one of Option's two.
     * A declared type wins over Option's names, so a model may still declare {@code Some}. */
    private List<Hir.Name> caseNames(List<Ast.Name> names) {
        List<Hir.Name> out = new ArrayList<>();
        for (Ast.Name n : names) {
            out.add(caseName(n));
        }
        return out;
    }

    private Hir.Name caseName(Ast.Name n) {
        return answered(switch (symbols.scope().resolveCase(n.name())) {
            case Denotation.Denotes d -> denoting(n, d.type());
            case Denotation.StandsForNothing ignored -> unanswered(n);
            case Denotation.NotInScope ignored -> {
                TypeSymbol option = TypeSymbol.optionCase(n.written());
                yield option != null ? denoting(n, option) : nothingDenotes(n);
            }
        });
    }

    /**
     * What a reference denotes, or {@link souther.compiler.types.Type#ERRONEOUS} when nothing does.
     *
     * <p>This pass is where a failure becomes a value. {@link TypeOps#denoted} answers or says it
     * cannot, because it is asked one reference at a time and has nowhere to put a report; here there
     * is somewhere to put it, and a tree to carry on resolving.
     */
    private Type typeOf(TypeOps.Reference ref) {
        try {
            return TypeOps.denoted(ref, symbols);
        } catch (CompileException e) {
            failed++;
            unresolved.add(e);
            return Type.ERRONEOUS;
        }
    }

    /** Reports that nothing declares {@code n}, and hands back the name that says so. */
    private Hir.Name nothingDenotes(Ast.Name n) {
        unresolved.add(TypeOps.unknownType(n.name(), symbols));
        return unanswered(n);
    }

    /** Records what a name was answered with, and hands it back. A name with no position was
     * synthesized by an earlier pass rather than written, so there is nothing to point at, and a
     * name nothing answered is an absence rather than a declaration to record. */
    private Hir.Name answered(Hir.Name n) {
        if (!(n instanceof Hir.Name.Denoting names)) {
            failed++;
        } else if (n.pos() != null) {
            denotations.add(new TypeUse(n.name(), names.type()));
        }
        return n;
    }
}
