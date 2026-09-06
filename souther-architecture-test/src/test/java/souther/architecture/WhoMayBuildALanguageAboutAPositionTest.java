package souther.architecture;

import souther.test.RepositoryLayout;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.instruction.InvokeInstruction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who may make a machine, and out of whose allowance.
 *
 * <p>Which strings a rule about a position admits is a question with one owner: the reading that
 * answers for that position builds it, once, out of what the position is allowed, and what crosses
 * into every later reader is the set. A reader that builds one itself is a second answer to what
 * the model admits there — and because it would be building under an allowance of its own, the two
 * can differ over a pattern one of them can afford and the other cannot, with neither saying so.
 *
 * <p><b>An allowance per question, which is the shape rather than a list of numbers.</b> The numbers
 * are equal today and that is a coincidence; what is not a coincidence is which question each of
 * them bounds. One is what a position may build to answer what it admits. One is what handing each
 * of its rules on as the set it leaves may build, which is a projection of that answer and is
 * granted beside it. One is what writing a value out of a pattern may cost, which is a witness for
 * a row and no answer about any position. One is what walking a published set for where it stops
 * may cost, which is a report's question about an answer somebody else built. And one is what
 * working out the classes a behavior's rules about the strings at a position divide it into may
 * build, which is what a measure spends rather than what a reading does. Each is named by its owner
 * and by nobody else, so a reader wanting a machine has to say whose allowance it is spending.
 *
 * <p>Read off the compiled classes and not the source, so that a name reached through a constant is
 * a row here whatever it is spelled as. A row that is new is a finding: either the capability moved
 * and this is where it is written down, or something has grown an allowance of its own.
 */
class WhoMayBuildALanguageAboutAPositionTest {

    private static final String BUDGET = "souther/compiler/regex/PatternPlan$Budget";

    private static final String PLAN = "souther/compiler/regex/PatternPlan";

    private static final String POLICY = "souther/compiler/check/ReadingPolicy";

    private static final String ALLOWANCE = "souther/compiler/values/Allowance";

    private static final RepositoryLayout REPOSITORY = RepositoryLayout.ofWorkingDirectory();

    /**
     * What a position may build to answer what its rules leave it, named where a compilation
     * grants it and nowhere else.
     *
     * <p>{@code Front.Reading} is where a compilation sets what a reading is governed by, and this
     * figure is one of those: the reading is handed the allowance made out of it and reaches no
     * figure of its own. A second namer is a second grant, and the two are one number only until
     * somebody changes one.
     */
    private static final List<String> OF_ADMITTED_VALUES =
            List.of("souther/compiler/query/Front$Reading");

    /**
     * And what handing each of a position's rules on as the set it leaves may cost, granted the
     * same way and by the same place.
     *
     * <p>Its own figure because it bounds a different question: what a position admits is every
     * rule of it met together, and this is what each of them leaves on its own. Named anywhere
     * else, a reader could grant itself the second while the first was the one the compilation
     * meant to bound.
     */
    private static final List<String> OF_WHAT_A_RULE_LEAVES =
            List.of("souther/compiler/query/Front$Reading");

    /**
     * And what working out the classes a behavior's rules about the strings at a position divide it
     * into may build, granted where a compilation grants what a measure may spend.
     *
     * <p>Its own figure and not either of the two above, because moving one of those must not move
     * this. Those bound how exactly a declaration is read to admit what it admits; this bounds what
     * a behavior is read to tell apart, and the two are not one question — a type stating a plain
     * rule beside a body stating an expensive one is the model where sharing a figure would let
     * raising one make a class appear that the other was never asked about.
     *
     * <p>Granted by {@code Front.Adequacy} and not by {@code Front.Reading}, because what runs out
     * here leaves a measurement partial rather than a declaration read less exactly: the classes
     * are not composed and the position is recorded as one this compiler did not divide.
     */
    private static final List<String> OF_BEHAVIOR_DISTINCTIONS =
            List.of("souther/compiler/query/Front$Adequacy");

    /**
     * Who may turn a granted figure into an allowance, which is the other half of the same rule.
     *
     * <p>Counting the figures alone leaves a way round: a policy hands out an allowance, and a
     * reader that asks for a second one has a second meter at every position without ever naming a
     * budget. So the askers are counted too. One answer is one allowance, and the place a reading
     * is made is the place that asks — {@code InvariantChecker} where a declaration's positions are
     * read, and the two input readers that read a declaration's rules for a behavior's inputs.
     *
     * <p><b>How many times each of them asks, and not just that it does.</b> Two calls in one class
     * reach the constant pool once, so a walk over the names it mentions sees the same row for one
     * asking and for two — and two askings inside one reading is exactly the thing being refused. So
     * the invocations are counted where they are written, which is the only place they differ.
     */
    private static final List<String> ASKING_FOR_AN_ALLOWANCE = List.of(
            "souther/compiler/check/InvariantChecker allowanceForAdmittedValues x1",
            "souther/compiler/check/InvariantChecker allowanceForWhatARuleLeaves x1",
            "souther/compiler/inputs/PlacedRules allowanceForAdmittedValues x1",
            "souther/compiler/inputs/ReadQuantities allowanceForAdmittedValues x1");

    /**
     * Who may make an allowance at all, which is the way round the rows above do not close.
     *
     * <p>A reader that writes the figure out where it stands names no budget of this compiler's and
     * asks the policy for nothing, so neither of the counts above moves — and it has a meter at
     * every position all the same. What it cannot do is make one without saying so here.
     *
     * <p>The policies a compilation hands out, and nobody else: that is where a grant becomes
     * something a reader may spend. Two makers of one of them is two answers to how much a position
     * may build, and the second of them is one nothing granted.
     *
     * <p>Two policies rather than one, because a compilation grants two different things. What a
     * reading may build to say what a declaration admits is {@code ReadingPolicy}'s; what a measure
     * may build to say what a behavior tells the values at a position apart is the measures' half
     * of {@code AdequacyPolicy}, and the answer each of them leaves partial is a different answer.
     */
    private static final List<String> MAKING_AN_ALLOWANCE = List.of(
            "souther/compiler/check/ReadingPolicy",
            "souther/compiler/partition/AdequacyPolicy$OfTheMeasures");

    /**
     * And who may write a figure out at all.
     *
     * <p>The last way round: a budget made where it is wanted is a grant nobody made, and the three
     * constants would be three defaults somebody could go past without touching. Only the class
     * that declares them makes one, so what a compilation chooses ({@code Front.Reading}) is chosen
     * out of what is written down rather than out of a number typed at the call.
     */
    private static final List<String> MAKING_A_BUDGET = List.of();

    /**
     * What writing one value out of a pattern may cost.
     *
     * <p>Its own because it bounds a different question: a caller here is offering a string for a
     * row and has no answer about a position to compose. Where it runs out, no row is offered —
     * which is what it does for a pattern it cannot read either, so nothing about a model turns on
     * the number.
     */
    private static final List<String> OF_A_WITNESS =
            List.of("souther/compiler/partition/Partitions");

    /**
     * What walking a set for where it stops on the order may cost.
     *
     * <p>A report's question about an answer already published, so it is paid for out of an
     * allowance of the report's. Charged to the position instead, a diagnostic would decide what
     * the model is read to admit; and the set it walks is the position's, so nothing here is a
     * second answer about the model.
     */
    private static final List<String> OF_AN_ORDERED_EXTENT =
            List.of("souther/compiler/values/TextExtents");

    /**
     * And who turns a plan into a machine at all, which is the capability the three allowances are
     * about.
     *
     * <p>{@code Realizer} is what a position's answer is built by, and the witness is the one
     * caller that builds a machine to write a value out of rather than to answer with. Everything
     * else takes what one of them left.
     */
    private static final List<String> BUILDS_A_MACHINE = List.of(
            "souther/compiler/partition/Partitions",
            "souther/compiler/values/Realizer");

    @Test
    void whatAPositionMayBuildIsNamedByTheAllowanceAndByNobodyElse() {
        assertEquals(OF_ADMITTED_VALUES, namingTheBudget("OF_ADMITTED_VALUES"),
                "one allowance per position, granted in one place: a second namer of it is a"
                        + " second answer to how much a position may build");
    }

    @Test
    void whatHandingARuleOnMayCostIsNamedTheSameWay() {
        assertEquals(OF_WHAT_A_RULE_LEAVES, namingTheBudget("OF_WHAT_A_RULE_LEAVES"),
                "the second figure a reading builds against, granted where the first is: a namer"
                        + " elsewhere is a reader allowing itself what a compilation was to allow");
    }

    @Test
    void whatABehaviorsDistinctionsMayCostIsNamedWhereAMeasureIsGranted() {
        assertEquals(OF_BEHAVIOR_DISTINCTIONS, namingTheBudget("OF_BEHAVIOR_DISTINCTIONS"),
                "what a behavior is read to tell apart is bounded where a compilation grants what a"
                        + " measure may spend: a namer elsewhere is a measure allowing itself what"
                        + " the compilation was to allow, and one among the figures a reading"
                        + " spends is a declaration's exactness deciding a behavior's classes");
    }

    @Test
    void everyPlaceThatMakesAnAllowanceIsWrittenDownHere() {
        assertEquals(MAKING_AN_ALLOWANCE, makingAnAllowance(),
                "an allowance is what a compilation's grant becomes, so it is made where the grant"
                        + " is held: one made anywhere else is a meter at every position that"
                        + " nobody granted and no figure of this compiler's names");
    }

    @Test
    void andEveryPlaceThatWritesAFigureOut() {
        assertEquals(MAKING_A_BUDGET, makingABudget(),
                "a figure is declared where the figures are and chosen where a compilation chooses"
                        + " them: one written at the place it is wanted is a grant nobody made");
        // What the row above says is that nobody outside writes one, which an empty answer says
        // whether or not this walk can see one being written. So the same question is asked with
        // the class that does write them left in.
        assertTrue(found(entry -> entry instanceof MemberRefEntry member
                        && member.owner().name().stringValue().equals(BUDGET)
                        && member.name().stringValue().equals("<init>"), false)
                        .contains(BUDGET),
                "the figures are written out where they are declared, so a walk that cannot find"
                        + " that is finding nothing at all");
    }

    @Test
    void everyPlaceThatAsksForAnAllowanceIsWrittenDownHere() {
        assertEquals(ASKING_FOR_AN_ALLOWANCE, askingForAnAllowance(),
                "one answer, one allowance: a second asker inside one reading is a second meter at"
                        + " every position, and no budget is named to give it away");
    }

    @Test
    void whatAWitnessMayCostIsNamedByWhoeverWritesOne() {
        assertEquals(OF_A_WITNESS, namingTheBudget("OF_A_WITNESS"),
                "a value pasted into a row is not an answer about a position, and the reverse is"
                        + " what naming this budget elsewhere would make it");
    }

    @Test
    void whatAnOrderedExtentMayCostIsNamedByWhoeverWalksOne() {
        assertEquals(OF_AN_ORDERED_EXTENT, namingTheBudget("OF_AN_ORDERED_EXTENT"),
                "where a set stops is a report's question about a published answer, and this is the"
                        + " allowance it is asked under");
    }

    @Test
    void everyClassThatTurnsAPlanIntoAMachineIsWrittenDownHere() {
        assertEquals(BUILDS_A_MACHINE, callingCompile(),
                "a machine for what a position admits is made by the position's own realizer, and a"
                        + " caller that makes one elsewhere is answering the same question a second"
                        + " time under an allowance of its own");
    }

    /**
     * The walk reads every module's classes.
     *
     * <p>Asked of the modules the repository has and not of what a build happened to leave: a
     * module whose classes are missing is one whose names this cannot see, and the rows from the
     * rest would match while this answered about fewer modules than it names.
     */
    @Test
    void andEveryModuleTheRepositoryHoldsWasRead() {
        List<String> unbuilt = new ArrayList<>();
        for (Path module : REPOSITORY.modules()) {
            if (!Files.isDirectory(classesOf(module)) && hasMainSources(module)) {
                unbuilt.add(module.getFileName().toString());
            }
        }

        assertEquals(List.of(), unbuilt,
                "a module whose classes are not built is one this walk passes over, and a walk that"
                        + " passes over a module answers about the rest while saying it answers"
                        + " about all of them");
    }

    /**
     * And the walk finds a namer that is there.
     *
     * <p>The rows above are who may name a budget; this is that a name this walk should see is one
     * it does see. Matched on a field nothing names, every list would be empty and equal to an
     * empty expectation.
     */
    @Test
    void andTheWalkSeesANamerThatIsThere() {
        assertTrue(namingTheBudget("OF_A_WITNESS").contains("souther/compiler/partition/Partitions"),
                "the witness names its own budget, so a walk that cannot find it finds nothing");
    }

    /** Every class whose constant pool reaches {@code field} of the budgets. */
    private static List<String> namingTheBudget(String field) {
        return found(entry -> entry instanceof MemberRefEntry member
                && member.owner().name().stringValue().equals(BUDGET)
                && member.name().stringValue().equals(field));
    }

    /** Every asking of the policy for an allowance, counted where it is written. */
    private static List<String> askingForAnAllowance() {
        Map<String, Integer> counted = new TreeMap<>();
        for (Path module : REPOSITORY.modules()) {
            for (Path each : classesUnder(module)) {
                String asker = internalName(module, each);
                for (MethodModel method : classOf(each).methods()) {
                    method.code().ifPresent(code -> {
                        for (CodeElement element : code) {
                            if (element instanceof InvokeInstruction call
                                    && call.owner().name().stringValue().equals(POLICY)
                                    && call.name().stringValue().startsWith("allowanceFor")) {
                                counted.merge(asker + " " + call.name().stringValue(), 1,
                                        Integer::sum);
                            }
                        }
                    });
                }
            }
        }
        List<String> out = new ArrayList<>();
        counted.forEach((who, times) -> out.add(who + " x" + times));
        return out;
    }

    /** Every class that makes an allowance out of a figure. */
    private static List<String> makingAnAllowance() {
        return found(entry -> entry instanceof MemberRefEntry member
                && member.owner().name().stringValue().equals(ALLOWANCE)
                && (member.name().stringValue().equals("of")
                        || member.name().stringValue().equals("besides")));
    }

    /** Every class that writes a figure out, which is the class that declares them and no other. */
    private static List<String> makingABudget() {
        return found(entry -> entry instanceof MemberRefEntry member
                && member.owner().name().stringValue().equals(BUDGET)
                && member.name().stringValue().equals("<init>"));
    }

    /** Every class that asks a plan for the machine it names. */
    private static List<String> callingCompile() {
        return found(entry -> entry instanceof MemberRefEntry member
                && member.owner().name().stringValue().equals(PLAN)
                && member.name().stringValue().equals("compile"));
    }

    private static List<String> found(java.util.function.Predicate<PoolEntry> reaching) {
        return found(reaching, true);
    }

    private static List<String> found(java.util.function.Predicate<PoolEntry> reaching,
                                      boolean butNotTheOwner) {
        Set<String> out = new TreeSet<>();
        for (Path module : REPOSITORY.modules()) {
            for (Path each : classesUnder(module)) {
                String reader = internalName(module, each);
                // What a class names of itself is not a reader of anything. The plan declares the
                // budgets and the meter they make, so a row for it would be this walk reporting the
                // owner as its own caller.
                if (butNotTheOwner && (reader.equals(PLAN) || reader.startsWith(PLAN + "$"))) {
                    continue;
                }
                for (PoolEntry entry : constantPoolOf(each)) {
                    if (reaching.test(entry)) {
                        out.add(reader);
                    }
                }
            }
        }
        return new ArrayList<>(out);
    }

    private static Iterable<PoolEntry> constantPoolOf(Path compiled) {
        return classOf(compiled).constantPool();
    }

    private static ClassModel classOf(Path compiled) {
        try {
            return ClassFile.of().parse(Files.readAllBytes(compiled));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The class's own binary name, taken against the directory it was found under rather than off
     *  the first {@code classes} in the path, which a checkout under one would be. */
    private static String internalName(Path module, Path compiled) {
        String name = classesOf(module).relativize(compiled).toString().replace('\\', '/');
        return name.substring(0, name.length() - ".class".length());
    }

    private static Path classesOf(Path module) {
        return module.resolve("target").resolve("classes");
    }

    /** Whether the module has main sources to have been built from. A module holding only tests or
     *  only a pom leaves no classes and is not one this walk is missing. */
    private static boolean hasMainSources(Path module) {
        return Files.isDirectory(module.resolve("src").resolve("main").resolve("java"));
    }

    private static List<Path> classesUnder(Path module) {
        Path where = classesOf(module);
        if (!Files.isDirectory(where)) {
            return List.of();
        }
        try (Stream<Path> found = Files.walk(where)) {
            return found.filter(p -> p.toString().endsWith(".class")).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
