package souther.bench;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * One method introduces a measure's answer, and nothing else makes a case of one.
 *
 * <p>Every measure's {@code of} is the rule: what a measure
 * came to follows from what it found and whether its own reading ran out, and those two facts
 * arrive together or not at all. A caller writing {@code new Complete(entries)} decides it from the
 * entries alone, which is the reconstruction the type exists to stop — and is what every reader did
 * while the answer was a list beside a status.
 *
 * <p>The type cannot forbid it. {@code Absent} takes the proof that its own measure's reading ran
 * out, and only the reading can make one; {@code Complete} claims the same thing and carries
 * entries instead, so a case public enough to match on is public enough to write. A proof on
 * {@code Complete} as well would close it, at the price of every fixture that wants a measure made
 * in full needing a reading to have made one — which the tests that fabricate a border to hold a
 * criterion to cannot pay. So this is the whole of what enforces the rule.
 *
 * <p>Which is why it asks {@link Compiled} rather than reading the classes itself. Making a value is
 * three things in bytecode and not one, and a check that knew about {@code new} alone would be
 * passed by a constructor reference — the vocabulary belongs in the one place that has it, not in
 * each check that needs it.
 *
 * <p><b>By the method, with its parameters.</b> The rule names {@code of}. Counted per class, a
 * helper beside it would keep the class's total where it was and answer for nothing; counted per
 * name, an overload would be admitted without anybody deciding it should be.
 */
class AMeasureIsIntroducedInOnePlaceTest {

    /**
     * The top of the hierarchy, so that the arms are counted wherever they sit in it.
     *
     * <p>Asked of {@code Measure} and not of {@code Measurement}: whether there is a question here
     * and how far asking it got are two types since #996, and a check that named the lower one
     * would stop seeing {@code NotApplicable} the moment it moved — which is the case it was
     * written to watch.
     */
    private static final String MEASURE = "souther.compiler.query.Measure";

    /**
     * Every method that makes a state of a measurement, and how many it makes.
     *
     * <p>One entry per measure's introduction rule, and nothing else. Each of the ten measures has
     * one method that decides between the states from what it found and what its reading came to —
     * {@code of} where there is a choice to make, and a named factory for each answer a caller can
     * ask for outright ({@code none}, {@code noRows}, {@code noSubject}, {@code truncated}).
     * A caller writing {@code new Complete(...)} would decide it from the entries alone, which is
     * the reconstruction the type exists to stop.
     *
     * <p><b>By the method, with its parameters.</b> Counted per class, a helper beside one would
     * keep the class's total where it was and answer for nothing; counted per name, an overload
     * would be admitted without anybody deciding it should be.
     *
     * <p>What an authored line came to across the readings of it is not here, and not because
     * nobody makes it. {@code ObligationCoverage} is a type of its own and not a state of a measure:
     * a reading may be made in part and have found a row, and a debt cannot, so the states are not
     * the same states. It has an introduction rule of the same shape — one fold, read by a report, a
     * build's refusal, an editor and the generator — and that rule is its own type's to keep.
     *
     * <p>{@code PairSpace}'s static initialiser makes the one space that is measured in full and
     * holds nothing — a behavior with no pair of positions — and {@code ItemAssessment} makes the
     * answer a point nobody is owed a row at has. Both are answers about the model rather than
     * readings of it, which is why neither goes through a reading.
     */
    private static final Map<String, Integer> INTRODUCED_BY = new LinkedHashMap<>(
            Map.ofEntries(
            Map.entry("souther.compiler.query.Coverages#pairsOf(Ljava/lang/String;Lsouther/compiler/query/Coverages$Readings;ZLsouther/compiler/partition/AdequacyPolicy$OfTheMeasures;)Lsouther/compiler/query/PartitionEvidence$PairSpace;", 2),
            Map.entry("souther.compiler.query.Coverages#coverageOf(Lsouther/compiler/query/Coverages$Readings$AtPosition;Lsouther/compiler/query/Coverages$Readings$AxisReading;Lsouther/compiler/query/Coverages$Readings;Lsouther/compiler/partition/Partitions$Partitioning;Z)Lsouther/compiler/query/PartitionEvidence$AxisCoverage;", 2),
            Map.entry("souther.compiler.query.Coverages#verdictOf(Lsouther/compiler/partition/StandingAtAPoint$Met;ZLsouther/compiler/partition/Border;Lsouther/compiler/query/Adequacy$RowReading;)Lsouther/compiler/query/Measurement;", 3),
            Map.entry("souther.compiler.query.Coverages#whyNoGuardLine(Lsouther/compiler/query/Adequacy$RowReading;Lsouther/compiler/query/Adequacy$Level;)Lsouther/compiler/query/Measurement;", 2),
            Map.entry("souther.compiler.query.Coverages#whyNoInvariantLine(Lsouther/compiler/query/Adequacy$RowReading;Lsouther/compiler/query/Adequacy$Level;)Lsouther/compiler/query/Measurement;", 1),
            // Two searches of one reading of one line, as that reading's measurement. It makes
            // states and decides none of them: what the pair comes to is asked of
            // `acrossTheReadings`, and the five here are the measurements that say each of that
            // one's answers back. A second reading of the same question would be a second coverage
            // semantics, which is what this count exists to stop, so the answer is borrowed rather
            // than worked out and only the writing back is here.
            //
            // Five and not three, because a row one of the searches saw is written back with what
            // both of them went without. Kept as whichever search saw it, the sentence a report
            // prints about what the reading could not read moved with the order the searches were
            // walked in — so the answer is written rather than picked, and a row seen is two shapes
            // depending on whether there was anything to say beside it.
            Map.entry("souther.compiler.query.ObligationCoverage#acrossOneReadingsSearches(Lsouther/compiler/query/Measurement;Lsouther/compiler/query/Measurement;)Lsouther/compiler/query/Measurement;", 5),
            // The reading of a behavior's rows, which is a measure like the ones counted over them
            // and is the one that can never be inapplicable. `of` chooses between the three states
            // a reading that was asked for comes to; the two constants are a behavior with no rows
            // written and a build that does not read rows, which are not the same nothing.
            Map.entry("souther.compiler.query.Adequacy$RowReading#of(Ljava/util/List;Ljava/util/List;)Lsouther/compiler/query/Adequacy$RowReading;", 3),
            Map.entry("souther.compiler.query.Adequacy$RowReading#<clinit>()V", 2),
            Map.entry("souther.compiler.query.PartitionDerivation#noSubject()Lsouther/compiler/query/Measure;", 1),
            Map.entry("souther.compiler.query.PartitionDerivation#of(Ljava/util/List;Lsouther/compiler/partition/MeasureClosure$OfThePartition;Lsouther/compiler/inputs/EmptyInput;)Lsouther/compiler/query/Measure;", 5),
            Map.entry("souther.compiler.query.OutputCaseEvidence#none()Lsouther/compiler/query/OutputCaseEvidence;", 1),
            Map.entry("souther.compiler.query.OutputCaseEvidence#of(Ljava/lang/String;Ljava/util/Set;Lsouther/compiler/query/OutputCaseEvidence$Cases;ZLsouther/compiler/query/WeakeningSet;)Lsouther/compiler/query/OutputCaseEvidence;", 3),
            Map.entry("souther.compiler.query.InputCaseEvidence#none(I)Lsouther/compiler/query/InputCaseEvidence;", 1),
            Map.entry("souther.compiler.query.InputCaseEvidence#of(Ljava/lang/String;ILjava/util/Set;Ljava/util/Set;Lsouther/compiler/query/InputCaseEvidence$Cases;ZLsouther/compiler/query/WeakeningSet;)Lsouther/compiler/query/InputCaseEvidence;", 3),
            Map.entry("souther.compiler.query.BoundaryDerivation#noSubject()Lsouther/compiler/query/Measure;", 1),
            Map.entry("souther.compiler.query.BoundaryDerivation#of(Ljava/util/List;Lsouther/compiler/partition/MeasureClosure$OfTheBorder;Lsouther/compiler/inputs/EmptyInput;)Lsouther/compiler/query/Measure;", 5),
            Map.entry("souther.compiler.query.Adequacy$SignatureEvidence#notASum(Lsouther/compiler/query/OutputCaseEvidence;Ljava/util/List;)Lsouther/compiler/query/Adequacy$SignatureEvidence;", 1),
            Map.entry("souther.compiler.query.Adequacy$SignatureEvidence#noRows(Lsouther/compiler/query/OutputCaseEvidence;Ljava/util/List;)Lsouther/compiler/query/Adequacy$SignatureEvidence;", 1),
            Map.entry("souther.compiler.query.Adequacy$SignatureEvidence#of(Lsouther/compiler/query/OutputCaseEvidence;Ljava/util/List;)Lsouther/compiler/query/Adequacy$SignatureEvidence;", 2),
            Map.entry("souther.compiler.query.PartitionEvidence$AxisCoverage#noRows(Lsouther/compiler/partition/AxisId;Ljava/lang/String;Ljava/util/List;Ljava/util/List;ZLsouther/compiler/query/PartitionEvidence$AxisCoverage$Reading;)Lsouther/compiler/query/PartitionEvidence$AxisCoverage;", 1),
            Map.entry("souther.compiler.query.PartitionEvidence$PairSpace#noRows(Ljava/util/List;)Lsouther/compiler/query/PartitionEvidence$PairSpace;", 1),
            Map.entry("souther.compiler.query.PartitionEvidence$PairSpace#truncated(Ljava/lang/String;Ljava/util/List;JI)Lsouther/compiler/query/PartitionEvidence$PairSpace;", 1),
            Map.entry("souther.compiler.query.PartitionEvidence$PairSpace#<clinit>()V", 1),
            Map.entry("souther.compiler.query.Adequacy$BranchEvidence#noArms(Lsouther/compiler/query/Adequacy$BranchEvidence$NoArms;)Lsouther/compiler/query/Adequacy$BranchEvidence;", 1),
            Map.entry("souther.compiler.query.Adequacy$BranchEvidence#notAsked(Lsouther/compiler/query/Adequacy$BranchEvidence$NotAsked;)Lsouther/compiler/query/Adequacy$BranchEvidence;", 1),
            Map.entry("souther.compiler.query.Adequacy$BranchEvidence#unreadable(Lsouther/compiler/query/WeakeningSet;)Lsouther/compiler/query/Adequacy$BranchEvidence;", 1),
            Map.entry("souther.compiler.query.Adequacy$BranchEvidence#unelaborated(Ljava/lang/String;)Lsouther/compiler/query/Adequacy$BranchEvidence;", 1),
            Map.entry("souther.compiler.query.Adequacy$BranchEvidence#measured(Ljava/lang/String;Ljava/util/List;Ljava/util/Set;Lsouther/compiler/check/PathReachability$Answers$AsRun;Lsouther/compiler/query/WeakeningSet;)Lsouther/compiler/query/Adequacy$BranchEvidence;", 2),
            // And one arm of a behavior, which is a measure of its own beside the account over all
            // of them. Three states from two facts: whether a row lit this arm, and whether the
            // reading that could have lit it ran out. The fourth combination is the one that cannot
            // happen — a row through an arm went through it whatever else stopped — and the
            // constructor refuses it, so this method is where the three are chosen between and the
            // arms of the account cannot be assembled from anything else.
            Map.entry("souther.compiler.query.ArmObligation#of(Ljava/util/List;Ljava/util/Set;Lsouther/compiler/query/WeakeningSet;)Lsouther/compiler/query/ArmObligation;", 3),
            Map.entry("souther.compiler.query.Coverages#whyNothingWasAsked(Lsouther/compiler/query/Adequacy$Level;)Lsouther/compiler/query/Measurement;", 1),
            Map.entry("souther.compiler.query.OutputCaseEvidence#notAsked(Ljava/util/Set;)Lsouther/compiler/query/OutputCaseEvidence;", 1),
            Map.entry("souther.compiler.query.InputCaseEvidence#notAsked(ILjava/util/Set;Ljava/util/Set;)Lsouther/compiler/query/InputCaseEvidence;", 1),
            Map.entry("souther.compiler.query.Adequacy$SignatureEvidence#notAsked(Lsouther/compiler/query/OutputCaseEvidence;Ljava/util/List;)Lsouther/compiler/query/Adequacy$SignatureEvidence;", 1),
            Map.entry("souther.compiler.query.PartitionEvidence$AxisCoverage#notAsked(Lsouther/compiler/partition/AxisId;Ljava/lang/String;Ljava/util/List;Ljava/util/List;ZLsouther/compiler/query/PartitionEvidence$AxisCoverage$Reading;)Lsouther/compiler/query/PartitionEvidence$AxisCoverage;", 1),
            Map.entry("souther.compiler.query.PartitionEvidence$PairSpace#notAsked(Ljava/util/List;)Lsouther/compiler/query/PartitionEvidence$PairSpace;", 1),
            Map.entry("souther.compiler.query.ItemAssessment#weakeningSource()Lsouther/compiler/query/Measurement;", 1),
            // A behavior missing something its boundary is made of — its signature, or the reading
            // of what it takes. Every measure of it is short of the same one thing, so the state is
            // made here and each of them hands its own type parameter to it — five factories and
            // one introduction, which is what keeps them saying the same thing. Asked of the reason
            // rather than handed one, so that which fact the measure carries follows from which
            // half was missing rather than from what a caller chose to name.
            Map.entry("souther.compiler.query.BoundaryForMeasurement$NotDerived#failed(Ljava/lang/String;)Lsouther/compiler/query/Measurement;", 1),
            // And the positions of a signature, which are its own measure: known where something
            // wrote them down, whether that is the boundary or the declaration the boundary was to
            // be built from, and unknown where a composition takes what a stage nobody could work
            // out takes. Two states and one place that chooses between them — the cases at each
            // position are that position's own answer and never this one's.
            Map.entry("souther.compiler.query.Adequacy$SignatureEvidence#at(Ljava/util/List;)Lsouther/compiler/query/Measure;", 1),
            // And the one place a measure's answer is carried over to a value read off what it
            // found. All five arms, because carrying them over is the whole of what it does: what a
            // behavior is owed a row for is read off the lines its positions met, and the two are
            // one measurement. This is the only entry here that decides nothing — a projection that
            // chose between the arms could call a reading that did not run out complete over
            // whatever it happened to produce, which is what every other entry exists to stop.
            Map.entry("souther.compiler.query.Measure#readAs(Ljava/util/function/Function;)Lsouther/compiler/query/Measure;", 5)));

    @Test
    void nothingButTheIntroductionRuleMakesACaseOfAMeasure() throws Exception {
        List<String> cases = casesOf(MEASURE);

        assertEquals(5, cases.size(), () -> "the arms of a measure: " + cases);

        Map<String, Integer> made = new LinkedHashMap<>();
        for (Compiled.Site site : Compiled.sites()) {
            for (String each : cases) {
                if (site.makesA(each)) {
                    made.merge(site.at(), 1, Integer::sum);
                }
            }
        }
        assertEquals(INTRODUCED_BY, made, () -> "what makes a state of a measurement: " + made);
    }

    /**
     * The cases as the type says they are.
     *
     * <p>Asked of the sealed interface rather than listed, so a case added is one this counts
     * without being told — and a case renamed does not quietly leave the set.
     */
    private static List<String> casesOf(String... measures) throws ClassNotFoundException {
        List<String> out = new ArrayList<>();
        for (String measure : measures) {
            gather(Class.forName(measure), out);
        }
        return out;
    }

    /**
     * The arms under one type, descending wherever a permitted subclass is itself a sum.
     *
     * <p>{@code Measure} permits {@code NotApplicable} and {@code Measurement}, and only the first
     * is an arm: the second is where the four states of a measurement live. Descending rather than
     * listing keeps this counting what the types say and not what a test remembered.
     */
    private static void gather(Class<?> type, List<String> out) {
        for (Class<?> each : type.getPermittedSubclasses()) {
            if (each.isSealed()) {
                gather(each, out);
            } else {
                out.add(each.getName());
            }
        }
    }
}
