package souther.compiler.inputs;

import org.junit.jupiter.api.Test;

import souther.compiler.check.DeclaredSig;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.ReadingPolicy;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.ReadAs;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Every answer a position can give about a distinction is one some model here is answered with.
 *
 * <p>Which arm a position comes back with is what a consumer acts on: a refusal is a case nobody
 * may write a row at, an admission is one a caller can supply, and an unsettled answer is neither.
 * A change that moves a model from one of them to another is a change to what the compiler says,
 * and it is invisible to a suite that never asks for the arm it moved to.
 *
 * <p><b>Counted from the type and not from a list.</b> The arms are read off {@link Admits}'s own
 * permitted subclasses, so an arm added later is one this fails on until some model here is
 * answered with it. Written out instead, this would be a copy of the declaration that stops being
 * one the day the declaration grows.
 *
 * <p>One model answers all three, which is the point of it: the rules of {@code R} refuse
 * {@code k = B} and leave {@code k = A}, and a reading that cannot hold what the two clauses leave
 * settles neither.
 */
class EveryAnswerAPositionGivesAboutADistinctionIsOneSomeModelHereGetsTest {

    /**
     * Only {@code (k = A, n = "0")} satisfies both clauses — a {@code k} of {@code B} is asked for
     * with {@code n = "1"} by one and with {@code n = "0"} by the other — so no value of this type
     * has {@code k = B}.
     */
    private static final String SOURCE = """
            module demo

            data A
            data B
            data K = A | B
            data Taken

            data R = { k: K, n: String }
                invariant one = (k == A && n == "0") || (k == B && n == "1")
                invariant two = (k == A && n == "0") || (k == B && n == "0")

            behavior take : (r: R) -> Taken
            """;

    private static Admits admissionOf(String case_, ReadingPolicy policy) {
        Compilation compilation = Compilation.ofSource(SOURCE, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        assertNotNull(sigs);
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        Position k = InputDomain.of(sigs.get("take"), rules, policy).positions().stream()
                .filter(p -> p.path().toString().equals("r.k"))
                .findFirst().orElseThrow();
        return k.admissionOf(TypeSymbols.declared(new TypeKey(rules.symbols().module(), case_)));
    }

    @Test
    void theRulesRefuseACaseAndLeaveAnother() {
        assertEquals(new Admits.Refused(), admissionOf("B", ReadAs.THE_COMPILATION_DOES),
                "no value of this type has k = B, and every rule saying so was read");
        assertEquals(new Admits.Admitted(), admissionOf("A", ReadAs.THE_COMPILATION_DOES),
                "and the case they do leave is one a row may be written at");
    }

    /**
     * And a reading that cannot hold what the two clauses leave settles neither, which is the
     * answer that is not about the model.
     *
     * <p>Neither: what such a reading reports a position as holding is an upper bound, a case it had
     * no reason to remove, and that is not an admission. It answers the same for the case the rules
     * refuse and for the one they leave, because it cannot tell them apart.
     */
    @Test
    void andAReadingThatCannotHoldThemSettlesNeither() {
        assertEquals(new Admits.Unsettled(new Unsettlement.AlternativesNotSeparated()),
                admissionOf("B", ReadAs.MERGING_WHAT_A_CHOICE_LEAVES));
        assertEquals(new Admits.Unsettled(new Unsettlement.AlternativesNotSeparated()),
                admissionOf("A", ReadAs.MERGING_WHAT_A_CHOICE_LEAVES),
                "one reading answers for the position, and it answers the same for both cases");
    }

    /**
     * And between them the models here are answered with every arm there is.
     *
     * <p>The arm an answer is is what a consumer switches on, so an arm nothing here is answered
     * with is one a change may move a model onto with nothing to notice.
     */
    @Test
    void andBetweenThemEveryArmIsAnswered() {
        Set<Class<?>> answered = new LinkedHashSet<>();
        for (ReadingPolicy policy : Set.of(ReadAs.THE_COMPILATION_DOES,
                ReadAs.MERGING_WHAT_A_CHOICE_LEAVES)) {
            for (String case_ : Set.of("A", "B")) {
                answered.add(admissionOf(case_, policy).getClass());
            }
        }

        assertEquals(Set.of(Admits.class.getPermittedSubclasses()),
                answered, "read off the type, so an arm added later is one this asks for");
    }
}
