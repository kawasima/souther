package souther.compiler.query;

import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.report.AdequacyReport;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A reading that could not hold what it read together says so in the document, under its own word.
 *
 * <p>Issue #877, at the surface a consumer reads. What is owed there is not that something went
 * unread — every rule about the position arrived and every one was taken in — but that the values
 * the document prints for the position may be wider than the rules leave it, so a class made out of
 * them may be one no value can be in.
 *
 * <p>Under its own {@code kind} and not among what a reading did not read. Told apart there, a
 * consumer grouping by kind counts a limit an author can go looking for a clause behind, and there
 * is no clause: the two would be one number and the sentence beside it would be false of half of
 * them.
 */
class AProductWiderThanItsRulesIsSaidInTheDocumentTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** The witness: two invariants, each a choice reaching across both fields of one record. */
    private static final String WITNESS = """
            module demo

            data Taken

            data R = { a: String, b: String }
                invariant one = (a == "5" && b == "0") || (a == "6" && b == "1")
                invariant two = (a == "5" && b == "0") || (a == "6" && b == "0")

            behavior take : (r: R) -> Taken
            """;

    /** The same shape with each clause written at one position, which the reading holds exactly. */
    private static final String HELD = """
            module demo

            data Taken

            data R = { a: String, b: String }
                invariant one = a == "5" || a == "6"
                invariant two = b == "0"

            behavior take : (r: R) -> Taken
            """;

    /** The same, with a third position a clause of its own answers for. */
    private static final String BESIDE = """
            module demo

            data Taken

            data R = { a: String, b: String, c: Bool }
                invariant one = (a == "5" && b == "0") || (a == "6" && b == "1")
                invariant two = (a == "5" && b == "0") || (a == "6" && b == "0")
                invariant three = c == true

            behavior take : (r: R) -> Taken
            """;

    /** The same clauses beside one nothing satisfies with them, so the rules leave no value. */
    private static final String NONE = """
            module demo

            data Taken

            data R = { a: String, b: String }
                invariant one = (a == "5" && b == "0") || (a == "6" && b == "1")
                invariant two = (a == "5" && b == "0") || (a == "6" && b == "0")
                invariant apart = a == "9"

            behavior take : (r: R) -> Taken
            """;

    private static JsonNode behaviorOf(String source) {
        // Read with no choice held apart, which is the reading this finding comes of. Nothing
        // written here expands far enough to fall back to it at the limit a compilation sets, so a
        // test that wants the fallback says so — and this is the one seam where what the fallback
        // answers has to reach a document.
        Compilation compilation = Compilation.ofSource(source, "Main")
                .withReadingPolicy(souther.compiler.query.ReadAs.MERGING_WHAT_A_CHOICE_LEAVES);
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return JSON.readTree(AdequacyReport.of(compilation).json(SourceRendering.namedByIdentity(compilation.texts())))
                .get("modules").get(0).get("behaviors").get(0);
    }

    private static List<JsonNode> ofKind(String source, String kind) {
        List<JsonNode> out = new ArrayList<>();
        for (JsonNode each : behaviorOf(source).get("findings")) {
            if (each.get("kind").asString().equals(kind)) {
                out.add(each);
            }
        }
        return out;
    }

    @Test
    void thePositionsAreNamedUnderTheirOwnKind() {
        List<JsonNode> said = ofKind(WITNESS, "partition_values_not_separated");

        assertEquals(List.of("r.a", "r.b"),
                said.stream().map(each -> each.get("subject").asString()).toList(),
                "each position the product was read at is one entry");
    }

    /**
     * And nothing about it is said as a rule that went unread.
     *
     * <p>The whole of why it is its own kind. No rule is answerable for the width — it comes of the
     * clauses taken together and the limit they are read under — so an entry naming one would send
     * an author looking for a clause this compiler could not take in.
     */
    @Test
    void andNothingIsSaidAsARuleNothingRead() {
        assertEquals(List.of(), ofKind(WITNESS, "partition_not_read"),
                "no rule of this model went unread");
        for (JsonNode each : ofKind(WITNESS, "partition_values_not_separated")) {
            assertFalse(each.has("ruleId"), "no rule is answerable for it: " + each);
            assertFalse(each.has("reason"), "and what would lift it is one thing: " + each);
        }
    }

    /**
     * And a position beside them, answered by a clause of its own, is not named.
     *
     * <p>What the choice costs is the positions it reached across. Said of the reading rather than
     * of each position, "not every position is shown exact" is the only thing there is to say, and
     * every position gets it — which would name `c` for a correlation no clause about it was ever
     * part of.
     */
    @Test
    void aPositionAnsweredByAClauseOfItsOwnIsNotNamed() {
        assertEquals(List.of("r.a", "r.b"),
                ofKind(BESIDE, "partition_values_not_separated").stream()
                        .map(each -> each.get("subject").asString()).toList(),
                "c is left where its own clause put it");
    }

    /** A model whose clauses the reading holds exactly says none of this. */
    @Test
    void aReadingThatHoldsItsClausesSaysNothing() {
        assertTrue(ofKind(HELD, "partition_values_not_separated").isEmpty(),
                "each clause is written at one position, so the product is what they admit");
    }

    /**
     * And with the alternatives held apart, the same model says none of it.
     *
     * <p>Which is the whole of what holding them apart buys, arrived at where a consumer reads it:
     * the reading answers `a` the one value the two clauses leave, so there is no width to qualify
     * and no entry to write. What the fallback says above is what a reading that could not hold them
     * owes, and it is reached by nothing an author writes.
     */
    @Test
    void andHeldApartTheSameModelSaysNoneOfIt() {
        Compilation compilation = Compilation.ofSource(WITNESS, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        JsonNode behavior = JSON.readTree(
                        AdequacyReport.of(compilation).json(SourceRendering.namedByIdentity(compilation.texts())))
                .get("modules").get(0).get("behaviors").get(0);

        List<String> said = new ArrayList<>();
        for (JsonNode each : behavior.get("findings")) {
            said.add(each.get("kind").asString());
        }
        assertFalse(said.contains("partition_values_not_separated"),
                "every rule is read and what it leaves is held: " + said);
        assertFalse(said.contains("partition_not_read"), said.toString());
    }


    /**
     * A declaration the rules leave no value is not told how its values were held.
     *
     * <p>What a reading holds once it admits nothing is where the arithmetic had got to, and not the
     * relation's projections — those are empty wherever the relation is. So whether it is exact is a
     * question about a projection nobody is being shown, and answering it here would write a note
     * about the width of a set at a position no value of the type ever stands at.
     *
     * <p>Read with the alternatives merged, which is the reading that has anything to say about
     * width at all. Every clause is read and the positions are the ones the choices reached across,
     * so this is the same shape that does report — with one more rule beside it that nothing
     * satisfies.
     */
    @Test
    void aDeclarationTheRulesLeaveNoValueIsNotToldHowItsValuesWereHeld() {
        assertEquals(List.of(), ofKind(NONE, "partition_values_not_separated"),
                "no value of this type exists, and that is the answer it is owed");
    }
}
