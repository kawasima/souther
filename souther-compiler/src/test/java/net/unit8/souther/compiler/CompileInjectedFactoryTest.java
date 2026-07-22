package net.unit8.souther.compiler;

import org.junit.jupiter.api.Test;

import java.lang.classfile.ClassFile;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The abstract base an injected behavior's Java implementation extends hands out a {@code protected}
 * factory for each type the behavior declares it {@code constructs} — for a field-bearing type as
 * well as a unit case, so the implementation can build its declared output directly rather than
 * round-tripping through the decoder.
 */
class CompileInjectedFactoryTest {

    private Class<?> base(String moduleSrc, String baseClass) throws Exception {
        Map<String, byte[]> classes = Compiler.compile(moduleSrc);
        return new BytesClassLoader(classes, getClass().getClassLoader()).loadClass(baseClass);
    }

    @Test
    void aFieldBearingConstructedTypeGetsATypedProtectedFactory() throws Exception {
        Class<?> mk = base("""
                module demo
                data Made = { n: Int, tag: String }
                data In = { x: Int }
                behavior mk : (i: In) -> Made constructs Made
                """, "demo.Mk");
        Class<?> made = mk.getClassLoader().loadClass("demo.Made");
        // Int -> long, String -> String, in field declaration order; returns the constructed type
        Method factory = mk.getDeclaredMethod("Made", long.class, String.class);
        assertEquals(made, factory.getReturnType());
        assertTrue(Modifier.isProtected(factory.getModifiers()), "the factory is protected");
    }

    @Test
    void aNewtypeConstructedTypeGetsASingleArgFactory() throws Exception {
        Class<?> mk = base("""
                module demo
                data OrderId = String
                    invariant String.length(value) > 0
                data In = { x: Int }
                behavior mk : (i: In) -> OrderId constructs OrderId
                """, "demo.Mk");
        Class<?> orderId = mk.getClassLoader().loadClass("demo.OrderId");
        Method factory = mk.getDeclaredMethod("OrderId", String.class);
        assertEquals(orderId, factory.getReturnType());
        assertTrue(Modifier.isProtected(factory.getModifiers()));
    }

    @Test
    void aUnitConstructedCaseKeepsItsNoArgFactory() throws Exception {
        Class<?> mk = base("""
                module demo
                data Done = { result: Int }
                data Failed
                data In = { x: Int }
                behavior mk : (i: In) -> Done | Failed constructs Done, Failed
                """, "demo.Mk");
        Class<?> failed = mk.getClassLoader().loadClass("demo.Failed");
        Method unit = mk.getDeclaredMethod("Failed");
        assertEquals(failed, unit.getReturnType());
        // and the field-bearing success case now also gets a factory
        Method done = mk.getDeclaredMethod("Done", long.class);
        assertEquals(mk.getClassLoader().loadClass("demo.Done"), done.getReturnType());
    }

    @Test
    void aContainerFactoryParameterCarriesItsElementType() throws Exception {
        // Like the value-class accessor (#17), the factory's List parameter carries List<Line>, so a
        // Java caller does not pass a raw List.
        Class<?> mk = base("""
                module demo
                data Line = { n: Int }
                data Bag = { lines: List<Line> }
                data In = { x: Int }
                behavior mk : (i: In) -> Bag constructs Bag
                """, "demo.Mk");
        Method factory = mk.getDeclaredMethod("Bag", java.util.List.class);
        assertEquals("java.util.List<demo.Line>", factory.getGenericParameterTypes()[0].getTypeName());
    }

    @Test
    void theFieldBearingFactoryBuildsThroughConstructNotTheRawConstructor() throws Exception {
        // The factory must route through __construct (which checks the invariant) and orThrow (which
        // aborts on a violation), not the raw non-checking constructor. Verify the emitted bytecode.
        Map<String, byte[]> classes = Compiler.compile("""
                module demo
                data OrderId = String
                    invariant String.length(value) > 0
                data In = { x: Int }
                behavior mk : (i: In) -> OrderId constructs OrderId
                """);
        Set<String> invoked = invokedIn(classes.get("demo.Mk"), "OrderId");
        assertTrue(invoked.contains("__construct"), "the factory runs the invariant through __construct");
        assertTrue(invoked.contains("orThrow"), "a violation aborts via orThrow");
    }

    @Test
    void aRepeatedConstructsEntryDoesNotEmitADuplicateFactory() {
        // `constructs T, T` must not emit the factory twice, which would be a duplicate-method class file
        assertDoesNotThrow(() -> base("""
                module demo
                data Made = { n: Int }
                data In = { x: Int }
                behavior mk : (i: In) -> Made constructs Made, Made
                """, "demo.Mk"));
    }

    /** The names of the methods a given method's body invokes, read from the emitted bytecode. */
    private Set<String> invokedIn(byte[] classBytes, String methodName) {
        Set<String> names = new HashSet<>();
        ClassFile.of().parse(classBytes).methods().stream()
                .filter(m -> m.methodName().stringValue().equals(methodName))
                .forEach(m -> m.code().ifPresent(code -> code.forEach(e -> {
                    if (e instanceof InvokeInstruction inv) {
                        names.add(inv.name().stringValue());
                    }
                })));
        return names;
    }

    @Test
    void aPassThroughOutputTypeGetsNoFactory() throws Exception {
        // `mk` reads Member through a decoder (it is not in `constructs`), so no factory is handed out
        // for it — only `Missing`, which the behavior mints.
        Class<?> mk = base("""
                module demo
                data Member = { id: Int }
                data Missing
                data In = { x: Int }
                behavior mk : (i: In) -> Member | Missing constructs Missing
                """, "demo.Mk");
        mk.getDeclaredMethod("Missing");   // present
        boolean memberFactory = true;
        try {
            mk.getDeclaredMethod("Member", long.class);
        } catch (NoSuchMethodException e) {
            memberFactory = false;
        }
        assertEquals(false, memberFactory, "no factory for a pass-through (non-constructed) type");
    }
}
