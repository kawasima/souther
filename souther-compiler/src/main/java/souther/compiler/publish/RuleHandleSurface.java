package souther.compiler.publish;

import souther.compiler.diag.SourceNameResolver;
import souther.compiler.source.SourceId;

import tools.jackson.databind.node.ObjectNode;

/**
 * A field of the adequacy document that carries a rule handle.
 *
 * <p>An enum, so the fields a document publishes a handle into are what the compiler has rather than
 * a list somebody keeps. Each names the field by where the schema declares it, which is what tells
 * two fields of one name apart: {@code rule} is written under three parents, and a set of keys would
 * have said there were fewer fields than there are and gone on saying it as more were added.
 *
 * <p><b>Writing is the whole of what this offers.</b> There is no way to ask one of these what a
 * handle reads as, because a renderer that handed a string back is a renderer whose answer can be
 * put under a field nobody declared — and the count of fields would then be the count of the ones
 * somebody remembered to register. What a person's report shows is
 * {@link RuleHandleProse}, and which classes may name that is written down
 * ({@code WhoMaySayWhatARuleHandleReadsAs}).
 *
 * <p>A field whose sentence has words of its own takes {@link PublishedSentence} rather than the
 * finished words, so that the handle inside it is one this put together.
 */
public enum RuleHandleSurface {

    /** The rule a standing question is about. */
    UNANSWERED_RULE("/$defs/partition/properties/unanswered/items/properties/rule",
            "rule", Carries.THE_HANDLE_ALONE),

    /**
     * The rule an entry holding a verdict open is about.
     *
     * <p>The same handle the questions themselves publish, written where the entry is. What such an
     * entry tells a reader to do is read the rule and what stopped it, and one that named neither
     * sent them to the questions to find both — which is the join this array was written to spare
     * them.
     */
    OPENING_RULE("/$defs/subject/oneOf/8/properties/rule", "rule", Carries.THE_HANDLE_ALONE),

    /** The rule a reading could not turn into a line. */
    NOT_READ_RULE("/$defs/partition/properties/notRead/items/properties/rule",
            "rule", Carries.THE_HANDLE_ALONE),

    /** The rule that drew the line a point is owed for. */
    OBLIGATION_RULE("/$defs/obligations/items/properties/rule",
            "rule", Carries.THE_HANDLE_ALONE),

    /** The rule a boundary came from, together with whatever narrowed the line. */
    BOUNDARY_ORIGIN("/$defs/partition/properties/boundaries/items/properties/origin",
            "origin", Carries.A_SENTENCE_AROUND_IT),

    /** What a finding is about, which names the rule among other things. */
    FINDING_SUBJECT("/$defs/findings/items/properties/subject",
            "subject", Carries.A_SENTENCE_AROUND_IT);

    private final String schemaPath;
    private final String key;
    private final Carries carries;

    RuleHandleSurface(String schemaPath, String key, Carries carries) {
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
     * <p>Only where the field is the handle. A field with words of its own has a sentence to put
     * together, and handed a bare handle it would carry the shortest true answer and lose the rest.
     */
    public void put(DocumentItem into, PublishedRuleHandle handle, SourceNameResolver names,
                    SourceId sectionSource) {
        if (carries != Carries.THE_HANDLE_ALONE) {
            throw new IllegalStateException(
                    "this field writes a sentence with a handle in it: " + this);
        }
        into.node().put(here(into), RuleHandleSentence.said(handle, names, sectionSource));
    }

    /**
     * The same, written straight into an object this surface is the field of.
     *
     * <p>For a field of an object the document does not reach through a part: what says where it is
     * written is this declaration, which is the same thing the check below compares against, so
     * there is nothing here for a caller to get wrong and nothing for it to state twice.
     */
    public void put(ObjectNode into, PublishedRuleHandle handle, SourceNameResolver names,
                    SourceId sectionSource) {
        // The object this field is declared on, which is what the field's own path names less the
        // two steps that name the field: an item says where it is and adds `/properties/<key>`.
        String object = schemaPath.substring(0, schemaPath.length() - ("/properties/" + key).length());
        put(DocumentItem.at(into, object), handle, names, sectionSource);
    }

    /**
     * Write {@code sentence} into {@code into} under this field.
     *
     * <p>Only where the field has words of its own. A field that is the handle would carry words
     * beside it that a consumer reading it as a handle cannot take apart.
     */
    public void put(DocumentItem into, PublishedSentence sentence, SourceNameResolver names,
                    SourceId sectionSource) {
        if (carries != Carries.A_SENTENCE_AROUND_IT) {
            throw new IllegalStateException("this field is the handle and nothing else: " + this);
        }
        into.node().put(here(into), RuleHandleSentence.of(sentence, names, sectionSource));
    }

    /**
     * The key, once the object being written is the one the schema declares this field on.
     *
     * <p>Where a field is written is half of what it is, and it was the half nothing compared. The
     * place this names and the place the writer reached are two answers arrived at apart — the
     * contract says where the field lives, the writer says which part of the document it is
     * building — so a handle put into some other object is a field the schema declares nothing
     * about, whatever the check that counts these constants had to say.
     */
    private String here(DocumentItem into) {
        if (!schemaPath.equals(into.fieldPath(key))) {
            throw new IllegalStateException("this field is declared at " + schemaPath
                    + " and was written into " + into.fieldPath(key));
        }
        return key;
    }

    /** How much of the field a handle is. */
    public enum Carries {
        /** The field is the handle. */
        THE_HANDLE_ALONE,
        /** The field is a sentence that may have a handle in it. */
        A_SENTENCE_AROUND_IT
    }
}
