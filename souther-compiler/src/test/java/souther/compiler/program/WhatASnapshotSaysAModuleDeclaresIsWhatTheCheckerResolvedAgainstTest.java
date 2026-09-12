package souther.compiler.program;

import souther.compiler.ast.Hir;
import souther.compiler.types.TypeKey;
import souther.compiler.check.ScopedDeclarations;
import souther.compiler.check.Symbols;
import souther.compiler.check.TypeOps;
import souther.compiler.core.ValueShape;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Compilation;
import souther.compiler.query.Names;
import souther.compiler.types.TypeSymbol;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * What a checked program says a module declares is what the checker resolved names against.
 *
 * <p>A compile holds a declaration in more than one tree. Two of them come back together and are
 * both an {@code Hir.Module} — one the declarations were settled in, one the bodies were lowered
 * into — so which of the two an assembler hands on is settled by the name of a local variable and
 * by nothing javac can see. Everything below the check resolves a name through the declaration
 * world, and that world is the one a reader of a checked program has to be reading too: a snapshot
 * taken from a tree that had been rewritten for some later stage's convenience would describe a
 * program the checker never saw, and would do it silently for as long as the rewrite left
 * declarations alone.
 *
 * <p>So the two are compared here. Not the snapshot against the tree it was taken from, which would
 * be an answer against itself, but against the world every other reader of a declaration asks —
 * {@code symbols.declarations()}, reached through the derived registry, which is what
 * {@code TypeOps} and {@code AtomSpace} answer over.
 *
 * <p>Inside {@code souther-compiler} because that world is the compiler's own and does not cross
 * the boundary. What the boundary hands out is checked from outside it, and this is the seam that
 * check cannot see.
 */
class WhatASnapshotSaysAModuleDeclaresIsWhatTheCheckerResolvedAgainstTest {

    /**
     * Every shape a module declares, including the ones a later stage has a reason to touch: a
     * newtype, whose applications are rewritten below the declarations; a data carrying an
     * invariant, which is settled and rewritten between the two trees; an include; a sum of sums;
     * and units a case list declares rather than a line of its own.
     *
     * <p>{@code Wrapper} is the newtype's twin and is here so that the form is compared both ways.
     * A snapshot deciding the form by the shape it was left with — one field, called {@code value} —
     * answers as the declaration does for {@code Amount} and would go unremarked if nothing was
     * declared that the shape says the same thing about and the language does not.
     */
    private static final String MODULE = """
            module demo

            data Amount = Int
                invariant value >= 0

            data Wrapper = { value: Int }

            data Common = { id: Int, tag: String }
            data Wide   = { ...Common, extra: Int }
            data Only   = { ...Common }

            data Middle = { ...Common }
            data Left   = Wide | Middle
            data Right  = Middle | Only
            data Reach  = Left | Right

            data Kind = Plain | Express

            behavior widen : (n: Int) -> Wide constructs Wide

            let widen (n) = Wide { id = n, tag = "t", extra = n }
            """;

    @Test
    void everyDeclarationTheCheckerHasIsOneTheSnapshotHas() {
        Read read = read();

        assertEquals(read.world().keySet(), read.snapshot().keySet(),
                "the declarations the checker resolves against, and the ones the snapshot holds");
    }

    /**
     * And each one is made of what the checker would say it is made of.
     *
     * <p>Asked of the declaration the world answers with, not of the one the snapshot was taken
     * from. A product's fields and a sum's cases are read here the way every reader below the check
     * reads them, so a snapshot built over a tree that had drifted answers differently at the first
     * declaration the drift touched.
     *
     * <p>And which form it was written in, which the fields do not say. A newtype and a product of
     * one member are made of the same field of the same type, and the arm is where they part; read
     * off {@code Hir.Data.newtype()}, which is what the derived codec is generated from, so the
     * form an output writes a value by and the form the JVM's codec writes it by are one answer.
     */
    @Test
    void andIsMadeOfWhatTheCheckerWouldSayItIsMadeOf() {
        Read read = read();

        for (Map.Entry<TypeSymbol.AtModule, Hir.Def> declared : read.world().entrySet()) {
            CheckedData published = read.snapshot().get(declared.getKey());
            assertNotNull(published, () -> "not in the snapshot: " + declared.getKey());
            switch (declared.getValue()) {
                case Hir.Data data -> {
                    CheckedData.WithFields built = assertInstanceOf(CheckedData.WithFields.class,
                            published, declared.getKey()::toString);
                    assertEquals(new ArrayList<>(TypeOps.fieldTypes(data, read.symbols()).keySet()),
                            built.fields().stream().map(ValueShape.Field::name).toList(),
                            () -> "the fields of " + declared.getKey());
                    assertEquals(data.newtype(), built instanceof CheckedData.Newtype,
                            () -> "which form " + declared.getKey() + " was declared in");
                }
                case Hir.SumData sum -> assertEquals(
                        souther.compiler.check.AtomSpace.subjectAtoms(
                                souther.compiler.types.Type.ref(sum.declares()),
                                ScopedDeclarations.of(read.symbols())),
                        assertInstanceOf(CheckedData.Sum.class, published,
                                declared.getKey()::toString).cases(),
                        () -> "the cases of " + declared.getKey());
                case Hir.UnitData _ -> assertInstanceOf(CheckedData.Unit.class, published,
                        declared.getKey()::toString);
            }
        }
    }

    /** The four declarations a module can write, so that neither side is compared over a set that
     *  happens to hold only one of them. */
    @Test
    void andAllFourShapesAreAmongThem() {
        Set<Class<?>> shapes = new LinkedHashSet<>();
        for (CheckedData published : read().snapshot().values()) {
            shapes.add(published.getClass());
        }

        assertEquals(Set.of(CheckedData.Product.class, CheckedData.Newtype.class,
                CheckedData.Sum.class, CheckedData.Unit.class), shapes);
    }

    /** The declaration world the checker resolves against, the snapshot taken of the same compile,
     *  and the world itself for asking what a declaration is made of. */
    private record Read(Map<TypeSymbol.AtModule, Hir.Def> world,
                        Map<TypeSymbol.AtModule, CheckedData> snapshot,
                        Symbols symbols) {}

    private static Read read() {
        Compilation compilation = Compilation.ofSources(List.of(MODULE), ModulePath.EMPTY);
        Symbols symbols = Names.derivedSymbols(compilation.db(), "demo").value();
        assertNotNull(symbols, "the module has a declaration world");
        Map<TypeSymbol.AtModule, Hir.Def> world = new LinkedHashMap<>();
        symbols.declaredNamesIn("demo").forEach(declared -> {
            Hir.Def def = symbols.declaredNode(new TypeKey("demo", declared));
            if (def.declares() instanceof TypeSymbol.AtModule at) {
                world.put(at, def);
            }
        });

        CheckedModule module = CheckedProgram.of(List.of(MODULE)).module("demo");
        assertNotNull(module, "the compile checked this module");
        Map<TypeSymbol.AtModule, CheckedData> snapshot = new LinkedHashMap<>();
        for (CheckedData published : module.data()) {
            snapshot.put(published.name(), published);
        }
        return new Read(world, snapshot, symbols);
    }
}
