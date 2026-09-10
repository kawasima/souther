package souther.compiler.query;

import souther.compiler.partition.BorderObligationPoint;
import souther.compiler.partition.BorderQuantity;
import souther.compiler.partition.Demand;
import souther.compiler.partition.DomainPoint;
import souther.compiler.partition.PointRole;
import souther.compiler.check.RuleReportAnchor;
import souther.compiler.publish.PublicationOrders;
import souther.compiler.publish.PublishedRuleHandle;
import souther.compiler.publish.PublishedSentence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything known about one point a row is owed at: what it asks, and what all the readings of it
 * came to.
 *
 * <p>What a report prints, what a build refuses over and what an editor offers are readings of
 * these. A {@link BorderAssessment} beside one is a border as some position of some behavior met it,
 * and there is one of those per position of every behavior carrying the type. Answered from an
 * occurrence, one clause of {@code UserId} is 126 things to write a row for over {@code crm}.
 *
 * <p><b>Every point, whosever it is.</b> Whether a row here is a body's to write or is owed to the
 * declarations that drew the line is what {@link souther.compiler.partition.PointAttribution}
 * answers, and it decides who keeps the account and where a row goes — not whether the readings of
 * the point are gathered. Made for one of the two, the other kind had no value naming its readings
 * at all, and its one row was offered for whichever reading a walk wrote last. So the readings are
 * gathered here for both, and whose the point is is read off this rather than asked before it.
 *
 * <p><b>One of these per point and not per line.</b> A line owes as many as four things and they are
 * not one piece of work: a row at the line and a row beside it are two values, and two runs beside
 * one line that stop in different places are two obligations, whether or not one row answers both.
 * Which of them a reading owes is the
 * border's own answer ({@link souther.compiler.partition.Border#owes}), so nothing here decides it
 * again — and a line has no assessment of its own, only the points it owes. A report that shows a
 * border whole groups these by the line and joins them with the border's four answers, because a
 * role nobody is owed a row in has no point here to be found by.
 *
 * <p><b>And a point has no word for where it is.</b> Where on the quantity the rule cut is part of
 * what a point is, and it is published as the identity it is. What it has no word for is a
 * <em>reader's</em> spelling of that place, because writing one takes a quantity and a quantity is
 * a reading's: a distance writes its levels as how far the row stands from the other position —
 * {@code d.to - 1} — and which position that is differs between the readings. Over {@code crm} one
 * point of one line has twenty-five such spellings. Written from the level alone instead, the
 * number comes out true and unreadable: the {@code ON} point of {@code from < to} is a distance of
 * −1, which is no value any position holds and nothing an author can write. So a point says which
 * of the four it is and which rule drew the line, and every word with a quantity in it is said
 * under it by the reading whose quantity it is ({@link #readingsSaid}). A line a declaration drew
 * is the exception the author makes: there the quantity is one they wrote, and {@link #said(String)}
 * takes it from them.
 *
 * <p><b>What is owed is the same at every reading, and that is checked rather than folded.</b> A
 * {@link Demand} is what the point asks — a criterion over the levels of the quantity the line cut,
 * or a reason no row is asked for — and none of it is about where the line was read. So two readings
 * of one point that disagree about it have not disagreed: something has called two points one, and
 * the identity is what is wrong. Joined instead — the stronger demand winning, or the two merged —
 * that mistake would come out as a report asking for a row at a point some other line owns.
 */
public record BorderObligationPointAssessment(BorderObligationPoint point,
                                              souther.compiler.partition.PointAttribution
                                                      attribution,
                                              java.util.Set<RuleReportAnchor> reachedBy,
                                              Demand demand, ObligationAssessment item,
                                              java.util.SequencedMap<Reading, BorderAssessment>
                                                      met) {

    /**
     * Every handle a reader was offered for the rule that drew this line.
     *
     * <p>Which rule it is comes from the point, which is what the debt is filed under
     * ({@link souther.compiler.partition.BorderObligationId#provenance}); what varies is how a
     * reader is sent to it. Made here rather than kept, so that a handle is of this point's rule
     * and can be of no other.
     *
     * <p><b>Several, because one authored rule reached twice is one debt.</b> A comparison inside a
     * helper this compilation holds no source for is met at each call, and each call is a way a
     * reader can be sent to it — while the author wrote one guard and owes one row. Held as one
     * handle, the debt would name whichever way in the walk met first, which is what the
     * publication order exists to decide instead.
     */
    public java.util.Set<souther.compiler.check.RuleCitation> citations() {
        return souther.compiler.check.RuleCitation.handlesFor(point.line().provenance(), reachedBy);
    }

    /**
     * One reading of the line: which behavior met it, and where in that behavior it was met.
     *
     * <p><b>The place and not the behavior alone.</b> One behavior can carry the type at more than
     * one place — {@code { a: Code, b: Code }} is two readings of {@code Code}'s line in whichever
     * behavior takes that record — and what a search of one of them comes to is a fact about that
     * place: the rules reaching it, and the values its decoder took. Told apart by the behavior
     * alone, the second answer was dropped and which one survived was whichever the search walked
     * first.
     *
     * <p><b>The whole target, because that is what says where a line was read.</b> A quantity runs
     * over as many positions as it runs over: a rule relating {@code today} to a field of a sum is
     * read once under each case, and both readings write {@code today} on the left. A word off the
     * target — the left of it, the sentence a report prints — names one of the positions or spells
     * the pair, and neither is what tells two of them apart; keyed on one, two readings of one line
     * arrived as one and {@link #across} refused a model the compiler can read.
     *
     * <p><b>So a point and a reading of it are a line and where it was read.</b> A point carries
     * the authored line ({@link souther.compiler.partition.BorderObligationId#line}), this carries
     * the target, and the two together are the {@link souther.compiler.partition.BoundaryLine} the
     * readings of one behavior were folded under ({@code Coverages.merged}). That is why the fold
     * and this cannot come apart: they are one equivalence written once, rather than two that agree
     * while every quantity has one position.
     */
    public record Reading(souther.compiler.partition.BoundaryTarget target) {

        public Reading {
            if (target == null) {
                throw new IllegalArgumentException("a reading is of some line, somewhere");
            }
        }

        /** The reading made where {@code line} was met. */
        public static Reading of(souther.compiler.partition.Border line) {
            return new Reading(line.cut());
        }

        /**
         * Which behavior read it. The target's answer and not a second field: a quantity is some
         * behavior's input, so a reading holding the behavior beside it would hold one fact twice
         * and check nowhere that the two agree.
         */
        public String behavior() {
            return target.behavior();
        }

        /**
         * How a message names this reading. For a person to read, and never a key: what tells two
         * readings apart is the value, and a caller comparing these words is asking a question the
         * pair already answers.
         */
        @Override
        public String toString() {
            return behavior() + "/" + target.label();
        }
    }

    public BorderObligationPointAssessment {
        if (point == null) {
            throw new IllegalArgumentException("an assessment is of some point");
        }
        if (met == null || met.isEmpty()) {
            throw new IllegalArgumentException(
                    "a point is what its readings came to, and this is none of them: " + point);
        }
        if (demand == null || item == null || attribution == null || reachedBy == null) {
            throw new IllegalArgumentException(
                    "a point owed a row asks for one, came to something, is owed to somebody and"
                            + " is found somewhere: " + point);
        }
        reachedBy = java.util.Set.copyOf(reachedBy);
        met = java.util.Collections.unmodifiableSequencedMap(new LinkedHashMap<>(met));
    }

    /**
     * The points {@code module}'s declarations owe, from every reading of every one of them.
     *
     * <p>In the order the readings were made, so that what a report prints is read against the one
     * before it. What tells the points apart is what a border says it owes and nothing here: a
     * caller that grouped by anything else — the label, the rule, the position — would be deciding
     * what a debt is a second time and somewhere else.
     *
     * <p><b>No point is left out, whosever it is.</b> Which account a point falls in and who may
     * answer for it are questions about what this produces, and every reader of one asks them of
     * it: a module keeping the declarations' account reads {@link #ownersIn}, and a behavior's own
     * account reads {@link #owedToTheReading}. Asked before the grouping instead, one of the two
     * kinds is gathered and the other is not — and the one that is not has no value naming its
     * readings, so whatever offers it a row has only one of them to offer from.
     *
     * <p><b>Of lines already folded, one per line a behavior read.</b> A point and a {@link Reading}
     * are the authored line and where it was read, which is the
     * {@link souther.compiler.partition.BoundaryLine} {@code Coverages.merged} folds on — so after
     * merging, one point of one behavior has one reading at one target, and two of them under one
     * key say the caller handed lines that were never merged. That is what the refusal below is
     * about; it is not a fold, and joining two such entries would put the order of a walk into what
     * a row is offered for.
     */
    public static List<BorderObligationPointAssessment> across(List<BorderAssessment> readings) {
        Map<BorderObligationPoint, java.util.SequencedMap<Reading, BorderAssessment>> byPoint =
                new LinkedHashMap<>();
        Map<BorderObligationPoint, souther.compiler.partition.PointAttribution> attribution =
                new LinkedHashMap<>();
        // The lines alone, not filed under behaviors. Which behavior read a line is the line's own
        // answer, so a caller filing it under one would be saying that fact a second time.
        for (BorderAssessment reading : readings) {
            Reading where = Reading.of(reading.border());
            // Every arm answered, for the reason the readings are: a point whose arm nothing
            // names is a point gathered nowhere, and everything downstream would go on
            // compiling.
            for (souther.compiler.partition.OwedPoint each : reading.border().owes()) {
                BorderObligationPoint owed = each.point();
                // What settled the point is the reading's, so a point read twice is owed to
                // what either reading says owes it. Kept as the first reading's, a point one
                // module's declaration narrowed at one position and another's at another would
                // be attributed to whichever the walk reached first.
                attribution.merge(owed, each.attribution(),
                        souther.compiler.partition.PointAttribution::and);
                BorderAssessment already = byPoint
                        .computeIfAbsent(owed, _ -> new LinkedHashMap<>()).put(where, reading);
                if (already != null) {
                    // One line, one behavior, one place, twice — which the lines handed in were
                    // folded on and so cannot be. What is wrong is upstream: these are the
                    // readings a behavior's lines came to after Coverages merged them, and two
                    // entries under one key say the list was never merged. Refused rather than
                    // kept, because keeping one of them means what a search of it came to stands
                    // for the other, chosen by the order the walk took.
                    throw new IllegalStateException("two of one behavior's lines are the same"
                            + " line read at the same place, so they were never merged: " + owed
                            + " at " + where);
                }
            }
        }
        List<BorderObligationPointAssessment> out = new ArrayList<>();
        byPoint.forEach((point, met) -> out.add(of(point, attribution.get(point), met)));
        return List.copyOf(out);
    }

    /**
     * One point, from the readings of it.
     *
     * <p><b>Nothing here says what the line is on.</b> A reading names the position it met the line
     * at — {@code String.length(draft.owner)} — and there are as many of those as there are
     * positions carrying the type, so none of them names the point. What the author wrote — {@code
     * String.length(value)} — is the declaration's word, and a point no declaration drew has no such
     * word at all: written on this, the rule's own name stood in for a quantity nobody named, and a
     * report reading it was told a comparison was the thing being compared.
     *
     * <p>So a consumer with a declaration to speak about takes the wording from the declaration
     * ({@link Adequacy.DeclaredDebt}), and one without does not have one to take.
     */
    public static BorderObligationPointAssessment of(
            BorderObligationPoint point,
            souther.compiler.partition.PointAttribution attribution,
            java.util.SequencedMap<Reading, BorderAssessment> met) {
        List<BorderAssessment> readings = List.copyOf(met.values());
        Demand asked = asked(point, readings);
        return new BorderObligationPointAssessment(point, attribution,
                reachedBy(point, readings), asked, came(point.point(), readings, asked), met);
    }

    /**
     * Every way a reader can be sent to the rule that drew this line, out of the readings of it.
     *
     * <p><b>All of them, because one debt is not one way in.</b> A rule the author named is found
     * by that name from anywhere and every reading offers the same question; a comparison inside a
     * helper this compilation holds no source for is met at each call, and each call is a different
     * thing to show. The author wrote one guard and owes one row either way
     * ({@link souther.compiler.partition.BorderObligationId}), so the several are ways in and never
     * several points.
     *
     * <p>Which is why nothing is chosen here. Which of them a document writes is decided where a
     * sentence is written, over what a document would write of each
     * ({@link souther.compiler.publish.PublicationOrders#handleFor}); chosen at the fold, it would
     * be whichever reading the walk met first — the thing the whole projection exists to remove.
     *
     * <p><b>Held to the point's own rule.</b> What tells this debt from another is the authored
     * line the point carries, and a reading whose rule is a different one is a reading of another
     * debt filed here. Compared with the point rather than with each other, so that a set of
     * readings that agree on the wrong rule is refused as well.
     *
     * <p><b>And it is not what the line is on.</b> That is the reading's word — {@code n} here and
     * {@code r@P.deadline} there — and a point read at two positions has one for each, so a point
     * that held one would be named after a place it is not owed at. Which is why what a report says
     * about the quantity comes from the readings and what it says about the rule comes from here.
     */
    private static java.util.Set<RuleReportAnchor> reachedBy(
            BorderObligationPoint point, List<BorderAssessment> readings) {
        java.util.Set<RuleReportAnchor> out = new java.util.LinkedHashSet<>();
        for (BorderAssessment reading : readings) {
            souther.compiler.check.RuleCitation cited = reading.border().origin().cited();
            if (!cited.rule().equals(point.line().provenance())) {
                throw new IllegalStateException("a reading filed under this point is of another"
                        + " rule, so they are not one point: " + point + " is "
                        + point.line().provenance() + " and " + reading.border().cut().named()
                        + " read " + cited.rule());
            }
            out.addAll(souther.compiler.check.RuleCitation.anchorOf(cited));
        }
        return out;
    }

    /**
     * How a report writes where this line came from, with the sources under the names {@code names}
     * gives them.
     *
     * <p>The citation's own sentence, which every reading of the point says the same way: a rule the
     * author named is found by that name wherever it is read, and a comparison by the place it is
     * written.
     */
    public PublishedSentence describe(PublishedRuleHandle.WhereARuleIs places) {
        return PublishedSentence.AroundAHandle.alone(handle(places));
    }

    /**
     * How a document sends a reader to the rule that drew this line.
     *
     * <p>The handle rather than what it reads as, for the field that is the handle and nothing
     * else. What that field says is the surface's to write.
     */
    public PublishedRuleHandle handle(PublishedRuleHandle.WhereARuleIs places) {
        return PublishedRuleHandle.of(
                PublicationOrders.handleFor(citations(), places)
                        .orElseThrow(() -> new IllegalStateException("a line a reader is sent to is"
                                + " one some reading said how to find: " + point)),
                places);
    }

    /**
     * The same point as a reader shown only {@code behaviors} is owed it, or null where none of
     * them reads it.
     *
     * <p>What a debt came to is what its readings came to together, so a view that shows some of
     * them is owed what those came to and not what the rest did: a row that was not read in a
     * behavior the reader cannot see leaves this debt undecided for a reader who cannot act on it,
     * and the reason it is undecided names a position that is not on the page.
     *
     * <p>Made again from the readings that are left rather than trimmed, because everything about a
     * debt but its identity follows from them — what it asks of a row, what became of it, which
     * behaviors carry it. Trimming the ones a reader can name and keeping the answer folded from
     * all of them is how a filtered view came to carry a hidden behavior's evidence.
     *
     * <p>Who owes it is not re-derived. A declaration owes a line wherever the type is carried, and
     * which behaviors this reader is shown is no part of that.
     */
    public BorderObligationPointAssessment keptFor(java.util.Set<String> behaviors) {
        java.util.SequencedMap<Reading, BorderAssessment> kept = new LinkedHashMap<>();
        met.forEach((where, reading) -> {
            if (behaviors.contains(where.behavior())) {
                kept.put(where, reading);
            }
        });
        return kept.isEmpty() ? null : of(point, attribution, kept);
    }

    /** Every reading of the line that owes this point, in the order they were made. */
    public List<BorderAssessment> readings() {
        return List.copyOf(met.values());
    }

    /** Which line of the model a row here is owed for. */
    public souther.compiler.partition.BorderObligationId id() {
        return point.line();
    }

    /** Which point of a border this is, as a place on the quantity. */
    public DomainPoint at() {
        return point.point();
    }

    /** Which of the four it is, which the line it is a point of answers. */
    public PointRole role() {
        return met.firstEntry().getValue().border().roleOf(at());
    }

    /**
     * Which side of the line this point is on, where the role alone does not tell it from another
     * point of the same line.
     *
     * <p>What a mark says beside the role. Which side a point is on is the line's own and is the
     * same at every reading of it, so any of them answers; where on the quantity it is takes a
     * reading's words and is said under the mark rather than in it.
     */
    public String whichSide() {
        return met.firstEntry().getValue().border().whichSide(at());
    }

    /**
     * What the point asks, which every reading of it says the same way.
     *
     * <p>Checked here, because this is where two readings of one point first stand beside each
     * other. A disagreement is not something to resolve: it says the two are not one point, and the
     * identity that put them together is the defect. What it names is both readings, since which of
     * them is the wrong one is exactly what is not known.
     */
    private static Demand asked(BorderObligationPoint point, List<BorderAssessment> readings) {
        Demand asked = readings.get(0).border().demand(point.point());
        for (BorderAssessment reading : readings) {
            Demand also = reading.border().demand(point.point());
            if (!asked.sameAs(also)) {
                throw new IllegalStateException("two readings of one point disagree about what it"
                        + " asks for, so they are not one point: " + point
                        + " asks " + asked + " at " + readings.get(0).border().cut().named()
                        + " and " + also + " at " + reading.border().cut().named());
            }
        }
        return asked;
    }

    /**
     * What the readings came to.
     *
     * <p>The coverage is folded ({@link ItemAssessment.Coverage}). So is what
     * building a value came to, and it is here for one thing: that a value at the point was built
     * is evidence the point exists, and whether a point exists is what tells a line no row stands at
     * from one no row could stand at ({@link ItemAssessment#isUnmetGap}). Every reading of one point
     * asks the same of a row — which is checked, not assumed — so a value built at one of them is a
     * value at this point.
     *
     * <p><b>Not the row a point is offered.</b> A row is offered once for a point, and which reading
     * composes it is a search over the readings rather than a fold of them ({@link
     * PointResolver}): the row here is written in one behavior's terms and choosing it as the
     * one to offer would be choosing a representative, which is the mistake this whole value exists
     * to undo. What it is here for is that a value at the point was built, which is evidence the
     * point exists.
     */
    private static ObligationAssessment came(DomainPoint role,
                                             List<BorderAssessment> readings,
                                             Demand asked) {
        if (asked instanceof Demand.NotOwed not) {
            throw new IllegalStateException(
                    "a point nobody is owed a row at, assessed as one that is: " + not.reason());
        }
        List<Measurement<ItemAssessment.Coverage>> coverage = new ArrayList<>();
        // What each reading's search came to, all of them. A search is made per reading and the
        // readings can have come to different things — one composing a row, one stopped at a figure
        // of this compiler's, one finding nothing — and every one of those is a fact about this
        // point. Kept as the strongest, whatever the others found out was dropped, and the answer a
        // reader got depended on the order the readings were walked in.
        SearchOutcomes searched = SearchOutcomes.none();
        // Whether a value at the point exists is a fact about the point and not about the reading
        // that reached it: one reading proving it proves it. The other two states are what a reading
        // says about itself, so the weaker of them stands only where nothing proved anything.
        ItemAssessment.WritabilityProjection projection =
                ItemAssessment.WritabilityProjection.NOT_COMPUTED;
        for (BorderAssessment reading : readings) {
            ItemAssessment.Owed owed = reading.owedAt(role);
            if (owed == null) {
                throw new IllegalStateException(
                        "a reading owing nothing at a point it owes one at: " + role);
            }
            coverage.add(owed.coverage());
            searched = searched.plus(owed.searches());
            if (owed.projection().proves()) {
                projection = ItemAssessment.WritabilityProjection.PROVEN;
            } else if (projection != ItemAssessment.WritabilityProjection.PROVEN
                    && owed.projection() == ItemAssessment.WritabilityProjection.UNPROVEN) {
                projection = ItemAssessment.WritabilityProjection.UNPROVEN;
            }
        }
        return new ObligationAssessment(asked.criterion(),
                ObligationCoverage.acrossTheReadings(coverage), projection, searched);
    }

    /** The measured half, which a point owed a row always has. */
    public ObligationAssessment owed() {
        return item;
    }

    /**
     * Which of {@code module}'s declarations owe a row here, in the order the point names them.
     *
     * <p>Empty for a point a body's rule settled, and empty for one owed to declarations none of
     * which are this module's — a line this module's values are held to and somebody else's to
     * answer for. The two are one answer to the question asked: this module keeps no account of the
     * point.
     */
    public List<souther.compiler.types.TypeSymbol.AtModule> ownersIn(String module) {
        return attribution instanceof souther.compiler.partition.PointAttribution
                .TheDeclarations owed ? owed.ownersIn(module) : List.of();
    }

    /**
     * Whether a row here is the reading's own to write.
     *
     * <p>The other side of {@link #ownersIn} and not its negation. A point owed to declarations in
     * another module is neither, and a caller reading one question as the other would put that
     * line into this behavior's account.
     */
    public boolean owedToTheReading() {
        return attribution instanceof souther.compiler.partition.PointAttribution.TheReading;
    }

    /**
     * Whether {@code module} is the one that answers for this point.
     *
     * <p>The three the attribution tells apart, put as the one question every reader of it was
     * asking: a line this module's own rule drew, a line one of its declarations owns, and a line
     * owed to declarations somewhere else. A module that merely carries the type reads the third
     * kind and answers for none of it — the row belongs where the declaration is.
     *
     * <p>Here because it is asked in more than one place, and the two spellings of it came apart. An
     * editor deciding whether to offer rows and a search deciding which points to resolve are asking
     * this, and a reader that wrote it as {@link #ownersIn} alone, or as its negation, put a foreign
     * module's line into this one's work.
     */
    public boolean keptBy(String module) {
        return owedToTheReading() || !ownersIn(module).isEmpty();
    }

    /**
     * Whether this point is one of the things {@code behavior} is owed a row for.
     *
     * <p>The one spelling of a behavior's account. A point is in it where the row is the reading's
     * own to write — a line a body's rule drew, and not one a declaration is owed — and that
     * behavior is one of the readings carrying it. Two facts, and every reader of the account wants
     * their conjunction: a report's count, its findings, the strict verdict and the offering. Spelled
     * at each of them, two of the four would drift apart the way {@link #keptBy} records the
     * module's question once did.
     *
     * <p>Not {@link #keptBy}: a point one of this module's declarations owns is that account's and
     * not any behavior's, however many behaviors carry it.
     */
    public boolean belongsToBehaviorAccount(String behavior) {
        return owedToTheReading() && carriedBy(behavior);
    }

    /**
     * Which behaviors read the line at this point, in the order the module declares them.
     *
     * <p>Not part of what the point is — a line is owed once however many behaviors carry the type —
     * and not an account either: which behavior's work this point is takes whose the point is as
     * well, which is {@link #belongsToBehaviorAccount}. This is the fact under it, and is what an
     * editor's offer beside a behavior asks about a declaration's line, since a row written for that
     * behavior settles it whoever owes it.
     */
    public boolean carriedBy(String behavior) {
        return met.keySet().stream().anyMatch(each -> each.behavior().equals(behavior));
    }

    /** The same, as the list of them. Distinct, because a behavior reading one line at two
     *  positions carries it once. */
    public List<String> carriedBy() {
        return met.keySet().stream().map(Reading::behavior).distinct().toList();
    }

    /** How this point relates a row's value to what it is against. */
    public String operator() {
        return demand.criterion().operator();
    }

    /**
     * One reading of the point as a surface says it: where it was read, and what a row there has
     * to do, in that position's own terms.
     *
     * <p>Words for a reader and never a key. Two readings that happen to say the same words are
     * still two entries here — what tells them apart is {@link #where}, and a surface that folded
     * them by their words would be deciding identity from a rendering.
     */
    public record ReadingSaid(Reading where, String at, String asks) {}

    /**
     * How many readings a surface says under the point before saying how many are left.
     *
     * <p>One number, because two surfaces say the readings: a report under its mark and a warning
     * under its sentence. Over {@code crm} one clause is read at 133 positions, and neither surface
     * is a place to list them.
     */
    public static final int READINGS_SAID = 4;

    /**
     * Every reading of the point, as the sentences a surface prints under it, in the order the
     * sentences sort.
     *
     * <p>Sorted by what is printed and by nothing else. The order the readings were made in is the
     * order a walk took, which is what this value exists to keep out of what anybody is shown; and
     * sorting by anything the sentence does not show would give two runs that print the same words
     * in a different order for a reason no reader can see. Whether two of these are one reading is
     * not asked here: the sentence is not the identity, and a sort key need not be one.
     *
     * <p>All of them. Which to show is the surface's ({@link #READINGS_SAID}), so that what is left
     * out is a count the surface says rather than a reading this dropped.
     */
    public List<ReadingSaid> readingsSaid() {
        List<ReadingSaid> out = new ArrayList<>();
        met.forEach((where, at) -> out.add(new ReadingSaid(where, at.axis(),
                new BorderAssessment.Point(at, at(), at.at(at())).asked())));
        out.sort(java.util.Comparator.comparing((ReadingSaid said) -> said.at())
                .thenComparing(ReadingSaid::asks));
        return List.copyOf(out);
    }

    /**
     * What a row here would have to do, written on a quantity called {@code axis}, or null where a
     * declaration has no words for it.
     *
     * <p>The quantity is handed in because no reading of the point names it and this holds no name
     * of its own. What a criterion writes is the level in the terms of the order that level is on,
     * and which order that is, is part of what a point is.
     *
     * <p><b>Asked of the quantity and never of whether the readings agree.</b> A line an {@code
     * invariant} drew is owed once for the module and read at every position the type reaches, so a
     * sentence about the debt may hold nothing that differs between those readings — and whether
     * there is such a sentence is a fact about what the quantity writes a level as
     * ({@link BorderQuantity#statesADeclarationRelativeLevel}), known without asking any reading.
     *
     * <p>It used to be asked by writing the level at every reading and refusing where two of them
     * differed. They differ exactly where the answer is a reading's: a line between two positions
     * writes the level as a distance from the other one, and what that position is called is the
     * path a walk reached it by. So a model with a relation on a case of a sum — read once through
     * the case and once through the sum — was a model whose report could not be produced at all,
     * and the sentence the check refused to write was one nothing could have written (issue #1251).
     */
    public String against(String axis) {
        for (BorderAssessment reading : met.values()) {
            BorderQuantity of = reading.border().cut().of();
            // Which quantity this is is the line's and not this reading's; every reading of one
            // point cuts one carrier at one place, which is checked where their demands are.
            return of.statesADeclarationRelativeLevel()
                    ? demand.criterion().written(of, axis) : null;
        }
        return null;
    }

    /**
     * What this point asks of a row, as a report writes it, on a quantity called {@code axis}.
     *
     * <p>The same sentence a reading's point writes ({@link BorderAssessment.Point#said}), on the
     * quantity the caller has a declaration for rather than on the position a reading met it at.
     * The two are joined by a consumer against one of a border's items, so they are spelled by one
     * rule and not two.
     *
     * <p><b>Total.</b> Every obligation the account counts is one a report names, so a point this
     * cannot write a level for is written as far as it goes — the quantity, and nothing invented
     * for the other side. What is left out is a reading's spelling of another position, which is
     * not the declaration's to give.
     */
    public String said(String axis) {
        String against = against(axis);
        if (against == null) {
            return axis;
        }
        return role().againstTheLine() ? axis + " = " + against
                : axis + " " + operator() + " " + against;
    }

    /**
     * The point, as a surface names it: which of the four it is, and which rule drew the line,
     * with the sources under the names {@code names} gives them.
     *
     * <p>What a body's line gets, since it has no authored spelling of what it is on
     * ({@link #said(String)} is for a line a declaration wrote). No word for where it is either,
     * for the reason above: what a reader is shown of that is each reading's, said under this.
     *
     * <p>Which is why these words are not what tells two points apart. Two lines of one rule can
     * be at two places, and two runs beside one line can stop in two places, and this says the
     * same of both — a consumer joins on {@code obligationId} and shows this.
     */
    public PublishedSentence said(PublishedRuleHandle.WhereARuleIs places) {
        return new PublishedSentence.AroundAHandle(role() + " point of ", handle(places), "");
    }
}
