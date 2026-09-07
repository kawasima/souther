package souther.compiler.publish;

import souther.compiler.source.SourceId;

import java.util.List;

import tools.jackson.databind.node.ObjectNode;

/**
 * What one entry of a document is about, as the document writes it.
 *
 * <p>A sum and not one shape with an id and a label. What names a module is a name; what names a
 * point is a line, a level and a side; what names a position is a spelling for a person and a
 * second one that tells two of them apart. Held as one product, every arm would carry the fields of
 * every other and a consumer would be left reading which of them are filled in to find out what it
 * was handed.
 *
 * <p><b>Two answers where the two differ, and one word where they do not.</b> What a reader is
 * shown and what tells two entries apart are not always the same thing. A position spelled as an
 * author would recognise it can be the spelling of two positions this compiler holds apart, and the
 * spelling that separates them is one its own owner says is for a message about this compiler and
 * never about a model. So both are written: the surface is what a person reads and the identity is
 * what a consumer joins and what an order is taken over. It is the split the rule handles already
 * make, where {@code rule} is the reader's and {@code ruleId} is the document's.
 *
 * <p>{@link #identity()} is what the canonical order reads, and it has to tell apart every two
 * subjects the compiler holds apart. A reader-facing spelling is allowed not to.
 */
public sealed interface PublishedSubject {

    /** What kind of place this is, in the document's own word. */
    SubjectWord kind();

    /**
     * What tells this apart from every other subject in one document.
     *
     * <p>Never a reader-facing spelling where the two differ. Two entries this cannot tell apart
     * are two the canonical order writes in whichever order they arrived, which is the one thing
     * the arrangement refuses.
     */
    String identity();

    /** One module. */
    record OfAModule(String module) implements PublishedSubject {

        @Override
        public SubjectWord kind() {
            return SubjectWord.MODULE;
        }

        @Override
        public String identity() {
            return module;
        }
    }

    /** One behavior, as the facts about it name it. */
    record OfABehavior(String behavior) implements PublishedSubject {

        @Override
        public SubjectWord kind() {
            return SubjectWord.BEHAVIOR;
        }

        @Override
        public String identity() {
            return behavior;
        }
    }

    /**
     * One source, as the compilation identifies it.
     *
     * <p>The identity and not the name this document gives it. What a document calls a source is
     * recorded as the document writes it, so a name taken here would be taken while the entries
     * were still being arranged — and the table of sources would come out in the order they were
     * projected rather than the order they are written.
     */
    record OfASource(SourceId source) implements PublishedSubject {

        @Override
        public SubjectWord kind() {
            return SubjectWord.SOURCE;
        }

        @Override
        public String identity() {
            return source.value();
        }
    }

    /**
     * One row of one behavior.
     *
     * <p>{@code name} where the row was written with one and absent where it was not. A row without
     * a name is not addressable from outside this compiler — its own identity says so — so what is
     * written for it is which of its behavior's rows in that source it is, and a reader is not
     * handed a number as though they could look it up by it.
     */
    record OfARow(String behavior, String source, String name, Integer ordinal)
            implements PublishedSubject {

        public OfARow {
            if ((name == null) == (ordinal == null)) {
                throw new IllegalArgumentException("a row was written with a name or without one: "
                        + behavior + " in " + source);
            }
        }

        @Override
        public SubjectWord kind() {
            return SubjectWord.ROW;
        }

        @Override
        public String identity() {
            return behavior + "/" + source + "/" + (name == null ? "#" + ordinal : name);
        }
    }

    /**
     * A position inside a behavior's input.
     *
     * <p>Both spellings. {@code path} is what an author recognises and is what a person is shown;
     * {@code positionId} tells apart two positions that path spells alike, and is what this
     * document joins and orders on.
     */
    record AtAPosition(String behavior, String path, String positionId)
            implements PublishedSubject {

        @Override
        public SubjectWord kind() {
            return SubjectWord.READ_POSITION;
        }

        @Override
        public String identity() {
            return behavior + "/" + positionId;
        }
    }

    /** A position as a reason spells it, where that spelling is all the reason had. */
    record AtASpelledPosition(String behavior, String path) implements PublishedSubject {

        @Override
        public SubjectWord kind() {
            return SubjectWord.POSITION;
        }

        @Override
        public String identity() {
            return behavior + "/" + path;
        }
    }

    /** One of a behavior's declared inputs. */
    record AtAnInput(String behavior, int at) implements PublishedSubject {

        @Override
        public SubjectWord kind() {
            return SubjectWord.INPUT;
        }

        @Override
        public String identity() {
            return behavior + "/" + at;
        }
    }

    /**
     * One rule, at the position it was read at.
     *
     * <p>The place, what tells one rule from another, and what stopped the reading of it. All
     * three, because what a reader is told to do with such an entry is read the rule and what
     * stopped it — and an entry that named neither sent them to another array to find both, which
     * is the join this one was written to spare them.
     *
     * <p>The words a person is shown for the rule are not here. Those are what the rule handles
     * write wherever this document names a rule, through the one surface that renders a handle;
     * spelled again, a document would have two answers to what a rule is called.
     */
    record AtARule(String at, ObjectNode ruleId, List<String> stopped)
            implements PublishedSubject {

        public AtARule {
            stopped = List.copyOf(stopped);
        }

        @Override
        public SubjectWord kind() {
            return SubjectWord.RULE;
        }

        @Override
        public String identity() {
            return at + "/" + ruleId;
        }
    }

    /**
     * One line the rules drew.
     *
     * <p>{@code label} is what a person is shown and {@code line} is what tells two apart. One rule
     * draws more than one line — a clause of an invariant draws one at each end of what it admits —
     * so a border identified by the rule it came from would be two borders under one identity.
     */
    record AtABorder(String label, ObjectNode line) implements PublishedSubject {

        @Override
        public SubjectWord kind() {
            return SubjectWord.BORDER;
        }

        @Override
        public String identity() {
            return line.toString();
        }
    }

    /** One thing a line asks a row at, spelled as the obligations already are. */
    record AtAPoint(ObjectNode obligationId) implements PublishedSubject {

        @Override
        public SubjectWord kind() {
            return SubjectWord.POINT;
        }

        @Override
        public String identity() {
            return obligationId.toString();
        }
    }

    /** One decision of a body, spelled as what wrote it and where among that body's constructs. */
    record AtAFork(String module, ObjectNode writtenBy, int construct, int lowered, String said)
            implements PublishedSubject {

        @Override
        public SubjectWord kind() {
            return SubjectWord.FORK;
        }

        @Override
        public String identity() {
            return module + "/" + writtenBy + "/" + construct + "/" + lowered + "/" + said;
        }
    }

    /** One arm of a body, spelled as the branch account already spells one. */
    record AtAnArm(ObjectNode armId) implements PublishedSubject {

        @Override
        public SubjectWord kind() {
            return SubjectWord.ARM;
        }

        @Override
        public String identity() {
            return armId.toString();
        }
    }

    /** One of the measurements a behavior has exactly one of. */
    record OfAMeasure(String module, String behavior, MeasureWord measure)
            implements PublishedSubject {

        @Override
        public SubjectWord kind() {
            return SubjectWord.MEASURE;
        }

        @Override
        public String identity() {
            return module + "/" + behavior + "/" + measure;
        }
    }

    /** What the rows reach of one position, named by that position. */
    record OfAnAxisMeasure(String module, String behavior, String axis)
            implements PublishedSubject {

        @Override
        public SubjectWord kind() {
            return SubjectWord.AXIS_MEASURE;
        }

        @Override
        public String identity() {
            return module + "/" + behavior + "/" + axis;
        }
    }
}
