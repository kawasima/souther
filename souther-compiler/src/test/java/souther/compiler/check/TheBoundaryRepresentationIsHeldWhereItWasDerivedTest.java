package souther.compiler.check;

import org.junit.jupiter.api.Test;
import souther.compiler.WhatWasCompiled;

import souther.compiler.ast.Hir;

import java.lang.classfile.ClassModel;
import java.lang.classfile.constantpool.MethodRefEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How a value of a product crosses is held by the stage that derived it, and by nothing above.
 *
 * <p>Three propositions, and they are one arrangement read from three sides. A declaration as
 * resolution left it says nothing about how a value of it crosses, because there is nowhere on it to
 * say it. A declaration the derivation answered for says it always, because there is no way to make
 * one without saying it. And the way to make one is the derivation, because nothing else can reach
 * the constructor.
 *
 * <p>Held syntactically on purpose. What each of these rules out is a shape rather than a behavior,
 * and a shape is what a later change reaches for first: an {@code Optional} added back to hold "the
 * one we could not derive", a second factory for a declaration somebody wants to put on a surface, a
 * reader given the node where it wanted the representation.
 */
class TheBoundaryRepresentationIsHeldWhereItWasDerivedTest {

    /**
     * A declaration as resolution left it holds no representation.
     *
     * <p>Read of the accessors rather than of the components, so a hand-written reader that worked
     * one out and answered with it is refused as well: what this says is that nobody asks a resolved
     * declaration how a value of it crosses, and a method is how they would ask.
     */
    @Test
    void aResolvedDeclarationAnswersNothingAboutHowAValueCrosses() {
        List<String> answering = new ArrayList<>();
        for (Method m : Hir.Data.class.getMethods()) {
            if (m.getReturnType() == Hir.DecoderDef.class
                    || m.getReturnType() == Hir.EncoderDef.class
                    || mentions(m.getGenericReturnType(), Hir.DecoderDef.class)
                    || mentions(m.getGenericReturnType(), Hir.EncoderDef.class)) {
                answering.add(m.getName());
            }
        }
        assertEquals(List.of(), answering,
                "a declaration resolution left says how a value of it crosses");
    }

    /**
     * And a derived one holds both, with nothing optional about either.
     *
     * <p>The components are read and not the accessors, because what is being refused is the state:
     * a field that may be empty is a product that reached this stage without a representation, which
     * is the thing every reader below would have to ask about.
     */
    @Test
    void aDerivedProductHoldsBothAndNeitherIsOptional() {
        Set<Class<?>> held = new LinkedHashSet<>();
        for (java.lang.reflect.Field f : Derived.Data.class.getDeclaredFields()) {
            held.add(f.getType());
        }
        assertTrue(held.contains(Hir.DecoderDef.class), "it holds how a value is read: " + held);
        assertTrue(held.contains(Hir.EncoderDef.class), "and how one is written: " + held);
        assertFalse(held.contains(java.util.Optional.class),
                "and holds nothing that may be empty: " + held);
    }

    /**
     * The one way to make one is to derive a declaration.
     *
     * <p>Counted over the class files rather than asked of the type, because what is being closed is
     * the reach: a constructor is package-private and every class of this package could call it, so
     * what says only one does is that only one names it. Read as an exact match on the owner and the
     * name, since a partial match takes {@code Derived$Data} for {@code Derived$DataSomething} and
     * would go on passing after a second holder appeared.
     */
    @Test
    void theOnlyWayToMakeOneIsToDeriveADeclaration() {
        Set<String> making = new LinkedHashSet<>();
        for (ClassModel model : WhatWasCompiled.compiled().all()) {
            String owner = model.thisClass().asInternalName();
            for (PoolEntry entry : model.constantPool()) {
                if (entry instanceof MethodRefEntry ref
                        && ref.owner().asInternalName()
                                .equals("souther/compiler/check/Derived$Data")
                        && ref.name().stringValue().equals("<init>")) {
                    making.add(owner);
                }
            }
        }
        assertEquals(Set.of("souther/compiler/check/Derived$Def"), making,
                "somewhere other than the derivation builds a derived product");
    }

    /** A field of {@code Derived.Data} is reached through the type and never through a raw node,
     *  which is what says the two accessors below are the whole of the reach. */
    @Test
    void whatADerivedProductAnswersWithIsTheRepresentationAndTheDeclaration() {
        Set<String> answers = new LinkedHashSet<>();
        for (Method m : Derived.Data.class.getDeclaredMethods()) {
            if (m.getParameterCount() == 0 && !m.getName().equals("hashCode")) {
                answers.add(m.getName());
            }
        }
        assertEquals(Set.of("declaration", "decoder", "encoder"), answers,
                "a derived product answers with the declaration it was made from and how a value of"
                        + " it crosses, and no more");
    }

    private static boolean mentions(java.lang.reflect.Type type, Class<?> named) {
        if (type == named) {
            return true;
        }
        if (type instanceof java.lang.reflect.ParameterizedType parameterized) {
            for (java.lang.reflect.Type argument : parameterized.getActualTypeArguments()) {
                if (mentions(argument, named)) {
                    return true;
                }
            }
        }
        return false;
    }

}
