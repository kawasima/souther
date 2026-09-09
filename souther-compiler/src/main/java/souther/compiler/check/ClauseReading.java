package souther.compiler.check;

import souther.compiler.core.Core;

/**
 * A clause tree read into one state, the connectives being the same whatever the parts are read as.
 *
 * <p>What a clause is written out of — a conjunction, a choice, a denial — is the clause's own shape
 * and not a fact about the language it is read in. Written once per language, that shape is the same
 * code as many times as there are languages, and each copy is free to drift from the others.
 *
 * <p><b>And the connectives belong to the clause and not to a component of the state.</b> That is
 * what this is for. A choice between two alternatives is a choice between two <em>readings of the
 * whole value</em>, so the alternative that cannot be taken has to be dropped by asking the whole of
 * what is known about it. Applied inside each language on its own, an alternative was dropped only
 * where the language doing the joining was also the one that could show it impossible — and a choice
 * between a branch no order admits and a branch no set of values admits came out open, each language
 * having found nothing wrong with the branch the other one refused.
 *
 * <p><b>What a clause states is answered upward and what its names mean is handed downward.</b> A
 * reading composes its parts into one answer, and that answer is a function of the parts; the
 * environment a part is read in is a function of the bindings above it, which is the other
 * direction. Carried only upward, there was nowhere for a binding to be, so a clause under one was
 * a shape every reading had no word for — and since almost every binding this check meets is one a
 * helper's expansion made, a rule stated through a helper was read less than the same rule written
 * out.
 *
 * <p>So {@code E} is handed down and {@code S} comes back up, and a part is read at the environment
 * it stands in. What a binding does to that environment is not asked of a reading: the fold finds
 * the boundary and {@link ClauseScope} answers it, which is what keeps a binder's meaning the
 * environment's (ADR-0106) rather than something each of three readings works out again.
 *
 * <p><b>A part is not the same thing for every reading.</b> Where a reading stops is its own answer
 * ({@link Descent}), so the node an author wrote a connective at is a part to a reading that takes
 * it whole and is none to a reading that descends. What every reading shares is the shape below it,
 * not the depth it reads to.
 *
 * @param <S> what a reading of a clause comes to
 * @param <E> what the reading carries into a binding — what its parts are read at
 */
interface ClauseReading<S, E> {

    /**
     * What one part of the clause says, read at the environment {@code at} it stands in.
     *
     * <p>A part of no connective, or a connective this reading takes whole — the two are one case,
     * and {@link ClauseExpr.Part} is that case. What is inside a part is the part language's to
     * ask, and a binding standing there is crossed by each question it asks about its own inside
     * (ADR-0106); a connective taken whole is a part on exactly those terms.
     *
     * <p>Handed the shape and not the node. What the part is ({@link ClauseExpr.Part#of}), how it
     * stands ({@link ClauseExpr#positive}), what the author wrote it as ({@link ClauseExpr#written})
     * and, for a connective taken whole, which two halves it composes
     * ({@link ClauseExpr.Joined#writtenHalves}) are all answers the shape already holds. A reading
     * given the node alone works whichever of them it needs out of the tree again, which is the
     * shape being read twice.
     *
     * <p>Reached with the denials already counted, so a reading of a comparison is a reading of the
     * comparison it states rather than of the one that was written. And reached with the bindings
     * above it entered, so a name a helper's expansion introduced denotes what it was given — a
     * reading that answered from the environment the whole clause began in would be reading one
     * value's rule at another value's names.
     */
    S whole(ClauseExpr.Part part, E at);

    /**
     * How far this reading goes into {@code join}, and what holding both of its parts comes to.
     *
     * <p>Asked of the shape and not of the operator, so what a connective composes is settled in
     * one place. The whole shape is handed over because a reading with something to say about the
     * choice has nowhere else to learn where it stands: which choice two readings are the branches
     * of is known here and at no call after it, so a reading not given it works it out from
     * whichever place a walk happened to reach, which is a fact about the walk.
     */
    Descent<S> at(ClauseExpr.Joined join);

    /**
     * What {@code e} leaves, stated where {@code positive} and denied where it is not, read from
     * {@code at} with {@code scope} answering for the bindings inside it.
     *
     * <p>A denial is carried down to the parts rather than applied to what a branch came to. What a
     * state says is a fact per position, and the denial of that is not one — the values a
     * conjunction rules out are a choice between the positions it named, which no map of positions
     * holds. Carried down, every denial meets a part, where it is one.
     */
    default S read(Core e, boolean positive, E at, ClauseScope<E> scope) {
        return read(e, positive, at, scope, null);
    }

    /**
     * The same, telling {@code per} what each part of the clause came to as it is read.
     *
     * <p>Keyed by the part as the tree holds it, so a reader that walks the same clause afterwards
     * finds what this reading made of the very node it is looking at. Asked again instead, that
     * reader is a second reading of the part, and two readings of one conjunct agree only for as
     * long as nobody changes one of them.
     */
    default S read(Core e, boolean positive, E at, ClauseScope<E> scope,
                   java.util.function.BiConsumer<Core, S> per) {
        return read(e, positive, at, scope, per, ClauseView.whole());
    }

    /**
     * The same, read in the world {@code view} describes rather than in the one the author wrote.
     *
     * <p>What a reader asking what one conjunct was holding compares against. A part outside that
     * world is not read, is not composed with what is read, and is told to nothing: the tree walked
     * here is the tree of the rules that world has, so honouring the omission is not something a
     * reading is asked to remember to do.
     */
    default S read(Core e, boolean positive, E at, ClauseScope<E> scope,
                   java.util.function.BiConsumer<Core, S> per, ClauseView view) {
        // The clause is named once more here, on the outside of everything its shape was written
        // as, which is where a caller holding the clause and nothing under it looks. What it came
        // to is not told to {@code per} a second time: the walk below has already said it of the
        // very same node, and a reader counting what it was told would count the whole clause
        // twice and every part of it once.
        return from(e, over(ClauseExpr.of(e, positive), at, scope, per, view));
    }

    /**
     * The same reading over the shape a clause has, which is read out of the tree once
     * ({@link ClauseExpr}).
     *
     * <p>Here rather than over {@link Core}, so that what counts as a connective is settled in one
     * place and every reading agrees about it by having been given the answer. Two readings that
     * each recognised {@code &&} for themselves agreed until one of them learned something.
     */
    private S over(ClauseExpr shape, E at, ClauseScope<E> scope,
                   java.util.function.BiConsumer<Core, S> per, ClauseView view) {
        S out = switch (shape) {
            case ClauseExpr.Leaf it -> whole(it, at);
            // How far this reading goes is its own answer, and taking the connective whole is
            // reading the node an author wrote it at as a part. A reading told to descend and
            // unable to compose what it found had nowhere to say so.
            //
            // A reading that takes a conjunction whole reads whatever is under it, this world's
            // omissions included: what it composes is not the clause's connective, so there is no
            // side for a part to be left out of. Which is why the omission is answered where the
            // reading descends and nowhere else.
            case ClauseExpr.Joined it -> switch (at(it)) {
                case Descent.Whole<S> _ -> whole(it, at);
                // A side holding no rule of this world is not read and not composed with. What a
                // conjunction of one rule and no rule comes to is that rule, and reading the
                // missing side as a state saying nothing would be the same answer arrived at by
                // having read a rule that is not there — which a walk collecting parts, and every
                // account made of one, can tell apart.
                case Descent.Into<S> into -> {
                    if (view.omits(it.left())) {
                        yield over(it.right(), at, scope, per, view);
                    }
                    if (view.omits(it.right())) {
                        yield over(it.left(), at, scope, per, view);
                    }
                    yield into.compose().apply(over(it.left(), at, scope, per, view),
                            over(it.right(), at, scope, per, view));
                }
            };
            // The one place the environment changes, and it changes for what is under the binding
            // alone. What the binding means is not worked out here and not by the reading either.
            case ClauseExpr.Scoped it ->
                    over(it.body(), scope.inside(it.binding(), at), scope, per, view);
        };
        // Every node that was written as this shape, so a reader asking about the node it is
        // holding finds what this made of it — the denial as well as what is under it, since the
        // two are one shape, and the binding as well as the clause under it, since a binding states
        // what the clause under it states.
        for (Core each : shape.spelled()) {
            out = from(each, out);
            if (per != null) {
                per.accept(each, out);
            }
        }
        return out;
    }

    /**
     * The same reading, remembering that it is what {@code e} came to.
     *
     * <p>For a reading that has to answer about the parts of a clause afterwards. Kept in what the
     * reading carries rather than handed to somebody who keeps it: a part of a branch that turns
     * out dead is answered differently from the same part in a branch that stands, and everything
     * that decides which of those it was happens above here. Kept outside, the part would be read
     * again against a tree the decision had not reached.
     */
    default S from(Core e, S out) {
        return out;
    }
}
