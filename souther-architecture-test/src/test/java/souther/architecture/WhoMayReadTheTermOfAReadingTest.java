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
 * Who may take the term out of a reading of one.
 *
 * <p>{@code TermMeaning} is what a reader in another module depends on, and two of them are equal
 * where the terms they hold say the same thing whatever the places on those terms are. So two
 * readings a store has called one answer hold terms that are not the same object and were written
 * at different places, and anything read off the term and published would be a fact about which of
 * two equal answers the store happened to keep.
 *
 * <p>One reader takes the term out all the same, and what makes that sound is where it goes rather
 * than what it is: the reading of a declaration's clauses builds its own tree out of it, publishes
 * what the clauses state and never a tree, and asks where a clause is written of the thing that
 * says where a clause is written. That is a fact about the caller, so the caller is what is written
 * down here.
 *
 * <p>Being package-private is not what holds it. Every class beside it in the package may call it,
 * and one written tomorrow would compile; the row below is what says which of them does.
 *
 * <p>Read off the compiled classes, so a reader that reaches it through a method reference is one
 * of these: a method handed to something that will call it reads the term as surely as calling it.
 */
class WhoMayReadTheTermOfAReadingTest {

    private static final String OWNER = "souther/compiler/check/TermMeaning";

    private static final String READING_THE_TERM = "termForClauseReading";

    private static final CompiledOutputs COMPILED = CompiledOutputs.ofWhatThisRepositoryPublishes();

    /**
     * The one class that reads it, and why it is entitled to.
     *
     * <p>{@code Clauses} is the reading of a declaration's clauses. It puts what a construction
     * gives each field where that field is read and hands back a tree of its own, which is this
     * check's from there on; nothing it answers with holds the term it was given, and nothing it
     * answers with says where anything was written.
     */
    private static final List<String> READING_IT =
            List.of("souther/compiler/check/Clauses -> " + OWNER + "#" + READING_THE_TERM);

    @Test
    void everyClassThatTakesATermOutOfAReadingIsWrittenDown() {
        assertEquals(READING_IT, new ArrayList<>(reading()),
                "a row added here is a reader that can tell two equal answers apart, so it is a"
                        + " reader that has to be able to say why what it does with the term never"
                        + " reaches an answer");
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
                "the classes this reads are in more than the one module that declares the reading");
    }

    /** Every class naming the accessor, as the class and what it named. */
    private static Set<String> reading() {
        Set<String> found = new TreeSet<>();
        for (ClassModel model : COMPILED.all()) {
            for (PoolEntry entry : model.constantPool()) {
                if (entry instanceof MemberRefEntry member
                        && OWNER.equals(member.owner().name().stringValue())
                        && READING_THE_TERM.equals(member.name().stringValue())) {
                    found.add(model.thisClass().asInternalName() + " -> "
                            + OWNER + "#" + READING_THE_TERM);
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
