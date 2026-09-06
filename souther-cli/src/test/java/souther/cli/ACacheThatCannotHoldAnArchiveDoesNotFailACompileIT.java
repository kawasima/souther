package souther.cli;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Writing a class-data archive is fatal to the run that writes it: a JVM told to write one where
 * it cannot ends in an initialization error, after the work it was asked for is done and its
 * output written. A machine whose cache directory cannot be made is an ordinary one — a read-only
 * home, a container with nowhere to write — and a compile there has to be a compile.
 */
class ACacheThatCannotHoldAnArchiveDoesNotFailACompileIT {

    private static Path binary;

    @BeforeAll
    static void theBuiltBinary() {
        binary = Path.of(System.getProperty("souther.binary", "target/souther"));
        assertTrue(Files.isExecutable(binary),
                "the prepended launcher is built before this runs: " + binary.toAbsolutePath());
    }

    @Test
    void aCompileWhoseCacheDirectoryCannotBeMadeStillWritesItsClasses(@TempDir Path work)
            throws Exception {
        // A file where a directory is wanted: `mkdir -p` fails against it whoever is running, which
        // a directory with its permissions taken away does not do for root.
        Path cache = work.resolve("occupied");
        Files.writeString(cache, "");

        Path source = work.resolve("trip.sou");
        Files.writeString(source, """
                module trip exposing ( Draft, name )

                data Draft = { who: String }

                behavior name : (d: Draft) -> String
                let name (d) = d.who
                """);
        Path out = work.resolve("out");

        ProcessBuilder builder = new ProcessBuilder(binary.toString(),
                "compile", "-d", out.toString(), source.toString());
        builder.environment().put("XDG_CACHE_HOME", cache.toString());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String said = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertEquals(0, process.waitFor(), "the compile is what the run is judged by: " + said);
        assertTrue(Files.isRegularFile(out.resolve("trip").resolve("Draft.class")),
                "and it wrote what it was asked for: " + said);
    }
}
