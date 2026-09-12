package souther.cli;

import souther.compiler.cst.SourceLayout;
import souther.compiler.source.SourceId;

import souther.compiler.jvm.ClassFileImage;
import souther.compiler.jvm.JvmClassName;
import souther.compiler.Compiler;
import souther.compiler.Reserved;
import souther.compiler.cst.CstError;
import souther.compiler.cst.CstParser;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.DiagnosticCode;
import souther.compiler.diag.DiagnosticRenderer;
import souther.compiler.diag.HumanRenderer;
import souther.compiler.diag.JsonRenderer;
import souther.compiler.diag.Located;
import souther.compiler.diag.Messages;
import souther.compiler.diag.SourceContext;
import souther.compiler.diag.SourceContextResolver;
import souther.compiler.diag.SourceNameResolver;
import souther.compiler.diag.SourceRendering;
import souther.compiler.diag.SourceNames;
import souther.compiler.doc.ApiCommand;
import souther.compiler.doc.DocCommand;
import souther.compiler.doc.JapiCommand;
import souther.compiler.doc.McpServer;
import souther.compiler.fmt.Deviations;
import souther.compiler.fmt.Formatter;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;
import souther.compiler.report.GeneratedRows;
import souther.compiler.report.UnifiedDiff;
import souther.lsp.LspServer;
import souther.cli.init.InitCommand;

import java.io.Console;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The command line's entry point: it resolves the command a line names, holds the line against what
 * that command takes, and runs it.
 *
 * <p>Which commands there are and what each takes is not written here. {@link CliCommand} is the
 * table of commands and {@link CliOption} of options, and both {@link Usage} — what
 * {@code souther help} writes — and the check above the dispatch are read off them. What this class
 * holds is each command's own reading of its arguments, and the one boundary the whole run sits
 * inside.
 */
public final class Main {

    /** Every option this compiler knows, for the test that holds the table against the parsers. */
    static java.util.Set<String> knownOptions() {
        return CliOption.everySpelling();
    }

    /** The commands that take {@code option}, or null where this compiler has no such option. */
    static String optionOwners(String option) {
        return CliOption.owners(option);
    }

    public static void main(String[] args) {
        int code = guarded(() -> dispatch(args));
        if (code != 0) {
            System.exit(code);
        }
    }

    /**
     * Runs the command with the last boundary this compiler has around it, and answers with the exit
     * code.
     *
     * <p>Around the whole command and not around the compile inside it: reading the arguments builds
     * paths and options, and a failure there is as much this compiler's as one from the emitter. A
     * {@link CompileException} is not one of these — it is an answer about the source and is rendered
     * as one where it is caught — so it goes on rather than being reported as a fault of the compiler.
     */
    static int guarded(java.util.function.IntSupplier command) {
        try {
            return command.getAsInt();
        } catch (RuntimeException e) {
            String said = internalFailure(e);
            if (said == null) {
                throw e;
            }
            System.err.println(said);
            return 1;
        }
    }

    /**
     * The command's answer as its exit code, which only {@link #main} turns into one.
     *
     * <p>Ending the process is a fact about the process, not about the command, and a command that
     * ends it has no answer left to give: everything it decides — that an option is not one of its
     * own, that a file is not formatted — is unreachable from anywhere but a shell. Read as a number
     * here, each of those is an ordinary result.
     */
    static int dispatch(String[] args) {
        String named = args.length == 0 ? "" : args[0];
        String[] rest = args.length == 0 ? args : java.util.Arrays.copyOfRange(args, 1, args.length);
        // Written where a command goes, `--help` asks what `help` asks. Read as an option it would
        // be an option of no command, since which command's options are read is what has not been
        // said yet; read as its own shape it would be a line whose remaining arguments nothing
        // looks at, and `souther --help compile` would answer with the listing.
        CliCommand command = CliOption.isHelp(named) ? CliCommand.HELP : CliCommand.named(named);
        if (command == null) {
            String hint = named.endsWith(".sou")
                    ? "no command given — did you mean `souther compile " + named
                            + " …` or `souther run " + named + " …`?"
                    : named.isEmpty() ? "no command given" : "unknown command `" + named + "`";
            System.err.println(hint);
            System.err.println(Usage.all());
            return 2;
        }
        // Before the command, and the same check whichever one was named. What each parser used to
        // ask for itself — is this token mine — three of them asked and two did not, and none of
        // them could ask the question the tokens cannot answer one at a time: whether an option
        // written here is one this line ever reads.
        CliOption.Reading read = CliOption.read(command.spelling(), rest);
        // Before the refusal. A line that asks what the command takes is asking because it does not
        // know, so answering it with what is wrong with the line — and then not answering it — is
        // the one reply that leaves its author where they started.
        if (read.help()) {
            System.out.println(Usage.of(command));
            return 0;
        }
        if (read.refusal() != null) {
            System.err.println(Messages.get(read.refusal().key(),
                    RenderOptions.asking(read.lang()).locale(), read.refusal().args()));
            System.err.println(Usage.of(command));
            return 2;
        }
        return work(command, rest).getAsInt();
    }

    /**
     * What the command does, kept unrun until everything said above about the line holds.
     *
     * <p>A {@code switch} over the table and not over the name, and an expression with no default
     * arm: a command written into {@link CliCommand} and left out here is a compile error. The
     * names used to be matched a second time in this method, which made the table and the dispatch
     * two lists to keep in step — the drift a test would then have to go looking for.
     */
    private static java.util.function.IntSupplier work(CliCommand command, String[] rest) {
        return switch (command) {
            case INIT -> () -> initSubcommand(rest);
            case RUN -> () -> runSubcommand(rest);
            case COMPILE -> () -> compileSubcommand(rest);
            case FMT -> () -> fmtSubcommand(rest);
            case EXAMPLES -> () -> examplesSubcommand(rest);
            case DOC -> () -> DocCommand.run(rest, System.out, System.err);
            case API -> () -> ApiCommand.run(rest, System.out, System.err);
            case JAPI -> () -> JapiCommand.run(rest, System.out, System.err);
            case MCP -> () -> mcpSubcommand(rest);
            case LSP -> () -> lspSubcommand(rest);
            case HELP -> () -> helpSubcommand(rest);
        };
    }

    /**
     * The one line the command line says when the compiler fails for a reason nobody listed, or
     * {@code null} for a {@link CompileException}, which is an answer about the source and is
     * rendered as one wherever it is caught.
     *
     * <p>A stack trace at this point asks the author to read this compiler's call stack for a problem
     * that is not theirs. What is left — the exception's name and what it said — is what a report of
     * it would be filed with, and is the only part of it they can pass on.
     */
    static String internalFailure(RuntimeException e) {
        if (e instanceof CompileException) {
            return null;
        }
        String said = e.getMessage();
        return "internal compiler error: " + e.getClass().getSimpleName()
                + (said == null ? "" : ": " + said.replaceAll("\\R", " "));
    }

    /** {@code souther compile <file.sou>... -d <outdir>}: writes the generated {@code .class} files. */
    private static int compileSubcommand(String[] rawArgs) {
        RenderOptions render = new RenderOptions();
        String[] args = render.extract(rawArgs);
        if (args == null) {
            return 2;
        }
        List<Path> sources = new ArrayList<>();
        List<Path> classPath = new ArrayList<>();
        Path outDir = Path.of(".");
        Adequacy.Asked measure = Adequacy.Asked.NOTHING;
        boolean refuseWarnings = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--adequacy" -> {
                    Adequacy.Asked named = adequacyAsked(args[++i]);
                    if (named == null) {
                        System.err.println(
                                "`--adequacy` takes off, witness, all, reliable-domain"
                                        + " or classes");
                        return 2;
                    }
                    measure = named;
                }
                case "--warnings" -> {
                    if (!(args[++i].equals("report") || args[i].equals("error"))) {
                        System.err.println("`--warnings` takes report or error");
                        return 2;
                    }
                    refuseWarnings = args[i].equals("error");
                }
                case "-d", "--dir" -> outDir = Path.of(args[++i]);
                case "-cp", "--class-path" -> {
                    for (String entry : args[++i].split(java.io.File.pathSeparator)) {
                        if (!entry.isBlank()) {
                            classPath.add(Path.of(entry));
                        }
                    }
                }
                default -> sources.add(Path.of(args[i]));
            }
        }
        if (sources.isEmpty()) {
            System.err.println("compile takes at least one .sou file");
            System.err.println(Usage.of(CliCommand.COMPILE));
            return 2;
        }
        List<Located> warnings = new ArrayList<>();
        try {
            Map<String, ClassFileImage> classes =
                    compiledClasses(sources, classPath, warnings, measure);
            // Before the written files: the warnings are about the source, and a long list of paths
            // between them and the command would bury them.
            report(warnings, sources, render);
            if (refuseWarnings && !warnings.isEmpty()) {
                return refused(render);
            }
            for (Path file : writeClasses(classes, outDir)) {
                System.out.println("wrote " + file);
            }
            return 0;
        } catch (CompileException e) {
            reportCompileError(e, sources, render);
            return 1;
        } catch (IOException e) {
            // The compile itself finished — these warnings are the whole set, and what stopped the
            // command was writing the classes out, which says nothing about the source.
            report(warnings, sources, render);
            System.err.println("io error: " + e.getMessage());
            return 1;
        }
    }

    /**
     * {@code souther init [<groupId>:<artifactId>]}: writes a project, or adds Souther to one.
     *
     * <p>Through the same flag extraction as every other command that answers a reader, so that what
     * it writes is in the language the line asked for, and a language tag that names nothing is
     * refused here the way it is everywhere else. No other rendering flag is one of this command's:
     * what it writes is a list of files rather than a diagnostic.
     */
    private static int initSubcommand(String[] rawArgs) {
        RenderOptions render = new RenderOptions();
        String[] args = render.extract(rawArgs);
        if (args == null) {
            return 2;
        }
        return InitCommand.run(args, render.locale(), System.out, System.err);
    }

    /**
     * {@code souther examples <file.sou>...}: how well the model's {@code example}s cover it.
     *
     * <p>Its own command rather than a flag on {@code compile}, because the two answer different
     * questions. {@code compile} writes classes out and stops at the first error; this asks how much
     * of a model the rows have pinned down, which is a question about what was observed and not
     * about whether anything can be written out. Mixing them would put a report on stdout next to
     * {@code wrote <path>} and make a failing build the only way to see it.
     *
     * <p>So the three things this answers with are decided apart. The report goes to stdout wherever
     * there is a subject to report on; the diagnostics go to stderr as they always did; the exit code
     * says whether the command succeeded, and an error still means it did not. Reading the report off
     * a compile that had to survive every error is what left the whole {@code incompleteness} channel
     * unreachable — all but one of the reasons that field carries arrive with a diagnostic beside
     * them, so the fail-fast path hid the whole of it.
     */
    private static int examplesSubcommand(String[] rawArgs) {
        RenderOptions render = new RenderOptions();
        String[] args = render.extract(rawArgs);
        if (args == null) {
            return 2;
        }
        List<Path> sources = new ArrayList<>();
        List<Path> classPath = new ArrayList<>();
        String module = null;
        String behavior = null;
        boolean strict = false;
        boolean generate = false;
        // The report is this command's whole output, so everything is measured and nothing is said
        // twice: what the warnings would say, the report says in one place. Measuring is not a
        // choice this command makes, and the bar it is read against is `--adequacy`'s — the same
        // word a compile picks a bar with, meaning the same bar. Left unsaid it is the whole of
        // what the syllabus asks for.
        //
        // `--strict` decides the exit status of the verdict below and no more. It names no bar,
        // which is what keeps the report a reader is given the same whether or not it was written:
        // a flag that chose a bar would change which findings the report marks, and the two runs a
        // reader compares would be reports of two different questions.
        Adequacy.Asked measure = Adequacy.Asked.fullReport();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-cp", "--class-path" -> {
                    for (String entry : args[++i].split(java.io.File.pathSeparator)) {
                        if (!entry.isBlank()) {
                            classPath.add(Path.of(entry));
                        }
                    }
                }
                case "--module" -> module = Reserved.name(args[++i]);   // a name from outside
                case "--behavior" -> behavior = Reserved.name(args[++i]);   // a name from outside
                case "--adequacy" -> {
                    Adequacy.AdequacyBar bar = barNamed(args[++i]);
                    if (bar == null) {
                        // The levels are turned down in the same words they are offered in
                        // elsewhere. This command measures everything, so `off` and `witness` name
                        // no state it has — and a reader who wrote one is told what this option
                        // takes here rather than that the word does not exist.
                        System.err.println(
                                "`--adequacy` takes reliable-domain or classes for `examples`,"
                                        + " whose output is the report and which measures"
                                        + " everything either way");
                        return 2;
                    }
                    measure = Adequacy.Asked.fullReport(bar);
                }
                case "--generate" -> generate = true;
                case "--strict" -> strict = true;
                default -> sources.add(Path.of(args[i]));
            }
        }
        if (sources.isEmpty()) {
            System.err.println("examples takes at least one .sou file");
            System.err.println(Usage.of(CliCommand.EXAMPLES));
            return 2;
        }
        List<Located> warnings = new ArrayList<>();
        try {
            List<String> texts = new ArrayList<>();
            for (Path source : sources) {
                texts.add(Files.readString(source));
            }
            ModulePath path = classPath.isEmpty() ? ModulePath.EMPTY
                    : ModulePath.ofClassPath(classPath);
            // The analysing entry points, not the compiling ones. What the rows cover is a question
            // this command answers from whatever was observed, and taking the compilation from an
            // entry point that raises the first error made every failure the report describes the
            // one thing that stopped it being written.
            Compilation compilation = texts.size() == 1 && classPath.isEmpty()
                    ? Compiler.analyzed(texts.get(0), Runner.moduleName(sources.get(0)), warnings,
                            measure)
                    : Compiler.analyzedModules(texts, path, warnings, measure);
            // Said first, and whatever the command answers with after. What is wrong with the source
            // is the same news whether the rest of this reports, refuses, or succeeds.
            List<Located> errors = compilation.errors();
            List<Located> said = new ArrayList<>(errors);
            said.addAll(warnings);
            report(said, sources, render);
            // Before the report, because a name that names nothing is not something a report can
            // say. Filtering one after the fact leaves an empty document whose every word is about
            // what was measured, and nothing measured anything.
            Selection.Refusal refused = new Selection(module, behavior).unresolved(compilation);
            if (refused != null) {
                System.err.println(Messages.get(refused.key(), render.locale(), refused.args()));
                return 2;
            }
            AdequacyReport assessed = AdequacyReport.of(compilation);
            // Whether there is anything to report on is a question about the compilation, and what
            // the report shows is a question about the selection. They are asked of different things
            // — this of everything that formed, `only` of what was named — so that neither can come
            // to stand for the other. That is the shape of the defect this command had.
            boolean assessable = !assessed.modules().isEmpty();
            AdequacyReport report = assessed.only(module, behavior);
            if (assessable) {
                SourceRendering rendering =
                        new SourceRendering(namesOf(sources), compilation.texts());
                String rendered = render.json()
                        ? report.json(rendering) + System.lineSeparator()
                        : report.human(rendering);
                System.out.print(rendered);
                // After the report, because the rows are what to do about what the report just said.
                // Beside it rather than in it where the report is JSON: the rows are source, and
                // source in the middle of a JSON document is not a document.
                if (generate) {
                    String rows = GeneratedRows.of(compilation, module, behavior, rendering).text();
                    (render.json() ? System.err : System.out).print(rows);
                }
            }
            // The report is written either way; whether the command succeeded is a separate answer,
            // and an error is an error whatever could still be said about the rows.
            if (!errors.isEmpty()) {
                return 1;
            }
            // What the report just named, and nothing else. A row waiting for a `let` is not one of
            // them: waiting is the normal state of a model being written, and a gate on it refuses the
            // record of what an injected behavior owes — which is the thing the report exists to keep.
            if (strict && report.adequacy() == AdequacyReport.AdequacyStatus.NOT_SATISFIED) {
                // Said in the terms of the report the reader was given. A count is only worth
                // anything beside a way of finding the entries it counts, and the two surfaces name
                // them differently: a person reads a mark and a consumer reads a field. Pointing at
                // the mark whatever was printed sent a reader of a JSON document looking for a
                // character that is nowhere in it.
                System.err.println(Messages.get(render.json()
                                ? "cli.examples.strict.refused.json"
                                : "cli.examples.strict.refused",
                        render.locale(), report.adequacyGaps().size()));
                return 1;
            }
            return 0;
        } catch (CompileException e) {
            reportCompileError(e, sources, render);
            return 1;
        } catch (IOException e) {
            System.err.println("io error: " + e.getMessage());
            return 1;
        }
    }

    /**
     * {@code souther mcp}: serves doc, api and japi over MCP stdio, and is written on its own.
     *
     * <p>Said here rather than left to the check above the dispatch, because what the check lets
     * through is a permission this command cannot honour. A token that reads as a short option
     * another command owns is passed on as an operand — a file may be named {@code -d}, and the
     * command that has no {@code -d} reads it as one — and that is sound wherever there is an
     * operand for it to be. There is none here. This command was handed its arguments and read none
     * of them, so {@code souther mcp -w} served as though nothing had been written, and so did
     * {@code souther mcp model.sou}. What a command takes no arguments at all is a fact about its
     * own grammar, which is where it is now stated.
     */
    private static int mcpSubcommand(String[] args) {
        if (args.length > 0) {
            System.err.println(Messages.get("cli.mcp.arguments",
                    RenderOptions.asking(null).locale(), String.join(", ", args)));
            System.err.println(Usage.of(CliCommand.MCP));
            return 2;
        }
        return McpServer.serve(System.in, System.out);
    }

    /**
     * {@code souther lsp}: serves the language server over LSP stdio, and takes no arguments for
     * the reason {@code mcp} takes none.
     *
     * <p>The same server an editor launches from the shaded jar, in this process. A protocol on
     * stdio has the whole of stdout, so nothing this command line writes for a reader may be
     * written under it, and the refusal above goes to stderr like every other one.
     */
    private static int lspSubcommand(String[] args) {
        if (args.length > 0) {
            System.err.println(Messages.get("cli.lsp.arguments",
                    RenderOptions.asking(null).locale(), String.join(", ", args)));
            System.err.println(Usage.of(CliCommand.LSP));
            return 2;
        }
        return LspServer.serve(System.in, System.out);
    }

    /**
     * {@code souther help [<command>]}: what this command line takes, asked rather than refused.
     *
     * <p>On stdout under a zero exit code, which is the whole point of it being a command. What it
     * writes was reachable only from a failure before — no command, an unknown one, a missing
     * argument — so it went to stderr every time and the exit code said the line was wrong. An
     * author who wanted to read it had to write a line they knew this compiler would refuse, and a
     * reader piping the answer anywhere got an empty pipe.
     */
    private static int helpSubcommand(String[] args) {
        Locale locale = RenderOptions.asking(null).locale();
        if (args.length == 0) {
            System.out.println(Usage.all());
            return 0;
        }
        if (args.length > 1) {
            System.err.println(Messages.get("cli.help.arguments", locale,
                    String.join(", ", args)));
            System.err.println(Usage.of(CliCommand.HELP));
            return 2;
        }
        CliCommand about = helpTarget(args);
        if (about == null) {
            // Not answered with the listing as though nothing had been asked: a word that is no
            // command of this compiler's is a thing its author believes exists, and being shown
            // every command without being told that is not one of them leaves them looking for it.
            System.err.println(Messages.get("cli.help.command", locale, args[0]));
            System.err.println(Usage.all());
            return 2;
        }
        System.out.println(Usage.of(about));
        return 0;
    }

    /**
     * The command {@code souther help} was asked about, or null where its argument names none.
     *
     * <p>Of a line that names one: the argument is a command name, and a line that wrote no name or
     * wrote two has not named a command to answer about. Which of those it is stays with the
     * command that has to say so.
     */
    static CliCommand helpTarget(String[] args) {
        return args.length == 1 ? CliCommand.named(args[0]) : null;
    }

    /**
     * What {@code --adequacy} names, or null where it names none.
     *
     * <p>A preset and not a level. Each of these says both how much to measure and what the build is
     * held to, and the two are not one dial: the points a row is owed away from a line are measured
     * whenever the ones against it are, so what {@code reliable-domain} adds over {@code all} is a
     * bar and not a measurement (issue #937).
     */
    private static Adequacy.Asked adequacyAsked(String written) {
        Adequacy.AdequacyBar bar = barNamed(written);
        if (bar != null) {
            // A bar names no level, and every bar wants everything measured: what a bar adds over
            // `all` is what a build refuses over and never what was looked at (issue #937).
            return Adequacy.Asked.warningsAt(Adequacy.Level.ALL, bar);
        }
        return switch (written) {
            case "off" -> Adequacy.Asked.warningsAt(Adequacy.Level.OFF);
            case "witness" -> Adequacy.Asked.warningsAt(Adequacy.Level.WITNESS);
            case "all" -> Adequacy.Asked.warningsAt(Adequacy.Level.ALL);
            default -> null;
        };
    }

    /**
     * The bar {@code written} names, or null where it names none.
     *
     * <p>One reading of these words for both commands that take them. What {@code classes} means
     * is a bar and nothing else, so a compile and a report holding a model to it are holding it to
     * the same thing — and the word said twice is two tables free to disagree about the one thing
     * a reader picked it for.
     *
     * <p>The levels are not here. {@code off}, {@code witness} and {@code all} say how much to
     * measure, which is a question {@code souther examples} does not ask: its output is the report,
     * so everything is measured and there is nothing for those words to choose.
     */
    private static Adequacy.AdequacyBar barNamed(String written) {
        return switch (written) {
            case "reliable-domain" -> Adequacy.AdequacyBar.RELIABLE_DOMAIN;
            case "classes" -> Adequacy.AdequacyBar.CLASSES;
            default -> null;
        };
    }

    /**
     * {@code souther fmt <file.sou>... [-w] [--check]}: rewrites each file into its canonical form
     * (see {@link Formatter}). With no flag the formatted source is printed to stdout; {@code -w}
     * writes it back in place; {@code --check} writes no file and exits 1 if any file is not already
     * formatted, printing each one's difference from its canonical form. A file with a syntax error
     * is reported and left untouched.
     *
     * <p>The difference is printed as the file is judged rather than gathered for the end. The
     * canonical form is what the verdict is taken against, so a gate that answered with the file's
     * name alone had computed the whole of what the reader needed and dropped it — leaving them to
     * write the file over with {@code -w} against a copy, or run the formatter again into a scratch
     * file, to be told what the gate already knew.
     */
    private static int fmtSubcommand(String[] args) {
        // Through the CLI's one resolution rather than beside it: a syntax error is the one thing
        // `fmt` says to a reader, and the language it says it in is the same policy as everywhere
        // else this command answers in.
        Locale locale = new RenderOptions().locale();
        boolean write = false;
        boolean check = false;
        List<Path> files = new ArrayList<>();
        for (String a : args) {
            switch (a) {
                case "-w", "--write" -> write = true;
                case "--check" -> check = true;
                default -> files.add(Path.of(a));
            }
        }
        if (files.isEmpty()) {
            System.err.println("fmt takes at least one .sou file");
            System.err.println(Usage.of(CliCommand.FMT));
            return 2;
        }
        if (!write && !check && files.size() > 1) {
            // Concatenating several files to stdout gives an unattributable blob; make the intent explicit.
            System.err.println("formatting multiple files needs `-w` (write in place) or `--check`");
            return 2;
        }
        boolean unformatted = false;
        boolean failed = false;
        for (Path file : files) {
            String source;
            try {
                source = Files.readString(file);
            } catch (IOException e) {
                System.err.println("io error: " + file + ": " + e.getMessage());
                failed = true;
                continue;
            }
            String formatted;
            try {
                CstParser.Result parsed = CstParser.parse(source);
                if (!parsed.errors().isEmpty()) {
                    CstError<?> first = parsed.errors().get(0);
                    System.err.println(file + ": syntax error: "
                            + Messages.render(first.said(), locale));
                    failed = true;
                    continue;   // the formatter assumes a clean parse; leave a broken file untouched
                }
                formatted = Formatter.format(parsed.root());
            } catch (CompileException e) {
                // One file this command cannot read must cost that file, not the run and not the
                // author's screen. Reading is a walk down the source and a walk down the tree it
                // made, and either can find a limit; a `fmt` that answered those with a stack trace
                // told the author about the compiler rather than about their file.
                System.err.println(file + ": " + e.getMessage());
                failed = true;
                continue;
            } catch (RuntimeException e) {
                System.err.println(file + ": " + internalFailure(e));
                failed = true;
                continue;
            }
            if (check) {
                if (!formatted.equals(source)) {
                    unformatted = true;
                    // Here, where both texts are still in hand. Nothing is kept for the end: what the
                    // run has to remember about a file it has judged is that one of them differed.
                    //
                    // What is printed is the decisions and not the diff. A diff says which lines
                    // are not the canonical form's; a reader who wants to write the canonical form
                    // by hand needs to know which rule each of them answers to.
                    Deviations.Report report;
                    try {
                        report = Deviations.of(source);
                    } catch (RuntimeException e) {
                        // A rule that could not answer says so and is left out of the report. This
                        // is anything else: a defect, and one this file is told about rather than
                        // being judged as though the rules had been asked.
                        System.err.println(file + ": " + internalFailure(e));
                        System.out.print(UnifiedDiff.of(file.toString(), file + " (formatted)",
                                source, formatted));
                        failed = true;
                        continue;
                    }
                    for (Deviations.Deviation d : report.deviations()) {
                        System.out.println(file + ":" + d.line() + ":" + d.column() + ": "
                                + d.rule());
                        System.out.println("    the canonical form: " + d.canonical()
                                + "; this source: " + d.source());
                    }
                    if (!report.whole()) {
                        // Said rather than left out, and shown. A list that named what it can and
                        // stopped reads as a file with these deviations and no others, and a
                        // reader still has to see the rest of what differs.
                        System.out.println(file + ": and more that no rule accounts for yet:");
                        System.out.print(UnifiedDiff.of(file.toString(), file + " (formatted)",
                                source, formatted));
                    }
                }
            } else if (write) {
                if (!formatted.equals(source)) {
                    try {
                        Files.writeString(file, formatted);
                    } catch (IOException e) {
                        System.err.println("io error: " + file + ": " + e.getMessage());
                        failed = true;
                    }
                }
            } else {
                System.out.print(formatted);
            }
        }
        return failed || unformatted ? 1 : 0;
    }

    /** {@code souther run <file.sou> [-cp <path>] [--behavior <name>] [--input <json>]}: compiles the
     * file in memory against the modules on the path and drives one behavior, printing its output as
     * JSON (see {@link Runner}). */
    private static int runSubcommand(String[] rawArgs) {
        RenderOptions render = new RenderOptions();
        String[] args = render.extract(rawArgs);
        if (args == null) {
            return 2;
        }
        Path source = firstSource(args);
        List<Path> sources = source == null ? List.of() : List.of(source);
        List<Located> warnings = new ArrayList<>();
        try {
            String output = Runner.runCli(args, warnings);
            // The warnings go to stderr and the behavior's output to stdout, so a caller piping the
            // result reads JSON and nothing else.
            report(warnings, sources, render);
            System.out.println(output);
            return 0;
        } catch (Runner.RunException e) {
            // The compile finished before the run began, so these warnings are the whole set — and a
            // run that aborted on an invariant is where a warning that the construction was unproven
            // is worth most. A usage error is raised before any of it and carries none.
            report(warnings, sources, render);
            System.err.println(e.localized(render.locale()));
            return e.exitCode;
        } catch (CompileException e) {
            reportCompileError(e, sources, render);
            sayHowRunReachesAModule(e, render);
            return 1;
        }
    }

    /**
     * Says how {@code run} is given a module to resolve against, where that is what the reader is
     * missing.
     *
     * <p>Beside the diagnostic and not inside it. E1504 states what the compile observed — that the
     * module is in neither the sources nor the path — and that is true of every compile, however it
     * was started. Which option widens the path is a fact about this command line, so a diagnostic
     * carrying it would be the compiler holding the command line's knowledge, and would be carried by
     * every other caller of the same compile.
     *
     * <p>Once per run rather than per diagnostic. An error carrying several is what a pass reporting
     * each of its independent failures produces, and however many of them went unresolved there is
     * one thing to do about it. And it says what the option is for rather than that it would fix this
     * import — a misspelled module name is the same E1504, and a line promising that a class path
     * resolves it would send that author to build something they do not need.
     *
     * <p>Not under {@code --format json}, for the reason {@link #refused} is not: a reader taking one
     * object per line cannot tell a sentence among them from output it should have understood. A
     * remediation JSON carries is a field of the diagnostic schema, which is a thing to design rather
     * than a line to print.
     */
    private static void sayHowRunReachesAModule(CompileException e, RenderOptions render) {
        if (render.json()) {
            return;
        }
        for (Diagnostic d : e.diagnostics()) {
            if (DiagnosticCode.E1504.name().equals(d.code())) {
                Locale locale = render.locale();
                System.err.println(Messages.get("diag.hint.label", locale) + " "
                        + Messages.get("cli.run.classpath", locale));
                return;
            }
        }
    }

    /**
     * The file whose line the error should quote: the only source of a single-file compile, or — when
     * several were linked — the one the compiler was working on, which it tags the error with. Null
     * when a multi-file error names no source, so the snippet is left out rather than quoting a line
     * from the wrong file.
     */
    static Path sourceOf(List<Path> sources, CompileException e) {
        return sourceOf(sources, e, 0);
    }

    /** The file the {@code i}-th diagnostic should quote. */
    static Path sourceOf(List<Path> sources, CompileException e, int i) {
        return pathOf(sources, e.sourceIdOf(i));
    }

    /**
     * Which of the files handed over a source id names, or null when it names none of them.
     *
     * <p>Matched on the id, like everything else this command resolves. A report says which source
     * it points into, so a caller with one file to hand has nothing to guess at and a caller with
     * several has nothing to work out.
     */
    private static Path pathOf(List<Path> sources, SourceId sourceId) {
        int at = indexOf(sources, sourceId);
        return at < 0 ? null : sources.get(at);
    }

    /** What to quote for each source a diagnostic points into, read once per file, under names no
     *  two of these files share. */
    private static SourceContextResolver sourcesOf(List<Path> sources) {
        List<String> names = displayNames(sources);
        return SourceContextResolver.memoized(id -> {
            int at = indexOf(sources, id);
            return at < 0 ? null : read(sources.get(at), names.get(at));
        });
    }

    /**
     * What to call each source a report names, which is what a diagnostic calls it.
     *
     * <p>The report identifies a source by the id this command handed it over as — a position in the
     * list — and the name for one is this command's answer, since only it knows which files are in
     * front of the reader. Both renderings go through {@link #displayNames}, so a run cannot quote a
     * line from {@code a/model.sou} and then say the rows of {@code model.sou} were not read.
     *
     * <p>Matched on the id and nothing else, as {@link #indexOf} is. An id that is none of these
     * files is an id about a source this command did not hand over, and answering with the only file
     * there happens to be would be inventing the very correspondence this is here to stop being
     * guessed at.
     */
    static SourceNameResolver namesOf(List<Path> sources) {
        List<String> names = displayNames(sources);
        return id -> {
            for (int i = 0; i < sources.size(); i++) {
                if (Compilation.idOfSourceIndex(i).equals(id)) {
                    return names.get(i);
                }
            }
            return id.value();
        };
    }

    /** What each of these files is called in front of a reader, in the order they were given. */
    private static List<String> displayNames(List<Path> sources) {
        return SourceNames.of(sources.stream().map(Path::toString).toList());
    }

    /**
     * Which of the files handed over a source id names, or -1 when it names none of them.
     *
     * <p>On the id and nothing else. The one file handed over used to be the answer for whatever it
     * was asked, including for no id at all, because a report could arrive here without one and the
     * single file was the only guess to be had. A report says which source it points into now, so
     * there is nothing left to guess — and the guess was answering for reports it had no business
     * answering for: handed one file and a report about another, it quoted that file at the
     * report's numbers, which put a caret past the end of a line the author never wrote.
     */
    private static int indexOf(List<Path> sources, SourceId sourceId) {
        for (int i = 0; i < sources.size(); i++) {
            if (Compilation.idOfSourceIndex(i).equals(sourceId)) {
                return i;
            }
        }
        return -1;
    }

    /** A file as a snippet source under the name a reader is shown it by, or null when it cannot be
     *  read — a snippet-less rendering is the honest fallback. */
    private static SourceContext read(Path source, String name) {
        if (source == null) {
            return null;
        }
        try {
            String text = Files.readString(source);
            return new SourceContext(name, text, SourceLayout.of(text));
        } catch (IOException _) {
            return null;
        }
    }

    /** Renders a compile error: an Elm-style snippet (or JSON) in the chosen locale, or the legacy
     * one-line form when the error is not yet structured. An error that carries several diagnostics
     * — every failing {@code example} row — prints each, so none of the reasons is lost. */
    private static void reportCompileError(CompileException e, List<Path> sources, RenderOptions render) {
        if (e.diagnostic() == null) {
            System.err.println(e.getMessage());
            return;
        }
        report(e.locatedDiagnostics(), sources, render);
    }

    /**
     * Says that the warnings just printed are what stopped this build, and answers with the exit code
     * for it.
     *
     * <p>Not under {@code --format json}. What that format writes is one object per diagnostic with
     * nothing around them, so a reader takes the output a line at a time; a sentence among them is a
     * line that parses as nothing, and a reader given it cannot tell that from output it should have
     * understood. There the exit code is what says it, which is what a tool reads anyway.
     */
    private static int refused(RenderOptions render) {
        if (!render.json()) {
            System.err.println(Messages.get("cli.warnings.refused", render.locale()));
        }
        return 1;
    }

    /** Prints each diagnostic to stderr as the chosen renderer renders it, quoting the file it
     * belongs to. Errors and warnings take the same path; only where they come from differs. */
    private static void report(List<Located> located, List<Path> sources, RenderOptions render) {
        Locale locale = render.locale();
        DiagnosticRenderer renderer = render.json()
                ? new JsonRenderer() : new HumanRenderer(render.useColor());
        for (String line : DiagnosticRenderer.renderAll(
                located, sourcesOf(sources), renderer, locale)) {
            System.err.println(line);
        }
    }

    /**
     * The first non-option argument of {@code run} — the source file, for the error snippet.
     *
     * <p>Every option that takes a value is skipped with it, including the short one: {@code -cp} is
     * a path and not a file to quote, and reading it as one made the snippet name a file the author
     * never wrote.
     */
    private static Path firstSource(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.equals("--behavior") || a.equals("--input")
                    || a.equals("-cp") || a.equals("--class-path")) {
                i++;
            } else if (!a.startsWith("--")) {
                return Path.of(a);
            }
        }
        return null;
    }

    /**
     * Rendering flags shared by both subcommands, and the extraction that strips them out.
     *
     * <p>Which of them a run has a reader for is decided here and not in {@link CliOption}. What that
     * table states is which options may appear beside which — an option's owner, the option it needs,
     * the option it excludes — and each of those holds of the tokens as written, before anything is
     * made of them. That {@code --color} goes unread under {@code --format json} holds of what the
     * values mean: it is the JSON renderer, chosen by one option's value, that is not a reader of the
     * other. Written as a row in that table it would read as a fact about the two options being
     * written together, which is not the fact — the same two options under {@code --format human} are
     * the line that was meant.
     *
     * <p>What values each of them has is decided here for the same reason, and was decided nowhere
     * at all: these three arrived as strings and were matched where they were read, so a value none
     * of the arms matched fell to the default and the run went on under the value that was in force
     * anyway. {@code --format jsn} rendered a snippet and answered a caller asking for JSON with the
     * exit code the JSON run gives. Each command's own options already refuse a value they do not
     * have — {@code --warnings}, {@code --adequacy} — and these are the ones that did not.
     */
    private static final class RenderOptions {

        /** The values {@code --format} has, in the order the usage text writes them. */
        private static final List<String> FORMATS = List.of("human", "json");

        /** The values {@code --color} has, in the order the usage text writes them. */
        private static final List<String> COLORS = List.of("auto", "always", "never");

        private String format = "human";
        private String lang = null;
        private String color = "auto";

        /**
         * How the line wrote {@code --color}, or null where it did not write it.
         *
         * <p>Beside the value rather than read back out of it. The value a line writes and the value
         * in force where nobody wrote one are the same string — {@code auto} is the default — and
         * they are not the same statement: one line has asked for a colour policy and the other has
         * not. Recovering that from the value is the loss this class had, one option along.
         */
        private String colorAsWritten = null;

        boolean json() {
            return "json".equals(format);
        }

        /**
         * These flags as a line that wrote {@code --lang} and nothing else, for the refusal raised
         * before any subcommand has parsed its arguments.
         *
         * <p>Through here rather than resolving beside it. A usage error is one of the answers this
         * command line gives, and which language it is written in is this class's question — asking
         * it twice is two policies that happen to agree.
         */
        static RenderOptions asking(String lang) {
            RenderOptions options = new RenderOptions();
            options.lang = lang;
            return options;
        }

        /**
         * What named the language for this invocation: the option where the line wrote it, and
         * {@code SOUTHER_LANG} where the line did not.
         *
         * <p>Whether the option was written is what this class has and the value alone does not.
         * {@code lang} is null until {@code --lang} is read, so a blank value is a line writing one
         * where a language goes rather than a line leaving the option out — and the environment is
         * outranked by it either way. Reading the two alike is what sent {@code --lang ''} to the
         * shell to be answered in a language the line did not ask for.
         */
        private Messages.Named named() {
            return lang != null ? new Messages.Named(lang, false) : Messages.namedLanguage(null);
        }

        /**
         * The language this invocation answers in: the {@code --lang} value if one was written,
         * then {@code SOUTHER_LANG}, then the default.
         *
         * <p>The CLI's policy, in the CLI, and asked here by everything that prints. A place that
         * resolves for itself is a second answer that happens to agree, with nothing saying it has
         * to.
         */
        Locale locale() {
            return Messages.resolveLocale(named());
        }

        boolean useColor() {
            return switch (color) {
                case "always" -> true;
                case "never" -> false;
                // Two things have to hold, and the check below knows only the second. A runtime
                // built without a console provider answers none at all, so the null stands; and
                // where there is one it exists whether or not the output is a terminal, so it is
                // asked which — colour written into a pipe is escape codes in a file.
                default -> System.console() instanceof Console c && c.isTerminal()
                        && System.getenv("NO_COLOR") == null;
            };
        }

        /**
         * The arguments with these flags taken out, or null where what they say refuses the line —
         * said here, so that a command that has one of these has the refusal too.
         *
         * <p>Each of them has its value where this reads one: an option written without one is
         * refused before any command is run, against the same table this walk agrees with.
         */
        String[] extract(String[] args) {
            List<String> kept = new ArrayList<>();
            for (int i = 0; i < args.length; i++) {
                String option = args[i];
                switch (option) {
                    case "--format" -> format = args[++i];
                    case "--lang" -> lang = args[++i];
                    case "--color" -> {
                        colorAsWritten = option;
                        color = args[++i];
                    }
                    default -> kept.add(option);
                }
            }
            String refusal = refusal();
            if (refusal != null) {
                System.err.println(refusal);
                return null;
            }
            return kept.toArray(new String[0]);
        }

        /**
         * Why these flags refuse the line, or null where they do not.
         *
         * <p>Asked after the whole walk rather than where each value is read, so that a refusal is
         * written in the language the line asked for wherever on it {@code --lang} was written.
         *
         * <p>What the value is before what it is read with. An option given a value it does not have
         * is told that first: {@code --format jsn --color always} is a line whose author wrote a
         * format this compiler has no such thing as, and answering it with what {@code --color} goes
         * unread under would be answering a question about a line they did not write.
         *
         * <p>And the language before either, being the one every other answer would be written in.
         * This refusal is written in whatever the tag it refuses resolves to and never in the
         * language the line outranked: {@code ja_JP.UTF-8} is answered in Japanese, and a tag with
         * nothing readable in front of it in the default — including a blank one, where reaching for
         * the environment would be the fallback this refusal exists to stop.
         */
        private String refusal() {
            Messages.Named named = named();
            if (named != null && !Messages.namesALanguage(named.tag())) {
                return Messages.get(named.fromEnvironment() ? "cli.lang.environment" : "cli.lang.tag",
                        locale(), named.tag());
            }
            if (!FORMATS.contains(format)) {
                return outsideItsValues("--format", format, FORMATS);
            }
            if (!COLORS.contains(color)) {
                return outsideItsValues("--color", color, COLORS);
            }
            if (json() && colorAsWritten != null) {
                return Messages.get("cli.option.unread", locale(),
                        colorAsWritten, "--format json");
            }
            return null;
        }

        /**
         * That the option has no such value, and which ones it has.
         *
         * <p>The values listed rather than described. What a reader does next is write one of them,
         * and a sentence about what the option means is not one of them.
         */
        private String outsideItsValues(String option, String written, List<String> values) {
            return Messages.get("cli.option.values", locale(),
                    option, String.join(", ", values), written);
        }
    }

    /**
     * Compiles the given source files together — a single file, or several linked through their
     * imports (spec §modules) — and writes each generated class under {@code outDir}. Returns the paths
     * written, in order.
     *
     * <p>This is the {@code compile} subcommand's own wiring, not a way to embed the compiler: it
     * hands its warnings to the caller to render rather than reporting them. Compiling from Java
     * goes through {@link Compiler} or the annotation processor, both of which say what they found.
     */
    static List<Path> compileToDir(List<Path> sources, Path outDir) throws IOException {
        return compileToDir(sources, outDir, List.of());
    }

    /**
     * As {@link #compileToDir(List, Path)}, resolving an import that names no module among
     * {@code sources} against the compiled modules on {@code classPath} — the directories and jars
     * of the projects this one depends on.
     */
    static List<Path> compileToDir(List<Path> sources, Path outDir, List<Path> classPath)
            throws IOException {
        return compileToDir(sources, outDir, classPath, new ArrayList<>());
    }

    /** As {@link #compileToDir(List, Path, List)}, collecting the compile's warnings into
     *  {@code warningsOut} for the caller to render — the CLI does, with the flags it was given. */
    static List<Path> compileToDir(List<Path> sources, Path outDir, List<Path> classPath,
                                   List<Located> warningsOut) throws IOException {
        return compileToDir(sources, outDir, classPath, warningsOut, Adequacy.Asked.NOTHING);
    }

    /** As above, warning about what the model's {@code example}s do not cover, at the level the
     * caller asked for. Off leaves the compile exactly as it was. */
    static List<Path> compileToDir(List<Path> sources, Path outDir, List<Path> classPath,
                                   List<Located> warningsOut, Adequacy.Asked measure)
            throws IOException {
        return writeClasses(compiledClasses(sources, classPath, warningsOut, measure), outDir);
    }

    /**
     * The classes the sources compile to, by binary name, with the compile's warnings collected into
     * {@code warningsOut}.
     *
     * <p>Held apart from writing them because a command may read the warnings before deciding whether
     * this build is one it accepts, and a build it does not accept writes nothing: the exit code says
     * the classes are not the output of an accepted compile, and a directory holding them anyway is
     * what a later step would read as if they were.
     */
    static Map<String, ClassFileImage> compiledClasses(List<Path> sources, List<Path> classPath,
                                               List<Located> warningsOut, Adequacy.Asked measure)
            throws IOException {
        List<String> texts = new ArrayList<>();
        for (Path source : sources) {
            texts.add(Files.readString(source));
        }
        ModulePath path = classPath.isEmpty() ? ModulePath.EMPTY : ModulePath.ofClassPath(classPath);
        // A single header-less file is named after the file (F#/Elm; ADR-0043); a multi-file build
        // links by imports, so each must declare its own module header. One file that imports another
        // project's module is a module set of one, not a self-contained module.
        List<Located> compileWarnings = new ArrayList<>();
        Compilation compilation = texts.size() == 1 && classPath.isEmpty()
                ? Compiler.compiled(texts.get(0), Runner.moduleName(sources.get(0)),
                        compileWarnings, measure)
                : Compiler.compiledModules(texts, path, compileWarnings, measure);
        warningsOut.addAll(compileWarnings);
        return compilation.classes();
    }

    /** Writes each class under {@code outDir}, and answers with the paths written, in order. */
    static List<Path> writeClasses(Map<String, ClassFileImage> classes, Path outDir)
            throws IOException {
        List<Path> written = new ArrayList<>();
        for (Map.Entry<String, ClassFileImage> entry : classes.entrySet()) {
            Path file = outDir.resolve(JvmClassName.classFile(entry.getKey()));
            Files.createDirectories(file.getParent());
            Files.write(file, entry.getValue().bytes());
            written.add(file);
        }
        return written;
    }

    private Main() {}
}
