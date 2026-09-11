package souther.compiler.report;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.meta.ModulePath;
import souther.compiler.publish.PublishedIncompleteness;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every surface that says what a module could not read says them in the one order.
 *
 * <p>Three of them publish these facts: the document a build reads, the page a person reads, and
 * the block a generator writes. An order put on one is an order on one, and the other two go on
 * saying whatever the account was iterated in — which is the shape this went wrong in once.
 *
 * <p><b>What this can ask and what it cannot.</b> That two runs of this compiler write one document
 * is what an order being written down is for, and no test inside one run can ask it: a set built by
 * {@code Set.copyOf} iterates by a salt fixed once per process, so two answers in one run agree
 * whatever is wrong. What is asked here is the property that gives that one — that the surfaces
 * take their sequence from the same place — by holding the page against the document. A surface put
 * back on the account itself is then caught in whichever runs the salt disagrees with the written
 * order, which is most of them and not all.
 */
class WhatIsSaidTwiceAboutOneModelIsSaidTheSameWayTest {

    private static final List<String> SOURCES = List.of("""
            module example.twice

            data Amount = Int
                invariant value >= 0 && value <= 1000

            data Draft = { cost: Amount }
            data Ok = { n: Int }

            let shared = Draft { cost = Amount(7) }

            behavior take : (request: Draft) -> Ok
                constructs Ok

            let take (request) = Ok { n = request.cost.value }

            example take
                | (Draft { cost = Amount(7) }) -> Ok { n = 7 }
            """, """
            examples for example.twice

            let shared = Draft { cost = Amount(0) }

            example take
                | (Draft { cost = Amount(0) }) -> Ok { n = 0 }
            """, """
            examples for example.twice

            let shared = Draft { cost = Amount(3) }

            example take
                | (Draft { cost = Amount(3) }) -> Ok { n = 3 }
            """);

    /** Two facts, so that an order over them is something rather than nothing. */
    @Test
    void theModelLeavesMoreThanOneThingUnread() {
        List<PublishedIncompleteness> said = report().modules().get(0).incompleteness().written();

        assertTrue(said.size() > 1,
                () -> "one fact or none says nothing about an order: " + said);
    }

    /** And the page names them in the order the document writes them in. */
    @Test
    void thePageNamesThemInTheOrderTheDocumentWritesThem() {
        List<String> written = report().modules().get(0).incompleteness().written().stream()
                .map(each -> each.fact().subject()).toList();

        assertEquals(written, namedOnThePage(report().human(SourceRendering.namedByIdentity(SourceLayouts.NONE))),
                "the page a person reads and the document a build reads say what a module could"
                        + " not read in two orders, so one of them is not the order this compiler"
                        + " decided");
    }

    /** The sources the page says nothing was read from, in the order it says them. */
    private static List<String> namedOnThePage(String page) {
        List<String> out = new ArrayList<>();
        for (String line : page.lines().toList()) {
            int from = line.indexOf("no rows were read from `");
            if (from >= 0) {
                String rest = line.substring(from + "no rows were read from `".length());
                out.add(rest.substring(0, rest.indexOf('`')));
            }
        }
        return out;
    }

    private static AdequacyReport report() {
        Compilation compilation = Compilation.ofSources(SOURCES, ModulePath.EMPTY);
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return AdequacyReport.of(compilation);
    }
}
