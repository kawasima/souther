package souther.compiler.partition;

import souther.compiler.check.PartId;

import org.junit.jupiter.api.Test;
import souther.compiler.WhatWasCompiled;

import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Who carries the number of a statement beside the part it is a statement of, and who may make one.
 *
 * <p>The second of a clause's two decompositions. Which part of a clause a conjunct is was written
 * by its author and is named where the clause is split ({@link PartId}); what one of those parts
 * states is read off the tree the part expanded into, where a helper's body brings connectives
 * nobody wrote — so a part states as many things as the reading finds, and which of them a line came
 * out of is the number this is about.
 *
 * <p>Two things are held, and they are the two that went wrong when this number had no name. It is
 * counted in one place, so that a reader cannot arrive at a statement's number by walking the part
 * again; and it is held in one place, so that a reader cannot put a number it has beside a part it
 * is holding. Counted where the lines were drawn, the number ran on across the parts of the clause —
 * one running count answering both which part the author wrote and which thing that part states, and
 * neither recoverable from it.
 *
 * <p>Read off the compiled classes, because what a type carries and what a method calls are what the
 * class file says.
 */
class WhoMayCarryTheNumberOfAStatementIsWrittenDownTest {

    private static final String STATEMENT_ID = "souther/compiler/partition/ClauseStatementId";

    private static final String INVARIANT_STATEMENT_ID =
            "souther/compiler/check/InvariantStatementId";

    /** A field that holds such a number, or a method that makes one, and why it may. */
    private record Licence(String who, String why) { }

    private static final List<Licence> MAY_HOLD = List.of(
            new Licence("souther.compiler.partition.ClauseStatementId.ordinal",
                    "the pair itself, made where a part is read for what it states and carried from"
                            + " there. This is the name everything downstream holds instead of a"
                            + " number, so it is the one place the two stand together"),
            new Licence("souther.compiler.check.InvariantStatementId.ordinal",
                    "the same pair for a declaration's clause, which is decomposed twice as a"
                            + " behavior's is. Two of them and not one, because the number means"
                            + " which statement of the part its own reading arrived at: the readings"
                            + " are separate and a number issued by either would be a place in the"
                            + " other's list"));

    private static final List<Licence> MAY_MAKE = List.of(
            new Licence("souther.compiler.partition.ClauseStatements.of",
                    "the one place a part is taken apart into the things it states, which is where"
                            + " the place each of them holds among that part's statements was"
                            + " assigned"));

    private static final List<Licence> MAY_MAKE_AN_INVARIANTS = List.of(
            new Licence("souther.compiler.check.InvariantChecker.direct",
                    "the walk that reads a declaration's conjunct for what it states, which is where"
                            + " a statement is recognised. Numbered where an end or a hand-over is"
                            + " written down instead, the number would say which of the outcomes"
                            + " this was rather than which of the statements"));

    /**
     * Only the reading that took a part apart names one of its statements.
     *
     * <p>A second place that made one would be a second place that decided which statement a
     * statement is, which is the number being counted by whoever was holding the part.
     */
    @Test
    void onlyTheReadingThatTookThePartApartNamesOne() {
        assertEquals(named(MAY_MAKE), callsTo(STATEMENT_ID, "<init>"),
                "a statement named anywhere else is a number somebody counted for themselves put"
                        + " beside whichever part they were holding. What may make one, and why: "
                        + why(MAY_MAKE));
    }

    /**
     * And the same of a declaration's clause, whose statements a walk of its own arrives at.
     *
     * <p>Asked apart from the behavior's, because the two readings are apart. What they share is
     * that the number means a place in one reading's list of what a part states, so a second maker
     * on either side is a second answer to which statement a statement is.
     */
    @Test
    void andOnlyTheWalkThatReadsADeclarationsConjunctNamesOneOfIts() {
        assertEquals(named(MAY_MAKE_AN_INVARIANTS), callsTo(INVARIANT_STATEMENT_ID, "<init>"),
                "a statement named anywhere else is a number somebody counted for themselves put"
                        + " beside whichever part they were holding. What may make one, and why: "
                        + why(MAY_MAKE_AN_INVARIANTS));
    }

    /** And nothing downstream of that reading holds the number instead of the name. */
    @Test
    void aNumberBesideAPartIsWrittenDownWithWhatItCounts() {
        assertEquals(named(MAY_HOLD), numbersHeldBesideAPart(),
                "a type downstream of the reading that numbers a part's statements holds the name"
                        + " that reading issued, not a number of its own. What still holds one, and"
                        + " what that number counts: " + why(MAY_HOLD));
    }

    private static Map<String, String> named(List<Licence> licences) {
        Map<String, String> out = new TreeMap<>();
        licences.forEach(each -> out.put(each.who(), ""));
        return out;
    }

    private static Map<String, String> why(List<Licence> licences) {
        Map<String, String> out = new TreeMap<>();
        licences.forEach(each -> out.put(each.who(), each.why()));
        return out;
    }

    /**
     * Every number this compiler holds beside a part of a clause.
     *
     * <p>Asked of what a state holds and not of what a field is called. A number beside a part is
     * the pair whatever it is named, and a check that looked for the word would be satisfied by
     * renaming the field.
     */
    private static Map<String, String> numbersHeldBesideAPart() {
        Map<String, String> found = new TreeMap<>();
        for (ClassModel model : WhatWasCompiled.compiled().all()) {
            String from = model.thisClass().asInternalName().replace('/', '.').replace('$', '.');
            boolean holdsAPart = false;
            for (FieldModel field : model.fields()) {
                holdsAPart |= field.fieldType().stringValue()
                        .equals("Lsouther/compiler/check/PartId;");
            }
            if (!holdsAPart) {
                continue;
            }
            for (FieldModel field : model.fields()) {
                if (field.fieldType().stringValue().equals("I")) {
                    found.put(from + "." + field.fieldName().stringValue(), "");
                }
            }
        }
        return found;
    }

    /** Which methods of the compiler call {@code owner.name}. */
    private static Map<String, String> callsTo(String owner, String name) {
        Map<String, String> calls = new TreeMap<>();
        for (ClassModel model : WhatWasCompiled.compiled().all()) {
            String from = model.thisClass().asInternalName().replace('/', '.').replace('$', '.');
            for (MethodModel method : model.methods()) {
                String where = from + "." + method.methodName().stringValue();
                method.code().ifPresent(code -> code.forEach(element -> {
                    if (element instanceof InvokeInstruction call
                            && call.owner().asInternalName().equals(owner)
                            && call.name().stringValue().equals(name)) {
                        calls.put(where, "");
                    }
                }));
            }
        }
        return calls;
    }
}
