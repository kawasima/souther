package souther.compiler.query;

import souther.compiler.partition.Generator;
import souther.compiler.partition.ObligationIdentity;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.Set;

/**
 * Everything the two searches composed, under the behavior each row is written for.
 *
 * <p>Where both halves meet. A behavior's own rows and the rows a declaration's line is owed are
 * composed by two searches asked in two ways, and they meet as work for one person: a line is one
 * piece of work and is offered once, in the terms of whichever reading composed it. Put together
 * where the block is written, the meeting was a step of the layout — so what a run offers could
 * only be said by printing it.
 *
 * <p><b>Not what a person is handed.</b> A row here may answer what another here answers, and which
 * of them goes out is settled by asking what each would settle. {@link Offering} is that answer, and
 * this is what it is made from — two types, because a renderer handed one of these would print rows
 * nobody chose, which is what the reduction exists to stop.
 *
 * <p>Nothing here is evidence of coverage. A row this holds is a question — these inputs, and what
 * does the system answer? — and what it would settle if it were written is not something this says.
 *
 * @param request  what was asked for, which is what settles which rows are here
 * @param rowsByBehavior one entry per behavior with rows, in the order they were asked about
 * @param searched what each behavior's own search came to, keyed the way a report keys them
 * @param account every point of a line this request answers for, whosever it is — a body's own and
 *                 its declarations' alike — or null where the request asked for no boundary rows,
 *                 which is not the same as a request that asked and found none
 */
public record Composition(OfferingRequest request,
                          SequencedMap<String, List<OfferedRow>> rowsByBehavior,
                          SequencedMap<String, Adequacy.Filling> searched, BorderAccount account) {

    public Composition {
        rowsByBehavior =
                Collections.unmodifiableSequencedMap(new LinkedHashMap<>(rowsByBehavior));
        searched = Collections.unmodifiableSequencedMap(new LinkedHashMap<>(searched));
    }

    /**
     * What the two searches composed, before anything asks what the rows settle.
     *
     * <p>Not what a person is handed. A row here may answer what another here answers, and which of
     * them goes out is settled by asking — {@link Adequacy#offeredFor} is where that happens, and
     * what it hands back is the offering. So what it answers is a question nobody has put yet.
     *
     * <p>Walked in the order the behaviors were asked about, and within a behavior the cells before
     * the lines. The order a person is offered the rows in is the order they were composed in, and a
     * block read against the one before it is read by somebody who did not change the model between
     * them.
     *
     * @param generated one filling per behavior, keyed the way a report keys them
     * @param account  the rows the module's declarations are owed, or null where the request asked
     *                  for no boundary rows. One row per point of a line however many behaviors
     *                  carry the type, and under the behavior whose reading composed it
     */
    public static Composition composed(OfferingRequest request,
                                    Map<String, Adequacy.Filling> generated,
                                    BorderAccount account) {
        Map<String, List<Generator.GeneratedRow>> owed = account == null
                ? Map.of() : account.rowsByCarrier();
        SequencedMap<String, Map<RowKey, OfferedRow>> byBehavior = new LinkedHashMap<>();
        // Every behavior with rows, its own and the ones it carries for a declaration alike. Walked
        // as one list because a row of either kind is a row of that behavior, and a walk that took
        // the second somewhere else is a way into the block that the first one's rules never
        // reached — which is what let a carrier's rows out standing nothing in.
        SequencedMap<String, Object> behaviors = new LinkedHashMap<>();
        generated.keySet().forEach(name -> behaviors.put(name, name));
        owed.keySet().forEach(name -> behaviors.put(name, name));
        for (String behavior : behaviors.keySet()) {
            Adequacy.Filling filling = generated.get(behavior);
            // The fill's rows and the ones the requirement search stood in the rules, which is a
            // second search of this behavior's own the way the lines are a third. Taken as rows
            // that say what they were composed for rather than as lines: two searches arriving at
            // one stimulus is one row offered for both things, and a row that kept only the first
            // purpose would be work a person is handed under half of what it does.
            //
            // Every one of them already stands the behavior's dependencies in. A row is composed
            // with its stand-ins on it, and a search that could compose none of them composed no
            // rows — so there is nothing to check here, and a check would be a second place
            // deciding what a row needs to be run.
            take(byBehavior, behavior,
                    filling == null ? List.of() : filling.composed().rows(),
                    atTheLines(owed.get(behavior)),
                    filling == null ? List.of() : filling.rules().byRule().values());
        }
        SequencedMap<String, List<OfferedRow>> out = new LinkedHashMap<>();
        byBehavior.forEach((behavior, here) -> {
            if (!here.isEmpty()) {
                out.put(behavior, List.copyOf(here.values()));
            }
        });
        return new Composition(request, out, new LinkedHashMap<>(generated), account);
    }

    /** One behavior's rows, joined onto whatever it already offers. */
    private static void take(SequencedMap<String, Map<RowKey, OfferedRow>> byBehavior,
                             String behavior, List<Generator.GeneratedRow> cells,
                             List<Generator.GeneratedRow> lines,
                             Collection<Generator.GeneratedRow> rules) {
        // One block per behavior, however many kinds of row it holds. Rows of one behavior written
        // under two headings are legal and read as two lists of something, which they are not.
        Map<RowKey, OfferedRow> here =
                byBehavior.computeIfAbsent(behavior, _ -> new LinkedHashMap<>());
        for (Generator.GeneratedRow row : cells) {
            RowKey key = RowKey.of(behavior, row);
            here.put(key, here.computeIfAbsent(key,
                    _ -> new OfferedRow(key, row.inputs(), row.answers(), List.of())).and(row.purposes()));
        }
        // The lines, joined on the stimulus and never on what they were composed for. A row at a
        // point carries a purpose no offered row may be named after — {@link OfferedRow} refuses
        // one, because what a line is owed is answered in the account under the declaration that
        // owes it rather than by a word over a row — so there is nothing here to union, and the
        // entry a stimulus already has keeps the purposes it has.
        //
        // Which is not a purpose going missing. A row a line and a rule arrive at alike is one row,
        // and the rules below add their purpose to whatever entry this left: what a person is shown
        // is that the row is for the rule, and that it also stands at a line is the account's
        // answer and not this row's label.
        for (Generator.GeneratedRow row : lines) {
            RowKey key = RowKey.of(behavior, row);
            here.putIfAbsent(key, new OfferedRow(key, row.inputs(), row.answers(), List.of()));
        }
        // And the rules, after the lines. What a row settles decides whether it is kept and the
        // order decides which of two that settle the same things is; the body's own lines are
        // offered before what a search of the ways composed, so an edit to the body does not move
        // the row a line is offered at.
        //
        // Through the same join as the cells, and never the one above: a stimulus a line and a
        // rule arrive at alike is one row for both, and a row that kept only what it was reached
        // by first would be work a person is handed under half of what it does.
        for (Generator.GeneratedRow row : rules) {
            RowKey key = RowKey.of(behavior, row);
            here.put(key, here.computeIfAbsent(key,
                    _ -> new OfferedRow(key, row.inputs(), row.answers(), List.of())).and(row.purposes()));
        }
    }

    /**
     * The rows at one behavior's lines, which are the ones the account resolved under it.
     *
     * <p>One source, whoever owes the line. A row at a point is what a search over every reading of
     * that point composed, and the account is where that search is made.
     *
     * <p><b>And not what the behavior's own boundary search built beside it.</b> That search builds
     * at each place a line was met, so a line read at two positions of one behavior comes back with
     * a row at each — a form of two positions gets one putting the value at the first and one
     * putting it at the second, for one point of one declaration's line. A point is one row to
     * write, so the second of them is a piece of work nobody is owed, and the two roads differ in
     * nothing else: every other row either produces is the same row for the same point under the
     * same behavior.
     *
     * <p>Public because it is a question and not a step of the layout: what is offered at one
     * behavior's lines is what a reader of the block beside that behavior sees, and it is asked
     * elsewhere.
     */
    public static List<Generator.GeneratedRow> atTheLines(List<Generator.GeneratedRow> owed) {
        return owed == null ? List.of() : List.copyOf(owed);
    }

    /** How many pieces of work this holds, which is what a block says at the top of it. */
    public int count() {
        return rowsByBehavior.values().stream().mapToInt(List::size).sum();
    }

    /**
     * What a person is handed: these rows, less the ones nothing would miss.
     *
     * <p>The rows and nothing else changes. What the searches came to is what they came to whatever
     * a person is handed afterwards — a row not offered was still composed, and the note beside a
     * search that came to nothing says what happened rather than what is in the block.
     *
     * <p><b>Reachable from this package and no further.</b> What may be passed here is what asking
     * came to, and the asking is {@link Settlements}; a caller outside could hand in every row and
     * an empty answer, which is the raw composition under the name of an offering. Closing the
     * constructor and leaving the one call that reaches it open would have left the same door with
     * a longer name on it.
     */
    Offering keeping(Set<RowKey> kept, Set<ObligationIdentity> answered) {
        SequencedMap<String, List<OfferedRow>> out = new LinkedHashMap<>();
        rowsByBehavior.forEach((behavior, here) -> {
            List<OfferedRow> left = here.stream().filter(row -> kept.contains(row.key())).toList();
            if (!left.isEmpty()) {
                out.put(behavior, left);
            }
        });
        return new Offering(request, out, searched, account, answered);
    }
}
