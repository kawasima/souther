package souther.compiler.inputs;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import souther.compiler.check.RuleCitation;
import souther.compiler.types.WrittenOwner;
import souther.compiler.check.RuleRef;
import souther.compiler.diag.Citation;
import souther.compiler.diag.SourcePos;
import souther.compiler.observe.RunSensitivity;
import souther.compiler.partition.ReportedReason;
import souther.compiler.partition.UndividedPosition;
import souther.compiler.types.SourceConstruct;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.values.UnreadReason;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Every way a reading comes back without a line, and what each of them leaves behind, in one table.
 *
 * <p>The seal already refuses a reason nobody answered for: {@link BlockReason.RuleReadingStopped}
 * switches over its members with no {@code default}, so a fourteenth is a build failure. What it
 * does not refuse is a reason moved from one arm to another. Everything keeps compiling when a
 * reason changes which half it is in or which word a document writes for it, and every sentence
 * built on the old answer goes on being written and is now wrong.
 *
 * <p>Which is not hypothetical here. The word is what a person is shown about their own model, and
 * two of these are opposite sentences about this compiler under words a reader cannot tell apart —
 * a reason moved between them sends an author to change a rule this compiler read perfectly well.
 *
 * <p>So the answers are here, written out, and this is where they are changed. A reason that moves
 * fails this test and is meant to: what it is asking is not whether the code is right but whether
 * the person moving it meant to move it.
 */
class WhatEachWayOfDrawingNoLineLeavesIsWrittenDownOnceTest {

    /**
     * Every rule this compiler read and drew no line from, and what it leaves.
     *
     * <p>Written as {@code word/sensitivity}: the word a document writes for the reason, and whether
     * a run of this compiler that allows more could get past it.
     *
     * <p>The last column is {@code -} for exactly those, and that is not a spare answer. A rule
     * read from end to end weakens no measurement, so it is in neither of the two capabilities that
     * answer {@code runSensitivity} — and a reason answering for a measure it does not weaken is an
     * answer a report could reach for.
     *
     * <p>What a measure is short of is not among the columns, and cannot be: that is a question
     * about the rule, and which of these reasons a finding carries says only what became of the
     * reading.
     */
    private static Map<String, String> theRulesWithNoLine() {
        Map<String, String> table = new LinkedHashMap<>();
        // Read partway. What the rule would have divided or bounded is exactly the part that was
        // not read, so neither measure knows what it is missing and both are short.
        //
        // And nothing was compared against a figure in any of these four: a form nothing takes
        // apart, values no line can be drawn on, a rule about a value made from this one and a
        // value rule in a form nothing read are met again however much a run is allowed.
        table.put("UnreadComparisonForm", "UNSUPPORTED_SYNTAX/UNAFFECTED");
        table.put("UnreadComparisonDomain", "UNSUPPORTED_DOMAIN/UNAFFECTED");
        table.put("RuleAboutADerivedValue",
                "RULE_ABOUT_A_DERIVED_VALUE/UNAFFECTED");
        // And a rule about an element of one of several sequences, which is the same two measures
        // short for a reason of its own: the rule was read and which of the sequences it is about
        // is what nothing here works out, so what it divides and what it bounds are unknown at
        // each of them. A run allowed more meets it again — nothing was compared against a figure.
        table.put("RuleAboutAnElementOfSeveralSequences",
                "RULE_ABOUT_AN_ELEMENT_OF_SEVERAL_SEQUENCES/UNAFFECTED");
        table.put("UnreadValueRule", "UNSUPPORTED_SYNTAX/UNAFFECTED");
        // An end a choice in the rule left open. Its own word and not the one above: the rule at
        // this position was read, and what an author acts on is the branch written beside it. A run
        // allowed more meets it again — the reading of ends stopped on a form and not at a figure.
        table.put("EndLeftOpenByAChoice", "UNREAD_ALTERNATIVE_OF_A_CHOICE/UNAFFECTED");
        // A pattern read to the end and larger than this will make a machine of. Both measures are
        // short because both are read off the set it names: a class is a part of it and an end is
        // where it stops. Its own word and not the one above — that one sends an author after the
        // form their rule is written in, and here the form was read and the rule is simply large.
        //
        // What is not here is the answer nobody could work out, which leaves the position short of
        // the same two things and is not a rule without a line. This table is what a rule leaves,
        // and that one is not about a rule.
        //
        // The two figures a rule is compared against, and the only two rows here a wider run may
        // get past: the states a pattern is built into, and how deeply one may be bracketed.
        // Neither figure is one a caller can set today, which is not the question — what a wider
        // run is, is the allowances widened, and whether a knob exists for one is a fact about
        // which knobs exist this month.
        table.put("PatternTooCostly", "EXACT_VALUES_TOO_COSTLY/MAY_CHANGE");
        // And a rule this would not read that far in, which is short of both for the same reason:
        // what the rule says is unknown, so what it would have divided or bounded is unknown too.
        // Its own word and not the one above — that one reached the values and this did not.
        table.put("PatternTooDeeplyNested",
                "PATTERN_TOO_DEEPLY_NESTED/MAY_CHANGE");
        // And the third figure, which is the further work of asking where the strings a rule was
        // read to actually stop. One word with `PatternTooCostly` because out there both are the
        // values coming out wider than the rules leave them; its own row because what was too much
        // is a machine nobody wrote, and an author sent after their pattern would find one this
        // read perfectly.
        table.put("OrderedExtentTooCostly", "EXACT_VALUES_TOO_COSTLY/MAY_CHANGE");
        // And a position that could not hand its rules on as the sets they leave, which is the one
        // row here that names no rule: the sets are made as a group out of one allowance, so a rule
        // cheap enough on its own goes unmade beside one that was not, and a reader asking which of
        // them was too much would be told about whichever the building reached last. One word with
        // `PatternTooCostly` out there, where both are a set the rules name not being worked out.
        table.put("RulesNotHandedOnAsSets", "EXACT_VALUES_TOO_COSTLY/MAY_CHANGE");
        // The same shape of row and its own word, which is the point of it. That one is a position
        // that could not hand on what its declarations leave, and this is a behavior's own rules
        // about the position not being told apart — so a position answered to the letter by its
        // declaration still lands here, and an author given that word would go and simplify a
        // declaration that was never the matter. A group again, so no rule is named as the
        // expensive one, and a run allowed more may get past it.
        table.put("BehaviorDistinctionsTooCostly", "BEHAVIOR_DISTINCTIONS_TOO_COSTLY/MAY_CHANGE");
        // One word with `ComparisonBetweenPositions` below, and on purpose: they are the two
        // readings of `a < b`, opposite sentences about this compiler, and a document promises
        // its reader which kind of thing stopped a derivation rather than which reader stopped.
        table.put("ValueRuleRelatingTwoPositions",
                "UNSUPPORTED_PARTITION_SHAPE/UNAFFECTED");
        // Read to the end, and placed nowhere. Its own word beside the two above: the comparison
        // was taken apart, a line came out of it and every name it is between reached positions —
        // what was not reached is which of those positions the line runs between. Both measures are
        // short, because a row either side of the line is owed and there is nowhere to ask for one.
        table.put("CasePairingNotDetermined", "UNRESOLVED_CASE_PAIRING/UNAFFECTED");
        // Read to the end. Whatever the rule places has been placed, and there is none to be owed —
        // and no measurement is weakened, so from here down there is no sensitivity to answer.
        table.put("ComparisonCuttingNothing", "RULE_CUTS_NOTHING/-");
        table.put("ComparisonCuttingOutsideDomain",
                "RULE_CUTS_OUTSIDE_WHAT_THE_QUANTITY_HOLDS/-");
        // Its own word beside the one above. There the declarations never run as far as the line,
        // wherever the rule stands; here they do, and the conditions on the way to the comparison
        // rule the line's values out. Read to the end either way, and nothing is owed: the classes
        // the line would make hold nothing that arrives, which is a fact about the model.
        table.put("ComparisonNothingArrivesAtItsLine",
                "NOTHING_ARRIVES_AT_THE_RULES_LINE/-");
        table.put("ComparisonBetweenPositions", "UNSUPPORTED_PARTITION_SHAPE/-");
        // Its own word beside the one above, because what a reader does about it differs: a rule
        // between two positions is waiting on a class about the pair, and a rule about what the
        // values at one come to has nothing to wait for — the position has no class from it and
        // its border is drawn.
        table.put("ComparisonOverARun", "RULE_ABOUT_A_RUN/-");
        // A row of its own, and here is what it is for. The three above are rules that leave the
        // position where they found it: the quantity is empty, the line falls outside it, or the
        // number is over a run. This one holds the position to the values it admits, and everything
        // else is refused at construction — so what a reader acts on is that the value written here
        // is one of them. Read as one of the three, such a position goes out with no rule saying
        // anything about it.
        //
        // Neither measure is short. The rule was read to the end and there is no class away from
        // what it admits for a row to be owed at, so there is nothing to ask an author for.
        table.put("RuleRestrictingToAdmittedValues",
                "POSITION_RESTRICTED_TO_WHAT_A_RULE_ADMITS/-");
        // And its own row beside those four. This one is about the position and was read to the
        // end, and every value the position holds comes out one side of it — so unlike the first
        // three there is a rule to send an author to, and unlike the fourth the values are left
        // where they were found. Nothing is owed: the rule states no second class for a row to be
        // asked for, and no measurement is weakened by it.
        table.put("PredicateTellingNothingApart", "RULE_TELLS_NOTHING_APART/-");
        // And its own row again, which is the one that says nothing fell short. Every rule about
        // the position was read and every set was worked out; what will not go together is the
        // classes, because a line on the order and a set of the values are two vocabularies and a
        // class in one cannot be written in the other. So no measure is weakened by a wider run,
        // and nothing is owed on its account — the position simply has no classes here.
        table.put("ClassesNotComposed", "CLASSES_NOT_COMPOSED/-");
        return table;
    }

    /**
     * And every way the reading never got to a rule at all, which names a position and no rule.
     *
     * <p>Apart from the above because there is nothing to ask them: a reason with no rule behind it
     * has no {@code leavesShort} to answer, and the position it is about is short for both measures
     * by the position rather than by anything written about it.
     */
    private static Map<String, String> theStopsAtAPosition() {
        Map<String, String> table = new LinkedHashMap<>();
        table.put("TypeUnresolved", "TYPE_UNRESOLVED/UNAFFECTED");
        table.put("RecursiveExpansion",
                "RETURNS_TO_A_DECLARATION_ALREADY_READ/UNAFFECTED");
        table.put("UnsupportedTraversal", "UNSUPPORTED_TRAVERSAL/UNAFFECTED");
        table.put("ValueRulesNotReached", "RULES_NOT_READ_AT_ALL/UNAFFECTED");
        // The two rows this pair is for. One word out there and two reasons in here: a document
        // promises a reader the hole under the position and not which figure this compiler stopped
        // at on the way, and a reader of a measure asks whether a wider run would get past it —
        // which the word cannot answer, because it covers both. Held as one reason, a walk stopped
        // by the fields it could afford to seed was reported alongside a clause nothing could type.
        table.put("ValueRulesNotReachedPastDepthLimit", "RULES_NOT_READ_AT_ALL/MAY_CHANGE");
        return table;
    }

    /**
     * And every reason that is neither of those, which is an answer nothing built.
     *
     * <p>Its own table because the two above are answers about a line: what a rule with none of one
     * leaves, and what a position nothing was reached at leaves. An answer larger than the
     * allowance is neither — it names no rule for a line to be missing from, and every rule of the
     * position did arrive — so it has no {@code leavesShort} to answer and no position to be the
     * account of.
     *
     * <p>What it does have is a word a document writes, which is why it is here at all: the whole
     * of the coarsening is meant to be reviewable in one place, and a reason with a capability of
     * its own would otherwise be projected where nothing reads the collapse back.
     */
    private static Map<String, String> theReasonsInNeitherHalf() {
        Map<String, String> table = new LinkedHashMap<>();
        // Here because it is in neither capability, which is the fact rather than an oversight. It
        // is not a rule without a line — it is about no rule — and it is not a position whose rules
        // were never reached, since every one of them arrived and was understood. What was not
        // worked out is what they leave between them.
        //
        // One word with `PatternTooCostly` all the same. Out there the two are the same kind of
        // thing: the values are wider than the rules leave them because working them out was too
        // much. Which of them it was decides whether a rule can be named, and that is this
        // compiler's question rather than a promise the document makes.
        table.put("ExactValuesTooCostly", "EXACT_VALUES_TOO_COSTLY/MAY_CHANGE");
        return table;
    }

    /**
     * The table, held against what the reasons answer.
     *
     * <p>Every member of the seal is here: the map is built from the reasons themselves, so one
     * added and left out of the table above comes back with no expectation beside it and fails.
     */
    @Test
    void everyRuleWithNoLineSaysWhatItLeavesAndWhatItIsCalled() {
        Map<String, String> said = new LinkedHashMap<>();
        for (BlockReason.RuleWithoutLineReason each : everyRuleWithoutALine()) {
            said.put(each.getClass().getSimpleName(),
                    ReportedReason.of((BlockReason) each).name()
                            + "/" + sensitivityOf((BlockReason) each));
        }

        assertEquals(theRulesWithNoLine(), said);
    }

    /** The same of the reasons that name a position and no rule. */
    @Test
    void everyStopAtAPositionSaysWhatItIsCalled() {
        Map<String, String> said = new LinkedHashMap<>();
        for (BlockReason.AboutThePosition each : everyStopAtAPosition()) {
            said.put(each.getClass().getSimpleName(),
                    ReportedReason.of(each).name() + "/" + sensitivityOf(each));
        }

        assertEquals(theStopsAtAPosition(), said);
    }

    /** And of the reasons that are in neither of those capabilities. */
    @Test
    void everyReasonInNeitherHalfSaysWhatItIsCalled() {
        Map<String, String> said = new LinkedHashMap<>();
        for (BlockReason each : theOtherReasons()) {
            said.put(each.getClass().getSimpleName(),
                    ReportedReason.of(each).name() + "/" + sensitivityOf(each));
        }

        assertEquals(theReasonsInNeitherHalf(), said);
    }

    /**
     * The two reasons a position's rules go unread reach the reasons that tell them apart.
     *
     * <p>The join between this table and the one over {@code RulesMissed}, which is written in
     * neither of them. That one says which {@code UnreadReason} a way of missing a rule comes to
     * and stops; this one says what a reason answers about a wider run. Without the step between,
     * both could be right while the projection sent the depth to the reason that says no allowance
     * changes it.
     */
    @Test
    void aReadingsAccountOfNeverReachingAPositionKeepsWhichOfThemItWas() {
        assertEquals(RunSensitivity.MAY_CHANGE,
                BlockReason.of(UnreadReason.NOT_REACHED_PAST_DEPTH_LIMIT).runSensitivity(),
                "a walk stopped by the depth it could afford is one a wider run reads past");
        assertEquals(RunSensitivity.UNAFFECTED,
                BlockReason.of(UnreadReason.NOT_REACHED).runSensitivity(),
                "and every other way of never reaching a position is met again");
    }

    /**
     * Whether a wider run could get past {@code reason}, asked of whichever capability holds it.
     *
     * <p>Asked of the reason and never of the word a document writes for it, which is what makes
     * the fourth column worth having. {@code UNSUPPORTED_PARTITION_SHAPE} is one word over a rule
     * this read partway and a rule it read to the end, so a sensitivity worked out from the word
     * would have to be the same for both — and it is not; the same is now true of
     * {@code RULES_NOT_READ_AT_ALL}.
     *
     * <p>{@code -} where neither capability holds it, which is the answer and not a missing one:
     * those reasons weaken no measurement, so nothing may ask them.
     */
    private static String sensitivityOf(BlockReason reason) {
        return reason instanceof BlockReason.ReadingStopReason stopped
                ? stopped.runSensitivity().name() : "-";
    }

    /**
     * A finding is a finding whichever half its reason is in, and nothing here decides a measure.
     *
     * <p>What a reason says is what became of the reading, and a reader is owed that either way. It
     * does not say whether a measure stays open: that is a question about the rule, raised by
     * whatever classifies the rule, and a reading that stopped is neither the only way to raise one
     * nor by itself enough to.
     */
    @Test
    void aReasonSaysWhatBecameOfTheReadingAndNotWhatAMeasureIsShortOf() {
        for (BlockReason.RuleWithoutLineReason each : everyRuleWithoutALine()) {
            RulesWithNoLine.Gathered gathered = new RulesWithNoLine.Gathered();
            gathered.add(new RuleCitation.WrittenAt(
                            new RuleRef.Comparison("b",
                                    new SourceConstructOrigin(new WrittenOwner.Body("m", "b"),
                                            1, 1, SourceConstruct.IF)),
                            Citation.of(new SourcePos(1, 1))),
                    new FilingCoordinate.AtPosition(TermPath.of("x")), each);
            RulesWithNoLine filed = gathered.found();

            assertEquals(1, filed.reported().size(), each.getClass().getSimpleName());
            assertEquals(each instanceof BlockReason.ReadToEndWithoutLine ? 1 : 0,
                    filed.modelStatements().size(),
                    () -> each.getClass().getSimpleName()
                            + ": what the model states is asked of the reason, not of what a report"
                            + " prints");
            assertEquals(List.of(), filed.unclassified(),
                    () -> each.getClass().getSimpleName()
                            + ": a finding raises no question by having been filed");
        }
    }

    /**
     * Every member of the seal is in the lists above.
     *
     * <p>The lists are written out rather than found by reflection, because what this test is about
     * is the answers and a list that built itself from the reasons would move with them. That is
     * what this check is for: written out, a list is a thing to forget, and a fourteenth reason
     * added and answered for in {@code leavesShort} would leave the rows above passing over
     * thirteen.
     */
    @Test
    void everyReasonThereIsHasARowAbove() {
        java.util.Set<String> written = new java.util.LinkedHashSet<>();
        written.addAll(theRulesWithNoLine().keySet());
        written.addAll(theStopsAtAPosition().keySet());
        written.addAll(theReasonsInNeitherHalf().keySet());

        assertEquals(reasons(BlockReason.class), written,
                "a reason a document has a word for, and no row saying which word");
    }

    /**
     * Every reason there is, which is what {@link ReportedReason} is asked about.
     *
     * <p>Read from {@link BlockReason} itself and down through whatever seals stand under it,
     * rather than from the capabilities. The capabilities are what is true of a reason and a reason
     * may be in more than one of them, so a list per capability is a list per way of being asked —
     * and a reason in a capability nobody enumerated is one this compiler projects to a published
     * word with nothing reading the collapse back. Which is what happened: a reason moved out of
     * both capabilities kept its projection and left the tables passing.
     *
     * <p>What the switch already refuses is a reason with no word at all. What it cannot refuse is
     * a word whose promise the reason does not meet, and that is the whole of what the rows are
     * for.
     */
    private static java.util.Set<String> reasons(Class<?> seal) {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        for (Class<?> each : seal.getPermittedSubclasses()) {
            if (each.isSealed()) {
                out.addAll(reasons(each));
            } else {
                out.add(each.getSimpleName());
            }
        }
        return out;
    }

    /**
     * One of each reason there is, and nothing about which capability any of them is in.
     *
     * <p>The list is here to make a value of each, because a reason with an argument cannot be made
     * from its class alone. Which capabilities one is in is the reason's own answer and is asked of
     * the type below — written out per capability, the lists are a second declaration of
     * membership, and a reason that gained one would keep whichever list it was put in.
     *
     * <p>That it holds every reason is not this list's word either: {@link
     * #everyReasonThereIsHasARowAbove} reads the seal.
     */
    private static List<BlockReason> everyReason() {
        return List.of(
                new BlockReason.UnreadComparisonForm(),
                new BlockReason.UnreadComparisonDomain(),
                new BlockReason.RuleAboutADerivedValue(),
                new BlockReason.RuleAboutAnElementOfSeveralSequences(),
                new BlockReason.UnreadValueRule(),
                new BlockReason.EndLeftOpenByAChoice(),
                new BlockReason.PatternTooCostly(),
                new BlockReason.PatternTooDeeplyNested(),
                new BlockReason.OrderedExtentTooCostly(
                        souther.compiler.regex.Meter.Stopped.ONE_MACHINE),
                new BlockReason.ExactValuesTooCostly(),
                new BlockReason.BehaviorDistinctionsTooCostly(),
                new BlockReason.RulesNotHandedOnAsSets(),
                new BlockReason.ValueRuleRelatingTwoPositions(),
                new BlockReason.CasePairingNotDetermined(),
                new BlockReason.ComparisonCuttingNothing(),
                new BlockReason.ComparisonCuttingOutsideDomain(),
                new BlockReason.ComparisonNothingArrivesAtItsLine(),
                new BlockReason.ComparisonBetweenPositions(),
                new BlockReason.ComparisonOverARun(),
                new BlockReason.RuleRestrictingToAdmittedValues(),
                new BlockReason.PredicateTellingNothingApart(),
                new BlockReason.ClassesNotComposed(),
                new BlockReason.TypeUnresolved(),
                new BlockReason.RecursiveExpansion(
                        souther.compiler.types.TypeSymbols.declared(
                                new souther.compiler.types.TypeKey("g", "Chain")),
                        TermPath.of("c")),
                new BlockReason.UnsupportedTraversal(BlockReason.Traversal.MAPPING_CONTENT),
                new BlockReason.ValueRulesNotReached(),
                new BlockReason.ValueRulesNotReachedPastDepthLimit());
    }

    /** Those of them that are rules with no line, asked of each rather than listed. */
    private static List<BlockReason.RuleWithoutLineReason> everyRuleWithoutALine() {
        return everyReason().stream()
                .filter(BlockReason.RuleWithoutLineReason.class::isInstance)
                .map(BlockReason.RuleWithoutLineReason.class::cast).toList();
    }

    /**
     * A surface of the document admits the words its own reasons reach, and no others.
     *
     * <p>Two surfaces, and each is fed by the capabilities its producers hold. An entry about a
     * position is written from a rule that came to no line and from a stop at the position itself;
     * a question's is written from what leaves a question standing. Neither set of reasons contains
     * the other: a type nothing could work out is a stop at a position and raises no question, and
     * an answer larger than the allowance leaves a question standing and is no rule a position's
     * entry names.
     *
     * <p>Their words are another matter, and the words are what a document promises. An answer
     * larger than the allowance and a pattern larger than one machine are one word out there, and
     * the second is a rule with no line — so the question's vocabulary comes out inside the
     * position's while the reasons behind them do not. Which is why each surface is held to its own
     * producers: read off the other, either would be right about a word its own reasons never
     * reach.
     */
    @Test
    void eachSurfaceAdmitsTheWordsItsOwnReasonsReach() throws Exception {
        JsonNode schema = schema();

        java.util.Set<String> aPosition = new java.util.LinkedHashSet<>(
                projected(everyRuleWithoutALine()));
        aPosition.addAll(projected(everyStopAtAPosition()));
        // The walk stopping after a count of steps, which nothing writes any more. A word of this
        // surface and of no other: documents of this version carry it where a position's reading
        // ran out, and the question's vocabulary is new in this version and never carried it.
        aPosition.add("depth_limit");

        assertEquals(aPosition, words(schema, "notReadReason"),
                "an entry about a position admits what a position's readings can be short of");
        assertEquals(projected(everyRuleReadingStopped()),
                words(schema, "ruleStoppedReadingReason"),
                "what a question's rule left admits what a reading can stop on");
        assertEquals(projected(everyLimitAQuestionCanStandOn()),
                words(schema, "answerRealizationStoppedReason"),
                "and what its position was short of admits what an answer a question waited on can"
                        + " be short of");
    }

    /**
     * And every word a document has is one some reason reaches.
     *
     * <p>Beside the two above, which are about which surface may carry a word. This is about the
     * words themselves: one no reason projects to is a promise to a reader that nothing can keep,
     * and the surfaces would both be right about it by neither of them admitting it.
     */
    @Test
    void everyPublishedWordIsOneSomeReasonReaches() {
        assertEquals(java.util.Arrays.stream(
                                souther.compiler.partition.UndividedPosition.Reason.values())
                        .map(each -> each.name().toLowerCase(java.util.Locale.ROOT))
                        .collect(java.util.stream.Collectors
                                .toCollection(java.util.LinkedHashSet::new)),
                projected(everyReason()),
                "a word a document may carry that no reason comes to");
    }

    /** The words {@code these} come to, which is what a document writes for each of them. */
    private static java.util.Set<String> projected(List<? extends BlockReason> these) {
        return these.stream().map(each -> ReportedReason.of(each).name())
                .map(java.lang.String::toLowerCase)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    /** The words the schema allows at one of its definitions, following what it is written as. */
    private static java.util.Set<String> words(JsonNode schema, String def) {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        JsonNode node = schema.get("$defs").get(def);
        assertNotNull(node, "the schema has no " + def);
        if (node.has("enum")) {
            node.get("enum").forEach(each -> out.add(each.asString()));
        }
        // A surface whose words are exactly another's is written as that one and not as a copy of
        // it. Read only through `anyOf`, such a surface came back with no words at all and the
        // vocabularies were held equal by both of them being empty.
        if (node.has("$ref")) {
            out.addAll(words(schema, node.get("$ref").asString().substring("#/$defs/".length())));
        }
        if (node.has("anyOf")) {
            for (JsonNode each : node.get("anyOf")) {
                if (each.has("const")) {
                    out.add(each.get("const").asString());
                }
                if (each.has("enum")) {
                    each.get("enum").forEach(word -> out.add(word.asString()));
                }
                if (each.has("$ref")) {
                    out.addAll(words(schema,
                            each.get("$ref").asString().substring("#/$defs/".length())));
                }
            }
        }
        return out;
    }

    private static JsonNode schema() throws Exception {
        try (java.io.InputStream in = souther.compiler.report.AdequacyReport.class
                .getResourceAsStream(
                        souther.compiler.report.AdequacyReport.SCHEMA_RESOURCE)) {
            assertNotNull(in, "the schema ships beside the compiler");
            return JsonMapper.builder().build().readTree(
                    new java.lang.String(in.readAllBytes(),
                            java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    /**
     * And those in neither, which is what a question is left standing by and no surface about a
     * position can carry.
     *
     * <p>Asked of the two capabilities and not named. A reason that gained one of them belongs in
     * the list beside this and would keep its row here — so the rows would go on saying what a
     * document writes for it while what may carry it had moved, which is the whole of what the
     * schema's two vocabularies are held against.
     */
    private static List<BlockReason> theOtherReasons() {
        return everyReason().stream()
                .filter(each -> !(each instanceof BlockReason.RuleWithoutLineReason))
                .filter(each -> !(each instanceof BlockReason.AboutThePosition))
                .toList();
    }

    /**
     * What a value reading's reason is a fact about decides which capability it arrives in, and the
     * two agree in both directions.
     *
     * <p>Enumerated from the reasons rather than listed. {@link UnreadReason#about()} is the one
     * place the classification is taken, and what this holds is that the projection into these
     * reasons keeps it: a reason about a rule reaches one a rule is named for, a reason about the
     * answer reaches one that names none and still leaves a question standing, and a reason about
     * neither reaches a stop at a position, which raises no question for anything to stand on.
     *
     * <p>Both directions, because one of them alone is satisfiable by a projection that sends
     * everything to one arm. A reason added to the vocabulary fails here rather than arriving at
     * whichever arm was nearest.
     */
    @Test
    void whatAReasonIsAboutDecidesWhichCapabilityItArrivesIn() {
        Map<String, String> arrived = new LinkedHashMap<>();
        for (UnreadReason why : UnreadReason.values()) {
            arrived.put(why.name(), capabilityOf(BlockReason.of(why)));
        }

        Map<String, String> expected = new LinkedHashMap<>();
        for (UnreadReason why : UnreadReason.values()) {
            expected.put(why.name(), switch (why.about()) {
                case A_RULE -> "RuleReadingStopped";
                case THE_ANSWER -> "AnswerRealizationStopped";
                case NEITHER -> "AboutThePosition";
            });
        }

        assertEquals(expected, arrived);
    }

    /** Which of the three a reason is, asked of the reason and not of where it was written. */
    private static String capabilityOf(BlockReason.ReadingStopReason reason) {
        return switch (reason) {
            case BlockReason.RuleReadingStopped _ -> "RuleReadingStopped";
            case BlockReason.AnswerRealizationStopped _ -> "AnswerRealizationStopped";
            case BlockReason.AboutThePosition _ -> "AboutThePosition";
        };
    }

    /**
     * And a question a rule raised is left standing by the first two of those and by neither of the
     * third.
     *
     * <p>The outlet, asked of every reason there is. A rule this reading gave up on and an answer
     * it could not build both leave the question where they found it; a reading that never arrived
     * at the position raises no question, so one of those reaching a question would be an account
     * taken from a place nothing looked at.
     */
    @Test
    void aQuestionIsLeftStandingByEverythingButAStopAtAPosition() {
        for (UnreadReason why : UnreadReason.values()) {
            if (why.about() == UnreadReason.About.NEITHER) {
                assertThrows(IllegalArgumentException.class,
                        () -> BlockReason.ofAQuestionStandingOn(why),
                        () -> why + " reached no rule, so no question of one stands on it");
            } else {
                assertEquals(BlockReason.of(why), BlockReason.ofAQuestionStandingOn(why),
                        () -> why + " leaves a question standing, and says the same thing there");
            }
        }
    }

    /**
     * What a question stands on reaches a document as two, and each is under the order that can
     * answer for it.
     *
     * <p>The parts of the rule have a written place and keep it. A limit the position's answer ran
     * into has none — the same rules met in another order would have been built — so it stands
     * under no order beside them, and a sequence across the two would publish a precedence read off
     * which of this compiler's stores a reason came out of.
     *
     * <p>Sorted on the capability, so the placing is the reason's own answer rather than a
     * convention whoever assembled the list knew.
     */
    @Test
    void whatAQuestionStandsOnIsSaidAsTheTwoOrdersThatAnswerForIt() {
        WhatAQuestionStandsOn said = new WhatAQuestionStandsOn(
                RuleReasons.from(List.of(
                        new RuleReasons.Placed(new SourcePos(1, 1),
                                new BlockReason.UnreadComparisonDomain()),
                        new RuleReasons.Placed(new SourcePos(1, 9),
                                new BlockReason.UnreadValueRule()))),
                Optional.of(new BlockReason.ExactValuesTooCostly()));

        assertEquals(List.of(UndividedPosition.Reason.UNSUPPORTED_DOMAIN,
                        UndividedPosition.Reason.UNSUPPORTED_SYNTAX),
                ReportedReason.wordsFor(said.itsRuleLeft()).written(),
                "the parts of the rule, in the order they were written");
        assertEquals(Optional.of(UndividedPosition.Reason.EXACT_VALUES_TOO_COSTLY),
                said.itsPositionWasShortOf().map(ReportedReason::of),
                "and what the position's answer was short of, on its own");
    }

    /**
     * And a second limit a question can stand on is a decision somebody takes.
     *
     * <p>{@code answerStopped} is one word and not an array, because there is one such limit. A
     * second would be a pair with no order between them either, and what a document writes for a
     * pair is a decision to take when there is one to take — taken by silence, it would be taken by
     * whichever a walk met first.
     *
     * <p><b>Counted from what can reach the field and not from the capability.</b>
     * {@link BlockReason.AnswerRealizationStopped} holds what an answer this compiler was building
     * ran out on, wherever that answer was being built; a question of a rule stands on the half of
     * it a value reading records, which is what {@link UnreadReason} has words for. A position that
     * could not hand its rules on as sets is the other half — it is a line a reader is owed at the
     * place, and no rule raises a question of it — so counting the capability would have this
     * asking for a decision about a field that reason cannot reach.
     */
    @Test
    void aSecondLimitAQuestionCanStandOnIsADecisionSomebodyTakes() {
        assertEquals(List.of(UnreadReason.EXACT_VALUES_TOO_COSTLY),
                Arrays.stream(UnreadReason.values())
                        .filter(each -> each.about() == UnreadReason.About.THE_ANSWER).toList(),
                "a second one is a decision about what a document writes, and this is where it"
                        + " comes up for taking");
    }

    /**
     * Those of them a question stands on because a reading stopped on a part of its rule, and those
     * it stands on because the position's answer was not built.
     *
     * <p>Two lists because they are two surfaces. A question's account is written as what the parts
     * of the rule left, in the order they were written, and what its position was short of beside
     * it under no order at all — so a word each of them admits is a word the other need not, and a
     * check over their union would pass on either of them holding a word only the other reaches.
     */
    private static List<BlockReason.RuleReadingStopped> everyRuleReadingStopped() {
        return everyReason().stream()
                .filter(BlockReason.RuleReadingStopped.class::isInstance)
                .map(BlockReason.RuleReadingStopped.class::cast).toList();
    }

    /**
     * The other half a question stands on, which names no rule.
     *
     * <p>From what reaches the field and not from {@link BlockReason.AnswerRealizationStopped}. That
     * capability holds what an answer this compiler was building ran out on, wherever it was being
     * built — a position that could not hand its rules on as sets is one, and it is a line a reader
     * is owed at the place rather than anything a rule raises a question of. What a question waits
     * on is the half a value reading records, which is what {@link UnreadReason} has words for.
     */
    private static List<BlockReason.AnswerRealizationStopped> everyLimitAQuestionCanStandOn() {
        List<BlockReason.AnswerRealizationStopped> out = new java.util.ArrayList<>(
                Arrays.stream(UnreadReason.values())
                        .filter(each -> each.about() == UnreadReason.About.THE_ANSWER)
                        .map(BlockReason::ofAQuestionStandingOn)
                        .map(BlockReason.AnswerRealizationStopped.class::cast).toList());
        // And every other member of the capability, which is where this used to be short. The
        // surface a document writes one of these into is the capability's, so a reason that
        // implements it and is not here is a word a question may carry and the schema refuses —
        // read off one producer, the population was whatever that producer happened to make.
        everyReason().stream()
                .filter(BlockReason.AnswerRealizationStopped.class::isInstance)
                .map(BlockReason.AnswerRealizationStopped.class::cast)
                .filter(each -> out.stream().noneMatch(had -> had.getClass() == each.getClass()))
                .forEach(out::add);
        return out;
    }

    /** And those of them that name a position and no rule. */
    private static List<BlockReason.AboutThePosition> everyStopAtAPosition() {
        return everyReason().stream()
                .filter(BlockReason.AboutThePosition.class::isInstance)
                .map(BlockReason.AboutThePosition.class::cast).toList();
    }
}
