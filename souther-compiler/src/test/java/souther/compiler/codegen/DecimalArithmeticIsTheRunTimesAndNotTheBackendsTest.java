package souther.compiler.codegen;

import souther.compiler.Compiler;
import souther.compiler.core.Kernel;

import souther.compiler.jvm.ClassFileImage;

import org.junit.jupiter.api.Test;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.ConstantInstruction.LoadConstantInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ConstantDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicConstantDesc;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The backend knows what a {@code Decimal} is represented by and does not decide what an operation
 * on one means (ADR-0112).
 *
 * <p>Those are two different things and only the second is refused here. {@code BigDecimal} is the
 * JVM form of a {@code Decimal}, so the backend builds a literal with it, names it in a descriptor
 * and casts to it, and a test asking that the name not appear would fail on a model that writes
 * {@code 1.5m}. What it may not do is perform a Souther operation with a {@code BigDecimal} method:
 * {@code a + b} emits a call to {@code DecimalMath.add}, as an {@code Int} {@code +} has always
 * emitted one to {@code IntMath.addExact}.
 *
 * <p>This is the rule that was not held. {@code +}, {@code -} and {@code *} went straight to
 * {@code BigDecimal}, whose sum, difference and product are all partial at the ends of the scale
 * range — so a scale overflow left a behavior as {@code java.lang.ArithmeticException}, from a
 * program that has no such type. {@code Decimal.divide} was written out in {@code BodyGen} and
 * narrowed its scale with a raw {@code l2i} (issue #976). Both are shapes this refuses structurally:
 * an operation written the old way stops the build rather than waiting to be noticed.
 */
class DecimalArithmeticIsTheRunTimesAndNotTheBackendsTest {

    /**
     * What generated code may invoke on a {@code java.math.BigDecimal}, and nothing else.
     *
     * <p>An allowlist and not a list of forbidden operations. A list of the ones to refuse can only
     * refuse the ones whoever wrote it thought of, and what #976 was is nobody thinking of one:
     * {@code add}, {@code subtract} and {@code multiply} sat under a comment saying Decimal does not
     * overflow. Written that way this test would pass a later kernel backed by
     * {@code BigDecimal.sqrt}, which is partial, because {@code sqrt} is not a name anyone listed.
     * Written this way that kernel stops the build and is considered.
     *
     * <p>What is on it is building a value and asking a question of one, which is the backend
     * knowing the representation. Everything that answers a {@code Decimal} is an operation the
     * language has, and those are {@code DecimalMath}'s.
     */
    private static final Set<String> REPRESENTATION = Set.of(
            "<init>",       // a literal, named as the bootstrap of the constant that loads it
            "signum",       // the zero test the `/` operator branches on
            "compareTo",    // the comparison operators, and a Map/Set key
            "equals", "hashCode", "toString");

    /** Every operator and every Decimal kernel a model can write, in one module. */
    private static final String MODULE = """
            module demo

            data In = { a: Decimal, b: Decimal, s: Int, n: Int }
            data Out = { value: Decimal, m: Int }

            behavior ops : (i: In) -> Out constructs Out
            let ops (i) = Out {
                value = i.a + i.b - i.a * i.b / i.b,
                m = Decimal.compare(i.a, i.b) + Decimal.toInt(HALF_UP, i.a)
            }

            behavior more : (i: In) -> Out constructs Out
            let more (i) = Out {
                value = Decimal.round(i.s, HALF_UP, Decimal.add(i.a, Decimal.fromInt(i.n))),
                m = 0
            }

            behavior lit : (i: In) -> Out constructs Out
            let lit (i) = Out { value = 1.5m + 0.0m - i.a, m = 0 }

            // Unary minus, and the comparison and equality a Decimal reaches BigDecimal for. The
            // negation is here because the list above named it and no fixture ran it, so a call
            // this test declared it refused went on being emitted and the test stayed green.
            behavior unary : (i: In) -> Out constructs Out
            let unary (i) = Out {
                value = -i.a + Decimal.abs(i.b) + Decimal.min(i.a, i.b) + Decimal.clamp(i.a, i.b, i.a),
                m = if i.a < i.b then 1 else if i.a == i.b then 2 else 3
            }

            behavior divv : (i: In) -> Out constructs Out
            let divv (i) =
                match Decimal.divide(i.a, i.b, i.s, HALF_UP) with
                    | Decimal as q -> Out { value = q, m = 1 }
                    | DivisionByZero -> Out { value = i.a, m = 0 }
            """;

    /**
     * Two ways a method reaches a {@code BigDecimal} method, read into one vocabulary. An
     * invocation stands in the code. A literal is loaded as a dynamic constant whose bootstrap
     * arguments name the constructor as a method handle, and the JVM runs it at resolution — which
     * is a way to run any {@code BigDecimal} method without an invoke instruction to read. Both go
     * through the same allowlist, so that there is one policy and not one per way.
     */
    private static List<String> reaches(CodeElement element) {
        return switch (element) {
            case InvokeInstruction call ->
                    List.of(call.owner().asInternalName() + "." + call.name().stringValue());
            case LoadConstantInstruction loaded
                    when loaded.constantValue() instanceof DynamicConstantDesc<?> constant -> {
                List<String> handles = new ArrayList<>();
                for (ConstantDesc argument : constant.bootstrapArgs()) {
                    if (argument instanceof DirectMethodHandleDesc handle) {
                        handles.add(handle.owner().descriptorString()
                                .replaceAll("^L|;$", "") + "." + handle.methodName());
                    }
                }
                yield handles;
            }
            default -> List.of();
        };
    }

    @Test
    void noEmittedCodeRunsADecimalOperationOnBigDecimalItself() {
        Set<String> byTheBackend = new TreeSet<>();
        Set<String> onBigDecimal = new TreeSet<>();
        for (Map.Entry<String, ClassFileImage> emitted : Compiler.compile(MODULE).entrySet()) {
            for (MethodModel method : ClassFile.of().parse(emitted.getValue().bytes()).methods()) {
                if (!(method.code().orElse(null) instanceof CodeModel body)) {
                    continue;
                }
                for (CodeElement element : body) {
                    for (String target : reaches(element)) {
                        if (!target.startsWith("java/math/BigDecimal.")) {
                            continue;
                        }
                        String name = target.substring(target.lastIndexOf('.') + 1);
                        onBigDecimal.add(name);
                        if (!REPRESENTATION.contains(name)) {
                            byTheBackend.add(emitted.getKey() + "."
                                    + method.methodName().stringValue()
                                    + " calls BigDecimal." + name);
                        }
                    }
                }
            }
        }

        // The literal in `lit` reaches the constructor through a dynamic constant and through no
        // invoke instruction. If the walk stops seeing it, the allowlist above is being applied to
        // half of what reaches BigDecimal, and this test is green over an emitter that hides an
        // operation the same way.
        assertEquals(true, onBigDecimal.contains("<init>"),
                "no literal's constructor was read out of a dynamic constant; what reached"
                        + " BigDecimal was " + onBigDecimal);

        assertEquals(Set.of(), byTheBackend,
                "the backend invokes something on BigDecimal that is not building a value or asking"
                        + " a question of one — if it is a Decimal operation, route it through"
                        + " DecimalMath, which owns what it means and how it fails; if it is"
                        + " representation, add it above and say why (ADR-0112)");
    }

    /**
     * And the other half: every Decimal kernel is emitted one way, reading its own declaration and
     * owned by the Decimal runtime.
     *
     * <p>These were four shapes — a hand-written emitter in {@code BodyGen} for {@code divide}, a
     * JDK instance call for {@code add}/{@code subtract}/{@code multiply}, a descriptor derived from
     * the call for {@code compare}/{@code fromInt}, and a declaration-reading one for
     * {@code toInt}/{@code round}. Four shapes is four places to decide a question that has one
     * answer, which is how {@code divide} came to be the one narrowing its scale.
     *
     * <p>What each is emitted as is now what every runtime kernel is emitted as, so what is left to
     * hold here is the owner: a Decimal operation is answered by the Decimal runtime and by nothing
     * else.
     */
    @Test
    void everyDecimalKernelIsOneEmitterOwnedByTheDecimalRunTime() {
        List<String> otherwise = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        // By the module that declares them, which is what a kernel's key spells. The question here
        // is about one module of the library, not about which operation a call reaches.
        for (Map.Entry<Kernel, Intrinsics.Emit> row : Intrinsics.emitters().entrySet()) {
            if (!row.getKey().key().startsWith("decimal.")) {
                continue;
            }
            seen.add(row.getKey().key());
            if (!(row.getValue() instanceof Intrinsics.RuntimeStatic kernel)
                    || !Descriptors.CD_DecimalMath.equals(kernel.owner())) {
                otherwise.add(row.getKey().key() + " is emitted as " + row.getValue());
            }
        }

        assertEquals(List.of(), otherwise,
                "a Decimal kernel is emitted by something other than a static on DecimalMath");
        assertEquals(Set.of("decimal.add", "decimal.subtract", "decimal.multiply", "decimal.divide",
                        "decimal.compare", "decimal.fromInt", "decimal.toInt", "decimal.round"),
                seen,
                "the Decimal module gained or lost a kernel — say which shape the new one takes");
    }
}
