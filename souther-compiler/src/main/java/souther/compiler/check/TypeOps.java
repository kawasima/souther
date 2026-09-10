package souther.compiler.check;

import souther.compiler.ast.Ast;
import souther.compiler.ast.Hir;
import souther.compiler.ast.WrittenName;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.SourcePos;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.msg.NameMessage;
import souther.compiler.diag.msg.TypeMessage;
import souther.compiler.diag.msg.ModuleMessage;
import souther.compiler.diag.msg.DataMessage;
import souther.compiler.types.BindingId;
import souther.compiler.types.CaseSelector;
import souther.compiler.types.LeafScalar;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.CaseShape;
import souther.compiler.types.MapKeyRepresentation;
import souther.compiler.types.Type;
import souther.compiler.types.Denotation;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The type-level questions the checker asks, independent of any expression being checked: resolving
 * a written type, deciding assignability, unifying an intrinsic's type variables, and reading what a
 * data or a newtype is made of.
 *
 * <p>Every operation here is a pure function of a type and the module's symbol table, so it is
 * static and can be called from the backend as well as from the checker.
 */
public final class TypeOps {

    private TypeOps() {}

    /** Resolves a written type. Kept for the readers that name a parameter's type as such. */
    public static Type resolveParamType(Hir.RetType t) {
        return successType(t);
    }

    /**
     * What a position may require of a type. {@link #EQUALITY} is what {@code ==} requires and what a
     * {@code Set} requires of its element and a {@code Map} of its key; {@link #EXTERNAL_FORM} is
     * what a data's field, a newtype's base and a behavior's input and output require.
     *
     * <p>Ordering is not one of these. Both of these are answered {@code true} or {@code false} and
     * nothing more is wanted, while a reader that admits an ordered value goes on to ask what orders
     * it — so the answer is a witness and lives in {@link Ordering}, and {@link #supportsOrdering}
     * is that witness existing. Kept as a row here, the capability had one answer and the four
     * places that emit a comparison each worked out the other for themselves (issue #856).
     */
    public enum Requires { EQUALITY, EXTERNAL_FORM }

    /**
     * Whether {@code t} answers {@code required}. One table, so both answers for a type constructor
     * are read in one place, and neither switch carries a {@code default}: a constructor added to
     * {@link Type}, or a third question added here, stops compiling until it is answered — which is
     * where "can the representation actually do this?" gets asked.
     *
     * <p>The two are separate questions, and the arms combine their children differently: a tuple
     * carries values through a computation and is never encoded, whatever it holds.
     *
     * <p>Both are answered by the shape of the type alone. Ordering was the row that consulted the
     * module, to learn what a {@code Ref} denotes, and it is no longer one of these — it is a
     * witness rather than a yes, and {@link Ordering} holds it. The {@code symbols} parameter is
     * what that row read and is threaded for a third question that needs it.
     *
     * <p>{@code Raw} answers yes to equality because its value's own {@code equals} answers, and that
     * is the only answer available: a Raw is an arbitrary Java object and the language promises
     * nothing about it. It is the one place a capability is claimed that the representation does not
     * guarantee.
     *
     * <p>A variable, {@code Nothing}, {@code Never} and {@code Erroneous} stand for a type rather
     * than being one. They answer the way an unconstrained type does, so a generic core signature and
     * a module that already reported an error are not refused a second time for what they hold.
     */
    public static boolean answers(Type t, Requires required, Symbols symbols) {
        return switch (t) {
            case Type.Prim _ -> switch (required) {
                case EQUALITY, EXTERNAL_FORM -> true;
            };
            case Type.Ref _ -> switch (required) {
                case EQUALITY, EXTERNAL_FORM -> true;
            };
            case Type.Union _ -> switch (required) {
                case EQUALITY, EXTERNAL_FORM -> true;
            };
            case Type.ListOf l -> switch (required) {
                case EQUALITY, EXTERNAL_FORM -> answers(l.element(), required, symbols);
            };
            case Type.SetOf s -> switch (required) {
                case EQUALITY, EXTERNAL_FORM -> answers(s.element(), required, symbols);
            };
            case Type.OptionOf o -> switch (required) {
                case EQUALITY, EXTERNAL_FORM -> answers(o.element(), required, symbols);
            };
            case Type.MapOf m -> switch (required) {
                case EQUALITY, EXTERNAL_FORM -> answers(m.key(), required, symbols)
                        && answers(m.value(), required, symbols);
            };
            case Type.TupleOf tu -> switch (required) {
                case EQUALITY -> {
                    for (Type e : tu.elements()) {
                        if (!answers(e, required, symbols)) {
                            yield false;
                        }
                    }
                    yield true;
                }
                // a tuple carries values through a computation and is never encoded, whatever it holds
                case EXTERNAL_FORM -> false;
            };
            case Type.FnOf _ -> false;
            case Type.Open _, Type.Nothing _, Type.Never _, Type.Erroneous _ -> switch (required) {
                case EQUALITY, EXTERNAL_FORM -> true;
            };
        };
    }

    /**
     * Whether values of this type can be compared for equality — what {@code ==} requires, and what
     * a {@code Set} requires of its element and a {@code Map} of its key (ADR-0009, ADR-0039).
     */
    public static boolean supportsEquality(Type t) {
        // No declaration is read to answer it: every declared type is compared by what it holds, so
        // the question is settled by the shape of the type alone. Asked while a module is being
        // resolved as well as after, which is what says it cannot need one.
        return answers(t, Requires.EQUALITY, null);
    }

    /** Whether values of this type have an ordering — what {@code sort} and a {@code sortBy} key
     * require of what they order, and what {@code <} requires of two operands of one type. A
     * single-value newtype is ordered by the value it wraps (ADR-0047), and an enumeration by the
     * order its cases are declared in (ADR-0069), so a newtype over an enumeration is ordered by
     * that enumeration. This is {@link Ordering#of} having an answer, and asking it any other way is
     * a second definition of the same word. */
    public static boolean supportsOrdering(Type t, Symbols symbols) {
        return Ordering.of(t, symbols) != null;
    }

    /**
     * Whether a value of this type has an external representation — what a boundary requires of it
     * (ADR-0004). A boundary is where a codec is derived or a value crosses one: a data's field, a
     * newtype's base, a behavior's input and its output.
     */
    public static boolean hasExternalForm(Type t, Symbols symbols) {
        return answers(t, Requires.EXTERNAL_FORM, symbols);
    }

    /**
     * The part of {@code t} that has no external form, or null when the whole of it has one. What it
     * is, is part of the answer: a function and a tuple are both refused at a boundary and for the
     * same reason, but what an author should write instead differs, so a reader that reports one must
     * not report it as the other.
     *
     * <p>The decision is {@link #answers}'s; this only walks to where it was made. A type that
     * answers no and is not a collection is itself the part that cannot cross.
     */
    public static Type withoutExternalForm(Type t, Symbols symbols) {
        if (answers(t, Requires.EXTERNAL_FORM, symbols)) {
            return null;
        }
        return switch (t) {
            case Type.ListOf l -> withoutExternalForm(l.element(), symbols);
            case Type.SetOf s -> withoutExternalForm(s.element(), symbols);
            case Type.OptionOf o -> withoutExternalForm(o.element(), symbols);
            case Type.MapOf m -> {
                Type inKey = withoutExternalForm(m.key(), symbols);
                yield inKey != null ? inKey : withoutExternalForm(m.value(), symbols);
            }
            default -> t;
        };
    }

    /** The type a field's written type stands for. A field whose type is not representable at the
     * boundary is refused where the data is checked; the type is read the same way either way. */
    public static Type fieldType(Hir.Field f) {
        return f.type() instanceof Hir.TypeRef ref ? ref.denotes() : resolveTerm(f.type());
    }

    /** The type one written term stands for. */
    public static Type resolveTerm(Hir.TypeTerm t) {
        return switch (t) {
            case Hir.TypeRef ref -> ref.denotes();
            case Hir.FnType ft -> {
                List<Type> params = new ArrayList<>();
                for (Hir.RetType p : ft.params()) {
                    params.add(successType(p));
                }
                yield Type.fn(params, successType(ft.result()));
            }
        };
    }

    /**
     * The output type of a behavior return: a single case, or a union of two or more cases.
     *
     * <p>An output with a member resting on a name that denotes nothing has no case set, and is the
     * type that absorbs — the same answer a single such case already gives, so one mistake has one
     * recovery wherever it is written. A check that would hold such an output against what is
     * produced reads it here first, so that what is wrong with the union itself is still said, and
     * then asks {@link #restsOnAnUnresolvedName} whether there is a case set to compare.
     */
    public static Type successType(Hir.RetType ret) {
        List<Type> members = new ArrayList<>();
        for (Hir.TypeTerm t : ret.cases()) {
            members.add(resolveTerm(t));
        }
        if (members.size() == 1) {
            return members.get(0);
        }
        // The two ways a member can fail to be one are different mistakes, and the author owns only
        // one of them. A member that cannot be written in an arm is theirs and is reported where it
        // stands, as the first such member always was. A member whose name denotes nothing was
        // reported where that name was written, and what this reading finds there is that same
        // mistake: the output has no case set at all, so it takes the type that absorbs and this
        // says nothing further. Finding one does not end the reading, because a member the author
        // does own may be written after it.
        Set<TypeSymbol> names = new LinkedHashSet<>();
        boolean unknown = false;
        for (Type m : members) {
            switch (memberName(m)) {
                case MemberName.Named named -> names.add(named.name());
                case MemberName.NoType _ -> unknown = true;
                case MemberName.NotAMember _ -> throw CompileException.of(Diagnostic
                                .at(ret.pos()).say(new TypeMessage.NotAUnionMember(Type.show(m))).build());
            }
        }
        return unknown ? Type.ERRONEOUS : Type.union(names);
    }

    /**
     * What a union member goes by, which is three answers and not two.
     *
     * <p>A member the compiler could not work out a type for and a member whose type cannot be one
     * are not the same finding, and a reader that gets one answer for both reports the second
     * sentence about the first: that a name denoting nothing is not the kind of thing an arm can
     * name. Kept apart here so that a reader has to say which of the two it is acting on, and a
     * reader added later cannot decide it by not noticing.
     */
    sealed interface MemberName {

        /** The case name this member is written and dispatched under. */
        record Named(TypeSymbol name) implements MemberName {}

        /** A type no arm can name, so no union can carry it. */
        record NotAMember() implements MemberName {}

        /** A member resting on a name that denotes nothing, reported where that name was written. */
        record NoType() implements MemberName {}
    }

    private static final MemberName NOT_A_MEMBER = new MemberName.NotAMember();
    private static final MemberName NO_TYPE = new MemberName.NoType();

    /**
     * The case name a union member goes by: a data type's own name, or the name a primitive is
     * written under in a match arm ({@code Int} in {@code Int | NoAnswer}).
     *
     * <p>A member has to be nominal and has to tell itself apart from the other members at run time,
     * because that is what a {@code match} arm and a Java {@code switch} both dispatch on. A
     * collection fails the second: its type argument is erased, so {@code List<Order>} and
     * {@code List<Item>} are one runtime type and no arm could choose between them. An
     * {@code Option} and a function fail it the same way. That they also have no arm form to write
     * is the surface showing the same fact.
     */
    static MemberName memberName(Type m) {
        // The type that absorbs stands where the compiler could not work one out. It is not a shape
        // this question has an answer about, and reading it as one is how the name that denotes
        // nothing came to be reported a second time as a member an arm could not name.
        if (m instanceof Type.Erroneous) {
            return NO_TYPE;
        }
        if (m instanceof Type.Ref r) {
            return new MemberName.Named(r.name());
        }
        // Exhaustive over the primitives rather than a chain of comparisons, and reading the one
        // spelling table rather than repeating it. A chain answers "not a member" for a primitive
        // added later without asking anyone, and that answer is the truth about Raw and about
        // nothing else.
        if (m instanceof Type.Prim p) {
            return switch (p) {
                case INT, STRING, BOOL, DECIMAL, DATE, TIME, DATETIME, INSTANT ->
                        new MemberName.Named(TypeSymbol.primitive(p.shown()));
                case RAW -> NOT_A_MEMBER;
            };
        }
        return NOT_A_MEMBER;
    }

    /** Builds a Ref (one name) or Union (two or more) from a set of case names. */
    static Type caseSetType(Set<TypeSymbol> names) {
        if (names.size() == 1) {
            return Type.ref(names.iterator().next());
        }
        return Type.union(names);
    }

    public static boolean isDataLike(Type t) {
        return t instanceof Type.Ref || t instanceof Type.Union;
    }

    /**
     * The case names {@code t} carries, which is none for a type that carries no name.
     *
     * <p>Both ways of carrying none are none here, on purpose: this asks which names are on a type
     * and nothing about why a type has none. Where the difference matters is a declaration held
     * against what is produced — an output resting on a name that denotes nothing has no case set
     * rather than the empty one — and that is asked at those checks by
     * {@link #restsOnAnUnresolvedName}, before either side is turned into names.
     */
    public static Set<TypeSymbol> namesOf(Type t) {
        if (t instanceof Type.Union u) {
            return u.members();
        }
        return switch (memberName(t)) {
            case MemberName.Named named -> Set.of(named.name());
            case MemberName.NotAMember _, MemberName.NoType _ -> Set.of();
        };
    }

    /** Case names of a stage output, treating a {@code Raw} encoder output as the case {@code "Raw"}
     * so it can be unioned with propagated error cases (spec §sequential-composition, §case-propagation). */
    static Set<TypeSymbol> caseNamesOf(Type t) {
        if (t == Type.RAW) {
            return Set.of(TypeSymbol.primitive("Raw"));
        }
        return namesOf(t);
    }

    /** True when a value of {@code sub} is acceptable where {@code sup} is expected. */
    public static boolean subtypeOf(Type sub, Type sup) {
        if (sub.equals(sup)) {
            return true;
        }
        Set<TypeSymbol> names = namesOf(sub);
        return !names.isEmpty() && sup instanceof Type.Union u && u.members().containsAll(names);
    }

    /** The sum type that {@code t}'s case belongs to, or null when {@code t} is not a case of a named
     * sum. A {@code fold} whose seed is a case ({@code PricedCart}) and whose step grows and matches the
     * accumulator at the sum ({@code PricedCart | NotFound}) is typed at that sum, not the seed case. */
    public static Type enclosingSum(Type t, Symbols symbols) {
        // A case of a primitive-headed union belongs to no named sum: nothing declares it, so no
        // module holds a sum it is a case of.
        if (!(t instanceof Type.Ref(TypeSymbol.AtModule named))
                || symbols.declaredNode(named) instanceof Hir.SumData) {
            return null;
        }
        // A sum and its cases are declared together, so only that module can hold the sum this case
        // belongs to. A case may belong to more than one; pick by name so the choice is deterministic
        // across runs rather than dependent on the symbol map's iteration order.
        Hir.SumData chosen = null;
        for (String declared : symbols.declaredNamesIn(named.module())) {
            if (symbols.declaredNode(new TypeKey(named.module(), declared)) instanceof Hir.SumData sum
                    && caseNames(sum).contains(named)
                    && (chosen == null || sum.name().compareTo(chosen.name()) < 0)) {
                chosen = sum;
            }
        }
        // The identity is the one the declaration carries. Put together from this case's module and
        // the sum's spelling instead, it would be an identity for whatever that address names.
        return chosen == null ? null : Type.ref(chosen.declares());
    }

    public static boolean isSumType(Type t, Symbols symbols) {
        return t instanceof Type.Union
                || (t instanceof Type.Ref r && symbols.declaredNode(r.name()) instanceof Hir.SumData);
    }

    /**
     * A sum's cases: what each name it lists denotes. A name that denotes nothing lists no case —
     * it is reported where it is written, and a reader counting the cases counts what is there.
     *
     * <p>One layer, which is what a descent over the cases would be built out of: hold this and the
     * declarations, and a transitive closure is a loop away. There is one such closure and it is
     * {@link AtomSpace}. Package-private for that reason and not by accident of who happens to call
     * it — every reader of a case list is in this package, so nothing outside it can write a second
     * closure at all, and that much is javac's to say rather than a test's.
     */
    static List<TypeSymbol> caseNames(Hir.SumData sum) {
        List<TypeSymbol> names = new ArrayList<>();
        for (Hir.Name c : sum.cases()) {
            if (c instanceof Hir.Name.Denoting named) {
                names.add(named.type());
            }
        }
        return names;
    }

    /** The type a case holds when a value turns out to be it — {@link CaseSelector#heldBy}, which is
     * where a selector reads it, under the name the callers outside a match know it by. */
    public static Type caseBindType(TypeSymbol caseName) {
        return CaseSelector.heldBy(caseName);
    }

    /**
     * What a sum's encoding adds to this case, or a behavior's answer to this member — read from the
     * declaration the name denotes (spec §encoder-derivation). A braced data lays its fields beside the
     * discriminator, a data with no contents is the discriminator alone, and a newtype or a primitive
     * puts its standalone representation under {@code "value"}.
     *
     * <p>The one place code generation classifies a case's representation. The encoder of a named
     * sum, the encoder of a behavior's anonymous answer, the decoder that hands a case what it wrote
     * and the jOOQ row all ask it, and each of them working it out a different way is how the
     * envelope came to have two owners.
     *
     * <p>A fixture asks something else. Its question is which form a value takes at the position it
     * is written in, which the shape alone does not answer — the same newtype is bare at its own type
     * and wrapped inside a sum — so {@code NeutralForm} reads the position and this stays about the
     * declaration.
     */
    public static CaseShape caseShape(TypeSymbol name, Symbols symbols) {
        if (name.isPrimitive()) {
            return CaseShape.WRAPPED;   // a bare scalar carries no key the discriminator could go on
        }
        return switch (symbols.declaredNode(name)) {
            case Hir.Data d when d.newtype() -> CaseShape.WRAPPED;
            case Hir.Data _ -> CaseShape.PRODUCT;
            case Hir.UnitData _ -> CaseShape.UNIT;
            case null, default -> throw new IllegalStateException(
                    "`" + name + "` stands as a case but denotes no data");
        };
    }

    /**
     * Whether anything in {@code module} has a type the compiler could not work out.
     *
     * <p>Asked after the module is checked, never before: the error type exists so that the check can
     * carry on past one mistake, and a hole in one declaration must not silence every other definition
     * in the file.
     *
     * <p>It is asked as well as {@link Names.Sound}, not instead of it, because a module can hold a
     * hole without having reported anything: an import of a module that is here and unusable leaves
     * the names it was to bring denoting nothing, and what is wrong was reported on that module's
     * source. No pass added later can forget this gate — a pass that cannot work a type out puts an
     * error type in, and this finds it wherever it is.
     */
    public static boolean holdsAnErroneousType(Hir.Module module) {
        for (Hir.Def def : module.defs()) {
            if (def instanceof Hir.Data d) {
                for (Hir.Field f : d.fields()) {
                    if (erroneous(f.type())) {
                        return true;
                    }
                }
                for (Hir.Name include : d.includes()) {
                    if (erroneous(include)) {
                        return true;
                    }
                }
            }
            if (def instanceof Hir.SumData sum) {
                for (Hir.Name c : sum.cases()) {
                    if (erroneous(c)) {
                        return true;
                    }
                }
            }
        }
        for (Hir.BehaviorDef b : module.behaviors()) {
            if (b instanceof Hir.SpecBehavior spec) {
                for (Hir.Param param : spec.params()) {
                    if (erroneous(param.type())) {
                        return true;
                    }
                }
                if (erroneous(spec.ret())) {
                    return true;
                }
                for (Hir.Name constructs : spec.constructs()) {
                    if (erroneous(constructs)) {
                        return true;
                    }
                }
            } else if (b instanceof Hir.PipeBehavior pipe && erroneous(pipe.declaredOut())) {
                return true;
            }
        }
        for (Hir.FnDef fn : module.fns()) {
            for (Hir.FnParam param : fn.params()) {
                if (erroneous(param.type())) {
                    return true;
                }
            }
            if (erroneous(fn.declaredReturn())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether {@code name} is one resolution read and found no declaration for.
     *
     * <p>Named as the three states it is one of, so a name nothing has read yet is refused rather
     * than answered no. A tree reaching here holding one is a pass that did not answer its own
     * nodes, and calling it "not erroneous" would let that go by while every walk below read the
     * module as though the author had written it that way.
     */
    private static boolean erroneous(Hir.Name name) {
        return switch (name) {
            case Hir.Name.Unanswered _ -> true;
            case Hir.Name.Denoting _ -> false;
        };
    }

    private static boolean erroneous(Hir.RetType ret) {
        return ret != null && ret.cases().stream().anyMatch(TypeOps::erroneous);
    }

    /**
     * Whether a written output rests on a name that denotes nothing.
     *
     * <p>Asked by the two checks that hold a declared output against what is produced. The case set
     * of such an output is not the empty set — there is no answer to take a set from — and comparing
     * against it says the declaration names nothing of what the body or the composition builds,
     * which is the unresolved name arriving a second time under a sentence about the declaration.
     * Those checks abandon instead, and the name is reported where it was written.
     *
     * <p>Read off what was written rather than off the type it stands for, because that is where
     * the reference sits and there is nothing to lose on the way.
     */
    static boolean restsOnAnUnresolvedName(Hir.RetType ret) {
        return erroneous(ret);
    }

    private static boolean erroneous(Hir.TypeTerm term) {
        return switch (term) {
            case null -> false;
            case Hir.FnType ft ->
                    ft.params().stream().anyMatch(TypeOps::erroneous) || erroneous(ft.result());
            case Hir.TypeRef ref -> {
                if (ref.denotes() instanceof Type.Erroneous) {
                    yield true;
                }
                if (ref.tupleElems() != null
                        && ref.tupleElems().stream().anyMatch(TypeOps::erroneous)) {
                    yield true;
                }
                yield erroneous(ref.arg());
            }
        };
    }

    /** Whether a {@code from} value can be assigned where {@code to} is expected. Lists are
     * covariant, and a data-like type widens to the set of leaf cases it can be — so a list of
     * a sum's cases is assignable to a list of the sum (spec §sum-data, §unmarked-output). */
    public static boolean assignable(Type from, Type to, Symbols symbols) {
        if (from.equals(to)) {
            return true;
        }
        if (from instanceof Type.Erroneous || to instanceof Type.Erroneous) {
            // Something here has no type because the compiler already said why. Answering "yes"
            // reports nothing further about it: the alternative is one mistake arriving again at
            // every position the value it produced flowed into.
            return true;
        }
        if (from instanceof Type.Nothing) {
            return true;   // the empty list's bottom element assigns into any element type (ADR-0028)
        }
        if (from instanceof Type.Never) {
            // nothing arrives from there, so no value of the wrong type can (spec §match)
            return true;
        }
        // immutable collections are element-covariant: A <: S makes a List/Map/Option of A
        // assignable to one of S. Sound because they cannot be mutated (spec §collections), so no write can
        // smuggle a sibling case in — the same reason Scala's immutable List and Kotlin's read-only
        // List are covariant, and Java's mutable arrays are not.
        if (from instanceof Type.ListOf a && to instanceof Type.ListOf b) {
            return assignable(a.element(), b.element(), symbols);
        }
        if (from instanceof Type.MapOf a && to instanceof Type.MapOf b) {
            return assignable(a.key(), b.key(), symbols) && assignable(a.value(), b.value(), symbols);
        }
        if (from instanceof Type.SetOf a && to instanceof Type.SetOf b) {
            return assignable(a.element(), b.element(), symbols);
        }
        if (from instanceof Type.OptionOf a && to instanceof Type.OptionOf b) {
            return assignable(a.element(), b.element(), symbols);
        }
        if (from instanceof Type.TupleOf a && to instanceof Type.TupleOf b
                && a.elements().size() == b.elements().size()) {
            for (int i = 0; i < a.elements().size(); i++) {
                if (!assignable(a.elements().get(i), b.elements().get(i), symbols)) {
                    return false;
                }
            }
            return true;
        }
        List<TypeSymbol> fa = AtomSpace.subjectAtoms(from, symbols);
        List<TypeSymbol> ta = AtomSpace.subjectAtoms(to, symbols);
        return !fa.isEmpty() && !ta.isEmpty() && ta.containsAll(fa);
    }

    /** The type two joined positions ({@code if} branches, {@code match} arms) agree on, or
     * {@code null} when they agree on none and the caller reports the disagreement.
     *
     * <p>It descends the covariant constructors {@link #assignable} descends, so the widening a leaf
     * gets applies under one too: two data-like types widen to the union of their cases, and a list
     * of one case joins a list of another as a list of both — the same direction {@code ++} takes
     * on its elements (spec §unmarked-output, §if). An empty collection's bottom takes on the other side's
     * type, at the top or at any depth, so a bare {@code Set.empty} accumulator joins the
     * {@code Set<Int>} the other arm grows and a {@code (Set.empty, [])} joins position by
     * position. */
    public static Type join(Type a, Type b) {
        if (a.equals(b)) {
            return a;
        }
        if (a instanceof Type.Erroneous || b instanceof Type.Erroneous) {
            return Type.ERRONEOUS;   // one side has no type; neither has the joined position
        }
        if (BottomInfer.isBottom(a)) {
            return b;
        }
        if (BottomInfer.isBottom(b)) {
            return a;
        }
        // An arm that answers `unreachable` contributes no case to the join: what the position holds
        // is what the arms that answer a value hold (spec §match).
        if (a instanceof Type.Never) {
            return b;
        }
        if (b instanceof Type.Never) {
            return a;
        }
        if (a instanceof Type.ListOf la && b instanceof Type.ListOf lb) {
            Type e = join(la.element(), lb.element());
            return e == null ? null : Type.list(e);
        }
        if (a instanceof Type.SetOf sa && b instanceof Type.SetOf sb) {
            Type e = join(sa.element(), sb.element());
            return e == null ? null : Type.set(e);
        }
        if (a instanceof Type.OptionOf oa && b instanceof Type.OptionOf ob) {
            Type e = join(oa.element(), ob.element());
            return e == null ? null : Type.option(e);
        }
        if (a instanceof Type.MapOf ma && b instanceof Type.MapOf mb) {
            Type k = join(ma.key(), mb.key());
            Type v = join(ma.value(), mb.value());
            return k == null || v == null ? null : Type.map(k, v);
        }
        if (a instanceof Type.TupleOf ta && b instanceof Type.TupleOf tb
                && ta.elements().size() == tb.elements().size()) {
            List<Type> elements = new ArrayList<>();
            for (int i = 0; i < ta.elements().size(); i++) {
                Type e = join(ta.elements().get(i), tb.elements().get(i));
                if (e == null) {
                    return null;
                }
                elements.add(e);
            }
            return Type.tuple(elements);
        }
        if (isDataLike(a) && isDataLike(b)) {
            // In the order the cases were met, because a report quotes this type back to the author
            // and a set that hands its members over in the order their hashes fall would quote it
            // in an order nothing about the program decides. Two unions holding alike are one value
            // whatever order either walks in, so the order is the rendering's to read and nothing
            // else's.
            Set<TypeSymbol> names = new LinkedHashSet<>(namesOf(a));
            names.addAll(namesOf(b));
            return caseSetType(names);
        }
        return null;
    }

    /**
     * The type two branches take when they do not join on their own but the position expects a union
     * both are members of. A primitive joins nothing by itself — {@code Int} against {@code Decimal}
     * is a mistake wherever no union was asked for, and that error is worth keeping — so a branch
     * answering the value and a branch answering the reason there is none are settled by the output
     * that was written. Null when {@code expected} does not admit both.
     */
    static Type joinAt(Type expected, Type a, Type b) {
        if (!(expected instanceof Type.Union) || !subtypeOf(a, expected) || !subtypeOf(b, expected)) {
            return null;
        }
        Set<TypeSymbol> names = new LinkedHashSet<>(namesOf(a));
        names.addAll(namesOf(b));
        return caseSetType(names);
    }

    /**
     * Matches an intrinsic's declared parameter type against an actual argument type, binding any
     * type variables it carries (spec §intrinsics). A variable binds on first sight and every later
     * occurrence must agree; a composite ({@code List<'a>}, {@code Map<String, 'a>}) recurses into
     * its element; a concrete parameter just requires the argument to be assignable. This is what
     * monomorphises a generic intrinsic — {@code values(m: Map<String, 'a>): List<'a>} learns
     * {@code 'a} from the map so {@link #substitute} can resolve the {@code List<'a>} result.
     *
     * <p>Answers with the two types that disagreed rather than a report about them. Which operand
     * supplied either type, where it is written and what a reader is told are the caller's, and the
     * caller is what still has the expression in hand.
     *
     * <p>A walk that does not fit settles nothing. It stops at the first position that disagrees,
     * so whatever it had settled before that was settled by a reading it went on to refuse, and a
     * caller reading the map afterwards cannot tell those from what was there before.
     */
    public static Fit unify(Type param, Type arg, Map<String, Type> bindings, Symbols symbols) {
        Map<String, Type> settled = new HashMap<>(bindings);
        Fit fit = unify(param, arg, settled, symbols, true);
        if (fit instanceof Fit.Disagrees) {
            return fit;
        }
        bindings.putAll(settled);
        return fit;
    }

    /**
     * What {@code arg} settles of the variables {@code param} carries, refusing nothing.
     *
     * <p>For a caller that requires each argument afterwards, against the parameter type this walk
     * settled and at the argument's own position. Refusing here as well would answer the same
     * mismatch twice, and this reading is the one with no argument in its hands.
     *
     * <p>Reading every position rather than stopping at the first that disagrees is the difference:
     * a position this cannot settle says nothing about the ones beside it, and refusing is not this
     * walk's question.
     */
    public static void bindVars(Type param, Type arg, Map<String, Type> bindings, Symbols symbols) {
        unify(param, arg, bindings, symbols, false);
    }

    private static Fit unify(Type param, Type arg, Map<String, Type> bindings,
                             Symbols symbols, boolean refusing) {
        switch (param) {
            case Type.Var v -> {
                Type bound = bindings.get(v.name());
                if (bound == null || bound instanceof Type.Nothing) {
                    // first sight, or widen an empty-collection bottom to a concrete element: an
                    // earlier `[]` / `Map.empty` argument bound NOTHING, and a later real element
                    // fixes it (ADR-0028). Order-independent, so insert(k, v, Map.empty) infers V.
                    bindings.put(v.name(), arg);
                } else if (arg instanceof Type.Nothing) {
                    // the empty bottom absorbs into the concrete binding already learned
                } else if (refusing && !assignable(arg, bound, symbols)
                        && !assignable(bound, arg, symbols)) {
                    return new Fit.Disagrees(bound, arg);
                }
            }
            case Type.ListOf p when arg instanceof Type.ListOf a -> {
                return unify(p.element(), a.element(), bindings, symbols, refusing);
            }
            case Type.MapOf p when arg instanceof Type.MapOf a -> {
                Fit key = unify(p.key(), a.key(), bindings, symbols, refusing);
                if (key instanceof Fit.Disagrees) {
                    return key;
                }
                return unify(p.value(), a.value(), bindings, symbols, refusing);
            }
            case Type.SetOf p when arg instanceof Type.SetOf a -> {
                return unify(p.element(), a.element(), bindings, symbols, refusing);
            }
            case Type.OptionOf p when arg instanceof Type.OptionOf a -> {
                return unify(p.element(), a.element(), bindings, symbols, refusing);
            }
            case Type.TupleOf p when arg instanceof Type.TupleOf a
                    && p.elements().size() == a.elements().size() -> {
                for (int i = 0; i < p.elements().size(); i++) {
                    Fit at = unify(p.elements().get(i), a.elements().get(i), bindings, symbols, refusing);
                    if (at instanceof Fit.Disagrees) {
                        return at;
                    }
                }
            }
            case Type.FnOf p when arg instanceof Type.FnOf a && p.params().size() == a.params().size() -> {
                for (int i = 0; i < p.params().size(); i++) {
                    Fit at = unify(p.params().get(i), a.params().get(i), bindings, symbols, refusing);
                    if (at instanceof Fit.Disagrees) {
                        return at;
                    }
                }
                return unify(p.result(), a.result(), bindings, symbols, refusing);
            }
            default -> {
                if (refusing && !assignable(arg, param, symbols)) {
                    return new Fit.Disagrees(param, arg);
                }
            }
        }
        return Fit.FITS;
    }

    /**
     * {@code t} with every type variable still free read as the bottom.
     *
     * <p>A declaration's arguments are what bind its variables, so one with no arguments leaves any
     * the context did not pin standing. That is the same situation an empty {@code []} is in, and it
     * takes the same answer: the bottom unifies with whatever a later position fixes (ADR-0028).
     *
     * <p>Only for a declaration written with no parameter list. Elsewhere a variable left free is a
     * signature that could not be worked out, and reading it as the bottom would hide that.
     */
    public static Type toBottom(Type t) {
        return switch (t) {
            case Type.Var _ -> Type.NOTHING;
            case Type.ListOf l -> Type.list(toBottom(l.element()));
            case Type.MapOf m -> Type.map(toBottom(m.key()), toBottom(m.value()));
            case Type.SetOf s -> Type.set(toBottom(s.element()));
            case Type.OptionOf o -> Type.option(toBottom(o.element()));
            default -> t;
        };
    }

    /** {@code t} with each variable a declaration wrote replaced by what {@code bindings} gives it,
     * by the name the declaration wrote — so two occurrences of one variable stay one. */
    public static Type substituteVars(Type t, Map<String, Type> bindings) {
        return substitute(t, bindings);
    }

    /** {@code t} with each variable of an application replaced by what {@code bindings} gives it. */
    public static Type substituteMetas(Type t, Map<Type.MetaVar, Type> bindings) {
        return switch (t) {
            case Type.MetaVar m -> bindings.getOrDefault(m, m);
            case Type.ListOf l -> Type.list(substituteMetas(l.element(), bindings));
            case Type.MapOf m -> Type.map(substituteMetas(m.key(), bindings),
                    substituteMetas(m.value(), bindings));
            case Type.SetOf s -> Type.set(substituteMetas(s.element(), bindings));
            case Type.OptionOf o -> Type.option(substituteMetas(o.element(), bindings));
            case Type.FnOf f -> {
                List<Type> params = new ArrayList<>();
                for (Type p : f.params()) {
                    params.add(substituteMetas(p, bindings));
                }
                yield Type.fn(params, substituteMetas(f.result(), bindings));
            }
            case Type.TupleOf tup -> {
                List<Type> es = new ArrayList<>();
                for (Type e : tup.elements()) {
                    es.add(substituteMetas(e, bindings));
                }
                yield Type.tuple(es);
            }
            default -> t;
        };
    }

    public static Type substitute(Type t, Map<String, Type> bindings) {
        return switch (t) {
            case Type.Var v -> bindings.getOrDefault(v.name(), v);
            case Type.ListOf l -> Type.list(substitute(l.element(), bindings));
            case Type.MapOf m -> Type.map(substitute(m.key(), bindings), substitute(m.value(), bindings));
            case Type.SetOf s -> Type.set(substitute(s.element(), bindings));
            case Type.OptionOf o -> Type.option(substitute(o.element(), bindings));
            case Type.FnOf f -> {
                List<Type> params = new ArrayList<>();
                for (Type p : f.params()) {
                    params.add(substitute(p, bindings));
                }
                yield Type.fn(params, substitute(f.result(), bindings));
            }
            case Type.TupleOf tup -> {
                List<Type> es = new ArrayList<>();
                for (Type e : tup.elements()) {
                    es.add(substitute(e, bindings));
                }
                yield Type.tuple(es);
            }
            default -> t;
        };
    }

    /**
     * Two effective members of {@code out} that go by one written name, or null when every member's
     * name is its own. Asked after a named sum is expanded to its leaves, since the leaves are what a
     * {@code match} arm names and what the {@code "type"} discriminator carries — a sum contributes
     * its cases, not itself.
     */
    static TypeSymbol.AtModule[] ambiguousMembers(Type out, Symbols symbols) {
        Map<String, TypeSymbol.AtModule> byName = new LinkedHashMap<>();
        // Only a module's declarations can collide by spelling here: a module may not declare a
        // name the language gives, so a union holding one holds it under a spelling nothing else
        // in it has.
        for (TypeSymbol atom : AtomSpace.subjectAtoms(out, symbols)) {
            if (!(atom instanceof TypeSymbol.AtModule member)) {
                continue;
            }
            TypeSymbol.AtModule seen = byName.put(member.name(), member);
            if (seen != null && !seen.equals(member)) {
                return new TypeSymbol.AtModule[] {seen, member};
            }
        }
        return null;
    }

    /**
     * The first leaf of {@code out} that declares a field named {@code key}, or null when none does.
     * A leaf lays its fields flatly beside the discriminator, so a field of that name and the tag
     * want one key: whichever is written second is the only one left. Asked of the leaves, since a
     * nested sum contributes its cases rather than itself.
     */
    static TypeSymbol memberCarryingField(Type out, String key, Symbols symbols) {
        for (TypeSymbol member : AtomSpace.subjectAtoms(out, symbols)) {
            if (declaresField(member, key, symbols)) {
                return member;
            }
        }
        return null;
    }

    /** Whether {@code name} declares a field called {@code key}, spread fields included. A newtype's
     * one field is named {@code value} and a unit has none, so only a record can. */
    static boolean declaresField(TypeSymbol name, String key, Symbols symbols) {
        return symbols.declaredNode(name) instanceof Hir.Data data && !data.newtype()
                && fieldTypes(data, symbols).containsKey(key);
    }

    /**
     * The cases a position of this type can be, when it can be more than one thing: the atoms of
     * a union or of a named sum, and nothing otherwise.
     *
     * <p>What a row's expected arm is held against, and what an adequacy report counts as declared. The
     * two have to agree — a report that read a wider set than the rows are checked against would name a
     * case no row is allowed to write — so the rule is here rather than stated twice.
     *
     * <p>An admission and not a second descent: what a type is made of is {@link AtomSpace}'s, and
     * what is left here is which types this reader will take an answer about. A primitive output
     * names one atom like any other type, and is not a case list.
     */
    public static Set<TypeSymbol> outputCases(Type t, Symbols symbols) {
        return t instanceof Type.Union || t instanceof Type.Ref
                ? new LinkedHashSet<>(AtomSpace.subjectAtoms(t, symbols))
                : Set.of();
    }

    /**
     * The binding each of {@code data}'s fields introduces inside its own invariant.
     *
     * <p>An invariant reads its declaration's fields, and reads them as the bindings they are. Which
     * binding each is is answered here and nowhere else, so the pass that resolves an invariant and
     * the pass that emits it reach the same one. A field name is unique within a declaration, which
     * is what lets the answer be keyed by it; the order the map iterates in says nothing, and no
     * reader may take one from it.
     *
     * <p>What is fixed is the numbering: a field is numbered among the fields of the declaration
     * that declares it, in the order that declaration writes them. An include brings a field in
     * without renumbering it, so the two passes agree however either of them reaches it.
     *
     * <p>{@code declared} is which declaration this is, and is the caller's to name: a declaration
     * carries the name it was written under and not the module that wrote it, so worked out here it
     * would be worked out against whoever is reading, and a reader of another module's declaration
     * would bind its fields under its own name — a different binding for the same field, and the
     * clauses carried in with the declaration resolve against nothing. A caller reading its own
     * declaration passes {@link Symbols#module}; a caller reading one it reached passes the name it
     * reached it by.
     *
     * <p>The body under that name is read here. Naming which declaration this is and holding the
     * declaration are two things, and only the first is the caller's: a caller made to fetch the
     * body holds one for a question that was never its own, and can read the record's structure out
     * of it. A name declaring no record binds no field.
     */
    public static Map<String, BindingId> fieldBindings(TypeSymbol.AtModule declared,
                                                       Symbols symbols) {
        Map<String, BindingId> bindings = new LinkedHashMap<>();
        if (symbols.declaredNode(declared.key()) instanceof Hir.Data data) {
            walkFields(data, declared, symbols, new LinkedHashSet<>(), bindings);
        }
        return bindings;
    }

    /**
     * The same, of a declaration as it was written — what {@code Resolve} asks while it is resolving
     * one.
     *
     * <p>A separate way in rather than the same one under a flag, because what an include is differs
     * between the two: here it is a spelling this pass is about to answer, and there it is a name
     * already answered. A reader after the pass cannot reach this one, so an unanswered include
     * cannot be repaired from characters by anything that met one.
     */
    static Map<String, BindingId> fieldBindingsAsWritten(TypeSymbol.AtModule declared,
                                                         Ast.Data data, SyntaxSymbols symbols) {
        Map<String, BindingId> bindings = new LinkedHashMap<>();
        walkWrittenFields(data, declared, symbols, new LinkedHashSet<>(), bindings);
        return bindings;
    }

    /**
     * Every field {@code data} has, each with the binding the declaration that declares it gives it.
     *
     * <p>A field brought in by an include keeps the binding of the declaration it was written in,
     * because the invariant that reads it was written there too and is carried in with it. So a
     * declaration binds its own fields and the fields underneath, and an invariant reads the same
     * binding wherever it is checked or emitted.
     *
     * <p>A walk of its own, not the one {@link #fieldTypes} makes, because it answers where that one
     * cannot: a field has a name whether or not its type has been worked out. It therefore reaches
     * the fields in an order of its own, which is why nothing reads one off the result. An include
     * that names nothing is skipped, and a name an include repeats keeps the declaration's own —
     * both are refused where the declaration is checked, and refusing them twice says nothing more.
     */
    private static void walkFields(Hir.Data data, TypeSymbol.AtModule declared, Symbols symbols,
                                   Set<TypeSymbol> seen, Map<String, BindingId> out) {
        BindingOwner owner = new BindingOwner.OfFields(declared);
        int ordinal = 0;
        for (Hir.Field field : data.fields()) {
            out.putIfAbsent(field.name(), new BindingId(owner, ordinal++));
        }
        for (Hir.Name include : data.includes()) {
            TypeSymbol source = switch (include) {
                case Hir.Name.Denoting denoting -> denoting.type();
                // Reported where it is written. A name nothing declares brings in no fields, and
                // saying so again here would be a second report about the one mistake.
                case Hir.Name.Unanswered _ -> null;
            };
            // An include names a data, which a module declares; what the language gives is
            // no declaration to walk, and the lookup below already answered nothing for one.
            if (source instanceof TypeSymbol.AtModule at && seen.add(at)
                    && symbols.declaredNode(at) instanceof Hir.Data included) {
                walkFields(included, at, symbols, seen, out);
            }
        }
    }

    /** The same walk over a declaration as it was written: an include is a spelling, and what it
     *  denotes is asked of the scope the declaration was written in. */
    private static void walkWrittenFields(Ast.Data data, TypeSymbol.AtModule declared, SyntaxSymbols symbols,
                                          Set<TypeSymbol> seen, Map<String, BindingId> out) {
        BindingOwner owner = new BindingOwner.OfFields(declared);
        int ordinal = 0;
        for (Ast.Field field : data.fields()) {
            out.putIfAbsent(field.name(), new BindingId(owner, ordinal++));
        }
        for (Ast.Name include : data.includes()) {
            TypeSymbol source = symbols.scope().resolve(include.name()).type();
            if (source instanceof TypeSymbol.AtModule at && seen.add(at)
                    && symbols.declarations().declaration(at) instanceof Ast.Data included) {
                walkWrittenFields(included, at, symbols, seen, out);
            }
        }
    }

    /** Effective field name → type (included data flattened first, then own fields). */
    public static Map<String, Type> fieldTypes(Hir.Data data, Symbols symbols) {
        Map<String, Type> types = new LinkedHashMap<>();
        // Which spread put each field here, so a collision names the group that supplied the earlier
        // one. Reporting it against the taking data names a declaration that, where both fields came
        // through spreads, holds no such field at all.
        Map<String, String> suppliedBy = new LinkedHashMap<>();
        for (Hir.Name inc : data.includes()) {
            if (!(inc instanceof Hir.Name.Denoting names)) {
                // Nothing declares it, which was reported where it is written. It brings in no
                // fields, and complaining here that it is not a product data would be a second
                // report about the one mistake.
                continue;
            }
            TypeSymbol included = names.type();
            if (!(symbols.declaredNode(included) instanceof Hir.Data id)) {
                throw CompileException.of(Diagnostic.at(inc.name().reportedAt())
                        .say(new DataMessage.SpreadIsNotAProductData(inc.written()))
                        .build());
            }
            for (Map.Entry<String, Type> e : fieldTypes(id, symbols).entrySet()) {
                if (types.put(e.getKey(), e.getValue()) != null) {
                    throw CompileException.of(Diagnostic.at(inc.name().reportedAt())
                            .say(new DataMessage.SpreadFieldCollision(
                                    e.getKey(), inc.written(), suppliedBy.get(e.getKey())))
                            .build());
                }
                suppliedBy.put(e.getKey(), "..." + inc.written());
            }
        }
        for (Hir.Field f : data.fields()) {
            if (types.put(f.name(), fieldType(f)) != null) {
                throw CompileException.of(Diagnostic.at(f.pos())
                        .say(new DataMessage.FieldIsDeclaredMoreThanOnceIn(f.name(), data.name()))
                        .build());
            }
        }
        return types;
    }

    /**
     * The resolved type of one field, or null when the data has no such field. Reading a field is not
     * a reason to resolve the data's other fields: a module that imports a data to read one `Int` out
     * of it would otherwise need every sibling field's type in scope, and the failure would carry the
     * declaring module's position (issue #110). The duplicate-field checks {@link #fieldTypes} makes
     * belong to the declaring module's own check, which has already run.
     */
    public static Type fieldType(Hir.Data data, String field, Symbols symbols) {
        for (Hir.Field f : data.fields()) {
            if (f.name().equals(field)) {
                return fieldType(f);
            }
        }
        for (Hir.Name inc : data.includes()) {
            Hir.Data included = spreadTarget(inc, symbols);
            if (included != null) {
                Type t = fieldType(included, field, symbols);
                if (t != null) {
                    return t;
                }
            }
        }
        return null;
    }

    /** The product data a spread names, or null when nothing here denotes it or what it denotes is
     * not a product. Only {@link #fieldTypes} turns those into a diagnostic, and every declared data
     * goes through it; the readers asked about one field or one invariant answer for what they see. */
    private static Hir.Data spreadTarget(Hir.Name inc, Symbols symbols) {
        return inc instanceof Hir.Name.Denoting named
                && symbols.declaredNode(named.type()) instanceof Hir.Data d
                ? d : null;
    }

    /** Whether a data has a field of that name, without resolving any type. */
    public static boolean hasField(Hir.Data data, String field, Symbols symbols) {
        for (Hir.Field f : data.fields()) {
            if (f.name().equals(field)) {
                return true;
            }
        }
        for (Hir.Name inc : data.includes()) {
            Hir.Data included = spreadTarget(inc, symbols);
            if (included != null && hasField(included, field, symbols)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The part a sum's cases hold in common: the declarations every one of them spreads,
     * transitively, and the fields those declare.
     *
     * <p>What holds of every case is a property of the sum, and a spread is nominal, so what makes
     * the part shared is that the author wrote `...Common` in each case — not that two cases happen
     * to agree on a field name, which is the structural reading the language declines.
     * {@link Shape.CommonProduct.None} when they share no spread, so a read of a field stays the
     * error it is.
     *
     * <p>Package-private, and the answer travels as {@link Shape.Sum#common}. Every reader of a
     * sum's shared part is a reader of what a position is, which is {@link Shape}'s to say; asked
     * here directly, a reader would have a second way to find out what kind of sum it is holding.
     */
    static Shape.CommonProduct commonSpreadOf(Hir.SumData sum, Symbols symbols) {
        return commonSpreadOf(AtomSpace.subjectAtoms(Type.ref(sum.declares()), symbols), symbols);
    }

    /** As {@link #commonSpreadOf(Hir.SumData, Symbols)}, for cases already flattened to leaves. */
    static Shape.CommonProduct commonSpreadOf(List<TypeSymbol> cases, Symbols symbols) {
        if (cases == null || cases.isEmpty()) {
            return new Shape.CommonProduct.None();
        }
        Set<TypeSymbol> common = null;
        for (TypeSymbol c : cases) {
            Set<TypeSymbol> spreads = symbols.declaredNode(c) instanceof Hir.Data d
                    ? spreadAncestors(d, symbols) : Set.of();
            if (common == null) {
                common = new LinkedHashSet<>(spreads);
            } else {
                common.retainAll(spreads);
            }
            if (common.isEmpty()) {
                return new Shape.CommonProduct.None();
            }
        }
        Map<String, Type> fields = new LinkedHashMap<>();
        List<TypeSymbol> origins = new ArrayList<>();
        for (TypeSymbol ancestor : common) {
            if (symbols.declaredNode(ancestor) instanceof Hir.Data d) {
                origins.add(ancestor);
                fields.putAll(fieldTypes(d, symbols));
            }
        }
        // A data declaring no field is a unit data and is written without a body (E1008), so this
        // is empty only where the cases share nothing at all.
        return fields.isEmpty() ? new Shape.CommonProduct.None()
                : new Shape.CommonProduct.Shared(origins, fields);
    }

    /**
     * The declaration {@code name} denotes where it writes fields, with the name it was reached by,
     * and null where it denotes none.
     *
     * <p>The one resolution. What kind of position a name makes is {@link Shape}'s answer and is
     * settled before anything asks this; what is left is finding the declaration that answer is
     * about, and a reader doing that for itself has a second way to decide what stands at a
     * position.
     */
    static ValueReading.Owner writingFields(TypeSymbol name, Symbols symbols) {
        return name instanceof TypeSymbol.AtModule at
                && symbols.declaredNode(at) instanceof Hir.Data
                ? new ValueReading.Owner(at) : null;
    }

    /** Every data reachable from {@code data} through spreads, transitively — the set two cases are
     * intersected on. The data itself is not one of them: a case is not its own shared part. */
    private static Set<TypeSymbol> spreadAncestors(Hir.Data data, Symbols symbols) {
        Set<TypeSymbol> out = new LinkedHashSet<>();
        collectSpreadAncestors(data, symbols, out);
        return out;
    }

    private static void collectSpreadAncestors(Hir.Data data, Symbols symbols, Set<TypeSymbol> out) {
        for (Hir.Name inc : data.includes()) {
            Hir.Data included = spreadTarget(inc, symbols);
            if (included != null && out.add(inc.answered().type())) {
                collectSpreadAncestors(included, symbols, out);
            }
        }
    }

    /**
     * What each rule that governs a declaration is called and where it was written, in the order a
     * failure is decided by: the rules of what it spreads first, then its own, each in the order it
     * is written.
     *
     * <p>The part of a clause that does not turn on the representation the declaration is read in.
     * Resolution settles which clauses a declaration has, what each is called and where it stands;
     * after it, the only thing that makes one is {@link Hir.InvariantClause#with}, which replaces the
     * expression and carries the name and the position across, and no representation below adds a
     * clause, drops one, or reorders them. So this is well defined in every representation, and a
     * reader may ask it of whichever world it holds.
     *
     * <p>What a clause states is not here, and that is what this answer is for: what a rule states is
     * owned by the representation it is read in, and nothing can reach it through this.
     */
    public static List<InvariantHeader> invariantHeadersGoverning(
            TypeSymbol.AtModule named, Symbols symbols) {
        List<InvariantHeader> headers = new ArrayList<>();
        for (Hir.InvariantClause clause : settledClauses(named, symbols)) {
            headers.add(new InvariantHeader(clause.name(), clause.pos()));
        }
        return headers;
    }

    /**
     * The rules that govern {@code named}, as they were settled: what the declaring module made of
     * each clause once the helpers it names were expanded into it.
     *
     * <p>Not public, and there is one reader. What a clause states is owned by the representation it
     * is read in — the settled form by the derived world, the expanded one by
     * {@link ExpandedClauseLookup} — so a way of asking for it that anyone could reach is a way of
     * asking for one representation and being answered in whichever the caller's world happened to
     * be at.
     *
     * <p>Every declaration is read from {@code symbols}: the one asked about and every one a spread
     * reaches. Handed a node beside the world, the declaration asked about would be at whichever
     * stage the caller was holding and the ones under it at whichever stage the world reads.
     */
    static List<Hir.InvariantClause> settledClausesGoverning(
            TypeSymbol.AtModule named, DerivedSymbols symbols) {
        return settledClauses(named, symbols);
    }

    /** The same for a reader that wants only what every representation agrees on, which is why this
     *  takes any world. Private, so the world a clause's body is read from stays said by the method
     *  a caller names. */
    private static List<Hir.InvariantClause> settledClauses(
            TypeSymbol.AtModule named, Symbols symbols) {
        if (!(symbols.declaredNode(named) instanceof Hir.Data data)) {
            return List.of();
        }
        List<Hir.InvariantClause> invs = new ArrayList<>();
        for (Hir.Name inc : data.includes()) {
            if (inc.answered() instanceof Hir.Name.Denoting denoting
                    && denoting.type() instanceof TypeSymbol.AtModule spread) {
                invs.addAll(settledClauses(spread, symbols));
            }
        }
        invs.addAll(data.invariants());
        return invs;
    }

    /**
     * The same clauses in the representation a reading of rules takes, with whether every rule that
     * applies was reached.
     *
     * <p>Which representation is read is in the name and not in an argument a caller may pass. There
     * is deliberately no way to ask this for the written tree: what a declaration's rules are in this
     * representation comes from {@code form} and from nowhere else, and a walk that could be handed
     * another source of clauses is a walk somebody can hand the wrong one.
     */
    public static ExpandedRules expandedInvariants(
            TypeSymbol.AtModule named, Symbols symbols, ExpandedClauseLookup form) {
        if (form == null) {
            throw new IllegalArgumentException(
                    "reading a declaration's clauses takes somewhere to read them from");
        }
        return governedBy(named, symbols, form);
    }

    /**
     * A clause and the declaration that wrote it.
     *
     * <p>A clause reaching a type through a spread is that type's to hold and the other type's to
     * have written, and a reader told which clause failed on a data that declares none of its own is
     * told about a declaration they would not find.
     *
     * <p>{@code ordinal} is which of {@code declaredOn}'s own clauses this is, counted where that
     * declaration writes them and not where this walk happened to reach it. Two spreads of one type
     * bring one clause in twice, and a caller keeping one answer per clause has to be able to tell
     * that from two clauses — which the pair says and neither half of it does. Every representation
     * of a declaration writes its clauses in the order they were written, so the number means the
     * same thing in each.
     */
    public record Declared(TypeSymbol.AtModule declaredOn, int ordinal,
                           Hir.InvariantClause clause, CallsLeftStanding standing,
                           AuthoredShape shape) {

        public Declared {
            if (standing == null) {
                throw new IllegalArgumentException("a clause says what its expansion left standing");
            }
            if (shape == null) {
                throw new IllegalArgumentException(
                        "a clause is written in the shape its author wrote it in");
            }
        }

        /** The clause as the reading takes it: the tree, and what the expansion that produced it
         *  left standing. Handed on as one value, because the two are one fact about one tree and
         *  a reader given them apart can be given them mismatched. */
        ClauseAsExpanded asExpanded() {
            return new ClauseAsExpanded(clause.expr(), standing);
        }

        /**
         * The parts its author wrote it in, each with the tree the expansion made of it.
         *
         * <p>Asked of the clause and not worked out from its tree. Which parts a clause has was
         * settled where it was split, and the tree an expansion left holds conjunctions the author
         * did not write — so a reader splitting it would be answering a question this already has
         * an answer to, with a different answer.
         */
        public List<AuthoredShape.Written> parts() {
            return shape.onto(clause.expr(), new RuleRef.Invariant(Clause.Ref.of(this)));
        }
    }

    /**
     * The same in the representation a reading of rules takes, wherever the declaration was written.
     *
     * <p>Its own walk and not the settled one under an argument. Every clause here comes from
     * {@code form}, asked by the declaration's address; there is no parameter through which a caller
     * could offer clauses of its own, and so no way to ask for this representation and be given the
     * written tree. The two walks say the same thing about spreads and differ in that one sentence,
     * and that is the sentence the whole of #1312 was about — shared through a clause-source
     * parameter, it is one edit away from being a choice again.
     *
     * <p>{@code named} is the declaration being read, and the only way in. Which declarations the
     * walk visits is {@code symbols}' to answer and what each of them states is {@code form}'s: two
     * authorities with two questions, and neither is asked the other's. Handed a node as well, which
     * declarations are reached would come from the caller for the one asked about and from the world
     * for the ones under it — and the two need not be the same reading.
     *
     * <p>A spread whose name is not a module's declaration contributes nothing and costs nothing: no
     * module wrote it, so there is no expansion of it to be short of.
     *
     * <p>A declaration this world cannot read is rules not reached and not rules there are none of.
     * What a declaration takes in is written on it, so a walk that could not read it did not walk
     * what it spreads — and the two authorities settle that between them: something declares it or
     * nothing does, which is {@code form}'s to say, and it can be read here or it cannot, which is
     * the world's. Only where nothing declares it at all are there no spreads to be short of.
     */
    private static ExpandedRules governedBy(
            TypeSymbol.AtModule named, Symbols symbols, ExpandedClauseLookup form) {
        ExpandedClauseResult stated = form.of(named.key());
        Hir.Def declared = symbols.declaredNode(named);
        ExpandedRules found = new ExpandedRules(List.of(),
                declared != null || stated instanceof ExpandedClauseResult.NotDeclared);
        if (declared instanceof Hir.Data data) {
            for (Hir.Name inc : data.includes()) {
                if (inc.answered() instanceof Hir.Name.Denoting denoting
                        && denoting.type() instanceof TypeSymbol.AtModule spread) {
                    found = found.and(governedBy(spread, symbols, form));
                }
            }
        }
        return found.and(rulesOf(named, stated));
    }

    /**
     * What the lookup answered for {@code named} alone, as rules.
     *
     * <p>An answer this could not be given is carried as a rule not reached rather than as no rule.
     * They are the same empty list and opposite facts: a declaration that states nothing holds every
     * value its type does, and one whose clauses nobody worked out holds whatever its author wrote.
     * A reading handed the second as the first says a position admits everything and says nothing
     * about not having looked.
     *
     * <p>Handed the answer rather than the lookup, because the walk above reads the same answer to
     * decide something else — whether a declaration nothing here can read is one nothing declares.
     * Asked twice, the two questions could be answered by two.
     */
    private static ExpandedRules rulesOf(TypeSymbol.AtModule named, ExpandedClauseResult stated) {
        return switch (stated) {
            case ExpandedClauseResult.Found(ExpandedClauses clauses) -> {
                List<Declared> out = new ArrayList<>();
                for (int ordinal = 0; ordinal < clauses.clauses().size(); ordinal++) {
                    ExpandedClauses.Expanded each = clauses.clauses().get(ordinal);
                    out.add(new Declared(named, ordinal, each.clause(), each.standing(),
                            each.shape()));
                }
                yield new ExpandedRules(out, true);
            }
            case ExpandedClauseResult.Unavailable _ -> new ExpandedRules(List.of(), false);
            // Nothing declares one, so there is no rule of its to be short of. This is not the walk
            // failing to reach something: it is a name no declaration answers, which every reader
            // above has already had to deal with to have got here.
            case ExpandedClauseResult.NotDeclared _ -> new ExpandedRules(List.of(), true);
        };
    }

    /** The type a newtype wraps ({@code data X = Y} gives {@code Y}), or null when {@code name} is not
     * a newtype — the implicit inner field is {@code value}. */
    public static Type newtypeInner(TypeSymbol name, Symbols symbols) {
        if (symbols.declaredNode(name) instanceof Hir.Data d && d.newtype()) {
            return fieldTypes(d, symbols).get("value");
        }
        return null;
    }

    /**
     * Whether {@code key} may stand as a {@code Map} key in a signature. This is not the question the
     * codecs ask: a type variable may be written — the {@code core}'s {@code Map<'k, 'a>} signatures
     * are — and stands for a key rather than being one, so it is admissible and classifies as
     * nothing. Everything else is admissible exactly when it classifies.
     */
    public static boolean isMapKeyAdmissibleInSignature(Type key, Symbols symbols) {
        return key instanceof Type.Var || classifyConcreteMapKey(key, symbols) != null;
    }

    /**
     * What a concrete {@code key} would be converted through, or null when it has no representation
     * at all. A map's external form is a JSON object, whose keys are strings, so a key that has one
     * renders as and parses from a bare string: {@code String} itself, a temporal as the ISO form a
     * {@code Date} field already travels as, an enumeration as its case's name (issue #161), or a
     * newtype over any of those (ADR-0040).
     *
     * <p>The newtype rule is the base's rule, asked again of the base: a newtype is written as what
     * it wraps is written, so it is a key exactly when what it wraps is a key. That is why the walk
     * recurses and why the recursion is here and nowhere else. What it answers with is the outermost
     * name, never the base's representation — a wrapper is read and written by its own derived
     * codec, which is what carries its invariant, so a reader that reached for the base's would be
     * reaching past the type the map is keyed by.
     *
     * <p>An {@code Int}-backed newtype stays out because {@code Int} does, and for the reason
     * ADR-0040 gives: {@code Int} is written as a JSON number in a field, so writing it as a string
     * in a key would make a type's external form depend on where it stands.
     *
     * <p>This classifies and does not admit. Whether a key of a given representation may stand in a
     * given position is the position's own question — a behavior's boundary refuses a name the
     * language declares of its own operations, and a fixture's position asks something else again —
     * so what comes back says what the key converts through and nothing about where it may be
     * written. A boundary turns one into a {@link CrossingMapKey} once it has admitted the name.
     *
     * <p>The classification itself is this function's alone. A reader that builds a decoder, renders
     * an encoder's keys or lowers a key into the codec IR takes what it needs from the result; none
     * of them asks the type again.
     */
    public static MapKeyRepresentation classifyConcreteMapKey(Type key, Symbols symbols) {
        return classifyMapKey(key, symbols, new HashSet<>());
    }

    private static MapKeyRepresentation classifyMapKey(Type key, Symbols symbols,
                                                       Set<TypeSymbol> unwrapping) {
        // Exhaustive over the primitives: whether a key has a text form is a question about each one,
        // and a chain of comparisons answers "no" for a primitive added later without being asked.
        if (key instanceof Type.Prim p) {
            return switch (p) {
                case STRING -> new MapKeyRepresentation.Text();
                case DATE -> new MapKeyRepresentation.Date();
                case TIME -> new MapKeyRepresentation.Time();
                case DATETIME -> new MapKeyRepresentation.DateTime();
                case INSTANT -> new MapKeyRepresentation.Instant();
                // a key is addressed by the text it is written as, and a number, a flag and Raw have
                // none a boundary could name one by
                case INT, BOOL, DECIMAL, RAW -> null;
            };
        }
        if (!(key instanceof Type.Ref r) || !unwrapping.add(r.name())) {
            return null;   // a newtype reaching itself; DataChecker reports it, this must terminate
        }
        // Whether a key renders as a bare name is the boundary's question and is asked of it. Asked
        // as `isUnitOnlySum`, this read the language's notion of an enumeration for an answer about
        // how a value is written, and the two would part the day either moved.
        if (Boundary.of(key, symbols).representation()
                instanceof Boundary.Representation.Enumeration) {
            return new MapKeyRepresentation.NamedKey(r.name());
        }
        Type base = newtypeInner(r.name(), symbols);
        return base != null && classifyMapKey(base, symbols, unwrapping) != null
                ? new MapKeyRepresentation.NamedKey(r.name())
                : null;
    }

    /**
     * Whether every case of a sum is a unit data — an enumeration, carrying nothing but which case it
     * is. What holds of every case is a property of the sum: such a sum crosses the boundary as that
     * case's name, a bare string, so it renders and parses in key position like any other string
     * (issue #161, ADR-0040). A sum with even one field-bearing case keeps the discriminator object.
     */
    public static boolean isUnitOnlySum(Type t, Symbols symbols) {
        return t instanceof Type.Ref ref && symbols.declaredNode(ref.name()) instanceof Hir.SumData sum
                && isUnitOnlySum(sum, symbols);
    }

    public static boolean isUnitOnlySum(Hir.SumData sum, Symbols symbols) {
        List<TypeSymbol> leaves = AtomSpace.subjectAtoms(Type.ref(sum.declares()), symbols);
        if (leaves.isEmpty()) {
            return false;
        }
        for (TypeSymbol leaf : leaves) {
            if (!(symbols.declaredNode(leaf) instanceof Hir.UnitData)) {
                return false;
            }
        }
        return true;
    }

    /** The key of the first {@code Map} inside {@code t} that cannot cross the boundary, or null when
     * every one can — what a data field or a behavior's input/output is checked against. */
    public static Type nonBoundaryMapKey(Type t, Symbols symbols) {
        if (t instanceof Type.MapOf m && !isMapKeyAdmissibleInSignature(m.key(), symbols)) {
            return m.key();
        }
        return switch (t) {
            case Type.ListOf l -> nonBoundaryMapKey(l.element(), symbols);
            case Type.SetOf s -> nonBoundaryMapKey(s.element(), symbols);
            case Type.OptionOf o -> nonBoundaryMapKey(o.element(), symbols);
            case Type.MapOf m -> nonBoundaryMapKey(m.value(), symbols);
            case Type.TupleOf tu -> tu.elements().stream()
                    .map(e -> nonBoundaryMapKey(e, symbols)).filter(k -> k != null).findFirst().orElse(null);
            default -> null;
        };
    }


    public static boolean isSingleValueNewtype(Type t, Symbols symbols) {
        return t instanceof Type.Ref ref
                && symbols.declaredNode(ref.name()) instanceof Hir.Data d && d.newtype();
    }

    /**
     * The enumeration two operands of {@code <}/{@code <=}/{@code >}/{@code >=} are ordered by, or
     * null when they are not both values of one. Either side may name it: {@code stage < Won}
     * carries the order on the left, and a case listed by two sums takes the one it is compared with.
     *
     * <p>Which types this is asked of decides what it means, so it is not visible outside this
     * package. Asked of the operands as written it is the nominal admissibility rule, and that is
     * {@code BinaryElaborator}'s alone: two different newtypes over one enumeration must not meet
     * here. Asked of what they open to it is how the comparison emits, and that is
     * {@link Ordering#ofComparison}. The backend reaching past both and asking this itself is how
     * the same type came to be ordered to one reader and not to another (issue #856).
     */
    static TypeSymbol comparisonEnumeration(Type lt, Type rt, Symbols symbols) {
        TypeSymbol named = orderingEnumeration(lt, symbols);
        if (named == null) {
            named = orderingEnumeration(rt, symbols);
        }
        return named != null && isValueOfEnumeration(lt, named, symbols)
                && isValueOfEnumeration(rt, named, symbols) ? named : null;
    }

    /** Whether {@code t} is that enumeration, one of its leaves, or a union of them. */
    private static boolean isValueOfEnumeration(Type t, TypeSymbol enumeration, Symbols symbols) {
        if (t instanceof Type.Union union) {
            for (TypeSymbol member : union.members()) {
                if (!isValueOfEnumeration(Type.ref(member), enumeration, symbols)) {
                    return false;
                }
            }
            return !union.members().isEmpty();
        }
        return t instanceof Type.Ref ref && (ref.name().equals(enumeration)
                || (symbols.declaredNode(enumeration) instanceof Hir.SumData sum
                    && AtomSpace.subjectAtoms(Type.ref(sum.declares()), symbols).contains(ref.name())));
    }

    /**
     * The enumeration that orders a value of {@code t} — a sum all of whose cases are unit data,
     * ordered by the order they are declared in, which is the order a state machine moves through
     * (F#'s discriminated union, Haskell's derived {@code Ord}, Java's ordinal; issue #161). It is
     * the type itself when it is one, or the single one that lists it as a case.
     *
     * <p>Null when the type is not one and when more than one enumeration lists it: a unit data may
     * be a case of two sums, which place it differently, so no one order is the value's own. The
     * order therefore belongs to the sum and not to the case value.
     */
    static TypeSymbol orderingEnumeration(Type t, Symbols symbols) {
        Set<TypeSymbol> candidates = orderingCandidates(t, symbols);
        return candidates != null && candidates.size() == 1 ? candidates.iterator().next() : null;
    }

    /**
     * Every enumeration that could order a value of {@code t}, or null when it is not a value of one.
     * A list literal of case values (`[ Negotiation, Prospecting ]`) is typed as the union of their
     * types, and what orders it is the enumeration that lists all of them — so the candidates are
     * intersected across the members rather than each member having to name one on its own.
     */
    private static Set<TypeSymbol> orderingCandidates(Type t, Symbols symbols) {
        if (t instanceof Type.Union union) {
            Set<TypeSymbol> shared = null;
            for (TypeSymbol member : union.members()) {
                Set<TypeSymbol> owners = orderingCandidates(Type.ref(member), symbols);
                if (owners == null) {
                    return null;
                }
                if (shared == null) {
                    shared = new LinkedHashSet<>(owners);
                } else {
                    shared.retainAll(owners);
                }
            }
            return shared;
        }
        // What the language gives is declared by no module, so no module holds an enumeration
        // listing it; the lookups below answered nothing for one before they were asked this way.
        if (!(t instanceof Type.Ref(TypeSymbol.AtModule named))) {
            return null;
        }
        if (symbols.declaredNode(named) instanceof Hir.SumData sum) {
            return isUnitOnlySum(sum, symbols) ? Set.of(named) : null;
        }
        if (!(symbols.declaredNode(named) instanceof Hir.UnitData)) {
            return null;
        }
        // A sum and its cases are declared together (a case declared elsewhere cannot join a union
        // here, E1606), so every enumeration that lists this case is in the case's own module. Asking
        // that module rather than what is visible keeps the answer the same in every module that
        // reads the value.
        Set<TypeSymbol> owners = new LinkedHashSet<>();
        for (String declared : symbols.declaredNamesIn(named.module())) {
            if (symbols.declaredNode(new TypeKey(named.module(), declared)) instanceof Hir.SumData s
                    && isUnitOnlySum(s, symbols)
                    && AtomSpace.subjectAtoms(Type.ref(s.declares()), symbols).contains(named)) {
                owners.add(s.declares());
            }
        }
        return owners;
    }

    /**
     * The underlying base of a type: itself, or — for a single-value newtype ({@code data X = Y}) —
     * the base of its {@code value} type, recursively (so {@code 管理職 = レベル = Int} bases to Int).
     * A newtype's value is what its comparison and equality read.
     */
    public static Type base(Type t, Symbols symbols) {
        return newtypeSpine(t, symbols).terminal();
    }

    /**
     * The names wrapped round a value and what is left when they are off.
     *
     * <p>The one walk. How far a newtype reaches is a fact about the declarations and not about who
     * is asking, and two readers deciding it apart is what #461 was: a comparison reaching the base
     * while the range beside it stopped at the first name. Everything that needs to know derives from
     * here — what a value is carried as, which declarations' rules apply to it, whether it is a
     * number at all.
     *
     * <p>Stops on a name already worn, so a declaration reachable from itself ends the walk rather
     * than repeating it, and stops where a newtype's {@code value} is not declared.
     */
    public static NewtypeSpine newtypeSpine(Type t, Symbols symbols) {
        List<Layer> layers = new ArrayList<>();
        Set<TypeSymbol> worn = new LinkedHashSet<>();
        Type at = t;
        while (isSingleValueNewtype(at, symbols) && worn.add(((Type.Ref) at).name())) {
            Hir.Data data = (Hir.Data) symbols.declaredNode(((Type.Ref) at).name());
            layers.add(new Layer(((Type.Ref) at).name()));
            Type inner = fieldTypes(data, symbols).get("value");
            if (inner == null) {
                break;
            }
            at = inner;
        }
        return new NewtypeSpine(List.copyOf(layers), at);
    }

    /** The names a value wears, and the type underneath them. */
    public record NewtypeSpine(List<Layer> layers, Type terminal) {}

    /**
     * One name a value wears.
     *
     * <p>The name and nothing under it. A rule is read at a layer and reported by the declaration
     * that wrote it, and the name is what says which that is — so a reader with the name has what a
     * diagnostic needs and asks the world it is reading in for the rest. The declaration was carried
     * here as well, and a reader holding one holds the clauses written on it, in whichever
     * representation this walk's world happened to be at; what a rule states is asked for by naming
     * the representation it is read in, and this walk names none.
     */
    public record Layer(TypeSymbol named) {}

    /**
     * The names wrapped round a value of {@code t}, outermost first.
     *
     * <p>One place says how far a newtype reaches, because more than one thing needs to know: what a
     * construction has to satisfy, what a position's range is, what a comparison of two such values
     * compares. Each working it out for itself is how they came to disagree about a value wearing two
     * names — the projection reading through and the range reader stopping at the first.
     *
     * <p>Stops on a name already worn, so a declaration that wraps its own kind ends the walk rather
     * than repeating it. A type that is not a newtype has one layer or none.
     */
    public static List<Layer> newtypeChain(Type t, Symbols symbols) {
        return newtypeSpine(t, symbols).layers();
    }

    /**
     * The {@code Int} or {@code Decimal} a value of {@code t} is carried as, reaching through as many
     * newtypes as it is written with, or {@code null} where it is carried as neither.
     *
     * <p>The language's own reading: a comparison of two such values compares the numbers underneath
     * however many names are wrapped round them (ADR-0047). Not what arithmetic asks — that is
     * {@link #directNumericNewtypeBase} and stops at one layer, which the language means.
     */
    public static Type numericBase(Type t, Symbols symbols) {
        Type carried = newtypeSpine(t, symbols).terminal();
        return carried == Type.INT || carried == Type.DECIMAL ? carried : null;
    }

    /** What a single-value newtype directly wraps (one level, so a newtype over a newtype answers
     * with that newtype), or {@code null} for anything that is not one. */
    static Type wrapped(Type t, Symbols symbols) {
        if (isSingleValueNewtype(t, symbols)) {
            return fieldTypes((Hir.Data) symbols.declaredNode(((Type.Ref) t).name()), symbols).get("value");
        }
        return null;
    }

    /** The Int or Decimal that a single-value newtype directly wraps (one level), or {@code null}
     * (a non-newtype, or a newtype over a non-numeric or over another newtype). */
    static Type directNumericNewtypeBase(Type t, Symbols symbols) {
        Type inner = wrapped(t, symbols);
        return inner == Type.INT || inner == Type.DECIMAL ? inner : null;
    }

    /** The single-value numeric newtype a closed {@code +}/{@code -} over {@code lt} and {@code rt}
     * yields — whichever operand is such a newtype — or {@code null} if neither is. Callers that have
     * already passed the type checker's admissibility gate (codegen, the invariant analysis) use this
     * to pick the result without re-deriving the rule. */
    public static Type closedNewtypeArithResult(Type lt, Type rt, Symbols symbols) {
        if (directNumericNewtypeBase(lt, symbols) != null) {
            return lt;
        }
        if (directNumericNewtypeBase(rt, symbols) != null) {
            return rt;
        }
        return null;
    }

    public static Type primType(Hir.RawKind kind) {
        return switch (kind) {
            case TEXT -> Type.STRING;
            case INT -> Type.INT;
            case BOOL -> Type.BOOL;
            case DECIMAL -> Type.DECIMAL;
            case DATE -> Type.DATE;
            case TIME -> Type.TIME;
            case DATETIME -> Type.DATETIME;
            case INSTANT -> Type.INSTANT;
        };
    }

    public static Type primType(LeafScalar kind) {
        return switch (kind) {
            case STRING -> Type.STRING;
            case INT -> Type.INT;
            case BOOL -> Type.BOOL;
            case DECIMAL -> Type.DECIMAL;
            case DATE -> Type.DATE;
            case TIME -> Type.TIME;
            case DATETIME -> Type.DATETIME;
            case INSTANT -> Type.INSTANT;
        };
    }

    /**
     * A reference whose parts have been resolved and whose own type has not been decided yet.
     *
     * <p>The one moment there is such a thing, and it is inside {@code Resolve}: the arguments are
     * {@link Hir} because they have been answered, and there is no {@link Hir.TypeRef} yet because
     * what it denotes is what {@link #denoted} is being asked for. Naming it is what keeps a
     * half-built reference — one carrying a type standing in for the answer — from existing at all.
     */
    record Reference(WrittenName written, Hir.TypeTerm arg, List<Hir.TypeTerm> tupleElems,
                     SourcePos anchor) {

        String name() {
            return written == null ? null : written.canonical();
        }

        boolean isTuple() {
            return written == null && tupleElems != null;
        }

        SourcePos pos() {
            return written == null ? anchor : written.pos();
        }
    }

    /**
     * The type {@code ref} denotes, computed from the reference and the scope it was written in. The
     * one place a written type becomes a {@link Type}; {@code Resolve} calls it once per reference and
     * everything else reads {@link Hir.TypeRef#denotes()}. Its own arguments are already resolved, so
     * a nested reference is read rather than recomputed.
     */
    static Type denoted(Reference ref, NameSense symbols) {
        if (ref.isTuple()) {
            List<Type> elems = new ArrayList<>();
            for (Hir.TypeTerm e : ref.tupleElems()) {
                elems.add(resolveTerm(e));
            }
            return Type.tuple(elems);   // (A, B, ...) — a helper/stdlib signature only (ADR-0036)
        }
        return switch (ref.name()) {
            case "Int" -> Type.INT;
            case "String" -> Type.STRING;
            case "Bool" -> Type.BOOL;
            case "Decimal" -> Type.DECIMAL;
            case "Date" -> Type.DATE;
            case "Time" -> Type.TIME;
            case "DateTime" -> Type.DATETIME;
            case "Instant" -> Type.INSTANT;
            // 制約違反 is no longer a writable case: an invariant violation aborts (spec §algebraic-types,
            // §violation-destination).
            case "List" -> Type.list(typeArg(ref, "list", 4));
            case "Set" -> {
                // a set holds no duplicates, which is a question about equality of its elements
                Type element = typeArg(ref, "set", 3);
                requireEquality(element, ref, false);
                yield Type.set(element);
            }
            case "Option" -> Type.option(typeArg(ref, "option", 6));
            case "Map" -> {
                // The key is not restricted here: a map that stays inside a behavior body renders
                // nothing, so it may be keyed by any value (`List.groupBy` already builds such maps).
                // What a key must satisfy is the boundary — see #isBoundaryMapKey, checked where a
                // type is a data field or a behavior's input/output.
                Type value = typeArg(ref, "map", 3);
                Type key = ref.tupleElems() == null
                        ? Type.STRING : resolveTerm(ref.tupleElems().get(0));
                requireEquality(key, ref, true);
                yield Type.map(key, value);
            }
            default -> {
                if (ref.name().startsWith("'")) {
                    yield Type.var(ref.name());   // a type variable, admitted only in the core
                }
                switch (symbols.scope().resolve(ref.written())) {
                    case Denotation.Denotes d -> {
                        yield Type.ref(d.type());
                    }
                    // In scope denoting nothing: the import line that could not bring it in was
                    // reported there, and a use of it takes the error type rather than being
                    // reported again here.
                    case Denotation.StandsForNothing ignored -> {
                        yield Type.ERRONEOUS;
                    }
                    case Denotation.NotInScope ignored -> { }
                }
                // A union's case names a type where a type goes. A `match` arm has always read it
                // that way; a declaration reads it the same, which is what lets `Int |
                // DivisionByZero` be written rather than only met. Asked after the module's own
                // declarations, so a name a model declares keeps its meaning.
                if (symbols.scope().resolveCase(ref.written()) instanceof Denotation.Denotes asCase) {
                    yield Type.ref(asCase.type());
                }
                throw unknownType(ref, symbols);
            }
        };
    }

    /**
     * A collection inside a type that would have to compare something it cannot. Which collection it
     * is, is part of the answer: a {@code Set} asks whether two elements are equal and a {@code Map}
     * whether two keys are, and those are different requirements, so a reader that reports one must
     * not report it as the other.
     */
    public sealed interface UncomparableIn {
        Type type();

        record SetElement(Type type) implements UncomparableIn {}

        record MapKey(Type type) implements UncomparableIn {}
    }

    /**
     * The {@code Set} element or {@code Map} key inside {@code t} that cannot be compared, or null
     * when every one of them can. Walks the whole type, so a set nested in a list or under a map's
     * value is asked the same question as one written on its own.
     */
    public static UncomparableIn uncomparableCollection(Type t, Symbols symbols) {
        return switch (t) {
            case Type.SetOf s -> !supportsEquality(s.element())
                    ? new UncomparableIn.SetElement(s.element())
                    : uncomparableCollection(s.element(), symbols);
            case Type.MapOf m -> !supportsEquality(m.key())
                    ? new UncomparableIn.MapKey(m.key())
                    : firstNonNull(uncomparableCollection(m.key(), symbols),
                            uncomparableCollection(m.value(), symbols));
            case Type.ListOf l -> uncomparableCollection(l.element(), symbols);
            case Type.OptionOf o -> uncomparableCollection(o.element(), symbols);
            case Type.TupleOf tu -> {
                for (Type e : tu.elements()) {
                    UncomparableIn bad = uncomparableCollection(e, symbols);
                    if (bad != null) {
                        yield bad;
                    }
                }
                yield null;
            }
            default -> null;
        };
    }

    private static UncomparableIn firstNonNull(UncomparableIn a, UncomparableIn b) {
        return a != null ? a : b;
    }

    /** Refuses a collection whose element or key a function makes uncomparable. */
    private static void requireEquality(Type t, Reference at, boolean aMapKey) {
        if (!supportsEquality(t)) {
            throw CompileException.of(Diagnostic.at(at.pos())
                    .say(aMapKey
                            ? new TypeMessage.AMapKeyIsComparedAndAFunctionIsNot(Type.show(t))
                            : new TypeMessage.ASetElementIsComparedAndAFunctionIsNot(Type.show(t)))
                    .build());
        }
    }

    /** The single type argument of a built-in constructor, or the error that says it is missing. */
    private static Type typeArg(Reference ref, String key, int width) {
        if (ref.arg() == null) {
            throw CompileException.of(Diagnostic.at(ref.pos(), width)
                    .say(switch (key) {
                        case "list" -> new TypeMessage.AListNeedsItsElementType();
                        case "set" -> new TypeMessage.ASetNeedsItsElementType();
                        case "map" -> new TypeMessage.AMapNeedsItsValueType();
                        default -> new TypeMessage.AnOptionNeedsItsTypeArgument();
                    })
                    .build());
        }
        return resolveTerm(ref.arg());
    }

    /**
     * A written type name nothing here denotes. A qualified name says which of three things went
     * wrong, since the qualifier narrows it: the qualifier names no module, the module declares no
     * such type, or it declares it and does not expose it.
     */
    private static CompileException unknownType(Reference ref, NameSense symbols) {
        return unknownType(ref.written(), symbols);
    }

    public static CompileException unknownType(WrittenName written, NameSense symbols) {
        String canonical = written.canonical();
        int dot = canonical.lastIndexOf('.');
        if (dot >= 0) {
            String qualifier = canonical.substring(0, dot);
            String name = canonical.substring(dot + 1);
            String module = symbols.scope().moduleOfQualifier(qualifier);
            if (module == null) {
                // Said over the whole name, since which of the two parts is wrong is what the
                // message settles, and repaired over the qualifier alone: the candidate is a
                // qualifier, and writing it over `up.Amount` would take the type name with it.
                return CompileException.of(Diagnostic.say(new ModuleMessage.NoModuleOfThatName(qualifier, name))
                                .at(written.reportedAt())
                                .repair(written.qualifier(),
                                        Suggest.candidate(qualifier, symbols.scope().qualifiers()))
                                .build());
            }
            boolean declared = symbols.declares(new TypeKey(module, name));
            return CompileException.of(Diagnostic.at(written.reportedAt())
                            .say(declared
                                    ? new ModuleMessage.ItIsDeclaredThereAndNotExposed(name, module)
                                    : new ModuleMessage.TheModuleDeclaresNoSuchQualifiedName(name,
                                            module))
                            // The module is the one the author meant; the part after the dot is
                            // what it has no such name for, and is the part to write over.
                            .repair(written.lastSegment(),
                                    Suggest.candidate(name, symbols.declaredNamesIn(module)))
                            .build());
        }
        Set<String> known = symbols.scope().namesInScope();
        return CompileException.of(Diagnostic
                        .at(written.reportedAt())
                        .repair(written.reportedAt(), Suggest.candidate(canonical, known))
                        .say(new NameMessage.NoTypeOfThatName(written.quoted())).build());
    }
}
