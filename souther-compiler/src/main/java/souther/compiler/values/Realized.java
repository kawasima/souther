package souther.compiler.values;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A reading worked out, together with what could not be built while working it out.
 *
 * <p>Three facts settled by one piece of work and handed over together. Which values each position
 * admits is one; whether the reading admits anything at all is the second; and which limit stopped
 * this compiler where it stopped is the third. They are not separable questions — the second and
 * third are only answerable by doing the work the first needed — and a caller given the first alone
 * has to guess the others from what it holds. What it holds is a set widened to every value, and
 * every guess made from that is the wrong one: it reads a position nobody could work out as one the
 * rules left open.
 *
 * <p><b>The two shortfalls are apart because they are owed to different people.</b> What is about a
 * rule may be filed under that rule and shown to an author as something to change. What is about the
 * answer may not: the same rules in another order would have been built, so there is no rule to
 * name. A store that took either would be a store whose type says less than the model does.
 *
 * <p>Which is why the three of them are not a way in, and why there is no way in that takes the
 * reading alone either. Handed the parts side by side, a caller writes a reading beside shortfalls
 * no work refused; handed the reading, a caller says of a reading that came from somewhere that
 * nothing was refused while it was made, which is a claim about work it did not do and can be false
 * of the very reading it is handed. So one is reached by doing the work
 * ({@link AdmissibleValues#realize}), and there is no second way.
 *
 * <p>{@code aboutARule} is not per position: an allowance is held per position and every rule
 * reaching one pays into it, so the place is what the spending was arranged by and is not what any
 * of it is about.
 */
@souther.compiler.reading.StateOfAReading
public final class Realized<A> {

    /** The parts, together — see {@link AdmissibleValues.Parts}, whose reasoning this is. */
    private record Parts<A>(AdmissibleValues<A> values,
                            Set<Unbuilt.RuleShortfall<A>> aboutARule,
                            List<Unbuilt.AnswerShortfall<A>> aboutTheAnswer) {

        private Parts {
            // Held in the order they were recorded, which nothing may read and every run has to
            // give the same: an immutable copy iterates in an order salted per run of the machine,
            // and a reader that seeded anything from one would report a model two ways on two days.
            aboutARule = Collections.unmodifiableSet(new LinkedHashSet<>(aboutARule));
            aboutTheAnswer = List.copyOf(aboutTheAnswer);
        }
    }

    private final Parts<A> parts;

    /** The one constructor there is, and it takes the parts as one — see
     *  {@link AdmissibleValues}, where a maker handed the parts is what may not be written. */
    private Realized(Parts<A> parts) {
        this.parts = parts;
    }

    /**
     * What a working-out came to, as the two things it settled.
     *
     * <p>Handed one thing and not two. The reading and the record of the work are settled together
     * and only realization can put them together ({@link AdmissibleValues.Outcome}), so what comes
     * back holds shortfalls that piece of work noted — handed them separately, a caller pairs a
     * reading whose positions an allowance ran out on with a record that noted nothing, and the
     * sentence this type says about itself is false of it.
     *
     * <p>Which of the two a refusal is owed to is read off the record here, since that is settled
     * where it was noted.
     */
    static <A> Realized<A> of(AdmissibleValues.Outcome<A> outcome) {
        return new Realized<>(new Parts<>(outcome.values(), outcome.work().aboutARule(),
                outcome.work().aboutTheAnswer()));
    }

    /** What the reading leaves, every position it could not work out widened to every value. */
    public AdmissibleValues<A> values() {
        return parts.values();
    }

    /** What a rule of the model is answerable for, each saying which written thing asked for what
     *  was refused. */
    public Set<Unbuilt.RuleShortfall<A>> aboutARule() {
        return parts.aboutARule();
    }

    /** What the answer is answerable for, naming no rule and nothing written. */
    public List<Unbuilt.AnswerShortfall<A>> aboutTheAnswer() {
        return parts.aboutTheAnswer();
    }

    /**
     * The same answer, unable to speak for {@code these} because a choice offered an alternative
     * nothing could read.
     *
     * <p>What was refused while working the reading out is what it was, and this adds nothing to
     * it: the positions a choice left open are a fact about the branches anybody can be in, and
     * they are carried by the reading — see {@link AdmissibleValues#alsoOpenedAt}.
     */
    public Realized<A> alsoOpenedAt(Set<A> these) {
        return these.isEmpty() ? this
                : new Realized<>(new Parts<>(values().alsoOpenedAt(these), aboutARule(),
                        aboutTheAnswer()));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Realized<?> it && parts.equals(it.parts);
    }

    /** What it holds, as this kind of value — see {@link ValueHash}. The parts are a record, and a
     *  record's number is one its last component joins unchanged, so handing that up is handing up
     *  a number whatever hashes this next can still take apart. */
    @Override
    public int hashCode() {
        return ValueHash.ofOnePart(Realized.class, parts.hashCode());
    }

    @Override
    public String toString() {
        return parts.toString();
    }

    /**
     * Whether working this out left a position nobody could build.
     *
     * <p>What a reader asking about the reading is owed beside the reading. The values it comes
     * back with hold every value at such a position, which is true and is wider than the rules — so
     * an answer worked out of them alone is sound and is not exact, and what says so is here rather
     * than at whoever is asking.
     *
     * <p>The existential question and not the shortfalls: what a rule or the answer is answerable
     * for is a report's ({@link #aboutARule}, {@link #aboutTheAnswer}), and what a verdict turns on
     * is only whether there is such a position. What that comes to for a verdict is
     * {@link LeftUnbuilt}'s, so nothing here names one of the answers.
     */
    public LeftUnbuilt leftUnbuilt() {
        return unbuilt().isEmpty() ? LeftUnbuilt.NOTHING : LeftUnbuilt.A_POSITION;
    }

    /** Every position whose answer was not built, whichever of the two it is owed to. */
    public Set<A> unbuilt() {
        Set<A> out = new LinkedHashSet<>();
        aboutARule().forEach(each -> out.add(each.at()));
        aboutTheAnswer().forEach(each -> out.add(each.at()));
        return Collections.unmodifiableSet(out);
    }
}
