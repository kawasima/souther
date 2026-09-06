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
 * what still holds a number. It is not empty, and what is on it is a different question wearing the
 * same word: the lines a body's rule draws are counted over the comparisons the rule states, which
 * is not which part of a clause its author wrote. Those two share a field today, and telling them
 * apart is its own change.
 *
 * <p>Read off the compiled classes, because what a type carries is what the class file says.
 */
class WhoMayCarryTheNumberOfAPartIsWrittenDownTest {

    /** A field that holds such a number, and why it is not the name of an authored part. */
    private record Held(String who, String why) { }

    private static final List<Held> MAY_HOLD = List.of(
            new Held("souther.compiler.check.DeclaredBorders.Key.conjunct",
                    "which line of a declaration's clause a report reads its own words for. What"
                            + " reaches it from the report side is a line of the model, which"
                            + " carries a number rather than a part's name, so a key built from"
                            + " the part that drew the line and one built from a line have to be"
                            + " one key"),
            new Held("souther.compiler.partition.AuthoredLine.conjunct",
                    "which line of a rule this is, counted over the conjuncts an author wrote for a"
                            + " declaration's clause and over the comparisons a body's rule states."
                            + " One field for two questions, which is why it is a number here and"
                            + " not a part's name"),
            new Held("souther.compiler.partition.LineOrigin.EnsuresOrigin.conjunct",
                    "which line of the clause a behavior's rule drew, counted over the comparisons"
                            + " it states rather than over the parts its author wrote"));

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

    /** Every field of the compiler named for a clause's conjunct that holds a number. */
    private static Map<String, String> numbersHeldBesideARule() throws IOException {
        Map<String, String> found = new TreeMap<>();
        int read = 0;
        for (Path each : classes()) {
            ClassModel model = ClassFile.of().parse(Files.readAllBytes(each));
            read++;
            String from = model.thisClass().asInternalName().replace('/', '.').replace('$', '.');
            for (FieldModel field : model.fields()) {
                if (field.fieldName().stringValue().equals("conjunct")
                        && field.fieldType().stringValue().equals("I")) {
                    found.put(from + ".conjunct", "");
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
