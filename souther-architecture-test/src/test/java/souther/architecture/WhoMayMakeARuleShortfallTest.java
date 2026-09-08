package souther.architecture;

import souther.test.RepositoryLayout;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

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

    private static final String A_LEAF = OWNER + "$Site$AtALeaf";

    // A place of its own and not one of the shortfall's, because both readings name it: a choice
    // offering an alternative nothing reads leaves the values open and the ends open, and an author
    // lifting it lifts both. Held under the shortfall, the reading that never files one would have
    // had to name a shortfall to say which choice it means.
    private static final String A_CHOICE = "souther/compiler/check/ChoiceSite";

    private static final RepositoryLayout REPOSITORY = RepositoryLayout.ofWorkingDirectory();

    /**
     * Every class that makes one, and why it is entitled to.
     *
     * <p>{@code AdmissibleReading} is where a form nothing reads is met, and it holds what it was
     * short of at the node it was reading. {@code StatedByClauses} holds the two facts a reading
     * cannot make at a leaf: what a refused machine was asked for, which is known once the plan the
     * leaf asked with is matched to the refusal, and what a choice left open, which is a fact about
     * the choice and about no clause under it.
     */
    private static final List<String> MAKING_ONE = List.of(
            "souther/compiler/check/AdmissibleReading -> " + OWNER + "#<init>",
            "souther/compiler/check/StatedByClauses -> " + OWNER + "#<init>",
            "souther/compiler/check/StatedByClauses$Part -> " + OWNER + "#<init>");

    /**
     * And every class that settles the place one is filed at, which is fewer.
     *
     * <p>A leaf is settled where the node is being read, and nowhere after: a reading that met a
     * clause holds the place it met, and what is decided later about that clause is filed at the
     * place it was handed rather than at one made again from a node in hand. A choice is settled
     * where the branches are joined, which is the one place that holds both what tells this choice
     * from every other and the operator an author wrote it with.
     */
    private static final List<String> MAKING_A_SITE = List.of(
            "souther/compiler/check/AdmissibleReading -> " + A_LEAF + "#<init>",
            "souther/compiler/check/StatedByClauses$Reading -> " + A_CHOICE + "#<init>");

    @Test
    void everyClassThatSaysARuleIsAnswerableForAPositionIsWrittenDown() {
        assertEquals(MAKING_ONE, new ArrayList<>(naming(Set.of(OWNER))),
                "a row added here is a pass answering what a rule is answerable for out of what it"
                        + " has in hand, which is a place's reasons wherever the asking is not");
    }

    @Test
    void andEveryClassThatSettlesWhereOneIsFiledIsWrittenDown() {
        assertEquals(MAKING_A_SITE, new ArrayList<>(naming(Set.of(A_LEAF, A_CHOICE))),
                "a site minted where a fact is carried rather than where it is made turns one copy"
                        + " of a fact into a second fact of the same shape");
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
        assertTrue(modulesRead() > 1,
                "the classes this reads are in more than the one module that declares the fact");
    }

    /** Every class naming a constructor of one of {@code these}, as the class and what it named. */
    private static Set<String> naming(Set<String> these) {
        Set<String> found = new TreeSet<>();
        for (Path module : REPOSITORY.modules()) {
            for (Path each : classesUnder(module)) {
                for (PoolEntry entry : constantPoolOf(each)) {
                    if (entry instanceof MemberRefEntry member
                            && these.contains(member.owner().name().stringValue())
                            && "<init>".equals(member.name().stringValue())) {
                        found.add(internalName(module, each) + " -> "
                                + member.owner().name().stringValue() + "#<init>");
                    }
                }
            }
        }
        return found;
    }

    private static int modulesRead() {
        int read = 0;
        for (Path module : REPOSITORY.modules()) {
            if (!classesUnder(module).isEmpty()) {
                read++;
            }
        }
        return read;
    }

    private static Iterable<PoolEntry> constantPoolOf(Path compiled) {
        try {
            return ClassFile.of().parse(Files.readAllBytes(compiled)).constantPool();
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

    /** The compiled classes of one module that the compiler is made of. Its own tests are not among
     *  them: a test makes one to look at it and ships nothing. */
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
