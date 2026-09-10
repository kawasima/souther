package souther.compiler.types;

import souther.compiler.Reserved;
import souther.compiler.hash.SaysWhatStandsForIt;
import souther.compiler.hash.ValueHash;

/**
 * A data type's identity: which declaration this is, told apart by who declared it.
 *
 * <p>Every {@link Type.Ref} carries one, so a name that reached the checker has already been
 * resolved. What the source wrote — a bare {@code 金額}, a qualified {@code probe.b.金額}, or an
 * alias {@code B.金額} — is settled during resolution and does not survive into the type.
 *
 * <p>Two ways a declaration comes to be, and the sum is over exactly that. {@link AtModule} is one a
 * module wrote, and a module and a name is what tells two of those apart: two modules may both
 * declare {@code 金額}, and only the pair says which. {@link OfLanguage} is one no module wrote —
 * a primitive standing in a union, {@code Option}'s cases, the error cases a division answers — and
 * there is no module to name, so none is named. What stood here before was a module string either
 * way, which meant every declaration the language gives had to be filed under a module nothing
 * declares: {@code souther} for a primitive, {@code souther.runtime} for the rest. Choosing that
 * string was choosing a namespace, and the one chosen was a JVM package.
 *
 * <p>Nothing here says what any of this is called on a machine. {@code jvm.SoutherJvmAbi} is where
 * that is asked and answered, and it is the only place that may.
 */
public sealed interface TypeSymbol extends Comparable<TypeSymbol> {

    /** The name this is written under. */
    String name();

    /**
     * A declaration a module wrote, at the address that says which.
     *
     * <p>{@link TypeKey} is structural and anything holding two strings has one; this is that key
     * where it stands for the declaration in the compiler's own reasoning, and one is minted from
     * the other only in {@link TypeSymbols}.
     */
    final class AtModule implements TypeSymbol, SaysWhatStandsForIt {

        private final TypeKey key;

        /**
         * Closed. An identity comes from {@link TypeSymbols} and nowhere else: a caller that could
         * build one from a key is a caller that could arrive at an identity without a declaration
         * world having handed it one, which is what issues #464, #696 and #700 each were.
         *
         * <p>Which is also why this is not a record. The other two cases are — a {@link Type.Prim}
         * and a {@link LanguageCaseId} are closed sets, and there is no wrong one to fabricate —
         * but a record's canonical constructor is as public as the record, and this one carries two
         * strings that anything could supply.
         */
        AtModule(TypeKey key) {
            if (key == null) {
                throw new IllegalArgumentException("an address is what this is");
            }
            this.key = key;
        }

        /** Which declaration this is, written down. */
        public TypeKey key() {
            return key;
        }

        /** The module that declares it. */
        public String module() {
            return key.module();
        }

        @Override
        public String name() {
            return key.name();
        }

        /** The address, which is the whole of what tells one of these from another. */
        @Override
        public TypeKey standsFor() {
            return key;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof AtModule at && key.equals(at.key);
        }

        @Override
        public int hashCode() {
            return ValueHash.ofOnePart(AtModule.class, key.hashCode());
        }

        @Override
        public String toString() {
            return key.qualified();
        }
    }

    /** A declaration the language gives and no module does. */
    sealed interface OfLanguage extends TypeSymbol permits Primitive, LanguageCase {}

    /**
     * A primitive standing as a case of a union: {@code Int} in {@code Int | DivisionByZero}.
     *
     * <p>Its identity is the primitive, and not a name minted from the primitive's spelling. A
     * spelling written down beside {@link Type.Prim} would be a second table of the same nine words,
     * and recovering the primitive from one was already written as the inverse of writing it out for
     * exactly that reason.
     */
    record Primitive(Type.Prim primitive) implements OfLanguage {

        public Primitive {
            if (primitive == null) {
                throw new IllegalArgumentException("a primitive is what this is");
            }
        }

        @Override
        public String name() {
            return primitive.shown();
        }

        @Override
        public String toString() {
            return name();
        }
    }

    /** One of the cases the language gives, from the closed list of them. */
    record LanguageCase(LanguageCaseId id) implements OfLanguage {

        public LanguageCase {
            if (id == null) {
                throw new IllegalArgumentException("a case of the language is what this is");
            }
        }

        @Override
        public String name() {
            return id.spelling();
        }

        @Override
        public String toString() {
            return name();
        }
    }

    default boolean isPrimitive() {
        return this instanceof Primitive;
    }

    /**
     * Whether the language declares this rather than a module of some compilation.
     *
     * <p>The primitives, {@code Option}'s two cases, the error cases beside them, and the data the
     * standard library declares — together and as one answer. Nothing publishes any of them: there
     * is no {@code souther/$Module.class} for a path to carry, so a reader that goes looking for
     * the module behind one is asking after an artifact that cannot exist, and the reader that did
     * was told to add a dependency nobody ships (#1049).
     *
     * <p>Two shapes and one question. What the language gives has no module at all and answers by
     * being what it is. What the library declares has one, and it is a module of the reserved
     * namespace — {@code souther.decimal} declares {@code RoundingMode}, and no compilation
     * declares it. {@link Reserved#isNamespace} is where the reserved namespace is written down and
     * this is the only place in the compiler that reads it of a declaration.
     *
     * <p>Not the same question as which class carries it. That one is
     * {@code jvm.SoutherJvmAbi}'s, and the answers differ: this backend ships
     * {@code souther.decimal}'s {@code RoundingMode} as {@code souther.runtime.RoundingMode}.
     */
    default boolean isDeclaredByLanguage() {
        return !(this instanceof AtModule at) || Reserved.isNamespace(at.module());
    }

    /** The primitive this denotes, or null where it denotes none. */
    default Type.Prim primitiveKind() {
        return this instanceof Primitive p ? p.primitive() : null;
    }

    /**
     * By the name written, and then by what tells two of that name apart.
     *
     * <p>The name first, because that is what a reader of an ordered list is looking down. Two
     * modules declaring one spelling are told apart by the module, as they always were; a module's
     * and the language's are told apart by which they are, there being no module on one side to
     * compare.
     */
    @Override
    default int compareTo(TypeSymbol other) {
        int byName = name().compareTo(other.name());
        if (byName != 0) {
            return byName;
        }
        if (this instanceof AtModule mine && other instanceof AtModule theirs) {
            return mine.module().compareTo(theirs.module());
        }
        return Integer.compare(rank(this), rank(other));
    }

    private static int rank(TypeSymbol type) {
        return switch (type) {
            case AtModule _ -> 0;
            case Primitive _ -> 1;
            case LanguageCase _ -> 2;
        };
    }

    /** A primitive case name ({@code Int}) as it appears in a union.
     *
     *  @throws IllegalArgumentException where {@code name} spells no primitive */
    static TypeSymbol primitive(String name) {
        Type.Prim prim = Type.Prim.named(name);
        if (prim == null) {
            throw new IllegalArgumentException("`" + name + "` is no primitive");
        }
        return new Primitive(prim);
    }

    /** The same, minted from the primitive itself. */
    static TypeSymbol primitive(Type.Prim prim) {
        return new Primitive(prim);
    }

    /** {@code Some} / {@code None}: written in a match arm over an {@code Option}, declared by no
     * module. They name no class: an Option match dispatches on the runtime Option classes, never on
     * the arm's own name. */
    TypeSymbol SOME = new LanguageCase(LanguageCaseId.SOME);

    /** Option's empty case, on the same terms as {@link #SOME}. */
    TypeSymbol NONE = new LanguageCase(LanguageCaseId.NONE);

    /** Option's case of that spelling, or {@code null} for any other. */
    static TypeSymbol optionCase(String written) {
        return switch (written) {
            case "Some" -> SOME;
            case "None" -> NONE;
            default -> null;
        };
    }
}
