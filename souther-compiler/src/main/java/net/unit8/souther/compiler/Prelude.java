package net.unit8.souther.compiler;

import net.unit8.souther.compiler.ast.Ast;
import net.unit8.souther.compiler.syntax.Parser;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The standard library the compiler ships in the reserved {@code souther} namespace (ADR-0028,
 * spec §reserved-namespace). Its modules are packaged as resources and parsed once; their public
 * helpers are auto-imported into every user module (see {@link
 * net.unit8.souther.compiler.check.HelperInliner}). A non-recursive helper such as {@code not}
 * needs no class of its own — it is expanded inline at each call site.
 */
public final class Prelude {

    /** The bundled prelude sources, in load order. */
    private static final List<String> RESOURCES = List.of("/souther/bool.sou");

    private static final List<Ast.FnDef> HELPERS = loadHelpers();

    private Prelude() {
    }

    /** The auto-imported helper functions of the prelude. */
    public static List<Ast.FnDef> helpers() {
        return HELPERS;
    }

    private static List<Ast.FnDef> loadHelpers() {
        List<Ast.FnDef> out = new ArrayList<>();
        for (String resource : RESOURCES) {
            // A prelude module declares only auto-imported helpers (no behaviors, no data yet).
            out.addAll(Parser.parse(read(resource)).fns());
        }
        return List.copyOf(out);
    }

    private static String read(String resource) {
        try (InputStream in = Prelude.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing bundled prelude resource " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read prelude resource " + resource, e);
        }
    }
}
