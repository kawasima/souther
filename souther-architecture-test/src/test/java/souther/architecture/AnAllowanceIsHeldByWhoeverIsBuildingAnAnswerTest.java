package souther.architecture;

import souther.test.RepositoryLayout;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.reflect.AccessFlag;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who holds an allowance, and where an operation says which answer is paying.
 *
 * <p>An {@link souther.compiler.values.Allowance} is what one answer being built may spend. Held by
 * something that is building one, it is that answer's own account of itself. Held by a value that
 * answers, it is a fact about no reading in the value — and a composition of two such values has two
 * purses to be charged to, so which of them pays, and with it how exactly the composition comes out,
 * is settled by which side the call was written on rather than by anything either reading says.
 *
 * <p>So the holders are written down, and every one of them is somewhere an answer is under
 * construction:
 *
 * <pre>
 *     AdmissibleReading         reads one declaration's clauses into the sets they leave
 *     PlacedRules               reads one value's rules at the paths its positions have
 *     InvariantChecker$Seeded   one such reading part-built, for a reader that finishes it
 * </pre>
 *
 * <p>Read off the compiled classes, so a record component is a row here as readily as a field: the
 * two are one thing to a reader that can spend what it finds. A row that is new is a finding —
 * either an answer is being built somewhere new, or a purse has moved into a value.
 */
class AnAllowanceIsHeldByWhoeverIsBuildingAnAnswerTest {

    private static final String ALLOWANCE = "souther/compiler/values/Allowance";

    private static final String CONJUNCTION = "souther/compiler/values/ConjoinedAdmissibleValues";

    private static final RepositoryLayout REPOSITORY = RepositoryLayout.ofWorkingDirectory();

    /** Every production class that holds one, which is every place an answer is being built. */
    private static final List<String> HOLDING_AN_ALLOWANCE = List.of(
            "souther/compiler/check/AdmissibleReading",
            "souther/compiler/check/InvariantChecker$Seeded",
            "souther/compiler/inputs/PlacedRules");

    /**
     * And what a conjunction of readings names one for.
     *
     * <p>{@link souther.compiler.values.ConjoinedAdmissibleValues} is the value the rule above is
     * about: two of them are met, so a purse it held would be the one the meet spent. It holds none,
     * and the allowance reaches it as the argument of the one operation that may build — the meet of
     * two readings over a shared vocabulary, which comes to a set neither of them holds.
     *
     * <p>Every other operation of it reads what is already there, and a second name in this row is
     * one of those having grown a way to spend. A getter is a row too, since what it hands back is
     * the purse itself.
     */
    private static final List<String> NAMING_AN_ALLOWANCE = List.of("meet");

    @Test
    void everyHolderOfAnAllowanceIsBuildingAnAnswer() {
        assertEquals(HOLDING_AN_ALLOWANCE, holdingAnAllowance(),
                "an allowance is what one answer being built may spend, so it is held by whoever is"
                        + " building one: held by a value that answers, a composition of two of them"
                        + " has two purses and picks by which side the call was written on");
    }

    @Test
    void andAConjunctionNamesOneOnlyWhereItComposes() {
        assertEquals(NAMING_AN_ALLOWANCE, namingAnAllowance(CONJUNCTION),
                "a conjunction is composed under the caller's allowance and holds none of its own:"
                        + " another operation naming one is either a second place that spends or a"
                        + " purse handed back for somebody else to spend");
    }

    /**
     * And the walk sees a holder that is there.
     *
     * <p>Matched on a descriptor nothing has, both lists would be empty and equal to an empty
     * expectation. So the same walk is asked for something it must find: the reading of a
     * declaration's clauses holds the purse it spends.
     */
    @Test
    void andTheWalkSeesAHolderThatIsThere() {
        assertTrue(holdingAnAllowance().contains("souther/compiler/check/AdmissibleReading"),
                "the reading that spends an allowance holds it, so a walk that cannot find that is"
                        + " finding nothing at all");
    }

    /** Every class with a field of that type, which is a record component as well. */
    private static List<String> holdingAnAllowance() {
        TreeSet<String> out = new TreeSet<>();
        for (Path module : REPOSITORY.modules()) {
            for (Path each : classesUnder(module)) {
                for (FieldModel field : classOf(each).fields()) {
                    if (field.fieldType().stringValue().equals("L" + ALLOWANCE + ";")) {
                        out.add(internalName(module, each));
                    }
                }
            }
        }
        return new ArrayList<>(out);
    }

    /**
     * The methods of {@code owner} a caller can reach whose signature mentions an allowance, by
     * name.
     *
     * <p>What a caller can reach, because the rule is about which of a value's questions come with
     * a purse. A private helper is part of how one of them is written and spends what that
     * operation was handed; what it must not do is make an allowance of its own, and that is a
     * different rule with a walk of its own
     * ({@code WhoMayBuildALanguageAboutAPositionTest} counts every making and every asking).
     */
    private static List<String> namingAnAllowance(String owner) {
        TreeSet<String> out = new TreeSet<>();
        for (Path module : REPOSITORY.modules()) {
            for (Path each : classesUnder(module)) {
                if (!internalName(module, each).equals(owner)) {
                    continue;
                }
                for (MethodModel method : classOf(each).methods()) {
                    if (!method.flags().has(AccessFlag.PRIVATE)
                            && method.methodType().stringValue().contains("L" + ALLOWANCE + ";")) {
                        out.add(method.methodName().stringValue());
                    }
                }
            }
        }
        return new ArrayList<>(out);
    }

    private static ClassModel classOf(Path compiled) {
        try {
            return ClassFile.of().parse(Files.readAllBytes(compiled));
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
