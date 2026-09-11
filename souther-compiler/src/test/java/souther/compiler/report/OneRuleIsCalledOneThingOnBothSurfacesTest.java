package souther.compiler.report;

import souther.compiler.diag.SourceLayouts;
import souther.compiler.diag.SourceRendering;
import org.junit.jupiter.api.Test;

import souther.compiler.check.BehaviorContract;
import souther.compiler.check.Clause;
import souther.compiler.check.ClauseName;
import souther.compiler.check.RuleCitation;
import souther.compiler.check.RuleReportAnchor;
import souther.compiler.check.RuleRef;
import souther.compiler.diag.Citation;
import souther.compiler.diag.SourceNameResolver;
import souther.compiler.diag.SourcePos;
import souther.compiler.publish.PublishedRuleHandle;
import souther.compiler.publish.PublishedRuleKind;
import souther.compiler.publish.RuleHandleProse;
import souther.compiler.types.SourceConstruct;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;
import souther.compiler.types.ValueName;
import souther.compiler.types.WrittenOwner;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A rule with no name is called the same thing by a reader and by a document.
 *
 * <p>Two surfaces and one rule. A reader is sent to a rule with no name by where it is written and
 * sees {@code comparison@…}; a document groups the same rule under a word of its own. While every
 * rule found that way was a comparison, calling all of them {@code comparison} was not yet wrong —
 * the word named the one kind there was. A behavior's rule about the strings at a position is found
 * the same way and is not a comparison, so the word became a statement about the rule that is false
 * of it, and a reader following the handle was sent to a call and told it was a comparison.
 *
 * <p>Over every kind of rule the seal has, and not over one somebody built here. The word that was
 * wrong was wrong for a kind nobody had written a case for, so a check of one kind is a check that
 * passes for exactly the kinds it was written after.
 *
 * <p>Held to the reader's word rather than to a spelling written down here. Both surfaces naming
 * {@code guard} would satisfy a pair of literals as readily as both naming the rule.
 *
 * <p>What is checked is the agreement and not a shared spelling. The wire word is a contract a
 * schema version pins and what a reader is shown is not, so the two stay separate values that a
 * version may take apart deliberately — and that this is where such a version would be refused is
 * the point of asking here rather than folding them into one string.
 */
class OneRuleIsCalledOneThingOnBothSurfacesTest {

    /** Somewhere for a rule with no name to be, since what is read here is the word in front of
     *  the place and not the place. Which module answers where a rule is is asked elsewhere. */
    private static final PublishedRuleHandle.WhereARuleIs SOMEWHERE =
            _ -> Citation.of(new SourcePos(1, 1));

    /**
     * One of each kind, built by hand.
     *
     * <p>From the seal and not from a compilation: what is being asked is whether every kind agrees
     * with itself across the two surfaces, and a model that happens to write three kinds of rule
     * would make the population what that model states.
     */
    private static List<RuleRef> everyKind() {
        WrittenOwner.Body body = new WrittenOwner.Body("m", "b");
        return List.of(
                new RuleRef.Invariant(new Clause.Ref(
                        new Clause.Id(TypeSymbols.declared(new TypeKey("m", "Amount")), 0),
                        Optional.of(new ClauseName("cap")))),
                new RuleRef.Ensures(
                        new BehaviorContract.RuleId(new ValueName.Behavior("m", "b"), 0, 0, null),
                        "c"),
                new RuleRef.Comparison("b",
                        new SourceConstructOrigin(body, 1, 0, SourceConstruct.BINARY)),
                new RuleRef.Fork("b",
                        new SourceConstructOrigin(body, 3, 0, SourceConstruct.IF)),
                new RuleRef.Predicate("b",
                        new SourceConstructOrigin(body, 2, 0, SourceConstruct.CALL)));
    }

    /** That the list above is the seal and not a list somebody kept up by hand. */
    @Test
    void thePopulationIsEveryKindTheSealHas() {
        assertEquals(
                kindsOf(RuleRef.class),
                everyKind().stream().map(each -> (Class<?>) each.getClass())
                        .collect(Collectors.toSet()),
                "one of each kind of rule the seal has, and no other");
        assertEquals(
                Set.of(RuleRef.Named.class, RuleRef.Written.class),
                Set.of(RuleRef.class.getPermittedSubclasses()),
                "and the seal divides them first by how a reader finds them, which is what the two"
                        + " surfaces are two ways of saying");
    }

    /** Every kind of rule the seal names, which are its leaves — the halves it divides into first
     *  are not kinds a document names. */
    private static Set<Class<?>> kindsOf(Class<?> sealed) {
        Class<?>[] permits = sealed.getPermittedSubclasses();
        if (permits == null || permits.length == 0) {
            return Set.of(sealed);
        }
        Set<Class<?>> out = new LinkedHashSet<>();
        for (Class<?> each : permits) {
            out.addAll(kindsOf(each));
        }
        return out;
    }

    /**
     * Every kind of rule found by where it is written is called the same thing on both surfaces.
     *
     * <p>The handle's own sentence and not a word this test knows: what a reader sees is the front
     * of the sentence, cut at the join, so a word that stopped agreeing with the document's would
     * fail here whichever of the two moved.
     */
    @Test
    void aDocumentCallsARuleWithNoNameWhatAReaderIsShown() {
        for (RuleRef each : everyKind()) {
            if (!(each instanceof RuleRef.Written written)) {
                continue;
            }
            RuleCitation cited = new RuleCitation.Written(written,
                    new RuleReportAnchor.ByTheModuleThatWroteIt());
            String said = RuleHandleProse.said(
                    PublishedRuleHandle.of(cited, SOMEWHERE),
                    new SourceRendering(SourceNameResolver.identity(), SourceLayouts.NONE), null);

            assertEquals(AdequacyReport.schemaRuleKind(each),
                    said.substring(0, said.indexOf('@')),
                    () -> "the word a reader is shown and the word a document groups by, for "
                            + each);
            assertEquals(AdequacyReport.schemaRuleKind(each),
                    PublishedRuleKind.of(written).word(),
                    () -> "and the word the handle is built from is that same word, for " + each);
            // And the word a diagnostic says of the rule, which is a third surface: a reader met by
            // a sentence about a comparison and sent to a handle that calls it something else is
            // reading about two rules.
            assertEquals(PublishedRuleKind.of(written).word(), written.whatItIs(),
                    () -> "and the word a diagnostic says of it, for " + each);
        }
    }

    /**
     * And a rule the author named is shown that name, which is nobody's word but theirs.
     *
     * <p>The other half of the seal, so that the check above is not passing because every kind of
     * rule reached this test as one with no name.
     */
    @Test
    void aRuleTheAuthorNamedIsShownTheirName() {
        for (RuleRef each : everyKind()) {
            if (!(each instanceof RuleRef.Named named)) {
                continue;
            }
            RuleCitation cited = new RuleCitation.Named(named);

            assertEquals(named.citedName(), RuleHandleProse.said(
                            PublishedRuleHandle.of(cited, SOMEWHERE),
                            new SourceRendering(SourceNameResolver.identity(),
                                    SourceLayouts.NONE), null),
                    () -> "a rule with a name is cited by it: " + each);
            assertTrue(!named.citedName().isBlank(),
                    () -> "and there is a name to cite it by: " + each);
        }
    }
}
