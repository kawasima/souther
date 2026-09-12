package souther.compiler.conformance;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import souther.compiler.partition.StoodInAnswer;
import souther.compiler.query.Adequacy;
import souther.compiler.query.Compilation;
import souther.compiler.query.GenerationScope;
import souther.compiler.query.OfferedRow;
import souther.compiler.query.Offering;
import souther.compiler.query.OfferingRequest;
import souther.compiler.query.RequiredDependencies;
import souther.compiler.types.ValueName;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A row this hands a person is a row that runs.
 *
 * <p>Which takes standing in for every behavior its target depends on, whether or not the thing it
 * was composed for is about what one of them answers. A row short of one is refused the moment it
 * is pasted, and refused for a reason that is about the block rather than about the model — so a
 * block that offers one has answered a person's question with work they cannot use.
 *
 * <p>Asked of the rows and not of the composer. Whether a stand-in was composed is one question and
 * whether the row carrying it goes out is another, and they are answered in different places: the
 * second is what this holds, because it is the one an author meets.
 *
 * <p>Over the corpus rather than over a model written here. What this is about is every shape of
 * body the corpus holds — a dependency answered by a union of cases, two dependencies where the
 * decision reads one, a dependency inside a composition — and a model written for the test would
 * be the shapes somebody remembered.
 */
@Tag("population")
class EveryRowOfferedStandsInWhatItsTargetRequiresTest {

    @Test
    void everyRowNamesEveryDependencyItsTargetRequires() {
        List<String> short_ = new ArrayList<>();
        int rows = 0;
        for (ConformanceCorpus corpus : ConformanceCorpus.all()) {
            Compilation compilation = corpus.analyse().compilation();
            for (String module : compilation.modules()) {
                Offering offering = Adequacy.offeredFor(compilation.db(),
                        new OfferingRequest(module, new GenerationScope.Module()));
                if (offering == null) {
                    continue;
                }
                for (var behavior : offering.rowsByBehavior().entrySet()) {
                    RequiredDependencies requires =
                            RequiredDependencies.of(compilation.db(), module, behavior.getKey());
                    if (requires == null) {
                        continue;
                    }
                    for (OfferedRow row : behavior.getValue()) {
                        rows++;
                        Set<ValueName.Behavior> stood = new LinkedHashSet<>();
                        for (StoodInAnswer each : row.answers()) {
                            stood.add(each.dependency());
                        }
                        for (RequiredDependencies.Required each : requires.inOrder()) {
                            if (!stood.contains(each.dependency())) {
                                short_.add(module + "." + behavior.getKey() + " (" + row.key()
                                        .inputs() + ") stands nothing in for " + each.dependency());
                            }
                        }
                    }
                }
            }
        }
        assertFalse(rows == 0, "found no offered rows at all — the walk missed the corpus");
        assertEquals(List.of(), short_,
                "a row offered without a stand-in its target requires is one nothing applies");
    }
}
