package net.unit8.souther.compiler.check;

import java.util.Collection;

/**
 * "Did you mean" hints for unknown names. When a name the checker cannot resolve is a near-miss of
 * one that is in scope, the error carries the likely intended name, so a typo reads as a typo. The
 * closeness bound is Levenshtein distance ≤ 2 and strictly less than the name's length, so a short
 * name does not match every candidate.
 */
final class Suggest {

    private Suggest() {
    }

    /** {@code " (did you mean `X`?)"} for the closest candidate within bound, or {@code ""}. */
    static String hint(String name, Collection<String> candidates) {
        String best = candidate(name, candidates);
        return best == null ? "" : " (did you mean `" + best + "`?)";
    }

    /** The closest in-scope candidate to {@code name} within the closeness bound, or {@code null}.
     * The structured form of {@link #hint}, for a diagnostic's {@code suggestion} field. */
    static String candidate(String name, Collection<String> candidates) {
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String candidate : candidates) {
            if (candidate.equals(name)) {
                continue;
            }
            int d = distance(name, candidate);
            if (d < bestDistance) {
                bestDistance = d;
                best = candidate;
            }
        }
        if (best != null && bestDistance <= 2 && bestDistance < name.length()) {
            return best;
        }
        return null;
    }

    /** Levenshtein edit distance (insert/delete/substitute), on Unicode code points. */
    static int distance(String a, String b) {
        int[] ca = a.codePoints().toArray();
        int[] cb = b.codePoints().toArray();
        int[] prev = new int[cb.length + 1];
        int[] cur = new int[cb.length + 1];
        for (int j = 0; j <= cb.length; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= ca.length; i++) {
            cur[0] = i;
            for (int j = 1; j <= cb.length; j++) {
                int sub = prev[j - 1] + (ca[i - 1] == cb[j - 1] ? 0 : 1);
                cur[j] = Math.min(sub, Math.min(prev[j] + 1, cur[j - 1] + 1));
            }
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[cb.length];
    }
}
