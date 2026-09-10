package souther.compiler.inputs;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.classfile.AttributedElement;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.RecordAttribute;
import java.lang.classfile.attribute.RecordComponentInfo;
import java.lang.classfile.attribute.RuntimeInvisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeInvisibleParameterAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeInvisibleTypeAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleParameterAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleTypeAnnotationsAttribute;
import java.lang.classfile.attribute.SignatureAttribute;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.MethodTypeEntry;
import java.lang.classfile.constantpool.NameAndTypeEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nothing that walks a tree names the reading of the input.
 *
 * <p>The two are told apart by how long each of them lives. {@link InputReads} is a function of the
 * program point — the bindings gone under, the arm gone into — and changes at every step of a walk;
 * {@link InputDomain} is one value for a whole analysis. Held together, the reading is copied into
 * every step and each reader asks whichever copy it is holding, which is how a reader comes to take
 * an answer off a value that is only carrying it.
 *
 * <p><b>Two prohibitions, because there are two ways to be walk state.</b> A value is walk state by
 * being the environment, and a value is walk state by moving one along as it goes. What is neither
 * — a value that keeps an environment to say where something was read, and does not move it — is
 * not forbidden anything here: holding a reading beside the place a fact came from is an ordinary
 * thing for an answer to do, and this is about what a walk carries rather than about what an answer
 * records.
 *
 * <p>Which is why the second subject is worked out from moving and not from holding. Keeping an
 * environment is what an answer and a cursor have in common, so a check on that would forbid a
 * shape it was never about.
 *
 * <p><b>What counts as naming a type is a role and not an entry.</b> A class file spells a type in a
 * class reference, a field or method descriptor, a generic signature, a method type, the type of a
 * member it links against, an annotation's own type, and a class an annotation was given as an
 * element. Each of those is read here as what it is. Nothing is subtracted afterwards: text a class
 * carries as a string constant is never one of these roles, so it never arrives — and a descriptor
 * that happens to read the same as some string constant is still a descriptor, which subtracting by
 * the entry they share would have thrown away.
 *
 * <p>So this is a claim about what Java links against, and not about what is reachable: a class
 * named as text and found by reflection would not be seen here.
 */
class NothingThatWalksATreeNamesTheReadingOfTheInputTest {

    private static final String ENVIRONMENT = "souther/compiler/inputs/InputReads";
    private static final String READING = "souther/compiler/inputs/InputDomain";

    /** What a name in a tree stands for, which is where the two were held together. */
    @Test
    void theEnvironmentNamesTheReadingNowhere() throws IOException {
        assertEquals(Set.of(), mentionsIn(InputReads.class));
    }

    /** And the value an environment travels with, which would be the reading one step further
     *  out. */
    @Test
    void andNeitherDoesTheValueAnEnvironmentTravelsWith() throws IOException {
        assertEquals(Set.of(), mentionsIn(Denotation.class));
    }

    /**
     * And nothing that moves an environment along as it walks owns a reading.
     *
     * <p>The other way of being walk state. Such a value is at a program point the way the
     * environment is, so a reading kept in it is copied into every step and asked of whichever copy
     * a reader is holding — which is the same defect as the one above, one container out.
     */
    @Test
    void andNothingThatMovesOneAlongOwnsAReading() throws IOException, URISyntaxException {
        Map<String, Set<String>> named = new LinkedHashMap<>();
        for (ClassModel each : compiled()) {
            if (!movesAnEnvironmentAlong(each)) {
                continue;
            }
            Set<String> mentions = new LinkedHashSet<>();
            for (String spelling : namesOfTypesIn(each)) {
                if (spelling.contains(READING)) {
                    mentions.add(spelling);
                }
            }
            if (!mentions.isEmpty()) {
                named.put(each.thisClass().asInternalName(), mentions);
            }
        }
        assertEquals(Map.of(), named);
    }

    /**
     * And moving one is what that subject is, which is what keeps it off the values that only keep
     * an environment.
     *
     * <p>Both sides. A walk that found nothing to check would report every class clean, and one
     * that took keeping for moving would be forbidding an answer to hold what it is an answer
     * about.
     */
    @Test
    void andMovingAnEnvironmentIsWhatTellsWalkStateFromAnAnswer()
            throws IOException, URISyntaxException {
        List<String> moves = new ArrayList<>();
        List<String> keeps = new ArrayList<>();
        for (ClassModel each : compiled()) {
            if (!keepsAnEnvironment(each)) {
                continue;
            }
            (movesAnEnvironmentAlong(each) ? moves : keeps)
                    .add(each.thisClass().asInternalName());
        }
        // Every naming of the ways through a body is here, and nothing else is. A naming is scoped
        // like the environment by contract — it answers under a binding and inside an arm — so
        // moving one along is what a naming does rather than a shape one of them happens to have.
        assertEquals(List.of(
                        "souther/compiler/partition/DecisionNaming",
                        "souther/compiler/reading/CoverageNaming",
                        "souther/compiler/reading/NumberWays"),
                moves.stream().sorted().toList());
        assertTrue(keeps.containsAll(List.of(
                        "souther/compiler/inputs/Denotation",
                        "souther/compiler/inputs/ComparedNumbers$Read",
                        "souther/compiler/partition/AffineReading$OfAComparison$Stopped",
                        "souther/compiler/partition/ComparisonReadings$Reading",
                        "souther/compiler/partition/Condition$Compares")),
                () -> "what records where something was read is not walk state, and found " + keeps);
    }

    /**
     * And each way a class file spells a type is one the walk reads.
     *
     * <p>The checks above pass on the empty set, which is also what a walk that read nothing
     * produces. So each role is put through it on a class that names the reading that way.
     *
     * <p>The last of them is where subtracting by entry went wrong: a field of the reading's type
     * beside a string constant spelling that same descriptor, which javac holds as one
     * {@code Utf8}. Read by role the descriptor is still a descriptor.
     */
    @Test
    void andEachWayOfSpellingATypeIsRead() throws IOException {
        assertFinds(InAMethodDescriptor.class, "a method's descriptor");
        assertFinds(InAFieldDescriptor.class, "a field's descriptor");
        assertFinds(InAClassReference.class, "a class reference");
        assertFinds(InAMemberType.class, "the type of a member linked against");
        assertFinds(InAClassSignature.class, "a class's generic signature");
        assertFinds(InAMethodSignature.class, "a method's generic signature");
        assertFinds(InARecordComponent.class, "a record component");
        assertFinds(InAnAnnotation.class, "an annotation's element");
        assertFinds(InAParameterAnnotation.class, "an annotation on a parameter");
        assertFinds(InACodeTypeAnnotation.class, "an annotation on a type inside a body");
        assertFinds(InAMethodType.class, "a method type a bootstrap is handed");
        assertFinds(SharedWithAStringConstant.class,
                "a descriptor sharing its spelling with a string constant");
    }

    /** And a class that names the reading only as text is not read as naming it. */
    @Test
    void andTextIsNotASpelling() throws IOException {
        assertEquals(Set.of(), mentionsIn(OnlyAsAStringConstant.class));
    }

    /** And each witness is the shape it is named for, which is also what keeps its members read. */
    @Test
    void andEachWitnessIsTheShapeItIsNamedFor() {
        assertNull(InAMethodDescriptor.none());
        assertTrue(InAFieldDescriptor.isNull());
        assertFalse(InAClassReference.isOne("not one"));
        assertEquals(List.of(), InAMemberType.taken());
        assertTrue(new InAClassSignature().isEmpty());
        assertNull(InAMethodSignature.none());
        assertNull(new InARecordComponent(null).held());
        assertEquals(InputDomain.class, InAnAnnotation.class.getAnnotation(Names.class).value());
        assertEquals("held", InAParameterAnnotation.none("held"));
        assertEquals("held", InACodeTypeAnnotation.none("held"));
        assertNull(InAMethodType.none().get());
        assertTrue(SharedWithAStringConstant.isNull());
        assertEquals("Lsouther/compiler/inputs/InputDomain;", SharedWithAStringConstant.SPELLED);
        assertEquals("Lsouther/compiler/inputs/InputDomain;", OnlyAsAStringConstant.SPELLED);
    }

    private static void assertFinds(Class<?> of, String how) throws IOException {
        assertFalse(mentionsIn(of).isEmpty(), () -> "the reading named in " + how + " is read");
    }

    private static Set<String> mentionsIn(Class<?> of) throws IOException {
        Set<String> out = new LinkedHashSet<>();
        for (String spelling : namesOfTypesIn(parsed(of))) {
            if (spelling.contains(READING)) {
                out.add(spelling);
            }
        }
        return out;
    }

    /** Whether {@code of} keeps an environment, which an answer and a cursor both do. */
    private static boolean keepsAnEnvironment(ClassModel of) {
        for (FieldModel field : of.fields()) {
            if (field.fieldType().stringValue().equals("L" + ENVIRONMENT + ";")
                    || signatureOf(field).contains(ENVIRONMENT)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether {@code of} moves an environment along as it goes, which is what makes it walk state.
     *
     * <p>It keeps one and it asks one for the environment inside a binding or inside an arm, which
     * are the two things that change what a name stands for. A value that keeps one and asks it
     * neither is standing where it was made and is an answer about that place.
     */
    private static boolean movesAnEnvironmentAlong(ClassModel of) {
        if (!keepsAnEnvironment(of)) {
            return false;
        }
        for (PoolEntry entry : of.constantPool()) {
            if (entry instanceof MemberRefEntry member
                    && member.owner().asInternalName().equals(ENVIRONMENT)
                    && (member.name().stringValue().equals("and")
                            || member.name().stringValue().equals("insideArm"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Every spelling of a type in {@code of}, each read as the role it is written in.
     *
     * <p>The roles, and there is nothing else a class file names a type with. A class reference
     * carries supertypes, what is caught, what is tested against and what is built. A descriptor
     * carries what a field holds and what a method takes and answers, and the type half of a member
     * this links against carries the same for the other side. A generic signature carries what an
     * erasure left out, on a class, a method, a field or a record component. A method type carries
     * what a bootstrap is handed. And an annotation carries its own type and any class it was given
     * as an element.
     *
     * <p>Where the annotations are is gone after rather than listed. A class file hangs them off
     * everything that has attributes of its own — the class, its fields, its methods, its record
     * components, and a method's body — and off a method's parameters, which have nothing of their
     * own to hang them on and so are carried as an attribute of the method. Listed by hand, the
     * declarations were read and the body and the parameters were not, and a class named on either
     * was named nowhere.
     */
    private static Set<String> namesOfTypesIn(ClassModel of) {
        Set<String> out = new LinkedHashSet<>();
        for (PoolEntry entry : of.constantPool()) {
            switch (entry) {
                case ClassEntry named -> out.add(named.asInternalName());
                case NameAndTypeEntry member -> out.add(member.type().stringValue());
                case MethodTypeEntry taken -> out.add(taken.descriptor().stringValue());
                default -> { }
            }
        }
        out.add(signatureOf(of));
        annotationsOf(of, out);
        for (FieldModel field : of.fields()) {
            out.add(field.fieldType().stringValue());
            out.add(signatureOf(field));
            annotationsOf(field, out);
        }
        for (MethodModel method : of.methods()) {
            out.add(method.methodType().stringValue());
            out.add(signatureOf(method));
            annotationsOf(method, out);
            // What a method carries besides itself. Its parameters hold their own annotations,
            // which are an attribute of the method rather than something with attributes of its
            // own; and its body is an element with attributes, where an annotation written on a
            // type inside it goes.
            parametersOf(method, out);
            method.code().ifPresent(body -> annotationsOf(body, out));
        }
        for (RecordComponentInfo component : of.findAttribute(Attributes.record())
                .map(RecordAttribute::components).orElse(List.of())) {
            out.add(component.descriptor().stringValue());
            out.add(component.findAttribute(Attributes.signature())
                    .map(SignatureAttribute::signature).map(each -> each.stringValue()).orElse(""));
            annotationsOf(component, out);
        }
        return out;
    }

    private static String signatureOf(AttributedElement of) {
        return of.findAttribute(Attributes.signature())
                .map(each -> each.signature().stringValue()).orElse("");
    }

    private static void annotationsOf(AttributedElement of, Set<String> out) {
        List<java.lang.classfile.Annotation> written = new ArrayList<>();
        of.findAttribute(Attributes.runtimeVisibleAnnotations())
                .map(RuntimeVisibleAnnotationsAttribute::annotations).ifPresent(written::addAll);
        of.findAttribute(Attributes.runtimeInvisibleAnnotations())
                .map(RuntimeInvisibleAnnotationsAttribute::annotations).ifPresent(written::addAll);
        of.findAttribute(Attributes.runtimeVisibleTypeAnnotations())
                .map(RuntimeVisibleTypeAnnotationsAttribute::annotations)
                .ifPresent(each -> each.forEach(one -> written.add(one.annotation())));
        of.findAttribute(Attributes.runtimeInvisibleTypeAnnotations())
                .map(RuntimeInvisibleTypeAnnotationsAttribute::annotations)
                .ifPresent(each -> each.forEach(one -> written.add(one.annotation())));
        written.forEach(one -> take(one, out));
    }

    private static void parametersOf(MethodModel of, Set<String> out) {
        of.findAttribute(Attributes.runtimeVisibleParameterAnnotations())
                .map(RuntimeVisibleParameterAnnotationsAttribute::parameterAnnotations)
                .ifPresent(each -> each.forEach(one -> one.forEach(written -> take(written, out))));
        of.findAttribute(Attributes.runtimeInvisibleParameterAnnotations())
                .map(RuntimeInvisibleParameterAnnotationsAttribute::parameterAnnotations)
                .ifPresent(each -> each.forEach(one -> one.forEach(written -> take(written, out))));
    }

    private static void take(java.lang.classfile.Annotation written, Set<String> out) {
        out.add(written.className().stringValue());
        written.elements().forEach(element -> take(element.value(), out));
    }

    private static void take(java.lang.classfile.AnnotationValue value, Set<String> out) {
        switch (value) {
            case java.lang.classfile.AnnotationValue.OfClass named ->
                    out.add(named.className().stringValue());
            case java.lang.classfile.AnnotationValue.OfEnum named ->
                    out.add(named.className().stringValue());
            case java.lang.classfile.AnnotationValue.OfAnnotation nested ->
                    take(nested.annotation(), out);
            case java.lang.classfile.AnnotationValue.OfArray several ->
                    several.values().forEach(each -> take(each, out));
            default -> { }
        }
    }

    /** Every class this compiler was built into, found from where one of them is loaded from. */
    private static List<ClassModel> compiled() throws IOException, URISyntaxException {
        Path root = Path.of(InputReads.class.getResource("InputReads.class").toURI());
        for (int up = 0; up <= ENVIRONMENT.chars().filter(each -> each == '/').count(); up++) {
            root = root.getParent();
        }
        List<ClassModel> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path each : walk.filter(one -> one.toString().endsWith(".class")).sorted()
                    .toList()) {
                out.add(ClassFile.of().parse(Files.readAllBytes(each)));
            }
        }
        return out;
    }

    private static ClassModel parsed(Class<?> of) throws IOException {
        String binary = of.getName();
        String resource = binary.substring(binary.lastIndexOf('.') + 1) + ".class";
        try (var in = of.getResourceAsStream(resource)) {
            return ClassFile.of().parse(in.readAllBytes());
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE,
        java.lang.annotation.ElementType.TYPE_USE})
    private @interface Names {
        Class<?> value();
    }

    /** The same, where a type may not carry it, so a parameter's own attribute is the only place
     *  it can go. */
    @Retention(RetentionPolicy.RUNTIME)
    @java.lang.annotation.Target(java.lang.annotation.ElementType.PARAMETER)
    private @interface NamesOnAParameter {
        Class<?> value();
    }

    private static final class InAMethodDescriptor {
        static InputDomain none() {
            return null;
        }
    }

    private static final class InAFieldDescriptor {
        private static InputDomain none;

        static boolean isNull() {
            return none == null;
        }
    }

    private static final class InAClassReference {
        static boolean isOne(Object what) {
            return what instanceof InputDomain;
        }
    }

    private static final class InAMemberType {
        static Object taken() {
            // The call is what puts a member of the reading in this class file, which is what is
            // being witnessed. A reading is made by walking an input and there is none to walk
            // here, so the call is written and not made.
            InputDomain read = null;
            return read == null ? List.of() : read.positions();
        }
    }

    private static final class InAClassSignature extends java.util.ArrayList<InputDomain> {
        private static final long serialVersionUID = 1L;
    }

    private static final class InAMethodSignature {
        // The concrete class in the signature is what puts the reading in the class file, which is
        // what this witnesses.
        @SuppressWarnings("NonApiType")
        static java.util.ArrayList<InputDomain> none() {
            return null;
        }
    }

    private record InARecordComponent(InputDomain held) {
    }

    @Names(InputDomain.class)
    private static final class InAnAnnotation {
    }

    /**
     * On a parameter, which the method's own descriptor says nothing about and which is an
     * attribute of the method rather than of anything with attributes of its own.
     *
     * <p>Written with an annotation a type may not carry, so this is the parameter's attribute and
     * not the method's list of annotations on types: one that could be written on a type would be
     * in both, and would be found by a walk that read only the second.
     */
    private static final class InAParameterAnnotation {
        static Object none(@NamesOnAParameter(InputDomain.class) Object value) {
            return value;
        }
    }

    /** On a type written inside a body, which is an attribute of the code and not of the method. */
    private static final class InACodeTypeAnnotation {
        static String none(Object value) {
            return (@Names(InputDomain.class) String) value;
        }
    }

    private static final class InAMethodType {
        // The lambda is what puts the reading in the method's type, which is what this witnesses.
        @SuppressWarnings("UnnecessaryLambda")
        static Supplier<InputDomain> none() {
            return () -> null;
        }
    }

    /**
     * A field of the reading's type beside a string constant spelling that same descriptor. One
     * {@code Utf8} holds both, so a walk that took string constants out by the entry they use took
     * the field's descriptor with them.
     */
    private static final class SharedWithAStringConstant {
        static final String SPELLED = "Lsouther/compiler/inputs/InputDomain;";

        private static InputDomain none;

        static boolean isNull() {
            return none == null;
        }
    }

    /** And the same string constant with nothing of the reading's type beside it. */
    private static final class OnlyAsAStringConstant {
        static final String SPELLED = "Lsouther/compiler/inputs/InputDomain;";
    }
}
