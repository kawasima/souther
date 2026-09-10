package souther.compiler.publish;

import org.junit.jupiter.api.Test;
import souther.compiler.check.Clause;
import souther.compiler.check.ClauseName;
import souther.compiler.check.RuleCitation;
import souther.compiler.check.RuleReportAnchor;
import souther.compiler.check.RuleRef;
import souther.compiler.diag.Citation;
import souther.compiler.diag.SourcePos;
import souther.compiler.source.SourceId;
import souther.compiler.types.SourceConstruct;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;
import souther.compiler.types.WrittenOwner;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Choosing which handle a document writes asks where a rule is once for each one offered.
 *
 * <p>What it costs to ask is the caller's, not this one's: a report holds the answers it gathered
 * and a lookup is a lookup, while a generator asks the module that wrote the rule and each of those
 * is a question of its own. So the count is the contract, and it is written down here because a
 * fold that reads a projection while it compares reads it as many times as it compares — which is a
 * number that grows with what was offered and looks like nothing on a pair.
 */
class WhereARuleIsIsAskedOncePerHandleOfferedTest {

    private static final RuleRef.Comparison RULE = new RuleRef.Comparison("b",
            new SourceConstructOrigin(new WrittenOwner.Body("m", "b"), 0, 0,
                    SourceConstruct.BINARY));

    @Test
    void everyHandleOfferedIsResolvedOnce() {
        List<RuleCitation> offered = new ArrayList<>();
        for (int reach = 0; reach < 6; reach++) {
            offered.add(new RuleCitation.Written(RULE,
                    new RuleReportAnchor.ByTheReadingThatMetIt("m", "b", reach)));
        }
        List<RuleCitation.Written> asked = new ArrayList<>();

        Optional<RuleCitation> written = PublicationOrders.handleFor(offered, cited -> {
            asked.add(cited);
            return Citation.of(new SourcePos(cited.anchor()
                    instanceof RuleReportAnchor.ByTheReadingThatMetIt met ? met.reach() + 1 : 1,
                    1, new SourceId("0")));
        });

        assertEquals(offered.size(), asked.size(),
                () -> "one question per handle offered, and this asked " + asked);
        assertEquals(Optional.of(offered.get(0)), written,
                "and the handle a document writes is the one nearest the top of the file");
    }

    /** And a rule the author named is not asked about at all, because a name is found from
     *  anywhere. */
    @Test
    void aRuleWithANameIsNotAskedAbout() {
        RuleCitation named = new RuleCitation.Named(new RuleRef.Invariant(new Clause.Ref(
                new Clause.Id(TypeSymbols.declared(new TypeKey("m", "Amount")), 0),
                Optional.of(new ClauseName("cap")))));

        assertEquals(Optional.of(named), PublicationOrders.handleFor(List.of(named), cited -> {
            throw new AssertionError("a rule with a name has no place to be asked for: " + cited);
        }));
    }
}
