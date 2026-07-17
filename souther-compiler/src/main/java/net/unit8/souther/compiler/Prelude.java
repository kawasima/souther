package net.unit8.souther.compiler;

import net.unit8.souther.compiler.ast.Ast;
import net.unit8.souther.compiler.check.Type;
import net.unit8.souther.compiler.check.TypeChecker;
import net.unit8.souther.compiler.syntax.Parser;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The standard library the compiler ships in the reserved {@code souther} namespace (ADR-0028,
 * spec §reserved-namespace). Its modules are packaged as resources and parsed once, and split into:
 *
 * <ul>
 *   <li>{@linkplain #helpers() helpers} — ordinary generic functions (e.g. {@code not}), auto-imported
 *       and expanded inline at each call site (see {@code check.HelperInliner});</li>
 *   <li>{@linkplain #intrinsics() intrinsics} — functions whose body is a named primitive
 *       ({@code = intrinsic "key"}). These behave like built-ins: the checker verifies a call against
 *       the declared signature and the backend emits the primitive for the key. They are neither
 *       inlined nor lowered.</li>
 * </ul>
 */
public final class Prelude {

    /** The bundled prelude sources, in load order. */
    private static final List<String> RESOURCES =
            List.of("/souther/bool.sou", "/souther/string.sou", "/souther/map.sou");

    /** A shipped primitive: its declared signature and the backend key naming its bytecode. */
    public record IntrinsicSig(String name, List<Type> params, Type result, String key) {
    }

    private static final List<Ast.FnDef> HELPERS = new ArrayList<>();
    private static final Map<String, IntrinsicSig> INTRINSICS = new LinkedHashMap<>();

    static {
        load();
    }

    private Prelude() {
    }

    /** The auto-imported helper functions of the prelude (inlined at call sites). */
    public static List<Ast.FnDef> helpers() {
        return HELPERS;
    }

    /** The shipped primitives, keyed by their (bare, auto-imported) name. */
    public static Map<String, IntrinsicSig> intrinsics() {
        return INTRINSICS;
    }

    private static void load() {
        Map<String, Ast.Def> noSymbols = Map.of();   // prelude signatures use only primitives / 'a
        for (String resource : RESOURCES) {
            for (Ast.FnDef fn : Parser.parse(read(resource)).fns()) {
                if (fn.isIntrinsic()) {
                    List<Type> params = new ArrayList<>();
                    for (Ast.FnParam p : fn.params()) {
                        params.add(TypeChecker.resolveParamType(p.type(), noSymbols));
                    }
                    Type result = TypeChecker.successType(fn.declaredReturn(), noSymbols);
                    INTRINSICS.put(fn.name(), new IntrinsicSig(fn.name(), params, result, fn.intrinsicKey()));
                } else {
                    HELPERS.add(fn);
                }
            }
        }
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
