package souther.compiler.check;

import souther.compiler.ast.Hir;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.Diagnostic;
import souther.compiler.diag.msg.TypeMessage;
import souther.compiler.diag.Region;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.LeafScalar;
import souther.compiler.types.MapKeyRepresentation;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds what a behavior's boundary carries, refusing what it does not admit as it goes.
 *
 * <p>Validating and building are one walk because they are one question. Asked separately, the
 * checker's admitted set and the shapes a reader is handed agree only by coincidence, and nothing
 * says so: a rule relaxed on one side and not the other makes an ordinary compile succeed and the
 * reader raise. Here a shape exists because the walk that would have refused it did not, and a
 * signature that has one has been through every obligation.
 *
 * <p>Each structural position owns admissibility of the subtree under it, so there is no ordering
 * between rules to know. A {@code Map}'s key is asked whether it can be written as a boundary key,
 * whatever else it is; an element is asked what an element is asked. That is also what makes the
 * report the useful one — an optional in key position is refused for standing where a key goes, and
 * writing {@code Int} there instead does not help.
 *
 * <p>What replaces the ordering, where a signature is wrong in more than one place, is the order the
 * walk takes: the parameters as they are written, then the answer, and within a type from the
 * outside in. A precedence between rules would be a second thing to state and to keep true; the
 * traversal is one thing, and it is the one the author reads their own declaration in.
 *
 * <p>Reached from {@link SignatureDeclarations} for a written declaration and from
 * {@link PipelineSigs} for what a composition answers: this is how a signature is made, not a
 * question about a type that anything may ask again. Those are the two origins there are, and each
 * is admitted once.
 */
final class SignatureBoundary {

    private SignatureBoundary() {}

    /**
     * The signature a declared behavior publishes — every parameter and its answer.
     *
     * <p>Each parameter leaves the walk beside the shape it was admitted as. One parameter makes one
     * shape or the walk refuses, so which shape belongs to which parameter is settled here, where
     * both are in hand, rather than by a reader holding the two lists afterwards.
     */
    static DeclaredSig of(Hir.SpecBehavior spec, Symbols symbols, DeclarationKinds kinds,
                          PublishedDeclarations published) {
        List<DeclaredSig.Input> ins = new ArrayList<>(spec.params().size());
        for (Hir.Param p : spec.params()) {
            Type t = TypeOps.successType(p.type());
            ins.add(new DeclaredSig.Input(p.name(),
                    input(t, t, Where.param(p, spec.pos()), symbols, kinds, published)));
        }
        Type out = TypeOps.successType(spec.ret());
        return new DeclaredSig(ins,
                output(out, out, Where.output(spec.name(), spec.pos()), symbols, kinds, published));
    }

    /**
     * What a composition answers with. A composition's inputs are its first stage's, which arrive
     * already admitted; its output is a type nobody wrote — the last stage's answer merged with the
     * cases that left the main line — and is asked here for the first time.
     */
    static BoundaryOutput composedOutput(String behavior, SourcePos at, Type out, Symbols symbols,
                                         DeclarationKinds kinds,
                                         PublishedDeclarations published) {
        return output(out, out, Where.output(behavior, at), symbols, kinds, published);
    }

    /**
     * What a parameter can arrive as.
     *
     * <p>{@code whole} is the parameter's own type, which the report about a function names: what is
     * refused is carrying one, and where in the type it sits is not what the author has to change.
     */
    private static BoundaryInput input(Type t, Type whole, Where where, Symbols symbols,
                                       DeclarationKinds kinds,
                                       PublishedDeclarations published) {
        return switch (t) {
            case Type.Prim p -> new BoundaryInput.Scalar(scalar(p, where));
            case Type.Ref r -> new BoundaryInput.Nominal(nominal(r.name(), where, symbols));
            case Type.ListOf l ->
                    new BoundaryInput.ListOf(input(l.element(), whole, where, symbols, kinds, published));
            case Type.SetOf s ->
                    new BoundaryInput.SetOf(input(s.element(), whole, where, symbols, kinds, published));
            case Type.MapOf m -> new BoundaryInput.MapOf(mapKey(m.key(), where, symbols, kinds, published),
                    input(m.value(), whole, where, symbols, kinds, published));
            // A parameter names a single type, a named sum included, so the members of a union have
            // no name the far side can hold onto: the input and the output are separate for this.
            case Type.Union u -> throw union(u, where);
            case Type.OptionOf o -> throw optional(o, where);
            case Type.TupleOf _ -> throw tuple(where);
            case Type.FnOf _ -> throw function(whole, where);
            case Type.Erroneous _ -> throw new Unanswerable(where.pos());
            case Type.Var _, Type.MetaVar _, Type.Nothing _, Type.Never _ -> throw unwritable(t);
        };
    }

    /** What a behavior's answer can leave as. */
    private static BoundaryOutput output(Type t, Type whole, Where where, Symbols symbols,
                                         DeclarationKinds kinds,
                                         PublishedDeclarations published) {
        return switch (t) {
            case Type.Prim p -> new BoundaryOutput.Scalar(scalar(p, where));
            case Type.Ref r -> new BoundaryOutput.Nominal(nominal(r.name(), where, symbols));
            case Type.Union u -> new BoundaryOutput.Cases(members(u, where, symbols));
            case Type.ListOf l ->
                    new BoundaryOutput.ListOf(output(l.element(), whole, where, symbols, kinds, published));
            case Type.SetOf s ->
                    new BoundaryOutput.SetOf(output(s.element(), whole, where, symbols, kinds, published));
            case Type.MapOf m -> new BoundaryOutput.MapOf(mapKey(m.key(), where, symbols, kinds, published),
                    output(m.value(), whole, where, symbols, kinds, published));
            case Type.OptionOf o -> throw optional(o, where);
            case Type.TupleOf _ -> throw tuple(where);
            case Type.FnOf _ -> throw function(whole, where);
            case Type.Erroneous _ -> throw new Unanswerable(where.pos());
            case Type.Var _, Type.MetaVar _, Type.Nothing _, Type.Never _ -> throw unwritable(t);
        };
    }

    /** The scalar a primitive stands for. {@code Raw} is written like one and is the language's own
     *  vocabulary rather than a model's, so it is refused as the name it is. */
    private static LeafScalar scalar(Type.Prim prim, Where where) {
        LeafScalar scalar = LeafScalar.of(prim);
        if (scalar == null) {
            throw foreignName(TypeSymbol.primitive("Raw"), where);
        }
        return scalar;
    }

    /** A name the boundary carries: one a model declared, which is what a derived codec is for. The
     *  language declares vocabulary of its own — what a division by zero answers with, what a
     *  rounding takes — and each of those says what one of the language's operations can answer.
     *  The rule is {@link CrossingNominal}'s, shared with every other position that crosses; how a
     *  refusal is worded is this position's. */
    private static CrossingNominal nominal(TypeSymbol name, Where where, Symbols symbols) {
        CrossingNominal admitted = CrossingNominal.admitted(name, symbols);
        if (admitted == null) {
            throw foreignName(name, where);
        }
        return admitted;
    }

    /**
     * The members of the union a behavior answers with, each a name in what crosses.
     *
     * <p>The one position where a name may be a scalar's. {@code Int | DivisionByZero} answers a
     * primitive beside a case, and a union holds its members as names, so the primitive arrives
     * spelled like a declaration. Which of the two a member is decides which rule it is held to —
     * a scalar the boundary writes, or a name a model declares — and neither answers for the other.
     */
    private static List<TypeSymbol> members(Type.Union union, Where where, Symbols symbols) {
        List<TypeSymbol> members = new ArrayList<>(union.members().size());
        for (TypeSymbol member : union.members()) {
            members.add(member.isPrimitive()
                    ? scalarMember(member, where)
                    : nominal(member, where, symbols).name());
        }
        return members;
    }

    /**
     * A member written in the language's own namespace, which crosses when it is a scalar the
     * boundary writes.
     *
     * <p>{@code Raw} is spelled like a primitive and stands for no scalar, and {@code Some} and
     * {@code None} are names of that namespace standing for no primitive at all. Each is the
     * language's own word rather than a model's, which is what the report says.
     */
    private static TypeSymbol scalarMember(TypeSymbol member, Where where) {
        Type.Prim prim = member.primitiveKind();
        if (prim == null || LeafScalar.of(prim) == null) {
            throw foreignName(member, where);
        }
        return member;
    }

    /**
     * The witness a key position answers with. A map's external form is an object, whose keys are
     * strings, so this is the first thing asked of whatever stands there — an optional, a list or an
     * {@code Int} is refused for being unwritable as a key rather than for anything else it is.
     *
     * <p>A key that classifies is still the boundary's, so a name the language declares is refused
     * here as it is anywhere else in the shape.
     */
    private static CrossingMapKey mapKey(Type key, Where where, Symbols symbols,
                                         DeclarationKinds kinds,
                                         PublishedDeclarations published) {
        MapKeyRepresentation representation =
                TypeOps.classifyConcreteMapKey(key, symbols, kinds, published);
        if (representation == null) {
            throw notAKey(key, where);
        }
        return switch (representation) {
            case MapKeyRepresentation.NamedKey n ->
                    CrossingMapKey.named(nominal(n.name(), where, symbols));
            case MapKeyRepresentation.Lexical l -> CrossingMapKey.lexical(l);
        };
    }

    private static CompileException union(Type.Union u, Where where) {
        String shown = Type.show(u);
        return where.refusal(new TypeMessage.AParameterIsAnAnonymousUnion(where.name(), shown));
    }

    /** A tuple is expression-level only: it has no external representation, so it cannot cross a
     *  decoder or an encoder. A tuple in a helper's signature is fine — it never touches a codec. */
    private static CompileException tuple(Where where) {
        return where.parameter()
                ? where.refusal(new TypeMessage.AParameterIsATuple(where.name()))
                : where.refusal(new TypeMessage.AnOutputIsATuple(where.name()));
    }

    /** A function has no external representation at any depth, since one hides as easily inside a
     *  {@code List} or a {@code Map} as it stands on its own. */
    private static CompileException function(Type whole, Where where) {
        String shown = Type.show(whole);
        return where.parameter()
                ? where.refusal(new TypeMessage.AParameterCarriesAFunction(where.name(), shown))
                : where.refusal(new TypeMessage.AnOutputCarriesAFunction(where.name(), shown));
    }

    /**
     * An optional does not stand in a behavior's boundary. A boundary carries the model's own
     * vocabulary, and absence there is owned by the data that holds it, on a {@code ?} field, or by
     * a sum the model names. What may not cross is the runtime representation of structural
     * optionality, which has no owner on the far side.
     */
    private static CompileException optional(Type.OptionOf o, Where where) {
        String shown = Type.show(o);
        return where.parameter()
                ? where.hinted(new TypeMessage.AParameterCarriesAnOptional(where.name(), shown),
                        new TypeMessage.AModelTypeOwnsTheAbsence())
                : where.hinted(new TypeMessage.AnOutputCarriesAnOptional(where.name(), shown),
                        new TypeMessage.AModelTypeOwnsTheAbsence());
    }

    /** What to write instead, said of the optional rather than of the position it was found in: the
     *  optional a collection carries is not the behavior's own answer, so advice naming the
     *  behavior's type would be advice about something else. */
    private static CompileException foreignName(TypeSymbol foreign, Where where) {
        return where.parameter()
                ? where.hinted(new TypeMessage.AParameterTakesATypeTheLanguageDeclares(
                                where.name(), foreign.name()),
                        new TypeMessage.DeclareItAsATypeOfTheModel())
                : where.hinted(new TypeMessage.AnOutputIsATypeTheLanguageDeclares(
                                where.name(), foreign.name()),
                        new TypeMessage.DeclareItAsATypeOfTheModel());
    }

    /** What to write instead. Said as declaring a type rather than as supplying a codec: the name is
     *  refused for whose vocabulary it is, and a codec for it would not change that. */
    private static CompileException notAKey(Type key, Where where) {
        String shown = Type.show(key);
        return where.parameter()
                ? where.hinted(new TypeMessage.AParameterMapCannotBeKeyedByThat(where.name(), shown),
                        new TypeMessage.AMapIsAJsonObjectKeyedByStrings())
                : where.hinted(new TypeMessage.AnOutputMapCannotBeKeyedByThat(where.name(), shown),
                        new TypeMessage.AMapIsAJsonObjectKeyedByStrings());
    }

    /**
     * A signature carrying a type no signature can be written with — a variable, a bottom, the type
     * of an expression that answers nothing. The spelling that would reach one is refused where it
     * is written, so arriving here is the compiler disagreeing with itself rather than anything a
     * reader of the module can act on.
     */
    private static IllegalStateException unwritable(Type t) {
        return new IllegalStateException(
                "`" + Type.show(t) + "` reached a behavior's boundary, which carries the types a"
                        + " model can write; the resolver and this disagree about what may be written");
    }

    /**
     * Where a refusal is reported and how it names what it refuses. A parameter is underlined where
     * it was written; an answer is reported on the behavior, which is what names it.
     */
    private record Where(boolean parameter, String name, Region region, SourcePos pos) {

        static Where param(Hir.Param p, SourcePos behavior) {
            return new Where(true, p.name(), p.written().region(), behavior);
        }

        static Where output(String behavior, SourcePos pos) {
            return new Where(false, behavior, null, pos);
        }

        <M extends souther.compiler.diag.msg.Message & souther.compiler.diag.msg.Reported>
                CompileException refusal(M said) {
            Diagnostic.Builder builder = Diagnostic.say(said);
            return CompileException.of(
                    (region == null ? builder.at(pos) : builder.at(region)).build());
        }

        <M extends souther.compiler.diag.msg.Message & souther.compiler.diag.msg.Reported,
                H extends souther.compiler.diag.msg.Message & souther.compiler.diag.msg.Supporting>
                CompileException hinted(M said, H hint) {
            Diagnostic.Builder builder = Diagnostic.say(said).hint(hint);
            return CompileException.of(
                    (region == null ? builder.at(pos) : builder.at(region)).build());
        }

    }
}
