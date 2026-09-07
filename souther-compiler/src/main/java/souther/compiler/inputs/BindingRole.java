package souther.compiler.inputs;

import souther.compiler.core.Core;

import java.util.List;
import java.util.Objects;

/**
 * What a binding is, by where it came from: the one ordering of the facts a name is answered from.
 *
 * <p>A binding can answer to more than one of these at once. A closure parameter of a walk that was
 * joined to the walk before it is what an operation handed an element on, and the join also binds it
 * to what the earlier closure made — so a reader that asked the facts in an order of its own would
 * be deciding, for itself, whether the name is the element it was handed or the value the rewrite
 * left under it. Two readers that decided differently are two accounts of what one name means, and
 * the one that answered second would be about values the first never reached.
 *
 * <p><b>So the order is here and is not a race between roads.</b> A parameter is its position; then
 * a name an operation handed an element on is that element, whatever reading it comes to; then a
 * name is what it was bound to. Each is asked only where the ones before it said nothing, and the
 * one that answers is the answer — not a candidate to be dropped for a longer road found later.
 *
 * <p>Facts about where the binding came from and not what a reader may do with them. Whether the
 * value under an alias may stand where the name stands, and whether an element's container is a
 * position, are questions asked of these rather than settled here ({@link ReadMeaning},
 * {@link InputPath}).
 *
 * <p>What an arm narrowed is not one of these. A {@code match} arm leaves a name standing for some
 * of the values its container was written with, and that is a fact about where a walk is in the tree
 * rather than about where the binding came from — read under one arm and not under the next, where
 * every fact here is as true at one read of the name as at another. Held among these, it would be
 * carried by an ordering that says nothing about it.
 */
sealed interface BindingRole {

    /**
     * The binding is a parameter of the behavior, which is a place a row writes at.
     *
     * <p>Each of these carries what makes it the answer it is, and refuses to be made without it.
     * Held as a shape that may be empty, the answer "this is a parameter" and the answer "nothing
     * here says where it came from" would be two values a reader cannot tell apart — which is the
     * absence this ordering exists to have none of.
     */
    record Root(TermPath path) implements BindingRole {

        public Root {
            Objects.requireNonNull(path, "a parameter is the name of a position");
        }
    }

    /**
     * An operation of the language handed the binding an element of {@code container}.
     *
     * <p>Read from what was recorded where the operation still stood. The tree that runs has no
     * operation left in it, so nothing in it says which container an element came from, and a walk
     * that worked it out from the shape a rewrite happens to leave would answer for whichever shapes
     * that rewrite currently produces.
     */
    record Element(Core container) implements BindingRole {

        public Element {
            Objects.requireNonNull(container, "an element came from a container");
        }
    }

    /**
     * Operations of the language handed the binding an element of each of {@code containers}.
     *
     * <p>One block handed to two walks. The binding has one name and takes an element of a
     * different container on each run, so it is an element and no one container's — and both halves
     * of that are said here, because a reader told only the second would have a name that came from
     * nowhere and a reader told only the first would be sent to whichever container turned up
     * first.
     */
    record ElementOfSeveral(List<Core> containers) implements BindingRole {

        public ElementOfSeveral {
            containers = List.copyOf(containers);
            if (containers.size() < 2) {
                throw new IllegalArgumentException(
                        "an element of several came from more than one container");
            }
        }
    }

    /**
     * The binding was given {@code value} on the way to where it is read.
     *
     * <p>The value bound on the way here and not the one the body bound anywhere. What a binding
     * holds is also recorded over the whole body, which is what a walk after a container's elements
     * reaches for ({@link BindingEnvironment#heldAnywhereBy}); that is a different question, asked
     * where the way to a container does not run through the way to here, and answering this one with
     * it would give a name the meaning of a binding the reader never passed.
     *
     * <p>The expression alone. What it comes to is read in the environment the reader is holding,
     * and an environment that answered with a value already paired to a reading would be reachable
     * from the reading built on it — after which what a name means could be asked of the answer to
     * what a name means.
     */
    record Alias(Core value) implements BindingRole {

        public Alias {
            Objects.requireNonNull(value, "a name was given something");
        }
    }

    /** Nothing here says where the binding came from. */
    record Unknown() implements BindingRole {}
}
