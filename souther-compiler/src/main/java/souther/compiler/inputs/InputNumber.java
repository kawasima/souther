package souther.compiler.inputs;

import souther.compiler.check.NumericMeasures;
import souther.compiler.check.RuleReadingSource;
import souther.compiler.check.Symbols;
import souther.compiler.check.WalkElements;
import souther.compiler.core.Core;
import souther.compiler.types.Type;

/**
 * Which number of a behavior's input an expression names, or nothing where it names none.
 *
 * <p>The companion of {@link InputPath}, one question further in. That answers which location an
 * expression points at; this answers which number a rule about it is written on, which is the
 * location's own content or something taken of it — and the two are not the same answer, because a
 * position holding a string is a position holding no number while every rule about its length is
 * about one.
 *
 * <p>One answer, for every reader that has to say which number a comparison is about. The reading
 * that draws lines and the reading that names what a run was steered by both ask it, and each
 * working it out for itself is two accounts of one number: matched afterwards by how a path is
 * spelled, two numbers taken of one location are one, which is the whole of what a line drawn on
 * the second of them is lost to.
 *
 * <p>Which of the standard library's calls take a number of a location is asked of
 * {@link NumericMeasures} rather than decided here, and asked of the operation the call resolved to
 * rather than of its spelling.
 */
public final class InputNumber {

    private InputNumber() {
    }

    /**
     * The number {@code e} names.
     *
     * <p>The argument of a taking has to be a location: {@code List.length(List.map(f, xs))} counts
     * something no path names, and a boundary on it could not be looked for in a row.
     */
    public static NumericTerm of(Core e, InputDomain inputs, InputReads reads,
                                 RuleReadingSource source) {
        Symbols symbols = source.symbols();
        NumericMeasures.Measured measured = NumericMeasures.takenIn(e);
        if (measured != null) {
            // A taking is of a location, so an argument that stands at none is one there is no
            // location to take it of.
            TermPath of = switch (reads.pathOf(measured.of(), symbols)) {
                case PathResolution.At(var at) -> at;
                case PathResolution.NotAPosition _ -> null;
                // A taking is of one location, and a name that only may stand at one is no one of
                // them. Taken of any, the number would be a size of a sequence the run it is on
                // never walked.
                case PathResolution.MayStandAt _ -> null;
            };
            if (of != null) {
                return NumericTerm.TakenOf.of(measured.operation(), of,
                        inputs.typeAt(of, source), source.inners(), symbols);
            }
            // A location the operation is not taken of, or a value standing at none. The second is
            // a walk's answer, and a number over the values it walked is a term of its own where
            // those values are read from a place.
            return overARun(measured, inputs, reads, source);
        }
        // And a number of the input is the value at a position, so an expression naming none names
        // no number here.
        return switch (reads.pathOf(e, symbols)) {
            case PathResolution.At(var at) -> new NumericTerm.ValueOf(at);
            case PathResolution.NotAPosition _ -> null;
            // And a number of the input is the value at one position. A name standing at one of
            // several would be a number at whichever of them a reader picked, and a line drawn on
            // it would fall at a place the rule may say nothing about.
            case PathResolution.MayStandAt _ -> null;
        };
    }

    /**
     * The number {@code measured} takes over the values a walk was given, or null where those
     * values are not read from a place.
     *
     * <p>Three answers have to be in hand, and each is somebody else's. That the walk answers one
     * value per element of what it was given is a fact about the operation that handed the closure
     * its elements, proved before the tree was rewritten. Where in an element the answer stands is
     * what the closure came to, read once and kept as the way there. And which position the
     * elements are at is the reading of the input's, as it is for every other term.
     *
     * <p>What the walk itself supplies is the element's binding and nothing more. The form it has
     * now is what a rewrite left, so it is asked for an identity and not for a meaning: reading the
     * answer off the shape would make the walks a rule can be written over a consequence of which
     * ones that rewrite happens to recognise, and a walk an author wrote by hand would be read as
     * a {@code map}.
     *
     * <p>The walk is met under whatever name is in scope, and what a name stands for is asked of
     * the reading that owns the question ({@link InputReads#meaningOf}) rather than read off the
     * tree. A model of any size binds the mapped list before totalling it, and a route that walked
     * only the expression as written would answer one thing for
     * {@code List.sum(List.map(f, xs))} and another for the same rule with a name in the middle —
     * which is a `let` changing what a model means. The environment the value was given in comes
     * with it, so what is read of the walk afterwards is read where the walk stands.
     *
     * <p>Null wherever any of the three is missing, which is a rule this compiler did not read
     * rather than a rule the model does not state — and is reported as one. Null too where the three
     * are in hand and what they come to is not one run
     * ({@link RunSource#overTheOccurrencesAt}), which is the same answer for the same reason: a
     * reading short rather than a line somewhere it does not go.
     */
    private static NumericTerm overARun(NumericMeasures.Measured measured, InputDomain inputs,
                                        InputReads reads, RuleReadingSource source) {
        Symbols symbols = source.symbols();
        // The walk and the names it stands under, which travel together: a name bound inside a
        // helper stands for what the call handed over, and what is read of that afterwards is read
        // where it stands rather than where the name was.
        Denotation met = reads.denotes(measured.of(), symbols);
        Core walk = met.value();
        InputReads where = met.at();
        souther.compiler.types.BindingId element =
                WalkElements.elementBindingOf(walk, where, symbols);
        if (element == null) {
            return null;
        }
        ElementProjection answered = where.projectionAt(element);
        // Where the elements stand, and nothing where they stand nowhere: a run is over the values
        // at a position, so a container this reading could not place leaves no run to take.
        TermPath at = switch (where.elementAt(element, symbols)) {
            case PathResolution.At(var stands) -> stands;
            case PathResolution.NotAPosition _ -> null;
            // A run is over the values at one position, and a walk whose elements come from more
            // than one container is no one run.
            case PathResolution.MayStandAt _ -> null;
        };
        if (answered == null || at == null) {
            return null;
        }
        TermPath under = answered.from(at);
        // Whether what is read from there is one run is the run's own question, and it is asked
        // rather than assumed: a walk over a sequence inside another sequence is over some of the
        // occurrences of its path, and a term made of it would state of every one of them what the
        // model says of those.
        RunSource over = RunSource.overTheOccurrencesAt(under);
        if (over == null) {
            return null;
        }
        Type stands = inputs.typeAt(under, source);
        return stands == null ? null
                : NumericTerm.TakenOver.of(measured.operation(), over, stands, source.inners(),
                        symbols);
    }

}
