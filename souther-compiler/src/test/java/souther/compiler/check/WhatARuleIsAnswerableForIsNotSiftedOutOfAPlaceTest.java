package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.diag.EveryShippedMessageCatalogIsCompleteAndValidTest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * What a rule is answerable for is carried from where the asking is, and never sifted out of what a
 * place was left holding.
 *
 * <p>A position says what it was left holding and says it for every reason there is. Sifting those
 * for the ones a rule could be answerable for gives a list of reasons and no rule: an allowance is
 * the position's and every rule reaching it pays in, so what comes back is every rule that named the
 * place, standing in for the one that asked.
 *
 * <p>Closing a call site leaves the sift behind — a method on a finished reading that does exactly
 * it, unused and one call away from being put back. So what is held is the sift itself: naming
 * {@link souther.compiler.values.UnreadReason.About#A_RULE} is how a caller separates the two kinds,
 * and the places entitled to are counted.
 *
 * <p><b>A tripwire and not a proof.</b> A second sift written inside one of the files below passes
 * this, and so does one written without naming the classification. What it stops is the shape that
 * was actually there: a reading answering what a rule is answerable for by filtering its own
 * position's reasons.
 */
class WhatARuleIsAnswerableForIsNotSiftedOutOfAPlaceTest {

    /**
     * Where the two kinds may be told apart, and why each is entitled to.
     *
     * <p>{@code UnreadReason} is the classification itself. {@code Unbuilt} makes the two facts and
     * refuses a reason of the wrong kind in either. {@code BlockReason} and {@code RuleShortfall}
     * refuse rather than sift: a reason no rule is answerable for is turned away where it would have
     * been filed under one, so what is filed is of the right kind by having been made at all.
     * {@code ReadingShortfall} refuses it where the decision is made, which is one step before the
     * filing and is the same refusal.
     *
     * <p>Nowhere reads it to answer with. What a rule is answerable for is made where the asking
     * is and handed on as {@code RuleShortfall}, and a reading with one in hand has the answer
     * rather than a place to sift for it.
     */
    private static final Set<String> ENTITLED = Set.of(
            "UnreadReason.java", "Unbuilt.java", "BlockReason.java", "RuleShortfall.java",
            "ReadingShortfall.java");

    @Test
    void onlyTheseTellTheTwoKindsApart() throws IOException {
        List<Path> sources = EveryShippedMessageCatalogIsCompleteAndValidTest.mainSources();
        assertFalse(sources.isEmpty(), "found no sources at all — the scan missed the tree");

        Set<String> naming = new TreeSet<>();
        for (Path source : sources) {
            String text = Files.readString(source, StandardCharsets.UTF_8);
            if (text.contains("About.A_RULE") || text.contains("About#A_RULE")) {
                naming.add(source.getFileName().toString());
            }
        }

        assertEquals(new TreeSet<>(ENTITLED), naming,
                "a place's reasons are sifted for what a rule is answerable for somewhere new, and"
                        + " what comes back names every rule that mentioned the place");
    }
}
