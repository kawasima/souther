package souther.compiler.check;

import souther.compiler.hash.SaysWhatStandsForIt;
import souther.compiler.types.BinOp;
import souther.compiler.types.TypeSymbol;
import souther.compiler.types.ValueName;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What the invariant-discharge check names a value by — the identity two writings of one value
 * share, and the whole of what a fact set knows about which value it is talking about.
 *
 * <p>A value, not a rendering of one. Two terms are the same term when they are built the same way
 * out of the same parts, and that is the only thing that makes them the same. This was a string
 * before, written by concatenating the parts' own strings, and the encoding was not injective: a
 * list of one string element {@code a", "b} and a list of two elements {@code ["a", "b"]} wrote one
 * key, so a guard about the first discharged a clause about the second. Nothing here can spell one
 * value the way another spells itself, because nothing here spells anything.
 *
 * <p>Equality is structural. {@link Terms} hands every term it builds through an interner, so two
 * writings of one value are usually one object and the comparison stops at the first line — but that
 * is an optimisation and not the contract. A term built without going through that interner, or
 * through another one, is equal to its twin all the same, which is what lets one reading be compared
 * against another.
 *
 * <p>The hash is computed once, when the term is built, out of the hashes its parts already carry.
 * A chain of terms is a graph and not a tree — a name read twice is one value read twice — so a hash
 * that walked the parts at every ask would cost what writing the parts out used to cost, which is
 * the thing that made the string form of this hold a name for each shape rather than the shape.
 *
 * <p>Equality of an {@code ==} between two values does not depend on which was written first, so
 * that shape holds its two parts as a pair with no order. Every other shape is ordered.
 */
final class Term {

    /** How a term is built. What a shape means is what {@link Terms} builds it for; this says only
     * which shapes are told apart, and what each carries beside its parts ({@link Payload}). */
    enum Shape {
        /** A place: a binding and the fields read from it. */
        AT(Payload.of(Location.class)),
        /** A parameter of a closure, named by where it is bound rather than by which binding it is. */
        BOUND(Payload.of(At.class)),
        /** Fields read off a term that is not a place. */
        ON(Payload.listOf(Payload.of(String.class))),
        INT(Payload.of(Long.class)),
        DECIMAL(Payload.of(BigDecimal.class)),
        STRING(Payload.of(String.class)),
        BOOL(Payload.of(Boolean.class)),
        UNIT(Payload.of(TypeSymbol.class)),
        /** Arithmetic negation. */
        NEG(Payload.none()),
        /** The denial of a condition. */
        NOT(Payload.none()),
        /** An {@code ==}, whose two parts are unordered. */
        EQ(Payload.none()),
        /** Any other operator, over its two operands in the order written. */
        OP(Payload.of(BinOp.class)),
        LIST(Payload.none()),
        TUPLE(Payload.none()),
        /** One element of a tuple, by index. */
        PART(Payload.of(Integer.class)),
        /** A conditional, over its condition and its two branches. */
        CHOICE(Payload.none()),
        /** A closure, over the number of parameters it binds and its body. */
        CLOSURE(Payload.of(Integer.class)),
        /** A construction, over the values its fields are given in declaration order. */
        BUILT(Payload.of(Built.class)),
        /** A call, over its arguments. */
        CALLED(Payload.of(ValueName.class)),
        /**
         * One evaluation, told apart from every other by the { EvaluationId} it holds and not by
         * anything about its shape.
         *
         * <p>The one nominal atom. Everything else here is structural all the way down, which is what
         * makes two writings of one value one term; a value nothing may share needs a leaf that is
         * equal to nothing but itself, and it needs to be a leaf of this same algebra so that what is
         * built over it composes. { length(E)} written twice is one term because { CALLED}
         * interns over its children and the child is the one atom both times.
         */
        EVALUATION(Payload.of(EvaluationId.class)),
        /** A value given to an optional position. */
        SOME(Payload.none()),
        /**
         * What a present optional holds, read where an arm has opened one.
         *
         * <p>The one operation that takes a value apart here, and it is not a projection algebra: an
         * optional's carrier is the only case whose binding is a different value from the value it was
         * read out of, so this is that one case and nothing more general. Everything else a
         * {@code match} opens binds the value it was given.
         */
        HELD(Payload.none()),
        /** The empty optional, at the type of the position it fills. */
        NONE(Payload.of(souther.compiler.types.Type.class)),
        /**
         * The value one case of a union carries, over the value that union is.
         *
         * <p>For an operation answering its result as a case of a union rather than as a number:
         * the call is one value and the number its success carries is another, and naming the two
         * alike would file everything said about either under one key. {@link #HELD} is the same
         * shape for the one case the language builds itself, and this is the general one — held
         * apart because an optional's carrier is a case of a type this compiler declares and can
         * cancel against the {@code Some} that built it, and a union's case is neither.
         *
         * <p>Not every case result takes this. Where the language has a second spelling for the same
         * arithmetic, the value is named by that arithmetic — the {@code Int} of {@code Int.divide}
         * is {@code a / b} and is one term with the written divide (spec
         * §invariant-discharge-arithmetic).
         */
        OPENED(Payload.of(souther.compiler.types.Type.class)),
        /**
         * A value a walk hands its step, read where that walk is being proved about.
         *
         * <p>Named against the walk and by which parameter it arrives on, because that is what makes
         * it one value. The step's parameters are bindings, and two readings of one walk bind them
         * twice — a walk is keyed by what it is written over, with the step's binders normalised, so
         * the very same walk arrives with different bindings and is one atom. An accumulator named by
         * its binding was then two values under one name, which is what the check refuses to hold.
         *
         * <p>And by the walk, not only by the position: two walks in one body each hand their step an
         * accumulator, and they are two values.
         */
        HANDED(Payload.of(Integer.class)),
        /** A value opened, over the scrutinee and each arm's body. */
        MATCHED(Payload.listOf(Payload.listOf(Payload.of(TypeSymbol.class)))),
        /** An attempted construction, over what it builds and what each of its departures answers. */
        ATTEMPTED(Payload.listOf(Payload.of(String.class)));

        private final Payload payload;

        Shape(Payload payload) {
            this.payload = payload;
        }

        /** What a term of this shape carries beside its parts. */
        Payload payload() {
            return payload;
        }
    }

    /**
     * What a shape carries beside its parts, said as something a walk can read.
     *
     * <p>Written here because {@link #of} is an {@code Object} and an {@code Object} states nothing:
     * a hash taken of one is taken of whatever that value's own class does, and the values a term is
     * made of have to be values for the hash to be one. So the shapes say what they carry, and
     * {@link #hashOf} says how each kind of thing is taken — the two together are what makes the
     * whole of a term's hash a function of the term.
     *
     * <p>A list says what its elements are rather than being one class, since a class does not carry
     * its element type and an element is where the walk would otherwise stop.
     */
    sealed interface Payload {

        /**
         * Whether {@code value} is what this says a shape carries.
         *
         * <p>Asked of every term as it is built, under assertions. What a shape carries is written
         * down so that a walk can prove nothing a term is hashed from is hashed by its identity, and
         * a walk over what was written proves that of the shapes as written — so if the writing and
         * the building come apart, what is proved is a set of types nothing builds. The building is
         * what settles it, and this is where the two meet.
         */
        boolean holds(Object value);

        /** A shape whose parts are the whole of it. */
        record Nothing() implements Payload {

            @Override
            public boolean holds(Object value) {
                return value == null;
            }
        }

        /** A value of {@code type}, or of one of the types {@code type} permits. */
        record OfType(Class<?> type) implements Payload {

            @Override
            public boolean holds(Object value) {
                return type.isInstance(value);
            }
        }

        /** A list of {@code element}. */
        record OfList(Payload element) implements Payload {

            @Override
            public boolean holds(Object value) {
                if (!(value instanceof List<?> values)) {
                    return false;
                }
                for (Object each : values) {
                    if (!element.holds(each)) {
                        return false;
                    }
                }
                return true;
            }
        }

        static Payload none() {
            return new Nothing();
        }

        static Payload of(Class<?> type) {
            return new OfType(type);
        }

        static Payload listOf(Payload element) {
            return new OfList(element);
        }
    }

    /** What a shape holds beside its parts: a location, a written value, an operator, the name of an
     * operation, the type and fields of a construction. Compared by its own equality. */
    private final Object of;
    private final Shape shape;
    private final List<Term> parts;
    private final int hash;

    /**
     * How a term's hash is mixed with the hashes of its parts.
     *
     * <p>An odd number, and not a small one. A term that reads one part twice folds that part in
     * twice, so the multiplier decides what a chain of those does to the hash it carries up: with
     * {@code 31}, a part folded twice contributes {@code 32} times what the level under it did, and
     * thirty-two is two to the fifth — seven links of that shift every bit of the hash out, and from
     * there every link of the chain hashes alike. The chain is exactly what {@link Terms} builds when
     * a body names a value and reads it twice, so the terms that collided were the ones the sharing
     * is for.
     */
    private static final int MIX = 0x9E3779B1;

    /**
     * How the value under a payload is taken into a term's hash.
     *
     * <p>Asked of a class rather than written into one switch body, so that the walk which proves the
     * payload types are all taken by value reads the same answer the hash reads. A rule stated in a
     * place a test cannot ask is a rule a test can only copy.
     */
    enum Rule {
        /** Its own hash. The platform's own scalars and nothing else: every other class is taken
         *  apart, so that what a hash of it reads is walked rather than trusted. */
        ITS_OWN_HASH,
        /** Its name: an enum constant's own hash is its identity and is drawn afresh each run. */
        AN_ENUM_BY_NAME,
        /** Each of its record components, in the order they are declared. */
        ITS_COMPONENTS,
        /** Each of its elements, in order. */
        ITS_ELEMENTS,
        /** Each of its elements, in no order — what a set's own equality reads. */
        ITS_UNORDERED_ELEMENTS,
        /** The value it says stands for it ({@link SaysWhatStandsForIt}). */
        THE_PARTS_IT_NAMES,
        /** Nothing here takes a value of this class. */
        NONE_HERE
    }

    /** A class the platform hashes by what the value is. */
    private static final java.util.Set<Class<?>> SCALARS = java.util.Set.of(
            String.class, Long.class, Integer.class, Boolean.class, BigDecimal.class,
            Short.class, Byte.class, Character.class, Double.class, Float.class);

    private static final ClassValue<Rule> RULES = new ClassValue<>() {

        @Override
        protected Rule computeValue(Class<?> type) {
            return ruleOf(type);
        }
    };

    /** How a value of {@code type} is taken. */
    static Rule ruleFor(Class<?> type) {
        return RULES.get(type);
    }

    private static Rule ruleOf(Class<?> type) {
        if (Enum.class.isAssignableFrom(type)) {
            return Rule.AN_ENUM_BY_NAME;
        }
        if (SCALARS.contains(type)) {
            return Rule.ITS_OWN_HASH;
        }
        if (List.class.isAssignableFrom(type)) {
            return Rule.ITS_ELEMENTS;
        }
        if (java.util.Set.class.isAssignableFrom(type)) {
            return Rule.ITS_UNORDERED_ELEMENTS;
        }
        // Asked before the question about records, because a value that keeps the number it is
        // asked for is a class here and may hold its parts in a record all the same: what it says
        // stands for it is the answer either way, and its own components are the field it keeps
        // them in and the number it worked out.
        if (SaysWhatStandsForIt.class.isAssignableFrom(type)) {
            return Rule.THE_PARTS_IT_NAMES;
        }
        // A record given its equality holds it over everything it carries, so its components are
        // what it is. One stating an equality of its own holds it over some of what it carries, and
        // taking the rest in would give two equal values two hashes — so it says which parts stand
        // for it or nothing here takes it.
        if (type.isRecord() && !statesAHashOfItsOwn(type)) {
            return Rule.ITS_COMPONENTS;
        }
        return Rule.NONE_HERE;
    }

    /**
     * Whether {@code type} says what a value of it hashes to, rather than leaving it to what the
     * value is made of or to which object it is.
     *
     * <p>Read off the modifier, since the hash a record is given is final and one written by hand is
     * not. What it decides is what is left of a record that has not said what stands for it: one
     * stating no hash is what it holds, and following its components is following its hash; one
     * stating a hash states it over some of what it holds, so following the rest would give two
     * equal values two hashes, and nothing here takes it.
     *
     * <p>Which is why a value that keeps the number it is asked for says what stands for it. A hash
     * is not walked into — what such a number is taken from would be the one thing nothing here
     * reads — and naming the value it is over puts that back inside the walk. Asked about the hash
     * and not about the equality: {@link EvaluationId} tells two apart by which object each is, and
     * it is the number a term is built from that this is about.
     */
    private static boolean statesAHashOfItsOwn(Class<?> type) {
        for (java.lang.reflect.Method method : type.getDeclaredMethods()) {
            if (method.getName().equals("hashCode") && method.getParameterCount() == 0) {
                return !java.lang.reflect.Modifier.isFinal(method.getModifiers());
            }
        }
        return false;
    }

    /** How each of a record's components is read, worked out once for the class. */
    private static final ClassValue<java.lang.invoke.MethodHandle[]> ACCESSORS = new ClassValue<>() {

        @Override
        protected java.lang.invoke.MethodHandle[] computeValue(Class<?> type) {
            List<java.lang.reflect.Method> read = readersOf(type);
            java.lang.invoke.MethodHandle[] accessors =
                    new java.lang.invoke.MethodHandle[read.size()];
            java.lang.invoke.MethodHandles.Lookup lookup = java.lang.invoke.MethodHandles.lookup();
            for (int i = 0; i < read.size(); i++) {
                java.lang.reflect.Method accessor = read.get(i);
                accessor.setAccessible(true);
                try {
                    accessors[i] = lookup.unreflect(accessor)
                            .asType(java.lang.invoke.MethodType.methodType(
                                    Object.class, Object.class));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("a " + type.getName() + " does not answer "
                            + accessor.getName(), e);
                }
            }
            return accessors;
        }
    };

    /**
     * How each of what stands for a value of {@code type} is read: the one value it says stands for
     * it, or its record components where it says nothing.
     *
     * <p>A value that names one is read through that one and not through what it holds beside it.
     * Which is what the naming is for: a value keeping the number it is asked for holds that number
     * too, and a number is the one thing a walk proving where numbers come from must not read.
     */
    static List<java.lang.reflect.Method> readersOf(Class<?> type) {
        List<java.lang.reflect.Method> read = new ArrayList<>();
        try {
            if (SaysWhatStandsForIt.class.isAssignableFrom(type)) {
                read.add(type.getMethod("standsFor"));
                return read;
            }
            for (java.lang.reflect.RecordComponent component : type.getRecordComponents()) {
                read.add(component.getAccessor());
            }
            return read;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(type.getName() + " does not answer what it says stands"
                    + " for it", e);
        }
    }

    /**
     * The hash of a value a shape carries, which is a function of that value and of nothing else.
     *
     * <p>The one place a term's hash meets something that is not a term. What is wanted of it is not
     * a good hash but a hash of the value: {@code Object.hashCode} tells an object from every other
     * and says nothing about what it holds, and on HotSpot the number it answers is drawn from a
     * sequence held per thread — so a term hashed through one is hashed by when its class was first
     * asked, which is a fact about the run and not about the program. An enum is that case wearing a
     * value's clothes, since {@code Enum.hashCode} is {@code Object}'s and cannot be replaced.
     *
     * <p>What is refused is refused here rather than left to answer wrongly. A payload nothing above
     * knows how to take is a shape carrying something this algebra was never extended to, and the
     * only thing it could do quietly is hash it by its identity.
     */
    static int hashOf(Object value) {
        if (value == null) {
            return 0;
        }
        Class<?> type = value.getClass();
        return switch (ruleFor(type)) {
            case ITS_OWN_HASH -> value.hashCode();
            case AN_ENUM_BY_NAME -> ((Enum<?>) value).name().hashCode();
            case ITS_COMPONENTS, THE_PARTS_IT_NAMES -> componentsOf(value, type);
            case ITS_ELEMENTS -> elementsOf((List<?>) value);
            case ITS_UNORDERED_ELEMENTS -> unorderedOf((java.util.Set<?>) value);
            case NONE_HERE -> throw new IllegalStateException(
                    "nothing says what a term hashed from a " + type.getName() + " is hashed from");
        };
    }

    /** What a set holds, taken so that the answer does not depend on the order it hands them over
     *  in — which is what its own equality reads, and a hash that read more would give two equal
     *  sets two hashes. */
    private static int unorderedOf(java.util.Set<?> values) {
        int h = values.size();
        for (Object value : values) {
            int each = hashOf(value);
            h += each ^ (each >>> 16);
        }
        return h;
    }

    /** Which record it is and what it holds. The class is taken in because two records holding alike
     *  are two values, and a term carrying either is told apart by nothing else here. */
    private static int componentsOf(Object value, Class<?> type) {
        int h = type.getName().hashCode();
        for (java.lang.invoke.MethodHandle accessor : ACCESSORS.get(type)) {
            try {
                h = h * MIX + hashOf((Object) accessor.invokeExact(value));
            } catch (Throwable e) {
                throw new IllegalStateException("a " + type.getName() + " does not answer one of"
                        + " what it holds", e);
            }
        }
        return h;
    }

    private static int elementsOf(List<?> values) {
        int h = values.size();
        for (Object value : values) {
            h = h * MIX + hashOf(value);
        }
        return h;
    }

    private Term(Shape shape, Object of, List<Term> parts) {
        assert shape.payload().holds(of) : shape + " is written as carrying " + shape.payload()
                + " and was built with " + (of == null ? "nothing" : of.getClass().getName());
        this.shape = shape;
        this.of = of;
        this.parts = List.copyOf(parts);
        int h = hashOf(shape) * MIX + hashOf(of);
        h = h * MIX + this.parts.size();
        if (shape == Shape.EQ) {
            // an equality is between two values and not from one to the other
            h = h * MIX + (this.parts.get(0).hash + this.parts.get(1).hash);
        } else {
            for (Term part : this.parts) {
                h = h * MIX + part.hash;
            }
        }
        // A last mix, so that what a level carries up depends on its high bits as well as its low
        // ones: without it a hash the multiplier only ever shifts left says less the deeper it goes.
        this.hash = h ^ (h >>> 16);
    }

    Shape shape() {
        return shape;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Term term) || term.hash != hash || term.shape != shape
                || !java.util.Objects.equals(term.of, of)) {
            return false;
        }
        if (shape == Shape.EQ) {
            return (parts.get(0).equals(term.parts.get(0)) && parts.get(1).equals(term.parts.get(1)))
                    || (parts.get(0).equals(term.parts.get(1))
                            && parts.get(1).equals(term.parts.get(0)));
        }
        return parts.equals(term.parts);
    }

    /**
     * Whether this stands on one evaluation and nothing else this check can read — the atom itself,
     * or a field read off one.
     *
     * <p>Such a term names a value, which is what lets a guard or a declaration speak about it; it
     * says nothing at all on its own. The two used to be one answer, because a value with no
     * structure to read had no name either, and a reader could take having a name as evidence that
     * something was readable. Now it is not evidence, and the readers that used it that way ask this.
     */
    boolean standsOnAnEvaluation() {
        return switch (shape) {
            case EVALUATION -> true;
            case ON, HELD, HANDED -> parts.get(0).standsOnAnEvaluation();
            default -> false;
        };
    }

    String rendered() {
        StringBuilder sb = new StringBuilder();
        switch (shape) {
            case AT, BOUND -> sb.append(of);
            case EVALUATION -> sb.append(of);
            case ON -> sb.append(parts.get(0).rendered()).append(path());
            case INT, DECIMAL, BOOL -> sb.append(of);
            case STRING -> sb.append('"').append(of).append('"');
            case UNIT -> sb.append(of);
            case NEG -> sb.append('-').append(parts.get(0).rendered());
            case NOT -> sb.append('!').append(parts.get(0).rendered());
            case EQ -> sb.append('(').append(parts.get(0).rendered()).append(" == ")
                    .append(parts.get(1).rendered()).append(')');
            case OP -> sb.append('(').append(parts.get(0).rendered()).append(' ').append(of)
                    .append(' ').append(parts.get(1).rendered()).append(')');
            case LIST -> joined(sb.append('['), ", ").append(']');
            case TUPLE -> joined(sb.append('('), ", ").append(')');
            case PART -> sb.append(parts.get(0).rendered()).append('.').append(of);
            case CHOICE -> joined(sb.append("if("), ", ").append(')');
            case CLOSURE -> joined(sb.append("\\").append(of).append('('), ", ").append(')');
            case BUILT -> {
                Built built = (Built) of;
                sb.append(built.type()).append('{');
                for (int i = 0; i < parts.size(); i++) {
                    sb.append(i == 0 ? "" : ", ").append(built.fields().get(i)).append('=')
                            .append(parts.get(i).rendered());
                }
                sb.append('}');
            }
            case CALLED -> joined(sb.append(((ValueName) of).name()).append('('), ", ").append(')');
            case SOME -> sb.append("Some(").append(parts.get(0).rendered()).append(')');
            case HELD -> sb.append("held(").append(parts.get(0).rendered()).append(')');
            case HANDED -> sb.append("handed(").append(parts.get(0).rendered()).append(", ")
                    .append(of).append(')');
            case NONE -> sb.append("None:").append(of);
            case OPENED -> sb.append("opened(").append(parts.get(0).rendered()).append(", ")
                    .append(souther.compiler.types.Type.show((souther.compiler.types.Type) of))
                    .append(')');
            case MATCHED -> joined(sb.append("match("), ", ").append(')');
            case ATTEMPTED -> joined(sb.append("attempt("), ", ").append(')');
        }
        return sb.toString();
    }

    private StringBuilder joined(StringBuilder sb, String between) {
        for (int i = 0; i < parts.size(); i++) {
            sb.append(i == 0 ? "" : between).append(parts.get(i).rendered());
        }
        return sb;
    }

    private String path() {
        StringBuilder sb = new StringBuilder();
        for (String field : fields()) {
            sb.append('.').append(field);
        }
        return sb.toString();
    }

    /**
     * How this reads where a person is shown one — a report, a log, this compiler's own tests.
     *
     * <p>What it renders as says nothing about what it is. Two terms rendering alike are not thereby
     * one term, and nothing here reads a rendering back: that is what the string form of this was,
     * and it is why an escaping bug in it could make one value out of two.
     */
    @Override
    public String toString() {
        return rendered();
    }

    /** The type a construction builds and the fields it gives values to, in the order the parts hold
     * those values. */
    record Built(TypeSymbol type, List<String> fields) {

        Built {
            fields = List.copyOf(fields);
        }
    }

    /** Where a closure's parameter is bound: which closure down from here, and which parameter. */
    record At(int depth, int index) {

        @Override
        public String toString() {
            return "#" + depth + "." + index;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> fields() {
        return (List<String>) of;
    }

    // --- how terms are built -------------------------------------------------------------------

    /**
     * Terms this analysis has built, each under the one instance of it that stands for it.
     *
     * <p>Sharing, not identity. Two writings of one value are equal terms whether or not they were
     * built here; going through this makes the comparison stop at the first line and makes the hash
     * a term already carries the one its parts are hashed from. One of these lives as long as the
     * reading that fills it.
     */
    static final class Interner {

        private final Map<Term, Term> shared = new HashMap<>();

        private Term of(Shape shape, Object of, List<Term> parts) {
            Term made = new Term(shape, of, parts);
            Term had = shared.putIfAbsent(made, made);
            return had != null ? had : made;
        }

        /** The place {@code where} is. */
        Term at(Location where) {
            return of(Shape.AT, where, List.of());
        }

        /** A closure's parameter, by where it is bound. */
        Term bound(int depth, int index) {
            return of(Shape.BOUND, new At(depth, index), List.of());
        }

        /**
         * {@code base} with {@code fields} read off it, or {@code base} where none are.
         *
         * <p>One chain and not a chain of chains: fields read off something that already has fields
         * read off it are the one path, so a value reached a field at a time and the same value
         * reached all at once are one term.
         *
         * <p>Which holds of a place too. A place is a root and the fields read from it ({@link
         * Shape#AT}), so reading one more field off it is that same root with a longer path, and not
         * a path over a term that happens to be a place. Reading a value's fields does not decide
         * which value it is, so the two spellings of {@code x.a} — the whole chain asked at once, and
         * {@code a} read off what {@code x} is — are one term. Without it a reader that builds a
         * chain from the root's own identity and a reader that builds it from the location answer
         * differently about one value, which is the identity question given two authorities.
         */
        Term on(Term base, List<String> fields) {
            if (fields.isEmpty()) {
                return base;
            }
            if (base.shape == Shape.AT) {
                Location where = (Location) base.of;
                List<String> whole = new ArrayList<>(where.path());
                whole.addAll(fields);
                return at(new Location(where.root(), whole));
            }
            if (base.shape == Shape.ON) {
                List<String> whole = new ArrayList<>(base.fields());
                whole.addAll(fields);
                return of(Shape.ON, List.copyOf(whole), base.parts);
            }
            return of(Shape.ON, List.copyOf(fields), List.of(base));
        }

        Term written(long value) {
            return of(Shape.INT, value, List.of());
        }

        Term written(BigDecimal value) {
            return of(Shape.DECIMAL, value, List.of());
        }

        Term written(String value) {
            return of(Shape.STRING, value, List.of());
        }

        Term written(boolean value) {
            return of(Shape.BOOL, value, List.of());
        }

        Term unit(TypeSymbol data) {
            return of(Shape.UNIT, data, List.of());
        }

        Term negated(Term operand) {
            return of(Shape.NEG, null, List.of(operand));
        }

        /** The denial of {@code condition}, and of a denial the condition itself. */
        Term not(Term condition) {
            return condition.shape == Shape.NOT ? condition.parts.get(0)
                    : of(Shape.NOT, null, List.of(condition));
        }

        /**
         * The operator over its two operands, in the order written.
         *
         * <p>Knows nothing about comparisons and refuses one. What a comparison of two values comes
         * to is decided from what it placed and reaches here as a statement
         * ({@link #comparison}); an operator arriving here is what is left, which is arithmetic,
         * a join of two conditions, or a concatenation. Answered here as well, the six ways to
         * compare two values would be read a second time out of the operator, below the point where
         * what they state was already settled.
         */
        Term operator(BinOp op, Term left, Term right) {
            if (op.compares()) {
                throw new IllegalArgumentException(
                        "what a comparison names is decided from what it placed: " + op);
            }
            return of(Shape.OP, op, List.of(left, right));
        }

        /**
         * The term {@code canonical} names.
         *
         * <p>Two clauses comparing the same two terms are one term however the author reached for
         * the comparison, which matters wherever the comparison is not the whole condition — only
         * there can the denial not be carried by the polarity instead. What each spelling states is
         * the claim's answer, and this says which term states it.
         */
        Term comparison(CanonicalComparison<Term> canonical) {
            return canonical.expressedAs(asTerms);
        }

        private final AsTerms asTerms = new AsTerms();

        /** Which term a canonical statement is written as. */
        private final class AsTerms implements CanonicalComparison.Expression<Term, Term> {

            /** Whose two parts are unordered, because which side of an equality a value was written
             *  on says nothing about it ({@link Shape#EQ}). */
            @Override
            public Term theSameValue(Term left, Term right) {
                return of(Shape.EQ, null, List.of(left, right));
            }

            /** The one order the terms are written in, so a comparison written the other way round
             *  is the same term with its two operands exchanged. */
            @Override
            public Term below(Term left, Term right) {
                return of(Shape.OP, BinOp.LT, List.of(left, right));
            }

            @Override
            public Term denied(Term statement) {
                return not(statement);
            }
        }

        Term list(List<Term> elements) {
            return of(Shape.LIST, null, elements);
        }

        Term tuple(List<Term> elements) {
            return of(Shape.TUPLE, null, elements);
        }

        Term part(Term tuple, int index) {
            return of(Shape.PART, index, List.of(tuple));
        }

        Term choice(Term condition, Term then, Term els) {
            return of(Shape.CHOICE, null, List.of(condition, then, els));
        }

        Term closure(int params, Term body) {
            return of(Shape.CLOSURE, params, List.of(body));
        }

        /** A construction, over what each field is given in declaration order. */
        Term built(TypeSymbol type, List<String> fields, List<Term> values) {
            return of(Shape.BUILT, new Built(type, fields), values);
        }

        /** A call to {@code operation} over its arguments — which is what a size taken of a container
         * is, so a guard's size and a clause's size meet as one value rather than as two spellings
         * that have to keep agreeing. */
        Term called(ValueName operation, List<Term> args) {
            return of(Shape.CALLED, operation, args);
        }

        /** The value one evaluation answered. Equal to itself and to nothing else, because an
         * { EvaluationId} is. */
        Term evaluated(EvaluationId where) {
            return of(Shape.EVALUATION, where, List.of());
        }

        /**
         * The call {@code operation} over {@code args} where this has built one before, and null
         * where it has not.
         *
         * <p>Recognised and not made. A reader asking what was said about a size wants the term a
         * clause already named it by; answering with a fresh one would put a term nothing has spoken
         * about where an answer was expected.
         */
        Term calledIfBuilt(ValueName operation, List<Term> args) {
            Term made = new Term(Shape.CALLED, operation, args);
            return shared.containsKey(made) ? made : null;
        }

        /** A value given to an optional position. */
        Term some(Term value) {
            return of(Shape.SOME, null, List.of(value));
        }

        /**
         * What the present optional {@code optional} holds.
         *
         * <p>What a value was built out of is what taking it apart answers: an optional written as
         * {@code Some(v)} holds {@code v}, so opening it names {@code v} and not a value of its own.
         * Stated here beside the building of it, because a constructor and what undoes it are one
         * fact and holding them apart is holding it twice.
         */
        Term held(Term optional) {
            if (optional.shape == Shape.SOME) {
                return optional.parts.get(0);
            }
            return of(Shape.HELD, null, List.of(optional));
        }

        /** What the walk {@code walk} hands its step on parameter {@code param}. */
        Term handed(Term walk, int param) {
            return of(Shape.HANDED, param, List.of(walk));
        }

        /** The empty optional. Held at the type of the position, since the absent value of one
         * optional is not the absent value of another. */
        Term none(souther.compiler.types.Type type) {
            return of(Shape.NONE, type, List.of());
        }

        /** The value the case carrying {@code carries} opens out of {@code value}. Held at the type
         * the case carries, since two cases of one union are two values. */
        Term opened(Term value, souther.compiler.types.Type carries) {
            return of(Shape.OPENED, carries, List.of(value));
        }

        /** A value opened: the scrutinee, and what each arm answers, under the cases that arm takes.
         * An arm's binding is a parameter of the arm, keyed by where it is bound as a closure's is. */
        Term matched(Term scrutinee, List<List<TypeSymbol>> cases, List<Term> arms) {
            List<Term> parts = new ArrayList<>();
            parts.add(scrutinee);
            parts.addAll(arms);
            return of(Shape.MATCHED, List.copyOf(cases), parts);
        }

        /** An attempted construction: what it builds, what its success answers, and what each of its
         * departures answers, under the clauses those departures name. */
        Term attempted(Term built, List<String> clauses, List<Term> answers) {
            List<Term> parts = new ArrayList<>();
            parts.add(built);
            parts.addAll(answers);
            return of(Shape.ATTEMPTED, List.copyOf(clauses), parts);
        }

        /** How many distinct terms this has been asked for — what a test measuring the sharing
         * reads. */
        int size() {
            return shared.size();
        }
    }

    /** The parts, for a reader that walks one. */
    List<Term> parts() {
        return parts;
    }

    /**
     * How many distinct terms this one is made of, itself among them — what the representation of it
     * costs.
     *
     * <p>Counted rather than written out. A term is a graph and not a tree: a name read twice is one
     * value read twice, so writing out what a term holds writes a part once per path to it, and a
     * chain of them doubles per link. Held once and read from two places, a link costs a link.
     */
    int distinct() {
        java.util.Set<Term> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        java.util.Deque<Term> left = new java.util.ArrayDeque<>(List.of(this));
        while (!left.isEmpty()) {
            Term term = left.pop();
            if (seen.add(term)) {
                left.addAll(term.parts);
            }
        }
        return seen.size();
    }
}
