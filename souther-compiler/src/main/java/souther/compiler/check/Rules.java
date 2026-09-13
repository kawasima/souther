package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;
import souther.compiler.values.AdmissibleSet;
import souther.compiler.values.ValueSet;

/**
 * The rules written about a value, or the fact that a value of this kind has none to write.
 *
 * <p>Two answers that {@link FieldDomains#NONE} reads as one. Its contract is a caller that chose
 * not to read a declaration or had none to read, which is why it says of every name that the
 * reading never reached the rules about it — the safe answer for a caller that stopped, and a false
 * one for a caller that looked at a declaration which can hold no rule. A reader deciding whether
 * the model divides a value needs those apart: told the second as the first, it would answer "a
 * rule about this may have gone unread" of every plain {@code String} in every model, and told the
 * first as the second it would say the model states nothing where nobody looked.
 *
 * <p>Which of the two it is comes from the declaration standing there, over
 * {@link Hir.Def} with no {@code default}. That is where the fact lives: an invariant clause is
 * carried by {@link Hir.Data} and by nothing else, and the front end refuses one written on a
 * declaration lowered into another kind (spec
 * §an-invariant-is-declared-where-a-construction-owes-it), so a kind that holds no clauses is a kind
 * nothing was written on. A declaration kind added later stops this compiling rather than arriving
 * as a value quietly said to have no rules.
 *
 * <p>Nothing here reads a {@code Shape}. Which shapes can carry a rule is the declaration world's
 * answer, and a reading of it made out here would be a copy of that kept in step by hand.
 */
public sealed interface Rules {

    /** A declaration that can carry clauses, read. */
    record Read(FieldDomains domains) implements Rules {

        public Read {
            if (domains == null) {
                throw new IllegalArgumentException("a reading with no domains is not a reading");
            }
        }
    }

    /**
     * No rule about this value's own names exists to be read.
     *
     * <p>A statement about the model and not about this reading. What stands here is a
     * primitive, a collection, a name for a choice between declarations, or nothing declared at all
     * — none of which carries a clause.
     */
    record NoneWritten() implements Rules {}

    /**
     * Whether any rule at all was written about this value.
     *
     * <p>Not which arm this is. A sum and a unit data carry no clause and are {@link NoneWritten};
     * every other {@code data} is a declaration clauses may be written on and is {@link Read}
     * whether or not any were. So the arm says what kind of thing was looked at, and this says what
     * was found — and a caller wanting the second and reading the first counts a record nobody wrote
     * a rule about as a value that spoke.
     */
    default boolean anythingWasWritten() {
        return switch (this) {
            case Read read -> read.domains().anythingWasWritten();
            case NoneWritten _ -> false;
        };
    }

    /**
     * Whether the gathering reached every rule written at {@code path}.
     *
     * <p>The same two answers this type exists to keep apart, asked of reach. A value of a kind that
     * carries no clause has no rule for a walk to reach, which is not a walk that stopped — read as
     * the second, every plain {@code String} in every model would have something out of sight.
     */
    default boolean everyRuleReachedAt(RuleKey path) {
        return switch (this) {
            case Read read -> read.domains().everyRuleReachedAt(path);
            case NoneWritten _ -> true;
        };
    }

    /** What the rules leave what stands at {@code path}, which is {@link ValueSet#ANY} read in
     *  full where no rule was written at all. */
    default AdmissibleSet admits(RuleKey path) {
        return switch (this) {
            case Read read -> read.domains().admits(path);
            case NoneWritten _ -> AdmissibleSet.complete(ValueSet.ANY);
        };
    }

    /**
     * How much of what the rules say the bounds these leave are able to state.
     *
     * <p>The same two answers this type exists to keep apart, asked of a projection. A value of a
     * kind that carries no clause has no rule for a projection to have been short of, so its bounds
     * state everything there was — read off {@link FieldDomains#NONE} instead, which answers as a
     * caller that stopped, every plain {@code String} in every model would be a place no edge
     * could be promised at.
     */
    default ProjectionEvidence projection() {
        return switch (this) {
            case Read read -> read.domains().projection();
            case NoneWritten _ -> NOTHING_WAS_WRITTEN;
        };
    }

    /**
     * What a value of a kind carrying no clause comes to, worked out once.
     *
     * <p>Asked of the algebra rather than made here. What certifies a range is the algebra's to say,
     * and a value carrying no clause is a system of no rules — which meets every hypothesis and has
     * no rule to leave unproven, so every certificate holds of it. Made once because it is the same
     * answer every time and this is asked of every name and every line drawn on one.
     */
    ProjectionEvidence NOTHING_WAS_WRITTEN = whatNoRuleAtAllComesTo();

    private static ProjectionEvidence whatNoRuleAtAllComesTo() {
        return switch (souther.compiler.numeric.NumericDomain.<FactSubject>top()
                .projectionCertification()) {
            case souther.compiler.numeric.ProjectionCertification.Certified it ->
                    new ProjectionEvidence.CertifiedExact(it.by());
            // The arms below are the algebra changing what it takes a certificate to be, and not
            // anything about a value: a system of no rules leaves nothing unproven and relates no
            // two subjects of different kinds.
            case souther.compiler.numeric.ProjectionCertification other ->
                    throw new IllegalStateException(
                            "no rule is written anywhere and the algebra answered " + other);
        };
    }

    /**
     * The numbers, ends and narrowings these rules leave.
     *
     * <p>{@link FieldDomains#NONE} where nothing was written, which is what it says of those: no
     * bounds anywhere, nothing placed, and no contradiction. Only the values question reads the two
     * arms apart, because only there does "nothing was written" differ from "nothing was read" in
     * what a reader may conclude.
     */
    default FieldDomains bounds() {
        return this instanceof Read read ? read.domains() : FieldDomains.NONE;
    }

    /**
     * What is written about a value of {@code type}.
     *
     * @param named the declaration the value is read under, or null where the type names none
     */
    static Rules of(TypeSymbol named, RuleReadingSource source, ReadingPolicy policy) {
        return of(named, source, policy, DeclarationReadings.NONE);
    }

    /** The same, asking {@code machines} for what somebody has already made of the string rules
     *  before building any of it. */
    static Rules of(TypeSymbol named, RuleReadingSource source, ReadingPolicy policy,
                    DeclarationReadings machines) {
        if (named == null) {
            return new NoneWritten();
        }
        if (named instanceof TypeSymbol.AtModule at) {
            return switch (source.kinds().of(at.key())) {
                // Which form the declaration is, and nothing it says: whether a rule can be written
                // on one at all is decided by the form, and what is written on it is read below
                // that. Exhaustive over the forms, so one added to the language arrives here as a
                // compile error rather than as a value the model states no rule about.
                case DeclarationKind.PRODUCT ->
                        new Read(FieldDomains.of(at, source, policy, machines));
                // A sum names which cases a value can be and carries no clause of its own; a unit
                // data has one value and may write no rule about it (spec §unit-data). Both are
                // declarations this looked at and found nothing written on, which is not the same
                // as not having looked.
                case DeclarationKind.SUM, DeclarationKind.UNIT -> new NoneWritten();
                // A name denoting no declaration. Nothing is written about it here because there is
                // nothing here to write it on — and what that costs the value is said by the
                // reading of its shape, which reports the type as one this could not interpret.
                case null -> new NoneWritten();
            };
        }
        // A primitive or one of the language's own cases, which no module wrote. The world is asked
        // here and nowhere above it: what comes back is a declaration of the library's, and one
        // that wrote fields would be this compiler answering a module's declaration for a name that
        // is not one.
        return switch (source.symbols().declaredNode(named)) {
            case Hir.Data data -> Declared.notAModules(named, data);
            // A sum names which cases a value can be and carries no clause of its own; a unit data
            // has one value and may write no rule about it (spec §unit-data). Both are declarations
            // this looked at and found nothing written on, which is not the same as not having
            // looked.
            case Hir.SumData _, Hir.UnitData _ -> new NoneWritten();
            // A name denoting no declaration. Nothing is written about it here because there is
            // nothing here to write it on — and what that costs the value is said by the reading
            // of its shape, which reports the type as one this could not interpret.
            //
            // No `default` beside it. A declaration kind added later has to be classified here, and
            // the classification is what says whether a rule can be written on one: read as
            // `NoneWritten` by a default, an unknown kind would arrive as a value the model states
            // no rule about, which is the sentence this whole reading exists to stop being cheap.
            case null -> new NoneWritten();
        };
    }

    /** The same, for a value whose type may name no declaration at all. */
    static Rules of(Type type, RuleReadingSource source, ReadingPolicy policy) {
        return of(type, source, policy, DeclarationReadings.NONE);
    }

    /** The same, asking {@code machines} first. */
    static Rules of(Type type, RuleReadingSource source, ReadingPolicy policy,
                    DeclarationReadings machines) {
        return of(type instanceof Type.Ref ref ? ref.name() : null, source, policy, machines);
    }
}
