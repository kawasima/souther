package souther.compiler.codegen;

import souther.compiler.Compiler;
import souther.compiler.generated.MemoryClassLoader;
import souther.compiler.jvm.ClassFileImage;
import souther.compiler.jvm.GeneratedClass;
import souther.compiler.jvm.GeneratedClasses;
import souther.runtime.Behavior;
import souther.runtime.PersistentVector;

import org.junit.jupiter.api.Test;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.constantpool.ConstantDynamicEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.instruction.ConstantInstruction.LoadConstantInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicConstantDesc;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Decimal literal is emitted once per class it is written in, and loaded where it stands.
 *
 * <p>What is held is that constructing the value does not happen once per evaluation. A literal in
 * the step of a fold is evaluated once per element, and a {@code new BigDecimal("1.1")} there
 * parses the spelling that many times. The literal is loaded as a dynamic constant: the class file
 * names the {@code BigDecimal} constructor and the spelling as the constant's bootstrap, the JVM
 * runs that once at resolution, and every later load answers from the constant pool.
 *
 * <p>Read off the class file rather than off the objects. That two loads answer the same
 * {@code BigDecimal} instance is true, and is not a property of a Souther {@code Decimal} — two
 * Decimals are equal by value and nothing in the language observes their identity. What the
 * language does observe is what an evaluation costs, and the class file says that: one
 * {@code ldc} of a dynamic constant per literal, and one pool entry per spelling per class.
 */
class ADecimalLiteralIsAConstantOfTheClassItIsWrittenInTest {

    private static final String MODULE = """
            module demo

            // The literal is in the step of a fold, which is evaluated once per element.
            behavior priced : (xs: List<Decimal>) -> Decimal
            let priced (xs) = List.fold((acc, x) -> acc + x * 1.1m, 0.0m, xs)

            // The same spelling twice in one body: one pool entry, loaded twice.
            behavior twice : (d: Decimal) -> Decimal
            let twice (d) = d * 1.1m + 1.1m
            """;

    /** A spelling and how it reached the code of one class: loaded as a constant, or built. */
    private record Reached(List<String> loadedSpellings, int built, int poolEntries) {}

    private static Map<String, Reached> reached() {
        Map<String, Reached> byClass = new TreeMap<>();
        for (Map.Entry<String, ClassFileImage> emitted : Compiler.compile(MODULE).entrySet()) {
            ClassModel model = ClassFile.of().parse(emitted.getValue().bytes());
            List<String> loaded = new ArrayList<>();
            int built = 0;
            for (MethodModel method : model.methods()) {
                if (!(method.code().orElse(null) instanceof CodeModel body)) {
                    continue;
                }
                for (CodeElement element : body) {
                    switch (element) {
                        case NewObjectInstruction made
                                when "java/math/BigDecimal".equals(made.className().asInternalName())
                                -> built++;
                        case LoadConstantInstruction load
                                when load.constantValue() instanceof DynamicConstantDesc<?> constant
                                -> loaded.add(spellingOf(constant));
                        default -> { }
                    }
                }
            }
            int entries = 0;
            for (PoolEntry entry : model.constantPool()) {
                if (entry instanceof ConstantDynamicEntry dynamic
                        && "decimal".equals(dynamic.name().stringValue())) {
                    entries++;
                }
            }
            if (!loaded.isEmpty() || built > 0 || entries > 0) {
                byClass.put(emitted.getKey(), new Reached(loaded, built, entries));
            }
        }
        return byClass;
    }

    /**
     * The spelling a literal's constant carries, having checked that it is the literal's constant:
     * resolved by running the {@code BigDecimal(String)} constructor on that spelling, and nothing
     * else.
     */
    private static String spellingOf(DynamicConstantDesc<?> constant) {
        assertEquals(ConstantDescs.BSM_INVOKE, constant.bootstrapMethod(),
                "a Decimal literal is resolved by ConstantBootstraps.invoke");
        assertEquals(2, constant.bootstrapArgs().length, "a constructor and its one argument");
        assertTrue(constant.bootstrapArgs()[0] instanceof DirectMethodHandleDesc handle
                        && handle.kind() == DirectMethodHandleDesc.Kind.CONSTRUCTOR
                        && "Ljava/math/BigDecimal;".equals(handle.owner().descriptorString()),
                "the constant runs the BigDecimal constructor: " + constant);
        return (String) constant.bootstrapArgs()[1];
    }

    @Test
    void everyLiteralIsLoadedAsAConstantAndNoneIsConstructedWhereItStands() {
        Map<String, Reached> byClass = reached();
        List<String> spellings = new ArrayList<>();
        for (Map.Entry<String, Reached> each : byClass.entrySet()) {
            assertEquals(0, each.getValue().built(),
                    each.getKey() + " constructs a BigDecimal where a literal stands");
            spellings.addAll(each.getValue().loadedSpellings());
        }
        // Every literal the module writes, and each as many times as it is written: `0.0m` once,
        // `1.1m` three times. A walk that stopped seeing loads would pass the assertion above on
        // its own.
        assertEquals(List.of("0.0", "1.1", "1.1", "1.1"), spellings.stream().sorted().toList());
    }

    @Test
    void oneSpellingIsOnePoolEntryHoweverOftenItIsLoaded() {
        Map<String, Reached> byClass = reached();
        boolean loadedTwiceSomewhere = false;
        for (Map.Entry<String, Reached> each : byClass.entrySet()) {
            Reached reached = each.getValue();
            long distinct = reached.loadedSpellings().stream().distinct().count();
            assertEquals(distinct, reached.poolEntries(),
                    each.getKey() + " holds a pool entry per load rather than per spelling: "
                            + reached.loadedSpellings());
            loadedTwiceSomewhere |= reached.loadedSpellings().size() > distinct;
        }
        assertTrue(loadedTwiceSomewhere,
                "no class loads one spelling twice, so the entry count says nothing: " + byClass);
    }

    @Test
    void theFoldAnswersWithTheLiteralInItsStep() throws Exception {
        ClassLoader loader = new MemoryClassLoader(Compiler.compile(MODULE),
                getClass().getClassLoader());
        Behavior<Object, Object> priced = behavior(loader, "priced");
        Behavior<Object, Object> twice = behavior(loader, "twice");

        PersistentVector<BigDecimal> xs = PersistentVector.from(
                List.of(new BigDecimal("1"), new BigDecimal("2"), new BigDecimal("3")));
        assertEquals(0, new BigDecimal("6.6").compareTo((BigDecimal) priced.apply(xs)));
        assertEquals(0, new BigDecimal("2.2").compareTo((BigDecimal) twice.apply(new BigDecimal("1"))));
    }

    @SuppressWarnings("unchecked")
    private static Behavior<Object, Object> behavior(ClassLoader loader, String name)
            throws ReflectiveOperationException {
        Class<?> emitted = GeneratedClasses.load(loader, new GeneratedClass.BehaviorImpl("demo", name));
        Constructor<?> ctor = emitted.getDeclaredConstructor();
        ctor.setAccessible(true);
        return (Behavior<Object, Object>) ctor.newInstance();
    }
}
