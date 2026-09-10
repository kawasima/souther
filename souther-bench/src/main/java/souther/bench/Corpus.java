package souther.bench;

import souther.compiler.source.SourceId;

import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.Located;
import souther.compiler.diag.Severity;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Compilation;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sources a measurement is taken against, carried in this module's jar rather than read off disk, so
 * a number means the same thing on any machine and in CI.
 *
 * <p>Which corpus is which is not decided here. {@code souther/corpus/corpora.txt} names every model
 * this repository carries and what each was written for, and each corpus's {@code sources.txt} names
 * its files in the order they are handed over; both are read below and neither is restated. A list
 * written here would be a second account of the same membership, and the two would go on answering
 * separately with nothing to say when they differed.
 *
 * <p>The models are owned by {@code souther-test-support} and copied into this module's classes at
 * build time. Nothing of that module is called from here: what a measurement needs is the bytes, and
 * a dependency would put its repository tooling inside the jar a measurement is replayed from.
 *
 * <p>Every corpus must compile without an error. A language change that leaves one behind is caught
 * by {@link #check(Compilation)} before any timing is reported — a compile that stops early is
 * faster and would read as an improvement.
 *
 * <p>Compiling is all that is asked of these. What the compiler <em>answers</em> about a model of
 * this size is held beside the compiler, against documents written down there. Asking both of one
 * corpus pulls it two ways: a corpus a number is compared against must not move, and a corpus an
 * answer is checked against grows whenever a rule is tightened. That is what the manifest says of
 * each of them, and it is why the ones taken here are the ones written for measurement.
 */
public record Corpus(String name, List<String> sources, int lines) {

    private static final String ROOT = "/souther/corpus/";

    /** What a line of the manifest begins with where it declares a purpose rather than uses one. */
    private static final String DECLARES = "purpose";

    /**
     * The purpose this module consumes, which is this module's own business.
     *
     * <p>Not a copy of what the manifest declares. What the words are is the manifest's to say, and
     * a word added there is nothing this has to be taught; what is said here is which of them a
     * measurement is taken over. It is held to the manifest all the same — a manifest that stopped
     * declaring it would leave this asking for something no corpus can be written for.
     */
    private static final String MEASUREMENT = "measurement";

    /**
     * The corpora the compile measurements are taken against, in the order the manifest names them,
     * which is largest first.
     *
     * <p>The ones written for measurement, asked for by that and not by name. The {@code runtime}
     * corpus is not among them because the manifest says it was written to be run: it is small
     * enough that timing its compile would say nothing.
     */
    public static List<Corpus> all() {
        Manifest manifest = manifest();
        List<Corpus> out = new ArrayList<>();
        for (Map.Entry<String, String> each : manifest.byCorpus().entrySet()) {
            if (each.getValue().equals(MEASUREMENT)) {
                out.add(load(each.getKey()));
            }
        }
        if (out.isEmpty()) {
            throw new IllegalStateException("the manifest names no corpus written for `"
                    + MEASUREMENT + "`");
        }
        return List.copyOf(out);
    }

    /**
     * The one called {@code name}, raising where the manifest does not name it.
     *
     * <p>Through the manifest and not straight to the directory, so that a corpus loaded by name is
     * one this repository declares it has. Read off the directory instead, a corpus could be
     * measured while being in no listing, and a check over every model would pass without it.
     */
    public static Corpus load(String name) {
        if (!manifest().byCorpus().containsKey(name)) {
            throw new IllegalStateException("the manifest names no corpus `" + name + "`");
        }
        List<String> sources = new ArrayList<>();
        int lines = 0;
        for (String line : read(ROOT + name + "/sources.txt").lines().toList()) {
            String file = line.strip();
            if (file.isEmpty()) {
                continue;
            }
            String text = read(ROOT + name + "/" + file);
            sources.add(text);
            lines += (int) text.lines().count();
        }
        if (sources.isEmpty()) {
            throw new IllegalStateException("the `" + name + "` corpus names no source");
        }
        return new Corpus(name, List.copyOf(sources), lines);
    }

    /** The purposes the manifest declares, and every corpus it names with the one it was written
     *  for. */
    private record Manifest(Set<String> purposes, Map<String, String> byCorpus) {}

    /**
     * The manifest, refused where it does not say one thing.
     *
     * <p>A purpose is only what the manifest declares. Taken as whatever word a line happens to
     * carry, a corpus written for a word misspelt is one this passes over while another reader takes
     * it for something else, and neither says anything — the corpus leaves the measurements without
     * leaving anything behind. A name is only ever one corpus for the same reason: keyed without
     * looking, a repeated name is a corpus the last line silently replaces.
     */
    private static Manifest manifest() {
        Set<String> purposes = new LinkedHashSet<>();
        Map<String, String> byCorpus = new LinkedHashMap<>();
        List<String[]> entries = new ArrayList<>();
        for (String line : read(ROOT + "corpora.txt").lines().toList()) {
            String entry = line.strip();
            if (entry.isEmpty() || entry.startsWith("#")) {
                continue;
            }
            String[] parts = entry.split("\\s+");
            if (parts.length != 2) {
                throw new IllegalStateException(
                        "a line declares a purpose or names a corpus and its purpose: " + entry);
            }
            if (parts[0].equals(DECLARES) && !purposes.add(parts[1])) {
                throw new IllegalStateException("the manifest declares `" + parts[1] + "` twice");
            }
            if (!parts[0].equals(DECLARES)) {
                entries.add(parts);
            }
        }
        for (String[] entry : entries) {
            if (!purposes.contains(entry[1])) {
                throw new IllegalStateException("the `" + entry[0] + "` corpus is written for `"
                        + entry[1] + "`, which the manifest does not declare. It declares "
                        + purposes);
            }
            if (byCorpus.put(entry[0], entry[1]) != null) {
                throw new IllegalStateException("the manifest names `" + entry[0] + "` twice");
            }
        }
        if (byCorpus.isEmpty()) {
            throw new IllegalStateException("the manifest names no corpus at all");
        }
        if (!purposes.contains(MEASUREMENT)) {
            throw new IllegalStateException("the manifest declares no `" + MEASUREMENT
                    + "`, which is what a measurement is taken over. It declares " + purposes);
        }
        // Kept in the order the manifest writes them. What is handed out is what a measurement
        // reports, and the manifest is where that order is said.
        return new Manifest(Collections.unmodifiableSet(purposes),
                Collections.unmodifiableMap(byCorpus));
    }

    private static String read(String resource) {
        try (InputStream in = Corpus.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("no such corpus resource: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** One compile of this corpus, everything answered — what the timings time. */
    public Compilation compile() {
        Compilation compilation = Compilation.ofSources(sources, ModulePath.EMPTY);
        compilation.answerEverything();
        compilation.classes();
        return compilation;
    }

    /**
     * Raises when this corpus no longer compiles, naming what it now says.
     *
     * <p>Takes the compile to check rather than making one, because the first compile in a process
     * is also the cold measurement and there is only one of those: compiling here to check would
     * warm the JIT and the number reported as cold would be a warm one.
     */
    public void check(Compilation compilation) {
        List<String> errors = new ArrayList<>();
        for (Map.Entry<SourceId, List<Diagnostic>> found
                : Located.diagnosticsOf(compilation.diagnostics()).entrySet()) {
            for (Diagnostic diagnostic : found.getValue()) {
                if (diagnostic.severity() == Severity.ERROR) {
                    errors.add(name + " source " + found.getKey() + ": " + diagnostic.code()
                            + " at " + diagnostic.primary());
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException("the `" + name + "` corpus no longer compiles:\n  "
                    + String.join("\n  ", errors));
        }
        if (compilation.classes().isEmpty()) {
            throw new IllegalStateException("the `" + name + "` corpus generated no classes");
        }
    }

    @Override
    public String toString() {
        return name;
    }
}
