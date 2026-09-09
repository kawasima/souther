package souther.bench;

import org.junit.jupiter.api.Test;
import souther.compiler.jvm.DecoderKind;
import souther.compiler.jvm.GeneratedClass;
import souther.compiler.jvm.SoutherJvmAbi;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;
import souther.compiler.types.TypeSymbol;
import souther.test.CompiledClasses;

import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.constantpool.StringEntry;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nothing outside {@code souther.compiler.jvm} assembles the name of a class this compiler invents.
 *
 * <p>The guarantee this backs up is held by the type system, not by this test: a
 * {@link souther.compiler.jvm.JvmClassName} can only be made inside that package, so no reader can
 * produce a generated-class identity of its own. What the type system cannot stop is a caller
 * building a plain {@code String} that happens to be one of those names and handing it to a class
 * loader or an {@code equals}. That is what this looks for, and it is a tripwire rather than the
 * invariant: it does not have to be complete to be worth having.
 *
 * <p>It reads the constant pool rather than the source, because a joint is visible there and prose is
 * not. {@code module + "." + member.name() + "Case"} compiles to a string-concatenation recipe in
 * which every argument stands as U+0001, so the recipe holds a {@code Case} with a joint right before
 * it and a sentence that merely says the word Case does not. So
 * the general words this ABI uses can be looked for without catching every diagnostic that mentions
 * them.
 *
 * <p>The spellings are not listed here. They are asked of the ABI — the difference between what a
 * derived class is called and what it is derived from is the suffix, whatever the suffix currently is
 * — so a naming scheme changed there does not leave this looking for the old one.
 *
 * <p>It lives in the last module the reactor builds, because it reads every module's classes and they
 * have to be there. The module list is asserted, so a reactor reordered later cannot quietly shrink
 * what is covered.
 */
class TheAbiIsSpelledInOnePlaceTest {


    /** What a string-concatenation recipe stands each argument as, so a joint in an assembled name is
     *  visible where a word in a sentence is not. */
    private static final String JOINT = "\u0001";

    /** The other recipe placeholder: a constant folded into the recipe stands as U+0002. */
    private static final String CONSTANT_JOINT = "\u0002";

    /** The one package that may spell these names. */
    private static final String ABI_PACKAGE = "souther.compiler.jvm.";

    /** The one test that may write a generated class's name out, because holding the ABI to its
     *  spellings is what it is for. Everything else asks. */
    private static final String THE_CONTRACT_TEST = "souther.compiler.jvm.SoutherJvmAbiTest";

    @Test
    void noMainClassOutsideTheAbiAssemblesAGeneratedClassName() {
        // What the spellings are is stated in the contract test, not here. This only needs to know
        // that there are some and what they look like now.
        Set<String> spellings = abiSpellings();
        assertFalse(spellings.isEmpty(), "the ABI adds nothing to any name, which cannot be right");
        assertFalse(spellings.contains(""), "a kind of class whose name is its base: " + spellings);

        List<String> violations = new ArrayList<>();
        List<Path> scanned = new ArrayList<>();
        List<Path> modules = Reactor.modules();
        for (Path module : modules) {
            if (Reactor.hasMainSources(module)) {
                walk(Reactor.mainOutputOf(module), violations, spellings);
            }
            // A module with no tests of its own compiled none, and there is nothing to read.
            Reactor.testOutputOf(module).ifPresent(built -> walk(built, violations, spellings));
            scanned.add(module);
        }
        assertEquals(modules, scanned, "every module the reactor builds was read");
        assertTrue(violations.isEmpty(),
                "a generated class's name is assembled outside " + ABI_PACKAGE + ":\n"
                        + String.join("\n", violations));
    }

    /** The control: the shape this looks for is a shape it can find. Without it an emptied scan and a
     *  clean tree read the same. */
    @Test
    void andTheScanWouldSeeOneIfItWereThere() {
        Set<String> spellings = abiSpellings();
        // Every case is built out of a spelling the ABI gave, so this class holds no name of its own
        // for the scan above to catch, and the control cannot drift from what is looked for.
        String suffixed = spellings.stream().filter(sp -> sp.contains("$")).findFirst().orElseThrow();
        String worded = spellings.stream().filter(sp -> !sp.contains("$")).findFirst().orElseThrow();
        assertTrue(offends(JOINT + "." + JOINT + worded, spellings), "a joint before a spelling");
        assertTrue(offends("bench.runtime." + JOINT + suffixed, spellings), "and one in the middle");
        assertTrue(offends(JOINT + ".Foo" + suffixed, spellings),
                "and a constant tail, where the joint is not against the spelling");
        assertFalse(offends("a diagnostic mentioning " + worded + " and nothing joined", spellings),
                "prose using the same word is not a joint");
        assertFalse(offends("demo.Quote", spellings), "nor is a name with no spelling in it");
        // What this cannot see. A behavior's interface has no spelling of its own — it is the
        // behavior's name with its first letter capitalized — so a reader rebuilding one leaves a
        // recipe indistinguishable from any other join. That is what the next test is for.
        assertFalse(offends(JOINT + "." + JOINT, spellings),
                "a behavior interface rebuilt by hand is not a shape this can name");
    }

    /**
     * Nothing outside the ABI capitalizes a behavior's first letter.
     *
     * <p>The rule that turns a behavior into the class it is declared as adds no suffix, so the scan
     * above has nothing to look for and the reconstruction that started all of this — a private copy
     * of {@code Character.toUpperCase(name.charAt(0)) + name.substring(1)} — would go unseen. What it
     * does leave is a call, and in the compiler there is one caller of it. Tests uppercase for their
     * own reasons and are not held to this.
     */
    @Test
    void andNothingOutsideTheAbiCapitalizesABehaviorsFirstLetter() {
        List<String> callers = new ArrayList<>();
        for (Path module : Reactor.modules()) {
            if (!Reactor.hasMainSources(module)) {
                continue;   // a module with no main sources of its own
            }
            walkFor(Reactor.mainOutputOf(module), callers, TheAbiIsSpelledInOnePlaceTest::capitalizes);
        }
        assertEquals(List.of(), callers,
                "the rule a behavior's class name follows is stated somewhere else too");
    }

    /** Whether {@code model} calls {@code Character.toUpperCase}. */
    private static boolean capitalizes(java.lang.classfile.ClassModel model) {
        for (PoolEntry entry : model.constantPool()) {
            if (entry instanceof java.lang.classfile.constantpool.MethodRefEntry m
                    && m.owner().asInternalName().equals("java/lang/Character")
                    && m.name().stringValue().equals("toUpperCase")) {
                return true;
            }
        }
        return false;
    }

    private static void walkFor(CompiledClasses classes, List<String> found,
                                java.util.function.Predicate<java.lang.classfile.ClassModel> offending) {
        classes.all().forEach(model -> {
            String owner = model.thisClass().asInternalName().replace('/', '.');
            if (!owner.startsWith(ABI_PACKAGE) && offending.test(model)) {
                found.add(owner);
            }
        });
    }

    /**
     * What this ABI adds to a name, asked of the ABI. A derived class is its base plus a suffix, and a
     * module-level one is the module plus a suffix, so the difference is the spelling in both cases.
     */
    private static Set<String> abiSpellings() {
        TypeSymbol x = TypeSymbols.declared(new TypeKey("m", "X"));
        GeneratedClass.Value value = new GeneratedClass.Value(x);
        Set<String> out = new LinkedHashSet<>();
        out.add(beyond(new GeneratedClass.BehaviorImpl("m", "x"),
                new GeneratedClass.BehaviorInterface("m", "x")));
        out.add(beyond(new GeneratedClass.BehaviorResult("m", "x"),
                new GeneratedClass.BehaviorInterface("m", "x")));
        out.add(beyond(new GeneratedClass.BridgeCase("m", TypeSymbols.declared(new TypeKey("n", "X"))), value));
        out.add(beyond(new GeneratedClass.Encoder(value), value));
        for (DecoderKind kind : DecoderKind.values()) {
            out.add(beyond(new GeneratedClass.Decoder(value, kind), value));
        }
        out.add(beyond(new GeneratedClass.Ctfe(value), value));
        GeneratedClass.BehaviorInterface behavior = new GeneratedClass.BehaviorInterface("m", "x");
        out.add(beyond(new GeneratedClass.ExampleFake(behavior), behavior));
        out.add(afterTheModule(new GeneratedClass.ModuleDeclarations("m")));
        out.add(afterTheModule(new GeneratedClass.Helpers("m")));
        // A lambda is numbered, and the number is not part of the spelling.
        out.add(afterTheModule(new GeneratedClass.Lambda("m", 0)).replaceAll("\\d+$", ""));
        return out;
    }

    private static String beyond(GeneratedClass derived, GeneratedClass base) {
        String name = SoutherJvmAbi.nameOf(derived).binaryName();
        String of = SoutherJvmAbi.nameOf(base).binaryName();
        assertTrue(name.startsWith(of), name + " is not " + of + " with something after it");
        return name.substring(of.length());
    }

    /**
     * What a module-level class adds to its module — the dot included.
     *
     * <p>The dot is part of it because of how such a name is assembled when it is assembled by hand:
     * {@code module + ".$Fns"} puts the joint before the dot, not before the {@code $}. Stripping the
     * dot here left the scan looking for something no recipe holds, and two of these went unseen.
     */
    private static String afterTheModule(GeneratedClass generated) {
        return SoutherJvmAbi.nameOf(generated).binaryName().substring("m".length());
    }

    /**
     * Whether {@code constant} is a generated class's name being put together.
     *
     * <p>A spelling the ABI writes with a {@code $} is a word no sentence uses, so a recipe holding
     * one anywhere is a name being assembled — which catches a constant tail as well as a joint
     * right before it: {@code m + ".Foo$Impl"} leaves the joint two characters away. The two
     * spellings that are ordinary words are only read where a joint stands immediately before them,
     * so a diagnostic that says Case or Result is not mistaken for one.
     */
    private static boolean offends(String constant, Set<String> spellings) {
        for (String spelling : spellings) {
            if (constant.equals(spelling)) {
                return true;
            }
            boolean found = spelling.contains("$")
                    ? constant.contains(JOINT) && constant.contains(spelling)
                    : constant.contains(JOINT + spelling);
            if (found) {
                return true;
            }
        }
        return false;
    }

    private static void walk(CompiledClasses classes, List<String> violations,
                             Set<String> spellings) {
        classes.all().forEach(model -> read(model, violations, spellings));
    }

    private static void read(java.lang.classfile.ClassModel model, List<String> violations,
                             Set<String> spellings) {
        String owner = model.thisClass().asInternalName().replace('/', '.');
        if (owner.startsWith(ABI_PACKAGE) || owner.equals(THE_CONTRACT_TEST)
                || owner.startsWith(THE_CONTRACT_TEST + "$")) {
            return;
        }
        for (PoolEntry entry : model.constantPool()) {
            if (entry instanceof StringEntry s && offends(s.stringValue(), spellings)) {
                violations.add(owner + ": " + visible(s.stringValue()));
            }
        }
    }

    private static String visible(String constant) {
        return constant.replace(JOINT, "{}").replace(CONSTANT_JOINT, "{}");
    }

    /** The tree this module sits in. */
}
