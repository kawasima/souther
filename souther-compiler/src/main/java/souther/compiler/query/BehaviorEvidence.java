package souther.compiler.query;

/**
 * Everything measured about one behavior, and the one place what it went without is worked out.
 *
 * <p>A whole holds what its parts went without and decides nothing of its own. That was already the
 * rule (issue #953); what it lacked was somewhere to be. The union was written where the document is
 * assembled, over a list of measures named there — so the parts a behavior has were a list somebody
 * had to remember to add to, and the fourth of them was missing from it. A run that went without
 * something reached the document only through the three that were named, and a module every one of
 * which had nothing to be about reported {@code complete} beside a line saying a row of it did not
 * come back (issue #996).
 *
 * <p><b>The reading is one of the parts.</b> It is the measure of the rows themselves rather than a
 * measure counted over them, and it is the only one that can never be inapplicable — so it is what
 * carries a run's shortfall where every other part has nothing to be about. Which is the whole of
 * the fix: the parts are asked here, and the reading is one of them by being a field rather than by
 * anybody remembering it.
 *
 * <p><b>Null is the compile not having got far enough to be asked.</b> Three of these are absent
 * where the module never reached the query that answers them, which is not a measure that came back
 * with nothing: every measure that ran says why it has no number. The reading is always there,
 * because there is always an answer to how far the rows were read — including that nobody asked.
 *
 * <p><b>The lines are read here and accounted for beside it.</b> Which lines this behavior's
 * positions meet, and how far the reading that found them got, is one measure and is held once; what
 * this behavior is owed a row for at them is one account of it, and what the module's declarations
 * are owed is another. A reader that describes a border whole — a block that accounts for its four
 * points, a document that publishes them — reads the lines; a reader that measures this behavior
 * reads its account.
 *
 * @param reading          how far the reading of this behavior's rows got, and what it read
 * @param signature        what the rows establish about the cases of its inputs and its output
 * @param partition        what they establish about the classes
 * @param boundaryReadings every line its positions met, in every role, whosever the row at each
 *                         point is
 * @param account          what this behavior is owed a row for at the lines its own rules drew,
 *                         each once however many of its positions read it
 *                         ({@link Adequacy.BodyBorders})
 * @param branch           what they establish about the arms of its body
 * @param decision         the rules of the decision its body states and which of them the rows
 *                         took, or null where the compile did not get far enough to be asked.
 *                         Beside {@code branch} and not among it: two rules can go through one
 *                         arm, and a body whose arms answer alike states two rules that one row
 *                         through each arm covers
 */
public record BehaviorEvidence(Adequacy.RowReading reading,
                               Adequacy.SignatureEvidence signature,
                               PartitionEvidence partition,
                               Measure<java.util.List<BorderAssessment>> boundaryReadings,
                               Measure<java.util.List<BorderObligationPointAssessment>> account,
                               Adequacy.BranchEvidence branch,
                               DecisionEvidence decision) {

    public BehaviorEvidence {
        java.util.Objects.requireNonNull(reading,
                "there is always an answer to how far a behavior's rows were read");
        // The lines, the classes and the account arrive together or not at all. They are readings
        // of one measurement, so a behavior holding some of them is this compiler having answered
        // part of a question — and whoever met it next would have to decide what the part it was
        // holding meant.
        if ((partition == null) != (boundaryReadings == null)
                || (account == null) != (boundaryReadings == null)) {
            throw new IllegalArgumentException("a behavior measured at the lines its positions met"
                    + " and not at what it is owed a row for, or the other way about: "
                    + partition + " / " + boundaryReadings + " / " + account);
        }
        // And the account is of these lines. What this behavior is owed is a projection of the
        // module's one relation, and every point of it is read at some line of this behavior — and
        // the two are read by different readers: a block and a document show the lines, and a
        // finding, a verdict and an editor read the account. Held apart without being held together,
        // one behavior could show one reading's borders under another behavior's findings, and
        // nothing downstream is in a position to notice.
        if (account != null) {
            java.util.Set<souther.compiler.partition.Border> read = new java.util.HashSet<>();
            boundaryReadings.made().orElseGet(java.util.List::of)
                    .forEach(line -> read.add(line.border()));
            for (BorderObligationPointAssessment point
                    : account.made().orElseGet(java.util.List::of)) {
                if (point.readings().stream().noneMatch(at -> read.contains(at.border()))) {
                    throw new IllegalArgumentException("a behavior owed a row at a line none of its"
                            + " positions read: " + point.point() + " against " + boundaryReadings);
                }
            }
        }
    }

    /**
     * The measures this behavior is made of, under the names a reader knows them by.
     *
     * <p>The list, held once. Whoever wants the parts of a behavior asks here — the union below,
     * and the checks that hold a rule over each of them — so a measure added is a field and an entry
     * beside it rather than an entry in every place that walks them. That is the whole of what
     * issue #996 was: a list of three written where the document is assembled, and a fourth measure
     * that reached nobody through it. Its own tests then walked lists of their own and were short of
     * the same measure, which is the same defect in the place that was supposed to catch it.
     *
     * <p>A part is null where the compile did not get far enough to be asked, and is here as one:
     * what a walker does about that is its own business, and leaving it out would be this deciding
     * that an absent measure is no part of a behavior.
     */
    public java.util.Map<String, Measure<?>> parts() {
        java.util.Map<String, Measure<?>> parts = new java.util.LinkedHashMap<>();
        parts.put("reading", reading.measured());
        parts.put("signature", signature == null ? null : signature.counted());
        parts.put("partition", partition == null ? null : partition.partitioned());
        parts.put("border", boundaryReadings);
        parts.put("branch", branch == null ? null : branch.measured());
        // Which rules of the body's decision the rows took, which is a measure of this behavior
        // like the rest of them. A reading that placed only some of the rows leaves every rule
        // nothing was seen taking as one a row may already take, and this is the one way that
        // reaches a status, a verdict and a document without each of them asking the decision
        // itself.
        parts.put("decision", decision == null ? null : decision.took());
        return java.util.Collections.unmodifiableMap(parts);
    }

    /**
     * What this behavior's measures went without, all of them.
     *
     * <p>Asked of the parts, and each of those answers for its own. Nothing here reads the shape of
     * what came back: a measure nobody asked for is not a degradation and neither is one a behavior
     * has nothing to answer, so neither is in this — which is the measurement's own answer rather
     * than a question put to the field's name.
     *
     * <p>The pair space and each position's own reading are under {@code partition}, which answers
     * for them: a whole holds what its parts went without, at every level.
     */
    public WeakeningSet weakening() {
        WeakeningSet out = WeakeningSet.none();
        for (Measure<?> part : parts().values()) {
            if (part != null) {
                out = out.union(part.weakening());
            }
        }
        // The partition answers for the measures under it, which `parts` names two of.
        if (partition != null) {
            out = out.union(partition.weakening());
        }
        // And each point of the account answers for its own measurement: a point read from rows
        // some of which could not be read is undecided, and that is the point's answer rather than
        // the lines'.
        for (BorderObligationPointAssessment point
                : account == null ? java.util.List.<BorderObligationPointAssessment>of()
                        : account.made().orElseGet(java.util.List::of)) {
            out = out.union(point.item().weakening());
        }
        return out;
    }
}
