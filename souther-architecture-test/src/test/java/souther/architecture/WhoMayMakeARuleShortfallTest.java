package souther.architecture;

import org.junit.jupiter.api.Test;

import java.lang.classfile.ClassModel;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who may say that a rule is answerable for a position, and where the place an author wrote is
 * settled.
 *
 * <p>What a rule was short of is made where the asking is: the reading that could not take a form
 * in knows the position, the reason and the clause it was reading, and everything downstream is
 * handed the answer. A pass reaching for the constructor makes the fact out of whatever it has —
 * a place's reasons, most easily, which name every rule that reached the place and not the one
 * that asked.
 *
 * <p>The site is held apart from that. A conjunction distributing over a choice copies a shortfall
 * into both branches, and what says the two copies are one fact is that they carry one site. Minted
 * again on the way, a copy would be a second fact of the same shape — and a choice asking whether
 * the branch that went unread accounts for a position would read a shortfall standing in both
 * branches as that branch's own.
 *
 * <p>Read off the compiled classes, so a maker reached through a method reference is one of these:
 * a constructor handed to something that will call it makes a fact as surely as calling it here.
 */
class WhoMayMakeARuleShortfallTest {

    private static final String OWNER = "souther/compiler/check/RuleShortfall";

    // What a reading decides while it is still running, which is what crosses out of it as the
    // fact above. Told apart by where in this reading's own tree it was met, so a second maker is
    // a second answer to which decision this is.
    private static final String AS_MET = "souther/compiler/check/ReadingShortfall";

    private static final CompiledOutputs COMPILED = CompiledOutputs.ofWhatThisRepositoryPublishes();

    /**
     * Every class that makes one, and why it is entitled to.
     *
     * <p>One, because there is one crossing. What a reading decided is met while the reading runs
     * and is told apart by where in its own tree it stands; what crosses out of it is about what an
     * author wrote, and the one place that turns the first into the second is where the reading is
     * filed. A second maker is a pass answering what a rule is answerable for out of what it has in
     * hand, which is a place's reasons wherever the asking is not.
     */
    private static final List<String> MAKING_ONE = List.of(
            AS_MET + " -> " + OWNER + "#<init>");

    /**
     * And every class that decides one while a reading is running, which is more.
     *
     * <p>{@code AdmissibleReading} is where a form nothing reads is met, and it holds what it was
     * short of at the node it was reading. {@code StatedByClauses} holds the two facts a reading
     * cannot make at a leaf: what a refused machine was asked for, which is known once the plan the
     * leaf asked with is matched to the refusal, and what a choice left open, which is a fact about
     * the choice and about no clause under it.
     */
    private static final List<String> DECIDING_ONE = List.of(
            "souther/compiler/check/AdmissibleReading -> " + AS_MET + "#<init>",
            "souther/compiler/check/StatedByClauses -> " + AS_MET + "#<init>",
            "souther/compiler/check/StatedByClauses$Part -> " + AS_MET + "#<init>");

    @Test
    void everyClassThatSaysARuleIsAnswerableForAPositionIsWrittenDown() {
        assertEquals(MAKING_ONE, new ArrayList<>(naming(Set.of(OWNER))),
                "a row added here is a pass answering what a rule is answerable for out of what it"
                        + " has in hand, which is a place's reasons wherever the asking is not");
    }

    @Test
    void andEveryClassThatDecidesOneWhileAReadingRunsIsWrittenDown() {
        assertEquals(DECIDING_ONE, new ArrayList<>(naming(Set.of(AS_MET))),
                "a decision minted where a fact is carried rather than where it is made turns one"
                        + " copy of a fact into a second fact of the same shape");
    }

    /**
     * The walk reads every module's classes.
     *
     * <p>Asked of the modules the repository has and not of what a build happened to leave: a module
     * whose classes are missing is one whose calls this cannot see, and the rows from the rest would
     * match and this would pass while answering about fewer modules than it names.
     */
    @Test
    void andEveryModuleTheRepositoryHoldsWasRead() {
        assertTrue(modulesRead() > 1,
                "the classes this reads are in more than the one module that declares the fact");
    }

    /** Every class naming a constructor of one of {@code these}, as the class and what it named. */
    private static Set<String> naming(Set<String> these) {
        Set<String> found = new TreeSet<>();
        for (ClassModel model : COMPILED.all()) {
            for (PoolEntry entry : model.constantPool()) {
                if (entry instanceof MemberRefEntry member
                        && these.contains(member.owner().name().stringValue())
                        && "<init>".equals(member.name().stringValue())) {
                    found.add(model.thisClass().asInternalName() + " -> "
                            + member.owner().name().stringValue() + "#<init>");
                }
            }
        }
        return found;
    }

    private static int modulesRead() {
        int read = 0;
        for (Path module : COMPILED.modules()) {
            if (COMPILED.mainOutputOf(module).isPresent()) {
                read++;
            }
        }
        return read;
    }

}
