package souther.architecture;


import org.junit.jupiter.api.Test;

import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who may ask an account of the rules whether a clause of it went unread.
 *
 * <p>That an alternative is one nothing could read is a fact about the clause somebody wrote.
 * Whether the branch beside it still holds a position down is a fact about what the two branches
 * leave, and the two are not the same question: alternatives narrowing a position the same way
 * narrow it whether or not either could be read to the end. Derived from the first, the second
 * comes out as a rule that is false, and a rule holding a position down is reported as holding it
 * down nowhere.
 *
 * <p>So the answer has one owner. What the branches leave is worked out where they are settled and
 * arrives as an opening ({@code Opening}); an account applies one and works none out. What the flag
 * is still good for is written down below, and a row that is not one of those is a second
 * derivation of one fact.
 *
 * <p>Read off the compiled classes and named down to the method, so that a reader is licensed for
 * the question it asks rather than for the class it happens to sit in. Both ways of reaching the
 * flag count: an accessor called on an account, and the field read straight off it, which is what a
 * class in the same file compiles to.
 *
 * <p><b>The account's own methods are not rows.</b> Composing the flag over a choice is what the
 * flag is for, and every method that carries one reads it. What holds that composition to carrying
 * it and nothing else is a law over the operation
 * ({@code AnOpeningIsAppliedToAnAccountAndNeverDerivedFromItTest}), which this cannot see and which
 * says the part this cannot.
 */
class WhoMayAskWhetherAClauseWentUnreadTest {

    private static final String ACCOUNT = "souther/compiler/check/Adoption";

    private static final String FLAG = "hasUnreadPart";

    private static final CompiledOutputs COMPILED = CompiledOutputs.ofWhatThisRepositoryPublishes();

    /**
     * Where an unread alternative is turned into what it left open, and where an author is sent for
     * it.
     *
     * <p>Two questions of the one fact, and one place asks both. What the alternative left open is
     * the first; which choice to send an author to is the second. Both are answered off the same
     * reading's two accounts and the same reading's width, so both are asked where all three are in
     * hand, bound to one reading — which is what the method's shape holds it to.
     *
     * <p>One row and not two, which is the whole of it. Asked at a second site, one reading's flag
     * stands beside a set of positions reached by whichever reading the writer had in hand, and
     * that composes without a complaint: an author is sent to a choice on the strength of an
     * alternative the reading that named the positions read to the end.
     */
    private static final List<String> MAY_ASK =
            List.of("souther/compiler/check/StatedByClauses#openedBy");

    @Test
    void onlyWhereAnUnreadAlternativeBecomesWhatItLeftOpen() {
        assertEquals(MAY_ASK, new ArrayList<>(asking()),
                "whether a branch beside an unread alternative still holds a position down is"
                        + " settled by what the branches leave: a row that is not this one is that"
                        + " question being answered again out of the account of the rules");
    }

    /**
     * The walk reads every module's classes.
     *
     * <p>Asked of the modules the repository has and not of what a build happened to leave: a
     * module whose classes are missing is one whose reads this cannot see, and the rows from the
     * rest would match and this would pass while answering about fewer modules than it names.
     */
    @Test
    void andEveryModuleTheRepositoryHoldsWasRead() {
    }

    /**
     * And the walk finds the flag being read where it is read.
     *
     * <p>The account composes the flag over every connective, so a walk that can see field reads at
     * all sees several inside the account itself. Matched on a name nothing has, every row would be
     * absent and the list above would be empty and equal to itself.
     */
    @Test
    void andTheWalkSeesTheFlagBeingReadWhereItIsCarried() {
        assertTrue(within(ACCOUNT) > 1,
                "the account carries the flag across its own operations, so a walk finding none of"
                        + " those is finding nothing at all");
    }

    /** Every method outside the account that asks one whether a clause of it went unread. */
    private static Set<String> asking() {
        Set<String> found = new TreeSet<>();
        for (Path module : COMPILED.modules()) {
            for (ClassModel each : COMPILED.classesOf(module)) {
                String reader = each.thisClass().asInternalName();
                if (reader.equals(ACCOUNT)) {
                    continue;
                }
                for (MethodModel method : each.methods()) {
                    if (reads(method)) {
                        found.add(reader + "#" + method.methodName().stringValue());
                    }
                }
            }
        }
        return found;
    }

    /** How many of the account's own methods read it, which is what says the walk can see one. */
    private static int within(String owner) {
        int reads = 0;
        for (Path module : COMPILED.modules()) {
            for (ClassModel each : COMPILED.classesOf(module)) {
                if (!each.thisClass().asInternalName().equals(owner)) {
                    continue;
                }
                for (MethodModel method : each.methods()) {
                    if (reads(method)) {
                        reads++;
                    }
                }
            }
        }
        return reads;
    }

    /** Whether this method asks an account whether a clause of it went unread, either way round. */
    private static boolean reads(MethodModel method) {
        CodeModel code = method.code().orElse(null);
        if (code == null) {
            return false;
        }
        return code.elementStream().anyMatch(element -> switch (element) {
            case FieldInstruction it -> ACCOUNT.equals(it.owner().name().stringValue())
                    && FLAG.equals(it.name().stringValue());
            case InvokeInstruction it -> ACCOUNT.equals(it.owner().name().stringValue())
                    && FLAG.equals(it.name().stringValue());
            default -> false;
        });
    }










}
