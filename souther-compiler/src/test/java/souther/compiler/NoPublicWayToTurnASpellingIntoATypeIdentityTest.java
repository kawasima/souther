package souther.compiler;

import org.junit.jupiter.api.Test;

import souther.compiler.ast.Ast;
import souther.compiler.ast.Hir;
import souther.compiler.check.Resolve;
import souther.compiler.check.SyntaxSymbols;
import souther.compiler.check.Symbols;
import souther.compiler.diag.EveryShippedMessageCatalogIsCompleteAndValidTest;
import souther.compiler.frontend.CstFrontend;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;
import souther.compiler.types.TypeSymbol;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * A spelling means something in the module that wrote it, and only the pass that reads that module
 * turns one into the type it denotes. Everything after holds the type.
 *
 * <p>This is checked against the shape of the API rather than against an answer, because the rule was
 * already written down three times — on {@code Symbols}, on {@code Resolve}, on
 * {@code Hir.Apply.written()} — and was broken all the same. A sentence is kept by whoever reads it;
 * a signature is kept by everyone. What a reader outside can reach is the whole of what it can do
 * wrong, so that is what is measured here.
 */
class NoPublicWayToTurnASpellingIntoATypeIdentityTest {

    private static final String LIB = """
            module lib exposing ( Amount )

            data Amount = Int
            """;

    private static final String APP = """
            module app

            data Note = { text: String }
            """;

    private static final String APP_WITH_AMOUNT = """
            module app2

            data Amount = { yen: Int }
            """;

    /**
     * No public method takes a spelling and answers with an identity. A caller outside the pass has
     * a type or it has nothing, and the way to get one is to be handed it.
     */
    @Test
    void nothingPublicOnSymbolsTakesAStringAndAnswersWithADeclarationOrItsName() {
        List<String> answering = new ArrayList<>();
        for (Method m : Symbols.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(m.getModifiers())) {
                continue;
            }
            boolean identity = m.getReturnType() == TypeSymbol.class
                    || Hir.Def.class.isAssignableFrom(m.getReturnType());
            boolean fromASpelling = List.of(m.getParameterTypes()).contains(String.class);
            if (identity && fromASpelling) {
                answering.add(m.getName());
            }
        }
        assertEquals(List.of(), answering,
                "a spelling reaches a declaration only through the pass that resolves it");
    }

    /** And the same holds of a declaration, which is what answers with an identity now that
     * nothing pairs a name with a module of its own. */
    @Test
    void nothingPublicOnADeclarationTakesAStringAndAnswersWithAnIdentity() {
        List<String> answering = new ArrayList<>();
        for (Method m : Hir.Def.class.getMethods()) {
            if (m.getReturnType() == TypeSymbol.class
                    && List.of(m.getParameterTypes()).contains(String.class)) {
                answering.add(m.getName());
            }
        }
        assertEquals(List.of(), answering,
                "a declaration says what it declares and is told nothing to say it with");
    }

    /**
     * An identity is named from a spelling only for what the language declares, and every one of
     * those is here by name.
     *
     * <p>{@code Int} and {@code Some} are declared by no module, so nothing indexes them and there
     * is nothing to look one up in — naming them is how they are reached, and what comes back is a
     * case of a closed set rather than a pair of strings a caller supplied. What must not join them
     * is a spelling paired with a module: an identity for a declaration that address may not name,
     * arrived at without the declaration world having said it does.
     *
     * <p>{@code runtime} used to be here, and what it minted was a spelling paired with a module
     * name no module has. The library's declarations are addressed by the module that writes them
     * now, and the cases beside them are a closed set, so there is nothing left for it to do.
     */
    @Test
    void aSpellingNamesAnIdentityOnlyForWhatTheLanguageDeclares() {
        Set<String> naming = new java.util.LinkedHashSet<>();
        for (Method m : TypeSymbol.class.getMethods()) {
            List<Class<?>> takes = List.of(m.getParameterTypes());
            if (m.getReturnType() == TypeSymbol.class && !takes.isEmpty()
                    && !takes.contains(TypeKey.class)) {
                naming.add(m.getName());
            }
        }
        assertEquals(Set.of("primitive", "optionCase"), naming,
                "a spelling and a module of the compilation do not make an identity between them");
    }

    /**
     * An address turns into an identity in one place, and every other reader that takes one gets
     * back an answer about the declaration world rather than an identity.
     *
     * <p>A key is what a class file carries and what a query is asked with, and it is structural and
     * public on purpose. What must not follow is a second way to hand one back and be given the
     * identity the compiler reasons with: a key read off a class is exactly what a reader would
     * otherwise assemble, and the whole of the difference is where it may be exchanged.
     *
     * <p>The list is the exchange and the questions around it, named rather than counted — one that
     * appears later is a capability someone was given, and it fails here until it is either narrowed
     * or written down as this one is.
     */
    @Test
    void anAddressBecomesAnIdentityInOnePlace() {
        Set<String> exchanging = new java.util.LinkedHashSet<>();
        Set<String> asking = new java.util.LinkedHashSet<>();
        // The cases of TypeSymbol are read as well as the type itself. One of them holds a key, and
        // a public constructor on it would be this door standing open in a place the list above
        // does not look — which is what a record would have made it, its canonical constructor being
        // as public as the record.
        List<Class<?>> read = new java.util.ArrayList<>(List.of(TypeSymbol.class,
                souther.compiler.types.TypeSymbols.class,
                souther.compiler.check.Declarations.class, souther.compiler.check.TypeScope.class,
                souther.compiler.check.Registry.class, Symbols.class, Hir.Def.class));
        java.util.Collections.addAll(read, TypeSymbol.class.getPermittedSubclasses());
        java.util.Collections.addAll(read,
                TypeSymbol.OfLanguage.class.getPermittedSubclasses());
        for (Class<?> c : read) {
            for (Method m : c.getMethods()) {
                if (!List.of(m.getParameterTypes()).contains(souther.compiler.types.TypeKey.class)) {
                    continue;
                }
                String named = c.getSimpleName() + "." + m.getName();
                // Any of the sum's cases counts as handing back an identity: what is being held
                // is that an address was exchanged for one, not which case came out.
                (TypeSymbol.class.isAssignableFrom(m.getReturnType()) ? exchanging : asking)
                        .add(named);
            }
            for (java.lang.reflect.Constructor<?> k : c.getConstructors()) {
                if (List.of(k.getParameterTypes()).contains(souther.compiler.types.TypeKey.class)) {
                    exchanging.add("new " + c.getSimpleName());
                }
            }
        }
        assertEquals(Set.of("TypeSymbols.declared", "Registry.identify", "Declarations.identify"),
                exchanging,
                "an address becomes an identity where a declaration world says one is declared there");
        assertEquals(Set.of("Declarations.declaration", "Declarations.contains",
                        "Declarations.declaredByCompilation", "Registry.declaration",
                        "Registry.declares", "Symbols.declares", "Symbols.declaredNode",
                        "Symbols.declaredByCompilation"),
                asking,
                "everything else that takes an address answers about the declaration world, or — "
                        + "where what is asked is whether anything is declared there at all — about "
                        + "the compilation. The three on the reader that names no stage are what "
                        + "such a reader may ask of one address: what is declared there, and by "
                        + "whom. None of them hands back an identity, so none is a way of making "
                        + "one");
    }

    /**
     * And the one exchange a caller may write is handed a declaration's own key, or an address the
     * declaration world has just answered for.
     *
     * <p>{@link #anAddressBecomesAnIdentityInOnePlace} says how many doors there are; this says what
     * goes through them. {@code TypeSymbols.declared} takes a {@link TypeKey}, and a key is two
     * strings, so nothing in its signature stops a caller writing
     * {@code TypeSymbols.declared(new TypeKey(module, spelling))} — which is the fabrication #464,
     * #696 and #700 each were, under a new name. Java has no way to say "only from a declaration"
     * across packages, so the argument is held to that here.
     *
     * <p>Read off the source rather than the bytecode, because what is being held is the expression
     * the author wrote. Test sources are not read: a fixture naming a declaration it invented is
     * what a fixture is for.
     */
    @Test
    void andTheOneExchangeIsHandedADeclarationRatherThanASpelling() throws IOException {
        Set<String> handed = new LinkedHashSet<>();
        for (Path source : EveryShippedMessageCatalogIsCompleteAndValidTest.mainSources()) {
            for (String argument : argumentsOf(Files.readString(source), "TypeSymbols.declared(")) {
                handed.add(source.getFileName() + ": " + argument);
            }
        }
        assertEquals(Set.of(
                        // The declaration says which one it is: indexing what a module writes,
                        // assembling a module's scope out of the declarations it and its imports
                        // have, and writing a declaration's class into the metadata.
                        "SyntaxSymbols.java: def.declaredKey()",
                        "Scoping.java: own.declaredKey()",
                        "Scoping.java: declared.declaredKey()",
                        "ModuleMetadata.java: def.declaredKey()",
                        // The library's own declarations, each under the module of the library that
                        // writes it. `souther.decimal` declares `RoundingMode`, and that is the
                        // identity — what a source may write it as is a separate answer, and
                        // `LibraryNames` is where that one is.
                        "StdlibLoader.java: def.declaredKey()",
                        // The address a declaration world has just been asked about and answered for.
                        "Registry.java: address",
                        "Declarations.java: address",
                        "Stdlib.java: address",
                        "TypeOps.java: address",
                        // The declaration a store question is keyed by, which the reading that asked
                        // it had already resolved: the question reads that declaration and no other.
                        "Machines.java: named"),
                handed,
                "an identity is exchanged for a declaration, or for an address one was found at");
    }

    /** Everything written between the parentheses of each {@code call} in {@code source}. Counted
     *  rather than matched, because an argument holds parentheses of its own. */
    private static List<String> argumentsOf(String source, String call) {
        List<String> found = new ArrayList<>();
        for (int at = source.indexOf(call); at >= 0; at = source.indexOf(call, at + 1)) {
            int from = at + call.length();
            int depth = 1;
            int i = from;
            while (i < source.length() && depth > 0) {
                depth += switch (source.charAt(i)) {
                    case '(' -> 1;
                    case ')' -> -1;
                    default -> 0;
                };
                i++;
            }
            found.add(source.substring(from, i - 1).trim());
        }
        return found;
    }

    /** A declaration says which one it is without being told the module. */
    @Test
    void aDeclarationSaysItsKey() {
        assertEquals(new souther.compiler.types.TypeKey("lib", "Amount"),
                declarationOf(LIB, "Amount").declaredKey());
        assertNotEquals(declarationOf(APP_WITH_AMOUNT, "Amount").declaredKey(),
                declarationOf(LIB, "Amount").declaredKey());
    }

    /** And the pass's own way in is not reachable from outside it. */
    @Test
    void resolutionIsNotPartOfTheSurface() {
        for (Method m : Symbols.class.getDeclaredMethods()) {
            if (m.getName().equals("resolve") || m.getName().equals("resolveCase")) {
                assertFalse(Modifier.isPublic(m.getModifiers()),
                        m.getName() + " is the pass's own reading and belongs to it");
            }
        }
    }

    /**
     * A declaration answers with the type it declares, and there is no module for a caller to
     * supply. What one is asked through — a scope, a registry, a check — has no say in the answer,
     * so handing one somewhere it did not come from cannot name a declaration of wherever it is
     * being read.
     */
    @Test
    void aDeclarationAnswersWithTheModuleThatWroteIt() {
        assertEquals(TypeSymbols.declared(new TypeKey("app", "Note")), declarationOf(APP, "Note").declares());
        assertEquals(TypeSymbols.declared(new TypeKey("lib", "Amount")), declarationOf(LIB, "Amount").declares());
    }

    /**
     * Two modules that write one spelling declare two types, and holding either says which. This is
     * what nothing could tell apart while the module came from the reader: a declaration under a
     * name the reading module also writes was answered about the reader's.
     */
    @Test
    void oneSpellingWrittenByTwoModulesIsTwoDeclarations() {
        TypeSymbol mine = declarationOf(APP_WITH_AMOUNT, "Amount").declares();
        TypeSymbol theirs = declarationOf(LIB, "Amount").declares();

        assertEquals("Amount", mine.name());
        assertEquals("Amount", theirs.name());
        assertNotEquals(mine, theirs);
    }

    private static Hir.Module resolved(String source) {
        Ast.Module parsed = CstFrontend.parse(source);
        return Resolve.module(parsed, SyntaxSymbols.of(parsed, DefaultStdlib.get()));
    }

    private static Hir.Def declarationOf(String source, String name) {
        for (Hir.Def def : resolved(source).defs()) {
            if (def.name().equals(name)) {
                return def;
            }
        }
        throw new IllegalStateException("the fixture declares `" + name + "`");
    }
}
