package souther.compiler.check;

import souther.compiler.stdlib.Stdlib;
import souther.compiler.Reserved;
import souther.compiler.ast.Ast;
import souther.compiler.ast.Hir;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.Denotation;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the names a module writes can mean, worked out against a {@link ModuleUniverse}.
 *
 * <p>The one place a module's name-resolution environment is derived. A universe supplies facts
 * about modules — what it has under a name — and everything after that is here: what a bare type
 * name denotes, which module a qualifier stands for, what a bare name in the value namespace
 * reaches, and what an import line could not do.
 *
 * <p>Written this way because the same rules were being applied twice, once by a compilation
 * reading its own sources and once by a reader putting published classes back together, and each
 * was right on its own. What that cost was a module that compiled in the project that wrote it and
 * refused to be imported anywhere else: the two readings disagreed about what one import line
 * brought in, and nothing was written down that said they had to agree. So which universe answered
 * is the only thing that differs between the two; what is done with the answers is this.
 *
 * <p>Nothing here reports. An import line that names a module nothing has is a {@link Refusal}, and
 * what to do about one is the caller's: a compilation says it to the author, and a reader that is
 * only reading takes it as this module not being readable here. A walk that made diagnostics could
 * not be used by the second reader without saying things to nobody.
 */
public final class Scoping {

    private Scoping() {}

    /**
     * What a module is resolved against, in {@code universe}.
     *
     * <p>Only a module the universe can read has a scope. One it has and cannot read has no
     * declarations to give, and a scope assembled around the hole would say what a name means in a
     * module nothing can say anything about.
     */
    public static Scoped of(ModuleUniverse universe, Subject subject) {
        Ast.Module m = subject.module();
        List<Refusal> refused = new ArrayList<>();
        Map<String, String> aliases = new LinkedHashMap<>();
        Map<String, Ast.Def> ownData = subject.declared().declarations();
        // What this module writes for itself, by the namespace each spelling reaches. A data is in
        // both — it is a type and the construction of one — and a `let` or a behavior is a value
        // and nothing in the type namespace, which is what lets one of each under a spelling come
        // apart after the collision between them has been said.
        Set<String> ownValues = new LinkedHashSet<>(ownData.keySet());
        ownValues.addAll(behaviorNames(m));
        for (Ast.FnDef fn : m.fns()) {
            ownValues.add(fn.name());
        }

        // Every claim on a spelling, whichever kind of line made it. Which provider a claim came
        // from is what makes it, and settles nothing after that: two lines asking for one spelling
        // are one contest, and reading the library ones apart from the rest is how `map` could
        // arrive from a library import and a user module at once with nobody saying so.
        //
        // In the order the lines are written, and not in the order the two sets of claims were
        // gathered. Which line a contest is reported at, and which is shown as the one that has
        // the name already, is read off this — so gathered order would put the library line first
        // whatever the author wrote.
        List<Claim> claims = new ArrayList<>(subject.libraryClaims());
        claims.addAll(claimsOf(universe, m, aliases, refused));
        claims.sort(IN_WRITTEN_ORDER);
        ResolvedImports imports =
                adjudicate(claims, ownData.keySet(), ownValues, refused);

        Map<String, Denotation> denotations = new LinkedHashMap<>();
        for (Ast.Def own : ownData.values()) {
            denotations.put(own.name(),
                    new Denotation.Denotes(TypeSymbols.declared(own.declaredKey())));
        }
        denotations.putAll(imports.types());

        Resolve.Values values = new Resolve.Values(
                reachable(universe, subject, imports), new OfTheUniverse(universe));
        refused.addAll(oneSpellingTwice(subject, ownData));
        return new Scoped(new Meanings(m.name(), denotations, aliases), values, imports, refused);
    }

    /**
     * The order an author reads the lines in, which is the order they are written in.
     *
     * <p>A line the author did not write is last. An import synthesized from a qualified reference
     * stands at the module header, which is before every line that was written, and showing it as
     * the one that has a name already would name a line nobody can go and look at.
     *
     * <p>Asked of the positions and not of their numbers. What comes before what is one question
     * with one answer ({@link SourcePos#isBefore}), and a comparator spelling it again out of a line
     * and a column is a second account of it that a change to either would leave behind.
     */
    private static final Comparator<Claim> IN_WRITTEN_ORDER = (one, other) -> {
        SourcePos here = one.imp().pos();
        SourcePos there = other.imp().pos();
        if (here == null || there == null) {
            return here == there ? 0 : here == null ? 1 : -1;
        }
        return SourcePos.IN_WRITTEN_ORDER.compare(here, there);
    };

    /**
     * One import line asking for one spelling.
     *
     * <p>Written down before anything is decided, because deciding needs all of them. A spelling
     * two lines ask for is settled by what the two would bring and not by which was read first —
     * read first-wins, the same two lines in the other order give one mistaken program two
     * meanings, and every pass below reads whichever meaning the order happened to give.
     */
    public sealed interface Claim {

        /** The line this was written on. */
        Ast.Import imp();

        /** The spelling it asks for. */
        String name();

        /** Whether an author wrote this name on an import list.
         *
         * <p>A name that was not is one a qualified reference asked for — {@code app.third.quote}
         * reaches into that module whether or not a line says so, and an import is synthesized to
         * record the dependency. It claims no bare spelling: the reference names its module, so
         * there is nothing for it to be ambiguous with, and a contest it took part in would be
         * reported on a line nobody wrote. */
        boolean written();

        /** The line asked, and the module it names hands this over. */
        record Stands(Ast.Import imp, String name, boolean written, Brought what)
                implements Claim {}

        /** The line asked, and there is nothing to hand over — the claim does not stand at all.
         *  Told apart from a claim that stands and is not adopted, which is a decision between
         *  claims rather than a fact about this one. */
        record DoesNot(Ast.Import imp, String name, boolean written) implements Claim {}
    }

    /**
     * What one import line would bring in under one spelling.
     *
     * <p>Two claims bring the same thing when these are equal, which is what decides whether a
     * spelling is claimed twice or claimed once by two lines. Compared as what is brought and not
     * as which line brought it: importing one declaration twice is one meaning written twice, and
     * an author who wrote the line twice has made no ambiguity.
     */
    public sealed interface Brought {

        /** A data, which is a name in the type namespace and a construction in the value one. */
        record ADeclaration(TypeSymbol type) implements Brought {}

        /** A behavior, which a reader calls. */
        record ABehavior(ValueName.Behavior name) implements Brought {}

        /** A definition the module hands over, which a reader writes bare.
         *
         * <p>Carries the leave the reading granted and not a name made from the spelling. A leave
         * that survives the contest is a leave the reader may redeem, so what may be taken from
         * that module afterwards is what was settled here — asked of the module again instead, the
         * answer would be what it publishes rather than what this module was left with. */
        record AHelper(ModuleUniverse.InSight.Read.PublishedHelper leave) implements Brought {}

        /** An operation of the standard library, which an {@code import List ( map )} line lets a
         *  reader write without its qualifier. */
        record ALibraryOperation(ValueName.Stdlib name) implements Brought {}
    }


    /**
     * Every claim this module's import lines make, and the refusals that are about the lines
     * themselves rather than about any contest between them.
     *
     * <p>An import line that is wrong is refused and its claims do not stand; the ones that are
     * fine still bring in what they bring in. A half-typed import is as ordinary as a half-typed
     * name, and taking the whole scope away would leave every name in the file meaning nothing —
     * which is when an author most wants to be told what one means.
     */
    private static List<Claim> claimsOf(ModuleUniverse universe, Ast.Module m,
                                        Map<String, String> aliases, List<Refusal> refused) {
        List<Claim> claims = new ArrayList<>();
        for (Ast.Import imp : importsOf(universe, m)) {
            ModuleUniverse.InSight.Read there;
            switch (universe.module(imp.module())) {
                case ModuleUniverse.InSight.Read read0 -> there = read0;
                // Being part of this universe and being part of it while saying nothing usable are
                // different things, and only the second is the importer's business. Whatever is
                // wrong with a module that is here is answered where it is; saying it again here
                // sends the author to a file that is fine.
                case ModuleUniverse.InSight.Unreadable _ -> there = null;
                case ModuleUniverse.InSight.Unknown _ -> {
                    refused.add(new Refusal.NoSuchModule(imp));
                    there = null;
                }
            }
            if (there == null) {
                claims.addAll(none(imp));
                continue;
            }
            if (imp.alias() != null) {
                String taken = aliasTakenBy(universe, imp.alias(), aliases);
                if (taken != null) {
                    refused.add(new Refusal.AliasTaken(imp, taken));
                    claims.addAll(none(imp));   // an alias that names two things names neither here
                    continue;
                }
                aliases.put(imp.alias(), imp.module());
            }
            for (Ast.ImportedName imported : imp.importedNames()) {
                Claim made = claim(imp, imported, there, refused);
                if (made != null) {
                    claims.add(made);
                }
            }
        }
        return claims;
    }

    /** What one name on one import line asks for, of a module that can answer. */
    private static Claim claim(Ast.Import imp, Ast.ImportedName named,
                               ModuleUniverse.InSight.Read there, List<Refusal> refused) {
        String imported = named.text();
        boolean written = named.pos() != null;
        if (!there.exposes(imported)) {
            refused.add(new Refusal.NotExposed(imp, imported));
            return new Claim.DoesNot(imp, imported, written);
        }
        Ast.Def declared = there.declaration(imported);
        if (declared != null) {
            return new Claim.Stands(imp, imported, written,
                    new Brought.ADeclaration(TypeSymbols.declared(declared.declaredKey())));
        }
        if (there.declaresBehavior(imported)) {
            return new Claim.Stands(imp, imported, written,
                    new Brought.ABehavior(new ValueName.Behavior(imp.module(), imported)));
        }
        java.util.Optional<ModuleUniverse.InSight.Read.PublishedHelper> published =
                there.publishedHelper(imported);
        if (published.isPresent()) {
            return new Claim.Stands(imp, imported, written,
                    new Brought.AHelper(published.get()));
        }
        if (there.declaresValue(imported)) {
            // Declared and exposed, and nothing to hand over. Nothing is wrong with the line and
            // nothing arrived, so no claim is made on the spelling at all — it is not in scope
            // here, and a use of it is a name this module never had.
            return null;
        }
        refused.add(new Refusal.NoSuchName(imp, imported));
        return new Claim.DoesNot(imp, imported, written);
    }

    /** Every name a line asks for, claimed by nothing — the line could not do its job at all. */
    private static List<Claim> none(Ast.Import imp) {
        List<Claim> claims = new ArrayList<>();
        for (Ast.ImportedName name : imp.importedNames()) {
            claims.add(new Claim.DoesNot(imp, name.text(), name.pos() != null));
        }
        return claims;
    }

    /**
     * What each spelling the import lines claim means here.
     *
     * <p>Counted in distinct things brought and not in claims made. Two lines importing one
     * declaration have written one meaning twice, and an author who did that has made no ambiguity
     * to resolve; two lines bringing different things have, and neither wins — a spelling that
     * means two things means neither, and choosing by the order the lines were read would give the
     * same mistaken program two meanings.
     *
     * <p>A declaration of this module's own beats every claim on its spelling, and the line that
     * made the claim is told so. It beats it where it is, though, and not everywhere: a
     * {@code let} of a name and a data brought in under it are one spelling in two namespaces, and
     * taking the data away as well would report a type nothing declares at every field written
     * with it — a second thing said about the one line already refused. Which namespace a
     * declaration holds is what the projections read, and each keeps its own.
     */
    private static ResolvedImports adjudicate(List<Claim> claims, Set<String> ownTypes,
                                              Set<String> ownValues, List<Refusal> refused) {
        Map<String, List<Claim>> byName = new LinkedHashMap<>();
        for (Claim each : claims) {
            if (each.written()) {
                byName.computeIfAbsent(each.name(), k -> new ArrayList<>()).add(each);
            }
        }
        Map<String, ResolvedImport> settled = new LinkedHashMap<>();
        byName.forEach((spelling, made) -> {
            ResolvedImport.Held held = new ResolvedImport.Held(
                    ownTypes.contains(spelling), ownValues.contains(spelling));
            Map<Brought, Ast.Import> distinct = new LinkedHashMap<>();
            for (Claim each : made) {
                if (each instanceof Claim.Stands(Ast.Import imp, String _, boolean _,
                        Brought what)) {
                    if (held.asAValue() || held.asAType()) {
                        refused.add(new Refusal.CollidesWithADeclaration(imp, spelling));
                    }
                    distinct.putIfAbsent(what, imp);
                }
            }
            if (distinct.size() > 1) {
                List<Ast.Import> lines = new ArrayList<>(distinct.values());
                for (Ast.Import imp : lines.subList(1, lines.size())) {
                    refused.add(new Refusal.BroughtTwice(imp, spelling, lines.get(0)));
                }
                settled.put(spelling, new ResolvedImport.BringsNothing(held));
                return;
            }
            if (distinct.size() == 1) {
                settled.put(spelling,
                        new ResolvedImport.Brings(distinct.keySet().iterator().next(), held));
                return;
            }
            // Every claim on it failed. The name is in scope meaning nothing, so a use of it takes
            // the error type and says nothing more; left out of scope, every use would be reported
            // as an unknown name, which sends the author to a body when what is wrong is the line.
            settled.put(spelling, new ResolvedImport.BringsNothing(held));
        });
        return new ResolvedImports(settled);
    }

    /**
     * The module a scope is being assembled for, and what only it needs.
     *
     * <p>The module itself is here and not in the universe. A module being scoped is the one this
     * reads the syntax of — its import lines, its bodies, the names its declarations write — and
     * that is a capability nothing has over any other module: what a universe answers about a
     * neighbour is what was settled about it ({@link ModuleUniverse.InSight.Read}), asked one name
     * at a time, so no walk can work the same facts out a second way. Its own declaration index is
     * here for the same reason and from the other side — reading every one of them is what a
     * module may do to itself and to nothing else.
     *
     * <p>One value, because the parts cannot be told apart afterwards. The
     * {@code import List ( map )} lines are dropped once read ({@link Exposing}), so what they
     * brought in outlives them and travels with the module or is lost — read as a second answer it
     * was answered emptily for a module off the class path, and every bare name in a published
     * invariant then denoted nothing.
     *
     * <p>Told apart from what the universe says about the modules around it, because it is a fact
     * about here and not about them. A reader of another module never asks what that module may
     * write bare, and a universe that answered it anyway would put every importer's scope behind an
     * edit to a library import line in a module it imports from.
     */
    public record Subject(Ast.Module module, Registry.Declared<Ast.Def> declared,
                          List<Claim> libraryClaims) {

        public Subject {
            libraryClaims = List.copyOf(libraryClaims);
        }
    }

    /**
     * What a module is resolved against: what each name it writes can mean, and what an import line
     * could not do.
     *
     * <p>One value for both namespaces, because a name written in a module means one thing and the
     * question of what that is was being asked twice — once of the type namespace and once of the
     * value namespace — with the import lines walked once for each. A spelling that arrives in both
     * is a clash, and a walk that only ever saw one of them could not say so.
     *
     * <p>The table and the way of asking the universe a further question are here as one pair
     * rather than the table alone. The universe is not a free axis — it is what this was assembled
     * against — so a caller that had to put the two together could put a table with a second
     * universe, leaving what an import brought in decided by one and what a qualifier names by the
     * other. The declaration registry is handed over at the point of use for the opposite reason:
     * one compilation genuinely reads one scope against three stages of its declarations.
     *
     * <p>{@link Meanings} is the part of it that is a value on its own, and a reader wanting only
     * to know what a name means here is handed that rather than this.
     */
    public record Scoped(Meanings meanings, Resolve.Values values,
                         ResolvedImports imports, List<Refusal> refused) {

        /**
         * Copied, for the reason {@link Exposing.Checked} is: this is an answer a compilation
         * remembers, and an answer it remembers is a value.
         *
         * <p>This one is not one yet. Two of these assembled from one input are unequal, because
         * the way of asking the universe a further question holds the store it asks — which is a
         * capability in an answer, and is on the register
         * {@code EverythingAnAnswerHoldsMeansSomethingTest} keeps.
         */
        public Scoped {
            refused = List.copyOf(refused);
        }

        /** The module being scoped. */
        public String module() {
            return meanings.module();
        }

        /** What each type spelling written here denotes. */
        public Map<String, Denotation> denotations() {
            return meanings.denotations();
        }

        /** What each {@code import ... as} alias reaches. */
        public Map<String, String> aliases() {
            return meanings.aliases();
        }

        /** What this module can name in the value namespace, on its own. What a reader wanting to
         *  know what may be written here asks for; the pair is what the resolve pass is given. */
        public Resolve.Reachable reachable() {
            return values.reachable();
        }
    }

    /**
     * What the names written in a module mean: what each type spelling denotes, and what each alias
     * reaches.
     *
     * <p>Apart from {@link Scoped} because it is the part of a scope that is a value and nothing
     * else. What a module is resolved against is this together with the value namespace and a way
     * of asking the universe a further question, and those two are not values of this kind — one
     * carries a way of reading the modules around this one, which is a thing to do rather than a
     * thing to compare. A reader that only wants to know what a name means here is handed this, and
     * two of them coming out the same is what lets everything below stop.
     *
     * <p>Which is what a behavior being declared shows: the value namespace gains a name and this
     * does not, because a behavior is not a type. Read as part of the whole assembly, that edit
     * arrived at every reader of what the names mean; read as this, it arrives at none of them.
     *
     * <p>A type being declared does change this, and reaches whoever read it. Which readers those
     * are is {@link Denoting}'s doing rather than this value's: a scope asks for these the first
     * time it is read, so declaring a type reaches the bodies that write its spelling and the
     * reports that are read off every name in sight, rather than everything built over a scope.
     *
     * <p>The three parts are here together and not handed over one at a time, for the reason
     * {@link Scoped} gives: the module name, the scope and the aliases come out of one assembly and
     * mean nothing apart from each other, so a caller putting them together could pair parts of two.
     * The declaration registry is the one axis that stays free, because one compilation genuinely
     * reads one set of meanings against its written, its resolved and its derived declarations.
     */
    public record Meanings(String module, Map<String, Denotation> denotations,
                           Map<String, String> aliases) {

        public Meanings {
            denotations = Collections.unmodifiableMap(new LinkedHashMap<>(denotations));
            aliases = Collections.unmodifiableMap(new LinkedHashMap<>(aliases));
        }

        /** What {@code Resolve} reads this module against, over the declarations as they were
         *  written. */
        public SyntaxSymbols writtenSymbols(Registry<Ast.Def> registry, Stdlib stdlib) {
            return SyntaxSymbols.of(module, registry, denoting(), stdlib);
        }

        /** The same over a stage of the declarations something has resolved. */
        public ResolvedSymbols symbolsOver(Registry<Hir.Def> registry, Stdlib stdlib) {
            return ResolvedSymbols.over(module, registry, denoting(), stdlib);
        }

        /** These meanings as the operations a scope performs on them — what a reader that already
         *  holds the answer is asked through, so that it and one asking a store as it reads are
         *  told apart by nothing above {@link Denoting}. */
        public Denoting denoting() {
            return Denoting.of(denotations, aliases);
        }
    }

    /**
     * A reason the scope a module asked for cannot hold a name.
     *
     * <p>Each of these carries what it is about rather than what to say about it. A compilation
     * turns them into diagnostics where it reads the result; a reader that is only reading a module
     * takes one as the module not being readable here. Values because the second reader has to tell
     * them apart, and a diagnostic can only be counted.
     */
    public sealed interface Refusal {

        /** The import names a module this universe does not have. */
        record NoSuchModule(Ast.Import imp) implements Refusal {}

        /** The module is there and does not expose the name. */
        record NotExposed(Ast.Import imp, String name) implements Refusal {}

        /** The module is there and declares nothing of that name. */
        record NoSuchName(Ast.Import imp, String name) implements Refusal {}

        /** The alias is a qualifier something already answers to — another alias, a module of this
         *  universe, or the standard library. */
        record AliasTaken(Ast.Import imp, String takenBy) implements Refusal {}

        /** Two imports bring the same bare name in. {@code earlier} is the one that has it, and
         *  there is always one: an import against a declaration is the arm below. */
        record BroughtTwice(Ast.Import imp, String name, Ast.Import earlier) implements Refusal {

            public BroughtTwice {
                java.util.Objects.requireNonNull(earlier, "the import that already brought it in");
            }
        }

        /** An import brings in a name this module declares itself. */
        record CollidesWithADeclaration(Ast.Import imp, String name) implements Refusal {}

        /** A declaration takes a standard-library qualifier, which is no module's to shadow. */
        record TakesTheLibraryQualifier(Ast.Def def) implements Refusal {}

        /** A {@code let} and a data are written under one spelling. */
        record ALetAndADataShareASpelling(Ast.FnDef fn) implements Refusal {}
    }

    /**
     * What already answers to {@code alias} as a qualifier, or null where nothing does.
     *
     * <p>An alias must be a qualifier nothing else already is: another alias, a module of this
     * universe, or a standard-library qualifier. Left to win silently it would take over what
     * {@code List.map} or {@code billing.Amount} means here.
     */
    private static String aliasTakenBy(ModuleUniverse universe, String alias,
                                       Map<String, String> aliases) {
        if (aliases.containsKey(alias)) {
            return aliases.get(alias);
        }
        if (universe.module(alias).isThere()) {
            return alias;
        }
        return Reserved.isQualifier(alias) ? "souther" : null;
    }

    /**
     * What a module's bodies can name without a binding: its own helpers, the definitions its
     * imports bring in, and every behavior it can reach.
     *
     * <p>Whether the behaviors are all of them is worked out here rather than decided by whoever
     * built the universe. An import naming a module that cannot be read may have been where a bare
     * name came from, and a reader that answered "all of them" for a universe it had not finished
     * reading would report the name as denoting nothing.
     */
    private static Resolve.Reachable reachable(ModuleUniverse universe, Subject subject,
                                               ResolvedImports imports) {
        Ast.Module m = subject.module();
        // A behavior's `let` is not a helper: it implements the behavior, and the name reaches the
        // behavior. Asked the same way as HelperInliner.helpersOf, which decides what is expanded —
        // two answers to one question is how a name came to denote a helper here and a behavior
        // there.
        Set<String> behaviorNames = behaviorNames(m);
        Map<String, ValueName.Helper> helpers = new LinkedHashMap<>();
        // Which of them an attached file declares, read off the definition rather than off which
        // file's text it arrived in. The values of an attached file join the module its rows join,
        // so they are reachable under the same names as the module's own, and this is what says
        // which of those names only the rows may write.
        Set<String> attached = new LinkedHashSet<>();
        for (Ast.FnDef fn : m.fns()) {
            if (HelperInliner.isHelperName(behaviorNames, fn.name())) {
                helpers.put(fn.name(), new ValueName.Helper(m.name(), fn.name()));
                if (!fn.role().isTheModels()) {
                    attached.add(fn.name());
                }
            }
        }
        Map<String, ValueName.Behavior> behaviors = new LinkedHashMap<>();
        for (String own : behaviorNames) {
            behaviors.put(own, new ValueName.Behavior(m.name(), own));
        }
        Map<String, ValueName.Stdlib> library = new LinkedHashMap<>();
        Set<String> nothing = new LinkedHashSet<>();
        // What the lines settled, read rather than worked out again. A definition another module
        // publishes is written here bare, like one of this module's own — a value substituted at
        // its reference, a helper expanded at its call (ADR-0072).
        imports.values().forEach((spelling, reach) -> {
            switch (reach) {
                case Reach.Reaches(ValueName.Helper named) -> helpers.putIfAbsent(spelling, named);
                case Reach.Reaches(ValueName.Behavior named) ->
                        behaviors.putIfAbsent(spelling, named);
                case Reach.Reaches(ValueName.Stdlib named) -> library.putIfAbsent(spelling, named);
                case Reach.Reaches _ -> { }
                case Reach.StandsForNothing _ -> nothing.add(spelling);
                case Reach.NotInScope _ -> { }
            }
        });
        boolean whole = true;
        for (Ast.Import imp : m.imports()) {
            if (!(universe.module(imp.module()) instanceof ModuleUniverse.InSight.Read)) {
                whole = false;
            }
        }
        return new Resolve.Reachable(m.name(), helpers, behaviors, nothing, whole, library,
                attached);
    }

    /**
     * What resolution can be told about the behaviors of the modules around this one.
     *
     * <p>Asked by name while a module is being resolved, and left that way. Which modules a
     * qualified behavior reference names is settled by resolution — a qualifier is whatever the
     * author wrote — so a set worked out here would be this walk guessing what that one will ask,
     * and a guess that came up short would answer "no module of that name" for a module that is
     * there. Nothing derived is held: this is the universe, asked the questions it exists to
     * answer.
     *
     * <p>A record, and not the closure it reads like, because it is a value: two of these put the
     * same question to the same universe and are the same thing. What it is part of is remembered,
     * and an answer a compilation keeps is compared with the next one to decide whether the work
     * that read it has to be done again — so an object that never equals the last one is an answer
     * that is never kept.
     */
    record OfTheUniverse(ModuleUniverse universe) implements Resolve.Elsewhere {

        @Override
        public boolean hasModule(String name) {
            return universe.module(name).isThere();
        }

        @Override
        public Resolve.Declares declaresBehavior(String module, String name) {
            if (!(universe.module(module) instanceof ModuleUniverse.InSight.Read read)) {
                return Resolve.Declares.CANNOT_SAY;
            }
            return read.declaresBehavior(name) ? Resolve.Declares.YES : Resolve.Declares.NO;
        }

        @Override
        public Set<String> behaviorNamesToSuggest(String module) {
            return universe.module(module) instanceof ModuleUniverse.InSight.Read read
                    ? read.behaviorNamesToSuggest() : Set.of();
        }
    }

    /**
     * Every spelling that reaches this module's value namespace twice.
     *
     * <p>A data reaches it — a unit data is a value, a newtype is applied to what it wraps, a record
     * is constructed by its name — and so do a {@code let}, a behavior, a definition another module
     * publishes, and a standard-library qualifier. One name means one thing there, whichever way it
     * arrived. A spelling with two meanings is answered by whichever reader looks first, which is
     * how a name could be a unit data where it stood alone and an imported helper where it was
     * applied.
     *
     * <p>Asked here because this is where the namespace is assembled. A rule stated per arrival
     * would have to be restated for each new way in.
     *
     * <p>A behavior and a {@code let} of one name are not two: they are the declaration and the
     * implementation of one thing (ADR-0072).
     */
    private static List<Refusal> oneSpellingTwice(Subject subject, Map<String, Ast.Def> declared) {
        Ast.Module m = subject.module();
        List<Refusal> refused = new ArrayList<>();
        for (Ast.Def def : declared.values()) {
            // A standard-library qualifier is the only spelling that reaches the library, so a data
            // of that name hides it — from every module, since the qualifier is not this module's
            // to shadow. Refused where it is declared, as a reserved module name is.
            if (Reserved.isQualifier(def.name())) {
                refused.add(new Refusal.TakesTheLibraryQualifier(def));
            }
        }
        Set<String> implementing = behaviorNames(m);
        for (Ast.FnDef fn : m.fns()) {
            if (!implementing.contains(fn.name()) && declared.containsKey(fn.name())) {
                refused.add(new Refusal.ALetAndADataShareASpelling(fn));
            }
        }
        return refused;
    }

    /**
     * The imports a module has: the ones it wrote, and the ones a qualified reference in its header
     * asked for.
     *
     * <p>Naming a behavior through its module reaches into that module whether or not an import
     * line says so: the borrowed signature and the injected field are found by an import, and the
     * module is a dependency for the same reason. So it is answered here, off the spellings the
     * header writes.
     *
     * <p>Whether the behavior is there at all is not asked beyond the module declaring it. A name
     * that module does not declare is borrowed from nowhere, and that it names nothing is answered
     * where the clause is read.
     */
    public static List<Ast.Import> importsOf(ModuleUniverse universe, Ast.Module m) {
        List<Ast.Import> imports = new ArrayList<>(m.imports());
        Map<String, Set<String>> borrowed = borrowed(universe, m);
        for (Map.Entry<String, Set<String>> reached : borrowed.entrySet()) {
            Set<String> already = new LinkedHashSet<>();
            for (Ast.Import imp : m.imports()) {
                if (imp.module().equals(reached.getKey())) {
                    already.addAll(imp.names());
                }
            }
            List<Ast.ImportedName> names = new ArrayList<>();
            for (String bare : reached.getValue()) {
                if (!already.contains(bare)) {
                    // No position on a synthesized name: nobody wrote it on an import list. The
                    // qualified reference that asked for it is where it came from.
                    names.add(new Ast.ImportedName(bare, null));
                }
            }
            if (!names.isEmpty()) {
                imports.add(new Ast.Import(reached.getKey(), null, names, m.pos()));
            }
        }
        return imports;
    }

    /** The behaviors this module names through another module's name, by that module. */
    private static Map<String, Set<String>> borrowed(ModuleUniverse universe, Ast.Module m) {
        Map<String, String> qualifiers = qualifiersWritten(m);
        Map<String, Set<String>> out = new LinkedHashMap<>();
        for (Ast.Var ref : qualifiedBehaviorRefs(m)) {
            String written = ref.name();
            String target = moduleNamedBy(written, qualifiers);
            if (target == null || target.equals(m.name())
                    || !(universe.module(target) instanceof ModuleUniverse.InSight.Read read)) {
                continue;
            }
            String bare = written.substring(written.lastIndexOf('.') + 1);
            if (read.declaresBehavior(bare)) {
                out.computeIfAbsent(target, k -> new LinkedHashSet<>()).add(bare);
            }
        }
        return out;
    }

    /**
     * Each {@code import ... as} alias against the module its line names, as the source writes
     * them.
     *
     * <p>Not what a qualifier means here. An alias two lines both take is refused and the first
     * keeps it; this reads every line and the last one wins, so the two answer differently for
     * exactly the module an author was told about. What a qualifier names is
     * {@link Scoped#aliases()}, which is what the refusal was applied to.
     *
     * <p>Here for finding which modules a module reaches, which is a different question and wants
     * the wider answer: a line that was refused still names a module this one depends on, and a
     * cycle running through it is a cycle whether or not the alias stood.
     */
    public static Map<String, String> qualifiersWritten(Ast.Module m) {
        Map<String, String> qualifiers = new HashMap<>();
        for (Ast.Import imp : m.imports()) {
            if (imp.alias() != null) {
                qualifiers.put(imp.alias(), imp.module());
            }
        }
        return qualifiers;
    }

    /** The module a qualified spelling names, by its qualifier — an alias, or a module named under
     * its own name. Null where the spelling carries no qualifier. */
    public static String moduleNamedBy(String written, Map<String, String> qualifiers) {
        int dot = written.lastIndexOf('.');
        if (dot < 0) {
            return null;
        }
        String qualifier = written.substring(0, dot);
        return qualifiers.getOrDefault(qualifier, qualifier);
    }

    /** Every name the behavior namespace writes with a qualifier: a {@code >->} stage, a
     *  {@code depends on}. */
    public static List<Ast.Var> qualifiedBehaviorRefs(Ast.Module m) {
        List<Ast.Var> out = new ArrayList<>();
        for (Ast.BehaviorDef b : m.behaviors()) {
            List<Ast.Var> refs = switch (b) {
                case Ast.PipeBehavior pipe -> pipe.stages();
                case Ast.SpecBehavior spec -> spec.dependsOn();
            };
            for (Ast.Var ref : refs) {
                if (ref.name().lastIndexOf('.') >= 0 && !Resolve.namesABoundaryEdge(ref.name())) {
                    out.add(ref);
                }
            }
        }
        return out;
    }

    /** The behavior names a module declares. */
    public static Set<String> behaviorNames(Ast.Module m) {
        Set<String> names = new LinkedHashSet<>();
        for (Ast.BehaviorDef b : m.behaviors()) {
            names.add(b.name());
        }
        return names;
    }
}
