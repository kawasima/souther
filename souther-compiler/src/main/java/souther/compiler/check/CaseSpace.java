package souther.compiler.check;

import souther.compiler.types.CaseSelector;
import souther.compiler.types.Refinement;
import souther.compiler.types.ResolvedCase;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The cases a subject can be selected as, worked out once from the subject's type.
 *
 * <p>A subject is one of three things and they are told apart here and nowhere else: an optional,
 * whose two carriers are the present one and the absent one; an anonymous union, whose cases are its
 * members; a named sum, whose cases are the ones declared with it. Anything else has no cases, and
 * that is an answer rather than a failure — what a reader does about a subject it cannot open is the
 * reader's ({@code E1202} for a {@code match}).
 *
 * <p>What a case comes to is settled here and never asked of the subject again. Two halves of it,
 * and they go to different readers. What to test and what the value is once the test answers is the
 * {@link CaseSelector}, which says that much wherever it is written and is what a backend emits.
 * What selecting the case <em>covers</em> is a fact about the declarations this compile read, and
 * it is the half a later stage cannot work out for itself — so {@link ResolvedCase} is the pair,
 * and it is the pair that crosses into {@code Core}. That is the rule this type exists to keep: the
 * backend used to re-derive optional-ness, arity and or-pattern-ness while emitting, and the
 * readings of an input used to re-derive which distinction an arm picked from its name, which one
 * name over several leaves cannot say (#1252).
 */
sealed interface CaseSpace {

    /** The type the cases are of. */
    Type subject();

    /** How a report names what is being selected from: {@code union `A | B`}, {@code data `X`},
     *  {@code Option}. Written here beside the cases so a diagnostic never spells it a second way. */
    String described();

    /**
     * The cases; empty for a subject that has none.
     *
     * <p>Ordered so that two readings of one subject list them alike — a report saying what a match
     * left out reads this, and an order that came out differently each time would move a message
     * nothing about the program had changed. A sum's cases come as declared and an optional's
     * present carrier before its absent one; a union states no order of its own, so the one
     * {@link AtomSpace#statedBy} puts on it is used rather than the order its set happens to
     * iterate in — which is not an order anything about the program decided.
     *
     * <p>It is not the order arms are tried in. Which arm of a {@code match} takes a value is
     * decided by the order the arms are written, which is the match's and not the subject's.
     */
    List<ResolvedCase> selectors();

    /** A subject with no cases: nothing selects from it and nothing opens it. */
    record Plain(Type subject) implements CaseSpace {

        @Override
        public String described() {
            return Type.show(subject);
        }

        @Override
        public List<ResolvedCase> selectors() {
            return List.of();
        }
    }

    /**
     * An optional, whose two carriers are the present one and the absent one.
     *
     * <p>Its own arm because what may be <em>written</em> over one differs, and a reader choosing
     * those rules reads this rather than asking the subject's type a second time. Every form that
     * admits a different surface is an arm here, so gaining one is a compile error at each reader
     * that decides by form rather than a silent fall into the general reading.
     */
    record Optional(Type subject, List<ResolvedCase> selectors) implements CaseSpace {

        public Optional {
            selectors = List.copyOf(selectors);
        }

        @Override
        public String described() {
            return "Option";
        }
    }

    /** A subject whose cases are named data: a union's members, or a sum's declared cases. */
    record Cases(Type subject, String described, List<ResolvedCase> selectors) implements CaseSpace {

        public Cases {
            selectors = List.copyOf(selectors);
        }

        /**
         * Whatever covers part of what the subject covers, which is what makes a nested sum
         * transparent.
         *
         * <p>Inclusion of what the two cover and not descent of what the subject declared. A value
         * of the subject is one of its atoms, so a type whose own atoms are among them is a type
         * some values of the subject are and others are not — which is what an arm is. Answering by
         * walking down from the declaration instead would admit the same names in every case anyone
         * has written and a different set where two declarations reach one group of leaves without
         * either naming the other; there is no value that tells those apart, so nothing here should.
         * It is the reading {@link TypeOps#assignable} already takes, where a data-like type widens
         * to the leaves it can be.
         *
         * <p>Covering nothing is not covering part: a name that denotes no type answers for no value
         * of the subject, and admitting it would be an arm no run can take.
         */
        @Override
        public ResolvedCase covering(TypeSymbol name, PublishedDeclarations published) {
            if (TypeSymbol.SOME.equals(name) || TypeSymbol.NONE.equals(name)) {
                return null;   // an optional's carriers, which no subject with cases has
            }
            ResolvedCase candidate =
                    resolve(CaseSelector.direct(name), published);
            return !candidate.atoms().isEmpty()
                    && new LinkedHashSet<>(AtomSpace.subjectAtoms(subject, published))
                            .containsAll(candidate.atoms())
                    ? candidate
                    : null;
        }
    }

    /**
     * What {@code subject} can be selected as.
     *
     * <p>The one place the forms are told apart. An optional is read before a name is, because
     * {@code Option} is not a declaration a module holds; a union is read before a name because it
     * has no name to look up.
     */
    static CaseSpace of(Type subject, DeclarationKinds kinds, PublishedDeclarations published) {
        if (subject instanceof Type.OptionOf option) {
            // An optional's carriers cover themselves. What `Some` holds is the element, and the
            // element's own atoms are not what an arm over an optional answers for: `Some` is the
            // case, whatever it wraps.
            return new Optional(subject, List.of(
                    resolve(CaseSelector.optionPresent(option.element()), published),
                    resolve(CaseSelector.optionAbsent(), published)));
        }
        if (subject instanceof Type.Union union) {
            // Described from the members this lists and not by showing the union again. What a
            // report names the subject as and what it says the subject is made of are one answer;
            // shown from the type, the members would come out in whatever order its set iterates,
            // and the two halves of one message would order the same union two ways.
            List<TypeSymbol> members = AtomSpace.statedBy(union);
            return new Cases(subject, "union `" + shown(members) + "`", direct(members, published));
        }
        // Whether the subject is a sum is asked of the form, which is settled where the module was
        // indexed; what its cases are is asked of the declaration, and only of the ones that are
        // sums. Asking the second of every subject would ask what a declaration says of one whose
        // own meaning is being worked out.
        if (subject instanceof Type.Ref ref
                && ref.name() instanceof TypeSymbol.AtModule at
                && kinds.isSum(at.key())
                && published.of(at.key()) instanceof DeclarationMeaning.Sum sum) {
            return new Cases(subject, "data `" + sum.declares().name() + "`",
                    direct(AtomSpace.declaredCases(sum), published));
        }
        return new Plain(subject);
    }

    /** Whether {@code name} selects part of what this subject can be. */
    default boolean holds(TypeSymbol name, PublishedDeclarations published) {
        return selector(name, published) != null;
    }

    /**
     * The case {@code name} selects, or null where it selects none of what this subject can be.
     *
     * <p>What the subject states, first and by name, so a case it declared keeps the identity and
     * the place it was resolved at. A name it did not state is then asked of {@link #covering}:
     * a sum whose case is a sum is transparent as a value (spec §sum-data), so a name standing for
     * part of what the subject can be selects that part whether or not the subject listed it.
     */
    default ResolvedCase selector(TypeSymbol name, PublishedDeclarations published) {
        for (ResolvedCase selected : selectors()) {
            if (selected.name().equals(name)) {
                return selected;
            }
        }
        return covering(name, published);
    }

    /**
     * A name the subject did not state that stands for part of what it can be, or null.
     *
     * <p>None, for a subject whose cases are all it has. An optional has two carriers and no others:
     * {@code Some} and {@code None} are not declarations, and a name that happened to cover one of
     * them would be selecting a carrier it is not.
     */
    default ResolvedCase covering(TypeSymbol name, PublishedDeclarations published) {
        return null;
    }

    /** The names of these cases, in the order the subject states them. */
    default List<TypeSymbol> names() {
        List<TypeSymbol> out = new ArrayList<>();
        for (ResolvedCase selected : selectors()) {
            out.add(selected.name());
        }
        return out;
    }

    /**
     * A union written out as its members, in the order it states them.
     *
     * <p>Not {@link Type#show}, which renders a union's members as it finds them. What that does is
     * a wider question than this one — the standard library's {@code Int | DivisionByZero} would
     * read {@code DivisionByZero | Int}, and the order a reader wants there is the one the author
     * wrote, which a union does not keep — so what is settled here is only that one report orders
     * one union one way.
     */
    private static String shown(List<TypeSymbol> members) {
        List<String> names = new ArrayList<>();
        for (TypeSymbol member : members) {
            names.add(Type.show(Type.ref(member)));
        }
        return String.join(" | ", names);
    }

    /**
     * Cases whose carrier is the value itself, de-duplicated the way the subject states them: a
     * member written twice is one case, and the first spelling is the one the order keeps.
     *
     * <p>What each covers is {@link ResolvedCase#atoms}'s to work out. This says which cases there
     * are and in what order; what one of them reaches is not restated here.
     */
    private static List<ResolvedCase> direct(Iterable<TypeSymbol> members, PublishedDeclarations published) {
        Set<TypeSymbol> seen = new LinkedHashSet<>();
        for (TypeSymbol member : members) {
            seen.add(member);
        }
        List<ResolvedCase> out = new ArrayList<>();
        for (TypeSymbol member : seen) {
            out.add(resolve(CaseSelector.direct(member), published));
        }
        return out;
    }

    /**
     * {@code selector} resolved against the declarations {@code published} holds.
     *
     * <p>The one place what a case covers is worked out. A {@link ResolvedCase} is a selector and
     * the atoms selecting it reaches, and the second half is a fact about the declarations this
     * compile read — so it is answered here, where they are, and the value carries no way of asking
     * again.
     *
     * <p>Also where a caller holding a selector alone gets the pair. An elaborated arm carries the
     * resolution already, so a reader of {@code Core} asks the arm and not this; what comes here is
     * a selector built somewhere with no arm around it, and asking is that caller crossing into
     * this pass rather than reading the declarations a second time.
     */
    static ResolvedCase resolve(CaseSelector selector, PublishedDeclarations published) {
        return ResolvedCase.of(selector, covers(selector, published));
    }

    /**
     * What selecting {@code selector} covers.
     *
     * <p>Read from the refinement and not from the name, because the two carriers that are not a
     * case of a declaration are told apart by nothing else. An optional's carrier covers itself:
     * what {@code Some} covers is {@code Some}, and taking the element's atoms would make an
     * optional over a sum cover that sum's leaves, so the two arms of a {@code match} over it would
     * be held against cases no optional has.
     *
     * <p>A case whose carrier is the value covers what it holds — one atom for a leaf, and the
     * leaves under it for a case that is itself a sum. A name that denotes no type holds nothing to
     * descend ({@code Raw}, which a stage may be unioned with and which no declaration takes apart)
     * and covers no atom: the answer {@link AtomSpace} gives a type that names no case, said here
     * because the type to ask it about is the one that is missing.
     */
    private static List<TypeSymbol> covers(CaseSelector selector, PublishedDeclarations published) {
        return switch (selector.refinement()) {
            case Refinement.OptionPresent _ -> List.of(TypeSymbol.SOME);
            case Refinement.OptionAbsent _ -> List.of(TypeSymbol.NONE);
            case Refinement.Direct direct -> direct.bound() == null
                    ? List.of()
                    : AtomSpace.subjectAtoms(direct.bound(), published);
        };
    }
}
