package souther.lsp.analysis;

import org.junit.jupiter.api.Test;
import souther.compiler.cst.LineIndex;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Abandonment;
import souther.compiler.sites.Evidence;
import souther.compiler.sites.MemberReceiver;
import souther.compiler.sites.SemanticSnapshot;
import souther.compiler.sites.TypeFact;
import souther.compiler.source.SourceId;
import souther.compiler.types.Type;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The parser reads {@code m.name} and {@code x.field} the same way, so what may be written after a
 * {@code .} is not a question the syntax answers. It is answered on the source as it stands
 * mid-edit, and the answer says which of the two the author is looking at.
 *
 * <p>The two are asked together because they pull the probe's rule in opposite directions. What may
 * be read off a repaired source is what was written before anything was put in, and the access
 * around the cursor never is: it runs to the end of the name the probe supplied. The receiver does —
 * a value's occurrence stops at the {@code .}, and so does a qualifier. So the rule is put to where
 * the receiver is written, which is what {@link MemberReceiver#writtenAt} carries.
 */
class WhatIsLeftOfADotIsSaidByTheSnapshotAndNotGuessedTest {

    private static final String MODEL_URI = "file:///m.sou";
    private static final String LIB_URI = "file:///lib.sou";

    private static final String LIB = """
            module lib exposing ( Cost )

            data Cost = { value: Int }
            """;

    private static String model(String body) {
        return """
                module m

                import lib as l ( Cost )

                data Draft = { plannedCost: Cost }

                behavior submit : (request: Draft) -> Int
                let submit (request) = \
                """ + body;
    }

    /**
     * The line the issue is about, answered from declarations alone.
     *
     * <p>Two steps, and neither is inference. What arrives as {@code request} is on the
     * {@code behavior} line; what {@code plannedCost} is is in the {@code data} declaration — in
     * another module, which is where a field list is least guessable and most wanted. Nothing here
     * needs the body to have compiled, which is the point: it has not, and cannot, while the line
     * ends at a {@code .}.
     */
    @Test
    void aFieldTakenOffAParameterIsTheTypeTheDeclarationsSay() {
        MemberReceiver receiver = leftOfTheDot(model("request.plannedCost.\n"));

        TypeFact fact = assertInstanceOf(MemberReceiver.Value.class, receiver).type();
        assertEquals("Cost",
                assertInstanceOf(Type.Ref.class, fact.type()).name().name(),
                "`request.plannedCost` is a `Cost`, said by a signature and a data declaration");
        assertInstanceOf(Evidence.Declared.class, fact.evidence(),
                "and said by declarations, which is what a reader is entitled to know");
    }

    /**
     * And a behavior that is handed what it depends on has its parameters spoken for like any
     * other's.
     *
     * <p>Such a {@code let} takes the behaviors it is injected with beside its inputs, so it always
     * writes more parameters than the signature has input types. Told apart by comparing those two
     * lengths, none of its parameters was spoken for at all — so an author working inside any
     * behavior that depends on anything was shown nothing after a {@code .}, while the same line one
     * behavior up answered.
     */
    @Test
    void aParameterOfABehaviorThatIsHandedWhatItDependsOnIsSpokenForToo() {
        MemberReceiver receiver = leftOfTheDot("""
                module m

                import lib as l ( Cost )

                data Draft = { plannedCost: Cost }

                behavior price : (draft: Draft) -> Int

                behavior submit : (request: Draft) -> Int
                    depends on price

                let submit (request, price) = request.plannedCost.
                """);

        assertEquals("Cost",
                assertInstanceOf(Type.Ref.class,
                        assertInstanceOf(MemberReceiver.Value.class, receiver).type().type())
                        .name().name(),
                "the signature says what `request` is whatever else the `let` was handed");
    }

    /**
     * And an injected parameter is a value the declarations type, not one they are silent about.
     *
     * <p>The signature above the {@code let} says nothing about it — it is not an input. What it is
     * is the behavior the {@code depends on} clause names, and that behavior's own signature says
     * what it takes and answers. So the answer here is a typed value that offers no names after the
     * {@code .}, which is a different thing to say than that nothing states what it is: a behavior
     * is not a record, and an author writing a {@code .} on one is writing on something the
     * declarations do account for.
     */
    @Test
    void anInjectedParameterIsTypedAsTheBehaviorItNames() {
        Probed probed = probe("""
                module m

                import lib as l ( Cost )

                data Draft = { plannedCost: Cost }

                behavior price : (draft: Draft) -> Cost

                behavior submit : (request: Draft) -> Cost
                    depends on price

                let submit (request, price) = price.
                """);

        Type.FnOf takes = assertInstanceOf(Type.FnOf.class,
                assertInstanceOf(MemberReceiver.Value.class, probed.receiver()).type().type());
        assertEquals("Draft",
                assertInstanceOf(Type.Ref.class, takes.params().getFirst()).name().name(),
                "what `price` takes");
        assertEquals("Cost", assertInstanceOf(Type.Ref.class, takes.result()).name().name(),
                "and what it answers");
        assertTrue(probed.snapshot().fieldsOf(
                        ((MemberReceiver.Value) probed.receiver()).type()).isEmpty(),
                "a behavior carries no field for a `.` to name");
    }

    /**
     * A call of a helper is what the helper's body states, read at what it was applied to.
     *
     * <p>The helper declares nothing about its answer — a Souther helper never does — so what states
     * the type is its body, read with its parameter standing for what arrived. That is the step the
     * expansion below takes at every call, and until it was taken here a body that called something
     * and took a field off the answer was unanswered from the {@code .} onwards.
     */
    @Test
    void aCallOfAHelperIsWhatItsBodyStatesAtWhatItWasAppliedTo() {
        MemberReceiver receiver = leftOfTheDot("""
                module m

                import lib as l ( Cost )

                data Draft = { plannedCost: Cost }

                behavior submit : (request: Draft) -> Int
                let costOf (d) = d.plannedCost
                let submit (request) = costOf(request).
                """);

        assertEquals("Cost",
                assertInstanceOf(Type.Ref.class,
                        assertInstanceOf(MemberReceiver.Value.class, receiver).type().type())
                        .name().name(),
                "`costOf(request)` is what its body takes off a `Draft`");
    }

    /**
     * And a call of the behavior an implementation was handed is what that behavior answers.
     *
     * <p>The name itself was already a value the declarations spoke for — it arrives as what the
     * behavior takes and answers — and nothing could spend that type on a call, so {@code price(x)}
     * was unanswered while {@code price} alone was not.
     */
    @Test
    void aCallOfAnInjectedBehaviorIsWhatThatBehaviorAnswers() {
        MemberReceiver receiver = leftOfTheDot("""
                module m

                import lib as l ( Cost )

                data Draft = { plannedCost: Cost }

                behavior price : (draft: Draft) -> Cost

                behavior submit : (request: Draft) -> Int
                    depends on price
                let submit (request, price) = price(request).
                """);

        assertEquals("Cost",
                assertInstanceOf(Type.Ref.class,
                        assertInstanceOf(MemberReceiver.Value.class, receiver).type().type())
                        .name().name(),
                "what the injected behavior answers is what the call is");
    }

    /**
     * A call of a behavior is what that behavior's signature answers.
     *
     * <p>Which is a declaration, and the one the author wrote a line above. Read as a value nothing
     * speaks for, everything from the {@code .} onwards was unanswered in a body that calls
     * anything, however completely the declarations settled both halves of it.
     */
    @Test
    void aCallOfABehaviorIsWhatItsSignatureAnswers() {
        MemberReceiver receiver = leftOfTheDot("""
                module m

                import lib as l ( Cost )

                data Draft = { plannedCost: Cost }

                behavior make : () -> Draft
                behavior submit : (request: Draft) -> Cost
                let submit (request) = make().
                """);

        assertEquals("Draft",
                assertInstanceOf(Type.Ref.class,
                        assertInstanceOf(MemberReceiver.Value.class, receiver).type().type())
                        .name().name(),
                "`make()` answers what `behavior make` says it answers");
    }

    /**
     * And a receiver the declarations really do say nothing about is still a value.
     *
     * <p>What is missing there is the type and not the receiver, and the two are different answers:
     * a reader told the second knows the author is not writing a {@code .} on anything. A helper
     * answering a fork is one such receiver — what a fork answers is the join of its arms, which is
     * the elaboration's and not a declaration.
     */
    @Test
    void aReceiverNoDeclarationSpeaksForIsStillAValue() {
        MemberReceiver receiver = leftOfTheDot("""
                module m

                data Draft = { plannedCost: Int }

                let larger (a, b) = if a.plannedCost > b.plannedCost then a else b

                behavior submit : (request: Draft) -> Int
                let submit (request) = larger(request, request).
                """);

        assertInstanceOf(MemberReceiver.UntypedValue.class, receiver,
                "the helper answers a fork, and no declaration read here says what that is");
    }

    @Test
    void anAliasIsAnsweredAsTheModuleItNames() {
        MemberReceiver receiver = leftOfTheDot(model("l.\n"));

        assertEquals(new MemberReceiver.Namespace.OfModule("lib", receiver.writtenAt()), receiver,
                "the alias is resolved here, so nothing downstream has to resolve `l` again");
    }

    @Test
    void aCursorOnNoAccessIsToldSo() {
        String text = model("request\n");
        Probed probed = probe(model("request.plannedCost.\n"));
        LineIndex lines = new LineIndex(text, new SourceId(MODEL_URI));

        assertTrue(probed.snapshot().memberReceiverAround(lines.posOf(0)).isEmpty(),
                "the first character of `module m` is in no field read");
    }

    /**
     * And what every answer here rests on is source the author wrote.
     *
     * <p>The other half — that the access around it is not — is what
     * {@code WhatIsAskedOfAHalfWrittenLineIsAskedOfWhatItSaysNowTest} holds, on the access node
     * itself. What is checked here is that the receiver an editor is handed clears the rule, which
     * is the thing that makes the receiver usable and the access not.
     */
    @Test
    void whatTheAnswerRestsOnIsSourceTheAuthorWrote() {
        for (String body : new String[]{"request.plannedCost.\n", "l.\n"}) {
            Probed probed = probe(model(body));
            assertTrue(probed.reading().mayBeRead(probed.receiver().writtenAt()),
                    "the receiver of `" + body.strip() + "` stops at the `.`, so it may be read");
        }
    }

    private record Probed(SemanticProbe.Reading reading, SemanticSnapshot snapshot,
                          MemberReceiver receiver) {}

    private static MemberReceiver leftOfTheDot(String text) {
        return probe(text).receiver();
    }

    /** The buffer with its half-written line finished off, and what the snapshot says about the
     *  access the cursor is in — which is the question an editor puts, cursor and all. */
    private static Probed probe(String text) {
        Map<String, String> joining = new LinkedHashMap<>();
        joining.put(LIB_URI, LIB);
        int cursor = text.lastIndexOf(".\n") + 1;
        SemanticProbe.Reading reading = new SemanticProbe().of(joining, Set.of(), ModulePath.EMPTY,
                MODEL_URI, text, cursor, Abandonment.NEVER);
        if (reading == null) {
            throw new AssertionError("the half-written line is one the probe finishes off");
        }
        SemanticSnapshot snapshot = SemanticSnapshot.of(reading.compilation().db(), "m")
                .orElseThrow(() -> new AssertionError("the repaired source has a snapshot"));
        LineIndex lines = new LineIndex(text, new SourceId(MODEL_URI));
        MemberReceiver receiver = snapshot.memberReceiverAround(lines.posOf(cursor))
                .orElseThrow(() -> new AssertionError("nothing is written at the cursor"));
        return new Probed(reading, snapshot, receiver);
    }
}
