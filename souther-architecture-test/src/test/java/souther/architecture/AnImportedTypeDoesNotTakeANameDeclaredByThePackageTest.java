package souther.architecture;

import souther.test.RepositoryLayout;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;

import org.junit.jupiter.api.Test;

import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A name a package declares means that name inside the package.
 *
 * <p>Two packages may call two things by one name. A word that fits one bounded context fits
 * another, and asking every such pair to give one of them up would be a rule about the words rather
 * than about what they say — {@code Ordered} in a partition and {@code Ordered} in a query are two
 * answers to two questions, and nothing is clearer for one of them being renamed.
 *
 * <p>What is refused is one file rebinding the name. A single import wins over the package a
 * compilation unit is in, so
 *
 * <pre>
 * package a;
 * import b.X;
 * </pre>
 *
 * leaves the bare {@code X} in that file meaning {@code b.X} while the package around it declares
 * one of its own. A reader who knows where they are has to find the import before they know which
 * question they are looking at, a search for either finds both, and javac says nothing — the file
 * compiles and the two names are the same word.
 *
 * <p>The escape is to write the foreign type out where it is used. It is longer, and the length is
 * the point: such a file is holding two things one word names, and it says which it means at each
 * place it means one.
 *
 * <p><b>Whichever of the two ways the import is spelled.</b> {@code import static b.Outer.X} binds
 * {@code X} to a member type and shadows the package's own just as {@code import b.X} does, so the
 * rule is about what an import binds and not about the keyword in front of it. Which of them a
 * static import binds is the owner's answer, and it is asked of the compiler
 * ({@link MemberTypes}): the members of a type are the ones it inherits as well as the ones it
 * declares, so no reading of the owner's source text answers this. An owner nothing here resolves
 * is a third answer and is listed rather than let through
 * ({@link #everyStaticImportThatCouldTakeANameIsOneThisCanAnswerAbout}).
 *
 * <p>An on-demand import is not one of these. {@code import b.*} and {@code import static b.Outer.*}
 * are lower in precedence than the package's own members, so the bare name goes on meaning what the
 * package declares and nothing is rebound.
 *
 * <p><b>Which names a package declares is asked of the sources that compile with it.</b> A package
 * may be split across modules, and a name declared in another module's main sources is as much a
 * member of the package as one beside it. A test source is not: it is compiled apart, no main
 * source can see it, so it declares nothing a bare name in a main source could otherwise have
 * meant. So the main sources answer for every source, and a test source's own root answers for it
 * besides.
 */
class AnImportedTypeDoesNotTakeANameDeclaredByThePackageTest {

    private static final RepositoryLayout REPOSITORY = RepositoryLayout.ofWorkingDirectory();

    /**
     * Every source this repository holds, and not its main sources alone.
     *
     * <p>The reversal is the same wherever it is written. A test in {@code souther.compiler.partition}
     * importing a {@code query} type of the same name reads exactly as wrongly as a class beside it
     * would, and the tests are where most of the reading happens.
     */
    @Test
    void noSourceRebindsANameItsOwnPackageDeclares() {
        assertEquals(List.of(), read(repositorySources(), repositoryMembers()).rebindings(),
                "a file whose package declares this name and which imports another package's:"
                        + " inside it the bare name means the other one, and nothing says so."
                        + " Drop the import and write the foreign type out where it is used");
    }

    /**
     * And every static import that could have taken a name is one this could answer about.
     *
     * <p>Beside the rule rather than inside it. Whether such an import binds a type is the owner's
     * answer, and an owner outside this repository has none here — reported as no rebinding, the
     * check would be passing over exactly the case it cannot see, and reported as one it would be
     * refusing an import on the strength of not knowing what it names.
     */
    @Test
    void everyStaticImportThatCouldTakeANameIsOneThisCanAnswerAbout() {
        assertEquals(List.of(), read(repositorySources(), repositoryMembers()).unanswered(),
                "a static import whose simple name a package here declares, taken from an owner"
                        + " this repository does not declare: whether it binds a member type is"
                        + " that owner's answer and nothing here has it");
    }

    /**
     * And the roots this read are every place the repository keeps a Java source.
     *
     * <p>The population's own control. What is read is a root at a time, because that is what
     * decides which names a source resolves against; a source somewhere no root covers would be
     * passed over, and this check would go on reporting a pass about the rest.
     */
    @Test
    void everyJavaSourceUnderASourceTreeIsUnderOneOfTheRootsRead() {
        Set<String> read = new TreeSet<>();
        for (Root root : roots()) {
            root.sources().forEach(each -> read.add(named(each)));
        }
        Set<String> held = new TreeSet<>();
        REPOSITORY.filesUnderSourceTrees(".java").forEach(each -> held.add(named(each)));

        assertEquals(List.copyOf(held), List.copyOf(read),
                "a Java source the roots do not cover is one this passes over");
    }

    /**
     * What the rule holds and does not, on sources written to be either.
     *
     * <p>Here rather than by naming the pairs the repository happens to hold. That two packages both
     * declare {@code Ordered} is true today and is not something a check should hold them to, and a
     * fixture that named it would be this test asking for the duplication to stay.
     */
    @Test
    void aNameTheOwnPackageDoesNotDeclareIsImportedFreely() {
        assertEquals(List.of("p/C.java rebinds `A`, which p declares, to q.A"),
                rebindingsIn(Map.of("p/A.java", "package p; class A {}",
                        "q/A.java", "package q; class A {}",
                        "q/B.java", "package q; class B {}",
                        "p/C.java", "package p; import q.A; class C { A a; }")),
                "the name is the package's own and the import takes it");

        assertEquals(List.of(),
                rebindingsIn(Map.of("p/A.java", "package p; class A {}",
                        "q/A.java", "package q; class A {}",
                        "p/C.java", "package p; class C { q.A a; }")),
                "the foreign type written out takes no name");

        assertEquals(List.of(),
                rebindingsIn(Map.of("p/A.java", "package p; class A {}",
                        "q/A.java", "package q; class A {}",
                        "q/B.java", "package q; class B {}",
                        "p/C.java", "package p; import q.B; class C { B b; }")),
                "a name p declares nothing by is a name nothing here is about");
    }

    /**
     * The package an import comes from is the type's owner, and not the package it is written in.
     *
     * <p>A nested type is imported through whatever holds it, so an import from the file's own
     * package can still take the name: {@code import p.Outer.A} inside {@code p} leaves the bare
     * {@code A} meaning the nested one while {@code p} declares its own. What changes nothing is an
     * import of the very type the package declares — the name already meant that.
     */
    @Test
    void anImportFromTheOwnPackageTakesTheNameWhereItNamesSomethingElse() {
        Map<String, String> declared = Map.of("p/A.java", "package p; class A {}",
                "p/Outer.java", "package p; class Outer { static class A {} }");

        Map<String, String> throughAnOwner = new LinkedHashMap<>(declared);
        throughAnOwner.put("p/C.java", "package p; import p.Outer.A; class C { A a; }");
        assertEquals(List.of("p/C.java rebinds `A`, which p declares, to p.Outer.A"),
                rebindingsIn(throughAnOwner), "the owner is another type, so the name is taken");

        Map<String, String> theSameType = new LinkedHashMap<>(declared);
        theSameType.put("p/C.java", "package p; import p.A; class C { A a; }");
        assertEquals(List.of(), rebindingsIn(theSameType),
                "the import names what the package declares, and the name already meant it");
    }

    /**
     * A static import takes the name where it binds a member type, and not where it binds a member.
     *
     * <p>The three answers the owner gives. A member type shadows the package's own name exactly as
     * a plain import does; a field or a method is a different namespace and shadows nothing; and an
     * owner this repository does not declare is one nothing here can ask, which is said rather than
     * decided either way.
     */
    @Test
    void aStaticImportTakesTheNameWhereWhatItBindsIsAType() {
        Map<String, String> owner = Map.of("p/A.java", "package p; class A {}",
                "q/Outer.java",
                "package q; class Outer { static class A {} static int B; static void C() {} }");

        Map<String, String> aType = new LinkedHashMap<>(owner);
        aType.put("p/C.java", "package p; import static q.Outer.A; class C { A a; }");
        assertEquals(List.of("p/C.java rebinds `A`, which p declares, to q.Outer.A"),
                rebindingsIn(aType), "a member type takes the name, however the import is spelled");

        Map<String, String> aMember = new LinkedHashMap<>(owner);
        aMember.put("p/B.java", "package p; class B {}");
        aMember.put("p/C.java", "package p; import static q.Outer.B; class C { B b; }");
        assertEquals(List.of(), rebindingsIn(aMember),
                "a field is a different namespace and takes no type name");

        Map<String, String> onDemand = new LinkedHashMap<>(owner);
        onDemand.put("p/C.java", "package p; import static q.Outer.*; class C { A a; }");
        assertEquals(List.of(), rebindingsIn(onDemand),
                "an on-demand import is below the package's own members and rebinds nothing");
    }

    /**
     * A member type the owner inherits is one a static import binds, and takes the name.
     *
     * <p>The case a reading of the owner's source text cannot answer. {@code Child} declares
     * nothing, and {@code import static q.Child.A} binds {@code Base}'s {@code A} all the same —
     * so a check that looked for the declaration inside {@code Child} would find none, call it a
     * field or a method, and let the rebinding through.
     */
    @Test
    void aStaticImportOfAnInheritedMemberTypeTakesTheName() {
        assertEquals(List.of("p/C.java rebinds `A`, which p declares, to q.Child.A"),
                rebindingsIn(Map.of(
                        "q/Base.java", "package q; public class Base { public static class A {} }",
                        "q/Child.java", "package q; public class Child extends Base {}",
                        "p/A.java", "package p; class A {}",
                        "p/C.java", "package p; import static q.Child.A; class C { A a; }")),
                "the owner has the member type by inheriting it, and the import binds it");
    }

    /**
     * And what a static import binds is the compiler's answer, over types nothing here wrote.
     *
     * <p>Beside the fixtures, which pin what the rule does with each of the three answers. This
     * pins the answers themselves against a type this repository does not own and cannot edit:
     * {@code HashMap} has {@code SimpleEntry} by inheriting it from {@code AbstractMap}, and its
     * own body says nothing about it.
     */
    @Test
    void whatAnOwnerHasByANameIsAskedOfTheCompiler() {
        MemberTypes members = repositoryMembers();

        assertEquals(Boolean.TRUE, members.bindsATypeNamed("java.util.AbstractMap", "SimpleEntry"),
                "a member type the owner declares");
        assertEquals(Boolean.TRUE, members.bindsATypeNamed("java.util.HashMap", "SimpleEntry"),
                "and one it inherits, which no reading of its own text finds");
        assertEquals(Boolean.FALSE, members.bindsATypeNamed("java.util.HashMap", "size"),
                "a method is a different namespace and takes no type name");
        assertNull(members.bindsATypeNamed("nothing.declares.This", "A"),
                "and an owner nothing resolves is neither of those");
    }

    /** And an owner nothing here declares is one the check says it cannot answer about. */
    @Test
    void aStaticImportFromAnOwnerThisDoesNotDeclareIsSaidToBeUnanswered() {
        Found found = ofWritten(Map.of("p/A.java", "package p; class A {}",
                "p/C.java", "package p; import static x.Elsewhere.A; class C { A a; }"));

        assertEquals(List.of(), found.rebindings(), "nothing here says what it binds");
        assertEquals(List.of("p/C.java imports `A` from x.Elsewhere, which nothing here resolves"),
                found.unanswered(), "so the import is named rather than let through");
    }

    /**
     * A name written under one root resolves against the main sources of its package and against
     * its own root, and against no other root's tests.
     *
     * <p>Both directions, because only one of them is a rule. A test source sees the main classes
     * beside it, so a main class does take the name there; a main source sees no test class, and a
     * rule that took one to shadow would say a bare name in it means something it cannot mean.
     */
    @Test
    void aMainSourceDoesNotResolveAgainstTheTestsBesideIt() {
        Map<String, String> declaresA = Map.of("p/A.java", "package p; class A {}");
        Map<String, String> takesIt = Map.of("q/A.java", "package q; class A {}",
                "p/C.java", "package p; import q.A; class C { A a; }");

        assertEquals(List.of("p/C.java rebinds `A`, which p declares, to q.A"),
                read(inRoots(Map.of("main", declaresA), Map.of("test", takesIt)),
                        membersOf(merged(declaresA, takesIt))).rebindings(),
                "the test root resolves against the main sources of the package");

        assertEquals(List.of(),
                read(inRoots(Map.of("main", takesIt), Map.of("test", declaresA)),
                        membersOf(merged(declaresA, takesIt))).rebindings(),
                "a class declared only under a test root shadows nothing in a main source");
    }

    /**
     * A source this cannot parse is refused, and not read as one that declares nothing.
     *
     * <p>The parser recovers what it can and hands back a unit either way, so a file it choked on
     * contributes no declaration and no import — which reads exactly like a file that rebinds
     * nothing. What this check is worth is that it cannot pass quietly, and a source it never read
     * is the one way it could.
     */
    @Test
    void aSourceThisCannotParseIsRefusedRatherThanReadAsEmpty() {
        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> written(Map.of("p/A.java", "package p; class A {}",
                        "p/C.java", "package p; class C { import q.A; ((( }")));

        assertTrue(refused.getMessage().contains("p/C.java"),
                "the file is named, so a reader is told which source went unread: "
                        + refused.getMessage());
    }

    /** One compilation unit, as much of it as this question is about. */
    private record Unit(String where, String pkg, String root, boolean isMain,
                        Set<String> declares, List<Import> imports) {}

    /** One single import, which binds a name whatever the keyword in front of it is. */
    private record Import(String spelled, boolean isStatic) {

        String name() {
            return spelled.substring(spelled.lastIndexOf('.') + 1);
        }

        String owner() {
            int dot = spelled.lastIndexOf('.');
            return dot < 0 ? "" : spelled.substring(0, dot);
        }
    }

    /** One source root, whether it holds a module's main sources, and what is under it. */
    private record Root(String named, boolean isMain, List<Path> sources) {}

    /** A source handed to the parser, under the name whoever handed it over calls it. */
    private record Named(JavaFileObject source, String where) {}

    /**
     * What reading the sources came to: the names an import took, and the imports nothing here
     * could answer about.
     *
     * <p>Two lists and not one. An import this cannot resolve is not an import that binds nothing —
     * folded into either answer, the check would either pass over what it cannot see or refuse an
     * import for not being understood.
     */
    private record Found(List<String> rebindings, List<String> unanswered) {}

    /**
     * The rebindings among {@code units}, each said as the file, the name and what it was taken to.
     *
     * <p>What a name resolves against is where the source is compiled and not where the walk found
     * it: every main source of a package answers for the package, wherever in the reactor it is,
     * and a test source's own root answers for it besides. A test class of another root is nothing
     * a source here can see, so it declares no name a bare one could otherwise have meant.
     *
     * <p>Sorted, so that what a failure lists is the same on every machine and reads as a list of
     * places to go rather than as whatever order a walk happened to have.
     */
    private static Found read(List<Unit> units, MemberTypes members) {
        Map<String, Set<String>> byMain = new LinkedHashMap<>();
        Map<String, Map<String, Set<String>>> byTestRoot = new LinkedHashMap<>();
        for (Unit unit : units) {
            if (unit.isMain()) {
                byMain.computeIfAbsent(unit.pkg(), _ -> new LinkedHashSet<>())
                        .addAll(unit.declares());
            } else {
                byTestRoot.computeIfAbsent(unit.root(), _ -> new LinkedHashMap<>())
                        .computeIfAbsent(unit.pkg(), _ -> new LinkedHashSet<>())
                        .addAll(unit.declares());
            }
        }
        Set<String> found = new TreeSet<>();
        Set<String> unanswered = new TreeSet<>();
        for (Unit unit : units) {
            Set<String> own = new LinkedHashSet<>(byMain.getOrDefault(unit.pkg(), Set.of()));
            if (!unit.isMain()) {
                own.addAll(byTestRoot.getOrDefault(unit.root(), Map.of())
                        .getOrDefault(unit.pkg(), Set.of()));
            }
            for (Import each : unit.imports()) {
                if (!own.contains(each.name())) {
                    continue;
                }
                // A same-package import binds the name it already had, so nothing means anything
                // different for it being written. Whether it should be written at all is another
                // rule's question.
                if (!each.isStatic() && each.owner().equals(unit.pkg())) {
                    continue;
                }
                // A plain single import names a type by being one. A static import names whichever
                // member the owner has by that name, so the owner is asked.
                Boolean binds = each.isStatic()
                        ? members.bindsATypeNamed(each.owner(), each.name()) : Boolean.TRUE;
                if (Boolean.TRUE.equals(binds)) {
                    found.add(unit.where() + " rebinds `" + each.name() + "`, which " + unit.pkg()
                            + " declares, to " + each.spelled());
                } else if (binds == null) {
                    unanswered.add(unit.where() + " imports `" + each.name() + "` from "
                            + each.owner() + ", which nothing here resolves");
                }
            }
        }
        return new Found(List.copyOf(found), List.copyOf(unanswered));
    }

    /**
     * Every source root this repository has, and whether it holds a module's main sources.
     *
     * <p>Which directory is which root is the repository's answer ({@link RepositoryLayout}) and
     * never a reading of a path: worked out here, whether a source is a test source would be a
     * spelling this file agreed with by having been written the same way.
     */
    private static List<Root> roots() {
        List<Root> out = new ArrayList<>();
        for (Path module : REPOSITORY.modules()) {
            Path main = REPOSITORY.javaTreeOf(module, "main");
            Path test = REPOSITORY.javaTreeOf(module, "test");
            if (main != null) {
                out.add(new Root(named(main), true, sourcesUnder(main)));
            }
            if (test != null) {
                out.add(new Root(named(test), false, sourcesUnder(test)));
            }
        }
        return List.copyOf(out);
    }

    /** The sources this repository holds, read a root at a time. */
    private static List<Unit> repositorySources() {
        List<Unit> out = new ArrayList<>();
        JavaCompiler compiler = compiler();
        try (StandardJavaFileManager files =
                     compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            for (Root root : roots()) {
                Map<URI, Named> named = new LinkedHashMap<>();
                for (Path at : root.sources()) {
                    JavaFileObject source =
                            files.getJavaFileObjectsFromPaths(List.of(at)).iterator().next();
                    named.put(source.toUri(), new Named(source, named(at)));
                }
                out.addAll(parse(compiler, named, root));
            }
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
        return List.copyOf(out);
    }

    /** Sources written here, all under one main root. */
    private static List<Unit> written(Map<String, String> sources) {
        return inRoots(Map.of("main", sources));
    }

    /** The rebindings among sources written here, for a case that has nothing to say about
     *  roots. */
    private static List<String> rebindingsIn(Map<String, String> sources) {
        return ofWritten(sources).rebindings();
    }

    /** What reading sources written here comes to, resolved against those same sources. */
    private static Found ofWritten(Map<String, String> sources) {
        return read(written(sources), membersOf(sources));
    }

    /**
     * The same, under roots of their own. A root called {@code main} holds main sources and
     * anything else holds a module's tests, which is the one distinction the rule draws.
     */
    @SafeVarargs
    private static List<Unit> inRoots(Map<String, Map<String, String>>... roots) {
        JavaCompiler compiler = compiler();
        List<Unit> out = new ArrayList<>();
        for (Map<String, Map<String, String>> root : roots) {
            root.forEach((named, sources) -> {
                Map<URI, Named> given = new LinkedHashMap<>();
                sources.forEach((at, text) -> {
                    JavaFileObject source = new Written(named, at, text);
                    given.put(source.toUri(), new Named(source, at));
                });
                out.addAll(parse(compiler, given, new Root(named, "main".equals(named), List.of())));
            });
        }
        return List.copyOf(out);
    }

    /**
     * What each source says about its package, the types it declares and the names it imports.
     *
     * <p>Parsed and not compiled. Every part of the question is written in the file — which package
     * it declares, which types it declares in it, which names it imports — so nothing here needs a
     * symbol resolved, a classpath, or the rest of the repository to be built.
     *
     * <p>What each source is called comes from {@code named}, which is the caller that handed it
     * over. Read back off the file object, a name would be whatever spelling the compiler kept of a
     * path or a URI, and every caller would be undoing a different one. Looked up by the URI,
     * because the compilation unit need not carry the very object it was handed and does carry
     * where it came from.
     *
     * <p>A source the parser could not read is refused rather than taken for one that declares
     * nothing. The parser recovers what it can and hands back a unit either way, so a file it
     * choked on would contribute no declaration and no import — and this check would report a pass
     * over sources it never read.
     */
    private static List<Unit> parse(JavaCompiler compiler, Map<URI, Named> named, Root root) {
        List<String> refused = new ArrayList<>();
        JavacTask task = (JavacTask) compiler.getTask(null, null, diagnostic -> {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                refused.add(diagnostic.getSource() == null ? diagnostic.getMessage(null)
                        : named.get(diagnostic.getSource().toUri()).where()
                                + ": " + diagnostic.getMessage(null));
            }
        }, List.of("-proc:none"), null, named.values().stream().map(Named::source).toList());
        List<Unit> out = new ArrayList<>();
        try {
            for (CompilationUnitTree unit : task.parse()) {
                Named came = named.get(unit.getSourceFile().toUri());
                if (came == null) {
                    throw new IllegalStateException(
                            "a compilation unit came back from " + unit.getSourceFile().toUri()
                                    + ", which nothing here handed over");
                }
                String pkg = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
                Set<String> declares = new LinkedHashSet<>();
                for (Tree declared : unit.getTypeDecls()) {
                    // A stray `;` among the declarations is not one. Every top-level type is a
                    // class, an interface, a record or an enum, and a `ClassTree` is what the
                    // parser calls all four.
                    if (declared instanceof ClassTree it) {
                        declares.add(it.getSimpleName().toString());
                    }
                }
                out.add(new Unit(came.where(), pkg, root.named(), root.isMain(), declares,
                        importsOf(unit)));
            }
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
        if (!refused.isEmpty()) {
            throw new IllegalStateException("sources this could not parse, which contribute no"
                    + " declaration and no import and would leave this reporting a pass over"
                    + " them: " + refused);
        }
        return out;
    }

    /**
     * Whether a static import of {@code name} from {@code owner} binds a type.
     *
     * <p>Three answers. What a static import brings in is whichever member the owner has by that
     * name, and a member type shadows the package's own where a field or a method does not — so the
     * two have to be told apart, and an owner nothing can resolve is a third thing rather than
     * either of them.
     *
     * <p><b>Asked of the compiler and never of the owner's source text.</b> The members of a type
     * include the ones it inherits, so a class whose own body declares nothing can still be the
     * owner of a member type: {@code java.util.HashMap} has {@code SimpleEntry} because
     * {@code AbstractMap} declares it, and no reading of {@code HashMap}'s text says so. Answered
     * by walking declarations, this would be Java's member lookup written a second time here —
     * superclasses, interfaces, hiding, accessibility — and the second copy is the one that gets a
     * case wrong and reports a pass.
     */
    private interface MemberTypes {

        /** {@code TRUE} where the owner has a member type of that name, {@code FALSE} where what it
         *  has by that name is not a type, and null where the owner is not resolvable here. */
        Boolean bindsATypeNamed(String owner, String name);
    }

    /** The compiler's own answer, over whatever {@code elements} can reach. */
    private static MemberTypes memberTypesOf(Elements elements) {
        return (owner, name) -> {
            TypeElement it = elements.getTypeElement(owner);
            if (it == null) {
                return null;
            }
            return ElementFilter.typesIn(elements.getAllMembers(it)).stream()
                    .anyMatch(each -> each.getSimpleName().contentEquals(name));
        };
    }

    /**
     * What the classes this test runs against declare.
     *
     * <p>This module depends on every other, so the owners a repository source imports from are on
     * the classpath along with the libraries beside them. An owner that is not — a test class of
     * another module, which nothing depends on — comes back unresolvable and is said to be.
     */
    private static MemberTypes repositoryMembers() {
        return memberTypesOf(((JavacTask) compiler().getTask(null, null, diagnostic -> { },
                List.of("-proc:none"), null, List.of())).getElements());
    }

    /** The same, of types written here rather than compiled. */
    private static MemberTypes membersOf(Map<String, String> sources) {
        List<JavaFileObject> written = new ArrayList<>();
        sources.forEach((at, text) -> written.add(new Written("main", at, text)));
        JavacTask task = (JavacTask) compiler().getTask(null, null, diagnostic -> { },
                List.of("-proc:none"), null, written);
        try {
            // Entered and attributed, because a member of a type is known once the type is. What
            // the sources here fail to resolve is not this question — a fixture may import from an
            // owner nobody declares, which is one of the three answers.
            task.analyze();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
        return memberTypesOf(task.getElements());
    }

    /**
     * The single imports of one source, static and not.
     *
     * <p>An on-demand import is left out and is not an omission: it is lower in precedence than the
     * members of the package a source is in, so the bare name goes on meaning what the package
     * declares and there is nothing here to refuse.
     */
    private static List<Import> importsOf(CompilationUnitTree unit) {
        List<Import> out = new ArrayList<>();
        for (ImportTree each : unit.getImports()) {
            String spelled = each.getQualifiedIdentifier().toString();
            if (!spelled.endsWith(".*")) {
                out.add(new Import(spelled, each.isStatic()));
            }
        }
        return out;
    }

    /** The compiler this parses with, which a JRE does not have. */
    private static JavaCompiler compiler() {
        JavaCompiler found = ToolProvider.getSystemJavaCompiler();
        if (found == null) {
            throw new IllegalStateException("this reads sources with the system Java compiler and"
                    + " this runtime has none: run the tests on a JDK");
        }
        return found;
    }

    /** The {@code .java} under one root, sorted, which is how the walk of a source tree hands
     *  them over. */
    private static List<Path> sourcesUnder(Path root) {
        return REPOSITORY.filesUnderSourceTrees(".java").stream()
                .filter(each -> each.startsWith(root)).toList();
    }

    /** What this repository calls a path, which is where it sits under the root. */
    private static String named(Path at) {
        return REPOSITORY.root().relativize(at).toString();
    }

    /** Two sets of sources as one, for a case that hands the same texts to two readings. */
    private static Map<String, String> merged(Map<String, String> these,
                                              Map<String, String> those) {
        Map<String, String> out = new LinkedHashMap<>(these);
        out.putAll(those);
        return out;
    }

    /** A source written here rather than read from the repository. */
    private static final class Written extends SimpleJavaFileObject {

        private final String text;

        private Written(String root, String named, String text) {
            super(URI.create("string:///" + root + "/" + named), Kind.SOURCE);
            this.text = text;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return text;
        }
    }
}
