package souther.compiler.check;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The positions whose ends this reading did not derive, and the choice an author is sent to for
 * each of them.
 *
 * <p>One question and one answer: of the ends a part of a clause states, which are the ones this
 * reading did not work out. Whether anybody can be in a branch, which positions a choice leaves as
 * wide as they are, and whether the model raises a question anywhere are three other questions with
 * three other owners, and nothing here decides any of them — what is done with their answers is to
 * strike positions off this one.
 *
 * <p><b>A choice never puts a position in here.</b> {@link #either} intersects, so what comes out of
 * a choice is contained in what its alternatives brought to it: a choice can only show that a
 * position it was handed is one the alternatives leave where they found it. So a position here was
 * put here by a leaf the reading gave up on, and the choices above it are the road, not the source.
 *
 * <p>The road matters all the same, which is why the choices are carried beside the positions. A
 * position reaching a report with none of them is one a conjunction left open, and that is the
 * account the rule's own reading already gives; one reaching it with a choice is what an author has
 * no other sentence about, and the choice is what they can act on.
 *
 * <p><b>The nearest choice that kept it, and not every choice above.</b> A position surviving a
 * choice that already names one is one the choice below it accounted for, and naming both would
 * make how an author bracketed a chain of choices decide how many things they are sent to —
 * {@code (a || b) || c} and {@code a || (b || c)} are one rule.
 *
 * @param byPosition every position whose end this part left open, each under the choices an author
 *                   is sent to for it. Empty where the part's ends were all derived; a position
 *                   under an empty set is one no choice is answerable for
 */
record EndsLeftOpen(Map<FactSubject, Set<RuleShortfall.Site.AtAChoice>> byPosition) {

    EndsLeftOpen {
        Map<FactSubject, Set<RuleShortfall.Site.AtAChoice>> held = new LinkedHashMap<>();
        byPosition.forEach((position, choices) -> held.put(position,
                Collections.unmodifiableSet(new LinkedHashSet<>(choices))));
        byPosition = Collections.unmodifiableMap(held);
    }

    /** A part whose ends this reading derived, and one no reading has a word for at all. */
    static EndsLeftOpen nothing() {
        return new EndsLeftOpen(Map.of());
    }

    /**
     * One leaf, as the reading of ends answered for it.
     *
     * <p>Handed the positions rather than asked for them: whether this reading followed the rule to
     * the end is the reading's own answer ({@code OrderedReading.gaveUpAt}) and which positions the
     * leaf is about is the clause's, and both are in hand where a leaf is read. A leaf it followed
     * brings nothing here, whatever it found — a rule read from end to end that places no end is
     * one this reading answered.
     */
    static EndsLeftOpen at(Set<FactSubject> positions) {
        Map<FactSubject, Set<RuleShortfall.Site.AtAChoice>> out = new LinkedHashMap<>();
        positions.forEach(each -> out.put(each, Set.of()));
        return new EndsLeftOpen(out);
    }

    /**
     * Both parts holding at once.
     *
     * <p>The union, and nothing is struck off. A part beside one this reading could not work out
     * still says what it says, and the end it places may be the loose one of the two — so a
     * conjunction leaves the end at a position either of its parts left open still open.
     */
    EndsLeftOpen both(EndsLeftOpen other) {
        if (other.byPosition.isEmpty()) {
            return this;
        }
        if (byPosition.isEmpty()) {
            return other;
        }
        Map<FactSubject, Set<RuleShortfall.Site.AtAChoice>> out = new LinkedHashMap<>(byPosition);
        other.byPosition.forEach((position, choices) -> out.merge(position, choices,
                EndsLeftOpen::joined));
        return new EndsLeftOpen(out);
    }

    /**
     * Either part holding, under what the choice between them was settled to leave open.
     *
     * <p>{@code opening} is the whole of what the choice does here and it arrives worked out
     * ({@link Settlement.WidthDependency}). A position left out of it is one this reading showed
     * the alternatives leave where the choice leaves it, so an end nothing derived in one branch
     * takes nothing back at it: the branch beside it is reached by every value the unread one is,
     * and the choice stops where it would without either. A position kept is one nobody settled,
     * and the end there is as open as the branch that was not read.
     *
     * <p>So this is a filter and never a source. What comes out is contained in what the two
     * branches brought, which is what keeps a choice from inventing a rule nobody could read.
     *
     * <p>The choice names itself at the positions arriving with nothing to send an author to. Where
     * a choice below already named one, an author lifting that one is lifting this — and told
     * twice, they would be sent to a bracket rather than to a clause.
     */
    EndsLeftOpen either(RuleShortfall.Site.AtAChoice choice, Opening<FactSubject,
            ReadingLanguage.Order> opening, EndsLeftOpen other) {
        Map<FactSubject, Set<RuleShortfall.Site.AtAChoice>> out = new LinkedHashMap<>();
        both(other).byPosition.forEach((position, choices) -> {
            if (opening.positions().contains(position)) {
                out.put(position, choices.isEmpty() ? Set.of(choice) : choices);
            }
        });
        return new EndsLeftOpen(out);
    }

    private static Set<RuleShortfall.Site.AtAChoice> joined(
            Set<RuleShortfall.Site.AtAChoice> these, Set<RuleShortfall.Site.AtAChoice> those) {
        if (those.isEmpty()) {
            return these;
        }
        if (these.isEmpty()) {
            return those;
        }
        Set<RuleShortfall.Site.AtAChoice> out = new LinkedHashSet<>(these);
        out.addAll(those);
        return out;
    }
}
