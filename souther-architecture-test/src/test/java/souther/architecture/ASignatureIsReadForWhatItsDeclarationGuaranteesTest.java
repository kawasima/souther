package souther.architecture;

import souther.architecture.WhatASignatureReaches.Scope;
import souther.compiler.check.DerivedSymbols;
import souther.compiler.check.ResolvedSymbols;
import souther.compiler.check.Symbols;

import org.junit.jupiter.api.Test;

import java.lang.classfile.Attributes;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodSignature;
import java.lang.classfile.Signature;
import java.lang.classfile.attribute.SignatureAttribute;
import java.lang.constant.ClassDesc;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a reading of a signature answers, held over declarations written here to be read.
 *
 * <p>{@link WhatASignatureReaches} is what the rules about a compiled surface are written against,
 * and the shapes it has to answer for are not shapes this repository writes. Nothing here is
 * declared with a world arriving through a type variable, which is the whole reason a reading that
 * could not see one went unnoticed. So the subjects are declared below and read out of this class's
 * own class file: what javac wrote for a declaration, rather than a signature spelled by hand into
 * a fixture, which would hold of a grammar this test believed in instead of the one there is.
 *
 * <p>Four of these say what the reading means and the rest say that each branch of the two
 * algebras it walks was walked.
 */
class ASignatureIsReadForWhatItsDeclarationGuaranteesTest {

    private static final String HERE =
            "souther/architecture/ASignatureIsReadForWhatItsDeclarationGuaranteesTest";

    private static final String DECLARATIONS = HERE + "$Declarations";

    private static final String THE_DERIVED_WORLD = internal(DerivedSymbols.class);
    private static final String THE_RESOLVED_WORLD = internal(ResolvedSymbols.class);

    private static final CompiledOutputs COMPILED = CompiledOutputs.ofEverythingCompiledHere();

    private static final WhatASignatureReaches READING = new WhatASignatureReaches(COMPILED);

    private static String internal(Class<?> type) {
        return type.getName().replace('.', '/');
    }

    /** One declaration below, read as a rule reads it. */
    private static MethodSignature signatureOf(String owner, String method) {
        MethodModel found = COMPILED.read(owner).methods().stream()
                .filter(each -> each.methodName().equalsString(method))
                .findFirst().orElseThrow(() -> new AssertionError("no " + method + " in " + owner));
        return found.findAttribute(Attributes.signature())
                .map(SignatureAttribute::asMethodSignature)
                .orElseGet(() -> MethodSignature.of(found.methodTypeSymbol()));
    }

    /** The bindings that declaration is read under: its class's, and its own standing on them. */
    private static Scope scopeOf(String owner, String method) {
        return Scope.of(owner, WhatASignatureReaches.typeParametersOf(COMPILED.read(owner)))
                .and(method, signatureOf(owner, method).typeParameters());
    }

    /** What the arguments of one declaration below reach. */
    private static boolean takes(String owner, String method, String wanted) {
        return READING.anyOf(signatureOf(owner, method).arguments(), scopeOf(owner, method),
                Set.of(wanted));
    }

    private static boolean takes(String method, String wanted) {
        return takes(DECLARATIONS, method, wanted);
    }

    @Test
    void aWorldArrivingThroughAVariableIsTheWorldItsBoundNames() {
        assertTrue(takes("aBoundThatIsReferredTo", THE_DERIVED_WORLD),
                "a parameter written as a type variable is read for what its declaration"
                        + " guarantees, which is what its bound says");
    }

    @Test
    void aVariableWithNothingBoundingItReachesNoWorld() {
        assertFalse(takes("aVariableWithNoBoundOfItsOwn", THE_DERIVED_WORLD),
                "a variable bounded by nothing guarantees nothing, and a reading that answered"
                        + " otherwise would be inventing the bound it wanted to find");
    }

    @Test
    void aBoundNothingRefersToIsReachedThroughNothing() {
        assertFalse(takes("aBoundNothingRefersTo", THE_DERIVED_WORLD),
                "a type parameter is what the variables written in the two halves stand for and is"
                        + " not itself a place a reading starts, so a bound the declaration never"
                        + " writes is a bound nothing arrives through");
    }

    @Test
    void aVariableIsTheOneDeclaredNearestAndNotTheOneOfTheSameName() {
        assertTrue(takes(HERE + "$Owned", "shadowing", THE_RESOLVED_WORLD),
                "a method declaring a variable its class also names is read with its own");
        assertFalse(takes(HERE + "$Owned", "shadowing", THE_DERIVED_WORLD),
                "and not with the one it shadows, which a reading keyed by the name alone would"
                        + " have answered with");
    }

    @Test
    void aBoundIsReadWhereItsVariableWasDeclared() {
        for (String owner : List.of(HERE + "$BoundedByItsSibling",
                HERE + "$BoundedThroughAnArgument")) {
            assertTrue(takes(owner, "throughTheClassesBound", THE_DERIVED_WORLD),
                    owner + ": a variable bounded by another of its class stands for what that one"
                            + " was declared to be, and where that name was declared is where the"
                            + " bound is read");
            assertFalse(takes(owner, "throughTheClassesBound", THE_RESOLVED_WORLD),
                    owner + ": and not where the variable was written — a method naming the same"
                            + " letter binds it for what the method writes, never for what its"
                            + " class already said");
        }
    }

    @Test
    void aReportNamesEveryFrameAndWhatDeclaredIt() {
        String shown = scopeOf(HERE + "$BoundedByItsSibling", "throughTheClassesBound").shown();

        assertTrue(shown.contains(
                        "$BoundedByItsSibling<B extends " + THE_DERIVED_WORLD + ", A extends B>"),
                "a frame is named by what declared it and by what it declared, so a report about"
                        + " a letter says which frame is being spoken of: " + shown);
        assertTrue(shown.contains("throughTheClassesBound<B extends " + THE_RESOLVED_WORLD + ">"),
                "and the frame standing on it is named too, or a letter both of them declare is"
                        + " reported as one: " + shown);
    }

    @Test
    void aVariableOfTheClassIsInScopeInItsMethods() {
        assertTrue(takes(HERE + "$Owned", "fromTheClassesOwnVariable", THE_DERIVED_WORLD),
                "a class declares type parameters its methods write, so the bindings a reading is"
                        + " given are the class's with the method's over them");
    }

    @Test
    void aVariableBoundedByItselfIsFollowedOnce() {
        assertFalse(takes("aVariableBoundedByItself", THE_DERIVED_WORLD),
                "a variable whose bound names it again is ordinary Java, and the closure of a"
                        + " bound is finite because a binding already followed is not followed"
                        + " again");
    }

    @Test
    void whatBoundsAVariableIsEveryBoundAndNotTheFirst() {
        assertTrue(takes("aVariableBoundedByInterfaces", HERE + "$AMark"),
                "a variable bounded by interfaces has no class bound at all, so a reading that"
                        + " asked only for that one would answer for none of them");
    }

    @Test
    void anArrayOfAVariableReachesWhatTheVariableDoes() {
        assertTrue(takes("anArrayOfTheVariable", THE_DERIVED_WORLD),
                "an array is what it is an array of");
    }

    @Test
    void anArgumentUnderAWildcardIsRead() {
        assertTrue(takes("anArgumentUnderExtends", THE_DERIVED_WORLD),
                "a bound written on a wildcard is a type the signature names");
        assertTrue(takes("anArgumentUnderSuper", THE_DERIVED_WORLD),
                "and so is one written under a lower wildcard: what is read is which types the"
                        + " declaration names, not which of them a value could be assigned to");
        assertFalse(takes("anArgumentThatIsAnything", THE_DERIVED_WORLD),
                "a wildcard with no bound names nothing");
    }

    @Test
    void theTypeAnInnerOneIsWrittenUnderIsRead() {
        assertTrue(takes("anOuterTypesArgument", THE_DERIVED_WORLD),
                "a nested type carries the arguments of the type it is nested in, and a reading"
                        + " that stopped at the inner name would walk past them");
    }

    @Test
    void aComponentOfARecordIsReadUnderThatRecordsOwnVariables() {
        assertTrue(takes("aComponentUnderTheRecordsOwnBound", THE_DERIVED_WORLD),
                "a record's component is written in the record's variables, so what it reaches is"
                        + " what that record declared them to be");
        assertFalse(takes("aRecordThatCannotSeeTheCallersBinding", THE_DERIVED_WORLD),
                "and never in the caller's: a record is static wherever it is written, so a"
                        + " reading that kept the scope it arrived with would answer for a binding"
                        + " the component cannot name");
    }

    @Test
    void aRecordHoldingItsOwnKindIsOpenedOnce() {
        assertTrue(takes("aRecordThatHoldsItsOwnKind", THE_DERIVED_WORLD),
                "a record whose component holds another of its own kind is a record that can be"
                        + " written, and what it holds beside that is read, so a reading arrives at"
                        + " the second component rather than at the first one for ever");
    }

    @Test
    void aDeclarationWithNoGenericsIsReadAsOneWithThem() {
        assertTrue(takes("aWorldSpelledOutright", THE_DERIVED_WORLD),
                "a method with nothing generic about it writes no signature of its own, and its"
                        + " descriptor is read as the signature it is");
        assertTrue(takes("aComponentWrittenWithoutGenerics", THE_DERIVED_WORLD),
                "and so is a record component's, so that whether a declaration is generic is"
                        + " settled before the reading and not inside it");
    }

    @Test
    void aVariableBoundNowhereInScopeFailsTheReading() {
        AssertionError refused = assertThrows(AssertionError.class,
                () -> takes(HERE + "$Enclosing$Nested", "takesTheEnclosingVariable",
                        THE_DERIVED_WORLD));

        assertTrue(refused.getMessage().contains("bound nowhere"),
                "a method of an inner class writes the variables of the class enclosing it, which"
                        + " are in neither of the two places these bindings come from — and a"
                        + " reading that cannot say what a variable stands for says so, because"
                        + " answering that it reaches nothing is how the spelling this reading"
                        + " replaced went unread: " + refused.getMessage());
    }

    @Test
    void aClassOfThisRepositoryThatWasNotBuiltFailsTheReading() {
        Signature absent = Signature.of(ClassDesc.of("souther.architecture.NothingCompiledThis"));

        AssertionError refused = assertThrows(AssertionError.class, () -> READING.reaches(absent,
                Scope.of(HERE, List.of()), Set.of(THE_DERIVED_WORLD)));

        assertTrue(refused.getMessage().contains("not built here"),
                "the same holds of a class whose components a reading has to open and cannot find,"
                        + " and it is the reading that has to say so rather than the lookup under"
                        + " it: " + refused.getMessage());
    }

    @Test
    void whatIsPublishedIsNotWhatWasCompiledBesideIt() {
        assertTrue(CompiledOutputs.ofEverythingCompiledHere().find(DECLARATIONS).isPresent(),
                "the subjects declared here are compiled where test output goes, which is the"
                        + " population this test's readings are about");
        assertTrue(CompiledOutputs.ofWhatThisRepositoryPublishes().find(DECLARATIONS).isEmpty(),
                "and are not in the one a rule about a compiled surface is about, which is why the"
                        + " two are named apart rather than searched together");
    }

    @Test
    void theDeclarationsTheseReadingsAreAboutWereRead() {
        assertEquals(1, COMPILED.read(DECLARATIONS).methods().stream()
                        .filter(each -> each.methodName().equalsString("aBoundThatIsReferredTo"))
                        .count(),
                "the subjects declared here were compiled and found, so a reading that answered"
                        + " nothing answered about something");
    }

    /** Something for a variable to be bounded by that is not a class. */
    interface AMark {
    }

    /** A class that declares a variable of its own, for the two readings that are about which
     *  binding a name is. */
    static class Owned<S extends DerivedSymbols> {

        void fromTheClassesOwnVariable(S world) {
        }

        // The name is its class's again, which is what this one is for: a reading keyed by the
        // name alone answers with the wrong bound here, and a subject that spelled the two apart
        // could not say so.
        @SuppressWarnings("TypeParameterShadowing")
        <S extends ResolvedSymbols> void shadowing(S world) {
        }
    }

    /** A class whose second variable is bounded by its first, with a method that names the first
     *  again. What the second stands for is settled where it was declared. */
    static class BoundedByItsSibling<B extends DerivedSymbols, A extends B> {

        @SuppressWarnings("TypeParameterShadowing")
        <B extends ResolvedSymbols> void throughTheClassesBound(A world) {
        }
    }

    /** The same, with the bound reaching its sibling through a type argument and the sibling
     *  declared after the variable that names it. */
    static class BoundedThroughAnArgument<A extends List<B>, B extends DerivedSymbols> {

        @SuppressWarnings("TypeParameterShadowing")
        <B extends ResolvedSymbols> void throughTheClassesBound(A world) {
        }
    }

    /** A class whose variable a class inside it writes, which is a binding neither the inner
     *  class's signature nor its method's declares. */
    static class Enclosing<S extends DerivedSymbols> {

        class Nested {

            void takesTheEnclosingVariable(S world) {
            }
        }
    }

    /**
     * The subjects.
     *
     * <p>Written where javac compiles them rather than spelled as signatures, and reachable from
     * outside this class so that what is read is a declaration and not a member kept alive for a
     * reading to find.
     */
    static class Declarations {

        record Held<T>(T it) {
        }

        record Bounded<T extends DerivedSymbols>(T it) {
        }

        record Plain(DerivedSymbols world) {
        }

        record HoldingItsOwnKind(List<HoldingItsOwnKind> more, DerivedSymbols world) {
        }

        <S extends DerivedSymbols> void aBoundThatIsReferredTo(S world) {
        }

        <S> void aVariableWithNoBoundOfItsOwn(S world) {
        }

        <S extends DerivedSymbols> void aBoundNothingRefersTo(Symbols world) {
        }

        <S extends DerivedSymbols> void anArrayOfTheVariable(S[] worlds) {
        }

        <S extends Comparable<S>> void aVariableBoundedByItself(S it) {
        }

        <S extends Runnable & AMark> void aVariableBoundedByInterfaces(S it) {
        }

        <S extends DerivedSymbols> void anArgumentUnderExtends(List<? extends S> them) {
        }

        <S extends DerivedSymbols> void anArgumentUnderSuper(List<? super S> them) {
        }

        <S extends DerivedSymbols> void anArgumentThatIsAnything(List<?> them) {
        }

        <S extends DerivedSymbols> void anOuterTypesArgument(Enclosing<S>.Nested it) {
        }

        void aComponentUnderTheRecordsOwnBound(Bounded<?> it) {
        }

        <T extends DerivedSymbols> void aRecordThatCannotSeeTheCallersBinding(Held<?> it) {
        }

        void aComponentWrittenWithoutGenerics(Plain it) {
        }

        void aRecordThatHoldsItsOwnKind(HoldingItsOwnKind it) {
        }

        void aWorldSpelledOutright(DerivedSymbols world) {
        }
    }
}
