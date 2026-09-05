package souther.architecture;

import souther.test.RepositoryLayout;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;

import org.junit.jupiter.api.Test;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A name a package declares means that name inside the package.
 *
 * <p>Two packages may call two things by one name. A word that fits one bounded context fits
 * another, and asking every such pair to give one of them up would be a rule about the words rather
 * than about what they say — {@code Ordered} in a partition and {@code Ordered} in a query are two
 * answers to two questions, and nothing is clearer for one of them being renamed.
 *
 * <p>What is refused is one file rebinding the name. A single-type import wins over the package a
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
 * <p><b>Only a non-static single-type import.</b> That is the whole of what this reads, so it is the
 * whole of what the rule claims. Java introduces names other ways — a static import, a nested type
 * reached through its owner — and each is a question of its own; a sentence here about what a bare
 * name always means would be wider than the reading under it.
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
        List<Unit> units = parsed(REPOSITORY.filesUnderSourceTrees(".java"));
        assertTrue(units.size() > 1000,
                "this reads the repository's sources, and it read " + units.size());

        assertEquals(List.of(), rebindings(units),
                "a file whose package declares this name and which imports another package's:"
                        + " inside it the bare name means the other one, and nothing says so."
                        + " Drop the import and write the foreign type out where it is used");
    }

    /**
     * And what the rule holds and does not, on sources written to be either.
     *
     * <p>Here rather than by naming the pairs the repository happens to hold. That two packages both
     * declare {@code Ordered} is true today and is not something a check should hold them to, and a
     * fixture that named it would be this test asking for the duplication to stay.
     */
    @Test
    void aNameTheOwnPackageDoesNotDeclareIsImportedFreely() {
        String bringsIn = "package p; import q.A; class C { A a; }";
        String leavesAlone = "package p; class C { q.A a; }";
        String another = "package p; import q.B; class C { B b; }";

        assertEquals(List.of("p/C.java rebinds `A`, which p declares, to q.A"),
                rebindings(parsedFrom(Map.of("p/A.java", "package p; class A {}",
                        "q/A.java", "package q; class A {}",
                        "q/B.java", "package q; class B {}",
                        "p/C.java", bringsIn))),
                "the name is the package's own and the import takes it");

        assertEquals(List.of(),
                rebindings(parsedFrom(Map.of("p/A.java", "package p; class A {}",
                        "q/A.java", "package q; class A {}",
                        "p/C.java", leavesAlone))),
                "the foreign type written out takes no name");

        assertEquals(List.of(),
                rebindings(parsedFrom(Map.of("p/A.java", "package p; class A {}",
                        "q/A.java", "package q; class A {}",
                        "q/B.java", "package q; class B {}",
                        "p/C.java", another))),
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
                rebindings(parsedFrom(throughAnOwner)),
                "the owner is another type, so the name is taken");

        Map<String, String> theSameType = new LinkedHashMap<>(declared);
        theSameType.put("p/C.java", "package p; import p.A; class C { A a; }");
        assertEquals(List.of(), rebindings(parsedFrom(theSameType)),
                "the import names what the package declares, and the name already meant it");
    }

    /** One compilation unit, as much of it as this question is about. */
    private record Unit(String where, String pkg, Set<String> declares, List<String> imports) {}

    /**
     * The rebindings among {@code units}, each said as the file, the name and what it was taken to.
     *
     * <p>Sorted, so that what a failure lists is the same on every machine and reads as a list of
     * places to go rather than as whatever order a walk happened to have.
     */
    private static List<String> rebindings(List<Unit> units) {
        Map<String, Set<String>> declaredBy = new LinkedHashMap<>();
        for (Unit unit : units) {
            declaredBy.computeIfAbsent(unit.pkg(), _ -> new LinkedHashSet<>())
                    .addAll(unit.declares());
        }
        Set<String> found = new TreeSet<>();
        for (Unit unit : units) {
            Set<String> own = declaredBy.getOrDefault(unit.pkg(), Set.of());
            for (String imported : unit.imports()) {
                int dot = imported.lastIndexOf('.');
                String name = dot < 0 ? imported : imported.substring(dot + 1);
                String from = dot < 0 ? "" : imported.substring(0, dot);
                // A same-package import binds the name it already had, so nothing means anything
                // different for it being written. Whether it should be written at all is another
                // rule's question.
                if (!from.equals(unit.pkg()) && own.contains(name)) {
                    found.add(unit.where() + " rebinds `" + name + "`, which " + unit.pkg()
                            + " declares, to " + imported);
                }
            }
        }
        return List.copyOf(found);
    }

    /** The sources at {@code paths}, named by their path under the repository root. */
    private static List<Unit> parsed(List<Path> paths) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager files =
                     compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            return read(compiler, files.getJavaFileObjectsFromPaths(paths),
                    at -> REPOSITORY.root().relativize(Path.of(at)).toString());
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    /** The same, of sources written here, named by the key they were written under. */
    private static List<Unit> parsedFrom(Map<String, String> sources) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        List<JavaFileObject> written = new ArrayList<>();
        sources.forEach((named, text) -> written.add(new Written(named, text)));
        // What a written source is called is the path of the URI it was given, which is that key
        // under the root every URI needs.
        return read(compiler, written, at -> at.startsWith("/") ? at.substring(1) : at);
    }

    /**
     * What each source says about its package, its top-level types and its imports.
     *
     * <p>Parsed and not compiled. Every part of the question is written in the file — which package
     * it declares, which names it declares in that package, which types it imports by name — so
     * nothing here needs a symbol resolved, a classpath, or the rest of the repository to be built.
     */
    private static List<Unit> read(JavaCompiler compiler, Iterable<? extends JavaFileObject> sources,
                                   java.util.function.UnaryOperator<String> naming) {
        JavacTask task = (JavacTask) compiler.getTask(null, null, diagnostic -> { },
                List.of("-proc:none"), null, sources);
        List<Unit> out = new ArrayList<>();
        try {
            for (CompilationUnitTree unit : task.parse()) {
                Set<String> declares = new LinkedHashSet<>();
                for (Tree declared : unit.getTypeDecls()) {
                    // A stray `;` among the declarations is not one. Every top-level type is a
                    // class, an interface, a record or an enum, and a `ClassTree` is what the
                    // parser calls all four.
                    if (declared instanceof ClassTree it) {
                        declares.add(it.getSimpleName().toString());
                    }
                }
                List<String> imports = new ArrayList<>();
                for (ImportTree each : unit.getImports()) {
                    String named = each.getQualifiedIdentifier().toString();
                    // A static import brings in a member and an on-demand import brings in no name
                    // at all until something is written; neither is what this is about, and the
                    // class doc says so rather than this quietly covering them.
                    if (!each.isStatic() && !named.endsWith(".*")) {
                        imports.add(named);
                    }
                }
                out.add(new Unit(naming.apply(unit.getSourceFile().getName()),
                        unit.getPackageName() == null ? "" : unit.getPackageName().toString(),
                        declares, imports));
            }
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
        return List.copyOf(out);
    }

    /** A source written here rather than read from the repository. */
    private static final class Written extends SimpleJavaFileObject {

        private final String text;

        private Written(String named, String text) {
            super(URI.create("string:///" + named), Kind.SOURCE);
            this.text = text;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return text;
        }
    }
}
