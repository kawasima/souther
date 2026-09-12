package souther.bench;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Who may say what order a plurality of reasons is in.
 *
 * <p>Two orders and two answers, and the asymmetry between them is the arrangement. What order this
 * compiler publishes a kind in is one decision for the whole compiler, so making one is closed to
 * the place that declares them. That a particular sequence is in the order the model has is not a
 * decision at all — it is something a producer either knows or does not — so anybody who knows it
 * may say it, and a reader who does not may not.
 *
 * <p>Read off the call sites rather than off the declarations. A check over the fields of the
 * classes there would see a field of the order's type and miss an order made inside a method and
 * used from there, which is the same second order with nowhere to see it. What makes one of these
 * is calling a method, and this says who calls them.
 */
class OnlyOnePlaceDeclaresAPublicationOrderTest {

    private static final String ORDER = "souther.compiler.publish.CanonicalSelection$Order";
    private static final String ARRANGEMENT =
            "souther.compiler.publish.CanonicalArrangement$Order";
    private static final String AUTHORITY = "souther.compiler.publish.PublicationOrders";
    private static final String SOURCE_ORDERED = "souther.compiler.publish.SourceOrdered";
    private static final String AUTHORED_ORDER = "souther.compiler.inputs.AuthoredOrder";
    private static final String REASONS = "souther.compiler.inputs.RuleReasons";
    private static final String REPORT = "souther.compiler.report.";
    private static final String PROJECTION = "souther.compiler.partition.ReportedReason";

    /**
     * Nothing but the one place makes an order.
     *
     * <p><b>Which methods make one is asked of what they answer with, not listed here.</b> Named,
     * this rule covered the factories somebody remembered when they wrote it — and the second shape
     * an order comes in was added to the list a round later than the shape itself, which is a rule
     * that grows by being reminded. What makes an order is answering with one, so that is the
     * question, and a factory added to either shape is inside this without anybody adding a line.
     *
     * <p>Called or named for later, because either makes one: a reference runs the same factory
     * somewhere else and puts no invoke in the caller's code, so a rule counting calls alone is
     * passed by {@code Order::by}. What is not making one is reading a field that happens to have
     * a factory's name, which is why the reach is asked as well.
     */
    @Test
    void nothingButThePublicationOrdersMakesAnOrder() throws Exception {
        List<String> made = new ArrayList<>();
        boolean reached = false;
        for (Compiled.Site site : Compiled.sites()) {
            if (!makesAnOrder(site)) {
                continue;
            }
            reached = true;
            if (!site.from().equals(AUTHORITY)) {
                made.add(site.at());
            }
        }

        assertFalse(made.isEmpty() && !reached,
                "no order is made anywhere, so this is passing for the wrong reason");
        assertEquals(List.of(), made,
                "an order over a kind of reason made outside the one place that declares them,"
                        + " which is how a kind comes to have two orders that agree until one moves");
    }

    /** Whether reaching this is making an order, which is whether what it answers with is one. */
    private static boolean makesAnOrder(Compiled.Site site) {
        if (site.how() != Compiled.How.CALLS && site.how() != Compiled.How.REFERS) {
            return false;
        }
        if (!site.owner().equals(ORDER) && !site.owner().equals(ARRANGEMENT)) {
            return false;
        }
        for (java.lang.reflect.Method each : orderShape(site.owner()).getDeclaredMethods()) {
            if (each.getName().equals(site.member())
                    && java.lang.reflect.Modifier.isStatic(each.getModifiers())
                    && (each.getReturnType().equals(orderShape(ORDER))
                            || each.getReturnType().equals(orderShape(ARRANGEMENT)))) {
                return true;
            }
        }
        return false;
    }

    private static Class<?> orderShape(String named) {
        try {
            return Class.forName(named);
        } catch (ClassNotFoundException absent) {
            throw new AssertionError("a shape an order comes in that this cannot read: " + named,
                    absent);
        }
    }

    /**
     * Nothing downstream of a reading says a sequence is in the order the model has.
     *
     * <p>The claim an {@link souther.compiler.inputs.AuthoredOrder} carries is that the order came
     * from what somebody wrote, and it is the one place that claim is made. Nothing in a list says
     * where its order came from, so a caller that never saw a clause is stating a fact about the
     * model out of whatever it was handed — correctly for as long as one producer fills the list,
     * and wrong the day a second one does with nothing in a position to notice.
     *
     * <p><b>Which callers, by what they have in hand rather than by name.</b> A projection onto
     * published words has words and nothing else. So it is named and everything else is left to
     * whoever can say it — a producer added where the places are read is inside this without a
     * line, and one added at the projection fails.
     *
     * <p>{@link souther.compiler.publish.SourceOrdered} makes no claim of its own any more: it
     * carries one of these across the crossing, so there is nothing at a report for this to permit
     * or refuse.
     */
    @Test
    void nothingThatHasOnlyWordsSaysASequenceIsInTheModelsOrder() throws Exception {
        List<String> claimed = new ArrayList<>();
        boolean reached = false;
        for (Compiled.Site site : Compiled.sites()) {
            if (site.owner().equals(AUTHORED_ORDER) && site.member().equals("asWritten")) {
                reached = true;
                if (site.from().startsWith(PROJECTION)) {
                    claimed.add(site.at());
                }
            }
        }

        assertFalse(claimed.isEmpty() && !reached,
                "nothing anywhere says a sequence is in the order it was written, so this is"
                        + " passing for the wrong reason");
        assertEquals(List.of(), claimed,
                "the model's order is claimed for a sequence by something holding published words,"
                        + " which is a claim about what it cannot see");
    }

    /**
     * And the claim is made in the one place that reads the places it is a claim about.
     *
     * <p>Narrower than the rule above, and for the same reason one step further in. What settles
     * whether a sequence of reasons is in the author's order is where each of them stands, and what
     * holds the places is what may answer. What a reading publishes holds none — a construct is
     * counted over what its author wrote and says nothing about where that is — so the boundary
     * that resolves them to places is the one that can say it, and a caller with the reasons alone
     * is holding the half that cannot.
     *
     * <p>So a second maker is a second answer to one question, and the two would disagree the day
     * one of them learned something. Which callers there may be is a list because there is one.
     */
    @Test
    void theModelsOrderIsClaimedWhereThePlacesAreRead() throws Exception {
        List<String> claiming = new ArrayList<>();
        for (Compiled.Site site : Compiled.sites()) {
            if (site.owner().equals(AUTHORED_ORDER) && site.member().equals("asWritten")
                    && !claiming.contains(site.from())) {
                claiming.add(site.from());
            }
        }

        assertEquals(List.of(REPORT + "AdequacyReport"), claiming,
                "a sequence is called the order somebody wrote it in somewhere that is not the"
                        + " one place holding what they wrote");
    }

    /** And the crossing carries that claim rather than making a second one. */
    @Test
    void theCrossingIntoADocumentMakesNoClaimOfItsOwn() throws Exception {
        List<String> made = new ArrayList<>();
        for (Compiled.Site site : Compiled.sites()) {
            if (site.owner().equals(SOURCE_ORDERED) && site.member().equals("asWritten")) {
                made.add(site.at());
            }
        }

        assertEquals(List.of(), made,
                "a plurality is called the author's order where it crosses into a document, which"
                        + " is the one place that has nothing left to see it by");
    }
}
