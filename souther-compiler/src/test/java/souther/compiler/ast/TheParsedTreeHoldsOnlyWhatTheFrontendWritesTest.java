package souther.compiler.ast;

import org.junit.jupiter.api.Test;
import souther.compiler.WhatWasCompiled;

import java.lang.classfile.ClassModel;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Ast} is what the frontend answers with, so every form in it is one the frontend writes.
 *
 * <p>The parsed tree is not the tree below it with the resolution taken out. A form that comes into
 * being after resolution — a codec, which is derived from a data's shape, an expansion, which the
 * inliner writes — belongs to the representation whose pass makes it. Where such a form has a twin
 * here, nothing ever builds the twin, and every rule the reachable frontend is held to is applied
 * to a vocabulary that means nothing: an exhaustive translation is written for kinds no parse
 * answers with, and a reader takes the forms for something the language can be written in.
 *
 * <p>This asks less than that rule says, on purpose. What it measures is that the frontend's
 * compiled classes name the form somewhere — not that one of them builds it. Proving production
 * would mean following constructors through the factories {@code Ast} keeps for its own forms, and
 * the check would then be a harder thing to read than what it checks. A form the frontend names
 * without ever building would pass here; the rule is stated in {@code Ast}, and this is the cheap
 * way to find out that it has been broken.
 *
 * <p>The population is read off {@code Ast}'s nest members, so a form added tomorrow is in it the
 * day it is written. Only the concrete ones: an interface has no values of its own, and one whose
 * arms are all gone goes with them. Names are matched whole — {@code Ast$Bind} is not
 * {@code Ast$Binder}, and a check that let one stand for the other would report neither.
 */
class TheParsedTreeHoldsOnlyWhatTheFrontendWritesTest {

    private static final String FRONTEND = "souther.compiler.frontend";

    @Test
    void everyFormOfTheParsedTreeIsNamedByTheFrontendThatWritesIt() {
        Set<String> named = whatTheFrontendNames();

        List<String> unwritten = new ArrayList<>();
        for (Class<?> form : forms()) {
            if (!isNamedIn(form, named)) {
                unwritten.add(form.getSimpleName());
            }
        }

        assertEquals(List.of(), unwritten.stream().sorted().toList(),
                "no parse answers with these. Either the frontend is to write one, or the form"
                        + " belongs to the representation whose pass makes it");
    }

    /** The control: the check reads the classes it is about, so an empty answer above is an
     *  answer. */
    @Test
    void andTheCheckReadsTheFrontendAndTheFormsItIsAbout() {
        Set<String> named = whatTheFrontendNames();
        List<Class<?>> forms = forms();

        assertTrue(forms.stream().anyMatch(each -> each == Ast.Data.class),
                "the walk reaches the form a data definition is");
        assertTrue(isNamedIn(Ast.Data.class, named), "and the frontend's classes name it");
    }

    /** Whether {@code form}'s own name is one of these, as a name and not as the start of a longer
     *  one: a descriptor spells it {@code Lsouther/compiler/ast/Ast$Data;}. */
    private static boolean isNamedIn(Class<?> form, Set<String> named) {
        String internal = form.getName().replace('.', '/');
        for (String each : named) {
            for (int at = each.indexOf(internal); at >= 0; at = each.indexOf(internal, at + 1)) {
                int after = at + internal.length();
                if (after == each.length() || each.charAt(after) == ';') {
                    return true;
                }
            }
        }
        return false;
    }

    /** Every name the frontend's classes hold, descriptors and signatures included. */
    private static Set<String> whatTheFrontendNames() {
        Set<String> named = new HashSet<>();
        for (ClassModel each : WhatWasCompiled.compiled().inPackage(FRONTEND)) {
            for (PoolEntry entry : each.constantPool()) {
                if (entry instanceof Utf8Entry text) {
                    named.add(text.stringValue());
                }
            }
        }
        return named;
    }

    /** The forms of the parsed tree: the nest members of {@link Ast} that have values. */
    private static List<Class<?>> forms() {
        return Arrays.stream(Ast.class.getNestMembers())
                .filter(each -> each.isRecord() || each.isEnum())
                .toList();
    }
}
