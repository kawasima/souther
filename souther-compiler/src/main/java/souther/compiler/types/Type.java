package souther.compiler.types;

/**
 * The Souther value types. Either a primitive ({@code Int}/{@code String}/{@code Bool})
 * or a reference to a named data type. {@code Type.INT} etc. remain usable as constants.
 */
public sealed interface Type permits Type.Leaf, Type.Compound {

    /**
     * A type that holds no type inside it, where a walk over the tree of types ends. A {@link Ref}
     * and a {@link Union} hold names rather than types, so a reader after names still has something
     * to read at one; a reader after types has not.
     *
     * <p>Written here rather than as a {@code default} arm in each walk. What "there is nothing
     * inside this one" means is a fact about the constructor, and stating it once is what lets a
     * constructor added later be accounted for at its declaration instead of at every walk that
     * would have swallowed it.
     */
    sealed interface Leaf extends Type
            permits Prim, Ref, Open, Nothing, Never, Erroneous, Union {}

    /**
     * A type built out of other types: what a collection holds, what a map keys and holds, what a
     * tuple carries, what a function takes and answers.
     *
     * <p>{@link #mapChildren} and {@link #forEachChild} are exhaustive over this and carry no
     * {@code default}, so a constructor added here stops the build at the one place that says which
     * positions it holds.
     */
    sealed interface Compound extends Type
            permits ListOf, SetOf, OptionOf, MapOf, TupleOf, FnOf {}

    /**
     * A type that stands for another rather than being one. There are two, and they differ in who
     * gets to say what it stands for: a {@link Var} is a declaration's, and every use of that
     * declaration must hold for it; a {@link MetaVar} is one application's, and that application
     * decides it. Wherever the question is only "does this stand for something else", both answer
     * yes, and a reader asking it says so by naming this.
     */
    sealed interface Open extends Leaf permits Var, MetaVar {}

    enum Prim implements Leaf {
        INT, STRING, BOOL, DECIMAL, DATE, TIME, DATETIME, INSTANT, RAW;

        /** How this primitive is written. One table, read forwards by everything that shows a type
         *  and backwards by {@link TypeSymbol#primitiveKind()} — a primitive case name is minted from
         *  this spelling, so recovering the primitive has to read the same one and not a copy. */
        public String shown() {
            return switch (this) {
                case INT -> "Int";
                case STRING -> "String";
                case BOOL -> "Bool";
                case DECIMAL -> "Decimal";
                case DATE -> "Date";
                case TIME -> "Time";
                case DATETIME -> "DateTime";
                case INSTANT -> "Instant";
                case RAW -> "Raw";
            };
        }

        /** The primitive written {@code spelling}, or null where none is. The backwards reading of
         *  {@link #shown()}, here so that turning a written name into a primitive goes through the
         *  same table that writes one out and not through a second list of the spellings. */
        public static Prim named(String spelling) {
            for (Prim p : values()) {
                if (p.shown().equals(spelling)) {
                    return p;
                }
            }
            return null;
        }

        /** Whether this is one of the temporals — the primitives a written form spells as ISO 8601
         *  text and a boundary carries as that text. Asked here so that adding a primitive is where
         *  the question gets answered, rather than at each reader that compares against a few names. */
        public boolean temporal() {
            return switch (this) {
                case DATE, TIME, DATETIME, INSTANT -> true;
                case INT, STRING, BOOL, DECIMAL, RAW -> false;
            };
        }
    }

    /** The element type of the empty-list literal {@code []} (ADR-0028): a bottom that unifies with
     * any element type. It only ever appears as {@code ListOf(NOTHING)} — the empty list — whose type
     * is fixed by context ({@code ++}, an {@code if}/{@code match} case, a {@code fold} seed, or the
     * {@code List<T>} a position expects). It never reaches codegen: an empty list is element-agnostic
     * at runtime. */
    record Nothing() implements Leaf {}

    /**
     * The type of an expression that answers no value: {@code unreachable "reason"}, and nothing
     * else. It fits every expected type, and joining it with a type yields that type, so an arm
     * that cannot arise leaves the {@code match} typed by the arms that can.
     *
     * <p>It is not {@link Nothing}. That one is an inference result waiting to be filled in — the
     * element of {@code []} — and a pass that meets it asks what the context makes it. This one is
     * an answer: nothing arrives here.
     *
     * <p>Fitting every type is not being one. The code around the abort is still emitted and still
     * reads a shape, so where this survives to codegen — the position stated no type and no branch
     * beside it supplied one — that position is refused rather than emitted.
     */
    record Never() implements Leaf {}

    /**
     * A type the compiler could not work out, and has already said so about.
     *
     * <p>It exists so that being wrong about one thing does not stop the compiler saying anything
     * about the rest. A name that denotes nothing used to end the module: everything after it went
     * unchecked, and an editor could not answer what any name in the file meant, which is when an
     * author most wants to ask. The name denotes this instead, the tree stays whole, and the passes
     * below carry on.
     *
     * <p>It absorbs. Every comparison with it holds and every join with it yields it, so the one
     * mistake is reported once rather than as a mismatch at each place the value flowed. That is what
     * makes it different from {@link Nothing}, which is a real inference result — the element type of
     * {@code []} — and must not silence anything.
     *
     * <p>It never reaches codegen. There is no bytecode for a type nobody could name, so the one
     * place that gates emitting a module refuses a tree that holds one.
     */
    record Erroneous() implements Leaf {}

    /**
     * A type variable ({@code 'a}). It stands for any type; a non-recursive helper carrying one is
     * monomorphised by inline expansion, so the variable is resolved to the concrete argument type at
     * each call site.
     *
     * <p>{@code inferred} is whether the compiler made it. A variable the shipped core wrote is not:
     * its spelling is what an author of core reads. One the compiler minted is: it stands for a value
     * a use decides, and its spelling is an internal name that says nothing to anyone, so nothing
     * shows it.
     */
    record Var(String name, boolean inferred) implements Open {}

    /**
     * A variable one application of a signature left open, waiting to be decided by that
     * application. It is not {@link Var}: that one stands for any type and may not be rewritten,
     * because it is what a declaration wrote and every use of the declaration must hold for it.
     * This one stands for the one type a single application settles on, and is rewritten as soon as
     * something says what that is.
     *
     * <p>Identity is the pair, not the spelling. {@code application} is the expansion of one call,
     * and {@code spelling} is the variable that call's callee wrote, which is unique within a
     * signature. So two occurrences of {@code 'a} in one signature are one variable at one call, and
     * two calls of that signature leave two — which is the rule this exists to state.
     *
     * <p>It lives no longer than the elaboration of the expansion that made it. Every one is either
     * decided there or read as {@link Nothing} at its boundary (ADR-0028), so nothing below sees one
     * and no answer the compiler stores holds one.
     */
    record MetaVar(BindingOwner application, String spelling) implements Open {

        public MetaVar {
            if (application == null || spelling == null) {
                throw new IllegalArgumentException(
                        "a meta variable needs an application and a spelling: " + application + " "
                                + spelling);
            }
        }

        @Override
        public String toString() {
            return spelling + "@" + application;
        }
    }

    /** A reference to a named data type (product or sum). */
    record Ref(TypeSymbol name) implements Leaf {
        public Ref {
            if (name == null) {
                throw new IllegalArgumentException("a type reference needs a resolved name");
            }
        }
    }

    /**
     * What a container of this type hands one at a time, or null where it holds no such thing: a
     * list's or set's element, a map's value, an option's payload, and a function's first parameter
     * where what is held is the closure itself.
     *
     * <p>A fact about the type, so it is here. Held inside a reader, every other reader that had to
     * say what a container holds either asked that one or answered for itself.
     *
     * <p>A map's key is the other parameter a closure is handed and is not what this answers.
     */
    static Type elementOf(Type t) {
        return switch (t) {
            case ListOf list -> list.element();
            case SetOf set -> set.element();
            case MapOf map -> map.value();
            case OptionOf option -> option.element();
            case FnOf fn when !fn.params().isEmpty() -> fn.params().get(0);
            case null, default -> null;
        };
    }

    /**
     * What a container of this type holds many of, or null where it is no container: a list's or a
     * set's element, and a map's value.
     *
     * <p>Beside {@link #elementOf} and narrower than it. That one answers what is handed out one at
     * a time, which an option's payload and a closure's first parameter also are; this one answers
     * what a construction holds many of, which neither of them is. A reader that asks the wider
     * question where it means this one is told an {@code Option<Int>} has elements, and goes on to
     * say what became of them.
     *
     * <p>The element and not a yes. Whether a type is a container and what it holds are wanted
     * together wherever either is wanted at all, and asking them separately is two answers that can
     * come apart — which is what a check testing for the constructor here and reading the element
     * over there had.
     */
    static Type elementOfAContainer(Type t) {
        return switch (t) {
            case ListOf _, SetOf _, MapOf _ -> elementOf(t);
            case null, default -> null;
        };
    }

    /** A homogeneous list of {@code element}. */
    record ListOf(Type element) implements Compound {}

    /** A {@code Map<key, value>}. A key that crosses is one ADR-0040 admits;
     * at runtime the map is keyed by that value (value equality, ADR-0009) and its external
     * representation is a JSON object whose string keys are the key's bare form. */
    record MapOf(Type key, Type value) implements Compound {}

    /** A {@code Set<element>} — an unordered collection with no duplicate elements, compared by value
     * equality (ADR-0009). Its external representation is a JSON array, deduplicated on decode. */
    record SetOf(Type element) implements Compound {}

    /** An optional value {@code Option<element>} — the desugaring of a {@code T?} field (spec §optional). */
    record OptionOf(Type element) implements Compound {}

    /** An anonymous union of data types (a behavior's multi-success output). */
    record Union(java.util.Set<TypeSymbol> members) implements Leaf {}

    /** A function type {@code (params...) -> result}. Written only on a helper {@code fn}'s
     * parameter (spec §fn-declaration); a value of this type is never stored in a data field, so it
     * never crosses a codec boundary. */
    record FnOf(java.util.List<Type> params, Type result) implements Compound {}

    /** A tuple {@code (A, B, ...)} of two or more element types (ADR-0036). Expression-level only —
     * like {@link FnOf}, a tuple is never stored in a data field or a behavior's I/O, so it never
     * crosses a codec boundary; it only carries several values through a computation. */
    record TupleOf(java.util.List<Type> elements) implements Compound {}

    /** The one {@link Erroneous}: it carries nothing, so there is nothing to tell two apart. */
    Type ERRONEOUS = new Erroneous();

    Type INT = Prim.INT;
    Type STRING = Prim.STRING;
    Type BOOL = Prim.BOOL;
    Type DECIMAL = Prim.DECIMAL;
    Type DATE = Prim.DATE;
    /** A local time of day, to the second (spec §temporal-literal). What a {@code DateTime} holds
     * beside its {@code Date}, and what a model that names an opening time holds on its own. */
    Type TIME = Prim.TIME;
    Type DATETIME = Prim.DATETIME;
    /** A moment on the timeline, to the nanosecond. It carries what an outside timestamp said and
     * nothing a model reads: naming its year or its hour needs a zone, and the language names no
     * zone (spec §primitives). A model compares two, keys by one, and hands one to a behavior with
     * no implementation to get a {@code DateTime} back (spec §injected-behavior). */
    Type INSTANT = Prim.INSTANT;
    /** The external (encoded) representation type: an encoder's raw output at a railway's edge,
     * unioned with propagated error cases as the case {@code "Raw"} (spec §case-propagation). Reserved — no stage
     * produces it yet; {@code >->} composes behaviors, not codecs (spec §sequential-composition). */
    Type RAW = Prim.RAW;
    /** The bottom element type of the empty-list literal (see {@link Nothing}). */
    Type NOTHING = new Nothing();
    /** The type of the empty-list literal {@code []}: a list whose element type is not yet fixed. */
    Type EMPTY_LIST = new ListOf(NOTHING);
    /** The type of {@code unreachable "reason"} (see {@link Never}). */
    Type NEVER = new Never();

    static Type ref(TypeSymbol name) {
        return new Ref(name);
    }

    static Type list(Type element) {
        return new ListOf(element);
    }

    static Type option(Type element) {
        return new OptionOf(element);
    }

    /** A string-keyed map (the common case): {@code MapOf(STRING, value)}. */
    static Type map(Type value) {
        return new MapOf(STRING, value);
    }

    /** A map with an explicit key type. */
    static Type map(Type key, Type value) {
        return new MapOf(key, value);
    }

    static Type set(Type element) {
        return new SetOf(element);
    }

    static Type union(java.util.Set<TypeSymbol> members) {
        return new Union(members);
    }

    static Type fn(java.util.List<Type> params, Type result) {
        return new FnOf(params, result);
    }

    static Type tuple(java.util.List<Type> elements) {
        return new TupleOf(elements);
    }

    /** A variable as the core wrote it. */
    static Type var(String name) {
        return new Var(name, false);
    }

    /** A variable the compiler minted, standing for a value each use of the declaration decides. */
    static Type inferredVar(String name) {
        return new Var(name, true);
    }

    /**
     * {@code c} with each position that holds a type replaced by what {@code at} answers for it, or
     * {@code c} itself where every position answered what it was given, so a walk that only reads
     * allocates nothing.
     *
     * <p>This is the one place that says which positions a compound holds. Both {@link #mapChildren}
     * and {@link #forEachChild} are derived from it, so a position a constructor gains later is
     * written once and neither walk can be left behind. Being exhaustive over {@link Compound}, a
     * constructor added later stops the build here, which is the one place it has to be accounted
     * for.
     */
    private static Type atChildren(Compound c, java.util.function.UnaryOperator<Type> at) {
        return switch (c) {
            case ListOf l -> {
                Type element = at.apply(l.element());
                yield element == l.element() ? l : new ListOf(element);
            }
            case SetOf s -> {
                Type element = at.apply(s.element());
                yield element == s.element() ? s : new SetOf(element);
            }
            case OptionOf o -> {
                Type element = at.apply(o.element());
                yield element == o.element() ? o : new OptionOf(element);
            }
            case MapOf m -> {
                Type key = at.apply(m.key());
                Type value = at.apply(m.value());
                yield key == m.key() && value == m.value() ? m : new MapOf(key, value);
            }
            case TupleOf tu -> {
                java.util.List<Type> elements = each(tu.elements(), at);
                yield elements == tu.elements() ? tu : new TupleOf(elements);
            }
            case FnOf f -> {
                java.util.List<Type> params = each(f.params(), at);
                Type result = at.apply(f.result());
                yield params == f.params() && result == f.result() ? f : new FnOf(params, result);
            }
        };
    }

    /** {@code xs} with {@code at} applied to each, or {@code xs} itself where none of them changed. */
    private static java.util.List<Type> each(java.util.List<Type> xs,
            java.util.function.UnaryOperator<Type> at) {
        java.util.List<Type> out = null;
        for (int i = 0; i < xs.size(); i++) {
            Type before = xs.get(i);
            Type after = at.apply(before);
            if (out == null && after != before) {
                out = new java.util.ArrayList<>(xs.subList(0, i));
            }
            if (out != null) {
                out.add(after);
            }
        }
        return out == null ? xs : out;
    }

    /**
     * {@code t} with each type it holds replaced by what {@code at} answers for it; a {@link Leaf}
     * is answered as it was given. The single authoritative rewrite over the tree of types, so a
     * pass that rewrites types — a substitution writing back what an application decided, a reading
     * of what it did not — writes only the position it is about and delegates the descent here.
     *
     * <p>It rewrites the direct children only. A pass that means every depth says so by passing
     * itself, which is what makes the recursion the caller's to state rather than this one's to
     * assume.
     */
    static Type mapChildren(Type t, java.util.function.UnaryOperator<Type> at) {
        return t instanceof Compound c ? atChildren(c, at) : t;
    }

    /**
     * Applies {@code f} to each type {@code t} directly holds (a {@link Leaf} holds none) — the
     * read-only counterpart of {@link #mapChildren}.
     */
    static void forEachChild(Type t, java.util.function.Consumer<Type> f) {
        if (t instanceof Compound c) {
            atChildren(c, child -> {
                f.accept(child);
                return child;
            });
        }
    }

    /** A user-facing rendering of {@code t} in surface syntax: {@code Int}, {@code List<Int>},
     * {@code A | B}, {@code (A, B)}, {@code T?}. Used by diagnostics; unlike the record {@code toString}
     * it reads the way the source is written. */
    static String show(Type t) {
        return show(t, java.util.Set.<String>of());
    }

    /**
     * {@code t} rendered as it is written where a type argument goes, which differs from
     * {@link #show} for an optional: {@code Option<Int>} rather than {@code Int?}. A message about a
     * type standing inside another one names it this way, so that what it names is a form the author
     * can write.
     */
    static String showInside(Type t) {
        return show(t, java.util.Set.<String>of(), true);
    }

    /**
     * Renders {@code t} for a message that also shows {@code against}. Where one name stands for two
     * types — {@code Mid} of one module beside {@code Mid} of another — both are written with their
     * module, so a mismatch does not read as {@code Mid} against {@code Mid}.
     */
    static String show(Type t, Type against) {
        java.util.Map<String, TypeSymbol> here = new java.util.HashMap<>();
        collectNames(t, here);
        java.util.Map<String, TypeSymbol> there = new java.util.HashMap<>();
        collectNames(against, there);
        java.util.Set<String> ambiguous = new java.util.HashSet<>();
        for (java.util.Map.Entry<String, TypeSymbol> e : here.entrySet()) {
            TypeSymbol other = there.get(e.getKey());
            if (other != null && !other.equals(e.getValue())) {
                ambiguous.add(e.getKey());
            }
        }
        return show(t, ambiguous);
    }

    /**
     * Every name {@code t} mentions, in the order the type writes them.
     *
     * <p>Every constructor is named here rather than answered by {@link Leaf} and {@link Compound},
     * because what this asks is not what those divide types by. They say whether one holds a type;
     * this asks whether one holds a name, and {@link Ref} and {@link Union} are leaves that do. A
     * constructor added later has to answer for itself, so it stops the build here. Only the descent
     * is delegated: which positions a compound holds is still {@link #atChildren}'s to say.
     *
     * <p>The one walk over the names of a type. Rendering asks it which names it will have to spell
     * and a reader asks it which it will have to reach; a second walk written beside it would be a
     * second answer to which names a type mentions, and the two would part company at whichever
     * constructor was added after one of them.
     */
    static void forEachName(Type t, java.util.function.Consumer<TypeSymbol> f) {
        switch (t) {
            case Ref r -> f.accept(r.name());
            case Union u -> u.members().forEach(f);
            case ListOf _, SetOf _, OptionOf _, MapOf _, TupleOf _, FnOf _ ->
                    forEachChild(t, held -> forEachName(held, f));
            case Prim _, Var _, MetaVar _, Nothing _, Never _, Erroneous _ -> { }
        }
    }

    /** Every name {@code t} mentions, by the simple name it would be written under. A simple name
     * that covers two different types in one type is already ambiguous within it, and is recorded
     * under whichever came first — the pair rendering only has to tell the two sides apart. */
    private static void collectNames(Type t, java.util.Map<String, TypeSymbol> out) {
        forEachName(t, name -> out.putIfAbsent(name.name(), name));
    }

    /** {@code name}, written with its module when the simple name is one of {@code qualify}. */
    private static String showName(TypeSymbol name, java.util.Set<String> qualify) {
        // What the language gives has no module to write, and two of them never share a spelling,
        // so a name of one is never the ambiguous one this is disambiguating.
        return qualify.contains(name.name()) && name instanceof TypeSymbol.AtModule at
                ? at.key().qualified() : name.name();
    }

    private static String show(Type t, java.util.Set<String> qualify) {
        return show(t, qualify, false);
    }

    private static String show(Type t, java.util.Set<String> qualify, boolean inside) {
        return showAs(t, name -> showName(name, qualify), inside);
    }

    /**
     * {@code t} in surface syntax, with every name it mentions written as {@code naming} spells it.
     *
     * <p>The one renderer. What differs between a message and a published signature is how a name is
     * spelled and nothing else, so that is the parameter: a caller with a spelling rule of its own
     * says it here rather than walking the type a second time, which is what a second renderer would
     * have to do and would get subtly differently.
     *
     * <p>{@code inside} says whether {@code t} stands in a type argument or a tuple's member rather
     * than as a whole type, which decides how an optional is spelled: {@code ?} marks where an
     * optional is made and is written on a whole type only, so a nested one is named
     * {@code Option<T>} (spec {@code [#an-optional-is-not-written-inside-another-type]}). A message
     * that showed {@code List<T?>} or {@code Int??} would name a form the author cannot write.
     *
     * <p>A function type's parameter and its result are whole types, so they are not inside one.
     */
    static String showAs(Type t, java.util.function.Function<TypeSymbol, String> naming,
                         boolean inside) {
        return switch (t) {
            case Prim p -> p.shown();
            case Ref r -> naming.apply(r.name());
            // A variable the core wrote is shown as the core wrote it; the name carries the `'`
            // (`'a`), so it is not added twice. One the compiler minted is shown as `_`: its spelling
            // is an internal name an author never wrote and could not write, so it says nothing to
            // the reader, while what is open about the type is what they need.
            case Var v -> v.inferred() ? "_"
                    : v.name().startsWith("'") ? v.name() : "'" + v.name();
            // What an application has not decided yet. Shown as what is open about the type, like
            // every other variable the author did not write: the call it belongs to says nothing to
            // someone reading their own program.
            case MetaVar _ -> "_";
            case Nothing _ -> "_";
            case Never _ -> "Never";
            // An error type should not reach a message: it absorbs, so nothing compares against it
            // and finds a mismatch to describe. If one does, say what it is rather than a shape the
            // author could go looking for.
            case Erroneous _ -> "?";
            case ListOf l -> "List<" + showAs(l.element(), naming, true) + ">";
            case SetOf s -> "Set<" + showAs(s.element(), naming, true) + ">";
            case OptionOf o -> inside
                    ? "Option<" + showAs(o.element(), naming, true) + ">"
                    : showAs(o.element(), naming, true) + "?";
            case MapOf m -> "Map<" + showAs(m.key(), naming, true) + ", "
                    + showAs(m.value(), naming, true) + ">";
            // A set, so what is shown is settled here and not by whoever built it: two writings of
            // one union are one value, and a renderer walking the members would be showing which
            // container the value was made in.
            case Union u -> CanonicalNameOrder.shown(u.members()).stream().map(naming)
                    .collect(java.util.stream.Collectors.joining(" | "));
            case TupleOf tu -> tu.elements().stream().map(e -> showAs(e, naming, true))
                    .collect(java.util.stream.Collectors.joining(", ", "(", ")"));
            case FnOf f -> f.params().stream().map(p -> showAs(p, naming, false))
                    .collect(java.util.stream.Collectors.joining(", ", "(", ")"))
                    + " -> " + showAs(f.result(), naming, false);
        };
    }

    /** Whether {@code t}, or any type nested inside it, satisfies {@code p}. Tests {@code t} itself
     * first, then descends through {@link #forEachChild}, so it reaches every position a rewrite
     * would write and no other. A {@link Leaf} ends the walk — a {@code Union}'s members are names,
     * not nested types. The single tree walk both "contains a tuple" and "still carries the
     * empty-collection bottom" are expressed over. */
    static boolean mentions(Type t, java.util.function.Predicate<Type> p) {
        if (p.test(t)) {
            return true;
        }
        boolean[] found = { false };
        forEachChild(t, held -> {
            if (!found[0] && mentions(held, p)) {
                found[0] = true;
            }
        });
        return found[0];
    }
}
