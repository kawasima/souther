package net.unit8.souther.compiler;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Generated classes target Java 21 (spec 19.1).
 *
 * <p>Without this the version follows whatever JDK runs the compiler, which is silent and
 * environment-dependent: building on a newer JDK produces classes the target runtime cannot
 * load, and two developers emit mutually incompatible artifacts.
 */
class CompileClassVersionTest {

    private static final String MODULE = """
            module demo
            import String { length }

            data 会員ID = { value: String  invariant length(value) > 0 }
            data Contact = EmailContact | PhoneContact
            data EmailContact = { email: String }
            data PhoneContact = { phone: String }
            data Mark

            behavior 名を取る : (c: EmailContact) -> 会員ID constructs 会員ID

            let 名を取る (c) = 会員ID { value: c.email }
            """;

    /** major 65 is Java 21; the JDK running this test is newer, so a default would not match. */
    private static final int JAVA_21 = 65;

    private static int majorVersion(byte[] cls) {
        // u4 magic, u2 minor, u2 major
        return ((cls[6] & 0xff) << 8) | (cls[7] & 0xff);
    }

    @Test
    void everyGeneratedClassTargetsJava21() {
        Map<String, byte[]> classes = Compiler.compile(MODULE);
        for (Map.Entry<String, byte[]> e : classes.entrySet()) {
            assertEquals(JAVA_21, majorVersion(e.getValue()),
                    e.getKey() + " must target Java 21, not the JDK that built it");
        }
    }

    @Test
    void theCompilerEmitsDataDecodersAndBehaviorsAlike() {
        Map<String, byte[]> classes = Compiler.compile(MODULE);
        // guards the test above against silently covering an empty or tiny set
        assertEquals(true, classes.size() >= 10, "expected the whole module's output, got " + classes.keySet());
    }
}
