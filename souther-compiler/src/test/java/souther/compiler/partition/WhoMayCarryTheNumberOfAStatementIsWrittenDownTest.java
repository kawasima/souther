package souther.compiler.partition;

import souther.compiler.check.PartId;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

    /** A field that holds such a number, or a method that makes one, and why it may. */
    private record Licence(String who, String why) { }

    private static final List<Licence> MAY_HOLD = List.of(
            new Licence("souther.compiler.partition.ClauseStatementId.ordinal",
                    "the pair itself, made where a part is read for what it states and carried from"
                            + " there. This is the name everything downstream holds instead of a"
                            + " number, so it is the one place the two stand together"));

    private static final List<Licence> MAY_MAKE = List.of(
            new Licence("souther.compiler.partition.ClauseStatements.of",
                    "the one place a part is taken apart into the things it states, which is where"
                            + " the place each of them holds among that part's statements was"
                            + " assigned"));

    /**
     * Only the reading that took a part apart names one of its statements.
     *
     * <p>A second place that made one would be a second place that decided which statement a
     * statement is, which is the number being counted by whoever was holding the part.
     */
    @Test
    void onlyTheReadingThatTookThePartApartNamesOne() throws IOException {
        assertEquals(named(MAY_MAKE), callsTo(STATEMENT_ID, "<init>"),
                "a statement named anywhere else is a number somebody counted for themselves put"
                        + " beside whichever part they were holding. What may make one, and why: "
                        + why(MAY_MAKE));
    }

    /** And nothing downstream of that reading holds the number instead of the name. */
    @Test
    void aNumberBesideAPartIsWrittenDownWithWhatItCounts() throws IOException {
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
    private static Map<String, String> numbersHeldBesideAPart() throws IOException {
        Map<String, String> found = new TreeMap<>();
        int read = 0;
        for (ClassModel model : compiled()) {
            read++;
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
        assertFalse(read == 0, "no compiled class was read at all, so this says nothing");
        return found;
    }

    /** Which methods of the compiler call {@code owner.name}. */
    private static Map<String, String> callsTo(String owner, String name) throws IOException {
        Map<String, String> calls = new TreeMap<>();
        int read = 0;
        for (ClassModel model : compiled()) {
            read++;
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
        assertFalse(read == 0, "no compiled class was read at all, so this says nothing");
        return calls;
    }

    /**
     * Every compiled class, parsed once for both questions.
     *
     * <p>The two ask different things of the same classes, and reading the tree twice is reading
     * every class file of the compiler twice for an answer that does not change between them.
     */
    private static List<ClassModel> compiled() throws IOException {
        if (COMPILED != null) {
            return COMPILED;
        }
        Path root = Path.of("target", "classes").toAbsolutePath();
        List<ClassModel> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path each : walk.filter(p -> p.toString().endsWith(".class")).toList()) {
                out.add(ClassFile.of().parse(Files.readAllBytes(each)));
            }
        }
        COMPILED = List.copyOf(out);
        return COMPILED;
    }

    private static List<ClassModel> COMPILED;
}
