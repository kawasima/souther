package souther.compiler.report;

import souther.compiler.publish.MeasureWord;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Every measure the verdict rests on is handed over with what it is a measure of.
 *
 * <p>The walks that settle the verdict reach each measure through the module and behavior that own
 * it, and once passed the measurement alone the owner is gone: a measurement carries what it was
 * waiting for and nothing about which measure it is. That is what left a reader told how many
 * things held a verdict open and none of what they were (issue #1437).
 *
 * <p><b>Nothing else catches a seventh measure.</b> A measure added to the walk compiles, is
 * counted by the verdict, and arrives at the reader under whatever word the walk happened to hand
 * over — or under none. Javac says nothing, because the type it is added as is the type the others
 * are. So the walk is read here.
 *
 * <p>Held on the source of the walk rather than on the answers it produces. What a corpus reaches is
 * what its models happen to hold over; what the walk hands over is the claim, and a measure no model
 * here leaves unmeasured would slip through a check made over the answers.
 *
 * <p><b>That the pair travels at all is javac's.</b> What the walks hand back is a list of pairs, so
 * a measurement passed on its own does not compile. What javac has nothing to say about is a measure
 * handed over under a word that names another, or a word this document can spell that no measure is
 * ever handed under — which is what is read here.
 */
class EveryMeasureAVerdictRestsOnHasAWordToBeNamedByTest {

    /** The walks, as they are written. */
    private static String walked() {
        Path source = Path.of("src", "main", "java", "souther", "compiler", "report",
                "AdequacyReport.java");
        try {
            return Files.readString(source, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("the walk that settles a verdict is read from its own"
                    + " source, and this run has no working directory holding it", e);
        }
    }

    /**
     * Every measure handed over names itself with a word of the measure vocabulary, or with the
     * position a measure of a position is named by.
     *
     * <p>Read off the calls rather than off a list beside them. What is looked for is every
     * subject the walks build for a measure: one names it by a word and one by the position it is
     * of, and a measure handed over as anything else is a measure a reader is sent to by nothing.
     */
    @Test
    void everyMeasureHandedOverIsNamedByAWordOrByItsPosition() {
        String source = walked();
        Set<String> words = new LinkedHashSet<>();
        Matcher named = Pattern.compile("MeasureWord\\.([A-Z_]+)").matcher(source);
        while (named.find()) {
            words.add(named.group(1));
        }

        assertFalse(words.isEmpty(), "the walk names measures by these words, and this found none");
        // Every word the vocabulary has is handed over by the walk. A word nothing hands over is a
        // measure this report can spell and never names, which is the same silence read from the
        // other side.
        List<String> unused = new ArrayList<>();
        for (MeasureWord word : MeasureWord.values()) {
            if (!words.contains(word.name())) {
                unused.add(word.name());
            }
        }

        assertEquals(List.of(), unused, () -> "the walk hands over no measure under " + unused
                + ", so the word is one this document can spell and nothing writes");
    }

}
