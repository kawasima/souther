package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.DefaultStdlib;
import souther.compiler.ast.Hir;
import souther.compiler.query.Compilation;
import souther.compiler.query.Scopes;
import souther.compiler.query.Shapes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A module's assembly and the witness that every declaration in it came out have to be about the
 * same module, and a name does not say they are.
 *
 * <p>{@link Prepared} answers for a module out of two values: what a check reads comes from the
 * assembly, and what may only be read where the module is whole rests on the witness. Held together
 * by name alone, two compilations that each write a {@code module m} pair — the fields of one
 * module's declarations beside the claim made about another's. Every reader below is then told
 * something true of a module it is not looking at.
 *
 * <p>What the two have in common when they belong together is the settled module they were both
 * built from, and that is what is compared. Two modules of one name are two trees.
 */
class AnAssemblyAndItsWitnessAreAboutOneModuleNotOneNameTest {

    private static final String ONE = """
            module m exposing ( T )

            data T = { a: Int }
            """;

    /** The same name over a declaration that says something else, so that a pairing of the two is a
     *  pairing a name cannot tell apart. */
    private static final String OTHER = """
            module m exposing ( T )

            data T = { b: Int }
            """;

    @Test
    void twoModulesOfOneNameDoNotPair() {
        Desugared.Module declarations = declarationsOf(ONE);
        CheckSurface assembly = assemblyOf(OTHER);

        assertEquals("m", declarations.name());
        assertEquals("m", assembly.name());

        assertThrows(IllegalArgumentException.class,
                () -> Prepared.prepare(declarations, assembly),
                "an assembly of one module was paired with the witness of another, and the two"
                        + " agreeing on a name is all it took");
    }

    /** And the pair that does belong together is made, so the refusal is about the pairing and not
     *  about the values being unreachable. */
    @Test
    void theTwoBuiltFromOneModuleDoPair() {
        assertNotNull(Prepared.prepare(declarationsOf(ONE), assemblyOf(ONE)));
    }

    /**
     * One settling, and declarations read against two sets of names, do not pair either.
     *
     * <p>The case a tree cannot tell apart. Normalizing reads what a name denotes — whether the
     * applied name is a newtype, and so whether what was written is a construction — so one settled
     * declaration read against two scopes is two declarations while the settled tree stays the one
     * tree. A pairing proved from that tree would put a witness about the first beside an assembly
     * of the second, and the constructions a reader below finds would be the ones the other reading
     * made.
     */
    @Test
    void oneSettlingReadAgainstTwoSetsOfNamesDoesNotPair() {
        Desugared.Module declarations = declarationsOf(WITH_A_CONSTRUCTION);
        InvariantSettled settling = assemblyOf(WITH_A_CONSTRUCTION).settling();

        Map<String, Normalized.Def> elsewhere = new LinkedHashMap<>();
        for (InvariantSettled.Def def : settling.defs()) {
            elsewhere.put(def.name(),
                    Normalized.Def.of(def, ResolvedSymbols.none(DefaultStdlib.get())));
        }

        assertNotEquals(normalizedIn(declarations), List.copyOf(elsewhere.values()),
                "the two readings are two declarations, or this says nothing");

        Map<String, Desugared.Fn> itsOwn = new LinkedHashMap<>();
        for (Desugared.Fn each : declarations.fns()) {
            itsOwn.put(each.name(), each);
        }
        CheckSurface read = CheckSurface.assemble(settling, elsewhere, itsOwn,
                Scopes.derived(Compilation.ofSource(WITH_A_CONSTRUCTION, "Main").db(), "m").value(),
                Map.of(), FakeTables.classify(settling.module()));
        assertNotNull(read, "the assembly is made, so the refusal below is about the pairing");

        assertThrows(IllegalArgumentException.class, () -> Prepared.prepare(declarations, read),
                "a witness about one reading of the declarations was paired with an assembly of"
                        + " another, and the settled tree they share says nothing about it");
    }

    /**
     * And an answer standing in for a definition it is not about is refused where it arrives.
     *
     * <p>The third antecedent, guarded where the assembly is made. What the parts are looked up by
     * is the name the module wrote, and a name is a name in some module: an answer for another
     * definition of the same spelling would otherwise be built in, and the assembly would hand a
     * reader a body about something else under the name it asked for. Said the way
     * {@link Desugared.Module#assemble} says it of the same table, so the two agree about what an
     * answer standing in for a part has to be.
     */
    @Test
    void ananswerForAnotherDefinitionIsRefusedWhereItArrives() {
        CheckSurface itsOwn = assemblyOf(TWO_DEFINITIONS);
        InvariantSettled settling = itsOwn.settling();

        Map<String, Normalized.Def> normalized = new LinkedHashMap<>();
        for (Normalized.Def each : itsOwn.declarations()) {
            normalized.put(each.name(), each);
        }
        Map<String, Desugared.Fn> underTheWrongName = new LinkedHashMap<>();
        Desugared.Fn second = itsOwn.desugaredFrom().get(1);
        for (Hir.FnDef wrote : settling.module().fns()) {
            underTheWrongName.put(wrote.name(), second);   // both names reach the second definition
        }

        assertEquals(2, settling.module().fns().size(), "two definitions, or this says nothing");

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> CheckSurface.assemble(settling, normalized, underTheWrongName,
                        Scopes.derived(Compilation.ofSource(TWO_DEFINITIONS, "Main").db(), "m")
                                .value(),
                        Map.of(), FakeTables.classify(settling.module())),
                "an answer for one definition stood in for another, and the name they were looked"
                        + " up by is the same shape");
        assertTrue(refused.getMessage().contains("first"), refused.getMessage());
    }

    /**
     * And an assembly of one reading of the bodies does not pair with a witness about another.
     *
     * <p>The third antecedent, at the pairing rather than at the assembly. What stands in for
     * {@code first} here says it is {@code first} and is a definition whose constructions are
     * constructions, so the assembly takes it — every check the assembly makes is about the part it
     * stands in for and not about which answer it is. What tells the two apart is the witness, which
     * holds the answer the desugaring gave for that definition; the settling and the declarations
     * are the same on both sides and say nothing about the bodies.
     */
    @Test
    void anAssemblyOfOtherBodiesDoesNotPairWithTheWitness() {
        Desugared.Module declarations = declarationsOf(TWO_DEFINITIONS);
        CheckSurface itsOwn = assemblyOf(TWO_DEFINITIONS);
        InvariantSettled settling = itsOwn.settling();

        Map<String, Normalized.Def> normalized = new LinkedHashMap<>();
        for (Normalized.Def each : itsOwn.declarations()) {
            normalized.put(each.name(), each);
        }
        // `first` under its own name, with the body the module wrote for `second`. It is the
        // definition it says it is and holds no construction, so nothing the assembly asks refuses
        // it.
        Hir.FnDef first = settling.module().fns().get(0);
        Hir.FnDef second = settling.module().fns().get(1);
        Hir.FnDef otherBody = first.withBody(second.body());
        Map<String, Desugared.Fn> read = new LinkedHashMap<>();
        read.put(first.name(),
                Desugared.Fn.desugar(otherBody, ResolvedSymbols.none(DefaultStdlib.get())));
        read.put(second.name(), itsOwn.desugaredFrom().get(1));

        CheckSurface assembled = CheckSurface.assemble(settling, normalized, read,
                Scopes.derived(Compilation.ofSource(TWO_DEFINITIONS, "Main").db(), "m").value(),
                Map.of(), FakeTables.classify(settling.module()));
        assertNotNull(assembled, "the assembly is made, so the refusal below is about the pairing");
        assertNotEquals(declarations.fns(), assembled.desugaredFrom(),
                "the two readings are two sets of definitions, or this says nothing");

        assertThrows(IllegalArgumentException.class,
                () -> Prepared.prepare(declarations, assembled),
                "the witness says what the desugaring answered for each definition, and the"
                        + " assembly was joined from something else");
    }

    /** Two definitions with different bodies, so that one standing in for the other is a thing to
     *  write and a thing to tell apart. */
    private static final String TWO_DEFINITIONS = """
            module m exposing ( Wrapped )

            data Wrapped = Int

            let first (n: Int) : Int = n
            let second (n: Int) : Int = 0
            """;

    /** A module whose clause writes a construction, so that what a name denotes decides what the
     *  normalized declaration is. */
    private static final String WITH_A_CONSTRUCTION = """
            module m exposing ( Wrapped, Amount )

            data Wrapped = Int
            data Amount = Int
                invariant ok = Wrapped(value) == Wrapped(0)

            let wrap (n: Int) : Wrapped = Wrapped(n)
            """;

    private static List<Normalized.Def> normalizedIn(Desugared.Module module) {
        List<Normalized.Def> out = new ArrayList<>();
        for (Derived.Def def : module.defs()) {
            out.add(def.declaration());
        }
        return out;
    }

    private static Desugared.Module declarationsOf(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        Desugared.Module module = compilation.db().ask(new Shapes.Desugared("m")).value();
        assertNotNull(module, "the source under test does not get as far as being desugared");
        return module;
    }

    private static CheckSurface assemblyOf(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        CheckSurface surface = compilation.db().ask(new Shapes.CheckSurface("m")).value();
        assertNotNull(surface, "the source under test does not get as far as being assembled");
        return surface;
    }
}
