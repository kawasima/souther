package souther.compiler.types;

/**
 * What caused a function reference to be expanded into the block that applies it.
 *
 * <p>A name written where a value goes is the function it names, and what a body holds there is the
 * block taking as many parameters as that function takes and applying it to them. The block is a
 * pass's, and the reference that made it necessary is what tells one such expansion from another.
 *
 * <p><b>The cause and not what the expansion produced.</b> The parameters the block is written with
 * are minted by that expansion, so naming it by one of them would be naming a thing by its own
 * output — and the counter those names are spelled from is the one this exists to stop being an
 * identity. What is here is the reference, which was already there before anything was expanded.
 *
 * <p>Two arms, which is what a reference in that position can be. Both were measured over the
 * models this repository carries; a third would be a generation whose meaning is worth a look before
 * it gets an arm.
 */
public sealed interface EtaOrigin {

    /**
     * A reference to something a module declares.
     *
     * <p>Told by the reference and not by how it is spelled: a helper written bare and the same
     * helper written qualified are one reference, and a pass may have respelled it on the way here.
     * Told by the reference and not by what it reaches, either — two occurrences of one name reach
     * the same declaration and are two references, and expanding each of them writes a block of its
     * own.
     *
     * <p>Whoever wrote the reference. An author's is one this source counted; a pass writing a name
     * of its own where the language has no syntax for what it means wrote a reference too, and
     * expanding that one as a value writes a block just the same. Held as the author's alone, this
     * would be a slot a pass's name could not be put in — and what stood in for the missing arm was
     * a reader refusing halfway down, which says nothing about whether such a name is impossible or
     * merely has not turned up.
     */
    record Declaration(ReferenceOrigin reference) implements EtaOrigin {

        public Declaration {
            if (reference == null) {
                throw new IllegalArgumentException(
                        "a name reaching a declaration is some reference of it");
            }
        }
    }

    /**
     * A read of something bound in the body, which a pass put there.
     *
     * <p>No source wrote it, so there is no reference to name it by — and none is wanted: what such
     * a name reads is one binding, and a binding is already a thing this compiler tells from every
     * other ({@link BindingId}).
     */
    record Bound(BindingId binding) implements EtaOrigin {

        public Bound {
            if (binding == null) {
                throw new IllegalArgumentException("a read of a binding reads some binding");
            }
        }
    }
}
