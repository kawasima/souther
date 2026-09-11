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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * A clause reads the same whichever compile built the term it states.
 *
 * <p>What a declaration states is published, and two readings of one clause written a line apart
 * are equal while holding terms that are neither the same object nor written at the same place. So
 * a reader handed either of them has to come to the same answer, or "equal" is a word about two
 * things a reader can tell apart.
 *
 * <p>Three things are held here at once, and the first two are what make the third worth asking:
 * the published readings are equal, the terms behind them are two objects written at two places,
 * and what a reading of the model comes to is the same either way.
 *
 * <p>Compared as readings and not as terms. Two compiles write the clause at two places, so the
 * terms differ where the reading does not — which is the whole of what {@link TermMeaning} is for,
 * and using it here is using the thing the boundary is built on.
 *
 * <p><b>Nothing known makes this red, and that is worth writing down.</b> It was written expecting
 * to catch a reader that works out which fields a clause reads by walking the term it was handed:
 * looking for the declaring module's bindings in a tree another reading built, finding none, and
 * concluding the clause reads no fields — from which follows that every field it needs is filled.
 * Made to do exactly that, this still passes. A binding is named by the declaration that wrote the
 * field and by a number among that declaration's fields, so two compiles of one source name it
 * alike and the walk finds what it is looking for. The discontinuity that reasoning turns on is
 * not there.
 *
 * <p>So this holds a property rather than guarding a defect: what a reader comes to is a function
 * of what it was told and not of which compile built it. What it would show is a reading below
 * this one letting the place a term was written at change what it concludes; a place published as
 * where to put a caret is not that, and is not compared here.
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
     * The same, with a declaration written above it and saying nothing more about it.
     *
     * <p>A declaration and not a comment. A place is which of the things written in a text it is,
     * so writing a comment moves none of them — and the two compiles below have to have written
     * their terms at two places for the readings being equal to say anything.
     */
    private static final String MOVED = SOURCE.replace("data Held", "data Other\n\ndata Held");

    /**
     * The reading of one compile, handed what another compile published.
     *
     * <p>Both answers are worked out by the same reading over the same scope, so what differs
     * between them is only which compile built the term each was told about.
     */
    @Test
    void aReadingIsTheSameWhicheverCompilePublishedWhatTheClauseStates() {
        Compilation mine = compiled(SOURCE);
        Compilation moved = compiled(MOVED);

        ClauseMeaning.Stated here = published(mine);
        ClauseMeaning.Stated there = published(moved);

        // What the two compiles published is one reading of one clause.
        assertEquals(here.states(), there.states(),
                "one clause moved down its file states what it stated");
        // And it is one reading of two terms: were it one term, everything below would be one
        // answer compared with itself.
        assertNotSame(here.states().termForClauseReading(), there.states().termForClauseReading(),
                "two compiles built two terms, which is what makes this a crossing");
        assertNotEquals(here.states().termForClauseReading().pos(),
                there.states().termForClauseReading().pos(),
                "and wrote them at two places, which is what the readings being equal is about");

        Clauses.StatedClauses own = readOf(mine, Shapes.clauseMeanings(mine.db()));
        Clauses.StatedClauses crossed = readOf(mine, Shapes.clauseMeanings(moved.db()));

        assertFalse(own.clauses().isEmpty(), "the reading under test reads the clause at all");
        assertEquals(said(own), said(crossed),
                "what the clause states is what it states, whichever compile built the term it was"
                        + " published as");
        assertEquals(parts(own), parts(crossed),
                "and the rules its author wrote it as are the same rules");
        assertEquals(own.lost(), crossed.lost(),
                "and neither reading dropped a clause the other kept");
    }

    /** What {@code Held}'s one clause states, as {@code c} publishes it. */
    private static ClauseMeaning.Stated published(Compilation c) {
        List<ClauseMeaning> clauses = Shapes.clauseMeanings(c.db()).of(HELD.key());
        assertEquals(1, clauses.size(), "the declaration under test writes one clause");
        return assertInstanceOf(ClauseMeaning.Stated.class, clauses.getFirst(),
                "and this reading has a form for it");
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
        Clauses reading = new Clauses(new RuleReadingSource(
                Scopes.resolved(mine.db(), "demo").value(),
                RuleReadings.declaredBy(mine.db(), "demo"), states, ClauseLocations.NONE));
        Map<BindingId, Core> given = new LinkedHashMap<>();
        reading.bindingsOf(HELD).values()
                .forEach(each -> given.put(each, new Core.Bool(true, Type.BOOL, POS)));
        return reading.statedAt(HELD, given);
    }

    private static final SourcePos POS = new SourcePos(1, 1);

    private static Compilation compiled(String source) {
        Compilation c = Compilation.ofSources(List.of(source), ModulePath.EMPTY);
        c.answerEverything();
        return c;
    }
}
