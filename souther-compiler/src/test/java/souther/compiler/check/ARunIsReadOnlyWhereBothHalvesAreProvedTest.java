package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.core.Core;
import souther.compiler.core.GrowingFold;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.types.BindingId;
import souther.compiler.types.ValueName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A number over a run of values is read only where both halves of it were proved, and the two are
 * proved by different owners.
 *
 * <p>One half is about the operation: a walk answers exactly one closure result per element of what
 * it was given. The other is about the closure: what it answered is a place inside the element it
 * was applied to. Either alone licenses nothing, and this holds each of them by a model that has one
 * and not the other.
 *
 * <p>The join between them is a binding, so it is held too: what the rewritten walk hands out has to
 * be the binding the projection was recorded against, or the lookup finds somebody else's proof or
 * none.
 */
class ARunIsReadOnlyWhereBothHalvesAreProvedTest {

    private static final String MODEL = """
            module example.claims

            data Amount = Int
                invariant value >= 0

            data Item = { amount: Amount, free: Bool }

            data Needed
            data NotNeeded
            data Verdict = Needed | NotNeeded

            behavior projected : (lines: List<Item>) -> Verdict
            let projected (lines) =
                if List.sum(List.map(line -> line.amount.value, lines)) >= 100000
                    then Needed else NotNeeded

            behavior branching : (lines: List<Item>) -> Verdict
            let branching (lines) =
                if List.sum(List.map(line -> if line.free then 0 else line.amount.value, lines))
                        >= 100000 then Needed else NotNeeded

            behavior kept : (lines: List<Item>) -> Verdict
            let kept (lines) =
                if List.length(List.filter(line -> line.free, lines)) >= 3
                    then Needed else NotNeeded

            let amountOf (line: Item): Amount = line.amount

            behavior throughAHelperOnTheElement : (lines: List<Item>) -> Verdict
            let throughAHelperOnTheElement (lines) =
                if List.sum(List.map(line -> amountOf(line).value, lines)) >= 100000
                    then Needed else NotNeeded
            """;

    /**
     * The operation says one answer per element, so the closure's parameter is written down beside
     * the container it walks.
     */
    @Test
    void aWalkThatAnswersOnePerElementIsWrittenDown() {
        assertEquals(1, elements("projected").provenance().projectedFrom().size(),
                "a `map` answers what the closure made of each element and as many as it was"
                        + " given, so the pair is recorded");
    }

    /**
     * And it is written down whatever the closure turns out to be, because it is a fact about the
     * operation. What the closure came to is the other half.
     */
    @Test
    void theOperationsHalfDoesNotDependOnWhatTheClosureIs() {
        assertEquals(1, elements("branching").provenance().projectedFrom().size(),
                "a `map` whose closure branches is still a `map`, and the licence is the same one");
    }

    /**
     * A walk that keeps some of what it was given is not one of these, and nothing is written down.
     * Read off the four words a construction is described by, {@code List.filter} and
     * {@code List.map} would be told apart by their size alone.
     */
    @Test
    void aWalkThatKeepsSomeOfWhatItWasGivenIsNotWrittenDown() {
        assertTrue(elements("kept").provenance().projectedFrom().isEmpty(),
                "a `filter` answers the elements it was given and no more than it was given, so"
                        + " there is no one-per-element answer for a run to be over");
    }

    /**
     * The operation's half is both declared statements and not either of them.
     *
     * <p>Asked of the declarations rather than through a model, because the language has no model
     * that reaches it: nothing sums what a {@code Set.map} answered, since a sum takes a list. So
     * the half that would be untested through a body is tested here, where it is stated.
     *
     * <p>{@code Set.map} is the one that separates them. Its elements are what the closure made —
     * the same lineage a {@code List.map}'s are — and there are no more of them than it was given,
     * because two elements may answer one. A run over that is a run over some of the answers, and a
     * total of it is not a total of what the closure made of the values a row wrote.
     */
    @Test
    void bothStatementsAreAskedAndNeitherAlone() {
        assertNotNull(mapsEachElementOf("List", "map"),
                "a mapping answers what the closure made and as many as it was given");
        assertNull(mapsEachElementOf("Set", "map"),
                "a mapping into a set answers what the closure made and no more than it was given,"
                        + " which is not one per element");
        assertNull(mapsEachElementOf("List", "filter"),
                "a filter answers the elements it was given rather than what a closure made");
    }

    /** The argument the operation answers one closure result per element of, read off the bound
     *  building — which every operation asked here has, so a null is the projection's answer. */
    private static DeclaredArgument mapsEachElementOf(String module, String operation) {
        return DefaultBoundOperationFacts.get()
                .buildsItsResultFrom(ValueName.Stdlib.operation(module, operation))
                .mapsEachElementOf();
    }

    /** With both halves, the closure's answer is kept as the way from the element to it. */
    @Test
    void theClosuresAnswerIsKeptAsTheWayThere() {
        ElementBindings elements = elements("projected");
        BindingId element = elements.projected().keySet().iterator().next();
        assertEquals("amount", elements.projectionAt(element).toString(),
                "the value each element answered stands one field in, and the expression it was"
                        + " read from is not kept");
    }

    /**
     * A closure that reaches the place through a helper answers the same place.
     *
     * <p>What a helper leaves behind once it is spliced in is a binding holding the element and a
     * field of that binding, and the way there is the same way. Read without following the binding,
     * a model that names its projection — which is how a model of any size writes one — would have
     * been the shape this could not follow, and the rule about the total would go unread for a
     * reason that is about the spelling rather than about the model.
     */
    @Test
    void aClosureThatReachesThePlaceThroughAHelperAnswersTheSamePlace() {
        ElementBindings elements = elements("throughAHelperOnTheElement");
        assertEquals(1, elements.projected().size(),
                "a helper applied to the element is a binding holding it and a field of that");
        assertEquals("amount",
                elements.projectionAt(elements.projected().keySet().iterator().next()).toString());
    }

    /**
     * With the operation's half and not the closure's, nothing is kept — which is what says a rule
     * about what the walk answered is not a rule about any position.
     */
    @Test
    void aClosureThatBranchesLeavesNoWayThere() {
        assertTrue(elements("branching").projected().isEmpty(),
                "one element answers a field and another answers nought, and neither is where the"
                        + " answer stands in the element");
    }

    /**
     * The binding the rewritten walk hands out is the one the projection was recorded against.
     *
     * <p>The join between the two proofs. The walk is rewritten after the fact is written down, and
     * nothing renames a binding on the way — so what a reader recovers from the emitted walk is an
     * address into what was proved and not a name that happens to look alike. A rewrite that lost
     * it would leave the lookup finding nothing, which costs a reading and states nothing false;
     * one that reused it for another element would state something false, and this is what says it
     * does not.
     */
    @Test
    void theWalkHandsOutTheBindingTheProofWasRecordedAgainst() {
        Bodies.CheckedBody checked = body("projected");
        BindingId handed = GrowingFold.elementBindingOf(argumentOfSum(checked.body()));
        assertNotNull(handed, "the emitted walk says which binding an element arrives under");
        assertEquals(checked.elements().projected().keySet().iterator().next(), handed,
                "and it is the one the closure's answer was recorded against");
    }

    /**
     * The projection is hung on the element of the container the licence names, and on no other.
     *
     * <p>The third of the three that have to agree, and the one that is not a reading but an
     * identity: the licence was proved of one walk, and the element it is put on has to be an
     * element of that walk's container. Asked only whether a licence exists, a body with two walks
     * in it would take whichever binding the parameter happened to be bound to — and a rule about
     * one sequence's total would be measured against another sequence. That is not a reading lost
     * but a false one.
     *
     * <p>Held here rather than through a model, because no model reaches it: every expansion wires
     * the two ends of a licence together, so there is no source text that crosses them. What can be
     * asked of a real body is the agreement itself, over the bindings that body really has.
     */
    @Test
    void theProjectionGoesOnlyOnAnElementOfTheContainerTheLicenceNames() {
        ElementBindings elements = elements("projected");
        BindingId parameter = elements.provenance().projectedFrom().keySet().iterator().next();
        BindingId source = elements.provenance().projectedFrom(parameter);
        BindingId element = elements.projected().keySet().iterator().next();
        Core container = elements.containersOf(element).get(0);

        assertTrue(ElementBindings.readsWhatIsHeldBy(container, source, elements.held()),
                "the element the projection was hung on is an element of the container the licence"
                        + " names, through however many names stand between them");
        assertFalse(ElementBindings.readsWhatIsHeldBy(container, element, elements.held()),
                "and a licence naming some other binding is not that agreement: an element is no"
                        + " container of itself, and a walk is not licensed by another's proof");
        assertFalse(ElementBindings.readsWhatIsHeldBy(container, null, elements.held()),
                "no licence, no agreement — a run needs the source to be named, not merely absent");
    }

    /** A walk nothing proved anything about answers no projection, however it is shaped. */
    @Test
    void aWalkWithNoProofBehindItAnswersNothing() {
        Bodies.CheckedBody checked = body("kept");
        BindingId handed = GrowingFold.elementBindingOf(argumentOfLength(checked.body()));
        assertNull(handed == null ? null : checked.elements().projectionAt(handed),
                "the lookup is into what was proved, and finding a binding proves nothing");
    }

    private static ElementBindings elements(String behavior) {
        return body(behavior).elements();
    }

    private static Bodies.CheckedBody body(String behavior) {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.answerEverything();
        return compilation.db().ask(new Bodies.CheckedBehavior(
                compilation.modules().get(0), behavior)).value();
    }

    private static Core argumentOfSum(Core e) {
        return argumentOf(e, "sum");
    }

    private static Core argumentOfLength(Core e) {
        return argumentOf(e, "length");
    }

    /** The one argument of the call to that operation, found by walking the tree. */
    private static Core argumentOf(Core e, String operation) {
        if (e instanceof Core.Call call && call.fn() instanceof Core.Reached reached
                && reached.toString().contains(operation) && call.args().size() == 1) {
            return call.args().get(0);
        }
        Core[] found = {null};
        Core.forEachChild(e, child -> {
            if (found[0] == null) {
                found[0] = argumentOf(child, operation);
            }
        });
        return found[0];
    }
}
