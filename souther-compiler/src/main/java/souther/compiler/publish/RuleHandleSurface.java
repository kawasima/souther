package souther.compiler.publish;

import souther.compiler.diag.SourceNameResolver;
import souther.compiler.source.SourceId;

import tools.jackson.databind.node.ObjectNode;

/**
 * Where a rule handle reaches a reader.
 *
 * <p>Every sentence about a rule is written through one of these, which is what makes the places a
 * document publishes one a set rather than a habit. A check can then ask the schema which fields
 * carry a handle and ask this which fields the compiler writes one into, and compare the two —
 * where a check that went looking for the calls would be reading a key out of a string literal and
 * calling two fields of one name one field.
 *
 * <p>Held as an API and not as a convention. {@link RuleHandleSentence} is not public, so a handle
 * cannot reach a reader without a surface: adding a field that carries one means adding a constant
 * here, and a constant here with nothing to match it in the schema is a document field nobody
 * declared.
 */
public sealed interface RuleHandleSurface {

    /** {@code handle} as this surface writes it. */
    default String render(PublishedRuleHandle handle, SourceNameResolver names,
                          SourceId sectionSource) {
        return RuleHandleSentence.said(handle, names, sectionSource);
    }

    /**
     * A field of the adequacy document.
     *
     * <p>An enum, so the population is what the compiler has rather than a list somebody keeps.
     * Each names the field by where the schema declares it, which is what tells two fields of one
     * name apart: {@code rule} is written under three parents, and a set of keys would have said
     * there were fewer surfaces than there are and gone on saying it as more were added.
     */
    enum InADocument implements RuleHandleSurface {

        /** The rule a standing question is about. */
        UNANSWERED_RULE("/$defs/partition/properties/unanswered/items/properties/rule",
                "rule", Carries.THE_HANDLE_ALONE),

        /** The rule a reading could not turn into a line. */
        NOT_READ_RULE("/$defs/partition/properties/notRead/items/properties/rule",
                "rule", Carries.THE_HANDLE_ALONE),

        /** The rule that drew the line a point is owed for. */
        OBLIGATION_RULE("/$defs/obligations/items/properties/rule",
                "rule", Carries.THE_HANDLE_ALONE),

        /** The rule a boundary came from, together with whatever narrowed it. */
        BOUNDARY_ORIGIN("/$defs/partition/properties/boundaries/items/properties/origin",
                "origin", Carries.A_SENTENCE_AROUND_IT),

        /** What a finding is about, which names the rule among other things. */
        FINDING_SUBJECT("/$defs/findings/items/properties/subject",
                "subject", Carries.A_SENTENCE_AROUND_IT);

        private final String schemaPath;
        private final String key;
        private final Carries carries;

        InADocument(String schemaPath, String key, Carries carries) {
            this.schemaPath = schemaPath;
            this.key = key;
            this.carries = carries;
        }

        /** Where the schema declares this field, as a pointer into the schema this compiler ships. */
        public String schemaPath() {
            return schemaPath;
        }

        /** What the field is called, taken from here so that no writer spells it again. */
        public String key() {
            return key;
        }

        /** Whether the field is the handle or a sentence with one in it. */
        public Carries carries() {
            return carries;
        }

        /**
         * Write {@code handle} into {@code into} under this field.
         *
         * <p>Only where the field is the handle. A field that writes a sentence around one has words
         * of its own to put together, and they are the caller's — so it says what it wrote rather
         * than being handed a handle and left to add to it.
         */
        public void put(ObjectNode into, PublishedRuleHandle handle, SourceNameResolver names,
                        SourceId sectionSource) {
            if (carries != Carries.THE_HANDLE_ALONE) {
                throw new IllegalStateException(
                        "this field writes a sentence with a handle in it: " + this);
            }
            into.put(key, render(handle, names, sectionSource));
        }

        /**
         * Write {@code sentence} into {@code into} under this field.
         *
         * <p>For the fields that put words around a handle. The key still comes from here, so that
         * a field carrying a handle is one this names however it is written.
         */
        public void putSentence(ObjectNode into, String sentence) {
            if (carries != Carries.A_SENTENCE_AROUND_IT) {
                throw new IllegalStateException("this field is the handle and nothing else: " + this);
            }
            into.put(key, sentence);
        }
    }

    /**
     * The report a person reads.
     *
     * <p>Beside the document's fields rather than among them, because it is held to nothing in the
     * schema: what a person is shown is not a contract a consumer builds against. It is a surface
     * all the same, so that the sentence has one spelling wherever it is read.
     */
    record InTheReportAPersonReads() implements RuleHandleSurface {}

    /** The report a person reads, which there is one of. */
    RuleHandleSurface PROSE = new InTheReportAPersonReads();

    /** How much of the field a handle is. */
    enum Carries {
        /** The field is the handle. */
        THE_HANDLE_ALONE,
        /** The field is a sentence with a handle in it. */
        A_SENTENCE_AROUND_IT
    }
}
