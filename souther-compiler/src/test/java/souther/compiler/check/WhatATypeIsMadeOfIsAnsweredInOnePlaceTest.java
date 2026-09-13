package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.test.RepositoryLayout;

import souther.compiler.DefaultStdlib;
import souther.compiler.ast.Hir;
import souther.compiler.diag.msg.MessageValues;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Compilation;
import souther.compiler.query.Names;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a value of a type can be, when a case of it is itself a sum.
 *
 * <p>Four readers used to descend a sum's cases themselves — two in {@link TypeOps}, one keyed on
 * the declaration rather than the type, and one in the derivation keyed on the written name. All
 * four are gone: {@link AtomSpace} is the descent, and every reader asks it. What each of them was
 * handed differed and the descent no longer does — a sum asked about through its declaration is
 * asked about as its type.
 *
 * <p>What a reader answers <em>about</em> a type it was handed stayed with the reader, which is
 * where it belongs: a field read asks whether there are cases to read at all, and a type that is
 * its own one leaf is not that. Held here at what it produces rather than at the call it makes.
 */
class WhatATypeIsMadeOfIsAnsweredInOnePlaceTest {

    /** Read once: what this asks of it does not change between its checks. */
    private static final RepositoryLayout REPOSITORY = RepositoryLayout.ofWorkingDirectory();

    /** A sum two deep, and a leaf two of its cases reach. */
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

    @Test
    void aCaseThatIsASumIsTheLeavesUnderIt() {
        assertEquals(List.of("Station", "Hospital", "Renkei"), shown(leavesOf("VisitKind")),
                "a value of the outer sum is one of the leaves, not one of the two cases");
    }

    /**
     * The declaration reaches {@code Station} through {@code OnceKind} and again through
     * {@code Outpatient}. It is one type, so it is one leaf, and it keeps the place it was first
     * reached at — the order a derived codec writes its variants in (spec §sum-discrimination).
     */
    @Test
    void aLeafReachedTwiceIsOneLeafWhereItWasFirstReached() {
        assertEquals(List.of("Station", "Hospital", "Clinic"), shown(leavesOf("Both")));
    }

    /**
     * The same where what two cases reach is a sum rather than a leaf.
     *
     * <p>Held apart from the leaf-level one because the descent stops differently: the second
     * reach of {@code N} is not descended at all, where the second reach of a leaf is descended
     * and discarded. What the two must agree on is that the leaves under it are contributed once,
     * where {@code N} was first reached.
     */
    @Test
    void aNestedSumReachedThroughTwoCasesContributesItsLeavesOnce() {
        Hir.Module shared = resolved("""
                module m

                data R
                data T
                data P
                data Q
                data N   = R | T
                data A   = N | P
                data B   = N | Q
                data Top = A | B
                """);
        assertEquals(List.of("R", "T", "P", "Q"),
                shown(AtomSpace.subjectAtoms(Type.ref(named(shared, "Top")),
                        ScopedDeclarations.of(TypeChecker.symbols(shared, DefaultStdlib.get())))));
    }

    @Test
    void aTypeThatIsNoSumIsTheOneLeafItIs() {
        assertEquals(List.of("Station"), shown(leavesOf("Station")));
    }

    /**
     * A union descends a member that is a sum, as a declared case is descended.
     *
     * <p>And answers the same however the set it holds was built. A union states no order of its
     * own — {@code Type.Union} is a set — so the members are taken in their name's order rather
     * than in whichever order the set iterates: an order nothing decided is one that can come out
     * differently between two runs, and a derived artifact is written in it.
     */
    @Test
    void aUnionAnswersTheSameWhicheverWayItsMembersWereCollected() {
        TypeSymbol once = named("OnceKind");
        TypeSymbol renkei = named("Renkei");
        List<String> expected = List.of("Station", "Hospital", "Renkei");

        assertEquals(expected, shown(AtomSpace.subjectAtoms(
                Type.union(new java.util.LinkedHashSet<>(List.of(once, renkei))), said)));
        assertEquals(expected, shown(AtomSpace.subjectAtoms(
                Type.union(new java.util.LinkedHashSet<>(List.of(renkei, once))), said)),
                "the union written the other way round is the same union");
    }

    /** A sum naming itself is refused where it is written; the descent only has to come back. */
    @Test
    void aSumReachingItselfComesBack() {
        Hir.Module itself = resolved("""
                module m

                data A
                data S = A | S
                """);
        assertEquals(List.of("A"),
                shown(AtomSpace.subjectAtoms(Type.ref(named(itself, "S")),
                        ScopedDeclarations.of(TypeChecker.symbols(itself, DefaultStdlib.get())))));
    }

    /** What a sum declares, asked of the declaration — the same leaves, and its own name is not one. */
    @Test
    void aSumsOwnDeclarationAnswersWithTheSameLeaves() {
        Hir.SumData both = (Hir.SumData) declaration("Both");
        assertEquals(shown(leavesOf("Both")),
                shown(AtomSpace.subjectAtoms(Type.ref(both.declares()), said)));
    }

    // --- what each reader answers about a type that is no sum ------------------------------------

    /**
     * A field read on a type that is no sum is not told to open its cases.
     *
     * <p>The one answer {@code TypeOps.sumCases} carried that the descent does not: it said null
     * where a type is its own single leaf, and the field read turned that into which of two things
     * the author is told. The descent answers {@code [Station]} there — a leaf is what it is — so
     * the reader asks whether the type is a sum itself, and this is where that is held.
     */
    @Test
    void aFieldReadOnATypeThatIsNoSumIsNotToldToOpenIt() {
        souther.compiler.diag.CompileException refused = org.junit.jupiter.api.Assertions
                .assertThrows(souther.compiler.diag.CompileException.class,
                        () -> souther.compiler.Compiler.compile(MODULE + """

                                let f (s: Station) : Int = s.x
                                """));
        org.junit.jupiter.api.Assertions.assertInstanceOf(
                souther.compiler.diag.msg.DeclarationMessage.CannotReadAFieldOnThisValue.class,
                refused.diagnostic().said(),
                "a data with no cases has none to read, which is not the same as having itself");
    }

    @Test
    void aLeafSetAnswersForATypeThatIsNoSum() {
        assertEquals(List.of("Station"), shown(AtomSpace.subjectAtoms(Type.ref(named("Station")), said)));
    }

    /**
     * Where the one answer that changed is seen from outside.
     *
     * <p>{@code sumCases} named a leaf once per path that reached it, and the cases without the
     * field are listed from what it answered, so a reader of a diamond was told about one case
     * twice. This is the only reader of that list, which is what makes the change to it this
     * small.
     */
    @Test
    void aCaseReachedTwiceIsNamedOnceInWhatHasNoSuchField() {
        souther.compiler.diag.CompileException refused = org.junit.jupiter.api.Assertions
                .assertThrows(souther.compiler.diag.CompileException.class,
                        () -> souther.compiler.Compiler.compile(MODULE + """

                                let f (b: Both) : Int = b.x
                                """));
        assertEquals("E1321", refused.diagnostic().code());
        assertEquals(List.of("Station, Hospital, Clinic"),
                refused.diagnostic().notes().stream()
                        .map(n -> String.valueOf(MessageValues.of(n.said()).get("cases")))
                        .toList());
    }

    /**
     * The two a selector's own coverage will be read from (#966's next step): a primitive case is
     * the one atom it is, and an optional has none of its own — its carriers are named by
     * {@code Option} and given where a subject's cases are worked out, not descended to here.
     */
    @Test
    void aPrimitiveIsOneAtomAndAnOptionalHasNone() {
        assertEquals(List.of("Int"), shown(AtomSpace.subjectAtoms(Type.INT, said)));
        assertEquals(List.of(), shown(AtomSpace.subjectAtoms(Type.option(Type.INT), said)));
    }

    @Test
    void anOutputsCasesAreEmptyWhereTheOutputNamesNoCase() {
        assertEquals(Set.of(), TypeOps.outputCases(Type.INT, said),
                "a primitive output is not a case list, whatever leaf its name would be");
    }


    // --- who can build a case closure at all -----------------------------------------------------

    /**
     * A sum's own case list is read at four call sites, all of them in the package that declares it.
     *
     * <p>What a second closure would be built out of is one layer of a sum plus the declarations:
     * hold both and a loop away is a descent. The declarations are everywhere, so the layer is the
     * part that can be held down, and {@code TypeOps.caseNames} is package-private for that reason.
     * That is where most of this rule lives — no code outside {@code check} can write a second
     * closure at all, and javac says so rather than a scan guessing at spellings.
     *
     * <p>What is left for a scan is the package itself, and it counts <em>calls</em> rather than
     * files. A file already reading one layer is where a second reading is cheapest to add, so a
     * check that stops at the file name would see nothing: the reader most likely to grow a descent
     * is one of the four already listed.
     *
     * <p>Called out by file and not by line. A line number moves whenever anything above it is
     * edited, and a tripwire that goes red on an unrelated edit is one that gets deleted. The name
     * repeated is what a second call site in an existing reader shows up as.
     *
     * <p>Three of the four are not the descent and read one layer on purpose: a {@code match} is
     * decided over the cases the sum declared and not over what they reach (spec
     * §an-or-pattern-binds-the-sum-and-opens-nothing), which is #966 itself. When that changes they
     * ask {@link AtomSpace} and this list gets shorter, never longer.
     *
     * <p><b>What this does not see.</b> A descent written around a call that is already here. Both
     * halves are about who can reach the material — the visibility bounds who may, and this bounds
     * how many do — and neither reads what a caller does with what it got.
     */
    @Test
    void aSumsOwnCaseListIsReadAtOneCallSiteInTheOnePackageThatCanReadIt() throws IOException {
        List<Path> sources = sourcesOfTheCheckPackage();
        assertTrue(sources.size() > 100,
                () -> "the scan found only " + sources.size() + " sources, which is not the package");

        List<String> calls = new ArrayList<>();
        for (Path source : sources) {
            String code = code(source);
            assertFalse(code.contains("static souther.compiler.check.TypeOps.caseNames"),
                    () -> source.getFileName() + " imports the case list under a bare name, "
                            + "which is a call site this counts by the spelling it is written in");
            int here = count(QUALIFIED, code);
            if (source.getFileName().toString().equals(DECLARES_IT)) {
                // Its own calls are written without the class name, and the declaration is not one.
                here += count(UNQUALIFIED, code) - count(DECLARATION, code);
            }
            for (int each = 0; each < here; each++) {
                calls.add(source.getFileName().toString());
            }
        }
        assertEquals(List.of("TypeOps.java"), calls,
                "what a sum's cases are is asked of what the declaration says, so the tree it was"
                        + " written in is read in one place and by nothing that answers about kind");
    }

    /** The file that declares the case list, whose own calls to it carry no class name. */
    private static final String DECLARES_IT = "TypeOps.java";

    private static final Pattern QUALIFIED = Pattern.compile("TypeOps\\.caseNames\\s*\\(");

    /** A call written without the class name — {@code caseNamesOf} is a different reader and is not
     *  one, which is why the name is closed with its own parenthesis. */
    private static final Pattern UNQUALIFIED = Pattern.compile("(?<![\\w.])caseNames\\s*\\(");

    /** The declaration, which {@link #UNQUALIFIED} matches and which is no call. */
    private static final Pattern DECLARATION = Pattern.compile("caseNames\\s*\\(\\s*Hir\\.SumData");

    private static int count(Pattern pattern, String code) {
        int found = 0;
        Matcher at = pattern.matcher(code);
        while (at.find()) {
            found++;
        }
        return found;
    }

    private static List<Path> sourcesOfTheCheckPackage() throws IOException {
        List<Path> sources = new ArrayList<>();
        for (Path each : mainSources()) {
            if (each.getParent().getFileName().toString().equals("check")) {
                sources.add(each);
            }
        }
        return sources;
    }

    private static String code(Path source) throws IOException {
        return withoutComments(Files.readString(source, StandardCharsets.UTF_8));
    }

    /**
     * The source with its comments taken out.
     *
     * <p>So that a javadoc naming the rule does not read as a breach of it. Lexical and small: it
     * follows string and character literals so a {@code //} inside one is not a comment, and keeps
     * what is inside them, so the worst it can do is leave a comment standing.
     */
    private static String withoutComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int at = 0;
        while (at < source.length()) {
            char here = source.charAt(at);
            char next = at + 1 < source.length() ? source.charAt(at + 1) : '\0';
            if (here == '/' && next == '/') {
                while (at < source.length() && source.charAt(at) != '\n') {
                    at++;
                }
            } else if (here == '/' && next == '*') {
                at += 2;
                while (at + 1 < source.length()
                        && !(source.charAt(at) == '*' && source.charAt(at + 1) == '/')) {
                    at++;
                }
                at = Math.min(source.length(), at + 2);
            } else if (here == '"' || here == '\'') {
                out.append(here);
                at++;
                while (at < source.length() && source.charAt(at) != here) {
                    if (source.charAt(at) == '\\' && at + 1 < source.length()) {
                        out.append(source.charAt(at));
                        at++;
                    }
                    out.append(source.charAt(at));
                    at++;
                }
                if (at < source.length()) {
                    out.append(source.charAt(at));
                    at++;
                }
            } else {
                out.append(here);
                at++;
            }
        }
        return out.toString();
    }

    private static List<Path> mainSources() throws IOException {
        return REPOSITORY.mainJavaSources();
    }

    // --- helpers ---------------------------------------------------------------------------------


    private List<TypeSymbol> leavesOf(String type) {
        return AtomSpace.subjectAtoms(Type.ref(named(type)), said);
    }

    private TypeSymbol named(String type) {
        return named(module, type);
    }

    private static TypeSymbol named(Hir.Module m, String type) {
        return declarationIn(m, type).declares();
    }

    private Hir.Def declaration(String type) {
        return declarationIn(module, type);
    }

    private static Hir.Def declarationIn(Hir.Module m, String type) {
        for (Hir.Def d : m.defs()) {
            if (d.name().equals(type)) {
                return d;
            }
        }
        throw new AssertionError("the module does not declare " + type);
    }

    private static List<String> shown(List<TypeSymbol> names) {
        return names.stream().map(TypeSymbol::name).toList();
    }

    private static Hir.Module resolved(String source) {
        Map<String, String> byId = new LinkedHashMap<>();
        byId.put("m.sou", source);
        return Compilation.ofDocuments(byId, Set.of(), ModulePath.EMPTY)
                .db().ask(new Names.Resolved("m")).value();
    }
}
