package souther.compiler.fmt;

import souther.compiler.cst.CstParser;
import souther.compiler.cst.SyntaxElement;
import souther.compiler.cst.SyntaxKind;
import souther.compiler.cst.SyntaxNode;
import souther.compiler.cst.SyntaxToken;
import souther.compiler.diag.CompileException;
import souther.compiler.diag.msg.DeclarationMessage;
import souther.compiler.diag.Diagnostic;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static souther.compiler.fmt.TokenDoc.concat;
import static souther.compiler.fmt.TokenDoc.group;
import static souther.compiler.fmt.TokenDoc.nest;

/**
 * A single-canonical-form (gofmt-style) formatter over the concrete syntax tree. It re-derives the
 * layout from the tree's structure and a fixed style — a product type as a leading-comma block, a
 * record literal as a trailing-comma {@code Type { ... }}, a {@code |>} pipeline one stage per line —
 * choosing inline or broken by whether the construct fits {@link #WIDTH} columns. Full-line comments
 * are kept above the construct they precede; blank lines between top-level items collapse to one.
 */
public final class Formatter {

    private static final int INDENT = 4;

    private static final TokenDoc LPAREN = TokenDoc.token(SyntaxKind.LPAREN, "(");
    private static final TokenDoc RPAREN = TokenDoc.token(SyntaxKind.RPAREN, ")");
    private static final TokenDoc LBRACE = TokenDoc.token(SyntaxKind.LBRACE, "{");
    private static final TokenDoc RBRACE = TokenDoc.token(SyntaxKind.RBRACE, "}");
    private static final TokenDoc LBRACKET = TokenDoc.token(SyntaxKind.LBRACKET, "[");
    private static final TokenDoc RBRACKET = TokenDoc.token(SyntaxKind.RBRACKET, "]");
    private static final TokenDoc LT = TokenDoc.token(SyntaxKind.LT, "<");
    private static final TokenDoc GT = TokenDoc.token(SyntaxKind.GT, ">");
    private static final TokenDoc COMMA = TokenDoc.token(SyntaxKind.COMMA, ",");
    private static final TokenDoc DOT = TokenDoc.token(SyntaxKind.DOT, ".");
    private static final TokenDoc QUESTION = TokenDoc.token(SyntaxKind.QUESTION, "?");
    private static final TokenDoc UNANSWERED = TokenDoc.token(SyntaxKind.UNANSWERED, "<?>");
    private static final TokenDoc ASSIGN = TokenDoc.token(SyntaxKind.ASSIGN, "=");
    private static final TokenDoc COLON = TokenDoc.token(SyntaxKind.COLON, ":");
    private static final TokenDoc ARROW = TokenDoc.token(SyntaxKind.ARROW, "->");
    private static final TokenDoc PIPE = TokenDoc.token(SyntaxKind.PIPE, "|");
    private static final TokenDoc GAP = TokenDoc.GAP;
    private static final TokenDoc SOFT_GAP = TokenDoc.SOFT_GAP;

    /** The canonical width. It applies to every breakable construct, a declaration's as much as an
     * expression's: a module header that lists more names than fit breaks the same way a call with
     * more arguments than fit does. A line wider than this is one whose content has no separator to
     * break at — a long pattern, a single long token, or a nesting deep enough that the indent alone
     * takes the width.
     *
     * <p>Columns on the screen, under {@code DisplayColumns}: a full-width name takes two of them
     * per character, which is what makes a line of a model written in Japanese as long as it looks.
     * Counting the characters instead would let such a line past the width by however many
     * full-width characters it holds. */
    private static final int WIDTH = 100;

    /** The comments taken by some construct, by where they are in the source. A comment is reachable
     * two ways once members nest — from the parent's child list and from the front of the member's
     * own subtree — and it must be written once. The offset identifies a comment without depending on
     * how the tree hands nodes back. It records what was consumed rather than what reached the
     * output, which is what makes it comparable against the comments the tree holds. One instance
     * formats one file, so this lives as long as that. */
    private final java.util.Set<Integer> consumedComments = new java.util.HashSet<>();

    /** Where each of the file's comments goes, decided once before any of it is written. One
     * instance formats one file, so this lives exactly as long as the tree it describes. */
    private Attachments comments = Attachments.empty();

    /** The places this formatter writes, and what the source had at each. Built as the document is
     * and read once it is whole: what a place can carry is decided over the places, not while one
     * of them is being written. */
    private final Correspondence places = new Correspondence();

    private Formatter() {
    }

    /** Formats source text into its canonical form. Assumes the source parses without syntax errors;
     * a caller that cannot assume that should check {@link CstParser#parse} first. */
    public static String format(String source) {
        try {
            return format(CstParser.parse(source).root());
        } catch (StackOverflowError _) {
            throw tooDeep();   // the descent that found the end of the stack was the parse's own
        }
    }

    /**
     * The comments {@code file} holds that {@code consumed} does not — the ones the formatter found
     * and did not write. {@link SyntaxKind#LINE_COMMENT} is the only kind of comment the grammar
     * has, so this is all of them.
     */
    static List<SyntaxToken> unconsumed(SyntaxNode file, java.util.Set<Integer> consumed) {
        List<SyntaxToken> out = new ArrayList<>();
        for (SyntaxToken t : tokens(file)) {
            if (t.kind() == SyntaxKind.LINE_COMMENT && !consumed.contains(t.start())) {
                out.add(t);
            }
        }
        return out;
    }

    /** Formats an already-parsed file into its canonical form — for a caller that has parsed the
     * source (e.g. to check for syntax errors) and need not parse it again. Assumes {@code file}
     * came from a clean parse. */
    public static String format(SyntaxNode file) {
        // The construction, then the carriers, then the boundaries, then the layout. Each stage is
        // whole before the next begins: a comment goes to a place of the canonical form and there
        // are no places until the construction has finished making them.
        return canonicalize(file).text();
    }

    /**
     * What one canonicalization of {@code file} came to: everything it made, and the text it came
     * to, from the one run that made them.
     *
     * <p>The identities are why this is one object. A {@link Place}, a {@link Doc.GroupRef} and the
     * rest are made by a run and mean something only to it — {@link Layout} keys its spans and
     * decisions on them and {@link Correspondence} keys its relation on the same places. Asked for
     * separately they come from separate runs, and a place of one has no span in the other's layout.
     * Nothing refuses the mixture: a lookup simply misses, so two collections of the same size can
     * share no member and a count of them holds.
     */
    record CanonicalForm(Construction construction, Layout layout) {

        String text() {
            return layout.text();
        }

        /** The places this run made, in the order it made them. */
        List<Place> places() {
            return construction.places().made();
        }
    }

    /** Canonicalizes {@code file}: one construction, laid out, and everything either of them made. */
    static CanonicalForm canonicalize(SyntaxNode file) {
        try {
            Construction construction = build(file);
            List<SyntaxToken> missing = unconsumed(file, construction.consumed());
            if (!missing.isEmpty()) {
                throw new IllegalStateException(
                        missing.size() + " comment(s) in this source reached no construct and would"
                                + " have been dropped; the first is at offset "
                                + missing.get(0).start() + ": "
                                + missing.get(0).text().stripTrailing());
            }
            Layout laid = construction.doc().resolve().layout(WIDTH);
            // The file's place is the output, which is the run's answer and not one a construct
            // emits: nothing writes the file, and every place it made is somewhere inside it.
            return new CanonicalForm(construction, laid.and(construction.places().file(),
                    new Extent(0, laid.text().length())));
        } catch (StackOverflowError _) {
            throw tooDeep();
        }
    }

    /**
     * What the formatter builds for a file: the document, the places it was written at, and which
     * place was handed each comment. All three come out of one construction because they are made
     * by it — the second reconstructed from the first is the reconstruction this is here to stop.
     *
     * <p>The carriers are here because two of them can print the same. A comment above a
     * definition's body and one above the definition itself both come back on the line before the
     * body, so text is not enough to say which place owns one.
     */
    record Construction(TokenDoc doc, Correspondence places,
            Map<Place, Map<Carrier, List<SyntaxToken>>> carriers, Map<Place, Integer> order,
            java.util.Set<Integer> consumed) {}

    private static Construction build(SyntaxNode file) {
        Formatter formatter = new Formatter();
        TokenDoc doc = formatter.file(file);
        Map<Place, Integer> order = Place.orderedIn(doc);
        TokenDoc resolved = formatter.resolved(doc);
        return new Construction(resolved, formatter.places, formatter.assigned, order,
                formatter.consumedComments);
    }

    /** {@code doc} with the comments in it. The one place a carrier is answered. */
    private TokenDoc resolved(TokenDoc doc) {
        assigned = assign(doc);
        return Carriers.resolve(doc, this::heldAt);
    }

    /** Which comments each place carries, decided once the whole document exists. */
    private Map<Place, Map<Carrier, List<SyntaxToken>>> assigned = new IdentityHashMap<>();

    /**
     * Which place carries each comment.
     *
     * <p>What a comment was written about is already known and is a fact about the source. This is
     * the other question: of the places the canonical form has, which one can be handed it. The
     * answer is found by walking up the places, and the places are the canonical form's — where it
     * writes something the source has nowhere for, or writes a source construct's child somewhere
     * the source does not have it, walking the source instead answers about a construct that is not
     * written here.
     *
     * <p>A comment whose source element stands behind several places is carried by whichever place
     * they converge on. Two places that do not converge are a correspondence that cannot be read,
     * so it is refused rather than settled by picking one.
     */
    private Map<Place, Map<Carrier, List<SyntaxToken>>> assign(TokenDoc doc) {
        Map<Place, java.util.EnumSet<Carrier>> declared = new IdentityHashMap<>();
        Carriers.slots(doc, (place, which) ->
                declared.computeIfAbsent(place, _ -> java.util.EnumSet.noneOf(Carrier.class))
                        .add(which));

        Map<Place, Map<Carrier, List<SyntaxToken>>> out = new IdentityHashMap<>();
        // The two ends of a name are tried before the constructs, so a comment written at the end of
        // a line the canonical form opens a construct with goes to that line and not to wherever the
        // construct ends.
        for (var e : comments.aboveToken().entrySet()) {
            file(out, declared,
                    namePlaces(e.getKey(), e.getValue(), Carrier.ABOVE, declared),
                    Carrier.ABOVE, e.getValue().comments());
        }
        for (var e : comments.afterToken().entrySet()) {
            file(out, declared,
                    namePlaces(e.getKey(), e.getValue(), Carrier.TRAILING, declared),
                    Carrier.TRAILING, e.getValue().comments());
        }
        for (var e : comments.above().entrySet()) {
            file(out, declared, entryPlaces(e.getKey(), Carrier.ABOVE), Carrier.ABOVE, e.getValue());
        }
        for (var e : comments.after().entrySet()) {
            file(out, declared, endsTheLine(e.getKey(), e.getKey().end(), declared),
                    Carrier.TRAILING, e.getValue());
        }
        for (var e : comments.below().entrySet()) {
            file(out, declared, entryPlaces(e.getKey(), Carrier.BELOW), Carrier.BELOW, e.getValue());
        }
        for (var e : comments.atEnd().entrySet()) {
            file(out, declared, entryPlaces(e.getKey(), Carrier.AT_END), Carrier.AT_END, e.getValue());
        }
        for (Map<Carrier, List<SyntaxToken>> byCarrier : out.values()) {
            for (List<SyntaxToken> run : byCarrier.values()) {
                run.sort(java.util.Comparator.comparingInt(SyntaxToken::start));
            }
        }
        return out;
    }

    /**
     * The places a comment held against a name reaches.
     *
     * <p>A name is a place of the canonical form wherever the canonical form writes it as one: the
     * name a sum's case is written from, the {@code =} a declaration's first line ends with. Where
     * it writes no place for it, the name is in the middle of a construct — a {@code module}, an
     * {@code exposing}, a bracket — and the construction said which construct that is, so this asks
     * that construct and not the source tree.
     */
    private List<Place> namePlaces(Written name, Attachments.OnAName held, Carrier which,
            Map<Place, java.util.EnumSet<Carrier>> declared) {
        List<Place> written = places.placesOf(name);
        if (!written.isEmpty()) {
            return written;
        }
        return which == Carrier.ABOVE
                ? entryPlaces(held.inside(), Carrier.ABOVE)
                : endsTheLine(held.inside(), name.end(), declared);
    }

    /**
     * The places a source construct was written at.
     *
     * <p>Every construct has an answer and the construction gave it. One the canonical form writes
     * a place for is written at that place; one it writes inside another construct's line is
     * written at that construct's place; and a run the canonical form flattens — {@code 1 |> b} of
     * {@code 1 |> b |> c} — opens at the head place and ends at the stage place its own right
     * operand is written at, which is what a comment written after it is at the end of the line of.
     *
     * <p>Nothing here walks the source tree. Which canonical place a construct the canonical form
     * has no construct for contributed to is a fact the construction knew and recorded; worked out
     * afterwards from the shape of the source it is a guess, and it was one — the first or last
     * child of whatever held it, which is neither of the two ends of a flattened run.
     */
    private List<Place> entryPlaces(SyntaxNode node, Carrier which) {
        List<Place> written = places.placesOf(new Written.Construct(node));
        if (!written.isEmpty()) {
            return written;
        }
        Correspondence.Span span = places.spanOf(node);
        if (span == null) {
            throw new IllegalStateException("the construction recorded no place for " + node.kind()
                    + " at " + node.start() + ".." + node.end()
                    + "; every construct it writes is written somewhere");
        }
        return List.of(which == Carrier.ABOVE ? span.from() : span.to());
    }

    /**
     * The places a comment written at {@code wrote}, inside {@code anchor}, is at the end of the
     * line of.
     *
     * <p>Where the place that would take it goes on past {@code wrote}, the comment was written in
     * the middle of that place and not at the end of its line: what it ends is the line that place
     * is on, so the question is asked again of the place that one is written under. A comment after
     * the condition of an {@code if} is written at the end of the declaration that holds the
     * {@code if}, and this is why.
     *
     * <p>Where the comment was written is carried separately from the construct it was written in,
     * because for a comment in the middle of one the two are not the same offset: an anchor that
     * ends past the comment would say the first place asked already ends the line.
     *
     * <p>The widening is over the places. It used to read the place's source elements, find what
     * the source has ending there and ask the source tree again, which is the round trip between
     * the two structures that having places is meant to end.
     */
    private List<Place> endsTheLine(SyntaxNode anchor, int wrote,
            Map<Place, java.util.EnumSet<Carrier>> declared) {
        List<Place> entries = entryPlaces(anchor, Carrier.TRAILING);
        Place carrier = null;
        for (Place entry : entries) {
            Place found = nearestCarrier(entry, Carrier.TRAILING, declared);
            if (found != null && (carrier == null || isAncestorOf(carrier, found))) {
                carrier = found;
            }
        }
        int pos = wrote;
        while (carrier != null) {
            int ends = endsAt(carrier);
            if (ends <= pos) {
                break;                        // its line ends where the comment was written
            }
            // The comment is inside this place, so what it ends is the line this place is on, and
            // that line runs to the end of the outermost place ending where this one does.
            Place outer = carrier;
            for (Place p = carrier.parent(); p != null && endsAt(p) == ends; p = p.parent()) {
                Place found = nearestCarrier(p, Carrier.TRAILING, declared);
                if (found != null && endsAt(found) == ends) {
                    outer = found;
                }
            }
            pos = ends;
            if (outer == carrier) {
                break;                        // nothing wider ends here to hand it to
            }
            carrier = outer;
        }
        return carrier == null ? entries : List.of(carrier);
    }

    /** Where what {@code place} is written from ends, or the start of the file where the canonical
     *  form writes it from nothing the source has. */
    private int endsAt(Place place) {
        int end = 0;
        for (Written w : places.sourcesOf(place)) {
            end = Math.max(end, w.end());
        }
        return end;
    }

    private void file(Map<Place, Map<Carrier, List<SyntaxToken>>> out,
            Map<Place, java.util.EnumSet<Carrier>> declared, List<Place> entries, Carrier which,
            List<SyntaxToken> run) {
        java.util.Set<Place> carriers = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (Place entry : entries) {
            Place carrier = nearestCarrier(entry, which, declared);
            if (carrier != null) {
                carriers.add(carrier);
            }
        }
        for (Place carrier : agreed(carriers, which)) {
            List<SyntaxToken> held = out
                    .computeIfAbsent(carrier, _ -> new java.util.EnumMap<>(Carrier.class))
                    .computeIfAbsent(which, _ -> new ArrayList<>());
            for (SyntaxToken t : run) {
                if (!held.contains(t)) {
                    held.add(t);
                }
            }
        }
    }

    /**
     * The one place the carriers found agree on, or none where there were none.
     *
     * <p>Several source elements may stand behind one comment's answer, and they agree when the
     * places they reach lie on one line of descent: an element written under another reaches a
     * carrier at or below the one its holder reaches, and the deeper of the two is the one that was
     * asked about. Places on different branches are a correspondence that cannot be read, so it is
     * refused rather than settled by picking one.
     */
    private static List<Place> agreed(java.util.Set<Place> carriers, Carrier which) {
        Place deepest = null;
        for (Place c : carriers) {
            if (deepest == null || isAncestorOf(deepest, c)) {
                deepest = c;
            }
        }
        for (Place c : carriers) {
            if (!isAncestorOf(c, deepest)) {
                throw new IllegalStateException(
                        "a comment stands behind places that do not agree on which of them carries"
                                + " it (" + which + "): " + carriers);
            }
        }
        return deepest == null ? List.of() : List.of(deepest);
    }

    private static boolean isAncestorOf(Place maybe, Place of) {
        for (Place p = of; p != null; p = p.parent()) {
            if (p == maybe) {
                return true;
            }
        }
        return false;
    }

    /**
     * The nearest place at or above {@code from} that can be handed a comment in that direction.
     *
     * <p>A place with no line of its own has nowhere to put one written above it: the rest of that
     * line would be written inside the comment. Whether it has one is read from the same value the
     * layout reads to write the boundary in front of it, so the two cannot come apart.
     */
    private static Place nearestCarrier(Place from, Carrier which,
            Map<Place, java.util.EnumSet<Carrier>> declared) {
        for (Place p = from; p != null; p = p.parent()) {
            boolean carries = which == Carrier.ABOVE
                    ? p.opening().opensALine()
                    : declared.getOrDefault(p, java.util.EnumSet.noneOf(Carrier.class))
                            .contains(which);
            if (carries) {
                return p;
            }
        }
        return null;
    }

    /**
     * A tree that nests deeper than this walk can descend. {@link CstParser} bounds what it builds,
     * so a tree from a parse this compiler ran does not reach here; what does is a thread with less
     * stack than that bound was set for, and a {@code StackOverflowError} is not a
     * {@link CompileException}, so left alone it passes through the recovery boundary and reaches
     * the author as a stack trace.
     *
     * <p>No position is claimed: unlike the parser's limit, this one was not reached at a token —
     * it was reached wherever the stack happened to end, which is not a fact about the source.
     */
    private static CompileException tooDeep() {
        return CompileException.of(Diagnostic.say(new DeclarationMessage.TheCompilerRanOutOfRoom()).nowhere().build());
    }

    // --- layout ---
    //
    // Every repeated or joined construct is written through these, so the separator of a construct
    // is a place the layout may break rather than a literal the construct spelled itself. A
    // construct that spells its own separators is one the width cannot reach: the break has to exist
    // in the document before the renderer can choose it, and the renderer breaks the outermost group
    // that does not fit, so a member is split only when the structure around it had nothing to give.

    /**
     * A member and the comment written at the end of the line it takes. The comment goes after
     * whatever the enclosing construct writes between this member and the next, because that
     * punctuation is on this line too — a comma written after the comment would be inside it.
     */
    private record Member(TokenDoc doc, TokenDoc trailing) {}

    /**
     * Members with a comma between them, one to a line where they do not fit. The comma stays on
     * the line its member ends, and a comment written at the end of that line follows the comma.
     *
     * <p>What opens a member's line is the member's own place's and is written from there, so
     * nothing here says where a line begins. A run that wrote the boundary itself would be saying
     * it in one place while the members said it in another.
     */
    private static TokenDoc separated(List<Member> members) {
        List<TokenDoc> parts = new ArrayList<>();
        for (int i = 0; i < members.size(); i++) {
            parts.add(members.get(i).doc());
            if (i < members.size() - 1) {
                parts.add(GAP);
                parts.add(COMMA);
            }
            parts.add(members.get(i).trailing());
        }
        return concat(parts);
    }

    /** A member of a run: a place whose line the layout may open with a break. */
    private Place memberPlace(Place run, SyntaxNode node) {
        return places.under(run, node.kind(), Opening.breaks(TokenDoc.Break.MAY),
                Written.of(node));
    }

    /**
     * What ends the line a construct opens with — the {@code example j}, the {@code data D =}, the
     * {@code match … with}, the {@code else}, the <code>{</code>.
     *
     * <p>A construct written over several lines has a first line that none of its members is on, so
     * that line is a place of its own: what the source wrote at the end of it is about the construct
     * and belongs there rather than travelling to wherever the construct ends.
     */
    private TokenDoc headerLine(Place at, Optional<SyntaxToken> opener) {
        return opener.map(t -> headerLine(at, t)).orElse(TokenDoc.NIL);
    }

    private TokenDoc headerLine(Place at, SyntaxToken opener) {
        return TokenDoc.endsTheLineOf(places.under(at, opener.kind(), Opening.NONE,
                new Written.Run(opener.start(), opener.end())));
    }

    /** {@code d}, written at {@code place}, with the comments that construct still hands over. The
     *  place is what says which construct that is, so nothing here reads the node again. */
    private Member member(Place place, TokenDoc d) {
        return new Member(TokenDoc.at(place, d), TokenDoc.endsTheLineOf(place));
    }

    /**
     * Members between brackets. What sits just inside them is a boundary like any other: the
     * layout may break there, and where it does not, what is written is the rule's answer for the
     * bracket against what stands next to it. It used to be the caller's pick of one line or the
     * other, which made {@code exposing ( a, b )} and {@code f(a, b)} two decisions instead of one.
     *
     * <p>{@code construct} is what the canonical form writes here, which is not always the node the
     * source had: a definition written {@code let f = (x) -> x} is written back as
     * {@code let f (x) = x}, and those brackets are a parameter list in what is read back.
     */
    private static TokenDoc delimited(Place run, SyntaxKind construct, TokenDoc open,
            List<Member> members, TokenDoc close) {
        if (members.isEmpty()) {
            // What goes between brackets with no member is one line or none, and which of the two
            // depends on whether a comment was written there. The construction does not know, so it
            // hands the brackets over and the pass that answers the carriers says.
            return new TokenDoc.Vacant(run, construct, open, close, INDENT);
        }
        return group(bracketed(construct, open, members, close));
    }

    /**
     * The same brackets and members, with no group of its own.
     *
     * <p>For the one caller that has to decide this bracket's layout together with what is written
     * after it. A group is one decision, so a construct holding two of them holds two, and which of
     * the two gives way first is then whichever the layout reaches with too little room left —
     * an order that comes from how the document was built rather than from anything anyone decided.
     * Handing the brackets over ungrouped is how a caller makes the two one decision.
     */
    private static TokenDoc bracketed(SyntaxKind construct, TokenDoc open, List<Member> members,
            TokenDoc close) {
        return TokenDoc.node(construct, concat(open,
                nest(INDENT, separated(members)),
                SOFT_GAP, close));
    }

    /**
     * The place one part of a chain is written at. The connector that joins it to what came before
     * opens its line, so the connector is the place's opener: what is written above the part goes
     * above the connector too, and a comment put after the connector would leave the part itself
     * starting a line the connector never opened.
     *
     * <p>{@code owner} is null only where the source has nothing there — an example row with no
     * expected value — and such a place stands for nothing, which is ordinary.
     */
    private Place segmentPlace(Place chain, TokenDoc connector, SyntaxNode owner) {
        return places.under(chain, owner == null ? null : owner.kind(),
                Opening.breaks(TokenDoc.Break.MAY, connector), Written.of(owner));
    }


    private Place segmentPlace(Place chain, TokenDoc connector, SyntaxToken owner) {
        return places.under(chain, owner.kind(),
                Opening.breaks(TokenDoc.Break.MAY, connector),
                new Written.Run(nameStart(owner), nameEnd(owner)));
    }

    /**
     * A part of a chain that something in the source stands for. Every segment of every chain is
     * built here: a chain is written from what the source holds, and one built without asking what
     * was written above and beside it is one whose comments have nowhere to go.
     */
    private TokenDoc segment(Place place, SyntaxNode owner, TokenDoc doc) {
        return owner == null
                ? TokenDoc.at(place, concat(GAP, doc))
                : TokenDoc.at(place, concat(GAP, doc, TokenDoc.endsTheLineOf(place)));
    }

    /**
     * The last part of a run the canonical form writes flat.
     *
     * <p>It ends where the whole run does, and what the run is written inside goes on writing that
     * line — a condition's {@code then}, a call's {@code )}. So this part is not what ends the line
     * and carries nothing there; what does is whatever holds the run, whose own place is outside the
     * group the run is laid out in. Only this shape has it: a match arm's body and an example row's
     * expected value are the last of their chains and do end their lines.
     */
    private TokenDoc lastOfARun(Place place, TokenDoc doc) {
        return TokenDoc.at(place, concat(GAP, doc));
    }

    /** The same for a part the grammar writes as a bare identifier rather than as a node — a sum's
     * cases. It is the only other way a chain's part can stand for something written, so between the
     * two of them nothing else builds a segment. Such a part always stands for something, so unlike
     * the one above there is no absent owner to ask about and the place says all of it. */
    private TokenDoc segment(Place place, TokenDoc doc) {
        return TokenDoc.at(place, concat(GAP, doc, TokenDoc.endsTheLineOf(place)));
    }

    /**
     * A head and the parts written after it, each opening with its connector — a union's {@code |},
     * an operator chain's operator, a pipeline's {@code |>}, an example row's {@code :} and
     * {@code ->}. Broken, each part starts a line one indent in and the connector leads it, so what
     * joins two parts is visible at the front of the second.
     */
    private static TokenDoc chained(Member head, List<TokenDoc> segments) {
        return group(concat(head.doc(), head.trailing(), nest(INDENT, concat(segments))));
    }

    /** The place the part of a chain written before the first connector is at. Nothing opens its
     * line: it is written where the construct holding the chain had already got to. */
    private Place headPlace(Place chain, SyntaxNode owner) {
        return places.under(chain, owner == null ? null : owner.kind(), Opening.NONE,
                Written.of(owner));
    }

    /**
     * The part of a chain written before the first connector.
     *
     * <p>The same whatever it stands for — a node, an identifier the grammar writes as a token, or
     * nothing the source wrote. What is written above a head belongs to the construct holding the
     * chain and is written above that construct's own line, so a head carries only what ends the
     * line it opens, and the place is all this needs to say that.
     */
    private Member head(Place place, TokenDoc doc) {
        return new Member(TokenDoc.at(place, doc), TokenDoc.endsTheLineOf(place));
    }

    // --- top level ---

    private TokenDoc file(SyntaxNode file) {
        comments = attach(file);
        Place ofTheFile = places.fileOf(file);
        List<TokenDoc> parts = new ArrayList<>();
        List<SyntaxNode> items = new ArrayList<>();
        for (SyntaxNode item : file.childNodes()) {
            if (isTopLevel(item.kind())) {
                items.add(item);
            }
        }
        java.util.Set<SyntaxNode> paragraph = openedByABlankLine(file, items);
        SyntaxKind prev = null;
        for (SyntaxNode item : items) {
            // A top-level item's comments are read the same way a member's are, and marked written
            // the same way: an `example`'s comment is the item's leading trivia here and the first
            // row's from inside, and it belongs to whichever asks first.
            //
            // The break that separates an item from the one before opens the item's line, so it is
            // the item's; the first item's line is opened by the start of the file. The blank line
            // between two of them separates them and belongs to neither, so it stays here.
            Place at = places.under(ofTheFile, item.kind(),
                    prev == null ? Opening.FILE_BEGINS
                            : Opening.forced(Obligation.MEMBERS_TAKE_LINES_OF_THEIR_OWN),
                    Written.of(item));
            if (prev != null && blankBetween(prev, item.kind(), paragraph.contains(item))) {
                parts.add(TokenDoc.blank(Obligation.A_BLANK_LINE_SEPARATES_TOP_LEVEL_ITEMS));
            }
            parts.add(TokenDoc.at(at, concat(item(item, at), TokenDoc.endsTheLineOf(at))));
            prev = item.kind();
        }
        parts.add(TokenDoc.carries(ofTheFile, Carrier.AT_END));
        parts.add(TokenDoc.forced(Obligation.A_FILE_ENDS_WITH_ONE_NEWLINE));
        // The file joins its items, so the boundary between the last token of one and the first of
        // the next belongs to a construct like any other. Left unnamed, the one adjacency a reader
        // can close up by hand — writing two top-level items on a line — is the one no rule is
        // recorded against.
        //
        // And it is a level, written at column zero. The indentation rule answers about a pair of
        // levels, so without the file among them the outermost pair has one member and the column
        // its lines begin at is decided by nothing: a source that indented every definition it has
        // departed from a canonical form no rule could name. Nesting by nothing writes what it
        // wrote before.
        return TokenDoc.nest(0, TokenDoc.node(file.kind(), concat(parts)));
    }

    /**
     * What separates two top-level items: one blank line, or none.
     *
     * <p>Two of them are the file's rather than the author's. A module header is a header and the
     * file begins under it; the imports are a block and what follows them is the module's own text.
     * Both are written with a blank line under them whatever was there, which is what gofmt writes
     * under a package clause and under an import block.
     *
     * <p>Everywhere else {@code written} says, which is whether the author put a blank line there.
     * That a paragraph break is there is something a reader wrote and the canonical form keeps;
     * how big it is is not, so any number of blank lines comes back as one. The canonical form still
     * has one answer per input, and a run of related one-line declarations stays a run — which
     * writing a blank line between every pair took away.
     */
    private static boolean blankBetween(SyntaxKind prev, SyntaxKind current, boolean written) {
        if (prev == SyntaxKind.MODULE_HEADER || prev == SyntaxKind.EXAMPLES_FILE_HEADER) {
            return true;
        }
        if (prev == SyntaxKind.IMPORT_DECL && current != SyntaxKind.IMPORT_DECL) {
            return true;
        }
        return written;
    }

    /**
     * Which of {@code members} the author wrote a blank line in front of.
     *
     * <p>Read from the trivia between the last code token of one member and the first of the next.
     * A comment written in that gap belongs to one of the two members, and the blank line beside it
     * is what separates the two groups either way, so what is asked is the gap and not the comment.
     *
     * <p>The tree is lossless and holds the blank lines; nothing read them. Counting the line breaks
     * rather than asking whether one is there is the whole of the difference between a paragraph
     * break and the end of a line.
     */
    private static java.util.Set<SyntaxNode> openedByABlankLine(SyntaxNode parent,
            List<SyntaxNode> members) {
        java.util.Set<SyntaxNode> out =
                java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        if (members.size() < 2) {
            return out;
        }
        List<SyntaxToken> blanks = new ArrayList<>();
        for (SyntaxToken t : tokens(parent)) {
            if (t.kind() == SyntaxKind.WHITESPACE && newlines(t) >= 2) {
                blanks.add(t);
            }
        }
        for (int i = 1; i < members.size(); i++) {
            SyntaxToken last = lastCodeTokenOf(members.get(i - 1));
            int from = last == null ? members.get(i - 1).end() : last.end();
            int to = firstCodeOffset(members.get(i));
            for (SyntaxToken t : blanks) {
                if (t.start() >= from && t.start() < to) {
                    out.add(members.get(i));
                    break;
                }
            }
        }
        return out;
    }

    /** The kinds {@link #file} writes as items of the file, and so the kinds the separation between
     * two items is a function of. Reachable from a test so that the table of what goes between two
     * of them can be asked whether it covers every pair rather than the pairs someone thought of. */
    static boolean isTopLevel(SyntaxKind k) {
        return k == SyntaxKind.MODULE_HEADER || k == SyntaxKind.IMPORT_DECL
                || k == SyntaxKind.DATA_DEF || k == SyntaxKind.BEHAVIOR_DEF || k == SyntaxKind.FN_DEF
                || k == SyntaxKind.EXAMPLE_DEF || k == SyntaxKind.EXAMPLES_FILE_HEADER
                || k == SyntaxKind.FAKE_DEF;
    }

    private TokenDoc item(SyntaxNode n, Place at) {
        places.within(n, at);
        return switch (n.kind()) {
            case MODULE_HEADER -> moduleHeader(n, at);
            case IMPORT_DECL -> importDecl(n, at);
            case DATA_DEF -> dataDef(n, at);
            case BEHAVIOR_DEF -> behaviorDef(n, at);
            case FN_DEF -> fnDef(n, at);
            case EXAMPLES_FILE_HEADER -> examplesFileHeader(n, at);
            case EXAMPLE_DEF -> exampleDef(n, at);
            case FAKE_DEF -> fakeDef(n, at);
            default -> throw new IllegalStateException("no case writes " + n.kind());
        };
    }

    // --- example ---

    private TokenDoc examplesFileHeader(SyntaxNode n, Place at) {
        return TokenDoc.node(n.kind(), concat(ident("examples"), GAP, ident("for"), GAP,
                qualifiedName(n.child(SyntaxKind.QUALIFIED_NAME).orElseThrow(), at)));
    }

    private TokenDoc exampleDef(SyntaxNode n, Place at) {
        List<SyntaxToken> ids = idents(n);   // ["example", target]
        String target = ids.size() >= 2 ? ids.get(1).text() : "";
        List<TokenDoc> rows = new ArrayList<>();
        for (SyntaxNode row : childNodes(n, SyntaxKind.EXAMPLE_ROW)) {
            Place r = places.under(at, row.kind(),
                    Opening.forced(Obligation.MEMBERS_TAKE_LINES_OF_THEIR_OWN),
                    Written.of(row));
            rows.add(TokenDoc.at(r, concat(exampleRow(row, r), TokenDoc.endsTheLineOf(r))));
        }
        rows.add(TokenDoc.carries(at, Carrier.AT_END));
        return TokenDoc.node(n.kind(), concat(ident("example"), GAP, ident(target),
                ids.size() >= 2 ? headerLine(at, ids.get(ids.size() - 1)) : TokenDoc.NIL, nest(INDENT, concat(rows))));
    }

    /**
     * A row of an example: its description, its input and what it is expected to give. The three are
     * the row's own parts, so the row is what breaks when it does not fit — a row that broke inside
     * its input instead left {@code ), Amount(100)) -> Accepted} opening a line, and stopped showing
     * which part was which.
     */
    private TokenDoc exampleRow(SyntaxNode n, Place at) {
        var desc = n.token(SyntaxKind.STRING_LIT);
        var argList = n.child(SyntaxKind.ARG_LIST);
        // The description opens the row where there is one; otherwise the input does. Either way the
        // input is written at the place the row's head or its first segment is.
        Place ofTheHead = desc.isPresent()
                ? places.under(at, SyntaxKind.STRING_LIT, Opening.NONE,
                        new Written.Run(desc.get().start(), desc.get().end()))
                : headPlace(at, argList.orElse(null));
        Place ofTheInput = desc.isPresent()
                ? segmentPlace(at, COLON, argList.orElse(null))
                : ofTheHead;

        TokenDoc input = argList
                .map(a -> delimited(ofTheInput, SyntaxKind.ARG_LIST, LPAREN,
                        exprDocs(a, ofTheInput), RPAREN))
                .orElse(concat(LPAREN, GAP, RPAREN));
        var with = n.child(SyntaxKind.WITH_CLAUSE);
        if (with.isPresent()) {
            // Written after the input on the input's line, so it is a place of that segment rather
            // than of the row. A comment about the whole clause then reaches the carrier of the
            // line the clause is on, which is the line the input ends.
            Place ofTheWith = places.under(ofTheInput, with.get().kind(), Opening.NONE,
                    Written.of(with.get()));
            List<Member> binds = new ArrayList<>();
            for (SyntaxNode b : childNodes(with.get(), SyntaxKind.WITH_BINDING)) {
                // the first binding is written after the `with` on its line, so nothing opens one
                Place bind = binds.isEmpty()
                        ? places.under(ofTheWith, b.kind(), Opening.NONE, Written.of(b))
                        : memberPlace(ofTheWith, b);
                // The dependency, which is a name and may be written through its module.
                binds.add(member(bind, TokenDoc.node(b.kind(), concat(dottedName(idents(b)), GAP,
                        ASSIGN, GAP,
                        childAt(bind, firstExprChildOpt(b).orElseThrow(), Opening.NONE)))));
            }
            // The `with` is the clause's own, as a product block's braces are, so the place is
            // what it wrote and not what its bindings cover.
            input = concat(input, GAP, TokenDoc.node(with.get().kind(), TokenDoc.at(ofTheWith,
                    concat(TokenDoc.token(SyntaxKind.WITH_KW, "with"), GAP,
                            group(nest(INDENT, separated(withEndComments(ofTheWith, binds))))))));
        }

        List<TokenDoc> segs = new ArrayList<>();
        Member head;
        if (desc.isPresent()) {
            head = head(ofTheHead, token(desc.get()));
            segs.add(segment(ofTheInput, argList.orElse(null), input));
        } else {
            // with no description the input opens the row, so the row's head carries its comments
            head = argList.isEmpty() ? new Member(TokenDoc.at(ofTheHead, input), TokenDoc.NIL)
                    : head(ofTheHead, input);
        }
        List<SyntaxNode> expected = exprChildren(n);   // the row's expr child that is not the ARG_LIST
        SyntaxNode gives = expected.isEmpty() ? null : expected.get(0);
        Place ofTheExpected = segmentPlace(at, ARROW, gives);
        // What the row put where its answer goes: the expression it wrote, the mark that says the
        // answer is owed, or nothing where the row is malformed. The mark is a token of the row and
        // is written back as it was read — a formatter that dropped it would answer a row nobody
        // has answered.
        TokenDoc answer = gives != null ? expr(gives, ofTheExpected)
                : n.token(SyntaxKind.UNANSWERED).isPresent() ? UNANSWERED : TokenDoc.NIL;
        segs.add(segment(ofTheExpected, gives, answer));
        return concat(PIPE, GAP, TokenDoc.node(n.kind(), chained(head, segs)));
    }

    private TokenDoc fakeDef(SyntaxNode n, Place at) {
        // ["fake", target…]. The target is a name and may be written through the module that
        // declares the behavior, so every identifier after the first belongs to it.
        List<SyntaxToken> ids = idents(n);
        TokenDoc target = ids.size() >= 2
                ? dottedName(ids.subList(1, ids.size())) : TokenDoc.NIL;
        List<TokenDoc> rows = new ArrayList<>();
        for (SyntaxNode row : childNodes(n, SyntaxKind.FAKE_ROW)) {
            Place r = places.under(at, row.kind(),
                    Opening.forced(Obligation.MEMBERS_TAKE_LINES_OF_THEIR_OWN),
                    Written.of(row));
            rows.add(TokenDoc.at(r, concat(fakeRow(row, r), TokenDoc.endsTheLineOf(r))));
        }
        rows.add(TokenDoc.carries(at, Carrier.AT_END));
        return TokenDoc.node(n.kind(), concat(ident("fake"), GAP, target,
                ids.size() >= 2 ? headerLine(at, ids.get(ids.size() - 1)) : TokenDoc.NIL, nest(INDENT, concat(rows))));
    }

    private TokenDoc fakeRow(SyntaxNode n, Place at) {
        var args = n.child(SyntaxKind.ARG_LIST);
        Place ofTheInput = headPlace(at, args.orElse(null));
        TokenDoc input;
        if (args.isPresent()) {
            input = delimited(ofTheInput, SyntaxKind.ARG_LIST, LPAREN, exprDocs(args.get(), ofTheInput),
                    RPAREN);
        } else {
            input = ident("_");   // the default row
        }
        List<SyntaxNode> outs = exprChildren(n);
        // the input opens the row, so the head carries what was written above and beside it
        Member head = args.isPresent() ? head(ofTheInput, input)
                : new Member(TokenDoc.at(ofTheInput, input), TokenDoc.NIL);
        SyntaxNode gives = outs.isEmpty() ? null : outs.get(0);
        Place ofTheOutput = segmentPlace(at, ARROW, gives);
        return concat(PIPE, GAP, TokenDoc.node(n.kind(), chained(head,
                List.of(segment(ofTheOutput, gives,
                        gives == null ? TokenDoc.NIL : expr(gives, ofTheOutput))))));
    }

    private TokenDoc moduleHeader(SyntaxNode n, Place at) {
        TokenDoc d = concat(TokenDoc.token(SyntaxKind.MODULE_KW, "module"), GAP,
                qualifiedName(n.child(SyntaxKind.QUALIFIED_NAME).orElseThrow(), at));
        return TokenDoc.node(n.kind(), n.child(SyntaxKind.EXPOSING_CLAUSE)
                .map(c -> concat(d, GAP, exposing(c, at)))
                .orElse(d));
    }

    private TokenDoc exposing(SyntaxNode clause, Place at) {
        Place run = places.under(at, clause.kind(), Opening.NONE, Written.of(clause));
        List<Member> entries = new ArrayList<>();
        for (SyntaxNode e : childNodes(clause, SyntaxKind.EXPOSED_ENTRY)) {
            Place entry = memberPlace(run, e);
            TokenDoc name = qualifiedName(e.child(SyntaxKind.QUALIFIED_NAME).orElseThrow(), entry);
            entries.add(member(entry, TokenDoc.node(e.kind(), e.child(SyntaxKind.RET_TYPE)
                    .map(rt -> concat(name, GAP, COLON, GAP, retType(rt, entry)))
                    .orElse(name))));
        }
        return TokenDoc.at(run, delimited(run, SyntaxKind.EXPOSING_CLAUSE,
                concat(TokenDoc.token(SyntaxKind.EXPOSING_KW, "exposing"), GAP, LPAREN),
                withEndComments(run, entries), RPAREN));
    }

    private TokenDoc importDecl(SyntaxNode n, Place at) {
        TokenDoc d = concat(TokenDoc.token(SyntaxKind.IMPORT_KW, "import"), GAP,
                qualifiedName(n.child(SyntaxKind.QUALIFIED_NAME).orElseThrow(), at));
        Optional<SyntaxNode> alias = n.child(SyntaxKind.IMPORT_ALIAS);
        if (alias.isPresent()) {
            places.within(alias.get(), at);
            d = concat(d, GAP, TokenDoc.node(alias.get().kind(),
                    concat(TokenDoc.token(SyntaxKind.AS_KW, "as"), GAP,
                            token(idents(alias.get()).get(0)))));
        }
        Optional<SyntaxNode> list = n.child(SyntaxKind.NAME_LIST);
        if (list.isEmpty()) {
            return TokenDoc.node(n.kind(), d);   // one that only renames it, or only names it
        }
        Place run = places.under(at, list.get().kind(), Opening.NONE, Written.of(list.get()));
        List<Member> names = new ArrayList<>();
        for (SyntaxToken t : idents(list.get())) {
            names.add(tokenMember(places.under(run, t.kind(),
                    Opening.breaks(TokenDoc.Break.MAY),
                    new Written.Run(nameStart(t), nameEnd(t))), token(t)));
        }
        return TokenDoc.node(n.kind(), concat(d, GAP, TokenDoc.at(run,
                delimited(run, SyntaxKind.NAME_LIST, LPAREN, withEndComments(run, names),
                        RPAREN))));
    }

    // --- data ---

    private TokenDoc dataDef(SyntaxNode n, Place at) {
        String name = firstIdent(n);
        List<TokenDoc> invariants = new ArrayList<>();
        for (SyntaxNode inv : childNodes(n, SyntaxKind.INVARIANT_CLAUSE)) {
            // A named clause keeps its name: it is what an attempt's arm and a boundary issue call it.
            TokenDoc label = inv.token(SyntaxKind.ASSIGN).isPresent()
                    ? concat(ident(firstIdent(inv)), GAP, ASSIGN, GAP) : TokenDoc.NIL;
            Place clause = places.under(at, inv.kind(),
                    Opening.forced(Obligation.MEMBERS_TAKE_LINES_OF_THEIR_OWN),
                    Written.of(inv));
            invariants.add(TokenDoc.at(clause, concat(TokenDoc.node(inv.kind(),
                            concat(TokenDoc.token(SyntaxKind.INVARIANT_KW, "invariant"), GAP, label,
                                    childAt(clause, onlyExpr(inv), Opening.NONE))),
                            TokenDoc.endsTheLineOf(clause))));
        }

        var product = n.child(SyntaxKind.PRODUCT_BODY);
        if (product.isPresent()) {
            if (isEmptyProduct(product.get())) {
                places.within(product.get(), at);
                return TokenDoc.node(n.kind(),
                        concat(TokenDoc.token(SyntaxKind.DATA_KW, "data"), GAP, ident(name), GAP,
                        ASSIGN, GAP, TokenDoc.node(product.get().kind(), concat(LBRACE, GAP, RBRACE)),
                        headerLine(at, n.token(SyntaxKind.ASSIGN)),
                        nest(INDENT, concat(invariants))));
            }
            return TokenDoc.node(n.kind(), concat(TokenDoc.token(SyntaxKind.DATA_KW, "data"), GAP, ident(name), GAP, ASSIGN,
                    headerLine(at, n.token(SyntaxKind.ASSIGN)),
                    nest(INDENT, concat(productBody(product.get(), at), concat(invariants)))));
        }
        var sum = n.child(SyntaxKind.SUM_BODY);
        if (sum.isPresent()) {
            // A sum's cases are bare idents, not nodes, so a case's comments are held against where
            // its identifier is rather than against a member node.
            //
            // The first case shares its line with `data S =`, so a comment written above it has
            // nowhere to go there without describing the declaration instead: the union moves down
            // a line, and the line it moves to is the union's own. Which shape the union is written
            // in is not where the comment goes — it is what the source has above that case, which
            // the token stream has already said.
            SyntaxToken firstCase = firstCaseOf(sum.get());
            boolean movesDown = firstCase != null && comments.aboveToken()
                    .containsKey(new Written.Run(nameStart(firstCase), nameEnd(firstCase)));
            Place chainAt = places.under(at, sum.get().kind(),
                    movesDown ? Opening.forced(Obligation.NOTHING_SHARES_A_COMMENTS_LINE)
                            : Opening.NONE,
                    Written.of(sum.get()));
            TokenDoc head = null;
            List<TokenDoc> cases = new ArrayList<>();
            for (SyntaxElement e : sum.get().children()) {
                if (!(e instanceof SyntaxToken t) || t.kind() != SyntaxKind.IDENT) {
                    continue;
                }
                if (head == null) {
                    Place first = places.under(chainAt, t.kind(), Opening.NONE,
                            new Written.Run(nameStart(t), nameEnd(t)));
                    head = TokenDoc.at(first,
                            concat(token(t), TokenDoc.endsTheLineOf(first)));
                } else {
                    cases.add(segment(segmentPlace(chainAt, PIPE, t), token(t)));
                }
            }
            TokenDoc chain = TokenDoc.at(chainAt, TokenDoc.node(sum.get().kind(),
                    chained(new Member(head, TokenDoc.NIL), cases)));
            if (!movesDown) {
                return TokenDoc.node(n.kind(), concat(TokenDoc.token(SyntaxKind.DATA_KW, "data"), GAP, ident(name), GAP, ASSIGN, GAP, chain));
            }
            return TokenDoc.node(n.kind(), concat(TokenDoc.token(SyntaxKind.DATA_KW, "data"), GAP, ident(name), GAP, ASSIGN,
                    nest(INDENT, chain)));
        }
        var newtype = n.child(SyntaxKind.NEWTYPE_BODY);
        if (newtype.isPresent()) {
            // The representation is written after the `=` on the declaration's line, so nothing
            // opens a line for it. It still ends that line, which is what lets it own the comment
            // written there.
            Place ofTheBody = places.under(at, newtype.get().kind(), Opening.NONE,
                    Written.of(newtype.get()));
            TokenDoc inner = TokenDoc.at(ofTheBody, concat(typeRef(typeChild(newtype.get()), ofTheBody), TokenDoc.endsTheLineOf(ofTheBody)));
            return TokenDoc.node(n.kind(), concat(TokenDoc.token(SyntaxKind.DATA_KW, "data"), GAP, ident(name), GAP, ASSIGN, GAP, inner,
                    nest(INDENT, concat(invariants))));
        }
        return TokenDoc.node(n.kind(), concat(TokenDoc.token(SyntaxKind.DATA_KW, "data"), GAP, ident(name)));   // unit
    }

    /** The identifier a union's first case is written from, or null for a body with no case in it. */
    private static SyntaxToken firstCaseOf(SyntaxNode sum) {
        for (SyntaxElement e : sum.children()) {
            if (e instanceof SyntaxToken t && t.kind() == SyntaxKind.IDENT) {
                return t;
            }
        }
        return null;
    }

    /** The leading-comma product block: {@code { f1: T1\n, f2: T2\n}}. Multi-line wherever it holds
     * anything: the block writes its opening brace on the first member's line, so a body with no
     * members has no line to write one on, and it is written as the empty brackets it is. */
    private TokenDoc productBody(SyntaxNode body, Place at) {
        Place run = places.under(at, body.kind(), Opening.NONE, Written.of(body));
        List<TokenDoc> lines = new ArrayList<>();
        for (SyntaxNode m : body.childNodes()) {
            if (m.kind() != SyntaxKind.FIELD && m.kind() != SyntaxKind.SPREAD_MEMBER) {
                continue;
            }
            // The opener is part of the member's line, so it is what the place is opened with: a
            // comment written after it would leave the member starting a line of its own, at the
            // block's indent rather than after the comma the rest of the block is written with.
            boolean first = lines.isEmpty();
            Place place = places.under(run, m.kind(),
                    Opening.forced(Obligation.MEMBERS_TAKE_LINES_OF_THEIR_OWN,
                            first ? LBRACE : COMMA),
                    Written.of(m));
            TokenDoc written = m.kind() == SyntaxKind.FIELD
                    ? field(m, place)
                    : TokenDoc.node(m.kind(), concat(
                            TokenDoc.token(SyntaxKind.SPREAD, "..."), GAP,
                            dottedName(idents(m))));
            lines.add(TokenDoc.at(place, concat(GAP, written, TokenDoc.endsTheLineOf(place))));
        }
        if (lines.isEmpty()) {
            // No member wrote the opening brace, so the block writes it on a line of its own. This
            // is the body that holds only comments: `dataDef` writes a body holding nothing at all
            // as `{}` and never reaches here.
            lines.add(concat(TokenDoc.forced(Obligation.A_BRACKET_TAKES_A_LINE_OF_ITS_OWN),
                    LBRACE));
        }
        lines.add(TokenDoc.carries(run, Carrier.AT_END));
        lines.add(concat(TokenDoc.forced(Obligation.A_BRACKET_TAKES_A_LINE_OF_ITS_OWN),
                RBRACE));
        // The place is the whole block and not what its members cover: the braces are the block's,
        // so the members leave them out and where the block is is where it wrote.
        return TokenDoc.node(body.kind(), TokenDoc.at(run, concat(lines)));
    }

    /**
     * Whether {@code body} has nothing for the block to write a line for. A body holding only
     * comments is not empty: they are written where a member would be, so the block keeps its lines.
     *
     * <p>Asked of the attachments rather than through the walk that writes the end-of-line
     * comments, which takes the comments it reports. A question about what is there has to leave it
     * there for whoever writes it.
     */
    private boolean isEmptyProduct(SyntaxNode body) {
        for (SyntaxNode m : body.childNodes()) {
            if (m.kind() == SyntaxKind.FIELD || m.kind() == SyntaxKind.SPREAD_MEMBER) {
                return false;
            }
        }
        return comments.atEnd().getOrDefault(body, List.of()).isEmpty();
    }

    private TokenDoc field(SyntaxNode n, Place at) {
        TokenDoc d = concat(ident(firstIdent(n)), GAP, COLON, GAP, typeRef(typeChild(n), at));
        return TokenDoc.node(n.kind(),
                n.token(SyntaxKind.QUESTION).isPresent() ? concat(d, GAP, QUESTION) : d);
    }

    // --- behavior ---

    private TokenDoc behaviorDef(SyntaxNode n, Place at) {
        String name = firstIdent(n);
        var sig = n.child(SyntaxKind.BEHAVIOR_SIG);
        if (sig.isPresent()) {
            SyntaxNode s = sig.get();
            Place ofTheSig = places.under(at, s.kind(), Opening.NONE, Written.of(s));
            TokenDoc params = paramList(s.child(SyntaxKind.PARAM_LIST).orElseThrow(), ofTheSig);
            SyntaxNode retNode = s.child(SyntaxKind.RET_TYPE).orElseThrow();
            // What is written after the `->` is on the line the `->` is on, so nothing opens a line
            // for it. It still ends that line, which is what lets it own the comment there.
            Place ofTheResult = places.under(ofTheSig, retNode.kind(), Opening.NONE,
                    Written.of(retNode));
            TokenDoc ret = TokenDoc.at(ofTheResult, retType(retNode, ofTheResult));
            List<TokenDoc> clauses = new ArrayList<>();
            for (SyntaxNode c : s.childNodes()) {
                if (c.kind() != SyntaxKind.CONSTRUCTS_CLAUSE
                        && c.kind() != SyntaxKind.DEPENDS_CLAUSE
                        && c.kind() != SyntaxKind.ENSURES_CLAUSE) {
                    continue;
                }
                Place clause = places.under(ofTheSig, c.kind(),
                        Opening.forced(Obligation.MEMBERS_TAKE_LINES_OF_THEIR_OWN),
                        Written.of(c));
                TokenDoc listed;
                if (c.kind() == SyntaxKind.CONSTRUCTS_CLAUSE) {
                    listed = TokenDoc.node(c.kind(),
                            concat(TokenDoc.token(SyntaxKind.CONSTRUCTS_KW, "constructs"), GAP,
                                    nameList(c, 0, clause)));
                } else if (c.kind() == SyntaxKind.DEPENDS_CLAUSE) {
                    listed = TokenDoc.node(c.kind(),
                            concat(TokenDoc.token(SyntaxKind.DEPENDS_KW, "depends"), GAP,
                                    ident("on"), GAP, nameList(c, 1, clause)));
                } else {
                    listed = ensuresClause(c, clause);
                }
                clauses.add(TokenDoc.at(clause, concat(listed, TokenDoc.endsTheLineOf(clause))));
            }
            return TokenDoc.node(n.kind(),
                    concat(TokenDoc.token(SyntaxKind.BEHAVIOR_KW, "behavior"), GAP, ident(name),
                            GAP, COLON, GAP, TokenDoc.at(ofTheSig,
                                    TokenDoc.node(s.kind(), concat(
                                            signature(params, ret),
                                            TokenDoc.carries(ofTheResult, Carrier.TRAILING),
                                            nest(INDENT, concat(clauses)))))));
        }
        SyntaxNode pipe = n.child(SyntaxKind.PIPE_BEHAVIOR).orElseThrow();
        Place ofThePipe = places.under(at, pipe.kind(), Opening.NONE, Written.of(pipe));
        List<SyntaxNode> stages = childNodes(pipe, SyntaxKind.STAGE);
        TokenDoc declaredOut = pipe.child(SyntaxKind.RET_TYPE)
                .map(rt -> concat(GAP, ARROW, GAP, retType(rt, ofThePipe))).orElse(TokenDoc.NIL);
        List<TokenDoc> parts = new ArrayList<>();
        for (int i = 0; i < stages.size(); i++) {
            SyntaxNode st = stages.get(i);
            // What joins a stage to the one before opens the stage's line, so it is the place's
            // opener. What the declaration writes after the last stage is on that stage's line, so
            // it comes before the comment that ends the line rather than after it.
            Place ofTheStage = places.under(ofThePipe, st.kind(),
                    Opening.breaks(TokenDoc.Break.MAY, i == 0 ? TokenDoc.NIL
                            : concat(TokenDoc.token(SyntaxKind.PIPEFWD, ">->"), GAP)),
                    Written.of(st));
            // The declared output is written after the last stage and on that stage's line, so that
            // stage is not what ends the line and carries nothing there: what ends it is the
            // declaration, whose own place is outside the group this run is laid out in.
            boolean last = i == stages.size() - 1;
            parts.add(TokenDoc.at(ofTheStage, concat(stage(st, ofTheStage),
                    last ? declaredOut : TokenDoc.endsTheLineOf(ofTheStage))));
        }
        return TokenDoc.node(n.kind(), concat(TokenDoc.token(SyntaxKind.BEHAVIOR_KW, "behavior"), GAP, ident(name), GAP, ASSIGN,
                TokenDoc.at(ofThePipe,
                        TokenDoc.node(pipe.kind(), group(nest(INDENT, concat(parts)))))));
    }

    private TokenDoc ensuresClause(SyntaxNode clause, Place at) {
        TokenDoc label = clause.token(SyntaxKind.ASSIGN).isPresent()
                ? concat(ident(firstIdent(clause)), GAP, ASSIGN, GAP) : TokenDoc.NIL;
        List<SyntaxNode> arms = childNodes(clause, SyntaxKind.ENSURES_ARM);
        if (arms.isEmpty()) {
            return TokenDoc.node(clause.kind(), concat(
                    TokenDoc.token(SyntaxKind.ENSURES_KW, "ensures"), GAP, label,
                    childAt(at, onlyExpr(clause), Opening.NONE)));
        }
        List<TokenDoc> written = new ArrayList<>();
        for (int i = 0; i < arms.size(); i++) {
            SyntaxNode arm = arms.get(i);
            Place armPlace = places.under(at, arm.kind(), Opening.NONE, Written.of(arm));
            List<TokenDoc> cases = new ArrayList<>();
            for (SyntaxNode name : childNodes(arm, SyntaxKind.QUALIFIED_NAME)) {
                if (!cases.isEmpty()) {
                    cases.add(concat(GAP, PIPE, GAP));
                }
                cases.add(qualifiedName(name, armPlace));
            }
            TokenDoc prefix = i == 0
                    ? concat(TokenDoc.token(SyntaxKind.ENSURES_KW, "ensures"), GAP, label)
                    : concat(GAP, PIPE, GAP);
            written.add(concat(prefix, TokenDoc.at(armPlace, TokenDoc.node(arm.kind(),
                    concat(concat(cases), GAP, ARROW, GAP,
                            childAt(armPlace, onlyExpr(arm), Opening.NONE))))));
        }
        return TokenDoc.node(clause.kind(), concat(written));
    }

    /**
     * A behavior's inputs and output as one decision, so that the parameter list is what gives way
     * when the signature does not fit and the output union is what gives way after it.
     *
     * <p>The order is a rule about reading a signature and not about the shape of the tree. A
     * reader takes a signature as three regions — the name, the inputs, the output — and breaking
     * the union first leaves one member of it on the signature line, attached to the arrow, with
     * the rest below: a union whose members are alike, written as though the first were the answer.
     * Breaking the inputs keeps all three regions whole.
     *
     * <p>What states the order is that the parameters have no group of their own and the union
     * does. This one group covers both, so it is measured with the union flat and breaks at the
     * parameters; the union is then measured on the line the {@code )} left it, and breaks only
     * where it does not fit there by itself. Two groups would be two decisions and the layout would
     * take whichever it reached with too little room — which is the parameters last, since they are
     * written first.
     *
     * <p>The gaps around the arrow never break, so this group adds no place to break that the
     * parameters and the union did not already have.
     */
    private static TokenDoc signature(TokenDoc params, TokenDoc ret) {
        return group(concat(params, GAP, ARROW, GAP, ret));
    }

    private TokenDoc paramList(SyntaxNode n, Place at) {
        Place run = places.under(at, n.kind(), Opening.NONE, Written.of(n));
        List<Member> params = new ArrayList<>();
        for (SyntaxNode p : childNodes(n, SyntaxKind.PARAM)) {
            Place param = memberPlace(run, p);
            params.add(member(param, TokenDoc.node(p.kind(),
                    concat(ident(firstIdent(p)), GAP, COLON, GAP,
                            retType(p.child(SyntaxKind.RET_TYPE).orElseThrow(), param)))));
        }
        List<Member> members = withEndComments(run, params);
        // Ungrouped: a behavior signature lays this list out together with the union after the
        // arrow, and which of the two gives way first is that construct's rule to state.
        return TokenDoc.at(run, members.isEmpty()
                ? new TokenDoc.Vacant(run, SyntaxKind.PARAM_LIST, LPAREN, RPAREN, INDENT)
                : bracketed(SyntaxKind.PARAM_LIST, LPAREN, members, RPAREN));
    }

    /** One stage of a pipeline, which is a name and is written as one. */
    private TokenDoc stage(SyntaxNode n, Place at) {
        return qualifiedName(n, at);
    }

    /** The names a {@code constructs} / {@code depends on} clause lists. {@code skipIdents} drops
     * the leading identifiers that belong to the keyword rather than the list — the {@code on} of
     * {@code depends on}, which lexes as an ordinary identifier. */
    private TokenDoc nameList(SyntaxNode clause, int skipIdents, Place at) {
        Place run = places.under(at, SyntaxKind.NAME_LIST, Opening.NONE, Written.of(clause));
        // an entry may name through a module, so the dots of one name are kept and only a comma
        // starts the next
        List<Member> names = new ArrayList<>();
        List<SyntaxToken> current = new ArrayList<>();
        int skipped = 0;
        for (SyntaxElement e : meaningful(clause)) {
            if (!(e instanceof SyntaxToken t)) {
                continue;
            }
            if (t.kind() == SyntaxKind.IDENT && skipped < skipIdents) {
                skipped++;
                continue;
            }
            switch (t.kind()) {
                case IDENT -> current.add(t);
                case COMMA -> {
                    names.add(named(run, names.isEmpty(), current));
                    current = new ArrayList<>();
                }
                default -> { }   // the dots of one name, and the `constructs` / `depends` keyword
            }
        }
        if (!current.isEmpty()) {
            names.add(named(run, names.isEmpty(), current));
        }
        return TokenDoc.at(run, TokenDoc.node(clause.kind(),
                group(nest(INDENT, separated(withEndComments(run, names))))));
    }

    /** One name of such a clause, held against where its identifiers are. The first is written
     * after the keyword on its line, so nothing opens one for it — which is also why a comment
     * above it is about the clause and not about the name. */
    private Member named(Place run, boolean first, List<SyntaxToken> idents) {
        SyntaxToken from = idents.get(0);
        SyntaxToken to = idents.get(idents.size() - 1);
        Place place = places.under(run, from.kind(),
                first ? Opening.NONE : Opening.breaks(TokenDoc.Break.MAY),
                new Written.Run(nameStart(from), nameEnd(to)));
        return tokenMember(place, dottedName(idents));
    }

    /** The {@code : T} a node wrote, or nothing — a helper's return type, a local binding's annotation. */
    private TokenDoc writtenType(SyntaxNode n, Place at) {
        return n.child(SyntaxKind.RET_TYPE)
                .map(rt -> concat(GAP, COLON, GAP, retType(rt, at))).orElse(TokenDoc.NIL);
    }

    // --- fn ---

    private TokenDoc fnDef(SyntaxNode n, Place at) {
        String name = firstIdent(n);
        // The modifiers are written back in the order the parser reads them: `private partial let`.
        // Each modifier is written at the head of the definition's own line, so that is where it is
        // written and there is no place of its own to record.
        List<TokenDoc> modifiers = new ArrayList<>();
        for (SyntaxNode modifier : childNodes(n, SyntaxKind.PRIVATE_MODIFIER)) {
            places.within(modifier, at);
            modifiers.add(concat(ident("private"), GAP));
        }
        for (SyntaxNode modifier : childNodes(n, SyntaxKind.PARTIAL_MODIFIER)) {
            places.within(modifier, at);
            modifiers.add(concat(ident("partial"), GAP));
        }
        TokenDoc keyword = concat(concat(modifiers), TokenDoc.token(SyntaxKind.LET_KW, "let"), GAP);
        var written = n.child(SyntaxKind.FN_PARAM_LIST);
        // A lambda on the right of `=` is the parameter-list form written the other way round, so it
        // is written back with its parameters on the left. A definition with neither is a value, and
        // writes no list at all.
        SyntaxNode lifted = written.isPresent() ? null : liftedLambda(n);
        TokenDoc params = written.isPresent() ? concat(GAP, fnParamList(written.get(), at))
                : lifted == null ? TokenDoc.NIL : concat(GAP, lambdaParams(lifted, at));
        TokenDoc head = concat(keyword, ident(name), params, writtenType(n, at));

        var intrinsic = n.child(SyntaxKind.INTRINSIC_BODY);
        if (intrinsic.isPresent()) {
            SyntaxToken body = intrinsic.get().token(SyntaxKind.STRING_LIT).orElseThrow();
            Place ofTheBody = places.under(at, intrinsic.get().kind(),
                    Opening.breaks(TokenDoc.Break.MAY), Written.of(intrinsic.get()));
            return TokenDoc.node(n.kind(), concat(head, GAP, ASSIGN,
                    group(nest(INDENT, TokenDoc.at(ofTheBody, concat(TokenDoc.node(intrinsic.get().kind(),
                                    concat(ident("intrinsic"), GAP, token(body))),
                                    TokenDoc.endsTheLineOf(ofTheBody)))))));
        }
        var block = n.child(SyntaxKind.BLOCK_EXPR);
        if (block.isPresent()) {
            return TokenDoc.node(n.kind(), concat(head, GAP, ASSIGN, GAP, block(block.get(), at)));
        }
        // What the canonical form writes after the `=` stands for the source's body where nothing
        // was lifted, and for the lifted lambda's last expression child where something was. The
        // parameter list on the left stands for the lambda itself. Both are written down here
        // because here is where they are known: asked of the source tree afterwards, the answer for
        // either is the lambda, and the canonical form has a lambda at neither position.
        SyntaxNode body = lifted == null ? onlyExpr(n) : lastExprChild(lifted);
        Place ofTheBody = places.under(at, body.kind(), Opening.breaks(TokenDoc.Break.MAY),
                Written.of(body));
        return TokenDoc.node(n.kind(),
                concat(head, GAP, ASSIGN, group(nest(INDENT,
                        TokenDoc.at(ofTheBody, concat(expr(body, ofTheBody), TokenDoc.endsTheLineOf(ofTheBody)))))));
    }

    /** The lambda a parameter-less definition was written as, or null when its body is an ordinary
     * expression and the definition is a value. */
    static SyntaxNode liftedLambda(SyntaxNode n) {
        if (n.child(SyntaxKind.BLOCK_EXPR).isPresent() || n.child(SyntaxKind.INTRINSIC_BODY).isPresent()) {
            return null;
        }
        // A written function type moves nothing. It says what the definition is, and what it says is
        // a function, so the definition is a value of that type; lifting its parameters out would
        // leave the written type describing something the definition is no longer. The frontend
        // reads such a definition as a value for the same reason, and a definition the two of them
        // read differently is one whose canonical form does not compile.
        if (writesAFunctionType(n)) {
            return null;
        }
        SyntaxNode body = onlyExpr(n);
        return body.kind() == SyntaxKind.LAMBDA_EXPR ? body : null;
    }

    /** Whether a definition's written type is a lone function type. This is what the frontend asks
     * of the type it built ({@code Ast.RetType.asFn}), asked of the syntax instead: a sum of a
     * function with anything else is not one, an optional is a type of its own, and a single term in
     * parentheses is the term. */
    private static boolean writesAFunctionType(SyntaxNode n) {
        Optional<SyntaxNode> written = n.child(SyntaxKind.RET_TYPE);
        if (written.isEmpty() || written.get().token(SyntaxKind.QUESTION).isPresent()) {
            return false;
        }
        List<SyntaxNode> cases = typeTerms(written.get());
        return cases.size() == 1 && isFunctionType(cases.get(0));
    }

    private static boolean isFunctionType(SyntaxNode type) {
        if (type.kind() == SyntaxKind.FN_TYPE) {
            return true;
        }
        List<SyntaxNode> elements = typeTerms(type);
        return type.kind() == SyntaxKind.TUPLE_TYPE && elements.size() == 1
                && isFunctionType(elements.get(0));
    }

    private static List<SyntaxNode> typeTerms(SyntaxNode n) {
        List<SyntaxNode> out = new ArrayList<>();
        for (SyntaxNode c : n.childNodes()) {
            if (isTypeNode(c.kind())) {
                out.add(c);
            }
        }
        return out;
    }

    /** A lambda's parameters as a definition's parameter list — always parenthesised, which is the
     * only shape a definition writes. The list is a place of its own, and what the source has at it
     * is the lambda: the parameters moved to the left of the {@code =} and the lambda did not. */
    private TokenDoc lambdaParams(SyntaxNode lambda, Place under) {
        Place run = places.under(under, SyntaxKind.FN_PARAM_LIST, Opening.NONE,
                Written.of(lambda));
        List<Member> params = new ArrayList<>();
        for (SyntaxNode c : lambda.childNodes()) {
            if (isPatternNode(c.kind())) {
                Place param = memberPlace(run, c);
                params.add(member(param, pattern(c, param)));
            }
        }
        return TokenDoc.at(run,
                delimited(run, SyntaxKind.FN_PARAM_LIST, LPAREN, withEndComments(run, params),
                        RPAREN));
    }

    private TokenDoc fnParamList(SyntaxNode n, Place at) {
        Place run = places.under(at, n.kind(), Opening.NONE, Written.of(n));
        List<Member> params = new ArrayList<>();
        for (SyntaxNode p : childNodes(n, SyntaxKind.FN_PARAM)) {
            Place param = memberPlace(run, p);
            SyntaxNode pat = optionalPatternChild(p);
            TokenDoc d = pat == null ? ident(firstIdent(p)) : pattern(pat, param);
            var rt = p.child(SyntaxKind.RET_TYPE);
            if (rt.isPresent()) {
                d = concat(d, GAP, COLON, GAP, retType(rt.get(), param));
            }
            params.add(member(param, TokenDoc.node(p.kind(), d)));
        }
        return TokenDoc.at(run,
                delimited(run, SyntaxKind.FN_PARAM_LIST, LPAREN, withEndComments(run, params), RPAREN));
    }

    // --- types ---

    private TokenDoc fnType(SyntaxNode n, Place at) {
        Place run = places.under(at, n.kind(), Opening.NONE, Written.of(n));
        List<Member> params = new ArrayList<>();
        TokenDoc result = TokenDoc.NIL;
        boolean afterArrow = false;
        for (SyntaxElement e : meaningful(n)) {
            if (e instanceof SyntaxToken t && t.kind() == SyntaxKind.ARROW) {
                afterArrow = true;
            } else if (e instanceof SyntaxNode c && c.kind() == SyntaxKind.RET_TYPE) {
                if (afterArrow) {
                    // written after the `->` on that line, so nothing opens a line for it
                    Place ofTheResult = places.under(run, c.kind(), Opening.NONE, Written.of(c));
                    result = TokenDoc.at(ofTheResult, concat(retType(c, ofTheResult), TokenDoc.endsTheLineOf(ofTheResult)));
                } else {
                    Place param = memberPlace(run, c);
                    params.add(member(param, retType(c, param)));
                }
            }
        }
        // The place is the whole function type and not the bracketed run alone: the result is
        // written at a place under it, and a place holds what is written at the places beneath it.
        return TokenDoc.node(n.kind(), TokenDoc.at(run, concat(
                delimited(run, SyntaxKind.FN_TYPE, LPAREN, withEndComments(run, params), RPAREN),
                GAP, ARROW, GAP, result)));
    }

    private TokenDoc retType(SyntaxNode n, Place at) {
        places.within(n, at);
        List<TokenDoc> cases = new ArrayList<>();
        List<TokenDoc> rest = new ArrayList<>();
        for (SyntaxNode c : n.childNodes()) {
            if (!isTypeNode(c.kind())) {
                continue;
            }
            boolean first = cases.isEmpty();
            Place place = first ? headPlace(at, c) : segmentPlace(at, PIPE, c);
            TokenDoc body = concat(typeTerm(c, place), TokenDoc.endsTheLineOf(place));
            if (first) {
                cases.add(TokenDoc.at(place, body));
            } else {
                rest.add(segment(place, c, typeTerm(c, place)));
            }
        }
        TokenDoc d = cases.isEmpty() ? TokenDoc.NIL
                : TokenDoc.node(n.kind(),
                        chained(new Member(cases.get(0), TokenDoc.NIL), rest));
        // `T?` in a core signature, the same mark a field carries
        return n.token(SyntaxKind.QUESTION).isPresent()
                ? TokenDoc.node(n.kind(), concat(d, GAP, QUESTION)) : d;
    }

    private TokenDoc typeRef(SyntaxNode n, Place at) {
        places.within(n, at);
        if (n.kind() == SyntaxKind.TUPLE_TYPE) {
            Place run = places.under(at, n.kind(), Opening.NONE, Written.of(n));
            List<Member> elems = new ArrayList<>();
            for (SyntaxNode c : n.childNodes()) {
                if (isTypeNode(c.kind())) {
                    Place elem = memberPlace(run, c);
                    elems.add(member(elem, typeTerm(c, elem)));
                }
            }
            return TokenDoc.at(run,
                    delimited(run, SyntaxKind.TUPLE_TYPE, LPAREN, withEndComments(run, elems), RPAREN));
        }
        var typevar = n.token(SyntaxKind.TYPEVAR);
        if (typevar.isPresent()) {
            return token(typevar.get());
        }
        TokenDoc name = qualifiedName(n, at);   // a type may be named through its module or an import alias
        var args = n.child(SyntaxKind.TYPE_ARGS);
        if (args.isEmpty()) {
            return name;
        }
        Place run = places.under(at, args.get().kind(), Opening.NONE, Written.of(args.get()));
        List<Member> typeArgs = new ArrayList<>();
        for (SyntaxNode c : args.get().childNodes()) {
            if (isTypeNode(c.kind())) {
                Place arg = memberPlace(run, c);
                typeArgs.add(member(arg, typeTerm(c, arg)));
            }
        }
        return TokenDoc.node(n.kind(), concat(name, GAP, TokenDoc.at(run,
                delimited(run, SyntaxKind.TYPE_ARGS, LT, withEndComments(run, typeArgs), GT))));
    }

    private static boolean isTypeNode(SyntaxKind k) {
        return k == SyntaxKind.TYPE_REF || k == SyntaxKind.TUPLE_TYPE || k == SyntaxKind.FN_TYPE;
    }

    /** One term of a written type. A function type reads as itself wherever a type goes. */
    private TokenDoc typeTerm(SyntaxNode n, Place at) {
        places.within(n, at);
        return n.kind() == SyntaxKind.FN_TYPE ? fnType(n, at) : typeRef(n, at);
    }

    // --- expressions ---

    /** {@code n} written at {@code at}, which is the place the canonical form has it at. What it
     *  writes under itself is written at places under that one. */
    private TokenDoc expr(SyntaxNode n, Place at) {
        places.within(n, at);
        return switch (n.kind()) {
            case LITERAL_EXPR -> token(firstMeaningfulToken(n));
            case VAR_EXPR -> ident(firstIdent(n));
            case FIELD_ACCESS -> TokenDoc.node(n.kind(),
                    concat(childAt(at, firstExprChild(n), Opening.NONE), GAP, DOT, GAP,
                            ident(lastIdent(n))));
            case FIELD_GETTER -> TokenDoc.node(n.kind(), concat(DOT, GAP, ident(lastIdent(n))));
            case APPLY_EXPR -> apply(n, at);
            case BINARY_EXPR -> binary(n, at);
            case UNARY_EXPR -> TokenDoc.node(n.kind(),
                    concat(TokenDoc.token(SyntaxKind.MINUS, "-"), GAP,
                            childAt(at, onlyExpr(n), Opening.NONE)));
            case PIPE_EXPR -> pipe(n, at);
            case PAREN_EXPR -> TokenDoc.node(n.kind(),
                    concat(LPAREN, GAP, childAt(at, onlyExpr(n), Opening.NONE), GAP, RPAREN));
            case TUPLE_EXPR -> tuple(n, at);
            case LIST_EXPR -> list(n, at);
            case LIST_COMP -> listComp(n, at);
            case IF_EXPR -> ifExpr(n, at);
            case MATCH_EXPR -> matchExpr(n, at);
            case LAMBDA_EXPR -> lambda(n, at);
            case NEW_DATA_EXPR -> newData(n, at);
            case BLOCK_EXPR -> block(n, at);
            case UNREACHABLE_EXPR -> TokenDoc.node(n.kind(), concat(
                    TokenDoc.token(SyntaxKind.UNREACHABLE_KW, "unreachable"), GAP,
                    childAt(at, onlyExpr(n), Opening.NONE)));
            default -> throw new IllegalStateException("no case writes " + n.kind());
        };
    }

    /**
     * {@code child} written at a place of its own under {@code at}.
     *
     * <p>The place, the boundary that opens the line it is on, and what is written there are one
     * call. A construct that could write the line without saying which place is on it is one where
     * the two can come to disagree, which is how a comment came to be filed against a construct the
     * canonical form does not write.
     */
    private TokenDoc childAt(Place at, SyntaxNode child, Opening opening) {
        Place place = places.under(at, child.kind(), opening, Written.of(child));
        return TokenDoc.at(place, expr(child, place));
    }

    /**
     * An argument list applied to the expression before it — every application, whatever is
     * applied. The callee is printed as the expression it is, so a qualified name reaches here as
     * the field read it was parsed as and no name is reassembled from the tokens under the node.
     *
     * <p>Printed on the line its callee ends on: an argument list that began the next line would be
     * a parenthesised expression rather than an application.
     */
    private TokenDoc apply(SyntaxNode n, Place at) {
        return TokenDoc.node(n.kind(),
                concat(childAt(at, firstExprChild(n), Opening.NONE), GAP, arguments(n, at)));
    }

    /** The bracketed argument list of a call or an application. */
    private TokenDoc arguments(SyntaxNode n, Place at) {
        List<SyntaxNode> args = n.child(SyntaxKind.ARG_LIST).map(Formatter::exprChildren).orElse(List.of());
        SyntaxNode argList = n.child(SyntaxKind.ARG_LIST).orElse(null);
        Place run = places.under(at, SyntaxKind.ARG_LIST, Opening.NONE, Written.of(argList));
        if (args.isEmpty()) {
            List<Member> only = withEndComments(run, List.of());
            return TokenDoc.at(run, delimited(run, SyntaxKind.ARG_LIST, LPAREN, only, RPAREN));
        }
        List<Member> argDocs = new ArrayList<>();
        for (SyntaxNode a : args) {
            Place arg = memberPlace(run, a);
            argDocs.add(member(arg, expr(a, arg)));
        }
        return TokenDoc.at(run, delimited(run, SyntaxKind.ARG_LIST, LPAREN,
                withEndComments(run, argDocs), RPAREN));
    }

    private TokenDoc binary(SyntaxNode n, Place at) {
        List<TokenDoc> segs = new ArrayList<>();
        TokenDoc head = collectChain(n, at, ladderLevel(operatorKind(n)), segs, true);
        return TokenDoc.node(n.kind(), chained(new Member(head, TokenDoc.NIL), segs));
    }

    /**
     * Flattens a run of operators the parser reads at one level of its precedence ladder, so that
     * what the source wrote as one run is laid out as one run: {@code a + b * c + d} breaks into
     * {@code a}, {@code + b * c} and {@code + d}, the three parts the {@code +} level has.
     *
     * <p>Only the left spine, and only within the level. The right operand of a left-associative
     * level is never that level's own operator unless the source parenthesised it, and a
     * parenthesised operand is a structure its author wrote — descending into either would show a
     * run the tree does not have.
     */
    private TokenDoc collectChain(SyntaxNode n, Place at, int level, List<TokenDoc> segs,
            boolean last) {
        List<SyntaxNode> ops = exprChildren(n);
        SyntaxNode left = ops.get(0);
        TokenDoc head;
        Place opensAt;
        if (left.kind() == SyntaxKind.BINARY_EXPR && ladderLevel(operatorKind(left)) == level) {
            head = collectChain(left, at, level, segs, false);
            opensAt = places.spanOf(left).from();
        } else {
            Place ofTheLeft = headPlace(at, left);
            opensAt = ofTheLeft;
            head = TokenDoc.at(ofTheLeft, concat(expr(left, ofTheLeft), TokenDoc.endsTheLineOf(ofTheLeft)));
        }
        SyntaxNode right = ops.get(1);
        Place ofTheRight = segmentPlace(at, token(operatorToken(n)), right);
        segs.add(last ? lastOfARun(ofTheRight, expr(right, ofTheRight))
                : segment(ofTheRight, right, expr(right, ofTheRight)));
        // What the parser read as one operand of the level above is written here as a run of
        // sibling places. It opens where the run's head is written and ends at the segment its own
        // right operand is written at, and the construction is the only thing that knows both.
        places.spanning(n, opensAt, ofTheRight);
        return head;
    }

    /**
     * Which rung of {@link CstParser}'s precedence ladder an operator is read on. Operators on one
     * rung are read by one loop and chain; the comparisons are read by a single test and never
     * chain, so their runs are one operator long and flattening them is a no-op.
     *
     * <p>An operator missing from here is refused rather than given a rung of its own: sharing one
     * would lay out a run the parser does not read as a run, which is the reading a reader takes
     * from the layout and cannot check.
     */
    private static int ladderLevel(SyntaxKind k) {
        return switch (k) {
            case OR -> 1;
            case AND -> 2;
            case EQ, NE, LT, LE, GT, GE -> 3;
            case PLUS, MINUS, PLUSPLUS -> 4;
            case STAR, SLASH -> 5;
            default -> throw new IllegalStateException("no precedence rung for " + k);
        };
    }

    private TokenDoc pipe(SyntaxNode n, Place at) {
        List<TokenDoc> stages = new ArrayList<>();
        TokenDoc head = collectPipe(n, at, stages, true);
        return TokenDoc.node(n.kind(), chained(new Member(head, TokenDoc.NIL), stages));
    }

    /** Flattens a left-nested {@code |>} chain: returns the head doc and fills {@code stages} with each
     * right-hand stage in source order. */
    private TokenDoc collectPipe(SyntaxNode n, Place at, List<TokenDoc> stages, boolean last) {
        List<SyntaxNode> ops = exprChildren(n);
        SyntaxNode left = ops.get(0);
        SyntaxNode right = ops.get(1);
        TokenDoc head;
        Place opensAt;
        if (left.kind() == SyntaxKind.PIPE_EXPR) {
            head = collectPipe(left, at, stages, false);
            opensAt = places.spanOf(left).from();
        } else {
            Place ofTheLeft = headPlace(at, left);
            opensAt = ofTheLeft;
            head = TokenDoc.at(ofTheLeft, concat(expr(left, ofTheLeft), TokenDoc.endsTheLineOf(ofTheLeft)));
        }
        Place ofTheStage = segmentPlace(at, TokenDoc.token(SyntaxKind.VPIPE, "|>"), right);
        stages.add(last ? lastOfARun(ofTheStage, expr(right, ofTheStage))
                : segment(ofTheStage, right, expr(right, ofTheStage)));
        places.spanning(n, opensAt, ofTheStage);
        return head;
    }

    private TokenDoc tuple(SyntaxNode n, Place at) {
        Place run = places.under(at, n.kind(), Opening.NONE, Written.of(n));
        return TokenDoc.at(run,
                delimited(run, SyntaxKind.TUPLE_EXPR, LPAREN, exprDocs(n, run), RPAREN));
    }

    private TokenDoc list(SyntaxNode n, Place at) {
        Place run = places.under(at, n.kind(), Opening.NONE, Written.of(n));
        return TokenDoc.at(run,
                delimited(run, SyntaxKind.LIST_EXPR, LBRACKET, exprDocs(n, run), RBRACKET));
    }

    private TokenDoc listComp(SyntaxNode n, Place at) {
        Place run = places.under(at, n.kind(), Opening.NONE, Written.of(n));
        List<SyntaxNode> children = exprChildren(n);
        // The element is written after the `[` on its line, so nothing opens one for it; each guard
        // after the `|` has a line the layout may open.
        List<Member> parts = new ArrayList<>();
        for (SyntaxNode c : children) {
            Place p = parts.isEmpty()
                    ? places.under(run, c.kind(), Opening.NONE, Written.of(c))
                    : memberPlace(run, c);
            parts.add(member(p, expr(c, p)));
        }
        List<Member> all = withEndComments(run, parts);
        Member element = all.get(0);
        List<Member> guards = all.subList(1, all.size());
        // The `|` is the comprehension's and it is on the element's line, so it goes before the
        // comment that ends that line — as a comma does for a member of a list.
        return TokenDoc.at(run, TokenDoc.node(n.kind(),
                group(concat(LBRACKET, GAP, element.doc(), GAP, PIPE, element.trailing(),
                        nest(INDENT, separated(guards)), SOFT_GAP, RBRACKET))));
    }

    /**
     * Three places, and the kinds do not tell them apart: written {@code if flag then p else q} all
     * three are a {@code VAR_EXPR}. The condition stands between {@code if} and {@code then} with no
     * boundary the layout can break, so it opens no line; the two branches each open one.
     */
    private TokenDoc ifExpr(SyntaxNode n, Place at) {
        List<SyntaxNode> parts = exprChildren(n);
        Place condition = places.under(at, parts.get(0).kind(), Opening.NONE,
                Written.of(parts.get(0)));
        Place then = places.under(at, parts.get(1).kind(), Opening.breaks(TokenDoc.Break.MAY),
                Written.of(parts.get(1)));
        TokenDoc departures = elseArms(n, at);
        return TokenDoc.node(n.kind(), group(concat(TokenDoc.token(SyntaxKind.IF_KW, "if"), GAP,
                TokenDoc.at(condition, expr(parts.get(0), condition)),
                attemptBinder(n), GAP, TokenDoc.token(SyntaxKind.THEN_KW, "then"),
                nest(INDENT, TokenDoc.at(then, concat(expr(parts.get(1), then), TokenDoc.endsTheLineOf(then)))),
                SOFT_GAP, TokenDoc.token(SyntaxKind.ELSE_KW, "else"),
                departures instanceof TokenDoc.Nil
                        ? otherwise(at, parts.get(2))
                        : departures)));
    }

    private TokenDoc otherwise(Place at, SyntaxNode part) {
        Place branch = places.under(at, part.kind(), Opening.breaks(TokenDoc.Break.MAY),
                Written.of(part));
        return nest(INDENT, TokenDoc.at(branch, concat(expr(part, branch), TokenDoc.endsTheLineOf(branch))));
    }

    /** An attempt's per-clause departures, one to a line under the {@code else}, or nothing where the
     * {@code else} took one expression. */
    private TokenDoc elseArms(SyntaxNode n, Place at) {
        var arms = n.child(SyntaxKind.ELSE_ARMS);
        if (arms.isEmpty()) {
            return TokenDoc.NIL;
        }
        Place run = places.under(at, arms.get().kind(), Opening.NONE, Written.of(arms.get()));
        List<TokenDoc> lines = new ArrayList<>();
        lines.add(headerLine(run, n.token(SyntaxKind.ELSE_KW)));
        for (SyntaxNode arm : childNodes(arms.get(), SyntaxKind.ELSE_ARM)) {
            Place place = places.under(run, arm.kind(),
                    Opening.forced(Obligation.MEMBERS_TAKE_LINES_OF_THEIR_OWN),
                    Written.of(arm));
            lines.add(TokenDoc.at(place, concat(TokenDoc.node(arm.kind(),
                            concat(PIPE, GAP, ident(firstIdent(arm)), GAP, ARROW, GAP,
                                    childAt(place, onlyExpr(arm), Opening.NONE))),
                            TokenDoc.endsTheLineOf(place))));
        }
        lines.add(TokenDoc.carries(run, Carrier.AT_END));
        return TokenDoc.at(run, TokenDoc.node(arms.get().kind(), nest(INDENT, concat(lines))));
    }

    /** The {@code as x} of an attempted construction, or nothing where none was written. It sits
     * between the construction and the {@code then}/{@code else} that follows it. */
    private TokenDoc attemptBinder(SyntaxNode n) {
        boolean afterAs = false;
        for (SyntaxElement e : meaningful(n)) {
            if (!(e instanceof SyntaxToken t)) continue;
            if (t.kind() == SyntaxKind.AS_KW) {
                afterAs = true;
            } else if (afterAs && t.kind() == SyntaxKind.IDENT) {
                return concat(GAP, TokenDoc.token(SyntaxKind.AS_KW, "as"), GAP, token(t));
            }
        }
        return TokenDoc.NIL;
    }

    private TokenDoc matchExpr(SyntaxNode n, Place at) {
        SyntaxNode scrutinee = exprChildren(n).get(0);
        Place ofTheScrutinee = places.under(at, scrutinee.kind(), Opening.NONE,
                Written.of(scrutinee));
        List<TokenDoc> cases = new ArrayList<>();
        for (SyntaxNode c : childNodes(n, SyntaxKind.MATCH_CASE)) {
            Place place = places.under(at, c.kind(),
                    Opening.forced(Obligation.MEMBERS_TAKE_LINES_OF_THEIR_OWN),
                    Written.of(c));
            cases.add(TokenDoc.at(place, concat(matchCase(c, place), TokenDoc.endsTheLineOf(place))));
        }
        cases.add(TokenDoc.carries(at, Carrier.AT_END));
        return TokenDoc.node(n.kind(), concat(TokenDoc.token(SyntaxKind.MATCH_KW, "match"), GAP,
                TokenDoc.at(ofTheScrutinee, expr(scrutinee, ofTheScrutinee)),
                GAP, TokenDoc.token(SyntaxKind.WITH_KW, "with"),
                headerLine(at, n.token(SyntaxKind.WITH_KW)), nest(INDENT, concat(cases))));
    }

    /** The brackets a construct is written between. One place knows which they are. */
    static boolean isOpeningBracket(SyntaxKind k) {
        return k == SyntaxKind.LPAREN || k == SyntaxKind.LBRACKET || k == SyntaxKind.LBRACE;
    }

    static boolean isClosingBracket(SyntaxKind k) {
        return k == SyntaxKind.RPAREN || k == SyntaxKind.RBRACKET || k == SyntaxKind.RBRACE;
    }

    /**
     * One arm. The grammar writes an arm's pattern as a run of tokens rather than as nodes, so this
     * is where those tokens are handed over; what goes between two of them is the rule's answer for
     * the arm, the same way it is for every other construct. It used to be the one place the
     * formatter joined two tokens itself, and it wrote `Some ( x )` where the `let` opening the same
     * value writes `Some(x)`.
     */
    private TokenDoc matchCase(SyntaxNode n, Place at) {
        List<TokenDoc> pattern = new ArrayList<>();
        SyntaxNode body = null;
        SyntaxToken patternEnd = null;
        boolean afterArrow = false;
        for (SyntaxElement e : meaningful(n)) {
            if (afterArrow) {
                body = (SyntaxNode) e;
                break;
            }
            if (e instanceof SyntaxToken t) {
                if (t.kind() == SyntaxKind.ARROW) {
                    afterArrow = true;
                    continue;
                }
                if (patternEnd != null) {
                    pattern.add(GAP);
                }
                pattern.add(TokenDoc.token(t.kind(), t.text()));
                patternEnd = t;
            }
        }
        TokenDoc written = concat(pattern);
        Place ofThePattern = places.under(at, patternEnd == null ? null : patternEnd.kind(),
                Opening.NONE, patternEnd == null ? Written.NONE
                        : new Written[] {new Written.Run(patternEnd.start(), patternEnd.end())});
        Member headMember = patternEnd == null
                ? new Member(TokenDoc.at(ofThePattern, written), TokenDoc.NIL)
                : head(ofThePattern, written);
        Place ofTheBody = segmentPlace(at, ARROW, body);
        return concat(PIPE, GAP, TokenDoc.node(n.kind(), chained(headMember,
                List.of(segment(ofTheBody, body, expr(body, ofTheBody))))));
    }

    private TokenDoc lambda(SyntaxNode n, Place at) {
        Place run = places.under(at, n.kind(), Opening.NONE, Written.of(n));
        // `x -> e` keeps its bare parameter, and that one is not written between brackets: nothing
        // opens a line for it, so its place brings no boundary of its own.
        boolean bracketed = n.token(SyntaxKind.LPAREN).isPresent();
        List<Member> params = new ArrayList<>();
        for (SyntaxNode c : n.childNodes()) {
            if (isPatternNode(c.kind())) {
                Place param = bracketed ? memberPlace(run, c)
                        : places.under(run, c.kind(), Opening.NONE, Written.of(c));
                params.add(member(param, pattern(c, param)));
            }
        }
        // `x -> e` keeps its bare parameter; anything parenthesised was written that way
        TokenDoc paramsDoc = n.token(SyntaxKind.LPAREN).isPresent()
                ? delimited(run, SyntaxKind.LAMBDA_EXPR, LPAREN, withEndComments(run, params), RPAREN)
                : concat(params.get(0).doc(), params.get(0).trailing());
        SyntaxNode body = lastExprChild(n);
        Place ofTheBody = places.under(at, body.kind(), Opening.NONE, Written.of(body));
        return TokenDoc.node(n.kind(), concat(TokenDoc.at(run, paramsDoc), GAP, ARROW, GAP,
                TokenDoc.at(ofTheBody, expr(body, ofTheBody))));
    }

    private TokenDoc newData(SyntaxNode n, Place at) {
        String typeName = firstIdent(n);
        Place run = places.under(at, n.kind(), Opening.NONE, Written.of(n));
        List<Member> members = new ArrayList<>();
        for (SyntaxNode c : n.childNodes()) {
            if (c.kind() != SyntaxKind.SPREAD_MEMBER && c.kind() != SyntaxKind.FIELD_INIT) {
                continue;
            }
            Place place = memberPlace(run, c);
            TokenDoc written;
            if (c.kind() == SyntaxKind.SPREAD_MEMBER) {
                // `...c` or `...c.address`
                written = TokenDoc.node(c.kind(), concat(
                        TokenDoc.token(SyntaxKind.SPREAD, "..."), GAP,
                        dottedName(idents(c))));
            } else {
                var value = firstExprChildOpt(c);
                written = TokenDoc.node(c.kind(),
                        value.map(v -> concat(ident(firstIdent(c)), GAP, ASSIGN, GAP,
                                        childAt(place, v, Opening.NONE)))
                                .orElse(ident(firstIdent(c))));   // shorthand `field`
            }
            // A member's leading comments come before it, each on its own line. The boundary after
            // one forces the enclosing group to break, which is what a literal with a comment in it
            // wants anyway: a `//` on a line the group had collapsed would swallow the rest of it.
            members.add(member(place, written));
        }
        return TokenDoc.node(n.kind(), concat(ident(typeName), GAP, TokenDoc.at(run,
                delimited(run, SyntaxKind.NEW_DATA_EXPR, LBRACE, withEndComments(run, members),
                        RBRACE))));
    }

    private TokenDoc block(SyntaxNode n, Place at) {
        Place run = places.under(at, n.kind(), Opening.NONE, Written.of(n));
        List<TokenDoc> lines = new ArrayList<>();
        List<SyntaxNode> steps = n.childNodes();
        // The paragraph breaks between the steps are the author's, the same as between two
        // top-level items, and for the same reason: what a block has for structure is where its
        // steps are grouped, and a body written as three paragraphs says something a body written
        // as nine lines does not.
        java.util.Set<SyntaxNode> paragraph = openedByABlankLine(n, steps);
        for (SyntaxNode c : steps) {
            if (!lines.isEmpty() && paragraph.contains(c)) {
                lines.add(TokenDoc.blank(Obligation.MEMBERS_TAKE_LINES_OF_THEIR_OWN));
            }
            // A statement inside a block carries its leading comments the same way a top-level item
            // does. Walking only the child nodes dropped them, so a comment explaining a step was
            // lost on the first format.
            Place place = places.under(run, c.kind(),
                    Opening.forced(Obligation.MEMBERS_TAKE_LINES_OF_THEIR_OWN),
                    Written.of(c));
            TokenDoc d = switch (c.kind()) {
                case LET_STMT -> TokenDoc.node(c.kind(), concat(TokenDoc.token(SyntaxKind.LET_KW, "let"), GAP, ident(firstIdent(c)),
                        writtenType(c, place), GAP, ASSIGN, GAP,
                        childAt(place, onlyExpr(c), Opening.NONE)));
                case LET_DESTRUCTURE -> TokenDoc.node(c.kind(), concat(TokenDoc.token(SyntaxKind.LET_KW, "let"), GAP,
                        pattern(patternChild(c), place), GAP, ASSIGN, GAP,
                        childAt(place, onlyExpr(c), Opening.NONE)));
                case GUARD_STMT -> guardStmt(c, place);
                default -> expr(c, place);   // the result expression
            };
            lines.add(TokenDoc.at(place, concat(d, TokenDoc.endsTheLineOf(place))));
        }
        lines.add(TokenDoc.carries(run, Carrier.AT_END));
        return TokenDoc.at(run, TokenDoc.node(n.kind(),
                concat(LBRACE, headerLine(run, n.token(SyntaxKind.LBRACE)),
                        nest(INDENT, concat(lines)),
                        TokenDoc.forced(Obligation.A_BRACKET_TAKES_A_LINE_OF_ITS_OWN),
                        RBRACE)));
    }

    /** A binding pattern, written back as it was: a name, a tuple, a newtype opened by its
     * constructor, or a record's fields. */
    private TokenDoc pattern(SyntaxNode n, Place at) {
        places.within(n, at);
        return switch (n.kind()) {
            case PATTERN_NAME -> ident(firstIdent(n));
            case PATTERN_TUPLE -> {
                Place run = places.under(at, n.kind(), Opening.NONE, Written.of(n));
                List<Member> elems = new ArrayList<>();
                for (SyntaxNode c : n.childNodes()) {
                    if (isPatternNode(c.kind())) {
                        Place elem = memberPlace(run, c);
                        elems.add(member(elem, pattern(c, elem)));
                    }
                }
                yield TokenDoc.at(run, delimited(run, SyntaxKind.PATTERN_TUPLE, LPAREN,
                        withEndComments(run, elems), RPAREN));
            }
            case PATTERN_CTOR -> {
                SyntaxNode inner = patternChild(n);
                Place ofTheInner = places.under(at, inner.kind(), Opening.NONE, Written.of(inner));
                yield TokenDoc.node(n.kind(), concat(qualifiedName(n, at), GAP, LPAREN, GAP,
                        TokenDoc.at(ofTheInner, pattern(inner, ofTheInner)), GAP, RPAREN));
            }
            case PATTERN_RECORD -> {
                Place run = places.under(at, n.kind(), Opening.NONE, Written.of(n));
                List<Member> fields = new ArrayList<>();
                for (SyntaxNode f : n.childNodes()) {
                    if (f.kind() != SyntaxKind.PATTERN_FIELD) {
                        continue;
                    }
                    List<SyntaxToken> names = idents(f);
                    fields.add(member(memberPlace(run, f), TokenDoc.node(f.kind(),
                            names.size() > 1
                                    ? concat(token(names.get(0)), GAP, ASSIGN, GAP,
                                            token(names.get(1)))
                                    : token(names.get(0)))));
                }
                yield TokenDoc.at(run, delimited(run, SyntaxKind.PATTERN_RECORD, LBRACE,
                        withEndComments(run, fields), RBRACE));
            }
            default -> ident(firstIdent(n));
        };
    }

    private static boolean isPatternNode(SyntaxKind k) {
        return k == SyntaxKind.PATTERN_NAME || k == SyntaxKind.PATTERN_TUPLE
                || k == SyntaxKind.PATTERN_CTOR || k == SyntaxKind.PATTERN_RECORD;
    }

    private SyntaxNode patternChild(SyntaxNode n) {
        SyntaxNode c = optionalPatternChild(n);
        if (c == null) {
            throw new IllegalStateException("no pattern in " + n.kind());
        }
        return c;
    }

    private SyntaxNode optionalPatternChild(SyntaxNode n) {
        for (SyntaxNode c : n.childNodes()) {
            if (isPatternNode(c.kind())) {
                return c;
            }
        }
        return null;
    }

    private TokenDoc guardStmt(SyntaxNode n, Place at) {
        List<SyntaxNode> exprs = exprChildren(n);
        Place ofTheTest = places.under(at, exprs.get(0).kind(), Opening.NONE,
                Written.of(exprs.get(0)));
        TokenDoc departures = elseArms(n, at);
        if (!(departures instanceof TokenDoc.Nil)) {
            return TokenDoc.node(n.kind(), concat(TokenDoc.token(SyntaxKind.GUARD_KW, "guard"), GAP,
                    TokenDoc.at(ofTheTest, expr(exprs.get(0), ofTheTest)),
                    attemptBinder(n), GAP, TokenDoc.token(SyntaxKind.ELSE_KW, "else"), departures));
        }
        // The `else` opens the departure's line, so it is the place's opener: a guard that does not
        // fit gives way there before its condition gives way anywhere. The two are one group and
        // this is the order it takes — the departure is what the guard is for, and left to the
        // condition's own breaking it ends up at the end of whichever continuation line the
        // condition happened to finish on. The condition's group is measured afterwards, on the
        // line the `guard` left it, so it stays flat where it fits there.
        Place ofTheDeparture = places.under(at, exprs.get(1).kind(),
                Opening.breaks(TokenDoc.Break.MAY,
                        concat(TokenDoc.token(SyntaxKind.ELSE_KW, "else"), GAP)),
                Written.of(exprs.get(1)));
        return TokenDoc.node(n.kind(), group(concat(
                TokenDoc.token(SyntaxKind.GUARD_KW, "guard"), GAP,
                TokenDoc.at(ofTheTest, expr(exprs.get(0), ofTheTest)),
                attemptBinder(n),
                nest(INDENT, TokenDoc.at(ofTheDeparture, expr(exprs.get(1), ofTheDeparture))))));
    }

    // --- comments ---
    //
    // Where a comment goes is decided once, for the file, and in two steps. What it was written
    // about is read off the token stream: a comment with a newline between it and the code before it
    // was written above the line that follows, and one without was written at the end of the line
    // before. That is a fact about the source and it is in the tree, since whitespace is kept.
    //
    // Where it is written back is a second question, and the answer is not always the construct it
    // was written about. Only a construct the layout gives a line of its own can carry a comment: put
    // anywhere else, the rest of that line would be written inside it, which changes what the code
    // says rather than how it reads. So the anchor moves up to the nearest construct that has a
    // line, and a comment written after the condition of an `if` is written at the end of the
    // declaration that holds it. It travels further than it was written; the alternative is dropping
    // it.

    /** Where the comments of a file go, decided before any of it is written. */
    private record Attachments(
            // Above the line the node opens.
            Map<SyntaxNode, List<SyntaxToken>> above,
            // At the end of the line the node ends.
            Map<SyntaxNode, List<SyntaxToken>> after,
            // On lines of its own below the line the node ends, with a member still to come.
            Map<SyntaxNode, List<SyntaxToken>> below,
            // Inside the node, under its last member and before it closes.
            Map<SyntaxNode, List<SyntaxToken>> atEnd,
            // Against a name rather than a node — the identifiers a sum's case or a clause's
            // member is written as, and a token in the middle of a construct, which is a line of
            // the canonical form without being a construct of the source.
            Map<Written, OnAName> aboveToken,
            Map<Written, OnAName> afterToken) {

        /**
         * The comments held against one name, and the construct that name is written in.
         *
         * <p>Both are read here, where the comment's own position is read. Leaving the second to be
         * worked out later would have the walk over the places begin by walking the source tree,
         * which is what recording the places is here to stop.
         */
        record OnAName(SyntaxNode inside, List<SyntaxToken> comments) {}

        static Attachments empty() {
            return new Attachments(new IdentityHashMap<>(), new IdentityHashMap<>(),
                    new IdentityHashMap<>(), new IdentityHashMap<>(),
                    new java.util.HashMap<>(), new java.util.HashMap<>());
        }
    }

    /** Files {@code comment} against the name {@code t} is part of, written inside {@code inside}. */
    private static void onAName(Map<Written, Attachments.OnAName> to, SyntaxToken t,
            SyntaxNode inside, SyntaxToken comment) {
        to.computeIfAbsent(new Written.Run(nameStart(t), nameEnd(t)),
                        _ -> new Attachments.OnAName(inside, new ArrayList<>()))
                .comments().add(comment);
    }

    /**
     * Every comment of {@code file}, against where it will be written. One pass over the tokens: a
     * comment is read as written at the end of the code before it, above the code that follows it,
     * or below the code before it, and then given to whichever construct has the line that code is
     * on.
     *
     * <p>Which of the three is read from the line breaks around the comment, the blank line
     * included. A comment on the code's own line was written after that code. A comment on a line
     * of its own belongs to what follows it — unless a blank line stands under it and none stands
     * over it, because then the blank line is what separates the comment from what follows and
     * there is nothing between the comment and the code above. Counting only whether a line ended
     * left that last case unreadable, and a note written under a declaration came back describing
     * the next one.
     */
    private static Attachments attach(SyntaxNode file) {
        Attachments out = Attachments.empty();
        List<SyntaxToken> all = tokens(file);
        List<SyntaxToken> code = new ArrayList<>();   // the tokens that were not trivia
        int breaks = 1;                               // nothing precedes the first token of a file
        for (int i = 0; i < all.size(); i++) {
            SyntaxToken t = all.get(i);
            if (t.kind() == SyntaxKind.WHITESPACE) {
                breaks += newlines(t);
            } else if (t.kind() == SyntaxKind.LINE_COMMENT) {
                if (breaks == 0) {
                    after(out, lastCode(code), t);
                    breaks = 1;                       // a line comment runs to the end of its line
                    continue;
                }
                // A run of comment lines with no blank line in it is one comment as far as this
                // question goes. Asked line by line, a run with a blank line under it would have
                // its last line answered one way and every line above it the other.
                int last = runEnds(all, i);
                SyntaxNode under = breaks >= 2 || !blankUnder(all, last)
                        ? null
                        : belowAnchor(lastCode(code));
                Follows follows = nextCode(all, last);
                for (int j = i; j <= last; j++) {
                    SyntaxToken c = all.get(j);
                    if (c.kind() != SyntaxKind.LINE_COMMENT) {
                        continue;
                    }
                    if (under != null) {
                        add(out.below(), under, c);
                    } else {
                        above(out, follows.code(), c, file, follows.pastAConnector());
                    }
                }
                i = last;
                breaks = 1;
            } else {
                code.add(t);
                breaks = 0;
            }
        }
        return out;
    }

    /** The line breaks {@code whitespace} holds. Two of them are a blank line. */
    private static int newlines(SyntaxToken whitespace) {
        int n = 0;
        String text = whitespace.text();
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                n++;
            }
        }
        return n;
    }

    /** The last comment of the run beginning at {@code i}: the comment lines following one another
     *  with no blank line and no code between them. */
    private static int runEnds(List<SyntaxToken> all, int i) {
        int last = i;
        int breaks = 0;
        for (int j = i + 1; j < all.size(); j++) {
            SyntaxToken t = all.get(j);
            if (t.kind() == SyntaxKind.WHITESPACE) {
                breaks += newlines(t);
            } else if (t.kind() == SyntaxKind.LINE_COMMENT && breaks < 2) {
                last = j;
                breaks = 0;
            } else {
                break;
            }
        }
        return last;
    }

    /** Whether a blank line stands between the comment at {@code last} and whatever follows it. The
     *  end of the file is not one: nothing follows there for a blank line to separate it from, and
     *  a comment with nothing after it is the file's rather than the last declaration's. */
    private static boolean blankUnder(List<SyntaxToken> all, int last) {
        int breaks = 0;
        for (int j = last + 1; j < all.size(); j++) {
            SyntaxToken t = all.get(j);
            if (t.kind() != SyntaxKind.WHITESPACE) {
                return breaks >= 2 && t.kind() != SyntaxKind.EOF;
            }
            breaks += newlines(t);
        }
        return false;
    }

    /**
     * What a comment written under {@code written}, and cut off from what follows by a blank line,
     * was written about: the outermost construct that ends there.
     *
     * <p>Null where the construct ending there is not a member of something that writes its members
     * on lines of their own. A comment written below one that is not — below the type of a
     * {@code data} whose {@code invariant} is still to come — is in the middle of a construct, so the
     * blank line under it separates it from nothing that construct has finished saying, and it is
     * read as written above what follows, which is what it was before any blank line was read.
     *
     * <p>Asked of the member rather than of the place it will be written at, because the place is
     * found by widening to the nearest one that can carry a comment below it, and widening from the
     * middle of a construct reaches the whole construct: a comment under a declaration's first line
     * would come back under its last.
     */
    private static SyntaxNode belowAnchor(SyntaxToken written) {
        if (written == null) {
            return null;                              // no code above it: it is above what follows
        }
        SyntaxToken code = lastOfName(written);
        // A member with something still to come after it owns its own line, and what is written
        // below it is below that member rather than below the construct. The last member of a run
        // ends where the run does, so what is below it is below the whole thing — the same reading
        // {@link #after} takes of a comment at the end of that line.
        if (isBareMember(code) && nameEnd(code) != code.parent().end()) {
            return null;
        }
        if (isChainHead(code)) {
            return null;
        }
        SyntaxNode ends = endingAt(code);
        if (ends.end() != code.end()) {
            return null;
        }
        SyntaxNode holder = ends.parent();
        return holder != null && writesEachMemberOnItsOwnLine(holder.kind()) ? ends : null;
    }

    /** Whether a construct of this kind writes each of its members beginning a line of its own, so
     *  that a member has a line under it that is the member's and not the construct's. These are
     *  the two that separate members by a break alone; everywhere else a member is written into a
     *  run its holder wrote the connectors of, and what is under it is under the run. */
    private static boolean writesEachMemberOnItsOwnLine(SyntaxKind k) {
        return k == SyntaxKind.SOURCE_FILE || k == SyntaxKind.BLOCK_EXPR;
    }

    /**
     * What separates one member of a construct from the next. It belongs to the construct rather
     * than to either member, so it is not what a comment was written about in either direction: a
     * comment before a {@code |} was written about the case after it, and one after a {@code ,} about
     * the member the comma closed.
     */
    private static boolean separates(SyntaxToken t) {
        return switch (t.kind()) {
            case COMMA, PIPE, PIPEFWD, VPIPE -> true;
            // An arrow joins two parts of a chain only where a chain is what the layout writes. A
            // function type and a lambda are written as one line with an arrow in it, and a comment
            // before that arrow has no part after it to be about — reading it as a connector there
            // moved the comment on the first formatting and moved it again on the second.
            case ARROW, COLON -> switch (t.parent().kind()) {
                case EXAMPLE_ROW, FAKE_ROW, MATCH_CASE -> true;
                default -> false;
            };
            // A guard's `else` opens the departure's line the way a comma opens a member's, so a
            // comment written above it is about the departure after it. Read as part of the guard
            // instead, the comment came back above the `else` — a position the next formatting reads
            // as a comment about the whole guard, so the first one had already moved it.
            case ELSE_KW -> t.parent().kind() == SyntaxKind.GUARD_STMT
                    && t.parent().child(SyntaxKind.ELSE_ARMS).isEmpty();
            default -> isBinaryOperator(t.kind());
        };
    }

    /** What opens a construct. It is the construct's too, so a comment written above it was written
     * above the first member rather than above the bracket — which is also what keeps the answer
     * the same on a second formatting, when the bracket has moved onto the member's line. */
    private static boolean opens(SyntaxToken t) {
        return isOpeningBracket(t.kind());
    }

    /**
     * Whether {@code n} is what its brackets hold and nothing more.
     *
     * <p>Only then does the opening bracket share its line with the first member: a lambda writes
     * {@code -> x} after its parameters, so a comment above the {@code (} of {@code (y) -> x} is
     * above the lambda and not above {@code y}. Read the other way, the comment came back between
     * the brackets and the parameter was pushed onto a line of its own.
     */
    private static boolean isBracketed(SyntaxNode n) {
        SyntaxToken last = lastCodeTokenOf(n);
        return last != null && closes(last);
    }

    /**
     * The code the comment at {@code i} was written above, or null where the file ends first, and
     * whether a connector stood between the two.
     *
     * <p>A connector joins what follows it to what came before, and the canonical form writes it at
     * the head of the line what follows is on. So a comment written above it is above the whole of
     * what follows and not above whatever that opens with — read the other way, a comment above an
     * example row's {@code : (2)} came back inside the brackets.
     */
    private record Follows(SyntaxToken code, boolean pastAConnector) {}

    private static Follows nextCode(List<SyntaxToken> all, int i) {
        boolean past = false;
        for (int j = i + 1; j < all.size(); j++) {
            SyntaxToken t = all.get(j);
            if (t.isTrivia()) {
                continue;
            }
            // what closes a construct is asked about before what separates its members, because a
            // type's `>` is both: it is the angle bracket here and the comparison everywhere else
            if (separates(t) && !closes(t)) {
                past = true;
                continue;
            }
            return new Follows(t.kind() == SyntaxKind.EOF ? null : t, past);
        }
        return new Follows(null, past);
    }

    /** The code a comment was written after. */
    private static SyntaxToken lastCode(List<SyntaxToken> code) {
        for (int i = code.size() - 1; i >= 0; i--) {
            if (!separates(code.get(i)) || closes(code.get(i))) {
                return code.get(i);
            }
        }
        return null;
    }

    /**
     * What a comment written above {@code next} was written about: the outermost construct that
     * begins there. This is what the comment describes, and it is decided without asking whether
     * that construct has anywhere to put it — that is the next question, and answering the two
     * together is what turned a comment about a case into a comment about the declaration.
     */
    private static void above(Attachments out, SyntaxToken next, SyntaxToken comment, SyntaxNode file,
            boolean pastAConnector) {
        if (next == null) {
            add(out.atEnd(), file, comment);          // nothing follows: it closes the file
            return;
        }
        // What opens a construct is the construct's, and it shares a line with the first member, so
        // a comment above the bracket was written above that member and not above the bracket —
        // unless something larger begins at that bracket, as an example row does at its `(`, in
        // which case the comment is above that and not above what it opens with. Nor where a
        // connector stands in front of it: the connector opens the line, and what the comment is
        // above is everything written on that line.
        if (!pastAConnector && opens(next) && beginningAt(next) == next.parent()
                && isBracketed(next.parent())) {
            SyntaxElement first = firstMemberOf(next.parent());
            if (first instanceof SyntaxNode node) {
                add(out.above(), node, comment);
                return;
            }
            if (first instanceof SyntaxToken token) {
                onAName(out.aboveToken(), token, beginningAt(token), comment);
                return;
            }
        }
        if (isBareMember(next)) {
            // A clause keyword opens the line its first name is on, the way a bracket does, so a
            // comment above that name is above the clause rather than between the two.
            SyntaxElement first = firstMemberOf(next.parent());
            boolean opensTheClause = first instanceof SyntaxToken t && t.start() == nameStart(next)
                    && (next.parent().kind() == SyntaxKind.CONSTRUCTS_CLAUSE
                            || next.parent().kind() == SyntaxKind.DEPENDS_CLAUSE);
            if (opensTheClause) {
                add(out.above(), next.parent(), comment);
            } else {
                onAName(out.aboveToken(), next, beginningAt(next), comment);
            }
            return;
        }
        if (closes(next)) {
            add(out.atEnd(), next.parent(), comment);
            return;
        }
        add(out.above(), beginningAt(next), comment);
    }

    /** What a comment written after {@code code} was written about: the outermost construct that
     * ends there. */
    private static void after(Attachments out, SyntaxToken written, SyntaxToken comment) {
        if (written == null) {
            return;                                   // a comment with no code before it is above
        }
        // A name written as several identifiers is one thing, so a comment written inside it was
        // written after all of it. Read from the identifier it happens to follow, the rest of the
        // name is still to come and the comment looks like one written in the middle of a construct.
        SyntaxToken code = lastOfName(written);
        // A bare member is only its own line's owner while something follows it. The last case of a
        // sum ends where the declaration does, and what ends on that line is the declaration.
        // Measured from the end of the whole name, not from the identifier the comment happens to
        // follow: a comment inside `other.mod.Thing` and one after it are about the same member, and
        // the last member of a run ends where the run does, which is the run's line and not its own.
        if (isBareMember(code) && nameEnd(code) != code.parent().end()) {
            onAName(out.afterToken(), code, endingAt(code), comment);
            return;
        }
        if (isChainHead(code)) {
            onAName(out.afterToken(), code, endingAt(code), comment);
            return;
        }
        // The outermost construct ending where the comment is. Outermost because
        // `data D = A | B // c` is about the declaration and not about `B`. Whether that construct
        // is one the canonical form gives a line to is the next question and not this one's:
        // answering the two together is what made a comment about a member come back describing
        // whatever held it.
        SyntaxNode ends = endingAt(code);
        if (ends.end() != code.end()) {
            // Nothing ends here, so the comment was written in the middle of a construct — after a
            // `data D =`, a `match … with`, a `{`. What it ends is the line that token is on, so it
            // is held against the token rather than against the construct the token is inside.
            onAName(out.afterToken(), code, ends, comment);
            return;
        }
        add(out.after(), ends, comment);
    }

    private static SyntaxToken lastCodeTokenOf(SyntaxNode n) {
        SyntaxToken last = null;
        for (SyntaxToken t : tokens(n)) {
            if (!t.isTrivia()) {
                last = t;
            }
        }
        return last;
    }

    /** The outermost construct beginning at {@code t}. */
    private static SyntaxNode beginningAt(SyntaxToken t) {
        SyntaxNode node = t.parent();
        while (node.parent() != null && node.parent().parent() != null
                && firstCodeOffset(node.parent()) == t.start()) {
            node = node.parent();
        }
        return node;
    }

    /** The outermost construct ending at {@code t}. */
    private static SyntaxNode endingAt(SyntaxToken t) {
        SyntaxNode node = t.parent();
        while (node.parent() != null && node.parent().parent() != null
                && node.parent().end() == t.end()) {
            node = node.parent();
        }
        return node;
    }

    /** Where {@code n}'s own text begins, past whatever trivia the parser put in front of it. */
    private static int firstCodeOffset(SyntaxNode n) {
        for (SyntaxToken t : tokens(n)) {
            if (!t.isTrivia()) {
                return t.start();
            }
        }
        return n.start();
    }

    /**
     * A qualified name is one member written as several identifiers, so a comment anywhere in it is
     * about the whole name. These answer where that name begins and ends, so the two ends of the
     * run agree on which member they are, however deep into it the comment was written.
     */
    private static int nameStart(SyntaxToken ident) {
        SyntaxToken at = ident;
        for (SyntaxToken before = previousOfName(at); before != null; before = previousOfName(at)) {
            at = before;
        }
        return at.start();
    }

    private static int nameEnd(SyntaxToken ident) {
        return lastOfName(ident).end();
    }

    /** The last identifier of the name {@code ident} is part of, which is {@code ident} itself
     *  wherever the name is one identifier. */
    private static SyntaxToken lastOfName(SyntaxToken ident) {
        SyntaxToken at = ident;
        for (SyntaxToken after = nextOfName(at); after != null; after = nextOfName(at)) {
            at = after;
        }
        return at;
    }

    private static SyntaxToken previousOfName(SyntaxToken ident) {
        List<SyntaxToken> siblings = codeTokensOf(ident.parent());
        int i = indexOf(siblings, ident);
        return i >= 2 && siblings.get(i - 1).kind() == SyntaxKind.DOT ? siblings.get(i - 2) : null;
    }

    private static SyntaxToken nextOfName(SyntaxToken ident) {
        List<SyntaxToken> siblings = codeTokensOf(ident.parent());
        int i = indexOf(siblings, ident);
        return i >= 0 && i + 2 < siblings.size() && siblings.get(i + 1).kind() == SyntaxKind.DOT
                ? siblings.get(i + 2) : null;
    }

    private static List<SyntaxToken> codeTokensOf(SyntaxNode n) {
        List<SyntaxToken> out = new ArrayList<>();
        for (SyntaxElement e : n.children()) {
            if (e instanceof SyntaxToken t && !t.isTrivia()) {
                out.add(t);
            }
        }
        return out;
    }

    private static int indexOf(List<SyntaxToken> tokens, SyntaxToken t) {
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).start() == t.start()) {
                return i;
            }
        }
        return -1;
    }

    private static <K> void add(Map<K, List<SyntaxToken>> to, K key, SyntaxToken c) {
        to.computeIfAbsent(key, _ -> new ArrayList<>()).add(c);
    }

    /** The first of {@code container}'s members, which is what shares the line its opener starts.
     * Nothing where it has no members: an empty construct's brackets open and close one line. */
    private static SyntaxElement firstMemberOf(SyntaxNode container) {
        for (SyntaxElement e : container.children()) {
            if (e instanceof SyntaxNode c) {
                return c;
            }
            if (e instanceof SyntaxToken t && isBareMember(t)) {
                return t;
            }
        }
        return null;
    }

    /**
     * A member the grammar writes as a bare identifier rather than as a node — a sum's cases, the
     * names an import or a {@code constructs} clause lists. It has no node to be named by, so it is
     * named by where its identifier is.
     *
     * <p>The identifier a {@code depends on} clause opens with is the {@code on}, which is the
     * keyword and not a name.
     */
    private static boolean isBareMember(SyntaxToken t) {
        if (t.kind() != SyntaxKind.IDENT) {
            return false;
        }
        SyntaxNode parent = t.parent();
        return switch (parent.kind()) {
            case SUM_BODY, NAME_LIST, CONSTRUCTS_CLAUSE -> true;
            case DEPENDS_CLAUSE -> !t.equals(firstIdentToken(parent));
            default -> false;
        };
    }

    /** A token the head of a chain is written from — a match arm's pattern, an example row's
     * description. Like a sum's cases these are tokens rather than nodes, so they are named by where
     * they are; unlike them they open a chain rather than being one of its parts. */
    private static boolean isChainHead(SyntaxToken t) {
        return switch (t.parent().kind()) {
            case EXAMPLE_ROW -> t.kind() == SyntaxKind.STRING_LIT;
            case MATCH_CASE -> t.kind() != SyntaxKind.ARROW && followedByArrow(t);
            default -> false;
        };
    }

    private static boolean followedByArrow(SyntaxToken t) {
        boolean after = false;
        for (SyntaxElement e : t.parent().children()) {
            if (!(e instanceof SyntaxToken s) || s.isTrivia()) {
                continue;
            }
            if (after) {
                return s.kind() == SyntaxKind.ARROW;
            }
            after = s.start() == t.start();
        }
        return false;
    }

    private static SyntaxToken firstIdentToken(SyntaxNode n) {
        for (SyntaxElement e : n.children()) {
            if (e instanceof SyntaxToken t && t.kind() == SyntaxKind.IDENT) {
                return t;
            }
        }
        return null;
    }

    /** Whether {@code t} is what closes a construct whose members take a line each — the place a
     * comment written under the last member goes. */
    private static boolean closes(SyntaxToken t) {
        // Usually a construct's members run to its own end. A function type and a parenthesised
        // lambda are written as one node whose members stop at a bracket in the middle of it —
        // `(Int, String) -> Bool` — and the layout writes that bracketed run as a construct of its
        // own, so what closes the run closes something even though it closes no node.
        boolean ends = t.end() == t.parent().end()
                || ((t.parent().kind() == SyntaxKind.FN_TYPE
                        || t.parent().kind() == SyntaxKind.LAMBDA_EXPR)
                    && t.kind() == SyntaxKind.RPAREN);
        return ends && switch (t.kind()) {
            case RBRACE, RPAREN, RBRACKET, GT -> true;
            default -> false;
        };
    }

    // --- writing them back ---

    /**
     * The comments {@code place} carries in that direction.
     *
     * <p>Asked once every place exists, and answered here and nowhere else. The construction leaves
     * a slot; which comments go in it is read from the source elements the place was written from,
     * which the construction recorded rather than the source tree being asked a second time.
     */
    private List<TokenDoc> heldAt(Place place, Carrier which) {
        List<TokenDoc> out = new ArrayList<>();
        take(out, assigned.getOrDefault(place, Map.of()).getOrDefault(which, List.of()), which);
        return out;
    }

    private void take(List<TokenDoc> out, List<SyntaxToken> run, Carrier which) {
        for (SyntaxToken t : run) {
            if (consumedComments.add(t.start())) {
                out.add(which == Carrier.TRAILING
                        ? TokenDoc.trailing(t.text().stripTrailing())
                        : TokenDoc.comment(t.text().stripTrailing()));
            }
        }
    }

    /** A member the grammar wrote as an identifier: the same shape as one written as a node. Where
     * the identifier is, the place already says. */
    private Member tokenMember(Place place, TokenDoc d) {
        return new Member(TokenDoc.at(place, d), TokenDoc.endsTheLineOf(place));
    }

    /**
     * {@code members} of a run, with room under the last of them for the comments written inside
     * the run and below everything it holds.
     *
     * <p>They go after that member's own trailing comment, since the line they are under is the one
     * that member ends. A run with no members at all is a different shape and not this one's — see
     * {@link TokenDoc.Vacant}.
     */
    private List<Member> withEndComments(Place run, List<Member> members) {
        if (members.isEmpty()) {
            return members;
        }
        List<Member> out = new ArrayList<>(members);
        Member last = out.get(out.size() - 1);
        out.set(out.size() - 1, new Member(
                concat(last.doc(), last.trailing(), TokenDoc.carries(run, Carrier.AT_END)),
                TokenDoc.NIL));
        return out;
    }

    /** Every token of {@code n}'s subtree, in document order. */
    private static List<SyntaxToken> tokens(SyntaxNode n) {
        List<SyntaxToken> out = new ArrayList<>();
        for (SyntaxElement e : n.children()) {
            if (e instanceof SyntaxNode c) {
                out.addAll(tokens(c));
            } else if (e instanceof SyntaxToken t) {
                out.add(t);
            }
        }
        return out;
    }


    // --- CST navigation ---

    /**
     * A name written through the module or the alias that declares it: its identifiers, and the
     * dots the canonical form writes between them. What goes at each of those boundaries is the
     * rule's to say, which is why the name is handed over as its tokens rather than assembled into
     * one — assembling it is deciding, and a name is one of the places the same decision used to be
     * written out again.
     */
    private TokenDoc qualifiedName(SyntaxNode n, Place at) {
        places.within(n, at);
        return TokenDoc.node(n.kind(), dottedName(idents(n)));
    }

    /** A token of the source, written back as itself. */
    private static TokenDoc token(SyntaxToken t) {
        return TokenDoc.token(t.kind(), t.text());
    }

    /** A name the canonical form writes, as the token that spelling is. The discard is written
     *  where a name is written and is not one, so a document that laid it out as a name would hold
     *  a token the output does not have. */
    private static TokenDoc ident(String text) {
        return TokenDoc.token(text.equals("_") ? SyntaxKind.UNDERSCORE : SyntaxKind.IDENT, text);
    }

    private static TokenDoc dottedName(List<SyntaxToken> idents) {
        List<TokenDoc> parts = new ArrayList<>();
        for (SyntaxToken t : idents) {
            if (!parts.isEmpty()) {
                parts.add(GAP);
                parts.add(DOT);
                parts.add(GAP);
            }
            parts.add(TokenDoc.token(t.kind(), t.text()));
        }
        return concat(parts);
    }

    private List<Member> exprDocs(SyntaxNode n, Place run) {
        List<Member> out = new ArrayList<>();
        for (SyntaxNode c : exprChildren(n)) {
            Place at = memberPlace(run, c);
            out.add(member(at, expr(c, at)));
        }
        return withEndComments(run, out);
    }

    private static List<SyntaxNode> exprChildren(SyntaxNode n) {
        List<SyntaxNode> out = new ArrayList<>();
        for (SyntaxNode c : n.childNodes()) {
            if (isExprKind(c.kind())) {
                out.add(c);
            }
        }
        return out;
    }

    private static SyntaxNode firstExprChild(SyntaxNode n) {
        return exprChildren(n).get(0);
    }

    private Optional<SyntaxNode> firstExprChildOpt(SyntaxNode n) {
        List<SyntaxNode> c = exprChildren(n);
        return c.isEmpty() ? Optional.empty() : Optional.of(c.get(0));
    }

    private SyntaxNode lastExprChild(SyntaxNode n) {
        List<SyntaxNode> c = exprChildren(n);
        return c.get(c.size() - 1);
    }

    private static SyntaxNode onlyExpr(SyntaxNode n) {
        return firstExprChild(n);
    }

    private static boolean isExprKind(SyntaxKind k) {
        return switch (k) {
            case LITERAL_EXPR, VAR_EXPR, FIELD_ACCESS, APPLY_EXPR, BINARY_EXPR,
                 UNARY_EXPR, PIPE_EXPR, PAREN_EXPR, TUPLE_EXPR, LIST_EXPR, LIST_COMP, IF_EXPR,
                 MATCH_EXPR, LAMBDA_EXPR, FIELD_GETTER, NEW_DATA_EXPR, BLOCK_EXPR,
                 UNREACHABLE_EXPR -> true;
            default -> false;
        };
    }

    private List<SyntaxNode> childNodes(SyntaxNode n, SyntaxKind kind) {
        List<SyntaxNode> out = new ArrayList<>();
        for (SyntaxNode c : n.childNodes()) {
            if (c.kind() == kind) {
                out.add(c);
            }
        }
        return out;
    }

    private List<SyntaxToken> idents(SyntaxNode n) {
        List<SyntaxToken> out = new ArrayList<>();
        for (SyntaxElement e : n.children()) {
            if (e instanceof SyntaxToken t && t.kind().standsWhereANameStands()) {
                out.add(t);
            }
        }
        return out;
    }

    private String firstIdent(SyntaxNode n) {
        for (SyntaxElement e : n.children()) {
            if (e instanceof SyntaxToken t && t.kind().standsWhereANameStands()) {
                return t.text();
            }
        }
        throw new IllegalStateException("no identifier in " + n.kind());
    }

    private String lastIdent(SyntaxNode n) {
        String last = null;
        for (SyntaxElement e : n.children()) {
            if (e instanceof SyntaxToken t && t.kind().standsWhereANameStands()) {
                last = t.text();
            }
        }
        return last;
    }

    private SyntaxKind operatorKind(SyntaxNode n) {
        return operatorToken(n).kind();
    }

    private SyntaxToken operatorToken(SyntaxNode n) {
        for (SyntaxElement e : n.children()) {
            if (e instanceof SyntaxToken t && !t.isTrivia() && isBinaryOperator(t.kind())) {
                return t;
            }
        }
        throw new IllegalStateException("no operator in " + n.kind());
    }

    private static boolean isBinaryOperator(SyntaxKind k) {
        return switch (k) {
            case EQ, NE, LT, LE, GT, GE, AND, OR, PLUS, MINUS, STAR, SLASH, PLUSPLUS -> true;
            default -> false;
        };
    }

    private SyntaxNode typeChild(SyntaxNode n) {
        for (SyntaxNode c : n.childNodes()) {
            if (isTypeNode(c.kind())) {
                return c;
            }
        }
        throw new IllegalStateException("no type in " + n.kind());
    }

    private List<SyntaxElement> meaningful(SyntaxNode n) {
        List<SyntaxElement> out = new ArrayList<>();
        for (SyntaxElement e : n.children()) {
            if (e instanceof SyntaxNode || (e instanceof SyntaxToken t && !t.isTrivia())) {
                out.add(e);
            }
        }
        return out;
    }

    private SyntaxToken firstMeaningfulToken(SyntaxNode n) {
        for (SyntaxElement e : n.children()) {
            if (e instanceof SyntaxToken t && !t.isTrivia()) {
                return t;
            }
        }
        throw new IllegalStateException("no token in " + n.kind());
    }
}
