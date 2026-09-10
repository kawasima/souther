package souther.compiler.types;

import souther.compiler.hash.KeepsTheNumberItIsAskedFor;
import souther.compiler.hash.ValueHash;

/**
 * What a binding belongs to — the definition whose text introduced it, or the copy of a body that a
 * pass placed inside one.
 *
 * <p>An owner is named by what already identifies a definition across the whole compiler: the
 * declaring module and the name declared there, which is what {@link TypeSymbol} is for a type. None of
 * them is a bare spelling, so neither is a {@link BindingId}: two modules that both declare
 * {@code f} own different bindings, and saying so needs no comparison of text.
 *
 * <p>The interface is sealed and the switches over it carry no {@code default}, so a case added here
 * is a compile error at every place that reads one.
 */
public sealed interface BindingOwner {

    /** The module that declares what this belongs to. A copy is owned where it was placed, so it
     * answers with the module that reads it rather than the one the body was written in. */
    default String module() {
        return switch (this) {
            case OfValue v -> v.module();
            case OfSignature s -> s.behavior().module();
            case OfData d -> d.declared().module();
            case OfFields f -> f.declared().module();
            case Expansion e -> e.within().module();
            case Synthesized s -> s.within().module();
        };
    }

    /**
     * The body of a value definition: a module's own {@code let}, or a behavior's implementation.
     *
     * <p>Which of the two it is says nothing about identity, because a module cannot declare both
     * under one name — a {@code let} whose name a behavior declares <em>is</em> that behavior's
     * implementation. So the pair names the definition, as {@link TypeSymbol} names a declared type.
     */
    record OfValue(String module, String name) implements BindingOwner {

        public OfValue {
            if (module == null || name == null) {
                throw new IllegalArgumentException("module and name are required: " + module + "."
                        + name);
            }
        }

        @Override
        public String toString() {
            return module + "." + name;
        }
    }

    /** The parameter and answer bindings written by a behavior declaration. */
    record OfSignature(ValueName.Behavior behavior) implements BindingOwner {
        @Override
        public String toString() {
            return behavior + ".signature";
        }
    }

    /**
     * The fields of a data declaration, which its own invariant reads as bindings.
     *
     * <p>Apart from {@link OfData} because these are not numbered by reading the declaration's text:
     * a field spread in from elsewhere is bound here too, and the declaration it came from may not
     * have been resolved. They are numbered by the one walk that says what fields a declaration has,
     * so the pass that resolves the invariant and the pass that emits it agree without either
     * repeating the other.
     */
    record OfFields(TypeSymbol.AtModule declared) implements BindingOwner {

        @Override
        public String toString() {
            return declared + ".fields";
        }
    }

    /** What a data declaration writes for itself: an invariant, a decoder, an encoder. */
    record OfData(TypeSymbol.AtModule declared) implements BindingOwner {

        @Override
        public String toString() {
            return declared.toString();
        }
    }

    /**
     * A copy of {@code expanded}'s body, placed inside {@code within}: the bindings one expansion of
     * one call writes.
     *
     * <p>Expanding a helper puts a second copy of its bindings into one body, so the copy owns them
     * rather than the definition they were written in — otherwise two copies of one {@code let}
     * would answer as one binding.
     *
     * <p>Named by what it is an expansion of and not by how many were met before it. Which calls a
     * pass expands is what the policy it runs under decides — the tree a backend emits has the
     * language's own operations expanded into what they do, and the tree an analysis reads has them
     * standing — so a counter over the expansions of one body runs differently in the two, and a
     * module helper expanded in both would own different bindings in each. What is here is what the
     * application says about itself instead, which is settled where that application is written and
     * so before either tree exists.
     *
     * <p>And only an application that can be told from every other of its kind
     * ({@link ApplicationOrigin.Identified}). An expansion is a thing bindings belong to, so two of
     * them must be two — an application carrying no more than why it is here would put the bindings
     * of two expansions under one owner, and nothing afterwards would say so.
     *
     * <p>{@code expanded} beside the site, because a call site may come to apply something else: the
     * same characters calling another helper are the same reference and another expansion, and what
     * a binding belongs to has moved.
     *
     * <p>And {@code within}, which is what tells copies apart. One call written inside a helper
     * that is itself expanded twice is one site and two expansions, and the two are inside different
     * owners.
     *
     * <p><b>Its number is worked out when it is made and kept.</b> What it stands inside is one of
     * these too, so working one out walks every copy above it and the call each was expanded at.
     * That walk answers the same thing every time, and an owner is asked its number once for every
     * name filed under a binding it owns.
     */
    final class Expansion implements BindingOwner, KeepsTheNumberItIsAskedFor {

        /** The whole of what one expansion is, read by its equality, by its number and by the walk
         *  that proves the number is taken from values. */
        record Parts(BindingOwner within, ValueName expanded, ApplicationOrigin.Identified at) {
        }

        private final Expansion.Parts parts;

        private final int hash;

        public Expansion(BindingOwner within, ValueName expanded, ApplicationOrigin.Identified at) {
            if (within == null || expanded == null || at == null) {
                throw new IllegalArgumentException(
                        "an expansion is of something, somewhere, at some call");
            }
            this.parts = new Expansion.Parts(within, expanded, at);
            this.hash = ValueHash.ofOnePart(Expansion.class, parts.hashCode());
        }

        /** What this copy stands inside. */
        public BindingOwner within() {
            return parts.within();
        }

        /** What was expanded here. */
        public ValueName expanded() {
            return parts.expanded();
        }

        /** The call it was expanded at. */
        public ApplicationOrigin.Identified at() {
            return parts.at();
        }

        @Override
        public Expansion.Parts standsFor() {
            return parts;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Expansion that && hash == that.hash && parts.equals(that.parts);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public String toString() {
            return parts.within() + "/" + parts.expanded() + "@" + parts.at();
        }
    }

    /**
     * Bindings {@code pass} made inside {@code within}, which no source wrote.
     *
     * <p>Its number is kept for the reason an expansion's is: what it stands inside is an owner,
     * and asking it for a number walks whatever is above it.
     */
    final class Synthesized implements BindingOwner, KeepsTheNumberItIsAskedFor {

        /** The whole of what one pass's bindings inside one owner are. */
        record Parts(BindingOwner within, Pass pass, int ordinal) {
        }

        private final Synthesized.Parts parts;

        private final int hash;

        public Synthesized(BindingOwner within, Pass pass, int ordinal) {
            this.parts = new Synthesized.Parts(within, pass, ordinal);
            this.hash = ValueHash.ofOnePart(Synthesized.class, parts.hashCode());
        }

        /** What these bindings stand inside. */
        public BindingOwner within() {
            return parts.within();
        }

        /** Which pass made them. */
        public Pass pass() {
            return parts.pass();
        }

        /** Which of that pass's bindings inside that owner this is. */
        public int ordinal() {
            return parts.ordinal();
        }

        @Override
        public Synthesized.Parts standsFor() {
            return parts;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Synthesized that && hash == that.hash
                    && parts.equals(that.parts);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public String toString() {
            return parts.within() + "/" + parts.pass() + "#" + parts.ordinal();
        }
    }

    /** The passes that introduce a binding of their own. Named here rather than by each pass, so
     * that what may mint one is a closed list. */
    enum Pass { DERIVER, NEWTYPE_DESUGAR, HELPER_PARAMS, INLINER, LOWER }
}
