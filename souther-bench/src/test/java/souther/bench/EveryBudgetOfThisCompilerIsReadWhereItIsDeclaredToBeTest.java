package souther.bench;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every figure this compiler stops a search at is read where a rule says it is read, and nowhere
 * else.
 *
 * <p>What a budget is for is being carried: a search that stopped at one says which, and an account
 * that reads it knows the point is open for a reason somebody could raise rather than because the
 * model refuses a row. That only works while every place a figure is reached is a place that hands
 * it over — a walk that consults one and comes back with a word alone is a stop that vanished, and
 * the word it comes back with is written wherever a walk stops for any reason at all, so nothing
 * downstream can tell that it happened.
 *
 * <p><b>So the places are counted rather than trusted.</b> Twice while this was written the list of
 * budgets was taken from the constants that looked like budgets, and twice it was short — once by a
 * figure spelled differently and once by a figure that is the size of a walk rather than a number
 * anybody wrote down. A name is not what makes a budget; being reached and stopping something is.
 *
 * <p>Each entry below says what that place does with the figure. A place added is a question, and
 * it has three answers rather than two: the place hands the budget over, or what it hands over is
 * carried somewhere else, or reaching the figure omits no work at all and there is nothing to carry.
 * The answer belongs beside the code that decides, which is why the list is here and not a count.
 *
 * <p><b>The third is an answer and not an excuse.</b> A figure whose channel is simply missing and
 * one that gives nothing up look alike from here — both are read and neither travels — and the
 * difference is the whole of whether a reader is owed something. So a place claiming it has to say
 * why nothing is lost, and the sentence has to be one somebody could find wrong.
 *
 * <p><b>One of two sheets.</b> A figure a class copies into a field of its own is reached once,
 * where the field is set, and the walk that then stops at the field is not a reader of the figure
 * here — so this holds the registered figures to being handed over, and
 * {@link EveryFigureTheComposingStageStopsAtIsABudgetOrIsSaidNotToBeTest} holds the classes to
 * registering a figure at all. Neither is enough alone: that one sees a number written down and
 * this one sees a figure that is the size of a walk.
 *
 * <p><b>And what neither sees is the predicate.</b> Each entry below says the place hands the
 * figure over; that the place reaches it only where there was more to do is not something either
 * sheet can read. What says that is the threshold tests, one either side of a figure and on it, and
 * they exist for the walks that can be put there.
 *
 * <p>Which is why a place that reaches a figure and a place that decides a walk was stopped are
 * worth keeping apart. A consumer here says it has no room for the piece in front of it and says
 * under which figure; that the walk was holding a piece at all is the walk's, and a walk that
 * records a figure without one is the reading these sheets cannot see going wrong.
 */
class EveryBudgetOfThisCompilerIsReadWhereItIsDeclaredToBeTest {

    private static final String BUDGET = "souther.compiler.partition.CompositionBudget";

    /**
     * Where a figure is read, and what that place does with it.
     *
     * <p>By the method and not by the class. A helper beside one that reads a figure answers for
     * nothing, and counting per class would let a second reader hide behind the first.
     */
    private static final Map<String, String> READ_AT = new LinkedHashMap<>(Map.ofEntries(
            // The figures themselves. Every other place asks one of these for its number.
            Map.entry("souther.compiler.partition.CompositionBudget#<clinit>()V",
                    "the figures, which is where they are"),

            // The stops. Each reaches a figure and hands it over as itself.
            Map.entry("souther.compiler.partition.Witnesses#<clinit>()V",
                    "how many elements and characters a proposal holds, and how many pairings"),
            Map.entry("souther.compiler.partition.Witnesses#sized("
                            + "Lsouther/compiler/check/Shape;I"
                            + "Lsouther/compiler/check/RuleReadingContext;Ljava/util/Set;)"
                            + "Lsouther/compiler/partition/Witnesses$Built;",
                    "stops at the elements and the characters, and says which"),
            Map.entry("souther.compiler.partition.Witnesses#ofMapping("
                            + "Lsouther/compiler/check/Shape$Mapping;I"
                            + "Lsouther/compiler/check/RuleReadingContext;Ljava/util/Set;)"
                            + "Lsouther/compiler/partition/Witnesses$Built;",
                    "stops at the pairings a map is built from at once, and says which"),
            // A total's two, one consumer each. The figure is asked for twice per consumer and for
            // two questions: whether there is room for the piece in front of it, and — where the
            // walk finds there was not — which figure to say so under. Held apart, a consumer could
            // report having run out of one figure while making room by another.
            //
            // Two and not three, because the ways a difference is spread are not held to a figure.
            // What is written of them is that this compiler writes some of them, which is no number
            // to raise and travels as a CompositionRepertoire rather than from here.
            Map.entry("souther.compiler.partition.ContainersAddingUp$HowManyElements#take("
                            + "Ljava/lang/Integer;)Lsouther/compiler/partition/Taking$Taken;",
                    "has no room for a count of more elements than a row carries"),
            Map.entry("souther.compiler.partition.ContainersAddingUp$HowManyElements#figure()"
                            + "Lsouther/compiler/partition/CompositionBudget;",
                    "says which figure that was, which is what the walk hands over"),
            Map.entry("souther.compiler.partition.ContainersAddingUp$WhatIsOffered#take("
                            + "Lsouther/compiler/partition/FixtureTemplate;)"
                            + "Lsouther/compiler/partition/Taking$Taken;",
                    "has no room for a container past as many as are offered"),
            Map.entry("souther.compiler.partition.ContainersAddingUp$WhatIsOffered#figure()"
                            + "Lsouther/compiler/partition/CompositionBudget;",
                    "says which figure that was, which is what the walk hands over"),
            Map.entry("souther.compiler.partition.ContainersAddingUp$Asking#next()"
                            + "Lsouther/compiler/partition/ConstructionPlan$Result;",
                    "stops asking about ways down to the number, which is the one place they are"
                            + " asked about, and hands the figure over with whatever the planning"
                            + " gave up at"),
            Map.entry("souther.compiler.partition.LevelRealizer#<clinit>()V",
                    "the places a pair is tried at, the steps, the progression — and the re-reads,"
                            + " which travel nowhere because reaching that one gives nothing up:"
                            + " the walk carries on against the wider box, which offers every"
                            + " assignment the narrowing would have and skips none of them"),
            Map.entry("souther.compiler.partition.LevelRealizer#ofTwo("
                            + "Lsouther/compiler/partition/Standing$OfTwoOnOneCarrier;"
                            + "Lsouther/compiler/inputs/SearchRegion;)"
                            + "Lsouther/compiler/partition/Realization;",
                    "stops walking a line at the places it tries and says which figure"),
            Map.entry("souther.compiler.partition.LevelRealizer#ofAForm("
                            + "Lsouther/compiler/partition/Standing$OfAForm;"
                            + "Lsouther/compiler/inputs/SearchRegion;)"
                            + "Lsouther/compiler/partition/Realization;",
                    "collects what the level walks ran out of, and the levels it was offered"),
            Map.entry("souther.compiler.partition.LevelRealizer$Search#stepsLeft()Z",
                    "the steps a search may take, marked where there is no room for another"),
            Map.entry("souther.compiler.partition.LevelRealizer$Search#outward("
                            + "ILsouther/compiler/partition/CandidateDomain$Outward;"
                            + "Ljava/math/BigDecimal;Ljava/math/BigDecimal;"
                            + "Lsouther/compiler/inputs/SearchRegion;)"
                            + "Lsouther/compiler/partition/LevelRealizer$Reached;",
                    "walks a progression as far as this walks one, and says that is why"),
            Map.entry("souther.compiler.partition.LevelCandidateSource#<clinit>()V",
                    "how many levels a side is asked at"),
            Map.entry("souther.compiler.partition.NumericWitness#<clinit>()V",
                    "how many values a position on the way is tried at"),
            Map.entry("souther.compiler.partition.NumericWitness#walk("
                            + "Lsouther/compiler/inputs/SearchRegion;Ljava/util/List;I"
                            + "Ljava/util/function/Function;Ljava/util/Map;Ljava/util/Set;)Z",
                    "stops trying values of a position on the way and says which figure"),
            Map.entry("souther.compiler.partition.Generator#<clinit>()V",
                    "how many assignments a search composes"),
            Map.entry("souther.compiler.partition.Generator#conditioned("
                            + "Lsouther/compiler/partition/MeasuredInput;I"
                            + "Lsouther/compiler/partition/ConstructionPlan;Ljava/util/Map;"
                            + "Ljava/util/Map;"
                            + "Lsouther/compiler/partition/Generator$CandidateCheck;)"
                            + "Lsouther/compiler/partition/Generator$Outcome;",
                    "the assignments a descent composes, marked where the bound is reached"),
            Map.entry("souther.compiler.partition.Generator#over("
                            + "Lsouther/compiler/partition/MeasuredInput;I"
                            + "Lsouther/compiler/partition/ConstructionPlan;Ljava/util/List;"
                            + "Ljava/util/List;"
                            + "Lsouther/compiler/partition/Generator$CandidateCheck;)"
                            + "Lsouther/compiler/partition/Generator$Outcome;",
                    "the same bound over one pass of the assignments"),
            Map.entry("souther.compiler.partition.ConstructionPlan#node("
                            + "Lsouther/compiler/types/Type;"
                            + "Lsouther/compiler/inputs/TermPath;"
                            + "Lsouther/compiler/check/Symbols;"
                            + "Lsouther/compiler/check/PublishedDeclarations;I"
                            + "Ljava/util/Set;"
                            + "Lsouther/compiler/inputs/Requirements;"
                            + "Lsouther/compiler/partition/ConstructionPlan$HowManyItHolds;)"
                            + "Lsouther/compiler/partition/ConstructionPlan$NodeResult;",
                    "stops descending where a value is still made of positions, and says which"
                            + " figure — on the position where it stopped, or as the whole plan"
                            + " where the caller asked for something below it"),

            // The reading of what a body decides. The figure is what the reading of the ways will
            // hold apart, and where it will not, the rules are none of the body's rather than some
            // of them -- so what is carried out is the figure itself.
            Map.entry("souther.compiler.partition.DecisionReading#<clinit>()V",
                    "how many paths through one body a decision is read for"),
            Map.entry("souther.compiler.partition.DecisionReading#of("
                            + "Ljava/lang/String;Lsouther/compiler/core/Core;"
                            + "Lsouther/compiler/inputs/InputReading;"
                            + "Lsouther/compiler/inputs/InputReads;Ljava/util/Set;)"
                            + "Lsouther/compiler/partition/DecisionReading;",
                    "hands the figure to the reading of the ways, and says which it reached"),

            // The readings. Each takes a figure that was handed over and says what it comes to.
            Map.entry("souther.compiler.partition.Generator$UnresolvedCombination$Reason#wordFor("
                            + "Ljava/util/Collection;)"
                            + "Lsouther/compiler/partition/Generator$UnresolvedCombination$Reason;",
                    "the word a search stopped by these comes back with"),
            Map.entry("souther.compiler.report.AdequacyReport#said("
                            + "Lsouther/compiler/publish/CanonicalSelection;)Ljava/lang/String;",
                    "what a figure is called where a reader meets one"),
            // The order. It reaches every figure and hands none of them anywhere: what it decides
            // is which of them a reader is told about first, where two were reached.
            Map.entry("souther.compiler.publish.PublicationOrders#<clinit>()V",
                    "the order the figures are said in, where a search met more than one")));

    /**
     * The places that read a figure are the places that say they do.
     *
     * <p>Both ways round. A place missing from the list is one nobody has said what it does with
     * the figure it reached; a place in the list that reads none is a rule about code that has gone.
     */
    @Test
    void aFigureIsReachedOnlyWhereSomethingSaysWhatItDoesWithIt() throws Exception {
        Set<String> found = new LinkedHashSet<>();
        for (Compiled.Site site : Compiled.sites()) {
            if (site.owner().equals(BUDGET) && !site.member().equals("<init>")
                    && !site.from().equals(BUDGET) && !javacsOwn(site.from())) {
                found.add(site.at());
            }
        }
        // The figures' own class reads itself, which is what an enum is; counted as a reader, every
        // constant would be a place to explain.
        found.add(BUDGET + "#<clinit>()V");

        List<String> unsaid = new ArrayList<>(found);
        unsaid.removeAll(READ_AT.keySet());
        List<String> gone = new ArrayList<>(READ_AT.keySet());
        gone.removeAll(found);

        assertEquals(List.of(), unsaid,
                "a figure of this compiler's reached where nothing says what it does with it."
                        + " Either it hands the budget over, and the list says so, or it swallows"
                        + " it — and a swallowed budget is a stop nothing downstream can see");
        assertEquals(List.of(), gone,
                "a rule about a place that no longer reads a figure");
    }

    /**
     * A class javac wrote, which is not a place anybody decided anything.
     *
     * <p>A switch over the figures compiles to a table beside the class that switches, and the
     * table reads every constant to number them. Counted as readers, every exhaustive switch would
     * be a place to write a sentence about — and the sentence would be about the switch, which is
     * already named here by the method that holds it.
     */
    private static boolean javacsOwn(String from) {
        int nested = from.lastIndexOf('$');
        return nested >= 0 && from.length() > nested + 1
                && Character.isDigit(from.charAt(nested + 1));
    }
}
