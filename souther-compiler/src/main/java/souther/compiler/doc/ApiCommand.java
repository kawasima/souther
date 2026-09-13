package souther.compiler.doc;

import souther.compiler.DefaultStdlib;
import souther.compiler.Reserved;
import souther.compiler.stdlib.Stdlib;
import souther.compiler.types.ValueName;
import souther.compiler.ast.Hir;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.SequencedMap;

/**
 * {@code souther api}: the standard library's published surface, one name per line with the
 * signature the checker resolved. The listing answers "what is there and how is it called"; the
 * {@code --source} form prints a module's own {@code .sou} text, whose comments say why each
 * declaration is shaped the way it is.
 */
public final class ApiCommand {

    private ApiCommand() {}

    public static int run(String[] args, PrintStream out, PrintStream err) {
        return run(args, out, err, Caller.CLI);
    }

    static int run(String[] args, PrintStream out, PrintStream err, Caller caller) {
        // The boundary: a command that lists the library is not downstream of a compile, so this is
        // where the process's library is read. Everything below is handed the value.
        return run(args, out, err, caller, DefaultStdlib.get());
    }

    static int run(String[] args, PrintStream out, PrintStream err, Caller caller, Stdlib stdlib) {
        if (args.length == 0) {
            listPublished(out, null, stdlib);
            return 0;
        }
        // Which of the things this line asked for is the one being answered. The forms below read
        // the front of the line and nothing after it, so `api Option --source String` answered about
        // `Option` and dropped an option of its own without a word — a listing a reader has no way
        // to tell from the one they asked for. What `souther doc` already says, said here too.
        int reads = args[0].equals("--source") || args[0].equals("--search") ? 2 : 1;
        if (args.length > reads) {
            err.println("`souther api` reads one at a time; asked for "
                    + String.join(", ", List.of(args)) + " — reading `"
                    + String.join(" ", List.of(args).subList(0, reads)) + "`");
        }
        if (args[0].equals("--source")) {
            if (args.length < 2) {
                err.println("usage: souther api --source <Module>");
                return 2;
            }
            return printSource(args[1], out, err);
        }
        if (args[0].equals("--search")) {
            if (args.length < 2 || args[1].isBlank()) {
                err.println("`--search` needs a term to look for");
                err.println("usage: souther api --search <term>");
                return 2;
            }
            String needle = args[1].toLowerCase(Locale.ROOT);
            List<String> found = surface(stdlib).entrySet().stream()
                    .filter(e -> e.getKey().toLowerCase(Locale.ROOT).contains(needle))
                    .map(e -> line(e.getKey(), e.getValue()))
                    .toList();
            if (found.isEmpty()) {
                err.println("no stdlib name contains `" + args[1] + "`");
                return 0;
            }
            found.forEach(out::println);
            return 0;
        }
        String asked = args[0];
        if (asked.contains(".")) {
            Signature signature = surface(stdlib).get(asked);
            if (signature == null) {
                err.println("no stdlib declaration `" + asked + "`");
                return 2;
            }
            out.println(line(asked, signature));
            // A signature says what to pass, not what it means — whether a span counts both ends,
            // whether a division aborts or answers a case. That is in the module's own source.
            err.println(caller.stdlibSource(asked.substring(0, asked.indexOf('.'))));
            return 0;
        }
        if (!Reserved.isQualifier(asked)) {
            err.println("no stdlib module `" + asked + "`");
            err.println("modules: " + String.join(", ", Reserved.QUALIFIERS.stream().sorted().toList()));
            return 2;
        }
        listPublished(out, asked + ".", stdlib);
        return 0;
    }

    /** One published name's parameters, as written, and the type it answers with. A name declaring
     *  none is a value rather than a function of no arguments. The return is carried as the
     *  declaration wrote it beside the type it resolved to, because a result of more than one case
     *  is published in the order it was written ({@link PublishedCaseOrder}) and a type does not
     *  hold that. */
    record Signature(List<String> paramNames, List<Type> paramTypes, Type result,
                     Hir.RetType declaredReturn) {}

    private static void listPublished(PrintStream out, String prefix, Stdlib stdlib) {
        surface(stdlib).forEach((name, signature) -> {
            if (prefix == null || name.startsWith(prefix)) {
                out.println(line(name, signature));
            }
        });
    }

    /**
     * Every name a program may write, in module order.
     *
     * <p>This is the surface a reader writes, not the declared one: a name written as sugar over a
     * private helper — {@code List.fold}, which the checker rewrites to {@code List.foldFrom(…, 0)}
     * — is what the specification tells a reader to call, so it is listed under its own name and
     * with only the arguments its caller writes. Leaving it out would have this command contradict the
     * specification about what exists.
     *
     * <p>Which names those are and what order they come in are both {@link Stdlib#published()}'s
     * answer, walked here rather than rebuilt: a listing assembled from the declarations and then
     * the rewrites puts every sugar after every module, whichever module it reads as. What each
     * name's signature comes from is this command's own question, and the only one it decides.
     *
     * <p>Answered as something that has an order, because a reader is given these one after another.
     * A map that only said which names are on the surface would be one every caller had to walk in
     * whatever it walked in, and the order a reader is shown would be a fact about how this was
     * built rather than the one that was asked for.
     */
    static SequencedMap<String, Signature> surface(Stdlib stdlib) {
        SequencedMap<String, Signature> surface = new LinkedHashMap<>();
        for (String name : stdlib.published()) {
            // A published name is a spelling, and the library is what turns one into the operation
            // it reaches. Everything below is asked with that operation.
            ValueName.Stdlib.Operation operation = stdlib.operation(name);
            if (operation == null) {
                continue;
            }
            Stdlib.Rewrite rewrite = stdlib.rewriteOf(operation);
            if (rewrite != null) {
                Stdlib.Entry target = stdlib.entry(rewrite.target());
                if (target != null) {
                    surface.put(name, declared(target, rewrite.keptArgs()));
                }
                continue;
            }
            Stdlib.Entry entry = stdlib.entry(operation);
            if (entry != null) {
                surface.put(name, declared(entry, entry.signature().params().size()));
            }
        }
        return surface;
    }

    private static Signature declared(Stdlib.Entry entry, int arity) {
        List<Hir.FnParam> params = entry.declaration().params();
        List<Type> types = entry.signature().params();
        List<String> names = new ArrayList<>();
        List<Type> kept = new ArrayList<>();
        for (int i = 0; i < arity && i < params.size(); i++) {
            names.add(params.get(i).binder().name());
            kept.add(types.get(i));
        }
        return new Signature(names, kept, entry.signature().result(),
                entry.declaration().declaredReturn());
    }

    /**
     * One name as its caller writes it. A parameter list is written where the declaration declares
     * one; a declaration with none is a value, which is named where the value it stands for would go
     * and refused where it is applied (E1803). No parameters is that case and only that case: the
     * parameter list is what tells a function from a value, and an empty {@code ()} is refused
     * rather than being a second spelling of either.
     */
    private static String line(String qualifiedName, Signature signature) {
        StringBuilder sb = new StringBuilder(qualifiedName);
        if (!signature.paramNames().isEmpty()) {
            sb.append("(");
            for (int i = 0; i < signature.paramNames().size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(signature.paramNames().get(i)).append(": ")
                        .append(Type.show(signature.paramTypes().get(i)));
            }
            sb.append(")");
        }
        if (signature.result() != null) {
            sb.append(" : ").append(result(signature));
        }
        return sb.toString();
    }

    /**
     * The type a published name answers with, written the way its declaration writes it.
     *
     * <p>Which differs from {@link Type#show} for a result of more than one case and for nothing
     * else. What is published here is a declaration — the parameter names above are the
     * declaration's too — so a result somebody wrote as {@code Int | DivisionByZero} is published
     * that way round, and not in the order a set of names is shown in.
     */
    static String result(Signature signature) {
        if (!(signature.result() instanceof Type.Union union)) {
            return Type.show(signature.result());
        }
        return PublishedCaseOrder.asDeclared(union.members(), signature.declaredReturn()).stream()
                .map(TypeSymbol::name).collect(java.util.stream.Collectors.joining(" | "));
    }

    private static int printSource(String alias, PrintStream out, PrintStream err) {
        if (!Reserved.isQualifier(alias)) {
            err.println("no stdlib module `" + alias + "`");
            err.println("modules: " + String.join(", ", Reserved.QUALIFIERS.stream().sorted().toList()));
            return 2;
        }
        String resource = "/souther/" + alias.toLowerCase(Locale.ROOT) + ".sou";
        try (InputStream in = ApiCommand.class.getResourceAsStream(resource)) {
            if (in == null) {
                err.println("no bundled source for `" + alias + "`");
                return 2;
            }
            out.print(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            return 0;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
