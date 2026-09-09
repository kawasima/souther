package souther.architecture;

import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationElement;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.AttributedElement;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.constantpool.StringEntry;
import java.lang.classfile.instruction.ConstantInstruction;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The text a check has written down, by where it wrote it.
 *
 * <p>What a rule about what a check <em>says</em> reads. Such a rule asks whether two words stand
 * together in one thing somebody wrote — a directory a build writes to beside the one it compiles
 * into, a step out of a module beside the name of another — so the text has to come back grouped by
 * the thing that was written, and not as one bag for the class.
 *
 * <p><b>What a check says is not only what its code loads.</b> A parameterized case takes its
 * subjects from {@code @ValueSource} or {@code @CsvSource}, and a corpus named there is named as
 * plainly as one named in the body. That text is an annotation's element value and never reaches
 * the code, so a reading that walked the instructions saw the same check as saying nothing. Both
 * rules here had that reading, written out twice, and a rule is only as wide as what it is given to
 * read.
 *
 * <p>Grouped by method for what a method's code and its own annotations say, and by class for what
 * the class and its fields say. A field or a class annotation is written by the class rather than by
 * any one of its methods, and putting it in every method's row would report a method that says
 * nothing of the kind.
 */
final class WhatACheckSays {

    private WhatACheckSays() {}

    /**
     * Everything {@code check} says, by where it says it.
     *
     * <p>The key is what a rule names when it reports: {@code class#method} for a method's own, and
     * the class's name for what is written outside one.
     */
    static Map<String, List<String>> of(ClassModel check) {
        String named = check.thisClass().asInternalName().replace('/', '.');
        Map<String, List<String>> said = new LinkedHashMap<>();

        List<String> outsideAMethod = new ArrayList<>();
        annotationsOn(check, outsideAMethod);
        for (FieldModel field : check.fields()) {
            annotationsOn(field, outsideAMethod);
            field.findAttribute(Attributes.constantValue()).ifPresent(held -> {
                if (held.constant() instanceof StringEntry text) {
                    outsideAMethod.add(text.stringValue());
                }
            });
        }
        if (!outsideAMethod.isEmpty()) {
            said.put(named, outsideAMethod);
        }

        for (MethodModel method : check.methods()) {
            List<String> here = new ArrayList<>();
            CodeModel code = method.code().orElse(null);
            if (code != null) {
                for (var element : code) {
                    if (element instanceof ConstantInstruction loaded
                            && loaded.constantValue() instanceof String text) {
                        here.add(text);
                    }
                }
            }
            annotationsOn(method, here);
            method.findAttribute(Attributes.runtimeVisibleParameterAnnotations())
                    .ifPresent(taken -> taken.parameterAnnotations()
                            .forEach(each -> each.forEach(one -> valuesOf(one, here))));
            method.findAttribute(Attributes.runtimeInvisibleParameterAnnotations())
                    .ifPresent(taken -> taken.parameterAnnotations()
                            .forEach(each -> each.forEach(one -> valuesOf(one, here))));
            if (!here.isEmpty()) {
                said.put(named + "#" + method.methodName().stringValue(), here);
            }
        }
        return said;
    }

    /**
     * What the annotations on one declaration say.
     *
     * <p>Both retentions, because which one an annotation has is the annotation's business and not
     * the business of a rule about what somebody wrote down.
     */
    private static void annotationsOn(AttributedElement declaration, List<String> into) {
        declaration.findAttribute(Attributes.runtimeVisibleAnnotations())
                .ifPresent(taken -> taken.annotations().forEach(one -> valuesOf(one, into)));
        declaration.findAttribute(Attributes.runtimeInvisibleAnnotations())
                .ifPresent(taken -> taken.annotations().forEach(one -> valuesOf(one, into)));
    }

    /** What one annotation says, through the arrays and the annotations written inside it. */
    private static void valuesOf(Annotation annotation, List<String> into) {
        for (AnnotationElement element : annotation.elements()) {
            valuesOf(element.value(), into);
        }
    }

    private static void valuesOf(AnnotationValue value, List<String> into) {
        switch (value) {
            case AnnotationValue.OfString text -> into.add(text.stringValue());
            case AnnotationValue.OfArray many -> many.values().forEach(one -> valuesOf(one, into));
            case AnnotationValue.OfAnnotation inside -> valuesOf(inside.annotation(), into);
            default -> { }
        }
    }
}
