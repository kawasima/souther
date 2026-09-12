package souther.compiler.check;

import souther.compiler.DefaultStdlib;
import souther.compiler.types.BinOp;
import souther.compiler.ast.Hir;
import souther.compiler.core.Core;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.ConstructOccurrence;
import souther.compiler.types.Type;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a name was given is followed to the end, and a reading that follows it does so once.
 *
 * <p>The text behind a name is no longer written down beside it: it is at the end of the chain of
 * names, and reading it walks that chain. Which is a walk per ask, and the readers that ask do so
 * inside a walk of their own — the arithmetic reading asks it of a name, then asks it again of what
 * that name was given, and so on down. Left as it stands, a chain of names costs the square of its
 * length, and nothing about the answers would say so.
 *
 * <p>Held as what the chain costs rather than as how long it takes. Doubling the chain doubles the
 * steps taken while it costs the chain once, and quadruples them while it costs the chain per link;
 * a bound on the number would be a bound on one shape of body, and this is about the shape of the
 * reading.
 */
class FollowingWhatANameWasGivenCostsTheChainOnceTest {

    private static final SourcePos POS = new SourcePos(0, 0);
    private static final BindingOwner OWNER = new BindingOwner.OfValue("demo", "f");

    @Test
    void doublingTheChainDoublesWhatFollowingItCosts() {
        Map<Integer, Long> steps = new LinkedHashMap<>();
        for (int links : new int[] {40, 80, 160, 320}) {
            steps.put(links, followedOver(links));
        }

        assertTrue(steps.get(80) < steps.get(40) * 3, "80 links: " + steps);
        assertTrue(steps.get(160) < steps.get(80) * 3, "160 links: " + steps);
        assertTrue(steps.get(320) < steps.get(160) * 3, "320 links: " + steps);
    }

    /** The steps taken following names while the arithmetic of {@code x(links)} is read, where each
     * link is a name for the one before it and the first is arithmetic. */
    private static long followedOver(int links) {
        PathEngine engine = new PathEngine(
                RuleReadingContext.unshared(
                        RuleReadings.ofNoClauseFiled(Symbols.none(DefaultStdlib.get())),
                        souther.compiler.query.ReadAs.THE_COMPILATION_DOES),
                Terms.Of.THE_DISCHARGE_TREE);
        Hir.Binders binders = new Hir.Binders(OWNER);
        Core.Binder first = CoreBinders.of(binders.binder("x0", POS));
        Denotations at = engine.enter(Terms.read(CoreBinders.of(binders.binder("a", POS)), Type.INT, POS),
                Known.top(), Denotations.none()).at();
        Core arithmetic = new Core.Binary(BinOp.ADD,
                new Core.Read("a", at.bound().keySet().iterator().next(), Type.INT, POS),
                new Core.Int(1, Type.INT, POS), ConstructOccurrence.unwritten(), Type.INT, POS);
        at = bound(engine, first, arithmetic, at);
        Core.Binder last = first;
        for (int i = 1; i <= links; i++) {
            Core.Binder next = CoreBinders.of(binders.binder("x" + i, POS));
            at = bound(engine, next, read(last), at);
            last = next;
        }

        long[] counting = {0};
        Terms.FOLLOWED = counting;
        try {
            engine.terms().affineOf(read(last), at);
        } finally {
            Terms.FOLLOWED = null;
        }
        return counting[0];
    }

    private static Denotations bound(PathEngine engine, Core.Binder binder, Core value,
                                     Denotations at) {
        return engine.bindLet(new Core.LetIn(binder, value, read(binder), Type.INT, POS),
                Known.top(), at).at();
    }

    private static Core read(Core.Binder binder) {
        return new Core.Read(binder.name(), binder.binding(), Type.INT, POS);
    }
}
