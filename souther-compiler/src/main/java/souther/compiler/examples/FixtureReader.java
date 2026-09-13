package souther.compiler.examples;

import souther.compiler.execute.BoundaryValues;
import souther.compiler.generated.MemoryClassLoader;
import souther.compiler.ast.Hir;
import souther.compiler.check.AtomSpace;
import souther.compiler.check.CallElaborator;
import souther.compiler.check.DeclarationKinds;
import souther.compiler.check.PublishedDeclarations;
import souther.compiler.check.Symbols;
import souther.compiler.cst.SyntaxKind;
import souther.compiler.core.Kernel;
import souther.compiler.observe.Asserted;
import souther.compiler.observe.Expectation;
import souther.compiler.observe.FieldTypes;
import souther.compiler.observe.Limits;
import souther.compiler.observe.PathElement;
import souther.compiler.observe.Position;
import souther.compiler.observe.Verdict;
import souther.compiler.observe.ObservedValue;
import souther.compiler.observe.ValueTypes;
import souther.compiler.types.BindingId;
import souther.compiler.check.BoundaryInput;
import souther.compiler.check.BoundaryOutput;
import souther.compiler.types.LeafScalar;
import souther.compiler.types.Type;
import souther.compiler.jvm.ClassFileImage;
import souther.compiler.jvm.GeneratedClass;
import souther.compiler.jvm.SoutherJvmAbi;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.ValueName;
import souther.runtime.Sets;

import net.unit8.raoh.Err;
import net.unit8.raoh.Issues;
import net.unit8.raoh.Ok;
import net.unit8.raoh.Result;
import net.unit8.raoh.decode.Decoder;
import net.unit8.raoh.decode.ObjectDecoders;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads a written fixture as the value it states, against the type of the position it is written in.
 *
 * <p>One reading, and only one. A row's operand runs as compiled code and a {@code partial} helper
 * in it may not stop — so a reading is held to a budget, and a worker that runs out of it is asked
 * to stop and cannot be made to: a fixture reaches no interrupt point, so it may still be inside
 * {@link #expandedValue} holding a binding or a half-walked expansion. An instance is therefore one
 * row's, or one written statement's, and is dropped when that reading ends. Nothing it can still
 * write to — {@link #bindings}, {@link #expanding} — is read by the reading after it.
 *
 * <p>What a reading cannot build, and what did not finish, are a {@link FixtureException} and a
 * {@link StackExhaustedException}. Which diagnostic either becomes, and what a budget is, are not
 * here: this class has no {@code Diagnostic}, no place of its own, and no worker of its own. The
 * row evaluation ({@link ExampleVerifier}) and the reading of a module's written statements each say
 * what a failure means where they are, and they say different things about the same one.
 *
 * <p>Showing a value is here too, though it is not reading one. It needs the generated encoders, the
 * loader and the built value, and both readers above need it — left in either, the other would grow
 * a second copy.
 *
 * <p>Public for one thing only: {@link BoundaryValues}, which the adequacy report asks to find out
 * whether a value composed elsewhere builds at this module's boundary. That is this class's own
 * question — putting a value through the decoder a row's fixture goes through is the whole of the
 * answer — and it used to be asked of a row evaluator built with signatures, requirements and a
 * budget it had no rows to spend. Everything else here is package-private, and the constructor is:
 * a reading belongs to whichever of the two readers above started it.
 */
public final class FixtureReader {

    private final souther.compiler.check.Prepared.ForExamples module;
    private final Symbols symbols;
    /** What the declarations a fixture names say about themselves. */
    private final PublishedDeclarations published;
    /** The values a row may name: this module's own, and the ones its imports bring in. */
    private final Map<String, Hir.FnDef> values;
    private final MemoryClassLoader loader;
    /** Runs a helper a fixture applies. One reading's, because what it is running is one reading's. */
    private final OperandRunner operands;
    /** What a value looks like in the form a decoder reads — the rules both directions of a row read. */
    private final NeutralForm neutral;
    /** The same answer as the places a comparison reads a value's parts at. */
    private final ValueTypes types;

    FixtureReader(souther.compiler.check.Prepared.ForExamples module, Symbols symbols,
                  PublishedDeclarations published, DeclarationKinds kinds,
                  FieldTypes fields, Map<String, Hir.FnDef> values,
                  MemoryClassLoader loader) {
        this.module = module;
        this.symbols = symbols;
        this.published = published;
        this.values = values;
        this.loader = loader;
        this.operands = new OperandRunner(module.name(), loader);
        // What a declaration is laid out as reaches this reading through the two that read a value
        // by it, and is not held beside them: a reader here asks one of those, and one that asked
        // the declarations itself would be a third answer about what a value's parts are.
        this.neutral = new NeutralForm(symbols, published, kinds, fields);
        this.types = ValueTypes.over(fields);
    }

    /** A way to build values against this module's generated classes, without any rows to run. */
    public static BoundaryValues constructing(souther.compiler.check.Prepared.ForExamples module,
                                              Symbols symbols, PublishedDeclarations published,
                                            DeclarationKinds kinds, FieldTypes fields,
                                            Map<String, ClassFileImage> classes, ClassLoader parent,
                                            Map<String, Hir.FnDef> values) {
        // A reader is the whole of it. There are no rows, so nothing runs on a worker and no budget is
        // read; what a behavior takes and what stands in for what it depends on are not questions about
        // whether a value builds, so this does not ask for the answers to them.
        //
        // One reader per value asked about, because that is what a reading is here as anywhere: a
        // caller holds one of these and asks it about every candidate of every behavior it is
        // measuring, and a reader bound into it once would be a session spanning all of them. It is
        // the loader that is shared, and has to be — it caches the classes it has defined and loaded,
        // and a fake's subclass is generated once.
        MemoryClassLoader loader = new MemoryClassLoader(classes, parent);
        return (at, fixture) ->
                new FixtureReader(module, symbols, published, kinds, fields, values, loader)
                        .building(at, fixture);
    }

    /** The method emitted for {@code operand}, or null where nothing emitted one — read off the
     *  correspondence the module's preparation constructed, which this reader already holds. */
    private String emittedFor(Hir.Expr operand) {
        return module.operandMethods().get(operand);
    }

    /** The value the method emitted under {@code emittedAs} answers with. What a failure inside it
     *  is said of is the operand — the row's own account of itself — and not the method's name,
     *  which no source spells. */
    Object ran(String emittedAs) {
        try {
            return operands.run(emittedAs);
        } catch (InvocationFailure f) {
            // The row's reading of it, said of the operand rather than of the method it was emitted
            // as: what a report names is what the author wrote.
            throw RowFailures.of(f, "the value the row writes");
        }
    }

    Object built(Hir.Expr written, Type type) {
        String method = emittedFor(written);
        if (method != null) {
            return ran(method);
        }
        return built(written, FixtureShape.of(type, symbols));
    }

    /** The same at a position a behavior's boundary established, which carries its own admitted
     *  answer: it is read rather than the type it was admitted from being put through the walk
     *  again. */
    Object built(Hir.Expr written, BoundaryInput in) {
        String method = emittedFor(written);
        if (method != null) {
            return ran(method);
        }
        return built(written, FixtureShape.of(in));
    }

    /** The same, where the position's shape has already been settled. */
    Object built(Hir.Expr written, FixtureShape shape) {
        return built(written, shape, Admission.HELD);
    }

    /** The same, saying whether what is read is a value the model is given or one it is compared
     *  against. */
    private Object built(Hir.Expr written, FixtureShape shape, Admission admission) {
        return decode(shape, raw(written, Position.at(shape.type()), admission));
    }

    /**
     * A written fixture built into the whole value it states, and the case that value is.
     *
     * <p>What a stand-in installs. A fake row and a {@code with} both have to produce one of these to
     * stand in at all, so both are read this way and neither is read as an assertion.
     */
    record BuiltFixture(TypeSymbol caseName, Object value) {}
    /**
     * The whole value {@code written} states. Throws where it does not build or does not finish —
     * a fixture is one or the other, and what to make of that is the caller's.
     */
    BuiltFixture buildFixture(Hir.Expr fixture, BoundaryOutput out) {
        Hir.Expr written = fixture;
        Type outType = out.type();
        String method = emittedFor(fixture);
        if (method != null) {
            Object ran = ran(method);
            TypeSymbol answered = caseOfValue(ran, outType);
            return new BuiltFixture(answered != null ? answered : constructedCase(written), ran);
        }
        Object value = builtStandIn(written, out);
        TypeSymbol was = caseOfValue(value, outType);
        return new BuiltFixture(was != null ? was : constructedCase(written), value);
    }

    /**
     * The whole value {@code written} states, admitted at the output it stands in for.
     *
     * <p>Built the way an expected value is, and then held to what an expected value is not held to.
     * A row may expect a case the behavior does not answer with — that disagreement is what the row
     * reports — so {@link #assertedExpected} reads what the row wrote and leaves the rest to the
     * comparison. A stand-in is the dependency's answer while the row runs, so a value of another
     * type is not a disagreement to report but a fixture that cannot be built. Admitting it here is
     * what keeps the model from being handed a value it could not have been given.
     */
    private Object builtStandIn(Hir.Expr written, BoundaryOutput out) {
        FixtureShape whole = FixtureShape.ofWholeAnswer(out);
        TypeSymbol asserted = constructedCase(written);
        if (asserted != null) {
            // Through the case the row named, not the one the output declares: a row naming another
            // type builds that type and is refused for being it, rather than being read as the
            // output and refused for how it is written.
            return admitted(built(written, FixtureShape.of(Type.ref(asserted), symbols)), out, whole);
        }
        if (whole != null) {
            // Read as the output is written rather than as itself, through the decode a row's input
            // goes through — and held to it as an input is, since a stand-in is what the dependency
            // answers while the row runs. A number where the dependency answers with an `Amount`
            // states no answer it could have given.
            return built(written, whole);
        }
        // An answer of several types has no one decoder, so the value is read as written and what
        // admits it is which case it turned out to be.
        return admitted(raw(written, Position.UNREAD), out, null);
    }

    /**
     * {@code value} where the output it stands in for admits it, throwing where it does not.
     *
     * <p>Two rules, because an output is one of two things. Where the whole answer is one shape, a
     * value is admitted by being represented as that shape. Where it is several types, no one decoder
     * reads it and what admits a value is being one of the cases — the question the expected side
     * asks as E1904, asked of a value rather than of the name a row wrote.
     */
    private Object admitted(Object value, BoundaryOutput out, FixtureShape whole) {
        if (whole == null) {
            if (caseOfValue(value, out.type()) == null) {
                throw new FixtureException("a `" + nameOfBuilt(value) + "` is not one of the cases `"
                        + Type.show(out.type()) + "` answers with");
            }
            return value;
        }
        if (!holds(value, whole.type())) {
            throw new FixtureException("a `" + nameOfBuilt(value) + "` does not stand in for `"
                    + Type.show(out.type()) + "`");
        }
        return value;
    }

    /**
     * Whether {@code position} holds {@code value} as it stands at run time, at every depth.
     *
     * <p>The one reading of a built value, which the stand-in a row writes is put through: a scalar
     * is the class its values arrive in, a name is any of the leaves it holds — a sum has no class
     * of its own — an optional is absence or what it holds, and a collection of the right container
     * holds what its element type holds.
     *
     * <p>A position that says none of those holds anything. There is nothing here to refuse it with:
     * a type variable is decided by each call and a position with no declared type says nothing, and
     * both are answered where a fixture's shape is read.
     */
    private boolean holds(Object value, Type position) {
        return switch (position) {
            // Absence is what an optional makes room for; a value under one is held to what it holds,
            // and a `?` position takes the value itself as well as a `Some` around it.
            case Type.OptionOf o -> value == null || NeutralForm.isAbsent(value)
                    || holds(NeutralForm.heldBy(value), o.element());
            case Type.Prim p -> represents(TypeSymbol.primitive(p), value);
            case Type.Ref r when r.name().isPrimitive() -> represents(r.name(), value);
            case Type.Ref _, Type.Union _ -> caseOfValue(value, position) != null;
            case Type.ListOf l -> value instanceof List<?> els && each(els, l.element());
            case Type.SetOf s -> value instanceof Set<?> els && each(els, s.element());
            case Type.MapOf m -> value instanceof Map<?, ?> entries
                    && each(entries.keySet(), m.key()) && each(entries.values(), m.value());
            case null, default -> true;
        };
    }

    private boolean each(java.util.Collection<?> values, Type element) {
        for (Object value : values) {
            if (!holds(value, element)) {
                return false;
            }
        }
        return true;
    }

    /** What a built value is, named as the language names it, for a message about the type it is not.
     *  A generated class is its type, so the simple name is the name it was declared under. */
    private static String nameOfBuilt(Object value) {
        if (value == null) {
            return "missing value";
        }
        for (LeafScalar scalar : LeafScalar.values()) {
            if (represents(TypeSymbol.primitive(scalar.type()), value)) {
                return scalar.type().shown();
            }
        }
        return switch (value) {
            case Map<?, ?> _ -> "Map";
            case Set<?> _ -> "Set";
            case List<?> _ -> "List";
            default -> value.getClass().getSimpleName();
        };
    }

    /**
     * Which of the cases {@code outType} holds the built {@code value} is, or null where none of them
     * is what it turned out to be.
     *
     * <p>Matched against the cases the position declares rather than looked up by the name of the
     * class the value arrived in. A class name is not a case name — {@code Int} arrives as a
     * {@code Long} and {@code Date} as a {@code LocalDate}, so a lookup finds nothing and a primitive
     * case reads as no case at all — and resolving one in this module's scope would answer for a case
     * this module happens to declare under the same spelling rather than for the one the value is.
     */
    private TypeSymbol caseOfValue(Object value, Type outType) {
        if (value == null) {
            return null;
        }
        for (TypeSymbol candidate : AtomSpace.subjectAtoms(outType, published)) {
            if (represents(candidate, value)) {
                return candidate;
            }
        }
        return null;
    }

    /** Whether {@code value} is how {@code candidate} is represented at run time: the class a data
     * case is generated as, and for a primitive case the class its values arrive in. */
    private static boolean represents(TypeSymbol candidate, Object value) {
        if (value == null) {
            return false;   // nothing is how a type is represented; an absent value has its own reader
        }
        String carried = switch (candidate.name()) {
            case "Int" -> "java.lang.Long";
            case "String" -> "java.lang.String";
            case "Bool" -> "java.lang.Boolean";
            case "Decimal" -> "java.math.BigDecimal";
            case "Date" -> "java.time.LocalDate";
            case "Time" -> "java.time.LocalTime";
            case "DateTime" -> "java.time.LocalDateTime";
            case "Instant" -> "java.time.Instant";
            default -> null;
        };
        String is = value.getClass().getName();
        if (candidate instanceof TypeSymbol.Primitive) {
            return carried != null && carried.equals(is);
        }
        // What class a declaration is is the backend's answer and not the identity's spelling.
        // `Some` and `None` are named by no class at all, so no live value's class is theirs.
        return (candidate instanceof TypeSymbol.AtModule
                        || SoutherJvmAbi.providedByTheRuntime(candidate))
                && is.equals(SoutherJvmAbi.nameOf(new GeneratedClass.Value(candidate)).binaryName());
    }

    /**
     * The case a row asserts and nothing more: a bare name denoting a case — a unit case, or a case
     * written bare — where there is no value under it to compare. Null for anything else, including a
     * name denoting a value, which stands for the value it was defined as.
     *
     * <p>The case's own name, not the spelling: what came out carries the name its type was declared
     * under, so a row spelling that type qualified asserts the same case.
     */
    TypeSymbol caseOnly(Hir.Expr expected) {
        return expected instanceof Hir.Var.Denoting v
                && v.denotes() instanceof ValueName.OfType named
                ? named.type() : null;
    }

    // --- what a row asserts ---------------------------------------------------------------------

    /**
     * The value a row's expectation computed, and that value read as what the row asserts.
     *
     * <p>The two from one evaluation. What computes the expectation is the module's own code and it
     * is applied by running it, so asking for the value a second time would apply the helpers a
     * second time — counted twice against the row's budget, and a second time for whatever they do.
     * A reader that wants only the assertion reads {@link #asserted()} and pays for one run.
     *
     * @param live     the value as the run produced it, which is what a check is handed
     * @param asserted that value as the structure a row is compared by
     */
    record ExpectedValue(Object live, Asserted asserted) {}

    /**
     * What a row asserts, as the value it wrote rather than as a value of the position it wrote it at.
     *
     * <p>An input is a value the model is given, so it is read at its position and refused where it
     * states none. An expected value is not that: a row may state what the behavior does not answer
     * with, and reporting that disagreement is what the row is for. So this builds what the row wrote,
     * keeping the name every position was written under, and leaves the disagreement to
     * {@link ValueMatch}.
     *
     * <p>Three rules, and the first two are not one rule said twice. A construction has parts, and the
     * type each part is read at is the one that construction's own declaration gives it — a newtype's
     * argument is read at the base it wraps, a field at what its record declares that field to be. The
     * finished node is then a value of the type it was written under, and only there may a decoder run
     * over it. Reading a part at the whole's type is the same defect as reading it at the enclosing
     * position, one level in: {@code AmountN(1)} read with {@code AmountN} for its argument states that
     * a number is an {@code AmountN}, which is what was being removed.
     *
     * <ol>
     * <li>A part is built at the type its construction requires of it, never at the position the
     *     construction stands in.</li>
     * <li>A finished node may be decoded as its own written type, never as the position it stands
     *     in.</li>
     * <li>What a construction states of itself is the form's and not the position's: which collection
     *     a row wrote, that a map's keys are distinct, that a record has a value for every field it
     *     has. The position answers only what the form leaves open — {@code [ ]} is how a list and a
     *     set are both written, and nothing else about it is.</li>
     * <li>What renders a value reads the names and forms it wears. An encoder writes the
     *     representation a value crosses a boundary in, which is not that.</li>
     * </ol>
     *
     * <p>The third is the second read at the level of the whole rather than the part. A row's
     * expectation is what it wrote, and what it wrote is more than the names in it.
     *
     * <p>{@code position} is the type the enclosing value declares here, and what it may decide is
     * bounded by the above: it resolves what the written text does not say on its own — whether
     * {@code []} is a list, a set or a map — and it is never a type anything is read as. Together this
     * is what keeps {@code Amount("x")} a fixture that cannot be built while leaving
     * {@code Receipt { total = 1 }} a value of its own to disagree with what came out.
     */
    ExpectedValue assertedExpected(Hir.Expr expected, BoundaryOutput out) {
        // The value the row wrote, computed by the module's own code: the emitted method runs and
        // what it answers is read as what the row stated, exactly as a helper's answer for the
        // whole expectation is. Which collection a sequence is comes from the value — the position
        // contributed which one the brackets meant when the operand was compiled, and required
        // nothing of it.
        String method = emittedFor(expected);
        if (method != null) {
            Object live = ran(method);
            return new ExpectedValue(live, assertedLive(live));
        }
        // Every expectation that computes a value has a method — the correspondence is constructed
        // where the methods are emitted — and one written as a bare case name is read by caseOnly
        // before anything asks here. A miss is the correspondence broken, not a case to fall back on.
        throw new IllegalStateException("no method was emitted for the expected value at "
                + expected.pos());
    }

    /**
     * The type an application constructs, or null where what it applies is not a type.
     *
     * <p>Read off what the callee denotes, which resolution settled and every producer of a tree
     * carries (ADR-0067). Asked of the spelling instead, an import that let a name be written bare
     * missed the table, a module declaring its own type of the same spelling answered with that one,
     * and a row generated for an aliased type resolved to nothing at all (issue #696) — all three
     * silently, since a miss is what a table keyed by names does with a key it has not got.
     */
    private static TypeSymbol constructs(Hir.Apply c) {
        return c.answered() != null && c.answered().denotes() instanceof ValueName.OfType named
                ? named.type() : null;
    }

    /** Whether an application is a newtype's construction written in call form (ADR-0032). */
    private boolean constructsANewtype(Hir.Apply c) {
        TypeSymbol built = constructs(c);
        return built != null && neutral.isNewtype(built);
    }

    /**
     * Whether what a row wrote is a value of {@code type}, read off what the row wrote and nothing
     * else.
     *
     * <p>The twin of {@link #holds}, which asks it of a value that exists. This asks it of a written
     * one, so it is what a construction holds its own parts to before any of them is built into
     * anything. Shallow at a name, because what stands under that name is that value's own question
     * and not this one's — a {@code DecisionN(Approved { id = 1 })} whose {@code id} is written as a
     * number is a disagreement about the {@code id}, not a {@code DecisionN} that cannot be built.
     */
    private boolean states(Asserted a, Type type) {
        return switch (type) {
            // An optional holds a value or holds none, and a value written at one is written at what
            // it holds. Left out of this walk, everything under a `?` was admitted unasked.
            case Type.OptionOf o -> absent(a) || states(a, o.element());
            case Type.Prim p -> a instanceof Asserted.Value(ObservedValue v)
                    && spells(v, TypeSymbol.primitive(p));
            case Type.Ref r when r.name().isPrimitive() -> a instanceof Asserted.Value(ObservedValue v)
                    && spells(v, r.name());
            case Type.Ref _, Type.Union _ -> {
                TypeSymbol name = named(a);
                yield name != null && AtomSpace.subjectAtoms(type, published).contains(name)
                        && parts(a, name);
            }
            case Type.ListOf l -> a instanceof Asserted.Elements(Asserted.Container stated,
                    List<Asserted> elements)
                    && stated != Asserted.Container.SET && each(elements, l.element());
            case Type.SetOf s -> a instanceof Asserted.Elements(Asserted.Container stated,
                    List<Asserted> elements)
                    && stated != Asserted.Container.LIST && each(elements, s.element());
            case Type.MapOf m -> {
                if (!(a instanceof Asserted.Entries entries)) {
                    yield false;
                }
                for (Asserted.Entry e : entries.entries()) {
                    if (!states(e.key(), m.key()) || !states(e.value(), m.value())) {
                        yield false;
                    }
                }
                yield true;
            }
            // Nothing a fixture writes stands at one of these: a tuple is how a map's entries are
            // written rather than a type of its own, and a function is not a value a row states.
            case Type.TupleOf _, Type.FnOf _ -> false;
            // A position that says nothing says nothing about what may stand at it. A variable is
            // decided by each call, and the rest stand where a type could not be worked out at all —
            // holding a row to any of them would be holding it to something nothing wrote.
            case Type.Var _, Type.MetaVar _, Type.Nothing _, Type.Never _, Type.Erroneous _ -> true;
        };
    }

    private static boolean absent(Asserted a) {
        return a instanceof Asserted.Value(ObservedValue v) && v instanceof ObservedValue.Absent;
    }

    /**
     * The parts of a written construction, held to what that construction declares them to be.
     *
     * <p>Asked because this is what a construction takes, and what a construction takes is a value.
     * A row may write {@code Receipt { total = 1 }} as an expectation of its own — nothing is built
     * from it there, and the number it wrote at a named field is the disagreement it reports. Handed
     * to something that constructs, it is an argument, and there is no value of {@code Receipt} for
     * it to be.
     */
    private boolean parts(Asserted a, TypeSymbol name) {
        if (!(a instanceof Asserted.Built built)) {
            return true;   // a unit case carries nothing to hold
        }
        // A newtype's `value` is among these, since that is the field it is written with.
        for (Map.Entry<String, Type> f : neutral.fieldTypes(name).entrySet()) {
            Asserted field = built.fields().get(f.getKey());
            if (field != null && !states(field, f.getValue())) {
                return false;
            }
        }
        return true;
    }

    /** The name a written value wears, or null where it wears none. */
    private static TypeSymbol named(Asserted a) {
        return switch (a) {
            case Asserted.Built built -> built.type();
            case Asserted.Value(ObservedValue v) when v instanceof ObservedValue.Unit unit ->
                    unit.type();
            default -> null;
        };
    }

    /** Whether a written value with no parts is of {@code name}. Asked of what the row wrote, which
     *  is why it is not asked of a decoder: one reads a whole number where a `Decimal` stands, and a
     *  row writing `1` there wrote an `Int`. */
    private static boolean spells(ObservedValue v, TypeSymbol name) {
        return name.primitiveKind() != null && name.primitiveKind() == v.primitive();
    }

    private boolean each(List<Asserted> elements, Type element) {
        for (Asserted e : elements) {
            if (!states(e, element)) {
                return false;
            }
        }
        return true;
    }

    /**
     * What a row wrote, as the value it is, for asking whether two keys are one key.
     *
     * <p>The one thing this drops is which collection a sequence is, and a map's key cannot be one: a
     * key that crosses a boundary renders as and parses from a bare string, which no collection does.
     * So nothing a key can be loses anything here.
     */
    private static ObservedValue flattened(Asserted a) {
        return switch (a) {
            case Asserted.Value(ObservedValue v) -> v;
            case Asserted.Built built -> {
                Map<String, ObservedValue> fields = ObservedValue.fields();
                built.fields().forEach((name, held) -> fields.put(name, flattened(held)));
                yield new ObservedValue.Constructed(built.type(), fields);
            }
            case Asserted.Elements elements -> {
                List<ObservedValue> out = new ArrayList<>();
                for (Asserted e : elements.elements()) {
                    out.add(flattened(e));
                }
                yield new ObservedValue.Sequence(out);
            }
            case Asserted.Entries entries -> {
                List<ObservedValue.Entry> out = new ArrayList<>();
                for (Asserted.Entry e : entries.entries()) {
                    out.add(new ObservedValue.Entry(flattened(e.key()), flattened(e.value())));
                }
                yield new ObservedValue.Mapping(out);
            }
        };
    }

    /**
     * A value a helper produced, read as what a row stated by writing that application.
     *
     * <p>An {@link ObservedValue} would do for everything but one thing: which collection a sequence
     * is. That is left out there because the type behind an observation says it, and here the type
     * behind it is the helper's rather than the position's — a helper answering with a {@code Set} has
     * not stated the {@code List} the position holds. The value itself says which it is, so it is read
     * from the value.
     */
    private Asserted assertedLive(Object live) {
        if (live == null) {
            return new Asserted.Value(new ObservedValue.Unknown("a null reached the reader"));
        }
        String name = NeutralForm.simpleName(live);
        if (name.equals("Option$None")) {
            return new Asserted.Value(new ObservedValue.Absent());
        }
        if (name.equals("Option$Some")) {
            return assertedLive(ObservedValues.readOrNull(live, "value"));
        }
        if (live instanceof Map<?, ?> entries) {
            List<Asserted.Entry> out = new ArrayList<>();
            for (Map.Entry<?, ?> e : entries.entrySet()) {
                out.add(new Asserted.Entry(assertedLive(e.getKey()), assertedLive(e.getValue())));
            }
            return new Asserted.Entries(true, out);
        }
        if (live instanceof Iterable<?> elements) {
            List<Asserted> out = new ArrayList<>();
            for (Object e : elements) {
                out.add(assertedLive(e));
            }
            return new Asserted.Elements(live instanceof Set<?> ? Asserted.Container.SET
                    : Asserted.Container.LIST, out);
        }
        // The type the value is, and not this module's reading of its spelling: a helper a fixture
        // applies may be one another module published, and what it answered with is that module's
        // type however this module spells the same name.
        TypeSymbol type = typeOf(live);
        if (type != null && symbols.declaredNode(type) instanceof Hir.Data) {
            Map<String, Asserted> fields = new LinkedHashMap<>();
            // A newtype's `value` is among these, since that is the field it is written with.
            for (String each : neutral.fieldTypes(type).keySet()) {
                fields.put(each, assertedLive(ObservedValues.readOrNull(live, each)));
            }
            return new Asserted.Built(type, fields);
        }
        return new Asserted.Value(structured(live));
    }

    /** The arm an expected names, as it was written — what a row that names no case of the target is
     * told it wrote. Which case it stands for is {@link #constructedCase}. */
    String expectedArm(Hir.Expr expected) {
        if (expected instanceof Hir.Var v) {
            return v.name();
        }
        if (expected instanceof Hir.NewData nd) {
            return nd.typeName().written();
        }
        if (expected instanceof Hir.Apply c && constructsANewtype(c)) {
            return c.written();
        }
        return null;   // a literal expected (a primitive output)
    }

    /**
     * The case a fixture stands for: the one a construction names, and for a name, the one the value it
     * denotes constructs.
     *
     * <p>Read through the value rather than off the spelling. A value's name is not a case name, and
     * where the position is a union written inline there is no decoder to dispatch on a tag — so the
     * value is the only thing that says which case it is, and reading the name found a type of that
     * spelling or nothing (issue #206).
     */
    TypeSymbol constructedCase(Hir.Expr e) {
        return constructedCase(e, new LinkedHashSet<>(), List.of());
    }

    /**
     * The case a fixture supplies at a position whose values are written under {@code worn}: the same
     * reading, with those names taken off first.
     *
     * <p>Which case constructed the value and which case the position it is written at sees are two
     * questions, and only one of them is the one above. A {@code data DecisionN = Decision} is the sum
     * it names; a row there writes {@code DecisionN(Approved { id = 1 })}, which constructs a
     * {@code DecisionN} and supplies an {@code Approved}. Only the second is a case of the position,
     * so only the second may be counted at it.
     *
     * <p>The other direction of what {@link souther.compiler.partition.Classifier#inside} does to an
     * observation, and it takes the names off on the same terms: only the ones that are there come
     * off, so a fixture not wearing them is read as it stands rather than decided to be nothing.
     *
     * @param worn the names the position writes its values under, outermost first, as
     *             {@link souther.compiler.check.TypeView} reads them off it
     */
    TypeSymbol caseUnder(List<TypeSymbol> worn, Hir.Expr e) {
        return constructedCase(e, new LinkedHashSet<>(), worn);
    }

    /**
     * As above; {@code followed} are the names already followed, so a value defined in terms of itself
     * stops here and is reported as the cycle it is where the fixture is built.
     *
     * <p>The binding a closed body carries is not read here, and does not need to be: a value is
     * substituted where it is named, so the only name closing leaves standing in a fixture is the one a
     * spread holds — a spread cannot hold an expression — and that name is read where the spread is
     * copied. A closed body therefore ends in the construction, whether it was published itself or
     * named by another value that was.
     *
     * <p>{@code worn} are the names still to come off, which is what tells this reading from the
     * nominal one. They come off where the fixture is the construction wearing them and nowhere else —
     * a name is followed to what it stands for with the same names still to take off, since where the
     * fixture is written says nothing about which of them a published value already carries.
     */
    private TypeSymbol constructedCase(Hir.Expr e, Set<String> followed, List<TypeSymbol> worn) {
        return switch (e) {
            case Hir.NewData nd -> nd.typeName().answered() == null
                    ? null : nd.typeName().answered().type();
            case Hir.Apply c when constructsANewtype(c) -> {
                TypeSymbol named = constructs(c);
                yield wears(named, c, worn)
                        ? constructedCase(c.args().get(0), followed, worn.subList(1, worn.size()))
                        : named;
            }
            case Hir.LetIn let -> constructedCase(let.body(), followed, worn);
            case Hir.Var v -> namedCase(v, followed, worn);
            case null, default -> null;
        };
    }

    /** Whether {@code c} is the outermost name still to come off, holding the one value under it. */
    private static boolean wears(TypeSymbol named, Hir.Apply c, List<TypeSymbol> worn) {
        return !worn.isEmpty() && worn.get(0).equals(named) && c.args().size() == 1;
    }

    /** The case a bare name stands for: the type it denotes where it denotes one — a unit case, or a
     * case written bare — and otherwise the case the value or binding it names constructs. */
    private TypeSymbol namedCase(Hir.Var name, Set<String> followed, List<TypeSymbol> worn) {
        if (!(name instanceof Hir.Var.Denoting v)) {
            // it names nothing, so it stands for no case; reported where it is written
            return null;
        }
        return switch (v.denotes()) {
            case ValueName.OfType named -> named.type();
            case ValueName.Local local -> {
                Hir.Expr held = bindings.get(local.id());
                yield held == null ? null : constructedCase(held, followed, worn);
            }
            case ValueName.Helper _ -> {
                Hir.Expr body = followed.add(v.name()) ? valueBody(v.name()) : null;
                yield body == null ? null : constructedCase(body, followed, worn);
            }
            case null, default -> null;
        };
    }

    /** What actually came out, in the same notation: the case name plus the value the derived encoder
     * writes, so the two sides of a mismatch can be read against each other. A value with no encoder
     * (or one that fails to encode) falls back to its case name alone. */
    String describeActual(Object result) {
        String name = NeutralForm.simpleName(result);
        if (name.isEmpty()) {
            return String.valueOf(result);
        }
        if (result instanceof Iterable<?> || result instanceof Map<?, ?>) {
            return showAny(result);
        }
        Object encoded = encodedOrNull(result, typeOf(result));
        if (encoded != null) {
            return show(name, encoded);
        }
        return NeutralForm.isScalar(result) ? showValue(result) : name;
    }

    /** A live value in the notation a fixture is written in, at any depth: a collection element by
     * element, a data as its case name with fields, a scalar as written. Both sides of a collection
     * mismatch go through this, so they can be read against each other — and neither shows the JDK
     * class that happened to carry the collection. */
    private String showAny(Object v) {
        if (v == null || NeutralForm.isScalar(v)) {
            return showValue(v);
        }
        if (v instanceof Map<?, ?> m) {
            List<String> entries = new ArrayList<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                entries.add("(" + showAny(e.getKey()) + ", " + showAny(e.getValue()) + ")");
            }
            return entries.isEmpty() ? "[]" : "[ " + String.join(", ", entries) + " ]";
        }
        if (v instanceof Iterable<?> it) {
            List<String> elements = new ArrayList<>();
            for (Object e : it) {
                elements.add(showAny(e));
            }
            return elements.isEmpty() ? "[]" : "[ " + String.join(", ", elements) + " ]";
        }
        String name = NeutralForm.simpleName(v);
        Object encoded = encodedOrNull(v, typeOf(v));
        return encoded != null ? show(name, encoded) : name;
    }

    /**
     * The generated {@code decoder()} / {@code encoder()} of a type, reached whether or not the type
     * is exposed. {@code exposing} decides what leaves the module, and an {@code example} does not
     * leave it: the fixture is built and the result read back inside the module being compiled, so a
     * module-local type has to be readable here. Its generated class is package-private, which
     * {@link Class#getMethod} cannot reach from this classloader, so the declared method is taken and
     * opened.
     */
    private static Object staticCodec(Class<?> c, String name) throws ReflectiveOperationException {
        java.lang.reflect.Method m = c.getDeclaredMethod(name);
        m.setAccessible(true);
        return m.invoke(null);
    }

    /** {@code result} through the derived {@code encoder()} of the type it is, or null where the type
     * is not one a module declares and so has no derived codec to reach. The type names the class
     * whether or not the reader spells it the way its module does. */
    private Object encodedOrNull(Object result, TypeSymbol type) {
        return type == null ? null : encoded(result, SoutherJvmAbi.nameOf(new GeneratedClass.Value(type)).binaryName());
    }

    private Object encoded(Object result, String className) {
        try {
            Class<?> c = loader.loadClass(className);
            Object encoder = staticCodec(c, "encoder");
            return net.unit8.raoh.encode.Encoder.class.getMethod("encode", Object.class)
                    .invoke(encoder, result);
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (souther.compiler.evaluate.EvaluationContext.overspending(e)) {
                throw (RuntimeException) e;   // the evaluation ran out, not this value
            }
            return null;
        }
    }

    /** A case name with its neutral value: a record's fields in braces, a newtype's value in parens,
     * a unit case as the bare name. */
    private String show(String name, Object neutral) {
        if (neutral instanceof Map<?, ?> fields) {
            if (fields.isEmpty()) {
                return name;
            }
            StringBuilder sb = new StringBuilder(name).append(" { ");
            boolean first = true;
            for (Map.Entry<?, ?> e : fields.entrySet()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append(e.getKey()).append(" = ").append(showValue(e.getValue()));
            }
            return sb.append(" }").toString();
        }
        return name + "(" + showValue(neutral) + ")";
    }

    /** A neutral value in the notation a fixture is written in: a quoted string, a list in brackets,
     * a map as its list of pairs, a date as {@code Date("...")}. */
    private String showValue(Object v) {
        if (v == null) {
            return "None";
        }
        if (v instanceof String s) {
            return "\"" + s + "\"";
        }
        // Written by whatever writes the value everywhere else, and not by `toString`. A time of
        // day and a date-time drop their seconds at zero that way, so what came back was shown
        // `Time("16:00")` beside a line the same value named `Time("16:00:00")` — one value in two
        // spellings, in the one report where a reader holds them up against each other.
        String temporal = switch (v) {
            case java.time.LocalDate at -> "Date(\"" + at + "\")";
            case java.time.LocalTime at ->
                    "Time(\"" + souther.compiler.numeric.Times.written(at) + "\")";
            case java.time.LocalDateTime at ->
                    "DateTime(\"" + souther.compiler.numeric.DateTimes.written(at) + "\")";
            case java.time.Instant at -> "Instant(\"" + at + "\")";
            default -> null;
        };
        if (temporal != null) {
            return temporal;
        }
        if (v instanceof Map<?, ?> m) {
            List<String> entries = new ArrayList<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                entries.add("(" + showValue(e.getKey()) + ", " + showValue(e.getValue()) + ")");
            }
            return entries.isEmpty() ? "[]" : "[ " + String.join(", ", entries) + " ]";
        }
        if (v instanceof Iterable<?> it) {
            List<String> elements = new ArrayList<>();
            for (Object e : it) {
                elements.add(showValue(e));
            }
            return elements.isEmpty() ? "[]" : "[ " + String.join(", ", elements) + " ]";
        }
        return String.valueOf(v);
    }

    // --- raw evaluation of a fixture expression -----------------------------------------------

    /**
     * The neutral form of a written fixture value — a literal to a boxed value, a newtype
     * construction to its inner value, a record construction to a field map.
     *
     * <p>{@code at} is what reads the value where it is written: a bare case name travels differently
     * depending on the sum it is read as, and only the place it stands says which sum that is. Where
     * nothing reads it ({@link Position#UNREAD}) nothing is written beside the case, which is the
     * case's own form.
     */
    private Object raw(Hir.Expr e, Position at) {
        return raw(e, at, Admission.HELD);
    }

    /**
     * Whether what a frame reads is held to the position it is read at.
     *
     * <p>An input, an argument and a stand-in are values the model is given, so what stands at such a
     * position either is a value of it or is one the position could not have been given. An expected
     * value is neither: a row may state what the behavior does not answer with, and reporting that
     * disagreement is what the row is for — so it is compared, and comparing is not admitting.
     *
     * <p>A spread is a frame of its own kind. {@code Filed { ...d, filedOn = on }} copies a
     * {@code Document}'s fields, which is how the language writes one record from another, so that
     * frame states no value of the construction's type while every position under it states one as
     * any other does.
     */
    private enum Admission {
        /** This frame states a value at its position, and every position under it does. */
        HELD,
        /** This frame supplies fields rather than a value of its position; the positions under it are
         *  {@link #HELD}. */
        HELD_BELOW,
        /** Nothing this reading reaches is admitted, at any depth. */
        UNHELD
    }

    /** What the positions inside this frame are read under: a spread's exemption is this frame's, and
     *  a reading that admits nothing goes on admitting nothing. */
    private static Admission below(Admission admission) {
        return admission == Admission.UNHELD ? Admission.UNHELD : Admission.HELD;
    }

    /** As above, saying whether this frame is one that states a value at {@code at}. */
    private Object raw(Hir.Expr e, Position at, Admission admission) {
        if (admission == Admission.HELD) {
            admitWritten(e, at);
        }
        Admission inside = below(admission);
        return switch (e) {
            case Hir.IntLit i -> i.value();
            case Hir.DecimalLit d -> d.value();
            case Hir.StringLit s -> text(s, at);
            case Hir.BoolLit b -> b.value();
            // An operand of an arithmetic fold is a literal, so nothing reads it as anything.
            case Hir.Neg n -> negate(raw(n.operand(), Position.UNREAD));
            case Hir.Binary bin -> fold(bin);
            // The three that go on at this same position, so what this frame is read under travels
            // with them: a name stands for a body, a `let` for its own body, and an application for
            // the value it answers with.
            case Hir.Apply c -> collectionOrNewtype(c, at, admission);
            case Hir.Var v -> named(v, at, admission);
            case Hir.LetIn let -> bound(let, at, admission);
            case Hir.NewData nd -> record(nd, at, admission);
            case Hir.ListLit l -> {
                Position element = at.element();
                List<Object> out = new ArrayList<>();
                for (Hir.Expr el : l.elements()) {
                    out.add(raw(el, element, inside));
                }
                yield out;
            }
            // a `(key, value)` pair: only a `Map` field's entries are written this way, and `shaped`
            // collects them into the map the decoder reads (a tuple is not a data field type itself).
            case Hir.Tuple t -> {
                List<Position> parts = at.parts(t.elements().size());
                List<Object> out = new ArrayList<>();
                for (int i = 0; i < t.elements().size(); i++) {
                    out.add(raw(t.elements().get(i), parts.get(i), inside));
                }
                yield out;
            }
            // A field taken off another value is one of the forms this does not read, and it is
            // refused here as they all are. Reading it would be a second reading of what a `.`
            // names, answering beside the one the language is checked by: a row's operand is
            // compiled and run, so the field a row takes is typed by the elaboration like any
            // other, and what reaches here instead is a value named at a boundary — which stands
            // for whatever the module wrote, up to and including this.
            default -> throw new FixtureException("an example fixture must be a literal or a construction");
        };
    }

    // --- the name a position is written under ---------------------------------------------------

    /**
     * What a position takes of the name a value written at it is written under.
     *
     * <p>Three answers and not two. A nominal position takes one of the names it holds; a primitive
     * or a collection takes a value wearing no name at all, which is as much a rule as the first —
     * a {@code data AmountN = Int} written at an {@code Int} is an {@code AmountN} whatever its
     * representation reads as. Where the position is not declared, or is a kind a fixture is refused
     * at before this ({@link FixtureShape}), nothing here is asked.
     *
     * <p>An optional is one of these and not a reading around them: writing a value for a {@code T?}
     * position writes a {@code T}, and absence is written {@code None}, which stands where an
     * optional makes room for it and nowhere else. Opening the position instead loses the room it
     * made, and {@code None} written at a unit case decoded as that case — a decoder that reads
     * nothing reads a missing value as well as any other.
     */
    private sealed interface Admits {

        /** One of these names, and no other, and not a value wearing none. */
        record OneOf(Set<TypeSymbol> names) implements Admits {}

        /** No name: a value written under one is a value of that name and not of this position. */
        record NoName() implements Admits {}

        /** Nothing is said about the position, so nothing is asked of what stands at it. */
        record Unsaid() implements Admits {}

        /** Absence, or what the optional holds. */
        record OrAbsent(Admits held) implements Admits {}
    }

    private static final Admits NO_NAME = new Admits.NoName();
    private static final Admits UNSAID = new Admits.Unsaid();

    /**
     * What {@code position} takes.
     *
     * <p>{@link AtomSpace#subjectAtoms} answers the nominal side rather than a reading of its own: it
     * expands a sum to its cases and stops at everything else, so a record takes itself, a sum takes
     * the cases it lists and a newtype takes its own name, with no arm here for any of the three.
     *
     * <p>A union is {@code Unsaid} because a fixture never reaches one: a position of several types
     * is refused where its shape is read, saying to write the case it is. Answering for it here would
     * be a second rule about a position this cannot be asked about.
     */
    private Admits admits(Type position) {
        return switch (position) {
            case Type.OptionOf o -> new Admits.OrAbsent(admits(o.element()));
            case Type.Ref r when r.name().isPrimitive() -> NO_NAME;
            case Type.Ref r -> new Admits.OneOf(
                    new LinkedHashSet<>(AtomSpace.subjectAtoms(Type.ref(r.name()), published)));
            case Type.Prim _, Type.ListOf _, Type.SetOf _, Type.MapOf _, Type.TupleOf _ -> NO_NAME;
            case null, default -> UNSAID;
        };
    }

    /** What the text in a frame says about the name of the value it supplies. */
    private sealed interface Stated {

        /** A construction, or a name denoting a case: this name, said here. */
        record Name(TypeSymbol name) implements Stated {}

        /** A value under no name: a literal, a written collection, a temporal, an arithmetic fold. */
        record NoName() implements Stated {}

        /** Absence, which the optional a position holds takes and which names no type. */
        record Absence() implements Stated {}

        /** Said by the frame this opens, or by the value an application answers with, or not the
         *  question at all — a call that is no construction is refused for being one. */
        record Elsewhere() implements Stated {}
    }

    private static final Stated STATES_NO_NAME = new Stated.NoName();
    private static final Stated ABSENCE = new Stated.Absence();
    private static final Stated ELSEWHERE = new Stated.Elsewhere();

    /**
     * Holds what is written in this frame to what its position takes.
     *
     * <p>A derived decoder reads the form its base reads and returns a value of its own type, so the
     * name a position is written under is one the decoder supplies: another name over the same base,
     * and no name at all, reach it as the one form. What holds the row to that name is here, before
     * anything reaches a decoder, and at every frame rather than at the outermost one — a field, an
     * element and a helper's argument are each a position a value is written at.
     */
    private void admitWritten(Hir.Expr e, Position at) {
        // Nothing says what stands here, so nothing is asked of what does — the answer `admits` gives
        // for every other position it has no reading of.
        if (!(at instanceof Position.At(Type expected))) {
            return;
        }
        Admits admits = admits(expected);
        Admits held = admits instanceof Admits.OrAbsent(Admits under) ? under : admits;
        if (held instanceof Admits.Unsaid) {
            return;
        }
        switch (states(e)) {
            case Stated.Elsewhere _ -> { }
            case Stated.Absence _ -> {
                if (!(admits instanceof Admits.OrAbsent)) {
                    throw new FixtureException("`None` is the absent value of a `?` position, and this"
                            + " position holds a value of `" + Type.show(expected) + "`");
                }
            }
            case Stated.Name(TypeSymbol named) -> {
                if (!(held instanceof Admits.OneOf(Set<TypeSymbol> names) && names.contains(named))) {
                    throw wrongName(named, held, expected);
                }
            }
            case Stated.NoName _ -> {
                if (held instanceof Admits.OneOf(Set<TypeSymbol> names)) {
                    throw noName(names, expected);
                }
            }
        }
    }

    /**
     * What the written form says here.
     *
     * <p>Read off the text and never by running anything: an application answers {@code Elsewhere},
     * so the helper is run once, where it is read, and the value it answered with is held there.
     *
     * <p>A name that is not a value, and a call that is no construction, answer {@code Elsewhere}
     * too. Each is refused where it is read, for being what it is, and a rule about names would
     * otherwise reach them first and tell a row that named a behavior about a type.
     */
    private Stated states(Hir.Expr e) {
        return switch (e) {
            case Hir.LetIn _ -> ELSEWHERE;
            case Hir.NewData nd when nd.typeName().answered() != null ->
                    new Stated.Name(nd.typeName().answered().type());
            case Hir.Var v -> statedByName(v);
            case Hir.Apply c when constructsANewtype(c) -> caseNamed(constructs(c));
            // `Date("…")` is a temporal, and `Set.fromList([…])` a collection: values under no name.
            // Whether the first of those builds anything is {@link ValueName.Stdlib#constructs},
            // which is the one place that says so — and it is what this asks, so a module's own
            // behavior spelled like a namespace is not read as one, and a namespace that builds
            // nothing (`List`, `Option`) is the call it is and is refused where it is read.
            case Hir.Apply c when c.answered() != null
                    && ((c.answered().denotes() instanceof ValueName.Stdlib library
                                    && library.constructs() != null)
                            || isFromList(c)) ->
                    STATES_NO_NAME;
            case Hir.Apply _ -> ELSEWHERE;
            // A field taken off another value is a form this reading refuses, and it is refused
            // where it is read. Answered as a value under no name, the rule about names would reach
            // it first and tell whoever wrote it about the name a position takes, which is not what
            // is wrong with it.
            case Hir.FieldAccess _ -> ELSEWHERE;
            case null, default -> STATES_NO_NAME;
        };
    }

    /** Which declaration a live value is — {@link NeutralForm#typeOf}, which every reader of a run
     *  asks, and {@link #represents} is the same discipline the other way about. */
    TypeSymbol typeOf(Object value) {
        return neutral.typeOf(value);
    }

    /** A name resolved to a case, or a value under no name where this module has no such case — the
     *  reading that follows says what it could not build. */
    private static Stated caseNamed(TypeSymbol resolved) {
        return resolved == null ? STATES_NO_NAME : new Stated.Name(resolved);
    }

    private Stated statedByName(Hir.Var name) {
        if (!(name instanceof Hir.Var.Denoting v)) {
            // it names nothing, and the reading that follows says so where it is written
            return ELSEWHERE;
        }
        return switch (v.denotes()) {
            case ValueName.OfType of -> new Stated.Name(of.type());
            case ValueName.Builtin b when b.name().equals("None") -> ABSENCE;
            // `Map.empty` / `Set.empty`: the empty collection, under no name.
            case ValueName.Stdlib.Operation lib
                    when symbols.library().isEmptyCollectionValue(lib) ->
                    STATES_NO_NAME;
            // A binding and a value open a frame at this same position; anything else is not a value
            // a fixture can name, which the reading says of it.
            case null, default -> ELSEWHERE;
        };
    }

    private FixtureException wrongName(TypeSymbol supplied, Admits admits, Type expected) {
        String at = Type.show(NeutralForm.open(expected));
        return new FixtureException("`" + supplied.name() + "` is written where a value of `" + at
                + "` stands; " + (admits instanceof Admits.OneOf(Set<TypeSymbol> names)
                        ? "one is written " + writtenAs(names)
                        : "a value of `" + at + "` wears no name"));
    }

    private FixtureException noName(Set<TypeSymbol> admits, Type expected) {
        return new FixtureException("nothing here names a value of `" + Type.show(NeutralForm.open(expected))
                + "`; one is written " + writtenAs(admits));
    }

    /** How a value of an admitted name is written, which is the declaration's own form and not a rule
     *  of this reading: a newtype takes its value in parens, a record its fields in braces, a unit
     *  case is the bare name. */
    private String writtenAs(Set<TypeSymbol> admits) {
        List<String> forms = new ArrayList<>();
        for (TypeSymbol name : admits) {
            forms.add(neutral.isNewtype(name) ? "`" + name.name() + "(...)`"
                    : symbols.declaredNode(name) instanceof Hir.Data ? "`" + name.name() + " { ... }`"
                    : "`" + name.name() + "`");
        }
        return admits.size() == 1 ? forms.get(0) : "as one of " + String.join(", ", forms);
    }

    /**
     * A written string, which is a {@code String} — and at a position that declares a temporal, is
     * not one of those.
     *
     * <p>Which positions those are is {@link NeutralForm#temporalUnder}, which the reading of a value
     * a helper returned asks too: a row reaches the neutral form both ways, and the rule is about the
     * form.
     */
    private Object text(Hir.StringLit s, Position at) {
        if (neutral.temporalUnder(at) instanceof Type.Prim temporal) {
            throw NeutralForm.notWrittenAsATemporal(temporal, s.value());
        }
        return s.value();
    }

    /**
     * A bare name in a fixture, read as what it denotes.
     *
     * <p>Which of the four it is was settled where the name was written, so it is not worked out
     * again here: a binding in force is the value it holds, whatever else bears its spelling.
     */
    private Object named(Hir.Var name, Position at, Admission admission) {
        if (!(name instanceof Hir.Var.Denoting v)) {
            throw new FixtureException("`" + name.name() + "` is not a value a fixture can name");
        }
        return switch (v.denotes()) {
            // `None` maps to a null, which the optional decoder reads as the absent optional
            // (spec §absence-is-written-as-null, absent/null -> None), the same as omitting a `T?` field
            case ValueName.Builtin b when b.name().equals("None") -> null;
            case ValueName.OfType named
                    when symbols.declaredNode(named.type()) instanceof Hir.UnitData ->
                    unitInput(named.type(), at);
            case ValueName.Local local -> {
                Hir.Expr held = bindings.get(local.id());
                if (held == null) {
                    throw new FixtureException("`" + v.name()
                            + "` is bound to no value a fixture can name");
                }
                yield expandedValue(local, held, at, admission);
            }
            case ValueName.Helper helper -> {
                Hir.Expr value = valueBody(v.name());
                if (value == null) {
                    throw new FixtureException("`" + v.name() + "` is not a value a fixture can name");
                }
                yield expandedValue(helper, value, at, admission);
            }
            // `Map.empty` / `Set.empty`: a library value, not a library call, so there is no method to
            // run and its value is known from the name alone. It is the empty collection, which a row
            // writes `[]` — admitted for the reason `fromList` is (see `collectionOrNewtype`), so a
            // body and a row spell an empty map the one way.
            case ValueName.Stdlib.Operation lib
                    when symbols.library().isEmptyCollectionValue(lib) ->
                    new ArrayList<>();
            case null, default ->
                    throw new FixtureException("`" + v.name() + "` is not a value a fixture can name");
        };
    }

    /** A unit case as a fixture writes it: a unit's decoder ignores the input, so an empty map
     * stands in. */
    private Object unitInput(TypeSymbol caseName, Position at) {
        {
            // A fixture is built in the neutral form the boundary reads, so a case of an enumeration
            // is written the way that sum travels: its name, bare (issue #161). The same unit data may
            // be a case of an enumeration and of a sum that has a field-bearing case, and those travel
            // differently — so the position's own type decides, and the case's sums answer only where
            // the position does not say.
            if (caseName != null && neutral.readsABareName(at)) {
                return caseName.name();
            }
            Map<String, Object> unit = new LinkedHashMap<>();
            // a unit case read through a sum still needs the tag that sum's decoder reads
            neutral.tagged(at, caseName, unit);
            return unit;
        }
    }

    /**
     * A name bound while this fixture is being built, holding what it was bound to.
     *
     * <p>A spread holds a name rather than an expression, so a published body that spreads one of its
     * module's values arrives with that value bound ahead of the construction — the shape a spread of a
     * local already has. That binding is what the spread then names, so a fixture reads one the way it
     * reads a value: the position says what type to read it as.
     */
    private final Map<BindingId, Hir.Expr> bindings = new LinkedHashMap<>();

    /** A {@code let} inside a fixture: its name stands for what it was bound to while the body is
     * built, and a binding of the same spelling that was already in force is put back afterwards. */
    private Object bound(Hir.LetIn let, Position at, Admission admission) {
        BindingId binding = let.binder().id();
        bindings.put(binding, let.value());
        try {
            return raw(let.body(), at, admission);
        } finally {
            // a binding of its own, so there is nothing of an outer one to put back
            bindings.remove(binding);
        }
    }

    /**
     * What a name stands for: a binding in force, or the body a value — a {@code let} with no parameter
     * list — was defined as. Null where the name is neither.
     *
     * <p>Read from {@code values}, and from nothing else. That table is the
     * settled representation this whole reading is written against, and what it does not hold is not
     * a value a fixture may read. The written module is not a second way to the same answer: it
     * holds every definition, a behavior's implementation among them, and a behavior taking nothing
     * is written the way a value is — so a spread, which asks here for any name it does not bind,
     * used to copy the fields of one.
     *
     * <p>Whether a fixture may name it is not a property of this one body: a value stands for a
     * fixture when it is a literal, a construction, a spread, a {@code fromList} over one, or a name
     * of another such value. So the body is read the same way the row's own text is, and a chain of
     * values holds.
     */
    private Hir.Expr valueBody(String name) {
        Hir.FnDef value = values.get(name);
        return value != null && value.params().isEmpty()
                && value.body() instanceof Hir.FnBody.Written w ? w.expr() : null;
    }

    /**
     * What is being expanded, innermost last — a value that reaches itself has no fixture to be.
     *
     * <p>Held as what each one is rather than as what it is called: two bindings of one spelling are
     * two values, and reading the second while the first is open is not a value reaching itself. The
     * spelling is what the report shows, and decides nothing.
     */
    private final Deque<ValueName> expanding = new ArrayDeque<>();

    /**
     * The body a name stands for, read where the name is written — with the name held open while it
     * is, so a value defined in terms of itself is reported as the cycle it is rather than read
     * until the stack runs out.
     */
    private Object expandedValue(ValueName named, Hir.Expr body, Position at,
                                 Admission admission) {
        if (expanding.contains(named)) {
            List<String> cycle = new ArrayList<>();
            expanding.forEach(open -> cycle.add(open.name()));
            cycle.add(named.name());
            throw new FixtureException("`" + named.name() + "` is defined in terms of itself ("
                    + String.join(" -> ", cycle) + ")");
        }
        expanding.addLast(named);
        try {
            return raw(body, at, admission);
        } finally {
            expanding.removeLast();
        }
    }

    /**
     * {@code Set.fromList([…])} and {@code Map.fromList([…])} are the forms a fixture's own notation
     * stands for — a set is written as its elements and a map as its entry pairs — so the neutral
     * value is the argument's. A value has to be ordinary code, where a list literal is a
     * {@code List} whatever the position declares, so this is what lets one record serve as both a
     * value and a fixture. Anything else applied here is a newtype or nothing.
     *
     * <p>The empty collection is admitted for the same reason, but it is named rather than applied
     * ({@code Map.empty}), so it is read in {@link #named}.
     */
    private Object collectionOrNewtype(Hir.Apply c, Position at, Admission admission) {
        if (isFromList(c)) {
            if (c.args().size() != 1) {
                throw new FixtureException("`" + c.written() + "` takes one argument");
            }
            return raw(c.args().get(0), at, admission);
        }
        return newtypeInner(c, at, admission);
    }

    /** Whether {@code c} is the collection notation a fixture writes as its elements — asked of what
     *  the callee reaches, as every other question about which operation a call applies is.
     *
     *  <p>Which two operations those are is the library's to say, so this asks which kernel the
     *  operation is declared to be rather than writing out what the library publishes it as. The
     *  alias is the library's own, and a reader that spelt one was right for as long as the two
     *  agreed. */
    private boolean isFromList(Hir.Apply c) {
        if (c.answered() == null
                || !(c.answered().denotes() instanceof ValueName.Stdlib.Operation operation)) {
            return false;
        }
        Kernel kernel = symbols.kernelOf(operation);
        return kernel == Kernel.SET_FROM_LIST || kernel == Kernel.MAP_FROM_LIST;
    }

    private Object newtypeInner(Hir.Apply c, Position at, Admission admission) {
        // Which temporal this builds, or none — asked of what the callee denotes
        // ({@link ValueName.Stdlib#constructs()}), as every other question about which operation a
        // call applies is. Read off the spelling, a model's own behavior named `Date` was a written
        // date here; a fixture expression is never elaborated, so this reader answers for itself and
        // has to ask the same place the elaborator asks.
        Type.Prim temporal = c.answered() != null
                && c.answered().denotes() instanceof ValueName.Stdlib library
                ? library.constructs() : null;
        if (temporal != null) {
            // a written date: the decoders take the parsed temporal, not its text (a Date field's
            // neutral form is a LocalDate), so the fixture hands over the same value the checker read
            if (c.args().size() != 1 || !(c.args().get(0) instanceof Hir.StringLit lit)) {
                throw new FixtureException("`" + c.written() + "` takes one written string");
            }
            return CallElaborator.parseTemporal(temporal, c.written(), lit.value(), lit.reportedAt());
        }
        if (!constructsANewtype(c)) {
            throw new FixtureException("`" + c.written() + "` is not a newtype; a fixture cannot call it");
        }
        TypeSymbol built = constructs(c);
        if (c.args().size() != 1) {
            throw new FixtureException("`" + c.written() + "` takes one argument");
        }
        // A newtype over a temporal (`data 貸出日 = Date`) wraps a temporal, so what it takes is one:
        // `貸出日(Date("2026-07-25"))`, which is what a model body writes (a bare string there is
        // E1317) and what the generator writes for it. There is no reading of the argument here that
        // this position has and the rest of the language does not.
        //
        // the argument is shaped against what the newtype wraps, the same way a record fixture
        // shapes a field's value: a `Map` newtype's entry pairs become a map, a `Set` newtype's
        // written list stays a list for its decoder to dedupe
        Position base = NeutralForm.declaredBy(neutral.newtypeBaseType(built));
        return neutral.newtypeAt(at, built,
                neutral.shaped(raw(c.args().get(0), base, below(admission)), base));
    }

    private Object record(Hir.NewData nd, Position at, Admission admission) {
        // A construction naming nothing builds no value a fixture can supply, and the name it wrote
        // is reported where it is written.
        if (nd.typeName().answered() == null) {
            throw new FixtureException("`" + nd.typeName().written()
                    + "` is not a type a fixture can build");
        }
        // `金額(500)` is the record literal `金額 { value = 500 }` written in call form (ADR-0032), and
        // a value's body reaches here already written the second way. Either spelling is the newtype's
        // own neutral form — its inner value — not a field map.
        TypeSymbol built = nd.typeName().answered().type();
        if (neutral.isNewtype(built) && nd.spreads().isEmpty() && nd.inits().size() == 1
                && nd.inits().get(0).name().equals(NeutralForm.NEWTYPE_FIELD)) {
            Position base = NeutralForm.declaredBy(neutral.newtypeBaseType(built));
            return neutral.newtypeAt(at, built,
                    neutral.shaped(raw(nd.inits().get(0).value(), base, below(admission)), base));
        }
        Map<String, Type> declared = neutral.fieldTypes(nd.typeName().answered().type());
        Map<String, Object> map = new LinkedHashMap<>();
        // `...base` copies the fields of a value, and the fields written after it replace what it
        // brought.
        for (Hir.Var spreadName : nd.spreads()) {
            if (!(spreadName instanceof Hir.Var.Denoting ref)) {
                throw new FixtureException("`" + spreadName.name()
                        + "` is not a value a fixture can spread");
            }
            // A spread names a value in force, so what it copies is what that name denotes: a binding
            // holds what it was bound to, and a definition stands for its body. A definition is
            // reached by the name this row spells it with — a definition of this module by its own
            // name, one another module published by that module's name and its own. `bare()` is the
            // name it was *declared* under, which is not that key for an imported value (issue #212).
            String spread = ref.name();
            Hir.Expr value = ref.denotes() instanceof ValueName.Local local
                    ? bindings.get(local.id()) : valueBody(spread);
            if (value == null) {
                throw new FixtureException("`" + spread
                        + "` is not a value a fixture can spread");
            }
            // The fields of a value of another type, which is how the language writes one record from
            // another (`Filed { ...d, filedOn = on }`, where `d` is a `Document`). So the frame this
            // opens states no value of the construction's type, while the fields it copies were
            // written at their own positions and hold there.
            Object copied = expandedValue(ref.denotes(), value,
                    Position.at(Type.ref(nd.typeName().answered().type())),
                    admission == Admission.UNHELD ? admission : Admission.HELD_BELOW);
            if (!(copied instanceof Map<?, ?> fields)) {
                throw new FixtureException("`" + spread + "` is not a record, so it has no fields to"
                        + " spread");
            }
            for (Map.Entry<?, ?> f : fields.entrySet()) {
                String field = String.valueOf(f.getKey());
                // A spread copies fields, and only the ones this construction has — which is what the
                // language copies. The discriminator the spread source's own sum's decoder reads is not
                // one of them: it says which case that value is, and this construction says which case
                // it builds (below). Copying it left the source's case in place of it, and where the
                // position is a union that is the case the behavior saw (issue #206).
                if (declared.containsKey(field)) {
                    map.put(field, f.getValue());
                }
            }
        }
        for (Hir.FieldInit fi : nd.inits()) {
            Position field = NeutralForm.declaredBy(declared.get(fi.name()));
            Object v = neutral.shaped(raw(fi.value(), field, below(admission)), field);
            // `None` on a `T?` field yields a null; leave the key out so the optional decoder reads it as
            // absent (spec §absence-is-written-as-null, absent -> None), the same neutral form as omitting
            // the field. A spread already wrote the field, so leaving the key out means taking it back out —
            // not writing nothing, which would leave what the spread copied standing.
            if (v == null) {
                map.remove(fi.name());
                continue;
            }
            map.put(fi.name(), v);
        }
        neutral.tagged(at, nd.typeName().answered().type(), map);
        return map;
    }

    private static Object negate(Object v) {
        if (v instanceof Long l) {
            return -l;
        }
        if (v instanceof BigDecimal d) {
            return d.negate();
        }
        throw new FixtureException("only a number can be negated in a fixture");
    }

    private Object fold(Hir.Binary b) {
        Object l = raw(b.left(), Position.UNREAD);
        Object r = raw(b.right(), Position.UNREAD);
        if (l instanceof Long x && r instanceof Long y) {
            return switch (b.op()) {
                case ADD -> x + y;
                case SUB -> x - y;
                case MUL -> x * y;
                default -> throw new FixtureException("unsupported arithmetic in a fixture");
            };
        }
        if (l instanceof BigDecimal x && r instanceof BigDecimal y) {
            return switch (b.op()) {
                case ADD -> x.add(y);
                case SUB -> x.subtract(y);
                case MUL -> x.multiply(y);
                default -> throw new FixtureException("unsupported arithmetic in a fixture");
            };
        }
        throw new FixtureException("a fixture can only combine numbers of the same kind");
    }

    // --- decode a raw value into the parameter/expected type ----------------------------------

    private Object decode(FixtureShape shape, Object rawValue) {
        Type type = shape.type();
        Object raw = neutral.shaped(rawValue, Position.at(type));
        Decoder<Object, ?> decoder = decoderFor(shape);
        Result<?> result;
        try {
            result = decoder.decode(raw, net.unit8.raoh.Path.ROOT);
        } catch (RuntimeException e) {
            if (souther.compiler.evaluate.EvaluationContext.overspending(e)) {
                throw e;   // the evaluation ran out, not this fixture failing to fit
            }
            // The decoder is generated for the declared type and casts on the way in, so a fixture of
            // another shape — a string where the parameter is a product, a number where it is a
            // string-backed newtype — fails inside it rather than returning an Err. That is the
            // fixture's problem, and it reads as one (E1903) instead of aborting the compile.
            throw new FixtureException("a " + shapeOf(raw) + " does not fit " + Type.show(type));
        }
        if (result instanceof Ok<?> ok) {
            return ok.value();
        }
        // Name where each failure landed. A decoder reports at a path, and a fixture that breaks the
        // same rule twice — two keys of a newtype-keyed map, two elements of a list — otherwise reads
        // as one message repeated, with nothing to say which value it is about.
        String detail = ((Err<?>) result).issues().asList().stream()
                .map(issue -> {
                    String at = String.join(".", issue.path().segments());
                    return at.isEmpty() ? issue.message() : at + ": " + issue.message();
                })
                .collect(java.util.stream.Collectors.joining("; "));
        throw new FixtureException(detail);
    }

    /** What a fixture value looks like from the decode boundary, for the message above: the raw kinds
     *  a fixture can produce, named as the author wrote them rather than by their JVM class. */
    private static String shapeOf(Object raw) {
        return switch (raw) {
            case null -> "missing value";
            case String _ -> "string";
            case Long _, Integer _ -> "number";
            case java.math.BigDecimal _ -> "decimal";
            case Boolean _ -> "boolean";
            case java.util.Map<?, ?> _ -> "record";
            case java.util.List<?> _ -> "list";
            default -> raw.getClass().getSimpleName();
        };
    }

    @SuppressWarnings("unchecked")
    private Decoder<Object, ?> decoderFor(FixtureShape shape) {
        return switch (shape) {
            case FixtureShape.Scalar s -> leafDecoder(s.scalar());
            // An imported type's decoder lives in its declaring module's package, not this one's.
            //
            // A shape that got here was admitted, and admitting a name is the compiler saying a
            // codec was derived for it. So the class not being there, or having no `decoder()`, is
            // this compiler disagreeing with itself and not a fixture that cannot be read — which is
            // what reporting it as one used to make of it, in the reader's own words, about a
            // program the author would find nothing wrong with.
            case FixtureShape.Nominal n -> {
                try {
                    Class<?> c = loader.loadClass(SoutherJvmAbi.nameOf(new GeneratedClass.Value(n.name())).binaryName());
                    yield (Decoder<Object, ?>) staticCodec(c, "decoder");
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("`" + SoutherJvmAbi.nameOf(new GeneratedClass.Value(n.name())).binaryName()
                            + "` was admitted as a"
                            + " type a fixture builds through its derived decoder, and it has none", e);
                }
            }
            // A collection is decoded the way a data's collection field is, built from the same
            // pieces the derived decoder is (spec §decoder-derivation): a list over its element decoder, a set as a
            // list deduplicated, a map over its value decoder with the keys read by their own.
            case FixtureShape.ListOf l -> ObjectDecoders.list(decoderFor(l.element()));
            case FixtureShape.SetOf s -> ObjectDecoders.list(decoderFor(s.element()))
                    .map(elements -> Sets.fromList(new ArrayList<Object>(elements)));
            case FixtureShape.MapOf m -> mapDecoder(m.key(), m.value());
        };
    }

    /** The leaf decoder for a scalar. A fixture hands over the value it wrote — a temporal arrives
     *  parsed, since that is what the checker read — so these are the neutral-source decoders and
     *  not the ones that parse text. */
    private static Decoder<Object, ?> leafDecoder(LeafScalar scalar) {
        return switch (scalar) {
            case STRING -> ObjectDecoders.string();
            case INT -> ObjectDecoders.long_();
            case BOOL -> ObjectDecoders.bool();
            case DECIMAL -> ObjectDecoders.decimal();
            case DATE -> ObjectDecoders.date();
            case TIME -> ObjectDecoders.time();
            case DATETIME -> ObjectDecoders.dateTime();
            case INSTANT -> ObjectDecoders.iso8601();
        };
    }

    /**
     * The decoder for a map a fixture writes.
     *
     * <p>Its own rather than the neutral-source map decoder wrapped in a rekey. That one reads an
     * object of strings, which is the form a map crosses a boundary as; a fixture writes a list of
     * pairs and what it hands over is a map of the keys themselves. Reading it through the string
     * form was what made a {@code Map<Int, Int>} unbuildable — the last of the boundary's
     * assumptions standing in a position that crosses nothing.
     *
     * <p>Both sides go through their own decoder, so a key's invariant runs where a key is written
     * and a value's where a value is. Every entry is tried and the failures merged, so a fixture
     * with two bad entries names both rather than stopping at the first, and two keys that are one
     * key once decoded are refused rather than collapsed.
     */
    private Decoder<Object, ?> mapDecoder(FixtureShape key, FixtureShape value) {
        Decoder<Object, ?> keys = decoderFor(key);
        Decoder<Object, ?> values = decoderFor(value);
        return (raw, path) -> {
            if (!(raw instanceof Map<?, ?> entries)) {
                return Result.fail(path, "not_a_map",
                        "a `Map` fixture is a list of (key, value) pairs, e.g. [ (\"apple\", 3) ]");
            }
            Map<Object, Object> out = new LinkedHashMap<>();
            Issues issues = Issues.EMPTY;
            for (Map.Entry<?, ?> entry : entries.entrySet()) {
                net.unit8.raoh.Path at = path.append(String.valueOf(entry.getKey()));
                Result<?> k = keys.decode(entry.getKey(), at);
                Result<?> v = values.decode(entry.getValue(), at);
                if (k instanceof Err<?>(var badKey)) {
                    issues = issues.merge(badKey);
                }
                if (v instanceof Err<?>(var badValue)) {
                    issues = issues.merge(badValue);
                }
                if (k instanceof Ok<?>(var decodedKey) && v instanceof Ok<?>(var decodedValue)) {
                    if (out.containsKey(decodedKey)) {
                        issues = issues.merge(((Err<?>) Result.fail(at, "duplicate_key",
                                "two keys are the same key once decoded")).issues());
                    } else {
                        out.put(decodedKey, decodedValue);
                    }
                }
            }
            return issues.isEmpty() ? Result.ok(out) : Result.err(issues);
        };
    }

    /** One decoded input, in the form the compiler owns. Never throws: a value that cannot be read is
     * an unreadable value, and a row that carries one still carries everything else. */
    ObservedValue observed(Object decoded) {
        try {
            return ObservedValues.of(decoded, symbols, neutral, Limits.DEFAULT);
        } catch (RuntimeException | LinkageError e) {
            if (souther.compiler.evaluate.EvaluationContext.overspending(e)) {
                throw (RuntimeException) e;   // the evaluation ran out, not this value being unreadable
            }
            return new ObservedValue.Unknown(e.getClass().getSimpleName());
        }
    }

    /**
     * What a value is, structured for a comparison rather than for a query answer.
     *
     * <p>{@link Limits#DEFAULT} is what keeps a memoised answer from holding a large value. Nothing
     * here is memoised — the structure is compared and dropped inside the row — and a limit that
     * stopped the walk would make two values that differ past it read as the same one.
     */
    static final Limits WHOLE = new Limits(256, Integer.MAX_VALUE, Integer.MAX_VALUE,
            Integer.MAX_VALUE);

    ObservedValue structured(Object value) {
        try {
            return ObservedValues.of(value, symbols, neutral, WHOLE);
        } catch (RuntimeException | LinkageError e) {
            if (souther.compiler.evaluate.EvaluationContext.overspending(e)) {
                throw (RuntimeException) e;
            }
            return new ObservedValue.Unknown(e.getClass().getSimpleName());
        }
    }

    /**
     * A value this compile built, in the form a derived decoder reads.
     *
     * <p>What an answerer whose classes are not this compile's puts through its own decoders
     * ({@link Handed}). The same walk a helper's answer goes through on the way into a fixture, because
     * the form is decided by the rules and not by which side is asking — an answerer reading a value
     * this way is not a second reading of what a value is.
     *
     * @param what a noun phrase naming the value, for the reason a row is given when it cannot be
     *             read back
     */
    NeutralValue neutral(Object built, BoundaryInput at, String what) {
        return new NeutralValue(neutral.of(built, Position.at(FixtureShape.of(at).type()), what));
    }

    /**
     * The same, at a position named as a type rather than as a boundary shape.
     *
     * <p>What needs it is a value coming back the other way: an answer is read at the case it turned
     * out to be, and that case is a type this module declares rather than a position a signature
     * writes down. The walk is the one above — a neutral form is decided by a position — and only
     * where the position comes from differs.
     */
    NeutralValue neutralAt(Object value, Type position, String what) {
        return new NeutralValue(neutral.of(value, Position.at(position), what));
    }

    /**
     * Whether what a row stated is what came back.
     *
     * <p>Asked of the statement, which is what owns the comparison: whether the row wrote a value or
     * named a case decides what being the same answer means, and a caller that told the two apart
     * itself would be a second place that decided it.
     */
    Verdict holds(Expectation.Asserts stated, Object result, Type position) {
        return souther.compiler.observe.Comparisons.verdict(stated, structured(result), types,
                Position.at(position));
    }

    /** What a row stated, as it stated it: the value it wrote, the case it named, or the mark that
     *  says its answer is owed. */
    String shown(Expectation stated) {
        return switch (stated) {
            case Expectation.TheValue(Asserted value) -> shown(value);
            case Expectation.TheCase(TypeSymbol name) -> name.name();
            // As the row writes it. A reader shown a phrase about the row instead would be shown
            // this compiler's words where the row's own characters are what they are looking for.
            case Expectation.Owed _ -> SyntaxKind.UNANSWERED.fixedSpelling().orElseThrow();
        };
    }

    /** Where inside a value two of them differ. */
    String shown(List<PathElement> path) {
        return new ValueRendering(neutral).shown(path);
    }

    /** A value in the notation a fixture is written in — what a row wrote, or what came out. */
    String shown(Asserted a) {
        return new ValueRendering(neutral).show(a);
    }

    String shown(ObservedValue v, Type position) {
        return new ValueRendering(neutral).show(v, position);
    }

    /**
     * The same, beside what the row stated, whose order the pairs of a map are put in.
     *
     * <p>Which of the answer's parts stands for which of the row's is the comparison's answer and is
     * asked for here rather than found again: a map's entries are paired by key and a set's
     * elements without an order, and a second way of finding them is a second answer to what being
     * the same value means.
     */
    String shown(ObservedValue v, Type position, Asserted stated) {
        return new ValueRendering(neutral).show(v, position,
                souther.compiler.observe.Comparisons.alignment(stated, v, types,
                        souther.compiler.observe.Position.at(position)));
    }

    /** What a value is, named as the language names it. */
    String typeShown(Asserted a) {
        return new ValueRendering(neutral).typeShown(a);
    }

    String typeShown(ObservedValue v, Type position) {
        return new ValueRendering(neutral).typeShown(v, position);
    }

    String typeShown(ObservedValue v, Position position) {
        return new ValueRendering(neutral).typeShown(v, position);
    }

    /** What a row stated, named as the language names it. */
    String typeShown(Expectation.Asserts stated) {
        return new ValueRendering(neutral).typeShown(stated);
    }

    BoundaryValues.Built building(BoundaryInput at, Hir.Expr fixture) {
        try {
            // What it came to, and not only that it came to something. Read through the same walk
            // a row's own inputs are read through, so where a candidate lands is answered the way
            // where a written row lands is.
            return new BoundaryValues.Built.Value(observed(built(fixture, at)));
        } catch (FixtureException e) {
            return new BoundaryValues.Built.Refused(e.getMessage());
        } catch (RuntimeException e) {
            if (souther.compiler.evaluate.EvaluationContext.overspending(e)) {
                throw e;   // the evaluation ran out; whether the fixture is refused is still unread
            }
            return new BoundaryValues.Built.Refused(String.valueOf(e.getMessage()));
        }
    }
}
