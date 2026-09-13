package souther.compiler.check;

import souther.compiler.Reserved;
import souther.compiler.ast.Ast;
import souther.compiler.ast.Hir;
import souther.compiler.stdlib.Stdlib;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a semantic identity is a declaration of.
 *
 * <p>The other half of what a module needs, and not the half {@link Scope} answers: this takes an
 * identity and never a spelling, so nothing here has to know which module is being compiled. A
 * reader holding one of these has already resolved whatever it is asking about.
 *
 * <p>{@code D} is the representation the declarations are in, and a representation is all it is.
 * Everything below {@code Resolve} holds {@link Hir}; {@code Resolve} reads the declarations of the
 * modules it resolves against as they were written, because a name written there is that module's to
 * resolve and not this one's. What {@code D} does not say is how far the declarations have got,
 * because the two sources below have got different distances — the language's vocabulary is loaded
 * resolved and derivation never runs over it — so a {@code D} naming a rung would be false of one of
 * them. Which rung a reader is at is which of these it was handed.
 *
 * <p>Two sources, kept apart. {@link Registry} is what this compilation declares, read one
 * declaration at a time; the language's own vocabulary — the data the standard library declares — is
 * declared by no module of the compilation and is answered beside it. {@link #declaration} sees
 * both, because a name that denotes one types like a name that denotes the other;
 * {@link #declaredByCompilation} sees only the first, because a construction set is made of the
 * data a compilation declares and never of the language's vocabulary.
 *
 * <p>Asked one identity at a time. {@link #declaredIn} enumerates, for the questions that really are
 * about a whole module — which sum a case belongs to, what a "did you mean" may offer — and is not
 * how {@link #declaration} is answered. Reading a module's declarations to reach one of them makes
 * the reader depend on all of them, and the failure of any one of them becomes the failure of every
 * question about the rest.
 */
public final class Declarations<D> {

    private final Registry<D> registry;
    private final Vocabulary<D> language;

    Declarations(Registry<D> registry, Vocabulary<D> language) {
        this.registry = registry;
        this.language = language;
    }

    /**
     * What the language itself declares, as the reader asking holds declarations.
     *
     * <p>The prelude's runtime-backed data is loaded resolved, so it is there to be answered with
     * only where the reader holds {@link Hir}. A reader at the written representation is
     * {@code Resolve}, which reaches a declaration to walk what it includes, and nothing the
     * language declares is included from — {@link #ofNothing} says that once, rather than as a null
     * every caller has to know about.
     */
    public interface Vocabulary<D> {

        /** The declaration this identity names, or null where the language declares none. */
        D declaration(TypeKey address);

        /** What it declares in {@code moduleName}, keyed by the name written there. */
        Map<String, D> declaredIn(String moduleName);

        /** The language's own vocabulary, as a resolved compilation reads it: what {@code stdlib}
         *  declares of its own rather than through any module of the compilation. */
        static Vocabulary<Hir.Def> of(Stdlib stdlib) {
            return new Vocabulary<>() {
                @Override
                public Hir.Def declaration(TypeKey address) {
                    return stdlib.languageDeclaration(address);
                }

                @Override
                public Map<String, Hir.Def> declaredIn(String moduleName) {
                    return stdlib.languageDeclarationsIn(moduleName);
                }
            };
        }

        /**
         * The same vocabulary as the stage below the derivation reads it.
         *
         * <p>The library's declarations do not go through the derivation and do not need to: what it
         * declares is a sum and the units under it, and there is nothing deriving one would
         * establish ({@link Derived.Def#ofLanguage}). So the witness is built here, once, and the
         * two sources a reader below the derivation is answered from are at the same rung — which is
         * what lets that reader be handed a table of derived declarations rather than of nodes.
         *
         * @throws IllegalStateException where the library declares a product, which would need
         *     deriving and has not been derived
         */
        static Vocabulary<Derived.Def> ofDerived(Stdlib stdlib) {
            Map<TypeKey, Derived.Def> byAddress = new LinkedHashMap<>();
            Map<String, Map<String, Derived.Def>> byModule = new LinkedHashMap<>();
            stdlib.languageDeclarations().forEach((address, def) -> {
                Derived.Def derived = Derived.Def.ofLanguage(Normalized.Def.ofLanguage(def));
                byAddress.put(address, derived);
                byModule.computeIfAbsent(address.module(), _ -> new LinkedHashMap<>())
                        .put(address.name(), derived);
            });
            byModule.replaceAll((_, defs) -> Map.copyOf(defs));
            Map<String, Map<String, Derived.Def>> grouped = Map.copyOf(byModule);
            Map<TypeKey, Derived.Def> declared = Map.copyOf(byAddress);
            return new Vocabulary<>() {
                @Override
                public Derived.Def declaration(TypeKey address) {
                    return declared.get(address);
                }

                @Override
                public Map<String, Derived.Def> declaredIn(String moduleName) {
                    return grouped.getOrDefault(moduleName, Map.of());
                }
            };
        }

        /**
         * The same vocabulary as a reader of normalized declarations reads it.
         *
         * <p>Every kind of it, a product among them. Normalizing is something a declaration of any
         * kind has had done to it once its clauses hold no unwritten construction, and what the
         * library declares holds none — so there is nothing here for the derivation's refusal of a
         * product to be about.
         */
        static Vocabulary<Normalized.Def> ofNormalized(Stdlib stdlib) {
            Map<TypeKey, Normalized.Def> byAddress = new LinkedHashMap<>();
            Map<String, Map<String, Normalized.Def>> byModule = new LinkedHashMap<>();
            stdlib.languageDeclarations().forEach((address, def) -> {
                Normalized.Def normalized = Normalized.Def.ofLanguage(def);
                byAddress.put(address, normalized);
                byModule.computeIfAbsent(address.module(), _ -> new LinkedHashMap<>())
                        .put(address.name(), normalized);
            });
            byModule.replaceAll((_, defs) -> Map.copyOf(defs));
            Map<String, Map<String, Normalized.Def>> grouped = Map.copyOf(byModule);
            Map<TypeKey, Normalized.Def> declared = Map.copyOf(byAddress);
            return new Vocabulary<>() {
                @Override
                public Normalized.Def declaration(TypeKey address) {
                    return declared.get(address);
                }

                @Override
                public Map<String, Normalized.Def> declaredIn(String moduleName) {
                    return grouped.getOrDefault(moduleName, Map.of());
                }
            };
        }

        /** Nothing beside what the compilation itself declares. */
        static Vocabulary<Ast.Def> ofNothing() {
            return new Vocabulary<>() {
                @Override
                public Ast.Def declaration(TypeKey address) {
                    return null;
                }

                @Override
                public Map<String, Ast.Def> declaredIn(String moduleName) {
                    return Map.of();
                }
            };
        }
    }

    /**
     * The declaration {@code name} is, or null where the language gives it and nothing declares one.
     *
     * <p>Asked with the identity, which is what a reader holding one has. Taking its address apart
     * to look it up is that reader assembling an address again, and the address-taking method below
     * is for a reader whose address really did come from outside — a name read off a class file.
     *
     * <p>What the language gives answers null, and answers it without an address being made for it.
     * There is no source that declares {@code Int}, and none that declares the cases beside it
     * either; a reader asking what one of them is a declaration of is asking about something that
     * is not a declaration, and the answer is the same as for a name nothing declares.
     */
    public D declaration(TypeSymbol name) {
        return name instanceof TypeSymbol.AtModule at ? declaration(at.key()) : null;
    }

    /** The declaration at {@code address}, or null when nothing declares one there. */
    public D declaration(TypeKey address) {
        D def = registry.declaration(address);
        return def != null ? def : language.declaration(address);
    }

    /**
     * Whether anything declares {@code name} — this compilation or the language.
     *
     * <p>Asked of each source as the question it is, rather than by reaching for a declaration and
     * seeing whether one came back. A reader wanting to know whether something is declared does not
     * mean anything by the declaration, and handed it would be told every time the declaration was
     * written over or written somewhere else in its text.
     */
    public boolean contains(TypeKey address) {
        return registry.declares(address) || language.declaration(address) != null;
    }

    /**
     * The identity of the declaration that address names, or null where nothing declares it.
     *
     * <p>{@link Registry#identify} over both sources, for a reader whose address came from outside
     * the compiler — a binary name read off a live value is one. Asking rather than assembling: an
     * address is not an identity until something declares one there, so a reader that gets nothing
     * back has nothing it could have gone on with, and one that gets an identity has been told a
     * declaration is behind it.
     */
    public TypeSymbol identify(TypeKey address) {
        return contains(address) ? TypeSymbols.declared(address) : null;
    }

    /** Whether {@code name} is declared by a module of this compilation — as opposed to a
     * declaration the language gives, which resolves and types like any other but belongs to no
     * module here. The construction discipline asks this: a construction set holds data a
     * compilation declares, and never the language's vocabulary. */
    public boolean declaredByCompilation(TypeSymbol name) {
        return name instanceof TypeSymbol.AtModule at && declaredByCompilation(at.key());
    }

    /** The same, of an address. */
    public boolean declaredByCompilation(TypeKey address) {
        return registry.declares(address);
    }

    /**
     * Every definition of one module, keyed by the name written there.
     *
     * <p>Both worlds, because a standard-library module is a module: {@code souther.decimal}
     * declares {@code RoundingMode}, and a reader asking what that module declares is asking about
     * a declaration no compilation of it made.
     *
     * <p>Which world by the name and not by which answered something. The reserved namespace is the
     * library's and nothing of a compilation is in it (ADR-0028), so this is a decision about who
     * owns the name rather than a first world tried and a second fallen back to — a module of the
     * compilation that declares no types would reach the fallback and be answered for by whatever
     * happened to be under its name.
     */
    public Map<String, D> declaredIn(String moduleName) {
        return Reserved.isNamespace(moduleName)
                ? language.declaredIn(moduleName) : registry.declaredIn(moduleName);
    }
}

