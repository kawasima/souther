package souther.bench;

import org.junit.jupiter.api.Test;

import souther.compiler.publish.DocumentPart;
import souther.compiler.publish.PublicationOrders;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A field a document decides the order of is written from a sequence somebody put in order.
 *
 * <p>What the orders themselves cannot say. A method that starts an array and walks a collection
 * into it publishes whatever that collection iterates in, and there is nothing in the array or in
 * the collection to say so — the document comes out looking the same until something upstream is
 * tidied, and then it comes out different for a reason no diff explains.
 *
 * <p><b>Which fields, read off what was declared and not off the schema.</b> Whether an array's
 * order is this compiler's or the model's is a fact about the contract rather than about the shape
 * of the field, so it is written down where the orders are
 * ({@link PublicationOrders#CANONICALLY_ARRANGED_FIELDS}). Read off the schema instead, this would
 * be asking every array of the document to be sorted, which is false of the ones whose order is the
 * order somebody wrote — and a check making that demand would be met by sorting them.
 *
 * <p>So the arrays whose order is somebody's own are outside this on purpose, and
 * {@link #anArrayWhoseOrderIsNotThisCompilersIsOutsideThis} is what says the population really does
 * leave them out rather than happening to contain no counterexample.
 */
class AFieldWhoseOrderIsThisCompilersIsWrittenThroughACrossingTest {

    private static final String STARTS_AN_ARRAY = "putArray";

    private static final String NAMES_A_PART = "souther.compiler.publish.DocumentPart";

    private static final Set<String> CROSSINGS = Set.of(
            "souther.compiler.publish.CanonicalArrangement",
            "souther.compiler.publish.CanonicalSelection",
            "souther.compiler.publish.SourceOrdered");

    /**
     * Every array a report starts is one whose name can be read here.
     *
     * <p>What the rule below is over is the writers of a named field, found by the name written at
     * the call. A name this cannot read is not a writer that fails the rule — it is a writer the
     * rule never sees, so the population would go on looking complete while a field left it. Held
     * first, because a check whose population can shrink in silence says less than it appears to.
     */
    @Test
    void everyArrayAReportStartsIsStartedUnderANameThisCanRead() throws Exception {
        List<String> unread = new ArrayList<>();
        for (Compiled.Invocation each : called()) {
            if (each.site().member().equals(STARTS_AN_ARRAY)
                    && each.site().from().startsWith("souther.compiler.report.")
                    && each.said().isEmpty()
                    // The call and not the method it is in. A method that starts one array by
                    // naming a part of the document says nothing about the arrays beside it, and
                    // excusing it as a whole is how a check written to keep a population from
                    // shrinking lets one shrink.
                    && !each.site().owner().equals(NAMES_A_PART)) {
                unread.add(each.site().at());
            }
        }
        // And named for later rather than called, which is the other way an array is started
        // somewhere this cannot read a name: a reference puts no invoke in the caller's code, so
        // the reading above meets it nowhere and the field would leave the population in silence.
        for (Compiled.Site each : compiled()) {
            if (each.member().equals(STARTS_AN_ARRAY) && each.how() != Compiled.How.CALLS
                    && each.from().startsWith("souther.compiler.report.")) {
                unread.add(each.at() + " names it for later");
            }
        }

        assertEquals(List.of(), unread,
                "an array of the document is started under a name nothing here can read, so a"
                        + " field written there is outside every rule about named fields");
    }

    @Test
    void everyFieldWhoseOrderIsThisCompilersIsWrittenFromACrossing() throws Exception {
        List<String> straightFromACollection = new ArrayList<>();
        for (String field : PublicationOrders.CANONICALLY_ARRANGED_FIELDS) {
            Set<String> writers = whereTheArrayIsStarted(field);
            assertFalse(writers.isEmpty(), () -> "nothing writes " + field + ", so what this holds"
                    + " to that name is nothing");
            for (String writer : writers) {
                // The method and no more of it. What is read here is that a method reaches a
                // crossing, so a method that also writes an array with an order of its own would
                // answer for that one by having crossed for this.
                Set<String> alsoStartedThere = arraysStartedIn(writer);
                if (!alsoStartedThere.equals(Set.of(field))) {
                    straightFromACollection.add(field + " is written by " + writer
                            + ", which also writes " + alsoStartedThere);
                } else if (!crossesIn(writer)) {
                    straightFromACollection.add(field + " is written by " + writer
                            + ", which reaches no crossing");
                }
            }
        }

        assertEquals(List.of(), straightFromACollection,
                "a field whose order this compiler decides is written straight out of a collection,"
                        + " so what a reader is given is whatever that iterates in");
    }

    /**
     * And a field whose order is somebody else's is not asked to go through one.
     *
     * <p>The control. Without it this passes by holding two fields to a rule and saying nothing
     * about whether the rule was written wide enough to be false of anything — and the way this
     * check goes wrong is by growing until it demands an order of arrays that have one.
     */
    @Test
    void anArrayWhoseOrderIsNotThisCompilersIsOutsideThis() throws Exception {
        Set<String> writers = whereTheArrayIsStarted("behaviors");

        assertFalse(writers.isEmpty(), "nothing writes the behaviors of a module");
        assertTrue(writers.stream().noneMatch(
                        AFieldWhoseOrderIsThisCompilersIsWrittenThroughACrossingTest::crossesIn),
                () -> "the behaviors of a module are written in the order they were declared in,"
                        + " and something is now putting them in an order of this compiler's:"
                        + " " + writers);
        assertFalse(PublicationOrders.CANONICALLY_ARRANGED_FIELDS.contains("behaviors"),
                "a field written in the order somebody wrote it is not one this compiler orders");
    }

    /** Every array {@code method} starts, so that a method answering for one is answering for
     *  nothing else. */
    private static Set<String> arraysStartedIn(String method) throws Exception {
        Set<String> out = new LinkedHashSet<>();
        for (Compiled.Invocation each : called()) {
            if (each.site().at().equals(method) && each.site().member().equals(STARTS_AN_ARRAY)) {
                out.addAll(each.said());
            }
        }
        out.addAll(namesStartedThrough(method));
        return out;
    }

    /**
     * The arrays {@code method} starts by naming a part of the document rather than a string.
     *
     * <p>The five parts that carry a rule handle are started through {@link DocumentPart}, which is
     * what makes a part's name and the place the schema declares it one value instead of two
     * literals a writer could disagree about. The name is still written down and still read here —
     * it moved from the call into a constant the call reads, and the set of them is closed by the
     * enum, so an array started this way cannot leave the population in silence either.
     */
    private static Set<String> namesStartedThrough(String method) throws Exception {
        Set<String> out = new LinkedHashSet<>();
        boolean starts = false;
        for (Compiled.Invocation each : called()) {
            starts |= each.site().at().equals(method)
                    && each.site().member().equals(STARTS_AN_ARRAY)
                    && each.site().owner().equals(NAMES_A_PART);
        }
        if (!starts) {
            return out;
        }
        for (Compiled.Site each : compiled()) {
            if (each.at().equals(method) && each.how() == Compiled.How.READS
                    && each.owner().equals(NAMES_A_PART)) {
                out.add(DocumentPart.valueOf(each.member()).key());
            }
        }
        return out;
    }

    /** The methods that start an array under {@code field}, read off the name written at the call
     *  or off the part the call names. */
    private static Set<String> whereTheArrayIsStarted(String field) throws Exception {
        Set<String> out = new LinkedHashSet<>();
        for (Compiled.Invocation each : called()) {
            if (each.site().member().equals(STARTS_AN_ARRAY) && each.said().contains(field)) {
                out.add(each.site().at());
            }
        }
        for (Compiled.Invocation each : called()) {
            if (each.site().member().equals(STARTS_AN_ARRAY)
                    && namesStartedThrough(each.site().at()).contains(field)) {
                out.add(each.site().at());
            }
        }
        return out;
    }

    /**
     * Whether that method takes what it writes from a sequence somebody put in order.
     *
     * <p>The method itself and nothing it goes on to call. Asked a level deeper, a method that
     * called anything that had ever crossed would answer yes — and the array whose order is
     * somebody else's is written by a method that calls plenty, so the control below could no
     * longer ask the same question the rule does. What makes that affordable is that the sequence
     * is handed over as the arrangement: a writer that wants it in order says so where it writes.
     */
    private static boolean crossesIn(String method) {
        for (Compiled.Site site : compiled()) {
            // Called and not merely named. What is asked is that this method takes the sequence
            // from a crossing; a method that hands `written` on as a reference has not taken one,
            // and counting that would let a writer answer for a sequence it never asked for.
            if (site.how() == Compiled.How.CALLS
                    && CROSSINGS.contains(site.owner()) && site.member().equals("written")
                    && (site.at().equals(method) || site.at().startsWith(method + "("))) {
                return true;
            }
        }
        return false;
    }

    /** The compiled classes, read once for every question here rather than once per question. */
    private static List<Compiled.Site> compiled() {
        return SITES;
    }

    /** The same, for the calls whose text was read. */
    private static List<Compiled.Invocation> called() {
        return CALLS;
    }

    private static final List<Compiled.Site> SITES = read(Compiled::sites);
    private static final List<Compiled.Invocation> CALLS = read(Compiled::invocations);

    private interface Reading<T> {
        List<T> read() throws Exception;
    }

    private static <T> List<T> read(Reading<T> of) {
        try {
            return of.read();
        } catch (Exception opaque) {
            throw new AssertionError("the compiled classes could not be read", opaque);
        }
    }
}
