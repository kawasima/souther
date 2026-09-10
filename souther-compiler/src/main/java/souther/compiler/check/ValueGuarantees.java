package souther.compiler.check;

import souther.compiler.numeric.NumericDomain.Bounds;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a value of one type guarantees of itself, by the path under it each bound is about.
 *
 * <p>True of every value of the type and of nothing narrower. Nothing about where the value came
 * from, which case it turned out to be, or what a caller has taken in about it reaches here — so a
 * reader standing anywhere at all may assume these, and a reader that walks values it cannot name
 * one at a time has nothing else it may assume.
 *
 * <p>Three readers ask it, and one question is what makes them agree. The discharge check asks it of
 * a container's elements and of a parameter a walk's step is handed; the reading that measures a
 * model asks it of the values a run walks. Each of them is standing somewhere different and none of
 * them may condition the answer on where.
 *
 * <p><b>All of a value's owners or none.</b> Which clauses hold of a value of a type is
 * {@link ValueReading}'s answer, and a type with more than one owner is one whose cases each own
 * part of what a value of it is. A bound read off some of them is a bound half the values may be
 * outside, so a reading that falls over anywhere answers nothing — which leaves the value unbounded
 * rather than bounded by half of what the declarations say.
 *
 * <p><b>And a bound written on a case is not one of these.</b> Two cases can bound one shared field
 * differently, and what holds of a value that is one of them is neither. Nothing here joins them:
 * what is read is what each owner's own reading records at a path, and a name every case spreads
 * reads alike under each of them because the clauses of a shared part are what that part writes. So
 * a bound the cases disagree on is a bound this does not have, and a bound the value's own type
 * carries is one it does.
 */
public final class ValueGuarantees {

    private ValueGuarantees() {}

    /**
     * What a value of {@code type} guarantees, by the path under it each bound is about.
     *
     * <p>{@link InvariantChecker#seedFields} is what decides it and this reads that answer: a
     * record's own invariant bounds its fields, and a reading of the declarations is what has that.
     */
    public static Map<RuleKey, Bounds> of(Type type, RuleReadingSource source,
                                          ReadingPolicy policy) {
        // Whose clauses hold of a value of this type is the one reading's answer. Asked here from
        // the declaration instead, an element that is a sum is an element nothing is known about,
        // while the same value read as a field of a record carries the shared part's bounds.
        List<ValueReading.Owner> owners = ValueReading.of(type, source.symbols()).owners();
        Map<RuleKey, Bounds> guaranteed = new LinkedHashMap<>();
        for (ValueReading.Owner owner : owners) {
            InvariantChecker.Seeded seeded =
                    seededOf(owner.named(), source, policy);
            if (seeded == null) {
                // All of them or none, which is what leaves a value unbounded rather than bounded
                // by half of what the declarations say.
                return Map.of();
            }
            seeded.atoms().forEach((path, atom) -> {
                Bounds bounds = seeded.numbers().boundsOf(atom);
                if (bounds != null && !bounds.saysNothing()) {
                    // A shared part reached through two of its own ancestors is read twice and reads
                    // alike both times, since a declaration's clauses are what it writes and what it
                    // spreads.
                    guaranteed.putIfAbsent(path, bounds);
                }
            });
        }
        return guaranteed;
    }

    /** The reading of {@code named}, or null where it fell over. A reading that fell over is one
     * this says nothing from, which leaves a value unbounded rather than bounded by half of what
     * a declaration says. */
    private static InvariantChecker.Seeded seededOf(TypeSymbol.AtModule named,
                                                    RuleReadingSource source, ReadingPolicy policy) {
        InvariantChecker.Seeded seeded =
                InvariantChecker.seedFieldsUnshared(named, source, policy);
        return seeded.everyClauseRead() && !seeded.constraints().isBottom() ? seeded : null;
    }
}
