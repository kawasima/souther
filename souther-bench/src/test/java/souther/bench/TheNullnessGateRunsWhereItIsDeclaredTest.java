package souther.bench;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.lang.classfile.ClassModel;
import java.lang.classfile.attribute.RuntimeInvisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The modules an ordinary build runs NullAway over are the modules that declare {@code @NullMarked}.
 *
 * <p>#711 put the null analysis in the build so that a rule only some checkouts enforce would not be
 * the rule, and asked that adopting a package be one line in its {@code package-info.java}. Two
 * things have to agree for that to hold: which packages are annotated, and which modules configure
 * the gate. Both are written by hand, in different files, and nothing else compares them. They agree
 * today because there is one of each. This is what refuses the day they stop.
 *
 * <p>Equality rather than containment, and in both directions. A module that declares the annotation
 * and does not run the gate has a rule nobody checks; a module that runs the gate and declares
 * nothing pays Error Prone's startup on every compile to check nothing at all. Either way the two
 * statements have come apart, and the fix is named by which side is short.
 *
 * <p>The annotated side is read off class files rather than sources, because souther-compiler's
 * javadoc discusses {@code @NullMarked} and its code generator writes the annotation into the classes
 * it emits — both put the word in a source file that declares nothing. An annotation on a class is
 * only an annotation in the class file.
 *
 * <p>The configured side is read off {@code /project/build}, never a profile: the question is what an
 * ordinary {@code mvn test} runs, and the {@code lint} profile deliberately runs the gate over
 * everything. A gate declared in the root's build would cover every module, so that is read too.
 *
 * <p>The root's properties are substituted into an argument before it is read. The gate's
 * options are named in one property so that the module and the profile cannot ask for
 * different ones, and a reader that stopped at the placeholder would see neither of them
 * mention NullAway.
 *
 * <p>It lives in the last module the reactor builds, because it reads every module's classes and they
 * have to be there.
 */
class TheNullnessGateRunsWhereItIsDeclaredTest {

    /** JSpecify's package-level annotation, as a class file spells it. */
    private static final String NULL_MARKED = "Lorg/jspecify/annotations/NullMarked;";

    @Test
    void theGateIsConfiguredForEveryModuleThatDeclaresItAndNoOther() {
        Set<String> declared = new LinkedHashSet<>();
        Set<String> configured = new LinkedHashSet<>();
        boolean rootGates = gateIsInTheBuildOf(Reactor.root().resolve("pom.xml"));
        for (Path module : Reactor.modules()) {
            if (declaresNullMarked(module)) {
                declared.add(Reactor.name(module));
            }
            if (rootGates || gateIsInTheBuildOf(module.resolve("pom.xml"))) {
                configured.add(Reactor.name(module));
            }
        }

        // Neither set may be empty. An empty declared set would make this pass by having nothing to
        // say, and an empty configured set is the gate being gone.
        assertFalse(declared.isEmpty(),
                "no module declares @NullMarked, so either the annotation was dropped or this is"
                        + " reading the wrong thing");
        assertFalse(configured.isEmpty(),
                "no module runs the nullness gate in an ordinary build, which is what #711 was for");

        assertEquals(declared, configured,
                "the gate and the annotation have come apart.\n"
                        + "  declares @NullMarked: " + declared + "\n"
                        + "  runs the gate:        " + configured + "\n"
                        + "A module in the first and not the second states a rule its own build does"
                        + " not check: give it the maven-compiler-plugin block souther-runtime"
                        + " carries. A module in the second and not the first runs Error Prone on"
                        + " every compile to check nothing: take that block out.");
    }

    /** Whether any class the module built carries the annotation. */
    private static boolean declaresNullMarked(Path module) {
        if (!Reactor.hasMainSources(module)) {
            return false;   // nothing of its own to annotate
        }
        return Reactor.mainOutputOf(module).all().stream()
                .anyMatch(TheNullnessGateRunsWhereItIsDeclaredTest::carriesNullMarked);
    }

    private static boolean carriesNullMarked(ClassModel model) {
        // Both retentions, because which one JSpecify uses is JSpecify's to change.
        Stream<java.lang.classfile.Annotation> annotations = Stream.concat(
                model.findAttribute(java.lang.classfile.Attributes.runtimeVisibleAnnotations())
                        .map(RuntimeVisibleAnnotationsAttribute::annotations).orElse(List.of()).stream(),
                model.findAttribute(java.lang.classfile.Attributes.runtimeInvisibleAnnotations())
                        .map(RuntimeInvisibleAnnotationsAttribute::annotations).orElse(List.of()).stream());
        return annotations.anyMatch(a -> a.className().stringValue().equals(NULL_MARKED));
    }

    /**
     * Whether this pom's own build — not one of its profiles — hands javac the Error Prone plugin
     * with NullAway turned on.
     */
    private static boolean gateIsInTheBuildOf(Path pom) {
        if (!Files.isRegularFile(pom)) {
            return false;
        }
        Element project = parse(pom);
        Map<String, String> properties = rootProperties();
        for (Element build : childrenNamed(project, "build")) {
            for (Element plugins : childrenNamed(build, "plugins")) {
                for (Element plugin : childrenNamed(plugins, "plugin")) {
                    if (!textOfFirst(plugin, "artifactId").equals("maven-compiler-plugin")) {
                        continue;
                    }
                    for (Element configuration : childrenNamed(plugin, "configuration")) {
                        for (Element args : childrenNamed(configuration, "compilerArgs")) {
                            for (Element arg : childrenNamed(args, "arg")) {
                                String text = substitute(arg.getTextContent(), properties);
                                if (text.contains("-Xplugin:ErrorProne") && text.contains("NullAway")) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    /** The root pom's {@code <properties>}, which is where the gate's options are named. */
    private static Map<String, String> rootProperties() {
        Map<String, String> properties = new LinkedHashMap<>();
        for (Element block : childrenNamed(parse(Reactor.root().resolve("pom.xml")), "properties")) {
            NodeList children = block.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if (children.item(i) instanceof Element element) {
                    properties.put(element.getTagName(), element.getTextContent().trim());
                }
            }
        }
        assertFalse(properties.isEmpty(), "the root pom names no properties, which cannot be right");
        return properties;
    }

    /**
     * To a fixed point, because a property here names another: the gate is written as the plugin
     * plus {@code ${nullaway.args}}. One pass over the properties in document order would resolve
     * that only when the inner one happens to be declared first, which is a reading that depends on
     * the order of a file nobody is holding to an order.
     */
    private static String substitute(String text, Map<String, String> properties) {
        String substituted = text;
        for (int pass = 0; pass < properties.size() + 1; pass++) {
            String next = substituted;
            for (Map.Entry<String, String> property : properties.entrySet()) {
                next = next.replace("${" + property.getKey() + "}", property.getValue());
            }
            if (next.equals(substituted)) {
                return substituted;
            }
            substituted = next;
        }
        throw new IllegalStateException("a property in the root pom refers to itself: " + text);
    }

    private static Element parse(Path pom) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            return factory.newDocumentBuilder().parse(pom.toFile()).getDocumentElement();
        } catch (Exception e) {
            throw new IllegalStateException("cannot read " + pom, e);
        }
    }

    private static List<Element> childrenNamed(Element parent, String name) {
        List<Element> found = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element element && element.getTagName().equals(name)) {
                found.add(element);
            }
        }
        return found;
    }

    private static String textOfFirst(Element parent, String name) {
        List<Element> found = childrenNamed(parent, name);
        return found.isEmpty() ? "" : found.get(0).getTextContent().trim();
    }

    /**
     * Everything this covers, read off the reactor rather than listed here, for the reason the ABI
     * check gives: a list of its own would say what was true when it was written.
     */

}
