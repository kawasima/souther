package souther.architecture;

import org.junit.jupiter.api.Test;
import souther.test.RepositoryLayout;

import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.reflect.AccessFlag;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A check names what it licenses by something the source states.
 *
 * <p>A structural rule here holds a list of who may do something, each entry addressing one reader
 * by name. A lambda is compiled to a method of its own, named after the method it was written in and
 * numbered by where it fell among that class's lambdas — and that number is javac's answer rather
 * than anything a line of the code says.
 *
 * <p>Addressed by it, a licence fails both ways. A lambda written earlier in the same class moves
 * the number, and the entry goes red for an edit that has nothing to do with who is licensed, which
 * is the licence being edited to match whatever the compiler did. The other way round is quieter: a
 * different lambda that comes to hold that number is licensed by an entry nobody wrote for it, and
 * the check cannot tell the two apart.
 *
 * <p><b>What is forbidden is the identity javac made, and it is taken from javac.</b> Not a
 * spelling: a rule that recognised such a name by its shape would be a second account of how javac
 * names a lambda, which is the same defect this is about one level up — and one that goes wrong at
 * exactly the places a naming scheme is allowed to surprise its readers. So the population is every
 * lambda this repository compiled, under the address a licence writes: the class it was written in,
 * and the name javac gave it.
 *
 * <p><b>What is refused is the identity reaching the licence, and not the lambda.</b> A rule that
 * reads compiled classes meets lambdas wherever it walks, and answering for one by the method it was
 * written in is a reading several rules here already have. What that reading hands back is a name
 * the source states; what is refused here is writing javac's name down as the address instead.
 *
 * <p><b>Asked of every check this repository has.</b> A population taken from the checks that hold a
 * licence would be taken from the property being checked — a list written tomorrow would not be in
 * it — so it is every class compiled beside a test, and the rule is the narrow one.
 *
 * <p>What it does not see, said rather than left to be found. A name put together at run time out of
 * pieces is not something the class file holds as text, so a licence assembled that way would pass;
 * {@link WhatACheckSays} is what a check has written down. And an address of a lambda that no longer
 * exists is not here either, because no such lambda was compiled — an entry like that matches
 * nothing the licence it sits in walks over, and goes red there.
 */
class NoCheckAddressesAReaderByANameJavacMadeUpTest {

    private static final CompiledOutputs EVERYTHING = CompiledOutputs.ofEverythingCompiledHere();

    private static final RepositoryLayout REPOSITORY = RepositoryLayout.ofWorkingDirectory();

    /**
     * The prefix javac writes a lambda's method under.
     *
     * <p>The whole of what is spelled here, and what the readings elsewhere spell too. It is not how
     * the name is recognised — the names come from the classes — but what tells the texts that could
     * name one from the texts that cannot: an address in the population has it, so a text carrying
     * an address has it as well, and one that does not carries none.
     */
    private static final String A_LAMBDA = "lambda$";

    /** The method the lambdas here are written in, whose own name carries a {@code $}. */
    private static final String THE_HOLDER = "named$inOneText";

    /** The method here that is named the way javac names a lambda and is not one. */
    private static final String THE_DECOY = "lambda$thisClass$0";

    @Test
    void nothingAddressesAReaderByAnIdentityJavacMadeUp() {
        Set<String> forbidden = whatJavacNamedTheLambdasHere();
        assertFalse(forbidden.isEmpty(), "this repository compiled no lambda at all, so the rule is"
                + " asked of nothing and passes by having nothing to forbid");

        List<ClassModel> checks = checks();
        assertFalse(checks.isEmpty(), "no check of this repository was read at all, so this rule is"
                + " asked of nothing and passes by having nobody to ask");

        Map<String, List<String>> addressed = new LinkedHashMap<>();
        for (ClassModel each : checks) {
            for (Map.Entry<String, List<String>> said : WhatACheckSays.of(each).entrySet()) {
                List<String> named = new ArrayList<>();
                for (String one : said.getValue()) {
                    named.addAll(named$inOneText(one, forbidden));
                }
                if (!named.isEmpty()) {
                    addressed.put(said.getKey(), named);
                }
            }
        }

        assertEquals(Map.of(), addressed,
                "a check addresses a reader by an identity javac made up, so the entry moves when a"
                        + " lambda is added above it and licenses whichever lambda comes to hold"
                        + " that number. Give the reader a name the source states, or answer for it"
                        + " by the method it was written in");
    }

    /**
     * And that a lambda written where the name around it already has a {@code $} arrives whole.
     *
     * <p>The rule above passes when nothing is found, which is also what it would do if the
     * population were built out of names javac does not write. What holds it to what javac writes is
     * this class: every lambda here is written inside {@link #named$inOneText}, whose name carries a
     * {@code $} of its own, so what javac made of it is a name with the separator in the middle of
     * the part that is not the separator. A reading that took such a name apart would lose it, and
     * an expected spelling written out here would be that reading again — so what is asked is that
     * the lambdas this class compiled to are in the population, whatever they are called.
     */
    @Test
    void andALambdaOfAHolderSpelledWithADollarIsInThatPopulation() {
        ClassModel itself = EVERYTHING.read(lambda$thisClass$0());
        assertTrue(declares(itself, THE_HOLDER),
                "the method the lambdas here are written in is gone or is called something else, so"
                        + " what this reads is an ordinary name and the case it exists for is"
                        + " unasked");

        Set<String> mine = new TreeSet<>();
        lambdasOf(itself, mine);
        assertFalse(mine.isEmpty(), "this class compiled to no lambda of its own, so there is"
                + " nothing here to have been carried into the population");

        Set<String> missing = new TreeSet<>(mine);
        missing.removeAll(whatJavacNamedTheLambdasHere());
        assertEquals(Set.of(), missing,
                "a lambda this class compiled to is not in the population the rule beside this one"
                        + " is held to, so what javac named it was dropped on the way in");
    }

    /**
     * And that a method the source named that way is not in it.
     *
     * <p>The other side of the same question. {@code $} is a letter, so a name javac gives a lambda
     * is a name anybody may write, and a method somebody declared is a name the source states —
     * exactly what a licence is entitled to address a reader by. The one below is declared here for
     * this to ask about, and what tells it from a lambda's is the flag javac sets and not what
     * either of them is called.
     */
    @Test
    void andAMethodTheSourceDeclaredThatWayIsNotInThatPopulation() {
        ClassModel itself = EVERYTHING.read(lambda$thisClass$0());
        assertTrue(declares(itself, THE_DECOY),
                "the method this reads is not declared here any more, so what it asks about is"
                        + " nothing and the answer means nothing");

        assertFalse(whatJavacNamedTheLambdasHere().contains(
                        NoCheckAddressesAReaderByANameJavacMadeUpTest.class.getName()
                                + "." + THE_DECOY),
                "a method this source declares is in the population of what javac made up, so the"
                        + " rule forbids an address the source states — which is the whole of what a"
                        + " licence is asked to use");
    }

    /**
     * This class, as the outputs name it.
     *
     * <p>Declared under a name javac would give a lambda, on purpose and as the subject of the case
     * above: a name is something anybody may write, and what says who wrote a method is the flag
     * beside it. It does a real piece of the work here so that it is a method this class has rather
     * than one kept for a test to look at.
     */
    private static String lambda$thisClass$0() {
        return NoCheckAddressesAReaderByANameJavacMadeUpTest.class.getName().replace('.', '/');
    }

    /**
     * Which of {@code identities} {@code said} names.
     *
     * <p>Spelled with a {@code $} in its own name on purpose, and the only method here a lambda is
     * written in. What javac makes of a lambda written here is named after this method, separator
     * and all, which is the name the case above reads off the artifact.
     */
    private static List<String> named$inOneText(String said, Set<String> identities) {
        if (!said.contains(A_LAMBDA)) {
            return List.of();
        }
        return identities.stream().filter(each -> said.contains(each)).toList();
    }

    /**
     * Every lambda this repository compiled, under the address a licence writes one down by.
     *
     * <p>Built where it is asked for. The classes behind it are read once for the fork
     * ({@link CompiledOutputs}), so what a second ask costs is the walk over what that already
     * holds — measured, and under what a run of this varies by.
     */
    private static Set<String> whatJavacNamedTheLambdasHere() {
        Set<String> out = new TreeSet<>();
        for (ClassModel each : EVERYTHING.all()) {
            lambdasOf(each, out);
        }
        return out;
    }

    /**
     * The lambdas of one class, as addresses, into {@code out}.
     *
     * <p>The class it was written in and the name javac gave it, written the way a licence writes a
     * method: the owner with its nesting as the source spells it, and the method after it. A name
     * alone would be an address two classes could share, and the thing forbidden is an identity
     * rather than a spelling that happens to be in use somewhere.
     *
     * <p><b>Written by javac, which the class file says and the name does not.</b> {@code $} is a
     * letter to Java, so a method the source declares may be called anything a lambda's is called;
     * what makes one a lambda's is that the compiler wrote it, and that is what the synthetic flag
     * is. The name is asked as well, because the compiler writes other methods nobody declared — a
     * bridge, an enum's own members — and those are not what a licence would be addressing a reader
     * by.
     *
     * <p>The owner is put together where one is found, because most classes have no lambda at all
     * and spelling a name for them is work this asks of every class of every module.
     */
    private static void lambdasOf(ClassModel model, Set<String> out) {
        String owner = null;
        for (MethodModel method : model.methods()) {
            String name = method.methodName().stringValue();
            if (name.startsWith(A_LAMBDA) && method.flags().has(AccessFlag.SYNTHETIC)) {
                if (owner == null) {
                    owner = model.thisClass().asInternalName()
                            .replace('/', '.').replace('$', '.');
                }
                out.add(owner + "." + name);
            }
        }
    }

    /** Whether {@code model} still declares {@code named} itself, rather than javac having written
     *  a method of that name. */
    private static boolean declares(ClassModel model, String named) {
        for (MethodModel method : model.methods()) {
            if (method.methodName().stringValue().equals(named)
                    && !method.flags().has(AccessFlag.SYNTHETIC)) {
                return true;
            }
        }
        return false;
    }

    private static List<ClassModel> checks() {
        List<ClassModel> out = new ArrayList<>();
        for (Path module : REPOSITORY.modules()) {
            out.addAll(EVERYTHING.testClassesOf(module));
        }
        return out;
    }
}
