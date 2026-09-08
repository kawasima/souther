package souther.compiler.coverage;

import souther.compiler.diag.Citation;

/**
 * Where a report about an arm points, for a surface that is about to write one.
 *
 * <p>What an arm carries is which of the two places a report about it names
 * ({@link ArmReportAnchor}); this is what turns that into somewhere to put a caret. Held apart so
 * that the value crossing a module boundary says what the model is and nothing about where any of
 * it is written, and so that a surface that never writes a sentence never asks.
 *
 * <p>Handed to a surface rather than reached for. What a place is, is the compilation's answer, and
 * a surface that worked one out from something it was holding would be reading a position off a
 * copy — which is the reading this exists to replace.
 */
@FunctionalInterface
public interface ArmLocations {

    /** Where a report about the arm {@code anchor} belongs to points. */
    Citation of(ArmReportAnchor anchor);
}
