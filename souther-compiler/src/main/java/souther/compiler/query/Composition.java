package souther.compiler.query;

import souther.compiler.partition.Generator;
import souther.compiler.partition.ObligationIdentity;
import souther.compiler.partition.StoodInAnswer;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
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
        for (Map.Entry<String, Adequacy.Filling> behavior : generated.entrySet()) {
            // The fill's rows and the ones the requirement search stood in the rules, which is a
            // second search of this behavior's own the way the lines are a third. Taken as rows
            // that say what they were composed for rather than as lines: two searches arriving at
            // one stimulus is one row offered for both things, and a row that kept only the first
            // purpose would be work a person is handed under half of what it does.
            // The fill's rows and the ones at the lines stand the dependencies in with what the
            // behavior requires and nothing more: neither was composed against anything a
            // dependency answers, and a row of a behavior that requires one is a row nothing
            // applies until something does. The rules' rows carry their own, which is what the way
            // asked of them.
            //
            // Where nothing composed those stand-ins, the rows that would have carried them do not
            // go out. What is wrong with them is not their values — it is that nothing applies the
            // behavior they are written for — so offering them would hand a person work that
            // cannot be run, and the reason is on the filling for whoever writes the block.
            List<Generator.GeneratedRow> supplied =
                    behavior.getValue().supplies() instanceof AnswersStoodIn.Stood(
                            var answers)
                            ? standingIn(behavior.getValue().composed().rows(), answers)
                            : List.of();
            List<Generator.GeneratedRow> atLines =
                    behavior.getValue().supplies() instanceof AnswersStoodIn.Stood(
                            var answers) && request.boundaries()
                            ? standingIn(atTheLines(owed.get(behavior.getKey())), answers)
                            : List.of();
            take(byBehavior, behavior.getKey(), supplied, atLines,
                    behavior.getValue().rules().byRule().values());
        }
        // A behavior with nothing of its own to fill can still be the one reading that composed the
        // row a declaration is owed. Left out, that row would be resolved and then dropped on the
        // way to the block.
        for (Map.Entry<String, List<Generator.GeneratedRow>> carrier : owed.entrySet()) {
            if (!generated.containsKey(carrier.getKey())) {
                take(byBehavior, carrier.getKey(), List.of(), carrier.getValue(), List.of());
            }
        }
        SequencedMap<String, List<OfferedRow>> out = new LinkedHashMap<>();
        byBehavior.forEach((behavior, here) -> out.put(behavior, List.copyOf(here.values())));
        return new Composition(request, out, new LinkedHashMap<>(generated), account);
    }

    /**
     * {@code rows} standing the dependencies in with {@code supplies}.
     *
     * <p>Only where a row stands nothing in of its own. A row composed against what a way asks of a
     * dependency carries that answer, and a supply written over it would offer a row that no longer
     * takes the way it was composed for.
     */
    private static List<Generator.GeneratedRow> standingIn(List<Generator.GeneratedRow> rows,
                                                           List<StoodInAnswer> supplies) {
        if (supplies.isEmpty()) {
            return rows;
        }
        List<Generator.GeneratedRow> out = new ArrayList<>(rows.size());
        for (Generator.GeneratedRow row : rows) {
            out.add(row.answers().isEmpty()
                    ? new Generator.GeneratedRow(row.purposes(), row.inputs(), supplies) : row);
        }
        return List.copyOf(out);
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
        // What the rows that answer a dependency by what it was applied to come to, merged as the
        // rows are walked. A row whose table the merge cannot hold is one the block cannot hold,
        // so it is taken out here rather than printed beside a table that answers it another way.
        SequencedMap<ValueName.Behavior, Map<List<String>, StandInTable.Entry>> tables =
                new LinkedHashMap<>();
        List<Generator.UnresolvedCombination> withheld = new ArrayList<>();
        SequencedMap<String, List<OfferedRow>> out = new LinkedHashMap<>();
        rowsByBehavior.forEach((behavior, here) -> {
            List<OfferedRow> left = new ArrayList<>();
            for (OfferedRow row : here) {
                if (!kept.contains(row.key())) {
                    continue;
                }
                Generator.UnresolvedCombination cost = merge(tables, row);
                if (cost != null) {
                    withheld.add(cost);
                    continue;
                }
                left.add(row);
            }
            if (!left.isEmpty()) {
                out.put(behavior, List.copyOf(left));
            }
        });
        SequencedMap<ValueName.Behavior, StandInTable> written = new LinkedHashMap<>();
        tables.forEach((dependency, rows) ->
                written.put(dependency, new StandInTable(dependency, List.copyOf(rows.values()))));
        return new Offering(request, out, searched, account, answered, written,
                List.copyOf(withheld));
    }

    /**
     * {@code row}'s table rows merged into what the block already writes, or why they do not fit.
     *
     * <p>A row answering every call states no table row and always fits: what it writes is its own
     * {@code with}, which the run prefers to whatever a module says. What does not fit is a row
     * answering one call the block already answers another way — two blocks are not rows of one
     * table, so writing a second recovers nothing, and which row goes out is what was composed
     * first.
     */
    private static Generator.UnresolvedCombination merge(
            SequencedMap<ValueName.Behavior, Map<List<String>, StandInTable.Entry>> tables,
            OfferedRow row) {
        Map<ValueName.Behavior, Map<List<String>, StandInTable.Entry>> adding = new LinkedHashMap<>();
        for (StoodInAnswer each : row.answers()) {
            if (!(each.asking() instanceof StoodInAnswer.Asking.OfOne(var _, var appliedTo))) {
                continue;
            }
            StandInTable.Entry writing = new StandInTable.Entry(appliedTo, each.value());
            StandInTable.Entry already = tables.getOrDefault(each.dependency(), Map.of())
                    .get(writing.writtenAs());
            if (already != null && !already.answers().text().equals(each.value().text())) {
                return new Generator.UnresolvedCombination(row.key().written(),
                        Generator.UnresolvedCombination.Reason.A_TABLE_IS_WRITTEN_ONCE_FOR_A_MODULE);
            }
            adding.computeIfAbsent(each.dependency(), _ -> new LinkedHashMap<>())
                    .put(writing.writtenAs(), writing);
        }
        // Put in after every one of them was looked at, so a row the block cannot hold leaves the
        // table as it was — half of one merged in would answer calls for a row nobody is offered.
        adding.forEach((dependency, rows) ->
                tables.computeIfAbsent(dependency, _ -> new LinkedHashMap<>()).putAll(rows));
        return null;
    }
}
