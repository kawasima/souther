package net.unit8.souther.compiler;

import net.unit8.souther.compiler.ast.Ast;
import net.unit8.souther.compiler.check.Type;
import net.unit8.souther.compiler.check.TypeChecker;
import net.unit8.souther.compiler.syntax.Parser;

import net.unit8.raoh.Err;
import net.unit8.raoh.Issue;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Result;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.decode.ObjectDecoders;
import net.unit8.raoh.encode.Encoder;
import net.unit8.raoh.encode.ObjectEncoders;

import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Drives a compiled behavior from the command line: {@code souther run <file.sou> [--behavior
 * <name>] [--input <json>]}. The runner is itself the Java boundary (spec section 13) — it decodes
 * the JSON input into the behavior's parameter types through their derived decoders, applies the
 * behavior, and encodes the returned domain value back to JSON through its derived encoder. Souther
 * keeps no I/O of its own; a header-less {@code .sou} is named after the file (see {@link Parser}).
 *
 * <p>Only a self-contained module (no imports) and a simple behavior with a {@code let} and no
 * {@code requires} can be driven. An injected behavior (no implementation) or one that needs
 * injected dependencies has no in-language implementation to run, and a {@code >->} pipeline is not
 * yet supported; each is refused with a reason.
 */
public final class Runner {

    private Runner() {}

    /** A user-facing failure of {@code souther run}, carrying the process exit code to use. */
    static final class RunException extends RuntimeException {
        final int exitCode;

        RunException(String message, int exitCode) {
            super(message);
            this.exitCode = exitCode;
        }

        RunException(String message) {
            this(message, 1);
        }
    }

    /** Parses the {@code run} subcommand's arguments (everything after {@code run}) and runs it. */
    static String runCli(String[] args) {
        Path file = null;
        String behaviorName = null;
        String inputJson = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--behavior" -> behaviorName = value(args, ++i, "--behavior");
                case "--input" -> inputJson = value(args, ++i, "--input");
                default -> {
                    if (args[i].startsWith("--")) {
                        throw usage("unknown option `" + args[i] + "`");
                    }
                    if (file != null) {
                        throw usage("run takes a single .sou file");
                    }
                    file = Path.of(args[i]);
                }
            }
        }
        if (file == null) {
            throw usage("no source file given");
        }
        return run(file, behaviorName, inputJson);
    }

    private static String value(String[] args, int i, String option) {
        if (i >= args.length) {
            throw usage("`" + option + "` needs a value");
        }
        return args[i];
    }

    private static RunException usage(String problem) {
        return new RunException(problem
                + "\nusage: souther run <file.sou> [--behavior <name>] [--input <json>]", 2);
    }

    /** Compiles {@code file}, drives {@code behaviorName} with {@code inputJson}, and returns the
     * encoded output as JSON text. {@code behaviorName} may be null when the module holds exactly
     * one runnable behavior; {@code inputJson} may be null for a zero-argument behavior. */
    public static String run(Path file, String behaviorName, String inputJson) {
        String source = read(file);
        String moduleName = moduleName(file);

        Map<String, byte[]> classes = Compiler.compile(source, moduleName);
        Ast.Module module = Parser.parse(source, moduleName);
        Map<String, Ast.Def> symbols = TypeChecker.symbols(module);
        Map<String, TypeChecker.Sig> sigs = TypeChecker.signatures(module, symbols);

        Ast.SpecBehavior spec = resolveBehavior(module, behaviorName);
        TypeChecker.Sig sig = sigs.get(spec.name());
        List<Type> ins = sig.ins();

        Object[] rawArgs = splitInput(spec.name(), ins.size(), inputJson);
        MemoryClassLoader loader = new MemoryClassLoader(classes, Runner.class.getClassLoader());
        Object[] args = new Object[ins.size()];
        for (int i = 0; i < ins.size(); i++) {
            args[i] = decode(loader, module.name(), ins.get(i), rawArgs[i], i);
        }

        Object result = invoke(loader, module.name(), spec.name(), args);
        Object encoded = encode(module.name(), sig.out(), result);
        return writeJson(encoded);
    }

    // --- behavior selection ---------------------------------------------------------------------

    private static Ast.SpecBehavior resolveBehavior(Ast.Module module, String requested) {
        java.util.Set<String> implemented = module.fns().stream()
                .map(Ast.FnDef::name).collect(Collectors.toSet());
        Map<String, Ast.SpecBehavior> runnable = new java.util.LinkedHashMap<>();
        for (Ast.BehaviorDef b : module.behaviors()) {
            if (b instanceof Ast.SpecBehavior spec
                    && implemented.contains(spec.name()) && spec.requires().isEmpty()) {
                runnable.put(spec.name(), spec);
            }
        }
        if (requested == null) {
            if (runnable.size() == 1) {
                return runnable.values().iterator().next();
            }
            if (runnable.isEmpty()) {
                throw new RunException("no runnable behavior in this module. "
                        + "A runnable behavior has a `let` and no `requires`.");
            }
            throw usage("several runnable behaviors — pick one with --behavior: "
                    + String.join(", ", runnable.keySet()));
        }
        Ast.SpecBehavior found = runnable.get(requested);
        if (found != null) {
            return found;
        }
        throw new RunException(whyNotRunnable(module, requested, runnable.keySet()));
    }

    private static String whyNotRunnable(Ast.Module module, String name, java.util.Set<String> runnable) {
        String available = runnable.isEmpty() ? "none" : String.join(", ", runnable);
        for (Ast.BehaviorDef b : module.behaviors()) {
            if (!b.name().equals(name)) {
                continue;
            }
            if (b instanceof Ast.PipeBehavior) {
                return "`" + name + "` is a `>->` pipeline, which `run` cannot drive yet. "
                        + "Runnable: " + available + ".";
            }
            if (b instanceof Ast.SpecBehavior spec) {
                boolean implemented = module.fns().stream().anyMatch(f -> f.name().equals(name));
                if (!implemented) {
                    return "`" + name + "` has no implementation (it is injected from Java). "
                            + "Runnable: " + available + ".";
                }
                if (!spec.requires().isEmpty()) {
                    return "`" + name + "` requires injected dependencies ("
                            + String.join(", ", spec.requires()) + "), which `run` cannot supply. "
                            + "Runnable: " + available + ".";
                }
            }
        }
        return "no behavior named `" + name + "`. Runnable: " + available + ".";
    }

    // --- input --------------------------------------------------------------------------------

    private static Object[] splitInput(String name, int arity, String inputJson) {
        if (arity == 0) {
            return new Object[0];
        }
        if (inputJson == null) {
            throw usage("`" + name + "` takes " + arity + " input" + (arity == 1 ? "" : "s")
                    + " — pass --input");
        }
        Object tree = parseJson(inputJson);
        if (arity == 1) {
            return new Object[] {tree};
        }
        if (!(tree instanceof List<?> list)) {
            throw new RunException("`" + name + "` takes " + arity
                    + " inputs — --input must be a JSON array of that length");
        }
        if (list.size() != arity) {
            throw new RunException("`" + name + "` takes " + arity + " inputs, but --input has "
                    + list.size());
        }
        return list.toArray();
    }

    // --- decode / encode ----------------------------------------------------------------------

    private static Object decode(MemoryClassLoader loader, String pkg, Type type, Object raw, int index) {
        Result<?> result = decoderFor(loader, pkg, type, index).decode(raw, net.unit8.raoh.Path.ROOT);
        if (result instanceof Ok<?> ok) {
            return ok.value();
        }
        List<Issue> issues = ((Err<?>) result).issues().asList();
        String detail = issues.stream()
                .map(i -> jsonPointer(i.path()) + ": " + i.message())
                .collect(Collectors.joining("; "));
        throw new RunException("input #" + (index + 1) + " could not be decoded — " + detail);
    }

    private static Decoder<Object, ?> decoderFor(MemoryClassLoader loader, String pkg, Type type, int index) {
        if (type instanceof Type.Prim prim) {
            return leafDecoder(prim, index);
        }
        if (type instanceof Type.Ref ref) {
            try {
                Class<?> c = loader.loadClass(pkg + "." + ref.name());
                @SuppressWarnings("unchecked")   // the generated class's decoder() erases at the reflection boundary
                Decoder<Object, ?> decoder = (Decoder<Object, ?>) c.getMethod("decoder").invoke(null);
                return decoder;
            } catch (ReflectiveOperationException e) {
                throw new RunException("cannot obtain a decoder for `" + ref.name() + "`: " + e);
            }
        }
        throw new RunException("input #" + (index + 1) + " has type `" + type
                + "`, which `run` cannot decode yet (only a data type or a primitive).");
    }

    private static Decoder<Object, ?> leafDecoder(Type.Prim prim, int index) {
        return switch (prim) {
            case STRING -> ObjectDecoders.string();
            case INT -> ObjectDecoders.long_();
            case BOOL -> ObjectDecoders.bool();
            case DECIMAL -> ObjectDecoders.decimal();
            case DATE -> ObjectDecoders.date();
            case DATETIME -> ObjectDecoders.dateTime();
            case RAW -> throw new RunException("input #" + (index + 1)
                    + " has the reserved Raw type, which `run` cannot decode.");
        };
    }

    private static Object encode(String pkg, Type out, Object result) {
        if (out instanceof Type.Prim prim) {
            return encodeLeaf(prim, result);
        }
        // A sum output is one concrete case at run time; its own class carries the right encoder.
        try {
            @SuppressWarnings("unchecked")   // the generated class's encoder() erases at the reflection boundary
            Encoder<Object, ?> encoder = (Encoder<Object, ?>) result.getClass().getMethod("encoder").invoke(null);
            return encoder.encode(result);
        } catch (ReflectiveOperationException e) {
            throw new RunException("cannot obtain an encoder for the output `"
                    + result.getClass().getSimpleName() + "`: " + e);
        }
    }

    private static Object encodeLeaf(Type.Prim prim, Object value) {
        return switch (prim) {
            case STRING -> ObjectEncoders.string().encode((String) value);
            case INT -> ObjectEncoders.long_().encode((Long) value);
            case BOOL -> ObjectEncoders.bool().encode((Boolean) value);
            case DECIMAL -> ObjectEncoders.decimal().encode((java.math.BigDecimal) value);
            case DATE -> ObjectEncoders.date().encode((java.time.LocalDate) value);
            case DATETIME -> ObjectEncoders.dateTime().encode((java.time.LocalDateTime) value);
            case RAW -> throw new RunException("the output is the reserved Raw type, which `run` "
                    + "cannot encode.");
        };
    }

    // --- reflection helpers -------------------------------------------------------------------

    private static Object invoke(MemoryClassLoader loader, String pkg, String behavior, Object[] args) {
        String className = pkg + "." + behaviorClass(behavior);
        try {
            Class<?> c = loader.loadClass(className);
            Object instance = c.getConstructor().newInstance();
            Class<?>[] paramTypes = new Class<?>[args.length];
            java.util.Arrays.fill(paramTypes, Object.class);
            return c.getMethod("apply", paramTypes).invoke(instance, args);
        } catch (ReflectiveOperationException e) {
            Throwable cause = e instanceof java.lang.reflect.InvocationTargetException ite
                    ? ite.getCause() : e;
            throw new RunException("`" + behavior + "` failed: " + cause);
        }
    }

    /** The generated class capitalizes a behavior's first character (a Japanese name is unchanged),
     * matching {@code CodegenContext.behaviorClass}. */
    private static String behaviorClass(String name) {
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    // --- small utilities ----------------------------------------------------------------------

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new RunException("cannot read " + file + ": " + e.getMessage());
        }
    }

    /** The module name for a header-less source: the file name without its extension, or {@code main}
     * when that is not a usable identifier. */
    static String moduleName(Path file) {
        String fileName = file.getFileName().toString();
        int dot = fileName.indexOf('.');
        String stem = dot < 0 ? fileName : fileName.substring(0, dot);
        if (stem.isEmpty() || !Character.isLetter(stem.charAt(0))) {
            return "main";
        }
        for (int i = 1; i < stem.length(); i++) {
            char ch = stem.charAt(i);
            if (!Character.isLetterOrDigit(ch) && ch != '_') {
                return "main";
            }
        }
        return stem;
    }

    private static String jsonPointer(net.unit8.raoh.Path path) {
        String p = path.toJsonPointer();
        return p.isEmpty() ? "(root)" : p;
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static Object parseJson(String input) {
        try {
            return JSON.readValue(input, Object.class);
        } catch (Exception e) {
            throw new RunException("--input is not valid JSON: " + e.getMessage());
        }
    }

    private static String writeJson(Object tree) {
        try {
            return JSON.writeValueAsString(tree);
        } catch (Exception e) {
            throw new RunException("could not render the output as JSON: " + e.getMessage());
        }
    }
}
