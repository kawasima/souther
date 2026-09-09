package souther.architecture;

import souther.compiler.types.SourceConstruct;
import souther.compiler.types.SourceConstructOrigin;
import souther.compiler.types.WrittenOwner;

import org.junit.jupiter.api.Test;

import java.lang.classfile.ClassModel;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who may say which construct a coverage obligation was written as.
 *
 * <p>A non-recursive helper is expanded at each call, so one construct the author wrote becomes
 * several in the tree that runs, and what says those copies are one obligation is that they carry
 * one origin. An origin made after that expansion gives two copies of one construct two, which is
 * the thing the type exists to prevent. Its own account says where one may be made: at the reading
 * of a source construct, and at the derivation of the forks a lowering makes.
 *
 * <p>Which is a narrower question than where the type is answered with. A pass may also say that no
 * source wrote the construct in hand, and that is not asked here: it hands out no construct of the
 * author's, so there is nothing in it for a copy to be given twice. Anywhere a pass composes a form
 * the language has no syntax for, it may say so.
 *
 * <p>Nothing held that. The record's constructor is public and takes every component, as a record's
 * is, so a pass reaching for one makes whatever it likes. So the account is read off the compiled
 * classes here: every class naming a member that makes an origin, with the member it names.
 *
 * <p>What is read is the constant pool and not the instructions. A name reached through a method
 * reference is a constant and no call instruction — {@code SourceConstructOrigin::unwritten} handed to
 * something that will call it makes an origin as surely as calling it here does, and a walk over
 * instructions passes over it.
 *
 * <p>Not the shape its neighbours have. Where a construction came from and which of its fields had
 * to be written are answered by asking the node, so their vocabulary stays inside the package that
 * owns it and a check over callers is enough. What reads a coverage origin reads the module, the
 * construct and the fork, so the vocabulary crosses and who names the makers is what there is to
 * read.
 *
 * <p>A member that hands on an origin it was given is not one of these. {@link
 * souther.compiler.ast.Hir.ListComp#forkOfGuard} derives a fork by asking the origin it holds, and
 * what it can answer with is a fork of its own comprehension and nothing else — which is that
 * method's to refuse and is refused there, rather than a thing this could tell by reading a call.
 */
class WhoMaySettleASourceConstructOriginTest {

    /** Read off the type, so that renaming it stops the build rather than leaving this walk with
     *  nothing to find and nothing to say. */
    private static final String OWNER = internalNameOf(SourceConstructOrigin.class);

    private static final String AN_ORIGIN = "L" + OWNER + ";";

    private static final String A_CONSTRUCT = "L" + internalNameOf(SourceConstruct.class) + ";";

    /** What a construct's number is counted within, which every way of making one takes. Read off
     *  the type for the reason the owner is: a rename stops the build rather than emptying the walk. */
    private static final String AN_OWNER = "L" + internalNameOf(WrittenOwner.class) + ";";

    private static final CompiledOutputs COMPILED = CompiledOutputs.ofWhatThisRepositoryPublishes();

    /**
     * What makes an origin, written down — so that a way of making one that is added is a row here
     * before anyone names it, and not a silence until someone does.
     */
    private static final List<String> MAKERS = List.of(
            OWNER + "#<init>(" + AN_OWNER + "II" + A_CONSTRUCT + ")V",
            OWNER + "#lowered(I)" + AN_ORIGIN,
            OWNER + "#written(" + AN_OWNER + "I" + A_CONSTRUCT + ")" + AN_ORIGIN);

    /**
     * Every class that names one of the makers, with the maker it names.
     *
     * <p>A source construct is read in one place, and the fork of a comprehension's guard is derived
     * where the comprehension is — so that the lowering and the reading that runs before it cannot
     * number the guards differently.
     *
     * <p>Saying that no source wrote one is not here and is nobody's permission. A pass composing a
     * form the language has no syntax for — an application where an author wrote a name, the two
     * lists a comprehension lowers to, a value a fixture composes — says so, and says it wherever
     * such a pass is written. What it gains by saying it is nothing: there is no construct of the
     * author's in the answer for a copy to take twice, which is what the rows below are about.
     *
     * <p>The constructor is named only from inside {@code SourceConstructOrigin}. A row naming it elsewhere
     * is a pass making an origin of its own, which after an expansion is two obligations where the
     * author wrote one.
     *
     * <p>What reads a source is a reading of one owner, not the builder around it. A number means
     * which construct only under what it was counted within, so the two are made together: the
     * builder finds the owner and hands its syntax to a reading, and the reading is what has a
     * number to give. A row here naming the builder would be a count over everything a file wrote.
     */
    private static final List<String> NAMING_A_MAKER = List.of(
            "souther/compiler/ast/Hir$ListComp -> " + OWNER + "#lowered(I)" + AN_ORIGIN,
            "souther/compiler/frontend/AstBuilder$Reading -> " + OWNER
                    + "#written(" + AN_OWNER + "I" + A_CONSTRUCT + ")" + AN_ORIGIN,
            OWNER + " -> " + OWNER + "#<init>(" + AN_OWNER + "II" + A_CONSTRUCT + ")V");

    @Test
    void everyWayOfMakingOneIsWrittenDown() {
        assertEquals(MAKERS, new ArrayList<>(makers()),
                "a row added here is a way to say which construct an obligation was written as:"
                        + " say what reads a source to answer it, and add who names it");
    }

    @Test
    void andEveryClassThatNamesOneIsWrittenDownWithWhatItNames() {
        assertEquals(NAMING_A_MAKER, new ArrayList<>(namingAMaker()),
                "an origin is made where a source is read, and derived where a lowering forks one"
                        + " construct into several: a row that is neither is a copy given an"
                        + " obligation of its own");
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
                "the classes this reads are in more than the one module that declares an origin");
    }

    /** Every member of {@code SourceConstructOrigin} that answers with one, and the constructor they all go
     *  through — read off the type rather than written out, so a member added beside them is one of
     *  these by being one. */
    private static Set<String> makers() {
        Set<String> members = new TreeSet<>();
        for (Method each : SourceConstructOrigin.class.getDeclaredMethods()) {
            if (each.getReturnType() == SourceConstructOrigin.class && !statesAnAbsence(each)) {
                members.add(OWNER + "#" + each.getName()
                        + descriptorOf(each.getParameterTypes(), each.getReturnType()));
            }
        }
        for (Constructor<?> each : SourceConstructOrigin.class.getDeclaredConstructors()) {
            members.add(OWNER + "#<init>" + descriptorOf(each.getParameterTypes(), void.class));
        }
        return members;
    }

    /**
     * Whether {@code member} answers that no source wrote the construct, rather than saying which
     * construct a source wrote.
     *
     * <p>Two operations wear one shape here. Settling an identity says which construct of a source
     * this is, and two copies of one construct carrying two of them is the thing this walk exists to
     * stop. Saying there is none says the opposite — that nothing was written, so there is no
     * identity for a copy to take twice — and a pass that says it gains no construct of the
     * author's. So the second is not a permission, and a pass composing a form the language has no
     * syntax for is not made a source authority by admitting it wrote one.
     *
     * <p>Read by asking the answer rather than by its name. A member named for what it does today
     * is renamed tomorrow, and a walk keyed to the spelling passes over the renamed one and
     * reports nothing.
     */
    private static boolean statesAnAbsence(Method member) {
        if (member.getParameterCount() != 0 || !java.lang.reflect.Modifier.isStatic(
                member.getModifiers())) {
            return false;
        }
        try {
            return !((SourceConstructOrigin) member.invoke(null)).isWritten();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("a way of making an origin could not be asked what it"
                    + " answers: " + member, e);
        }
    }

    /** Every class naming a maker, as the class and the maker — the maker by the whole of what it
     *  is, as the key that found it was. */
    private static Set<String> namingAMaker() {
        Set<String> found = new TreeSet<>();
        Set<String> makers = makers();
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











    /** The descriptor of a member taking these and answering with that. */
    private static String descriptorOf(Class<?>[] takes, Class<?> answers) {
        StringBuilder out = new StringBuilder("(");
        for (Class<?> each : takes) {
            out.append(descriptorOf(each));
        }
        return out.append(')').append(descriptorOf(answers)).toString();
    }

    private static String descriptorOf(Class<?> type) {
        if (type == void.class) {
            return "V";
        }
        if (type == int.class) {
            return "I";
        }
        if (type == boolean.class) {
            return "Z";
        }
        if (type.isArray()) {
            return "[" + descriptorOf(type.getComponentType());
        }
        return "L" + internalNameOf(type) + ";";
    }

    private static String internalNameOf(Class<?> type) {
        return type.getName().replace('.', '/');
    }
}
