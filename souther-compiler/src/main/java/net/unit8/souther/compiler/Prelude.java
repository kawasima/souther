package net.unit8.souther.compiler;

import net.unit8.souther.compiler.ast.Ast;
import net.unit8.souther.compiler.check.Type;
import net.unit8.souther.compiler.check.TypeChecker;
import net.unit8.souther.compiler.frontend.CstFrontend;

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
            List.of("/souther/bool.sou", "/souther/string.sou", "/souther/map.sou", "/souther/list.sou",
                    "/souther/set.sou", "/souther/date.sou", "/souther/datetime.sou",
                    "/souther/int.sou", "/souther/decimal.sou", "/souther/option.sou");

    /** Reserved module name → short qualifier (spec §stdlib). {@code souther.list} → {@code List}. */
    private static final Map<String, String> MODULE_TO_ALIAS = Map.ofEntries(
            Map.entry("souther.list", "List"),
            Map.entry("souther.string", "String"),
            Map.entry("souther.map", "Map"),
            Map.entry("souther.set", "Set"),
            Map.entry("souther.bool", "Bool"),
            Map.entry("souther.date", "Date"),
            Map.entry("souther.datetime", "DateTime"),
            Map.entry("souther.int", "Int"),
            Map.entry("souther.decimal", "Decimal"),
            Map.entry("souther.option", "Option"));

    /** The checker built-ins, by qualified name — the primitives that have no prelude source because
     *  they are overloaded (length/get) or need bespoke codegen (get/find/sortBy). The Int/Decimal
     *  {@code divide}/{@code remainder} stay here too: they return a primitive-headed union
     *  ({@code Int | DivisionByZero}) a core declaration cannot yet express, so their branch codegen
     *  stays in the compiler. The rest of Int/Decimal (add/subtract/multiply/compare/modBy) is now
     *  declared in {@code souther.int}/{@code souther.decimal}. {@code fold} is not among them: it is
     *  an ordinary recursive helper in {@code souther.list} ({@code foldFrom}) that takes its step as
     *  a closure. They are reached qualified like everything else (spec §stdlib). */
    private static final Set<String> BUILTINS = Set.of(
            "List.length", "List.get", "List.max", "List.min", "List.find", "List.sortBy",
            "String.length", "String.toInt",
            "Map.get", "Map.empty", "Set.empty",
            "Option.map",
            "Int.remainder", "Int.divide", "Decimal.divide");

    /** Every qualifier a call may carry: the four prelude modules plus the arithmetic built-in
     *  namespaces {@code Int}/{@code Decimal} (spec §stdlib). */
    private static final Set<String> QUALIFIERS =
            Set.of("List", "String", "Map", "Set", "Bool", "Int", "Decimal", "Date", "DateTime", "Option");

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

    /** Every standard-library qualifier ({@code List} / {@code Map} / … / {@code Option}). The
     *  syntax highlighter derives its qualifier list from this so the two never drift apart. */
    public static Set<String> qualifiers() {
        return QUALIFIERS;
    }

    /** Names that are sugar for another standard-library call, recognised as library functions but
     *  rewritten before inlining: {@code List.fold(step, seed, xs)} is {@code List.foldFrom(step, seed,
     *  xs, 0)} (the walk from the head). */
    private static final Set<String> SUGARED = Set.of("List.fold");

    /** Whether {@code qualifiedName} (e.g. {@code "List.map"}) is a standard-library function —
     *  a prelude helper, a prelude intrinsic, a checker built-in, or a sugar for one. */
    public static boolean hasQualified(String qualifiedName) {
        return HELPERS.containsKey(qualifiedName)
                || INTRINSICS.containsKey(qualifiedName)
                || BUILTINS.contains(qualifiedName)
                || SUGARED.contains(qualifiedName);
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
            Ast.Module module = CstFrontend.parse(read(resource));
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
        // Explicit bare→qualified hints: the checker built-ins that have no prelude source, plus the
        // dual-namespace arithmetic names whose auto-derived single hint (Int, loaded first) would hide
        // the Decimal alternative.
        BARE_TO_QUALIFIED.put("length", "List.length` or `String.length");
        BARE_TO_QUALIFIED.put("toInt", "String.toInt");
        BARE_TO_QUALIFIED.put("get", "List.get` or `Map.get");
        BARE_TO_QUALIFIED.put("map", "List.map`, `Map.map`, or `Option.map");
        BARE_TO_QUALIFIED.put("empty", "Map.empty` or `Set.empty");
        BARE_TO_QUALIFIED.put("fold", "List.fold");
        BARE_TO_QUALIFIED.put("max", "List.max");
        BARE_TO_QUALIFIED.put("min", "List.min");
        BARE_TO_QUALIFIED.put("find", "List.find");
        BARE_TO_QUALIFIED.put("sortBy", "List.sortBy");
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
