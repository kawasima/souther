package souther.architecture;

import souther.compiler.values.Emptiness;
import souther.test.CompiledClasses;

import org.junit.jupiter.api.Test;

import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Predicate;

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
 * without spelling one. Inside it is who may decide the positive answer. Inside that is the one the
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

    /** The two answers that settle something, which is what these rules are about.
     *  {@code UNDECIDED} settles nothing and is named freely. */
    private static final Set<String> SETTLED = Set.of("EMPTY", "NONEMPTY");

    /** The operations that read whether an answer is settled, carry one onwards, or hand one out. A
     *  caller of these holds a settled answer without naming one, which is why calling them counts
     *  as saying it — a walk asking an operation where it starts is handed whichever answer that is,
     *  and one asking where it stops is holding the answer it stopped on. */
    private static final Set<String> OBSERVES_OR_COMPOSES =
            Set.of("isEmpty", "isDecided", "met", "joined", "of", "bothStand",
                    "identityForMeet", "identityForJoin", "endsAMeet", "endsAJoin");

    /** What javac writes for a switch over this word: a synthetic table of its constants, read by
     *  whoever switched. Taking the answer apart by which of the three it is, under a spelling that
     *  names no constant in the code that does it. */
    private static final String TAKEN_APART = Word.ANSWERS.switchTable();

    /** The same for a switch over which alternatives stand, which is the other thing a reader may
     *  take apart and is a different question from which of the three an answer is. */
    private static final String TAKEN_APART_BY_STANDING = Word.STANDING.switchTable();

    /** And for a switch over which of two answers were shown empty, which is the observation the
     *  connectives share rather than any of their readings of it. */
    private static final String TAKEN_APART_BY_SIDES_SHOWN_EMPTY =
            Word.SIDES_SHOWN_EMPTY.switchTable();

    /** A comparison of one of these against one of its constants, which is a reading of the word
     *  that no owned operation answered and that an answer added to the three would fall through
     *  without anybody deciding. Found by following what the constant becomes
     *  ({@link WhatBecomesOfAValueOnTheStack}), because what compares a value is whatever takes it
     *  off the stack and not whatever is written near it. */
    private static final String COMPARED = Word.ANSWERS.compared();

    /**
     * The words these rules are about: the answers, and each reading of two of them published beside
     * them.
     *
     * <p><b>One list, because every rule here asks the same three things.</b> Who names a word's
     * constants, who takes it apart by switching, and who compares one against a constant are asked
     * of each of these, and each answer is derived from the name rather than written beside the
     * question. Named at the questions instead, a word added here arrives covered by whichever of
     * them its author happened to make compile — which is how a reading of two answers came to be
     * counted where it switched and invisible where it compared.
     *
     * <p>What is not a word here is an operation over one. {@code isEmpty} and {@code bothStand} are
     * readings this vocabulary owns and answer for themselves; a word is something whose constants a
     * reader could name.
     */
    private enum Word {

        /** Whether a position admits anything. */
        ANSWERS(EMPTINESS),

        /** Which alternatives of a choice anybody can still be in. */
        STANDING(EMPTINESS + "$Alternatives"),

        /** Which of two answers were shown empty. */
        SIDES_SHOWN_EMPTY(EMPTINESS + "$SidesShownEmpty");

        private final String internalName;

        Word(String internalName) {
            this.internalName = internalName;
        }

        /** The word, as a class is named in a class file. */
        String internalName() {
            return internalName;
        }

        /** What javac calls the synthetic table a switch over this word reads. */
        String switchTable() {
            return "$SwitchMap$" + internalName.replace('/', '$');
        }

        /** What a comparison of one of this word's constants is said as. */
        String compared() {
            return "compared:" + internalName;
        }

        /** Which of these a class file names, where it names one. */
        static Word of(String internalName) {
            for (Word word : values()) {
                if (word.internalName.equals(internalName)) {
                    return word;
                }
            }
            return null;
        }
    }

    /**
     * The body beside this test that compares a constant of {@code word}, and what it answered.
     *
     * <p>Called and not only read. Every detector here is held to a body that exercises it, and a
     * body nothing calls is one whose behaviour nothing checked: it could compare the wrong constant,
     * or hold two references apart some other way, and the walk would go on reporting that it found a
     * comparison of this word. So the control is asked what it says, both of the ways below.
     *
     * <p>A switch over every word, for the same reason {@link #mayCompare} is one: a word added to
     * the vocabulary cannot compile until something beside this test compares one of its constants,
     * and a rule with no control is a rule whose green is about the detector and not about production.
     */
    private static boolean comparesAConstantOf(Word word) {
        return switch (word) {
            case ANSWERS -> Comparing.itIsEmpty(Emptiness.EMPTY);
            case STANDING -> Comparing.onlyTheLeftStands(Emptiness.Alternatives.ONLY_THE_LEFT);
            case SIDES_SHOWN_EMPTY ->
                    Comparing.theLeftWasShownEmpty(Emptiness.SidesShownEmpty.THE_LEFT);
        };
    }

    /** The same control handed something the constant is not, which is what says it compares. */
    private static boolean comparesAConstantOfAgainstAnother(Word word) {
        return switch (word) {
            case ANSWERS -> Comparing.itIsEmpty(Emptiness.NONEMPTY);
            case STANDING -> Comparing.onlyTheLeftStands(Emptiness.Alternatives.BOTH_STAND);
            case SIDES_SHOWN_EMPTY ->
                    Comparing.theLeftWasShownEmpty(Emptiness.SidesShownEmpty.NEITHER);
        };
    }

    /**
     * Which readings of {@code word} may compare one of its constants, and where that is being
     * decided.
     *
     * <p>A switch over every word, so that a word added to the vocabulary cannot compile until
     * somebody has said whether anything may read it that way. The answers carry the readings this
     * compiler has not decided yet; the two readings of two answers carry none, and that is the whole
     * of why the classification exists — a connective that compared instead of switching would be
     * spending a meaning no exhaustive reading had to give it.
     */
    private static List<Open> mayCompare(Word word) {
        return switch (word) {
            case ANSWERS -> COMPARED_IN_PRODUCTION;
            case STANDING, SIDES_SHOWN_EMPTY -> List.of();
        };
    }

    private static final CompiledOutputs COMPILED = CompiledOutputs.ofWhatThisRepositoryPublishes();

    /**
     * Every nest that says a position is settled one way or the other.
     *
     * <p>The readings that work an admission out, and nothing else. {@code Carrier} and
     * {@code TextExtents} answer what a set of values and a range come to between them, which is
     * the bottom of the pair; {@code Confinement} owns the pair and the walk over the alternatives
     * that both halves are asked along. {@code AdmissibleValues},
     * {@code ConjoinedAdmissibleValues} and {@code PlannedValues} are that walk over what a reading
     * holds, and {@code Apartness} is what the denials between two blocks come to.
     * {@code LeftUnbuilt} says what a reading short of a position leaves an answer about it.
     * {@code StatedByClauses} and {@code Settlement} put the answers of a choice's branches
     * together, and {@code StringMachineAnswers} keeps an answer once somebody has looked.
     *
     * <p>{@code Realized} is not here, and it is what holds a position nobody could build. What
     * that leaves an answer is {@code LeftUnbuilt}'s and is said there once, so the value carrying
     * the reading says nothing about the answers at all — which is what leaving this list is.
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
            "souther/compiler/values/LeftUnbuilt",
            "souther/compiler/values/PlannedValues",
            "souther/compiler/values/StringMachineAnswers",
            "souther/compiler/values/TextExtents");

    /**
     * And who may decide that the settled positive answer is the answer.
     *
     * <p>{@code Settlement} is one nest that says a position is settled without ever deciding that
     * something is admitted: it joins two branches' answers and reads whether the join came out
     * empty. A nest arriving here is one that has begun to claim something exists, which is what
     * these rules are about.
     *
     * <p><b>Returning the answer is not deciding it.</b> The walks over what a reading holds hand a
     * positive answer back out of every alternative that stands, and the answers they hand back are
     * the one the caller's question gave them and the one the arithmetic starts a walk from
     * ({@link #COMPOSES_SEVERAL_ANSWERS}) — an empty conjunction comes out positive because that is
     * where a meet begins, which the word decides and the walk carries. What puts a nest here is
     * saying in its own words which answer is the answer: {@code PlannedValues} does, for a position
     * whose plan is already a set and needs no machine.
     *
     * <p>{@code LeftUnbuilt} is above and not here, for that reason. What a reading that built
     * everything leaves an answer is the answer a meet begins from, and it asks the word for it —
     * naming it there would be this list gaining an entry whose whole content is that the identity
     * happens to be the positive answer.
     */
    private static final List<String> SAYS_SOMETHING_IS_ADMITTED = List.of(
            "souther/compiler/check/Carrier",
            "souther/compiler/check/Confinement",
            "souther/compiler/check/StatedByClauses",
            "souther/compiler/values/Apartness",
            "souther/compiler/values/PlannedValues",
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
     *
     * <p>And one that is owed a different number rather than a different reading: how many
     * alternatives each of a declaration's choices was left with, which is what a reader asking
     * what a compile did with them counts. It is here because the counting has to happen once —
     * the four ways two branches fall divide into three a count is kept for, and a reader given
     * the fates and left to sort them would be a second place holding that division, to be told
     * again the day a fifth way is written. It leaves no branch and reads no position, and it is
     * written where the fates are.
     */
    private static final List<String> TAKES_IT_APART_BY_STANDING = List.of(
            "souther/compiler/check/Settlement#howTheChoicesFell"
                    + "()Lsouther/compiler/check/Settlement$HowTheChoicesFell;",
            "souther/compiler/check/StatedByClauses$Reading#chosen"
                    + "(Lsouther/compiler/check/StatedByClauses$Either;Ljava/util/Map;)"
                    + "Lsouther/compiler/check/StatedTogether;",
            "souther/compiler/check/StatedByClauses$Reading#decided"
                    + "(Lsouther/compiler/check/StatedTogether$Said;"
                    + "Lsouther/compiler/check/Settlement$Sided;"
                    + "Lsouther/compiler/check/StatedTogether$Said;"
                    + "Lsouther/compiler/check/Settlement$Sided;)"
                    + "Lsouther/compiler/check/StatedTogether$Said;");

    /**
     * And the readings of which sides two answers show empty.
     *
     * <p>The observation two connectives share, and the whole list of what reads it. A conjunct
     * shown empty decides the conjunction and its proof is what carries; an alternative shown empty
     * drops out of a choice and its proof goes with it. So each of these owes something of its own
     * to all four cases and switches, and a third connective arriving is one that has to be written
     * down here before it can read the observation at all.
     *
     * <p>One of them, because a choice's reading is published beside the word
     * ({@link Emptiness.Alternatives#from}) and the walk passes over the word's own nest, where the
     * meanings are worked out. So what this list holds is every reading outside it, and today that
     * is the conjunction alone.
     */
    private static final List<String> TAKES_IT_APART_BY_SIDES_SHOWN_EMPTY = List.of(
            "souther/compiler/check/Confinement#eitherShown"
                    + "(Lsouther/compiler/check/Confinement$Admission;"
                    + "Lsouther/compiler/check/Confinement$Admission;)"
                    + "Lsouther/compiler/check/Confinement$Admission;");

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
                    + "(Lsouther/compiler/values/Emptiness;)Z",
            "souther/architecture/WhoMaySayThatAPositionAdmitsSomethingIsWrittenDownTest$Comparing#itIsWhatWasPutAwayGoingRound"
                    + "(Lsouther/compiler/values/Emptiness;"
                    + "Lsouther/compiler/values/Emptiness;I)Z",
            "souther/architecture/WhoMaySayThatAPositionAdmitsSomethingIsWrittenDownTest$Comparing#itIsWhatWasPutAwayUnlessSomethingElseWas"
                    + "(Lsouther/compiler/values/Emptiness;"
                    + "Lsouther/compiler/values/Emptiness;Z)Z",
            "souther/architecture/WhoMaySayThatAPositionAdmitsSomethingIsWrittenDownTest$Comparing#itIsWhatWasPutAwayWhereSomethingWasCaught"
                    + "(Lsouther/compiler/values/Emptiness;Ljava/lang/RuntimeException;)Z");

    /**
     * One reading whose meaning nobody has decided yet, and which question it is.
     *
     * <p>The question is carried by the reading and not written beside it. What orders this list is
     * how the walk's answer sorts, which is nobody's to arrange, so a grouping written between the
     * entries says an adjacency the order does not give — and says it wrongly the first time an
     * entry sorts into the middle of another group.
     *
     * @param place    the one method, as a permission is written
     * @param question where the question this reading is is being decided
     */
    private record Open(String place, String question) {}

    /**
     * And the readings of this word whose meaning nobody has decided yet, which are none.
     *
     * <p>Every reading of one of these answers is an operation the word owns or a switch over all
     * of them, so an answer added to the three is told what it means to each reader before anything
     * compiles.
     *
     * <p><b>Empty, and what says the walk still works is beside this test.</b> A list nothing is in
     * is a list a detector that stopped finding anything would also fill, so what holds this one to
     * meaning something is {@link #COMPARED_IN_THE_FIXTURE}: bodies here comparing every way one
     * can be written, found by the same walk in the same run.
     */
    private static final List<Open> COMPARED_IN_PRODUCTION = List.of();

    /** The operations that make one of these answers out of two. Where a walk starts and where it
     *  stops are questions asked about an operation and answer for none of it, so a place that asks
     *  them and composes nothing is not composing. */
    private static final Set<String> COMPOSES = Set.of("met", "joined");

    /**
     * And the readings that make one of these answers out of several.
     *
     * <p>Out of several and into one of them, which is not every way two are read together:
     * {@link Emptiness.SidesShownEmpty} takes two and answers which of them were shown empty, and
     * what comes back is an observation for a connective to value rather than an answer about a
     * position. A reading of that is a reading of the classification, and this is about the
     * arithmetic.
     *
     * <p>Three of them walk, and one shape between the three: an alternative stands where every
     * block of it does, and a reading stands where any alternative does. Each starts where the
     * operation it walks under starts and stops where that operation can no longer be moved, and
     * asks the word for both — a walk that named either would be saying the arithmetic's fact in its
     * own words, true of these three answers by a coincidence nobody wrote down. The other two
     * compose two answers and no more, so there is no place in either to start from or stop at:
     * what two occurrences of one branch come to is one call, and so is what a reading short of a
     * position leaves an answer about it.
     *
     * <p><b>Directly, which is the whole of what this holds.</b> What each of these does with the
     * answers it put together is its own, and no walk here follows a call into what it calls. A
     * reading that handed the composing to something beside it would leave here and that something
     * would arrive, which is the finding to read: the composing moved, and where the questions a
     * fold asks are answered moved with it.
     *
     * <p>Written down because the shape is what makes such a walk possible and nothing about a walk
     * announces it. What holds these to it is the operations being ones a fold may be taken over at
     * all, which is asked where they are written. A reading arriving here is walking over answers
     * whose order is somebody's, and is entitled to what these are entitled to only if it is the
     * same shape.
     */
    private static final List<String> COMPOSES_SEVERAL_ANSWERS = List.of(
            "souther/compiler/check/Settlement$Sided#alsoSeen"
                    + "(Lsouther/compiler/check/Settlement$Sided;)"
                    + "Lsouther/compiler/check/Settlement$Sided;",
            "souther/compiler/values/AdmissibleValues#anyAlternativeAdmits"
                    + "(Lsouther/compiler/values/AskedOfEachBlock;"
                    + "Lsouther/compiler/values/AskedOfARelation;)"
                    + "Lsouther/compiler/values/Emptiness;",
            "souther/compiler/values/ConjoinedAdmissibleValues#anyAlternativeAdmits"
                    + "(Lsouther/compiler/values/AskedOfEachBlock;"
                    + "Lsouther/compiler/values/AskedOfARelation;)"
                    + "Lsouther/compiler/values/Emptiness;",
            "souther/compiler/values/LeftUnbuilt#hold"
                    + "(Lsouther/compiler/values/Emptiness;)"
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

    @Test
    void andTheseAreTheReadingsThatMakeOneOfThemOutOfSeveral() {
        assertEquals(COMPOSES_SEVERAL_ANSWERS,
                placesSaying(saidInProduction(), use -> COMPOSES.contains(use.said())),
                "a walk that takes these answers in one at a time answers about a set with a walk"
                        + " over one order of it, and stops on reaching what the operation cannot be"
                        + " moved from: a reading arriving here is one answering out of several"
                        + " answers, and what says it may is where the operations are written");
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
     * And these read which sides two answers show empty, which is the observation and not a reading.
     *
     * <p>The list every connective has to be written into. Which sides were shown empty is one fact
     * and what it is worth is the connective's — opposite things for a choice and a conjunction — so
     * a reading arriving here is a connective this compiler has begun answering for, and one that
     * arrived at the same four some other way would be spending a meaning nobody gave it.
     */
    @Test
    void andTheseReadWhichSidesWereShownEmpty() {
        assertEquals(2, TakingBySidesShownEmpty.by(Emptiness.SidesShownEmpty.THE_RIGHT),
                "the fixture answers by switching");
        assertTrue(saidHere().stream().anyMatch(
                        use -> use.said().equals(TAKEN_APART_BY_SIDES_SHOWN_EMPTY)),
                "the body beside this test switches over which sides were shown empty, so a"
                        + " detector that cannot find it there is one whose green would be about"
                        + " production having stopped switching and not about the rule");

        assertEquals(TAKES_IT_APART_BY_SIDES_SHOWN_EMPTY,
                placesSaying(saidInProduction(),
                        use -> use.said().equals(TAKEN_APART_BY_SIDES_SHOWN_EMPTY)),
                "what a connective makes of the observation is the connective's, and a reading that"
                        + " owes each of the four cases something says so by switching");
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
        assertTrue(Comparing.itIsWhatWasPutAwayUnlessSomethingElseWas(
                        Emptiness.EMPTY, Emptiness.NONEMPTY, false),
                "and one out of a name another way round writes over is a comparison on the way"
                        + " that did not — and what writes over it is no constant, so this body is"
                        + " a comparison for this constant or for none");
        assertTrue(Comparing.itIsWhatWasPutAwayUnlessSomethingElseWas(
                Emptiness.NONEMPTY, Emptiness.NONEMPTY, true));
        assertTrue(Comparing.itIsWhatWasPutAwayGoingRound(Emptiness.EMPTY, Emptiness.NONEMPTY, 1),
                "and one written into a name going round a loop is compared after it");
        assertFalse(Comparing.itIsWhatWasPutAwayGoingRound(Emptiness.EMPTY, Emptiness.NONEMPTY, 0));
        assertTrue(Comparing.itIsWhatWasPutAwayWhereSomethingWasCaught(
                        Emptiness.EMPTY, new IllegalStateException("thrown")),
                "and one compared where what was thrown is caught is compared there");
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

        for (Word word : Word.values()) {
            assertTrue(comparesAConstantOf(word),
                    () -> "the body beside this test answers for a constant of " + word);
            assertFalse(comparesAConstantOfAgainstAnother(word),
                    () -> "and it is a comparison, so a body of " + word + " that only looked like"
                            + " one would hold the detector to nothing");
            assertTrue(saidHere().stream().anyMatch(use -> use.said().equals(word.compared())),
                    () -> "the bodies beside this test compare a constant of " + word + ", so a"
                            + " detector that cannot find one there is one that would report none of"
                            + " them anywhere");
            assertEquals(mayCompare(word).stream().map(Open::place).sorted().toList(),
                    placesSaying(saidInProduction(), use -> use.said().equals(word.compared())),
                    () -> "a reading that compares is one no owned meaning answered, so what is"
                            + " written here is the readings of " + word + " whose meaning nobody has"
                            + " decided yet, each with where it is being decided:\n"
                            + questionsBeingDecided(word));
        }
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

        assertEquals(Emptiness.SidesShownEmpty.NEITHER,
                Referring.SORTS.apply(Emptiness.NONEMPTY, Emptiness.UNDECIDED),
                "and the fixture sorts two answers by handle");
        assertTrue(saidHere().stream().anyMatch(use -> use.said().equals("of")),
                "and that as well, which is what says the branch asks the words and not the two of"
                        + " them somebody wrote into it");
    }

    /**
     * And every word of the vocabulary is in the nest the walk's two coarse questions name.
     *
     * <p>Two of the walk's questions are about a nest and not about a word: which classes are worth
     * reading at all ({@code holdsTheWord}), and whose own code works out the meanings and is
     * therefore passed over ({@code isTheWord}). Both name {@code Emptiness}, which answers for every
     * word today because each of them is written inside it.
     *
     * <p>So the assumption is checked rather than relied on. A word put beside the answers instead of
     * inside them would be read by neither question — its classes skipped by the first and its
     * meanings counted as somebody else's by the second — and every list above would go on matching.
     */
    @Test
    void andEveryWordIsWrittenInsideTheNestThoseQuestionsName() {
        for (Word word : Word.values()) {
            assertEquals(EMPTINESS, nestOf(word.internalName()),
                    () -> word + " is not written inside the nest the walk reads and passes over, so"
                            + " a rule about it would be about whichever of the two questions its"
                            + " author noticed");
        }
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
        int read = 0;
        for (Path module : COMPILED.modules()) {
            // A module holding only tests or only a pom leaves no classes and is not one this walk
            // is missing. One that has sources and left none is refused where the outputs are
            // taken, so a walk reading fewer modules than the repository has does not get here.
            if (!COMPILED.classesOf(module).isEmpty()) {
                read++;
            }
        }

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
     * A body that switches over which sides were shown empty, for the detector that finds those.
     *
     * <p>Here for the same reason as the one above: the rule names what production switches, and a
     * rule with nothing beside it goes on passing the day javac writes a switch some other way.
     */
    private enum TakingBySidesShownEmpty {
        ;

        static int by(Emptiness.SidesShownEmpty shown) {
            return switch (shown) {
                case NEITHER -> 0;
                case THE_LEFT -> 1;
                case THE_RIGHT -> 2;
                case BOTH -> 3;
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

        /** And the classification the connectives read, for the same reason: a handle names a word
         *  the same ways a call does, and a branch that knew two of the words would read a reference
         *  to the third as naming nothing. */
        static final BiFunction<Emptiness, Emptiness, Emptiness.SidesShownEmpty> SORTS =
                Emptiness.SidesShownEmpty::of;
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

        /** And a constant of each reading of two answers, which the rule refuses production any of:
         *  a connective owes something to all four cases and says so by switching, so a comparison
         *  there is a case left whatever the comparison happened to leave it. */
        static boolean onlyTheLeftStands(Emptiness.Alternatives standing) {
            return standing == Emptiness.Alternatives.ONLY_THE_LEFT;
        }

        static boolean theLeftWasShownEmpty(Emptiness.SidesShownEmpty shown) {
            return shown == Emptiness.SidesShownEmpty.THE_LEFT;
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
         * And one written into a name that another way round writes over.
         *
         * <p>The second writing is on one way through and not on the other, so what is compared is
         * this constant wherever the condition was not taken. Read as the writing that stands
         * nearest before the comparison, the name would be read as holding the other constant on
         * every way, and this comparison would be one nothing sees.
         */
        static boolean itIsWhatWasPutAwayUnlessSomethingElseWas(Emptiness said, Emptiness other,
                                                                boolean instead) {
            Emptiness nothingAtAll = Emptiness.EMPTY;
            if (instead) {
                nothingAtAll = other;
            }
            return said == nothingAtAll;
        }

        /**
         * And one written into a name inside a loop and compared after it.
         *
         * <p>The way from the writing to the comparison goes backwards first, round the loop and
         * out of it. Followed only forwards from the writing, that way is not there at all, and the
         * comparison after the loop is one nothing reads.
         */
        static boolean itIsWhatWasPutAwayGoingRound(Emptiness said, Emptiness other, int times) {
            Emptiness nothingAtAll = other;
            for (int each = 0; each < times; each++) {
                nothingAtAll = Emptiness.EMPTY;
            }
            return said == nothingAtAll;
        }

        /**
         * And one compared where what was thrown is caught.
         *
         * <p>A name holds what it held when the throwing began, so the way to the handler is a way
         * the value travels. Followed only along what carries on, the throw ends the way and the
         * comparison written in the handler is one nothing reads.
         */
        static boolean itIsWhatWasPutAwayWhereSomethingWasCaught(Emptiness said,
                                                                RuntimeException thrown) {
            Emptiness nothingAtAll = Emptiness.EMPTY;
            try {
                throw thrown;
            } catch (RuntimeException caught) {
                return said == nothingAtAll;
            }
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

    /** Where each reading that compares is being decided, for whoever the rule above stopped: an
     *  entry added to that list is a question somebody is deciding, and an entry that has gone is
     *  one that was. */
    private static String questionsBeingDecided(Word word) {
        StringBuilder out = new StringBuilder();
        mayCompare(word).forEach(open ->
                out.append("  ").append(open.place()).append("  ").append(open.question())
                        .append('\n'));
        return out.toString();
    }

    /** The one method each of the sayings {@code which} keeps is in, for a rule about readings. */
    private static List<String> placesSaying(List<Use> said, Predicate<Use> which) {
        Set<String> out = new TreeSet<>();
        said.stream().filter(which).forEach(use -> out.add(use.place()));
        return new ArrayList<>(out);
    }



    /** The two walks, each read once. Every rule here asks the same question of the same class
     *  files, and nothing writes one while this runs, so a walk per rule is the same answer read
     *  again — over every class of every module each time. */
    private static List<Use> inProduction;

    private static List<Use> here;

    /** Every saying of the word in the reactor's own compiled classes. */
    private static List<Use> saidInProduction() {
        if (inProduction == null) {
            inProduction = List.copyOf(saidUnder(COMPILED.all()));
        }
        assertFalse(inProduction.isEmpty(), "no saying of the word was read at all");
        return inProduction;
    }

    /** Every saying in the classes compiled beside this test, which is the fixture above. */
    private static List<Use> saidHere() {
        if (here == null) {
            here = List.copyOf(saidUnder(CompiledClasses.ofModule(
                    WhoMaySayThatAPositionAdmitsSomethingIsWrittenDownTest.class).all()));
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
    private static List<Use> saidUnder(List<ClassModel> classes) {
        List<Use> found = new ArrayList<>();
        for (ClassModel model : classes) {
            // Cheap first, so the code of a class with nothing to do with this is never walked.
            // The filter admits more than these rules are about — another type in `check` is
            // called the same — and refuses nothing that could match, which is the direction it
            // has to err in.
            if (!holdsTheWord(model)) {
                continue;
            }
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
                    String compared = comparesAConstant(elements, at);
                    if (compared != null) {
                        found.add(new Use(nest, holds, where, spelt, compared));
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
            if (isASwitchTable(named)) {
                return field.owner().asInternalName().equals(holds) ? List.of() : List.of(named);
            }
            // And a constant of any of the words, named. Asked of the words rather than of the
            // answers alone: a reading of two answers holds constants too, and a walk that knew only
            // the answers read a nest that names one as a nest that names nothing.
            return Word.of(field.owner().asInternalName()) != null ? List.of(named) : List.of();
        }
        if (element instanceof InvokeInstruction call) {
            String owner = call.owner().asInternalName();
            return Word.of(owner) != null ? List.of(call.name().stringValue()) : List.of();
        }
        if (element instanceof InvokeDynamicInstruction lambda) {
            List<String> out = new ArrayList<>();
            for (var argument : lambda.bootstrapArgs()) {
                if (argument instanceof DirectMethodHandleDesc handle
                        && Word.of(named(handle.owner())) != null) {
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
    private static String comparesAConstant(List<CodeElement> elements, int at) {
        if (!(elements.get(at) instanceof FieldInstruction field)
                || field.opcode() != Opcode.GETSTATIC) {
            return null;
        }
        Word word = Word.of(field.owner().asInternalName());
        if (word == null) {
            return null;
        }
        return WhatBecomesOfAValueOnTheStack.isTakenByAReferenceComparison(elements, at)
                ? word.compared() : null;
    }

    /** Whether {@code named} is the table a switch over one of the words reads. */
    private static boolean isASwitchTable(String named) {
        for (Word word : Word.values()) {
            if (named.equals(word.switchTable())) {
                return true;
            }
        }
        return false;
    }

    /** What a descriptor names, as a class is named in a class file. */
    private static String named(ClassDesc owner) {
        String descriptor = owner.descriptorString();
        return descriptor.startsWith("L") && descriptor.endsWith(";")
                ? descriptor.substring(1, descriptor.length() - 1) : descriptor;
    }

    /** Whether the bytes name the word anywhere at all. */
    private static boolean holdsTheWord(ClassModel model) {
        for (PoolEntry entry : model.constantPool()) {
            if (entry instanceof Utf8Entry said && said.stringValue().contains("Emptiness")) {
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




}
