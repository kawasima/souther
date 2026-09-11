package souther.compiler;

import souther.compiler.diag.PhysicalRegion;
import souther.compiler.cst.SourceLayout;
import souther.compiler.diag.Primary;

import souther.compiler.diag.CompileException;
import souther.compiler.diag.HumanRenderer;
import souther.compiler.diag.SourceContext;
import souther.compiler.jvm.ClassFileImage;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A JVM method takes at most 255 argument slots, and an instance method spends one of them on
 * {@code this}. What a declaration turns into is a method with one slot per field, parameter or
 * dependency — two for an {@code Int}, which is carried as a {@code long} — so a wide enough
 * declaration writes a class no JVM will load. Said at the declaration, before anything is emitted.
 */
class AGeneratedMethodStaysWithinTheJvmParameterSlotLimitTest {

    @Test
    void aDataOf127IntFieldsLoadsAndOneMoreIsSaid() throws Exception {
        loadAll(Compiler.compile(dataOf(127, "Int")));

        CompileException e = assertThrows(CompileException.class,
                () -> Compiler.compile(dataOf(128, "Int")));
        assertEquals("E2101", e.code(), e.getMessage());
        assertTrue(e.getMessage().contains("Wide"), e.getMessage());
    }

    @Test
    void aDecimalFieldTakesOneSlotSoTheLimitIs254OfThem() throws Exception {
        loadAll(Compiler.compile(dataOf(254, "Decimal")));

        CompileException e = assertThrows(CompileException.class,
                () -> Compiler.compile(dataOf(255, "Decimal")));
        assertEquals("E2101", e.code(), e.getMessage());
    }

    @Test
    void theFieldsAnIncludeBringsAreCounted() {
        String src = "module demo\n\ndata Common = { "
                + fields(200, "String")
                + " }\n\ndata Wide = { ...Common, "
                + IntStream.range(200, 255).mapToObj(i -> "f" + i + ": String")
                        .collect(Collectors.joining(", "))
                + " }\n\nbehavior f : (w: Wide) -> Wide\n\nlet f (w) = w\n";

        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));
        assertEquals("E2101", e.code(), e.getMessage());
        assertTrue(e.getMessage().contains("Wide"), e.getMessage());
    }

    @Test
    void aBehaviorOf254ParametersLoadsAndOneMoreIsSaid() throws Exception {
        loadAll(Compiler.compile(behaviorOf(254, "Int")));

        CompileException e = assertThrows(CompileException.class,
                () -> Compiler.compile(behaviorOf(255, "Int")));
        assertEquals("E2101", e.code(), e.getMessage());
        assertTrue(e.getMessage().contains("f"), e.getMessage());
    }

    @Test
    void aBehaviorsParameterTakesOneSlotWhateverItsType() {
        // an apply parameter is a reference (Long for Int), so 255 Int parameters break at the same
        // count as 255 String ones — unlike a data's fields, where an Int takes two slots
        CompileException e = assertThrows(CompileException.class,
                () -> Compiler.compile(behaviorOf(255, "String")));
        assertEquals("E2101", e.code(), e.getMessage());
    }

    @Test
    void aRecursiveHelperOf255ParametersLoadsAndOneMoreIsSaid() throws Exception {
        loadAll(Compiler.compile(helperOf(255)));

        CompileException e = assertThrows(CompileException.class,
                () -> Compiler.compile(helperOf(256)));
        assertEquals("E2101", e.code(), e.getMessage());
        assertTrue(e.getMessage().contains("spin"), e.getMessage());
    }

    @Test
    void theDependenciesABehaviorIsBuiltWithAreCounted() {
        CompileException e = assertThrows(CompileException.class,
                () -> Compiler.compile(dependentOf(255)));
        assertEquals("E2101", e.code(), e.getMessage());
        assertTrue(e.getMessage().contains("use"), e.getMessage());
    }

    @Test
    void itSaysTheSlotsNeededTheLimitAndWhatToDo() {
        String src = dataOf(128, "Int");
        CompileException e = assertThrows(CompileException.class, () -> Compiler.compile(src));

        String said = new HumanRenderer(false).render(e.diagnostic(),
                new SourceContext("demo.sou", src, SourceLayout.of(src)), Locale.ENGLISH);
        assertTrue(said.contains("256"), "the slots it needs: " + said);
        assertTrue(said.contains("254"), "the slots it may take: " + said);
        assertTrue(said.contains("Split"), "what to do about it: " + said);

        String ja = new HumanRenderer(false).render(e.diagnostic(),
                new SourceContext("demo.sou", src, SourceLayout.of(src)), Locale.JAPANESE);
        assertTrue(ja.contains("256"), ja);
        assertTrue(ja.contains("data"), ja);
    }

    /**
     * The refusal underlines the name it is about, which is not where the declaration starts.
     *
     * <p>`data` comes first, so anchoring at the declaration and taking the name's length for a
     * width underlines the keyword and stops before the name — a caret under `data` for a report
     * whose subject is `Wide`. The declaration carries where its name is written, so neither number
     * has to be worked out here.
     */
    @Test
    void theRefusalUnderlinesTheNameAndNotTheKeywordInFrontOfIt() {
        String source = dataOf(128, "Int");
        CompileException e = assertThrows(CompileException.class,
                () -> Compiler.compile(source));

        PhysicalRegion region = WhereItSits.in(source,
                ((Primary.InSource) e.diagnostic().primary()).place().region());
        assertEquals(3, region.start().line(), "the `data Wide` line");
        assertEquals("module demo\n\ndata ".length()
                - "module demo\n\n".length() + 1, region.start().column(),
                "the caret sits on `data` rather than on the name it is about");
        assertEquals("Wide".length(), region.end().column() - region.start().column());
    }

    private static String fields(int n, String type) {
        return IntStream.range(0, n).mapToObj(i -> "f" + i + ": " + type)
                .collect(Collectors.joining(", "));
    }

    private static String dataOf(int n, String type) {
        return "module demo\n\ndata Wide = { " + fields(n, type)
                + " }\n\nbehavior f : (w: Wide) -> Wide\n\nlet f (w) = w\n";
    }

    private static String behaviorOf(int n, String type) {
        String params = IntStream.range(0, n).mapToObj(i -> "p" + i + ": " + type)
                .collect(Collectors.joining(", "));
        String args = IntStream.range(0, n).mapToObj(i -> "p" + i).collect(Collectors.joining(", "));
        return "module demo\n\ndata Out = { n: " + type + " }\n\nbehavior f : (" + params
                + ") -> Out\n    constructs Out\nlet f (" + args + ") = Out { n = p0 }\n";
    }

    private static String helperOf(int n) {
        String params = IntStream.range(0, n).mapToObj(i -> "p" + i + ": Int")
                .collect(Collectors.joining(", "));
        String args = IntStream.range(0, n).mapToObj(i -> "p" + i).collect(Collectors.joining(", "));
        return "module demo\n\ndata Out = { n: Int }\n\npartial let spin (" + params + "): Int = spin("
                + args + ")\n\nbehavior f : (n: Int) -> Out\n    constructs Out\n"
                + "let f (n) = Out { n = spin(" + IntStream.range(0, n).mapToObj(_ -> "n")
                        .collect(Collectors.joining(", ")) + ") }\n";
    }

    private static String dependentOf(int n) {
        StringBuilder sb = new StringBuilder("module demo\n\ndata Out = { n: Int }\n\n");
        for (int i = 0; i < n; i++) {
            sb.append("behavior dep").append(i).append(" : (o: Out) -> Out\n\n");
        }
        String names = IntStream.range(0, n).mapToObj(i -> "dep" + i).collect(Collectors.joining(", "));
        sb.append("behavior use : (o: Out) -> Out\n    depends on ").append(names).append("\n");
        // `depends on` must name exactly what the body calls (E1603), so every dependency is applied;
        // one `let` each rather than a nest, which reads too deeply at this width
        StringBuilder body = new StringBuilder("{\n");
        for (int i = 0; i < n; i++) {
            body.append("    let a").append(i).append(" = dep").append(i)
                    .append("(").append(i == 0 ? "o" : "a" + (i - 1)).append(")\n");
        }
        body.append("    a").append(n - 1).append("\n}");
        sb.append("let use (o, ").append(names).append(") = ").append(body).append("\n");
        return sb.toString();
    }

    private void loadAll(Map<String, ClassFileImage> classes) throws Exception {
        BytesClassLoader loader = new BytesClassLoader(classes, getClass().getClassLoader());
        for (String name : classes.keySet()) {
            // getDeclaredMethods is what forces the descriptors to be parsed; the methods
            // themselves are not the question, whether the JVM accepts them is.
            assertDoesNotThrow(() -> Class.forName(name, false, loader).getDeclaredMethods(), name);
        }
    }
}
