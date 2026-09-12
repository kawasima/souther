package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.DefaultStdlib;
import souther.compiler.ast.Hir;
import souther.compiler.core.Core;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.Names;
import souther.compiler.types.Refinement;
import souther.compiler.types.ResolvedCase;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * What selecting a case covers, and where that is worked out.
 *
 * <p>A case is two facts of different phases. What tests and reads a value is the
 * {@link souther.compiler.types.CaseSelector}, which says all of itself wherever it is written and
 * is held to that by {@code ACaseIsNotHalfDecidedTest}. What selecting it <em>covers</em> is not
 * like that: a case that is a sum stands for the leaves under it, and which leaves those are is a
 * fact about the declarations this compile read. So the atoms are not a component of the selector —
 * they are what {@link CaseSpace} adds when it resolves one, and {@link ResolvedCase} is the pair.
 *
 * <p>Held here and not in {@code types} for that reason. An invariant that needs {@link Symbols} to
 * state was never an invariant of a selector.
 */
class WhatSelectingACaseCoversIsResolvedWhereTheSubjectIsTest {

    /** A sum two deep, a leaf two cases reach, and a case that carries a primitive. */
    private static final String MODULE = """
            module m

            data Station
            data Hospital
            data Clinic
            data Renkei
            data OnceKind   = Station | Hospital
            data Outpatient = Station | Clinic
            data VisitKind  = OnceKind | Renkei
            data Both       = OnceKind | Outpatient
            """;

    private final Hir.Module module = resolved(MODULE);
    private final Symbols symbols = TypeChecker.symbols(module, DefaultStdlib.get());

    private final PublishedDeclarations said = ScopedDeclarations.of(symbols);

    private final DeclarationKinds forms = ScopedDeclarations.kindsOf(symbols);

    @Test
    void aCaseThatIsASumCoversTheLeavesUnderIt() {
        assertEquals(List.of("Station", "Hospital"), covered("VisitKind", "OnceKind"),
                "an arm naming the inner sum answers for everything under it");
    }

    @Test
    void aCaseThatIsALeafCoversItself() {
        assertEquals(List.of("Renkei"), covered("VisitKind", "Renkei"));
    }

    /**
     * Two cases of one sum may cover one atom, and that is representable.
     *
     * <p>{@code Both} reaches {@code Station} through each of its cases. Nothing here refuses it:
     * what an overlap means to a {@code match} — which arm takes such a value, and whether writing
     * both is a mistake — is the match's to decide (#966), and a coverage that could not say two
     * arms overlap would have decided it by being unable to state it.
     */
    @Test
    void twoCasesOfOneSumMayCoverOneAtom() {
        assertEquals(List.of("Station", "Hospital"), covered("Both", "OnceKind"));
        assertEquals(List.of("Station", "Clinic"), covered("Both", "Outpatient"));
    }

    /** The order is the subject's, so the atoms of one case keep the order that case declares. */
    @Test
    void theAtomsOfACaseComeInTheOrderItDeclaresThem() {
        assertEquals(List.of("Station", "Hospital", "Renkei"),
                CaseSpace.of(type("VisitKind"), forms, said).selectors().stream()
                        .flatMap(each -> each.atoms().stream()).map(TypeSymbol::name).toList());
    }

    /**
     * An optional's carrier covers itself and not what it holds.
     *
     * <p>{@code Some<VisitKind>} binds a {@code VisitKind}, so what it <em>holds</em> reaches three
     * leaves. What an arm over the optional answers for is still {@code Some}: a match over an
     * optional has two arms, and reading the coverage through the element would hold them against
     * cases no optional has.
     */
    @Test
    void anOptionalsCarrierCoversItselfAndNotWhatItHolds() {
        CaseSpace space = CaseSpace.of(Type.option(type("VisitKind")), forms, said);
        assertInstanceOf(CaseSpace.Optional.class, space);
        ResolvedCase some = space.selector(TypeSymbol.SOME, said);
        assertEquals(List.of(TypeSymbol.SOME), some.atoms());
        assertEquals(List.of(TypeSymbol.NONE), space.selector(TypeSymbol.NONE, said).atoms());
        assertInstanceOf(Refinement.OptionPresent.class, some.refinement(),
                "the carrier still binds the element; only what it covers is its own name");
        assertEquals(type("VisitKind"), some.bound());
    }

    /** A subject with no cases hands out none, so there is nothing to resolve. */
    @Test
    void aSubjectWithNoCasesHasNothingToCover() {
        assertEquals(List.of(), CaseSpace.of(type("Station"), forms, said).selectors());
    }

    /**
     * What a selector is, and what it therefore cannot answer on its own.
     *
     * <p>A name and a refinement, and nothing about the program the case was written in. That is
     * what makes it sayable wherever it is written — and it is also the whole of why the atoms are
     * a second value rather than a third component: an invariant that needs {@link Symbols} to
     * state was never an invariant of a selector.
     */
    @Test
    void aSelectorIsANameAndARefinementAndSaysNothingAboutTheProgram() {
        ResolvedCase once = resolvedCase("VisitKind", "OnceKind");
        assertEquals(once.name(), once.selector().name());
        assertEquals(once.refinement(), once.selector().refinement());
        assertEquals(List.of("name", "refinement"),
                java.util.Arrays.stream(once.selector().getClass().getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName).toList(),
                "a selector is a name and a refinement; what it covers is not one of its parts");
    }

    /**
     * And what goes downstream is the resolution, not the selector alone.
     *
     * <p><b>The boundary the other way round from where it began.</b> {@code Core} used to carry the
     * selector, on the ground that a backend tests and reads a value and wants nothing about the
     * program around it. That is true of a backend and false of every other reader: which case of a
     * subject an arm picked is a question about the leaves, and below the checker there are no
     * declarations left to work them out from. Handed the selector alone, such a reader answered
     * from the name — and {@code OnceKind} is one name over two leaves, so the answer was a place
     * the reading of a position never holds (#1252).
     *
     * <p>Asked of a compiled body rather than of the shape of the types, because the claim is that
     * the atoms actually survive the checker. Nothing here holds {@link Symbols}: if what an arm
     * covers could only be had by asking the declarations again, this could not be written.
     */
    @Test
    void whatGoesDownstreamIsTheResolutionAndNotTheSelectorAlone() {
        Core.Case arm = armSelecting(inner());

        assertEquals(List.of("Station", "Hospital"),
                arm.selectedCase().orElseThrow().atoms().stream().map(TypeSymbol::name).toList(),
                "a reader of Core says what the arm covers without a declaration in hand");
        assertEquals(named("OnceKind"), arm.selectedCase().orElseThrow().selector().name(),
                "and the selector a backend emits is still there, as a projection of it");
    }

    /** The model an arm over the inner sum is written in. */
    private static String inner() {
        return MODULE + """

                data Ack = { at: String }

                behavior visit : (k: VisitKind) -> Ack
                let visit (k) =
                    match k with
                        | OnceKind as x -> Ack { at = "once" }
                        | Renkei as r -> Ack { at = "renkei" }
                """;
    }

    /** The first arm of the first {@code match} of {@code visit}, as the checker built it. */
    private static Core.Case armSelecting(String source) {
        Compilation compilation =
                Compilation.ofSources(List.of(source), ModulePath.EMPTY);
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Core body = compilation.db().ask(new Bodies.Checked(module)).value()
                .behaviorBodies().get("visit");
        assertNotNull(body, "the model under test compiles");
        return firstMatch(body).cases().get(0);
    }

    private static Core.Match firstMatch(Core at) {
        if (at instanceof Core.Match match) {
            return match;
        }
        Core.Match[] found = new Core.Match[1];
        Core.forEachChild(at, each -> {
            if (found[0] == null) {
                found[0] = firstMatch(each);
            }
        });
        return found[0];
    }

    /** Kept in what a declaration comes to, so it compares as a value and not as an object. */
    @Test
    void twoResolvedCasesOfOneCaseAreOneValue() {
        assertEquals(resolvedCase("VisitKind", "OnceKind"), resolvedCase("VisitKind", "OnceKind"));
        assertEquals(resolvedCase("VisitKind", "OnceKind").hashCode(),
                resolvedCase("VisitKind", "OnceKind").hashCode());
        assertNotEquals(resolvedCase("Both", "OnceKind"), resolvedCase("Both", "Outpatient"));
    }

    // --- helpers ---------------------------------------------------------------------------------

    private List<String> covered(String subject, String caseName) {
        return resolvedCase(subject, caseName).atoms().stream().map(TypeSymbol::name).toList();
    }

    private ResolvedCase resolvedCase(String subject, String caseName) {
        return CaseSpace.of(type(subject), forms, said).selector(named(caseName), said);
    }

    private Type type(String name) {
        return Type.ref(named(name));
    }

    private TypeSymbol named(String type) {
        for (Hir.Def d : module.defs()) {
            if (d.name().equals(type)) {
                return d.declares();
            }
        }
        throw new AssertionError("the module does not declare " + type);
    }

    private static Hir.Module resolved(String source) {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("m.sou", source);
        return Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY)
                .db().ask(new Names.Resolved("m")).value();
    }
}
