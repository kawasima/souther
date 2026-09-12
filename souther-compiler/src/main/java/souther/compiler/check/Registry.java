package souther.compiler.check;

import souther.compiler.ast.Ast;
import souther.compiler.ast.Hir;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Where the declarations of a compilation are read from. {@link Symbols} answers what a name means
 * here; this answers what any module declares, which is the part that is not about "here" at all.
 *
 * <p>Three questions, and no more: a module's definitions, what it exposes, and which modules there
 * are. Keeping it to that is the point — a registry that handed out {@code Hir.Module} would let a
 * caller reach a module's behaviors, examples or fns and so depend on which pass had last rewritten
 * it, which is how a definition's meaning came to depend on the whole compile.
 *
 * <p>Every question here is a lookup, and no lookup refuses. A registry is built out of declarations
 * that have already been indexed, so whether a module's declarations can be indexed at all is
 * settled by whoever built it, once, where there is still somebody to answer — and every asker
 * afterwards gets the same answer. Working them out on first use is what put a refusal behind a
 * lookup: the reader that caught the first ask was caught by the second, because a module named with
 * a qualifier is asked for again while some other module is being resolved.
 */
public interface Registry<D> {

    /**
     * One declaration, or null when no module of this compilation declares it.
     *
     * <p>The question nearly everything asks, and the reason it is not {@link #declaredIn}: reading
     * a whole module's declarations to reach one of them makes the reader depend on all of them, so
     * declaring something new — which nobody can even see yet — reaches every module that imported
     * anything from there.
     */
    D declaration(TypeKey address);

    /**
     * Whether a module of this compilation declares something at {@code address}.
     *
     * <p>Not {@link #declaration} answering something. What a registry holds is declarations at one
     * rung, and whether one is declared at all is not a fact about any rung: a product no
     * representation could be derived for is a name its module declares, and a registry that
     * answered no here would be saying this compilation has no such type. Asking the declaration is
     * also more than the question needs — a reader wanting to know whether something is there is
     * told, by the answer it gets, where the thing now stands.
     *
     * <p>The default is for a registry built out of declarations already in hand, where there is
     * nothing else to ask and nothing the answer could be read from beside it.
     */
    default boolean declares(TypeKey address) {
        return declaration(address) != null;
    }

    /**
     * The identity of the declaration that address names, or null where nothing declares it.
     *
     * <p>Asking rather than assembling. A reader with a module and a name has an address, and an
     * address is not an identity until something declares one there — so this is where the two are
     * told apart, and a reader that gets nothing back has nothing it could have gone on with.
     *
     * <p>Which is a question about the declaration being there and not about this registry having a
     * representation of it, so it is {@link #declares} that decides it.
     */
    default TypeSymbol identify(TypeKey address) {
        return declares(address) ? TypeSymbols.declared(address) : null;
    }

    /** Every definition of one module, keyed by the name written there. Empty when this compilation
     * has no such module.
     *
     * <p>For the questions that really are about a whole module — which sum a case belongs to, what
     * a "did you mean" may offer. A reader after one declaration asks {@link #declaration}. */
    Map<String, D> declaredIn(String moduleName);

    /** The base type names {@code moduleName} exposes, with any {@code .decoder} / {@code .encoder}
     * member dropped. Empty when this compilation has no such module. */
    Set<String> exposedBy(String moduleName);

    /** Every module name in this compilation. A qualifier is one of these or an import alias. */
    Set<String> moduleNames();

    /** Nothing is declared anywhere — for signatures written over primitives and type variables. */
    static <D> Registry<D> empty() {
        return ofRead(Map.of());
    }

    /**
     * One module as a registry has it: what it declares, and the base type names it exposes.
     *
     * <p>One value, because having a module is one fact and the three questions a registry answers
     * are answered from it. Handed over as two maps, a caller could fill one and not the other, and
     * what came back said a declaration was there under a name the registry did not have.
     *
     * @param declarations what it declares, by the name written there
     * @param exposed      the base type names it exposes ({@link #baseNames})
     */
    record Declared<D>(Map<String, D> declarations, Set<String> exposed) {

        /** Copied in the order the module wrote them. What a module declares is read out in that
         *  order — a reader rebuilding a module from what a registry has puts its declarations back
         *  in the order it finds them, so a copy that does not keep it moves them. */
        public Declared {
            declarations =
                    java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(declarations));
            exposed = Set.copyOf(exposed);
        }
    }

    /**
     * The declarations of a set of modules already indexed.
     *
     * <p>Indexed by the caller, because what a refusal means depends on where the declarations came
     * from and this cannot know: a module of this compilation has an author to report it to, one
     * read back off an artifact has nobody and is an artifact this compiler will not read, and one
     * the compiler ships is a fault in the compiler. Each of those is said where the module is
     * obtained, and what arrives here is what was left standing.
     *
     * <p>A module that had nothing to give is not among {@code modules}, and is answered the way a
     * module nobody has is answered. Which of the two it was is not this registry's to say: that is
     * what the reader settled, and it says it where it says what it has.
     */
    static <D> Registry<D> ofRead(Map<String, Declared<D>> modules) {
        Map<String, Declared<D>> has = Map.copyOf(modules);
        return new Registry<D>() {

            @Override
            public D declaration(TypeKey address) {
                return declaredIn(address.module()).get(address.name());
            }

            @Override
            public Map<String, D> declaredIn(String moduleName) {
                Declared<D> module = has.get(moduleName);
                return module == null ? Map.of() : module.declarations();
            }

            @Override
            public Set<String> exposedBy(String moduleName) {
                Declared<D> module = has.get(moduleName);
                return module == null ? Set.of() : module.exposed();
            }

            @Override
            public Set<String> moduleNames() {
                return has.keySet();
            }
        };
    }

    /** What one module declares as it was written, and the declarations it may not have. */
    static DeclaredNames.Index<Ast.Def> indexed(Ast.Module module) {
        return DeclaredNames.index(module.defs(), Ast.Def::name);
    }

    /** What one resolved module declares, and the declarations it may not have. */
    static DeclaredNames.Index<Hir.Def> indexed(Hir.Module module) {
        return DeclaredNames.index(module.defs(), Hir.Def::name);
    }

    /** An {@code exposing} list as the type names it names: {@code Amount.decoder} exposes
     * {@code Amount}. */
    static Set<String> baseNames(Iterable<String> exposing) {
        Set<String> names = new LinkedHashSet<>();
        for (String e : exposing) {
            int dot = e.indexOf('.');
            names.add(dot < 0 ? e : e.substring(0, dot));
        }
        return Set.copyOf(names);
    }
}
