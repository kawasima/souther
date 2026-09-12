package souther.compiler.query;

import souther.compiler.check.FieldDomains;
import souther.compiler.check.ReadingPolicy;
import souther.compiler.check.RuleReadingContext;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.values.StringMachineAnswers;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;
import souther.compiler.values.StringFacts;

/**
 * The string machines a declaration's rules come to, answered once per declaration.
 *
 * <p>A reading of a declaration plans its patterns, canonicalises the languages and takes the
 * extents, and every question that reaches the declaration used to read it again — every
 * construction in a body, every input a behavior takes, the count of what a module's types hold.
 * What those readings build is the same machines, because a machine is a fact about a plan, a set
 * or a pair of a language and a stretch, and about nothing else. So the declaration's own reading
 * is made once here, what it built is kept as the declaration's answer, and every other reading
 * borrows from it through the capability the store hands out ({@code Db.readings}).
 *
 * <p>Under the declaration and not under the plan. Keyed by what it is a fact about, a machine
 * would be an answer of no module, which a store keeps for as long as it lives: every pattern an
 * author passes through on the way to the one they mean would stay. Keyed by the declaration,
 * there is one answer per declaration, it is recomputed when the declaration's clauses change, and
 * it goes when the declaration's source does.
 *
 * <p>Which is about what a store keeps and not about what a walk may be spared. Where a set's
 * strings stop is settled by the set, under an allowance minted for that set, so it is the same
 * answer wherever it is met — and what the revision has worked out about sets is held for the
 * revision and dropped with it ({@link souther.compiler.check.DeclarationReadings#extents}).
 * Nothing there is an answer of anything, and nothing there outlives the world it was worked out
 * in.
 *
 * <p>No question is put to the store while the answer is made. The reading here works out its own
 * declaration's machines and those of the declarations its fields reach rather than asking for
 * their answers, so two declarations that reach each other do not wait on each other in a circle.
 * What it takes from what the revision knows is asked of nobody: it is a fact about a set, worked
 * out by whichever reading met it first.
 *
 * <p>The reading made here is the declaration's canonical one, and the store's lender hands it to
 * whoever asks next for the rest of the revision. What the store keeps is still what is written
 * above and only that: the machines, under the declaration, compared as the values they are. The
 * reading beside them is shared work and is not an answer of anything.
 */
public final class Machines {

    private Machines() {}

    /** The machines {@code named}'s rules come to, as its own reading builds them. */
    public record OfDeclaration(TypeKey named) implements Key<StringFacts> {

        @Override
        public String module() {
            return named.module();
        }

        @Override
        public Answer<StringFacts> compute(Db db) {
            Answer<RuleReadingSource> source = Shapes.ruleReading(db, named.module());
            Answer<ReadingPolicy> policy = db.ask(new Front.Reading());
            if (!source.present() || !policy.present()) {
                return Answer.absent();
            }
            StringMachineAnswers recorder =
                    StringMachineAnswers.unborrowed(db.readings().extents());
            RuleReadingContext reading =
                    RuleReadingContext.of(source.value(), policy.value(), db.readings());
            FieldDomains domains = FieldDomains.of(TypeSymbols.declared(named),
                    reading.whileTheAnswerIsMade(named, recorder));
            // And whether the rules leave a value at all, which is the question every reading of
            // an input puts to the declaration and the one that meets each language with the
            // whole of the order. Asked here so that the machines it takes are the declaration's
            // answer too, and not built again by the first input that asks.
            domains.infeasible(recorder);
            return Answer.of(recorder.facts());
        }
    }
}
