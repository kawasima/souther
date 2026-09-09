package souther.architecture;

import souther.compiler.ast.ConstructionOrigin;
import souther.test.RepositoryLayout;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.lang.classfile.ClassModel;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A construction says where it came from, and no pass says it for one.
 *
 * <p>The arms of a construction origin are declared where only {@code souther.compiler.ast} can name
 * them, so a pass elsewhere cannot make one or ask one what it means. What javac cannot refuse is a
 * pass taking an origin off a node and handing it to another node, which is the same defect a step
 * along: the answer would come from wherever the writer happened to have one rather than from what
 * the node is. So the forms are built through the constructors that take no origin, and a member
 * that takes one is this package's to call.
 *
 * <p>Read off the compiled classes and not the source, because it is a rule about who calls what: a
 * reference is in the constant pool of the class that makes it whatever the call looks like.
 *
 * <p>What is forbidden is a crossing and not a call on a particular form: from outside the package,
 * naming anything inside it that takes an origin. Written the other way — the members of the forms
 * that hold one — it would ask which forms those are, and then a method beside them taking an origin
 * and handing it on would be a way in that the question does not reach. Nothing here knows which
 * forms hold an origin, which is the compiler's own to say and is said in its tests.
 *
 * <p>What counts as an origin is read off the type: the interface and the arms it permits, so an
 * argument written as one of the arms is one of these too.
 *
 * <p>This lives in a module that depends on every other, so every module's classes are built when it
 * runs and the population is the whole repository rather than whatever happened to be built. That
 * every module is depended on is asserted below against {@link RepositoryLayout}, which is what says
 * what the repository is made of — a module added without a dependency from here would otherwise be
 * passed over in silence, and the build order does not reliably show it.
 *
 * <p>What is here is the question no module can answer alone: who, across every module of the
 * reactor, calls what.
 */
class WhereAConstructionCameFromIsNotAPassesToWriteTest {

    /** Where an origin may be made, asked about and handed over. */
    private static final String THEIRS = "souther/compiler/ast/";

    private static final CompiledOutputs EVERYTHING = CompiledOutputs.ofEverythingCompiledHere();

    private static final RepositoryLayout REPOSITORY = RepositoryLayout.ofWorkingDirectory();

    @Test
    void nothingOutsideTheTreeHandsAnOriginToIt() {
        List<String> handing = new ArrayList<>();
        for (ClassModel each : EVERYTHING.all()) {
            if (each.thisClass().asInternalName().startsWith(THEIRS) || !handsAnOriginIn(each)) {
                continue;
            }
            handing.add(each.thisClass().asInternalName());
        }

        assertEquals(List.of(), handing.stream().sorted().toList(),
                "a pass writing one of these forms says what it is building and not where the"
                        + " construction came from: what takes no origin is what to call, and a"
                        + " rebuild carries the origin it was handed");
    }

    /**
     * Every module of the repository is depended on from here, which is what has them built when
     * this runs and so what makes the answer above about all of them.
     *
     * <p>Asked of what this module declares as a dependency, because that is what Maven orders the
     * reactor on. Not of the build order, which shows an omission only sometimes: what sends this
     * module to the end is every edge it has, so a missing edge is covered whenever another module
     * it does depend on is written after the one it forgot — measured, dropping
     * {@code souther-cli} left the order unchanged. And not of the classpath these tests run on,
     * which says where each dependency was resolved from rather than which are declared: run to
     * {@code test} it holds each module's {@code target/classes}, and run to {@code verify} it
     * holds their jars, so a check reading it answers about how the build was invoked.
     */
    @Test
    void andEveryModuleTheRepositoryHoldsIsDependedOnFromHere() {
        Set<String> declared = new TreeSet<>(dependenciesOfThisModule());
        Set<String> repository = new TreeSet<>(everyModule());
        repository.remove(here().getFileName().toString());

        assertEquals(repository, declared,
                "a module this does not depend on is one Maven need not build before these tests"
                        + " run, and one the walk above then reads whatever an earlier build left"
                        + " of");
    }

    /** The control: the walk reads the classes it is about, and sees the crossing it forbids where
     *  it is not a crossing at all — inside the package, where a form hands one to itself. */
    @Test
    void andTheCheckSeesWhatItIsLookingForWhereThatIsAllowed() {
        assertFalse(anOrigin().isEmpty(), "what an origin is, is read off the type");
        assertTrue(EVERYTHING.all().stream()
                        .filter(each -> each.thisClass().asInternalName().startsWith(THEIRS))
                        .anyMatch(WhereAConstructionCameFromIsNotAPassesToWriteTest
                                ::handsAnOriginIn),
                "a form hands an origin to its own constructor, which is what this reads");
    }

    /** The descriptors of an origin: the type and the arms it permits, so an argument written as an
     *  arm is one too. */
    private static Set<String> anOrigin() {
        Set<String> descriptors = new LinkedHashSet<>();
        descriptors.add(descriptorOf(ConstructionOrigin.class));
        for (Class<?> arm : ConstructionOrigin.class.getPermittedSubclasses()) {
            descriptors.add(descriptorOf(arm));
        }
        return descriptors;
    }

    private static String descriptorOf(Class<?> type) {
        return "L" + type.getName().replace('.', '/') + ";";
    }

    /** Whether {@code compiled} names anything of the tree's package that takes an origin. */
    private static boolean handsAnOriginIn(ClassModel compiled) {
        for (PoolEntry entry : compiled.constantPool()) {
            if (entry instanceof MemberRefEntry member
                    && member.owner().name().stringValue().startsWith(THEIRS)
                    && takesAnOrigin(member.type().stringValue())) {
                return true;
            }
        }
        return false;
    }

    /** Whether a descriptor takes an origin, which is what may not be handed over from outside.
     *  What it answers with is another question: a form holds one and says so. */
    private static boolean takesAnOrigin(String descriptor) {
        int arguments = descriptor.lastIndexOf(')');
        if (arguments < 0) {
            return anOrigin().contains(descriptor);   // a field, whose descriptor is its type
        }
        String parameters = descriptor.substring(0, arguments);
        return anOrigin().stream().anyMatch(parameters::contains);
    }





    /** What the repository's modules are called, which is {@link RepositoryLayout}'s answer and not
     *  a second reading of the root pom. */
    private static Set<String> everyModule() {
        return REPOSITORY.modules().stream().map(each -> each.getFileName().toString())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    /** This module, named because it is this one: what the repository is made of is
     *  {@link RepositoryLayout}'s answer, and which of those this is, is this module's own. */
    private static Path here() {
        return REPOSITORY.root().resolve("souther-architecture-test");
    }

    /**
     * The modules of this repository that this module depends on, test-scoped as its pom says they
     * are.
     *
     * <p>Read at {@code project > dependencies > dependency} and nowhere else, because that is what
     * Maven builds the reactor's order from. A {@code <dependency>} under
     * {@code <dependencyManagement>} says what version one would have if it were depended on, and
     * orders nothing; found by a search for the element wherever it appears, it would answer this
     * question with a declaration that does not have a module built before these tests run.
     */
    private static Set<String> dependenciesOfThisModule() {
        Set<String> modules = everyModule();
        Set<String> named = new LinkedHashSet<>();
        for (Element dependency : childrenNamed(
                onlyChildNamed(read(here().resolve("pom.xml")).getDocumentElement(), "dependencies"),
                "dependency")) {
            String artifact = textOf(dependency, "artifactId");
            if (modules.contains(artifact) && "test".equals(textOf(dependency, "scope"))) {
                named.add(artifact);
            }
        }
        return named;
    }

    /** The one element of {@code parent} with this name, of its own children. */
    private static Element onlyChildNamed(Element parent, String name) {
        List<Element> found = childrenNamed(parent, name);
        assertEquals(1, found.size(), parent.getTagName() + " holds one <" + name + ">");
        return found.get(0);
    }

    /** {@code parent}'s own children of this name — not its descendants. */
    private static List<Element> childrenNamed(Element parent, String name) {
        List<Element> found = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child && child.getTagName().equals(name)) {
                found.add(child);
            }
        }
        return found;
    }

    /** What {@code element} says under this name, or the empty string where it says nothing. */
    private static String textOf(Element element, String name) {
        List<Element> found = childrenNamed(element, name);
        return found.isEmpty() ? "" : found.get(0).getTextContent().trim();
    }

    private static Document read(Path pom) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return factory.newDocumentBuilder().parse(pom.toFile());
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new IllegalStateException("a pom that cannot be read: " + pom, e);
        }
    }
}
