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
import java.util.Set;

/**
 * The standard library the compiler ships in the reserved {@code souther} namespace (ADR-0028,
 * spec §reserved-namespace). Its modules are packaged as resources and parsed once, and split into:
 *
 * <ul>
 *   <li>{@linkplain #helpers() helpers} — ordinary generic functions (e.g. {@code not}), expanded
 *       inline at each call site (see {@code check.HelperInliner});</li>
 *   <li>{@linkplain #intrinsics() intrinsics} — functions whose body is a named primitive
 *       ({@code = intrinsic "key"}). These behave like built-ins: the checker verifies a call against
 *       the declared signature and the backend emits the primitive for the key. They are neither
 *       inlined nor lowered.</li>
 * </ul>
 *
 * <p>Everything is reached through a module qualifier — {@code List.map}, {@code String.trim} — since
 * the standard library carries no bare names (spec §stdlib). Helpers and intrinsics are therefore
 * keyed by their qualified name, e.g. {@code "List.map"} / {@code "String.trim"}.
 */
public final class Prelude {

    /** The bundled prelude sources, in load order. */
    private static final List<String> RESOURCES =
            List.of("/souther/bool.sou", "/souther/string.sou", "/souther/map.sou", "/souther/list.sou");

    /** Reserved module name → short qualifier (spec §stdlib). {@code souther.list} → {@code List}. */
    private static final Map<String, String> MODULE_TO_ALIAS = Map.of(
            "souther.list", "List",
            "souther.string", "String",
            "souther.map", "Map",
            "souther.bool", "Bool");

    /** The checker built-ins, by qualified name — the primitives that have no prelude source because
     *  they are overloaded (length/get) or need a bespoke loop/codegen (fold), plus arithmetic. They
     *  are reached qualified like everything else (spec §stdlib). */
    private static final Set<String> BUILTINS = Set.of(
            "List.fold", "List.length", "List.get",
            "String.length",
            "Map.get",
            "Int.compare", "Int.remainder", "Int.divide", "Int.add", "Int.subtract", "Int.multiply",
            "Decimal.add", "Decimal.subtract", "Decimal.multiply", "Decimal.divide", "Decimal.compare");

    /** Every qualifier a call may carry: the four prelude modules plus the arithmetic built-in
     *  namespaces {@code Int}/{@code Decimal} (spec §stdlib). */
    private static final Set<String> QUALIFIERS =
            Set.of("List", "String", "Map", "Bool", "Int", "Decimal");

    /** A shipped primitive: its declared signature and the backend key naming its bytecode. */
    public record IntrinsicSig(String name, List<Type> params, Type result, String key) {
    }

    /** Helpers keyed by qualified name, e.g. {@code "List.map"}. */
    private static final Map<String, Ast.FnDef> HELPERS = new LinkedHashMap<>();
    /** Intrinsics keyed by qualified name, e.g. {@code "String.trim"}. */
    private static final Map<String, IntrinsicSig> INTRINSICS = new LinkedHashMap<>();
    /** Bare stdlib name → a qualified suggestion, so a bare call gets a "did you mean" hint. The
     *  checker built-ins (which are not in the prelude sources) are listed here explicitly. */
    private static final Map<String, String> BARE_TO_QUALIFIED = new LinkedHashMap<>();

    static {
        load();
    }

    private Prelude() {
    }

    /** Whether {@code qualifier} names a standard-library namespace a call/import may use:
     *  {@code List}/{@code String}/{@code Map}/{@code Bool}/{@code Int}/{@code Decimal} (spec §stdlib). */
    public static boolean isQualifier(String qualifier) {
        return QUALIFIERS.contains(qualifier);
    }

    /** Whether {@code qualifiedName} (e.g. {@code "List.map"}) is a standard-library function —
     *  a prelude helper, a prelude intrinsic, or a checker built-in. */
    public static boolean hasQualified(String qualifiedName) {
        return HELPERS.containsKey(qualifiedName)
                || INTRINSICS.containsKey(qualifiedName)
                || BUILTINS.contains(qualifiedName);
    }

    /** The helper functions of the prelude (inlined at call sites), keyed by qualified name. */
    public static Map<String, Ast.FnDef> helpers() {
        return HELPERS;
    }

    /** The shipped primitives, keyed by their qualified name ({@code "String.trim"}). */
    public static Map<String, IntrinsicSig> intrinsics() {
        return INTRINSICS;
    }

    /** A qualified suggestion for a bare standard-library name ({@code "map"} → {@code "List.map"}),
     *  or {@code null} if the name is not a standard-library function. */
    public static String qualifiedFor(String bareName) {
        return BARE_TO_QUALIFIED.get(bareName);
    }

    private static void load() {
        Map<String, Ast.Def> noSymbols = Map.of();   // prelude signatures use only primitives / 'a
        for (String resource : RESOURCES) {
            Ast.Module module = Parser.parse(read(resource));
            String alias = MODULE_TO_ALIAS.get(module.name());
            if (alias == null) {
                throw new IllegalStateException("prelude resource " + resource
                        + " declares unknown module " + module.name());
            }
            for (Ast.FnDef fn : module.fns()) {
                String qualified = alias + "." + fn.name();
                if (fn.isIntrinsic()) {
                    List<Type> params = new ArrayList<>();
                    for (Ast.FnParam p : fn.params()) {
                        params.add(TypeChecker.resolveParamType(p.type(), noSymbols));
                    }
                    Type result = TypeChecker.successType(fn.declaredReturn(), noSymbols);
                    INTRINSICS.put(qualified, new IntrinsicSig(fn.name(), params, result, fn.intrinsicKey()));
                } else {
                    HELPERS.put(qualified, fn);
                }
                BARE_TO_QUALIFIED.putIfAbsent(fn.name(), qualified);
            }
        }
        // checker built-ins have no prelude source; list their bare→qualified hints explicitly.
        BARE_TO_QUALIFIED.put("length", "List.length` or `String.length");
        BARE_TO_QUALIFIED.put("get", "List.get` or `Map.get");
        BARE_TO_QUALIFIED.put("fold", "List.fold");
        BARE_TO_QUALIFIED.put("compare", "Int.compare` or `Decimal.compare");
        BARE_TO_QUALIFIED.put("remainder", "Int.remainder");
        BARE_TO_QUALIFIED.put("add", "Int.add` or `Decimal.add");
        BARE_TO_QUALIFIED.put("subtract", "Int.subtract` or `Decimal.subtract");
        BARE_TO_QUALIFIED.put("multiply", "Int.multiply` or `Decimal.multiply");
        BARE_TO_QUALIFIED.put("divide", "Int.divide` or `Decimal.divide");
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
