package souther.compiler.types;

import souther.compiler.diag.QuotedFrom;

/**
 * What a source wrote a construct inside — the thing a number handed out while reading it is
 * counted within.
 *
 * <p>A construct is told from every other by a number, and a number means nothing on its own: it
 * says which construct only under something that says which constructs were being counted. Counted
 * over a whole file, the answer is a function of everything written before it there, so a
 * declaration nobody can name renumbers every construct after it — and an identity that moves for
 * an edit nothing about it can see is not one. What is counted within is written here, so the two
 * halves of an identity travel together.
 *
 * <p>The cases are not the kinds of syntax. They are what may be edited without the numbers after
 * it moving: a type declaration, a behavior's statement of itself, a definition's body, and — for
 * the two forms that several places may write for one behavior — a source's example rows and a
 * source's stand-in for it. A behavior and the definition that implements it are written under one
 * name and are two of these, because each is read on its own; so are a behavior's rows and the
 * stand-in beside them, because a row added to one is none of the other's business.
 *
 * <p>An owner of the last two says which text as well, because more than one legitimately writes
 * them for one behavior: the module's own file and every {@code examples for} file attached to it.
 * Their ordinals restart in each, so which text is part of which construct — the same three-part
 * identity {@code RowRef} carries for a row, and for the same reason. The first three do not, a
 * module holding one declaration under one name.
 *
 * <p>The text is a {@link QuotedFrom} and not a source identity, because a parse does not always
 * have one: a module read back off the module path is a text this compile cannot show, and a buffer
 * handed in on its own is a text it cannot name. Both are read by a builder like any other and both
 * may write rows, so an owner that could only be made for a file of this compile is one the builder
 * would sometimes have no way to make. It is the same answer a position carries about which text it
 * is in, asked the one way there is to ask it.
 *
 * <p>Written as the syntax says and not as a later pass settles it. Whether a {@code let} implements
 * a behavior is answered by looking for a declaration of that name, which is a question about the
 * module and not about the definition; a parse has the name and nothing else. So what is here is an
 * address among what a source wrote, and the settled forms it lines up with —
 * {@code Names.Declaration}, {@code Bodies.Stated}, {@code Bodies.LoweredBody} — are answers to
 * their own questions that happen to agree with it.
 */
public sealed interface WrittenOwner {

    /** The module whose source wrote this — what keeps a prelude helper's constructs apart from
     *  those of the module expanding it. */
    String module();

    /**
     * The definition whose body wrote this, for a reader that answers only for those.
     *
     * <p>Said here, once, because it is one rule however many values rest on it. A row is owed for
     * a fork or a comparison of the tree that runs, and what runs is a body: a construct written in
     * a type's clauses or in a behavior's statement of itself is answered for by the clause and the
     * arm it is in, and one in a source's rows is answered for by the row. A value that carries a
     * number counted within a body and is published under an identity a body's number belongs to
     * asks for this where it is made, so what it holds is a body's from then on.
     *
     * <p>Stated as a refusal rather than as an absent answer. A reader given nothing here would
     * publish the module alone, and two definitions' first constructs are one identity under that.
     *
     * @throws IllegalStateException where something other than a definition's body wrote it
     */
    static Body theBodyThatWrote(WrittenOwner owner) {
        if (owner instanceof Body body) {
            return body;
        }
        throw new IllegalStateException("this is answered for by the definition whose body wrote"
                + " it, and it was written by " + owner);
    }

    /**
     * Where this stands among these, for a reader putting some of them in a steady order.
     *
     * <p>Beside the members, so that whoever adds one places it. Every component each of them
     * holds is read, because what this is for is that two owners this compiler can tell apart are
     * two here as well — an order that stopped at the names would leave two rows of one behavior
     * wherever a walk happened to put them.
     *
     * <p>What the result means is nothing beyond which comes first. Which of two owners an author
     * wrote first is a fact about where they wrote them and is asked of the places.
     */
    static java.util.Comparator<WrittenOwner> inASteadyOrder() {
        return java.util.Comparator.<WrittenOwner>comparingInt(WrittenOwner::rank)
                .thenComparing(WrittenOwner::inWhichModule, java.util.Comparator.naturalOrder())
                .thenComparing(WrittenOwner::whatItNames, java.util.Comparator.naturalOrder())
                .thenComparing(WrittenOwner::quoting, QuotedFrom.inASteadyOrder());
    }

    private static int rank(WrittenOwner owner) {
        return switch (owner) {
            case Declaration _ -> 0;
            case Stated _ -> 1;
            case Body _ -> 2;
            case Examples _ -> 3;
            case Fake _ -> 4;
        };
    }

    /**
     * Which module wrote it, and what inside that module it is — two names and two comparisons.
     *
     * <p>Compared apart and never joined into one word. Two names run together are one word, and
     * one word is the same word for more than one pair of names: a module called {@code a b} that
     * writes {@code c} and a module called {@code a} that writes {@code b c} render alike and are
     * two owners. An order that compared the rendering would call them one and leave them wherever
     * the walk put them.
     */
    private static String inWhichModule(WrittenOwner owner) {
        return switch (owner) {
            case Declaration it -> it.declaration().module();
            case Stated it -> it.module();
            case Body it -> it.module();
            case Examples it -> it.module();
            case Fake it -> it.module();
        };
    }

    /** What inside that module it is — see {@link #inWhichModule}. */
    private static String whatItNames(WrittenOwner owner) {
        return switch (owner) {
            case Declaration it -> it.declaration().name();
            case Stated it -> it.behavior();
            case Body it -> it.definition();
            case Examples it -> it.behavior();
            case Fake it -> it.target();
        };
    }

    /** And the text one was read from, for the two that are named by one. */
    private static QuotedFrom quoting(WrittenOwner owner) {
        return switch (owner) {
            case Declaration _, Stated _, Body _ -> new QuotedFrom.TextItCannotName();
            case Examples it -> it.text();
            case Fake it -> it.text();
        };
    }

    /** A type declaration, and everything written inside it: the clauses of its invariant. */
    record Declaration(TypeKey declaration) implements WrittenOwner {

        public Declaration {
            if (declaration == null) {
                throw new IllegalArgumentException("a declaration owns what is written in it");
            }
        }

        @Override
        public String module() {
            return declaration.module();
        }
    }

    /**
     * A behavior's declaration of itself, and everything written inside it: the arms of its
     * {@code ensures}.
     *
     * <p>Its own owner and not the body's. What a behavior states and what a definition does are
     * read as two questions, and a rule written in one that renumbered the other would be the thing
     * this exists to stop — as well as giving two constructs one number, both counting from zero
     * under a name they share.
     */
    record Stated(String module, String behavior) implements WrittenOwner {

        public Stated {
            if (module == null || behavior == null) {
                throw new IllegalArgumentException("a stated behavior is some module's: "
                        + module + "." + behavior);
            }
        }
    }

    /** A definition's body, and everything written inside it. */
    record Body(String module, String definition) implements WrittenOwner {

        public Body {
            if (module == null || definition == null) {
                throw new IllegalArgumentException("a definition is some module's: "
                        + module + "." + definition);
            }
        }
    }

    /**
     * What one source wrote as one behavior's {@code example} rows.
     *
     * <p>Per behavior rather than per block, because a source may write more than one block for one
     * behavior and a reader shown a row of {@code submit} is being shown one of that behavior's.
     * Per text, because an attached file's rows for a behavior are counted apart from the model
     * file's.
     */
    record Examples(QuotedFrom text, String module, String behavior) implements WrittenOwner {

        public Examples {
            if (text == null || module == null || behavior == null) {
                throw new IllegalArgumentException("example rows are some text's, for some"
                        + " behavior of some module: " + text + " " + module + "." + behavior);
            }
        }
    }

    /** What one source wrote as the stand-in for one behavior — {@code fake} rows, counted apart
     *  from the {@code example} rows written for the same behavior beside them. */
    record Fake(QuotedFrom text, String module, String target) implements WrittenOwner {

        public Fake {
            if (text == null || module == null || target == null) {
                throw new IllegalArgumentException("a stand-in is some text's, for some behavior"
                        + " of some module: " + text + " " + module + "." + target);
            }
        }
    }
}
