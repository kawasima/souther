package souther.compiler.check;

import souther.compiler.DefaultStdlib;
import souther.compiler.core.CompleteSignature;
import souther.compiler.semantics.AnswerAspect;
import souther.compiler.semantics.ArgumentRef;
import souther.compiler.semantics.BuiltFrom;
import souther.compiler.semantics.ElementLineage;
import souther.compiler.semantics.OperationFact;
import souther.compiler.semantics.SizeAgainstItsSource;
import souther.compiler.types.ValueName;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A rule names an argument of an operation, and what an operation's arguments are is the library's to
 * say. Where the two disagree there is nothing to be done at a call — the rule is about an argument
 * that is not there, or is not the kind of thing the rule is about — so the disagreement is said where
 * the tables are bound rather than met as a missing answer at whichever reader arrives first.
 *
 * <p>This holds the binding to saying it. Each rule below is one a reader would have had to defend
 * itself against, and each is now a build that does not start.
 */
class ARuleIsHeldToTheDeclarationItIsAboutTest {

    /** A row written here, including one naming an operation the library does not have — which is
     * what several of these are for, so it takes the spelling apart rather than asking the library. */
    private static ValueName op(String qualified) {
        int dot = qualified.lastIndexOf('.');
        return ValueName.Stdlib.operation(qualified.substring(0, dot), qualified.substring(dot + 1));
    }

    /** The operation read against the library, as the binder reads every one before holding a
     *  fact to it — so an operation the library does not have is refused here, one question before
     *  the argument. */
    private static CompleteSignature declared(String operation) {
        return OperationFactBinder.declaredSignature(DefaultStdlib.get(), op(operation));
    }

    private static void bindCarried(String operation, ArgumentRef container) {
        OperationFactBinder.holdToTheDeclaration(declared(operation), container,
                new ArgumentRef.TheContainer(), TypeRequirement.CONTAINER,
                "the container a predicate reads");
    }

    private static void bindBuilt(String operation, ArgumentRef from) {
        OperationFactBinder.holdToTheDeclaration(declared(operation),
                new BuiltFrom<>(new ElementLineage.SameAs<>(new ElementLineage.Source<>(from, 1)),
                        SizeAgainstItsSource.AT_MOST).from(),
                new ArgumentRef.TheContainer(), TypeRequirement.CONTAINER,
                "the container something is built from");
    }

    /** A rule saying a side of the answer turns on whether an argument holds, bound to the
     *  declaration it is about. */
    private static void bindTurnsOn(String operation,
                                    AnswerAspect aspect,
                                    ArgumentRef argument) {
        CompleteSignature declaration = declared(operation);
        OperationFactBinder.holdTurnsOn(declaration, declaration.declaring(),
                new OperationFact.TurnsOnWhetherAnArgumentHolds(
                        aspect, argument));
    }

    /**
     * A closure that answers something other than a truth decides no "whether it holds".
     *
     * <p>The two the library has are the ones this compiler must not credit. {@code List.distinctBy}
     * answers fewer where its key sends two elements to one, so the key does decide a count — and a
     * key answers whichever value it projects rather than holding or not. {@code List.filterMap}
     * answers fewer where its closure answered nothing, and what decides that is whether a value is
     * there, which is not a truth either.
     *
     * <p>Written as this fact, both would be followed as one: a rule inside such a closure would be
     * credited with deciding whether the answer is empty, and a model nothing read would come back
     * read. So they are refused where the fact meets the signature.
     */
    @Test
    void aClosureAnsweringSomethingOtherThanATruthIsRefused() {
        IllegalStateException key = assertThrows(IllegalStateException.class,
                () -> bindTurnsOn("List.distinctBy",
                        AnswerAspect.EMPTINESS,
                        new ArgumentRef.TheClosure()));
        assertTrue(key.getMessage().contains("whether it holds is not something to read"),
                key.getMessage());

        IllegalStateException optional = assertThrows(IllegalStateException.class,
                () -> bindTurnsOn("List.filterMap",
                        AnswerAspect.EMPTINESS,
                        new ArgumentRef.TheClosure()));
        assertTrue(optional.getMessage().contains("whether it holds is not something to read"),
                optional.getMessage());
    }

    /** And a side of the answer the answer does not have. */
    @Test
    void aSideTheAnswerDoesNotHaveIsRefused() {
        IllegalStateException truth = assertThrows(IllegalStateException.class,
                () -> bindTurnsOn("List.filter",
                        AnswerAspect.TRUTH,
                        new ArgumentRef.TheClosure()));
        assertTrue(truth.getMessage().contains("no truth for an argument to decide"),
                truth.getMessage());

        IllegalStateException empty = assertThrows(IllegalStateException.class,
                () -> bindTurnsOn("List.any",
                        AnswerAspect.EMPTINESS,
                        new ArgumentRef.TheClosure()));
        assertTrue(empty.getMessage().contains("holds nothing for an argument to decide"),
                empty.getMessage());
    }

    /** And the ones the library really does state, which bind. */
    @Test
    void theOnesTheLibraryStatesBind() {
        assertDoesNotThrow(() -> bindTurnsOn("List.filter",
                AnswerAspect.EMPTINESS,
                new ArgumentRef.TheClosure()));
        assertDoesNotThrow(() -> bindTurnsOn("List.any",
                AnswerAspect.TRUTH,
                new ArgumentRef.TheClosure()));
        assertDoesNotThrow(() -> bindTurnsOn("Bool.not",
                AnswerAspect.TRUTH, new ArgumentRef.At(0)));
    }

    @Test
    void anArgumentTheDeclarationDoesNotHave() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> bindCarried("String.contains", new ArgumentRef.At(7)));
        assertTrue(e.getMessage().contains("String.contains takes 2 argument(s)"), e.getMessage());
    }

    @Test
    void anArgumentThatIsNotWhatTheRuleIsAbout() {
        // `String.contains(needle, haystack)` reads a string, and a shape says what became of a
        // container's elements — of a string this names only its length.
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> bindCarried("String.contains", new ArgumentRef.At(1)));
        assertTrue(e.getMessage().contains("is String, not a container"), e.getMessage());
        assertTrue(e.getMessage().contains("named as the container a predicate reads"),
                e.getMessage());
    }

    @Test
    void aPartOfSomethingTheSignatureSaysItDoesNotHand() {
        // `List.contains(value, xs)` applies no closure, so there is no container it hands one.
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> bindCarried("List.contains", new ArgumentRef.TheContainer()));
        assertTrue(e.getMessage().contains("hands one nothing a container holds"), e.getMessage());
    }

    @Test
    void anOperationTheLibraryDoesNotDeclare() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> bindCarried("List.containsTwice", new ArgumentRef.At(1)));
        assertTrue(e.getMessage().contains("which the library does not declare"), e.getMessage());
    }

    /**
     * A position written where the signature already answers is two answers to one question, and two
     * answers are what come apart later — which is what {@code List.all} had, its container written in
     * one table and derived in another.
     */
    @Test
    void aPositionTheSignatureAlreadyAnswers() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> bindBuilt("List.filter", new ArgumentRef.At(1)));
        assertTrue(e.getMessage().contains("writes the argument its signature already answers"),
                e.getMessage());
    }

    /** And the rules the library actually has: every row of every table, on the first ask. */
    @Test
    void theRulesTheCheckShipsWith() {
        assertDoesNotThrow(() -> {
            DischargeRules.builtOperations();
            DischargeRules.carryingOperations();
            DischargeRules.projections();
        });
    }

    /** The binding reads what the declaration says, so a rule it agrees with binds. */
    @Test
    void aRuleThatAgreesWithTheDeclaration() {
        assertDoesNotThrow(() -> OperationFactBinder.holdToTheDeclaration(declared("List.reverse"),
                new ArgumentRef.At(0), new ArgumentRef.TheContainer(),
                TypeRequirement.CONTAINER, "the container something is built from"));
    }
}
