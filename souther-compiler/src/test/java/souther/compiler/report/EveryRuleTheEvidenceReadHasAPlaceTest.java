package souther.compiler.report;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import souther.compiler.check.RuleCitation;
import souther.compiler.conformance.RepositoryModels;
import souther.compiler.query.Compilation;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every rule a compilation read has somewhere a report can send a reader.
 *
 * <p>Where a rule with no name is shown is worked out once, when the report is assembled, and a
 * page asked for one it was not assembled with raises rather than pointing nowhere. So what the
 * report is assembled with has to be every rule its evidence read — not the ones some list of
 * arrays happened to hold, which is what it was, and which left the readings that compose a
 * position's classes with no place at all.
 *
 * <p><b>Held over every model this repository carries.</b> The subject is the assembly and not one
 * source: a rule reaches a page by whichever reading found it, and a population of one model is an
 * answer about the readings that model happens to raise.
 */
@Tag("population")
class EveryRuleTheEvidenceReadHasAPlaceTest {

    @Test
    void aPageIsAssembledWithEveryRuleItsMeasuresRead() {
        int checked = 0;
        for (Compilation compilation : RepositoryModels.all()) {
            for (AdequacyReport.ModuleReport module : AdequacyReport.of(compilation).modules()) {
                for (AdequacyReport.BehaviorReport behavior : module.behaviors()) {
                    Set<RuleCitation.Written> read = written(behavior.evidence().ruleCitations());
                    assertTrue(behavior.rulePlaces().keySet().containsAll(read),
                            () -> "every rule `" + behavior.name() + "`'s measures read has a"
                                    + " place: " + missing(read, behavior.rulePlaces().keySet()));
                    checked += read.size();
                }
            }
        }
        // The models walked have rules a reader is sent to by place. Asked of a population with
        // none, the assertion above holds of a report that is assembled with nothing.
        assertTrue(checked > 0, "the models walked hold rules a page sends a reader to by place");
    }

    /**
     * And the one rule in this repository that reaches a page by having divided a position is one
     * of them.
     *
     * <p>The population the assertion above runs over holds exactly one such reading, so the
     * subset it measures is satisfied by a closure that reads none of them wherever that one
     * happens to be cited by something else. This names it.
     */
    @Test
    void includingTheOneThatDividedAPosition() {
        Set<String> found = new LinkedHashSet<>();
        for (Compilation compilation : RepositoryModels.all()) {
            for (AdequacyReport.ModuleReport module : AdequacyReport.of(compilation).modules()) {
                for (AdequacyReport.BehaviorReport behavior : module.behaviors()) {
                    if (behavior.partition() == null) {
                        continue;
                    }
                    behavior.partition().axes().forEach(axis -> axis.divides().forEach(each -> {
                        if (each.cited() instanceof RuleCitation.Written written) {
                            found.add(module.module() + "." + behavior.name());
                            assertTrue(behavior.rulePlaces().containsKey(written),
                                    () -> "the rule that divided `" + axis.name() + "` of `"
                                            + behavior.name() + "` has a place");
                        }
                    }));
                }
            }
        }
        assertTrue(found.contains("conformance.waitlist.seat"),
                () -> "the models carry a behavior whose rule divided a position by telling a set"
                        + " of its values from the rest: " + found);
    }

    private static Set<RuleCitation.Written> written(Set<RuleCitation> cited) {
        Set<RuleCitation.Written> out = new LinkedHashSet<>();
        for (RuleCitation each : cited) {
            if (each instanceof RuleCitation.Written it) {
                out.add(it);
            }
        }
        return out;
    }

    private static Set<RuleCitation.Written> missing(Set<RuleCitation.Written> read,
                                                     Set<RuleCitation.Written> shown) {
        Set<RuleCitation.Written> out = new LinkedHashSet<>(read);
        out.removeAll(shown);
        return out;
    }
}
