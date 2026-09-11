package souther.compiler.diag;

import souther.compiler.source.SourceId;

import java.util.Objects;

/**
 * Where a position is: which text it is in, and whose code it carries.
 *
 * <p>Two questions, and this is their product. They look like one question and are not, which is
 * what the first attempt at this got wrong. A text put back together out of what a module published
 * has real positions — its line 4 is where that module's author wrote that code — and no reader
 * holds a file those numbers are of. Being unable to show a text and carrying somebody else's code
 * are different facts, and a value answering both with one boolean has to pick, so a splice into a
 * text nobody named had nowhere to land and rewrote which text it was in.
 *
 * <blockquote>
 * <table>
 *   <caption>What each question answers</caption>
 *   <tr><td><b>which text</b></td>
 *       <td>a source this compilation holds | a text it cannot name | what a module published</td></tr>
 *   <tr><td><b>whose code</b></td>
 *       <td>this position's own | copied here from somewhere else</td></tr>
 * </table>
 * </blockquote>
 *
 * <p>Six states, all of them reached, each explained in a sentence:
 *
 * <ul>
 *   <li>a file this compile holds, code written at that position — every ordinary position.
 *   <li>a file this compile holds, code copied in from elsewhere — a spliced body, a report moved to
 *       an import line.
 *   <li>a text with no name here, code written at that position — a buffer an editor is holding, a
 *       snippet somebody parsed, and the position a pass mints to mean nowhere.
 *   <li>a text with no name here, code copied in — a body spliced into such a text: the caller can
 *       still point at the position and nobody else can.
 *   <li>a module's published text, code written at that position — where that module's author wrote
 *       it, in a text this compile has no file for.
 *   <li>a module's published text, code copied in from a third module — a body spliced in while the
 *       module was being read back.
 * </ul>
 *
 * <p>Held as a pair because every pair is legal. Which is the same rule that put the two halves of a
 * position together in the first place: hold the pair where the pair is what is legal, and split it
 * where it is not. The nine combinations of a nullable source and a boolean were not all legal;
 * these six are.
 *
 * <p>A splice keeps the text and replaces the code. That is the whole of what it does, and it is why
 * {@link #standingInFor} cannot change which text a position is in.
 *
 * <p>Where the code is written follows from both: code copied here is written where it was copied
 * from, and code of its own is written wherever this text is — which is a module, for a published
 * one, and is nothing to state for a text a reader has in front of them.
 *
 * <p>Two rules hold over this and over everything that carries one.
 *
 * <blockquote>
 * A source location has exactly one authority for its provenance.
 * <br>
 * The source in which coordinates are interpreted is never inferred from how the code came to
 * exist.
 * </blockquote>
 *
 * <p>The first is why a placement is minted where a text is turned into positions and nowhere else:
 * a pass that worked provenance out downstream would be a second authority, and the two disagree the
 * day one of them learns something the other does not.
 *
 * <p>The second is what the two questions are. A shortcut in either direction — a text with no name
 * taken to mean the code is elsewhere, a copied body taken to be in the file it came from — puts
 * them back under one answer.
 *
 * <h2>What this publishes</h2>
 *
 * <p>Outside this package: three ways to make one, a way to make a position out of one, and nothing
 * else. It answers no question. Every question a reader has about a place is answered by exactly one
 * projection, and each lives here — {@link Citation#of} for what a report may say,
 * {@link DiagnosticPlace#of} for where a reader may be sent, {@link SourcePos#quotedFrom()} for
 * which file this is read from, {@link SourcePos#wasCopiedHere()} for whether the position is this
 * node's own.
 *
 * <p>A pass reading the components would be a further answer to whichever of those it was really
 * asking, arrived at privately, and that is the defect this family is. So both questions are
 * package-private: something publishing "is this named" would be the nullable source identity again
 * under a name that reads like an improvement.
 *
 * <p>Value equality, because {@link SourcePos} is compared by every pass that rebuilds a node, by the
 * incremental engine deciding whether an answer changed, and by the store deciding whether two
 * diagnostics are one problem.
 *
 * <p>Provenance is in the equality and is not published. A caller may ask whether this is the
 * placement it already holds and may not ask what it is: reading the declaration off a position and
 * writing a place of one's own is how one position came to be presented as where code is written by
 * three surfaces and qualified by one.
 */
public final class Placement {

    /**
     * Which text a position is in.
     *
     * <p>What can be shown, and under what name. Not what is written in it — a text is the same text
     * whether the code at line 4 was written there or copied in.
     */
    sealed interface Text permits ASourceOfThisCompile, AnUnnamedText, WhatAModulePublished {

        /** What this compilation files the text under, or none where it names none. */
        SourceId sourceId();

        /** Where the code of this text is written, for a text that is not one a reader holds — or
         *  null where being in this text says nothing about where code came from. */
        SourceProvenance ofItsOwn();
    }

    /** A file this compile holds, under the identity it holds it by. */
    record ASourceOfThisCompile(SourceId source) implements Text {

        ASourceOfThisCompile {
            Objects.requireNonNull(source, "a file of this compile is named");
        }

        @Override
        public SourceId sourceId() {
            return source;
        }

        @Override
        public SourceProvenance ofItsOwn() {
            return null;   // the reader has the file; there is no elsewhere to name
        }

        @Override
        public String toString() {
            return String.valueOf(source);
        }
    }

    /**
     * A text this compile is reading and has no name for.
     *
     * <p>Two things live here today and nothing distinguishes them: a text somebody handed over
     * without a name — an editor's unsaved buffer, a snippet parsed to look at the tree — and a
     * position a pass minted to mean nowhere. Inside a compile it is only the second: the two
     * {@code NOWHERE} constants, whose whole use is that two of them are the same position. Telling
     * a text apart from a position nobody placed is the question about whether such a position is a
     * place at all, and is not answered here.
     *
     * <p>One instance for that reason. It holds nothing to tell two of them apart by, and what holds
     * it is compared by the incremental engine.
     */
    record AnUnnamedText() implements Text {

        static final AnUnnamedText IT = new AnUnnamedText();

        @Override
        public SourceId sourceId() {
            return null;
        }

        @Override
        public SourceProvenance ofItsOwn() {
            return null;   // a text with no name says nothing about where its code came from
        }

        @Override
        public String toString() {
            return "an unnamed text";
        }
    }

    /**
     * A text put back together out of what {@code module} published, which this compile has no file
     * for.
     *
     * <p>Its positions are real positions in that text and are not where a reader can be sent. Code
     * of its own is code that module's author wrote, which is what {@link #ofItsOwn()} says.
     *
     * <p>Identified by the module and not by how anybody reached it. The name a reader here writes
     * for the code belongs to the reading, and lives on what a position carries rather than on which
     * text it is in.
     */
    record WhatAModulePublished(SourceProvenance module) implements Text {

        WhatAModulePublished {
            Objects.requireNonNull(module, "a published text is a module's");
            // As the declaration knows it. What a reading reached the code by is a fact about that
            // reading — two readings of one module reach it by two names as easily as one — so a
            // text carrying it would be two texts, and the same published module would stop being
            // the same text depending on which import found it.
            module = module.asDeclared();
        }

        @Override
        public SourceId sourceId() {
            return null;
        }

        @Override
        public SourceProvenance ofItsOwn() {
            return module;
        }

        @Override
        public String toString() {
            return "what " + module + " published";
        }
    }

    /** Whose code a position carries: the code written at it, or code copied here from elsewhere. */
    sealed interface CodeOrigin permits Native, CopiedFrom {
    }

    /** The position is this node's own — nothing moved it here. */
    record Native() implements CodeOrigin {

        static final Native IT = new Native();

        @Override
        public String toString() {
            return "native";
        }
    }

    /**
     * The position was borrowed from the site this code was copied into, and the code itself is
     * written in {@code from}.
     *
     * <p>What a splice writes and what moving a report's caret writes. Replaced rather than composed
     * when it happens twice: a position already carrying copied code is carrying the copy this one is
     * nested inside, and the body a position belongs to is the innermost one it came out of.
     */
    record CopiedFrom(SourceProvenance from) implements CodeOrigin {

        CopiedFrom {
            Objects.requireNonNull(from, "code copied here was written somewhere");
        }

        @Override
        public String toString() {
            return "copied from " + from;
        }
    }

    private final Text text;
    private final CodeOrigin code;

    private Placement(Text text, CodeOrigin code) {
        this.text = text;
        this.code = code;
    }

    /** Which text this is in. */
    Text text() {
        return text;
    }

    /** Whose code this position carries. */
    CodeOrigin code() {
        return code;
    }

    /**
     * Where the code this position names is written, or null where it is written at the position in
     * a text a reader has in front of them.
     *
     * <p>Read off both. Code copied here is written where it came from; code of its own is written
     * wherever this text is, which is a module for a published text and is nothing to state for one
     * a reader holds.
     */
    SourceProvenance codeIsWrittenIn() {
        return code instanceof CopiedFrom(SourceProvenance from) ? from : text.ofItsOwn();
    }

    /** Which source of this compilation this text is, as a finished answer. Off the text alone. */
    QuotedFrom quotedFrom() {
        return switch (text) {
            case ASourceOfThisCompile(SourceId source) ->
                    new QuotedFrom.ASourceThisCompileHolds(source);
            case AnUnnamedText _ -> new QuotedFrom.TextItCannotName();
            case WhatAModulePublished(SourceProvenance module) ->
                    new QuotedFrom.TextItCannotShow(module);
        };
    }

    /** Whether this and {@code other} are in the same text, whatever is written in either. */
    boolean isTheSameTextAs(Placement other) {
        return text.equals(other.text);
    }

    /** This placement as something a report may say about {@code at}. The one way what a position
     *  carries reaches a reader, and package-private so that it stays the one way. */
    Citation cite(SourcePos at) {
        SourceProvenance written = codeIsWrittenIn();
        return switch (text) {
            case ASourceOfThisCompile _ -> written == null
                    ? new WrittenCitation(at) : new ReachedCitation(written, at);
            case AnUnnamedText _ -> written == null
                    ? new UnplacedCitation(at) : new UnplacedElsewhereCitation(written, at);
            // A text nobody holds always says where its code came from, so there is no arm here for
            // a citation with nowhere to point and nothing to say instead.
            case WhatAModulePublished _ -> new OutOfSightCitation(written);
        };
    }

    /**
     * This text, carrying code copied here from where {@code declaring} says.
     *
     * <p>What an expansion gives a copy it cannot give its own positions, and what moving a report's
     * caret gives the place it moved to. Total over every state, because a body is spliced into
     * whatever calls it and a call is in whatever text its caller is in — including one this compile
     * cannot name, and including a body already copied out of somewhere else.
     *
     * <p>The text is kept. A splice moves code, not files: a copy read against a caller's file is in
     * that file, and one read against a text nobody named is in that text. Rewriting the text here is
     * what the first version of this did, and it turned a snippet into a module's published text.
     */
    Placement standingInFor(DeclaringCode declaring) {
        return new Placement(text, new CopiedFrom(declaring.provenance()));
    }

    /**
     * A place in this text, in the first of its top-level constructs.
     *
     * <p>For a caller spelling one out rather than reading it off a text — a test writing a place
     * to compare, a pass minting one to mean nowhere. Where a text is what a place is being made
     * from, {@code SourceLayout} is what makes it, and this is not the way in.
     */
    public SourcePos at(int token, int within) {
        return at(0, token, within);
    }

    /**
     * The same, in the {@code construct}-th of them.
     *
     * <p>The way a laid-out text makes its places, and the only way in from outside this package
     * that says which construct. What counts the constructs of a text is what parsed it, so a
     * caller reaching this without one has counted them a second time.
     */
    public SourcePos at(int construct, int token, int within) {
        return new SourcePos(construct, token, within, this);
    }

    /** A file this compile holds, under the identity it holds it by. Its positions are where the
     *  code is, and they say which file they are in. */
    public static Placement aFileOfThisCompile(SourceId sourceId) {
        return new Placement(new ASourceOfThisCompile(sourceId), Native.IT);
    }

    /**
     * A text nobody has named — a buffer an editor is holding and has not saved, a snippet a caller
     * parsed to look at the tree.
     *
     * <p>Its positions are where the code is: what was read is what somebody wrote. What they do not
     * say is which file, so nothing built from them reaches a reader on its own, and a report made
     * against one is a report the caller has to place.
     */
    public static Placement aTextWithNoIdentity() {
        return new Placement(AnUnnamedText.IT, Native.IT);
    }

    /**
     * A text put back together out of what a module published, which this compile has no file for.
     *
     * <p>The positions are real positions in that text and are not where a reader can be sent, so
     * they say so from the moment they are made. What they carry is that module's own code, which is
     * where a report about them points a reader instead.
     *
     * <p>There is deliberately no way to say "reassembled, and here is its source id": a text put
     * back together out of what a module published is in no file, and a caller with a file for it is
     * reading a file.
     */
    public static Placement whatAModulePublished(SourceProvenance provenance) {
        return new Placement(new WhatAModulePublished(provenance), Native.IT);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Placement that && text.equals(that.text) && code.equals(that.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, code);
    }

    @Override
    public String toString() {
        return "In[" + text + ", " + code + "]";
    }
}
