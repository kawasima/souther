package souther.compiler.partition;

import souther.compiler.inputs.AuthoredOrder;
import souther.compiler.inputs.BlockReason;
import souther.compiler.inputs.RuleReasons;
import souther.compiler.inputs.RuleSite;
import souther.compiler.publish.SourceOrdered;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The word an adequacy document writes for a reason a derivation stopped.
 *
 * <p>Between the two vocabularies and belonging to neither. {@link BlockReason} records what this
 * compiler could not do and does not know how it is reported; {@link UndividedPosition.Reason} is
 * what a document promises its reader they can tell apart and does not know what produced it. A
 * projection owned by either would be that one reaching into the other, which is the direction the
 * split was made to stop.
 *
 * <p>So a second surface — a diagnostic that says more than the document does — is another
 * projection beside this one rather than a change to what a reason is.
 */
public final class ReportedReason {

    /**
     * The words for what the parts of a rule left a question standing on, in the order they were
     * written.
     *
     * <p><b>Carried and not claimed.</b> The order arrives already said — it was said where a
     * reading's own record of a clause was still in hand — and this maps each member to the word a
     * document writes. Handed a bare list instead, this stated an order it had nothing to see: it
     * was right while every member came from one producer, and stopped being right when a second
     * arrived with nobody in a position to notice.
     *
     * <p>Each projected on its own and the words made distinct afterwards, never the other way
     * round. What a document promises is deliberately coarser than what this compiler records, so
     * two reasons a reader is not offered to tell apart come out as one word — and that is this
     * projection saying they are one thing to lift, rather than a reader dropping one of them.
     */
    public static SourceOrdered<Stop> asWritten(AuthoredOrder<RuleReasons.Said> stopped) {
        return SourceOrdered.carrying(stopped.map(ReportedReason::stop));
    }

    /**
     * One thing a question stands on, in the words a document promises, and where to go about it.
     *
     * <p>The pair and not the word, because the word is deliberately coarser than what produced it
     * and two things to lift can come out under one of them. A clause whose ends two choices left
     * open leaves two, and a list of words says the reader has one thing to do.
     *
     * @param reason what kind of thing stopped the derivation, at the coarseness promised
     * @param sentTo where inside the rule a reader goes about it — the rule itself for a reason
     *               about the whole of it
     */
    public record Stop(UndividedPosition.Reason reason, RuleSite about, RuleSite sentTo) {

        public Stop {
            if (reason == null || about == null || sentTo == null) {
                throw new IllegalArgumentException(
                        "a question stands on some reason, about something, somewhere in its rule");
            }
        }
    }

    private static Stop stop(RuleReasons.Said said) {
        return new Stop(of(said.reason()), said.about(), said.sentTo());
    }

    /**
     * The same words, each once, and in no order anybody wrote.
     *
     * <p>Nothing here claims an order. Which of two reasons an author wrote first is a fact about
     * where they wrote them, and nothing that reaches here holds a place — so the claim is made at
     * the one boundary that can ask, out of what it resolved the places to. Made here instead, it
     * would be read off the numbers a construct is identified by, and those are a function of the
     * owner's syntax rather than the order it is written in.
     */
    public static List<Stop> wordsFor(RuleReasons stopped) {
        return distinct(stopped.said());
    }

    /**
     * The words alone, each once, for a reader that has no use for where to go about them.
     *
     * <p>Bounded by the vocabulary rather than by what an author wrote, which is why the scan of
     * what is already held is kept here and not where the places are: throwing the places away is
     * what makes two entries one word.
     */
    public static List<UndividedPosition.Reason> words(List<Stop> these) {
        List<UndividedPosition.Reason> out = new ArrayList<>();
        for (Stop each : these) {
            if (!out.contains(each.reason())) {
                out.add(each.reason());
            }
        }
        return List.copyOf(out);
    }

    /**
     * These, carrying the claim that they are in the order their author wrote them.
     *
     * <p>Carried and not made. What settles whether a sequence of reasons is in the author's order
     * is where each of them stands, and this holds words; the boundary that resolved the places
     * says it, and this brings the answer across.
     */
    public static Published asTheAuthorWroteThem(AuthoredOrder<Stop> these) {
        return new Published.AsTheAuthorWroteThem(SourceOrdered.carrying(these));
    }

    /** And these, said to be in no order anybody wrote. */
    public static Published inNoAuthoredOrder(List<Stop> these) {
        return new Published.InNoAuthoredOrder(List.copyOf(these));
    }

    /**
     * Each of them once, keeping where it first stood, which is what a coarsening leaves.
     *
     * <p>Told apart by the word and by where it sends a reader. Two producers a document offers one
     * word for are one thing to lift where they are about the same part of the rule, and two where
     * they are not — folded on the word alone, a clause with two choices in it came out as one.
     *
     * <p>Kept in a set, because how many of these there are is how many parts of the rule a reader
     * is sent to and not how many words the vocabulary has. A scan of what is already held was
     * bounded while a member was a word; a member is now a word and a place, so the scan would grow
     * with the choices somebody wrote. {@link Published#words()} keeps its scan for the opposite
     * reason: it throws the places away, so what it holds is bounded by the vocabulary again.
     */
    private static List<Stop> distinct(List<RuleReasons.Said> these) {
        Set<Stop> out = new LinkedHashSet<>();
        for (RuleReasons.Said each : these) {
            out.add(stop(each));
        }
        return List.copyOf(out);
    }

    /**
     * What a document is handed, saying what its order is.
     *
     * <p>A writer that only prints the words asks {@link #written} and prints them. A writer that
     * wants to tell a reader the order is the author's has to say which arm it is holding, which is
     * the whole of what this is for: an array in a document says somebody put it in an order and
     * never says who.
     */
    public sealed interface Published {

        /** What the question stands on, in whatever order this arm answers for. */
        List<Stop> written();

        /** The words alone, each once, for a reader that has no use for where to go about them. */
        default List<UndividedPosition.Reason> words() {
            List<UndividedPosition.Reason> out = new ArrayList<>();
            for (Stop each : written()) {
                if (!out.contains(each.reason())) {
                    out.add(each.reason());
                }
            }
            return List.copyOf(out);
        }

        /** Reasons of one text, in the order that text puts the places they stand on. */
        record AsTheAuthorWroteThem(SourceOrdered<Stop> order) implements Published {

            @Override
            public List<Stop> written() {
                return order.written();
            }
        }

        /**
         * Reasons written across texts, in an order that is steady and is nothing else.
         *
         * <p>Steady so that one compiler over one source writes one document. Not a claim: what
         * settles it is which text this walk reached first, and nothing an author did says a word of
         * one file comes before a word of another.
         */
        record InNoAuthoredOrder(List<Stop> written) implements Published {

            public InNoAuthoredOrder {
                written = List.copyOf(written);
            }
        }
    }

    /**
     * Deliberately coarser than what it is given. What a reader of a document is promised is which
     * kind of thing stopped the derivation, not which capability this compiler is missing this
     * month: three missing traversals are one word, because the model reads the same whichever of
     * them it was.
     *
     * <p>One switch rather than an answer on each case, because a coarsening is only reviewable
     * where the collapses are visible together. No {@code default}, so a reason added and not said
     * stops the compile rather than arriving in a report as the nearest word that already existed.
     */
    public static UndividedPosition.Reason of(BlockReason reason) {
        return switch (reason) {
            case BlockReason.RuleAboutADerivedValue _ ->
                    UndividedPosition.Reason.RULE_ABOUT_A_DERIVED_VALUE;
            case BlockReason.RuleAboutAnElementOfSeveralSequences _ ->
                    UndividedPosition.Reason.RULE_ABOUT_AN_ELEMENT_OF_SEVERAL_SEQUENCES;
            case BlockReason.TypeUnresolved _ -> UndividedPosition.Reason.TYPE_UNRESOLVED;
            case BlockReason.RecursiveExpansion _ ->
                    UndividedPosition.Reason.RETURNS_TO_A_DECLARATION_ALREADY_READ;
            case BlockReason.UnsupportedTraversal _ ->
                    UndividedPosition.Reason.UNSUPPORTED_TRAVERSAL;
            case BlockReason.UnreadComparisonForm _ ->
                    UndividedPosition.Reason.UNSUPPORTED_SYNTAX;
            case BlockReason.UnreadValueRule _ -> UndividedPosition.Reason.UNSUPPORTED_SYNTAX;
            // Its own word, and not the one above. That one promises the rule at this position is
            // written in a form nothing here takes apart, and an author acting on it rewrites a
            // bound that reads perfectly well. What they can act on is the branch beside it.
            // One word for the two readings of one operator. Each says a different thing about it —
            // what may stand at the position, and where those values stop — and which of them a
            // reader is being told is what the section they meet it in says. Two words would ask a
            // reader joining the pair to know that they mean the same operator.
            case BlockReason.EndLeftOpenByAChoice _, BlockReason.ValueRuleLeftOpenByAChoice _ ->
                    UndividedPosition.Reason.UNREAD_ALTERNATIVE_OF_A_CHOICE;
            // Its own word, and not the one above. That one promises a rule is written in a form
            // nothing here takes apart, and what a reader does about it is rewrite the rule. These
            // two say the shape was taken apart and what came of it was more than this compiler
            // builds, so there is no form to rewrite.
            //
            // One word for the two, and they are two reasons on purpose. Which of them it was
            // decides whether a rule can be named — a pattern too large is one somebody wrote, and
            // an answer too large is a product no rule of it is answerable for — and that is a
            // question about what this compiler may say next rather than about what a document
            // promises its reader. Out there both are the same kind of thing: the values are wider
            // than the rules leave them, because working them out was too much.
            // And a third with them, for the same reason and about further work again: where the
            // strings a rule admits stop is asked of machines made out of the ones the rule named,
            // and a limit reached there is the values coming out wider than the rules leave them.
            // Which of the three it was decides what may be said next and not what a reader is
            // promised.
            // And a fourth, which is a position that could not hand its rules on as the sets they
            // leave. Out there it is the same news again: what a rule of the model leaves was more
            // than this compiler would work out. Which of the four it was decides what may be said
            // next and not what a reader is promised.
            case BlockReason.PatternTooCostly _, BlockReason.ExactValuesTooCostly _,
                 BlockReason.OrderedExtentTooCostly _, BlockReason.RulesNotHandedOnAsSets _ ->
                    UndividedPosition.Reason.EXACT_VALUES_TOO_COSTLY;
            // And its own word again, because this one never reached the values at all. A reader
            // told the values were too much would go looking for what makes them so, and what is
            // the matter is how far in the rule goes.
            case BlockReason.PatternTooDeeplyNested _ ->
                    UndividedPosition.Reason.PATTERN_TOO_DEEPLY_NESTED;
            // Its own word, and not the one above. Both are rules this reading did not turn into a
            // line, and a reader acting on them is doing different work: one wants a reader for a
            // form that was seen, and one wants the gathering to reach the rules at all. Collapsed
            // together, a position whose rules nothing had looked at was reported as an expression
            // the terms do not name, which is a cause it was never observed to have.
            // One word for the two, and on purpose. Which figure stopped a walk is this compiler's
            // business: a document promises a reader the hole under the position and not the route
            // this took to it, and a depth it could not afford is a route. The two are apart inside
            // because a reader of a measure asks whether a wider run would get past it, and that is
            // a question a published word does not answer.
            case BlockReason.ValueRulesNotReached _,
                 BlockReason.ValueRulesNotReachedPastDepthLimit _ ->
                    UndividedPosition.Reason.RULES_NOT_READ_AT_ALL;
            case BlockReason.UnreadComparisonDomain _ ->
                    UndividedPosition.Reason.UNSUPPORTED_DOMAIN;
            // Its own word, and not the shape one below. Both sides of this line are read and
            // ordered and a line is drawn on them; what is missing is which positions the line runs
            // between, which is a question about the model and not about the form it was written in.
            case BlockReason.CasePairingNotDetermined _ ->
                    UndividedPosition.Reason.UNRESOLVED_CASE_PAIRING;
            case BlockReason.ComparisonBetweenPositions _ ->
                    UndividedPosition.Reason.UNSUPPORTED_PARTITION_SHAPE;
            // Its own word, because what a reader does about it is different. A rule between two
            // positions is waiting on a class about the pair; a rule about what a run comes to has
            // nothing to wait for — the model divides no position by it, and its border is already
            // drawn.
            case BlockReason.ComparisonOverARun _ ->
                    UndividedPosition.Reason.RULE_ABOUT_A_RUN;
            // Its own word again, and the one furthest from the two above. Those two are rules
            // that leave the position where they found it; this one holds it to what the rule
            // admits, which is a fact a reader acts on — the value written here is one of those.
            case BlockReason.RuleRestrictingToAdmittedValues _ ->
                    UndividedPosition.Reason.POSITION_RESTRICTED_TO_WHAT_A_RULE_ADMITS;
            // The same word, from the other reading of the same rule. What a document promises its
            // reader is which kind of thing stopped the derivation, and a relation between two
            // positions is one kind of thing whether the reading that met it was drawing a line or
            // gathering values — so the split this compiler needs between the two is a split it
            // keeps to itself, and the two vocabularies still come to one word for one rule.
            case BlockReason.ValueRuleRelatingTwoPositions _ ->
                    UndividedPosition.Reason.UNSUPPORTED_PARTITION_SHAPE;
            // Its own word and not the one above. Both are rules this read to the end that divide
            // nothing, and what a reader may go on to do about them differs: one is waiting on a
            // class about two positions, and the other has nothing to wait for.
            case BlockReason.ComparisonCuttingNothing _ ->
                    UndividedPosition.Reason.RULE_CUTS_NOTHING;
            // Its own word beside that one, because what a reader looks at differs. There the rule
            // is about a quantity this position is not in, and an author looks at what it compares;
            // here it is about this position and everything the position holds comes out one side
            // of it, and an author looks at the rule.
            case BlockReason.PredicateTellingNothingApart _ ->
                    UndividedPosition.Reason.RULE_TELLS_NOTHING_APART;
            // Its own word again, and the one that says nothing fell short. Every rule was read and
            // every set was worked out; what will not go together is the classes, so a reader sent
            // after what the rules cost would be looking at something that was never the trouble.
            case BlockReason.ClassesNotComposed _ ->
                    UndividedPosition.Reason.CLASSES_NOT_COMPOSED;
            // And its own word beside the four collapsed above. Those are what a position admits
            // coming out wider than its rules leave it, which is about the declarations; this is
            // about a body, and a position whose declaration was answered exactly still lands here.
            case BlockReason.BehaviorDistinctionsTooCostly _ ->
                    UndividedPosition.Reason.BEHAVIOR_DISTINCTIONS_TOO_COSTLY;
            // And its own word beside that one. A rule with no quantity to cut states nothing about
            // the position; a rule whose line falls outside where its quantity runs states
            // something no row satisfies, and an author reading the first would take the second for
            // a clause they could delete.
            case BlockReason.ComparisonCuttingOutsideDomain _ ->
                    UndividedPosition.Reason.RULE_CUTS_OUTSIDE_WHAT_THE_QUANTITY_HOLDS;
            // And its own word beside that one. There the declarations never run as far as the
            // line, wherever the rule stands; here they do, and what stops short of it is the rows
            // that arrive — a reader of the first looks at one rule against the declarations, and
            // a reader of this one looks at the guards above it.
            case BlockReason.ComparisonNothingArrivesAtItsLine _ ->
                    UndividedPosition.Reason.NOTHING_ARRIVES_AT_THE_RULES_LINE;
        };
    }

    private ReportedReason() {}
}
