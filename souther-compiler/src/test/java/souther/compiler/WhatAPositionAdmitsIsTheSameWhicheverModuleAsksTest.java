package souther.compiler;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import souther.compiler.meta.ModulePath;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * What a declaration's rules say about a position is settled by the declaration, not by the module
 * that asks (spec §invariant-discharge-representation).
 *
 * <p>A clause naming anything else its module defines — a {@code let} whose value it composes into
 * a pattern, a helper it calls — used to be read from whatever tree the asking module happened to
 * hold. The declaring module held the expanded one and every importer held the written one, so one
 * declaration said two things about the values it admits and which a reader got depended on who
 * was asking. A report comparing two modules had no way to tell that from the declarations
 * differing.
 *
 * <p><b>What is held here is agreement, not readability.</b> The two modules answer alike about
 * every declaration, and that is the whole claim. A clause whose expanded form this compiler cannot
 * take apart still cannot be taken apart — it is now unreadable from both, where it used to be
 * unread from one and unreadable from the other. Asking for it to become readable would be asking
 * this to close a second thing: how far a reading gets into an expanded helper is a limit of the
 * reading, and is not decided by where anyone was standing.
 *
 * <p>The literal clause is the control. Its written form and its expanded form are the same tree,
 * so it read alike from both modules before any of this and reads alike now; a run where it moved
 * would be a run where something other than the representation changed.
 */
class WhatAPositionAdmitsIsTheSameWhicheverModuleAsksTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /**
     * The declaring module: four declarations, one per way a clause can reach its rule, and a
     * behavior that takes each so the reading of that position is measured.
     */
    private static final String DECLARING = """
            module owner exposing (
                OutOfALet, OutOfAHelper, Spread, Literal, Held, Yes, No,
                fromALet, fromAHelper, fromASpread, fromALiteral
            )

            // The clause composes a value this module defines, which no importer has a name for.
            let tail = "[0-9]{3}"

            data OutOfALet = String
                invariant String.matches("00" ++ tail, value)

            // The clause calls a helper this module defines, which no importer has a name for.
            let atLeastOne (s: String) = String.length(s) >= 1

            data OutOfAHelper = String
                invariant atLeastOne(value)

            // A clause reaching a declaration through a spread is the spread type's, and travels the
            // same way. It names a value of this module too, or its written form and its expanded
            // form would be one tree and the spread would be a second literal control.
            let floor = 1

            data Held = { n: Int }
                invariant n >= floor

            data Spread = { ...Held, m: Int }

            // The control: written out, so its two forms are one tree.
            data Literal = String
                invariant String.matches("00[0-9]{3}", value)

            data Yes
            data No

            behavior fromALet : (i: OutOfALet) -> Yes | No
            let fromALet (i) = Yes

            example fromALet
                | "one" : (OutOfALet("00123")) -> Yes

            behavior fromAHelper : (i: OutOfAHelper) -> Yes | No
            let fromAHelper (i) = Yes

            example fromAHelper
                | "one" : (OutOfAHelper("a")) -> Yes

            behavior fromASpread : (i: Spread) -> Yes | No
            let fromASpread (i) = Yes

            example fromASpread
                | "one" : (Spread { n = 1, m = 2 }) -> Yes

            behavior fromALiteral : (i: Literal) -> Yes | No
            let fromALiteral (i) = Yes

            example fromALiteral
                | "one" : (Literal("00123")) -> Yes
            """;

    /** The importing module: the same four behaviors over the same four declarations. */
    private static final String IMPORTING = """
            module importer exposing (fromALet, fromAHelper, fromASpread, fromALiteral)

            import owner ( OutOfALet, OutOfAHelper, Spread, Literal, Yes, No )

            behavior fromALet : (i: OutOfALet) -> Yes | No
            let fromALet (i) = Yes

            example fromALet
                | "one" : (OutOfALet("00123")) -> Yes

            behavior fromAHelper : (i: OutOfAHelper) -> Yes | No
            let fromAHelper (i) = Yes

            example fromAHelper
                | "one" : (OutOfAHelper("a")) -> Yes

            behavior fromASpread : (i: Spread) -> Yes | No
            let fromASpread (i) = Yes

            example fromASpread
                | "one" : (Spread { n = 1, m = 2 }) -> Yes

            behavior fromALiteral : (i: Literal) -> Yes | No
            let fromALiteral (i) = Yes

            example fromALiteral
                | "one" : (Literal("00123")) -> Yes
            """;

    /**
     * How far the rules about the behavior's one position were read, by module and behavior.
     *
     * <p>Read off the measure rather than off the sentence a person sees. What is compared has to be
     * what the reading came to, and the words around it are written from that; held to the words,
     * this would fail the day one of them was rephrased and pass the day two different readings were
     * described alike.
     */
    private static Map<String, JsonNode> howFarTheRulesWereRead() {
        Compilation compilation =
                Compilation.ofSources(List.of(DECLARING, IMPORTING), ModulePath.EMPTY);
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        JsonNode document = JSON.readTree(
                AdequacyReport.of(compilation).json(SourceRendering.namedByIdentity(SourceLayouts.NONE)));
        Map<String, JsonNode> out = new LinkedHashMap<>();
        for (JsonNode module : document.get("modules")) {
            for (JsonNode behavior : module.path("behaviors")) {
                out.put(module.path("module").asString() + "/" + behavior.path("name").asString(),
                        behavior.path("partition").path("axesMeasure"));
            }
        }
        return out;
    }

    /**
     * Every one of the four is answered alike from the module that declares it and from the one
     * that imports it.
     *
     * <p>All four in one assertion, because what is claimed is about the four together: a fix that
     * moved one of them and left another is the arrangement this replaces, and read one at a time
     * the run would say which one and not that they disagree.
     */
    @Test
    void aDeclarationIsReadTheSameFromBothModules() {
        Map<String, JsonNode> read = howFarTheRulesWereRead();
        Map<String, String> disagreed = new LinkedHashMap<>();
        for (String behavior
                : List.of("fromALet", "fromAHelper", "fromASpread", "fromALiteral")) {
            JsonNode owner = read.get("owner/" + behavior);
            JsonNode importer = read.get("importer/" + behavior);
            assertNotNull(owner, "the declaring module measures " + behavior);
            assertNotNull(importer, "and the importing one does too");
            if (!owner.equals(importer)) {
                disagreed.put(behavior, owner + " from owner, " + importer + " from importer");
            }
        }
        assertEquals(Map.of(), disagreed,
                "a declaration says one thing about the values it admits, and which of them a"
                        + " reader gets is not decided by who is asking");
    }

    /**
     * And the one whose expanded form this compiler does read is read to the end from both.
     *
     * <p>The anchor the agreement above needs. Two modules that both failed to read a rule agree
     * about it perfectly, so agreement alone is satisfied by the defect having been made worse. This
     * says that the {@code let}-derived clause is read as far as the literal one — which is what the
     * declaring module always got, and what an importer now gets too.
     */
    @Test
    void theClauseWrittenOutOfALetIsReadAsFarAsTheLiteralOne() {
        Map<String, JsonNode> read = howFarTheRulesWereRead();
        assertEquals(read.get("owner/fromALiteral"), read.get("importer/fromALet"),
                "a pattern composed from a `let` admits what a pattern written out admits, asked"
                        + " from a module that never had a name for the `let`");
    }

    /**
     * The helper's clause is read as far as the literal one, from either module.
     *
     * <p>Which is the other half of what this file holds, and it moved. A clause stating its rule
     * through a helper reaches the reading as that rule under a binding, and the reading goes inside
     * the binding now (ADR-0106) — so the outcome is the literal's, and agreeing across modules is
     * agreeing about something read rather than about a limit met twice.
     */
    @Test
    void theClauseCallingAHelperIsReadAsFarAsTheLiteralOne() {
        Map<String, JsonNode> read = howFarTheRulesWereRead();
        assertEquals(read.get("owner/fromAHelper"), read.get("importer/fromAHelper"),
                "the reading gets as far from one module as from the other");
        assertEquals(read.get("owner/fromALiteral"), read.get("owner/fromAHelper"),
                "and as far as it gets into the same rule written out, which is what says the"
                        + " agreement above is about a rule that was read");
    }
}
