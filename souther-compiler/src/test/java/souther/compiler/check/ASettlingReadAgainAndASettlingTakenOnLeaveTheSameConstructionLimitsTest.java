package souther.compiler.check;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import souther.compiler.conformance.RepositoryModels;
import souther.compiler.numeric.Count;
import souther.compiler.numeric.Endpoint;
import souther.compiler.numeric.NumericDomain;
import souther.compiler.query.Bodies;
import souther.compiler.query.Compilation;
import souther.compiler.query.Front;
import souther.compiler.types.Type;
import souther.compiler.types.TypeSymbol;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Reading a declaration with a coordinate settled and taking the settling onto a reading already
 * made answer the same thing about building a value.
 *
 * <p>What a search choosing one position at a time asks for at each of them is where the value
 * there stops and how many it holds, both under whatever the positions before it took. Those two
 * are the whole of what the rules give it. Read a declaration per position and they are answered by
 * a reading made under the settling; taken onto a reading made once, they are answered by a
 * projection of the constraints that reading came to — and this is the statement that the two are
 * the same question.
 *
 * <p>The statement is what lets one reading serve a whole search. Without it a search that read the
 * declaration once would be answering about the rules as they stood before it chose anything, which
 * is the reading that leaves {@code b} its whole range while {@code a < b} is still open.
 */
@Tag("population")
class ASettlingReadAgainAndASettlingTakenOnLeaveTheSameConstructionLimitsTest {

    /**
     * Every record the models reach, every settling of one or two of its coordinates.
     *
     * <p>The settlings are taken from what each coordinate's own reading leaves it, plus the two
     * numbers any counted position can take. A pair as well as a single, because a rule relating two
     * positions says nothing until one of them is fixed and the whole point of settling one at a
     * time is what it leaves the other.
     */
    @Test
    void everySettlingLeavesWhatAReadingUnderItLeaves() {
        int compared = 0;
        int stopped = 0;
        int counted = 0;
        for (Record record : recordsRead()) {
            FieldDomains base = FieldDomains.of(record.declared(), record.source(), record.policy(),
                    DeclarationReadings.NONE);
            for (Map<RuleKey, Count> settling : settlings(base, record.fields())) {
                FieldDomains readUnder = FieldDomains.of(record.declared(), record.source(),
                        record.policy(), settling, DeclarationReadings.NONE);
                FieldDomains.Composing takenOn = base.composing(named(settling));
                for (RuleKey field : record.fields()) {
                    NumericDomain.Bounds values = readUnder.at(field).bounds();
                    FieldDomains.Held held = readUnder.heldAt(field);
                    FieldDomains.ConstructionLimits limits = takenOn.at(field);
                    assertEquals(values, limits.values(),
                            () -> "where " + record.declared() + " is read with " + settling
                                    + " settled, `" + field + "` stops where the same settling "
                                    + "taken onto one reading says it stops");
                    assertEquals(held, limits.held(),
                            () -> "where " + record.declared() + " is read with " + settling
                                    + " settled, `" + field + "` holds what the same settling "
                                    + "taken onto one reading says it holds");
                    compared++;
                    stopped += values == null ? 0 : 1;
                    counted += held == null ? 0 : 1;
                }
            }
        }
        assertFalse(compared == 0,
                "no record of any model this repository carries was compared, so this holds by "
                        + "asking nothing");
        // And that what was compared says something. Two readings agreeing that nothing is known
        // about every coordinate is an agreement either of them could reach by answering nothing,
        // and it is the answer a projection that read the wrong state would give.
        assertFalse(stopped == 0, "no coordinate was stopped anywhere, so every comparison above "
                + "was of one absent range against another");
        assertFalse(counted == 0, "no coordinate was counted, so nothing above compared what a "
                + "position holds");
        System.out.println("construction limits compared: " + compared + ", of them " + stopped
                + " stopped and " + counted + " counted");
    }

    /** One record declaration, read where its rules are. */
    private record Record(TypeSymbol.AtModule declared, RuleReadingSource source,
                          ReadingPolicy policy, List<RuleKey> fields) {}

    /**
     * The settlings asked about: each coordinate at each value it could take, and each pair of them
     * at the first value each could.
     */
    private static List<Map<RuleKey, Count>> settlings(FieldDomains base, List<RuleKey> fields) {
        Map<RuleKey, List<Count>> take = new LinkedHashMap<>();
        for (RuleKey field : fields) {
            take.put(field, values(base.at(field).bounds()));
        }
        List<Map<RuleKey, Count>> out = new ArrayList<>();
        for (RuleKey field : fields) {
            for (Count at : take.get(field)) {
                out.add(Map.of(field, at));
            }
        }
        for (int i = 0; i < fields.size(); i++) {
            for (int j = i + 1; j < fields.size(); j++) {
                List<Count> one = take.get(fields.get(i));
                List<Count> other = take.get(fields.get(j));
                if (one.isEmpty() || other.isEmpty()) {
                    continue;
                }
                Map<RuleKey, Count> both = new LinkedHashMap<>();
                both.put(fields.get(i), one.get(0));
                both.put(fields.get(j), other.get(0));
                out.add(both);
            }
        }
        return out;
    }

    /** What one coordinate is settled at: where its own rules stop it, and the two numbers a
     *  coordinate with no end still takes. */
    private static List<Count> values(NumericDomain.Bounds bounds) {
        Set<Count> out = new LinkedHashSet<>();
        out.add(new Count(BigDecimal.ZERO));
        out.add(new Count(BigDecimal.ONE));
        if (bounds != null) {
            end(bounds.min(), out);
            end(bounds.max(), out);
        }
        return List.copyOf(out);
    }

    private static void end(Endpoint endpoint, Set<Count> out) {
        if (endpoint != null && endpoint.at() instanceof Count at) {
            out.add(at);
        }
    }

    /** The same settling as the names a reading of the value writes it under. */
    private static Map<NumberAt<RuleKey>, Count> named(Map<RuleKey, Count> settling) {
        Map<NumberAt<RuleKey>, Count> out = new LinkedHashMap<>();
        settling.forEach((field, at) -> out.put(NumberAt.valueOf(field), at));
        return out;
    }

    /** Every record every behavior of every model this repository carries reads, and the ones
     *  under them. */
    private static List<Record> recordsRead() {
        List<Record> out = new ArrayList<>();
        for (Compilation compilation : RepositoryModels.all()) {
            ReadingPolicy policy = compilation.db().ask(new Front.Reading()).value();
            for (String module : compilation.modules()) {
                RuleReadingSource source = RuleReadings.of(compilation, module);
                Set<TypeSymbol> seen = new LinkedHashSet<>();
                for (DeclaredSig declared
                        : compilation.db().ask(new Bodies.DeclaredSignatures(module)).value()
                                .values()) {
                    for (DeclaredSig.Input input : declared.inputs()) {
                        under(input.type(), source, policy, seen, out);
                    }
                }
            }
        }
        return out;
    }

    private static void under(Type type, RuleReadingSource source, ReadingPolicy policy,
                              Set<TypeSymbol> seen, List<Record> out) {
        if (!(TypeView.of(type, source.symbols()).shape()
                instanceof Shape.Product(TypeSymbol.AtModule declared, Map<String, Type> fields))) {
            return;
        }
        if (!seen.add(declared)) {
            return;
        }
        List<RuleKey> keys = new ArrayList<>();
        fields.keySet().forEach(field -> keys.add(RuleKey.of(field)));
        if (!keys.isEmpty()) {
            out.add(new Record(declared, source, policy, keys));
        }
        fields.values().forEach(field -> under(field, source, policy, seen, out));
    }
}
