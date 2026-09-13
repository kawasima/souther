package souther.compiler.examples;

import net.unit8.raoh.Ok;
import net.unit8.raoh.Path;
import net.unit8.raoh.decode.Decoder;

import org.junit.jupiter.api.Test;

import souther.compiler.query.Scopes;
import souther.compiler.check.ScopedDeclarations;
import souther.compiler.check.Symbols;
import souther.compiler.observe.Position;
import souther.compiler.query.Compilation;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The envelope a value wears is the one the position reads it through, not one its case is owed
 * wherever it stands. A case's own Encoder writes no discriminator and its sum's writes one
 * (spec §encode-law), so the same {@code Approved} is {@code {"id":1}} where {@code Approved} is
 * declared and {@code {"id":1,"type":"Approved"}} where {@code Approved | Rejected} is.
 */
class AValueWearsTheEnvelopeThePositionReadsItThroughTest {

    private static final String MODULE = """
            module demo

            data Approved = { id: Int }
            data Rejected = { id: Int }
            data Decision = Approved | Rejected
            data Outcome = Approved | Rejected

            data Draft
            data Filed
            data Stage = Draft | Filed
            data Note = { text: String }
            data Step = Draft | Note

            data Yen = Int
            data Paid = Yen
            data Settlement = Paid | Draft

            data Withdrawn = { id: Int }
            data Appeal = Decision | Withdrawn
            """;

    /**
     * A second module: the same declarations and one more sum, which lists {@code Filed} beside a
     * case that bears a field.
     *
     * <p>{@code Filed} because it is the case the two modules disagree about if membership is what
     * answers. In the first, the only sum listing it is the enumeration {@code Stage}, so a search
     * over the sums answers "a bare name"; in the second, {@code Revision} is not an enumeration, so
     * the same search answers "the tag {@code Stage} reads it under". Nothing reads a value through
     * either sum where the form below is asked for, so neither may be part of the answer.
     */
    private static final String AND_ANOTHER_SUM = MODULE + """

            data Revision = Filed | Note
            """;

    private final Compilation compilation = compiled(MODULE);
    private final Symbols symbols = Scopes.derived(compilation.db(), "demo").value();
    private final NeutralForm neutral =
            new NeutralForm(symbols, ScopedDeclarations.of(symbols),
                    ScopedDeclarations.kindsOf(symbols),
                    souther.compiler.query.ExampleExecutions.of(compilation.db(), "demo")
                            .fieldTypes());

    private static Compilation compiled(String module) {
        Compilation c = Compilation.ofSource(module, "Main");
        c.answerEverything();
        return c;
    }

    /** A value of {@code type}, as a helper would have answered with one: through its own decoder,
     *  which is the one place a case is built here without going through a sum. */
    private Object value(String type, Object written) throws Exception {
        return value(compilation, type, written);
    }

    @SuppressWarnings("unchecked")
    private static Object value(Compilation from, String type, Object written) throws Exception {
        Decoder<Object, ?> decoder = (Decoder<Object, ?>) from.loader()
                .loadClass("demo." + type).getMethod("decoder").invoke(null);
        return ((Ok<?>) decoder.decode(written, Path.ROOT)).value();
    }

    private Object approved() throws Exception {
        return value("Approved", Map.of("id", 1L));
    }

    private Object unit(String type) throws Exception {
        return value(type, Map.of());
    }

    private Object paid() throws Exception {
        return value("Paid", 500L);
    }

    private Position at(String type) {
        return at(symbols, type);
    }

    private static Position at(Symbols symbols, String type) {
        return Position.at(Type.ref(TypeSymbols.declared(new TypeKey(symbols.module(), type))));
    }

    @Test
    void aCaseAtItsOwnPositionCarriesNoDiscriminator() throws Exception {
        assertEquals(Map.of("id", 1L), neutral.of(approved(), at("Approved"), "h"));
    }

    @Test
    void aCaseAtASumThatListsItCarriesTheTagThatSumsDecoderReads() throws Exception {
        assertEquals(Map.of("id", 1L, "type", "Approved"),
                neutral.of(approved(), at("Decision"), "h"));
    }

    /**
     * Two sums may list one case, and each writes what it reads the case under. That the two agree
     * today is a fact about derivation — every discriminator is derived, keyed {@code "type"} and
     * tagged with the case's own name — and not one either answer rests on: each is read off the
     * decoder of the sum at the position, so a sum that read it under another key would move its own
     * position and no other.
     */
    @Test
    void eachSumThatListsOneCaseWritesWhatItReadsItUnder() throws Exception {
        assertEquals(neutral.of(approved(), at("Decision"), "h"),
                neutral.of(approved(), at("Outcome"), "h"));
    }

    /** A derived decoder dispatches over the leaves of the sums it names, so a case reached through
     *  a nested sum is listed by the outer position's own decoder and is read there without walking
     *  to the sum that names it directly. */
    @Test
    void aCaseReachedThroughANestedSumIsReadAtTheOuterPosition() throws Exception {
        assertEquals(Map.of("id", 1L, "type", "Approved"),
                neutral.of(approved(), at("Appeal"), "h"));
    }

    /** A unit case travels as its bare name where the position is an enumeration, and wears the tag
     *  where the position is a sum that has a field-bearing case as well. */
    @Test
    void aUnitCaseIsANameAtAnEnumerationAndATagAtASumThatIsNotOne() throws Exception {
        assertEquals("Draft", neutral.of(unit("Draft"), at("Stage"), "h"));
        assertEquals(Map.of("type", "Draft"), neutral.of(unit("Draft"), at("Step"), "h"));
    }

    /** At its own type a unit case is read by its own decoder, which ignores what it is handed. */
    @Test
    void aUnitCaseAtItsOwnPositionCarriesNoDiscriminator() throws Exception {
        assertEquals(Map.of(), neutral.of(unit("Draft"), at("Draft"), "h"));
    }

    /** A newtype's form is its inner value where the position reads it as itself, and the inner
     *  value under `value` beside the tag where the position is a sum that lists it. */
    @Test
    void aNewtypeIsBareAtItsOwnPositionAndWrappedAtASumThatListsIt() throws Exception {
        assertEquals(500L, neutral.of(paid(), at("Paid"), "h"));
        assertEquals(Map.of("type", "Paid", "value", 500L),
                neutral.of(paid(), at("Settlement"), "h"));
    }

    /**
     * Where nothing reads the value — an answer of several types, which admission and not a decoder
     * decides, and a place no declaration names — the case is written as it is on its own. A
     * discriminator is what a sum reading the case asks to be written beside it, and there is no such
     * sum here; a bare name is what an enumeration reads, and there is no such enumeration either.
     *
     * <p>The same forms a position typed by the case itself gives, which is what the two tests above
     * hold.
     */
    @Test
    void aPlaceNothingReadsWritesTheCaseAsItIsOnItsOwn() throws Exception {
        assertEquals(Map.of("id", 1L), neutral.of(approved(), Position.UNREAD, "h"));
        assertEquals(Map.of(), neutral.of(unit("Draft"), Position.UNREAD, "h"));
    }

    /**
     * And it is the same form when another sum listing the case is declared. A sum nothing here reads
     * the value through says where the case may be read and not where it is, so it is no part of the
     * answer. A rule reading membership could not hold this: declaring {@code Revision} moved what it
     * answered for a case standing somewhere else entirely.
     *
     * <p>The form itself is stated rather than the two being compared with each other, because both
     * would move together under a rule that reads membership.
     */
    @Test
    void anotherSumListingTheCaseDoesNotMoveWhatAPlaceNothingReadsWrites() throws Exception {
        Compilation with = compiled(AND_ANOTHER_SUM);
        Symbols theirs = Scopes.derived(with.db(), "demo").value();
        NeutralForm and = new NeutralForm(theirs, ScopedDeclarations.of(theirs),
                ScopedDeclarations.kindsOf(theirs),
                souther.compiler.query.ExampleExecutions.of(with.db(), "demo").fieldTypes());
        assertEquals(Map.of(), neutral.of(unit("Filed"), Position.UNREAD, "h"));
        assertEquals(Map.of(), and.of(value(with, "Filed", Map.of()), Position.UNREAD, "h"));
        // and each sum that does read it there still writes what it reads it under
        assertEquals("Filed", and.of(value(with, "Filed", Map.of()), at(theirs, "Stage"), "h"));
        assertEquals(Map.of("type", "Filed"),
                and.of(value(with, "Filed", Map.of()), at(theirs, "Revision"), "h"));
    }

}
