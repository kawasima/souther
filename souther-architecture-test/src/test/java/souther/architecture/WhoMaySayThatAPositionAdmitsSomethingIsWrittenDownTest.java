package souther.architecture;

import souther.compiler.values.Emptiness;
import souther.test.RepositoryLayout;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who may say that a position admits something, and who may only say that one admits nothing.
 *
 * <p>{@link Emptiness} answers three ways and the two settled answers are not one another's mirror.
 * Either half of a pair holding nothing leaves the pair nothing, so {@code EMPTY} is sound from
 * whichever reading reached it. {@code NONEMPTY} is a claim about the whole of what was asked, and
 * {@code Confinement} says in its own words what that whole is: which values a position may take
 * and where its order stops, and nothing else. The components beside it stay outside, so a reader
 * taking one of these answers for "a value of this declaration exists" would be holding a claim no
 * reduction here carries.
 *
 * <p>So everywhere the word is said is written down. What the lists hold is readings that work an
 * admission out and hand it on; what they must not grow is a reader that acts on the settled
 * positive answer as though it were about more than the reading that gave it.
 *
 * <p><b>Three rules and not one, because they are three claims.</b> The widest names every nest
 * that touches a settled answer at all — said as a constant, or asked of one through the operations
 * that observe and compose settledness, since {@code a.joined(b)} carries a positive answer onwards
 * without spelling one. Inside it is who may make the positive answer. Inside that is the one the
 * contract is about: which nests outside the readings themselves say it.
 *
 * <p><b>What these rules do not say.</b> They fix who may name a settled answer, and not who may
 * act on one: a nest already on a list can grow a second reader of an answer it was already making,
 * and no owner set changes. What is claimed is what a walk over the compiled classes holds.
 *
 * <p><b>And beside them, which reading of the word each place is.</b> A nest is who may hold a
 * claim; a reading is one method, so those rules name the class, the method and what it takes and
 * gives — a permission written as a name would widen to an overload added later, and what they are
 * written down as is the questions this compiler has not decided. The word's own class is passed
 * over by the rules about who says it, and by them only: the meanings are worked out there, so a
 * reading there that no meaning answered is one nobody decided in the one place that decides them.
 *
 * <p>What those rules are about is a reference compared against one of the constants, which is what
 * a repository whose enums are compared with {@code ==} writes. A comparison spelt some other way
 * is not one of them, and what they hold is that spelling and not every way two of these could be
 * told apart.
 *
 * <p>Read off those classes, because a call is what the compiler made of it: a lambda body, a
 * method reference and a switch are three spellings a scan of source text would have to know about
 * one at a time. Every module's classes, because this module is built last and a check living in
 * the module it is about passes over everything built after it.
 *
 * <p><b>Every list here is what a walk found, so every walk is held to a body beside this test.</b>
 * A list and the walk that fills it are written together and agree by construction: what a walk
 * stopped reading drops out of the list, and the list is then what the walk can still see rather
 * than what the repository holds. So each of them is shown finding something written here —
 * {@link Taking} and {@link TakingByStanding} switch, {@link Referring} asks through a reference,
 * {@link Comparing} compares every way one can be written, a name away and a conditional away
 * included — and, where something near it would look the same to a walk that read less, shown not
 * finding that: {@link Constructing} writes a constant where a value is wanted, and calls one
 * before comparing what the call gave.
 */
class WhoMaySayThatAPositionAdmitsSomethingIsWrittenDownTest {

    private static final String EMPTINESS = "souther/compiler/values/Emptiness";

    /** Which of two alternatives still stand, which is the one reading of this word that is about
     *  two answers at once and is published beside them. */
    private static final String STANDING = EMPTINESS + "$Alternatives";

    /** The two answers that settle something, which is what these rules are about.
     *  {@code UNDECIDED} settles nothing and is named freely. */
    private static final Set<String> SETTLED = Set.of("EMPTY", "NONEMPTY");

    /** The operations that read whether an answer is settled or carry one onwards. A caller of
     *  these holds a settled answer without naming one, which is why calling them counts as saying
     *  it. */
    private static final Set<String> OBSERVES_OR_COMPOSES =
            Set.of("isEmpty", "isDecided", "met", "joined", "of", "bothStand");

    /** What javac writes for a switch over this word: a synthetic table of its constants, read by
     *  whoever switched. Taking the answer apart by which of the three it is, under a spelling that
     *  names no constant in the code that does it. */
    private static final String TAKEN_APART = "$SwitchMap$souther$compiler$values$Emptiness";

    /** The same for a switch over which alternatives stand, which is the other thing a reader may
     *  take apart and is a different question from which of the three an answer is. */
    private static final String TAKEN_APART_BY_STANDING = TAKEN_APART + "$Alternatives";

    /** A comparison of one of these against one of its constants, which is a reading of the word
     *  that no owned operation answered and that an answer added to the three would fall through
     *  without anybody deciding. Found by following what the constant becomes
     *  ({@link WhatBecomesOfAValueOnTheStack}), because what compares a value is whatever takes it
     *  off the stack and not whatever is written near it. */
    private static final String COMPARED = "compared";

    private static final RepositoryLayout REPOSITORY = RepositoryLayout.ofWorkingDirectory();

    /**
     * Every nest that says a position is settled one way or the other.
     *
     * <p>The readings that work an admission out, and nothing else. {@code Carrier} and
     * {@code TextExtents} answer what a set of values and a range come to between them, which is
     * the bottom of the pair; {@code Confinement} owns the pair and the walk over the alternatives
     * that both halves are asked along. {@code AdmissibleValues},
     * {@code ConjoinedAdmissibleValues} and {@code PlannedValues} are that walk over what a reading
     * holds, and {@code Apartness} is what the denials between two blocks come to.
     * {@code Realized} says whether everything asked for was built. {@code StatedByClauses} and
     * {@code Settlement} put the answers of a choice's branches together, and
     * {@code StringMachineAnswers} keeps an answer once somebody has looked.
     *
     * <p>What is not here is the list's point. Nothing else in {@code check}, nothing downstream of
     * the compiler, and nothing that reports: a settled answer reaching one of those would be a
     * claim travelling further than the reading that made it.
     */
    private static final List<String> SAYS_A_POSITION_IS_SETTLED = List.of(
            "souther/compiler/check/Carrier",
            "souther/compiler/check/Confinement",
            "souther/compiler/check/Settlement",
            "souther/compiler/check/StatedByClauses",
            "souther/compiler/values/AdmissibleValues",
            "souther/compiler/values/Apartness",
            "souther/compiler/values/ConjoinedAdmissibleValues",
            "souther/compiler/values/PlannedValues",
            "souther/compiler/values/Realized",
            "souther/compiler/values/StringMachineAnswers",
            "souther/compiler/values/TextExtents");

    /**
     * And who may make the settled positive answer.
     *
     * <p>{@code Settlement} is the one nest that says a position is settled without ever saying it
     * admits something: it joins two branches' answers and reads whether the join came out empty. A
     * nest arriving here is one that has begun to claim something exists, which is what these rules
     * are about.
     */
    private static final List<String> SAYS_SOMETHING_IS_ADMITTED = List.of(
            "souther/compiler/check/Carrier",
            "souther/compiler/check/Confinement",
            "souther/compiler/check/StatedByClauses",
            "souther/compiler/values/AdmissibleValues",
            "souther/compiler/values/Apartness",
            "souther/compiler/values/ConjoinedAdmissibleValues",
            "souther/compiler/values/PlannedValues",
            "souther/compiler/values/Realized",
            "souther/compiler/values/TextExtents");

    /**
     * And which of them are outside the readings that hold the values.
     *
     * <p>{@code values} is where an admission is worked out, so a nest there saying something is
     * admitted is a reading answering about itself. These three are in {@code check}:
     * {@code Carrier} makes the answer at the bottom of the pair, {@code Confinement} owns the
     * pair, and {@code StatedByClauses} is where an answer that came out positive decides something
     * else — a choice whose two branches both stand is held open rather than merged.
     */
    private static final List<String> SAYS_IT_OUTSIDE_THE_READINGS = List.of(
            "souther/compiler/check/Carrier",
            "souther/compiler/check/Confinement",
            "souther/compiler/check/StatedByClauses");

    /**
     * And the readings that owe each of the three answers something of its own.
     *
     * <p>What becomes of a branch of a choice whose fate came back is three things and not two: a
     * branch nobody can be in leaves an account and not an alternative, a branch nobody settled is
     * kept saying so, and a branch somebody can be in is itself. Nothing composed out of the two
     * settled answers says the middle one, so this reading is written as a switch and is told about
     * an answer added to the three.
     */
    private static final List<String> TAKES_THE_ANSWER_APART = List.of(
            "souther/compiler/check/StatedByClauses$Taken#under"
                    + "(Lsouther/compiler/check/Settlement$Sided;)"
                    + "Lsouther/compiler/check/StatedByClauses$Taken;");

    /**
     * And the readings that owe something of its own to each way two alternatives can fall.
     *
     * <p>What a choice comes to before anything is built, and what one occurrence of it leaves once
     * its branches were probed. Both leave the branch that stands where one of them does, and both
     * have a settlement of their own for a choice nobody can take, so neither is composed out of
     * the other three ways.
     */
    private static final List<String> TAKES_IT_APART_BY_STANDING = List.of(
            "souther/compiler/check/StatedByClauses$Reading#chosen"
                    + "(Lsouther/compiler/check/StatedByClauses$Either;Ljava/util/Map;)"
                    + "Lsouther/compiler/check/StatedTogether;",
            "souther/compiler/check/StatedByClauses$Reading#decided"
                    + "(Lsouther/compiler/check/StatedTogether$Said;"
                    + "Lsouther/compiler/check/Settlement$Sided;"
                    + "Lsouther/compiler/check/StatedTogether$Said;"
                    + "Lsouther/compiler/check/Settlement$Sided;)"
                    + "Lsouther/compiler/check/StatedTogether$Said;");

    /** The bodies beside this test that compare, which is what holds the detector to finding one
     *  however it was written. */
    private static final List<String> COMPARED_IN_THE_FIXTURE = List.of(
            "souther/architecture/WhoMaySayThatAPositionAdmitsSomethingIsWrittenDownTest$Comparing#emptyIsIt"
                    + "(Lsouther/compiler/values/Emptiness;)Z",
            "souther/architecture/WhoMaySayThatAPositionAdmitsSomethingIsWrittenDownTest$Comparing#emptyIsNotIt"
                    + "(Lsouther/compiler/values/Emptiness;)Z",
            "souther/architecture/WhoMaySayThatAPositionAdmitsSomethingIsWrittenDownTest$Comparing#emptyIsWhicheverOfThese"
                    + "(Lsouther/compiler/values/Emptiness;"
                    + "Lsouther/compiler/values/Emptiness;Z)Z",
            "souther/architecture/WhoMaySayThatAPositionAdmitsSomethingIsWrittenDownTest$Comparing#itIsEmpty"
                    + "(Lsouther/compiler/values/Emptiness;)Z",
            "souther/architecture/WhoMaySayThatAPositionAdmitsSomethingIsWrittenDownTest$Comparing#itIsNotEmpty"
                    + "(Lsouther/compiler/values/Emptiness;)Z",
            "souther/architecture/WhoMaySayThatAPositionAdmitsSomethingIsWrittenDownTest$Comparing#itIsWhatWasPutAway"
                    + "(Lsouther/compiler/values/Emptiness;)Z");

    /**
     * And the readings of this word whose meaning nobody has decided yet.
     *
     * <p>Three questions and no owner for any of them. {@code eitherShown} and {@code alsoSeen}
     * are the same partition of two answers that {@link Emptiness.Alternatives} is, read the other
     * way round: an alternative shown empty drops out of a choice, and a conjunct shown empty
     * decides the conjunction, so the four cases mean opposite things and one word for both would
     * say that a left alternative and a left conjunct are one thing. {@code admission} reads what a
     * settled positive answer is worth beside a position nobody could build. The two
     * {@code anyAlternativeAdmits} read that a joined answer has reached the top of what a choice
     * can be, which is a fact about the arithmetic and is known here rather than where the
     * arithmetic is.
     *
     * <p>Each of them is a question this word could be given an owner for, and none of them is one
     * this compiler has decided. Naming a reading here says that; it does not say that the reading
     * was left alone.
     */
    private static final List<String> COMPARED_IN_PRODUCTION = List.of(
            // The same partition of two answers, read one way by a choice and the other by a
            // conjunction (#1492).
            "souther/compiler/check/Confinement#eitherShown"
                    + "(Lsouther/compiler/check/Confinement$Admission;"
                    + "Lsouther/compiler/check/Confinement$Admission;)"
                    + "Lsouther/compiler/check/Confinement$Admission;",
            // What a positive answer is worth beside a position nobody could build, and that a
            // joined answer has reached the top of what a choice can be (#1493).
            "souther/compiler/check/Confinement$Worked#admission"
                    + "(Lsouther/compiler/check/PositionEnvelope$Restrictions;"
                    + "Lsouther/compiler/values/StringMachineAnswers;)"
                    + "Lsouther/compiler/check/Confinement$Admission;",
            "souther/compiler/check/Settlement$Sided#alsoSeen"
                    + "(Lsouther/compiler/check/Settlement$Sided;)"
                    + "Lsouther/compiler/check/Settlement$Sided;",
            "souther/compiler/values/AdmissibleValues#anyAlternativeAdmits"
                    + "(Lsouther/compiler/values/AskedOfEachBlock;"
                    + "Lsouther/compiler/values/AskedOfARelation;)"
                    + "Lsouther/compiler/values/Emptiness;",
            "souther/compiler/values/PlannedValues#anyAlternativeAdmits"
                    + "(Lsouther/compiler/values/AskedOfEachBlock;)"
                    + "Lsouther/compiler/values/Emptiness;");

    /**
     * One saying of the word, and whose code holds it.
     *
     * <p>Both the nest and the class, because the rules above and the rules below are about
     * different things. Which nest may hold a claim is a claim about a nest: a helper written
     * beside a reading is that reading's, and the class a lambda's body was put in is not a second
     * owner of anything. Which reading of the word this is is a claim about one method, and the
     * class it is declared in is part of naming it.
     *
     * @param spelt what the method takes and gives, so that a name is one method and not every
     *              method wearing it — a permission written as a name would widen to an overload
     *              added later, and this list is what says which questions are still open
     */
    private record Use(String nest, String owner, String method, String spelt, String said) {

        /** The one method this is, said the way a permission is written. */
        String place() {
            return owner + "#" + method + spelt;
        }
    }

    @Test
    void everyNestThatSaysAPositionIsSettledIsWrittenDown() {
        assertEquals(SAYS_A_POSITION_IS_SETTLED, nestsSaying(saidInProduction(),
                        use -> SETTLED.contains(use.said())
                                || OBSERVES_OR_COMPOSES.contains(use.said())),
                "a settled answer is about the reading that reached it, so where it is said is"
                        + " written down: said somewhere new, a claim about which values a position"
                        + " may take and where its order stops is being read as one about more");
    }

    @Test
    void andOnlyTheseSayThatSomethingIsAdmitted() {
        assertEquals(SAYS_SOMETHING_IS_ADMITTED,
                nestsSaying(saidInProduction(), use -> use.said().equals("NONEMPTY")),
                "nothing is admitted where either half of the pair holds nothing, and something is"
                        + " admitted only where both were asked: the second is a claim about the"
                        + " pair, and a nest that has begun making it is a nest to read");
    }

    @Test
    void andOutsideTheReadingsThreeSayIt() {
        assertEquals(SAYS_IT_OUTSIDE_THE_READINGS, nestsSaying(saidInProduction(),
                        use -> use.said().equals("NONEMPTY")
                                && !use.nest().startsWith("souther/compiler/values/")),
                "an admission is worked out in the readings; outside them it is made at the bottom"
                        + " of the pair, by the pair itself, and read once where two branches that"
                        + " both stand are held open");
    }

    /**
     * And the walk reads a body the source does not name.
     *
     * <p>Two of the sayings are inside lambdas — the stand-in answering that the values refuse
     * nothing, so that what the ranges alone refuse can be asked. A walk that read declared methods
     * and not the synthetic ones javac writes for these would miss both and go on reporting the
     * same owner sets, since those two nests say the word elsewhere as well.
     */
    @Test
    void andTheWalkReadsALambdaBody() {
        assertEquals(List.of("souther/compiler/check/Confinement",
                        "souther/compiler/values/PlannedValues"),
                nestsSaying(saidInProduction(), use -> use.said().equals("NONEMPTY")
                        && use.method().startsWith("lambda$")),
                "these say it in a lambda body, and a walk that cannot see one is reading less than"
                        + " it reports");
    }

    /**
     * And these take the answer apart by which of the three it is.
     *
     * <p>A switch over this word reads the answer as a choice between arms rather than asking
     * whether it settles anything, and a reading owed a different thing for each of the three has
     * to. What it buys is the one shape an answer added to the three stops: read by comparisons
     * against constants, a fourth answer falls into whichever arm the last comparison left it, and
     * nobody has decided that. So these are written down one method at a time, and what the list is
     * for is that each entry is a reading somebody chose to owe every answer.
     *
     * <p>The nest and the method, since a nest holds readings that are not this one — a name would
     * be enough to tell two of them apart the day one nest switches in two places, and a descriptor
     * would be carried the day two methods of one nest share a name.
     *
     * <p>Shown with the same detector run over a body that does switch ({@link Taking}), because a
     * short expectation passes just as well when the detector has stopped working — which is what
     * the day javac writes a switch some other way would look like.
     */
    @Test
    void andTheseTakeTheAnswerApartByWhichOfTheThreeItIs() {
        assertEquals(1, Taking.by(Emptiness.NONEMPTY), "the fixture answers by switching");
        assertTrue(saidHere().stream().anyMatch(use -> use.said().equals(TAKEN_APART)),
                "the fixture beside this test switches over the word, so a detector that cannot"
                        + " find it there is one that would report none anywhere");

        assertEquals(TAKES_THE_ANSWER_APART,
                placesSaying(saidInProduction(), use -> use.said().equals(TAKEN_APART)),
                "a reading that owes each of the three answers something different says so by"
                        + " switching, and one that arrived some other way is a reading a fourth"
                        + " answer would be given a meaning by without anybody deciding it");
    }

    /**
     * And these take the answer apart by which alternatives of a choice still stand.
     *
     * <p>The other thing about this word a reader may switch over, and a different question: which
     * of the three an answer is, is about one branch, and which alternatives stand is about two.
     * A reading owed something different for each of the ways two branches can fall has to switch,
     * and one that arrived at the same four some other way would go on answering the way the
     * alternatives fell before a fifth was written.
     */
    @Test
    void andTheseTakeTheAnswerApartByWhichAlternativesStand() {
        assertEquals(3, TakingByStanding.by(Emptiness.Alternatives.BOTH_STAND),
                "the fixture answers by switching");
        assertTrue(saidHere().stream().anyMatch(
                        use -> use.said().equals(TAKEN_APART_BY_STANDING)),
                "the body beside this test switches over which alternatives stand, so a detector"
                        + " that cannot find it there is one whose green would be about production"
                        + " having stopped switching and not about the rule");

        assertEquals(TAKES_IT_APART_BY_STANDING,
                placesSaying(saidInProduction(),
                        use -> use.said().equals(TAKEN_APART_BY_STANDING)),
                "what a choice comes to differs by which of its alternatives stand, and a reading"
                        + " that owes each of them something says so by switching");
    }

    /**
     * And nothing outside these compares the word against one of its constants.
     *
     * <p>What a settled answer means is the word's own, and every meaning it has an owner for is
     * asked. A comparison is what is left when nothing was asked: it reads one of the answers by
     * name and leaves every other answer to whatever the comparison happened to say about it, so
     * an answer added to the three is given a meaning there without anybody deciding one.
     *
     * <p><b>What is left is what has no owner yet, and not what was not got round to.</b> Each of
     * these is a reading whose meaning is still to be decided — what a conjunction of two readings
     * is shown empty by, what a positive answer means beside a position nobody could build, and
     * what it means that a joined answer has reached the top of what a choice can be. Naming a
     * reading here is saying that it is one of those, and the list is short so that it can be read
     * as the open questions it is.
     *
     * <p>Shown with the same detector run over bodies that compare every way one can be written
     * ({@link Comparing}), and over one that writes a constant where a value is wanted
     * ({@link Constructing}) — an answer is made by naming one, and a detector that read every
     * naming as a reading would refuse the making of an answer.
     */
    @Test
    void andNothingElseComparesTheWordAgainstOneOfItsConstants() {
        assertTrue(Comparing.itIsEmpty(Emptiness.EMPTY), "each of the four answers what it says");
        assertTrue(Comparing.emptyIsIt(Emptiness.EMPTY));
        assertTrue(Comparing.itIsNotEmpty(Emptiness.NONEMPTY));
        assertTrue(Comparing.emptyIsNotIt(Emptiness.NONEMPTY));
        assertFalse(Comparing.itIsEmpty(Emptiness.UNDECIDED), "and each of them is a comparison,"
                + " so a body that only looked like one would hold the detector to nothing");
        assertFalse(Comparing.emptyIsIt(Emptiness.UNDECIDED));
        assertFalse(Comparing.itIsNotEmpty(Emptiness.EMPTY));
        assertFalse(Comparing.emptyIsNotIt(Emptiness.EMPTY));
        assertTrue(Comparing.itIsWhatWasPutAway(Emptiness.EMPTY),
                "and one written into a name and compared out of it is a comparison");
        assertFalse(Comparing.itIsWhatWasPutAway(Emptiness.NONEMPTY));
        assertTrue(Comparing.emptyIsWhicheverOfThese(Emptiness.EMPTY, Emptiness.NONEMPTY, true),
                "and one compared against an answer the code works out first is a comparison too");
        assertFalse(Comparing.emptyIsWhicheverOfThese(Emptiness.EMPTY, Emptiness.NONEMPTY, false));

        assertEquals(COMPARED_IN_THE_FIXTURE,
                placesSaying(saidHere(), use -> use.said().equals(COMPARED)),
                "the bodies beside this test compare it every way it can be written, so a detector"
                        + " that finds fewer is one that would let the rest past");
        assertEquals(Emptiness.UNDECIDED, Constructing.whatNobodyHasWorkedOut(),
                "and the body that writes a constant answers with it");
        assertTrue(Constructing.whatItIsCalledIs(Emptiness.EMPTY.name().intern()),
                "and the body that compares what a call made of one answers about that");
        assertTrue(saidHere().stream().anyMatch(use ->
                        use.method().equals("whatNobodyHasWorkedOut")
                                && use.said().equals("UNDECIDED")),
                "which the walk sees it naming, so its absence above is the detector telling a"
                        + " comparison from the making of an answer and not the walk missing it");

        assertEquals(COMPARED_IN_PRODUCTION,
                placesSaying(saidInProduction(), use -> use.said().equals(COMPARED)),
                "a reading that compares is one no owned meaning answered, so what is written here"
                        + " is the readings whose meaning nobody has decided yet");
    }

    /**
     * And it reads a saying that is written as a reference to one.
     *
     * <p>{@code Emptiness::isEmpty} puts no call to the word in the code that wrote it: what it
     * names is a handle among a bootstrap's arguments. A walk over calls alone would read a nest
     * observing a settled answer as one saying nothing, and nothing in production is written that
     * way today — so what holds the walk to it is a body beside this test that is.
     */
    @Test
    void andItReadsASayingWrittenAsAReference() {
        assertFalse(Referring.OBSERVES.test(Emptiness.NONEMPTY), "the fixture observes by handle");
        assertTrue(saidHere().stream().anyMatch(use -> use.said().equals("isEmpty")),
                "the fixture beside this test observes a settled answer through a reference, so a"
                        + " walk that cannot find it there is one that would let one past");

        assertTrue(Referring.STANDS.test(Emptiness.Alternatives.BOTH_STAND),
                "and the fixture asks which alternatives stand by handle");
        assertTrue(saidHere().stream().anyMatch(use -> use.said().equals("bothStand")),
                "which the walk finds as well: the two are one word between them, and a rule that"
                        + " read a reference to one and not the other would be about which of them"
                        + " a caller happened to name");
    }

    /**
     * And the walk sees classes at all, in every module the repository has.
     *
     * <p>Matched against a name nothing has, every list above would be empty and equal to an empty
     * expectation. And a module whose classes are not there is one the walk reads nothing of while
     * the lists still match — so what is asserted is that every module the reactor names was read,
     * and not only that something was.
     */
    @Test
    void andEveryModuleTheRepositoryHoldsWasRead() {
        List<String> unbuilt = new ArrayList<>();
        int read = 0;
        for (Path module : REPOSITORY.modules()) {
            Path where = classesOf(module);
            if (!classesUnder(where).isEmpty()) {
                read++;
            } else if (Files.isDirectory(module.resolve("src").resolve("main").resolve("java"))) {
                // A module holding only tests or only a pom leaves no classes and is not one this
                // walk is missing.
                unbuilt.add(module.getFileName().toString());
            }
        }

        assertEquals(List.of(), unbuilt,
                "a module whose classes are not built is one this walk passes over, and a walk that"
                        + " passes over a module answers about the rest while saying it answers"
                        + " about all of them");
        assertTrue(read > 1, "the classes this reads are in more than the one module that declares"
                + " the word");
        assertTrue(nestsSaying(saidInProduction(), _ -> true)
                        .contains("souther/compiler/check/Confinement"),
                "and the pair's own reading says the word, so a walk that cannot find it there is"
                        + " finding nothing at all");
    }

    /**
     * A body that switches over the word, for the detector to be held to.
     *
     * <p>Compiled beside this test and never among the classes the rules read, which are the
     * modules' own. What it is for is that a rule expecting none has something to be shown finding.
     */
    private enum Taking {
        ;

        static int by(Emptiness said) {
            return switch (said) {
                case EMPTY -> 0;
                case NONEMPTY -> 1;
                case UNDECIDED -> 2;
            };
        }
    }

    /**
     * A body that switches over which alternatives stand, for the other switch detector.
     *
     * <p>The rule about it names what production switches, so the rule alone would go on passing
     * the day production stopped switching and the day the detector stopped finding one — the same
     * green, for two reasons a reader could not tell apart.
     */
    private enum TakingByStanding {
        ;

        static int by(Emptiness.Alternatives standing) {
            return switch (standing) {
                case NEITHER_STANDS -> 0;
                case ONLY_THE_LEFT -> 1;
                case ONLY_THE_RIGHT -> 2;
                case BOTH_STAND -> 3;
            };
        }
    }

    /**
     * A body that observes a settled answer through a reference to the observation, for the same
     * reason.
     *
     * <p>Nothing in production is written this way today, so a walk that could not read it would go
     * on reporting the same owner sets — and the day something is, it would pass the rules without
     * being one of the nests they name.
     */
    private enum Referring {
        ;

        static final Predicate<Emptiness> OBSERVES = Emptiness::isEmpty;

        /** And which alternatives stand, which is published beside the word and is read the same
         *  ways: a rule that found one spelling and not the other would be about how a caller
         *  writes an ask and not about the ask. */
        static final Predicate<Emptiness.Alternatives> STANDS =
                Emptiness.Alternatives::bothStand;
    }

    /**
     * A body that compares the word against one of its constants, written every way it can be.
     *
     * <p>Four spellings and one reading. Which operand the constant is written as, and whether the
     * comparison is for sameness or difference, are the author's and not the rule's — a detector
     * that found one of them would let the other three past while reporting that there are none.
     */
    private enum Comparing {
        ;

        static boolean itIsEmpty(Emptiness said) {
            return said == Emptiness.EMPTY;
        }

        static boolean emptyIsIt(Emptiness said) {
            return Emptiness.EMPTY == said;
        }

        static boolean itIsNotEmpty(Emptiness said) {
            return said != Emptiness.EMPTY;
        }

        static boolean emptyIsNotIt(Emptiness said) {
            return Emptiness.EMPTY != said;
        }

        /**
         * And one written into a name and compared out of it.
         *
         * <p>A constant a clause gave a name to is the same constant, and a rule that read only
         * what is compared where it was written is one anybody gets round by writing the name.
         */
        static boolean itIsWhatWasPutAway(Emptiness said) {
            Emptiness nothingAtAll = Emptiness.EMPTY;
            return said == nothingAtAll;
        }

        /**
         * And one compared against something the code has to work out first.
         *
         * <p>The constant is pushed and the comparison that takes it is several jumps away, with
         * the branches that choose the other side in between. Which is what a walk that reads the
         * instructions standing near the constant cannot tell from a constant written where a
         * value is wanted: both have something other than a comparison after them.
         */
        static boolean emptyIsWhicheverOfThese(Emptiness one, Emptiness other, boolean take) {
            return Emptiness.EMPTY == (take ? one : other);
        }
    }

    /**
     * And a body that writes a constant where a value is wanted, which is not a comparison.
     *
     * <p>The other side of the same rule. An answer is made by naming one, so a detector that read
     * every naming as a reading would refuse the one thing every maker of an answer has to do.
     */
    private enum Constructing {
        ;

        static Emptiness whatNobodyHasWorkedOut() {
            return Emptiness.UNDECIDED;
        }

        /**
         * And one written where a call wants it, whose answer is then compared.
         *
         * <p>The call takes the constant and leaves something else, and the comparison after it is
         * about what the call gave. Read as the height of the stack the two look alike — a call
         * that takes a receiver and returns a value leaves the stack where it found it — so a walk
         * that counted would report the constant as compared when what is compared is a string.
         */
        static boolean whatItIsCalledIs(String text) {
            return Emptiness.EMPTY.name() == text;
        }
    }

    /** The nests of the sayings {@code which} keeps, each once and in one order. */
    private static List<String> nestsSaying(List<Use> said, Predicate<Use> which) {
        Set<String> out = new TreeSet<>();
        said.stream().filter(which).forEach(use -> out.add(use.nest()));
        return new ArrayList<>(out);
    }

    /** The one method each of the sayings {@code which} keeps is in, for a rule about readings. */
    private static List<String> placesSaying(List<Use> said, Predicate<Use> which) {
        Set<String> out = new TreeSet<>();
        said.stream().filter(which).forEach(use -> out.add(use.place()));
        return new ArrayList<>(out);
    }

    private static Path classesOf(Path module) {
        return module.resolve("target").resolve("classes");
    }

    /** The two walks, each read once. Every rule here asks the same question of the same class
     *  files, and nothing writes one while this runs, so a walk per rule is the same answer read
     *  again — over every class of every module each time. */
    private static List<Use> inProduction;

    private static List<Use> here;

    /** Every saying of the word in the reactor's own compiled classes. */
    private static List<Use> saidInProduction() {
        if (inProduction == null) {
            List<Path> where = new ArrayList<>();
            for (Path module : REPOSITORY.modules()) {
                where.add(classesOf(module));
            }
            inProduction = List.copyOf(saidUnder(where));
        }
        assertFalse(inProduction.isEmpty(), "no saying of the word was read at all");
        return inProduction;
    }

    /** Every saying in the classes compiled beside this test, which is the fixture above. */
    private static List<Use> saidHere() {
        if (here == null) {
            try {
                here = List.copyOf(saidUnder(List.of(Path.of(
                        WhoMaySayThatAPositionAdmitsSomethingIsWrittenDownTest.class
                                .getProtectionDomain().getCodeSource().getLocation().toURI()))));
            } catch (URISyntaxException notAPath) {
                throw new IllegalStateException("this test's own classes are somewhere unreadable",
                        notAPath);
            }
        }
        return here;
    }

    /**
     * Every saying of the word under {@code roots}.
     *
     * <p>The word's own class is passed over by the rules about who says it, and by them only:
     * what an enum's constants do among themselves is how one is written, and read as sayings they
     * would put the word on every list as a namer of itself. Which reading of one a place is
     * is asked of it as well — see the comment where that is done.
     */
    private static List<Use> saidUnder(List<Path> roots) {
        List<Use> found = new ArrayList<>();
        for (Path root : roots) {
            for (Path each : classesUnder(root)) {
                byte[] bytes = bytesOf(each);
                // Cheap first, so the code of a class with nothing to do with this is never walked.
                // The filter admits more than these rules are about — another type in `check` is
                // called the same — and refuses nothing that could match, which is the direction it
                // has to err in.
                if (!holdsTheWord(bytes)) {
                    continue;
                }
                ClassModel model = ClassFile.of().parse(bytes);
                String holds = model.thisClass().asInternalName();
                String nest = nestOf(holds);
                // The word's own class is passed over by the rules about who says it, and by them
                // only: what an enum's constants do among themselves is how one is written, and
                // read as sayings they would put the word on every list as a namer of itself. How
                // one of the answers is read is another question and is asked of the word too — the
                // meanings are worked out there, so a comparison there is a meaning nobody decided
                // in the one place that decides them.
                boolean isTheWord = nest.equals(EMPTINESS);
                for (MethodModel method : model.methods()) {
                    CodeModel code = method.code().orElse(null);
                    if (code == null) {
                        continue;
                    }
                    String where = method.methodName().stringValue();
                    String spelt = method.methodType().stringValue();
                    List<CodeElement> elements = new ArrayList<>();
                    code.forEach(elements::add);
                    for (int at = 0; at < elements.size(); at++) {
                        if (!isTheWord) {
                            for (String what : saidBy(elements.get(at), holds)) {
                                found.add(new Use(nest, holds, where, spelt, what));
                            }
                        }
                        if (comparesAConstant(elements, at)) {
                            found.add(new Use(nest, holds, where, spelt, COMPARED));
                        }
                    }
                }
            }
        }
        return found;
    }

    /**
     * What one instruction says of the word, where it says anything.
     *
     * <p>A reference is the call it stands for. {@code Emptiness::isEmpty} puts no call to the word
     * in the code that wrote it — what it names is a handle among a bootstrap's arguments — so a
     * walk over calls alone reads a nest that observes a settled answer as one that says nothing.
     *
     * <p>And a switch's table is a saying where it is read and not where it is filled. The
     * synthetic class javac writes to hold one fills it in its own initializer, which is how a
     * table is made rather than a reading of the word — counted, every switch would be answered for
     * twice, once by whoever switched and once by nobody.
     */
    private static List<String> saidBy(CodeElement element, String holds) {
        if (element instanceof FieldInstruction field) {
            String named = field.name().stringValue();
            if (named.equals(TAKEN_APART) || named.equals(TAKEN_APART_BY_STANDING)) {
                return field.owner().asInternalName().equals(holds) ? List.of() : List.of(named);
            }
            return field.owner().asInternalName().equals(EMPTINESS)
                    ? List.of(named) : List.of();
        }
        if (element instanceof InvokeInstruction call) {
            String owner = call.owner().asInternalName();
            return owner.equals(EMPTINESS) || owner.equals(STANDING)
                    ? List.of(call.name().stringValue()) : List.of();
        }
        if (element instanceof InvokeDynamicInstruction lambda) {
            List<String> out = new ArrayList<>();
            for (var argument : lambda.bootstrapArgs()) {
                if (argument instanceof DirectMethodHandleDesc handle
                        && (named(handle.owner()).equals(EMPTINESS)
                                || named(handle.owner()).equals(STANDING))) {
                    // Whichever kind of handle it is, what it names is what the code would have
                    // said had it been written out: a field for a constant, a method for the rest.
                    out.add(handle.methodName());
                }
            }
            return out;
        }
        return List.of();
    }

    /**
     * Whether the instruction at {@code at} pushes one of the word's constants to compare it.
     *
     * <p>A constant written where a value is wanted is not a reading of the word — an answer is
     * made by naming one, and that is how one is made. What this is about is a constant pushed so
     * that something can be told from it, which javac writes as the constant and then a comparison
     * of two references. So the pair is read and not the constant alone, and which side of the
     * comparison the constant was written on does not matter: the other operand is worked out
     * between them either way, and what is looked for is the comparison this constant reaches.
     */
    private static boolean comparesAConstant(List<CodeElement> elements, int at) {
        if (!(elements.get(at) instanceof FieldInstruction field)
                || field.opcode() != Opcode.GETSTATIC
                || !field.owner().asInternalName().equals(EMPTINESS)) {
            return false;
        }
        return WhatBecomesOfAValueOnTheStack.isTakenByAReferenceComparison(elements, at);
    }

    /** What a descriptor names, as a class is named in a class file. */
    private static String named(ClassDesc owner) {
        String descriptor = owner.descriptorString();
        return descriptor.startsWith("L") && descriptor.endsWith(";")
                ? descriptor.substring(1, descriptor.length() - 1) : descriptor;
    }

    /** Whether the bytes name the word anywhere at all. */
    private static boolean holdsTheWord(byte[] bytes) {
        byte[] word = "Emptiness".getBytes(StandardCharsets.US_ASCII);
        for (int start = 0; start + word.length <= bytes.length; start++) {
            int at = 0;
            while (at < word.length && bytes[start + at] == word[at]) {
                at++;
            }
            if (at == word.length) {
                return true;
            }
        }
        return false;
    }

    /** What a class is written inside: a helper, a lambda's synthetic method and a switch's
     *  synthetic table are all part of the type they were written in. */
    private static String nestOf(String internalName) {
        int nested = internalName.indexOf('$');
        return nested < 0 ? internalName : internalName.substring(0, nested);
    }

    private static byte[] bytesOf(Path compiled) {
        try {
            return Files.readAllBytes(compiled);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static List<Path> classesUnder(Path where) {
        if (!Files.isDirectory(where)) {
            return List.of();
        }
        try (Stream<Path> found = Files.walk(where)) {
            return found.filter(each -> each.toString().endsWith(".class")).toList();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
