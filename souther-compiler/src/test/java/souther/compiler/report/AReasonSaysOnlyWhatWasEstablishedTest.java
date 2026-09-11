package souther.compiler.report;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import souther.compiler.source.SourceId;

import org.junit.jupiter.api.Test;

import souther.compiler.observe.Incompleteness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A sentence about a reason is a claim about every place that writes it.
 *
 * <p>Turning the codes into sentences is how the report stops handing a reader an enum's name. It
 * is also how a reader gets told what happened on the authority of a name that may be wider than
 * the sentence — which is the defect this whole change is about, made once more one level up. So a
 * code is written out in words only where its producers have been read.
 *
 * <p>The subject is the part of a sentence that is not written here. A reason names what it is about
 * as an identity, and for a source that is whatever the compile was handed it as — a position in a
 * list under a build. What to call it is the caller's answer and arrives with the request, so what
 * these hold is that the words come from the code and the subject comes from the caller.
 */
class AReasonSaysOnlyWhatWasEstablishedTest {

    @Test
    void aSourceWithNoObservationSaysThatAndNotWhyItHadNone() {
        String said = Reasons.said(Incompleteness.ofSource(
                Incompleteness.Code.OBSERVATION_ABSENT, new SourceId("1")).identity(),
                new SourceRendering(id -> "trip.sou", SourceLayouts.NONE));

        assertEquals("no rows were read from `trip.sou`, so what they cover is unknown", said);
    }

    /**
     * And it says it about a file, not about the id the compile holds it under.
     *
     * <p>This held the id: the reason was written for source {@code 1} and the sentence was expected
     * to read {@code `1`}. The subject was the one part of the sentence that is not written here, and
     * pinning it made the sentence agree with the identity it was handed — which is what a report
     * printing `` `0` `` at a person had been doing all along.
     */
    @Test
    void theSubjectOfASourceIsTheNameAndNotTheId() {
        Incompleteness gap = Incompleteness.ofSource(
                Incompleteness.Code.OBSERVATION_ABSENT, new SourceId("1"));

        String said = Reasons.said(gap.identity(),
                new SourceRendering(id -> "1".equals(id.value()) ? "b/model.sou" : id.value(),
                        SourceLayouts.NONE));

        assertTrue(said.contains("`b/model.sou`"), said);
        assertFalse(said.contains("`1`"), "an id is not what a person is shown: " + said);
    }

    /** Named for the error that was caught, and it says no more than that. The runtime being off
     * the classpath is the case it was written for and not the only one the JVM raises it for. */
    @Test
    void aLinkageFailureDoesNotClaimTheRuntimeIsMissing() {
        String said = Reasons.said(Incompleteness.of(Incompleteness.Code.LINKAGE_FAILED,
                Incompleteness.Scope.BEHAVIOR, "submit").identity(),
                SourceRendering.namedByIdentity(SourceLayouts.NONE));

        assertFalse(said.contains("runtime"), said);
        assertTrue(said.contains("would not link"), said);
    }

    /**
     * And it says what did not happen next, now that one producer is left to say it.
     *
     * <p>Three places wrote it: an example whose classes would not load, a fill that could not put
     * its candidates through the decoder, and a boundary that could not build one. Only the first
     * was rows that did not run — the other two happen after the rows were read — so the sentence
     * stopped where the three stopped agreeing. The other two are the generator's now and report in
     * its vocabulary, which leaves the example, where nothing was observed.
     *
     * <p>This holds the wording and not the producers. Which places write a code is not something a
     * unit of this size can walk; it is read by hand, and the reading is what the sentence rests
     * on — so it is re-read whenever a producer is added or taken away, as one was here.
     */
    @Test
    void aLinkageFailureSaysWhatItsOneProducerEstablishes() {
        String said = Reasons.said(Incompleteness.of(Incompleteness.Code.LINKAGE_FAILED,
                Incompleteness.Scope.BEHAVIOR, "submit").identity(),
                SourceRendering.namedByIdentity(SourceLayouts.NONE));

        assertEquals("the classes for `submit` would not link, so its rows did not run", said);
    }

    /**
     * A code with one producer says what that producer establishes, and that can be more than the
     * code alone.
     *
     * <p>{@code INSTRUMENTATION_ABSENT} is written at one place, on a branch taken only where arm
     * coverage was asked for, and it returns no rows with it. Both are part of what the sentence may
     * say. What it may not say is which of the things that stop those classes being made happened —
     * a backend that failed, inputs that were not there — because nothing there can tell them apart.
     */
    @Test
    void oneProducerLetsTheSentenceSayWhatThatProducerEstablishes() {
        String said = Reasons.said(Incompleteness.of(Incompleteness.Code.INSTRUMENTATION_ABSENT,
                Incompleteness.Scope.MODULE, "example.trip").identity(),
                SourceRendering.namedByIdentity(SourceLayouts.NONE));

        assertEquals("the classes `example.trip` needed for arm coverage could not be made,"
                + " so none of its rows were read", said);
    }

    /**
     * Every code says something now, and none of them says its own name back.
     *
     * <p>A reason reading {@code submit (value_unreadable)} is the data printed as though it were a
     * sentence: it tells a reader what the code was and nothing about what happened. Every code left
     * here is one a report is written in, and one whose producers have been read far enough to say
     * so in words.
     */
    @Test
    void noCodeIsPrintedAsItsOwnName() {
        for (Incompleteness.Code code : Incompleteness.Code.values()) {
            String said = Reasons.said(Incompleteness.of(code,
                    Incompleteness.Scope.BEHAVIOR, "submit").identity(),
                    SourceRendering.namedByIdentity(SourceLayouts.NONE));

            assertNotEquals("submit (" + code.name().toLowerCase(java.util.Locale.ROOT) + ")", said,
                    code + " is printed as itself");
            assertTrue(said.contains("submit"), code + " does not say what it is about: " + said);
        }
    }
}
