package souther.compiler.check;

import souther.compiler.DefaultStdlib;
import souther.compiler.types.BinOp;
import org.junit.jupiter.api.Test;
import souther.compiler.types.ConstructOccurrence;

import souther.compiler.core.Core;
import souther.compiler.diag.SourcePos;
import souther.compiler.types.BindingId;
import souther.compiler.types.BindingOwner;
import souther.compiler.types.Type;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a term costs when it reads a named value twice.
 *
 * <p>What terms are made of is a graph and not a tree. A name read twice is one value read twice, and
 * {@code let (a, b) = t} reads {@code t} twice by itself, so a chain of those is a graph whose nodes
 * grow by one per link and whose paths double. A term holding each of its parts in full holds one
 * copy per path — twenty-four links once asked for more characters than an array can hold, when a
 * term was the string it was written out as.
 *
 * <p>Held on the size of the term rather than on the time a compile takes. Time is what the growth
 * showed up as last: the chain measured flat right up to the link that asked for two gigabytes at
 * once, because the walk is one lookup per name and only what it wrote doubled. What was wrong is the
 * size of the representation, so that is what is measured, and it is measured where it is built.
 *
 * <p>Written as bindings, since that is where the sharing is. A value is read twice by being named
 * and read twice, not by a tree holding one node twice — which is also why the walk stayed linear
 * while the writing did not.
 */
class ATermThatReadsAnotherHoldsItsNameAndNotItsShapeTest {

    private static final SourcePos NOWHERE = new SourcePos(0, 0);
    private static final BindingOwner OWNER = new BindingOwner.OfValue("m", "f");

    /** {@code v0 = 1 + 1}, then {@code v(i) = v(i-1) + v(i-1)}: each link names one value and reads
     *  it twice. Answers the term of the last link. */
    private static Term chainTerm(Terms terms, int links) {
        return chain(terms, links).get(links);
    }

    /** The term of each link, the first one first. */
    private static List<Term> chain(Terms terms, int links) {
        List<Term> along = new java.util.ArrayList<>();
        Denotations at = Denotations.none();
        Core value = new Core.Binary(BinOp.ADD, new Core.Int(1, Type.INT, NOWHERE),
                new Core.Int(1, Type.INT, NOWHERE), ConstructOccurrence.unwritten(), Type.INT,
                NOWHERE);
        Term term = terms.bodyKey(value, at);
        along.add(term);
        for (int i = 0; i < links; i++) {
            BindingId id = new BindingId(OWNER, i);
            at = at.binding(id, value, FactSubject.of(term), null, term, null);
            Core read = new Core.Read("v" + i, id, Type.INT, NOWHERE);
            value = new Core.Binary(BinOp.ADD, read, read, ConstructOccurrence.unwritten(),
                    Type.INT, NOWHERE);
            term = terms.bodyKey(value, at);
            along.add(term);
        }
        return along;
    }

    private static int held(int links) {
        Term term = chainTerm(RuleReadings.termsOfNoClauseFiled(Symbols.none(DefaultStdlib.get()), souther.compiler.query.ReadAs.THE_COMPILATION_DOES), links);
        return term == null ? -1 : term.distinct();
    }

    /**
     * A link adds a term, and a term is what a link adds.
     *
     * <p>Asked at four lengths as one map, because what is held is that the size does not follow the
     * paths through the chain — one length is met by any growth at all, and it was met by one that
     * doubled.
     */
    @Test
    void aLinkCostsTheSameWhateverIsUnderIt() {
        // Asked shortest first and held as it goes, so that a term which follows the paths is caught
        // at the length where it is still a number and not at the one where it is an allocation.
        Map<Integer, Integer> measured = new LinkedHashMap<>();
        for (int links : List.of(4, 8, 16, 32)) {
            int size = held(links);
            measured.put(links, size);
            assertTrue(size > 0 && size <= 2 * links + 4,
                    "a link holds the term under it rather than a copy of it: " + measured);
        }
    }

    /**
     * The chain a compile met, at a length the growth never allowed.
     *
     * <p>Twenty-four links is where it stopped, having asked for two gigabytes. A thousand is past
     * every length that was reachable, and the term is still one term per link.
     */
    @Test
    void aChainLongerThanTheOldLimitIsStillOneTermPerLink() {
        assertTrue(held(1000) <= 2004, "the term at a thousand links holds: " + held(1000));
    }

    /**
     * Sharing a term does not merge two terms that differ.
     *
     * <p>Which is the property terms are for: two writings are one term exactly when they compute one
     * value, and that is what makes naming an expression not change what is known of it. A chain of
     * four and a chain of five read alike at every link but the last.
     */
    @Test
    void twoShapesThatDifferAreTwoTerms() {
        Terms terms = RuleReadings.termsOfNoClauseFiled(Symbols.none(DefaultStdlib.get()), souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
        Term four = chainTerm(terms, 4);
        Term five = chainTerm(terms, 5);
        Term fourAgain = chainTerm(terms, 4);

        assertEquals(four, fourAgain, "one shape is one term");
        assertNotEquals(four, five, "and two shapes are two");
    }

    /** How many places a table holding a thousand and one of these has, and how it reads a hash into
     *  one: what {@link java.util.HashMap} does, since it is the table this is held for. */
    private static final int PLACES = 2048;

    /** How many of the chain's terms fall in the fullest place of that table. */
    private static int longestBucket(List<Term> along) {
        int[] held = new int[PLACES];
        for (Term term : along) {
            int hash = term.hashCode();
            held[(hash ^ (hash >>> 16)) & (PLACES - 1)]++;
        }
        int longest = 0;
        for (int count : held) {
            longest = Math.max(longest, count);
        }
        return longest;
    }

    /**
     * This chain does not hash into one bucket.
     *
     * <p>Not a law about hashing. Two values may share a hash and nothing is wrong; what is held is
     * that <em>this family</em> does not collapse, because it is the family the sharing exists for
     * and it collapsed. A term reading one part twice folds that part in twice, so a chain of them
     * multiplies the hash it carries up by a fixed amount per link, and with a multiplier of 31 that
     * amount is thirty-two — two to the fifth, so seven links shift a level's own hash out and every
     * link past that hashes alike. Nothing about the terms says so: they are distinct, they compare
     * distinct, and only the table holding them turns into a list. A thousand links took 8.7 seconds
     * where a hundred took 20 milliseconds.
     *
     * <p>Held on the fullest place of the table rather than on how many hashes there are. What made
     * the lookup a walk is a place holding the family, and the two are not the same measurement:
     * that collapse put 997 of the thousand in one place, while a family spread over 84% of its
     * hashes still fills its fullest place seven deep, which is what an evenly spread thousand into
     * two thousand places does anyway. Counting hashes both passes a hash whose low bits agree and
     * fails one that is perfectly good.
     *
     * <p>Held as places and not as a time, since a time is a fact about the machine.
     */
    @Test
    void theChainDoesNotHashIntoOneBucket() {
        List<Term> along = chain(RuleReadings.termsOfNoClauseFiled(Symbols.none(DefaultStdlib.get()), souther.compiler.query.ReadAs.THE_COMPILATION_DOES), 1000);
        java.util.Set<Term> terms = new java.util.HashSet<>(along);
        java.util.Set<Integer> hashes = new java.util.HashSet<>();
        along.forEach(term -> hashes.add(term.hashCode()));

        assertEquals(1001, terms.size(), "a thousand links are a thousand terms");
        // Sixteen: the collapse filled a place 997 deep and this family fills one 6 deep, which is
        // what a thousand spread evenly over two thousand places comes to. A bound between them
        // holds the difference and lets the hash change without saying so.
        assertTrue(longestBucket(along) <= 16, "and no place of the table holds many of them: "
                + longestBucket(along) + " at the fullest, over " + hashes.size() + " of "
                + terms.size() + " distinct hashes");
    }

    /**
     * A term built by one reading equals its twin built by another.
     *
     * <p>Sharing is what makes the comparison cheap and is not what makes two terms equal. Two
     * readings of one program — one compile against another, a test comparing what two spellings
     * name — hold terms from different interners, and they are the same terms.
     */
    @Test
    void aTermBuiltByAnotherReadingIsTheSameTerm() {
        Term here = chainTerm(RuleReadings.termsOfNoClauseFiled(Symbols.none(DefaultStdlib.get()), souther.compiler.query.ReadAs.THE_COMPILATION_DOES), 6);
        Term there = chainTerm(RuleReadings.termsOfNoClauseFiled(Symbols.none(DefaultStdlib.get()), souther.compiler.query.ReadAs.THE_COMPILATION_DOES), 6);

        assertEquals(here, there, "two readings name one value alike");
        assertEquals(here.hashCode(), there.hashCode(), "and hash it alike");
    }
}
