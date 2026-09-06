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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The class-data archive is the launcher's, not the compiler's, so nothing below the prepended
 * script can be asked whether it works. Two things about it are decided there and are worth
 * holding: that the archive is written by a run that loaded the compiler, and that a later run
 * loads the compiler out of it.
 *
 * <p>Which run writes it matters because an archive holds what the run that wrote it loaded, and
 * nothing later extends it to hold more. One written by a command that never reaches the compiler
 * would stand, and every compile after it would load its classes itself.
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

        run(cache, Map.of(), "doc", "cli/commands");
        assertTrue(archivesUnder(cache).isEmpty(),
                "`doc` loads nothing a compile would reuse, so it writes no archive");

        // The command that compiles, on a line that does not: usage is answered from the line and
        // returns, so this run reaches the compiler no further than `doc` did, and an archive of
        // what it loaded would be the one every compile after it read.
        run(cache, Map.of(), "compile", "--help");
        assertTrue(archivesUnder(cache).isEmpty(),
                "a line asking what `compile` takes writes no archive either");

        run(cache, Map.of(), "compile", "-d", work.resolve("out").toString(), source.toString());
        // The name, and not only that there is one: a version reaches the script by being filtered
        // into it, and a filtering that stopped happening would leave every version one archive to
        // fight over while every run here still passed.
        assertEquals(List.of(System.getProperty("souther.version") + ".jsa"),
                archivesUnder(cache).stream().map(p -> p.getFileName().toString()).toList(),
                "a compile writes one, and it is this version's");

        Path loaded = work.resolve("class-load.log");
        run(cache, Map.of("JAVA_TOOL_OPTIONS", "-Xlog:class+load=info:file=" + loaded),
                "compile", "-d", work.resolve("out").toString(), source.toString());
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

    /**
     * The binary, run with {@code cache} as the cache directory the launcher is told to use, held
     * to answering the command it was given.
     */
    private static void run(Path cache, Map<String, String> environment, String... args)
            throws Exception {
        ProcessBuilder builder = new ProcessBuilder(
                Stream.concat(Stream.of(binary.toString()), Stream.of(args)).toList());
        builder.environment().put("XDG_CACHE_HOME", cache.toString());
        builder.environment().putAll(environment);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String said = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(),
                "`souther " + String.join(" ", args) + "` answered instead: " + said);
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
