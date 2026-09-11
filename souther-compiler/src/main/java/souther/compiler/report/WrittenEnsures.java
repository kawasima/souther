package souther.compiler.report;

import souther.compiler.ast.Hir;
import souther.compiler.cst.SourceLayout;
import souther.compiler.diag.PhysicalRegion;
import souther.compiler.diag.QuotedFrom;
import souther.compiler.query.Answer;
import souther.compiler.query.Bodies;
import souther.compiler.query.Db;
import souther.compiler.query.Front;
import souther.compiler.source.SourceId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a behavior has written in its {@code ensures}, in the author's own words.
 *
 * <p>Cut out of the source and not printed from what the clause was read to mean. The two are
 * different things and only one of them is what a reader is owed here: a clause the author wrote is
 * text, and a rule the checker holds is that text resolved, typed and specialized per case
 * ({@link souther.compiler.check.BehaviorContract}). Rendering the second would hand back the
 * compiler's equivalent words for the author's, and the day a normalization changed one of them the
 * quote would move without anybody editing anything.
 *
 * <p>Read off the settled module and not off the contracts, which is the same decision seen from the
 * other side. A contract is a clause the checker could read: {@link Bodies.Contracts} takes each
 * behavior's reading on its own and drops the ones that were refused, so a behavior carrying a clause
 * this compiler would not accept has no entry there. {@code souther examples} says what is wrong with
 * the source and then reports what it observed, so that behavior's rows are offered anyway — a
 * behavior whose arm names a data that is no case of its answer is refused and still gets a row per
 * case of a sum it takes. A quote taken from the contracts would print nothing over those rows, which
 * reads as a behavior that states nothing rather than one whose statement was refused. The words are
 * on the page either way, and that is the whole of what the heading claims.
 */
final class WrittenEnsures {

    /**
     * The clauses of each behavior of {@code module}, keyed by the name the behavior is declared
     * under, in the order they are written. A behavior carrying none is not in the map, and neither
     * is one whose clauses an author did not write.
     *
     * <p>Those two absences are the same fact and are the only ones there are. Not being here means
     * nothing was written, which is what the heading over the rows would have claimed; it never
     * means a clause was written and this could not get at it. A clause the positions say is in a
     * source this compile holds is one this can cut, so failing to is this compiler being wrong
     * about its own store and is raised rather than dropped — dropping it would put "written and not
     * quoted" under "not written", which is the reading this whole class exists to keep apart.
     */
    static Map<String, List<String>> of(Db db, String module) {
        Answer<Hir.Module> settled = db.ask(new Bodies.Settled(module));
        if (!settled.present()) {
            return Map.of();
        }
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Hir.BehaviorDef behavior : settled.value().behaviors()) {
            if (!(behavior instanceof Hir.SpecBehavior spec) || spec.ensures().isEmpty()) {
                continue;
            }
            List<String> written = quoted(db, spec);
            if (written != null) {
                out.put(spec.name(), written);
            }
        }
        return out;
    }

    /**
     * Every clause of one behavior as it is written, or nothing where an author wrote none of them.
     *
     * <p>Asked of the clauses and not of the behavior's name, because it is the clauses that are
     * being quoted. A behavior a pass wrote carries positions in no text anybody holds, and there is
     * nothing of its author's to print; some of them written and some not is a shape nothing builds
     * — a clause is read off the declaration it is written on, and the passes that rewrite what one
     * states carry its position over — so it is said rather than half printed.
     */
    private static List<String> quoted(Db db, Hir.SpecBehavior spec) {
        long written = spec.ensures().stream()
                .filter(clause -> clause.pos().quotedFrom()
                        instanceof QuotedFrom.ASourceThisCompileHolds)
                .count();
        if (written == 0) {
            return null;
        }
        if (written != spec.ensures().size()) {
            throw new IllegalStateException("a behavior whose clauses are written in part: "
                    + spec.name());
        }
        List<String> out = new ArrayList<>();
        for (Hir.EnsuresClause clause : spec.ensures()) {
            SourceId source = ((QuotedFrom.ASourceThisCompileHolds) clause.pos().quotedFrom())
                    .source();
            Answer<String> text = db.ask(new Front.Text(source));
            if (!text.present()) {
                throw new IllegalStateException("no text for a source this compile holds: "
                        + source + ", for a clause of " + spec.name());
            }
            out.add(cut(text.value(),
                    SourceLayout.of(text.value(), source).resolve(clause.region()),
                    spec.name()));
        }
        return List.copyOf(out);
    }

    /**
     * The characters {@code region} covers, with the indentation the declaration sits at taken off
     * the lines under the first.
     *
     * <p>A clause begins at the {@code ensures} keyword, so the first line of the cut carries none of
     * that indentation and the rest carry all of it. Taking the start column off the others is what
     * keeps a clause written over several lines the shape its author gave it — the arms stay where
     * they were put relative to the keyword — while letting the block indent the whole of it as one.
     *
     * <p>A region that does not fall inside the text is this compiler being wrong about a source it
     * says it holds, so it is raised. Nothing about the model can put it here: the numbers were made
     * by reading this same text, and the passes that rewrite what a clause states carry them over
     * unchanged.
     */
    private static String cut(String text, PhysicalRegion region, String behavior) {
        if (region == null) {
            throw new IllegalStateException("a written clause of " + behavior + " covers nothing");
        }
        List<String> lines = text.lines().toList();
        int first = region.start().line();
        int last = region.end().line();
        if (first < 1 || last > lines.size() || first > last) {
            throw outside(region, behavior);
        }
        int declaredAt = region.start().column() - 1;
        StringBuilder out = new StringBuilder();
        for (int n = first; n <= last; n++) {
            String line = lines.get(n - 1);
            int from = n == first ? declaredAt : Math.min(declaredAt, indentOf(line));
            int to = n == last ? region.end().column() - 1 : line.length();
            if (from > line.length() || to > line.length() || to < from) {
                throw outside(region, behavior);
            }
            out.append(line, from, to);
            if (n < last) {
                out.append('\n');
            }
        }
        return out.toString();
    }

    private static IllegalStateException outside(PhysicalRegion region, String behavior) {
        return new IllegalStateException("a clause of " + behavior + " is written at " + region
                + ", which is not in the text it was read from");
    }

    private static int indentOf(String line) {
        int i = 0;
        while (i < line.length() && Character.isWhitespace(line.charAt(i))) {
            i++;
        }
        return i;
    }

    private WrittenEnsures() {}
}
