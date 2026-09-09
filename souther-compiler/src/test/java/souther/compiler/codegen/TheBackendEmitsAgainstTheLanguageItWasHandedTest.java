package souther.compiler.codegen;

import org.junit.jupiter.api.Test;
import souther.compiler.WhatWasCompiled;

import java.lang.classfile.ClassModel;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nothing in the JVM backend reaches the standard library.
 *
 * <p>What a kernel was declared to take is a decision of the language, made once and carried: a
 * checked program hands it over, and this backend is handed the same value when it is built. A
 * backend that could reach the library instead would be one that could put any question to it, and
 * the answer it emitted from would be one nobody handed it — which is the arrangement where two
 * readings of one language drift apart, and the arrangement this exists to keep closed.
 *
 * <p>Not about a dependency between modules; they are one artifact. It is about which value decides
 * what a call is emitted at, and it is worth holding mechanically because the route back is one
 * method call long ({@code symbols.library()}) and reads like nothing at the place it is written.
 *
 * <p>Read off the compiled classes rather than the sources. A call to a method answering a
 * {@code Stdlib} leaves the type in the calling class's constant pool whatever the source says, so a
 * reference reached through a local variable or a chained call is here too, and a mention of the
 * word in a comment is not.
 */
class TheBackendEmitsAgainstTheLanguageItWasHandedTest {

    private static final String THE_BACKEND = "souther.compiler.codegen";

    /** What the backend may not name, as the constant pool spells it. */
    private static final String THE_LIBRARY = "souther/compiler/stdlib/";

    @Test
    void nothingInTheBackendNamesTheStandardLibrary() {
        List<String> reaching = new ArrayList<>();
        List<ClassModel> classes = WhatWasCompiled.compiled().inPackage(THE_BACKEND);

        // A walk that found nothing because it read next to nothing answers the same as one that
        // read everything and found nothing.
        assertTrue(classes.size() > 10,
                () -> "read only " + classes.size() + " compiled classes of " + THE_BACKEND);

        for (ClassModel each : classes) {
            for (PoolEntry entry : each.constantPool()) {
                if (entry instanceof Utf8Entry utf8 && utf8.stringValue().contains(THE_LIBRARY)) {
                    reaching.add(each.thisClass().asInternalName() + " names " + utf8.stringValue());
                    break;
                }
            }
        }

        assertEquals(List.of(), reaching,
                "the backend emits against the kernel declarations it is handed; these reach the"
                        + " library for an answer of their own");
    }
}
