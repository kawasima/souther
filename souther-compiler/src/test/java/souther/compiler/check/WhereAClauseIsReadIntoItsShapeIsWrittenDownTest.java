package souther.compiler.check;

import org.junit.jupiter.api.Test;
import souther.compiler.WhatWasCompiled;

import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Who reads a clause's tree into its shape, and where such a reading may happen.
 *
 * <p>What a clause is written out of — a conjunction, a choice, a denial — is read out of the tree
 * once ({@link ClauseExpr}), and everything downstream is an evaluator over what that left. A reader
 * that starts a second one is deciding for itself what the author wrote, and it decides it under a
 * polarity of its own: read afresh from a node, a clause the walk was holding as denied comes back
 * stated, and the occurrences under it are numbered from wherever the node begins rather than from
 * the clause.
 *
 * <p>Which is what a reading of ends did. It held the shape, went back to the node it was spelled
 * as, and asked for the shape again with the polarity written in as {@code true} — so a conjunction
 * an author wrote as a denied choice was a choice to that second reading, and the answer it filed
 * was about a clause nobody wrote.
 *
 * <p>Read off the compiled classes, and by the type the call is made with rather than by the name
 * alone: the reading that walks a clause into its shape calls itself, and a licence keyed by the
 * name would licence the entry point and the recursion together, so a caller reaching for the entry
 * point could take the recursion's place in the table and pass.
 */
class WhereAClauseIsReadIntoItsShapeIsWrittenDownTest {

    private static final String SHAPE = "souther/compiler/check/ClauseExpr";

    /** The way in, which takes a tree and how it stands and nothing else. What the walk calls
     *  itself with carries where it has got to, and is a different descriptor. */
    private static final String THE_WAY_IN =
            "(Lsouther/compiler/core/Core;Z)Lsouther/compiler/check/ClauseExpr;";

    /** A method that may read a clause into its shape, how many times it does, and why. */
    private record Licence(String who, int calls, String why) { }

    private static final List<Licence> MAY_READ = List.of(
            new Licence("souther.compiler.check.ClauseReading.read", 1,
                    "the fold every reading of a clause is written as, which reads the shape and"
                            + " hands each reading its parts"),
            new Licence("souther.compiler.check.Clauses.statedAt", 1,
                    "the shape a world of rules is described against, made where the parts a"
                            + " caller reads are chosen"),
            new Licence("souther.compiler.check.InvariantChecker.seedFieldsFresh", 1,
                    "the same, for the reading a declaration's rules are gathered by"),
            new Licence("souther.compiler.check.StatedByClauses.mirrors", 1,
                    "the shape of a clause a caller holds only as a tree, compared against one"
                            + " already read"));

    @Test
    void onlyTheReadingsThatMakeAShapeReadOne() {
        assertEquals(declared(), callsTo(SHAPE, "of", THE_WAY_IN),
                "a clause read into its shape anywhere else is a second answer to what its author"
                        + " wrote, arrived at under whatever polarity that reader wrote down."
                        + " What may read one, and why: " + why());
    }

    private static Map<String, Integer> declared() {
        Map<String, Integer> out = new TreeMap<>();
        MAY_READ.forEach(each -> out.put(each.who(), each.calls()));
        return out;
    }

    private static Map<String, String> why() {
        Map<String, String> out = new TreeMap<>();
        MAY_READ.forEach(each -> out.put(each.who(), each.why()));
        return out;
    }

    /** How many times each method of the compiler calls {@code owner.name} with {@code descriptor}. */
    private static Map<String, Integer> callsTo(String owner, String name, String descriptor) {
        Map<String, Integer> calls = new TreeMap<>();
        for (ClassModel model : WhatWasCompiled.compiled().all()) {
            String from = model.thisClass().asInternalName().replace('/', '.').replace('$', '.');
            for (MethodModel method : model.methods()) {
                method.code().ifPresent(code -> code.forEach(element -> {
                    if (element instanceof InvokeInstruction call
                            && call.owner().asInternalName().equals(owner)
                            && call.name().stringValue().equals(name)
                            && call.type().stringValue().equals(descriptor)) {
                        calls.merge(from + "." + method.methodName().stringValue(), 1, Integer::sum);
                    }
                }));
            }
        }
        return calls;
    }
}
