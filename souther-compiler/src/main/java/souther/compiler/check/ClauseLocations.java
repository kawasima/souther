package souther.compiler.check;

import souther.compiler.diag.DiagnosticPlace;

/**
 * Where a clause a report names is written.
 *
 * <p>Asked here and not carried from the reading that judged the clause. Which clause it is, is
 * {@link Clause.Id} and follows from the declaration; where it is written follows from the text of
 * that declaration and from nothing the reading did. Carried along with the judgment, an edit that
 * moves the clause and changes nothing it states leaves every reader of the judgment to be worked
 * out again — and the reader that keeps its answer keeps the place the clause used to be at.
 *
 * <p>So a place is looked up where a sentence is written, by the reader that is about to point
 * somewhere, and by nobody else. What a clause states and where it is written are two facts about
 * one declaration, and a reader asks for the one it uses.
 *
 * <p>Every clause is answered. A clause of a module this compile has no source for is
 * {@link DiagnosticPlace.Unavailable}, which says where the code came from — the absence of a file
 * is not the absence of an answer.
 */
@FunctionalInterface
public interface ClauseLocations {

    /** Where {@code clause} is written. */
    DiagnosticPlace of(Clause.Id clause);

    /**
     * Nothing declared anywhere — the other half of {@link ExpandedClauseLookup#NONE}, for a reading
     * over primitives.
     *
     * <p>It refuses rather than answering. A reading that has no clauses has no clause to report
     * about, so an ask here is a reader pointing at something nothing declared; answered with a
     * place that points nowhere, that reader would publish a sentence about a clause no author
     * wrote.
     */
    ClauseLocations NONE = clause -> {
        throw new IllegalStateException("nothing declares a clause, so " + clause
                + " is written nowhere");
    };
}
