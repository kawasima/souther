package souther.compiler.report;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.meta.ModulePath;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a document writes about a model two of whose sources were not read, written out.
 *
 * <p>Two sources nothing was observed from are two facts. They are the same word about the same
 * kind of thing, and what tells them apart is which source — which both arrays of this document now
 * say. One of them did not: its entries carried the word and nothing about what the word was said
 * of, so a reader was told how many things held the verdict open and none of what they were.
 *
 * <p><b>What is asked here is the wiring and not the order.</b> That an order is a total one over
 * what a document writes is asked of the order, over every sequence its members could be met in;
 * this asks that the document's arrays go through it at all, over data a compiler produced rather
 * than data a test built. A renderer that walked the account directly would answer this correctly
 * on a model whose facts are met in one order, which is every model with one such source — so the
 * model here has two.
 *
 * <p><b>And that the two arrays disagree on purpose.</b> They no longer disagree about telling two
 * facts apart, and that is the change: what they disagree about is what they count. {@code
 * keptOpenBy} counts facts, so two facts one word covers are two entries and neither is folded
 * away; {@code incompleteness} counts kinds per module. A change that folded either onto the
 * other's unit would pass one half of this and fail the other.
 */
class TwoFactsCalledOneThingAreTwoEntriesInTheDocumentsOrderTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** One module, and two further sources of examples for it that redefine what it already has —
     *  so neither is evaluated and nothing is observed from either. */
    private static final List<String> TWO_SOURCES_NOTHING_WAS_READ_FROM = List.of("""
            module example.split

            data Amount = Int
                invariant value >= 0 && value <= 1000

            data Yes
            data No
            data Flag = Yes | No

            data Draft = { cost: Amount, flag: Flag }
            data Ok = { n: Int }
            data Refused = { why: String }

            let shared = Draft { cost = Amount(7), flag = Yes }

            behavior take : (request: Draft) -> Ok | Refused
                constructs Ok

            let take (request) = Ok { n = request.cost.value }

            example take
                | (Draft { cost = Amount(7), flag = Yes }) -> Ok { n = 7 }
            """, """
            examples for example.split

            let shared = Draft { cost = Amount(0), flag = No }

            example take
                | (Draft { cost = Amount(0), flag = No }) -> Ok { n = 0 }
            """, """
            examples for example.split

            let shared = Draft { cost = Amount(3), flag = No }

            example take
                | (Draft { cost = Amount(3), flag = No }) -> Ok { n = 3 }
            """);

    /**
     * Both, neither folded away, and each saying which source it is about.
     *
     * <p>The unit is the fact, so a reader counting these counts how many things hold the verdict
     * open rather than how many words this document has for them. That much was always so. What is
     * new is that the two are no longer written identically: an entry says what it is about, so a
     * reader who has counted them can go on to look at one.
     */
    @Test
    void twoFactsOneWordCoversAreTwoEntriesOfThatWord() {
        assertEquals("""
                [ {
                  "kind" : "observation_absent",
                  "about" : {
                    "kind" : "source",
                    "source" : "1"
                  },
                  "runSensitivity" : "unaffected"
                }, {
                  "kind" : "observation_absent",
                  "about" : {
                    "kind" : "source",
                    "source" : "2"
                  },
                  "runSensitivity" : "unaffected"
                } ]""",
                written().get("keptOpenBy").toPrettyString());
    }

    /**
     * And the same two, told apart by what each was about, in the order this document says them in.
     *
     * <p>Which source came first is not the order they were met in but the order the identities are
     * compared in, which is what a run comparing this document against the last one needs.
     */
    @Test
    void theSameTwoAreToldApartByWhichSourceAndArrangedByIt() {
        assertEquals("""
                [ {
                  "code" : "observation_absent",
                  "scope" : "source",
                  "subject" : "1"
                }, {
                  "code" : "observation_absent",
                  "scope" : "source",
                  "subject" : "2"
                } ]""",
                written().get("modules").get(0).get("incompleteness").toPrettyString());
    }

    /**
     * And the table of sources this document owes an explanation of follows from writing them.
     *
     * <p>Not an order of its own. A source is recorded as the document writes its identity, so the
     * table comes out in the order the entries above did — which is a consequence of those being
     * arranged before anything was written, and would be the order a comparison happened to ask
     * about them in if a place were chosen while writing.
     */
    @Test
    void theSourcesThisDocumentExplainsFollowFromWritingThem() {
        assertEquals("""
                {
                  "1" : "1",
                  "2" : "2"
                }""", written().get("sources").toPrettyString());
    }

    private static JsonNode written() {
        Compilation compilation =
                Compilation.ofSources(TWO_SOURCES_NOTHING_WAS_READ_FROM, ModulePath.EMPTY);
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return JSON.readTree(
                AdequacyReport.of(compilation).json(SourceRendering.namedByIdentity(SourceLayouts.NONE)));
    }
}
