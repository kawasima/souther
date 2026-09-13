package souther.cli;

import souther.compiler.Compiler;
import souther.compiler.generated.GeneratedBehavior;
import souther.compiler.generated.JsonBoundary;
import souther.compiler.Reserved;
import souther.compiler.ast.Hir;
import souther.compiler.check.Prepared;
import souther.compiler.check.Sig;
import souther.compiler.check.BoundaryInput;
import souther.compiler.check.BoundaryOutput;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Compilation;
import souther.compiler.diag.Located;
import souther.compiler.diag.Messages;
import souther.compiler.check.BehaviorRequirement;
import souther.compiler.query.Bodies;
import net.unit8.raoh.Issues;
import net.unit8.raoh.ResourceBundleMessageResolver;


import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamWriteConstraints;
import tools.jackson.core.TokenStreamContext;
import tools.jackson.core.exc.StreamConstraintsException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Drives a compiled behavior from the command line: {@code souther run <file.sou> [-cp <path>]
 * [--behavior <name>] [--input <json>]}. The runner is itself the Java boundary (spec §fn) —
 * it decodes the JSON input into the behavior's parameter types through their derived decoders,
 * applies the behavior, and encodes the returned domain value back to JSON through its derived
 * encoder. Souther keeps no I/O of its own; a header-less {@code .sou} is named after the file (see
 * {@link Parser}).
 *
 * <p>The runner drives one {@code .sou} file. Stdlib imports resolve, and an import of another user
 * module resolves against {@code -cp} — the same class path {@code compile} takes, read for the
 * imported module's declarations and holding the classes the behavior then runs against. A behavior
 * is runnable when it has a {@code let} and no
 * {@code depends on}, or when it is a {@code >->} pipeline whose stages are all themselves runnable
 * (each has a {@code let} and no {@code depends on}) — such a pipeline compiles to a no-arg {@code
 * $Impl} that chains the stages, so it runs like any other behavior. An injected behavior (no
 * implementation), one that needs injected dependencies, or a pipeline with such a stage has nothing
 * for {@code run} to supply; each is refused with a reason.
 *
 * <p>Being runnable is not the whole of it. The runner is another module's reader, reaching the
 * compiled behavior by reflection from {@code souther.compiler}, and what a module publishes is what
 * codegen marks public. So {@code run} drives a behavior the module exposes, and a runnable one kept
 * to the module is refused as that — before its input type is asked for a decoder, which is the same
 * refusal wearing another name.
 */
public final class Runner {

    private Runner() {}

    /**
     * A user-facing failure of {@code souther run}, carrying the process exit code to use.
     *
     * <p>It is written twice, as a compile error is (see {@code Diagnostic}): {@code getMessage()} is
     * the English form a Java caller sees, and {@code messageKey} plus {@code args} resolve against
     * the catalog for the locale the command line selected. The runner is a user surface, so its
     * failures are read in the same language as the compiler's diagnostics.
     */
    public static final class RunException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final int exitCode;
        private final String messageKey;
        private final transient Object[] args;
        /** The decoder's own issues, when this is a decode failure: their text is written by the
         * decoder's catalog, so it is resolved per locale rather than fixed at throw time. */
        private final transient Issues issues;

        RunException(String messageKey, String message, int exitCode, Issues issues, Object... args) {
            super(message);
            this.exitCode = exitCode;
            this.messageKey = messageKey;
            this.args = args;
            this.issues = issues;
        }

        RunException(String message) {
            this(null, message, 1, null);
        }

        /** This failure in {@code locale}. An exit code of 2 is a usage error, which ends with the
         * command's usage line. */
        String localized(java.util.Locale locale) {
            if (messageKey == null) {
                return getMessage();
            }
            Object[] resolved = args;
            if (issues != null) {
                // the issue text is the last argument of `run.decode.failed`, the one key that carries
                // issues; rewriting it here is what puts the decoder's own wording into the reader's
                // language
                resolved = args.clone();
                resolved[resolved.length - 1] = detail(issues.resolve(DECODE_MESSAGES, locale));
            }
            String text = Messages.get(messageKey, locale, resolved);
            return exitCode == 2 ? text + "\n" + Messages.get("run.usage.line", locale) : text;
        }
    }

    /**
     * The decoder's own message catalog, which its issues are written against. It answers per issue
     * rather than per code: an issue names the constraint that rejected the value, so a code covering
     * several of them — {@code out_of_range} states a lower bound, an upper bound, or both — is
     * described as the one it is. An entry whose placeholders the issue cannot fill is skipped rather
     * than half-filled, and where no entry applies the message the decoder already wrote stands.
     */
    private static final ResourceBundleMessageResolver DECODE_MESSAGES =
            new ResourceBundleMessageResolver("net.unit8.raoh.messages");

    /** A failure that names a catalog key: the English literal for a Java caller, the key for the
     * command line's chosen locale. */
    private static RunException fail(String key, String message, Object... args) {
        return new RunException(key, message, 1, null, args);
    }

    /** Parses the {@code run} subcommand's arguments (everything after {@code run}) and runs it,
     *  collecting the compile's warnings into {@code warningsOut} for the caller to render —
     *  running a module says what compiling it would have said. */
    static String runCli(String[] args, List<Located> warningsOut) {
        Path file = null;
        String behaviorName = null;
        String inputJson = null;
        List<Path> classPath = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--behavior" -> behaviorName = value(args, ++i, "--behavior");
                case "--input" -> inputJson = value(args, ++i, "--input");
                case "-cp", "--class-path" -> {
                    String option = args[i];
                    classPath.addAll(entriesOf(value(args, ++i, option)));
                }
                default -> {
                    if (file != null) {
                        throw usage("run.usage.singlefile", "run takes a single .sou file");
                    }
                    file = Path.of(args[i]);
                }
            }
        }
        if (file == null) {
            throw usage("run.usage.nofile", "no source file given");
        }
        return run(file, behaviorName, inputJson, warningsOut, pathOf(classPath));
    }

    /** A class path as its entries, in the order they are searched. */
    static List<Path> entriesOf(String written) {
        List<Path> entries = new ArrayList<>();
        for (String entry : written.split(java.io.File.pathSeparator)) {
            if (!entry.isBlank()) {
                entries.add(Path.of(entry));
            }
        }
        return entries;
    }

    private static ModulePath pathOf(List<Path> entries) {
        return entries.isEmpty() ? ModulePath.EMPTY : ModulePath.ofClassPath(entries);
    }

    private static String value(String[] args, int i, String option) {
        if (i >= args.length) {
            throw usage("run.usage.optionvalue", "`" + option + "` needs a value", option);
        }
        return args[i];
    }

    private static RunException usage(String key, String problem, Object... args) {
        return new RunException(key, problem
                + "\nusage: souther run <file.sou> [-cp <path>] [--behavior <name>] [--input <json>]",
                2, null, args);
    }

    /** Compiles {@code file}, drives {@code behaviorName} with {@code inputJson}, and returns the
     * encoded output as JSON text. {@code behaviorName} may be null when the module holds exactly
     * one runnable behavior; {@code inputJson} may be null for a zero-argument behavior. */
    public static String run(Path file, String behaviorName, String inputJson) {
        return run(file, behaviorName, inputJson, new ArrayList<>(), ModulePath.EMPTY);
    }

    /**
     * As {@link #run(Path, String, String)}, resolving an import naming no module in {@code file}
     * against {@code path} and running against its classes, and collecting the compile's warnings
     * into {@code warningsOut}.
     */
    static String run(Path file, String behaviorName, String inputJson, List<Located> warningsOut,
                      ModulePath path) {
        String source = read(file);
        String moduleName = moduleName(file);

        // One compilation answers all of it. Re-reading the source here to find the behavior and
        // its signature would resolve every name a second time, against a tree this compile has
        // already produced.
        Compilation compilation = Compiler.compiled(source, moduleName, warningsOut, path);
        // The module this file declares, and not one it reached: what the path holds is read for its
        // declarations and is no part of what this compilation declares.
        Prepared module = compilation.module(compilation.modules().get(0));
        Map<String, Sig> sigs = compilation.signatures(module.name());

        Hir.BehaviorDef spec = resolveBehavior(compilation, module, behaviorName);
        Sig sig = sigs.get(spec.name());

        // The compilation's own — its classes over the ones the path already built. Composing a
        // loader here instead would be a second statement of that order, which is the one thing
        // about loading this compiler settles in a single place.
        ClassLoader loader = compilation.loader();
        Object[] args = decodeInputs(loader, spec.name(), sig.ins(), inputJson);

        Object result = invoke(loader, module.name(), spec.name(), args);
        Object encoded = encodeOutput(loader, module.name(), spec.name(), sig.out(), result);
        return writeJson(encoded);
    }

    // --- behavior selection ---------------------------------------------------------------------

    /** The names a behavior depends on, as a message lists them. */
    private static String dependencyNames(Hir.SpecBehavior spec) {
        List<String> names = new java.util.ArrayList<>();
        for (Hir.Var dep : spec.dependsOn()) {
            names.add(named(dep));
        }
        return String.join(", ", names);
    }

    /**
     * The name a declared dependency or a pipeline stage is reached by.
     *
     * <p>Everything below the compile above names something: {@code Compiler.compiled} raises on the
     * first error and a name that denotes nothing is one, so this reads a module that got past it.
     * One arriving here would be `run` working from a module the compile refused.
     */
    private static String named(Hir.Var name) {
        return switch (name) {
            case Hir.Var.Denoting d -> d.denotes().name();
            case Hir.Var.Unanswered u -> throw u.unexpectedHere();
        };
    }

    /**
     * Whether the module publishes {@code name}. This is the same question codegen asks before it
     * marks a generated class {@code ACC_PUBLIC} ({@code CodegenContext.pub}), and it is asked here
     * for that reason: the runner reaches the compiled behavior by reflection from another package,
     * which the JVM allows only for a class the module published. A module with no {@code exposing}
     * list keeps nothing to itself, so a header-less file publishes everything in it.
     */
    private static boolean exposes(Prepared module, String name) {
        return module.exposing().isEmpty() || module.exposing().contains(name);
    }

    /**
     * The behavior to drive.
     *
     * <p>Two questions are asked, and they are not the same one. Whether a behavior can run at all is
     * asked of the module's own workings — it has a {@code let} and needs nothing injected. Whether
     * {@code run} can reach it is asked at the module's edge, because {@code run} stands outside the
     * module and arrives the way any other reader does. A behavior can answer the first and not the
     * second, and one the module keeps to itself is exactly that.
     */
    private static Hir.BehaviorDef resolveBehavior(Compilation compilation, Prepared module,
                                                   String requestedSpelling) {
        // What `--behavior` was given is a name arriving from outside, and it is looked up
        // against names the source settled.
        String requested = Reserved.name(requestedSpelling);
        Map<String, List<BehaviorRequirement>> requirements = requirementsOf(compilation, module);
        // The ones that can be run, in the order the module declares them. A reader is given this
        // list to pick from, so it is held as something that has an order rather than as a set of
        // names put in whatever a walk of them came to.
        java.util.SequencedMap<String, Hir.BehaviorDef> drivable = new java.util.LinkedHashMap<>();
        for (Hir.BehaviorDef b : module.behaviors()) {
            if (!exposes(module, b.name())) {
                continue;
            }
            // Two facts, and each is asked of whoever owns it. Whether there is a body here to run
            // is the module's classification; whether anything has to be handed to it is what the
            // declaration says it depends on.
            if (b instanceof Hir.SpecBehavior spec
                    && module.implementationOf(spec).hasBody() && spec.dependsOn().isEmpty()) {
                drivable.put(spec.name(), spec);
            } else if (b instanceof Hir.PipeBehavior pipe
                    && pipelineBlocker(pipe, requirements) == null) {
                drivable.put(pipe.name(), pipe);
            }
        }
        if (requested == null) {
            if (drivable.size() == 1) {
                return drivable.values().iterator().next();
            }
            if (drivable.isEmpty()) {
                throw fail("run.behavior.none", "no behavior in this module can be run. "
                        + "`run` runs a behavior that is runnable — implemented in Souther and"
                        + " needing nothing injected, a `>->` pipeline when every stage is — and"
                        + " that the module exposes.");
            }
            String names = String.join(", ", drivable.sequencedKeySet());
            throw usage("run.behavior.several",
                    "several behaviors can be run — pick one with --behavior: " + names, names);
        }
        Hir.BehaviorDef found = drivable.get(requested);
        if (found != null) {
            return found;
        }
        throw whyNotRunnable(compilation, module, requested, drivable.sequencedKeySet());
    }

    /** A reason a behavior cannot be driven, in both forms: the catalog key with its arguments, and
     * the English literal. The list of behaviors that can be driven is appended to both. */
    private record Blocker(String key, String message, Object... args) {}

    /**
     * Why a {@code >->} pipeline cannot be driven by {@code run}, or null when every stage is
     * in-language. A pipeline whose flattened stages all have a {@code let} and no {@code depends on}
     * compiles to an {@code $Impl} with a public no-arg constructor, so it runs exactly like a simple
     * behavior. A stage with no implementation (injected from Java) or one that needs its own injected
     * dependencies would make that constructor take those behaviors, which {@code run} cannot supply.
     */
    private static Blocker pipelineBlocker(Hir.PipeBehavior pipe,
            Map<String, List<BehaviorRequirement>> requirements) {
        List<BehaviorRequirement> requires = requirements.get(pipe.name());
        if (requires == null || requires.isEmpty()) {
            return null;
        }
        // What the composition would be handed, and who wanted it. A dependency the composition
        // itself asks for is a stage with no implementation; one another definition asks for came in
        // through a stage that declares it.
        BehaviorRequirement first = requires.get(0);
        if (first.requiredBy().contains(pipe.name())) {
            String stage = first.dependency().toString();
            return new Blocker("run.pipeline.noimpl",
                    "`" + pipe.name() + "` is a pipeline whose stage `" + stage
                            + "` has no implementation (it is injected from Java), which `run` cannot supply.",
                    pipe.name(), stage);
        }
        String stage = first.requiredBy().get(0);
        List<String> wanted = new java.util.ArrayList<>();
        for (BehaviorRequirement each : requires) {
            if (each.requiredBy().contains(stage)) {
                wanted.add("`" + each.dependency().name() + "`");
            }
        }
        String dependencies = String.join(", ", wanted);
        return new Blocker("run.pipeline.depends",
                "`" + pipe.name() + "` is a pipeline whose stage `" + stage
                        + "` depends on injected dependencies (" + dependencies
                        + "), which `run` cannot supply.",
                pipe.name(), stage, dependencies);
    }

    /**
     * What each behavior of the module has to be handed to be built.
     *
     * <p>Asked of the compilation rather than worked out from the stages here. Which behavior a
     * stage names was answered when the module was resolved, and a name is not the answer: a stage
     * naming another module's behavior and one naming this module's own can be written the same, so
     * a walk that matched the spelling offered a composition nothing here can build as one `run`
     * could drive. This is the same answer the emitter reads when it decides whether the generated
     * class takes a constructor argument, which is the thing `run` needs to know.
     */
    private static Map<String, List<BehaviorRequirement>> requirementsOf(Compilation compilation,
                                                                        Prepared module) {
        Map<String, List<BehaviorRequirement>> answered =
                compilation.db().ask(new Bodies.Requirements(module.name())).value();
        return answered == null ? Map.of() : answered;
    }

    /**
     * Why the behavior the caller named cannot be driven, with the ones that can.
     *
     * <p>A behavior the module keeps to itself is answered as that and nothing else, ahead of the
     * reasons that read its workings. Those reasons describe what is inside the module, which is not
     * what a reader standing outside it is owed, and following one of them would only bring the
     * author back to the same refusal.
     */
    private static RunException whyNotRunnable(Compilation compilation, Prepared module,
                                               String name,
                                               java.util.SequencedSet<String> drivable) {
        String available = drivable.isEmpty() ? "none" : String.join(", ", drivable);
        Map<String, List<BehaviorRequirement>> requirements = requirementsOf(compilation, module);
        for (Hir.BehaviorDef b : module.behaviors()) {
            if (!b.name().equals(name)) {
                continue;
            }
            if (!exposes(module, name)) {
                return fail("run.behavior.notexposed",
                        "`" + name + "` is not exposed, and `run` drives a behavior the module"
                                + " exposes — add it to `exposing`. Available to run: " + available + ".",
                        name, available);
            }
            if (b instanceof Hir.PipeBehavior pipe) {
                Blocker blocker = pipelineBlocker(pipe, requirements);
                if (blocker == null) {
                    return fail("run.behavior.runnable",
                            "`" + name + "` is runnable. Available to run: " + available + ".", name, available);
                }
                Object[] args = new Object[blocker.args().length + 1];
                System.arraycopy(blocker.args(), 0, args, 0, blocker.args().length);
                args[args.length - 1] = available;
                return fail(blocker.key(), blocker.message() + " Available to run: " + available + ".", args);
            }
            if (b instanceof Hir.SpecBehavior spec) {
                // Two ways to have no body, and they send an author to different places: one is
                // supplied from Java and the other is a `let` this model has not written yet. Which
                // of them this is, is the module's answer and not a table read again here.
                switch (module.implementationOf(spec)) {
                    case IMPLEMENTED -> { }
                    case INJECTION_TARGET -> {
                        return fail("run.behavior.noimpl",
                                "`" + name + "` has no implementation (it is injected from Java)."
                                        + " Available to run: " + available + ".", name, available);
                    }
                    case UNIMPLEMENTED -> {
                        return fail("run.behavior.unwritten",
                                "`" + name + "` declares what it depends on and has no `let` yet,"
                                        + " so there is nothing to run. Available to run: "
                                        + available + ".", name, available);
                    }
                }
                if (!spec.dependsOn().isEmpty()) {
                    String dependencies = dependencyNames(spec);
                    return fail("run.behavior.depends",
                            "`" + name + "` depends on injected dependencies (" + dependencies
                                    + "), which `run` cannot supply. Available to run: " + available + ".",
                            name, dependencies, available);
                }
            }
        }
        return fail("run.behavior.unknown",
                "no behavior named `" + name + "`. Available to run: " + available + ".", name, available);
    }

    // --- input --------------------------------------------------------------------------------

    /**
     * The behavior's arguments, read from {@code --input}.
     *
     * <p>How the text is cut into arguments is not a question the syntax answers. From two inputs up
     * it is: the text is a JSON array of that length, positionally, and an array of another length or
     * no array at all is refused by arity alone. A single input is written as it stands, and there the
     * same text has two readings — for {@code (xs: List<Int>)}, {@code [1,2,3]} is the argument, and
     * {@code [[1,2,3]]} is the argument written inside the argument array the two-input form takes.
     * Arity cannot tell them apart; only decoding against the parameter type can. So the split and the
     * decode are one step, taken with the types in hand.
     */
    private static Object[] decodeInputs(ClassLoader loader, String name,
                                         List<BoundaryInput> ins, String inputJson) {
        int arity = ins.size();
        if (arity == 0) {
            return new Object[0];
        }
        if (inputJson == null) {
            throw usage("run.input.missing", "`" + name + "` takes " + arity + " input"
                    + (arity == 1 ? "" : "s") + " — pass --input", name, arity);
        }
        JsonNode tree = parseJson(inputJson);
        if (arity == 1) {
            return new Object[] {decodeSoleInput(loader, ins.get(0), tree)};
        }
        if (!tree.isArray()) {
            throw fail("run.input.notarray", "`" + name + "` takes " + arity
                    + " inputs — --input must be a JSON array of that length", name, arity);
        }
        if (tree.size() != arity) {
            throw fail("run.input.count", "`" + name + "` takes " + arity + " inputs, but --input has "
                    + tree.size(), name, arity, tree.size());
        }
        Object[] args = new Object[arity];
        for (int i = 0; i < arity; i++) {
            args[i] = decode(loader, ins.get(i), tree.get(i), i);
        }
        return args;
    }

    /**
     * The sole input, read as it stands — and only when that fails, read again as the value a
     * one-element array wraps.
     *
     * <p>The order is what makes the second reading safe: an input that is an array in its own right
     * decodes on the first attempt and never reaches it. Being an array is not the mistake; failing as
     * the value while succeeding as the array's sole element is. When the element fails too, the input
     * is wrong for some other reason and the decoder's own report is what says so.
     */
    private static Object decodeSoleInput(ClassLoader loader, BoundaryInput shape,
                                          JsonNode tree) {
        try {
            return decode(loader, shape, tree, 0);
        } catch (RunException asWritten) {
            if (tree.isArray() && tree.size() == 1 && decodes(loader, shape, tree.get(0))) {
                throw fail("run.input.wrapped", "This behavior has one parameter. Pass its value"
                        + " directly to --input; remove the outer array.");
            }
            throw asWritten;
        }
    }

    /** Whether {@code raw} decodes as {@code type}, asked without reporting anything. */
    private static boolean decodes(ClassLoader loader, BoundaryInput shape, JsonNode raw) {
        return JsonBoundary.reads(loader, shape, raw);
    }

    // --- decode / encode ----------------------------------------------------------------------

    /**
     * One input read at its position in the argument vector.
     *
     * <p>The reading is {@link JsonBoundary}'s; what is here is where it sat in {@code --input} and
     * how that is said. A position in an argument vector is a thing only a command line has, so the
     * boundary answers with what it found and this turns it into the message a reader gets.
     */
    private static Object decode(ClassLoader loader, BoundaryInput shape, JsonNode raw,
                                 int index) {
        return switch (JsonBoundary.read(loader, shape, raw)) {
            case JsonBoundary.Read.Value v -> v.value();
            case JsonBoundary.Read.Malformed m -> throw fail("run.decode.malformed",
                    "input #" + (index + 1) + " is not shaped like `" + m.type() + "` — check --input",
                    index + 1, m.type());
            case JsonBoundary.Read.Refused r -> throw new RunException("run.decode.failed",
                    "input #" + (index + 1) + " could not be decoded — " + detail(r.issues()), 1,
                    r.issues(), index + 1, detail(r.issues()));
        };
    }

    /** The decoder's issues as one line: each at its JSON pointer, in the order they were found. */
    private static String detail(Issues issues) {
        return issues.asList().stream()
                .map(i -> jsonPointer(i.path()) + ": " + i.message())
                .collect(Collectors.joining("; "));
    }


    /**
     * The whole output, encoded.
     *
     * <p>{@link JsonBoundary#write} descends the value by recursion, so a value that nests deeply
     * enough runs the stack out before the writer ever counts a level. A behavior can build a value
     * deeper than anything it was handed, so this is reached from input the boundary accepted. A
     * {@code StackOverflowError} is not a {@code RunException}, so left alone it reaches the caller
     * as a stack trace.
     */
    private static Object encodeOutput(ClassLoader loader, String pkg, String behavior,
                                       BoundaryOutput out, Object result) {
        try {
            return JsonBoundary.write(loader, pkg, behavior, out, result);
        } catch (StackOverflowError _) {
            throw fail("run.encode.toodeep",
                    "the output nests too deeply for this boundary to encode.");
        }
    }

    // --- reflection helpers -------------------------------------------------------------------

    /**
     * The behavior applied to its arguments, with the two ways that fails told apart.
     *
     * <p>The behavior itself throwing arrives as an {@code InvocationTargetException} and is the
     * behavior's failure. Anything else — the class not there, no no-arg constructor, no reachable
     * {@code apply} — is the runner unable to start it, which after the exposed check is codegen and
     * the runner disagreeing rather than anything the module did. Reported as one, the second reads
     * as the behavior having run and failed, and sends the author looking through a body that was
     * never entered.
     */
    private static Object invoke(ClassLoader loader, String pkg, String behavior, Object[] args) {
        try {
            return GeneratedBehavior.apply(loader, pkg, behavior, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            throw fail("run.behavior.failed", "`" + behavior + "` failed: " + cause,
                    behavior, String.valueOf(cause));
        } catch (ReflectiveOperationException e) {
            String className = GeneratedBehavior.implClass(pkg, behavior);
            throw fail("run.behavior.unreachable",
                    "`" + behavior + "` could not be started — the compiled `" + className
                            + "` is not reachable: " + e + ". This is a defect in the compiler,"
                            + " not in the module.", behavior, className, e.toString());
        }
    }

    // --- small utilities ----------------------------------------------------------------------

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw fail("run.read.failed", "cannot read " + file + ": " + e.getMessage(),
                    file.toString(), e.getMessage());
        }
    }

    /** The module name for a header-less source: the file name without its extension, or {@code main}
     * when that is not a usable identifier. */
    static String moduleName(Path file) {
        String fileName = file.getFileName().toString();
        int dot = fileName.indexOf('.');
        // Canonicalized before it is judged, not after: a file delivered by macOS carries its name
        // decomposed, and a combining mark is not a letter or a digit, so the same file would be
        // `main` on one machine and its own name on another.
        String stem = Reserved.name(dot < 0 ? fileName : fileName.substring(0, dot));
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

    /** How deep the mapper reads and writes before it refuses, counted in levels of JSON. */
    private static final int READ_DEPTH_LIMIT =
            StreamReadConstraints.defaults().getMaxNestingDepth();
    private static final int WRITE_DEPTH_LIMIT =
            StreamWriteConstraints.defaults().getMaxNestingDepth();

    /**
     * The depth a context stands at when its side has just refused a value for nesting. The two
     * sides count differently, measured: reading stands on the level it refused to enter, and
     * writing on the last level it accepted.
     */
    private static final int READ_REFUSAL_DEPTH = READ_DEPTH_LIMIT + 1;
    private static final int WRITE_REFUSAL_DEPTH = WRITE_DEPTH_LIMIT;

    /**
     * Parses {@code --input}.
     *
     * <p>The parser is made here rather than left to {@code readTree(String)} because the exception a
     * depth refusal throws carries neither a location nor the parser that raised it. Holding the
     * parser is what lets the refusal name the path it gave up at, the way every other thing the
     * boundary rejects is named.
     *
     * <p>Input with nothing in it reads as the missing node rather than as no node at all, so an
     * empty {@code --input} reaches the same refusal as input of the wrong shape.
     */
    private static JsonNode parseJson(String input) {
        JsonParser parser = JSON.createParser(input);
        try {
            JsonNode tree = JSON.readTree(parser);
            return tree == null ? JSON.missingNode() : tree;
        } catch (Exception e) {
            TokenStreamContext where = parser.streamReadContext();
            if (refusedForDepth(e, where, READ_REFUSAL_DEPTH)) {
                throw tooDeep("run.input.toodeep", "--input nests deeper than this boundary accepts",
                        where, READ_DEPTH_LIMIT);
            }
            throw fail("run.input.badjson", "--input is not valid JSON: " + e.getMessage(),
                    e.getMessage());
        } finally {
            parser.close();
        }
    }

    /** Renders the encoded output. The generator is held for the same reason the parser is. */
    private static String writeJson(Object tree) {
        StringWriter out = new StringWriter();
        JsonGenerator generator = JSON.createGenerator(out);
        try {
            JSON.writeValue(generator, tree);
            return out.toString();
        } catch (Exception e) {
            // read before the close in `finally`, which resets the generator's context to the root
            TokenStreamContext where = generator.streamWriteContext();
            if (refusedForDepth(e, where, WRITE_REFUSAL_DEPTH)) {
                throw tooDeep("run.output.toodeep",
                        "the output nests deeper than this boundary can render",
                        where, WRITE_DEPTH_LIMIT);
            }
            throw fail("run.output.json", "could not render the output as JSON: " + e.getMessage(),
                    e.getMessage());
        } finally {
            generator.close();
        }
    }

    /**
     * Whether {@code failure} is the reader or writer refusing a value for its depth.
     *
     * <p>{@code StreamConstraintsException} is not the depth refusal alone: the reader raises the
     * same type for a number too long, a name too long, a string too long, a document too long and a
     * token count too high. Which limit it was is asked of the context's own depth rather than of
     * the exception's text, which is prose Jackson is free to rewrite.
     *
     * <p>Standing at {@code refusalDepth} is the depth refusal and can be nothing else. A value
     * refused for another reason stands shallower: the reader admits scalars at every level up to
     * the limit, so the deepest a long number can be refused at is one level short of where a
     * refusal for depth stands. Every other constraint keeps the wording it had.
     */
    private static boolean refusedForDepth(Exception failure, TokenStreamContext where,
                                           int refusalDepth) {
        return failure instanceof StreamConstraintsException
                && where.getNestingDepth() >= refusalDepth;
    }

    /**
     * A value that nests deeper than the boundary handles, reported where it stopped.
     *
     * <p>The limit is named rather than the value's own depth: reading and writing both stop at the
     * level that crosses the limit, so how much deeper the value goes was never counted. What is
     * counted is levels of JSON, which is not levels of the data — one level of a self-referential
     * data spends a level for its object and another for the list that carries the next one.
     */
    private static RunException tooDeep(String key, String english, TokenStreamContext where,
                                        int limit) {
        String path = depthPath(where);
        return fail(key, english + ": " + path + " and below (JSON nesting past " + limit
                + " levels)", path, limit);
    }

    /** How much of the path to the deepest level is worth reading. */
    private static final int DEPTH_PATH_SEGMENTS = 8;

    /**
     * The path the depth limit was reached at, cut short. Nesting that deep is nesting that repeats,
     * so the whole pointer runs to hundreds of segments of the same few names; the head is the part
     * that says which branch went deep.
     */
    private static String depthPath(TokenStreamContext where) {
        String pointer = where.pathAsPointer().toString();
        if (pointer.isEmpty()) {
            return "(root)";
        }
        int cut = 0;
        for (int i = 0; i < DEPTH_PATH_SEGMENTS; i++) {
            int next = pointer.indexOf('/', cut + 1);
            if (next < 0) {
                return pointer;
            }
            cut = next;
        }
        return pointer.substring(0, cut) + "/…";
    }
}
