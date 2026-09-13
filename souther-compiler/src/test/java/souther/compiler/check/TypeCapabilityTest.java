package souther.compiler.check;

import souther.compiler.types.Type;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three capability questions are answered per type constructor in one place. They are three
 * questions, not one: equality and the external form descend into what a collection holds, and an
 * ordering does not, because a collection has none of its own whatever it holds.
 */
class TypeCapabilityTest {

    @Test
    void aTupleComparesButDoesNotOrder() {
        Type pair = Type.tuple(List.of(Type.STRING, Type.STRING));
        assertTrue(TypeOps.supportsEquality(pair));
        assertFalse(TypeOps.supportsOrdering(pair, NewtypeInners.NONE, null, DeclarationKinds.NONE, PublishedDeclarations.NONE));
    }

    @Test
    void aFunctionAnswersNoneOfTheThree() {
        Type fn = Type.fn(List.of(Type.INT), Type.BOOL);
        assertFalse(TypeOps.supportsEquality(fn));
        assertFalse(TypeOps.supportsOrdering(fn, NewtypeInners.NONE, null, DeclarationKinds.NONE, PublishedDeclarations.NONE));
        assertFalse(TypeOps.hasExternalForm(fn, null));
    }

    @Test
    void aCollectionOfFunctionsCannotBeCompared() {
        Type fn = Type.fn(List.of(Type.INT), Type.BOOL);
        assertFalse(TypeOps.supportsEquality(Type.list(fn)));
        assertFalse(TypeOps.supportsEquality(Type.set(fn)));
        assertFalse(TypeOps.supportsEquality(Type.option(fn)));
        assertFalse(TypeOps.supportsEquality(Type.map(Type.STRING, fn)));
        assertFalse(TypeOps.supportsEquality(Type.tuple(List.of(Type.INT, fn))));
    }

    @Test
    void aListOfOrderedElementsIsNotItselfOrdered() {
        assertFalse(TypeOps.supportsOrdering(Type.list(Type.INT), NewtypeInners.NONE, null, DeclarationKinds.NONE, PublishedDeclarations.NONE));
        assertTrue(TypeOps.supportsEquality(Type.list(Type.INT)));
    }

    @Test
    void theOrderedPrimitivesAreTheFiveThatCarryAnOrder() {
        assertTrue(TypeOps.supportsOrdering(Type.INT, NewtypeInners.NONE, null, DeclarationKinds.NONE, PublishedDeclarations.NONE));
        assertTrue(TypeOps.supportsOrdering(Type.STRING, NewtypeInners.NONE, null, DeclarationKinds.NONE, PublishedDeclarations.NONE));
        assertTrue(TypeOps.supportsOrdering(Type.DECIMAL, NewtypeInners.NONE, null, DeclarationKinds.NONE, PublishedDeclarations.NONE));
        assertTrue(TypeOps.supportsOrdering(Type.DATE, NewtypeInners.NONE, null, DeclarationKinds.NONE, PublishedDeclarations.NONE));
        assertTrue(TypeOps.supportsOrdering(Type.DATETIME, NewtypeInners.NONE, null, DeclarationKinds.NONE, PublishedDeclarations.NONE));
    }

    @Test
    void boolAndRawCompareButDoNotOrder() {
        assertTrue(TypeOps.supportsEquality(Type.BOOL));
        assertFalse(TypeOps.supportsOrdering(Type.BOOL, NewtypeInners.NONE, null, DeclarationKinds.NONE, PublishedDeclarations.NONE));
        assertTrue(TypeOps.supportsEquality(Type.RAW));
        assertFalse(TypeOps.supportsOrdering(Type.RAW, NewtypeInners.NONE, null, DeclarationKinds.NONE, PublishedDeclarations.NONE));
    }

    @Test
    void onlyAFunctionIsRefusedAnExternalForm() {
        assertTrue(TypeOps.hasExternalForm(Type.list(Type.STRING), null));
        assertTrue(TypeOps.hasExternalForm(Type.map(Type.STRING, Type.DECIMAL), null));
        assertFalse(TypeOps.hasExternalForm(Type.list(Type.fn(List.of(), Type.INT)), null));
    }
}
