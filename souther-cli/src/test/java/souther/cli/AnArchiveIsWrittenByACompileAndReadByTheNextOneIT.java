package souther.cli;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The class-data archive is the launcher's, not the compiler's, so nothing below the prepended
 * script can be asked whether it works. Two things about it are decided there and are worth
 * holding: that the archive is written by a run that loaded the compiler, and that a later run
 * loads the compiler out of it.
 *
 * <p>Which run writes it matters because an archive holds what the run that wrote it loaded, and
 * is rewritten only when it cannot be read. One written by a command that never reaches the
 * compiler would stand, and every compile after it would load its classes itself.
 */
class AnArchiveIsWrittenByACompileAndReadByTheNextOneIT {

    private static Path binary;

    @BeforeAll
    static void theBuiltBinary() {
        binary = Path.of(System.getProperty("souther.binary", "target/souther"));
        assertTrue(Files.isExecutable(binary),
                "the prepended launcher is built before this runs: " + binary.toAbsolutePath());
    }

    @Test
    void aCommandThatNeverReachesTheCompilerLeavesNoArchiveForOneThatDoes(@TempDir Path work)
            throws Exception {
        Path cache = work.resolve("cache");
        Path source = aSource(work);

        assertEquals(0, run(cache, Map.of(), "doc", "cli/commands"));
        assertTrue(archivesUnder(cache).isEmpty(),
                "`doc` loads nothing a compile would reuse, so it writes no archive");

        assertEquals(0, run(cache, Map.of(), "compile", "-d", work.resolve("out").toString(),
                source.toString()));
        assertEquals(1, archivesUnder(cache).size(),
                "a compile writes one, named for this version: " + archivesUnder(cache));

        Path loaded = work.resolve("class-load.log");
        assertEquals(0, run(cache, Map.of("JAVA_TOOL_OPTIONS", "-Xlog:class+load=info:file=" + loaded),
                "compile", "-d", work.resolve("out").toString(), source.toString()));
        assertTrue(Files.readAllLines(loaded).stream()
                        .anyMatch(l -> l.contains("souther.compiler.") && l.contains("shared objects file")),
                "and the compile after it takes the compiler's classes out of the archive");
    }

    /** A module small enough to say nothing but that it compiled. */
    private static Path aSource(Path work) throws IOException {
        Path source = work.resolve("trip.sou");
        Files.writeString(source, """
                module trip exposing ( Draft, name )

                data Draft = { who: String }

                behavior name : (d: Draft) -> String
                let name (d) = d.who
                """);
        return source;
    }

    /** The binary, run with {@code cache} as the cache directory the launcher is told to use. */
    private static int run(Path cache, Map<String, String> environment, String... args)
            throws Exception {
        ProcessBuilder builder = new ProcessBuilder(
                Stream.concat(Stream.of(binary.toString()), Stream.of(args)).toList());
        builder.environment().put("XDG_CACHE_HOME", cache.toString());
        builder.environment().putAll(environment);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String said = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int status = process.waitFor();
        assertFalse(said.contains("Error occurred during initialization of VM"), said);
        return status;
    }

    private static List<Path> archivesUnder(Path cache) throws IOException {
        if (!Files.isDirectory(cache)) {
            return List.of();
        }
        try (Stream<Path> tree = Files.walk(cache)) {
            return tree.filter(p -> p.getFileName().toString().endsWith(".jsa")).toList();
        }
    }
}
