package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A lambda bound to a name and then written where a value goes is expanded at that name.
 *
 * <p>Two different things read a binding where a function is wanted. A pass puts one there for a
 * block a call handed to a function parameter, and no source wrote a name for it — the copy is named
 * by which copy took it and which parameter it filled. An author binds a lambda with a {@code let}
 * and writes the name where the value goes, and that name is one the source counted — so the copy is
 * named by the reference, the way every other name written where a value goes is.
 *
 * <p><b>What made this a crash was a sentence rather than a change.</b> The cause said no source
 * wrote either of them, which was never true of the second; the reader that first had to tell them
 * apart believed it, and refused the model rather than the impossible case the sentence described.
 * A {@code let} binding a lambda and passing it by name has always been ordinary.
 */
class ANameAValueWasBoundToIsExpandedWhereItIsWrittenTest {

    @Test
    void aLetBoundLambdaMayBePassedByName() {
        Compilation compilation = Compilation.ofSource("""
                module m

                data Total = Int

                behavior add : (xs: List<Int>) -> Total
                    constructs Total
                let add (xs) = {
                    let step = (a, b) -> a + b
                    Total(List.fold(step, 0, xs))
                }

                example add
                  | "two of them" : ([1, 2]) -> Total(3)
                """, "Main");
        compilation.answerEverything();

        assertEquals(List.of("m"), compilation.modules(),
                "a lambda a `let` bound and a call was handed by name is a model this compiles");
        assertTrue(compilation.db().ask(new Bodies.Checked("m"))
                        .value() != null,
                "and its body is read, which is where the copy the name expands into is named");
    }
}
