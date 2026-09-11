package souther.compiler;

import org.junit.jupiter.api.Test;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.query.GenerationScope;
import souther.compiler.partition.ObligationIdentity;
import souther.compiler.query.Composition;
import souther.compiler.query.Offering;
import souther.compiler.query.OfferingRequest;
import souther.compiler.query.RowKey;
import souther.compiler.query.Settlements;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two rows answering the same thing are one piece of work.
 *
 * <p>A candidate is composed for one item and the positions that item does not name hold whatever
 * the row has to hold, so a row composed for one thing can stand where another asks. Nothing used to
 * put that question: rows were joined where their values were written the same way and nowhere else,
 * so a row inside a region and a row at the end of it went out as two.
 *
 * <p>The model here is the one the shipping rules are written from, with the three rows a person
 * writes first. What is left uncovered is a class at two of the regions, four arms, two points of
 * the comparison a guard draws and two of a declaration's own line — and the rows composed at the
 * declaration's line stand inside the comparison's regions, which is what takes two rows out.
 *
 * <p>What says five is enough is not the arithmetic above but the findings: every gap the report
 * raises is something this run is asked about, and one of the rows that are offered stands at it.
 * The issue counted six by hand, one of which was the only thing offering for a point a written row
 * already stands at — which is nobody's work.
 */
class ARowIsNotOfferedForWhatAnotherOfferedRowAnswersTest {

    private static final String MODULE = """
            module example.shippingfee

            data 商品合計 = Int
                invariant value >= 0 && value <= 1000000

            data 本州
            data 北海道沖縄
            data 離島
            data 配送地域 = 本州 | 北海道沖縄 | 離島

            data 一般
            data プレミアム
            data 会員区分 = 一般 | プレミアム

            data 注文 =
                { 合計: 商品合計
                , 地域: 配送地域
                , 会員: 会員区分
                }

            data 送料無料
            data 送料あり = { 金額: Int }

            behavior 送料を求める : (注文: 注文) -> 送料無料 | 送料あり
                constructs 送料あり

            let 送料を求める (注文) =
                match 注文.会員 with
                    | プレミアム -> 送料無料
                    | 一般 -> 一般会員の送料(注文)

            let 一般会員の送料 (注文: 注文): 送料無料 | 送料あり = {
                guard 注文.合計.value < 5000 else 離島以外は無料(注文.地域)
                送料あり { 金額 = 地域別送料(注文.地域) }
            }

            let 離島以外は無料 (地域: 配送地域): 送料無料 | 送料あり =
                match 地域 with
                    | 離島 -> 送料あり { 金額 = 1500 }
                    | 本州 -> 送料無料
                    | 北海道沖縄 -> 送料無料

            let 地域別送料 (地域: 配送地域): Int =
                match 地域 with
                    | 本州 -> 500
                    | 北海道沖縄 -> 800
                    | 離島 -> 1500

            example 送料を求める
                | "プレミアム会員は無料" :
                    (注文 { 合計 = 商品合計(1000), 地域 = 本州, 会員 = プレミアム })
                        -> 送料無料
                | "5,000円以上は無料" :
                    (注文 { 合計 = 商品合計(5000), 地域 = 本州, 会員 = 一般 })
                        -> 送料無料
                | "5,000円未満の本州は500円" :
                    (注文 { 合計 = 商品合計(4999), 地域 = 本州, 会員 = 一般 })
                        -> 送料あり { 金額 = 500 }
            """;

    @Test
    void aRowStandingWhereAnotherAlreadyStandsIsNotOfferedTwice() {
        Compilation compilation = compiled();
        Composition composed = composed(compilation);
        Offering offered = Adequacy.offeredFor(compilation.db(),
                OfferingRequest.overTheModule("example.shippingfee", true));
        assertNotNull(offered, "the model under test compiles");

        // Five, of the eight the searches composed. Two of them stand at the ends of the
        // comparison's regions, where the rows composed at the declaration's line already stand.
        // The third is the only thing offering for a point nobody is asked about — a row is written
        // at it already — and what it stands at besides, another offered row stands at too.
        assertEquals(5, offered.count(),
                "a row answering what another answers is not offered: " + composed.count()
                        + " composed, " + offered.count() + " offered");
    }

    @Test
    void everyGapTheReportRaisesIsAnsweredByAnOfferedRow() {
        Compilation compilation = compiled();
        Settlements table = Settlements.of(compilation.db(), composed(compilation));
        Set<RowKey> kept = table.keeping();

        // Read off the findings and not off the items: what a person is short of is the report's
        // answer, and a reduction that shrank its own universe would otherwise be marking its own
        // homework.
        java.util.List<Adequacy.Finding> findings = compilation.db()
                .ask(new Adequacy.Findings("example.shippingfee")).value();
        assertNotNull(findings, "the model under test is measured");
        int asked = 0;
        for (Adequacy.Finding finding : findings) {
            ObligationIdentity item = switch (finding.about()) {
                case souther.compiler.query.About.APointOfABorder(var point) ->
                        new ObligationIdentity.OfALine(point.point());
                case souther.compiler.query.About.APointOfADeclaredBorder(var owed) ->
                        new ObligationIdentity.OfALine(owed.debt().point());
                case souther.compiler.query.About.AnArmNoRowGoesThrough(var arm) ->
                        new ObligationIdentity.OfAnArm(arm.obligation());
                case souther.compiler.query.About.AClassNoRowIsIn(var missing) ->
                        new ObligationIdentity.OfAClass(
                                new souther.compiler.partition.ClassOfAPosition(
                                        missing.axis().at(), missing.name()));
                default -> null;
            };
            if (item == null) {
                continue;
            }
            // In the universe, and not skipped where it is not. A gap whose item this run left out
            // is the reduction shrinking what it is judged against, which is the thing to catch.
            assertTrue(table.requested().contains(item),
                    "a gap the report raises is something this run is asked about: " + item);
            asked++;
            assertTrue(table.offers(kept, item),
                    "a gap the report raises is answered by a row that is offered: " + item);
        }
        assertTrue(asked > 0, "the model under test has gaps of the kinds a row is offered for");
    }

    @Test
    void whatTheRowsAnswerIsWhatTheyAnsweredBefore() {
        Compilation compilation = compiled();
        Settlements table = Settlements.of(compilation.db(), composed(compilation));
        Set<ObligationIdentity> before = table.settled();
        Set<RowKey> kept = table.keeping();

        assertTrue(kept.size() < table.byRow().size(), "something goes: " + kept.size()
                + " of " + table.byRow().size());
        for (ObligationIdentity item : before) {
            assertTrue(kept.stream().anyMatch(row -> table.at(row, item).settles()),
                    "and " + item + " is still answered by one of the rows that are left");
        }
    }

    @Test
    void theRowsThatGoAreTheOnesInsideARegionAnotherRowSitsIn() {
        Compilation compilation = compiled();
        Settlements table = Settlements.of(compilation.db(), composed(compilation));
        Set<RowKey> kept = table.keeping();
        for (Map.Entry<RowKey, Map<ObligationIdentity, souther.compiler.query.Settlement>> row
                : table.byRow().entrySet()) {
            if (kept.contains(row.getKey())) {
                continue;
            }
            // Everything it answered, another row that is left answers as well. Anything else would
            // be a piece of work going missing rather than one being said once instead of twice.
            row.getValue().forEach((item, settlement) -> {
                if (settlement.settles()) {
                    assertTrue(kept.stream().anyMatch(one -> table.at(one, item).settles()),
                            row.getKey() + " went, and nothing left answers " + item);
                }
            });
        }
    }

    private static Compilation compiled() {
        Compilation compilation = Compilation.ofSource(MODULE, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        return compilation;
    }

    /** What the searches composed, before anything asks what the rows settle. */
    private static Composition composed(Compilation compilation) {
        Map<String, Adequacy.Filling> generated =
                Adequacy.generatedOf(compilation.db(), "example.shippingfee");
        assertNotNull(generated, "the model under test compiles: " + compilation.errors());
        Composition composed = Composition.composed(
                OfferingRequest.overTheModule("example.shippingfee", true), generated,
                Adequacy.accountFor(compilation.db(), "example.shippingfee",
                        new GenerationScope.Module()),
                Composition.WhatStandsIn.REQUIRING_NOTHING);
        assertEquals(8, composed.count(),
                "the searches compose one row per thing they are asked for");
        return composed;
    }
}
