package souther.compiler.check;

import org.junit.jupiter.api.Test;

import souther.compiler.numeric.Count;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.Front;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A coordinate a clause reaches below a field is settled the same way one at a field is.
 *
 * <p>The rules of a record name positions at whatever depth they can reach, so
 * {@code interval.startsAt} is one name and not two. Reading the declaration with such a name
 * settled and taking that settling onto a reading already made have to leave the same thing, or a
 * search that reads once is answering about a position it never fixed.
 *
 * <p><b>Written here rather than left to the models.</b> What the corpora happen to write is what
 * the population this is a case of asks about, and the statement is about the language and not
 * about them: a model rewritten to relate two fields directly would take the whole depth out of
 * that population with nothing to see.
 */
class ASettlingBelowAFieldIsTakenOnLikeAnyOtherTest {

    private static final String NAMES_BELOW_A_FIELD = """
            module booking.deep

            data Span = { startsAt: Int, endsAt: Int }
            data Window = { interval: Span, cap: Int }
                invariant within = interval.startsAt < cap
                invariant ordered = interval.startsAt < interval.endsAt

            data Ok

            behavior take : (w: Window) -> Ok
            """;

    @Test
    void aNameReachingBelowAFieldLeavesWhatAReadingUnderItLeaves() {
        Read read = read();
        FieldDomains base = FieldDomains.of(read.declared(), read.source(), read.policy(),
                DeclarationReadings.NONE);

        RuleKey below = RuleKey.of("interval").then("startsAt");
        // That the reading files a coordinate under the whole name, said as what a settling beside
        // it does: with nothing settled the clause stops nothing, since what it holds the name
        // against is open. Asked first, because every comparison below would hold of a name the
        // rules say nothing about — two derivations agreeing that nothing is known is not this.
        assertTrue(FieldDomains.of(read.declared(), read.source(), read.policy(),
                        Map.of(RuleKey.of("cap"), new Count(BigDecimal.valueOf(5))),
                        DeclarationReadings.NONE)
                        .at(below).bounds() != null,
                "`" + below + "` is a name this record's rules reach, and settling `cap` beside it "
                        + "stops it nowhere");

        for (RuleKey settledAt : List.of(below, RuleKey.of("cap"),
                RuleKey.of("interval").then("endsAt"))) {
            for (long at : new long[] {0, 1, 5}) {
                Map<RuleKey, Count> settling = Map.of(settledAt, new Count(BigDecimal.valueOf(at)));
                FieldDomains readUnder = FieldDomains.of(read.declared(), read.source(),
                        read.policy(), settling, DeclarationReadings.NONE);
                FieldDomains.Composing takenOn = base.composing(FieldDomains.atValues(settling));
                for (RuleKey asked : List.of(below, RuleKey.of("interval").then("endsAt"),
                        RuleKey.of("cap"), RuleKey.of("interval"))) {
                    NumericDomain.Bounds values = readUnder.at(asked).bounds();
                    assertEquals(values, takenOn.at(asked).values(),
                            () -> "with `" + settledAt + "` settled at " + at + ", `" + asked
                                    + "` stops where the same settling taken onto one reading "
                                    + "says it stops");
                    assertEquals(readUnder.heldAt(asked), takenOn.at(asked).held(),
                            () -> "with `" + settledAt + "` settled at " + at + ", `" + asked
                                    + "` holds what the same settling taken onto one reading "
                                    + "says it holds");
                }
            }
        }
    }

    /**
     * That settling the name below a field moves what is left beside it.
     *
     * <p>What makes the comparison above about a settling rather than about two readings of the
     * same rules. A record whose clause the settling does not reach would answer alike either way,
     * and the agreement would say nothing about whether the settling arrived at all.
     */
    @Test
    void settlingTheNameBelowAFieldMovesWhatIsLeftBesideIt() {
        Read read = read();
        FieldDomains base = FieldDomains.of(read.declared(), read.source(), read.policy(),
                DeclarationReadings.NONE);
        RuleKey below = RuleKey.of("interval").then("startsAt");
        Map<RuleKey, Count> settling = Map.of(below, new Count(BigDecimal.valueOf(5)));

        NumericDomain.Bounds before = base.composing(Map.of()).at(RuleKey.of("cap")).values();
        NumericDomain.Bounds after =
                base.composing(FieldDomains.atValues(settling)).at(RuleKey.of("cap")).values();

        assertNotEquals(before, after,
                "settling `" + below + "` leaves `cap` where it was, so the clause relating them "
                        + "was not taken on and nothing else here would notice");
    }

    /** The declaration the rules above are written on, with everything it needs to be read. */
    private record Read(TypeSymbol.AtModule declared, RuleReadingSource source,
                        ReadingPolicy policy) {}

    private static Read read() {
        Compilation compilation = Compilation.ofSource(NAMES_BELOW_A_FIELD, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        RuleReadingSource source = RuleReadings.of(compilation, module);
        Map<String, DeclaredSig> sigs =
                compilation.db().ask(new Bodies.DeclaredSignatures(module)).value();
        Type window = sigs.get("take").inputs().get(0).type();
        if (!(TypeView.of(window, source.symbols()).shape()
                instanceof Shape.Product(TypeSymbol.AtModule declared, Map<String, Type> _))) {
            throw new IllegalStateException("the behavior above takes a record");
        }
        return new Read(declared, source,
                compilation.db().ask(new Front.Reading()).value());
    }
}
