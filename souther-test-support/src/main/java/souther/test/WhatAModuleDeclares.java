package souther.test;

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodSignature;
import java.lang.classfile.Signature;
import java.lang.classfile.attribute.RecordAttribute;
import java.lang.classfile.attribute.RecordComponentInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * What one module compiled, read off its class files.
 *
 * <p>The mechanism a check on names needs, and none of the policy. Which names a module reserves
 * and what they may answer is that module's to say; finding the classes and reading a declaration's
 * type off them is the same work wherever it is asked, and written out per module it drifts — the
 * copy that stops seeing record components goes on reporting a pass.
 *
 * <p><b>One module, found from a class of it.</b> Not a walk over whatever {@code target/classes}
 * directories exist beside it: a module built earlier in the reactor may have last run's classes
 * on disk and a module not built at all has none, so a check that swept the file system would prove
 * something about a build nobody ran. A marker class is loaded from the classpath the test was
 * given, which is the module as this run built it.
 *
 * <p>The classes themselves come from {@link CompiledClasses}, which is where a compiled output is
 * read. What is asked of them is here; how many times the files behind them are opened is not a
 * question this has to hold an answer to.
 */
public final class WhatAModuleDeclares {

    private final List<ClassModel> classes;

    private WhatAModuleDeclares(List<ClassModel> classes) {
        this.classes = List.copyOf(classes);
    }

    /**
     * The classes of the module {@code marker} was compiled into.
     *
     * @param marker any class of that module's main sources
     */
    public static WhatAModuleDeclares of(Class<?> marker) {
        return new WhatAModuleDeclares(CompiledClasses.ofModule(marker).all());
    }

    /** Every class of it. */
    public List<ClassModel> classes() {
        return classes;
    }

    /**
     * Every declaration of it that goes by one of {@code names}.
     *
     * <p>Every one of them, and not every name. A record's component, the field the compiler writes
     * for it and the accessor that answers it are one thing the author wrote, and a class file says
     * which two those are: the field goes by the component's name and descriptor, and the accessor
     * goes by its name, takes nothing and answers its type. Those two are left out and everything
     * else is here — a field and a method of one name are two declarations, and so are a component
     * and an overload beside it. Collapsing them by the name they share would be answering about
     * names while being asked about declarations, and a check reading this would stop at the first
     * of them.
     */
    public List<Declared> taking(Set<String> names) {
        List<Declared> found = new ArrayList<>();
        for (ClassModel each : classes) {
            found.addAll(takenIn(each, names));
        }
        return found;
    }

    private static List<Declared> takenIn(ClassModel of, Set<String> names) {
        List<Declared> found = new ArrayList<>();
        List<RecordComponentInfo> components = of.findAttribute(Attributes.record())
                .map(RecordAttribute::components).orElse(List.of());
        for (RecordComponentInfo each : components) {
            if (names.contains(each.name().stringValue())) {
                found.add(new Declared(of, each.name().stringValue(), signatureOf(each),
                        Kind.RECORD_COMPONENT));
            }
        }
        for (FieldModel each : of.fields()) {
            String name = each.fieldName().stringValue();
            if (names.contains(name) && !writtenFor(components, name,
                    each.fieldTypeSymbol().descriptorString())) {
                found.add(new Declared(of, name, signatureOf(each), Kind.FIELD));
            }
        }
        for (MethodModel each : of.methods()) {
            String name = each.methodName().stringValue();
            boolean answersAComponent = each.methodTypeSymbol().parameterCount() == 0
                    && writtenFor(components, name,
                            each.methodTypeSymbol().returnType().descriptorString());
            if (names.contains(name) && !answersAComponent) {
                found.add(new Declared(of, name, answeredBy(each), Kind.METHOD));
            }
        }
        return found;
    }

    /** Whether a record wrote a component of this name and type, which is what a field of the same
     *  name and type, or an accessor answering it, belongs to. */
    private static boolean writtenFor(List<RecordComponentInfo> components, String name,
                                      String descriptor) {
        return components.stream().anyMatch(each -> each.name().stringValue().equals(name)
                && each.descriptorSymbol().descriptorString().equals(descriptor));
    }

    /** How a declaration was written, which is what tells a component from a member beside it. */
    public enum Kind {
        /** A record's component, whose name a reader of the record's own form may go by. */
        RECORD_COMPONENT,
        /** A field the author wrote. */
        FIELD,
        /** A method the author wrote. */
        METHOD
    }

    /** One declaration, and what it answers. */
    public record Declared(ClassModel owner, String name, Signature type, Kind kind) {

        /** What the class holding it is called, as a class file spells it. */
        public String ownerName() {
            return owner.thisClass().asInternalName();
        }

        /** What the declaration reads as, for a check saying which one it found. */
        public String shown() {
            return ownerName() + "." + name + " : " + Signatures.shown(type);
        }
    }

    private static Signature answeredBy(MethodModel of) {
        return of.findAttribute(Attributes.signature())
                .map(it -> MethodSignature.parseFrom(it.signature().stringValue()).result())
                .orElseGet(() -> Signature.of(of.methodTypeSymbol().returnType()));
    }

    private static Signature signatureOf(FieldModel of) {
        return of.findAttribute(Attributes.signature())
                .map(it -> Signature.parseFrom(it.signature().stringValue()))
                .orElseGet(() -> Signature.of(of.fieldTypeSymbol()));
    }

    private static Signature signatureOf(RecordComponentInfo of) {
        return of.findAttribute(Attributes.signature())
                .map(it -> Signature.parseFrom(it.signature().stringValue()))
                .orElseGet(() -> Signature.of(of.descriptorSymbol()));
    }
}
