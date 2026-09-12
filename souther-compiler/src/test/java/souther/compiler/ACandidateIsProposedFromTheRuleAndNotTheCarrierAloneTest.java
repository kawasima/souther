package souther.compiler;

import org.junit.jupiter.api.Test;

import souther.compiler.partition.Generator;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a position offers, once the rules written on its type are read before the value is chosen.
 *
 * <p>A representative used to come from the carrier alone — the empty one for every collection, a
 * one-character string — and the rules were consulted only afterwards, by the decoder that refuses
 * what was built. A rule saying a collection is not empty is then the one rule that refuses the one
 * value on offer, and since a row is the product of its positions, one such field took every row of
 * the behavior with it.
 *
 * <p>A candidate is still a proposal the decoder answers. Nothing here claims a witness exists for an
 * arbitrary rule: what is held to is narrower — a minimum this can read is a minimum the candidate is
 * built at, rather than one the choice is made in ignorance of.
 */
class ACandidateIsProposedFromTheRuleAndNotTheCarrierAloneTest {

    /**
     * A model whose one uncovered combination needs a value of the constrained type.
     *
     * <p>{@code t.kind} is divided into two and one of them has a row, so the row that is owed is the
     * other one — and writing it takes a {@code tags} the type admits.
     */
    private static String model(String declaration, String field, String written) {
        return """
                module nd.gen

                data Domestic
                data Overseas
                data Kind = Domestic | Overseas

                %s

                data T = { kind: Kind, %s }

                behavior look : (t: T) -> Int

                let look (t) = 1

                example look
                    | (T { kind = Domestic, %s }) -> 1
                """.formatted(declaration, field, written);
    }

    /**
     * What the generator came back with for that combination.
     *
     * <p>The diagnostics are held to as well as the rows. A written fixture that breaks the rule it is
     * written under leaves a model that does not compile, and a test asking such a model for its rows
     * gets an empty answer that agrees with whatever it expected of one.
     */
    private static souther.compiler.partition.FillResult generated(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.measure(Adequacy.Asked.fullReport());
        compilation.answerEverything();
        assertEquals(List.of(), compilation.diagnostics().values().stream()
                .flatMap(List::stream).map(each -> each.diagnostic().code() + " "
                        + each.diagnostic().titleKey()).toList(),
                "the model under test compiles");
        Map<String, Adequacy.Filling> all = Adequacy.generatedOf(compilation.db(), compilation.modules().get(0));
        assertNotNull(all, "the rows come back");
        return all.get("look").composed();
    }

    /** The one row the generator writes for that combination, as the text an author is offered. */
    private static String generatedRow(String source) {
        souther.compiler.partition.FillResult filled = generated(source);
        assertEquals(List.of(), filled.unresolved(),
                "the combination is one a value exists for, so nothing is left unresolved");
        assertEquals(1, filled.rows().size(), "one combination is uncovered, so one row is written");
        return filled.rows().get(0).inputs().get(0).text();
    }

    /**
     * The rows the generator writes, where the collection's elements divide and each class is owed
     * one.
     *
     * <p>Several rather than one, and that is the point. What a collection holds is a position, so a
     * carrier that divides divides it — and a row is owed for each class of it, each a collection
     * holding an element there.
     */
    private static List<String> generatedRows(String source) {
        souther.compiler.partition.FillResult filled = generated(source);
        assertEquals(List.of(), filled.unresolved(),
                "the combination is one a value exists for, so nothing is left unresolved");
        return filled.rows().stream().map(row -> row.inputs().get(0).text()).toList();
    }

    // --- two rules about one position --------------------------------------------------------------

    /**
     * Two rules about the strings of a position, and a value that clears both.
     *
     * <p>A proposal per rule is a proposal each of the others may refuse. Offered only those, a
     * position carrying two rules about its strings has no value at all — each is what one rule
     * asked for and the next one refuses it — and the behavior it sits in comes back with no row
     * anywhere.
     *
     * <p>Held at the row, because that is the size of what it costs. A reading of the proposals
     * alone would have shown two values where one was wanted and said nothing about the model
     * losing every row it has.
     */
    @Test
    void aPositionCarryingTwoRulesIsOfferedAValueClearingBoth() {
        String row = generatedRow(model("""
                data Code = String
                    invariant begins = String.startsWith("X", value)
                    invariant ends = String.endsWith("Z", value)
                """, "code: Code", "code = Code(\"XZ\")"));

        assertEquals("T { kind = Overseas, code = Code(\"XZ\") }", row);
    }

    // --- a collection the rules say is not empty ---------------------------------------------------

    @Test
    void aListTheRuleSaysIsNotEmptyIsOfferedAnElement() {
        String row = generatedRow(model("""
                data Tag = List<String>
                    invariant someTags = List.length(value) >= 1
                """, "tags: Tag", "tags = Tag([\"t\"])"));

        assertEquals("T { kind = Overseas, tags = Tag([\"x\"]) }", row);
    }

    /**
     * The minimum itself, and not one element.
     *
     * <p>A reader that recognised {@code >= 3} and then offered a list of one would refuse the row for
     * the same reason it refused the empty one, having read the rule and not used it.
     */
    @Test
    void aListIsOfferedAsManyElementsAsTheMinimumNames() {
        String row = generatedRow(model("""
                data Tags = List<String>
                    invariant enough = List.length(value) >= 3
                """, "tags: Tags", "tags = Tags([\"a\", \"b\", \"c\"])"));

        assertEquals("T { kind = Overseas, tags = Tags([\"x\", \"x\", \"x\"]) }", row);
    }

    /**
     * A set counts what a list does, and its rule is read the same way.
     *
     * <p>Which the decoder's own reading cannot supply. Raoh has no constraint for a set's size — a
     * set crosses the boundary as a list, so a size chained after the mapping that drops duplicates
     * would count the wrong things — and a candidate chooser reading the decoder's constraints would
     * find nothing here.
     */
    @Test
    void aSetTheRuleSaysIsNotEmptyIsOfferedAnElement() {
        String row = generatedRow(model("""
                data Codes = Set<String>
                    invariant someCodes = Set.size(value) >= 1
                """, "codes: Codes", "codes = Codes([\"c\"])"));

        assertEquals("T { kind = Overseas, codes = Codes([\"x\"]) }", row);
    }

    /** A set of three is three elements no two of which are equal, or it is a set of fewer. */
    @Test
    void aSetIsOfferedElementsNoTwoOfWhichAreEqual() {
        String row = generatedRow(model("""
                data Codes = Set<String>
                    invariant enough = Set.size(value) >= 3
                """, "codes: Codes", "codes = Codes([\"a\", \"b\", \"c\"])"));

        assertEquals("T { kind = Overseas, codes = Codes([\"x\", \"xx\", \"xxx\"]) }", row);
    }

    @Test
    void aMapTheRuleSaysIsNotEmptyIsOfferedAnEntry() {
        String row = generatedRow(model("""
                data Props = Map<String, String>
                    invariant someProps = Map.size(value) >= 1
                """, "props: Props", "props = Props([(\"k\", \"v\")])"));

        assertEquals("T { kind = Overseas, props = Props([(\"x\", \"x\")]) }", row);
    }

    /** Two entries is two keys no two of which are equal; the values under them are free to repeat. */
    @Test
    void aMapIsOfferedEntriesNoTwoOfWhichShareAKey() {
        String row = generatedRow(model("""
                data Props = Map<String, String>
                    invariant enough = Map.size(value) >= 2
                """, "props: Props", "props = Props([(\"k\", \"v\"), (\"l\", \"w\")])"));

        assertEquals("T { kind = Overseas, props = Props([(\"x\", \"x\"), (\"xx\", \"x\")]) }", row);
    }

    /**
     * A carrier with two values in it is a carrier a set of two can be built from.
     *
     * <p>Nothing is being solved here: the rule is read, the carrier's values are known, and the two
     * facts have to meet. They meet only where the second value is invented by stepping something, so a
     * carrier that is not counted or spelled has no second element and a set of two is refused for
     * holding one.
     */
    @Test
    void aSetOfACarrierWithTwoValuesIsBuiltFromBoth() {
        List<String> rows = generatedRows(model("""
                data Flags = Set<Bool>
                    invariant both = Set.size(value) >= 2
                """, "flags: Flags", "flags = Flags([true, false])"));

        // The written row's set holds both of what a `Bool` divides into, so neither element class
        // is owed a row and the one offered is for the other position. What it shows is still what
        // this is about: the set at the position the row is not about is built from both values,
        // because the rule asks for two and a `Bool` has no third.
        assertEquals(List.of("T { kind = Overseas, flags = Flags([true, false]) }"), rows);
    }

    /**
     * A sum divides into its cases, and a set of two is built from two of them.
     *
     * <p>Nothing here was written for sums. What a type divides into is asked of the one reader that
     * answers it, so a carrier reaches this by being divided rather than by being remembered — which is
     * the difference between covering a carrier and having covered the carriers somebody thought of.
     */
    @Test
    void aSetOfASumIsBuiltFromItsCases() {
        List<String> rows = generatedRows(model("""
                data Red
                data Green
                data Blue
                data Color = Red | Green | Blue

                data Palette = Set<Color>
                    invariant enough = Set.size(value) >= 2
                """, "palette: Palette", "palette = Palette([Red, Blue])"));

        // The written row holds `Red` and `Blue`, so `Green` is the case left, and the other
        // position owes a row of its own. Both are sets of two, and the one for `Green` holds it.
        assertEquals(List.of("Palette([Green, Red])", "Palette([Red, Green])"),
                rows.stream().map(each -> each.replaceAll(".*palette = ", "").replace(" }", ""))
                        .toList(),
                "a set of two, built from the cases the sum divides into");
    }

    // --- a string the rules give a length ----------------------------------------------------------

    /**
     * The fixed {@code "x"} is a string of one, so a minimum of one was met by accident and a minimum
     * of two was not. Both are here: the first has to keep working and the second is the defect.
     */
    @Test
    void aStringIsOfferedALengthTheMinimumAdmits() {
        String row = generatedRow(model("""
                data Name = String
                    invariant longEnough = String.length(value) >= 5
                """, "name: Name", "name = Name(\"abcde\")"));

        assertEquals("T { kind = Overseas, name = Name(\"xxxxx\") }", row);
    }

    /**
     * A string is counted in characters, and how many of those are worth writing is its own question.
     *
     * <p>A collection of sixty-five is sixty-five values each built in turn; a string of sixty-five is
     * one literal. Holding the two to one number is holding a string to a limit nothing about it
     * suggests, and the type here is one the model plainly admits.
     */
    @Test
    void aStringsLengthIsNotBoundedByWhatACollectionHolds() {
        String row = generatedRow(model("""
                data Token = String
                    invariant longEnough = String.length(value) >= 65
                """, "token: Token", "token = Token(\"" + "a".repeat(65) + "\")"));

        assertEquals("T { kind = Overseas, token = Token(\"" + "x".repeat(65) + "\") }", row);
    }

    @Test
    void aStringWhoseMinimumIsOneIsStillOfferedTheOneCharacterValue() {
        String row = generatedRow(model("""
                data Name = String
                    invariant longEnough = String.length(value) >= 1
                """, "name: Name", "name = Name(\"abcde\")"));

        assertEquals("T { kind = Overseas, name = Name(\"x\") }", row);
    }

    /**
     * A format and a minimum are two proposals, not a precedence.
     *
     * <p>The shortest string the pattern accepts is the empty one, which the minimum refuses; the
     * string the minimum asks for is five characters, which this pattern accepts. A reader that took
     * the format as the answer wherever there was one would offer only the first and report the
     * combination as refused. Neither candidate is a claim — what settles it is that the decoder was
     * given both to answer.
     */
    @Test
    void aFormatAndAMinimumAreBothProposedAndTheDecoderChooses() {
        String row = generatedRow(model("""
                data Tag = String
                    invariant shaped = String.matches("x*", value)
                    invariant longEnough = String.length(value) >= 5
                """, "tag: Tag", "tag = Tag(\"xxxxx\")"));

        assertEquals("T { kind = Overseas, tag = Tag(\"xxxxx\") }", row);
    }

    // --- what a rule reaches -----------------------------------------------------------------------

    /**
     * The elements have rules of their own, and the value inside the collection is built against them.
     *
     * <p>A witness is a tree: what stands for a list is a list of what stands for its element, which
     * is itself a value some rule may have something to say about.
     */
    @Test
    void anElementsOwnRuleIsReadWhereTheElementIsBuilt() {
        String row = generatedRow(model("""
                data Word = String
                    invariant longEnough = String.length(value) >= 4

                data Words = List<Word>
                    invariant someWords = List.length(value) >= 1
                """, "words: Words", "words = Words([Word(\"abcd\")])"));

        assertEquals("T { kind = Overseas, words = Words([Word(\"xxxx\")]) }", row);
    }

    /**
     * An element offering several values offers all of them to the collection built around it.
     *
     * <p>Which is the same thing the position itself is owed, one level down. {@code Word} proposes the
     * shortest string its format accepts and the string its minimum asks for, and the decoder settles
     * which; a list that took the first of those and stopped would report the combination as refused
     * while a list of the second is a value the model plainly admits. Collapsing the element to one
     * value moves the defect this whole change is about from the collection to what it holds.
     */
    @Test
    void anElementOfferingSeveralValuesOffersThemAllThroughTheCollection() {
        String row = generatedRow(model("""
                data Word = String
                    invariant shaped = String.matches("x*", value)
                    invariant longEnough = String.length(value) >= 5

                data Words = List<Word>
                    invariant someWords = List.length(value) >= 1
                """, "words: Words", "words = Words([Word(\"xxxxx\")])"));

        assertEquals("T { kind = Overseas, words = Words([Word(\"xxxxx\")]) }", row);
    }

    /** The same of what a map holds under its keys. */
    @Test
    void aValueOfferingSeveralOfThemOffersThemAllThroughTheMap() {
        String row = generatedRow(model("""
                data Word = String
                    invariant shaped = String.matches("x*", value)
                    invariant longEnough = String.length(value) >= 5

                data Words = Map<String, Word>
                    invariant someWords = Map.size(value) >= 1
                """, "words: Words", "words = Words([(\"k\", Word(\"xxxxx\"))])"));

        assertEquals("T { kind = Overseas, words = Words([(\"x\", Word(\"xxxxx\"))]) }", row);
    }

    /**
     * Elements that differ are elements of the type, not numbers counted from nothing.
     *
     * <p>A set of two needs a second value no rule of the element refuses. Counting {@code 0, 1, 2} for
     * distinctness offers a number the element's own bound rejects, and the set is then refused for the
     * element rather than for anything about its size — the same collapse as above, arrived at from the
     * other side.
     */
    @Test
    void aSetsSecondElementComesFromInsideTheElementsOwnBound() {
        String row = generatedRow(model("""
                data Positive = Int
                    invariant atLeastTen = value >= 10

                data Positives = Set<Positive>
                    invariant enough = Set.size(value) >= 2
                """, "ns: Positives", "ns = Positives([Positive(10), Positive(11)])"));

        assertEquals("T { kind = Overseas, ns = Positives([Positive(10), Positive(11)]) }", row);
    }

    /**
     * The same of a map's keys, which have to differ for the same reason.
     *
     * <p>Keyed by a string, because a map that crosses the boundary is a JSON object and its keys are
     * strings (<<e1314>>). The rule the second key has to keep is its length rather than a bound, and
     * it is the same question: a key made without reading the key's own rules is a key the map is
     * refused for.
     */
    @Test
    void aMapsSecondKeyComesFromInsideTheKeysOwnRules() {
        String row = generatedRow(model("""
                data Code = String
                    invariant longEnough = String.length(value) >= 3

                data ByCode = Map<Code, String>
                    invariant enough = Map.size(value) >= 2
                """, "by: ByCode", "by = ByCode([(Code(\"aaa\"), \"a\"), (Code(\"bbbb\"), \"b\")])"));

        assertEquals("T { kind = Overseas, by = ByCode([(Code(\"xxx\"), \"x\"), "
                + "(Code(\"xxxx\"), \"x\")]) }", row);
    }

    /**
     * A key and a value are two parts of one value, so their proposals meet each other.
     *
     * <p>The search a row is found by is over the positions of the behavior, and a map is one of those
     * positions. What its key proposes and what its value proposes are settled inside it or not at all:
     * a key at its second proposal beside a value at its first is a map the model admits, and nothing
     * outside this can arrive at that pair.
     */
    @Test
    void aMapsKeyAndValueProposalsMeetEachOther() {
        String row = generatedRow(model("""
                data Key = String
                    invariant shaped = String.matches("x*", value)
                    invariant longEnough = String.length(value) >= 5

                data Val = String
                    invariant shaped = String.matches("a{5}", value)

                data M = Map<Key, Val>
                    invariant nonEmpty = Map.size(value) >= 1
                """, "m: M", "m = M([(Key(\"xxxxx\"), Val(\"aaaaa\"))])"));

        assertEquals("T { kind = Overseas, m = M([(Key(\"xxxxx\"), Val(\"aaaaa\"))]) }", row);
    }

    /**
     * A proposal a rule was read to build is not spent by how many rules were read before it.
     *
     * <p>Each format contributes the shortest string it accepts and the minimum contributes a string of
     * its length, and the minimum's is last because it is added last. A budget over the whole list drops
     * the one this change exists to add as soon as enough formats are written above it — and drops it
     * only inside a collection, so the same `Word` is writable at a position of its own and not as an
     * element.
     */
    @Test
    void aProposalIsNotDroppedForTheNumberOfRulesReadBeforeIt() {
        String row = generatedRow(model("""
                data Word = String
                    invariant p1 = String.matches("x*", value)
                    invariant p2 = String.matches("x{2,}", value)
                    invariant p3 = String.matches("x{3,}", value)
                    invariant longEnough = String.length(value) >= 5

                data Words = List<Word>
                    invariant nonEmpty = List.length(value) >= 1
                """, "words: Words", "words = Words([Word(\"xxxxx\")])"));

        assertEquals("T { kind = Overseas, words = Words([Word(\"xxxxx\")]) }", row);
    }

    /**
     * A minimum past what a row can carry is not built.
     *
     * <p>The count comes from the model, so a rule can ask for a million of something. Building that
     * would take a row nobody could read to find out what the decoder already says about it, and the
     * bound belongs here rather than on the proposals: what is given up on is a collection this would
     * have invented, not a candidate some rule was read to produce.
     */
    @Test
    void aMinimumPastWhatARowCanCarryIsNotBuilt() {
        // No row of its own, because no fixture anybody can write satisfies the rule — which is what
        // makes this the shape that reaches the builder with the whole count to build.
        String source = """
                module nd.gen

                data Domestic
                data Overseas
                data Kind = Domestic | Overseas

                data Many = List<String>
                    invariant huge = List.length(value) >= 1000000

                data T = { kind: Kind, many: Many }

                behavior look : (t: T) -> Int

                let look (t) = 1
                """;
        souther.compiler.partition.FillResult filled = generated(source);

        assertEquals(List.of(), filled.rows(),
                "no row, rather than one carrying a million elements");
        // Not `ALL_CANDIDATES_REJECTED`. A list of a million exists and somebody could write one; what
        // happened is that this declined to build it, which is a fact about the generator. Reporting it
        // as a refusal would send a reader to look for the rule that refuses a value nothing refuses.
        assertTrue(filled.unresolved().stream().allMatch(left ->
                        left.reason() == Generator.UnresolvedCombination.Reason
                                .NOTHING_COMPOSES_ONE),
                "and said as this not composing one rather than as the model refusing it: "
                        + filled.unresolved());
    }

    /**
     * More pairings of what a map's parts propose than are built at once.
     *
     * <p>Every pair is built before any of them is tried, so the count is what this allocates and not
     * what the search walks. Where it stops short the reader is owed that: values of the shape were
     * built and refused, and more of them exist that nothing here got to.
     *
     * <p>The eight formats hold together — a string of eight letters clears all of them — and that
     * is what makes this about the count rather than about the rules. Written as eight lengths no
     * string has at once, the values reading follows them and shows the declaration admits nothing,
     * and a model refused before a search is asked for is not one this can say anything about.
     *
     * <p>What no pair clears is the ninth rule, which this compiler cannot take apart. Strings
     * clearing all nine exist — a run of b's of even length is one — so the declaration is not an
     * empty one, and what stops a row is that nothing here derives a value from a rule it could not
     * read. Held that way because the rules it can read it meets with each other: eight formats that
     * hold together are eight a proposal clears at once, and a pair built from them is a row.
     */
    @Test
    void moreParingsThanAreBuiltIsSaidAsASearchThatStopped() {
        String formats = "";
        for (int i = 1; i <= 8; i++) {
            formats += "    invariant p%d = String.matches(\"[a-h]{%d,}\", value)\n"
                    .formatted(i, i);
        }
        formats += "    invariant twice = String.matches(\"(b+)\\\\1\", value)\n";
        souther.compiler.partition.FillResult filled = generated("""
                module nd.gen

                data Domestic
                data Overseas
                data Kind = Domestic | Overseas

                data K = String
                %s
                data V = String
                %s
                data M = Map<K, V>
                    invariant nonEmpty = Map.size(value) >= 1

                data T = { kind: Kind, m: M }

                behavior look : (t: T) -> Int

                let look (t) = 1
                """.formatted(formats, formats));

        assertEquals(List.of(), filled.rows(),
                "no row, because the pairings ran out before one of them was tried");
        assertTrue(filled.unresolved().stream().allMatch(left ->
                        left.reason() == Generator.UnresolvedCombination.Reason.THE_SEARCH_LEFT_SOMETHING_UNTRIED),
                "and the pairings this did not build are said as a search that stopped: "
                        + filled.unresolved());
    }

    /**
     * A map nothing was paired for is not a search that stopped.
     *
     * <p>{@code /= 0} says the map is not empty, and a count is never below none, so what it says is
     * that the map holds at least one pair. A minimum is read, pairings are built for it, and what
     * stops is the search for a key and a value that clear eight rules apiece. So the reason is the
     * search reaching its limit, and it is the reason because that is what happened.
     *
     * <p>This asked for the other reason while a disequality reached the domain as nothing at all:
     * no minimum was read, the position offered the empty map, and it was refused. The distinction
     * still matters — a reader told a search stopped would go looking for the pairing it stopped
     * short of — but a map the rules will not let be empty is no longer an example of it.
     *
     * <p>The ninth rule is one this compiler cannot take apart, which is what leaves the parts
     * unfound: the rules it can read are met with each other and a value clearing all of them is
     * proposed, so eight formats that hold together are eight a single proposal clears.
     */
    @Test
    void aMapWhoseSearchStoppedSaysSoRatherThanCallingItARefusal() {
        String formats = "";
        for (int i = 1; i <= 8; i++) {
            formats += "    invariant p%d = String.matches(\"[a-h]{%d,}\", value)\n"
                    .formatted(i, i);
        }
        formats += "    invariant twice = String.matches(\"(b+)\\\\1\", value)\n";
        souther.compiler.partition.FillResult filled = generated("""
                module nd.gen

                data Domestic
                data Overseas
                data Kind = Domestic | Overseas

                data K = String
                %s
                data V = String
                %s
                data M = Map<K, V>
                    invariant notEmpty = Map.size(value) /= 0

                data T = { kind: Kind, m: M }

                behavior look : (t: T) -> Int

                let look (t) = 1
                """.formatted(formats, formats));

        assertEquals(List.of(), filled.rows(), "no key and value clearing all eight rules was found");
        assertTrue(filled.unresolved().stream().allMatch(left ->
                        left.reason() == Generator.UnresolvedCombination.Reason
                                .THE_SEARCH_LEFT_SOMETHING_UNTRIED),
                "the pairing was built and the search for its parts is what stopped: "
                        + filled.unresolved());
    }

    /**
     * A type written in terms of itself is descended into and comes back.
     *
     * <p>A tree holds trees, so composing one is composing another, and what makes that stop is that
     * a list may be empty. The descent has to find that rather than follow the name round again.
     *
     * <p>This used to be asked of a type with no value at all — a sum every case of which held the
     * sum — because that was the shape of a base-less recursion the front end admitted, and what came
     * back was the refusal rather than a row. It is refused at the declaration now, so no model
     * reaching the descent carries one, and the question left here is the one about descending: a
     * recursion that does bottom out is walked and answered.
     */
    @Test
    void aTypeWrittenInTermsOfItselfIsDescendedIntoAndComesBack() {
        souther.compiler.partition.FillResult filled = generated("""
                module nd.gen

                data Domestic
                data Overseas
                data Kind = Domestic | Overseas

                data Tree = { kids: List<Tree> }

                data T = { kind: Kind, tree: Tree }

                behavior look : (t: T) -> Int

                let look (t) = 1
                """);

        assertFalse(filled.rows().isEmpty(), "a tree with no kids is a tree");
        assertEquals(List.of(), filled.unresolved(),
                "and the descent came back rather than following the name round again: "
                        + filled.unresolved());
    }

    /**
     * A chain of names is not a type written in terms of itself.
     *
     * <p>What the descent has to give up on is a value inside itself, and how many names a value wears
     * on the way down is not that. A count of levels cannot tell the two apart, so it is the names being
     * expanded that say when to stop.
     */
    @Test
    void aLongChainOfNamesIsStillBuilt() {
        String row = generatedRow(model("""
                data L1 = String
                data L2 = L1
                data L3 = L2
                data L4 = L3
                data L5 = L4

                data Deep = List<L5>
                    invariant someDeep = List.length(value) >= 1
                """, "deep: Deep", "deep = Deep([L5(L4(L3(L2(L1(\"a\")))))])"));

        assertEquals("T { kind = Overseas, deep = Deep([L5(L4(L3(L2(L1(\"x\")))))]) }", row);
    }

    /**
     * A rule the reader has nothing to say about leaves the position where it was.
     *
     * <p>The guarantee is that a minimum this recognises is used, not that a witness is found for
     * whatever anyone writes. A rule of a shape nothing here reads still refuses every candidate, and
     * that is still reported as a refusal rather than as a combination nobody can write a row for.
     */
    @Test
    void aRuleNothingHereReadsStillLeavesTheCombinationRefusedAndSaidSo() {
        String source = model("""
                data Tags = List<String>
                    invariant distinct = List.allDistinctBy(x -> x, value)
                        && List.length(value) >= 2
                    invariant notTwo = List.length(value) /= 2
                """, "tags: Tags", "tags = Tags([\"a\", \"b\", \"c\"])");
        souther.compiler.partition.FillResult filled = generated(source);

        assertEquals(List.of(), filled.rows(), "two equal elements is not a list of two distinct ones");
        assertTrue(filled.unresolved().stream().allMatch(left ->
                        left.reason() == Generator.UnresolvedCombination.Reason
                                .ALL_CANDIDATES_REJECTED),
                "refused, which is not a claim that no row can be written: "
                        + filled.unresolved());
    }

    // --- the row as text ---------------------------------------------------------------------------

    /**
     * A row is offered as text for somebody to paste into a model, so the text has to come back as the
     * value it was built from. Held against the compiler rather than against a spelling: a template
     * that prints a map entry a reader does not accept would satisfy every assertion above.
     */
    @Test
    void theTextOfAGeneratedRowIsAnExampleThatCompiles() {
        String declarations = """
                data Tags = List<String>
                    invariant someTags = List.length(value) >= 2

                data Codes = Set<String>
                    invariant someCodes = Set.size(value) >= 2

                data Props = Map<String, String>
                    invariant someProps = Map.size(value) >= 2

                data Name = String
                    invariant longEnough = String.length(value) >= 3
                """;
        String fields = "tags: Tags, codes: Codes, props: Props, name: Name";
        String written = "tags = Tags([\"a\", \"b\"]), codes = Codes([\"a\", \"b\"]), "
                + "props = Props([(\"k\", \"v\"), (\"l\", \"w\")]), name = Name(\"abc\")";
        String row = generatedRow(model(declarations, fields, written));

        String withTheRow = model(declarations, fields, written)
                + "    | (" + row + ") -> 1\n";
        Compilation again = Compilation.ofSource(withTheRow, "Main");
        again.answerEverything();

        assertEquals(List.of(), again.diagnostics().values().stream().flatMap(List::stream).toList(),
                "the generated row is written the way a fixture is read: " + row);
    }
}
