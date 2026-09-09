package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.core.Core;
import souther.compiler.diag.SourcePos;
import souther.compiler.meta.ModulePath;
import souther.compiler.query.Compilation;
import souther.compiler.query.Scopes;
import souther.compiler.query.Shapes;
import souther.compiler.types.BindingId;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.TypeSymbols;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A clause reads the same whichever compile built the term it states.
 *
 * <p>What a declaration states is published, and two published readings of one unedited source are
 * equal while holding terms that are not the same objects and carry different places. So a reader
 * handed either of them has to come to the same answer, or "equal" is a word about two things a
 * reader can tell apart.
 *
 * <p><b>What this is about is silent.</b> A reader that works out which fields a clause reads by
 * walking the term it was handed looks for the declaring module's bindings in a tree another
 * reading built, finds none of them, and concludes that the clause reads no fields — from which
 * follows that every field it needs is filled, and the clause is read against values that were
 * never put in. Nothing is thrown and nothing is reported; the reading is simply about a clause
 * nobody wrote. So the two readings are put side by side here rather than either being checked
 * against a value written down.
 *
 * <p>Compared as readings and not as terms. Two compiles write the same clause at two places, so
 * the terms differ where the reading does not — which is the whole of what {@link TermMeaning} is
 * for, and using it here is using the thing the boundary is built on.
 */
class AClauseReadsTheSameWhicheverCompileBuiltTheTermTest {

    private static final TypeSymbol.AtModule HELD =
            TypeSymbols.declared(new TypeKey("demo", "Held"));

    /** Two fields and a clause that reads one of them, so which fields it reads is not empty. */
    private static final String SOURCE = """
            module demo exposing ( Held )

            data Held = { ok: Bool, other: Bool }
                invariant kept = ok
            """;

    /**
     * The reading of one compile, handed what another compile published.
     *
     * <p>Both answers are worked out by the same reading over the same scope, so what differs
     * between them is only which compile built the term each was told about.
     */
    @Test
    void aReadingIsTheSameWhicheverCompilePublishedWhatTheClauseStates() {
        Compilation mine = compiled();
        Compilation other = compiled();

        Clauses.StatedClauses own = readOf(mine, Shapes.clauseMeanings(mine.db()));
        Clauses.StatedClauses crossed = readOf(mine, Shapes.clauseMeanings(other.db()));

        assertFalse(own.clauses().isEmpty(), "the reading under test reads the clause at all");
        assertEquals(said(own), said(crossed),
                "what the clause states is what it states, whichever compile built the term it was"
                        + " published as");
        assertEquals(parts(own), parts(crossed),
                "and the rules its author wrote it as are the same rules");
        assertEquals(own.lost(), crossed.lost(),
                "and neither reading dropped a clause the other kept");
    }

    /** What each clause came to, as a reading of it — which is what two of them are compared by. */
    private static Map<Clause.Ref, TermMeaning> said(Clauses.StatedClauses read) {
        Map<Clause.Ref, TermMeaning> out = new LinkedHashMap<>();
        read.clauses().forEach(each -> out.put(each.clause(), TermMeaning.of(each.expr())));
        return out;
    }

    /** What each clause's parts are called. */
    private static Map<Clause.Ref, List<PartId<RuleRef.Invariant>>> parts(
            Clauses.StatedClauses read) {
        Map<Clause.Ref, List<PartId<RuleRef.Invariant>>> out = new LinkedHashMap<>();
        read.clauses().forEach(each -> out.put(each.clause(),
                each.parts().stream().map(Clauses.StatedPart::id).toList()));
        return out;
    }

    /**
     * {@code Held}'s clauses as {@code mine} reads them, told by {@code states} what they state,
     * with every field given a value.
     *
     * <p>Every field, so that a clause is not left to its run-time check for want of one — which is
     * the answer a reader that could not tell which fields are read would fall into.
     */
    private static Clauses.StatedClauses readOf(Compilation mine, ClauseMeanings states) {
        Clauses reading = new Clauses(Scopes.resolved(mine.db(), "demo").value(),
                RuleReadings.declaredBy(mine.db(), "demo"), ClauseLocations.NONE,
                DeclarationReadings.NONE, states);
        Map<BindingId, Core> given = new LinkedHashMap<>();
        reading.bindingsOf(HELD).values()
                .forEach(each -> given.put(each, new Core.Bool(true, Type.BOOL, POS)));
        return reading.statedAt(HELD, given);
    }

    private static final SourcePos POS = new SourcePos(1, 1);

    private static Compilation compiled() {
        Compilation c = Compilation.ofSources(List.of(SOURCE), ModulePath.EMPTY);
        c.answerEverything();
        return c;
    }
}
