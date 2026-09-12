package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.ast.Hir;
import souther.compiler.query.Compilation;
import souther.compiler.query.Front;
import souther.compiler.query.Shapes;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a count of how many values a type has was short of is what the readings it made record, and
 * not what happened while it ran.
 *
 * <p>Two things follow from that and neither follows from the other. A reading records a rule that
 * never arrived apart from the places it stopped for reasons of its own, so a count is short only
 * where the first happened; and a reading handed on carries what it was short of, so a count is
 * short whether it read the clauses itself or was lent a reading of them.
 */
class WhatACountIsShortOfIsTheReadingsAndNotTheRunsTest {

    /** A model with a rule no reading gets to the end of, beside declarations whose rules it does
     *  read whole. The regex is over a string with no length written on it, so where the values it
     *  admits stop is not a place any reading here reaches. */
    private static final String MODEL = """
            module demo

            data Email = String
                invariant matches("[^@]+@[^@]+", value)

            data Held = { n: Int }
                invariant n >= 1

            data Row = { ...Held, e: Email }
            """;

    /**
     * A reading that stopped short of a rule it had is not a count that never got one.
     *
     * <p>The two live in one place — what the reading did not gather — and are one word to anything
     * asking whether it gathered everything. What decides whether an answer about a type having no
     * value may be published at all is only the second: a run allowed more meets the first again
     * and comes to the same rules, and no run whatever meets a rule nobody could work out.
     *
     * <p>Written as one pair with one thing between them. The first count reads a model that stops
     * short and nothing else; the second reads the same model with one declaration's clauses
     * refused. Held apart in two models, what moved between the runs would be the models.
     */
    @Test
    void aReadingThatStoppedShortHasStillReadEveryRuleItWasGiven() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        RuleReadingSource whole = RuleReadings.of(compilation, module);
        ReadingPolicy policy = compilation.db().ask(new Front.Reading()).value();

        assertTrue(TypeCardinality.solve(declarationsOf(compilation, module), whole, policy)
                        .everyRuleReached(),
                "a reading that stopped where it could go no further was given every rule there is,"
                        + " and a count over it is a count of what the model states");

        InvariantChecker.Seeded email = InvariantChecker.seedFields(
                named(compilation, module, "Email"), whole, policy, DeclarationReadings.NONE);
        assertFalse(email.notGathered().isEmpty(),
                "the model says nothing here unless a reading of it does stop somewhere");
        assertFalse(email.clausesNotExpanded(),
                "and stops for a reason that is not a rule failing to arrive");

        assertFalse(TypeCardinality.solve(declarationsOf(compilation, module),
                        refusing(new TypeKey(module, "Held"), whole), policy).everyRuleReached(),
                "and the same count is short as soon as one declaration's clauses are ones nobody"
                        + " could work out");
    }

    /**
     * A count answered out of readings somebody else made is short of what those readings were
     * short of.
     *
     * <p>Asked of the readings and not of the looking up, which the second count does none of. What
     * a lender hands over is the reading of a declaration under one source and one policy, so a
     * count that asks for the same declarations under the same terms reads no clause at all — and a
     * count that took what it was short of from the clauses it happened to ask for would come back
     * saying it had read every rule of a model it never read.
     */
    @Test
    void aCountAnsweredOutOfLentReadingsIsShortOfWhatTheyWereShortOf() {
        Compilation compilation = Compilation.ofSource(MODEL, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        ReadingPolicy policy = compilation.db().ask(new Front.Reading()).value();
        List<Hir.Def> declarations = declarationsOf(compilation, module);

        AtomicInteger asked = new AtomicInteger();
        RuleReadingSource source =
                counting(asked, refusing(new TypeKey(module, "Held"),
                        RuleReadings.of(compilation, module)));
        DeclarationReadings lender = compilation.db().readings();

        assertFalse(TypeCardinality.solve(declarations, source, policy, lender).everyRuleReached(),
                "the count that read the clauses is short of the one it was refused");
        int read = asked.get();
        assertTrue(read > 0, "and it read some");

        assertFalse(TypeCardinality.solve(declarations, source, policy, lender).everyRuleReached(),
                "and so is the count that was lent those readings");
        assertEquals(read, asked.get(),
                "which is lent them, rather than reading the clauses a second time");
    }

    /** {@code source}, with {@code declaration} one nobody could work out. */
    private static RuleReadingSource refusing(TypeKey declaration, RuleReadingSource source) {
        return new RuleReadingSource(source.symbols(), source.invariants(),
                named -> named.equals(declaration) ? null : source.published().of(named),
                source.written());
    }

    /** The same source, counting what is asked of its declarations. One source and one origin, so
     *  two counts reading under it are reading one world. */
    private static RuleReadingSource counting(AtomicInteger asked, RuleReadingSource source) {
        return new RuleReadingSource(source.symbols(), source.invariants(), named -> {
            asked.incrementAndGet();
            return source.published().of(named);
        }, source.written());
    }

    /** The name {@code module} declares {@code declaration} under, taken from what it wrote. */
    private static TypeSymbol.AtModule named(Compilation compilation, String module,
                                             String declaration) {
        TypeKey key = new TypeKey(module, declaration);
        return declarationsOf(compilation, module).stream()
                .map(Hir.Def::declares)
                .filter(each -> each instanceof TypeSymbol.AtModule at && at.key().equals(key))
                .map(TypeSymbol.AtModule.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError(key + " is not declared here"));
    }

    private static List<Hir.Def> declarationsOf(Compilation compilation, String module) {
        return compilation.db().ask(new Shapes.Prepared(module)).value().defs().stream()
                .map(each -> each.declaration().node()).map(Hir.Def.class::cast).toList();
    }
}
