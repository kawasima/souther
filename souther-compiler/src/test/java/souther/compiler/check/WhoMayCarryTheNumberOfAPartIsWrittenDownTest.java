package souther.compiler.check;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
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
 * Who carries the number of a part beside the rule it is a part of, rather than the name that was
 * issued for it.
 *
 * <p>Which part of which rule a line came out of is one value ({@link PartId}), made where a clause
 * is split and carried from there. Held as a rule and a number side by side, the number has to come
 * from somewhere, and where it came from was whichever walk the holder happened to have — which is
 * how one authored part came to be numbered by two counters over two trees.
 *
 * <p>So a type downstream of that split holds the name and not the number, and this is the list of
 * what still holds one. There is one entry, and it is the pair itself: the place a clause's parts
 * are numbered, which is the place every reader below holds a name instead of.
 *
 * <p>The other number a clause has is not here and is not this question. What one part states is
 * read off the tree that part expanded into, and which of those statements a line came out of is
 * counted within the part — a different decomposition, with a mint and a check of its own
 * ({@link souther.compiler.partition.ClauseStatementId}). Held on one list, the two would be one
 * allowance covering two things, and either could grow a second counter under the other's reason.
 *
 * <p>Read off the compiled classes, because what a type carries is what the class file says.
 */
class WhoMayCarryTheNumberOfAPartIsWrittenDownTest {

    /** A field that holds such a number, and why it is not the name of an authored part. */
    private record Held(String who, String why) { }

    private static final List<Held> MAY_HOLD = List.of(
            new Held("souther.compiler.check.PartId.ordinal",
                    "the pair itself, made where a clause is split and carried from there. This is"
                            + " the name everything downstream holds instead of a number, so it is"
                            + " the one place the two stand together"));

    @Test
    void aNumberBesideARuleIsWrittenDownWithWhatItCounts() throws IOException {
        assertEquals(declared(MAY_HOLD), numbersHeldBesideARule(),
                "a type downstream of the split that numbers a clause's parts holds the name that"
                        + " split issued, not a number of its own. What still holds one, and what"
                        + " that number counts: " + why(MAY_HOLD));
    }

    private static Map<String, String> declared(List<Held> held) {
        Map<String, String> out = new TreeMap<>();
        held.forEach(each -> out.put(each.who(), ""));
        return out;
    }

    private static Map<String, String> why(List<Held> held) {
        Map<String, String> out = new TreeMap<>();
        held.forEach(each -> out.put(each.who(), each.why()));
        return out;
    }

    /**
     * Every number this compiler holds beside a rule.
     *
     * <p>Asked of what a state holds and not of what a field is called. A number beside a rule is
     * the pair whatever it is named, and a check that looked for the word would be satisfied by
     * renaming the field.
     */
    private static Map<String, String> numbersHeldBesideARule() throws IOException {
        Map<String, String> found = new TreeMap<>();
        int read = 0;
        for (Path each : classes()) {
            ClassModel model = ClassFile.of().parse(Files.readAllBytes(each));
            read++;
            String from = model.thisClass().asInternalName().replace('/', '.').replace('$', '.');
            boolean holdsARule = false;
            for (FieldModel field : model.fields()) {
                holdsARule |= field.fieldType().stringValue()
                        .startsWith("Lsouther/compiler/check/RuleRef");
            }
            if (!holdsARule) {
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

    private static List<Path> classes() throws IOException {
        Path root = Path.of("target", "classes").toAbsolutePath();
        try (Stream<Path> walk = Files.walk(root)) {
            return new ArrayList<>(walk.filter(p -> p.toString().endsWith(".class")).toList());
        }
    }
}
