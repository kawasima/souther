package souther.compiler.codegen;

import souther.compiler.diag.SourceLayouts;
import org.junit.jupiter.api.Test;

import souther.compiler.ast.Hir;
import souther.compiler.check.Boundary;
import souther.compiler.check.DerivedSymbols;
import souther.compiler.jvm.GeneratedClass;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Compilation;
import souther.compiler.query.Names;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A codec emitter writes the alternatives it was handed, and cannot reach past them to the type.
 *
 * <p>Holding the generated classes against each other says the emitters <em>agree</em>; it does not
 * say there is one answer. Five emitters each writing {@code atom.name()} agree on every input there
 * is, so no comparison of their output can tell that apart from one answer with five readers — the
 * shape #994 is about. What tells them apart is handing an emitter something the type would never
 * have produced and seeing it come out: a key nothing derives and a shorter list of alternatives
 * than the sum has. An emitter that went back to the type and the symbols answers with the sum's own
 * key and the sum's own atoms, and these fail.
 *
 * <p>This is what the settled value being a <em>parameter</em> buys, and it is why the emitters take
 * one rather than each calling {@code Boundary.of} for itself. The place that does call it is the
 * ownership point in {@link ValueClassGen} and {@link Backend}, which is where the answer is settled
 * and not where it is used.
 */
class AnEmitterWritesWhatItWasHandedTest {

    private static final String SENTINEL_KEY = "__no_derivation_writes_this__";

    private static final String MODULE = """
            module m

            data Draft
            data Card = { no: String }
            data Cash = Int
            data Payment = Draft | Card | Cash

            data Prospecting
            data Negotiation
            data Won
            data Stage = Prospecting | Negotiation | Won
            """;

    private final Compilation compilation = Compilation.ofSources(List.of(MODULE), ModulePath.EMPTY);
    private final Hir.Module module =
            compilation.db().ask(new Names.Resolved("m")).value();
    /** The world an emitter reads against, which is the derived one. */
    private final DerivedSymbols symbols =
            souther.compiler.query.Scopes.derived(compilation.db(), "m").value();
    private final CodecGen codec = codecGen();

    @Test
    void theSumEncoderWritesTheKeyItWasHanded() {
        String written = text(codec.generateSumEncoder(sum("Payment"), oneAtom("Payment", SENTINEL_KEY)));
        assertTrue(written.contains(SENTINEL_KEY), "the encoder did not write the key it was handed");
    }

    @Test
    void theSumDecoderReadsTheKeyItWasHanded() {
        String read = text(codec.generateSumDecoder(sum("Payment"),
                oneAtom("Payment", SENTINEL_KEY), CodecGen.Src.JSON));
        assertTrue(read.contains(SENTINEL_KEY), "the decoder did not read the key it was handed");
    }

    /**
     * Handed one alternative of the three, the encoder dispatches over that one.
     *
     * <p>The tag is the atom's name on both sides, so a sentinel tag cannot be handed in — the
     * projection from atom to tag belongs to {@code Alternatives} and there is no second one to
     * differ from it. What can be handed in is a set of alternatives the type does not have, and an
     * emitter working the atoms out again writes all three.
     */
    @Test
    void theSumEncoderDispatchesOverTheAlternativesItWasHanded() {
        String written = text(codec.generateSumEncoder(sum("Payment"), oneAtom("Payment", SENTINEL_KEY)));
        assertTrue(written.contains("Draft"), "the one alternative handed in is missing");
        assertFalse(written.contains("Card"), "the encoder went back to the sum for its atoms");
        assertFalse(written.contains("Cash"), "the encoder went back to the sum for its atoms");
    }

    @Test
    void theEnumDecoderReadsTheNamesItWasHanded() {
        String read = text(codec.generateEnumSumDecoder(sum("Stage"),
                new Boundary.Alternatives(List.of(atom("Prospecting")),
                        new Boundary.Representation.Enumeration()),
                CodecGen.Src.JSON));
        assertTrue(read.contains("Prospecting"), "the one name handed in is missing");
        assertFalse(read.contains("Negotiation"), "the decoder went back to the sum for its atoms");
        assertFalse(read.contains("Won"), "the decoder went back to the sum for its atoms");
    }

    // --- helpers ---------------------------------------------------------------------------------

    /** The sum's first atom alone, under a key no derivation produces. */
    private Boundary.Alternatives oneAtom(String sumName, String key) {
        List<TypeSymbol> atoms = Boundary.of(Type.ref(sum(sumName).declares()), symbols).atoms();
        return new Boundary.Alternatives(List.of(atoms.get(0)),
                new Boundary.Representation.Discriminated(key));
    }

    /** A classfile's bytes as text, which is enough to find a constant-pool UTF-8 entry in. */
    private static String text(byte[] classfile) {
        return new String(classfile, StandardCharsets.ISO_8859_1);
    }

    private TypeSymbol atom(String name) {
        return def(name).declares();
    }

    private Hir.SumData sum(String name) {
        return (Hir.SumData) def(name);
    }

    private Hir.Def def(String name) {
        for (Hir.Def d : module.defs()) {
            if (d.name().equals(name)) {
                return d;
            }
        }
        throw new AssertionError("the module does not declare " + name);
    }

    private CodecGen codecGen() {
        Map<String, List<GeneratedClass>> caseToSums = new HashMap<>();
        for (Hir.Def d : module.defs()) {
            if (d instanceof Hir.SumData s) {
                for (Hir.Name c : s.cases()) {
                    caseToSums.computeIfAbsent(Backend.names(c).name(), k -> new java.util.ArrayList<>())
                            .add(new GeneratedClass.Value(s.declares()));
                }
            }
        }
        return new CodecGen(new CodegenContext("m", symbols, symbols.library().kernelSignatures(),
                caseToSums, Map.of(), true, Set.of(), Map.of(), SourceLayouts.NONE));
    }

}
