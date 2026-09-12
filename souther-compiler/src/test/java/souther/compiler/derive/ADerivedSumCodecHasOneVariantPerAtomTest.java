package souther.compiler.derive;

import org.junit.jupiter.api.Test;

import souther.compiler.DefaultStdlib;
import souther.compiler.ast.Hir;
import souther.compiler.check.AtomSpace;
import souther.compiler.check.ScopedDeclarations;
import souther.compiler.check.Boundary;
import souther.compiler.check.TypeChecker;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Compilation;
import souther.compiler.query.Names;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a derived sum codec dispatches over: the atoms of the sum, one variant each, in their order.
 *
 * <p>The derivation used to descend the cases itself and ask {@code List.contains} of an
 * {@link Hir.Name}, which is a written occurrence and what it denotes. A leaf two of the sum's
 * cases reach is written twice and is one type, so it got a variant from each of them and the codec
 * carried one case twice (#990). Nothing said so: both readers take the first variant whose tag or
 * case answers, so the second was never reached.
 *
 * <p>Held against {@link AtomSpace} rather than against a written-out list, so what a wire case is
 * about and what an atom is stay one answer. The decoder and the encoder used to be two lists on the
 * declaration, and this held each against the atoms and the two against each other; there is one
 * list now ({@link Boundary}), so the agreement is by construction and what is left to hold is that
 * the one list is the atoms.
 */
class ADerivedSumCodecHasOneVariantPerAtomTest {

    /** A leaf `Both` reaches through `OnceKind` and again through `Outpatient`. */
    private static final String MODULE = """
            module m

            data Station
            data Hospital
            data Clinic
            data OnceKind   = Station | Hospital
            data Outpatient = Station | Clinic
            data Both       = OnceKind | Outpatient
            """;

    private final Hir.Module derived = derive(MODULE);

    @Test
    void aLeafReachedThroughTwoCasesIsOneVariant() {
        assertEquals(List.of("Station", "Hospital", "Clinic"), tagsOf("Both"));
    }

    @Test
    void theVariantsAreTheAtomsInTheirOrder() {
        assertEquals(atomsOf("Both"), wireAtoms("Both"));
    }

    /** The tag names the case it dispatches to. */
    @Test
    void theTagAndTheCaseNameOneType() {
        assertEquals(names(atomsOf("Both")), tagsOf("Both"));
    }

    /** A sum no case of which is reached twice is unchanged by any of this. */
    @Test
    void aSumWithNoSharedLeafIsTheLeavesUnderIt() {
        assertEquals(List.of("Station", "Hospital"), tagsOf("OnceKind"));
    }

    // --- helpers ---------------------------------------------------------------------------------

    private List<String> tagsOf(String sum) {
        return settled(sum).wireCases().stream().map(Boundary.WireCase::tag).toList();
    }

    private List<TypeSymbol> wireAtoms(String sum) {
        return settled(sum).wireCases().stream().map(Boundary.WireCase::atom).toList();
    }

    private Boundary.Alternatives settled(String sum) {
        return Boundary.of(Type.ref(sumData(sum).declares()),
                ScopedDeclarations.kindsOf(TypeChecker.symbols(derived, DefaultStdlib.get())),
                ScopedDeclarations.of(TypeChecker.symbols(derived, DefaultStdlib.get())));
    }

    private List<TypeSymbol> atomsOf(String sum) {
        return AtomSpace.subjectAtoms(Type.ref(sumData(sum).declares()),
                ScopedDeclarations.of(TypeChecker.symbols(derived, DefaultStdlib.get())));
    }

    private static List<String> names(List<TypeSymbol> atoms) {
        return atoms.stream().map(TypeSymbol::name).toList();
    }

    private Hir.SumData sumData(String name) {
        for (Hir.Def d : derived.defs()) {
            if (d.name().equals(name)) {
                return (Hir.SumData) d;
            }
        }
        throw new AssertionError("the module does not declare " + name);
    }

    private static Hir.Module derive(String source) {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("m.sou", source);
        // The module as it is below the derivation, which is the resolved one: what deriving
        // establishes is carried by the declarations and not written back into the tree.
        return Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY)
                .db().ask(new Names.Resolved("m")).value();
    }
}
