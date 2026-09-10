package souther.compiler.conformance;

import souther.compiler.Compiler;
import souther.compiler.diag.Located;
import souther.compiler.diag.SourceNameResolver;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.report.AdequacyReport;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A model written to be compiled here, and what this compiler answers about it.
 *
 * <p>Written rather than copied. A model taken from somewhere it was written for its own reasons
 * reaches the constructs that domain happened to need and no others, and it changes for reasons
 * that have nothing to do with this compiler. What is here is written against what the language
 * declares, and {@link AConformanceCorpusReachesEveryConstructTheLanguageDeclaresTest} says which
 * of those it does not reach yet.
 *
 * <p>A corpus is a directory of {@code .sou} files plus a {@code sources.txt} naming them in the
 * order they are handed over, and {@code corpora.txt} names the directories and what each was
 * written for. Both are written out rather than discovered because a jar has no directory to list,
 * and because the order is part of what is compiled: a file of {@code examples for} a module is read
 * after the module it is attached to.
 *
 * <p><b>The models and the answers about them are kept apart.</b> The sources are the repository's,
 * owned by {@code souther-test-support} so that every module reaches the same ones whichever side of
 * the compiler it sits on; what this compiler answers about them is this module's and is checked in
 * here. Which corpora are conformance corpora is read off the manifest below and stated nowhere
 * else.
 */
public record ConformanceCorpus(String name, List<String> files, List<String> sources) {

    /** Where the models are, which is every model this repository carries and not only these. */
    private static final String MODELS = "/souther/corpus/";

    /** What the manifest calls the corpora whose answers are checked in beside this compiler. */
    static final String CONFORMANCE = "conformance";

    /** Where the answers about them are, which is this module's own resources. */
    static final String ROOT = "/souther/compiler/conformance/";

    /** Where those are written, for the one caller that writes rather than reads them. */
    static final Path SOURCE_DIR = Path.of("src", "test", "resources", "souther", "compiler",
            "conformance");

    public static List<ConformanceCorpus> all() {
        List<ConformanceCorpus> out = new ArrayList<>();
        manifest().forEach((name, writtenFor) -> {
            if (writtenFor.equals(CONFORMANCE)) {
                out.add(load(name));
            }
        });
        if (out.isEmpty()) {
            throw new IllegalStateException("the manifest names no conformance corpus");
        }
        return out;
    }

    public static ConformanceCorpus load(String name) {
        if (!manifest().containsKey(name)) {
            throw new IllegalStateException("the manifest names no corpus `" + name + "`");
        }
        List<String> files = filesOf(name);
        List<String> sources = new ArrayList<>();
        files.forEach(file -> sources.add(read(MODELS + name + "/" + file)));
        return new ConformanceCorpus(name, files, sources);
    }

    /**
     * The files one corpus is made of, in the order they are handed over.
     *
     * <p>Any corpus the manifest names and not only a conformance one, because what a reader over
     * every model this repository carries needs is the same reading. Written a second time for the
     * others, the two would part over a file added to one corpus and nothing would say so.
     */
    public static List<String> filesOf(String name) {
        List<String> out = new ArrayList<>();
        for (String line : read(MODELS + name + "/sources.txt").lines().toList()) {
            String file = line.strip();
            if (!file.isEmpty()) {
                out.add(file);
            }
        }
        if (out.isEmpty()) {
            throw new IllegalStateException("the `" + name + "` corpus names no source");
        }
        return List.copyOf(out);
    }

    /** The text of each of them. */
    public static List<String> sourcesOf(String name) {
        List<String> out = new ArrayList<>();
        filesOf(name).forEach(file -> out.add(read(MODELS + name + "/" + file)));
        return List.copyOf(out);
    }

    /**
     * Every corpus this repository carries, by name, with what each was written for.
     *
     * <p>Read rather than restated. What a corpus is for is what decides whether an answer about it
     * is checked in, and a list of names written here would answer that a second time — going on
     * answering after the manifest had changed, with nothing to say that it had.
     */
    public static Map<String, String> manifest() {
        Map<String, String> out = new LinkedHashMap<>();
        for (String line : read(MODELS + "corpora.txt").lines().toList()) {
            String entry = line.strip();
            if (entry.isEmpty() || entry.startsWith("#")) {
                continue;
            }
            String[] parts = entry.split("\\s+");
            if (parts.length != 2) {
                throw new IllegalStateException(
                        "a corpus is named with what it was written for: " + entry);
            }
            out.put(parts[0], parts[1]);
        }
        if (out.isEmpty()) {
            throw new IllegalStateException("the manifest names no corpus at all");
        }
        return out;
    }

    /**
     * What to call each source in a rendering of this corpus.
     *
     * <p>The file it was written in. A compile names its sources by the position they were handed
     * over at, which is right for saying that two reasons are about one source and says nothing to
     * a reader of a checked-in document — and a document naming a position would also change every
     * time a file was inserted before another.
     */
    public SourceNameResolver names() {
        return id -> {
            for (int i = 0; i < files.size(); i++) {
                if (Compilation.idOfSourceIndex(i).equals(id)) {
                    return files.get(i);
                }
            }
            return id == null ? null : id.value();
        };
    }

    private static String read(String resource) {
        try (InputStream in = ConformanceCorpus.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("no such conformance resource: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * This corpus analysed, with everything the report needs measured and every warning collected.
     *
     * <p>The analysing entry point rather than the compiling one. What the rows cover is answered
     * from whatever was observed, and an entry point that raises at the first error would make
     * every failure here a report about the one thing that stopped it.
     *
     * <p><b>And the report is written, not merely asked for.</b> {@link Adequacy.Asked#fullReport()}
     * says how much to measure and makes no measurement happen: what puts the questions is somebody
     * building the report, so a compilation handed that and left alone holds none of the adequacy
     * answers at all. Since this is what says a corpus was analysed, whoever reads the store
     * afterwards would be reading a compile that measured nothing while saying it measured
     * everything. Built here, the sentence this method is named for is true of what it hands back.
     */
    public Analysed analyse() {
        List<Located> warnings = new ArrayList<>();
        Compilation compilation = Compiler.analyzedModules(sources, ModulePath.EMPTY, warnings,
                Adequacy.Asked.fullReport());
        return new Analysed(this, compilation, compilation.errors(), warnings,
                AdequacyReport.of(compilation));
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * One analysis of one corpus: what it came to, everything it said getting there, and what it
     * measured.
     *
     * <p>The report is carried rather than left to a reader to build. Two readers building one over
     * the same compilation get the same document — the measures are answers and are kept — so what
     * this settles is not which document but that there is one: the questions are put where the
     * analysis happens, and no caller can have the analysis without them.
     */
    public record Analysed(ConformanceCorpus corpus, Compilation compilation,
                           List<Located> errors, List<Located> warnings, AdequacyReport report) {

        /** Everything said about this corpus, errors before warnings, as the report writes them. */
        public List<Located> said() {
            List<Located> out = new ArrayList<>(errors);
            out.addAll(warnings);
            return out;
        }
    }
}
