package net.unit8.souther.compiler;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The CLI compiles a multi-file project, resolving imports across the given source files. */
class MainTest {

    @Test
    void compilesAMultiModuleProjectResolvingImports() throws Exception {
        Path dir = Files.createTempDirectory("souther-cli");
        Path a = dir.resolve("a.sou");
        Path b = dir.resolve("b.sou");
        Files.writeString(a, """
                module a exposing ( 従業員ID )

                import String ( length )

                data 従業員ID = String
                    invariant length(value) > 0
                """);
        Files.writeString(b, """
                module b

                import a ( 従業員ID )

                data Trip = { who: 従業員ID }
                """);
        Path out = Files.createTempDirectory("souther-cli-out");

        Main.run(List.of(a, b), out);

        assertTrue(Files.exists(out.resolve("a/従業員ID.class")), "module a's class is written");
        assertTrue(Files.exists(out.resolve("b/Trip.class")), "module b's class, which imports a, is written");
    }
}
