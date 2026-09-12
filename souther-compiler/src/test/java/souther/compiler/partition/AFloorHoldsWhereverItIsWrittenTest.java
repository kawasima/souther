package souther.compiler.partition;

import org.junit.jupiter.api.Test;

import souther.compiler.ast.Hir;
import souther.compiler.check.RuleReadingContext;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.RuleReadings;
import souther.compiler.check.FieldDomains;
import souther.compiler.check.RuleKey;
import souther.compiler.check.TypeView;
import souther.compiler.query.Compilation;
import souther.compiler.types.Type;
import souther.compiler.types.TypeKey;
import souther.compiler.types.TypeSymbols;
import souther.compiler.types.TypeSymbol;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * How much a position has to hold, where two things can say so.
 *
 * <p>A rule counting a value is written either on that value's own type or on the record that has it
 * as a field, and both are rules the construction has to satisfy. Read one of them and a position is
 * offered a value the other refuses — which is #650, where a field's list was offered the empty one
 * because the floor was the record's and only the type's was read.
 */
class AFloorHoldsWhereverItIsWrittenTest {

    private record Model(RuleReadingSource rules, String module) {

        FieldDomains domainsOf(String type) {
            TypeSymbol.AtModule named = TypeSymbols.declared(new TypeKey(module, type));
            Hir.Data data = (Hir.Data) rules.symbols().declaredNode(named.key());
            assertNotNull(data, "no `" + type + "`");
            return FieldDomains.of(named, rules, souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
        }

        Type ref(String type) {
            return new Type.Ref(TypeSymbols.declared(new TypeKey(module, type)));
        }

        /** The world these readings are made in, with nothing to borrow from. */
        RuleReadingContext reading() {
            return RuleReadingContext.unshared(rules,
                    souther.compiler.query.ReadAs.THE_COMPILATION_DOES);
        }

        /** The floor of a position of {@code type}, read as this model's rules leave it. */
        int floorOf(Type type) {
            return Partitions.leastHeld(
                    TypeView.of(type, rules.symbols(), rules.published()), reading());
        }

        /** The same, where the record the position sits in has a rule about it too. */
        int floorOf(Type type, FieldDomains.Held held) {
            return Partitions.leastHeld(
                    TypeView.of(type, rules.symbols(), rules.published()), reading(), held);
        }
    }

    /** A model to read floors off, held to compiling. A rule the language refuses still reaches this
     * reader and still comes back a number, so a model asserted only to have symbols can pin a floor
     * that no program could have written. */
    private static Model modelOf(String source) {
        Compilation compilation = Compilation.ofSource(source, "Main");
        compilation.answerEverything();
        String module = compilation.modules().get(0);
        RuleReadingSource rules = RuleReadings.of(compilation, module);
        assertNotNull(rules, "the model did not compile");
        assertEquals(List.of(), compilation.diagnostics().values().stream()
                        .flatMap(List::stream).map(each -> each.diagnostic().code()).toList(),
                "the model under test is a program that can be written");
        return new Model(rules, module);
    }

    /** The record's rule, at a field whose own type says nothing. */
    @Test
    void aRecordsRuleIsAFloorAtTheFieldItCounts() {
        Model model = modelOf("""
                module example.bag

                data Bag =
                    { xs: List<Int>
                    }
                    invariant atLeastTwo = List.length(xs) >= 2
                """);

        assertEquals(2, model.floorOf(new Type.ListOf(Type.INT),
                model.domainsOf("Bag").heldAt(RuleKey.of("xs"))));
    }

    /** And the type's own rule where the record says nothing, which is the reading that already
     * worked and has to keep working. */
    @Test
    void aTypesOwnRuleIsStillAFloorWhereNoRecordSpeaks() {
        Model model = modelOf("""
                module example.bag

                data NonEmpty = List<Int>
                    invariant atLeastOne = List.length(value) >= 1

                data Bag = { xs: NonEmpty }
                """);

        assertEquals(1, model.floorOf(model.ref("NonEmpty"),
                model.domainsOf("Bag").heldAt(RuleKey.of("xs"))));
    }

    /**
     * And the higher of the two where both speak.
     *
     * <p>Both are rules the construction has to satisfy, so a value clearing one and not the other is
     * refused as surely as one clearing neither. Taking the record's alone would offer a list of two
     * where the type asks for three; taking the type's alone is #650 again.
     */
    @Test
    void theHigherOfTheTwoIsWhatHasToHold() {
        Model model = modelOf("""
                module example.bag

                data AtLeastThree = List<Int>
                    invariant atLeastThree = List.length(value) >= 3

                data Bag =
                    { xs: AtLeastThree
                    }
                    invariant atLeastTwo = List.length(xs.value) >= 2
                """);

        assertEquals(3, model.floorOf(model.ref("AtLeastThree"),
                model.domainsOf("Bag").heldAt(RuleKey.of("xs"))));
    }

    /**
     * A rule bounding only a sum of two counts is a floor under neither of them.
     *
     * <p>Either list may hold nothing as long as the other does not, so the value that holds nothing
     * is one the rule admits at both positions. Held because a reading that finds a floor here is one
     * that would put a floor under every field a rule so much as names.
     */
    @Test
    void aRuleOverASumOfTwoCountsIsAFloorUnderNeither() {
        Model model = modelOf("""
                module example.duplicate

                data Possible =
                    { accounts: List<Int>
                    , contacts: List<Int>
                    }
                    invariant oneOfThem = List.length(accounts) + List.length(contacts) >= 1
                """);
        FieldDomains domains = model.domainsOf("Possible");

        assertEquals(0, model.floorOf(new Type.ListOf(Type.INT),
                domains.heldAt(RuleKey.of("accounts"))), "an empty list of accounts stands beside a contact");
        assertEquals(0, model.floorOf(new Type.ListOf(Type.INT),
                domains.heldAt(RuleKey.of("contacts"))), "and the same the other way round");
    }

    /** A rule a layer down is the outer name's rule too: the reader reaches every name the value
     * wears, so which layer the author wrote it on does not change what the value has to hold. */
    @Test
    void aFloorWrittenUnderANameIsReadAtTheNameOverIt() {
        Model model = modelOf("""
                module example.chain

                data Kids = Inner

                data Inner = List<Int>
                    invariant atLeastOne = List.length(value) >= 1
                """);

        assertEquals(1, model.floorOf(model.ref("Kids")));
    }

    /**
     * A count is not always a count of elements.
     *
     * <p>{@code String.length} is one of the measures, so a string's rule reaches this reader and
     * comes back a number like any other. What that number counts is settled by the type it was read
     * on, and a caller taking every floor above zero for a collection that cannot be empty would be
     * reading characters as elements.
     */
    @Test
    void aStringsRuleIsAFloorOnItsCharacters() {
        Model model = modelOf("""
                module example.name

                data Name = String
                    invariant nonEmpty = String.length(value) >= 1
                """);

        assertEquals(1, model.floorOf(model.ref("Name")));
    }
}
