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
 * Where a reading of a module's rules reaches for the tree a declaration's module expanded its
 * clauses into.
 *
 * <p>That tree is the declaring module's own reading of what it wrote, and it holds where every
 * token of it stands. Read by somebody else, an answer built on it differs whenever anything above
 * the declaration moves — and the answers travel to every module that imports the declaration. So
 * what governs a value and what each rule of it states are read from what the declaration publishes
 * instead, and the tree is reached in the places written down here.
 *
 * <p><b>What this holds, said exactly.</b> It holds which classes reach the tree through a source of
 * a module's rules, and no more. It does not hold that no tree of a declaration is reached anywhere:
 * a reading still asks the world which fields a declaration has, and the world answers out of the
 * tree; a module's own declarations are expanded, emitted from and reported on by the questions that
 * own those answers, none of which takes a source of this kind.
 *
 * <p>Being a record accessor is not what holds even that much. The source is a public record, so
 * anything holding one may ask it; the rows below are what say which of them does.
 *
 * <p>Read off the compiled classes, so a reader that reaches it through a method reference is one of
 * these: a method handed to something that will call it reads the tree as surely as calling it.
 */
class WhoMayReadTheTreeAModuleExpandedItsClausesIntoTest {

    private static final String OWNER = "souther/compiler/check/RuleReadingSource";

    private static final String THE_TREE = "invariants";

    private static final CompiledOutputs COMPILED = CompiledOutputs.ofWhatThisRepositoryPublishes();

    /**
     * The classes that reach it, and why each is entitled to.
     *
     * <p>{@code Clauses} is the reading of a declaration's clauses, and reaches the tree on the one
     * side that is a declaration answering for itself: what a module publishes about its own
     * declaration is worked out from the clauses that module expanded, which is where the tree comes
     * from and where it stops. What it answers for a declaration written elsewhere is read from what
     * that declaration publishes.
     *
     * <p>{@code DeclarationMeaning} hands the tree on to make exactly that answer, and takes the
     * lookup rather than assembling one so that what a declaration says is worked out over the
     * source its module is read under.
     *
     * <p>{@code DeclaredClauses} is the odd one and is not a reading of the model. What it answers
     * is the text of each rule, for a caller proposing a value a rule would admit, and the text is
     * what an author wrote: a clause the declaring module could not type has no term and still has a
     * rule somebody can read a string out of. Moved onto what the declaration publishes, a model
     * would propose fewer values than its author wrote rules for.
     */
    private static final List<String> READING_IT = List.of(
            "souther/compiler/check/Clauses -> " + OWNER + "#" + THE_TREE,
            "souther/compiler/check/DeclarationMeaning -> " + OWNER + "#" + THE_TREE,
            "souther/compiler/check/DeclaredClauses -> " + OWNER + "#" + THE_TREE);

    @Test
    void everyClassThatReachesTheTreeThroughASourceIsWrittenDown() {
        assertEquals(READING_IT, new ArrayList<>(reaching()),
                "a row added here is a reader of a declaration's authored tree, which is a reader"
                        + " whose answers move when the declaration does");
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
                "the classes this reads are in more than the one module that declares the source");
    }

    /**
     * Every class naming the accessor, as the class and what it named.
     *
     * <p>The record that declares it is not one of them. What a record says about its own component
     * is what a record is — the name is in its equality, its hash and how it prints itself — and a
     * row for it would be this rule reporting the accessor for existing.
     */
    private static Set<String> reaching() {
        Set<String> found = new TreeSet<>();
        for (ClassModel model : COMPILED.all()) {
            if (OWNER.equals(model.thisClass().asInternalName())) {
                continue;
            }
            for (PoolEntry entry : model.constantPool()) {
                if (entry instanceof MemberRefEntry member
                        && OWNER.equals(member.owner().name().stringValue())
                        && THE_TREE.equals(member.name().stringValue())) {
                    found.add(model.thisClass().asInternalName() + " -> "
                            + OWNER + "#" + THE_TREE);
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
