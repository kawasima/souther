package souther.architecture;

import souther.compiler.partition.FixtureReferences;
import souther.compiler.types.FixtureReferenceOrigin;

import org.junit.jupiter.api.Test;

import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.instruction.InvokeInstruction;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who may compose a reference for a row the generator offers, and who may number one.
 *
 * <p>A row naming a value the module states holds a name reaching a declaration, and such a name is
 * some reference of it. No source wrote that one, so what says which it is, is the run that composed
 * it — counted within that run and meaning nothing outside it
 * ({@link FixtureReferenceOrigin}).
 *
 * <p>Which is a rule about how many minters there are. One per run and handed to whatever composes:
 * a minter per composer starts each of them at nought and gives one number to several references,
 * and a static counter carries one run's numbering into the next. Both make two occurrences one, and
 * neither shows up as a failure — the rows still come out, naming values that are no longer told
 * apart.
 *
 * <p>Held here because the types cannot hold it. A record's constructor is public, as a record's is,
 * and a class with a counter has to be built by somebody — so what stops a second minter is not the
 * compiler but this walk, the way the makers of a construct's origin are held
 * ({@code WhoMaySettleASourceConstructOriginTest}). Written down as who names the maker, so that a
 * second one is a row here before it is a numbering nobody notices.
 */
class WhoMayComposeAFixtureReferenceTest {

    private static final String MINTER = internalNameOf(FixtureReferences.class);

    private static final String ORIGIN = internalNameOf(FixtureReferenceOrigin.class);

    private static final String GENERATOR = "souther/compiler/partition/Generator";

    private static final CompiledOutputs COMPILED = CompiledOutputs.ofWhatThisRepositoryPublishes();

    /**
     * Every class that makes a minter or numbers a reference, with what it names.
     *
     * <p>The generator makes the one minter its run hands round, and the minter is the only thing
     * that numbers a reference. A row naming the origin's constructor from anywhere else is a
     * second numbering, and one naming the minter's is a second run inside a run.
     */
    private static final List<String> NAMING_A_MAKER = List.of(
            "souther/compiler/partition/FixtureReferences -> " + ORIGIN + "#<init>(I)V",
            "souther/compiler/partition/Generator -> " + MINTER + "#<init>()V");

    /**
     * And the run makes one of them.
     *
     * <p>Which is the fact this is here for, and not one the rows above reach: a class naming the
     * minter's constructor names it once in the constant pool however many times it writes the
     * call, so a second minter beside the first is a second numbering that the rows cannot see. So
     * the calls are counted where they are made.
     *
     * <p>Counted over the class and not over one method, because the run is the class's: a minter
     * made in a second method is handed round a second walk, and the references of the two runs
     * begin again at nought.
     */
    @Test
    void andTheRunMakesOneOfThem() {
        assertEquals(1, timesGeneratorMakesAMinter(),
                "a run hands one minter round: a second is a second numbering, and the references"
                        + " of the two begin again at nought while naming different values");
    }

    @Test
    void everyClassThatComposesOrNumbersOneIsWrittenDown() {
        assertEquals(NAMING_A_MAKER, new ArrayList<>(namingAMaker()),
                "a reference a row names is numbered within one run of the generator: a row here is"
                        + " a second minter or a second numbering, and either tells two references"
                        + " as one");
    }

    /**
     * The walk reads every module's classes.
     *
     * <p>Asked of the modules the repository has and not of what a build happened to leave: a module
     * whose classes are missing is one whose calls this cannot see, and the rows from the rest would
     * match while answering about fewer modules than it names.
     */
    @Test
    void andEveryModuleTheRepositoryHoldsWasRead() {
        assertTrue(modulesRead() > 1,
                "the classes this reads are in more than the one module that declares a minter");
    }

    /**
     * How many times the generator writes the call that makes a minter.
     *
     * <p>Read off the instructions rather than the constant pool, which is where the rows above
     * stop: a pool says the class names a member and never how often it calls it, so counting there
     * would answer one for any number of them.
     */
    private static int timesGeneratorMakesAMinter() {
        int made = 0;
        for (Path module : COMPILED.modules()) {
            for (ClassModel each : COMPILED.classesOf(module)) {
                if (!each.thisClass().asInternalName().equals(GENERATOR)) {
                    continue;
                }
                for (MethodModel method : each.methods()) {
                    made += method.code().map(WhoMayComposeAFixtureReferenceTest::minters).orElse(0);
                }
            }
        }
        return made;
    }

    /** The calls in one method's code that make a minter. */
    private static int minters(CodeModel code) {
        int made = 0;
        for (CodeElement element : code) {
            if (element instanceof InvokeInstruction call
                    && call.opcode() == Opcode.INVOKESPECIAL
                    && call.owner().name().stringValue().equals(MINTER)
                    && call.name().stringValue().equals("<init>")) {
                made++;
            }
        }
        return made;
    }

    /** Every class naming one of the two makers, as the class and the maker it names. */
    private static Set<String> namingAMaker() {
        Set<String> makers = Set.of(ORIGIN + "#<init>(I)V", MINTER + "#<init>()V");
        Set<String> found = new TreeSet<>();
        for (Path module : COMPILED.modules()) {
            for (ClassModel each : COMPILED.classesOf(module)) {
                for (PoolEntry entry : each.constantPool()) {
                    if (entry instanceof MemberRefEntry member) {
                        String named = member.owner().name().stringValue() + "#"
                                + member.name().stringValue() + member.type().stringValue();
                        if (makers.contains(named)) {
                            found.add(each.thisClass().asInternalName() + " -> " + named);
                        }
                    }
                }
            }
        }
        return found;
    }

    private static int modulesRead() {
        int read = 0;
        for (Path module : COMPILED.modules()) {
            if (!COMPILED.classesOf(module).isEmpty()) {
                read++;
            }
        }
        return read;
    }













    private static String internalNameOf(Class<?> type) {
        return type.getName().replace('.', '/');
    }
}
