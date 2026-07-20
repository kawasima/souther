package net.unit8.souther.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code souther run} drives a compiled behavior end to end: the runner decodes the input through
 * the derived decoders, applies the behavior, and encodes the output back to JSON.
 */
class RunnerTest {

    @TempDir
    Path dir;

    private Path write(String fileName, String source) throws Exception {
        Path file = dir.resolve(fileName);
        Files.writeString(file, source);
        return file;
    }

    @Test
    void drivesAHeaderlessHelloWorldByName() throws Exception {
        Path file = write("hello.sou", """
                behavior greet : (name: String) -> String
                let greet (name) = "Hello, " ++ name
                """);
        assertEquals("\"Hello, world\"", Runner.run(file, "greet", "\"world\""));
    }

    @Test
    void selectsTheSoleRunnableBehaviorWhenNoneNamed() throws Exception {
        Path file = write("hello.sou", """
                behavior greet : (name: String) -> String
                let greet (name) = "Hi, " ++ name
                """);
        assertEquals("\"Hi, Souther\"", Runner.run(file, null, "\"Souther\""));
    }

    @Test
    void decodesSeveralInputsFromAJsonArrayPositionally() throws Exception {
        Path file = write("pair.sou", """
                data A = Int
                data B = Int
                data Pair = {
                    left: Int
                    , right: Int
                }
                behavior mkPair : (a: A, b: B) -> Pair constructs Pair
                let mkPair (a, b) = Pair { left = a.value, right = b.value }
                """);
        assertEquals("{\"left\":3,\"right\":7}", Runner.run(file, "mkPair", "[3, 7]"));
    }

    @Test
    void encodesTheRuntimeCaseOfASumOutput() throws Exception {
        Path file = write("classify.sou", """
                data Adult = { name: String }
                data Minor = { age: Int }
                behavior classify : (age: Int) -> Adult | Minor constructs Adult, Minor
                let classify (age) = {
                    require age >= 18 else Minor { age = age }
                    Adult { name = "adult" }
                }
                """);
        assertEquals("{\"name\":\"adult\"}", Runner.run(file, "classify", "20"));
        assertEquals("{\"age\":10}", Runner.run(file, "classify", "10"));
    }

    @Test
    void reportsADecodeFailureWithItsPathAndMessage() throws Exception {
        Path file = write("pair.sou", """
                data A = Int
                data B = Int
                data Pair = { left: Int, right: Int }
                behavior mkPair : (a: A, b: B) -> Pair constructs Pair
                let mkPair (a, b) = Pair { left = a.value, right = b.value }
                """);
        Runner.RunException e = assertThrows(Runner.RunException.class,
                () -> Runner.run(file, "mkPair", "[\"x\", 7]"));
        assertEquals(1, e.exitCode);
        assertTrue(e.getMessage().contains("could not be decoded"), e.getMessage());
    }

    @Test
    void refusesAnInjectedBehaviorThatHasNoImplementation() throws Exception {
        Path file = write("clock.sou", """
                behavior now : () -> String
                """);
        Runner.RunException e = assertThrows(Runner.RunException.class,
                () -> Runner.run(file, "now", null));
        assertTrue(e.getMessage().contains("no implementation"), e.getMessage());
    }

    @Test
    void refusesABehaviorThatNeedsInjectedDependencies() throws Exception {
        Path file = write("stamp.sou", """
                data Stamped = { at: String }
                behavior now : () -> String
                behavior stamp : (x: String) -> Stamped
                    requires now
                    constructs Stamped
                let stamp (x, now) = Stamped { at = now() }
                """);
        Runner.RunException e = assertThrows(Runner.RunException.class,
                () -> Runner.run(file, "stamp", "\"x\""));
        assertTrue(e.getMessage().contains("requires injected dependencies"), e.getMessage());
    }

    @Test
    void refusesAPipelineBehavior() throws Exception {
        Path file = write("flow.sou", """
                data In = { v: Int }
                data Out = { v: Int }
                behavior stage : (i: In) -> Out constructs Out
                let stage (i) = Out { v = i.v }
                behavior flow = stage
                """);
        Runner.RunException e = assertThrows(Runner.RunException.class,
                () -> Runner.run(file, "flow", "{\"v\": 1}"));
        assertTrue(e.getMessage().contains("pipeline"), e.getMessage());
    }

    @Test
    void refusesToPickAmongSeveralRunnableBehaviors() throws Exception {
        Path file = write("two.sou", """
                behavior a : (s: String) -> String
                let a (s) = s
                behavior b : (s: String) -> String
                let b (s) = s
                """);
        Runner.RunException e = assertThrows(Runner.RunException.class,
                () -> Runner.run(file, null, "\"x\""));
        assertEquals(2, e.exitCode);
        assertTrue(e.getMessage().contains("--behavior"), e.getMessage());
    }

    @Test
    void namesAHeaderlessModuleAfterTheFile() throws Exception {
        Path file = write("greeter.sou", """
                behavior id : (s: String) -> String
                let id (s) = s
                """);
        // The generated class lands in the file-stem package; driving it confirms the name resolves.
        assertEquals("\"ok\"", Runner.run(file, "id", "\"ok\""));
    }
}
