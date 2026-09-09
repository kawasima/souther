package souther.compiler.check;

import souther.compiler.core.Core;
import souther.compiler.types.BindingId;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a type guarantees of a value, asked one value at a time.
 *
 * <p>The one reading of a declaration. A value of type {@code T} was built through {@code T}'s
 * checked constructor, so what {@code T} states holds of it; that argument is the same for a
 * behavior's parameter, for what a {@code match} arm binds, for what an attempt built, and for what
 * a combinator hands a closure. Each of those has a reader, and none of them may come to a different
 * answer about what the declaration says — so the answer is here and the readers ask.
 *
 * <p><b>A question about one value, not a walk over what it holds.</b> Everything a walk would carry
 * — how deep to go, which names to leave out, what a stop costs, what is being measured — is missing
 * on purpose. A caller that wants what is readable here asks {@link At#readable} and decides for
 * itself whether to go there. Held the other way, the depth a walk could afford would be part of
 * what a declaration means, and two callers that could afford different depths would be two readings
 * of the model.
 *
 * <p><b>What the model writes where a value stands is {@link ValueReading}'s answer.</b> Nothing
 * here resolves a name to a declaration, so this cannot come to a conclusion of its own about what
 * kind of value it is reading — which declarations state something of every value here, and what is
 * readable on one, are settled before this starts.
 *
 * <p>Nothing here mentions {@link Known}, a path, or a {@link InvariantChecker.Gathering}. Reading a
 * clause as an assumption cannot consult what was already known ({@link Predicates.Discharge}), so
 * there is nothing for a caller's knowledge to change about the answer.
 */
final class TypeGuarantees {

    private final Symbols symbols;

    private final Clauses clauses;

    private final Predicates predicates;

    TypeGuarantees(Symbols symbols, Clauses clauses, Predicates predicates) {
        this.symbols = symbols;
        this.clauses = clauses;
        this.predicates = predicates;
    }

    /**
     * What the type of the value at {@code root} guarantees of it, what is readable off it, and
     * what a reading opened elsewhere answers for.
     *
     * <p>The clauses are the declaration's, rebased onto this very value: a rule written about a
     * field is a rule about that field of this value, and where it is established and where it is
     * owed differ only in direction.
     */
    At at(Core root, Denotations denotations) {
        return at(root, denotations, PartsLeftOut.NONE);
    }

    /**
     * The same, reading only the conjuncts {@code withoutParts} leaves in.
     *
     * <p>Here rather than filtered out of what comes back. What a guarantee states is read out of
     * the clause, and a conjunct taken away afterwards would leave a guarantee whose parts and
     * whose answer were read from two different clauses — the whole one and the one the caller
     * asked about.
     */
    At at(Core root, Denotations denotations, PartsLeftOut withoutParts) {
        ValueReading written = ValueReading.of(root.type(), symbols);
        Map<String, Core> values = new LinkedHashMap<>();
        List<At.Readable> readable = new ArrayList<>();
        for (Map.Entry<String, Type> field : written.named().entrySet()) {
            Core value = new Core.FieldAccess(root, field.getKey(), field.getValue(), root.pos());
            values.put(field.getKey(), value);
            readable.add(new At.Readable(field.getKey(), value, field.getValue()));
        }
        List<TypeGuarantee> here = new ArrayList<>();
        List<RuleRef.Invariant> lost = new ArrayList<>();
        // One rule stated once. A declaration's clauses are what it writes and what it spreads, so
        // a shared part reached through two of its own ancestors states the ancestor's rule twice —
        // and a reader counting what it was handed would count one rule of the model as two.
        Set<Object> already = new HashSet<>();
        for (ValueReading.Owner owner : written.owners()) {
            Map<BindingId, Core> given = new HashMap<>();
            clauses.bindingsOf(owner.named()).forEach((name, binding) -> {
                Core value = values.get(name);
                if (value != null) {
                    given.put(binding, value);
                }
            });
            Clauses.StatedClauses stated =
                    clauses.statedAt(owner.named(), given);
            for (Clauses.Stated one : stated.clauses()) {
                if (already.add(one.clause())) {
                    here.add(read(one, denotations, withoutParts));
                }
            }
            for (RuleRef.Invariant each : stated.lost()) {
                if (already.add(each.clause())) {
                    lost.add(each);
                }
            }
        }
        return new At(written.entering(), here, readable, handedOn(written, already),
                lost.isEmpty() ? new At.Coverage.Complete() : new At.Coverage.Incomplete(lost));
    }

    /**
     * What another reading answers for here.
     *
     * <p>Asked of what this reading did not take in, which is why {@code stated} is here: a case of
     * a sum carries the clauses its cases share as well as its own, and those were read at this very
     * value. Handed on all the same, one rule of the model would be owed twice — once here and
     * once by whoever opens the case — and nothing below could settle the first.
     */
    private At.HandedOn handedOn(ValueReading written, Set<Object> stated) {
        List<Type> under = new ArrayList<>();
        for (Type each : written.handedOn()) {
            if (beyond(each, written, stated)) {
                under.add(each);
            }
        }
        return under.isEmpty() ? new At.HandedOn.Nothing() : new At.HandedOn.ToAnotherReading(under);
    }

    /** Whether {@code type} holds a rule that is not one this value already stated, nor one under a
     * name this reading already reaches. */
    private boolean beyond(Type type, ValueReading here, Set<Object> stated) {
        ValueReading there = ValueReading.of(type, symbols);
        for (ValueReading.Owner owner : there.owners()) {
            for (TypeOps.Declared each : clauses.declared(owner.named())) {
                if (!stated.contains(Clause.Ref.of(each))) {
                    return true;
                }
            }
        }
        for (Map.Entry<String, Type> field : there.named().entrySet()) {
            if (!here.named().containsKey(field.getKey()) && anyRuleUnder(field.getValue())) {
                return true;
            }
        }
        for (Type deeper : there.handedOn()) {
            if (anyRuleUnder(deeper)) {
                return true;
            }
        }
        return false;
    }

    /**
     * One clause, read as something that holds of this value.
     *
     * <p>What each part of it came to is read here and kept, rather than handed to a caller as it
     * happens. A reader told mid-reading is a reader the reading has to know about; a reader given
     * the parts afterwards is one this does not.
     */
    private TypeGuarantee read(Clauses.Stated one, Denotations denotations,
                               PartsLeftOut withoutParts) {
        // Where this clause becomes a rule of the model something can be attributed to. Settled here
        // so that no reader of the reading has to decide which of the rules of the model it holds.
        List<TypeGuarantee.Part> parts = new ArrayList<>();
        List<Quantified> quantified = new ArrayList<>();
        // A part at a time, and the ones this world holds. Which parts a clause has was settled
        // where it was split, so leaving one out is leaving a part out of the list and never a node
        // out of a walk — and the same list answers for what the clause owes and for what it
        // quantifies, since a part left out of the one and left in the other is a clause taken half
        // away. Which of them are here is the world's answer and not a rule this consults.
        Predicates.Owed owed = null;
        for (Clauses.StatedPart part : withoutParts.viewOf(one.parts()).present()) {
            Predicates.Owed said = predicates.assumed(part.expr(), denotations, false,
                    (of, came) -> parts.add(new TypeGuarantee.Part(of, came)));
            owed = owed == null ? said : owed.and(said);
            predicates.quantifiedBy(part.expr(), denotations, true, quantified);
        }
        // Nothing owed where every part was left out, which is a clause with all of it taken away
        // and not a clause that holds.
        return new TypeGuarantee(one.expr(), one.parts(),
                owed == null ? Predicates.Owed.unread() : owed, quantified, parts);
    }

    /**
     * Whether any rule is written anywhere under {@code type}.
     *
     * <p>A question about the model and not about any walk, which is what makes it the right one to
     * ask where a reader stops: the reader's own reach is what is being decided, so reading that
     * would answer that whatever was not read had nothing in it.
     *
     * <p><b>The closure over {@link ValueReading}, and not a second reading of what the model writes
     * where a value stands.</b> One step is what a declaration says; this is the transitive question
     * built from that step, the way {@link AtomSpace} is the one closure over a sum's cases. Written
     * as a switch of its own it can answer that a sum has rules under it while {@link #at} answers
     * that the sum has nothing at all, and the two are readings of one model.
     *
     * <p><b>Whether anything is owed here, and never what became of it.</b> What became of what was
     * handed on is a fact about the walk and is settled there
     * ({@link souther.compiler.inputs.RuleHandoffs}). Answered as that, a value is reported short of
     * a rule the walk has already gone one name down to, and no row can discharge it.
     */
    boolean anyRuleUnder(Type type) {
        return anyRuleUnder(type, new HashSet<>());
    }

    /** {@code seen} stops a type that holds its own kind. A name met on the way here was read where
     * it was met, so what it holds is accounted for and reaching it again adds nothing. */
    private boolean anyRuleUnder(Type type, Set<TypeSymbol> seen) {
        ValueReading written = ValueReading.of(type, symbols);
        if (written.entering() != null && !seen.add(written.entering())) {
            return false;
        }
        for (ValueReading.Owner owner : written.owners()) {
            if (!clauses.declared(owner.named()).isEmpty()) {
                return true;
            }
        }
        for (Type under : written.named().values()) {
            if (anyRuleUnder(under, seen)) {
                return true;
            }
        }
        for (Type under : written.handedOn()) {
            if (anyRuleUnder(under, seen)) {
                return true;
            }
        }
        return false;
    }

    /**
     * What stands at one value: what is guaranteed here, what is readable off it, what another
     * reading answers for, and whether every rule written here could be read.
     *
     * <p><b>A product and not a classification.</b> The three are independent — a sum whose cases
     * share a spread states rules here <em>and</em> leaves its cases to a reading opened elsewhere —
     * so one answer standing for all three leaves a reader that recognises the first unable to
     * account for the second. An arm for the pair is the same shape one case later.
     *
     * @param entered   the declaration entered here, or null where none stands here
     * @param here      what the rules written here guarantee of this value
     * @param readable  the names a reader may write here, and what stands at each
     * @param handedOn  what a reading opened elsewhere answers for
     * @param coverage  whether every rule written here was read
     */
    record At(TypeSymbol entered, List<TypeGuarantee> here, List<Readable> readable,
              HandedOn handedOn, Coverage coverage) {

        public At {
            here = List.copyOf(here);
            readable = List.copyOf(readable);
        }

        /** What a reading opened elsewhere answers for at this value. */
        sealed interface HandedOn {

            /** Nothing under this value is left for another reading — either nothing stands under
             * it, or no rule is written under what does. */
            record Nothing() implements HandedOn {}

            /**
             * Rules stand under this value that no reading here takes in, and these are the types
             * they are written under. A case of a sum is opened by a match; what a container holds
             * is reached by whoever walks into it.
             */
            record ToAnotherReading(List<Type> under) implements HandedOn {

                public ToAnotherReading {
                    under = List.copyOf(under);
                }
            }
        }

        /** Whether every rule written at this value was read as one. */
        sealed interface Coverage {

            /** Every clause written here states something this reading could use. */
            record Complete() implements Coverage {}

            /**
             * These clauses state nothing this reading can use, so what they were about is not among
             * what was handed over.
             *
             * <p>Named and not counted. A clause lost here is not a clause handed on: nobody else
             * takes it, and it stands at run time. Told apart from {@link HandedOn} for that reason —
             * read as one axis, a reader would have to work out from the count which of the two it
             * was.
             */
            record Incomplete(List<RuleRef.Invariant> lost) implements Coverage {

                public Incomplete {
                    if (lost.isEmpty()) {
                        throw new IllegalArgumentException("nothing lost is Complete");
                    }
                    lost = List.copyOf(lost);
                }
            }
        }

        /**
         * One name a reader may write here, and what stands at it.
         *
         * <p>The name as the declaration writes it, and never whether reading it goes anywhere: a
         * newtype's {@code value} is this same value under a name and a record's field is one step
         * in, which is {@link Location#isStep}'s answer and is asked by whoever writes a path.
         *
         * @param name  the name it is read at
         * @param value what stands there
         * @param type  what stands there is of this type
         */
        record Readable(String name, Core value, Type type) {}
    }
}
