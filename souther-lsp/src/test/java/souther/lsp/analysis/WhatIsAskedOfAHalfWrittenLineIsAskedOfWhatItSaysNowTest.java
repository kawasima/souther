package souther.lsp.analysis;

import org.junit.jupiter.api.Test;
import souther.compiler.ast.Hir;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Abandonment;
import souther.compiler.query.Names;
import souther.compiler.sites.MemberReceiver;
import souther.compiler.sites.SemanticSnapshot;
import souther.lsp.analysis.SemanticProbe.Reading;
import souther.lsp.analysis.SemanticProbe.Repair;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A field list is wanted at {@code request.plannedCost.}, which is not a document that parses, so
 * the compiler is told nothing about the line it is being asked about. What answers is the line as
 * it stands now, finished off — never the last line that compiled, whose type would be a wrong
 * answer wearing the shape of a right one.
 */
class WhatIsAskedOfAHalfWrittenLineIsAskedOfWhatItSaysNowTest {

    private static final String URI = "file:///m.sou";

    private static final String HEAD = """
            module m

            data Cost = { value: Int }
            data Draft = { plannedCost: Cost }

            behavior submit : (request: Draft) -> Int
            let submit (request) = \
            """;

    /** The buffer mid-edit: a `.` with nothing after it yet. */
    private static final String HALF_WRITTEN = HEAD + "request.plannedCost.\n";

    @Test
    void aDocumentThatParsesIsNotRepaired() {
        String whole = HEAD + "request.plannedCost.value\n";
        assertNull(SemanticProbe.repair(whole, whole.length()),
                "there is nothing here the author has not finished");
    }

    @Test
    void aTrailingDotIsFinishedOffAndTheSourceBeforeItIsUntouched() {
        int cursor = HALF_WRITTEN.indexOf(".\n") + 1;
        Repair repair = SemanticProbe.repair(HALF_WRITTEN, cursor);

        assertNotNull(repair, "a `.` with nothing after it is what this is for");
        assertEquals(cursor, repair.firstInserted(), "the insertion begins at the cursor");
        assertEquals(HALF_WRITTEN.substring(0, cursor),
                repair.text().substring(0, repair.firstInserted()),
                "and everything before it is the source, character for character");
        assertTrue(repair.text().startsWith(HALF_WRITTEN.substring(0, cursor)),
                "nothing was deleted or replaced");
    }

    @Test
    void anUnclosedCallIsClosedAtTheEnd() {
        String calling = HEAD + "cost(request\n";
        Repair repair = SemanticProbe.repair(calling, calling.length());

        assertNotNull(repair, "a call whose bracket has not been typed yet");
        assertEquals(calling.length(), repair.firstInserted(),
                "the bracket goes at the end, so every offset in the source is where it was");
    }

    @Test
    void whatMayBeReadStopsWhereTheInsertionBegins() {
        Reading reading = probed(HALF_WRITTEN);
        Hir.FieldAccess written = accessOf(reading);

        assertTrue(reading.mayBeRead(written.target().region()),
                "`request.plannedCost` is the author's, and it is what a field list is taken from");
        assertFalse(reading.mayBeRead(written.region()),
                "and the access around it takes in the name the probe put there");
    }

    @Test
    void theReceiverIsReadFromTheTextThatIsThereNow() {
        String head = """
                module m

                data Address = { city: Text }
                data Amount = { units: Int }
                data Customer = { address: Address, amount: Amount }

                behavior f : (customer: Customer) -> Int
                let f (customer) = \
                """;
        // One probe across both revisions, which is what an editor has. A fresh one for each would
        // answer from a store that never held the earlier reading, and the danger this is about is a
        // store that did.
        SemanticProbe probe = new SemanticProbe();

        assertEquals(List.of("city"), fieldsAfterTheDot(probe, head + "customer.address.\n"),
                "an `Address` has a city");
        assertEquals(List.of("units"), fieldsAfterTheDot(probe, head + "customer.amount.\n"),
                "and the fields offered after the edit are the ones the new receiver has");
    }

    /**
     * What may be written after the {@code .}, taken through one probe.
     *
     * <p>The member list and not the spelling of the receiver: what an author is shown comes through
     * the snapshot and the reading of the declarations, and a receiver read correctly whose fields
     * came from the revision before would be the same wrong answer one step further along.
     */
    private static List<String> fieldsAfterTheDot(SemanticProbe probe, String text) {
        Reading reading = reading(probe, text);
        SemanticSnapshot snapshot = SemanticSnapshot.of(reading.compilation().db(), "m")
                .orElseThrow(() -> new AssertionError("the repaired source has a snapshot"));
        int cursor = text.lastIndexOf(".\n") + 1;
        // The reading's own layout, which is of the text it compiled and not of the buffer.
        MemberReceiver receiver = snapshot
                .memberReceiverAround(reading.placeAt(cursor))
                .orElseThrow(() -> new AssertionError("nothing is written at the cursor"));
        return List.copyOf(snapshot
                .fieldsOf(assertInstanceOf(MemberReceiver.Value.class, receiver).type()).keySet());
    }

    /** The probe over a workspace of one document, with the buffer as it stands. */
    private static Reading probed(String text) {
        return reading(new SemanticProbe(), text);
    }

    /** The same, through a probe the caller keeps — which is what the server has. */
    private static Reading reading(SemanticProbe probe, String text) {
        Map<String, String> joining = new LinkedHashMap<>();
        Reading reading = probe.of(joining, Set.of(), ModulePath.EMPTY, URI, text,
                text.indexOf(".\n") < 0 ? text.length() : text.lastIndexOf(".\n") + 1,
                Abandonment.NEVER);
        assertNotNull(reading, "the half-written line is one this knows how to finish");
        return reading;
    }

    /** The `let`'s body, which every source here writes as one field taken off another. */
    private static Hir.FieldAccess accessOf(Reading reading) {
        Hir.Module module = reading.compilation().db()
                .ask(new Names.Resolved("m")).value();
        assertNotNull(module, "the repaired source resolves");
        for (Hir.FnDef fn : module.fns()) {
            if (fn.body() instanceof Hir.FnBody.Written(Hir.Expr expr)
                    && expr instanceof Hir.FieldAccess access) {
                return access;
            }
        }
        throw new AssertionError("the module has no `let` whose body is a field access");
    }

}
