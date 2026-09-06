package souther.compiler.query;

import souther.compiler.check.FieldDomains;
import souther.compiler.check.ReadingPolicy;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.StringMachineLookup;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;
import souther.compiler.values.StringFacts;
import souther.compiler.values.StringMachineAnswers;

/**
 * The string machines a declaration's rules come to, answered once per declaration.
 *
 * <p>A reading of a declaration plans its patterns, canonicalises the languages and takes the
 * extents, and every question that reaches the declaration used to read it again — every
 * construction in a body, every input a behavior takes, the count of what a module's types hold.
 * What those readings build is the same machines, because a machine is a fact about a plan, a set
 * or a pair of a language and a stretch, and about nothing else. So the declaration's own reading
 * is made once here, what it built is kept as the declaration's answer, and every other reading
 * borrows from it through the capability {@link #of} hands out.
 *
 * <p>Under the declaration and not under the plan. Keyed by what it is a fact about, a machine
 * would be an answer of no module, which a store keeps for as long as it lives: every pattern an
 * author passes through on the way to the one they mean would stay. Keyed by the declaration,
 * there is one answer per declaration, it is recomputed when the declaration's clauses change, and
 * it goes when the declaration's source does.
 *
 * <p>Nothing is borrowed while the answer is made. The reading here builds every machine itself
 * — its own declaration's and those of the declarations its fields reach — so no question is put
 * to the store from inside this one, and two declarations that reach each other do not ask for each
 * other's answers in a circle.
 */
public final class Machines {

    private Machines() {}

    /** Where a reading gets the answers about a declaration's string machines, asking this store. */
    public static StringMachineLookup of(Db db) {
        return declaration -> {
            Answer<StringFacts> facts = db.ask(new OfDeclaration(declaration));
            return facts.present()
                    ? StringMachineAnswers.borrowing(facts.value()) : StringMachineAnswers.NONE;
        };
    }

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
            StringMachineAnswers recorder = StringMachineAnswers.recording();
            FieldDomains domains = FieldDomains.of(TypeSymbols.declared(named), source.value(),
                    policy.value(),
                    declaration -> declaration.equals(named) ? recorder : StringMachineAnswers.NONE);
            // And whether the rules leave a value at all, which is the question every reading of
            // an input puts to the declaration and the one that meets each language with the
            // whole of the order. Asked here so that the machines it takes are the declaration's
            // answer too, and not built again by the first input that asks.
            domains.infeasible(recorder);
            return Answer.of(recorder.facts());
        }
    }
}
