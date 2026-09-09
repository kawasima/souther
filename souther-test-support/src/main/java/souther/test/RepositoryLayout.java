package souther.test;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * The shape of this repository, read once.
 *
 * <p>A check that asks about the repository rather than about one class needs the same few answers:
 * where the root is, which modules the reactor owns, and where each of them keeps its sources.
 * Answered separately in each check, the answers drift, and the one that stops covering the module
 * added next says nothing about it while still reporting a pass.
 *
 * <p>The modules come from the root pom, which is what the reactor itself reads. A list written
 * here would be a copy of the reactor rather than a reading of it.
 *
 * <p>Everything below is stated positively: a source tree is {@code <module>/src}, and that is the
 * whole of what a walk descends into. Nothing is subtracted. This matters beyond tidiness, because
 * subtraction has to name every kind of file a build writes and is always one name short —
 * {@code target/} was the first and surefire's {@code .surefire-*} record, written beside the
 * module while the tests run, was the second. Both are siblings of {@code src} rather than children
 * of it, so a walk given a source tree cannot reach either, and a walk that cannot reach them
 * cannot race with the build writing them.
 *
 * <p>It follows that what such a walk costs is set by how much source there is, and not by how
 * large {@code .git} has grown or how much a build has written. That is the property to hold on to:
 * a check that reads sources should not get slower because the repository has more of something
 * else in it.
 *
 * <p>A module the root pom names whose directory or pom is missing is refused rather than skipped:
 * the reactor could not build such a repository, and a check that quietly walked one module fewer
 * would answer about the modules it read and say nothing about the rest. A module with no
 * {@code src} is not that — a module can legitimately have no sources of its own — so it
 * contributes no source tree and no complaint.
 */
public final class RepositoryLayout {

    private static final String PARENT_ARTIFACT_ID = "souther-parent";

    private final Path root;
    private final List<Path> modules;
    private final List<Path> sourceTrees;

    private RepositoryLayout(Path root, List<Path> modules, List<Path> sourceTrees) {
        this.root = root;
        this.modules = List.copyOf(modules);
        this.sourceTrees = List.copyOf(sourceTrees);
    }

    /**
     * The repository the working directory is in.
     *
     * <p>Found by walking up from the working directory to the pom that declares
     * {@code souther-parent}, so a test run by Maven from a module's directory, run from the
     * repository root, and run by an editor from somewhere else all get the same answer. Walking up
     * also reads no more of the repository than the path it is standing on.
     */
    public static RepositoryLayout ofWorkingDirectory() {
        return of(Path.of(""));
    }

    /**
     * The repository {@code start} is in, for a caller that already knows where it is standing.
     *
     * <p>{@code start} is made absolute here, which is the one place a path from outside becomes
     * one of this class's own. A relative path means whatever the working directory is, and this
     * class exists so that nothing else has to know what that is; searching upward from one would
     * reach the working directory's own parents through {@code ..} segments it never resolved, and
     * from a bare name like {@code souther-compiler} it would reach nothing at all — its parent is
     * null and the search would end at the first step. Making it absolute at the door is what stops
     * that from being each caller's problem to remember.
     */
    public static RepositoryLayout of(Path start) {
        Path root = rootAbove(start.toAbsolutePath().normalize());
        List<Path> modules = new ArrayList<>();
        List<Path> sourceTrees = new ArrayList<>();
        for (String named : modulesNamedBy(root.resolve("pom.xml"))) {
            Path module = root.resolve(named).normalize();
            if (!Files.isRegularFile(module.resolve("pom.xml"))) {
                throw new IllegalStateException("the root pom names the module " + named
                        + " but " + module + " holds no pom: the reactor could not build this"
                        + " repository, and a check that walked one module fewer would answer"
                        + " about the rest and say nothing about this one");
            }
            modules.add(module);
            Path src = module.resolve("src");
            if (Files.isDirectory(src)) {
                sourceTrees.add(src);
            }
        }
        return new RepositoryLayout(root, modules, sourceTrees);
    }

    /** The repository root, absolute and normalised. */
    public Path root() {
        return root;
    }

    /** Every module directory the root pom names, in the order it names them. */
    public List<Path> modules() {
        return modules;
    }

    /**
     * What a build calls the directory it writes into.
     *
     * <p>Where a build puts what it made is a fact about how this repository is laid out, and it
     * belongs beside the rest of them. Written out wherever it is wanted, it is a fact each writer
     * has taken on: a check that says it is one that would go on looking in the old place, and a
     * walk that says it in order to leave it out is one more copy to find when it moves.
     */
    public static String whereABuildWrites() {
        return "target";
    }

    /**
     * Whether {@code file} is something a build wrote rather than something somebody did.
     *
     * <p>Asked of a walk that means to read what the repository holds: what a build wrote is a copy
     * of something already counted, or output derived from it, and a walk that took both would
     * report the same source twice and call the second one somebody's work.
     */
    public boolean isUnderBuildOutput(Path file) {
        Path absolute = file.toAbsolutePath().normalize();
        for (Path module : modules) {
            if (absolute.startsWith(module.resolve(whereABuildWrites()))) {
                return true;
            }
        }
        return absolute.startsWith(root.resolve(whereABuildWrites()));
    }

    /**
     * The {@code src} of every module that has one.
     *
     * <p>A source tree and not a source root: {@code src/main/java} and {@code src/test/resources}
     * are source roots, and this is what holds them. It is the boundary a search for sources
     * descends into, whatever kind of source it is looking for.
     */
    public List<Path> sourceTrees() {
        return sourceTrees;
    }

    /** The {@code src/main/java} of every module that has one. */
    public List<Path> mainJavaTrees() {
        return mainTrees("java");
    }

    /**
     * The {@code src/main/<kind>} of every module that has one, sorted.
     *
     * <p>{@code java} for the sources, {@code resources} for what ships beside them.
     */
    public List<Path> mainTrees(String kind) {
        List<Path> out = new ArrayList<>();
        for (Path module : modules) {
            Path tree = treeOf(module, "main", kind);
            if (tree != null) {
                out.add(tree);
            }
        }
        out.sort(Path::compareTo);
        return List.copyOf(out);
    }

    /**
     * The {@code src/<phase>/java} of {@code module}, or null where it has none.
     *
     * <p>The roots the compiler is handed apart, which is what a check that reads sources for what
     * a name means has to keep apart too. A name written in a test source resolves against that
     * module's test root and its main root; one written in a main source resolves against the main
     * root alone, whatever the tests beside it declare. Read off a path instead, which root a
     * source is under is worked out by whoever asks and the answer is a spelling.
     */
    public Path javaTreeOf(Path module, String phase) {
        return treeOf(module, phase, "java");
    }

    /** Where a module keeps one kind of source, or null where it keeps none of that kind. */
    private static Path treeOf(Path module, String phase, String kind) {
        Path tree = module.resolve("src").resolve(phase).resolve(kind);
        return Files.isDirectory(tree) ? tree : null;
    }

    /**
     * Every {@code .java} this repository's main sources have, sorted.
     *
     * <p>What the checks that hold a rule over the compiler itself read. Held as one answer because
     * six of them had worked it out for themselves, and a rule about where something may be written
     * says nothing about a module its scan did not reach.
     */
    public List<Path> mainJavaSources() {
        List<Path> out = new ArrayList<>();
        for (Path tree : mainJavaTrees()) {
            try (Stream<Path> walk = Files.walk(tree)) {
                walk.filter(Files::isRegularFile)
                        .filter(each -> each.getFileName().toString().endsWith(".java"))
                        .forEach(out::add);
            } catch (IOException unreadable) {
                throw new UncheckedIOException(unreadable);
            }
        }
        if (out.isEmpty()) {
            throw new IllegalStateException("no Java sources under " + mainJavaTrees()
                    + ": a scan of nothing holds a rule over nothing");
        }
        out.sort(Path::compareTo);
        return List.copyOf(out);
    }

    /** Every {@code .sou} in a source tree, sorted. */
    public List<Path> southerSources() {
        return filesUnderSourceTrees(".sou");
    }

    /**
     * Every file in a source tree whose name ends with {@code suffix}, sorted.
     *
     * <p>Sorted and held rather than streamed: the walk's handles are closed before this returns,
     * so a caller cannot leak one, and a parameterized test over the result names its cases in the
     * same order on every machine.
     */
    public List<Path> filesUnderSourceTrees(String suffix) {
        List<Path> out = new ArrayList<>();
        for (Path tree : sourceTrees) {
            try (Stream<Path> walk = Files.walk(tree)) {
                walk.filter(Files::isRegularFile)
                        .filter(each -> each.getFileName().toString().endsWith(suffix))
                        .forEach(out::add);
            } catch (IOException unreadable) {
                throw new UncheckedIOException(unreadable);
            }
        }
        out.sort(Path::compareTo);
        return List.copyOf(out);
    }

    private static Path rootAbove(Path start) {
        for (Path candidate = start; candidate != null; candidate = candidate.getParent()) {
            Path pom = candidate.resolve("pom.xml");
            if (Files.isRegularFile(pom) && PARENT_ARTIFACT_ID.equals(artifactIdOf(pom))) {
                return candidate;
            }
        }
        throw new IllegalStateException("no pom declaring " + PARENT_ARTIFACT_ID + " above " + start
                + ": this reads the repository it is running in, and there is none here");
    }

    /**
     * The {@code artifactId} the pom declares for itself.
     *
     * <p>Its own and not its parent's, which is why this reads the element rather than searching
     * the text: every module in this repository names {@code souther-parent} in its {@code parent}
     * block, so a pom found by its spelling would be whichever module the test happened to start
     * in.
     */
    private static String artifactIdOf(Path pom) {
        return childText(parse(pom).getDocumentElement(), "artifactId");
    }

    /**
     * The modules {@code /project/modules/module} names.
     *
     * <p>Read as elements rather than matched as text, so that how the pom happens to be written —
     * an attribute, a comment, whitespace inside the tag — is not what the answer depends on.
     *
     * <p>Deliberately blind to a {@code <modules>} inside a profile, and this refuses when it finds
     * one. A profile that adds a module would make the reactor's module list depend on which
     * profiles are active, and a reading that silently returned the unconditional ones would go on
     * reporting passes about a module nobody was walking.
     */
    private static List<String> modulesNamedBy(Path pom) {
        Document document = parse(pom);
        Element project = document.getDocumentElement();
        for (Element profiles : childElements(project, "profiles")) {
            for (Element profile : childElements(profiles, "profile")) {
                if (!childElements(profile, "modules").isEmpty()) {
                    throw new IllegalStateException(pom + " declares modules inside a profile: which"
                            + " modules the reactor has would then depend on which profiles are"
                            + " active, and this reads the unconditional ones only");
                }
            }
        }
        List<String> named = new ArrayList<>();
        for (Element modules : childElements(project, "modules")) {
            for (Element module : childElements(modules, "module")) {
                named.add(module.getTextContent().trim());
            }
        }
        if (named.size() < 2) {
            throw new IllegalStateException(pom + " names " + named
                    + ": this is the aggregator of a repository with several modules");
        }
        return named;
    }

    private static Document parse(Path pom) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            // A pom is read for what it says, so nothing it names is fetched or expanded.
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(pom.toFile());
        } catch (ParserConfigurationException | SAXException malformed) {
            throw new IllegalStateException(pom + " does not parse", malformed);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static String childText(Element parent, String name) {
        List<Element> found = childElements(parent, name);
        return found.isEmpty() ? "" : found.getFirst().getTextContent().trim();
    }

    /** The direct children of {@code parent} called {@code name}, and not its descendants. */
    private static List<Element> childElements(Element parent, String name) {
        List<Element> out = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element element && element.getTagName().equals(name)) {
                out.add(element);
            }
        }
        return out;
    }
}
