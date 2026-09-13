package souther.compiler.stdlib;

import souther.compiler.Reserved;
import souther.compiler.ast.Hir;
import souther.compiler.core.Kernel;
import souther.compiler.core.KernelSignature;
import souther.compiler.core.KernelSignatures;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedSet;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * What the standard library declares: every operation's declaration and resolved signature, which
 * names are sugar for which, what the language's own vocabulary is, and which kernels the library
 * names.
 *
 * <p>A finished value. Everything here was settled while it was being built and nothing is worked
 * out on demand, so two readers asking the same question get the same answer and neither can be
 * holding a library that is half loaded. What builds one is {@code check.StdlibLoader}, which needs
 * the resolver and the type checker to turn a {@code .sou} resource into these declarations; that
 * dependency belongs to the loader and not to what it produces, which is why the two are apart. A
 * reader that wants to know what the library declares takes this and nothing else.
 *
 * <p>Neutral about how any of it is implemented. A declaration says what an operation means in
 * Souther — its parameters, what it answers, the kernel behind it where there is one. What a
 * kernel is emitted as, and what class or module a language-declared type is represented by, is the
 * backend's and is not stated here: a second backend answers those differently for the same
 * declarations, and a fact recorded here that only one of them could honour would be a fact about
 * that backend wearing the library's name.
 *
 * <p>Keyed by qualified name, because the library publishes no bare names (spec §stdlib):
 * {@code List.map}, {@code String.trim}. A module that imports a name writes it without the
 * qualifier; what it reaches is this same entry, since an import elides the qualifier where the call
 * is written and settles nothing else.
 */
public final class Stdlib {

    /**
     * A declaration's resolved signature: its parameter types, and the success type of its declared
     * return — or null where the declaration writes no return type and leaves its result to its
     * body, which a Souther-bodied helper with parameters may and a kernel never does. A
     * zero-parameter declaration is a value and always has a result here. What the failure cases of
     * the return are is a checker's question about the call, not the signature's.
     */
    public record Signature(List<Type> params, Type result) {
        public Signature {
            params = List.copyOf(params);
        }
    }

    /** Everything the library says under one qualified name: the {@code let} as it was written and
     *  its resolved signature. Existence, declaration, signature and implementation are one answer —
     *  a reader takes the projection it wants rather than picking a map. */
    public record Entry(Hir.FnDef declaration, Signature signature) {
    }

    /**
     * What a sugared name is sugar for: the call it becomes, the arguments the rewrite supplies
     * after the ones that were written, and how many of the written ones stand where they stood.
     *
     * <p>{@code List.fold(step, seed, xs)} is {@code List.foldFrom(step, seed, xs, 0)} — three
     * written, and the index the walk starts at supplied. Said here whole because two readers need
     * different halves of it: the rewrite performs it, and anything stated of an operation is stated
     * of the sugar by the arguments that stand where they stood.
     *
     * <p>{@code keptArgs} is worked out while the library is being built, out of the target's own
     * parameter count. Asked of the library later it would make this a part of the library that
     * cannot be read without the rest of it, which is the ambient dependency this value exists to
     * end.
     *
     * <p>A sugar supplies constants and nothing else, which is why what it supplies is numbers; one
     * that had to supply anything else could not be written down as this and would say so.
     *
     * <p>The constants are not handed out. Where they go is what this says and not something a
     * caller works out from them: the arguments the author wrote keep the places they were written
     * in and the supplied ones follow, which is what makes the numbering in a report about the call
     * the author's own. Given the list, a caller could put them anywhere and every reader of a
     * position — the report, the combinator's block argument, the reduction's seed — would be
     * reading a different call from the one the author wrote. A sugar that has to supply an
     * argument somewhere else cannot be written down as this, and the day one is wanted this is
     * where it is said, beside what the numbering then means.
     */
    public static final class Rewrite {

        private final ValueName.Stdlib.Operation target;
        private final List<Integer> supplied;
        private final int keptArgs;

        Rewrite(ValueName.Stdlib.Operation target, List<Integer> supplied, int keptArgs) {
            this.target = target;
            this.supplied = List.copyOf(supplied);
            this.keptArgs = keptArgs;
        }

        /** The operation the sugared name becomes. */
        public ValueName.Stdlib.Operation target() {
            return target;
        }

        /** How many of the target's arguments a call of the sugar writes — the prefix, so that an
         *  argument the author counts to is the one the target takes at that position. */
        public int keptArgs() {
            return keptArgs;
        }

        /**
         * The arguments of the call this rewrites to: the {@code written} ones where they were
         * written, then the ones this supplies, each made by {@code supplying} from the constant it
         * is.
         *
         * <p>Generic in what an argument is because two callers want two things of it. The pass
         * writing the rewrite out wants expressions; a reader working out what a position of the
         * target a written argument lands on wants the position. Both are this same placing, and
         * a second statement of it is a second answer to where a supplied argument goes.
         */
        public <T> List<T> arguments(List<T> written, IntFunction<T> supplying) {
            if (written.size() != keptArgs) {
                throw new IllegalArgumentException("`" + target.qualified() + "` is written with "
                        + keptArgs + " argument(s) where it is sugar, and this was given "
                        + written.size());
            }
            List<T> all = new ArrayList<>(written);
            for (int constant : supplied) {
                all.add(supplying.apply(constant));
            }
            return List.copyOf(all);
        }
    }

    /** A kernel the library names, and the signature it was declared with. The kernel is the
     *  language's operation ({@link Kernel#DECIMAL_ROUND}); what a backend emits for it is that
     *  backend's, and a signature is what both of them derive their own boundary form from. */
    public record Intrinsic(Kernel kernel, KernelSignature signature) {
    }

    private final Map<ValueName.Stdlib.Operation, Entry> entries;
    private final Set<ValueName.Stdlib.Operation> privateNames;
    /** Which operation each spelling reaches — the one table here a written name is the key of, and
     *  the only way into the rest. What the library publishes an operation as is the library's, so
     *  a reader holding a spelling asks here for the operation and asks everything else with that;
     *  a reader holding an operation never comes through. */
    private final Map<String, ValueName.Stdlib.Operation> operations;
    private final Map<ValueName.Stdlib.Operation, Rewrite> sugars;
    private final Map<TypeKey, Hir.Def> language;
    /** And the same declarations by the library module that writes them, worked out once with
     *  everything else rather than gathered on each ask. */
    private final Map<String, Map<String, Hir.Def>> byModule;
    /** What the language declares of its kernels, worked out once here so that a program and a
     *  backend hold one value rather than two readings of it. Kept as the snapshot and not as the
     *  declarations it was built from: what a reader has business asking is what a kernel takes and
     *  answers, and the library is not the place that question is put. */
    private final KernelSignatures kernels;
    /** And which kernel each operation that is one reaches, so a reader holding a name asks the
     *  library rather than the declaration it would have to open to find out. */
    private final Map<ValueName.Stdlib.Operation, Intrinsic> kernelOperations;
    private final Map<ValueName.Stdlib.Operation, Hir.FnDef> helpers;
    private final SequencedSet<String> published;
    private final Map<String, List<String>> candidates;
    /** The projection a resolver takes, worked out once with everything else. A set built on each
     *  ask would be the same answer allocated again for every module of every compilation. */
    private final LibraryNames names;

    private Stdlib(Map<ValueName.Stdlib.Operation, Entry> entries,
                   Set<ValueName.Stdlib.Operation> privateNames,
                   Map<String, ValueName.Stdlib.Operation> operations,
                   Map<ValueName.Stdlib.Operation, Rewrite> sugars,
                   Map<TypeKey, Hir.Def> language, Map<Kernel, Intrinsic> intrinsics,
                   Map<ValueName.Stdlib.Operation, Intrinsic> kernelOperations,
                   Map<ValueName.Stdlib.Operation, Hir.FnDef> helpers,
                   SequencedSet<String> published,
                   Map<String, List<String>> candidates) {
        this.entries = entries;
        this.privateNames = privateNames;
        this.operations = operations;
        this.sugars = sugars;
        this.language = language;
        Map<String, TypeSymbol> identities = new LinkedHashMap<>();
        Map<String, Map<String, Hir.Def>> grouped = new LinkedHashMap<>();
        for (Map.Entry<TypeKey, Hir.Def> e : language.entrySet()) {
            TypeKey address = e.getKey();
            identities.put(address.name(), TypeSymbols.declared(address));
            grouped.computeIfAbsent(address.module(), _ -> new LinkedHashMap<>())
                    .put(address.name(), e.getValue());
        }
        grouped.replaceAll((_, defs) -> Collections.unmodifiableMap(defs));
        this.byModule = Collections.unmodifiableMap(grouped);
        Map<Kernel, KernelSignature> declared = new EnumMap<>(Kernel.class);
        intrinsics.forEach((kernel, intrinsic) -> declared.put(kernel, intrinsic.signature()));
        this.kernels = KernelSignatures.of(declared);
        this.kernelOperations = kernelOperations;
        this.helpers = helpers;
        this.published = published;
        this.candidates = candidates;
        // Rendered here and not held rendered: resolution is given spellings and answers with
        // them, which is the one place a written name is what is being asked about.
        Set<String> named = new LinkedHashSet<>();
        entries.keySet().forEach(operation -> named.add(operation.qualified()));
        sugars.keySet().forEach(operation -> named.add(operation.qualified()));
        Set<String> reserved = new LinkedHashSet<>();
        privateNames.forEach(operation -> reserved.add(operation.qualified()));
        this.names = new LibraryNames(identities, reserved, named, candidates);
    }

    /** The library's entry for {@code operation}, or null where the library declares no such
     *  operation — including for {@linkplain #sugared sugar}, which is a rewrite, not a
     *  declaration. */
    public Entry entry(ValueName.Stdlib.Operation operation) {
        return entries.get(operation);
    }

    /** Every entry, by the operation it declares, in declaration order — private ones included,
     *  because a checker and a backend still have to type and emit what they are behind. */
    public Map<ValueName.Stdlib.Operation, Entry> entries() {
        return entries;
    }

    /**
     * What a name reaching {@code qualifiedName} denotes, or null where the library has no such name.
     *
     * <p>The alias and the operation as the library wrote them, so a caller holding a qualified
     * spelling gets them back rather than splitting it. The library is the only thing that knows
     * which part is which: {@code souther.list} declares {@code foldFrom} and publishes it under
     * {@code List}, and the spelling says nothing about either.
     *
     * <p>The one question here a written name is the subject of, and so the way in for a reader
     * that has one: everything else is asked with the operation this answers with. A reader already
     * holding an operation has its answer and does not come through here.
     */
    public ValueName.Stdlib.Operation operation(String qualifiedName) {
        return operations.get(qualifiedName);
    }

    /** Whether {@code operation} is a declaration the library keeps to itself. Such a name may be
     *  written inside the reserved namespace and nowhere else, so everything outside it is told the
     *  library has no such member. */
    public boolean isPrivateMember(ValueName.Stdlib.Operation operation) {
        return privateNames.contains(operation);
    }

    /** Whether {@code operation} is a standard-library function — a declared one, or a sugar for
     *  one. Sugar has no declaration of its own, so this is wider than {@link #entry} answering:
     *  {@code List.fold} is a library function and has no entry. */
    public boolean isLibraryFunction(ValueName.Stdlib.Operation operation) {
        return entries.containsKey(operation) || sugars.containsKey(operation);
    }

    /** Whether {@code operation} is sugar for another library call — one no tree holds after the
     *  rewrite. */
    public boolean sugared(ValueName.Stdlib.Operation operation) {
        return sugars.containsKey(operation);
    }

    /** What {@code operation} rewrites to, or null where it is not sugar. */
    public Rewrite rewriteOf(ValueName.Stdlib.Operation operation) {
        return sugars.get(operation);
    }

    /** Every sugared operation, by what it rewrites to. */
    public Map<ValueName.Stdlib.Operation, Rewrite> rewrites() {
        return sugars;
    }

    /** Whether {@code operation} is a library <em>value</em> standing for an empty collection —
     *  {@code Map.empty}, {@code Set.empty}. Written with no parameter list, so it has no argument to
     *  learn its element type from and takes it from the position it is written in, as {@code []}
     *  does. Asked rather than spelled out: which those are is the library's own to say. */
    public boolean isEmptyCollectionValue(ValueName.Stdlib.Operation operation) {
        Entry entry = entries.get(operation);
        return entry != null && entry.declaration().params().isEmpty();
    }

    /**
     * The library's published surface: every qualified name a module outside the reserved namespace
     * may write, one module's vocabulary at a time.
     *
     * <p>One module's at a time is an order, and it is this that has it: the names are gathered by
     * walking the load order. A reader is shown them one after another, so what is handed over says
     * there is an order rather than leaving whoever writes the listing to take one from however the
     * names happen to be held.
     */
    public SequencedSet<String> published() {
        return published;
    }

    /** The Souther-bodied declarations (expanded inline at each call site), by the operation each
     *  is the body of, in declaration order. */
    public Map<ValueName.Stdlib.Operation, Hir.FnDef> helpers() {
        return helpers;
    }

    /**
     * The one walk this library publishes: the recursion every fold in a program is.
     *
     * <p>An output lowers a call to it as a loop rather than as a call, so it has to say which
     * operation that is. The library says: the alias is the library's, and a name written out
     * wherever the lowering is done would be that place deciding what this library publishes its
     * own walk as. Handed to an output the way a kernel's declaration is, so the output holds a
     * value rather than a question it could put to the library.
     *
     * <p>That there is one, and that this library has it, is checked while the library is built.
     */
    public ValueName.Stdlib.Operation theWalk() {
        return THE_WALK;
    }

    /** Which operation the walk is. Named where the library is described, so a library that
     *  published it under another alias is refused rather than silently lowered as a call. */
    private static final ValueName.Stdlib.Operation THE_WALK =
            ValueName.Stdlib.operation("List", "foldFrom");

    /**
     * The one operation this library publishes that states elements are distinct: that a list holds
     * no two of them with the same key.
     *
     * <p>The one that states it, not the only one about distinctness — {@code distinct} and
     * {@code distinctBy} build a list that is, and say nothing of the list they were given. An
     * invariant is a clause, so what a backend can represent as a constraint is the claim, and this
     * is the operation that makes it.
     *
     * <p>Here for the reason {@link #theWalk} is: it has a Souther body and so no kernel to ask
     * about, and a pass spelling it out would be deciding what this library publishes it as.
     *
     * <p>That this library has it is checked while the library is built.
     */
    public ValueName.Stdlib.Operation theDistinctnessPredicate() {
        return THE_DISTINCTNESS_PREDICATE;
    }

    /** Which operation states distinctness. Named beside the walk for the same reason. */
    private static final ValueName.Stdlib.Operation THE_DISTINCTNESS_PREDICATE =
            ValueName.Stdlib.operation("List", "allDistinctBy");

    /** What the language declares of its kernels, as the one value everything emitting a call to
     *  one reads. What a checked program carries across the boundary and what a backend derives its
     *  descriptors from is this, so neither is reading a library of its own. */
    public KernelSignatures kernelSignatures() {
        return kernels;
    }

    /**
     * What the library declares for {@code operation} where that operation is a kernel, and null
     * where it is anything else — a Souther-bodied helper, a sugar, a name the library does not
     * have.
     *
     * <p>Whether an operation is a kernel is a fact about the library and is answered by the
     * library. A reader that opened the declaration to look at its body would be reading how the
     * library is written to learn what it means, and would go on holding the declaration — which is
     * {@code Hir}, and is this compiler's own.
     *
     * <p>Asked with the operation and not with a spelling of it, and filed under the operation too.
     * A reach name renders as a qualified one today, which is a thing that is true rather than a
     * thing that is held: a reader handing over a rendered name would be asking the library to
     * resolve a spelling, and would go on doing it correctly for exactly as long as the two agreed.
     */
    public Intrinsic intrinsicOf(ValueName.Stdlib.Operation operation) {
        return kernelOperations.get(operation);
    }

    /**
     * The declaration the language itself gives under {@code address}, or null where it gives none.
     *
     * <p>The library's own data declarations, which belong to no module of a compilation: they
     * resolve and type like any other declaration and no compilation declares them, which is what a
     * boundary asks about ({@code check.Declarations.declaredByCompilation}). What a backend does to
     * represent one — ship an implementation by hand, generate it — is that backend's and is not
     * asked here.
     */
    public Hir.Def languageDeclaration(TypeKey address) {
        return language.get(address);
    }

    /** What resolving a module against this library takes. */
    public LibraryNames names() {
        return names;
    }

    /**
     * Every declaration the language itself gives, by the address it is declared under, in the
     * order the library declares them.
     *
     * <p>Which those are is the library's own answer. A reader that walked the reserved namespace's
     * modules asking each what it declares would be building this again, and would be right about
     * the modules it thought to walk — a declaration written in a module that reader did not name
     * would be one the language gives and nothing knows about.
     */
    public Map<TypeKey, Hir.Def> languageDeclarations() {
        return language;
    }

    /** What the language declares in {@code moduleName}, keyed by the name written there. Empty
     *  where no module of the library is called that. */
    public Map<String, Hir.Def> languageDeclarationsIn(String moduleName) {
        return byModule.getOrDefault(moduleName, Map.of());
    }


    /**
     * Every published name a bare {@code bareName} could be reaching for, in {@link Reserved#MODULES}
     * order — {@code ["Map.insert", "Set.insert"]} — or empty where the library publishes no such
     * name. What a report does with more than one is offer them all: a reader told only about
     * {@code Map.insert} has been told the library has no {@code Set.insert}.
     */
    public List<String> qualifiedCandidates(String bareName) {
        return candidates.getOrDefault(bareName, List.of());
    }

    /** Those candidates as a diagnostic writes them: {@code `Map.insert`, `Set.insert`}. The
     *  sentence they sit in belongs to the message catalog, so what is built here is the list and
     *  not a phrase — an "or" assembled in Java would be an English word in the Japanese report. */
    public String candidateList(String bareName) {
        StringBuilder sb = new StringBuilder();
        for (String qualified : qualifiedCandidates(bareName)) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append('`').append(qualified).append('`');
        }
        return sb.toString();
    }

    /**
     * Names that are sugar for another standard-library call, recognised as library functions but
     * rewritten before inlining.
     *
     * <p>Written here, beside what a library name is, rather than in whatever assembles the library:
     * that {@code List.fold} means {@code List.foldFrom} from index zero is a fact about the
     * library, and a loader is a thing that reads sources. What each rewrite keeps in place is
     * worked out at {@link Builder#freeze()}, where the target's declaration is to hand.
     */
    private record Sugar(ValueName.Stdlib.Operation written, ValueName.Stdlib.Operation target,
                         List<Integer> supplied) {
    }

    private static final List<Sugar> SUGARED = List.of(
            new Sugar(ValueName.Stdlib.operation("List", "fold"),
                    ValueName.Stdlib.operation("List", "foldFrom"), List.of(0)));

    /** What a loader hands its declarations to. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * The library under construction.
     *
     * <p>Everything a reader is later answered from is derived once here and never written again —
     * the published surface, the bare-name candidates, the helper table, the kernels, what each
     * sugar keeps. A projection worked out on demand instead would be a reader asking the library a
     * question the library answers by reading itself, which is how a value came to depend on the
     * order its own parts were initialized in.
     */
    public static final class Builder {

        private final Map<ValueName.Stdlib.Operation, Entry> entries = new LinkedHashMap<>();
        private final Set<ValueName.Stdlib.Operation> privateNames = new LinkedHashSet<>();
        private final Map<String, ValueName.Stdlib.Operation> operations = new LinkedHashMap<>();
        private final Map<TypeKey, Hir.Def> language = new LinkedHashMap<>();
        private final Map<String, TypeKey> declaredBare = new LinkedHashMap<>();

        private Builder() {
        }

        /**
         * One operation the library declares.
         *
         * @throws IllegalStateException where the name is already declared. The library has no
         *     overloading: a name that read two ways would have to be chosen between, and nothing at
         *     a value position could do the choosing. Put into a map, a second one would replace the
         *     first in silence, so it is refused where it arrives.
         */
        public Builder declares(ValueName.Stdlib.Operation operation, Entry entry,
                                boolean isPrivate) {
            if (entries.containsKey(operation)) {
                throw new IllegalStateException(
                        "the standard library declares `" + operation.qualified() + "` twice");
            }
            // Under the operation, which is what arrives. Rendering it to file it would make the
            // spelling the key of everything derived from this, and every reader holding an
            // operation would have to render it back to ask — the library resolving a name it
            // itself had just been handed.
            entries.put(operation, entry);
            operations.put(operation.qualified(), operation);
            if (isPrivate) {
                privateNames.add(operation);
            }
            return this;
        }

        /** One declaration the language itself gives, under the bare name it is written as.
         *
         * @throws IllegalStateException where the name is already declared, for the reason
         *     {@link #declares} is refused twice. */
        public Builder languageDeclares(Hir.Def def) {
            // Kept by address, and refused by spelling. What a source may write one as is its bare
            // name, so two of the library's modules declaring one spelling would publish two
            // declarations under one word and nothing at a type position could choose. That is a
            // rule about what the library may declare and not a property of how this is stored,
            // which is why the store is the address and the refusal is written out.
            TypeKey address = def.declares().key();
            if (declaredBare.containsKey(address.name())) {
                throw new IllegalStateException("the standard library declares `" + address.name()
                        + "` in both " + declaredBare.get(address.name()).module() + " and "
                        + address.module());
            }
            declaredBare.put(address.name(), address);
            language.put(address, def);
            return this;
        }

        /** The finished library. */
        public Stdlib freeze() {
            Map<ValueName.Stdlib.Operation, Rewrite> sugars = sugars();
            Map<String, Kernel> byKey = kernelsByKey();
            Map<Kernel, Intrinsic> intrinsics = new EnumMap<>(Kernel.class);
            Map<ValueName.Stdlib.Operation, Intrinsic> kernelOperations = new LinkedHashMap<>();
            Map<ValueName.Stdlib.Operation, Hir.FnDef> helpers = new LinkedHashMap<>();
            for (Map.Entry<ValueName.Stdlib.Operation, Entry> e : entries.entrySet()) {
                ValueName.Stdlib.Operation operation = e.getKey();
                Hir.FnBody body = e.getValue().declaration().body();
                if (body instanceof Hir.FnBody.Intrinsic written) {
                    // The one place a written key becomes the operation it names. Everything after
                    // this holds the kernel, so a key that names none is refused here — while the
                    // library is being built, and so for a kernel nothing goes on to call as much as
                    // for one every program calls.
                    Kernel named = byKey.get(written.key());
                    if (named == null) {
                        throw new IllegalStateException("the standard library declares `intrinsic \""
                                + written.key() + "\"`, which this compiler has no kernel for");
                    }
                    Intrinsic intrinsic = new Intrinsic(named,
                            declaredAs(operation.qualified(), e.getValue().signature()));
                    // And one kernel is declared once. A backend builds its boundary form out of
                    // the signature this holds, so two declarations of one kernel would be two
                    // answers to a question that has one — and put into a map, the second would
                    // replace the first in silence.
                    if (intrinsics.put(intrinsic.kernel(), intrinsic) != null) {
                        throw new IllegalStateException("two standard-library declarations name the"
                                + " kernel `" + intrinsic.kernel().key() + "`");
                    }
                    kernelOperations.put(operation, intrinsic);
                }
                if (body instanceof Hir.FnBody.Written) {
                    helpers.put(operation, e.getValue().declaration());
                }
            }
            // A sugar has no declaration, so nothing above put it among the operations; it is a
            // name a reader may write, so it belongs there.
            Map<String, ValueName.Stdlib.Operation> named = new LinkedHashMap<>(operations);
            SUGARED.forEach(sugar -> named.put(sugar.written().qualified(), sugar.written()));
            SequencedSet<String> published = published(sugars.keySet());
            for (ValueName.Stdlib.Operation ascribed
                    : List.of(THE_WALK, THE_DISTINCTNESS_PREDICATE)) {
                if (!helpers.containsKey(ascribed)) {
                    throw new IllegalStateException("a library publishes `" + ascribed
                            + "` as a body of its own, and this one publishes no such body");
                }
            }
            return new Stdlib(
                    Collections.unmodifiableMap(new LinkedHashMap<>(entries)),
                    Collections.unmodifiableSet(new LinkedHashSet<>(privateNames)),
                    Collections.unmodifiableMap(named),
                    Collections.unmodifiableMap(sugars),
                    Collections.unmodifiableMap(new LinkedHashMap<>(language)),
                    Collections.unmodifiableMap(intrinsics),
                    Collections.unmodifiableMap(kernelOperations),
                    Collections.unmodifiableMap(helpers),
                    published,
                    candidates(published));
        }

        /**
         * The kernels this compiler has, by the key a declaration names one by.
         *
         * <p>Here rather than on {@link Kernel}, because turning a written key into an operation is
         * what this step is and not something a kernel can do. Held there, every reader downstream
         * could take the key off a kernel and ask for the kernel back, which is the route out of the
         * operation and into a spelling that this exists to close.
         */
        private static Map<String, Kernel> kernelsByKey() {
            Map<String, Kernel> byKey = new LinkedHashMap<>();
            for (Kernel kernel : Kernel.values()) {
                if (byKey.put(kernel.key(), kernel) != null) {
                    throw new IllegalStateException("two kernels are written `" + kernel.key() + "`");
                }
            }
            return byKey;
        }

        /**
         * One kernel's declaration in the language's own vocabulary.
         *
         * <p>A kernel has no body to infer an answer from, so its calls are checked against the
         * declared result and there is nothing else to check them against. A declaration that
         * writes none is refused here, where which one it is can be said: the library is this
         * compiler's own source, so a fault in it is nobody's mistake to be told about.
         */
        private static KernelSignature declaredAs(String qualified, Signature declared) {
            if (declared.result() == null) {
                throw new IllegalStateException("the standard library declares the kernel `"
                        + qualified + "`, which states no return type");
            }
            return new KernelSignature(declared.params(), declared.result());
        }

        /** Each sugar with what it keeps in place: the target's own parameter count, less what the
         *  rewrite supplies. A sugar naming a target the library does not declare is refused —
         *  nothing downstream would report it, because everything downstream reads this. */
        private Map<ValueName.Stdlib.Operation, Rewrite> sugars() {
            Map<ValueName.Stdlib.Operation, Rewrite> sugars = new LinkedHashMap<>();
            for (Sugar sugar : SUGARED) {
                Entry declared = entries.get(sugar.target());
                if (declared == null) {
                    throw new IllegalStateException("`" + sugar.written().qualified()
                            + "` is sugar for `" + sugar.target().qualified()
                            + "`, which the standard library does not declare");
                }
                sugars.put(sugar.written(),
                        new Rewrite(sugar.target(), sugar.supplied(),
                                declared.signature().params().size() - sugar.supplied().size()));
            }
            return sugars;
        }

        /** The published surface, in {@link Reserved#MODULES} order. A sugar has no declaration to
         *  be ordered by, so it is placed among the module it belongs to — a reader of this list is
         *  reading one module's vocabulary at a time, and a name that reads as {@code List}'s belongs
         *  among them. */
        private SequencedSet<String> published(Set<ValueName.Stdlib.Operation> sugared) {
            Set<ValueName.Stdlib.Operation> named = new LinkedHashSet<>();
            for (ValueName.Stdlib.Operation operation : entries.keySet()) {
                if (!privateNames.contains(operation)) {
                    named.add(operation);
                }
            }
            named.addAll(sugared);
            // Which module a name belongs to is the operation's alias, which it holds. Read off a
            // spelling, this had to be given the operations back to look each one up again.
            SequencedSet<String> byModule = new LinkedHashSet<>();
            for (String qualifier : Reserved.QUALIFIERS) {
                for (ValueName.Stdlib.Operation operation : named) {
                    if (operation.alias().equals(qualifier)) {
                        byModule.add(operation.qualified());
                    }
                }
            }
            // anything under a qualifier not in the load order
            named.forEach(operation -> byModule.add(operation.qualified()));
            return Collections.unmodifiableSequencedSet(byModule);
        }

        /** Bare name → every published name it could be, in the order they are published in. */
        private Map<String, List<String>> candidates(Set<String> published) {
            Map<String, List<String>> byBareName = new LinkedHashMap<>();
            for (String qualified : published) {
                byBareName.computeIfAbsent(qualified.substring(qualified.indexOf('.') + 1),
                        bare -> new ArrayList<>()).add(qualified);
            }
            byBareName.replaceAll((bare, qualified) -> List.copyOf(qualified));
            return Collections.unmodifiableMap(byBareName);
        }
    }
}
