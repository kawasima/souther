package net.unit8.souther.compiler;

import net.unit8.souther.compiler.ast.Ast;
import net.unit8.souther.compiler.check.TypeChecker;
import net.unit8.souther.compiler.codegen.Backend;
import net.unit8.souther.compiler.derive.Deriver;
import net.unit8.souther.compiler.syntax.Parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The compiler pipeline facade: source → parse → derive → type check → ClassFile bytecode
 * (spec section 20). {@link #compile} handles a single self-contained module;
 * {@link #compileModules} links several modules through explicit imports (spec section 4).
 */
public final class Compiler {

    private Compiler() {}

    /** Compiles a single self-contained module (no imports) into binary class name → bytecode. */
    public static Map<String, byte[]> compile(String source) {
        Ast.Module module = Deriver.derive(Parser.parse(source));
        TypeChecker.check(module);
        return Backend.generate(module);
    }

    /** Compiles a set of modules together, resolving explicit imports and rejecting cycles. */
    public static Map<String, byte[]> compileModules(List<String> sources) {
        List<Ast.Module> parsed = new ArrayList<>();
        for (String s : sources) {
            parsed.add(Parser.parse(s));
        }
        Map<String, Ast.Module> byName = new LinkedHashMap<>();
        for (Ast.Module m : parsed) {
            if (byName.put(m.name(), m) != null) {
                throw new CompileException(m.pos(), "duplicate module `" + m.name() + "`");
            }
        }
        detectCycles(parsed, byName);

        // pass 1: derive each module's codecs, resolving imported types against the original defs
        Map<String, Ast.Module> derived = new LinkedHashMap<>();
        for (Ast.Module m : parsed) {
            derived.put(m.name(), Deriver.derive(m, visibleDefs(m, byName)));
        }
        // pass 2: type-check and generate against the derived (codec-bearing) defs
        Map<String, byte[]> out = new LinkedHashMap<>();
        for (Ast.Module original : parsed) {
            Ast.Module m = derived.get(original.name());
            Map<String, Ast.Def> symbols = visibleDefs(m, derived);
            Map<String, TypeChecker.Sig> importedSigs = importedBehaviorSigs(m, derived);
            Set<String> importedInjected = importedInjectedBehaviors(m, derived);
            TypeChecker.check(m, symbols, importedSigs);
            out.putAll(Backend.generate(m, symbols, importedPackages(m), importedSigs, importedInjected));
        }
        return out;
    }

    /** Signatures of the behaviors {@code m} imports from other modules (spec 4, 14), so a
     * composition here can name one as a stage. The declaring module's own signatures are computed
     * against its visible defs; a behavior it in turn imports is out of scope for now. */
    private static Map<String, TypeChecker.Sig> importedBehaviorSigs(
            Ast.Module m, Map<String, Ast.Module> registry) {
        Map<String, TypeChecker.Sig> result = new HashMap<>();
        for (Ast.Import imp : m.imports()) {
            Ast.Module src = registry.get(imp.module());
            if (src == null) {
                continue; // an unknown module is reported by visibleDefs
            }
            Set<String> behaviors = behaviorNames(src);
            Map<String, TypeChecker.Sig> srcSigs = null;
            for (String name : imp.names()) {
                if (!behaviors.contains(name)) {
                    continue;
                }
                if (srcSigs == null) {
                    srcSigs = TypeChecker.signatures(src, visibleDefs(src, registry));
                }
                result.put(name, srcSigs.get(name));
            }
        }
        return result;
    }

    private static Set<String> behaviorNames(Ast.Module m) {
        Set<String> names = new HashSet<>();
        for (Ast.BehaviorDef b : m.behaviors()) {
            names.add(b.name());
        }
        return names;
    }

    /** The behaviors {@code m} imports that are injection targets in their declaring module (a
     * SpecBehavior with no fn — spec 13.2). A composition here that names one as a stage inherits
     * it as an inferred requirement, so the consuming module injects and binds it (spec 14.3). */
    private static Set<String> importedInjectedBehaviors(Ast.Module m, Map<String, Ast.Module> registry) {
        Set<String> result = new HashSet<>();
        for (Ast.Import imp : m.imports()) {
            Ast.Module src = registry.get(imp.module());
            if (src == null) {
                continue;
            }
            Set<String> injected = injectedNames(src);
            for (String name : imp.names()) {
                if (injected.contains(name)) {
                    result.add(name);
                }
            }
        }
        return result;
    }

    /** The injection-target behaviors of a module: a SpecBehavior with no matching fn (spec 13.2). */
    private static Set<String> injectedNames(Ast.Module m) {
        Set<String> fnNames = new HashSet<>();
        for (Ast.FnDef f : m.fns()) {
            fnNames.add(f.name());
        }
        Set<String> injected = new HashSet<>();
        for (Ast.BehaviorDef b : m.behaviors()) {
            if (b instanceof Ast.SpecBehavior && !fnNames.contains(b.name())) {
                injected.add(b.name());
            }
        }
        return injected;
    }

    /** Own definitions plus imported ones, validated against the source module's {@code exposing}. */
    private static Map<String, Ast.Def> visibleDefs(Ast.Module m, Map<String, Ast.Module> registry) {
        Map<String, Ast.Def> defs = new HashMap<>(TypeChecker.symbols(m));
        for (Ast.Import imp : m.imports()) {
            Ast.Module src = registry.get(imp.module());
            if (src == null) {
                throw new CompileException(imp.pos(), "unknown module `" + imp.module() + "`");
            }
            Map<String, Ast.Def> srcDefs = TypeChecker.symbols(src);
            Set<String> exposed = exposedBaseNames(src);
            for (String name : imp.names()) {
                if (!exposed.contains(name)) {
                    throw new CompileException(imp.pos(),
                            "`" + name + "` is not exposed by `" + imp.module() + "`");
                }
                Ast.Def d = srcDefs.get(name);
                if (d == null) {
                    // a behavior import is resolved separately (importedBehaviorSigs); it is not a
                    // data Def, so it does not go into the symbols map.
                    if (behaviorNames(src).contains(name)) {
                        continue;
                    }
                    throw new CompileException(imp.pos(),
                            "`" + name + "` is not defined in `" + imp.module() + "`");
                }
                if (defs.put(name, d) != null) {
                    throw new CompileException(imp.pos(),
                            "imported `" + name + "` conflicts with a local definition");
                }
            }
        }
        return defs;
    }

    /** Maps each imported type name to its declaring module, for cross-package class references. */
    private static Map<String, String> importedPackages(Ast.Module m) {
        Map<String, String> pkg = new HashMap<>();
        for (Ast.Import imp : m.imports()) {
            for (String name : imp.names()) {
                pkg.put(name, imp.module());
            }
        }
        return pkg;
    }

    /** The base type names a module exposes (dropping any {@code .decoder}/{@code .encoder} member). */
    private static Set<String> exposedBaseNames(Ast.Module m) {
        Set<String> names = new HashSet<>();
        for (String e : m.exposing()) {
            int dot = e.indexOf('.');
            names.add(dot < 0 ? e : e.substring(0, dot));
        }
        return names;
    }

    private static void detectCycles(List<Ast.Module> modules, Map<String, Ast.Module> byName) {
        Set<String> done = new HashSet<>();
        Set<String> stack = new HashSet<>();
        for (Ast.Module m : modules) {
            visit(m.name(), byName, done, stack);
        }
    }

    private static void visit(String name, Map<String, Ast.Module> byName,
                              Set<String> done, Set<String> stack) {
        if (done.contains(name)) {
            return;
        }
        stack.add(name);
        Ast.Module m = byName.get(name);
        if (m != null) {
            for (Ast.Import imp : m.imports()) {
                if (stack.contains(imp.module())) {
                    throw new CompileException(imp.pos(), "E1501", "Cyclic module dependency detected.");
                }
                if (byName.containsKey(imp.module())) {
                    visit(imp.module(), byName, done, stack);
                }
            }
        }
        stack.remove(name);
        done.add(name);
    }

    /** Compiles source and writes each generated class under {@code outDir}. */
    public static void compileToDir(String source, Path outDir) throws IOException {
        for (Map.Entry<String, byte[]> entry : compile(source).entrySet()) {
            Path file = outDir.resolve(entry.getKey().replace('.', '/') + ".class");
            Files.createDirectories(file.getParent());
            Files.write(file, entry.getValue());
        }
    }
}
