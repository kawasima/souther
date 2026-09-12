package souther.architecture;

import org.junit.jupiter.api.Test;
import souther.test.RepositoryLayout;

import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
 * <p><b>What is refused is the number reaching the licence, and not the lambda.</b> A rule that
 * reads compiled classes meets lambdas wherever it walks, and answering for one by the method it was
 * written in is a reading several rules here already have. What that reading hands back is a name
 * the source states; what is refused here is writing javac's name down as the address instead.
 *
 * <p><b>Asked of every check this repository has.</b> A population taken from the checks that hold a
 * licence would be taken from the property being checked — a list written tomorrow would not be in
 * it — so it is every class compiled beside a test, and the rule is the narrow one.
 *
 * <p>What it does not see, said rather than left to be found: a name put together at run time out of
 * pieces is not something the class file holds as text, so a licence assembled that way would pass.
 * {@link WhatACheckSays} is what a check has written down.
 */
class NoCheckAddressesAReaderByANameJavacMadeUpTest {

    private static final CompiledOutputs EVERYTHING = CompiledOutputs.ofEverythingCompiledHere();

    private static final RepositoryLayout REPOSITORY = RepositoryLayout.ofWorkingDirectory();

    /**
     * How javac spells a lambda: the method it was written in, and its place among the class's
     * lambdas.
     *
     * <p>The number is what this is about, so it is what the pattern requires. A rule that carries
     * the prefix alone to answer for a lambda by the method around it says {@code lambda$} and stops
     * there, which is the reading this leaves alone.
     */
    private static final Pattern MADE_UP = Pattern.compile("lambda\\$[^$]*\\$\\d");

    @Test
    void nothingAddressesAReaderByTheNumberJavacGaveItsLambda() {
        List<ClassModel> checks = new ArrayList<>();
        for (Path module : REPOSITORY.modules()) {
            checks.addAll(EVERYTHING.testClassesOf(module));
        }
        assertFalse(checks.isEmpty(), "no check of this repository was read at all, so this rule is"
                + " asked of nothing and passes by having nobody to ask");

        Map<String, List<String>> addressed = new LinkedHashMap<>();
        for (ClassModel each : checks) {
            WhatACheckSays.of(each).forEach((where, said) -> {
                List<String> madeUp = said.stream().filter(one -> MADE_UP.matcher(one).find())
                        .toList();
                if (!madeUp.isEmpty()) {
                    addressed.put(where, madeUp);
                }
            });
        }

        assertEquals(Map.of(), addressed,
                "a check addresses a reader by the number javac gave a lambda, so the entry moves"
                        + " when a lambda is added above it and licenses whichever lambda comes to"
                        + " hold that number. Give the reader a name the source states, or answer"
                        + " for it by the method it was written in");
    }

    /**
     * And that the pattern is what javac writes, asked of javac.
     *
     * <p>The rule above passes when nothing matches, which is also what it does when the spelling it
     * looks for is not the spelling in use. So what it is held to is a name this very class made:
     * the check above is written with a lambda, and the method that lambda became is in this class
     * beside it.
     */
    @Test
    void andThatIsHowALambdaIsNamedHere() {
        ClassModel itself = EVERYTHING.read(
                NoCheckAddressesAReaderByANameJavacMadeUpTest.class.getName().replace('.', '/'));
        List<String> lambdas = itself.methods().stream()
                .map(MethodModel::methodName)
                .map(each -> each.stringValue())
                .filter(each -> MADE_UP.matcher(each).find())
                .toList();
        assertFalse(lambdas.isEmpty(), "no method of this class is named the way javac names a"
                + " lambda, so the rule beside this one is looking for a spelling nothing has and"
                + " would pass over a licence addressed by one");
    }
}
