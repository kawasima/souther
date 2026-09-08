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

    @Test
    void aReceiverNoDeclarationSpeaksForIsStillAValue() {
        // `submitted()` answers something no declaration read here states, so what is missing is the
        // type and not the receiver.
        MemberReceiver receiver = leftOfTheDot("""
                module m

                data Draft = { plannedCost: Int }

                behavior make : () -> Draft
                behavior submit : (request: Draft) -> Int
                let submit (request) = make().
                """);

        assertInstanceOf(MemberReceiver.UntypedValue.class, receiver,
                "a call's answer is a value, and no declaration read here says what it is");
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
