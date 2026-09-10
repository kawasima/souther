package souther.test;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The manifest of models this repository carries says one thing.
 *
 * <p>The readers of it are elsewhere — a measurement is taken over the corpora written for
 * measurement, and the compiler's tests check in what it answers about the ones written against what
 * the language declares — and each of them refuses a manifest it cannot read. This is why they can:
 * what they are held to is the data, and the data is checked where it is written rather than at each
 * of the places it is read.
 *
 * <p><b>Not a copy of what a reader does.</b> A reader knows which purpose it consumes and nothing
 * else about the vocabulary; what is asked here is that the manifest declares a vocabulary at all,
 * uses only what it declares, and names nothing twice. A purpose added below is a purpose no reader
 * has to be taught, and this goes on holding.
 *
 * <p>And that every corpus it names is on disk with sources of its own, because a manifest naming a
 * directory that is not there is one every reader meets as a missing resource, one at a time.
 */
class EveryCorpusIsWrittenForAPurposeTheManifestDeclaresTest {

    private static final String ROOT = "/souther/corpus/";

    /** What a line begins with where it declares a purpose rather than uses one. */
    private static final String DECLARES = "purpose";

    /** One line of the manifest that is not a comment: two words, whatever they are. */
    private record Entry(String first, String second) {}

    @Test
    void everyCorpusIsWrittenForADeclaredPurpose() {
        Set<String> declared = declaredPurposes();
        assertTrue(!declared.isEmpty(),
                "the manifest declares no purpose, so no corpus can be written for one");

        List<String> undeclared = new ArrayList<>();
        for (Entry each : corpora()) {
            if (!declared.contains(each.second())) {
                undeclared.add(each.first() + " " + each.second());
            }
        }
        assertEquals(List.of(), undeclared,
                () -> "a corpus is written for a purpose the manifest does not declare, so a reader"
                        + " looking for one purpose passes over it and a reader sorting on another"
                        + " takes it for something else. Declared: " + declared);
    }

    @Test
    void nothingIsDeclaredOrNamedTwice() {
        assertEquals(List.of(), repeated(declaredList()),
                "a purpose the manifest declares twice");
        assertEquals(List.of(), repeated(corpora().stream().map(Entry::first).toList()),
                "a corpus the manifest names twice, which a reader keyed by name answers with"
                        + " whichever line came last");
    }

    @Test
    void everyCorpusNamedIsOneThisRepositoryHas() {
        Path root = RepositoryLayout.ofWorkingDirectory().moduleNamed("souther-test-support")
                .resolve("src/main/resources/souther/corpus");
        List<String> missing = new ArrayList<>();
        for (Entry each : corpora()) {
            if (!Files.isRegularFile(root.resolve(each.first()).resolve("sources.txt"))) {
                missing.add(each.first());
            }
        }

        assertTrue(!corpora().isEmpty(), "the manifest names no corpus at all");
        assertEquals(List.of(), missing,
                () -> "the manifest names a corpus with no sources.txt under " + root);
    }

    /**
     * And a directory of models nothing names is not a corpus.
     *
     * <p>The other way round, which is the one a manifest cannot fail by itself. A corpus added to
     * the tree and left out of the manifest is in no population and no measurement, and everything
     * that reads the manifest goes on passing.
     */
    @Test
    void everyCorpusOnDiskIsOneTheManifestNames() {
        Path root = RepositoryLayout.ofWorkingDirectory().moduleNamed("souther-test-support")
                .resolve("src/main/resources/souther/corpus");
        Set<String> named = new TreeSet<>();
        corpora().forEach(each -> named.add(each.first()));

        Set<String> onDisk = new TreeSet<>();
        try (var found = Files.list(root)) {
            found.filter(Files::isDirectory).forEach(each ->
                    onDisk.add(each.getFileName().toString()));
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }

        assertEquals(named, onDisk,
                "the models on disk and the ones the manifest names are the same models");
    }

    private static Set<String> declaredPurposes() {
        return new LinkedHashSet<>(declaredList());
    }

    private static List<String> declaredList() {
        return entries().stream().filter(each -> each.first().equals(DECLARES))
                .map(Entry::second).toList();
    }

    private static List<Entry> corpora() {
        return entries().stream().filter(each -> !each.first().equals(DECLARES)).toList();
    }

    /** Every line that is neither blank nor a comment, as its two words. */
    private static List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        for (String line : read(ROOT + "corpora.txt").lines().toList()) {
            String entry = line.strip();
            if (entry.isEmpty() || entry.startsWith("#")) {
                continue;
            }
            String[] parts = entry.split("\\s+");
            assertEquals(2, parts.length,
                    () -> "a line declares a purpose or names a corpus and its purpose: " + entry);
            out.add(new Entry(parts[0], parts[1]));
        }
        return out;
    }

    private static List<String> repeated(List<String> of) {
        Set<String> met = new LinkedHashSet<>();
        List<String> twice = new ArrayList<>();
        for (String each : of) {
            if (!met.add(each)) {
                twice.add(each);
            }
        }
        return twice;
    }

    private static String read(String resource) {
        try (InputStream in = EveryCorpusIsWrittenForAPurposeTheManifestDeclaresTest.class
                .getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("no such corpus resource: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
